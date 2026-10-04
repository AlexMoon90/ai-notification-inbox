package com.ainotification.inbox

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class MoneyStructuredDeviceTest {
 @Test fun moneyStorageAndStructuredUiKeepBalanceSeparate()=runBlocking {
  val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext.applicationContext as InboxApplication
  val db=Room.inMemoryDatabaseBuilder(app,InboxDatabase::class.java).build()
  val time=System.currentTimeMillis()
  val deposit=previewNotifications()[0].copy(snapshotId="money-fixture-A",packageName="synthetic.bank",appLabel="테스트은행",title="입금 알림",text="[Web발신]\n<우리은행>123456**7 김OO 입금50,000 잔액1,253,400원",bigText=null,messagesJson="[]",isGroupSummary=false,availableFields="",conversationIdentity=null,conversationTitle=null,notificationCategory=null,serviceType=null,postedTime=time)
  val chat=deposit.copy(snapshotId="money-fixture-F",packageName="com.kakao.talk",appLabel="카카오톡",title="친구",text="오늘 저녁에 볼까?")
  val engine=JevEngine(app){payload->
   val answers=JSONObject();val qs=payload.getJSONObject("questions")
   qs.keys().forEach{k->val opts=qs.getJSONObject(k).getJSONObject("criteria");val choice=when(k){"financial"->"MONEY";"transactionType"->"deposit";"transactionAmount"->"M0";"balanceAfter"->"M1";else->"NONE"};val probs=JSONObject();opts.keys().forEach{probs.put(it,if(it==choice)1.0 else 0.0)};answers.put(k,JSONObject().put("type","choice").put("choice",choice).put("confidence",1.0).put("probabilities",probs))}
   JSONObject().put("answers",answers).put("usage",JSONObject().put("input_tokens",0).put("output_tokens",0))
  }
  val template=JSONObject().put("category","money").put("schema","money_v1").put("possibleTransactionTypes",JSONArray(moneyTypes)).put("fields",JSONArray(moneyFields)).put("questions",JSONArray())
  try{
   db.notifications().insert(deposit);db.notifications().insert(chat)
   val pipe=MoneyPipeline(app,db,engine,designer={template});pipe.process(deposit);pipe.process(chat)
   val events=db.structured().observe().first();assertEquals(1,events.size)
   assertEquals(50000L,events.single().money!!.transactionAmount);assertEquals(1253400L,events.single().money!!.balanceAfter)
   assertNotNull(db.notifications().find(chat.snapshotId));assertNull(db.structured().money(chat.snapshotId))
   val device=UiDevice.getInstance(inst);device.wakeUp()
   fun find(tag:String)=device.wait(Until.findObject(By.res(tag)),15000) ?: error("Missing $tag")
   ActivityScenario.launch(MainActivity::class.java).use{scenario->
    scenario.onActivity{a->a.setContent{InboxTheme{SmartDashboard(app,true,Modifier.fillMaxSize().systemBarsPadding().semantics{testTagsAsResourceId=true},{},fixtureEntries=events,loadOriginal={id->if(id==deposit.snapshotId)deposit else null})}};a.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
    find("smart_concept_2")
    assertTrue(device.wait(Until.hasObject(By.text("+50,000원")),5000))
    assertFalse(device.hasObject(By.textContains("1,253,400")))
    assertTrue(device.hasObject(By.text("김OO · 입금")))
    assertTrue(device.hasObject(By.text("우리은행 · 계좌 123456**7")))
    device.takeScreenshot(File(app.cacheDir,"money-structured-card.png"))
    device.waitForIdle()
    // Tap the visible left side; a user's picture-in-picture video may cover the center.
    find("smart_item_${deposit.snapshotId}").visibleBounds.let{device.click(it.left+40,it.centerY())}
    val detailShown=device.wait(Until.hasObject(By.textContains("거래 후 잔액")),5000)
    if(!detailShown){device.takeScreenshot(File(app.cacheDir,"money-detail-failure.png"));device.dumpWindowHierarchy(File(app.cacheDir,"money-detail-failure.xml"))}
    assertTrue(detailShown)
    assertFalse(device.hasObject(By.textContains("김OO 입금50,000")))
    find("smart_original").click()
    assertTrue(device.wait(Until.hasObject(By.textContains("김OO 입금50,000")),5000))
    device.takeScreenshot(File(app.cacheDir,"money-structured-detail.png"))
    find("smart_mark_seen").click();device.pressBack()
    find("money_shortcut_0").click();find("smart_item_${deposit.snapshotId}")
    scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
   }
   db.notifications().delete(deposit.snapshotId);assertNull(db.structured().money(deposit.snapshotId));assertTrue(db.structured().observe().first().isEmpty())
  }finally{db.close()}
 }
}
