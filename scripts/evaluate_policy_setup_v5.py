"""Same frozen fixtures and prompts, settings model comparison. No gold leakage."""
import json
import evaluate_policy_setup_v4 as experiment
import policy_setup_v5 as setup

OUT = setup.ROOT / 'evaluations/policy-setup-v5'
OUT.mkdir(exist_ok=True)
for name in ('multiturn.json', 'review.sha256.txt'):
    raw = (experiment.OUT / name).read_bytes()
    target = OUT / name
    if target.exists():
        assert target.read_bytes() == raw
    else:
        target.write_bytes(raw)
experiment.OUT = OUT
experiment.setup = setup
experiment.pilot.OUT = OUT
experiment.pilot.setup = setup
original_freeze = experiment.freeze


def freeze(rows, cases):
    original_freeze(rows, cases)
    path = OUT / 'model-comparison-sources.json'
    config = {'model': setup.MODEL, 'source_sha256': {
        name: experiment.pilot.digest((setup.ROOT / 'scripts' / name).read_bytes())
        for name in ('policy_setup_v5.py', 'evaluate_policy_setup_v5.py')},
        'note': 'Only settings model changed from v4. v4 failed cases are preserved. All fixtures reused, not blind.'}
    if path.exists():
        assert json.loads(path.read_text()) == config
    else:
        experiment.pilot.write_json(path, config)


experiment.freeze = freeze
if __name__ == '__main__':
    experiment.main()
    report = OUT / 'REPORT.md'
    report.write_text(report.read_text().replace('v4', 'v5'))
