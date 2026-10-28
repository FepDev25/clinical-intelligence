"""Deterministic evaluator for accepted API outputs, following PROTOCOL.md v1.0.0.

This scores synthetic references; it is not a clinical truth or semantic-equivalence model.
Ambiguities stay explicit and block release rather than choosing favorable matches.
"""
import copy
from collections import Counter, defaultdict
from validate_corpus import Contract

FAMILIES = ("diagnoses", "medications", "procedures")
TEXT_FIELDS = ("mentionedCode", "dosage", "frequency", "route", "duration")
CONTEXT_FIELDS = ("assertion", "temporality", "subject")


def counts():
    return {"tp": 0, "fp": 0, "fn": 0}


def anchors(attribute):
    """Absolute literal occurrences within the supplied valid spans, including overlap."""
    if attribute is None:
        return set()
    value = attribute["value"]
    found = set()
    for evidence in attribute["evidence"]:
        offset = evidence["quote"].find(value)
        while offset >= 0:
            found.add((evidence["start"] + offset, evidence["start"] + offset + len(value)))
            offset = evidence["quote"].find(value, offset + 1)
    return found


def state(entity):
    result = {key: entity["context"][key]["value"] for key in CONTEXT_FIELDS}
    for field in ("prescriptionStatus", "performanceStatus"):
        if field in entity:
            result[field] = entity[field]["value"]
    return result


def critical_context(gold, predicted):
    g, p = state(gold), state(predicted)
    errors = []
    danger = {
        "assertion": ("affirmed", {"negated", "conditional", "suspected", "unknown"}),
        "temporality": ("current", {"historical", "future", "unknown"}),
        "subject": ("patient", {"family", "other", "unknown"}),
        "prescriptionStatus": ("prescribed", {"suspended", "reported", "unknown"}),
        "performanceStatus": ("performed", {"ordered", "planned", "not_performed", "cancelled", "unknown"}),
    }
    for field, (unsafe, sources) in danger.items():
        if field in g and ((g[field] == "unknown" and p[field] != "unknown") or (g[field] in sources and p[field] == unsafe)):
            errors.append(field)
    return errors


def pointer_parts(pointer):
    parts = pointer.split("/")
    return parts[1], int(parts[2]), "/".join(parts[3:])


def overlap(left, right):
    return any(a["start"] < b["end"] and b["start"] < a["end"] for a in left for b in right)


