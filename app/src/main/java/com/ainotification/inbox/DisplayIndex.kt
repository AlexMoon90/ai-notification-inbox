package com.ainotification.inbox

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

internal const val DISPLAY_PREVIEW="|display-preview"
@Entity(tableName="notification_display",primaryKeys=["snapshotId"],indices=[Index("postedTime")],foreignKeys=[ForeignKey(entity=CapturedNotification::class,parentColumns=["snapshotId"],childColumns=["snapshotId"],onDelete=ForeignKey.CASCADE)])
data class NotificationDisplay(@Embedded val row:CapturedNotification)
@Entity(tableName="message_display",primaryKeys=["roomId","identity"],indices=[Index("sourceId")],foreignKeys=[ForeignKey(entity=CapturedNotification::class,parentColumns=["snapshotId"],childColumns=["sourceId"],onDelete=ForeignKey.CASCADE)])
data class MessageDisplay(val roomId:String,val identity:String,val sourceId:String,val capturedTime:Long,
    val sender:String?,val text:String,val time:Long,val imageFile:String?,val hasImage:Boolean,val senderAvatarFile:String?)
@Dao interface DisplayDao {
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(value:NotificationDisplay)
    @Query("SELECT * FROM notification_display WHERE packageName NOT IN ('android','com.android.systemui') ORDER BY postedTime DESC,capturedTime DESC,snapshotId") fun observeRows():Flow<List<NotificationDisplay>>
    @Query("SELECT * FROM message_display ORDER BY time,identity") fun observeMessages():Flow<List<MessageDisplay>>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertMessage(value:MessageDisplay)
    @Query("UPDATE message_display SET sourceId=CASE WHEN :capturedTime>=capturedTime THEN :sourceId ELSE sourceId END, imageFile=CASE WHEN :capturedTime>=capturedTime THEN COALESCE(:imageFile,imageFile) ELSE COALESCE(imageFile,:imageFile) END,senderAvatarFile=CASE WHEN :capturedTime>=capturedTime THEN COALESCE(:senderAvatarFile,senderAvatarFile) ELSE COALESCE(senderAvatarFile,:senderAvatarFile) END,capturedTime=MAX(capturedTime,:capturedTime) WHERE roomId=:roomId AND identity=:identity")
    suspend fun enrichMessage(roomId:String,identity:String,sourceId:String,capturedTime:Long,imageFile:String?,senderAvatarFile:String?)
    suspend fun mergeMessage(roomId:String,identity:String,sourceId:String,capturedTime:Long,sender:String?,text:String,time:Long,imageFile:String?,hasImage:Boolean,senderAvatarFile:String?) {
        insertMessage(MessageDisplay(roomId,identity,sourceId,capturedTime,sender,text,time,imageFile,hasImage,senderAvatarFile))
        enrichMessage(roomId,identity,sourceId,capturedTime,imageFile,senderAvatarFile)
    }
    @Query("SELECT n.* FROM notifications n LEFT JOIN notification_display d ON n.snapshotId=d.snapshotId WHERE d.snapshotId IS NULL ORDER BY n.capturedTime,n.snapshotId LIMIT 32") suspend fun missing():List<CapturedNotification>
}

internal fun stableMessageIdentity(value:String):String {
    val bytes=java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    val digits="0123456789abcdef"
    return "sha256:"+buildString(64){for(b in bytes){val n=b.toInt() and 255;append(digits[n ushr 4]);append(digits[n and 15])}}
}

/** Rebuildable read model. Raw sources and policy decisions are never replaced by this preview. */
internal suspend fun indexNotification(db:InboxDatabase,input:CapturedNotification) {
    db.withTransaction {
        // Media enrichment may already have committed since a backfill batch was read.
        val row=db.notifications().find(input.snapshotId) ?: return@withTransaction
        val room=conversationRooms(listOf(row)).firstOrNull()
        val latest=row.latestMessage()?.let { JSONObject(it.toString()).apply {
            put("text",optString("text").take(240))
            room?.latest?.let{put("displayIdentity",it.identity)}
        } }
        val preview=row.copy(text=row.preview().take(240),bigText=null,subText=row.subText?.take(240),
            messagesJson=if(latest==null)"[]" else JSONArray().put(latest).toString(),availableFields=row.availableFields+DISPLAY_PREVIEW)
        room?.messages?.forEach { m->db.display().mergeMessage(room.id,m.identity,row.snapshotId,row.capturedTime,m.sender,m.text.take(240),m.time,m.imageFile,m.hasImage,m.senderAvatarFile) }
        db.display().save(NotificationDisplay(preview))
    }
}

