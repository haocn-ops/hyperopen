# Order Book Hyperliquid Color and Width Parity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the order book in Hyperopen will visually track Hyperliquid more closely in the areas users called out: narrower-feeling order columns and matching font colors for `Price`, `Size`, and `Total` values. The visible result should be that order rows no longer look overly wide and size/total text no longer appears too bright.

## Progress

- [x] (2026-02-24 00:24Z) Captured Hyperliquid order book styles via live browser inspection (`/hyperopen/tools/browser-inspection/`) and confirmed exact computed colors and geometry.
- [x] (2026-02-24 00:26Z) Captured local Hyperopen order book styles and quantified deltas versus Hyperliquid.
- [x] (2026-02-24 00:29Z) Implemented order book/trades class updates for column ratio, neutral text colors, and depth-bar opacity parity in `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`.
- [x] (2026-02-24 00:29Z) Updated orderbook view tests for neutral text, 15% depth bars, and `1:2:2` column template assertions.
- [x] (2026-02-24 00:30Z) Narrowed trade right-rail columns from 340px to 280px to align panel proportions more closely with Hyperliquid and updated layout contract tests.
- [x] (2026-02-24 00:32Z) Ran required validation gates (`npm run check`, `npm test`, `npm run test:websocket`) after all code changes.
- [x] (2026-02-24 00:33Z) Re-ran browser inspection and confirmed computed-style parity targets for header/value colors, column geometry, and depth shading.

## Surprises & Discoveries

- Observation: Hyperliquid `Price` up/down value colors already match Hyperopen exactly (`rgb(237, 112, 136)` and `rgb(31, 166, 125)`), so the perceived mismatch is primarily neutral text tone and typography/geometry.
  Evidence: Live eval from `node tools/browser-inspection/src/cli.mjs eval` on `https://app.hyperliquid.xyz/trade` and `http://localhost:8080/trade`.
- Observation: Hyperliquid orderbook rows use a `1:2:2` grid column split while Hyperopen uses equal thirds.
  Evidence: Hyperliquid computed `gridTemplateColumns` was approximately `53px 106px 106px`; Hyperopen computed `107px 107px 107px`.
- Observation: Hyperliquid depth bars are rendered with 15% opacity rather than 20%.
  Evidence: Computed background layers show red/green bars with `opacity: 0.15`.
- Observation: Most of the “orderbook is wider” discrepancy came from trade layout column sizing (`340px`) rather than only row-level grid classes.
  Evidence: Local `trade-orderbook-panel` measured at `340px` before changes; after updating `/hyperopen/src/hyperopen/views/trade_view.cljs` it measured `280px`.

## Decision Log

- Decision: Keep red/green price text values unchanged and focus on neutral text colors + width/spacing.
  Rationale: Exact computed color inspection shows price colors already parity-accurate; changing them would add churn without benefit.
  Date/Author: 2026-02-24 / Codex
- Decision: Switch orderbook/trades grid to a `1:2:2` column template to better match Hyperliquid visual density.
  Rationale: This directly addresses the user-observed “ours is wider” discrepancy while preserving existing alignment behavior.
  Date/Author: 2026-02-24 / Codex
- Decision: Tune depth bar opacity from `0.20` to `0.15`.
  Rationale: This aligns with measured Hyperliquid rendering and avoids over-saturated depth shading.
  Date/Author: 2026-02-24 / Codex
- Decision: Reduce fixed right-rail panel widths from `340px` to `280px` at `lg/xl` breakpoints.
  Rationale: Internal orderbook row classes alone did not materially reduce panel breadth; narrowing the rail width brought the orderbook footprint closer to Hyperliquid.
  Date/Author: 2026-02-24 / Codex

## Outcomes & Retrospective

Implemented and validated. Hyperopen now uses Hyperliquid-matching neutral orderbook colors (`rgb(148, 158, 156)` headers and `rgb(210, 218, 215)` size/total values), keeps exact ask/bid price colors, and uses 15% depth-bar fills. The orderbook/trades row template is now `1:2:2`, and the trade-side rail was reduced from `340px` to `280px` for closer panel-width parity.

Post-change browser inspection on local trade view showed:

