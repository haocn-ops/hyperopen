# Integrate Optimizer History API v2

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The portfolio optimizer currently assembles daily price, funding, and vault return history in the browser by fanning out to Hyperliquid and vault detail endpoints. The Hyperopen Optimizer History API v2 offers a backend-owned, validated daily history cache that can return strict instrument identities, proxy and vault lineage, funding summaries, warnings, and optionally pre-aligned returns. After this change, optimizer users should be able to select assets and run the optimizer using the backend history service instead of browser-side history assembly, while still seeing clear row-level warnings for missing, stale, proxied, vault-derived, or insufficient data.

The implementation should be gated so the existing browser-side history path remains available as a legacy fallback until the v2 integration has enough product and QA confidence. A developer can verify the result by enabling the optimizer history API flag, routing mocked API v2 responses in tests, confirming that optimizer rows use API-returned aligned returns, and confirming that legacy Hyperliquid candle/funding requests are not made on the v2 success path.

## Context References

Public refs:

- Direct maintainer request on 2026-05-14 to turn API review findings into a deferred or active ExecPlan. Because implementation is not starting now, this file lives in `/hyperopen/docs/exec-plans/deferred/`.

Repo artifacts:

- `/hyperopen/src/hyperopen/portfolio/optimizer/BOUNDARY.md` is the optimizer boundary and names the history-loader and infrastructure seams.
- `/hyperopen/docs/PLANS.md` explains active, completed, and deferred ExecPlan lifecycle.
- `/hyperopen/.agents/PLANS.md` is the detailed ExecPlan writing contract.
- `/hyperopen/docs/BROWSER_TESTING.md` governs browser validation if this work changes optimizer route behavior.

Local scratch refs, non-authoritative:

- API documents inspected during planning: `/Users/barry/.codex/worktrees/fde8/hyperopen_data_service/API_CONTRACT.md` and `/Users/barry/.codex/worktrees/fde8/hyperopen_data_service/docs/api-usage.md`. During subagent inspection, that worktree appeared empty and a sibling copy at `/Users/barry/.codex/worktrees/60dd/hyperopen_data_service` contained equivalent v2 docs. Treat checked-in API docs or the live API as authoritative when this plan is activated.

## Progress

- [x] (2026-05-14 20:14Z) Reviewed the updated API guide, API contract, live production behavior, optimizer boundary files, history loader, request builder, runtime effect adapter, universe candidate code, readiness code, and focused test surfaces.
- [x] (2026-05-14 20:14Z) Decided to record the work in `/hyperopen/docs/exec-plans/deferred/` because active ExecPlans are only for work being executed now.
- [x] (2026-05-14 21:08Z) Moved this file to `/hyperopen/docs/exec-plans/active/` for implementation in the current Codex worktree.
- [x] (2026-05-15) Milestone 1: Added a gated API v2 client path and kept the legacy history path unchanged by default.
- [x] (2026-05-15) Milestone 2: Added API v2 discovery normalization and carried backend strict instrument IDs through optimizer universe rows without replacing local optimizer IDs.
- [x] (2026-05-15) Milestone 3: Normalized API v2 history bundles into optimizer history inputs, using API-provided aligned returns instead of recomputing stitched returns from close prices.
- [x] (2026-05-15) Milestone 4: Extended readiness, warnings, and UI-facing diagnostics for API v2 lineage, funding, stale, rejected, missing, and proxy states.
- [x] (2026-05-15) Milestone 5: Added route-mocked browser coverage and ran focused optimizer tests plus required validation gates.
- [x] (2026-05-15) Live REPL follow-up: enabled the API v2 path by default and enriched already-selected or queued optimizer rows from discovery at history-request time.
- [ ] (2026-05-15) Completion blocked by the repo docs-review gate: `npm run check` fails in `npm run lint:docs` because unchanged canonical docs are 91 days old with a 90-day review cycle. `npm test`, `npm run test:websocket`, focused CLJS tests, and the route-mocked Playwright spec pass.

## Surprises & Discoveries

