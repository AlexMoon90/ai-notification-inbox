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
    @Test fun phoneStatusExcludedButMissedCallsAndMessagesRemain() {
        val call=row().copy(packageName="com.samsung.android.incallui",channelId="Ongoing_call",notificationCategory="call")
        assertTrue(call.isExcludedCallStatus())
        assertTrue(call.copy(channelId="InCallRecordNotification",notificationCategory=null).isExcludedCallStatus())
        assertFalse(call.copy(packageName="com.samsung.android.dialer",channelId="missedCall",notificationCategory=null).isExcludedCallStatus())
        assertFalse(call.copy(channelId="vendor-channel",notificationCategory="missed_call").isExcludedCallStatus())
        assertFalse(row().copy(packageName="com.kakao.talk",channelId="messages",notificationCategory="msg").isExcludedCallStatus())
        assertFalse(call.copy(packageName="com.whatsapp").isExcludedCallStatus())
        assertFalse(call.copy(packageName="com.whatsapp",notificationCategory="missed_call").isExcludedCallStatus())
    }
    @Test fun unnamedGroupScopeNeverUsesSenderAsRoomTitle() {
        val room=row().copy(conversationIdentity="room-1",conversationTitle="",title="궁시렁 프로도",isGroupConversation=true,messagesJson="""[{"sender":"궁시렁 프로도","text":"hello"}]""")
        val whole=recommendationRule(room,RuleRecommendation(RecommendationAction.HIDE_CONVERSATION,"방 숨김"),emptySet(),"whole")
        assertEquals("단톡방 전체 · 모든 내용 → 숨김",policyResultText(whole,listOf(room)))
        val sender=recommendationRule(room,RuleRecommendation(RecommendationAction.HIDE_ROOM_SENDER,"발신자 숨김"),emptySet(),"sender")
        assertEquals("단톡방의 궁시렁 프로도 메시지 · 모든 내용 → 숨김",policyResultText(sender,listOf(room)))
        assertEquals("‘회사’ 대화방 전체",scopeLabel(whole,listOf(room.copy(conversationTitle="회사"))))
        assertEquals("선택한 대화방 전체",scopeLabel(whole,emptyList()))
    }
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
    @Test fun moreSpecificTemplatePreservesBaselineWithoutCallingEditor() {
        val m=NotificationSelection(context,scope)
        val old=recommendationRule(row(),RuleRecommendation(RecommendationAction.HIDE_APP,"모두 숨기기"),emptySet(),"new_1")
        m.installRules(JSONArray().put(old),true,"");val before=canonical(m.state.value.getJSONObject("policy"));var calls=0
        val c=PolicyController(context,scope,m,SetupEngine(context){calls++;error("network unavailable")},catalogProvider={JSONObject().put("apps",JSONArray().put("test.shop")).put("conversations",JSONArray()).put("senders",JSONArray())})
        c.recommend(row(),RuleRecommendation(RecommendationAction.SHOW_SIMILAR,"배송 보기","DELIVERY"))
        assertEquals(0,calls);assertEquals(before,canonical(m.state.value.getJSONObject("policy")));assertNull(c.error.value)
        assertTrue(m.state.value.getJSONObject("policy_proposal").getBoolean("validated"))
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

    @Test fun wholeRoomContextNeverMeansSender() {
        val room=row().copy(conversationIdentity="room-A",conversationTitle="회사방",isGroupConversation=true,messagesJson="""[{"sender":"민수","text":"안녕하세요","timestamp":1}]""")
        val target=recommendationTarget(room).put("whole_room_hide",true)
        assertTrue(target.getBoolean("is_group_conversation"));assertEquals("room-A",target.getString("conversation_id"))
        assertTrue(isWholeRoomHide("이 대화방은 숨겨줘."));assertTrue(isWholeRoomHide("Hide this group chat"))
        assertFalse(isWholeRoomHide("이 방에서 민수만 숨겨줘"));assertFalse(isWholeRoomHide("이 대화방은 숨기지 마"))
        val rule=recommendationRule(room,RuleRecommendation(RecommendationAction.ALWAYS_SHOW_CONVERSATION,"방"),emptySet(),"new_1").put("action","HIDE")
        validateContextScope(target,JSONArray(),JSONArray().put(rule))
        val runtime=StructuredPolicyRuntime(JevEngine(context){error("ANY scope must be local")})
        assertEquals("HIDE",runtime.classify(JSONArray().put(rule),room,false).getString("action"))
        assertEquals("HIDE",runtime.classify(JSONArray().put(rule),room.copy(messagesJson="""[{"sender":"지영","text":"네","timestamp":2}]"""),false).getString("action"))
        assertEquals("unconfigured",runtime.classify(JSONArray().put(rule),room.copy(conversationIdentity="room-B"),false).getString("status"))
        assertEquals("unconfigured",runtime.classify(JSONArray().put(rule),room.copy(packageName="other.app"),false).getString("status"))
        val wrong=JSONObject(rule.toString());wrong.getJSONObject("scope").put("type","SPECIFIC_SENDER").put("conversation_ids",JSONArray()).put("sender_names",JSONArray().put("민수"))
        assertThrows(IllegalArgumentException::class.java){validateContextScope(target,JSONArray(),JSONArray().put(wrong))}
        wrong.getJSONObject("scope").put("type","SPECIFIC_CONVERSATION").put("conversation_ids",JSONArray().put("room-A"))
        assertThrows(IllegalArgumentException::class.java){validateContextScope(target,JSONArray(),JSONArray().put(wrong))}
        wrong.getJSONObject("scope").put("sender_names",JSONArray()).put("conversation_ids",JSONArray().put("room-B"))
        assertThrows(IllegalArgumentException::class.java){validateContextScope(target,JSONArray(),JSONArray().put(wrong))}
    }
    @Test fun unresolvedRoomAsksWithoutGuessingOrNetwork() {
        val m=NotificationSelection(context,scope)
        val c=PolicyController(context,scope,m,SetupEngine(context){error("must not call network")},catalogProvider={JSONObject().put("apps",JSONArray()).put("conversations",JSONArray()).put("senders",JSONArray())})
        c.requestContext(recommendationTarget(row()),"이 대화방은 숨겨줘")
        assertNull(c.error.value)
        val p=m.state.value.getJSONObject("policy_proposal")
        assertEquals("clarification_required",p.getJSONObject("result").getString("status"))
        assertFalse(p.getBoolean("validated"));assertFalse(m.state.value.has("policy"))
    }

    @Test fun wrongSenderProposalCannotBecomeValidatedOrSaved() {
        val room=row().copy(conversationIdentity="room-A",conversationTitle="회사방",isGroupConversation=true,messagesJson="""[{"sender":"민수","text":"네","timestamp":1}]""")
        val wrong=recommendationRule(room,RuleRecommendation(RecommendationAction.ALWAYS_SHOW_SENDER,"사람"),emptySet(),"new_1").put("action","HIDE")
        val response=JSONObject().put("status","ready").put("message","").put("question","").put("options",JSONArray()).put("uncertain",false).put("rules",JSONArray().put(wrong)).put("operations",JSONArray().put(JSONObject().put("type","ADD").put("id","new_1")))
        var calls=0
        val ai=SetupEngine(context){calls++;JSONObject().put("status","completed").put("output",JSONArray().put(JSONObject().put("type","message").put("content",JSONArray().put(JSONObject().put("type","output_text").put("text",response.toString())))))}
        val m=NotificationSelection(context,scope)
        val c=PolicyController(context,scope,m,ai,catalogProvider={JSONObject().put("apps",JSONArray()).put("conversations",JSONArray()).put("senders",JSONArray().put("민수"))})
        c.requestContext(recommendationTarget(room),"이 대화방은 숨겨줘")
        assertEquals(2,calls) // initial editor and one bounded repair, no semantic audit can approve this
        assertNotNull(c.error.value);assertFalse(m.state.value.getJSONObject("policy_proposal").getBoolean("validated"));assertFalse(m.state.value.has("policy"))
        c.apply();assertFalse(m.state.value.has("policy"))
    }

    @Test fun senderInsideRoomRequiresAllThreeBindings() {
        val row=row().copy(conversationIdentity="room-A",messagesJson="""[{"sender":"민수","text":"네","timestamp":1}]""")
        val target=recommendationTarget(row).put("room_sender_hide",true)
        assertTrue(isRoomSenderHide("이 대화방에서 이 발신자만 숨겨줘",target))
        assertTrue(isRoomSenderHide("이 단톡방에서 민수의 메시지만 숨겨줘",target))
        assertTrue(isRoomSenderHide("Hide this sender only in this group chat.",target))
        assertFalse(isWholeRoomHide("이 방에서 이 발신자만 숨겨줘"))
        assertFalse(isRoomSenderHide("이 방에서 이 발신자만 숨기지 마",target))
        val rule=recommendationRule(row,RuleRecommendation(RecommendationAction.ALWAYS_SHOW_CONVERSATION,"방"),emptySet(),"new_1").put("action","HIDE")
        val scope=rule.getJSONObject("scope");scope.put("sender_names",JSONArray().put("민수"))
        validateContextScope(target,JSONArray(),JSONArray().put(rule))
        scope.put("apps",JSONArray())
        assertThrows(IllegalArgumentException::class.java){validateContextScope(target,JSONArray(),JSONArray().put(rule))}
        scope.put("apps",JSONArray().put(row.packageName)).put("sender_names",JSONArray())
        assertThrows(IllegalArgumentException::class.java){validateContextScope(target,JSONArray(),JSONArray().put(rule))}
        scope.put("sender_names",JSONArray().put("민수")).put("conversation_ids",JSONArray())
        assertThrows(IllegalArgumentException::class.java){validateContextScope(target,JSONArray(),JSONArray().put(rule))}
    }

    @Test fun hideWizardExceptionsStayInsideParentScope() {
        val room=row().copy(conversationIdentity="room-A",isGroupConversation=true,messagesJson="""[{"sender":"민수","text":"네","timestamp":1}]""")
        val rule=recommendationRule(room,RuleRecommendation(RecommendationAction.HIDE_ROOM_SENDER,"이 방의 민수 숨김"),setOf("REPLY_REQUIRED","SCHEDULE_CHANGE"),"new_1")
        val scope=rule.getJSONObject("scope")
        assertEquals(listOf("room-A"),jsonStrings(scope.getJSONArray("conversation_ids")))
        assertEquals(listOf("민수"),jsonStrings(scope.getJSONArray("sender_names")))
        assertEquals("HIDE",rule.getString("action"))
        val exception=rule.getJSONArray("exceptions").getJSONObject(0)
        assertEquals("SHOW",exception.getString("action"));assertEquals("ANY",exception.getString("logic"))
        assertEquals(setOf("REPLY_REQUIRED","SCHEDULE_CHANGE"),jsonObjects(exception.getJSONArray("conditions")).map{it.getString("type")}.toSet())
        val runtime=StructuredPolicyRuntime(JevEngine(context){error("must not call")})
        assertEquals("unconfigured",runtime.classify(JSONArray().put(rule),room.copy(conversationIdentity="room-B"),false).getString("status"))
        assertEquals("unconfigured",runtime.classify(JSONArray().put(rule),room.copy(messagesJson="""[{"sender":"지영","text":"네","timestamp":1}]"""),false).getString("status"))
        assertEquals("review",runtime.classify(JSONArray().put(rule),room,false).getString("status"))
        val noException=recommendationRule(room,RuleRecommendation(RecommendationAction.HIDE_CONVERSATION,"방 숨김"),emptySet(),"new_1")
        assertEquals(0,noException.getJSONArray("exceptions").length())
        assertEquals("HIDE",runtime.classify(JSONArray().put(noException),room,false).getString("action"))
        assertThrows(IllegalArgumentException::class.java){recommendationRule(room.copy(conversationIdentity=null),RuleRecommendation(RecommendationAction.HIDE_CONVERSATION,"방"),emptySet(),"new_1")}
    }

}
