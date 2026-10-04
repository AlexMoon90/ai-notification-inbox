package com.ainotification.inbox

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class InitialPreferencesTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var scope:CoroutineScope
    private val catalog get()=JSONObject().put("apps",JSONArray().put("test.shop")).put("conversations",JSONArray()).put("senders",JSONArray())
    private fun row()=previewNotifications()[0].copy(packageName="test.shop",conversationIdentity=null,messagesJson="[]",isGroupSummary=false)
    @Before fun before(){context.deleteDatabase("policies.db");context.filesDir.listFiles()?.forEach{it.delete()};scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)}
    @After fun after(){scope.cancel()}
    @Test fun fixedSelectionsValidateAndOnlySaveAfterConfirmation(){
        val manager=NotificationSelection(context,scope)
        val controller=PolicyController(context,scope,manager,SetupEngine(context){error("fixed choices must not call OpenAI")},catalogProvider={catalog})
        controller.prepareInitialPreferences(setOf("money","security"),setOf("ads","discount"))
        assertNull(controller.error.value);assertFalse(manager.state.value.has("policy"))
        assertTrue(manager.state.value.getJSONObject("policy_proposal").getBoolean("validated"))
        controller.apply();assertNull(controller.error.value);assertEquals(3,controller.currentRules().length())
    }
    @Test fun replacingInitialPreferencesPreservesConcreteRulesAndAllowsEmptySelection(){
        val concrete=recommendationRule(row(),RuleRecommendation(RecommendationAction.HIDE_APP,"앱 숨김"),emptySet(),"existing")
        val before=initialPreferenceRules(setOf("money"),setOf("ads"),JSONArray().put(concrete))
        val after=initialPreferenceRules(emptySet(),emptySet(),before)
        assertEquals(1,after.length());assertEquals(canonical(concrete),canonical(after.getJSONObject(0)))
        assertThrows(IllegalArgumentException::class.java){initialPreferenceRules(setOf("invented"),emptySet(),before)}
    }
    @Test fun keepExceptionsProtectInitialQuietAndSpecificRulesOverrideInitial(){
        val initial=initialPreferenceRules(setOf("security"),setOf("ads"),JSONArray())
        val engine=JevEngine(context){q->JSONObject().put("usage",JSONObject().put("input_tokens",1).put("output_tokens",1)).put("answers",JSONObject().apply{q.getJSONObject("questions").keys().forEach{put(it,JSONObject().put("type","choice").put("choice","MATCH").put("confidence",.99).put("probabilities",JSONObject().put("MATCH",.99).put("NO_MATCH",.005).put("UNKNOWN",.005)))}})}
        val runtime=StructuredPolicyRuntime(engine)
        assertEquals("SHOW",runtime.classify(initial,row()).getString("action"))
        initial.put(recommendationRule(row(),RuleRecommendation(RecommendationAction.HIDE_APP,"앱 숨김"),emptySet(),"specific"))
        assertEquals("HIDE",runtime.classify(initial,row()).getString("action"))
    }
    @Test fun semanticRuleBeatsAppWideButEqualPriorityConflictNeedsReview(){
        val hide=recommendationRule(row(),RuleRecommendation(RecommendationAction.HIDE_APP,"앱 숨김"),emptySet(),"hide")
        val show=recommendationRule(row(),RuleRecommendation(RecommendationAction.SHOW_SIMILAR,"배송 보기","DELIVERY"),emptySet(),"show")
        assertTrue(policySpecificity(show)>policySpecificity(hide))
        val engine=JevEngine(context){q->JSONObject().put("usage",JSONObject().put("input_tokens",1).put("output_tokens",1)).put("answers",JSONObject().apply{q.getJSONObject("questions").keys().forEach{put(it,JSONObject().put("type","choice").put("choice","MATCH").put("confidence",.99).put("probabilities",JSONObject().put("MATCH",.99).put("NO_MATCH",.005).put("UNKNOWN",.005)))}})}
        val runtime=StructuredPolicyRuntime(engine)
        assertEquals("SHOW",runtime.classify(JSONArray().put(hide).put(show),row()).getString("action"))
        val conflict=JSONObject(show.toString()).put("id","conflict").put("action","HIDE")
        assertEquals("review",runtime.classify(JSONArray().put(show).put(conflict),row()).getString("status"))
    }
    @Test fun roomTopicDoesNotLeakToOtherRoom(){
        val room=row().copy(conversationIdentity="room-A")
        val rule=recommendationRule(room,RuleRecommendation(RecommendationAction.SHOW_ROOM_TOPIC,"배송 보기","DELIVERY"),emptySet(),"room")
        assertEquals(listOf("room-A"),jsonStrings(rule.getJSONObject("scope").getJSONArray("conversation_ids")))
        val runtime=StructuredPolicyRuntime(JevEngine(context){error("out of scope must not call")})
        assertEquals("unconfigured",runtime.classify(JSONArray().put(rule),room.copy(conversationIdentity="room-B")).getString("status"))
    }
    @Test fun ambiguousButtonOverlapDoesNotInvokeEditorOrSave(){
        val manager=NotificationSelection(context,scope)
        val old=recommendationRule(row(),RuleRecommendation(RecommendationAction.HIDE_SIMILAR,"배송 숨김","DELIVERY"),emptySet(),"old")
        manager.installRules(JSONArray().put(old),false,"")
        val before=canonical(manager.state.value.getJSONObject("policy"))
        val controller=PolicyController(context,scope,manager,SetupEngine(context){error("buttons must not call OpenAI")},catalogProvider={catalog})
        controller.recommend(row(),RuleRecommendation(RecommendationAction.SHOW_SIMILAR,"배송 보기","DELIVERY"))
        assertNotNull(controller.error.value);assertEquals(before,canonical(manager.state.value.getJSONObject("policy")))
        assertFalse(manager.state.value.optJSONObject("policy_proposal")?.optBoolean("validated")==true)
    }
}
