"""Additional 10/30-message stress experiment, fixed before inspecting its results."""
import copy, hashlib, json, os, random
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta, timezone
import compare_jev_prompts as c
OUT=c.ROOT/'evaluations/jev/long-context-v1'
SELECTED=['dues-03','approval-03','english-03','reopened-04','meeting-05','correction-05']
NOISE=['오늘 날씨가 좋네요.','점심 잘 먹었습니다.','사진 분위기 좋네요.','저는 커피 마시고 있어요.','창밖이 맑아요.','음악이 좋네요.','간식 맛있어요.']

def jobs_and_rows():
 source={r['id']:r for r in [json.loads(l) for l in (c.ROOT/'evaluations/jev/timeline.v1.jsonl').read_text().splitlines()]}
 jobs=[]; rows=[]
 for case in SELECTED:
  for length in [10,30]:
   row=copy.deepcopy(source[case]); state=row['state']; history=state['recent_messages']
   while len(history)<length-1: history.append({'text':NOISE[(len(history)-1)%len(NOISE)]})
   start=datetime(2026,9,21,10,tzinfo=timezone(timedelta(hours=9)))
   for i,msg in enumerate(history): msg['timestamp']=(start+timedelta(minutes=i)).isoformat()
   state['notification']['timestamp']=(start+timedelta(minutes=length-1)).isoformat()
   row['id']=f'{case}-length{length}'; rows.append(row)
   for variant in c.PROMPTS:
    jobs.append({'id':f'long/{row["id"]}/{variant}','case':case,'suite':'long','mode':str(length),'variant':variant,'thread':row['thread'],'step':row['step'],'state':copy.deepcopy(state),'expected':row['expected']})
 random.Random(20260921).shuffle(jobs)
 return jobs,rows

if __name__=='__main__':
 import argparse
 p=argparse.ArgumentParser(); p.add_argument('--run',action='store_true'); args=p.parse_args()
 OUT.mkdir(exist_ok=True,parents=True); jobs,rows=jobs_and_rows()
 config={'jobs_sha256':hashlib.sha256(json.dumps(jobs,sort_keys=True,ensure_ascii=False).encode()).hexdigest(),'prompts':c.PROMPTS,'lengths':[10,30],'binary_threshold':.5,'model':'jev-1.13.0','probability_sum_tolerance':.051}
 manifest=OUT/'manifest.json'
 if manifest.exists(): assert json.loads(manifest.read_text())==config
 else: manifest.write_text(json.dumps(config,ensure_ascii=False,indent=2))
 (OUT/'dataset.jsonl').write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in rows))
 path=OUT/'responses.jsonl'; records=[json.loads(l) for l in path.read_text().splitlines()] if path.exists() else []
 if args.run:
  key=os.environ.get('TYPESAFE_API_KEY')
  if not key:
   for line in (c.ROOT/'.env.local').read_text().splitlines():
    if line.startswith('TYPESAFE_API_KEY='): key=line.split('=',1)[1].strip().strip('\"\'')
  assert key
  done={r['id'] for r in records}
  with ThreadPoolExecutor(max_workers=3) as pool,path.open('a') as out:
   futures=[pool.submit(c.call,j,key) for j in jobs if j['id'] not in done]
   for future in as_completed(futures):
    r=future.result(); records.append(r); out.write(json.dumps(r,ensure_ascii=False)+'\n'); out.flush()
 c.OUT=OUT; c.summarize(jobs,records)
