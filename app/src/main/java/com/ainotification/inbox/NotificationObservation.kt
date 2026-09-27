package com.ainotification.inbox

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.stringOrNull(key:String):String? = if(isNull(key)) null else optString(key)
internal fun CapturedNotification.latestMessage():JSONObject? = runCatching {
    val messages=JSONArray(messagesJson)
    (0 until messages.length()).mapNotNull { i->messages.optJSONObject(i)?.let { i to it } }
        .maxWithOrNull(compareBy<Pair<Int,JSONObject>> { it.second.optLong("timestamp",0) }.thenBy { it.first })?.second
}.getOrNull()

/** A current attachment/empty caption must not inherit historical bundled text. */
internal fun CapturedNotification.currentMessageText():String {
    latestMessage()?.let { return it.stringOrNull("text").orEmpty() }
    return bigText?.takeIf { it.isNotBlank() } ?: text.orEmpty()
}
