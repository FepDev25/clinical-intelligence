#!/usr/bin/env python3
"""Score development-only synthetic records locally; no network or file mutation."""
import argparse
import hashlib
import json
import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT / 'evaluation/tools'))
sys.path.insert(0, str(PROJECT / 'evaluation/next'))
from validate_corpus import ROOT, read_json, validate_cases, audit_coverage
from run_evaluation import validate_attempts
from score_extractions_v2 import score_case

ASSETS = {'evaluation/corpus/v1/development.json', 'evaluation/next/development.json',
          'evaluation/tools/score_extractions.py', 'evaluation/next/score_extractions_v2.py',
          'evaluation/next/analyze_development.py', 'evaluation/tools/validate_corpus.py',
          'evaluation/tools/run_evaluation.py', 'evaluation/case.schema.json',
          'openapi/openapi.json', 'openapi/tools/validate_contract.py'}


def verify_assets(manifest):
    hashes = read_json(manifest)['sha256']
    if set(hashes) != ASSETS:
        raise ValueError('Incomplete development asset manifest')
    for path, expected in hashes.items():
        if hashlib.sha256((PROJECT / path).read_bytes()).hexdigest() != expected:
            raise ValueError('Development asset changed: ' + path)


def analyze_rows(rows):
    verify_assets(PROJECT / 'evaluation/next/manifest.json')
    cases = read_json(PROJECT / 'evaluation/next/development.json')
    validate_cases(cases, 'development')
    audit_coverage(cases)
    if any(row.get('split') != 'development' for row in rows):
        raise ValueError('Only development records are allowed')
    validate_attempts(rows, {'development': cases})
    lookup = {case['id']: case for case in cases}
    return [{'split': row['split'], 'round': row['round'],
             **score_case(lookup[row['caseId']], row['status'], row['body'], row['headers'])}
            for row in rows]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('records', type=Path, help='Development-only JSONL records')
    args = parser.parse_args()
    with args.records.open(encoding='utf-8') as stream:
        rows = [json.loads(line) for line in stream if line.strip()]
    print(json.dumps({'version': '1.0.1-development', 'provisional': True,
                      'scores': analyze_rows(rows)}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
