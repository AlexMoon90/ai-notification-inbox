package com.ainotification.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONArray
import org.json.JSONObject

@Composable internal fun FirstOnboardingScreen(app:InboxApplication,rows:List<CapturedNotification>,granted:Boolean,settings:()->Unit,onExit:(()->Unit)?=null,c:PolicyController=app.policies) {
    val state by c.state.collectAsStateWithLifecycle();val busy by c.busy.collectAsStateWithLifecycle();val error by c.error.collectAsStateWithLifecycle()
    var form by remember{mutableStateOf(JSONObject(state.optJSONObject("initial_onboarding")?.toString() ?: "{}"))}
    val step=form.optInt("step",0).coerceIn(0,3)
    var confirmQuiet by remember(step){mutableStateOf(false)}
    var submitted by remember{mutableStateOf(false)}
    fun selected(key:String)=jsonStrings(form.optJSONArray(key)?:JSONArray()).toSet()
    fun update(key:String,value:Any){form=JSONObject(form.toString()).put(key,value);c.storeInitialForm(form);if(key!="step")c.discard()}
    fun toggle(key:String,id:String){val values=selected(key).toMutableSet();if(!values.remove(id))values.add(id);update(key,JSONArray(values.toList()))}
    LaunchedEffect(state.optBoolean("onboarding_complete"),busy,submitted){if(submitted && !busy && error==null && state.optBoolean("onboarding_complete") && state.optJSONObject("policy_proposal")==null)onExit?.invoke()}
    BackHandler(step>0 || onExit!=null){if(step>0){c.discard();update("step",step-1)}else onExit?.invoke()}
    Column(Modifier.fillMaxSize().safeDrawingPadding().semantics{testTagsAsResourceId=true}.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("처음에는 방향만 정해주세요",style=MaterialTheme.typography.headlineSmall)
        Text("${step+1} / 4 · "+listOf("알림 접근","꼭 알려주세요","이런 건 조용히 해주세요","사용 시작")[step])
        LinearProgressIndicator(progress={(step+1)/4f},modifier=Modifier.fillMaxWidth())
        when(step){
            0->{
                Text("알림을 모아서 정리할 수 있도록 허용해주세요",style=MaterialTheme.typography.titleLarge)
                Text("휴대폰에 제공된 알림만 읽습니다. 앱 내부의 대화나 과거 메시지를 직접 읽지는 않습니다.")
                Text("필요한 알림 내용은 외부 AI 서비스로 전달되어 분석될 수 있습니다. 원본은 휴대폰에 보관합니다.")
                Button(onClick=settings,modifier=Modifier.fillMaxWidth().testTag("onboarding_access")){Text(if(granted)"알림 접근 허용됨 · 설정 보기" else "알림 접근 허용")}
            }
            1,2->{
                val must=step==1;val choices=if(must)initialMust else initialLess;val field=if(must)"must" else "less"
                Text(if(must)"꼭 알려주세요" else "이런 건 조용히 해주세요",style=MaterialTheme.typography.titleLarge)
                Text(if(must)"놓치고 싶지 않은 정보를 선택해주세요." else "자주 오지만 바로 확인하지 않아도 되는 정보를 선택해주세요.")
                choices.forEach{choice->Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                    Checkbox(choice.id in selected(field),{toggle(field,choice.id)},enabled=!busy,modifier=Modifier.testTag("initial_${field}_${choice.id}"))
                    Text(choice.label)
                }}
                if(!must)Text("여기서 ‘조용히’는 Now에서 숨긴다는 뜻입니다. 원본은 남고 메시지·스마트함에서 확인할 수 있어요. 휴대폰 소리나 팝업은 바뀌지 않습니다.",style=MaterialTheme.typography.bodySmall)
            }
            3->{
                Text("이 방향으로 시작할게요",style=MaterialTheme.typography.titleLarge)
                Text("적용 범위 · 모든 앱의 기본 방향. 이후 알림에서 만든 구체적인 기준이 우선합니다.")
                Text("꼭 알려주세요 · "+initialMust.filter{it.id in selected("must")}.joinToString{it.label}.ifEmpty{"선택 없음"})
                Text("Now에서 숨기기 · "+initialLess.filter{it.id in selected("less")}.joinToString{it.label}.ifEmpty{"선택 없음"})
                Text("이번 초기 선택에서는 꼭 알려달라고 선택한 내용이 숨김 항목에 해당해도 우선 보여드립니다. 선택하지 않은 내용을 자동으로 숨기지 않습니다.")
                if(selected("must").isEmpty() && selected("less").isEmpty())Text("선택 없이 시작하면 새 기준을 만들기 전까지 Now 판정은 하지 않고 원본을 모읍니다.")
                if(selected("less").isNotEmpty())Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(confirmQuiet,{confirmQuiet=it},modifier=Modifier.testTag("risk_confirm"));Text("선택한 내용이 Now에서 숨겨지는 것을 확인했어요.")}
                val ready=state.optJSONObject("policy_proposal")?.let{it.optString("origin")=="initial_preferences" && it.optBoolean("validated")}==true
                if(!ready && !busy)Button(onClick={c.prepareInitialPreferences(selected("must"),selected("less"))},modifier=Modifier.testTag("onboarding_generate")){Text("선택 내용 확인")}
                Button(onClick={submitted=true;c.apply(onboarding=true)},enabled=granted && ready && !busy && (selected("less").isEmpty() || confirmQuiet),modifier=Modifier.fillMaxWidth().testTag("confirm_policy")){Text("이 방향으로 시작하기")}
            }
        }
        Text("처음부터 완벽하게 설정하지 않아도 됩니다. 사용하시면서 필요한 알림이나 불필요한 알림이 보이면 그 자리에서 바로 조정할 수 있습니다.",style=MaterialTheme.typography.bodyMedium)
        if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(step<3)Button(onClick={if(step==2)c.prepareInitialPreferences(selected("must"),selected("less"));update("step",step+1)},enabled=!busy && (step!=0 || granted),modifier=Modifier.fillMaxWidth().testTag("onboarding_next")){Text(if(step==2)"선택 확인" else "다음")}
        if(step>0)TextButton(onClick={c.discard();update("step",step-1)},enabled=!busy){Text("이전 · 수정하기")}
        if(onExit!=null)TextButton(onClick={c.deferInitialSetup();onExit()},enabled=!busy){Text("나중에 정하고 둘러보기")}
    }
}
