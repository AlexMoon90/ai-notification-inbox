package com.ainotification.inbox

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal data class ReplayProgress(val active:Boolean=false,val total:Int=0,val processed:Int=0,val failed:Int=0,val calls:Int=0)
/** Explicit user-requested, bounded one-time replay. Cursor survives process restarts. */
internal class StructuredReplay(context:Context){
 val prefs=context.getSharedPreferences("structured-replay",0)
 private fun read()=ReplayProgress(prefs.getBoolean("active",false),prefs.getInt("total",0),prefs.getInt("processed",0),prefs.getInt("failed",0),prefs.getInt("calls",0))
 private val mutable=MutableStateFlow(read());val progress:StateFlow<ReplayProgress> = mutable
 fun publish(){mutable.value=read()}
 fun charge(kind:String){val key="calls:$kind";val n=prefs.getInt(key,0);check(n<if(kind=="structure")500 else 12000){"Replay call budget reached"};prefs.edit().putInt(key,n+1).putInt("calls",prefs.getInt("calls",0)+1).commit();publish()}
}
