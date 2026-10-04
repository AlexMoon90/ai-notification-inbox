package com.ainotification.inbox

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** IDs only: the original and its delivery obligation commit together. */
@Entity(tableName = "pending_now_selection", foreignKeys = [ForeignKey(
    entity = CapturedNotification::class, parentColumns = ["snapshotId"],
    childColumns = ["notificationId"], onDelete = ForeignKey.CASCADE)])
data class PendingNowSelection(@PrimaryKey val notificationId: String)

@Dao
interface NowSelectionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(value: PendingNowSelection)
    @Query("SELECT n.* FROM notifications n INNER JOIN pending_now_selection p ON n.snapshotId = p.notificationId ORDER BY n.capturedTime, n.snapshotId LIMIT 32")
    suspend fun batch(): List<CapturedNotification>
    @Query("DELETE FROM pending_now_selection WHERE notificationId = :id")
    suspend fun finish(id: String)
}

internal val nowSelectionMigration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS pending_now_selection (notificationId TEXT NOT NULL, PRIMARY KEY(notificationId), FOREIGN KEY(notificationId) REFERENCES notifications(snapshotId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        // Do not enqueue historical notifications or infer whether they were live.
    }
}

internal class NowSelectionQueue(
    private val db: InboxDatabase,
    private val accept: suspend (CapturedNotification) -> Boolean,
    private val failed: () -> Unit = {},
) {
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val drainLock = Mutex()
    private var worker: Job? = null

    suspend fun save(row: CapturedNotification, live: Boolean) {
        db.withTransaction {
            db.notifications().insert(row)
            if (live) db.nowQueue().enqueue(PendingNowSelection(row.snapshotId))
        }
        // UI indexing is recoverable and must never roll back a captured original.
        runCatching { indexNotification(db,row) }
    }

    fun wake() { wakeups.trySend(Unit) }

    @Synchronized fun start(scope: CoroutineScope) {
        if (worker?.isActive == true) return
        worker = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try { drain() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { failed() }
                withTimeoutOrNull(30_000) { wakeups.receive() }
            }
        }
    }

    /** A rejected/failed handoff stays durable; other rows in this batch can progress. */
    internal suspend fun drain() = drainLock.withLock {
        do {
            val batch = db.nowQueue().batch()
            var retry = false
            for (row in batch) {
                currentCoroutineContext().ensureActive()
                try {
                    if (db.notifications().find(row.snapshotId) == null) continue
                    if (accept(row)) db.nowQueue().finish(row.snapshotId)
                    else { retry = true; failed() }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { retry = true; failed() }
            }
            if (retry) break
        } while (batch.isNotEmpty())
    }
}
