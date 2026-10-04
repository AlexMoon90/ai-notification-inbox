package com.ainotification.inbox

import org.json.JSONArray
import org.json.JSONObject

internal data class InitialPreference(val id:String,val label:String,val type:String,val meaning:String="") {
    fun condition()=JSONObject().put("type",type).put("value",meaning).put("negated",false)
}
internal val initialMust=listOf(
    InitialPreference("money","결제·출금·입금","CONTENT","실제로 발생한 결제, 출금 또는 입금 거래 안내. 할인 광고나 상품 가격 안내는 제외"),
    InitialPreference("schedule","예약·일정 생성과 변경","CONTENT","실제 예약이나 일정의 확정, 생성, 변경 또는 취소 안내"),
    InitialPreference("delivery","배송 지연·반품·문제","CONTENT","실제 주문의 배송 지연, 반품 또는 배송 문제 안내"),
    InitialPreference("reply","답변을 기다리는 메시지","REPLY_REQUIRED"),
    InitialPreference("work","업무 요청·마감·중요 공지","CONTENT","업무 수행 요청, 업무 마감 또는 업무에 관한 중요 공지"),
    InitialPreference("security","보안·인증·계정 알림","SECURITY")
)
internal val initialLess=listOf(
    InitialPreference("ads","광고·프로모션","PROMOTION"),
    InitialPreference("discount","할인·쿠폰 안내","CONTENT","할인이나 쿠폰 사용을 권하는 홍보 안내. 실제 결제나 거래 안내는 제외"),
    InitialPreference("event","이벤트 참여 유도","CONTENT","경품이나 판촉 이벤트 참여를 권하는 홍보 안내"),
    InitialPreference("recommend","추천 상품·콘텐츠","CONTENT","추천 상품이나 추천 콘텐츠를 보도록 유도하는 홍보 안내"),
    InitialPreference("return","앱 재방문 유도","CONTENT","앱을 다시 방문하거나 실행하도록 권하는 홍보 안내")
)

/** Finite selections only. Important selections are SHOW exceptions inside every exclusion. */
internal fun initialPreferenceRules(must:Set<String>,less:Set<String>,before:JSONArray):JSONArray {
    require(must.all{key->initialMust.any{it.id==key}} && less.all{key->initialLess.any{it.id==key}})
    val remaining=jsonObjects(before).filter{it.getJSONObject("scope").getString("type")!="INITIAL_PREFERENCE"}
    val after=JSONArray(remaining)
    val ids=PolicyContract.newRuleIds(before).iterator()
    val keep=initialMust.filter{it.id in must}
    val quiet=initialLess.filter{it.id in less}
    fun add(name:String,conditions:JSONArray,action:String,exceptions:JSONArray=JSONArray()) {
        after.put(JSONObject().put("id",ids.next()).put("name",name).put("enabled",true)
            .put("scope",PolicyContract.emptyScope().put("type","INITIAL_PREFERENCE"))
            .put("conditions",conditions).put("logic","ANY").put("action",action).put("exceptions",exceptions)
            .put("time",PolicyContract.emptyTime()).put("source_instruction","처음 선택한 방향: $name"))
    }
    if(keep.isNotEmpty())add("꼭 알려주세요 · "+keep.joinToString{it.label},JSONArray(keep.map{it.condition()}),"SHOW")
    quiet.forEach{item->
        val exceptions=JSONArray()
        if(keep.isNotEmpty())exceptions.put(JSONObject().put("id","keep_initial").put("conditions",JSONArray(keep.map{it.condition()})).put("logic","ANY").put("action","SHOW"))
        add("조용히 해주세요 · ${item.label}",JSONArray().put(item.condition()),"HIDE",exceptions)
    }
    return after
}

internal fun policySpecificity(rule:JSONObject):Int {
    val s=rule.getJSONObject("scope")
    if(s.getString("type")=="INITIAL_PREFERENCE")return 0
    val targeted=s.getJSONArray("apps").length()>0
    val semantic=jsonObjects(rule.getJSONArray("conditions")).any{it.getString("type")!="ANY"}
    return when {
        s.getJSONArray("conversation_ids").length()>0 || s.getJSONArray("sender_names").length()>0 -> 40+if(semantic)1 else 0
        semantic -> 30+if(targeted)1 else 0
        targeted -> 20
        else -> 10
    }
}
