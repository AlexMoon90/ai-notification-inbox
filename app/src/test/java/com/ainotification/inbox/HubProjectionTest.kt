package com.ainotification.inbox

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class HubProjectionTest {
    private fun row(id:String="a")=previewNotifications()[0].copy(snapshotId=id,notificationKey="key",notificationCategory="msg",conversationIdentity="room",conversationTitle="회사",messagesJson="[]")
    @Test fun unnamedGroupDoesNotUseSenderAsRoomTitleOrMergeRooms(){
        val group=row().copy(isGroupConversation=true,conversationTitle=null,title="민수")
        assertEquals("단톡방",notificationDisplayTitle(group))
        assertEquals("단톡방",notificationDisplayTitle(group.copy(conversationTitle="  ")))
        assertEquals("회사",notificationDisplayTitle(group.copy(conversationTitle="회사")))
        assertEquals("민수",notificationDisplayTitle(group.copy(isGroupConversation=false)))
        val rooms=conversationRooms(listOf(group,group.copy(snapshotId="b",conversationIdentity="other")))
        assertEquals(2,rooms.size)
        assertTrue(rooms.all{it.name=="단톡방"})
    }
    @Test fun nowRequiresPositiveShowAndNeverUsesSemanticCategoryAsPermission(){
        val r=row();val decisions=JSONObject()
        assertFalse(isNowNotification(r,decisions))
        for(status in listOf("pending","review","outside")){decisions.put(r.snapshotId,JSONObject().put("status",status));assertFalse(isNowNotification(r,decisions))}
        decisions.put(r.snapshotId,JSONObject().put("status","match").put("action","SHOW"));assertTrue(isNowNotification(r,decisions))
        decisions.getJSONObject(r.snapshotId).put("action","QUIET");assertFalse(isNowNotification(r,decisions))
    }
    @Test fun socialAppDoesNotBecomeDmWithoutMessageEvidence(){
        val r=row().copy(packageName="com.instagram.android",appLabel="Instagram",notificationCategory=null)
        assertNull(messageService(r));assertEquals("Instagram",messageService(r.copy(notificationCategory="msg")))
        assertNull(messageService(r.copy(notificationCategory="msg",isGroupSummary=true)))
        assertEquals("문자",messageService(r.copy(serviceType="SMS")))
        assertEquals("문자",messageService(r.copy(packageName="com.samsung.android.messaging",notificationCategory="msg")))
    }
    @Test fun groupHistoryIsDeduplicatedWithoutSplittingSenders(){
        val a="""{"sender":"민수","text":"내일 미팅 변경","timestamp":100} """
        val b="""{"sender":"지영","text":"오후 3시 가능","timestamp":200} """
        val r=row().copy(messagesJson="[$a]",isGroupConversation=true)
        val updated=r.copy(snapshotId="b",messagesJson="[$a,$b]",capturedTime=r.capturedTime+1)
        val rooms=conversationRooms(listOf(r,updated));assertEquals(1,rooms.size);assertEquals(2,rooms.single().messages.size)
        assertEquals(listOf("민수","지영"),rooms.single().messages.map{it.sender})
        assertEquals(2,conversationRooms(listOf(r,updated.copy(conversationIdentity="another"))).size)
    }
    @Test fun uncertainRoomsDoNotMergeByDisplayNameAndSmsUsesIdentity(){
        val r=row().copy(conversationIdentity=null)
        assertEquals(2,conversationRooms(listOf(r,r.copy(snapshotId="b",notificationKey="different"))).size)
        val sms=r.copy(serviceType="SMS",personIdentity="tel:01012345678",isGroupConversation=false)
        assertEquals(conversationRoomId(sms),conversationRoomId(sms.copy(notificationKey="different")))
        assertNotEquals(conversationRoomId(sms),conversationRoomId(sms.copy(personIdentity="tel:01099999999")))
        assertNotEquals(conversationRoomId(sms),conversationRoomId(sms.copy(packageName="other.sms")))
    }
    @Test fun oneReferenceCanBelongToSeveralCategoriesAndUnknownHasNoBadge(){
        val f=HubClassification("a","[\"RESERVATION\",\"TRAVEL\"]",1)
        assertEquals(setOf("RESERVATION","TRAVEL"),f.categories());assertNull(hubBadge(f));assertNull(hubBadge(null))
        assertEquals("약속 확정",hubBadge(f.copy(eventsJson="[\"MEETING_CONFIRMED\"]")))
        assertEquals(setOf("OTHER"),f.copy(eventsJson="[]").categories())
    }
    @Test fun classifierUsesTypedEvidenceAndDoesNotInventFields(){
        val context=ApplicationProvider.getApplicationContext<Application>()
        val db=androidx.room.Room.inMemoryDatabaseBuilder(context,InboxDatabase::class.java).build()
        var calls=0
        val engine=JevEngine(context){payload->calls++
            assertFalse(payload.getJSONObject("state").has("policy"))
            val answers=JSONObject();payload.getJSONObject("questions").keys().forEach{k->answers.put(k,JSONObject().put("type","noul").put("noul",if(k=="DELIVERY") .99 else .1))}
            JSONObject().put("answers",answers).put("usage",JSONObject().put("input_tokens",1).put("output_tokens",1))
        }
        val classifier=HubClassifier(db,engine)
        assertEquals(listOf("DELIVERY"),classifier.classify(row()))
        assertTrue(classifier.classify(row().copy(isGroupSummary=true)).isEmpty());assertEquals(1,calls)
        db.close()
    }
    @Test fun migrationPreservesOriginalAndCascadeDeletesOnlyReferences()=kotlinx.coroutines.runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val name="hub-migration-test.db";context.deleteDatabase(name)
        val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        val schema=JSONObject(javaClass.classLoader!!.getResourceAsStream("hub-v2.json")!!.bufferedReader().readText()).getJSONObject("database").getJSONArray("entities").getJSONObject(0)
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file,null).use { old->
            old.execSQL(schema.getString("createSql").replace("\${TABLE_NAME}","notifications"))
            val indices=schema.getJSONArray("indices")
            for(i in 0 until indices.length())old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}","notifications"))
            old.execSQL("INSERT INTO notifications (snapshotId,packageName,appLabel,notificationKey,notificationId,postedTime,capturedTime,title,text,messagesJson,hasContentIntent,isGroupSummary,availableFields,conversationIdentity) VALUES ('saved','test','Test','key',1,1,1,'Original title','Original text','[]',0,0,'','stable-room')")
            old.version=2
        }
        val db=androidx.room.Room.databaseBuilder(context,InboxDatabase::class.java,name).addMigrations(hubMigration,avatarMigration).build()
        try {
            val saved=db.notifications().find("saved")!!
            assertEquals("Original text",saved.text);assertEquals("stable-room",saved.conversationIdentity)
            assertNull(saved.notificationCategory)
            db.hub().save(HubClassification("saved","[\"PAYMENT\"]",1))
            assertNotNull(db.hub().find("saved"))
            db.notifications().deleteAll();assertNull(db.hub().find("saved"))
        } finally { db.close();context.deleteDatabase(name) }
    }

}
