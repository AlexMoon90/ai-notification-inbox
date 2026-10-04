package com.ainotification.inbox

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.border
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun ConversationAvatar(file:String?,name:String,sender:Boolean=false,size:Dp=if(sender)34.dp else 40.dp,appBadge:(@Composable ()->Unit)?=null){
    val context=LocalContext.current
    val bitmap by produceState<Bitmap?>(null,file){value=withContext(Dispatchers.IO){runCatching{NotificationImages(context).file(file)?.let{BitmapFactory.decodeFile(it.absolutePath)}}.getOrNull()}}
    Box(Modifier.size(size)){
    if(bitmap!=null)Image(bitmap!!.asImageBitmap(),if(sender)"발신자 프로필" else "대화방 썸네일",Modifier.size(size).clip(RectangleShape),contentScale=ContentScale.Crop)
    else Surface(shape=RectangleShape,color=MaterialTheme.colorScheme.secondaryContainer,modifier=Modifier.size(size)){
        Box(contentAlignment=Alignment.Center){Text(name.trim().take(1).ifEmpty{"?"},fontWeight=FontWeight.ExtraBold,style=if(sender)MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium)}
    }
    if(appBadge!=null)Box(Modifier.align(Alignment.BottomEnd).offset(5.dp,5.dp).size(22.dp).border(2.dp,ModernBg).padding(2.dp),contentAlignment=Alignment.Center){appBadge()}
    }
}
