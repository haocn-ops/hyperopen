# HYPE/USDC Active-Asset Icon Parity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the active asset bar in Hyperopen will render the `HYPE/USDC` icon with the same visual polarity as Hyperliquid. A user will be able to open the trade page, see `HYPE/USDC` selected, and observe that the icon no longer appears white-backed or visually inverted relative to Hyperliquid's live trade header.

The user-visible proof is simple: compare Hyperliquid's live `HYPE/USDC` header icon with Hyperopen's active asset bar. Before the fix, Hyperopen shows the transparent portions of the SVG on a forced white fill. After the fix, Hyperopen lets the SVG remain transparent so the icon sits on the same dark surface treatment as Hyperliquid.

## Progress

- [x] (2026-03-06 16:33Z) Captured a fresh Hyperliquid desktop snapshot at `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T16-33-02-954Z-3b30705a/` and verified the live page loads with `HYPE/USDC` active in a fresh browser profile.
- [x] (2026-03-06 16:34Z) Queried the live Hyperliquid DOM and confirmed the active header icon source is `https://app.hyperliquid.xyz/coins/HYPE_spot.svg`.
- [x] (2026-03-06 16:35Z) Isolated the local visual delta to `/hyperopen/src/hyperopen/views/active_asset_view.cljs`, where the `<img>` element adds a forced `bg-white` class behind transparent SVG icons.
- [x] (2026-03-06 16:35Z) Created `bd` issue `hyperopen-l7b` and claimed it for this session.
- [x] (2026-03-06 16:36Z) Removed the forced white image background from the active asset icon renderer while preserving existing load/error/fallback behavior.
- [x] (2026-03-06 16:36Z) Updated the active asset view tests so the HYPE spot case and generic icon cases assert transparent rendering semantics instead of white-backed rendering.
- [x] (2026-03-06 16:38Z) Ran required validation gates successfully: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-03-06 16:38Z) Closed `bd` issue `hyperopen-l7b` with reason `Completed`.

## Surprises & Discoveries

- Observation: Hyperliquid currently defaults a fresh trade-page profile to `HYPE/USDC`, which made live reference capture straightforward.
  Evidence: `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T16-33-02-954Z-3b30705a/hyperliquid/desktop/screenshot.png` and page title `29.770 | HYPE/USDC | Hyperliquid`.

- Observation: Hyperliquid's live DOM uses the same asset URL family Hyperopen already targets; the mismatch is not an upstream icon-key selection problem for this case.
  Evidence: `node tools/browser-inspection/src/cli.mjs eval --session-id sess-1772814838650-f6fca6 --expression "...document.querySelectorAll('img')..."` returned `https://app.hyperliquid.xyz/coins/HYPE_spot.svg`.

- Observation: `HYPE.svg` and `HYPE_spot.svg` currently resolve to identical content, so changing icon-key selection would not address the inversion.
  Evidence: Both URLs returned the same `ETag` and identical SVG payloads on 2026-03-06.

- Observation: The visual inversion is reproduced by compositing the same transparent SVG over white instead of a dark header background.
  Evidence: `/hyperopen/tmp/icon-diff/hype-side-by-side.png` shows the same `HYPE_spot.svg` on dark background (matches Hyperliquid) versus white background (matches the reported Hyperopen issue).

## Decision Log

- Decision: Treat this as a rendering-background bug instead of an asset-key bug.
  Rationale: Hyperliquid's live DOM and Hyperopen's `market-icon-url` resolution both point to `HYPE_spot.svg`; only Hyperopen adds `bg-white` behind the transparent SVG.
  Date/Author: 2026-03-06 / Codex

- Decision: Scope the code change to the active asset icon renderer instead of changing every icon surface in the app.
  Rationale: The reported regression is in the active asset bar, and the only forced white background found in icon rendering code is in `/hyperopen/src/hyperopen/views/active_asset_view.cljs`.
  Date/Author: 2026-03-06 / Codex

- Decision: Preserve existing fallback behavior, image sizing, and load/error wiring while removing only the white backing class.
  Rationale: This fixes the parity issue with minimal regression risk and keeps icon failure handling intact.
  Date/Author: 2026-03-06 / Codex

## Outcomes & Retrospective

The root cause was a local rendering class, not a bad icon URL. Hyperopen already requested the same `HYPE_spot.svg` asset that Hyperliquid uses live, but then painted a white background behind the transparent SVG. Removing that class restored the expected dark-surface rendering with minimal code change and no effect on fallback/error behavior.

Delivered results:

- `/hyperopen/src/hyperopen/views/active_asset_view.cljs` no longer forces `bg-white` behind active-asset SVG icons.
- `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs` now asserts that the HYPE spot icon and generic icon cases remain transparent-backed.
- All required validation gates passed after the change.

