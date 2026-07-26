# Trader Address Return Benchmarks

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `.agents/PLANS.md` from the repository root. It captures a direct user/maintainer request from 2026-05-25: add support on the portfolio returns benchmark page for comparing the current portfolio against another Hyperliquid trader address.

## Purpose / Big Picture

After this change, a user viewing the portfolio returns page can paste or type a Hyperliquid account address into the benchmark selector and add that trader as a benchmark alongside existing market symbols and vault benchmarks. The selected benchmark is shareable through the existing `bench` URL query parameter as `trader:0x...`. The app fetches that address's Hyperliquid portfolio history in the background, keeps it separate from the active account's portfolio data, and renders the other trader's return series and performance metric columns when data is available.

The visible behavior should be simple: open the returns benchmark selector, enter a valid `0x` address, select the trader option, and see a chip and chart/performance benchmark label such as `Trader 0x1234...5678`. Existing market and vault benchmarks must keep working.

## Context References

Public refs:
- Direct user/maintainer request in this Codex session on 2026-05-25.

Repo artifacts:
- `docs/PLANS.md` and `.agents/PLANS.md` define this ExecPlan format and lifecycle.
- `docs/references/hyperliquid-portfolio-history-and-returns.md` documents that Hyperliquid's `portfolio` info endpoint returns `accountValueHistory` and `pnlHistory` grouped by range, and that current app returns are derived from account value history.
- Existing benchmark plans in `docs/exec-plans/completed/2026-02-26-portfolio-returns-benchmark-series.md` and `docs/exec-plans/completed/2026-02-27-vault-detail-returns-benchmarks-performance-metrics.md` explain the predecessor market and vault benchmark work.

Local scratch refs:
- None.

## Progress

- [x] (2026-05-25T17:37Z) Inspected current portfolio benchmark actions, selector VM, computation VM, runtime effect adapter, and existing tests.
- [x] (2026-05-25T17:37Z) Created this active ExecPlan with the implementation shape and validation strategy.
- [x] (2026-05-25T17:42Z) Wrote RED tests for trader benchmark normalization, effect selection, selector option creation, VM computation, background fetch projections, facade aliases, and effect arg contracts.
- [x] (2026-05-25T17:42Z) Ran `npm test`; the compile phase reported undeclared trader benchmark/projection vars as expected before implementation.
- [x] (2026-05-25T17:55Z) Implemented keyed trader benchmark fetch state and runtime effect without clobbering the active portfolio.
- [x] (2026-05-25T17:55Z) Implemented selector support for typed trader addresses and fallback labels for `trader:` values.
- [x] (2026-05-25T17:55Z) Implemented benchmark computation support for trader portfolio summaries.
- [x] (2026-05-25T17:55Z) Ran `npm test`; 4049 tests and 22280 assertions passed with 0 failures and 0 errors.
- [x] (2026-05-25T17:55Z) Ran governed browser design review for `portfolio-route` at `375`, `768`, `1280`, and `1440`; all six required passes reported `PASS`.
- [x] (2026-05-25T18:00Z) Ran required repository gates: `npm run check`, `npm test`, and `npm run test:websocket`; all passed.

## Surprises & Discoveries

- Observation: Current vault benchmark support already uses a prefixed benchmark value (`vault:<address>`) and filters that source out of market candle fetches.
  Evidence: `src/hyperopen/portfolio/actions.cljs` defines `vault-benchmark-address`, `selected-portfolio-vault-benchmark-addresses`, and `fetchable-benchmark-coin`; `src/hyperopen/views/portfolio/vm/benchmarks/computation.cljs` branches on `selector/vault-benchmark-address`.
- Observation: The existing trader inspection route reuses active account context and is not appropriate for simultaneous benchmarking against a second address.
  Evidence: `src/hyperopen/portfolio/routes.cljs` supports `/portfolio/trader/<0x>`, while active portfolio fetch state is stored at `[:portfolio :summary-by-key]`, which would be overwritten by a benchmark fetch if reused directly.
- Observation: The RED `npm test` run compiles far enough to report the new missing trader benchmark vars, then exits at Node runtime because `lucide/dist/esm/icons/external-link.js` is not resolvable from the current dependency tree.
  Evidence: The command reported undeclared vars such as `hyperopen.portfolio.actions/trader-benchmark-address` and `hyperopen.api.projections.portfolio/begin-trader-benchmark-portfolio-load`, followed by `Error: Cannot find module 'lucide/dist/esm/icons/external-link.js'`.
- Observation: The `lucide` module-resolution error was caused by this worktree missing `node_modules`, not by the code change.
  Evidence: `ls node_modules` failed before setup; `npm ci` installed dependencies from `package-lock.json`, after which `npm test` ran to completion.
