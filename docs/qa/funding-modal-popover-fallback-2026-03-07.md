---
owner: platform
status: draft
last_reviewed: 2026-03-07
review_cycle_days: 90
source_of_truth: false
---

# Funding Modal Popover Fallback QA 2026-03-07

## Scope

Manual browser QA for the funding popover regression fix that restored selector-based fallback anchoring when the funding modal opens without explicit bounds.

## Environment

- Repository: `/hyperopen`
- Live browser session: `sess-1772892717619-1f5036`
- Browser: headed Google Chrome launched through `/hyperopen/tools/browser-inspection/`

## Browser Inspection Artifacts

- Trade snapshot run:
  `/hyperopen/tmp/browser-inspection/inspect-2026-03-07T14-12-04-396Z-4a97ef42/`

## Manual Checks

1. Navigated to `http://localhost:8080/trade`.
2. Triggered the trade-page `Deposit` funding action through browser-inspection eval.
3. Queried the rendered funding panel and close overlay classes/styles from the live DOM.

## Observations

- The funding panel opened with title `Deposit`.
- The panel rendered explicit anchored geometry:
  - `left: 714px`
  - `top: 328px`
  - `width: 448px`
- The panel class list did not include centered modal sizing classes such as `w-full` / `max-w-md`.
- The click-catcher overlay used `bg-transparent`, which matches the anchored popover path rather than the dimmed modal path.

## Conclusion

The trade-page deposit flow now falls back to anchored popover presentation again when explicit event bounds are unavailable, matching the behavior introduced in commit `3736b3f1`.
