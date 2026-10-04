package com.ainotification.inbox

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Media cannot hold the persistence or policy queues. No content is passed to diagnostics. */
internal class NotificationCapturePipeline<T>(scope:CoroutineScope,
    private val save:suspend(T)->Unit,
    private val onSaved:(T)->Unit,
    private val media:suspend(T)->CapturedNotification,
    private val attach:suspend(CapturedNotification)->Unit,
    private val report:(String,Long)->Unit={_,_->},
    private val failed:(String)->Unit={}) {
    private class Work<T>(val value:T,val received:Long=System.nanoTime(),val saved:CompletableDeferred<Boolean> = CompletableDeferred())
    private val storage=Channel<Work<T>>(256)
    private val images=Channel<Work<T>>(16)
    init {
        scope.launch {
            for(w in storage) {
                try {
                    save(w.value);w.saved.complete(true)
                    report("saved",(System.nanoTime()-w.received)/1_000_000)
                    // Only a non-blocking wakeup; work is already durable in save().
                    onSaved(w.value)
                }catch(e:CancellationException){w.saved.complete(false);throw e}
                catch(_:Exception){w.saved.complete(false);failed("save_failed")}
            }
        }
        repeat(2){scope.launch {
            for(w in images)try {
                // Open image streams immediately on these workers, independently of database/AI work.
                val enriched=media(w.value)
                if(w.saved.await())attach(enriched)
                report("media_finished",(System.nanoTime()-w.received)/1_000_000)
            }catch(e:CancellationException){throw e}catch(_:Exception){failed("media_failed")}
        }}
    }
    fun submit(value:T):Boolean {
        val w=Work(value)
        if(storage.trySend(w).isFailure)return false
        if(images.trySend(w).isFailure)failed("media_queue_full")
        return true
    }
    fun close(){storage.close();images.close()}
}
