package com.ainotification.inbox

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="structured_events",indices=[Index("sourceNotificationId",unique=true)],foreignKeys=[ForeignKey(entity=CapturedNotification::class,parentColumns=["snapshotId"],childColumns=["sourceNotificationId"],onDelete=ForeignKey.CASCADE)])
data class StructuredEvent(@PrimaryKey val id:String,val sourceNotificationId:String,val category:String,val title:String,val sourceApp:String,val sourcePackage:String,val observedAt:Long,val isChanged:Boolean=false,val createdAt:Long,val updatedAt:Long)
@Entity(tableName="money_events",foreignKeys=[ForeignKey(entity=StructuredEvent::class,parentColumns=["id"],childColumns=["id"],onDelete=ForeignKey.CASCADE)])
data class MoneyEvent(@PrimaryKey val id:String,val sourceNotificationId:String,val transactionType:String,val direction:String,
 val transactionAmount:Long?,val balanceAfter:Long?,val merchant:String?,val counterparty:String?,val provider:String?,val paymentMethod:String?,val accountHint:String?,val occurredAt:Long?,val sourceApp:String,
 val confidence:Double,val subtypeConfidence:Double,val extractionStatus:String,val originalTextAvailable:Boolean,val createdAt:Long,val updatedAt:Long,
 @ColumnInfo(defaultValue="0") val recurring:Boolean=false,
 @ColumnInfo(defaultValue="'completed'") val status:String="completed",
 val dueAt:Long?=null,val referenceKey:String?=null,val settledByTransactionEventId:String?=null,val spendCategory:String?=null)
data class StructuredEntry(@Embedded val event:StructuredEvent,@Relation(parentColumn="id",entityColumn="id") val money:MoneyEvent?,@Relation(parentColumn="id",entityColumn="id") val context:EventContext?=null,@Relation(parentColumn="id",entityColumn="id") val life:LifeEvent?=null)
@Entity(tableName="money_patterns")
data class MoneyPattern(@PrimaryKey val key:String,val templateJson:String?,val retryAt:Long,val createdAt:Long,val updatedAt:Long)
@Entity(tableName="structured_processing",foreignKeys=[ForeignKey(entity=CapturedNotification::class,parentColumns=["snapshotId"],childColumns=["sourceNotificationId"],onDelete=ForeignKey.CASCADE)])
data class StructuredProcessing(@PrimaryKey val sourceNotificationId:String,val status:String,val retryAt:Long=Long.MAX_VALUE,val attempts:Int=0)
@Dao interface StructuredDao {
 @Upsert suspend fun saveLife(event:LifeEvent)
 @Query("UPDATE structured_events SET isChanged=1,updatedAt=:at WHERE id=:id") suspend fun markChanged(id:String,at:Long)
 @Query("SELECT * FROM structured_events WHERE id=:id") suspend fun event(id:String):StructuredEvent?
 @Query("SELECT * FROM money_events WHERE referenceKey=:ref AND provider=:provider AND sourceNotificationId!=:source") suspend fun relatedMoney(ref:String,provider:String,source:String):List<MoneyEvent>
 @Query("UPDATE money_events SET status=:status, updatedAt=:at WHERE id=:id") suspend fun setMoneyStatus(id:String,status:String,at:Long)
 @Query("UPDATE life_events SET status=:status WHERE id=:id") suspend fun setLifeStatus(id:String,status:String)
 @Query("SELECT * FROM life_events WHERE kind=:kind AND referenceKey=:ref AND provider=:provider") suspend fun relatedLife(kind:String,ref:String,provider:String):List<LifeEvent>

