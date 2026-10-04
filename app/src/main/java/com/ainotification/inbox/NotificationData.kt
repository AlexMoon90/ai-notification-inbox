package com.ainotification.inbox

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.*

// Exclude only these system sources, not every preinstalled/system app.
internal val excludedNotificationPackages = listOf("com.android.systemui", "android")

// Product default: phone status cards are not inbox events; missed calls remain eligible.
internal fun CapturedNotification.isExcludedCallStatus(): Boolean {
    val channel=channelId.orEmpty().lowercase().replace(Regex("[^a-z0-9]"),"")
    if(notificationCategory=="missed_call" || channel in setOf("missedcall","missedcalls"))return false
    return packageName in setOf("com.samsung.android.incallui","com.samsung.android.dialer")
}

@Entity(tableName = "notifications", indices = [Index("postedTime"), Index("notificationKey")])
data class CapturedNotification(
    @PrimaryKey val snapshotId: String,
    val packageName: String,
    val appLabel: String,
    val notificationKey: String,
    val notificationId: Int,
    val postedTime: Long,
    val capturedTime: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val conversationTitle: String?,
    val messagesJson: String,
    val groupKey: String?,
    val channelId: String?,
    val hasContentIntent: Boolean,
    val isGroupSummary: Boolean,
    val availableFields: String,
    val conversationIdentity: String? = null,
    val notificationCategory: String? = null,
    val serviceType: String? = null,
    val personIdentity: String? = null,
    val isGroupConversation: Boolean? = null,
    val conversationAvatarFile: String? = null,
) {
    fun hasEmptyContent(): Boolean = listOf(title,text,bigText,subText,conversationTitle).all{it.isNullOrBlank()} &&
        runCatching { val messages=org.json.JSONArray(messagesJson); (0 until messages.length()).all { i ->
            val m=messages.getJSONObject(i)
            (m.isNull("text") || m.optString("text").isBlank()) && (m.isNull("mimeType") || m.optString("mimeType").isBlank())
        } }.getOrDefault(false)
    fun preview(): String = currentMessageText().takeIf { it.isNotBlank() } ?: "본문이 제공되지 않은 알림"
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE packageName NOT IN (:excludedPackages) ORDER BY postedTime DESC, capturedTime DESC LIMIT 100")
    fun observeRecent(excludedPackages: List<String> = excludedNotificationPackages): Flow<List<CapturedNotification>>
    @Query("SELECT * FROM notifications WHERE packageName NOT IN (:excludedPackages) ORDER BY postedTime DESC, capturedTime DESC")
    fun observeAll(excludedPackages: List<String> = excludedNotificationPackages): Flow<List<CapturedNotification>>
    @Query("SELECT * FROM notifications WHERE packageName NOT IN (:excludedPackages) AND capturedTime <= :anchor ORDER BY postedTime DESC, capturedTime DESC, snapshotId LIMIT :limit OFFSET :offset")
    fun observePage(anchor:Long,offset:Int,limit:Int,excludedPackages:List<String> = excludedNotificationPackages):Flow<List<CapturedNotification>>
    @Query("SELECT COUNT(*) FROM notifications WHERE packageName NOT IN (:excludedPackages)")
    fun observeCount(excludedPackages: List<String> = excludedNotificationPackages): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: CapturedNotification)
    @Query("SELECT * FROM notifications WHERE snapshotId = :id LIMIT 1")
    suspend fun find(id: String): CapturedNotification?
    @Query("UPDATE notifications SET messagesJson = :messages, conversationAvatarFile = :avatar WHERE snapshotId = :id")
    suspend fun attachMedia(id:String,messages:String,avatar:String?)
    @Query("DELETE FROM notifications WHERE snapshotId = :id")
    suspend fun delete(id: String)
    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY postedTime DESC LIMIT 1")
    suspend fun latestFrom(packageName: String): CapturedNotification?
    @Query("SELECT n.* FROM notifications n LEFT JOIN hub_classifications h ON n.snapshotId = h.notificationId WHERE h.notificationId IS NULL AND n.capturedTime >= :since ORDER BY n.capturedTime ASC LIMIT 32")
    suspend fun unclassified(since:Long): List<CapturedNotification>
    @Query("SELECT * FROM notifications ORDER BY postedTime DESC")
    suspend fun readAll(): List<CapturedNotification>
}

@Database(entities = [CapturedNotification::class, HubClassification::class, PendingNowSelection::class, NotificationDisplay::class, MessageDisplay::class, StructuredEvent::class, MoneyEvent::class, MoneyPattern::class, StructuredProcessing::class, ConversationThread::class, EventContext::class], version = 9, exportSchema = true)
abstract class InboxDatabase : RoomDatabase() { abstract fun notifications(): NotificationDao; abstract fun hub(): HubDao; abstract fun nowQueue(): NowSelectionDao; abstract fun display():DisplayDao; abstract fun structured():StructuredDao; abstract fun conversations():ConversationDao }

// The listener depends only on this sink; future AI work consumes stored snapshots separately.
interface NotificationSink { suspend fun save(notification: CapturedNotification) }
class NotificationRepository(private val dao: NotificationDao, scope:kotlinx.coroutines.CoroutineScope?=null,private val db:InboxDatabase?=null) : NotificationSink {
    private val bodies=object:LinkedHashMap<String,CapturedNotification>(32,.75f,true){}
    private fun bodyBytes(row:CapturedNotification)=(row.messagesJson.length+row.text.orEmpty().length+row.bigText.orEmpty().length)*2L
    suspend fun loadBody(id:String):CapturedNotification? {
        synchronized(bodies){bodies[id]?.let{return it}}
        val row=dao.find(id) ?: return null
        synchronized(bodies){
            bodies[id]=row
            while(bodies.size>32 || bodies.values.sumOf(::bodyBytes)>4*1024*1024){bodies.remove(bodies.keys.first())}
        }
        return row
    }
    val recent = dao.observeRecent().map { rows -> rows.filterNot { it.isExcludedCallStatus() } }
    val all = (db?.display()?.observeRows()?.map{rows->rows.map{it.row}} ?: dao.observeAll()).map{rows->rows.filterNot{it.isExcludedCallStatus()}}
        .let{source->if(scope==null)source else source.shareIn(scope,SharingStarted.WhileSubscribed(5000,0),1)}
    val count = dao.observeCount()
    override suspend fun save(notification: CapturedNotification) { dao.insert(notification) }
    suspend fun attachMedia(row:CapturedNotification) {
        synchronized(bodies){bodies.remove(row.snapshotId)}
        dao.attachMedia(row.snapshotId,row.messagesJson,row.conversationAvatarFile)
        db?.let{indexNotification(it,row)}
    }
    suspend fun clear() { synchronized(bodies){bodies.clear()};dao.deleteAll() }
}
