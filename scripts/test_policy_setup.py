import copy
import json
from pathlib import Path
import tempfile
import unittest
from policy_setup import (SetupError, new_session, step, answer, confirm, save_session,
                          refresh_candidates, reset_target_selection, validate_result, build_request)

CANDIDATES = [{'id': 'a', 'label': '대화 A', 'preview': '합성 메시지'}]


def ready():
    return {'status': 'ready', 'message': '검토해 주세요', 'questions': [], 'unresolved': [],
            'normalized_instruction': '광고는 조용히 처리한다.', 'referenced_candidate_ids': [],
            'change_kind': 'new', 'change_summary': '새 기준'}


def question(kind='conversation'):
    r = ready()
    r.update(status='needs_input', normalized_instruction=None, unresolved=['대상 선택'], questions=[
        {'id': 'target', 'field': 'scope', 'kind': kind, 'prompt': '대상을 선택하세요', 'reason': '지정 필요',
         'options': [{'id': 'o1', 'label': '대화 A', 'candidate_id': 'a'}]}])
    return r


def response(result):
    return {'status': 'completed', 'output': [{'type': 'message', 'content': [
        {'type': 'output_text', 'text': json.dumps(result)}]}],
        'usage': {'input_tokens': 100, 'output_tokens': 30, 'input_tokens_details': {'cached_tokens': 20}}}


