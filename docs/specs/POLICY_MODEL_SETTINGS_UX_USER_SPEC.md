# AI Notification Inbox — Policy Model & Settings UX

## 1. 목적

사용자는 자연어로 다음과 같은 요구를 말한다.

> “회사 단톡에서는 일정 변경이나 내가 답해야 할 때만 알려줘.”

> “광고는 숨기되 기타 장비 할인은 보여줘.”

> “친구 모임방에서는 약속 확정과 회비 요청만 알려줘.”

이 자연어 요구는 그대로 저장하지 않고, 앱이 실행할 수 있는 **구조화된 정책(Policy)** 으로 변환해야 한다.

또한 사용자는 언제든:

* 현재 어떤 정책이 적용되고 있는지 확인
* 켜기/끄기
* 수정
* 삭제
* 예외 추가
* 적용 범위 변경

을 할 수 있어야 한다.

---

# 2. 핵심 설계 원칙

정책은 단순한 category 하나로 저장하지 않는다.

각 정책은 최소한 다음 4개 요소를 가진다.

```text
Scope
Condition
Action
Exception
```

즉:

> 어디에 적용되는가
> 무엇을 찾는가
> 어떻게 처리할 것인가
> 어떤 예외가 있는가

를 분리해서 저장한다.

---

# 3. Policy 기본 구조

예시 JSON:

```json
{
  "id": "policy_001",
  "name": "회사 단톡 주요 알림",
  "enabled": true,

  "scope": {
    "apps": ["KakaoTalk"],
    "conversation_ids": ["work_room_123"],
    "relationship": "WORK"
  },

  "conditions": [
    {
      "type": "SCHEDULE_CHANGE"
    },
    {
      "type": "REPLY_REQUIRED"
    }
  ],

  "action": {
    "type": "SHOW"
  },

  "exceptions": [],

  "source_instruction": "회사 단톡에서는 일정 변경이나 내가 답해야 할 때만 알려줘.",

  "created_at": "...",
  "updated_at": "..."
}
```

---

# 4. Scope 구조

Scope는 정책이 어디에 적용되는지를 정의한다.

가능한 범위:

```text
ALL_APPS
SPECIFIC_APP
SPECIFIC_CONVERSATION
SPECIFIC_SENDER
RELATIONSHIP
SOURCE_TYPE
```

예:

```json
{
  "scope": {
    "apps": "ALL"
  }
}
```

또는:

```json
{
  "scope": {
    "apps": ["WhatsApp"],
    "conversation_ids": ["group_7788"]
  }
}
```

또는:

```json
{
  "scope": {
    "relationship": "WORK"
  }
}
```

---

# 5. Condition 구조

Condition은 어떤 내용일 때 정책이 적용되는지를 정의한다.

초기 Condition 타입:

```text
MEETING_CONFIRMED
SCHEDULE_CHANGE
PAYMENT_REQUIRED
MONEY_RECEIVED
REPLY_REQUIRED
ACTION_REQUIRED
DELIVERY
RESERVATION
SECURITY
IMPORTANT_NOTICE
PROMOTION
CASUAL_CHAT
USER_MENTIONED
AI_TASK_COMPLETED
AI_INPUT_REQUIRED
```

향후 확장 가능.

조건은 여러 개를 가질 수 있다.

예:

```json
{
  "conditions": [
    {"type": "MEETING_CONFIRMED"},
    {"type": "PAYMENT_REQUIRED"}
  ]
}
```

---

# 6. Action 구조

초기 Action:

```text
SHOW
QUIET
HIDE
WATCH
```

향후:

```text
NOTIFY_NOW
ADD_CALENDAR_CANDIDATE
ADD_TODO_CANDIDATE
SEND_TO_WATCH
```

등 확장 가능.

MVP에서는 우선:

```text
SHOW
QUIET
HIDE
WATCH
```

만 사용한다.

---

# 7. Exception 구조

상위 정책 안에 하위 예외를 둘 수 있어야 한다.

예:

> 모든 광고는 숨김
> 단, 기타 장비 할인은 표시

구조:

```json
{
  "name": "광고 숨김",
  "scope": {
    "apps": "ALL"
  },
  "conditions": [
    {
      "type": "PROMOTION"
    }
  ],
  "action": {
    "type": "HIDE"
  },

  "exceptions": [
    {
      "condition": {
        "topic": "GUITAR_GEAR"
      },
      "action": {
        "type": "SHOW"
      }
    }
  ]
}
```

---

# 8. 정책 우선순위

정책이 겹칠 수 있으므로 명확한 우선순위가 필요하다.

기본 원칙:

```text
더 구체적인 Scope
>
더 일반적인 Scope
```

예:

```text
특정 대화방
>
특정 앱
>
특정 관계
>
전체 앱
```

그리고:

```text
Exception
>
Parent Policy
```

즉:

> 전체 광고 숨김

보다

> 특정 기타 장비 광고 표시

가 우선한다.

---

# 9. Conflict 처리

