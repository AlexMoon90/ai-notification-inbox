package com.ainotification.inbox

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.json.JSONArray

internal class MoneyPipeline(private val context:Context,private val db:InboxDatabase,
 private val engine:JevEngine=JevEngine(context),private val designer:((JSONObject)->JSONObject)?=null,
 private val clock:()->Long={System.currentTimeMillis()}) {
 internal val replay=StructuredReplay(context)
 private var replaying=false
 suspend fun requestReplay()=lock.withLock {
  if(!replay.progress.value.active){val anchor=clock();replay.prefs.edit().clear().putBoolean("active",true).putLong("anchor",anchor).putInt("total",db.structured().replayCount(anchor)).putLong("after",Long.MIN_VALUE).putString("id","").commit();replay.publish()}
  wake()
 }
 private val lock=Mutex()
 private val signal=Channel<Unit>(Channel.CONFLATED)
 private val limits=context.getSharedPreferences("money-extraction-budget",0)
 fun wake(){signal.trySend(Unit)}
 fun start(scope:CoroutineScope){scope.launch {
  db.structured().removeOwnEvents(context.packageName)
  while(isActive){
   if(replay.progress.value.active){
    val p=replay.prefs
    val rows=db.structured().replayPage(p.getLong("anchor",0),p.getLong("after",Long.MIN_VALUE),p.getString("id","").orEmpty())
    if(rows.isEmpty()){p.edit().putBoolean("active",false).commit();replay.publish();continue}
    for(row in rows){
     var failed=false
     try{process(row,true)}catch(e:CancellationException){throw e}catch(e:Exception){replay.prefs.edit().putString("lastFailure",e.javaClass.simpleName).apply();failed=true;db.structured().saveProcessing(StructuredProcessing(row.snapshotId,"retry",clock()+3_600_000,1))}
     p.edit().putLong("after",row.capturedTime).putString("id",row.snapshotId).putInt("processed",p.getInt("processed",0)+1).putInt("failed",p.getInt("failed",0)+if(failed)1 else 0).commit();replay.publish()
    }
    delay(30);continue
   }
   val batch=try{db.structured().pending(clock())}catch(e:CancellationException){throw e}catch(_:Exception){emptyList()}
   for(row in batch)try{process(row)}catch(e:CancellationException){throw e}catch(_:Exception){
    if(db.notifications().find(row.snapshotId)!=null){val old=db.structured().processing(row.snapshotId);db.structured().saveProcessing(StructuredProcessing(row.snapshotId,"retry",clock()+3_600_000,(old?.attempts ?: 0)+1))}
   }
   if(batch.isEmpty())withTimeoutOrNull(30_000){signal.receive()} else delay(30)
  }
 }}
 private fun budget(kind:String){
  if(replaying){replay.charge(kind);return}
  if(designer!=null)return // Injected deterministic tests never spend API budget.
  val day=java.time.LocalDate.now().toString()
  val key="$day:$kind";val count=limits.getInt(key,0)
  check(count<if(kind=="structure")20 else 200){"Daily money analysis budget reached"}
  limits.edit().putInt(key,count+1).apply()
 }
 suspend fun onClassified(row:CapturedNotification,events:List<String>){
  if(db.notifications().find(row.snapshotId)==null)return
  if(moneyPossible(row,events)){
   if(db.structured().processing(row.snapshotId)?.status=="ignored")db.structured().saveProcessing(StructuredProcessing(row.snapshotId,"pending",0))
  }else promoteOther(row,events)
  wake()
 }
 private suspend fun promoteOther(row:CapturedNotification,events:List<String>){
  if(messageLike(row) || row.packageName==context.packageName || row.packageName in excludedNotificationPackages || row.isGroupSummary || row.isExcludedCallStatus())return
  if(db.structured().money(row.snapshotId)!=null)return
  if(events.isEmpty() || events.any{it in setOf("PAYMENT","MONEY_RECEIVED","REFUND")})return
  val now=clock()
  db.withTransaction{if(db.notifications().find(row.snapshotId)!=null)db.structured().insertEvent(StructuredEvent("event:${row.snapshotId}",row.snapshotId,smartCategory(events),smartHeading(events),row.appLabel,row.packageName,row.postedTime,"SCHEDULE_CHANGE" in events,now,now))}
 }
 internal suspend fun process(row:CapturedNotification,force:Boolean=false)=lock.withLock {
  replaying=force
  try {
  if(force)db.structured().resetProcessing(row.snapshotId)
  val dao=db.structured();if(db.notifications().find(row.snapshotId)==null)return@withLock
  val old=dao.processing(row.snapshotId);if(old!=null&&old.retryAt>clock())return@withLock
  if(row.packageName==context.packageName){dao.removeEvent(row.snapshotId);dao.saveProcessing(StructuredProcessing(row.snapshotId,"ignored"));return@withLock}
  explicitFinancialReceipt(row,clock())?.let{receipt->
   val event=ReceiptSenders(context).enrich(row,receipt)
   db.withTransaction{dao.removeEvent(row.snapshotId);dao.insertEvent(StructuredEvent(event.id,row.snapshotId,"money",moneyLabels.getValue(event.transactionType),row.appLabel,row.packageName,row.postedTime,false,event.createdAt,event.updatedAt));dao.saveMoney(event);dao.saveProcessing(StructuredProcessing(row.snapshotId,"done"))};return@withLock
  }
  if(force)dao.removeEvent(row.snapshotId)
  if(messageLike(row) && !row.isGroupSummary && !row.hasEmptyContent()){
   ConversationPipeline(context,db,engine,{budget("judgment")},clock).process(row);return@withLock
  }
  val events=db.hub().find(row.snapshotId)?.events().orEmpty()
  if(!moneyPossible(row,events)){
   promoteOther(row,events);dao.saveProcessing(StructuredProcessing(row.snapshotId,"ignored"));return@withLock
  }
  val c=moneyCandidates(row)
  if(row.currentMessageText().length>4000){dao.saveProcessing(StructuredProcessing(row.snapshotId,"needs_original"));return@withLock}
  val key=moneyPatternKey(row,c);val now=clock();val existing=dao.pattern(key)
  if(existing!=null&&existing.templateJson==null&&existing.retryAt>now && !force)error("Pattern analysis cooling down")
  val template=if(existing?.templateJson!=null)validateMoneyTemplate(JSONObject(existing.templateJson)) else {
   // Mark before the external call; failures/restarts do not immediately repeat the same LLM request.
   dao.savePattern(MoneyPattern(key,null,now+3_600_000,existing?.createdAt ?: now,now))
   budget("structure")
   val state=JSONObject().put("sourceApp",row.appLabel).put("pattern",c.redactedText(engine)
     .replace(Regex("(?<![\\d,])(?:\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원"),"<AMOUNT>원")
     .replace(Regex("(?<!\\d)\\d{6,}(?!\\d)"),"<NUMBER>"))
     .put("candidateKinds",JSONArray(c.values.map{it.kind}.distinct()))
   val result=validateMoneyTemplate(designer?.invoke(state) ?: SetupEngine(context).structured("money_pattern_structure",moneyStructurePrompt,moneyTemplateSchema(),state))
   dao.savePattern(MoneyPattern(key,result.toString(),0,existing?.createdAt ?: now,clock()));result
  }
  budget("judgment")
  val questions=moneyQuestions(c,template)
  val result=engine.evaluateMoney(c.external(engine).put("sourceApp",row.appLabel),questions)
  val event=assembleMoney(row,c,result,questions,engine,clock())
  db.withTransaction {
   if(db.notifications().find(row.snapshotId)==null)return@withTransaction
   if(event!=null){
    dao.insertEvent(StructuredEvent(event.id,row.snapshotId,"money",moneyLabels.getValue(event.transactionType),row.appLabel,row.packageName,row.postedTime,false,event.createdAt,event.updatedAt))
    dao.saveMoney(event)
   }
   dao.saveProcessing(StructuredProcessing(row.snapshotId,if(event==null)"ignored" else "done"))
  }
  }finally{replaying=false}
 }
}
