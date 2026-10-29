"""T8 successor of the immutable T7 blueprint; preparation only, no remote execution."""
import copy
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[3]
HERE=Path(__file__).resolve().parent
PARENT=ROOT/'evaluation/quality-review/experiment-plan.json'

def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()

def build():
    parent=json.loads(PARENT.read_text());p=copy.deepcopy(parent)
    p.update(version='t8-causal-study-1',status='prepared-awaiting-specific-budget',inferenceExecuted=False,
        parent=dict(path=str(PARENT.relative_to(ROOT)),sha256=digest(PARENT)),authorization=dict(granted=False,maxUsd=None),
        budget=dict(proposedMaxUsd='20',reservationPerGenerationUsd={'gemini-3.8-flash':'1.032192','gemini-4-argon':'2.752512'},
            worstCaseCompleteUsd=str(Decimal('1.032192')*702+Decimal('2.752512')*117),
            reconciliation='full input price, output plus thinking; cache discounts do not release extra budget',
            unknownUsage='retain full reservation and stop',completionWithinCapGuaranteed=False),
        tariff=dict(verifiedDate='2026-10-28',source='https://ai.google.dev/gemini-api/docs/pricing',currency='USD',tier='standard',
            profiles={'gemini-3.8-flash':dict(inputPerMillion='0.75',outputAndThinkingPerMillion='3.75',cachedPerMillion='0.075',expires='2026-12-31'),
                      'gemini-4-argon':dict(inputPerMillion='2.00',outputAndThinkingPerMillion='10.00',cachedPerMillion='0.10',expires='2026-11-30')}),
        stopping=['budget headroom insufficient','unreconciled usage','transport or HTTP error','identity mismatch','artifact or audit failure','expired tariff'],
        semanticFailurePolicy='record schema or stage-boundary failure and continue if usage is reconciled; no retries or partial success',
        selection=dict(requireCompleteCampaign=True,releaseApproved=False,
            provisionalRule='all three rounds satisfy unchanged automatic criteria; exclude baseline; among eligible choose lowest conservative estimated total cost, then mean end-to-end latency; semantic adjudication remains required',
            ifNone='no candidate; report negative or incomplete result and next hypothesis'))
    # Freeze references; public request data are unchanged and disputed gold is not silently patched.
    source=ROOT/parent['inputs']['development']['path']
    if digest(source)!=parent['inputs']['development']['sha256']:raise ValueError('Development source changed')
    p['inputs']=dict(development=copy.deepcopy(parent['inputs']['development']),caseIds=list(parent['inputs']['caseIds']),
        t2Proposals=dict(path='evaluation/quality-review/t2/reference-proposals.json',sha256=digest(ROOT/'evaluation/quality-review/t2/reference-proposals.json')))
    p['configurations'].append(dict(id='model-argon-low',workflow='single',model='gemini-4-argon',thinkingLevel='LOW',promptVersion='extraction-gemini-v3-t7'))
    p['arms'].append(dict(id='model-comparator',configuration='model-argon-low',filter='evidence-4'))
    p['transport'].update(stream=True,operation='streamGenerateContent?alt=sse',transportVersion='t8-sse-1',continuationHops=0,reason='Argon direct non-streaming REST cap32K; common streaming preserves65536 for every arm')
    p['modelComparison']='Argon LOW versus Flash LOW, same prompt/filter/output cap; nominal levels do not imply equal compute'
    p['limits'].update(maxGenerateCalls=819,maxCountCalls=819,maxHttpCalls=1638)
    cases=json.loads(source.read_text());configs=p['configurations'];schedule=[];block=0
    for r in range(1,4):
        ordered=sorted(cases,key=lambda c:hashlib.sha256(f"1729:{r}:{c['id']}".encode()).hexdigest())
        for c in ordered:
            order=configs[block%6:]+configs[:block%6];block+=1
            for slot,config in enumerate(order):
                schedule.append(dict(caseId=c['id'],round=r,slot=slot,configuration=config['id'],generationId=f"r{r}:{c['id']}:{config['id']}",maxGenerateCalls=2 if config['workflow']=='two-stage' else 1,evaluatedArms=[a['id'] for a in p['arms'] if a['configuration']==config['id']]))
    p['schedule']=schedule
    p['unresolved']=['specific inference budget authorization','actual account access','T2 adjudication (not a bar to diagnostic synthetic work; no release claims)','independent clinical review for clinical/release claims']
    return p

def validate(p):
    if p!=build():raise ValueError('T8 design or input mismatch')
    keys=[(u['caseId'],u['round'],u['configuration']) for u in p['schedule']]
    expected={(c,r,s['id']) for c in p['inputs']['caseIds'] for r in (1,2,3) for s in p['configurations']}
    if len(keys)!=len(set(keys)) or set(keys)!=expected:raise ValueError('Incomplete or duplicated design')
    return dict(notes=39,rounds=3,arms=len(p['arms']),units=len(keys),maxGenerateCalls=sum(u['maxGenerateCalls'] for u in p['schedule']),maxHttpCalls=p['limits']['maxHttpCalls'],evaluations=sum(len(u['evaluatedArms']) for u in p['schedule']),authorized=False)

if __name__=='__main__':
    if sys.argv[1:]==['--check']:print(json.dumps(validate(json.loads((HERE/'plan.json').read_text())),indent=2))
    elif not sys.argv[1:]:print(json.dumps(build(),indent=2,ensure_ascii=False))
    else:raise SystemExit('No inference command; print design or --check')
