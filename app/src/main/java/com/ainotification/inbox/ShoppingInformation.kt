package com.ainotification.inbox

internal val shoppingTabs=listOf("배송","환불","광고")
internal val shoppingPackages=setOf("com.coupang.mobile","com.elevenst","com.ebay.kr.gmarket","com.ebay.kr.auction","com.ssg","com.lotteon.app")
/** Explicit promotions only; an order/delivery message containing a coupon is not an ad. */
internal fun extractShoppingAd(row:CapturedNotification):LifeEvent? {
 if(row.isGroupSummary || row.isGroupConversation==true || row.packageName=="com.ainotification.inbox")return null
 val body=row.currentMessageText();val text=row.title.orEmpty()+"\n"+body
 val marked=Regex("[（(\\[]광고[）)\\]]").containsMatchIn(text)
 val commerce=Regex("쇼핑|특가|타임딜|장바구니|배송비|쿠팡|지마켓|G마켓|11번가|옥션|롯데온|SSG",RegexOption.IGNORE_CASE).containsMatchIn(text)
 if(!marked || (row.packageName !in shoppingPackages && !commerce))return null
 // Group conversations are excluded; service-style SMS/channel ads keep their source.
 if(messageLike(row) && row.packageName !in smsPackages && row.packageName!="com.kakao.talk")return null
 if(row.packageName=="com.kakao.talk" && row.conversationIdentity!=null && row.isGroupConversation!=false)return null
 val title=body.lineSequence().map{it.trim()}.firstOrNull{it.isNotBlank()}?.replace(Regex("[（(\\[]광고[）)\\]]"),"")?.trim()?.take(90)?.takeIf{it.isNotBlank()} ?: "${row.appLabel} 쇼핑 광고"
 return LifeEvent("event:${row.snapshotId}","delivery","advertisement",title,row.appLabel)
}
internal fun shoppingFilter(e:StructuredEntry,tab:String):Boolean=when(tab){
 "광고"->e.life?.status=="advertisement"
 "환불"->e.life?.status in setOf("returning","returned","refunded","cancelled")
 else->e.life?.status !in setOf("advertisement","returning","returned","refunded","cancelled")
}
internal suspend fun repairShoppingAds(db:InboxDatabase,anchor:Long):Int {
 var after=Long.MIN_VALUE;var id="";var count=0
 while(true){
  val rows=db.structured().missingShoppingAdPage(anchor,after,id);if(rows.isEmpty())return count
  for(row in rows){extractShoppingAd(row)?.let{persistLife(db,row,it,anchor);count++};after=row.capturedTime;id=row.snapshotId}
  kotlinx.coroutines.yield()
 }
}
