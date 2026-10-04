package com.ainotification.inbox

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun NotificationImage(name:String?,present:Boolean,compact:Boolean=false) {
    if(!present)return
    val context=LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null,name){
        value=withContext(Dispatchers.IO){runCatching{NotificationImages(context).file(name)?.let{BitmapFactory.decodeFile(it.absolutePath)}}.getOrNull()}
    }
    var enlarged by remember(name){mutableStateOf(false)}
    if(bitmap!=null) {
        Image(bitmap!!.asImageBitmap(),contentDescription="메시지 첨부 이미지",contentScale=ContentScale.Fit,
            modifier=(if(compact)Modifier.width(132.dp).heightIn(max=84.dp) else Modifier.fillMaxWidth().aspectRatio(bitmap!!.width.toFloat()/bitmap!!.height)).padding(vertical=6.dp).testTag("notification_image").clickable{enlarged=true})
    } else Text("사진 · 원래 앱에서 확인",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=6.dp))
    if(enlarged && bitmap!=null)Dialog(onDismissRequest={enlarged=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(color=ModernInk,contentColor=ModernBg,modifier=Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("첨부 사진",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick={enlarged=false}){Text("닫기",color=ModernBg)}}
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Image(bitmap!!.asImageBitmap(),"첨부 이미지 크게 보기",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("알림에 포함된 사진",style=MaterialTheme.typography.bodySmall);Text("1 / 1",style=MaterialTheme.typography.bodySmall)}
            }
        }
    }
}