두 정책이 충돌할 수 있다.

예:

```text
모든 광고 → 숨김

회사 관련 정보 → 표시
```

어떤 메시지가 “회사 광고”라면 충돌 가능.

이 경우 Policy Engine은:

1. scope specificity 비교
2. exception 여부
3. 최신 사용자 명시 정책
4. 그래도 불명확하면 사용자 확인

순서로 처리한다.

AI가 임의로 해결하지 않는다.

---

# 10. 자연어 → 정책 생성 흐름

사용자:

> “회사 단톡에서는 일정 변경이나 내가 답해야 할 때만 알려줘.”

↓

LLM:

문맥과 의도를 짧고 명확하게 정리.

↓

Jev:

Policy JSON 생성.

예:

```json
{
  "scope": {
    "relationship": "WORK",
    "conversation_type": "GROUP"
  },

  "conditions": [
    {"type": "SCHEDULE_CHANGE"},
    {"type": "REPLY_REQUIRED"}
  ],

  "action": {
    "type": "SHOW"
  }
}
```

↓

사용자 확인

↓

저장

---

# 11. 정책 수정 흐름

기존 정책:

```text
회사 단톡
- 일정 변경 표시
- 답변 필요 표시
```

사용자:

> “내 이름 나오면 그것도 알려줘.”

↓

LLM:

기존 정책 + 새 요구 정리

↓

Jev:

기존 JSON에:

```json
{
  "type": "USER_MENTIONED"
}
```

추가

↓

사용자에게 변경사항 표시:

```text
추가됨:
+ 내 이름 언급 → 표시
```

↓

저장

---

# 12. 정책 삭제/비활성화

각 정책 항목은 다음 상태 지원:

```text
enabled = true / false
```

사용자는 삭제하지 않고 일시적으로 끌 수 있어야 한다.

UI:

```text
[ON] 일정 변경
[OFF] 광고 숨김
```

삭제는 별도 action.

---

# 13. Policy Settings 메인 화면

정책 설정 화면은 복잡한 JSON을 보여주지 않는다.

예:

## 현재 알림 정책

### 모든 앱

* 광고 → 숨김

  * 예외: 기타 장비 할인 → 표시

* 일정 변경 → 표시

### 회사

* 답변 필요 → 표시
* 내 이름 언급 → 표시
* 일반 잡담 → 조용히 처리

### 친구/모임

* 약속 확정 → 표시
* 회비 요청 → 표시

각 항목에:

```text
toggle
edit
delete
```

제공.

---

# 14. Hierarchy UI

정책은 계층 구조로 보여주는 것이 중요하다.

예:

```text
모든 앱
 ├─ 광고 → 숨김
 │    └─ 예외: 기타 장비 → 표시
 │
 └─ 일정 변경 → 표시

회사
 ├─ 답변 필요 → 표시
 ├─ 내 이름 언급 → 표시
 └─ 일반 잡담 → 조용히

친구/모임
 ├─ 약속 확정 → 표시
 └─ 회비 요청 → 표시
```

상위 정책과 예외 관계가 한눈에 보여야 한다.

---

# 15. Policy Card UI

각 정책 Card:

```text
회사 단톡

일정 변경
답변 필요
내 이름 언급

→ 표시
```

아래:

```text
ON/OFF
수정
삭제
```

---

# 16. 자연어 입력 UI

정책 화면 상단 또는 하단에 자연어 입력창 제공.

예:

> 어떤 알림을 보고 싶은지 말해보세요.

사용자:

> “친구 모임에서는 약속하고 돈 내는 것만 알려줘.”

AI 처리 후:

> 이렇게 설정할게요.

```text
친구/모임
+ 약속 확정 → 표시
+ 결제/회비 요청 → 표시
```

사용자:

```text
확인
수정
취소
```

---

# 17. Diff UI

정책 변경 시 전체 정책을 다시 보여주지 않는다.

이번 변경사항만 보여준다.

예:

```text
이번 변경

추가
+ 내 이름 언급 → 표시

유지
· 일정 변경 → 표시
· 답변 필요 → 표시
```

또는:

```text
삭제
- 일반 광고 → 표시

변경
광고 표시 → 광고 숨김
```

사용자가 변경사항을 쉽게 이해해야 한다.

---

# 18. Direct Editing

사용자가 반드시 자연어를 사용할 필요는 없다.

정책 화면에서 직접:

```text
ON/OFF
삭제
범위 변경
Action 변경
```

가능해야 한다.

즉:

```text
Natural language = 정책 생성/수정의 쉬운 입력 방법
Policy UI = 정책을 직접 관리하는 방법
```

둘을 함께 제공한다.

---

# 19. MVP에서 직접 편집 가능한 항목

MVP에서는 최소한 다음을 지원한다.

```text
정책 ON/OFF
정책 삭제
Action 변경
Scope 확인
Condition 확인
```

복잡한 hierarchy drag/drop이나 custom category builder는 제외.

---

# 20. Policy Data Model 권장

