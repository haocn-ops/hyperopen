# Positions Tab API and Canonical View-Model Refactor

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Tracked `bd` issue: `hyperopen-xuj0` (closed: Completed).

## Purpose / Big Picture

The positions tab currently mixes data normalization, filter/sort query logic, overlay coordination, and rendering in one namespace. It also exposes an ambiguous multi-arity API (`positions-tab-content`) that infers argument meaning from runtime shape checks. After this refactor, callers will use explicit and unambiguous entry points, row semantics will flow through one canonical row view-model, and key correctness issues (margin-mode boolean parsing, sort/display mismatches, and empty-state messaging) will be fixed.

Users should observe the same core table/card behavior with clearer and more trustworthy semantics: margin mode detection must preserve explicit `false`, sort order must match what the user sees, and empty states must distinguish between no positions and no filter/search matches.

## Progress

- [x] (2026-03-12 01:23Z) Claimed `bd` issue `hyperopen-xuj0` and gathered architecture/testing context for positions-tab refactor.
- [x] (2026-03-12 01:24Z) Drafted active ExecPlan with milestones, acceptance criteria, and scope boundaries.
- [x] (2026-03-12 01:32Z) Implemented `/hyperopen/src/hyperopen/views/account_info/positions_vm.cljs` canonical row view-model and moved business/schema derivation concerns into it.
- [x] (2026-03-12 01:34Z) Replaced overloaded `positions-tab-content` multi-arity API with explicit options-map entry points and updated account view/tests/callers.
- [x] (2026-03-12 01:36Z) Removed view-level sort cache, aligned sort semantics with displayed values, and added no-data vs no-matches empty-state behavior.
- [x] (2026-03-12 01:44Z) Ran required validation gates (`npm run check`, `npm test`, `npm run test:websocket`) and completed manual QA for desktop sorting/search empty states plus mobile overlay presentation.

## Surprises & Discoveries

- Observation: existing tests heavily rely on overloaded `positions-tab-content` arities and direct access to `reset-positions-sort-cache!`, so API cleanup requires broad fixture/test updates.
  Evidence: `/hyperopen/test/hyperopen/views/account_info/tabs/positions_test.cljs` includes many direct positional arity calls and cache-reset fixtures.

- Observation: `positions.cljs` already contains partial extraction to shared/projections/sort helpers, so the safest path is incremental extraction of row derivation and query semantics rather than a full renderer rewrite.
  Evidence: Existing dependencies include `/hyperopen/src/hyperopen/views/account_info/projections.cljs`, `shared.cljs`, `sort_kernel.cljs`, `table.cljs`, and `mobile_cards.cljs`.

- Observation: portfolio scenes compile surfaced stale `positions-tab-content` arity usage after the API cleanup.
  Evidence: `npm run check` initially emitted `Wrong number of args (6)` warnings in `/hyperopen/portfolio/hyperopen/workbench/scenes/account/positions_scenes.cljs`.

- Observation: `sort-position-rows-by-column` initially threaded arguments in the wrong order and returned malformed sort output in tests.
  Evidence: first `npm test` run after refactor showed `mapv ... [:position :coin]` resolving to repeated `nil` rows until argument order was corrected.

## Decision Log

- Decision: Use explicit entry points (`positions-tab-content-from-rows`, `positions-tab-content-from-webdata`) and a single map-arg dispatcher (`positions-tab-content`) instead of retaining ambiguous multi-arity overloads.
  Rationale: This preserves convenience while removing semantic ambiguity and runtime shape guessing.
  Date/Author: 2026-03-12 / Codex

- Decision: Keep current string column ids for this refactor wave.
  Rationale: Sort action contracts currently validate string args (`::sort-column-args`), so converting ids to keywords would require cross-cutting runtime/schema changes outside this scoped refactor.
  Date/Author: 2026-03-12 / Codex

- Decision: Remove mutable per-view sorted-rows cache and compute deterministic filtered/sorted rows from canonical row view-models each render.
  Rationale: Hidden mutable cache state in a view namespace increases coupling and interpretive complexity; correctness and comprehensibility are higher priority in this cleanup wave.
  Date/Author: 2026-03-12 / Codex

