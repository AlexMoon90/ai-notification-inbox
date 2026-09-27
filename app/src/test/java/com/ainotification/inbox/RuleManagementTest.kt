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
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class RuleManagementTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var scope:CoroutineScope
    @Before fun setup(){context.deleteDatabase("policies.db");context.filesDir.listFiles()?.forEach{it.delete()};scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)}
    @After fun clean(){scope.cancel()}
    private fun catalog()=JSONObject().put("apps",JSONArray().put("com.kakao.talk")).put("conversations",JSONArray().put(JSONObject().put("id","room1").put("package","com.kakao.talk"))).put("senders",JSONArray().put("Alex"))
    private fun rule(id:String="new_1",action:String="SHOW",type:String="PAYMENT_REQUIRED")=JSONObject().put("id",id).put("name","납부 요청").put("enabled",true).put("scope",PolicyContract.emptyScope()).put("conditions",JSONArray().put(condition(type))).put("logic","ANY").put("action",action).put("exceptions",JSONArray()).put("time",PolicyContract.emptyTime()).put("source_instruction","납부 요청 알려줘")
    private fun condition(type:String)=JSONObject().put("type",type).put("value","").put("negated",false)
    private fun response(rules:JSONArray,status:String="ready")=JSONObject().put("status",status).put("message","확인해 주세요").put("question",if(status=="ready")"" else "어느 대화인가요?").put("options",JSONArray()).put("uncertain",false).put("rules",rules).put("operations",JSONArray(jsonObjects(rules).map{JSONObject().put("type","ADD").put("id",it.getString("id"))}))
    private fun raw(value:JSONObject)=JSONObject().put("status","completed").put("output",JSONArray().put(JSONObject().put("type","message").put("content",JSONArray().put(JSONObject().put("type","output_text").put("text",value.toString())))))
    private fun setupEngine(handler:(JSONObject)->JSONObject)=SetupEngine(context){raw(handler(it))}
    private fun audit(valid:Boolean=true,clarify:Boolean=false)=JSONObject().put("valid",valid).put("issues",JSONArray()).put("needs_user_clarification",clarify).put("clarification_question",if(clarify)"이 대화만 숨길까요?" else "")
    private fun manager()=NotificationSelection(context,scope,JevEngine(context){error("Jev must not run in setup")})
    @Test fun appConnectionsIncludeGlobalAndSpecificRulesButExcludeOtherApps() {
        val global=rule("global")
        val kakao=rule("kakao",type="SECURITY").apply{getJSONObject("scope").put("apps",JSONArray().put("com.kakao.talk"))}
        val other=rule("other").apply{getJSONObject("scope").put("apps",JSONArray().put("com.other"))}
        val disabled=rule("off").put("enabled",false)
        val all=listOf(global,kakao,other,disabled);val before=all.map(::canonical)
        assertEquals(listOf("global","kakao","off"),relationRulesForApp(all,"com.kakao.talk").map{it.getString("id")})
        assertEquals(listOf("global","off"),relationRulesForApp(all,"*").map{it.getString("id")})
        assertEquals(before,all.map(::canonical))
    }
    @Test fun equivalentAppRulesBecomeOneCommonDisplayWithoutChangingPolicies() {
        val a=rule("a","HIDE","PROMOTION");a.getJSONObject("scope").put("type","SPECIFIC_APP").put("apps",JSONArray().put("com.kakao.talk"))
        val b=JSONObject(a.toString()).put("id","b").put("name","문자 광고 차단");b.getJSONObject("scope").put("apps",JSONArray().put("sms"))
        val original=canonical(JSONArray().put(a).put(b))
        val g=policyDisplayGroups(listOf(a,b)).single()
        assertTrue(g.common);assertEquals(setOf("com.kakao.talk","sms"),g.apps.toSet());assertEquals("광고 · 숨김",g.title)
        assertEquals(listOf("a","b"),g.rules.map{it.getString("id")})
        assertEquals(original,canonical(JSONArray().put(a).put(b)))
        assertTrue(displayGroupsForApp(listOf(g),"com.kakao.talk").single().common)
        assertTrue(displayGroupsForApp(listOf(g),"other").isEmpty())
    }
    @Test fun groupingDoesNotMixDifferentStatesQualifiersTimesOrSenders() {
        val base=rule("a","HIDE","PROMOTION")
        val off=JSONObject(base.toString()).put("id","off").put("enabled",false)
        val quiet=JSONObject(base.toString()).put("id","quiet").put("action","QUIET")
        val sender=JSONObject(base.toString()).put("id","sender");sender.getJSONObject("scope").put("type","SPECIFIC_SENDER").put("apps",JSONArray().put("com.kakao.talk")).put("sender_names",JSONArray().put("Alex"))
        val qualifier=JSONObject(base.toString()).put("id","topic");qualifier.getJSONArray("conditions").getJSONObject(0).put("value","쇼핑")
        val timed=JSONObject(base.toString()).put("id","time");timed.getJSONObject("time").put("start","09:00").put("end","18:00")
        val groups=policyDisplayGroups(listOf(base,off,quiet,sender,qualifier,timed))
        assertEquals(6,groups.size);assertFalse(groups.single{it.id=="sender"}.common)
    }
    @Test fun resetRemovesCriteriaButPreservesHistoryAndBudgetAndPersists() {
        val m=manager();m.installRules(JSONArray().put(rule()),true,"")
        m.updateState{it.put("rulebook",JSONObject()).put("legacy_policy",JSONObject()).put("policy_proposal",JSONObject()).put("policy_editor_input","입력").put("onboarding",JSONObject()).put("budget_calls",42)
            .getJSONObject("results").put("old",JSONObject().put("status","outside")).put("pending",JSONObject().put("status","pending"))}
        val rev=m.state.value.getJSONObject("policy").getString("id")
        assertThrows(IllegalArgumentException::class.java){m.resetRules("stale")}
        assertTrue(m.state.value.has("policy"))
        m.resetRules(rev)
        val restored=manager().state.value
        listOf("policy","rulebook","legacy_policy","policy_proposal","policy_editor_input","onboarding").forEach{assertFalse(restored.has(it))}
        assertEquals("outside",restored.getJSONObject("results").getJSONObject("old").getString("status"))
        assertEquals("review",restored.getJSONObject("results").getJSONObject("pending").getString("status"))
        assertEquals(42,restored.getInt("budget_calls"));assertTrue(restored.getBoolean("onboarding_complete"))
    }
    @Test fun relationProjectionPreservesScopeAndMultipleConditionsWithoutChangingRules() {
        val r=rule();r.getJSONObject("scope").put("apps",JSONArray().put("com.kakao.talk").put("com.whatsapp"))
        r.getJSONArray("conditions").put(condition("SECURITY"));val original=canonical(r)
        assertEquals(listOf("com.kakao.talk","com.whatsapp"),relationApps(r))
        assertEquals(listOf("PAYMENT_REQUIRED","SECURITY"),relationConditions(r));assertEquals(original,canonical(r))
        assertEquals(listOf("*"),relationApps(rule()))
    }
    @Test fun strictSchemaRejectsUnknownFieldsEnumsAndIncompleteRules(){
        val good=rule();PolicyContract.validate(JSONArray(),JSONArray().put(good),catalog())
        listOf(JSONObject(good.toString()).put("extra",true),JSONObject(good.toString()).put("action","SEND"),JSONObject(good.toString()).apply{remove("enabled")}).forEach{bad->assertThrows(Exception::class.java){PolicyContract.validate(JSONArray(),JSONArray().put(bad),catalog())}}
    }
    @Test fun missingRoomAndSenderCannotBecomeAllApps(){
        val r=rule();r.getJSONObject("scope").put("type","SPECIFIC_CONVERSATION").put("conversation_ids",JSONArray().put("invented"))
        assertThrows(Exception::class.java){PolicyContract.validate(JSONArray(),JSONArray().put(r),catalog())}
        r.getJSONObject("scope").put("type","ALL_APPS")
        assertThrows(Exception::class.java){PolicyContract.validate(JSONArray(),JSONArray().put(r),catalog())}
    }
    @Test fun unknownPolicyIdAndWatchCannotActivate(){
        assertThrows(Exception::class.java){PolicyContract.validate(JSONArray(),JSONArray().put(rule("invented")),catalog())}
        assertThrows(Exception::class.java){PolicyContract.validate(JSONArray(),JSONArray().put(rule(action="WATCH")),catalog())}
    }
    @Test fun operationListMustDescribeActualChanges(){
        val before=JSONArray().put(rule());val after=JSONArray().put(rule().put("enabled",false))
        assertThrows(Exception::class.java){PolicyContract.validate(before,after,catalog(),JSONArray())}
        PolicyContract.validate(before,after,catalog(),JSONArray().put(JSONObject().put("type","SET_ENABLED").put("id","new_1")))
    }
    @Test fun scopeExpansionDeletionAndNewHideAreRisky(){
        val a=rule();a.getJSONObject("scope").put("type","SPECIFIC_APP").put("apps",JSONArray().put("com.kakao.talk"))
        assertTrue(PolicyContract.validate(JSONArray().put(a),JSONArray().put(rule()),catalog()).contains("적용 범위 변경"))
        assertTrue(PolicyContract.validate(JSONArray().put(a),JSONArray(),catalog()).contains("삭제"))
        assertTrue(PolicyContract.validate(JSONArray(),JSONArray().put(rule(action="HIDE")),catalog()).contains("알림 표시 감소"))
    }
    @Test fun sameScopeContradictionIsNotOverriddenByModelApproval(){
        assertThrows(Exception::class.java){PolicyContract.validate(JSONArray(),JSONArray().put(rule()).put(rule("new_2","HIDE")),catalog())}
    }
    @Test fun unrelatedFieldsAreIncludedInDiffAndHashChanges(){
        val before=JSONArray().put(rule());val after=JSONArray().put(rule().put("name","변경"));assertEquals("UPDATE",PolicyContract.diff(before,after).single().first)
        assertNotEquals(policyHash(before),policyHash(after))
    }
    @Test fun exceptionsAreTypedAndNeverContainIndependentScope(){
        val r=rule(action="HIDE",type="PROMOTION");r.getJSONArray("exceptions").put(JSONObject().put("id","ex1").put("conditions",JSONArray().put(condition("CONTENT").put("value","기타 장비"))).put("logic","ANY").put("action","SHOW").put("scope",PolicyContract.emptyScope()))
        assertThrows(Exception::class.java){PolicyContract.validate(JSONArray(),JSONArray().put(r),catalog())}
    }
    @Test fun midnightUsesStartingDayAndEndIsExclusive(){
        val t=PolicyContract.emptyTime().put("zone","UTC").put("days",JSONArray().put("FRIDAY")).put("start","22:00").put("end","07:00")
        fun at(s:String)=PolicyContract.timeMatches(t,Instant.parse(s).toEpochMilli())
        assertTrue(at("2026-09-25T22:00:00Z"));assertTrue(at("2026-09-26T06:59:00Z"));assertFalse(at("2026-09-26T07:00:00Z"));assertFalse(at("2026-09-26T23:00:00Z"))
    }
    @Test fun temporaryEndFallsBackWithoutRestoringOldPolicies(){
        val t=PolicyContract.emptyTime().put("from","2026-09-24T00:00:00Z").put("until","2026-09-25T00:00:00Z")
        assertTrue(PolicyContract.timeMatches(t,Instant.parse("2026-09-24T01:00:00Z").toEpochMilli()));assertFalse(PolicyContract.timeMatches(t,Instant.parse("2026-09-25T00:00:00Z").toEpochMilli()))
        assertThrows(Exception::class.java){PolicyContract.checkTime(t.put("until","2026-09-23T00:00:00Z"))}
    }
    @Test fun newShowSkipsSemanticCallButWaitsForUserConfirmation(){
        var calls=0;val m=manager();val c=PolicyController(context,scope,m,setupEngine{calls++;response(JSONArray().put(rule()))}){catalog()}
        c.request("납부 요청 보여줘")
        assertNull(c.error.value);assertEquals(1,calls);assertFalse(m.state.value.has("policy"))
        c.apply();assertNull(c.error.value);assertEquals(1,m.state.value.getJSONObject("policy").getJSONArray("rules").length())
        assertEquals(1,manager().state.value.getJSONObject("policy").getJSONArray("rules").length())
    }
    @Test fun hideRunsSeparateSemanticValidationAndDoesNotSaveBlindly(){
        var calls=0;val m=manager();val c=PolicyController(context,scope,m,setupEngine{calls++;if(calls==1)response(JSONArray().put(rule(action="HIDE"))) else audit()}){catalog()}
        c.request("납부 요청 숨겨줘");assertNull(c.error.value);assertEquals(2,calls);assertFalse(m.state.value.has("policy"));assertTrue(m.state.value.getJSONObject("policy_proposal").getBoolean("validated"))
    }
    @Test fun semanticAmbiguityAsksQuestionAndCannotApply(){
        var calls=0;val m=manager();val c=PolicyController(context,scope,m,setupEngine{calls++;if(calls==1)response(JSONArray().put(rule(action="HIDE"))) else audit(false,true)}){catalog()}
        c.request("이건 숨겨줘");assertNull(c.error.value);assertEquals("clarification_required",m.state.value.getJSONObject("policy_proposal").getJSONObject("result").getString("status"));c.apply();assertFalse(m.state.value.has("policy"))
    }
    @Test fun malformedAuditAndApiFailureKeepOriginalState(){
        var calls=0;val m=manager();m.installRules(JSONArray().put(rule()),false,"");val previous=m.state.value.getJSONObject("policy").toString()
        val c=PolicyController(context,scope,m,setupEngine{calls++;error("secret provider response")}){catalog()}
        c.request("変更");assertEquals(previous,m.state.value.getJSONObject("policy").toString());assertNotNull(c.error.value);assertFalse(c.error.value!!.contains("secret"))
    }
    @Test fun staleAndModifiedProposalCannotApply(){
        val m=manager();val c=PolicyController(context,scope,m,setupEngine{response(JSONArray().put(rule()))}){catalog()};c.request("납부 요청 보여줘")
        m.updateState{it.getJSONObject("policy_proposal").getJSONObject("result").getJSONArray("rules").getJSONObject(0).put("action","HIDE")}
        c.apply();assertFalse(m.state.value.has("policy"));assertNotNull(c.error.value)
        c.request("납부 요청 보여줘");m.updateState{it.getJSONObject("policy_proposal").put("base_revision","obsolete")};c.apply();assertFalse(m.state.value.has("policy"))
    }
    @Test fun deterministicFailureDoesNotCallSemanticValidator(){
        var calls=0;val m=manager();val c=PolicyController(context,scope,m,setupEngine{calls++;response(JSONArray().put(rule().put("action","INVALID")))}){catalog()}
        c.request("납부 요청 보여줘");assertEquals(1,calls);assertFalse(m.state.value.has("policy"));assertNotNull(c.error.value)
    }
    @Test fun unrelatedRulesSurviveDirectToggleAndDeleteNeedsPreview(){
        val m=manager();m.installRules(JSONArray().put(rule()).put(rule("new_2",type="SECURITY")),false,"")
        var calls=0;val c=PolicyController(context,scope,m,setupEngine{calls++;audit()}){catalog()}
        c.direct("new_1","DELETE");assertNull(c.error.value);assertEquals(1,calls);assertEquals(2,c.currentRules().length());c.apply();assertEquals("new_2",c.currentRules().getJSONObject(0).getString("id"))
    }
    @Test fun legacyFileIsImportedIntoRoomWithoutChangingMeaning(){
        val legacy=JSONObject().put("results",JSONObject()).put("policy",JSONObject().put("id","legacy").put("instruction","기존 조건 그대로").put("draft_id","d").put("activated_at",1).put("conversation_bindings",JSONArray()))
        java.io.File(context.filesDir,"notification-selection.json").writeText(legacy.toString())
        val m=manager();assertEquals("기존 조건 그대로",m.state.value.getJSONObject("policy").getString("instruction"));assertNotNull(PolicyStateStore(context).read())
    }
    @Test fun ordinaryEditOfLegacyPolicyIncludesPreviousMeaningAndRequiresAudit(){
        val m=manager();m.updateState{it.put("policy",JSONObject().put("id","legacy").put("instruction","납부 요청 알려줘"))}
        val previous=m.state.value.getJSONObject("policy").toString();var calls=0
        val c=PolicyController(context,scope,m,setupEngine{request->
            calls++;assertTrue(request.toString().contains("legacy_instruction"));assertTrue(request.toString().contains("납부 요청 알려줘"))
            if(calls==1)response(JSONArray().put(rule())) else audit()
        }){catalog()}
        c.request("기존 기준 유지하고 정리해줘")
        assertNull(c.error.value);assertEquals(2,calls);assertEquals(previous,m.state.value.getJSONObject("policy").toString())
        assertTrue(m.state.value.getJSONObject("policy_proposal").getBoolean("migration"))
    }
    @Test fun editorSchemaRestrictsBothIdsAndAvoidsExistingNewIdCollisions(){
        val before=JSONArray().put(rule("new_1")).put(rule("stored-uuid"))
        val schema=PolicyContract.editorFor(before).getJSONObject("properties")
        val ruleIds=jsonStrings(schema.getJSONObject("rules").getJSONObject("items").getJSONObject("properties").getJSONObject("id").getJSONArray("enum"))
        val operationIds=jsonStrings(schema.getJSONObject("operations").getJSONObject("items").getJSONObject("properties").getJSONObject("id").getJSONArray("enum"))
        assertEquals(ruleIds,operationIds);assertEquals(32,ruleIds.size);assertEquals(ruleIds.size,ruleIds.distinct().size)
        assertTrue("stored-uuid" in ruleIds);assertFalse("new_1" in PolicyContract.newRuleIds(before))
        assertThrows(Exception::class.java){PolicyContract.check(response(JSONArray().put(rule("invented-uuid"))),PolicyContract.editorFor(before))}
        val valid=response(JSONArray().put(rule("new_2")))
        PolicyContract.check(valid,PolicyContract.editorFor(before))
        valid.getJSONArray("operations").getJSONObject(0).put("id","invented-uuid")
        assertThrows(Exception::class.java){PolicyContract.check(valid,PolicyContract.editorFor(before))}
    }
    @Test fun controllerSendsAllocatedIdsAndKeepsStoredIdWhenEditing(){
        val m=manager();m.installRules(JSONArray().put(rule()),false,"")
        var calls=0
        val c=PolicyController(context,scope,m,setupEngine{request->
            calls++
            if(calls==1){
                val schema=request.getJSONObject("text").getJSONObject("format").getJSONObject("schema")
                val ids=jsonStrings(schema.getJSONObject("properties").getJSONObject("rules").getJSONObject("items").getJSONObject("properties").getJSONObject("id").getJSONArray("enum"))
                assertTrue("new_1" in ids);assertTrue("new_2" in ids)
                response(JSONArray().put(rule().put("name","납부 알림"))).put("operations",JSONArray().put(JSONObject().put("type","UPDATE").put("id","new_1")))
            }else audit()
        }){catalog()}
        c.request("납부 요청 기준의 이름만 납부 알림으로 바꿔줘")
        assertNull(c.error.value);c.apply();assertNull(c.error.value)
        assertEquals("new_1",c.currentRules().getJSONObject(0).getString("id"));assertEquals("납부 알림",c.currentRules().getJSONObject(0).getString("name"))
    }
    private fun rejectedAudit()=audit(false).put("issues",JSONArray().put(JSONObject().put("code","UNREQUESTED_DELETION").put("policy_id","new_1").put("path","exceptions").put("message","기존 예외가 빠졌습니다.")))
    @Test fun semanticRepairIsBoundedAndRevalidatedBeforeConfirmation(){
        var calls=0;val m=manager()
        val c=PolicyController(context,scope,m,setupEngine{calls++;when(calls){1,3->response(JSONArray().put(rule(action="HIDE")));2->rejectedAudit();else->audit()}}){catalog()}
        c.request("납부 요청 숨겨줘");assertNull(c.error.value);assertEquals(4,calls)
        assertTrue(m.state.value.getJSONObject("policy_proposal").getBoolean("validated"));assertFalse(m.state.value.has("policy"))
        assertEquals(1,m.state.value.getJSONObject("policy_proposal").getInt("repair_count"))
    }
    @Test fun repeatedSemanticFailureStaysPendingAndRetryPreservesAnswers(){
        var calls=0;val m=manager()
        val c=PolicyController(context,scope,m,setupEngine{calls++;if(calls%2==1)response(JSONArray().put(rule(action="HIDE"))) else rejectedAudit()}){catalog()}
        c.request("납부 요청 숨겨줘");assertNull(c.error.value);assertEquals(4,calls)
        val p=m.state.value.getJSONObject("policy_proposal");assertFalse(p.getBoolean("validated"));assertEquals("unsupported",p.getJSONObject("result").getString("status"))
        val history=canonical(p.getJSONArray("history"));c.apply();assertFalse(m.state.value.has("policy"))
        c.retryProposal();assertEquals(8,calls);assertEquals(history,canonical(m.state.value.getJSONObject("policy_proposal").getJSONArray("history")))
        assertFalse(m.state.value.has("policy"))
    }

    @Test fun anyCannotSilentlyDiscardUserQualifier() {
        val r=rule(type="ANY");r.getJSONArray("conditions").getJSONObject(0).put("value","only tax payments")
        assertThrows(IllegalArgumentException::class.java) { PolicyContract.validate(JSONArray(),JSONArray().put(r),catalog(),response(JSONArray().put(r)).getJSONArray("operations")) }
    }

    @Test fun deterministicRepairIsBoundedAndMustPassSemanticAudit() {
        val invalid=rule(action="HIDE").apply {getJSONObject("time").put("zone","")}
        var calls=0;val m=manager()
        val c=PolicyController(context,scope,m,setupEngine { calls++;when(calls) {
            1->response(JSONArray().put(invalid));2->response(JSONArray().put(rule(action="HIDE")));else->audit()
        } }) {catalog()}
        c.request("납부 요청 숨겨줘")
        assertEquals(3,calls);assertNull(c.error.value);assertFalse(m.state.value.has("policy"))
        assertTrue(m.state.value.getJSONObject("policy_proposal").getBoolean("validated"))
        assertEquals(1,m.state.value.getJSONObject("policy_proposal").getInt("repair_count"))
        c.apply();assertNull(c.error.value);assertEquals(1,c.currentRules().length())
    }
    @Test fun repeatedDeterministicFailurePreservesSavedPolicyAndHasCorrectStage() {
        val m=manager();m.installRules(JSONArray().put(rule()),true,"")
        val before=canonical(m.state.value.getJSONObject("policy"));var calls=0
        val invalid=rule().apply {getJSONObject("time").put("zone","")}
        val c=PolicyController(context,scope,m,setupEngine {calls++;response(JSONArray().put(invalid)).put("operations",JSONArray().put(JSONObject().put("type","UPDATE").put("id","new_1")))}) {catalog()}
        c.request("납부 알림 기준 수정")
        assertEquals(2,calls);assertNotNull(c.error.value);assertFalse(c.error.value!!.contains("연결"))
        assertEquals("policy_contract",m.state.value.getJSONObject("policy_proposal").getString("failure_stage"))
        c.apply();assertEquals(before,canonical(m.state.value.getJSONObject("policy")))
    }

}
