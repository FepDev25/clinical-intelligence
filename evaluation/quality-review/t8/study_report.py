"""Diagnostic T8 reporting. Exposed development data cannot approve a release."""
import copy
from collections import defaultdict,Counter
from decimal import Decimal
import math
import random
import sys
sys.path.insert(0,str(__import__('pathlib').Path(__file__).resolve().parents[1]))
import score_extractions_v3 as scorer
from study_runtime import ROOT,read,sha,verify_frozen,write_new


def sensitivity(cases,proposals):
    alternate=copy.deepcopy(cases);index={c['id']:c for c in alternate};seen=set()
    for change in proposals.get('changes',[]):
        if change['split']!='development' or change['ruleId']!='R1':continue
        case=index.get(change['caseId'])
        if case is None:raise ValueError('Sensitivity contains foreign development case')
        key=(change['caseId'],change['path'])
        if key in seen:raise ValueError('Duplicate sensitivity change')
        seen.add(key);parts=change['path'].lstrip('/').split('/');target=case
        for part in parts[:-1]:target=target[int(part)] if isinstance(target,list) else target[part]
        last=int(parts[-1]) if isinstance(target,list) else parts[-1]
        if target[last]!=change['before']:raise ValueError('Sensitivity before-value mismatch')
        target[last]=copy.deepcopy(change['after'])
    return alternate


def paired_difference(cases,left,right,*,draws=1000):
    if not cases or len(cases)!=len(left) or len(cases)!=len(right) or draws<1:raise ValueError('Exact paired observations required')
    groups=defaultdict(list)
    for c,a,b in zip(cases,left,right):groups[c['originGroup']].append((a,b))
    rng=random.Random(1729);names=sorted(groups);values={f:{k:[] for k in ('precision','recall')} for f in scorer.FAMILIES}
    def differences(pairs):
        result={}
        for f in scorer.FAMILIES:
            metrics=[scorer.ratios({k:sum(p[i]['entities'][f][k] for p in pairs) for k in ('tp','fp','fn')}) for i in (0,1)]
            result[f]={k:metrics[1][k]-metrics[0][k] if all(m[k] is not None for m in metrics) else None for k in ('precision','recall')}
        return result
    for _ in range(draws):
        delta=differences([pair for name in rng.choices(names,k=len(names)) for pair in groups[name]])
        for f in values:
            for k in values[f]:
                if delta[f][k] is not None:values[f][k].append(delta[f][k])
    def interval(v):
        v=sorted(v);return [v[math.floor((len(v)-1)*.025)],v[math.ceil((len(v)-1)*.975)]] if v else None
    point=differences(list(zip(left,right)))
    return dict(method='paired origin-cluster percentile bootstrap; right minus left; descriptive only',seed=1729,draws=draws,observations=len(cases),originGroups=len(groups),entities={f:{**{k+'Difference':point[f][k] for k in values[f]},**{k+'DifferenceInterval':interval(v) for k,v in values[f].items()},'validDraws':{k:len(v) for k,v in values[f].items()}} for f in values})


def financials(events,terminal):
    intents={};receipts={};counts=Counter()
    for e in events:
        counts[e['kind']]+=1;stage=e['stageId']
        if e['kind']=='GENERATION_INTENT':
            if stage in intents:raise ValueError('Duplicate spend intent')
            intents[stage]=e
        if e['kind']=='GENERATION_RESULT':
            if stage not in intents or stage in receipts:raise ValueError('Foreign or duplicate receipt')
            receipts[stage]=e
    charged=Decimal('0');attempts=[]
    for stage,intent in intents.items():
        receipt=receipts.get(stage);amount=Decimal(receipt['chargedUsd'] if receipt else intent['reservedUsd'])
        if amount<0 or amount>Decimal(intent['reservedUsd']):raise ValueError('Invalid stage cost')
        charged+=amount;attempts.append(dict(stageId=stage,conservativeChargedUsd=str(amount),receiptPresent=receipt is not None,receiptKnown=bool(receipt and receipt.get('usageReconciled') is True),usage=receipt.get('usage') if receipt else None,latencyMillis=receipt.get('latencyMillis') if receipt else None,reportedModelVersion=receipt.get('reportedModelVersion') if receipt else None,completion=receipt.get('completion') if receipt else None))
    if terminal and charged!=Decimal(terminal['conservativeChargedUsd']):raise ValueError('Ledger does not reconcile; no cost conclusion permitted')
    return dict(conservativeChargedUsd=str(charged),generationAttempts=len(intents),unreconciledGenerations=sum(not a['receiptKnown'] for a in attempts),httpIntentCounts={k:counts[k] for k in ('COUNT_INTENT','GENERATION_INTENT')},attempts=attempts,warning='Conservative tariff estimate, not account invoice; shared filter replays add no remote cost')


