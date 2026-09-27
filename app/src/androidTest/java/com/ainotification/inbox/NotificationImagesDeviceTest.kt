package com.ainotification.inbox

import android.app.Notification
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NotificationImagesDeviceTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val app get()=inst.targetContext.applicationContext as InboxApplication
    @Test fun privatePicturePreviewAndExpand(){
        val image=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(30,100,190))}
        val folder=File(app.cacheDir,"synthetic-images").apply{mkdirs()}
        val isolated=object:ContextWrapper(app){override fun getFilesDir()=folder}
        val store=NotificationImages(isolated)
        val notification=Notification().apply{extras.putParcelable(Notification.EXTRA_PICTURE,image)}
        val row=store.capture(previewNotifications()[0],notification)
        val name=row.latestMessage()!!.getString("imageFile")
        assertNotNull(store.file(name))
        val device=UiDevice.getInstance(inst);device.wakeUp()
        assumeFalse("Unlock to verify image UI",(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked)
        try{ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{a->a.setContent{androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides isolated){InboxTheme{Surface{NotificationImage(name,true)}}}};a.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
            val preview=device.wait(Until.findObject(By.desc("메시지 첨부 이미지")),8000) ?: error("Image missing")
            preview.click();assertNotNull(device.wait(Until.findObject(By.desc("첨부 이미지 크게 보기")),8000))
            device.takeScreenshot(File(app.cacheDir,"image-synthetic-preview.png"))
            device.findObject(By.text("닫기")).click()
            scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
        }}finally{store.clear();folder.deleteRecursively();image.recycle()}
    }
    @Test fun inspectKakaoAttachmentAvailabilityWithoutExportingImages(){
        app.startActivity(android.content.Intent(app,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline=SystemClock.elapsedRealtime()+15000
        while(InboxNotificationListener.diagnosticInstance==null && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(250)
        val listener=InboxNotificationListener.diagnosticInstance
        assertNotNull("Notification listener required",listener)
        val folder=File(app.cacheDir,"kakao-image-probe").apply{mkdirs()}
        val isolated=object:ContextWrapper(app){override fun getFilesDir()=folder}
        val store=NotificationImages(isolated)
        var notifications=0;var imageMessages=0;var readable=0;var pictures=0;var roomAvatars=0;var senderAvatars=0
        try{listener!!.activeNotifications.filter{it.packageName=="com.kakao.talk" && it.notification.flags and Notification.FLAG_GROUP_SUMMARY==0}.forEach {sbn->
            notifications++
            val normalized=NotificationNormalizer(app).normalize(sbn)
            val captured=store.capture(normalized,sbn.notification)
            if(captured.conversationAvatarFile!=null)roomAvatars++
            val a=JSONArray(captured.messagesJson)
            for(i in 0 until a.length())if(a.getJSONObject(i).stringOrNull("senderAvatarFile")!=null)senderAvatars++
            for(i in 0 until a.length())if(a.getJSONObject(i).isImageAttachment()){
                imageMessages++;if(store.file(a.getJSONObject(i).stringOrNull("imageFile"))!=null)readable++
            }
            if(sbn.notification.extras.containsKey(Notification.EXTRA_PICTURE))pictures++
        }}finally{store.clear();folder.deleteRecursively()}
        File(app.cacheDir,"kakao-image-availability.json").writeText(JSONObject().put("active_notifications",notifications).put("image_messages",imageMessages).put("readable_images",readable).put("picture_extras",pictures).put("room_avatars",roomAvatars).put("sender_avatars",senderAvatars).put("raw_images_exported",false).toString(2))
    }
    @Test fun roomAndSenderThumbnailUi(){
        val green=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.GREEN)}
        val blue=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.BLUE)}
        val folder=File(app.cacheDir,"synthetic-avatars").apply{mkdirs()}
        val isolated=object:ContextWrapper(app){override fun getFilesDir()=folder}
        val store=NotificationImages(isolated)
        val person=androidx.core.app.Person.Builder().setName("민수").setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(green)).build()
        val style=androidx.core.app.NotificationCompat.MessagingStyle(androidx.core.app.Person.Builder().setName("나").build()).setConversationTitle("테스트 대화방").setGroupConversation(true).addMessage("테스트 메시지",100,person)
        val n=androidx.core.app.NotificationCompat.Builder(app,"test").setSmallIcon(android.R.drawable.ic_dialog_info).setLargeIcon(blue).setStyle(style).build()
        n.extras.putParcelable(Notification.EXTRA_PICTURE,blue)
        val original=previewNotifications()[0].copy(title="테스트 대화방",conversationTitle="테스트 대화방",conversationIdentity="synthetic-room",isGroupConversation=true,notificationCategory="msg",messagesJson="""[{"sender":"민수","text":"테스트 메시지","timestamp":100,"mimeType":"image/jpeg"}]""")
        val row=store.capture(original,n)
        assertNotNull(row.conversationAvatarFile);assertNotNull(row.latestMessage()!!.stringOrNull("senderAvatarFile"))
        val device=UiDevice.getInstance(inst);device.wakeUp()
        assumeFalse("Unlock to verify avatars",(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked)
        try{ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{a->a.setContent{androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides isolated){InboxTheme{InboxScreen(app,true,{}, {},fixtureRows=listOf(row),fixtureDecisions=JSONObject().put(row.snapshotId,JSONObject().put("status","match").put("action","SHOW")),initialTab=0)}}};a.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
            assertNotNull(device.wait(Until.findObject(By.desc("대화방 썸네일")),8000))
            assertNull(device.findObject(By.desc("발신자 프로필")))
            assertNotNull(device.findObject(By.text("민수")))
            device.takeScreenshot(File(app.cacheDir,"avatar-now.png"))
            device.findObject(By.res("nav_1")).click()
            assertNotNull(device.wait(Until.findObject(By.desc("대화방 썸네일")),8000))
            assertNull(device.findObject(By.desc("발신자 프로필")))
            assertNotNull(device.findObject(By.text("민수")))
            device.takeScreenshot(File(app.cacheDir,"avatar-room-list.png"))
            device.findObject(By.text("테스트 대화방")).click()
            assertNotNull(device.wait(Until.findObject(By.desc("발신자 프로필")),8000))
            val photo=device.wait(Until.findObject(By.desc("메시지 첨부 이미지")),8000) ?: error("Room photo missing")
            photo.click()
            assertNotNull(device.wait(Until.findObject(By.desc("첨부 이미지 크게 보기")),8000))
            device.findObject(By.text("닫기")).click()
            device.takeScreenshot(File(app.cacheDir,"avatar-sender.png"))
            scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
        }}finally{store.clear();folder.deleteRecursively();green.recycle();blue.recycle()}
    }

}
