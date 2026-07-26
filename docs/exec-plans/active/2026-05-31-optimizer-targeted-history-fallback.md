# Make Optimizer History Fallback Targeted And Rate Limited

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The optimizer should keep the history backend API as the primary data source, while preserving a legacy `/info` fallback that does not stampede Hyperliquid or duplicate work the backend already completed. When the backend returns usable history for an asset, the frontend must not request the same asset through legacy `/info`. Legacy fallback is reserved for assets without backend identities, assets omitted by the backend, or assets whose backend series is missing. Backend instrument-kind, proxy approval, proxy validation, quality, and validation failures are hard API-v2 safety decisions; they must remain visible and blocking instead of being replaced by legacy candle or vault data.

The user-visible proof is an optimizer load that stays fast for backend-covered assets and does not hit `/info` 429s when fallback is necessary.

A follow-up user-visible proof is that the `fetch returns matrix` progress row explains the split between the backend batch request and legacy `/info` fallback, so a `232 requests` count cannot appear without context.

## Context References

- Direct user request on 2026-05-31: fallback can remain, but it should be rate-limited and should only request assets the backend does not have, cannot return, or returns with corrupted data.
- `src/hyperopen/portfolio/optimizer/infrastructure/history_client.cljs` owns the API-v2 history client facade and legacy fallback.
- `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/alignment.cljs` owns API-v2 history alignment for optimizer inputs.

## Progress

- [x] (2026-05-31) Reproduced the old behavior with failing tests: API partial success did not request targeted legacy gaps, legacy requests were concurrent, and legacy fallback rows were ignored when API-v2 history existed.
- [x] (2026-05-31) Implemented targeted fallback selection for missing backend identity and unusable API series.
- [x] (2026-05-31) Serialized legacy history requests and added configurable fallback request spacing.
- [x] (2026-05-31) Blended legacy fallback series into API-v2 alignment so fallback data can make an otherwise API-backed universe eligible.
- [x] (2026-05-31) Ran focused related tests for history client, API-v2 client, API-v2 alignment, and config.
- [x] (2026-05-31) Ran `npm test` and `npm run test:websocket`; both passed.
- [x] (2026-05-31) Ran `npm run check`; it remains blocked by pre-existing docs and namespace-size guardrails unrelated to this change.
- [x] (2026-05-31) Patched the current-portfolio history request so it is backend-only and cannot fan out into legacy `/info` requests for every current holding.
- [x] (2026-05-31) Added source-aware progress events for backend API and `/info` requests, and changed the pipeline progress detail to show `backend API: X/Y assets, /info: A/B requests`.
- [x] (2026-06-05T02:08Z) Add RED coverage showing backend hard warnings do not trigger targeted legacy fallback and are not suppressed when legacy data is present.
- [x] (2026-06-05T02:11Z) Implement recoverable-only fallback classification for API-v2 successful responses while preserving fallback for no-backend-ID instruments.
- [x] (2026-06-05T02:15Z) Re-run focused history client/alignment/readiness tests and broad repo gates, then record evidence here.
- [ ] Resolve or explicitly accept unrelated repository guardrail blockers before moving this plan to completed.

## Surprises & Discoveries

- Observation: The history facade previously caught any API-v2 request failure and retried the whole request with the legacy loader when `:fallback-to-legacy?` was true.
  Evidence: `history_client.cljs` called `request-legacy-history-bundle!` from the API catch branch.

- Observation: A successful API-v2 partial response was not using legacy fallback for missing or rejected series at all.
  Evidence: The existing `request-history-bundle-does-not-fallback-on-api-v2-partial-success-test` asserted that missing API series did not fallback. That expectation was updated to preserve no-fallback behavior only for usable partial API results.

- Observation: Fetching legacy fallback data is not enough by itself because `history-loader/align-history-inputs` chooses API-v2 alignment whenever `:api-v2-history` is present.
  Evidence: New RED coverage showed DOGE legacy candles were ignored until API-v2 alignment learned to consume fallback series.

- Observation: The `232 requests` progress count came from the current-portfolio history overlay, not the selected optimizer universe.
  Evidence: `runtime/effect_adapters/portfolio_optimizer/history.cljs` issued a second history request for `:current-portfolio-universe` whenever the selected and current instrument sets differed. Large portfolios can turn that into `N` candle requests plus `N` funding requests.