- Observation: The optimizer already has an injectable history boundary.
  Evidence: `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` defines dynamic `*request-history-bundle!*`, which defaults to `/hyperopen/src/hyperopen/portfolio/optimizer/infrastructure/history_client.cljs` `request-history-bundle!`.

- Observation: The existing history workflow can remain structurally intact.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_workflow.cljs` already owns request signatures, loading state, stale request rejection, full-load success/error, and selection prefetch merging.

- Observation: The current alignment code recomputes returns from adjacent `close` values and would be wrong for stitched proxy/native series if the stitch creates a level discontinuity.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/alignment.cljs` has `return-series`, which calculates `(- (/ (:close current) (:close previous)) 1)`. The API contract states that stitch-boundary returns may intentionally be `null`.

- Observation: The updated API contract is much closer to frontend needs, but live production still had two mismatches during planning.
  Evidence: live checks showed spot `hl:spot:PURR/USDC` returning funding status `missing` where docs say `not_applicable`, and discovery coverage fields such as `observation_count` and `max_lookback_days` returning `0` while history bundle returned points.

- Observation: Backend instrument identity must not replace current optimizer identity in one step.
  Evidence: current app IDs such as `perp:BTC` and `spot:PURR/USDC` key constraints, Black-Litterman views, worker maps, result payloads, UI row state, and orderbook cost contexts.

- Observation: In the live app, an optimizer row can already be selected before discovery has populated backend IDs.
  Evidence: CLJS nREPL inspection on 2026-05-15 showed the live draft universe contained `{:instrument-id "perp:BTC"}` without `:optimizer-history/instrument-id`, while discovery later mapped `"perp:BTC"` to `"hl:perp:BTC"`. `history-request` now enriches full-load and selection-prefetch request universes from discovery so already-selected rows still use the v2 endpoint.

## Decision Log

- Decision: Store this plan in `deferred/`, not `active/`.
  Rationale: `/hyperopen/docs/PLANS.md` says `active` means work is being executed now. This is an authored planning note for future work, so `deferred/` is the correct lifecycle state.
  Date/Author: 2026-05-14 / Codex

- Decision: Integrate through an optimizer-owned anti-corruption layer, abbreviated ACL.
  Rationale: In this plan, ACL means a small boundary that translates external API field names, status codes, and response shapes into optimizer-owned maps before application code sees them. This keeps views and optimizer domain/application code from branching on raw API JSON.
  Date/Author: 2026-05-14 / Codex

- Decision: Keep current optimizer `:instrument-id` values stable and store backend history IDs separately.
  Rationale: Local IDs already drive optimizer state, persisted scenarios, constraints, Black-Litterman views, worker payloads, and execution previews. Replacing them with backend IDs would create a broad migration and execution risk that is unnecessary for history loading.
  Date/Author: 2026-05-14 / Codex

- Decision: Use the API's aligned returns when available.
  Rationale: The backend owns proxy stitching and intentionally emits `return: null` at boundaries where a return should not be computed. Recomputing returns from price levels would lose that semantic information.
  Date/Author: 2026-05-14 / Codex

- Decision: Treat partial API v2 `200` responses as data plus warnings, not as fallback triggers.
  Rationale: The API is designed for partial success. Falling back when one series is missing or warned would hide backend lineage and warning semantics and could silently change optimizer inputs.
  Date/Author: 2026-05-14 / Codex

- Decision: Default the feature flag off. When enabling for internal testing before proxy disclosure UI is complete, use `native_only`; switch to `approved_proxy_allowed` only when proxy disclosures are visible and tested.
  Rationale: The API supports explicit proxy use, but the optimizer UI must be able to disclose proxy lineage before proxied data becomes the default user path.
  Date/Author: 2026-05-14 / Codex

- Decision: Enable the optimizer history API by default with `:proxy-policy :approved-proxy-allowed`.
  Rationale: The Hyperopen history API is the intended production path, supports batched history requests, and avoids Hyperliquid `/info` fanout/rate-limit pressure. The service uses approved proxy history for assets such as BTC, so `native-only` rejects valid optimizer history. The UI already surfaces approved proxy lineage warnings.
  Date/Author: 2026-05-15 / Codex

