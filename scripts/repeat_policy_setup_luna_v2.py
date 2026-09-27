"""Two additional independent calls per original failure; retain all outcomes."""
import json
import evaluate_policy_setup_luna_v2 as run

def main():
    cases=json.loads((run.client.ROOT/'evaluations/policy-setup-v4/multiturn.json').read_text())
    rows=[dict(c,id=c['id']+'-repeat-'+str(i)) for i in (2,3) for c in cases if c['id'] in ('mt-007','mt-009')]
    path=run.OUT/'repeat-results.jsonl'
    records=run.exp.pilot.read_records(path)
    done={r['id'] for r in records}
    key=run.client.load_key()
    with path.open('a') as f:
        for row in rows:
            if row['id'] in done: continue
            record=run.exp.multiturn(row,key)
            f.write(json.dumps(record,ensure_ascii=False)+'\n');f.flush()
            records.append(record)
            print(row['id'],record['valid'],record.get('error',''),flush=True)
            if record.get('error','').startswith(('http_','network_or_timeout')): break
    run.exp.pilot.write_json(run.OUT/'repeat-summary.json',{'completed':len(records),'passed':sum(r['valid'] for r in records),'usage':run.exp.pilot.usage_summary(records,'openai')})

if __name__=='__main__':main()
