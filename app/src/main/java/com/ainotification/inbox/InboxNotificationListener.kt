package com.ainotification.inbox

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class InboxNotificationListener : NotificationListenerService() {
    companion object {
        // Instrumentation can inspect public notification metadata on a debug device.
        // Never populated in a non-debuggable build; cleared with the service lifecycle.
        @Volatile internal var diagnosticInstance: InboxNotificationListener? = null
            private set
    }
    private val app get() = application as InboxApplication
    private val normalizer by lazy { NotificationNormalizer(this) }
    private data class Capture(val row:CapturedNotification,val live:Boolean,val notification:android.app.Notification,val key:String)
    private lateinit var pipeline:NotificationCapturePipeline<Capture>
    private val hubQueue = Channel<Unit>(capacity=Channel.CONFLATED)
    private var hubJob:kotlinx.coroutines.Job?=null
    private val timingLock=Any()
    private fun timing(stage:String,millis:Long){
        synchronized(timingLock){runCatching{
            val f=java.io.File(filesDir,"capture-timing.jsonl")
            if(f.length()>1024*1024)f.writeText("")
            f.appendText(org.json.JSONObject().put("time",System.currentTimeMillis()).put("stage",stage).put("elapsed_ms",millis).toString()+"\n")
        }}
    }
    override fun onCreate() {
        super.onCreate()
        val hubPrefs=getSharedPreferences("hub-backlog",MODE_PRIVATE)
        val since=hubPrefs.getLong("since",System.currentTimeMillis())
        hubPrefs.edit().putLong("since",since).apply()
        hubJob=app.scope.launch {
            // DB is the durable classification backlog; wakeups can be safely coalesced.
            while (true) {
                kotlinx.coroutines.withTimeoutOrNull(30_000) { hubQueue.receive() }
                try {
                    do {
                        val batch=app.database.notifications().unclassified(since)
                        var failed=false
                        for(row in batch) try { app.hub.accept(row) }
                        catch(e:CancellationException){throw e}
                        catch(_:Exception){failed=true}
                        if(failed)break
                    } while(batch.isNotEmpty())
                } catch(e:CancellationException){throw e}
                catch(_:Exception){ /* Retain unfinished originals and retry later. */ }
            }
        }
        hubQueue.trySend(Unit)
        pipeline=NotificationCapturePipeline(app.scope,
            save={work->
                app.opener.update(work.row,work.notification.contentIntent)
                app.nowQueue.save(app.images.metadata(work.row,work.notification), work.live)
                hubQueue.trySend(Unit)
                // Temporary, debug-only Kakao experiment. Never wait for AI to dismiss the source.
                val probe=getSharedPreferences("privacy-probe",MODE_PRIVATE)
                if(work.live && work.row.packageName=="com.kakao.talk" &&
                    applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
                    probe.getLong("until",0)>System.currentTimeMillis()){
                    try{
                        cancelNotification(work.key)
                        probe.edit().putLong("last_request",System.currentTimeMillis())
                            .putInt("requests",probe.getInt("requests",0)+1).apply()
                    }catch(_:Exception){probe.edit().putBoolean("cancel_failed",true).apply()}
                }
            },
            onSaved={app.nowQueue.wake();app.structured.wake()},
            media={work->
                val images=PendingImageReads()
                try{
                    images.prepare(work.row){contentResolver.openInputStream(android.net.Uri.parse(it))}
                    app.images.capture(work.row,work.notification,images)
                }finally{images.close()}
            },
            attach={app.repository.attachMedia(it)},report=::timing,
            failed={stage->timing(stage,0);if(!stage.startsWith("media"))app.captureError.value="일부 알림 처리가 완료되지 않았습니다. 원래 앱도 확인해 주세요."})
    }
    override fun onListenerConnected() {
        super.onListenerConnected()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            diagnosticInstance = this
        }
        app.listenerConnected.value = true
        runCatching { app.nowAlerts.refreshVisualPolicy() }.onFailure {
            app.captureError.value = "다른 앱의 알림창 숨김 설정을 적용하지 못했습니다. Now 알림 설정을 확인해 주세요."
        }
        try { activeNotifications?.forEach { capture(it, false) } }
        catch (_: Exception) { app.captureError.value = "현재 알림을 읽지 못했습니다. 알림 접근 설정을 확인해 주세요." }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { sbn?.let { capture(it) } }
    private fun capture(sbn: StatusBarNotification, live: Boolean = true) {
        try {
            val row = normalizer.normalize(sbn)
            if (!pipeline.submit(Capture(row,live,sbn.notification,sbn.key))) {
                app.captureError.value = "알림이 한꺼번에 많이 들어와 일부를 저장하지 못했습니다. 원래 앱도 확인해 주세요."
            }
        } catch (_: Exception) {
            // Never log the exception: vendor exception messages may contain notification text.
            app.captureError.value = "일부 알림을 읽지 못했습니다. 원래 앱에서 확인해 주세요."
        }
    }
    override fun onNotificationRemoved(sbn:StatusBarNotification?,rankingMap:RankingMap?,reason:Int){
        if(sbn?.packageName=="com.kakao.talk" && reason==REASON_LISTENER_CANCEL &&
            applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0){
            getSharedPreferences("privacy-probe",MODE_PRIVATE).edit()
                .putLong("last_removed",System.currentTimeMillis()).apply()
        }
    }
    // Keep the original token after removal; its creator decides when it is canceled.
    override fun onListenerDisconnected() {
        if (diagnosticInstance === this) diagnosticInstance = null
        app.listenerConnected.value = false
        runCatching { app.nowAlerts.stop() }
        super.onListenerDisconnected()
    }
    override fun onDestroy() {
        if (diagnosticInstance === this) diagnosticInstance = null
        app.listenerConnected.value = false
        runCatching { app.nowAlerts.stop() }
        hubJob?.cancel()
        pipeline.close() // accepted snapshots drain in application scope
        super.onDestroy()
    }
}
