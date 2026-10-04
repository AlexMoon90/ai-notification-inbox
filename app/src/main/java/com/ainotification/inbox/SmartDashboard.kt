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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
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

internal val smartCategories=linkedMapOf("money" to "돈","schedule" to "일정·약속","delivery" to "쇼핑·배송","todo" to "할 일","security" to "보안·인증","other" to "기타","investment" to "투자","health" to "건강","work" to "업무","school" to "학교·과제","customer" to "고객관리","project" to "프로젝트")
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
private val DashboardInk=Color(0xff172847)
private val DashboardBlue=Color(0xff287eff)
private val DashboardMuted=Color(0xff607596)
private fun categoryIcon(key:String):ImageVector=when(key){"money"->Icons.Default.AccountBox;"delivery"->Icons.Default.ShoppingCart;"schedule"->Icons.Default.DateRange;"health"->Icons.Default.Favorite;"investment"->Icons.Default.Star;else->Icons.Default.List}

@Composable internal fun SmartDashboard(app:InboxApplication,fixture:Boolean,modifier:Modifier=Modifier,open:(CapturedNotification)->Unit,fixtureEntries:List<StructuredEntry> = emptyList(),loadOriginal:suspend(String)->CapturedNotification?={app.repository.loadBody(it)},onSettings:(()->Unit)?=null) {
 MaterialTheme(colorScheme=lightColorScheme(primary=DashboardBlue,onPrimary=Color.White,surface=Color.White,onSurface=DashboardInk),
  typography=Typography(bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=14.sp,lineHeight=16.sp),bodyMedium=androidx.compose.ui.text.TextStyle(fontSize=13.sp,lineHeight=17.sp)),
  shapes=Shapes(small=RoundedCornerShape(10.dp))) {
  SmartDashboardContent(app,fixture,modifier,open,fixtureEntries,loadOriginal,onSettings)
 }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SmartDashboardContent(app:InboxApplication,fixture:Boolean,modifier:Modifier=Modifier,open:(CapturedNotification)->Unit,fixtureEntries:List<StructuredEntry> = emptyList(),loadOriginal:suspend(String)->CapturedNotification?={app.repository.loadBody(it)},onSettings:(()->Unit)?=null) {
 val prefs=remember(app){app.getSharedPreferences("smart-dashboard-seen",0)}
 val scope=rememberCoroutineScope()
 val source=remember(app,fixture,fixtureEntries){if(fixture)flowOf(fixtureEntries) else app.database.structured().observe()}
 val stored by source.collectAsStateWithLifecycle(initialValue=emptyList())
 val now=System.currentTimeMillis()
 val current=remember(stored){currentInformation(stored)}
 var seen by remember{mutableStateOf(if(fixture)emptySet<String>() else prefs.getStringSet("ids",emptySet()).orEmpty().toSet())}
 var accepted by remember{mutableStateOf(prefs.getStringSet("personal-categories",emptySet()).orEmpty().toSet())}
 var category by rememberSaveable{mutableStateOf<String?>(null)}
 var selected by rememberSaveable{mutableStateOf<String?>(null)}
 var section by rememberSaveable{mutableStateOf("거래 내역")}
 var filter by rememberSaveable{mutableStateOf("전체")}
 var period by rememberSaveable{mutableStateOf("전체")}
 var account by rememberSaveable{mutableStateOf("전체 계좌")}
 var updatesOnly by rememberSaveable{mutableStateOf(false)}
 var raw by rememberSaveable(selected){mutableStateOf(false)}
 var query by rememberSaveable{mutableStateOf("")}
 var search by rememberSaveable{mutableStateOf(false)}
 var statusError by remember{mutableStateOf<String?>(null)}
 val showWork=current.count{it.event.category=="todo" && it.context?.relationship=="work_likely"}>=3
 val groups=current.groupBy{if(showWork && it.event.category=="todo" && it.context?.relationship=="work_likely")"work" else it.event.category}
 val detail=stored.find{it.event.id==selected}
 fun seenKey(e:StructuredEntry)="${e.event.sourceNotificationId}:${e.event.updatedAt}"
 val fresh=current.count{!it.event.isChanged && seenKey(it) !in seen}
 val changed=current.count{it.event.isChanged && seenKey(it) !in seen}
 val relatedIds=remember(detail,stored){
  val d=detail
  if(d==null)emptyList() else (listOf(d.event.sourceNotificationId)+d.context?.let{evidenceIds(it.evidenceIds)}.orEmpty()+
   d.life?.referenceKey?.let{ref->stored.filter{it.life?.referenceKey==ref && it.life?.provider==d.life.provider && it.life?.kind==d.life.kind}.map{it.event.sourceNotificationId}}.orEmpty()+
   d.money?.referenceKey?.let{ref->stored.filter{it.money?.referenceKey==ref && it.money?.provider==d.money.provider && it.money?.accountHint==d.money.accountHint && it.money?.transactionType==d.money.transactionType}.map{it.event.sourceNotificationId}}.orEmpty()+
   d.money?.settledByTransactionEventId?.let{id->listOfNotNull(stored.find{it.event.id==id}?.event?.sourceNotificationId)}.orEmpty()).distinct()
 }
 val originals by produceState<List<CapturedNotification>>(emptyList(),selected,raw,relatedIds){value=if(raw)relatedIds.mapNotNull{loadOriginal(it)} else emptyList()}
 fun enter(key:String,target:String="거래 내역"){category=key;section=if(target=="거래 내역")"거래 내역" else target;filter="전체";period="전체";account="전체 계좌";query=""}
 fun back(){if(selected!=null){selected=null;raw=false}else{category=null;updatesOnly=false;query=""}}
 BackHandler(selected!=null || category!=null || updatesOnly){back()}
 fun mark(e:StructuredEntry){seen=seen+stored.filter{it.event.sourceNotificationId in relatedIds}.map{seenKey(it)}+seenKey(e);if(!fixture)prefs.edit().putStringSet("ids",seen).apply()}
 fun updateStatus(e:StructuredEntry,value:String){scope.launch{statusError=null;try{withContext(Dispatchers.IO){if(e.money!=null)app.database.structured().setMoneyStatus(e.event.id,value,System.currentTimeMillis()) else app.database.structured().setLifeStatus(e.event.id,value);app.database.structured().markChanged(e.event.id,System.currentTimeMillis())}}catch(_:Exception){statusError="상태를 저장하지 못했어요. 다시 시도해 주세요."}}}
 @Composable fun chip(text:String){Surface(color=Color(0xffeef3fb),shape=RoundedCornerShape(6.dp)){Text(text,Modifier.padding(horizontal=6.dp,vertical=3.dp),fontSize=10.sp,color=DashboardMuted)}}
 @Composable fun record(e:StructuredEntry,compact:Boolean=false){
  Surface(onClick={selected=e.event.id},color=Color.White,shape=RoundedCornerShape(13.dp),modifier=Modifier.fillMaxWidth().testTag("smart_item_${e.event.sourceNotificationId}")){
   Row(Modifier.padding(if(compact)9.dp else 12.dp),horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.CenterVertically){
    Surface(color=Color(0xffedf4ff),shape=RoundedCornerShape(10.dp)){Icon(categoryIcon(e.event.category),null,Modifier.padding(7.dp).size(19.dp),tint=DashboardBlue)}
    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){Text(entryTitle(e),Modifier.weight(1f),fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=DashboardInk,maxLines=2,overflow=TextOverflow.Ellipsis)
      if(compact)e.money?.let{m->moneyDisplayAmount(m)?.let{Text(it,Modifier.padding(start=5.dp),fontSize=13.sp,fontWeight=FontWeight.Bold,color=if(m.direction=="in")Color(0xff008b65) else DashboardInk)}}
      if(seenKey(e) !in seen)Text(if(e.event.isChanged)"변경" else "●",fontSize=10.sp,color=Color(0xffec6870))}
     e.money?.let{m->
      if(!compact)moneyDisplayAmount(m)?.let{Text(it,fontSize=15.sp,fontWeight=FontWeight.Bold,color=if(m.direction=="in")Color(0xff008b65) else DashboardInk)}
      moneySourceLabel(m)?.let{Text(it,fontSize=11.sp,color=DashboardMuted)}
     }
     val date=e.life?.dateText ?: e.context?.dateTimeText
     if(!compact)Text(date ?: "수신 ${formatTime(e.event.observedAt)}",fontSize=11.sp,color=DashboardMuted,maxLines=1)
     entryStatus(e,now)?.let{chip(it)}
    }
    Icon(Icons.Default.KeyboardArrowRight,"상세 보기",Modifier.size(18.dp),tint=DashboardMuted)
   }
  }
 }
 @Composable fun smallCard(key:String,rows:List<StructuredEntry>,modifier:Modifier=Modifier){
  Surface(onClick={enter(key)},color=Color.White,shape=RoundedCornerShape(17.dp),modifier=modifier.testTag("smart_category_$key")){
   Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){Icon(categoryIcon(key),null,tint=DashboardBlue,modifier=Modifier.size(20.dp));Text(smartCategories[key] ?: key,Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=14.sp,color=DashboardInk);Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(16.dp),tint=DashboardMuted)}
    val active=when(key){"delivery"->rows.count{it.life?.status in setOf("ordered","preparing","shipping","arriving_today")};"schedule"->rows.count{it.life?.scheduledAt?.let{t->t>=now}==true && it.life?.status!="cancelled"};else->rows.size}
    Text(when(key){"delivery"->"진행 ${active}건";"schedule"->"예정 ${active}건";else->"${rows.size}개 기록"},fontSize=12.sp,fontWeight=FontWeight.Medium,color=DashboardInk)
    val unseen=rows.count{seenKey(it) !in seen};val changes=rows.count{it.event.isChanged && seenKey(it) !in seen}
    if(unseen>0)Text("새 정보 ${unseen-changes} · 변경 $changes",fontSize=10.sp,color=DashboardBlue)
    rows.firstOrNull()?.let{e->Text(entryTitle(e),fontSize=12.sp,color=DashboardInk,maxLines=1,overflow=TextOverflow.Ellipsis);Text(e.life?.dateText ?: entryStatus(e,now) ?: "최근 수신 기록",fontSize=10.sp,color=DashboardMuted,maxLines=2)} ?: Text("정보가 들어오면 정리해요",fontSize=11.sp,color=DashboardMuted)
   }
  }
 }
 @Composable fun choices(values:List<String>,selectedValue:String,onSelect:(String)->Unit){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){values.forEach{value->FilterChip(selectedValue==value,{onSelect(value)},label={Text(value,fontSize=12.sp)},modifier=Modifier.testTag("smart_filter_$value"))}}}
 @Composable fun metric(label:String,value:String){Row(Modifier.fillMaxWidth().padding(vertical=6.dp)){Text(label,Modifier.weight(1f),fontSize=13.sp,color=DashboardMuted);Text(value,fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=DashboardInk)}}
 val bg=Color(0xfff5f8fc)
 LazyColumn(modifier.fillMaxSize().background(bg).testTag("smart_dashboard_2"),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   if(selected!=null || category!=null || updatesOnly)TextButton(onClick={back()}){Text("‹ 뒤로")}
   else Column(Modifier.weight(1f)){Text("내 하루의 모든 정보",fontSize=11.sp,color=DashboardMuted);Text("스마트함",fontSize=27.sp,fontWeight=FontWeight.Bold,color=DashboardInk)}
   if(category!=null)Text(smartCategories[category] ?: category.orEmpty(),Modifier.weight(1f),fontSize=22.sp,fontWeight=FontWeight.Bold,color=DashboardInk)
   IconButton(onClick={search=!search}){Icon(Icons.Default.Search,"구조화 정보 검색",tint=DashboardInk)}
   if(onSettings!=null)IconButton(onClick=onSettings,modifier=Modifier.testTag("open_settings")){Icon(Icons.Default.Settings,"설정",tint=DashboardInk)}
  }}
  if(search)item{OutlinedTextField(query,{query=it},singleLine=true,placeholder={Text("상대방·가맹점·계좌 검색")},modifier=Modifier.fillMaxWidth().testTag("smart_search"))}
  if(statusError!=null)item{Text(statusError!!,color=MaterialTheme.colorScheme.error)}
  when {
   detail!=null -> {
    val e=detail
    item{Text(entryTitle(e),fontSize=24.sp,fontWeight=FontWeight.Bold,color=DashboardInk)}
    e.money?.let{m->
     item{moneyDisplayAmount(m)?.let{Text(it,fontSize=30.sp,fontWeight=FontWeight.Bold,color=DashboardInk)}}
     item{moneySourceLabel(m)?.let{Text(it,color=DashboardMuted)}}
     m.balanceAfter?.let{item{metric("거래 후 잔액",moneyText(it))}}
     m.paymentMethod?.let{item{metric("결제수단",it)}}
     if(m.recurring)item{chip("정기결제")}
     m.dueAt?.let{item{metric("납부기한",formatTime(it))}}
     entryStatus(e,now)?.let{item{chip(it)}}
     if(m.transactionType in requestTypes){
      item{Text("알림으로 납부가 확인되면 상태가 연결됩니다. 직접 처리한 경우 아래에서 변경할 수 있어요.",fontSize=12.sp,color=DashboardMuted)}
      item{choices(listOf("pending","processing","completed","overdue","cancelled").map{informationStatuses.getValue(it)},informationStatuses[m.status].orEmpty()){label->updateStatus(e,informationStatuses.entries.first{it.value==label}.key)}}
      m.settledByTransactionEventId?.let{id->item{TextButton(onClick={selected=id}){Text("연결된 거래 보기")}}}
     }
    }
    e.life?.let{l->
     item{chip(informationStatuses[l.status] ?: l.status)}
     listOf("일시" to l.dateText,"장소" to l.place,"참여자" to l.participant,"배송사" to l.carrier,"상품" to l.itemName).forEach{(label,value)->if(value!=null)item{metric(label,value)}}
     l.value?.let{item{metric(l.title,"${java.text.NumberFormat.getIntegerInstance().format(it)}${l.unit.orEmpty()}")}}
     if(l.kind=="schedule")item{choices(listOf("확정","완료","취소"),informationStatuses[l.status].orEmpty()){updateStatus(e,when(it){"완료"->"completed";"취소"->"cancelled";else->"confirmed"})}}
    }
    e.context?.let{c->
     item{Text(if(c.needsContext)"관련 정보 · 확인 필요" else "확정된 정보",fontWeight=FontWeight.SemiBold)}
     c.dateTimeText?.let{item{metric("언급된 일시",it)}}
     if(c.relationship!="unknown")item{chip(if(c.relationship=="work_likely")"업무 가능성" else "개인 일정")}
     if(c.contextCompleteness!="full")item{Text("수신된 메시지만 확인했어요. 사용자가 보낸 메시지와 전체 대화는 포함되지 않을 수 있습니다.",fontSize=12.sp,color=DashboardMuted)}
    }
    item{Text("${e.event.sourceApp} · 수신 ${formatTime(e.event.observedAt)}",fontSize=12.sp,color=DashboardMuted)}
    item{TextButton(onClick={mark(e)},modifier=Modifier.testTag("smart_mark_seen")){Text(if(seenKey(e) in seen)"확인한 내용이에요" else "내용 확인했어요")}}
    item{TextButton(onClick={raw=!raw},modifier=Modifier.testTag("smart_original")){Text(if(raw)"원본 접기" else "원본 알림 보기")}}
    if(raw){if(originals.isEmpty())item{Text("원본을 불러올 수 없어요.",fontSize=12.sp)};items(originals,key={it.snapshotId}){r->Surface(color=Color.White,shape=RoundedCornerShape(12.dp)){Column(Modifier.padding(12.dp)){Text(r.title.orEmpty(),fontWeight=FontWeight.Bold);Text(r.preview());TextButton(onClick={open(r)}){Text("원래 앱에서 보기")}}}}}
   }
   category=="money" -> {
    item{choices(moneySections,section){section=it;filter="전체";account="전체 계좌";period=if(it=="통계")"이번 주" else "전체"}}
    val moneyRows=current.filter{it.money!=null}
    item{choices(if(section=="통계")listOf("이번 주","이번 달","최근 3개월") else listOf("전체","오늘","이번 주","이번 달","최근 3개월"),period){period=it}}
    if(section=="통계"){
     val summary=summarizeMoney(moneyRows,period,now)
     item{Surface(color=Color(0xffeaf3ff),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(16.dp)){
      metric("확인된 결제 지출",moneyText(summary.spending));metric("들어온 돈",moneyText(summary.income));metric("계좌 출금·송금",moneyText(summary.accountOut));metric("환불",moneyText(summary.refunds));metric("정기결제 ${summary.recurringCount}건",moneyText(summary.recurring));metric("확인된 기록","${summary.count}건")
      summary.comparison?.let{Text("이전 기간 같은 경과 시점 대비 ${if(it>0)"+" else ""}$it%",fontSize=12.sp,color=DashboardMuted)}
     }}}
     item{Text("결제수단별 사용",fontWeight=FontWeight.Bold)}
     items(summary.methods){(label,value)->metric(label,moneyText(value))}
     item{Text("많이 사용한 가맹점",fontWeight=FontWeight.Bold)}
     items(summary.merchants){(label,value)->metric(label,moneyText(value))}
     item{Text("수집된 완료 알림 기준입니다. 계좌 출금은 결제 지출에 더하지 않으며, 미확정·청구·받기 전 송금은 제외합니다. 누락·중복 알림이 있을 수 있어 은행 명세서 합계와 다를 수 있습니다. 분야 분류 근거가 없는 거래에는 지출 분야를 붙이지 않습니다.",fontSize=11.sp,color=DashboardMuted)}
    }else{
     if(section=="거래 내역")item{choices(listOf("전체","카드","계좌이체","페이","정기결제","환불·취소"),filter){filter=it}}
     if(section=="계좌 입출금"){
      val accounts=moneyRows.mapNotNull{it.money?.let(::moneySourceLabel)?.takeIf{_ -> it.money.accountHint!=null}}.distinct()
      if(accounts.isNotEmpty())item{choices(listOf("전체 계좌")+accounts,account){account=it}}
     }
     if(section=="내야 할 돈")item{choices(listOf("전체","미처리","처리 중","처리 완료","미납","취소"),filter){filter=it}}
     val rows=moneyRows.filter{e->matchesMoney(e,section,if(section=="거래 내역")filter else "전체") && eventTime(e) in periodStart(period,now)..now && (account=="전체 계좌" || moneySourceLabel(e.money!!)==account) && (section!="내야 할 돈" || filter=="전체" || informationStatuses[obligationStatus(e.money!!,now)]==filter) && (query.isBlank() || (entryTitle(e)+e.money?.let(::moneySourceLabel)).contains(query,true))}
     if(rows.isEmpty())item{Text("해당하는 기록이 없어요.",color=DashboardMuted,modifier=Modifier.padding(16.dp))}
     items(rows,key={it.event.id}){record(it)}
     if(section=="거래 내역" && filter=="전체") {
      val uncertain=moneyRows.filter{it.money!!.transactionType in setOf("money_related_unknown","transfer_related","transfer_in_pending") && eventTime(it) in periodStart(period,now)..now && (query.isBlank() || (entryTitle(it)+it.money?.let(::moneySourceLabel)).contains(query,true))}
      if(uncertain.isNotEmpty())item{Text("확인할 금융 정보",fontWeight=FontWeight.Bold)}
      items(uncertain,key={it.event.id}){record(it)}
     }
    }
   }
   category!=null || updatesOnly || query.isNotBlank() -> {
    val key=category
    val filters=when(key){"delivery"->listOf("전체","주문","배송 준비","배송 중","오늘 도착","완료","반품","환불");"schedule"->listOf("전체","오늘","예정","변경","확인 필요","완료","취소","지난 일정");else->listOf("전체")}
    if(key!=null)item{choices(filters,filter){filter=it}}
    val sourceRows=if(key==null)current else groups[key].orEmpty()
    val rows=sourceRows.filter{categoryFilter(it,filter,now) && (!updatesOnly || seenKey(it) !in seen) && (query.isBlank() || (entryTitle(it)+it.money?.let(::moneySourceLabel)).contains(query,true))}
    if(rows.isEmpty())item{Text("해당하는 정보가 없어요.",color=DashboardMuted,modifier=Modifier.padding(16.dp))}
    items(rows,key={it.event.id}){record(it)}
    if(filter=="전체" && key in setOf("delivery","schedule")){
     item{Text("지난 알림 기록",fontSize=13.sp,fontWeight=FontWeight.Bold)}
     val history=stored.filter{it.event.category==key && it.event.id !in sourceRows.map{r->r.event.id} && validSmartEntry(it)}
     items(history,key={"history:${it.event.id}"}){record(it)}
    }
   }
   else -> {
    item{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Surface(color=DashboardBlue,shape=RoundedCornerShape(20.dp),modifier=Modifier.testTag("smart_concept_2")){Text("대시보드",Modifier.padding(horizontal=14.dp,vertical=6.dp),fontSize=12.sp,color=Color.White)}
     TextButton(onClick={updatesOnly=true},modifier=Modifier.testTag("smart_all_changes")){Text("새 정보 $fresh · 변경 $changed",fontSize=12.sp)}
    }}
    item{Surface(color=Color(0xffeaf3ff),shape=RoundedCornerShape(20.dp),modifier=Modifier.testTag("smart_category_money").clickable{enter("money")} ){
     Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
      val summary=summarizeMoney(current,"이번 주",now)
      Row(Modifier.fillMaxWidth().clickable{enter("money")},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
       Surface(color=Color(0xffd6e7ff),shape=RoundedCornerShape(16.dp)){Icon(Icons.Default.AccountBox,null,Modifier.padding(12.dp).size(28.dp),tint=DashboardBlue)}
       Column(Modifier.weight(1f)){Text("돈",fontSize=22.sp,fontWeight=FontWeight.Bold,color=DashboardInk);Text("이번 주 · 확인된 결제",fontSize=11.sp,color=DashboardMuted);Text(moneyText(summary.spending),fontSize=26.sp,fontWeight=FontWeight.Bold,color=DashboardInk)}
       Icon(Icons.Default.KeyboardArrowRight,"돈 상세",tint=DashboardMuted)
      }
      summary.comparison?.let{Text("지난주 같은 경과 시점 대비 ${if(it>0)"+" else ""}$it%",fontSize=11.sp,color=DashboardMuted)}
      Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
       moneySections.forEachIndexed{i,label->Surface(onClick={enter("money",label)},color=Color.White,shape=RoundedCornerShape(13.dp),modifier=Modifier.weight(1f).testTag("money_shortcut_$i")){
        Column(Modifier.padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){Icon(listOf(Icons.Default.AccountBox,Icons.Default.List,Icons.Default.Info,Icons.Default.DateRange)[i],null,Modifier.size(22.dp),tint=DashboardBlue);Text(label,fontSize=10.sp,fontWeight=FontWeight.Medium,maxLines=1)}
       }}
      }
      Row(verticalAlignment=Alignment.CenterVertically){Text("최근 거래",Modifier.weight(1f),fontSize=12.sp,color=DashboardMuted);TextButton(onClick={enter("money")},modifier=Modifier.height(28.dp),contentPadding=PaddingValues(0.dp)){Text("더보기",fontSize=11.sp)}}
      val recent=current.filter{it.money!=null}.take(2)
      if(recent.isEmpty())Text("금융 알림이 들어오면 거래를 정리해요.",fontSize=12.sp,color=DashboardMuted)
      recent.forEach{record(it,true)}
     }
    }}
    item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){smallCard("delivery",groups["delivery"].orEmpty(),Modifier.weight(1f));smallCard("schedule",groups["schedule"].orEmpty(),Modifier.weight(1f))}}
    val optional=listOf("investment","health","work").filter{groups[it].orEmpty().size>=3}+accepted.filter{groups[it].orEmpty().isNotEmpty()}
    optional.distinct().chunked(3).forEach{keys->item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){keys.forEach{smallCard(it,groups[it].orEmpty(),Modifier.weight(1f))}}}}
    val others=listOf("todo","other","security").filter{groups[it].orEmpty().isNotEmpty()}
    others.chunked(2).forEach{keys->item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){keys.forEach{smallCard(it,groups[it].orEmpty(),Modifier.weight(1f))}}}}
    listOf("school","customer","project").filter{it !in accepted && groups[it].orEmpty().size>=3}.forEach{key->item{Surface(color=Color(0xffeef0ff),shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(12.dp)){Text("새로운 흐름이 보여요",fontWeight=FontWeight.Bold);Text("${smartCategories[key]} 관련 정보 ${groups[key].orEmpty().size}건",fontSize=12.sp);TextButton(onClick={accepted=accepted+key;prefs.edit().putStringSet("personal-categories",accepted).apply()}){Text("따로 묶기")}}}}}
    item{Text("알림에서 확인된 정보만 정리합니다. 과거 기록과 원본은 카테고리 안에서 확인하세요.",fontSize=11.sp,color=DashboardMuted)}
   }
  }
 }
}