def context_transitions(case,raw,final):
    """Literal occurrence alignment, diagnostic only; never adjudicates semantic support."""
    result={}
    for family in scorer.FAMILIES:
        def anchor(e):return (e['name']['value'],tuple(sorted((v['start'],v['end']) for v in e['name']['evidence'])))
        gold=defaultdict(list);accepted=defaultdict(list)
        for e in case['expected'][family]:gold[anchor(e)].append(e)
        for e in final[family]:accepted[anchor(e)].append(e)
        fields=(*scorer.CONTEXT_FIELDS,*(('prescriptionStatus',) if family=='medications' else ('performanceStatus',) if family=='procedures' else ()))
        result[family]={f:dict(changed=0,knownToUnknown=0,referenceAgreedThenChanged=0,entityRemoved=0,ambiguousAlignment=0) for f in fields}
        rawanchors=Counter(anchor(e) for e in raw[family])
        for e in raw[family]:
            key=anchor(e);targets=accepted[key];refs=gold[key]
            for field in fields:
                metric=result[family][field]
                if rawanchors[key]!=1 or len(targets)>1 or len(refs)>1:metric['ambiguousAlignment']+=1;continue
                if not targets:metric['entityRemoved']+=1;continue
                a=scorer.state(e)[field];b=scorer.state(targets[0])[field]
                metric['changed']+=a!=b;metric['knownToUnknown']+=a!='unknown' and b=='unknown'
                metric['referenceAgreedThenChanged']+=bool(refs and a==scorer.state(refs[0])[field] and a!=b)
    return result


def scenario_stability(cases,arm,rowmap):
    """Compare actual anchored field values, not only correctness scores."""
    result=[]
    for case in cases:
        scenario={'caseId':case['id']}
        for stage in ('raw','final'):
            families={};bodies=[]
            for r in (1,2,3):
                row=rowmap[(case['id'],r,arm)]
                bodies.append(row.get('rawBody') if stage=='raw' else row['body'] if row['status']==200 else None)
            for family in scorer.FAMILIES:
                indices=[];anchors=set()
                for body in bodies:
                    index=defaultdict(list)
                    for e in (body or {}).get(family,[]):
                        key=(e['name']['value'],tuple(sorted((v['start'],v['end']) for v in e['name']['evidence'])))
                        index[key].append(e);anchors.add(key)
                    indices.append(index)
                mentions=[]
                for key in sorted(anchors):
                    matches=[index[key] for index in indices];unambiguous=all(body is not None and len(m)==1 for body,m in zip(bodies,matches))
                    fields=(*scorer.CONTEXT_FIELDS,*scorer.TEXT_FIELDS,*(('prescriptionStatus',) if family=='medications' else ('performanceStatus',) if family=='procedures' else ()))
                    metrics={}
                    for field in fields:
                        values=[]
                        for matched in matches:
                            if len(matched)!=1:values.append(None);continue
                            e=matched[0];value=scorer.state(e)[field] if field in scorer.state(e) else (e.get(field) or {}).get('value')
                            values.append(value)
                        metrics[field]=dict(valuesByRound=values,stableAcrossAllRounds=len(set(__import__('json').dumps(v,sort_keys=True) for v in values))==1 if unambiguous else None)
                    mentions.append(dict(name=key[0],anchors=[list(v) for v in key[1]],stageAvailableByRound=[b is not None for b in bodies],presentCountByRound=[len(m) for m in matches],comparisonCompleteAndUnambiguous=unambiguous,fields=metrics))
                families[family]=mentions
            scenario[stage]=families
        result.append(scenario)
    return result


