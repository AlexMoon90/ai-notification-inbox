# AI Notification Inbox — Android 테스트 앱

현재 개인 테스트 앱: [구조화 정책·검증·첫 온보딩](docs/STRUCTURED_POLICY_IMPLEMENTATION.md). OpenAI 규칙 JSON 생성과 위험 검증, Jev 알림 판단, 정책별 설정 및 5단계 첫 설정을 연결했다. 기존 기준 변환은 사용자 확인 후 적용하며 최종 검증 기록과 제한을 함께 확인한다.

Android 알림 접근을 통해 다른 앱의 알림을 수집하고 로컬 Room DB와 Compose Inbox에서 확인하는 Android 네이티브 앱입니다.

개발 전 반드시 [작업지시서](AI_NOTIFICATION_INBOX_ANDROID_MVP.md)와 [개발 원칙](AGENTS.md)을 읽습니다. Phase 0 전체 검증은 미완료입니다. 사용자 요청으로 UI와 기준 정리 테스트 경로를 추가했으며 자동 분류·AI Watch 품질 게이트는 유지합니다. [현재 앱 범위와 실행](docs/ANDROID_APP_EXPERIENCE.md).

## 로컬 키 설정

`.env.example`을 `.env.local`로 복사하고 본인의 OpenAI·TypeSafe 키를 입력합니다. 키가 없는 환경에서는 독립 실행용 debug 자산 생성이 실패합니다. 키나 APK를 Git에 추가하지 마세요.

## 실행

- JDK 17, Android SDK Platform 36, Build Tools 35.0.0
- Android 8.0(API 26) 이상 기기
- Android Studio에서 이 폴더를 열고 Gradle Sync 후 `app` 실행
- `local.properties`에 본인의 SDK 경로 설정: `sdk.dir=/path/to/Android/sdk`

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

이 Mac의 정적 검사에 사용한 Java 20을 사용할 경우(앱 바이트코드는 Java 17):

