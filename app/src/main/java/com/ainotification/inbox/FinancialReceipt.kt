package com.ainotification.inbox

/** Source-independent explicit receipt formats. A conversational claim alone never matches. */
internal fun explicitFinancialReceipt(row:CapturedNotification,now:Long):MoneyEvent? {
 if(row.packageName=="com.ainotification.inbox" || row.packageName in excludedNotificationPackages || row.isGroupSummary || row.hasEmptyContent() || row.isExcludedCallStatus())return null
 kakaoPayReceipt(row,now)?.let{return it}
 val c=moneyCandidates(row);val text=c.text
 if(Regex("광고|예시|예정|예상|거절|실패|미승인|미결제|입금 전|출금 전|한도 안내|입금해|출금할|결제할|입금하면|결제하면|결제 시|결제시|가정|테스트 문구|보내주세요").containsMatchIn(text))return null
 fun labeled(label:String)=c.amounts.filter { a ->
  val before=text.substring((a.start-16).coerceAtLeast(0),a.start)
  Regex("(?:$label)\\]?(?:금액)?\\s*[:：]?\\s*[₩￦]?\\s*$").containsMatchIn(before)
 }.singleOrNull()
 val bankType=listOf("입금" to "deposit","출금" to "withdrawal").mapNotNull{(label,type)->labeled(label)?.let{type to it}}.singleOrNull()
 val bankCue=Regex("\\[Web발신\\]|은행|계좌|<[^>]{1,12}>|\\d{2}[/:]\\d{2}",RegexOption.IGNORE_CASE).containsMatchIn(text)
 val balance=labeled("잔액")
 var type:String?=null;var amount:MoneyCandidate?=null
 if(bankType!=null && balance!=null && bankCue){type=bankType.first;amount=bankType.second}
 val provider=Regex("삼성|현대|신한|우리|국민|하나|농협|기업|카카오페이|토스|롯데|비씨|BC|KB|NH|IBK|은행|카드|월렛",RegexOption.IGNORE_CASE).containsMatchIn(text+row.appLabel)
 if(type==null && provider){
  val marker=Regex("승인(?:완료)?|결제\\s*(?:완료|되었습니다|알림)|환불\\s*(?:완료|되었습니다)|결제 취소")
  val currencyReceipt=Regex("^[₩￦]\\s*[\\d,]+\\s*결제",RegexOption.MULTILINE).containsMatchIn(text)
  if(marker.containsMatchIn(text)||currencyReceipt){
   val eligible=c.amounts.filter{a->!Regex("잔액|누적|한도|잔여").containsMatchIn(text.substring((a.start-10).coerceAtLeast(0),a.start).substringAfterLast('\n'))}
   amount=eligible.singleOrNull()
   if(amount!=null)type=when{Regex("환불").containsMatchIn(text)->"refund";Regex("취소").containsMatchIn(text)->"cancellation";else->"payment"}
  }
 }
 if(type==null || amount==null || amount.id==balance?.id)return null
 val party=receiptParty(row,type)
 val merchant=party.takeIf{type !in listOf("deposit","withdrawal","transfer_in","transfer_out")}
 val counterparty=party.takeIf{merchant==null}
 return MoneyEvent("event:${row.snapshotId}",row.snapshotId,type,directionFor(type),amount.amount,balance?.amount,merchant,counterparty,receiptProvider(text),null,null,c.dates.singleOrNull()?.time,row.appLabel,1.0,1.0,"complete",true,now,now)
}
