import copy
import unittest
from experiment_plan import build,validate,ROOT
class ExperimentPlanTest(unittest.TestCase):
    def test_complete_factorial_and_fixed_capped_schedule(self):
        p=build();report=validate(p)
        self.assertEqual(39,report['notes']);self.assertEqual(3,report['rounds'])
        self.assertEqual(702,report['maxGenerateCalls']);self.assertEqual(1404,report['maxHttpCalls'])
        self.assertEqual(819,report['evaluations']);self.assertFalse(report['executionReady'])
        self.assertEqual(build()['schedule'],p['schedule'])
    def test_missing_duplicate_or_extra_schedule_record_is_rejected(self):
        for mutate in [lambda p:p['schedule'].pop(),lambda p:p['schedule'].append(p['schedule'][0]),lambda p:p['schedule'][0].update(caseId='unlisted')]:
            p=build();mutate(p)
            with self.assertRaises(ValueError):validate(p)
    def test_prompt_filter_or_thinking_changes_cannot_be_hidden(self):
        for part,key,value in [('arms','filter','evidence-1'),('configurations','thinkingLevel','MINIMAL'),('configurations','promptVersion','external')]:
            p=build();p[part][0][key]=value
            with self.assertRaises(ValueError):validate(p)
    def test_cannot_lower_limits_or_enable_retries_to_look_better(self):
        for key,value in [('outputTokens',1024),('retries',1),('maxGenerateCalls',999),('rounds',1)]:
            p=build();p['limits'][key]=value
            with self.assertRaises(ValueError):validate(p)
    def test_current_blueprint_cannot_be_changed_into_authorization(self):
        p=build();p['authorization']={'granted':True,'maxUsd':1}
        with self.assertRaises(ValueError):validate(p)
    def test_source_or_reference_changes_require_a_new_plan(self):
        p=build();p['inputs']['development']['sha256']='0'*64
        with self.assertRaises(ValueError):validate(p)
    def test_counterbalancing_uses_each_order_slot(self):
        p=build();positions={c['id']:set() for c in p['configurations'] if c['workflow']=='single'}
        for row in p['schedule']:
            if row['configuration'] in positions:positions[row['configuration']].add(row['slot'])
        self.assertTrue(all(v==set(range(5)) for v in positions.values()))
if __name__=='__main__':unittest.main()
