package com.ainotification.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf

internal val smartCategories=linkedMapOf("money" to "돈","schedule" to "일정","delivery" to "배송·주문","todo" to "할 일","security" to "보안·인증","other" to "기타")
internal fun smartCategory(events:List<String>):String=when {
    events.any{it in setOf("PAYMENT","MONEY_RECEIVED","REFUND")}->"money"
    events.any{it in setOf("RESERVATION","SCHEDULE_CHANGE","MEETING_CONFIRMED","TRAVEL")}->"schedule"
    events.any{it in setOf("DELIVERY","ORDER")}->"delivery"
    events.any{it in setOf("REPLY_REQUIRED","AI_INPUT_REQUIRED")}->"todo"
    else->"other"
}
internal fun smartHeading(events:List<String>):String=when {
    "SCHEDULE_CHANGE" in events->"일정 변경"
    "REFUND" in events->"취소·환불 안내"
    "MONEY_RECEIVED" in events->"입금 안내"
    "PAYMENT" in events->"결제·청구 안내"
    "DELIVERY" in events->"배송 안내"
    "ORDER" in events->"주문 안내"
    "RESERVATION" in events->"예약 안내"
    "MEETING_CONFIRMED" in events->"모임 확정"
    "REPLY_REQUIRED" in events->"답변 요청"
    "AI_INPUT_REQUIRED" in events->"입력·확인 요청"
    "TRAVEL" in events->"여행 안내"
    "SOCIAL_ACTIVITY" in events->"소셜 활동"
    "AI_TASK_COMPLETED" in events->"작업 완료"
    else->"저장된 알림"
}
// Only literal amounts are displayed; no guessed merchant, date or event linkage.
internal fun smartAmount(text:String):String?=Regex("(?<![0-9])(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\s*원").findAll(text).map{it.value}.distinct().toList().singleOrNull()
private data class SmartItem(val event:StructuredEvent,val money:MoneyEvent?,val context:EventContext?) {
    val category=event.category
    val heading=money?.let{moneyHeading(it)} ?: event.title
    val changed=event.isChanged
    val secondary=money?.let{listOfNotNull(it.provider).distinct().joinToString(" · ").takeIf{it.isNotBlank()}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SmartDashboard(app:InboxApplication,fixture:Boolean,modifier:Modifier=Modifier,open:(CapturedNotification)->Unit,fixtureEntries:List<StructuredEntry> = emptyList(),loadOriginal:suspend(String)->CapturedNotification?={app.repository.loadBody(it)}) {
    var category by rememberSaveable{mutableStateOf<String?>(null)}
    var changesOnly by rememberSaveable{mutableStateOf<String?>(null)}
    var selected by rememberSaveable{mutableStateOf<String?>(null)}
    var period by rememberSaveable{mutableStateOf("전체")}
    var raw by rememberSaveable(selected){mutableStateOf(false)}
    val prefs=remember(app){app.getSharedPreferences("smart-dashboard-seen",0)}
    var seen by remember{mutableStateOf(if(fixture)emptySet<String>() else prefs.getStringSet("ids",emptySet()).orEmpty().toSet())}
    val source=remember(app,fixture,fixtureEntries){if(fixture)flowOf(fixtureEntries) else app.database.structured().observe()}
    val stored by source.collectAsStateWithLifecycle(initialValue=emptyList())
    val remainingSource=remember(app,fixture){if(fixture)flowOf(0) else app.database.structured().outstanding()}
    val remaining by remainingSource.collectAsStateWithLifecycle(initialValue=0)
    val replay by app.structured.replay.progress.collectAsStateWithLifecycle()
    val entries=remember(stored){stored.map{SmartItem(it.event,it.money,it.context)}}
    val pager=rememberPagerState(initialPage=if(fixture)2 else prefs.getInt("concept",2).coerceIn(0,2),pageCount={3})
    val scope=rememberCoroutineScope()
    var timelineDay by rememberSaveable{mutableStateOf("오늘")}
    LaunchedEffect(pager.settledPage){if(!fixture)prefs.edit().putInt("concept",pager.settledPage).apply()}
    val groups=remember(entries){entries.groupBy{it.category}}
    val unseen=remember(entries,seen){entries.filter{it.event.sourceNotificationId !in seen}}
    val changed=unseen.filter{it.changed}
    val fresh=unseen.filterNot{it.changed}
    val detail=entries.find{it.event.sourceNotificationId==selected}
    val relatedBodies by produceState<List<CapturedNotification>>(emptyList(),selected,detail?.context?.evidenceIds){value=detail?.context?.let{c->evidenceIds(c.evidenceIds).filter{it!=selected}.take(8).mapNotNull{loadOriginal(it)}}.orEmpty()}
    val body by produceState<CapturedNotification?>(null,selected){value=null;value=if(selected==null)null else loadOriginal(selected!!)}
    fun back(){when{selected!=null->selected=null;category!=null->{category=null;period="전체"};else->changesOnly=null}}
    BackHandler(selected!=null || category!=null || changesOnly!=null){back()}
    fun mark(item:SmartItem){seen=seen+item.event.sourceNotificationId;if(!fixture)prefs.edit().putStringSet("ids",seen).apply()}
    val ink=Color(0xff28345b)
    @Composable fun line(item:SmartItem,compact:Boolean=false){
        Surface(onClick={selected=item.event.sourceNotificationId},color=Color.White,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().testTag("smart_item_${item.event.sourceNotificationId}")){
            Row(Modifier.padding(horizontal=12.dp,vertical=if(compact)6.dp else 8.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text(item.heading,Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=18.sp,color=ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                        item.money?.let{money->moneyDisplayAmount(money)?.let{Text(it,fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=ink)}}
                    }
                    item.context?.let{c->Text(listOfNotNull(when(c.relationship){"work_likely"->"업무 가능성";"personal_likely"->"개인 일정";else->if(c.eventType.startsWith("appointment"))"업무·개인 미정" else null},c.dateTimeText,if(c.needsContext){if(c.eventType=="appointment_related")"시간 조율 중" else "내용 검토 필요"} else "확정").joinToString(" · "),fontSize=11.sp,color=ModernMuted)}
                    item.money?.let{money->
                        if(money.transactionType=="money_related_unknown")Text("거래 유형 확인 필요",fontSize=11.sp,color=ModernMuted)
                        else if(money.transactionAmount==null)Text("거래금액 확인 필요",fontSize=11.sp,color=ModernMuted)
                    }
                    Text("${item.secondary?.let{"$it · "}.orEmpty()}${item.event.sourceApp} · ${if(item.money?.occurredAt!=null)"거래" else "수신"} ${formatTime(item.money?.occurredAt ?: item.event.observedAt)}",fontSize=11.sp,lineHeight=15.sp,color=ModernMuted,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                if(item.event.sourceNotificationId !in seen)Text(if(item.changed)"변경" else "●",fontSize=11.sp,color=Color(0xff6963c8))
            }
        }
    }
    @Composable fun content(concept:Int){
    LazyColumn(Modifier.fillMaxSize().testTag("smart_dashboard_$concept"),contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(if(concept==2)6.dp else 8.dp)){
        if(selected!=null || category!=null || changesOnly!=null)item{TextButton(onClick={back()}){Text("‹ 돌아가기")}}
        when {
            detail!=null->{
                item{Text(detail.heading,fontSize=23.sp,fontWeight=FontWeight.Bold)}
                item{Text("${detail.event.sourceApp} · 수신 ${formatTime(detail.event.observedAt)}",color=ModernMuted)}
                detail.money?.let{money->
                    moneyDisplayAmount(money)?.let{amount->item{Text(amount,fontSize=26.sp,fontWeight=FontWeight.Bold)}}
                    money.balanceAfter?.let{balance->item{Text("거래 후 잔액  ${java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREAN).format(balance)}원",fontSize=14.sp)}}
                    detail.secondary?.let{value->item{Text(value,fontSize=16.sp)}}
                    money.paymentMethod?.let{value->item{Text("결제 수단 · $value")}}
                    money.accountHint?.let{value->item{Text("계좌 · $value")}}
                    money.occurredAt?.let{value->item{Text("거래 시각 · ${formatTime(value)}")}}
                    if(money.extractionStatus!="complete")item{Text(if(money.transactionType=="money_related_unknown")"금융 정보는 확인됐지만 거래 유형 확인이 필요해요." else "거래금액을 확정하지 못했어요. 잔액을 거래금액으로 표시하지 않습니다.",color=ModernMuted)}
                }
                detail.context?.let{c->
                    if(c.relationship!="unknown")item{Text(if(c.relationship=="work_likely")"업무 관련 가능성이 있어요 · 대화 내용 기준" else "개인 일정으로 보여요 · 대화 내용 기준",fontSize=12.sp,color=ModernMuted)}
                    c.dateTimeText?.let{value->item{Text("원문에 언급된 시각 · $value")}}
                    item{Text(if(c.needsContext)if(c.eventType=="appointment_related")"시간을 조율하는 내용이에요. 약속 확정과 구분해 표시합니다." else "대화 맥락에서 거래 완료 여부는 확인되지 않았어요." else "관찰한 정보에서 확인된 내용이에요.",color=ModernMuted)}
                    if(c.contextCompleteness!="full")item{Text("수신 메시지만 확인했어요. 사용자가 보낸 메시지와 전체 대화는 포함되지 않을 수 있어요.",fontSize=12.sp,color=ModernMuted)}
                }
                item{Text("분류: ${smartCategories[detail.category]}",fontSize=14.sp)}
                item{Text("원문에서 확인된 정보만 표시합니다. 구체적인 내용은 원본 알림에서 확인하세요.",fontSize=12.sp,color=ModernMuted)}
                item{Button(onClick={mark(detail)},enabled=detail.event.sourceNotificationId !in seen,modifier=Modifier.testTag("smart_mark_seen")){Text(if(detail.event.sourceNotificationId in seen)"확인한 내용이에요" else "내용 확인했어요")}}
                item{TextButton(onClick={raw=!raw},modifier=Modifier.testTag("smart_original")){Text(if(detail.context!=null){if(raw)"대화 원문 접기" else "대화 원문 보기"}else if(raw)"원본 알림 접기" else "원본 알림 보기")}}
                if(raw)item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    relatedBodies.forEach{source->Text("관련 수신 기록 · ${formatTime(source.postedTime)}",fontSize=12.sp);Text(source.preview());TextButton(onClick={open(source)}){Text("원래 앱에서 보기")}}
                    if(body==null)Text("원본을 불러오고 있어요") else {Text(body!!.title.orEmpty(),fontWeight=FontWeight.Bold);Text(body!!.preview());TextButton(onClick={open(body!!)}){Text("원래 앱에서 보기")}}
                }}
            }
            category!=null || changesOnly!=null->{
                item{Text(category?.let{smartCategories[it]} ?: "주요 변화 전체 보기",fontSize=21.sp,fontWeight=FontWeight.Bold)}
                if(category!=null)item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("전체","오늘","이번 주","이번 달","이전 기록").forEach{p->FilterChip(period==p,{period=p},label={Text(p)})}}}
                val now=System.currentTimeMillis()
                val startToday=java.util.Calendar.getInstance().apply{set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)}.timeInMillis
                val startMonth=java.util.Calendar.getInstance().apply{set(java.util.Calendar.DAY_OF_MONTH,1);set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)}.timeInMillis
                val startWeek=java.util.Calendar.getInstance().apply{firstDayOfWeek=java.util.Calendar.MONDAY;set(java.util.Calendar.DAY_OF_WEEK,java.util.Calendar.MONDAY);set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)}.timeInMillis
                val list=if(category!=null)groups[category].orEmpty().filter{when(period){"오늘"->it.event.observedAt in startToday..now;"이번 주"->it.event.observedAt in startWeek..now;"이번 달"->it.event.observedAt in startMonth..now;"이전 기록"->it.event.observedAt<startMonth;else->true}} else when(changesOnly){"new"->fresh;"changed"->changed;else->unseen}
                if(list.isEmpty())item{Text(if(category=="security")"보안·인증 분류는 준비 중이에요. 저장된 알림은 기타에서 확인해 주세요." else "이곳에 해당하는 기록이 없어요.",color=ModernMuted)}
                items(list,key={it.event.sourceNotificationId}){line(it)}
            }
            concept==0->{
                item{Surface(color=Color(0xffe3f1fa),shape=RoundedCornerShape(16.dp)){
                    Column(Modifier.fillMaxWidth().padding(12.dp)){
                        Text("오늘의 흐름",fontSize=19.sp,fontWeight=FontWeight.Bold,color=ink)
                        Text("알림이 도착한 시간순으로 살펴보세요",fontSize=12.sp,color=ModernMuted)
                    }
                }}
                item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf("오늘","어제","이전 날짜").forEach{day->FilterChip(timelineDay==day,{timelineDay=day},label={Text(day,fontSize=12.sp)})}
                }}
                val today=java.time.LocalDate.now()
                val zone=java.time.ZoneId.systemDefault()
                val start=today.atStartOfDay(zone).toInstant().toEpochMilli()
                val yesterday=today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val timeline=entries.filter{when(timelineDay){"오늘"->it.event.observedAt>=start;"어제"->it.event.observedAt in yesterday until start;else->it.event.observedAt<yesterday}}.asReversed()
                if(timeline.isEmpty())item{Text("이 날짜에 저장된 기록이 없어요.",color=ModernMuted)}
                items(timeline,key={it.event.sourceNotificationId}){item->
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Text(java.text.SimpleDateFormat(if(timelineDay=="이전 날짜")"M/d\nHH:mm" else "HH:mm",java.util.Locale.KOREAN).format(java.util.Date(item.event.observedAt)),fontSize=11.sp,color=ModernMuted,modifier=Modifier.width(38.dp))
                        Box(Modifier.width(2.dp).height(48.dp).background(Color(0xffcdd7ed)))
                        Box(Modifier.weight(1f)){line(item)}
                    }
                }
                item{Text("카테고리별 지난 기록",fontWeight=FontWeight.Bold)}
                items(smartCategories.keys.toList()){key->
                    TextButton(onClick={category=key;period="전체"},modifier=Modifier.fillMaxWidth()){Text(smartCategories.getValue(key));Spacer(Modifier.weight(1f));Text(if(key=="security")"분류 준비 중" else "${groups[key].orEmpty().size}개 기록")}
                }
            }
            concept==1->{
                item{Column(Modifier.padding(vertical=4.dp)){
                    Text("생활 레이더",fontSize=19.sp,fontWeight=FontWeight.Bold,color=ink)
                    Text("카테고리별 저장된 정보와 미확인 기록",fontSize=12.sp,color=ModernMuted)
                }}
                item{BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).testTag("smart_radar")){
                    val size=maxWidth
                    val bubble=size*.25f
                    val radius=size*.35f
                    Surface(onClick={changesOnly="all"},color=Color(0xff6463bb),shape=CircleShape,modifier=Modifier.size(size*.28f).align(Alignment.Center)){
                        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text("미확인",fontSize=12.sp,color=Color.White);Text(unseen.size.toString(),fontSize=20.sp,fontWeight=FontWeight.Bold,color=Color.White)}
                    }
                    val colors=listOf(0xfffcebe9,0xffe8edff,0xffe4f3ed,0xffeaf4f2,0xffeee9fb,0xffedf0f5)
                    smartCategories.entries.forEachIndexed{i,(key,label)->
                        val angle=(-90+i*60)*Math.PI/180
                        Surface(onClick={category=key;period="전체"},color=Color(colors[i]),shape=CircleShape,modifier=Modifier.offset(x=size/2+radius*cos(angle).toFloat()-bubble/2,y=size/2+radius*sin(angle).toFloat()-bubble/2).size(bubble).testTag("smart_radar_$key")){
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(label,fontSize=12.sp,fontWeight=FontWeight.Bold,color=ink,maxLines=1);Text(if(key=="security")"준비 중" else "${groups[key].orEmpty().size}건",fontSize=11.sp,color=ModernMuted)}
                        }
                    }
                }}
                item{Text("최근 들어온 정보",fontWeight=FontWeight.Bold)}
                items(entries.take(3),key={it.event.sourceNotificationId}){line(it)}
                item{Text("카테고리 상태 목록",fontWeight=FontWeight.Bold)}
                items(smartCategories.keys.toList()){key->
                    TextButton(onClick={category=key;period="전체"},modifier=Modifier.fillMaxWidth()){Text(smartCategories.getValue(key));Spacer(Modifier.weight(1f));Text(if(key=="security")"분류 준비 중" else "${groups[key].orEmpty().size}개 기록")}
                }
            }
            else->{
                item{Surface(color=Color(0xffeae7fb),shape=RoundedCornerShape(16.dp)){
                    Column(Modifier.fillMaxWidth().padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(if(unseen.isEmpty()&&remaining>0)"정보를 정리하고 있어요" else if(unseen.isEmpty())"새로운 변화는 모두 살펴봤어요" else "그동안 이런 변화가 있었어요",fontSize=17.sp,fontWeight=FontWeight.Bold,color=ink)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            listOf(Triple("새 항목",fresh.size,"new"),Triple("변경",changed.size,"changed")).forEach{(label,count,key)->Surface(onClick={changesOnly=key},color=Color.White,shape=RoundedCornerShape(10.dp),modifier=Modifier.weight(1f)){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),fontSize=12.sp);Text(count.toString(),fontSize=19.sp,fontWeight=FontWeight.Bold)}}}
                        }
                    }
                }}
                item{Row(verticalAlignment=Alignment.CenterVertically){Text("주요 변화 미리보기",Modifier.weight(1f),fontWeight=FontWeight.Bold);TextButton(onClick={changesOnly="all"},modifier=Modifier.testTag("smart_all_changes")){Text("전체 보기",fontSize=12.sp)}}}
                items((changed+fresh).take(3),key={it.event.sourceNotificationId}){line(it,compact=true)}
                item{Text("카테고리별 전체 보기",fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=4.dp))}
                smartCategories.entries.toList().chunked(2).forEach{pair->item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    pair.forEach{(key,label)->Surface(onClick={category=key;period="전체"},color=Color.White,shape=RoundedCornerShape(12.dp),modifier=Modifier.weight(1f).testTag("smart_category_$key")){
                        Column(Modifier.padding(horizontal=12.dp,vertical=6.dp)){
                            Text(label,fontSize=14.sp,fontWeight=FontWeight.Bold,color=ink)
                            Text(if(key=="security")"분류 준비 중" else "${groups[key].orEmpty().size}개 기록",fontSize=11.sp,lineHeight=15.sp,color=ModernMuted)
                        }
                    }}
                }}}
                item{Text("확인한 내용과 지난 기록은 카테고리에 남아요. 보안·인증 자동 분류는 준비 중이며 해당 기록은 기타에서 확인할 수 있어요.",fontSize=11.sp,lineHeight=15.sp,color=ModernMuted)}
            }
        }
    }
    }
    if(selected!=null || category!=null || changesOnly!=null){
        Box(modifier){content(pager.currentPage)}
    }else Column(modifier.testTag("smart_dashboard")){
        if(replay.active)Text("기존 기록 다시 정리 ${replay.processed}/${replay.total} · 재시도 ${replay.failed}건",fontSize=11.sp,color=ModernMuted,modifier=Modifier.padding(horizontal=16.dp,vertical=3.dp))
        else if(replay.total>0)Text("기존 기록 ${replay.processed}건 검토 · 재시도 ${replay.failed}건",fontSize=11.sp,color=ModernMuted,modifier=Modifier.padding(horizontal=16.dp,vertical=3.dp))
        if(remaining>0)Text("정보 정리 대기 ${remaining}건 · 원본은 보관 중",fontSize=11.sp,color=ModernMuted,modifier=Modifier.padding(horizontal=16.dp,vertical=3.dp))
        TabRow(selectedTabIndex=pager.currentPage,containerColor=Color.Transparent){
            listOf("오늘의 흐름","생활 레이더","변화 중심").forEachIndexed{i,label->
                Tab(selected=pager.currentPage==i,onClick={scope.launch{pager.animateScrollToPage(i)}},modifier=Modifier.testTag("smart_concept_$i"),text={Text(label,fontSize=12.sp,maxLines=1,softWrap=false)})
            }
        }
        HorizontalPager(state=pager,modifier=Modifier.weight(1f).testTag("smart_concept_pager"),key={it}){page->content(page)}
    }
}
