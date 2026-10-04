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
class NowWindowAndMessengerTest {
    @Test fun expiryWithoutIncomingNotificationsPreservesArchiveAndMessages()=runBlocking {
        val time=NOW_WINDOW_MS+10000
        val row=previewNotifications()[0].copy(snapshotId="expires",packageName="com.kakao.talk",postedTime=10001,notificationCategory="msg")
        val expired=row.copy(snapshotId="expired",postedTime=10000,conversationIdentity="older")
        val pending=row.copy(snapshotId="pending",postedTime=10000)
        val decisions=JSONObject().put("results",JSONObject().put("expires",JSONObject().put("status","match").put("action","SHOW"))
            .put("expired",JSONObject().put("status","match").put("action","SHOW")))
        val clock=MutableStateFlow(time)
        val values=mutableListOf<HubScreenData>()
        withTimeout(5000){nowScreenData(flowOf(listOf(row,expired,pending)),flowOf(decisions),includeConversations=true,clock=clock).take(2).collect{
            values.add(it)
            if(values.size==1)clock.value=time+1
        }}
        assertEquals(listOf(row),values[0].now)
        assertTrue(values[0].pending.isEmpty())
        assertTrue(values[1].now.isEmpty())
        assertEquals(3,values[1].records.rows.size)
        assertTrue(values[1].records.rooms.isNotEmpty())
        assertEquals(3,withHubClassifications(values[1],emptyList()).categories["OTHER"]!!.size)
    }
    @Test fun generalMessageCategoryAndMessagingStyleDoNotCreateMessengerServices(){
        val row=previewNotifications()[0].copy(notificationCategory="msg",isGroupSummary=false,
            messagesJson="""[{"sender":"service","text":"notice","timestamp":123}]""")
        for(pkg in listOf("com.nhn.android.search","com.nhn.android.navercafe","com.google.android.gm","com.instagram.android","unknown.app"))
            assertNull(pkg,messageService(row.copy(packageName=pkg,serviceType=null)))
        for(pkg in listOf("com.kakao.talk","jp.naver.line.android","org.telegram.messenger","com.whatsapp"))
            assertNotNull(pkg,messageService(row.copy(packageName=pkg,serviceType=null)))
        assertEquals("문자",messageService(row.copy(packageName="com.samsung.android.messaging")))
        assertNull(messageService(row.copy(packageName="com.kakao.talk",isGroupSummary=true)))
    }
}
