#!/usr/bin/env python3
"""Validate synthetic gold annotations; does not execute an extraction model."""

import argparse
import hashlib
import json
import sys
import unicodedata
from collections import Counter
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker
from jsonschema.exceptions import ValidationError
from referencing import Resource

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT.parent / "openapi" / "tools"))
from validate_contract import Contract, read_json, require  # noqa: E402


def note_fingerprint(note):
    return " ".join(unicodedata.normalize("NFC", note).casefold().split())


def annotation_response(case):
    gold = case["expected"]
    return {
        "requestId": "00000000-0000-4000-8000-000000000001",
        "contractVersion": "0.1.0", "locale": case["request"].get("locale", "es"),
        "reviewRequired": True,
        "processingStatus": "completed_with_warnings" if gold["requiredWarnings"] else "completed",
        "diagnoses": gold["diagnoses"], "medications": gold["medications"],
        "procedures": gold["procedures"], "warnings": gold["requiredWarnings"],
        "processing": {"version": "annotation-1", "promptVersion": "not-a-model-output",
                       "validationVersion": "evidence-1", "executionMode": "simulated",
                       "provider": "synthetic-annotation", "model": "none"},
    }


def validate_cases(cases, expected_split, other_cases=(), public_notes=()):
    contract = Contract()
    schema = read_json(ROOT / "case.schema.json")
    registry = contract.registry.with_resource(schema["$id"], Resource.from_contents(schema))
    validator = Draft202012Validator(schema, registry=registry, format_checker=FormatChecker())
    ids = set()
    notes = set()
    other_ids = {case["id"] for case in other_cases}
    other_origins = {case["originGroup"] for case in other_cases}
    other_notes = {note_fingerprint(case["request"]["clinicalNote"]) for case in other_cases}
    public = {note_fingerprint(note) for note in public_notes}
    family_counts = Counter()
    entity_counts = Counter()
    for case in cases:
        case_id = case.get("id", "<missing>")
        try:
            validator.validate(case)
            require(case["split"] == expected_split, "Case in wrong split")
            require(case_id not in ids | other_ids, "Duplicate case ID")
            require(case["originGroup"] not in other_origins, "Shared originGroup across splits")
            fingerprint = note_fingerprint(case["request"]["clinicalNote"])
            require(fingerprint not in notes | other_notes, "Duplicate normalized note")
            if expected_split == "reserved":
                require(fingerprint not in public, "Reserved copy of public contract example")
            require(case["cohort"] != "core" or case["family"] in {"outpatient", "progress", "consultation", "procedure"}, "Core family outside approved coverage")
            require(case["cohort"] != "exploratory" or case["family"] in {"admission", "emergency", "discharge"}, "Exploratory family outside approved coverage")
            response = annotation_response(case)
            contract.response(case["request"], response, {"X-Request-Id": response["requestId"], "Cache-Control": "no-store"})
            contract.limits(case["request"])
            for excluded in case["excludedMentions"]:
                contract.evidence(case["request"]["clinicalNote"], excluded["evidence"])
                interval = excluded["evidence"]
                def inside(evidence):
                    return interval["start"] <= evidence["start"] and evidence["end"] <= interval["end"]
                if excluded["reason"] in {"symptom_only", "administrative", "instruction_not_clinical", "unnamed_entity", "not_explicit"}:
                    for kind in ("diagnoses", "medications", "procedures"):
                        for entity in case["expected"][kind]:
                            require(not any(inside(e) for e in entity["name"]["evidence"]), "Gold entity contradicts an exclusion")
                if excluded["reason"] in {"followup_not_duration", "rest_not_duration", "presentation_not_dosage"}:
                    field = "dosage" if excluded["reason"] == "presentation_not_dosage" else "duration"
                    for medication in case["expected"]["medications"]:
                        attribute = medication[field]
                        require(attribute is None or not any(inside(e) for e in attribute["evidence"]), "Gold medication attribute taken from excluded text")
        except Exception as error:
            if isinstance(error, ValidationError):
                location = "/" + "/".join(str(part) for part in error.absolute_path)
                reason = f"schema rule {error.validator} at {location}"
            elif isinstance(error, ValueError):
                reason = str(error)
            else:
                reason = type(error).__name__
            raise ValueError(f"{case_id}: invalid annotation: {reason}") from None
        ids.add(case_id)
        notes.add(fingerprint)
        family_counts[case["family"]] += 1
        for name in ("diagnoses", "medications", "procedures"):
            entity_counts[name] += len(case["expected"][name])
    return {"cases": len(cases), "families": dict(sorted(family_counts.items())), "entities": dict(entity_counts)}


def audit_coverage(cases):
    core_families = {"outpatient", "progress", "consultation", "procedure"}
    exploratory_families = {"admission", "emergency", "discharge"}
    for family in core_families:
        require(sum(c["family"] == family and c["cohort"] == "core" for c in cases) >= 6, f"Missing core coverage: {family}")
    for family in exploratory_families:
        require(sum(c["family"] == family and c["cohort"] == "exploratory" for c in cases) >= 3, f"Missing exploratory coverage: {family}")
    require(sum(c["cohort"] == "adversarial" for c in cases) >= 6, "Missing adversarial coverage")
    core_counts = {key: sum(len(c["expected"][key]) for c in cases if c["cohort"] == "core") for key in ("diagnoses", "medications", "procedures")}
    require(all(count >= 20 for count in core_counts.values()), "Insufficient positive entity denominators")
    require(any(not any(c["expected"][key] for key in core_counts) for c in cases), "Missing valid-empty case")
    tags = {tag for case in cases for tag in case["tags"]}
    required = {"negation", "family_history", "personal_history", "suspected", "contradiction",
                "unknown_context", "unknown_subject", "followup_not_duration", "rest_not_duration",
                "presentation_not_dosage", "note_injection", "catalog_injection", "catalog_ambiguous",
                "explicit_alias", "accent_preserved", "wrong_catalog_kind", "attempt_unknown",
                "literal_code", "equivalent_repetition", "attribute_association", "unicode"}
    require(required <= tags, f"Missing coverage tags: {sorted(required - tags)}")
    require({"narrative", "sections", "soap", "telegraphic"} <= {case["format"] for case in cases}, "Missing writing formats")
    return {"coreEntities": core_counts, "cohorts": dict(Counter(c["cohort"] for c in cases))}


