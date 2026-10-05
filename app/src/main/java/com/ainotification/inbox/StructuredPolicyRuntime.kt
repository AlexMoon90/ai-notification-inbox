package com.ainotification.inbox

import org.json.JSONArray
import org.json.JSONObject

internal class StructuredPolicyRuntime(private val engine:JevEngine, private val cache:NowJudgmentCache?=null, private val revision:String="") {
    fun classify(rules:JSONArray,row:CapturedNotification,allowAi:Boolean=true):JSONObject {
        val latest=row.latestMessage()
        val sender=latest?.stringOrNull("sender").orEmpty();val text=row.currentMessageText()
        val candidates=jsonObjects(rules).filter { r ->
            if(!r.getBoolean("enabled") || !PolicyContract.timeMatches(r.getJSONObject("time"),row.postedTime)) false else {
                val s=r.getJSONObject("scope");val apps=jsonStrings(s.getJSONArray("apps"));val rooms=jsonStrings(s.getJSONArray("conversation_ids"));val senders=jsonStrings(s.getJSONArray("sender_names"))
                (apps.isEmpty() || row.packageName in apps) && (rooms.isEmpty() || row.conversationIdentity in rooms) && (senders.isEmpty() || sender in senders)
            }
        }
        if(candidates.isEmpty())return JSONObject().put("status","unconfigured").put("reason","이 알림에 적용할 활성 기준이 없어 원본만 저장했습니다.")
        // Also protect older saved JSON: a qualifier on a local ANY/EMPTY condition
        // must never disappear merely because the AI editor accepted it previously.
        val allConditions=candidates.flatMap { r -> jsonObjects(r.getJSONArray("conditions"))+jsonObjects(r.getJSONArray("exceptions")).flatMap { jsonObjects(it.getJSONArray("conditions")) } }
        if(allConditions.any { it.getString("type") in listOf("ANY","EMPTY_CONTENT") && it.getString("value").isNotBlank() })
            return JSONObject().put("status","review").put("reason","기준의 조건을 다시 확인해야 하므로 표시합니다.").put("decision_stage","policy_contract")
        val empty=row.hasEmptyContent()
        val meeting=extractGroupMeeting(row)!=null
        fun local(c:JSONObject):Boolean? = when {
            c.getString("type")=="ANY"->true
            c.getString("type")=="EMPTY_CONTENT" || (c.getString("type")=="CONTENT" && c.getString("value") in setOf("알림 내용이 비어 있는 경우","알림 내용이 비어있음"))->empty
            empty->false
            c.getString("type")=="CONTENT" && c.getString("value") in setOf("미팅이나 약속에 관한 내용","미팅 관련된 내용") && meeting->true
            else->null
        }
        val questions=JSONObject();val definitions=JSONObject()
        val aliases=mutableMapOf<String,String>();val questionIds=mutableMapOf<String,String>()
        fun localGroup(conditions:JSONArray,logic:String):Boolean? {
            val values=jsonObjects(conditions).map{c->local(c)?.let{if(c.getBoolean("negated"))!it else it}}
            return if(logic=="ALL") when{false in values->false;values.all{it==true}->true;else->null}
                else when{true in values->true;values.all{it==false}->false;else->null}
        }
        fun add(key:String,conditions:JSONArray,logic:String,skip:Boolean=false) {
            definitions.put(key,JSONObject().put("conditions",conditions).put("logic",logic).put("skip",skip))
            if(skip || localGroup(conditions,logic)!=null)return
            jsonObjects(conditions).forEachIndexed { index,c ->
                if(local(c)!=null)return@forEachIndexed
                val id=key+"c"+index;val question=conditionQuestion(c)
                val existing=questionIds.getOrPut(canonical(question)){questions.put(id,question);id}
                aliases[id]=existing
            }
        }
        candidates.forEachIndexed { i,r ->
            add("r$i",r.getJSONArray("conditions"),r.getString("logic"))
            val inactive=localGroup(r.getJSONArray("conditions"),r.getString("logic"))==false
            jsonObjects(r.getJSONArray("exceptions")).forEachIndexed { j,e ->
                add("r${i}e$j",e.getJSONArray("conditions"),e.getString("logic"),inactive)
            }
        }
        if(questions.length()>100)return JSONObject().put("status","review").put("reason","한 번에 판단할 조건이 많아 원본 확인이 필요합니다.")
        val state=JSONObject().put("app",row.appLabel).put("package",row.packageName).put("title",row.title.orEmpty()).put("conversation",row.conversationTitle.orEmpty()).put("sender",sender).put("text",text)
        val result=when {
            questions.length()==0 -> JSONObject()
            !allowAi -> cache?.lookup(row,revision,JSONArray(candidates),state,questions,engine) ?: JSONObject()
            else -> cache?.evaluate(row,revision,JSONArray(candidates),state,questions,engine)
                ?: engine.evaluateConditions(JSONObject(engine.masked(state.toString())),JSONObject(engine.masked(questions.toString())))
        }
        fun matches(key:String):Boolean? {
            val definition=definitions.getJSONObject(key)
            if(definition.optBoolean("skip"))return false
            localGroup(definition.getJSONArray("conditions"),definition.getString("logic"))?.let{return it}
            val values=jsonObjects(definition.getJSONArray("conditions")).mapIndexed { index,c ->
                val value: Boolean? = local(c) ?: if(!allowAi && !result.has("answers")) null else {
                    val id=key+"c"+index
                    engine.readConditionVerdict(result,aliases.getValue(id))
                }
                value?.let { if(c.getBoolean("negated")) !it else it }
            }
            return if(definition.getString("logic")=="ALL") when { false in values->false; values.all{it==true}->true;else->null }
                else when {true in values->true;values.all{it==false}->false;else->null}
        }
        val verdicts=candidates.mapIndexed { i,r -> PolicyVerdict(r.getString("id"),policySpecificity(r),matches("r$i"),r.getString("action"),
            jsonObjects(r.getJSONArray("exceptions")).mapIndexed { j,e -> matches("r${i}e$j") to e.getString("action") }) }
        val trace=JSONArray(verdicts.map { v -> JSONObject().put("rule_id",v.id).put("priority",v.priority)
            .put("matched",v.matched ?: JSONObject.NULL).put("possible_actions",JSONArray(v.possibleActions().map { it ?: "NO_MATCH" }))
            .put("exception_matches",JSONArray(v.exceptions.map { it.first ?: JSONObject.NULL })) })
        fun diagnosed(decisionResult:JSONObject)=decisionResult.put("decision_stage","policy_resolution").put("contract_version","predicate-choice-v2").put("policy_trace",trace).put("cache_hit",result.optBoolean("cache_hit"))
        val action=resolvePolicyAction(verdicts) ?: return diagnosed(JSONObject().put("status","review").put("reason","조건 또는 예외를 확실히 판단하지 못해 표시합니다. 원문을 확인해 주세요."))
        val confirmed=verdicts.filter { it.matched==true && it.possibleActions()==setOf(action) }
        val top=confirmed.maxOfOrNull { it.priority }
        val ids=confirmed.filter { it.priority==top }.map { it.id }
        val names=candidates.filter { it.getString("id") in ids }.joinToString(" · ") { it.getString("name") }
        return diagnosed(decision(action,ids,if(names.isEmpty()) "해당하는 제외 조건이 없어 기본 표시합니다." else "$names 기준을 적용했습니다."))
    }

    private fun decision(action:String,ids:List<String>,reason:String)=JSONObject().put("status",if(action=="HIDE")"outside" else "match").put("action",action).put("matched_policy_ids",JSONArray(ids)).put("reason",reason)
}
