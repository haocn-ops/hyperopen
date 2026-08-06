# Correct portfolio-margin, scale-summary, and staking-account parity

This ExecPlan is a completed living record. Its `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` sections were maintained under `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md` through accepted validation; it is retained in `completed/` for future contributors.

## Purpose / Big Picture

This is a correctness and truthfulness repair for three money-sensitive surfaces. A Scale ticket must show the notional of the actual floored ladder legs it will create, not the entered total size multiplied by one reference price, and it must visibly expose its already-supported Post Only choice. A portfolio-margin account must identify itself as PM and must not present spot USDC or classic leverage arithmetic as portfolio-margin buying power or required margin. Staking must read and validate the same master account that the signed mutation already uses, so selecting a subaccount cannot show a balance from one account and submit against another.

After the work, a HYPE Scale draft with total size `1414.70`, start `48.5`, end `40.5`, `100` orders, skew `1.5`, and `szDecimals` `2` shows order value `$62,547.00`, derived from the 100 floored legs, rather than the generic `$72,733.97` (`1414.70 * 51.414...` reference price). A raw `portfolioMargin` abstraction displays `PM`; the two classic-risk summary rows are explicitly unavailable unless an authoritative portfolio-margin buying-power value is already present in state. On `/staking`, an owned selected subaccount does not redirect the load, max controls, validations, or post-success refresh away from the wallet owner/master whose action is signed.

## Context References

Public refs:

- Direct user request captured on 2026-08-02: repair portfolio-margin, Scale, and staking parity defects with no invented portfolio-margin risk math and no execution-semantics drift.

Repo artifacts:

- `/hyperopen/AGENTS.md` requires the worker role for `/src/**` edits and requires `npm run check`, `npm test`, and `npm run test:websocket` for code changes.
- `/hyperopen/docs/MULTI_AGENT.md`, `/hyperopen/docs/WORK_TRACKING.md`, and `/hyperopen/docs/QUALITY_SCORE.md` require the active plan to own durable scope, TDD evidence, deterministic tests, UI QA, and final gate evidence.
- `/hyperopen/docs/exec-plans/completed/2026-03-11-scale-order-panel-hyperliquid-parity.md` is historical layout context only. It does not define the notional calculation or alter this plan's contract.
- `/hyperopen/docs/BROWSER_TESTING.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/browser-qa.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md` govern the changed `/trade` and `/staking` surfaces.

Local scratch refs (non-authoritative):

- None.

## Scope Freeze

This plan contains exactly four repairs.

First, change only the Scale order-value calculation in the order summary and expose its already-supported Post Only control. The value must sum each deterministic `:size * :price` pair returned by the existing `scale-order-legs` generation with the active market's `:szDecimals`. The size values are floored by that generator before the multiplication; display formatting rounds the final sum to two USDC decimals. Generic market, limit, stop, take, and TWAP summaries remain on the existing `size * reference-price` path. Set Scale's existing `:supports-post-only?` capability to true so the pre-existing `:post-only` draft field is visible and controllable instead of potentially persisting invisibly after a Stop Limit or Take Limit transition. The gateway mapping remains unchanged: false is GTC and true is ALO.

Second, replace the hard-coded third mode chip in the perp order ticket with the documented PM-versus-Classic raw-abstraction indicator. `:account :abstraction-raw` is deliberately retained when account normalization reduces several exchange modes to `:unified` or `:classic`; it is the required discriminator. The frozen display mapping is case- and whitespace-insensitive: only `portfolioMargin` renders `PM`; `unifiedAccount`, `default`, `disabled`, `dexAbstraction`, absent, and unrecognized values render `Classic`. Official account-mode documentation groups standard, DEX abstraction, and unified account under the frontend's `Classic` label. No normalization or fetch contract changes in `api/fetch_compat.cljs` are part of this work.

Third, make the two PM-specific risk labels truthful. When the raw abstraction is `portfolioMargin`, `Available to Trade` may use a positive, finite portfolio-margin buying-power field only if the field is already present in the existing normalized state and its provenance is explicitly verified in repository code/tests. The current source audit finds no such state selector or payload contract. Therefore the implementation baseline is `N/A` for PM `Available to Trade` and `N/A` for PM `Margin Required`; it must not substitute spot USDC, `withdrawable`, `accountValue - totalMarginUsed`, or `order-value / ui-leverage`. Do not add a new endpoint, a guessed field name, or a portfolio-margin model. Non-PM behavior, including standard/unified summaries and order sizing, remains unchanged. `Order Value`, fees, and existing submit validation continue to be informative; this scope does not promise a new PM liquidation or sizing model.

Fourth, make native staking account-scoped consistently from load through refresh. Add `account-context/native-staking-account-address`: in normal connected state it returns the owner/master (the normalized wallet address), even when an owned subaccount is selected; on spectate and trader-portfolio inspection routes it returns `effective-account-address` so the read-only inspected page continues to load the inspected account. `effective-account-address` can otherwise become an owned selected subaccount, a spectated address, or a trader-portfolio address. On each semantic-address change, `load-staking` must synchronously save `[:staking :account-address]`, clear all five address-scoped projections (including staking spot), their loading/errors/loaded-for state, then issue reads. Every address-scoped fetch, including an explicit direct invocation, must preflight that its requested address equals both the saved account and current semantic native-staking address before it sets loading or makes a network request. For a started current fetch, success or error applies only if that request is still the latest request for its resource as well as current for those two identities; a later same-address request invalidates an earlier one. Successful current/latest responses record `[:staking :loaded-for resource]`. The route's delegator summary, delegations, rewards, history, and spot balance requests, the amounts used by max/validation helpers, and the post-success `load-staking` refresh must all use this semantic helper. The address-scoped staking spot response must be projected only to `[:staking :spot-state]`, `[:staking :loading :spot-state]`, `[:staking :errors :spot-state]`, and `[:staking :loaded-at-ms :spot-state]`; it must never overwrite global `[:spot :clearinghouse-state]`, which remains the active trading account's snapshot, and its errors must participate in the staking route VM/view error aggregation. Existing mutation guards keep inspected routes read-only. Signing already supplies the owner/master to `submit-c-deposit!`, `submit-c-withdraw!`, and `submit-token-delegate!`; retain that behavior. After the existing read-only, disconnected-owner, validator, input, and balance guards, a normal owner mutation also requires that `[:staking :account-address]` equal the semantic native-staking address and that the needed `[:staking :loaded-for resource]` equal it: deposit uses `:spot-state`; withdraw and delegate use `:delegator-summary`; undelegate uses `:delegations`. Otherwise emit only the existing form/submitting reset effects with `"Staking account data is still loading. Please try again."` and do not submit.

