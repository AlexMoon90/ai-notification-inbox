import copy
import unittest
from collections import defaultdict
import evaluate_jev_requirements as ev

class RequirementTests(unittest.TestCase):
 def test_paired_inputs_differ_only_in_policy(self):
  rows=ev.build(); self.assertEqual(len(rows),128)
  self.assertEqual(len({r['id'] for r in rows}),128)
  groups=defaultdict(list)
  for row in rows:
   self.assertTrue(row['synthetic']); groups[row['pair']].append(row)
   self.assertNotIn('expected',row['state'])
  self.assertEqual(len(groups),64)
  for pair in groups.values():
   self.assertEqual(len(pair),2)
   states=[copy.deepcopy(r['state']) for r in pair]
   policies=[s.pop('policy') for s in states]
   self.assertNotEqual(*policies); self.assertEqual(*states)
   self.assertEqual(pair[0]['expected']['category'],pair[1]['expected']['category'])
 def test_unknown_group_not_labeled_as_private(self):
  unknown=[r for r in ev.build() if r['family']=='conversation_scope' and r['state']['conversation']['is_group'] is None]
  self.assertEqual(len(unknown),2)
  self.assertTrue(all(r['expected']['should_notify'] is None for r in unknown))

if __name__=='__main__': unittest.main()
