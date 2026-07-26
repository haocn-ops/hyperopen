# Mobile account surface Hyperliquid parity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`.

Tracking issue: `hyperopen-mge`.

## Purpose / Big Picture

After this change, mobile `/trade` should behave like three distinct footer-selected surfaces instead of one route that still leaks account content into the market and ticket views. On a phone viewport, `Account` must become the place where the user sees either the unified account summary or the classic account summary, plus the `Deposit`, `Perps ↔ Spot`, and `Withdraw` actions. The mobile `Trade` surface should stop rendering those summary metrics and funding actions so the order ticket has more vertical room.

The visible proof is straightforward. On an iPhone-sized viewport in local Hyperopen, tapping `Markets` should show market content without the account panel stacked underneath, tapping `Trade` should show the order form without the summary/action block below it, and tapping `Account` should show the summary card, the funding-action row, and the account tables in one account-owned surface that is materially closer to the provided Hyperliquid screenshot.

## Progress

- [x] (2026-03-09 01:45Z) Re-read `/hyperopen/ARCHITECTURE.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, `/hyperopen/docs/agent-guides/trading-ui-policy.md`, `/hyperopen/.agents/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md`.
- [x] (2026-03-09 01:53Z) Created and claimed `bd` feature issue `hyperopen-mge` for this parity wave.
- [x] (2026-03-09 01:56Z) Audited the current ownership split across `/hyperopen/src/hyperopen/views/trade_view.cljs`, `/hyperopen/src/hyperopen/views/account_equity_view.cljs`, `/hyperopen/src/hyperopen/views/account_info_view.cljs`, `/hyperopen/src/hyperopen/views/footer_view.cljs`, and existing mobile parity tests.
- [x] (2026-03-09 01:58Z) Authored this active ExecPlan before code edits.
- [x] (2026-03-09 01:59Z) Implemented the mobile account-surface recomposition in `/hyperopen/src/hyperopen/views/trade_view.cljs` and factored reusable funding actions from `/hyperopen/src/hyperopen/views/account_equity_view.cljs`.
- [x] (2026-03-09 02:00Z) Updated regression coverage in `/hyperopen/test/hyperopen/views/account_equity_view_test.cljs` and `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`.
- [x] (2026-03-09 02:01Z) Ran `npm test` and got a green full suite (`2044` tests, `10658` assertions).
- [x] (2026-03-09 02:01Z) Ran `npm run check` successfully.
- [x] (2026-03-09 02:01Z) Ran `npm run test:websocket` successfully (`339` tests, `1852` assertions).
- [x] (2026-03-09 02:07Z) Completed live iPhone QA on the local `/trade` route, including non-spectate action-state screenshots and spectate-mode unified/classic summary verification under `/hyperopen/tmp/browser-inspection/manual-mobile-account-surface-parity-2026-03-09T02-04-37Z/`.
- [x] (2026-03-09 02:08Z) Wrote `/hyperopen/docs/qa/mobile-account-surface-parity-qa-2026-03-09.md`, closed `hyperopen-mge`, and prepared this plan for move to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: the current small-viewport `Account` footer target only owns the account-history tables, while the account summary and funding actions still live under the `Trade` ticket surface.
  Evidence: `/hyperopen/src/hyperopen/views/trade_view.cljs` renders `account-equity-view/account-equity-view` inside `trade-order-entry-panel` and renders `account-info-view/account-info-view` inside `trade-account-tables-panel`.

- Observation: the current small-viewport `Markets` surfaces still render the account tables beneath chart/order book/trades, which makes the footer navigation behave more like a filter than true page ownership.
  Evidence: `/hyperopen/src/hyperopen/views/trade_view.cljs` shows `trade-account-tables-panel` whenever `mobile-market-surface?` is true.

- Observation: the current account-history panel is hard-coded to `h-96`, which is appropriate when it is a secondary lower panel but may need a caller override when it becomes part of a first-class mobile account surface.
  Evidence: `/hyperopen/src/hyperopen/views/account_info_view.cljs` root classes include `h-96`.

- Observation: the stock browser-inspection `inspect` command always re-navigates, which resets the selected mobile surface back to the default chart state for `/trade`.
  Evidence: the initial `inspect` runs at `/hyperopen/tmp/browser-inspection/inspect-2026-03-09T02-02-20-427Z-a448e107/` and `/hyperopen/tmp/browser-inspection/inspect-2026-03-09T02-02-42-503Z-6142f428/` both produced chart screenshots even after dispatching `:actions/select-trade-mobile-surface`.

## Decision Log

- Decision: On mobile `/trade`, treat `Markets`, `Trade`, and `Account` as distinct owned surfaces.
  Rationale: This is the cleanest way to satisfy the user request for moving summary/actions to `Account`, improving trade visibility, and matching the footer-navigation mental model shown in the reference screenshot.
  Date/Author: 2026-03-09 / Codex

- Decision: Keep desktop composition unchanged and scope the ownership change to small viewports only.
  Rationale: The request is explicitly about mobile parity and mobile trade visibility; the current desktop split already uses the right rail and lower account panel appropriately.
  Date/Author: 2026-03-09 / Codex

- Decision: Reuse the existing account-equity metrics logic and account-info tabs, but factor them so the mobile account surface composes them differently instead of duplicating UI logic.
  Rationale: The summary semantics for unified versus classic mode already exist and are covered by tests. Recomposition is safer than building a parallel account-summary implementation.
  Date/Author: 2026-03-09 / Codex

- Decision: Use a direct CDP screenshot capture for the live selected mobile surface during QA instead of relying only on the higher-level `inspect` command.
  Rationale: QA needed screenshots of the already-selected `Markets`, `Trade`, and `Account` states; direct capture preserved the in-session mobile surface and avoided false chart resets.
  Date/Author: 2026-03-09 / Codex

## Outcomes & Retrospective

Implementation completed.

The mobile `/trade` route now behaves like three distinct footer-owned surfaces. `Markets` no longer drags the account tables underneath the chart surface. `Trade` no longer renders the account summary or funding-action row below the ticket on small screens. `Account` now owns the summary card, the funding actions, and the shared account tabs in one vertical surface.

The summary/action logic stayed centralized instead of forking. `/hyperopen/src/hyperopen/views/account_equity_view.cljs` now exposes a reusable funding-action block and supports rendering the summary without inline actions, which made it possible to move those controls into the account-owned surface while leaving desktop behavior intact.

Validation and QA both passed. Automated coverage remained green across the full JS suite, `npm run check`, and websocket tests. Live QA on the iPhone-sized viewport verified the non-spectate account surface with buttons plus the unified/classic summary variants in spectate mode using the two known reference addresses.

## Context and Orientation

This task touches the `/trade` route only, but it uses shared account view modules.

The mobile route shell is composed in `/hyperopen/src/hyperopen/views/trade_view.cljs`. That file decides which surface is visible for the current phone viewport and currently still mixes account-owned content into both the `Trade` and `Markets` experiences.

The footer navigation lives in `/hyperopen/src/hyperopen/views/footer_view.cljs`. It already exposes the three relevant actions: `Markets`, `Trade`, and `Account`. No new action is expected; the work is mostly about what those actions own visually.

The account summary card lives in `/hyperopen/src/hyperopen/views/account_equity_view.cljs`. That namespace already knows how to derive unified versus classic metrics and already renders the `Deposit`, `Perps <-> Spot`, and `Withdraw` controls. It currently assumes those actions are embedded inside the summary block.

The shared account-history tabs live in `/hyperopen/src/hyperopen/views/account_info_view.cljs`. That panel is reused by `/trade` and `/portfolio`. Any layout override added here must preserve current `/portfolio` and desktop `/trade` behavior.

The current mobile surface state is normalized in `/hyperopen/src/hyperopen/trade/layout_actions.cljs`. That file already supports `:chart`, `:orderbook`, `:trades`, `:ticket`, and `:account`; the current task should not need new surface ids.

Relevant regression coverage already exists in:

- `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`
- `/hyperopen/test/hyperopen/views/account_equity_view_test.cljs`
- `/hyperopen/test/hyperopen/views/account_info_view_test.cljs`
- `/hyperopen/test/hyperopen/views/footer_view_test.cljs`

The browser QA tooling for live iPhone validation lives under `/hyperopen/tools/browser-inspection/`. Artifact directories are written under `/hyperopen/tmp/browser-inspection/`, and human-readable QA notes are stored in `/hyperopen/docs/qa/`.

## Plan of Work

Start by refactoring the account summary module so it can render the summary metrics with or without inline funding actions. Export a small reusable funding-actions block from `/hyperopen/src/hyperopen/views/account_equity_view.cljs` instead of forcing every caller through the current embedded layout. Preserve the existing default behavior for current callers until the new trade composition is wired.

Next, add a mobile-account surface composition layer for `/trade`. The simplest safe shape is a new view helper that stacks three pieces in this order for mobile `:account`: the summary card, the funding-action block, and the shared account-history panel. This helper should be used only on mobile `:account`; desktop should continue using the current grid layout.

Then update `/hyperopen/src/hyperopen/views/trade_view.cljs` so mobile `:ticket` no longer renders the account summary below the order form, and mobile market surfaces no longer render the account-history panel underneath the market content. The account-history panel should render on mobile only when `mobile-surface` is `:account`. Preserve the existing desktop behavior by keeping the `lg:` layout classes and desktop panel mounting intact.

If the shared account-history panel needs more flexible sizing for the new mobile surface, add an option in `/hyperopen/src/hyperopen/views/account_info_view.cljs` that lets callers override the root classes or height policy. Keep the current no-options behavior identical so `/portfolio` and desktop `/trade` stay stable.

After the layout changes, update tests to lock the new ownership boundaries. The trade/mobile tests must assert that market surfaces no longer contain account tables, that the mobile ticket no longer contains the account summary, and that the mobile account surface now contains both summary and tables. The account-equity tests must assert that the summary can render without inline actions while the reusable action block still wires the same funding actions. Any new account-info panel option should get a focused test.

Finish by running the required repo gates and a live iPhone QA pass using browser-inspection against the local app. Capture screenshots showing the mobile `Trade` surface without the summary block and the mobile `Account` surface with summary plus actions. Write a QA note under `/hyperopen/docs/qa/` with the artifact paths and the final parity/regression assessment.

## Concrete Steps

Run commands from `/Users/barry/.codex/worktrees/6717/hyperopen`.

1. Implement the summary/action factoring and trade mobile composition changes.

   rg -n "account-equity-view|trade-account-tables-panel|mobile-market-surface|funding-action" \
     src/hyperopen/views/trade_view.cljs \
     src/hyperopen/views/account_equity_view.cljs \
     src/hyperopen/views/account_info_view.cljs

   # edit the affected view files and, if needed, add a dedicated mobile account surface helper

2. Run focused tests while iterating.

   npm test -- \
     test/hyperopen/views/account_equity_view_test.cljs \
     test/hyperopen/views/account_info_view_test.cljs \
     test/hyperopen/views/footer_view_test.cljs \
     test/hyperopen/views/app_shell_spacing_test.cljs

   Expected: the updated mobile-surface ownership tests pass and no existing footer/account summary tests regress.

3. Run the required gates.

   npm run check
   npm test
   npm run test:websocket

4. Run manual iPhone QA with browser-inspection.

   Start a browser-inspection session with local app management, navigate to `http://localhost:8080/trade` (or the served fallback port if `8080` is busy), switch to the mobile `Trade` and `Account` surfaces, and capture screenshots plus a small JSON summary under `/hyperopen/tmp/browser-inspection/`.

