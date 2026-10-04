package com.ainotification.inbox

import org.json.JSONObject
import org.json.JSONArray

internal val moneyTypes=listOf("payment","deposit","withdrawal","transfer_in","transfer_out","refund","cancellation","scheduled_payment","recurring_payment","billing","money_related_unknown","transfer_related")
internal val moneyLabels=mapOf("transfer_in_pending" to "송금 도착 · 받기 전","payment" to "결제","deposit" to "입금","withdrawal" to "출금","transfer_in" to "송금 수신","transfer_out" to "송금 발신","refund" to "환불","cancellation" to "결제 취소","scheduled_payment" to "결제 예정","recurring_payment" to "정기결제","billing" to "청구","money_related_unknown" to "금융 관련 정보","transfer_related" to "송금 관련 정보")
internal data class MoneyCandidate(val id:String,val text:String,val kind:String,val start:Int=-1,val end:Int=-1,val amount:Long?=null,val time:Long?=null)
internal data class MoneyCandidates(val text:String,val values:List<MoneyCandidate>) {
 val amounts get()=values.filter{it.kind=="amount"}
 val texts get()=values.filter{it.kind=="text" || it.kind=="source"}
 val dates get()=values.filter{it.kind=="date"}
 val accounts get()=values.filter{it.kind=="account"}
 fun redactedText(engine:JevEngine):String {
  var safe=text
  accounts.sortedByDescending{it.start}.forEach{safe=safe.replaceRange(it.start,it.end,"[ACCOUNT_HINT]")}
  return engine.masked(safe).replace(Regex("(?<!\\d)\\d{6,}(?!\\d)"),"[LONG_NUMBER]")
 }
 fun external(engine:JevEngine):JSONObject=JSONObject().put("text",redactedText(engine))
  .put("candidates",JSONArray(values.map{JSONObject().put("id",it.id).put("kind",it.kind).put("text",if(it.kind=="account")"[ACCOUNT_HINT]" else engine.masked(it.text)).put("start",it.start).put("end",it.end)}))
}
internal fun moneyCandidates(row:CapturedNotification):MoneyCandidates {
 val text=listOfNotNull(row.title?.takeIf{it.isNotBlank()},row.currentMessageText().takeIf{it.isNotBlank()}).distinct().joinToString("\n").take(4000)
 val values=mutableListOf<MoneyCandidate>()
 val amountPattern=Regex("[₩￦]\\s*(?:\\d{1,3}(?:,\\d{3})+|\\d+)|(?<![\\d,])(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?\\s*(?:만\\s*)?원|(?:입금|출금|잔액|결제금액|거래금액)\\s*[:：]?\\s*(?:\\d{1,3}(?:,\\d{3})+|\\d+)")
 amountPattern.findAll(text).take(12).forEachIndexed{i,m->
  if(text.getOrNull(m.range.last+1) in listOf('/',':','-'))return@forEachIndexed
  val number=Regex("[0-9][0-9,.]*").find(m.value) ?: return@forEachIndexed
  val start=m.range.first+if(m.value.first() in listOf('₩','￦'))0 else number.range.first
  val raw=text.substring(start,m.range.last+1)
  val n=runCatching{java.math.BigDecimal(number.value.replace(",","")).multiply(java.math.BigDecimal(if(m.value.contains("만"))10000 else 1)).longValueExact()}.getOrNull()
  if(n!=null && n>=0)values+=MoneyCandidate("M$i",raw,"amount",start,m.range.last+1,n)
 }
 // Text spans, never generated names. Short phrases and individual tokens cover merchants and senders.
 val spans=linkedSetOf<Pair<Int,Int>>()
 Regex("([^\\n\\d]{1,35}?)(?:님이|님께서|님에게|님께)").findAll(text).forEach{m->val g=m.groups[1]!!;spans+=g.range.first to g.range.last+1}
 Regex("[가-힣A-Za-z][가-힣A-Za-z0-9·_*-]{1,29}").findAll(text).forEach{m->spans+=m.range.first to m.range.last+1}
 spans.take(40).forEachIndexed{i,(a,b)->values+=MoneyCandidate("T$i",text.substring(a,b),"text",a,b)}
 values+=MoneyCandidate("S0",row.appLabel,"source")
 Regex("(?<![\\d])(?:[\\d*]{2,6}[- ][\\d*]{2,6}[- ][\\d*]{2,8})(?![\\d])").findAll(text).take(3).forEachIndexed{i,m->if(!Regex("^0[0-9]{1,2}[- ]").containsMatchIn(m.value))values+=MoneyCandidate("A$i",m.value.takeLast(4).padStart(m.value.length,'*'),"account",m.range.first,m.range.last+1)}
 // Only full, valid calendar dates with times; do not invent a year from a notification timestamp.
 Regex("(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})[ T]+(\\d{1,2}):(\\d{2})").findAll(text).take(3).forEachIndexed{i,m->
  val time=runCatching{java.time.LocalDateTime.of(m.groupValues[1].toInt(),m.groupValues[2].toInt(),m.groupValues[3].toInt(),m.groupValues[4].toInt(),m.groupValues[5].toInt()).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrNull()
  if(time!=null)values+=MoneyCandidate("D$i",m.value,"date",m.range.first,m.range.last+1,time=time)
 }
 return MoneyCandidates(text,values)
}
internal fun moneyPossible(row:CapturedNotification,events:List<String>):Boolean {
 if(row.packageName=="com.ainotification.inbox" || row.packageName in excludedNotificationPackages || row.isGroupSummary || row.isExcludedCallStatus() || row.hasEmptyContent() || row.needsOriginalReview())return false
 return events.any{it in setOf("PAYMENT","MONEY_RECEIVED","REFUND")} || Regex("입금|출금|송금|보냈습니다|받았어요|결제|승인|환불|이체|청구|잔액|payment|deposit|refund|withdrawal",RegexOption.IGNORE_CASE).containsMatchIn(listOf(row.title,row.currentMessageText()).joinToString(" "))
}
internal fun messageLike(row:CapturedNotification)=messageService(row)!=null || row.packageName in messengerPackages || row.packageName in smsPackages || (row.packageName=="com.instagram.android" && (row.notificationCategory=="msg" || row.conversationIdentity!=null)) || row.notificationCategory=="email" || row.packageName in setOf("com.google.android.gm","com.microsoft.office.outlook","com.samsung.android.email.provider","com.nhn.android.mail","com.kakao.talk")
internal fun moneyPatternKey(row:CapturedNotification,c:MoneyCandidates):String {
 // Normalize numeric slots and the explicit sender/merchant position only. Keep semantic words and negation.
 var normalized=c.text.replace(Regex("(?<![\\d,])(?:\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원"),"<MONEY>")
 normalized=normalized.replace(Regex("(?m)^([^\\n\\d]{1,30}?)(님이|님께서|님에게|님께)"),"<PERSON>$2")
 normalized=normalized.replace(Regex("(?m)^([가-힣A-Za-z][가-힣A-Za-z·_-]{1,24}) +(?=<MONEY>)")){m->if(Regex("입금|출금|잔액|한도|누적|결제|승인|환불|청구").containsMatchIn(m.groupValues[1]))m.value else "<PARTY> "}
 normalized=normalized.replace(Regex("20\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}:\\d{2}|\\d{1,2}/\\d{1,2}"),"<DATE>")
 normalized=normalized.replace(Regex("[ \\t]+")," ").trim()
 return stableMessageIdentity(listOf("money-v1",JevEngine.MODEL,row.packageName,row.channelId.orEmpty(),row.conversationIdentity ?: if(messageLike(row))row.notificationKey else "",row.conversationTitle.orEmpty(),row.personIdentity.orEmpty(),normalized,c.values.filter{it.kind in setOf("amount","date","account")}.map{it.kind}.joinToString(",")).joinToString("\u0000"))
}
internal fun directionFor(type:String)=when(type){"deposit","transfer_in","refund"->"in";"payment","withdrawal","transfer_out","recurring_payment"->"out";"billing","scheduled_payment","cancellation"->"neutral";else->"unknown"}
internal fun moneyDisplayAmount(event:MoneyEvent):String?=event.transactionAmount?.let{(when(event.direction){"in"->"+";"out"->"-";else->""})+java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREAN).format(it)+"원"}
