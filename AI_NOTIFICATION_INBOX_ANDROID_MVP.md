# AI Notification Inbox — Android MVP 작업지시서

2026-09-27 최신 화면 구조: [정보 허브 수정안](docs/INFORMATION_HUB_SCOPE.md). Now/메신저/분류함을 순서대로 구현·검증한다. 세 화면은 원본 알림을 참조하며 정책과 의미 분류는 별도다.

2026-09-26 추가 범위: [실제 알림 기반 추천 UX](docs/CONTEXTUAL_RULE_RECOMMENDATION_SCOPE.md). 초기 정책 작성 강제를 해제하고 Timeline 추천→변경 확인→저장으로 점진적 설정을 지원한다. 기존 Phase 전체 게이트 완료를 뜻하지 않는다.

2026-09-24 앱 구현 갱신: [구조화 정책·온보딩 구현 기록](docs/STRUCTURED_POLICY_IMPLEMENTATION.md). OpenAI 정책 작성/위험 검증, 앱 정책 엔진·Room 저장·설정 UI·5단계 온보딩을 연결했다. 기존 정책은 확인 후 변환하며 AI Watch 문맥 추적은 미구현이다. 최종 테스트 상태는 구현 기록을 따른다.

아래는 구현 전 범위 결정 이력이며, 현재 구현 상태는 위 기록이 우선한다.

2026-09-24 첫 온보딩 범위: [First Onboarding](docs/FIRST_ONBOARDING_SCOPE.md). 권한 → 앱별 처리 → 꼭 볼 내용 → 추가 관심/줄일 내용 → 예시/최종 확인의 5단계와 시간·요일·임시 기간 조건을 개발 목표에 포함한다. 공통 OpenAI 정책 생성·검증·저장을 사용하며 아직 구현 완료가 아니다.

2026-09-24 검증 계약 추가: [Policy JSON Validation](docs/POLICY_JSON_VALIDATION.md). strict schema → 결정론적 검사 → 위험 변경만 OpenAI 의미 검증 → 질문 또는 Diff 확인 → 원자적 저장을 목표로 한다. 미검증 JSON은 활성화하지 않는다. 문서 반영 단계이며 구현·검증 완료는 아니다.

2026-09-24 최신 역할 결정: [OpenAI 정책 JSON 생성 / Jev 규칙 일치 판단](docs/AI_ROLE_DECISION.md). 이전 Jev 정책 생성 설계를 대체한다. 설치 앱의 기존 컴파일 경로는 후속 전환 대상이며 이번 결정만으로 변경된 것은 아니다.

2026-09-24 범위 추가: [Policy Model & Settings UX](docs/POLICY_MODEL_SETTINGS_SCOPE.md)를 적용한다. Scope·Condition·Action·Exception 분리, 정책별 대상, Room 저장, 우선순위/충돌 엔진, 계층 설정·직접 편집·변경 중심 Diff를 개발 범위에 포함한다. 기존 문장별 체크 구현과 구분하며 새 모델은 아직 미구현이다. 이번 명세의 A~F는 내부 구현 순서이고 기존 Phase 게이트는 유지한다.

2026-09-23 범위 갱신: [UI/UX 및 AI Watch 개발 범위](docs/UI_UX_AI_WATCH_SCOPE.md)를 함께 적용한다. 사용자 추가 지시에 따라 기본 Timeline·다축 분류·AI Watch·조건부 짧은 LLM 요약을 포함한다. 이는 개발 계획이며 현재 Phase 0 미완료 상태를 변경하지 않는다.

2026-09-23 후속 구현: 사용자 요청으로 [Android 테스트 앱](docs/ANDROID_APP_EXPERIENCE.md)에 Timeline·상세·기준 초안 및 USB 개발 서버 기반 재질문을 연결했다. Jev 실행 정책·실시간 자동 선별·AI Watch 및 각 Phase 전체 통과는 아직 아니다.

2026-09-23 추가 요청: 개인 테스트 debug APK에 기존 API 키를 포함하고 휴대폰 직접 HTTPS 호출로 전환했다. release에는 키를 포함하지 않는다. 이는 기준 정리의 독립 실행이며 자동 알림 선별이나 AI Watch 활성화를 의미하지 않는다.

