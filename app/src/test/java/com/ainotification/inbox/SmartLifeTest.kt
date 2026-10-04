package com.ainotification.inbox

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class SmartLifeTest {
 private fun row(text:String,id:String="life")=previewNotifications()[0].copy(snapshotId=id,packageName="synthetic.shop",appLabel="테스트서비스",title="안내",text=text,bigText=null,messagesJson="[]",isGroupSummary=false,serviceType=null,notificationCategory=null,conversationIdentity=null)
 private fun entry(type:String,amount:Long,at:Long,status:String="completed",recurring:Boolean=false):StructuredEntry {
  val r=row("",type)
  return StructuredEntry(StructuredEvent(type,type,"money",type,"테스트","synthetic",at,false,at,at),MoneyEvent(type,type,type,directionFor(type),amount,99999999L,"가맹점",null,"테스트",null,null,at,"테스트",1.0,1.0,"complete",true,at,at,recurring,status))
 }
 @Test fun spendingDoesNotAddWithdrawalsBalanceOrBills(){
  val now=System.currentTimeMillis()
  val rows=listOf(entry("payment",6500,now),entry("withdrawal",6500,now),entry("deposit",50000,now),entry("bill",80000,now,"pending"),entry("transfer_in_pending",2000,now,"pending"))
  val s=summarizeMoney(rows,"이번 주",now)
  assertEquals(6500L,s.spending);assertEquals(50000L,s.income);assertEquals(6500L,s.accountOut);assertNull(s.comparison)
  assertTrue(matchesMoney(rows[1],"계좌 입출금","전체"));assertFalse(matchesMoney(rows[3],"거래 내역","전체"));assertTrue(matchesMoney(rows[3],"내야 할 돈","전체"))
 }
 @Test fun lifecycleAndMethodsAreExplicit(){
  val r=row("삼성카드 정기결제 체크카드 승인 6500원")
  val m=moneyLifecycle(r,entry("recurring_payment",6500,1).money!!)
  assertEquals("payment",m.transactionType);assertTrue(m.recurring);assertEquals("체크카드",m.paymentMethod)
  assertEquals("pending",moneyLifecycle(row("청구서"),entry("billing",1000,1).money!!).status)
 }
 @Test fun strictBillNeedsLabeledAmountAndNoDialogue(){
  val r=row("청구서\n청구금액: 30,000원\n청구처: 아파트\n청구번호: bill-001\n납부기한: 2026-10-10")
  val m=explicitObligation(r,1)!!;assertEquals(30000L,m.transactionAmount);assertEquals("pending",m.status);assertNotNull(m.dueAt)
  assertNull(explicitObligation(r.copy(packageName="com.kakao.talk"),1))
  assertNull(explicitObligation(row("청구서 상품 가격 30000원"),1))
 }
 @Test fun deliveryRequiresEntityAndKeepsReference(){
  val r=row("배송 중\n상품명: 정수기 필터\n주문번호: order-001\n배송사: 테스트택배")
  val l=extractLife(r)!!;assertEquals("shipping",l.status);assertEquals("정수기 필터",l.itemName);assertEquals("order-001",l.referenceKey)
  assertNull(extractLife(row("배송 중인 것 같아")))
  assertEquals("delivered",extractLife(row("주문하신 상품 배송이 완료되었습니다.").copy(packageName="com.coupang.mobile"))!!.status)
  assertNull(extractLife(row("배송이 완료되었습니다.").copy(packageName="com.coupang.flex.mobile")))
  assertNull(extractLife(r.copy(packageName="com.kakao.talk")))
 }
 @Test fun appointmentsPreserveUnspecifiedDateAndDoNotInventYear(){
  val l=extractLife(row("예약 안내\n예약명: 치과\n예약일시: 9일 11시"))!!
  assertEquals("candidate",l.status);assertNull(l.scheduledAt);assertEquals("9일 11시",l.dateText)
  assertNull(extractLife(row("오늘 저녁 뭐 먹을까?")))
 }
 @Test fun latestStateGroupsOnlySameReferenceAndProvider(){
  val now=System.currentTimeMillis();val old=entry("payment",1,now).copy(event=StructuredEvent("old","old","delivery","상품","샵","shop",now,false,now,now),money=null,life=LifeEvent("old","delivery","shipping","상품","샵","order-a"))
  val newer=old.copy(event=old.event.copy(id="new",sourceNotificationId="new",observedAt=now+1),life=old.life!!.copy(id="new",status="delivered"))
  val other=old.copy(event=old.event.copy(id="other",sourceNotificationId="other"),life=old.life.copy(id="other",provider="다른샵"))
  assertEquals(2,currentInformation(listOf(old,newer,other)).size)
  assertFalse(validSmartEntry(old.copy(life=null)))
 }
 @Test fun settlementRequiresUniqueMatchingReferenceAndAccount()=kotlinx.coroutines.runBlocking {
  val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
  val db=androidx.room.Room.inMemoryDatabaseBuilder(context,InboxDatabase::class.java).build()
  suspend fun save(id:String,type:String,status:String,ref:String="bill-123",account:String="123-**-456",amount:Long=30000):MoneyEvent {
   val base=entry(type,amount,100,status)
   val event=base.event.copy(id=id,sourceNotificationId=id)
   val m=base.money!!.copy(id=id,sourceNotificationId=id,referenceKey=ref,accountHint=account)
   db.notifications().insert(row("",id));db.structured().insertEvent(event);db.structured().saveMoney(m)
   return m
  }
  try {
   save("request","bill","pending")
   settleObligations(db,save("wrongAccount","payment","completed",account="999-**-000"))
   assertEquals("pending",db.structured().money("request")!!.status)
   settleObligations(db,save("wrongRef","payment","completed",ref="other-123"))
   assertEquals("pending",db.structured().money("request")!!.status)
   settleObligations(db,save("payment","payment","completed"))
   assertEquals("completed",db.structured().money("request")!!.status)
   assertEquals("payment",db.structured().money("request")!!.settledByTransactionEventId)
   assertTrue(db.structured().event("request")!!.isChanged)
   save("duplicate1","bill","pending",ref="duplicate-123")
   save("duplicate2","bill","pending",ref="duplicate-123")
   settleObligations(db,save("ambiguous","payment","completed",ref="duplicate-123"))
   assertEquals("pending",db.structured().money("duplicate1")!!.status)
   save("earlierPayment","payment","completed",ref="reverse-123")
   settleObligations(db,save("lateRequest","bill","pending",ref="reverse-123"))
   assertEquals("earlierPayment",db.structured().money("lateRequest")!!.settledByTransactionEventId)
  } finally {db.close()}
 }
 @Test fun dateOnlyAppointmentDoesNotInventTime(){
  val l=extractLife(row("예약 확정\n예약일시: 2026-10-09"))!!
  assertNull(l.scheduledAt);assertEquals("2026-10-09",l.dateText)
 }

 @Test fun conversationalRequestStillRequiresContextGateAndCurrentAmount(){
  val d=ConversationDecision("money","likely",false,false,false,.99,ConversationValue("V0","r","30,000원",30000),null,true)
  assertTrue(isContextualPaymentRequest("회비 30,000원 입금 부탁드립니다.","r",d))
  assertFalse(isContextualPaymentRequest("예를 들어 30,000원 입금 부탁드립니다.","r",d))
  assertFalse(isContextualPaymentRequest("30,000원 보내주세요","other",d))
  assertFalse(isContextualPaymentRequest("30,000원 보내주세요","r",d.copy(intent="none")))
  assertFalse(isContextualPaymentRequest("30,000원 보냈어요","r",d))
 }

}
