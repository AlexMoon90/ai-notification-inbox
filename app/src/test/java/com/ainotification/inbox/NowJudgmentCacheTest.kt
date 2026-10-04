package com.ainotification.inbox

import android.app.Application
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class NowJudgmentCacheTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private fun condition(type:String="DELIVERY",value:String="")=JSONObject().put("type",type).put("value",value).put("negated",false)
    private fun rules(c:JSONObject=condition())=JSONArray().put(JSONObject().put("id","r1").put("conditions",JSONArray().put(c)).put("exceptions",JSONArray()))
    private fun row(text:String="09/30 18:42 배송비 6,500원 안내")=previewNotifications()[0].copy(packageName="test.delivery",serviceType=null,notificationCategory=null,messagesJson="[]",title="배송",text=text,bigText=null,conversationTitle=null,conversationIdentity=null,isGroupConversation=false)
    private fun state(row:CapturedNotification)=JSONObject().put("title",row.title).put("text",row.currentMessageText())
    private val questions get()=JSONObject().put("q",conditionQuestion(condition()))
    private fun key(row:CapturedNotification,revision:String="r1",version:String="v1",rules:JSONArray=rules())=NowPattern.key(row,revision,version,rules,state(row),questions)
    private fun result(questions:JSONObject,uncertain:Boolean=false)=JSONObject().put("usage",JSONObject().put("input_tokens",10).put("output_tokens",1)).put("answers",JSONObject().apply {
        questions.keys().forEach { put(it,JSONObject().put("type","choice").put("choice",if(uncertain)"UNKNOWN" else "MATCH").put("confidence",.99)
            .put("probabilities",JSONObject().put("MATCH",if(uncertain).005 else .99).put("NO_MATCH",.005).put("UNKNOWN",if(uncertain).99 else .005))) }
    })
    @Test fun safePatternReuseKeepsWordsAndIdentity() {
        val a=row();val b=row("10/01 12:15 배송비 32,800원 안내")
        assertEquals(key(a),key(b))
        assertNotEquals(key(a),key(b.copy(text="10/01 12:15 배송 취소")))
        assertNotEquals(key(a),key(a.copy(channelId="different")))
        assertNotEquals(key(a),key(a.copy(conversationIdentity="other-room")))
        assertNotEquals(key(a),key(a,"new-policy"))
        assertNotEquals(key(a),key(a,version="updated-app"))
        assertNotEquals(NowPattern.normalize("6,500원"),NowPattern.normalize("{AMOUNT}"))
        assertNotEquals(NowPattern.normalize("스타벅스 6,500원"),NowPattern.normalize("쿠팡 6,500원"))
    }
    @Test fun valueConditionsAndMessengerNeverGeneralize() {
        val a=row();val b=row("10/01 12:15 배송비 32,800원 안내")
        val sensitive=rules(condition("CONTENT","3만원 이상"))
        assertNotEquals(key(a,rules=sensitive),key(b,rules=sensitive))
        assertNotEquals(key(a.copy(serviceType="SMS")),key(b.copy(serviceType="SMS")))
        val exceptionRules=rules().also{it.getJSONObject(0).getJSONArray("exceptions").put(JSONObject().put("conditions",JSONArray().put(condition("CONTENT","10만원 이상"))))}
        assertFalse(NowPattern.mayGeneralize(exceptionRules))
    }
    @Test fun persistentCacheExpiresAndUncertaintyIsNotReused() {
        val file=File.createTempFile("now-cache",".json",context.cacheDir);file.delete()
        var now=1000L;var calls=0;var uncertain=false
        val engine=JevEngine(context){payload->calls++;result(payload.getJSONObject("questions"),uncertain)}
        fun cache()=NowJudgmentCache(AtomicFile(file),{"v1"},{now})
        fun evaluate(c:NowJudgmentCache)=c.evaluate(row(),"revision",rules(),state(row()),questions,engine)
        assertFalse(evaluate(cache()).getBoolean("cache_hit"))
        assertTrue(evaluate(cache()).getBoolean("cache_hit"));assertEquals(1,calls)
        now+=24*60*60*1000L
        assertFalse(evaluate(cache()).getBoolean("cache_hit"));assertEquals(2,calls)
        now+=24*60*60*1000L;uncertain=true
        assertFalse(evaluate(cache()).getBoolean("cache_hit"))
        assertFalse(evaluate(cache()).getBoolean("cache_hit"));assertEquals(4,calls)
    }
    @Test fun runtimeReusesAnswersBeforeAiQueueAndDeduplicatesQuestions() {
        val file=File.createTempFile("runtime-cache",".json",context.cacheDir);file.delete()
        val cache=NowJudgmentCache(AtomicFile(file),{"v1"})
        var calls=0;var count=0
        val engine=JevEngine(context){payload->calls++;count=payload.getJSONObject("questions").length();result(payload.getJSONObject("questions"))}
        val r=rules().getJSONObject(0).put("enabled",true).put("name","배송")
            .put("scope",PolicyContract.emptyScope()).put("time",PolicyContract.emptyTime()).put("logic","ALL").put("action","HIDE")
        r.getJSONArray("conditions").put(condition())
        val runtime=StructuredPolicyRuntime(engine,cache,"revision")
        val policies=JSONArray().put(r)
        assertEquals("HIDE",runtime.classify(policies,row()).getString("action"))
        assertEquals(1,count)
        val reused=runtime.classify(policies,row("10/01 12:15 배송비 32,800원 안내"),allowAi=false)
        assertEquals("HIDE",reused.getString("action"));assertTrue(reused.getBoolean("cache_hit"));assertEquals(1,calls)
        val changed=runtime.classify(policies,row("배송이 취소되었습니다"),allowAi=false)
        assertEquals("review",changed.getString("status"));assertEquals(1,calls)
        assertEquals("review",StructuredPolicyRuntime(engine,cache,"changed-policy").classify(policies,row(),false).getString("status"))
    }

    @Test fun runtimeNoApplicablePolicyAndLocalFalseAvoidAi() {
        var calls=0;val engine=JevEngine(context){calls++;error("unnecessary AI")}
        assertEquals("unconfigured",StructuredPolicyRuntime(engine).classify(JSONArray(),row()).getString("status"))
        val r=rules(condition("EMPTY_CONTENT")).getJSONObject(0).put("enabled",true).put("name","empty")
            .put("scope",JSONObject().put("type","APP").put("apps",JSONArray().put("test.delivery")).put("conversation_ids",JSONArray()).put("sender_names",JSONArray()))
            .put("time",JSONObject().put("zone","Asia/Seoul").put("days",JSONArray()).put("start","").put("end","").put("from","").put("until",""))
            .put("logic","ALL").put("action","HIDE")
        r.getJSONArray("conditions").put(condition())
        // Use canonical always-time contract from runtime defaults if optional fields are absent.
        val answer=StructuredPolicyRuntime(engine).classify(JSONArray().put(r),row())
        assertEquals("SHOW",answer.getString("action"));assertEquals(0,calls)
    }
}