2026-09-23 후속 요청: [개인 테스트용 자동 선별](docs/AUTOMATIC_SELECTION_TEST_APP.md)을 연결한다. 검토 완료 기준 하나를 명시적으로 켠 뒤 새 알림에 Jev를 적용하고, 불확실한 결과는 확인 필요로 남긴다. 원래 앱 알림 삭제/차단과 AI Watch는 포함하지 않는다. 품질 게이트와 실기기 미검증 항목은 별도 기록한다.

## 1. 프로젝트 목표

Android 스마트폰에 들어오는 여러 앱의 알림을 수집하고, 사용자가 자연어로 정의한 개인 기준에 따라 AI가 알림의 의미를 판단한다.

기존 알림 관리 앱처럼 앱·채널·키워드 단위로 사용자가 직접 복잡한 규칙을 만드는 방식이 아니라,

> “나는 이런 정보가 중요해.”

라고 자연스럽게 말하면 이를 AI가 이해하고 정책으로 변환한 뒤, 이후 들어오는 모든 알림에 지속적으로 적용하는 **개인화 AI Notification Inbox**를 만든다.

MVP의 핵심 가치는 다음과 같다.

> 모든 알림을 직접 읽지 않아도  
> 내가 알아야 할 사건만 구조화해서 보여준다.

---

# 2. AI 역할 분담 — 중요

본 제품은 **LLM과 Jev의 역할을 명확하게 분리한다.**

비용이 상대적으로 높은 LLM 사용은 최소화하고, 설정 시 규칙 JSON 생성은 OpenAI, 반복 알림의 규칙 일치 판단은 Jev가 담당한다.

전체 흐름:

```text
사용자의 자연어 지시
        ↓
OpenAI
문맥 이해 + 보충 질문 + 실행 규칙 JSON 생성
        ↓
App Code
검증 + 사용자 변경 확인
        ↓
Policy 저장
        ↓
새로운 알림 발생
        ↓
Jev
저장된 정책 기준으로 실시간 의미 분류
        ↓
JSON + probability
        ↓
App Code
표시 / 정책상 제외 / 이벤트 row / 향후 action
```

---

AI Watch 추가 흐름:

```text
선택한 대화의 수신 알림 → Room 문맥 누적 → Jev 의미 변화/사용자 참여 판단
→ 앱의 사용자 정책·중복·비용 검사 → 필요할 때만 LLM 짧은 맥락 요약
→ 근거 확인 → 이벤트 갱신 / 알림 / 원본 연결
```

# 3. LLM의 역할

OpenAI LLM은 **Policy JSON 생성·수정의 주 엔진이다.**

LLM의 역할은 사용자가 말한 복잡하고 모호할 수 있는 자연어를:

- 문맥을 이해하고
- 의미를 잃지 않도록
- 짧고
- 명확하며
- Jev가 처리하기 쉬운 지시문

과 실행 규칙 JSON으로 정리하는 것이다.

추가 범위(2026-09-22): 기존 기준과 새 요구의 누락·모순·지원 가능 범위를 확인하고, 실제 후보 조회를 요청하며, 공통 기준 확인 화면의 질문·선택지·응답 형식을 구성한다. 사용자 답변을 반영해 지시를 보완한다. 실행용 Policy JSON도 OpenAI가 생성하고 앱이 검증한다.

LLM은 다음 경우에만 호출한다.

1. 최초 정책 입력
2. 사용자가 정책을 수정
3. 기존 정책에 자연어 조건 추가
4. 지시 내용이 모호해 재정리가 필요한 경우
5. AI Watch에서 Jev가 알릴 시점으로 판단하고 앱의 정책·중복·비용 검사를 통과했을 때 짧은 맥락 요약

일반 notification 반복 분류에는 LLM을 사용하지 않는다. AI Watch 요약은 사용자 요청으로 추가된 예외이며, 모든 메시지마다 호출하지 않는다. 요약 실패로 확인이 필요한 사건 자체를 버리지 않는다.

---

# 4. LLM 입력/출력 예시

사용자:

> “회사 단톡은 평소에는 거의 안 알려줘도 되고, 일정 바뀌거나 내가 뭔가 해야 하는 게 생기거나 거래처 관련 얘기가 나오면 알려줘. 광고 같은 건 필요 없고. 그리고 내 이름이 직접 나오면 그것도 알려줘.”

