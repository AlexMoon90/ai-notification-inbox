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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*


@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class InboxApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val screenStore by lazy { HubScreenStore(scope, source = {
        combine(nowScreenData(repository.all, selection.state),displayIndexer.ready){data,ready->data.copy(loaded=ready)}
    }, classifications = database.hub().observe(), roomSource = combine(repository.all,database.display().observeMessages(),::indexedRooms)) }
    override fun onCreate() {
        super.onCreate()
        // NotificationListenerService also starts this process before the user opens Now.
        screenStore
        displayIndexer.start(scope)
        nowQueue.start(scope)
        structured.start(scope)
        scope.launch { runCatching { nowAlerts.restoreReadState() } }
    }
    val database by lazy { Room.databaseBuilder(this, InboxDatabase::class.java, "inbox.db").addMigrations(object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notifications ADD COLUMN conversationIdentity TEXT")
        }
    }, hubMigration, avatarMigration, nowSelectionMigration, displayMigration, structuredMigration, conversationMigration,relationshipMigration,lifeMigration).build() }
    internal val displayIndexer by lazy { DisplayIndexer(database) }
    internal val nowQueue by lazy { NowSelectionQueue(database, accept = { selection.accept(it) },
        failed = { captureError.value = "일부 알림 판단이 지연되고 있습니다. 원문을 확인해 주세요." }) }
    internal val images by lazy { NotificationImages(this) }
    internal val structured by lazy { MoneyPipeline(this,database) }
    internal val hub by lazy { HubClassifier(database, JevEngine(this), afterClassification={ row,events -> structured.onClassified(row,events) }) }
    val repository by lazy { NotificationRepository(database.notifications(),scope,database) }
    val listenerConnected = MutableStateFlow(false)
    val captureError = MutableStateFlow<String?>(null)
    internal val replyPreferences by lazy { ReplyPreferences(this) }
    val opener = NotificationOpener()
    internal val nowAlerts by lazy { NowAlerts(this) }
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
    private val rooms = object : LinkedHashMap<String, Pair<Long,PendingIntent>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long,PendingIntent>>?) = size > capacity
    }
    private fun roomKey(row:CapturedNotification):String? = row.conversationIdentity?.takeIf{it.isNotBlank() && !row.isGroupSummary}?.let{"${row.packageName}:$it"}
    @Synchronized fun update(row: CapturedNotification, intent: PendingIntent?) {
        if (intent == null || row.isGroupSummary) return
        targets[row.snapshotId] = intent
        roomKey(row)?.let{key->if(row.postedTime >= (rooms[key]?.first ?: Long.MIN_VALUE)){rooms.remove(key);rooms[key]=row.postedTime to intent}}
    }
    @Synchronized fun clear() { targets.clear(); rooms.clear() }
    @Synchronized fun hasTarget(row: CapturedNotification) = targets.containsKey(row.snapshotId) || roomKey(row)?.let{rooms.containsKey(it)}==true
    @Synchronized fun open(row: CapturedNotification, context: Context): Boolean {
        // Only an OS shortcut-derived room identity permits reuse. Titles/Android keys can collide.
        val options=listOfNotNull(targets[row.snapshotId],roomKey(row)?.let{rooms[it]?.second}).distinct()
        for(intent in options)try {
            intent.send(context, 0, null, null, null, null, notificationLaunchOptions()?.toBundle())
            return true
        } catch (_: PendingIntent.CanceledException) {
            targets.entries.removeAll{it.value==intent};rooms.entries.removeAll{it.value.second==intent}
        } catch (_: SecurityException) { }
        return false
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
