package com.ainotification.inbox

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class FinancialReceiptTest {
 private fun row(text:String,title:String="은행 알림",app:String="메시지")=previewNotifications()[0].copy(snapshotId="receipt",title=title,text=text,bigText=null,messagesJson="[]",appLabel=app,isGroupSummary=false,availableFields="",packageName="synthetic.bank")
 @Test fun sameReceiptWorksAcrossTransports(){for(app in listOf("메시지","은행앱","카카오톡")){
  val e=explicitFinancialReceipt(row("[Web발신]\n<은행>10/04 출금 35,000 잔액 1,253,400원",app=app),1)!!
  assertEquals("withdrawal",e.transactionType);assertEquals(35000L,e.transactionAmount);assertEquals(1253400L,e.balanceAfter);assertEquals("complete",e.extractionStatus)
 }}
 @Test fun depositsAndBalanceAreDifferent(){val e=explicitFinancialReceipt(row("[Web발신]\n은행 입금 50,000 잔액 900,000원"),1)!!;assertEquals(50000L,e.transactionAmount);assertEquals("deposit",e.transactionType)}
 @Test fun wonPrefixInWalletTitle(){val e=explicitFinancialReceipt(row("테스트 가맹점",title="₩6,500 결제 알림",app="삼성 월렛"),1)!!;assertEquals(6500L,e.transactionAmount);assertEquals("payment",e.transactionType)}
 @Test fun cardApprovalAndLimit(){val e=explicitFinancialReceipt(row("삼성카드 승인\n6,500원\n한도 900,000원"),1)!!;assertEquals(6500L,e.transactionAmount)}
 @Test fun neverPromotePromisesAdsOrAmbiguousReceipts(){for(s in listOf("은행 입금 50000 잔액 100000원 예시입니다","5만원 보냈어요","[Web발신] 은행 입금 예정 50000 잔액 100000원","삼성카드 6500원 결제 시 할인","삼성카드 승인 6500원 7000원","삼성카드 승인 거절 6500원","[Web발신] 은행 출금 실패 35000 잔액 900000원"))assertNull(s,explicitFinancialReceipt(row(s),1))}
 @Test fun amountUnitsAndDayOnlyDateAreRetained(){assertEquals(50000L,moneyCandidates(row("5만원")).amounts.single().amount);val w=ConversationWindow(ContextEvidence("x","9일 11시가 괜찮을 것 같아요",1));assertEquals("9일 11시",conversationValues(w).single().text)}
 @Test fun phoneIsOnlyWeakRelationshipEvidence(){val w=ConversationWindow(ContextEvidence("x","9일 11시가 괜찮을 것 같아요",1,sourceLabel="⁨010-0000-0000⁩"));val engine=JevEngine(androidx.test.core.app.ApplicationProvider.getApplicationContext());assertTrue(conversationState(w,engine).getBoolean("senderLabelIsPhone"));assertTrue(conversationQuestions(w).getJSONObject("relationship").getString("instructions").contains("WEAK"))}
 @Test fun literalPartiesAreShownInHeading(){
  assertEquals("새마을금고",receiptProvider("<새마을금고>123456**7"))
  val incoming=explicitFinancialReceipt(row("[Web발신]\n<우리은행>123456**7 김민수 입금50,000 잔액900,000원"),1)!!
  assertEquals("김민수",incoming.counterparty);assertEquals("우리은행",incoming.provider);assertEquals("김민수 · 입금",moneyHeading(incoming))
  val outgoing=explicitFinancialReceipt(row("[Web발신]\n<우리은행>123456**7 (주)쿠팡 출금35,000 잔액900,000원"),1)!!
  assertEquals("(주)쿠팡",outgoing.counterparty)
  val card=explicitFinancialReceipt(row("CU 테스트점",title="₩6,500 결제 알림",app="삼성 월렛"),1)!!
  assertEquals("CU 테스트점",card.merchant);assertEquals("CU 테스트점 · 결제",moneyHeading(card))
 }
 @Test fun metadataIsNotCounterparty(){
  assertNull(explicitFinancialReceipt(row("삼성카드 승인\n6,500원\n잔액 900,000원"),1)!!.merchant)
  assertNull(explicitFinancialReceipt(row("[Web발신] 은행 입금 50,000 잔액 900,000원"),1)!!.counterparty)
 }
 @Test fun senderEvidenceRequiresDistinctReceiptsAndRejectsConflict(){
  val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
  context.getSharedPreferences("receipt-senders-v1",0).edit().clear().commit()
  val senders=ReceiptSenders(context)
  val sms=row("[Web발신] 은행 입금 50,000 잔액 900,000원",title="1588-0000").copy(packageName=smsPackages.first())
  val base=explicitFinancialReceipt(sms,1)!!
  senders.enrich(sms,base.copy(provider="우리은행"));senders.enrich(sms,base.copy(provider="우리은행"))
  assertNull(senders.enrich(sms.copy(snapshotId="unknown"),base).provider)
  senders.enrich(sms.copy(snapshotId="second"),base.copy(provider="우리은행"))
  assertEquals("우리은행",senders.enrich(sms.copy(snapshotId="third"),base).provider)
  assertNull(senders.enrich(sms.copy(title="1588-1111"),base).provider)
  senders.enrich(sms.copy(snapshotId="conflict"),base.copy(provider="신한은행"))
  assertNull(senders.enrich(sms.copy(snapshotId="fourth"),base).provider)
 }
 @Test fun kakaoServiceReceiptsUseRecipientsPerspective(){
  fun kakao(t:String)=row(t,title="테스트 상대").copy(packageName="com.kakao.talk",isGroupConversation=false)
  val paid=kakao("50,000원을 받았어요. *(안내) 받은 분은 계좌송금도 수수료 무료로 전환 가능해요.")
  val e=explicitFinancialReceipt(paid,1)!!
  assertEquals("transfer_out",e.transactionType);assertEquals("out",e.direction);assertEquals("테스트 상대",e.counterparty)
  val arrived=explicitFinancialReceipt(kakao("50,000원을 보냈어요. 송금 받기 전까지 보낸 분은 내역 상세화면에서 취소할 수 있어요."),1)!!
  assertEquals("transfer_in_pending",arrived.transactionType);assertEquals("neutral",arrived.direction)
  assertNull(explicitFinancialReceipt(kakao("50,000원을 보냈어요."),1))
  assertNull(explicitFinancialReceipt(paid.copy(packageName="com.ainotification.inbox"),1))
  assertNull(explicitFinancialReceipt(paid.copy(isGroupConversation=true),1)!!.counterparty)
 }
 @Test fun mgPushBracketedLabelsPreserveAmountBalanceAndParty(){
  for((label,type) in listOf("입금" to "deposit","출금" to "withdrawal")) {
   val n=row("[$label] 50,000원 1234-00-00****-0 잔액 900,000원 10/04 12:30 테스트상대",title="MG새마을금고").copy(packageName="com.smg.spbs")
   val e=explicitFinancialReceipt(n,1)!!
   assertEquals(type,e.transactionType);assertEquals(50000L,e.transactionAmount);assertEquals(900000L,e.balanceAfter)
   assertEquals("테스트상대",e.counterparty);assertEquals("새마을금고",e.provider)
   assertNull(explicitFinancialReceipt(n.copy(isGroupSummary=true),1))
   assertNull(explicitFinancialReceipt(n.copy(text="[출금] 50,000원 출금 예정",bigText=null),1))
  }
 }
 @Test fun sameBankDifferentMaskedAccountsRemainDistinct(){
  val sms=row("[Web발신]\n<새마을금고>123456**7 테스트상대 입금50,000 잔액900,000원")
  val push=row("[입금] 50,000원 9000-00-00****-1 잔액 900,000원 10/04 12:30 테스트상대",title="MG새마을금고").copy(packageName="com.smg.spbs")
  val a=explicitFinancialReceipt(sms,1)!!;val b=explicitFinancialReceipt(push,1)!!
  assertEquals("123456**7",a.accountHint);assertEquals("9000-00-00****-1",b.accountHint)
  assertNotEquals(a.accountHint,b.accountHint)
  assertEquals("새마을금고 · 계좌 123456**7",moneySourceLabel(a))
  assertEquals("***-**-**5678",receiptAccountHint(row("계좌번호: 123-45-005678")))
  assertNull(receiptAccountHint(row("잔액 123456원 10/04 12:30")))
  assertNull(receiptAccountHint(row("계좌번호: 123456**7\n계좌번호: 765432**1")))
 }
 @Test fun optionalPrivateLocalAudit(){
  val input=System.getProperty("receipt.audit") ?: return
  val rows=org.json.JSONArray(java.io.File(input).readText());val found=org.json.JSONArray()
  for(i in 0 until rows.length()){
   val r=rows.getJSONObject(i);fun str(k:String)=if(r.isNull(k))null else r.getString(k)
   val n=row(str("text").orEmpty(),str("title").orEmpty(),r.getString("appLabel")).copy(snapshotId=r.getString("snapshotId"),packageName=r.getString("packageName"),bigText=str("bigText"),messagesJson=r.getString("messagesJson"),postedTime=r.getLong("postedTime"),isGroupSummary=r.getInt("isGroupSummary")!=0,notificationCategory=str("notificationCategory"))
   val e=explicitFinancialReceipt(n,1) ?: continue
   found.put(org.json.JSONObject().put("id",n.snapshotId).put("app",n.appLabel).put("type",e.transactionType).put("amount",e.transactionAmount).put("balance",e.balanceAfter))
  }
  java.io.File("/private/tmp/inbox-reclassify/local-results.json").writeText(found.toString())
 }

}
