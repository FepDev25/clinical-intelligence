"""Development-only analysis must use verified next assets and never touch reserved."""
import copy
import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from validate_corpus import ROOT, read_json, annotation_response
from run_evaluation import hash_request

spec = importlib.util.spec_from_file_location('next_analysis', ROOT / 'next/analyze_development.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class DevelopmentAnalysisTests(unittest.TestCase):
    def setUp(self):
        self.case = next(c for c in read_json(ROOT / 'next/development.json') if c['id'] == 'dev-outpatient-05')
        self.row = {'split': 'development', 'round': 1, 'caseId': self.case['id'],
                    'requestSha256': hash_request(self.case['request']), 'status': 200,
                    'body': copy.deepcopy(annotation_response(self.case)), 'headers': {'X-Request-Id': '00000000-0000-4000-8000-000000000001', 'Cache-Control': 'no-store'}}

    def test_corrected_case_is_used_and_reserved_rejected(self):
        result = module.analyze_rows([self.row])
        self.assertEqual(result[0]['entities']['medications'], {'tp': 1, 'fp': 0, 'fn': 0})
        foreign = dict(self.row, split='reserved')
        with self.assertRaises(ValueError):
            module.analyze_rows([foreign])

    def test_changed_request_and_duplicate_are_rejected(self):
        with self.assertRaises(ValueError):
            module.analyze_rows([dict(self.row, requestSha256='0' * 64)])
        with self.assertRaises(ValueError):
            module.analyze_rows([self.row, self.row])

    def test_changed_asset_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'evaluation/next/development.json'
            path.parent.mkdir(parents=True)
            path.write_text('[]')
            manifest = root / 'manifest.json'
            manifest.write_text(json.dumps({'sha256': {'evaluation/next/development.json': hashlib.sha256(path.read_bytes()).hexdigest()}}))
            with patch.object(module, 'PROJECT', root), patch.object(module, 'ASSETS', {'evaluation/next/development.json'}):
                module.verify_assets(manifest)
                path.write_text('[1]')
                with self.assertRaises(ValueError):
                    module.verify_assets(manifest)
