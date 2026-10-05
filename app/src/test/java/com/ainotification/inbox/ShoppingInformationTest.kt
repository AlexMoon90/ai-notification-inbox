package com.ainotification.inbox

import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@org.robolectric.annotation.Config(sdk=[34],application=android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class ShoppingInformationTest {
 private fun row(body:String)=previewNotifications()[0].copy(snapshotId="shopping-test",packageName="com.coupang.mobile",appLabel="테스트 쇼핑",title="쇼핑",text=body,bigText=null,messagesJson="[]",isGroupSummary=false,isGroupConversation=false,notificationCategory=null,conversationIdentity=null,postedTime=1,capturedTime=1)
 @Test fun explicitShoppingAdHasSeparateStatus(){val ad=extractShoppingAd(row("(광고) 주말 특가 상품을 만나보세요"))!!;assertEquals("delivery",ad.kind);assertEquals("advertisement",ad.status);assertFalse(ad.title.contains("(광고)"))}
 @Test fun deliveryAndFinancialMarketingAreNotShoppingAds(){
  assertNull(extractShoppingAd(row("배송 완료. 다음 주문 쿠폰을 확인하세요")))
  assertNull(extractShoppingAd(row("(광고) 금융 상품 안내").copy(packageName="bank.app",title="은행")))
  assertNull(extractShoppingAd(row("(광고) 주말 특가").copy(isGroupConversation=true)))
 }
 @Test fun explicitSmsShoppingAdIsIncluded(){assertNotNull(extractShoppingAd(row("(광고) 온라인 쇼핑 특가").copy(packageName="com.samsung.android.messaging",serviceType="SMS")))}
 @Test fun exactlyThreeTabsSeparateStates(){
  assertEquals(listOf("배송","환불","광고"),shoppingTabs)
  fun entry(status:String):StructuredEntry{val e=StructuredEvent("e","source","delivery","상품","앱","test",1,false,1,1);return StructuredEntry(e,null,life=LifeEvent("e","delivery",status,"상품","앱"))}
  for(status in listOf("ordered","preparing","shipping","arriving_today","delivered")){assertTrue(shoppingFilter(entry(status),"배송"));assertFalse(shoppingFilter(entry(status),"환불"));assertFalse(shoppingFilter(entry(status),"광고"))}
  for(status in listOf("returning","returned","refunded")){assertTrue(shoppingFilter(entry(status),"환불"));assertFalse(shoppingFilter(entry(status),"배송"))}
  assertTrue(shoppingFilter(entry("advertisement"),"광고"));assertFalse(shoppingFilter(entry("advertisement"),"배송"))
 }
 @Test fun localBackfillIsIdempotentAndPreservesOriginal()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<android.content.Context>()
  val db=Room.inMemoryDatabaseBuilder(context,InboxDatabase::class.java).build()
  try{val r=row("(광고) 쇼핑 특가");db.notifications().insert(r);assertEquals(1,repairShoppingAds(db,10));assertEquals(0,repairShoppingAds(db,10));assertEquals(r,db.notifications().find(r.snapshotId));assertEquals("advertisement",db.structured().observe().first().single().life!!.status)}finally{db.close()}
 }
}
