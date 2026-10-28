#!/usr/bin/env python3
"""Validate OpenAPI, public fixtures and cross-field contract invariants offline.

This checks documentation artifacts, not an implemented backend or LLM quality.
Run with dependencies from tools/requirements.txt, from any working directory.
"""

import copy
import json
import sys
import unicodedata
from pathlib import Path

import langcodes
from jsonschema import Draft202012Validator, FormatChecker
from openapi_spec_validator import validate_spec
from referencing import Registry, Resource


ROOT = Path(__file__).resolve().parents[1]
CONTRACT_URI = "urn:clinical-intelligence:contract"
DIALECT = "https://json-schema.org/draft/2020-12/schema"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def unique_keys(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_keys)


def local_ref(reference):
    require(not reference.startswith(("http:", "https:")), "Remote fixture reference")
    target = (ROOT / reference).resolve()
    require(target.is_relative_to(ROOT), "Fixture reference escapes openapi/")
    return target


def example_value(reference):
    example = read_json(local_ref(reference))
    require(set(example) == {"summary", "value"}, "Expected Example Object")
    return example["value"]


class Contract:
    def __init__(self):
        self.spec = read_json(ROOT / "openapi.json")
        resource = Resource.from_contents({"$schema": DIALECT, **self.spec})
        self.registry = Registry().with_resource(CONTRACT_URI, resource)

    def schema(self, name, value):
        schema = {"$ref": f"{CONTRACT_URI}#/components/schemas/{name}"}
        Draft202012Validator(
            schema, registry=self.registry, format_checker=FormatChecker()
        ).validate(value)

    def request(self, request):
        self.schema("ExtractionRequest", request)
        self.unicode_scalars(request)
        note = request["clinicalNote"]
        require(note.strip(), "clinicalNote must not be Unicode whitespace only")
        locale = request.get("locale", "es")
        require(langcodes.tag_is_valid(locale), "Invalid BCP 47 tag")
        require("_" not in locale, "Underscore is not a BCP 47 separator")
        require(locale.split("-")[0].lower() == "es", "Unsupported locale")
        catalog_ids = set()
        for catalog in request.get("catalogs", []):
            require(catalog["catalogId"] not in catalog_ids, "Duplicate catalogId")
            catalog_ids.add(catalog["catalogId"])
            entry_ids = [entry["entryId"] for entry in catalog["entries"]]
            require(len(entry_ids) == len(set(entry_ids)), "Duplicate entryId")
            for entry in catalog["entries"]:
                require(entry["name"].strip(), "Blank catalog name")
                require(all(alias.strip() for alias in entry.get("aliases", [])), "Blank alias")

    @staticmethod
    def unicode_scalars(value):
        if isinstance(value, str):
            require(not any(0xD800 <= ord(c) <= 0xDFFF for c in value), "Invalid Unicode scalar")
        elif isinstance(value, dict):
            for key, item in value.items():
                Contract.unicode_scalars(key)
                Contract.unicode_scalars(item)
        elif isinstance(value, list):
            for item in value:
                Contract.unicode_scalars(item)

    def limits(self, request, configured=None):
        limits = {**self.spec["x-runtime-limits"], **(configured or {})}
        require(len(request["clinicalNote"]) <= limits["clinicalNoteCodePoints"], "Note limit exceeded")
        catalogs = request.get("catalogs", [])
        require(len(catalogs) <= limits["catalogCount"], "Catalog count limit exceeded")
        entries = [entry for catalog in catalogs for entry in catalog["entries"]]
        require(len(entries) <= limits["totalCatalogEntries"], "Entry limit exceeded")
        require(sum(len(entry.get("aliases", [])) for entry in entries) <= limits["totalCatalogAliases"], "Alias limit exceeded")
        # Body byte limit is measured on actual transferred bytes; reserialization
        # cannot prove it, because whitespace/escaping can vary. See README.

    @staticmethod
    def evidence(note, evidence):
        start, end = evidence["start"], evidence["end"]
        require(0 <= start < end <= len(note), "Evidence range out of bounds")
        require(note[start:end] == evidence["quote"], "Evidence quote differs from original")

    def grounded(self, note, attribute):
        if attribute is None:
            return
        for evidence in attribute["evidence"]:
            self.evidence(note, evidence)
        require(
            any(attribute["value"] in item["quote"] for item in attribute["evidence"]),
            "Text attribute value lacks literal backing",
        )

    def contextual(self, note, attribute):
        if attribute["value"] == "unknown":
            require(not attribute["evidence"], "unknown must have no evidence")
        else:
            require(attribute["evidence"], "Known context needs evidence")
            for evidence in attribute["evidence"]:
                self.evidence(note, evidence)

    @staticmethod
    def normalize(value):
        return " ".join(unicodedata.normalize("NFC", value).casefold().split())

    def match(self, request, kind, name, match):
        catalogs = [c for c in request.get("catalogs", []) if c["kind"] == kind]
        candidates = set()
        for catalog in catalogs:
            for entry in catalog["entries"]:
                names = [entry["name"], *entry.get("aliases", [])]
                if any(self.normalize(name) == self.normalize(n) for n in names):
                    candidates.add((catalog["catalogId"], catalog["version"], entry["entryId"]))
        expected = [dict(zip(("catalogId", "version", "entryId"), item)) for item in sorted(candidates)]
        status = "not_provided" if not catalogs else "not_found" if not expected else "matched" if len(expected) == 1 else "ambiguous"
        require(match["status"] == status, "Incorrect catalog resolution status")
        require(match["candidates"] == expected, "Catalog candidates differ from request matches")

    @staticmethod
    def pointer(document, pointer):
        require(pointer.startswith("/"), "Expected non-root JSON Pointer")
        current = document
        for part in pointer[1:].split("/"):
            part = part.replace("~1", "/").replace("~0", "~")
            current = current[int(part)] if isinstance(current, list) else current[part]
        return current

    def response(self, request, response, headers):
        self.request(request)
        self.schema("ExtractionResponse", response)
        self.headers(response, headers)
        require(response["locale"] == request.get("locale", "es"), "Locale not preserved/defaulted")
        ids = set()
        for collection, kind in (("diagnoses", "diagnosis"), ("medications", "medication"), ("procedures", "procedure")):
            for entity in response[collection]:
                require(entity["id"] not in ids, "Duplicate entity ID")
                ids.add(entity["id"])
                self.grounded(request["clinicalNote"], entity["name"])
                self.grounded(request["clinicalNote"], entity["mentionedCode"])
                for attribute in entity["context"].values():
                    self.contextual(request["clinicalNote"], attribute)
                for key in ("prescriptionStatus", "performanceStatus"):
                    if key in entity:
                        self.contextual(request["clinicalNote"], entity[key])
                for key in ("dosage", "frequency", "route", "duration"):
                    if key in entity:
                        self.grounded(request["clinicalNote"], entity[key])
                self.match(request, kind, entity["name"]["value"], entity["catalogMatch"])
        for warning in response["warnings"]:
            targets = warning["targets"]
            require(len(set(targets)) == len(targets), "Duplicate warning targets")
            for target in targets:
                self.pointer(response, target)
                require(target.startswith(("/diagnoses/", "/medications/", "/procedures/")), "Warning must target entities")
            if warning["code"] == "ENTITY_DISCARDED":
                require(not targets and warning["source"] == "/clinicalNote", "Discarded entity has no response target")
            if warning["code"] == "ATTRIBUTE_DISCARDED":
                require(targets, "Discarded attribute needs target")
                for target in targets:
                    value = self.pointer(response, target)
                    require(value is None or isinstance(value, dict) and value.get("value") == "unknown", "Discarded attribute still exposed")
            if warning["code"] == "CONTRADICTORY_MENTIONS":
                require(len(targets) >= 2, "Contradiction needs multiple targets")

    @staticmethod
    def headers(body, headers):
        require(headers["X-Request-Id"] == body["requestId"], "Request ID header differs")
        require(headers["Cache-Control"] == "no-store", "Missing no-store")

    def problem(self, problem, status, headers):
        self.schema("Problem", problem)
        self.headers(problem, headers)
        require(problem["status"] == status, "HTTP status differs from Problem Details")
        require(problem["instance"] == "urn:uuid:" + problem["requestId"], "Problem instance differs")
        if status == 401:
            require(headers.get("WWW-Authenticate") == 'ApiKey realm="clinical-intelligence"', "Missing API key challenge")
        if status == 405:
            require(headers.get("Allow") == "POST", "Missing Allow")
        if "Retry-After" in headers:
            require(status in (429, 503) and headers["Retry-After"].isdigit(), "Invalid retry hint")


