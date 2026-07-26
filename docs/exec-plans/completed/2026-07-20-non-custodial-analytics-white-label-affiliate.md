# Authenticate Provider Confirmation And Resume Pending Attribution Delivery

Status: completed on 2026-07-20 after the 34/34 repository gate matrix passed.

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current as work proceeds. It follows `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The completed attribution slice already records redacted, idempotent events and retries failed delivery without changing an external venue order. This bounded follow-on makes the delivery result trustworthy and durable across a browser restart. When an approved attribution endpoint returns a public confirmation envelope, the client will parse it, run it through the existing pure validation contract, and show a provider-confirmed settlement only when that validation succeeds. A browser restart will restore persisted pending records and retry the same logical event with its original idempotency key, rather than creating a new event.

The observable result is a deterministic test run: a valid provider response updates the existing event to `:settled`; an HTTP success with malformed, mismatched, or insufficient confirmation data never does; and a persisted `:pending` record produces one resumed request with the same event id after bootstrap. A rejected venue order must remain rejected even while the attribution endpoint fails, returns malformed JSON, or completes later.

## Context References

Durable user context:

- Direct user request on 2026-07-20: continue the approved Hyperopen productization plan with authenticated provider response parsing in the attribution transport, startup resumption of persisted pending delivery without duplicate logical events, non-blocking order behavior, and deterministic tests. Provider-specific secrets must remain outside browser configuration and settlement must require pure validation.

Repository artifacts:

- `/hyperopen/src/hyperopen/service/attribution.cljs` owns the pure redacted event contract. `normalize-provider-result` is the one validator for provider response data.
- `/hyperopen/src/hyperopen/runtime/effect_adapters/attribution.cljs` owns browser `fetch`, retry timers, local persistence, and operator export. It currently marks any `response.ok` delivery as accepted and only restores persistence when a new event is recorded.
- `/hyperopen/src/hyperopen/app/bootstrap.cljs` already installs the attribution operator API during `bootstrap-runtime!`; it is the startup seam for one non-blocking resume attempt.
- `/hyperopen/src/hyperopen/order/effects.cljs` and `/hyperopen/test/hyperopen/core_bootstrap/order_effects/attribution_test.cljs` establish that attribution failures cannot change a venue order result.
- `/hyperopen/docs/BROWSER_STORAGE.md` requires browser persistence to stay at infrastructure boundaries, stay deterministic, and avoid using browser-readable storage for secrets.

Public issue or pull request: none supplied. The direct user request above is the durable work reference.

Local scratch references: none. No Beads or other local tracker is authoritative.

## Scope

This slice changes only the attribution confirmation and restart path.

- Parse a successful attribution HTTP response as JSON in the runtime effect adapter, convert it to the data shape consumed by `normalize-provider-result`, and retain only data that the pure contract allows.
- Preserve the distinction between delivery acknowledgement and settlement. A successful HTTP response may acknowledge delivery; it is never by itself proof of a rebate or settlement.
- Resume persisted `:pending` queue records once per application-store lifetime during application bootstrap, reusing the original safe event payload and `Idempotency-Key`.
- Deduplicate restored records by `:event/id` before attempting delivery, keep the latest serialized record when duplicates exist, and preserve its delivery attempt count.
- Add deterministic unit and integration tests for valid confirmation, invalid confirmation, restart recovery, idempotency, redaction, and unchanged order behavior.

## Non-Goals

- Do not add a backend, provider credential, signing secret, access token, `Authorization` header, or provider-specific secret to `TENANT_CONFIG_JSON`, tenant configuration, browser storage, telemetry, or the event body.
- Do not invent client-side cryptographic verification. The browser consumes only the existing public verification envelope from an approved provider or relay and applies the pure structural, identity, timestamp, and evidence checks already defined by `normalize-provider-result`.
- Do not change wallet signing, external venue request fields, order state ownership, WebSocket behavior, analytics, white-label branding, routes, or views.
- Do not migrate the existing bounded `hyperopen:attribution-events:v1` queue to another persistence backend in this slice. It remains a small, redacted diagnostic/retry queue at the runtime boundary.
- Do not display or calculate a local dollar rebate, alter the queue capacity or retry budget, or retry terminal `:unavailable` records.
- This is reliability and integrity work, not performance work. It adds no throughput optimization, cache, or algorithm that needs a performance baseline or profiling run.

## Progress

- [x] (2026-07-20) The original productization slice created the pure attribution contract, bounded redacted queue, idempotency header, bounded retry path, and non-blocking wallet/order event emission.
- [x] (2026-07-20) Inspected the active transport, pure validator, bootstrap seam, browser storage policy, and existing attribution/order tests; scope is frozen to provider confirmation parsing and pending-delivery startup resumption.
- [x] (2026-07-20) Implemented structured provider JSON parsing, strict `normalize-provider-result` application, namespace-preserving request/queue JSON, bounded retries, hostile-storage filtering, and linear duplicate coalescing in `src/hyperopen/runtime/effect_adapters/attribution.cljs`.
- [x] (2026-07-20) Implemented one-shot startup resumption in `src/hyperopen/app/bootstrap.cljs` without changing the bootstrap return value or allowing attribution failures to escape startup.
- [x] (2026-07-20) Added deterministic service, adapter, bootstrap, and order-isolation coverage. Final focused evidence: 39 tests / 284 assertions / 0 failures / 0 errors; final static review verdict: PASS with no actionable findings.
- [x] (2026-07-20) Ran the attribution implementation gate matrix with the Node local-storage backing file required by this Node 25 environment. ClojureScript passed 5,786 tests / 31,984 assertions; WebSocket passed 560 tests / 3,174 assertions; all app and worker builds compiled with zero warnings. The intermediate matrix was 33/34 because the deterministic release fixture assumed a Git `HEAD` existed.
- [x] (2026-07-20) Browser QA explicitly skipped because this slice changes no route, view, styling, control, or interaction flow.
- [x] (2026-07-20) Built the final release and served it from `out/release-public` at `http://127.0.0.1:8082/trade`; the route health check returns HTTP 200. The bundle budget remains a soft warning at 652,910 gzip bytes versus the 640,000 target.
- [x] (2026-07-20) Removed the release fixture's hidden Git-history dependency by injecting and restoring deterministic build metadata in the test. The release file passed 27/27, release-assets passed 41/41, final static review passed, and the final repository matrix passed 34/34 with 6,505 tests and 35,370 assertions recorded by the gate aggregator.

