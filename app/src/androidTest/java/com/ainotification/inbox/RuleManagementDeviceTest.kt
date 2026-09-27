package com.ainotification.inbox

import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RuleManagementDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private fun click(tag: String) {
        repeat(6) {
            val node = device.findObject(By.res(tag))
            if (node != null) { node.click(); return }
            device.swipe(device.displayWidth/2, device.displayHeight*3/4, device.displayWidth/2, device.displayHeight/3, 20)
            Thread.sleep(200)
        }
        error("UI control missing: $tag")
    }
    private suspend fun awaitIdle(manager: NotificationSelection) {
        repeat(1200) { if (!manager.busy.value) { assertNull(manager.error.value); return }; delay(100) }
        error("Operation timed out")
    }
    @Test fun isolatedSyntheticItemsUiAndLiveAiRevision() = runBlocking {
        val app = instrumentation.targetContext.applicationContext as InboxApplication
        val folder = File(app.filesDir, "rule-test-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(app) { override fun getFilesDir() = folder }
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val source = "카카오톡 광고성 알림은 제외한다. 문자 광고성 알림은 제외한다. 모든 앱의 일정 변경 공지는 알려준다."
        try {
            val items = RuleAssistant(isolated).organize(source)
            assertEquals("Independent rules should be separate", 3, items.length())
            val book = JSONObject().put("revision", "fixture-v1").put("draft_id", "fixture").put("organized", true).put("bindings", JSONArray()).put("items", items)
            val policy = JevEngine(isolated).compile(source, JSONArray()).put("id", "fixture-p1").put("draft_id", "fixture").put("activated_at", System.currentTimeMillis())
            File(folder, "notification-selection.json").writeText(JSONObject().put("rulebook", book).put("policy", policy).put("results", JSONObject()).toString())
            val manager = NotificationSelection(isolated, scope)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent { InboxTheme {
                    val state by manager.state.collectAsState()
                    val busy by manager.busy.collectAsState()
                    Column(Modifier.semantics { testTagsAsResourceId = true }.verticalScroll(rememberScrollState())) {
                        Text("합성 기준 관리 테스트")
                        RuleBookPanel(state.getJSONObject("rulebook"), busy, manager::toggleItem, manager::deleteItem, {})
                    }
                } } }
                assertTrue(device.wait(Until.hasObject(By.res("rule_toggle_0")), 15000))
                click("rule_toggle_0"); delay(300); awaitIdle(manager)
                assertFalse(manager.state.value.getJSONObject("rulebook").getJSONArray("items").getJSONObject(0).getBoolean("enabled"))
                assertFalse(manager.state.value.getJSONObject("policy").getString("instruction").contains("카카오"))
                click("rule_toggle_0"); delay(300); awaitIdle(manager)
                assertTrue(manager.state.value.getJSONObject("rulebook").getJSONArray("items").getJSONObject(0).getBoolean("enabled"))
                manager.requestRuleEdit("문자 광고성 알림 제외 항목만 꺼 두고, 나머지 항목의 내용과 적용 상태는 그대로 유지해줘.")
                awaitIdle(manager)
                val proposal = manager.state.value.getJSONObject("rule_proposal").getJSONObject("result")
                assertEquals("ready", proposal.getString("status"))
                assertTrue(manager.state.value.getJSONObject("rulebook").getJSONArray("items").getJSONObject(1).getBoolean("enabled"))
                manager.applyRuleProposal(); awaitIdle(manager)
                assertFalse(manager.state.value.getJSONObject("rulebook").getJSONArray("items").getJSONObject(1).getBoolean("enabled"))
                assertFalse(manager.state.value.getJSONObject("policy").getString("instruction").contains("문자"))
                click("rule_delete_1")
                assertTrue(device.wait(Until.hasObject(By.res("confirm_rule_delete")), 5000))
                click("confirm_rule_delete"); delay(300); awaitIdle(manager)
                assertEquals(2, manager.state.value.getJSONObject("rulebook").getJSONArray("items").length())
            }
            val reopened = NotificationSelection(isolated, scope)
            assertEquals(2, reopened.state.value.getJSONObject("rulebook").getJSONArray("items").length())
            assertFalse(reopened.state.value.getJSONObject("policy").getString("instruction").contains("문자"))
            val metrics = listOf("rule-management-usage.jsonl", "jev-usage.jsonl").flatMap { File(folder, it).readLines() }.map { JSONObject(it) }
            instrumentation.sendStatus(0, Bundle().apply {
                putInt("synthetic_remote_calls", metrics.size)
                putDouble("estimated_usd", metrics.sumOf { it.optDouble("estimated_usd", 0.0) })
                putBoolean("checkbox_delete_ai_apply_restart", true)
            })
        } finally { scope.cancel(); folder.deleteRecursively() }
    }
    @Test fun currentUserRuleIsItemizedWithoutChangingAppliedMeaning() = runBlocking {
        val app = instrumentation.targetContext.applicationContext as InboxApplication
        val previous = app.selection.state.value.getJSONObject("policy").toString()
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(device.wait(Until.hasObject(By.res("nav_1")), 15000))
            click("nav_1")
            repeat(1200) { if (app.selection.state.value.optJSONObject("rulebook")?.optBoolean("organized") != true) delay(100) }
            assertTrue(device.wait(Until.hasObject(By.res("rule_toggle_0")), 5000))
            assertEquals(previous, app.selection.state.value.getJSONObject("policy").toString())
            val items = app.selection.state.value.getJSONObject("rulebook").getJSONArray("items")
            assertTrue(ruleObjects(items).all { item -> item.getBoolean("enabled") })
            val source = JSONObject(previous).getString("instruction")
            sourceRuleItems(source, JSONArray(ruleObjects(items).map { item -> item.getString("text") }))
            instrumentation.sendStatus(0, Bundle().apply { putInt("current_rule_items", items.length()); putBoolean("existing_policy_unchanged", true) })
        }
    }
}
