# Refactor account history actions into bounded modules

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Related `bd` tracking: epic `hyperopen-s3t`, implementation task `hyperopen-s3t.1`.

## Purpose / Big Picture

After this change, account history behavior will be easier to extend without reopening one large namespace that mixes unrelated concerns. A contributor will be able to change funding-history filters, order-history pagination, open-orders and balances filters, or position overlay workflows in focused files with narrower reasons to change. The user-visible behavior must stay the same: the account info tabs still load, restore persisted table preferences, sort and paginate the same way, and the TP/SL, Reduce, and Margin overlays still open and submit exactly as before.

The result is observable in two ways. First, `/hyperopen/src/hyperopen/account/history/actions.cljs` becomes a thin compatibility layer instead of the real home for all behavior. Second, the existing test suite for account history actions and runtime bootstrap still passes, proving the public action ids and startup restore flows still behave the same after the extraction.

## Progress

- [x] (2026-03-06 20:22Z) Inspected `/hyperopen/src/hyperopen/account/history/actions.cljs`, its tests, and direct callers in startup, system, runtime collaborators, and effects to map the current responsibilities.
- [x] (2026-03-06 20:22Z) Created `bd` epic `hyperopen-s3t` and claimed child task `hyperopen-s3t.1` for the implementation slice.
- [x] (2026-03-06 20:31Z) Extracted shared helper functions into `/hyperopen/src/hyperopen/account/history/shared.cljs` and moved funding-history actions into `/hyperopen/src/hyperopen/account/history/funding_actions.cljs`.
- [x] (2026-03-06 20:31Z) Moved order-history and trade-history actions into `/hyperopen/src/hyperopen/account/history/order_actions.cljs`.
- [x] (2026-03-06 20:31Z) Moved balances, positions, and open-orders filter and sort actions into `/hyperopen/src/hyperopen/account/history/surface_actions.cljs`.
- [x] (2026-03-06 20:31Z) Moved TP/SL, Reduce, and Margin overlay workflows into `/hyperopen/src/hyperopen/account/history/position_overlay_actions.cljs`.
- [x] (2026-03-06 20:31Z) Reduced `/hyperopen/src/hyperopen/account/history/actions.cljs` to a compatibility facade with thin routing for `select-account-info-tab`.
- [x] (2026-03-06 20:33Z) Updated `/hyperopen/src/hyperopen/system.cljs`, `/hyperopen/src/hyperopen/app/startup.cljs`, and `/hyperopen/src/hyperopen/account/history/effects.cljs` to depend on focused owner modules instead of the monolith.
- [x] (2026-03-06 20:36Z) Regenerated the test runner, restored missing JS dependencies with `npm ci`, and passed `npm run check`, `npm test`, and `npm run test:websocket`.

## Surprises & Discoveries

- Observation: the monolithic namespace is not only the runtime action catalog input. It is also used as a source of default state, startup restore behavior, and selectors consumed by effects.
  Evidence: `/hyperopen/src/hyperopen/system.cljs`, `/hyperopen/src/hyperopen/app/startup.cljs`, and `/hyperopen/src/hyperopen/account/history/effects.cljs` all require `/hyperopen/src/hyperopen/account/history/actions.cljs` directly.

- Observation: `trade-history` is not called out in the review note, but it shares the same pagination, sorting, and coin-search mechanics as `order-history`, so a clean split still has to assign it an owner.
  Evidence: `/hyperopen/src/hyperopen/account/history/actions.cljs` stores the `trade-history` page-size, page, filter-open, direction-filter, and sort logic next to `order-history` logic.

- Observation: the strongest compatibility constraint is not the file path itself. It is the stable public action vars and startup restore function names that other assemblies already depend on.
  Evidence: `/hyperopen/src/hyperopen/runtime/collaborators.cljs`, `/hyperopen/src/hyperopen/core/public_actions.cljs`, and `/hyperopen/src/hyperopen/startup/init.cljs` all expect the current function names.

- Observation: repository validation in this worktree was initially blocked by missing npm dependencies rather than by compile errors in the refactor.
  Evidence: the first `npm run check` stopped because `@noble/secp256k1` was not present in `node_modules`; running `npm ci` restored the locked dependency set and the next `check` passed.

## Decision Log

- Decision: keep `/hyperopen/src/hyperopen/account/history/actions.cljs` as a compatibility facade instead of deleting it in this slice.
  Rationale: the runtime action catalog, tests, and other assemblies already reference its public vars. Keeping a thin facade preserves those contracts while still removing the large multi-domain implementation body from the file.
  Date/Author: 2026-03-06 / Codex

- Decision: assign `trade-history` behavior to the order-history module.
  Rationale: both areas are historical tables with the same pagination, localized integer parsing, coin-search resets, and filter-open patterns. Grouping them avoids a fifth extraction namespace that would be thinner and less coherent than `order_actions`.
  Date/Author: 2026-03-06 / Codex

