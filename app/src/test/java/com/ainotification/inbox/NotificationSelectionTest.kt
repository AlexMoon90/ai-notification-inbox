package com.ainotification.inbox

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationSelectionTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var scope: CoroutineScope
    @Before fun setup() { context.deleteDatabase("policies.db"); context.filesDir.listFiles()?.forEach { it.delete() }; scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }
    @After fun cleanup() { scope.cancel() }
    private fun answer(vararg values: Pair<String, Double>) = JSONObject().put("answers", JSONObject().apply {
        values.forEach { (key, value) -> put(key, JSONObject().put("type", "noul").put("noul", value)) }
    }).put("usage", JSONObject().put("input_tokens", 500).put("output_tokens", 20))
    private fun policy() = JSONObject().put("id", "policy-1").put("draft_id", "draft-1").put("activated_at", System.currentTimeMillis() - 1000)
        .put("instruction", "모든 앱에서 납부 요청만 알려줘").put("conversation_bindings", JSONArray())
        .put("notify_threshold", .8).put("outside_threshold", .05).put("evidence_threshold", .9).put("match_evidence_threshold", .8)
    private fun row() = previewNotifications()[1].copy(snapshotId = "fixture", postedTime = System.currentTimeMillis(), capturedTime = System.currentTimeMillis())
    private fun selection(engine: JevEngine, p: JSONObject = policy(), results: JSONObject = JSONObject()): NotificationSelection {
        PolicyStateStore(context).write(JSONObject().put("policy", p).put("results", results))
        return NotificationSelection(context, scope, engine)
    }
    @Test fun jevCannotCompilePoliciesOrMakeSetupCalls() {
        var calls=0
        assertThrows(IllegalStateException::class.java) { JevEngine(context) { calls++; error("never") }.compile("납부 알림",JSONArray()) }
        assertEquals(0,calls)
    }
    @Test fun thresholdsKeepAmbiguousAndMissingEvidenceVisible() {
        for ((match, evidence, expected) in listOf(Triple(.99, .99, "match"), Triple(.01, .99, "outside"), Triple(.3, .99, "review"), Triple(.01, .5, "review"), Triple(.92, .85, "match"), Triple(.03, .89, "review"), Triple(.03, .91, "outside"))) {
            assertEquals(expected, JevEngine(context) { answer("matches" to match, "sufficient" to evidence) }.classify(policy(), row()).getString("status"))
        }
    }
    @Test fun duplicatesOldNotificationsAndSystemSourcesNeverCallApi() {
        var calls = 0
        val manager = selection(JevEngine(context) { calls++; answer("matches" to .99, "sufficient" to .99) })
        manager.accept(row().copy(postedTime = 1))
        manager.accept(row().copy(packageName = "android"))
        assertEquals(0, calls)
        val event = row(); manager.accept(event); manager.accept(event)
        assertEquals(1, calls)
        assertEquals("match", manager.state.value.getJSONObject("results").getJSONObject(event.snapshotId).getString("status"))
    }
    @Test fun longKakaoMissingBodySummariesAndUnknownRoomsRemainReviewWithoutCalls() {
        var calls = 0
        val engine = JevEngine(context) { calls++; error("must not call") }
        val manager = selection(engine)
        listOf(row().copy(snapshotId = "long", packageName = "com.kakao.talk", text = "가".repeat(500)),
            row().copy(snapshotId = "empty", text = null), row().copy(snapshotId = "summary", isGroupSummary = true)).forEach { manager.accept(it) }
        val bound = selection(engine, policy().put("conversation_bindings", JSONArray().put(JSONObject().put("package", "com.kakao.talk").put("identity", "known"))))
        bound.accept(row())
        assertEquals(0, calls)
        assertEquals("review", bound.state.value.getJSONObject("results").getJSONObject("fixture").getString("status"))
        assertEquals(3, manager.state.value.getJSONObject("results").length())
    }
    @Test fun errorsAndMalformedAnswersNeverBecomeOutside() {
        for (mode in listOf("network", "bad_response")) {
            var calls = 0
            val manager = selection(JevEngine(context) { calls++; if (mode == "network") error("offline") else answer("matches" to 2.0, "sufficient" to .99) })
            manager.accept(row())
            assertEquals("review", manager.state.value.getJSONObject("results").getJSONObject("fixture").getString("status"))
        }
    }
    @Test fun pausedInFlightResultCannotPublishAndInterruptedWorkRecoversAsReview() {
        lateinit var manager: NotificationSelection
        manager = selection(JevEngine(context) { manager.pause(); answer("matches" to .99, "sufficient" to .99) })
        manager.accept(row())
        assertFalse(manager.state.value.has("policy"))
        assertEquals("review", manager.state.value.getJSONObject("results").getJSONObject("fixture").getString("status"))
        val pending = JSONObject().put("old", JSONObject().put("status", "pending"))
        val restored = selection(JevEngine(context) { error("never") }, results = pending)
        assertEquals("review", restored.state.value.getJSONObject("results").getJSONObject("old").getString("status"))
    }
    @Test fun usageLogsContainNoNotificationOrInstructionAndLatestMessageIsUsed() {
        val event = row().copy(text = "OLD SECRET", messagesJson = JSONArray()
            .put(JSONObject().put("timestamp", 1).put("text", "older"))
            .put(JSONObject().put("timestamp", 2).put("text", "newer private@example.com").put("sender", "test sender")).toString())
        val engine = JevEngine(context) { request ->
            val n = request.getJSONObject("state").getJSONObject("notification")
            assertTrue(n.getString("text").startsWith("newer [ID_"))
            assertFalse(request.toString().contains("OLD SECRET"))
            answer("matches" to .99, "sufficient" to .99)
        }
        engine.classify(policy(), event)
        val log = File(context.filesDir, "jev-usage.jsonl").readText()
        assertFalse(log.contains("newer")); assertFalse(log.contains("납부")); assertFalse(log.contains("private@example"))
        assertTrue(JSONObject(log.trim()).getDouble("estimated_usd") > 0)
    }
    @Test fun activationRequiresLocalConfirmation() {
        var calls = 0
        val manager = NotificationSelection(context, scope, JevEngine(context) { calls++; answer("supported" to .99, "needs_conversation" to .01) })
        manager.activate(JSONObject().put("id", "draft").put("session", JSONObject().put("confirmed_instruction", JSONObject.NULL)))
        assertEquals(0, calls); assertFalse(manager.state.value.has("policy")); assertNotNull(manager.error.value)
        val instruction = "모든 앱의 납부 요청만 알려줘"
        manager.activate(JSONObject().put("id", "draft").put("session", JSONObject().put("confirmed_instruction", instruction)
            .put("result", JSONObject().put("status", "ready").put("normalized_instruction", instruction))))
        assertEquals(0, calls); assertFalse(manager.state.value.has("policy")); assertNotNull(manager.error.value)

    }
    @Test fun activationFailureExplainsCauseAndNeverExposesProviderDetails() {
        assertTrue(activationErrorMessage(ActivationFailure("unsupported"), "compile").contains("인터넷 문제는 아닙니다"))
        assertTrue(activationErrorMessage(ActivationFailure("binding_missing"), "binding").contains("대화 식별 정보"))
        assertTrue(activationErrorMessage(JevHttpFailure(401), "compile").contains("인증"))
        assertTrue(activationErrorMessage(JevHttpFailure(429), "compile").contains("이용 한도"))
        assertTrue(activationErrorMessage(java.io.IOException("SECRET"), "compile").contains("인터넷 연결"))
        assertTrue(activationErrorMessage(java.io.IOException("SECRET"), "save").contains("저장 공간"))
        assertFalse(activationErrorMessage(IllegalArgumentException("SECRET"), "compile").contains("SECRET"))
    }
    @Test fun unsupportedActivationPreservesExistingPolicyAndReportsSemanticFailure() {
        val manager = selection(JevEngine(context) { answer("supported" to .2, "needs_conversation" to .01) })
        val instruction = "이전에 받지 못한 전체 대화를 조회해줘"
        manager.activate(JSONObject().put("id", "replacement").put("session", JSONObject().put("confirmed_instruction", instruction)
            .put("result", JSONObject().put("status", "ready").put("normalized_instruction", instruction))))
        assertEquals("policy-1", manager.state.value.getJSONObject("policy").getString("id"))
        assertTrue(manager.error.value!!.contains("새 정책"))
    }

    @Test fun repeatedUpdatedNotificationReusesJudgmentButChangedTextAndPolicyDoNot() {
        var calls=0
        val manager=selection(JevEngine(context){calls++;answer("matches" to .99,"sufficient" to .99)})
        val first=row();manager.accept(first)
        manager.state.value.put("budget_calls",200)
        manager.accept(first.copy(snapshotId="repeat",postedTime=first.postedTime+1))
        assertEquals(1,calls)
        assertTrue(manager.state.value.getJSONObject("results").getJSONObject("repeat").getBoolean("reused"))
        manager.accept(first.copy(snapshotId="changed",text="Changed"))
        assertEquals("match",manager.state.value.getJSONObject("results").getJSONObject("changed").getString("status"))
        manager.state.value.getJSONObject("policy").put("id","new-revision")
        manager.accept(first.copy(snapshotId="new-policy"))
        assertFalse(manager.state.value.getJSONObject("results").getJSONObject("new-policy").optBoolean("reused"))
        assertEquals(3,calls)
        assertEquals(202,manager.state.value.getInt("budget_calls"))
    }
    @Test fun explicitEmptyHideRunsBeforeSummaryAndBudgetGuards() {
        var calls=0
        val r=JSONObject().put("id","empty").put("name","빈 내용 숨김").put("enabled",true).put("scope",PolicyContract.emptyScope()).put("conditions",JSONArray().put(JSONObject().put("type","EMPTY_CONTENT").put("value","").put("negated",false))).put("logic","ALL").put("action","HIDE").put("exceptions",JSONArray()).put("time",PolicyContract.emptyTime())
        val manager=selection(JevEngine(context){calls++;error("no AI")},policy().put("rules",JSONArray().put(r)))
        manager.state.value.put("budget_day",System.currentTimeMillis()/86400000).put("budget_calls",200)
        manager.accept(row().copy(title=null,text=null,bigText=null,subText=null,conversationTitle=null,messagesJson="[]",isGroupSummary=true))
        assertEquals("outside",manager.state.value.getJSONObject("results").getJSONObject("fixture").getString("status"))
        assertEquals(0,calls);assertEquals(200,manager.state.value.getInt("budget_calls"))
    }

    @Test fun dailyUsageIsMeasuredButDoesNotBlockAtAnyPreviousLimit() {
        var calls=0
        val manager=selection(JevEngine(context){calls++;answer("matches" to .99,"sufficient" to .99)})
        for(count in listOf(200,1000,10000)) {
            manager.state.value.put("budget_day",System.currentTimeMillis()/86400000).put("budget_calls",count)
            manager.accept(row().copy(snapshotId="usage-$count",text="Unique notification $count"))
            assertEquals("match",manager.state.value.getJSONObject("results").getJSONObject("usage-$count").getString("status"))
            assertEquals(count+1,manager.state.value.getInt("budget_calls"))
        }
        assertEquals(3,calls)
    }

}
