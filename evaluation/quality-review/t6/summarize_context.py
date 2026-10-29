"""Offline comparison of fixed context proposals against two real application builds."""
import json
from pathlib import Path
import sys
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
sys.path.insert(0,str(ROOT/'evaluation/tools'))
from validate_corpus import Contract


def index(rows):
    result={}
    for row in rows:
        if row['id'] in result:raise ValueError('Duplicate case')
        result[row['id']]=row
    return result


def response(row,request):
    body=row['response']
    try:Contract().response(request,body,{'X-Request-Id':body['requestId'],'Cache-Control':'no-store'})
    except Exception as exc:raise ValueError('Invalid response') from exc
    return body


def compare(before,after):
    if before.get('inferenceExecuted') is not False or after.get('inferenceExecuted') is not False:raise ValueError('Offline only')
    a,b=index(before['cases']),index(after['cases'])
    if set(a)!=set(b) or not a:raise ValueError('Exact nonempty coverage required')
    for cid in a:
        for key in ('note','proposal','expected','expectedFindings','expectedContradiction'):
            if a[cid][key]!=b[cid][key]:raise ValueError('Changed fixed input/reference')
    metrics=[];relations=[];fields=[]
    for run in (a,b):
        count={'positive':dict(total=0,retained=0,falseDiscards=0,mismatches=0),'negative':dict(total=0,blocked=0,leaked=0),'residual':dict(total=0,blocked=0,leaked=0)}
        relation=dict(expected=0,detected=0,missing=0,falseWarnings=0)
        for cid,row in run.items():
            body=response(row,dict(clinicalNote=row['note'],locale='es'))
            if sum(len(body[k]) for k in ('diagnoses','medications','procedures'))!=row['expectedFindings']:raise ValueError('Entity loss or duplication')
            actual=any(w['code']=='CONTRADICTORY_MENTIONS' for w in body['warnings'])
            expected=row['expectedContradiction'];relation['expected']+=expected;relation['detected']+=actual and expected;relation['missing']+=expected and not actual;relation['falseWarnings']+=actual and not expected
            seen=set()
            for e in row['expected']:
                if e['path'] in seen:raise ValueError('Duplicate field reference')
                seen.add(e['path'])
                try:value=Contract.pointer(body,e['path'])
                except (KeyError,IndexError,ValueError,TypeError) as exc:raise ValueError('Missing field') from exc
                m=count[e['role']];m['total']+=1
                if e['role']=='positive':m['retained' if value==e['value'] else 'falseDiscards' if value=='unknown' else 'mismatches']+=1
                else:m['blocked' if value=='unknown' else 'leaked']+=1
        metrics.append(count);relations.append(relation)
    for cid,row in a.items():
        for e in row['expected']:fields.append(dict(caseId=cid,**e,before=Contract.pointer(row['response'],e['path']),after=Contract.pointer(b[cid]['response'],e['path'])))
    dev={'cases':0,'baselineKnownValueFields':0,'unchangedKnownValueFields':0,'changes':[]}
    if 'development' in before or 'development' in after:
        da,db=index(before['development']),index(after['development'])
        source=index(json.loads((ROOT/'evaluation/next/development.json').read_text()))
        if set(da)!=set(db) or set(da)!=set(source):raise ValueError('Exact development coverage required')
        dev['cases']=len(da)
        def values(node,path=''):
            if isinstance(node,dict):
                for k,v in node.items():
                    if k=='value':yield path+'/value',v
                    else:yield from values(v,path+'/'+k)
            elif isinstance(node,list):
                for i,v in enumerate(node):yield from values(v,path+'/'+str(i))
        for cid,x in da.items():
            y=db[cid]
            if x['note']!=y['note'] or x['proposal']!=y['proposal'] or x['note']!=source[cid]['request']['clinicalNote']:raise ValueError('Changed development input')
            for row in (x,y):response(row,source[cid]['request'])
            for collection in ('diagnoses','medications','procedures'):
                if len(x['response'][collection])!=len(y['response'][collection]):raise ValueError('Development entity loss/duplication')
                old=dict(values(x['response'][collection],'/'+collection));new=dict(values(y['response'][collection],'/'+collection))
                if old.keys()!=new.keys():raise ValueError('Value field coverage changed')
                for path,value in old.items():
                    if value!='unknown':
                        dev['baselineKnownValueFields']+=1
                        dev['unchangedKnownValueFields']+=value==new[path]
                    if value!=new[path]:dev['changes'].append(dict(caseId=cid,path=path,before=value,after=new[path],adjudication='not resolved by this software study'))
    return dict(cases=len(a),before=metrics[0],after=metrics[1],contradictions=dict(before=relations[0],after=relations[1]),development=dev,fields=fields)


def main():
    initial=compare(json.loads((HERE/'before.json').read_text()),json.loads((HERE/'after.json').read_text()))
    review=compare(json.loads((HERE/'review-before.json').read_text()),json.loads((HERE/'review-after.json').read_text()))
    total={phase:{role:{k:initial[phase][role][k]+review[phase][role][k] for k in initial[phase][role]} for role in initial[phase]} for phase in ('before','after')}
    print(json.dumps(dict(scope='scripted synthetic field references, no model or clinical quality claim',initial=initial,supplemental=review,total=total),ensure_ascii=False,indent=2))
if __name__=='__main__':main()