 @Query("DELETE FROM structured_events WHERE sourcePackage = :ownPackage") suspend fun removeOwnEvents(ownPackage:String)
 @Query("SELECT COUNT(*) FROM notifications WHERE capturedTime<=:anchor") suspend fun replayCount(anchor:Long):Int
 @Query("SELECT * FROM notifications WHERE capturedTime<=:anchor AND (capturedTime>:after OR (capturedTime=:after AND snapshotId>:id)) ORDER BY capturedTime,snapshotId LIMIT 16") suspend fun replayPage(anchor:Long,after:Long,id:String):List<CapturedNotification>
 @Query("SELECT n.* FROM notifications n WHERE n.isGroupConversation=1 AND n.isGroupSummary=0 AND n.capturedTime<=:anchor AND (n.capturedTime>:after OR (n.capturedTime=:after AND n.snapshotId>:id)) AND ((COALESCE(n.text,'') || COALESCE(n.bigText,'') || n.messagesJson) LIKE '%미팅%' OR (COALESCE(n.text,'') || COALESCE(n.bigText,'') || n.messagesJson) LIKE '%회의%' OR (COALESCE(n.text,'') || COALESCE(n.bigText,'') || n.messagesJson) LIKE '%약속%') AND NOT EXISTS (SELECT 1 FROM structured_events e WHERE e.id='event:' || n.snapshotId) ORDER BY n.capturedTime,n.snapshotId LIMIT 16") suspend fun missingMeetingPage(anchor:Long,after:Long,id:String):List<CapturedNotification>
 @Query("SELECT n.* FROM notifications n WHERE n.isGroupSummary=0 AND n.capturedTime<=:anchor AND (n.capturedTime>:after OR (n.capturedTime=:after AND n.snapshotId>:id)) AND (COALESCE(n.title,'') || COALESCE(n.text,'') || COALESCE(n.bigText,'') || n.messagesJson) LIKE '%광고%' AND NOT EXISTS (SELECT 1 FROM structured_events e WHERE e.id='event:' || n.snapshotId) ORDER BY n.capturedTime,n.snapshotId LIMIT 16") suspend fun missingShoppingAdPage(anchor:Long,after:Long,id:String):List<CapturedNotification>
 @Query("SELECT n.* FROM notifications n WHERE n.isGroupSummary=0 AND n.capturedTime<=:anchor AND (n.capturedTime>:after OR (n.capturedTime=:after AND n.snapshotId>:id)) AND NOT EXISTS (SELECT 1 FROM structured_events e WHERE e.sourceNotificationId=n.snapshotId) ORDER BY n.capturedTime,n.snapshotId LIMIT 32") suspend fun missingSchedulePage(anchor:Long,after:Long,id:String):List<CapturedNotification>
 @Query("DELETE FROM structured_events WHERE sourceNotificationId=:id") suspend fun removeEvent(id:String)
 @Query("DELETE FROM structured_processing WHERE sourceNotificationId=:id") suspend fun resetProcessing(id:String)

 @Transaction @Query("SELECT * FROM structured_events ORDER BY observedAt DESC, id") fun observe():Flow<List<StructuredEntry>>
 @Query("SELECT * FROM money_events WHERE sourceNotificationId=:id") suspend fun money(id:String):MoneyEvent?
 @Upsert suspend fun insertEvent(value:StructuredEvent)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveMoney(value:MoneyEvent)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveProcessing(value:StructuredProcessing)
 @Query("SELECT * FROM structured_processing WHERE sourceNotificationId=:id") suspend fun processing(id:String):StructuredProcessing?
 @Query("SELECT * FROM notifications n WHERE NOT EXISTS (SELECT 1 FROM structured_processing p WHERE p.sourceNotificationId=n.snapshotId AND p.retryAt>:now) ORDER BY n.capturedTime DESC LIMIT 16") suspend fun pending(now:Long):List<CapturedNotification>
 @Query("SELECT * FROM money_patterns WHERE `key`=:key") suspend fun pattern(key:String):MoneyPattern?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun savePattern(value:MoneyPattern)
 @Query("SELECT COUNT(*) FROM notifications n WHERE NOT EXISTS (SELECT 1 FROM structured_processing p WHERE p.sourceNotificationId=n.snapshotId AND p.status IN ('done','ignored','insufficient_context'))") fun outstanding():Flow<Int>
}
internal val structuredMigration=object:Migration(6,7){override fun migrate(db:SupportSQLiteDatabase){
 db.execSQL("CREATE TABLE IF NOT EXISTS structured_events (id TEXT NOT NULL PRIMARY KEY, sourceNotificationId TEXT NOT NULL, category TEXT NOT NULL, title TEXT NOT NULL, sourceApp TEXT NOT NULL, sourcePackage TEXT NOT NULL, observedAt INTEGER NOT NULL, isChanged INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(sourceNotificationId) REFERENCES notifications(snapshotId) ON DELETE CASCADE)")
 db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_structured_events_sourceNotificationId ON structured_events(sourceNotificationId)")
 db.execSQL("CREATE TABLE IF NOT EXISTS money_events (id TEXT NOT NULL PRIMARY KEY, sourceNotificationId TEXT NOT NULL, transactionType TEXT NOT NULL, direction TEXT NOT NULL, transactionAmount INTEGER, balanceAfter INTEGER, merchant TEXT, counterparty TEXT, provider TEXT, paymentMethod TEXT, accountHint TEXT, occurredAt INTEGER, sourceApp TEXT NOT NULL, confidence REAL NOT NULL, subtypeConfidence REAL NOT NULL, extractionStatus TEXT NOT NULL, originalTextAvailable INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(id) REFERENCES structured_events(id) ON DELETE CASCADE)")
 db.execSQL("CREATE TABLE IF NOT EXISTS money_patterns (`key` TEXT NOT NULL PRIMARY KEY, templateJson TEXT, retryAt INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
 db.execSQL("CREATE TABLE IF NOT EXISTS structured_processing (sourceNotificationId TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL, retryAt INTEGER NOT NULL, attempts INTEGER NOT NULL, FOREIGN KEY(sourceNotificationId) REFERENCES notifications(snapshotId) ON DELETE CASCADE)")
}}
