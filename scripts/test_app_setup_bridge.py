import unittest
from app_setup_bridge import process
from test_policy_setup import ready, response, question, CANDIDATES
import policy_setup_luna_v2 as engine

class BridgeTests(unittest.TestCase):
    def test_question_answer_review_stays_unapplied(self):
        s = process({'action':'create','instruction':'회사 단톡 답변 요청만 알려줘','candidates':CANDIDATES}, 'unused', lambda *_:response(question()))['session']
        result=ready();result['referenced_candidate_ids']=['a']
        s = process({'action':'answer','session':s,'answers':[{'id':'target','options':['o1']}]}, 'unused', lambda *_:response(result))['session']
        self.assertIsNone(s['confirmed_instruction'])
        s = process({'action':'confirm','session':s}, 'unused')['session']
        self.assertIsNotNone(s['confirmed_instruction'])
        self.assertNotIn('active_policy', s)
        self.assertEqual(len(s['metrics']),3)

    def test_api_failure_keeps_answer_but_clears_old_result(self):
        s=engine.new_session('회사 단톡 답변 요청',CANDIDATES)
        engine.step(s,'unused',lambda *_:response(question()))
        def fail(*_): raise engine.SetupError('upstream failure')
        result=process({'action':'answer','session':s,'answers':[{'id':'target','options':['o1']}]},'unused',fail)
        self.assertIn('error',result)
        self.assertIsNone(result['session']['result'])
        self.assertEqual(result['session']['selected_candidate_ids'],['a'])
        self.assertEqual(len(result['session']['answers']),1)

    def test_waiting_can_refresh_candidates(self):
        s=engine.new_session('회사 단톡 답변 요청',[])
        result=process({'action':'refresh','session':s,'candidates':CANDIDATES},'unused',lambda *_:response(question()))
        self.assertEqual(result['session']['result']['status'],'needs_input')

    def test_confirmation_before_ready_rejected(self):
        with self.assertRaises(engine.SetupError):
            process({'action':'confirm','session':engine.new_session('일정 변경만 알려줘',[])},'unused')

if __name__=='__main__': unittest.main()
