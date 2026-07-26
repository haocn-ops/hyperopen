---
owner: platform
status: draft
last_reviewed: 2026-03-07
review_cycle_days: 90
source_of_truth: false
---

# Funding Modal Workflow Slices QA 2026-03-07

## Scope

Manual browser QA for the funding modal refactor that introduced explicit content kinds, shared panels, and intent-revealing actions.

## Environment

- Repository: `/hyperopen`
- Local app: `npm run dev` started by browser-inspection
- Live browser session: `sess-1772891307682-1ae2d7`
- Browser: headed Google Chrome launched through `/hyperopen/tools/browser-inspection/`

## Browser Inspection Artifacts

- Trade snapshot run:
  `/hyperopen/tmp/browser-inspection/inspect-2026-03-07T13-50-54-300Z-3267d94b/`
- Portfolio snapshot run:
  `/hyperopen/tmp/browser-inspection/inspect-2026-03-07T13-50-54-304Z-6be36191/`

## Manual Checks

1. Navigated to `http://localhost:8080/trade`.
2. Opened the trade-page `Deposit` funding action in the live browser session.
3. Verified the deposit asset-selection modal rendered with title `Deposit`.
4. Selected `USDC` from the deposit asset list.
5. Verified the amount-entry step rendered with title `Deposit USDC`, showed the new `MIN` control, and did not render a lifecycle panel.
6. Closed the deposit modal.
7. Navigated to `http://localhost:8080/portfolio`.
8. Opened `Withdraw` from the portfolio action row and verified the modal title, asset select, withdraw amount input, and MAX control.
9. Closed the withdraw modal.
10. Opened `Send` from the portfolio action row and verified the transfer modal title, both direction buttons, and the transfer amount input.

## Observations

- Trade deposit modal:
  - `modalOpen: true`
  - `depositSelectVisible: true`
  - `title: "Deposit"`
- Trade deposit `USDC` amount step:
  - `title: "Deposit USDC"`
  - `amountStepVisible: true`
  - `minButtonVisible: true`
  - `lifecycleVisible: false`
- Portfolio withdraw modal:
  - `title: "Withdraw"`
  - `withdrawAmountVisible: true`
  - `assetSelectVisible: true`
  - `maxButtonVisible: true`
- Portfolio transfer modal:
  - `title: "Perps <-> Spot"`
  - `transferAmountVisible: true`
  - `directionButtonsVisible: true`
  - `maxButtonVisible: true`

## Caveat

The browser-inspection interaction path used `eval --allow-unsafe-eval` to click buttons in a live browser tab. Those scripted clicks opened the correct flows and content, but they did not preserve anchor-bound placement data in a way that let this QA pass confirm anchored popover geometry from the live session. Anchored width and no-overflow behavior are covered by the new view/helper tests and by the explicit action wiring change from `/portfolio` to the anchor-aware open actions.
