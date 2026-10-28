"""Offline evaluator 1.1.0 candidate. Strict matching inherited from frozen 1.0.1.

Not integrated into a campaign runner. See t3/README.md for metrics and limits.
"""
import copy
import hashlib
import json
import math
import random
import re
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
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


def _legacy_score_case(case, status, response, headers=None, semantic_review=None):
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
            winner = choose_winner(predicted, indices, reference, family, invalid_entities, invalid_attributes)
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

    def coverage(warnings, predicted):
        pairs = set()
        for warning in warnings:
            code, targets = warning_signature(warning, predicted)
            if code == "CONTRADICTORY_MENTIONS":
                continue
            pairs.update((code, target) for target in targets)
            if not targets:
                pairs.add((code, None))
        return pairs

    required = coverage(gold["requiredWarnings"], False)
    actual = coverage(response["warnings"], True)
    result["missingWarnings"] = len(required - actual)
    result["additionalWarnings"] = len(actual - required)
    expected_relations = Counter(warning_signature(w, False) for w in gold["requiredWarnings"] if w["code"] == "CONTRADICTORY_MENTIONS")
    actual_relations = Counter(warning_signature(w, True) for w in response["warnings"] if w["code"] == "CONTRADICTORY_MENTIONS")
    result["missingWarnings"] += sum((expected_relations - actual_relations).values())
    result["additionalWarnings"] += sum((actual_relations - expected_relations).values())
    for code in sorted({code for code, _ in actual - required}):
        if code in {"AMBIGUOUS_CONTEXT", "CONTRADICTORY_MENTIONS"}:
            targets = tuple(sorted((target for c, target in actual - required if c == code and target is not None)))
            result["adjudications"].append({"code": "UNEXPECTED_SEMANTIC_WARNING", "warningCode": code, "targets": targets})
    for signature, count in (actual_relations - expected_relations).items():
        for _ in range(count):
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


def _legacy_bucket(cases, scored):
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


def _legacy_aggregate(cases, scored):
    if len(cases) != len(scored) or [c["id"] for c in cases] != [s["id"] for s in scored]:
        raise ValueError("Results must cover the exact ordered case set")
    result = _legacy_bucket(cases, scored)
    result["cohorts"] = {}
    for cohort in sorted({c["cohort"] for c in cases}):
        selected = [i for i, c in enumerate(cases) if c["cohort"] == cohort]
        result["cohorts"][cohort] = _legacy_bucket([cases[i] for i in selected], [scored[i] for i in selected])
    for dimension, field in (("families", "family"), ("locales", None), ("sizes", None)):
        groups = defaultdict(list)
        for i, case in enumerate(cases):
            key = case[field] if field else case["request"].get("locale", "es") if dimension == "locales" else ("<=1000" if len(case["request"]["clinicalNote"]) <= 1000 else ">1000")
            groups[key].append(i)
        result[dimension] = {k: _legacy_bucket([cases[i] for i in indices], [scored[i] for i in indices]) for k, indices in sorted(groups.items())}
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



VERSION = '1.1.0-candidate'


def canonical_entity(entity):
    """Stable duplicate tie-break, independent of ID and evidence list order.

    Does not choose attributes by their agreement with gold. This is a
    deterministic policy for equally eligible duplicates, not a semantic oracle.
    """
    def clean(value):
        if isinstance(value, dict):
            return {k: clean(v) for k, v in value.items() if k != 'id'}
        if isinstance(value, list):
            return sorted((clean(v) for v in value), key=lambda x: json.dumps(x, sort_keys=True, ensure_ascii=False))
        return value
    return json.dumps(clean(entity), sort_keys=True, ensure_ascii=False, separators=(',', ':'))


def choose_winner(predicted, indices, reference, family, invalid_entities, invalid_attributes=()):
    return min(indices, key=lambda pi: (
        not (state(predicted[pi]) == state(reference) and f'/{family}/{pi}' not in invalid_entities),
        canonical_entity(predicted[pi]),
        tuple(sorted(p.rsplit("/",1)[1] for p in invalid_attributes if p.startswith(f"/{family}/{pi}/")))))


