package com.ainotification.inbox

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NowSelectionQueueTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private fun row(id: Int) = previewNotifications()[0].copy(snapshotId = "queue-$id", capturedTime = id.toLong())
    private fun memoryDb() = Room.inMemoryDatabaseBuilder(context, InboxDatabase::class.java).build()

    @Test fun blockedSelectionDoesNotBlockStorageAndAll258AreDelivered() = runBlocking {
        val db = memoryDb()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val all = CompletableDeferred<Unit>()
        val selected = ConcurrentHashMap.newKeySet<String>()
        val queue = NowSelectionQueue(db, accept = {
            entered.complete(Unit); release.await()
            selected.add(it.snapshotId)
            if (selected.size == 258) all.complete(Unit)
            true
        })
        val saved = CompletableDeferred<Unit>()
        var count = 0
        val pipeline = NotificationCapturePipeline<CapturedNotification>(scope,
            save = { queue.save(it, true); count++; if (count == 258) saved.complete(Unit) },
            onSaved = { queue.wake() }, media = { it }, attach = {})
        try {
            queue.start(scope)
            assertTrue(pipeline.submit(row(0)))
            withTimeout(5000) { entered.await() }
            // One storage worker plus its 256 slots accommodates these remaining items.
            for (id in 1..257) {
                while (!pipeline.submit(row(id))) yield()
            }
            withTimeout(10000) { saved.await() }
            assertEquals(258, db.notifications().readAll().size)
            assertTrue(selected.isEmpty())
            release.complete(Unit)
            withTimeout(10000) { all.await() }
            queue.drain()
            assertTrue(db.nowQueue().batch().isEmpty())
            assertEquals(258, selected.size)
        } finally { pipeline.close(); scope.coroutineContext[Job]!!.cancelAndJoin(); db.close() }
    }

    @Test fun restartRecoversLiveOnlyAndDeletionCascades() = runBlocking {
        val name = "now-queue-restart-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, InboxDatabase::class.java, name).build()
        var db = open()
        try {
            val before = NowSelectionQueue(db, accept = { error("Not started") })
            before.save(row(1), true); before.save(row(2), false); before.save(row(3), true)
            db.notifications().delete(row(3).snapshotId)
            db.close(); db = open()
            val selected = mutableListOf<String>()
            val after = NowSelectionQueue(db, accept = { selected.add(it.snapshotId); true })
            after.drain()
            assertEquals(listOf(row(1).snapshotId), selected)
            assertEquals(2, db.notifications().readAll().size)
            assertTrue(db.nowQueue().batch().isEmpty())
            after.drain(); assertEquals(1, selected.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun rejectedAndThrowingHandoffsStayPendingWhileOtherRowsProgress() = runBlocking {
        val db = memoryDb()
        var fail = true
        val delivered = mutableListOf<String>()
        val queue = NowSelectionQueue(db, accept = {
            if (fail && it.snapshotId == row(1).snapshotId) false
            else if (fail && it.snapshotId == row(2).snapshotId) error("write failure")
            else { delivered.add(it.snapshotId); true }
        })
        try {
            (1..3).forEach { queue.save(row(it), true) }
            queue.drain()
            assertEquals(listOf(row(3).snapshotId), delivered)
            assertEquals(2, db.nowQueue().batch().size)
            fail = false; queue.drain()
            assertEquals(3, delivered.size)
            assertTrue(db.nowQueue().batch().isEmpty())
        } finally { db.close() }
    }

    @Test fun enqueueFailureRollsBackOriginal() = runBlocking {
        val db = memoryDb()
        try {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_now BEFORE INSERT ON pending_now_selection BEGIN SELECT RAISE(ABORT, 'test'); END")
            try { NowSelectionQueue(db, accept = { true }).save(row(1), true); fail("Expected rollback") }
            catch (_: android.database.sqlite.SQLiteException) { }
            assertNull(db.notifications().find(row(1).snapshotId))
        } finally { db.close() }
    }
}
