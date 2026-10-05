package com.ainotification.inbox

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import org.json.JSONObject

/** Private-device opt-in experiment. Never cancels a source notification. */
internal class NowAlerts(private val app: InboxApplication) {
    private val manager get() = app.getSystemService(NotificationManager::class.java)
    private val prefs get() = app.getSharedPreferences("now-alerts", Context.MODE_PRIVATE)
    private val uri get() = Uri.parse("condition://${app.packageName}/now-alerts")
    private val channelId = "now_alerts_v1"
    val enabled get() = prefs.getBoolean("enabled", false)
    val debug get() = app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    @Synchronized fun start() {
        check(debug)
        if (Build.VERSION.SDK_INT < 35) error("Android 15 이상에서 사용할 수 있어요.")
        check(manager.isNotificationPolicyAccessGranted) { "방해금지 접근을 허용해 주세요." }
        check(manager.areNotificationsEnabled()) { "우리 앱 알림을 허용해 주세요." }
        check(app.listenerConnected.value) { "알림 접근 연결을 확인해 주세요." }
        check(hasActiveNowPolicy(app.selection.state.value.optJSONObject("policy"))) { "먼저 알림 기준을 켜 주세요." }
        manager.createNotificationChannel(NotificationChannel(channelId,"Now 소리·팝업",NotificationManager.IMPORTANCE_HIGH).apply {
            setBypassDnd(true); enableVibration(true); lockscreenVisibility=Notification.VISIBILITY_PRIVATE
        })
        val channel=manager.getNotificationChannel(channelId)
        check(channel.canBypassDnd() && channel.importance==NotificationManager.IMPORTANCE_HIGH && channel.sound!=null) {
            "Now 알림 채널의 소리·팝업과 방해금지 예외 설정을 확인해 주세요."
        }
        stop()
        val policy=ZenPolicy.Builder().allowAlarms(true).allowCalls(ZenPolicy.PEOPLE_TYPE_ANYONE).allowRepeatCallers(true)
            .allowMedia(true).allowSystem(true).allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
            .allowConversations(ZenPolicy.CONVERSATION_SENDERS_NONE).allowEvents(false).allowReminders(false)
            .allowPriorityChannels(true).showPeeking(false).showFullScreenIntent(false)
            .showInNotificationList(false).showStatusBarIcons(false).build()
        val id=manager.addAutomaticZenRule(AutomaticZenRule("Now 알림만 · 테스트", null,
            ComponentName(app,"${app.packageName}.NowAlertSettingsActivity"),uri,policy,NotificationManager.INTERRUPTION_FILTER_PRIORITY,true))
        try {
            check(prefs.edit().putString("rule",id).putLong("since",System.currentTimeMillis()).putBoolean("enabled",true).commit())
            manager.setAutomaticZenRuleState(id,Condition(uri,"Now 알림만",Condition.STATE_TRUE))
        } catch(e:Exception) { manager.removeAutomaticZenRule(id); prefs.edit().clear().commit(); throw e }
    }
    /** Upgrade only our active rule; keep the user's exceptions and start time intact. */
    @Synchronized fun refreshVisualPolicy() {
        if(!debug || !enabled || Build.VERSION.SDK_INT<35 || !manager.isNotificationPolicyAccessGranted)return
        val id=prefs.getString("rule",null) ?: return
        val rule=manager.getAutomaticZenRule(id) ?: return
        if(rule.conditionId!=uri)return
        val policy=rule.zenPolicy ?: return
        if(policy.visualEffectNotificationList==ZenPolicy.STATE_DISALLOW &&
            policy.visualEffectStatusBar==ZenPolicy.STATE_DISALLOW)return
        val wasActive=manager.currentInterruptionFilter!=NotificationManager.INTERRUPTION_FILTER_ALL
        rule.zenPolicy=hideOrdinaryNotifications(policy)
        check(manager.updateAutomaticZenRule(id,rule)) { "Now 알림창 표시 설정을 적용하지 못했습니다." }
        if(wasActive)manager.setAutomaticZenRuleState(id,Condition(uri,"Now 알림만",Condition.STATE_TRUE))
    }
    @Synchronized fun stop() {
        // Only remove this feature's rule; never change the user's other modes.
        val id=prefs.getString("rule",null)
        if(id!=null && manager.isNotificationPolicyAccessGranted && Build.VERSION.SDK_INT>=24) {
            check(manager.removeAutomaticZenRule(id) || manager.getAutomaticZenRule(id)==null) { "방해금지 설정에서 Now 테스트를 꺼 주세요." }
        }
        prefs.edit().putBoolean("enabled",false).remove("rule").commit()
    }
    @Synchronized fun deliver(row:CapturedNotification,result:JSONObject) {
        if(!debug || !enabled || !shouldAlertNow(row,result,prefs.getLong("since",Long.MAX_VALUE),app.packageName))return
        try {
            val id=prefs.getString("rule",null) ?: return
            if(Build.VERSION.SDK_INT<35 || !manager.isNotificationPolicyAccessGranted || manager.getAutomaticZenRule(id)?.isEnabled!=true ||
                !manager.areNotificationsEnabled() || !app.listenerConnected.value) { stop(); return }
            val c=manager.getNotificationChannel(channelId)
            check(c!=null && c.canBypassDnd() && c.importance==NotificationManager.IMPORTANCE_HIGH && c.sound!=null)
            val sent=prefs.getStringSet("sent",emptySet()).orEmpty()
            if(row.snapshotId in sent)return
            val open=PendingIntent.getActivity(app,0,Intent(app,MainActivity::class.java).apply {
                data=Uri.parse("inbox://now/${row.snapshotId}")
                putExtra("now_snapshot",row.snapshotId)
            },PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val text=row.currentMessageText().ifBlank { row.preview() }.take(500)
            manager.notify("now:${row.snapshotId}",1,Notification.Builder(app,channelId)
                .setSmallIcon(R.drawable.ic_nowset_notification).setContentTitle("${row.appLabel} · ${notificationDisplayTitle(row)}")
                .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE).build())
            check(prefs.edit().putStringSet("sent",(sent.toList().takeLast(999)+row.snapshotId).toSet())
                .putLong("lastPosted",System.currentTimeMillis()).putInt("postedCount",prefs.getInt("postedCount",0)+1).commit())
        } catch(_:Exception) {
            runCatching { stop() }
            app.captureError.value="Now 소리·팝업 전달에 실패해 테스트를 중지했습니다. 원래 앱 알림도 확인해 주세요."
        }
    }
}

