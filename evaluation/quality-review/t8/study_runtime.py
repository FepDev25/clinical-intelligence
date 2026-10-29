"""Freeze, invoke the study application, and replay outputs. Never opens dotenv or provider keys."""
import copy
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile
from study_plan import ROOT,HERE,build,validate


def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def read(p):return json.loads(Path(p).read_text())
def write_new(p,data):
    with Path(p).open('x') as f:json.dump(data,f,ensure_ascii=False,indent=2);f.write('\n')

def check_authorization(manifest,auth,digest):
    try:
        if manifest['version']!='t8-causal-study-1' or auth.get('granted') is not True or auth.get('manifestSha256')!=digest or not str(auth.get('approvalReference','')).strip():raise ValueError()
        text=auth['maxUsd']
        if not isinstance(text,str) or not re.fullmatch(r'(?:0|[1-9][0-9]*)(?:\.[0-9]{1,8})?',text):raise ValueError()
        cap=Decimal(text)
        if cap<=0 or cap>Decimal(manifest['budget']['proposedMaxUsd']):raise ValueError()
        return cap
    except (KeyError,TypeError,ValueError):raise ValueError('Explicit exact-manifest budget authorization required') from None

def verify_frozen(manifest,root=ROOT):
    root=Path(root).resolve();seen={}
    for item in manifest.get('artifacts',[]):
        rel=item['path'];p=(root/rel).resolve()
        if Path(rel).is_absolute() or not p.is_relative_to(root) or rel in seen or p.suffix not in {'.jar','.zip','.json','.java','.py','.txt','.md','.properties'} or not p.is_file() or sha(p)!=item['sha256']:raise ValueError('Frozen artifact missing, duplicate, foreign or changed')
        seen[rel]=p
    if not seen:raise ValueError('Frozen artifacts required')
    for entries in manifest.get('classpaths',{}).values():
        if not entries or len(set(entries))!=len(entries) or any(p not in seen or not p.endswith('.jar') for p in entries):raise ValueError('Unverified launch classpath')
    return seen

def make_thin(fat,out):
    with zipfile.ZipFile(fat) as original,zipfile.ZipFile(out,'x',compression=zipfile.ZIP_DEFLATED) as thin:
        entries=[n for n in original.namelist() if n.startswith('BOOT-INF/classes/') and not n.endswith('/')]
        if not entries:raise ValueError('Application classes absent')
        for n in entries:thin.writestr(n.removeprefix('BOOT-INF/classes/'),original.read(n))

