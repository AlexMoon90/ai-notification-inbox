package com.ainotification.inbox

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class MemoryBoundariesTest {
    @Test fun pagesAreBoundedAndAllOriginalsRemainReachable()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val db=Room.inMemoryDatabaseBuilder(context,InboxDatabase::class.java).build()
        try {
            db.withTransaction { repeat(325){i->db.notifications().insert(previewNotifications()[0].copy(
                snapshotId="page-$i",postedTime=i.toLong(),capturedTime=i.toLong()))} }
            val repository=NotificationRepository(db.notifications())
            val seen=mutableSetOf<String>()
            repeat(4){
                val page=db.notifications().observePage(Long.MAX_VALUE,it*100,100).first()
                assertTrue(page.size<=100)
                assertTrue(page.all{seen.add(it.snapshotId)})
            }
            assertEquals(325,seen.size)
            assertEquals(325,repository.count.first())
            assertNotNull(db.notifications().find("page-0"))
            assertEquals("page-324",db.notifications().observePage(Long.MAX_VALUE,0,100).first().first().snapshotId)
        }finally{db.close()}
    }
    @Test fun snapshotSharesLongStringsButNotMutableContainers() {
        val text="x".repeat(100_000)
        val original=JSONObject().put("nested",JSONObject().put("text",text).put("array",JSONArray().put(JSONObject().put("value",1))))
        val copied=jsonSnapshot(original)
        assertSame(text,copied.getJSONObject("nested").getString("text"))
        copied.getJSONObject("nested").getJSONArray("array").getJSONObject(0).put("value",2)
        assertEquals(1,original.getJSONObject("nested").getJSONArray("array").getJSONObject(0).getInt("value"))
    }
    @Test fun historicalInstructionsArePreservedOncePerRevision() {
        val text="rule ".repeat(1000)
        val results=JSONObject()
        repeat(1000){results.put("$it",JSONObject().put("policy_id","rev").put("instruction",text).put("status","match"))}
        val state=JSONObject().put("results",results)
        val size=state.toString().length
        compactDecisionInstructions(state)
        assertEquals(1000,results.length())
        assertEquals(text,state.getJSONObject("result_instructions").getString("rev"))
        assertFalse(results.getJSONObject("0").has("instruction"))
        assertTrue(state.toString().length<size/10)
        compactDecisionInstructions(state)
        assertEquals(text,state.getJSONObject("result_instructions").getString("rev"))
    }
    @Test fun repeatedMessagingHistoryKeepsUniqueMessagesAndLatestSource() {
        val messages=JSONArray()
        repeat(80){i->messages.put(JSONObject().put("timestamp",i+1).put("sender","sender").put("text","$i "+"body".repeat(500)))}
        val rows=(0 until 100).map { i->previewNotifications()[0].copy(snapshotId="dup-$i",packageName="com.kakao.talk",
            notificationCategory="msg",conversationIdentity="same-room",isGroupSummary=false,capturedTime=i.toLong(),messagesJson=messages.toString()) }
        val room=conversationRooms(rows).single()
        assertEquals(80,room.messages.size)
        assertTrue(room.messages.all{it.source.snapshotId=="dup-99"})
        assertEquals((1L..80L).toList(),room.messages.map{it.time})
    }
}
