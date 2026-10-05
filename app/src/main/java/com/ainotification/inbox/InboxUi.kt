package com.ainotification.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = ModernInk
private val Blue = ModernAccent
@Composable internal fun InboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary=ModernAccent,onPrimary=ModernBg,
        primaryContainer=Color(0xFFFFF2EF),onPrimaryContainer=Color(0xFF7C1405),
        secondaryContainer=Color(0xFFD7D3D3),onSecondaryContainer=ModernInk,
        background=ModernBg,surface=ModernBg,onSurface=ModernInk,onBackground=ModernInk,
        surfaceVariant=ModernSurface,onSurfaceVariant=ModernMuted,outline=ModernInk,
        error=ModernRedText,surfaceContainer=ModernSurface),
        typography=ModernTypography,
        shapes=Shapes(androidx.compose.foundation.shape.RoundedCornerShape(0.dp),androidx.compose.foundation.shape.RoundedCornerShape(0.dp),androidx.compose.foundation.shape.RoundedCornerShape(0.dp),androidx.compose.foundation.shape.RoundedCornerShape(0.dp),androidx.compose.foundation.shape.RoundedCornerShape(0.dp)),content=content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun LegacySettingsScreen(app: InboxApplication, granted: Boolean, settings: () -> Unit,
    open: (CapturedNotification) -> Unit, preview: Boolean = false, initialTab:Int=1, startVoice:Boolean=false, onExit:()->Unit={}) {
    val stored by app.repository.recent.collectAsStateWithLifecycle(initialValue = emptyList())
    val storedCount by app.repository.count.collectAsStateWithLifecycle(initialValue = 0)
    val connected by app.listenerConnected.collectAsStateWithLifecycle()
    val captureError by app.captureError.collectAsStateWithLifecycle()
    val drafts by app.setupDrafts.drafts.collectAsStateWithLifecycle()
    val busy by app.setupDrafts.busy.collectAsStateWithLifecycle()
    val setupError by app.setupDrafts.error.collectAsStateWithLifecycle()
    val selectionState by app.selection.state.collectAsStateWithLifecycle()
    val selectionBusy by app.selection.busy.collectAsStateWithLifecycle()
    val selectionError by app.selection.error.collectAsStateWithLifecycle()
    val activePolicy = if (preview) null else selectionState.optJSONObject("policy")
    val rulebook = if (preview) null else selectionState.optJSONObject("rulebook")
    val proposal = if (preview) null else selectionState.optJSONObject("rule_proposal")
    var ruleEditor by rememberSaveable { mutableStateOf(false) }
    var editPrefill by rememberSaveable { mutableStateOf("") }
    val decisions = if (preview) JSONObject() else selectionState.getJSONObject("results")
    var selectionFilter by rememberSaveable { mutableStateOf("선별 알림") }
    var activateDraft by remember { mutableStateOf<JSONObject?>(null) }
    val rows = if (preview) previewNotifications() else stored
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftId by rememberSaveable { mutableStateOf<String?>(null) }
    var composing by rememberSaveable { mutableStateOf(false) }
    var recommendId by rememberSaveable { mutableStateOf<String?>(null) }
    var source by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewOnly by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selected = rows.find { it.snapshotId == selectedId }
    val draft = drafts.find { it.optString("id") == draftId }
    val inDetail = selectedId != null || draftId != null || composing || ruleEditor
    val back = { selectedId = null; draftId = null; composing = false; ruleEditor = false }
    var newPolicyRequest by rememberSaveable { mutableIntStateOf(if(startVoice)1 else 0) }
    var onboarding by rememberSaveable { mutableStateOf(false) }
    if (!preview && onboarding) {
        FirstOnboardingScreen(app, stored, granted, settings, {onboarding=false})
        return
    }
    BackHandler { if(inDetail)back() else onExit() }
    Scaffold(modifier = Modifier.semantics { testTagsAsResourceId = true }, containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(if (ruleEditor) "기준 추가·수정" else if (selectedId != null) "알림 상세" else if (composing) "새 알림 기준" else if (draftId != null) "기준 확인" else listOf("내 알림", "기준 관리", "설정")[tab], fontWeight = FontWeight.Bold)
                    if (!inDetail) Text(if (preview) "디자인 미리보기 · 예시 데이터" else "AI Notification Inbox", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }, navigationIcon = { IconButton(onClick = {if(inDetail)back() else onExit()}) { Icon(Icons.Outlined.ArrowBack, "뒤로") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        }, bottomBar = {
            if (!inDetail) Column {
                if(tab==1) Box(Modifier.fillMaxWidth().padding(vertical=8.dp),contentAlignment=Alignment.Center) {
                    NewPolicyButton(!app.policies.busy.collectAsStateWithLifecycle().value){newPolicyRequest++}
                }

            }
        }) { padding ->
        key(tab, selectedId, draftId, composing, draft?.result()?.toString()) {
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = if(tab == 1 && !inDetail) 20.dp else 0.dp, bottom = 96.dp)) {
            when {
                ruleEditor && rulebook != null -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (selectionError != null) Text(selectionError!!, color = MaterialTheme.colorScheme.error)
                        RuleEditPanel(rulebook, proposal, selectionBusy, editPrefill,
                            onRequest = { request, answer -> app.selection.requestRuleEdit(request, answer) },
                            onApply = { app.selection.applyRuleProposal() }, onDiscard = { app.selection.discardRuleProposal() })
                        if (proposal == null && !selectionBusy) Text("현재 적용 상태는 기준 목록에서 확인할 수 있습니다.")
                    }
                }
                selected != null -> {
                    item { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){SourceAppIcon(selected.packageName,selected.appLabel);TextButton(onClick={recommendId=selected.snapshotId}){Text(selected.appLabel)};SectionLabel(formatTime(selected.postedTime))} }
                    item { OutlinedButton(onClick={recommendId=selected.snapshotId},modifier=Modifier.testTag("notification_policy_settings")){Text("이 알림 기준 설정하기")} }
                    item { Text(selected.conversationTitle ?: selected.title ?: "알림", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                    decisions.optJSONObject(selected.snapshotId)?.let { decision -> item {
                        Notice(decision.selectionLabel(), decision.optString("reason") + "\n적용했던 기준: " + decision.optString("instruction").ifBlank{selectionState.optJSONObject("result_instructions")?.optString(decision.optString("policy_id")).orEmpty()})
                    } }
                    if (selected.needsOriginalReview()) item { Notice("긴 내용 · 원본 확인 필요", "알림에 전체 내용이 담기지 않았을 수 있습니다. 카카오톡에서 전체 내용을 확인해 주세요.") }
                    item { Button(onClick = { open(selected) }, Modifier.fillMaxWidth()) { Text("원래 앱에서 확인") } }
                    item { Surface(shape = MaterialTheme.shapes.large) { SelectionContainer { Text(selected.preview(), Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge) } } }
                    item { Text("이 화면은 수신한 알림을 보여줍니다. 연결이 만료되면 원래 앱을 열어 확인할 수 있습니다.", style = MaterialTheme.typography.bodySmall) }
                    item { var expanded by rememberSaveable(selected.snapshotId) { mutableStateOf(false) }
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "수집 정보 접기" else "수집 정보 보기") }
                        if (expanded) detailFields(selected).forEach { (name, value) -> Column(Modifier.padding(vertical = 6.dp)) { SectionLabel(name); SelectionContainer { Text(value, style = MaterialTheme.typography.bodySmall) } } }
                }
                }
                selectedId != null -> item { Notice("이 알림을 찾을 수 없어요", "최근 목록에서 벗어났거나 삭제된 기록입니다.") }
                composing -> item { NewDraftForm(rows, busy != null) { instruction, candidates -> draftId = app.setupDrafts.create(instruction, candidates); composing = false } }
                draft != null -> item { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (selectionError != null) Text(selectionError!!, color = MaterialTheme.colorScheme.error)
                    if (activePolicy?.optString("draft_id") == draft.getString("id")) {
                        Notice("현재 적용 중", activePolicy.getString("instruction"))
                        OutlinedButton(onClick = { app.selection.pause() }, Modifier.testTag("pause_rule")) { Text("선별 중지") }
                    } else if (draft.draftStatus() == "검토 완료 · 미적용") {
                        Button(onClick = { activateDraft = draft }, enabled = !selectionBusy && busy == null, modifier = Modifier.fillMaxWidth().testTag("activate_rule")) { Text(if (selectionBusy) "기준 확인 중" else "이 기준으로 선별 시작") }
                    }
                    if (draft.optBoolean("items_error")) OutlinedButton(onClick = { app.setupDrafts.organize(draft.getString("id")) }, enabled = busy == null) { Text("항목 정리 다시 시도") }
                    DraftDetail(draft, rows, busy != null || selectionBusy, busy == draftId, setupError,
                    onRequest = { action, answers, candidates -> app.setupDrafts.request(draft.getString("id"), action, answers, candidates) },
                    onDelete = { app.selection.removeDraft(draft.getString("id")); app.setupDrafts.remove(draft.getString("id")); draftId = null }, applied = activePolicy?.optString("draft_id") == draft.getString("id")) } }
                draftId != null -> item { Notice("저장된 기준이 없어요", "목록으로 돌아가 다시 선택해 주세요.") }
                tab == 0 -> {
                    item { Notice(if (!granted) "알림 접근을 허용해 주세요" else if (connected || preview) "알림 수집 연결됨" else "알림 수집 연결 대기 중",
                        if (activePolicy == null) "알림 옆 ⋯에서 필요한 것만 하나씩 골라 보세요." else "새 알림에 기준을 적용 중입니다. 애매한 알림은 확인 필요로 남깁니다.", compact = true) }
                    if (!granted) item { Button(onClick = settings) { Text("알림 접근 허용") } }
                    if (captureError != null && !preview) item { Text(captureError!!, color = MaterialTheme.colorScheme.error) }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Timeline", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("최근 ${rows.size}개", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                    item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = source == null, onClick = { source = null }, label = { Text("전체 앱") })
                        rows.distinctBy { it.packageName }.forEach { row -> FilterChip(selected = source == row.packageName, onClick = { source = row.packageName }, label = { Text(row.appLabel) }) }
                    } }
                    item { Row(Modifier.clickable { reviewOnly = !reviewOnly }, verticalAlignment = Alignment.CenterVertically) { Checkbox(reviewOnly, { reviewOnly = it }); Text("긴 내용 확인이 필요한 알림만", style = MaterialTheme.typography.bodyMedium) } }
                    item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("선별 알림", "기준 밖", "전체").forEach { label -> FilterChip(selected = selectionFilter == label,
                            onClick = { selectionFilter = label }, label = { Text(label) }, modifier = Modifier.testTag("selection_$label")) }
                    } }
                    if (selectionError != null) item { Text(selectionError!!, color = MaterialTheme.colorScheme.error) }
                    val visible = rows.filter { row ->
                        val decision = decisions.optJSONObject(row.snapshotId)
                        when (selectionFilter) {
                            "기준 밖" -> decision?.optString("status") == "outside"
                            "선별 알림" -> if (activePolicy == null) decision?.optString("status") != "outside" else
                                row.postedTime >= activePolicy.getLong("activated_at") && decision?.optString("status") != "outside"
                            else -> true
                        }
                    }.filter { (source == null || it.packageName == source) && (!reviewOnly || it.needsOriginalReview()) }
                    if (visible.isEmpty()) item { Notice("아직 표시할 알림이 없어요", if (reviewOnly || source != null) "필터를 바꾸거나 새 알림을 기다려 주세요." else "다른 앱에서 새 알림을 받으면 여기에 나타납니다.") }
                    items(visible, key = { it.snapshotId }) { row -> NotificationRow(row, decisions.optJSONObject(row.snapshotId)?.selectionLabel(),onAppSettings={recommendId=row.snapshotId},onRecommendations={recommendId=row.snapshotId}) { selectedId = row.snapshotId } }
                }
                tab == 1 -> {
                    item { PolicySettingsPanel(app, stored,newRequest=newPolicyRequest,inlineAddButton=false,onNewRequestHandled={newPolicyRequest=0}) }
                    if(drafts.isNotEmpty()) item { SectionLabel("이전 입력 기록·미완성 초안") }
                    if(!preview) items(drafts,key={it.getString("id")}) { old ->
                        Surface(shape=MaterialTheme.shapes.medium) { Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(old.getString("instruction"))
                            TextButton(onClick={app.policies.request(old.getJSONObject("session").optString("confirmed_instruction").takeIf{it.isNotBlank()&&it!="null"}?:old.getString("instruction"))}) { Text("새 정책으로 이어서 정리") }
                        } }
                    }
                }
                else -> {
                    item { OutlinedButton(onClick={onboarding=true},Modifier.fillMaxWidth().testTag("open_onboarding")){Text("처음 설정 안내 다시 보기")} }
                    item { ReplySettingsRow(app.replyPreferences) }
                    item { SectionLabel("수집과 보관") }
                    item { Notice(if (granted) "알림 접근 허용됨" else "알림 접근 꺼짐", "알림 제목과 본문은 이 기기에 저장합니다. 시스템 UI 알림은 목록에서 제외합니다.") }
                    item { OutlinedButton(onClick = settings, Modifier.fillMaxWidth()) { Text("알림 접근 설정") } }
                    item { Notice("저장된 알림 ${if (preview) rows.size else storedCount}개", "최근 500개를 목록에 표시합니다. 원래 앱의 알림은 삭제하지 않습니다.") }
                    item { OutlinedButton(onClick = { if (!preview) confirmClear = true }, Modifier.fillMaxWidth()) { Text("저장 기록 전체 삭제") } }
                    item { SectionLabel("기준 정리 테스트") }
                    item { Text("인터넷이 연결되면 휴대폰에서 직접 기준을 정리합니다. 입력한 지시와 답변을 OpenAI에 전달하며, 최근 알림 후보는 선택한 경우에만 전달합니다.") }
                    item { Text("추천을 열면 선택한 알림 내용을 TypeSafe에 보내 선택지를 준비합니다. 자동 선별은 켠 이후 새 알림의 앱·제목·본문·발신자를 TypeSafe에 보내 판단합니다. 새 알림의 의미 분류에도 TypeSafe를 사용합니다. 기록은 이 기기에 보관하고 앱 자체의 일일 판정 제한은 없습니다. AI Watch는 준비 중입니다.", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    }
    rows.find{it.snapshotId==recommendId}?.let{row->ContextRecommendationSheet(row,rows,app.policies,{app.recommendations.classify(it)}){recommendId=null}}
    activateDraft?.let { pending -> AlertDialog(modifier = Modifier.semantics { testTagsAsResourceId = true }, onDismissRequest = { activateDraft = null }, title = { Text("이 기준으로 선별할까요?") },
        text = { Text(pending.getJSONObject("session").getString("confirmed_instruction") + "\n\n지금부터 새 알림의 앱·제목·본문·발신자를 TypeSafe에 보내 판단합니다. 앱 자체의 일일 판정 제한 없이 실행하며 긴 내용·연결 오류는 확인 필요로 남습니다. 원래 앱 알림은 유지합니다." + if (activePolicy != null) "\n기존 기준을 이 기준으로 교체합니다." else "") },
        confirmButton = { TextButton(onClick = { activateDraft = null; app.selection.activate(pending) }, Modifier.testTag("confirm_activation")) { Text("선별 시작") } },
        dismissButton = { TextButton(onClick = { activateDraft = null }) { Text("취소") } }) }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("저장 기록을 삭제할까요?") }, text = { Text("이 앱의 알림 기록만 삭제합니다. 기준 초안에 포함했던 후보는 해당 초안을 삭제해야 지워집니다.") }, confirmButton = { TextButton(onClick = { confirmClear = false; scope.launch { try { app.repository.clear(); app.images.clear(); app.selection.clearResults(); app.opener.clear() } catch (_: Exception) { app.captureError.value = "기록 삭제에 실패했습니다." } } }) { Text("삭제") } }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } })
}

