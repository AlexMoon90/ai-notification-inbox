package com.ainotification.inbox

import android.content.ContextWrapper
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class IncrementalExceptionsDeviceTest {
    private suspend fun idle(c:PolicyController) {withTimeout(180000) {while(c.busy.value)delay(100)};check(c.error.value==null){c.error.value.orEmpty()}}
    @Test fun hideAllThenAddModifyAndRemoveExceptionsAcrossReloads()=runBlocking {
        val inst=InstrumentationRegistry.getInstrumentation()
        val app=inst.targetContext.applicationContext as InboxApplication
        val realBefore=app.selection.state.value.optJSONObject("policy")?.toString()
        val folder=File(app.filesDir,"incremental-${UUID.randomUUID()}").apply{mkdirs()}
        val context=object:ContextWrapper(app){override fun getFilesDir()=folder;override fun getDatabasePath(name:String)=File(folder,name)}
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val sms="com.google.android.apps.messaging";val gmail="com.google.android.gm"
        val catalog=JSONObject().put("apps",JSONArray().put(sms).put(gmail)).put("app_labels",JSONArray().put(JSONObject().put("package",sms).put("label","문자 메시지")).put(JSONObject().put("package",gmail).put("label","Gmail"))).put("conversations",JSONArray()).put("senders",JSONArray())
        val unrelated=JSONObject().put("id","new_1").put("name","Gmail 보안 표시").put("enabled",true).put("scope",PolicyContract.emptyScope().put("type","SPECIFIC_APP").put("apps",JSONArray().put(gmail)))
            .put("conditions",JSONArray().put(JSONObject().put("type","SECURITY").put("value","").put("negated",false))).put("logic","ALL").put("action","SHOW").put("exceptions",JSONArray()).put("time",PolicyContract.emptyTime()).put("source_instruction","Gmail 보안 알림을 보여줘")
        val report=JSONObject().put("steps",JSONArray()).put("failures",JSONArray());val failures=mutableListOf<String>()
        val instructions=listOf("문자 메시지 앱의 알림은 전부 숨겨줘. Gmail 기준은 그대로 유지해.","문자 메시지에서 은행 입금 알림은 보여줘.","문자 메시지에서 세금 납부 안내도 보여줘.","문자의 세금 납부 예외를 재산세 납부 안내만 보여주는 것으로 바꿔줘. 은행 입금 예외는 유지해.","문자의 은행 입금 표시 예외는 삭제해줘. 전체 숨김과 재산세 납부 예외는 유지해.")
        val texts=listOf("[은행] 입금 100,000원. 급여가 계좌에 입금되었습니다.","재산세 납부 안내: 재산세 150,000원을 9월 30일까지 납부해 주세요.","자동차세 납부 안내: 자동차세 50,000원을 9월 30일까지 납부해 주세요.","(광고) 오늘만 운동화 50% 할인. 지금 구매하세요!","재산세 납부 안내: 재산세 150,000원을 납부해 주세요.")
        val expected=listOf(listOf(true,true,true,true,false),listOf(false,true,true,true,false),listOf(false,false,false,true,false),listOf(false,false,true,true,false),listOf(true,false,true,true,false))
        try {
            NotificationSelection(context,scope).installRules(JSONArray().put(unrelated),true,"")
            var parentId:String?=null
            var depositException:JSONObject?=null
            var taxExceptionId:String?=null
            for((i,instruction) in instructions.withIndex()) {
                val step=JSONObject().put("instruction",instruction).put("diagnostics",JSONArray()).put("observations",JSONArray());report.getJSONArray("steps").put(step)
                val manager=NotificationSelection(context,scope)
                val c=PolicyController(context,scope,manager,diagnostic={stage,value->step.getJSONArray("diagnostics").put(JSONObject().put("stage",stage).put("value",value))},catalogProvider={catalog})
                val before=canonical(c.currentRules())
                c.request(instruction);idle(c)
                val proposal=manager.state.value.getJSONObject("policy_proposal");step.put("proposal",proposal)
                check(proposal.optBoolean("validated") && proposal.getJSONObject("result").getString("status")=="ready") {"$i proposal not ready"}
                check(before==canonical(c.currentRules())) {"Saved before confirmation"}
                c.apply();idle(c)
                val restored=NotificationSelection(context,scope)
                val rules=jsonObjects(restored.state.value.getJSONObject("policy").getJSONArray("rules"))
                check(canonical(unrelated)==canonical(rules.single{it.getString("id")=="new_1"})) {"Unrelated policy changed"}
                val parent=rules.single{sms in jsonStrings(it.getJSONObject("scope").getJSONArray("apps"))}
                if(parentId==null)parentId=parent.getString("id") else check(parentId==parent.getString("id")) {"Parent replaced"}
                check(parent.getString("action")=="HIDE" && parent.getJSONArray("conditions").getJSONObject(0).getString("type")=="ANY") {"Whole-app baseline lost"}
                val exceptions=jsonObjects(parent.getJSONArray("exceptions"))
                check(exceptions.size==listOf(0,1,2,2,1)[i]) {"$i wrong exception count"}
                if(i==1)depositException=JSONObject(exceptions.single().toString())
                if(i in 2..3)check(exceptions.any {canonical(it)==canonical(depositException!!)}) {"Deposit exception changed"}
                if(i==2)taxExceptionId=exceptions.single{it.getString("id")!=depositException!!.getString("id")}.getString("id")
                if(i>=3)check(exceptions.any{it.getString("id")==taxExceptionId}) {"Tax exception identity changed"}
                for(j in texts.indices) {
                    val pkg=if(j==4)gmail else sms
                    val row=previewNotifications()[0].copy(snapshotId="step-$i-$j",notificationKey="step-$i-$j",packageName=pkg,appLabel=if(j==4)"Gmail" else "문자 메시지",postedTime=System.currentTimeMillis(),capturedTime=System.currentTimeMillis(),title=if(j==0)"은행" else "안내",text=texts[j],bigText=null,subText=null,conversationTitle=null,messagesJson="[]",isGroupSummary=false,conversationIdentity=null)
                    restored.accept(row)
                    var actual:JSONObject?=null
                    withTimeout(90000) {while(true){actual=restored.state.value.getJSONObject("results").optJSONObject(row.snapshotId);if(actual!=null && actual!!.optString("status")!="pending")break;delay(100)}}
                    val want=if(expected[i][j])"outside" else "match"
                    step.getJSONArray("observations").put(JSONObject().put("text",texts[j]).put("package",pkg).put("expected",want).put("actual",actual))
                    if(actual!!.getString("status")!=want)failures.add("$i/$j: expected=$want actual=${actual!!.getString("status")}")
                    inst.sendStatus(0,Bundle().apply{putString("case_result","$i/$j expected=$want actual=${actual!!.getString("status")}")})
                }
                File(app.cacheDir,"incremental-exceptions-result.json").writeText(report.toString(2))
            }
        }catch(e:Exception){failures.add(e.message.orEmpty())}
        finally {
            report.put("failures",JSONArray(failures)).put("metrics",JSONArray(listOf("rule-management-usage.jsonl","jev-usage.jsonl").flatMap {name->File(folder,name).takeIf{it.exists()}?.readLines().orEmpty()}.map{JSONObject(it)}))
            report.put("user_policy_preserved",realBefore==app.selection.state.value.optJSONObject("policy")?.toString())
            File(app.cacheDir,"incremental-exceptions-result.json").writeText(report.toString(2))
            scope.cancel();folder.deleteRecursively()
        }
        assertEquals(realBefore,app.selection.state.value.optJSONObject("policy")?.toString())
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
}
