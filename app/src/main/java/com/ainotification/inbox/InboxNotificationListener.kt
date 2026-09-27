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
    private val queue = Channel<Triple<CapturedNotification, Boolean, android.app.Notification>>(capacity = 256)

    private val hubQueue = Channel<CapturedNotification>(capacity=256)
    override fun onCreate() {
        super.onCreate()
        app.scope.launch {
            for(row in hubQueue) try { app.hub.accept(row) }
            catch(e:CancellationException){throw e}
            catch(_:Exception){ /* Missing metadata stays in 기타; never change the policy decision. */ }
        }
        app.scope.launch {
            for ((original, live, notification) in queue) {
                try { val row=app.images.capture(original,notification);app.opener.update(row,notification.contentIntent);app.repository.save(row); if (live) { app.selection.accept(row); hubQueue.trySend(row) } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { app.captureError.value = "알림 저장에 실패했습니다. 저장 공간을 확인해 주세요." }
            }
            hubQueue.close()
        }
    }
    override fun onListenerConnected() {
        super.onListenerConnected()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            diagnosticInstance = this
        }
        app.listenerConnected.value = true
        try { activeNotifications?.forEach { capture(it, false) } }
        catch (_: Exception) { app.captureError.value = "현재 알림을 읽지 못했습니다. 알림 접근 설정을 확인해 주세요." }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { sbn?.let { capture(it) } }
    private fun capture(sbn: StatusBarNotification, live: Boolean = true) {
        if (sbn.packageName == packageName || sbn.packageName in excludedNotificationPackages) return
        try {
            val row = normalizer.normalize(sbn)
            if (queue.trySend(Triple(row,live,sbn.notification)).isFailure) {
                app.captureError.value = "알림이 한꺼번에 많이 들어와 일부를 저장하지 못했습니다. 원래 앱도 확인해 주세요."
            }
        } catch (_: Exception) {
            // Never log the exception: vendor exception messages may contain notification text.
            app.captureError.value = "일부 알림을 읽지 못했습니다. 원래 앱에서 확인해 주세요."
        }
    }
    // Keep the original token after removal; its creator decides when it is canceled.
    override fun onListenerDisconnected() {
        if (diagnosticInstance === this) diagnosticInstance = null
        app.listenerConnected.value = false
        super.onListenerDisconnected()
    }
    override fun onDestroy() {
        if (diagnosticInstance === this) diagnosticInstance = null
        app.listenerConnected.value = false
        queue.close() // application scope drains already accepted snapshots
        super.onDestroy()
    }
}
