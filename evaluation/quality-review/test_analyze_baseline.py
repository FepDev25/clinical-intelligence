"""Behavioral tests: reject incomplete evidence and avoid double-counting context."""
import copy
import unittest
from analyze_baseline import analyze_baseline, read_json, verify_manifest
from pathlib import Path
from tempfile import TemporaryDirectory


def fixture():
    cases = [dict(id='one', split='reserved', cohort='core')]
    row = dict(id='one', split='reserved', round=1,
               entities={f: dict(tp=0, fp=0, fn=0) for f in ('diagnoses', 'medications', 'procedures')},
               issues=[], adjudications=[], operationalFailures=0, invariantViolations=0)
    row['entities']['procedures'] = dict(tp=0, fp=1, fn=1)
    row['issues'] = [dict(code='CONTEXT_MISMATCH', family='procedures', prediction=0,
                          expected=dict(subject='patient', temporality='current'),
                          actual=dict(subject='unknown', temporality='unknown'))]
    return [row], cases


class BaselineTests(unittest.TestCase):
    def test_two_dimensions_are_one_incorrect_entity(self):
        rows, cases = fixture()
        group = next(g for g in analyze_baseline(rows, cases, rounds=(1,))['groups'] if g['family'] == 'procedures')
        self.assertEqual(group['contextMismatchEntities'], 1)
        self.assertEqual(group['contextMismatchDimensions'], {'subject': 1, 'temporality': 1})
        self.assertEqual(group['alignedByAutomaticScorer'], 1)
        self.assertEqual(group['entities'], {'tp': 0, 'fp': 1, 'fn': 1})
        self.assertEqual(group['semanticAttribution'], 'not_determined_by_scores')

    def test_duplicate_score_rejected(self):
        rows, cases = fixture()
        with self.assertRaises(ValueError):
            analyze_baseline(rows + rows, cases, rounds=(1,))

    def test_missing_score_rejected(self):
        _, cases = fixture()
        with self.assertRaises(ValueError):
            analyze_baseline([], cases, rounds=(1,))

    def test_foreign_score_rejected(self):
        rows, cases = fixture()
        other = copy.deepcopy(rows[0]); other['id'] = 'foreign'
        with self.assertRaises(ValueError):
            analyze_baseline(rows + [other], cases, rounds=(1,))

    def test_duplicate_case_rejected(self):
        rows, cases = fixture()
        with self.assertRaises(ValueError):
            analyze_baseline(rows, cases + cases, rounds=(1,))

    def test_duplicate_context_issue_rejected(self):
        rows, cases = fixture(); rows[0]['issues'] *= 2
        with self.assertRaises(ValueError):
            analyze_baseline(rows, cases, rounds=(1,))

    def test_operational_failure_does_not_look_like_aligned_entity(self):
        rows, cases = fixture(); rows[0]['issues'] = []
        rows[0]['operationalFailures'] = 1
        rows[0]['entities']['procedures'] = dict(tp=0, fp=0, fn=1)
        group = next(g for g in analyze_baseline(rows, cases, rounds=(1,))['groups'] if g['family'] == 'procedures')
        self.assertEqual(group['alignedByAutomaticScorer'], 0)
        self.assertEqual(group['operationalFailureCases'], 1)

    def test_split_and_cohort_are_not_pooled(self):
        rows, cases = fixture()
        cases.append(dict(id='two', split='development', cohort='adversarial'))
        other = copy.deepcopy(rows[0]); other.update(id='two', split='development')
        groups = analyze_baseline(rows + [other], cases, rounds=(1,))['groups']
        self.assertEqual({(g['split'], g['cohort']) for g in groups},
                         {('reserved', 'core'), ('development', 'adversarial')})

    def test_missing_round_is_rejected(self):
        rows, cases = fixture()
        with self.assertRaises(ValueError):
            analyze_baseline(rows, cases)

    def test_empty_case_set_rejected(self):
        with self.assertRaises(ValueError):
            analyze_baseline([], [])

    def test_duplicate_round_configuration_rejected(self):
        rows, cases = fixture()
        with self.assertRaises(ValueError):
            analyze_baseline(rows, cases, rounds=(1, 1))

    def test_real_frozen_reserved_core_decomposition(self):
        root = Path(__file__).resolve().parents[2]
        rows = read_json(root/'evaluation/runs/2026-10-28-gemini38-v3/scores.json')
        cases = read_json(root/'evaluation/next/development.json') + read_json(root/'evaluation/corpus/v3/reserved.json')
        result = analyze_baseline(rows, cases)
        groups = [g for g in result['groups'] if g['split'] == 'reserved' and g['cohort'] == 'core' and g['family'] == 'procedures']
        self.assertEqual([g['alignedByAutomaticScorer'] for g in groups], [26, 26, 26])
        self.assertEqual([g['contextMismatchEntities'] for g in groups], [11, 9, 9])
        self.assertEqual(groups[0]['contextMismatchDimensions'], {'subject': 3, 'temporality': 9})
        self.assertEqual([g['entities']['tp'] for g in groups], [15, 17, 17])

    def test_duplicate_json_keys_rejected(self):
        with TemporaryDirectory() as d:
            p = Path(d)/'bad.json'; p.write_text('{"a":1,"a":2}')
            with self.assertRaises(ValueError):
                read_json(p)

    def test_changed_input_rejected(self):
        with TemporaryDirectory() as d:
            root = Path(d); (root/'input.json').write_text('{}')
            with self.assertRaises(ValueError):
                verify_manifest(root, {'sha256': {'input.json': '0'*64}})


if __name__ == '__main__':
    unittest.main()