- `trade-orderbook-panel` width: `280px` (was `340px`)
- Orderbook row grid template: `65px 98px 98px` in current BTC context with `grid-cols-[1fr_2fr_2fr]`
- Header colors: `rgb(148, 158, 156)`
- Size/total value colors: `rgb(210, 218, 215)`
- Ask depth background: `rgba(237, 112, 136, 0.15)`

## Context and Orientation

The order book UI is implemented in `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`. The same file contains both the orderbook and trades tab grid rows. Styling is mainly expressed via Tailwind utility class vectors in Hiccup/Replicant nodes.

Panel width is controlled by `/hyperopen/src/hyperopen/views/trade_view.cljs` through fixed grid column classes at `lg` and `xl` breakpoints.

The relevant tests are in:

- `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs` (orderbook/trades class contracts)
- `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs` (trade layout width classes)

Live style capture workflow is available in `/hyperopen/tools/browser-inspection/` and documented in `/hyperopen/docs/runbooks/browser-live-inspection.md`.

Measured style deltas for this task:

- Hyperliquid header text (`Price`, `Size`, `Total`): `rgb(148, 158, 156)`.
- Hyperopen header text currently: `rgb(156, 163, 175)`.
- Hyperliquid size/total body values: `rgb(210, 218, 215)`.
- Hyperopen size/total body values currently: `rgb(255, 255, 255)`.
- Hyperliquid depth bar opacity: `0.15`.
- Hyperopen depth bar opacity currently: `0.20`.
- Hyperliquid row column split: approximately `1:2:2`.
- Hyperopen row column split currently: `1:1:1`.

## Plan of Work

Update `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs` to introduce explicit parity color classes for orderbook neutral headers and neutral size/total body text, and apply a shared `1:2:2` grid template class to both orderbook and trades rows/headers. Keep all interaction and data flow logic unchanged.

Adjust depth bar class constants in `order-row` from 20% to 15% opacity.

Update `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs` so tests validate the new parity classes (neutral size/total color class, 15% depth bar opacity, column template class) while preserving existing alignment/structure checks.

## Concrete Steps

From `/hyperopen`:

1. Edit `src/hyperopen/views/l2_orderbook_view.cljs`:
   - Add reusable class constants for orderbook/trades column template and Hyperliquid neutral tones.
   - Apply those classes to header/row `Price`, `Size`, and `Total` cells.
   - Update depth bar opacity classes to 15%.
2. Edit `test/hyperopen/views/l2_orderbook_view_test.cljs`:
   - Rename and update assertions for neutral size/total text class.
   - Update depth bar opacity assertions to 15%.
   - Add assertions that header and level content rows include `grid-cols-[1fr_2fr_2fr]`.
3. Run validation:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
4. Re-inspect local computed styles with:
   - `node tools/browser-inspection/src/cli.mjs eval --session-id <id> --expression "..."`

## Validation and Acceptance

Acceptance criteria:

- Orderbook/trades headers and level rows use `grid-cols-[1fr_2fr_2fr]`.
- Orderbook and trades neutral headers render with color `rgb(148, 158, 156)`.
- Orderbook size/total body values render with color `rgb(210, 218, 215)`.
- Price up/down values remain `rgb(31, 166, 125)` and `rgb(237, 112, 136)`.
- Depth bars render with 15% opacity.
- Required validation gates pass.

## Idempotence and Recovery

These edits are additive class/token refinements in view markup and tests. Re-running the steps is safe. If any style class causes an unexpected regression, revert only the affected class changes in `l2_orderbook_view.cljs` and re-run tests.

## Artifacts and Notes

Inspection artifacts captured during this plan:

- `/hyperopen/tmp/browser-inspection/inspect-2026-02-24T00-24-12-267Z-47a113cb/hyperliquid/desktop/screenshot.png`
- `/hyperopen/tmp/browser-inspection/inspect-2026-02-24T00-32-38-083Z-fce3776a/hyperopen-local/desktop/screenshot.png`

## Interfaces and Dependencies

No API, reducer, websocket, or signing interface changes were required. This plan touched only view markup classes and view tests:

- `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`
- `/hyperopen/src/hyperopen/views/trade_view.cljs`
- `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs`
- `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`

Plan revision note: 2026-02-24 00:27Z - Initial plan created after live Hyperliquid and local style capture with concrete parity targets.
Plan revision note: 2026-02-24 00:33Z - Marked implementation complete with right-rail width adjustment, full gate validation, and post-change browser measurement evidence.