## Surprises & Discoveries

- Observation: the current transport treats `response.ok` as enough to set `:delivery/status` to `:accepted`; it does not read a response body or call the provider-result validator.
  Evidence: `/hyperopen/src/hyperopen/runtime/effect_adapters/attribution.cljs` lines 155-169 call `.-ok` and then `update-delivery!` directly.
- Observation: persisted events are restored only from `record-attribution-event!`, so an application that starts with pending records but emits no new event never retries them.
  Evidence: `/hyperopen/src/hyperopen/runtime/effect_adapters/attribution.cljs` lines 82-93 define `restore-queue!`, and its only current caller is lines 202-217 in new-event recording.
- Observation: the bootstrap seam is already isolated from trading and installs the attribution operator API after generic runtime setup.
  Evidence: `/hyperopen/src/hyperopen/app/bootstrap.cljs` lines 101-105 call `bootstrap-runtime-once!` and `install-operator-api!`.
- Observation: the current queue serializes only redacted records and limits retained events to 200, but legacy persisted JSON can still contain duplicate ids.
  Evidence: `/hyperopen/src/hyperopen/runtime/effect_adapters/attribution.cljs` lines 17-32 validate records and trim the queue but do not deduplicate restored ids.
- Observation: ClojureScript `clj->js` drops keyword namespaces, so the old queue serializer could turn `:event/id` and `:delivery/status` into ambiguous `id` and `status` keys that could not be restored through the keyword-keyed protocol.
  Evidence: the RED recovery fixtures reproduced the same loss until both production and test wire encoders preserved the full `namespace/name` key.
- Observation: browser local storage cannot prove that a settlement previously came from a provider, even when the stored evidence envelope is structurally complete.
  Evidence: static review constructed a complete local `:settled` record and showed that structural revalidation alone would export it. Restore now strips every reward field and downgrades persisted settlements to `:unknown`.

## Decision Log

- Decision: Treat HTTP acknowledgement and provider settlement as separate facts.
  Rationale: `response.ok` establishes only that the transport reached an endpoint. The event can become `:settled` only when `hyperopen.service.attribution/normalize-provider-result` returns `:settlement/verified? true`; otherwise rebate amount and settlement timestamp are omitted by the existing redaction contract.
  Date/Author: 2026-07-20 / spec_writer.
