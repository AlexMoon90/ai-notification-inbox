package com.ainotification.inbox

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class NowAlertsTest {
    private val row get()=previewNotifications()[0].copy(packageName="example.source",postedTime=100,capturedTime=100,isGroupSummary=false)
    private fun result(status:String,action:String="SHOW")=JSONObject().put("status",status).put("action",action)
    @Test fun onlyNowShowDecisionsAlert() {
        assertTrue(shouldAlertNow(row,result("match"),100,"own",now=100))
        listOf(result("outside","HIDE"),result("match","QUIET"),result("pending"),result("unconfigured"),result("review")).forEach {
            assertFalse(shouldAlertNow(row,it,100,"own",now=100))
        }
    }
    @Test fun hidingShadePreservesConfiguredCallAndAlarmExceptions() {
        val p=android.service.notification.ZenPolicy.Builder().allowAlarms(true)
            .allowCalls(android.service.notification.ZenPolicy.PEOPLE_TYPE_CONTACTS)
            .allowMessages(android.service.notification.ZenPolicy.PEOPLE_TYPE_NONE)
            .allowConversations(android.service.notification.ZenPolicy.CONVERSATION_SENDERS_NONE)
            .showPeeking(false).showInNotificationList(true).showStatusBarIcons(true).build()
        val next=hideOrdinaryNotifications(p)
        assertEquals(p.priorityCallSenders,next.priorityCallSenders)
        assertEquals(p.priorityCategoryAlarms,next.priorityCategoryAlarms)
        assertEquals(p.priorityMessageSenders,next.priorityMessageSenders)
        assertEquals(p.priorityConversationSenders,next.priorityConversationSenders)
        assertEquals(p.visualEffectPeek,next.visualEffectPeek)
        assertEquals(p.visualEffectBadge,next.visualEffectBadge)
        assertEquals(android.service.notification.ZenPolicy.STATE_DISALLOW,next.visualEffectNotificationList)
        assertEquals(android.service.notification.ZenPolicy.STATE_DISALLOW,next.visualEffectStatusBar)
        assertEquals(next,hideOrdinaryNotifications(next))
    }
    @Test fun disabledPolicyCannotEnableDnd() {
        assertFalse(hasActiveNowPolicy(null))
        assertFalse(hasActiveNowPolicy(JSONObject("{\"rules\":[]}")))
        assertFalse(hasActiveNowPolicy(JSONObject("{\"rules\":[{\"enabled\":false}]}")))
        assertTrue(hasActiveNowPolicy(JSONObject("{\"rules\":[{\"enabled\":true}]}")))
    }
    @Test fun historySelfAndSummariesNeverReplay() {
        val show=result("match")
        assertFalse(shouldAlertNow(row,show,0,"own",now=100+NOW_WINDOW_MS))
        assertFalse(shouldAlertNow(row,show,101,"own",now=100))
        assertFalse(shouldAlertNow(row.copy(postedTime=99),show,100,"own",now=100))
        assertFalse(shouldAlertNow(row.copy(capturedTime=99),show,100,"own",now=100))
        assertFalse(shouldAlertNow(row.copy(packageName="own"),show,100,"own",now=100))
        assertFalse(shouldAlertNow(row.copy(isGroupSummary=true),show,100,"own",now=100))
    }
}
