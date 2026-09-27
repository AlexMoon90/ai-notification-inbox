package com.ainotification.inbox

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.UUID

/** Exports only field presence, flags, counts and salted opaque identifiers. No message/name text. */
@RunWith(AndroidJUnit4::class)
class KakaoMetadataTest {
    @Test fun inspectPublicNotificationMetadata() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (InboxNotificationListener.diagnosticInstance == null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(250)
        }
        val listener = InboxNotificationListener.diagnosticInstance
        assertNotNull("Notification access and a connected listener are required", listener)
        val prefs = context.getSharedPreferences("debug_metadata_probe", 0)
        val salt = prefs.getString("salt", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("salt", it).commit()
        }
        fun token(value: String?): Any = if (value.isNullOrEmpty()) JSONObject.NULL else
            MessageDigest.getInstance("SHA-256").digest((salt + value).toByteArray())
                .joinToString("") { "%02x".format(it) }.take(16)
        val rows = JSONArray()
        listener!!.activeNotifications.filter { it.packageName == "com.kakao.talk" }.forEach { sbn ->
            val n = sbn.notification
            val e = n.extras
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
            val ranking = NotificationListenerService.Ranking()
            val ranked = listener.currentRanking.getRanking(sbn.key, ranking)
            rows.put(JSONObject().apply {
                put("notification_key_token", token(sbn.key))
                put("notification_id_token", token(sbn.id.toString()))
                put("tag_token", token(sbn.tag))
                put("app_group_token", token(n.group))
                put("system_group_token", token(sbn.groupKey))
                put("channel_token", token(n.channelId))
                put("shortcut_token", token(n.shortcutId))
                put("locus_token", if (Build.VERSION.SDK_INT >= 29) token(n.locusId?.id) else JSONObject.NULL)
                put("channel_conversation_token", if (Build.VERSION.SDK_INT >= 30 && ranked) token(ranking.channel?.conversationId) else JSONObject.NULL)
                put("ranking_is_conversation", if (Build.VERSION.SDK_INT >= 30 && ranked) ranking.isConversation else JSONObject.NULL)
                put("is_notification_group_summary", n.flags and Notification.FLAG_GROUP_SUMMARY != 0)
                put("explicit_group_conversation_present", e.containsKey("android.isGroupConversation"))
                put("explicit_group_conversation", if (e.containsKey("android.isGroupConversation")) e.getBoolean("android.isGroupConversation") else JSONObject.NULL)
                put("messaging_style_present", style != null)
                put("style_is_group_conversation", style?.isGroupConversation ?: JSONObject.NULL)
                put("conversation_title_present", !e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE).isNullOrEmpty())
                put("message_count", style?.messages?.size ?: 0)
                put("distinct_sender_count_in_visible_messages", style?.messages?.mapNotNull { it.person?.key ?: it.person?.name?.toString() }?.distinct()?.size ?: 0)
                put("sender_key_present_count", style?.messages?.count { !it.person?.key.isNullOrEmpty() } ?: 0)
                put("sender_uri_present_count", style?.messages?.count { !it.person?.uri.isNullOrEmpty() } ?: 0)
                put("extra_count", e.keySet().size)
                put("non_android_extra_count", e.keySet().count { !it.startsWith("android.") })
            })
        }
        val version = context.packageManager.getPackageInfo("com.kakao.talk", 0).versionName
        instrumentation.sendStatus(0, Bundle().apply {
            putString("kakao_version", version)
            putInt("android_sdk", Build.VERSION.SDK_INT)
            putInt("active_kakao_count", rows.length())
            putString("metadata_only", rows.toString())
        })
    }
}