- Decision: Use the already-recorded event as the provider-validation context and keep its `:event/id`, type, tenant, affiliate, venue, wallet hash, session, and market authoritative.
  Rationale: a response must not be able to substitute another tenant, affiliate, venue, wallet identity, event id, or event type. The pure validator receives the original public context, while the runtime adapter merges only the validated outcome fields permitted by the redaction contract.
  Date/Author: 2026-07-20 / spec_writer.
- Decision: Resume only persisted `:pending` records, once per app-store lifetime, using the stored event and stored attempt count.
  Rationale: this retries the same logical event with the same idempotency key without creating a new event, session id, or queue entry. Accepted, observed, and unavailable records are terminal for startup recovery. A record at the retry ceiling becomes unavailable rather than being retried again.
  Date/Author: 2026-07-20 / spec_writer.
- Decision: Deduplicate restored records by event id, retaining the final serialized record and its greatest current delivery state.
  Rationale: the append/update queue format is chronological. Keeping the final instance makes recovery deterministic and prevents a malformed or legacy duplicate array from issuing multiple requests for one logical event.
  Date/Author: 2026-07-20 / spec_writer.
- Decision: Provider authentication remains outside browser configuration.
  Rationale: any provider or relay credential belongs at the trusted provider boundary. The client sends only the existing public redacted event with `credentials: "omit"`, Content-Type, and Idempotency-Key. It accepts no secret-bearing config and stores no raw response or credential.
  Date/Author: 2026-07-20 / spec_writer.
- Decision: Browser QA is explicitly skipped for this slice.
  Rationale: no route, view, browser control, styling, or interaction flow changes. Deterministic ClojureScript tests cover the runtime behavior; a Playwright/browser-inspection pass would not add a stable observable assertion here.
  Date/Author: 2026-07-20 / spec_writer.
- Decision: Treat every persisted settlement and reward field as untrusted after restart.
  Rationale: the browser queue is diagnostic and retry state, not a provider ledger. Only a response validated during the current runtime may produce `:settled`; restore removes rebate and settlement timestamps for every outcome and changes stored `:settled` to `:unknown`.
  Date/Author: 2026-07-20 / worker and reviewer.
- Decision: Resume a pending event only when its tenant, affiliate, and venue identity exactly matches the active normalized tenant configuration.
  Rationale: selecting an endpoint from mutable active configuration without matching the stored event could disclose one white-label tenant's attribution metadata to another tenant endpoint.
  Date/Author: 2026-07-20 / worker and reviewer.

## Outcomes & Retrospective

The provider-confirmation and restart-recovery slice is implemented. HTTP success is no longer settlement proof: the adapter reads structured JSON, routes it through the pure validator, updates the existing safe event in place, and retries malformed or mismatched confirmations without changing the venue order result. Startup restores only structurally valid redacted records, removes every persisted reward field, downgrades persisted settlements, coalesces duplicate ids in linear time with terminal delivery precedence, and resumes only pending records that match the active tenant identity.

Final focused attribution evidence is 39 tests / 284 assertions / 0 failures / 0 errors. The complete ClojureScript suite passed 5,786 tests / 31,984 assertions, the WebSocket suite passed 560 tests / 3,174 assertions, and all application/worker/test compilation completed with zero warnings. The release fixture now supplies deterministic build metadata without requiring a local Git `HEAD`; its file passed 27/27 and release-assets passed 41/41. The final repository matrix is 34/34 PASS with 6,505 tests and 35,370 assertions recorded by the aggregator. Browser QA was skipped by design because no UI surface changed. A final release build succeeded and is served at `http://127.0.0.1:8082/trade`; its soft bundle advisory is 652,910 gzip bytes versus the 640,000 target.

Static review initially found forged persisted settlement, cross-tenant replay, a legacy authentication bypass, and terminal duplicate replay risks. All were fixed and covered by deterministic regressions; the final review report is PASS with no actionable findings. The residual product rule is explicit: `HYPEROPEN_ATTRIBUTION` and local storage are operator diagnostics only and must never be used as authoritative payout records.

## Context and Orientation

