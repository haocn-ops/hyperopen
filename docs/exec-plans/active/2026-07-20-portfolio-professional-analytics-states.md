# Deliver Truthful Portfolio Analytics States

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be maintained as work proceeds. It follows `docs/PLANS.md` and `.agents/PLANS.md`.

## Purpose / Big Picture

After this work, a connected wallet holder and a person viewing `/portfolio/trader/<address>` will see the same clear, professional analytics surface for the address in context. Equity, PnL, drawdown, volume, and fee information will be shown only when the existing Hyperopen account endpoints can support them. Every visible result will state whether it is loading, empty, live, stale, partial, unavailable because of a provider error, or explicitly supplied demo data. The page must never turn missing data into a real-looking zero, use a local wallet secret, or make up historical fees.

The observable proof is a deterministic state-matrix test and a Playwright scenario that load `/portfolio` and an observed trader route with fixtures. They must show the correct status, preserve the address boundary, and make a provider failure distinguishable from an empty portfolio. Browser design QA then verifies both routes at 375, 768, 1280, and 1440 pixel widths.

## Context References

Durable user context:

- Direct user request on 2026-07-20: continue the Hyperopen productization plan, next completing professional `/portfolio` analytics states for connected and observed addresses without expanding into optimizer, wallet, order, or attribution work.

Repository artifacts:

- `docs/exec-plans/completed/2026-07-20-non-custodial-analytics-white-label-affiliate.md` established public tenant configuration, non-custodial boundaries, basic portfolio analytics service contracts, and an explicit no-fabrication rule.
- `src/hyperopen/service/portfolio_analytics.cljs` currently exposes the pure `build-portfolio-view-model` state and metric projection, but `rg` shows no production caller yet.
- `src/hyperopen/api/projections/portfolio.cljs`, `src/hyperopen/api/projections/user_fees.cljs`, and `src/hyperopen/account/surface_service.cljs` own the existing portfolio and user-fee request state. They already fetch for the effective account address.
- `src/hyperopen/views/portfolio/vm.cljs` owns `/portfolio` composition; `src/hyperopen/views/portfolio_view.cljs`, `src/hyperopen/views/portfolio/summary_cards.cljs`, `src/hyperopen/views/portfolio/chart_view.cljs`, and `src/hyperopen/views/portfolio/performance_metrics_view.cljs` render the existing shell.
- `docs/BROWSER_TESTING.md`, `docs/FRONTEND.md`, `docs/agent-guides/browser-qa.md`, `docs/agent-guides/ui-foundations.md`, and `docs/agent-guides/trading-ui-policy.md` govern browser verification and truthful time/data presentation.

Public issue or pull request: none supplied. The direct user request above is the durable work reference.

Local scratch references: `tmp/multi-agent/portfolio-analytics-complete-states/` holds workflow artifacts only and is not authoritative.

## Scope

This slice connects the existing pure portfolio analytics contract to the existing `/portfolio` view-model and renders its quality state without replacing the portfolio metrics engine or the current optimizer workspace.

- Derive one canonical analytics input for the effective account address. `account-context/effective-account-address` must resolve either the connected wallet address or the address encoded by `/portfolio/trader/<address>`; neither path may fall back to the other address's cached response.
- Use only already-owned state: selected `[:portfolio :summary-by-key]` history, `[:portfolio :loading?]`, `[:portfolio :error]`, `[:portfolio :loaded-at-ms]`, current-address-matched `:user-fees` data and its loading/error/timestamp fields, and existing safe fill/volume projections where they already prove a value. Do not add an endpoint or raw browser-storage cache.
- Put the translation from those app-state paths to the service input in a small portfolio application boundary. Keep `hyperopen.service.portfolio-analytics` pure and keep `hyperopen.portfolio.metrics/**`, worker computation, benchmark selection, optimizer, and WebSocket decisions unchanged.
- Present a closed quality vocabulary: `:loading`, `:empty`, `:live`, `:stale`, `:partial`, `:provider-error`, `:demo`, and `:unavailable`. `:unavailable` means no effective address. `:provider-error` means no usable retained result and an existing provider request failed. A retained result after a failed or expired refresh remains visible but is labeled `:stale`, including its as-of time and an error/retry explanation.
- Make the metric rules explicit. Equity is the latest valid account-value point. PnL and return use native PnL history where present so deposits and withdrawals do not become profit. Drawdown is shown only for a complete, live PnL/equity path. Volume uses the selected portfolio summary volume or the existing current-address daily user-volume projection. Fees mean provider-supplied current maker/taker rates; aggregate historical fees are shown only when actual fee-bearing fills exist. Missing fee evidence renders unavailable, never `$0.00` or `0.000%`.
- Keep existing chart, account tabs, benchmark metrics, fee schedule popover, and product-context banner. Add a compact status and freshness presentation close to the analytics summary rather than a modal, route redirect, or page-wide blocking loader.
- Add deterministic ClojureScript coverage for the state matrix and a committed Playwright regression for the stable fixture route. Browser MCP design QA is required because this changes a `/portfolio` view and its reachable loading/error states.

