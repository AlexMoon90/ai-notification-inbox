package com.ainotification.inbox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable internal fun ReplySettingsRow(preferences:ReplyPreferences){
 val enabled by preferences.enabled.collectAsStateWithLifecycle()
 Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
  Column(Modifier.weight(1f)){Text("AI 답변 도우미",style=MaterialTheme.typography.titleSmall);Text("필요할 때 최근 대화 일부로 답변 초안을 준비해요.",style=MaterialTheme.typography.bodySmall)}
  Switch(enabled,{preferences.setEnabled(it)},modifier=Modifier.testTag("reply_enabled"))
 }
}
@Composable internal fun ReplyAssistantBar(room:HubRoom,preferences:ReplyPreferences,load:suspend(String)->CapturedNotification?,open:()->Unit,generator:ReplyGenerator){
 val enabled by preferences.enabled.collectAsStateWithLifecycle()
 var consent by remember(room.id){mutableStateOf(false)}
 var sheet by remember(room.id){mutableStateOf(false)}
 var tip by remember(room.id){mutableStateOf(false)}
 LaunchedEffect(enabled){
  if(enabled && !preferences.tipSeen){preferences.markTipSeen();tip=true;delay(3500);tip=false}
  if(!enabled){tip=false;sheet=false;consent=false}
 }
 Column(Modifier.fillMaxWidth().navigationBarsPadding()){
  ModernLine(true)
  if(enabled && tip)Text("바로 답하지 않아도 괜찮아요.\nAI와 답변을 준비한 뒤 필요할 때 보내세요.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.fillMaxWidth().clickable{tip=false}.padding(horizontal=16.dp,vertical=6.dp).testTag("reply_tip"))
  Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
   if(enabled)Button(onClick={if(preferences.consented)sheet=true else consent=true},modifier=Modifier.weight(1f).testTag("reply_help")){Text("✦ 답변 도와주기")}
   OutlinedButton(onClick=open,modifier=Modifier.weight(1f).testTag("reply_source")){Text("원본에서 열기")}
  }
 }
 if(enabled && consent)AlertDialog(modifier=Modifier.semantics{testTagsAsResourceId=true},onDismissRequest={consent=false},title={Text("AI 답변 도우미")},text={Text("답변을 만들기 위해 최근 대화 일부와 요청이 OpenAI에 전달됩니다. 이미지는 보내지 않으며, 답변은 사용자가 직접 확인하고 보냅니다.")},confirmButton={TextButton(onClick={preferences.consent();consent=false;sheet=true},modifier=Modifier.testTag("reply_consent")){Text("확인")}},dismissButton={TextButton(onClick={consent=false}){Text("취소")}})
 if(enabled && sheet)key(room.id){ReplyAssistantSheet(room,load,generator,{sheet=false}){sheet=false;open()}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ReplyAssistantSheet(room:HubRoom,load:suspend(String)->CapturedNotification?,generator:ReplyGenerator,dismiss:()->Unit,open:()->Unit){
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 var tone by remember{mutableStateOf("neutral")}
 var instruction by remember{mutableStateOf("")}
 var draft by remember{mutableStateOf("")}
 var hasResult by remember{mutableStateOf(false)}
 var busy by remember{mutableStateOf(false)}
 var failed by remember{mutableStateOf(false)}
 var lastRequest by remember{mutableStateOf<String?>(null)}
 var rewrite by remember{mutableStateOf(false)}
 var rewriteInstruction by remember{mutableStateOf("")}
 fun submit(request:suspend()->JSONObject){
  if(busy)return
  busy=true;failed=false
  scope.launch{
   try{
    val payload=withContext(Dispatchers.IO){request()}
    lastRequest=payload.toString()
    val result=withContext(Dispatchers.IO){generator.generate(payload)}
    draft=result;hasResult=true;rewrite=false
   }catch(e:CancellationException){throw e}
   catch(_:Exception){failed=true}
   finally{busy=false}
  }
 }
 fun generate(){val chosen=tone;val text=instruction;submit{replyRequest(room,chosen,text,load)}}
 fun revise(text:String){val current=draft;submit{rewriteRequest(current,text)}}
 ModalBottomSheet(modifier=Modifier.semantics{testTagsAsResourceId=true},onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=ModernBg){
  Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(bottom=24.dp).testTag("reply_sheet"),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text("어떻게 답할까요?",style=MaterialTheme.typography.titleLarge)
   Text("수신된 최근 대화 일부를 참고해요. 보낸 메시지나 전체 대화는 포함되지 않을 수 있어요.",style=MaterialTheme.typography.bodySmall,color=ModernMuted)
   if(!hasResult){
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
     replyTones.forEach{(id,label)->FilterChip(tone==id,{tone=if(tone==id)"neutral" else id},enabled=!busy,label={Text(label)},modifier=Modifier.testTag("reply_tone_$id"))}
    }
    OutlinedTextField(instruction,{if(it.length<=1000)instruction=it},enabled=!busy,label={Text("원하는 답변 내용을 말해주세요")},placeholder={Text("이번 주는 어렵고 다음 주 화요일은 가능하다고 해줘")},minLines=2,maxLines=5,modifier=Modifier.fillMaxWidth().testTag("reply_instruction"))
    Button(onClick={generate()},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("reply_generate")){Text("답변 만들기")}
   }else{
    Text("AI 답변 · 직접 수정할 수 있어요",style=MaterialTheme.typography.titleSmall)
    OutlinedTextField(draft,{if(it.length<=REPLY_MAX_DRAFT)draft=it},enabled=!busy,minLines=3,maxLines=8,modifier=Modifier.fillMaxWidth().testTag("reply_draft"))
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Button(onClick={
      (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("답변 초안",draft))
      Toast.makeText(context,"답변을 복사했어요",Toast.LENGTH_SHORT).show()
     },enabled=!busy && draft.isNotBlank(),modifier=Modifier.weight(1f).testTag("reply_copy")){Text("복사")}
     OutlinedButton(onClick={rewrite=!rewrite},enabled=!busy && draft.isNotBlank(),modifier=Modifier.weight(1f).testTag("reply_rewrite")){Text("다시 작성")}
    }
    if(rewrite){
     Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){
      replyRewrites.forEachIndexed{i,text->OutlinedButton(onClick={revise(text)},enabled=!busy,modifier=Modifier.testTag("reply_rewrite_$i")){Text(text)}}
     }
     OutlinedTextField(rewriteInstruction,{if(it.length<=1000)rewriteInstruction=it},enabled=!busy,label={Text("직접 요청")},maxLines=3,modifier=Modifier.fillMaxWidth().testTag("reply_rewrite_instruction"))
     TextButton(onClick={revise(rewriteInstruction)},enabled=!busy && rewriteInstruction.isNotBlank(),modifier=Modifier.testTag("reply_rewrite_submit")){Text("요청대로 다시 작성")}
    }
   }
   if(busy)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.testTag("reply_loading")){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Text("답변을 준비하고 있어요...")}
   if(failed){Text("답변을 만들지 못했어요.",color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("reply_error"));TextButton(onClick={val retry=lastRequest;if(retry==null)generate() else submit{JSONObject(retry)}},enabled=!busy,modifier=Modifier.testTag("reply_retry")){Text("다시 시도")}}
   OutlinedButton(onClick=open,modifier=Modifier.fillMaxWidth().testTag("reply_open_original")){Text("${room.service}에서 열기")}
   TextButton(onClick=dismiss,modifier=Modifier.fillMaxWidth().testTag("reply_close")){Text("닫기")}
  }
 }
}