def analyze(plan,cases,rows,units,events,terminal,*,proposals=None,draws=1000):
    if len({c['id'] for c in cases})!=len(cases) or set(c['id'] for c in cases)!=set(plan['inputs']['caseIds']):raise ValueError('Exact development case set required')
    bindings={};rowmap={}
    for u in plan['schedule']:
        for a in u['evaluatedArms']:
            key=(u['caseId'],u['round'],a)
            if key in bindings:raise ValueError('Duplicate planned evaluation')
            bindings[key]=u
    for row in rows:
        key=(row['caseId'],row['round'],row['armId']);expected=bindings.get(key)
        if key in rowmap or expected is None or row['generationId']!=expected['generationId']:raise ValueError('Duplicate, foreign or misbound evaluation')
        rowmap[key]=row
    # Missing rows are explicit failures, preserving every planned denominator.
    missing=0
    for key,u in bindings.items():
        if key not in rowmap:
            rowmap[key]=dict(caseId=key[0],round=key[1],armId=key[2],generationId=u['generationId'],status=0,body={'code':'NOT_ATTEMPTED'});missing+=1
    refs={'original':cases}
    if proposals is not None:refs['proposed-R1-sensitivity-not-adjudicated']=sensitivity(cases,proposals)
    result=dict(version='t8-report-1',inferenceExecuted=any(u.get('inferenceExecuted') is True for u in units.values()),simulationOnly=not any(u.get('inferenceExecuted') is True for u in units.values()),evaluatorVersion=scorer.VERSION,expectedEvaluations=len(bindings),notAttemptedEvaluations=sum(r['status']==0 for r in rowmap.values()),missingEvaluationRecords=missing,releaseApproved=False,provisionalCandidate=None,semanticAuditComplete=False,completeCampaign=bool(terminal.get('complete')) and len(units)==len(plan['schedule']) and all(u.get('inferenceExecuted') is True for u in units.values()) and missing==0,references={},finance=financials(events,terminal),limitations=['All development cases are exposed; no independent generalization claim','T2 adjudication and semantic audit remain open','Nominal thinking levels across models do not imply equal compute','No production switch; frozen aliases may lack immutable model version'])
    complete=result['completeCampaign'];base=plan['arms'][0]['id'];costs={a['id']:Decimal('0') for a in plan['arms']};latencies={a['id']:[] for a in plan['arms']}
    receipt_cost={a['stageId']:Decimal(a['conservativeChargedUsd']) for a in result['finance']['attempts']}
    for u in plan['schedule']:
        cost=sum((v for stage,v in receipt_cost.items() if stage.startswith(u['generationId']+'/')),Decimal('0'))
        for a in u['evaluatedArms']:
            costs[a]+=cost
            actual=units.get(u['generationId'])
            if actual:latencies[a].append(actual['latencyMillis']+rowmap[(u['caseId'],u['round'],a)].get('filterLatencyMillis',0))
    for reference,reference_cases in refs.items():
        out={'arms':{},'pairedComparisons':{}};all_scores={}
        for arm in plan['arms']:
            aid=arm['id'];rounds={};flat=[];flat_cases=[];scenarios=[]
            for r in (1,2,3):
                scored=[];raw=[];unavailable=0;raw_invalid=0;deltas=[];transitions={}
                for case in reference_cases:
                    row=rowmap[(case['id'],r,aid)];s=scorer.score_case(case,row['status'],row['body'],row.get('headers'));scored.append(s)
                    rawbody=row.get('rawBody');rawscore=scorer.score_case(case,200,rawbody) if rawbody is not None else scorer.score_case(case,0,{'code':'RAW_UNAVAILABLE'})
                    unavailable+=rawbody is None;raw_invalid+=rawbody is not None and bool(rawscore['invariantViolations']);raw.append(rawscore)
                    delta={f:{k:s['entities'][f][k]-rawscore['entities'][f][k] for k in ('tp','fp','fn')} for f in scorer.FAMILIES} if rawbody is not None and not rawscore['invariantViolations'] and row['status']==200 and not s['invariantViolations'] else None
                    deltas.append(dict(caseId=case['id'],status=row['status'],rawAvailable=rawbody is not None,entitiesFinalMinusRaw=delta))
                    if rawbody is not None and row['status']==200 and not s['invariantViolations'] and not rawscore['invariantViolations']:
                        changes=context_transitions(case,rawbody,row['body'])
                        for family,fields in changes.items():
                            for field,counts in fields.items():
                                bucket=transitions.setdefault(family,{}).setdefault(field,{k:0 for k in counts})
                                for k,v in counts.items():bucket[k]+=v
                    scenarios.append(dict(caseId=case['id'],round=r,status=row['status'],scores=s,rawScores=rawscore,filterDelta=delta))
                rounds[str(r)]=dict(final=scorer.aggregate(reference_cases,scored),raw=scorer.aggregate(reference_cases,raw),rawUnavailable=unavailable,rawContractInvalid=raw_invalid,rawComparisonWarning="Raw contract failures keep denominators but cannot establish filter entity gains/losses; inspect native proposal and field diagnostics",filterDeltas=deltas,contextTransitions=transitions)
                flat.extend(scored);flat_cases.extend(reference_cases)
            all_scores[aid]=flat
            out['arms'][aid]=dict(rounds=rounds,stabilityByScenario=scenario_stability(reference_cases,aid,rowmap),uncertainty=scorer.clustered_uncertainty(flat_cases,flat,draws=draws),scenarios=scenarios,estimatedRemoteCostUsd=str(costs[aid]),meanEndToEndMillis=sum(latencies[aid])/len(latencies[aid]) if latencies[aid] else None,automaticCriteriaAllRounds=all(r['final']['automaticCriteriaPass'] for r in rounds.values()))
        # Exact same notes/rounds, complete origin clusters. Pipeline comparison is descriptive.
        pairs=[(base,a['id']) for a in plan['arms'][1:]]
        combined=next((a['id'] for a in plan['arms'] if a['configuration']=='v3-low' and a['filter']=='evidence-4'),None)
        if combined:pairs.extend((combined,a['id']) for a in plan['arms'] if a['configuration'] not in ('v2-low','v3-low'))
        for left,right in pairs:
            out['pairedComparisons'][left+' -> '+right]=paired_difference(flat_cases,all_scores[left],all_scores[right],draws=draws)
        result['references'][reference]=out
    eligible=[a for a in result['references']['original']['arms'] if a!=base and result['references']['original']['arms'][a]['automaticCriteriaAllRounds']]
    if complete and eligible:result['provisionalCandidate']=min(eligible,key=lambda a:(costs[a],sum(latencies[a])/len(latencies[a]) if latencies[a] else float('inf'),a))
    result['candidateDecision']='Provisional automatic candidate only; semantic audit and independent evaluation required' if result['provisionalCandidate'] else 'No candidate: incomplete campaign or unchanged criteria not met'
    return result