- Observation: The optimizer progress row collapsed every history event into one generic request counter.
  Evidence: `portfolio_optimizer_pipeline.cljs` rendered `completed/total requests` from each progress payload and ignored whether the work came from the backend API or legacy `/info`.

- Observation: The previous fallback classifier included `:rejected`, `:validation-failed`, `:proxy-validation-failed`, and `:instrument-kind-mismatch` in the same bucket as missing history gaps.
  Evidence: `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/legacy_fallback.cljs` included those warning codes in `fallback-warning-codes`, while `docs/exec-plans/active/2026-05-15-optimizer-api-v2-return-first-readiness.md` says hard backend validation warnings must block and must not be suppressed.

- Observation: `:identity-ambiguous` is also a readiness blocking warning, but it is intentionally recoverable by legacy fallback when the selected row has no backend identity.
  Evidence: The existing `align-api-v2-history-uses-legacy-fallback-series-for-api-gaps-test` failed until the hard-fallback classifier separated no-backend-ID fallback from backend validation rejection fallback.

## Decision Log

- Decision: Keep full legacy fallback when the API request itself fails, but serialize it.
  Rationale: If the backend is unavailable, fallback may still be useful, but the old all-at-once request fanout caused 429s.
  Date/Author: 2026-05-31 / Codex

- Decision: On API-v2 success, fallback only the gap instruments.
  Rationale: Usable API-backed assets should not be requested through `/info`; this directly reduces request count and avoids duplicated data sources.
  Date/Author: 2026-05-31 / Codex

- Decision: Do not fallback merely for stale or short-but-present backend history.
  Rationale: The user specifically scoped fallback to backend missing, unable, or corrupted data. Staleness and insufficient coverage are readiness/model-quality issues, not a reason to fan out to `/info` for every selected asset.
  Date/Author: 2026-05-31 / Codex

- Decision: Disable legacy fallback for current-portfolio history overlay requests.
  Rationale: The current overlay is useful context but should not make the optimizer issue hundreds of `/info` requests. Selected optimizer inputs may use targeted fallback; current portfolio overlay history is backend-only and degrades to an unavailable-current-history warning if the backend cannot serve it.
  Date/Author: 2026-05-31 / Codex

- Decision: Keep the progress panel as a compact single-row summary, but make the detail source-aware.
  Rationale: The existing UI can expose the important debugging signal without a larger layout change: backend assets are counted separately from `/info` fallback requests.
  Date/Author: 2026-05-31 / Codex

- Decision: Treat instrument-kind, proxy approval, proxy validation, quality failure, rejected lineage, and validation-failed warnings as authoritative hard rejections for targeted fallback, while continuing to allow legacy fallback for selected rows with no backend identity.
  Rationale: Legacy `/info` fallback can safely fill absent data, but it cannot prove that a backend rejected series is the correct optimizer identity or an approved proxy. Allowing fallback for hard warnings can make readiness appear runnable while hiding the backend safety decision. A missing backend identity is different because there is no backend series to reject, and the existing targeted fallback behavior explicitly supports those rows.
  Date/Author: 2026-06-05 / Codex

## Outcomes & Retrospective

Earlier 2026-05-31 implementation evidence is retained for context:

    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.infrastructure.history-client-test --test=hyperopen.portfolio.optimizer.infrastructure.history-client-fallback-test --test=hyperopen.portfolio.optimizer.infrastructure.history-api-v2-client-test --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-test --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-legacy-fallback-test --test=hyperopen.config-test

    Ran 22 tests containing 111 assertions.
    0 failures, 0 errors.

Focused progress-debugging tests also pass:

    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.infrastructure.history-client-fallback-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-pipeline-progress-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-pipeline-test

    Ran 10 tests containing 41 assertions.
    0 failures, 0 errors.

Smallest relevant browser regression also passes against the existing dev server on port 8081:

    PLAYWRIGHT_BASE_URL=http://127.0.0.1:8081 PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test tools/playwright/test/optimizer-history-api-v2.spec.mjs -g "loads rows without legacy history fanout"

    1 passed

Full test gates from the earlier implementation also passed:

    npm test
    Generated test/test_runner_generated.cljs with 662 namespaces.
    Ran 4098 tests containing 22602 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 527 tests containing 3067 assertions.
    0 failures, 0 errors.

