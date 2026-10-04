package com.ainotification.inbox

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class DisplayIndexTest {
    private fun db()=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(),InboxDatabase::class.java).build()
    @Test fun allRecordsAndOlderNowSurviveMoreThan100NewerNotifications()=runBlocking {
        val db=db()
        try {
            val base=previewNotifications()[0].copy(packageName="example.mail",messagesJson="[]",notificationCategory=null,isGroupSummary=false)
            repeat(241){i->val r=base.copy(snapshotId="visible-$i",postedTime=i.toLong(),capturedTime=i.toLong(),text="full "+"x".repeat(4000),bigText=null)
                db.notifications().insert(r);indexNotification(db,r)}
            val repository=NotificationRepository(db.notifications(),db=db)
            val all=repository.all.first()
            assertEquals(241,all.size)
            assertTrue(all.all{it.text!!.length<=240})
            val decision=JSONObject().put("results",JSONObject().put("visible-0",JSONObject().put("status","match").put("action","SHOW")))
            val now=nowScreenData(flowOf(all),flowOf(decision),clock=flowOf(1000L)).first()
            assertEquals("visible-0",now.now.single().snapshotId)
            assertTrue(repository.loadBody("visible-0")!!.text!!.length>4000)
            assertEquals(241,repository.count.first())
        } finally{db.close()}
    }
    @Test fun historyDeduplicatesWithoutLosingFirstObservedMessagesOrFullBody()=runBlocking {
        val db=db()
        try {
            val long="original "+"x".repeat(2000)
            fun messages(vararg ids:Int)=JSONArray().also{a->ids.forEach{i->a.put(JSONObject().put("sender","sender").put("text","$i $long").put("timestamp",i))}}.toString()
            val old=previewNotifications()[0].copy(snapshotId="first",packageName="com.kakao.talk",conversationIdentity="stable",notificationCategory="msg",isGroupSummary=false,messagesJson=messages(1,2,3),capturedTime=1)
            val fresh=old.copy(snapshotId="second",capturedTime=2,messagesJson=messages(2,3,4))
            for(r in listOf(old,fresh)){db.notifications().insert(r);indexNotification(db,r)}
            val repo=NotificationRepository(db.notifications(),db=db)
            val room=indexedRooms(repo.all.first(),db.display().observeMessages().first()).single()
            assertEquals(listOf(1L,2L,3L,4L),room.messages.map{it.time})
            assertEquals("second",room.messages.first{it.time==2L}.source.snapshotId)
            val full=conversationRooms(listOf(repo.loadBody("first")!!)).single()
            assertTrue(full.messages.first().text.length>2000)
            assertEquals(full.messages.first().identity,room.messages.first().identity)
            indexNotification(db,fresh)
            assertEquals(4,db.display().observeMessages().first().size)
            db.notifications().deleteAll()
            assertTrue(db.display().observeRows().first().isEmpty())
            assertTrue(db.display().observeMessages().first().isEmpty())
        } finally{db.close()}
    }
    @Test fun backfillDoesNotEnqueueHistoricalAiWork()=runBlocking {
        val db=db();val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            repeat(65){i->db.notifications().insert(previewNotifications()[0].copy(snapshotId="backfill-$i"))}
            val indexer=DisplayIndexer(db);indexer.start(scope)
            withTimeout(10000){indexer.ready.first{it}}
            assertEquals(65,db.display().observeRows().first().size)
            assertTrue(db.nowQueue().batch().isEmpty())
        } finally{scope.cancel();db.close()}
    }
}
