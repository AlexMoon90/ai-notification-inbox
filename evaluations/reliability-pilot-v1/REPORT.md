# 다국어 신뢰성 파일럿 v1

합성 지시 40개(영어24/한국어12/혼용4), 명확한 지시32개 × 알림12개 = 고유 알림384개.
기존 v3 프롬프트를 수정하지 않은 첫턴 기준선이다. 사용자 답변 루프·정책 JSON 컴파일·Android 동작은 이번 실험에 포함되지 않았다.
gold는 독립 작성자가 명시한 기준, normalized는 OpenAI 정리문이다. 두 경로는 별도 Jev 요청으로 평가한다.
정리문 경로가 차단된 알림도 전체384 분모에 포함한다. 정리문 의미 보존은 별도 검토가 필요하다.

|언어|설정 상태 정답|위험한 ready|명확한 지시의 진행 차단|
|---|---:|---:|---:|
|all|29/40|0|10|
|en|21/24|0|3|
|ko|6/12|0|5|
|mixed|2/4|0|2|

|경로/언어|유효/전체|분류 정답(.5)|누락 FN(.5)|과잉 FP(.5)|실제 알림 조건 충족(.8)/필요|
|---|---:|---:|---:|---:|---:|
|gold/all|383/384|370|0|13|146/160|
|gold/en|239/240|233|0|6|98/100|
|gold/ko|108/108|103|0|5|33/45|
|gold/mixed|36/36|34|0|2|15/15|
|normalized/all|264/384|252|0|12|101/160|
|normalized/en|204/240|195|0|9|80/100|
|normalized/ko|48/108|45|0|3|16/45|
|normalized/mixed|12/36|12|0|0|5/15|

분류 threshold .5는 진단 점수다. .8 이상만 모의 적극 알림으로 계산하고, 나머지는 retain_for_review로 보관한다. 보관은 알림 성공으로 세지 않는다. 숨김은 실행하지 않는다.

## 비용·지연

```json
{
  "openai": {
    "calls": 40,
    "tokens": {
      "input_tokens": 71606,
      "output_tokens": 6635,
      "cached_tokens": 0
    },
    "estimated_usd_known_usage": 0.03925840000000001,
    "unknown_cost_calls": 0,
    "p50_ms": 1895.63,
    "p95_ms": 3968.27
  },
  "jev": {
    "calls": 648,
    "tokens": {
      "input_tokens": 333615,
      "output_tokens": 13587,
      "cached_tokens": 0
    },
    "estimated_usd_known_usage": 0.014011830000000008,
    "unknown_cost_calls": 1,
    "p50_ms": 482.28499999999997,
    "p95_ms": 581.48
  },
  "paired": {
    "comparable": 263,
    "both_correct_05": 248,
    "gold_correct_normalized_wrong_05": 8,
    "gold_wrong_normalized_correct_05": 3,
    "both_wrong_05": 4
  }
}
```

## 설정 불일치