LLM 출력:

> 회사 단톡의 일반 대화와 광고는 낮은 우선순위로 처리한다. 일정 변경, 사용자가 해야 할 일, 거래처 관련 메시지, 사용자의 이름이 직접 언급된 경우는 중요한 알림으로 처리한다.

중요:

LLM은 가능한 한 **짧은 normalized instruction**을 만든다.

LLM이 임의로 사용자의 요구를 추가하거나 삭제하면 안 된다.

---

# 5. OpenAI — Policy JSON 생성

OpenAI가 보충 질문으로 확인한 의도를 실행 정책 JSON으로 만든다.

앱은 구조화된 정책의 스키마·대상·버전·충돌을 검증하고 사용자가 확인한 뒤 저장한다. Jev 컴파일 단계는 두지 않는다.

예:

LLM 정리문:

> 회사 단톡의 일반 대화와 광고는 낮은 우선순위로 처리한다. 일정 변경, 사용자가 해야 할 일, 거래처 관련 메시지, 사용자의 이름이 직접 언급된 경우는 중요한 알림으로 처리한다.

OpenAI 정책 결과 예:

```json
{
  "scope": {
    "conversation_type": "group_chat",
    "context": "work"
  },
  "notify_when": {
    "schedule_change": true,
    "user_action_required": true,
    "client_related": true,
    "user_name_mentioned": true
  },
  "suppress_when": {
    "promotion": true,
    "casual_chat": true
  }
}
```

위 JSON은 초기 개념 예시다. 2026-09-24 이후 목표 실행 계약은 [정책 모델 범위](docs/POLICY_MODEL_SETTINGS_SCOPE.md)의 Scope / Condition / Action / Exception 및 버전 구조를 따른다.

---

# 6. 사용자 확인 UX

‘내 알림 기준’에서 적용 중·확인 필요·꺼짐을 구분한다. 미완성 지시와 응답은 저장하며, 재진입하면 최신 대화 후보로 이어서 설정한다. 수정 초안이 미완성인 동안 기존 기준은 유지한다. 질문 흐름·데이터 범위·완료 기준은 [기준 설정 개발 범위](docs/POLICY_SETUP_SCOPE.md)를 따른다.

OpenAI가 정책 JSON을 만들고 앱 검증을 통과하면 앱은 이를 사람이 읽기 쉬운 형태로 보여준다.

예:

### 현재 알림 기준

**알려드림**
- 일정 변경
- 내가 해야 할 일
- 거래처 관련 메시지
- 내 이름이 직접 언급된 경우

**조용히 처리**
- 일반 단톡 잡담
- 광고/프로모션

사용자가 확인하거나 수정할 수 있어야 한다.

사용자가 다시:

> “회비 내라는 것도 추가해줘.”

라고 하면 다시:

```text
사용자 자연어
→ LLM 짧게 정리
→ OpenAI 기존 JSON 수정 → 앱 검증
→ 새 Policy 저장
```

순서로 처리한다.

---

# 7. Jev — 실시간 규칙 일치 판단

새 알림이 들어올 때마다 LLM을 호출하지 않는다.

Jev가 저장된 Policy JSON과 notification 내용을 기반으로 실시간 판단한다.

입력:

- 현재 notification text
- source app
- conversation 정보
- 필요한 경우 최근 메시지 context
- UserPolicy JSON

출력 예:

```json
{
  "meeting_related": 0.96,
  "meeting_confirmed": 0.92,
  "schedule_change": 0.04,
  "payment_request": 0.03,
  "reply_required": 0.18,
  "promotion": 0.01,
  "matches_user_policy": 0.95,
  "should_notify": 0.94,
  "confidence": 0.96
}
```

앱 코드가 이 값을 이용해 행동한다.

---

# 8. 앱 코드의 역할

AI가 최종 시스템 동작을 직접 수행하지 않는다.

앱 코드가 Jev 결과를 바탕으로 최종 행동을 결정한다.

예:

```text
if confidence >= 0.90
and should_notify >= 0.85:
    create_event_card()
```

```text
if promotion >= 0.95
and matches_user_policy <= 0.10:
    mark_as_low_priority()
```