def _validate_review(review, status, response, preview):
    if review is None:
        return
    if review.get('decision') not in {'support_verified','support_failed','pending','operation_failure'} or not isinstance(review.get('notes'), str) or not review['notes'].strip():
        raise ValueError('Audit requires decision and rationale')
    if status == 200 and review['decision'] == 'operation_failure':
        raise ValueError('Operation failure decision conflicts with HTTP status')
    if status != 200 and review['decision'] not in {'operation_failure','pending'}:
        raise ValueError('Failed operation cannot have verified semantic support')
    rejected = set()
    for field in ('invalidEntities','invalidContexts','invalidAttributes'):
        pointers = review.get(field, [])
        if not isinstance(pointers, list) or len(set(pointers)) != len(pointers):
            raise ValueError('Invalid or duplicate rejected pointers')
        for pointer in pointers:
            pattern = r'/(diagnoses|medications|procedures)/(0|[1-9][0-9]*)' + (r'/(mentionedCode|dosage|frequency|route|duration)' if field == 'invalidAttributes' else '')
            if not isinstance(pointer,str) or not re.fullmatch(pattern,pointer):
                raise ValueError('Invalid rejected pointer')
            try:
                value = Contract.pointer(response,pointer)
            except (IndexError,KeyError,TypeError,ValueError) as exc:
                raise ValueError('Audit pointer does not exist') from exc
            if value is None:
                raise ValueError('Cannot reject an absent value')
            rejected.add(pointer)
    resolutions = review.get('resolvedAdjudications', [])
    if not isinstance(resolutions,list):
        raise ValueError('Audit resolutions must be a list')
    if resolutions and (review.get('evaluatorVersion') != VERSION or review.get('automaticAdjudicationsSha256') != adjudications_hash(preview.get('automaticAdjudications',[]))):
        raise ValueError('Resolutions must bind to this evaluator and its exact automatic adjudications')
    seen = set()
    for resolution in resolutions:
        index = resolution.get('index')
        if type(index) is not int or index < 0 or index >= len(preview.get('automaticAdjudications',[])) or index in seen:
            raise ValueError('Invalid, repeated or foreign adjudication index')
        if resolution.get('decision') not in {'supported','unsupported','unresolved'} or not isinstance(resolution.get('reason'),str) or not resolution['reason'].strip():
            raise ValueError('Resolution requires decision and rationale')
        seen.add(index)
    decisions = {}
    for decision in review.get('attributeSupportReviews',[]):
        target = decision.get('target')
        if not isinstance(target,str) or not re.fullmatch(r'/(diagnoses|medications|procedures)/(0|[1-9][0-9]*)/(mentionedCode|dosage|frequency|route|duration)',target):
            raise ValueError('Invalid attribute support pointer')
        try:
            value = Contract.pointer(response,target)
        except (IndexError,KeyError,TypeError,ValueError) as exc:
            raise ValueError('Attribute support pointer does not exist') from exc
        if value is None or target in decisions or decision.get('decision') not in {'supported','unsupported'} or not isinstance(decision.get('reason'),str) or not decision['reason'].strip():
            raise ValueError('Invalid or duplicate attribute support decision')
        if target in rejected and decision['decision'] == 'supported':
            raise ValueError('Attribute both supported and rejected')
        decisions[target] = decision['decision']
    if review['decision'] == 'support_verified' and (rejected or 'unsupported' in decisions.values() or any(r['decision']=='unsupported' for r in resolutions)):
        raise ValueError('Verified audit contradicts explicit rejections')


def _alignment(case, response, invalid_entities, invalid_attributes):
    matches = {}
    ambiguous = set()
    for family in FAMILIES:
        candidates = defaultdict(list)
        for pi, prediction in enumerate(response[family]):
            compatible = [gi for gi, gold in enumerate(case['expected'][family])
                          if Contract.normalize(prediction['name']['value']) == Contract.normalize(gold['name']['value'])
                          and anchors(prediction['name']) & anchors(gold['name'])]
            if len(compatible)==1:
                candidates[compatible[0]].append(pi)
            elif len(compatible)>1:
                ambiguous.add((family,pi))
        matches[family] = {gi: choose_winner(response[family], indices, case['expected'][family][gi], family, invalid_entities, invalid_attributes)
                           for gi,indices in candidates.items()}
    return matches,ambiguous