## Non-Goals

- Do not modify `src/hyperopen/portfolio/optimizer/**`, optimizer workers, benchmark math, return/drawdown algorithms already owned by `hyperopen.portfolio.metrics/**`, or existing optimizer UI.
- Do not modify attribution, affiliate delivery, wallet connection/signing, order submission, venue API credentials, or WebSocket runtime decisions.
- Do not create a new portfolio API, historical database, trusted relay, browser persistence key, polling loop, analytics worker, or performance cache.
- Do not manufacture a dollar value for historical paid fees from volume multiplied by today's fee rate. Do not turn absent history, unavailable fees, or failed provider requests into zero-valued cards, points, or success copy.
- Do not change the semantics of the existing general performance-metrics/benchmark table beyond allowing it to coexist with the new portfolio-quality status.
- This is not performance-motivated work. It intentionally reuses current VM caches and metric/worker boundaries. No new algorithm, cache, or rendering pipeline is justified; browser jank review is a regression check, not a throughput claim.

## Progress

- [x] (2026-07-20) Read the completed productization plan, pure portfolio analytics service, portfolio metric/application/view-model/view/test boundaries, existing request projections, and the governed UI/browser documents.
- [x] (2026-07-20) Froze the minimal contract: existing address-scoped portfolio and user-fee data only; no optimizer, wallet, order, attribution, new endpoint, or fabricated fee aggregate.
- [x] (2026-07-20) Created and reconciled the approved RED contract for the pure app-state-to-analytics bridge, quality precedence, current-address isolation, and no-fabricated-value rules.
- [x] (2026-07-20) Implemented the pure bridge and closed service quality vocabulary without changing the three-argument service API or adding effects.
- [x] (2026-07-20) Integrated one additive `:analytics` model into the existing portfolio VM and compact summary/metric cards, preserving benchmark, chart, worker, and optimizer composition.
- [x] (2026-07-20) Closed reviewer blockers: trader summaries require an explicit matching address; portfolio and fee lifecycles stay independent; fee-rate freshness is field-local; incomplete fill-fee evidence remains unavailable; provider error text is sanitized; composition values remain visible when supported; and range labels describe the selected analytics range.
- [x] (2026-07-20) Recompiled `:app` and `:test`, passed namespace-boundary and changed-delimiter checks, and passed the focused connected/observed Playwright regression on the current `8080` app (`1 passed`, 5.4s).
- [x] (2026-07-20) Completed the earlier repository matrix: `npm run gates` passed 34/34, including `5797` ClojureScript tests with `32181` assertions and WebSocket `560` tests with `3174` assertions.
- [x] (2026-07-20) Resolved the second review: spectate mode now follows the observed-address boundary; unowned observed errors fail closed unless an explicit current-summary address proves ownership; expired/failed fee evidence makes the model partial; sensitive authorization-header text falls back; and invalid daily-volume rows stay unavailable. Recompiled app/test and passed the focused analytics regression (`1 passed`, 6.1s).
- [x] (2026-07-20) Reconciled the final review: portfolio request lifecycle state now records the normalized requested address for loading, success, and initial error; observed initial errors only surface with matching ownership; and safe provider messages reject `X-Session` text. The focused analytics Playwright regression passed (`1 passed`, 5.2s), and the post-correction gate matrix passed 34/34 (`5803` ClojureScript tests / `32271` assertions; WebSocket `561` tests / `3184` assertions) with `NODE_OPTIONS=--no-experimental-webstorage` in this local Node runtime.
- [x] (2026-07-20) Resolved the final P1 endpoint-shape compatibility review: observed summaries now accept either an explicit matching row address or, only for an unaddressed row, a normalized matching `[:portfolio :loaded-for-address]`. The deterministic endpoint -> projection -> trader bridge regression proves live metrics, while nil or old provenance remains rejected for spectate. Recompiled `:app` and `:test`, passed the Node suite (`5804` tests / `32290` assertions), focused analytics Playwright (`1 passed`, 5.2s), and the final 34/34 gate matrix (WebSocket `561` tests / `3184` assertions).
- [x] (2026-07-20) Resolved the final fee-only P1: fresh matching maker/taker rates are field-local evidence, not retained portfolio evidence. Fee-only portfolio errors are `:provider-error` and settled fee-only states are `:empty`; both retain supported current rates while equity, PnL, volume, and timeseries remain unavailable. Recompiled `:app` and `:test`, passed the Node suite (`5805` tests / `32304` assertions), and passed the final 34/34 gate matrix (WebSocket `561` tests / `3184` assertions).
- [ ] Leave this ExecPlan active for maintainer handoff and acceptance.

