package com.ainotification.inbox

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppExperienceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val app get() = context.applicationContext as InboxApplication

    private fun clickText(text: String) {
        repeat(12) {
            device.findObject(By.text(text))?.let { found -> found.click(); device.waitForIdle(); return }
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 3, 30)
        }
        fail("Required test control not found")
    }
    private fun clickTag(tag: String) {
        repeat(12) {
            device.findObject(By.res(tag))?.let { found -> found.click(); device.waitForIdle(); return }
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 3, 30)
        }
        fail("Required control not found: $tag")
    }
    private fun awaitSetup() {
        val until = SystemClock.elapsedRealtime() + 125000
        while (app.setupDrafts.busy.value != null && SystemClock.elapsedRealtime() < until) SystemClock.sleep(300)
        assertNull("Setup request timed out", app.setupDrafts.busy.value)
    }

    @Test fun syntheticDesignAndLongMessageFlow() {
        val intent = Intent().setClassName(context, "com.ainotification.inbox.DesignPreviewActivity")
        ActivityScenario.launch<android.app.Activity>(intent).use {
            assertTrue(device.wait(Until.hasObject(By.text("Timeline")), 15000))
            device.waitForIdle()
            assertTrue(device.takeScreenshot(File(context.filesDir, "design-timeline.png")))
            clickText("긴 내용 확인이 필요한 알림만")
            clickText("가족")
            assertTrue(device.wait(Until.hasObject(By.text("알림 상세")), 5000))
            assertTrue(device.hasObject(By.text("긴 내용 · 원본 확인 필요")))
            assertTrue(device.takeScreenshot(File(context.filesDir, "design-detail.png")))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("Timeline")), 5000))
        }
    }

    /** Runs without an Activity; direct phone HTTPS must work even without PC forwarding. */
    @Test fun standaloneDirectSetupAndLocalConfirmation() {
        val id = app.setupDrafts.create("모든 앱에서 일정 변경만 알려줘", org.json.JSONArray())
        try {
            awaitSetup()
            var draft = app.setupDrafts.drafts.value.single { it.getString("id") == id }
            assertEquals("ready", draft.result()?.optString("status"))
            assertEquals("모든 앱에서 일정 변경만 알려줘", draft.result()?.optString("normalized_instruction"))
            val calls = draft.getJSONObject("session").getJSONArray("metrics").length()
            assertEquals(1, calls)
            app.setupDrafts.request(id, "confirm")
            awaitSetup()
            draft = SetupDrafts(context, app.scope).drafts.value.single { it.getString("id") == id }
            assertEquals("검토 완료 · 미적용", draft.draftStatus())
            assertEquals(calls, draft.getJSONObject("session").getJSONArray("metrics").length())
            instrumentation.sendStatus(0, android.os.Bundle().apply { putInt("standalone_api_calls", calls) })
        } finally { awaitSetup(); app.setupDrafts.remove(id) }
    }

    @Test fun standaloneCandidateWaitingChoiceAndReview() {
        val id = app.setupDrafts.create("회사 단톡에서 일정 변경만 알려줘", org.json.JSONArray())
        fun draft() = app.setupDrafts.drafts.value.single { it.getString("id") == id }
        try {
            awaitSetup()
            assertEquals("waiting", draft().result()?.optString("status"))
            val candidates = org.json.JSONArray().put(org.json.JSONObject().put("id", "synthetic-room")
                .put("label", "합성 대화 A").put("preview", "합성 테스트 메시지"))
            app.setupDrafts.request(id, "refresh", candidates = candidates)
            awaitSetup()
            val result = draft().result()!!
            assertEquals("needs_input", result.getString("status"))
            val questions = result.getJSONArray("questions")
            assertEquals(1, questions.length())
            val question = questions.getJSONObject(0)
            assertEquals("conversation", question.getString("kind"))
            val choice = question.getJSONArray("options").getJSONObject(0)
            assertEquals("synthetic-room", choice.getString("candidate_id"))
            app.setupDrafts.request(id, "answer", org.json.JSONArray().put(org.json.JSONObject()
                .put("id", question.getString("id")).put("options", org.json.JSONArray().put(choice.getString("id")))))
            awaitSetup()
            assertEquals("ready", draft().result()?.optString("status"))
            assertEquals("synthetic-room", draft().result()!!.getJSONArray("referenced_candidate_ids").getString(0))
            val restored = SetupDrafts(context, app.scope).drafts.value.single { it.getString("id") == id }
            assertEquals("검토 필요", restored.draftStatus())
            val metrics = restored.getJSONObject("session").getJSONArray("metrics")
            assertEquals("semantic_review", metrics.getJSONObject(metrics.length() - 1).getString("purpose"))
            instrumentation.sendStatus(0, android.os.Bundle().apply { putInt("standalone_multiturn_calls", metrics.length()) })
        } finally { awaitSetup(); app.setupDrafts.remove(id) }
    }

    /** Real OpenAI request using synthetic instruction only, no saved notifications. */
    @Test fun liveClarificationFromFormPersistsAndNeverActivates() {
        val before = app.setupDrafts.drafts.value.map { it.getString("id") }.toSet()
        var createdId: String? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.res("nav_1")), 15000))
                clickTag("nav_1")
                clickTag("create_rule")
                assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5000))
                val input = device.findObject(By.clazz("android.widget.EditText"))
                input.click()
                device.waitForIdle()
                input.text = "모든 앱에서 중요한 것만 알려줘"
                device.waitForIdle()
                scenario.onActivity { activity ->
                    androidx.core.view.WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                        .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                }
                device.waitForIdle()
                assertTrue("Input must enable submit", device.findObject(By.res("submit_rule"))?.isEnabled == true)
                clickTag("submit_rule")
                assertTrue(device.wait(Until.hasObject(By.text("기준 확인")), 10000))
                createdId = app.setupDrafts.drafts.value.first { it.getString("id") !in before }.getString("id")
                awaitSetup()
                var draft = app.setupDrafts.drafts.value.first { it.getString("id") == createdId }
                assertEquals("needs_input", draft.result()?.optString("status"))
                assertEquals(0, draft.getJSONObject("session").getJSONArray("candidates").length())
                scenario.recreate()
                assertTrue(device.wait(Until.hasObject(By.text("기준 확인")), 10000))
                val questions = draft.result()!!.getJSONArray("questions")
                for (i in 0 until questions.length()) {
                    val q = questions.getJSONObject(i)
                    assertTrue("Expected a concrete meaning choice", q.getJSONArray("options").length() > 0)
                    val options = q.getJSONArray("options")
                    val choice = (0 until options.length()).firstOrNull {
                        options.getJSONObject(it).getString("label").contains("일정")
                    } ?: 0
                    clickTag("option_${i}_$choice")
                }
                clickTag("submit_answers")
                awaitSetup()
                draft = app.setupDrafts.drafts.value.first { it.getString("id") == createdId }
                instrumentation.sendStatus(0, android.os.Bundle().apply {
                    putInt("calls_after_answer", draft.getJSONObject("session").getJSONArray("metrics").length())
                    putString("synthetic_followup", draft.result()?.toString())
                })
                assertEquals("ready", draft.result()?.optString("status"))
                clickTag("confirm_rule")
                awaitSetup()
                val restored = SetupDrafts(context, app.scope).drafts.value.first { it.getString("id") == createdId }
                assertEquals("검토 완료 · 미적용", restored.draftStatus())
                assertFalse(restored.getJSONObject("session").has("active_policy"))
                val metrics = restored.getJSONObject("session").getJSONArray("metrics")
                var cost = 0.0
                for (i in 0 until metrics.length()) cost += metrics.getJSONObject(i).optDouble("estimated_usd", 0.0)
                instrumentation.sendStatus(0, android.os.Bundle().apply { putInt("setup_api_calls", metrics.length()); putDouble("setup_estimated_usd", cost) })
            }
        } finally {
            awaitSetup()
            createdId?.let { app.setupDrafts.remove(it) }
        }
    }
}