class PolicySetupTest(unittest.TestCase):
    def session(self):
        return new_session('회사 단톡에서 답변 요청만 알려줘', CANDIDATES, '기존 기준 유지')

    def test_choice_roundtrip_and_explicit_confirmation(self):
        s = self.session()
        step(s, 'unused', lambda *_: response(question()))
        answer(s, 'target', ['o1'])
        r = ready()
        r['referenced_candidate_ids'] = ['a']
        step(s, 'unused', lambda *_: response(r))
        self.assertIsNone(s['confirmed_instruction'])
        self.assertEqual(confirm(s), r['normalized_instruction'])
        self.assertEqual(s['current_instruction'], '기존 기준 유지')

    def test_cannot_auto_bind_real_but_unselected_candidate(self):
        s = self.session()
        r = ready()
        r['referenced_candidate_ids'] = ['a']
        with self.assertRaisesRegex(SetupError, 'unconfirmed_candidate'):
            validate_result(r, s)

    def test_invented_candidate_option_is_rejected(self):
        s = self.session()
        r = question()
        r['questions'][0]['options'][0]['candidate_id'] = 'invented'
        with self.assertRaisesRegex(SetupError, 'invented_candidate'):
            validate_result(r, s)

    def test_model_cannot_drop_selected_scope(self):
        s = self.session()
        s['selected_candidate_ids'] = ['a']
        with self.assertRaisesRegex(SetupError, 'selected_scope_dropped'):
            validate_result(ready(), s)

    def test_save_reload_preserves_partial_answers(self):
        s = self.session()
        step(s, 'unused', lambda *_: response(question()))
        answer(s, 'target', ['o1'])
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder)/'draft.json'
            save_session(p, s)
            loaded = json.loads(p.read_text())
            self.assertEqual(s, loaded)
            self.assertEqual(p.stat().st_mode & 0o777, 0o600)
            with self.assertRaisesRegex(SetupError, 'already_answered'):
                answer(loaded, 'target', ['o1'])

    def test_empty_candidates_can_refresh_without_losing_request(self):
        s = new_session('회사 단톡을 선택하고 싶어', [])
        old = copy.deepcopy(s)
        refresh_candidates(s, CANDIDATES)
        self.assertEqual(old['instruction'], s['instruction'])
        self.assertEqual(s['candidates'], CANDIDATES)

    def test_expired_selection_stops_before_api(self):
        s = self.session()
        s['selected_candidate_ids'] = ['a']
        refresh_candidates(s, [])
        calls = []
        with self.assertRaisesRegex(SetupError, 'reselection_required'):
            step(s, 'unused', lambda *_: calls.append(True))
        self.assertEqual(calls, [])
        self.assertEqual(s['selected_candidate_ids'], ['a'])

    def test_explicit_reselection_preserves_unrelated_answers(self):
        s = self.session()
        step(s, 'unused', lambda *_: response(question()))
        answer(s, 'target', ['o1'])
        other = {'question': {'kind': 'text'}, 'text': '지훈'}
        s['answers'].append(other)
        refresh_candidates(s, [])
        reset_target_selection(s)
        self.assertEqual(s['selected_candidate_ids'], [])
        self.assertEqual(s['answers'], [other])
        self.assertEqual(s['instruction'], '회사 단톡에서 답변 요청만 알려줘')

    def test_incomplete_response_keeps_draft_and_records_usage(self):
        s = self.session()
        raw = response(ready())
        raw['status'] = 'incomplete'
        with self.assertRaisesRegex(SetupError, 'incomplete_response'):
            step(s, 'unused', lambda *_: raw)
        self.assertIsNone(s['result'])
        self.assertEqual(s['current_instruction'], '기존 기준 유지')
        self.assertEqual(s['metrics'][0]['input_tokens'], 100)

    def test_timeout_keeps_answers_and_unknown_cost(self):
        s = self.session()
        s['answers'] = [{'synthetic': True}]
        def fail(*_):
            raise SetupError('network_or_timeout')
        with self.assertRaises(SetupError):
            step(s, 'unused', fail)
        self.assertEqual(s['answers'], [{'synthetic': True}])
        self.assertIsNone(s['metrics'][0]['estimated_usd'])

    def test_invalid_schema_never_reaches_confirmation(self):
        s = self.session()
        r = ready()
        r['execute'] = True
        with self.assertRaises(SetupError):
            step(s, 'unused', lambda *_: response(r))
        with self.assertRaises(SetupError):
            confirm(s)

    def test_input_validation_before_state_mutation(self):
        s = self.session()
        step(s, 'unused', lambda *_: response(question()))
        before = copy.deepcopy(s)
        for ids in (['missing'], ['o1', 'o1']):
            with self.assertRaises(SetupError):
                answer(s, 'target', ids)
            self.assertEqual(s, before)

    def test_free_text_answer_preserved_not_bound_automatically(self):
        s = self.session()
        step(s, 'unused', lambda *_: response(question()))
        answer(s, 'target', text='이 중에 없어요')
        self.assertEqual(s['selected_candidate_ids'], [])
        self.assertEqual(s['answers'][-1]['text'], '이 중에 없어요')

    def test_requests_do_not_store_and_mask_credentials(self):
        s = new_session('test@example.com 010-1234-5678 sk-fake-secret', [])
        p = build_request(s)
        self.assertFalse(p['store'])
        self.assertTrue(p['text']['format']['strict'])
        for secret in ('test@example.com', '010-1234-5678', 'sk-fake-secret'):
            self.assertNotIn(secret, p['input'][1]['content'])

    def test_pending_question_does_not_require_duplicate_reason_list(self):
        s = self.session()
        r = question()
        r['unresolved'] = []
        validate_result(r, s)
        s['result'] = r
        with self.assertRaisesRegex(SetupError, 'not_ready'):
            confirm(s)

    def test_waiting_cannot_contain_executable_instruction(self):
        r = ready()
        r['status'] = 'waiting'
        with self.assertRaises(SetupError):
            validate_result(r, self.session())

    def test_refusal_recorded_without_text(self):
        s = self.session()
        raw = response(ready())
        raw['output'][0]['content'] = [{'type': 'refusal', 'refusal': 'private refusal text'}]
        with self.assertRaisesRegex(SetupError, 'model_refusal'):
            step(s, 'unused', lambda *_: raw)
        self.assertNotIn('private refusal', json.dumps(s))


if __name__ == '__main__':
    unittest.main()
