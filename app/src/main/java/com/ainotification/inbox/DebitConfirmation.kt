package com.ainotification.inbox

internal data class DebitCandidate(val payment:StructuredEntry,val withdrawal:StructuredEntry) {
 // Keep the observed masking; never equate different visible account identifiers.
 val accountRuleKey:String? get(){
  val bank=withdrawal.money?.provider?.trim()?.takeIf{it.isNotEmpty()} ?: return null
  val account=withdrawal.money?.accountHint?.replace(Regex("[\\s-]"),"")?.takeIf{it.count(Char::isDigit)>=3} ?: return null
  return stableMessageIdentity("${payment.event.sourcePackage}|$bank|$account")
 }
 val key:String get()=stableMessageIdentity("${payment.event.id}|${withdrawal.event.id}|${payment.money?.transactionAmount}|${eventTime(payment)}|${eventTime(withdrawal)}")
}
/** Similar timing is only a question, never proof. Require a unique match in both directions. */
internal fun debitCandidates(rows:List<StructuredEntry>):List<DebitCandidate> {
 val payments=rows.filter{it.event.sourcePackage=="com.samsung.android.spay" && it.money?.let{m->m.transactionType=="payment" && m.status=="completed" && m.extractionStatus=="complete" && m.transactionAmount!=null && m.transactionAmount>0 && m.paymentMethod !in setOf("신용카드","체크카드")}==true}
 val withdrawals=rows.filter{it.money?.let{m->m.transactionType=="withdrawal" && m.status=="completed" && m.extractionStatus=="complete"}==true}
 val matches=payments.flatMap{p->withdrawals.filter{w->p.money!!.transactionAmount==w.money!!.transactionAmount && kotlin.math.abs(eventTime(p)-eventTime(w))<=120_000}.map{DebitCandidate(p,it)}}
 return matches.filter{pair->matches.count{it.payment.event.id==pair.payment.event.id}==1 && matches.count{it.withdrawal.event.id==pair.withdrawal.event.id}==1}
}
internal fun applyDebitConfirmations(rows:List<StructuredEntry>,pairs:List<DebitCandidate>,answers:Map<String,String>,accountRules:Set<String> = emptySet()):List<StructuredEntry> {
 val confirmed=pairs.filter{debitDecision(it,answers,accountRules)=="debit"}
 val payments=confirmed.map{it.payment.event.id}.toSet()
 val withdrawals=confirmed.map{it.withdrawal.event.id}.toSet()
 return rows.map{e->when(e.event.id){
  in payments->e.copy(money=e.money!!.copy(paymentMethod="체크카드"))
  in withdrawals->e.copy(money=e.money!!.copy(paymentMethod="체크카드 연결 출금"))
  else->e
 }}
}

/** A transaction-specific rejection overrides the learned account rule. */
internal fun debitDecision(pair:DebitCandidate,answers:Map<String,String>,accountRules:Set<String>):String? =
 answers[pair.key] ?: if(pair.accountRuleKey in accountRules) "debit" else null
