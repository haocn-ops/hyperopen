# Vault Detail Activity Row Styling Parity (Pass 3)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the individual vault page activity tables (from `Balances` through `Depositors`) should look and scan much closer to Hyperliquid, especially at row level. A user should be able to spot side/direction and positive/negative values immediately through color semantics and row accents, instead of reading plain monochrome rows.

A user can verify this by opening `/vaults/0xdfc24b077bc1425ad1dea75bcb6f8158e10df303`, switching tabs, and confirming that each table uses compact transparent rows, muted headers, and per-cell semantic styling (green/red tones, side highlights, and interactive underlines) comparable to Hyperliquid.

## Progress

- [x] (2026-02-27 03:28Z) Captured fresh tab-by-tab local and remote style evidence with generic DOM extraction and saved artifacts at `/hyperopen/tmp/vault-parity-pass-2026-02-27T03-27-48-859Z-generic/`.
- [x] (2026-02-27 03:28Z) Enumerated remaining styling discrepancies across `balances` through `depositors` from the live captures.
- [x] (2026-02-27 03:30Z) Authored this ExecPlan with an implementation path tied to specific files/functions.
- [x] (2026-02-27 03:36Z) Implemented tab/header/row base parity styling in `/hyperopen/src/hyperopen/views/vault_detail_view.cljs` (transparent active tabs, transparent headers, compact rows, shared row/cell classes).
- [x] (2026-02-27 03:36Z) Implemented per-tab semantic accents (side gradients, side tone classes, signed-value colors, status colors, and underline affordances for interactive values).
- [x] (2026-02-27 03:36Z) Added style-semantics tests in `/hyperopen/test/hyperopen/views/vault_detail_view_test.cljs`.
- [x] (2026-02-27 03:37Z) Ran required validation gates successfully: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-02-27 03:38Z) Finalized plan outcomes and prepared move to completed.

## Surprises & Discoveries

- Observation: Hyperliquid vault activity tabs are not `button` elements in this route state; they are clickable `div/span` nodes with tab styling classes.
  Evidence: `/hyperopen/tmp/vault-parity-pass-2026-02-27T03-27-48-859Z-generic/hyperliquid-remote.json` shows active tab nodes as `tag: "DIV"`.

- Observation: This worktree’s dev server serves SPA content at `/index.html` but route URLs return 404 without a fallback server.
  Evidence: direct `curl` to `/vaults/...` on dev ports returned 404, and route capture succeeded via local fallback server at `127.0.0.1:18080`.

- Observation: Most of the row-level semantic styling in Hyperliquid is on nested inline elements inside cells, not on `td` itself.
  Evidence: remote capture `accentNodes` in `/hyperopen/tmp/vault-parity-pass-2026-02-27T03-27-48-859Z-generic/hyperliquid-remote.json` contains green/red text nodes under coin/side/PNL cells while parent `td` remains white.

## Decision Log

- Decision: Implement parity in this pass by refining vault detail view table rendering classes and nested inline value wrappers, not by introducing a new table system.
  Rationale: This keeps scope contained to one file and reuses existing patterns already proven in account/portfolio tabs.
  Date/Author: 2026-02-27 / Codex

- Decision: Use data-driven semantic accents already available in the vault VM payload (`side`, signed numeric values, `status`, `type-label`) to style rows/cells.
  Rationale: We can achieve substantial visual parity without changing backend/state model contracts.
  Date/Author: 2026-02-27 / Codex

- Decision: Keep existing vault tab column schemas in this pass and focus on row-level semantic styling only.
  Rationale: The user request for this pass focused on visual/plain-row discrepancy; preserving schemas reduced risk while still delivering substantial parity gains.
  Date/Author: 2026-02-27 / Codex

## Outcomes & Retrospective

Implemented and validated. The vault detail activity panel now tracks Hyperliquid styling much more closely in the areas called out by the user:

1. Active/inactive activity tabs are now transparent-background with baseline border and text-color emphasis, matching Hyperliquid interaction appearance.
2. Table headers are now transparent and non-uppercase, which removes the prior heavy/boxed look.
3. Rows now use compact shared density with lower visual weight and semantic accents applied inside cells.
4. Positions and Open Orders now color coin/side semantics and apply side-accent cell backgrounds similar to Hyperliquid’s long/short cues.
5. Order/Funding/Ledger/Depositor rows now apply meaningful status and signed-value colors and include underlined affordances where appropriate.

Behavioral parity in this pass is visual and interactional for table rendering; no API contracts, actions, or projection shapes were changed. All required validation gates passed.

## Context and Orientation

The vault detail page is rendered in `/hyperopen/src/hyperopen/views/vault_detail_view.cljs`, with activity rows and counts supplied by `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs`.

In this repository, a “vault activity tab” is one of: balances, positions, open orders, TWAP, trade history, funding history, order history, deposits and withdrawals, and depositors.

The current styling gaps are concentrated in the view file. Data needed for semantic styling is already present per row:

