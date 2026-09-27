package com.ainotification.inbox

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=InboxApplication::class,qualifiers="w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PolicyRelationRenderTest {
    @Test fun renderNativeRelationshipLayout() {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity=controller.get()
        val labels=listOf("답변이 필요한 경우 알려주기","일정이 변경되면 알려주기","납부·회비 요청 알려주기","보안 관련 알림 표시","광고성 알림 조용히 보기")
        val types=listOf("REPLY_REQUIRED","SCHEDULE_CHANGE","PAYMENT_REQUIRED","SECURITY","PROMOTION")
        val rules=labels.mapIndexed { i,name -> JSONObject().put("id","r$i").put("name",name).put("enabled",true).put("scope",PolicyContract.emptyScope())
            .put("conditions",JSONArray().put(JSONObject().put("type",types[i]).put("value","").put("negated",false))).put("action",if(i==4)"QUIET" else "SHOW").put("logic","ANY").put("exceptions",JSONArray()).put("time",PolicyContract.emptyTime()).put("source_instruction",name) }
        rules[0].getJSONObject("scope").put("type","SPECIFIC_APP").put("apps",JSONArray().put("com.kakao.talk"))
        rules[1].getJSONObject("scope").put("type","SPECIFIC_APP").put("apps",JSONArray().put("com.whatsapp"))
        val app=activity.application as InboxApplication
        app.selection.installRules(JSONArray(rules),true,"")
        activity.setContent { InboxTheme { Surface(color=Color.White) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            PolicySettingsPanel(app,listOf(previewNotifications()[0].copy(packageName="com.kakao.talk",appLabel="카카오톡"),previewNotifications()[0].copy(packageName="com.whatsapp",appLabel="WhatsApp")))
        } } } }
        repeat(5){shadowOf(android.os.Looper.getMainLooper()).idleFor(100,TimeUnit.MILLISECONDS)}
        val view=activity.window.decorView
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(393,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(852,android.view.View.MeasureSpec.EXACTLY))
        view.layout(0,0,393,852)
        repeat(5){shadowOf(android.os.Looper.getMainLooper()).idleFor(100,TimeUnit.MILLISECONDS)}
        val bitmap=Bitmap.createBitmap(393,852,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("/tmp/relation-native.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        val result=JSONObject().put("status","ready").put("message","내부 정리 설명은 보이지 않아야 합니다").put("rules",JSONArray().put(rules[0]))
        app.selection.updateState{it.put("policy_proposal",JSONObject().put("base_revision",it.getJSONObject("policy").getString("id")).put("before",JSONArray()).put("result",result).put("validated",true))}
        activity.setContent{InboxTheme{Surface(color=Color.White){Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){PolicyProposalPanel(app.policies,listOf(previewNotifications()[0].copy(packageName="com.kakao.talk",appLabel="카카오톡")))}}}}
        repeat(5){shadowOf(android.os.Looper.getMainLooper()).idleFor(100,TimeUnit.MILLISECONDS)}
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(393,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(852,android.view.View.MeasureSpec.EXACTLY));view.layout(0,0,393,852)
        repeat(5){shadowOf(android.os.Looper.getMainLooper()).idleFor(100,TimeUnit.MILLISECONDS)}
        val review=Bitmap.createBitmap(393,852,Bitmap.Config.ARGB_8888);view.draw(Canvas(review))
        File("/tmp/simple-policy-native.png").outputStream().use{review.compress(Bitmap.CompressFormat.PNG,100,it)}
        controller.pause().stop().destroy()
    }
}
