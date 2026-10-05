package com.ainotification.inbox

import org.json.JSONArray
import org.json.JSONObject

internal data class ConversationValue(val id:String,val sourceId:String,val text:String,val amount:Long?=null)
internal fun conversationValues(w:ConversationWindow):List<ConversationValue> {
 val out=mutableListOf<ConversationValue>()
 for(e in w.all){
  Regex("(?<![\\d,])(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?\\s*(?:만\\s*)?원").findAll(e.text).take(6).forEach{m->
   val value=runCatching{java.math.BigDecimal(m.value.replace(Regex("[^0-9.]"),"")).multiply(java.math.BigDecimal(if(m.value.contains("만"))10000 else 1)).longValueExact()}.getOrNull()
   if(value!=null)out+=ConversationValue("V${out.size}",e.id,m.value,value)
  }
  val day="""(?:(?:20\d{2}년\s*)?(?:\d{1,2}월\s*)?\d{1,2}일|[월화수목금토일]요일|오늘|내일|모레)"""
  val time="""(?:(?:오전|오후)?\s*(?:[0-2]?\d:[0-5]\d|\d{1,2}시(?:\s*\d{1,2}분)?))"""
  Regex("$day(?:\\s*$time)?|$time").findAll(e.text).take(4).forEach{m->out+=ConversationValue("V${out.size}",e.id,m.value.trim())}
 }
 return out.take(60)
}
internal fun conversationState(w:ConversationWindow,engine:JevEngine):JSONObject {
 fun safe(s:String)=engine.masked(s).replace(Regex("(?<![\\d])(?:[\\d*]{2,6}[- ][\\d*]{2,6}[- ][\\d*]{2,8})(?![\\d])"),"[ACCOUNT_OR_IDENTIFIER]").replace(Regex("(?<!\\d)\\d{6,}(?!\\d)"),"[NUMBER]")
 fun entry(e:ContextEvidence)=JSONObject().put("id",e.id).put("text",safe(e.text)).put("direction",e.direction).put("sourceLabel",safe(e.sourceLabel)).put("time",e.time)
 return JSONObject().put("warning",conversationWarning).put("contextCompleteness",w.completeness).put("senderLabelIsPhone",Regex("[+0-9][0-9 ()-]{5,}").matches(w.current.sourceLabel.replace(Regex("[\\p{Cf}]"),"").trim())).put("contextSummary",safe(w.summary.orEmpty())).put("current",entry(w.current)).put("previous",JSONArray(w.previous.map(::entry)))
  .put("candidates",JSONArray(conversationValues(w).map{JSONObject().put("id",it.id).put("sourceId",it.sourceId).put("text",safe(it.text)).put("kind",if(it.amount==null)"datetime" else "amount")}))
}
internal fun conversationQuestions(w:ConversationWindow):JSONObject {
 fun choice(prompt:String,options:Map<String,String>)=JSONObject().put("type","choice").put("instructions",conversationWarning+" "+prompt).put("criteria",JSONObject(options))
 val q=JSONObject().put("intent",choice("Classify the TOPIC of `current.text` using `previous` when this is a short reply. This is topic relevance, NOT proof of completion. A reply about sending after a supplied bank-account money request is money-related even when direction/completion is unverified. A date/time availability reply is appointment-related even without a visible outgoing proposal. A casual dinner question without specific schedule is chatter. Ads and hypothetical examples are not events.",linkedMapOf("money" to "Actual money-related communication or financial service notice", "appointment" to "Specific appointment/reservation/visit/meeting coordination with concrete date/time or an identifiable earlier proposal", "task" to "Concrete work request, deadline, completion or change", "none" to "Casual talk, greetings, acknowledgement without specific event, advertisements", "insufficient_context" to "Cannot distinguish money, appointment, task or ordinary reply")))
 q.put("evidence",choice("Judge agreement/completion established by `current` AND `previous`. If contextCompleteness is full and previous includes an explicit OUTBOUND appointment proposal and current explicitly accepts that proposal, the appointment is confirmed. When only inbound availability replies are observed choose related, not confirmed; a specific date/time reply still has related information. Money sent as a human conversational claim is related, not proof of bank credit/direction. Choose insufficient_context for a bare reply with no identifiable topic/object.",linkedMapOf("confirmed" to "Explicit, supported event/acceptance with sufficient observed context", "related" to "Concrete event is related but completion, agreement or direction remains unverified", "not_related" to "No meaningful event", "insufficient_context" to "Antecedent/object missing")))
 q.put("sourceKind",choice("Is CURRENT a self-contained machine/service financial receipt or a person's conversational statement?",mapOf("service_notice" to "Explicit provider's transaction receipt: card approval, bank 입금/출금/잔액, named payment service says transfer received; not merely a person saying 보냈어요", "human_message" to "Conversational request/reply, person says they sent or will send money", "unknown" to "Cannot establish source form")))
 q.put("responseLike",choice("Does CURRENT reply to a prior proposal/request or omit its object, including 네, 그때, 보냈어요, 괜찮아요, 취소해주세요?",mapOf("yes" to "Response/deictic/elliptic", "no" to "Self-contained statement", "unknown" to "Uncertain")))
 q.put("linked",choice("Is `current.text` TOPICALLY linked to one supplied `previous` message? This does not certify completion, direction or agreement. A short sending response following a bank-account amount request is topically linked even though outgoing messages may be missing. A direct acceptance of an observed outbound appointment proposal is linked. If multiple incompatible topics/amounts/dates compete choose uncertain.",mapOf("yes" to "The current reply relates to the topic/value of supplied previous evidence; this is not proof of completion", "no" to "Unrelated/no supplied earlier evidence", "uncertain" to "Might refer to unseen message or another topic")))
 q.put("relationship",choice("For an appointment/task, what relationship is supported by current and previous messages? senderLabelIsPhone is a WEAK business prior per user preference, not proof of an unknown contact or work. Absence of intimacy alone is not evidence. Professional visit/service/customer/inspection/meeting/document language supports work; explicit friendship/family/leisure supports personal. Neutral time availability alone remains unknown. Never infer occupation or personal relationship from a number alone.",mapOf("work_likely" to "Business/customer/service context supported by content; phone label may reinforce", "personal_likely" to "Personal/family/friends/leisure context supported by content", "unknown" to "No reliable relationship evidence or conflicting evidence")))
 val values=conversationValues(w)
 for((field,amount) in listOf("amount" to true,"datetime" to false)){
  val options=linkedMapOf("NONE" to "Not explicitly present, ambiguous, balance/limit rather than transaction amount, or no supported link to earlier value")
  values.filter{(it.amount!=null)==amount}.forEach{options[it.id]="Exact candidate ${it.id}; use only if relevant to CURRENT event, not a different earlier event"}
  if(options.size>1)q.put(field,choice("Select the ${if(amount)"transaction amount (never balance or limit)" else "appointment/task date and time"} candidate ID. Do not infer missing year, AM/PM, time or amount. For a short reply, select the exact value mentioned in the single topically related previous proposal/request, even when agreement or money completion is uncertain. Selecting a mention is NOT confirming the event. Return NONE for competing amounts/dates or unrelated topics.",options))
 }
 return q
}
internal data class ConversationDecision(val intent:String,val status:String,val response:Boolean,val service:Boolean,val linked:Boolean,val confidence:Double,val amount:ConversationValue?,val date:ConversationValue?,val needsContext:Boolean,val relationship:String="unknown",val relationshipConfidence:Double=0.0)
internal fun conversationDecision(w:ConversationWindow,r:JSONObject,q:JSONObject,engine:JevEngine):ConversationDecision {
 fun answer(k:String)=engine.readChoice(r,k,q.getJSONObject(k).getJSONObject("criteria").keys().asSequence().toSet())
 fun strong(k:String,id:String)=answer(k).let{it.id==id&&it.probability>=.9&&it.confidence>=.6}
 val a=answer("intent");val ps=r.getJSONObject("answers").getJSONObject("intent").getJSONObject("probabilities")
 val ranked=ps.keys().asSequence().map{ps.getDouble(it)}.sortedDescending().toList()
 val intent=if(a.probability>=.85 && a.confidence>=.55 && ranked[0]-ranked[1]>=.25)a.id else "insufficient_context"
 val response=responseLike(w.current.text)||!strong("responseLike","no")
 val service=strong("sourceKind","service_notice") && Regex("카카오페이|은행|카드|잔액|승인|입금|출금").containsMatchIn(w.current.text+" "+w.current.sourceLabel)
 val linked=strong("linked","yes")
 val explicitAcceptance=w.completeness=="full" && linked && intent=="appointment" &&
  Regex("^(네[, .!]*\\s*)?(그때|그 시간에)\\s*(오세요|뵙겠습니다|만나요)[.! ]*$").matches(w.current.text.trim()) &&
  w.previous.count{it.direction=="outbound"&&Regex("될까요|괜찮을까요|어떨까요").containsMatchIn(it.text)}==1 &&
  conversationValues(w).filter{it.amount==null}.size==1
 val certainty=(strong("evidence","confirmed")||explicitAcceptance) && (!response || w.completeness=="full" || service)
 fun pick(k:String):ConversationValue? {
  if(!q.has(k))return null
  val v=answer(k)
  val exactMention=conversationValues(w).filter{it.sourceId==w.current.id && (it.amount!=null)==(k=="amount")}.singleOrNull()?.takeIf{
   !service && response && ((k=="amount" && intent=="money" && Regex("원(?:을|를)?\\s*(보냈|보내드렸|송금|이체|부탁)").containsMatchIn(w.current.text)) || (k=="datetime" && intent=="appointment"))
  }
  val chosen=conversationValues(w).find{it.id==v.id}?.takeIf{v.probability>=.95&&v.confidence>=.6} ?: exactMention
  return chosen?.takeIf{it.sourceId==w.current.id || linked}?.takeUnless{candidate->
   if(k!="amount")false else {
    val text=w.all.first{it.id==candidate.sourceId}.text
    val index=text.indexOf(candidate.text)
    Regex("잔액|한도|누적|가용|잔여|balance|limit",RegexOption.IGNORE_CASE).containsMatchIn(text.substring((index-14).coerceAtLeast(0),index).substringAfterLast('\n'))
   }
  }
 }
 val amount=pick("amount");val date=pick("datetime")
 val concrete=intent in setOf("money","appointment","task") && !strong("evidence","not_related") && (amount!=null || date!=null || strong("evidence","related")||strong("evidence","confirmed"))
 val confirmed=concrete&&certainty && (intent!="appointment" || date!=null) && (intent!="money" || service)
 val relation=if(q.has("relationship") && r.getJSONObject("answers").has("relationship"))answer("relationship") else null
 return ConversationDecision(intent,if(confirmed)"confirmed" else if(concrete)"likely" else "insufficient_context",response,service,linked,a.probability,amount,date,!confirmed,relation?.id?.takeIf{relation.probability>=.8&&relation.confidence>=.5} ?: "unknown",relation?.probability ?: 0.0)
}
