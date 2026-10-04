package com.ainotification.inbox

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class ConversationReadStoreTest {
    @Test fun readsExistingFormattedSha256Keys(){
        val context=ApplicationProvider.getApplicationContext<Application>()
        fun oldHash(value:String)=java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
        val message=HubMessage(previewNotifications()[0],"발신자","본문",10,"한글|message|123")
        val room=HubRoom("legacy-room","문자","방",listOf(message))
        context.getSharedPreferences("conversation-read",0).edit()
            .putStringSet("room:"+oldHash(room.id),setOf(oldHash(message.identity))).commit()
        assertEquals(0,ConversationReadStore(context).unread(room))
        assertFalse(ConversationReadStore(context).markRead(room))
        assertEquals(0,ConversationReadStore(context).unread(room.copy(messages=listOf(message.copy(identity=stableMessageIdentity(message.identity))))))
    }
    @Test fun countsUniqueMessagesPersistsAndIsolatesRooms(){
        val context=ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("conversation-read",0).edit().clear().commit()
        val store=ConversationReadStore(context)
        val row=previewNotifications()[0]
        val first=HubMessage(row,"sender","text",10,"first")
        val room=HubRoom("room","문자","sender",listOf(first,first))
        assertEquals(1,store.unread(room));assertTrue(store.markRead(room))
        assertEquals(0,ConversationReadStore(context).unread(room))
        assertFalse(store.markRead(room))
        assertEquals(1,store.unread(room.copy(messages=listOf(first,first.copy(identity="late",time=5)))))
        assertEquals(1,store.unread(room.copy(id="other-room")))
    }
}
