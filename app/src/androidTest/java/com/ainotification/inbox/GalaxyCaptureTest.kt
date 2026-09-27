package com.ainotification.inbox

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Requires user-granted notification access and the documented synthetic shell notification.
 * Reads only counts on the device; never exports the database or notification contents.
 */
@RunWith(AndroidJUnit4::class)
class GalaxyCaptureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun syntheticNotificationIsStoredLocally() {
        val file = context.getDatabasePath("inbox.db")
        assertTrue("Local database should exist", file.exists())
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            fun count(sql: String, args: Array<String>? = null): Int = db.rawQuery(sql, args).use { c -> c.moveToFirst(); c.getInt(0) }
            assertTrue("Synthetic shell notification should be captured", count(
                "SELECT COUNT(*) FROM notifications WHERE packageName=? AND text=?",
                arrayOf("com.android.shell", "Synthetic notification capture 001")
            ) >= 1)
            val metrics = Bundle().apply {
                putInt("stored_notification_count", count("SELECT COUNT(*) FROM notifications"))
                putInt("source_package_count", count("SELECT COUNT(DISTINCT packageName) FROM notifications"))
                for (pkg in listOf("com.kakao.talk", "com.google.android.gm", "com.samsung.android.messaging",
                    "com.google.android.apps.messaging", "com.Slack", "org.telegram.messenger", "com.whatsapp")) {
                    putInt("captured_" + pkg, count("SELECT COUNT(*) FROM notifications WHERE packageName=?", arrayOf(pkg)))
                }
            }
            instrumentation.sendStatus(0, metrics)
        }
    }

    @Test fun freshKakaoPendingIntentOpensActivity() {
        val app = context.applicationContext as InboxApplication
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(device.wait(Until.hasObject(By.text("알림 수집 연결됨")), 15000))
            val row = runBlocking { app.database.notifications().latestFrom("com.kakao.talk") }
            assumeTrue("A current Kakao notification is required", row != null && app.opener.hasTarget(row))
            if (android.os.Build.VERSION.SDK_INT >= 36) {
                val options = notificationLaunchOptions()!!
                assertEquals(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE,
                    options.pendingIntentBackgroundActivityStartMode)
            }
            scenario.onActivity { it.openNotification(row!!) }
            assertTrue("Original Kakao PendingIntent must actually bring Kakao to the foreground",
                device.wait(Until.hasObject(By.pkg("com.kakao.talk")), 10000))
        }
    }

    @Test fun kakaoFallbackOffersExplicitAppLaunch() {
        val app = context.applicationContext as InboxApplication
        val row = runBlocking { app.database.notifications().latestFrom("com.kakao.talk") }
        assumeNotNull(row) // This device-specific regression needs an existing Kakao record.
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Simulate a token lost after process death; no DB mutation or content export.
                activity.openNotification(row!!.copy(snapshotId = "missing-navigation-test-token"))
            }
            assertTrue(device.wait(Until.hasObject(By.text("대화 바로가기를 사용할 수 없어요")), 10000))
            device.findObject(By.text("앱 열기")).click()
            assertTrue("Fallback should launch Kakao", device.wait(Until.hasObject(By.pkg("com.kakao.talk")), 10000))
        }
    }

    @Test fun systemSourcesAreExcludedFromCaptureAndInbox() = runBlocking {
        val app = context.applicationContext as InboxApplication
        val device = UiDevice.getInstance(instrumentation)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.text("알림 수집 연결됨")), 15000))
        val listener = InboxNotificationListener.diagnosticInstance
        assertNotNull("Connected debug listener required", listener)
        val dao = app.database.notifications()
        val marker = "system-exclusion-regression"
        val now = System.currentTimeMillis()
        for (pkg in excludedNotificationPackages + "com.android.shell") {
            val n = android.app.Notification.Builder(context, "test").setContentTitle(marker).build()
            val sbn = android.service.notification.StatusBarNotification(
                pkg, pkg, 987654, marker, 1000, 0, 0, n, android.os.Process.myUserHandle(), now)
            instrumentation.runOnMainSync { listener!!.onNotificationPosted(sbn) }
        }
        // The accepted marker follows both excluded callbacks through the same FIFO.
        var captured = false
        repeat(100) {
            if (!captured) {
                captured = dao.latestFrom("com.android.shell")?.title == marker
                if (!captured) kotlinx.coroutines.delay(100)
            }
        }
        assertTrue("Non-excluded notification must still reach storage", captured)
        val all = dao.readAll()
        assertFalse(all.any { it.title == marker && it.packageName in excludedNotificationPackages })
        assertTrue(dao.observeRecent().first().none { it.packageName in excludedNotificationPackages })
        assertEquals(all.count { it.packageName !in excludedNotificationPackages }, dao.observeCount().first())
    }

    @Test fun inboxShowsConnectedListener() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val device = UiDevice.getInstance(instrumentation)
        assertTrue("Inbox title should be visible", device.wait(Until.hasObject(By.text("내 알림")), 10000))
        assertTrue("Notification listener should connect", device.wait(Until.hasObject(By.text("알림 수집 연결됨")), 15000))
    }
}