- `positions`: `side` inferred from signed `size`; `pnl` and `funding` signed values.
- `open-orders`: `side` and coin string.
- `trade-history`: `side`, `closed-pnl`.
- `funding-history`: `position-size` and `payment`.
- `order-history`: `side` and `status`.
- `deposits-withdrawals`: `type-label`, signed `amount`, `hash`.
- `depositors`: signed `unrealized-pnl`, signed `all-time-pnl`.

Discrepancy inventory from fresh capture evidence:

1. Tab selected state mismatch: Hyperopen active tab uses filled background (`bg-base-100/50`), while Hyperliquid uses transparent background with text color shift and baseline border.
2. Header mismatch: Hyperopen header row is uppercase on filled background; Hyperliquid header text is non-uppercase on transparent background with subtler contrast.
3. Row density mismatch: Hyperopen rows are visually heavier and plain; Hyperliquid rows are compact with lighter borders and more semantic inline accents.
4. Positions row semantics mismatch: Hyperliquid colors coin/side/leverage and signed metrics (green/red), while Hyperopen only colors PNL/funding text.
5. Open Orders row semantics mismatch: Hyperliquid colors side and coin by direction; Hyperopen leaves these mostly neutral.
6. Trade History / Funding / Order row semantics mismatch: Hyperliquid highlights direction/status/payment semantics; Hyperopen is mostly monochrome in these cells.
7. Deposits/Withdrawals mismatch: Hyperliquid uses richer action/status/account-value semantics and interactive affordance; Hyperopen uses minimal type+amount+hash styling.
8. Small affordance mismatch: Hyperliquid underlines some interactive values; Hyperopen leaves all values visually flat.

## Plan of Work

Milestone 1 updates shared activity container styling in `vault_detail_view.cljs`: tab button classes, header row classes, base row classes, and empty-state density. The goal is to remove the obvious “plain table” look and align spacing/contrast behavior with Hyperliquid.

Milestone 2 adds semantic style helpers in the same file (side class, status class, signed-value class, accent-chip wrappers) and applies them tab-by-tab to key columns. This includes position/open-order side coloring, trade/order direction styling, funding payment emphasis, ledger amount/type emphasis, and subtle interactive underlines on selected values.

Milestone 3 hardens tests for the new styling semantics and runs all required repository gates.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/vault_detail_view.cljs`.
   - Add small style helper fns (`side-tone-class`, `status-tone-class`, `interactive-value-class`, and coin-accent style maps).
   - Replace repeated plain row class strings with shared `activity-row-class` and shared cell class helpers.
   - Apply per-tab semantic class usage to the columns listed in the discrepancy inventory.

2. Edit `/hyperopen/test/hyperopen/views/vault_detail_view_test.cljs`.
   - Add assertions that key rows include semantic classes (for example, side/PNL/status class markers on representative tabs).

3. Run validation commands:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

4. Review `git status`, update this plan (`Progress`, `Decision Log`, `Outcomes`), and move to `/hyperopen/docs/exec-plans/completed/` when done.

## Validation and Acceptance

Acceptance criteria:

1. Activity tab selected styling is transparent-background-first (no filled active tab background), with clear active/inactive text color contrast.
2. Header row text is no longer hard-uppercase and appears visually lighter/closer to Hyperliquid density.
3. Positions and Open Orders show direction semantics at row-cell level (coin/side color cues).
4. Trade History, Funding History, and Order History show semantic coloring for signed/status/direction values.
5. Deposits/Withdrawals and Depositors rows no longer look flat; signed metrics and interactive fields are visually distinguishable.
6. Required validation commands pass.

## Idempotence and Recovery

All changes are additive view-layer class/style updates and are safe to re-run. If a class change causes visual regression, revert only the affected table helper or tab renderer hunk and re-run tests. No state migrations or destructive operations are involved.

## Artifacts and Notes

Primary evidence artifacts used for this pass:

- `/hyperopen/tmp/vault-parity-pass-2026-02-27T03-27-48-859Z-generic/hyperopen-local.json`
- `/hyperopen/tmp/vault-parity-pass-2026-02-27T03-27-48-859Z-generic/hyperliquid-remote.json`
- `/hyperopen/tmp/vault-tab-audit/2026-02-27T01-41-29-502Z-direct/hyperopen-local/tab-positions.png`
- `/hyperopen/tmp/vault-tab-audit/2026-02-27T01-41-29-502Z-direct/hyperliquid-remote/tab-positions.png`

## Interfaces and Dependencies

No new dependencies are required. This pass should preserve existing action IDs, VM contracts, and API effects. Scope is presentation-layer parity in:

- `/hyperopen/src/hyperopen/views/vault_detail_view.cljs`
- `/hyperopen/test/hyperopen/views/vault_detail_view_test.cljs`

Revision note (2026-02-27 / Codex): Initial plan created after fresh local+remote style captures and discrepancy inventory for row-level visual parity.
Revision note (2026-02-27 / Codex): Updated progress, decision log, and outcomes after implementing row-level styling parity changes and passing all validation gates.
