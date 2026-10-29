import copy
import json
from pathlib import Path
import unittest
from summarize_context import compare
HERE=Path(__file__).resolve().parent
class ComparisonTest(unittest.TestCase):
    def setUp(self):
        self.before=json.loads((HERE/'before.json').read_text())
    def test_identical_run_exposes_unblocked_fields_and_missing_warning(self):
        result=compare(self.before,copy.deepcopy(self.before))
        self.assertGreater(result['before']['negative']['leaked'],0)
        self.assertEqual(result['before'],result['after'])
        self.assertEqual([],result['development']['changes'])
        self.assertEqual(1,result['contradictions']['before']['missing'])
    def test_reject_changed_proposal(self):
        after=copy.deepcopy(self.before);after['cases'][0]['proposal']['mentions']=[]
        with self.assertRaises(ValueError):compare(self.before,after)
    def test_reject_changed_reference(self):
        after=copy.deepcopy(self.before);after['cases'][0]['expected'][0]['value']='affirmed'
        with self.assertRaises(ValueError):compare(self.before,after)
    def test_reject_missing_or_duplicate_case(self):
        for mutate in [lambda a:a['cases'].pop(),lambda a:a['cases'].append(a['cases'][0])]:
            after=copy.deepcopy(self.before);mutate(after)
            with self.assertRaises(ValueError):compare(self.before,after)
    def test_reject_invalid_accepted_response(self):
        after=copy.deepcopy(self.before);after['cases'][0]['response']['diagnoses'][0]['context']['assertion']['value']='invented'
        with self.assertRaises(ValueError):compare(self.before,after)
    def test_development_coverage_cannot_be_silently_reduced(self):
        after=copy.deepcopy(self.before);after['development'].pop()
        with self.assertRaises(ValueError):compare(self.before,after)
    def test_unknown_does_not_count_as_correct_known_label(self):
        after=copy.deepcopy(self.before)
        row=next(r for r in after['cases'] if r['id']=='positive-negation')
        row['response']['diagnoses'][0]['context']['assertion']={'value':'unknown','evidence':[]}
        result=compare(self.before,after)
        self.assertEqual(1,result['after']['positive']['falseDiscards'])
if __name__=='__main__':unittest.main()
