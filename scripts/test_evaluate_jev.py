"""Offline checks for evaluator failures; never calls an API."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
import evaluate_jev as ev

class EvaluationTests(unittest.TestCase):
    def setUp(self):
        self.row = json.loads(ev.DATA.read_text().splitlines()[0])
        self.body = {'model':'fixture', 'usage':{'input_tokens':100,'output_tokens':20},
          'answers':{'category':{'type':'choice','choice':'PROMOTION','confidence':1,
            'probabilities':{k:float(k=='PROMOTION') for k in ev.CATEGORIES}},
            **{f:{'type':'noul','noul':float(f=='promotion')} for f in ev.FLAGS}}}

    def evaluate(self, row, record):
        with tempfile.TemporaryDirectory() as directory:
            ev.report([row],[record],Path(directory))
            return json.loads((Path(directory)/'summary.json').read_text())

    def test_detects_important_false_suppression(self):
        ev.validate(self.body)
        record={'id':self.row['id'],'schema_ok':True,'response':self.body,'attempts':[{}],'latency_ms':100}
        result=self.evaluate(self.row,record)
        self.assertEqual(result['binary']['should_notify']['fn'],1)
        self.assertEqual(result['false_suppression_ids'],[self.row['id']])
        self.assertEqual(result['category_correct'],0)

    def test_rejects_malformed_probabilities_and_missing_answers(self):
        for change in ['out_of_range','sum','missing']:
            body=copy.deepcopy(self.body)
            if change=='out_of_range': body['answers']['promotion']['noul']=2
            elif change=='sum': body['answers']['category']['probabilities']['GENERAL']=.2
            else: del body['answers']['should_notify']
            with self.assertRaises((AssertionError,KeyError)):
                ev.validate(body)

    def test_api_failure_retains_and_does_not_score_as_correct(self):
        record={'id':self.row['id'],'schema_ok':False,'error':'network_or_timeout','attempts':[{}],'latency_ms':100}
        result=self.evaluate(self.row,record)
        self.assertEqual(result['valid_responses'],0)
        self.assertEqual(result['actions'],{'retain_for_review':1})
        self.assertEqual(result['all_fields_correct'],0)

    def test_unknown_label_is_excluded_and_suppression_is_reported(self):
        row=copy.deepcopy(self.row); row['expected']['should_notify']=None
        record={'id':row['id'],'schema_ok':True,'response':self.body,'attempts':[{}],'latency_ms':100}
        result=self.evaluate(row,record)
        self.assertEqual(result['binary']['should_notify']['excluded_unknown'],1)
        self.assertEqual(result['unknown_suppression_ids'],[row['id']])

if __name__=='__main__': unittest.main()
