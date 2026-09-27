package com.ainotification.inbox

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONObject

private val RelationBlue=Color(0xFF2868FF)
private val RelationInk=Color(0xFF142246)

/** A navigation projection only: selecting a node never changes a policy. */
internal fun relationApps(rule:JSONObject)=jsonStrings(rule.getJSONObject("scope").getJSONArray("apps")).ifEmpty{listOf("*")}
internal fun relationConditions(rule:JSONObject)=jsonObjects(rule.getJSONArray("conditions")).map{it.getString("type")}.distinct()

/** App overview includes global and app-specific rules without changing their conditions. */
internal fun relationRulesForApp(rules:List<JSONObject>,pkg:String)=rules.filter { r ->
    val apps=jsonStrings(r.getJSONObject("scope").getJSONArray("apps"))
    if(pkg=="*")apps.isEmpty() else apps.isEmpty() || pkg in apps
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun PolicyRelationMap(rules:List<JSONObject>,rows:List<CapturedNotification>,onAppSettings:(String)->Unit={},onRule:(String)->Unit) {
    var spotlight by remember { mutableStateOf<String?>(null) }
    var focus by rememberSaveable{mutableStateOf(0.58f)}
    val animatedFocus by animateFloatAsState(focus,animationSpec=spring(dampingRatio=1f,stiffness=900f),label="relation split")
    var mapWidth by remember { mutableStateOf(1) }
    val drag=rememberDraggableState { delta -> focus=(focus-delta/mapWidth*1.23f).coerceIn(.18f,.82f) }
    val groups=policyDisplayGroups(rules)
    val common=groups.filter{it.common}
    val individual=groups.filterNot{it.common}
    val apps=groups.flatMap{it.apps}.distinct()
    val rects=remember{mutableStateMapOf<String,Rect>()}
    var origin by remember{mutableStateOf(Offset.Zero)}
    fun measured(key:String)=Modifier.onGloballyPositioned{rects[key]=it.boundsInRoot()}
    Column(verticalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.testTag("policy_relation_map").onSizeChanged{mapWidth=it.width.coerceAtLeast(1)}.draggable(drag,Orientation.Horizontal)) {
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("공통 상황" to (1f-animatedFocus),"앱" to 0.23f,"개별 지시사항" to animatedFocus).forEach{(label,weight)->
                Text(label,(if(label=="앱")Modifier.width(40.dp) else Modifier.weight(weight)).padding(vertical=6.dp),color=RelationInk,fontSize=12.sp,lineHeight=17.sp,fontWeight=FontWeight.SemiBold,maxLines=1,softWrap=false,overflow=TextOverflow.Clip,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        if(rules.isEmpty())Text("새 기준을 만들면 상황과 앱, 지시사항의 연결을 볼 수 있어요.",color=RelationInk,modifier=Modifier.padding(vertical=28.dp))
        else Box(Modifier.heightIn(max=470.dp).clipToBounds().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().onGloballyPositioned{origin=it.boundsInRoot().topLeft}) {
                Canvas(Modifier.matchParentSize()) {
                    fun edge(from:String,to:String,active:Boolean) {
                        val a=rects[from]?:return;val b=rects[to]?:return
                        val start=Offset(a.right-origin.x+(if(from.startsWith("a:") || from=="app")1.dp.toPx() else 0f),a.center.y-origin.y);val end=Offset(b.left-origin.x-(if(to.startsWith("a:") || to=="app")1.dp.toPx() else 0f),b.center.y-origin.y)
                        val path=Path().apply{moveTo(start.x,start.y);cubicTo((start.x+end.x)/2,start.y,(start.x+end.x)/2,end.y,end.x,end.y)}
                        val color=if(active)RelationBlue else Color(0xFF587CB5)
                        drawPath(path,color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
                        drawCircle(color,2.dp.toPx(),start);drawCircle(color,2.dp.toPx(),end)
                    }
                    groups.forEach { group -> group.apps.forEach { app ->
                        if(group.common)edge("s:${group.id}","a:$app",false) else edge("a:$app","r:${group.id}:$app",false)
                    } }
                }
                Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f-animatedFocus),verticalArrangement=Arrangement.spacedBy(0.dp)) {
                        common.forEach{g->RelationNode(g.title,true,false,measured("s:${g.id}").testTag("situation_${g.id}"),alignRight=true){spotlight="s:${g.id}"}}
                        if(common.isEmpty())Text("공통 기준 없음",fontSize=11.sp,color=Color(0xFF667592))
                    }
                    Column(Modifier.width(40.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(0.dp)) {
                        apps.forEach{pkg->
                            val context=LocalContext.current
                            val icon=remember(pkg){if(pkg=="*")null else runCatching{context.packageManager.getApplicationIcon(pkg).toBitmap(96,96).asImageBitmap()}.getOrNull()}
                            val label=if(pkg=="*")"모든 앱" else rows.firstOrNull{it.packageName==pkg}?.appLabel ?: runCatching{context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg,0)).toString()}.getOrDefault(pkg)
                            Box(modifier=Modifier.fillMaxWidth().height(maxOf(40,individual.count{pkg in it.apps}*30).dp).testTag("relation_app_$pkg").clickable{spotlight="a:$pkg"},contentAlignment=Alignment.Center){
                                Column(Modifier.padding(1.dp),horizontalAlignment=Alignment.CenterHorizontally){if(icon!=null)Image(icon,label,Modifier.size(24.dp).then(measured("a:$pkg"))) else Icon(Icons.Outlined.Notifications,label,Modifier.size(24.dp).then(measured("a:$pkg")),tint=RelationBlue);Text(label,fontSize=8.sp,lineHeight=10.sp,maxLines=1,softWrap=false,overflow=TextOverflow.Clip,color=RelationInk)}
                            }
                        }
                    }
                    Column(Modifier.weight(animatedFocus),verticalArrangement=Arrangement.spacedBy(0.dp)) {
                        apps.forEach { pkg ->
                            val adjacent=individual.filter{pkg in it.apps}
                            Column(Modifier.height(maxOf(40,adjacent.size*30).dp),verticalArrangement=Arrangement.Center) {
                                adjacent.forEach { g ->
                                    val tag=if(pkg==g.apps.first())"relation_rule_${g.id}" else "relation_rule_${g.id}_$pkg"
                                    RelationNode(g.title,true,false,measured("r:${g.id}:$pkg").testTag(tag)){spotlight="r:${g.id}"}
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically){Text("상황",fontSize=11.sp,color=RelationInk);Slider(1f-focus,{focus=1f-it},valueRange=.18f.. .82f,modifier=Modifier.weight(1f).testTag("relation_width"),
            colors=SliderDefaults.colors(thumbColor=RelationBlue,activeTrackColor=Color(0xFFDCE5F7),inactiveTrackColor=Color(0xFFDCE5F7)),
            track={SliderDefaults.Track(it,modifier=Modifier.height(2.dp))},
            thumb={Surface(shape=RoundedCornerShape(10.dp),color=Color.White,border=BorderStroke(1.dp,Color(0xFFE1E7F2))){Icon(Icons.Outlined.Menu,"좌우 너비 조절",Modifier.padding(horizontal=6.dp,vertical=3.dp).size(18.dp),tint=RelationBlue)}});Text("지시사항",fontSize=11.sp,color=RelationInk)

        }
        Text("좌우로 밀어 너비 조절 · 터치해서 연결 보기",fontSize=11.sp,color=Color(0xFF667592))
    }
    spotlight?.let { key ->
        if(key.startsWith("a:")) {
            AppConnectionsSpotlight(key.removePrefix("a:"),groups,rows,{spotlight=null},{spotlight=null;onAppSettings(it)}) { id->spotlight=null;onRule(id) }
        } else {
            val group=groups.firstOrNull{it.id==key.substringAfter(":")}
            if(group!=null)RelationSpotlight(key,group.rules,rows,{spotlight=null},{spotlight="a:$it"},group.title) { id->spotlight=null;onRule(id) }
        }
    }

}
@Composable private fun RelationNode(text:String,active:Boolean,selected:Boolean,modifier:Modifier=Modifier,alignRight:Boolean=false,onClick:()->Unit) {
    Box(modifier.fillMaxWidth().heightIn(min=30.dp).clipToBounds().alpha(if(active)1f else .40f).clickable(onClick=onClick),contentAlignment=Alignment.CenterStart) {
        Text(text,Modifier.fillMaxWidth().padding(start=if(alignRight)0.dp else 6.dp,end=if(alignRight)6.dp else 0.dp,top=5.dp,bottom=5.dp),textAlign=if(alignRight)TextAlign.Right else TextAlign.Left,color=if(selected)RelationBlue else RelationInk,fontSize=12.sp,lineHeight=16.sp,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Medium,maxLines=1,softWrap=false,overflow=TextOverflow.Clip)
    }
}

/** Modal focus changes presentation only. The background keeps its position and width. */
@Composable private fun RelationSpotlight(key:String,rules:List<JSONObject>,rows:List<CapturedNotification>,dismiss:()->Unit,onApp:(String)->Unit,displayTitle:String,configure:(String)->Unit) {
    var settings by remember(key){mutableStateOf(false)}
    var frame by remember { mutableStateOf(Rect.Zero) }
    var labelBounds by remember { mutableStateOf(Rect.Zero) }
    val iconBounds=remember { mutableStateMapOf<String,Rect>() }
    val isSituation=key.startsWith("s:")
    val title=displayTitle
    val explicitApps=rules.flatMap(::relationApps).distinct()
    val packages=if("*" in explicitApps && rules.any{it.getJSONObject("scope").getString("type")=="ALL_APPS"})
        (explicitApps.filter{it!="*"}+rows.map{it.packageName}).distinct().ifEmpty{listOf("*")} else explicitApps
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(.82f) }
        Column(Modifier.fillMaxWidth().padding(24.dp).heightIn(max=550.dp).verticalScroll(rememberScrollState()).semantics{testTagsAsResourceId=true}.testTag("relation_spotlight"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            TextButton(onClick=dismiss,modifier=Modifier.align(Alignment.End).testTag("close_relation_spotlight")){Text("닫기",color=Color.White)}
            Text("텍스트를 다시 누르면 설정할 수 있어요",color=Color.White.copy(alpha=.75f),fontSize=12.sp)
            Box(Modifier.fillMaxWidth().clipToBounds().onGloballyPositioned{frame=it.boundsInRoot()}) {
                Canvas(Modifier.matchParentSize()) {
                    val textX=if(isSituation)labelBounds.right else labelBounds.left
                    val start=Offset(textX-frame.left,labelBounds.center.y-frame.top)
                    iconBounds.values.forEach { bounds ->
                        if(bounds.center.y>=frame.top && bounds.center.y<=frame.bottom) {
                            val end=Offset((if(isSituation)bounds.left-1.dp.toPx() else bounds.right+1.dp.toPx())-frame.left,bounds.center.y-frame.top)
                            val path=Path().apply{moveTo(start.x,start.y);cubicTo((start.x+end.x)/2,start.y,(start.x+end.x)/2,end.y,end.x,end.y)}
                            drawPath(path,Color(0xFFB4D0FF),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
                            drawCircle(Color(0xFFB4D0FF),2.dp.toPx(),end)
                        }
                    }
                }
                Row(Modifier.height(IntrinsicSize.Min),verticalAlignment=Alignment.CenterVertically) {
                    @Composable fun focusedText(modifier:Modifier) {
                        Text(title,modifier.onGloballyPositioned{labelBounds=it.boundsInRoot()}.clipToBounds().clickable{settings=!settings}.padding(start=if(isSituation)0.dp else 6.dp,end=if(isSituation)6.dp else 0.dp,top=12.dp,bottom=12.dp).testTag("spotlight_text"),textAlign=if(isSituation)TextAlign.Right else TextAlign.Left,fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=Color.White,maxLines=1,softWrap=false,overflow=TextOverflow.Clip)
                    }
                    @Composable fun appIcons(modifier:Modifier) {
                        Column(modifier.heightIn(max=240.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            packages.forEach { pkg ->
                                val context=LocalContext.current
                                val icon=remember(pkg){if(pkg=="*")null else runCatching{context.packageManager.getApplicationIcon(pkg).toBitmap(96,96).asImageBitmap()}.getOrNull()}
                                val label=if(pkg=="*")"모든 앱" else rows.firstOrNull{it.packageName==pkg}?.appLabel ?: runCatching{context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg,0)).toString()}.getOrDefault(pkg)
                                Column(Modifier.testTag("spotlight_app_$pkg").clickable{onApp(pkg)},horizontalAlignment=Alignment.CenterHorizontally) {
                                    if(icon!=null)Image(icon,label,Modifier.size(24.dp).onGloballyPositioned{iconBounds[pkg]=it.boundsInRoot()}) else Icon(Icons.Outlined.Notifications,label,Modifier.size(24.dp).onGloballyPositioned{iconBounds[pkg]=it.boundsInRoot()},tint=Color(0xFF8AB4FF))
                                    Text(label,color=Color.White,fontSize=9.sp,maxLines=1,softWrap=false,overflow=TextOverflow.Clip)
                                }
                            }
                        }
                    }
                    if(isSituation){focusedText(Modifier.weight(1f));Spacer(Modifier.width(8.dp));appIcons(Modifier.width(40.dp))}
                    else {appIcons(Modifier.width(40.dp));Spacer(Modifier.width(8.dp));focusedText(Modifier.weight(1f))}
                }
            }
            if("*" in explicitApps && packages!=listOf("*"))Text("모든 앱에 적용 · 수신 기록이 있는 앱 표시",fontSize=11.sp,color=Color.White.copy(alpha=.7f))
            if(settings) {
                if(rules.size==1)Button(onClick={configure(rules.first().getString("id"))},modifier=Modifier.testTag("configure_focused_rule")){Text("설정하기")}
                else {
                    Text("설정할 지시사항을 선택하세요",color=Color.White,fontSize=12.sp)
                    rules.forEach { r -> TextButton(onClick={configure(r.getString("id"))},modifier=Modifier.testTag("configure_focused_${r.getString("id")}")){Text(scopeLabel(r,rows)+" · 설정하기",color=Color.White,maxLines=1,softWrap=false,overflow=TextOverflow.Clip)} }
                }
            }
        }
    }
}

@Composable private fun AppConnectionsSpotlight(pkg:String,allGroups:List<PolicyDisplayGroup>,rows:List<CapturedNotification>,dismiss:()->Unit,appSettings:(String)->Unit,configure:(String)->Unit) {
    val context=LocalContext.current
    val label=if(pkg=="*")"모든 앱" else rows.firstOrNull{it.packageName==pkg}?.appLabel ?: runCatching{context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg,0)).toString()}.getOrDefault(pkg)
    val icon=remember(pkg){if(pkg=="*")null else runCatching{context.packageManager.getApplicationIcon(pkg).toBitmap(96,96).asImageBitmap()}.getOrNull()}
    val groups=displayGroupsForApp(allGroups,pkg)
    val common=groups.filter{it.common};val individual=groups.filterNot{it.common}
    val rules=groups.flatMap{it.rules}
    var selectedIds by remember(pkg){mutableStateOf<List<String>>(emptyList())}
    val anchors=remember { mutableStateMapOf<String,Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    fun anchor(id:String)=Modifier.onGloballyPositioned{anchors[id]=it.boundsInRoot()}
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect{window?.setDimAmount(.82f)}
        Column(Modifier.fillMaxWidth().padding(20.dp).heightIn(max=570.dp).verticalScroll(rememberScrollState()).semantics{testTagsAsResourceId=true}.testTag("app_connections"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                Text("$label · 전체 연결",color=Color.White,modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold,maxLines=1,softWrap=false,overflow=TextOverflow.Clip)
                TextButton(onClick=dismiss,modifier=Modifier.testTag("close_app_connections")){Text("닫기",color=Color.White)}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("공통 상황" to .36f,"앱" to .20f,"개별 지시사항" to .44f).forEach{(text,weight)->Text(text,if(text=="앱")Modifier.width(40.dp) else Modifier.weight(weight),color=Color.White.copy(alpha=.7f),fontSize=11.sp,maxLines=1,softWrap=false,overflow=TextOverflow.Clip)}
            }
            Box(Modifier.heightIn(max=320.dp).clipToBounds().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().onGloballyPositioned{origin=it.boundsInRoot().topLeft}) {
                    Canvas(Modifier.matchParentSize()) {
                        fun line(from:String,to:String) {
                            val a=anchors[from]?:return;val b=anchors[to]?:return
                            val start=Offset(a.right-origin.x+(if(from.startsWith("a:") || from=="app")1.dp.toPx() else 0f),a.center.y-origin.y);val end=Offset(b.left-origin.x-(if(to.startsWith("a:") || to=="app")1.dp.toPx() else 0f),b.center.y-origin.y)
                            val path=Path().apply{moveTo(start.x,start.y);cubicTo((start.x+end.x)/2,start.y,(start.x+end.x)/2,end.y,end.x,end.y)}
                            drawPath(path,Color(0xFFB4D0FF),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
                            drawCircle(Color(0xFFB4D0FF),2.dp.toPx(),start);drawCircle(Color(0xFFB4D0FF),2.dp.toPx(),end)
                        }
                        common.forEach{line("s:${it.id}","app")};individual.forEach{line("app","r:${it.id}")}
                    }
                    Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        Column(Modifier.weight(.36f)) {
                            common.forEach { g -> Text(g.title,anchor("s:${g.id}").fillMaxWidth().clickable{selectedIds=g.rules.map{it.getString("id")}}.padding(end=6.dp,top=7.dp,bottom=7.dp).testTag("app_common_${g.id}"),textAlign=TextAlign.Right,color=Color.White,fontSize=12.sp,lineHeight=16.sp,maxLines=1,softWrap=false,overflow=TextOverflow.Clip) }
                            if(common.isEmpty())Text("공통 기준 없음",color=Color.White.copy(alpha=.6f),fontSize=11.sp)
                        }
                        Box(Modifier.width(40.dp).fillMaxHeight(),contentAlignment=Alignment.Center) {
                            Column(Modifier.fillMaxWidth().testTag("app_connection_icon"),horizontalAlignment=Alignment.CenterHorizontally) {
                                if(icon!=null)Image(icon,label,Modifier.size(24.dp).then(anchor("app"))) else Icon(Icons.Outlined.Notifications,label,Modifier.size(24.dp).then(anchor("app")),tint=Color(0xFF8AB4FF))
                                Text(label,color=Color.White,fontSize=9.sp,maxLines=1,softWrap=false,overflow=TextOverflow.Clip)
                            }
                        }
                        Column(Modifier.weight(.44f).fillMaxHeight(),verticalArrangement=Arrangement.Center) {
                            individual.forEach { g -> Text(g.title,anchor("r:${g.id}").fillMaxWidth().clickable{selectedIds=g.rules.map{it.getString("id")}}.padding(start=6.dp,top=7.dp,bottom=7.dp).testTag("app_rule_${g.id}"),color=if(g.rules.any{it.getString("id") in selectedIds})Color(0xFF8AB4FF) else Color.White,fontSize=12.sp,lineHeight=16.sp,maxLines=1,softWrap=false,overflow=TextOverflow.Clip) }
                            if(individual.isEmpty())Text("개별 기준 없음",color=Color.White.copy(alpha=.6f),fontSize=11.sp)
                        }
                    }
                }
            }
            Text("전체 앱 기준도 포함합니다. 대화·발신자·시간 등 세부 조건과 예외는 각 기준에 따라 적용됩니다.",color=Color.White.copy(alpha=.7f),fontSize=11.sp)
            Button(onClick={appSettings(pkg)},modifier=Modifier.testTag("app_policy_settings")){Text("이 앱의 규칙 작성") }
            if(selectedIds.isEmpty())Text("텍스트를 누르면 설정하기가 나와요",color=Color.White.copy(alpha=.7f),fontSize=12.sp)
            rules.filter{it.getString("id") in selectedIds}.forEach{r->Button(onClick={configure(r.getString("id"))},modifier=Modifier.testTag("app_configure_${r.getString("id")}")){Text(if(selectedIds.size==1)"설정하기" else scopeLabel(r,rows)+" · 설정하기",maxLines=1,softWrap=false,overflow=TextOverflow.Clip)}}
        }
    }
}
