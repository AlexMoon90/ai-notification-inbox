package com.ainotification.inbox

import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.withTransaction
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import java.io.File

/** Local-only upgrade: preserve originals, context judgments, Now decisions and seen markers. */
class SmartLifeBackfillDeviceTest {
 @Test fun enrichExistingStructuredInformation():Unit=runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as InboxApplication
  val db=app.database;val dao=db.structured();val anchor=System.currentTimeMillis();var after=Long.MIN_VALUE;var id="";var scanned=0;var moneyCount=0;var lifeCount=0
  while(true){
   val rows=dao.replayPage(anchor,after,id);if(rows.isEmpty())break
   for(row in rows){
    val old=dao.money(row.snapshotId)
    val money=old?.let{moneyLifecycle(row,it)} ?: explicitObligation(row,anchor)
    if(money!=null)db.withTransaction {
     if(db.notifications().find(row.snapshotId)!=null){
      if(old==null)dao.insertEvent(StructuredEvent(money.id,row.snapshotId,"money",moneyLabels.getValue(money.transactionType),row.appLabel,row.packageName,row.postedTime,false,anchor,anchor))
      dao.saveMoney(money);settleObligations(db,money);moneyCount++
     }
    }
    if(money==null)extractLife(row)?.let{persistLife(db,row,it,anchor);lifeCount++}
    scanned++;after=row.capturedTime;id=row.snapshotId
   }
  }
  File(app.filesDir,"smart-life-backfill.json").writeText(JSONObject().put("reviewed",scanned).put("moneyEnriched",moneyCount).put("lifeExtracted",lifeCount).put("externalCalls",0).toString())
 }
}
