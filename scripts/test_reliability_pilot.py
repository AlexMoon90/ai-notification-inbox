import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import evaluate_reliability_pilot as pilot


def row():
    return {'id': 'case', 'language': 'ko', 'domain': 'payment', 'instruction': 'USER_ONLY',
            'expected_status': 'ready', 'gold_instruction': 'GOLD_SECRET',
            'notifications': [{'id': 'n1', 'app': 'Gmail', 'title': 'Invoice', 'text': 'Payment due',
                               'should_notify': True, 'high_risk': True}]}


class PilotTests(unittest.TestCase):
    def test_clarification_never_receives_gold_or_notifications(self):
        def fake_step(session, key):
            serialized = json.dumps(session)
            self.assertNotIn('GOLD_SECRET', serialized)
            self.assertNotIn('Payment due', serialized)
            self.assertNotIn('expected_status', serialized)
            return {'status': 'needs_input'}
        with patch.object(pilot.setup, 'step', fake_step):
            self.assertTrue(pilot.clarify(row(), 'dummy')['valid'])

    def test_normalized_path_never_receives_gold_or_expected(self):
        settings = [{'id': 'case', 'valid': True, 'result': {'status': 'ready', 'normalized_instruction': 'MODEL_ONLY'}}]
        jobs = pilot.make_jobs([row()], settings)
        state = next(j['state'] for j in jobs if j['id'].endswith('/normalized'))
        self.assertEqual(state['policy'], 'MODEL_ONLY')
        self.assertNotIn('GOLD_SECRET', json.dumps(state))
        self.assertNotIn('should_notify', state['notification'])
        self.assertNotIn('high_risk', state['notification'])

    def test_failed_or_pending_setup_never_produces_normalized_job(self):
        for setting in ({'valid': False, 'result': None}, {'valid': True, 'result': {'status': 'needs_input'}}):
            jobs = pilot.make_jobs([row()], [{'id': 'case', **setting}])
            self.assertEqual([j['id'] for j in jobs], ['case/n1/gold'])

    def test_retained_positive_is_not_success_and_blocked_stays_in_denominator(self):
        settings = [{'id': 'case', 'valid': True, 'result': {'status': 'needs_input'}, 'metrics': []}]
        records = [{'id': 'case/n1/gold', 'valid': True, 'p': .7, 'latency_ms': 1}]
        with tempfile.TemporaryDirectory() as tmp, patch.object(pilot, 'OUT', Path(tmp)), patch('builtins.print'):
            pilot.report([row()], settings, records)
            summary = json.loads((Path(tmp) / 'summary.json').read_text())
            for variant in ('gold', 'normalized'):
                c = summary['classification'][variant]['all']
                self.assertEqual(c['expected_positive'], 1)
                self.assertEqual(c['positive_not_actively_notified'], 1)
                self.assertEqual(c['active_recall_at_08_all_expected'], 0)
            self.assertEqual(summary['classification']['normalized']['all']['valid'], 0)
            self.assertEqual(summary['classification']['gold']['all']['accuracy_at_05'], 1)
            self.assertFalse(summary['suppression_enabled'])

    def test_api_failure_cost_unknown_not_reported_as_zero_cost_success(self):
        result = pilot.usage_summary([{'latency_ms': 40, 'valid': False}], 'jev')
        self.assertEqual(result['unknown_cost_calls'], 1)
        self.assertEqual(result['calls'], 1)

    def test_review_digest_prevents_changed_dataset(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(pilot, 'OUT', Path(tmp)):
            path = Path(tmp)
            (path / 'dataset.json').write_text('[]')
            (path / 'review.sha256.txt').write_text(pilot.digest(b'[]'))
            pilot.freeze([])
            (path / 'dataset.json').write_text('[{}]')
            with self.assertRaises(AssertionError):
                pilot.freeze([])


if __name__ == '__main__':
    unittest.main()