## Outcomes & Retrospective

The implementation adds the API v2 client, optimizer-owned ACL, discovery state, runtime adapter wiring, history-client dispatch/fallback, API v2 alignment, readiness and prefetch policy, and route-mocked browser coverage. The API v2 path is enabled by default with `:proxy-policy :approved-proxy-allowed`; legacy browser-side history remains only as an explicit fallback for API transport/HTTP/contract failures.

Validation results:

- `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.infrastructure.history-api-v2-client-test --test=hyperopen.portfolio.optimizer.infrastructure.history-client-test --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-test --test=hyperopen.portfolio.optimizer.application.universe-candidates-test --test=hyperopen.portfolio.optimizer.application.setup-readiness-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-history-test --test=hyperopen.portfolio.optimizer.application.history-workflow-test` passed: 39 tests, 156 assertions.
- `node out/test.js --test=hyperopen.config-test --test=hyperopen.portfolio.optimizer.contracts-test --test=hyperopen.portfolio.optimizer.defaults-test --test=hyperopen.portfolio.optimizer.actions-test --test=hyperopen.portfolio.optimizer.universe-actions-test --test=hyperopen.portfolio.optimizer.application.history-loader-test --test=hyperopen.portfolio.optimizer.application.history-loader-vaults-test --test=hyperopen.portfolio.optimizer.application.request-builder-test --test=hyperopen.portfolio.optimizer.application.history-prefetch-test` passed: 67 tests, 341 assertions.
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/optimizer-history-api-v2.spec.mjs --workers=1` passed: 1 test.
- `npm test` passed: 3924 tests, 21593 assertions.
- `npm run test:websocket` passed: 524 tests, 3043 assertions.
- Live CLJS nREPL probe on port `65161` after the default-enabled follow-up showed `:optimizer-history-api {:enabled? true ...}`, discovery loaded `613` local-to-backend IDs, and a direct history bundle request posted to `https://price-history.hyperopen.xyz/v1/optimizer/history-bundle` with body `{"instruments":[{"client_instrument_id":"perp:BTC","instrument_id":"hl:perp:BTC"}], ...}` and `legacy-count 0`.
- After the default-enabled follow-up, focused tests passed: 20 tests, 95 assertions; route-mocked Playwright passed: 1 test; `npm test` passed: 3925 tests, 21596 assertions; `npm run test:websocket` passed: 524 tests, 3043 assertions.
- `npm run check` failed in `npm run lint:docs` after earlier check subcommands passed. The failing diagnostics are unrelated stale-doc review-cycle failures for unchanged docs including `docs/PRODUCT_SENSE.md`, `docs/RELIABILITY.md`, `docs/SECURITY.md`, product-spec index/docs, and reference index/docs. These docs are 91 days old against a 90-day `review_cycle_days` policy.

Do not move this plan to `completed/` until the docs-review gate is resolved or the platform owner explicitly accepts that blocker.

## Context and Orientation

The optimizer route family lives under `/portfolio/optimize`. Its canonical boundary document is `/hyperopen/src/hyperopen/portfolio/optimizer/BOUNDARY.md`. Optimizer application code should remain pure: it may plan requests and normalize data, but browser/network effects belong in runtime effect adapters or infrastructure namespaces.

The current browser-side history path starts in `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs`. That namespace builds a history environment and delegates to `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer/history.cljs`. The runtime history adapter calls the injectable `:request-history-bundle!` function. By default, that function is `/hyperopen/src/hyperopen/portfolio/optimizer/infrastructure/history_client.cljs` `request-history-bundle!`.

The existing infrastructure history client uses `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/request_plan.cljs` to fan out requests for Hyperliquid candle snapshots, market funding history, and vault details. The result is stored under `[:portfolio :optimizer :history-data]`, with maps such as `:candle-history-by-coin`, `:funding-history-by-coin`, and `:vault-details-by-address`.