- Decision: Keep `reset-positions-sort-cache!` as a no-op compatibility shim.
  Rationale: Existing tests and exports reference it, and retaining a stable callable surface avoids unnecessary breakage while still removing hidden mutable cache state.
  Date/Author: 2026-03-12 / Codex

## Outcomes & Retrospective

Implemented outcomes:

- Added `/hyperopen/src/hyperopen/views/account_info/positions_vm.cljs` with canonical `position-row-vm` derivation, mark/margin/funding/TP-SL schema normalization, and centralized filter/sort logic.
- Refactored `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs` to:
  - render desktop/mobile rows from canonical row VMs,
  - remove mutable `sorted-positions-cache`,
  - switch to explicit map options API for `positions-tab-content-from-rows`, `positions-tab-content-from-webdata`, and `positions-tab-content`,
  - distinguish empty states (`No active positions` vs `No matching positions`).
- Updated call sites in `/hyperopen/src/hyperopen/views/account_info_view.cljs` and portfolio scenes in `/hyperopen/portfolio/hyperopen/workbench/scenes/account/positions_scenes.cljs`.
- Updated and expanded tests in:
  - `/hyperopen/test/hyperopen/views/account_info/tabs/positions_test.cljs`
  - `/hyperopen/test/hyperopen/views/account_info/table_contract_test.cljs`

Validation and QA evidence:

- `npm run check` passed (including app/portfolio/test compiles with zero warnings).
- `npm test` passed (`Ran 2299 tests containing 11975 assertions. 0 failures, 0 errors.`).
- `npm run test:websocket` passed (`Ran 376 tests containing 2145 assertions. 0 failures, 0 errors.`).
- Manual QA (browser debug session on local trade route) confirmed:
  - desktop positions table sorting responds correctly for `Size` and `PNL (ROE %)`,
  - coin search with unmatched query renders `No matching positions`,
  - mobile account surface positions tab opens and margin/reduce/TP-SL overlays present as `mobile-sheet`.

Complexity impact:

- Overall complexity was reduced. Business/schema interpretation and query semantics were removed from the render path into a dedicated VM module, and ambiguous multi-arity API behavior was replaced with explicit options-map entry points.

## Context and Orientation

The current file `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs` is roughly 1,000 lines and performs:

- data/schema normalization (mark price aliases, margin mode aliases, TP/SL trigger aliases, funding sign conventions),
- query/model operations (direction filter, coin search, sort accessors),
- overlay trigger/anchor logic for desktop/mobile,
- desktop and mobile rendering.

A canonical row view-model does not currently exist. Some helpers read raw rows, some read nested `:position`, and some support both. This creates duplication and regression risk.

Relevant integration points:

- Primary view orchestrator: `/hyperopen/src/hyperopen/views/account_info_view.cljs`
- Sort kernel used by tab views: `/hyperopen/src/hyperopen/views/account_info/sort_kernel.cljs`
- Shared rendering/format helpers: `/hyperopen/src/hyperopen/views/account_info/shared.cljs`
- Position source projection: `/hyperopen/src/hyperopen/views/account_info/projections/balances.cljs` via `collect-positions`
- Primary tests: `/hyperopen/test/hyperopen/views/account_info/tabs/positions_test.cljs`

## Plan of Work

### Milestone 1: Canonical row view-model extraction and correctness fixes

Create a dedicated positions row view-model namespace under `/hyperopen/src/hyperopen/views/account_info/` and move non-render business/schema logic there:

- mark price resolution (without entry-price masquerading as mark),
- margin mode normalization (preserve explicit boolean false),
- side derivation and display coin/dex labels,
- TP/SL trigger resolution,
- funding display and tooltip data,
- normalized sort/search keys.

Define one canonical row VM shape that includes both original row (`:row-data`) and derived fields required by desktop/mobile rendering.

### Milestone 2: API cleanup and rendering wiring

Refactor positions tab entry points to remove overloaded runtime arity/shape interpretation:

