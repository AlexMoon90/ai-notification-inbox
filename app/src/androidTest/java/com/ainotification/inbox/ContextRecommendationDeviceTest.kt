package com.ainotification.inbox

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ContextRecommendationDeviceTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val app get()=inst.targetContext.applicationContext as InboxApplication
    private suspend fun fixture(block:suspend (ContextWrapper,NotificationSelection,PolicyController,CapturedNotification)->Unit) {
        val before=app.selection.state.value.optJSONObject("policy")?.toString()
        val folder=File(app.filesDir,"recommendation-${UUID.randomUUID()}").apply{mkdirs()}
        val context=object:ContextWrapper(app){override fun getFilesDir()=folder;override fun getDatabasePath(name:String)=File(folder,name)}
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val row=previewNotifications()[0].copy(packageName="com.coupang.mobile",appLabel="테스트 쇼핑",title="오늘의 할인",text="(광고) 오늘만 의자 50% 할인! 지금 구매하세요.",bigText=null,subText=null,conversationTitle=null,conversationIdentity=null,messagesJson="[]",isGroupSummary=false)
        val manager=NotificationSelection(context,scope)
        val c=PolicyController(context,scope,manager,SetupEngine(context){error("Simple recommendation must not call OpenAI")},catalogProvider={JSONObject().put("apps",JSONArray().put(row.packageName)).put("conversations",JSONArray()).put("senders",JSONArray())})
        try {block(context,manager,c,row);assertEquals(before,app.selection.state.value.optJSONObject("policy")?.toString())}
        finally {scope.cancel();folder.deleteRecursively()}
    }
    private suspend fun idle(c:PolicyController) {withTimeout(10000){while(c.busy.value)delay(50)};assertNull(c.error.value)}
    @Test fun liveClassificationTemplatePreviewSaveAndSelection()=runBlocking {fixture {context,m,c,row->
        val engine=ContextRecommendationEngine(JevEngine(context))
        val event=engine.classify(row);assertEquals("PROMOTION",event)
        val choice=recommendedActions(row,event).first();assertEquals(RecommendationAction.HIDE_SIMILAR,choice.action)
        c.recommend(row,choice);idle(c);assertFalse(m.state.value.has("policy"))
        assertEquals("local_recommendation",m.state.value.getJSONObject("policy_proposal").getString("origin"))
        c.apply();idle(c)
        val result=StructuredPolicyRuntime(JevEngine(context)).classify(c.currentRules(),row)
        assertEquals("outside",result.getString("status"))
        val delivery=row.copy(title="배송 완료",text="주문하신 물품을 문 앞에 배송했습니다.")
        assertEquals("match",StructuredPolicyRuntime(JevEngine(context)).classify(c.currentRules(),delivery).getString("status"))
        assertFalse(File(context.filesDir,"rule-management-usage.jsonl").exists())
        val metrics=File(context.filesDir,"jev-usage.jsonl").readLines().map{JSONObject(it)}
        val report=JSONObject().put("promotion",result).put("metrics",JSONArray(metrics)).put("openai_calls",0)
        File(app.cacheDir,"context-recommendation-live.json").writeText(report.toString(2))
    }}
    @Test fun sheetPreviewCancelAndConfirmUi()=runBlocking {fixture {_,m,c,row->
        val device=UiDevice.getInstance(inst);device.wakeUp()
        val locked=(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
        if(locked)inst.sendStatus(0,Bundle().apply{putString("ui_validation","SKIPPED_LOCKED")})
        assumeFalse("Unlock device to validate UI",locked)
        fun find(tag:String):UiObject2 {repeat(15){device.wait(Until.hasObject(By.res(tag)),1000);device.findObject(By.res(tag))?.let{return it};device.findObject(By.scrollable(true))?.scroll(Direction.DOWN,.25f)};error("Missing $tag")}
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {a->a.setContent {InboxTheme {Surface {ContextRecommendationSheet(row,listOf(row),c,{"PROMOTION"},{})}}};a.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
            find("recommend_HIDE_SIMILAR")
            device.takeScreenshot(File(app.cacheDir,"context-recommendations.png"))
            find("recommend_HIDE_SIMILAR").click();find("confirm_policy")
            assertFalse(m.state.value.has("policy"))
            device.takeScreenshot(File(app.cacheDir,"context-recommendation-preview.png"))
            find("confirm_policy").click();delay(200);idle(c)
            assertEquals("HIDE",c.currentRules().getJSONObject(0).getString("action"))
            find("recommend_done")
            scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
        }
    }}
}
