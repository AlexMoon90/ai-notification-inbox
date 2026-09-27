package com.ainotification.inbox

import android.app.Application
import android.app.Notification
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class NotificationImagesTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    @Test fun pictureIsPrivateBoundedAndNotAvatar(){
        val store=NotificationImages(context);store.clear()
        val bitmap=Bitmap.createBitmap(2048,100,Bitmap.Config.ARGB_8888)
        val n=Notification();n.extras.putParcelable(Notification.EXTRA_PICTURE,bitmap)
        val row=store.capture(previewNotifications()[0],n)
        assertTrue(row.latestMessage()!!.isImageAttachment())
        assertNull(messageService(row))
        val name=row.latestMessage()!!.getString("imageFile");assertNotNull(store.file(name))
        val decoded=android.graphics.BitmapFactory.decodeFile(store.file(name)!!.absolutePath);assertEquals(1024,decoded.width)
        val avatar=Notification();avatar.extras.putParcelable(Notification.EXTRA_LARGE_ICON,bitmap)
        assertEquals(previewNotifications()[0],store.capture(previewNotifications()[0],avatar))
        assertNull(store.file("../../secret"));assertNull(store.readUri("https://example.com/image.png"))
        store.clear();assertNull(store.file(name));bitmap.recycle();decoded.recycle()
    }
    @Test fun pictureComplementsLatestPhotoButNeverTextReply(){
        val store=NotificationImages(context)
        val bitmap=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888)
        val n=Notification().apply{extras.putParcelable(Notification.EXTRA_PICTURE,bitmap)}
        val original=previewNotifications()[0].copy(notificationCategory="msg",messagesJson="""[{"sender":"민수","timestamp":100,"mimeType":"image/jpeg","dataUri":"content://missing-provider/photo"}]""")
        val captured=store.capture(original,n)
        val messages=conversationRooms(listOf(captured)).single().messages
        assertEquals(1,messages.size);assertNotNull(store.file(messages.single().imageFile))
        val reply=JSONObject().put("sender","지영").put("timestamp",200).put("text","좋아요")
        val later=store.capture(original.copy(messagesJson=JSONArray(original.messagesJson).put(reply).toString()),n)
        assertNull(later.latestMessage()!!.stringOrNull("imageFile"))
        assertFalse(later.latestMessage()!!.isImageAttachment())
        store.clear();bitmap.recycle()
    }
    @Test fun imageBelongsToItsMessageNotLaterReplyAndMissingPermissionIsSafe(){
        val store=NotificationImages(context)
        val photo=JSONObject().put("sender","민수").put("timestamp",100).put("mimeType","image/jpeg").put("dataUri","content://missing-provider/photo")
        val reply=JSONObject().put("sender","지영").put("timestamp",200).put("text","좋아요")
        val original=previewNotifications()[0].copy(notificationCategory="msg",conversationIdentity="room",messagesJson=JSONArray().put(photo).put(reply).toString())
        val row=store.capture(original,Notification())
        assertFalse(row.latestMessage()!!.isImageAttachment())
        val messages=conversationRooms(listOf(row)).single().messages
        assertTrue(messages[0].hasImage);assertNull(messages[0].imageFile);assertFalse(messages[1].hasImage)
        assertFalse(row.messagesJson.contains("content://"))
        assertEquals("좋아요",row.currentMessageText())
    }
}
