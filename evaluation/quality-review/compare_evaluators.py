"""Read-only old/new comparison on frozen synthetic v3 outputs; never runs inference."""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path

from analyze_baseline import read_json, verify_manifest
import score_extractions_v3 as new

ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('frozen_scoring_101',ROOT/'evaluation/next/score_extractions_v2.py')
old=importlib.util.module_from_spec(spec)
spec.loader.exec_module(old)


def record_hash(row):
    return hashlib.sha256(json.dumps(row,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()


def bind_audit(audit,records,freeze_hash,responses_hash):
    if (audit.get('schemaVersion')!='1.0.0' or not str(audit.get('reviewer','')).strip()
            or audit.get('reviewScope')!='synthetic-reference-support' or audit.get('clinicalReview') is not False
            or audit.get('freezeSha256')!=freeze_hash or audit.get('responsesSha256')!=responses_hash):
        raise ValueError('Audit bound to foreign freeze/responses or missing provenance')
    index={}
    for row in records:
        key=(row['split'],row['round'],row['caseId'])
        if key in index:raise ValueError('Duplicate response record')
        index[key]=row
    result={}
    for review in audit.get('reviews',[]):
        key=(review.get('split'),review.get('round'),review.get('caseId'))
        if key not in index or key in result or review.get('recordSha256')!=record_hash(index[key]):
            raise ValueError('Foreign, duplicate or changed audit record')
        if review.get('decision') not in {'support_verified','support_failed','pending','operation_failure'} or not str(review.get('notes','')).strip():
            raise ValueError('Incomplete review')
        result[key]=review
    return result


def migrate_review(case,row,review):
    """Translate only identical unique adjudications, never reuse raw indices.

    No clinical decision is created. Untranslatable historical resolutions are
    left unresolved in the candidate evaluator and reported individually.
    """
    if review is None:return None,[]
    stripped=copy.deepcopy(review);stripped['resolvedAdjudications']=[]
    args=(case,row['status'],row['body'],row.get('headers'))
    prior=old.score_case(*args,semantic_review=stripped)
    current=new.score_case(*args,semantic_review=stripped)
    old_issues=prior.get('automaticAdjudications',[])
    new_issues=current.get('automaticAdjudications',[])
    translated=[];unmapped=[];used=set()
    for resolution in review.get('resolvedAdjudications',[]):
        index=resolution.get('index')
        if type(index) is not int or not 0<=index<len(old_issues) or not str(resolution.get('reason','')).strip():
            raise ValueError('Historical resolution has invalid index or rationale')
        compatible=[i for i,a in enumerate(new_issues) if a==old_issues[index]]
        if len(compatible)!=1 or compatible[0] in used:
            unmapped.append(dict(originalIndex=index,automaticAdjudication=old_issues[index],reason='No unique identical adjudication in candidate evaluator'))
        else:
            used.add(compatible[0]);translated.append(dict(resolution,index=compatible[0]))
    stripped.update(resolvedAdjudications=translated,evaluatorVersion=new.VERSION,
                    automaticAdjudicationsSha256=new.adjudications_hash(new_issues))
    return stripped,unmapped


def _numeric_leaves(value,prefix=''):
    if isinstance(value,dict):
        return {p:n for k,v in value.items() for p,n in _numeric_leaves(v,prefix+'/'+k).items()}
    if isinstance(value,(int,float)) or value is None:
        return {prefix:value}
    return {}


def compare(*,draws=1000,seed=1729):
    baseline=read_json(HERE/'baseline-manifest.json')
    verify_manifest(ROOT,baseline)
    # Freeze the evaluator dependencies too; they are outside the T1 input set.
    freeze=read_json(HERE/'t3/input-manifest.json')
    verify_manifest(ROOT,freeze)
    campaign=ROOT/'evaluation/runs/2026-10-28-gemini38-v3'
    records=[json.loads(line) for line in (campaign/'responses.jsonl').read_text().splitlines()]
    cases=read_json(ROOT/'evaluation/next/development.json')+read_json(ROOT/'evaluation/corpus/v3/reserved.json')
    index={(c['split'],c['id']):c for c in cases}
    expected={(c['split'],r,c['id']) for c in cases for r in (1,2,3)}
    actual=[(r['split'],r['round'],r['caseId']) for r in records]
    if len(actual)!=len(set(actual)) or set(actual)!=expected:raise ValueError('Comparison requires exact complete rounds and case set')
    audit=read_json(ROOT/'evaluation/audits/2026-10-28-gemini38-v3-revision2/semantic-audit.json')
    digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
    reviews=bind_audit(audit,records,digest(campaign/'freeze.json'),digest(campaign/'responses.jsonl'))
    stored={(s['split'],s['round'],s['id']):s for s in read_json(campaign/'scores.json')}
    observations=[];differences=[];unmapped=[]
    for row in records:
        identity=(row['split'],row['round'],row['caseId'])
        case=index[(row['split'],row['caseId'])]
        request_hash=hashlib.sha256(json.dumps(case['request'],ensure_ascii=False,separators=(',',':')).encode()).hexdigest()
        if row['requestSha256']!=request_hash:raise ValueError('Record request differs from corpus')
        args=(case,row['status'],row['body'],row.get('headers'))
        old_auto=old.score_case(*args)
        # Reproduce the historical automatic engine before comparing changes.
        stored_case={k:v for k,v in stored[identity].items() if k not in {'split','round'}}
        if json.loads(json.dumps(old_auto))!=stored_case:raise ValueError('Frozen evaluator no longer reproduces stored automatic scores')
        new_auto=new.score_case(*args)
        historical=reviews.get(identity)
        migrated,left=migrate_review(case,row,historical)
        for item in left:unmapped.append(dict(split=row['split'],round=row['round'],caseId=case['id'],**item))
        old_audited=old.score_case(*args,semantic_review=historical) if historical is not None else old_auto
        new_audited=new.score_case(*args,semantic_review=migrated) if migrated is not None else new_auto
        for mode,before,after in [('automatic',old_auto,new_auto),('audited',old_audited,new_audited)]:
            prior={k:before[k] for k in ('entities','attributes','catalogs','unbackedAttributes','semanticSupportFailures','criticalContextErrors','invariantViolations','missingWarnings','additionalWarnings','discardedAttributes','operationalFailures','injectionViolations','discardedEntities','cleanEmpty','emptyWithWarnings')}
            following={k:after[k] for k in prior}
            prior['adjudicationCount']=len(before['adjudications']);following['adjudicationCount']=len(after['adjudications'])
            flat_a,flat_b=_numeric_leaves(prior),_numeric_leaves(following)
            changes={path:dict(before=flat_a.get(path),after=flat_b.get(path)) for path in sorted(set(flat_a)|set(flat_b)) if flat_a.get(path)!=flat_b.get(path)}
            if changes:differences.append(dict(split=row['split'],round=row['round'],caseId=case['id'],mode=mode,changes=changes))
        observations.append(dict(case=case,row=row,oldAutomatic=old_auto,newAutomatic=new_auto,oldAudited=old_audited,newAudited=new_audited))
    rounds={}
    for split in ('development','reserved'):
        for rnd in (1,2,3):
            selected=[o for o in observations if o['row']['split']==split and o['row']['round']==rnd]
            ordered=[o['case'] for o in selected]
            summary={}
            for mode in ('Automatic','Audited'):
                before=old.aggregate(ordered,[o['old'+mode] for o in selected])
                after=new.aggregate(ordered,[o['new'+mode] for o in selected])
                wanted=('entities','attributes','catalogs','unbackedAttributes','adjudications','semanticSupportFailures','semanticAuditComplete','automaticCriteriaPass','pass')
                summary[mode.lower()]=dict(before={k:before[k] for k in wanted},after={k:after[k] for k in (*wanted,'attributeReferenceDiscrepancies','attributeSupport','mentionAlignment','contextDimensions','semanticSupport','discardedAttributes','discardedAttributeWarnings')},core=dict(before=before['cohorts']['core']['entities'],after=after['cohorts']['core']['entities']))
            rounds[split+'/'+str(rnd)]=summary
    uncertainty={}
    for split in ('development','reserved'):
        for cohort in ('core','exploratory','adversarial'):
            selected=[o for o in observations if o['case']['split']==split and o['case']['cohort']==cohort]
            uncertainty[split+'/'+cohort]=new.clustered_uncertainty([o['case'] for o in selected],[o['newAudited'] for o in selected],seed=seed,draws=draws)
    return dict(evaluatorVersion=new.VERSION,scope='offline development comparison, not new inference or historical release decision',
                inputManifestSha256=digest(HERE/'t3/input-manifest.json'),historicalOperations=len(records),historicalAuditDecisions=len(reviews),
                historicalAutomaticScoresReproduced=True,rounds=rounds,caseCountDifferences=differences,
                unmappedHistoricalResolutions=unmapped,uncertainty=uncertainty,
                allStrictAutomaticEntityCountsEqual=all(o['oldAutomatic']['entities']==o['newAutomatic']['entities'] for o in observations),
                allStrictAuditedEntityCountsEqual=all(o['oldAudited']['entities']==o['newAudited']['entities'] for o in observations))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--draws',type=int,default=1000)
    parser.add_argument('--seed',type=int,default=1729)
    args=parser.parse_args()
    print(json.dumps(compare(draws=args.draws,seed=args.seed),indent=2,ensure_ascii=False))


if __name__=='__main__':main()
