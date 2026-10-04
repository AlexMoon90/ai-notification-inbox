package com.ainotification.inbox

import android.content.Context
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

internal class ConversationPipeline(private val context:Context,private val db:InboxDatabase,private val engine:JevEngine,private val budget:()->Unit,private val clock:()->Long) {
 private suspend fun recent(row:CapturedNotification):List<CapturedNotification> {
  val identity=row.conversationIdentity?.takeIf{it.isNotBlank()}
  val person=row.personIdentity?.takeIf{it.isNotBlank()&&row.isGroupConversation!=true}
  return db.conversations().recent(row.packageName,identity,person,row.notificationKey,row.channelId.orEmpty(),(row.conversationTitle ?: row.title).orEmpty(),row.postedTime-48*3_600_000,row.postedTime,row.snapshotId)
   .filter{conversationRoomId(it)==conversationRoomId(row)&&!it.isGroupSummary}
 }
 private fun window(row:CapturedNotification,previous:List<CapturedNotification>):ConversationWindow {
  var left=3600
  val currentText=row.currentMessageText().take(1400)
  val seen=hashSetOf(currentText)
  val prior=previous.sortedByDescending{it.postedTime}.mapNotNull { r ->
   val text=r.currentMessageText().take(800)
   if(text.isBlank()||!seen.add(text)||left<=0)null else ContextEvidence(r.snapshotId,text.take(left),r.postedTime).also{left-=it.text.length}
  }.take(8).reversed()
  return ConversationWindow(ContextEvidence(row.snapshotId,currentText,row.postedTime,sourceLabel=row.title.orEmpty()),prior,if(row.needsOriginalReview()||row.currentMessageText().length>1400)"partial" else "inbound_only")
 }
 suspend fun process(row:CapturedNotification){
  val dao=db.conversations();val now=clock();val id=stableMessageIdentity(conversationRoomId(row));val old=dao.thread(id)
  val thread=old ?: ConversationThread(id,row.appLabel,row.personIdentity,row.conversationIdentity,notificationDisplayTitle(row),null,row.postedTime,updatedAt=now)
  dao.saveThread(thread.copy(lastMessageAt=maxOf(row.postedTime,thread.lastMessageAt),updatedAt=now))
  dao.prune(now-48*3_600_000)
  if(!conversationCandidate(row.currentMessageText())){db.structured().saveProcessing(StructuredProcessing(row.snapshotId,"ignored"));return}
  var w=window(row,emptyList())
  fun judge():ConversationDecision {budget();val q=conversationQuestions(w);return conversationDecision(w,engine.evaluateConversation(conversationState(w,engine),q),q,engine)}
  var decision=judge()

  if(decision.needsContext || decision.intent=="insufficient_context"){
   // Rehydrate bounded source IDs: deleted/expired/future evidence is never supplied from a stale summary.
   val cached=evidenceIds(thread.evidenceIds).take(8).mapNotNull{db.notifications().find(it)}.filter{it.postedTime in (row.postedTime-48*3_600_000)..row.postedTime && it.snapshotId!=row.snapshotId && conversationRoomId(it)==conversationRoomId(row)}
   if(cached.isNotEmpty()) {w=window(row,cached).copy(summary=thread.contextSummary);decision=judge()}
   if(decision.needsContext){
    val rows=recent(row)
    val expanded=window(row,rows)
    if(expanded.previous.isNotEmpty() && expanded.previous.map{it.id}!=w.previous.map{it.id}){w=expanded;decision=judge()}
   }
  }
  val meaningful=decision.intent in setOf("money","appointment","task") && decision.status!="insufficient_context"
  // A bare yes or objectless reply is not promoted just because an old topic exists.
  val supported=meaningful && (decision.intent!="appointment" || decision.date!=null) && (decision.intent!="money" || decision.amount!=null || decision.service)
  if(!supported){db.structured().saveProcessing(StructuredProcessing(row.snapshotId,if(decision.intent=="none")"ignored" else "insufficient_context"));return}
  val refs=(listOf(row.snapshotId)+listOfNotNull(decision.amount?.sourceId,decision.date?.sourceId)+if(decision.linked)w.previous.map{it.id} else emptyList()).distinct().take(9)
  val eventId="event:${row.snapshotId}"
  var money:MoneyEvent?=null
  if(decision.intent=="money") {
   if(decision.service){
    val c=moneyCandidates(row);val template=JSONObject().put("questions",JSONArray());val qs=moneyQuestions(c,template)
    budget();money=assembleMoney(row,c,engine.evaluateMoney(c.external(engine).put("contextCompleteness",w.completeness).put("warning",conversationWarning),qs),qs,engine,now)
   }
   if(money==null){
    val type=if(decision.linked && decision.amount?.sourceId!=row.snapshotId && Regex("보냈|송금|이체").containsMatchIn(row.currentMessageText()))"transfer_related" else "money_related_unknown"
    money=MoneyEvent(eventId,row.snapshotId,type,"unknown",decision.amount?.amount,null,null,null,null,null,null,null,row.appLabel,decision.confidence,0.0,"needs_context",true,now,now)
   }
  }
  val status=if(decision.service && money?.extractionStatus=="complete")"confirmed" else if(money?.extractionStatus=="needs_context")"likely" else decision.status
  val kind=when(decision.intent){"money"->if(money?.transactionType=="transfer_related")"transfer_related" else "money_related";"appointment"->if(status=="confirmed")"appointment" else "appointment_related";else->if(status=="confirmed")"task_confirmed" else "task_related"}
  val title=when(decision.intent){"money"->moneyLabels.getValue(money!!.transactionType);"appointment"->if(status=="confirmed")"약속 확정" else "일정 관련 메시지";else->if(status=="confirmed")"업무 요청·상태" else "업무 관련 메시지"}
  val ec=EventContext(eventId,id,kind,status,status!="confirmed",w.completeness,"[\"notification_history\"]",JSONArray(refs).toString(),decision.date?.text,decision.confidence,decision.response,decision.relationship,decision.relationshipConfidence)
  db.withTransaction {
   if(db.notifications().find(row.snapshotId)==null)return@withTransaction
   db.structured().insertEvent(StructuredEvent(eventId,row.snapshotId,when(decision.intent){"appointment"->"schedule";"task"->"todo";else->"money"},title,row.appLabel,row.packageName,row.postedTime,false,now,now))
   money?.let{db.structured().saveMoney(it)};dao.saveContext(ec)
   db.structured().saveProcessing(StructuredProcessing(row.snapshotId,"done"))
   // Extractive summary: selected evidence IDs + intent/confidence/completeness, rendered from originals only.
   if(row.postedTime>=thread.lastMessageAt)dao.saveThread(thread.copy(lastMessageAt=row.postedTime,contextSummary="수신 메시지에서 ${when(decision.intent){"money"->"돈";"appointment"->"일정";else->"업무"}} 관련 정보가 관찰됨. 사용자 발신 메시지는 확인되지 않음.",evidenceIds=JSONArray(refs).toString(),lastIntent=decision.intent,contextConfidence=decision.confidence,contextCompleteness=w.completeness,updatedAt=now))
  }
 }
}
