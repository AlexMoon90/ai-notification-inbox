package com.ainotification.inbox

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class StandaloneSetupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun ready(refs: JSONArray = JSONArray()) = JSONObject().put("status", "ready").put("message", "draft")
        .put("questions", JSONArray()).put("unresolved", JSONArray()).put("normalized_instruction", "정리한 지시")
        .put("referenced_candidate_ids", refs).put("change_kind", "new").put("change_summary", "draft")
    private fun response(result: JSONObject) = JSONObject().put("status", "completed")
        .put("usage", JSONObject().put("input_tokens", 100).put("output_tokens", 30).put("input_tokens_details", JSONObject().put("cached_tokens", 20)))
        .put("output", JSONArray().put(JSONObject().put("type", "message").put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", result.toString())))))
    private fun create() = JSONObject().put("action", "create").put("instruction", "회사 단톡 답변 요청만 알려줘")
        .put("candidates", JSONArray().put(JSONObject().put("id", "a").put("label", "대화 A").put("preview", "합성 메시지")))
    private fun question() = ready().put("status", "needs_input").put("normalized_instruction", JSONObject.NULL)
        .put("questions", JSONArray().put(JSONObject().put("id", "target").put("field", "scope").put("kind", "conversation")
            .put("prompt", "어느 대화인가요?").put("reason", "대상 필요").put("options", JSONArray().put(JSONObject().put("id", "o1").put("label", "대화 A").put("candidate_id", "a")))))
    private fun answer(s: JSONObject) = JSONObject().put("action", "answer").put("session", s)
        .put("answers", JSONArray().put(JSONObject().put("id", "target").put("options", JSONArray().put("o1"))))

    @Test fun payloadMatchesPreviouslyTestedPythonContract() {
        val fixture = JSONObject(javaClass.classLoader!!.getResourceAsStream("luna-request-parity.json")!!.bufferedReader().use { it.readText() })
        val expected = fixture.getJSONObject("payload")
        val actual = SetupEngine(context).requestPayload(fixture.getJSONObject("session"))
        assertEquals(expected.getString("model"), actual.getString("model"))
        assertEquals(expected.getJSONArray("input").getJSONObject(0).getString("content"), actual.getJSONArray("input").getJSONObject(0).getString("content"))
        fun canonical(value: Any?): String = when (value) {
            is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix="{", postfix="}") { it + ":" + canonical(value.get(it)) }
            is JSONArray -> (0 until value.length()).joinToString(prefix="[", postfix="]") { canonical(value.get(it)) }
            else -> value.toString()
        }
        assertEquals(canonical(expected.getJSONObject("text")), canonical(actual.getJSONObject("text")))
        assertEquals(canonical(JSONObject(expected.getJSONArray("input").getJSONObject(1).getString("content"))),
            canonical(JSONObject(actual.getJSONArray("input").getJSONObject(1).getString("content"))))
    }
    @Test fun clearInstructionPreservedAndLunaContractUnchanged() {
        val engine = SetupEngine(context) { payload ->
            assertEquals("gpt-5.6-luna", payload.getString("model"))
            assertEquals("low", payload.getJSONObject("reasoning").getString("effort"))
            assertFalse(payload.getBoolean("store")); assertFalse(payload.has("temperature"))
            assertTrue(payload.getJSONObject("text").getJSONObject("format").getBoolean("strict"))
            response(ready())
        }
        val input = create().put("instruction", "모든 앱에서 일정 변경만 알려줘").put("candidates", JSONArray())
        val s = engine.process(input).getJSONObject("session")
        assertEquals(input.getString("instruction"), s.getJSONObject("result").getString("normalized_instruction"))
        assertTrue(s.isNull("confirmed_instruction")); assertEquals(1, s.getJSONArray("metrics").length())
    }
    @Test fun conversationAnswerIsReviewedThenOnlyLocallyConfirmed() {
        var calls = 0
        val engine = SetupEngine(context) { payload ->
            calls++
            if (calls == 3) assertTrue(payload.getJSONArray("input").getJSONObject(0).getString("content").contains("FINAL DRAFT REVIEW"))
            response(if (calls == 1) question() else ready(JSONArray().put("a")))
        }
        val s = engine.process(answer(engine.process(create()).getJSONObject("session"))).getJSONObject("session")
        assertEquals(3, calls); assertEquals("a", s.getJSONArray("selected_candidate_ids").getString(0))
        assertEquals("ready", s.getJSONObject("result").getString("status"))
        assertTrue(s.isNull("confirmed_instruction"))
        val confirmed = engine.process(JSONObject().put("action", "confirm").put("session", s)).getJSONObject("session")
        assertFalse(confirmed.isNull("confirmed_instruction")); assertEquals(3, calls); assertFalse(confirmed.has("active_policy"))
    }
    @Test fun reviewFailureCannotLeaveReadyOrLoseAnswer() {
        var calls = 0
        val engine = SetupEngine(context) {
            calls++
            if (calls == 3) error("synthetic transport failure")
            response(if (calls == 1) question() else ready(JSONArray().put("a")))
        }
        val r = engine.process(answer(engine.process(create()).getJSONObject("session")))
        val s = r.getJSONObject("session")
        assertTrue(r.has("error")); assertTrue(s.isNull("result")); assertTrue(s.isNull("confirmed_instruction"))
        assertEquals(1, s.getJSONArray("answers").length()); assertEquals(3, s.getJSONArray("metrics").length())
    }
    @Test fun inventedCandidatesAndInvalidSchemaFailClosed() {
        val invented = question()
        invented.getJSONArray("questions").getJSONObject(0).getJSONArray("options").getJSONObject(0).put("candidate_id", "invented")
        for (invalid in listOf(invented, ready().put("status", "active"), ready().put("extra", true))) {
            val r = SetupEngine(context) { response(invalid) }.process(create())
            assertTrue(r.has("error")); assertTrue(r.getJSONObject("session").isNull("result"))
        }
    }
    @Test fun disappearedSelectedCandidateStopsBeforeAnotherCall() {
        var calls = 0
        val engine = SetupEngine(context) { calls++; response(if (calls == 1) question() else ready(JSONArray().put("a"))) }
        val s = engine.process(answer(engine.process(create()).getJSONObject("session"))).getJSONObject("session")
        val r = engine.process(JSONObject().put("action", "refresh").put("session", s).put("candidates", JSONArray()))
        assertTrue(r.has("error")); assertEquals(3, calls)
    }
    @Test fun cannotConfirmAnUnresolvedDraft() {
        val engine = SetupEngine(context) { response(question()) }
        val s = engine.process(create()).getJSONObject("session")
        val r = engine.process(JSONObject().put("action", "confirm").put("session", s))
        assertTrue(r.has("error")); assertTrue(r.getJSONObject("session").isNull("confirmed_instruction"))
    }
}
