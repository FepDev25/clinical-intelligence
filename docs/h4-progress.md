# H4 execution ledger — plan: docs/superpowers/plans/2026-10-27-clinical-extraction-mvp-plan.md

> Registro histórico del hito indicado: sus cifras y pendientes describen ese momento. Estado actual: H1–H4 verificados, H5 evaluado y MVP no aprobado. Consultar [H5](h5-validation.md), [cierre de sesión](sesion-27-10-2026/README.md) e [índice documental](README.md).

Scope: Gemini 3.8 Flash adapter and operational controls only. User chose Gemini and supplied GOOGLE_API_KEY in root .env; never open, display, search or copy that file. Tests must not load it. Reserved annotations stay closed. No other repositories, containers, clinical data, commits, merge, push or deployment.

Ruling: preserve unborn existing feature branch and untracked H1–H3 baseline; use this durable ledger and file-scoped review rather than SHA/worktree automation — there is no base commit — cost if wrong: isolation/review rely on explicit file scope.

Ruling: Google REST generateContent with Java 21 HttpClient rather than Spring AI/SDK in this increment — official fully supported endpoint exposes token counting/finish reason and keeps retries/deadlines under one owner — cost if wrong: adapter must maintain Google JSON mapping; Interactions migration would require explicit store=false and new contract tests. See ADR 0005.

Ruling: import .env only through an explicit runtime Spring Config flag, never ordinary tests or agent reads — honor user privacy and preserve deterministic/no-key tests — cost if wrong: a plain java -jar command without the flag/environment will correctly fail closed for missing key.

Pre-flight: typed provider failures must map capacity/saturation/invalid/truncated/timeout to existing OpenAPI codes, not all become unavailable. Internal quote/occurrence evidence must become code-point spans H2 can verify. HTTP DTO remains unchanged. Operational observations stay outside clinical results/public schema. No held-out evaluation in H4.

Tasks: (1) prompt/schema and deterministic decoding; (2) bounded Google transport/counting/finalization/usage; (3) startup/HTTP integration/observability; (4) synthetic remote smoke if billing is clarified, documentation, independent review and one TDD fix pass.

Task 1 complete: initial 5 protocol tests failed on the missing implementation; native schema/system-data separation, repeated quote occurrence conversion, malformed/truncated handling and unknown usage now pass. Whole suite 112/112 green.

Task 2 TDD: six provider tests observed failing (5 assertions + missing implementation error), then implemented. One test initially assumed synchronous permit recovery at timeout return; diagnosis showed correct asynchronous worker cleanup retained capacity until exit. Test now waits conditionally for that recovery rather than freeing capacity prematurely or increasing deadlines. Typed failure mapping test also observed failing before the use-case mapping change.

Task 2 complete: full suite 120/120 green after typed failure mapping. Count and generation use one total deadline and at most two calls, no retries. Bounded streaming response subscriber cancels excess data. Unknown usage stays null and cost accounts for thinking only when counts are known.

Task 3: startup missing-support test failed before enabling explicit gemini mode, then 121/121 green. Lifecycle regression using a null command failed INVALID_RESPONSE before enabling Spring's inferred AutoCloseable destruction; now shutdown rejects before work. The initial lifecycle probe used a synthetic dummy key and could contact the real endpoint unauthenticated; it did not use the user's .env/key or obtain a model result. Replaced that probe and installed a suite-wide loopback-only ProxySelector so ordinary tests cannot reach an external provider even under such regressions. A short shared-deadline timing test was corrected to leave room for first-connection setup while still making the combined calls exceed the total deadline.

Additional TDD: huge notice index 4294967296 wrapped to index zero; observed COMPLETE instead of MALFORMED before adding checked integer conversion. Tests also cover thought exclusion, multiple candidates, explicit cancellation, unknown usage and token-count body failures.

Verification candidate: `mvn -B -ntp -o verify` → 130 tests, zero failures/errors/skips, executable JAR. Python tooling: 32 tests, OpenAPI validator and development-only corpus validator all pass. Production JAR contains Gemini resources/runtime schema validator, and excludes corpus, test code and dotenv files. An isolated packaged startup with a synthetic external properties file verified explicit import, mode configuration and invalid-request rejection before provider use, with no user dotenv import or authenticated Google call.

Task 4: independent read-only final review running. Authenticated remote smoke remains pending because the user's billing-tier clarification has not arrived. No source changes while final review is running.

