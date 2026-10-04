package com.ainotification.inbox

import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.withTransaction
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import java.io.File

/** Local-only operation: no Jev/LLM/pipeline calls; preserves all nonmatching rows and Now decisions. */
class LocalReceiptRepairDeviceTest {
 @Test fun applyExplicitLocalReceipts():Unit=runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as InboxApplication
  val db=app.database;val anchor=System.currentTimeMillis();var after=Long.MIN_VALUE;var id="";var reviewed=0;var matched=0;var named=0;var providers=0;var kakaoPay=0;var withAccount=0;val mgAccounts=mutableSetOf<String>()
  db.structured().removeOwnEvents(app.packageName)
  while(true){
   val rows=db.structured().replayPage(anchor,after,id);if(rows.isEmpty())break
   for(row in rows){
    val money=explicitFinancialReceipt(row,anchor)?.let{ReceiptSenders(app).enrich(row,it)}
    if(money!=null)db.withTransaction {
     if(db.notifications().find(row.snapshotId)!=null){
      db.structured().removeEvent(row.snapshotId)
      db.structured().insertEvent(StructuredEvent(money.id,row.snapshotId,"money",moneyLabels.getValue(money.transactionType),row.appLabel,row.packageName,row.postedTime,false,anchor,anchor))
      db.structured().saveMoney(money);db.structured().saveProcessing(StructuredProcessing(row.snapshotId,"done"));matched++;if(money.merchant!=null || money.counterparty!=null)named++;if(money.provider!=null)providers++;if(money.provider=="카카오페이")kakaoPay++;if(money.accountHint!=null){withAccount++;if(money.provider=="새마을금고")mgAccounts+=money.accountHint}
     }
    }
    reviewed++;after=row.capturedTime;id=row.snapshotId
   }
  }
  val ownCount=db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM structured_events WHERE sourcePackage = ?",arrayOf(app.packageName)).use{it.moveToFirst();it.getInt(0)}
  org.junit.Assert.assertEquals(0,ownCount)
  File(app.filesDir,"local-receipt-repair.json").writeText(JSONObject().put("reviewed",reviewed).put("matched",matched).put("externalCalls",0).put("withParty",named).put("withProvider",providers).put("kakaoPay",kakaoPay).put("ownEvents",ownCount).put("withAccount",withAccount).put("mgAccountHints",mgAccounts.size).toString())
 }
}