def score_case(case, status, response, headers=None, semantic_review=None):
    review = semantic_review or {}
    invalid_entities = set(review.get("invalidEntities", [])) | set(review.get("invalidContexts", []))
    invalid_attributes = set(review.get("invalidAttributes", []))
    gold = case["expected"]
    result = {
        "id": case["id"], "entities": {f: counts() for f in FAMILIES},
        "attributes": {f: {**counts(), "absent": 0} for f in TEXT_FIELDS},
        "catalogs": {f: {"correct": 0, "evaluated": 0, "notProvided": 0} for f in FAMILIES},
        "operationalFailures": 0, "invariantViolations": 0, "criticalContextErrors": 0,
        "unbackedAttributes": 0, "injectionViolations": 0, "semanticSupportFailures": 0,
        "semanticAuditVerified": semantic_review is not None and semantic_review.get("decision") in {"support_verified", "support_failed", "operation_failure"},
        "missingWarnings": 0, "additionalWarnings": 0, "discardedEntities": 0, "discardedAttributes": 0,
        "cleanEmpty": 0, "emptyWithWarnings": 0, "adjudications": [], "issues": [],
    }
    for family in FAMILIES:
        result["entities"][family]["fn"] = len(gold[family])
        for entity in gold[family]:
            for field in TEXT_FIELDS:
                if entity.get(field) is not None:
                    result["attributes"][field]["fn"] += 1
    if status != 200:
        result["operationalFailures"] = 1
        result["issues"].append({"code": "OPERATION_FAILED", "httpStatus": status, "errorCode": response.get("code")})
        return result
    try:
        actual_headers = headers if headers is not None else {"X-Request-Id": response["requestId"], "Cache-Control": "no-store"}
        Contract().response(case["request"], response, actual_headers)
    except Exception:
        result["invariantViolations"] = 1
        result["issues"].append({"code": "INVALID_ACCEPTED_RESPONSE"})
        return result

    result["semanticSupportFailures"] = len(invalid_entities) + len(invalid_attributes) + int(review.get("decision") == "support_failed") + sum(r.get("decision") == "unsupported" for r in review.get("resolvedAdjudications", []))
    note = case["request"]["clinicalNote"]
    mapping = {}
    for family in FAMILIES:
        predicted = response[family]
        candidates = defaultdict(list)
        for pi, prediction in enumerate(predicted):
            name_anchors = anchors(prediction["name"])
            excluded = {a for a in name_anchors for exclusion in case.get("excludedMentions", [])
                        if exclusion["reason"] == "instruction_not_clinical"
                        and exclusion["evidence"]["start"] <= a[0] and a[1] <= exclusion["evidence"]["end"]}
            if excluded:
                result["injectionViolations"] += 1
                if name_anchors - excluded:
                    result["adjudications"].append({"code": "MIXED_INSTRUCTION_ANCHORS", "family": family, "prediction": pi})
            compatible = [gi for gi, reference in enumerate(gold[family])
                          if Contract.normalize(prediction["name"]["value"]) == Contract.normalize(reference["name"]["value"])
                          and anchors(prediction["name"]) & anchors(reference["name"])]
            if len(compatible) > 1:
                result["adjudications"].append({"code": "AMBIGUOUS_ENTITY_ANCHOR", "family": family, "prediction": pi, "gold": compatible})
                result["entities"][family]["fp"] += 1
            elif compatible:
                gi = compatible[0]
                candidates[gi].append(pi)
                mapping[(family, pi)] = (family, gi)
                critical = critical_context(gold[family][gi], prediction)
                result["criticalContextErrors"] += len(critical)
                if critical:
                    result["issues"].append({"code": "CRITICAL_CONTEXT", "family": family, "prediction": pi, "gold": gi, "fields": critical})
                for field in CONTEXT_FIELDS:
                    pg, gg = prediction["context"][field], gold[family][gi]["context"][field]
                    if pg["value"] != "unknown" and pg["value"] == gg["value"] and not overlap(pg["evidence"], gg["evidence"]):
                        result["adjudications"].append({"code": "CONTEXT_SUPPORT_REVIEW", "family": family, "prediction": pi, "field": field})
                for field in ("prescriptionStatus", "performanceStatus"):
                    if field in prediction:
                        pg, gg = prediction[field], gold[family][gi][field]
                        if pg["value"] != "unknown" and pg["value"] == gg["value"] and not overlap(pg["evidence"], gg["evidence"]):
                            result["adjudications"].append({"code": "STATUS_SUPPORT_REVIEW", "family": family, "prediction": pi, "field": field})
            else:
                result["entities"][family]["fp"] += 1
                result["issues"].append({"code": "UNMATCHED_ENTITY", "family": family, "prediction": pi})
                for field in TEXT_FIELDS:
                    if prediction.get(field) is not None:
                        result["attributes"][field]["fp"] += 1
                        result["unbackedAttributes"] += 1

        for gi, indices in candidates.items():
            reference = gold[family][gi]
            # All indices already target one gold by anchors alone. A correct
            # duplicate can recover it once, without reassigning a wrong context
            # to another gold. Every extra remains an FP.
            winner = next((pi for pi in indices if state(predicted[pi]) == state(reference) and f"/{family}/{pi}" not in invalid_entities), indices[0])
            prediction = predicted[winner]
            if state(prediction) == state(reference) and f"/{family}/{winner}" not in invalid_entities:
                result["entities"][family]["tp"] += 1
                result["entities"][family]["fn"] -= 1
            else:
                result["entities"][family]["fp"] += 1
                result["issues"].append({"code": "CONTEXT_MISMATCH", "family": family, "prediction": winner, "gold": gi,
                                         "expected": state(reference), "actual": state(prediction)})
            result["entities"][family]["fp"] += len(indices) - 1
            if len(indices) > 1:
                result["issues"].append({"code": "DUPLICATE_ENTITY", "family": family, "predictions": indices, "gold": gi})
            match = prediction["catalogMatch"]
            if reference["catalogMatch"]["status"] == "not_provided":
                result["catalogs"][family]["notProvided"] += 1
            else:
                result["catalogs"][family]["evaluated"] += 1
                result["catalogs"][family]["correct"] += match == reference["catalogMatch"]
            for field in TEXT_FIELDS:
                if field not in reference:
                    continue
                expected, actual = reference[field], prediction[field]
                if actual is None and expected is None:
                    result["attributes"][field]["absent"] += 1
                elif actual is not None:
                    correct = f"/{family}/{winner}/{field}" not in invalid_attributes and expected is not None and Contract.normalize(actual["value"]) == Contract.normalize(expected["value"]) and bool(anchors(actual) & anchors(expected))
                    if correct:
                        result["attributes"][field]["tp"] += 1
                        result["attributes"][field]["fn"] -= 1
                        other_refs = [j for j, entity in enumerate(gold[family]) if j != gi and entity.get(field) is not None
                                      and Contract.normalize(entity[field]["value"]) == Contract.normalize(actual["value"])
                                      and anchors(entity[field]) & anchors(actual)]
                        if other_refs:
                            result["adjudications"].append({"code": "AMBIGUOUS_ATTRIBUTE_ASSOCIATION", "family": family, "prediction": winner, "field": field})
                    else:
                        result["attributes"][field]["fp"] += 1
                        a = Contract.normalize(actual["value"])
                        e = Contract.normalize(expected["value"]) if expected is not None else ""
                        variant = expected is not None and (a in e or e in a) and overlap(actual["evidence"], expected["evidence"])
                        if variant:
                            result["adjudications"].append({"code": "TEXT_VARIANT_REVIEW", "family": family, "prediction": winner, "gold": gi, "field": field})
                        else:
                            result["unbackedAttributes"] += 1
                            result["issues"].append({"code": "UNBACKED_ATTRIBUTE", "family": family, "prediction": winner, "gold": gi, "field": field})
            for duplicate in (pi for pi in indices if pi != winner):
                for field in TEXT_FIELDS:
                    extra = predicted[duplicate].get(field)
                    if extra is not None:
                        result["attributes"][field]["fp"] += 1
                        expected = reference.get(field)
                        supported = expected is not None and Contract.normalize(extra["value"]) == Contract.normalize(expected["value"]) and bool(anchors(extra) & anchors(expected))
                        if not supported:
                            result["unbackedAttributes"] += 1
                            result["issues"].append({"code": "UNBACKED_DUPLICATE_ATTRIBUTE", "family": family, "prediction": duplicate, "field": field})

    def warning_signature(warning, predicted):
        targets = []
        for pointer in warning["targets"]:
            family, index, suffix = pointer_parts(pointer)
            if predicted:
                aligned = mapping.get((family, index))
                if aligned is None:
                    targets.append(("unmatched", family, index, suffix))
                    continue
                family, index = aligned
            targets.append((family, index, suffix))
        return warning["code"], tuple(sorted(targets))

    required = Counter(warning_signature(w, False) for w in gold["requiredWarnings"])
    actual = Counter(warning_signature(w, True) for w in response["warnings"])
    result["missingWarnings"] = sum((required - actual).values())
    result["additionalWarnings"] = sum((actual - required).values())
    for signature in actual - required:
        if signature[0] in {"AMBIGUOUS_CONTEXT", "CONTRADICTORY_MENTIONS"}:
            result["adjudications"].append({"code": "UNEXPECTED_SEMANTIC_WARNING", "warningCode": signature[0], "targets": signature[1]})
    result["discardedEntities"] = sum(w["code"] == "ENTITY_DISCARDED" for w in response["warnings"])
    result["discardedAttributes"] = sum(w["code"] == "ATTRIBUTE_DISCARDED" for w in response["warnings"])
    no_actual = not any(response[f] for f in FAMILIES)
    no_gold = not any(gold[f] for f in FAMILIES)
    result["cleanEmpty"] = int(no_actual and no_gold and not response["warnings"])
    result["emptyWithWarnings"] = int(no_actual and bool(response["warnings"]))
    result["automaticAdjudications"] = copy.deepcopy(result["adjudications"])
    resolved = {r["index"] for r in review.get("resolvedAdjudications", [])
                if r.get("decision") == "supported" and r.get("reason", "").strip()}
    result["adjudications"] = [a for i, a in enumerate(result["adjudications"])
                               if i not in resolved or a["code"] == "AMBIGUOUS_ENTITY_ANCHOR"]
    return result