The optimizer engine request is built by `/hyperopen/src/hyperopen/portfolio/optimizer/application/request_builder.cljs`. That file calls `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader.cljs` `align-history-inputs`, which currently delegates to `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/alignment.cljs`. The alignment code creates `:calendar`, `:return-calendar`, `:return-series-by-instrument`, `:price-series-by-instrument`, `:funding-by-instrument`, `:eligible-instruments`, `:excluded-instruments`, and warning maps.

The Hyperopen Optimizer History API v2 provides two main optimizer endpoints. `GET /v1/optimizer/instruments` discovers backend-approved instrument IDs and their status. `POST /v1/optimizer/history-bundle` returns daily history for selected rows, keyed by submitted `client_instrument_id`. The backend identity field is called `instrument_id`; examples include `hl:perp:BTC`, `hl:spot:PURR/USDC`, `hl:vault:<id-or-label>`, `hl:hip3:<dex>:<coin>`, and `external:<provider>:<series>`. The frontend must submit strict backend IDs, but should keep local optimizer row identity separate.

The phrase `client_instrument_id` means the frontend's stable row key submitted to the API. In this integration it should be the existing optimizer `:instrument-id`, such as `perp:BTC`. The API echoes it by using it as the key in `series_by_instrument`, which lets the ACL map API series back to current optimizer rows without a broad identity migration.

The phrase "legacy fallback" means the existing browser-side Hyperliquid and vault detail history loader. It should remain available while API v2 is gated, but it should not be used to hide valid API v2 partial responses.

## Plan of Work

### Milestone 1: Add the Gated API v2 Client Path

Add an optimizer history API config entry in `/hyperopen/src/hyperopen/config.cljs`. The config should include `:enabled? true`, `:base-url "https://price-history.hyperopen.xyz"`, `:proxy-policy :approved-proxy-allowed`, `:include-aligned-returns? true`, and `:fallback-to-legacy? true`. The endpoint is the default optimizer history path; legacy browser-side fanout remains available only as fallback.

Create `/hyperopen/src/hyperopen/portfolio/optimizer/infrastructure/history_api_v2_client.cljs`. This file owns network access to `GET /v1/optimizer/instruments` and `POST /v1/optimizer/history-bundle`. It should accept an injected `fetch-fn`, a base URL, a request ID generator, the proxy policy, and the `include-aligned-returns?` setting. It should send `content-type: application/json` for POST and `x-request-id` for GET and POST. It should parse JSON into keywordized ClojureScript maps. HTTP 400 should reject without retry. Retry, if added, should be bounded to transient status codes 429, 500, 502, 503, and 504 only.

Modify `/hyperopen/src/hyperopen/portfolio/optimizer/infrastructure/history_client.cljs` so its public `request-history-bundle!` becomes a dispatching facade. The current implementation should become a private legacy function, for example `request-legacy-history-bundle!`. When `(:enabled? (:optimizer-history-api deps))` is false or absent, the public function must call the legacy implementation and existing tests should pass unchanged. When the flag is true, it should call `history_api_v2_client.cljs` and normalize the response through the API v2 ACL described in Milestone 3. If v2 fails due to transport, HTTP failure, invalid JSON, or unexpected `contract_version`, and `:fallback-to-legacy?` is true, it should call the legacy implementation and add a warning code such as `:optimizer-history-api-fallback`. It must not fallback on a successful partial `200` response.

Modify `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` so `history-env` passes the optimizer history API config and `js/fetch` into the history client deps. The existing dynamic vars should remain testable with `with-redefs`.

### Milestone 2: Normalize Discovery and Preserve Local Identity

Create `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs`. This pure namespace is the ACL for API v2. It should define plain functions that take API maps and current optimizer rows and return optimizer-owned maps. It should convert snake_case keys such as `contract_version`, `dataset_version`, `series_by_instrument`, `common_calendar`, `return_calendar`, and `aligned_returns_by_instrument` into kebab-case keywords. It should convert warning code strings such as `"insufficient-candle-history"` into keywords such as `:insufficient-candle-history`.

