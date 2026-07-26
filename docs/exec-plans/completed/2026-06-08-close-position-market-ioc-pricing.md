# Close Position Market IOC Pricing Fix

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Users must be able to close an open perp position from the Positions table using the `Close Position` market flow. Hyperopen currently submits that close as a reduce-only IOC order, which is the correct Hyperliquid wire shape, but it uses the position row's mark or mid price directly. For a short close this can place a buy IOC below the ask, so the exchange cancels it immediately with `Order could not immediately match against any resting orders`. After this fix, the close-position market flow will use a marketable protection price: best ask or bid plus slippage when the row coin's orderbook is loaded, otherwise the row mark plus slippage, always canonicalized for exchange submission.

The observable proof is a regression test where a CFX short close with best ask `0.046819` no longer submits `p: "0.046676"` and instead submits an aggressive buy price such as `0.049159`, while still preserving `asset=21`, `b=true`, `s="526"`, `r=true`, and `t.limit.tif="Ioc"`. A second regression test covers the exact live shape from this session: the active app had only a KAITO book loaded, so a CFX close must still submit a CFX mark-plus-slippage price such as `0.049067`, not the raw mark.

## Context References

Public refs:
- Direct user request in this Codex session on 2026-06-08: debug and fix a failed CFX close-position market order rejected with `Order could not immediately match against any resting orders. asset=21`.

Repo artifacts:
- `/hyperopen/AGENTS.md`
- `/hyperopen/docs/PLANS.md`
- `/hyperopen/docs/BROWSER_TESTING.md`
- `/hyperopen/docs/exec-plans/deferred/2026-02-24-market-order-execution-parity-and-submit-status.md`
- `/hyperopen/docs/exec-plans/completed/2026-05-13-order-error-toast-and-agent-safety.md`

Local scratch refs (non-authoritative):
- Live nREPL inspection on port `58838` in this session showed `position-reduce/prepare-submit` emitted `{:a 21, :b true, :p "0.046676", :s "526", :r true, :t {:limit {:tif "Ioc"}}}` for an open CFX short. It also showed the app had an orderbook only for the active route market `KAITO`, not CFX.

## Progress

- [x] (2026-06-08 16:19Z) Root-caused the live failure to non-marketable close-position IOC pricing, not asset id, side, reduce-only, signer, or size.
- [x] (2026-06-08 16:27Z) Created this active ExecPlan with the file-level fix strategy and validation expectations.
- [x] (2026-06-08 16:31Z) Added failing ClojureScript regression tests for CFX short market close protection pricing and missing target orderbook fail-closed behavior.
- [x] (2026-06-08 16:32Z) Implemented an initial code change so position-reduce market closes reused the existing market-order protection-price policy when an orderbook was loaded.
- [x] (2026-06-08 16:38Z) Reopened this plan after noticing the initial implementation would still block the exact live CFX close because the app had only a KAITO orderbook loaded.
- [x] (2026-06-08 16:39Z) Added a RED regression test proving a CFX close without a CFX orderbook should still use mark-plus-slippage protection pricing.
- [x] (2026-06-08 16:40Z) Implemented fallback mark-plus-slippage protection pricing in a small sibling namespace.
- [x] (2026-06-08 16:40Z) Ran focused regression verification with `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`; the RED failures were gone after implementation.
- [x] (2026-06-08 16:44Z) Reran required repository gates after the fallback implementation: `npm run check`, `npm test`, and `npm run test:websocket` all exited `0`.
- [x] (2026-06-08 16:44Z) Updated this plan with final evidence before moving it to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The outgoing close order used the correct Hyperliquid market-order wire shape, which is a limit order with `t.limit.tif` set to `Ioc`.
  Evidence: The live nREPL request and the network screenshot both showed `:t {:limit {:tif "Ioc"}}`.

- Observation: The order payload fields that would identify the position were correct.
  Evidence: `asset=21` is CFX in Hyperliquid metadata; `b=true` is the buy side needed to close a short; `s="526"` matches the absolute position size; `r=true` sets reduce-only.

- Observation: The exchange rejected the order because the close-position price was not marketable.
  Evidence: The live payload used `p: "0.046676"`, while a read-only CFX `l2Book` check showed the ask side above that price. A buy IOC below the ask cannot match resting asks and therefore cancels.

- Observation: The normal trade ticket already has a market-order pricing policy that uses best ask or bid, default slippage, and canonical formatting.
  Evidence: `/hyperopen/src/hyperopen/domain/trading/market.cljs` function `apply-market-price` and `/hyperopen/src/hyperopen/trading/submit_policy.cljs` function `prepare-order-form-for-submit`.

