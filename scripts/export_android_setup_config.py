"""Generate authorized debug-only assets; never print credentials."""
import json
import os
from pathlib import Path
import policy_setup_luna_v2 as luna

out = luna.ROOT / 'app/build/generated/standaloneDebugAssets'
out.mkdir(parents=True, exist_ok=True)
config = {'model': luna.MODEL, 'prompt': luna.previous.engine.PROMPT + '\n' + luna.CONTRACT,
          'review_prompt': luna.previous.engine.PROMPT + '\n' + luna.previous.REVIEW + '\n' + luna.CONTRACT,
          'schema': luna.base.SCHEMA, 'capabilities': luna.previous.engine.CAPABILITIES}
(out / 'setup-config.json').write_text(json.dumps(config, ensure_ascii=False))
with os.fdopen(os.open(out / 'test-openai-key', os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), 'w') as f:
    f.write(luna.load_key())
os.chmod(out / 'test-openai-key', 0o600)
from evaluate_reliability_pilot import load_jev_key
with os.fdopen(os.open(out / 'test-jev-key', os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), 'w') as f:
    f.write(load_jev_key())
os.chmod(out / 'test-jev-key', 0o600)
print('Debug standalone assets generated (credential value hidden).')
