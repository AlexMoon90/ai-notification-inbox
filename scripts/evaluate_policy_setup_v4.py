"""V4 regression + independently authored multistep API tests; synthetic only."""
import argparse
import hashlib
import json
from pathlib import Path
import tempfile

import evaluate_reliability_pilot as pilot
import policy_setup_v4 as setup

OUT = setup.ROOT / 'evaluations/policy-setup-v4'
BASELINE = setup.ROOT / 'evaluations/reliability-pilot-v1'
pilot.OUT = OUT
pilot.setup = setup


def record_step(session, key, outputs):
    def capture(payload, actual_key):
        raw = setup.call_openai(payload, actual_key)
        # This evaluator accepts only the frozen synthetic fixtures; no production log.
        outputs.append(json.loads(setup.base.mask(json.dumps(raw, ensure_ascii=False))))
        return raw
    return setup.step(session, key, capture)


def clarify(row, key):
    session = setup.new_session(row['instruction'], [])
    record = {'id': row['id'], 'valid': False, 'result': None, 'synthetic_api_outputs': []}
    try:
        record['result'] = record_step(session, key, record['synthetic_api_outputs'])
        record['valid'] = True
    except setup.SetupError as exc:
        record['error'] = str(exc)
    record['normalization_mode'] = session.get('normalization_mode')
    record['metrics'] = session['metrics']
    return record


def multiturn(case, key):
    session = setup.new_session(case['instruction'], case['candidates'], case['current_instruction'])
    record = {'id': case['id'], 'valid': False, 'turns': [], 'synthetic_api_outputs': [], 'checks': {}}
    def step():
        result = record_step(session, key, record['synthetic_api_outputs'])
        record['turns'].append(json.loads(json.dumps(result)))
        return result
    try:
        result = step()
        record['checks']['initial_status'] = result['status'] == case['initial_status']
        if not record['checks']['initial_status']:
            raise setup.SetupError('unexpected_initial_status')
        if case['refreshed_candidates'] is not None:
            setup.refresh_candidates(session, case['refreshed_candidates'])
            result = step()
            if result['status'] != 'needs_input':
                raise setup.SetupError('refresh_did_not_offer_selection')
        if case['answer_text'] is not None or case['select_candidate_id'] is not None:
            fields = ['scope'] if case['select_candidate_id'] else case.get('answer_fields', [case['answer_field']])
            questions = result['questions']
            if len(questions) != 1 or questions[0]['field'] not in fields:
                raise setup.SetupError('unexpected_question_field_or_count')
            q = questions[0]
            if case['select_candidate_id'] is not None:
                options = [o['id'] for o in q['options'] if o['candidate_id'] == case['select_candidate_id']]
                if len(options) != 1:
                    raise setup.SetupError('missing_target_option')
                setup.answer(session, q['id'], option_ids=options)
            else:
                setup.answer(session, q['id'], text=case['answer_text'])
            # Explicit disk roundtrip exercises saved user answers on resume.
            with tempfile.TemporaryDirectory() as folder:
                path = Path(folder) / 'session.json'
                setup.save_session(path, session)
                restored = json.loads(path.read_text())
                assert restored == session
                session = restored
            result = step()
        text = result.get('normalized_instruction') or ''
        record['checks'].update(final_status=result['status'] == case['final_status'],
                                required_terms=all(t.casefold() in text.casefold() for t in case['required_terms']),
                                forbidden_terms=all(t.casefold() not in text.casefold() for t in case['forbidden_terms']),
                                selected_scope=not case['select_candidate_id'] or case['select_candidate_id'] in result['referenced_candidate_ids'])
        record['valid'] = all(record['checks'].values())
    except setup.SetupError as exc:
        record['error'] = str(exc)
    record['result'] = session['result']
    record['metrics'] = session['metrics']
    return record