def ratios(row):
    total_predictions, total_gold = row["tp"] + row["fp"], row["tp"] + row["fn"]
    return {**row, "precision": row["tp"] / total_predictions if total_predictions else None,
            "recall": row["tp"] / total_gold if total_gold else None}


def bucket(cases, scored):
    result = {"cases": len(cases), "entities": {f: counts() for f in FAMILIES},
              "attributes": {f: {**counts(), "absent": 0} for f in TEXT_FIELDS},
              "catalogs": {f: {"correct": 0, "evaluated": 0, "notProvided": 0} for f in FAMILIES}}
    scalar = ("operationalFailures", "invariantViolations", "criticalContextErrors", "unbackedAttributes", "injectionViolations",
              "missingWarnings", "additionalWarnings", "discardedEntities", "discardedAttributes", "cleanEmpty", "emptyWithWarnings", "semanticSupportFailures")
    result.update({f: sum(s[f] for s in scored) for f in scalar})
    result["adjudications"] = sum(len(s["adjudications"]) for s in scored)
    for s in scored:
        for category in ("entities", "attributes", "catalogs"):
            for name, row in s[category].items():
                for key, value in row.items():
                    result[category][name][key] += value
    for category in ("entities", "attributes"):
        result[category] = {f: ratios(row) for f, row in result[category].items()}
    for row in result["catalogs"].values():
        row["accuracy"] = row["correct"] / row["evaluated"] if row["evaluated"] else None
    return result