```sh
export JAVA_HOME='/Library/Java/JavaVirtualMachines/jdk-20.jdk/Contents/Home'
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## 현재 동작 (2026-09-27)

- **Now**: 사용자 기준에 따라 표시된 알림. 미판정 알림은 ‘확인할 알림’에서 확인합니다.
- **메신저**: 실제 수신된 앱별 대화, 최신순 목록과 메시지, 서비스 탭 좌우 스와이프. 전체에서는 작은 출처 아이콘을 표시하고 개별 탭에서는 생략합니다.
- **분류함**: 예약·배송·금융·쇼핑/주문·SNS·여행·기타로 원본을 참조합니다.
- **알림 기준**: Now 상단에서 관계도와 자연어 편집에 접근합니다. 보충 질문 → 검증 → 변경 확인 → 적용으로 기존 규칙을 수정합니다.
- **사진**: 알림에 제공된 이미지 미리보기와 확대를 지원합니다. 단톡방 목록에는 방 사진과 발신자 이름, 방 안에는 각 발신자 프로필을 표시합니다.

System UI와 Android 시스템 알림은 수집에서 제외합니다. Inbox의 숨김은 원래 앱의 알림을 삭제하거나 OS 알림을 차단하는 동작이 아닙니다. 전체 저장 기록을 조회하며 대량 기록 페이지 조회는 후속 과제입니다.

[UI·UX 및 제품 콘셉트 PDF](output/pdf/AI_Notification_Inbox_UI_UX_Concept.pdf) · [현재 정보 허브 구현과 제한](docs/INFORMATION_HUB_IMPLEMENTATION.md)

PendingIntent는 DB에 저장하지 않고 알림 스냅샷별로 최대 500개를 메모리에 보관합니다. 알림이 업데이트되거나 알림창에서 제거되어도 원래 앱이 취소하지 않은 연결은 시도할 수 있습니다. 다른 스냅샷의 이동 정보로 대체하지 않습니다. 프로세스 재시작이나 원래 앱의 취소로 연결이 없으면 안내 창에서 사용자가 ‘앱 열기’를 선택해 원래 앱을 실행할 수 있습니다. 이는 해당 대화로 바로 이동한다는 뜻은 아닙니다. 리스너가 재연결되면 현재 활성 알림의 연결을 다시 확보합니다.

## 데이터와 범위

- 개인 테스트 debug 빌드에서는 OpenAI 기준 편집과 Jev 판단을 직접 호출합니다. 필요한 텍스트만 API에 전달하며 사진·프로필은 전송하지 않습니다. 로컬 API 키는 debug 생성 자산에만 포함되며 저장소와 release에 포함하지 않습니다. 키가 포함된 debug APK는 배포하지 않습니다.
- 수집된 원문은 앱 전용 저장소의 Room DB에 있습니다. 별도 DB 암호화는 아직 없으며 OS 앱 저장소 보호에 의존합니다.
- 앱 백업·기기 이전에 데이터가 포함되지 않도록 제외 규칙을 설정했습니다. 화면 캡처와 최근 앱 미리보기는 FLAG_SECURE로 보호합니다.
- 원문/예외 내용을 로그로 출력하지 않습니다. 원래 알림을 삭제하거나 숨기지 않습니다.
- 기준 정리·재질문 뒤 검토 완료 기준 하나로 새 알림 자동 선별을 시험할 수 있습니다. [사용법과 제한](docs/AUTOMATIC_SELECTION_TEST_APP.md). AI Watch·캘린더·할 일·접근성 서비스는 아직 연결하지 않았습니다.

[Galaxy 수동 검증](docs/GALAXY_PHASE0_TEST.md) · [현재 검증 상태](docs/PHASE0_STATUS.md)

## Jev 합성 알림 사전 평가

사용자 요청으로 앱 연동에 앞서 합성 알림 120건을 Python으로 평가했습니다. [결과와 제한](docs/JEV_SYNTHETIC_EVALUATION.md), [데이터셋 및 실행 방법](evaluations/jev/README.md)을 참고하세요. 앱은 Phase 0 상태이며 이 독립 실험이 Phase 0/1 전체 완료를 의미하지 않습니다.

추가로 [프롬프트·시간순 대화 비교](docs/JEV_PROMPT_CONTEXT_EVALUATION.md)를 완료했습니다. 기존/기준 명시/예시 추가 지시, 현재 메시지/누적 문맥, 10개·30개 대화 조건을 총 756회 비교했습니다.

[사용자 요구 16가지 검증](docs/JEV_REQUIREMENTS_EVALUATION.md): 같은 알림에 상반된 정책을 적용한 합성 128회 실제 Jev 평가와 앱용 JSON 자동 채점 결과입니다.

## OpenAI 지시 정리·재질문 실험

사용자 요청으로 합성 대화 후보를 이용한 로컬 질문·선택·최종 지시 정리 프로그램을 추가했습니다. [실행 안내](evaluations/policy_setup/README.md) · [결과 및 남은 실패](docs/LLM_POLICY_SETUP_SPIKE.md). 초기 실험 이후 debug 앱에 휴대폰 직접 API 기준 정리 경로를 연결했습니다.

2026-09-23 우선순위 개선: [현재 지시 정리 구현·검증](docs/POLICY_SETUP_IMPROVEMENTS.md). 새 실험 진입점은 `scripts/policy_setup_reliable.py`이며, 명확한 지시 원문 보존·답변/변경 후 원문 대조 검사·후보 질문과 미활성 안내 보정을 포함합니다. 이전 버전과 실패 결과는 재현용으로 보존합니다.

## 추가 제품 개발 범위

2026-09-23 [UI/UX 및 AI Watch](docs/UI_UX_AI_WATCH_SCOPE.md): 기본 최신순 Timeline·Topic/Relationship·Source 보조 필터, 이벤트 다축 구조, 선택 대화의 Room 문맥 추적, Jev 판단 후 조건부 LLM 짧은 요약을 개발 범위에 반영했다. 사용자 정의 카테고리는 후속 후보다. Timeline·Source 필터·기준 초안 UI는 테스트 앱에 연결했으며 Topic/Relationship 자동 분류와 AI Watch는 미구현이다.

2026-09-24 [항목별 선별 기준 관리](docs/RULE_MANAGEMENT.md): 적용 체크/해제·삭제와 AI 추가/수정안 검토를 개인 테스트 앱에 연결했다. 기존 활성 기준 보존 및 Galaxy 검증 완료.

2026-09-24 [Policy Model & Settings UX 개발 범위](docs/POLICY_MODEL_SETTINGS_SCOPE.md): 적용 범위·조건·동작·예외로 분리된 정책, Room 저장, 우선순위/충돌 엔진, 계층 설정 및 변경 중심 Diff를 추가했다. 원문과 현재 구현 대비 추가 작업·검증 기준을 함께 보관하며 아직 구현 완료는 아니다.

## AI 역할 변경 결정 — 2026-09-24

[사용자 최신 역할 결정](docs/AI_ROLE_DECISION.md): OpenAI가 규칙 JSON 생성·수정을 담당하고 Jev는 활성 규칙에 대한 알림 일치 판단만 담당한다. 기존 구현·테스트 기록은 보존한다. 이번 변경은 개발 기준 문서화이며 설치 앱의 Jev 컴파일 제거와 새 JSON 파이프라인 검증은 아직 수행하지 않았다.

2026-09-24 [Policy JSON Validation](docs/POLICY_JSON_VALIDATION.md)을 개발 기준에 추가했다. 구조 검증·위험 분기·추가 의미 검증·질문·변경 확인·원자적 저장의 구현 및 회귀 기준이며 아직 앱에 연결된 것은 아니다.

2026-09-24 [첫 온보딩 개발 범위](docs/FIRST_ONBOARDING_SCOPE.md): 5단계 첫 설정, 앱별 기본 처리, 자연어 관심/제외, 고급 예시·최종 확인과 시간 조건을 포함했다. 공통 정책 검증/엔진 의존성과 단계별 완료 기준을 기록한 개발 계획이며 앱 구현 완료는 아니다.