def prepare(*,asset_relative='target/t8-frozen/prepared-v1',output=HERE/'freeze.json'):
    plan=build();validate(plan);asset=ROOT/asset_relative
    if asset.exists() or Path(output).exists():raise FileExistsError('Choose a new freeze directory and manifest version')
    app=ROOT/'target/clinical-intelligence-0.1.0-SNAPSHOT.jar'
    if not app.is_file():raise ValueError('Run mvn verify first')
    with zipfile.ZipFile(app) as z:
        for path in (ROOT/'target/classes').rglob('*'):
            if path.is_file() and z.read('BOOT-INF/classes/'+str(path.relative_to(ROOT/'target/classes')))!=path.read_bytes():raise ValueError('Packaged application is stale; run mvn verify')
    for f in plan['filters']:
        if not (ROOT/f['jarPath']).is_file() or sha(ROOT/f['jarPath'])!=f['jarSha256']:raise ValueError('Required original filter binary missing or changed')
    asset.mkdir(parents=True);lib=asset/'lib';lib.mkdir();study=asset/'study-tests.jar'
    with zipfile.ZipFile(study,'x',compression=zipfile.ZIP_DEFLATED) as z:
        for path in sorted((ROOT/'target/test-classes').rglob('*')):
            if path.is_file():z.write(path,str(path.relative_to(ROOT/'target/test-classes')))
    classpaths={}
    for name,fat in [('study',app),*[(f['version'],ROOT/f['jarPath']) for f in plan['filters']]]:
        thin=asset/(name+'.jar');make_thin(fat,thin);deps=[]
        with zipfile.ZipFile(fat) as z:
            for n in sorted(z.namelist()):
                if n.startswith('BOOT-INF/lib/') and n.endswith('.jar'):
                    data=z.read(n);digest=hashlib.sha256(data).hexdigest();path=lib/(digest[:16]+'-'+Path(n).name)
                    if not path.exists():path.write_bytes(data)
                    elif sha(path)!=digest:raise ValueError('Dependency hash collision')
                    deps.append(str(path.relative_to(ROOT)))
        classpaths[name]=[str(thin.relative_to(ROOT)),str(study.relative_to(ROOT)),*deps]
    for key in ('development','t2Proposals'):
        original=copy.deepcopy(plan['inputs'][key]);target=asset/('development.json' if key=='development' else 't2-reference-proposals.json')
        target.write_bytes((ROOT/original['path']).read_bytes());plan['inputs']['source'+key[0].upper()+key[1:]]=original
        plan['inputs'][key]={'path':str(target.relative_to(ROOT)),'sha256':sha(target)}
    sources=[]
    for base,pattern in [('src','*.java'),('src/main/resources','*'),('evaluation/quality-review/t8','*.py')]:
        sources.extend(p for p in (ROOT/base).rglob(pattern) if p.is_file() and '__pycache__' not in str(p))
    sources.extend(ROOT/p for p in ['evaluation/quality-review/score_extractions_v3.py','evaluation/tools/validate_corpus.py','evaluation/ANNOTATION.md','openapi/openapi.json','evaluation/quality-review/annotation-decisions.md'])
    with zipfile.ZipFile(asset/'sources.zip','x',compression=zipfile.ZIP_DEFLATED) as z:
        for p in sorted(set(sources)):z.write(p,str(p.relative_to(ROOT)))
    plan['classpaths']=classpaths;plan['sourceProvenance']={'baseCommit':read(HERE/'baseline.json')['commit'],'snapshotPath':str((asset/'sources.zip').relative_to(ROOT))}
    analysis=list((ROOT/'evaluation/quality-review/t8').glob('*.py'))+list((ROOT/'evaluation/tools').glob('*.py'))+list((ROOT/'openapi/tools').glob('*.py'))+[ROOT/'evaluation/quality-review/score_extractions_v3.py',ROOT/'openapi/openapi.json']
    plan['artifacts']=[{'path':str(p.relative_to(ROOT)),'sha256':sha(p)} for p in sorted(set([p for p in asset.rglob('*') if p.is_file()]+analysis))]
    verify_frozen(plan);write_new(output,plan)
    write_new(Path(output).with_name(Path(output).stem+'-authorization.example.json'),{'granted':False,'manifestSha256':sha(output),'maxUsd':None,'approvalReference':None,'scope':'t8-synthetic-causal-study'})
    return plan

def build_replay_inputs(manifest,cases,units):
    index={c['id']:c for c in cases};arms={a['id']:a for a in manifest['arms']};rows={f['version']:[] for f in manifest.get('filters',[{'version':'evidence-4'}])};failures=[]
    for expected in manifest['schedule']:
        unit=units.get(expected['generationId'])
        if unit is not None and any(unit.get(k)!=expected[k] for k in ('generationId','caseId','round','configuration')):raise ValueError('Foreign or changed generation unit')
        for aid in expected['evaluatedArms']:
            arm=arms[aid];binding={k:expected[k] for k in ('generationId','caseId','round')};binding['armId']=aid
            if unit is None or unit.get('status')!='complete':
                failures.append(dict(binding,status=0 if unit is None else 502,body={'code':'NOT_ATTEMPTED' if unit is None else unit.get('errorCode',unit.get('stopCode','STAGE_FAILED'))},inferenceExecuted=bool(unit and unit.get('inferenceExecuted'))));continue
            if not isinstance(unit.get('structuredProposalText'),str) or not unit['structuredProposalText']:raise ValueError('Complete unit without a proposal')
            rows[arm['filter']].append(dict(binding,filterVersion=arm['filter'],request=copy.deepcopy(index[expected['caseId']]['request']),structuredProposalText=unit['structuredProposalText'],promptVersion=unit['promptVersion'],model=unit['model'],inferenceExecuted=unit['inferenceExecuted']))
    return rows,failures

def java_command(manifest,profile,main,args,root=ROOT):
    return ['java','-cp',os.pathsep.join(str((root/p).resolve()) for p in manifest['classpaths'][profile]),main,*map(str,args)]

