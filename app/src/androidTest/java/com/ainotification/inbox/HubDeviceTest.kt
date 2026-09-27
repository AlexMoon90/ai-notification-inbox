package com.ainotification.inbox

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.setContent
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HubDeviceTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val app get()=inst.targetContext.applicationContext as InboxApplication
    @Test fun liveCategoriesAndLocalReferencePersistence()=runBlocking {
        val before=app.selection.state.value.optJSONObject("policy")?.toString()
        val folder=File(app.cacheDir,"hub-live-isolated").apply{mkdirs()}
        val context=object:ContextWrapper(app){override fun getFilesDir()=folder}
        val db=Room.inMemoryDatabaseBuilder(app,InboxDatabase::class.java).build()
        val classifier=HubClassifier(db,JevEngine(context))
        val cases=listOf(
            Triple("예약 확정","OO병원 9월 30일 오후 2시 진료 예약이 확정되었습니다.","RESERVATION"),
            Triple("Delivery completed","Your parcel was delivered to your front door.","DELIVERY"),
            Triple("카드 승인","OO카드 32,500원 일시불 승인되었습니다.","PAYMENT"),
            Triple("Refund issued","Your refund for order #123 has been processed.","REFUND"),
            Triple("Instagram","Alex liked your photo.","SOCIAL_ACTIVITY"),
            Triple("Check in now","Your flight AB123 to London departs tomorrow at 10:00. Online check-in is open.","TRAVEL")
        )
        val results=JSONArray()
        try {
            cases.forEachIndexed{i,(title,text,expected)->
                val row=previewNotifications()[0].copy(snapshotId="hub-$i",packageName="synthetic.app",appLabel="Test",title=title,text=text,bigText=null,conversationTitle=null,messagesJson="[]",isGroupSummary=false)
                db.notifications().insert(row);classifier.accept(row)
                val fact=db.hub().find(row.snapshotId)!!
                results.put(JSONObject().put("expected",expected).put("events",JSONArray(fact.events())))
                assertTrue("Expected $expected, got ${fact.events()}",expected in fact.events())
                assertEquals(row,db.notifications().find(row.snapshotId))
                db.notifications().delete(row.snapshotId);assertNull(db.hub().find(row.snapshotId))
            }
        } finally {
            val metrics=File(folder,"jev-usage.jsonl").takeIf{it.exists()}?.readLines()?.map{JSONObject(it)}.orEmpty()
            File(app.cacheDir,"hub-live.json").writeText(JSONObject().put("cases",results).put("metrics",JSONArray(metrics)).toString(2))
            db.close();folder.deleteRecursively()
        }
        assertEquals(before,app.selection.state.value.optJSONObject("policy")?.toString())
    }
    @Test fun navigationNowRoomsAndCategories() {
        val device=UiDevice.getInstance(inst);device.wakeUp()
        assumeFalse("Unlock device to verify UI",(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked)
        val before=app.selection.state.value.optJSONObject("policy")?.toString()
        val base=previewNotifications()[0]
        val rows=listOf(
            base.copy(snapshotId="test-finance",packageName="com.google.android.apps.messaging",appLabel="Google Messages",serviceType="SMS",notificationCategory="msg",title="OO카드 이용 안내",text="32,500원이 승인되었습니다.",conversationIdentity="card"),
            base.copy(snapshotId="test-room",title="친구모임방",conversationTitle="친구모임방",conversationIdentity="friends",notificationCategory="msg",messagesJson="""[{"sender":"민수","text":"토요일 어때요?","timestamp":1790400000000},{"sender":"지영","text":"좋아요. 오후 7시 강남역으로 확정해요.","timestamp":1790400300000}]"""),
            base.copy(snapshotId="test-pending",title="확인 대기",text="아직 판단하지 않은 알림",notificationCategory=null)
        )
        val decisions=JSONObject();rows.take(2).forEach{decisions.put(it.snapshotId,JSONObject().put("status","match").put("action","SHOW"))}
        val facts=listOf(HubClassification("test-finance","[\"PAYMENT\"]",1),HubClassification("test-room","[\"MEETING_CONFIRMED\"]",1))
        fun find(tag:String)=device.wait(Until.findObject(By.res(tag)),8000) ?: error("Missing $tag")
        fun text(value:String)=device.wait(Until.findObject(By.text(value)),8000) ?: error("Missing text $value")
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{a->a.setContent{InboxTheme{InboxScreen(app,true,{}, {},fixtureRows=rows,fixtureDecisions=decisions,fixtureFacts=facts)}};a.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
            find("open_policy");text("OO카드 이용 안내");assertNull(device.findObject(By.text("확인 대기")))
            device.takeScreenshot(File(app.cacheDir,"hub-now.png"))
            find("nav_1").click();text("문자");text("친구모임방")
            device.takeScreenshot(File(app.cacheDir,"hub-messenger.png"))
            assertNotNull(device.findObject(By.res("source_icon_com.google.android.apps.messaging")))
            fun swipePage(left:Boolean){
                device.waitForIdle()
                find("messenger_pager").swipe(if(left)Direction.LEFT else Direction.RIGHT,0.85f)
            }
            swipePage(true)
            device.waitForIdle()
            device.dumpWindowHierarchy(File(app.cacheDir,"messenger-swipe.xml"))
            device.takeScreenshot(File(app.cacheDir,"messenger-swipe.png"))
            assertTrue(device.wait(Until.hasObject(By.res("messenger_tab_1").checked(true)),8000))
            text("OO카드 이용 안내")
            assertNull(device.findObject(By.res("source_icon_com.google.android.apps.messaging")))
            swipePage(true)
            assertTrue(device.wait(Until.hasObject(By.res("messenger_tab_2").checked(true)),8000))
            text("친구모임방")
            assertNull(device.findObject(By.res("source_icon_com.kakao.talk")))
            swipePage(false)
            assertTrue(device.wait(Until.hasObject(By.res("messenger_tab_1").checked(true)),8000))
            find("messenger_tab_0").click()
            assertTrue(device.wait(Until.hasObject(By.res("source_icon_com.google.android.apps.messaging")),8000))

            text("친구모임방").click()
            val older=text("민수");val newer=text("지영")
            assertTrue("Newest message must appear above older messages",newer.visibleBounds.top < older.visibleBounds.top)
            device.takeScreenshot(File(app.cacheDir,"hub-room.png"));device.pressBack()
            find("nav_2").click();find("category_FINANCE")
            device.takeScreenshot(File(app.cacheDir,"hub-categories.png"))
            find("category_FINANCE").click();text("OO카드 이용 안내");device.pressBack()
            find("nav_0").click();find("open_review").click();text("확인 대기");device.pressBack()
            scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
        }
        assertEquals(before,app.selection.state.value.optJSONObject("policy")?.toString())
    }
}
