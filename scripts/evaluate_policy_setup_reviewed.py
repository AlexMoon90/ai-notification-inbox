"""Frozen multistep regression of code-owned UI and source-aware final review."""
import json
import time
from pathlib import Path
import evaluate_policy_setup_v4 as exp
import policy_setup_reliable as client

OUT = client.ROOT / 'evaluations/policy-setup-reviewed'
OUT.mkdir(exist_ok=True)
exp.setup = client
last_call = [0.0]


def record_step(session, key, outputs):
    before = len(session['metrics'])
    timings = []
    def capture(payload, actual_key):
        delay = max(0, last_call[0] + 8 - time.monotonic())
        if delay:
            time.sleep(delay)
        last_call[0] = time.monotonic()
        start = time.monotonic()
        try:
            raw = client.call_openai(payload, actual_key)
            outputs.append(json.loads(client.base.mask(json.dumps(raw, ensure_ascii=False))))
            return raw
        finally:
            timings.append({'latency_ms': round((time.monotonic()-start)*1000, 2),
                            'rate_wait_ms': round(delay*1000, 2),
                            'request_bytes': len(json.dumps(payload).encode())})
    try:
        return client.step(session, key, capture)
    finally:
        for metric, timing in zip(session['metrics'][before:], timings):
            metric.update(timing)


exp.record_step = record_step


def main():
    source = client.ROOT / 'evaluations/policy-setup-v4'
    raw = (source / 'multiturn.json').read_bytes()
    assert exp.pilot.digest(raw) == (source / 'review.sha256.txt').read_text().strip()
    cases = json.loads(raw)
    config = {'dataset_sha256': exp.pilot.digest(raw), 'model': client.MODEL,
              'prompt': client.engine.PROMPT, 'review_prompt': client.REVIEW,
              'source_sha256': {name: exp.pilot.digest((client.ROOT/'scripts'/name).read_bytes()) for name in
                               ('policy_setup_reliable.py', 'policy_setup_v5.py', 'policy_setup_v4.py',
                                'policy_setup.py', 'evaluate_policy_setup_reviewed.py', 'evaluate_policy_setup_v4.py')},
              'note': 'Same exposed10 cases are regressions, not a new blind set. No threshold/expected label changes.'}
    path = OUT / 'manifest.json'
    if path.exists():
        assert json.loads(path.read_text()) == config
    else:
        exp.pilot.write_json(path, config)
    (OUT/'multiturn.json').write_bytes(raw)
    records = exp.pilot.read_records(OUT / 'multiturn-results.jsonl')
    done = {r['id'] for r in records}
    key = client.load_key()
    with (OUT/'multiturn-results.jsonl').open('a') as output:
        for case in cases:
            if case['id'] in done:
                continue
            record = exp.multiturn(case, key)
            records.append(record)
            output.write(json.dumps(record, ensure_ascii=False)+'\n')
            output.flush()
            print(case['id'], 'checks_passed='+str(record['valid']), record.get('error',''), flush=True)
            if record.get('error', '').startswith(('http_429','http_401','http_403','network_or_timeout')):
                break
    summary = {'completed': len(records), 'passed_automated_checks': sum(r['valid'] for r in records),
               'initial_status_correct': sum(r['checks'].get('initial_status',False) for r in records),
               'final_status_correct': sum(r['checks'].get('final_status',False) for r in records),
               'usage': exp.pilot.usage_summary(records,'openai'),
               'caveat': 'Exact-word checks are not semantic equivalence. Requires independent review and downstream notification probes.'}
    exp.pilot.write_json(OUT/'summary.json', summary)
    print(json.dumps(summary,ensure_ascii=False))


if __name__ == '__main__':
    main()
