#!/usr/bin/env python3
"""Explicit opt-in T8 CLI. Preparation and verification never load provider credentials."""
import argparse
from pathlib import Path
import subprocess
from study_runtime import HERE,ROOT,prepare,read,verify_frozen,java_command,run
from study_report import report

def main():
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='command',required=True)
    p=sub.add_parser('prepare');p.add_argument('--assets',default='target/t8-frozen/prepared-v1');p.add_argument('--manifest',type=Path,default=HERE/'freeze.json')
    p=sub.add_parser('verify');p.add_argument('--manifest',type=Path,default=HERE/'freeze.json')
    p=sub.add_parser('run');p.add_argument('--manifest',type=Path,default=HERE/'freeze.json');p.add_argument('--authorization',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p=sub.add_parser('report');p.add_argument('--manifest',type=Path,default=HERE/'freeze.json');p.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    if args.command=='prepare':prepare(asset_relative=args.assets,output=args.manifest);print('Prepared; not authorized; no credentials or remote calls')
    elif args.command=='verify':
        m=read(args.manifest);verify_frozen(m)
        result=subprocess.run(java_command(m,'study','com.clinicalintelligence.extraction.infrastructure.gemini.CausalStudyMain',['--verify-only',args.manifest.resolve()]),cwd=ROOT,capture_output=True)
        if result.returncode:raise ValueError('Frozen Java preflight failed')
        print('Frozen Python and Java preflight passed; credentialsLoaded=false; networkCalls=0')
    elif args.command=='run':
        output=run(args.manifest,args.authorization,args.output);report(args.manifest,output);print('Study stopped/completed; diagnostic report saved; releaseApproved=false')
    else:report(args.manifest,args.output);print('Diagnostic report saved; releaseApproved=false')
if __name__=='__main__':
    try:main()
    except (ValueError,OSError,subprocess.SubprocessError):raise SystemExit('T8 operation failed; no automatic retry or substitute result') from None
