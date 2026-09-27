# OpenAI 지시 정리·재질문 실험

사용자가 요청한 독립 실험이다. Android 앱·알림 DB에 연결하지 않고, 직접 작성한 합성 대화 후보와 지시를 사용한다. LLM은 질문/선택지를 만들고 지시를 정리한다. Jev 실행 정책 생성과 실제 알림 분류·숨김은 수행하지 않는다.

## 직접 실행

프로젝트 루트에서 Python 3.9 이상으로 실행한다. 외부 Python 패키지는 필요 없다. 승인된 `.env.local`의 `OPENAI_API_KEY` 또는 환경변수를 읽는다. 키를 출력하거나 파일에 복사하지 않는다.

```sh
python3 scripts/policy_setup.py
```

- 지시 입력 후 선택 번호를 입력한다. 복수 선택은 `1,2`, 텍스트 답변도 가능하다.
- 후보 카드는 LLM이 구성한 질문과 실제 합성 후보의 이름·미리보기를 표시한다.
- Enter로 나가면 `.local/policy-setup/session.json`에 저장된 지시/답변으로 다음 실행에서 이어간다. 파일은 Git 제외, 권한 0600이다.
- 서로 다른 실험은 `--session .local/policy-setup/second.json`으로 분리한다. 목록 UI는 아직 없다.
- 후보가 없을 때는 저장 후 나갔다가 후보 파일이 갱신된 뒤 재실행하거나 `r`을 선택한다. `--candidates`로 다른 합성 후보 JSON을 지정할 수 있다.
- 후보가 변경되면 질문을 다시 구성하되 기존 답변과 선택 ID를 유지한다. 선택 대상이 사라졌다면 자동 대체하지 않고, 명시적으로 선택 해제 후 재지정한다.
- `y`는 최종 지시 초안 확인을 로컬에 저장할 뿐이다. Android 활성 정책은 변경하지 않는다.

현재는 개발자용 터미널 흐름이다. Compose 선택 화면, 기준 목록, 실제 방 식별·보관, Jev 연결, 운영 서버는 구현하지 않았다. 원문 개인정보 입력 없이 합성 정보로만 사용한다. 간단한 전화번호·이메일·API 키 마스킹은 있으나 production PrivacyMasker가 아니다.

## 자동 검사와 실제 API 평가

```sh
python3 -m unittest discover -s scripts -p 'test_policy_setup.py' -v
python3 scripts/evaluate_policy_setup.py
python3 scripts/evaluate_policy_setup.py --run --out evaluations/policy_setup/new-development
python3 scripts/evaluate_policy_setup.py --run --suite holdout --out evaluations/policy_setup/new-holdout
```

`--run` 없이는 API를 호출하지 않는다. 기본 12개는 최대 17회, 별도 표현 8개는 최대 10회 호출하며 자동 재시도하지 않는다. 단위 테스트는 실제 키/API를 사용하지 않는다. 평가 시나리오만 결과와 모델 원문 출력을 기록한다. 일반 대화 프로그램은 별도 원문 로그 없이 초안과 수치만 저장한다.

비용은 고정한 `gpt-4.1-mini-2025-04-14` 기준이다. `store=false`, strict JSON Schema 출력과 앱 측 검증을 사용한다. HTTP 오류는 상태/허용된 오류 코드만 남긴다. 응답이 없을 때 비용은 0으로 단정하지 않고 미확인으로 기록한다. 추정 단가: 입력 $0.40 / 캐시 입력 $0.10 / 출력 $1.60 (100만 토큰당, 2026-09-22 확인).

- [OpenAI 구조화 출력](https://developers.openai.com/api/docs/guides/structured-outputs)
- [사용 모델과 가격](https://developers.openai.com/api/docs/models/gpt-4.1-mini)

## 평가 해석

상태, 질문 종류, 실제 후보 참조, 선택 결과 보존, 일부 핵심 단어를 자동 검사한다. 전체 의미의 동등성·한국어 표현 품질·사용자가 질문을 이해하는지는 자동 점수로 보장하지 않는다. 별도 표현 사례도 이번에 직접 작성한 소규모 합성 데이터이며 실제 사용자 평가가 아니다.

`results`, `results-v2`, `results-v3`, `holdout-v3`에는 개선 중 실패도 보존했다. `final-development`, `final-holdout`은 최종 코드 재검사다. `smoke`는 샌드박스 네트워크 실패이며 API 응답 검증 성공으로 세지 않는다. 최신 프롬프트는 `prompt-v3.txt`와 구현 코드에 있다.

[결과 및 제한](../../docs/LLM_POLICY_SETUP_SPIKE.md)
