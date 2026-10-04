package com.ainotification.inbox

import org.junit.Assert.*
import org.junit.Test

class SmartDashboardTest {
    @Test fun categoriesUseExistingEvidenceOnly(){
        assertEquals("schedule",smartCategory(listOf("SCHEDULE_CHANGE")))
        assertEquals("todo",smartCategory(listOf("REPLY_REQUIRED")))
        assertEquals("money",smartCategory(listOf("REFUND","ORDER")))
        assertEquals("other",smartCategory(emptyList()))
        assertEquals("other",smartCategory(listOf("UNKNOWN")))
    }
    @Test fun amountsAreLiteralAndAmbiguityIsNotResolvedByGuessing(){
        assertEquals("6,500원",smartAmount("스타벅스 6,500원 승인"))
        assertNull(smartAmount("원문에 금액 없음"))
        assertNull(smartAmount("이번 6,500원 누적 21,000원"))
    }
}