This plan achieved the original purpose: `HYPE/USDC` now renders with the same icon polarity as Hyperliquid's live trade header.

## Context and Orientation

The active asset bar is rendered in `/hyperopen/src/hyperopen/views/active_asset_view.cljs`. The relevant function is `asset-icon`, which decides whether to render a live coin SVG or a monogram fallback. When an SVG is available, the function currently renders an `<img>` with classes that include `bg-white`.

The coin SVG URL logic lives in `/hyperopen/src/hyperopen/views/asset_icon.cljs`. That file maps market metadata such as `:coin`, `:symbol`, `:base`, and `:market-type` into Hyperliquid's `https://app.hyperliquid.xyz/coins/<key>.svg` asset URLs. For `HYPE/USDC`, Hyperopen already resolves to `HYPE_spot.svg`, which matches Hyperliquid's live DOM.

The relevant behavior tests live in `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs`. Those tests already cover spot icon selection, fallback monograms, and image event wiring. One existing assertion explicitly expects the `bg-white` class, so the test suite must change with the implementation.

In this plan, "visual polarity" means whether the transparent portions of an SVG reveal the dark page background as Hyperliquid does, or reveal a forced white backing as Hyperopen currently does.

## Plan of Work

Edit `asset-icon` in `/hyperopen/src/hyperopen/views/active_asset_view.cljs` and remove the `bg-white` class from the rendered `<img>`. Do not change the parent rounded container, sizing, click behavior, or `:on` handlers for `:load` and `:error`.

Update `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs` so the active asset icon tests no longer expect the white background class. Keep the assertions that matter for behavior: the correct icon URL is chosen, the image renders immediately, the fallback monogram appears when icons are missing, and no background-image style hack is used.

Run the required repository validation commands from `/hyperopen`. After those pass, close `bd` issue `hyperopen-l7b` and update this document's `Progress` and `Outcomes & Retrospective` sections with the final results.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/active_asset_view.cljs` and remove `bg-white` from the `<img>` class list in `asset-icon`.
2. Edit `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs` and replace `bg-white` assertions with assertions that the class is absent while the image node and URL remain correct.
3. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
4. Close the issue:
   - `bd close hyperopen-l7b --reason "Completed" --json`

Expected validation shape:

    npm run check
    ...
    shadow-cljs - config: /Users/barry/projects/hyperopen/shadow-cljs.edn
    ...
    compiled successfully

    npm test
    ...
    Ran <N> tests containing <M> assertions.
    0 failures, 0 errors.

    npm run test:websocket
    ...
    Ran <N> tests containing <M> assertions.
    0 failures, 0 errors.

## Validation and Acceptance

Acceptance is satisfied when all of the following are true:

1. Hyperopen no longer forces a white backing behind active-asset SVG icons in `/trade`.
2. The `HYPE/USDC` icon therefore reads the same way as Hyperliquid's live header icon on a dark background.
3. Existing icon fallback behavior still works when an icon is missing.
4. `npm run check`, `npm test`, and `npm run test:websocket` all pass.

Manual evidence for parity should reference:

- `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T16-33-02-954Z-3b30705a/hyperliquid/desktop/screenshot.png`
- `/hyperopen/tmp/icon-diff/hype-side-by-side.png`

## Idempotence and Recovery

This change is safe to re-apply because it only removes one CSS class from one render path and adjusts the paired tests. If a regression appears, recover by restoring the previous class list and test expectations together so the renderer and test suite remain aligned.

## Artifacts and Notes

- Hyperliquid live snapshot: `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T16-33-02-954Z-3b30705a/`
- Hyperliquid component screenshot: `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T16-33-02-954Z-3b30705a/hyperliquid/desktop/screenshot.png`
- White-vs-dark icon compositing proof: `/hyperopen/tmp/icon-diff/hype-side-by-side.png`
- Tracking issue: `hyperopen-l7b`

## Interfaces and Dependencies

No new dependencies are required.

The relevant interfaces after the change remain:

- `hyperopen.views.asset-icon/market-icon-url`
- `hyperopen.views.active-asset-view/asset-icon`

The `asset-icon` renderer must continue to emit:

- an `<img>` node when `market-icon-url` returns a non-empty URL and the market key is not in `missing-icons`
- a monogram fallback node otherwise
- `:load` and `:error` handlers that dispatch `[:actions/mark-loaded-asset-icon market-key]` and `[:actions/mark-missing-asset-icon market-key]`

Plan revision note: 2026-03-06 16:35Z - Initial plan authored after live Hyperliquid capture, DOM verification, and local rendering root-cause isolation.
Plan revision note: 2026-03-06 16:38Z - Updated progress and retrospective after removing the forced white background, passing all required validation gates, and closing `hyperopen-l7b`.
