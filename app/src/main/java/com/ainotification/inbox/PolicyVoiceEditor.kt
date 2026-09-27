package com.ainotification.inbox

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.util.Locale

internal fun policySpeechIntent()=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault().toLanguageTag())
    .putExtra(RecognizerIntent.EXTRA_PROMPT,"추가하거나 바꿀 알림 기준을 말씀해 주세요")
    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)

@Composable internal fun NewPolicyButton(enabled:Boolean,onClick:()->Unit) {
    Button(onClick=onClick,enabled=enabled,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF2868FF)),modifier=Modifier.testTag("new_policy")) { Text("+ 새 기준 추가") }
}

@Composable internal fun PolicyVoiceEditor(input:String,onInput:(String)->Unit,busy:Boolean,autoVoice:Boolean,onVoiceStarted:()->Unit,dismiss:()->Unit,submit:()->Unit) {
    var message by rememberSaveable { mutableStateOf("") }
    var awaitingSpeech by rememberSaveable { mutableStateOf(false) }
    var speechBase by rememberSaveable { mutableStateOf("") }
    val latestInput by rememberUpdatedState(onInput)
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        awaitingSpeech=false
        val spoken=result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        if(result.resultCode==Activity.RESULT_OK && spoken.isNotBlank()) {
            latestInput(listOf(speechBase,spoken).filter{it.isNotBlank()}.joinToString("\n").take(4000))
            message="인식된 내용을 확인하고, 필요한 부분을 고쳐 주세요."
        } else message="음성 입력을 마쳤습니다. 다시 말하거나 직접 입력해 주세요."
    }
    fun speak() {
        if(awaitingSpeech || busy)return
        speechBase=input;awaitingSpeech=true;message=""
        try { launcher.launch(policySpeechIntent()) }
        catch(_:Exception) { awaitingSpeech=false;message="음성 입력을 열 수 없습니다. 키보드로 입력하거나 다시 시도해 주세요." }
    }
    LaunchedEffect(autoVoice) { if(autoVoice){onVoiceStarted();speak()} }
    Dialog(onDismissRequest=dismiss) {
        Surface(shape=MaterialTheme.shapes.large) {
            Column(Modifier.semantics{testTagsAsResourceId=true}.testTag("policy_voice_editor").heightIn(max=550.dp).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("새 요청",style=MaterialTheme.typography.titleLarge)
                Text("원하는 알림을 말하거나 적어 주세요.",style=MaterialTheme.typography.bodyMedium)
                OutlinedTextField(input,{onInput(it.take(4000))},minLines=3,label={Text("말한 내용 · 직접 수정 가능")},enabled=!busy&&!awaitingSpeech,modifier=Modifier.fillMaxWidth().testTag("policy_request"))
                if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick={speak()},enabled=!busy&&!awaitingSpeech,modifier=Modifier.testTag("policy_microphone")){Text("마이크로 말하기")}
                Text("음성은 휴대폰의 음성 인식 서비스로 처리합니다. 확인한 문장과 기존 기준을 OpenAI에 전달합니다.",style=MaterialTheme.typography.bodySmall)
                Button(onClick=submit,enabled=input.isNotBlank()&&!busy&&!awaitingSpeech,modifier=Modifier.fillMaxWidth().testTag("generate_policy")){Text("확인")}
                TextButton(onClick=dismiss,modifier=Modifier.testTag("close_policy_editor")){Text("닫기")}
            }
        }
    }
}