- `positions-tab-content-from-rows` takes one options map.
- `positions-tab-content-from-webdata` takes one options map.
- `positions-tab-content` takes one options map and dispatches explicitly.

Update `/hyperopen/src/hyperopen/views/account_info_view.cljs` and all tests/callers to use the explicit map contract.

### Milestone 3: Query semantics alignment and cache removal

Update filtering/sorting pipeline to operate on canonical VMs and align sort semantics with displayed values:

- sort `Size` by absolute size (as displayed),
- sort `Coin` by displayed/base label (not raw namespaced coin only),
- sort `PNL (ROE %)` by PNL then ROE tie-breaker,
- keep Funding sort on display-sign convention.

Remove mutable `sorted-positions-cache` and `memoized-sorted-positions` usage from the view path. Preserve public `reset-positions-sort-cache!` as a no-op compatibility shim only if needed by callers/tests.

Update empty-state behavior:

- no source rows -> `No active positions`,
- source rows exist but filters/search yield none -> `No matching positions`.

## Concrete Steps

Work from `/hyperopen` repository root.

1. Add new positions VM namespace and wire `positions.cljs` to consume it.
2. Replace overloaded positions tab APIs with explicit options-map entry points.
3. Update all callers and tests to the new API.
4. Adjust/expand tests for:
   - margin-mode false preservation,
   - sort semantics alignment,
   - empty-state distinction.
5. Run validation and manual QA.

Commands:

    npm run check
    npm test
    npm run test:websocket

Manual QA path:

- Start local app and open account info positions tab in desktop and mobile widths.
- Verify sorting behavior for `Coin`, `Size`, and `PNL (ROE %)` matches visible values.
- Verify empty-state messaging differs for no positions vs filtered-out positions.
- Verify Margin edit affordance remains hidden for cross margin and visible for isolated.
- Verify opening TP/SL, Reduce, and Margin overlays still dispatches correct actions and anchors.

## Validation and Acceptance

Acceptance requires:

- Unambiguous positions tab API: no multi-arity runtime argument guessing in `positions-tab-content`.
- Canonical row VM used by both desktop row and mobile card rendering.
- `position-margin-mode` preserves explicit `false` and correctly labels isolated mode.
- Sort semantics for size/coin/pnl align with displayed values.
- Empty states distinguish no data vs no matches.
- Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.
- Manual QA confirms desktop/mobile interactions and overlay triggers still work.

## Idempotence and Recovery

This refactor is safe to run incrementally. If any milestone introduces regressions, keep the new tests and revert only the affected function wiring while preserving API clarity changes. Re-running test commands is idempotent.

If manual QA finds overlay regressions, prefer restoring prior trigger wiring first, then re-introduce VM changes in smaller slices.

## Artifacts and Notes

Artifacts to capture during execution:

- Focused test diffs and new/updated assertions in positions tests.
- Validation output summaries for all required gates.
- Brief manual QA notes with viewport/context and observed behavior.

## Interfaces and Dependencies

Interfaces that must remain available after this refactor:

- `/hyperopen/src/hyperopen/views/account_info/tabs/positions.cljs`
  - `calculate-mark-price`
  - `format-position-size`
  - `position-unique-key`
  - `collect-positions`
  - `position-row`
  - `sort-positions-by-column`
  - `position-table-header`
  - `positions-tab-content`
  - `reset-positions-sort-cache!` (compatibility no-op if cache removed)

Expected new internal interface:

- New VM namespace function(s) returning canonical row maps, for example:
  `position-row-vm` and `position-row-vms`.

Callers in `/hyperopen/src/hyperopen/views/account_info_view.cljs` and tests must move to explicit options-map call style for positions-tab rendering.

---

Plan revision note (2026-03-12 / Codex): Initial plan created to execute `hyperopen-xuj0` with explicit API cleanup, canonical row VM extraction, correctness fixes, and mandatory validation/QA gates.

Plan revision note (2026-03-12 / Codex): Completed implementation, validation, and manual QA details were recorded; plan is ready to move from active to completed.
