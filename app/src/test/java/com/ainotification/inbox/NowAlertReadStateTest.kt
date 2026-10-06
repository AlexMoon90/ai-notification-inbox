package com.ainotification.inbox

import android.app.*
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class NowAlertReadStateTest {
 private val context=ApplicationProvider.getApplicationContext<Context>()
 private val manager get()=context.getSystemService(NotificationManager::class.java)
 private fun post(tag:String){manager.createNotificationChannel(NotificationChannel("read-test","read",3));manager.notify(tag,1,Notification.Builder(context,"read-test").setSmallIcon(android.R.drawable.ic_dialog_info).setContentText("synthetic").build())}
 @Test fun readingCancelsOnlyMatchedRelayAndPersistsAcrossRecreation(){
  post("now:read");post("now:unread");post("other")
  NowAlertReadState(context,{1000}).acknowledge(listOf("read"))
  assertEquals(setOf("now:unread","other"),manager.activeNotifications.map{it.tag}.toSet())
  assertTrue(NowAlertReadState(context,{1001}).isRead("read"));assertFalse(NowAlertReadState(context,{1001}).isRead("unread"))
 }
 @Test fun startupReconcilesStaleReadNotificationsAndRetentionExpiresReadMarker(){
  val state=NowAlertReadState(context,{1000});state.acknowledge(listOf("already-read"))
  post("now:already-read");post("now:new");state.reconcile()
  assertEquals(listOf("new"),state.activeIds())
  assertFalse(NowAlertReadState(context,{1000+49*3_600_000L}).isRead("already-read"))
 }
}