즉:

**OpenAI LLM = 지시 정리·질문·규칙 JSON 생성/수정 + AI Watch의 조건부 짧은 요약**

**Jev = 저장된 활성 규칙에 대한 실시간 확률형 일치 판단**

**App Code = 실제 실행**

이다.

---

# 9. Android Notification 수집

Android `NotificationListenerService`를 사용한다.

수집 필드:

- packageName
- appLabel
- notificationKey
- notificationId
- postedTime
- title
- text
- bigText
- subText
- conversationTitle
- MessagingStyle messages
- groupKey
- channelId
- contentIntent 존재 여부

대상 예:

- KakaoTalk
- WhatsApp
- Instagram
- Telegram
- Gmail
- Samsung Messages
- Google Messages
- Slack
- 기타 Android notification 앱

중요:

앱 내부 DB에는 접근하지 않는다.

Android notification에 실제 노출된 정보만 사용한다.

---

# 10. MVP 이벤트 타입

초기 이벤트 유형(Topic/Relationship과 분리):

```text
MEETING
SCHEDULE_CHANGE
PAYMENT_OR_DUES
DELIVERY_OR_RESERVATION
SECURITY
REPLY_REQUIRED
IMPORTANT_NOTICE
AI_TASK
PROMOTION
```

공통 판단:

```text
requiresUserAction
matchesUserPolicy
shouldNotify
confidence
```

---

event_type, topic, relationship, source_app은 별도 축으로 저장한다. observed_at과 event_time, 원문 evidence_refs, policy_version, 원본 확인 필요 상태도 분리한다. 관계를 알 수 없으면 미지정으로 남긴다. 앱이 임의의 중요도·긴급도 등급을 붙이지 않으며 사용자가 지정한 즉시 알림 정책만 적용한다. 확정 제외된 PROMOTION은 메인 Timeline에서 보여주지 않는다.

# 11. MEETING 이벤트

목표:

> 약속 확정  
> 토 19:00 · 강남역 5번 출구

Jev 판단:

```text
meeting_related
meeting_confirmed
date_present
time_present
location_present
```

구체적인 시간·날짜·장소 문자열을 Jev가 자유 생성하지 않도록 한다.

원문에서 candidate를 추출하고 Jev가 적합한 candidate를 선택하게 한다.

예:

```text
time candidates
A. 토요일 7시
B. 일요일 7시
C. 미정
```

Jev:

```text
selected_time = A
confidence = 0.97
```

---

# 12. PAYMENT_OR_DUES 이벤트

목표:

> 회비 납부  
> 30,000원 · 금요일까지

Jev 판단:

```text
payment_request
user_payment_required
amount_present
deadline_present
```

금액과 기한은 원문에서 candidate extraction 후 사용.

---

# 13. SCHEDULE_CHANGE 이벤트

목표:

> 일정 변경  
> 회의 14:00 → 15:00

판단:

```text
schedule_change
previous_schedule_present
new_schedule_present
```

---

# 14. REPLY_REQUIRED 이벤트

예:

> 답변 필요  
> 고객이 방문 가능한 시간을 묻고 있음

MVP에서는 장문의 AI 요약을 하지 않는다. 선택된 AI Watch 대화에서 Jev가 알릴 순간으로 판정한 경우에만 짧은 맥락 요약을 사용한다.

가능한 한:

```text
분류명
+
원문 핵심값
```

형태로 표시한다.

---

# 15. Conversation Context / AI Watch

사용자가 선택하고 켠 conversation에서 관찰한 알림 메시지를 Room에 누적하고 메모리는 최근 context 캐시로 사용한다. 프로세스 종료·재부팅 후 Watch 설정, 문맥, 마지막 처리 위치, 알린 사건을 복원한다. 같은 메시지의 재전달/묶음 갱신을 중복 누적하지 않는다.

최근10~30개 또는30~60분은 활성 문맥 실험 범위이며 토큰 상한과 조합해 검증한다. 실제 보관 기간과는 다르다. 설정용 후보 미리보기, Watch 문맥, 이벤트 근거의 보관 정책을 분리한다. 알림 미리보기가 꺼졌거나 수신되지 않은 메시지는 알 수 없다고 안내한다.

