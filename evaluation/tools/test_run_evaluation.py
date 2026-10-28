import json
import tempfile
import unittest
from decimal import Decimal
from pathlib import Path
from run_evaluation import freeze, verify_freeze, usage_upper_bound, can_reserve
from unittest.mock import patch


class RunnerTests(unittest.TestCase):
    def project(self, directory):
        root = Path(directory) / 'project'; root.mkdir()
        for name, text in {'pom.xml': '<project/>', 'src/main/java/App.java': 'class App {}',
                           'src/main/resources/gemini/prompt.txt': 'synthetic',
                           'evaluation/PROTOCOL.md': 'fixed rules', 'evaluation/ANNOTATION.md': 'fixed annotation',
                           'evaluation/corpus/v1/manifest.json': '{}', 'openapi/openapi.json': '{}',
                           'evaluation/tools/score_extractions.py': 'scorer-v1', 'evaluation/tools/run_evaluation.py': 'runner-v1',
                           'evaluation/tools/validate_corpus.py': 'validation-v1', 'evaluation/tools/stress_cases.py': 'stress-v1',
                           'openapi/tools/validate_contract.py': 'contract-v1', 'openapi/tools/requirements.txt': 'versions',
                           'evaluation/case.schema.json': '{}',
                           'target/clinical-intelligence-0.1.0-SNAPSHOT.jar': 'synthetic-jar'}.items():
            path = root / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(text)
        (root / '.env').write_text('GOOGLE_API_KEY=synthetic-canary-never-copy')
        return root

    def test_freeze_is_immutable_and_excludes_dotenv(self):
        with tempfile.TemporaryDirectory() as temp:
            root = self.project(temp); output = Path(temp) / 'run'
            manifest = freeze(output, '10', root)
            self.assertEqual(manifest['configuration']['outputTokens'], 65536)
            self.assertNotIn('.env', manifest['assets'])
            self.assertNotIn('GOOGLE_API_KEY', (output / 'freeze.json').read_text())
            self.assertTrue(verify_freeze(output, root))
            with self.assertRaises(FileExistsError): freeze(output, '10', root)

    def test_resume_refuses_modified_processing_or_scoring_assets(self):
        with tempfile.TemporaryDirectory() as temp:
            root = self.project(temp); output = Path(temp) / 'run'; freeze(output, '10', root)
            (root / 'evaluation/tools/score_extractions.py').write_text('different-rules')
            with self.assertRaises(ValueError): verify_freeze(output, root)

    def test_unknown_usage_never_becomes_zero_cost(self):
        self.assertIsNone(usage_upper_bound({'inputTokens': None, 'totalTokens': None}))
        self.assertIsNone(usage_upper_bound({'inputTokens': 100, 'totalTokens': 90}))
        self.assertEqual(usage_upper_bound({'inputTokens': 100, 'totalTokens': 130}), Decimal('0.0001875'))
        self.assertEqual(usage_upper_bound({'inputTokens': 0, 'totalTokens': 0}), Decimal('0'))

    def test_budget_checks_next_reservation_not_only_past_spend(self):
        self.assertFalse(can_reserve('9.5', '10', '0.98304'))
        self.assertTrue(can_reserve('9', '10', '0.98304'))
        self.assertFalse(can_reserve('0', '-1', '0.5'))

    def test_helper_changes_are_also_frozen(self):
        with tempfile.TemporaryDirectory() as temp:
            root = self.project(temp); output = Path(temp) / 'run'; freeze(output, '10', root)
            (root / 'openapi/tools/validate_contract.py').write_text('changed-helper')
            with self.assertRaises(ValueError): verify_freeze(output, root)

    def test_resume_after_append_before_unlink_never_duplicates_an_attempt(self):
        import run_evaluation as runner
        with tempfile.TemporaryDirectory() as temp:
            root = self.project(temp); output = Path(temp) / 'run'; freeze(output, '10', root)
            corpus = root / 'evaluation/corpus/v1/development.json'
            corpus.write_text(json.dumps([{'id': 'dev-unit', 'request': {'clinicalNote': 'synthetic'}}]))
            for round_number in (1, 2, 3):
                runner.append(output, {'split': 'development', 'round': round_number, 'caseId': 'dev-unit', 'chargeUpperBoundUsd': '0', 'requestSha256': runner.hash_request({'clinicalNote': 'synthetic'})})
            (output / 'inflight.json').write_text(json.dumps({'split': 'development', 'round': 1, 'caseId': 'dev-unit', 'requestSha256': runner.hash_request({'clinicalNote': 'synthetic'})}))
            with patch.object(runner, 'PROJECT', root), patch.object(runner, 'EVALUATION_ROOT', root / 'evaluation'), \
                    patch.object(runner, 'read_verified_cases', return_value=json.loads(corpus.read_text())), patch.object(runner, 'Backend'):
                self.assertTrue(runner.run(output, 'development'))
            self.assertEqual(len(runner.load_rows(output)), 3)
            self.assertFalse((output / 'inflight.json').exists())

    def test_http_nonobject_is_recorded_as_failure_without_crashing_the_run(self):
        import run_evaluation as runner
        import http.server
        import threading
        class Handler(http.server.BaseHTTPRequestHandler):
            def do_POST(self):
                self.send_response(200); self.end_headers(); self.wfile.write(b'[]')
            def log_message(self, *_): pass
        server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
        try:
            with tempfile.TemporaryDirectory() as temp:
                backend = runner.Backend({}); backend.log = Path(temp) / 'safe.log'; backend.log.write_text('')
                backend.key = 'synthetic-consumer-key'
                backend.url = f'http://127.0.0.1:{server.server_port}/api/v1/extractions'
                backend.opener = runner.urllib.request.build_opener(runner.urllib.request.ProxyHandler({}), runner.NoRedirect())
                result = backend.send({'clinicalNote': 'synthetic'})
                self.assertEqual(result['status'], 0)
                self.assertTrue(result['body']['code'].startswith('EVALUATION_TRANSPORT_'))
        finally:
            server.shutdown(); server.server_close(); thread.join()

    def test_run_lock_rejects_concurrent_owners(self):
        import run_evaluation as runner
        with tempfile.TemporaryDirectory() as directory:
            with runner.run_directory_lock(directory):
                with self.assertRaises(ValueError):
                    with runner.run_directory_lock(directory): pass

    def test_attempt_identity_cannot_be_overwritten_by_another_result(self):
        import run_evaluation as runner
        cases = {'development': [{'id': 'dev-unit', 'request': {'clinicalNote': 'synthetic'}}]}
        row = {'split': 'development', 'round': 1, 'caseId': 'dev-unit', 'requestSha256': 'wrong'}
        with self.assertRaises(ValueError): runner.validate_attempts([row], cases)
        row['requestSha256'] = runner.hash_request(cases['development'][0]['request'])
        with self.assertRaises(ValueError): runner.validate_attempts([row, dict(row)], cases)
        row['caseId'] = 'not-in-frozen-corpus'
        with self.assertRaises(ValueError): runner.validate_attempts([row], cases)

    def test_analysis_rejects_modified_gold_and_preserves_previous_report(self):
        import run_evaluation as runner
        import validate_corpus
        import hashlib
        real = validate_corpus.ROOT
        with tempfile.TemporaryDirectory() as temp:
            root = self.project(temp); evaluation = root / 'evaluation'
            cases = json.loads((real / 'corpus/v1/development.json').read_text())
            files = {'corpus/v1/development.json': json.dumps(cases), 'corpus/v1/reserved.json': '[]',
                     'corpus/v1/stress-recipes.json': '[]', 'case.schema.json': (real / 'case.schema.json').read_text(),
                     'tools/stress_cases.py': '# synthetic helper'}
            splits = ['development', 'reserved', 'stress', 'shared', 'stress']
            manifest = {'corpusVersion': '1.0.0', 'annotationVersion': '1.0.0', 'counts': {'development': 39, 'reserved': 39}, 'assets': []}
            for (name, text), split in zip(files.items(), splits):
                path = evaluation / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(text)
                manifest['assets'].append({'path': name, 'split': split, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
            (evaluation / 'corpus/v1/manifest.json').write_text(json.dumps(manifest))
            output = Path(temp) / 'run'; freeze(output, '10', root)
            request = cases[0]['request']
            runner.append(output, {'split': 'development', 'round': 1, 'caseId': cases[0]['id'], 'status': 504,
                                   'body': {'code': 'PROVIDER_TIMEOUT'}, 'headers': {}, 'chargeUpperBoundUsd': '1',
                                   'requestSha256': hashlib.sha256(json.dumps(request, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()})
            with patch.object(runner, 'PROJECT', root), patch.object(runner, 'EVALUATION_ROOT', evaluation), patch.object(validate_corpus, 'ROOT', evaluation):
                runner.analyze(output)
                before = (output / 'summary.json').read_bytes()
                cases[0]['expected']['diagnoses'] = []
                (evaluation / 'corpus/v1/development.json').write_text(json.dumps(cases))
                with self.assertRaises(ValueError): runner.analyze(output)
                self.assertEqual(before, (output / 'summary.json').read_bytes())

    def test_semantic_audit_is_bound_to_exact_results_and_cannot_skip_accepted_outputs(self):
        import run_evaluation as runner
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory / 'freeze.json').write_text('{}')
            row = {'split': 'development', 'round': 1, 'caseId': 'dev-unit', 'status': 200}
            runner.append(directory, row)
            audit = {'schemaVersion': '1.0.0', 'reviewer': 'synthetic text reviewer',
                     'reviewScope': 'synthetic-reference-support', 'clinicalReview': False,
                     'freezeSha256': runner.digest(directory / 'freeze.json'),
                     'responsesSha256': runner.digest(directory / 'responses.jsonl'),
                     'reviews': [{'split': 'development', 'round': 1, 'caseId': 'dev-unit',
                                  'recordSha256': runner.record_hash(row), 'decision': 'pending', 'notes': 'Await review'}]}
            path = directory / 'semantic-audit.json'; path.write_text(json.dumps(audit))
            self.assertEqual(runner.load_semantic_audit(directory, [row]), {})
            audit['reviews'][0]['decision'] = 'operation_failure'; path.write_text(json.dumps(audit))
            with self.assertRaises(ValueError): runner.load_semantic_audit(directory, [row])
            audit['reviews'][0]['decision'] = 'support_verified'; path.write_text(json.dumps(audit))
            self.assertEqual(len(runner.load_semantic_audit(directory, [row])), 1)
            runner.append(directory, dict(row, caseId='dev-other'))
            with self.assertRaises(ValueError): runner.load_semantic_audit(directory, [row])

    def test_child_binding_is_explicit_even_when_dotenv_imports_configuration(self):
        import run_evaluation as runner
        with patch.object(runner.subprocess, 'Popen') as launch:
            launch.return_value.poll.return_value = 1
            with self.assertRaises(RuntimeError):
                with runner.Backend({'artifact': {'path': 'synthetic.jar'}}): pass
            command = launch.call_args.args[0]
            self.assertIn('--server.address=127.0.0.1', command)
            self.assertIn('--server.port=0', command)


if __name__ == '__main__': unittest.main()
