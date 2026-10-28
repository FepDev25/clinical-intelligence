"""Regression tests for rejecting broken annotations and split contamination."""

import copy
import unittest

from validate_corpus import ROOT, audit_coverage, read_json, validate_cases, verify_assets


def case_fixture():
    # Hand-checked offsets: 'asma' is [13,17) in 'Diagnóstico: asma.'.
    evidence = {"start": 13, "end": 17, "quote": "asma"}
    unknown = {"value": "unknown", "evidence": []}
    return {
        "id": "dev-unit-001", "originGroup": "unit-origin-one", "split": "development",
        "family": "outpatient", "cohort": "core", "format": "narrative",
        "tags": ["explicit_diagnosis"], "annotationVersion": "1.0.0",
        "provenance": {"kind": "synthetic", "author": "codex", "review": "author_consistency_checked"},
        "request": {"clinicalNote": "Diagnóstico: asma."},
        "expected": {
            "diagnoses": [{"id": "d1", "name": {"value": "asma", "evidence": [evidence]},
                           "mentionedCode": None, "context": {"assertion": copy.deepcopy(unknown),
                           "temporality": copy.deepcopy(unknown), "subject": copy.deepcopy(unknown)},
                           "catalogMatch": {"status": "not_provided", "candidates": []}}],
            "medications": [], "procedures": [], "requiredWarnings": []},
        "excludedMentions": [], "annotationNotes": "Mención explícita; no se asumen fecha ni sujeto."
    }


