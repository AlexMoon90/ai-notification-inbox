package com.ainotification.inbox

import org.json.JSONArray
import org.json.JSONObject

/** Copy mutable containers, share immutable strings; never serialize a whole state to clone it. */
internal fun jsonSnapshot(value:JSONObject):JSONObject = JSONObject().also { copy ->
    value.keys().forEach { key -> copy.put(key,jsonSnapshotValue(value.get(key))) }
}
private fun jsonSnapshotValue(value:Any):Any = when(value) {
    is JSONObject -> jsonSnapshot(value)
    is JSONArray -> JSONArray().also { copy -> for(i in 0 until value.length())copy.put(jsonSnapshotValue(value.get(i))) }
    else -> value
}

/** Keep the historical instruction once per revision instead of once per notification. */
internal fun compactDecisionInstructions(state:JSONObject) {
    val instructions=state.optJSONObject("result_instructions") ?: JSONObject()
    val results=state.optJSONObject("results") ?: return
    val used=mutableSetOf<String>()
    results.keys().forEach { id ->
        val result=results.optJSONObject(id) ?: return@forEach
        val revision=result.optString("policy_id")
        if(revision.isNotEmpty()) {
            used.add(revision)
            if(result.has("instruction")){instructions.put(revision,result.get("instruction"));result.remove("instruction")}
        }
    }
    instructions.keys().asSequence().toList().filter{it !in used}.forEach{instructions.remove(it)}
    state.put("result_instructions",instructions)
}