Extend optimizer state paths in `/hyperopen/src/hyperopen/portfolio/optimizer/contracts/paths.cljs` and defaults in `/hyperopen/src/hyperopen/portfolio/optimizer/defaults.cljs` for optimizer-scoped API discovery state. Use a path such as `[:portfolio :optimizer :history-discovery]`. The default state should include `:status :idle`, `:contract-version nil`, `:request-id nil`, `:dataset-version nil`, `:loaded-at-ms nil`, `:instruments-by-backend-id {}`, `:backend-id-by-local-id {}`, `:warnings []`, and `:error nil`.

Add a runtime effect to load discovery when the optimizer route or setup universe surface needs it. The exact action/effect names should follow existing optimizer action naming. The effect should call `history_api_v2_client.cljs` discovery when the feature flag is enabled. It should not modify global `[:asset-selector :markets]`; instead, it should store discovery in optimizer-scoped state.

Modify `/hyperopen/src/hyperopen/portfolio/optimizer/application/universe_candidates.cljs` so candidate rows can merge optimizer history discovery metadata. The API field `aliases.hyperopen_market_key` is the helper for mapping backend instruments to current local market keys. For example, if discovery returns backend ID `hl:perp:BTC` with alias `perp:BTC`, a local candidate whose `:key` is `perp:BTC` should carry `:optimizer-history/instrument-id "hl:perp:BTC"`. Existing candidate ordering and search behavior should remain unchanged.

Modify `/hyperopen/src/hyperopen/portfolio/optimizer/actions/common.cljs` `market->universe-instrument` and `exposure->universe-instrument` to preserve backend history metadata when present. The local `:instrument-id` must remain the local key. Store backend identity in a namespaced key such as `:optimizer-history/instrument-id`, and optionally carry `:optimizer-history/display-symbol`, `:optimizer-history/instrument-kind`, `:optimizer-history/history-status`, `:optimizer-history/proxy`, and `:optimizer-history/quality-status` for diagnostics.

If a selected row does not have a backend ID when the API v2 path is enabled, the API v2 request builder should produce a warning such as `:identity-ambiguous` for that local `:instrument-id` and should not synthesize a backend `hl:*` ID.

### Milestone 3: Normalize API v2 History Bundles

In `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs`, add a function with a stable shape such as:

    normalize-history-bundle
      [request api-body]
      => normalized optimizer API history map

The normalized map should preserve `:contract-version`, `:request-id`, `:dataset-version`, top-level `:status`, top-level `:warnings`, and a `:series-by-instrument` map keyed by local optimizer `:instrument-id`. Each series should preserve `:instrument-id` as the backend ID, `:local-instrument-id` as the submitted `client_instrument_id`, `:lineage-kind`, `:series-kind`, `:points`, `:funding`, `:quality`, `:proxy`, and `:warnings`.

Normalize point rows so `:time-ms`, `:close`, `:index-value`, `:return`, `:component`, `:source-id`, and `:proxy-mapping-id` are available as ClojureScript keywords. Do not treat `nil` returns as zero. Do not compute returns across adjacent points when the API provides `:return` or `:aligned-returns-by-instrument`.

Add an alignment entry point to `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader.cljs`. The public `align-history-inputs` should be able to choose between legacy alignment and API v2 alignment. A practical shape is to pass the whole `history-data` map from `/hyperopen/src/hyperopen/portfolio/optimizer/application/request_builder.cljs`, then prefer API v2 normalized alignment when present and fall back to legacy candle/funding/vault maps otherwise.

Modify `/hyperopen/src/hyperopen/portfolio/optimizer/application/request_builder.cljs` so it passes enough `history-data` into `history-loader/align-history-inputs` for this choice. Keep existing output shape for the engine request: `:history` must still contain `:calendar`, `:return-calendar`, `:return-series-by-instrument`, `:price-series-by-instrument`, `:return-intervals`, `:expected-return-series-by-instrument`, `:expected-return-intervals-by-instrument`, `:funding-by-instrument`, `:warnings`, `:freshness`, and `:alignment-source`.

When `include_aligned_returns` is true and API returns `common_calendar`, `return_calendar`, and `aligned_returns_by_instrument`, build `:calendar`, `:return-calendar`, and `:return-series-by-instrument` from those fields. If the API omits aligned returns, build returns only from point-level `:return` values and exclude intervals where any required return is nil. Do not fall back to close-to-close returns for stitched series.

