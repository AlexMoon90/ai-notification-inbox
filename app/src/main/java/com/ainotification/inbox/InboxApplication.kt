package com.ainotification.inbox

import android.app.Application
import android.app.ActivityOptions
import android.content.Context
import android.os.Build
import android.app.PendingIntent
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow


class InboxApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this, InboxDatabase::class.java, "inbox.db").addMigrations(object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notifications ADD COLUMN conversationIdentity TEXT")
        }
    }, hubMigration, avatarMigration).build() }
    internal val images by lazy { NotificationImages(this) }
    internal val hub by lazy { HubClassifier(database, JevEngine(this)) }
    val repository by lazy { NotificationRepository(database.notifications()) }
    val listenerConnected = MutableStateFlow(false)
    val captureError = MutableStateFlow<String?>(null)
    val opener = NotificationOpener()
    internal val selection by lazy { NotificationSelection(this, scope) }
    internal val recommendations by lazy { ContextRecommendationEngine(JevEngine(this)) }
    internal val policies by lazy { PolicyController(this, scope, selection) }
    val setupDrafts by lazy { SetupDrafts(this, scope) }
}

class NotificationOpener(private val capacity: Int = 500) {
    // Retain each captured token, not the latest token for an Android notification key.
    // Removing a notification is not the same as its creator canceling the PendingIntent.
    private val targets = object : LinkedHashMap<String, PendingIntent>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PendingIntent>?) = size > capacity
    }
    @Synchronized fun update(row: CapturedNotification, intent: PendingIntent?) {
        if (intent != null) targets[row.snapshotId] = intent
    }
    @Synchronized fun clear() { targets.clear() }
    @Synchronized fun hasTarget(row: CapturedNotification) = targets.containsKey(row.snapshotId)
    @Synchronized fun open(row: CapturedNotification, context: Context): Boolean {
        val intent = targets[row.snapshotId] ?: return false
        return try {
            // A successful send is not proof of an Activity launch. On API 34+ the
            // visible sender must explicitly contribute its launch privileges.
            intent.send(context, 0, null, null, null, null, notificationLaunchOptions()?.toBundle())
            true
        } catch (_: PendingIntent.CanceledException) {
            targets.remove(row.snapshotId)
            false
        } catch (_: SecurityException) { false }
    }
}

internal fun notificationLaunchOptions(): ActivityOptions? {
    if (Build.VERSION.SDK_INT < 34) return null
    return ActivityOptions.makeBasic().apply {
        @Suppress("DEPRECATION")
        pendingIntentBackgroundActivityStartMode = if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
        } else {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
    }
}