5. Write a QA note in `/hyperopen/docs/qa/` and close the tracking issue.

   bd close hyperopen-mge --reason "Completed" --json

   If QA finds a residual issue, create a linked follow-up with `--deps discovered-from:hyperopen-mge` before closing or leave `hyperopen-mge` open and record the blocker here.

## Validation and Acceptance

Acceptance is satisfied when all of the following are true:

- On mobile `/trade`, the `Trade` footer surface renders the order form without the account summary metrics and without the deposit/transfer/withdraw action block.
- On mobile `/trade`, the `Markets` footer surface no longer renders the account-history tables beneath the market content.
- On mobile `/trade`, the `Account` footer surface renders the correct summary variant for the current account mode (`Unified Account Summary` or classic `Account Equity`/`Account Value`) plus `Deposit`, `Perps <-> Spot`, and `Withdraw`.
- The account-history tabs remain accessible from the mobile `Account` surface and continue to render on desktop `/trade` and `/portfolio`.
- `npm run check`, `npm test`, and `npm run test:websocket` pass.
- Manual iPhone QA artifacts and a QA note exist and confirm the intended visual ownership with no obvious mobile regression.

## Idempotence and Recovery

These edits are all local UI composition changes and are safe to re-run. Re-running the tests and browser-inspection captures produces new results without mutating external state beyond fresh artifact directories.