Map funding carefully. API funding `status: "available"` should become a carry source with `:annualized-carry` from the API. API funding `status: "not_applicable"` should become `{:source :not-applicable :annualized-carry 0}`. API funding `status: "missing"` should produce a warning only for funding-enabled instruments; if the local instrument is spot, vault, external, or proxy-only, treat it as not applicable locally and preserve a diagnostic note for support. This guards against the live PURR mismatch observed during planning.

### Milestone 4: Extend Readiness and Warning Semantics

Extend `/hyperopen/src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` with API v2 warning codes. Missing and rejected history should block. Insufficient history and insufficient common history should block by default. `:identity-ambiguous`, `:instrument-kind-mismatch`, `:proxy-mapping-unapproved`, `:proxy-validation-failed`, and `:validation-failed` should block. `:proxy-history-used`, `:vault-derived-history-used`, and `:funding-history-missing` should be visible but nonblocking when the price or return series is otherwise usable. `:stale-history` should be visible; product must decide whether stale data blocks before this plan is completed.

Update `warning-display-message` in `/hyperopen/src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` with user-facing messages for the new codes. Messages should name the affected asset when `:instrument-id` is present. Proxy messages should mention that approved proxy history is included. Vault-derived messages should say the row uses vault return-index history, not market candles. Funding messages should be separate from price history availability.

Update `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_prefetch.cljs` so required history checks work for API v2 normalized history. A v2-loaded row should count as loaded when its local instrument ID has a usable non-missing, non-rejected series in the normalized API history map. Do not require `:candle-history-by-coin` or `:funding-history-by-coin` for v2 rows.

Update view-model or setup-row tests only if user-visible status labels change. View code under `/hyperopen/src/hyperopen/views/portfolio/optimize/` should consume existing view models and readiness data; it should not inspect raw API response fields.

### Milestone 5: Rollout Tests, Browser Coverage, and Validation

Add unit tests before implementation where possible. Create `/hyperopen/test/hyperopen/portfolio/optimizer/infrastructure/history_api_v2_client_test.cljs` for client behavior. It should assert URL construction, request headers, POST body, request ID propagation, contract version validation, HTTP error parsing, no retry for HTTP 400, bounded retry for transient failures if retry is implemented, and fallback behavior when used through `history_client.cljs`.

Create `/hyperopen/test/hyperopen/portfolio/optimizer/application/history_loader_api_v2_test.cljs` for pure normalization. It should cover native price series, stitched native/proxy series with a nil boundary return, vault return-index series, proxy metadata and warnings, funding `available`, funding `missing`, funding `not_applicable`, missing/rejected series, top-level partial warnings, and discovery coverage values of zero that should not by themselves block a bundle-backed run.

Extend `/hyperopen/test/hyperopen/portfolio/optimizer/application/history_loader_test.cljs` and `/hyperopen/test/hyperopen/portfolio/optimizer/application/history_loader_vaults_test.cljs` only where the public history-loader facade behavior changes. Legacy tests should continue to prove current candle/funding/vault behavior.

Extend `/hyperopen/test/hyperopen/portfolio/optimizer/application/universe_candidates_test.cljs` for backend ID alias mapping. The test should show that a local candidate keeps `:key "perp:BTC"` while carrying `:optimizer-history/instrument-id "hl:perp:BTC"`.

Extend `/hyperopen/test/hyperopen/portfolio/optimizer/application/setup_readiness_test.cljs` for API v2 warning policy. Include at least one blocking rejected/missing case, one nonblocking proxy-used case, one vault-derived usable case, and one funding missing case for a perp.

Extend `/hyperopen/test/hyperopen/runtime/effect_adapters/portfolio_optimizer_history_test.cljs` to prove the feature flag selects the v2 path, partial `200` does not fallback, transport failure can fallback when configured, and volatile `request_id` does not dirty run identity.

