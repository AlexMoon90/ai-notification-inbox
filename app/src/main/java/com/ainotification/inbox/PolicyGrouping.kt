package com.ainotification.inbox

import org.json.JSONObject

/** Read-only grouping. Member IDs retain their independent edit/toggle and validation paths. */
internal data class PolicyDisplayGroup(val rules:List<JSONObject>,val common:Boolean) {
    val id get()=rules.first().getString("id")
    val apps get()=rules.flatMap(::relationApps).distinct().let{if("*" in it)listOf("*") else it}
    val title:String get() {
        val r=rules.first()
        val name=if(common) jsonObjects(r.getJSONArray("conditions")).joinToString(if(r.getString("logic")=="ALL")" + " else " / "){readableCondition(it)}+" · "+actionLabel(r.getString("action")) else r.getString("name")
        return name+(if(r.getBoolean("enabled"))"" else " · 꺼짐")+(if(common && r.getJSONArray("exceptions").length()>0)" · 예외 있음" else "")
    }
}
internal fun policyDisplayGroups(rules:List<JSONObject>):List<PolicyDisplayGroup> {
    fun broad(r:JSONObject):Boolean {
        val s=r.getJSONObject("scope")
        return s.getString("type") in listOf("ALL_APPS","SPECIFIC_APP") &&
            listOf("conversation_ids","sender_names").all{s.getJSONArray(it).length()==0} &&
            listOf("relationship","source_type").all{s.getString(it).isEmpty()}
    }
    return rules.groupBy { r ->
        if(!broad(r)) "individual:${r.getString("id")}" else canonical(JSONObject().apply {
            listOf("enabled","logic","action","conditions","exceptions","time").forEach{put(it,r.get(it))}
        })
    }.values.map{members->
        val apps=members.flatMap(::relationApps).distinct()
        PolicyDisplayGroup(members,broad(members.first()) && ("*" in apps || apps.size>1))
    }
}
internal fun displayGroupsForApp(groups:List<PolicyDisplayGroup>,pkg:String)=groups.mapNotNull { group ->
    relationRulesForApp(group.rules,pkg).takeIf{it.isNotEmpty()}?.let{PolicyDisplayGroup(it,group.common)}
}
