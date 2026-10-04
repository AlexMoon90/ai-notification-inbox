package com.ainotification.inbox

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import android.widget.*

/** Manual, debug-only Galaxy experiment; no user policy or source notification edits. */
class DndProbeActivity : Activity() {
    private val manager get()=getSystemService(NotificationManager::class.java)
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var status:TextView
    private var deadline=0L
    private var lastError=""
    private var lastPosted=0L
    private val prefs get()=getSharedPreferences("dnd-probe",MODE_PRIVATE)
    private val tick=object:Runnable { override fun run(){
        if(deadline>0 && SystemClock.elapsedRealtime()>=deadline)stopProbe()
        refresh();handler.postDelayed(this,1000)
    }}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        // DND can recreate this Activity. Keep the same bounded experiment alive.
        val savedDeadline=prefs.getLong("deadline",0)
        if(prefs.getString("rule",null)!=null && savedDeadline>SystemClock.elapsedRealtime() &&
            savedDeadline-SystemClock.elapsedRealtime()<=120000 && manager.isNotificationPolicyAccessGranted) {
            deadline=savedDeadline
        } else stopProbe()
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(32,70,32,30)}
        box.addView(TextView(this).apply{text="알림 통로 · 2분 실험";textSize=24f})
        box.addView(TextView(this).apply{text="일반 알림 소리·팝업을 억제하고 우리 테스트 채널은 허용합니다. 전화·알람·미디어는 유지합니다. 기존 방해금지 예외 앱은 별도로 울릴 수 있습니다. 원본 알림은 삭제하지 않습니다."})
        status=TextView(this);box.addView(status)
        fun button(label:String,action:()->Unit){box.addView(Button(this).apply{text=label;setOnClickListener{runCatching(action).onFailure{lastError="실패: ${it.javaClass.simpleName}";refresh()}}})}
        button("1. 우리 앱 알림 허용") { if(Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),42) }
        button("2. 방해금지 접근 허용") { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        button("3. 일반 상태에서 우리 알림 확인 (3초 뒤)") { scheduleNotification() }
        button("4. 2분 실험 시작 + 5초 뒤 우리 알림") { startProbe();handler.postDelayed({runCatching{postNotification()}.onFailure{lastError="알림 발행 실패: ${it.javaClass.simpleName}";refresh()}},5000) }
        button("5. 우리 알림 다시 보내기 (3초 뒤)") { scheduleNotification() }
        button("6. 즉시 실험 종료") { stopProbe();refresh() }
        setContentView(box);handler.post(tick)
    }
    private fun refresh(){if(!::status.isInitialized)return
        status.text="알림 허용: ${manager.areNotificationsEnabled()}\n방해금지 접근: ${manager.isNotificationPolicyAccessGranted}\n현재 필터: ${manager.currentInterruptionFilter}\n"+
            if(deadline>0)"실험 중 · 남은 시간 ${((deadline-SystemClock.elapsedRealtime())/1000).coerceAtLeast(0)}초" else "실험 꺼짐 · $lastError"
        val channel=manager.getNotificationChannel("dnd_probe_v1")
        val audio=getSystemService(android.media.AudioManager::class.java)
        java.io.File(filesDir,"dnd-probe-status.json").writeText(org.json.JSONObject()
            .put("time",System.currentTimeMillis()).put("access",manager.isNotificationPolicyAccessGranted)
            .put("notificationsEnabled",manager.areNotificationsEnabled()).put("filter",manager.currentInterruptionFilter)
            .put("ruleId",prefs.getString("rule",null)).put("activeTimer",deadline>0)
            .put("channelBypass",channel?.canBypassDnd()).put("channelImportance",channel?.importance)
            .put("channelHasSound",channel?.sound!=null).put("ringerMode",audio.ringerMode)
            .put("notificationVolume",audio.getStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION))
            .put("lastPosted",prefs.getLong("lastPosted",0)).put("error",lastError).toString())
    }
    private fun scheduleNotification(){check(manager.areNotificationsEnabled());handler.postDelayed({runCatching{postNotification()}},3000)}
    private fun postNotification(){
        check(manager.isNotificationPolicyAccessGranted){"방해금지 접근 필요"}
        check(manager.areNotificationsEnabled()){"알림 허용 필요"}
        val channel=NotificationChannel("dnd_probe_v1","소리·팝업 실험",NotificationManager.IMPORTANCE_HIGH).apply {
            setBypassDnd(true);enableVibration(true)
        }
        manager.createNotificationChannel(channel)
        val actual=manager.getNotificationChannel(channel.id)
        check(actual.canBypassDnd() && actual.importance==NotificationManager.IMPORTANCE_HIGH){"테스트 채널 설정 확인 필요"}
        val open=PendingIntent.getActivity(this,42,Intent(this,DndProbeActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(42002,Notification.Builder(this,channel.id).setSmallIcon(com.ainotification.inbox.R.drawable.ic_inbox)
            .setContentTitle("우리 앱 소리·팝업 테스트").setContentText("이 알림의 소리와 화면 위 팝업을 확인해 주세요.")
            .setContentIntent(open).setAutoCancel(true).build())
        lastPosted=System.currentTimeMillis();prefs.edit().putLong("lastPosted",lastPosted).apply();refresh()
    }
    private fun startProbe(){
        if(Build.VERSION.SDK_INT<35)throw IllegalStateException("Android 15 이상에서만 실험할 수 있습니다.")
        check(manager.isNotificationPolicyAccessGranted);check(manager.areNotificationsEnabled())
        stopProbe()
        val uri=Uri.parse("condition://$packageName/debug-dnd-probe")
        val policy=ZenPolicy.Builder().allowAlarms(true).allowCalls(ZenPolicy.PEOPLE_TYPE_ANYONE).allowRepeatCallers(true)
            .allowMedia(true).allowSystem(true).allowMessages(ZenPolicy.PEOPLE_TYPE_NONE).allowConversations(ZenPolicy.CONVERSATION_SENDERS_NONE)
            .allowEvents(false).allowReminders(false).allowPriorityChannels(true)
            .showPeeking(false).showFullScreenIntent(false).showInNotificationList(true).showStatusBarIcons(true).build()
        val rule=AutomaticZenRule("Inbox 2분 알림 실험",null,ComponentName(this,DndProbeActivity::class.java),uri,policy,NotificationManager.INTERRUPTION_FILTER_PRIORITY,true)
        val id=manager.addAutomaticZenRule(rule)
        deadline=SystemClock.elapsedRealtime()+120000
        if(!prefs.edit().putString("rule",id).putLong("deadline",deadline).commit()) {
            manager.removeAutomaticZenRule(id);deadline=0;error("실험 상태 저장 실패")
        }
        try {
            getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,deadline,expiryIntent())
            manager.setAutomaticZenRuleState(id,Condition(uri,"2분 실험",Condition.STATE_TRUE)) }
        catch(e:Exception){stopProbe();throw e}
        refresh()
    }
    private fun expiryIntent()=PendingIntent.getBroadcast(this,42001,Intent(this,DndProbeExpiry::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun stopProbe(){
        getSystemService(AlarmManager::class.java).cancel(expiryIntent())
        val id=prefs.getString("rule",null)
        if(id!=null && manager.isNotificationPolicyAccessGranted){
            if(manager.removeAutomaticZenRule(id))prefs.edit().remove("rule").remove("deadline").commit()
        }
        deadline=0
    }
    override fun onDestroy(){
        handler.removeCallbacksAndMessages(null)
        // The expiry alarm survives recreation/background process death.
        // Only an explicit finish ends the experiment before its deadline.
        if(isFinishing && !isChangingConfigurations)stopProbe()
        super.onDestroy()
    }
}

/** Process-independent cleanup backup; foreground timer also stops at the deadline. */
class DndProbeExpiry : BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val prefs=context.getSharedPreferences("dnd-probe",Context.MODE_PRIVATE)
        val manager=context.getSystemService(NotificationManager::class.java)
        val id=prefs.getString("rule",null) ?: return
        if(manager.isNotificationPolicyAccessGranted && manager.removeAutomaticZenRule(id))prefs.edit().remove("rule").remove("deadline").commit()
    }
}
