"""Synthetic-only API probe using the actual Android question strings; no user notification input."""
import hashlib,json,re,time,urllib.request,sys
from pathlib import Path
from evaluate_reliability_pilot import load_jev_key
ROOT=Path(__file__).resolve().parents[1]
source=(ROOT/'app/src/main/java/com/ainotification/inbox/HubClassifier.kt').read_text()
questions=dict(re.findall(r'"([A-Z_]+)" to "([^"\n]+)"',source))
prefix=re.search(r'put\("instructions","([^"\n]+)\$prompt"\)',source).group(1)
cases=[
 ('예약 확정','OO병원 9월 30일 오후 2시 진료 예약이 확정되었습니다.',{'RESERVATION'},set()),
 ('Delivery completed','Your parcel was delivered to your front door.',{'DELIVERY'},set()),
 ('카드 승인','OO카드 32,500원 일시불 승인되었습니다.',{'PAYMENT'},set()),
 ('Refund issued','Your refund for order #123 has been processed.',{'REFUND'},set()),
 ('Instagram','Alex liked your photo.',{'SOCIAL_ACTIVITY'},set()),
 ('Check in now','Your flight AB123 to London departs tomorrow at 10:00. Online check-in is open.',{'TRAVEL'},set()),
 ('오늘의 특가','(광고) 호텔 예약하면 최대 50% 할인! 지금 구매하세요.',set(),set(questions)),
 ('Direct message from Alex','Hi, I am on my way home.',set(),{'SOCIAL_ACTIVITY','MEETING_CONFIRMED','REPLY_REQUIRED'}),
 ('친구모임방','이번 주 토요일 시간 괜찮으세요?',{'REPLY_REQUIRED'},{'MEETING_CONFIRMED'}),
 ('입금 알림','OO은행 계좌로 50,000원이 입금되었습니다.',{'MONEY_RECEIVED'},set()),
 ('Task complete','Your AI research agent finished the report. It is ready to review.',{'AI_TASK_COMPLETED'},set()),
 ('Approval needed','Your coding agent is paused and needs your approval to continue.',{'AI_INPUT_REQUIRED'},set()),
]
holdout='--holdout' in sys.argv
if holdout:
 cases=[
  ('Booking confirmed','Your table for two at Green Cafe is booked for Friday at 7pm.',{'RESERVATION'},set()),
  ('배송 지연','주문하신 택배가 물류 사정으로 하루 늦게 도착할 예정입니다.',{'DELIVERY'},set()),
  ('Deposit received','A bank transfer of USD 120.00 was credited to your account.',{'MONEY_RECEIVED'},set()),
  ('환불 완료','취소하신 주문의 결제 금액 12,000원이 환불되었습니다.',{'REFUND'},set()),
  ('새 댓글','민수가 회원님의 게시물에 댓글을 남겼습니다.',{'SOCIAL_ACTIVITY'},set()),
  ('탑승 안내','서울행 AB123편의 탑승이 시작되었습니다. 12번 게이트로 오세요.',{'TRAVEL'},set()),
  ('Returns policy','Our store allows returns within 30 days. Read our policy here.',set(),{'REFUND','ORDER','PAYMENT'}),
  ('Travel deals','Save 40% on your next hotel stay. Book now!',set(),set(questions)),
  ('회의 제안','다음 주 화요일에 회의하면 어떨까요?',{'REPLY_REQUIRED'},{'MEETING_CONFIRMED','SCHEDULE_CHANGE'}),
  ('보안 안내','새로운 기기에서 로그인했습니다.',set(),set(questions)),
 ]
filename='live-holdout.json' if holdout else 'live-desktop.json'
out=ROOT/'evaluations/information-hub' ;out.mkdir(parents=True,exist_ok=True)
report={'source_sha256':hashlib.sha256(source.encode()).hexdigest(),'cases':[],'environment':'desktop_python_actual_android_questions','category_threshold':.90,'badge_threshold':.95}
key=load_jev_key()
for i,(title,body,required,forbidden) in enumerate(cases):
 payload={'model':'jev-1.13.0','state':{'notification':{'app':'Test','title':title,'text':body}},'questions':{k:{'type':'noul','instructions':prefix+v} for k,v in questions.items()}}
 started=time.monotonic()
 req=urllib.request.Request('https://api.typesafe.ai/v1/systemone',data=json.dumps(payload).encode(),headers={'Authorization':'Bearer '+key,'Content-Type':'application/json'})
 try:
  with urllib.request.urlopen(req,timeout=40) as res: result=json.load(res)
  scores={k:result['answers'][k]['noul'] for k in questions}
  predicted={k for k,v in scores.items() if v>=(.90 if k in {"RESERVATION","DELIVERY","PAYMENT","MONEY_RECEIVED","ORDER","REFUND","SOCIAL_ACTIVITY","TRAVEL"} else .95)}
  item={'id':i,'required':sorted(required),'forbidden':sorted(forbidden),'predicted':sorted(predicted),'passed':required<=predicted and not forbidden&predicted,'scores':scores,'usage':result['usage'],'latency_ms':round((time.monotonic()-started)*1000)}
 except Exception as e: item={'id':i,'passed':False,'error_type':type(e).__name__}
 report['cases'].append(item)
 (out/filename).write_text(json.dumps(report,ensure_ascii=False,indent=2))
 print(i,item['passed'],item.get('predicted',[]),flush=True)
report['passed']=sum(c['passed'] for c in report['cases'])
report['estimated_usd']=sum(c.get('usage',{}).get('input_tokens',0)*.042/1000000 for c in report['cases'])
(out/filename).write_text(json.dumps(report,ensure_ascii=False,indent=2))
