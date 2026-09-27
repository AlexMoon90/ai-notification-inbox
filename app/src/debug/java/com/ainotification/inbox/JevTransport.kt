package com.ainotification.inbox

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal class JevTransport(private val context: Context) {
    fun send(payload: JSONObject): JSONObject {
        val key = context.assets.open("test-jev-key").bufferedReader().use { it.readText().trim() }
        val connection = URL("https://api.typesafe.ai/v1/systemone").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            val status = connection.responseCode
            if (status != 200) throw JevHttpFailure(status)
            val data = connection.inputStream.use { it.readBytes() }
            require(data.size <= 128000)
            return JSONObject(String(data, Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}
