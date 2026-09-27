"""Prelabel chronological prefixes, never include future messages."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
POLICY=('나에게 해당하는 확정된 약속, 일정 변경·취소, 미납 금액 요청, 답변·선택·승인·자료 제출 요청, 실제 배송·예약 및 보안 경고를 알려줘. '
        '단순 제안, 잡담, 광고, 이미 해결·완료된 일은 조용히 처리해. 납부·행동 요청 철회도 알려줘. '
        '대화 전체를 매번 다시 알리지 말고 이번 새 메시지가 전달하는 사건을 판단해. 다른 사람만 할 일은 알리지 마.')
# Five chronological arrivals per conversation. Labels: current event, not overall pending state.
THREADS={
'meeting': [('내일 저녁 같이 볼까?','GENERAL',False),('나는 7시면 가능해. 아직 확정은 아니야.','GENERAL',False),('민수도 된대. 그럼 내일 7시 시청역에서 확정!','MEETING',True),('장소만 서울역으로 변경할게.','SCHEDULE_CHANGE',True),('내일 모임 자체를 취소합니다.','SCHEDULE_CHANGE',True)],
'dues': [('민수님 이번 회비 4만원 납부 부탁해요.','PAYMENT_OR_DUES',True),('아직 입금 확인이 안 됐어요.','PAYMENT_OR_DUES',True),('내일 낮까지 부탁드려요.','PAYMENT_OR_DUES',True),('민수님 입금 확인했습니다. 납부 완료예요.','GENERAL',False),('이제 추가로 보낼 필요 없어요.','GENERAL',False)],
'approval': [('민수님, 첨부한 로고 두 개 중 하나를 선택해서 회신해주세요.','REPLY_REQUIRED',True),('아직 회신 전이시죠?','REPLY_REQUIRED',True),('오전 중으로 부탁해요.','REPLY_REQUIRED',True),('선택해주신 B로 반영 완료했습니다.','GENERAL',False),('고마워요!','GENERAL',False)],
'withdrawal': [('민수님 서류에 서명해주세요.','REPLY_REQUIRED',True),('오늘까지 해야 해요.','REPLY_REQUIRED',True),('정정: 서명 요청을 철회합니다. 진행하지 마세요.','GENERAL',True),('새로 하실 일은 없습니다.','GENERAL',False),('네, 감사합니다.','GENERAL',False)],
'others': [('수진님만 회비 2만원 보내주세요. 민수님은 완납입니다.','GENERAL',False),('수진님, 내일까지 부탁해요.','GENERAL',False),('민수님은 별도로 시안 확인 후 의견 보내주세요.','REPLY_REQUIRED',True),('민수님 의견 확인했고 반영했습니다.','GENERAL',False),('수진님 회비도 납부 완료입니다.','GENERAL',False)],
'security': [('보안 교육에서 새 기기 로그인 사례를 배웠어요.','GENERAL',False),('예문은 "낯선 기기에서 로그인했습니다" 입니다. 실제 사건 아니에요.','GENERAL',False),('실제 경고: 민수님 계정에 미등록 기기가 로그인했습니다.','SECURITY',True),('확인 결과 승인되지 않은 접속으로 판명되었습니다.','SECURITY',True),('조치가 끝났습니다. 모든 세션 해제 및 비밀번호 변경 완료, 추가 행동 필요 없음.','GENERAL',False)],
'booking': [('금요일 저녁 식당을 예약하면 어떨까?','GENERAL',False),('아직 예약은 하지 않았어.','GENERAL',False),('민수님 식당 예약 접수가 완료됐습니다.','DELIVERY_OR_RESERVATION',True),('예약 시간이 오후 6시에서 7시로 변경됐습니다.','SCHEDULE_CHANGE',True),('예약은 취소됐습니다. 방문하지 마세요.','SCHEDULE_CHANGE',True)],
'promotion': [('(광고) 쇼핑몰 할인 쿠폰 받아가세요.','PROMOTION',False),('오늘만 반값! 지금 구매!','PROMOTION',False),('민수님이 주문한 상품의 배송이 시작됐습니다. 아래 광고: 다음 주문 5% 할인','DELIVERY_OR_RESERVATION',True),('고객님 상품 배송 완료, 현관 앞에 놓았습니다.','DELIVERY_OR_RESERVATION',True),('추가 구매하면 사은품! 할인 행사 안내입니다.','PROMOTION',False)],
'noise': [('민수님 미납 회비 6만원을 보내주세요.','PAYMENT_OR_DUES',True),('오늘 날씨 참 좋네요.','GENERAL',False),('점심은 다들 드셨나요?','GENERAL',False),('아까 회비 건, 아직 미납이에요. 내일까지 부탁해요.','PAYMENT_OR_DUES',True),('납부 요청은 철회합니다. 돈 보내지 마세요.','GENERAL',True)],
'english': [('Minsu, please reply with the delivery address.','REPLY_REQUIRED',True),('Still waiting for your response.','REPLY_REQUIRED',True),('By noon, please.','REPLY_REQUIRED',True),('Address received. No further action is needed.','GENERAL',False),('Thanks and have a good day!','GENERAL',False)],
'reopened': [('민수님 자료를 올려주세요.','REPLY_REQUIRED',True),('자료 받았습니다. 완료 처리했어요.','GENERAL',False),('파일이 깨져서 열 수 없네요. 민수님 다시 올려주세요.','REPLY_REQUIRED',True),('오늘 오후까지 부탁해요.','REPLY_REQUIRED',True),('새 파일 정상 확인했습니다. 이제 끝났어요.','GENERAL',False)],
'correction': [('민수님 회비 7만원이 미납입니다.','PAYMENT_OR_DUES',True),('방금 건 잘못된 안내입니다. 민수님은 완납이니 보내지 마세요.','GENERAL',True),('네, 추가 납부 안 하셔도 됩니다.','GENERAL',False),('다른 건입니다. 내일 상담은 오전 11시로 확정됐습니다.','MEETING',True),('정정합니다. 상담은 오후 1시로 변경됐어요.','SCHEDULE_CHANGE',True)],
}
rows=[]
for thread, messages in THREADS.items():
 history=[]
 for i,(text,category,notify) in enumerate(messages):
  now=f'2026-09-21T10:{i*5:02}:00+09:00'
  rows.append({'id':f'{thread}-{i+1:02}', 'thread':thread,'step':i+1,'synthetic':True,
    'state':{'user':{'name':'민수','english_name':'Minsu'},'policy':POLICY,
      'notification':{'source_app':'KakaoTalk','title':'대화 알림','text':text,'timestamp':now},
      'recent_messages':list(history)},
    'expected':{'category':category,'should_notify':notify}})
  history.append({'timestamp':now,'text':text})
out=ROOT/'evaluations/jev/timeline.v1.jsonl'
out.write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in rows))
print(f'{len(rows)} prefixes, {len(THREADS)} independent conversations')
if __name__=='__main__': pass
