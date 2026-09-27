package com.ainotification.inbox

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Measures only the synthetic marked message; never exports private notification text. */
@RunWith(AndroidJUnit4::class)
class KakaoLongMessageTest {
    @Test fun inspectRequestedSenderLengths() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val requested = InstrumentationRegistry.getArguments().getString("sender") ?: error("sender argument required")
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (InboxNotificationListener.diagnosticInstance == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(250)
        val listener = InboxNotificationListener.diagnosticInstance
        assertNotNull("Listener required", listener)
        val expected = instrument.context.assets.open("kakao-long-923.txt").bufferedReader().use { it.readText() }
        fun overlap(value: String?): Int {
            if (value == null) return 0
            var previous = IntArray(expected.length + 1)
            var best = 0
            for (ch in value) {
                val next = IntArray(expected.length + 1)
                for (j in expected.indices) if (ch == expected[j]) {
                    next[j+1] = previous[j] + 1
                    best = maxOf(best, next[j+1])
                }
                previous = next
            }
            return best
        }
        fun measure(value: String?): JSONObject = JSONObject().apply {
            put("exact_fixture", value == expected)
            put("fixture_contains_whole_value", value != null && value.isNotEmpty() && expected.contains(value))
            put("longest_contiguous_fixture_overlap", overlap(value))
            put("expected_length", expected.length)
            put("length", value?.length ?: 0)
            put("start", value?.contains("[KAKAO-LONG-923-START]") == true)
            put("middle", value?.contains("[KAKAO-LONG-923-MIDDLE]") == true)
            put("end", value?.contains("[KAKAO-LONG-923-END]") == true)
        }
        fun relevant(row: CapturedNotification): Boolean {
            if (row.packageName != "com.kakao.talk") return false
            val m = JSONArray(row.messagesJson)
            return row.title == requested || row.conversationTitle == requested ||
                (0 until m.length()).any { m.getJSONObject(it).optString("sender") == requested }
        }
        fun inspect(row: CapturedNotification): JSONObject = JSONObject().apply {
            put("posted_time", row.postedTime)
            put("summary", row.isGroupSummary)
            put("title_matches_sender", row.title == requested)
            put("text", measure(row.text))
            put("big_text", measure(row.bigText))
            val m = JSONArray(row.messagesJson)
            put("messages", JSONArray().apply {
                for (i in 0 until m.length()) {
                    val item = m.getJSONObject(i)
                    if (item.optString("sender") == requested) put(JSONObject().apply {
                        put("text", measure(if (item.isNull("text")) null else item.getString("text")))
                        put("media_present", !item.isNull("mimeType"))
                    })
                }
            })
        }
        val active = listener!!.activeNotifications.filter { it.packageName == "com.kakao.talk" }
            .map { NotificationNormalizer(context).normalize(it) }
        val saved = runBlocking { (context.applicationContext as InboxApplication).database.notifications().readAll() }
        instrument.sendStatus(0, Bundle().apply {
            putInt("active_kakao_count", active.size)
            putString("active_requested_sender", JSONArray().apply { active.filter { relevant(it) }.forEach { put(inspect(it)) } }.toString())
            putString("saved_requested_sender", JSONArray().apply { saved.filter { relevant(it) }.take(6).forEach { put(inspect(it)) } }.toString())
            putInt("saved_marker_count", saved.count { it.packageName == "com.kakao.talk" &&
                listOf(it.text, it.bigText, it.messagesJson).any { v -> v?.contains("KAKAO-LONG-923-START") == true } })
        })
    }

