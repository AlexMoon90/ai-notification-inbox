package com.ainotification.inbox

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Test
import org.junit.Assert.*

class DebitConfirmationDeviceTest {
 @Test fun confirmUndoAndRejectFromMoneyList(){
  val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext.applicationContext as InboxApplication
  val now=System.currentTimeMillis()-600000
  fun row(id:String,type:String)=StructuredEntry(StructuredEvent(id,id,"money",id,"테스트",if(type=="payment")"com.samsung.android.spay" else "bank",now,false,now,now),MoneyEvent(id,id,type,directionFor(type),6500,null,"테스트 가맹점",null,"테스트은행",null,"123**",now,"테스트",1.0,1.0,"complete",true,now,now))
  val rows=listOf(row("p","payment"),row("w","withdrawal"))
  val displayed=androidx.compose.runtime.mutableStateOf(rows)
  val device=UiDevice.getInstance(inst);device.wakeUp()
  fun find(tag:String)=device.wait(Until.findObject(By.res(tag)),10000) ?: error("Missing $tag")
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{a->a.setShowWhenLocked(true);a.setTurnScreenOn(true);a.setContent{InboxTheme{SmartDashboard(app,true,Modifier.fillMaxSize().systemBarsPadding().semantics{testTagsAsResourceId=true},{},displayed.value,{null})}}}
   find("money_shortcut_1").click();find("debit_question");device.waitForIdle();find("debit_yes").click()
   assertTrue(device.wait(Until.gone(By.res("debit_question")),5000))
   val next=rows.map{e->e.copy(event=e.event.copy(id=e.event.id+"2",sourceNotificationId=e.event.sourceNotificationId+"2",observedAt=now+300000),money=e.money!!.copy(id=e.money.id+"2",sourceNotificationId=e.money.sourceNotificationId+"2",occurredAt=now+300000))}
   scenario.onActivity{displayed.value=rows+next}
   find("smart_item_p2")
   assertFalse(device.hasObject(By.res("debit_question")))
   find("smart_item_p2").click()
   assertTrue(device.wait(Until.hasObject(By.text("체크카드")),5000))
   device.wait(Until.findObject(By.text("결제 연결 다시 확인")),5000)!!.click()
   find("debit_no").click()
   assertFalse(device.hasObject(By.text("체크카드")))
   assertTrue(device.hasObject(By.text("결제 연결 다시 확인")))
   scenario.onActivity{it.setShowWhenLocked(false);it.setTurnScreenOn(false)}
  }
 }
}