## Surprises & Discoveries

- Observation: the pure service exists but has no production caller.
  Evidence: `rg -n 'build-portfolio-view-model|portfolio-analytics' src/hyperopen` returns only `src/hyperopen/service/portfolio_analytics.cljs`.
- Observation: the current portfolio VM supplies numeric fallbacks such as `0` while selected history or user-fee data is absent.
  Evidence: `src/hyperopen/views/portfolio/vm.cljs` uses `or` fallbacks for PnL, volume, equity-derived fields, and default fee rates; its existing background banner does not make every metric's evidence explicit.
- Observation: the application already requests both portfolio history and user fees for the effective account surface.
  Evidence: `src/hyperopen/account/surface_service.cljs` calls `fetch-portfolio!` and `fetch-user-fees!` from the same visible-account bootstrap path, while the request projections retain loading/error/loaded-at fields under `:portfolio`.
- Observation: user-fee data contains current fee rates and daily volume, not a complete historical fee ledger.
  Evidence: `src/hyperopen/views/portfolio/vm/volume.cljs` reads `:userCrossRate`, `:userAddRate`, and `:dailyUserVlm`.
- Observation: an explicit empty `:userFills` vector confirms zero legacy fill totals but is not retained analytics evidence for quality classification.
  Evidence: the service now derives `:empty` from an otherwise settled, empty response while retaining legacy `:fees` compatibility only where fills are complete.
- Observation: the local Node release exposes experimental web storage without browser-compatible `getItem`.
  Evidence: bare `node out/test.js` fails during existing application bootstrap; `node --no-experimental-webstorage out/test.js` is the compatible local invocation.
- Observation: application analytics must not depend on portfolio view-model helpers.
  Evidence: `lint:namespace-boundaries` rejected the initial bridge imports from `hyperopen.views.portfolio.vm.summary` and `.volume`; the bridge now uses the canonical normalized action selection and local pure daily-volume parsing, and the boundary gate passes.
- Observation: the committed analytics fixture must seed the same selected range as its summary key.
  Evidence: the fixture stored only `:month` while `/portfolio` defaults to `:one-year`; the bridge correctly treated the absent selected summary as loading/stale. Seeding `[:portfolio-ui :summary-time-range] :month` restored the intended deterministic fixture without weakening stale semantics.
- Observation: an observed provider error can be attributed by its own address field or by the already selected, explicitly addressed summary, but never by a route-local or wallet-local assumption.
  Evidence: `matching-portfolio-error` now accepts `:error-for-address` only when it matches the effective address, and otherwise accepts the error only when the current selected summary itself has that same address. Missing or unaddressed observed summaries continue to fail closed.
- Observation: the normal portfolio endpoint leaves month summary rows unaddressed but the successful request projection owns the normalized requested address.
  Evidence: `normalize-portfolio-summary` preserves `{:data {"month" {...}}}` rows, while `apply-portfolio-success` writes `:loaded-for-address`; the bridge accepts that outer provenance only when it matches the effective observed account.

## Decision Log

- Decision: Treat the effective account address as the only identity for the analytics bridge.
  Rationale: connected and observed trader routes need equivalent read-only analytics, while cached data for a previously connected account must never appear for an observed address. `account-context/effective-account-address` already represents that route-aware identity.
  Date/Author: 2026-07-20 / spec_writer.
