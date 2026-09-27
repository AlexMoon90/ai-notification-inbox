# 사용자 요구별 Jev 검증

|요구 ID|요구사항|중요도 정답|누락|과잉|분류 정답|
|---|---|---:|---:|---:|---:|
|completion-A|새로운 배송 완료·예약 접수·발권 완료 통지는 알려줘. 이미 해결된 업무 대화나 광고는 알리지 마.|7/8|0|1|8/8|
|completion-B|배송·예약·발권의 접수나 완료 통지는 알리지 마. 기존 일정이 변경되거나 취소될 때만 알려줘.|8/8|0|0|8/8|
|conversation_scope-A|카카오톡 단톡방의 확정된 약속만 알려줘. 개인방은 알리지 마. 방 종류를 모르면 보류해.|6/7|0|1|8/8|
|conversation_scope-B|카카오톡 개인방의 확정된 약속만 알려줘. 단톡방은 알리지 마. 방 종류를 모르면 보류해.|7/7|0|0|8/8|
|meetings-A|확정된 약속만 알려줘. 제안과 기존 일정 변경·취소는 알리지 마.|8/8|0|0|8/8|
|meetings-B|확정 여부와 무관하게 약속 제안, 확정, 기존 일정 변경·취소는 알려줘. 나머지는 알리지 마.|8/8|0|0|7/8|
|mentions-A|현재 메시지에서 내 이름 민수가 직접 언급되면 잡담이라도 알려줘. 광고는 제외해. 이름이 없으면 알리지 마.|8/8|0|0|7/8|
|mentions-B|나에게 명시적으로 답변·승인·자료 제출을 요구할 때만 알려줘. 단순 이름 언급이나 광고는 제외해.|7/8|0|1|7/8|
|payments-A|내가 아직 내야 하는 회비·결제 요청만 알려줘. 입금 확인, 요청 철회, 광고는 알리지 마.|7/8|1|0|6/8|
|payments-B|내 납부 요청뿐 아니라 입금 확인과 납부 요청 철회도 알려줘. 광고나 타인의 납부 건은 제외해.|7/8|1|0|6/8|
|priority_sender-A|발신자가 엄마면 잡담을 포함해 모든 메시지를 알려줘. 다른 발신자는 실제 계정 보안 경고만 알려줘.|8/8|0|0|8/8|
|priority_sender-B|발신자에 관계없이 실제 계정 보안 경고만 알려줘. 가족 잡담도 알리지 마.|8/8|0|0|8/8|
|promotions-A|광고와 쿠폰은 모두 알리지 마. 내 실제 주문 배송과 예약 통지만 알려줘.|8/8|0|0|8/8|
|promotions-B|내 실제 주문 배송과 예약 통지를 알려줘. 광고 중에는 커피 할인 쿠폰만 추가로 알려줘. 옷이나 호텔 광고는 제외해.|7/8|1|0|8/8|
|room_scope-A|회사개발방에서 내 답변·자료 제출 요청만 알려줘. 가족방과 그 외 방은 알리지 마.|8/8|0|0|8/8|
|room_scope-B|가족방에서 내 답변·자료 제출 요청만 알려줘. 회사개발방과 그 외 방은 알리지 마.|7/8|0|1|8/8|

## 불일치·보류 사례

```json
{
  "id": "meetings-06-B",
  "pair": "meetings-06",
  "policy_id": "meetings-B",
  "expected": {
    "category": "GENERAL",
    "should_notify": true
  },
  "policy": "확정 여부와 무관하게 약속 제안, 확정, 기존 일정 변경·취소는 알려줘. 나머지는 알리지 마.",
  "text": "확정은 아니고 토요일 어때?",
  "valid": true,
  "app_json": {
    "category": "REPLY_REQUIRED",
    "should_notify": 0.86,
    "category_confidence": 0.52,
    "action": "notify"
  },
  "correct": true
}
```

```json
{
  "id": "payments-03-A",
  "pair": "payments-03",
  "policy_id": "payments-A",
  "expected": {
    "category": "GENERAL",
    "should_notify": false
  },
  "policy": "내가 아직 내야 하는 회비·결제 요청만 알려줘. 입금 확인, 요청 철회, 광고는 알리지 마.",
  "text": "회비 납부 요청을 철회합니다. 돈 보내지 마세요.",
  "valid": true,
  "app_json": {
    "category": "PAYMENT_OR_DUES",
    "should_notify": 0.14,
    "category_confidence": 0.62,
    "action": "retain_for_review"
  },
  "correct": true
}
```

