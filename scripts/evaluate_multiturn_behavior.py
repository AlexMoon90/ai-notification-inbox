"""Independent synthetic notification checks after actual clarification answers."""
import json
from collections import Counter, defaultdict
from pathlib import Path
import evaluate_reliability_pilot as p

ROOT = p.ROOT / 'evaluations/policy-setup-v5'
RESULT_ROOT = p.ROOT / 'evaluations/policy-setup-reviewed'
OUT = RESULT_ROOT / 'multiturn-behavior'


def main():
    rows = json.loads((ROOT / 'multiturn-notifications.json').read_text())
    settings = {r['id']: r for r in p.read_records(RESULT_ROOT / 'multiturn-results.jsonl')}
    assert len(rows) == 36 and len(settings) == 10
    assert len({r['id'] for r in rows}) == 36
    assert sum(r['expected'] for r in rows) == 18
    jobs = []
    for row in rows:
        result = settings[row['setting_id']].get('result') or {}
        policies = {'gold': row['gold_instruction']}
        if result.get('status') == 'ready':
            policies['normalized'] = result['normalized_instruction']
        for variant, policy in policies.items():
            jobs.append({'id': row['id'] + '/' + variant, 'state': p.notification_state(policy, row['notification'])})
    OUT.mkdir(exist_ok=True)
    config = {'dataset_sha256': p.digest((ROOT / 'multiturn-notifications.json').read_bytes()),
              'settings_sha256': p.digest((RESULT_ROOT / 'multiturn-results.jsonl').read_bytes()),
              'source_sha256': p.digest(Path(__file__).read_bytes()), 'questions': p.QUESTIONS,
              'binary_threshold': .5, 'notify_threshold': .8,
              'review': 'Root checked all36 source-based golds before running; author did not read model outputs.',
              'limit': 'Conversation labels stand in for IDs in these synthetic title fields. No actual Android ID binding verified.'}
    path = OUT / 'manifest.json'
    if path.exists():
        assert json.loads(path.read_text()) == config
    else:
        p.write_json(path, config)
    records = p.read_records(OUT / 'responses.jsonl')
    p.execute(jobs, p.judge, p.load_jev_key(), OUT / 'responses.jsonl', records)
    index = {r['id']: r for r in records}
    groups = defaultdict(Counter)
    details = []
    for row in rows:
        for variant in ('gold', 'normalized'):
            record = index.get(row['id'] + '/' + variant, {})
            c = groups[variant]
            c['expected'] += 1
            c['positive'] += row['expected']
            c['valid'] += bool(record.get('valid'))
            if record.get('valid'):
                prob = record['p']
                c['correct_05'] += (prob >= .5) == row['expected']
                c['positive_notified'] += row['expected'] and prob >= .8
                c['negative_notified'] += not row['expected'] and prob >= .8
                details.append({'id': record['id'], 'expected': row['expected'], 'p': prob})
    summary = {'groups': dict(groups), 'usage': p.usage_summary(records, 'jev'), 'details': details}
    p.write_json(OUT / 'summary.json', summary)
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == '__main__':
    main()