def must_reject(label, action):
    try:
        action()
    except (ValueError, KeyError, IndexError, TypeError):
        return
    except Exception as exc:
        # JSON Schema ValidationError is also an expected rejection.
        from jsonschema.exceptions import ValidationError

        if isinstance(exc, ValidationError):
            return
        raise
    raise AssertionError(f"Validator accepted invalid fixture: {label}")


def mutation_checks(contract, cases):
    success = next(c for c in cases if c["id"] == "success")
    request = example_value(success["requestExample"])
    response = example_value(success["responseExample"])
    headers = success["responseHeaders"]
    count = 0

    def reject_response(label, change):
        nonlocal count
        changed = copy.deepcopy(response)
        change(changed)
        must_reject(label, lambda: contract.response(request, changed, headers))
        count += 1

    def reject_request(label, change):
        nonlocal count
        changed = copy.deepcopy(request)
        change(changed)
        must_reject(label, lambda: contract.request(changed))
        count += 1

    reject_request("unknown tenantId", lambda x: x.update(tenantId="arbitrary"))
    reject_request("null clinicalNote", lambda x: x.update(clinicalNote=None))
    reject_request("blank note", lambda x: x.update(clinicalNote="\u2003\t\n"))
    reject_request("null catalogs", lambda x: x.update(catalogs=None))
    reject_request("unsupported locale", lambda x: x.update(locale="en-US"))
    reject_request("malformed locale", lambda x: x.update(locale="es_EC"))
    reject_request("invalid Unicode", lambda x: x.update(clinicalNote="\ud800"))
    reject_request("duplicate catalog", lambda x: x["catalogs"].append(copy.deepcopy(x["catalogs"][0])))
    reject_request("duplicate entry", lambda x: x["catalogs"][0]["entries"].append(copy.deepcopy(x["catalogs"][0]["entries"][0])))
    reject_response("invalid quote", lambda x: x["diagnoses"][0]["name"]["evidence"][0].update(quote="inventado"))
    reject_response("invalid offset", lambda x: x["diagnoses"][0]["name"]["evidence"][0].update(start=999999))
    reject_response("unbacked dosage", lambda x: x["medications"][0]["dosage"].update(value="999 mg"))
    reject_response("no entity evidence", lambda x: x["diagnoses"][0]["name"].update(evidence=[]))
    reject_response("catalog ID invented", lambda x: x["medications"][0]["catalogMatch"]["candidates"][0].update(entryId="invented"))
    reject_response("matched without candidate", lambda x: x["medications"][0]["catalogMatch"].update(candidates=[]))
    reject_response("ambiguous with one candidate", lambda x: x["medications"][0]["catalogMatch"].update(status="ambiguous"))
    reject_response("duplicate entity ID", lambda x: x["procedures"][0].update(id="d1"))
    reject_response("missing null duration", lambda x: x["medications"][0].pop("duration"))
    reject_response("procedureCode mismatch", lambda x: x["procedures"][0].update(procedureCode="123"))
    reject_response("review omitted", lambda x: x.update(reviewRequired=False))
    reject_response("truncation as success", lambda x: x.update(processingStatus="truncated"))
    reject_response("unknown context with evidence", lambda x: x["diagnoses"][0]["context"]["assertion"].update(value="unknown"))
    reject_response("known context without evidence", lambda x: x["diagnoses"][0]["context"]["assertion"].update(evidence=[]))
    reject_response("warnings inconsistent with status", lambda x: x.update(processingStatus="completed_with_warnings"))
    reject_response("wrong request ID", lambda x: x.update(requestId="00000000-0000-4000-8000-999999999999"))
    must_reject("duplicate raw JSON key", lambda: json.loads('{"clinicalNote":"a","clinicalNote":"b"}', object_pairs_hook=unique_keys))
    count += 1

    contract.limits(request, {"clinicalNoteCodePoints": len(request["clinicalNote"])})
    must_reject("configured note boundary", lambda: contract.limits(request, {"clinicalNoteCodePoints": len(request["clinicalNote"]) - 1}))
    must_reject("configured catalog boundary", lambda: contract.limits(request, {"catalogCount": 0}))
    must_reject("configured entry boundary", lambda: contract.limits(request, {"totalCatalogEntries": 0}))
    require(len("🩺") == 1, "Expected code point counting")
    count += 3

    problem_case = next(c for c in cases if c["status"] == 502)
    problem = example_value(problem_case["responseExample"])
    problem["status"] = 200
    must_reject("problem HTTP mismatch", lambda: contract.problem(problem, 502, problem_case["responseHeaders"]))
    count += 1

    # Positive/negative normalization boundaries, without medical inference.
    require(contract.normalize("  ÁLFA\t  10 mg ") == contract.normalize("a\u0301lfa 10 mg"), "NFC/case/space normalization")
    for a, b in (("Alfa 10 mg", "Alfa 20 mg"), ("café", "cafe"), ("Alfa-10", "Alfa 10"), ("Alfa oral", "Alfa tópica")):
        require(contract.normalize(a) != contract.normalize(b), "Normalization erased a relevant distinction")
    return count


