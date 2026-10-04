package com.ainotification.inbox

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import org.json.JSONObject

class NowAlertSettingsActivity:Activity() {
    private val app get()=application as InboxApplication
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var status:TextView
    private var error=""
    private val tick=object:Runnable { override fun run(){ refresh(); handler.postDelayed(this,1000) } }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(32,70,32,30) }
        box.addView(TextView(this).apply{text="Now 알림만 · 테스트";textSize=24f})
        box.addView(TextView(this).apply{text="켜면 새로 Now에 표시되는 알림만 우리 앱이 소리·팝업으로 알려드려요. 다른 일반 알림은 시스템 알림창과 상태표시줄에서도 숨겨요. 저장된 내용은 앱에서 확인할 수 있어요. 전화·알람과 기존 방해금지 예외는 유지돼요. 끌 때까지 계속됩니다. 판단 중이거나 확인이 필요한 알림은 따로 확인해 주세요."})
        status=TextView(this);box.addView(status)
        fun button(label:String,action:()->Unit){box.addView(Button(this).apply{text=label;setOnClickListener{runCatching(action).onFailure{error=it.message ?: "설정을 확인해 주세요."};refresh()}})}
        button("Now 알림만 켜기"){app.nowAlerts.start();error=""}
        button("끄고 원래 알림으로 돌아가기"){app.nowAlerts.stop();error=""}
        button("Now로 돌아가기"){startActivity(Intent(this,MainActivity::class.java));finish()}
        setContentView(box)
    }
    private fun refresh(){
        val manager=getSystemService(android.app.NotificationManager::class.java)
        status.text="${if(app.nowAlerts.enabled)"켜짐" else "꺼짐"} · 알림 수집 ${if(app.listenerConnected.value)"연결됨" else "연결 확인 필요"}\n$error"
        val p=getSharedPreferences("now-alerts",MODE_PRIVATE)
        java.io.File(filesDir,"now-alerts-status.json").writeText(JSONObject().put("time",System.currentTimeMillis())
            .put("enabled",app.nowAlerts.enabled).put("filter",manager.currentInterruptionFilter)
            .put("connected",app.listenerConnected.value).put("since",p.getLong("since",0))
            .put("lastPosted",p.getLong("lastPosted",0)).put("postedCount",p.getInt("postedCount",0)).toString())
    }
    override fun onResume(){super.onResume();handler.post(tick)}
    override fun onPause(){handler.removeCallbacksAndMessages(null);super.onPause()}
}
