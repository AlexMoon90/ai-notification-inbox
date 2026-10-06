package com.ainotification.inbox

import android.app.NotificationManager
import android.content.Context

/** Acknowledging our relay never cancels a source app's notification or deletes its record. */
internal class NowAlertReadState(context:Context,private val clock:()->Long={System.currentTimeMillis()}) {
 private val manager=context.getSystemService(NotificationManager::class.java)
 private val prefs=context.getSharedPreferences("now-alert-read",Context.MODE_PRIVATE)
 private val window=48*3_600_000L
 fun isRead(id:String)=prefs.getLong(id,Long.MIN_VALUE)>=clock()-window
 @Synchronized fun acknowledge(ids:Collection<String>) {
  if(ids.isEmpty())return
  val now=clock();val edit=prefs.edit()
  prefs.all.forEach{(id,at)->if((at as? Long ?: Long.MIN_VALUE)<now-window)edit.remove(id)}
  ids.distinct().forEach{id->edit.putLong(id,now)}
  // Persist before cancel so a late analysis result cannot post an already read item.
  edit.apply()
  ids.distinct().forEach{id->manager.cancel("now:$id",1)}
 }
 fun activeIds():List<String> = manager.activeNotifications.mapNotNull { n ->
  n.tag?.takeIf{it.startsWith("now:") && n.id==1}?.removePrefix("now:")
 }
 @Synchronized fun reconcile() {
  val cutoff=clock()-window
  manager.activeNotifications.forEach{n->
   val id=n.tag?.takeIf{it.startsWith("now:") && n.id==1}?.removePrefix("now:") ?: return@forEach
   if(isRead(id) || n.postTime<cutoff)manager.cancel(n.tag,n.id)
  }
 }
}
