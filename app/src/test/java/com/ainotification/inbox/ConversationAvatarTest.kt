package com.ainotification.inbox

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConversationAvatarTest {
    @Test fun groupAndSenderImagesStaySeparateAndNeverBecomeAttachments(){
        val context=ApplicationProvider.getApplicationContext<Application>();val store=NotificationImages(context)
        val sender=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.GREEN)}
        val room=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.BLUE)}
        val person=Person.Builder().setName("민수").setIcon(IconCompat.createWithBitmap(sender)).build()
        val style=NotificationCompat.MessagingStyle(Person.Builder().setName("나").build()).setGroupConversation(true).setConversationTitle("회사방").addMessage("안녕하세요",100,person)
        val n=NotificationCompat.Builder(context,"test").setSmallIcon(android.R.drawable.ic_dialog_info).setLargeIcon(room).setStyle(style).build()
        val original=previewNotifications()[0].copy(notificationCategory="msg",conversationIdentity="room",isGroupConversation=true,conversationTitle="회사방",messagesJson="""[{"sender":"민수","text":"안녕하세요","timestamp":100}]""")
        val row=store.capture(original,n)
        assertNotNull(row.conversationAvatarFile);assertNotNull(store.file(row.conversationAvatarFile))
        val m=row.latestMessage()!!;assertNotNull(store.file(m.stringOrNull("senderAvatarFile")));assertFalse(m.isImageAttachment());assertFalse(m.has("imageFile"))
        assertNotEquals(row.conversationAvatarFile,m.stringOrNull("senderAvatarFile"))
        val projected=conversationRooms(listOf(row)).single()
        assertEquals(row.conversationAvatarFile,projected.avatarFile);assertEquals(m.stringOrNull("senderAvatarFile"),projected.messages.single().senderAvatarFile)
        val same=NotificationCompat.Builder(context,"test").setSmallIcon(android.R.drawable.ic_dialog_info).setLargeIcon(sender).setStyle(style).build()
        assertNull(store.capture(original,same).conversationAvatarFile)
        store.clear();sender.recycle();room.recycle()
    }
    @Test fun absentProfileDoesNotBorrowAnotherSendersPhoto(){
        val row=previewNotifications()[0].copy(notificationCategory="msg",conversationIdentity="room",messagesJson="""[{"sender":"민수","text":"안녕","timestamp":100,"senderAvatarFile":"first.png"},{"sender":"지영","text":"응","timestamp":200}]""")
        val messages=conversationRooms(listOf(row)).single().messages
        assertEquals("first.png",messages[0].senderAvatarFile);assertNull(messages[1].senderAvatarFile)
    }
}
