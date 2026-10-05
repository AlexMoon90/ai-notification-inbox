package com.ainotification.inbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val ModernInk=NowsetColors.DeepNavy
internal val ModernBg=NowsetColors.Mist
internal val ModernSurface=NowsetColors.Surface
internal val ModernAccent=NowsetColors.VividBlue
internal val ModernRedText=NowsetColors.Error
internal val ModernMuted=NowsetColors.Muted
internal val ModernFont=FontFamily(Font(R.font.archivo_400,FontWeight.Normal),Font(R.font.archivo_600,FontWeight.SemiBold),Font(R.font.archivo_800,FontWeight.ExtraBold))
internal val ModernTypography=Typography(
 displaySmall=TextStyle(fontFamily=ModernFont,fontSize=38.sp,lineHeight=38.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-1.3).sp),
 headlineMedium=TextStyle(fontFamily=ModernFont,fontSize=32.sp,lineHeight=36.sp,fontWeight=FontWeight.ExtraBold),
 titleLarge=TextStyle(fontFamily=ModernFont,fontSize=23.sp,lineHeight=28.sp,fontWeight=FontWeight.ExtraBold),
 titleMedium=TextStyle(fontFamily=ModernFont,fontSize=17.sp,lineHeight=23.sp,fontWeight=FontWeight.ExtraBold),
 titleSmall=TextStyle(fontFamily=ModernFont,fontSize=15.5.sp,lineHeight=20.sp,fontWeight=FontWeight.Bold),
 bodyLarge=TextStyle(fontFamily=ModernFont,fontSize=17.sp,lineHeight=27.sp),
 bodyMedium=TextStyle(fontFamily=ModernFont,fontSize=14.5.sp,lineHeight=22.sp),
 bodySmall=TextStyle(fontFamily=ModernFont,fontSize=12.sp,lineHeight=17.sp),
 labelLarge=TextStyle(fontFamily=ModernFont,fontSize=15.sp,lineHeight=20.sp,fontWeight=FontWeight.ExtraBold),
 labelMedium=TextStyle(fontFamily=ModernFont,fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.SemiBold),
 labelSmall=TextStyle(fontFamily=ModernFont,fontSize=11.sp,lineHeight=14.sp,fontWeight=FontWeight.SemiBold))

@Composable internal fun ModernLine(thick:Boolean=false,modifier:Modifier=Modifier){HorizontalDivider(modifier,thickness=if(thick)2.dp else 1.dp,color=ModernInk.copy(alpha=if(thick)1f else .4f))}
@Composable internal fun ModernButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,dark:Boolean=false){
 Button(onClick,modifier.fillMaxWidth().heightIn(min=50.dp),enabled,shape=RectangleShape,colors=ButtonDefaults.buttonColors(containerColor=if(dark)ModernInk else NowsetColors.ActionBlue,contentColor=ModernBg),contentPadding=PaddingValues(horizontal=16.dp)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(label);Icon(Icons.Outlined.ArrowForward,null,Modifier.size(18.dp))}
 }
}
@Composable internal fun ModernTab(label:String,selected:Boolean,click:()->Unit,modifier:Modifier=Modifier){
 Column(modifier.clickable(onClick=click),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.heightIn(min=44.dp).padding(horizontal=16.dp),contentAlignment=Alignment.Center){Text(label,fontWeight=if(selected)FontWeight.ExtraBold else FontWeight.Medium,color=if(selected)ModernInk else ModernMuted)};Box(Modifier.fillMaxWidth().height(4.dp).background(if(selected)ModernAccent else Color.Transparent))}
}