Hyperopen is a ClojureScript browser application. Pure service namespaces transform data without browser side effects. Runtime effect adapters own browser APIs such as fetch, timers, and local storage. `hyperopen.runtime.effect-adapters.attribution` stores queue records shaped as:

    {:event safe-event
     :delivery/status :observed-or-pending-or-accepted-or-unavailable
     :delivery/attempt-count non-negative-integer}

`safe-event` is already whitelist-redacted by `hyperopen.service.attribution`. Its `:event/id` is deterministic and is also sent as the HTTP `Idempotency-Key`; an endpoint receiving the same key must treat retries as one logical delivery.

For this plan, a provider confirmation is a JSON object returned by the configured public attribution endpoint. The runtime converts it from JavaScript into a keyword-keyed ClojureScript map and passes it to `normalize-provider-result` with the original event's public identity fields. The validator admits `:settled` only when outcome, provider event id, tenant, affiliate, venue, finite timestamps, and public verification evidence all agree. A malformed JSON body, non-map body, subject mismatch, invalid timestamps, missing verification proof, or forged settlement-looking response must not produce `:settled`, a rebate amount, or `:settled-at-ms` in persistence or operator export.

The only browser storage key in scope is `hyperopen:attribution-events:v1`. It contains no private key, seed phrase, raw signature, provider credential, or raw provider response. The queue is currently bounded to 200 redacted records. This plan preserves that version and size; it improves only deterministic restoration and retry.

## Plan of Work

First, retain `normalize-provider-result` as the single pure confirmation gate in `src/hyperopen/service/attribution.cljs`. Do not create a transport-specific settlement predicate. Add or tighten tests around its existing contract so a validated provider response returns `:settlement/verified? true` with the public settlement fields, while every incomplete or mismatched response returns a non-settlement outcome with no reward fields. If a source change is needed, preserve the function's two-argument public signature and closed outcome vocabulary.

Next, extend `src/hyperopen/runtime/effect_adapters/attribution.cljs`. Add a private response parser that invokes the Fetch `Response.json` structured API, converts only a JSON object to a keyword-keyed map, and routes parsing errors through the existing bounded delivery-failure path. On an HTTP failure, retain the current retry behavior. On an HTTP success with valid JSON, construct the validation context from the already-queued event, call `attribution/normalize-provider-result`, and update the same queue record rather than appending another record. Preserve the original event id, type, market, tenant, affiliate, venue, session, and wallet hash. Apply a normal accepted/rejected/unknown provider outcome only through the pure result; apply `:provider-event-id`, `:rebate-amount`, `:settled-at-ms`, provider evidence, and `:settlement/verified?` only when the pure result is verified. Pass the updated event through `redact-attribution-event` before state persistence and export.

An HTTP-success response whose JSON cannot be parsed or cannot validate must enter the current retry/failure logic, not be silently marked accepted. Retried physical requests reuse the exact event and idempotency key. The endpoint acknowledgement and a valid non-settlement provider outcome may set `:delivery/status :accepted`; neither can manufacture a settlement. Keep fetch options `:credentials "omit"`, the public endpoint selection, and no-provider behavior unchanged.

Then make restoration idempotent. Refine queue parsing/restoration to discard invalid or secret-bearing records, retain at most 200 valid records, and coalesce duplicate event ids by final serialized occurrence. Add a public runtime helper with a dependency-injected arity, such as `resume-pending-delivery!`, which restores once, checks an in-memory startup-resumed marker, and attempts only valid pending records whose stored attempt count is below `max-delivery-attempts`. It must call the existing delivery function with the stored event and the next attempt count. It must not call `build-attribution-event`, create a session id, add a queue entry, or retry a terminal record. If the current tenant has no endpoint, leave pending records intact and perform no fetch. If the helper itself encounters storage, timer, or fetch failures, absorb them into attribution state and return promptly.

Wire that helper from `src/hyperopen/app/bootstrap.cljs` after runtime bootstrap and operator API installation. Preserve the existing `bootstrap-runtime!` result and ensure attribution initialization cannot throw through bootstrap or delay order/runtime setup. The helper's per-store marker must make repeated bootstrap/reload invocation a no-op for already-resumed records; a new app store after a browser reload is eligible to resume persisted pending work.

Finally, extend only the existing attribution and startup test surfaces. Use controlled promise-based fake fetch responses, in-memory storage, deterministic clocks, and immediate retry schedulers. Tests must assert state, queue serialization, event ids, request headers, and call counts rather than wall-clock timing or live network behavior. No browser test is added because no UI-facing path changes.