internal class DisplayIndexer(private val db:InboxDatabase) {
    private val lock=Mutex()
    private val wake=Channel<Unit>(Channel.CONFLATED)
    val ready=MutableStateFlow(false)
    fun wake(){wake.trySend(Unit)}
    fun start(scope:CoroutineScope){scope.launch(Dispatchers.IO){
        while(isActive){
            try { lock.withLock { do {
                val rows=db.display().missing()
                for(row in rows){ensureActive();indexNotification(db,row)}
            }while(rows.isNotEmpty());ready.value=true } }
            catch(e:CancellationException){throw e}
            catch(_:Exception){ready.value=false}
            withTimeoutOrNull(3000){wake.receive()}
        }
    }}
}
internal fun indexedRooms(rows:List<CapturedNotification>,messages:List<MessageDisplay>):List<HubRoom> {
    val sources=rows.associateBy{it.snapshotId}
    return messages.groupBy{it.roomId}.mapNotNull { (id,items)->
        val projected=items.mapNotNull { m->sources[m.sourceId]?.let { row->
            HubMessage(row,m.sender,m.text,m.time,m.identity,m.imageFile,m.hasImage,m.senderAvatarFile)
        } }.sortedBy{it.time}
        val latest=projected.lastOrNull() ?: return@mapNotNull null
        HubRoom(id,messageService(latest.source) ?: return@mapNotNull null,notificationDisplayTitle(latest.source),projected,
            projected.asReversed().firstNotNullOfOrNull{it.source.conversationAvatarFile})
    }.sortedByDescending{it.latest.time}
}

internal val displayMigration=object:Migration(5,6){override fun migrate(db:SupportSQLiteDatabase){
    db.execSQL("CREATE TABLE IF NOT EXISTS `notification_display` (`snapshotId` TEXT NOT NULL, `packageName` TEXT NOT NULL, `appLabel` TEXT NOT NULL, `notificationKey` TEXT NOT NULL, `notificationId` INTEGER NOT NULL, `postedTime` INTEGER NOT NULL, `capturedTime` INTEGER NOT NULL, `title` TEXT, `text` TEXT, `bigText` TEXT, `subText` TEXT, `conversationTitle` TEXT, `messagesJson` TEXT NOT NULL, `groupKey` TEXT, `channelId` TEXT, `hasContentIntent` INTEGER NOT NULL, `isGroupSummary` INTEGER NOT NULL, `availableFields` TEXT NOT NULL, `conversationIdentity` TEXT, `notificationCategory` TEXT, `serviceType` TEXT, `personIdentity` TEXT, `isGroupConversation` INTEGER, `conversationAvatarFile` TEXT, PRIMARY KEY(`snapshotId`), FOREIGN KEY(`snapshotId`) REFERENCES `notifications`(`snapshotId`) ON UPDATE NO ACTION ON DELETE CASCADE)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_display_postedTime ON notification_display(postedTime)")
    db.execSQL("CREATE TABLE IF NOT EXISTS message_display (roomId TEXT NOT NULL,identity TEXT NOT NULL,sourceId TEXT NOT NULL,capturedTime INTEGER NOT NULL,sender TEXT,text TEXT NOT NULL,time INTEGER NOT NULL,imageFile TEXT,hasImage INTEGER NOT NULL,senderAvatarFile TEXT,PRIMARY KEY(roomId,identity),FOREIGN KEY(sourceId) REFERENCES notifications(snapshotId) ON UPDATE NO ACTION ON DELETE CASCADE)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_message_display_sourceId ON message_display(sourceId)")
}}
