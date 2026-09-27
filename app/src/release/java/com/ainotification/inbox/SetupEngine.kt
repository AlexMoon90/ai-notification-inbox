package com.ainotification.inbox
import android.content.Context
import org.json.JSONObject
internal class SetupEngine(context: Context) {
    internal fun structured(purpose: String, prompt: String, spec: JSONObject, input: JSONObject): JSONObject = error("Private test only")
    fun process(body: JSONObject): JSONObject = error("Standalone API setup is test-only")
}