- Observation: The new regression tests fail before implementation for the expected behavioral reason.
  Evidence: `node out/test.js` reported the CFX market close price actual `"0.046731"` instead of expected `"0.049159"`, and the missing-orderbook case still returned `:ok? true` with an order request.

- Observation: The first `npm run check` after implementation failed only because the edited namespace exceeded the size guard by one line.
  Evidence: The check output reported `[missing-size-exception] src/hyperopen/account/history/position_reduce.cljs - namespace has 501 lines`; compacting the new helper code reduced the file to 496 lines and the rerun passed.

- Observation: The test runner command exits successfully after all tests complete, and the full log contains no `FAIL in` or `ERROR in` entries after the fix.
  Evidence: `npm test` exited `0`; a direct scan of `/tmp/hyperopen-close-position-npm-test.log` reported `FAIL_COUNT=0`.

- Observation: The initial orderbook-only fix was not enough for the exact live state.
  Evidence: Live nREPL had `:orderbook-keys ("KAITO")` while the open close popover was for `CFX`, so requiring `state[:orderbooks "CFX"]` would have changed the user-visible failure from exchange rejection to a local "market price unavailable" block.

- Observation: The corrected fallback regression failed before the mark-plus-slippage implementation.
  Evidence: `position-reduce-market-close-uses-mark-protection-price-without-target-orderbook-test` expected a CFX close request with `p "0.049067"`, but the orderbook-only implementation returned `:ok? false` and no submitted order.

## Decision Log

- Decision: Fix the close-position path by reusing the existing `hyperopen.domain.trading/apply-market-price` policy instead of creating a second market-pricing implementation.
  Rationale: The trade ticket already encodes the correct behavior and exchange formatting. Reusing it keeps order semantics consistent and limits the blast radius.
  Date/Author: 2026-06-08 / Codex

- Decision: Use the target orderbook when available, but fall back to row mark plus default market slippage when the target orderbook is unavailable.
  Rationale: The original bug was submitting raw mark without slippage. A slippage-adjusted mark preserves closeability for non-active Positions rows while still producing a marketable protection price in normal conditions.
  Date/Author: 2026-06-08 / Codex

- Decision: Move the close-position market-pricing helper into `/hyperopen/src/hyperopen/account/history/position_reduce_pricing.cljs`.
  Rationale: `/hyperopen/src/hyperopen/account/history/position_reduce.cljs` sits close to the repository namespace-size limit. A sibling namespace keeps the existing file below the guard and isolates the market-protection-price responsibility.
  Date/Author: 2026-06-08 / Codex

## Outcomes & Retrospective

Implemented. The close-position market path now prefers `hyperopen.domain.trading/apply-market-price` when the target orderbook is loaded, and falls back to row mark plus default market slippage when the orderbook is absent. This keeps non-active Positions rows closable while still avoiding raw-mark IOC submissions.

The original CFX short close would have submitted `p "0.046676"` as a reduce-only buy IOC. The regression coverage now proves the same close uses a marketable protection price: `p "0.049159"` with a CFX book whose best ask is `0.046819`, and `p "0.049067"` from row mark fallback when the live app shape has only a KAITO orderbook loaded.

## Context and Orientation

The Positions table renders row actions for open perps. Clicking `Close Position` opens a popover whose pure state and request builder live in `/hyperopen/src/hyperopen/account/history/position_reduce.cljs`. The popover records the coin, position side, absolute size, chosen percentage, and whether the user selected market or limit close.

In Hyperliquid protocol language, a market order is represented as an aggressive IOC limit order. `IOC` means "immediate or cancel": the order must match resting liquidity immediately or the exchange cancels it. For a short position, closing requires a buy order. That buy order must be priced at or above the best ask, usually with some slippage room. For a long position, closing requires a sell order priced at or below the best bid.

The normal trade ticket uses `/hyperopen/src/hyperopen/domain/trading/market.cljs` function `apply-market-price` to choose that aggressive price. It reads the target orderbook from a trading context. For buys it starts from the best ask; for sells it starts from the best bid; it applies `default-market-slippage-pct`, currently `5.0`; then it formats the price through `canonical-order-price-string` before the order is signed. The close-position flow also needs a fallback because users can close a row for a coin that is not the active chart coin; in that state the row has a mark price but the app may not have that coin's orderbook loaded.

The close-position path currently does not pass an orderbook to the shared market-pricing policy. Function `resolve-market-price` in `/hyperopen/src/hyperopen/account/history/position_reduce.cljs` chooses from popover mid price, limit price, market mark, and oracle price. For market close, `submit-price` uses that value directly. This is the exact bug: the popover's `mid-price` for CFX was `0.046676`, which was not enough to buy at the ask.

