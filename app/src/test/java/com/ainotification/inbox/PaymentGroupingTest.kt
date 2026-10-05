package com.ainotification.inbox

import org.junit.Test
import org.junit.Assert.*
class PaymentGroupingTest {
 private fun entry(id:String,type:String="payment",merchant:String?="테스트 마트",account:String?=null,at:Long=1000,method:String?=null)=StructuredEntry(
  StructuredEvent(id,id,"money",id,id,if(id.startsWith("wallet"))"com.samsung.android.spay" else "source.$id",at,false,at,at),
  MoneyEvent(id,id,type,directionFor(type),6500,null,if(type=="payment")merchant else null,if(type=="withdrawal")merchant else null,"은행",method,account,at,id,1.0,1.0,"complete",true,at,at))
 private val wallet get()=entry("wallet")
 private val card get()=entry("card",method="체크카드")
 private val withdrawal get()=entry("bank","withdrawal",account="123**456")
 @Test fun threeReceiptsBecomeOnePaymentWithAllOriginalsAndAccountFlow(){
  val rows=listOf(wallet,card,withdrawal)
  val result=groupCardPayments(rows)
  assertEquals(1,result.rows.size);assertEquals("card",result.rows.single().event.id)
  assertEquals(setOf("wallet","card","bank"),result.sources["card"]!!.toSet())
  assertEquals(3,result.accountRows.size)
  assertEquals("체크카드 연결 출금",result.accountRows.last().money!!.paymentMethod)
  val summary=summarizeMoney(result.rows,"전체",2000,result.accountRows)
  assertEquals(6500L,summary.spending);assertEquals(6500L,summary.accountOut)
  assertEquals(listOf("체크카드" to 6500L),summary.methods);assertEquals(1,summary.count)
  assertNull(rows.last().money!!.paymentMethod)
 }
 @Test fun receiptArrivalOrderDoesNotChangeGroup(){
  for(rows in listOf(listOf(wallet,card,withdrawal),listOf(withdrawal,wallet,card),listOf(card,withdrawal,wallet)))assertEquals("card",groupCardPayments(rows).rows.single().event.id)
  assertEquals(1,groupCardPayments(listOf(wallet,card)).rows.size)
  assertEquals(1,groupCardPayments(listOf(card,withdrawal)).rows.size)
  assertEquals(2,groupCardPayments(listOf(wallet,withdrawal)).rows.size)
 }
 @Test fun confirmedAccountConnectsWithdrawalWithoutMerchant(){
  val w=withdrawal.copy(money=withdrawal.money!!.copy(counterparty=null))
  val pair=DebitCandidate(wallet,w)
  assertEquals(1,groupCardPayments(listOf(wallet,card,w),listOf(pair)).rows.size)
  assertEquals(2,groupCardPayments(listOf(wallet,card,w)).rows.size)
 }
 @Test fun differentMerchantAmountTimeOrCardHintCannotMergeApprovals(){
  val mismatches=listOf(card.copy(money=card.money!!.copy(merchant="다른 마트")),card.copy(money=card.money!!.copy(transactionAmount=6501)),card.copy(money=card.money!!.copy(occurredAt=200000)),card.copy(money=card.money!!.copy(merchant=null)))
  for(c in mismatches)assertEquals(2,groupCardPayments(listOf(wallet,c)).rows.size)
  assertEquals(2,groupCardPayments(listOf(wallet.copy(money=wallet.money!!.copy(accountHint="111***")),card.copy(money=card.money!!.copy(accountHint="222***")))).rows.size)
 }
 @Test fun multipleAccountsAreNotGuessed(){
  val other=entry("bank2","withdrawal",account="987**654")
  val result=groupCardPayments(listOf(wallet,card,withdrawal,other))
  assertEquals(3,result.rows.size);assertEquals(2,result.sources["card"]!!.size)
 }
 @Test fun simultaneousCreditAndDebitApprovalsAreAmbiguous(){
  val credit=entry("credit",method="신용카드")
  assertEquals(3,groupCardPayments(listOf(wallet,card,credit)).rows.size)
 }
 @Test fun rejectionPreventsAccountLink(){
  val result=groupCardPayments(listOf(wallet,card,withdrawal),rejected=listOf(DebitCandidate(wallet,withdrawal)))
  assertEquals(2,result.rows.size);assertEquals(2,result.sources["card"]!!.size)
 }
 @Test fun explicitCreditNeverBecomesDebit(){
  val credit=card.copy(money=card.money!!.copy(paymentMethod="신용카드"))
  assertEquals(3,groupCardPayments(listOf(wallet,credit,withdrawal)).rows.size)
 }
}
