import copy
import unittest
from study_plan import build,ROOT
from study_runtime import read
from study_report import analyze,sensitivity,paired_difference,financials

class ReportTest(unittest.TestCase):
    def setUp(self):
        self.plan=build();self.cases=read(ROOT/self.plan['inputs']['development']['path'])
    def test_missing_attempts_keep_all_denominators_and_never_select_candidate(self):
        report=analyze(self.plan,self.cases,[],{},[],{},draws=10)
        self.assertEqual(936,report['expectedEvaluations']);self.assertEqual(936,report['notAttemptedEvaluations'])
        self.assertIsNone(report['provisionalCandidate']);self.assertFalse(report['releaseApproved'])
        first=report['references']['original']['arms']['base']['rounds']['1']
        self.assertEqual(39,first['final']['operationalFailures']);self.assertGreater(first['final']['entities']['diagnoses']['fn'],0)
        self.assertEqual(39,first['rawUnavailable']);self.assertFalse(first['final']['automaticCriteriaPass'])
    def test_raw_correct_context_lost_by_filter_is_reported_without_clinical_claim(self):
        from validate_corpus import annotation_response
        u=next(u for u in self.plan['schedule'] if u['caseId']==self.cases[0]['id'] and u['configuration']=='v2-low' and u['round']==1)
        raw=copy.deepcopy(annotation_response(self.cases[0]));final=copy.deepcopy(raw)
        final['diagnoses'][0]['context']['assertion']={'value':'unknown','evidence':[]}
        row=dict(generationId=u['generationId'],caseId=u['caseId'],round=1,armId='base',status=200,body=final,rawBody=raw)
        report=analyze(self.plan,self.cases,[row],{},[],{},draws=2)
        diag=report['references']['original']['arms']['base']['rounds']['1']
        self.assertEqual(1,diag['contextTransitions']['diagnoses']['assertion']['knownToUnknown'])
        self.assertEqual(1,diag['contextTransitions']['diagnoses']['assertion']['referenceAgreedThenChanged'])
        self.assertEqual(-1,diag['filterDeltas'][0]['entitiesFinalMinusRaw']['diagnoses']['tp'])
    def test_foreign_duplicate_or_misbound_evaluation_is_rejected(self):
        u=self.plan['schedule'][0];row=dict(generationId=u['generationId'],caseId=u['caseId'],round=u['round'],armId=u['evaluatedArms'][0],status=502,body={})
        with self.assertRaises(ValueError):analyze(self.plan,self.cases,[row,row],{},[],{},draws=2)
        row['caseId']='foreign'
        with self.assertRaises(ValueError):analyze(self.plan,self.cases,[row],{},[],{},draws=2)
    def test_sensitivity_copies_only_r1_and_validates_before_value(self):
        changes=read(ROOT/self.plan['inputs']['t2Proposals']['path']);before=copy.deepcopy(self.cases)
        alternate=sensitivity(self.cases,changes);self.assertEqual(before,self.cases);self.assertNotEqual(before,alternate)
        changes['changes'][0]['before']={'foreign':True}
        with self.assertRaises(ValueError):sensitivity(self.cases,changes)
    def test_paired_bootstrap_keeps_rounds_in_origin_cluster(self):
        cases=[dict(id='a',originGroup='x')]*3+[dict(id='b',originGroup='y')]*3
        def score(tp,fn):return {'entities':{f:dict(tp=tp,fp=0,fn=fn) for f in ('diagnoses','medications','procedures')}}
        result=paired_difference(cases,[score(1,1)]*6,[score(2,0)]*6,draws=50)
        self.assertEqual(2,result['originGroups']);self.assertEqual([0.5,0.5],result['entities']['diagnoses']['recallDifferenceInterval'])
    def test_equal_wrong_scores_still_expose_assertion_instability(self):
        from validate_corpus import annotation_response
        rows=[]
        for r,value in [(1,'suspected'),(2,'negated'),(3,'suspected')]:
            u=next(u for u in self.plan['schedule'] if u['caseId']==self.cases[0]['id'] and u['configuration']=='v2-low' and u['round']==r)
            body=copy.deepcopy(annotation_response(self.cases[0]));body['diagnoses'][0]['context']['assertion']['value']=value
            rows.append(dict(generationId=u['generationId'],caseId=u['caseId'],round=r,armId='base',status=200,body=body,rawBody=copy.deepcopy(body)))
        result=analyze(self.plan,self.cases,rows,{},[],{},draws=2)
        stable=result['references']['original']['arms']['base']['stabilityByScenario'][0]['final']['diagnoses'][0]
        self.assertFalse(stable['fields']['assertion']['stableAcrossAllRounds'])
        self.assertEqual(['suspected','negated','suspected'],stable['fields']['assertion']['valuesByRound'])
    def test_present_receipt_with_unknown_usage_is_still_unreconciled(self):
        events=[dict(kind='GENERATION_INTENT',stageId='x',reservedUsd='1.032192'),dict(kind='GENERATION_RESULT',stageId='x',chargedUsd='1.032192',usageReconciled=False,usage={'inputTokens':None})]
        result=financials(events,{'conservativeChargedUsd':'1.032192'})
        self.assertEqual(1,result['unreconciledGenerations'])
        self.assertFalse(result['attempts'][0]['receiptKnown'])
    def test_invalid_raw_attribute_cannot_create_false_diagnosis_filter_gain(self):
        from validate_corpus import annotation_response
        u=next(u for u in self.plan['schedule'] if u['caseId']==self.cases[0]['id'] and u['configuration']=='v2-low' and u['round']==1)
        raw=copy.deepcopy(annotation_response(self.cases[0]));final=copy.deepcopy(raw)
        raw['medications'][0]['dosage']['evidence'][0]['quote']='not in this synthetic note'
        final['medications'][0]['dosage']=None
        row=dict(generationId=u['generationId'],caseId=u['caseId'],round=1,armId='base',status=200,body=final,rawBody=raw)
        result=analyze(self.plan,self.cases,[row],{},[],{},draws=2)
        diag=result['references']['original']['arms']['base']['rounds']['1']
        self.assertIsNone(diag['filterDeltas'][0]['entitiesFinalMinusRaw'])
        self.assertEqual(1,diag['rawContractInvalid'])
    def test_finance_includes_lost_receipt_once_and_rejects_bad_totals(self):
        events=[dict(kind='GENERATION_INTENT',stageId='x',reservedUsd='1.032192'),dict(kind='GENERATION_RESULT',stageId='x',chargedUsd='0.01',usageReconciled=True,usage={'inputTokens':10}),dict(kind='GENERATION_INTENT',stageId='y',reservedUsd='1.032192')]
        result=financials(events,{'conservativeChargedUsd':'1.042192'})
        self.assertEqual('1.042192',result['conservativeChargedUsd']);self.assertEqual(1,result['unreconciledGenerations'])
        with self.assertRaises(ValueError):financials(events,{'conservativeChargedUsd':'0.01'})
if __name__=='__main__':unittest.main()
