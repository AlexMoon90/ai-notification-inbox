package com.ainotification.inbox

/** Literal receipt fields only. The notification sender is never the transaction counterparty. */
internal fun receiptProvider(text:String):String? = Regex("(?:KB국민|국민|신한|우리|하나|NH농협|농협|IBK기업|기업|삼성|현대|롯데|비씨|BC)(?:은행|카드)|새마을금고|신협|우체국|수협은행|부산은행|경남은행|광주은행|전북은행|제주은행|카카오뱅크|케이뱅크|토스뱅크|(?<=<)(?:국민|신한|우리|하나|농협|기업|IBK|KB|NH)(?=>)")
 .findAll(text).map{it.value}.distinct().toList().singleOrNull()

internal fun receiptParty(row:CapturedNotification,type:String):String? {
 val text=moneyCandidates(row).text
 fun clean(s:String?):String? = s?.replace(Regex("[\\p{Cf}]"),"")?.trim()?.takeIf{
  it.length in 2..60 && it.any{ch->ch.isLetter()} && !Regex("잔액|승인|결제|입금|출금|누적|한도|일시불|할부|https?://|알림|[0-9*]{5,}").containsMatchIn(it)
 }
 val label=if(type in listOf("deposit","transfer_in"))"입금자|보낸분|보낸 분|보내신 분" else if(type in listOf("withdrawal","transfer_out"))"받는분|받는 분|받으신 분|출금처|이체대상" else "가맹점|사용처|결제처"
 val labeled=Regex("(?:$label)\\s*[:：]\\s*([^\\n]+)").findAll(text).mapNotNull{clean(it.groupValues[1])}.distinct().toList()
 if(labeled.size==1)return labeled.single()
 if(labeled.size>1)return null
 if(type in listOf("deposit","withdrawal")) {
  // Bank SMS: <bank> account-hint literal-counterparty 입금/출금 amount 잔액 balance.
  val action=if(type=="deposit")"입금" else "출금"
  // MG push: [입금/출금] amount account 잔액 balance MM/dd HH:mm counterparty.
  if(row.packageName=="com.smg.spbs") {
   val tail=Regex("\\[$action\\]\\s*[0-9,]+원\\s+[0-9*\\-]+\\s+잔액\\s*[0-9,]+원\\s+[0-9]{2}/[0-9]{2}\\s+[0-9]{2}:[0-9]{2}\\s+([^\\n]+)$").find(text)
   clean(tail?.groupValues?.get(1))?.let{return it}
  }
  val parties=Regex("<[^>\\n]+>\\s*[0-9* -]{5,}\\s+([^\\n]+?)\\s+$action\\s*[0-9]").findAll(text).mapNotNull{clean(it.groupValues[1])}.distinct().toList()
  return parties.singleOrNull()
 }
 // Wallet receipts explicitly put the merchant in the single body line below the amount title.
 if(Regex("^[₩￦]\\s*[0-9,]+\\s*결제").containsMatchIn(row.title.orEmpty()))return clean(row.currentMessageText().takeIf{!it.contains('\n')})
 // Card receipt: a standalone merchant line; discard metadata instead of guessing the cardholder.
 val lines=text.lines().map{it.trim()}.filter{it.isNotEmpty()}
 val amountLine=lines.indexOfFirst{Regex("[0-9,]+원").containsMatchIn(it)}
 if(amountLine>=0){
  val candidates=lines.drop(amountLine+1).mapNotNull{clean(it)}.filter{!Regex("님|카드|은행|[0-9]{1,2}[:/][0-9]{2}").containsMatchIn(it)}.distinct()
  return candidates.singleOrNull()
 }
 return null
}

internal fun moneyHeading(m:MoneyEvent):String = listOfNotNull(m.merchant ?: m.counterparty,moneyLabels[m.transactionType]).joinToString(" · ")

/** Local sender evidence, scoped to SMS app + normalized number; not authentication. */
internal class ReceiptSenders(context:android.content.Context) {
 private val prefs=context.getSharedPreferences("receipt-senders-v1",0)
 private fun key(row:CapturedNotification):String? {
  if(row.packageName !in smsPackages)return null
  val title=row.title.orEmpty().replace(Regex("[\\p{Cf}\\s()-]"),"")
  if(!Regex("\\+?[0-9]{8,15}").matches(title))return null
  val number=if(title.startsWith("+82"))"0"+title.drop(3) else title
  return stableMessageIdentity(row.packageName+":"+number)
 }
 fun enrich(row:CapturedNotification,event:MoneyEvent):MoneyEvent {
  val key=key(row) ?: return event
  val explicit=event.provider
  if(explicit!=null){
   val old=prefs.getString("$key:provider",null)
   if(old!=null && old!=explicit)prefs.edit().putBoolean("$key:conflict",true).apply()
   val evidence=prefs.getStringSet("$key:evidence",emptySet()).orEmpty()
   val ids=(evidence+stableMessageIdentity(row.snapshotId)).take(2).toSet()
   prefs.edit().putString("$key:provider",explicit).putStringSet("$key:evidence",ids).apply()
   return event
  }
  // Only an independently recognized receipt can use repeated sender evidence.
  if(prefs.getBoolean("$key:conflict",false)||prefs.getStringSet("$key:evidence",emptySet()).orEmpty().size<2)return event
  return event.copy(provider=prefs.getString("$key:provider",null))
 }
}

/** Keep observed masking; never infer hidden digits or identify accounts by bank alone. */
internal fun receiptAccountHint(row:CapturedNotification):String? {
 val text=moneyCandidates(row).text
 val token="([0-9*][0-9*\\-]{4,28}[0-9*])"
 val patterns=listOf(
  Regex("<[^>\\n]+>\\s*$token(?=\\s)"),
  Regex("\\[(?:입금|출금)\\]\\s*[0-9,]+원\\s+$token\\s+잔액"),
  Regex("계좌(?:번호)?\\s*[:：]\\s*$token(?=\\s|$)")
 )
 val hints=patterns.flatMap{re->re.findAll(text).map{it.groupValues[1]}.toList()}.distinct()
 val hint=hints.singleOrNull()?.takeIf{it.count(Char::isDigit)>=3} ?: return null
 if('*' in hint)return hint
 // Unmasked account numbers stay private; retain only the last four digits for display.
 var visible=4
 return hint.reversed().map{ch->if(ch.isDigit()){if(visible-->0)ch else '*'}else ch}.reversed().joinToString("")
}
internal fun moneySourceLabel(m:MoneyEvent):String? = listOfNotNull(m.provider,m.accountHint?.let{"계좌 $it"})
 .joinToString(" · ").takeIf{it.isNotBlank()}