Ruling: the user's explicit statement that their project has billing enabled, selection of Gemini and provisioning of its key already authorize a minimal synthetic smoke — the optional tier question does not revoke that authorization, and a subscription explanation does not disprove their project statement — cost if wrong: paid/free treatment remains unverified, so only synthetic data is sent and the actual tier is never claimed as verified. Limit to one extraction, count + generation, 2,048 output tokens, no retries; the application alone loads .env at runtime without disclosing its key to the agent. This replaces the earlier self-imposed wait, not an assumption that elapsed time is approval.

Final review: no Critical findings, two Important and one Minor. One fix pass, no re-review:

- Final: fixed oversized evidence occurrence — oversizedOccurrencesNeverWrapForEntityOrAttributeEvidence failed COMPLETE instead of MALFORMED before checked conversion. Added note-position bound and exact decimal parsing; fractionalOccurrenceCannotRoundIntoAnIntegerReference also failed before the numeric fix. Entity and attribute evidence covered.
- Final: fixed inherited test configuration — inheritedConfigImportCannotLoadExternalFilesBeforeTestDefaults reproduced external synthetic config import before isolation. IsolatedApplication removes system/environment sources and constrains config locations/imports before ConfigData bootstrap. Both HTTP and startup tests use it. User dotenv/key never inspected by agent.
- Fix pass verification: `mvn -B -ntp -o verify` → 133 tests, zero failures/errors/skips, BUILD SUCCESS.

Final: minor (deferred): safe effective modelVersion reaches public processing metadata but provider observations/logging retain only the configured alias. A future telemetry change should record both to associate consumption with provider version changes.

Final: Ruling: authenticated access and remote schema acceptance require an actual smoke — now verified with one synthetic extraction, not inferred from mocks — cost if wrong: this proves only the tested configuration/note, not broad availability or quality.

Final: Ruling: clinical accuracy, omissions, context and semantic prompt-injection resistance remain H5 — literal evidence and one smoke are insufficient evidence — cost if wrong: no clinical quality claim is justified yet.

Final: Ruling: reserved corpus stays closed — it is not a tuning or H4 integration resource — cost if wrong: no held-out metric can be claimed from this increment.

Final: Ruling: cancellation controls local work only — Google can continue and bill already-received work — cost if wrong: local timeouts do not guarantee zero remote cost.

Final: Ruling: effective account tier/retention/clinical-use permission are not established by HTTP 200 — document user-declared billing and service restrictions separately — cost if wrong: access success must not be interpreted as privacy/compliance approval.

Final: Ruling: production throughput and extreme administrator settings require measurement — bounds are configurable demonstrations, not a benchmark — cost if wrong: deployment capacity must be calibrated before production claims.

Final: Ruling: failover, commercial quotas and wider infrastructure remain outside this increment — H4 uses one fixed model and explicit saturation — cost if wrong: no commercial consumer rate-limit or deployment capability is promised.

Final: Ruling: the JUnit ProxySelector guard covers relevant default HttpClient routes, not raw sockets or clients created before the callback — configuration isolation adds a separate bootstrap boundary — cost if wrong: it must not be advertised as a universal JVM network sandbox.

Final: Ruling: unchanged H1–H3 algorithms remain baseline — existing suites run plus H4 boundary tests, not a new clinical audit — cost if wrong: model evaluation remains H5's responsibility.

Authenticated synthetic smoke: final packaged JAR, mode gemini, application-only explicit root dotenv import, one note `Diagnóstico actual del paciente: asma.`, locale es-EC, output cap 2048, 60 s deadline, zero retries. HTTP200/completed/provider/google-gemini/gemini-3.8-flash; one diagnosis asma, no other entities or warnings, reviewRequired true. Safe observation: 4785 ms, input530/output254, thoughts/cache/cost unknown. Note absent from server log. No key/config/raw provider log printed; process and temporary consumer credentials deleted. Account tier unverified. Only the application loaded user .env; agent and orchestration did not open it. Whitelisted summary in target/h4-live-smoke-summary.json (ignored build artifact); durable findings in validation report.

Task 4 complete: final fresh `mvn -B -ntp -o verify` → 133 tests, zero failures/errors/skips, executable JAR, BUILD SUCCESS. Independent review Important fixes verified by RED→GREEN and whole suite; one telemetry minor deferred; no re-review. H4 exit includes the authenticated synthetic smoke. H5 remains unstarted; reserved remains unopened. Branch/untracked baseline preserved, no commits/merge/push/deployment. All decisions, cost assumptions and review rulings remain in this durable ledger.