def freeze(rows, cases):
    raw = (OUT / 'multiturn.json').read_bytes()
    assert pilot.digest(raw) == (OUT / 'review.sha256.txt').read_text().strip()
    source = (BASELINE / 'dataset.json').read_bytes()
    assert pilot.digest(source) == (BASELINE / 'review.sha256.txt').read_text().strip()
    config = {'regression_sha256': pilot.digest(source), 'multiturn_sha256': pilot.digest(raw),
              'model': setup.MODEL, 'prompt': setup.PROMPT, 'capabilities': setup.CAPABILITIES,
              'schema': setup.SCHEMA, 'jev_model': pilot.JEV_MODEL, 'questions': pilot.QUESTIONS,
              'binary_threshold': .5, 'notify_threshold': .8, 'max_attempts': 1,
              'source_sha256': {name: pilot.digest((setup.ROOT / 'scripts' / name).read_bytes()) for name in
                               ('policy_setup.py', 'policy_setup_v4.py', 'evaluate_policy_setup_v4.py', 'evaluate_reliability_pilot.py')}}
    path = OUT / 'manifest.json'
    if path.exists():
        assert json.loads(path.read_text()) == config, 'frozen experiment changed'
    else:
        pilot.write_json(path, config)


def report(rows, settings, records, multi):
    pilot.report(rows, settings, records)
    summary = json.loads((OUT / 'summary.json').read_text())
    summary['multiturn'] = {'completed': len(multi), 'passed_automated_checks': sum(r['valid'] for r in multi),
                            'expected': 10, 'usage': pilot.usage_summary(multi, 'openai')}
    summary['reference_baseline'] = str(BASELINE.relative_to(setup.ROOT))
    pilot.write_json(OUT / 'summary.json', summary)
    baseline = json.loads((BASELINE / 'summary.json').read_text())
    lines = ['# v4 회귀·다회차 평가', '',
             '기존40개는 실패 분석에 사용된 회귀 자료다. 신규10개 다회차는 독립 작성자가 사전에 작성·검토했다.',
             'Jev 원문 기준 경로는 이번에 재실행하지 않았다. 기존 결과는 이전 실행의 참고 기준이다.',
             '명확한 첫 지시는 원문 그대로 전달하고, 답변/기존 정책 변경이 있는 경우에만 모델이 합쳐 정리한다.', '',
             '|항목|v3|v4|', '|---|---:|---:|',
             f'|설정 상태 일치|{baseline["setup"]["all"]["status_correct"]}/40|{summary["setup"]["all"]["status_correct"]}/40|',
             f'|명확한 지시 진행 차단|{baseline["setup"]["all"]["ready_blocked"]}|{summary["setup"]["all"]["ready_blocked"]}|',
             f'|미완성을 ready로 오판|{baseline["setup"]["all"]["unsafe_ready"]}|{summary["setup"]["all"]["unsafe_ready"]}|',
             f'|모의 알림 도달(.8)|{baseline["classification"]["normalized"]["all"]["notified_positive"]}/160|{summary["classification"]["normalized"]["all"]["notified_positive"]}/160|',
             '', f'다회차 자동 상태·필수 어휘·후보 참조 검사: {sum(r["valid"] for r in multi)}/10. 어휘 검사는 의미 보존의 완전한 증명이 아니다.',
             '', '```json', json.dumps({k: summary[k] for k in ('openai', 'jev', 'multiturn')}, ensure_ascii=False, indent=2), '```',
             '', '정책 JSON 컴파일 및 Android 적용 미검증. 보류는 모의 알림 성공으로 세지 않는다.',
             '원시 모델 출력은 고정된 합성 평가에 한해 저장한다. 실제 사용자 원문을 제품 로그에 기록하지 않는다.']
    (OUT / 'REPORT.md').write_text('\n'.join(lines) + '\n')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--run', action='store_true')
    args = parser.parse_args()
    rows = json.loads((BASELINE / 'dataset.json').read_text())
    cases = json.loads((OUT / 'multiturn.json').read_text())
    pilot.validate_dataset(rows)
    assert len(cases) == len({c['id'] for c in cases}) == 10
    freeze(rows, cases)
    settings = pilot.read_records(OUT / 'settings.jsonl')
    records = pilot.read_records(OUT / 'responses.jsonl')
    multi = pilot.read_records(OUT / 'multiturn-results.jsonl')
    if args.run:
        key = setup.load_key()
        complete = pilot.execute(rows, clarify, key, OUT / 'settings.jsonl', settings)
        if complete:
            complete = pilot.execute(cases, multiturn, key, OUT / 'multiturn-results.jsonl', multi)
        if complete:
            jobs = [j for j in pilot.make_jobs(rows, settings) if j['id'].endswith('/normalized')]
            pilot.execute(jobs, pilot.judge, pilot.load_jev_key(), OUT / 'responses.jsonl', records)
    report(rows, settings, records, multi)


if __name__ == '__main__':
    main()
