# Mobile Balances Send Modal QA

Date: 2026-03-08
Viewport: iPhone 14 Pro Max (`430x932`)
Route: `/trade?spectate=0x162cc7c861ebd0c06b3d72319201150482518185`

## Scope

Verify that tapping `Send` from the mobile balances surface opens a real `Send Tokens` modal backed by the funding runtime instead of doing nothing.

## Environment

- Local app: `http://localhost:8080`
- Browser-inspection session: `sess-1773002540971-e0893d`
- Artifact run:
  - `/hyperopen/tmp/browser-inspection/inspect-2026-03-08T20-45-23-381Z-70fc474f/`

## Steps

1. Warmed the SPA by navigating to `http://localhost:8080/index.html`.
2. Navigated to `http://localhost:8080/trade?spectate=0x162cc7c861ebd0c06b3d72319201150482518185` in the iPhone 14 Pro Max viewport.
3. Located the mobile balances cards and expanded `mobile-balance-card-spot-0`.
4. Tapped the expanded card’s `Send` action.
5. Captured the resulting UI state via browser-inspection.

## Result

Pass.

The mobile balances `Send` action opened a `Send Tokens` sheet with the expected structure:

- title: `Send Tokens`
- destination placeholder: `0x...`
- fixed account label: `Trading Account`
- asset: `USDC`
- max label present: `MAX: 1,141,517.51940030 USDC`
- primary submit button present: `Send`

Structured browser-eval evidence is recorded in:

- `/hyperopen/tmp/browser-inspection/inspect-2026-03-08T20-45-23-381Z-70fc474f/summary.json`

Screenshot evidence:

- `/hyperopen/tmp/browser-inspection/inspect-2026-03-08T20-45-23-381Z-70fc474f/local-send-modal-open/mobile/screenshot.png`

## Notes

- The inspected card was `mobile-balance-card-spot-0`, not the synthetic `perps-usdc` card.
- This QA pass verified modal opening and field presence. It did not submit a live `sendAsset` transaction.
