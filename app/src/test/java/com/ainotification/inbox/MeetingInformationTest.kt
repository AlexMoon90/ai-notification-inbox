package com.ainotification.inbox

import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.*

@org.robolectric.annotation.Config(sdk=[34],application=android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class MeetingInformationTest {
 private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
 private val posted=LocalDate.of(2026,9,30).atTime(18,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
 private fun row(text:String)=previewNotifications()[0].copy(snapshotId="meeting-test",packageName="com.kakao.talk",appLabel="카카오톡",title="테스트 팀",conversationTitle="테스트 팀",conversationIdentity="synthetic-team",isGroupConversation=true,isGroupSummary=false,notificationCategory="msg",text=text,bigText=null,messagesJson="[]",availableFields="",postedTime=posted,capturedTime=posted)
 @Test fun completeNoticeAnchorsTomorrowToCaptureDate(){
  val life=extractGroupMeeting(row("내일 오전 10시 출발미팅합니다.\n전원 필참이며 9시 50분까지 와주세요!!"))!!
  assertEquals("schedule",life.kind);assertEquals("confirmed",life.status)
  assertEquals(LocalDate.of(2026,10,1).atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),life.scheduledAt)
  assertEquals("출발미팅",life.title);assertTrue(life.dateText!!.contains("오전 10시"))
 }
 @Test fun dateOnlyMeetingIsRetainedWithoutInventedTime(){val life=extractGroupMeeting(row("내일 미팅에서 뵙겠습니다."))!!;assertEquals("candidate",life.status);assertNull(life.scheduledAt);assertEquals("2026-10-01 · 시간 미정",life.dateText)}
 @Test fun unqualifiedHourIsNotGuessed(){val life=extractGroupMeeting(row("10월 1일 10시 출발미팅합니다"))!!;assertEquals("candidate",life.status);assertNull(life.scheduledAt);assertTrue(life.dateText!!.contains("10시"))}
 @Test fun unrelatedDateCannotReplaceMeetingDate(){val life=extractGroupMeeting(row("장비 교체는 10월 20일입니다.\n내일 미팅에서 뵙겠습니다."))!!;assertTrue(life.dateText!!.startsWith("2026-10-01"))}
 @Test fun noContextOrCasualQuestionsStayWithContextualPipeline(){for(t in listOf("네 그때 봐요","내일 미팅할까요?","내일 미팅 가능하신가요?","어제 미팅 했습니다","내일 미팅은 안 합니다","광고 내일 오전 10시 미팅합니다","내일 미팅한다고 합니다"))assertNull(t,extractGroupMeeting(row(t)))}
 @Test fun noDateDoesNotBorrowOtherTopicsDate(){assertNull(extractGroupMeeting(row("10월 20일 물품이 도착합니다.\n미팅에서 뵙겠습니다.")))}
 @Test fun onlyGroupAndCompleteNotificationsUseLocalPath(){val r=row("내일 오전 10시 미팅합니다");assertNull(extractGroupMeeting(r.copy(isGroupConversation=false)));assertNull(extractGroupMeeting(r.copy(isGroupSummary=true)))}
 @Test fun ambiguousMultipleMeetingsStayContextual(){assertNull(extractGroupMeeting(row("내일 오전 10시 미팅합니다.\n모레 오후 2시 회의합니다.")))}
 @Test fun invalidDatesAndTimesDoNotCrashOrBecomeConfirmed(){assertNull(extractGroupMeeting(row("2월 30일 미팅합니다")));assertEquals("candidate",extractGroupMeeting(row("내일 29:00 미팅합니다"))!!.status)}
 @Test fun weekdayAndExplicitAfternoon(){val life=extractGroupMeeting(row("다음 주 월요일 오후 2시 회의합니다"))!!;assertEquals(LocalDate.of(2026,10,5).atTime(14,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),life.scheduledAt)}
 @Test fun repairOnlyMissingRecordsIsIdempotentAndNeverCallsAi()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,InboxDatabase::class.java).build()
  try {
   val r=row("내일 오전 10시 미팅합니다")
   db.notifications().insert(r);db.structured().saveProcessing(StructuredProcessing(r.snapshotId,"retry",posted+999999,46))
   assertEquals(1,repairGroupMeetings(db,posted+1000));assertEquals(0,repairGroupMeetings(db,posted+1000))
   assertEquals(r,db.notifications().find(r.snapshotId));assertEquals("done",db.structured().processing(r.snapshotId)!!.status)
   assertEquals("schedule",db.structured().observe().first().single().event.category)
  }finally{db.close()}
 }
 @Test fun newMeetingBypassesExhaustedAiBudget()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,InboxDatabase::class.java).build()
  try {
   val r=row("내일 오전 10시 미팅합니다");db.notifications().insert(r)
   context.getSharedPreferences("money-extraction-budget",0).edit().putInt("${LocalDate.now()}:judgment",200).commit()
   val engine=JevEngine(context){error("Meeting must not call AI")}
   MoneyPipeline(context,db,engine,clock={posted}).process(r)
   assertEquals("schedule",db.structured().observe().first().single().event.category)
  }finally{context.getSharedPreferences("money-extraction-budget",0).edit().clear().commit();db.close()}
 }
 @Test fun nowHonorsMeetingExceptionAndRoomScopeWithoutAi(){
  fun c(type:String,value:String="")=org.json.JSONObject().put("type",type).put("value",value).put("negated",false)
  val rule=org.json.JSONObject().put("id","room-rule").put("name","미팅만 표시").put("enabled",true)
   .put("scope",PolicyContract.emptyScope().put("type","SPECIFIC_CONVERSATION").put("apps",org.json.JSONArray().put("com.kakao.talk")).put("conversation_ids",org.json.JSONArray().put("synthetic-team")))
   .put("conditions",org.json.JSONArray().put(c("ANY"))).put("logic","ALL").put("action","HIDE")
   .put("exceptions",org.json.JSONArray().put(org.json.JSONObject().put("id","meeting").put("conditions",org.json.JSONArray().put(c("CONTENT","미팅 관련된 내용"))).put("logic","ANY").put("action","SHOW")))
   .put("time",PolicyContract.emptyTime()).put("source_instruction","미팅만 표시")
  val runtime=StructuredPolicyRuntime(JevEngine(context){error("Local meeting exception must not call AI")})
  val rules=org.json.JSONArray().put(rule);val r=row("내일 미팅에서 뵙겠습니다")
  assertEquals("match",runtime.classify(rules,r).getString("status"))
  assertEquals("unconfigured",runtime.classify(rules,r.copy(conversationIdentity="other-room")).getString("status"))
  rule.put("exceptions",org.json.JSONArray())
  assertEquals("outside",runtime.classify(rules,r).getString("status"))
 }

 @Test fun contextualDateCandidatesIncludeDayWithoutHour(){
  val w=ConversationWindow(ContextEvidence("date-only","내일 미팅 괜찮으세요?",posted))
  assertEquals(listOf("내일"),conversationValues(w).map{it.text})
  val withTime=w.copy(current=w.current.copy(text="내일 오후 2시 미팅 괜찮으세요?"))
  assertEquals(listOf("내일 오후 2시"),conversationValues(withTime).map{it.text})
 }

 @Test fun nextDayNumberCrossesMonthBoundary(){assertEquals(LocalDate.of(2026,10,1),meetingDate("1일",LocalDate.of(2026,9,30)))}
 @Test fun longNoticeRetainsMeetingButCannotConfirm(){val life=extractGroupMeeting(row("자료 ".repeat(180)+"\n내일 오전 10시 미팅합니다"))!!;assertEquals("candidate",life.status)}

 @Test fun attendanceOnFollowingLineSupportsDateOnlyNotice(){val life=extractGroupMeeting(row("내일 미팅 진행자 외\n모두 참석바랍니다!"))!!;assertTrue(life.dateText!!.startsWith("2026-10-01"))}
 @Test fun dotDateAndArrivalTimeArePreservedWithoutInventingStart(){val life=extractGroupMeeting(row("10.1일 팀미팅 9.40분까지 오시면됩니다"))!!;assertEquals("candidate",life.status);assertNull(life.scheduledAt);assertTrue(life.dateText!!.contains("9.40분까지 도착 안내"))}

 @Test fun dotDateCannotBeMistakenForMeetingTime(){val life=extractGroupMeeting(row("10.20일 오전 10시 미팅합니다"))!!;assertEquals("confirmed",life.status);assertTrue(life.dateText!!.contains("오전 10시"))}
 @Test fun negativeMeetingNoticeStaysContextual(){assertNull(extractGroupMeeting(row("내일 오전 10시 회의는 안합니다")))}

}
