package com.ainotification.inbox

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal object NowsetColors {
 val DeepNavy=Color(0xFF0B1F44)
 val VividBlue=Color(0xFF2F7BFF)
 val CoolGray=Color(0xFF94A3B8)
 val Mist=Color(0xFFF4F7FB)
 val Surface=Color(0xFFEAF0F8)
 val Muted=Color(0xFF5F718D) // Readable small text; CoolGray is reserved for subdued graphics.
 val ActionBlue=Color(0xFF2163D4) // White small button labels require more contrast than VividBlue.
 val BlueTint=Color(0xFFEAF2FF)
 val White=Color.White
 val Error=Color(0xFFAE1800)
}
@Composable internal fun NowsetLogo(modifier:Modifier=Modifier,dark:Boolean=false){
 Image(painterResource(if(dark)R.drawable.nowset_logo_stacked_light else R.drawable.nowset_logo_stacked),
  contentDescription="NOWSET · Set your now.",modifier=modifier.width(200.dp).aspectRatio(360f/344f).testTag("nowset_logo"))
}
@Composable internal fun NowsetAboutContent(dark:Boolean=false){
 val ink=if(dark)Color.White else NowsetColors.DeepNavy
 Column(Modifier.fillMaxWidth().background(if(dark)NowsetColors.DeepNavy else NowsetColors.Mist).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
  NowsetLogo(dark=dark)
  Text("지금을 내 기준으로.",style=MaterialTheme.typography.titleMedium,color=ink)
  Text("정보를 정돈하고, 지금에 집중하세요.",style=MaterialTheme.typography.bodyMedium,color=if(dark)NowsetColors.CoolGray else NowsetColors.Muted)
  Text("NOWSET · 0.1.0",style=MaterialTheme.typography.labelSmall,color=if(dark)NowsetColors.CoolGray else NowsetColors.Muted)
 }
}