@Composable private fun SectionLabel(text: String) { Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun Notice(title: String, body: String, compact: Boolean = false) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(if (compact) 14.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun NotificationRow(row: CapturedNotification, decision: String? = null, onAppSettings: () -> Unit = {}, onRecommendations: () -> Unit = {}, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.weight(1f).testTag("notification_app_${row.snapshotId}").clickable(onClick=onAppSettings).padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){SourceAppIcon(row.packageName,row.appLabel);Text(row.appLabel, style = MaterialTheme.typography.labelMedium, color = Blue)}
                Text(SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()).format(Date(row.postedTime)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick=onRecommendations,modifier=Modifier.size(40.dp).testTag("notification_more_${row.snapshotId}")){Icon(Icons.Outlined.MoreVert,"이 알림 기준 설정")}
            }
            Text(row.conversationTitle ?: row.title ?: "알림", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(row.preview(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (decision != null) SectionLabel(decision)
            if (row.needsOriginalReview()) Text("긴 내용 · 원본 확인 필요", style = MaterialTheme.typography.labelMedium, color = Blue)
            if (row.isGroupSummary) SectionLabel("앱에서 제공한 묶음 요약")
        }
    }
}

@Composable private fun NewDraftForm(rows: List<CapturedNotification>, busy: Boolean, submit: (String, JSONArray) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var include by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("어떤 알림을 받고 싶으세요?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("부족한 조건이 있으면 필요한 부분만 다시 물어볼게요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(input, { if (it.length <= 8000) input = it }, Modifier.fillMaxWidth().testTag("rule_input"), label = { Text("원하는 알림 기준") }, placeholder = { Text("예: 회사 단톡에서 내 답변이 필요할 때 알려줘") }, minLines = 4)
        Row(Modifier.clickable { include = !include }, verticalAlignment = Alignment.CenterVertically) { Checkbox(include, { include = it }); Text("최근 알림을 대화 선택 후보로 포함") }
        Text("지시와 답변을 OpenAI에 전달합니다. 후보를 포함하면 최근 20개 알림의 앱·제목·본문 앞 200자도 전달합니다. 대화 전체나 참여자 명단은 알 수 없습니다.", style = MaterialTheme.typography.bodySmall)
        if (include) CandidatePreview(notificationCandidates(rows))
        Button(onClick = { submit(input.trim(), if (include) notificationCandidates(rows) else JSONArray()) }, enabled = input.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().testTag("submit_rule")) { Text("저장하고 기준 정리") }
        Text("완성 전에는 기준이 적용되지 않습니다.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun CandidatePreview(candidates: JSONArray) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("전달할 후보 ${candidates.length()}개 · 수신 알림 기준")
        for (i in 0 until candidates.length()) { val c = candidates.getJSONObject(i)
            Text(c.getString("label"), style = MaterialTheme.typography.labelMedium)
            Text(c.getString("preview"), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun DraftDetail(draft: JSONObject, rows: List<CapturedNotification>, anyBusy: Boolean, busy: Boolean, error: String?,
    onRequest: (String, JSONArray, JSONArray?) -> Unit, onDelete: () -> Unit, applied: Boolean = false) {
    val result = draft.result()
    val questions = result?.optJSONArray("questions") ?: JSONArray()
    var responseText by rememberSaveable(draft.getString("id"), result?.toString()) { mutableStateOf("{}") }
    val responses = JSONObject(responseText)
    var refresh by rememberSaveable { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionLabel(if (busy) "기준을 정리하고 있어요" else if (applied) "현재 적용 중" else draft.draftStatus())
        Text(draft.getString("instruction"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("잠시 나갔다 와도 초안은 남아 있습니다.", style = MaterialTheme.typography.bodySmall) }
        val failure = draft.optString("error").ifBlank { error }
        if (!failure.isNullOrBlank()) Notice("연결을 확인해 주세요", failure)
        result?.let {
            Text(it.optString("message"), style = MaterialTheme.typography.bodyMedium)
            val unresolved = it.optJSONArray("unresolved") ?: JSONArray()
            for (i in 0 until unresolved.length()) Text(unresolved.getString(i), style = MaterialTheme.typography.bodySmall)
        }
        for (i in 0 until questions.length()) {
            val q = questions.getJSONObject(i)
            val id = q.getString("id")
            val response = responses.optJSONObject(id) ?: JSONObject().put("id", id)
            val selected = response.optJSONArray("options") ?: JSONArray()
            val selectedIds = (0 until selected.length()).map { selected.getString(it) }
            Text(q.getString("prompt"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(q.getString("reason"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val options = q.getJSONArray("options")
            for (j in 0 until options.length()) {
                val option = options.getJSONObject(j)
                val oid = option.getString("id")
                val checked = oid in selectedIds
                Surface(shape = MaterialTheme.shapes.medium, color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().testTag("option_${i}_$j").clickable(enabled = !anyBusy) {
                        val updated = if (q.getString("kind") == "single") listOf(oid) else if (checked) selectedIds - oid else selectedIds + oid
                        responses.put(id, JSONObject().put("id", id).put("options", JSONArray(updated)))
                        responseText = responses.toString()
                    }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (q.getString("kind") == "single") RadioButton(checked, null) else Checkbox(checked, null)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(option.getString("label"), style = MaterialTheme.typography.bodyMedium)
                            val candidates = draft.optJSONObject("session")?.optJSONArray("candidates") ?: JSONArray()
                            val candidate = (0 until candidates.length()).map { candidates.getJSONObject(it) }.find { it.optString("id") == option.optString("candidate_id") }
                            if (candidate != null) Text(candidate.optString("preview"), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            OutlinedTextField(response.optString("text", ""), { text ->
                if (text.length <= 2000) { responses.put(id, JSONObject().put("id", id).put("text", text)); responseText = responses.toString() }
            }, Modifier.fillMaxWidth(), enabled = !anyBusy, label = { Text(if (options.length() == 0) "답변" else "또는 직접 입력") })
        }
        if (questions.length() > 0) {
            val complete = (0 until questions.length()).all { i ->
                val answer = responses.optJSONObject(questions.getJSONObject(i).getString("id"))
                answer != null && (answer.optString("text").isNotBlank() || (answer.optJSONArray("options")?.length() ?: 0) > 0)
            }
            Button(onClick = {
                val answers = JSONArray()
                for (i in 0 until questions.length()) answers.put(responses.getJSONObject(questions.getJSONObject(i).getString("id")))
                onRequest("answer", answers, null)
            }, enabled = complete && !anyBusy, modifier = Modifier.fillMaxWidth().testTag("submit_answers")) { Text("답변하고 다시 정리") }
        }
        if (result?.optString("status") == "ready") {
            if (draft.has("items")) {
                Text("정리된 항목", style = MaterialTheme.typography.titleMedium)
                ruleObjects(draft.getJSONArray("items")).forEachIndexed { index, item -> Notice("${index + 1}. 선별 항목", item.getString("text")) }
            } else Notice("정리된 기준", result.optString("normalized_instruction"))
            Text("원래 요청과 다르게 정리된 부분이 없는지 확인해 주세요. 저장한 뒤 ‘이 기준으로 선별 시작’을 눌러 적용할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
            if (draft.draftStatus() != "검토 완료 · 미적용") Button(onClick = { onRequest("confirm", JSONArray(), null) }, enabled = !anyBusy, modifier = Modifier.fillMaxWidth().testTag("confirm_rule")) { Text("검토 완료로 저장") }
        }
        if ((result == null || draft.has("pending")) && !busy) Button(onClick = { onRequest("retry", JSONArray(), null) }, enabled = !anyBusy) { Text("다시 정리") }
        if (result?.optString("status") == "waiting") {
            OutlinedButton(onClick = { refresh = !refresh }, enabled = !anyBusy) { Text("최근 알림 후보 확인") }
            if (refresh) {
                val candidates = notificationCandidates(rows)
                CandidatePreview(candidates)
                Text("표시된 앱·제목·본문 일부를 OpenAI에 보내 질문을 이어갑니다. 현재 알림을 대화 후보로 사용하는 테스트이며 새 알림의 대화 식별 정보가 있는 경우 해당 방에 선별 기준을 적용할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onRequest("refresh", JSONArray(), candidates); refresh = false }, enabled = !anyBusy && candidates.length() > 0) { Text("이 후보로 다시 확인") }
            }
        }
        HorizontalDivider()
        TextButton(onClick = { delete = true }, enabled = !anyBusy) { Text("이 초안 삭제") }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("초안을 삭제할까요?") }, text = { Text("이 초안의 지시, 답변, 포함된 알림 후보를 함께 삭제합니다.") }, confirmButton = { TextButton(onClick = { delete = false; onDelete() }) { Text("삭제") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("취소") } })
}

/** Synthetic-only design fixture, never stored or classified. */
internal fun previewNotifications(): List<CapturedNotification> {
    val now = System.currentTimeMillis()
    fun row(id: Int, app: String, title: String, text: String, pkg: String) = CapturedNotification(
        "preview-$id", pkg, app, "preview-$id", id, now - id * 600000L, now, title, text, null, null, null,
        "[]", null, null, false, false, "")
    return listOf(
        row(1, "카카오톡", "제품팀 프로젝트", "내일 회의는 오후 3시로 변경됐어요. 참석 가능하신지 알려주세요.", "com.kakao.talk"),
        row(2, "Gmail", "9월 카드 이용대금 안내", "이번 달 청구 내역이 도착했습니다. 자세한 내역은 앱에서 확인해 주세요.", "com.google.android.gm"),
        row(3, "Slack", "디자인 검토", "새 시안 검토가 완료되었습니다. 변경 내용을 확인하고 승인해 주세요.", "com.Slack"),
        row(4, "카카오톡", "가족", "이번 주말 여행 자료를 보냈어요. 숙소와 이동 일정을 확인해 주세요. ".repeat(20), "com.kakao.talk")
    )
}