- Decision: Preserve all existing data-fetch ownership and add only a pure translation boundary.
  Rationale: portfolio and user-fee lifecycle projections already contain request loading, error, address, and freshness information. A second fetch path would introduce conflicting state ownership and possibly duplicate requests.
  Date/Author: 2026-07-20 / spec_writer.
- Decision: Display current maker/taker fee rates separately from historical paid fees.
  Rationale: current rates and volume are supported by `userFees`; applying today's rates to prior volume would be false accounting. A missing historic fee value must remain unavailable.
  Date/Author: 2026-07-20 / spec_writer.
- Decision: A stale last-known-good result outranks a provider-error empty state.
  Rationale: a real dated result remains more useful and more truthful than replacing it with a blank error panel. The UI must show both that it is stale and why refresh failed.
  Date/Author: 2026-07-20 / spec_writer.
- Decision: Use a compact in-flow status/freshness presentation, not a blocking modal or a second dashboard.
  Rationale: analytics refresh is non-destructive and should leave account tabs, charts, and read-only inspection available. This follows the UI rule to prefer inline disclosure for recoverable page-local state.
  Date/Author: 2026-07-20 / spec_writer.
- Decision: Assign the stable volume and current-fee-rate anchors to the existing metric cards, and account-path metric anchors to the existing summary card.
  Rationale: this keeps one visible anchor per metric, preserves the established layout, and prevents unavailable evidence from falling back to legacy zero formatting.
  Date/Author: 2026-07-20 / worker.
- Decision: Treat `:loaded-for-address` as provenance for an unaddressed successful portfolio row only in an observed context.
  Rationale: the normal endpoint shape does not decorate month rows with an account, but the projection records the request identity. Requiring a normalized match preserves the cross-account cache boundary; an explicit row address remains independently sufficient.
  Date/Author: 2026-07-20 / worker.
- Decision: Keep fresh current fee rates visible independently from portfolio snapshot availability.
  Rationale: rates are separately address-matched provider evidence, not historical or retained portfolio evidence. They cannot turn an empty/error portfolio state into stale, but hiding them when supplied would contradict field-level availability.
  Date/Author: 2026-07-20 / worker.

## Outcomes & Retrospective

Implementation now uses one pure, effective-address-scoped analytics model from the existing portfolio/user-fee projections. The model exposes only evidence-backed values, closed quality states, safe freshness/error text, and field availability; the existing shell keeps its chart, tabs, benchmark, worker, and optimizer behavior. Reviewer corrections verified that trader routes cannot reuse an unaddressed or wrong-address summary, current rates never imply historical paid fees, failed fee refreshes do not downgrade portfolio quality, unsafe provider payloads never reach UI text, and the browser fixture supplies the actively selected summary range. The final correction records lifecycle ownership for the requested portfolio address, so an initial observed-route failure is surfaced only for that address; it also rejects `X-Session` as unsafe provider text. The final P1 correction treats a normal unaddressed endpoint row as observed-account evidence only when the successful projection's normalized `:loaded-for-address` matches the effective account; explicit row ownership still works, and missing or old projection ownership remains unavailable. Fresh current fee rates remain independently visible when their own evidence is valid and fresh, but do not create a retained portfolio result: fee-only errors are `:provider-error` and fee-only settled states are `:empty`. Validation after those corrections: `npm run setup:worktree`; application and test compilation; `env NODE_OPTIONS=--no-experimental-webstorage node out/test.js` (`5805` tests / `32304` assertions); the previously stable focused analytics Playwright regression (`1 passed`, 5.2s); design-review artifact `tmp/browser-inspection/design-review-2026-07-20T13-37-09-917Z-f1af909b/summary.md` with PASS for visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf on both portfolio routes at 375, 768, 1280, and 1440; cleanup confirmation from `npm run browser:cleanup`; and the final `npm run gates` matrix (34/34 PASS, `5805` tests / `32304` assertions, WebSocket `561` tests / `3184` assertions). In this local Node runtime, export `NODE_OPTIONS=--no-experimental-webstorage` for the matrix; without it, Node's incompatible experimental `localStorage` fails before project tests execute.

## Context and Orientation

