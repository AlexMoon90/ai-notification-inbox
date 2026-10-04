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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class HubScreenDataTest {
    @Test fun projectionRunsOffCollectorAndPreservesVisibilityAndOriginals()=runBlocking {
        val caller=Thread.currentThread()
        var producer:Thread?=null
        val original=previewNotifications()[0].copy(snapshotId="visible",packageName="test.shop",
            notificationCategory=null,serviceType=null,messagesJson="[]",postedTime=System.currentTimeMillis()-30)
        val hidden=original.copy(snapshotId="hidden",postedTime=System.currentTimeMillis()-20)
        val pending=original.copy(snapshotId="pending",postedTime=System.currentTimeMillis()-10)
        val results=JSONObject().put("visible",JSONObject().put("status","match").put("action","SHOW"))
            .put("hidden",JSONObject().put("status","match").put("action","HIDE"))
        val data=hubScreenData(flow { producer=Thread.currentThread();emit(listOf(original,hidden,pending)) },
            flowOf(JSONObject().put("results",results)),
            flowOf(listOf(HubClassification("visible","[\"PAYMENT\",\"DELIVERY\"]",1)))).first()
        assertNotSame(caller,producer)
        assertTrue(data.loaded)
        assertEquals(listOf(original),data.now)
        assertEquals(listOf(pending),data.pending)
        assertEquals(listOf(pending,hidden,original),data.records.history["test.shop"])
        assertEquals(listOf(original),data.categories["FINANCE"])
        assertEquals(listOf(original),data.categories["DELIVERY"])
        assertEquals(listOf(pending,hidden),data.categories["OTHER"])
    }

    @Test fun decisionUpdatesReuseConversationProjection()=runBlocking {
        val row=previewNotifications()[0].copy(snapshotId="message",notificationCategory="msg")
        val state=MutableStateFlow(JSONObject())
        val snapshots=mutableListOf<HubScreenData>()
        withTimeout(5000) {
            hubScreenData(flowOf(listOf(row)),state,flowOf(emptyList())).take(2).collect { data ->
                snapshots.add(data)
                if(snapshots.size==1)state.value=JSONObject().put("results",JSONObject()
                    .put(row.snapshotId,JSONObject().put("status","match").put("action","SHOW")))
            }
        }
        assertSame(snapshots[0].records,snapshots[1].records)
        assertEquals(listOf(row),snapshots[0].pending)
        assertEquals(listOf(row),snapshots[1].now)
    }
}
