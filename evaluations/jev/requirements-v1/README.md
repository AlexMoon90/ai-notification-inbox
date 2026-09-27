# 구체적인 사용자 요구 검증

16가지 사용자 요구(8가지 주제의 상반된 요구 2개), 64개 입력 × 요구 2개 = 실제 API 128회. 합성 데이터이며 휴대폰 메시지는 사용하지 않는다.

주제: 약속 확정/제안/변경, 납부/완납/철회, 광고 예외, 개인방/단톡방, 우선 발신자, 이름 언급/본인 행동 요청, 완료 통지, 방별 범위.

각 쌍은 policy만 바뀌고 나머지 state 및 사건 종류 기대값은 같다. 알릴지 여부는 정책별로 다르다. 판정 전에 입력과 정답, 질문, 모델과 임계값을 manifest로 고정한다. 기존 테스트를 보고 개선한 하나의 질문 세트를 사용하므로 이전 점수와 직접 비교하지 않는다.

JSON 원본은 Choice(category) + Noul(should_notify)이며, Python이 category/should_notify/category_confidence/action 구조의 app_json으로 변환한다. category_confidence는 분류 분포 집중도이며 전체 정확도 보증이 아니다. should_notify ≥0.5로 이진 채점, ≥0.8이면 모의 notify, 나머지는 원문 유지·보류다. 실제 알림을 숨기지 않는다. 미상인 방 종류는 null 정답으로 채점에서 제외하고 별도 기록한다.

## 범위

사용자의 자연어 요구를 state.policy로 직접 전달하여 **요구에 따른 스마트 자동 분류 가능성**을 검증한다. LLM 정규화 → Jev 정책 JSON 생성 → Android 실행 전체가 구현된 것은 아니다. 인원·문자량 제한, Pro 전환, 읽음 표시 보존, 실제 OS 알림 발생, 날짜/금액 추출은 이 평가의 범위가 아니다. 앱/방/발신자 등 확정 메타데이터의 엄격한 범위 검사는 향후 코드에서도 적용해야 한다.

일부 메타데이터(발신자 이름, 방 이름)는 테스트를 위해 제공한 합성 값이다. 실제 앱에서 모든 알림에 안정적으로 확보되는지를 보장하지 않는다. 라벨은 단일 작성자 작성이고 같은 문장을 재사용하는 쌍은 독립 표본이 아니다. 한 번씩 호출했으며 반복 안정성은 미검증이다.

## 실행

```sh
python3 -m unittest discover -s scripts -p 'test_jev_requirements.py'
python3 scripts/evaluate_jev_requirements.py       # 저장된 결과 재채점, API 호출 없음
python3 scripts/evaluate_jev_requirements.py --run # 미실행 사례만 API 호출
```

키는 .env.local 또는 TYPESAFE_API_KEY 환경 변수. 모델 jev-1.13.0, 병렬 3개, HTTP timeout 40초, HTTP 429/5xx에 한해 1회 재시도. 이전 비교 실행의 검증기를 사용하며 10개 범주의 확률 합 허용오차 0.051과 최대확률 선택 검사를 유지한다.

산출물: dataset.jsonl, manifest.json, responses.jsonl, judgments.jsonl, summary.json, REPORT.md. 형식 오류와 의미 판단 실패를 구분하고 원본 응답을 그대로 보존한다. 실제 가격 기준 추정 비용은 입력 100만 토큰당 $0.042를 사용하며 실청구서와 다를 수 있다. [TypeSafe API](https://docs.typesafe.ai/api), [Noul](https://docs.typesafe.ai/primitives/noul), [가격](https://docs.typesafe.ai/models).
