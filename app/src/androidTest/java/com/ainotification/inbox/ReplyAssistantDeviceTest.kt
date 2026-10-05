package com.ainotification.inbox

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

class ReplyAssistantDeviceTest {
 @Test fun manualGenerationRetryEditCopyRewriteCancelAndDisable(){
  val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext.applicationContext as InboxApplication
  val prefsName="reply-device-test";app.getSharedPreferences(prefsName,0).edit().clear().commit()
  val prefs=ReplyPreferences(app,prefsName);val calls=AtomicInteger();val opened=AtomicInteger()
  val requests=java.util.Collections.synchronizedList(mutableListOf<String>())
  val source=previewNotifications()[0].copy(snapshotId="synthetic-reply",packageName="com.kakao.talk",appLabel="카카오톡",title="합성 테스트방",text="내일 3시에 가능하세요?",bigText=null,messagesJson="[]")
  fun room(id:String)=HubRoom(id,"카카오톡","합성 테스트방",listOf(HubMessage(source,"테스트 발신자",source.text!!,1000,"synthetic-message")))
  val current=mutableStateOf(room("room-a"))
  val generator=ReplyGenerator{request->val n=calls.incrementAndGet();requests+=request.toString();delay(if(n>=4)1500 else 250);if(n==1)error("synthetic failure");if(request.getString("task")=="reply_assistant_rewrite")"짧게 수정한 답변입니다." else "확인했습니다. 가능한 시간을 확인해 볼게요."}
  val device=UiDevice.getInstance(inst);device.wakeUp()
  fun find(tag:String)=device.wait(Until.findObject(By.res(tag)),10000) ?: error("Missing $tag")
  fun editable(tag:String):UiObject2 {val root=find(tag);return if(root.className=="android.widget.EditText")root else root.findObject(By.clazz("android.widget.EditText")) ?: error("Missing input $tag")}
  fun reveal(tag:String):UiObject2 {repeat(6){device.findObject(By.res(tag))?.let{return it};device.swipe(540,1700,540,750,20)};return find(tag)}
  val clipboard=app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   var saved:android.content.ClipData?=null
   scenario.onActivity{a->saved=clipboard.primaryClip;a.setShowWhenLocked(true);a.setTurnScreenOn(true);a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);a.setContent{InboxTheme{Column(Modifier.fillMaxSize().systemBarsPadding().semantics{testTagsAsResourceId=true}){ReplySettingsRow(prefs);Text("합성 메시지 화면");Spacer(Modifier.weight(1f));androidx.compose.runtime.key(current.value.id){ReplyAssistantBar(current.value,prefs,{null},{opened.incrementAndGet()},generator)}}}}}
   try {
    find("reply_help");assertEquals(0,calls.get())
    find("reply_help").click();find("reply_consent").click();find("reply_sheet");assertEquals(0,calls.get())
    editable("reply_instruction").text="내일은 어렵고 다음 주 화요일은 가능하다고 해줘"
    find("reply_tone_polite").click();reveal("reply_generate").click();find("reply_error");assertEquals(1,calls.get())
    reveal("reply_retry").click();editable("reply_draft");assertEquals(2,calls.get())
    assertEquals(requests[0],requests[1]);assertTrue(JSONObject(requests[1]).getJSONObject("user_request").getString("instruction").contains("화요일"))
    editable("reply_draft").text="제가 직접 고친 답변";reveal("reply_copy").click()
    scenario.onActivity{assertEquals("제가 직접 고친 답변",clipboard.primaryClip!!.getItemAt(0).text.toString())};assertEquals(2,calls.get())
    reveal("reply_rewrite").click();find("reply_rewrite_0");device.waitForIdle();android.os.SystemClock.sleep(700);reveal("reply_rewrite_0").click();editable("reply_draft")
    assertTrue(device.wait(Until.hasObject(By.text("짧게 수정한 답변입니다.")),10000));assertEquals(3,calls.get())
    val rewrite=JSONObject(requests.last());assertFalse(rewrite.has("conversation"));assertEquals("제가 직접 고친 답변",rewrite.getString("current_reply"))
    reveal("reply_open_original").click();assertEquals(1,opened.get());find("reply_help")
    find("reply_help").click();find("reply_sheet");assertFalse(device.hasObject(By.res("reply_consent")))
    find("reply_tone_brief").click();reveal("reply_generate").click();find("reply_loading")
    // Room change disposes the request scope; the next room must never receive this draft.
    scenario.onActivity{current.value=room("room-b")}
    find("reply_help");assertFalse(device.hasObject(By.res("reply_sheet")))
    find("reply_enabled").click();assertTrue(device.wait(Until.gone(By.res("reply_help")),5000));find("reply_source")
    assertTrue(requests.size>=4)
   } catch(e:Throwable){device.dumpWindowHierarchy(java.io.File(app.cacheDir,"reply-test-failure.xml"));throw e} finally {
    scenario.onActivity{if(saved!=null)clipboard.setPrimaryClip(saved!!) else clipboard.clearPrimaryClip();it.setShowWhenLocked(false);it.setTurnScreenOn(false);it.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
    app.getSharedPreferences(prefsName,0).edit().clear().commit()
   }
  }
 }
 @Test fun nativeApiGeneratesStructuredReplyFromSyntheticConversation()=kotlinx.coroutines.runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as InboxApplication
  val source=previewNotifications()[0].copy(snapshotId="synthetic-native-reply",text="내일 오후 3시에 가능하세요?",bigText=null,messagesJson="[]")
  val room=HubRoom("synthetic-native-room","테스트","합성 대화",listOf(HubMessage(source,"합성 상대",source.text!!,1000,"synthetic")))
  val request=replyRequest(room,"polite","내일은 어렵고 다음 주 화요일은 가능하다고 해줘",{null})
  val reply=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){ReplyEngine(app).generate(request)}
  assertTrue(reply.isNotBlank());assertTrue(reply.contains("화요일"))
 }

}