If the new account surface causes sizing regressions, prefer adjusting the new caller-level layout or adding a narrowly scoped account-info panel option instead of changing portfolio layout behavior. If the funding action extraction breaks action wiring, compare the rendered `:on` vectors in view tests and restore the original anchor-aware action signatures.

## Artifacts and Notes

Artifacts captured from this plan:

- Updated view modules under `/hyperopen/src/hyperopen/views/`
- Updated regression tests under `/hyperopen/test/hyperopen/views/`
- Browser QA directory:
  `/hyperopen/tmp/browser-inspection/manual-mobile-account-surface-parity-2026-03-09T02-04-37Z/`
- Key screenshots:
  - `/hyperopen/tmp/browser-inspection/manual-mobile-account-surface-parity-2026-03-09T02-04-37Z/markets-surface.png`
  - `/hyperopen/tmp/browser-inspection/manual-mobile-account-surface-parity-2026-03-09T02-04-37Z/trade-surface.png`
  - `/hyperopen/tmp/browser-inspection/manual-mobile-account-surface-parity-2026-03-09T02-04-37Z/account-surface.png`
  - `/hyperopen/tmp/browser-inspection/manual-mobile-account-surface-parity-2026-03-09T02-04-37Z/spectate-unified-account.png`
  - `/hyperopen/tmp/browser-inspection/manual-mobile-account-surface-parity-2026-03-09T02-04-37Z/spectate-classic-account.png`
