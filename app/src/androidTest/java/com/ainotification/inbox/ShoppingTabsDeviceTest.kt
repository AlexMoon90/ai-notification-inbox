package com.ainotification.inbox

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test

class ShoppingTabsDeviceTest {
 @Test fun deliveryRefundAndAdsRemainSeparate(){
  val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext.applicationContext as InboxApplication
  val now=System.currentTimeMillis()
  fun entry(id:String,status:String)=StructuredEntry(StructuredEvent(id,id,"delivery","테스트 $id","테스트 쇼핑","synthetic.shop",now,false,now,now),null,life=LifeEvent(id,"delivery",status,"테스트 $id","테스트 쇼핑"))
  val entries=listOf(entry("shipping","shipping"),entry("completed","delivered"),entry("refund","refunded"),entry("ad","advertisement"))
  val device=UiDevice.getInstance(inst);device.wakeUp()
  fun find(tag:String)=device.wait(Until.findObject(By.res(tag)),10000) ?: error("Missing $tag")
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{a->a.setShowWhenLocked(true);a.setTurnScreenOn(true);a.setContent{InboxTheme{SmartDashboard(app,true,Modifier.fillMaxSize().systemBarsPadding().semantics{testTagsAsResourceId=true},{},entries,{null})}}}
   find("smart_category_delivery").click()
   find("smart_item_shipping");find("smart_item_completed")
   assertFalse(device.hasObject(By.res("smart_item_ad")));assertFalse(device.hasObject(By.res("smart_item_refund")))
   find("shopping_tab_환불").click();find("smart_item_refund")
   assertFalse(device.hasObject(By.res("smart_item_shipping")))
   find("shopping_tab_광고").click();find("smart_item_ad")
   assertFalse(device.hasObject(By.res("smart_item_refund")))
   find("shopping_tab_배송").click();find("smart_item_completed")
   scenario.onActivity{it.setShowWhenLocked(false);it.setTurnScreenOn(false)}
  }
 }
}
