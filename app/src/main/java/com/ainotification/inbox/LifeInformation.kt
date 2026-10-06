package com.ainotification.inbox

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.*

@Entity(tableName="life_events",foreignKeys=[ForeignKey(entity=StructuredEvent::class,parentColumns=["id"],childColumns=["id"],onDelete=ForeignKey.CASCADE)])
data class LifeEvent(@PrimaryKey val id:String,val kind:String,val status:String,val title:String,
 val provider:String,val referenceKey:String?=null,val itemName:String?=null,val carrier:String?=null,
 val scheduledAt:Long?=null,val dateText:String?=null,val place:String?=null,val participant:String?=null,
 val value:Long?=null,val unit:String?=null)

internal val lifeMigration=object:Migration(9,10){override fun migrate(db:SupportSQLiteDatabase){
 db.execSQL("ALTER TABLE money_events ADD COLUMN recurring INTEGER NOT NULL DEFAULT 0")
 db.execSQL("ALTER TABLE money_events ADD COLUMN status TEXT NOT NULL DEFAULT 'completed'")
 for(column in listOf("dueAt INTEGER","referenceKey TEXT","settledByTransactionEventId TEXT","spendCategory TEXT"))db.execSQL("ALTER TABLE money_events ADD COLUMN $column")
 db.execSQL("UPDATE money_events SET recurring=1, transactionType='payment' WHERE transactionType='recurring_payment'")
 db.execSQL("UPDATE money_events SET status='pending' WHERE transactionType IN ('billing','bill','payment_request','scheduled_payment','transfer_in_pending')")
 db.execSQL("UPDATE money_events SET status='unknown' WHERE extractionStatus!='complete'")
 db.execSQL("CREATE TABLE IF NOT EXISTS life_events (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, status TEXT NOT NULL, title TEXT NOT NULL, provider TEXT NOT NULL, referenceKey TEXT, itemName TEXT, carrier TEXT, scheduledAt INTEGER, dateText TEXT, place TEXT, participant TEXT, value INTEGER, unit TEXT, FOREIGN KEY(id) REFERENCES structured_events(id) ON DELETE CASCADE)")
}}

