package com.ainotification.inbox

import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@org.robolectric.annotation.Config(sdk=[34],application=android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class ConversationContextTest {
 private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
 private fun result(q:JSONObject,selected:Map<String,String>):JSONObject {
  val answers=JSONObject();q.keys().forEach{k->val choices=q.getJSONObject(k).getJSONObject("criteria");val pick=selected[k] ?: if(choices.has("NONE"))"NONE" else choices.keys().next();val probabilities=JSONObject();choices.keys().forEach{probabilities.put(it,if(it==pick)1.0 else 0.0)};answers.put(k,JSONObject().put("type","choice").put("choice",pick).put("confidence",1.0).put("probabilities",probabilities))}
  return JSONObject().put("answers",answers).put("usage",JSONObject().put("input_tokens",0).put("output_tokens",0))
 }
 private val defaults=mapOf("intent" to "appointment","evidence" to "confirmed","sourceKind" to "human_message","responseLike" to "yes","linked" to "yes")
 private fun judge(w:ConversationWindow,values:Map<String,String> = emptyMap()):ConversationDecision {val q=conversationQuestions(w);return conversationDecision(w,result(q,defaults+values),q,JevEngine(context))}
 @Test fun inboundAppointmentNeverConfirmed(){val w=ConversationWindow(ContextEvidence("A","네, 금요일 2시 괜찮아요.",100));val d=judge(w,mapOf("datetime" to "V0"));assertEquals("likely",d.status);assertTrue(d.needsContext);assertEquals("금요일 2시",d.date!!.text)}
 @Test fun fullRequiresObservedOutboundAndMatchingAcceptance(){val prior=ContextEvidence("proposal","금요일 오후 2시에 방문드려도 될까요?",10,"outbound");val w=ConversationWindow(ContextEvidence("B","네, 그때 오세요.",100),listOf(prior),"full");assertEquals("confirmed",judge(w,mapOf("datetime" to "V0")).status);assertEquals("inbound_only",w.copy(previous=listOf(prior.copy(direction="inbound"))).completeness)}
 @Test fun sentMoneyResponseIsNotProofOfCredit(){val w=ConversationWindow(ContextEvidence("C","5만원 보냈어요.",100));val d=judge(w,mapOf("intent" to "money","amount" to "V0"));assertTrue(d.needsContext);assertEquals(50000L,d.amount!!.amount);assertFalse(d.service)}
 @Test fun earlierAmountRequiresProvenLink(){val w=ConversationWindow(ContextEvidence("D","방금 보냈어요.",100),listOf(ContextEvidence("request","계좌로 5만원 부탁드립니다.",10)));assertEquals(50000L,judge(w,mapOf("intent" to "money","amount" to "V0")).amount!!.amount);assertNull(judge(w,mapOf("intent" to "money","amount" to "V0","linked" to "uncertain")).amount)}
 @Test fun ordinaryChatNotCandidate(){assertFalse(conversationCandidate("오늘 저녁 뭐 먹을까?"));assertTrue(conversationCandidate("네"))}
 @Test fun partialCannotConfirmEvenModelClaimsYes(){val w=ConversationWindow(ContextEvidence("G","네, 금요일 2시 괜찮아요.",100),declaredCompleteness="partial");assertNotEquals("confirmed",judge(w,mapOf("datetime" to "V0")).status);assertTrue(conversationState(w,JevEngine(context)).getString("warning").contains("outgoing"))}
 @Test fun competingIntentIsHeld(){val w=ConversationWindow(ContextEvidence("x","금요일 2시 괜찮아요.",100));val q=conversationQuestions(w);val r=result(q,defaults);val a=r.getJSONObject("answers").getJSONObject("intent");a.put("confidence",.1);a.getJSONObject("probabilities").put("appointment",.48).put("money",.42).put("none",.10);assertEquals("insufficient_context",conversationDecision(w,r,q,JevEngine(context)).intent)}
 @Test fun numbersAreLiteralAndNoAmPmInvented(){val w=ConversationWindow(ContextEvidence("x","5만원, 잔액 100000원. 금요일 2시",1));assertEquals(listOf(50000L,100000L),conversationValues(w).mapNotNull{it.amount});assertEquals("금요일 2시",conversationValues(w).last().text)}
 @Test fun storageLookupIsBoundedAndOtherRoomsCannotLeak()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,InboxDatabase::class.java).build()
  val base=previewNotifications()[0].copy(packageName="com.kakao.talk",appLabel="카카오톡",snapshotId="current",postedTime=100_000_000,capturedTime=100_000_000,conversationIdentity="room-a",text="방금 보냈어요.",bigText=null,title="친구",conversationTitle=null,notificationCategory="msg",messagesJson="[]",isGroupSummary=false,availableFields="")
  val earlier=base.copy(snapshotId="request",postedTime=base.postedTime-1000,text="계좌로 5만원 부탁드립니다.")
  var calls=0;val engine=JevEngine(context){payload->calls++;val state=payload.getJSONObject("state");val hasPrior=state.getJSONArray("previous").length()>0;result(payload.getJSONObject("questions"),defaults+mapOf("intent" to if(hasPrior)"money" else "insufficient_context","evidence" to if(hasPrior)"related" else "insufficient_context","amount" to "V0"))}
  try {
   for(r in listOf(base,earlier,earlier.copy(snapshotId="other",conversationIdentity="room-b",text="계좌로 9만원 부탁드립니다."),earlier.copy(snapshotId="future",postedTime=base.postedTime+1000)))db.notifications().insert(r)
   ConversationPipeline(context,db,engine,{}, {base.postedTime}).process(base)
   val entry=db.structured().observe().first().single();assertEquals(50000L,entry.money!!.transactionAmount);assertEquals("transfer_related",entry.money!!.transactionType);assertTrue(entry.context!!.needsContext);assertEquals(2,calls)
   assertFalse(entry.context!!.evidenceIds.contains("other"));assertFalse(entry.context!!.evidenceIds.contains("future"))
   val thread=db.conversations().thread(entry.context!!.threadId)!!;assertTrue(thread.contextSummary.contains("발신 메시지는 확인되지 않음"));assertEquals("inbound_only",thread.contextCompleteness)
   val chat=base.copy(snapshotId="chat",text="오늘 저녁 뭐 먹을까?",postedTime=base.postedTime+2000);db.notifications().insert(chat);ConversationPipeline(context,db,engine,{}, {base.postedTime}).process(chat);assertEquals(2,calls);assertEquals(1,db.structured().observe().first().size)
  }finally{db.close()}
 }
 @Test fun exportSyntheticLiveCases(){
  if(System.getProperty("money.export")==null)return
  val cases=listOf(
   "A" to ConversationWindow(ContextEvidence("A","네, 금요일 2시 괜찮아요.",100)),
   "B" to ConversationWindow(ContextEvidence("B","네, 그때 오세요.",100),listOf(ContextEvidence("proposal","금요일 오후 2시에 방문드려도 될까요?",10,"outbound")),"full"),
   "C" to ConversationWindow(ContextEvidence("C","5만원 보냈어요.",100)),
   "D" to ConversationWindow(ContextEvidence("D","방금 보냈어요.",100),listOf(ContextEvidence("request","계좌로 5만원 부탁드립니다.",10))),
   "E" to ConversationWindow(ContextEvidence("E","오늘 저녁 뭐 먹을까?",100)),
   "F" to ConversationWindow(ContextEvidence("F","네",100)),
   "G" to ConversationWindow(ContextEvidence("G","네, 금요일 2시 괜찮아요.",100),listOf(ContextEvidence("previous","금요일이나 토요일 가능합니다.",10)))
  )
  val data=org.json.JSONArray(cases.map{(id,w)->JSONObject().put("id",id).put("state",conversationState(w,JevEngine(context))).put("questions",conversationQuestions(w))})
  java.io.File("/private/tmp/conversation-live-inputs.json").writeText(data.toString(2))
 }
 @Test fun moneyBalanceCannotBecomeTransaction(){val w=ConversationWindow(ContextEvidence("balance","잔액 100000원 보냈어요",1));assertNull(judge(w,mapOf("intent" to "money","amount" to "V0")).amount)}

 @Test fun replayLiveSyntheticJudgmentsThroughAppGuards(){
  val dir=System.getProperty("conversation.replay") ?: return
  val inputs=org.json.JSONArray(java.io.File("/private/tmp/conversation-live-inputs.json").readText())
  val summary=org.json.JSONArray()
  for(i in 0 until inputs.length()){
   val c=inputs.getJSONObject(i);val id=c.getString("id");val state=c.getJSONObject("state")
   fun evidence(v:JSONObject)=ContextEvidence(v.getString("id"),v.getString("text"),v.getLong("time"),v.getString("direction"))
   val prev=state.getJSONArray("previous");val w=ConversationWindow(evidence(state.getJSONObject("current")),(0 until prev.length()).map{evidence(prev.getJSONObject(it))},state.getString("contextCompleteness"))
   val r=JSONObject(java.io.File(dir,"live-$id.json").readText()).getJSONObject("response")
   val d=conversationDecision(w,r,conversationQuestions(w),JevEngine(context))
   when(id){
    "A","G"->{assertEquals(id,"appointment",d.intent);assertNotEquals("confirmed",d.status);assertTrue(d.needsContext);assertEquals("금요일 2시",d.date!!.text)}
    "B"->{assertEquals("confirmed",d.status);assertEquals("금요일 오후 2시",d.date!!.text)}
    "C","D"->{assertEquals("money",d.intent);assertEquals(50000L,d.amount!!.amount);assertTrue(d.needsContext);assertFalse(d.service)}
    "E","F"->assertTrue(d.intent in setOf("none","insufficient_context"))
   }
   summary.put(JSONObject().put("id",id).put("intent",d.intent).put("status",d.status).put("needsContext",d.needsContext).put("amount",d.amount?.amount).put("date",d.date?.text).put("passed",true))
  }
  java.io.File(dir,"live-results.json").writeText(summary.toString(2))
 }
 @Test fun explicitFullAcceptanceStillRejectsNegation(){
  val prior=ContextEvidence("proposal","금요일 오후 2시에 방문드려도 될까요?",10,"outbound")
  val w=ConversationWindow(ContextEvidence("B","아니요, 그때는 안 됩니다.",100),listOf(prior),"full")
  val q=conversationQuestions(w);val r=result(q,defaults+mapOf("datetime" to "V0","evidence" to "related"))
  assertNotEquals("confirmed",conversationDecision(w,r,q,JevEngine(context)).status)
 }

 @Test fun explicitServiceReceiptUsesFinancialValidationEvenIfContextIsOnlyRelated()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,InboxDatabase::class.java).build();val now=100_000_000L
  val row=previewNotifications()[0].copy(snapshotId="receipt",packageName="com.kakao.talk",appLabel="카카오톡",title="카카오페이",text="카카오페이 50,000원을 받았습니다.",bigText=null,messagesJson="[]",isGroupSummary=false,postedTime=now,availableFields="")
  var calls=0
  val engine=JevEngine(context){p->calls++;val q=p.getJSONObject("questions");result(q,if(q.has("financial"))mapOf("financial" to "MONEY","transactionType" to "transfer_in","transactionAmount" to "M0") else defaults+mapOf("intent" to "money","evidence" to "related","sourceKind" to "service_notice","responseLike" to "no","linked" to "no"))}
  try{db.notifications().insert(row);ConversationPipeline(context,db,engine,{}, {now}).process(row);val entry=db.structured().observe().first().single();assertEquals(50000L,entry.money!!.transactionAmount);assertEquals("transfer_in",entry.money!!.transactionType);assertEquals("confirmed",entry.context!!.status);assertEquals(2,calls)}finally{db.close()}
 }

}