Jev는 meaningful_change, decision_reached, user_directly_addressed, user_reply_required, conversation_waiting_for_user, user_action_required, user_interest_match, should_notify_user를 판정하는 역할을 맡는다. 실제 typed 호출 계약과 임계값은 검증으로 결정한다.

사용자가 알아야 할 순간에만 짧은 LLM 요약을 생성한다. 예: ‘토요일 저녁 모임을 논의 중이며 현재 참석 가능 여부를 기다리고 있습니다.’ 제안/확정·타인 질문/본인 질문을 구분하고 원문에 없는 내용을 만들지 않는다. 원본 앱 알림 자동 취소와는 별개다.

긴 카카오톡은 이번 테스트에서1369자 중 앞500자만 저장됐다. 보편적500자 제한으로 확정하지 않는다. 이전 문맥과 현재 미리보기로 판단하되 정보가 부족하면 ‘원본 확인 필요’로 처리한다. 현재500자 이상 표시 기준을 유지하며 보이지 않은 뒷부분을 생성하지 않는다.

세부 저장·개인정보·실패 및 검증 기준은 [AI Watch 범위](docs/UI_UX_AI_WATCH_SCOPE.md)를 따른다.

---

# 16. AI Inbox

기본 화면은 **최신순 Timeline**이며 compact list row를 우선한다. 이벤트 유형, 핵심 내용, 출처 앱, 수신 상대 시각, 필요한 관계 태그를 보여준다. 임의 긴급도별 대시보드나 Android notification tray 복제로 만들지 않는다.

상단 View는 Timeline / Topic / Relationship. 같은 이벤트 데이터를 다른 관점으로 보여주며 Source는 보조 필터/메뉴다.

- Topic: 일정 / 돈 / 답변·요청 / 업무 / 개인 / 배송·예약 / AI 작업.
- Relationship: 업무 / 가족 / 친구·모임 / 개인 / 서비스 / AI·도구. 미지정 허용.
- 사용자 정의 카테고리 생성·구조 편집은 v1.1 이후 후보.

사용자가 요청한 이벤트만 메인에 보여주고 확정 제외한 광고/잡담은 설정·히스토리와 분리한다. 불확실한 사건의 원본 확인 필요와 확정 제외를 혼동하지 않는다. 사용자에게 허용된 긴 내용 확인 기준도 반영한다.

Smart Inbox는 허용된 이벤트를 정리하고, AI Watch는 선택한 대화에서 누적 맥락·변화·결정·참여 시점을 감지한다. Watch는 MVP의 주요 유료 가치 검증 대상이며 요금제/결제 구현은 아직 확정하지 않는다.

---

# 17. 3단계 UX

## Level 1

compact event row → Event Detail의 요약:

> 약속 확정  
> 토 19:00 · 강남역 5번 출구

## Level 2

관련 원문 notification:

```text
민수: 토요일 어때?
지훈: 난 7시 가능
수진: 강남에서 보자
민수: 그럼 7시 강남역 5번 출구
```

## Level 3

원문을 누르면 가능한 경우 notification `contentIntent`를 실행해 원래 앱으로 이동한다.

---

# 18. 사용자 Feedback

각 이벤트 상세:

- 중요함
- 중요하지 않음
- 잘못 분류됨

피드백은 저장한다.

MVP에서 모델 재학습은 하지 않는다.

하지만 향후:

- threshold
- local cache
- user policy

보정에 사용할 수 있도록 데이터 구조를 만든다.

---

# 19. Local Fast Path

모든 notification을 Jev에 보내지 않는다.

로컬에서 판단 가능한 것은 로컬에서 처리한다.

예:

- 확정된 광고 sender
- 반복 패턴
- OTP
- 배송
- whitelist
- blacklist
- 사용자가 직접 확정한 규칙

흐름:

```text
Notification
 ↓
Local Rule / Cache
 ↓
명확함?
 ├─ YES → 로컬 처리
 └─ NO → Jev 판단
```

Jev 호출 횟수가 사용하면서 줄어들 수 있도록 설계한다.

---

# 20. 비용 원칙

비용 최적화가 중요한 설계 목표다.

## LLM

호출 빈도 매우 낮음.

사용:

