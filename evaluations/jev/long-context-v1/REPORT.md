# 프롬프트·시간순 누적 문맥 비교

|데이터|문맥|프롬프트|유효|중요도 정답|누락 FN|과잉 FP|사건 분류|대화 전체 중요도 통과|
|---|---|---|---:|---:|---:|---:|---:|---:|
|long|10|A_baseline|6/6|5/6|1|0|6/6|—|
|long|10|B_explicit|6/6|6/6|0|0|6/6|—|
|long|10|C_examples|6/6|6/6|0|0|6/6|—|
|long|30|A_baseline|6/6|5/6|1|0|6/6|—|
|long|30|B_explicit|6/6|6/6|0|0|6/6|—|
|long|30|C_examples|6/6|6/6|0|0|6/6|—|

## 실제 불일치

- {"id": "long/approval-03-length10/A_baseline", "case": "approval-03", "suite": "long", "mode": "10", "variant": "A_baseline", "thread": "approval", "step": 3, "expected": {"category": "REPLY_REQUIRED", "should_notify": true}, "valid": true, "text": "오전 중으로 부탁해요.", "category": "REPLY_REQUIRED", "p": 0.46}
- {"id": "long/approval-03-length30/A_baseline", "case": "approval-03", "suite": "long", "mode": "30", "variant": "A_baseline", "thread": "approval", "step": 3, "expected": {"category": "REPLY_REQUIRED", "should_notify": true}, "valid": true, "text": "오전 중으로 부탁해요.", "category": "REPLY_REQUIRED", "p": 0.42}