Out of scope: order placement changes, order wire-schema changes, Scale price canonicalization changes, any change to the existing GTC/ALO mapping, fee-rate changes, account-abstraction normalization changes, new PM API calls or risk calculations, staking protocol/business-rule changes, and unrelated account/subaccount behavior. In particular, retain validator one-day lock semantics, the staking withdrawal queue behavior, existing `cDeposit`, `cWithdraw`, and `tokenDelegate` action shapes, and exchange/runtime error propagation.

This is correctness work, not performance work. It introduces no hot-path optimization, new data structure, or throughput claim; consequently no performance baseline is required. The Scale calculation remains a bounded maximum of 100 legs, the existing `scale-max-order-count`.

## Progress

- [x] (2026-08-02 15:57Z) Read the planning, multi-agent, work-tracking, quality, browser, and UI contracts; inspected all current production anchors and existing focused tests without editing source or tests.
- [x] (2026-08-02 15:57Z) Reproduced the Scale arithmetic from the existing floored-leg formula: HYPE `1414.70`, `48.5 -> 40.5`, 100, skew 1.5, and 2 size decimals totals `62546.995757575765`, which formats as `$62,547.00`.
- [x] (2026-08-02 15:57Z) Froze the PM policy at truthful `N/A` absent an already-normalized, authoritative buying-power field; no such field exists in the current audited state paths.
- [x] (2026-08-02 15:57Z) Incorporated the supplied fee and Scale-order-type evidence: loaded user rates yield the screenshot-native `0.0405% / 0.0135%`; missing/incomplete rates deliberately retain the conservative `0.045% / 0.015%` fallback; Scale already maps false/true post-only to GTC/ALO but hides its control.
- [x] (2026-08-02 16:48Z) Materialized the approved RED unit/integration coverage for Scale summary/value formatting and incomplete ladders; PM labels/risk rows; visible Scale Post Only; native staking identity, loader, and addressless effects. `npm test` compiled and ran `5,782` tests / `31,920` assertions, exiting `1` with `25` failures and `4` errors that match the intended defects: generated Scale value expected `62546.995757575765` / `$62,547.00` but got `72733.9711` / `$72,733.97`; incomplete Scale returned the same fabricated notional; PM returned `999` available and `$10` margin; the ticket rendered `Classic` and hid Post Only; selected-subaccount staking reads used the selected address rather than owner; and the new semantic helper was absent. Existing fee coverage remains unchanged because it already proves loaded versus conservative fallback semantics.
- [x] (2026-08-02 16:48Z) Deferred adding a Playwright RED test: the existing `HYPEROPEN_DEBUG` contract exposes dispatch/snapshot/oracle but not a stable state-fixture setter for raw account abstraction plus Scale legs. Existing tests directly reset private globals for bespoke fixtures; this plan does not add a new browser-only internal seam. The worker must make the focused CLJS contract green before browser QA.
- [x] (2026-08-02 17:12Z) Extended the RED contract for isolated staking spot state, facade aliases, account-scoped loader reset, response freshness, default state, and submit readiness. The latest `npm test` compile completed, then the generated runner remained RED at the expected source gaps: the semantic helper and staking spot projection/facade vars are undeclared; the adapter still injects nil generic-spot dependencies; selected-subaccount reads still target the selected address; global spot still drives staking max/validation; and otherwise-valid deposit, withdraw, delegate, and undelegate states emit submit effects rather than the frozen not-ready error. The test fixtures also now require the response guard to compare both current wallet/semantic identity and saved staking account, while preserving invalid/read-only/disconnected precedence.
- [x] (2026-08-02 17:31Z) Split the new Scale/PM regressions into `test/hyperopen/state/trading/scale_pm_summary_test.cljs` so the original market-summary namespace is now 489 lines; `npm run test:runner:generate` discovered 844 namespaces and `npm run lint:namespace-sizes` passed without an exception. The final RED run executed `5,793` tests / `32,012` assertions and exited `1` with the five intentional failures: three assertions that isolated staking-spot errors reach the route VM/view, plus the same-address older-success and older-error race cases. It also included the strict contract that a queued explicit old-address request after B becomes current performs no begin, network request, or state mutation.
- [x] (2026-08-02 17:44Z) Implemented the approved Scale, PM, and native-staking changes. The current full `npm test` run is GREEN: `5,794` tests / `32,016` assertions. The implementation shares the floored-leg Scale calculation, exposes Scale Post Only without changing GTC/ALO request semantics, displays the raw PM label and unavailable PM risk values, and makes native staking load/validation/refresh use one semantic account identity.
- [x] (2026-08-02 17:48Z) Completed independent-review fixes and their deterministic coverage: isolated staking spot errors are now visible in the staking route VM/view; an explicit old-address effect is rejected before loading or I/O; and each staking resource has its own monotonically increasing request generation so an A1 success or error cannot overwrite newer A2 state for the same address. `test/hyperopen/staking/effects_freshness_test.cljs` is 221 lines and `test/hyperopen/staking/effects_test.cljs` is 308 lines; both remain within namespace-size policy. `git diff --check` passed.
- [x] (2026-08-02 17:52Z) Ran diagnostic browser evidence before the final source review fixes: existing public-route Playwright smoke passed `4/4`, and governed visual QA recorded all six passes for `/trade` and `/staking` at 375, 768, 1280, and 1440. This evidence is diagnostic only and does not satisfy final browser validation after the last source changes.
- [x] (2026-08-02 17:59Z) Re-ran final browser validation after the source review fixes. Existing Playwright public-route smoke passed `4/4` across trade/staking desktop/mobile routes; both governed browser scenarios passed; and all six governed UI passes reported zero issues for both routes at 375, 768, 1280, and 1440. Browser cleanup confirmed zero remaining sessions/listeners. Evidence artifacts: `tmp/browser-inspection/design-review-2026-08-02T17-06-32-531Z-0e722bae` and `tmp/browser-inspection/scenario-2026-08-02T17-06-11-359Z-1c1d8e28`.
- [x] (2026-08-02 17:59Z) Ran `npm run gates` successfully in `1m59s`: all `34/34` checks passed, with `6,515` tests / `35,416` assertions and zero failures/errors. The main suite passed `5,794` tests / `32,016` assertions; websocket passed `561` / `3,188`.

## Surprises & Discoveries

