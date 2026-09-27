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

/** Fixed human-authored expected behavior. No model generates the answer key.
 * Calls production controller, validator, save/reload, selection queue and Jev.
 * Only synthetic observations and isolated databases are used. */
@RunWith(AndroidJUnit4::class)
class PolicyPipelineDeviceTest {
    data class Sample(val pkg:String,val title:String,val text:String,val hide:Boolean,val status:String?=null)
    data class Step(val instruction:String,val samples:List<Sample>,val clarify:Boolean=false)
    private val google="com.google.android.googlequicksearchbox"
    private val gmail="com.google.android.gm"
    private val slack="com.Slack"
    private val kakao="com.kakao.talk"
    private val youtube="com.google.android.youtube"
    private val samples=listOf(
        Step("구글 앱의 스포츠 소식과 날씨 정보는 숨겨줘. 다른 알림은 그대로 보여줘.",listOf(
            Sample(google,"Arsenal 2 - 1 Chelsea","Full time",true),
            Sample(google,"서울 20도","맑음. 내일 비가 옵니다.",true),
            Sample(google,"Arsenal 3 - 0 Chelsea · Full time","",true),
            Sample(google,"계정 보안 경고","새 기기에서 로그인했습니다. 본인이 아니라면 비밀번호를 변경하세요.",false),
            Sample(gmail,"Sports newsletter","Arsenal 2 - 1 Chelsea, full time",false))),
        Step("Gmail에서는 세금 납부를 요구하는 알림만 보여줘. 다른 Gmail 알림은 숨겨줘. 다른 앱에는 적용하지 마.",listOf(
            Sample(gmail,"Tax payment due","Please pay your property tax by October 10.",false),
            Sample(gmail,"Tax payment receipt","Your property tax payment has been received. Nothing further is due.",true),
            Sample(gmail,"Team lunch","Lunch tomorrow at noon",true),
            Sample(gmail,"Alex","그거 오늘까지 처리해 주세요. 자세한 내용은 첨부 문서를 봐 주세요.",false,"review"),
            Sample(slack,"Team lunch","Lunch tomorrow at noon",false))),
        Step("모든 앱에서 광고는 숨겨줘. 단, 악기인 일렉기타와 기타 앰프 할인 광고는 보여줘.",listOf(
            Sample(kakao,"쇼핑","(광고) 오늘만 사무용 의자 30% 할인",true),
            Sample(gmail,"Music sale","Ad: electric guitars and guitar amplifiers 30% off today!",false),
            Sample(kakao,"배송 완료","주문하신 상품을 문 앞에 배송했습니다.",false))),
        Step("Slack에서는 AI 작업이 완료된 알림은 숨겨줘. AI가 내 승인이나 입력을 요청하는 알림은 보여줘. 나머지는 그대로 둬.",listOf(
            Sample(slack,"AI assistant","Task complete: your requested report is ready to download. No action needed.",true),
            Sample(slack,"AI assistant","Approval required: please approve deployment before I can continue.",false),
            Sample(slack,"AI assistant","I have started the report and am still working on it.",false))),
        Step("YouTube에서는 천문학에 관한 영상 알림은 숨겨줘. 그 밖의 주제와 다른 앱 알림은 그대로 보여줘.",listOf(
            Sample(youtube,"New video","How black holes form: an astronomy lecture about stars and galaxies.",true),
            Sample(youtube,"새 영상","집에서 만드는 김치찌개 요리법",false),
            Sample(gmail,"Astronomy lecture","How black holes form",false))),
        Step("회사 단톡방 알림은 숨겨줘.",emptyList(),clarify=true)
    )
    private suspend fun idle(c:PolicyController) { withTimeout(180000) {while(c.busy.value)delay(100)} }
    @Test fun naturalLanguageThroughSavedPolicyAndLiveSelection()=runBlocking {
        val inst=InstrumentationRegistry.getInstrumentation()
        val app=inst.targetContext.applicationContext as InboxApplication
        val userBefore=app.selection.state.value.optJSONObject("policy")?.toString()
        val report=JSONObject().put("fixture_version","pipeline-v1").put("cases",JSONArray()).put("metrics",JSONArray())
        val failures=mutableListOf<String>()
        for((index,step) in samples.withIndex()) {
            val folder=File(app.filesDir,"pipeline-test-${UUID.randomUUID()}").apply{mkdirs()}
            val context=object:ContextWrapper(app){override fun getFilesDir()=folder;override fun getDatabasePath(name:String)=File(folder,name)}
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            val case=JSONObject().put("index",index).put("instruction",step.instruction).put("observations",JSONArray())
            report.getJSONArray("cases").put(case)
            try {
                val manager=NotificationSelection(context,scope)
                val apps=listOf(google to "Google",gmail to "Gmail",slack to "Slack",kakao to "카카오톡",youtube to "YouTube")
                val catalog=JSONObject().put("apps",JSONArray(apps.map { it.first })).put("app_labels",JSONArray(apps.map { JSONObject().put("package",it.first).put("label",it.second) })).put("conversations",JSONArray()).put("senders",JSONArray())
                case.put("diagnostics",JSONArray())
                val controller=PolicyController(context,scope,manager,catalogProvider={catalog},diagnostic={stage,value->
                    case.getJSONArray("diagnostics").put(JSONObject().put("stage",stage).put("value",value))
                })
                controller.request(step.instruction);idle(controller)
                check(controller.error.value==null) { controller.error.value.orEmpty() }
                val proposal=manager.state.value.getJSONObject("policy_proposal")
                val result=proposal.getJSONObject("result");case.put("proposal",result)
                if(step.clarify) {
                    check(result.getString("status")=="clarification_required") { "Expected clarification" }
                    check(!proposal.optBoolean("validated") && !manager.state.value.has("policy")) {"Ambiguous policy applied"}
                } else {
                    check(result.getString("status")=="ready" && proposal.optBoolean("validated")) {"Not ready: ${result.getString("status")}"}
                    controller.apply();idle(controller);check(controller.error.value==null) {controller.error.value.orEmpty()}
                    // Read the saved JSON through a fresh manager; no direct runtime shortcut.
                    val reloaded=NotificationSelection(context,scope)
                    for((j,s) in step.samples.withIndex()) {
                        val row=previewNotifications()[0].copy(snapshotId="pipeline-$index-$j",packageName=s.pkg,appLabel=apps.first{it.first==s.pkg}.second,
                            notificationKey="pipeline-$index-$j",postedTime=System.currentTimeMillis(),capturedTime=System.currentTimeMillis(),title=s.title,text=s.text,bigText=null,subText=null,conversationTitle=null,messagesJson="[]",isGroupSummary=false,conversationIdentity=null)
                        reloaded.accept(row)
                        var actual:JSONObject?=null
                        withTimeout(90000) { while(true) {
                            actual=reloaded.state.value.getJSONObject("results").optJSONObject(row.snapshotId)
                            if(actual!=null && actual!!.optString("status")!="pending")break
                            delay(100)
                        } }
                        val status=actual!!.getString("status")
                        case.getJSONArray("observations").put(JSONObject().put("package",s.pkg).put("title",s.title).put("text",s.text).put("expected_hide",s.hide).put("actual",actual))
                        if((status=="outside")!=s.hide || (s.status!=null && status!=s.status))failures.add("$index/$j expected_hide=${s.hide} actual=$status")
                        inst.sendStatus(0,Bundle().apply {putString("case_result","$index/$j expected_hide=${s.hide} actual=$status")})
                    }
                }
            }catch(e:Exception) {case.put("error",e.message);failures.add("$index: ${e.message}")}
            finally {
                for(name in listOf("rule-management-usage.jsonl","jev-usage.jsonl"))File(folder,name).takeIf{it.exists()}?.readLines()?.forEach {report.getJSONArray("metrics").put(JSONObject(it))}
                scope.cancel();folder.deleteRecursively()
                File(app.cacheDir,"policy-pipeline-result.json").writeText(report.put("failures",JSONArray(failures)).toString(2))
            }
        }
        assertEquals("Real user policy must be preserved",userBefore,app.selection.state.value.optJSONObject("policy")?.toString())
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
}
