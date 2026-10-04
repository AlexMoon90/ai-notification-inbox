package com.ainotification.inbox

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Only numeric syntax is recognized; this never assigns a business meaning. */
internal object NowPattern {
    private val token=Regex("https?://[^\\s]+|(?<!\\d)01[016789][- ]?\\d{3,4}[- ]?\\d{4}(?!\\d)|(?<!\\d)(?:20\\d{2}[-/.])?\\d{1,2}[-/.]\\d{1,2}(?!\\d)|(?<!\\d)\\d{1,2}:\\d{2}(?::\\d{2})?(?!\\d)|(?<![\\d.,])\\d[\\d,]*(?:\\.\\d+)?\\s*원|(?<![\\p{L}\\d])\\d{6,}(?![\\p{L}\\d])")
    fun normalize(text:String):String {
        val parts=JSONArray(); var offset=0
        for(m in token.findAll(text)) {
            parts.put(JSONArray().put("literal").put(text.substring(offset,m.range.first)))
            val type=when { m.value.startsWith("http")->"URL";m.value.startsWith("01") && m.value.replace(Regex("[- ]"),"").length in 10..11->"PHONE";m.value.endsWith("원")->"AMOUNT";':' in m.value->"TIME";m.value.any{it in "-/."}->"DATE";else->"NUMBER" }
            parts.put(JSONArray().put(type));offset=m.range.last+1
        }
        return parts.put(JSONArray().put("literal").put(text.substring(offset))).toString()
    }
    // Free-form qualifiers can depend on ANY removed value. Never guess dependencies.
    fun mayGeneralize(rules:JSONArray):Boolean = jsonObjects(rules).flatMap { r ->
        jsonObjects(r.getJSONArray("conditions"))+jsonObjects(r.getJSONArray("exceptions")).flatMap{jsonObjects(it.getJSONArray("conditions"))}
    }.all { it.getString("value").isBlank() && it.getString("type") in setOf("ANY","EMPTY_CONTENT","DELIVERY","MONEY_RECEIVED","PAYMENT_REQUIRED","RESERVATION") }

    fun key(row:CapturedNotification,revision:String,version:String,rules:JSONArray,state:JSONObject,questions:JSONObject):String {
        val normalized=JSONObject(state.toString())
        // Messenger prose and unknown conversation identity remain exact-match only.
        val generalize=messageService(row)==null && row.notificationCategory!="email" && row.packageName!="com.kakao.talk" &&
            row.conversationIdentity==null && row.conversationTitle==null && row.personIdentity==null && mayGeneralize(rules)
        for(field in listOf("title","text")) if(generalize) normalized.put(field,normalize(state.optString(field)))
        val identity=JSONArray().put("now-pattern-v1").put(JevEngine.MODEL).put(revision).put(version)
            .put(row.packageName).put(if(messageService(row)!=null && row.conversationIdentity==null) row.notificationKey else JSONObject.NULL).put(row.channelId ?: JSONObject.NULL).put(row.conversationIdentity ?: JSONObject.NULL).put(row.conversationTitle ?: JSONObject.NULL)
            .put(row.personIdentity ?: JSONObject.NULL).put(row.isGroupSummary).put(row.needsOriginalReview()).put(row.isGroupConversation ?: JSONObject.NULL).put(row.notificationCategory ?: JSONObject.NULL).put(row.serviceType ?: JSONObject.NULL)
            // URLs can carry the only semantic evidence (e.g. /delivery vs /payment).
            .put(JSONArray(Regex("https?://[^\\s]+").findAll(listOf(state.optString("title"),state.optString("text")).joinToString("\n")).map{it.value}.toList()))
            .put(normalized).put(rules).put(questions)
        return policyHash(identity)
    }
}

/** Bounded local optimization. The runtime always resolves actions again from these answers. */
internal class NowJudgmentCache(
    private val file:AtomicFile,
    private val version:(String)->String,
    private val clock:()->Long=System::currentTimeMillis,
    private val onMiss:()->Unit = {},
) {
    constructor(context:Context,onMiss:()->Unit = {}):this(AtomicFile(File(context.filesDir,"now-judgment-cache.json")), { pkg ->
        kotlin.runCatching { @Suppress("DEPRECATION") val info=context.packageManager.getPackageInfo(pkg,0)
            "${info.versionName}:${info.lastUpdateTime}" }.getOrDefault("unknown")
    }, onMiss=onMiss)
    private var entries=runCatching { JSONObject(file.openRead().bufferedReader().use{it.readText()}) }.getOrElse{JSONObject()}
    private val ttl=24*60*60*1000L
    private fun persist() {
        val out=file.startWrite()
        try { out.write(entries.toString().toByteArray());file.finishWrite(out) }
        catch(e:Exception){file.failWrite(out);throw e}
    }
    @Synchronized fun lookup(row:CapturedNotification,revision:String,rules:JSONArray,state:JSONObject,questions:JSONObject,engine:JevEngine):JSONObject? {
        val key=NowPattern.key(row,revision,version(row.packageName),rules,state,questions)
        val now=clock()
        entries.optJSONObject(key)?.let { entry ->
            if(now-entry.optLong("createdAt") in 0 until ttl) {
                val result=entry.optJSONObject("result")
                if(result!=null && runCatching { questions.keys().asSequence().all{engine.readConditionVerdict(result,it)!=null} }.getOrDefault(false)) {
                    entry.put("lastUsedAt",now).put("hitCount",entry.optLong("hitCount")+1)
                    runCatching{persist()}
                    return JSONObject(result.toString()).put("cache_hit",true)
                }
            }
            entries.remove(key)
        }
        return null
    }
    fun evaluate(row:CapturedNotification,revision:String,rules:JSONArray,state:JSONObject,questions:JSONObject,engine:JevEngine):JSONObject {
        lookup(row,revision,rules,state,questions,engine)?.let{return it}
        val key=NowPattern.key(row,revision,version(row.packageName),rules,state,questions)
        val now=clock()
        onMiss()
        val result=engine.evaluateConditions(JSONObject(engine.masked(state.toString())),JSONObject(engine.masked(questions.toString())))
        // Failures and ambiguous answers must be reconsidered, never made sticky by caching.
        if(questions.keys().asSequence().all{engine.readConditionVerdict(result,it)!=null}) synchronized(this) {
            entries.put(key,JSONObject().put("result",JSONObject().put("answers",result.getJSONObject("answers")))
                .put("revision",revision).put("ruleIds",JSONArray(jsonObjects(rules).map{it.getString("id")}))
                .put("questions",questions).put("createdAt",now).put("lastUsedAt",now).put("hitCount",0))
            val keys=entries.keys().asSequence().toList()
            keys.filter{ now-entries.getJSONObject(it).optLong("createdAt") !in 0 until ttl }.forEach{entries.remove(it)}
            entries.keys().asSequence().toList().sortedBy{entries.getJSONObject(it).optLong("lastUsedAt")}
                .take((entries.length()-256).coerceAtLeast(0)).forEach{entries.remove(it)}
            runCatching{persist()} // Cache storage failure never loses the actual judgment.
        }
        return result.put("cache_hit",false)
    }
}
