package com.ainotification.inbox

/** Small source projection: displaying the contact never loads message bodies or re-runs AI. */
data class ScheduleContact(val sourceNotificationId:String,val title:String?,val conversationTitle:String?,val isGroupConversation:Boolean?,val personIdentity:String?,val sourceApp:String)
internal fun scheduleContactLabel(entry:StructuredEntry,contact:ScheduleContact?):String? {
 if(entry.event.category!="schedule")return null
 fun usable(value:String?)=value?.trim()?.takeIf{it.isNotEmpty() && it !in setOf(entry.event.sourceApp,"메시지","새 메시지","알림","단톡방")}
 if(contact?.isGroupConversation==true) {
  val room=usable(contact.conversationTitle) ?: usable(entry.life?.participant)
  if(room!=null)return "대화방 · $room"
  return usable(contact.title)?.let{"보낸 사람 · $it"} ?: "대화방 정보 없음"
 }
 val phone=contact?.personIdentity?.takeIf{it.startsWith("tel:")}?.removePrefix("tel:")?.takeIf{Regex("[+0-9][0-9 ()-]{4,}").matches(it)}
 val person=usable(entry.life?.participant) ?: usable(contact?.conversationTitle) ?: usable(contact?.title) ?: phone
 return person?.let{"상대방 · $it"} ?: "상대방 정보 없음"
}
