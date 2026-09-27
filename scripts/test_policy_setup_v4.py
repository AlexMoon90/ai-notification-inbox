import json
import unittest
import policy_setup_v4 as v4
import test_policy_setup as legacy_tests
from test_policy_setup import ready, question, response
from unittest.mock import patch


class V4RegressionTests(legacy_tests.PolicySetupTest):
    """Run the same session contract against v4 without altering v3."""
    def setUp(self):
        import test_policy_setup as legacy
        self.patcher = patch.multiple(legacy, step=v4.step, build_request=v4.build_request)
        self.patcher.start()
        self.addCleanup(self.patcher.stop)


class V4ChangesTest(unittest.TestCase):
    def test_clear_instruction_preserved_even_when_model_drops_qualifier(self):
        original = 'Google Calendar에서 시간 변경은 알려줘. 장소만 바뀐 것은 제외해.'
        s = v4.new_session(original, [])
        r = ready()
        r['normalized_instruction'] = '장소 변경은 제외한다.'
        v4.step(s, 'dummy', lambda *_: response(r))
        self.assertEqual(v4.confirm(s), original)
        self.assertEqual(s['result']['change_summary'], original)
        self.assertEqual(s['metrics'][0]['prompt_version'], 'v4')

    def test_edit_is_not_replaced_with_latest_fragment(self):
        s = v4.new_session('이제 승인 요청도 알려줘.', [], 'Gmail 납부 요청은 알려줘.')
        r = ready()
        r['normalized_instruction'] = 'Gmail 납부 요청과 승인 요청을 알려준다.'
        v4.step(s, 'dummy', lambda *_: response(r))
        self.assertEqual(v4.confirm(s), r['normalized_instruction'])
        self.assertEqual(s['normalization_mode'], 'merged_draft_requires_review')

    def test_real_answers_reach_request_and_are_not_lost_to_verbatim_path(self):
        s = v4.new_session('내 이름이 나오면 알려줘.', [])
        q = question('text')
        q['questions'][0].update(field='identity', options=[])
        v4.step(s, 'dummy', lambda *_: response(q))
        v4.answer(s, 'target', text='소담')
        payload = v4.build_request(s)
        context = json.loads(payload['input'][1]['content'])
        self.assertEqual(context['recorded_answers'][0]['text'], '소담')
        r = ready()
        r['normalized_instruction'] = '소담이 언급되면 알려준다.'
        v4.step(s, 'dummy', lambda *_: response(r))
        self.assertIn('소담', v4.confirm(s))

    def test_v3_baseline_prompt_not_mutated(self):
        import policy_setup
        s = v4.new_session('Gmail 회신 요청만 알려줘.', [])
        v4.step(s, 'dummy', lambda *_: response(ready()))
        self.assertEqual(policy_setup.PROMPT_VERSION, 'v3')
        self.assertNotEqual(policy_setup.PROMPT, v4.PROMPT)


if __name__ == '__main__':
    unittest.main()
