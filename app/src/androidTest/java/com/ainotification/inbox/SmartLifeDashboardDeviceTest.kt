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
import java.io.File

class SmartLifeDashboardDeviceTest {
 @Test fun dashboardMoneySectionsAndSourceAreNavigable(){
  val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext.applicationContext as InboxApplication
  val now=System.currentTimeMillis()
  fun money(id:String,type:String,amount:Long,title:String,status:String="completed")=StructuredEntry(StructuredEvent(id,id,"money",title,"테스트은행","synthetic.bank",now,false,now,now),MoneyEvent(id,id,type,directionFor(type),amount,if(type=="deposit")1253400 else null,if(type=="payment")title else null,if(type!="payment")title else null,"테스트은행",if(type=="payment")"체크카드" else null,"1234-**-5678",null,"테스트은행",1.0,1.0,"complete",true,now,now,status=status))
  val entries=listOf(money("pay","payment",6500,"카페"),money("in","deposit",50000,"김OO"),money("bill","bill",186000,"관리비","pending"),
   StructuredEntry(StructuredEvent("delivery","delivery","delivery","주문상품","쇼핑앱","shop",now,false,now,now),null,life=LifeEvent("delivery","delivery","shipping","주문상품","쇼핑앱","order-001")),
   StructuredEntry(StructuredEvent("appointment","appointment","schedule","치과 예약","문자","sms",now,true,now,now),null,life=LifeEvent("appointment","schedule","confirmed","치과 예약","치과",scheduledAt=now+3600000,dateText="오늘 오후 2:00")))
  val original=previewNotifications()[0].copy(title="원문 제목",text="테스트 원본 결제 안내",bigText=null,messagesJson="[]")
  val device=UiDevice.getInstance(inst);device.wakeUp()
  fun find(tag:String):UiObject2 = device.wait(Until.findObject(By.res(tag)),10000) ?: error("Missing $tag")
  fun shot(name:String){device.waitForIdle();device.takeScreenshot(File(app.cacheDir,"smart-life-$name.png"))}
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{a->a.setShowWhenLocked(true);a.setTurnScreenOn(true);a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);a.setContent{InboxTheme{SmartDashboard(app,true,Modifier.fillMaxSize().systemBarsPadding().semantics{testTagsAsResourceId=true},{},entries,{original})}};a.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
   find("money_shortcut_0");shot("main")
   assertFalse(device.hasObject(By.text("투자")));assertFalse(device.hasObject(By.text("건강")))
   find("money_shortcut_0").click();find("smart_item_in");assertFalse(device.hasObject(By.res("smart_item_bill")));shot("accounts")
   device.wait(Until.findObject(By.text("‹ 뒤로")),5000)!!.click();find("money_shortcut_1").click();find("smart_item_pay");assertFalse(device.hasObject(By.res("smart_item_bill")))
   find("smart_filter_카드").click();find("smart_item_pay");shot("transactions")
   device.wait(Until.findObject(By.text("‹ 뒤로")),5000)!!.click();find("money_shortcut_2").click();find("smart_item_bill");assertTrue(device.hasObject(By.text("미처리")));shot("obligations")
   device.wait(Until.findObject(By.text("‹ 뒤로")),5000)!!.click();find("money_shortcut_3").click();assertTrue(device.wait(Until.hasObject(By.text("확인된 결제 지출")),5000));shot("statistics")
   device.wait(Until.findObject(By.text("‹ 뒤로")),5000)!!.click();find("smart_item_pay").click();assertFalse(device.hasObject(By.text("테스트 원본 결제 안내")))
   val originalButton=By.res("smart_original")
   repeat(4){if(!device.hasObject(originalButton))device.swipe(500,1600,500,600,20)}
   find("smart_original").click();assertTrue(device.wait(Until.hasObject(By.text("테스트 원본 결제 안내")),5000));shot("detail")
   scenario.onActivity{it.setShowWhenLocked(false);it.setTurnScreenOn(false);it.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
  }
 }
}
