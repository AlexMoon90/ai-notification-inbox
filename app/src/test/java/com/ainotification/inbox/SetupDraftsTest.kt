package com.ainotification.inbox

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SetupDraftsTest {
    @Test fun candidatesExcludeSummariesDeduplicateKeysAndBoundPrivatePreview() {
        val sample = previewNotifications().first()
        val rows = listOf(sample.copy(isGroupSummary = true)) + (1..25).map {
            sample.copy(notificationKey = "k$it", snapshotId = "s$it", text = "가".repeat(1000))
        } + listOf(sample.copy(notificationKey = "k1", snapshotId = "duplicate"))
        val candidates = notificationCandidates(rows)
        assertEquals(20, candidates.length())
        assertEquals("s1", candidates.getJSONObject(0).getString("id"))
        assertEquals(200, candidates.getJSONObject(0).getString("preview").length)
    }
    @Test fun reviewedDraftNeverClaimsActivationAndNullIsNotConfirmed() {
        val session = JSONObject().put("confirmed_instruction", JSONObject.NULL)
            .put("result", JSONObject().put("status", "ready"))
        val draft = JSONObject().put("session", session)
        assertEquals("검토 필요", draft.draftStatus())
        session.put("confirmed_instruction", "답변 요청을 알려준다")
        assertEquals("검토 완료 · 미적용", draft.draftStatus())
    }
}
