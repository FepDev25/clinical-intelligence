import copy
import json
from pathlib import Path
import tempfile
import unittest
from study_runtime import verify_frozen,check_authorization,make_thin,build_replay_inputs
class RuntimeTest(unittest.TestCase):
    def test_missing_binary_changed_binary_and_unlisted_classpath_are_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);m={'artifacts':[{'path':'missing.jar','sha256':'0'*64}],'classpaths':{'study':['missing.jar']}}
            with self.assertRaises(ValueError):verify_frozen(m,root)
            (root/'missing.jar').write_text('changed')
            with self.assertRaises(ValueError):verify_frozen(m,root)
    def test_authorization_binds_budget_and_hash_and_is_not_implied(self):
        p={'version':'t8-causal-study-1','budget':{'proposedMaxUsd':'20'}}
        auth={'granted':False,'maxUsd':'20','manifestSha256':'abc','approvalReference':'synthetic'}
        with self.assertRaises(ValueError):check_authorization(p,auth,'abc')
        auth['granted']=True;self.assertEqual('20',str(check_authorization(p,auth,'abc')))
        with self.assertRaises(ValueError):check_authorization(p,auth,'foreign')
        auth['maxUsd']='21'
        with self.assertRaises(ValueError):check_authorization(p,auth,'abc')
    def test_thin_jar_only_contains_real_application_classes(self):
        import zipfile
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);fat=root/'fat.jar';thin=root/'thin.jar'
            with zipfile.ZipFile(fat,'w') as z:
                z.writestr('BOOT-INF/classes/example.class',b'compiled');z.writestr('BOOT-INF/lib/dependency.jar',b'dependency');z.writestr('META-INF/MANIFEST.MF',b'loader')
            make_thin(fat,thin)
            with zipfile.ZipFile(thin) as z:self.assertEqual(['example.class'],z.namelist())
            with self.assertRaises(FileExistsError):make_thin(fat,thin)
    def test_replay_never_substitutes_gold_for_missing_or_failed_proposals(self):
        plan={'arms':[{'id':'a','configuration':'c','filter':'evidence-4'}],'schedule':[{'generationId':'r1:x:c','caseId':'x','round':1,'configuration':'c','evaluatedArms':['a']} ]}
        cases=[{'id':'x','request':{'clinicalNote':'asma'},'expected':{'must_not_be_used':'gold'}}]
        rows,failures=build_replay_inputs(plan,cases,{})
        self.assertEqual([],rows['evidence-4']);self.assertEqual('NOT_ATTEMPTED',failures[0]['body']['code'])
        unit={'generationId':'r1:x:c','caseId':'x','round':1,'configuration':'c','status':'stage_failed','stages':[]}
        rows,failures=build_replay_inputs(plan,cases,{'r1:x:c':unit})
        self.assertEqual([],rows['evidence-4']);self.assertEqual('STAGE_FAILED',failures[0]['body']['code'])
    def test_replay_bundle_binds_hashes_inputs_and_failure_rows(self):
        from study_runtime import validate_replay_bundle,write_new,sha
        with tempfile.TemporaryDirectory() as d:
            run=Path(d);(run/'replay').mkdir()
            plan={'arms':[{'id':'a','configuration':'c','filter':'evidence-4'}],'schedule':[{'generationId':'r1:x:c','caseId':'x','round':1,'configuration':'c','evaluatedArms':['a']}]}
            cases=[{'id':'x','request':{'clinicalNote':'asma'}}]
            _,failures=build_replay_inputs(plan,cases,{})
            write_new(run/'evaluations.json',failures)
            write_new(run/'replay-summary.json',{'manifestSha256':'bound','filesSha256':{'evaluations.json':sha(run/'evaluations.json')}})
            self.assertEqual(failures,validate_replay_bundle(plan,cases,{},run,'bound'))
            failures[0]['body']['code']='FORGED_FAILURE'
            (run/'evaluations.json').write_text(json.dumps(failures))
            with self.assertRaises(ValueError):validate_replay_bundle(plan,cases,{},run,'bound')
            summary=json.loads((run/'replay-summary.json').read_text());summary['filesSha256']['evaluations.json']=sha(run/'evaluations.json')
            (run/'replay-summary.json').write_text(json.dumps(summary))
            with self.assertRaises(ValueError):validate_replay_bundle(plan,cases,{},run,'bound')
if __name__=='__main__':unittest.main()
