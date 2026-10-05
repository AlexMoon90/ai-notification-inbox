package com.ainotification.inbox

import org.junit.Test
import org.junit.Assert.*
class DebitConfirmationTest {
 private fun row(id:String,type:String,at:Long=1000,amount:Long=6500):StructuredEntry=StructuredEntry(
  StructuredEvent(id,id,"money",id,"테스트",if(type=="payment")"com.samsung.android.spay" else "bank",at,false,at,at),
  MoneyEvent(id,id,type,directionFor(type),amount,null,"가맹점",null,null,null,null,at,"테스트",1.0,1.0,"complete",true,at,at))
 @Test fun onlyUserConfirmationClassifiesAndKeepsAccountEvidence(){
  val rows=listOf(row("p","payment"),row("w","withdrawal",2000))
  val pairs=debitCandidates(rows);assertEquals(1,pairs.size)
  assertEquals(rows,applyDebitConfirmations(rows,pairs,emptyMap()))
  assertEquals(rows,applyDebitConfirmations(rows,pairs,mapOf(pairs.single().key to "separate")))
  val confirmed=applyDebitConfirmations(rows,pairs,mapOf(pairs.single().key to "debit"))
  assertEquals("체크카드",confirmed[0].money!!.paymentMethod)
  assertEquals("체크카드 연결 출금",confirmed[1].money!!.paymentMethod)
  assertTrue(matchesMoney(confirmed[1],"계좌 입출금","전체"))
  val summary=summarizeMoney(confirmed,"전체",3000)
  assertEquals(6500L,summary.spending);assertEquals(listOf("체크카드" to 6500L),summary.methods)
  assertEquals(6500L,summary.accountOut)
  assertEquals(rows,applyDebitConfirmations(rows,pairs,emptyMap()))
 }
 @Test fun ambiguousOrDistantOrDifferentAmountsAreNeverProposed(){
  val p=row("p","payment");val w=row("w","withdrawal")
  assertTrue(debitCandidates(listOf(p,w,row("w2","withdrawal"))).isEmpty())
  assertTrue(debitCandidates(listOf(p,w,row("p2","payment"))).isEmpty())
  assertTrue(debitCandidates(listOf(p,row("w","withdrawal",121001))).isEmpty())
  assertTrue(debitCandidates(listOf(p,row("w","withdrawal",amount=6501))).isEmpty())
  assertTrue(debitCandidates(listOf(p.copy(money=p.money!!.copy(paymentMethod="신용카드")),w)).isEmpty())
  assertTrue(debitCandidates(listOf(p.copy(event=p.event.copy(sourcePackage="other.wallet")),w)).isEmpty())
 }
 @Test fun changedEvidenceDoesNotReuseAnswer(){
  val rows=listOf(row("p","payment"),row("w","withdrawal"))
  val old=debitCandidates(rows).single()
  val changed=rows.map{it.copy(money=it.money!!.copy(transactionAmount=7000))}
  assertNotEquals(old.key,debitCandidates(changed).single().key)
  assertEquals(changed,applyDebitConfirmations(changed,debitCandidates(changed),mapOf(old.key to "debit")))
 }
 private fun pair(suffix:String,account:String?="123-**-456",bank:String?="테스트은행"):DebitCandidate {
  val p=row("p$suffix","payment")
  val w=row("w$suffix","withdrawal").let{it.copy(money=it.money!!.copy(provider=bank,accountHint=account))}
  return DebitCandidate(p,w)
 }
 @Test fun learnedAccountAppliesToNewTransactionsButNotOtherAccounts(){
  val first=pair("1");val next=pair("2");val rules=setOf(first.accountRuleKey!!)
  assertNotEquals(first.key,next.key)
  assertEquals("debit",debitDecision(next,emptyMap(),rules))
  assertEquals("debit",debitDecision(pair("3","123**456"),emptyMap(),rules))
  assertNull(debitDecision(pair("4","123-**-789"),emptyMap(),rules))
  assertNull(debitDecision(pair("5",bank="다른은행"),emptyMap(),rules))
  assertNull(debitDecision(pair("6",account=null),emptyMap(),rules))
  assertNull(debitDecision(pair("7",bank=null),emptyMap(),rules))
  assertEquals("separate",debitDecision(next,mapOf(next.key to "separate"),rules))
  val rows=listOf(next.payment,next.withdrawal)
  assertEquals("체크카드",applyDebitConfirmations(rows,listOf(next),emptyMap(),rules)[0].money!!.paymentMethod)
  assertEquals(rows,applyDebitConfirmations(rows,listOf(next),emptyMap(),emptySet()))
 }
 @Test fun learnedAccountStillRequiresPairedAmountAndTime(){
  val p=pair("1");val rules=setOf(p.accountRuleKey!!)
  val lone=listOf(p.payment)
  assertEquals(lone,applyDebitConfirmations(lone,debitCandidates(lone),emptyMap(),rules))
  val distant=listOf(p.payment,p.withdrawal.copy(money=p.withdrawal.money!!.copy(occurredAt=200000)))
  assertEquals(distant,applyDebitConfirmations(distant,debitCandidates(distant),emptyMap(),rules))
 }

 @Test fun guideExplainsUnmatchedWithdrawalsWithoutGuessingMerchantOrPerson(){
  val now=1_000_000L
  val withdrawal=row("w","withdrawal",now-180000)
  assertTrue(needsCardApprovalGuide(listOf(withdrawal),now))
  assertTrue(needsCardApprovalGuide(listOf(withdrawal.copy(money=withdrawal.money!!.copy(merchant=null,counterparty="홍길동"))),now))
  assertFalse(needsCardApprovalGuide(listOf(withdrawal.copy(money=withdrawal.money!!.copy(paymentMethod="체크카드 연결 출금"))),now))
  assertFalse(needsCardApprovalGuide(listOf(withdrawal,row("p","payment",now-180000)),now))
  assertFalse(needsCardApprovalGuide(listOf(row("new","withdrawal",now-1000)),now))
  assertFalse(needsCardApprovalGuide(listOf(row("old","withdrawal",now-31L*86400000)),now))
  assertFalse(needsCardApprovalGuide(listOf(row("deposit","deposit",now-180000)),now))
  assertFalse(needsCardApprovalGuide(emptyList(),now))
 }

}