## Plan of Work

First, add a ClojureScript regression test under `/hyperopen/test/hyperopen/account/history/position_reduce_test.cljs`. The test should construct a CFX short row with position size `-526`, mark-like popover price `0.046676`, asset metadata `asset-id 21` and `szDecimals 0`, and a CFX orderbook with best ask `0.046819`. It should submit the default market close and expect an IOC reduce-only buy order priced from the best ask with default slippage, not from the row mid price.

Second, run the focused test command and confirm the new test fails before production changes. The expected failure is that the actual price remains `"0.046676"` rather than the expected aggressive market price.

Third, create `/hyperopen/src/hyperopen/account/history/position_reduce_pricing.cljs`. Add a helper that resolves the popover coin's orderbook from `state[:orderbooks coin]`, builds the same trading context shape expected by `trading-domain/apply-market-price`, and uses it for market close forms when possible. If the orderbook is missing, calculate a fallback protection price from the form's existing row-derived price and the default market slippage, then pass it through `trading-domain/canonical-order-price-string`. Keep limit closes unchanged. Preserve outcome behavior, where close orders intentionally do not use reduce-only perps semantics and should keep existing side behavior.

Fourth, rerun the focused test and confirm it passes. Then run the required project gates. Because this is a pure request-building change, no browser UI layout changed. Browser QA is not required unless implementation touches view files or interaction geometry.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/51dd/hyperopen`.

1. Edit `/hyperopen/test/hyperopen/account/history/position_reduce_test.cljs` to add tests named `position-reduce-market-close-uses-orderbook-protection-price-for-short-test` and `position-reduce-market-close-uses-mark-protection-price-without-target-orderbook-test`.

2. Run the focused test:

       npm run test:runner:generate
       npx shadow-cljs --force-spawn compile test && node out/test.js

   Observed before implementation: the first new test failed because the close-position market price was `"0.046731"`. After the initial orderbook-only implementation, the second fallback test failed because the no-CFX-orderbook case returned `:ok? false` and no submitted order.

3. Create `/hyperopen/src/hyperopen/account/history/position_reduce_pricing.cljs` and edit `/hyperopen/src/hyperopen/account/history/position_reduce.cljs` so `prepare-submit` builds market close forms through `position-reduce-pricing/market-close-form`. That helper should prefer `trading-domain/apply-market-price` using `{:active-asset (:coin popover), :asset-idx asset-id, :market market, :orderbook (get-in state [:orderbooks (:coin popover)])}`, then fall back to row mark plus default slippage when the orderbook is absent.

4. Rerun the focused regression command and confirm the new tests pass. Observed after fallback implementation: the command exited `0` and the RED failures were gone.

       npm run test:runner:generate
       npx shadow-cljs --force-spawn compile test && node out/test.js

5. Run required gates. Observed after fallback implementation:

       npm run check
       npm test
       npm run test:websocket

   Final validation passed. `npm run check` exited `0` and compiled `:app`, `:portfolio`, worker builds, and `:test` with `0 warnings`. `npm test` exited `0` with `FAIL_MARKERS:0`. `npm run test:websocket` exited `0` with `Ran 534 tests containing 3090 assertions. 0 failures, 0 errors.`

6. Update this plan's living sections with actual command output and move it from `docs/exec-plans/active/` to `docs/exec-plans/completed/` when validation passes. Observed after implementation: done.

## Validation and Acceptance

Acceptance is met when a CFX short position close at 100 percent produces a reduce-only buy IOC order whose price is based on the CFX best ask plus default slippage. With best ask `0.046819` and default market slippage `5.0`, the canonical price should be approximately `0.04916` for a `szDecimals 0` perp. The test should assert that the order remains `asset=21`, `b=true`, `s="526"`, `r=true`, and `t.limit.tif="Ioc"`.

Acceptance also requires that a market close with no target orderbook but a valid row mark uses mark plus default slippage. With row mark `0.046731` and default market slippage `5.0`, the canonical buy protection price should be `0.049067`. If neither orderbook nor positive row/market price is available, the existing market-price-unavailable guard may still block submission.

Required gates for code changes are:

    npm run check
    npm test
    npm run test:websocket

## Idempotence and Recovery

The test and code changes are additive and can be rerun safely. If the test runner fails because `node_modules` is missing in this fresh worktree, run `npm install` and repeat the commands. If the market-pricing change accidentally affects outcome close behavior, revert only the outcome branch of the helper and keep the perp regression red until the perp path is corrected. Do not remove or weaken the regression test after it catches the original bug.

## Artifacts and Notes

Root-cause evidence from live nREPL:

    {:popover {:coin "CFX", :position-side :short, :position-size 526, :close-type :market, :mid-price "0.046676"},
     :prepared {:request {:action {:type "order",
                                   :orders [{:a 21, :b true, :p "0.046676", :s "526", :r true, :t {:limit {:tif "Ioc"}}}],
                                   :grouping "na"}}},
     :orderbook-keys ("KAITO")}

Read-only CFX book evidence in the same session showed asks above the submitted price, so the buy IOC could not match.

RED test evidence before implementation:

    FAIL in (position-reduce-market-close-uses-orderbook-protection-price-for-short-test)
    expected: (= "0.049159" (:p submitted-order))
      actual: (not (= "0.049159" "0.046731"))

    FAIL in (position-reduce-market-close-fails-closed-without-target-orderbook-test)
    expected: (false? (:ok? result))
      actual: (not (false? true))

RED test evidence before fallback implementation:

    FAIL in (position-reduce-market-close-uses-mark-protection-price-without-target-orderbook-test)
    expected: (true? (:ok? result))
      actual: (not (true? false))

    FAIL in (position-reduce-market-close-uses-mark-protection-price-without-target-orderbook-test)
    expected: (= "0.049067" (:p submitted-order))
      actual: (not (= "0.049067" nil))

Earlier validation evidence before reopening for fallback behavior:

    npm run check
    EXIT:0
    [:app] Build completed. (1121 files, 1092 compiled, 0 warnings, 12.81s)
    [:portfolio] Build completed. (825 files, 824 compiled, 0 warnings, 9.36s)
    [:portfolio-worker] Build completed. (62 files, 61 compiled, 0 warnings, 3.71s)
    [:portfolio-optimizer-worker] Build completed. (100 files, 99 compiled, 0 warnings, 4.23s)
    [:vault-detail-worker] Build completed. (63 files, 62 compiled, 0 warnings, 3.15s)
    [:test] Build completed. (1781 files, 205 compiled, 0 warnings, 8.41s)

    npm test
    EXIT:0
    FAIL_COUNT=0

    npm run test:websocket
    EXIT:0
    Ran 534 tests containing 3090 assertions.
    0 failures, 0 errors.

Final validation evidence after fallback implementation:

    npm run check
    EXIT:0
    [:app] Build completed. (1122 files, 69 compiled, 0 warnings, 4.24s)
    [:portfolio] Build completed. (826 files, 53 compiled, 0 warnings, 2.98s)
    [:portfolio-worker] Build completed. (62 files, 0 compiled, 0 warnings, 0.92s)
    [:portfolio-optimizer-worker] Build completed. (100 files, 0 compiled, 0 warnings, 0.98s)
    [:vault-detail-worker] Build completed. (63 files, 0 compiled, 0 warnings, 0.93s)
    [:test] Build completed. (1782 files, 4 compiled, 0 warnings, 5.43s)

    npm test
    EXIT:0
    FAIL_MARKERS:0

    npm run test:websocket
    EXIT:0
    Ran 534 tests containing 3090 assertions.
    0 failures, 0 errors.

## Interfaces and Dependencies

Use existing modules only. No new dependency is required.

The target implementation interface is:

    hyperopen.account.history.position-reduce/prepare-submit
      input: app state and close-position popover state
      output: existing map shape with :ok?, :display-message, and optional :request

The shared market pricing interface to reuse is:

    hyperopen.domain.trading/apply-market-price
      input: trading context with :active-asset, :asset-idx, :market, and :orderbook plus a form with :type :market, :side, :size, and :reduce-only
      output: form with an exchange-canonical :price, or nil when the orderbook cannot provide a marketable price

The close-position pricing fallback interface is:

    hyperopen.account.history.position-reduce-pricing/market-close-form
      input: app state, close-position popover, resolved market, resolved asset id, and the base close form
      output: a market close form with exchange-canonical :price when either an orderbook or row-derived positive price is available

Revision note 2026-06-08 16:27Z: Initial active plan created from the direct user request, source trace, live nREPL payload, and CFX orderbook evidence.

Revision note 2026-06-08 16:31Z: Added RED-phase test evidence showing the current close-position path still uses mark-derived prices and submits without a target orderbook.

Revision note 2026-06-08 16:35Z: Added implementation, validation, and retrospective evidence before moving the plan to completed.

Revision note 2026-06-08 16:40Z: Reopened the plan and added fallback pricing scope after discovering the orderbook-only fix would still block the exact live CFX close when only the KAITO book was loaded.

Revision note 2026-06-08 16:44Z: Added final validation evidence after the fallback implementation before moving the plan to completed.