def adjudications_hash(adjudications):
    return hashlib.sha256(json.dumps(adjudications,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()


def score_case(case, status, response, headers=None, semantic_review=None):
    review = copy.deepcopy(semantic_review) if semantic_review is not None else None
    support_decisions = {r['target']:r['decision'] for r in (review or {}).get('attributeSupportReviews',[])}
    if review is not None:
        review['invalidAttributes'] = sorted(set(review.get('invalidAttributes',[])) | {p for p,d in support_decisions.items() if d=='unsupported'})
    without_resolutions = copy.deepcopy(review)
    if without_resolutions is not None:
        without_resolutions['resolvedAdjudications'] = []
    result = _legacy_score_case(case,status,response,headers,without_resolutions)
    _validate_review(semantic_review,status,response,result)
    resolutions = (review or {}).get('resolvedAdjudications',[])
    resolved = {r['index'] for r in resolutions if r['decision']=='supported'}
    result['adjudications'] = [a for i,a in enumerate(result['adjudications']) if i not in resolved or a['code']=='AMBIGUOUS_ENTITY_ANCHOR']
    result['semanticSupportFailures'] += sum(r['decision']=='unsupported' for r in resolutions)
    result['semanticSupport'] = dict(
        missingAuditCases=int(review is None),
        pendingAuditCases=int(review is not None and review['decision']=='pending'),
        completedAuditCases=int(result['semanticAuditVerified']),
        rejectedEntities=len(set((review or {}).get('invalidEntities',[])) | set((review or {}).get('invalidContexts',[]))),
        rejectedAttributes=len((review or {}).get('invalidAttributes',[])))
    result['evaluatorVersion'] = VERSION
    result['discardedAttributeWarnings'] = result['discardedAttributes']
    result['discardedAttributes'] = len({target for warning in response.get('warnings',[]) if warning.get('code')=='ATTRIBUTE_DISCARDED' for target in warning['targets']}) if not (result['operationalFailures'] or result['invariantViolations']) else 0
    result['attributeReferenceDiscrepancies'] = result['unbackedAttributes']
    rejected_attributes = set((review or {}).get('invalidAttributes',[]))
    result['unbackedAttributes'] = len(rejected_attributes)
    result['attributeSupport'] = dict(confirmedUnsupported=len(rejected_attributes), requiresReview=0, supportedByAudit=sum(d=='supported' for d in support_decisions.values()))
    result['mentionAlignment'] = {f: dict(tp=0,fp=0,fn=len(case['expected'][f])) for f in FAMILIES}
    result['contextDimensions'] = {f: {field:dict(correct=0,incorrect=0,missing=len(case['expected'][f]))
                                         for field in (*CONTEXT_FIELDS, *(('prescriptionStatus',) if f=='medications' else ('performanceStatus',) if f=='procedures' else ()))}
                                   for f in FAMILIES}
    if result['operationalFailures'] or result['invariantViolations']:
        return result
    invalid_entities = set((review or {}).get('invalidEntities',[])) | set((review or {}).get('invalidContexts',[]))
    matches,ambiguous = _alignment(case,response,invalid_entities,rejected_attributes)
    for family in FAMILIES:
        matched = matches[family]
        result['mentionAlignment'][family] = dict(tp=len(matched),fp=len(response[family])-len(matched),fn=len(case['expected'][family])-len(matched))
        for field, metric in result['contextDimensions'][family].items():
            metric['missing'] -= len(matched)
            for gi,pi in matched.items():
                correct = state(case['expected'][family][gi])[field] == state(response[family][pi])[field]
                metric['correct' if correct else 'incorrect'] += 1
    # Historically ambiguous predictions incurred entity FP but their text
    # attributes were not counted. They remain unmatched and need review.
    for family,pi in sorted(ambiguous):
        for field in TEXT_FIELDS:
            if response[family][pi].get(field) is not None:
                result['attributes'][field]['fp'] += 1
                result['attributeReferenceDiscrepancies'] += 1
    flags = set()
    for issue in result['issues']:
        if issue['code'] in {'UNBACKED_ATTRIBUTE','UNBACKED_DUPLICATE_ATTRIBUTE'}:
            flags.add(f"/{issue['family']}/{issue['prediction']}/{issue['field']}")
        if issue['code']=='UNMATCHED_ENTITY':
            family,pi=issue['family'],issue['prediction']
            flags.update(f'/{family}/{pi}/{field}' for field in TEXT_FIELDS if response[family][pi].get(field) is not None)
    flags.update(f'/{family}/{pi}/{field}' for family,pi in ambiguous for field in TEXT_FIELDS if response[family][pi].get(field) is not None)
    for target in sorted(flags):
        if target not in rejected_attributes and support_decisions.get(target)!='supported':
            result['attributeSupport']['requiresReview'] += 1
            result['adjudications'].append(dict(code='ATTRIBUTE_SUPPORT_REVIEW',target=target,
                reason='Reference discrepancy or unmatched occurrence; literal text alone does not establish semantic support'))
    for issue in result['issues']:
        if issue['code'] in {'UNBACKED_ATTRIBUTE','UNBACKED_DUPLICATE_ATTRIBUTE'}:
            issue['legacyCode'] = issue['code']
            issue['code'] = 'ATTRIBUTE_REFERENCE_DISCREPANCY'
    return result


def _diagnostic_bucket(scored):
    result = dict(discardedAttributeWarnings=sum(s['discardedAttributeWarnings'] for s in scored),
                  semanticSupport={k:sum(s['semanticSupport'][k] for s in scored) for k in ('missingAuditCases','pendingAuditCases','completedAuditCases','rejectedEntities','rejectedAttributes')},
                  attributeReferenceDiscrepancies=sum(s['attributeReferenceDiscrepancies'] for s in scored),
                  attributeSupport={k:sum(s['attributeSupport'][k] for s in scored) for k in ('confirmedUnsupported','requiresReview','supportedByAudit')},
                  mentionAlignment={f:ratios({k:sum(s['mentionAlignment'][f][k] for s in scored) for k in ('tp','fp','fn')}) for f in FAMILIES},
                  contextDimensions={})
    for family in FAMILIES:
        result['contextDimensions'][family] = {}
        fields = (*CONTEXT_FIELDS, *(('prescriptionStatus',) if family=='medications' else ('performanceStatus',) if family=='procedures' else ()))
        for field in fields:
            metric = {k:sum(s['contextDimensions'][family][field][k] for s in scored) for k in ('correct','incorrect','missing')}
            denominator = metric['correct']+metric['incorrect']
            metric['accuracyOnAligned'] = metric['correct']/denominator if denominator else None
            result['contextDimensions'][family][field] = metric
    return result


def aggregate(cases, scored):
    if not cases or len({c['id'] for c in cases}) != len(cases):
        raise ValueError('Nonempty unique case set required')
    result = _legacy_aggregate(cases,scored)
    result.update(_diagnostic_bucket(scored))
    result['evaluatorVersion'] = VERSION
    for dimension, key in (('cohorts','cohort'),('families','family'),('locales',None),('sizes',None)):
        for group, summary in result[dimension].items():
            def member(case):
                return case[key] if key else case['request'].get('locale','es') if dimension=='locales' else '<=1000' if len(case['request']['clinicalNote'])<=1000 else '>1000'
            summary.update(_diagnostic_bucket([s for c,s in zip(cases,scored) if member(c)==group]))
    return result


def clustered_uncertainty(cases, scored, *, seed=1729, draws=1000):
    """Descriptive percentile bootstrap, resampling complete origin clusters.

    All repeated rounds of one origin stay together. This is not certification
    or a substitute for the unchanged per-round release thresholds.
    """
    if not cases or len(cases)!=len(scored) or type(draws) is not int or draws<1:
        raise ValueError('Nonempty paired cases/scores and positive draws required')
    clusters=defaultdict(list)
    for case,score in zip(cases,scored):
        clusters[case['originGroup']].append(score)
    origins=sorted(clusters)
    rng=random.Random(seed)
    samples={f:{'precision':[],'recall':[]} for f in FAMILIES}
    for _ in range(draws):
        rows=[s for origin in rng.choices(origins,k=len(origins)) for s in clusters[origin]]
        for f in FAMILIES:
            metric=ratios({k:sum(s['entities'][f][k] for s in rows) for k in ('tp','fp','fn')})
            for k in ('precision','recall'):
                if metric[k] is not None:samples[f][k].append(metric[k])
    def interval(values):
        ordered=sorted(values)
        return [ordered[math.floor((len(ordered)-1)*.025)],ordered[math.ceil((len(ordered)-1)*.975)]] if ordered else None
    return dict(method='origin-cluster percentile bootstrap, descriptive 95%',seed=seed,draws=draws,
                observations=len(cases),originGroups=len(origins),
                warning='Repeated rounds are not independent; small or exposed synthetic corpus does not establish generalization',
                entities={f:{**{k+'Interval':interval(v) for k,v in samples[f].items()},
                             'validDraws':{k:len(v) for k,v in samples[f].items()}} for f in FAMILIES})
