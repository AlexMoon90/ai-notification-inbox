package com.ainotification.inbox

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private var accessGranted by mutableStateOf(false)
    private var unavailableRow by mutableStateOf<CapturedNotification?>(null)
    private val app get() = application as InboxApplication
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            InboxTheme {
                InboxScreen(app, accessGranted, ::openSettings, ::openNotification)
                unavailableRow?.let { row ->
                    AlertDialog(
                        onDismissRequest = { unavailableRow = null },
                        title = { Text("대화 바로가기를 사용할 수 없어요") },
                        text = { Text("알림의 연결 정보가 남아 있지 않거나 원래 앱에서 취소했습니다. ${row.appLabel} 앱을 열어 직접 확인할 수 있습니다. 해당 대화로 바로 이동하지는 않습니다.") },
                        confirmButton = { TextButton(onClick = {
                            unavailableRow = null
                            openSourceApp(row.packageName)
                        }) { Text("앱 열기") } },
                        dismissButton = { TextButton(onClick = { unavailableRow = null }) { Text("취소") } },
                    )
                }
            }
        }
    }
    internal fun openNotification(row: CapturedNotification) {
        // Called only by an explicit tap while this Activity is resumed.
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
        if (!accessGranted || !app.opener.open(row, this)) unavailableRow = row
    }
    private fun openSourceApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                Toast.makeText(this, "이 앱을 실행할 수 없습니다. 앱 목록에서 확인해 주세요.", Toast.LENGTH_LONG).show()
            } else startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "앱이 설치되어 있는지 확인해 주세요.", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "시스템에서 앱 실행을 제한했습니다.", Toast.LENGTH_LONG).show()
        }
    }
    override fun onResume() {
        super.onResume()
        val component = ComponentName(this, InboxNotificationListener::class.java)
        accessGranted = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
        if (!accessGranted) app.opener.clear()
    }
    private fun openSettings() {
        try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "설정에서 ‘알림 접근’을 검색해 주세요.", Toast.LENGTH_LONG).show()
        }
    }
}

internal fun formatTime(time: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))
internal fun detailFields(row: CapturedNotification): List<Pair<String, String>> = listOf(
    "packageName" to row.packageName, "appLabel" to row.appLabel,
    "notificationKey" to row.notificationKey, "notificationId" to row.notificationId.toString(),
    "postedTime" to "${row.postedTime} (${formatTime(row.postedTime)})",
    "capturedTime" to formatTime(row.capturedTime), "title" to row.title,
    "text" to row.text, "bigText" to row.bigText, "subText" to row.subText,
    "conversationTitle" to row.conversationTitle, "MessagingStyle messages" to row.messagesJson,
    "groupKey" to row.groupKey, "channelId" to row.channelId,
    "contentIntent 존재 (수집 당시)" to row.hasContentIntent.toString(),
    "isGroupSummary" to row.isGroupSummary.toString(), "제공된 extras 필드" to row.availableFields,
    "snapshotId" to row.snapshotId,
).map { (key, value) -> key to (value ?: "제공되지 않음") }
