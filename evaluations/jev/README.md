# Jev 합성 알림 평가

사용자 요청에 따라 Android 연동 전에 수행하는 독립 Python 실험이다. 웹 UI와 실제 기기 알림 전송은 없다. 프로젝트의 Phase 0 완료 여부와 별도로 결과를 기록한다.

## 데이터셋

`dataset.v1.jsonl`: 120개, 12개 그룹 × 10개. 약속 확정, 일정 변경, 납부, 배송/예약, 보안, 답변/행동 요청, 중요 공지, 광고, 부정/완료, 잡담, 문맥, 경계 사례를 포함한다. 한국어 중심이며 영어·줄임말·이모지·여러 줄·반복·명령 삽입·마스킹·빈 본문도 포함한다.

각 행은 합성 입력 `state`, 사전 기대 정답 `expected`, 라벨 근거 `rationale`을 가진다. **API에는 state와 공통 질문만 전달한다. 정답/그룹/근거는 전달하지 않는다.** source_app은 다양성을 위한 순환 배정이며 실제 Android 알림 포맷을 재현한 자료는 아니다. 단일 작성자의 기준이고, development/check는 기계적 분할로 독립 외부 검증셋이 아니다.

## 실행 및 결과 재현

Python 3 표준 라이브러리만 사용한다. 키는 프로젝트 `.env.local`의 `TYPESAFE_API_KEY` 또는 같은 환경 변수로 읽는다. 키는 결과에 저장하지 않는다.

```sh
# 오프라인 평가기 검증
python3 -m unittest discover -s scripts -p 'test_evaluate_jev.py'
# 저장된 응답만 다시 채점: API 비용 없음
python3 scripts/evaluate_jev.py
# 미실행 사례만 실제 API 호출 (과금 가능)
python3 scripts/evaluate_jev.py --run
# 별도 반복 측정이 필요한 경우: 전체 새 호출
python3 scripts/evaluate_jev.py --run --out evaluations/jev/run-v2
```

`build_jev_dataset.py`는 버전 1 데이터 생성 원본이다. 새 결과에 맞춰 기대 정답을 덮어쓰지 않는다. 라벨 수정이 필요하면 근거와 새 데이터 버전을 별도로 남긴다.

각 실행 폴더:

- `manifest.json`: 입력 SHA256, 질문/기준, 고정 모델, 임계값, 시작 시간
- `responses.jsonl`: 호출별 원본 응답, 입력 크기, HTTP 시도, 시간. 한 건씩 저장
- `judgments.jsonl`: 기대값/예측/오류/모의 행동
- `summary.json`: 정답률 집계, 혼동표, 중요 알림 누락, 토큰/비용/지연
- `REPORT.md`: 읽기 쉬운 결과와 모든 불일치 사례

같은 결과 폴더에서는 이미 시도한 사례(실패 포함)를 재호출하지 않는다. 데이터나 질문이 달라지면 새 결과 폴더가 필요하다. 동시에 같은 폴더로 두 프로세스를 실행하지 않는다.

## 채점 규칙 (첫 API 호출 전에 고정)

Choice 10개 범주와 Noul 4개(약속 확정, 납부 필요, 광고 포함, 알릴 필요)를 한 요청에서 독립 질문한다. 범주·필드·확률 범위·분포 합·usage를 검증한다. 이진 채점은 0.5 이상 참, null 정답은 제외한다. 범주는 배타적인 주된 사건이며 여러 사건 전체를 표현하지 않는다.

모의 행동은 Choice가 UNKNOWN이 아니고 confidence ≥0.8일 때만 판단한다. should_notify ≥0.8이면 notify, should_notify ≤0.1이고 promotion ≥0.95이면 suppress, 나머지는 retain_for_review다. 서비스/스키마 오류도 원문 유지로 처리한다. **폰의 알림에 이 동작을 적용하지 않는다.** Choice confidence는 분포 집중도이며 정답 보증이 아니다.

병렬 요청 3개, 요청 timeout 40초. HTTP 429/5xx에만 최대 1회 재시도하며 Retry-After(초)를 최대 30초까지 반영한다. 네트워크 단절은 과금 중복을 피하려고 자동 재시도하지 않는다. 실제 응답 usage로 비용을 계산하므로 응답을 못 받은 요청의 과금은 알 수 없다. p50은 중앙값, p95는 nearest-rank이며 요청 네트워크/재시도 시간을 포함한다.

[TypeSafe API](https://docs.typesafe.ai/api), [Choice](https://docs.typesafe.ai/primitives/choice), [Noul](https://docs.typesafe.ai/primitives/noul), [모델 및 가격](https://docs.typesafe.ai/models)을 확인했다. 고정 모델 jev-1.13.0; 입력 100만 토큰당 $0.042, 출력 무료(2026-09-21 확인). 실제 청구서는 별도다.