## Touched Areas

- `docs/exec-plans/active/2026-07-20-non-custodial-analytics-white-label-affiliate.md`: this scoped, living execution plan and final evidence.
- `src/hyperopen/service/attribution.cljs`: retain and, only if tests expose a contract gap, minimally harden `normalize-provider-result` without introducing another validator.
- `src/hyperopen/runtime/effect_adapters/attribution.cljs`: JSON response parsing, validated record updates, deterministic restore deduplication, and `resume-pending-delivery!`.
- `src/hyperopen/app/bootstrap.cljs`: one safe resume call during runtime bootstrap.
- `test/hyperopen/service/trade_attribution_acceptance_test.cljs`: provider confirmation and redaction acceptance cases.
- `test/hyperopen/runtime/effect_adapters/attribution_test.cljs`: provider JSON, malformed/mismatched response, retry, restart, duplicate-record, idempotency, and no-secret assertions.
- `test/hyperopen/app/bootstrap_test.cljs` or `test/hyperopen/core_bootstrap/runtime_startup_test.cljs`: bootstrap invokes recovery without changing its result or running recovery twice for one store.
- `test/hyperopen/core_bootstrap/order_effects/attribution_test.cljs`: preserve or strengthen the proof that attribution failures cannot alter the external venue result.

## Concrete Steps

All commands run from `/hyperopen`.

1. Bootstrap dependencies before any compile or gate.

       npm run setup:worktree

   Expected result: the command either links the shared `node_modules` or explains that dependencies must be installed. Do not classify an unbootstrapped worktree as an attribution defect.

2. Materialize the deterministic tests described in this plan, then generate and compile the complete ClojureScript test runner.

       npm run test:runner:generate
       npx shadow-cljs --force-spawn compile test

   Expected result: test compilation exits zero. The generated runner contains the modified existing test namespaces; no generated runner source is edited by hand.

3. Run the deterministic ClojureScript suite.

       node out/test.js

   Expected result: zero failures and zero errors. The named service, adapter, bootstrap, and order attribution tests demonstrate the cases in Validation and Acceptance without a live endpoint.

4. Run all repository quality gates after the source and tests are green.

       npm run gates

   Expected result: the PASS/FAIL matrix includes `npm run check`, `npm test`, and `npm run test:websocket`. Record the complete matrix in `Progress` and `Outcomes & Retrospective`. Any pre-existing unrelated failure must be named with its command and evidence; do not hide it as an attribution result.

5. Browser QA is not required for this slice. Run no Browser MCP session and no Playwright command unless implementation expands into a route, view, or interaction flow. If that scope changes, update this plan first and follow `docs/BROWSER_TESTING.md` before declaring completion.

## Validation and Acceptance

This slice is accepted only when all of the following are observable through the named deterministic tests and commands.

- Given a stored safe event and an HTTP 2xx response whose JSON supplies matching tenant, affiliate, venue, provider event id, finite ordered timestamps, and the public verified evidence accepted by `normalize-provider-result`, the adapter keeps the original event id/type/market, records `:delivery/status :accepted`, and exports exactly one redacted event whose `:outcome` is `:settled` and whose provider event id and settlement timestamp match the validated response.
- Given an HTTP 2xx response with malformed JSON, a JSON array/string, mismatched subject, invalid timestamp, missing verification id/proof, or a settlement-looking body that the pure validator rejects, the persisted/exported event is never `:settled`, contains no rebate amount or settlement timestamp, and follows the bounded retry path to `:unavailable` when attempts are exhausted.
- The valid and invalid confirmation tests call `normalize-provider-result` through the runtime adapter. No test or production code decides settlement solely from `response.ok`, a response `:outcome`, or a raw `:verified?` flag outside that pure function.
- Given persisted JSON containing two otherwise valid pending records with the same `:event/id`, startup recovery retains one deterministic record, sends one request, and uses that exact id for both the request `Idempotency-Key` and persisted event. Calling recovery or bootstrap again for the same store makes no second request and creates no second logical record.
- Given one valid pending record with a persisted attempt count below the retry ceiling, application bootstrap starts recovery without waiting for a new wallet/order event. The request body is the stored redacted event, not a rebuilt event, and the next persisted attempt count is correct. A pending record at the ceiling becomes unavailable; accepted, observed, and unavailable records produce zero resume requests.
- Given no configured endpoint, startup recovery returns promptly, leaves pending records intact, and produces no fetch. Given corrupt or secret-bearing stored JSON, recovery restores an empty safe queue and produces no fetch.
- Every inspected request keeps `credentials` set to `omit`, has only the existing public content-type/idempotency protocol needs, and contains no private-key-like value, seed phrase, API secret, access token, raw signature, raw wallet address, or raw provider response. Tenant configuration remains unchanged and accepts no new secret field.
- The external venue rejection test still observes one venue submit, `:submitting? false`, and the venue's rejection error even when attribution throws or provider parsing/retry fails. Attribution status may change independently, but it cannot change the venue result, order submission count, or wallet-signing boundary.
- `node out/test.js` and `npm run gates` produce the recorded final results. Browser QA is recorded as explicitly skipped because the touched areas contain no UI behavior.

