# v4 회귀·다회차 평가

기존40개는 실패 분석에 사용된 회귀 자료다. 신규10개 다회차는 독립 작성자가 사전에 작성·검토했다.
Jev 원문 기준 경로는 이번에 재실행하지 않았다. 기존 결과는 이전 실행의 참고 기준이다.
명확한 첫 지시는 원문 그대로 전달하고, 답변/기존 정책 변경이 있는 경우에만 모델이 합쳐 정리한다.

|항목|v3|v4|
|---|---:|---:|
|설정 상태 일치|29/40|33/40|
|명확한 지시 진행 차단|10|5|
|미완성을 ready로 오판|0|0|
|모의 알림 도달(.8)|101/160|0/160|

다회차 자동 상태·필수 어휘·후보 참조 검사: 4/10. 어휘 검사는 의미 보존의 완전한 증명이 아니다.

```json
{
  "openai": {
    "calls": 40,
    "tokens": {
      "input_tokens": 52286,
      "output_tokens": 6012,
      "cached_tokens": 0
    },
    "estimated_usd_known_usage": 0.0305336,
    "unknown_cost_calls": 0,
    "p50_ms": 1816.925,
    "p95_ms": 3147.95
  },
  "jev": {
    "calls": 0,
    "tokens": {},
    "estimated_usd_known_usage": 0,
    "unknown_cost_calls": 0,
    "p50_ms": null,
    "p95_ms": null
  },
  "multiturn": {
    "completed": 10,
    "passed_automated_checks": 4,
    "expected": 10,
    "usage": {
      "calls": 16,
      "tokens": {
        "input_tokens": 21949,
        "output_tokens": 2481,
        "cached_tokens": 0
      },
      "estimated_usd_known_usage": 0.012749200000000002,
      "unknown_cost_calls": 0,
      "p50_ms": 1966.825,
      "p95_ms": 3283.16
    }
  }
}
```

정책 JSON 컴파일 및 Android 적용 미검증. 보류는 모의 알림 성공으로 세지 않는다.
원시 모델 출력은 고정된 합성 평가에 한해 저장한다. 실제 사용자 원문을 제품 로그에 기록하지 않는다.
