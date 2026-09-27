import copy
import json
import unittest
import policy_setup_reliable as current
from test_policy_setup import ready, response


class PresentationContractTest(unittest.TestCase):
    def test_replay_all40_recorded_api_outputs_through_current_adapter(self):
        root = current.ROOT
        path = root / 'evaluations/policy-setup-v5/effective-settings.jsonl'
        if not path.exists():
            self.skipTest('Recorded synthetic API run not present')
        records = {r['id']: r for r in map(json.loads, path.read_text().splitlines())}
        rows = json.loads((root / 'evaluations/reliability-pilot-v1/dataset.json').read_text())
        for row in rows:
            with self.subTest(case=row['id']):
                record = records[row['id']]
                raw = copy.deepcopy(record['synthetic_api_outputs'][0])
                session = current.new_session(row['instruction'], [])
                result = current.step(session, 'unused', lambda *_: raw)
                self.assertEqual(result['status'], row['expected_status'])
                if result['status'] == 'ready':
                    self.assertEqual(result['normalized_instruction'], row['instruction'])
                self.assertEqual(len(session['metrics']), 1)

    def test_merged_draft_cannot_claim_activation_and_preserves_instruction(self):
        session = current.new_session('승인도 알려줘.', [], 'Gmail 미납을 알려줘.')
        result = ready()
        result.update(message='설정이 완료되었습니다.', change_summary='적용 완료',
                      normalized_instruction='Gmail 미납과 승인 요청을 알려준다.')
        actual = current.step(session, 'dummy', lambda *_: response(result))
        self.assertNotIn('완료', actual['message'])
        self.assertEqual(actual['normalized_instruction'], result['normalized_instruction'])
        self.assertEqual(actual['change_summary'], actual['normalized_instruction'])
        self.assertIsNone(session['confirmed_instruction'])
        self.assertEqual([m['purpose'] for m in session['metrics']], ['clarify', 'semantic_review'])

    def test_unsupported_status_does_not_advertise_invented_alternatives(self):
        session = current.new_session('Verify the physical delivery.', [])
        result = ready()
        result.update(status='unsupported', normalized_instruction=None, unresolved=['No physical evidence'],
                      message='We can inspect photos and your doorbell feed instead.')
        actual = current.step(session, 'dummy', lambda *_: response(result))
        self.assertNotIn('photos', actual['message'])
        self.assertNotIn('doorbell', actual['message'])
        self.assertEqual(actual['status'], 'unsupported')

    def test_present_does_not_rewrite_questions_or_policy(self):
        session = current.new_session('중요한 것만 알려줘.', [])
        result = ready()
        result.update(status='needs_input', normalized_instruction=None,
                      questions=[{'id': 'q', 'kind': 'text', 'prompt': '어떤 사건인가요?'}])
        original = copy.deepcopy(result['questions'])
        current.present(session, result)
        self.assertEqual(result['questions'], original)
        self.assertIsNone(result['normalized_instruction'])

    def test_conversation_widget_has_scope_field_without_changing_candidate_ids(self):
        session = current.new_session('회사 방을 선택할게.', [])
        result = ready()
        result.update(status='needs_input', normalized_instruction=None, questions=[
            {'kind': 'conversation', 'field': 'identity', 'options': [{'candidate_id': 'room-a'}]}])
        current.present(session, result)
        self.assertEqual(result['questions'][0]['field'], 'scope')
        self.assertEqual(result['questions'][0]['options'][0]['candidate_id'], 'room-a')

    def test_review_failure_cannot_leave_primary_draft_confirmable(self):
        session = current.new_session('승인도 추가해.', [], 'Gmail 미납을 알려줘.')
        calls = []
        def transport(*_):
            calls.append(True)
            if len(calls) == 1:
                return response(ready())
            raise current.SetupError('network_or_timeout')
        with self.assertRaises(current.SetupError):
            current.step(session, 'dummy', transport)
        self.assertIsNone(session['result'])
        with self.assertRaises(current.SetupError):
            current.confirm(session)


if __name__ == '__main__':
    unittest.main()
