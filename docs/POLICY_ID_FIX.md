# 기준 추가·수정 식별자 오류 수정 — 2026-09-24

## 원인과 수정

사용자에게 표시된 “기존 기준 식별자를 임의로 만들 수 없습니다.”는 `PolicyContract.validate`의 ID 검사에서 발생한다. 기존 응답 스키마는 ID를 자유 문자열로 받았지만, 로컬 검사는 기존 ID 또는 `new_` 접두사만 허용했다. 따라서 모델이 새 규칙에 UUID나 설명형 ID를 사용하면 새 기준인데도 기존 기준 참조 오류로 거부됐다. 당시 실패 응답은 보존되지 않아 사용자의 응답 문자열 자체를 재현했다고 주장하지 않는다.

요청마다 현재 규칙 ID와 앱이 배정한 새 ID 후보를 합쳐 `rules[].id`, `operations[].id`의 enum을 구성한다. 새 후보는 기존 ID와 겹치지 않는다. OpenAI에 `available_new_rule_ids`를 함께 전달하고, 이전 문장 항목 ID를 새 정책 ID로 재사용하지 않도록 지시한다. 자유 문자열보다 구체적으로 제한하는 방식은 [OpenAI Structured Outputs 문서](https://developers.openai.com/api/docs/guides/structured-outputs)의 enum 지원 범위에 해당한다.

기존 ID 변경, 변경 목록과 실제 Diff 불일치, 중복 ID, 위험 의미 변경에 대한 검사는 유지한다. 범위·조건·동작을 임의 수정하거나 AI가 잘못 참조한 ID를 유사한 다른 기준으로 추측 매핑하지 않는다. 모델·reasoning·API 키 설정은 변경하지 않는다.

## 검증

단위 55개와 debug APK/test APK 빌드·lint가 통과했다. 추가한 회귀 검사는 요청별 enum, 기존 ID 충돌 방지, 미허용 ID 거부, 실제 전송 스키마와 수정 시 ID 보존을 포함한다. Galaxy S23 / Android 16 실기기 연속 검사 1개(변환·추가·수정 3단계)가 통과했고 재시도 없이 실제 API 5회를 호출했다. 예상 비용은 $0.003611이며 Jev compile 호출은 0회다. [결과](../evaluations/android-app-integration/POLICY_ID_FIX_RESULT.json)를 기록하고 수정 debug APK를 설치했다.

실기기 검사는 합성 기준을 별도 DB에 저장하고 실제 OpenAI를 호출한다. 기존 문장 기준 변환 → 기준 추가 → 추가한 기준의 동작 수정 각각을 UI에서 확인·적용하며, 원래 기준의 JSON과 ID가 보존되는지 검사한다. 사용자의 실제 기준은 수정하지 않는다. 광범위한 의미 정확도와 Phase 0 전체 완료를 뜻하지 않는다.