Hyperopen is a ClojureScript browser application. A pure function accepts data and returns data without reading the browser, network, clock, or storage. A projection is the state shape written by an API request lifecycle. The `/portfolio` page currently builds its display model in `src/hyperopen/views/portfolio/vm.cljs` from `:portfolio` and other account projections.

The portfolio endpoint stores a map of summaries in `[:portfolio :summary-by-key]`. A selected summary can contain `:accountValueHistory`, `:pnlHistory`, and `:vlm`. The user-fee endpoint stores the response in `[:portfolio :user-fees]`, retains the address it was fetched for in `[:portfolio :user-fees-loaded-for-address]`, and exposes `:dailyUserVlm`, `:userCrossRate`, and `:userAddRate` when the provider returns them. The exact request state lives beside these values: `:loading?`, `:error`, and `:loaded-at-ms` for portfolio, then analogous `:user-fees-*` fields for fee data.

`src/hyperopen/service/portfolio_analytics.cljs` already normalizes account/PnL points and returns a compact VM with `:equity`, `:pnl`, `:return-pct`, `:max-drawdown-pct`, `:volume`, `:fees`, `:timeseries`, and `:data-quality`. It must remain the one place that decides numeric analytics evidence. A new small namespace at `src/hyperopen/portfolio/application/analytics_state.cljs` should translate the current store and selected range/scope into the service input. It must use `hyperopen.account.context/effective-account-address` and `hyperopen.service.tenant-config/active-tenant-config`, verify the user-fee payload's address matches the effective address, and pass explicit lifecycle/freshness metadata. The view-model will call this bridge once and pass its result to existing summary/chart/status renderers.

The time policy is intentionally simple. The bridge provides an explicit `now-ms` and carries each successful response's `loaded-at-ms`. It derives live/stale against the existing request policy expectation for portfolio/user-fee refreshes, documented in code beside the bridge and tested with fixed timestamps. It must not begin a timer or a polling loop. A render after a later ordinary state update may advance stale labeling; a requested refresh remains owned by existing account-surface effects.

## Plan of Work

First, add `src/hyperopen/portfolio/application/analytics_state.cljs` as a pure adapter from current application state to `portfolio-analytics/build-portfolio-view-model`. It will select the same summary range and scope already selected by `hyperopen.views.portfolio.vm.summary`, select the active tenant configuration, require an effective address, and package only observed history, request state, user-fee data, and explicit freshness timestamps. It will clear a user-fee payload when its fetched-for address is not the effective address. It will not issue requests, call `js/Date`, write the store, or retain raw fills in output.

Then adjust `src/hyperopen/service/portfolio_analytics.cljs` only as required to make the quality vocabulary closed and to accept the existing endpoint's proven volume/fee-rate evidence. Preserve the public three-argument `build-portfolio-view-model` signature. The function must retain current PnL/cash-flow correctness and drawdown guards. It must distinguish fresh partial from stale data, preserve provider-error only when no real retained result exists, and emit machine-readable `:as-of-ms`, `:message`, and field-level availability metadata needed by rendering. It must never return a numeric placeholder for absent data. `:demo` must require an explicit input source marker and cannot be inferred from an empty or disconnected state.

Next, update `src/hyperopen/views/portfolio/vm.cljs` to call the application bridge, to replace only the affected fallback values in its summary/metric model, and to retain current selectors, benchmark VM, metrics worker, volume-history model, and account-tab model. Update the smallest existing portfolio renderers, expected to be `src/hyperopen/views/portfolio/summary_cards.cljs`, `src/hyperopen/views/portfolio/chart_view.cljs`, and/or `src/hyperopen/views/portfolio/header.cljs`, with stable `data-role` anchors for the quality banner and each unavailable metric. The connected route and trader route must use the same components and differ only by the effective address/read-only context. The presentation must provide a textual status, timestamp when stale, and a textual explanation/retry path for errors; color cannot be the sole indication.

Do not rewrite `src/hyperopen/views/portfolio_view.cljs` or alter the optimizer conditional. It should continue to compose the current header, product banner, summary grid, account table, and existing popovers. Reuse it only for placing the state component if the selected renderer cannot own it cleanly.

