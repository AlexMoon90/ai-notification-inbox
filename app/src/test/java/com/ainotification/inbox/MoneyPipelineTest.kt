package com.ainotification.inbox

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class MoneyPipelineTest {
 private val context get()=ApplicationProvider.getApplicationContext<Application>()
 private fun row(id:String="a",text:String="김OO 50,000원 입금\n잔액 1,253,400원",pkg:String="bank.test",title:String="은행 알림")=previewNotifications()[0].copy(snapshotId=id,title=title,text=text,bigText=null,packageName=pkg,appLabel=if(pkg=="com.kakao.talk")"카카오톡" else "테스트은행",messagesJson="[]",isGroupSummary=false,availableFields="",conversationIdentity=null,conversationTitle=null,notificationCategory=null,serviceType=null,personIdentity=null)
 private fun template()=JSONObject().put("category","money").put("schema","money_v1").put("possibleTransactionTypes",JSONArray(moneyTypes)).put("fields",JSONArray(moneyFields)).put("questions",JSONArray())
 private fun answer(q:JSONObject,chosen:Map<String,String>,financial:Double=.99,certainty:Double=.99):JSONObject {
  val answers=JSONObject()
  q.keys().forEach{k->
   if(q.getJSONObject(k).getString("type")=="noul")answers.put(k,JSONObject().put("type","noul").put("noul",financial))
   else {
    val ids=q.getJSONObject(k).getJSONObject("criteria").keys().asSequence().toList()
    val choice=if(k=="financial")"MONEY" else chosen[k] ?: "NONE"
    val certainty=if(k=="financial")financial else certainty
    val p=JSONObject();ids.forEach{p.put(it,if(it==choice)certainty else (1-certainty)/(ids.size-1))}
    answers.put(k,JSONObject().put("type","choice").put("choice",choice).put("confidence",certainty).put("probabilities",p))
   }
  }
  return JSONObject().put("answers",answers).put("usage",JSONObject().put("input_tokens",100).put("output_tokens",10))
 }
 private fun extract(r:CapturedNotification,type:String,amount:String="M0",balance:String="NONE",extra:Map<String,String> = emptyMap(),financial:Double=.99):MoneyEvent? {
  val c=moneyCandidates(r);val q=moneyQuestions(c,template());return assembleMoney(r,c,answer(q,mapOf("transactionType" to type,"transactionAmount" to amount,"balanceAfter" to balance)+extra,financial),q,JevEngine(context),100)
 }
 @Test fun bankDepositAndWithdrawalSeparateBalance(){
  val incoming=extract(row(),"deposit",balance="M1")!!
  assertEquals(50000L,incoming.transactionAmount);assertEquals(1253400L,incoming.balanceAfter);assertEquals("in",incoming.direction)
  val outgoing=extract(row(text="100,000원 출금\n잔액 900,000원"),"withdrawal",balance="M1")!!
  assertEquals(100000L,outgoing.transactionAmount);assertEquals(900000L,outgoing.balanceAfter);assertEquals("out",outgoing.direction)
 }
 @Test fun merchantCounterpartyAndProviderMustBeLiteralCandidates(){
  val card=row(text="스타벅스 6,500원 승인",title="삼성카드")
  val c=moneyCandidates(card);val merchant=c.texts.single{it.text=="스타벅스"}.id
  val m=extract(card,"payment",extra=mapOf("merchant" to merchant))!!
  assertEquals("스타벅스",m.merchant);assertEquals("-6,500원",moneyDisplayAmount(m))
  val kakao=row(text="김OO님이 50,000원을 보냈습니다.",pkg="com.kakao.talk",title="카카오페이")
  val candidates=moneyCandidates(kakao)
  val e=extract(kakao,"transfer_in",extra=mapOf("counterparty" to candidates.texts.first{it.text=="김OO"}.id,"provider" to candidates.texts.first{it.text=="카카오페이"}.id))!!
  assertEquals("김OO",e.counterparty);assertEquals("카카오페이",e.provider);assertEquals(50000L,e.transactionAmount)
  assertFalse(moneyCandidates(kakao.copy(title="친구")).texts.any{it.text=="카카오페이"})
 }
 @Test fun refundIsIncomingAndNoBalanceIsUsedAsTransaction(){
  assertEquals("+32,500원",moneyDisplayAmount(extract(row(text="쿠팡 32,500원 환불 완료"),"refund")!!))
  val wrong=extract(row(),"deposit",amount="M1")!!
  assertNull(wrong.transactionAmount);assertEquals("partial",wrong.extractionStatus)
  assertNull(extract(row(),"deposit",amount="M0",balance="M0")!!.transactionAmount)
  assertNull(extract(row(text="한도 1,000,000원"),"money_related_unknown")!!.transactionAmount)
 }
 @Test fun uncertainSubtypeRetainsFinancialCategoryButNoInventedValues(){
  val e=extract(row(text="금융 관련 안내"),"money_related_unknown",amount="NONE")!!
  assertEquals("unknown",e.direction);assertNull(e.transactionAmount);assertNull(e.occurredAt);assertEquals("partial",e.extractionStatus)
  assertNull(extract(row(),"deposit",financial=.7))
  val r=row();val q=moneyQuestions(moneyCandidates(r),template())
  val a=answer(q,mapOf("transactionType" to "deposit","transactionAmount" to "M0"),certainty=.7)
  val low=assembleMoney(r,moneyCandidates(r),a,q,JevEngine(context),100)!!
  assertEquals("money_related_unknown",low.transactionType);assertNull(low.transactionAmount)
 }
 @Test fun normalChatDoesNotEnterSmartAndEmailDoesNotBecomeOther(){
  assertFalse(moneyPossible(row(text="오늘 저녁에 볼까?",pkg="com.kakao.talk",title="친구"),emptyList()))
  assertTrue(messageLike(row(pkg="com.google.android.gm")))
 }
 @Test fun patternReusesAmountsAndPartiesButNotMeaningOrScope(){
  val a=row();val b=row("b","이OO 60,000원 입금\n잔액 1,263,400원")
  assertEquals(moneyPatternKey(a,moneyCandidates(a)),moneyPatternKey(b,moneyCandidates(b)))
  listOf(a.copy(text="김OO 50,000원 출금\n잔액 1,253,400원"),a.copy(channelId="different"),a.copy(packageName="different"),a.copy(text="김OO 50,000원 입금 취소\n잔액 1,253,400원")).forEach{assertNotEquals(moneyPatternKey(a,moneyCandidates(a)),moneyPatternKey(it,moneyCandidates(it)))}
 }
 @Test fun invalidCandidatesAndTemplatesCannotEscapeContract(){
  val c=moneyCandidates(row());val q=moneyQuestions(c,template());val result=answer(q,mapOf("transactionType" to "deposit","transactionAmount" to "M0"))
  result.getJSONObject("answers").getJSONObject("transactionAmount").put("choice","invented")
  assertTrue(runCatching{assembleMoney(row(),c,result,q,JevEngine(context),100)}.isFailure)
  assertTrue(runCatching{validateMoneyTemplate(template().put("extra","invented"))}.isFailure)
  assertTrue(runCatching{validateMoneyTemplate(template().put("schema","investment"))}.isFailure)
  assertNull(moneyCandidates(row(text="999999999999999999999999원 입금")).amounts.firstOrNull())
 }
 @Test fun persistentEventsAndCacheDoNotReplaceOriginals()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,InboxDatabase::class.java).build()
  var designs=0;var judgments=0
  val engine=JevEngine(context){payload->judgments++;answer(payload.getJSONObject("questions"),mapOf("transactionType" to "deposit","transactionAmount" to "M0","balanceAfter" to "M1"))}
  try{
   val p=MoneyPipeline(context,db,engine,designer={designs++;template()})
   val a=row();val b=row("b","이OO 60,000원 입금\n잔액 1,263,400원")
   db.notifications().insert(a);db.notifications().insert(b)
   p.process(a);p.process(a)
   // A new pipeline instance uses the persisted template, not an in-memory cache.
   MoneyPipeline(context,db,engine,designer={designs++;template()}).process(b)
   assertEquals(1,designs);assertEquals(2,judgments)
   assertEquals(50000L,db.structured().money("a")!!.transactionAmount);assertEquals(60000L,db.structured().money("b")!!.transactionAmount)
   assertEquals(a,db.notifications().find("a"))
   db.notifications().delete("a");assertNull(db.structured().money("a"));assertNull(db.structured().processing("a"));assertNotNull(db.structured().money("b"))
  }finally{db.close()}
 }
 @Test fun plainMessageAndUnclassifiedRawNeverBecomeStructuredEvents()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,InboxDatabase::class.java).build()
  try{
   val p=MoneyPipeline(context,db,JevEngine(context){error("No AI expected")},designer={error("No LLM expected")})
   val chat=row(text="오늘 저녁에 볼까?",pkg="com.kakao.talk",title="친구")
   db.notifications().insert(chat);p.process(chat)
   assertEquals("ignored",db.structured().processing(chat.snapshotId)!!.status)
   assertEquals(0,db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM structured_events").use{it.moveToFirst();it.getInt(0)})
   assertNotNull(db.notifications().find(chat.snapshotId))
  }finally{db.close()}
 }
 @Test fun failedTemplateHasPersistentCooldown()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,InboxDatabase::class.java).build();var calls=0
  try{
   val p=MoneyPipeline(context,db,designer={calls++;throw IllegalStateException("fake failure")},clock={1000})
   val r=row();db.notifications().insert(r)
   assertTrue(runCatching{p.process(r)}.isFailure);assertTrue(runCatching{p.process(r)}.isFailure);assertEquals(1,calls)
   assertNull(db.structured().money(r.snapshotId));assertNotNull(db.notifications().find(r.snapshotId))
  }finally{db.close()}
 }
 @Test fun accountHintsAreRedactedBeforeExternalAnalysis(){
  val c=moneyCandidates(row(text="계좌 123-456-789012 입금 50,000원"))
  assertFalse(c.external(JevEngine(context)).getString("text").contains("123-456-789012"))
  assertTrue(c.accounts.all{it.text.endsWith("9012")&&!it.text.contains("123")})
 }
 @Test fun exportSyntheticContractForOptInLiveVerification(){
  val path=System.getProperty("money.export") ?: return
  val cases=listOf(row("A"),row("B","100,000원 출금\n잔액 900,000원"),row("C","스타벅스 6,500원 승인",title="삼성카드"),row("D","김OO님이 50,000원을 보냈습니다.","com.kakao.talk","카카오페이"),row("E","쿠팡 32,500원 환불 완료"),row("F","오늘 저녁에 볼까?","com.kakao.talk","친구"),row("G","스타벅스 6,500원 결제 시 할인",title="광고"),row("H","내일 50,000원 입금할게","com.kakao.talk","친구"))
  val out=JSONObject().put("schema",moneyTemplateSchema()).put("prompt",moneyStructurePrompt).put("cases",JSONArray(cases.map{r->val c=moneyCandidates(r);JSONObject().put("id",r.snapshotId).put("possible",moneyPossible(r,emptyList())).put("key",moneyPatternKey(r,c)).put("state",c.external(JevEngine(context)).put("sourceApp",r.appLabel)).put("questions",moneyQuestions(c,template()))}))
  File(path).writeText(out.toString())
 }
}
