package com.ainotification.inbox

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class ReplyAssistantTest {
 private val context get()=ApplicationProvider.getApplicationContext<Application>()
 private fun message(i:Int,text:String="내용 $i",at:Long=i*1000L,image:Boolean=false):HubMessage {
  val source=previewNotifications()[0].copy(snapshotId="source$i",title="테스트",text=text,bigText=null,messagesJson="[]")
  return HubMessage(source,"발신자",text,at,"identity$i",imageFile=if(image)"private.png" else null,hasImage=image)
 }
 private fun room(messages:List<HubMessage>)=HubRoom("private-room-id","카카오톡","테스트방",messages)
 @Test fun contextIsRecentBoundedAndImagesAndPathsAreOmitted()=runBlocking {
  val r=room((1..30).map{message(it,"가".repeat(2000))}+message(31,"사진",image=true))
  val input=replyRequest(r,"polite","다음 주 화요일은 가능해",{error("non-indexed source needs no lookup")})
  val conversation=input.getJSONObject("conversation");val a=conversation.getJSONArray("messages")
  assertTrue(a.length()<=10)
  assertTrue((0 until a.length()).sumOf{a.getJSONObject(it).getString("text").length}<=REPLY_MAX_CONTEXT)
  assertTrue(a.getJSONObject(0).getLong("timestamp")>=22000)
  assertTrue(conversation.getBoolean("images_omitted"));assertTrue(conversation.getBoolean("received_messages_only"))
  assertFalse(input.toString().contains("private.png"));assertFalse(input.toString().contains("private-room-id"))
 }
 @Test fun longGapDoesNotPullInUnrelatedEarlierThread()=runBlocking {
  val input=replyRequest(room(listOf(message(1,at=1),message(2,at=7*3600000L))),"neutral","",{null})
  assertEquals(1,input.getJSONObject("conversation").getJSONArray("messages").length())
 }
 @Test fun rewriteSendsEditedDraftAndRequestOnly(){
  val request=rewriteRequest("제가 수정한 답변","더 짧게")
  assertEquals("제가 수정한 답변",request.getString("current_reply"))
  assertFalse(request.has("conversation"))
 }
 @Test fun strictReplyValidationRejectsEmptyNullMissingAndExtraFields(){
  assertEquals("답변",parseReply(JSONObject().put("reply"," 답변 ")))
  for(invalid in listOf(JSONObject(),JSONObject().put("reply",JSONObject.NULL),JSONObject().put("reply"," "),JSONObject().put("reply",123),JSONObject().put("reply","답변").put("send",true),JSONObject().put("reply","가".repeat(4001)))){
   assertTrue(runCatching{parseReply(invalid)}.isFailure)
  }
 }
 @Test fun settingsPersistAndAreIndependentOfNotificationRules(){
  val name="reply-test-${System.nanoTime()}";val prefs=ReplyPreferences(context,name)
  assertTrue(prefs.enabled.value);assertFalse(prefs.consented)
  prefs.setEnabled(false);prefs.consent();prefs.markTipSeen()
  val restored=ReplyPreferences(context,name)
  assertFalse(restored.enabled.value);assertTrue(restored.consented);assertTrue(restored.tipSeen)
 }
 @Test fun generationIsExplicitAndSeparatedFromClassification()=runBlocking {
  var calls=0
  val engine=ReplyEngine(context){purpose,prompt,schema,input->
   calls++;assertEquals("reply_assistant",purpose);assertTrue(prompt.contains("Never invent"));assertFalse(schema.getBoolean("additionalProperties"));assertEquals("reply_assistant",input.getString("task"));JSONObject().put("reply","확인했습니다.")
  }
  val request=replyRequest(room(listOf(message(1))),"neutral","",{null})
  assertEquals(0,calls);assertEquals("확인했습니다.",engine.generate(request));assertEquals(1,calls)
 }
 @Test fun responseEnvelopeUsesStrictSchemaNoStoreAndDedicatedPurpose()=runBlocking {
  var payload:JSONObject?=null
  val setup=SetupEngine(context){body->payload=body;JSONObject("""{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"{\"reply\":\"확인했습니다.\"}"}]}]}""")}
  val engine=ReplyEngine(context){purpose,prompt,spec,input->setup.structured(purpose,prompt,spec,input)}
  engine.generate(replyRequest(room(listOf(message(1))),"neutral","",{null}))
  assertFalse(payload!!.getBoolean("store"));assertEquals(1800,payload!!.getInt("max_output_tokens"))
  val format=payload!!.getJSONObject("text").getJSONObject("format")
  assertEquals("json_schema",format.getString("type"));assertEquals("reply_assistant",format.getString("name"));assertTrue(format.getBoolean("strict"))
 }
 @Test fun indexedPreviewLoadsOnlySelectedOriginalAndUsesFullText()=runBlocking {
  val text="원문".repeat(500)
  val source=previewNotifications()[0].copy(snapshotId="full",packageName="com.kakao.talk",notificationCategory="msg",isGroupSummary=false,title="테스트방",messagesJson=org.json.JSONArray().put(JSONObject().put("text",text).put("timestamp",1000).put("sender","상대")).toString())
  val full=conversationRooms(listOf(source)).single().latest
  val preview=full.copy(text=text.take(240),source=source.copy(availableFields=DISPLAY_PREVIEW))
  var loads=0
  val request=replyRequest(room(listOf(preview)),"neutral","",{id->loads++;assertEquals("full",id);source})
  assertEquals(1,loads)
  assertEquals(text,request.getJSONObject("conversation").getJSONArray("messages").getJSONObject(0).getString("text"))
 }

}