- Decision: create one shared helper namespace for pagination parsing, localized integer normalization, and tab-search normalization.
  Rationale: these helpers are pure, reused across funding and order history, and should not live in the facade or be duplicated. A small shared namespace supports Single Responsibility Principle and improves agent comprehension because each extracted action namespace can depend on one obvious helper file.
  Date/Author: 2026-03-06 / Codex

- Decision: update internal callers to depend on focused modules where they only need one bounded concern.
  Rationale: if startup, effects, or system keep depending on the facade, the monolithic mental model remains even after extraction. Direct dependencies on focused modules make the new boundaries visible in code, not only in filenames.
  Date/Author: 2026-03-06 / Codex

- Decision: keep `/hyperopen/src/hyperopen/runtime/collaborators.cljs` and `/hyperopen/src/hyperopen/core/public_actions.cljs` on the facade in this slice.
  Rationale: those files are themselves compatibility assemblies for the public runtime action catalog. Leaving them on the facade preserves one stable aggregation seam while the deeper owner modules become explicit everywhere else.
  Date/Author: 2026-03-06 / Codex

## Outcomes & Retrospective

The refactor landed as planned. Funding history, order and trade history, balances and open-orders surfaces, and position overlays now each have focused owner namespaces under `/hyperopen/src/hyperopen/account/history/`, while `/hyperopen/src/hyperopen/account/history/actions.cljs` is reduced to a compatibility facade with one thin router for tab selection. The non-catalog internal callers that only needed one concern now depend on those focused modules directly.

Behavior stayed stable under the existing suite. `npm run check`, `npm test`, and `npm run test:websocket` all passed after restoring the locked npm dependencies with `npm ci`. No follow-up issue is required for the extraction itself; the remaining facade usage in runtime/public action assemblies is intentional and documented in `Decision Log`.

## Context and Orientation

The current implementation lives in `/hyperopen/src/hyperopen/account/history/actions.cljs`. That file contains at least four different kinds of behavior.

Funding-history actions manage funding filter draft state, request ids, time-range normalization, CSV export, and funding-specific pagination and sort state. Order-history actions manage historical order freshness checks, tab selection fetch orchestration, status filters, order pagination, and refresh logic. The same file also owns open-orders, positions, balances, and trade-history sort and filter actions. Finally, it owns three separate position overlay workflows: TP/SL, Reduce, and Margin.

In this repository, a “surface action” means a pure function that receives application state and user input, then returns a vector of effect descriptions such as `[:effects/save ...]` or `[:effects/api-fetch-historical-orders ...]`. These action functions do not perform side effects directly. Startup restore functions are different: they mutate the store atom by reading persisted browser state, and those must stay in the infrastructure boundary described by `/hyperopen/ARCHITECTURE.md`.

The files that currently bind to the monolith are important. `/hyperopen/src/hyperopen/system.cljs` pulls default funding, order, and trade history state constructors from the monolith. `/hyperopen/src/hyperopen/app/startup.cljs` injects restore functions from the monolith into startup dependencies. `/hyperopen/src/hyperopen/account/history/effects.cljs` calls selectors such as `funding-history-filters` and request-id helpers from the monolith. `/hyperopen/src/hyperopen/runtime/collaborators.cljs` and `/hyperopen/src/hyperopen/core/public_actions.cljs` expose the public action vars that must remain stable.

The target structure for this plan is:

- `/hyperopen/src/hyperopen/account/history/shared.cljs` for pure shared helpers such as page-size normalization, page normalization, and common tab-search normalization.
- `/hyperopen/src/hyperopen/account/history/funding_actions.cljs` for funding-history state, restore, filter, sort, pagination, export, and funding tab orchestration.
- `/hyperopen/src/hyperopen/account/history/order_actions.cljs` for order-history and trade-history state, tab orchestration, freshness checks, sort, filter, pagination, and refresh behavior.
- `/hyperopen/src/hyperopen/account/history/surface_actions.cljs` for balances, positions, and open-orders sorts and filters, balance hiding, coin-search routing, and open-orders sort restore.
- `/hyperopen/src/hyperopen/account/history/position_overlay_actions.cljs` for TP/SL, Reduce, and Margin overlay open, close, update, and submit flows.
- `/hyperopen/src/hyperopen/account/history/actions.cljs` as the stable facade that re-exports the existing public vars and retains only any unavoidable cross-module router functions.

## Plan of Work

Start by extracting the pure helpers that are reused across historical tables. Move `normalize-order-history-page-size`, `normalize-order-history-page`, localized search normalization, and any shared option constants into `/hyperopen/src/hyperopen/account/history/shared.cljs`. Keep the helper API small and pure so funding and order modules can depend on it without reintroducing coupling.