def main():
    contract = Contract()
    validate_spec(contract.spec, base_uri=(ROOT / "openapi.json").as_uri())
    for schema in contract.spec["components"]["schemas"].values():
        Draft202012Validator.check_schema(schema)
    manifest = read_json(ROOT / "examples" / "manifest.json")
    cases = manifest["cases"]
    ids = [case["id"] for case in cases]
    require(len(ids) == len(set(ids)), "Duplicate scenario ID")
    referenced_files = set()
    for case in cases:
        request = example_value(case["requestExample"])
        body = example_value(case["responseExample"])
        referenced_files.update((case["requestExample"], case["responseExample"]))
        if case["requestOutcome"] == "valid":
            contract.request(request)
            contract.limits(request)
        else:
            must_reject(case["id"], lambda request=request: contract.request(request))
        if "rawRequest" in case:
            must_reject("malformed raw JSON", lambda: json.loads(case["rawRequest"]))
        if "configuredLimits" in case:
            must_reject("example runtime limit", lambda: contract.limits(request, case["configuredLimits"]))
        if case["status"] == 200:
            contract.response(request, body, case["responseHeaders"])
            for evidence in case.get("evidenceChecks", []):
                contract.evidence(request["clinicalNote"], evidence)
        else:
            contract.problem(body, case["status"], case["responseHeaders"])
            if str(case["status"]) in contract.spec["paths"][case["path"]]["post"]["responses"]:
                schema_path = f"#/paths/~1api~1v1~1extractions/post/responses/{case['status']}/content/application~1problem+json/schema"
                Draft202012Validator({"$ref": CONTRACT_URI + schema_path}, registry=contract.registry, format_checker=FormatChecker()).validate(body)

    # Every public fixture must be exercised, and every linked example must exist.
    on_disk = {str(p.relative_to(ROOT)) for p in (ROOT / "examples").glob("*.json") if p.name != "manifest.json"}
    require(referenced_files == on_disk, "Orphan or missing example file")
    operation = contract.spec["paths"]["/api/v1/extractions"]["post"]
    linked_values = []
    linked_values.extend(operation["requestBody"]["content"]["application/json"]["examples"].values())
    for response in operation["responses"].values():
        for media in response["content"].values():
            linked_values.extend(media.get("examples", {}).values())
    for example in linked_values:
        reference = example["$ref"].removeprefix("./")
        require(reference in referenced_files, "OpenAPI example missing in manifest")
        example_value(reference)
    require(operation["x-required-scopes"] == ["extractions:write"], "Scope mismatch")
    require(contract.spec["security"] == [{"ConsumerApiKey": []}], "API key security mismatch")
    count = mutation_checks(contract, cases)
    print(f"PASS: OpenAPI 3.1.1, {len(contract.spec['components']['schemas'])} schemas, {len(cases)} scenarios, {len(on_disk)} example files.")
    print(f"PASS: evidence/Unicode/catalog/header invariants; {count} invalid mutations rejected; normalization boundaries preserved.")
    print("Contract artifacts only: no backend, provider calls or clinical quality evaluation executed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
