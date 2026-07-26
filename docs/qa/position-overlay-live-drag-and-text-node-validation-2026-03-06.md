# Position Overlay Live Drag And Text-Node Validation (2026-03-06)

## Purpose

Close the two residual follow-ups left after the retained-row repaint refactor:

- verify the live `Liq. Price` drag path in a real headed browser session;
- verify that text-only overlay updates now retain their text nodes and avoid child-list churn.

## Environment

- App: local `/hyperopen` dev server via `npm run dev`
- Browser: headed Google Chrome launched through `/hyperopen/tools/browser-inspection/`
- Route: `http://localhost:8080/trade?spectate=0x162cc7c861ebd0c06b3d72319201150482518185`
- Active asset: `MON`
- QA run summary: `/hyperopen/tmp/browser-inspection/manual-position-overlay-drag-2026-03-06T14-16-41-953Z/summary.json`

## Procedure

1. Start a headed browser-inspection session with managed local app startup.
2. Navigate to the spectate route above.
3. Set `localStorage["active-asset"] = "MON"` and reload the route.
4. Wait until `.chart-position-overlays` contains both retained row children and both liquidation/PNL chips are visible.
5. Install browser-side mutation observers on:
   - `.chart-position-overlays` root child list
   - the liquidation badge subtree
6. Record initial liquidation badge/chip text and retain the underlying text-node references.
7. Drag the visible liquidation badge downward by `12px` through CDP mouse events.
8. Capture screenshots before drag, during drag preview, and after release.

## Observed Result

- Before drag, the liquidation overlay showed `Liq. Price $0.02663089` with chip `0.02663089`.
- After a `12px` downward drag move, the overlay updated live to `Liq. Price $0.02275668 Remove $2,679.65 Margin` with chip `0.02275668`.
- The `Edit Margin` dialog opened during drag preview and remained open after release.
- The dialog showed chart-derived context:
  - `Chart liquidation drag`
  - `Current $0.02663089 -> Target $0.02275668`
  - active mode button: `Remove`
  - prefilled amount input: `249.446083`

## DOM Stability Result

- Root overlay child count stayed `2` throughout the interaction.
- `sameRootChildren` was `true`.
- `sameTextNodes` was `true` for the retained liquidation badge text nodes.
- `sameChipTextNode` was `true` for the retained liquidation chip text node.
- Root child-list mutations stayed at `0` added / `0` removed.
- Liquidation badge subtree child-list mutations stayed at `0` added / `0` removed.

This confirms both layers of the intended improvement:

- no root-level overlay teardown/rebuild during the live drag path;
- no text-node replacement churn inside the retained liquidation badge/chip subtree.

## Artifacts

- Summary JSON: `/hyperopen/tmp/browser-inspection/manual-position-overlay-drag-2026-03-06T14-16-41-953Z/summary.json`
- Before drag screenshot: `/hyperopen/tmp/browser-inspection/manual-position-overlay-drag-2026-03-06T14-16-41-953Z/before-drag.png`
- During drag screenshot: `/hyperopen/tmp/browser-inspection/manual-position-overlay-drag-2026-03-06T14-16-41-953Z/during-drag.png`
- After release screenshot: `/hyperopen/tmp/browser-inspection/manual-position-overlay-drag-2026-03-06T14-16-41-953Z/after-release.png`

## Notes

- Current product behavior opens the margin modal on the first drag preview move, not only after release. That matches the existing preview dispatch path already covered by `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs`.