Next, move funding-history behavior into `/hyperopen/src/hyperopen/account/history/funding_actions.cljs`. This module will own `default-funding-history-state`, the funding restore function, funding filter selectors, `select-account-info-tab` funding orchestration support, and all funding-specific filter, sort, pagination, and CSV export actions. The funding module will continue to use `/hyperopen/src/hyperopen/domain/funding-history.cljs` and `/hyperopen/src/hyperopen/platform.cljs` for policy and time access.

Then move order and trade table behavior into `/hyperopen/src/hyperopen/account/history/order_actions.cljs`. This module will own `default-order-history-state`, `default-trade-history-state`, order-history request id and freshness helpers, order tab selection orchestration support, order refresh, trade pagination, trade sorting, order sorting, status filtering, and the restore functions for order and trade pagination.

After that, move balances, positions, and open-orders sorts and filters into `/hyperopen/src/hyperopen/account/history/surface_actions.cljs`. This module will own open-orders sort restore, positions and balances sort actions, open-orders sort and filter actions, positions filter actions, `set-hide-small-balances`, and `set-account-info-coin-search`.

Move the TP/SL, Reduce, and Margin overlay workflows into `/hyperopen/src/hyperopen/account/history/position_overlay_actions.cljs`. This module will own the modal and popover open, close, keyboard, field update, preset, and submit functions while continuing to delegate domain calculations to `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs`, `/hyperopen/src/hyperopen/account/history/position_reduce.cljs`, and `/hyperopen/src/hyperopen/account/history/position_margin.cljs`.

Once the new modules exist, rewrite `/hyperopen/src/hyperopen/account/history/actions.cljs` into a facade. Public vars should alias the extracted implementations with `def`, except for any router functions that intentionally combine funding and order behaviors. `select-account-info-tab` may remain in the facade if it only dispatches to funding or order functions based on the tab keyword. If the routing logic stays small and obvious, that still satisfies the goal because the heavy behavior will live in bounded modules.

