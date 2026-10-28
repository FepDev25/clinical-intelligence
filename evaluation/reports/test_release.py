import copy
import unittest
from release import decide


def perfect():
    row = {'complete': True, 'semanticAuditComplete': True, 'operationalFailures': 0,
           'invariantViolations': 0, 'criticalContextErrors': 0, 'unbackedAttributes': 0,
           'injectionViolations': 0, 'semanticSupportFailures': 0, 'adjudications': 0, 'missingWarnings': 0,
           'cohorts': {'core': {'operationalFailures': 0, 'entities': {
               f: {'tp': 100, 'fp': 0, 'fn': 0} for f in ('diagnoses', 'medications', 'procedures')}}}}
    return {'allRequiredRoundsComplete': True, 'semanticAuditComplete': True, 'totalRecordedOperations': 234,
            'rounds': {s+'/'+str(n): copy.deepcopy(row) for s in ('development', 'reserved') for n in (1, 2, 3)}}


class ReleaseTests(unittest.TestCase):
    def test_development_critical_error_blocks_release_even_when_reserved_is_perfect(self):
        summary = perfect(); summary['rounds']['development/1']['injectionViolations'] = 1
        self.assertFalse(decide(summary)['pass'])

    def test_missing_development_audit_cannot_be_hidden_by_reserved_reviews(self):
        summary = perfect(); summary['semanticAuditComplete'] = False
        self.assertFalse(decide(summary)['pass'])
        summary = perfect(); summary['rounds']['development/1']['semanticAuditComplete'] = False
        self.assertFalse(decide(summary)['pass'])

    def test_exact_integer_thresholds_and_zero_denominators(self):
        summary = perfect(); summary['rounds']['reserved/2']['cohorts']['core']['entities']['medications'] = {'tp': 19, 'fp': 1, 'fn': 2}
        self.assertTrue(decide(summary)['pass'])
        summary['rounds']['reserved/2']['cohorts']['core']['entities']['medications']['fp'] = 2
        self.assertFalse(decide(summary)['pass'])
        summary['rounds']['reserved/2']['cohorts']['core']['entities']['medications'] = {'tp': 0, 'fp': 0, 'fn': 0}
        self.assertFalse(decide(summary)['pass'])

    def test_every_fixed_round_is_required_and_unknown_fields_fail_closed(self):
        summary = perfect(); del summary['rounds']['development/3']
        self.assertFalse(decide(summary)['pass'])
        summary = perfect(); del summary['rounds']['development/3']['invariantViolations']
        self.assertFalse(decide(summary)['pass'])

    def test_development_precision_is_reported_without_inventing_a_new_threshold(self):
        summary = perfect(); summary['rounds']['development/1']['cohorts']['core']['entities']['diagnoses'] = {'tp': 1, 'fp': 2, 'fn': 2}
        self.assertTrue(decide(summary)['pass'])


if __name__ == '__main__': unittest.main()
