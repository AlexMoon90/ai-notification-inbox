# AI Notification Inbox — UI/UX 및 AI Watch 추가 작업지시

## 1. 제품 화면의 기본 철학

이 앱은 기존 Android Notification Center를 다시 만드는 앱이 아니다.

목적은:

> 쏟아지는 알림을 사용자가 판단하기 쉬운 형태로 정리하고,
> 지속적으로 확인하기 어려운 대화는 AI가 대신 맥락을 추적하다가
> 사용자가 알아야 할 시점에만 알려주는 것.

앱이 임의로 모든 정보의 중요도나 긴급도를 결정하지 않는다.

기본적으로 사용자가 중요하다고 설정한 정보만 보여주고, 사용자가 최종 판단한다.

---

# 2. 메인 화면은 두 가지 목적을 지원해야 한다

## 목적 A — 지금 들어오는 정보 확인

사용자가 최근 어떤 중요한 정보가 들어왔는지 빠르게 볼 수 있어야 한다.

기본 화면은 **시간순 Timeline**이다.

기존 Notification Center처럼 단순 원문 목록을 보여주는 것이 아니라, 각 알림을 의미 단위로 정리해서 표시한다.

예:

```text
[약속]
토 19:00 · 강남역 5번 출구
KakaoTalk · 12분 전

[회비]
30,000원 · 금요일까지
WhatsApp · 28분 전

[일정 변경]
회의 14:00 → 15:00
Slack · 42분 전

[AI 작업]
Codex 작업 완료
ChatGPT · 1시간 전
```

각 row에는 최소한 다음을 표시한다.

* Event type
* 핵심 내용
* Source app
* 시간
* 필요하면 관계/맥락 tag

기본은 카드보다 **compact list row**를 우선한다.

목적은 빠르게 훑어보는 것이다.

---

# 3. 시간순 Timeline이 기본 View

앱을 열었을 때 기본값은 Timeline이다.

정렬:

```text
최신
↓
과거
```

Timeline에서는 사용자가 보기를 요청한 정보만 표시한다.

사용자가 숨기도록 설정한:

* 광고
* 일반 잡담
* 불필요한 프로모션
* 사용자 정책상 제외된 항목

등을 메인 Timeline에 다시 보여주지 않는다.

필요하면 설정/히스토리에 별도 로그를 둘 수 있지만 MVP의 핵심 화면에서는 제외한다.

---

# 4. 분류 View 제공

Timeline 외에 같은 이벤트 데이터를 다른 관점으로 볼 수 있어야 한다.

MVP에서는 다음 View를 우선 검토한다.

```text
Timeline
Topic
Relationship
Source
```

---

## 4.1 Topic View

내용의 성격으로 분류한다.

초기 예:

```text
일정
돈
답변/요청
업무
개인
배송/예약
AI 작업
```

예:

### 돈

```text
회비 납부
30,000원 · 금요일까지

입금
120,000원 · 김민수
```

### 일정

```text
약속 확정
토 19:00 · 강남역

일정 변경
회의 14:00 → 15:00
```

---

## 4.2 Relationship View

내용이 발생한 생활 맥락/관계를 기준으로 본다.

예:

```text
업무
가족
친구/모임
개인
서비스
AI/도구
```

같은 Event도 Topic과 Relationship을 동시에 가질 수 있다.

예:

```json
{
  "event": "PAYMENT_REQUEST",
  "topic": "MONEY",
  "relationship": "FRIENDS_GROUP"
}
```

따라서 DB에서 하나의 단일 category만 저장하지 않는다.

여러 분류 축을 분리해서 저장한다.

---

## 4.3 Source View

출처 앱별 보기.

예:

```text
KakaoTalk
WhatsApp
Slack
Gmail
Instagram
Messages
ChatGPT
```

Source View는 보조적인 기능이다.

제품의 핵심은 앱별 관리가 아니라 내용/의미 기반 관리다.

---

# 5. MVP에서는 사용자 정의 카테고리 생성 제외

MVP에서는:

* Timeline View
* 기본 Topic View
* 기본 Relationship View

까지만 구현한다.

사용자가 직접 카테고리 이름을 만들고 구조를 편집하는 기능은 v1.1 이후 후보로 둔다.

이유:

> MVP의 검증 목표는 AI 기반 의미 정리가 실제로 유용한지 확인하는 것이다.

복잡한 category builder를 만드는 것이 목적이 아니다.

---

# 6. Event 중심 데이터 구조

앱 중심이 아니라 Event 중심으로 설계한다.

