"""Opt-in frozen synthetic evaluation runner. Never reads dotenv/provider credentials.

prepare snapshots processing/scoring assets; run sends only corpus request objects to
a local authenticated API. Gold is used only afterwards for scoring, never prompts.
"""
import argparse
import contextlib
import fcntl
import copy
import hashlib
import json
import os
import re
import secrets
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import zipfile
from datetime import datetime, timezone, date
from decimal import Decimal
import platform
import sys
from importlib.metadata import version
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
sys.path.insert(0, str(Path(__file__).resolve().parent))
from validate_corpus import ROOT as EVALUATION_ROOT, read_json, verify_assets, validate_cases, audit_coverage
from score_extractions_v2 import score_case, aggregate

PROJECT = EVALUATION_ROOT.parent
MAX_RESERVATION = Decimal('1.277952')  # Full input + output + separate thought allowance, standard tariff.
CONFIG = {
    'model': 'gemini-3.8-flash', 'provider': 'google-gemini', 'promptVersion': 'extraction-gemini-v2',
    'validationVersion': 'evidence-2', 'temperature': 1.0, 'thinkingLevel': 'LOW',
    'outputTokens': 65536, 'deadlineMs': 120000, 'connectTimeoutMs': 10000,
    'providerConcurrency': 2, 'evaluationConcurrency': 1, 'responseBytes': 16777216,
    'rounds': 3, 'maxProviderCallsPerOperation': 2, 'retries': 0,
    'bodyBytes': 67108864, 'noteCodePoints': 1000000, 'catalogs': 1000, 'entries': 100000, 'aliases': 1000000,
    'tariff': 'gemini-3.8-flash-standard-2026-12-31',
}

@contextlib.contextmanager
def run_directory_lock(directory):
    # Persistent inode: unlinking the lock file would permit competing owners.
    with (Path(directory) / '.run.lock').open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError('Evaluation directory is already in use') from None
        try:
            yield
        finally:
            fcntl.flock(lock, fcntl.LOCK_UN)


def hash_request(request):
    return hashlib.sha256(json.dumps(request, ensure_ascii=False, separators=(',', ':')).encode('utf-8')).hexdigest()


def read_verified_cases(split):
    if split not in ('development', 'reserved'):
        raise ValueError('Unknown corpus split')
    manifest = read_json(EVALUATION_ROOT / 'corpus/v3/manifest.json')
    paths = {'development': 'next/development.json', 'reserved': 'corpus/v3/reserved.json'}
    path = EVALUATION_ROOT / paths[split]
    if digest(path) != manifest['sha256'][split]:
        raise ValueError('Versioned corpus changed')
    cases = read_json(path)
    if len(cases) != 39:
        raise ValueError('Frozen corpus population changed')
    validate_cases(cases, split)
    audit_coverage(cases)
    return cases


def validate_attempts(rows, case_sets):
    allowed = {(split, n, c['id']): hash_request(c['request'])
               for split, cases in case_sets.items() for n in (1, 2, 3) for c in cases}
    seen = set()
    for row in rows:
        key = (row.get('split'), row.get('round'), row.get('caseId'))
        if type(row.get('round')) is not int or key not in allowed or key in seen:
            raise ValueError('Duplicate or foreign attempt identity')
        if row.get('requestSha256') != allowed[key]:
            raise ValueError('Recorded request differs from frozen corpus')
        seen.add(key)
    return seen


def now():
    return datetime.now(timezone.utc).isoformat()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def assets(project):
    paths = [p for p in (project / 'src/main').rglob('*') if p.is_file()]
    paths += [project / p for p in (
        'pom.xml', 'evaluation/PROTOCOL.md', 'evaluation/ANNOTATION.md',
        'evaluation/corpus/v1/manifest.json', 'openapi/openapi.json',
        'evaluation/tools/score_extractions.py', 'evaluation/tools/run_evaluation.py',
        'evaluation/tools/validate_corpus.py', 'evaluation/tools/stress_cases.py',
        'openapi/tools/validate_contract.py', 'openapi/tools/requirements.txt', 'evaluation/case.schema.json',
        'evaluation/next/development.json', 'evaluation/corpus/v3/reserved.json', 'evaluation/corpus/v3/manifest.json',
        'evaluation/next/score_extractions_v2.py', 'evaluation/next/run_evaluation_v3.py',
        'evaluation/next/release_v3.py')]
    return {str(p.relative_to(project)): digest(p) for p in sorted(paths)}


