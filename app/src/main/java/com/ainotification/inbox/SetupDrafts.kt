package com.ainotification.inbox

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Device-private drafts. A reviewed instruction is never an active notification policy. */
class SetupDrafts(private val context: Context, private val scope: CoroutineScope) {
    private val file = AtomicFile(File(context.filesDir, "setup-drafts.json"))
    val drafts = MutableStateFlow<List<JSONObject>>(load())
    val busy = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    private fun load(): List<JSONObject> = try {
        val a = JSONArray(file.openRead().bufferedReader().use { it.readText() })
        (0 until a.length()).map { a.getJSONObject(it) }
    } catch (_: java.io.FileNotFoundException) { emptyList() }
      catch (_: Exception) { emptyList() }
    private fun save(items: List<JSONObject>) {
        val stream = file.startWrite()
        try { stream.write(JSONArray(items).toString().toByteArray()); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
        drafts.value = items
    }
    fun create(instruction: String, candidates: JSONArray): String {
        val id = UUID.randomUUID().toString()
        val draft = JSONObject().put("id", id).put("instruction", instruction)
            .put("candidates", candidates).put("created", System.currentTimeMillis())
        try { save(listOf(draft) + drafts.value) }
        catch (_: Exception) { error.value = "초안을 저장하지 못했습니다."; return id }
        request(id, "create")
        return id
    }
    fun organize(id: String) {
        if (busy.value != null) return
        val original = drafts.value.find { it.optString("id") == id } ?: return
        val result = original.result() ?: return
        if (result.optString("status") != "ready") return
        val source = result.getString("normalized_instruction")
        if (original.optString("items_for") == source && original.has("items")) return
        busy.value = id
        scope.launch {
            try {
                val items = RuleAssistant(context).organize(source)
                val current = drafts.value.find { it.optString("id") == id } ?: return@launch
                if (current.result()?.optString("normalized_instruction") != source) return@launch
                val updated = JSONObject(current.toString()).put("items", items).put("items_for", source).apply { remove("items_error") }
                save(drafts.value.map { if (it.optString("id") == id) updated else it })
            } catch (_: Exception) {
                val current = drafts.value.find { it.optString("id") == id }
                if (current != null) runCatching { save(drafts.value.map { if (it.optString("id") == id) JSONObject(current.toString()).put("items_error", true) else it }) }
                error.value = "항목 정리를 완료하지 못했습니다. 원래 기준은 보관했습니다. 다시 시도해 주세요."
            } finally { busy.value = null }
        }
    }
    fun remove(id: String) {
        if (busy.value != null) return
        try { save(drafts.value.filterNot { it.optString("id") == id }) }
        catch (_: Exception) { error.value = "초안 삭제에 실패했습니다." }
    }
    fun request(id: String, action: String, answers: JSONArray = JSONArray(), candidates: JSONArray? = null) {
        if (busy.value != null) return
        val original = drafts.value.find { it.optString("id") == id } ?: return
        busy.value = id
        error.value = null
        scope.launch {
            try {
                check(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
                var body = JSONObject().put("action", action).put("answers", answers)
                val session = original.optJSONObject("session")
                if (session == null) {
                    body.put("action", "create").put("instruction", original.getString("instruction"))
                        .put("candidates", candidates ?: original.getJSONArray("candidates"))
                } else body.put("session", session)
                if (candidates != null) body.put("candidates", candidates)
                if (action == "retry" && original.has("pending")) body = original.getJSONObject("pending")
                val pending = JSONObject(original.toString()).put("pending", body)
                save(drafts.value.map { if (it.optString("id") == id) pending else it })
                val response = SetupEngine(context).process(body)
                val updated = JSONObject(original.toString()).apply { remove("pending") }
                response.optJSONObject("session")?.let { updated.put("session", it) }
                if (response.has("error")) updated.put("error", response.getString("error")) else updated.remove("error")
                if (updated.result()?.optString("normalized_instruction") != original.result()?.optString("normalized_instruction")) {
                    updated.remove("items"); updated.remove("items_for"); updated.remove("items_error")
                }
                save(drafts.value.map { if (it.optString("id") == id) updated else it })
            } catch (_: Exception) {
                error.value = "인터넷 연결을 확인해 주세요. 기준 정리를 완료하지 못했지만 초안은 보관했습니다."
            } finally { busy.value = null }
        }
    }
}

internal fun JSONObject.result(): JSONObject? = optJSONObject("session")?.optJSONObject("result")
internal fun JSONObject.draftStatus(): String = when {
    optJSONObject("session")?.optString("confirmed_instruction", "")?.let { it.isNotBlank() && it != "null" } == true -> "검토 완료 · 미적용"
    else -> when (result()?.optString("status")) {
        "ready" -> "검토 필요"
        "needs_input" -> "추가 확인"
        "waiting" -> "알림 후보 대기"
        "unsupported" -> "지원 범위 확인"
        else -> "작성 중"
    }
}

internal fun notificationCandidates(rows: List<CapturedNotification>): JSONArray = JSONArray().apply {
    rows.filterNot { it.isGroupSummary }.distinctBy { it.notificationKey }.take(20).forEach {
        put(JSONObject().put("id", it.snapshotId).put("label", "${it.appLabel} · ${it.conversationTitle ?: it.title ?: "이름 없음"}".take(500))
            .put("preview", it.preview().take(200)))
    }
}