예:

```json
{
  "event_type": "MEETING_CONFIRMED",
  "topic": "SCHEDULE",
  "relationship": "FRIENDS_GROUP",
  "source_app": "KakaoTalk",
  "time": "19:00",
  "location": "Gangnam Station",
  "confidence": 0.96
}
```

다른 앱에서도 동일 Event 구조 사용.

WhatsApp, KakaoTalk, Slack 등의 차이는 source metadata일 뿐이다.

---

# 7. 핵심 유료 기능 — AI Watch

AI Watch는 이 제품의 주요 부가가치 기능이다.

단순한 알림 filter와 구분된다.

목적:

> 사용자가 단체 대화방을 계속 확인하지 않아도
> AI가 들어오는 알림 메시지를 누적하여 대화 맥락을 추적하고,
> 사용자가 알아야 할 시점에만 알려준다.

---

# 8. AI Watch 사용 예

사용자가 특정 대화방에 대해:

> 이 방에서는 약속이 정해지거나 내가 답해야 할 때 알려줘.

또는:

> 중요한 결정이 나면 알려줘.

라고 설정할 수 있다.

AI Watch가 활성화된 conversation에서는 들어오는 notification text를 지속적으로 누적한다.

---

# 9. Conversation Context 저장

같은 conversation에서 들어오는 notification message를 로컬 DB에 저장한다.

예:

```text
18:02 민수: 토요일 시간 괜찮아?
18:03 지훈: 나는 괜찮아
18:05 수진: 어디서 볼까?
18:07 민수: 강남은?
18:10 수진: 준석님은 괜찮아요?
18:12 민수: 준석님 일정 보고 정하죠
```

AI는 개별 메시지만 보지 않고 최근 conversation context를 함께 본다.

---

# 10. AI Watch가 판단해야 하는 주요 상태

Jev는 누적 context를 기반으로 다음과 같은 확률형 판단을 한다.

예:

```json
{
  "meaningful_change": 0.92,
  "decision_reached": 0.42,
  "user_directly_addressed": 0.98,
  "user_reply_required": 0.95,
  "user_action_required": 0.91,
  "user_interest_match": 0.94,
  "should_notify_user": 0.96
}
```

핵심 질문은:

```text
이 대화에서 사용자에게 알려줄 만큼 의미 있는 변화가 생겼는가?

사용자가 직접 질문을 받았는가?

사용자가 답해야 대화가 진행되는가?

사용자가 설정한 관심 조건에 해당하는가?

결정이 이루어졌는가?

새로운 조치가 필요한가?
```

---

# 11. 사용자가 참여해야 할 시점 감지

AI Watch의 중요한 기능 중 하나다.

예:

```text
민수: 토요일 가능?
지훈: 가능
수진: 장소는 강남 어때?
민수: 준석님은 괜찮아요?
수진: 준석님 답 보고 확정하자
```

AI 판단:

```json
{
  "context_type": "MEETING_DISCUSSION",
  "user_directly_addressed": 0.98,
  "reply_required": 0.97,
  "conversation_waiting_for_user": 0.91
}
```

이 시점에서 사용자에게 알림.

---

# 12. 이때만 LLM 요약 사용

AI Watch에서는 모든 메시지를 LLM에 보내지 않는다.

먼저 Jev가 값싼 비용으로 지속적으로 conversation context를 분석한다.

다음과 같이 판단됐을 때만 LLM을 호출한다.

```text
should_notify_user > threshold
```

LLM은 사용자가 대화에 들어가기 전에 알아야 할 맥락을 짧게 정리한다.

예:

> **답변이 필요해요**
> 토요일 저녁 강남에서 만나는 방향으로 논의 중이며, 현재 당신의 참석 가능 여부를 기다리고 있습니다.

또는:

> **모임 내용이 정해졌어요**
> 토요일 오후 7시 강남역에서 만나기로 했고, 회비는 3만원입니다.

---

# 13. AI Watch의 핵심 UX 원칙

사용자가 알림 20개를 받는 대신 AI Watch notification 하나를 받을 수 있어야 한다.

기존:

```text
message
message
message
message
message
...
```

AI Watch:

```text
[답변 필요]

토요일 강남 모임을 논의 중이며
현재 당신의 참석 가능 여부를 묻고 있습니다.
```

즉:

> Notification Reduction보다 Reading Reduction이 더 중요하다.

---

# 14. AI Watch와 일반 Smart Inbox 구분

## Smart Inbox

모든 허용된 notification을:

* Event 분류
* Timeline 표시
* Topic/Relationship 분류