- 초기 policy 작성
- policy 수정
- 새로운 자연어 규칙 입력
- AI Watch에서 Jev와 앱의 통지 조건을 통과한 사건의 짧은 맥락 요약

설정 호출과 Watch 요약 호출은 각각 호출 수·입력량·latency·예상 비용을 측정한다. 문맥 창·중복 억제·재시도 한도를 검증한다.

사용하지 않음:

- 매 notification
- 반복 분류
- 실시간 이벤트 판단

---

## Jev

대량 실시간 판단 담당.

사용:

- Notification classification
- Event detection
- candidate selection
- probability/confidence 판단

---

# 21. 개인정보

LLM/Jev 전송 전에 `PrivacyMasker`를 적용한다.

검토:

```text
전화번호
이메일
OTP
카드번호
계좌번호
주민번호형 숫자
URL query
긴 숫자
```

가능하면:

```text
<PHONE>
<EMAIL>
<OTP>
<ACCOUNT>
```

형태로 전환.

필요한:

- 날짜
- 시간
- 금액
- 장소

는 의미 분석을 위해 유지 가능.

Release log에 raw notification text 출력 금지.

---

# 22. MVP 제외 기능

현재 구현하지 않는다.

- iOS
- Wear OS
- Calendar integration
- Google Tasks
- 장문 LLM 요약
- 보이스피싱
- 통화 STT
- Accessibility 자동화
- KakaoTalk 내부 DB
- 범용 Phone Agent
- 복잡한 계정 시스템
- 클라우드 동기화
- 사용자 정의 카테고리 생성/구조 편집

---

# 23. 향후 기능

## v1.1

- 사용자 정의 카테고리 생성/편집 검토

- Wear OS
- 중요한 것만 손목 전달
- Calendar 추가
- Todo 추가
- Spanish
- Japanese

## v1.2

자연어 automation:

> 약속이 확정되면 캘린더 등록 후보를 만들어줘.

> 회비 요청이 오면 할 일로 만들어줘.

> 거래처 관련 메시지는 워치까지 보내줘.

---

# 24. 권장 Android Stack

```text
Kotlin
Jetpack Compose
Room
Coroutines / Flow
DataStore
Retrofit 또는 Ktor
Hilt
```

Android native 우선.

---

# 25. 주요 모듈

```text
app/
 ├─ ai/
 │   ├─ InstructionSummaryClient
 │   ├─ OpenAiPolicyCompiler
 │   ├─ JevClassifier
 │   ├─ JevClient
 │   ├─ ClassificationSchema
 │   └─ PrivacyMasker
 │
 ├─ notification/
 │   ├─ NotificationListener
 │   ├─ NotificationNormalizer
 │   └─ ConversationBuffer
 │
 ├─ policy/
 │   ├─ UserPolicy
 │   ├─ PolicyRepository
 │   └─ PolicyEngine
 │
 ├─ event/
 │   ├─ EventExtractor
 │   ├─ CandidateExtractor
 │   └─ EventRepository
 │
 ├─ data/
 │   └─ Room
 │
 └─ ui/
     ├─ onboarding/
     ├─ inbox/
     ├─ eventdetail/
     ├─ policy/
     └─ settings/
```

---

# 26. Phase 0 — Notification Capture

AI를 붙이기 전에 Android notification capture부터 검증한다.

구현:

1. Kotlin + Compose 프로젝트
2. Notification Access 화면
3. `NotificationListenerService`
4. Notification normalization
5. Room 저장
6. Debug Inbox
7. contentIntent 실행

실기기:

- KakaoTalk 개인방
- KakaoTalk 단톡
- Gmail
- Samsung / Google Messages
- WhatsApp 가능하면 테스트

완료:

최소 5개 앱 notification text 안정적 수집.

---

# 27. Phase 1 — Jev Classification Spike

수집 notification 100~200개 사용.

구현:

- JevClient
- typed JSON output
- probability/confidence
- timeout/retry
- latency logging
- API cost logging
- debug probability screen

측정:

- p50 latency
- p95 latency
- accuracy
- 중요한 알림 false suppression

---

# 28. Phase 2 — Policy Pipeline

2026-09-22 개발 범위 확장. 상세 기준과 작업량은 [알림 기준 설정·보완 개발 범위](docs/POLICY_SETUP_SCOPE.md)에 기록한다. 현재 Phase 0 미완료 상태를 변경하지 않는다.

