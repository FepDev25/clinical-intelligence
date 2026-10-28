# H2 execution ledger — plan: docs/superpowers/plans/2026-10-27-clinical-extraction-mvp-plan.md

> Registro histórico del hito indicado: sus cifras y pendientes describen ese momento. Estado actual: H1–H4 verificados, H5 evaluado y MVP no aprobado. Consultar [H5](h5-validation.md), [cierre de sesión](sesion-27-10-2026/README.md) e [índice documental](README.md).

Scope: H2 only. H1 artifacts are the baseline; H3–H5 are not authorized by this increment. Reserved annotations will not be opened or copied into Maven resources.

Baseline: Java 21.0.13, Maven 3.10.0; 32 corpus-tool tests and OpenAPI validation (29 scenarios, 30 rejected mutations) pass. Repository has an unborn HEAD and all existing artifacts are untracked user material.

Ruling: use branch `feat/extraction-core-h2` in the existing workspace, without creating a worktree or committing the untracked baseline — a worktree needs an initial commit and moving/committing unrelated material would exceed this change — cost if wrong: isolation is by branch and careful file scope, not a second directory.

Ruling: Java 21 Maven core, with Spring Boot deferred to H3 and Spring AI to H4 — H2 needs neither HTTP nor a model SDK — cost if wrong: H3 must add dependency management and transport explicitly.

Ruling: ICU4J 77.1 supplies full locale-independent Unicode case folding; JUnit 5.13.4 and Jackson 2.20.1 are test-only — `toLowerCase` does not implement the contracted folding, and JSON fixtures must stay out of runtime dependencies — cost if wrong: dependency upgrade and normalization regression checks are required before changing Unicode data versions.

Pre-flight: H2 consumes H1 context enums, code-point spans and catalog triples. It produces a domain result, not HTTP DTOs. H3 will map that result to the existing OpenAPI without changing the contract. Structural/lexical validation cannot certify semantic clinical correctness; H4/H5 still own model evaluation. No clinical regex extraction engine will be invented to simulate an LLM.

## Progress

- Complete: evidence TDD, 7 initial failures → 9 passing tests; catalog TDD, 8 failures → 17 passing tests; input TDD, 8 failures → 25 passing tests.
- Complete: typed provider/fake, sanitization, outcome handling and request-local state; first complete core suite: 41 passing tests.
- Complete: two association regressions (control duration and other-medication dosage) failed first and pass after scoping to medication evidence; suite 43 passing tests.
- Complete: 39 development corpus cases preserve supported proposals and resolve catalogs; reserved annotations excluded from test resources. Suite 83 passing tests.
- Complete: two Unicode scalar boundary regressions (half-emoji value and malformed metadata) observed failing before fix; 85 passing tests after fix.
- Complete: independent whole-increment review; one Important finding reproduced and fixed below. No critical findings.
- Complete: final offline `mvn -B -ntp -o verify`: 86 tests, zero failures/errors/skips, JAR built. Production JAR excludes corpus and test artifacts. Python tooling: 32 tests pass; OpenAPI and development corpus validators pass.

Ruling: conservatively scope medication attributes to validated affirmation/status evidence naming that medication — a literal from another clause is not sufficient association — cost if wrong: a provider must cite an explicit wider medication block for legitimate multi-line attributes, or they will be discarded with warnings. Broad scopes still need semantic evaluation in H4/H5.

Ruling: adapt the execution-skill bookkeeping to a strategic H2 and unborn repository, using this durable ledger rather than SHA-based scripts/automatic commits — there is no base commit and the approved plan intentionally has no per-file task transcript — cost if wrong: the final review must inspect the H2 file set rather than a git commit range.

## Independent final review

Read-only review by a fresh reviewer, scoped to H2 source/tests/POM/ADRs. Reserved annotations were not opened. The reviewer inspected 85 green test reports and reproduced a malformed-locale stack overflow using an isolated temporary Java probe.

Final: fixed Important — the unbounded repeated locale regex overflowed the Java stack for `es` plus 5,000 `-a` segments. `hugeMalformedLocaleIsControlledAndNeverInvokesProvider` failed with `StackOverflowError` first; stack-safe `Locale.Builder` validation now rejects it with `INVALID_LOCALE` before the provider. No `StackOverflowError` catch and no arbitrary low size cap added. Full suite: 86/86 passing.

Final: minor (deferred): evidence validation rescans a large note for each span and resolves offsets from the beginning. Optimize with a request-local offset index when measuring H4/H5 scale; no evidence-correctness bug or truncation is introduced by the current method.

Final: Ruling: HTTP decoding, explicit JSON nulls, unknown/duplicate keys, byte limits, credentials/scopes, Problem Details and full registered BCP47 validation remain H3 — H2 accepts decoded domain commands, not untrusted HTTP JSON — cost if wrong: these controls must be implemented before exposing the API, not assumed from H2 tests.

Final: Ruling: clinical interpretation, prompt injection behavior of a real model, broad-scope medication semantics and contradiction classification remain H4/H5 evaluation — the core verifies typed structure and literal/scoped support, not clinical truth — cost if wrong: no real-model correctness can be inferred from fake/gold regression tests.

Final: Ruling: provider budgets, operational observability and production capacity remain H4 — there is no real adapter or public deployment in H2 — cost if wrong: production readiness cannot be claimed before those controls and measurements.

Final: Ruling: reserved-corpus quality was intentionally not reviewed or loaded — preserve the agreed methodological separation — cost if wrong: H2 demonstrates policies against development only, not held-out generalization.
