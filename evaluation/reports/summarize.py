"""Descriptive metadata only; does not score, approve or call a provider.

Usage: python evaluation/reports/summarize.py RUN_DIRECTORY
p95 uses nearest rank; counts missing telemetry separately from known totals.
"""
import hashlib
import json
import math
import statistics
import sys
from collections import Counter
from decimal import Decimal
from pathlib import Path


def distribution(values):
    if not values:
        return {'n': 0, 'min': None, 'p50': None, 'p95': None, 'max': None}
    ordered = sorted(values)
    return {'n': len(values), 'min': ordered[0], 'p50': statistics.median(ordered),
            'p95': ordered[math.ceil(.95 * len(ordered)) - 1], 'max': ordered[-1]}


def describe(rows, cases):
    observations = [o for row in rows for o in row['observations']]
    result = {'operations': len(rows), 'httpStatuses': dict(Counter(str(r['status']) for r in rows)),
              'errorCodes': dict(Counter(r['body'].get('code', 'UNSPECIFIED') for r in rows if r['status'] != 200)),
              'elapsedMs': distribution([r['elapsedMs'] for r in rows if r['elapsedMs'] is not None]),
              'requestBytes': distribution([r['requestBytes'] for r in rows if 'requestBytes' in r]),
              'noteCodePoints': distribution([len(cases[r['caseId']]['request']['clinicalNote']) for r in rows]),
              'catalogEntries': distribution([sum(len(c['entries']) for c in cases[r['caseId']]['request'].get('catalogs', [])) for r in rows]),
              'effectiveModels': dict(Counter(o['effectiveModel'] or 'UNKNOWN' for o in observations)),
              'providerStatuses': dict(Counter(o['status'] for o in observations)),
              'operationsWithoutExactlyOneObservation': sum(len(r['observations']) != 1 for r in rows),
              'spendUpperBoundUsd': str(sum((Decimal(r['chargeUpperBoundUsd']) for r in rows), Decimal(0))),
              'usage': {}}
    for field in ('inputTokens', 'outputTokens', 'thoughtTokens', 'cachedTokens', 'totalTokens'):
        values = [o[field] for o in observations if o[field] is not None]
        result['usage'][field] = {'knownObservations': len(values), 'missingObservations': len(observations) - len(values),
                                  'knownSum': sum(values), 'perObservation': distribution(values)}
    return result


def main():
    directory = Path(sys.argv[1])
    source = directory / 'responses.jsonl'
    raw = source.read_bytes()
    rows = [json.loads(line) for line in raw.splitlines() if line]
    root = Path(__file__).resolve().parents[1]
    cases = {}
    for split in {r['split'] for r in rows}:
        cases.update({c['id']: c for c in json.loads((root / 'corpus/v1' / (split + '.json')).read_text())})
    report = {'method': 'descriptive-only; p95 nearest rank; no clinical scoring or approval',
              'responsesSha256': hashlib.sha256(raw).hexdigest(),
              'scriptSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'all': describe(rows, cases), 'rounds': {}}
    for split, repetition in sorted({(r['split'], r['round']) for r in rows}):
        report['rounds'][split + '/' + str(repetition)] = describe([r for r in rows if r['split'] == split and r['round'] == repetition], cases)
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
