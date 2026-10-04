package com.ainotification.inbox

/** Observed Kakao Pay service-card wording, interpreted from the notification recipient's view. */
internal fun kakaoPayReceipt(row:CapturedNotification,now:Long):MoneyEvent? {
 if(row.packageName!="com.kakao.talk" || row.isGroupSummary)return null
 val text=row.currentMessageText().trim()
 val outgoing=Regex("^[0-9,]+원을 받았어요\\.\\s*\\*?\\(안내\\) 받은 분은 계좌송금도 수수료 무료로 전환 가능해요\\.$").matches(text)
 val incoming=Regex("^[0-9,]+원을 보냈어요\\.\\s*송금 받기 전까지 보낸 분은 내역 상세화면에서 취소할 수 있어요\\.$").matches(text)
 if(!outgoing && !incoming)return null
 val amount=moneyCandidates(row).amounts.mapNotNull{it.amount}.distinct().singleOrNull() ?: return null
 // A group-room name must not become a person's name.
 val party=if(row.isGroupConversation==true)null else row.title?.replace(Regex("[\\p{Cf}]"),"")?.trim()?.takeIf{it.isNotEmpty() && it !in setOf("카카오톡","카카오페이")}
 val type=if(outgoing)"transfer_out" else "transfer_in_pending"
 return MoneyEvent("event:${row.snapshotId}",row.snapshotId,type,if(outgoing)"out" else "neutral",amount,null,null,party,"카카오페이",null,null,null,row.appLabel,1.0,1.0,"complete",true,now,now)
}
