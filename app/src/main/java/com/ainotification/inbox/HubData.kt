package com.ainotification.inbox

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

/** Only derived metadata lives here. All screens reference the one original notification. */
@Entity(tableName="hub_classifications", foreignKeys=[ForeignKey(entity=CapturedNotification::class,parentColumns=["snapshotId"],childColumns=["notificationId"],onDelete=ForeignKey.CASCADE)])
data class HubClassification(@PrimaryKey val notificationId:String,val eventsJson:String,val classifiedAt:Long,val version:Int=1) {
    fun events():List<String> = runCatching { val a=JSONArray(eventsJson);(0 until a.length()).map{a.getString(it)} }.getOrDefault(emptyList())
    fun categories():Set<String> = events().mapNotNull { when(it){
        "RESERVATION"->"RESERVATION";"DELIVERY"->"DELIVERY";"PAYMENT","MONEY_RECEIVED"->"FINANCE";"ORDER","REFUND"->"SHOPPING";"SOCIAL_ACTIVITY"->"SOCIAL";"TRAVEL"->"TRAVEL";else->null
    }}.toSet().ifEmpty{setOf("OTHER")}
}
@Dao interface HubDao {
    @Query("SELECT * FROM hub_classifications") fun observe():Flow<List<HubClassification>>
    @Query("SELECT * FROM hub_classifications WHERE notificationId IN (:ids)") fun observeIds(ids:List<String>):Flow<List<HubClassification>>
    @Query("SELECT * FROM hub_classifications WHERE notificationId=:id") suspend fun find(id:String):HubClassification?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(value:HubClassification)
}
internal val hubMigration=object:Migration(2,3){override fun migrate(db:SupportSQLiteDatabase){
    db.execSQL("ALTER TABLE notifications ADD COLUMN notificationCategory TEXT")
    db.execSQL("ALTER TABLE notifications ADD COLUMN serviceType TEXT")
    db.execSQL("ALTER TABLE notifications ADD COLUMN personIdentity TEXT")
    db.execSQL("ALTER TABLE notifications ADD COLUMN isGroupConversation INTEGER")
    db.execSQL("CREATE TABLE IF NOT EXISTS hub_classifications (notificationId TEXT NOT NULL, eventsJson TEXT NOT NULL, classifiedAt INTEGER NOT NULL, version INTEGER NOT NULL, PRIMARY KEY(notificationId), FOREIGN KEY(notificationId) REFERENCES notifications(snapshotId) ON UPDATE NO ACTION ON DELETE CASCADE)")
}}

internal val avatarMigration=object:Migration(3,4){override fun migrate(db:SupportSQLiteDatabase){
    db.execSQL("ALTER TABLE notifications ADD COLUMN conversationAvatarFile TEXT")
}}
