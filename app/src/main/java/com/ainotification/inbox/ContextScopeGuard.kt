package com.ainotification.inbox

import org.json.JSONArray
import org.json.JSONObject

// Only unqualified whole-room commands are bound locally. Qualified/ambiguous requests
// still go through the editor and semantic validator, rather than guessing their meaning.
internal fun isWholeRoomHide(text:String):Boolean {
    val t=text.lowercase().replace(Regex("[\\s.!?。]+"),"")
    return Regex("(이|해당|선택한)(대화방|단톡방|채팅방|방)(은|는|을|를)?(전체|전부|모두)?(숨겨줘|숨겨주세요|숨기기|숨김|차단해줘|차단해주세요)").matches(t) ||
        t in setOf("hidethisroom","hidethischat","hidethisconversation","hidethisgroup","hidethisgroupchat")
}
internal fun isRoomSenderHide(text:String,target:JSONObject):Boolean {
    val sender=target.optString("sender")
    if(sender.isBlank())return false
    val t=text.lowercase().replace(Regex("[\\s.!?。]+"),"")
    val name=Regex.escape(sender.lowercase().replace(Regex("\\s+"),""))
    return Regex("(이|해당|선택한)(대화방|단톡방|채팅방|방)에서(이발신자|이사람|$name)(의)?(메시지|메세지|글)?만(숨겨줘|숨겨주세요|숨기기|차단해줘)").matches(t) ||
        t in setOf("hidethissenderonlyinthisgroupchat","hidethissenderonlyinthisroom","hidethispersononlyinthischat")
}
internal fun contextCatalog(catalog:JSONObject,target:JSONObject?):JSONObject {
    target ?: return catalog
    val pkg=target.optString("package")
    if(pkg.isNotBlank() && pkg !in jsonStrings(catalog.getJSONArray("apps")))catalog.getJSONArray("apps").put(pkg)
    val room=target.optString("conversation_id")
    if(room.isNotBlank() && !target.optBoolean("is_group_summary") && jsonObjects(catalog.getJSONArray("conversations")).none{it.optString("id")==room})
        catalog.getJSONArray("conversations").put(JSONObject().put("id",room).put("package",pkg).put("label",target.optString("conversation_title")))
    val sender=target.optString("sender")
    if(sender.isNotBlank() && sender !in jsonStrings(catalog.getJSONArray("senders")))catalog.getJSONArray("senders").put(sender)
    return catalog
}
internal fun validateContextScope(target:JSONObject?,before:JSONArray,after:JSONArray) {
    if(target==null || (!target.optBoolean("whole_room_hide") && !target.optBoolean("room_sender_hide")))return
    val selectedSender=if(target.optBoolean("room_sender_hide"))listOf(target.getString("sender")) else emptyList()
    val room=target.optString("conversation_id");val pkg=target.optString("package")
    require(room.isNotBlank() && pkg.isNotBlank() && !target.optBoolean("is_group_summary")){"선택한 대화방을 확인해야 합니다. 발신자나 앱 전체로 대체할 수 없습니다."}
    val previous=jsonObjects(before).associateBy{it.getString("id")}
    val proposed=jsonObjects(after)
    require(previous.keys.all{id->proposed.any{it.getString("id")==id}}){"방 숨김 요청으로 기존 기준을 삭제할 수 없습니다."}
    val changed=proposed.filter{r->previous[r.getString("id")]?.let{canonical(it)==canonical(r)}!=true}
    fun exactRoom(r:JSONObject):Boolean {
        val s=r.getJSONObject("scope")
        return s.getString("type") in (if(selectedSender.isEmpty())setOf("SPECIFIC_CONVERSATION") else setOf("SPECIFIC_CONVERSATION","SPECIFIC_SENDER")) && jsonStrings(s.getJSONArray("apps"))==listOf(pkg) &&
            jsonStrings(s.getJSONArray("conversation_ids"))==listOf(room) && jsonStrings(s.getJSONArray("sender_names"))==selectedSender &&
            s.optString("relationship").isBlank() && s.optString("source_type").isBlank()
    }
    require(changed.all(::exactRoom)){"선택한 앱과 conversation_id를 모두 명시해야 합니다. 방 전체 요청은 발신자 제한 없이, 방 안의 특정 발신자 요청은 그 발신자만 포함해야 합니다."}
    require(proposed.any{r->exactRoom(r) && r.optBoolean("enabled") && r.optString("action")=="HIDE" &&
        jsonObjects(r.getJSONArray("conditions")).let{cs->cs.size==1 && cs[0].optString("type")=="ANY" && cs[0].optString("value").isBlank() && !cs[0].optBoolean("negated")}}){"선택한 대상에 적용되는 숨김 기준이 필요합니다."}
}
