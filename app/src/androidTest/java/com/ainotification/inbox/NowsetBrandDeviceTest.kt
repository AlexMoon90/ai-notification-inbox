package com.ainotification.inbox

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Only synthetic previews and brand assets are captured; real inbox stays FLAG_SECURE. */
class NowsetBrandDeviceTest {
 @Test fun brandAndExistingNavigationRender(){
  val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext.applicationContext as InboxApplication
  val device=UiDevice.getInstance(inst);device.wakeUp()
  fun find(tag:String)=device.wait(Until.findObject(By.res(tag)),10000) ?: error("Missing $tag")
  fun shot(name:String){device.waitForIdle();assertTrue(device.takeScreenshot(File(app.cacheDir,"nowset-$name.png")))}
  assertEquals("NOWSET",app.packageManager.getApplicationLabel(app.applicationInfo).toString())
  ActivityScenario.launch(DesignPreviewActivity::class.java).use{scenario->
   scenario.onActivity{a->a.setShowWhenLocked(true);a.setTurnScreenOn(true)}
   find("nav_0");shot("now")
   find("nav_1").click();find("nav_1");shot("messages")
   find("nav_2").click();find("smart_category_money");shot("smart")
   find("open_settings").click();find("nowset_about").click()
   find("nowset_logo");device.waitForIdle();Thread.sleep(350);shot("brand-light")
   device.wait(Until.findObject(By.text("닫기")),5000)!!.click()
   scenario.onActivity{a->a.setContent{InboxTheme{Column(Modifier.fillMaxSize().systemBarsPadding().background(NowsetColors.DeepNavy).semantics{testTagsAsResourceId=true}){NowsetAboutContent(true);Text("다크 브랜드",color=NowsetColors.White)}}}}
   find("nowset_logo");device.waitForIdle();Thread.sleep(350);shot("brand-dark")
   scenario.onActivity{it.setShowWhenLocked(false);it.setTurnScreenOn(false)}
  }
  val drawable=app.packageManager.getApplicationIcon(app.packageName)
  val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);drawable.setBounds(0,0,512,512);drawable.draw(Canvas(bitmap))
  File(app.cacheDir,"nowset-launcher.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
 }
}
