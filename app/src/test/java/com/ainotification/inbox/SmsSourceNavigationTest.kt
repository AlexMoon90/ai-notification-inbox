package com.ainotification.inbox

import android.app.Application
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class SmsSourceNavigationTest {
    private val sms get()=previewNotifications()[0].copy(packageName="com.samsung.android.messaging",serviceType="SMS",isGroupConversation=false,isGroupSummary=false,title="카드사",personIdentity="tel:1588-1234")
    @Test fun usesObservedNumberWithoutDraftOrSending(){
        val intent=smsSenderIntent(sms)!!
        assertEquals(Intent.ACTION_SENDTO,intent.action)
        assertEquals("15881234",intent.data!!.schemeSpecificPart)
        assertEquals("smsto",intent.data!!.scheme)
        assertEquals(sms.packageName,intent.`package`)
        assertNull(intent.extras)
    }
    @Test fun numericTitleFallbackAndInternationalNumber(){
        assertEquals("+821012345678",smsSenderNumber(sms.copy(personIdentity=null,title="+82 10-1234-5678")))
    }
    @Test fun neverGuessFromNameBodyOrGroup(){
        assertNull(smsSenderNumber(sms.copy(personIdentity=null,title="어머니",text="010-1234-5678")))
        assertNull(smsSenderNumber(sms.copy(isGroupConversation=true)))
        assertNull(smsSenderNumber(sms.copy(isGroupSummary=true)))
        assertNull(smsSenderNumber(sms.copy(packageName="com.kakao.talk",serviceType=null)))
        assertNull(smsSenderNumber(sms.copy(personIdentity="tel:123?body=test")))
    }
}
