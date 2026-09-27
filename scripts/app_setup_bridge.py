"""USB-only development bridge. No credentials in APK; no notification classification.

Run with --provision-device after installing the debug APK. Request/session bodies
are never logged or persisted on the host. Session storage belongs to Android.
"""
import argparse
import hmac
import json
import os
import time
import secrets
import subprocess
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import policy_setup_luna_v2 as engine

PORT = 8766
ADB = '/Users/alexmoon/Library/Android/sdk/platform-tools/adb'


def process(body, key, transport=engine.call_openai):
    action = body.get('action')
    if action == 'create':
        session = engine.new_session(body['instruction'], body.get('candidates', []))
    else:
        session = body['session']
        # Check the trusted engine's public input constraints before processing.
        engine.new_session(session['instruction'], session['candidates'], session['current_instruction'])
        if action == 'answer':
            for answer in body.get('answers', []):
                engine.answer(session, answer['id'], option_ids=answer.get('options'), text=answer.get('text'))
        elif action == 'refresh':
            engine.refresh_candidates(session, body.get('candidates', []))
        elif action == 'confirm':
            engine.confirm(session)
            return {'session': session}
        elif action != 'retry':
            raise ValueError('Unknown action')
    try:
        engine.step(session, key, transport)
        return {'session': session}
    except engine.SetupError:
        # Persist attempted answers/metrics, but never display an old ready result.
        session['result'] = None
        session['confirmed_instruction'] = None
        return {'session': session, 'error': '기준 정리에 실패했습니다. 입력은 보관했습니다. 다시 시도해 주세요.'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--provision-device', action='store_true')
    args = parser.parse_args()
    key = engine.load_key()
    token = secrets.token_urlsafe(32)
    lock = threading.Lock()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_POST(self):
            if self.path != '/setup' or self.headers.get('Origin') or not hmac.compare_digest(
                    self.headers.get('Authorization', ''), 'Bearer ' + token):
                self.send_error(403)
                return
            try:
                size = int(self.headers.get('Content-Length', '0'))
                if not 0 < size <= 1_000_000:
                    raise ValueError('Invalid body size')
                body = json.loads(self.rfile.read(size))
                with lock:
                    before = len((body.get('session') or {}).get('metrics', []))
                    result = process(body, key)
                    metrics = (result.get('session') or {}).get('metrics', [])[before:]
                    usage_dir = engine.ROOT / '.local'
                    usage_dir.mkdir(exist_ok=True)
                    allowed = {'model', 'purpose', 'input_tokens', 'output_tokens', 'cached_tokens',
                               'latency_ms', 'estimated_usd', 'reasoning_effort'}
                    with os.fdopen(os.open(usage_dir / 'setup-bridge-metrics.jsonl',
                                          os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600), 'a') as out:
                        for metric in metrics:
                            safe = {k: v for k, v in metric.items() if k in allowed}
                            safe.update(recorded_at=time.time(), request_bytes=size)
                            out.write(json.dumps(safe) + '\n')
                status = 200
            except Exception:
                # Do not expose upstream errors, prompts, keys, or request data.
                result, status = {'error': '입력을 처리하지 못했습니다. 저장된 초안에서 다시 시도해 주세요.'}, 400
            data = json.dumps(result, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            try:
                self.wfile.write(data)
            except (BrokenPipeError, ConnectionResetError):
                pass

    server = ThreadingHTTPServer(('127.0.0.1', PORT), Handler)
    if args.provision_device:
        subprocess.run([ADB, 'reverse', f'tcp:{PORT}', f'tcp:{PORT}'], check=True, capture_output=True)
        subprocess.run([ADB, 'shell', 'run-as', 'com.ainotification.inbox', 'sh', '-c',
                        "'mkdir -p files; umask 077; cat > files/setup-bridge-token; chmod 600 files/setup-bridge-token'"],
                       input=token.encode(), check=True, capture_output=True)
    print('USB setup bridge ready; model=gpt-5.6-luna, reasoning=low. No request logging.', flush=True)
    server.serve_forever()


if __name__ == '__main__':
    main()
