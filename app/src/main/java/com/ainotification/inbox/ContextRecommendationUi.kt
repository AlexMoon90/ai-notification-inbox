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
    val roomKnown=!row.isGroupSummary && !row.conversationIdentity.isNullOrBlank()
    val sender=row.latestMessage()?.stringOrNull("sender")
    var hideAction by rememberSaveable(row.snapshotId){mutableStateOf(RecommendationAction.HIDE_APP.name)}
    var hideEvent by rememberSaveable(row.snapshotId){mutableStateOf("UNKNOWN")}
    var hideLabel by rememberSaveable(row.snapshotId){mutableStateOf("")}
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
    fun chooseHide(action:RecommendationAction,label:String,topic:String="UNKNOWN"){
        hideAction=action.name;hideLabel=label;hideEvent=topic;selected=emptySet();phase="exceptions"
    }
    fun custom(withSelection:Boolean=false){
        input=if(withSelection)hideLabel+". "+if(selected.isEmpty())"단, 꼭 받을 알림은: " else "단, "+selected.joinToString{selectableNotificationTypes[it].orEmpty()}+"은 보여줘. 추가로 " else ""
        autoVoice=true;phase="voice"
    }
    if(phase=="voice") {
        PolicyVoiceEditor(input,{input=it},busy,autoVoice,{autoVoice=false},{phase="recommend"}, {
            baseline=state.optJSONObject("policy")?.optString("id").orEmpty()
            c.requestContext(recommendationTarget(row),input);phase="review"
        })
        return
    }
    ModalBottomSheet(onDismissRequest=dismiss,shape=androidx.compose.ui.graphics.RectangleShape,containerColor=ModernBg,dragHandle={ModernLine(true)},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),modifier=Modifier.semantics{testTagsAsResourceId=true}.testTag("context_recommendations")) {
        Column(Modifier.fillMaxWidth().heightIn(max=650.dp).verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(when(phase){"review"->"변경 확인";"exceptions"->"그래도 꼭 받을 알림이 있나요?";"topics"->"어떤 내용을 숨길까요?";else->"이 알림을 어떻게 볼까요?"},style=MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){SourceAppIcon(row.packageName,row.appLabel);Text(row.appLabel)}
            when(phase) {
                "recommend"->{
                    Text("계속 알려주세요",style=MaterialTheme.typography.titleMedium)
                    fun keep(action:RecommendationAction,label:String,topic:String="UNKNOWN") {
                        baseline=state.optJSONObject("policy")?.optString("id").orEmpty()
                        c.recommend(row,RuleRecommendation(action,label,topic));phase="review"
                    }
                    if(roomKnown)OutlinedButton(onClick={keep(RecommendationAction.ALWAYS_SHOW_CONVERSATION,"이 대화방은 계속 알려주기")},modifier=Modifier.fillMaxWidth().testTag("keep_room")){Text("이 대화방은 계속 알려주기")}
                    if(!sender.isNullOrBlank())OutlinedButton(onClick={keep(RecommendationAction.ALWAYS_SHOW_SENDER,"이 앱의 $sender 메시지는 계속 알려주기")},modifier=Modifier.fillMaxWidth().testTag("keep_sender")){Text("$sender 발신자는 계속 알려주기")}
                    if(event in recommendationEvents)OutlinedButton(onClick={keep(if(roomKnown)RecommendationAction.SHOW_ROOM_TOPIC else RecommendationAction.SHOW_SIMILAR,
                        (if(roomKnown)"이 방의 " else "이 앱의 ")+recommendationEvents.getValue(event)+" 계속 알려주기",event)},modifier=Modifier.fillMaxWidth().testTag("keep_topic")){Text("이런 내용 · ${recommendationEvents.getValue(event)} 계속 알려주기")}
                    else Text(if(loading)"이 알림의 내용을 확인하고 있어요." else "내용의 종류가 확실하지 않으면 직접 말씀해 주세요.",style=MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text("앞으로 조용히 해주세요",style=MaterialTheme.typography.titleMedium)
                    Text("Now에서 숨기며 원본은 남깁니다. 휴대폰 소리·팝업 설정은 바뀌지 않습니다.",style=MaterialTheme.typography.bodySmall)

                    if(roomKnown){
                        Text(row.conversationTitle?.takeIf{it.isNotBlank()} ?: if(row.hasGroupConversation())"단톡방" else "선택한 대화방",style=MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick={chooseHide(RecommendationAction.HIDE_CONVERSATION,"이 대화방 전체 숨기기")},modifier=Modifier.fillMaxWidth().testTag("hide_room")){Text("이 대화방 전체")}
                        if(!sender.isNullOrBlank())OutlinedButton(onClick={chooseHide(RecommendationAction.HIDE_ROOM_SENDER,"이 대화방에서 $sender 메시지만 숨기기")},modifier=Modifier.fillMaxWidth().testTag("hide_room_sender")){Text("이 방의 $sender 메시지")}
                        OutlinedButton(onClick={phase="topics"},modifier=Modifier.fillMaxWidth().testTag("hide_topics")){Text("이 방의 특정 내용 · 잡담, 광고")}
                    }else{
                        OutlinedButton(onClick={chooseHide(RecommendationAction.HIDE_APP,"${row.appLabel} 알림 모두 숨기기")},modifier=Modifier.fillMaxWidth().testTag("hide_app")){Text("이 앱의 모든 알림")}
                        OutlinedButton(onClick={phase="topics"},modifier=Modifier.fillMaxWidth().testTag("hide_topics")){Text("특정 종류의 알림 · 광고 등")}
                    }
                    TextButton(onClick={custom()},modifier=Modifier.testTag("recommend_CUSTOM_RULE")){Text("직접 말하기")}
                    if(roomKnown){HorizontalDivider();TextButton(onClick={chooseHide(RecommendationAction.HIDE_APP,"${row.appLabel} 알림 모두 숨기기")},modifier=Modifier.testTag("hide_app")){Text("이 앱 전체를 숨기고 싶어요",color=ModernMuted)}}
                    TextButton(onClick=dismiss){Text("닫기")}
                }
                "topics"->{
                    val topics=linkedMapOf("PROMOTION" to "광고").apply{if(roomKnown)put("CASUAL_CHAT","잡담");if(event in recommendationEvents)put(event,recommendationEvents.getValue(event))}
                    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                    topics.forEach{(type,label)->OutlinedButton(onClick={chooseHide(if(roomKnown)RecommendationAction.HIDE_ROOM_TOPIC else RecommendationAction.HIDE_SIMILAR,if(roomKnown)"이 방의 $label 숨기기" else "${row.appLabel}의 $label 숨기기",type)},modifier=Modifier.fillMaxWidth().testTag("hide_topic_$type")){Text(label)}}
                    TextButton(onClick={custom()}){Text("다른 내용 직접 말하기")}
                    TextButton(onClick={phase="recommend"}){Text("뒤로")}
                }
                "exceptions"->{
                    Text(hideLabel,style=MaterialTheme.typography.bodyMedium)
                    Text("선택한 대상 안에서만 예외로 보여드려요.",style=MaterialTheme.typography.bodySmall)
                    val types=if(roomKnown && hideAction!=RecommendationAction.HIDE_APP.name)listOf("REPLY_REQUIRED","MEETING_CONFIRMED","SCHEDULE_CHANGE") else listOf("DELIVERY","ORDER_UPDATES","PAYMENT_REQUIRED","SECURITY")
                    types.forEach{type->Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                        Checkbox(type in selected,{checked->selected=if(checked)selected+type else selected-type},modifier=Modifier.testTag("keep_$type"));Text(selectableNotificationTypes.getValue(type))
                    }}
                    TextButton(onClick={custom(true)},modifier=Modifier.testTag("keep_custom")){Text(if(roomKnown)"특정 사람·다른 예외 직접 말하기" else "관심 상품·다른 예외 직접 말하기")}
                    ModernButton(if(selected.isEmpty())"예외 없이 숨기기 · 내용 확인" else "선택한 예외로 내용 확인",{
                        baseline=state.optJSONObject("policy")?.optString("id").orEmpty()
                        c.recommend(row,RuleRecommendation(RecommendationAction.valueOf(hideAction),hideLabel,hideEvent),selected);phase="review"
                    },Modifier.testTag("recommend_preview"),!busy)
                    TextButton(onClick={phase="recommend"}){Text("뒤로")}
                }
                "review"->{
                    PolicyProposalPanel(c,rows,compact=true)
                    if(!busy)TextButton(onClick={c.discard();phase="voice";autoVoice=false},modifier=Modifier.testTag("recommend_modify")){Text("직접 수정하기")}
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
