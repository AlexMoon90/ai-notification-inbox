# Jev 합성 알림 평가 결과

- 실행 완료: 120/120, 유효 응답: 119
- 사건 분류 정답: 117/119; 모든 채점 가능 필드 정답: 110/119
- 중요한 알림 이진 판단 누락(0.5 기준): 4
- 모의 행동: {'notify': 60, 'retain_for_review': 49, 'suppress': 11}; 중요 알림 숨김: 0; 정답 보류 사례 숨김: 0
- HTTP 시도: 120; 입력/출력 토큰: {'input_tokens': 178494, 'output_tokens': 21731}
- 요청 시간 p50/p95: 680.6700000000001/869.18 ms (네트워크 포함, 동시 실행 3개)
- 확인 가능한 응답 사용량 기준 추정 비용: $0.007497. [공식 가격](https://docs.typesafe.ai/models): 입력 100만 토큰당 $0.042, 출력 무료 (2026-09-21 확인). 실패 요청 과금은 미확인.

## 그룹별 결과

|그룹|건수|유효|분류 정답|전체 필드 정답|
|---|---:|---:|---:|---:|
|confirmed|10|10|10|10|
|changed|10|10|10|9|
|payment|10|10|10|10|
|transaction|10|10|10|8|
|security|10|10|10|10|
|reply|10|10|10|7|
|notice|10|10|10|10|
|promotion|10|10|10|10|
|negative|10|10|10|9|
|casual|10|10|10|10|
|context|10|9|7|7|
|edge|10|10|10|10|

## 불일치 사례 (기대 정답을 사후 수정하지 않음)

### changed-06: meeting_confirmed

Your appointment has been rescheduled from 2 PM to 4 PM.

```json
{
  "expected": {
    "category": "SCHEDULE_CHANGE",
    "meeting_confirmed": false,
    "payment_required": false,
    "promotion": false,
    "should_notify": true
  },
  "predicted": {
    "category": "SCHEDULE_CHANGE",
    "meeting_confirmed": 0.6,
    "payment_required": 0.02,
    "promotion": 0.04,
    "should_notify": 0.94
  },
  "action": "notify"
}
```

### transaction-08: meeting_confirmed

예약하신 렌터카 인수 안내: 내일 09시 공항 지점.

```json
{
  "expected": {
    "category": "DELIVERY_OR_RESERVATION",
    "meeting_confirmed": false,
    "payment_required": false,
    "promotion": false,
    "should_notify": true
  },
  "predicted": {
    "category": "DELIVERY_OR_RESERVATION",
    "meeting_confirmed": 0.78,
    "payment_required": 0.04,
    "promotion": 0.05,
    "should_notify": 0.93
  },
  "action": "notify"
}
```

### transaction-09: meeting_confirmed

고객님의 식당 예약이 접수되었습니다. 인원 2명입니다.

```json
{
  "expected": {
    "category": "DELIVERY_OR_RESERVATION",
    "meeting_confirmed": false,
    "payment_required": false,
    "promotion": false,
    "should_notify": true
  },
  "predicted": {
    "category": "DELIVERY_OR_RESERVATION",
    "meeting_confirmed": 0.79,
    "payment_required": 0.03,
    "promotion": 0.05,
    "should_notify": 0.79
  },
  "action": "retain_for_review"
}
```

### reply-03: should_notify

첨부한 시안 A/B 중 선택해서 알려주세요.

```json
{
  "expected": {
    "category": "REPLY_REQUIRED",
    "meeting_confirmed": false,
    "payment_required": false,
    "promotion": false,
    "should_notify": true
  },
  "predicted": {
    "category": "REPLY_REQUIRED",
    "meeting_confirmed": 0.11,
    "payment_required": 0.04,
    "promotion": 0.06,
    "should_notify": 0.4
  },
  "action": "retain_for_review"
}
```

### reply-04: should_notify

고객 문의가 도착했습니다. 담당자인 회원님의 답변이 필요합니다.

```json
{
  "expected": {
    "category": "REPLY_REQUIRED",
    "meeting_confirmed": false,
    "payment_required": false,
    "promotion": false,
    "should_notify": true
  },
  "predicted": {
    "category": "REPLY_REQUIRED",
    "meeting_confirmed": 0.13,
    "payment_required": 0.05,
    "promotion": 0.06,
    "should_notify": 0.45
  },
  "action": "retain_for_review"
}
```

### reply-06: should_notify

Please reply with your preferred delivery address.

```json
{
  "expected": {
    "category": "REPLY_REQUIRED",
    "meeting_confirmed": false,
    "payment_required": false,
    "promotion": false,
    "should_notify": true
  },
  "predicted": {
    "category": "REPLY_REQUIRED",
    "meeting_confirmed": 0.11,
    "payment_required": 0.04,
    "promotion": 0.06,
    "should_notify": 0.49
  },
  "action": "retain_for_review"
}
```

### negative-02: should_notify

오늘 회비 납부 요청은 철회합니다. 돈 보내지 않아도 됩니다.

```json
{
  "expected": {
    "category": "GENERAL",
    "meeting_confirmed": false,
    "payment_required": false,
    "promotion": false,
    "should_notify": false
  },
  "predicted": {
    "category": "GENERAL",
    "meeting_confirmed": 0.15,
    "payment_required": 0.04,
    "promotion": 0.06,
    "should_notify": 0.7
  },
  "action": "retain_for_review"
}
```

### context-02: category

좋아, 그렇게 확정하자.

```json
{
  "expected": {
    "category": "UNKNOWN",
    "meeting_confirmed": null,
    "payment_required": false,
    "promotion": false,
    "should_notify": null
  },
  "predicted": {
    "category": "GENERAL",
    "meeting_confirmed": 0.39,
    "payment_required": 0.05,
    "promotion": 0.04,
    "should_notify": 0.35
  },
  "action": "retain_for_review"
}
```

### context-03: category, payment_required, should_notify

내일까지 부탁해.

```json
{
  "expected": {
    "category": "PAYMENT_OR_DUES",
    "meeting_confirmed": false,
    "payment_required": true,
    "promotion": false,
    "should_notify": true
  },
  "predicted": {
    "category": "REPLY_REQUIRED",
    "meeting_confirmed": 0.1,
    "payment_required": 0.47,
    "promotion": 0.06,
    "should_notify": 0.31
  },
  "action": "retain_for_review"
}
```

### context-05: invalid_response_schema

이제 안 보내도 돼.

```json
{
  "expected": {
    "category": "GENERAL",
    "meeting_confirmed": false,
    "payment_required": false,
    "promotion": false,
    "should_notify": false
  },
  "predicted": null,
  "action": "retain_for_review"
}
```

## 제한 및 다음 단계

단일 작성자가 만든 합성 데이터이며 실제 사용 정확도를 뜻하지 않는다. development/check는 기계적 분할로 독립 검증셋이 아니다. null 정답은 이진 정확도에서 제외한다. 보류는 원문 유지이며 중요 알림을 적극 알려주는 것과 다르다. 임계값은 실험용으로 사전 고정했고 검증된 운영 기준이 아니다.

정책 정규화·정책 JSON 생성·날짜/금액 추출·Android 실시간 연동은 이번 범위가 아니다. 서비스 오류/재시도는 실제 발생분만 관찰하며 별도 장애 주입 검증은 필요하다. 실패 사례의 라벨과 질문 경계를 검토하고 별도 신규 검증셋을 만든 후 다음 변경을 평가한다. Phase 0/1 전체 완료를 뜻하지 않는다.
