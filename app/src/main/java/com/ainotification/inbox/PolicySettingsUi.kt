package com.ainotification.inbox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONArray
import org.json.JSONObject

internal fun actionLabel(action:String)=when(action){"SHOW"->"표시";"QUIET"->"조용히 보기";"HIDE"->"숨김";else->"Watch · 준비 중"}
internal fun conditionLabel(type:String)=mapOf("ANY" to "모든 내용","EMPTY_CONTENT" to "내용 없음","MEETING_CONFIRMED" to "약속 확정","SCHEDULE_CHANGE" to "일정 변경","PAYMENT_REQUIRED" to "납부·회비 요청","MONEY_RECEIVED" to "입금","REPLY_REQUIRED" to "답변 필요","ACTION_REQUIRED" to "행동 요청","DELIVERY" to "배송","RESERVATION" to "예약","SECURITY" to "보안·로그인","IMPORTANT_NOTICE" to "주요 공지","PROMOTION" to "광고","CASUAL_CHAT" to "잡담","USER_MENTIONED" to "내 이름 언급","AI_TASK_COMPLETED" to "AI 작업 완료","AI_INPUT_REQUIRED" to "AI 입력 필요","MEANINGFUL_CHANGE" to "의미 있는 변화","CONTENT" to "내용 조건")[type] ?: type
internal fun scopeLabel(rule:JSONObject,rows:List<CapturedNotification>):String {
    if(rule.getJSONObject("scope").getString("type")=="INITIAL_PREFERENCE")return "처음 선택한 방향 · 모든 앱"
    val s=rule.getJSONObject("scope");val rooms=jsonStrings(s.getJSONArray("conversation_ids"));val apps=jsonStrings(s.getJSONArray("apps"))
    val parts=mutableListOf<String>()
    val senders=jsonStrings(s.getJSONArray("sender_names"))
    if(rooms.isNotEmpty())parts.add(rooms.joinToString { id->
        val matches=rows.filter{it.conversationIdentity==id && (apps.isEmpty() || it.packageName in apps)}
        val title=matches.firstNotNullOfOrNull{it.conversationTitle?.takeIf(String::isNotBlank)}
        val room=title?.let{"‘$it’ 대화방"} ?: if(matches.any{it.hasGroupConversation()})"단톡방" else "선택한 대화방"
        if(senders.isEmpty())"$room 전체" else "${room}의 ${senders.joinToString()} 메시지"
    })
    else {
        if(apps.isNotEmpty())parts.add(apps.joinToString{pkg->rows.firstOrNull{it.packageName==pkg}?.appLabel?:pkg}) else parts.add("모든 앱")
        if(senders.isNotEmpty())parts.add(senders.joinToString()+" 메시지")
    }
    if(s.getString("relationship").isNotBlank())parts.add(mapOf("WORK" to "회사","FAMILY" to "가족","FRIENDS" to "친구·모임","PERSONAL" to "개인","SERVICE" to "서비스","AI" to "AI·도구")[s.getString("relationship")].orEmpty())
    s.optString("source_type").takeIf{it.isNotBlank()}?.let{parts.add(mapOf("MESSENGER" to "메신저","EMAIL" to "이메일","AI" to "AI 알림","OTHER" to "기타 출처")[it]?:it)}
    return parts.joinToString(" · ")
}
internal fun readableCondition(c:JSONObject)=(if(c.getBoolean("negated"))"해당하지 않음 · " else "")+conditionLabel(c.getString("type"))+c.getString("value").takeIf{it.isNotBlank()}?.let{" · $it"}.orEmpty()
@Composable internal fun PolicyDescription(r:JSONObject,rows:List<CapturedNotification>) {
    Column(Modifier.testTag("structured_rule_${r.getString("id")}"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(r.getString("name"),style=MaterialTheme.typography.titleMedium)
        Text("대상 · "+scopeLabel(r,rows),style=MaterialTheme.typography.labelLarge,modifier=Modifier.testTag("policy_scope_${r.getString("id")}"))
        Text("조건 · "+if(r.getString("logic")=="ALL")"아래 조건 모두" else "아래 조건 중 하나")
        jsonObjects(r.getJSONArray("conditions")).forEach{Text("• "+readableCondition(it))}
        Text("처리 · "+actionLabel(r.getString("action")),style=MaterialTheme.typography.labelLarge)
        val exceptions=jsonObjects(r.getJSONArray("exceptions"))
        if(exceptions.isEmpty())Text("예외 · 없음",style=MaterialTheme.typography.bodySmall)
        exceptions.forEach{e->
            Text("예외 · "+(if(e.getString("logic")=="ALL")"아래 조건 모두" else "아래 조건 중 하나")+" → "+actionLabel(e.getString("action")))
            jsonObjects(e.getJSONArray("conditions")).forEach{Text("  • "+readableCondition(it),style=MaterialTheme.typography.bodySmall)}
        }
        val t=r.getJSONObject("time")
        if(listOf("start","from","until").any{t.getString(it).isNotBlank()} || t.getJSONArray("days").length()>0)
            Text("시간 · ${t.getString("start")}–${t.getString("end")} ${jsonStrings(t.getJSONArray("days")).joinToString()}\n${t.getString("from")} ~ ${t.getString("until")} (${t.getString("zone")}, 종료 시각 제외)",style=MaterialTheme.typography.bodySmall)
        else Text("시간 · 항상",style=MaterialTheme.typography.bodySmall)
    }
}
internal fun policyResultText(r:JSONObject,rows:List<CapturedNotification>):String {
    fun conditions(a:JSONArray,logic:String)=jsonObjects(a).joinToString(if(logic=="ALL")" + " else " 또는 ") { c ->
        val label=if(c.getString("type")=="CONTENT")c.getString("value") else conditionLabel(c.getString("type"))+c.getString("value").takeIf{it.isNotBlank()}?.let{" ($it)"}.orEmpty()
        if(c.getBoolean("negated"))"$label 제외" else label
    }
    val lines=mutableListOf(scopeLabel(r,rows)+" · "+conditions(r.getJSONArray("conditions"),r.getString("logic"))+" → "+if(r.getBoolean("enabled"))actionLabel(r.getString("action")) else "사용 안 함")
    jsonObjects(r.getJSONArray("exceptions")).forEach{e->lines.add("단, "+conditions(e.getJSONArray("conditions"),e.getString("logic"))+" → "+actionLabel(e.getString("action")))}
    val t=r.getJSONObject("time")
    val days=jsonStrings(t.getJSONArray("days"))
    val time=listOf(days.joinToString(", "),if(t.getString("start").isNotBlank())"${t.getString("start")}–${t.getString("end")}" else "",listOf(t.getString("from"),t.getString("until")).filter{it.isNotBlank()}.joinToString(" ~ ")).filter{it.isNotBlank()}
    if(time.isNotEmpty())lines.add(time.joinToString(" · ")+" (${t.getString("zone")})")
    return lines.joinToString("\n")
}
@Composable internal fun PolicyProposalPanel(controller:PolicyController,rows:List<CapturedNotification>,onboarding:Boolean=false,compact:Boolean=false) {
    val state by controller.state.collectAsStateWithLifecycle();val busy by controller.busy.collectAsStateWithLifecycle();val error by controller.error.collectAsStateWithLifecycle()
    val proposal=state.optJSONObject("policy_proposal");val result=proposal?.optJSONObject("result")
    var riskConfirmed by rememberSaveable(proposal?.toString()){mutableStateOf(false)}
    var answer by rememberSaveable(result?.optString("question")){mutableStateOf("")}
    var writing by rememberSaveable(result?.optString("question")){mutableStateOf(false)}
    var chooseRoom by remember { mutableStateOf(false) }
    val stale=proposal!=null && proposal.optString("base_revision")!=state.optJSONObject("policy")?.optString("id").orEmpty()
    if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());Text("잠시만요…")}
    if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error)
    if(stale)Text("기준이 바뀌었어요. 다시 요청해 주세요.",color=MaterialTheme.colorScheme.error)
    if(proposal!=null && !(compact && result?.optString("status")=="ready")) Text(when(result?.optString("status")){"clarification_required"->"이것만 확인해 주세요";"ready"->"이렇게 바꿀까요?";else->"요청 확인"},style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("pending_policy_heading"))
    if(result!=null) {
        when(result.getString("status")) {
            "clarification_required"->{
                Text(result.getString("question"),style=MaterialTheme.typography.titleMedium)
                val options=jsonStrings(result.getJSONArray("options")).take(3)
                options.forEachIndexed{i,o->OutlinedButton(onClick={controller.request(o,true)},enabled=!busy && !stale,modifier=Modifier.fillMaxWidth().testTag("policy_answer_$i")){Text(o)}}
                if(options.isNotEmpty())TextButton(onClick={writing=!writing}){Text("직접 답하기")}
                if(writing || options.isEmpty()) {
                    OutlinedTextField(answer,{answer=it.take(2000)},label={Text("답변")},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("policy_answer_text"))
                    Button(onClick={controller.request(answer,true)},enabled=answer.isNotBlank()&&!busy&&!stale){Text("확인")}
                }
                val rooms=rows.filter{it.conversationIdentity!=null && !it.isGroupSummary}.distinctBy{it.conversationIdentity}.take(15)
                if(rooms.isNotEmpty())TextButton(onClick={chooseRoom=!chooseRoom}){Text(if(chooseRoom)"대화 접기" else "대화에서 선택")}
                if(chooseRoom)rooms.forEach { row ->
                    TextButton(onClick={controller.request("선택 대화: ${row.conversationTitle?:row.title?:"이름 없는 대화"}, 앱 ${row.packageName}, ID ${row.conversationIdentity}. 이 대상으로 적용해 주세요.",true)},enabled=!busy && !stale){Text("${row.appLabel} · ${row.conversationTitle?:row.title?:"이름 없음"}")}
                }
            }
            "unsupported"->{
                Text("이 요청은 아직 적용할 수 없어요.")
                OutlinedTextField(answer,{answer=it.take(2000)},label={Text("원하는 조건을 다시 적어 주세요")},enabled=!busy,modifier=Modifier.fillMaxWidth())
                Button(onClick={if(answer.isBlank())controller.retryProposal() else controller.request(answer,true)},enabled=!busy&&!stale,modifier=Modifier.testTag("retry_policy")){Text("다시 요청")}
            }
            "ready"->{
                val changes=PolicyContract.diff(proposal.getJSONArray("before"),result.getJSONArray("rules"))
                val risky=changes.any{(_,before,after)->(after!=null && after.optBoolean("enabled") && after.optString("action") in listOf("HIDE","QUIET")) || (after==null && before?.optString("action")=="SHOW")}
                if(changes.isEmpty())Text("이미 적용된 내용이에요.")
                changes.forEach{(op,before,after)->Column(Modifier.fillMaxWidth().background(if(!compact && after?.optString("action")=="HIDE")Color(0xFFFFF2EF) else Color.Transparent).padding(vertical=8.dp,horizontal=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    if(!compact || op=="DELETE")Text(when(op){"ADD"->"추가";"DELETE"->"삭제";else->"변경"},style=MaterialTheme.typography.labelMedium)
                    if(!compact && before!=null && after!=null)Text("이전: "+policyResultText(before,rows),style=MaterialTheme.typography.bodySmall,color=ModernMuted)
                    Text(policyResultText(after?:before!!,rows),style=MaterialTheme.typography.bodyLarge)
                }}
                if(risky)Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(riskConfirmed,{riskConfirmed=it},enabled=!busy,modifier=Modifier.testTag("risk_confirm"));Text("알림이 숨겨지거나 줄어드는 변경을 확인했어요.",style=MaterialTheme.typography.bodySmall)}
                ModernButton(if(onboarding)"시작하기" else "적용",{controller.apply(onboarding)},Modifier.testTag("confirm_policy"),!busy&&!stale&&proposal.optBoolean("validated")&&(!risky||riskConfirmed))
            }
        }
        TextButton(onClick={controller.discard()},enabled=!busy){Text("취소")}
    } else if(proposal!=null && !busy)Button(onClick={controller.retryProposal()},enabled=!stale){Text("다시 시도")}
}
@Composable internal fun PolicySettingsPanel(app:InboxApplication,rows:List<CapturedNotification>,c:PolicyController=app.policies, newRequest:Int=0, inlineAddButton:Boolean=true,onNewRequestHandled:()->Unit={}) {
    val state by c.state.collectAsStateWithLifecycle();val busy by c.busy.collectAsStateWithLifecycle()
    val rules=state.optJSONObject("policy")?.optJSONArray("rules")?:JSONArray()
    var input by rememberSaveable{mutableStateOf("")}
    var contextTarget by remember { mutableStateOf<JSONObject?>(null) }
    var editingScope by remember{mutableStateOf<JSONObject?>(null)}
    var selectedRule by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showReview by rememberSaveable { mutableStateOf(false) }
    val reviewError by c.error.collectAsStateWithLifecycle()
    val hasProposal=state.optJSONObject("policy_proposal")!=null
    LaunchedEffect(showReview,busy,hasProposal,reviewError) {
        if(showReview && !c.busy.value && c.state.value.optJSONObject("policy_proposal")==null && c.error.value==null)showReview=false
    }
    var autoVoice by rememberSaveable { mutableStateOf(false) }
    fun startNewRequest() { input="";c.saveInput("");autoVoice=true;showEditor=true }
    LaunchedEffect(newRequest) { if(newRequest>0){startNewRequest();onNewRequestHandled()} }
    var resetRevision by remember { mutableStateOf<String?>(null) }
    val graphRules=rules
    val preview=false
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("알림 기준",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)

        }
        Text("어떤 알림을, 어떻게 알려드릴까요?",color=ModernMuted)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(state.optJSONObject("policy")?.has("rules")==true) Text(policyDisplayGroups(jsonObjects(rules)).let{groups->"공통 ${groups.count{it.common}}개 · 개별 ${groups.count{!it.common}}개 · 꺼짐 ${groups.count{!it.rules.first().getBoolean("enabled")}}개"},style=MaterialTheme.typography.bodySmall)
        else if(state.optJSONObject("policy")!=null) Text("기존 기준 적용 중 · 새 구조 전환 전")
        else Text("아직 적용 중인 기준이 없습니다.")
        if(hasProposal || busy || reviewError!=null) OutlinedButton(onClick={showReview=true},modifier=Modifier.testTag("resume_policy_review")) {
            Text(if(busy)"기준 정리 중 · 확인" else "작성 중인 변경 이어서 확인")
        }
        PolicyRelationMap(jsonObjects(graphRules),rows,onAppSettings={pkg->contextTarget=JSONObject().put("label",if(pkg=="*")"모든 앱 규칙" else (rows.firstOrNull{it.packageName==pkg}?.appLabel?:pkg)+" 규칙").put("package",pkg)}){selectedRule=it}
        HorizontalDivider()
        Text("추가 설정",style=MaterialTheme.typography.titleMedium)
        Surface(onClick={showEditor=true;input="방해 금지 시간을 설정해줘: ";c.saveInput(input)},shape=MaterialTheme.shapes.medium,border=androidx.compose.foundation.BorderStroke(1.dp,ModernInk),modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Notifications,null,tint=ModernInk)
                Text("방해 금지",fontWeight=FontWeight.SemiBold);Text("시간 조건 설정",style=MaterialTheme.typography.bodySmall,color=ModernMuted)
            }
        }
        Surface(onClick={showEditor=true;input="기존 기준에 예외를 추가해줘: ";c.saveInput(input)},shape=MaterialTheme.shapes.medium,border=androidx.compose.foundation.BorderStroke(1.dp,ModernInk),modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Settings,null,tint=ModernInk)
                Text("예외 조건",fontWeight=FontWeight.SemiBold);Text("기준별로 설정",style=MaterialTheme.typography.bodySmall,color=ModernMuted)
            }
        }
        TextButton(onClick={resetRevision=state.optJSONObject("policy")?.optString("id").orEmpty()},enabled=!busy,modifier=Modifier.testTag("reset_policies")){Text("조건 초기화",color=MaterialTheme.colorScheme.error)}
        if(preview)selectedRule?.let{id->jsonObjects(graphRules).find{it.getString("id")==id}?.let{r->
            AlertDialog(onDismissRequest={selectedRule=null},title={Text("검토 중 · 아직 미적용")},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState())){PolicyDescription(r,rows)}},confirmButton={TextButton(onClick={selectedRule=null}){Text("닫기")}})
        }}
        if(!state.optJSONObject("policy")?.has("rules").orFalse() && (state.optJSONObject("policy")!=null || state.optJSONObject("rulebook")!=null)){
            Text("기존 기준은 그대로 적용 중입니다. 새 구조로 정리한 내용을 확인하면 전환됩니다.")
            val oldItems=jsonObjects(state.optJSONObject("rulebook")?.optJSONArray("items")?:JSONArray())
            if(oldItems.isEmpty())Text(state.optJSONObject("policy")?.optString("instruction") ?: "저장된 꺼짐 기준을 정리할 수 있습니다.")
            oldItems.forEachIndexed{i,item->Surface(shape=MaterialTheme.shapes.medium){Column(Modifier.fillMaxWidth().padding(14.dp)){
                Text("기존 기준 ${i+1} · "+if(item.optBoolean("enabled",true))"적용 중" else "꺼짐",style=MaterialTheme.typography.labelLarge)
                Text(item.getString("text"));Text("세부 조건 분리는 검토 후 적용됩니다.",style=MaterialTheme.typography.bodySmall)
            }}}
            Button(onClick={showReview=true;c.request("기존 기준의 의미와 적용 상태를 그대로 보존해서 새 정책 구조로 옮겨줘.",migrate=true)},enabled=!busy,modifier=Modifier.testTag("migrate_policy")){Text("기존 기준 정리·확인")}
        }
        if(!preview && jsonObjects(rules).any{it.getString("id")==selectedRule}) androidx.compose.ui.window.Dialog(onDismissRequest={selectedRule=null}) {
            Surface(shape=MaterialTheme.shapes.large) { Column(Modifier.semantics{testTagsAsResourceId=true}.heightIn(max=600.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                TextButton(onClick={selectedRule=null}){Text("닫기")}
        jsonObjects(rules).filter{!preview && it.getString("id")==selectedRule}.groupBy{scopeLabel(it,rows)}.forEach{(label,group)->
            Text(label,style=MaterialTheme.typography.titleLarge)
            group.forEachIndexed{index,r->Surface(shape=MaterialTheme.shapes.medium){Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Row { Checkbox(r.getBoolean("enabled"),{showReview=true;c.direct(r.getString("id"),"SET_ENABLED",it);selectedRule=null},enabled=!busy,modifier=Modifier.testTag("policy_toggle_${r.getString("id")}"));Text(if(!r.getBoolean("enabled"))"꺼짐" else if(!PolicyContract.timeMatches(r.getJSONObject("time"),System.currentTimeMillis()))"시간 조건 밖" else "적용 중") }
                PolicyDescription(r,rows)
                Row(Modifier.horizontalScroll(rememberScrollState())){listOf("SHOW","QUIET","HIDE").forEach{action->FilterChip(selected=r.getString("action")==action,onClick={showReview=true;c.direct(r.getString("id"),"ACTION",action);selectedRule=null},enabled=!busy,label={Text(actionLabel(action))})}}
                Row { TextButton(onClick={editingScope=r;selectedRule=null},enabled=!busy){Text("범위 변경")};TextButton(onClick={selectedRule=null;contextTarget=JSONObject().put("label",r.getString("name")).put("rule_id",r.getString("id")).put("scope",r.getJSONObject("scope"))},enabled=!busy,modifier=Modifier.testTag("edit_context_rule")){Text("규칙 작성·수정")};TextButton(onClick={showReview=true;c.direct(r.getString("id"),"DELETE");selectedRule=null},enabled=!busy,modifier=Modifier.testTag("policy_delete_$index")){Text("삭제")}}
            }}}
        }
            } }
        }
        Text("체크한 기준만 적용합니다. 시스템 상태 알림은 수집하지 않습니다. 조용히 보기는 Inbox에 남기며 별도 알림음을 만들지 않습니다.",style=MaterialTheme.typography.bodySmall)
        if(showEditor) PolicyVoiceEditor(input,{input=it;c.saveInput(it)},busy,autoVoice,{autoVoice=false},{showEditor=false;autoVoice=false}) {
            c.request(input);input="";c.saveInput("");showEditor=false;autoVoice=false;showReview=true
        }
        if(showReview) androidx.compose.ui.window.Dialog(onDismissRequest={showReview=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth().padding(16.dp).heightIn(max=650.dp).semantics{testTagsAsResourceId=true}.testTag("policy_review_dialog")) {
                Column(Modifier.semantics{testTagsAsResourceId=true}.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text("기준 확인",style=MaterialTheme.typography.titleLarge)
                        TextButton(onClick={showReview=false},modifier=Modifier.testTag("close_policy_review")){Text("나중에 계속")}
                    }
                    Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        PolicyProposalPanel(c,rows)
                    }
                }
            }
        }
        if(inlineAddButton) Box(Modifier.fillMaxWidth(),contentAlignment=androidx.compose.ui.Alignment.Center) {
            NewPolicyButton(!busy){startNewRequest()}
        }
        Text("AI Watch 문맥 추적은 준비 중입니다. 일반 알림의 작업 완료·입력 필요 기준은 사용할 수 있습니다.",style=MaterialTheme.typography.bodySmall)
    }
    contextTarget?.let{target->ContextPolicyEditor(target,c,rows){contextTarget=null}}
    resetRevision?.let { revision -> AlertDialog(modifier=Modifier.semantics{testTagsAsResourceId=true},onDismissRequest={resetRevision=null},title={Text("모든 조건을 초기화할까요?")},text={Text("적용 중·꺼짐 기준, 검토 중인 변경과 설정 입력을 삭제합니다. 이후 알림은 조건으로 숨기지 않습니다. 수신 알림 기록과 API 연결은 유지됩니다.")},confirmButton={TextButton(onClick={c.reset(revision);resetRevision=null;selectedRule=null;input="";showEditor=false},enabled=!busy,modifier=Modifier.testTag("confirm_reset_policies")){Text("조건 초기화")}},dismissButton={TextButton(onClick={resetRevision=null},modifier=Modifier.testTag("cancel_reset_policies")){Text("취소")}}) }
    editingScope?.let{rule->AlertDialog(modifier=Modifier.semantics{testTagsAsResourceId=true},onDismissRequest={editingScope=null},title={Text("적용 범위 선택")},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState())){
        TextButton(onClick={editingScope=null;showReview=true;c.direct(rule.getString("id"),"SCOPE",PolicyContract.emptyScope())}){Text("모든 앱")}
        rows.distinctBy{it.packageName}.take(10).forEach{row->TextButton(onClick={editingScope=null;showReview=true;c.direct(rule.getString("id"),"SCOPE",PolicyContract.emptyScope().put("type","SPECIFIC_APP").put("apps",JSONArray().put(row.packageName)))}){Text(row.appLabel)}}
        rows.filter{it.conversationIdentity!=null&&!it.isGroupSummary}.distinctBy{it.conversationIdentity}.take(8).forEach{row->TextButton(onClick={editingScope=null;showReview=true;c.direct(rule.getString("id"),"SCOPE",PolicyContract.emptyScope().put("type","SPECIFIC_CONVERSATION").put("apps",JSONArray().put(row.packageName)).put("conversation_ids",JSONArray().put(row.conversationIdentity)))}){Text("${row.appLabel} · ${row.conversationTitle?:row.title?:"이름 없는 대화"}")}}
    }},confirmButton={TextButton(onClick={editingScope=null}){Text("취소")}})}
}
private fun Boolean?.orFalse()=this?:false