- Observation: the generic order summary derives `:order-value` from parsed total size and `reference-price`, so a Scale ladder is valued as one imaginary order.
  Evidence: `/hyperopen/src/hyperopen/domain/trading/market.cljs` `order-summary` currently calculates `(* size ref-price)` for every order type, while `/hyperopen/src/hyperopen/domain/trading/core.cljs` `scale-order-legs` independently floors individual sizes.

- Observation: the requested HYPE repro is exactly explained by two-decimal floored legs, not a general display rounding bug.
  Evidence: summing the existing `scale-order-legs` output gives `62546.995757575765`; the generic calculation produces `72733.97` when using the supplied total size and a reference price near `51.414...`.

- Observation: `build-scale-request` obtains its leg sizes from the same core generator, then canonicalizes prices for the wire request.
  Evidence: `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs` `build-scale-request` calls `build-scale-orders`, whose `scale-order-legs` source is `/hyperopen/src/hyperopen/domain/trading/core.cljs`; afterward it canonicalizes each `:p`. The Scale summary must use generator prices and floored sizes for the specified parity value, but must not change wire-price canonicalization.

- Observation: raw account abstraction is available even though the normalized mode cannot distinguish PM from a normal unified account.
  Evidence: `/hyperopen/src/hyperopen/api/fetch_compat.cljs` stores `{:mode ..., :abstraction-raw payload}`; `/hyperopen/test/hyperopen/api/default_user_abstraction_test.cljs` proves `portfolioMargin` becomes `{:mode :unified :abstraction-raw "portfolioMargin"}`; `/hyperopen/src/hyperopen/views/trade/order_form_controls.cljs` currently always renders `"Classic"`.

- Observation: current PM `Available to Trade` prefers spot USDC and `Margin Required` always applies classic notional/leverage math.
  Evidence: `/hyperopen/src/hyperopen/domain/trading/market.cljs` `available-to-trade` checks `unified-spot-usdc-available` first, then `order-summary` divides value by UI leverage. Repository searches found no authoritative PM buying-power selector or normalized response field.

- Observation: staking fetches follow `effective-account-address`, while signing and post-success refresh use the owner/master; however spectate and trader-portfolio routes intentionally use that effective identity for read-only inspection.
  Evidence: `/hyperopen/src/hyperopen/staking/actions.cljs` `load-staking` passes `effective-account-address` to each user-specific fetch; `/hyperopen/src/hyperopen/staking/effects.cljs` submits with `owner-address` and dispatches `[:actions/load-staking]` on success. An owned selected subaccount therefore can seed validation state from a different account than the signed operation, while changing all reads to owner would regress inspected read-only routes.

- Observation: the existing gateway tests already pin Scale GTC and ALO behavior.
  Evidence: `/hyperopen/test/hyperopen/api/gateway/orders/commands_test.cljs` asserts default Scale legs use `"Gtc"`, post-only legs use `"Alo"`, and the request grouping is `"na"`.

- Observation: the fee engine already produces the expected fee rate when complete loaded data is present; the generic rate is a conservative fallback, not bad fee arithmetic.
  Evidence: with `userCrossRate 0.00045`, `userAddRate 0.00015`, and `activeReferralDiscount 0.1`, the established formula yields `0.0405% / 0.0135%`. The current `market_summary_test.cljs` already covers closely related loaded user-fee selection; incomplete/missing user fees fall back to `default-fees` `0.045% / 0.015%`.

- Observation: Scale supports ALO in the gateway but hides the user control in the order-ticket capability registry; a true value can therefore remain in the draft after a type transition without being visible.
  Evidence: `commands.cljs` chooses `"Alo"` when `:post-only` is true and `"Gtc"` otherwise, `order_form_view.cljs` only renders the toggle when `:supports-post-only?` is true, and `order_type_registry.cljs` omits that capability from `:scale` while Stop Limit and Take Limit enable it.

- Observation: account equality alone rejects a former account but cannot distinguish two in-flight reads for the same account, and the staking route error selector initially omitted isolated spot errors.
  Evidence: the original `staking/effects.cljs` applied any response passing its current-address predicate, so an A1 success/error could overwrite a completed A2 response for the same saved/semantic address. The final implementation stores `[:staking :request-generations resource]` and requires the generation to match before applying success or error; `staking/vm.cljs` now includes `:spot-state` in route-error aggregation. `test/hyperopen/staking/effects_freshness_test.cljs` proves newer-success/older-success and newer-success/older-error orderings, while `test/hyperopen/views/staking_view_test.cljs` proves the visible error banner.

- Observation: the available debug bridge cannot safely and deterministically construct the authenticated raw-PM, exact Scale-ladder, or master-versus-owned-subaccount state required for these defects.
  Evidence: `HYPEROPEN_DEBUG` exposes dispatch/snapshot/oracle but no approved state-fixture setter for those private authenticated state paths. Existing browser tests that reset private globals are not a safe contract to extend, and this task deliberately did not add a browser-only private-state mutation seam.

## Decision Log

- Decision: compute Scale `Order Value` from `scale-order-legs`, not from the submission command map and not by replaying a reference-price formula.
  Rationale: the core generator is deterministic, pure, shared with the request builder, and contains the individual-size flooring that causes the defect. Pulling the domain summary into a gateway namespace would invert layers; duplicating the generator would permit the displayed and generated ladders to diverge.
  Date/Author: 2026-08-02 / Codex

- Decision: preserve exact Scale execution semantics: ordinary Scale remains GTC, post-only Scale remains ALO, `:grouping "na"` remains intact, and wire price canonicalization remains where it is.
  Rationale: a notional-display fix must never silently change resting/crossing behavior or the signed action. Existing gateway tests form the regression boundary.
  Date/Author: 2026-08-02 / Codex

- Decision: do not revise fee-rate sourcing for Scale or any other type.
  Rationale: `/hyperopen/src/hyperopen/state/trading.cljs` calls `state.trading.fee-context/select-fee-context` once and passes it to `domain.trading.market/order-summary`; that selector incorporates active market type, DEX deployer scale, user rates, referral/staking discounts, and special-quote flags. Complete loaded rates `0.00045 / 0.00015` plus 10% referral discount already produce `0.0405% / 0.0135%`; absent/incomplete data intentionally falls back to `0.045% / 0.015%`. Scale must retain both paths and must not derive a separate rate merely because its legs rest as limits.
  Date/Author: 2026-08-02 / Codex

- Decision: expose Scale Post Only by enabling the existing capability, without changing the request builder.
  Rationale: the gateway already makes the exact GTC/ALO distinction and the form already owns `:post-only`. Rendering the control makes a persistent true value visible and user-controlled instead of introducing a new order type or silently altering a signed action.
  Date/Author: 2026-08-02 / Codex

