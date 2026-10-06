package com.ainotification.inbox

import android.app.*
import android.content.Intent
import androidx.room.withTransaction
import org.json.JSONArray
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BadgeScheduleDeviceTest {
 @Test fun readAcknowledgementRemovesOnlyReadRelay() {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as InboxApplication
  val manager=app.getSystemService(NotificationManager::class.java)
  val channel="badge_verification_v1";val prefix="synthetic-badge-${System.nanoTime()}"
  val ids=listOf("$prefix-read","$prefix-unread")
  manager.createNotificationChannel(NotificationChannel(channel,"읽음 검증",NotificationManager.IMPORTANCE_LOW).apply{setShowBadge(true);setSound(null,null);enableVibration(false)})
  try {
   ids.forEach{id->manager.notify("now:$id",1,Notification.Builder(app,channel).setSmallIcon(R.drawable.ic_nowset_notification).setContentTitle("NOWSET 읽음 검증").setContentText("합성 알림").build())}
   Thread.sleep(300)
   assertTrue(manager.activeNotifications.any{it.tag=="now:${ids[0]}"})
   assertTrue(manager.activeNotifications.any{it.tag=="now:${ids[1]}"})
   app.nowAlerts.acknowledge(listOf(ids[0]));Thread.sleep(300)
   assertFalse(manager.activeNotifications.any{it.tag=="now:${ids[0]}"})
   assertTrue(manager.activeNotifications.any{it.tag=="now:${ids[1]}"})
   assertTrue(NowAlertReadState(app).isRead(ids[0]))
  }finally{
   ids.forEach{manager.cancel("now:$it",1)}
   app.getSharedPreferences("now-alert-read",0).edit().apply{ids.forEach{remove(it)}}.commit()
   manager.deleteNotificationChannel(channel)
  }
 }
 @Test fun actualNowDetailAcknowledgesItsSystemAlert():Unit=runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as InboxApplication
  val manager=app.getSystemService(NotificationManager::class.java)
  val id="synthetic-detail-${System.nanoTime()}";val channel="badge_detail_verification_v1"
  val now=System.currentTimeMillis()
  val row=previewNotifications()[0].copy(snapshotId=id,packageName=app.packageName,appLabel="NOWSET",title="읽음 연결 검증",text="합성 메시지 상세",bigText=null,messagesJson="[]",isGroupSummary=false,postedTime=now,capturedTime=now)
  manager.createNotificationChannel(NotificationChannel(channel,"상세 읽음 검증",NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null);enableVibration(false)})
  try {
   app.database.notifications().insert(row)
   app.database.structured().saveProcessing(StructuredProcessing(id,"ignored"))
   manager.notify("now:$id",1,Notification.Builder(app,channel).setSmallIcon(R.drawable.ic_nowset_notification).setContentTitle("합성 상세 읽음 검증").build())
   assertTrue(manager.activeNotifications.any{it.tag=="now:$id"})
   val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.wakeUp()
   ActivityScenario.launch<MainActivity>(Intent(app,MainActivity::class.java).putExtra("now_snapshot",id)).use{scenario->
    scenario.onActivity{it.setShowWhenLocked(true);it.setTurnScreenOn(true)}
    assertTrue(device.wait(Until.hasObject(By.text("합성 메시지 상세")),10000))
    val deadline=System.currentTimeMillis()+5000
    while(manager.activeNotifications.any{it.tag=="now:$id"} && System.currentTimeMillis()<deadline)Thread.sleep(50)
    assertFalse(manager.activeNotifications.any{it.tag=="now:$id"})
    assertTrue(NowAlertReadState(app).isRead(id))
    scenario.onActivity{it.setShowWhenLocked(false);it.setTurnScreenOn(false)}
   }
  }finally {
   manager.cancel("now:$id",1);manager.deleteNotificationChannel(channel)
   app.database.openHelper.writableDatabase.execSQL("DELETE FROM notifications WHERE snapshotId=?",arrayOf(id))
   app.getSharedPreferences("now-alert-read",0).edit().remove(id).commit()
  }
 }
 /** Optional one-run audit plan contains only IDs/timestamps created during this repair, never originals. */
 @Test fun applyThisRunsScheduleAudit():Unit=runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as InboxApplication
  val plan=File(app.filesDir,"schedule-repair-audit.json")
  if(!plan.exists())return@runBlocking
  val expected=JSONArray(plan.readText());require(expected.length()<=50)
  val dao=app.database.structured();var removed=0;var updated=0;var skipped=0
  for(i in 0 until expected.length())app.database.withTransaction {
   val item=expected.getJSONObject(i);val id=item.getString("id")
   val e=dao.event(id) ?: return@withTransaction
   val status=app.database.openHelper.readableDatabase.query("SELECT status FROM life_events WHERE id=?",arrayOf(id)).use{if(it.moveToFirst())it.getString(0) else null}
   if(e.category!="schedule" || e.createdAt!=item.getLong("createdAt") || e.updatedAt!=item.getLong("updatedAt") || status!=item.getString("status")){skipped++;return@withTransaction}
   val row=app.database.notifications().find(e.sourceNotificationId) ?: return@withTransaction
   val life=extractScheduleInformation(row)
   if(life==null){dao.removeEvent(row.snapshotId);dao.saveProcessing(StructuredProcessing(row.snapshotId,"ignored"));removed++}
   else {dao.saveLife(life);dao.insertEvent(e.copy(title=life.title,isChanged=life.status in setOf("changed","change_requested","cancelled","cancellation_requested")));updated++}
  }
  File(app.filesDir,"schedule-repair-audit-result.json").writeText(JSONObject().put("removedDerivedOnly",removed).put("updated",updated).put("skippedModified",skipped).toString())
  plan.delete()
 }
 @Test fun scheduleRepairIsIdempotent():Unit=runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as InboxApplication
  val anchor=System.currentTimeMillis()
  val first=repairScheduleMessages(app.database,anchor)
  val second=repairScheduleMessages(app.database,anchor)
  assertEquals(0,second)
  File(app.filesDir,"schedule-message-repair-result.json").writeText(JSONObject().put("firstPass",first).put("secondPass",second).put("externalReplayCalls",0).toString())
 }
 @Test fun scheduleProposalAndCancellationAreVisible() {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as InboxApplication
  val now=System.currentTimeMillis()
  fun entry(id:String,status:String,title:String)=StructuredEntry(StructuredEvent(id,id,"schedule",title,"테스트 문자","synthetic.sms",now,false,now,now),null,life=LifeEvent(id,"schedule",status,title,"테스트 문자",dateText="내일 오후 2시"))
  val entries=listOf(entry("schedule-proposal","proposed","일정 제안·조율"),entry("schedule-change","change_requested","일정 변경 요청"),entry("schedule-cancel","cancelled","일정 취소"))
  val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.wakeUp()
  fun find(tag:String)=device.wait(Until.findObject(By.res(tag)),10000) ?: error("Missing $tag")
  ActivityScenario.launch(DesignPreviewActivity::class.java).use{scenario->
   scenario.onActivity{a->a.setShowWhenLocked(true);a.setTurnScreenOn(true);a.setContent{InboxTheme{SmartDashboard(app,true,Modifier.fillMaxSize().systemBarsPadding().semantics{testTagsAsResourceId=true},{},entries,{null})}}}
   find("smart_category_schedule").click()
   find("smart_item_schedule-proposal");find("smart_item_schedule-change");find("smart_item_schedule-cancel")
   assertTrue(device.hasObject(By.text("일정 조율")))
   assertTrue(device.hasObject(By.text("변경 요청")))
   scenario.onActivity{it.setShowWhenLocked(false);it.setTurnScreenOn(false)}
  }
 }
}