Add a route-mocked Playwright test under `/hyperopen/tools/playwright/test/optimizer-history-api-v2.spec.mjs`. The test should intercept `GET /v1/optimizer/instruments` and `POST /v1/optimizer/history-bundle`, enable the feature flag through whatever test harness mechanism exists at implementation time, select assets, trigger history loading or optimizer run, and assert that row-level warnings render while usable rows remain usable. It should also assert that successful API v2 loading does not make legacy Hyperliquid `/info` candle/funding requests.

## Concrete Steps

When this plan is activated, work from `/hyperopen`.

First, move this file from deferred to active:

    mv docs/exec-plans/deferred/2026-05-14-optimizer-history-api-v2-integration.md docs/exec-plans/active/2026-05-14-optimizer-history-api-v2-integration.md

Update `Progress` with the activation timestamp and run:

    git status --short

Expected: only the plan move is shown before implementation edits begin.

For Milestone 1, add the config and API client tests first. Run the focused test before implementation and expect failures for missing namespaces or functions:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.infrastructure.history-api-v2-client-test --test=hyperopen.portfolio.optimizer.infrastructure.history-client-test

Expected before implementation: the new API v2 client test fails because the namespace or functions do not exist, while existing history-client tests still describe the legacy behavior. Expected after implementation: all named tests pass.

For Milestone 2, add discovery normalization tests and candidate mapping tests. Run:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-test --test=hyperopen.portfolio.optimizer.application.universe-candidates-test

Expected before implementation: the new discovery tests fail. Expected after implementation: candidates retain local IDs and carry backend history IDs.

For Milestone 3, add bundle normalization and alignment tests. Run:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-test --test=hyperopen.portfolio.optimizer.application.history-loader-test --test=hyperopen.portfolio.optimizer.application.history-loader-vaults-test --test=hyperopen.portfolio.optimizer.application.request-builder-test

Expected after implementation: API v2 aligned returns feed optimizer history without recomputing stitched boundary returns, and legacy alignment tests still pass.

For Milestone 4, add readiness and runtime effect tests. Run:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.application.setup-readiness-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-history-test --test=hyperopen.portfolio.optimizer.application.history-workflow-test

Expected after implementation: blocking and nonblocking warning policies match this plan, v2 and fallback paths are deterministic, and partial API responses do not fallback.

For Milestone 5, run the browser test with a dedicated static server port:

    PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/optimizer-history-api-v2.spec.mjs --workers=1

Expected: the spec passes, mocked API v2 responses are consumed, row-level warnings render, usable rows remain usable, and legacy `/info` history calls are absent on the v2 success path.

Before completing the plan, run the required gates from `/hyperopen`:

    npm run check
    npm test
    npm run test:websocket

Expected: all three commands exit with code 0. Record exact command outcomes in this plan before moving it to `completed/`.

## Validation and Acceptance

Acceptance is met when all of the following are true.

The optimizer history API defaults on and the existing legacy history-client tests pass unchanged through explicit disabled/fallback cases. At runtime, the optimizer uses `GET /v1/optimizer/instruments` and `POST /v1/optimizer/history-bundle` through the optimizer-owned client and ACL. Local optimizer IDs such as `perp:BTC` remain the keys for constraints, views, results, and UI rows, while backend IDs such as `hl:perp:BTC` are sent only to the API as `instrument_id`.

API v2 history bundles normalize into the existing optimizer engine request shape. The optimizer uses API-provided aligned returns when present and does not compute returns across API `return: nil` boundaries. Missing or rejected series block optimizer runs. Usable partial responses preserve usable rows and surface warnings. Proxy and vault-derived lineages are visible to users. Funding missing is shown separately from price history and does not block non-funding instruments.

The route-mocked Playwright test proves the user-facing flow with API v2 responses. The required gates `npm run check`, `npm test`, and `npm run test:websocket` all pass.

## Idempotence and Recovery

This plan is designed for additive implementation. The API v2 client, normalizer, discovery state, and browser test can be added while the legacy path remains available as fallback. If a milestone fails, temporarily disable the feature flag and fix the focused failing tests before broadening validation.

