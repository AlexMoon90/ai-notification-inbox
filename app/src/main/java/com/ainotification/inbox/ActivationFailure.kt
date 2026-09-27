package com.ainotification.inbox

/** Only code-owned identifiers reach the UI; never provider bodies or exception messages. */
internal class ActivationFailure(val code: String) : IllegalArgumentException(code)
internal class JevHttpFailure(val status: Int) : Exception("Jev HTTP failure")

internal fun activationErrorMessage(error: Exception, stage: String): String {
    val message = when {
        error is ActivationFailure -> when (error.code) {
            "conflict" -> "선택한 항목끼리 조건이 충돌하거나 연결된 조건이 빠져 있습니다. AI에게 수정 요청을 눌러 어느 조건을 우선할지 알려주세요."
            "stale" -> "수정하는 동안 현재 기준이 바뀌었습니다. 최신 목록을 확인하고 수정안을 다시 요청해 주세요."
            "unsupported" -> "저장한 기준을 현재 수신 알림만으로 판단할 수 있는지 확인하지 못했습니다. 인터넷 문제는 아닙니다. 과거 대화 조회·자동 답장 등 알림 선별 밖의 요청이 포함됐는지 기준을 확인해 주세요."
            "binding_required" -> "어느 대화에 적용할지 선택이 필요합니다. 최근 알림 후보에서 대화를 선택해 기준을 다시 정리해 주세요."
            "binding_missing" -> "선택했던 알림에 대화 식별 정보가 없거나 기록이 삭제되었습니다. 해당 대화의 새 알림을 받은 뒤 후보를 다시 선택해 주세요."
            "selection_changed" -> "저장된 대화 선택과 정리된 기준이 일치하지 않습니다. 기준을 다시 정리하고 검토해 주세요."
            else -> "기준 검토가 완료되지 않았거나 저장 내용이 바뀌었습니다. 정리된 기준을 확인하고 검토 완료로 다시 저장해 주세요."
        }
        error is JevHttpFailure -> when (error.status) {
            401, 403 -> "알림 선별 서비스의 API 인증을 확인해야 합니다. 저장한 기준은 그대로 유지됩니다."
            402, 429 -> "알림 선별 서비스의 이용 한도 또는 요청 제한에 도달했습니다. 잠시 뒤 다시 시도해 주세요."
            in 500..599 -> "알림 선별 서비스가 일시적으로 응답하지 못했습니다. 잠시 뒤 다시 시도해 주세요."
            else -> "알림 선별 서비스가 요청을 받아들이지 못했습니다. 앱 연결 설정을 점검해야 합니다."
        }
        stage == "save" -> "적용할 기준을 기기에 저장하지 못했습니다. 저장 공간을 확인해 주세요."
        stage == "compile" && error is java.io.IOException -> "알림 선별 서비스에 연결하지 못했습니다. 인터넷 연결을 확인하고 다시 시도해 주세요."
        stage == "compile" -> "알림 선별 서비스의 응답을 읽지 못했습니다. 다시 시도해 주세요."
        stage == "binding" -> "선택한 대화 기록을 읽지 못했습니다. 최근 알림 후보를 다시 확인해 주세요."
        else -> "저장한 기준을 읽지 못했습니다. 기준을 다시 열어 검토 완료로 저장해 주세요."
    }
    return "$message\n기존 적용 기준은 변경하지 않았습니다."
}