class CorpusValidationTests(unittest.TestCase):
    def test_valid_annotation_is_counted(self):
        self.assertEqual(validate_cases([case_fixture()], "development")["cases"], 1)

    def test_rejects_quote_that_does_not_match_note(self):
        case = case_fixture()
        case["expected"]["diagnoses"][0]["name"]["evidence"][0]["quote"] = "otra"
        with self.assertRaises(ValueError):
            validate_cases([case], "development")

    def test_rejects_offset_outside_note(self):
        case = case_fixture()
        case["expected"]["diagnoses"][0]["name"]["evidence"][0]["end"] = 999
        with self.assertRaises(ValueError):
            validate_cases([case], "development")

    def test_rejects_unknown_gold_field(self):
        case = case_fixture()
        case["expected"]["diagnoses"][0]["confidence"] = 0.99
        with self.assertRaises(ValueError):
            validate_cases([case], "development")

    def test_rejects_missing_nullable_field(self):
        case = case_fixture()
        del case["expected"]["diagnoses"][0]["mentionedCode"]
        with self.assertRaises(ValueError):
            validate_cases([case], "development")

    def test_rejects_invented_catalog_id(self):
        case = case_fixture()
        case["expected"]["diagnoses"][0]["catalogMatch"] = {"status": "matched", "candidates": [{"catalogId": "missing", "version": "1", "entryId": "invented"}]}
        with self.assertRaises(ValueError):
            validate_cases([case], "development")

    def test_rejects_duplicate_case_id(self):
        case = case_fixture()
        with self.assertRaises(ValueError):
            validate_cases([case, copy.deepcopy(case)], "development")

    def test_rejects_case_in_wrong_split(self):
        with self.assertRaises(ValueError):
            validate_cases([case_fixture()], "reserved")

    def test_rejects_shared_origin_across_splits(self):
        source = case_fixture()
        other = copy.deepcopy(source)
        other.update(id="holdout-001", split="reserved")
        other["request"]["clinicalNote"] += " Texto adicional."
        with self.assertRaises(ValueError):
            validate_cases([other], "reserved", other_cases=[source])

    def test_rejects_same_note_despite_case_and_whitespace_changes(self):
        source = case_fixture()
        other = copy.deepcopy(source)
        other.update(id="holdout-002", split="reserved", originGroup="different-label")
        # Leakage is checked independently of gold, which also needs reannotation.
        other["request"]["clinicalNote"] = "  DIAGNÓSTICO:   ASMA.  "
        with self.assertRaises(ValueError):
            validate_cases([other], "reserved", other_cases=[source])

    def test_rejects_reserved_copy_of_public_example(self):
        case = case_fixture()
        case.update(split="reserved", id="reserved-copy")
        with self.assertRaises(ValueError):
            validate_cases([case], "reserved", public_notes=["Diagnóstico: asma."])

    def test_accepts_codepoint_indices_after_emoji(self):
        case = case_fixture()
        case["request"]["clinicalNote"] = "🩺 asma"
        case["expected"]["diagnoses"][0]["name"]["evidence"] = [{"start": 2, "end": 6, "quote": "asma"}]
        self.assertEqual(validate_cases([case], "development")["cases"], 1)

    def test_rejects_utf16_offset_used_as_codepoint_offset(self):
        case = case_fixture()
        case["request"]["clinicalNote"] = "🩺 asma"
        case["expected"]["diagnoses"][0]["name"]["evidence"] = [{"start": 3, "end": 7, "quote": "asma"}]
        with self.assertRaises(ValueError):
            validate_cases([case], "development")

    def test_rejects_exclusion_without_literal_backing(self):
        case = case_fixture()
        case["excludedMentions"] = [{"evidence": {"start": 0, "end": 4, "quote": "nada"}, "reason": "symptom_only"}]
        with self.assertRaises(ValueError):
            validate_cases([case], "development")

    def test_rejects_contradiction_warning_pointing_to_missing_entity(self):
        case = case_fixture()
        case["expected"]["requiredWarnings"] = [{"code": "CONTRADICTORY_MENTIONS", "message": "Contradicción.", "targets": ["/diagnoses/0", "/diagnoses/1"], "source": "/clinicalNote"}]
        with self.assertRaises(ValueError):
            validate_cases([case], "development")

    def test_rejects_non_synthetic_provenance(self):
        case = case_fixture()
        case["provenance"]["kind"] = "patient_record"
        with self.assertRaises(ValueError):
            validate_cases([case], "development")

    def test_rejects_undocumented_family(self):
        case = case_fixture()
        case["family"] = "laboratory"
        with self.assertRaises(ValueError):
            validate_cases([case], "development")

    def test_rejects_duplicate_gold_entity_ids(self):
        case = case_fixture()
        case["expected"]["diagnoses"].append(copy.deepcopy(case["expected"]["diagnoses"][0]))
        with self.assertRaises(ValueError):
            validate_cases([case], "development")

    def test_incomplete_corpus_cannot_pass_coverage_gate(self):
        with self.assertRaises(ValueError):
            audit_coverage([case_fixture()])

    def test_rejects_gold_entity_inside_excluded_instruction(self):
        case = case_fixture()
        case["excludedMentions"] = [{"evidence": {"start": 13, "end": 17, "quote": "asma"}, "reason": "instruction_not_clinical"}]
        with self.assertRaises(ValueError):
            validate_cases([case], "development")

    def test_rejects_gold_duration_taken_from_control(self):
        case = case_fixture()
        case["request"]["clinicalNote"] = "Alfa. Control 7 días."
        entity = case["expected"]["diagnoses"].pop()
        entity.update(id="m1", name={"value": "Alfa", "evidence": [{"start": 0, "end": 4, "quote": "Alfa"}]},
                      prescriptionStatus={"value": "unknown", "evidence": []}, dosage=None, frequency=None, route=None,
                      duration={"value": "7 días", "evidence": [{"start": 14, "end": 20, "quote": "7 días"}]})
        case["expected"]["medications"] = [entity]
        self.assertEqual(validate_cases([case], "development")["cases"], 1)
        case["excludedMentions"] = [{"evidence": {"start": 6, "end": 21, "quote": "Control 7 días."}, "reason": "followup_not_duration"}]
        with self.assertRaises(ValueError):
            validate_cases([case], "development")

    def test_missing_integrity_entry_does_not_silently_pass(self):
        with self.assertRaises(ValueError):
            verify_assets({"assets": []}, {"development"})

    def test_hash_mismatch_is_rejected(self):
        manifest = read_json(ROOT / "corpus/v1/manifest.json")
        manifest["assets"][0]["sha256"] = "0" * 64
        with self.assertRaises(ValueError):
            verify_assets(manifest, {"development"})

    def test_failure_message_does_not_dump_reserved_note(self):
        case = case_fixture()
        case["request"]["clinicalNote"] = "SYNTHETIC_PRIVATE_MARKER"
        case["unexpected"] = "schema-error"
        with self.assertRaises(ValueError) as caught:
            validate_cases([case], "development")
        self.assertNotIn("SYNTHETIC_PRIVATE_MARKER", str(caught.exception))


if __name__ == "__main__":
    unittest.main()