If API v2 returns unexpected contract data, the client should reject with a clear error that includes status, `contract_version` when present, and `request_id` when present. If fallback is enabled, fallback should be explicit through a warning code and should be limited to transport, HTTP, malformed JSON, or contract-version failures. Fallback must not run for successful partial responses.

If discovery coverage fields remain inconsistent with bundle points, do not gate readiness solely on discovery `observation_count` or `max_lookback_days`. Use history-bundle series and warnings as the source of truth for run readiness.

If the API continues returning funding status `missing` for non-funding instruments, normalize those locally as not applicable for optimizer carry while preserving a support diagnostic. Do not block spot, vault, external, or proxy-only rows because of missing funding.

## Artifacts and Notes

Important API fields to preserve for support diagnostics:

    contract_version
    request_id
    dataset_version
    status
    warnings[].code
    instruments[].instrument_id
    instruments[].aliases.hyperopen_market_key
    instruments[].history.native_only
    instruments[].history.approved_proxy_allowed
    series_by_instrument[client_instrument_id].lineage_kind
    series_by_instrument[client_instrument_id].series_kind
    series_by_instrument[client_instrument_id].proxy.proxy_mapping_id
    series_by_instrument[client_instrument_id].quality.status
    series_by_instrument[client_instrument_id].funding.status

Current local ID to backend ID examples:

    perp:BTC       -> hl:perp:BTC
    spot:PURR/USDC -> hl:spot:PURR/USDC
    vault:<addr>   -> hl:vault:<addr-or-label>

Observed live API concerns from the planning review:

    hl:spot:PURR/USDC returned funding.status "missing" where docs described "not_applicable".
    Discovery reported available instruments with observation_count 0 and max_lookback_days 0 while history-bundle returned points.

These concerns should be retested when the plan is activated.

## Interfaces and Dependencies

In `/hyperopen/src/hyperopen/portfolio/optimizer/infrastructure/history_api_v2_client.cljs`, provide client functions shaped like:

    request-instruments!
      [{:keys [fetch-fn base-url request-id]}]
      => js/Promise resolving keywordized API body

    request-history-bundle!
      [{:keys [fetch-fn base-url request-id proxy-policy include-aligned-returns?]} request]
      => js/Promise resolving keywordized API body

The `request-history-bundle!` function should convert local optimizer request rows into API request rows using local `:instrument-id` as `client_instrument_id` and `:optimizer-history/instrument-id` as `instrument_id`.

In `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs`, provide pure functions shaped like:

    normalize-discovery
      [api-body]
      => optimizer discovery map

    with-discovery-metadata
      [local-market discovery]
      => local market with optional :optimizer-history/* keys

    normalize-history-bundle
      [request api-body]
      => normalized API v2 history map keyed by local optimizer instrument ID

    align-api-v2-history-inputs
      [{:keys [universe api-v2-history as-of-ms stale-after-ms funding-periods-per-year min-observations]}]
      => optimizer history map with the same shape as legacy align-history-inputs

In `/hyperopen/src/hyperopen/portfolio/optimizer/infrastructure/history_client.cljs`, preserve public:

    request-history-bundle!
      [deps request]
      => js/Promise resolving optimizer history bundle

The returned bundle may include API v2 metadata, but the legacy shape must remain compatible with existing callers.

## Revision Notes

2026-05-14 / Codex: Initial deferred plan authored from maintainer request, API contract review, live endpoint smoke findings, local optimizer boundary inspection, and read-only subagent findings. The plan is deferred because implementation is not starting in this session.
2026-05-15 / Codex: Implemented API v2 integration in the active worktree. Focused CLJS tests, adjacent optimizer tests, route-mocked Playwright coverage, `npm test`, and `npm run test:websocket` pass. `npm run check` is blocked by unrelated stale canonical docs in `lint:docs`.
2026-05-15 / Codex: Follow-up from live CLJS nREPL inspection: defaulted API v2 on and enriched already-selected/queued rows from discovery when building history requests. Live probe confirmed a batched `POST /v1/optimizer/history-bundle` for `perp:BTC -> hl:perp:BTC` and no legacy history fetcher calls.