- Machine-readable QA summaries:
  - `/hyperopen/tmp/browser-inspection/manual-mobile-account-surface-parity-2026-03-09T02-04-37Z/summary.json`
  - `/hyperopen/tmp/browser-inspection/manual-mobile-account-surface-parity-2026-03-09T02-04-37Z/spectate-summary.json`
- QA note:
  `/hyperopen/docs/qa/mobile-account-surface-parity-qa-2026-03-09.md`

Issue-tracking artifact:

- `hyperopen-mge`

## Interfaces and Dependencies

Stable interfaces that should remain unchanged:

- `hyperopen.trade.layout-actions/normalize-trade-mobile-surface`
- `hyperopen.trade.layout-actions/select-trade-mobile-surface`
- existing funding action vectors:
  - `[:actions/open-funding-deposit-modal :event.currentTarget/bounds]`
  - `[:actions/open-funding-transfer-modal :event.currentTarget/bounds]`
  - `[:actions/open-funding-withdraw-modal :event.currentTarget/bounds]`

Any new helper introduced for the mobile account surface should remain a view-level composition helper only. It must not add new websocket, fetch, or reducer ownership. Account-mode semantics must continue to derive from the existing `:account :mode` state and the current logic in `/hyperopen/src/hyperopen/views/account_equity_view.cljs`.

Plan revision note: 2026-03-09 01:58Z - Initial plan created after repo-policy review, current-layout audit, and `bd` issue creation/claim for mobile account-surface parity.
Plan revision note: 2026-03-09 02:07Z - Updated progress, discoveries, decisions, and artifacts after implementation, full validation, and live mobile QA.
Plan revision note: 2026-03-09 02:08Z - Added the final QA note path, closed `hyperopen-mge`, and marked the plan complete before moving it to `completed`.
