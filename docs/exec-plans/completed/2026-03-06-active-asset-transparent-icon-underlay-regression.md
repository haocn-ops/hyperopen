# Fix Active Asset Transparent Icon Underlay Regression

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, transparent active-asset icons such as `ETH-USDC` and `XRP-USDC` render the correct artwork in the trade header again instead of showing a composite of the icon plus fallback monogram letters beneath it. Users also still get the black monogram fallback for genuinely broken icon URLs.

## Progress

- [x] (2026-03-06 19:36Z) Reproduced the reported symptom from the user screenshots and traced it to the unresolved-icon path in `/hyperopen/src/hyperopen/views/active_asset_view.cljs`.
- [x] (2026-03-06 19:37Z) Created and claimed `bd` issue `hyperopen-zd1` for the regression.
- [x] (2026-03-06 19:41Z) Replaced the unresolved icon markup so working transparent SVGs no longer reveal fallback text underneath them.
- [x] (2026-03-06 19:45Z) Replaced the fragile attribute-order dependency with a post-render probe that inspects `complete` and `naturalWidth` and updates icon status sets deterministically.
- [x] (2026-03-06 19:46Z) Updated view tests to cover unresolved transparent-icon rendering and the probe behavior.
- [x] (2026-03-06 19:46Z) Ran `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-03-06 19:59Z) Performed manual browser QA for `ETH-USDC`, `XRP-USDC`, and `BRENTOIL-USDC`.
- [x] (2026-03-06 20:04Z) Closed `bd` issue `hyperopen-zd1`.

## Surprises & Discoveries

- Observation: The prior broken-image fix kept the monogram text mounted underneath every unresolved icon.
  Evidence: `/hyperopen/src/hyperopen/views/active_asset_view.cljs` rendered the monogram whenever `loaded-icons` did not contain the market key, even when a valid icon URL existed.

- Observation: Transparent SVGs make that underlay visible, so the issue was not a bad icon URL for `ETH` or `XRP`; it was an incorrect compositing stack in the unresolved state.
  Evidence: The user screenshots showed the expected icon container but with letterforms bleeding through transparent parts of the SVG.

- Observation: The `BRENTOIL` icon endpoint currently answers with HTTP `200` but serves `text/html`, so transport success is not a reliable signal that the icon is valid.
  Evidence: `curl -I https://app.hyperliquid.xyz/coins/xyz:BRENTOIL.svg` returned `Content-Type: text/html`, and the browser probe resolved the image as missing via `naturalWidth = 0`.

- Observation: Screenshot capture that reloads `/trade` can perturb the currently selected asset during QA.
  Evidence: Stable verification required one-shot in-page probes that selected the asset, waited for image resolution, and then inspected `loaded-icons`, `missing-icons`, and the rendered DOM in the same evaluation.

## Decision Log

- Decision: Keep the unresolved success path, but change its base layer from the monogram fallback to a plain dark icon surface.
  Rationale: Working transparent icons need a neutral dark backing, not fallback text, while broken icons can still transition to the monogram once the probe resolves them as missing.
  Date/Author: 2026-03-06 / Codex

- Decision: Move icon-status detection for the unresolved probe image to a Replicant render hook.
  Rationale: The regression came from event listeners attaching after `src`; a render hook can inspect the mounted DOM node directly and attach listeners through the browser API without relying on Replicant attribute ordering.
  Date/Author: 2026-03-06 / Codex

- Decision: Keep the render-hook probe local to the active-asset view and write the resolved `loaded-icons` / `missing-icons` sets synchronously through `apply-asset-icon-status-updates`.
  Rationale: The hidden probe must settle the icon state immediately from the mounted DOM node. The prior event-action path was vulnerable to the same timing window, while the synchronous store update correctly resolved `ETH`, `XRP`, and the broken `BRENTOIL` case in live QA.
  Date/Author: 2026-03-06 / Codex

## Outcomes & Retrospective

The root cause was the unresolved icon renderer itself. After the earlier broken-image fix, active asset icons with a valid but transparent SVG still mounted the monogram fallback text underneath the icon layer. Assets such as `ETH-USDC` and `XRP-USDC` therefore rendered a composite of the real SVG plus fallback letters.

The fix was to replace that unresolved base with a neutral dark circle, keep the icon visible through a CSS `background-image`, and use a hidden mounted image probe to promote the market key into `loaded-icons` or `missing-icons`. Broken-link behavior remains intact. In live verification, `BRENTOIL-USDC` resolved into `missing-icons` and rendered the `BRENT` monogram fallback instead of a browser broken-image glyph.

Validation passed on March 6, 2026:

- `npm run check`
- `npm test`
- `npm run test:websocket`

