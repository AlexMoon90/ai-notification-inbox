# v5 회귀·다회차 평가

기존40개는 실패 분석에 사용된 회귀 자료다. 신규10개 다회차는 독립 작성자가 사전에 작성·검토했다.
Jev 원문 기준 경로는 이번에 재실행하지 않았다. 기존 결과는 이전 실행의 참고 기준이다.
명확한 첫 지시는 원문 그대로 전달하고, 답변/기존 정책 변경이 있는 경우에만 모델이 합쳐 정리한다.

|항목|v3|v5|
|---|---:|---:|
|설정 상태 일치|29/40|40/40|
|명확한 지시 진행 차단|10|0|
|미완성을 ready로 오판|0|0|
|모의 알림 도달(.8)|101/160|148/160|

다회차 자동 상태·필수 어휘·후보 참조 검사: 5/10. 어휘 검사는 의미 보존의 완전한 증명이 아니다.

```json
{
  "openai": {
    "calls": 40,
    "tokens": {
      "input_tokens": 52286,
      "output_tokens": 7065,
      "cached_tokens": 0
    },
    "estimated_usd_known_usage": 0.16109199999999999,
    "unknown_cost_calls": 0,
    "p50_ms": 1800.175,
    "p95_ms": 3104.12
  },
  "jev": {
    "calls": 384,
    "tokens": {
      "input_tokens": 195799,
      "output_tokens": 8064,
      "cached_tokens": 0
    },
    "estimated_usd_known_usage": 0.008223558,
    "unknown_cost_calls": 0,
    "p50_ms": 502.655,
    "p95_ms": 610.9
  },
  "multiturn": {
    "completed": 10,
    "passed_automated_checks": 5,
    "expected": 10,
    "usage": {
      "calls": 18,
      "tokens": {
        "input_tokens": 25044,
        "output_tokens": 3670,
        "cached_tokens": 0
      },
      "estimated_usd_known_usage": 0.079448,
      "unknown_cost_calls": 0,
      "p50_ms": 1752.935,
      "p95_ms": 2951.3
    }
  }
}
```

정책 JSON 컴파일 및 Android 적용 미검증. 보류는 모의 알림 성공으로 세지 않는다.
원시 모델 출력은 고정된 합성 평가에 한해 저장한다. 실제 사용자 원문을 제품 로그에 기록하지 않는다.


429 최초 실패3회는 settings.jsonl에 유지. effective-settings.jsonl은 별도 재시도 결과를 합친 회복 후 결과다. 전체 시도 비용은 summary의 settings_usage_including_failed_attempts 참조.