```text
Policy
- id
- name
- enabled
- scope
- conditions[]
- action
- exceptions[]
- sourceInstruction
- createdAt
- updatedAt
```

---

# 21. Scope Data Model

```text
PolicyScope
- appIds[]
- conversationIds[]
- senderIds[]
- relationship
- sourceType
```

nullable 가능.

값이 없으면 더 넓은 범위로 간주.

---

# 22. Condition Data Model

```text
PolicyCondition
- type
- operator
- value
- confidenceThreshold(optional)
```

초기에는 type 중심.

향후:

```text
topic
keyword
person
time
```

등 확장 가능.

---

# 23. Action Data Model

```text
PolicyAction
- type
```

초기:

```text
SHOW
QUIET
HIDE
WATCH
```

---

# 24. Policy Engine 역할

Policy Engine은 다음을 수행한다.

```text
Jev runtime classification
↓
현재 활성 Policy 검색
↓
Scope matching
↓
Condition matching
↓
Exception 확인
↓
Specificity 계산
↓
최종 Action 결정
```

---

# 25. 프로그램 실행 규칙

프로그램은 의미를 임의로 해석하지 않는다.

Jev 결과:

```json
{
  "event_type": "PAYMENT_REQUIRED",
  "confidence": 0.94
}
```

Policy:

```json
{
  "condition": "PAYMENT_REQUIRED",
  "action": "SHOW"
}
```

↓

Program:

```text
SHOW
```

즉:

> Jev = 의미 판단
> Policy Engine = 규칙 매칭
> App = 실행

---

# 26. 정책이 없는 경우

어떤 이벤트에 적용되는 정책이 없으면 기본 행동을 사용한다.

MVP 권장:

```text
default_action = SHOW
```

중요 알림을 놓치는 위험을 줄이기 위해 처음에는 보수적으로 처리한다.

---

# 27. AI Watch 정책

AI Watch도 동일 Policy 시스템에 포함한다.

예:

```json
{
  "scope": {
    "conversation_id": "group_001"
  },

  "conditions": [
    {"type": "MEANINGFUL_CHANGE"},
    {"type": "REPLY_REQUIRED"},
    {"type": "MEETING_CONFIRMED"}
  ],

  "action": {
    "type": "WATCH"
  }
}
```

Watch 활성 conversation은 context 누적 대상으로 등록한다.

---

# 28. 정책 구조의 핵심 목적

사용자는 자연어로 쉽게 설정하지만,

앱 내부에서는 반드시:

```text
Scope
+
Condition
+
Action
+
Exception
```

으로 정리한다.

이 구조를 통해:

* 중복 정책
* 상위/하위 개념
* 예외
* 충돌
* 삭제
* 일시 비활성화

를 관리할 수 있다.

---

# 29. UX 핵심 원칙

사용자가 정책 화면을 보고 즉시:

> 지금 내가 뭘 보도록 설정했는지

이해할 수 있어야 한다.

사용자가 JSON이나 AI 로직을 이해할 필요는 없다.

정책 UI는:

```text
사람이 읽는 규칙
```

이어야 한다.

예:

```text
회사 단톡
일정 변경 · 답변 필요 · 내 이름 언급
→ 보여주기
```

---

# 30. Codex 구현 우선순위

## Phase A

Policy data model 생성

```text
Policy
Scope
Condition
Action
Exception
```

## Phase B

Room persistence

## Phase C

Policy Engine

Scope/Condition matching

## Phase D

Policy Settings 화면

* list
* hierarchy
* toggle
* delete

## Phase E

Natural language → Policy pipeline

```text
User
→ LLM normalization
→ Jev Policy JSON
→ preview
→ confirm
→ save
```

## Phase F

Diff UI

기존 정책 변경 시 변경사항만 표시

---

# 31. 구현 시 주의사항

정책 taxonomy를 코드에 너무 강하게 고정하지 않는다.

Jev가 새로운 event type이나 condition을 추가할 가능성을 고려한다.

다만 MVP에서는 허용된 schema 범위 안에서만 저장한다.

알 수 없는 값은:

```text
UNKNOWN
```

으로 처리한다.

---

# 32. 최종 사용자 경험

사용자:

> “광고는 안 보고 싶고, 회사 단톡에서는 일정 바뀌거나 내가 답해야 할 때 알려줘.”

↓

AI:

> 이렇게 설정할게요.

```text
모든 앱
광고 → 숨김

회사 단톡
일정 변경 → 표시
답변 필요 → 표시
```

↓

사용자 확인

↓

Policy 저장

↓

설정 화면:

```text
모든 앱
 └─ 광고 → 숨김

회사
 ├─ 일정 변경 → 표시
 └─ 답변 필요 → 표시
```

사용자는 언제든:

```text
끄기
삭제
수정
```

가능.

---

# 33. 핵심 정의

> 자연어는 정책을 만드는 가장 쉬운 입력 방식이고,
> 구조화된 Policy UI는 사용자가 자신의 AI 설정을 이해하고 통제하는 방법이다.