The test writers will first add RED cases to the existing service, VM, view, and Playwright surfaces. The worker then implements the smallest change needed to turn those tests green. A reviewer checks no raw address, fill record, secret, or cross-account cached response leaks into visible/exported analytics state. The browser debugger runs the full UI contract after Playwright has established the stable path.

## Touched Areas

- `docs/exec-plans/active/2026-07-20-portfolio-professional-analytics-states.md`: living execution plan and final evidence.
- `src/hyperopen/portfolio/application/analytics_state.cljs`: new pure current-state-to-service-input bridge; no browser or network effects.
- `src/hyperopen/service/portfolio_analytics.cljs`: quality precedence and honest input/output contract, retaining the existing public function signature.
- `src/hyperopen/views/portfolio/vm.cljs`: one analytics model consumer and removal of only analytics-specific fabricated fallback display values.
- `src/hyperopen/views/portfolio/summary_cards.cljs`, `src/hyperopen/views/portfolio/chart_view.cljs`, and, only if needed, `src/hyperopen/views/portfolio/header.cljs`: status/freshness and unavailable-value rendering with stable anchors.
- `test/hyperopen/service/portfolio_analytics_acceptance_test.cljs`: data-quality and numeric-truth contract.
- `test/hyperopen/portfolio/application/analytics_state_test.cljs`: address selection, lifecycle/freshness mapping, and no-cross-account projection.
- `test/hyperopen/views/portfolio/vm_test.cljs`, `test/hyperopen/views/portfolio/summary_cards_test.cljs`, `test/hyperopen/views/portfolio_view_status_test.cljs`, and a focused chart test if its state rendering changes: deterministic rendered-state assertions.
- `tools/playwright/test/portfolio-regressions.spec.mjs`: fixture-backed `/portfolio` and observed `/portfolio/trader/<address>` state regression.

## Concrete Steps

All commands run from `/Users/zh/Documents/Hyperopen`.

1. Prepare the worktree before compiling.

       npm run setup:worktree

   Expected result: shared dependencies are linked or the command explains that `npm ci` is required. Do not treat missing dependencies as an analytics failure.

2. Materialize the approved RED tests and compile the relevant application/test targets.

       npm run test:runner:generate
       npx shadow-cljs --force-spawn compile app
       npx shadow-cljs --force-spawn compile test

   Expected result before the worker change: the specifically approved new analytics test fails for the missing bridge/status behavior. Expected result after it: both compilation commands exit zero and the test failure disappears.

3. Run the deterministic ClojureScript suite and the focused browser regression before broadening validation.

       node out/test.js
       npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "analytics|portfolio"

   Expected result: zero ClojureScript failures/errors; Playwright observes the fixture-backed quality anchors and no wrong-account value on either route. If the focused grep finds no named test, run the single named spec without the grep and record the adjustment in this plan.

4. Run governed design review after the stable Playwright path passes.

       npm run qa:design-ui -- --targets portfolio-route --manage-local-app
       npm run browser:cleanup

   Expected result: an artifact under `tmp/browser-inspection/design-review-*/` accounting for visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf passes at 375, 768, 1280, and 1440 widths. The cleanup command exits with no live inspection session.

5. Run all repository gates once focused validation is green.

       npm run gates

   Expected result: a PASS/FAIL matrix covering `npm run check`, `npm test`, and `npm run test:websocket`. Record the final matrix and any unrelated existing advisory in `Progress` and `Outcomes & Retrospective`.

## Validation and Acceptance

The slice is accepted only when all outcomes below are observable through named tests, browser assertions, or the stated commands.

