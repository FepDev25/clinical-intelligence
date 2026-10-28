"""Materialize synthetic administrative-padding probes entirely in memory."""


def _evidence(note, quote):
    start = note.index(quote)
    return {"start": start, "end": start + len(quote), "quote": quote}


def _entity(note, identifier, name, clause):
    return {
        "id": identifier, "name": {"value": name, "evidence": [_evidence(note, name)]},
        "mentionedCode": None,
        "context": {key: {"value": value, "evidence": [_evidence(note, clause)]}
                    for key, value in (("assertion", "affirmed"), ("temporality", "current"), ("subject", "patient"))},
        "catalogMatch": {"status": "not_provided", "candidates": []},
    }


def materialize(recipe):
    allowed = {"id", "originGroup", "noteCodePoints", "catalogEntries", "procedureCount"}
    if set(recipe) != allowed:
        raise ValueError("Recipe has missing or unknown fields")
    length, entries, count = (recipe[k] for k in ("noteCodePoints", "catalogEntries", "procedureCount"))
    if any(type(v) is not int for v in (length, entries, count)) or not (0 < length <= 1000000 and 0 <= entries <= 100000 and 3 <= count <= 100):
        raise ValueError("Recipe exceeds the bounded initial probe grid")
    procedure_names = [f"procedimiento sintético P{i:04d}" for i in range(1, count + 1)]
    clauses = [f"Al paciente se le realizó hoy {name}." for name in procedure_names]
    diagnosis = "Diagnóstico actual del paciente: condición sintética R."
    prescription = "Se prescribe hoy al paciente Compuesto de prueba, dosis 1 unidad."
    chunks = [f"Escala sintética {recipe['id']}.\n{clauses[0]}\n{diagnosis}\n{prescription}\n", *[c + "\n" for c in clauses[1:]]]
    remaining = length - sum(map(len, chunks))
    if remaining < 0:
        raise ValueError("Note too short for the specified complete findings")
    gap, extra = divmod(remaining, len(chunks) - 1)
    filler = "Información administrativa de prueba. "
    parts = []
    for i, chunk in enumerate(chunks):
        parts.append(chunk)
        if i < len(chunks) - 1:
            size = gap + (1 if i < extra else 0)
            parts.append((filler * (size // len(filler) + 1))[:size])
    note = "".join(parts)
    expected = {"diagnoses": [_entity(note, "d1", "condición sintética R", diagnosis)],
                "medications": [_entity(note, "m1", "Compuesto de prueba", prescription)],
                "procedures": [_entity(note, f"p{i}", name, clause) for i, (name, clause) in enumerate(zip(procedure_names, clauses), 1)],
                "requiredWarnings": []}
    medication = expected["medications"][0]
    medication.update(prescriptionStatus={"value": "prescribed", "evidence": [_evidence(note, prescription)]},
                      dosage={"value": "1 unidad", "evidence": [_evidence(note, "1 unidad")]},
                      frequency=None, route=None, duration=None)
    for entity, clause in zip(expected["procedures"], clauses):
        entity["performanceStatus"] = {"value": "performed", "evidence": [_evidence(note, clause)]}
    request = {"clinicalNote": note}
    if entries:
        catalog_entries = [{"entryId": f"distractor-{i:06d}", "name": f"Compuesto ajeno {i:06d}", "aliases": []} for i in range(entries - 1)]
        catalog_entries.append({"entryId": "target", "name": "Compuesto de prueba", "aliases": []})
        request["catalogs"] = [{"catalogId": "stress-medications", "version": "1", "kind": "medication", "entries": catalog_entries}]
        medication["catalogMatch"] = {"status": "matched", "candidates": [{"catalogId": "stress-medications", "version": "1", "entryId": "target"}]}
    return {
        "id": recipe["id"], "originGroup": recipe["originGroup"], "split": "stress",
        "family": "procedure", "cohort": "stress", "format": "narrative",
        "tags": ["scale", "evidence_position", "synthetic_padding"], "annotationVersion": "1.0.0",
        "provenance": {"kind": "synthetic", "author": "codex", "review": "author_consistency_checked"},
        "request": request, "expected": expected, "excludedMentions": [],
        "annotationNotes": "Sonda de volumen y posición con relleno administrativo; no representa complejidad clínica real. Las variantes comparten origen solo dentro de stress.",
    }
