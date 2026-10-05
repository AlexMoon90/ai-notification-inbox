package com.ainotification.inbox

import java.time.*

internal val moneySections=listOf("계좌 입출금","거래 내역","내야 할 돈","통계")
internal val requestTypes=setOf("bill","billing","payment_request","overdue","scheduled_payment")
internal val accountTypes=setOf("deposit","withdrawal","transfer_in","transfer_out","refund")
internal val transactionTypes=setOf("payment","recurring_payment","transfer_out","refund","cancellation")
internal fun obligationStatus(m:MoneyEvent,now:Long)=if(m.status in setOf("pending","processing") && m.dueAt!=null && m.dueAt<now)"overdue" else m.status
internal val informationStatuses=mapOf("advertisement" to "쇼핑 광고","pending" to "미처리","processing" to "처리 중","completed" to "처리 완료","overdue" to "미납","cancelled" to "취소","unknown" to "확인 필요","candidate" to "확인 필요","confirmed" to "확정","changed" to "변경","ordered" to "주문 접수","preparing" to "배송 준비","shipping" to "배송 중","arriving_today" to "오늘 도착","delivered" to "도착 완료","returning" to "반품 중","returned" to "반품 완료","refunded" to "환불 완료","filled" to "체결","price" to "가격 알림","recorded" to "기록")
internal fun periodStart(period:String,now:Long,zone:ZoneId=ZoneId.systemDefault()):Long {
 val date=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
 val start=when(period){"오늘"->date;"이번 주"->date.minusDays((date.dayOfWeek.value-1).toLong());"이번 달"->date.withDayOfMonth(1);"최근 3개월"->date.minusMonths(2).withDayOfMonth(1);else->return Long.MIN_VALUE}
 return start.atStartOfDay(zone).toInstant().toEpochMilli()
}
internal fun eventTime(e:StructuredEntry)=e.money?.occurredAt ?: e.event.observedAt
internal fun matchesMoney(e:StructuredEntry,section:String,filter:String):Boolean {
 val m=e.money ?: return false
 val included=when(section){"계좌 입출금"->m.transactionType in accountTypes;"내야 할 돈"->m.transactionType in requestTypes;"거래 내역"->m.transactionType in transactionTypes;else->true}
 if(!included)return false
 return when(filter){"체크카드"->m.paymentMethod=="체크카드";"신용카드"->m.paymentMethod=="신용카드";"카드"->m.paymentMethod?.contains("카드")==true || m.provider?.endsWith("카드")==true;"계좌이체"->m.transactionType in setOf("transfer_in","transfer_out");"페이"->m.provider?.contains("페이")==true;"정기결제"->m.recurring;"환불·취소"->m.transactionType in setOf("refund","cancellation");else->true}
}
internal fun validSmartEntry(e:StructuredEntry):Boolean = when(e.event.category){
 "money"->e.money!=null
 "schedule","todo"->e.life!=null || e.context?.let{it.eventType in setOf("appointment","appointment_related","task_confirmed","task_related") }==true
 else->e.life!=null
}
/** A stable provider reference is required to collapse lifecycle updates. Every source remains stored. */
internal fun currentInformation(rows:List<StructuredEntry>):List<StructuredEntry> = rows.filter(::validSmartEntry)
 .sortedByDescending{it.event.observedAt}.distinctBy{e->
  val life=e.life
  if(life?.referenceKey!=null)"${life.kind}:${life.provider}:${life.referenceKey}"
  else if(e.money?.referenceKey!=null)"money:${e.money.provider}:${e.money.referenceKey}:${e.money.transactionType}:${e.money.accountHint}:${e.money.transactionAmount}"
  else e.event.id
 }