- Decision: use `N/A`, not `0 USDC`, for PM values that lack an authoritative state source.
  Rationale: zero is a real financial value and spot USDC/classic leverage are known-wrong proxies. The existing formatter already renders a non-number as `N/A`, which is explicit without inventing a risk model.
  Date/Author: 2026-08-02 / Codex

- Decision: define one semantic `native-staking-account-address` helper: owner/master for mutable normal state, effective account only for read-only spectate and trader-portfolio inspection.
  Rationale: native staking actions are signed for the owner/master, so showing an owned subaccount's HYPE or delegation while then signing the owner's action is a preventable account-identity error. Spectate and trader portfolio are intentionally inspecting another identity and must keep loading it; their existing mutation guards remain the safety gate.
  Date/Author: 2026-08-02 / Codex

- Decision: isolate the address-scoped staking spot response under `[:staking :spot-state]`; do not reuse generic active-market spot projections.
  Rationale: in normal selected-subaccount state, redirecting the existing generic spot fetch to the owner/master would overwrite global active-trading spot state with a different account. Staking max controls, deposit validation, and the staking VM need the master/inspected snapshot, while trading consumers must retain the selected active-account snapshot.
  Date/Author: 2026-08-02 / Codex

- Decision: bind every address-scoped staking projection to the synchronously saved staking account and discard late responses for a former account.
  Rationale: account selection, spectate, and trader inspection can change while asynchronous reads are in flight. Clearing old projections avoids stale display; a current-address check plus `:loaded-for` provenance prevents a late A response from repopulating B's state.
  Date/Author: 2026-08-02 / Codex

- Decision: gate normal native-staking mutations on current account and resource provenance after the established read-only, owner, validator, input, and balance validation guards.
  Rationale: a syntactically valid amount is not enough when its balance/delegation snapshot may belong to a prior account. The frozen resource mapping—deposit `:spot-state`, withdraw/delegate `:delegator-summary`, undelegate `:delegations`—keeps the existing request shapes while making the no-submit state explicit and deterministic.
  Date/Author: 2026-08-02 / Codex

- Decision: use one independent request generation per address-scoped staking resource in addition to semantic-account identity checks.
  Rationale: identity checks discard A after B, but they cannot distinguish A1 from A2 when both target the same still-current account. Incrementing `[:staking :request-generations resource]` at begin and requiring it at success/error makes the later request authoritative without cross-resource cancellation.
  Date/Author: 2026-08-02 / Codex

- Decision: do not add or claim state-seeded Playwright regression coverage for raw PM, the exact Scale fixture, or master/subaccount private state.
  Rationale: no safe, approved debug fixture setter exists for that authenticated state, and introducing a browser-only mutation seam expands the product surface solely for tests. Deterministic CLJS domain, VM, Hiccup, action, projection, and controlled-promise effect tests are the acceptance proof for those states; existing public-route Playwright smoke and governed visual QA remain the browser proof for rendered routes and layout. The remaining browser blind spot is explicit rather than hidden by an unsafe test fixture.
  Date/Author: 2026-08-02 / Codex

## Outcomes & Retrospective

Implementation, focused review fixes, and final validation are complete. The final `npm run gates` report passed all `34/34` checks in `1m59s`, with `6,515` tests / `35,416` assertions and zero failures/errors. Its main suite passed `5,794` / `32,016`; websocket passed `561` / `3,188`. Namespace hygiene remains green: `market_summary_test.cljs` is 489 lines after extracting the focused Scale/PM suite, `effects_test.cljs` is 308 lines, `effects_freshness_test.cljs` is 221 lines, and `git diff --check` passed.

The delivered source behavior reduces the Scale and PM semantic gaps by reusing the deterministic ladder calculation and refusing to fabricate PM risk values. Staking adds a small, isolated `account_scope` boundary and per-resource generation metadata, increasing local state detail but reducing overall correctness complexity: every staking read and mutation now has one semantic account identity, stale same-account responses cannot clobber newer data, and master staking data cannot overwrite active trading spot state. Independent review confirmed and fixed the visible isolated-spot error, queued old-address preflight, and same-account race gaps.

Final browser evidence passed: Playwright public-route smoke was `4/4` across trade/staking desktop/mobile routes, both governed browser scenarios passed, and all six governed UI passes found zero issues for both routes at 375, 768, 1280, and 1440. Cleanup left zero browser sessions/listeners. The authenticated raw-PM, exact Scale-ladder, and master-versus-subaccount states remain deliberately fixture-blocked in the browser; their behavior is proven by deterministic CLJS tests, while browser validation proves public routes, scenarios, and visual behavior. Completion confidence is `100.0%` (testing 40/40, independent review 30/30, logical inspection 30/30), above the required `84.7%`. Release readiness: PASS; this plan is accepted and moves to `docs/exec-plans/completed/`.

## Context and Orientation

The trading ticket is composed from `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs`, which passes the full state to `/hyperopen/src/hyperopen/views/trade/order_form_controls.cljs` for the margin/leverage/mode row, and uses `/hyperopen/src/hyperopen/views/trade/order_form_footer.cljs` for the summary metrics. `/hyperopen/src/hyperopen/trading/order_form_application.cljs` creates the view-model context, while `/hyperopen/src/hyperopen/state/trading.cljs` bridges application state to pure trading functions.

`Scale` means one submitted exchange action containing multiple limit-order legs at interpolated prices. A leg has a price and a size. `/hyperopen/src/hyperopen/domain/trading/core.cljs` `scale-order-legs` produces those legs using the requested count and skew and floors every size to the market's supported number of size decimals. A `GTC` order stays on the book until it fills or is cancelled; `ALO` is the exchange's post-only time-in-force and must not cross the book. `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs` turns the generated legs into signed-wire candidate maps, but it is not a display-layer dependency.

`Available to Trade` and `Margin Required` are summary labels under the ticket. The current standard-margin computation can use perps balance data and UI leverage. Portfolio margin is an exchange risk model; this repository has no verified PM buying-power selector or calculation. Thus an unavailable PM number is safer and more truthful than a plausible calculation.

An account abstraction is the exchange's raw mode string. The app stores both normalized mode (`:account :mode`) and raw mode (`:account :abstraction-raw`). The normal mode is appropriate for broad behavior; raw mode is necessary for the mode chip and PM-specific presentation because both `unifiedAccount` and `portfolioMargin` normalize to `:unified`.

