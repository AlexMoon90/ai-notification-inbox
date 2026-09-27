import copy
import unittest
import compare_jev_prompts as c

class ComparisonTests(unittest.TestCase):
 def test_prefixes_and_pairing(self):
  jobs=c.load_jobs(); self.assertEqual(len(jobs),720)
  self.assertEqual(len({j['id'] for j in jobs}),720)
  groups={}
  for j in jobs:
   groups.setdefault((j['suite'],j['case'],j['mode']),[]).append(j)
   if j['suite']=='timeline':
    h=j['state']['recent_messages']
    self.assertEqual(len(h),j['step']-1 if j['mode']=='full' else 0)
    self.assertTrue(all(m['timestamp']<j['state']['notification']['timestamp'] for m in h))
  for pairs in groups.values():
   self.assertEqual(len(pairs),3)
   self.assertTrue(all(j['state']==pairs[0]['state'] and j['expected']==pairs[0]['expected'] for j in pairs))

 def test_rounding_tolerance_and_invalid_values(self):
  b={'model':'fixture','usage':{'input_tokens':1,'output_tokens':1},'answers':{
   'category':{'type':'choice','choice':'GENERAL','confidence':.8,'probabilities':{k:(.99 if k=='GENERAL' else 0) for k in c.baseline.CATEGORIES}},
   'should_notify':{'type':'noul','noul':.1}}}
  c.validate(b)
  bad=copy.deepcopy(b); bad['answers']['category']['probabilities']['GENERAL']=.8
  with self.assertRaises(AssertionError): c.validate(bad)
  bad=copy.deepcopy(b); bad['answers']['should_notify']['noul']=float('nan')
  with self.assertRaises(AssertionError): c.validate(bad)

if __name__=='__main__': unittest.main()
