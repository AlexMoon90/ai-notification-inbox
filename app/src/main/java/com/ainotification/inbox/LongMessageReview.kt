package com.ainotification.inbox

/** Kakao's observed 500 UTF-16-unit boundary applies to the current message,
 * not historical messages bundled in MessagingStyle. This is a hint, not proof of truncation.
 */
internal fun CapturedNotification.needsOriginalReview(): Boolean {
    if (packageName != "com.kakao.talk" || isGroupSummary) return false
    return currentMessageText().length >= 500
}
