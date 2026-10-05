package com.ainotification.inbox

internal data class PaymentProjection(
 val rows:List<StructuredEntry>,
 val accountRows:List<StructuredEntry>,
 val sources:Map<String,List<String>>,
)

/** Reversible read projection: immutable receipts remain available as evidence. */
internal fun groupCardPayments(rows:List<StructuredEntry>,confirmed:List<DebitCandidate> = emptyList(),rejected:List<DebitCandidate> = emptyList()):PaymentProjection {
 fun complete(e:StructuredEntry)=e.money?.let{it.extractionStatus=="complete" && it.status=="completed" && (it.transactionAmount ?: 0)>0}==true
 fun payment(e:StructuredEntry)=complete(e) && e.money!!.transactionType in setOf("payment","recurring_payment")
 fun wallet(e:StructuredEntry)=e.event.sourcePackage=="com.samsung.android.spay"
 fun debit(e:StructuredEntry)=payment(e) && !wallet(e) && e.money!!.paymentMethod=="체크카드"
 fun close(a:StructuredEntry,b:StructuredEntry)=a.money!!.transactionAmount==b.money!!.transactionAmount && kotlin.math.abs(eventTime(a)-eventTime(b))<=120_000
 fun party(e:StructuredEntry)=(e.money?.merchant ?: e.money?.counterparty).orEmpty().lowercase().filter{it.isLetterOrDigit()}
 fun sameParty(a:StructuredEntry,b:StructuredEntry)=party(a).isNotEmpty() && party(a)==party(b)
 fun hint(e:StructuredEntry)=e.money?.accountHint?.filterNot{it.isWhitespace() || it=='-'}
 fun unique(matches:List<Pair<StructuredEntry,StructuredEntry>>)=matches.filter{m->matches.count{it.first.event.id==m.first.event.id}==1 && matches.count{it.second.event.id==m.second.event.id}==1}
 val parent=rows.associate{it.event.id to it.event.id}.toMutableMap()
 fun root(id:String):String {var r=id;while(parent[r]!=r){r=parent.getValue(r)};return r}
 fun join(a:String,b:String){if(a in parent && b in parent)parent[root(b)]=root(a)}
 val wallets=rows.filter{payment(it)&&wallet(it)&&it.money!!.paymentMethod!="신용카드"}
 val cards=rows.filter(::debit)
 // Two payment approvals need the same merchant, not merely the same amount/time.
 unique(wallets.flatMap{w->rows.filter{payment(it)&&!wallet(it)}.filter{c->close(w,c)&&sameParty(w,c) && (hint(w)==null || hint(c)==null || hint(w)==hint(c))}.map{w to it}})
  .filter{(_,c)->debit(c)}.forEach{(w,c)->join(c.event.id,w.event.id)}
 val blocked=rejected.flatMap{listOf(it.payment.event.id,it.withdrawal.event.id)}.toSet()
 confirmed.filter{it.payment.event.id !in blocked && it.withdrawal.event.id !in blocked}.forEach{join(it.payment.event.id,it.withdrawal.event.id)}
 val withdrawals=rows.filter{complete(it)&&it.money!!.transactionType=="withdrawal"}
 // Match against every withdrawal before uniqueness checks: different accounts must not be guessed.
 unique(cards.flatMap{c->withdrawals.filter{w->close(c,w)&&sameParty(c,w)}.map{c to it}})
  .filter{(c,w)->c.event.id !in blocked && w.event.id !in blocked}
  .forEach{(c,w)->
   val accounts=rows.filter{root(it.event.id)==root(c.event.id) && it.money?.transactionType=="withdrawal"}
   if(accounts.isEmpty() || accounts.any{it.event.id==w.event.id})join(c.event.id,w.event.id)
  }
 val groups=rows.groupBy{root(it.event.id)}.values
 val sourceMap=mutableMapOf<String,List<String>>()
 val linkedWithdrawals=mutableSetOf<String>()
 val projected=groups.map{group->
  val representative=group.firstOrNull(::debit) ?: group.firstOrNull{payment(it)&&wallet(it)} ?: group.first()
  val ids=group.map{it.event.sourceNotificationId}.distinct()
  group.forEach{sourceMap[it.event.id]=ids}
  if(group.size>1 && payment(representative))group.filter{it.money?.transactionType=="withdrawal"}.forEach{linkedWithdrawals+=it.event.id}
  representative
 }.sortedByDescending{it.event.observedAt}
 val accounts=rows.map{if(it.event.id in linkedWithdrawals)it.copy(money=it.money!!.copy(paymentMethod="체크카드 연결 출금")) else it}
 return PaymentProjection(projected,accounts,sourceMap)
}
