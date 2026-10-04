package com.ainotification.inbox

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class StructuredRuntimeTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private fun rule(action:String="HIDE")=JSONObject().put("id","r").put("name","광고 숨김").put("enabled",true).put("scope",PolicyContract.emptyScope()).put("conditions",JSONArray().put(JSONObject().put("type","PROMOTION").put("value","").put("negated",false))).put("logic","ANY").put("action",action).put("exceptions",JSONArray()).put("time",PolicyContract.emptyTime()).put("source_instruction","광고 숨김")
    private fun choice(p:Double,unknown:Boolean=false):JSONObject {
        val probabilities=if(unknown)JSONObject().put("MATCH",.01).put("NO_MATCH",.01).put("UNKNOWN",.98)
            else JSONObject().put("MATCH",p).put("NO_MATCH",1-p).put("UNKNOWN",0.0)
        val label=if(unknown)"UNKNOWN" else if(p>=.5)"MATCH" else "NO_MATCH"
        return JSONObject().put("type","choice").put("choice",label).put("confidence",.9).put("probabilities",probabilities)
    }
    private fun engine(values:Map<String,Double>)=JevEngine(context){request->JSONObject().put("usage",JSONObject().put("input_tokens",100).put("output_tokens",10)).put("answers",JSONObject().apply{request.getJSONObject("questions").keys().forEach{key->put(key,choice(values[key]?:.99,(values[key+"_unknown"]?:0.0)>.5))}})}
    @Test fun noApplicablePolicyStoresWithoutNowDecisionOrAi(){
        val r=rule();r.getJSONObject("scope").put("apps",JSONArray().put("different.app"))
        val runtime=StructuredPolicyRuntime(JevEngine(context){error("must not call")})
        assertEquals("unconfigured",runtime.classify(JSONArray().put(r),previewNotifications()[0]).getString("status"))
    }
    @Test fun explicitHideOnlyWhenConditionMatchIsCertain(){
        assertEquals("outside",StructuredPolicyRuntime(engine(emptyMap())).classify(JSONArray().put(rule()),previewNotifications()[0]).getString("status"))
        assertEquals("review",StructuredPolicyRuntime(engine(mapOf("r0c0_unknown" to 1.0))).classify(JSONArray().put(rule()),previewNotifications()[0]).getString("status"))
        assertEquals("match",StructuredPolicyRuntime(engine(mapOf("r0c0" to .01))).classify(JSONArray().put(rule()),previewNotifications()[0]).getString("status"))
    }
    @Test fun parentExceptionOverridesHideAndAmbiguousExceptionCannotHide(){
        val r=rule();r.getJSONArray("exceptions").put(JSONObject().put("id","e").put("conditions",JSONArray().put(JSONObject().put("type","CONTENT").put("value","기타 장비").put("negated",false))).put("logic","ANY").put("action","SHOW"))
        assertEquals("match",StructuredPolicyRuntime(engine(emptyMap())).classify(JSONArray().put(r),previewNotifications()[0]).getString("status"))
        assertEquals("review",StructuredPolicyRuntime(engine(mapOf("r0e0c0" to .5))).classify(JSONArray().put(r),previewNotifications()[0]).getString("status"))
    }
    @Test fun sharedAppExclusionKeepsSenderAllowanceWithinKakao() {
        val common=rule();common.getJSONObject("scope").put("type","SPECIFIC_APP").put("apps",JSONArray().put("com.kakao.talk").put("sms"))
        val exception=rule("SHOW").put("id","allow");exception.getJSONObject("scope").put("type","SPECIFIC_SENDER").put("apps",JSONArray().put("com.kakao.talk")).put("sender_names",JSONArray().put("문세현"))
        val policies=JSONArray().put(common).put(exception)
        fun row(pkg:String,sender:String)=previewNotifications()[0].copy(packageName=pkg,messagesJson=JSONArray().put(JSONObject().put("sender",sender).put("text","광고").put("timestamp",1)).toString())
        val runtime=StructuredPolicyRuntime(engine(emptyMap()))
        assertEquals("SHOW",runtime.classify(policies,row("com.kakao.talk","문세현")).getString("action"))
        assertEquals("HIDE",runtime.classify(policies,row("com.kakao.talk","다른 발신자")).getString("action"))
        assertEquals("HIDE",runtime.classify(policies,row("sms","문세현")).getString("action"))
        assertEquals("unconfigured",runtime.classify(policies,row("other","문세현")).getString("status"))
    }
    @Test fun narrowAppScopeWinsAndSameScopeConflictRemainsVisible(){
        val broad=rule();val narrow=rule("SHOW").put("id","second");narrow.getJSONObject("scope").put("type","SPECIFIC_APP").put("apps",JSONArray().put(previewNotifications()[0].packageName))
        assertEquals("match",StructuredPolicyRuntime(engine(emptyMap())).classify(JSONArray().put(broad).put(narrow),previewNotifications()[0]).getString("status"))
        narrow.put("scope",PolicyContract.emptyScope())
        assertEquals("review",StructuredPolicyRuntime(engine(emptyMap())).classify(JSONArray().put(broad).put(narrow),previewNotifications()[0]).getString("status"))
    }
    @Test fun emptyContentIsLocalAndNeverMistakesTitleOrMessageForEmpty() {
        val r=rule();r.getJSONArray("conditions").getJSONObject(0).put("type","CONTENT").put("value","알림 내용이 비어 있는 경우")
        val rt=StructuredPolicyRuntime(JevEngine(context){error("no AI")})
        val empty=previewNotifications()[0].copy(title=null,text=null,bigText=null,subText=null,conversationTitle=null,messagesJson="[]",isGroupSummary=true)
        assertEquals("outside",rt.classify(JSONArray().put(r),empty,false).getString("status"))
        for(row in listOf(empty.copy(title="보안 경고"),empty.copy(messagesJson="""[{"text":"납부 요청"}]"""),empty.copy(messagesJson="""[{"mimeType":"image/jpeg"}]""")))
            assertEquals("match",rt.classify(JSONArray().put(r),row,false).getString("status"))
        r.put("enabled",false)
        assertEquals("unconfigured",rt.classify(JSONArray().put(r),empty,false).getString("status"))
    }
    @Test fun localPassCannotGuessWeatherOrBypassUnknownException() {
        val r=rule();r.getJSONArray("conditions").getJSONObject(0).put("type","CONTENT").put("value","날씨 정보")
        val rt=StructuredPolicyRuntime(JevEngine(context){error("no AI")})
        assertEquals("review",rt.classify(JSONArray().put(r),previewNotifications()[0],false).getString("status"))
        r.getJSONArray("conditions").getJSONObject(0).put("type","ANY").put("value","")
        r.getJSONArray("exceptions").put(JSONObject().put("id","exception").put("conditions",JSONArray().put(JSONObject().put("type","SECURITY").put("value","").put("negated",false))).put("logic","ALL").put("action","SHOW"))
        assertEquals("review",rt.classify(JSONArray().put(r),previewNotifications()[0],false).getString("status"))
    }

    @Test fun resultSummaryPreservesOutcomeExceptionsAndDisabledState() {
        val r=rule();r.getJSONObject("scope").put("apps",JSONArray().put("com.kakao.talk"))
        val rows=listOf(previewNotifications()[0].copy(packageName="com.kakao.talk",appLabel="카카오톡"))
        assertEquals("카카오톡 · 광고 → 숨김",policyResultText(r,rows))
        r.getJSONArray("exceptions").put(JSONObject().put("conditions",JSONArray().put(JSONObject().put("type","CONTENT").put("value","약속 관련 내용").put("negated",false))).put("logic","ALL").put("action","SHOW"))
        assertTrue(policyResultText(r,rows).contains("단, 약속 관련 내용 → 표시"))
        r.put("enabled",false)
        assertTrue(policyResultText(r,rows).contains("사용 안 함"))
    }

    private fun googleRule(id:String,subject:String,action:String="HIDE")=rule(action).put("id",id).put("name",subject).put("scope",PolicyContract.emptyScope().put("type","SPECIFIC_APP").put("apps",JSONArray().put("com.google.android.googlequicksearchbox"))).put("conditions",JSONArray().put(JSONObject().put("type","CONTENT").put("value",subject).put("negated",false)))
    @Test fun confirmedSportsHideIsNotVetoedByUncertainWeatherHide() {
        val policies=JSONArray().put(googleRule("sports","스포츠 정보")).put(googleRule("weather","날씨 정보"))
        val row=previewNotifications()[0].copy(packageName="com.google.android.googlequicksearchbox",text="팀 A 2 : 1 팀 B · 경기 종료")
        val rt=StructuredPolicyRuntime(engine(mapOf("r1c0" to .2,"r1c0_unknown" to 1.0)))
        val result=rt.classify(policies,row)
        assertEquals("outside",result.getString("status"))
        assertEquals(listOf("sports"),jsonStrings(result.getJSONArray("matched_policy_ids")))
        assertEquals("unconfigured",rt.classify(policies,row.copy(packageName="another.app")).getString("status"))
    }
    @Test fun uncertainSportsAloneCannotHideAndPossibleAllowStillProtects() {
        val row=previewNotifications()[0].copy(packageName="com.google.android.googlequicksearchbox")
        assertEquals("review",StructuredPolicyRuntime(engine(mapOf("r0c0" to .5))).classify(JSONArray().put(googleRule("sports","스포츠 정보")),row).getString("status"))
        val sports=googleRule("sports","스포츠 정보")
        val allowance=googleRule("allow","보안 정보","SHOW")
        assertEquals("review",StructuredPolicyRuntime(engine(mapOf("r1c0" to .5))).classify(JSONArray().put(sports).put(allowance),row).getString("status"))
        val weather=googleRule("weather","날씨 정보")
        weather.getJSONArray("exceptions").put(JSONObject().put("id","safety").put("logic","ALL").put("action","SHOW").put("conditions",JSONArray().put(JSONObject().put("type","SECURITY").put("value","").put("negated",false))))
        assertEquals("review",StructuredPolicyRuntime(engine(mapOf("r1c0" to .5))).classify(JSONArray().put(sports).put(weather),row).getString("status"))
    }

    @Test fun everySemanticConditionUsesSharedPredicateAndExplicitUnknown() {
        for(type in PolicyContract.events.filter { it !in listOf("ANY","EMPTY_CONTENT") }) {
            val c=JSONObject().put("type",type).put("value","Cedar Science").put("negated",false)
            val question=conditionQuestion(c)
            assertEquals(setOf("MATCH","NO_MATCH","UNKNOWN"),question.getJSONObject("criteria").keys().asSequence().toSet())
            for(q in listOf(question)) {
                assertTrue(type,q.getString("instructions").contains(conditionPredicate(c)))
                assertTrue(type,q.getString("instructions").contains("Cedar Science"))
                assertTrue(type,q.getString("instructions").contains("`title`"))
                assertTrue(type,q.getString("instructions").contains("`text`"))
            }
        }
    }
    @Test fun latestMessageSameTimestampNullAndTitleReachClassifierConsistently() {
        val row=previewNotifications()[0].copy(title="actual title",conversationTitle="room",bigText="historical "+"x".repeat(500),
            messagesJson="""[{"timestamp":1,"text":"old","sender":"old"},{"timestamp":1,"text":"new","sender":"Alex"}]""")
        var sent:JSONObject?=null
        val e=JevEngine(context){request->sent=request.getJSONObject("state");JSONObject().put("usage",JSONObject().put("input_tokens",1).put("output_tokens",1)).put("answers",JSONObject().apply {request.getJSONObject("questions").keys().forEach { put(it,choice(.99)) }})}
        StructuredPolicyRuntime(e).classify(JSONArray().put(rule()),row)
        assertEquals("new",row.preview());assertEquals("new",sent!!.getString("text"));assertEquals("Alex",sent!!.getString("sender"))
        assertEquals("actual title",sent!!.getString("title"));assertEquals("room",sent!!.getString("conversation"))
        assertEquals("",row.copy(messagesJson="""[{"text":null,"mimeType":"image/jpeg"}]""").currentMessageText())
        assertFalse(row.copy(packageName="com.kakao.talk",messagesJson="""[{"text":null,"mimeType":"image/jpeg"}]""").needsOriginalReview())
    }

    @Test fun legacyAnyQualifierIsReviewNotSilentGlobalHide() {
        val r=rule();r.getJSONArray("conditions").getJSONObject(0).put("type","ANY").put("value","세금 납부")
        val result=StructuredPolicyRuntime(JevEngine(context){error("must not call")}).classify(JSONArray().put(r),previewNotifications()[0])
        assertEquals("review",result.getString("status"));assertEquals("policy_contract",result.getString("decision_stage"))
    }

    @Test fun typedConditionBoundariesAndInvalidDistributions() {
        val e=engine(emptyMap())
        fun result(answer:JSONObject)=JSONObject().put("answers",JSONObject().put("q",answer))
        assertEquals(true,e.readConditionVerdict(result(choice(.9)),"q"))
        assertEquals(false,e.readConditionVerdict(result(choice(.05)),"q"))
        assertNull(e.readConditionVerdict(result(choice(.89)),"q"))
        assertNull(e.readConditionVerdict(result(choice(.06)),"q"))
        assertNull(e.readConditionVerdict(result(choice(.99,true)),"q"))
        val malformed=listOf(choice(.99).put("type","noul"),choice(.99).put("choice","invented"),
            choice(.99).apply {getJSONObject("probabilities").put("MATCH",1.4)},
            choice(.99).apply {getJSONObject("probabilities").remove("UNKNOWN")},choice(.99).put("confidence",".99"))
        malformed.forEach {assertThrows(Exception::class.java) {e.readConditionVerdict(result(it),"q")}}
    }

}
