package com.ainotification.inbox

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class ScheduleMessagesTest {
 private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
 private fun row(text:String,id:String="schedule")=previewNotifications()[0].copy(snapshotId=id,packageName="com.samsung.android.messaging",appLabel="메시지",isGroupSummary=false,isGroupConversation=false,title="테스트",text=text,bigText=null,messagesJson="[]",availableFields="",notificationCategory="msg",serviceType="sms",postedTime=100,capturedTime=100)
 @Test fun alternativeDatesRemainOneProposalWithoutInventedStart(){
  val e=extractScheduleInformation(row("이번 주는 일정이 안 되고 다음 주 목요일 12시, 토요일 11시반경 가능합니다"))!!
  assertEquals("proposed",e.status);assertNull(e.scheduledAt)
  assertEquals("다음 주 목요일 12시 · 토요일 11시반경",e.dateText)
 }
 @Test fun changeRequestKeepsOldTimeAndNewPeriodWithoutClaimingAgreement(){
  val e=extractScheduleInformation(row("오늘 2시반에 케어예약 되어 있는데 시간을 다음주로 변경해야 해서 연락 드렸어요"))!!
  assertEquals("change_requested",e.status);assertNull(e.scheduledAt)
  assertEquals("오늘 2시반 · 다음주",e.dateText)
 }
 @Test fun cancellationAndRequestAreDistinctEvenInGroup(){
  val cancelled=extractScheduleInformation(row("내일 오후 3시 회의가 취소되었습니다"))!!
  assertEquals("cancelled",cancelled.status)
  val request=row("내일 오후 3시 회의 취소해주세요").copy(packageName="com.kakao.talk",isGroupConversation=true)
  assertEquals("cancellation_requested",extractScheduleInformation(request)!!.status)
  assertEquals("cancellation_requested",extractScheduleInformation(row("예약 취소 부탁드립니다"))!!.status)
 }
 @Test fun noDateCancellationIsVisibleButDoesNotInventLinkOrTime(){
  val e=extractScheduleInformation(row("예약이 취소되었습니다"))!!
  assertEquals("cancelled",e.status);assertNull(e.referenceKey);assertNull(e.scheduledAt)
 }
 @Test fun strongGroupNoticeStillHasConfirmedTime(){
  val e=extractScheduleInformation(row("내일 오후 3시 회의합니다").copy(packageName="com.kakao.talk",isGroupConversation=true))!!
  assertEquals("confirmed",e.status);assertNotNull(e.scheduledAt)
 }
 @Test fun negativesAdsExamplesAndObjectlessRepliesAreNotPromoted(){
  for(t in listOf("회의 취소하지 마세요","내일 예약 취소 안합니다","내일 회의 변경 없습니다","만약 회의가 취소되면 연락주세요","예를 들면 내일 예약 취소해주세요","(광고) 내일 미팅 예약 가능","내일 배송 가능합니다","네 그때요","일정 공유 부탁합니다","스케쥴 시작이 늦습니다","오전 9:00 알람 취소하려면 선택하세요","내일 회의가 취소됐다고 합니다")){assertNull(t,extractScheduleMessage(row(t)));assertNull(t,extractScheduleInformation(row(t).copy(packageName="com.kakao.talk",isGroupConversation=true)))}
 }
 @Test fun durationsAndCouponExpiryAreNotAppointmentSlots(){
  for(t in listOf("GPU는 매달 24시간 사용가능합니다", "쿠폰 유효기간 : 2026.10.12 23:59 까지 사용 가능", "내일 할인 혜택 이용 가능합니다", "6.1sol 체감이 어떠신가요 5.6sol보다 나을까요?", "현재 단계별 모델들도 5.6인 것은 괜찮네요", "도구가 있으면 6.1솔 괜찮아서 쓰는 게 좋겠네요"))assertNull(t,extractScheduleInformation(row(t)))
 }
 @Test fun reschedulingAcrossLinesIsAChangeRequest(){
  val e=extractScheduleInformation(row("케어 11시 예약했는데요\n오늘 안될 것 같네요\n다음에 일정 다시 잡고 진행할게요"))!!
  assertEquals("change_requested",e.status);assertNull(e.scheduledAt)
 }
 @Test fun slashDatesAndNextLineDateAreRetainedWithoutUnavailableWeek(){
  assertEquals("10/14",extractScheduleMessage(row("다음주는 좀 어려울 것 같고 혹시 10/14 가능할지요"))!!.dateText)
  assertEquals("토요일 · 10일",extractScheduleMessage(row("토요일 가능할까요?? 10일이요"))!!.dateText)
 }
 @Test fun filtersSeparateRequestedChangeAndCancellationFromConfirmedUpcoming(){
  val r=row("내일 예약 취소해주세요");val l=extractScheduleInformation(r)!!
  val e=StructuredEntry(StructuredEvent(l.id,r.snapshotId,"schedule",l.title,r.appLabel,r.packageName,100,false,100,100),null,life=l)
  assertTrue(categoryFilter(e,"취소",100));assertTrue(categoryFilter(e,"확인 필요",100));assertFalse(categoryFilter(e,"예정",100))
 }
 @Test fun exhaustedBudgetDoesNotBlockNewLiteralScheduleAndRepairIsIdempotent()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,InboxDatabase::class.java).build()
  val prefs=context.getSharedPreferences("money-extraction-budget",0)
  try {
   prefs.edit().putInt("${LocalDate.now()}:judgment",200).commit()
   val proposal=row("다음 주 목요일 12시, 토요일 11시반 가능합니다","a")
   val cancellation=row("예약 취소해주세요","b")
   db.notifications().insert(proposal);db.notifications().insert(cancellation)
   db.structured().saveProcessing(StructuredProcessing(cancellation.snapshotId,"retry",999999,3))
   MoneyPipeline(context,db,JevEngine(context){error("No external AI")},clock={100}).process(proposal)
   assertEquals("schedule",db.structured().event("event:a")!!.category)
   assertEquals(1,repairScheduleMessages(db,100));assertEquals(0,repairScheduleMessages(db,100))
   assertEquals("done",db.structured().processing("b")!!.status)
   assertEquals(cancellation,db.notifications().find("b"))
   assertTrue(db.structured().event("event:b")!!.isChanged)
  }finally{prefs.edit().clear().commit();db.close()}
 }
}