```text
사용자 자연어 + 기존 기준 + 앱 기능/제한 안내
↓
OpenAI LLM: 누락·충돌 확인, 실제 후보 조회 요청, 질문·선택지 구성
↕
앱 공통 확인 UI ↔ 사용자 응답 (초안 저장 / 중단 / 최신 후보로 재개)
↓
LLM: 확인된 최종 지시 정리
↓
OpenAI: 실행용 Policy JSON 생성 → 앱 유효성 검사
↓
사용자 변경 내용 확인 + 앱 유효성 검사
↓
활성 Policy 버전 저장
```

포함:

- 적용 중/미완성 기준 목록, 조회·수정·끄기·초안 삭제
- 실제 대화 후보 선택, 단일/복수 선택, 텍스트 입력의 공통 형식
- 최근 대화별 후보 저장과 보관 한도, 앱 종료 후 설정 재개
- 추가·변경·예외·충돌 구분, 기존 버전 보존과 되돌리기
- 앱 기능 안내의 자동 입력, 미지원/정보 부족 설명과 대안
- 실패·잘못된 후보·오래된 초안 처리, 비용 및 질문 부담 측정

2026-09-24 온보딩 추가 지시로 시간·요일·임시 기간 및 종료 후 기본 정책 재적용은 개발 범위에 포함한다. 지원 전에는 준비 중/미완성으로 표시한다. 알림 도착 시 설정 재개 푸시는 후속 범위다. LLM은 질문 UI용 데이터를 구성할 수 있지만 최종 실행 정책도 OpenAI가 생성하고 앱이 검증한다. 활성화되지 않은 초안으로 알림을 숨기지 않는다.

---

# 29. Phase 3 — Runtime Policy Classification

새 notification:

```text
Notification
+
UserPolicy JSON
+
Conversation Context
↓
Jev
↓
JSON probabilities
↓
PolicyEngine
```

Phase 3A: 결과를 DB에 저장한다. 활성 정책만 사용하고 미완성 수정 초안은 적용하지 않는다. 설정용 최신 대화 후보는 중요 알림 보관과 분리하며, 매 알림마다 LLM을 호출하지 않는다.

Phase 3B: 선택한 대화의 Watch 지속 문맥, Jev 변화 판정, 조건부 LLM 짧은 요약, 중복 억제와 실패 대체 안내를 연결한다. 재시작 복원·문맥 삭제·ID 변화·다른 대화 오병합·호출 비용을 검증한다. 일반 분류의 LLM 호출0과 Watch의 조건부 요약 호출을 분리 측정한다.

---

# 30. Phase 4 — AI Inbox / AI Watch UX

Phase 4A:

- Timeline 기본 진입, compact list row, Topic / Relationship 보기, Source 보조 필터
- 이벤트 다축 구조와 Event Detail → 근거 → 원래 앱 연결
- 확정 제외 재노출 방지, 원본 확인 필요, 사용자 기준 및 관계 수정

Phase 4B:

- Watch 목록·대상/기준·켜기/끄기·미완성 및 수집 제한 안내
- 사용자 참여 시점/결정의 짧은 맥락 요약 알림
- 중복 통지 방지, 새 행동 요청 누락 방지, 프로세스 재개 및 원본 연결

실제 관찰하지 않은 핵심 값은 추측해 채우지 않는다. 날짜·금액 등은 원문 후보 선택 검증이 끝난 항목부터 표시한다. 세부 완료 기준은 [추가 범위](docs/UI_UX_AI_WATCH_SCOPE.md)를 따른다.

---

# 31. Phase 5 — Event Candidate Extraction

구현:

- date
- time
- amount
- deadline
- location

candidate extraction.

Jev가 candidate selection 수행.

---

# 32. Phase 6 — Private Beta

본인 + 5~20명.

측정:

- notification/day
- Jev calls/day
- LLM calls/day
- Jev p50/p95 latency
- API cost/user/day
- correction rate
- false suppression
- Inbox opens/day
- Day-1 / Day-7 retention
- 유료 의향
- Watch별 문맥 메시지 수·Jev/요약 호출·비용, 반복 알림과 새 요청 누락
- 읽기 부담 감소, 요약 근거 정확성, 원본 확인 비율

