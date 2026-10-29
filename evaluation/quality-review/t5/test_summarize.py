"""Counterexamples for before/after field-regression comparison integrity."""
import copy
import json
from pathlib import Path
import unittest
from summarize import compare

HERE=Path(__file__).resolve().parent

class StudyComparisonTests(unittest.TestCase):
    def setUp(self):
        self.before=json.loads((HERE/'before.json').read_text())
        self.after=json.loads((HERE/'after-reviewed.json').read_text())

    def test_original_fixed_study_counts(self):
        result=compare(self.before,self.after)
        self.assertEqual(result['after']['positive']['retained'],24)
        self.assertEqual(result['after']['positive']['falseDiscards'],0)
        self.assertEqual(result['after']['negative']['blocked'],14)
        self.assertEqual(result['before']['negative']['leaked'],14)
        self.assertEqual(result['after']['residual']['leaked'],1)

    def test_changed_proposal_cannot_look_like_filter_improvement(self):
        altered=copy.deepcopy(self.after);altered['cases'][0]['proposal']['notices']=[{}]
        with self.assertRaises(ValueError):compare(self.before,altered)

    def test_changed_reference_cannot_look_like_improvement(self):
        altered=copy.deepcopy(self.after);altered['cases'][0]['expected'][0]['value']='different'
        with self.assertRaises(ValueError):compare(self.before,altered)

    def test_duplicate_case_rejected(self):
        altered=copy.deepcopy(self.after);altered['cases'].append(altered['cases'][0])
        with self.assertRaises(ValueError):compare(self.before,altered)

    def test_missing_case_rejected(self):
        altered=copy.deepcopy(self.after);altered['cases'].pop()
        with self.assertRaises(ValueError):compare(self.before,altered)

    def test_broken_output_cannot_count_as_correct_discard(self):
        altered=copy.deepcopy(self.after);del altered['cases'][0]['response']['medications'][0]['dosage']
        with self.assertRaises(ValueError):compare(self.before,altered)

if __name__=='__main__':unittest.main()
