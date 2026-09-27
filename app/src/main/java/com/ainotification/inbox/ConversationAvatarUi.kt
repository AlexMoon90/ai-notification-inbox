package com.ainotification.inbox

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

@Composable internal fun ConversationAvatar(file:String?,name:String,sender:Boolean=false){
    val context=LocalContext.current
    val bitmap by produceState<Bitmap?>(null,file){value=withContext(Dispatchers.IO){runCatching{NotificationImages(context).file(file)?.let{BitmapFactory.decodeFile(it.absolutePath)}}.getOrNull()}}
    val size=if(sender)28.dp else 42.dp
    if(bitmap!=null)Image(bitmap!!.asImageBitmap(),if(sender)"발신자 프로필" else "대화방 썸네일",Modifier.size(size).clip(CircleShape),contentScale=ContentScale.Crop)
    else Surface(shape=CircleShape,color=MaterialTheme.colorScheme.secondaryContainer,modifier=Modifier.size(size)){
        Box(contentAlignment=Alignment.Center){Text(name.trim().take(1).ifEmpty{"?"},style=if(sender)MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium)}
    }
}
