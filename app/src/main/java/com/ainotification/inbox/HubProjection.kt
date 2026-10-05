package com.ainotification.inbox

import org.json.JSONArray
import org.json.JSONObject

internal fun photoCaption(text:String,hasImage:Boolean,sender:String?=null):String {
    if(!hasImage)return text
    var label=text.trim().trimEnd('.', '!', '。')
    sender?.takeIf{it.isNotBlank()}?.let{name->
        for(prefix in listOf("${name}님이 ","${name}님이", "$name: "))if(label.startsWith(prefix)){label=label.removePrefix(prefix).trim();break}
    }
    return if(label in setOf("사진","사진을 보냈습니다","사진을 보냈어요") || Regex("사진 [0-9]+장(을 보냈습니다)?").matches(label)) "" else text
}

internal val hubCategories=linkedMapOf("RESERVATION" to "예약","DELIVERY" to "배송","FINANCE" to "금융","SHOPPING" to "쇼핑 / 주문","SOCIAL" to "SNS","TRAVEL" to "여행","OTHER" to "기타")
internal val smsPackages=setOf("com.google.android.apps.messaging","com.samsung.android.messaging","com.android.mms")
internal data class HubMessage(val source:CapturedNotification,val sender:String?,val text:String,val time:Long,val identity:String,val imageFile:String?=null,val hasImage:Boolean=false,val senderAvatarFile:String?=null)
internal data class HubRoom(val id:String,val service:String,val name:String,val messages:List<HubMessage>,val avatarFile:String?=null) {
    val latest get()=messages.last()
}
// Package identity establishes a communication service; CATEGORY_MESSAGE alone is not evidence.
internal val messengerPackages=setOf(
    "com.kakao.talk","jp.naver.line.android","org.telegram.messenger","org.telegram.messenger.web",
    "com.whatsapp","com.whatsapp.w4b","org.thoughtcrime.securesms","com.facebook.orca",
    "com.discord","com.Slack","com.microsoft.teams","com.viber.voip","com.tencent.mm",
    "com.google.android.apps.dynamite"
)
internal fun messageService(row:CapturedNotification):String? {
    if(row.isGroupSummary)return null
    val hasMessages=runCatching{val a=JSONArray(row.messagesJson);(0 until a.length()).any{a.optJSONObject(it)?.optBoolean("attachmentOnly")!=true}}.getOrDefault(false)
    // A social app package alone is not evidence of a DM.
    val legacySms=row.packageName in smsPackages && row.notificationCategory==null && !row.title.isNullOrBlank()
    if(!hasMessages && row.notificationCategory!="msg" && row.serviceType!="SMS" && !legacySms)return null
    if(row.serviceType=="SMS" || row.packageName in smsPackages)return "문자"
    if(row.packageName in messengerPackages)return row.appLabel
    // Social feeds are not messenger services, even when they label a push as a message.
    return null
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
        // Retain one message per identity as we go, not every copy from every snapshot.
        val unique=linkedMapOf<String,HubMessage>()
        fun retain(message:HubMessage) {
            val prior=unique[message.identity]
            if(prior==null)unique[message.identity]=message
            else if(prior.imageFile==null && message.imageFile!=null || prior.senderAvatarFile==null && message.senderAvatarFile!=null)
                unique[message.identity]=prior.copy(imageFile=prior.imageFile ?: message.imageFile,
                    senderAvatarFile=prior.senderAvatarFile ?: message.senderAvatarFile)
        }
        for(row in snapshots.sortedByDescending{it.capturedTime}) {
            val array=runCatching{JSONArray(row.messagesJson)}.getOrDefault(JSONArray())
            if(array.length()==0)retain(HubMessage(row,null,row.preview(),row.postedTime,stableMessageIdentity("${row.notificationKey}|${row.postedTime}|${row.currentMessageText()}")))
            else for(i in 0 until array.length()) {
                val m=array.optJSONObject(i) ?: continue
                val time=m.optLong("timestamp").takeIf{it>0} ?: row.postedTime
                val sender=m.stringOrNull("sender")
                val text=m.stringOrNull("text")?.takeIf{it.isNotBlank()} ?: "첨부 내용은 원래 앱에서 확인해 주세요"
                val attachment=m.stringOrNull("attachmentId") ?: m.stringOrNull("imageFile").orEmpty()
                val identity=m.stringOrNull("displayIdentity") ?: stableMessageIdentity(if(m.optLong("timestamp")>0)"$time|$sender|$text|$attachment" else "${row.snapshotId}|$i")
                if(identity !in unique || m.stringOrNull("imageFile")!=null || m.stringOrNull("senderAvatarFile")!=null)
                    retain(HubMessage(row,sender,text,time,identity,m.stringOrNull("imageFile"),m.isImageAttachment(),m.stringOrNull("senderAvatarFile")))
            }
        }
        val messages=unique.values.sortedBy{it.time}
        HubRoom(id,messageService(latest)!!,notificationDisplayTitle(latest),messages,snapshots.sortedByDescending{it.capturedTime}.firstNotNullOfOrNull{it.conversationAvatarFile})
    }.filter{it.messages.isNotEmpty()}.sortedByDescending{it.latest.time}

internal fun isNowNotification(row:CapturedNotification,decisions:JSONObject):Boolean {
    // Android group summaries are containers, not individual notifications. They may
    // arrive after a child with empty text and must never replace its Now card.
    if(row.isGroupSummary)return false
    val d=decisions.optJSONObject(row.snapshotId) ?: return false
    return (d.optString("status")=="match" && d.optString("action","SHOW")=="SHOW") ||
        (d.optString("status")=="review" && !row.isGroupSummary && row.latestMessage()?.isImageAttachment()==true)
}
internal fun hubBadge(fact:HubClassification?):String? = fact?.events()?.firstNotNullOfOrNull {
    when(it){"REPLY_REQUIRED"->"답변 필요";"SCHEDULE_CHANGE"->"일정 변경";"MEETING_CONFIRMED"->"약속 확정";"AI_TASK_COMPLETED"->"작업 완료";"AI_INPUT_REQUIRED"->"확인 요청";else->null}
}

internal fun CapturedNotification.hasGroupConversation()=isGroupConversation==true || !conversationTitle.isNullOrBlank() && isGroupConversation!=false

internal fun hubAppKey(row:CapturedNotification)=if(row.serviceType=="SMS" || row.packageName in smsPackages)"SMS" else row.packageName
internal fun hubAppLabel(row:CapturedNotification)=if(hubAppKey(row)=="SMS")"문자" else row.appLabel
internal fun hubTimeBucket(time:Long,now:Long):String {
    if(time<=now && now-time<=30*60*1000)return "최근 30분"
    val zone=java.time.ZoneId.systemDefault()
    val today=java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day=java.time.Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    return when(day){today->"오늘";today.minusDays(1)->"어제";else->"이전"}
}

/** Filter by policy first: an excluded update must not replace a visible notification. */
internal fun latestNowBySource(rows:List<CapturedNotification>,decisions:JSONObject):List<CapturedNotification>{
    val seen=mutableSetOf<String>()
    return rows.filter{isNowNotification(it,decisions)}.sortedWith(compareByDescending<CapturedNotification>{it.postedTime}.thenByDescending{it.capturedTime}.thenBy{it.snapshotId}).filter{seen.add(if(messageService(it)!=null) "conversation:"+conversationRoomId(it) else "app:"+it.packageName)}
}