```json
{
  "id": "payments-03-B",
  "pair": "payments-03",
  "policy_id": "payments-B",
  "expected": {
    "category": "GENERAL",
    "should_notify": true
  },
  "policy": "내 납부 요청뿐 아니라 입금 확인과 납부 요청 철회도 알려줘. 광고나 타인의 납부 건은 제외해.",
  "text": "회비 납부 요청을 철회합니다. 돈 보내지 마세요.",
  "valid": true,
  "app_json": {
    "category": "PAYMENT_OR_DUES",
    "should_notify": 0.85,
    "category_confidence": 0.56,
    "action": "notify"
  },
  "correct": true
}
```

```json
{
  "id": "payments-04-A",
  "pair": "payments-04",
  "policy_id": "payments-A",
  "expected": {
    "category": "GENERAL",
    "should_notify": false
  },
  "policy": "내가 아직 내야 하는 회비·결제 요청만 알려줘. 입금 확인, 요청 철회, 광고는 알리지 마.",
  "text": "수진님만 미납이에요. 민수님은 추가로 내실 돈 없습니다.",
  "valid": true,
  "app_json": {
    "category": "PAYMENT_OR_DUES",
    "should_notify": 0.35,
    "category_confidence": 0.61,
    "action": "retain_for_review"
  },
  "correct": true
}
```

```json
{
  "id": "payments-04-B",
  "pair": "payments-04",
  "policy_id": "payments-B",
  "expected": {
    "category": "GENERAL",
    "should_notify": false
  },
  "policy": "내 납부 요청뿐 아니라 입금 확인과 납부 요청 철회도 알려줘. 광고나 타인의 납부 건은 제외해.",
  "text": "수진님만 미납이에요. 민수님은 추가로 내실 돈 없습니다.",
  "valid": true,
  "app_json": {
    "category": "PAYMENT_OR_DUES",
    "should_notify": 0.25,
    "category_confidence": 0.62,
    "action": "retain_for_review"
  },
  "correct": true
}
```

```json
{
  "id": "payments-06-A",
  "pair": "payments-06",
  "policy_id": "payments-A",
  "expected": {
    "category": "PAYMENT_OR_DUES",
    "should_notify": true
  },
  "policy": "내가 아직 내야 하는 회비·결제 요청만 알려줘. 입금 확인, 요청 철회, 광고는 알리지 마.",
  "text": "내일 오전까지 부탁드려요.",
  "valid": true,
  "app_json": {
    "category": "PAYMENT_OR_DUES",
    "should_notify": 0.49,
    "category_confidence": 0.54,
    "action": "retain_for_review"
  },
  "correct": false
}
```

```json
{
  "id": "payments-06-B",
  "pair": "payments-06",
  "policy_id": "payments-B",
  "expected": {
    "category": "PAYMENT_OR_DUES",
    "should_notify": true
  },
  "policy": "내 납부 요청뿐 아니라 입금 확인과 납부 요청 철회도 알려줘. 광고나 타인의 납부 건은 제외해.",
  "text": "내일 오전까지 부탁드려요.",
  "valid": true,
  "app_json": {
    "category": "PAYMENT_OR_DUES",
    "should_notify": 0.43,
    "category_confidence": 0.63,
    "action": "retain_for_review"
  },
  "correct": false
}
```

```json
{
  "id": "promotions-05-B",
  "pair": "promotions-05",
  "policy_id": "promotions-B",
  "expected": {
    "category": "DELIVERY_OR_RESERVATION",
    "should_notify": true
  },
  "policy": "내 실제 주문 배송과 예약 통지를 알려줘. 광고 중에는 커피 할인 쿠폰만 추가로 알려줘. 옷이나 호텔 광고는 제외해.",
  "text": "예약하신 숙소의 체크인 안내입니다.",
  "valid": true,
  "app_json": {
    "category": "DELIVERY_OR_RESERVATION",
    "should_notify": 0.26,
    "category_confidence": 1.0,
    "action": "retain_for_review"
  },
  "correct": false
}
```

```json
{
  "id": "conversation_scope-07-A",
  "pair": "conversation_scope-07",
  "policy_id": "conversation_scope-A",
  "expected": {
    "category": "MEETING",
    "should_notify": null
  },
  "policy": "카카오톡 단톡방의 확정된 약속만 알려줘. 개인방은 알리지 마. 방 종류를 모르면 보류해.",
  "text": "금요일 상담이 확정됐습니다.",
  "valid": true,
  "app_json": {
    "category": "MEETING",
    "should_notify": 0.27,
    "category_confidence": 0.98,
    "action": "retain_for_review"
  },
  "correct": null
}
```