- Observation: Updating the effect-order policy in Clojure was insufficient until the Lean formal model was updated too.
  Evidence: `npm test` initially failed only `hyperopen.runtime.effect-order-contract-formal-conformance-test` for the three portfolio action policies; after editing `spec/lean/Hyperopen/Formal/EffectOrderContract.lean` and running `bb tools/formal.clj sync --surface effect-order-contract`, the full test suite passed.
- Observation: Governed browser QA passed on the default portfolio route, but the design-review runner still recorded residual interaction blind spots for hover, active, disabled, and loading states that are not present by default.
  Evidence: `npm run qa:design-ui -- --targets portfolio-route --manage-local-app --viewports review-375,review-768,review-1280,review-1440` reported `reviewOutcome: PASS` and `state: PASS`, with residual blind spots for state sampling at all four viewports.
- Observation: The repository namespace-size check requires explicit exceptions for this feature's larger portfolio/action, VM test, effect adapter, and effect-order files.
  Evidence: The first `npm run check` attempt failed only `lint:namespace-size`; after updating `dev/namespace_size_exceptions.edn`, `npm run check` completed successfully.

## Decision Log

- Decision: Represent trader benchmarks as `trader:<normalized-address>` in `:portfolio-ui :returns-benchmark-coins`.
  Rationale: Existing market benchmarks are raw strings and vault benchmarks are already prefixed strings. A second prefix preserves URL compatibility, avoids schema churn, and allows the same multi-select UI to hold market, vault, and trader sources.
  Date/Author: 2026-05-25 / Codex.
- Decision: Store fetched trader benchmark portfolios under `[:portfolio :trader-benchmarks-by-address <address>]` with keyed loading and error state under `[:portfolio :loading :trader-benchmarks-by-address <address>]` and `[:portfolio :errors :trader-benchmarks-by-address <address>]`.
  Rationale: The active account portfolio uses `[:portfolio :summary-by-key]`. Keyed benchmark state prevents a background trader fetch from replacing the portfolio being inspected.
  Date/Author: 2026-05-25 / Codex.
- Decision: Use the same raw account-value return history model already used for portfolio and vault benchmarks, not cash-flow-adjusted returns.
  Rationale: The existing chart and performance metrics compare cumulative return rows derived from account value history. Cash-flow adjustment requires ledger update fetching and a broader analytics contract, so it should be a separate feature if needed.
  Date/Author: 2026-05-25 / Codex.
- Decision: Add a selector candidate only when the typed search normalizes to a full 42-character `0x` address.
  Rationale: This avoids turning partial market searches into invalid network requests while keeping paste-and-enter behavior predictable.
  Date/Author: 2026-05-25 / Codex.

## Outcomes & Retrospective

Work is complete. This plan increased the number of benchmark source types by one, but kept complexity contained by reusing existing prefix-based selection, URL query preservation, and return-series computation patterns.

Milestone outcome 2026-05-25T17:55Z: Trader benchmark selection, keyed fetching, query preservation, chart/performance computation, effect contracts, and browser design-review coverage are implemented. Complexity increased modestly because benchmark state now has a third source type, but the implementation remains aligned with the existing market/vault benchmark pattern instead of introducing a new selector model.

Final outcome 2026-05-25T18:00Z: Required repository gates passed after namespace-size exceptions were updated for the files expanded by this feature. The feature is covered by RED/green CLJS tests, formal effect-order conformance, the full `npm run check` suite, full `npm test`, websocket tests, and governed browser design review for the portfolio route.

## Context and Orientation

Portfolio benchmark selection is mostly plain data in the application state. The selected benchmark strings live at `[:portfolio-ui :returns-benchmark-coins]`. Market benchmarks use normal coin strings such as `BTC` and fetch candles from websocket candle snapshots. Vault benchmarks use values such as `vault:0xabc...` and fetch vault detail history.

`src/hyperopen/portfolio/actions.cljs` owns selection actions. It normalizes benchmark values, chooses which effects to emit, and already avoids candle fetches for vault-prefixed values. This file should gain equivalent helpers for the `trader:` prefix and a new effect request `[:effects/api-fetch-trader-portfolio-benchmark address]`.

`src/hyperopen/views/portfolio/vm/benchmarks/selector.cljs` owns the benchmark selector model used by the portfolio chart view. It builds market options from `[:asset-selector :markets]`, vault options from `[:vaults :merged-index-rows]`, selected chip options, candidate filtering, `:top-coin`, and labels. This file should add a typed address candidate when `:returns-benchmark-search` is a valid Hyperliquid address and should label selected `trader:` values even when no fetched data exists yet.

