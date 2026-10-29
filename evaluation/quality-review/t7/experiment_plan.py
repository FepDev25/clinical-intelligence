"""Prepared causal design and integrity check only. No client, credentials or execution path."""
import hashlib
import json
from collections import Counter
from pathlib import Path
import sys
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]

def hashed(path):
    return dict(path=path,sha256=hashlib.sha256((ROOT/path).read_bytes()).hexdigest())

def build():
    development='evaluation/next/development.json'
    cases=json.loads((ROOT/development).read_text())
    if len(cases)!=39 or len({c['id'] for c in cases})!=39:raise ValueError('Expected intact development revision')
    configs=[]
    for cid,prompt,level in [('v2-low','extraction-gemini-v2','LOW'),('v3-low','extraction-gemini-v3-t7','LOW'),('v3-medium','extraction-gemini-v3-t7','MEDIUM'),('v3-high','extraction-gemini-v3-t7','HIGH')]:
        configs.append(dict(id=cid,workflow='single',model='gemini-3.8-flash',promptVersion=prompt,thinkingLevel=level))
    configs.append(dict(id='two-stage-low',workflow='two-stage',model='gemini-3.8-flash',thinkingLevel='LOW',prompts=['detection-gemini-v1-t7','classification-gemini-v1-t7']))
    arms=[]
    for aid,config,filt in [('base','v2-low','evidence-2'),('prompt-only','v3-low','evidence-2'),('filter-only','v2-low','evidence-4'),('combined','v3-low','evidence-4'),('thinking-medium','v3-medium','evidence-4'),('thinking-high','v3-high','evidence-4'),('two-stage','two-stage-low','evidence-4')]:
        arms.append(dict(id=aid,configuration=config,filter=filt))
    schedule=[];block=0
    for round_id in range(1,4):
        ordered=sorted(cases,key=lambda c:hashlib.sha256(f"1729:{round_id}:{c['id']}".encode()).hexdigest())
        for case in ordered:
            order=configs[block%5:]+configs[:block%5];block+=1
            for slot,config in enumerate(order):
                schedule.append(dict(caseId=case['id'],round=round_id,slot=slot,configuration=config['id'],generationId=f"r{round_id}:{case['id']}:{config['id']}",maxGenerateCalls=2 if config['workflow']=='two-stage' else 1,
                    evaluatedArms=[a['id'] for a in arms if a['configuration']==config['id']]))
    sources=['src/main/java/com/clinicalintelligence/extraction/infrastructure/gemini/GeminiGeneration.java','src/main/java/com/clinicalintelligence/extraction/infrastructure/gemini/GeminiProtocol.java','src/main/java/com/clinicalintelligence/extraction/infrastructure/gemini/GeminiProvider.java','src/main/java/com/clinicalintelligence/extraction/infrastructure/gemini/GeminiStageBoundary.java','src/main/java/com/clinicalintelligence/http/ApiConfiguration.java','src/test/java/com/clinicalintelligence/extraction/infrastructure/gemini/GeminiTwoStageExperiment.java','src/main/resources/gemini/extraction-v2.txt','src/main/resources/gemini/extraction-v3-t7.txt','src/main/resources/gemini/detection-v1-t7.txt','src/main/resources/gemini/classification-v1-t7.txt','src/main/resources/gemini/proposal-v1.schema.json','src/main/resources/gemini/detection-v1-t7.schema.json','evaluation/quality-review/score_extractions_v3.py','evaluation/tools/validate_corpus.py','openapi/openapi.json','evaluation/quality-review/annotation-decisions.md','docs/adr/0007-annotation-scope-and-temporal-context.md']
    old=json.loads((ROOT/'evaluation/quality-review/t5/baseline.json').read_text())
    current=json.loads((HERE/'baseline.json').read_text())
    return dict(version='t7-causal-blueprint-1',status='prepared-not-authorized',inferenceExecuted=False,
        authorization=dict(granted=False,maxUsd=None),
        unresolved=['T2 reference adjudication and proposed rules','T8 runner freeze and spend guard','account tier and current tariff verification','separate comparator model capability/pricing profile','independent clinical review'],
        inputs=dict(development=hashed(development),caseIds=[c['id'] for c in cases],sources=[hashed(p) for p in sources]),
        filters=[dict(version='evidence-2',commit=old['baselineCommit'],jarPath=old['jarPath'],jarSha256=old['jarSha256']),dict(version='evidence-4',commit=current['commit'],jarPath=current['jarPath'],jarSha256=current['jarSha256'])],
        transport=dict(endpoint='https://generativelanguage.googleapis.com/v1beta/',requestVersion='gemini-generate-content-t7-1',samplingParameters='omitted',includeThoughts=False,schemaFormat='responseJsonSchema',stream=False,tools=False,remoteVerified=False),
        configurations=configs,arms=arms,
        limits=dict(rounds=3,outputTokens=65536,responseBytes=16777216,deadlineMsPerGeneration=120000,maxTwoStageDeadlineMs=240000,concurrency=1,retries=0,maxGenerateCalls=702,maxCountCalls=702,maxHttpCalls=1404),
        ordering=dict(seed=1729,notes='SHA256(seed:round:caseId)',configurations='rotate by block ordinal',concurrencyPurpose='avoid interleaving paired comparisons'),
        scoring=dict(candidate='evaluation/quality-review/score_extractions_v3.py',strictThresholds=dict(precision=0.95,recall=0.90,perTypeAndRound=True,zeroTolerancesUnchanged=True),
            rawAndSanitized=True,pairedFilterReplay=True,disputes='retain denominators and separately disclose T2 sensitivity; no adjudication by score',
            metrics=['strict TP/FP/FN','mention alignment','context dimensions','attribute support and false discards','unknown transitions by stage','contradiction warnings','all operational failures','latency by stage and total','input/output/thought/cached tokens','cost including failed attempts','effective model and request identities'],
            uncertainty='paired resampling by originGroup; no treating repeated rounds as independent notes',
            selection='all rounds and predeclared criteria; no best-round selection, selective retries or automatic promotion'),
        capture=dict(syntheticOnly=True,ordinaryHttpCapture=False,rawProposals=True,failedStages=True,retainThoughts=False),
        schedule=schedule)