internal data class MoneySummary(val spending:Long,val income:Long,val accountOut:Long,val refunds:Long,val recurring:Long,val recurringCount:Int,val count:Int,val methods:List<Pair<String,Long>>,val merchants:List<Pair<String,Long>>,val comparison:Int?)
internal fun summarizeMoney(rows:List<StructuredEntry>,period:String,now:Long,accountRows:List<StructuredEntry> = rows):MoneySummary {
 val start=periodStart(period,now)
 val eligible=rows.filter{it.money?.extractionStatus=="complete" && it.money.status=="completed" && eventTime(it) in start..now && it.money.transactionAmount!=null}
 // Account withdrawals/transfers are not automatically consumption. Keep those totals separate.
 val spending=eligible.filter{it.money!!.transactionType in setOf("payment","recurring_payment")}
 fun sum(r:List<StructuredEntry>)=r.sumOf{it.money!!.transactionAmount ?: 0}
 val income=eligible.filter{it.money!!.transactionType in setOf("deposit","transfer_in")}
 val out=accountRows.filter{it.money?.extractionStatus=="complete" && it.money.status=="completed" && eventTime(it) in start..now && it.money.transactionType in setOf("withdrawal","transfer_out")}
 val recurring=spending.filter{it.money!!.recurring}
 val previousStart=when(period){"이번 주"->start-7*86400000L;"이번 달"->Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).minusMonths(1).toInstant().toEpochMilli();else->null}
 val elapsed=now-start
 val previous=if(previousStart!=null)rows.filter{it.money?.extractionStatus=="complete" && it.money.status=="completed" && it.money.transactionType in setOf("payment","recurring_payment") && eventTime(it) in previousStart..minOf(start-1,previousStart+elapsed)} else emptyList()
 val prior=sum(previous)
 return MoneySummary(sum(spending),sum(income),sum(out),sum(eligible.filter{it.money!!.transactionType=="refund"}),sum(recurring),recurring.size,eligible.size,
  spending.groupBy{it.money!!.paymentMethod ?: "수단 미확인"}.map{it.key to sum(it.value)}.sortedByDescending{it.second},
  spending.groupBy{it.money!!.merchant ?: it.money.counterparty ?: "가맹점 미확인"}.map{it.key to sum(it.value)}.sortedByDescending{it.second}.take(5),
  if(prior>0)((sum(spending)-prior)*100.0/prior).toInt() else null)
}
internal fun moneyText(amount:Long)=java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREAN).format(amount)+"원"
internal fun entryTitle(e:StructuredEntry)=e.money?.let(::moneyHeading) ?: e.life?.title ?: e.event.title
internal fun entryStatus(e:StructuredEntry,now:Long):String? {
 e.money?.let{m->return if(m.transactionType in requestTypes)informationStatuses[obligationStatus(m,now)] else if(m.recurring)"정기결제" else if(m.transactionType=="transfer_in_pending")"받기 전" else if(m.extractionStatus!="complete")"확인 필요" else null}
 e.life?.let{return informationStatuses[it.status]}
 return e.context?.let{if(it.needsContext)if(e.event.category=="schedule")"일정 관련 · 확인 필요" else "관련 정보 · 확인 필요" else "확정"}
}
internal fun categoryFilter(e:StructuredEntry,filter:String,now:Long):Boolean {
 val l=e.life;val status=l?.status ?: e.context?.status
 return when(filter){
  "오늘"->l?.scheduledAt?.let{it in periodStart("오늘",now)..(periodStart("오늘",now)+86400000-1)} ?: false
  "예정"->l?.scheduledAt?.let{it>=now && status!="cancelled"} ?: false
  "지난 일정"->l?.scheduledAt?.let{it<now} ?: false
  "변경"->e.event.isChanged || status=="changed"
  "완료"->status in setOf("completed","delivered")
  "취소"->status=="cancelled"
  "확인 필요"->status in setOf("candidate","likely") || e.context?.needsContext==true
  "주문"->status=="ordered";"배송 준비"->status=="preparing";"배송 중"->status=="shipping";"오늘 도착"->status=="arriving_today";"반품"->status in setOf("returning","returned");"환불"->status=="refunded"
  else->true
 }
}
