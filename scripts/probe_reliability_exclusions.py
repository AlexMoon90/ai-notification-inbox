"""Post-hoc counterexamples, separate from the frozen pilot baseline.

Tests whether dropping 'only' from an exclusion affects concurrent changes.
Not a blind evaluation. Does not change prompts or tune thresholds.
"""
import argparse
import json
from pathlib import Path
import evaluate_reliability_pilot as pilot

OUT = pilot.OUT / 'exclusion-probe'
CASES = [
    {'id': 'time-and-description', 'setting_id': 'pilot-017', 'app': 'Google Calendar',
     'title': 'Design review updated',
     'text': 'The event start time changed from 10:00 to 11:00. The description was also updated.', 'expected': True},
    {'id': 'cancel-and-description', 'setting_id': 'pilot-017', 'app': 'Google Calendar',
     'title': 'Event cancelled',
     'text': 'Your design review has been cancelled. Its description was also edited to explain the cancellation.', 'expected': True},
    {'id': 'time-and-place', 'setting_id': 'pilot-029', 'app': 'Google Calendar',
     'title': '일정 변경 확정',
     'text': '회의 시작 시간이 오후 2시에서 오후 3시로 변경됐습니다. 장소도 1층에서 2층으로 변경됐습니다.', 'expected': True},
    {'id': 'cancel-and-place', 'setting_id': 'pilot-029', 'app': 'Google Calendar',
     'title': '일정 취소 확정',
     'text': '장소가 2층으로 변경됐던 내일 회의는 최종 취소되었습니다.', 'expected': True},
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--run', action='store_true')
    args = parser.parse_args()
    rows = json.loads((pilot.OUT / 'dataset.json').read_text())
    pilot.freeze(rows)
    source = {r['id']: r for r in rows}
    settings = {r['id']: r for r in pilot.read_records(pilot.OUT / 'settings.jsonl')}
    jobs = []
    for case in CASES:
        sid = case['setting_id']
        for variant, policy in [('gold', source[sid]['gold_instruction']),
                                ('normalized', settings[sid]['result']['normalized_instruction'])]:
            jobs.append({'id': f'{case["id"]}/{variant}', 'state': pilot.notification_state(policy, case)})
    OUT.mkdir(exist_ok=True)
    config = {'purpose': 'post-hoc exclusion-scope counterexample; not blind or part of baseline',
              'cases': CASES, 'jobs': jobs, 'questions': pilot.QUESTIONS,
              'source_sha256': pilot.digest(Path(__file__).read_bytes()),
              'settings_sha256': pilot.digest((pilot.OUT / 'settings.jsonl').read_bytes())}
    manifest = OUT / 'manifest.json'
    if manifest.exists():
        assert json.loads(manifest.read_text()) == config
    else:
        pilot.write_json(manifest, config)
    records = pilot.read_records(OUT / 'responses.jsonl')
    if args.run:
        pilot.execute(jobs, pilot.judge, pilot.load_jev_key(), OUT / 'responses.jsonl', records)
    summary = {'usage': pilot.usage_summary(records, 'jev'),
               'results': [{'id': r['id'], 'valid': r['valid'], 'p': r.get('p'), 'expected': True,
                            'action': 'notify' if r.get('valid') and r['p'] >= .8 else 'retain_for_review'} for r in records]}
    pilot.write_json(OUT / 'summary.json', summary)
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == '__main__':
    main()