def report(manifest_path,run):
    from pathlib import Path
    manifest=read(manifest_path);verify_frozen(manifest);run=Path(run);digest=sha(manifest_path)
    start=read(run/'run-start.json');terminal=read(run/'run-summary.json')
    if start['manifestSha256']!=digest or terminal['manifestSha256']!=digest or sha(run/'events.jsonl')!=terminal['eventsSha256']:raise ValueError('Foreign run or modified journal')
    paths=list((run/'units').glob('*.json'));hashes=terminal['unitRecordsSha256']
    if set(hashes)!={str(p.relative_to(run)) for p in paths} or any(sha(run/p)!=v for p,v in hashes.items()):raise ValueError('Modified unit artifacts')
    units={read(p)['generationId']:read(p) for p in paths}
    from study_runtime import validate_replay_bundle
    cases=read(ROOT/manifest['inputs']['development']['path']);proposals=read(ROOT/manifest['inputs']['t2Proposals']['path'])
    rows=validate_replay_bundle(manifest,cases,units,run,digest)
    events=[__import__('json').loads(line) for line in (run/'events.jsonl').read_text().splitlines()]
    result=analyze(manifest,cases,rows,units,events,terminal,proposals=proposals)
    result['manifestSha256']=digest;result['evaluationsSha256']=sha(run/'evaluations.json')
    write_new(run/'report.json',result)
    text=f"# T8 — informe diagnóstico\n\nCampaña completa: {result['completeCampaign']}. Evaluaciones planificadas: {result['expectedEvaluations']}; no intentadas: {result['notAttemptedEvaluations']}.\n\n{result['candidateDecision']}. Candidata provisional: {result['provisionalCandidate']}. Aprobación de entrega: **no**.\n\nCoste conservador estimado: USD {result['finance']['conservativeChargedUsd']}. Generaciones sin recibo conciliable: {result['finance']['unreconciledGenerations']}.\n\nLas métricas por brazo, ronda, escenario, dimensión contextual, catálogo y propuesta cruda/final están en `report.json`, con intervalos descriptivos agrupados por origen. La sensibilidad R1 no adjudica las referencias. El corpus de desarrollo expuesto no demuestra generalización.\n"
    (run/'report.md').open('x').write(text)
    return result
