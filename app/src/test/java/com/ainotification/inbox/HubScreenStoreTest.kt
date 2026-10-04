package com.ainotification.inbox

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class HubScreenStoreTest {
    @Test fun nowDoesNotWaitForRoomsAndReentryUsesCurrentPolicy()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val row=previewNotifications()[0].copy(snapshotId="message",notificationCategory="msg")
            val selection=MutableStateFlow(JSONObject().put("results",JSONObject().put("message",
                JSONObject().put("status","match").put("action","SHOW"))))
            val releaseRooms=CompletableDeferred<Unit>()
            val roomStarted=CompletableDeferred<Unit>()
            val starts=AtomicInteger()
            val store=HubScreenStore(scope,source={
                starts.incrementAndGet()
                hubScreenData(flowOf(listOf(row)),selection,flowOf(emptyList()),includeConversations=false)
            },projectRooms={ rows -> roomStarted.complete(Unit);releaseRooms.await();conversationRooms(rows) })
            withTimeout(5000){store.now.first{it.loaded}}
            assertFalse(roomStarted.isCompleted)
            val subscriber=scope.launch{store.full.collect{}}
            withTimeout(5000){roomStarted.await()}
            assertEquals(listOf(row),store.now.value.now)
            assertFalse(store.full.value.loaded)
            // No UI subscribers are present while the policy changes.
            selection.value=JSONObject().put("results",JSONObject().put("message",
                JSONObject().put("status","match").put("action","HIDE")))
            withTimeout(5000){store.now.first{it.now.isEmpty()}}
            releaseRooms.complete(Unit)
            val full=withTimeout(5000){store.full.first{it.loaded}}
            assertTrue(full.now.isEmpty())
            assertEquals(1,full.records.rooms.size)
            repeat(2){assertTrue(store.now.first().loaded);assertTrue(store.now.first().now.isEmpty())}
            assertEquals(1,starts.get())
            subscriber.cancel()
        } finally {scope.cancel()}
    }

    @Test fun nowPublishesBeforeClassificationAndNeverWaitsForIt()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val row=previewNotifications()[0].copy(snapshotId="fast")
            val release=CompletableDeferred<Unit>()
            val decisions=flowOf(JSONObject().put("results",JSONObject().put("fast",JSONObject().put("status","match").put("action","SHOW"))))
            val store=HubScreenStore(scope,source={nowScreenData(flowOf(listOf(row)),decisions)},classifications=flow{
                release.await();emit(listOf(HubClassification("fast","[\"DELIVERY\"]",1)))
            })
            val now=withTimeout(5000){store.now.first{it.loaded}}
            assertEquals(listOf(row),now.now)
            assertFalse(store.full.value.loaded)
            release.complete(Unit)
            val full=withTimeout(5000){store.full.first{it.loaded}}
            assertEquals(listOf(row),full.categories["DELIVERY"])
            assertSame(now,store.now.value)
        }finally{scope.cancel()}
    }

    @Test fun policyOnlyUpdatesReusePreparedRooms()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val row=previewNotifications()[0]
            val rows=listOf(row)
            val source=MutableStateFlow(HubScreenData(records=HubRecords(rows,emptyList(),emptyMap()),loaded=true))
            val calls=AtomicInteger()
            val store=HubScreenStore(scope,{source},{calls.incrementAndGet();conversationRooms(it)})
            val subscriber=scope.launch{store.full.collect{}}
            val first=withTimeout(5000){store.full.first{it.loaded}}
            source.value=source.value.copy(state=JSONObject().put("revision",2))
            val next=withTimeout(5000){store.full.first{it.state.optInt("revision")==2}}
            assertSame(first.records,next.records)
            assertEquals(1,calls.get())
            subscriber.cancelAndJoin()
            withTimeout(5000){store.full.first{!it.loaded}}
            Unit
        } finally {scope.cancel()}
    }
}