`src/hyperopen/views/portfolio/vm/benchmarks/computation.cljs` converts selected benchmark values into cumulative return rows. It already branches between vault details and market candles. This file should add a trader branch that reads `[:portfolio :trader-benchmarks-by-address address :summary-by-key]`, selects the same effective range used by the current portfolio, derives return rows with `portfolio-metrics/returns-history-rows`, and aligns those rows to the current portfolio's strategy time points.

`src/hyperopen/api/endpoints/account/portfolio.cljs` already exposes `request-portfolio!`, which calls Hyperliquid's `{"type":"portfolio","user": address}` endpoint and normalizes the response. The new benchmark effect should call this API but write to keyed benchmark state, not the active account portfolio state.

`src/hyperopen/api/projections/portfolio.cljs` currently has `begin-portfolio-load`, `apply-portfolio-success`, and `apply-portfolio-error` for the active portfolio. It should gain keyed trader benchmark projection helpers so runtime effects can update benchmark loading, success, and error state consistently.

Runtime effect registration is split across `src/hyperopen/runtime/effect_adapters.cljs`, `src/hyperopen/schema/runtime_registration/portfolio.cljs`, and `src/hyperopen/schema/contracts/effect_args.cljs`. The new effect name must be registered in all relevant places so the runtime can dispatch it and the contract tests understand its argument shape.

The relevant tests are `test/hyperopen/portfolio/benchmark_actions_test.cljs`, `test/hyperopen/views/portfolio/vm/benchmarks_test.cljs`, and any runtime/projection contract tests discovered while implementing. The project test runner is generated by `npm run test:runner:generate` and then executed with `npm test`.

## Plan of Work

First, add failing tests that describe the new public behavior. In `test/hyperopen/portfolio/benchmark_actions_test.cljs`, add coverage showing that `trader:0xABC...` normalizes to a lowercase address, selected trader benchmark addresses are deduped, market candle fetches are not emitted for trader values, and selecting a new trader benchmark emits `[:effects/api-fetch-trader-portfolio-benchmark "0x..."]` only when keyed benchmark data is missing. Add search-keydown coverage that pressing Enter on a `trader:` top candidate selects it and emits the same effect.

Second, add selector VM tests in `test/hyperopen/views/portfolio/vm/benchmarks_test.cljs`. The test should build a minimal portfolio state with `:returns-benchmark-search` set to a valid address and assert that the selector candidate list contains one `trader:` candidate with a `Trader 0x1234...5678` label. It should also assert that selecting an existing `trader:` benchmark produces a selected option and performance metric label even before fetched data exists.

Third, add computation coverage in the VM tests. Create a minimal active portfolio summary with account value history at three timestamps and a trader benchmark summary under `[:portfolio :trader-benchmarks-by-address address :summary-by-key :month]` with matching timestamps. Select `trader:<address>` and assert that the chart has benchmark series id `:benchmark-0`, label `Trader 0x...`, and cumulative return points derived from the trader's account value history.

Fourth, add projection/runtime coverage. The exact test file should follow existing runtime projection test patterns found with `rg "begin-portfolio-load|apply-portfolio-success|effect_args" test src`. The tests should assert that begin/success/error projection functions update only the keyed trader benchmark address, and that the new effect argument contract accepts one address argument.

Fifth, run a focused test command. If the repository does not provide namespace-level CLJS test filtering, run `npm test` and confirm the new tests fail because the production code has not been implemented yet. Record the failure summary in this plan.

Sixth, implement the smallest production changes needed for the tests. Add trader helpers to `src/hyperopen/portfolio/actions.cljs`, update fetch effect composition, add selector address normalization and candidate creation to `src/hyperopen/views/portfolio/vm/benchmarks/selector.cljs`, add the trader summary branch to `src/hyperopen/views/portfolio/vm/benchmarks/computation.cljs`, add keyed projections to `src/hyperopen/api/projections/portfolio.cljs`, add the runtime effect to `src/hyperopen/runtime/effect_adapters.cljs`, and register the effect in schema/runtime files.

Seventh, run the focused tests again until green, then run required gates: `npm run check`, `npm test`, and `npm run test:websocket`. Because the implementation touches portfolio selector/view-model behavior, inspect `docs/BROWSER_TESTING.md` and related UI guidance, then either run the smallest relevant browser verification or document why the change is covered by deterministic VM tests and does not alter rendered layout or browser tooling.

## Concrete Steps

All commands run from `/Users/barry/.codex/worktrees/fec3/hyperopen`.