The staking route has two relevant account identities. `effective-account-address` follows selected subaccounts and read-only routes because it identifies the active trading view. `owner-address` is the normalized connected wallet address. Native staking belongs to the master/owner and the effects layer already signs the four staking mutations with that address. The load path must agree with that signing path before its snapshots are used for `Max`, amount validation, and the refresh after a successful action.

## Plan of Work

### Milestone 1 — Shared truthful ticket summary

Implemented `scale-order-value` in `/hyperopen/src/hyperopen/domain/trading/core.cljs`, beside `scale-order-legs`. It accepts the normalized form and market size decimals, invokes the shared generator, and sums finite positive `:price * :size` pairs only for a complete ladder. `/hyperopen/src/hyperopen/domain/trading/market.cljs` selects this value only when `:requested-type` is `:scale`; all non-Scale forms retain the existing `size * reference-price` result and incomplete Scale returns `nil`, displayed as `N/A`. `test/hyperopen/state/trading/scale_pm_summary_test.cljs` proves the HYPE `:szDecimals 2` fixture is approximately `62546.995757575765` and renders `$62,547.00`.

Fee construction remains untouched: `state.trading/order-summary` still calls `select-fee-context`, and `market/order-summary` still passes that context to `fees/quote-fees`. The existing fee regressions preserve complete loaded `userCrossRate 0.00045`, `userAddRate 0.00015`, and `activeReferralDiscount 0.1` as `0.0405% / 0.0135%`, and incomplete/missing data as the conservative `0.045% / 0.015%`. The displayed Scale value does not choose maker/taker fees.

`/hyperopen/src/hyperopen/trading/order_type_registry.cljs` now marks the existing `:scale` entry `:supports-post-only? true`; the pre-existing form path therefore renders and controls the same Post Only field rather than adding a new action. Deterministic order-form Hiccup/view-model coverage proves Scale renders that control, and established gateway coverage preserves default `{:limit {:tif "Gtc"}}`, post-only `{:limit {:tif "Alo"}}`, grouping `"na"`, side/reduce-only flags, and price canonicalization in `build-scale-request`. No live order is required.

### Milestone 2 — Raw mode display and PM-safe summary fields

Implemented the pure `portfolio-margin-abstraction?` predicate beside the trading-domain helpers and the `account-mode-label` view-model helper. `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs` threads the frozen `PM`/`Classic` label through `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs` to the existing disabled `controls/leverage-row` chip; margin-mode, leverage controls, and keyboard/focus behavior remain unchanged. `test/hyperopen/views/trade/order_form_view_test.cljs` and `test/hyperopen/views/trade/order_form_vm_test.cljs` prove whitespace/case-insensitive raw `portfolioMargin` yields `PM`, while supported non-PM, absent, and unknown raw values yield `Classic`.

In `market/order-summary`, the normalized case-insensitive predicate sets `:available-to-trade` and `:margin-required` to `nil` only for raw PM. The audit found no pre-existing, verified PM buying-power field, so neither a spot balance, `withdrawable`, account value, margin-used amount, UI leverage calculation, endpoint, nor guessed field was accepted. `test/hyperopen/state/trading/scale_pm_summary_test.cljs` gives PM both a `999` USDC spot balance and `888` withdrawable value yet observes visible `N/A` for both fields; a raw `unifiedAccount` control fixture retains `999` available and `10` margin.

### Milestone 3 — Semantic native-staking account lifecycle

Implemented `native-staking-account-address` in `/hyperopen/src/hyperopen/account/context.cljs`: normal connected state resolves the owner/master despite an owned selected subaccount, while spectate and trader-portfolio inspection retain `effective-account-address`. `/hyperopen/src/hyperopen/staking/actions.cljs` now saves that semantic address, clears all five user projections, loading/error records, and `:loaded-for` records using the new `/hyperopen/src/hyperopen/staking/account_scope.cljs` boundary, then issues the five addressed reads; validator summaries remain address-free. With no semantic address, it clears the same user projections and loads only validator summaries. `test/hyperopen/account/context_test.cljs` and `test/hyperopen/staking/actions_test.cljs` observe the chosen address and the required save/clear-before-fetch order.

Implemented staking-owned spot begin/success/error projections in `/hyperopen/src/hyperopen/api/projections/staking.cljs` and their public aliases/adapter injection. They update only `[:staking :spot-state]`, `[:staking :loading :spot-state]`, `[:staking :errors :spot-state]`, and `[:staking :loaded-at-ms :spot-state]`, preserving global `[:spot :clearinghouse-state]`. Staking balance selection, deposit max/validation, and the staking VM now read the isolated snapshot. `test/hyperopen/api/projections/staking_test.cljs`, `test/hyperopen/runtime/effect_adapters/staking_test.cljs`, `test/hyperopen/staking/actions_test.cljs`, and `test/hyperopen/views/staking_view_test.cljs` observe those paths; the last also proves an isolated spot-load error reaches the visible route banner.

`/hyperopen/src/hyperopen/staking/effects.cljs` now resolves addressless fetches through the semantic helper. Before beginning load or invoking a request, each addressed effect requires its explicit/resolved address to equal both the saved account and current semantic identity; a queued stale explicit request is therefore a no-op. Each resource increments `[:staking :request-generations resource]` when its current request begins, and success/error applies only when both identity and that resource generation still match. Current success records `[:staking :loaded-for resource]`. This prevents both A-after-B and A1-after-A2 clobbers without coupling independent resources. `test/hyperopen/staking/effects_test.cljs` proves address fallback, loaded-for, and A-after-B behavior; `test/hyperopen/staking/effects_freshness_test.cljs` proves same-address ordering and strict old-address preflight. The VM aggregates `:spot-state` errors.

After the existing read-only, owner, validator, input, and balance guards, actions require matching saved-account provenance: deposit `:spot-state`, withdraw/delegate `:delegator-summary`, and undelegate `:delegations`. A missing or mismatched current snapshot emits exactly `"Staking account data is still loading. Please try again."`, clears its submitting flag, and emits no submit effect. Existing owner-address signing, action payloads, success toast, submitting reset, `[:actions/load-staking]` refresh, exchange failure handling, runtime catch handling, one-day validator lock, and withdrawal queue behavior remain unchanged.

The worker must not implement a local one-day lock, modify an existing validator lock, reorder/proxy a withdrawal queue, or swallow errors. Tests must characterize that an unsuccessful `cWithdraw` / `tokenDelegate` response still leaves the exact surfaced error and submitting flag behavior already required by `staking/effects.cljs`.

### Milestone 4 — Browser proof and release validation

