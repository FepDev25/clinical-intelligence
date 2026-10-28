"""Mutation checks for traceable proposal coverage, not clinical correctness."""
import copy
import unittest
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
from validate_t2 import load_bundle, validate_bundle


class T2IntegrityTests(unittest.TestCase):
    def setUp(self):
        self.bundle = load_bundle()

    def test_complete_proposal_is_traceable(self):
        self.assertEqual(validate_bundle(self.bundle), {'groups': 27, 'historicalAttempts': 76, 'historicalPendingAttempts': 57, 'historicalRejectedAttempts': 19})

    def test_missing_attempt_is_rejected(self):
        bundle = copy.deepcopy(self.bundle)
        bundle['groups'][0]['historicalAttempts'].pop()
        with self.assertRaises(ValueError): validate_bundle(bundle)

    def test_duplicate_attempt_is_rejected(self):
        bundle = copy.deepcopy(self.bundle)
        bundle['groups'][0]['historicalAttempts'] *= 2
        with self.assertRaises(ValueError): validate_bundle(bundle)

    def test_changed_quote_is_rejected(self):
        bundle = copy.deepcopy(self.bundle)
        bundle['groups'][0]['issues'][0]['evidence'][0]['quote'] = 'not the source quote'
        with self.assertRaises(ValueError): validate_bundle(bundle)

    def test_fabricated_adjudication_is_rejected(self):
        bundle = copy.deepcopy(self.bundle); bundle['historicalAuditChanged'] = True
        with self.assertRaises(ValueError): validate_bundle(bundle)

    def test_misreported_audit_outcome_is_rejected(self):
        bundle = copy.deepcopy(self.bundle)
        bundle['groups'][0]['historicalAttempts'][0]['historicalAuditDecision'] = 'support_verified'
        with self.assertRaises(ValueError): validate_bundle(bundle)

    def test_changed_source_digest_is_rejected(self):
        bundle = copy.deepcopy(self.bundle)
        name = next(iter(bundle['sources']))
        bundle['sources'][name] = '0'*64
        with self.assertRaises(ValueError): validate_bundle(bundle)

    def test_wrong_source_corpus_is_rejected(self):
        bundle = copy.deepcopy(self.bundle)
        bundle['groups'][0]['sourceCorpus'] = 'evaluation/corpus/v3/reserved.json'
        with self.assertRaises(ValueError): validate_bundle(bundle)

    def test_changed_original_reference_is_rejected(self):
        bundle = copy.deepcopy(self.bundle)
        change = next(i for g in bundle['groups'] for i in g['issues'] if i.get('proposedReference'))
        change['originalReference']['value'] = 'historical'
        with self.assertRaises(ValueError): validate_bundle(bundle)


if __name__ == '__main__': unittest.main()