def aggregate(cases, scored):
    if len(cases) != len(scored) or [c["id"] for c in cases] != [s["id"] for s in scored]:
        raise ValueError("Results must cover the exact ordered case set")
    result = bucket(cases, scored)
    result["cohorts"] = {}
    for cohort in sorted({c["cohort"] for c in cases}):
        selected = [i for i, c in enumerate(cases) if c["cohort"] == cohort]
        result["cohorts"][cohort] = bucket([cases[i] for i in selected], [scored[i] for i in selected])
    for dimension, field in (("families", "family"), ("locales", None), ("sizes", None)):
        groups = defaultdict(list)
        for i, case in enumerate(cases):
            key = case[field] if field else case["request"].get("locale", "es") if dimension == "locales" else ("<=1000" if len(case["request"]["clinicalNote"]) <= 1000 else ">1000")
            groups[key].append(i)
        result[dimension] = {k: bucket([cases[i] for i in indices], [scored[i] for i in indices]) for k, indices in sorted(groups.items())}
    core = result["cohorts"].get("core")
    thresholds = bool(core) and all(row["tp"] + row["fp"] > 0 and row["tp"] + row["fn"] > 0
                                  and 100 * row["tp"] >= 95 * (row["tp"] + row["fp"])
                                  and 100 * row["tp"] >= 90 * (row["tp"] + row["fn"])
                                  for row in core["entities"].values())
    zero_tolerance = all(result[key] == 0 for key in ("invariantViolations", "criticalContextErrors", "unbackedAttributes", "injectionViolations", "adjudications", "semanticSupportFailures"))
    result["automaticCriteriaPass"] = bool(thresholds and zero_tolerance and core["operationalFailures"] == 0 and result["missingWarnings"] == 0)
    result["semanticAuditComplete"] = all(s["semanticAuditVerified"] for s in scored)
    result["pass"] = result["automaticCriteriaPass"] and result["semanticAuditComplete"]
    return result

