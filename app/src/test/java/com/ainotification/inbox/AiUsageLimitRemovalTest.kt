package com.ainotification.inbox

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class AiUsageLimitRemovalTest {
 private val context=ApplicationProvider.getApplicationContext<Context>()
 @Test fun dailyJudgmentAndStructureContinuePastFormerCapsAndKeepCounts(){
  val db=Room.inMemoryDatabaseBuilder(context,InboxDatabase::class.java).build()
  val prefs=context.getSharedPreferences("money-extraction-budget",0)
  val day=LocalDate.now().toString()
  try {
   prefs.edit().putInt("$day:judgment",200).putInt("$day:structure",20).commit()
   val pipeline=MoneyPipeline(context,db,JevEngine(context){error("Accounting must not call network")})
   repeat(3){pipeline.recordCall("judgment");pipeline.recordCall("structure")}
   assertEquals(203,prefs.getInt("$day:judgment",0));assertEquals(23,prefs.getInt("$day:structure",0))
  }finally{prefs.edit().clear().commit();db.close()}
 }
 @Test fun explicitReplayContinuesPastFormerCapsWithoutStartingReplay(){
  val replay=StructuredReplay(context)
  try {
   replay.prefs.edit().putInt("calls:judgment",12000).putInt("calls:structure",500).putInt("calls",12500).commit()
   replay.charge("judgment");replay.charge("structure")
   assertEquals(12001,replay.prefs.getInt("calls:judgment",0));assertEquals(501,replay.prefs.getInt("calls:structure",0))
   assertEquals(12502,replay.progress.value.calls);assertFalse(replay.progress.value.active)
  }finally{replay.prefs.edit().clear().commit()}
 }
}
