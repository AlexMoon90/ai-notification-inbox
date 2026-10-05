package com.ainotification.inbox

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Explicit local repair only. No notification replay, policy mutation, or external AI. */
class MeetingBackfillDeviceTest {
 @Test fun restoreMissingMeetingNotices():Unit=runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as InboxApplication
  val anchor=System.currentTimeMillis()
  val restored=repairGroupMeetings(app.database,anchor)
  val repeated=repairGroupMeetings(app.database,anchor)
  assertEquals(0,repeated)
  File(app.filesDir,"meeting-backfill.json").writeText(JSONObject().put("restored",restored).put("secondPass",repeated).put("externalCalls",0).toString())
 }
}