## Idempotence and Recovery

The queue remains bounded and only mutated through the attribution runtime adapter. Recording the same logical event retains the existing event id; delivery retries and startup recovery reuse that id and the event payload. The restore function is safe to call repeatedly, but the resume helper is deliberately one-shot for a live store so hot reload/bootstrap does not issue duplicate requests. A browser reload creates a new store and is allowed to resume durable pending records.

For a pending record with attempt count `n`, recovery attempts delivery at `n + 1` only when it remains below the configured maximum. Failure persists the new count before scheduling the next bounded attempt. If parsing or validation fails after an HTTP success, retry with the same idempotency key; do not generate another event. If storage cannot be read or written, retain the in-memory safe state where possible and do not throw through bootstrap or trading.

If the provider changes its public response schema, update only the parser-to-`normalize-provider-result` translation and fixture data, retain the pure validator as the settlement gate, and add both valid and malformed fixtures before deploying. A response requiring a browser-held secret is unsupported by this client; use an approved trusted relay and continue returning only the public evidence envelope to the browser.

## Artifacts and Notes

Baseline before this slice:

    Runtime attribution sends a redacted event with an Idempotency-Key and retries a failure.
    HTTP response.ok currently marks delivery accepted without reading confirmation JSON.
    restore-queue! currently runs only while recording a new event.

Completion evidence must add the exact `node out/test.js` summary and `npm run gates` matrix here. Do not claim provider authentication or settlement based on a manual browser request, an HTTP status, or an unvalidated response body.

## Interfaces and Dependencies

Keep these boundaries and names stable:

    hyperopen.service.attribution/normalize-provider-result
      [context provider-result] -> normalized-safe-provider-result

    hyperopen.runtime.effect-adapters.attribution/record-attribution-event!
      [deps store event-type attrs] -> safe-event

    hyperopen.runtime.effect-adapters.attribution/resume-pending-delivery!
      [deps store] -> non-blocking-nil-or-summary

`context` passed to the pure validator is selected from the existing stored event's public identity fields, not from mutable provider data. `provider-result` is a keyword-keyed map decoded from response JSON. A normalized settlement must carry `:settlement/verified? true`; all other outcomes are non-settlement and cannot retain rebate fields.

The runtime adapter continues to depend only on `fetch-fn`, local-storage get/set functions, clock/random functions used by new-event recording, and retry scheduling. Tests inject these dependencies. The production default dependency map continues to use browser `fetch`, existing platform storage helpers, and the platform timer. No new npm package, browser configuration key, provider SDK, token, or server dependency is introduced.

Plan revision note (2026-07-20): refocused the broad non-custodial/analytics/white-label/affiliate plan on the next bounded implementation slice after the redacted queue and non-blocking transport baseline landed. This revision freezes provider-result parsing and startup retry semantics, makes pure validation the only settlement gate, excludes provider secrets and unrelated product work, and anchors every acceptance condition to deterministic tests or repository gates.

Plan revision note (2026-07-20): recorded the completed provider-confirmation and startup-recovery implementation, hostile-storage and tenant-isolation hardening, final PASS review, focused 39/284 evidence, full 5,786/31,984 ClojureScript and 560/3,174 WebSocket results, explicit browser-QA skip, and the sole remaining pre-existing release-fixture blocker.

Plan revision note (2026-07-20): removed the final release-fixture blocker by making deterministic build metadata explicit and hermetic in the test. Release-assets now passes 41/41, the complete gate matrix passes 34/34, final review is PASS, and this plan is ready to move to `completed/`.
