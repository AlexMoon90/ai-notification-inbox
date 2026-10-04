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

class ConversationContextDeviceTest {
 @Test fun inboundReplyShowsUncertaintyAndOriginalContext():Unit=runBlocking {
  val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext.applicationContext as InboxApplication
  val db=Room.inMemoryDatabaseBuilder(app,InboxDatabase::class.java).build();val now=System.currentTimeMillis()
  val row=previewNotifications()[0].copy(snapshotId="context-fixture",packageName="com.kakao.talk",appLabel="카카오톡",title="테스트 대화",conversationIdentity="synthetic-room",conversationTitle=null,text="네, 금요일 2시 괜찮아요.",bigText=null,messagesJson="[]",isGroupSummary=false,availableFields="",notificationCategory="msg",postedTime=now)
  val engine=JevEngine(app){payload->val answers=JSONObject();val qs=payload.getJSONObject("questions");qs.keys().forEach{k->val criteria=qs.getJSONObject(k).getJSONObject("criteria");val selected=when(k){"intent"->"appointment";"evidence"->"confirmed";"sourceKind"->"human_message";"responseLike"->"yes";"linked"->"no";"relationship"->"work_likely";"datetime"->"V0";else->"NONE"};val p=JSONObject();criteria.keys().forEach{p.put(it,if(it==selected)1.0 else 0.0)};answers.put(k,JSONObject().put("type","choice").put("choice",selected).put("confidence",1.0).put("probabilities",p))};JSONObject().put("answers",answers).put("usage",JSONObject().put("input_tokens",0).put("output_tokens",0))}
  try {
   db.notifications().insert(row);ConversationPipeline(app,db,engine,{}, {now}).process(row)
   val entries=db.structured().observe().first();assertEquals(1,entries.size);assertTrue(entries.single().context!!.needsContext);assertEquals("inbound_only",entries.single().context!!.contextCompleteness)
   val device=UiDevice.getInstance(inst);device.wakeUp()
   fun find(tag:String)=device.wait(Until.findObject(By.res(tag)),15000) ?: error("Missing $tag")
   ActivityScenario.launch(MainActivity::class.java).use{scenario->
    scenario.onActivity{a->a.setContent{InboxTheme{SmartDashboard(app,true,Modifier.fillMaxSize().systemBarsPadding().semantics{testTagsAsResourceId=true},{},fixtureEntries=entries,loadOriginal={if(it==row.snapshotId)row else null})}};a.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
    find("smart_item_${row.snapshotId}");assertTrue(device.hasObject(By.textContains("일정 관련 · 확인 필요")));assertFalse(device.hasObject(By.text("약속 확정")))
    device.takeScreenshot(File(app.cacheDir,"conversation-card.png"))
    find("smart_item_${row.snapshotId}").visibleBounds.let{device.click(it.left+40,it.centerY())};assertTrue(device.wait(Until.hasObject(By.textContains("수신 메시지만 확인")),5000))
    assertFalse(device.hasObject(By.text(row.text!!)));find("smart_original").click();assertTrue(device.wait(Until.hasObject(By.text(row.text!!)),5000))
    device.takeScreenshot(File(app.cacheDir,"conversation-detail.png"));scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
   }
  }finally{db.close()}
 }
}
