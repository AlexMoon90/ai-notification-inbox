package com.ainotification.inbox

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Sends only authored fixtures. A selected synthetic room prevents real notifications reaching Jev. */
@RunWith(AndroidJUnit4::class)
class AutomaticSelectionTest {
    @Test fun liveListenerPolicyAndSelectionPersistWithoutPc() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as InboxApplication
        assumeTrue("Do not replace a user's active policy", app.selection.state.value.optJSONObject("policy") == null)
        repeat(100) { if (InboxNotificationListener.diagnosticInstance == null) delay(100) }
        val listener = InboxNotificationListener.diagnosticInstance
        assertNotNull("Notification listener must be connected", listener)
        val dao = app.database.notifications()
        val priorCount = dao.readAll().size
        val marker = "automatic-selection-${System.currentTimeMillis()}"
        val rows = mutableListOf<CapturedNotification>()
        fun fixture(index: Int, text: String): Pair<StatusBarNotification, CapturedNotification> {
            val n = Notification.Builder(context, "fixture").setContentTitle("Synthetic bill fixture")
                .setContentText(text).setShortcutId(marker).build()
            // Builder itself clips CharSequence on this OS; inject the intended captured fixture.
            n.extras.putCharSequence(Notification.EXTRA_TEXT, text)
            val sbn = StatusBarNotification("com.android.shell", "com.android.shell", 981100 + index, marker, 1000, 0, 0,
                n, android.os.Process.myUserHandle(), System.currentTimeMillis())
            val row = NotificationNormalizer(context).normalize(sbn)
            rows.add(row)
            return sbn to row
        }
        suspend fun awaitResult(id: String): JSONObject {
            repeat(600) {
                val result = app.selection.state.value.getJSONObject("results").optJSONObject(id)
                if (result != null && result.optString("status") != "pending") return result
                delay(100)
            }
            error("Selection did not finish")
        }
        val usageFile = File(context.filesDir, "jev-usage.jsonl")
        val beforeUsage = if (usageFile.exists()) usageFile.readLines().size else 0
        try {
            val (_, candidate) = fixture(0, "Synthetic room selection fixture")
            dao.insert(candidate)
            val instruction = "선택한 대화에서 아직 납부해야 할 청구서나 납부 요청만 알려줘. 광고와 납부 완료 알림은 제외해줘."
            val ids = JSONArray().put(candidate.snapshotId)
            val draft = JSONObject().put("id", marker).put("session", JSONObject()
                .put("confirmed_instruction", instruction).put("selected_candidate_ids", ids)
                .put("result", JSONObject().put("status", "ready").put("normalized_instruction", instruction).put("referenced_candidate_ids", ids)))
            app.selection.activate(draft)
            repeat(600) { if (app.selection.busy.value) delay(100) }
            assertNotNull("Jev must compile selected-room policy", app.selection.state.value.optJSONObject("policy"))
            val expected = listOf(
                "Your electricity bill of $82 is unpaid. Please pay by Friday." to "match",
                "여름 특가! 새 노트북 최대 30% 할인 중입니다." to "outside_or_review",
                "Your electricity bill payment was completed. No payment is due." to "outside_or_review",
                "긴 자료 ".repeat(900) to "review")
            val statuses = mutableListOf<String>()
            for ((index, pair) in expected.withIndex()) {
                val (sbn, row) = fixture(index + 1, pair.first)
                instrumentation.runOnMainSync { listener!!.onNotificationPosted(sbn) }
                val result = awaitResult(row.snapshotId)
                assertNotNull("Listener must store before selecting", dao.find(row.snapshotId))
                statuses.add(result.getString("status"))
                if (pair.second == "outside_or_review") {
                    assertTrue("Non-match must be outside or retained for review", result.getString("status") in listOf("outside", "review"))
                    assertTrue("Known synthetic non-match semantic judgment", result.getDouble("match_probability") <= .05)
                } else assertEquals("Synthetic case $index", pair.second, result.getString("status"))
                // Re-delivery must not repeat a paid judgment.
                instrumentation.runOnMainSync { listener!!.onNotificationPosted(sbn) }
            }
            delay(300)
            val persisted = JSONObject(File(context.filesDir, "notification-selection.json").readText())
            assertEquals(marker, persisted.getJSONObject("policy").getString("draft_id"))
            rows.drop(1).forEach { assertTrue(persisted.getJSONObject("results").has(it.snapshotId)) }
            val usage = usageFile.readLines().drop(beforeUsage).map { JSONObject(it) }
            assertEquals("One compile + three judgments; long text stays local", 4, usage.size)
            assertTrue(usage.all { it.getBoolean("ok") })
            instrumentation.sendStatus(0, Bundle().apply {
                putInt("live_api_calls", usage.size)
                putDouble("estimated_usd", usage.sumOf { it.getDouble("estimated_usd") })
                putString("synthetic_statuses", statuses.joinToString(","))
                putBoolean("existing_records_preserved", dao.readAll().size >= priorCount)
            })
        } finally {
            val testPolicy = app.selection.state.value.optJSONObject("policy")?.optString("id")
            val testResults = app.selection.state.value.getJSONObject("results")
            val testResultIds = testResults.keys().asSequence().filter {
                testResults.getJSONObject(it).optString("policy_id") == testPolicy
            }.toList()
            app.selection.pause()
            // Remove just the synthetic fixture results and database rows.
            rows.forEach { dao.delete(it.snapshotId) }
            app.selection.removeResults(testResultIds + rows.map { it.snapshotId })
        }
    }
    /** Only run with an explicit draftId after the user has already requested activation. */
    @Test fun activatePreviouslyReviewedDraftRequestedByUser() = runBlocking {
        val id = InstrumentationRegistry.getArguments().getString("draftId")
        assumeTrue("Explicit previously authorized draft is required", !id.isNullOrBlank())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as InboxApplication
        val draft = app.setupDrafts.drafts.value.single { it.getString("id") == id }
        val original = draft.toString()
        val current = app.selection.state.value.optJSONObject("policy")
        assumeTrue("Do not replace an unrelated active rule", current == null || current.optString("draft_id") == id)
        assertEquals("ready", draft.result()!!.getString("status"))
        val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        try {
            fun clickTag(tag: String) {
                val target = device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res(tag)), 15000)
                assertNotNull("UI control must be visible: $tag", target)
                target!!.click()
            }
            clickTag("nav_1")
            clickTag("draft_$id")
            clickTag("activate_rule")
            clickTag("confirm_activation")
            assertTrue("Active rule must show its stop button", device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.res("pause_rule")), 60000))
        } finally { scenario.close() }
        repeat(600) { if (app.selection.busy.value) delay(100) }
        val policy = app.selection.state.value.optJSONObject("policy")
        assertNotNull("Reviewed rule must activate", policy)
        assertNull("Activation must not leave an error", app.selection.error.value)
        assertEquals(id, policy!!.getString("draft_id"))
        assertEquals(draft.getJSONObject("session").getString("confirmed_instruction"), policy.getString("instruction"))
        assertEquals(original, app.setupDrafts.drafts.value.single { it.getString("id") == id }.toString())
        val disk = JSONObject(File(app.filesDir, "notification-selection.json").readText()).getJSONObject("policy")
        assertEquals(policy.getString("id"), disk.getString("id"))
        instrumentation.sendStatus(0, Bundle().apply {
            putBoolean("reviewed_draft_unchanged", true)
            putBoolean("requested_rule_active_and_persisted", true)
        })
    }

}
