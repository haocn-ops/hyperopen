# Subaccount Transfer Popover Polish

## Purpose

Direct user request on 2026-06-03: improve the `/subAccounts` transfer interaction to follow Hyperliquid's `Send Tokens` modal pattern while keeping Hyperopen's preferred anchored popover behavior.

## Progress

- [x] Record a failing view contract for a `Send Tokens` transfer popover instead of inline row controls.
- [x] Replace the inline transfer row controls with an anchored popover.
- [x] Preserve existing transfer action payload semantics for `:deposit` and `:withdraw`.
- [x] Add deterministic Playwright coverage across `375`, `768`, `1280`, and `1440` widths.
- [x] Run focused browser validation.

## Implementation Notes

- `hyperopen.views.subaccounts-view.management` now owns a compact transfer popover with title, supporting copy, close affordance, From/To labels, direction selector, disabled `USDC` selector, amount input, max hint, and Send/Cancel actions.
- `hyperopen.views.subaccounts-view` passes the row name and visible max values into row controls without adding new data fetching.
- The subaccount table row remains compact while the transfer popover is open.

## Surprises & Discoveries

- The existing transfer controls were inline inside the table row, which made the row become the form. The browser regression now asserts the row stays compact while the popover is open.
- Playwright route setup needed a deterministic `subAccounts` API fixture because route refresh can overwrite local seeded rows after wallet state changes.

## Decision Log

- Use an anchored popover, not a modal, per the user's requested Hyperopen difference from Hyperliquid.
- Keep the transfer token fixed to disabled `USDC`, matching current transfer semantics.
- Keep max display derived from already-rendered account equity values rather than introducing new balance-fetching behavior in a visual polish task.

## Outcomes & Retrospective

- Focused ClojureScript view test passed after implementation.
- Focused Playwright regression passed across all review widths.
- Initial `npm run check` failed only on active ExecPlan formatting and placement; this completed plan resolves that docs-lint issue before rerunning gates.