---

# 33. 핵심 품질 기준

가장 위험한 오류:

> 중요한 알림을 숨기는 것

따라서 초기에는 보수적으로 처리한다.

Jev confidence가 애매하면:

**숨김 금지**

대신:

- Inbox에 남김
- 강한 알림만 생략

등으로 처리한다.

---

# 34. Codex 최초 작업 명령

아래는 Phase 0 전용 초기 구현 명령이다. 후속 Phase의 AI Watch 조건부 요약을 금지하는 문서로 해석하지 않는다.

```text
We are building an Android MVP called AI Notification Inbox.

The AI architecture is:

User natural-language instruction
→ OpenAI normalizes intent, asks clarifying questions and creates policy JSON
→ App validates the policy and saves it after user confirmation; Jev does not compile policies
→ Jev performs repeated real-time notification classification
→ App code performs the actual actions.

Do NOT use the LLM for repeated notification classification.
OpenAI is the policy JSON author. Jev only judges incoming notifications against saved rules.
App code validates policies and resolves explicit priority/exception rules.

Start with Phase 0 only.

Build an Android application using:
- Kotlin
- Jetpack Compose
- Room
- Coroutines / Flow

Implement:

1. NotificationListenerService.
2. Notification Access onboarding.
3. Capture notifications from other applications.
4. Normalize:
   - packageName
   - appLabel
   - notificationKey
   - notificationId
   - postedTime
   - title
   - text
   - bigText
   - subText
   - conversationTitle
   - MessagingStyle messages
   - groupKey
   - channelId
   - contentIntent availability
5. Persist normalized notifications in Room.
6. Create a simple debug Inbox.
7. Allow tapping a notification to invoke its original contentIntent when possible.
8. Add a details/debug screen showing which notification fields were available.
9. Never log raw sensitive notification text in release-safe logs.
10. Keep notification capture isolated behind clean interfaces for later Jev integration.

Do NOT implement yet:
- LLM
- Jev
- Wear OS
- Calendar
- Todo
- iOS
- AccessibilityService
- LLM summaries

After implementation:
- build the project
- fix compilation errors
- run tests
- report all changed files
- document Android/OEM limitations
- provide exact Galaxy manual test steps

Prioritize a working vertical slice over architectural complexity.
```

---

# 35. 최종 MVP 사용자 경험

사용자:

> “광고는 숨겨줘. 단톡방에서는 약속이 확정되거나 회비를 내야 할 때만 알려줘.”

↓

LLM:

> 광고와 일반 단톡 대화는 조용히 처리하고, 약속 확정과 회비/결제 요청은 중요한 알림으로 처리.

↓

OpenAI Policy JSON:

```json
{
  "notify_when": {
    "meeting_confirmed": true,
    "payment_request": true
  },
  "suppress_when": {
    "promotion": true,
    "casual_group_chat": true
  }
}
```

↓

새 notification 발생

↓

Jev 실시간 판단

↓

AI Inbox:

**약속 확정**  
토 19:00 · 강남역 5번 출구

**회비 납부**  
30,000원 · 금요일까지

**일정 변경**  
회의 14:00 → 15:00

**답변 필요**  
고객 문의

확정 제외된 광고는 메인 Timeline에서 표시하지 않는다. 필요하면 별도 설정/히스토리에서 확인한다.

↓

Timeline row → Event Detail 요약 → 관련 원본 notification

↓

다시 탭 → 원래 앱

---

# 36. 핵심 아키텍처 한 문장

> **OpenAI는 사용자 의도 정리·규칙 JSON 생성/수정과 AI Watch의 조건부 짧은 요약을, Jev는 활성 규칙에 대한 반복적인 의미·맥락 일치 판단을, 앱은 문맥 보존·사용자 정책 적용·이벤트 표시와 실제 행동을 담당한다.**

2026-09-24 사용자 추가 범위 및 구현: [항목별 선별 기준 관리](docs/RULE_MANAGEMENT.md). 하나의 활성 기준 묶음을 여러 항목으로 표시하고 항목별 적용/해제/삭제 및 AI 수정안 검토를 제공한다. 전체 Phase 품질 게이트 상태는 변경하지 않는다.
