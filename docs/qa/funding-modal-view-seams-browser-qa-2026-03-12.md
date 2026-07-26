---
owner: platform
status: draft
last_reviewed: 2026-03-12
review_cycle_days: 90
source_of_truth: false
---

# Funding Modal View Seams Browser QA 2026-03-12

## Scope

Headed browser QA for the funding modal refactor in `/hyperopen/src/hyperopen/views/funding_modal.cljs`, focused on the two presentation modes called out in review:

- desktop anchored-popover behavior on `/trade`
- mobile bottom-sheet behavior on `/trade`
- live verification of the close-button `aria-label`
- live verification that the BTC deposit path now renders the address step instead of reusing the amount-step role

## Environment

- Repository: `/hyperopen`
- Local app: `npm run dev` started by browser-inspection
- Browser tooling: `/hyperopen/tools/browser-inspection/`
- Browser: headed Google Chrome launched by browser-inspection

## Artifacts

- Desktop scenario bundle:
  `/Users/barry/.codex/worktrees/456b/hyperopen/tmp/browser-inspection/scenario-2026-03-12T01-17-25-781Z-50c2dee0/`
- Desktop screenshot:
  `/Users/barry/.codex/worktrees/456b/hyperopen/tmp/browser-inspection/scenario-2026-03-12T01-17-25-781Z-50c2dee0/funding-modal-deposit-usdc/desktop/screenshot.png`
- Mobile scenario bundle:
  `/Users/barry/.codex/worktrees/456b/hyperopen/tmp/browser-inspection/scenario-2026-03-12T01-19-05-229Z-ea32f95a/`
- Mobile screenshot:
  `/Users/barry/.codex/worktrees/456b/hyperopen/tmp/browser-inspection/scenario-2026-03-12T01-19-05-229Z-ea32f95a/funding-modal-deposit-usdc/mobile/screenshot.png`

## Checks

1. Ran the checked-in `funding-modal-deposit-usdc` scenario in a headed browser at desktop width.
2. Verified the `/trade` deposit flow opened with `presentationMode: "anchored-popover"`, then advanced to `Deposit USDC` with `contentKind: ":deposit/amount"`.
3. Ran the same scenario in a headed browser at mobile width.
4. Verified the `/trade` deposit flow opened with `presentationMode: "mobile-sheet"` and stayed in the mobile sheet path after selecting `USDC`.
5. Opened a persistent mobile browser-inspection session, dispatched the mobile deposit flow live, and checked the rendered DOM for:
   - `data-funding-mobile-sheet-surface="true"`
   - close button `aria-label="Close funding dialog"`
   - `funding-deposit-amount-step` on the `USDC` amount step
6. Refreshed the live mobile route, reopened the deposit flow, selected `BTC`, and checked the rendered DOM plus oracle output for:
   - `title: "Deposit BTC"`
   - `contentKind: ":deposit/address"`
   - `funding-deposit-address-step` present
   - `funding-deposit-amount-step` absent

## Findings

- Desktop `/trade` deposit flow passed:
  - `title: "Deposit"` on asset select
  - `title: "Deposit USDC"` on amount entry
  - `presentationMode: "anchored-popover"`
  - screenshot captured successfully

- Mobile `/trade` deposit flow passed:
  - `title: "Deposit"` on asset select
  - `title: "Deposit USDC"` on amount entry
  - `presentationMode: "mobile-sheet"`
  - `mobileSheetCount: 1`
  - screenshot captured successfully

- Live mobile DOM checks passed:
  - `data-funding-mobile-sheet-surface="true"` present on the modal shell
  - close button exposes `aria-label="Close funding dialog"`
  - `USDC` path renders `funding-deposit-amount-step`
  - `BTC` path renders `funding-deposit-address-step` and does not render `funding-deposit-amount-step`

## Caveat

This was live headed browser QA, but the interactions were driven through `HYPEROPEN_DEBUG.dispatch(...)`, browser-inspection scenarios, and read-only DOM/oracle queries rather than literal hand-clicking. That still exercised the rendered browser UI and captured screenshots/artifacts, but it should be described as browser-inspection QA rather than a purely manual cursor-driven pass.
