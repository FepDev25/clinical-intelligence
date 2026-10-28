"""Next campaign cannot silently use v1 assets or resume after corpus changes."""
import importlib.util
import unittest
from unittest.mock import patch
from pathlib import Path
from validate_corpus import ROOT

spec = importlib.util.spec_from_file_location('campaign_v2', ROOT / 'next/run_evaluation_v2.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)

class CampaignTests(unittest.TestCase):
    def test_release_uses_new_runner_and_new_protocol(self):
        import importlib
        release = importlib.import_module('release_v2')
        self.assertIs(release.runner, importlib.import_module('run_evaluation_v2'))
        self.assertEqual(release.decide({})['protocol'], '1.0.1')

    def test_config_and_scorer_are_updated(self):
        self.assertEqual(runner.CONFIG['promptVersion'], 'extraction-gemini-v2')
        self.assertEqual(runner.CONFIG['validationVersion'], 'evidence-2')
        self.assertEqual(runner.score_case.__module__, 'score_extractions_v2')

    def test_freeze_includes_actual_new_corpus_and_scorer(self):
        assets = runner.assets(runner.PROJECT)
        for path in ('evaluation/next/development.json', 'evaluation/corpus/v2/reserved.json',
                     'evaluation/next/score_extractions_v2.py', 'evaluation/next/run_evaluation_v2.py'):
            self.assertIn(path, assets)

    def test_loads_corrected_reference_and_rejects_exposed_reserved_ids(self):
        development = runner.read_verified_cases('development')
        case = next(c for c in development if c['id'] == 'dev-outpatient-05')
        self.assertEqual(case['expected']['medications'][0]['name']['evidence'][0]['start'], 126)
