"""Audit binding must reject stale or foreign evidence before offline comparison."""
import copy
import unittest
from compare_evaluators import bind_audit, record_hash


def fixture():
    row=dict(split='development',round=1,caseId='synthetic',status=200,body={})
    audit=dict(schemaVersion='1.0.0',reviewer='Synthetic reviewer',reviewScope='synthetic-reference-support',clinicalReview=False,
               freezeSha256='freeze',responsesSha256='responses',reviews=[dict(split='development',round=1,caseId='synthetic',recordSha256=record_hash(row),decision='pending',notes='Open.')])
    return row,audit


class AuditBindingTests(unittest.TestCase):
    def test_partial_audit_remains_partial(self):
        row,audit=fixture();audit['reviews']=[]
        self.assertEqual(bind_audit(audit,[row],'freeze','responses'),{})

    def test_correct_binding_retains_pending_review(self):
        row,audit=fixture()
        self.assertEqual(bind_audit(audit,[row],'freeze','responses')[('development',1,'synthetic')]['decision'],'pending')

    def test_changed_record_rejected(self):
        row,audit=fixture();row['status']=503
        with self.assertRaises(ValueError):bind_audit(audit,[row],'freeze','responses')

    def test_changed_freeze_or_responses_rejected(self):
        row,audit=fixture()
        for f,r in [('wrong','responses'),('freeze','wrong')]:
            with self.subTest(f=f,r=r),self.assertRaises(ValueError):bind_audit(audit,[row],f,r)

    def test_duplicate_review_rejected(self):
        row,audit=fixture();audit['reviews']*=2
        with self.assertRaises(ValueError):bind_audit(audit,[row],'freeze','responses')

    def test_foreign_review_rejected(self):
        row,audit=fixture();audit['reviews'][0]['caseId']='foreign'
        with self.assertRaises(ValueError):bind_audit(audit,[row],'freeze','responses')

    def test_duplicate_record_rejected(self):
        row,audit=fixture()
        with self.assertRaises(ValueError):bind_audit(audit,[row,row],'freeze','responses')

if __name__=='__main__':unittest.main()
