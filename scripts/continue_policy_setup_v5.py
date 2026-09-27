"""Rate-limited continuation. Original failed records are never overwritten."""
import json
from pathlib import Path
import threading
import time
import evaluate_policy_setup_v5 as wrapper

exp = wrapper.experiment
pilot, setup, OUT = exp.pilot, exp.setup, exp.OUT
original_step = exp.record_step
lock = threading.Lock()
last_start = [0.0]


def paced_step(session, key, outputs):
    # Wait outside step so provider latency excludes intentional pacing.
    with lock:
        delay = max(0, last_start[0] + 8 - time.monotonic())
        if delay:
            time.sleep(delay)
        last_start[0] = time.monotonic()
        return original_step(session, key, outputs)


exp.record_step = paced_step


def main():
    rows = json.loads((exp.BASELINE / 'dataset.json').read_text())
    cases = json.loads((OUT / 'multiturn.json').read_text())
    exp.freeze(rows, cases)
    note = {'source_sha256': pilot.digest(Path(__file__).read_bytes()), 'minimum_start_interval_seconds': 8,
            'retry_condition': 'only http_429:rate_limit_exceeded, one extra attempt; original record retained',
            'configuration_changes': 'none; only evaluation transport pacing'}
    path = OUT / 'continuation-manifest.json'
    if path.exists():
        assert json.loads(path.read_text()) == note
    else:
        pilot.write_json(path, note)
    settings = pilot.read_records(OUT / 'settings.jsonl')
    retries = pilot.read_records(OUT / 'service-retries.jsonl')
    multi = pilot.read_records(OUT / 'multiturn-results.jsonl')
    records = pilot.read_records(OUT / 'responses.jsonl')
    key = setup.load_key()
    if not pilot.execute(rows, exp.clarify, key, OUT / 'settings.jsonl', settings):
        return
    failed = {r['id'] for r in settings if r.get('error') == 'http_429:rate_limit_exceeded'}
    if not pilot.execute([r for r in rows if r['id'] in failed], exp.clarify, key, OUT / 'service-retries.jsonl', retries):
        return
    effective = {r['id']: r for r in settings}
    effective.update({r['id']: r for r in retries})
    effective = list(effective.values())
    (OUT / 'effective-settings.jsonl').write_text(''.join(json.dumps(r, ensure_ascii=False) + '\n' for r in effective))
    if not pilot.execute(cases, exp.multiturn, key, OUT / 'multiturn-results.jsonl', multi):
        return
    jobs = [j for j in pilot.make_jobs(rows, effective) if j['id'].endswith('/normalized')]
    pilot.execute(jobs, pilot.judge, pilot.load_jev_key(), OUT / 'responses.jsonl', records)
    exp.report(rows, effective, records, multi)
    summary = json.loads((OUT / 'summary.json').read_text())
    summary['initial_setup'] = {'attempted': len(settings), 'valid': sum(r['valid'] for r in settings),
                                'rate_limit_failures': len(failed)}
    summary['settings_usage_including_failed_attempts'] = pilot.usage_summary(settings + retries, 'openai')
    pilot.write_json(OUT / 'summary.json', summary)
    report = OUT / 'REPORT.md'
    report.write_text(report.read_text().replace('v4', 'v5') + '\n\n429 최초 실패3회는 settings.jsonl에 유지. effective-settings.jsonl은 별도 재시도 결과를 합친 회복 후 결과다. 전체 시도 비용은 summary의 settings_usage_including_failed_attempts 참조.\n')
    print(json.dumps({k: summary[k] for k in ('setup', 'multiturn', 'initial_setup', 'settings_usage_including_failed_attempts')}, ensure_ascii=False))


if __name__ == '__main__':
    main()