Finally, move direct callers off the facade where possible. `/hyperopen/src/hyperopen/system.cljs` should depend on `funding_actions` and `order_actions` for default state constructors. `/hyperopen/src/hyperopen/app/startup.cljs` should depend on the restore owner modules. `/hyperopen/src/hyperopen/account/history/effects.cljs` should depend on funding and order modules for the selectors it needs. `/hyperopen/src/hyperopen/runtime/collaborators.cljs` and `/hyperopen/src/hyperopen/core/public_actions.cljs` should either keep the facade intentionally or merge maps from the focused modules so the runtime catalog stays stable while the source ownership becomes explicit.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/b31e/hyperopen`.

1. Create the new action namespaces and move pure helpers first so the later modules can compile against them.

   Expected command pattern:

       rg -n "normalize-order-history-page|normalize-order-history-page-size|set-account-info-coin-search|submit-position-tpsl" src/hyperopen/account/history

2. Rewrite `/hyperopen/src/hyperopen/account/history/actions.cljs` into a facade that re-exports the extracted vars and, if still needed, keeps only a thin `select-account-info-tab` router.

   Expected observation:

       /hyperopen/src/hyperopen/account/history/actions.cljs drops from a large implementation file to a short list of `def` aliases and small router functions.

3. Update focused internal callers.

   Expected command pattern:

       rg -n "account.history.actions" src test

   Expected observation:

       Only intentionally compatibility-oriented files still require the facade.

4. Update and run targeted tests while extracting, then run the required gates.

   Commands:

       npm run check
       npm test
       npm run test:websocket

## Validation and Acceptance

Acceptance is behavioral, not structural. After the refactor:

`npm run check` must succeed from the repository root without new schema or compile errors. `npm test` must pass, including account history action tests, startup init tests, runtime collaborator tests, and any direct module tests added during the refactor. `npm run test:websocket` must also pass to prove the action catalog and runtime registration remain compatible.

The store defaults and startup restore path must remain intact. A human should still be able to start the application, switch between balances, positions, open orders, order history, trade history, and funding history, and observe the same sort, filter, pagination, and overlay behavior as before. The TP/SL, Reduce, and Margin overlays must still open from positions rows, preserve locale-aware inputs, and emit the same submit effects validated by the existing tests.

## Idempotence and Recovery

This refactor is intentionally additive first and subtractive second. Creating the new namespaces and aliasing them from the facade is safe to repeat because each move can be validated by tests before removing duplicated code. If one extraction fails halfway, restore the broken module by re-pointing the facade back to the original local implementation until the new module compiles, then resume the move. Do not change runtime action ids or effect keywords during recovery, because that would turn a structural refactor into a behavior change.

If a direct caller update creates confusion, keep the caller on the facade temporarily and record the reason in `Decision Log` instead of forcing the dependency move. Compatibility is more important than perfect module purity during the first landing.

## Artifacts and Notes

Current evidence gathered before implementation:

    /hyperopen/src/hyperopen/account/history/actions.cljs currently owns funding-history filters, order-history freshness and refresh, trade-history pagination, balances and open-orders surface filters, and all TP/SL, Reduce, and Margin overlay actions.

    /hyperopen/src/hyperopen/system.cljs uses the monolith for default funding, order, and trade history state constructors.

    /hyperopen/src/hyperopen/app/startup.cljs injects restore-open-orders-sort-settings!, restore-funding-history-pagination-settings!, restore-trade-history-pagination-settings!, and restore-order-history-pagination-settings! from the monolith.

    /hyperopen/src/hyperopen/account/history/effects.cljs calls funding-history-filters, funding-history-request-id, and order-history-request-id from the monolith.

## Interfaces and Dependencies

In `/hyperopen/src/hyperopen/account/history/shared.cljs`, define pure helper functions that do not touch browser APIs:

    normalize-order-history-page-size
    normalize-order-history-page
    normalize-account-info-tab
    normalize-coin-search-value

In `/hyperopen/src/hyperopen/account/history/funding_actions.cljs`, define:

    default-funding-history-state
    restore-funding-history-pagination-settings!
    funding-history-filters
    funding-history-request-id
    select-funding-history-tab
    set-funding-history-filters
    toggle-funding-history-filter-open
    toggle-funding-history-filter-coin
    add-funding-history-filter-coin
    handle-funding-history-coin-search-keydown
    reset-funding-history-filter-draft
    apply-funding-history-filters
    view-all-funding-history
    export-funding-history-csv
    sort-funding-history
    set-funding-history-page-size
    set-funding-history-page
    next-funding-history-page
    prev-funding-history-page
    set-funding-history-page-input
    apply-funding-history-page-input
    handle-funding-history-page-input-keydown

In `/hyperopen/src/hyperopen/account/history/order_actions.cljs`, define:

    default-order-history-state
    default-trade-history-state
    restore-order-history-pagination-settings!
    restore-trade-history-pagination-settings!
    order-history-request-id
    select-order-history-tab
    sort-trade-history
    toggle-trade-history-direction-filter-open
    set-trade-history-direction-filter
    sort-order-history
    toggle-order-history-filter-open
    set-order-history-status-filter
    set-order-history-page-size
    set-order-history-page
    next-order-history-page
    prev-order-history-page
    set-order-history-page-input
    apply-order-history-page-input
    handle-order-history-page-input-keydown
    set-trade-history-page-size
    set-trade-history-page
    next-trade-history-page
    prev-trade-history-page
    set-trade-history-page-input
    apply-trade-history-page-input
    handle-trade-history-page-input-keydown
    refresh-order-history

In `/hyperopen/src/hyperopen/account/history/surface_actions.cljs`, define:

    restore-open-orders-sort-settings!
    sort-positions
    sort-balances
    sort-open-orders
    toggle-open-orders-direction-filter-open
    set-open-orders-direction-filter
    toggle-positions-direction-filter-open
    set-positions-direction-filter
    set-hide-small-balances
    set-account-info-coin-search

In `/hyperopen/src/hyperopen/account/history/position_overlay_actions.cljs`, define:

    open-position-tpsl-modal
    close-position-tpsl-modal
    handle-position-tpsl-modal-keydown
    set-position-tpsl-modal-field
    set-position-tpsl-configure-amount
    set-position-tpsl-limit-price
    submit-position-tpsl
    trigger-close-all-positions
    open-position-reduce-popover
    close-position-reduce-popover
    handle-position-reduce-popover-keydown
    set-position-reduce-popover-field
    set-position-reduce-size-percent
    set-position-reduce-limit-price-to-mid
    submit-position-reduce-close
    open-position-margin-modal
    close-position-margin-modal
    handle-position-margin-modal-keydown
    set-position-margin-modal-field
    set-position-margin-amount-percent
    set-position-margin-amount-to-max
    submit-position-margin-update

`/hyperopen/src/hyperopen/account/history/actions.cljs` must continue to expose the existing public vars so `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`, `/hyperopen/src/hyperopen/runtime/collaborators.cljs`, and `/hyperopen/src/hyperopen/core/public_actions.cljs` can remain behaviorally compatible during the migration.

Change note (2026-03-06 20:22Z): Initial ExecPlan created after inspecting the monolithic account history actions namespace, its tests, and its direct callers. The plan fixes the module boundaries up front so implementation can proceed in bounded slices without re-deciding ownership mid-refactor.

Change note (2026-03-06 20:36Z): Updated the plan after implementation. Progress, discoveries, decisions, and outcomes now reflect the extracted namespaces, the intentional remaining facade consumers, and the successful validation run after restoring npm dependencies.