No new state-seeded Playwright regression is part of this milestone. There is no safe approved debug fixture setter for authenticated raw PM, the exact Scale ladder, or master-versus-owned-subaccount state, and the implementation deliberately adds no browser-only private-state mutation seam. The deterministic CLJS domain, view-model, Hiccup, action, projection, and controlled-promise effect suites above are the direct proof for those states. Existing public-route Playwright smoke remains the committed browser check; it must not sign, call a live exchange, or pretend that public state exercises private authenticated conditions.

Final-source browser validation passed. Existing Playwright public-route smoke passed `4/4` for trade/staking desktop/mobile routes, both governed browser scenarios passed, and the visual, native-control, styling-consistency, interaction, layout-regression, and jank/performance passes all recorded zero issues for both routes at 375, 768, 1280, and 1440. `/trade` desktop geometry remained intact. `npm run browser:cleanup` confirmed zero sessions/listeners. Artifact directories are `tmp/browser-inspection/design-review-2026-08-02T17-06-32-531Z-0e722bae` and `tmp/browser-inspection/scenario-2026-08-02T17-06-11-359Z-1c1d8e28`.

## Concrete Steps

Run all commands from `/Users/barry/.codex/worktrees/925a/hyperopen`.

1. The RED and implementation phases are complete. Their recorded full-suite evidence is:

       RED:   npm test  -> exit 1, 5,793 tests / 32,012 assertions, 5 intended failures
       GREEN: npm test  -> exit 0, 5,794 tests / 32,016 assertions

   The five RED failures covered visible isolated-staking-spot errors and older same-address staking responses. The GREEN run is the current deterministic implementation evidence. Bootstrap with `npm run setup:worktree` before re-running any Node command; an unbootstrapped-checkout error is environmental, not a source failure.

2. The implementation hygiene checks have also passed:

       npm run test:runner:generate
       npm run lint:namespace-sizes
       git diff --check

   Expected recorded evidence: 844 test namespaces are generated; namespace lint passes with `market_summary_test.cljs` at 489 lines, `effects_test.cljs` at 308 lines, and `effects_freshness_test.cljs` at 221 lines; `git diff --check` prints no whitespace errors.

3. Final existing public-route browser evidence passed, without introducing private-state fixtures:

       npm run test:playwright:smoke
       npm run qa:design-ui -- --targets trade-route,staking-route --manage-local-app
       npm run browser:cleanup

   Observed: public-route smoke passed `4/4` across trade/staking desktop/mobile routes; both browser scenarios passed; and every governed visual, native-control, styling-consistency, interaction, layout-regression, and jank/performance pass reported zero issues at `/trade` and `/staking`, widths 375, 768, 1280, and 1440. Cleanup reported zero sessions/listeners. See the final artifact directories in Milestone 4.

4. Run final required gates without short-circuiting the report:

       npm run gates

   Observed final matrix: `34/34` checks PASS in `1m59s`, with `6,515` tests / `35,416` assertions and zero failures/errors. `npm test` passed `5,794` / `32,016`; `npm run test:websocket` passed `561` / `3,188`.

## Validation and Acceptance

Acceptance is complete. Each observable behavior below is demonstrated by its named deterministic test, and final existing public-route Playwright smoke plus governed visual review passed. Browser validation does not claim to seed authenticated PM/Scale/subaccount state; those fixture-blocked states remain covered by the deterministic tests named below.

- Scale calculation: `test/hyperopen/state/trading/scale_pm_summary_test.cljs` constructs HYPE with `:szDecimals 2`, size `1414.70`, range `48.5` to `40.5`, count `100`, and skew `1.5`; it observes raw generated-leg sum approximately `62546.995757575765` and formatted `$62,547.00`, never the generic `$72,733.97` fixture result.
- Scale isolation: the same focused test observes a normal limit order of size `2`, price `100`, leverage `20` remains order value `200` and margin required `10`; an incomplete Scale ladder returns `nil` and formats `N/A`, not a fabricated number.
- Scale execution and fees: existing `test/hyperopen/api/gateway/orders/commands_test.cljs` observes unchanged `Gtc`, `Alo`, `"na"`, side, and reduce-only wire semantics. Trade order-form view/Hiccup coverage observes Scale's existing Post Only toggle is visible. Existing `market_summary_test.cljs` observes loaded rates `0.0405% / 0.0135%` and incomplete rates `0.045% / 0.015%`; neither path uses a Scale-specific fee source.
- Mode chip: `test/hyperopen/views/trade/order_form_view_test.cljs` and `test/hyperopen/views/trade/order_form_vm_test.cljs` observe `PM` for `:abstraction-raw " portfolioMargin "` and `Classic` for `unifiedAccount`, `default`, `disabled`, `dexAbstraction`, missing, and unknown raw values. The existing raw-abstraction projection tests remain green, proving no account-normalization regression.
- PM truthfulness: `test/hyperopen/state/trading/scale_pm_summary_test.cljs` gives a PM fixture a nonzero USDC spot balance (`999`) and `withdrawable` (`888`) and observes `N/A` for both `Available to Trade` and `Margin Required`, rather than the spot amount or classic `order-value / leverage` result. Its raw `unifiedAccount` control fixture retains numeric values. No test depends on a guessed PM data field.
- Staking semantic identity: `test/hyperopen/account/context_test.cljs` asserts `native-staking-account-address` returns the owner wallet with an owned selected subaccount, and returns the effective spectate/trader address on existing read-only inspection routes. `test/hyperopen/staking/actions_test.cljs` asserts every address-bearing effect from `load-staking` uses the semantic helper's master result for a selected subaccount. The different subaccount amounts must not become `Max`/validation inputs after master load.
- Staking effect fallback and refresh: `test/hyperopen/staking/effects_test.cljs` asserts direct addressless fetch effects resolve the semantic native-staking identity, signed `cDeposit`, `cWithdraw`, and `tokenDelegate` still receive the owner, and a successful submission dispatches `[:actions/load-staking]` which re-requests master data in normal state. It also retains failure/error-propagation assertions, inspected-route read-only behavior, and queued withdrawal/validator lock behavior unchanged.
- Staking spot isolation and freshness: `test/hyperopen/api/projections/staking_test.cljs` observes staking spot begin/success/error write only the staking-owned value/loading/error/loaded-at paths and preserve global `[:spot :clearinghouse-state]`; `test/hyperopen/runtime/effect_adapters/staking_test.cljs` observes the adapter injects those staking projections, not generic market spot projections. `test/hyperopen/staking/actions_test.cljs` observes `load-staking` saves its semantic address and clears all five account-scoped values/loading/errors/loaded-for before fetch. `test/hyperopen/staking/effects_test.cljs` observes current responses record `:loaded-for` for every resource and A is ignored after B becomes current. `test/hyperopen/staking/effects_freshness_test.cljs` observes A1 success and A1 error cannot overwrite newer A2 state for the same account, and a queued explicit old address makes no begin/request/state change after B is current. `test/hyperopen/views/staking_view_test.cljs` observes max/validation and displayed transferable HYPE use `[:staking :spot-state]` when it differs from global spot state, and observes the isolated spot error banner.
- Staking readiness and defaults: `test/hyperopen/staking/actions_test.cljs` proves each otherwise-valid normal mutation requires its current saved semantic address and the matching resource `:loaded-for` tag, returns exactly `"Staking account data is still loading. Please try again."` with no submit effect when either is absent/mismatched, and retains existing read-only/disconnected/input/balance precedence. `test/hyperopen/state/app_defaults_test.cljs` proves fresh state initializes account address, staking spot value/loading/error, and each of the five loaded-for entries. `test/hyperopen/api/projections/facade_contract_test.cljs` proves the public facade directly aliases the staking-owned spot projection trio.
- Browser: final `npm run test:playwright:smoke` passed `4/4` across trade/staking desktop/mobile routes, and both governed browser scenarios passed. It verifies public routes only. Residual blind spot: raw PM, the exact Scale-ladder fixture, and master-versus-owned-subaccount private state have no safe approved browser fixture setter; these facts are accepted through the named deterministic CLJS tests above, not fabricated browser-state coverage.
- UI QA: all six governed passes completed with zero issues for both routes at 375, 768, 1280, and 1440, including `/trade` desktop geometry at 1280 and 1440. Browser cleanup confirmed zero sessions/listeners. Final evidence lives in the named design-review and scenario artifact directories.
- Required gates: `npm run gates` passed all `34/34` checks in `1m59s`, including `npm run check`, `npm test`, and `npm run test:websocket`; it reported `6,515` tests / `35,416` assertions and zero failures/errors. Completion confidence is `100.0%` using the 40% testing, 30% review, and 30% logical-inspection weights in `docs/QUALITY_SCORE.md`.

