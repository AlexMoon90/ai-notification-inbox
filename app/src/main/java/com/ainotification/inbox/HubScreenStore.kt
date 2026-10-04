package com.ainotification.inbox

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Process-local snapshots stay current even while no Activity collects them. */
internal class HubScreenStore(
    private val scope: CoroutineScope,
    source: () -> Flow<HubScreenData>,
    private val projectRooms: suspend (List<CapturedNotification>) -> List<HubRoom> = { conversationRooms(it) },
    classifications: Flow<List<HubClassification>>? = null,
    roomSource: Flow<List<HubRoom>>? = null,
) {
    private val current = MutableStateFlow(HubScreenData())
    val now: StateFlow<HubScreenData> = current.asStateFlow()
    // Heavy conversation projection exists only while the message/archive UI subscribes.
    val full: StateFlow<HubScreenData> = channelFlow {
        var previous:HubRecords?=null
        val basic=if(classifications==null)current.filter{it.loaded} else
            combine(current.filter{it.loaded},classifications){data,facts->withHubClassifications(data,facts)}
        val source=if(roomSource==null)basic else combine(basic,roomSource){data,rooms->data.copy(records=data.records.copy(rooms=rooms))}
        source.collectLatest { data ->
            val records=if(roomSource!=null)data.records else previous?.takeIf{it.rows === data.records.rows}
                ?: data.records.copy(rooms=projectRooms(data.records.rows))
            currentCoroutineContext().ensureActive()
            previous=records
            send(data.copy(records=records))
        }
    }.flowOn(Dispatchers.Default).stateIn(scope,SharingStarted.WhileSubscribed(0,0),HubScreenData())

    fun refreshTimeGroups() {
        scope.launch(Dispatchers.Default) {
            val time = System.currentTimeMillis()
            current.update { data ->
                if (!data.loaded) data else data.copy(
                    nowByTime = data.now.groupBy { hubTimeBucket(it.postedTime, time) },
                    pendingByTime = data.pending.groupBy { hubTimeBucket(it.postedTime, time) },
                )
            }
        }
    }

    init {
        // Construct Room/selection sources off the UI thread too. No polling or AI calls.
        scope.launch(Dispatchers.Default) { source().collect { current.value = it } }
    }
}
