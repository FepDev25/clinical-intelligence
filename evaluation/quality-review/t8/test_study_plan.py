import copy
import unittest
from study_plan import build,validate
class StudyPlanTest(unittest.TestCase):
    def test_eight_arms_keep_all_three_rounds_and_account_for_comparator(self):
        p=build();r=validate(p)
        self.assertEqual((39,3,8),(r['notes'],r['rounds'],r['arms']))
        self.assertEqual((702,819,1638,936),(r['units'],r['maxGenerateCalls'],r['maxHttpCalls'],r['evaluations']))
        self.assertFalse(r['authorized']);self.assertEqual('20',p['budget']['proposedMaxUsd'])
    def test_no_reduction_or_silent_retry(self):
        for key,value in [('outputTokens',1024),('rounds',1),('retries',1)]:
            p=build();p['limits'][key]=value
            with self.assertRaises(ValueError):validate(p)
    def test_no_missing_duplicate_or_unlisted_units(self):
        for op in [lambda p:p['schedule'].pop(),lambda p:p['schedule'].append(p['schedule'][0]),lambda p:p['schedule'][0].update(caseId='unlisted')]:
            p=build();op(p)
            with self.assertRaises(ValueError):validate(p)
    def test_wrong_comparator_or_arms_rejected(self):
        p=build();p['configurations'][-1]['model']='unverified'
        with self.assertRaises(ValueError):validate(p)
    def test_authorization_is_separate_from_the_frozen_design(self):
        p=build();p['authorization']['granted']=True
        with self.assertRaises(ValueError):validate(p)
if __name__=='__main__':unittest.main()
