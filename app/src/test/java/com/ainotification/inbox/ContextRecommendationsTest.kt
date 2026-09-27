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
class ContextRecommendationsTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var scope:CoroutineScope
    private fun row()=previewNotifications()[0].copy(packageName="test.shop",appLabel="테스트 쇼핑",conversationIdentity=null,messagesJson="[]",isGroupSummary=false)
    @Before fun before(){context.deleteDatabase("policies.db");context.filesDir.listFiles()?.forEach{it.delete()};scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)}
    @After fun after(){scope.cancel()}
    @Test fun recommendationLimitIdentityAndUnknownSafety() {
        for(event in recommendationEvents.keys+"UNKNOWN") {
            val options=recommendedActions(row(),event)
            assertTrue(options.size<=3);assertEquals(RecommendationAction.CUSTOM_RULE,options.last().action)
            assertFalse(options.any{it.action in listOf(RecommendationAction.WATCH_CONVERSATION,RecommendationAction.ALWAYS_SHOW_CONVERSATION,RecommendationAction.ALWAYS_SHOW_SENDER)})
        }
        assertFalse(recommendedActions(row(),"UNKNOWN").any{it.action==RecommendationAction.HIDE_SIMILAR})
        val room=row().copy(conversationIdentity="room-1")
        assertTrue(recommendedActions(room,"CASUAL_CHAT").any{it.action==RecommendationAction.ALWAYS_SHOW_CONVERSATION})
        assertFalse(recommendedActions(room.copy(isGroupSummary=true),"CASUAL_CHAT").any{it.action==RecommendationAction.ALWAYS_SHOW_CONVERSATION})
    }
    @Test fun onlySelectedMeansHideRemainderAndNeverAllApps() {
        val r=recommendationRule(row(),RuleRecommendation(RecommendationAction.SHOW_ONLY_SELECTED_TYPES,"선택"),setOf("DELIVERY","ORDER_UPDATES"),"new_1")
        assertEquals("HIDE",r.getString("action"));assertEquals("ANY",r.getJSONArray("conditions").getJSONObject(0).getString("type"))
        assertEquals(listOf("test.shop"),jsonStrings(r.getJSONObject("scope").getJSONArray("apps")))
        val e=r.getJSONArray("exceptions").getJSONObject(0);assertEquals("SHOW",e.getString("action"));assertEquals("ANY",e.getString("logic"));assertEquals(2,e.getJSONArray("conditions").length())
        assertThrows(Exception::class.java){recommendationRule(row(),RuleRecommendation(RecommendationAction.SHOW_ONLY_SELECTED_TYPES,"선택"),emptySet(),"new_1")}
        assertThrows(Exception::class.java){recommendationRule(row(),RuleRecommendation(RecommendationAction.WATCH_CONVERSATION,"선택"),emptySet(),"new_1")}
    }
    @Test fun senderIsObservedNotGuessedFromTitle() {
        val choice=RuleRecommendation(RecommendationAction.ALWAYS_SHOW_SENDER,"이 사람 보기")
        assertThrows(Exception::class.java){recommendationRule(row().copy(title="김철수"),choice,emptySet(),"new_1")}
        val r=recommendationRule(row().copy(messagesJson="""[{"text":"안녕","sender":"김철수"}]"""),choice,emptySet(),"new_1")
        assertEquals(listOf("김철수"),jsonStrings(r.getJSONObject("scope").getJSONArray("sender_names")))
    }
    @Test fun simpleTemplateNeverCallsOpenAiAndRequiresConfirmation() {
        var aiCalls=0
        val m=NotificationSelection(context,scope)
        val c=PolicyController(context,scope,m,SetupEngine(context){aiCalls++;error("must not call")},catalogProvider={JSONObject().put("apps",JSONArray().put("test.shop")).put("conversations",JSONArray()).put("senders",JSONArray())})
        c.recommend(row(),RuleRecommendation(RecommendationAction.HIDE_SIMILAR,"광고 숨김","PROMOTION"))
        assertNull(c.error.value);assertEquals(0,aiCalls);assertFalse(m.state.value.has("policy"))
        assertEquals("local_recommendation",m.state.value.getJSONObject("policy_proposal").getString("origin"))
        c.discard();assertFalse(m.state.value.has("policy"))
        c.recommend(row(),RuleRecommendation(RecommendationAction.HIDE_SIMILAR,"광고 숨김","PROMOTION"));c.apply()
        assertNull(c.error.value);assertEquals(0,aiCalls);assertEquals(1,c.currentRules().length())
    }
    @Test fun overlappingScopeUsesEditorAndPreservesBaselineOnFailure() {
        val m=NotificationSelection(context,scope)
        val old=recommendationRule(row(),RuleRecommendation(RecommendationAction.HIDE_APP,"모두 숨기기"),emptySet(),"new_1")
        m.installRules(JSONArray().put(old),true,"");val before=canonical(m.state.value.getJSONObject("policy"));var calls=0
        val c=PolicyController(context,scope,m,SetupEngine(context){calls++;error("network unavailable")},catalogProvider={JSONObject().put("apps",JSONArray().put("test.shop")).put("conversations",JSONArray()).put("senders",JSONArray())})
        c.recommend(row(),RuleRecommendation(RecommendationAction.SHOW_SIMILAR,"배송 보기","DELIVERY"))
        assertEquals(1,calls);assertEquals(before,canonical(m.state.value.getJSONObject("policy")));assertNotNull(c.error.value)
    }
    @Test fun semanticClassificationIsCachedAndUnknownDoesNotGuess() {
        var calls=0
        val engine=JevEngine(context){q->calls++;JSONObject().put("usage",JSONObject().put("input_tokens",1).put("output_tokens",1)).put("answers",JSONObject().apply {q.getJSONObject("questions").keys().forEach {put(it,JSONObject().put("type","noul").put("noul",if(it=="PROMOTION") .99 else .01))}})}
        val r=ContextRecommendationEngine(engine)
        assertEquals("PROMOTION",r.classify(row()));assertEquals("PROMOTION",r.classify(row()));assertEquals(1,calls)
        assertEquals("UNKNOWN",r.classify(row().copy(isGroupSummary=true)));assertEquals(1,calls)
    }
    @Test fun preferencesCountOnlyConfirmedTemplatesAndResetClearsProfiles() {
        val m=NotificationSelection(context,scope)
        val catalog=JSONObject().put("apps",JSONArray().put("test.shop").put("test.second")).put("conversations",JSONArray()).put("senders",JSONArray())
        val c=PolicyController(context,scope,m,SetupEngine(context){error("no OpenAI")},catalogProvider={catalog})
        val choice=RuleRecommendation(RecommendationAction.HIDE_SIMILAR,"광고 숨기기","PROMOTION")
        c.recommend(row(),choice);c.discard();assertFalse(m.state.value.has("context_preferences"))
        c.recommend(row(),choice);c.apply();assertNull(c.error.value)
        c.recommend(row().copy(packageName="test.second"),choice);c.apply();assertNull(c.error.value)
        val prefs=m.state.value.getJSONObject("context_preferences")
        assertEquals(2,prefs.getJSONObject("PROMOTION").getInt("HIDE"))
        val old=canonical(m.state.value)
        assertTrue(recommendedActions(row().copy(packageName="new.app"),"PROMOTION",prefs).first().label.startsWith("평소처럼"))
        assertEquals(old,canonical(m.state.value))
        val room=row().copy(conversationIdentity="room")
        c.setConversationProfile(room,"WORK")
        assertEquals("WORK",m.state.value.getJSONObject("conversation_profiles").getString(conversationProfileKey(room)))
        m.resetRules(m.state.value.getJSONObject("policy").getString("id"))
        assertFalse(m.state.value.has("context_preferences"));assertFalse(m.state.value.has("conversation_profiles"))
    }

}
