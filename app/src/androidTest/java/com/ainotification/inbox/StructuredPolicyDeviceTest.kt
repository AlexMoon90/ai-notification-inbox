package com.ainotification.inbox

import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StructuredPolicyDeviceTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val app get()=inst.targetContext.applicationContext as InboxApplication
    private val device get()=UiDevice.getInstance(inst)
    private fun find(tag:String,minHeight:Int=80):UiObject2 {
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard")
        if(tag in listOf("pending_policy_heading","confirm_policy","retry_policy") && !device.hasObject(By.res(tag))) {
            device.findObject(By.res("resume_policy_review"))?.click()
        }
        repeat(15){
            device.waitForIdle();Thread.sleep(1200)
            device.findObject(By.res(tag))?.let{if(!it.visibleBounds.isEmpty && it.visibleBounds.height()>=minHeight && (minHeight<80 || (it.visibleBounds.bottom<device.displayHeight-190 && it.visibleBounds.top>100)))return it}
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN,0.2f,500) ?: device.swipe(device.displayWidth/2,device.displayHeight*4/5,device.displayWidth/2,device.displayHeight/4,15)
        }
        device.dumpWindowHierarchy(File(app.cacheDir,"policy-ui-missing.xml"));device.takeScreenshot(File(app.cacheDir,"policy-ui-fixture.png"))
        error("Missing control: $tag")
    }
    private suspend fun idle(c:PolicyController){repeat(1500){if(!c.busy.value){assertNull(c.error.value);return};delay(100)};error("Timeout")}
    private suspend fun fixture(block:suspend (ContextWrapper,NotificationSelection,PolicyController,CoroutineScope)->Unit){
        val folder=File(app.filesDir,"structured-test-${UUID.randomUUID()}").apply{mkdirs()}
        val context=object:ContextWrapper(app){override fun getFilesDir()=folder;override fun getDatabasePath(name:String)=File(folder,name)}
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val manager=NotificationSelection(context,scope)
        val catalog=JSONObject().put("apps",JSONArray().put("com.kakao.talk")).put("app_labels",JSONArray().put(JSONObject().put("package","com.kakao.talk").put("label","카카오톡"))).put("conversations",JSONArray()).put("senders",JSONArray())
        val c=PolicyController(context,scope,manager,catalogProvider={catalog})
        try { block(context,manager,c,scope)
            val metrics=listOf("rule-management-usage.jsonl","jev-usage.jsonl").flatMap{File(folder,it).takeIf{f->f.exists()}?.readLines()?:emptyList()}.map{JSONObject(it)}
            inst.sendStatus(0,Bundle().apply{putInt("api_calls",metrics.size);putInt("jev_compile_calls",metrics.count{it.optString("purpose")=="compile"});putDouble("estimated_usd",metrics.sumOf{it.optDouble("estimated_usd",0.0)})})
        }finally{scope.cancel();folder.deleteRecursively()}
    }
    @Test fun shortKakaoReplyDoesNotInheritHistoryWarning() {
        val row=previewNotifications()[0].copy(packageName="com.kakao.talk",text="네",bigText=null,messagesJson=JSONArray().put(JSONObject().put("text","가".repeat(500)).put("timestamp",1)).put(JSONObject().put("text","네").put("timestamp",2)).toString())
        assertFalse(row.needsOriginalReview())
        val longLatest=JSONArray(row.messagesJson).put(JSONObject().put("text","나".repeat(500)).put("timestamp",3))
        assertTrue(row.copy(messagesJson=longLatest.toString()).needsOriginalReview())
    }
    @Test fun actualJevGoogleSportsWithOtherHideRules()=runBlocking{fixture{context,_,_,_->
        val userBefore=app.selection.state.value.optJSONObject("policy")?.toString()
        val seed=JSONObject(inst.context.assets.open("preservation-ui-fixture.json").bufferedReader().use{it.readText()}).getJSONArray("rules").getJSONObject(0)
        val rules=JSONArray()
        listOf("뉴스 관련 내용","날씨 예보, 강수 확률, 기온 등 날씨 정보","스포츠 경기, 경기 결과, 승패 또는 스포츠 팀·선수 관련 소식").forEachIndexed{i,subject->
            rules.put(JSONObject(seed.toString()).put("id","topic-$i").put("scope",PolicyContract.emptyScope().put("type","SPECIFIC_APP").put("apps",JSONArray().put("com.google.android.googlequicksearchbox"))).put("action","HIDE").put("exceptions",JSONArray()).put("time",PolicyContract.emptyTime())
                .put("conditions",JSONArray().put(JSONObject().put("type","CONTENT").put("value",subject).put("negated",false))))
        }
        val cases=listOf(
            Triple("파드리스 4 - 1 다저스","9회말, 실시간 경기",true),
            Triple("Arsenal 2 - 1 Chelsea","Full time",true),
            Triple("레이커스 102 - 98 셀틱스","4쿼터 · 경기 종료",true),
            Triple("서울: 20°","맑음 · 시간별 날씨 보기",true),
            Triple("계정 보안 알림","새 기기에서 로그인했습니다. 본인이 아니라면 계정을 보호하세요.",false),
            Triple("백업 완료","사진 12개를 계정에 백업했습니다.",false)
        )
        val failures=mutableListOf<String>()
        cases.forEachIndexed{i,(title,text,hide)->
            val row=previewNotifications()[0].copy(packageName="com.google.android.googlequicksearchbox",appLabel="Google",title=title,conversationTitle=null,text=text,bigText=null,messagesJson="[]",postedTime=System.currentTimeMillis())
            val result=StructuredPolicyRuntime(JevEngine(context)).classify(rules,row)
            val actual=result.getString("status")
            inst.sendStatus(0,Bundle().apply{putString("case_result","$i expected_hide=$hide actual=$actual")})
            if((actual=="outside")!=hide)failures.add("$i: $actual")
        }
        assertEquals(userBefore,app.selection.state.value.optJSONObject("policy")?.toString())
        assertTrue(failures.joinToString(),failures.isEmpty())
    }}
    @Test fun actualJevAdvertisingEvidenceRegression()=runBlocking{fixture{context,_,_,_->
        val userBefore=app.selection.state.value.optJSONObject("policy")?.toString()
        val rule=JSONObject(inst.context.assets.open("preservation-ui-fixture.json").bufferedReader().use{it.readText()}).getJSONArray("rules").getJSONObject(0)
        rule.put("scope",PolicyContract.emptyScope().put("type","SPECIFIC_APP").put("apps",JSONArray().put("com.coupang.mobile"))).put("action","HIDE").put("exceptions",JSONArray()).put("time",PolicyContract.emptyTime())
            .put("conditions",JSONArray().put(JSONObject().put("type","PROMOTION").put("value","").put("negated",false)))
        val cases=listOf(
            Triple("지금 가격 확인", "(광고) 고객님 지금이 좋은 가격이에요! 다시 한번 살펴보세요\n[수신거부:마이쿠팡]", "outside"),
            Triple("43% 할인", "(광고) 높이조절 테이블, 특가로 구매할 기회를 놓치지 마세요", "outside"),
            Triple("Limited offer", "Ad: Save 30% on desks today. Shop now!", "outside"),
            Triple("배송 완료", "주문하신 물품을 문 앞에 배송했습니다.", "match"),
            Triple("결제 완료", "주문하신 상품의 결제가 완료되었습니다. 주문 내역을 확인하세요.", "match"),
            Triple("로그인 알림", "새 기기에서 로그인했습니다. 본인이 아니라면 비밀번호를 변경하세요.", "match")
        )
        val failures=mutableListOf<String>()
        cases.forEachIndexed{i,(title,text,expected)->
            val row=previewNotifications()[0].copy(packageName="com.coupang.mobile",appLabel="쿠팡",title=title,conversationTitle=null,text=text,bigText=null,messagesJson="[]",postedTime=System.currentTimeMillis())
            val actual=StructuredPolicyRuntime(JevEngine(context)).classify(JSONArray().put(rule),row).getString("status")
            inst.sendStatus(0,Bundle().apply{putString("case_result","$i expected=$expected actual=$actual")})
            if(actual!=expected)failures.add("$i: $actual != $expected")
        }
        assertEquals(userBefore,app.selection.state.value.optJSONObject("policy")?.toString())
        assertTrue(failures.joinToString(),failures.isEmpty())
    }}
    @Test fun dailyUsageDoesNotStopClassification()=runBlocking{fixture{context,_,_,scope->
        val userBefore=app.selection.state.value.optJSONObject("policy")?.toString()
        var calls=0
        val engine=JevEngine(context){request->calls++;JSONObject().put("usage",JSONObject().put("input_tokens",1).put("output_tokens",1)).put("answers",JSONObject().apply{request.getJSONObject("questions").keys().forEach{put(it,JSONObject().put("type","choice").put("choice","MATCH").put("confidence",.99).put("probabilities",JSONObject().put("MATCH",.99).put("NO_MATCH",.005).put("UNKNOWN",.005)))}})}
        val m=NotificationSelection(context,scope,engine)
        val rule=JSONObject(inst.context.assets.open("preservation-ui-fixture.json").bufferedReader().use{it.readText()}).getJSONArray("rules").getJSONObject(0)
        rule.put("scope",PolicyContract.emptyScope()).put("action","HIDE").put("exceptions",JSONArray()).put("time",PolicyContract.emptyTime())
            .put("conditions",JSONArray().put(JSONObject().put("type","CONTENT").put("value","날씨 정보").put("negated",false)))
        m.installRules(JSONArray().put(rule),true,"")
        for(count in listOf(200,1000,10000)) {
            m.updateState{it.put("budget_day",System.currentTimeMillis()/86400000).put("budget_calls",count)}
            val row=previewNotifications()[0].copy(snapshotId="usage-$count",postedTime=System.currentTimeMillis(),text="날씨 예보 $count",messagesJson="[]")
            m.accept(row)
            repeat(100){if(m.state.value.getJSONObject("results").optJSONObject(row.snapshotId)?.optString("status")=="pending")delay(20)}
            assertEquals("outside",m.state.value.getJSONObject("results").getJSONObject(row.snapshotId).getString("status"))
            assertEquals(count+1,m.state.value.getInt("budget_calls"))
        }
        assertEquals(3,calls)
        assertEquals(userBefore,app.selection.state.value.optJSONObject("policy")?.toString())
    }}
    @Test fun emptyNotificationHonorsPolicyAtDailyLimit()=runBlocking{fixture{_,m,c,_->
        val userBefore=app.selection.state.value.optJSONObject("policy")?.toString()
        val rule=JSONObject(inst.context.assets.open("preservation-ui-fixture.json").bufferedReader().use{it.readText()}).getJSONArray("rules").getJSONObject(0)
        rule.put("scope",PolicyContract.emptyScope()).put("action","HIDE").put("exceptions",JSONArray()).put("time",PolicyContract.emptyTime())
            .put("conditions",JSONArray().put(JSONObject().put("type","CONTENT").put("value","알림 내용이 비어 있는 경우").put("negated",false)))
        m.installRules(JSONArray().put(rule),true,"")
        m.updateState{it.put("budget_day",System.currentTimeMillis()/86400000).put("budget_calls",200)}
        val row=previewNotifications()[0].copy(snapshotId="empty-test",postedTime=System.currentTimeMillis(),title=null,text=null,bigText=null,subText=null,conversationTitle=null,messagesJson="[]",isGroupSummary=true)
        m.accept(row)
        assertEquals("outside",m.state.value.getJSONObject("results").getJSONObject(row.snapshotId).getString("status"))
        assertEquals(200,m.state.value.getInt("budget_calls"))
        m.accept(row.copy(snapshotId="title-only",title="보안 경고"))
        assertNotEquals("outside",m.state.value.getJSONObject("results").getJSONObject("title-only").getString("status"))
        assertEquals(userBefore,app.selection.state.value.optJSONObject("policy")?.toString())
    }}
    @Test fun actualOpenAiJsonRiskValidationUiConfirmationAndJevClassification()=runBlocking{fixture{context,m,c,scope->
        val actualBefore=app.selection.state.value.optJSONObject("policy")?.toString()
        c.request("모든 앱에서 광고는 숨겨줘. 단, 악기인 일렉기타와 기타 앰프의 할인 광고는 보여줘. 다른 알림은 그대로 보여줘.")
        idle(c)
        val proposal=m.state.value.getJSONObject("policy_proposal");val result=proposal.getJSONObject("result")
        inst.sendStatus(0,Bundle().apply{putString("synthetic_editor_result",result.toString())})
        assertEquals("ready",result.getString("status"));assertTrue(proposal.getBoolean("validated"));assertFalse(m.state.value.has("policy"))
        assertTrue(jsonObjects(result.getJSONArray("rules")).any{it.getString("action")=="HIDE"})
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{activity->activity.setContent{InboxTheme{Column(Modifier.fillMaxSize().semantics{testTagsAsResourceId=true}.verticalScroll(rememberScrollState()).padding(20.dp)){PolicySettingsPanel(app,emptyList(),c)}}}}
            find("confirm_policy").click();delay(300);idle(c)
            assertTrue(m.state.value.getJSONObject("policy").has("rules"))
        }
        val restored=NotificationSelection(context,scope)
        assertEquals(canonical(m.state.value.getJSONObject("policy")),canonical(restored.state.value.getJSONObject("policy")))
        val rules=c.currentRules();val runtime=StructuredPolicyRuntime(JevEngine(context))
        val row=previewNotifications()[0].copy(postedTime=System.currentTimeMillis(),capturedTime=System.currentTimeMillis(),text="[광고] 오늘만 운동화 전품목 30% 할인!",bigText=null,messagesJson="[]")
        val general=runtime.classify(rules,row)
        val exception=runtime.classify(rules,row.copy(text="[광고] 일렉 기타와 기타 앰프 20% 할인 판매!"))
        val unrelated=runtime.classify(rules,row.copy(text="내일 회의가 오전 10시에서 11시로 변경되었습니다."))
        inst.sendStatus(0,Bundle().apply{putString("synthetic_rules",rules.toString());putString("synthetic_results",JSONArray().put(general).put(exception).put(unrelated).toString());putString("judgment_metrics",File(context.filesDir,"jev-usage.jsonl").readText())})
        assertEquals("outside",general.getString("status"))
        assertEquals("match",exception.getString("status"))
        assertEquals("match",unrelated.getString("status"))
        assertEquals(actualBefore,app.selection.state.value.optJSONObject("policy")?.toString())
    }}
    @Test fun policyIdsSurviveLegacyMigrationAdditionAndEditing()=runBlocking{fixture{_,m,c,_->
        val userBefore=app.selection.state.value.optJSONObject("policy")?.toString()
        m.updateState{it.put("policy",JSONObject().put("id","legacy-fixture").put("instruction","모든 앱의 일정 변경을 보여줘"))
            .put("rulebook",JSONObject().put("items",JSONArray().put(JSONObject().put("id","legacy-item-uuid").put("text","모든 앱의 일정 변경을 보여줘").put("enabled",true))))}
        suspend fun confirm(){
            assertEquals("ready",m.state.value.getJSONObject("policy_proposal").getJSONObject("result").getString("status"))
            ActivityScenario.launch(MainActivity::class.java).use{scenario->
                scenario.onActivity{activity->activity.setContent{InboxTheme{Column(Modifier.fillMaxSize().semantics{testTagsAsResourceId=true}.verticalScroll(rememberScrollState()).padding(20.dp)){PolicySettingsPanel(app,emptyList(),c)}}}}
                find("confirm_policy").click();delay(500);idle(c)
                assertFalse(m.state.value.has("policy_proposal"))
            }
        }
        c.request("기존 일정 변경 기준을 유지하고, 모든 앱에서 보안 및 로그인 알림도 보여줘.");idle(c);confirm()
        val original=jsonObjects(c.currentRules()).associate{it.getString("id") to canonical(it)}
        assertTrue(original.isNotEmpty());assertFalse(original.containsKey("legacy-item-uuid"))
        c.request("기존 기준은 그대로 두고 모든 앱의 배송 알림을 표시하는 별도 기준을 추가해줘.");idle(c);confirm()
        val added=jsonObjects(c.currentRules()).filter{it.getString("id") !in original}
        assertEquals(1,added.size);val deliveryId=added.single().getString("id")
        original.forEach{(id,json)->assertEquals(json,canonical(jsonObjects(c.currentRules()).single{it.getString("id")==id}))}
        c.request("방금 추가한 배송 알림 기준의 동작만 조용히 처리로 바꿔줘. 범위와 조건 및 다른 기준은 그대로 유지해줘.");idle(c);confirm()
        assertEquals("QUIET",jsonObjects(c.currentRules()).single{it.getString("id")==deliveryId}.getString("action"))
        original.forEach{(id,json)->assertEquals(json,canonical(jsonObjects(c.currentRules()).single{it.getString("id")==id}))}
        assertEquals(userBefore,app.selection.state.value.optJSONObject("policy")?.toString())
        inst.sendStatus(0,Bundle().apply{putBoolean("migration_add_edit_confirmed",true);putBoolean("unrelated_rules_preserved",true)})
    }}
    @Test fun preservedMeetingExceptionAndStructuredPendingUi()=runBlocking{fixture{_,m,c,_->
        val original="휴대폰 시스템의 모든 알림은 받지 않는다. 단, 출처와 관계없이 약속 또는 미팅에 관련된 공지는 허용한다. 카카오톡에서 오는 광고성 알림은 받지 않는다. 문자에서 오는 광고성 문자는 받지 않는다."
        val history=JSONArray().put(JSONObject().put("role","user").put("text","기존 기준은 요청한 부분만 수정해줘. 모든 앱의 약속 확정, 일정 변경, 보안 및 로그인, AI 작업 완료와 입력 필요는 보여줘. 모든 앱의 광고와 프로모션, 뉴스 속보는 숨겨줘. Codex가 작업을 끝냈거나 내 입력을 기다릴 때 알려줘."))
            .put(JSONObject().put("role","assistant").put("text","기존 전체 차단 기준은 어떻게 처리할까요?"))
            .put(JSONObject().put("role","user").put("text","OS 전체 알림 차단만 이관하지 않아. 약속 또는 미팅 관련 공지의 출처와 관계없는 허용 예외와 나머지 기존 기준은 유지해줘."))
        m.updateState{it.put("policy",JSONObject().put("id","legacy").put("instruction",original)).put("policy_proposal",JSONObject().put("base_revision","legacy").put("history",history).put("migration",true).put("validated",false))}
        c.retryProposal();idle(c)
        val initial=m.state.value.getJSONObject("policy_proposal").getJSONObject("result")
        if(initial.getString("status")=="clarification_required"){
            inst.sendStatus(0,Bundle().apply{putString("synthetic_clarification",initial.getString("question"))})
            c.request("표시하도록 요청한 모든 조건은 숨김 조건과 겹쳐도 표시해줘. 약속·미팅 공지 예외도 그대로 유지해줘.",answer=true);idle(c)
        }
        val p=m.state.value.getJSONObject("policy_proposal");val r=p.getJSONObject("result")
        inst.sendStatus(0,Bundle().apply{putString("synthetic_preservation_result",r.toString());putInt("repair_count",p.getInt("repair_count"))})
        assertEquals("ready",r.getString("status"));assertTrue(p.getBoolean("validated"));assertEquals(original,m.state.value.getJSONObject("policy").getString("instruction"))
        val rules=jsonObjects(r.getJSONArray("rules"))
        fun broad(a:JSONArray)=jsonObjects(a).any{it.getString("type")=="CONTENT" && (it.getString("value").contains("약속") || it.getString("value").contains("미팅"))}
        assertTrue(rules.any{it.getString("action")=="SHOW" && broad(it.getJSONArray("conditions"))})
        rules.filter{it.getString("action")=="HIDE"}.forEach{assertTrue(jsonObjects(it.getJSONArray("exceptions")).any{e->e.getString("action")=="SHOW" && broad(e.getJSONArray("conditions"))})}
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{activity->activity.setContent{InboxTheme{Column(Modifier.fillMaxSize().semantics{testTagsAsResourceId=true}.verticalScroll(rememberScrollState()).padding(20.dp)){PolicySettingsPanel(app,emptyList(),c)}}}}
            find("pending_policy_heading",1);find("policy_scope_${rules.first().getString("id")}",1);find("confirm_policy").click();delay(500);idle(c)
            assertTrue(m.state.value.getJSONObject("policy").has("rules"));assertFalse(m.state.value.has("policy_proposal"))
        }
    }}
    @Test fun groupChatRestrictionDoesNotBecomeAllCasualChat()=runBlocking{fixture{_,m,c,_->
        c.request("단체방에서 오는 잡담만 숨겨줘. 개인 대화는 그대로 보여줘.");idle(c)
        assertEquals("clarification_required",m.state.value.getJSONObject("policy_proposal").getJSONObject("result").getString("status"))
        assertFalse(m.state.value.has("policy"))
    }}
    @Test fun freshVoiceRequestUsesTranscriptWithoutOldInput()=runBlocking{fixture{_,m,c,_->
        val actualBefore=app.selection.state.value.optJSONObject("policy")?.toString()
        c.saveInput("지난번 요청 문장은 새 입력에 나오면 안 됩니다")
        var launches=0
        val owner=object:androidx.activity.result.ActivityResultRegistryOwner {
            override val activityResultRegistry=object:androidx.activity.result.ActivityResultRegistry() {
                override fun <I,O> onLaunch(requestCode:Int,contract:androidx.activity.result.contract.ActivityResultContract<I,O>,input:I,options:androidx.core.app.ActivityOptionsCompat?) {
                    val intent=contract.createIntent(app,input)
                    assertEquals(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH,intent.action)
                    launches++
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        dispatchResult(requestCode,if(launches==1)android.app.Activity.RESULT_OK else android.app.Activity.RESULT_CANCELED,
                            android.content.Intent().putStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS,arrayListOf("배송 알림도 보여줘")))
                    }
                }
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{activity->activity.setContent{androidx.compose.runtime.CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides owner){InboxTheme{Column(Modifier.fillMaxSize().semantics{testTagsAsResourceId=true}.verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp)){PolicySettingsPanel(app,emptyList(),c)}}}}}
            find("new_policy").click();find("policy_voice_editor",1)
            assertEquals(1,launches)
            assertEquals("배송 알림도 보여줘",c.state.value.getString("policy_editor_input"))
            assertTrue(find("generate_policy").isEnabled)
            assertFalse(m.state.value.has("policy_proposal"))
            find("close_policy_editor").click()
            find("new_policy").click();find("policy_voice_editor",1)
            assertEquals(2,launches)
            assertEquals("",c.state.value.getString("policy_editor_input"))
            assertFalse(find("generate_policy").isEnabled)
            assertFalse(m.state.value.has("policy_proposal"))
        }
        assertEquals(actualBefore,app.selection.state.value.optJSONObject("policy")?.toString())
    }}
    @Test fun incrementalJsonAddUpdateAndClarify()=runBlocking{fixture{_,m,c,_->
        val actualBefore=app.selection.state.value.optJSONObject("policy")?.toString()
        val seed=JSONObject(inst.context.assets.open("preservation-ui-fixture.json").bufferedReader().use{it.readText()}).getJSONArray("rules").getJSONObject(0)
        seed.put("name","보안 알림 표시").put("source_instruction","모든 앱의 보안 알림을 보여줘")
        seed.put("conditions",JSONArray().put(JSONObject().put("type","SECURITY").put("value","").put("negated",false)))
        m.installRules(JSONArray().put(seed),true,"")
        val original=canonical(seed)
        c.request("모든 앱의 배송 알림도 표시하는 기준을 추가해줘.");idle(c)
        var result=m.state.value.getJSONObject("policy_proposal").getJSONObject("result")
        assertEquals("ready",result.getString("status"))
        assertEquals(original,canonical(jsonObjects(result.getJSONArray("rules")).single{it.getString("id")==seed.getString("id")}))
        val delivery=jsonObjects(result.getJSONArray("rules")).single{r->jsonObjects(r.getJSONArray("conditions")).any{it.getString("type")=="DELIVERY"}}
        val deliveryId=delivery.getString("id")
        c.apply();idle(c)
        c.request("방금 배송 알림 기준의 처리만 조용히 보기로 바꿔줘. 범위와 다른 기준은 유지해줘.");idle(c)
        result=m.state.value.getJSONObject("policy_proposal").getJSONObject("result")
        assertEquals("ready",result.getString("status"))
        assertEquals("QUIET",jsonObjects(result.getJSONArray("rules")).single{it.getString("id")==deliveryId}.getString("action"))
        assertEquals(original,canonical(jsonObjects(result.getJSONArray("rules")).single{it.getString("id")==seed.getString("id")}))
        c.apply();idle(c)
        val saved=canonical(c.currentRules())
        c.request("그 기준은 이제 숨겨줘.");idle(c)
        assertEquals(m.state.value.getJSONObject("policy_proposal").getJSONObject("result").toString(),"clarification_required",m.state.value.getJSONObject("policy_proposal").getJSONObject("result").getString("status"))
        assertEquals(saved,canonical(c.currentRules()))
        assertEquals(actualBefore,app.selection.state.value.optJSONObject("policy")?.toString())
    }}
    @Test fun commonAdsAcrossAppsAndScopedSenderException()=runBlocking{fixture{context,m,_,scope->
        val actualBefore=app.selection.state.value.optJSONObject("policy")?.toString()
        val sms="com.google.android.apps.messaging"
        val catalog=JSONObject().put("apps",JSONArray().put("com.kakao.talk").put(sms).put("com.whatsapp"))
            .put("app_labels",JSONArray().put(JSONObject().put("package","com.kakao.talk").put("label","카카오톡")).put(JSONObject().put("package",sms).put("label","문자 메시지")).put(JSONObject().put("package","com.whatsapp").put("label","WhatsApp")))
            .put("conversations",JSONArray()).put("senders",JSONArray().put("문세현"))
        val c=PolicyController(context,scope,m,catalogProvider={catalog})
        suspend fun edit(text:String) { c.request(text);idle(c);val p=m.state.value.getJSONObject("policy_proposal");assertEquals(p.getJSONObject("result").toString(),"ready",p.getJSONObject("result").getString("status"));c.apply();idle(c) }
        edit("카카오톡에서 광고는 숨겨줘.")
        val initialId=c.currentRules().getJSONObject(0).getString("id")
        edit("문자 메시지 앱의 광고도 숨겨줘. 나머지 앱은 그대로 둬.")
        val common=c.currentRules().getJSONObject(0)
        assertEquals(1,c.currentRules().length());assertEquals(initialId,common.getString("id"));assertEquals("HIDE",common.getString("action"))
        assertEquals(setOf("com.kakao.talk",sms),jsonStrings(common.getJSONObject("scope").getJSONArray("apps")).toSet())
        assertEquals("SPECIFIC_APP",common.getJSONObject("scope").getString("type"))
        val commonBefore=canonical(common)
        edit("단, 카카오톡에서 문세현이 보내는 쇼핑 광고는 보여줘. 문자 메시지와 다른 발신자의 광고는 기존대로 숨겨줘.")
        val rules=jsonObjects(c.currentRules());assertEquals(2,rules.size)
        assertEquals(commonBefore,canonical(rules.single{it.getString("id")==initialId}))
        val allow=rules.single{it.getString("id")!=initialId};val allowId=allow.getString("id")
        assertEquals("SHOW",allow.getString("action"));assertEquals(listOf("com.kakao.talk"),jsonStrings(allow.getJSONObject("scope").getJSONArray("apps")))
        assertEquals(listOf("문세현"),jsonStrings(allow.getJSONObject("scope").getJSONArray("sender_names")))
        assertEquals("ALL",allow.getString("logic"));assertTrue(jsonObjects(allow.getJSONArray("conditions")).any{it.getString("type")=="PROMOTION"})
        assertTrue(jsonObjects(allow.getJSONArray("conditions")).any{it.getString("type")=="CONTENT" && it.getString("value").contains("쇼핑")})
        val before=canonical(c.currentRules())
        val groups=policyDisplayGroups(rules);assertEquals(1,groups.count{it.common});assertEquals(1,groups.count{!it.common})
        val rows=listOf(previewNotifications()[0].copy(packageName="com.kakao.talk",appLabel="카카오톡"),previewNotifications()[0].copy(packageName=sms,appLabel="문자 메시지"))
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard")
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{activity->activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);activity.setContent{InboxTheme{androidx.compose.material3.Surface(color=androidx.compose.ui.graphics.Color.White){Column(Modifier.fillMaxSize().semantics{testTagsAsResourceId=true}.verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp)){PolicySettingsPanel(app,rows,c)}}}}}
            find("situation_$initialId",1);find("relation_rule_$allowId",1)
            assertFalse(device.hasObject(By.res("relation_rule_$initialId")))
            scenario.onActivity{it.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
            device.takeScreenshot(File(app.cacheDir,"grouping-overview.png"))
            find("relation_app_com.kakao.talk").click();find("app_common_$initialId",1);find("app_rule_$allowId",1)
            device.takeScreenshot(File(app.cacheDir,"grouping-kakao.png"))
            find("close_app_connections").click()
            find("relation_app_$sms").click();find("app_common_$initialId",1)
            assertFalse(device.hasObject(By.res("app_rule_$allowId")))
            find("app_common_$initialId").click();find("app_configure_$initialId").click();find("policy_scope_$initialId",1)
            assertEquals(before,canonical(c.currentRules()))
            scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
        }
        assertEquals(actualBefore,app.selection.state.value.optJSONObject("policy")?.toString())
    }}
    @Test fun reviewDialogKeepsQuestionsAndDiffAwayFromGraph()=runBlocking{fixture{context,m,_,scope->
        val userBefore=app.selection.state.value.optJSONObject("policy")?.toString()
        val seed=JSONObject(inst.context.assets.open("preservation-ui-fixture.json").bufferedReader().use{it.readText()}).getJSONArray("rules").getJSONObject(0)
        seed.getJSONObject("scope").put("type","SPECIFIC_APP").put("apps",JSONArray().put("com.kakao.talk"))
        val second=JSONObject(seed.toString()).put("id","new_2").put("name","문자 납부 요청")
            .put("conditions",JSONArray().put(JSONObject().put("type","PAYMENT_REQUIRED").put("value","").put("negated",false)))
        second.getJSONObject("scope").put("apps",JSONArray().put("com.google.android.apps.messaging"))
        m.installRules(JSONArray().put(seed).put(second),true,"")
        val before=JSONArray(m.state.value.getJSONObject("policy").getJSONArray("rules").toString())
        val added=JSONObject(seed.toString()).put("id","new_3").put("name","보안 알림 표시").put("scope",PolicyContract.emptyScope())
            .put("conditions",JSONArray().put(JSONObject().put("type","SECURITY").put("value","").put("negated",false)))
        val after=JSONArray(before.toString()).put(added)
        val ready=JSONObject().put("status","ready").put("message","보안 알림을 추가합니다.").put("question","").put("options",JSONArray()).put("uncertain",false).put("rules",after)
            .put("operations",JSONArray().put(JSONObject().put("type","ADD").put("id","new_3")))
        var calls=0
        val ai=SetupEngine(context){calls++;JSONObject().put("status","completed").put("output",JSONArray().put(JSONObject().put("type","message").put("content",JSONArray().put(JSONObject().put("type","output_text").put("text",ready.toString())))))}
        val catalog=JSONObject().put("apps",JSONArray().put("com.kakao.talk").put("com.google.android.apps.messaging")).put("conversations",JSONArray()).put("senders",JSONArray())
        val c=PolicyController(context,scope,m,ai,catalogProvider={catalog})
        val clarification=JSONObject(ready.toString()).put("status","clarification_required").put("question","보안 알림은 모든 앱에 적용할까요?").put("options",JSONArray().put("모든 앱에 적용")).put("rules",JSONArray()).put("operations",JSONArray())
        m.updateState{it.put("policy_proposal",JSONObject().put("base_revision",it.getJSONObject("policy").getString("id")).put("before",before).put("history",JSONArray().put(JSONObject().put("role","user").put("text","보안 알림 보여줘"))).put("validated",false).put("result",clarification))}
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard")
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            val rows=listOf(previewNotifications()[0].copy(packageName="com.kakao.talk",appLabel="카카오톡"),previewNotifications()[0].copy(packageName="com.google.android.apps.messaging",appLabel="문자"))
            scenario.onActivity{activity->activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);activity.setContent{InboxTheme{androidx.compose.material3.Surface(color=androidx.compose.ui.graphics.Color.White){Column(Modifier.fillMaxSize().semantics{testTagsAsResourceId=true}.verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp)){PolicySettingsPanel(app,rows,c)}}}}}
            find("policy_relation_map",1)
            assertFalse(device.hasObject(By.res("pending_policy_heading")))
            val kakao=find("relation_app_com.kakao.talk",1).visibleBounds
            val sms=find("relation_app_com.google.android.apps.messaging",1).visibleBounds
            assertTrue(kakao.height()<150)
            assertTrue(kotlin.math.abs(find("relation_rule_new_1",1).visibleBounds.centerY()-kakao.centerY())<15)
            assertTrue(kotlin.math.abs(find("relation_rule_new_2",1).visibleBounds.centerY()-sms.centerY())<15)
            scenario.onActivity{it.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)};delay(700)
            device.takeScreenshot(File(app.cacheDir,"adjacent-app-rules.png"))
            find("resume_policy_review").click();find("policy_review_dialog",1);find("policy_answer_0").click();idle(c)
            find("confirm_policy",1)
            assertEquals(1,calls);assertEquals(canonical(before),canonical(c.currentRules()))
            device.takeScreenshot(File(app.cacheDir,"separate-policy-review.png"))
            find("close_policy_review").click();assertFalse(device.hasObject(By.res("confirm_policy")))
            find("resume_policy_review").click();find("confirm_policy").click();idle(c);delay(500)
            assertFalse(device.hasObject(By.res("policy_review_dialog")));assertFalse(device.hasObject(By.res("resume_policy_review")))
            assertEquals(3,c.currentRules().length());assertFalse(m.state.value.has("policy_proposal"))
            scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
        }
        assertEquals(userBefore,app.selection.state.value.optJSONObject("policy")?.toString())
    }}
    @Test fun relationNavigationAndConfirmedReset()=runBlocking{fixture{context,m,c,scope->
        val userBefore=app.selection.state.value.optJSONObject("policy")?.toString()
        val rules=JSONObject(inst.context.assets.open("preservation-ui-fixture.json").bufferedReader().use{it.readText()}).getJSONArray("rules")
        rules.getJSONObject(0).getJSONObject("scope").put("type","SPECIFIC_APP").put("apps",JSONArray().put("com.kakao.talk"))
        m.installRules(rules,true,"")
        val before=canonical(c.currentRules())
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{activity->activity.setContent{InboxTheme{androidx.compose.material3.Surface(color=androidx.compose.ui.graphics.Color.White){Column(Modifier.fillMaxSize().semantics{testTagsAsResourceId=true}.verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=20.dp,vertical=20.dp)){PolicySettingsPanel(app,listOf(previewNotifications()[0].copy(packageName="com.kakao.talk",appLabel="카카오톡")),c)}}}}}
            find("policy_relation_map",1)
            // Capture synthetic content only, after it has replaced the real screen. Production FLAG_SECURE stays intact.
            scenario.onActivity{it.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
            delay(700);device.takeScreenshot(File(app.cacheDir,"relation-overview.png"))
            val graph=find("policy_relation_map",1).visibleBounds
            val firstNode=find("relation_rule_${rules.getJSONObject(0).getString("id")}")
            val oldX=firstNode.visibleBounds.left
            val y=firstNode.visibleBounds.centerY()
            device.swipe(graph.centerX(),y,graph.right-35,y,25);delay(500)
            val moved=find("relation_rule_${rules.getJSONObject(0).getString("id")}").visibleBounds.left
            assertTrue("Dragging right must widen left column",moved>oldX+20)
            assertEquals(before,canonical(c.currentRules()))
            device.takeScreenshot(File(app.cacheDir,"relation-dragged.png"))
            device.swipe(graph.centerX(),y,graph.left+35,y,25);delay(500)
            assertTrue(find("relation_rule_${rules.getJSONObject(0).getString("id")}").visibleBounds.left<moved)
            val event=rules.getJSONObject(1).getString("id")
            assertFalse(device.hasObject(By.res("relation_spotlight")))
            find("situation_$event").click();delay(300)
            find("relation_spotlight",1)
            find("spotlight_app_com.kakao.talk",1).click()
            find("app_connections",1)
            find("app_common_$event",1)
            find("app_rule_${rules.getJSONObject(0).getString("id")}",1)
            assertEquals(before,canonical(c.currentRules()))
            device.takeScreenshot(File(app.cacheDir,"app-connections.png"))
            find("app_policy_settings").click()
            find("context_policy_editor",1)
            assertFalse(find("context_policy_submit",1).isEnabled)
            find("context_policy_request").text="광고는 숨기고 배송 안내는 보여줘"
            assertTrue(find("context_policy_submit",1).isEnabled)
            device.takeScreenshot(File(app.cacheDir,"context-app-editor.png"))
            find("close_context_policy",1).click()
            assertEquals(before,canonical(c.currentRules()))
            find("relation_app_com.kakao.talk",1).click()
            find("app_rule_${rules.getJSONObject(0).getString("id")}").click()
            find("app_configure_${rules.getJSONObject(0).getString("id")}").click()
            find("policy_scope_${rules.getJSONObject(0).getString("id")}",1)
            find("edit_context_rule",1).click()
            find("context_policy_request",1)
            assertEquals(before,canonical(c.currentRules()))
            find("close_context_policy",1).click()
            find("situation_$event").click();delay(300)
            find("relation_spotlight",1)
            assertFalse(device.hasObject(By.res("configure_focused_rule")))

            assertEquals(before,canonical(c.currentRules()))
            device.takeScreenshot(File(app.cacheDir,"relation-selected.png"))
            find("close_relation_spotlight").click()
            find("relation_rule_${rules.getJSONObject(0).getString("id")}").click()
            find("spotlight_text").click()
            find("configure_focused_rule").click()
            find("policy_scope_${rules.getJSONObject(0).getString("id")}",1)
            device.pressBack()
            fun openReset() {
                repeat(3) {
                    find("reset_policies").click()
                    if(device.wait(Until.hasObject(By.res("cancel_reset_policies")),3000))return
                }
                error("Reset confirmation did not open")
            }
            openReset();find("cancel_reset_policies").click()
            assertEquals(before,canonical(c.currentRules()))
            openReset();find("confirm_reset_policies").click();delay(500)
            assertFalse(m.state.value.has("policy"));assertFalse(NotificationSelection(context,scope).state.value.has("policy"))
            scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
        }
        assertEquals(userBefore,app.selection.state.value.optJSONObject("policy")?.toString())
    }}
    @Test fun structuredPolicyFixtureUi()=runBlocking{fixture{_,m,c,_->
        val result=JSONObject(inst.context.assets.open("preservation-ui-fixture.json").bufferedReader().use{it.readText()})
        val rules=result.getJSONArray("rules")
        // UI-only fixture captured from a separately audited synthetic response. No live AI claims here.
        m.updateState{it.put("policy_proposal",JSONObject().put("base_revision","").put("before",JSONArray()).put("history",JSONArray()).put("result",result).put("validated",true).put("hash",policyHash(rules)))}
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{activity->activity.setContent{InboxTheme{Column(Modifier.fillMaxSize().semantics{testTagsAsResourceId=true}.verticalScroll(rememberScrollState()).padding(20.dp)){PolicySettingsPanel(app,emptyList(),c)}}}}
            find("pending_policy_heading",1);device.dumpWindowHierarchy(File(app.cacheDir,"policy-ui-before.xml"));find("policy_scope_${rules.getJSONObject(0).getString("id")}",1)
            find("confirm_policy").click();delay(500);idle(c)
            assertEquals(canonical(rules),canonical(c.currentRules()));assertFalse(m.state.value.has("policy_proposal"))
        }
    }}
    @Test fun refreshStoredProposalWithoutApplying()=runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("refreshStoredProposal")=="true")
        val c=app.policies;val old=app.selection.state.value
        val active=canonical(old.getJSONObject("policy"));val history=canonical(old.getJSONObject("policy_proposal").getJSONArray("history"))
        c.retryProposal();idle(c)
        val p=app.selection.state.value.getJSONObject("policy_proposal");val r=p.getJSONObject("result")
        assertEquals(active,canonical(app.selection.state.value.getJSONObject("policy")))
        assertEquals(history,canonical(p.getJSONArray("history")))
        inst.sendStatus(0,Bundle().apply{putString("stored_proposal_status",r.getString("status"));putBoolean("stored_proposal_validated",p.getBoolean("validated"));putInt("proposed_rule_count",r.getJSONArray("rules").length());putInt("repair_count",p.getInt("repair_count"));putBoolean("active_policy_unchanged",true)})
        assertTrue(r.getString("status") in listOf("ready","clarification_required"))
    }
    @Test fun semanticQuestionDiagnostic()=runBlocking{fixture{context,_,_,_->
        val e=JevEngine(context)
        val q=JSONObject().put("ad",JSONObject().put("type","noul").put("instructions","Is `text` a promotional advertisement or sales offer?"))
            .put("guitar",JSONObject().put("type","noul").put("instructions","Is `text` promoting a sale of guitar gear, such as guitars, guitar amps or guitar pedals?"))
            .put("evidence",JSONObject().put("type","noul").put("instructions","Is `text` readable and sufficiently complete to determine what it is about?"))
        val results=JSONArray()
        for(text in listOf("[광고] 오늘만 운동화 전품목 30% 할인!","[광고] 일렉 기타와 기타 앰프 20% 할인 판매!","내일 회의가 오전 10시에서 11시로 변경되었습니다."))results.put(e.evaluateConditions(JSONObject().put("text",text),q).getJSONObject("answers"))
        inst.sendStatus(0,Bundle().apply{putString("atomic_question_judgments",results.toString())})
    }}
    @Test fun ambiguousRoomAsksBeforeAnyRuleIsApplied()=runBlocking{fixture{_,m,c,_->
        c.request("회사 단톡 알림은 숨기고 일정이 변경될 때만 보여줘.");idle(c)
        assertEquals("clarification_required",m.state.value.getJSONObject("policy_proposal").getJSONObject("result").getString("status"))
        assertFalse(m.state.value.has("policy"))
    }}
    @Test fun onboardingNavigationAndSavedInputRestoration()=runBlocking{fixture{_,m,c,_->
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{activity->activity.setContent{InboxTheme{FirstOnboardingScreen(app,emptyList(),true,{},c=c)}}}
            inst.waitForIdleSync();delay(500)
            find("onboarding_next").click();delay(500)
            assertEquals(1,m.state.value.getJSONObject("onboarding").getInt("step"))
            find("onboarding_next").click();delay(500)
            assertEquals(2,m.state.value.getJSONObject("onboarding").getInt("step"))
            find("onboarding_instruction").text="모든 앱의 일정 변경을 알려줘"
            delay(300)
            assertEquals("모든 앱의 일정 변경을 알려줘",m.state.value.getJSONObject("onboarding").getString("must_text"))
            scenario.recreate();scenario.onActivity{activity->activity.setContent{InboxTheme{FirstOnboardingScreen(app,emptyList(),true,{},c=c)}}}
            assertEquals("모든 앱의 일정 변경을 알려줘",find("onboarding_instruction").text)
            find("onboarding_next").click();delay(400)
            find("onboarding_next").click();delay(400)
            find("onboarding_generate").click();delay(200);idle(c)
            assertEquals("ready",m.state.value.getJSONObject("policy_proposal").getJSONObject("result").getString("status"))
            find("confirm_policy");device.waitForIdle();delay(1200)
            val confirm=find("confirm_policy");assertTrue(confirm.wait(Until.enabled(true),5000))
            device.takeScreenshot(File(app.cacheDir,"onboarding-before.png"));device.dumpWindowHierarchy(File(app.cacheDir,"onboarding-before.xml"))
            inst.sendStatus(0,Bundle().apply{putString("confirm_bounds",confirm.visibleBounds.toString());putBoolean("confirm_clickable",confirm.isClickable);putInt("screen_height",device.displayHeight)})
            confirm.click();delay(800);idle(c)
            device.takeScreenshot(File(app.cacheDir,"onboarding-after.png"))
            inst.sendStatus(0,Bundle().apply{putBoolean("fixture_has_policy",m.state.value.has("policy"));putBoolean("fixture_has_proposal",m.state.value.has("policy_proposal"));putBoolean("fixture_complete",m.state.value.optBoolean("onboarding_complete"))})
            assertTrue("Final confirmation did not complete onboarding",m.state.value.optBoolean("onboarding_complete"))
        }
    }}
    @Test fun installedUserPolicyIsPreservedAndSettingsEntryIsReachable() {
        val previous=app.selection.state.value.optJSONObject("policy")?.toString()
        ActivityScenario.launch(MainActivity::class.java).use{
            assertTrue(device.wait(Until.hasObject(By.res("nav_1")),15000));find("nav_1").click()
            find("policy_request")
            find("nav_2").click();find("open_onboarding").click();find("onboarding_next")
            assertEquals(previous,app.selection.state.value.optJSONObject("policy")?.toString())
        }
    }
}
