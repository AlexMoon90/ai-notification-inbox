package com.ainotification.inbox

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
            modifier=Modifier.fillMaxWidth().heightIn(max=if(compact)120.dp else 240.dp).padding(vertical=6.dp).testTag("notification_image").clickable{enlarged=true})
    } else Text("사진 · 원래 앱에서 확인",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=6.dp))
    if(enlarged && bitmap!=null)Dialog(onDismissRequest={enlarged=false}) {
        Surface {Column(Modifier.fillMaxWidth().padding(12.dp)){
            Image(bitmap!!.asImageBitmap(),"첨부 이미지 크게 보기",Modifier.fillMaxWidth().heightIn(max=550.dp),contentScale=ContentScale.Fit)
            TextButton(onClick={enlarged=false}){Text("닫기")}
        }}
    }
}