```json
{
  "id": "conversation_scope-07-B",
  "pair": "conversation_scope-07",
  "policy_id": "conversation_scope-B",
  "expected": {
    "category": "MEETING",
    "should_notify": null
  },
  "policy": "카카오톡 개인방의 확정된 약속만 알려줘. 단톡방은 알리지 마. 방 종류를 모르면 보류해.",
  "text": "금요일 상담이 확정됐습니다.",
  "valid": true,
  "app_json": {
    "category": "MEETING",
    "should_notify": 0.49,
    "category_confidence": 0.99,
    "action": "retain_for_review"
  },
  "correct": null
}
```

```json
{
  "id": "conversation_scope-08-A",
  "pair": "conversation_scope-08",
  "policy_id": "conversation_scope-A",
  "expected": {
    "category": "MEETING",
    "should_notify": false
  },
  "policy": "카카오톡 단톡방의 확정된 약속만 알려줘. 개인방은 알리지 마. 방 종류를 모르면 보류해.",
  "text": "회의는 16시로 확정되었습니다.",
  "valid": true,
  "app_json": {
    "category": "MEETING",
    "should_notify": 0.56,
    "category_confidence": 0.97,
    "action": "retain_for_review"
  },
  "correct": false
}
```

```json
{
  "id": "mentions-06-B",
  "pair": "mentions-06",
  "policy_id": "mentions-B",
  "expected": {
    "category": "GENERAL",
    "should_notify": false
  },
  "policy": "나에게 명시적으로 답변·승인·자료 제출을 요구할 때만 알려줘. 단순 이름 언급이나 광고는 제외해.",
  "text": "민수님 요청하신 수정은 모두 완료했어요.",
  "valid": true,
  "app_json": {
    "category": "GENERAL",
    "should_notify": 0.57,
    "category_confidence": 0.89,
    "action": "retain_for_review"
  },
  "correct": false
}
```

```json
{
  "id": "mentions-07-A",
  "pair": "mentions-07",
  "policy_id": "mentions-A",
  "expected": {
    "category": "REPLY_REQUIRED",
    "should_notify": true
  },
  "policy": "현재 메시지에서 내 이름 민수가 직접 언급되면 잡담이라도 알려줘. 광고는 제외해. 이름이 없으면 알리지 마.",
  "text": "수진님만 회신해주세요. 민수님은 답변 안 하셔도 됩니다.",
  "valid": true,
  "app_json": {
    "category": "GENERAL",
    "should_notify": 0.67,
    "category_confidence": 0.58,
    "action": "retain_for_review"
  },
  "correct": true
}
```

```json
{
  "id": "mentions-07-B",
  "pair": "mentions-07",
  "policy_id": "mentions-B",
  "expected": {
    "category": "REPLY_REQUIRED",
    "should_notify": false
  },
  "policy": "나에게 명시적으로 답변·승인·자료 제출을 요구할 때만 알려줘. 단순 이름 언급이나 광고는 제외해.",
  "text": "수진님만 회신해주세요. 민수님은 답변 안 하셔도 됩니다.",
  "valid": true,
  "app_json": {
    "category": "GENERAL",
    "should_notify": 0.15,
    "category_confidence": 0.64,
    "action": "retain_for_review"
  },
  "correct": true
}
```

```json
{
  "id": "completion-05-A",
  "pair": "completion-05",
  "policy_id": "completion-A",
  "expected": {
    "category": "SCHEDULE_CHANGE",
    "should_notify": false
  },
  "policy": "새로운 배송 완료·예약 접수·발권 완료 통지는 알려줘. 이미 해결된 업무 대화나 광고는 알리지 마.",
  "text": "예약 시간이 18시에서 19시로 변경되었습니다.",
  "valid": true,
  "app_json": {
    "category": "SCHEDULE_CHANGE",
    "should_notify": 0.63,
    "category_confidence": 0.63,
    "action": "retain_for_review"
  },
  "correct": false
}
```

```json
{
  "id": "room_scope-07-B",
  "pair": "room_scope-07",
  "policy_id": "room_scope-B",
  "expected": {
    "category": "GENERAL",
    "should_notify": false
  },
  "policy": "가족방에서 내 답변·자료 제출 요청만 알려줘. 회사개발방과 그 외 방은 알리지 마.",
  "text": "보내주신 자료 확인했습니다. 끝났어요.",
  "valid": true,
  "app_json": {
    "category": "GENERAL",
    "should_notify": 0.71,
    "category_confidence": 0.95,
    "action": "retain_for_review"
  },
  "correct": false
}
```

