package com.ainotification.inbox

import android.content.Context
import java.security.MessageDigest

/** Read state belongs to this inbox only; no source app is modified. */
internal class ConversationReadStore(context:Context){
    private val prefs=context.getSharedPreferences("conversation-read",Context.MODE_PRIVATE)
    private fun hash(value:String):String {
        if(value.startsWith("sha256:") && value.length==71)return value.removePrefix("sha256:")
        val digits="0123456789abcdef"
        val bytes=MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return buildString(bytes.size*2){for(byte in bytes){val n=byte.toInt() and 255;append(digits[n ushr 4]);append(digits[n and 15])}}
    }
    private fun key(room:HubRoom)="room:"+hash(room.id)
    fun unread(room:HubRoom):Int {
        val seen=prefs.getStringSet(key(room),emptySet()).orEmpty()
        return room.messages.distinctBy{it.identity}.count{hash(it.identity) !in seen}
    }
    fun markRead(room:HubRoom):Boolean {
        val key=key(room);val before=prefs.getStringSet(key,emptySet()).orEmpty()
        val after=before+room.messages.map{hash(it.identity)}
        if(before==after)return false
        prefs.edit().putStringSet(key,after).apply()
        return true
    }
}
