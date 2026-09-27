package com.ainotification.inbox

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class NotificationNormalizer(private val context: Context) {
    fun normalize(sbn: StatusBarNotification): CapturedNotification {
        val n = sbn.notification
        val extras = n.extras
        fun text(key: String) = extras.getCharSequence(key)?.toString()
        val title = text(Notification.EXTRA_TITLE)
        val body = text(Notification.EXTRA_TEXT)
        val big = text(Notification.EXTRA_BIG_TEXT)
        val sub = text(Notification.EXTRA_SUB_TEXT)
        val conversation = text(Notification.EXTRA_CONVERSATION_TITLE)
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val messages = style?.messages.orEmpty()
        val array = JSONArray()
        messages.forEach { message ->
            val sender = message.person?.name?.toString()
            array.put(JSONObject().apply {
                put("text", message.text?.toString() ?: JSONObject.NULL)
                put("sender", sender ?: JSONObject.NULL)
                put("timestamp", message.timestamp)
                put("mimeType", message.dataMimeType ?: JSONObject.NULL)
                message.dataUri?.let{put("dataUri",it.toString())}
            })
        }
        val available = listOf(
            "title" to Notification.EXTRA_TITLE, "text" to Notification.EXTRA_TEXT,
            "bigText" to Notification.EXTRA_BIG_TEXT, "subText" to Notification.EXTRA_SUB_TEXT,
            "conversationTitle" to Notification.EXTRA_CONVERSATION_TITLE,
            "messages" to Notification.EXTRA_MESSAGES,
        ).filter { extras.containsKey(it.second) }.map { it.first }.joinToString(", ")
        val label = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) { sbn.packageName }
        val conversationIdentity = n.shortcutId?.takeIf { it.isNotBlank() }?.let { shortcut ->
            MessageDigest.getInstance("SHA-256").digest("${sbn.packageName}|${sbn.user.hashCode()}|$shortcut".toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
        val summary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
        // A repeated delivery is ignored, but updates to the same Android key remain as history.
        val identity = JSONArray(listOf(sbn.key, sbn.postTime, title, body, big, sub,
            conversation, array.toString(), sbn.groupKey, n.channelId, n.contentIntent != null,
            summary, available, conversationIdentity, n.category,
            if(sbn.packageName == android.provider.Telephony.Sms.getDefaultSmsPackage(context) || sbn.packageName in smsPackages) "SMS" else null,
            if(style?.isGroupConversation != true) messages.lastOrNull()?.person?.uri else null, style?.isGroupConversation)).toString()
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return CapturedNotification(hash, sbn.packageName, label, sbn.key, sbn.id,
            sbn.postTime, System.currentTimeMillis(), title, body, big, sub, conversation,
            array.toString(), sbn.groupKey, n.channelId, n.contentIntent != null, summary, available, conversationIdentity, n.category,
            if(sbn.packageName == android.provider.Telephony.Sms.getDefaultSmsPackage(context) || sbn.packageName in smsPackages) "SMS" else null,
            if(style?.isGroupConversation != true) messages.lastOrNull()?.person?.uri else null, style?.isGroupConversation)
    }
}
