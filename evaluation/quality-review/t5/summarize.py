"""Compare fixed scripted proposals across two actual core builds; no model calls."""
import copy
import json
from pathlib import Path
import sys

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
sys.path.insert(0,str(ROOT/'evaluation/tools'))
from validate_corpus import Contract


def compare(before,after):
    rows=[]
    indexes=[]
    for run in (before,after):
        if run.get('inferenceExecuted') is not False:raise ValueError('This comparison is for local scripted proposals only')
        index={}
        for row in run['cases']:
            if row['id'] in index:raise ValueError('Duplicate case')
            index[row['id']]=row
        indexes.append(index)
    a,b=indexes
    if set(a)!=set(b) or not a:raise ValueError('Exact nonempty case coverage required')
    counters=[]
    for run in (a,b):
        count={'positive':dict(total=0,retained=0,falseDiscards=0,mismatches=0),
               'negative':dict(total=0,blocked=0,leaked=0),'residual':dict(total=0,blocked=0,leaked=0)}
        for cid,row in run.items():
            if any(row[key]!=a[cid][key] for key in ('note','proposal','catalogs','expected')):
                raise ValueError('Note, proposal, catalog or reference changed')
            catalogs=copy.deepcopy(row['catalogs'])
            for catalog in catalogs:catalog['kind']=catalog['kind'].lower()
            request=dict(clinicalNote=row['note'],locale='es',catalogs=catalogs)
            body=row['response']
            try:Contract().response(request,body,{'X-Request-Id':body['requestId'],'Cache-Control':'no-store'})
            except Exception as exc:raise ValueError('Invalid accepted response') from exc
            seen=set()
            for expected in row['expected']:
                if expected['path'] in seen:raise ValueError('Duplicate reference field')
                seen.add(expected['path'])
                try:actual=Contract.pointer(body,expected['path'])
                except (KeyError,IndexError,TypeError,ValueError) as exc:raise ValueError('Missing output field') from exc
                value=actual['value'] if actual is not None else None
                role=expected['role'];metric=count[role];metric['total']+=1
                if role=='positive':metric['retained' if value==expected['value'] else 'falseDiscards' if value is None else 'mismatches']+=1
                else:metric['blocked' if value is None else 'leaked']+=1
        counters.append(count)
    for cid in a:
        for expected in a[cid]['expected']:
            def value(row):
                item=Contract.pointer(row['response'],expected['path'])
                return item['value'] if item is not None else None
            rows.append(dict(caseId=cid,**expected,before=value(a[cid]),after=value(b[cid])))
    return dict(scope='synthetic field references; not extraction precision, clinical quality or model improvement',
                cases=len(a),before=counters[0],after=counters[1],fields=rows)


def main():
    original=compare(json.loads((HERE/'before.json').read_text()),json.loads((HERE/'after-reviewed.json').read_text()))
    supplemental=compare(json.loads((HERE/'review-before.json').read_text()),json.loads((HERE/'review-after.json').read_text()))
    totals={}
    for phase in ('before','after'):
        totals[phase]={role:{key:original[phase][role][key]+supplemental[phase][role][key] for key in original[phase][role]} for role in original[phase]}
    print(json.dumps(dict(version='association-study-summary-1',original=original,reviewRegressions=supplemental,total=totals,
                          caveat='No new pass threshold; positive controls and disclosed residual are both retained in reporting.'),indent=2,ensure_ascii=False))


if __name__=='__main__':main()