## Risks and Non-Goals

The primary risk is displaying a value that resembles an exchange-authoritative portfolio-margin number but is not one. Mitigation: PM receives `N/A` until a state contract proves otherwise; no call to spot balance, perps withdrawable, account value, margin used, or UI leverage may be used to fill it.

The Scale risk is accidentally changing signed legs while changing only a display value. Mitigation: summarize from existing generated legs and retain request-shape characterization tests for GTC, ALO, grouping, side, reduce-only, price canonicalization, and fee-rate sourcing.

The staking risk is broadening a master-only rule into spectate or trader routes, or letting an older same-account request overwrite newer data. Mitigation: retain `mutations-blocked-message`, make the semantic helper choose effective identity only for those read-only inspection routes, use the master only in normal state, preserve no-address clearing behavior, and require the current per-resource request generation before applying a response. Do not turn a selected owned subaccount into an error; it should simply see the master staking account.

The residual browser risk is that public routes cannot safely synthesize authenticated raw PM, exact Scale-ladder, or master/subaccount private state. Mitigation: keep the browser surface free of a test-only private-state mutation API, exercise those scenarios with deterministic CLJS domain/VM/Hiccup/action/projection/effect tests, and rerun public-route smoke plus governed visual QA after final source changes.

No performance target, PM risk engine, endpoint, backend schema, new abstraction normalizer, signing contract, queue scheduler, validator lock, or browser-only state API is included in this plan.

## Idempotence and Recovery

The pure summary, mode-label, action, projection, and controlled-promise freshness tests can be rerun without external state. Existing public-route browser coverage must not submit to a live exchange; it must not mutate private authenticated state through an unsupported debug path. If a PM buying-power source cannot be demonstrated with a stable state path and provenance, keep the `N/A` implementation; do not block the plan by inventing a fallback.

If a Scale test exposes a price-canonicalization mismatch, revert only the new summary helper and retain the untouched gateway behavior. Re-check the HYPE fixture against raw `scale-order-legs`; the specified `$62,547.00` contract uses floored generated sizes with their generated ladder prices, not post-canonicalized wire-price truncation.

If master-scoped staking refresh regressions appear, restore the action/effect behavior together rather than leaving mixed identities. The safe retry is to make `load-staking` and the effect `resolve-address` use the same owner helper, then rerun the pure action/effect tests before browser work. Never use destructive state resets, remote sync, or live wallet signing as recovery.

## Artifacts and Notes

Reproduced and implemented Scale evidence:

    Inputs: size 1414.70 HYPE; start 48.5; end 40.5; count 100; skew 1.5; szDecimals 2
    Existing core legs: sum(price * floor(size, 2)) = 62546.995757575765
    Required ticket text: $62,547.00
    Defective generic summary: 72733.97

Final validation evidence:

    npm run gates
    PASS 34/34 in 1m59s; 6,515 tests / 35,416 assertions; 0 failures / 0 errors
    Main suite: 5,794 tests / 32,016 assertions
    Websocket suite: 561 tests / 3,188 assertions
    Playwright public-route smoke: PASS 4/4 trade/staking desktop/mobile routes
    Governed browser scenarios: PASS 2/2
    Governed UI passes: PASS, zero issues, both routes at 375/768/1280/1440
    Browser cleanup: 0 sessions / 0 listeners

Final browser artifacts:

    tmp/browser-inspection/design-review-2026-08-02T17-06-32-531Z-0e722bae
    tmp/browser-inspection/scenario-2026-08-02T17-06-11-359Z-1c1d8e28

Implemented source boundaries:

    domain summary: src/hyperopen/domain/trading/market.cljs -> order-summary
    ladder generator and value helper: src/hyperopen/domain/trading/core.cljs -> scale-order-legs, scale-order-value
    signed request: src/hyperopen/api/gateway/orders/commands.cljs -> build-scale-request
    fee source: src/hyperopen/state/trading/fee_context.cljs -> select-fee-context
    ticket mode chip: src/hyperopen/views/trade/order_form_controls.cljs -> leverage-row
    user abstraction snapshot: src/hyperopen/api/fetch_compat.cljs -> fetch-user-abstraction!
    native staking identity: src/hyperopen/account/context.cljs -> native-staking-account-address
    staking load/action: src/hyperopen/staking/actions.cljs -> load-staking
    staking effect/freshness: src/hyperopen/staking/effects.cljs -> fetch-staking-resource!, request generations
    staking account boundary: src/hyperopen/staking/account_scope.cljs -> current-address?, resource-ready?
    staking route error aggregation: src/hyperopen/views/staking/vm.cljs -> staking-route-error

