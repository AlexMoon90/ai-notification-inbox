import json
import unittest
import policy_setup_luna_v2 as client
from test_policy_setup import ready,response

class LunaStateTests(unittest.TestCase):
    def test_refresh_is_reflected_in_payload_without_inventing_selection(self):
        session=client.new_session('Let me choose the room.',[])
        build=client.previous.engine.build_request
        first=client.adapt(build(session))
        self.assertEqual(json.loads(first['input'][1]['content'])['candidate_availability']['count'],0)
        client.refresh_candidates(session,[{'id':'r','label':'Room','preview':'x'}])
        original=build(session)
        updated=client.adapt(original)
        context=json.loads(updated['input'][1]['content'])
        self.assertEqual(context['candidate_availability'],{'count':1,'selection_recorded':False})
        self.assertEqual(context['selected_candidate_ids'],[])
        self.assertEqual(original['model'],'gpt-4.1-2025-04-14')
        self.assertEqual(updated['reasoning'],{'effort':'low'})
        self.assertNotIn('temperature',updated)
        self.assertEqual(updated['text'],original['text'])

    def test_both_calls_receive_contract_and_luna_settings(self):
        session=client.new_session('Add approvals.',[],'Notify about bills.')
        calls=[]
        def transport(payload,key):
            calls.append(payload)
            return response(ready())
        client.step(session,'unused',transport)
        self.assertEqual(len(calls),2)
        for payload in calls:
            self.assertIn(client.CONTRACT,payload['input'][0]['content'])
            self.assertEqual(payload['model'],'gpt-5.6-luna')
        self.assertIn('FINAL DRAFT REVIEW',calls[1]['input'][0]['content'])
        self.assertTrue(all(m['model']=='gpt-5.6-luna' for m in session['metrics']))
        self.assertIsNone(session['confirmed_instruction'])

if __name__=='__main__': unittest.main()