- {"id": "pilot-003", "instruction": "In Slack, notify me only when a message explicitly assigns an action to Robin, my name. Exclude completed actions and assignments to other people.", "expected": "ready", "actual": {"id": "pilot-003", "valid": true, "result": {"status": "needs_input", "message": "'Robin'이라는 이름이 사용자 본인의 이름인지 확인이 필요합니다. 또한, '명시적으로 할당된 행동'의 구체적인 기준을 알고 싶습니다.", "questions": [{"id": "q1", "field": "identity", "kind": "text", "prompt": "알림 대상인 'Robin'이 사용자의 이름이 맞습니까? 맞다면 이름을 정확히 입력해 주세요.", "reason": "사용자 이름이 명확해야 알림 조건을 정확히 설정할 수 있습니다.", "options": []}, {"id": "q2", "field": "meaning", "kind": "multiple", "prompt": "'명시적으로 할당된 행동'이란 어떤 경우를 의미합니까? 해당되는 항목을 모두 선택해 주세요.", "reason": "할당된 행동의 구체적 의미를 알아야 알림 조건을 정확히 설정할 수 있습니다.", "options": [{"id": "assign_task", "label": "업무나 작업 지시가 포함된 메시지", "candidate_id": null}, {"id": "멘션_이름_포함", "label": "사용자 이름이 멘션된 메시지", "candidate_id": null}, {"id": "특정_키워드_포함", "label": "특정 키워드(예: '해야 함', '책임자')가 포함된 메시지", "candidate_id": null}]}], "unresolved": ["사용자 이름 확인 필요", "명시적 할당 행동의 구체적 의미 필요"], "normalized_instruction": null, "referenced_candidate_ids": [], "change_kind": "new", "change_summary": "사용자 이름과 명시적 할당 행동의 구체적 의미를 확인하는 질문 추가"}, "metrics": [{"model": "gpt-4.1-mini-2025-04-14", "prompt_version": "v3", "request_bytes": 13576, "latency_ms": 7250.96, "input_tokens": 1790, "output_tokens": 343, "cached_tokens": 0, "estimated_usd": 0.0012648000000000002, "error": null, "api_attempts": 1}]}}
- {"id": "pilot-011", "instruction": "Notify me only about Instagram notifications explicitly saying Maya sent me a direct message. Exclude likes, comments, mentions and messages from other people.", "expected": "ready", "actual": {"id": "pilot-011", "valid": true, "result": {"status": "needs_input", "message": "Instagram 알림 중 Maya가 보낸 다이렉트 메시지만 알려주고 싶으시군요. Maya가 누구인지 명확히 알기 위해 이름이나 호칭을 알려주세요.", "questions": [{"id": "q1", "field": "identity", "kind": "text", "prompt": "Maya의 이름이나 호칭을 알려주세요. 알림에서 인식할 이름입니다.", "reason": "Maya가 누구인지 명확히 해야 해당 발신자 알림만 필터링할 수 있습니다.", "options": []}], "unresolved": [], "normalized_instruction": null, "referenced_candidate_ids": [], "change_kind": "new", "change_summary": "Maya의 이름/호칭을 확인하는 질문을 추가함."}, "metrics": [{"model": "gpt-4.1-mini-2025-04-14", "prompt_version": "v3", "request_bytes": 13589, "latency_ms": 2728.22, "input_tokens": 1789, "output_tokens": 157, "cached_tokens": 0, "estimated_usd": 0.0009668000000000001, "error": null, "api_attempts": 1}]}}
- {"id": "pilot-020", "instruction": "Notify me only about Google Messages notifications explicitly saying my account password was changed or that there was a new login. Exclude verification codes and security advertisements.", "expected": "ready", "actual": {"id": "pilot-020", "valid": true, "result": {"status": "needs_input", "message": "'Google Messages' 앱 알림 중에서 '내 계정 비밀번호 변경' 또는 '새 로그인'에 관한 알림만 받고 싶다고 하셨습니다. 제외할 내용은 '인증 코드'와 '보안 광고'입니다. 이 기준을 정확히 적용하기 위해 몇 가지를 확인하겠습니다.", "questions": [{"id": "q1", "field": "meaning", "kind": "multiple", "prompt": "'내 계정 비밀번호 변경'과 '새 로그인' 알림을 구체적으로 어떤 문구나 상황으로 판단할지 선택해 주세요. 예를 들어, '비밀번호가 변경되었습니다', '새 기기에서 로그인됨' 등 구체적인 표현을 알려주시면 도움이 됩니다.", "reason": "중요 알림의 의미를 명확히 해야 정확한 필터링이 가능합니다.", "options": [{"id": "o1", "label": "비밀번호 변경 알림", "candidate_id": null}, {"id": "o2", "label": "새 로그인 알림", "candidate_id": null}]}], "unresolved": [], "normalized_instruction": null, "referenced_candidate_ids": [], "change_kind": "new", "change_summary": "Google Messages 앱에서 계정 비밀번호 변경 및 새 로그인 알림만 받고 인증 코드와 보안 광고는 제외하는 기준 설정 초안 작성 중"}, "metrics": [{"model": "gpt-4.1-mini-2025-04-14", "prompt_version": "v3", "request_bytes": 13617, "latency_ms": 2962.84, "input_tokens": 1791, "output_tokens": 273, "cached_tokens": 0, "estimated_usd": 0.0011532, "error": null, "api_attempts": 1}]}}
- {"id": "pilot-021", "instruction": "Gmail 알림 중 학생에게 준비물을 가져오라고 명시한 학교 안내만 알려줘. 물품 구매 광고와 준비물이 없다는 안내는 제외해.", "expected": "ready", "actual": {"id": "pilot-021", "valid": false, "result": null, "error": "missing_options", "metrics": [{"model": "gpt-4.1-mini-2025-04-14", "prompt_version": "v3", "request_bytes": 13735, "latency_ms": 2174.09, "input_tokens": 1796, "output_tokens": 185, "cached_tokens": 0, "estimated_usd": 0.0010144000000000002, "error": "missing_options", "api_attempts": 1}]}}
- {"id": "pilot-023", "instruction": "Slack에서 내 이름인 서윤에게 검토나 승인을 직접 요청한 메시지만 알려줘. 다른 사람에게만 요청한 것과 이미 완료된 건은 제외해.", "expected": "ready", "actual": {"id": "pilot-023", "valid": true, "result": {"status": "needs_input", "message": "'서윤'이라는 이름이 사용자의 이름인지 확인이 필요합니다. 정확한 이름이나 호칭을 알려주세요.", "questions": [{"id": "q1", "field": "identity", "kind": "text", "prompt": "알림 대상이 되는 사용자의 정확한 이름이나 호칭을 입력해 주세요.", "reason": "사용자 이름이 명확해야 해당 이름이 포함된 메시지를 정확히 필터링할 수 있습니다.", "options": []}], "unresolved": ["사용자 이름 정보 부족"], "normalized_instruction": null, "referenced_candidate_ids": [], "change_kind": "new", "change_summary": ""}, "metrics": [{"model": "gpt-4.1-mini-2025-04-14", "prompt_version": "v3", "request_bytes": 13754, "latency_ms": 1745.83, "input_tokens": 1798, "output_tokens": 131, "cached_tokens": 0, "estimated_usd": 0.0009288, "error": null, "api_attempts": 1}]}}
- {"id": "pilot-026", "instruction": "Amazon Shopping에서 배송 실패로 내가 재배송 날짜나 수령 방법을 선택해야 한다고 명시한 것만 알려줘. 일반 배송 현황과 처리 완료는 제외해.", "expected": "ready", "actual": {"id": "pilot-026", "valid": true, "result": {"status": "needs_input", "message": "Amazon Shopping 앱에서 배송 실패 알림 중 재배송 날짜나 수령 방법 선택이 명시된 알림만 알려주고 싶으신데, '재배송 날짜나 수령 방법 선택'의 구체적인 의미나 예시를 알려주실 수 있나요? 예를 들어, 어떤 문구나 상황을 중요하게 여기시는지요?", "questions": [{"id": "q1", "field": "meaning", "kind": "multiple", "prompt": "'재배송 날짜나 수령 방법 선택'이 포함된 알림의 구체적인 의미를 선택해 주세요.", "reason": "사용자가 원하는 알림의 중요 기준을 명확히 하여 정확한 필터링을 위해 필요합니다.", "options": [{"id": "option1", "label": "재배송 날짜를 직접 선택해야 하는 알림", "candidate_id": null}, {"id": "option2", "label": "수령 방법(예: 직접 수령, 택배 재배송 등)을 선택해야 하는 알림", "candidate_id": null}, {"id": "option3", "label": "배송 실패 사실만 알리는 알림", "candidate_id": null}, {"id": "option4", "label": "기타 (직접 입력)", "candidate_id": null}]}], "unresolved": [], "normalized_instruction": null, "referenced_candidate_ids": [], "change_kind": "new", "change_summary": ""}, "metrics": [{"model": "gpt-4.1-mini-2025-04-14", "prompt_version": "v3", "request_bytes": 13765, "latency_ms": 2962.78, "input_tokens": 1799, "output_tokens": 284, "cached_tokens": 0, "estimated_usd": 0.001174, "error": null, "api_attempts": 1}]}}
- {"id": "pilot-027", "instruction": "Outlook에서 발신자 표시가 하린이고 내게 회신을 요청한 메일만 알려줘. 회신이 필요 없다는 메일은 제외해.", "expected": "ready", "actual": {"id": "pilot-027", "valid": true, "result": {"status": "needs_input", "message": "발신자 '하린'이 Outlook에서 관찰된 이름인지 확인이 필요합니다. 또한 '회신을 요청한 메일'의 구체적인 기준을 명확히 해야 합니다.", "questions": [{"id": "q1", "field": "identity", "kind": "text", "prompt": "발신자 '하린'의 이름이나 호칭을 정확히 알려주세요.", "reason": "발신자 이름이 명확하지 않아 정확한 알림 설정을 위해 필요합니다.", "options": []}, {"id": "q2", "field": "meaning", "kind": "single", "prompt": "'회신을 요청한 메일'의 기준을 어떻게 정의하시겠습니까? 예: 메일 본문에 '회신 요청' 문구 포함, 특정 플래그 설정 등", "reason": "회신 요청 여부를 판단하는 기준이 필요합니다.", "options": [{"id": "opt1", "label": "메일 본문에 '회신 요청' 문구 포함", "candidate_id": null}, {"id": "opt2", "label": "특정 플래그(예: 중요 표시) 설정", "candidate_id": null}, {"id": "opt3", "label": "기타 (직접 입력)", "candidate_id": null}]}], "unresolved": [], "normalized_instruction": null, "referenced_candidate_ids": [], "change_kind": "new", "change_summary": "발신자 이름과 회신 요청 기준을 명확히 하기 위한 질문"}, "metrics": [{"model": "gpt-4.1-mini-2025-04-14", "prompt_version": "v3", "request_bytes": 13692, "latency_ms": 2750.3, "input_tokens": 1797, "output_tokens": 297, "cached_tokens": 0, "estimated_usd": 0.001194, "error": null, "api_attempts": 1}]}}
- {"id": "pilot-028", "instruction": "Gmail 알림 중 내 계정의 비밀번호가 변경됐거나 새 기기에서 로그인했다고 명시한 안내만 알려줘. 인증번호와 보안 상품 광고는 제외해.", "expected": "ready", "actual": {"id": "pilot-028", "valid": true, "result": {"status": "needs_input", "message": "Gmail 알림 중에서 '내 계정'을 특정하기 위해 사용자 이름이나 호칭이 필요합니다. 알려주실 수 있나요?", "questions": [{"id": "q1", "field": "identity", "kind": "text", "prompt": "내 계정을 특정할 이름이나 호칭을 입력해 주세요.", "reason": "비밀번호 변경이나 새 기기 로그인 알림에서 내 계정을 정확히 식별하기 위해 필요합니다.", "options": []}], "unresolved": [], "normalized_instruction": null, "referenced_candidate_ids": [], "change_kind": "new", "change_summary": ""}, "metrics": [{"model": "gpt-4.1-mini-2025-04-14", "prompt_version": "v3", "request_bytes": 13766, "latency_ms": 1825.94, "input_tokens": 1801, "output_tokens": 128, "cached_tokens": 0, "estimated_usd": 0.0009252, "error": null, "api_attempts": 1}]}}
- {"id": "pilot-031", "instruction": "Gmail school notices 중 내게 permission form 서명이나 제출을 요청하면 알려줘. Submission complete와 단순 newsletter는 제외해.", "expected": "ready", "actual": {"id": "pilot-031", "valid": true, "result": {"status": "needs_input", "message": "'permission form 서명이나 제출 요청'의 구체적인 의미를 명확히 하고 싶습니다. 어떤 표현이나 키워드를 기준으로 알림을 구분할지 알려주세요.", "questions": [{"id": "q1", "field": "meaning", "kind": "multiple", "prompt": "'permission form 서명이나 제출 요청'에서 어떤 상황이나 표현을 중요한 알림으로 간주할까요?", "reason": "중요한 알림의 구체적 의미를 명확히 해야 정확한 알림 설정이 가능합니다.", "options": [{"id": "o1", "label": "서명 요청 메시지 포함", "candidate_id": null}, {"id": "o2", "label": "제출 요청 메시지 포함", "candidate_id": null}, {"id": "o3", "label": "특정 키워드(예: '서명', '제출') 포함", "candidate_id": null}, {"id": "o4", "label": "기타 (직접 입력)", "candidate_id": null}]}], "unresolved": ["'permission form 서명이나 제출 요청'의 구체적 의미"], "normalized_instruction": null, "referenced_candidate_ids": [], "change_kind": "new", "change_summary": ""}, "metrics": [{"model": "gpt-4.1-mini-2025-04-14", "prompt_version": "v3", "request_bytes": 13651, "latency_ms": 2751.49, "input_tokens": 1790, "output_tokens": 250, "cached_tokens": 0, "estimated_usd": 0.001116, "error": null, "api_attempts": 1}]}}
- {"id": "pilot-032", "instruction": "Instagram에서 Nori가 보낸 direct message만 알려줘. Likes, comments, 다른 사람 DM은 제외해.", "expected": "ready", "actual": {"id": "pilot-032", "valid": true, "result": {"status": "needs_input", "message": "Instagram에서 Nori가 보낸 direct message만 알려주는 설정을 위해, 'Nori'가 누구인지 알려주세요. 사용자 이름이나 호칭을 입력해 주세요.", "questions": [{"id": "q1", "field": "identity", "kind": "text", "prompt": "Nori의 정확한 이름이나 호칭을 입력해 주세요.", "reason": "발신자 'Nori'를 정확히 식별하기 위해 필요합니다.", "options": []}], "unresolved": [], "normalized_instruction": null, "referenced_candidate_ids": [], "change_kind": "new", "change_summary": ""}, "metrics": [{"model": "gpt-4.1-mini-2025-04-14", "prompt_version": "v3", "request_bytes": 13588, "latency_ms": 1769.41, "input_tokens": 1785, "output_tokens": 126, "cached_tokens": 0, "estimated_usd": 0.0009156, "error": null, "api_attempts": 1}]}}
- {"id": "pilot-037", "instruction": "내 동호회 단톡방에서만 약속 변경을 알려줘. 어떤 방인지는 아직 지정하지 않았어.", "expected": "waiting", "actual": {"id": "pilot-037", "valid": false, "result": null, "error": "missing_options", "metrics": [{"model": "gpt-4.1-mini-2025-04-14", "prompt_version": "v3", "request_bytes": 13640, "latency_ms": 1836.93, "input_tokens": 1787, "output_tokens": 159, "cached_tokens": 0, "estimated_usd": 0.0009692, "error": "missing_options", "api_attempts": 1}]}}

