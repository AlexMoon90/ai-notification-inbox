package com.ainotification.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
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
    fixtureRows:List<CapturedNotification>?=null,fixtureDecisions:JSONObject?=null,fixtureFacts:List<HubClassification>?=null,initialTab:Int=0) {
    val stored by app.repository.all.collectAsStateWithLifecycle(initialValue=emptyList())
    val state by app.selection.state.collectAsStateWithLifecycle()
    val storedFacts by app.database.hub().observe().collectAsStateWithLifecycle(initialValue=emptyList())
    val rows=fixtureRows ?: if(preview)previewNotifications() else stored
    val facts=(fixtureFacts ?: storedFacts).associateBy{it.notificationId}
    val decisions=fixtureDecisions ?: state.optJSONObject("results") ?: JSONObject()
    var tab by rememberSaveable{mutableIntStateOf(initialTab)}
    var settingsPage by rememberSaveable{mutableStateOf<Int?>(null)}
    var roomId by rememberSaveable{mutableStateOf<String?>(null)}
    var detailId by rememberSaveable{mutableStateOf<String?>(null)}
    var detailMessageKey by rememberSaveable{mutableStateOf<String?>(null)}
    var recommendation by rememberSaveable{mutableStateOf<String?>(null)}
    var category by rememberSaveable{mutableStateOf<String?>(null)}
    var service by rememberSaveable{mutableStateOf<String?>(null)}
    var review by rememberSaveable{mutableStateOf(false)}
    val rooms=remember(rows){conversationRooms(rows)}
    val room=rooms.find{it.id==roomId}
    val originalDetail=rows.find{it.snapshotId==detailId}
    val selectedMessage=room?.messages?.find{it.identity==detailMessageKey}
    val detail=originalDetail?.let{r->if(selectedMessage!=null)r.copy(text=selectedMessage.text,bigText=null,messagesJson="[]",postedTime=selectedMessage.time) else r}
    if(settingsPage!=null){LegacySettingsScreen(app,granted,settings,open,preview,settingsPage!!){settingsPage=null};return}
    fun back(){when{detailId!=null->{detailId=null;detailMessageKey=null};roomId!=null->roomId=null;category!=null->category=null;else->review=false}}
    val inDetail=detailId!=null || roomId!=null || category!=null || review
    BackHandler(inDetail){back()}
    Scaffold(modifier=Modifier.semantics{testTagsAsResourceId=true},topBar={TopAppBar(
        title={Text(when{detailId!=null->"알림 상세";roomId!=null->room?.name ?: "대화";category!=null->hubCategories[category].orEmpty();review->"확인할 알림";else->listOf("Now","메신저","분류함")[tab]},fontWeight=FontWeight.Bold)},
        navigationIcon={if(inDetail)IconButton(onClick={back()}){Icon(Icons.Outlined.ArrowBack,"뒤로")}},
        actions={if(!inDetail && tab==0)TextButton(onClick={settingsPage=1},modifier=Modifier.testTag("open_policy")){Text("알림 기준")};if(!inDetail)IconButton(onClick={settingsPage=2},modifier=Modifier.testTag("open_settings")){Icon(Icons.Outlined.Settings,"설정")}}
    )},bottomBar={if(!inDetail)NavigationBar {
        listOf("Now","메신저","분류함").forEachIndexed{i,label->NavigationBarItem(selected=tab==i,onClick={tab=i;service=null},modifier=Modifier.testTag("nav_$i"),icon={Icon(when(i){0->Icons.Outlined.Notifications;1->Icons.Outlined.Email;else->Icons.Outlined.List},label)},label={Text(label)})}
    }}){padding->
        if(tab==1 && !inDetail) {
            MessengerRooms(rooms,service,{service=it},{roomId=it},Modifier.fillMaxSize().padding(padding))
        } else LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp).testTag("hub_list"),contentPadding=PaddingValues(bottom=24.dp)){
            when {
                detail!=null->{
                    item{Text(notificationDisplayTitle(detail),style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(vertical=12.dp))}
                    item{val sender=selectedMessage?.sender ?: originalDetail?.latestMessage()?.stringOrNull("sender");if(!sender.isNullOrBlank())Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){ConversationAvatar(selectedMessage?.senderAvatarFile ?: originalDetail?.latestMessage()?.stringOrNull("senderAvatarFile"),sender,true);Text(sender)}}
                    item{Text(detail.preview(),style=MaterialTheme.typography.bodyLarge)}
                    item{NotificationImage(selectedMessage?.imageFile ?: detail.latestMessage()?.stringOrNull("imageFile"),selectedMessage?.hasImage ?: (detail.latestMessage()?.isImageAttachment()==true))}
                    item{SourceLine(detail)}
                    if(detail.needsOriginalReview())item{Text("긴 내용 · 원본 확인 필요",Modifier.padding(vertical=8.dp))}
                    item{Button(onClick={open(detail)},modifier=Modifier.fillMaxWidth()){Text("원래 앱에서 열기")}}
                    item{OutlinedButton(onClick={recommendation=detail.snapshotId},modifier=Modifier.fillMaxWidth().testTag("notification_policy_settings")){Text("이 알림 기준 설정")}}
                }
                roomId!=null->{
                    if(room==null)item{EmptyHub("대화 기록이 없습니다.")}
                    else {
                        item{Row(Modifier.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){ConversationAvatar(room.avatarFile,room.name);Text("알림으로 수집된 메시지 ${room.messages.size}개",style=MaterialTheme.typography.bodySmall)}}
                        items(room.messages.asReversed(),key={it.identity}){message->Column(Modifier.fillMaxWidth().clickable{detailMessageKey=message.identity;detailId=message.source.snapshotId}.padding(vertical=10.dp)){
                            message.sender?.takeIf{it.isNotBlank()}?.let{name->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){ConversationAvatar(message.senderAvatarFile,name,sender=true);Text(name,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.labelLarge)}}
                            Text(message.text,style=MaterialTheme.typography.bodyMedium)
                            NotificationImage(message.imageFile,message.hasImage)
                            Text(formatTime(message.time),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(Modifier.padding(top=10.dp))
                        }}
                        item{OutlinedButton(onClick={open(room.latest.source)},modifier=Modifier.fillMaxWidth()){Text("원래 앱에서 열기")}}
                        item{Text("수집하지 못한 메시지는 표시되지 않습니다.",style=MaterialTheme.typography.bodySmall)}
                    }
                }
                category!=null->{
                    val selected=rows.filter{category in (facts[it.snapshotId]?.categories() ?: setOf("OTHER"))}
                    if(selected.isEmpty())item{EmptyHub("아직 이 분류의 정보가 없습니다.")}
                    items(selected,key={it.snapshotId}){row->HubNotificationRow(row,hubBadge(facts[row.snapshotId]),{recommendation=row.snapshotId}){detailId=row.snapshotId}}
                }
                tab==0->{
                    if(!granted)item{TextButton(onClick=settings){Text("알림 접근 허용")}}
                    val pending=rows.filter{val d=decisions.optJSONObject(it.snapshotId); d==null || d.optString("status") in listOf("review","pending")}
                    if(!review && pending.isNotEmpty())item{TextButton(onClick={review=true},modifier=Modifier.testTag("open_review")){Text("확인할 알림 ${pending.size}개")}}
                    val visible=if(review)pending else rows.filter{isNowNotification(it,decisions)}
                    if(visible.isEmpty())item{EmptyHub(if(review)"확인할 알림이 없습니다." else "지금 알려드릴 알림이 없습니다.")}
                    items(visible,key={it.snapshotId}){row->HubNotificationRow(row,hubBadge(facts[row.snapshotId]),{recommendation=row.snapshotId}){detailId=row.snapshotId}}
                }
                else->{
                    hubCategories.forEach{(id,label)->
                        val count=rows.count{id in (facts[it.snapshotId]?.categories() ?: setOf("OTHER"))}
                        item{Row(Modifier.fillMaxWidth().testTag("category_$id").clickable{category=id}.padding(vertical=18.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(label,fontWeight=FontWeight.SemiBold);Text("${count}개",color=MaterialTheme.colorScheme.onSurfaceVariant)};HorizontalDivider()}
                    }
                    item{Text("아직 분류하지 못한 알림은 기타에 보관합니다.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=16.dp))}
                }
            }
        }
    }
    rows.find{it.snapshotId==recommendation}?.let{row->ContextRecommendationSheet(row,rows,app.policies,{app.recommendations.classify(it)}){recommendation=null}}
}
@Composable private fun EmptyHub(text:String){Text(text,Modifier.padding(vertical=24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable private fun SourceLine(row:CapturedNotification){Row(Modifier.padding(top=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
    SourceAppIcon(row.packageName,row.appLabel)
    Text("${if(row.serviceType=="SMS" || row.packageName in smsPackages)"문자" else row.appLabel} · ${formatTime(row.postedTime)}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}}
@Composable internal fun HubNotificationRow(row:CapturedNotification,badge:String?,recommend:()->Unit,click:()->Unit){
    Row(Modifier.fillMaxWidth().clickable(onClick=click).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
        val message=row.latestMessage()
        val messaging=messageService(row)!=null
        if(messaging){
            if(row.hasGroupConversation())ConversationAvatar(row.conversationAvatarFile,notificationDisplayTitle(row))
            else ConversationAvatar(message?.stringOrNull("senderAvatarFile"),message?.stringOrNull("sender") ?: notificationDisplayTitle(row),sender=true)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)){
            if(badge!=null)Text(badge,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
            Text(notificationDisplayTitle(row),fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(messaging)message?.stringOrNull("sender")?.let{sender->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
                    Text(sender,style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
            Text(row.preview(),style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            NotificationImage(row.latestMessage()?.stringOrNull("imageFile"),row.latestMessage()?.isImageAttachment()==true,compact=true)
            SourceLine(row)
            if(row.needsOriginalReview())Text("긴 내용 · 원본 확인 필요",style=MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick=recommend,modifier=Modifier.testTag("notification_more_${row.snapshotId}")){Icon(Icons.Outlined.MoreVert,"이 알림 기준 설정")}
    }
    HorizontalDivider()
}


@Composable private fun MessengerRooms(rooms:List<HubRoom>,selectedService:String?,select:(String?)->Unit,openRoom:(String)->Unit,modifier:Modifier=Modifier){
    val services=rooms.map{it.service}.distinct().sortedWith(compareBy<String>{it!="문자"}.thenBy{it})
    val tabs=listOf<String?>(null)+services
    // Keep the selected service stable when newly observed apps add another tab.
    key(services){
        val pager=rememberPagerState(initialPage=tabs.indexOf(selectedService).coerceAtLeast(0),pageCount={tabs.size})
        val scope=rememberCoroutineScope()
        val chipScroll=rememberScrollState()
        LaunchedEffect(pager.settledPage){select(tabs[pager.settledPage])}
        Column(modifier){
            Row(Modifier.fillMaxWidth().horizontalScroll(chipScroll).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                tabs.forEachIndexed{i,label->FilterChip(pager.currentPage==i,{scope.launch{pager.animateScrollToPage(i)}},modifier=Modifier.testTag("messenger_tab_$i"),label={Text(label ?: "전체")})}
            }
            HorizontalPager(pager,Modifier.weight(1f).testTag("messenger_pager"),key={tabs[it] ?: "all"}){page->
                val visible=rooms.filter{tabs[page]==null || it.service==tabs[page]}
                LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),contentPadding=PaddingValues(bottom=24.dp)){
                    if(visible.isEmpty())item{EmptyHub("수집된 대화가 없습니다.")}
                    items(visible,key={it.id}){r->
                        Row(Modifier.fillMaxWidth().clickable{openRoom(r.id)}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                            if(r.latest.source.hasGroupConversation())ConversationAvatar(r.avatarFile,r.name)
                            else ConversationAvatar(r.latest.senderAvatarFile,r.latest.sender ?: r.name,sender=true)
                            Column(Modifier.weight(1f)){
                                Text(r.name,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                r.latest.sender?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)}
                                Text(r.latest.text,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
                                    if(tabs[page]==null)SourceAppIcon(r.latest.source.packageName,r.latest.source.appLabel)
                                    Text("${formatTime(r.latest.time)} · 수집 ${r.messages.size}개",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
