# H3 execution ledger — plan: docs/superpowers/plans/2026-10-27-clinical-extraction-mvp-plan.md

> Registro histórico del hito indicado: sus cifras y pendientes describen ese momento. Estado actual: H1–H4 verificados, H5 evaluado y MVP no aprobado. Consultar [H5](h5-validation.md), [cierre de sesión](sesion-27-10-2026/README.md) e [índice documental](README.md).

Scope: H3 only; approved spec, ADR 0002 and OpenAPI remain authoritative. H2 is the baseline. Do not read reserved annotations. No remote model, containers, database, SFM/PreAudit changes or public deployment.

Ruling: continue on the existing feature branch and preserve the untracked baseline, without automatic commits or worktree creation — unborn HEAD prevents a useful separate checkout — cost if wrong: review uses explicit file scope rather than a commit range. Use this durable ledger instead of SHA-based execution scripts for the strategic plan.

Ruling: Spring Boot 3.5.16, Java 21, Jackson aligned by the Boot BOM — published Maven artifact verified and Boot 3.5 supports Java 21 and Spring AI 1.1.x — cost if wrong: H4 must recheck the eventual model SDK against dependency management. Spring AI is not installed in H3. Dependency inspection caught and removed overrides that mixed Jackson 2.20/2.21 and JUnit aggregate/engine versions; the BOM properties now align each family.

Pre-flight: HTTP consumes H2 Command and maps Result, never serializes domain directly. H2 locale syntax must be preceded by registry validation; omitted values differ from explicit JSON null. HTTP request ID replaces the core ID on the wire so errors and success have one correlation ID. Auth precedes decoding and provider calls. Real-provider operational codes stay contracted but cannot be triggered by caller-controlled fake switches.

Tasks: (1) credentials/configuration/registry; (2) strict wire mapping; (3) HTTP/filter/errors; (4) external contract and startup verification/documentation; (5) independent final review and TDD fix pass.

Baseline: `mvn -B -ntp -o test` → 86 tests, zero failures/errors.

Task 1 complete: credentials and registered locale policies; initial stub run failed (2 assertions, 3 unsupported-operation errors); after implementation and wire task, 95 tests green. One test expectation corrected: QQ is a registered private-use region range, so use unregistered AB as the negative case.

Task 2 complete: strict decoding and explicit output mapping; missing-behavior tests observed failing before implementation; 95/95 green. Runtime Jackson is required by transport, while JUnit remains test-only.

Task 3 first verification: HTTP six tests failed on 404 before implementation; startup two tests failed because missing mode/credentials were accepted. Implemented filter/controller/configuration; 103/103 tests green. Corrected test setup to use a command-line ephemeral port: application.properties takes precedence over builder default properties.

Task 4 complete: `mvn -B -ntp verify` → 107/107 tests green, executable JAR built. Includes all ten public success scenarios, response validation against OpenAPI JSON Schema 2020-12 with NetworkNT (test-only), and 20 concurrent requests across two authenticated consumers with distinct catalogs. Request IDs are unique and notes/catalogs stay isolated. Actual byte boundaries, chunked body limits, Unicode point counts and aggregate structure limits pass. Captured artifacts contain synthetic response data only.

External verification: executable JAR launched with environment configuration, ephemeral loopback port and temporary credentials; independent cURL consumer received 200 simulated and 401 without a key, correct IDs/no-store. Key and synthetic note canary absent from server log. Process stopped and temporary material deleted. Production JAR excludes corpus/JUnit/schema-test dependency. Documented credential-generation snippet executed and creates correct 0600 files without printing keys. Python: 32 tests; OpenAPI validator; development-only corpus validator all pass, no reserved annotations loaded.

Task 5: independent whole-increment read-only review complete. No Critical/Important findings. Reviewer independently exercised the packaged JAR from /tmp, confirmed registry transformation/hash against IANA and inspected the 107-test verification. No source changes were needed; no fix pass or re-review.

Final: minor (deferred): Accept negotiation ignores representation parameters such as profile; an unusual qualified positive range can override rejection of the plain JSON representation. Ordinary application/json and wildcard quality exclusions are covered; parameter-aware negotiation requires a focused future regression.

Final: minor (deferred): one-character-primary BCP 47 forms (`i-klingon`, `x-demo`) are classified INVALID_LOCALE by the inherited OpenAPI syntax filter, while other valid unsupported languages receive UNSUPPORTED_LOCALE. This does not affect Spanish tags. Clarify the restricted syntax or revise schema and validator together in a future contract update.

Final: Ruling: real extraction accuracy and prompt injection remain H4/H5 — H3 provides an authenticated transport with explicit simulation — cost if wrong: integration success must not be advertised as extraction quality.

Final: Ruling: provider budgets/retries/rate limits/saturation/production capacity remain H4 — input bounds alone do not establish operational capacity — cost if wrong: no production-readiness claim is justified until measured and controlled.

Final: Ruling: remote TLS/proxy deployment remains an installation responsibility outside this local increment — H3 defaults to loopback and rejects insecure remote HTTP, does not trust forwarded headers — cost if wrong: deployment owners must verify TLS termination and proxy logs before exposing traffic.

Final: Ruling: container errors before the application filter remain a documented boundary — review reproduced bare Tomcat 405 on TRACE, with no extraction/authentication bypass — cost if wrong: consumers may receive a non-Problem-Details rejection before application dispatch.

Final: Ruling: base BCP 47 registration/syntax does not validate all u/t payload semantics — RFC 5646 permits extension validation separately, and no cultural preference behavior is promised — cost if wrong: consumers cannot assume the server has certified extension-specific preferences.

Final: Ruling: H2 clinical policies are the tested baseline, not a new clinical evaluation in H3 — integration preserves their outputs and runs their suite — cost if wrong: semantic correctness still requires H4/H5 evidence.

Final: Ruling: reserved evaluation stays closed and unmeasured — the agreed separation excludes reserved annotations from implementation and review — cost if wrong: no held-out performance claim can be made from this work.

Task 5 complete: final `mvn -B -ntp -o verify` → 107 tests, zero failures/errors/skips, BUILD SUCCESS and executable JAR. H3 marked complete in strategic plan/spec; H4/H5 remain pending. The existing unborn feature branch and all untracked baseline artifacts are preserved; no commits, merge, push or deployment performed.

Ruling: default simulated startup returns an explicit empty proposal, while tests inject public controlled proposals — H3 proves transport and must not introduce a pretend medical extraction engine — cost if wrong: users must understand that integration success does not demonstrate extraction quality. Remote model remains H4.

Ruling: snapshot IANA registry rather than runtime downloads — locale validity must be offline and reproducible — cost if wrong: maintainers must version and test registry updates. Private-use ranges are intentionally valid, while unregistered regions are rejected.
