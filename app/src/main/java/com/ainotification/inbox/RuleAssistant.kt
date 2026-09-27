package com.ainotification.inbox

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal fun ruleObjects(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it) }
internal fun enabledRuleText(items: JSONArray) = ruleObjects(items).filter { it.getBoolean("enabled") }.joinToString("\n") { it.getString("text") }
internal fun validateRuleItems(items: JSONArray) {
    require(items.length() <= 30)
    val values = ruleObjects(items)
    require(values.map { it.getString("id") }.distinct().size == values.size)
    require(values.all { it.get("enabled") is Boolean && it.getString("id").isNotBlank() && it.getString("text").isNotBlank() })
    require(values.sumOf { it.getString("text").length + 1 } <= 9000)
    require(enabledRuleText(items).length <= 8000)
}
internal fun sourceRuleItems(source: String, segments: JSONArray): JSONArray {
    require(segments.length() in 1..30)
    var remainder = source.trim()
    val items = JSONArray()
    for (i in 0 until segments.length()) {
        val part = segments.getString(i).trim()
        require(part.isNotEmpty() && remainder.startsWith(part))
        remainder = remainder.substring(part.length).trimStart()
        items.put(JSONObject().put("id", UUID.randomUUID().toString()).put("text", part).put("enabled", true))
    }
    require(remainder.isBlank()) // Exact coverage, no invented words, dropped exceptions, reordering or omissions.
    validateRuleItems(items)
    return items
}

internal class RuleAssistant(context: Context, private val engine: SetupEngine = SetupEngine(context)) {
    private fun string() = JSONObject().put("type", "string")
    private fun array(item: JSONObject) = JSONObject().put("type", "array").put("items", item)
    private fun obj(vararg fields: Pair<String, JSONObject>) = JSONObject().put("type", "object")
        .put("properties", JSONObject().apply { fields.forEach { put(it.first, it.second) } })
        .put("required", JSONArray(fields.map { it.first })).put("additionalProperties", false)
    fun organize(source: String): JSONArray {
        val schema = obj("segments" to array(string()))
        val input = JSONObject().put("instruction", source)
        val result = engine.structured("organize_rules", "Split the user's reviewed notification instruction into independently switchable list items. Return exact contiguous substrings in original order covering ALL original text; do not paraphrase. Group dependent clauses, AND/OR conditions, ONLY restrictions, shared scope, and exceptions with their governing rule. An exception referring to a previous clause must NOT become a separate switch. Split independent app/content conditions so users can control each. If unsure keep the dependent block together. Never add facts or discard punctuation. A single segment is allowed when splitting would change meaning.", schema, input)
        val items = sourceRuleItems(source, result.getJSONArray("segments"))
        if (items.length() > 1) {
            val audit = engine.structured("organize_rules_review", "Audit whether these exact segments can be toggled independently without orphaning shared scope, exceptions, AND/OR or ONLY conditions. Return safe=false if any segment depends on another segment for its meaning. Splitting separate app-specific ad exclusions is safe. Exceptions must remain with their base rule. Do not judge whether the user's filter is desirable.",
                obj("safe" to JSONObject().put("type", "boolean")), input.put("segments", result.getJSONArray("segments")))
            if (!audit.getBoolean("safe")) return sourceRuleItems(source, JSONArray().put(source))
        }
        return items
    }
    fun propose(book: JSONObject, conversation: JSONArray): JSONObject {
        val item = obj("id" to string(), "text" to string(), "enabled" to JSONObject().put("type", "boolean"))
        val schema = obj("status" to string().put("enum", JSONArray(listOf("ready", "needs_input", "unsupported"))),
            "message" to string(), "question" to string(), "options" to array(string()), "items" to array(item))
        val input = JSONObject().put("current_items", book.getJSONArray("items")).put("conversation", conversation)
            .put("has_fixed_conversation_scope", book.getJSONArray("bindings").length() > 0)
        val prompt = """You help manage an Android notification inbox's rules. Reply in Korean. Return the complete proposed item list, including unchanged and disabled items. Existing IDs MUST be preserved when editing/toggling a rule; new items use unique new_ IDs. Omit an item only when the user requests deletion. Preserve every unaffected scope, exception, AND/OR, ordering and enabled flag. Check/uncheck means enabled true/false, not changing the item's text. 'Add' keeps other items; 'replace/change' only changes the specified scope. Never infer precedence just from recency. Ask a specific question with 2-3 short selectable options if target, conflict or intention is ambiguous; status needs_input must have a question and has no effect. Multiple exclusions plus explicit exceptions are supported, not contradictory. Keep dependent exceptions with their base item. Plain 'don't receive/block' means inbox filtering, not control of Android. System UI/android notifications are always excluded before capture. No unseen history, participant rosters, timers, automatic replies or external lookups. Existing selected-conversation scope is fixed for this rule set; don't silently broaden it or invent another room. If a new target needs binding, return unsupported and explain that the user must use '기준 만들기' to choose the target. When no bindings exist, named apps and visible-content conditions are allowed but unidentified company/family rooms require target selection. Never claim changes are already applied. For ready, question and options must be empty. The user will review the exact list before applying. Limit to 30 items and 8000 characters total."""
        var result = engine.structured("edit_rules", prompt, schema, input)
        validateProposal(book, result)
        if (result.getString("status") == "ready") {
            result = engine.structured("edit_rules_review", prompt + "\nFINAL REVIEW: Verify the proposed list against ALL current items and the user's requests/answers. Correct omissions/unrequested flag changes. If the user intention is unresolved ask a question. Return the final proposed list, never execute anything.", schema, input.put("proposal_to_review", result))
            validateProposal(book, result)
        }
        return result
    }
    internal fun validateProposal(book: JSONObject, result: JSONObject) {
        require(result.getString("message").length <= 2000 && result.getString("question").length <= 2000)
        val status = result.getString("status")
        require(status in listOf("ready", "needs_input", "unsupported"))
        val options = result.getJSONArray("options")
        require(options.length() <= 4)
        for (i in 0 until options.length()) require(options.getString(i).length in 1..300)
        if (status == "needs_input") require(result.getString("question").isNotBlank())
        if (status == "ready") {
            require(result.getString("question").isEmpty() && options.length() == 0)
            val items = result.getJSONArray("items"); validateRuleItems(items)
            val ids = ruleObjects(book.getJSONArray("items")).map { it.getString("id") }.toSet()
            require(ruleObjects(items).all { it.getString("id") in ids || it.getString("id").startsWith("new_") })
        }
    }
}