def validate_replay_bundle(manifest,cases,units,run,manifest_digest):
    run=Path(run);summary=read(run/'replay-summary.json')
    paths=[run/'evaluations.json',*sorted((run/'replay').glob('*.json'))]
    expected_files={str(p.relative_to(run)) for p in paths}
    if summary.get('manifestSha256')!=manifest_digest or set(summary.get('filesSha256',{}))!=expected_files or any(sha(run/p)!=v for p,v in summary['filesSha256'].items()):raise ValueError('Replay artifacts changed or foreign')
    grouped,failures=build_replay_inputs(manifest,cases,units);expected=list(failures);allowed_files={'evaluations.json'}
    for filt,inputs in grouped.items():
        if not inputs:continue
        source='replay/'+filt+'-input.json';out='replay/'+filt+'-output.json';allowed_files.update((source,out))
        if read(run/source)!=inputs:raise ValueError('Replay inputs not bound to generation units')
        outputs=read(run/out);bindings={(r['generationId'],r['armId']):r for r in inputs};seen=set()
        for row in outputs:
            key=(row.get('generationId'),row.get('armId'));binding=bindings.get(key)
            if key in seen or binding is None or any(row.get(k)!=binding[k] for k in ('caseId','round')) or row.get('sourceFilterVersion')!=filt:raise ValueError('Replay output bindings differ')
            seen.add(key)
        if seen!=set(bindings):raise ValueError('Incomplete replay outputs')
        expected.extend(outputs)
    if expected_files!=allowed_files:raise ValueError('Unexpected replay files')
    rows=read(run/'evaluations.json')
    if rows!=expected:raise ValueError('Evaluation rows differ from replay and deterministic failures')
    return rows


def replay_outputs(manifest,run,root=ROOT,*,manifest_digest=None):
    verify_frozen(manifest,root);run=Path(run)
    start=read(run/'run-start.json');terminal=read(run/'run-summary.json')
    if manifest_digest is None or start.get('manifestSha256')!=manifest_digest or terminal.get('manifestSha256')!=manifest_digest:raise ValueError('Run belongs to a foreign manifest')
    if sha(run/'events.jsonl')!=terminal.get('eventsSha256'):raise ValueError('Run journal changed')
    cases=read(root/manifest['inputs']['development']['path']);units={}
    paths=sorted((run/'units').glob('*.json'))
    if set(terminal.get('unitRecordsSha256',{}))!={str(p.relative_to(run)) for p in paths} or terminal.get('finishedUnits')!=len(paths):raise ValueError('Missing or extra unit artifact')
    if [read(p).get('generationId') for p in paths]!=[u['generationId'] for u in manifest['schedule'][:len(paths)]]:raise ValueError('Non-prefix or reordered run units')
    for path in paths:
        u=read(path)
        if u.get('manifestSha256')!=manifest_digest or terminal.get('unitRecordsSha256',{}).get(str(path.relative_to(run)))!=sha(path):raise ValueError('Changed or foreign unit record')
        if u['generationId'] in units:raise ValueError('Duplicate generation unit')
        units[u['generationId']]=u
    allowed={u['generationId'] for u in manifest['schedule']}
    if not set(units)<=allowed:raise ValueError('Unlisted generation unit')
    grouped,failures=build_replay_inputs(manifest,cases,units);results=list(failures)
    replay=run/'replay';replay.mkdir()
    for filt,rows in grouped.items():
        if not rows:continue
        source=replay/(filt+'-input.json');out=replay/(filt+'-output.json');write_new(source,rows)
        process=subprocess.run(java_command(manifest,filt,'com.clinicalintelligence.extraction.infrastructure.gemini.FilterReplayMain',[source,out],root),cwd=root,capture_output=True,timeout=600)
        if process.returncode!=0:raise ValueError('Frozen filter replay failed; no result substituted')
        actual=read(out)
        expected_keys={(r['generationId'],r['armId']) for r in rows}
        keys=[(r.get('generationId'),r.get('armId')) for r in actual]
        if len(keys)!=len(set(keys)) or set(keys)!=expected_keys or any(r.get('sourceFilterVersion')!=filt for r in actual):raise ValueError('Filter result coverage or version mismatch')
        results.extend(actual)
    write_new(run/'evaluations.json',results)
    files=[run/'evaluations.json',*sorted(replay.glob('*.json'))]
    write_new(run/'replay-summary.json',dict(manifestSha256=manifest_digest,filesSha256={str(p.relative_to(run)):sha(p) for p in files}))
    validate_replay_bundle(manifest,cases,units,run,manifest_digest)
    return results


def run(manifest_path,authorization_path,output):
    manifest=read(manifest_path);verify_frozen(manifest);check_authorization(manifest,read(authorization_path),sha(manifest_path))
    output=Path(output).resolve()
    if output.exists():raise ValueError('Run directory must be new; no automatic resume or retry')
    process=subprocess.run(java_command(manifest,'study','com.clinicalintelligence.extraction.infrastructure.gemini.CausalStudyMain',[Path(manifest_path).resolve(),Path(authorization_path).resolve(),output]),cwd=ROOT,capture_output=True)
    if process.returncode!=0 or not (output/'run-summary.json').is_file():raise ValueError('Study application failed; inspect safe local artifacts, no retry performed')
    replay_outputs(manifest,output,manifest_digest=sha(manifest_path))
    return output
