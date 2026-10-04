package com.ainotification.inbox

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class NotificationCapturePipelineTest {
    @Test fun blockedMediaDoesNotDelayNextNotificationAndAttachesAfterSave()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val mediaGate=CompletableDeferred<Unit>();val selected=CompletableDeferred<Unit>();val attached=CompletableDeferred<Unit>()
        val saved=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val decisions=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val pipeline=NotificationCapturePipeline<String>(scope,
            save={saved.add(it)},onSaved={decisions.add(it);if(decisions.size==2)selected.complete(Unit)},
            media={mediaGate.await();previewNotifications()[0].copy(snapshotId=it)},
            attach={assertTrue(saved.contains(it.snapshotId));if(it.snapshotId=="first")attached.complete(Unit)})
        try{
            assertTrue(pipeline.submit("first"));assertTrue(pipeline.submit("second"))
            withTimeout(3000){selected.await()}
            assertFalse(attached.isCompleted)
            mediaGate.complete(Unit);withTimeout(3000){attached.await()}
            assertEquals(setOf("first","second"),decisions)
        }finally{pipeline.close();scope.cancel()}
    }
    @Test fun failedSaveCannotBeRecreatedByLateMedia()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val done=CompletableDeferred<Unit>();var attached=false;var selected=false
        val pipeline=NotificationCapturePipeline<String>(scope,save={error("disk")},onSaved={selected=true},
            media={previewNotifications()[0]},attach={attached=true},report={stage,_->if(stage=="media_finished")done.complete(Unit)})
        try{pipeline.submit("test");withTimeout(3000){done.await()};assertFalse(attached);assertFalse(selected)}finally{pipeline.close();scope.cancel()}
    }
}
