"""Synthetic-only Luna low comparison. Never changes the default client."""
import hashlib
import json
import time
from pathlib import Path
import evaluate_policy_setup_v4 as exp
import policy_setup_reliable as client

OUT = client.ROOT / 'evaluations/policy-setup-luna-low'
MODEL = 'gpt-5.6-luna'
exp.setup = client
last_call = 0.0


def adapt(payload):
    payload = dict(payload, model=MODEL, reasoning={'effort': 'low'})
    payload.pop('temperature', None)
    return payload


def record_step(session, key, outputs):
    before = len(session['metrics'])
    calls = []
    def capture(payload, actual_key):
        global last_call
        payload = adapt(payload)
        delay = max(0, last_call + 2 - time.monotonic())
        if delay:
            time.sleep(delay)
        last_call = time.monotonic()
        info = {'model': MODEL, 'reasoning_effort': 'low', 'request_bytes': len(json.dumps(payload).encode()),
                'request_sha256': hashlib.sha256(json.dumps(payload, sort_keys=True).encode()).hexdigest(),
                'max_output_tokens': payload['max_output_tokens'], 'rate_wait_ms': round(delay*1000,2)}
        start = time.monotonic()
        try:
            raw = client.call_openai(payload, actual_key)
            info['response_model'] = raw.get('model')
            info['reasoning_tokens'] = ((raw.get('usage') or {}).get('output_tokens_details') or {}).get('reasoning_tokens')
            outputs.append(json.loads(client.base.mask(json.dumps(raw, ensure_ascii=False))))
            return raw
        finally:
            info['latency_ms'] = round((time.monotonic()-start)*1000,2)
            calls.append(info)
    try:
        return client.step(session, key, capture)
    finally:
        for metric, info in zip(session['metrics'][before:], calls):
            metric.update(info)
            it, ot, cached = (metric[k] for k in ('input_tokens','output_tokens','cached_tokens'))
            metric['estimated_usd'] = ((it-cached)*.2+cached*.02+ot*1.2)/1e6 if all(type(v) is int for v in (it,ot,cached)) else None


exp.record_step = record_step


def main():
    OUT.mkdir(exist_ok=True)
    datasets = [client.ROOT/'evaluations/reliability-pilot-v1/dataset.json', client.ROOT/'evaluations/policy-setup-v4/multiturn.json']
    manifest = {'model': MODEL, 'reasoning': {'effort':'low'}, 'temperature':'omitted', 'max_output_tokens':2200,
                'store':False, 'strict_json_schema':True, 'attempts_per_request':1,
                'pricing_per_million':{'input':.2,'cached_input':.02,'output':1.2},
                'sources':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in datasets + [Path(__file__),Path('scripts/policy_setup_reliable.py'),Path('scripts/policy_setup_v5.py'),Path('scripts/policy_setup_v4.py'),Path('scripts/policy_setup.py')]},
                'note':'Same exposed regression fixtures; same prompts and source review. No default model replacement. Output limit retained; incomplete outputs count as failures.'}
    path=OUT/'manifest.json'
    if path.exists():
        assert json.loads(path.read_text())==manifest
    else:
        exp.pilot.write_json(path,manifest)
    key=client.load_key()
    stopped=False
    groups=[]
    for source,name,fn in zip(datasets,['settings','multiturn-results'],[exp.clarify,exp.multiturn]):
        rows=json.loads(source.read_text())
        records=exp.pilot.read_records(OUT/(name+'.jsonl'))
        done={r['id'] for r in records}
        with (OUT/(name+'.jsonl')).open('a') as f:
            for row in rows:
                if stopped or row['id'] in done:
                    continue
                record=fn(row,key)
                if name=='settings':
                    record['expected_status']=row['expected_status']
                    record['status_correct']=bool(record['valid'] and record['result']['status']==row['expected_status'])
                records.append(record)
                f.write(json.dumps(record,ensure_ascii=False)+'\n');f.flush()
                print(row['id'], record.get('status_correct',record['valid']),record.get('error',''),flush=True)
                if record.get('error','').startswith(('http_','network_or_timeout')):
                    stopped=True
        groups.append(records)
    settings,multi=groups
    summary={'settings':{'completed':len(settings),'valid':sum(r['valid'] for r in settings),'status_correct':sum(r.get('status_correct',False) for r in settings)},
             'multiturn':{'completed':len(multi),'automated_pass':sum(r['valid'] for r in multi),'initial_status_correct':sum(r['checks'].get('initial_status',False) for r in multi),'final_status_correct':sum(r['checks'].get('final_status',False) for r in multi)},
             'usage':exp.pilot.usage_summary(settings+multi,'openai'),'stopped_on_service_error':stopped}
    exp.pilot.write_json(OUT/'summary.json',summary)
    print(json.dumps(summary,ensure_ascii=False),flush=True)

if __name__=='__main__':
    main()