    @Test fun compareMarkedMessageAcrossNotificationAndDatabase() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val expected = instrument.context.assets.open("kakao-long-923.txt").bufferedReader().use { it.readText() }
        val start = "[KAKAO-LONG-923-START]"
        val middle = "[KAKAO-LONG-923-MIDDLE]"
        val end = "[KAKAO-LONG-923-END]"
        fun measure(value: String?): JSONObject = JSONObject().apply {
            put("present", value != null)
            put("utf16_length", value?.length ?: 0)
            put("code_points", value?.codePointCount(0, value.length) ?: 0)
            put("start", value?.contains(start) == true)
            put("middle", value?.contains(middle) == true)
            put("end", value?.contains(end) == true)
            put("exact_original", value == expected)
            put("contains_full_original", value?.contains(expected) == true)
            put("prefix_of_original", value != null && value.isNotEmpty() && expected.startsWith(value))
        }
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (InboxNotificationListener.diagnosticInstance == null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(250)
        }
        val listener = InboxNotificationListener.diagnosticInstance
        assertNotNull("Connected notification listener is required", listener)
        val matches = listener!!.activeNotifications.filter { sbn ->
            if (sbn.packageName != "com.kakao.talk") false else {
                val n = sbn.notification
                val texts = listOf(n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                    n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()) +
                    NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)?.messages.orEmpty().map { it.text?.toString() }
                texts.any { it?.contains(start) == true }
            }
        }
        assertTrue("Marked incoming Kakao notification required; send fixture as one message and do not open it", matches.isNotEmpty())
        val app = context.applicationContext as InboxApplication
        val observations = JSONArray()
        var fullOriginalFound = false
        for (sbn in matches) {
            val n = sbn.notification
            val rawText = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val rawBig = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            val messages = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)?.messages.orEmpty()
            val relevantMessages = messages.mapNotNull { it.text?.toString() }.filter { it.contains(start) }
            val row = NotificationNormalizer(context).normalize(sbn)
            assertTrue("Normalizer altered text", rawText == row.text)
            assertTrue("Normalizer altered bigText", rawBig == row.bigText)
            val normalizedMessages = JSONArray(row.messagesJson)
            assertEquals("Normalizer dropped messages", messages.size, normalizedMessages.length())
            messages.forEachIndexed { i, message ->
                val storedText = if (normalizedMessages.getJSONObject(i).isNull("text")) null else normalizedMessages.getJSONObject(i).getString("text")
                assertTrue("Normalizer altered message text", message.text?.toString() == storedText)
            }
            var saved: CapturedNotification? = null
            val saveDeadline = SystemClock.elapsedRealtime() + 10000
            while (saved == null && SystemClock.elapsedRealtime() < saveDeadline) {
                saved = runBlocking { app.database.notifications().readAll().firstOrNull { it.snapshotId == row.snapshotId } }
                if (saved == null) SystemClock.sleep(200)
            }
            assertNotNull("Listener snapshot not found in database", saved)
            assertTrue("DB altered text", row.text == saved!!.text)
            assertTrue("DB altered bigText", row.bigText == saved!!.bigText)
            assertTrue("DB altered message array", row.messagesJson == saved!!.messagesJson)
            val containsFull = (listOfNotNull(rawText, rawBig) + relevantMessages).any { it.contains(expected) }
            fullOriginalFound = fullOriginalFound || containsFull
            observations.put(JSONObject().apply {
                put("group_summary", row.isGroupSummary)
                put("text", measure(rawText))
                put("big_text", measure(rawBig))
                put("message_count", messages.size)
                put("marked_messages", JSONArray().apply { relevantMessages.forEach { put(measure(it)) } })
                put("app_preview", measure(row.preview()))
                put("full_original_available", containsFull)
                put("normalizer_and_db_preserve_payload", true)
            })
        }
        instrument.sendStatus(0, Bundle().apply {
            putInt("expected_utf16_length", expected.length)
            putString("kakao_version", context.packageManager.getPackageInfo("com.kakao.talk", 0).versionName)
            putBoolean("full_original_found", fullOriginalFound)
            putString("synthetic_length_observations", observations.toString())
        })
        // A shortened source is a valid measurement, not a claim that capture failed.
    }
}
