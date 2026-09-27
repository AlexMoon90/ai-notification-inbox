package com.ainotification.inbox

import org.json.JSONArray
import org.json.JSONObject

internal val hubCategories=linkedMapOf("RESERVATION" to "예약","DELIVERY" to "배송","FINANCE" to "금융","SHOPPING" to "쇼핑 / 주문","SOCIAL" to "SNS","TRAVEL" to "여행","OTHER" to "기타")
internal val smsPackages=setOf("com.google.android.apps.messaging","com.samsung.android.messaging","com.android.mms")
internal data class HubMessage(val source:CapturedNotification,val sender:String?,val text:String,val time:Long,val identity:String,val imageFile:String?=null,val hasImage:Boolean=false,val senderAvatarFile:String?=null)
internal data class HubRoom(val id:String,val service:String,val name:String,val messages:List<HubMessage>,val avatarFile:String?=null) {
    val latest get()=messages.last()
}
internal fun messageService(row:CapturedNotification):String? {
    if(row.isGroupSummary)return null
    val hasMessages=runCatching{val a=JSONArray(row.messagesJson);(0 until a.length()).any{a.optJSONObject(it)?.optBoolean("attachmentOnly")!=true}}.getOrDefault(false)
    // A social app package alone is not evidence of a DM.
    val legacySms=row.packageName in smsPackages && row.notificationCategory==null && !row.title.isNullOrBlank()
    if(!hasMessages && row.notificationCategory!="msg" && row.serviceType!="SMS" && !legacySms)return null
    if(row.serviceType=="SMS" || row.packageName in smsPackages)return "문자"
    return row.appLabel
}
// A group notification title may be the sender, not the room name.
internal fun notificationDisplayTitle(row:CapturedNotification):String =
    row.conversationTitle?.takeIf{it.isNotBlank()}
        ?: if(row.isGroupConversation==true) "단톡방"
        else row.title?.takeIf{it.isNotBlank()} ?: row.appLabel

internal fun conversationRoomId(row:CapturedNotification):String {
    val id=row.conversationIdentity?.takeIf{it.isNotBlank()}
    if(id!=null)return "${row.packageName}|shortcut|$id"
    // A stable contact URI is preferable to its display name. Never use a group member as room identity.
    if(row.isGroupConversation!=true && !row.personIdentity.isNullOrBlank())return "${row.packageName}|person|${row.personIdentity}"
    val title=(row.conversationTitle ?: row.title).orEmpty().trim()
    if((row.serviceType=="SMS" || row.packageName in smsPackages) && Regex("[+\\d][\\d ()-]{4,}").matches(title))
        return "${row.packageName}|phone|${title.filter{it.isDigit()||it=='+'}}"
    // Title alone can conflate unrelated rooms. Keep Android key + title + channel for conservative fallback.
    return "${row.packageName}|fallback|${row.channelId}|${row.notificationKey}|$title"
}
internal fun conversationRooms(rows:List<CapturedNotification>):List<HubRoom> = rows.filter{messageService(it)!=null}
    .groupBy(::conversationRoomId).map { (id,snapshots)->
        val latest=snapshots.maxWith(compareBy<CapturedNotification>{it.postedTime}.thenBy{it.capturedTime})
        val messages=snapshots.sortedByDescending{it.capturedTime}.flatMap {row->
            val array=runCatching{JSONArray(row.messagesJson)}.getOrDefault(JSONArray())
            if(array.length()==0)listOf(HubMessage(row,null,row.preview(),row.postedTime,"${row.notificationKey}|${row.postedTime}|${row.currentMessageText()}"))
            else (0 until array.length()).mapNotNull{i->array.optJSONObject(i)?.let{m->
                val time=m.optLong("timestamp").takeIf{it>0} ?: row.postedTime
                val sender=m.stringOrNull("sender")
                val text=m.stringOrNull("text")?.takeIf{it.isNotBlank()} ?: "첨부 내용은 원래 앱에서 확인해 주세요"
                // Deduplicate the same timestamped message repeated in MessagingStyle history; keep different senders/times.
                val attachment=m.stringOrNull("attachmentId") ?: m.stringOrNull("imageFile").orEmpty()
                val identity=if(m.optLong("timestamp")>0)"$time|$sender|$text|$attachment" else "${row.snapshotId}|$i"
                HubMessage(row,sender,text,time,identity,m.stringOrNull("imageFile"),m.isImageAttachment(),m.stringOrNull("senderAvatarFile"))
            }}
        }.groupBy{it.identity}.values.map{copies->copies.first().copy(imageFile=copies.firstNotNullOfOrNull{it.imageFile},senderAvatarFile=copies.firstNotNullOfOrNull{it.senderAvatarFile})}.sortedBy{it.time}
        HubRoom(id,messageService(latest)!!,notificationDisplayTitle(latest),messages,snapshots.sortedByDescending{it.capturedTime}.firstNotNullOfOrNull{it.conversationAvatarFile})
    }.filter{it.messages.isNotEmpty()}.sortedByDescending{it.latest.time}

internal fun isNowNotification(row:CapturedNotification,decisions:JSONObject):Boolean {
    val d=decisions.optJSONObject(row.snapshotId) ?: return false
    return d.optString("status")=="match" && d.optString("action","SHOW")=="SHOW"
}
internal fun hubBadge(fact:HubClassification?):String? = fact?.events()?.firstNotNullOfOrNull {
    when(it){"REPLY_REQUIRED"->"답변 필요";"SCHEDULE_CHANGE"->"일정 변경";"MEETING_CONFIRMED"->"약속 확정";"AI_TASK_COMPLETED"->"작업 완료";"AI_INPUT_REQUIRED"->"확인 요청";else->null}
}

internal fun CapturedNotification.hasGroupConversation()=isGroupConversation==true || !conversationTitle.isNullOrBlank() && isGroupConversation!=false
