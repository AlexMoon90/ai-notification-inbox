"""Restore the last verified private debug assets. No network or credential logging."""
from pathlib import Path
import os, shutil, sys
root=Path(__file__).resolve().parents[1]
source=root/'recovery/private-debug-assets'
out=Path(sys.argv[1]) if len(sys.argv)>1 else root/'app/build/generated/standaloneDebugAssets'
required=['setup-config.json','test-openai-key','test-jev-key']
if not all((source/name).is_file() for name in required):
    raise SystemExit('Private debug assets missing; restore locally before building debug.')
out.mkdir(parents=True,exist_ok=True)
for name in required:
    shutil.copyfile(source/name,out/name)
    os.chmod(out/name,0o600)
print('Recovered debug assets copied; credential values hidden.')