internal fun literalField(text:String,label:String):String?=Regex("(?:^|\\n)(?:$label)\\s*[:：]\\s*([^\\n]{1,80})").find(text)?.groupValues?.get(1)?.trim()
internal fun literalReference(text:String):String?=Regex("(?:주문번호|예약번호|청구번호|거래번호)\\s*[:：]\\s*([A-Za-z0-9-]{4,40})(?=\\s|$)").find(text)?.groupValues?.get(1)
internal fun fullDate(text:String,requireTime:Boolean=false):Long? {
 val m=Regex("(20\\d{2})[-/.년]\\s*(\\d{1,2})[-/.월]\\s*(\\d{1,2})(?:일)?(?:[ T]+(\\d{1,2}):(\\d{2}))?").find(text) ?: return null
 if(requireTime && m.groupValues[4].isEmpty())return null
 return runCatching{LocalDateTime.of(m.groupValues[1].toInt(),m.groupValues[2].toInt(),m.groupValues[3].toInt(),m.groupValues[4].toIntOrNull()?:23,m.groupValues[5].toIntOrNull()?:59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrNull()
}
internal fun moneyLifecycle(row:CapturedNotification,m:MoneyEvent):MoneyEvent {
 val t=row.currentMessageText();val recurring=m.recurring || m.transactionType=="recurring_payment" || Regex("정기결제|정기 결제|구독 결제").containsMatchIn(t)
 val type=if(m.transactionType=="recurring_payment")"payment" else m.transactionType
 val obligation=type in setOf("billing","bill","payment_request","scheduled_payment","overdue")
 val status=when{m.extractionStatus!="complete"->"unknown";type=="cancellation"->"cancelled";type=="overdue"->"overdue";obligation||type=="transfer_in_pending"->"pending";else->m.status}
 val method=m.paymentMethod ?: when{Regex("체크카드|체크 카드|debit",RegexOption.IGNORE_CASE).containsMatchIn(t)->"체크카드";Regex("신용카드|신용 카드").containsMatchIn(t)->"신용카드";m.provider?.contains("페이")==true->"페이";m.provider?.endsWith("카드")==true->"카드";type in setOf("transfer_in","transfer_out")->"송금";else->null}
 return m.copy(transactionType=type,recurring=recurring,status=status,paymentMethod=method,referenceKey=literalReference(t) ?: m.referenceKey,dueAt=if(obligation)literalField(t,"납부기한|납부 기한|마감일")?.let{fullDate(it)} else m.dueAt)
}
internal fun explicitObligation(row:CapturedNotification,now:Long):MoneyEvent? {
 if(row.isGroupSummary || row.packageName=="com.ainotification.inbox" || row.packageName in excludedNotificationPackages)return null
 // Human dialogue continues through the contextual Jev gate.
 if(messageLike(row))return null
 val t=moneyCandidates(row).text
 if(Regex("광고|예시|납부 완료|결제 완료|납부완료|자동납부 예정").containsMatchIn(t))return null
 val marker=Regex("청구서|납부 요청|납부요청|미납요금|미납 요금|납부할 금액").containsMatchIn(t)
 if(!marker)return null
 val labeled=literalField(t,"청구금액|납부금액|납부할 금액|미납금액") ?: return null
 val amount=Regex("^([0-9,]+)원$").matchEntire(labeled)?.groupValues?.get(1)?.replace(",","")?.toLongOrNull() ?: return null
 val party=literalField(t,"청구처|납부처|항목")
 val type=if(t.contains("미납"))"overdue" else "bill"
 return moneyLifecycle(row,MoneyEvent("event:${row.snapshotId}",row.snapshotId,type,"neutral",amount,null,party,null,receiptProvider(t) ?: row.appLabel,null,receiptAccountHint(row),null,row.appLabel,1.0,1.0,"complete",true,now,now))
}
internal fun extractLife(row:CapturedNotification):LifeEvent? {
 if(row.isGroupSummary || row.packageName=="com.ainotification.inbox" || row.packageName in excludedNotificationPackages || messageLike(row))return null
 val t=moneyCandidates(row).text
 if(Regex("광고|예시|이벤트 참여").containsMatchIn(t))return null
 val ref=literalReference(t)
 val item=literalField(t,"상품명|상품|주문상품")
 val provider=row.appLabel
 val id="event:${row.snapshotId}"
 val delivery=when{t.contains("반품 완료")->"returned";t.contains("반품 접수")->"returning";t.contains("환불 완료")->"refunded";Regex("배송 완료|배송완료|배송이 완료|배송을 완료|배달 완료").containsMatchIn(t)->"delivered";Regex("오늘 도착|금일 도착").containsMatchIn(t)->"arriving_today";Regex("배송 시작|배송을 시작|배송중|배송 중|배송 출발|집화 완료").containsMatchIn(t)->"shipping";Regex("배송 준비|상품 준비").containsMatchIn(t)->"preparing";Regex("주문 완료|주문 접수|주문완료").containsMatchIn(t)->"ordered";else->null}
 if(delivery!=null && (item!=null || ref!=null || row.packageName=="com.coupang.mobile"))return LifeEvent(id,"delivery",delivery,item ?: "$provider 주문",provider,ref,item,literalField(t,"택배사|배송사"),literalField(t,"도착예정|도착 예정|배송예정일")?.let{fullDate(it)},literalField(t,"도착예정|도착 예정|배송예정일"))
 val date=literalField(t,"예약일시|예약 일시|일정|방문일시")
 if(date!=null && Regex("예약|방문 일정").containsMatchIn(t)) {
  val status=when{Regex("예약 취소|취소 완료").containsMatchIn(t)->"cancelled";t.contains("변경")->"changed";Regex("예약 확정|예약 완료|예약완료").containsMatchIn(t)->"confirmed";else->"candidate"}
  return LifeEvent(id,"schedule",status,literalField(t,"예약명|예약 항목|일정명") ?: "$provider 예약",provider,ref,scheduledAt=fullDate(date,requireTime=true),dateText=date,place=literalField(t,"장소|방문 장소"),participant=literalField(t,"담당자|참석자"))
 }
 if(Regex("체결 완료|주문 체결").containsMatchIn(t) && literalField(t,"종목명|종목")!=null)return LifeEvent(id,"investment","filled",literalField(t,"종목명|종목")!!,provider,ref)
 if(Regex("가격 알림").containsMatchIn(t) && literalField(t,"종목명|종목")!=null)return LifeEvent(id,"investment","price",literalField(t,"종목명|종목")!!,provider)
 val steps=Regex("(?:오늘|걸음 수)\\s*[:：]?\\s*([0-9,]+)걸음").find(t)?.groupValues?.get(1)?.replace(",","")?.toLongOrNull()
 val customKind=when{literalField(t,"과제명")!=null && t.contains("제출")->"school";literalField(t,"고객명")!=null && t.contains("요청")->"customer";literalField(t,"프로젝트명")!=null && t.contains("마감")->"project";else->null}
 if(customKind!=null)return LifeEvent(id,customKind,"pending",literalField(t,"과제명|프로젝트명|요청내용") ?: "고객 요청",provider,ref,dateText=literalField(t,"마감일|제출기한"),participant=literalField(t,"고객명"))
 if(steps!=null && row.packageName in setOf("com.sec.android.app.shealth","com.google.android.apps.fitness"))return LifeEvent(id,"health","recorded","걸음 수",provider,value=steps,unit="걸음")
 return null
}

internal suspend fun persistLife(db:InboxDatabase,row:CapturedNotification,life:LifeEvent,now:Long) {
 db.withTransaction {
  if(db.notifications().find(row.snapshotId)==null)return@withTransaction
  // Stable order/reservation reference only. Never join merely by title or amount.
  val prior=life.referenceKey?.let{db.structured().relatedLife(life.kind,it,life.provider)}.orEmpty()
  val changed=prior.any{it.status!=life.status} || (life.kind=="schedule" && life.status in setOf("changed","cancelled","change_requested","cancellation_requested"))
  db.structured().insertEvent(StructuredEvent(life.id,row.snapshotId,life.kind,life.title,row.appLabel,row.packageName,row.postedTime,changed,now,now))
  db.structured().saveLife(life);db.structured().saveProcessing(StructuredProcessing(row.snapshotId,"done"))
 }
}
internal suspend fun settleObligations(db:InboxDatabase,m:MoneyEvent) {
 val key=m.referenceKey ?: return;val provider=m.provider ?: return
 val completed=m.status=="completed" && m.transactionType in setOf("payment","transfer_out","withdrawal")
 val related=db.structured().relatedMoney(key,provider,m.sourceNotificationId)
 if(completed) {
  val requests=related.filter{it.transactionType in setOf("bill","billing","payment_request","overdue") && it.transactionAmount==m.transactionAmount && it.status in setOf("pending","processing","overdue") && (it.accountHint==null || m.accountHint==null || it.accountHint==m.accountHint)}
  if(requests.size==1){db.structured().saveMoney(requests.single().copy(status="completed",settledByTransactionEventId=m.id,updatedAt=m.updatedAt));db.structured().markChanged(requests.single().id,m.updatedAt)}
 } else if(m.transactionType in setOf("bill","billing","payment_request","overdue")) {
  val payments=related.filter{it.status=="completed" && it.transactionType in setOf("payment","transfer_out","withdrawal") && it.transactionAmount==m.transactionAmount && (it.accountHint==null || m.accountHint==null || it.accountHint==m.accountHint)}
  if(payments.size==1)db.structured().saveMoney(m.copy(status="completed",settledByTransactionEventId=payments.single().id))
 }
}

/** A literal current request is only promoted after the conversation relevance/amount gate. */
internal fun isContextualPaymentRequest(text:String,sourceId:String,d:ConversationDecision):Boolean {
 if(d.intent!="money" || d.status=="insufficient_context" || d.amount?.sourceId!=sourceId || d.amount.amount==null || d.confidence<.9)return false
 if(Regex("광고|예시|예를|이라고|라고|보냈|완료|취소|필요 없|말아|보내지|납부하지|송금하지").containsMatchIn(text))return false
 return Regex("(?:입금|송금|납부|결제)(?:을|를)?\\s*(?:부탁|해\\s*주세요|해주세요)|(?:보내|입금해|송금해|납부해)\\s*주세요").containsMatchIn(text)
}