- Given a connected address with a current selected summary containing valid account/PnL history and address-matched complete volume/fee evidence, the `/portfolio` summary displays equity, signed PnL/return, drawdown, volume, and current maker/taker fees with a `data-role` status of `live`. The service test proves a deposit-only history returns zero PnL/return rather than false profit.
- Given `/portfolio/trader/<address-b>` while the state retains wallet/address-a and an address-a user-fee response, the analytics bridge selects address-b and does not display address-a volume, fee rates, equity, PnL, or history. The Playwright fixture changes both address values and asserts only the observed address's data roles/text.
- Given no effective address, visible metric values are unavailable rather than zero and the model/status is `unavailable`. Given a settled provider response with no history, the status is `empty`, with explanatory copy distinct from `provider-error`.
- Given the first portfolio/user-fee request has started but has no successful usable result, the route displays structured loading feedback without blocking navigation or account tabs. Given a provider error before any usable history, it displays `provider-error`, the provider's safe message, and a retry/refresh affordance owned by an existing action; no real-looking zero card appears.
- Given a previously successful response whose fixed test clock exceeds the documented freshness threshold, real values remain visible, the status is `stale`, an as-of timestamp is present, and a provider refresh error is explained. Given fresh valid history but missing volume or fee evidence, status is `partial` and only those unsupported fields are unavailable.
- Given an explicit `:source :demo` fixture, the status says `demo`; it does not claim provider freshness, does not emit a live/settled label, and demo source is never inferred from an absent address/history.
- Raw fills, raw wallet addresses outside route context, private keys, seed phrases, API secrets, access tokens, raw signatures, and the raw provider response are absent from the analytics VM and rendered text. The deterministic tests inspect keys and text for the exclusion.
- A committed Playwright test covers the stable connected/observed fixture flow. The browser QA report marks each of six passes PASS, FAIL, or BLOCKED at all four required widths. The work cannot be marked complete with an unaccounted-for pass, viewport, or browser-inspection session.
- `node out/test.js`, the focused Playwright command, `npm run qa:design-ui -- --targets portfolio-route --manage-local-app`, `npm run browser:cleanup`, and `npm run gates` report their final results in this plan. The expected complete matrix has no new analytics regression.

## Idempotence and Recovery

The analytics bridge is a pure projection and is safe to invoke repeatedly for the same state. It creates no request, timer, subscription, storage record, queue event, or mutation. Existing account-surface request effects remain responsible for retry and refresh. When an address changes, address-mismatch checks make the old user-fee payload unavailable immediately until the existing lifecycle writes a matching result.

If a fixture or provider payload is malformed, normalizers drop unusable numeric fields, return the matching non-success quality state, and keep the rest of a valid last-known-good projection only when it is explicitly stale. The worker must not add a global exception path or an error boundary that hides other current `/portfolio` content. Test cleanup restores VM caches and test stores with the repository's existing fixture helpers; browser cleanup is mandatory after Browser MCP/design-review work.

## Artifacts and Notes

Pre-implementation evidence:

    $ rg -n 'build-portfolio-view-model|portfolio-analytics' src/hyperopen
    src/hyperopen/service/portfolio_analytics.cljs:102:(defn build-portfolio-view-model ...)
    src/hyperopen/service/portfolio_analytics.cljs:148:(defn build-analytics-viewed-event ...)

This proves the service has no runtime caller before the planned bridge. Completion evidence must add the exact ClojureScript test counts, focused Playwright result, browser-QA artifact path and six-pass matrix, cleanup confirmation, and gate matrix. Do not claim that a local fixture is a live provider response or that displayed fee rates are historical fees paid.

## Interfaces and Dependencies

Keep these public/service boundaries stable:

    hyperopen.service.portfolio-analytics/build-portfolio-view-model
      [tenant history options] -> analytics-view-model

    hyperopen.portfolio.application.analytics-state/build-analytics-state
      [state now-ms] -> analytics-view-model

`build-analytics-state` is a new pure adapter. `state` is the current immutable app map; `now-ms` is injected so every test controls freshness without a browser clock. It selects the normalized active tenant, effective account address, selected portfolio summary, matching user-fee projection, and existing lifecycle metadata. It must return no raw source collections that a renderer could accidentally expose.

The analytics view model must include at least `:account`, `:range`, `:equity`, `:pnl`, `:return-pct`, `:max-drawdown-pct`, `:volume`, `:fee-rates` or a clearly unavailable fees field, `:timeseries`, `:data-quality`, `:as-of-ms`, and a safe status/message field. Numeric fields may be `nil` only when their field-level status explains why; they must never be replaced by a display zero. Existing service callers and tests that consume `:fees` retain compatibility unless an approved RED test documents a narrow additive replacement.

The only dependencies are existing Hyperopen namespaces, existing account/API projections, the current metrics normalizers, current formatters, Playwright, and Browser MCP inspection tooling. No new npm package, provider SDK, server process, browser secret, or persistence schema is allowed.

Plan revision note (2026-07-20): created after the completed non-custodial/white-label/affiliate plan. It narrows the next delivery slice to address-scoped, truthful portfolio analytics state and governed UI verification.
