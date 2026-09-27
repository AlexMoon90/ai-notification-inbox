package com.ainotification.inbox

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=InboxApplication::class,qualifiers="w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubRenderTest {
    @Test fun renderThreeNativeScreens(){
        for(tab in 0..2){
            val controller=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val a=controller.get();val app=a.application as InboxApplication
            val rows=previewNotifications().take(3).mapIndexed{i,r->r.copy(snapshotId="hub-render-$i",notificationCategory="msg",conversationIdentity="room-$i",serviceType=if(i==1)"SMS" else null)}
            val decisions=JSONObject();rows.forEach{decisions.put(it.snapshotId,JSONObject().put("status","match").put("action","SHOW"))}
            val facts=listOf(HubClassification(rows[0].snapshotId,"[\"SCHEDULE_CHANGE\"]",1),HubClassification(rows[1].snapshotId,"[\"PAYMENT\"]",1))
            a.setContent{InboxTheme{InboxScreen(app,true,{}, {},fixtureRows=rows,fixtureDecisions=decisions,fixtureFacts=facts,initialTab=tab)}}
            repeat(5){shadowOf(android.os.Looper.getMainLooper()).idleFor(100,TimeUnit.MILLISECONDS)}
            val view=a.window.decorView
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(393,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(852,android.view.View.MeasureSpec.EXACTLY));view.layout(0,0,393,852)
            repeat(5){shadowOf(android.os.Looper.getMainLooper()).idleFor(100,TimeUnit.MILLISECONDS)}
            val bitmap=Bitmap.createBitmap(393,852,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
            File("/tmp/hub-native-$tab.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            controller.pause().stop().destroy()
        }
    }
}