def validate(plan):
    if plan!=build():raise ValueError('Blueprint/source mismatch; freeze a new explicit version, not a silent edit')
    schedule=plan['schedule'];ids=[r['generationId'] for r in schedule]
    if len(set(ids))!=len(ids):raise ValueError('Duplicate generation unit')
    coverage=Counter((r['caseId'],r['round'],r['configuration']) for r in schedule)
    expected={(cid,r,c['id']) for cid in plan['inputs']['caseIds'] for r in range(1,4) for c in plan['configurations']}
    if set(coverage)!=expected or any(v!=1 for v in coverage.values()):raise ValueError('Incomplete coverage')
    calls=sum(r['maxGenerateCalls'] for r in schedule)
    evaluations=sum(len(r['evaluatedArms']) for r in schedule)
    if calls!=702 or evaluations!=819:raise ValueError('Design cardinality mismatch')
    for f in plan['filters']:
        path=ROOT/f['jarPath']
        if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest()!=f['jarSha256']:raise ValueError('Frozen filter binary changed')
    return dict(notes=39,rounds=3,generationUnits=len(schedule),maxGenerateCalls=calls,maxCountCalls=calls,maxHttpCalls=2*calls,evaluations=evaluations,executionReady=False)

if __name__=='__main__':
    if sys.argv[1:]==['--check']:
        print(json.dumps(validate(json.loads((ROOT/'evaluation/quality-review/experiment-plan.json').read_text())),indent=2))
    elif not sys.argv[1:]:print(json.dumps(build(),ensure_ascii=False,indent=2))
    else:raise SystemExit('Use no arguments to print blueprint, or --check; no inference command exists')
