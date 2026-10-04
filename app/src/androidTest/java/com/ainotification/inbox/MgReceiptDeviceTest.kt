package com.ainotification.inbox

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.io.File

class MgReceiptDeviceTest {
 @Test fun capturedMgReceiptsAreInSmartAndNow():Unit=runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as InboxApplication
  val dao=app.database.structured();val anchor=System.currentTimeMillis();var after=Long.MIN_VALUE;var id=""
  val mg=mutableListOf<CapturedNotification>()
  while(true){val page=dao.replayPage(anchor,after,id);if(page.isEmpty())break
   mg+=page.filter{it.packageName=="com.smg.spbs" && !it.isGroupSummary}
   after=page.last().capturedTime;id=page.last().snapshotId
  }
  assertTrue("MG receipts must be present",mg.isNotEmpty())
  var complete=0
  for(row in mg){val event=dao.money(row.snapshotId) ?: continue
   if(event.transactionAmount!=null && event.balanceAfter!=null && !event.counterparty.isNullOrBlank())complete++
  }
  assertEquals(mg.size,complete)
  val now=latestNowBySource(mg.filter{isWithinNowWindow(it,anchor)},app.selection.state.value.getJSONObject("results"))
  assertTrue("MG must be included in Now projection",now.isNotEmpty())
  File(app.filesDir,"mg-repair-summary.json").writeText(JSONObject().put("captured",mg.size).put("complete",complete).put("nowVisible",now.size).toString())
 }
}