Implemented source and test files:

    src/hyperopen/domain/trading/market.cljs
    src/hyperopen/domain/trading/core.cljs
    src/hyperopen/trading/order_type_registry.cljs
    src/hyperopen/schema/order_form_contracts.cljs
    src/hyperopen/views/trade/order_form_controls.cljs
    src/hyperopen/views/trade/order_form_vm.cljs
    src/hyperopen/views/trade/order_form_view.cljs
    src/hyperopen/account/context.cljs
    src/hyperopen/staking/account_scope.cljs
    src/hyperopen/staking/actions.cljs
    src/hyperopen/staking/effects.cljs
    src/hyperopen/api/projections.cljs
    src/hyperopen/api/projections/staking.cljs
    src/hyperopen/runtime/effect_adapters/staking.cljs
    src/hyperopen/state/app_defaults.cljs
    src/hyperopen/views/staking/vm.cljs
    test/hyperopen/state/trading/market_summary_test.cljs
    test/hyperopen/state/trading/scale_pm_summary_test.cljs
    test/hyperopen/views/trade/order_form_view_test.cljs
    test/hyperopen/views/trade/order_form_vm_test.cljs
    test/hyperopen/schema/contracts_test.cljs
    test/hyperopen/account/context_test.cljs
    test/hyperopen/api/projections/facade_contract_test.cljs
    test/hyperopen/api/projections/staking_test.cljs
    test/hyperopen/runtime/effect_adapters/staking_test.cljs
    test/hyperopen/staking/actions_test.cljs
    test/hyperopen/staking/effects_test.cljs
    test/hyperopen/staking/effects_freshness_test.cljs
    test/hyperopen/state/app_defaults_test.cljs
    test/hyperopen/views/staking_view_test.cljs
    test/test_runner_generated.cljs
    docs/exec-plans/active/2026-08-02-portfolio-margin-scale-staking-parity.md

No Playwright test file changed. The absent browser fixture seam is intentional; see Milestone 4 and the Browser acceptance criterion. The final browser run validates public routes/scenarios and visual behavior; authenticated raw-PM, exact Scale, and master/subaccount states remain deterministic-CLJS-only by design.

## Interfaces and Dependencies

The existing `scale-order-legs` interface remains the source of truth:

    (scale-order-legs size count skew start end {:sz-decimals sz-decimals})
    ;; => [{:price number :size floored-number} ...] or nil

The implementation adds two pure helpers in the same domain module:

    (portfolio-margin-abstraction? raw-abstraction)
    ;; => true only for a case/whitespace-insensitive "portfolioMargin" string

    (scale-order-value form {:sz-decimals n})
    ;; => finite positive sum of generated floored legs, or nil for invalid/incomplete legs

The order summary remains a map consumed by `order_form_summary_display.cljs`:

    {:available-to-trade number-or-nil
     :order-value number-or-nil
     :margin-required number-or-nil
     :fees fee-quote}

For raw `portfolioMargin`, this plan requires `:available-to-trade nil` and `:margin-required nil` in the absence of an explicitly proven existing PM buying-power field. Formatters already map nil to visible `N/A`; no new presentation dependency is necessary.

The ticket view-model may gain one internal string such as `:account-mode-label`. `leverage-row` may accept that string in place of its literal, but the public order form/action APIs must not change.

`native-staking-account-address` is an account-context helper with this behavior:

    (native-staking-account-address state)
    ;; normal connected state, including owned selected subaccount => owner-address
    ;; spectate or trader-portfolio inspection => effective-account-address

`load-staking` continues to emit the same fetch effect identifiers and no address is added to mutation payloads. Its five address-bearing fetch effects receive the helper's result. Fresh default staking state includes `[:staking :account-address] nil`, isolated spot value/loading/error defaults, and nil `:loaded-for` entries for every address-scoped resource. `staking/account_scope.cljs` defines `current-address?` as equality with both the stored account and semantic native-staking account; `resource-ready?` additionally requires the matching `:loaded-for` address. `staking/effects.cljs` uses the same fallback when callers omit an address, preflights resolved or explicit addresses before side effects, treats a missing `[:staking :request-generations resource]` as generation `0`, increments only that resource at begin, and applies success/error only when both identity and generation remain current. Submission still calls the existing trading API with `(store owner-address action)` and refreshes through `[:actions/load-staking]`; before it can do so, the normal-state resource must carry the frozen `:loaded-for` provenance mapping.

Revision note (2026-08-02): Initial active ExecPlan created from the direct request and source audit. It freezes Scale value/fee/TIF semantics, raw PM labeling with unavailable-risk fallback, and master-scoped native staking before any implementation begins.

Revision note (2026-08-02): Corrected the third-chip mapping to the official PM-versus-Classic grouping. Only case/whitespace-insensitive `portfolioMargin` displays `PM`; standard, DEX abstraction, unified account, missing, and unknown raw values display `Classic`. This removes the unsupported `Unified` label without changing any other scope.

Revision note (2026-08-02): Refined staking identity to a semantic `native-staking-account-address` helper. It resolves to the owner/master for normal selected-subaccount state and to the effective inspected account for existing spectate/trader read-only routes; actions and addressless effect fallbacks share that helper. Address-scoped projections now carry current-account and `:loaded-for` provenance; late responses must satisfy both stored and current semantic identity checks. Normal mutation submission also requires the resource-specific current provenance gate.

Revision note (2026-08-02): Refreshed this active plan after implementation and independent review. The final deterministic suite is GREEN at `5,794` tests / `32,016` assertions; review fixes include visible staking-spot errors, strict queued old-address preflight, and per-resource request generations. Removed the contradictory requirement for state-seeded Playwright coverage: no safe approved authenticated-state fixture setter exists and no browser-only mutation seam was added. Final public-route browser smoke, governed visual QA, and `npm run gates` remain explicitly pending, so the plan stays active.

Revision note (2026-08-02): Final browser QA and `npm run gates` passed. Public-route Playwright smoke passed `4/4`, both browser scenarios passed, all six governed UI passes found zero issues at 375/768/1280/1440 for `/trade` and `/staking`, cleanup left zero sessions/listeners, and the gates passed `34/34` in `1m59s` with `6,515` tests / `35,416` assertions. Completion confidence is `100.0%`; the accepted plan moves to `docs/exec-plans/completed/` while retaining the explicit authenticated-state browser fixture limitation.