internal fun shouldAlertNow(row:CapturedNotification,result:JSONObject,since:Long,ownPackage:String,now:Long=System.currentTimeMillis()):Boolean =
    isWithinNowWindow(row,now) && row.packageName!=ownPackage && !row.isGroupSummary && row.postedTime>=since && row.capturedTime>=since &&
        isNowNotification(row,JSONObject().put(row.snapshotId,result))

internal fun hasActiveNowPolicy(policy:JSONObject?):Boolean {
    if(policy==null)return false
    val rules=policy.optJSONArray("rules") ?: return true
    return (0 until rules.length()).any { rules.getJSONObject(it).optBoolean("enabled") }
}

/** Public SDK has no ZenPolicy copy builder. Preserve each explicitly configured field. */
internal fun hideOrdinaryNotifications(policy:ZenPolicy):ZenPolicy {
    val builder=ZenPolicy.Builder()
    fun copy(state:Int,set:(Boolean)->ZenPolicy.Builder) {
        if(state!=ZenPolicy.STATE_UNSET)set(state==ZenPolicy.STATE_ALLOW)
    }
    copy(policy.priorityCategoryAlarms,builder::allowAlarms)
    copy(policy.priorityCategoryMedia,builder::allowMedia)
    copy(policy.priorityCategorySystem,builder::allowSystem)
    copy(policy.priorityCategoryEvents,builder::allowEvents)
    copy(policy.priorityCategoryReminders,builder::allowReminders)
    copy(policy.priorityCategoryRepeatCallers,builder::allowRepeatCallers)
    if(policy.priorityCallSenders!=ZenPolicy.PEOPLE_TYPE_UNSET)builder.allowCalls(policy.priorityCallSenders)
    if(policy.priorityMessageSenders!=ZenPolicy.PEOPLE_TYPE_UNSET)builder.allowMessages(policy.priorityMessageSenders)
    if(policy.priorityConversationSenders!=ZenPolicy.CONVERSATION_SENDERS_UNSET)builder.allowConversations(policy.priorityConversationSenders)
    if(Build.VERSION.SDK_INT>=35)copy(policy.priorityChannelsAllowed,builder::allowPriorityChannels)
    copy(policy.visualEffectAmbient,builder::showInAmbientDisplay)
    copy(policy.visualEffectBadge,builder::showBadges)
    copy(policy.visualEffectLights,builder::showLights)
    copy(policy.visualEffectPeek,builder::showPeeking)
    copy(policy.visualEffectFullScreenIntent,builder::showFullScreenIntent)
    return builder.showInNotificationList(false).showStatusBarIcons(false).build()
}