1. Write the RED tests in the relevant `test/hyperopen/**` files and this plan.
2. Run `npm test` or the smallest available equivalent. Expected before implementation: failing assertions or missing-var errors for trader benchmark helpers/effects.
3. Implement production code using `apply_patch`, preserving existing market and vault behavior.
4. Run `npm test`. Expected after implementation: all CLJS tests pass.
5. Run `npm run check`. Expected: documentation, namespace, lint, compile, and auxiliary checks pass.
6. Run `npm run test:websocket`. Expected: websocket test target compiles and `node out/ws-test.js` passes.
7. If browser QA is needed, run the smallest command prescribed by `docs/BROWSER_TESTING.md` and stop browser sessions with `npm run browser:cleanup`.

## Validation and Acceptance

Acceptance criteria:

- Typing a full Hyperliquid address into the returns benchmark selector creates a candidate with value `trader:<lowercase-address>` and label `Trader 0x1234...5678`.
- Selecting that candidate saves it in `:portfolio-ui :returns-benchmark-coins`, preserves URL query participation through the existing benchmark query state, closes the selector, clears search text, and emits `:effects/api-fetch-trader-portfolio-benchmark` when the benchmark portfolio is not cached or already loading.
- Market candle fetch effects are never emitted for `trader:` values.
- The runtime effect fetches `request-portfolio!` for the benchmark address and writes the response under `[:portfolio :trader-benchmarks-by-address address]`; it does not mutate `[:portfolio :summary-by-key]`.
- When trader benchmark data exists, the returns chart and performance metrics include the trader benchmark using cumulative return rows derived from that trader's account value history.
- Existing market and vault benchmark tests continue to pass.
- Required gates pass: `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

All code changes are additive or narrow edits to existing pure functions and effect registration. Re-running tests is safe. Re-running a trader benchmark fetch should overwrite only that trader's keyed benchmark cache and loading/error slots. If a command fails partway through, inspect the failure, update this plan's `Surprises & Discoveries` or `Decision Log` if the implementation direction changes, and retry the same command after fixing the cause.

No destructive database migration, local storage migration, or remote write is required. Do not run `git pull --rebase` or `git push` as part of this plan.

## Artifacts and Notes

Current known relevant files:

- `src/hyperopen/portfolio/actions.cljs`
- `src/hyperopen/views/portfolio/vm/benchmarks/selector.cljs`
- `src/hyperopen/views/portfolio/vm/benchmarks/computation.cljs`
- `src/hyperopen/api/projections/portfolio.cljs`
- `src/hyperopen/runtime/effect_adapters.cljs`
- `src/hyperopen/schema/runtime_registration/portfolio.cljs`
- `src/hyperopen/schema/contracts/effect_args.cljs`
- `test/hyperopen/portfolio/benchmark_actions_test.cljs`
- `test/hyperopen/views/portfolio/vm/benchmarks_test.cljs`
- `test/hyperopen/schema/contracts/effect_args_test.cljs`

The active plan was created before source or test edits in this continuation.

## Interfaces and Dependencies

At completion, these interfaces should exist:

- `hyperopen.portfolio.actions/trader-benchmark-prefix`, or a private constant if no external test needs direct access, with value `"trader:"`.
- `hyperopen.portfolio.actions/trader-benchmark-address`, accepting a benchmark value and returning a normalized lowercase address for `trader:` values or nil for other inputs.
- `hyperopen.portfolio.actions/trader-benchmark-value`, accepting an address and returning `trader:<lowercase-address>` for valid addresses.
- `hyperopen.portfolio.actions/selected-portfolio-trader-benchmark-addresses`, returning deduped selected trader benchmark addresses.
- A runtime effect vector `[:effects/api-fetch-trader-portfolio-benchmark address]`.
- Projection functions in `hyperopen.api.projections.portfolio` for beginning, succeeding, and failing a keyed trader benchmark portfolio load.
- Selector helpers in `hyperopen.views.portfolio.vm.benchmarks.selector` that format trader benchmark labels as `Trader 0x1234...5678`.

Revision note 2026-05-25T17:37Z: Initial active ExecPlan created from the user's direct continuation request. The plan chooses a prefix-based trader benchmark value and keyed portfolio cache to preserve current market and vault benchmark behavior while adding the new source type.

Revision note 2026-05-25T17:42Z: Updated progress and discoveries after writing RED tests and running `npm test`. The plan now records both the expected missing trader behavior and the separate lucide module-resolution issue observed after compile.

Revision note 2026-05-25T17:55Z: Updated progress, discoveries, and outcomes after implementation, formal vector sync, successful `npm test`, and governed browser design review. Required repository gates still remain before the plan can be closed.

Revision note 2026-05-25T18:00Z: Updated progress, discoveries, and outcomes after `npm run check`, `npm test`, and `npm run test:websocket` all completed successfully.
