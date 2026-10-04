package com.ainotification.inbox

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** Open while the notification URI grant is still active; never read pixels on the callback thread. */
internal class PendingImageReads : java.io.Closeable {
    companion object { private val permits=java.util.concurrent.Semaphore(16) }
    val streams=mutableMapOf<String,java.io.InputStream>()
    val status=mutableMapOf<String,String>()
    fun prepare(row:CapturedNotification,open:(String)->java.io.InputStream?) {
        if(row.isGroupSummary)return
        jsonObjects(JSONArray(row.messagesJson)).filter{it.isImageAttachment()}.mapNotNull{it.stringOrNull("dataUri")}.distinct().takeLast(4).forEach { uri->
            if(Uri.parse(uri).scheme!="content")return@forEach
            if(!permits.tryAcquire()){status[uri]="capture_busy";return@forEach}
            try {
                val input=open(uri)
                if(input==null){status[uri]="empty_stream";permits.release()}
                else {streams[uri]=input;status[uri]="opened_at_receipt"}
            } catch(e:Exception){status[uri]=e.javaClass.simpleName;permits.release()}
        }
    }
    override fun close(){streams.values.forEach{runCatching{it.close()};permits.release()};streams.clear()}
}

/** Private previews of actual notification attachments. Never fetch arbitrary web URLs; keep attachments and avatars separate. */
internal class NotificationImages(private val context:Context) {
    private val directory get()=File(context.filesDir,"notification-images").apply{mkdirs()}
    fun file(name:String?):File? = name?.takeIf{Regex("[a-f0-9]{64}\\.png").matches(it)}?.let{File(directory,it).takeIf{f->f.isFile}}
    fun clear(){directory.deleteRecursively()}
    internal fun store(bitmap:Bitmap):String {
        val scale=minOf(1.0,1024.0/maxOf(bitmap.width,bitmap.height))
        val preview=if(scale<1)Bitmap.createScaledBitmap(bitmap,maxOf(1,(bitmap.width*scale).toInt()),maxOf(1,(bitmap.height*scale).toInt()),true) else bitmap
        val bytes=ByteArrayOutputStream().use{out->check(preview.compress(Bitmap.CompressFormat.PNG,100,out));out.toByteArray()}
        if(preview!==bitmap)preview.recycle()
        val name=hash(bytes)+".png"
        val target=File(directory,name)
        if(!target.exists()) { val tmp=File.createTempFile("image-",".tmp",directory);try{tmp.writeBytes(bytes);check(tmp.renameTo(target))}finally{tmp.delete()} }
        return name
    }
    internal fun readUri(value:String,onFailure:(String)->Unit = {},opened:java.io.InputStream?=null):String? = runCatching {
        val uri=Uri.parse(value)
        if(uri.scheme!="content"){onFailure("unsupported_scheme");return null}
        val bytes=(opened ?: context.contentResolver.openInputStream(uri))?.use { input->
            val out=ByteArrayOutputStream();val buffer=ByteArray(8192);var total=0
            while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=10*1024*1024);out.write(buffer,0,n)}
            out.toByteArray()
        } ?: run{onFailure("empty_stream");return null}
        val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
        if(options.outWidth<=0 || options.outHeight<=0){onFailure("not_decodable_image");return null}
        var sample=1
        while(maxOf(options.outWidth,options.outHeight)/sample>1024)sample*=2
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample}) ?: return null
        try{store(bitmap)}finally{bitmap.recycle()}
    }.onFailure{onFailure(it.javaClass.simpleName)}.getOrNull()
    /** Preserve attachment evidence before decoding; never persist raw content URIs. */
    fun metadata(row:CapturedNotification,notification:Notification):CapturedNotification {
        if(row.isGroupSummary)return row
        val messages=JSONArray(row.messagesJson)
        for(i in 0 until messages.length()){
            val m=messages.getJSONObject(i)
            m.stringOrNull("dataUri")?.let{uri->m.remove("dataUri");m.put("attachmentId",hash(uri.toByteArray()))}
        }
        if(messages.length()==0 && (notification.extras.containsKey(Notification.EXTRA_PICTURE) || notification.extras.containsKey(Notification.EXTRA_PICTURE_ICON))){
            messages.put(JSONObject().put("text",row.currentMessageText()).put("timestamp",row.postedTime).put("mimeType","image/*").put("attachmentOnly",true))
        }
        return row.copy(messagesJson=messages.toString())
    }
    @Suppress("DEPRECATION")
    fun capture(row:CapturedNotification,notification:Notification,pending:PendingImageReads?=null):CapturedNotification {
        if(row.isGroupSummary)return row
        val messages=JSONArray(row.messagesJson)
        var changed=false
        for(i in 0 until messages.length()) {
            val m=messages.getJSONObject(i)
            val uri=m.stringOrNull("dataUri")
            if(uri!=null){
                m.remove("dataUri");m.put("attachmentId",hash(uri.toByteArray()));changed=true
                if(m.isImageAttachment()) {
                    m.put("imageUriScheme",Uri.parse(uri).scheme ?: "missing")
                    m.put("imageReadStatus","unavailable")
                    pending?.status?.get(uri)?.let{m.put("imageOpenStatus",it)}
                    readUri(uri,{status->m.put("imageReadStatus",status)},pending?.streams?.get(uri))?.let{m.put("imageFile",it);m.put("imageReadStatus","saved")}
                }
            }
        }
        // EXTRA_LARGE_ICON and Person icons are profile pictures, never message attachments.
        val latest=messages.optJSONObject(messages.length()-1)
        if(messages.length()==0 || latest?.isImageAttachment()==true && latest.stringOrNull("imageFile")==null) {
            val picture=notification.extras.get(Notification.EXTRA_PICTURE) as? Bitmap
            val pictureIcon=if(Build.VERSION.SDK_INT>=31)notification.extras.get(Notification.EXTRA_PICTURE_ICON) as? Icon else null
            val saved=runCatching {
                picture?.let(::store) ?: pictureIcon?.takeIf{Build.VERSION.SDK_INT>=31}?.let { icon->
                    // URI icons follow the same bounded reader. Bitmap icons come from the notification parcel.
                    if(Build.VERSION.SDK_INT<31)null else when(icon.type){Icon.TYPE_URI,Icon.TYPE_URI_ADAPTIVE_BITMAP->readUri(icon.uri.toString())
                        Icon.TYPE_BITMAP,Icon.TYPE_ADAPTIVE_BITMAP->{val drawable=icon.loadDrawable(context) as? android.graphics.drawable.BitmapDrawable;drawable?.bitmap?.let(::store)}
                        else->null}
                }
            }.getOrNull()
            if(picture!=null || pictureIcon!=null){
                // A picture may complement the latest image message when its URI cannot be read.
                // Never attach it to a later text reply or an older message in the history.
                val m=latest ?: JSONObject().put("text",row.currentMessageText()).put("timestamp",row.postedTime).put("mimeType","image/*").put("attachmentOnly",true)
                saved?.let{m.put("imageFile",it)}
                if(latest==null)messages.put(m)
                changed=true
            }
        }
        var avatar:String?=null
        if(messageService(row)!=null) {
            val style=androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            style?.messages?.forEachIndexed { i,message->
                if(i<messages.length())message.person?.icon?.let{icon->
                    val saved=saveAvatar(icon)
                    if(saved!=null){messages.getJSONObject(i).put("senderAvatarFile",saved);changed=true}
                }
            }
            val supplied=notification.getLargeIcon()?.let { saveAvatar(androidx.core.graphics.drawable.IconCompat.createFromIcon(context,it)) }
            val senderImages=(0 until messages.length()).mapNotNull{messages.getJSONObject(it).stringOrNull("senderAvatarFile")}
            val group=row.hasGroupConversation()
            // Some apps use the most recent speaker as the notification icon. Do not call that the group photo.
            avatar=supplied?.takeUnless{group && it in senderImages}
            if(avatar==null && row.isGroupConversation==false)avatar=senderImages.lastOrNull()
        }
        if(!changed && avatar==null)return row
        return row.copy(messagesJson=messages.toString(),conversationAvatarFile=avatar)
    }
    private fun saveAvatar(icon:androidx.core.graphics.drawable.IconCompat?):String? = runCatching {
        if(icon==null)return null
        if(icon.type==androidx.core.graphics.drawable.IconCompat.TYPE_URI || icon.type==androidx.core.graphics.drawable.IconCompat.TYPE_URI_ADAPTIVE_BITMAP) {
            val image=readUri(icon.uri.toString()) ?: return null
            val bitmap=BitmapFactory.decodeFile(file(image)?.absolutePath) ?: return null
            try{return storeThumbnail(bitmap)}finally{bitmap.recycle()}
        }
        val drawable=icon.loadDrawable(context) ?: return null
        val bitmap=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
        try {drawable.setBounds(0,0,128,128);drawable.draw(android.graphics.Canvas(bitmap));store(bitmap)}finally{bitmap.recycle()}
    }.getOrNull()
    private fun storeThumbnail(bitmap:Bitmap):String {
        val small=Bitmap.createScaledBitmap(bitmap,128,128,true)
        return try{store(small)}finally{if(small!==bitmap)small.recycle()}
    }
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
}
internal fun JSONObject.isImageAttachment()=stringOrNull("mimeType")?.startsWith("image/")==true
