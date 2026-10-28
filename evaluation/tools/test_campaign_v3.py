"""Campaign v3 uses a separate immutable corpus and release gate."""
import unittest
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'next'))
import run_evaluation_v3 as runner
import release_v3

class CampaignV3Tests(unittest.TestCase):
    def test_configuration_and_assets_identify_v3(self):
        self.assertEqual(runner.CONFIG['promptVersion'], 'extraction-gemini-v2')
        self.assertEqual(runner.CONFIG['validationVersion'], 'evidence-2')
        self.assertEqual(runner.score_case.__module__, 'score_extractions_v2')
        assets = runner.assets(runner.PROJECT)
        for path in ('evaluation/corpus/v3/reserved.json', 'evaluation/corpus/v3/manifest.json', 'evaluation/next/run_evaluation_v3.py', 'evaluation/next/release_v3.py'):
            self.assertIn(path, assets)
        self.assertIs(release_v3.runner, runner)

    def test_corrected_development_is_used(self):
        cases = runner.read_verified_cases('development')
        brisa = next(c for c in cases if c['id'] == 'dev-outpatient-05')
        self.assertEqual(brisa['expected']['medications'][0]['name']['evidence'][0]['start'], 126)
