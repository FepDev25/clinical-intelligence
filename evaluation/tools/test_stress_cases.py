"""Tests for deterministic, bounded synthetic size recipes."""

import unittest

from stress_cases import materialize
from validate_corpus import validate_cases


def recipe(**changes):
    return {"id": "stress-test", "originGroup": "stress-only-test", "noteCodePoints": 1000,
            "catalogEntries": 10, "procedureCount": 3, **changes}


class StressCaseTests(unittest.TestCase):
    def test_materializes_exact_codepoint_length(self):
        case = materialize(recipe())
        self.assertEqual(len(case["request"]["clinicalNote"]), 1000)

    def test_places_evidence_at_beginning_middle_and_end(self):
        case = materialize(recipe())
        points = [entity["name"]["evidence"][0]["start"] for entity in case["expected"]["procedures"]]
        self.assertEqual(len(points), 3)
        self.assertLess(points[0], 200)
        self.assertTrue(300 < points[1] < 700)
        self.assertGreater(points[2], 800)

    def test_catalog_match_is_last_entry_not_first(self):
        case = materialize(recipe())
        entries = case["request"]["catalogs"][0]["entries"]
        self.assertEqual(len(entries), 10)
        self.assertEqual(entries[-1]["entryId"], "target")
        self.assertEqual(case["expected"]["medications"][0]["catalogMatch"]["candidates"][0]["entryId"], "target")

    def test_materialized_case_obeys_wire_contract(self):
        case = materialize(recipe())
        self.assertIn("id", case)
        self.assertEqual(validate_cases([case], "stress")["cases"], 1)

    def test_rejects_note_too_short_for_all_findings(self):
        with self.assertRaises(ValueError):
            materialize(recipe(noteCodePoints=10))

    def test_rejects_negative_catalog_count(self):
        with self.assertRaises(ValueError):
            materialize(recipe(catalogEntries=-1))

    def test_density_is_not_silently_truncated(self):
        case = materialize(recipe(noteCodePoints=20000, procedureCount=100))
        self.assertEqual(len(case["expected"]["procedures"]), 100)
        self.assertEqual(len(case["request"]["clinicalNote"]), 20000)

    def test_repeated_recipe_is_deterministic(self):
        self.assertEqual(len(materialize(recipe())["request"]["clinicalNote"]), 1000)
        self.assertEqual(materialize(recipe()), materialize(recipe()))


if __name__ == "__main__":
    unittest.main()
