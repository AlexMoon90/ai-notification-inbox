package com.ainotification.inbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import org.json.JSONObject

internal data class HubRecords(
    val rows: List<CapturedNotification>,
    val rooms: List<HubRoom>,
    val history: Map<String, List<CapturedNotification>>,
)

internal data class HubScreenData(
    val records: HubRecords = HubRecords(emptyList(), emptyList(), emptyMap()),
    val state: JSONObject = JSONObject(),
    val decisions: JSONObject = JSONObject(),
    val facts: Map<String, HubClassification> = emptyMap(),
    val pending: List<CapturedNotification> = emptyList(),
    val now: List<CapturedNotification> = emptyList(),
    val categories: Map<String, List<CapturedNotification>> = emptyMap(),
    val nowByTime: Map<String, List<CapturedNotification>> = emptyMap(),
    val pendingByTime: Map<String, List<CapturedNotification>> = emptyMap(),
    val nowByApp: Map<String, List<CapturedNotification>> = emptyMap(),
    val loaded: Boolean = false,
)

internal const val NOW_WINDOW_MS=48L*60*60*1000
internal fun isWithinNowWindow(row:CapturedNotification,now:Long)=row.postedTime>now-NOW_WINDOW_MS
internal fun nowClock():Flow<Long> = flow {
    while(true){emit(System.currentTimeMillis());delay(60_000)}
}

/** Now has no dependency on semantic classifications or category grouping. */
internal fun nowScreenData(
    notifications: Flow<List<CapturedNotification>>,
    selection: Flow<JSONObject>,
    includeConversations: Boolean = false,
    clock: Flow<Long> = nowClock(),
): Flow<HubScreenData> {
    val records = notifications.distinctUntilChanged().conflate().map { stored ->
        val rows = stored.filterNot { it.isExcludedCallStatus() }
        HubRecords(rows, if (includeConversations) conversationRooms(rows) else emptyList(), rows.filter { messageService(it) == null }
            .sortedByDescending { it.postedTime }.groupBy { it.packageName })
    }
    return combine(records, selection, clock) { data, state, time ->
        val decisions = state.optJSONObject("results") ?: JSONObject()
        val recent = data.rows.filter { isWithinNowWindow(it,time) }
        val pending = recent.filter {
            val decision = decisions.optJSONObject(it.snapshotId)
            decision == null || decision.optString("status") in listOf("review", "pending", "unconfigured")
        }.sortedByDescending { it.postedTime }
        val now = latestNowBySource(recent, decisions)
        HubScreenData(records=data,state=state,decisions=decisions,pending=pending,now=now,
            nowByTime=now.groupBy { hubTimeBucket(it.postedTime, time) },
            pendingByTime=pending.groupBy { hubTimeBucket(it.postedTime, time) },
            nowByApp=now.groupBy { hubAppKey(it) }, loaded=true)
    }.flowOn(Dispatchers.Default)
}

internal fun withHubClassifications(data:HubScreenData,storedFacts:List<HubClassification>):HubScreenData {
    val facts=storedFacts.associateBy{it.notificationId}
    val categories=linkedMapOf<String,MutableList<CapturedNotification>>()
    data.records.rows.sortedByDescending{it.postedTime}.forEach { row ->
        (facts[row.snapshotId]?.categories() ?: setOf("OTHER")).forEach { category ->
            categories.getOrPut(category){mutableListOf()}.add(row)
        }
    }
    return data.copy(facts=facts,categories=categories)
}

/** Fixture and full-screen projection; production Now uses nowScreenData directly. */
internal fun hubScreenData(
    notifications:Flow<List<CapturedNotification>>,
    selection:Flow<JSONObject>,
    classifications:Flow<List<HubClassification>>,
    includeConversations:Boolean=true,
):Flow<HubScreenData> = combine(nowScreenData(notifications,selection,includeConversations),classifications) { data,facts ->
    withHubClassifications(data,facts)
}.flowOn(Dispatchers.Default)
