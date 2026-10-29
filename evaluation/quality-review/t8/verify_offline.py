"""Real frozen-filter proof with a deliberately incorrect synthetic model proposal; no network."""
import argparse
from pathlib import Path
import subprocess
from study_runtime import ROOT,read,write_new,verify_frozen,java_command,sha,replay_outputs

def verify(manifest_path,output):
    m=read(manifest_path);verify_frozen(m);output=Path(output).resolve();output.mkdir()
    note='El paciente niega asma.'
    context={k:dict(value='unknown',evidence=[]) for k in ('assertion','subject','temporality')}
    context['assertion']=dict(value='affirmed',evidence=[dict(quote=note,occurrence=0)])
    native=dict(diagnoses=[dict(name=dict(value='asma',evidence=[dict(quote='asma',occurrence=0)]),mentionedCode=None,context=context)],medications=[],procedures=[],notices=[])
    outputs={}
    for filt in ('evidence-2','evidence-4'):
        request=dict(request={'clinicalNote':note},structuredProposalText=__import__('json').dumps(native),model='gemini-3.8-flash',promptVersion='extraction-gemini-v2',filterVersion=filt,inferenceExecuted=False)
        inp=output/(filt+'-input.json');out=output/(filt+'-output.json');write_new(inp,request)
        p=subprocess.run(java_command(m,filt,'com.clinicalintelligence.extraction.infrastructure.gemini.FilterReplayMain',[inp.resolve(),out.resolve()]),cwd=ROOT,capture_output=True)
        if p.returncode:raise ValueError('Real frozen filter probe failed')
        outputs[filt]=read(out)
        assert outputs[filt]['sourceFilterVersion']==filt
        assert outputs[filt]['rawBody']['diagnoses'][0]['context']['assertion']['value']=='affirmed'
        assert outputs[filt]['body']['processing']['executionMode']=='simulated'
    assert outputs['evidence-2']['body']['diagnoses'][0]['context']['assertion']['value']=='affirmed'
    assert outputs['evidence-4']['body']['diagnoses'][0]['context']['assertion']['value']=='unknown'
    # Current filter must not masquerade as the old baseline.
    p=subprocess.run(java_command(m,'evidence-4','com.clinicalintelligence.extraction.infrastructure.gemini.FilterReplayMain',[(output/'evidence-2-input.json').resolve(),(output/'wrong-output.json').resolve()]),cwd=ROOT,capture_output=True)
    assert p.returncode==2 and not (output/'wrong-output.json').exists()
    proof=dict(manifestSha256=sha(manifest_path),inferenceExecuted=False,networkCalls=0,credentialsLoaded=False,realOldAndNewBinaries=True,rawAffirmedBoth=True,oldFinal='affirmed',newFinal='unknown',wrongClasspathRejected=True,artifacts=[dict(path=str(p.relative_to(ROOT)),sha256=sha(p)) for p in sorted(output.glob('*.json'))])
    write_new(output/'proof.json',proof);return proof
def verify_report(manifest_path,output):
    """One explicitly simulated empty proposal; proves plumbing, not model quality."""
    from study_report import report
    import json
    m=read(manifest_path);verify_frozen(m);digest=sha(manifest_path);output=Path(output).resolve();output.mkdir();(output/'units').mkdir()
    first=m['schedule'][0];config=next(c for c in m['configurations'] if c['id']==first['configuration'])
    unit=dict(first,status='complete',inferenceExecuted=False,syntheticData=True,latencyMillis=0,stages=[],manifestSha256=digest,structuredProposalText=json.dumps(dict(diagnoses=[],medications=[],procedures=[],notices=[])),promptVersion=config.get('promptVersion','detection-gemini-v1-t7'),model=config['model'])
    write_new(output/'units/0000.json',unit);write_new(output/'run-start.json',dict(manifestSha256=digest,inferenceExecuted=False,simulationOnly=True))
    (output/'events.jsonl').write_text('')
    write_new(output/'run-summary.json',dict(manifestSha256=digest,inferenceExecuted=False,complete=False,finishedUnits=1,stopCode='OFFLINE_SIMULATED_PROBE',conservativeChargedUsd='0',unitRecordsSha256={'units/0000.json':sha(output/'units/0000.json')},eventsSha256=sha(output/'events.jsonl')))
    rows=replay_outputs(m,output,manifest_digest=digest);result=report(manifest_path,output)
    assert len(rows)==936 and result['notAttemptedEvaluations']==936-len(first['evaluatedArms'])
    assert not result['completeCampaign'] and result['provisionalCandidate'] is None and result['releaseApproved'] is False
    assert result['simulationOnly'] and not result['inferenceExecuted']
    assert result['finance']['conservativeChargedUsd']=='0'
    return dict(simulationOnly=True,inferenceExecuted=False,networkCalls=0,credentialsLoaded=False,plannedEvaluations=936,actualUnitProposals=1,missingEvaluationsPreserved=result['notAttemptedEvaluations'],noCandidate=True,reportSha256=sha(output/'report.json'))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('manifest',type=Path);parser.add_argument('output',type=Path);args=parser.parse_args()
    verify(args.manifest,args.output);proof=verify_report(args.manifest,args.output/'simulated-run');write_new(args.output/'report-proof.json',proof);print('Real frozen filters and partial simulated report passed; no credentials or network')
