package com.ainotification.inbox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ContextRecommendationSheet(row:CapturedNotification,rows:List<CapturedNotification>,c:PolicyController,classify:suspend (CapturedNotification)->String,dismiss:()->Unit) {
    val state by c.state.collectAsStateWithLifecycle();val busy by c.busy.collectAsStateWithLifecycle();val error by c.error.collectAsStateWithLifecycle()
    val profileKey=remember(row.snapshotId){runCatching{conversationProfileKey(row)}.getOrNull()}
    var phase by rememberSaveable(row.snapshotId){mutableStateOf(if(state.optJSONObject("policy_proposal")!=null)"review" else "recommend")}
    var event by rememberSaveable(row.snapshotId){mutableStateOf("UNKNOWN")}
    var loading by remember {mutableStateOf(true)}
    var selected by remember {mutableStateOf(setOf<String>())}
    var input by rememberSaveable(row.snapshotId){mutableStateOf("")}
    var autoVoice by remember {mutableStateOf(false)}
    var baseline by rememberSaveable {mutableStateOf(state.optJSONObject("policy")?.optString("id").orEmpty())}
    LaunchedEffect(row.snapshotId) {
        if(phase in listOf("recommend","profile"))event=runCatching {withContext(Dispatchers.IO){classify(row)}}.getOrDefault("UNKNOWN")
        loading=false
    }
    fun choose(choice:RuleRecommendation) {
        when(choice.action) {
            RecommendationAction.CUSTOM_RULE->{phase="voice";autoVoice=true}
            RecommendationAction.SHOW_ONLY_SELECTED_TYPES->phase="types"
            else->{baseline=state.optJSONObject("policy")?.optString("id").orEmpty();c.recommend(row,choice);phase="review"}
        }
    }
    if(phase=="voice") {
        PolicyVoiceEditor(input,{input=it},busy,autoVoice,{autoVoice=false},{phase="recommend"}, {
            baseline=state.optJSONObject("policy")?.optString("id").orEmpty()
            c.request(contextualPolicyRequest(recommendationTarget(row),input));phase="review"
        })
        return
    }
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),modifier=Modifier.semantics{testTagsAsResourceId=true}.testTag("context_recommendations")) {
        Column(Modifier.fillMaxWidth().heightIn(max=650.dp).verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("앞으로 이런 알림을 어떻게 할까요?",style=MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){SourceAppIcon(row.packageName,row.appLabel);Text(row.appLabel)}
            when(phase) {
                "profile"->{
                    Text("이 대화는 어떤 종류인가요?",style=MaterialTheme.typography.titleMedium)
                    conversationKinds.forEach { (kind,label)->OutlinedButton(onClick={c.setConversationProfile(row,kind);phase="recommend"},modifier=Modifier.fillMaxWidth().testTag("conversation_kind_$kind")){Text(label)} }
                    TextButton(onClick={phase="recommend"}){Text("나중에")}
                }
                "recommend"->{
                    if(loading){LinearProgressIndicator(Modifier.fillMaxWidth());Text("선택지를 준비하고 있어요")}
                    else recommendedActions(row,event,state.optJSONObject("context_preferences") ?: org.json.JSONObject()).forEach {choice->
                        OutlinedButton(onClick={choose(choice)},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("recommend_${choice.action.name}")){Text(choice.label)}
                    }
                    HorizontalDivider()
                    TextButton(onClick={choose(RuleRecommendation(RecommendationAction.HIDE_APP,"${row.appLabel} 알림 모두 숨기기"))},enabled=!busy&&!loading,modifier=Modifier.testTag("recommend_HIDE_APP"),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("이 앱 알림 모두 숨기기")}
                    TextButton(onClick=dismiss,modifier=Modifier.testTag("recommend_KEEP_AS_IS")){Text("그대로 두기")}
                }
                "types"->{
                    Text("${row.appLabel}에서 볼 내용을 골라 주세요",style=MaterialTheme.typography.titleMedium)
                    Text("선택하지 않은 알림은 이 앱에서 숨겨집니다.",style=MaterialTheme.typography.bodySmall)
                    selectableNotificationTypes.forEach { (type,label)->Row {
                        Checkbox(type in selected,{checked->selected=if(checked)selected+type else selected-type},modifier=Modifier.testTag("recommend_type_$type"));Text(label,Modifier.padding(top=12.dp))
                    }}
                    Button(onClick={baseline=state.optJSONObject("policy")?.optString("id").orEmpty();c.recommend(row,RuleRecommendation(RecommendationAction.SHOW_ONLY_SELECTED_TYPES,"${row.appLabel}에서 선택한 내용만 보기"),selected);phase="review"},enabled=selected.isNotEmpty()&&!busy,modifier=Modifier.fillMaxWidth().testTag("recommend_preview")){Text("변경 내용 확인")}
                    TextButton(onClick={phase="recommend"}){Text("뒤로")}
                }
                "review"->{
                    PolicyProposalPanel(c,rows)
                    if(state.optJSONObject("policy_proposal")!=null && !busy)TextButton(onClick={c.discard();phase="voice";autoVoice=false},modifier=Modifier.testTag("recommend_modify")){Text("직접 수정하기")}
                    if(!busy && state.optJSONObject("policy_proposal")==null) {
                        Text(if(state.optJSONObject("policy")?.optString("id").orEmpty()!=baseline)"적용했어요. 다음 알림부터 반영돼요." else if(error!=null)"다시 선택해 주세요." else "변경하지 않았어요.")
                        TextButton(onClick={phase="recommend"}){Text("선택지 다시 보기")}
                        Button(onClick=dismiss,modifier=Modifier.testTag("recommend_done")){Text("닫기")}
                    }
                }
            }
        }
    }
}