Manual browser QA against `http://localhost:8081/trade` also passed on March 6, 2026:

- `ETH-USDC` rendered the correct icon artwork with no fallback letters visible through transparent areas.
- `XRP-USDC` rendered the correct icon artwork with no fallback letters visible through transparent areas.
- `BRENTOIL-USDC` rendered the monogram fallback after the hidden probe marked the icon missing.

## Context and Orientation

The active asset header icon is rendered in `/hyperopen/src/hyperopen/views/active_asset_view.cljs`, inside the `asset-icon` function. That function receives two sets from app state: `loaded-icons`, which marks icon keys known to have loaded successfully, and `missing-icons`, which marks icon keys known to have failed. The actual URL resolution lives in `/hyperopen/src/hyperopen/views/asset_icon.cljs`.

The final code uses three visual states. A loaded icon renders a visible `<img>`. A missing icon renders the monogram fallback, which is the black circle with up to five letters from the asset symbol. An unresolved icon renders a neutral dark circle plus a CSS `background-image` and a hidden probe image. That avoids showing fallback text beneath transparent SVG artwork while still allowing broken icons to downgrade to the monogram once the probe resolves them as missing.

Replicant is the rendering engine for this app. It supports `:replicant/on-render`, which is a lifecycle hook invoked with the mounted DOM node. That hook can inspect `HTMLImageElement.complete` and `HTMLImageElement.naturalWidth`, and it can register native `load` and `error` listeners after mount. That is the deterministic seam used here.

The relevant tests live in `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs`. They now assert that unresolved icons do not expose the monogram text while still wiring the hidden probe and background layer, and that complete images resolve to the correct icon-status set membership.

## Plan of Work

Edit `/hyperopen/src/hyperopen/views/active_asset_view.cljs` in two places. First, add a small post-render probe helper that inspects the mounted hidden image node, registers native `load` and `error` listeners for later resolution, and applies the resulting icon status update to the shared `loaded-icons` / `missing-icons` sets. Second, change `asset-icon` so the unresolved state shows a plain dark circular base instead of the text monogram, then overlays the CSS `background-image` success path and the hidden probe image that uses the new lifecycle hook.

Update `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs` so unresolved icon cases no longer expect the monogram string for working assets. Add coverage for the probe helper so a complete image with `naturalWidth` greater than zero resolves as `:loaded`, and a complete image with `naturalWidth` zero resolves as `:missing`.

After the code and tests pass, perform manual browser QA. Verify `ETH-USDC` and `XRP-USDC` render the correct artwork in the active asset bar. Then verify the broken `BRENTOIL-USDC` case still resolves to the monogram fallback instead of a browser broken-image glyph.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/active_asset_view.cljs`.
2. Edit `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs`.
3. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
4. Start the app with `npm run dev` if it is not already running.
5. Perform browser QA for `ETH`, `XRP`, and `xyz:BRENTOIL`.
6. Close the issue:
   - `bd close hyperopen-zd1 --reason "Completed" --json`

## Validation and Acceptance

Acceptance is satisfied when all of the following are true:

1. `ETH-USDC` and `XRP-USDC` render the correct icon artwork in the active asset bar and no longer show fallback letters through transparent SVG areas.
2. Broken icon URLs still resolve to the monogram fallback instead of a browser broken-image placeholder.
3. `npm run check`, `npm test`, and `npm run test:websocket` all pass.
4. Manual browser QA confirms the fix on March 6, 2026 for `ETH-USDC`, `XRP-USDC`, and `BRENTOIL-USDC`.

## Idempotence and Recovery

This work is safe to retry because it only touches the active asset renderer, its tests, and this plan. If the render-hook probe behaves incorrectly, revert the renderer and the paired test assertions together so the unresolved-state markup and probe expectations stay aligned.

## Artifacts and Notes

- Tracking issue: `hyperopen-zd1`
- User-provided evidence is in the current thread screenshots for `ETH-USDC` and `XRP-USDC`.

## Interfaces and Dependencies

No new external dependencies are required.

The existing action ids for asset-icon status updates remain available for the rest of the application. This change keeps the Replicant lifecycle seam local to `/hyperopen/src/hyperopen/views/active_asset_view.cljs` and uses it only for the active asset header icon.

Plan revision note: 2026-03-06 19:38Z - Initial plan authored after tracing the transparent-icon regression to the unresolved icon compositing stack and deciding to move icon probing into a deterministic Replicant render hook.
Plan revision note: 2026-03-06 20:04Z - Updated after implementation, validation, browser QA, and issue closure confirmed the transparent-icon underlay regression was fixed without reintroducing the broken-link fallback regression.
