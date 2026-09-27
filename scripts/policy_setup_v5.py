"""V4 clarification contract with a stronger settings-only model.

No per-notification LLM calls. Keep the less costly v4 experiment as a baseline.
"""
import policy_setup_v4 as v4

ROOT, DATA, SCHEMA = v4.ROOT, v4.DATA, v4.SCHEMA
MODEL = 'gpt-4.1-2025-04-14'
PROMPT_VERSION = 'v5'
PROMPT, CAPABILITIES = v4.PROMPT, v4.CAPABILITIES
base = v4.base
SetupError, NoRedirect = v4.SetupError, v4.NoRedirect
new_session, answer, confirm = v4.new_session, v4.answer, v4.confirm
refresh_candidates, reset_target_selection = v4.refresh_candidates, v4.reset_target_selection
save_session, load_key, validate_result = v4.save_session, v4.load_key, v4.validate_result
call_openai = v4.call_openai


def build_request(session):
    payload = v4.build_request(session)
    payload['model'] = MODEL
    return payload


def step(session, key, transport=call_openai):
    before = len(session['metrics'])
    def with_model(payload, actual_key):
        payload = dict(payload, model=MODEL)
        return transport(payload, actual_key)
    try:
        return v4.step(session, key, with_model)
    finally:
        if len(session['metrics']) > before:
            metric = session['metrics'][-1]
            metric.update(model=MODEL, prompt_version=PROMPT_VERSION)
            it, ot, cached = (metric[k] for k in ('input_tokens', 'output_tokens', 'cached_tokens'))
            metric['estimated_usd'] = (((it-cached)*2 + cached*.5 + ot*8) / 1e6
                                       if all(type(v) is int for v in (it, ot, cached)) else None)


if __name__ == '__main__':
    import argparse
    import json
    from pathlib import Path
    parser = argparse.ArgumentParser(description='v5 지시 정리 초안 — 실제 알림 정책 적용 없음')
    parser.add_argument('--session', type=Path, required=True)
    parser.add_argument('--instruction')
    parser.add_argument('--question-id')
    parser.add_argument('--answer')
    parser.add_argument('--select', nargs='+')
    parser.add_argument('--candidates', type=Path)
    args = parser.parse_args()
    if args.session.exists():
        session = json.loads(args.session.read_text())
        if args.instruction:
            parser.error('Use a new session path for a new instruction.')
    elif args.instruction:
        session = new_session(args.instruction, [])
    else:
        parser.error('--instruction is required for a new session.')
    if args.candidates:
        refresh_candidates(session, json.loads(args.candidates.read_text()))
    if args.question_id:
        answer(session, args.question_id, text=args.answer, option_ids=args.select)
    try:
        result = step(session, load_key())
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except SetupError as exc:
        print('초안 처리 실패:', str(exc))
    finally:
        save_session(args.session, session)
