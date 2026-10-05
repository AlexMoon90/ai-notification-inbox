package com.ainotification.inbox

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Native port of policy_setup_luna_v2. Drafts only, never an execution policy. */
internal class SetupEngine(private val context: Context,
    private val transport: ((JSONObject) -> JSONObject)? = null) {
    private val config by lazy { JSONObject(context.assets.open("setup-config.json").bufferedReader().use { it.readText() }) }
    private fun arr(vararg values: Any) = JSONArray(values.toList())
    private fun objects(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }
    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
    private fun candidates(a: JSONArray) {
        require(a.length() <= 100)
        val ids = objects(a).map {
            require(it.keys().asSequence().toSet() == setOf("id", "label", "preview"))
            listOf("id", "label", "preview").forEach { k -> require(it.get(k) is String && it.getString(k).length <= 500) }
            require(it.getString("id").isNotBlank()); it.getString("id")
        }
        require(ids.distinct().size == ids.size)
    }
    fun process(body: JSONObject): JSONObject {
        val action = body.getString("action")
        val s = if (action == "create") {
            val instruction = body.getString("instruction")
            require(instruction.isNotBlank() && instruction.length <= 8000)
            JSONObject().put("version", 1).put("instruction", instruction).put("current_instruction", "")
                .put("candidates", body.optJSONArray("candidates") ?: JSONArray())
                .put("selected_candidate_ids", JSONArray()).put("answers", JSONArray()).put("metrics", JSONArray())
                .put("result", JSONObject.NULL).put("confirmed_instruction", JSONObject.NULL)
        } else JSONObject(body.getJSONObject("session").toString())
        candidates(s.getJSONArray("candidates"))
        try {
            when (action) {
                "answer" -> objects(body.getJSONArray("answers")).forEach { answer(s, it) }
                "refresh" -> {
                    val list = body.getJSONArray("candidates"); candidates(list)
                    s.put("candidates", list).put("result", JSONObject.NULL).put("confirmed_instruction", JSONObject.NULL)
                }
                "confirm" -> {
                    val result = s.getJSONObject("result")
                    require(result.getString("status") == "ready"); validate(result, s)
                    s.put("confirmed_instruction", result.getString("normalized_instruction"))
                    return JSONObject().put("session", s)
                }
                "create", "retry" -> Unit
                else -> error("Unknown action")
            }
            var result = step(s)
            if (result.getString("status") == "ready" &&
                (s.getString("current_instruction").isNotEmpty() || s.getJSONArray("answers").length() > 0)) {
                result = step(s, result.getString("normalized_instruction"))
            }
            val ko = Regex("[가-힣]").containsMatchIn(s.getString("instruction"))
            val message = when(result.getString("status")) {
                "ready" -> if (ko) "검토할 지시 초안입니다. 아직 알림 기준에 적용되지 않았습니다." else "Draft for review. It has not been applied to your notification rules."
                "needs_input" -> if (ko) "아래의 부족한 정보만 확인하면 이어서 정리할 수 있습니다." else "Please provide the missing information below to continue this draft."
                "waiting" -> if (ko) "대상을 선택하는 데 필요한 알림 후보를 기다리고 있습니다. 초안을 저장해 이어갈 수 있습니다." else "Waiting for notification candidates needed to select the target. You can save and resume this draft."
                else -> if (ko) "현재 제공되는 알림 정보로는 이 조건을 확인할 수 없습니다. 기준은 적용되지 않았습니다." else "This condition cannot be verified using the available notification fields. No rule has been applied."
            }
            result.put("message", message).put("change_summary", if (result.getString("status") == "ready") result.getString("normalized_instruction") else "")
            objects(result.getJSONArray("questions")).filter { it.getString("kind") == "conversation" }.forEach { it.put("field", "scope") }
            return JSONObject().put("session", s)
        } catch (_: Exception) {
            s.put("result", JSONObject.NULL).put("confirmed_instruction", JSONObject.NULL)
            return JSONObject().put("session", s).put("error", "기준을 정리하지 못했습니다. 인터넷 연결과 API 사용 상태를 확인하고 다시 시도해 주세요. 입력은 보관했습니다.")
        }
    }
    private fun answer(s: JSONObject, a: JSONObject) {
        val r = s.getJSONObject("result"); require(r.getString("status") == "needs_input")
        val q = objects(r.getJSONArray("questions")).single { it.getString("id") == a.getString("id") }
        val turn = s.getJSONArray("metrics").length()
        require(objects(s.getJSONArray("answers")).none { it.getInt("turn") == turn && it.getJSONObject("question").getString("id") == a.getString("id") })
        val ids = strings(a.optJSONArray("options") ?: JSONArray())
        val text = a.optString("text", "").trim()
        require(ids.isNotEmpty() xor text.isNotEmpty()); require(text.length <= 2000)
        require(ids.distinct().size == ids.size)
        val options = objects(q.getJSONArray("options")).associateBy { it.getString("id") }
        require(ids.all { it in options })
        if (q.getString("kind") == "single") require(ids.size <= 1)
        if (q.getString("kind") == "text") require(ids.isEmpty())
        val selected = JSONArray(ids.map { options.getValue(it) })
        val available = objects(s.getJSONArray("candidates")).map { it.getString("id") }
        objects(selected).filterNot { it.isNull("candidate_id") }.forEach {
            val id = it.getString("candidate_id"); require(id in available)
            if (id !in strings(s.getJSONArray("selected_candidate_ids"))) s.getJSONArray("selected_candidate_ids").put(id)
        }
        s.getJSONArray("answers").put(JSONObject().put("turn", turn).put("question", q).put("selected", selected)
            .put("text", text.takeIf { it.isNotEmpty() } ?: JSONObject.NULL))
        s.put("confirmed_instruction", JSONObject.NULL)
    }
    internal fun requestPayload(s: JSONObject, draft: String? = null): JSONObject {
        val c = JSONObject()
        listOf("instruction", "current_instruction", "candidates", "selected_candidate_ids", "answers").forEach { c.put(it, s.get(it)) }
        c.put("capabilities", config.getJSONObject("capabilities"))
        c.put("recorded_answers", JSONArray(objects(s.getJSONArray("answers")).map {
            val q = it.getJSONObject("question")
            JSONObject().put("question", q.getString("prompt")).put("field", q.getString("field"))
                .put("selected_labels", JSONArray(objects(it.getJSONArray("selected")).map { o -> o.getString("label") })).put("text", it.get("text"))
        }))
        c.put("candidate_availability", JSONObject().put("count", s.getJSONArray("candidates").length())
            .put("selection_recorded", s.getJSONArray("selected_candidate_ids").length() > 0))
        if (draft != null) c.put("draft_to_review", draft)
        val serialized = mask(c.toString()); require(serialized.length <= 40000)
        return JSONObject().put("model", config.getString("model")).put("store", false).put("max_output_tokens", 2200)
            .put("reasoning", JSONObject().put("effort", "low"))
            .put("input", arr(JSONObject().put("role", "developer").put("content", config.getString(if (draft == null) "prompt" else "review_prompt")),
                JSONObject().put("role", "user").put("content", serialized)))
            .put("text", JSONObject().put("format", JSONObject().put("type", "json_schema").put("name", "policy_clarification")
                .put("strict", true).put("schema", config.getJSONObject("schema"))))
    }
    private fun step(s: JSONObject, review: String? = null): JSONObject {
        require(strings(s.getJSONArray("selected_candidate_ids")).all { id -> objects(s.getJSONArray("candidates")).any { it.getString("id") == id } })
        val payload = requestPayload(s, review)
        s.put("result", JSONObject.NULL).put("confirmed_instruction", JSONObject.NULL)
        val metric = JSONObject().put("model", config.getString("model")).put("reasoning_effort", "low")
            .put("prompt_version", "luna-v2-android").put("purpose", if (review == null) "clarify" else "semantic_review")
            .put("request_bytes", payload.toString().toByteArray().size).put("api_attempts", 1)
            .put("input_tokens", JSONObject.NULL).put("output_tokens", JSONObject.NULL).put("cached_tokens", JSONObject.NULL)
            .put("estimated_usd", JSONObject.NULL).put("error", JSONObject.NULL)
        val start = System.nanoTime()
        try {
            val raw = transport?.invoke(payload) ?: http(payload)
            raw.optJSONObject("usage")?.let { u ->
                val input = u.optLong("input_tokens", -1); val output = u.optLong("output_tokens", -1)
                val cached = u.optJSONObject("input_tokens_details")?.optLong("cached_tokens", 0) ?: 0
                if (input >= 0 && output >= 0 && cached in 0..input) metric.put("input_tokens", input).put("output_tokens", output).put("cached_tokens", cached)
                    .put("estimated_usd", ((input - cached) * .2 + cached * .02 + output * 1.2) / 1e6)
            }
            require(raw.optString("status") == "completed")
            val contents = objects(raw.getJSONArray("output")).filter { it.optString("type") == "message" }.flatMap { objects(it.getJSONArray("content")) }
            require(contents.none { it.optString("type") == "refusal" })
            val text = contents.filter { it.optString("type") == "output_text" }.single().getString("text")
            val result = JSONObject(text); validate(result, s)
            if (result.getString("status") == "ready" && s.getString("current_instruction").isEmpty() &&
                s.getJSONArray("answers").length() == 0 && s.getJSONArray("selected_candidate_ids").length() == 0) {
                result.put("normalized_instruction", s.getString("instruction").trim())
                result.put("change_summary", s.getString("instruction").trim())
                s.put("normalization_mode", "verbatim_clear_instruction")
            }
            s.put("result", result)
            return result
        } catch (_: Exception) { metric.put("error", "request_or_validation_failed"); throw IllegalStateException("Setup failed") }
        finally {
            metric.put("latency_ms", (System.nanoTime() - start) / 1e6)
            s.getJSONArray("metrics").put(metric)
            // Separate usage-only ledger survives draft deletion. Never logs inputs or credentials.
            if (transport == null) runCatching { File(context.filesDir, "setup-usage.jsonl").appendText(metric.toString() + "\n") }
        }
    }
    internal fun structured(purpose: String, prompt: String, spec: JSONObject, input: JSONObject): JSONObject {
        val replyTask=purpose in setOf("reply_assistant","reply_assistant_rewrite")
        val serialized = input.toString()
        require(serialized.length <= 48000)
        val payload = JSONObject().put("model", config.getString("model")).put("store", false)
            .put("max_output_tokens", if(replyTask)1800 else 6000).put("reasoning", JSONObject().put("effort", "low"))
            .put("input", arr(JSONObject().put("role", "developer").put("content", prompt), JSONObject().put("role", "user").put("content", serialized)))
            .put("text", JSONObject().put("format", JSONObject().put("type", "json_schema").put("name", if(replyTask)"reply_assistant" else "rule_management").put("strict", true).put("schema", spec)))
        val start = System.nanoTime()
        val metric = JSONObject().put("model", config.getString("model")).put("purpose", purpose).put("reasoning_effort", "low")
            .put("request_bytes", payload.toString().toByteArray().size).put("api_attempts", 1)
        try {
            val raw = transport?.invoke(payload) ?: http(payload)
            raw.optJSONObject("usage")?.let { u ->
                val i = u.getLong("input_tokens"); val o = u.getLong("output_tokens")
                val c = u.optJSONObject("input_tokens_details")?.optLong("cached_tokens", 0) ?: 0
                require(i >= 0 && o >= 0 && c in 0..i)
                metric.put("input_tokens", i).put("output_tokens", o).put("cached_tokens", c)
                    .put("estimated_usd", ((i-c)*.2 + c*.02 + o*1.2)/1e6)
            }
            require(raw.optString("status") == "completed")
            val content = objects(raw.getJSONArray("output")).filter { it.optString("type") == "message" }.flatMap { objects(it.getJSONArray("content")) }
            require(content.none { it.optString("type") == "refusal" })
            val result = JSONObject(content.single { it.optString("type") == "output_text" }.getString("text"))
            schema(result, spec); metric.put("ok", true)
            return result
        } catch (e: Exception) { metric.put("ok", false); throw e }
        finally {
            metric.put("latency_ms", (System.nanoTime()-start)/1e6)
            if (transport == null) synchronized(SetupEngine::class.java) {
                runCatching {
                    val log=File(context.filesDir,if(replyTask)"reply-assistant-usage.jsonl" else "rule-management-usage.jsonl")
                    if(replyTask && log.length()>256*1024)log.writeText("")
                    log.appendText(metric.toString()+"\n")
                }
            }
        }
    }
    private fun http(payload: JSONObject): JSONObject {
        val key = context.assets.open("test-openai-key").bufferedReader().use { it.readText().trim() }
        val connection = URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"; connection.connectTimeout = 15000; connection.readTimeout = 60000; connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $key"); connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            check(connection.responseCode == 200) // Do not log error bodies or auth headers.
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
    internal fun validate(r: JSONObject, s: JSONObject) {
        schema(r, config.getJSONObject("schema"))
        val status = r.getString("status"); val questions = objects(r.getJSONArray("questions"))
        require(questions.size <= 2 && questions.map { it.getString("id") }.distinct().size == questions.size)
        require((status == "needs_input") == questions.isNotEmpty())
        if (status == "ready") require(r.getString("normalized_instruction").isNotBlank() && r.getJSONArray("unresolved").length() == 0)
        else { require(r.isNull("normalized_instruction")); if (status in listOf("waiting", "unsupported")) require(r.getJSONArray("unresolved").length() > 0) }
        val refs = strings(r.getJSONArray("referenced_candidate_ids")); val selected = strings(s.getJSONArray("selected_candidate_ids"))
        val available = objects(s.getJSONArray("candidates")).associateBy { it.getString("id") }
        require(refs.distinct().size == refs.size && refs.all { it in selected && it in available })
        if (status == "ready") require(selected.all { it in refs })
        questions.forEach { q ->
            require(q.getString("id").isNotBlank() && q.getString("prompt").isNotBlank())
            val opts = objects(q.getJSONArray("options")); val kind = q.getString("kind")
            require(opts.map { it.getString("id") }.distinct().size == opts.size)
            require((kind == "text") == opts.isEmpty())
            opts.forEach { o ->
                require(o.getString("id").isNotBlank() && o.getString("label").isNotBlank())
                if (kind == "conversation") require(!o.isNull("candidate_id") && available[o.getString("candidate_id")]?.getString("label") == o.getString("label"))
                else require(o.isNull("candidate_id"))
            }
        }
    }
    private fun schema(value: Any?, spec: JSONObject) {
        val types = if (spec.get("type") is JSONArray) strings(spec.getJSONArray("type")) else listOf(spec.getString("type"))
        val type = when (value) { null, JSONObject.NULL -> "null"; is JSONObject -> "object"; is JSONArray -> "array"; is String -> "string"; is Boolean -> "boolean"; else -> "invalid" }
        require(type in types)
        if (spec.has("enum")) require(value in strings(spec.getJSONArray("enum")))
        if (value is JSONObject) {
            val properties = spec.getJSONObject("properties")
            require(value.keys().asSequence().toSet() == properties.keys().asSequence().toSet())
            properties.keys().forEach { schema(value.get(it), properties.getJSONObject(it)) }
        }
        if (value is JSONArray) for (i in 0 until value.length()) schema(value.get(i), spec.getJSONObject("items"))
    }
    private fun mask(text: String): String = text.replace(Regex("sk-[A-Za-z0-9_-]+"), "<API_KEY>")
        .replace(Regex("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}"), "<EMAIL>")
        .replace(Regex("(?<!\\d)01[016789][- ]?\\d{3,4}[- ]?\\d{4}(?!\\d)"), "<PHONE>")
}
