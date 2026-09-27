package com.ainotification.inbox

import android.content.Context
import androidx.room.*
import org.json.JSONObject
import java.util.concurrent.Executors

@Entity(tableName="policy_state")
data class PolicyStateRow(@PrimaryKey val id: Int=1, val json: String)
@Entity(tableName="policy_versions")
data class PolicyVersionRow(@PrimaryKey val revision: String, val json: String, val createdAt: Long)
@Dao interface PolicyStateDao {
    @Query("SELECT json FROM policy_state WHERE id=1") fun read(): String?
    @Insert(onConflict=OnConflictStrategy.REPLACE) fun write(row:PolicyStateRow)
    @Insert(onConflict=OnConflictStrategy.IGNORE) fun version(row:PolicyVersionRow)
}
@Database(entities=[PolicyStateRow::class,PolicyVersionRow::class],version=1,exportSchema=true)
abstract class PolicyDatabase:RoomDatabase() { abstract fun state():PolicyStateDao }
internal class PolicyStateStore(context:Context) {
    private val db=Room.databaseBuilder(context,PolicyDatabase::class.java,"policies.db").build()
    private val io=Executors.newSingleThreadExecutor()
    fun read():JSONObject?=io.submit<JSONObject?> { db.state().read()?.let(::JSONObject) }.get()
    fun write(value:JSONObject) { val snapshot=value.toString(); io.submit {
        db.runInTransaction {
            db.state().write(PolicyStateRow(json=snapshot))
            value.optJSONObject("policy")?.let { p -> db.state().version(PolicyVersionRow(p.getString("id"),p.toString(),System.currentTimeMillis())) }
        }
    }.get() }
}
