package com.ainotification.inbox

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONArray
import org.json.JSONObject

@Composable internal fun FirstOnboardingScreen(app:InboxApplication,rows:List<CapturedNotification>,granted:Boolean,settings:()->Unit,onExit:(()->Unit)?=null,c:PolicyController=app.policies) {
    val state by c.state.collectAsStateWithLifecycle();val busy by c.busy.collectAsStateWithLifecycle()
    var form by remember{mutableStateOf(JSONObject(state.optJSONObject("onboarding")?.toString() ?: "{}"))}
    val step=form.optInt("step",0);val context=LocalContext.current
    fun update(key:String,value:Any){val next=JSONObject(form.toString()).put(key,value);form=next;c.storeOnboarding(next);if(key!="step")c.discard()}
    fun selected(key:String)=jsonStrings(form.optJSONArray(key)?:JSONArray())
    fun toggle(key:String,text:String){val list=selected(key).toMutableList();if(!list.remove(text))list.add(text);update(key,JSONArray(list))}
    BackHandler(step>0 || onExit!=null){if(step>0)update("step",step-1) else onExit?.invoke()}
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().semantics{testTagsAsResourceId=true}.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("처음부터 규칙을 만들지 마세요.\n무엇을 알고 싶은지만 말해주세요.",style=MaterialTheme.typography.headlineSmall)
        Text("${step+1} / 5 · "+listOf("알림 접근","앱별 기본 처리","꼭 놓치고 싶지 않은 것","관심과 줄일 내용","예시와 최종 확인")[step])
        LinearProgressIndicator(progress={(step+1)/5f},modifier=Modifier.fillMaxWidth())
        when(step){
            0->{
                Text("알림을 대신 정리하려면 접근 권한이 필요해요",style=MaterialTheme.typography.titleLarge)
                Text("휴대폰에 표시되는 알림 내용을 바탕으로 필요한 정보를 고릅니다. 앱 내부 대화나 과거 데이터를 직접 읽지 않습니다.")
                Text("분석할 앱의 알림과 메시지 미리보기가 켜져 있어야 합니다. 미리보기가 짧거나 꺼져 있으면 원본 확인이 필요할 수 있어요.")
                Text("기준 정리는 OpenAI, 새 알림 판단은 Jev를 사용합니다. 설정 요청과 필요한 정보가 외부 AI에 전달됩니다.")
                Button(onClick=settings,modifier=Modifier.fillMaxWidth().testTag("onboarding_access")){Text(if(granted)"알림 접근 허용됨 · 설정 보기" else "알림 접근 허용")}
            }
            1->{
                Text("아예 받을 필요가 없는 알림이 있나요?",style=MaterialTheme.typography.titleLarge)
                Text("전혀 필요 없다면 원래 앱에서 끄고, 일부만 필요하다면 AI가 골라드릴 수 있어요.")
                if(rows.isEmpty())Text("아직 받은 알림이 없습니다. 알림이 들어오면 앱 목록이 생깁니다. 다음 단계에서 관심 기준을 먼저 정할 수 있어요.")
                rows.distinctBy{it.packageName}.forEach{row->
                    Text(row.appLabel,style=MaterialTheme.typography.titleMedium)
                    val modes=form.optJSONObject("apps")?:JSONObject();val mode=modes.optString(row.packageName,"AI가 골라주기")
                    listOf("그대로 받기","AI가 골라주기","알림 끄기").forEach{label->Row{
                        RadioButton(selected=mode==label,onClick={
                            update("apps",JSONObject(modes.toString()).put(row.packageName,label))
                            if(label=="알림 끄기")try{context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,row.packageName))}catch(_:Exception){Toast.makeText(context,"Android 설정에서 해당 앱의 알림을 변경해 주세요.",Toast.LENGTH_LONG).show()}
                        });Text(label,Modifier.padding(top=12.dp))
                    }}
                    if(mode=="알림 끄기")Text("설정으로 안내했습니다. 실제로 꺼졌는지는 해당 앱 설정에서 확인하세요. Inbox 숨김과 다릅니다.",style=MaterialTheme.typography.bodySmall)
                }
            }
            2->{
                Text("어떤 내용은 꼭 놓치고 싶지 않나요?",style=MaterialTheme.typography.titleLarge)
                listOf("약속 확정","일정 변경","돈 내야 하는 요청","입금 / 중요한 결제","답변이 필요한 메시지","내 이름이 언급된 메시지","가족 관련","업무 요청","예약 변경","배송 문제","보안 / 로그인","AI 작업 완료 / 입력 필요").forEach{label->Row{Checkbox(label in selected("must"),{toggle("must",label)});Text(label,Modifier.padding(top=12.dp))}}
                OutlinedTextField(form.optString("must_text"),{update("must_text",it.take(2000))},label={Text("원하는 기준을 편하게 말해보세요")},placeholder={Text("돈 관련하고 약속, 내가 답해야 하는 건 꼭 알려줘")},modifier=Modifier.fillMaxWidth().testTag("onboarding_instruction"),minLines=3)
            }
            3->{
                Text("급하지 않아도 보고 싶은 것",style=MaterialTheme.typography.titleLarge)
                Text("특정 상품 할인 · 여행 · 취미 · 프로젝트 · 배송 · 뉴스 · 특정 사람 · AI 작업 결과")
                OutlinedTextField(form.optString("more"),{update("more",it.take(2000))},label={Text("예: 기타 장비 할인은 광고라도 보여줘")},modifier=Modifier.fillMaxWidth(),minLines=2)
                Text("반대로 줄이고 싶은 알림",style=MaterialTheme.typography.titleLarge)
                listOf("쇼핑 광고","카드사 이벤트","뉴스 속보","SNS 좋아요/팔로우","단체방 잡담","게임 이벤트","일반 날씨 알림","프로모션").forEach{label->Row{Checkbox(label in selected("less"),{toggle("less",label)});Text(label,Modifier.padding(top=12.dp))}}
                OutlinedTextField(form.optString("less_text"),{update("less_text",it.take(2000))},label={Text("예: 광고는 숨기는데 배송은 보여줘")},modifier=Modifier.fillMaxWidth(),minLines=2)
                Text("반복 시스템 UI 알림은 기본적으로 수집하지 않습니다.",style=MaterialTheme.typography.bodySmall)
            }
            4->{
                Text("이렇게 말해도 됩니다",style=MaterialTheme.typography.titleLarge)
                Text("예시를 누르면 요청에 추가됩니다. 시간과 대화가 불명확하면 먼저 확인할게요.")
                listOf("밤 10시부터 아침 7시까지는 가족과 보안 알림만 알려줘.","쿠팡에서는 배송과 환불 관련 내용만 보여줘.","이 단톡방에서는 약속 장소나 시간이 결정될 때만 알려줘.","내가 직접 질문받거나 내 답을 기다릴 때 알려줘.","업무시간에는 Slack과 회사 단톡의 일정 변경, 답변 요청만 알려줘.","주말에는 업무 관련 알림을 조용히 처리해줘.","Codex가 작업을 끝냈거나 내 입력을 기다릴 때 알려줘.","이번 주만 여행과 항공편 관련 정보를 보여줘.").forEach{example->TextButton(onClick={update("advanced",(form.optString("advanced")+"\n"+example).trim())},enabled=!busy){Text(example)}}
                OutlinedTextField(form.optString("advanced"),{update("advanced",it.take(3000))},label={Text("추가 조건 · 시간·앱·대화·예외")},modifier=Modifier.fillMaxWidth(),enabled=!busy,minLines=2)
                Text("처음부터 완벽하게 설정할 필요는 없습니다. 언제든 말로 바꾸거나 기준 화면에서 수정·끄기·삭제할 수 있어요.")
                Button(onClick={
                    val modes=form.optJSONObject("apps")?:JSONObject()
                    val keep=modes.keys().asSequence().filter{modes.optString(it)=="그대로 받기"}.toList()
                    val request="온보딩에서 선택한 내용만 반영해 주세요. 미선택 항목을 숨기지 마세요. 기존 기준은 요청한 부분만 수정하세요.\n꼭 보여줄 내용: ${selected("must").joinToString()} ${form.optString("must_text")}\n추가 관심: ${form.optString("more")}\n줄일 내용: ${selected("less").joinToString()} ${form.optString("less_text")}\n추가 조건: ${form.optString("advanced")}\n앱 전체 그대로 표시: ${keep.joinToString()}\nOS 알림 끄기는 별도 설정이므로 숨김 정책으로 생성하지 마세요."
                    c.request(request)
                },enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("onboarding_generate")){Text("내 기준 정리·검증하기")}
                PolicyProposalPanel(c,rows,true)
                if(state.optBoolean("onboarding_complete") && state.optJSONObject("policy_proposal")==null){Button(onClick={onExit?.invoke()},modifier=Modifier.testTag("onboarding_done")){Text("Timeline으로 이동")}}
            }
        }
        if(step<4)Button(onClick={update("step",step+1)},enabled=(step!=0 || granted)&&!busy,modifier=Modifier.fillMaxWidth().testTag("onboarding_next")){Text("다음")}
        if(step>0)TextButton(onClick={update("step",step-1)},enabled=!busy){Text("이전 · 수정하기")}
        if(onExit!=null)TextButton(onClick=onExit,enabled=!busy){Text("나중에 이어서")}
    }
}
