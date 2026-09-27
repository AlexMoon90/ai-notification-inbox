package com.ainotification.inbox

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NotificationCaptureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val normalizer = NotificationNormalizer(context)
    private fun notification(text: String? = "원문", intent: PendingIntent? = null): Notification =
        Notification.Builder(context, "messages").setContentTitle("제목").setContentText(text)
            .setContentIntent(intent).build()
    private fun sbn(n: Notification, time: Long = 1000): StatusBarNotification = StatusBarNotification(
        "test.sender", "test.sender", 7, null, 1000, 0, 0, n, Process.myUserHandle(), time
    )

    @Test fun longKakaoReviewUsesIndividualMessageBoundary() {
        val base = normalizer.normalize(sbn(notification())).copy(packageName = "com.kakao.talk")
        assertFalse(base.copy(text = "가".repeat(499)).needsOriginalReview())
        assertTrue(base.copy(text = "가".repeat(500)).needsOriginalReview())
        assertTrue(base.copy(text = "가".repeat(1369)).needsOriginalReview())
        assertTrue(base.copy(text = null, bigText = "가".repeat(500)).needsOriginalReview())
        val messages = org.json.JSONArray().put(org.json.JSONObject().put("text", "가".repeat(500)))
        assertTrue(base.copy(text = null, messagesJson = messages.toString()).needsOriginalReview())
        val shortMessages = org.json.JSONArray().put(org.json.JSONObject().put("text", "가".repeat(300)))
            .put(org.json.JSONObject().put("text", "나".repeat(300)))
        assertFalse(base.copy(messagesJson = shortMessages.toString()).needsOriginalReview())
        assertFalse(base.copy(text = "가".repeat(500), isGroupSummary = true).needsOriginalReview())
        assertFalse(base.copy(text = "가".repeat(500), packageName = "other.app").needsOriginalReview())
        assertFalse(base.copy(messagesJson = "invalid").needsOriginalReview())
    }

    @Test fun shortReplyDoesNotInheritLongHistoryWarning() {
        val base=normalizer.normalize(sbn(notification("네"))).copy(packageName="com.kakao.talk")
        fun msg(body:String,time:Long)=org.json.JSONObject().put("text",body).put("timestamp",time)
        val history=org.json.JSONArray().put(msg("가".repeat(500),1)).put(msg("네",2))
        assertFalse(base.copy(messagesJson=history.toString()).needsOriginalReview())
        assertFalse(base.copy(bigText="이전 글".repeat(500),messagesJson=history.toString()).needsOriginalReview())
        val unordered=org.json.JSONArray().put(msg("네",2)).put(msg("가".repeat(500),1))
        assertFalse(base.copy(messagesJson=unordered.toString()).needsOriginalReview())
        history.put(msg("나".repeat(500),3))
        assertTrue(base.copy(messagesJson=history.toString()).needsOriginalReview())
        val ties=org.json.JSONArray().put(msg("가".repeat(500),0)).put(msg("네",0))
        assertFalse(base.copy(messagesJson=ties.toString()).needsOriginalReview())
    }

    @Test fun normalizesMissingFieldsWithoutInventingValues() {
        val row = normalizer.normalize(sbn(notification(null)))
        assertEquals("test.sender", row.appLabel)
        assertEquals(7, row.notificationId)
        assertEquals(1000L, row.postedTime)
        assertNull(row.text)
        assertNull(row.bigText)
        assertNull(row.conversationTitle)
        assertEquals("[]", row.messagesJson)
        assertFalse(row.hasContentIntent)
        assertEquals("본문이 제공되지 않은 알림", row.preview())
    }
    @Test fun retainsMessagingStyleAndSender() {
        @Suppress("DEPRECATION")
        val style = Notification.MessagingStyle("나").setConversationTitle("단톡")
            .addMessage("토요일 7시", 123L, "민수").addMessage("좋아요", 124L, "지훈")
        val n = Notification.Builder(context, "chat").setStyle(style).setGroup("team").setGroupSummary(true).build()
        val row = normalizer.normalize(sbn(n))
        assertEquals("단톡", row.conversationTitle)
        val messages = org.json.JSONArray(row.messagesJson)
        assertEquals(2, messages.length())
        assertEquals("민수", messages.getJSONObject(0).getString("sender"))
        assertEquals("토요일 7시", messages.getJSONObject(0).getString("text"))
        assertTrue(row.isGroupSummary)
        assertTrue(row.availableFields.contains("messages"))
    }
    @Test fun repeatedDeliveryDeduplicatesButSameKeyUpdatesPersist() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, InboxDatabase::class.java).build()
        try {
            val dao = db.notifications()
            val first = normalizer.normalize(sbn(notification("첫 메시지")))
            val repeat = normalizer.normalize(sbn(notification("첫 메시지")))
            val update = normalizer.normalize(sbn(notification("두 번째 메시지")))
            assertEquals(first.snapshotId, repeat.snapshotId)
            assertEquals(first.notificationKey, update.notificationKey)
            assertNotEquals(first.snapshotId, update.snapshotId)
            dao.insert(first); dao.insert(repeat); dao.insert(update)
            assertEquals(2, dao.readAll().size)
            assertTrue(dao.readAll().any { it.text == "첫 메시지" })
            dao.deleteAll()
            assertTrue(dao.readAll().isEmpty())
        } finally { db.close() }
    }
    @Test fun systemHistoryDoesNotCrowdOutMessagesOrInflateInboxCount() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, InboxDatabase::class.java).build()
        try {
            val dao = db.notifications()
            val row = normalizer.normalize(sbn(notification()))
            // Existing system history must be excluded before the 500-row limit.
            repeat(501) { index ->
                dao.insert(row.copy(snapshotId = "system-$index", postedTime = 2000L + index,
                    packageName = if (index % 2 == 0) "android" else "com.android.systemui"))
            }
            val messages = row.copy(snapshotId = "messages", packageName = "com.samsung.android.messaging")
            val kakao = row.copy(snapshotId = "kakao", packageName = "com.kakao.talk")
            dao.insert(messages)
            dao.insert(kakao)
            assertEquals(setOf(messages, kakao), dao.observeRecent().first().toSet())
            assertEquals(2, dao.observeCount().first())
            assertEquals(503, dao.readAll().size) // Filtering preserves stored history.
        } finally { db.close() }
    }
    @Test fun persistsAcrossDatabaseReopen() = runBlocking {
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        val row = normalizer.normalize(sbn(notification("재시작 후에도 보존")))
        var db = Room.databaseBuilder(context, InboxDatabase::class.java, name).build()
        try {
            db.notifications().insert(row)
            db.close()
            db = Room.databaseBuilder(context, InboxDatabase::class.java, name).build()
            assertEquals(row, db.notifications().readAll().single())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun preservesExpandedTextAndMissingVersusEmptyFields() {
        val n = notification("")
        n.extras.putCharSequence(Notification.EXTRA_BIG_TEXT, "긴 원문\n두 번째 줄")
        n.extras.putCharSequence(Notification.EXTRA_SUB_TEXT, "계정 표시")
        val row = normalizer.normalize(sbn(n))
        assertEquals("", row.text)
        assertEquals("긴 원문\n두 번째 줄", row.preview())
        assertEquals("계정 표시", row.subText)
        assertTrue(row.availableFields.contains("bigText"))
        assertFalse(row.availableFields.contains("conversationTitle"))
    }
    @Test fun updateRetainsOriginalTokenWithoutSubstitutingNewerConversation() {
        val oldIntent = PendingIntent.getActivity(context, 10, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val newIntent = PendingIntent.getActivity(context, 11, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val first = normalizer.normalize(sbn(notification("첫 메시지", oldIntent)))
        val second = normalizer.normalize(sbn(notification("수정", newIntent)))
        val opener = NotificationOpener()
        opener.update(first, oldIntent)
        opener.update(second, newIntent)
        assertTrue(opener.open(first, context))
        oldIntent.cancel()
        assertFalse(opener.open(first, context)) // Never falls through to the new conversation's token.
        assertTrue(opener.open(second, context))
    }
    @Test fun cacheIsBoundedAndClearRevokesAllTargets() {
        val intent = PendingIntent.getActivity(context, 12, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val first = normalizer.normalize(sbn(notification("첫", intent)))
        val second = normalizer.normalize(sbn(notification("둘", intent)))
        val opener = NotificationOpener(capacity = 1)
        opener.update(first, intent); opener.update(second, intent)
        assertFalse(opener.hasTarget(first))
        assertTrue(opener.hasTarget(second))
        opener.clear()
        assertFalse(opener.open(second, context))
    }
    @Test fun android14SenderExplicitlyOptsIntoActivityLaunch() {
        val options = notificationLaunchOptions()!!
        @Suppress("DEPRECATION")
        assertEquals(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            options.pendingIntentBackgroundActivityStartMode)
    }
    @Test fun canceledIntentFailsSafely() {
        val intent = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val row = normalizer.normalize(sbn(notification(intent = intent)))
        val opener = NotificationOpener()
        opener.update(row, intent)
        intent.cancel()
        assertFalse(opener.open(row, context))
    }
}