한다.

## AI Watch

선택된 conversation의 흐름을 지속적으로 추적하고:

* 누적 context
* 의미 있는 변화
* 사용자 참여 시점
* 결정
* 필요한 action

을 감지한다.

AI Watch가 더 높은 부가가치를 가지는 기능이다.

---

# 15. Notification 조건

AI Watch가 작동하려면 원본 앱에서 notification이 활성화되어 있어야 한다.

사용자에게 명확하게 안내한다.

예:

> AI Watch는 Android 알림에 표시되는 메시지를 기반으로 작동합니다.
> 이 대화방을 AI가 지켜보려면 원본 앱의 알림과 메시지 미리보기가 켜져 있어야 합니다.

---

# 16. 메시지 길이 제한 대응

메신저 앱마다 notification text 길이 제한이 존재할 수 있다.

예: KakaoTalk notification 본문은 확인된 테스트 기준 약 500자 제한.

AI Watch는 긴 문서 전체를 완벽하게 요약하려는 기능이 아니다.

목적:

* 대화 주제
* 맥락
* 변화
* 결정
* 사용자 관련성

을 판단하는 것이다.

긴 메시지가 잘린 경우에도:

```text
이전 conversation context
+
현재 notification preview
```

를 이용해 의미 판단을 시도한다.

세부 내용이 필요한 경우:

> 원문 확인 필요

로 처리한다.

절대 보이지 않은 내용을 생성하지 않는다.

---

# 17. Conversation 저장 정책

메모리만 사용하지 않는다.

Android process는 언제든 종료될 수 있다.

따라서:

```text
Room DB
+
memory cache
```

하이브리드 사용.

Room:

* 지속적 conversation context 보존

Memory:

* 최근 context 빠른 처리

예:

```text
최근 10~30 messages
또는
최근 30~60분
```

을 active context로 사용한다.

---

# 18. 개인정보 원칙

Conversation text는 매우 민감하다.

기본 원칙:

* 가능한 한 로컬 저장
* 필요 이상 장기 보관하지 않음
* 외부 AI 호출 전 privacy masking
* debug/release log에 원문 출력 금지
* 사용자 data retention 설정 제공 가능

향후:

```text
7일
30일
즉시 삭제
```

등 보관 설정 검토.

---

# 19. UI 구조 권장안

메인 상단에 View selector:

```text
Timeline | Topic | Relationship
```

필요하면 Source는 별도 filter/menu.

기본:

```text
Timeline
```

---

# 20. Timeline Row 예

compact list 형태.

```text
[약속]
토 19:00 · 강남역 5번 출구
KakaoTalk · 친구모임 · 12분 전

[회비]
30,000원 · 금요일까지
WhatsApp · 모임 · 28분 전

[답변 필요]
고객이 방문 가능 시간을 문의
Messages · 업무 · 41분 전
```

큰 카드보다 scan speed를 우선한다.

---

# 21. Event Detail

Timeline row 선택:

```text
Event Summary
↓
관련 원본 notification/message
↓
원래 app
```

즉:

```text
결론
→ 근거
→ 원본
```

구조 유지.

---

# 22. 화면에서 중요도 강제 판단 금지

기본 MVP에서는 앱이 임의로:

```text
긴급
매우 중요
낮음
```

등을 강제 분류하지 않는다.

사용자가 요청한 정책에 따라 표시 여부를 결정하고,

최종 중요도 판단은 사용자가 한다.

다만 사용자가:

> 결제 관련은 항상 바로 알려줘.

처럼 정책을 명시했다면 해당 정책을 적용한다.

---

# 23. MVP 핵심 UX

MVP 사용자 경험은 다음으로 정의한다.

```text
Android notifications
↓
Jev semantic classification
↓
사용자가 요청한 알림만 선택
↓
Timeline
↓
Topic / Relationship View
```

그리고 선택한 대화방에서는:

```text
Incoming conversation notifications
↓
Local accumulation
↓
Jev context monitoring
↓
Meaningful moment detected
↓
LLM short context summary
↓
Notify user
```

---

# 24. 제품 핵심 정의

Smart Inbox의 가치:

> 쓸데없는 알림을 줄이고 중요한 정보를 정리한다.

AI Watch의 가치:

> 사용자가 대화를 계속 읽지 않아도 AI가 대신 흐름을 보고 있다가 사용자가 알아야 할 시점에 알려준다.

두 기능을 합친 제품 정의:

> **Do not make the user read notifications.
> Help the user understand what is happening.**