The earlier implementation initially recorded unrelated `npm run check` blockers:

    [stale-doc] docs/references/hyperliquid-portfolio-history-and-returns.md - document is stale: 94 days old, max allowed 90
    [active-exec-plan-no-unchecked-progress] docs/exec-plans/active/2026-05-24-rebalance-slippage-snapshot-estimates.md - active ExecPlan has no remaining unchecked progress items; move it out of active

The earlier implementation also recorded unrelated namespace-size exceptions:

    [size-exception-exceeded] src/hyperopen/portfolio/optimizer/actions/draft.cljs - namespace has 519 lines; exception allows at most 511
    [size-exception-exceeded] test/hyperopen/portfolio/optimizer/draft_actions_test.cljs - namespace has 526 lines; exception allows at most 518

Hardening follow-up tests initially failed before implementation:

    npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.infrastructure.history-client-fallback-test --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-legacy-fallback-test

    request-history-bundle-does-not-targeted-fallback-hard-api-v2-rejections-test expected no legacy calls, but observed [[:candle "BAD"] [:funding "BAD"]].
    align-api-v2-history-does-not-suppress-hard-api-rejections-with-legacy-data-test expected BAD to be excluded with :validation-failed, but BAD was eligible with :optimizer-history-api-legacy-fallback.

After implementation, focused and broad validation passed:

    npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.infrastructure.history-client-test --test=hyperopen.portfolio.optimizer.infrastructure.history-client-fallback-test --test=hyperopen.portfolio.optimizer.infrastructure.history-api-v2-client-test --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-test --test=hyperopen.portfolio.optimizer.application.history-loader-api-v2-legacy-fallback-test --test=hyperopen.portfolio.optimizer.application.setup-readiness-test --test=hyperopen.portfolio.optimizer.application.request-builder-test
    Ran 49 tests containing 214 assertions.
    0 failures, 0 errors.

    npm test
    Ran 4220 tests containing 23418 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 531 tests containing 3080 assertions.
    0 failures, 0 errors.

    npm run check
    Completed successfully, including docs, namespace-size, namespace-boundary, release-assets, styles, dev-server-cleanup, Shadow app, portfolio, portfolio-worker, portfolio-optimizer-worker, vault-detail-worker, and test builds.

The current hardening pass supersedes the earlier `npm run check` blocker note above for this active plan. As of 2026-06-05, the broad `npm run check` gate completed successfully on the current tree.

## Validation and Acceptance

Acceptance is met when:

- API-v2 requests include only instruments with backend IDs.
- API-v2 successful partial responses do not fallback usable assets.
- Missing backend IDs and unusable API series trigger targeted legacy fallback.
- API-v2 hard warning codes `:instrument-kind-mismatch`, `:proxy-mapping-unapproved`, `:proxy-validation-failed`, and `:validation-failed`, plus `:rejected` lineage and failed or rejected quality, do not trigger targeted legacy fallback after a successful API response.
- API-v2 hard warning codes stay visible in aligned history warnings and setup readiness even when legacy candle, funding, or vault data is present for the same selected instrument.
- Legacy fallback requests run one at a time, with runtime-configured spacing in production config.
- Legacy fallback data is visible to optimizer alignment even when `:api-v2-history` is present.
- The `fetch returns matrix` progress detail distinguishes backend API assets from legacy `/info` fallback requests.
- `npm test`, `npm run test:websocket`, and `npm run check` outcomes are recorded.

## Artifacts and Notes

New coverage:

- `test/hyperopen/portfolio/optimizer/infrastructure/history_client_fallback_test.cljs`
- `test/hyperopen/portfolio/optimizer/application/history_loader_api_v2_legacy_fallback_test.cljs`

## Revision Notes

- 2026-06-05: Added a hardening milestone after a maintainer request to remediate the likely bug hotspot around API-v2 history fallback, warning suppression, and readiness. This revision resolves a conflict between the original targeted fallback wording, which mentioned validation-failed fallback, and the API-v2 readiness contract, which says hard backend validation warnings must block.
- 2026-06-05: Completed the hardening milestone and recorded validation. The classifier now keeps no-backend-ID fallback recoverable but treats backend safety rejections as authoritative.
