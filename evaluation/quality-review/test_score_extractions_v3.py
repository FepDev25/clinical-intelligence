"""Hand-derived behavior tests for the new offline evaluator; synthetic development only."""
import copy
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'evaluation/tools'))
from test_validate_corpus import case_fixture
from validate_corpus import annotation_response, Contract
from score_extractions_v3 import score_case, aggregate, clustered_uncertainty, canonical_entity


def fixture(medical=False):
    case = copy.deepcopy(json.loads((ROOT/'evaluation/next/development.json').read_text())[0]) if medical else case_fixture()
    return case, copy.deepcopy(annotation_response(case))


def review(**kwargs):
    return dict(decision='support_verified', notes='Synthetic support reviewed.', **kwargs)


class ScorerV3Tests(unittest.TestCase):
    def test_detection_is_separate_from_strict_context(self):
        case, body = fixture()
        body['diagnoses'][0]['context']['subject'] = {'value':'patient','evidence':[{'start':0,'end':18,'quote':'Diagnóstico: asma.'}]}
        r = score_case(case,200,body)
        self.assertEqual(r['entities']['diagnoses'],dict(tp=0,fp=1,fn=1))
        self.assertEqual(r['mentionAlignment']['diagnoses'],dict(tp=1,fp=0,fn=0))
        self.assertEqual(r['contextDimensions']['diagnoses']['subject'],dict(correct=0,incorrect=1,missing=0))
        self.assertEqual(r['criticalContextErrors'],1)

    def test_unmatched_literal_attribute_is_discrepancy_not_confirmed_invention(self):
        case,body=fixture(True)
        body['medications'][0]['name']['value']='Nébu'
        r=score_case(case,200,body)
        self.assertEqual(r['entities']['medications'],dict(tp=0,fp=1,fn=1))
        self.assertEqual(r['unbackedAttributes'],0)
        self.assertEqual(r['attributeSupport']['requiresReview'],4)
        self.assertEqual(r['attributeReferenceDiscrepancies'],4)
        self.assertFalse(aggregate([case],[r])['pass'])

    def test_explicit_attribute_rejection_is_confirmed_and_removes_tp(self):
        case,body=fixture(True)
        r=score_case(case,200,body,semantic_review=dict(decision='support_failed',notes='Dose belongs to another entity.',invalidAttributes=['/medications/0/dosage']))
        self.assertEqual(r['unbackedAttributes'],1)
        self.assertEqual(r['attributes']['dosage'],dict(tp=0,fp=1,fn=1,absent=0))

    def test_duplicate_penalty_and_attribute_metrics_do_not_depend_on_order(self):
        case,body=fixture(True)
        extra=copy.deepcopy(body['medications'][0]);extra['id']='extra';extra['duration']=None
        body['medications'].append(extra)
        a=score_case(case,200,body)
        body['medications'].reverse();b=score_case(case,200,body)
        self.assertEqual(a['entities'],b['entities']);self.assertEqual(a['attributes'],b['attributes'])
        self.assertEqual(a['entities']['medications'],dict(tp=1,fp=1,fn=0))
        self.assertEqual(a['mentionAlignment']['medications'],dict(tp=1,fp=1,fn=0))

    def test_ids_and_collection_order_do_not_change_counts(self):
        case,body=fixture(True)
        second=copy.deepcopy(body['diagnoses'][0]);second['id']='another'
        # Additional duplicate is deliberately penalized, regardless of arbitrary IDs.
        body['diagnoses'].append(second)
        a=score_case(case,200,body)
        body['diagnoses'].reverse();body['diagnoses'][0]['id']='new-id'
        b=score_case(case,200,body)
        self.assertEqual(a['entities'],b['entities'])
        self.assertEqual(canonical_entity(body['diagnoses'][0]),canonical_entity(body['diagnoses'][1]))

    def test_failed_operation_keeps_reference_denominators(self):
        case,_=fixture(True);r=score_case(case,504,{'code':'PROVIDER_TIMEOUT'})
        self.assertEqual(r['entities']['medications'],dict(tp=0,fp=0,fn=1))
        self.assertEqual(r['mentionAlignment']['medications'],dict(tp=0,fp=0,fn=1))
        self.assertEqual(r['contextDimensions']['medications']['subject']['missing'],1)

    def test_null_attributes_are_not_true_positives(self):
        case,body=fixture();r=score_case(case,200,body)
        self.assertEqual(r['attributes']['mentionedCode'],dict(tp=0,fp=0,fn=0,absent=1))

    def test_invalid_audit_pointer_rejected(self):
        case,body=fixture()
        with self.assertRaises(ValueError):score_case(case,200,body,semantic_review=dict(decision='support_failed',notes='bad pointer',invalidEntities=['/diagnoses/99']))

    def test_resolution_requires_reason_and_valid_index(self):
        case,body=fixture()
        for resolution in [dict(index=0,decision='supported',reason=''),dict(index=99,decision='supported',reason='invented')]:
            with self.subTest(resolution=resolution),self.assertRaises(ValueError):score_case(case,200,body,semantic_review=review(resolvedAdjudications=[resolution]))

    def test_verified_review_cannot_contain_rejections(self):
        case,body=fixture()
        with self.assertRaises(ValueError):score_case(case,200,body,semantic_review=review(invalidEntities=['/diagnoses/0']))

    def test_pending_audit_cannot_approve(self):
        case,body=fixture(True);r=score_case(case,200,body,semantic_review=dict(decision='pending',notes='Needs adjudication.'))
        self.assertFalse(aggregate([case],[r])['semanticAuditComplete'])
        self.assertFalse(aggregate([case],[r])['pass'])

    def test_perfect_automatic_result_does_not_replace_audit(self):
        case,body=fixture(True);r=score_case(case,200,body)
        self.assertTrue(aggregate([case],[r])['automaticCriteriaPass'])
        self.assertFalse(aggregate([case],[r])['pass'])

    def test_duplicate_cases_rejected_by_aggregation(self):
        case,body=fixture();r=score_case(case,200,body)
        with self.assertRaises(ValueError):aggregate([case,case],[r,r])

    def test_clustered_uncertainty_keeps_repeated_notes_together(self):
        case,body=fixture(True);r=score_case(case,200,body)
        cases=[dict(case, id=f'repetition-{i}') for i in range(3)]
        # Three observations of one origin are one resampling unit.
        result=clustered_uncertainty(cases,[r]*3,seed=17,draws=30)
        self.assertEqual(result['originGroups'],1)
        self.assertEqual(result['observations'],3)
        self.assertEqual(result['entities']['medications']['precisionInterval'],[1.0,1.0])
        self.assertEqual(result,clustered_uncertainty(cases,[r]*3,seed=17,draws=30))

    def test_rejected_attribute_on_duplicate_is_still_confirmed(self):
        case,body=fixture(True)
        extra=copy.deepcopy(body['medications'][0]);extra['id']='extra';body['medications'].append(extra)
        audit=dict(decision='support_failed',notes='Duplicate contains a rejected dosage.',invalidAttributes=['/medications/1/dosage'])
        self.assertEqual(score_case(case,200,body,semantic_review=audit)['unbackedAttributes'],1)

    def test_supported_discrepancy_requires_targeted_review(self):
        case,body=fixture(True);body['medications'][0]['name']['value']='Nébu'
        audit=review(attributeSupportReviews=[dict(target=f'/medications/0/{field}',decision='supported',reason='Literal attribute relation reviewed in this synthetic note.') for field in ('dosage','frequency','route','duration')])
        r=score_case(case,200,body,semantic_review=audit)
        self.assertEqual(r['attributeSupport']['requiresReview'],0)
        self.assertEqual(r['attributeSupport']['supportedByAudit'],4)
        self.assertEqual(r['entities']['medications']['tp'],0)

    def test_resolutions_bound_to_current_adjudication_list(self):
        case,body=fixture(True);body['medications'][0]['duration']['value']='3 días'
        audit=review(resolvedAdjudications=[dict(index=0,decision='supported',reason='Same duration phrase.')])
        # An in-range index still cannot clear a changed evaluator's issue.
        with self.assertRaises(ValueError):score_case(case,200,body,semantic_review=audit)
        import hashlib
        raw=score_case(case,200,body)
        audit['evaluatorVersion']='1.1.0-candidate'
        audit['automaticAdjudicationsSha256']=hashlib.sha256(json.dumps(raw['automaticAdjudications'],ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
        r=score_case(case,200,body,semantic_review=audit)
        self.assertEqual(r['adjudications'],[])
        audit['automaticAdjudicationsSha256']='0'*64
        with self.assertRaises(ValueError):score_case(case,200,body,semantic_review=audit)

    def test_alternative_context_quote_requires_adjudication(self):
        case,body=fixture(True)
        body['medications'][0]['context']['subject']['evidence']=[{'start':0,'end':2,'quote':'El'}]
        r=score_case(case,200,body)
        self.assertTrue(any(a['code']=='CONTEXT_SUPPORT_REVIEW' for a in r['adjudications']))
        self.assertFalse(aggregate([case],[r])['automaticCriteriaPass'])

    def test_same_dose_literal_in_other_clause_is_not_accepted(self):
        case,body=fixture(True);case['request']['clinicalNote']+=' Otra persona: 5 mg.'
        start=case['request']['clinicalNote'].rindex('5 mg')
        body['medications'][0]['dosage']['evidence']=[dict(start=start,end=start+4,quote='5 mg')]
        r=score_case(case,200,body)
        self.assertEqual(r['attributes']['dosage']['tp'],0)
        self.assertEqual(r['attributeSupport']['requiresReview'],1)
        self.assertEqual(r['unbackedAttributes'],0)

    def test_invalid_evidence_and_catalog_ids_block(self):
        for corrupt in ('span','catalog'):
            case,body=fixture()
            if corrupt=='span':body['diagnoses'][0]['name']['evidence'][0]['end']=999
            else:body['diagnoses'][0]['catalogMatch']=dict(status='matched',candidates=[dict(catalogId='alien',version='1',entryId='invented')])
            with self.subTest(corrupt=corrupt):
                r=score_case(case,200,body)
                self.assertEqual(r['invariantViolations'],1)
                self.assertFalse(aggregate([case],[r])['pass'])

    def test_wider_unique_name_quote_preserves_alignment(self):
        case,body=fixture();body['diagnoses'][0]['name']['evidence']=[dict(start=0,end=18,quote='Diagnóstico: asma.')]
        self.assertEqual(score_case(case,200,body)['entities']['diagnoses']['tp'],1)

    def test_ambiguous_anchor_cannot_choose_gold_by_context(self):
        case,body=fixture();case['request']['clinicalNote']='asma asma'
        case['expected']['diagnoses'][0]['name']['evidence']=[dict(start=0,end=4,quote='asma')]
        extra=copy.deepcopy(case['expected']['diagnoses'][0]);extra['id']='d2';extra['name']['evidence']=[dict(start=5,end=9,quote='asma')]
        case['expected']['diagnoses'].append(extra)
        body['diagnoses'][0]['name']['evidence']=[dict(start=0,end=9,quote='asma asma')]
        r=score_case(case,200,body)
        self.assertEqual(r['entities']['diagnoses'],dict(tp=0,fp=1,fn=2))
        self.assertEqual(r['mentionAlignment']['diagnoses'],dict(tp=0,fp=1,fn=2))
        self.assertTrue(any(a['code']=='AMBIGUOUS_ENTITY_ANCHOR' for a in r['adjudications']))

    def test_warning_target_grouping_preserves_coverage(self):
        case,body=fixture()
        base=dict(code='AMBIGUOUS_CONTEXT',message='review',source='/clinicalNote')
        case['expected']['requiredWarnings']=[dict(base,targets=['/diagnoses/0/context/assertion','/diagnoses/0/context/subject'])]
        body['warnings']=[dict(base,targets=['/diagnoses/0/context/assertion']),dict(base,targets=['/diagnoses/0/context/subject'])]
        body['processingStatus']='completed_with_warnings'
        r=score_case(case,200,body)
        self.assertEqual((r['missingWarnings'],r['additionalWarnings']),(0,0))

    def test_crossed_contradiction_relations_are_not_equivalent(self):
        case,body=fixture();base=dict(code='CONTRADICTORY_MENTIONS',message='review',source='/clinicalNote')
        a,b,c,d=('/diagnoses/0/context/assertion','/diagnoses/0/context/subject','/diagnoses/0/context/temporality','/diagnoses/0/name')
        case['expected']['requiredWarnings']=[dict(base,targets=[a,b]),dict(base,targets=[c,d])]
        body['warnings']=[dict(base,targets=[a,c]),dict(base,targets=[b,d])];body['processingStatus']='completed_with_warnings'
        r=score_case(case,200,body)
        self.assertEqual((r['missingWarnings'],r['additionalWarnings']),(2,2))

    def test_warning_targets_follow_entity_reordering(self):
        case,body=fixture(True)
        # Two distinct diagnoses with identical context, using literal names in note.
        other=copy.deepcopy(case['expected']['diagnoses'][0]);other['id']='d2'
        other['name']={'value':'paciente','evidence':[{'start':3,'end':11,'quote':'paciente'}]}
        case['expected']['diagnoses'].append(other)
        base=dict(code='AMBIGUOUS_CONTEXT',message='review',source='/clinicalNote')
        case['expected']['requiredWarnings']=[dict(base,targets=['/diagnoses/0/context/subject'])]
        body['diagnoses']=[copy.deepcopy(other),body['diagnoses'][0]]
        body['warnings']=[dict(base,targets=['/diagnoses/1/context/subject'])];body['processingStatus']='completed_with_warnings'
        r=score_case(case,200,body)
        self.assertEqual(r['missingWarnings'],0)
        self.assertEqual(r['entities']['diagnoses']['tp'],2)

    def test_integer_thresholds_and_missing_warnings_remain_blocking(self):
        case,body=fixture(True);r=score_case(case,200,body,semantic_review=review())
        for family in ('diagnoses','medications','procedures'):r['entities'][family]=dict(tp=19,fp=1,fn=2)
        self.assertTrue(aggregate([case],[r])['pass'])
        r['entities']['diagnoses']=dict(tp=18,fp=1,fn=1)
        self.assertFalse(aggregate([case],[r])['automaticCriteriaPass'])
        r['entities']['diagnoses']=dict(tp=19,fp=1,fn=2);r['missingWarnings']=1
        self.assertFalse(aggregate([case],[r])['pass'])

    def test_origin_bootstrap_does_not_split_rounds(self):
        case,body=fixture(True);good=score_case(case,200,body)
        bad=score_case(case,503,{'code':'PROVIDER_UNAVAILABLE'})
        result=clustered_uncertainty([dict(case,originGroup='good')]*3+[dict(case,originGroup='bad')]*3,[good]*3+[bad]*3,seed=1729,draws=100)
        self.assertEqual(result['originGroups'],2)
        self.assertEqual(result['entities']['medications']['recallInterval'],[0.0,1.0])

    def test_support_diagnostics_count_unique_rejections_and_pending_cases(self):
        case,body=fixture(True)
        audit=dict(decision='pending',notes='Entity rejected but another reference remains open.',invalidEntities=['/medications/0'],invalidContexts=['/medications/0'])
        r=score_case(case,200,body,semantic_review=audit)
        self.assertEqual(r['semanticSupport']['rejectedEntities'],1)
        self.assertEqual(r['semanticSupport']['pendingAuditCases'],1)
        self.assertFalse(r['semanticAuditVerified'])
        a=aggregate([case],[r]);self.assertEqual(a['semanticSupport']['pendingAuditCases'],1)

    def test_value_agreement_does_not_hide_audited_entity_rejection(self):
        case,body=fixture()
        r=score_case(case,200,body,semantic_review=dict(decision='support_failed',notes='Evidence unrelated.',invalidContexts=['/diagnoses/0']))
        self.assertEqual(r['contextDimensions']['diagnoses']['subject']['correct'],1)
        self.assertEqual(r['semanticSupport']['rejectedEntities'],1)
        self.assertEqual(r['entities']['diagnoses']['tp'],0)

    def test_audited_identical_duplicates_remain_order_invariant(self):
        case,body=fixture(True)
        duplicate=copy.deepcopy(body['medications'][0]);duplicate['id']='extra'
        body['medications'].append(duplicate)
        audit=dict(decision='support_failed',notes='One duplicate attribute is explicitly rejected.',invalidAttributes=['/medications/1/dosage'])
        a=score_case(case,200,body,semantic_review=audit)
        body['medications'].reverse();audit['invalidAttributes']=['/medications/0/dosage']
        b=score_case(case,200,body,semantic_review=audit)
        self.assertEqual(a['attributes'],b['attributes'])
        self.assertEqual(a['entities'],b['entities'])
        self.assertEqual(a['unbackedAttributes'],b['unbackedAttributes'])

    def test_duplicate_rejected_attribute_pointer_is_invalid_audit(self):
        case,body=fixture(True)
        with self.assertRaises(ValueError):
            score_case(case,200,body,semantic_review=dict(decision='support_failed',notes='Repeated pointer.',invalidAttributes=['/medications/0/dosage']*2))

    def test_discard_counts_unique_targets_independently_of_warning_grouping(self):
        case,body=fixture(True)
        body['medications'][0]['dosage']=None;body['medications'][0]['duration']=None
        base=dict(code='ATTRIBUTE_DISCARDED',message='discarded',source='/clinicalNote')
        targets=['/medications/0/dosage','/medications/0/duration']
        body['warnings']=[dict(base,targets=targets)];body['processingStatus']='completed_with_warnings'
        a=score_case(case,200,body)
        body['warnings']=[dict(base,targets=[t]) for t in targets]
        b=score_case(case,200,body)
        self.assertEqual(a['discardedAttributes'],2)
        self.assertEqual(a['discardedAttributes'],b['discardedAttributes'])
        self.assertEqual(a['discardedAttributeWarnings'],1)
        self.assertEqual(b['discardedAttributeWarnings'],2)

    def test_repeated_equal_doses_in_two_entities_require_association_review(self):
        case,body=fixture(True)
        note=case['request']['clinicalNote']+' Se prescribe Alfa, dosis 5 mg.'
        case['request']['clinicalNote']=note
        other=copy.deepcopy(case['expected']['medications'][0]);other['id']='m2'
        n=note.index('Alfa');d=note.rindex('5 mg')
        other['name']={'value':'Alfa','evidence':[dict(start=n,end=n+4,quote='Alfa')]}
        other['dosage']={'value':'5 mg','evidence':[dict(start=d,end=d+4,quote='5 mg')]}
        for k in ('frequency','route','duration'):other[k]=None
        case['expected']['medications'].append(other)
        body['medications'][0]['dosage']['evidence']=[dict(start=0,end=len(note),quote=note)]
        r=score_case(case,200,body)
        self.assertTrue(any(a['code']=='AMBIGUOUS_ATTRIBUTE_ASSOCIATION' for a in r['adjudications']))
        self.assertFalse(aggregate([case],[r])['automaticCriteriaPass'])

    def test_ambiguous_medication_prediction_still_counts_attribute_fp(self):
        case,body=fixture(True)
        note=case['request']['clinicalNote']+' Se prescribe Nébula, dosis 9 mg.'
        case['request']['clinicalNote']=note
        other=copy.deepcopy(case['expected']['medications'][0]);other['id']='m2'
        n=note.rindex('Nébula');d=note.rindex('9 mg')
        other['name']['evidence']=[dict(start=n,end=n+6,quote='Nébula')]
        other['dosage']={'value':'9 mg','evidence':[dict(start=d,end=d+4,quote='9 mg')]}
        case['expected']['medications'].append(other)
        body['medications'][0]['name']['evidence']=[dict(start=0,end=len(note),quote=note)]
        r=score_case(case,200,body)
        self.assertEqual(r['entities']['medications'],dict(tp=0,fp=1,fn=2))
        self.assertEqual(r['attributes']['dosage'],dict(tp=0,fp=1,fn=2,absent=0))
        self.assertEqual(r['attributeSupport']['requiresReview'],4)

    def test_empty_denominators_remain_na(self):
        case,body=fixture();case['expected']['diagnoses']=[];body['diagnoses']=[]
        r=score_case(case,200,body);a=aggregate([case],[r])
        self.assertIsNone(a['entities']['diagnoses']['precision']);self.assertFalse(a['pass'])

    def test_normalization_preserves_accents_and_nfc_case_spaces(self):
        self.assertEqual(Contract.normalize('  NE\u0301BULA\t'), 'nébula')
        self.assertNotEqual(Contract.normalize('Nebula'),Contract.normalize('Nébula'))

if __name__=='__main__':unittest.main()
