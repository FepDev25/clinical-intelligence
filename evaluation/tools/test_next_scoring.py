"""Tests of the next scoring rules using synthetic development fixtures only."""
import copy
import importlib.util
import unittest
from pathlib import Path

from test_validate_corpus import case_fixture
from validate_corpus import annotation_response, read_json, ROOT

spec = importlib.util.spec_from_file_location('score_next', ROOT / 'next/score_extractions_v2.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class NextScoringTests(unittest.TestCase):
    def test_crossed_contradiction_pairs_do_not_satisfy_gold_relationships(self):
        case = case_fixture()
        base = {'code': 'CONTRADICTORY_MENTIONS', 'message': 'review', 'source': '/clinicalNote'}
        a, b, c, d = ('/diagnoses/0/context/assertion', '/diagnoses/0/context/subject',
                      '/diagnoses/0/context/temporality', '/diagnoses/0/name')
        case['expected']['requiredWarnings'] = [dict(base, targets=[a, b]), dict(base, targets=[c, d])]
        body = copy.deepcopy(annotation_response(case))
        body['warnings'] = [dict(base, targets=[a, c]), dict(base, targets=[b, d])]
        result = module.score_case(case, 200, body)
        self.assertEqual((result['missingWarnings'], result['additionalWarnings']), (2, 2))

    def test_split_and_grouped_warnings_are_equivalent(self):
        case = case_fixture()
        base = {'code': 'AMBIGUOUS_CONTEXT', 'message': 'review', 'source': '/clinicalNote'}
        case['expected']['requiredWarnings'] = [dict(base, targets=['/diagnoses/0/context/assertion', '/diagnoses/0/context/subject'])]
        body = copy.deepcopy(annotation_response(case))
        body['warnings'] = [dict(base, targets=['/diagnoses/0/context/assertion']), dict(base, targets=['/diagnoses/0/context/subject'])]
        result = module.score_case(case, 200, body)
        self.assertEqual((result['missingWarnings'], result['additionalWarnings']), (0, 0))

    def test_wrong_warning_code_does_not_cover_required_target(self):
        case = case_fixture()
        case['expected']['requiredWarnings'] = [{'code': 'AMBIGUOUS_CONTEXT', 'message': 'review', 'targets': ['/diagnoses/0/context/subject'], 'source': '/clinicalNote'}]
        body = copy.deepcopy(annotation_response(case))
        body['warnings'][0]['code'] = 'CONTRADICTORY_MENTIONS'
        body['warnings'][0]['targets'].append('/diagnoses/0/context/assertion')
        result = module.score_case(case, 200, body)
        self.assertEqual((result['missingWarnings'], result['additionalWarnings']), (1, 1))

    def test_grouped_warning_covers_required_target_but_extra_target_needs_review(self):
        case = case_fixture()
        case['expected']['requiredWarnings'] = [{'code': 'AMBIGUOUS_CONTEXT', 'message': 'review', 'targets': ['/diagnoses/0/context/assertion'], 'source': '/clinicalNote'}]
        body = copy.deepcopy(annotation_response(case))
        body['warnings'][0]['targets'].append('/diagnoses/0/context/subject')
        result = module.score_case(case, 200, body)
        self.assertEqual(result['missingWarnings'], 0)
        self.assertEqual(result['additionalWarnings'], 1)
        self.assertEqual(result['adjudications'][-1]['targets'], (('diagnoses', 0, 'context/subject'),))

    def test_correct_prescription_occurrence_aligns_with_revised_reference(self):
        case = next(c for c in read_json(ROOT / 'next/development.json') if c['id'] == 'dev-outpatient-05')
        body = copy.deepcopy(annotation_response(case))
        note = case['request']['clinicalNote']
        start = note.rindex('Brisa')
        body['medications'][0]['name']['evidence'] = [{'start': start, 'end': start + 5, 'quote': 'Brisa'}]
        result = module.score_case(case, 200, body)
        self.assertEqual(result['entities']['medications'], {'tp': 1, 'fp': 0, 'fn': 0})
        self.assertEqual(result['attributes']['dosage']['tp'], 1)

    def test_package_mention_does_not_recover_prescription_or_validate_concentration(self):
        case = next(c for c in read_json(ROOT / 'next/development.json') if c['id'] == 'dev-outpatient-05')
        body = copy.deepcopy(annotation_response(case))
        body['medications'][0]['name']['evidence'] = [{'start': 77, 'end': 82, 'quote': 'Brisa'}]
        body['medications'][0]['dosage'] = {'value': '10 mg', 'evidence': [{'start': 90, 'end': 95, 'quote': '10 mg'}]}
        result = module.score_case(case, 200, body)
        self.assertEqual(result['entities']['medications'], {'tp': 0, 'fp': 1, 'fn': 1})
        self.assertEqual(result['unbackedAttributes'], 1)
