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
    @Test fun metadataPreservesPhotoEvidenceAndIdentityBeforeDecoding() {
        val store=NotificationImages(context)
        val row=previewNotifications()[0].copy(title=null,text=null,bigText=null,subText=null,conversationTitle=null,messagesJson="""[{"mimeType":"image/","dataUri":"content://private/photo"}]""")
        val metadata=store.metadata(row,Notification())
        assertFalse(metadata.hasEmptyContent());assertTrue(metadata.latestMessage()!!.isImageAttachment())
        assertFalse(metadata.messagesJson.contains("content://"));assertEquals(row.snapshotId,metadata.snapshotId)
        assertEquals(row.snapshotId,store.capture(row,Notification()).snapshotId)
    }
    @Test fun mediaUpdateKeepsSingleRowAndCannotRestoreDeletedNotification()=kotlinx.coroutines.runBlocking {
        val db=androidx.room.Room.inMemoryDatabaseBuilder(context,InboxDatabase::class.java).build()
        try{
            val row=previewNotifications()[0];db.notifications().insert(row)
            db.notifications().attachMedia(row.snapshotId,"[]","thumbnail.png")
            assertEquals(1,db.notifications().readAll().size)
            assertEquals(row.text,db.notifications().find(row.snapshotId)!!.text)
            db.notifications().delete(row.snapshotId)
            db.notifications().attachMedia(row.snapshotId,"[]","thumbnail.png")
            assertNull(db.notifications().find(row.snapshotId))
        }finally{db.close()}
    }
    @Test fun alreadyOpenedImageSurvivesLaterUriDenialAndCloses() {
        val bitmap=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888)
        val bytes=java.io.ByteArrayOutputStream().apply{bitmap.compress(Bitmap.CompressFormat.PNG,100,this)}.toByteArray();bitmap.recycle()
        var closed=false
        val input=object:java.io.ByteArrayInputStream(bytes){override fun close(){closed=true;super.close()}}
        val row=previewNotifications()[0].copy(isGroupSummary=false,messagesJson="""[{"mimeType":"image/","dataUri":"content://no-longer-readable/photo"}]""")
        val pending=PendingImageReads();pending.prepare(row){input}
        val store=NotificationImages(context)
        val result=try{store.capture(row,Notification(),pending)}finally{pending.close()}
        assertTrue(closed);assertEquals("saved",result.latestMessage()!!.getString("imageReadStatus"))
        assertEquals("opened_at_receipt",result.latestMessage()!!.getString("imageOpenStatus"))
        assertNotNull(store.file(result.latestMessage()!!.getString("imageFile")))
        assertTrue(pending.streams.isEmpty())
    }
    @Test fun attachmentFailureKeepsReasonWithoutRawUri() {
        val original=previewNotifications()[0].copy(messagesJson="""[{"text":"사진","mimeType":"image/","dataUri":"file:///private/photo.jpg"}]""")
        val captured=NotificationImages(context).capture(original,Notification())
        val m=captured.latestMessage()!!
        assertEquals("file",m.getString("imageUriScheme"))
        assertEquals("unsupported_scheme",m.getString("imageReadStatus"))
        assertFalse(m.has("dataUri"));assertFalse(m.toString().contains("photo.jpg"));assertFalse(m.has("imageFile"))
    }
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
        val unchanged=previewNotifications()[0]
        assertEquals(unchanged,store.capture(unchanged,avatar))
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
