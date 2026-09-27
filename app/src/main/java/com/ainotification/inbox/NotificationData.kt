package com.ainotification.inbox

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Exclude only these system sources, not every preinstalled/system app.
internal val excludedNotificationPackages = listOf("com.android.systemui", "android")

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
    @Query("SELECT * FROM notifications WHERE packageName NOT IN (:excludedPackages) ORDER BY postedTime DESC, capturedTime DESC LIMIT 500")
    fun observeRecent(excludedPackages: List<String> = excludedNotificationPackages): Flow<List<CapturedNotification>>
    @Query("SELECT * FROM notifications WHERE packageName NOT IN (:excludedPackages) ORDER BY postedTime DESC, capturedTime DESC")
    fun observeAll(excludedPackages: List<String> = excludedNotificationPackages): Flow<List<CapturedNotification>>
    @Query("SELECT COUNT(*) FROM notifications WHERE packageName NOT IN (:excludedPackages)")
    fun observeCount(excludedPackages: List<String> = excludedNotificationPackages): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: CapturedNotification)
    @Query("SELECT * FROM notifications WHERE snapshotId = :id LIMIT 1")
    suspend fun find(id: String): CapturedNotification?
    @Query("DELETE FROM notifications WHERE snapshotId = :id")
    suspend fun delete(id: String)
    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY postedTime DESC LIMIT 1")
    suspend fun latestFrom(packageName: String): CapturedNotification?
    @Query("SELECT * FROM notifications ORDER BY postedTime DESC")
    suspend fun readAll(): List<CapturedNotification>
}

@Database(entities = [CapturedNotification::class, HubClassification::class], version = 4, exportSchema = true)
abstract class InboxDatabase : RoomDatabase() { abstract fun notifications(): NotificationDao; abstract fun hub(): HubDao }

// The listener depends only on this sink; future AI work consumes stored snapshots separately.
interface NotificationSink { suspend fun save(notification: CapturedNotification) }
class NotificationRepository(private val dao: NotificationDao) : NotificationSink {
    val recent = dao.observeRecent()
    val all = dao.observeAll()
    val count = dao.observeCount()
    override suspend fun save(notification: CapturedNotification) = dao.insert(notification)
    suspend fun clear() = dao.deleteAll()
}
