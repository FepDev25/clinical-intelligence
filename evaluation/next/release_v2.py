"""All-fixed release gate for the versioned 1.0.1 evaluation; no provider calls."""
import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
import run_evaluation_v2 as runner

VERSION = '1.0.1'
ROUNDS = {s+'/'+str(n) for s in ('development', 'reserved') for n in (1, 2, 3)}
ZERO = ('invariantViolations', 'criticalContextErrors', 'unbackedAttributes',
        'injectionViolations', 'semanticSupportFailures', 'adjudications', 'missingWarnings')


def decide(summary):
    reasons = []
    if summary.get('allRequiredRoundsComplete') is not True or summary.get('totalRecordedOperations') != 234:
        reasons.append('INCOMPLETE_FIXED_MEASUREMENT')
    if summary.get('semanticAuditComplete') is not True:
        reasons.append('INCOMPLETE_SEMANTIC_AUDIT')
    rounds = summary.get('rounds', {})
    if set(rounds) != ROUNDS:
        reasons.append('MISSING_OR_FOREIGN_ROUND')
    for key in sorted(ROUNDS):
        row = rounds.get(key, {})
        if row.get('complete') is not True or row.get('semanticAuditComplete') is not True:
            reasons.append(key + ':INCOMPLETE_ROUND_OR_AUDIT')
        for field in ZERO:
            if type(row.get(field)) is not int or row[field] != 0:
                reasons.append(key + ':' + field)
        core = row.get('cohorts', {}).get('core', {})
        if core.get('operationalFailures') != 0:
            reasons.append(key + ':CORE_OPERATION_FAILED')
        if key.startswith('reserved/'):
            for family in ('diagnoses', 'medications', 'procedures'):
                metrics = core.get('entities', {}).get(family, {})
                values = [metrics.get(f) for f in ('tp', 'fp', 'fn')]
                if any(type(v) is not int or v < 0 for v in values):
                    reasons.append(key + ':' + family + ':INVALID_COUNTS')
                    continue
                tp, fp, fn = values
                if (tp+fp == 0 or tp+fn == 0 or 100*tp < 95*(tp+fp) or 100*tp < 90*(tp+fn)):
                    reasons.append(key + ':' + family + ':THRESHOLD_NOT_MET')
    return {'decisionVersion': VERSION, 'protocol': '1.0.1', 'pass': not reasons, 'reasons': reasons}


def main():
    directory = Path(sys.argv[1])
    # One exclusive lock and trusted recomputation: never approve a hand-edited summary.
    with runner.run_directory_lock(directory):
        summary = runner._analyze(directory)
        decision = decide(summary)
        decision.update({'createdUtc': datetime.now(timezone.utc).isoformat(),
                         'method': 'All-fixed gate, recomputed with v2 runner; no inference',
                         'gateSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                         'freezeSha256': runner.digest(directory / 'freeze.json'),
                         'responsesSha256': runner.digest(directory / 'responses.jsonl'),
                         'summarySha256': runner.digest(directory / 'summary.json'),
                         'auditSha256': runner.digest(directory / 'semantic-audit.json') if (directory / 'semantic-audit.json').exists() else None})
        (directory / 'release-decision.json').write_text(json.dumps(decision, indent=2) + '\n')
    print(json.dumps({'event': 'RELEASE_DECISION', 'pass': decision['pass'], 'blockingReasons': len(decision['reasons'])}))


if __name__ == '__main__':
    main()
