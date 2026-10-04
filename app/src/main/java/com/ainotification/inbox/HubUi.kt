package com.ainotification.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun InboxScreen(app:InboxApplication,granted:Boolean,settings:()->Unit,open:(CapturedNotification)->Unit,preview:Boolean=false,
    fixtureRows:List<CapturedNotification>?=null,fixtureDecisions:JSONObject?=null,fixtureFacts:List<HubClassification>?=null,initialTab:Int=0,initialSnapshot:String?=null) {
    var tab by rememberSaveable{mutableIntStateOf(initialTab)}
    var settingsPage by rememberSaveable{mutableStateOf<Int?>(null)}
    var roomId by rememberSaveable{mutableStateOf<String?>(null)}
    var jumpMessageId by rememberSaveable{mutableStateOf<String?>(null)}
    var sourceHistory by rememberSaveable{mutableStateOf<String?>(null)}
    var detailId by rememberSaveable{mutableStateOf<String?>(null)}
    LaunchedEffect(initialSnapshot){if(initialSnapshot!=null){tab=0;detailId=initialSnapshot}}
    var detailMessageKey by rememberSaveable{mutableStateOf<String?>(null)}
    var recommendation by rememberSaveable{mutableStateOf<String?>(null)}
    var category by rememberSaveable{mutableStateOf<String?>(null)}
    var service by rememberSaveable{mutableStateOf<String?>(null)}
    var voiceEntry by rememberSaveable{mutableStateOf(false)}
    var nowSort by rememberSaveable{mutableStateOf("time")}
    var collapsedApps by rememberSaveable{mutableStateOf(arrayListOf<String>())}
    var review by rememberSaveable{mutableStateOf(false)}
    val fixtureMode=preview || fixtureRows!=null || fixtureDecisions!=null || fixtureFacts!=null
    val fixtureFlow=remember(app,preview,fixtureRows,fixtureDecisions,fixtureFacts){
        if(fixtureMode)hubScreenData(
            fixtureRows?.let{flowOf(it)} ?: if(preview)flowOf(previewNotifications()) else app.repository.all,
            fixtureDecisions?.let{flowOf(JSONObject().put("results",it))} ?: app.selection.state,
            fixtureFacts?.let{flowOf(it)} ?: app.database.hub().observe()) else null
    }
    val data=if(fixtureFlow!=null){
        fixtureFlow.collectAsStateWithLifecycle(initialValue=HubScreenData()).value
    }else{
        // StateFlow's current value is the initial UI value on every Activity re-entry.
        val nowData by app.screenStore.now.collectAsStateWithLifecycle()
        if((tab==0 || tab==2) && roomId==null)nowData else app.screenStore.full.collectAsStateWithLifecycle().value
    }
    val rows=data.records.rows
    val state=data.state
    val facts=data.facts
    val decisions=data.decisions
    val rooms=data.records.rooms
    val room=rooms.find{it.id==roomId}
    val displayPrefs=remember(app){app.getSharedPreferences("conversation-display",android.content.Context.MODE_PRIVATE)}
    var newestFirst by rememberSaveable{mutableStateOf(displayPrefs.getBoolean("newest-first",true))}
    var orderMenu by remember{mutableStateOf(false)}
    val orderedMessages=remember(room?.messages,newestFirst){if(newestFirst)room?.messages?.asReversed().orEmpty() else room?.messages.orEmpty()}
    val readStore=remember(app){ConversationReadStore(app)}
    var readRevision by remember{mutableIntStateOf(0)}
    val unread by produceState<Map<String,Int>>(emptyMap(),rooms,readRevision){
        value=withContext(Dispatchers.IO){rooms.associate{it.id to readStore.unread(it)}}
    }
    LaunchedEffect(roomId,room?.messages){
        room?.let{if(withContext(Dispatchers.IO){readStore.markRead(it)})readRevision++}
    }
    val listState=remember(roomId,detailId,sourceHistory,review,tab){androidx.compose.foundation.lazy.LazyListState()}
    LaunchedEffect(roomId,room?.id,newestFirst,detailId) {
        if(room!=null && detailId==null && jumpMessageId==null && orderedMessages.isNotEmpty()){
            listState.scrollToItem(if(newestFirst)0 else orderedMessages.size)
        }
    }
    LaunchedEffect(roomId,jumpMessageId,room?.messages?.size,newestFirst) {
        val target=jumpMessageId
        if(target!=null && room!=null){
            val index=orderedMessages.indexOfFirst{it.identity==target}
            if(index>=0){listState.scrollToItem(index+1);jumpMessageId=null}
        }
    }
    fun openNow(row:CapturedNotification){
        if(messageService(row)!=null){
            detailId=null;detailMessageKey=null
            jumpMessageId=conversationRooms(listOf(row)).firstOrNull()?.latest?.identity
            roomId=conversationRoomId(row)
        }else{detailMessageKey=null;if(messageService(row)==null && !review){sourceHistory=row.packageName;detailId=null}else detailId=row.snapshotId}
    }
    val fetchedDetail by produceState<CapturedNotification?>(null,detailId) {
        value=if(!fixtureMode && detailId!=null)app.repository.loadBody(detailId!!) else null
    }
    val originalDetail=fetchedDetail ?: rows.find{it.snapshotId==detailId}
    val selectedMessage=originalDetail?.takeIf{detailMessageKey!=null}?.let{conversationRooms(listOf(it)).firstOrNull()?.messages?.find{m->m.identity==detailMessageKey}}
        ?: room?.messages?.find{it.identity==detailMessageKey}
    val detail=originalDetail?.let{r->if(selectedMessage!=null)r.copy(text=selectedMessage.text,bigText=null,messagesJson="[]",postedTime=selectedMessage.time) else r}
    if(!fixtureMode && data.loaded && !state.optBoolean("onboarding_complete") && !state.optBoolean("initial_setup_deferred") && state.optJSONObject("policy")==null) {
        FirstOnboardingScreen(app,rows,granted,settings,{app.policies.deferInitialSetup()});return
    }
    if(settingsPage!=null){LegacySettingsScreen(app,granted,settings,open,preview,settingsPage!!,startVoice=voiceEntry){settingsPage=null;voiceEntry=false};return}
    fun back(){when{detailId!=null->{detailId=null;detailMessageKey=null};roomId!=null->{roomId=null;jumpMessageId=null};sourceHistory!=null->sourceHistory=null;category!=null->category=null;else->review=false}}
    val inDetail=detailId!=null || roomId!=null || sourceHistory!=null || review
    val pending=data.pending
    val visibleNow=data.now
    val visible=if(review)pending else visibleNow
    val timeGroups=if(review)data.pendingByTime else data.nowByTime
    val appGroups=data.nowByApp
    BackHandler(inDetail || category!=null){back()}
    Scaffold(modifier=Modifier.semantics{testTagsAsResourceId=true}.testTag(if(data.loaded)"hub_ready" else "hub_loading"),topBar={
        if(tab==2 && !inDetail)Spacer(Modifier.statusBarsPadding()) else Column(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal=16.dp).padding(top=8.dp,bottom=24.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                if(inDetail)IconButton(onClick={back()}){Icon(Icons.Outlined.ArrowBack,"뒤로")}
                Text(when{detailId!=null->"원문";roomId!=null->"대화";sourceHistory!=null->"알림 기록";review->"기준 확인";tab==1->"";tab==2->"";else->java.text.SimpleDateFormat("M월 d일 EEEE",java.util.Locale.KOREAN).format(java.util.Date())},style=MaterialTheme.typography.bodySmall,color=ModernMuted,modifier=Modifier.weight(1f))
                val source=if(detail==null)room?.latest?.source else null
                if(source!=null)SourceOpenButton(source){open(source)}
                if(!inDetail)IconButton(onClick={settingsPage=2},modifier=Modifier.size(40.dp).testTag("open_settings")){Icon(Icons.Outlined.Settings,"설정",Modifier.size(20.dp))}
            }
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(when{detailId!=null->"알림 상세";roomId!=null->room?.name ?: "대화";sourceHistory!=null->rows.firstOrNull{it.packageName==sourceHistory}?.appLabel ?: "알림 기록";review->"따로 확인할 알림";else->listOf("Now","메시지","스마트함")[tab]},modifier=Modifier.weight(1f),style=if(inDetail)MaterialTheme.typography.titleLarge else if(tab==0)MaterialTheme.typography.displaySmall.copy(fontSize=42.sp,lineHeight=46.sp) else MaterialTheme.typography.displaySmall,maxLines=2)
                if(!inDetail && tab==0)OutlinedButton(onClick={settingsPage=1},border=androidx.compose.foundation.BorderStroke(1.dp,ModernInk),modifier=Modifier.heightIn(min=36.dp).testTag("open_policy")){Text("알림 기준",color=ModernInk,style=MaterialTheme.typography.labelMedium)}
            }
            if(!preview && !inDetail && tab==0 && app.nowAlerts.debug)TextButton(onClick={
                app.startActivity(android.content.Intent().setClassName(app,"${app.packageName}.NowAlertSettingsActivity").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }){Text("Now 소리·팝업 설정")}
            if(room!=null && detailId==null)Box(Modifier.align(Alignment.End)){
                TextButton(onClick={orderMenu=true},modifier=Modifier.testTag("conversation_order")){
                    Text(if(newestFirst)"↑ 최신이 위" else "↓ 최신이 아래",style=MaterialTheme.typography.labelMedium,color=ModernMuted)
                }
                DropdownMenu(expanded=orderMenu,onDismissRequest={orderMenu=false}){
                    listOf(true to "최신이 위",false to "최신이 아래").forEach{(value,label)->
                        DropdownMenuItem(text={Text(label)},onClick={
                            orderMenu=false;newestFirst=value
                            displayPrefs.edit().putBoolean("newest-first",value).apply()
                        })
                    }
                }
            }
            if(!inDetail && tab!=2)Text(listOf("급하고 중요한 것은 지금","모든 메시지를 한곳에서 조용히","필요한 정보는 보관했다가 나중에")[tab],style=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.Medium,lineHeight=24.sp),color=ModernInk,modifier=Modifier.padding(top=10.dp).testTag("hub_subtitle"))
        }
    },bottomBar={if(!inDetail)Column(Modifier.navigationBarsPadding()){
        if(tab==0){ModernLine(true);Row(Modifier.fillMaxWidth().background(ModernSurface).clickable{voiceEntry=true;settingsPage=1}.padding(horizontal=16.dp,vertical=6.dp).testTag("now_voice"),verticalAlignment=Alignment.CenterVertically){Text("말로 기준 만들기",Modifier.weight(1f),color=ModernMuted);Box(Modifier.size(40.dp).background(ModernAccent),contentAlignment=Alignment.Center){Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_mic),"말로 기준 만들기",tint=ModernBg)}}}
        ModernLine(true)
        Row(Modifier.fillMaxWidth().height(56.dp)){
            listOf("Now","메시지","스마트함").forEachIndexed{i,label->
                Column(Modifier.weight(1f).fillMaxHeight().selectable(tab==i,role=Role.Tab,onClick={tab=i;service=null;category=null}).testTag("nav_$i")){
                    Box(Modifier.fillMaxWidth().height(4.dp).background(if(tab==i)ModernAccent else Color.Transparent))
                    Column(Modifier.padding(horizontal=16.dp,vertical=12.dp)){Text(label,fontWeight=if(tab==i)FontWeight.ExtraBold else FontWeight.Medium,color=if(tab==i)ModernInk else ModernMuted)}
                }
                if(i<2)VerticalDivider(color=ModernInk.copy(alpha=.4f))
            }
        }
    }}){padding->
        if(!data.loaded && (tab!=0 || roomId!=null)) {
            Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center){Text("알림을 불러오고 있어요",color=ModernMuted)}
        } else if(tab==2 && !inDetail) {
            SmartDashboard(app,fixtureMode,Modifier.fillMaxSize().padding(padding),open,onSettings={settingsPage=2})
        } else if(tab==1 && !inDetail) {
            MessengerRooms(rooms,unread,service,{service=it},{roomId=it},Modifier.fillMaxSize().padding(padding))
        } else LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp).testTag("hub_list"),state=listState,contentPadding=PaddingValues(bottom=24.dp)){
            when {
                detail!=null->{
                    item{
                        Surface(color=Color.White,shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){
                            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                    SourceAppIcon(detail.packageName,detail.appLabel,size=20.dp)
                                    Text(hubAppLabel(detail),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=ModernMuted)
                                }
                                Text(notificationDisplayTitle(detail),style=MaterialTheme.typography.titleMedium.copy(fontSize=18.sp,lineHeight=26.sp),fontWeight=FontWeight.SemiBold)
                                val sender=selectedMessage?.sender ?: originalDetail?.latestMessage()?.stringOrNull("sender")
                                if(!sender.isNullOrBlank())Text(sender,style=MaterialTheme.typography.labelLarge,color=ModernMuted)
                                val caption=photoCaption(detail.preview(),selectedMessage?.hasImage ?: (detail.latestMessage()?.isImageAttachment()==true),sender)
                                if(caption.isNotBlank())Text(caption,style=MaterialTheme.typography.bodyMedium.copy(lineHeight=24.sp))
                                NotificationImage(selectedMessage?.imageFile ?: detail.latestMessage()?.stringOrNull("imageFile"),selectedMessage?.hasImage ?: (detail.latestMessage()?.isImageAttachment()==true))
                                if(detail.needsOriginalReview())Text("긴 내용 · 원본 확인 필요",style=MaterialTheme.typography.labelSmall,color=ModernMuted)
                            }
                        }
                    }
                    item{NotificationFooter(detail){open(detail)}}
                    item{
                        TextButton(onClick={recommendation=detail.snapshotId},modifier=Modifier.padding(top=12.dp).fillMaxWidth().testTag("notification_policy_settings")){
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                Icon(Icons.Outlined.Settings,null,Modifier.size(18.dp),tint=ModernMuted)
                                Text("알림 기준 설정",Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.labelLarge,color=ModernInk)
                                Icon(Icons.Outlined.ArrowForward,null,Modifier.size(18.dp),tint=ModernMuted)
                            }
                        }
                    }
                }
                sourceHistory!=null->{
                    val history=data.records.history[sourceHistory].orEmpty()
                    item{Text("최근 알림부터 · 저장된 기록",style=MaterialTheme.typography.labelSmall,color=ModernMuted,modifier=Modifier.padding(bottom=12.dp))}
                    items(history,key={it.snapshotId}){row->HubNotificationRow(row,null,{recommendation=row.snapshotId},fullText=true,onSource={open(row)}){detailId=row.snapshotId}}
                }
                roomId!=null->{
                    if(room==null)item{EmptyHub("대화 기록이 없습니다.")}
                    else {
                        item{ModernLine(true)}
                        items(orderedMessages,key={it.identity}){previewMessage->
                            val message by produceState(previewMessage,previewMessage) {
                                if(!fixtureMode && previewMessage.source.availableFields.endsWith(DISPLAY_PREVIEW)) {
                                    val full=app.repository.loadBody(previewMessage.source.snapshotId)
                                    val found=full?.let{conversationRooms(listOf(it)).firstOrNull()?.messages?.find{m->m.identity==previewMessage.identity}}
                                    if(found!=null)value=found.copy(imageFile=found.imageFile ?: previewMessage.imageFile,senderAvatarFile=found.senderAvatarFile ?: previewMessage.senderAvatarFile)
                                }
                            }
                            Row(Modifier.fillMaxWidth().clickable{detailMessageKey=message.identity;detailId=message.source.snapshotId}.padding(top=12.dp,bottom=16.dp,end=20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top){
                                val sender=message.sender?.takeIf{it.isNotBlank()}
                                if(sender!=null)ConversationAvatar(message.senderAvatarFile,sender,sender=true,size=36.dp)
                                else Spacer(Modifier.width(36.dp))
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                                    if(sender!=null)Text(sender,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.labelLarge)
                                    val caption=photoCaption(message.text,message.hasImage,message.sender)
                                    if(message.hasImage){
                                        if(caption.isNotBlank())Text(caption,style=MaterialTheme.typography.bodyMedium)
                                        NotificationImage(message.imageFile,true)
                                    }else Surface(
                                        color=Color.White,
                                        shape=androidx.compose.foundation.shape.RoundedCornerShape(topStart=3.dp,topEnd=12.dp,bottomEnd=12.dp,bottomStart=12.dp),
                                        border=androidx.compose.foundation.BorderStroke(1.dp,ModernInk.copy(alpha=.10f)),
                                        modifier=Modifier.fillMaxWidth().testTag("message_bubble")
                                    ) {
                                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                                            if(caption.isNotBlank())Text(caption,style=MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    Text(formatTime(message.time),modifier=Modifier.align(Alignment.End).padding(end=4.dp,top=2.dp).testTag("message_time"),style=MaterialTheme.typography.labelSmall,color=ModernMuted)
                                }
                            }
                        }
                        item{Text("수집하지 못한 메시지는 표시되지 않습니다.",style=MaterialTheme.typography.bodySmall)}
                    }
                }
                tab==0->{
                    if(!granted)item{TextButton(onClick=settings){Text("알림 접근 허용")}}
                    if(!review){
                        item{
                            Column(Modifier.fillMaxWidth().padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                val policy=state.optJSONObject("policy")
                                val active=policy!=null && (!policy.has("rules") || jsonObjects(policy.getJSONArray("rules")).any{it.optBoolean("enabled")})
                                val title=when{
                                    !data.loaded->"알림을 불러오고 있어요"
                                    !granted->"알림을 받아볼 준비를 해주세요"
                                    !active->"어떤 알림을 볼지 정해보세요"
                                    visibleNow.isNotEmpty()->"지금 확인할 알림이 있어요"
                                    pending.isNotEmpty()->"아직 확인이 필요한 알림이 있어요"
                                    else->"지금은 신경 쓸 알림이 없어요."
                                }
                                if(!granted || !active || visibleNow.isEmpty()){
                                Text(title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("now_status"))
                                Text(when{!data.loaded->"저장된 기록을 정리하고 있어요.";!granted->"알림 접근을 허용하면 여기에서 모아볼 수 있어요.";!active->"알림에서 기준을 만들거나, 원하는 내용을 말해주세요.";visibleNow.any{decisions.optJSONObject(it.snapshotId)?.optString("status")=="review"}->"내용을 확인하지 못한 사진도 함께 보여드려요.";visibleNow.isNotEmpty()->"최근 48시간 내 기준에 맞는 알림이에요. 이전 기록은 메시지·스마트함에서 볼 수 있어요.";pending.isNotEmpty()->"기준에 맞는지 확인하지 못한 알림도 따로 보관했어요.";else->"필요한 알림이 들어오면 여기에 보여드릴게요."},style=MaterialTheme.typography.bodyMedium,color=ModernMuted)
                                }
                                if(pending.isNotEmpty())TextButton(onClick={review=true},modifier=Modifier.testTag("open_review")){Text("따로 확인할 알림 보기",color=ModernMuted)}
                            }
                            ModernLine(true)
                        }
                        item{Row(Modifier.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
                            Row(Modifier.border(1.dp,ModernInk)){listOf("time" to "시간순","app" to "앱별").forEach{(value,label)->Box(Modifier.background(if(nowSort==value)ModernAccent else Color.Transparent).selectable(nowSort==value,role=Role.Tab,onClick={nowSort=value}).testTag("now_sort_$value").padding(horizontal=14.dp,vertical=8.dp)){Text(label,style=MaterialTheme.typography.labelMedium,color=if(nowSort==value)ModernBg else ModernInk)}}}
                            Spacer(Modifier.weight(1f));Text(if(nowSort=="time")"최신이 위" else "최근 받은 앱이 위",style=MaterialTheme.typography.bodySmall,color=ModernMuted)
                        }}
                    }else item{Text("아직 판단하지 못한 알림이에요. 조용히 버리지 않고 여기에 모아둬요.",color=ModernMuted,modifier=Modifier.padding(bottom=16.dp))}
                    if(visible.isEmpty() && review)item{EmptyHub("따로 확인할 알림이 없어요.")}
                    if(!review && nowSort=="app"){
                        appGroups.forEach{(pkg,group)->
                            item(key="group:$pkg"){ModernLine(true);Row(Modifier.fillMaxWidth().clickable{collapsedApps=ArrayList(if(pkg in collapsedApps)collapsedApps-pkg else collapsedApps+pkg)}.testTag("now_app_group_$pkg").padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                SourceAppIcon(group.first().packageName,hubAppLabel(group.first()));Text(hubAppLabel(group.first()),fontWeight=FontWeight.ExtraBold);Spacer(Modifier.weight(1f));Text(if(pkg in collapsedApps)"펼치기 +" else "접기 −",style=MaterialTheme.typography.bodySmall)
                            }}
                            if(pkg !in collapsedApps)items(group,key={it.snapshotId}){row->HubNotificationRow(row,if(decisions.optJSONObject(row.snapshotId)?.optString("status")=="review" && row.latestMessage()?.isImageAttachment()==true)"사진 · 기준 확인 필요" else hubBadge(facts[row.snapshotId]),{recommendation=row.snapshotId},showSource=false,onSource={open(row)}){openNow(row)}}
                        }
                    }else{
                        timeGroups.forEach{(period,group)->
                            item(key="period:$period"){Text(period,style=MaterialTheme.typography.labelSmall,color=ModernMuted,modifier=Modifier.padding(top=12.dp,bottom=4.dp))}
                            items(group,key={it.snapshotId}){row->HubNotificationRow(row,if(decisions.optJSONObject(row.snapshotId)?.optString("status")=="review" && row.latestMessage()?.isImageAttachment()==true)"사진 · 기준 확인 필요" else hubBadge(facts[row.snapshotId]),{recommendation=row.snapshotId},onSource={open(row)}){openNow(row)}}
                        }
                    }
                }
                else->{
                    item{ModernLine(true);Row(Modifier.horizontalScroll(rememberScrollState())){
                        (linkedMapOf("" to "전체")+hubCategories).forEach{(id,label)->
                            val selected=category==id.takeIf{it.isNotEmpty()}
                            Box(Modifier.background(if(selected)ModernInk else Color.Transparent).selectable(selected,role=Role.Tab,onClick={category=id.takeIf{it.isNotEmpty()}}).testTag("category_${id.ifEmpty{"ALL"}}").padding(horizontal=14.dp,vertical=13.dp)){Text(label,style=MaterialTheme.typography.labelMedium,color=if(selected)ModernBg else ModernInk)}
                        }
                    };ModernLine(true)}
                    var shown=false
                    hubCategories.forEach{(id,label)->if(category==null || category==id){
                        val group=data.categories[id].orEmpty()
                        if(group.isNotEmpty()){
                            shown=true
                            item(key="category-header:$id"){Row(Modifier.padding(top=16.dp,bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Text(label,fontWeight=FontWeight.ExtraBold)}}
                            items(group,key={"$id:${it.snapshotId}"}){row->HubNotificationRow(row,null,{recommendation=row.snapshotId},fullText=true,onSource={open(row)}){detailId=row.snapshotId}}
                        }
                    }}
                    if(!shown)item{EmptyHub("이곳에 보관된 알림은 아직 없어요.")}
                    item{Text("필요할 때 찾아보세요. 분류되지 않은 알림은 기타에 있어요.",style=MaterialTheme.typography.bodySmall,color=ModernMuted,modifier=Modifier.padding(vertical=16.dp))}
                }
            }
        }
    }
    val recommendationRow by produceState<CapturedNotification?>(null,recommendation) {
        value=if(!fixtureMode && recommendation!=null)app.repository.loadBody(recommendation!!) else rows.find{it.snapshotId==recommendation}
    }
    recommendationRow?.let{row->ContextRecommendationSheet(row,rows,app.policies,{app.recommendations.classify(it)}){recommendation=null}}
}
@Composable private fun EmptyHub(text:String){Text(text,Modifier.padding(vertical=24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable internal fun HubNotificationRow(row:CapturedNotification,badge:String?,recommend:()->Unit,fullText:Boolean=false,showSource:Boolean=true,onSource:()->Unit={},click:()->Unit){
    val app=androidx.compose.ui.platform.LocalContext.current.applicationContext as? InboxApplication
    val body by produceState(row,row) {
        if(app!=null && row.availableFields.endsWith(DISPLAY_PREVIEW))value=app.repository.loadBody(row.snapshotId) ?: row
    }
    NotificationCard(body,badge,recommend,fullText,showSource,onSource,click)
}
@Composable private fun NotificationCard(row:CapturedNotification,badge:String?,recommend:()->Unit,fullText:Boolean=false,showSource:Boolean=true,onSource:()->Unit={},click:()->Unit){
    val message=row.latestMessage()
    if(messageService(row)==null){
        Column(Modifier.fillMaxWidth().padding(vertical=5.dp)){
        Surface(onClick=click,color=Color.White,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().padding(vertical=5.dp)){
            Column(Modifier.padding(start=14.dp,end=10.dp,top=10.dp,bottom=14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    SourceAppIcon(row.packageName,row.appLabel,size=18.dp)
                    Text(hubAppLabel(row),Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=ModernMuted,maxLines=1,overflow=TextOverflow.Ellipsis)
                    IconButton(onClick=recommend,modifier=Modifier.size(32.dp).testTag("notification_more_${row.snapshotId}")){Icon(Icons.Outlined.MoreVert,"이 알림 기준 설정",Modifier.size(18.dp))}
                }
                Text(notificationDisplayTitle(row),style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(row.preview(),style=MaterialTheme.typography.bodyMedium,maxLines=if(fullText)Int.MAX_VALUE else 3,overflow=TextOverflow.Ellipsis)
                NotificationImage(message?.stringOrNull("imageFile"),message?.isImageAttachment()==true,compact=true)
                if(badge!=null)Text(badge,style=MaterialTheme.typography.labelSmall,color=ModernMuted)
                if(row.needsOriginalReview())Text("긴 내용 · 원본 확인 필요",style=MaterialTheme.typography.labelSmall,color=ModernMuted)
            }
        }
        NotificationFooter(row,onSource)
        }
        return
    }
    Row(Modifier.fillMaxWidth().clickable(onClick=click).padding(vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
        ConversationAvatar(if(row.hasGroupConversation())row.conversationAvatarFile else message?.stringOrNull("senderAvatarFile"),notificationDisplayTitle(row),sender=!row.hasGroupConversation(),size=40.dp,appBadge={SourceAppIcon(row.packageName,row.appLabel)})
        Column(Modifier.weight(1f)){
            if(badge!=null)Text(badge,style=MaterialTheme.typography.labelSmall,color=ModernRedText)
            Row(verticalAlignment=Alignment.CenterVertically){Text(notificationDisplayTitle(row),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis);IconButton(onClick=recommend,modifier=Modifier.size(32.dp).testTag("notification_more_${row.snapshotId}")){Icon(Icons.Outlined.MoreVert,"이 알림 기준 설정",Modifier.size(18.dp))}}
            Text(buildAnnotatedString {message?.stringOrNull("sender")?.takeIf{it.isNotBlank()}?.let{withStyle(SpanStyle(fontWeight=FontWeight.Bold)){append(it)};append(" · ")};append(row.preview())},style=MaterialTheme.typography.bodyMedium,maxLines=if(fullText)Int.MAX_VALUE else 2,overflow=TextOverflow.Ellipsis)
            NotificationImage(message?.stringOrNull("imageFile"),message?.isImageAttachment()==true,compact=true)
            NotificationFooter(row,onSource)
            if(row.needsOriginalReview())Text("긴 내용 · 원본 확인 필요",style=MaterialTheme.typography.bodySmall,color=ModernMuted)
            ModernLine()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MessengerRooms(rooms:List<HubRoom>,unread:Map<String,Int>,selectedService:String?,select:(String?)->Unit,openRoom:(String)->Unit,modifier:Modifier=Modifier){
    val services=rooms.map{it.service}.distinct().sortedWith(compareBy<String>{it!="문자"}.thenBy{it})
    val tabs=listOf<String?>(null)+services
    // Keep the selected service stable when newly observed apps add another tab.
    key(services){
        val pager=rememberPagerState(initialPage=tabs.indexOf(selectedService).coerceAtLeast(0),pageCount={tabs.size})
        val scope=rememberCoroutineScope()
        LaunchedEffect(pager.settledPage){select(tabs[pager.settledPage])}
        Column(modifier){
            ModernLine(true)
            ScrollableTabRow(selectedTabIndex=pager.currentPage,edgePadding=16.dp,
                containerColor=ModernBg,contentColor=ModernInk){
                tabs.forEachIndexed{i,label->Tab(selected=pager.currentPage==i,
                    onClick={scope.launch{pager.animateScrollToPage(i)}},
                    modifier=Modifier.testTag("messenger_tab_$i"),
                    text={Text(label ?: "전체",maxLines=1,softWrap=false,
                        fontWeight=if(pager.currentPage==i)FontWeight.ExtraBold else FontWeight.Medium)})}
            }
            ModernLine(true)
            HorizontalPager(pager,Modifier.weight(1f).testTag("messenger_pager"),key={tabs[it] ?: "all"}){page->
                val visible=rooms.filter{tabs[page]==null || it.service==tabs[page]}
                LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),contentPadding=PaddingValues(bottom=24.dp)){
                    if(visible.isEmpty())item{EmptyHub("대화가 도착하면 여기에 모아둘게요.")}
                    items(visible,key={it.id}){r->
                        Row(Modifier.fillMaxWidth().clickable{openRoom(r.id)}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                            ConversationAvatar(if(r.latest.source.hasGroupConversation())r.avatarFile else r.latest.senderAvatarFile,r.name,sender=!r.latest.source.hasGroupConversation(),size=48.dp,appBadge=if(tabs[page]==null)({SourceAppIcon(r.latest.source.packageName,r.service)}) else null)
                            Column(Modifier.weight(1f)){
                                Row(verticalAlignment=Alignment.CenterVertically){Text(r.name,Modifier.weight(1f),fontWeight=FontWeight.ExtraBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(java.util.Date(r.latest.time)),style=MaterialTheme.typography.bodySmall,color=ModernMuted)}
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                    Text(listOfNotNull(r.latest.sender?.takeIf{it.isNotBlank()},r.latest.text).joinToString(" · "),Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                                    val count=unread[r.id] ?: 0
                                    if(count>0)Box(Modifier.size(22.dp).background(ModernAccent,androidx.compose.foundation.shape.CircleShape).testTag("room_unread"),contentAlignment=Alignment.Center){
                                        Text(if(count>99)"99+" else count.toString(),color=ModernBg,style=MaterialTheme.typography.labelSmall.copy(fontSize=10.sp),maxLines=1)
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** Keep the original-app action reachable while the conversation scrolls. */
@Composable private fun SourceOpenButton(row:CapturedNotification,onClick:()->Unit){
    OutlinedButton(
        onClick=onClick,
        modifier=Modifier.heightIn(min=36.dp).testTag("open_source_app"),
        shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,ModernInk.copy(alpha=.25f)),
        colors=ButtonDefaults.outlinedButtonColors(contentColor=ModernInk)
    ){
        SourceAppIcon(row.packageName,row.appLabel,size=18.dp)
        Spacer(Modifier.width(6.dp))
        Text("에서 보기",style=MaterialTheme.typography.labelMedium)
    }
}

@Composable private fun NotificationFooter(row:CapturedNotification,onSource:()->Unit){
    Row(Modifier.fillMaxWidth().padding(top=4.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically){
        TextButton(onClick=onSource,contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp),modifier=Modifier.testTag("open_source_app")){
            SourceAppIcon(row.packageName,row.appLabel,size=16.dp)
            Text("에서 보기",Modifier.padding(start=5.dp),style=MaterialTheme.typography.labelSmall,color=ModernMuted)
        }
        Spacer(Modifier.weight(1f))
        Text(formatTime(row.postedTime),style=MaterialTheme.typography.labelSmall,color=ModernMuted,modifier=Modifier.padding(end=4.dp).testTag("notification_time"))
    }
}
