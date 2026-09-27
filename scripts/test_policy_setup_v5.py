import unittest
import policy_setup_v5 as v5
import policy_setup_v4 as v4
from test_policy_setup import ready, response


class SettingsModelTests(unittest.TestCase):
    def test_actual_request_model_usage_cost_and_original_preservation(self):
        seen = []
        def transport(payload, key):
            seen.append(payload)
            return response(ready())
        session = v5.new_session('Calendar 시간 변경은 알리고 장소만 변경은 제외해.', [])
        v5.step(session, 'dummy', transport)
        self.assertEqual(seen[0]['model'], 'gpt-4.1-2025-04-14')
        self.assertEqual(v5.confirm(session), session['instruction'])
        metric = session['metrics'][0]
        self.assertEqual(metric['model'], v5.MODEL)
        self.assertEqual(metric['prompt_version'], 'v5')
        self.assertAlmostEqual(metric['estimated_usd'], (80*2 + 20*.5 + 30*8)/1e6)
        self.assertEqual(v4.MODEL, 'gpt-4.1-mini-2025-04-14')

    def test_service_failure_is_not_retried_and_cost_stays_unknown(self):
        calls = []
        def fail(*args):
            calls.append(True)
            raise v5.SetupError('network_or_timeout')
        session = v5.new_session('Gmail 회신 요청만 알려줘.', [])
        with self.assertRaises(v5.SetupError):
            v5.step(session, 'dummy', fail)
        self.assertEqual(len(calls), 1)
        self.assertIsNone(session['result'])
        self.assertIsNone(session['metrics'][0]['estimated_usd'])

    def test_shared_http_adapter_exists(self):
        import urllib.request
        self.assertTrue(issubclass(v5.NoRedirect, urllib.request.HTTPRedirectHandler))
        self.assertTrue(issubclass(v4.NoRedirect, urllib.request.HTTPRedirectHandler))


if __name__ == '__main__':
    unittest.main()