## 제한

- 샘플 수가 작고 인위적으로 구성한 첫 공개 기준선이다. 실사용 정확도나 출시 승인 수치가 아니다.
- 같은 지시에 속한 12알림은 서로 상관되어 있다. 언어별 사례도 동일 내용 번역쌍이 아니므로 언어 자체의 우열로 해석하지 않는다.
- gold 정답/normalized 오답 차이는 정리 오류 후보이며, 한 번의 Jev 판단 차이와 한국어 변환 영향도 있어 원인을 확정하지 않는다.
- 현재 프롬프트는 한국어 응답을 요구한다. 영어 입력 이해는 검사하지만 영어 질문 UX는 검증하지 않는다.
- 앱 선정은 docs/RELIABILITY_SIMULATION_PLAN.md의 사용통계 참고. 알림 발생빈도 가중 표본이 아니다.
- 소스·프롬프트·정답 변경 시 새로운 버전에서 실행한다. 이번 실패를 수정한 뒤 같은 자료 재시험은 회귀검사로만 보고한다.
- 오류/미실행은 제외하여 성공률을 높이지 않으며 API 비용 확인 불가 호출은 별도 기록한다.
- 알려진 실패를 분석하기 위한 실험이며, 합격한 경우에도 정책 JSON 변환 검증과 독립 비공개 평가가 남는다.

가격/계약 확인: [OpenAI 모델](https://developers.openai.com/api/docs/models/gpt-4.1-mini), [Jev 모델](https://docs.typesafe.ai/models), [Jev API](https://docs.typesafe.ai/api).