def freeze(output, budget_usd, project=None):
    project = Path(project or PROJECT)
    output = Path(output)
    budget = Decimal(str(budget_usd))
    if not budget.is_finite() or budget < 0:
        raise ValueError('Budget must be finite and nonnegative')
    output.mkdir(parents=True, exist_ok=False)
    selected = assets(project)
    jar = project / 'target/clinical-intelligence-0.1.0-SNAPSHOT.jar'
    jar_hash = digest(jar)
    artifact = project / 'target/h5-frozen' / (jar_hash + '.jar')
    artifact.parent.mkdir(parents=True, exist_ok=True)
    if not artifact.exists():
        shutil.copyfile(jar, artifact)
    manifest = {
        'createdUtc': now(), 'configuration': copy.deepcopy(CONFIG), 'budgetUsd': str(budget),
        'maxReservationPerOperationUsd': str(MAX_RESERVATION), 'assets': selected,
        'artifact': {'path': str(artifact.relative_to(project)), 'sha256': jar_hash},
        'clinicalData': 'synthetic-only', 'accountBillingTierVerified': False,
        'repositoryRevision': 'working tree; enumerated hashes and source snapshot capture assets',
        'protocol': '1.0.1', 'scorer': '1.0.1', 'notes': 'Two H5 reserved scenarios were exposed and replaced; author review is not blinded.',
        'analysisEnvironment': {'python': platform.python_version(), 'unicode': __import__('unicodedata').unidata_version,
                                'packages': {name: version(name) for name in ('jsonschema', 'referencing', 'langcodes')}},
    }
    with zipfile.ZipFile(output / 'source-snapshot.zip', 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for path in selected:
            if path in {'evaluation/next/development.json', 'evaluation/corpus/v3/reserved.json'}:
                continue
            archive.write(project / path, path)
    manifest['sourceSnapshotSha256'] = digest(output / 'source-snapshot.zip')
    (output / 'freeze.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    return manifest


def verify_freeze(directory, project=None):
    project = Path(project or PROJECT)
    manifest = read_json(Path(directory) / 'freeze.json')
    if manifest['assets'] != assets(project):
        raise ValueError('Frozen processing/scoring assets changed; create a new run')
    artifact = project / manifest['artifact']['path']
    if digest(artifact) != manifest['artifact']['sha256']:
        raise ValueError('Frozen application artifact changed')
    if manifest['configuration'] != CONFIG:
        raise ValueError('Frozen configuration changed')
    if digest(Path(directory) / 'source-snapshot.zip') != manifest['sourceSnapshotSha256']:
        raise ValueError('Frozen source snapshot changed')
    return True


def usage_upper_bound(observation):
    """Upper charge estimate: all input at uncached price; all other tokens at output price.

    Google totalTokenCount includes input, tool use, thoughts and candidates.
    No missing thought/cache count is substituted with zero. Unknown totals stay unknown.
    """
    incoming, total = observation.get('inputTokens'), observation.get('totalTokens')
    if type(incoming) is not int or type(total) is not int or not 0 <= incoming <= total:
        return None
    return (Decimal(incoming) * Decimal('0.75') + Decimal(total - incoming) * Decimal('3.75')) / Decimal(1000000)


def can_reserve(spent, budget, reservation):
    values = [Decimal(str(v)) for v in (spent, budget, reservation)]
    return all(v.is_finite() and v >= 0 for v in values) and values[0] + values[2] <= values[1]


OBSERVATION = re.compile(
    r'Observation\[status=([A-Z_]+), latencyMillis=(\d+), usage=Usage\[inputTokens=(null|\d+), '
    r'outputTokens=(null|\d+), thoughtTokens=(null|\d+), cachedTokens=(null|\d+), totalTokens=(null|\d+)\], '
    r'estimatedUsd=(null|[0-9.]+), tariff=([a-z0-9.-]+), effectiveModel=(null|[A-Za-z0-9._-]+)\]')


def safe_observations(log_text):
    result = []
    for match in OBSERVATION.finditer(log_text):
        x = match.groups()
        record = {'status': x[0], 'latencyMillis': int(x[1]), 'estimatedUsd': None if x[7] == 'null' else x[7],
                  'tariff': x[8], 'effectiveModel': None if x[9] == 'null' else x[9]}
        for field, value in zip(('inputTokens', 'outputTokens', 'thoughtTokens', 'cachedTokens', 'totalTokens'), x[2:7]):
            record[field] = None if value == 'null' else int(value)
        result.append(record)
    return result


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Backend:
    """Only child application imports the user's dotenv, never this runner."""
    def __init__(self, manifest, project=None):
        self.project = Path(project or PROJECT)
        self.manifest = manifest
        self.temp = None
        self.process = None

    def __enter__(self):
        self.temp = tempfile.TemporaryDirectory(prefix='ci-h5-api-')
        temp = Path(self.temp.name)
        self.key = 'ci_' + secrets.token_urlsafe(32)
        credentials = temp / 'credentials.json'
        credentials.write_text(json.dumps([{'sha256': hashlib.sha256(self.key.encode()).hexdigest(),
                                           'consumerId': 'synthetic-evaluation', 'scopes': ['extractions:write']}]))
        credentials.chmod(0o600)
        self.log = temp / 'server.log'
        self.output = self.log.open('w')
        env = {'PATH': os.environ['PATH'], 'CI_PROVIDER_MODE': 'gemini', 'CI_CREDENTIALS_FILE': str(credentials),
               'CI_GEMINI_OUTPUT_TOKENS': str(CONFIG['outputTokens']), 'CI_GEMINI_DEADLINE_MS': str(CONFIG['deadlineMs']),
               'CI_GEMINI_CONNECT_TIMEOUT_MS': str(CONFIG['connectTimeoutMs']), 'CI_GEMINI_CONCURRENCY': str(CONFIG['providerConcurrency']),
               'CI_GEMINI_RESPONSE_BYTES': str(CONFIG['responseBytes']), 'CI_BODY_BYTES': str(CONFIG['bodyBytes']),
               'CI_NOTE_CODE_POINTS': str(CONFIG['noteCodePoints']), 'CI_CATALOGS': str(CONFIG['catalogs']),
               'CI_ENTRIES': str(CONFIG['entries']), 'CI_ALIASES': str(CONFIG['aliases'])}
        self.process = subprocess.Popen(
            ['java', '-jar', str(self.project / self.manifest['artifact']['path']), '--server.port=0', '--server.address=127.0.0.1',
             '--spring.config.import=optional:file:.env[.properties]'],
            cwd=self.project, env=env, stdout=self.output, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 40
            while time.monotonic() < deadline:
                found = re.search(r'Tomcat started on port (\d+)', self.log.read_text(errors='replace'))
                if found:
                    self.url = 'http://127.0.0.1:' + found.group(1) + '/api/v1/extractions'
                    self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
                    return self
                if self.process.poll() is not None:
                    raise RuntimeError('Application startup failed; private logs withheld')
                time.sleep(.1)
            raise RuntimeError('Application readiness timeout; private logs withheld')
        except BaseException:
            self.__exit__(None, None, None)
            raise

    def send(self, request):
        body = json.dumps(request, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
        offset = self.log.stat().st_size
        start = time.monotonic()
        query = urllib.request.Request(self.url, data=body, headers={
            'Content-Type': 'application/json', 'Accept': 'application/json, application/problem+json', 'X-API-Key': self.key})
        status, response, headers = 0, {'code': 'TRANSPORT_ERROR'}, {}
        try:
            try:
                stream = self.opener.open(query, timeout=CONFIG['deadlineMs'] / 1000 + 15)
            except urllib.error.HTTPError as error:
                stream = error
            with stream:
                status = stream.code
                headers = {name: stream.headers.get(name) for name in ('X-Request-Id', 'Cache-Control', 'Content-Type', 'WWW-Authenticate', 'Allow')}
                raw = stream.read(CONFIG['bodyBytes'] + 1)
                if len(raw) > CONFIG['bodyBytes']:
                    raise ValueError('Evaluation response limit exceeded')
                response = json.loads(raw)
                if not isinstance(response, dict):
                    raise ValueError('HTTP response must be an object')
        except Exception as error:
            response = {'code': 'EVALUATION_TRANSPORT_' + type(error).__name__.upper()}
            status = 0
        with self.log.open('rb') as log:
            log.seek(offset)
            observation = safe_observations(log.read().decode('utf-8', errors='replace'))
        if status == 200:
            processing = response.get('processing', {})
            if (processing.get('executionMode') != 'provider' or processing.get('provider') != CONFIG['provider']
                    or processing.get('promptVersion') != CONFIG['promptVersion']
                    or processing.get('validationVersion') != CONFIG['validationVersion']):
                return {'status': 0, 'body': {'code': 'EVALUATION_CONFIGURATION_MISMATCH'}, 'headers': headers,
                        'elapsedMs': int((time.monotonic() - start) * 1000), 'observations': observation,
                        'requestBytes': len(body), 'requestSha256': hashlib.sha256(body).hexdigest()}
        return {'status': status, 'body': response, 'headers': headers, 'elapsedMs': int((time.monotonic() - start) * 1000),
                'observations': observation, 'requestBytes': len(body), 'requestSha256': hashlib.sha256(body).hexdigest()}

    def __exit__(self, *_):
        if self.process is not None:
            self.process.terminate()
            try:
                self.process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.process.kill(); self.process.wait()
        if hasattr(self, 'output'):
            self.output.close()
        if self.temp is not None:
            self.temp.cleanup()


def load_rows(directory):
    path = Path(directory) / 'responses.jsonl'
    if not path.exists():
        return []
    with path.open() as stream:
        return [json.loads(line) for line in stream if line.strip()]


def append(directory, row):
    with (Path(directory) / 'responses.jsonl').open('a') as stream:
        stream.write(json.dumps(row, ensure_ascii=False, separators=(',', ':')) + '\n')
        stream.flush(); os.fsync(stream.fileno())


def run(directory, split):
    with run_directory_lock(directory):
        return _run(directory, split)


def _run(directory, split):
    directory = Path(directory)
    verify_freeze(directory)
    manifest = read_json(directory / 'freeze.json')
    if date.today() > date(2026, 12, 31):
        raise ValueError('Frozen tariff expired; refresh pricing in a new run')
    if split not in ('development', 'reserved'):
        raise ValueError('Fixed split must be explicit')
    cases = read_verified_cases(split)
    rows = load_rows(directory)
    case_sets = {split: cases}
    for prior_split in {r['split'] for r in rows} - {split}:
        if prior_split not in ('development', 'reserved'):
            raise ValueError('Foreign split in results')
        case_sets[prior_split] = read_verified_cases(prior_split)
    previous = validate_attempts(rows, case_sets)
    if split == 'reserved':
        development = case_sets.get('development') or read_verified_cases('development')
        if not all(('development', n, c['id']) in previous for n in (1, 2, 3) for c in development):
            raise ValueError('Complete development rounds before reserved measurement; no reserved tuning')
    spent = sum((Decimal(r['chargeUpperBoundUsd']) for r in rows), Decimal(0))
    interrupted = directory / 'inflight.json'
    if interrupted.exists():
        inflight = read_json(interrupted)
        validate_attempts([inflight], case_sets)
        if (inflight['split'], inflight['round'], inflight['caseId']) not in previous:
            append(directory, {**inflight, 'status': 0, 'body': {'code': 'INTERRUPTED_AFTER_DISPATCH'}, 'headers': {},
                               'elapsedMs': None, 'observations': [], 'chargeUpperBoundUsd': str(MAX_RESERVATION), 'completedUtc': now()})
            spent += MAX_RESERVATION
        interrupted.unlink()
        rows = load_rows(directory)
        previous = {(r['split'], r['round'], r['caseId']) for r in rows}
    with Backend(manifest) as backend:
        for repetition in (1, 2, 3):
            for index, case in enumerate(cases, 1):
                key = (split, repetition, case['id'])
                if key in previous:
                    continue
                if not can_reserve(spent, manifest['budgetUsd'], MAX_RESERVATION):
                    print(json.dumps({'event': 'BUDGET_STOP', 'split': split, 'round': repetition, 'nextCase': index,
                                      'spentUpperBoundUsd': str(spent)}, ensure_ascii=False), flush=True)
                    return False
                identity = {'split': split, 'round': repetition, 'caseId': case['id'], 'startedUtc': now(),
                            'requestSha256': hash_request(case['request'])}
                interrupted.write_text(json.dumps(identity) + '\n')
                result = backend.send(case['request'])
                known = [usage_upper_bound(o) for o in result['observations']]
                charge = max(known) if len(known) == 1 and known[0] is not None else MAX_RESERVATION
                spent += charge
                row = {**identity, **result, 'chargeUpperBoundUsd': str(charge), 'completedUtc': now()}
                append(directory, row)
                interrupted.unlink()
                print(json.dumps({'event': 'RESULT', 'split': split, 'round': repetition, 'case': index, 'id': case['id'],
                                  'status': result['status'], 'elapsedMs': result['elapsedMs'], 'spentUpperBoundUsd': str(spent)}, ensure_ascii=False), flush=True)
    return True


def record_hash(row):
    return hashlib.sha256(json.dumps(row, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def load_semantic_audit(directory, rows):
    path = Path(directory) / 'semantic-audit.json'
    if not path.exists():
        return {}
    audit = read_json(path)
    if (audit.get('schemaVersion') != '1.0.0' or not audit.get('reviewer', '').strip()
            or audit.get('reviewScope') != 'synthetic-reference-support' or audit.get('clinicalReview') is not False
            or audit.get('freezeSha256') != digest(Path(directory) / 'freeze.json')
            or audit.get('responsesSha256') != digest(Path(directory) / 'responses.jsonl')):
        raise ValueError('Semantic audit is incomplete or bound to different artifacts')
    records = {(r['split'], r['round'], r['caseId']): r for r in rows}
    result = {}
    for review in audit.get('reviews', []):
        key = (review.get('split'), review.get('round'), review.get('caseId'))
        if key not in records or key in result or review.get('recordSha256') != record_hash(records[key]):
            raise ValueError('Semantic review identity or response hash mismatch')
        if review.get('decision') not in {'support_verified', 'support_failed', 'operation_failure', 'pending'} or not review.get('notes', '').strip():
            raise ValueError('Semantic review requires a decision and rationale')
        if review['decision'] == 'operation_failure' and records[key]['status'] == 200:
            raise ValueError('Accepted response cannot bypass semantic audit')
        for field in ('invalidEntities', 'invalidContexts', 'invalidAttributes'):
            pointers = review.get(field, [])
            pattern = r'/(diagnoses|medications|procedures)/[0-9]+' + (r'/(mentionedCode|dosage|frequency|route|duration)' if field == 'invalidAttributes' else '')
            if not isinstance(pointers, list) or any(not isinstance(p, str) or not re.fullmatch(pattern, p) for p in pointers):
                raise ValueError('Invalid semantic review pointer')
        if review['decision'] == 'support_verified' and any(review.get(f) for f in ('invalidEntities', 'invalidContexts', 'invalidAttributes')):
            raise ValueError('Verified support conflicts with rejected findings')
        for resolution in review.get('resolvedAdjudications', []):
            if (type(resolution.get('index')) is not int or resolution['index'] < 0
                    or resolution.get('decision') not in {'supported', 'unsupported', 'unresolved'}
                    or not resolution.get('reason', '').strip()):
                raise ValueError('Adjudication requires explicit rationale')
        if review['decision'] != 'pending':
            result[key] = review
    return result


def analyze(directory):
    with run_directory_lock(directory):
        return _analyze(directory)


def _analyze(directory):
    directory = Path(directory)
    verify_freeze(directory)
    rows = load_rows(directory)
    splits = {r['split'] for r in rows}
    if not splits <= {'development', 'reserved'}:
        raise ValueError('Foreign split in results')
    case_sets = {split: read_verified_cases(split) for split in splits}
    validate_attempts(rows, case_sets)
    audit = load_semantic_audit(directory, rows)
    index = {(r['split'], r['round'], r['caseId']): r for r in rows}
    summary = {'createdUtc': now(), 'protocol': '1.0.1', 'configuration': read_json(directory / 'freeze.json')['configuration'],
               'rounds': {}, 'totalRecordedOperations': len(rows), 'allRequiredRoundsComplete': True,
               'spendUpperBoundUsd': str(sum((Decimal(r['chargeUpperBoundUsd']) for r in rows), Decimal(0)))}
    details = []
    for split in ('development', 'reserved'):
        if not any(r['split'] == split for r in rows):
            summary['allRequiredRoundsComplete'] = False
            continue
        cases = case_sets[split]
        for repetition in (1, 2, 3):
            scores = []
            completed = 0
            for case in cases:
                record = index.get((split, repetition, case['id']))
                if record is None:
                    record = {'status': 0, 'body': {'code': 'NOT_EXECUTED'}, 'headers': {}}
                else:
                    completed += 1
                score = score_case(case, record['status'], record['body'], record['headers'],
                                   audit.get((split, repetition, case['id'])))
                scores.append(score)
                details.append({'split': split, 'round': repetition, **score})
            round_summary = aggregate(cases, scores)
            round_summary['executedCases'] = completed
            round_summary['complete'] = completed == len(cases)
            round_summary['pass'] = round_summary['pass'] and round_summary['complete']
            summary['allRequiredRoundsComplete'] &= round_summary['complete']
            summary['rounds'][split + '/' + str(repetition)] = round_summary
    summary['responsesSha256'] = digest(directory / 'responses.jsonl')
    summary['semanticAuditComplete'] = bool(audit) and len(audit) == len(rows) and summary['allRequiredRoundsComplete']
    summary['pass'] = summary['allRequiredRoundsComplete'] and all(summary['rounds'].get('reserved/' + str(n), {}).get('pass', False) for n in (1, 2, 3))
    (directory / 'scores.json').write_text(json.dumps(details, ensure_ascii=False, indent=2) + '\n')
    (directory / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    prepare = commands.add_parser('prepare')
    prepare.add_argument('--output', type=Path, required=True)
    prepare.add_argument('--budget-usd', required=True)
    execute = commands.add_parser('run')
    execute.add_argument('--output', type=Path, required=True)
    execute.add_argument('--split', choices=('development', 'reserved'), required=True)
    score = commands.add_parser('analyze')
    score.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.command == 'prepare':
        result = freeze(args.output, args.budget_usd)
        print(json.dumps({'event': 'FROZEN', 'output': str(args.output), 'budgetUsd': result['budgetUsd'], 'operations': 234}))
    elif args.command == 'run':
        run(args.output, args.split)
    else:
        result = analyze(args.output)
        print(json.dumps({'event': 'SCORED', 'operations': result['totalRecordedOperations'], 'complete': result['allRequiredRoundsComplete'], 'pass': result['pass']}))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(json.dumps({'event': 'FAILED', 'errorType': type(error).__name__, 'detail': str(error) if isinstance(error, ValueError) else 'Private diagnostics withheld'}), flush=True)
        raise SystemExit(1)