def verify_assets(manifest, requested):
    expected = {"corpus/v1/development.json": "development", "corpus/v1/reserved.json": "reserved",
                "corpus/v1/stress-recipes.json": "stress", "case.schema.json": "shared", "tools/stress_cases.py": "stress"}
    assets = manifest["assets"]
    require(len(assets) == len(expected) and {asset["path"] for asset in assets} == set(expected), "Missing or duplicate integrity asset")
    for asset in assets:
        require(asset["split"] == expected[asset["path"]], "Integrity asset split mismatch")
        require(len(asset["sha256"]) == 64 and all(c in "0123456789abcdef" for c in asset["sha256"]), "Malformed asset digest")
        if asset["split"] not in requested | {"shared"}:
            continue
        path = (ROOT / asset["path"]).resolve()
        require(path.is_relative_to(ROOT), "Manifest asset escapes evaluation/")
        require(hashlib.sha256(path.read_bytes()).hexdigest() == asset["sha256"], f"Corpus asset changed without version review: {asset['path']}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--include-reserved", action="store_true", help="Author/release audit: explicitly load reserved annotations, never tune prompts from its output")
    parser.add_argument("--stress", action="store_true", help="Materialize bounded scale recipes in memory, without model calls")
    parser.add_argument("--json", action="store_true", help="Aggregate audit report only; no clinical notes or gold annotations")
    args = parser.parse_args()
    manifest = read_json(ROOT / "corpus" / "v1" / "manifest.json")
    require(manifest["corpusVersion"] == "1.0.0", "Unexpected corpus version")
    require(manifest["annotationVersion"] == "1.0.0", "Unexpected annotation version")
    schema = read_json(ROOT / "case.schema.json")
    Draft202012Validator.check_schema(schema)
    requested = {"development"} | ({"reserved"} if args.include_reserved else set()) | ({"stress"} if args.stress else set())
    # Do not open reserved data in the default development command.
    verify_assets(manifest, requested)
    development = read_json(ROOT / "corpus" / "v1" / "development.json")
    reserved = read_json(ROOT / "corpus" / "v1" / "reserved.json") if args.include_reserved else []
    public_notes = []
    for path in (ROOT.parent / "openapi" / "examples").glob("*-request.json"):
        value = read_json(path).get("value", {})
        if isinstance(value, dict) and isinstance(value.get("clinicalNote"), str):
            public_notes.append(value["clinicalNote"])
    report = {"corpusVersion": manifest["corpusVersion"], "qualityEvaluationExecuted": False, "splits": {}}
    for split, cases, other in (("development", development, reserved), ("reserved", reserved, development)):
        if split not in requested:
            continue
        require(len(cases) == manifest["counts"][split], f"Case count differs: {split}")
        stats = validate_cases(cases, split, other_cases=other, public_notes=public_notes)
        stats.update(audit_coverage(cases))
        report["splits"][split] = stats
    if args.stress:
        from stress_cases import materialize

        recipes = read_json(ROOT / "corpus" / "v1" / "stress-recipes.json")
        require(len(recipes) == manifest["counts"]["stress"], "Stress recipe count differs")
        seen_ids = set()
        stress_results = []
        for recipe in recipes:
            case = materialize(recipe)
            require(case["id"] not in seen_ids, "Duplicate stress recipe ID")
            seen_ids.add(case["id"])
            validate_cases([case], "stress", other_cases=development + reserved)
            encoded = json.dumps(case["request"], ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            require(len(encoded) <= Contract().spec["x-runtime-limits"]["requestBodyBytes"], "Canonical probe body exceeds default body byte limit")
            stress_results.append({"id": case["id"], "noteCodePoints": len(case["request"]["clinicalNote"]),
                                   "catalogEntries": recipe["catalogEntries"], "procedures": len(case["expected"]["procedures"]),
                                   "canonicalBodyBytes": len(encoded), "noteSha256": hashlib.sha256(case["request"]["clinicalNote"].encode("utf-8")).hexdigest()})
        report["stress"] = stress_results
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        for split, stats in report["splits"].items():
            print(f"PASS: {split}: {stats['cases']} cases; entities={stats['entities']}; cohorts={stats['cohorts']}.")
        if args.stress:
            print(f"PASS: {len(report['stress'])} scale recipes, up to 1,000,000 code points and 100,000 catalog entries, without model calls.")
        print("PASS: schema, literal evidence, catalog membership, coverage and SHA-256 assets.")
        print("Cross-split full audit executed." if args.include_reserved else "Development only: reserved annotations NOT loaded; run --include-reserved for a full split audit.")
        print("No LLM quality measurement, clinical validation or backend execution performed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
