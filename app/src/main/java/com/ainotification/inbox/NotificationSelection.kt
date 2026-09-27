package com.ainotification.inbox

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Durable private test state. No OS cancellation; uncertain items always remain visible. */
internal class NotificationSelection(private val context: Context, private val scope: CoroutineScope,
    private val engine: JevEngine = JevEngine(context),
    private val assistant: RuleAssistant = RuleAssistant(context)) {
    private val file = AtomicFile(File(context.filesDir, "notification-selection.json"))
    private val policyStore = PolicyStateStore(context)
    private val lock = Any()
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val state = MutableStateFlow(load())
    private var generation = 0L
    private val queue = Channel<Pair<CapturedNotification, JSONObject>>(64)
    private val recentJudgments=linkedMapOf<String,Pair<Long,JSONObject>>()
    init {
        scope.launch {
            for ((row, policy) in queue) {
                if (!isCurrent(row, policy)) continue
                val signature=policy.getString("id")+row.copy(snapshotId="",postedTime=0,capturedTime=0).toString()+
                    jsonObjects(policy.optJSONArray("rules")?:JSONArray()).map{PolicyContract.timeMatches(it.getJSONObject("time"),row.postedTime)}.toString()
                val cached=recentJudgments[signature]?.takeIf{System.currentTimeMillis()-it.first<60000}
                if(cached==null) synchronized(lock) {
                    val day=System.currentTimeMillis()/86400000
                    val used=if(state.value.optLong("budget_day")==day)state.value.optInt("budget_calls") else 0
                    save(copyState().put("budget_day",day).put("budget_calls",used+1))
                }
                val result = try { if(cached!=null) JSONObject(cached.second.toString()).put("reused",true)
                    else if(policy.has("rules")) StructuredPolicyRuntime(engine).classify(policy.getJSONArray("rules"), row) else engine.classify(policy, row) }
                catch (_: Exception) { review("연결 오류 또는 읽을 수 없는 결과입니다. 자동 재전송하지 않았습니다. 원문을 확인해 주세요.") }
                if(cached==null) {
                    recentJudgments[signature]=System.currentTimeMillis() to JSONObject(result.toString())
                    while(recentJudgments.size>64)recentJudgments.remove(recentJudgments.keys.first())
                }
                synchronized(lock) {
                    if (isCurrent(row, policy)) try { record(row, policy, result) }
                    catch (_: Exception) { error.value = "선별 결과를 저장하지 못했습니다. 원문을 확인해 주세요." }
                }
            }
        }
    }
    private fun load(): JSONObject = try {
        (policyStore.read() ?: JSONObject(file.openRead().bufferedReader().use { it.readText() }).also { policyStore.write(it) }).also { saved ->
            val results = saved.getJSONObject("results")
            results.keys().forEach { id ->
                val result = results.getJSONObject(id)
                if (result.optString("status") == "pending") result.put("status", "review")
                    .put("reason", "앱이 종료되어 판단이 완료되지 않았습니다. 원문을 확인해 주세요.")
            }
        }
    } catch (_: java.io.FileNotFoundException) { emptyState() }
      catch (_: Exception) { error.value = "저장된 선별 상태를 읽지 못해 선별을 중지했습니다."; emptyState() }
    private fun emptyState() = JSONObject().put("results", JSONObject())
    private fun save(value: JSONObject) {
        policyStore.write(value)
        state.value = JSONObject(value.toString())
    }
    private fun copyState() = JSONObject(state.value.toString())
    internal fun updateState(change: (JSONObject) -> Unit) = synchronized(lock) {
        val next = copyState(); change(next); save(next)
    }
    internal fun installRules(rules: JSONArray, completeOnboarding: Boolean, expectedRevision: String) = synchronized(lock) {
        require(state.value.optJSONObject("policy")?.optString("id").orEmpty() == expectedRevision) { "현재 기준이 바뀌었습니다. 다시 확인해 주세요." }
        val next = finishPending(copyState())
        val now=System.currentTimeMillis()
        val previous=next.optJSONObject("policy")
        if(previous!=null && !previous.has("rules")) next.put("legacy_policy",JSONObject(previous.toString()))
        val oldRules=jsonObjects(previous?.optJSONArray("rules") ?: JSONArray()).associateBy{it.getString("id")}
        val metadata=JSONObject()
        jsonObjects(rules).forEach { r ->
            val id=r.getString("id");val prior=previous?.optJSONObject("rule_metadata")?.optJSONObject(id)
            val unchanged=oldRules[id]?.let{canonical(it)==canonical(r)}==true
            metadata.put(id,JSONObject().put("created_at",prior?.optLong("created_at",now)?:now).put("updated_at",if(unchanged)prior?.optLong("updated_at",now)?:now else now))
        }
        next.put("policy",JSONObject().put("rule_metadata",metadata).put("id",UUID.randomUUID().toString()).put("draft_id","structured")
            .put("version",2).put("rules",JSONArray(rules.toString())).put("activated_at",previous?.optLong("activated_at") ?: now)
            .put("instruction",jsonObjects(rules).filter{it.getBoolean("enabled")}.joinToString("\n"){it.getString("name")+" → "+it.getString("action")})
            .put("conversation_bindings",JSONArray()).put("updated_at",now).put("created_at",previous?.optLong("created_at",now) ?: now))
        next.optJSONObject("policy_proposal")?.takeIf { it.optString("origin")=="local_recommendation" && it.optBoolean("validated") && it.optString("hash")==policyHash(rules) }?.let { proposal ->
            val event=proposal.optString("recommendation_event");val action=proposal.optString("recommendation_action")
            if(event in recommendationEvents && action in listOf("SHOW","HIDE","QUIET")) {
                val preferences=next.optJSONObject("context_preferences") ?: JSONObject()
                val counts=preferences.optJSONObject(event) ?: JSONObject()
                counts.put(action,(counts.optInt(action,0)+1).coerceAtMost(1000));preferences.put(event,counts);next.put("context_preferences",preferences)
            }
        }
        next.remove("policy_proposal"); next.remove("rule_proposal")
        if(completeOnboarding) next.put("onboarding_complete",true)
        generation++; save(next); error.value=null
    }
    internal fun resetRules(expectedRevision: String) = synchronized(lock) {
        require(state.value.optJSONObject("policy")?.optString("id").orEmpty()==expectedRevision) { "현재 기준이 바뀌었습니다. 다시 확인해 주세요." }
        val next=finishPending(copyState())
        listOf("policy", "legacy_policy", "rulebook", "rule_proposal", "policy_proposal", "policy_editor_input", "onboarding", "context_preferences", "conversation_profiles").forEach { next.remove(it) }
        next.put("onboarding_complete",true)
        save(next); generation++; error.value=null
    }
    fun activate(draft: JSONObject) { error.value="새 정책 편집 화면에서 내용을 검토한 뒤 적용해 주세요." }
    fun organizeCurrent() { /* Legacy items remain preserved; migration is explicit in Policy Settings. */ }
    fun toggleItem(id: String, enabled: Boolean) { error.value="새 정책 화면에서 변경해 주세요." }
    fun deleteItem(id: String) { error.value="새 정책 화면에서 변경해 주세요." }
    fun requestRuleEdit(request: String, answer: Boolean = false) { error.value="새 정책 화면에서 변경해 주세요." }
    fun applyRuleProposal() { error.value="이전 수정안은 새 정책 화면에서 다시 검토해 주세요." }
    fun discardRuleProposal() = synchronized(lock) { save(copyState().apply { remove("rule_proposal") }) }
    private fun finishPending(value: JSONObject): JSONObject {
        val results = value.getJSONObject("results")
        results.keys().forEach { key ->
            val result = results.getJSONObject(key)
            if (result.optString("status") == "pending") result.put("status", "review")
                .put("reason", "기준이 변경되거나 선별이 중지되어 판단을 완료하지 않았습니다.")
        }
        return value
    }
    fun pause() = synchronized(lock) {
        generation++
        try { save(finishPending(copyState()).apply {
            remove("policy")
            optJSONObject("rulebook")?.let { book ->
                ruleObjects(book.getJSONArray("items")).forEach { it.put("enabled", false) }
                book.put("revision", UUID.randomUUID().toString())
            }
        }); error.value = null }
        catch (_: Exception) { error.value = "선별 중지 상태를 저장하지 못했습니다." }
    }
    fun removeDraft(id: String) = synchronized(lock) {
        if (state.value.optJSONObject("policy")?.optString("draft_id") == id) pause()
    }
    fun removeResults(ids: List<String>) = synchronized(lock) {
        val next = copyState()
        ids.forEach { next.getJSONObject("results").remove(it) }
        save(next)
    }
    fun clearResults() = synchronized(lock) { save(copyState().put("results", JSONObject())) }
    fun accept(row: CapturedNotification) = synchronized(lock) {
        val policy = state.value.optJSONObject("policy") ?: return
        if (row.packageName in excludedNotificationPackages || row.packageName == context.packageName ||
            row.postedTime < policy.getLong("activated_at") || state.value.getJSONObject("results").has(row.snapshotId)) return
        // Room selection never relies on a visible title, Android groupKey or numeric notification ID.
        val bindings = policy.getJSONArray("conversation_bindings")
        if (bindings.length() > 0 && !(0 until bindings.length()).any {
                val b = bindings.getJSONObject(it)
                b.getString("package") == row.packageName && b.getString("identity") == row.conversationIdentity
            }) {
            record(row, policy, review("선택한 대화인지 확인되지 않아 판단을 보류했습니다.")); return
        }
        if(policy.has("rules")) {
            val local=StructuredPolicyRuntime(engine).classify(policy.getJSONArray("rules"),row,allowAi=false)
            if(local.optString("status")!="review") {record(row,policy,local);return}
        }
        val localReason = when {
            row.needsOriginalReview() -> "긴 내용 · 원본 확인 필요. 알림에 전체 내용이 담기지 않았을 수 있습니다."
            row.isGroupSummary -> "묶음 요약만으로 개별 알림을 판단할 수 없습니다."
            row.currentMessageText().isBlank() && row.title.isNullOrBlank() -> "알림 본문이 없어 원본 확인이 필요합니다."
            row.preview().length > 4000 || row.messagesJson.length > 16000 -> "긴 내용은 원래 앱에서 확인해 주세요."
            row.title.orEmpty().length > 500 || row.conversationTitle.orEmpty().length > 500 -> "제목이 길어 원본 확인이 필요합니다."
            System.currentTimeMillis() - row.postedTime > 600000 -> "늦게 수신된 알림입니다. 원본을 확인해 주세요."
            else -> null
        }
        if (localReason != null) { record(row, policy, review(localReason)); return }
        try {
            record(row, policy, JSONObject().put("status", "pending").put("reason", "기준에 맞는지 확인 중입니다."))
            if (queue.trySend(row to JSONObject(policy.toString())).isFailure)
                record(row, policy, review("알림이 많아 판단을 보류했습니다. 원문을 확인해 주세요."))
        } catch (_: Exception) { error.value = "선별 결과 저장에 실패했습니다. 전체 알림에서 확인해 주세요." }
    }
    private fun isCurrent(row: CapturedNotification, policy: JSONObject): Boolean = synchronized(lock) {
        state.value.optJSONObject("policy")?.optString("id") == policy.getString("id") &&
            state.value.getJSONObject("results").optJSONObject(row.snapshotId)?.optString("status") == "pending"
    }
    private fun record(row: CapturedNotification, policy: JSONObject, result: JSONObject) {
        val next = copyState()
        val results = next.getJSONObject("results")
        results.put(row.snapshotId, result.put("policy_id", policy.getString("id"))
            .put("instruction", policy.getString("instruction")).put("time", System.currentTimeMillis()))
        if (results.length() > 1000) results.keys().asSequence().toList()
            .sortedBy { results.getJSONObject(it).optLong("time") }.take(results.length() - 1000).forEach { results.remove(it) }
        save(next)
    }
    private fun review(reason: String) = JSONObject().put("status", "review").put("reason", reason)
}

internal fun JSONObject.selectionLabel(): String = when (optString("status")) {
    "match" -> "기준에 맞음"
    "outside" -> "기준 밖"
    "pending" -> "확인 중"
    else -> "확인 필요"
}
