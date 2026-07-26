# Restore Active Asset Icon Probe Fallback

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the active asset bar on `/trade` will no longer show the browser's broken-image glyph when an upstream coin icon URL fails. Instead, the UI will keep the black circular monogram fallback visible until an icon is confirmed loaded, and it will continue to show that monogram permanently when the icon URL is missing or broken. The user-visible proof is simple: choose an asset whose coin SVG 404s, or render the active asset icon in a failing state in tests, and observe that the first half of the symbol remains visible in the circle rather than being replaced by the broken-image placeholder.

## Progress

- [x] (2026-03-06 18:49Z) Reproduced the regression path from source inspection and git history review in `/hyperopen/src/hyperopen/views/active_asset_view.cljs`.
- [x] (2026-03-06 18:49Z) Created and claimed `bd` issue `hyperopen-qlw` for this regression.
- [x] (2026-03-06 18:49Z) Confirmed the root-cause change history: `c7bc829c9c329dc580ca86a031379e169c123acb` kept the monogram visible while probing, and `a791afc3014e6721fe9deeec3b6e057fee3ca969` removed that load gate while simplifying the parity fix.
- [x] (2026-03-06 18:51Z) Restored the active-asset icon renderer so icons become visible only after `:load`, while broken or missing icons leave the monogram fallback in place.
- [x] (2026-03-06 18:52Z) Updated active-asset view tests so they require the load-gated probe behavior and preserve the March 6 transparent-icon parity expectations.
- [x] (2026-03-06 18:53Z) Installed missing npm dependencies with `npm ci` after the first `npm run check` attempt failed because `@noble/secp256k1` was absent from `node_modules`.
- [x] (2026-03-06 18:55Z) Ran `npm run check`, `npm test`, and `npm run test:websocket` successfully.
- [x] (2026-03-06 18:55Z) Closed `bd` issue `hyperopen-qlw` with reason `Completed`.

## Surprises & Discoveries

- Observation: The broken-image regression is not from the icon URL resolver in `/hyperopen/src/hyperopen/views/asset_icon.cljs`; it is from the active asset renderer choosing to show the `<img>` before the app knows whether the resource succeeded.
  Evidence: The current `asset-icon` implementation computes `loaded-icons` but does not use it, and `show-icon?` depends only on whether a URL exists.

- Observation: This repository already solved this exact class of bug once.
  Evidence: `git show c7bc829c9c329dc580ca86a031379e169c123acb -- src/hyperopen/views/active_asset_view.cljs` shows the monogram-plus-probe pattern, while `git show a791afc3014e6721fe9deeec3b6e057fee3ca969 -- src/hyperopen/views/active_asset_view.cljs` removes it.

- Observation: The local validation environment was missing installed npm dependencies even though `package-lock.json` was present.
  Evidence: The first `npm run check` failed with `The required JS dependency "@noble/secp256k1" is not available`; `npm ci` installed 281 packages and the validation gates then passed.

## Decision Log

- Decision: Treat the regression as a load-gating bug, not an icon-key bug.
  Rationale: The UI regressed when it stopped using `loaded-icons` to decide when the visible icon should replace the fallback. Broken URLs should never be allowed to paint as the user-facing surface.
  Date/Author: 2026-03-06 / Codex

- Decision: Restore the guarded flow with a real `<img>` probe and a direct `<img>` render only after load, rather than reviving the older CSS `background-image` layer.
  Rationale: This preserves the March 6 transparent-SVG parity fix, keeps `object-contain` sizing, and avoids both the white background regression and the browser broken-image glyph.
  Date/Author: 2026-03-06 / Codex

## Outcomes & Retrospective

The root cause was a regression in the active asset icon renderer, not in the icon URL resolver. Commit `a791afc3014e6721fe9deeec3b6e057fee3ca969` simplified the icon markup for the HYPE parity fix and dropped the earlier load-gated probe behavior, which meant a broken upstream icon could render as the browser's default broken-image glyph before the app marked it missing. The restored implementation now keeps the monogram visible until a probe image reports success, and it still preserves the transparent-SVG parity fix by rendering the visible icon without a forced white background.

Delivered results:

- `/hyperopen/src/hyperopen/views/active_asset_view.cljs` now uses `loaded-icons` again to distinguish probe, loaded, and missing icon states.
- `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs` now requires the monogram-during-probe behavior, the loaded-icon transparent render, and the missing-icon fallback.
- `npm run check`, `npm test`, and `npm run test:websocket` all passed after installing missing npm packages with `npm ci`.
- `bd` issue `hyperopen-qlw` is closed.

This plan achieved the original purpose: missing or broken active-asset icons now resolve to the black monogram circle instead of exposing the browser broken-image placeholder.

## Context and Orientation

The active asset header is rendered in `/hyperopen/src/hyperopen/views/active_asset_view.cljs`. The function `asset-icon` is responsible for choosing between an upstream coin icon and the local monogram fallback. A monogram fallback means the small black circle that contains the first part of the asset symbol, such as `BRENT` for a broken `BRENTOIL` icon. The function receives two sets from app state: `:asset-selector :missing-icons` and `:asset-selector :loaded-icons`.

The icon URL itself comes from `/hyperopen/src/hyperopen/views/asset_icon.cljs`. That file is not the failing layer in this regression. If a URL exists, the current active-asset view renders an `<img>` immediately. If the request later fails, the browser has already painted the broken-image placeholder. The fallback only appears after `:actions/mark-missing-asset-icon` updates state, and the current view tests do not force the safer "monogram until success" behavior.

The relevant regression tests are in `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs`. Those tests already cover spot URL selection, missing-icon fallback, and the HYPE transparency parity behavior. They need stronger assertions so the view cannot regress by ignoring `loaded-icons` again.

## Plan of Work

Edit `asset-icon` in `/hyperopen/src/hyperopen/views/active_asset_view.cljs`. Reintroduce `loaded-icon?` and split the rendering into three states. When there is no icon URL, render only the monogram circle. When there is a URL but it is not yet in `loaded-icons`, render the monogram circle as the visible surface and add an invisible probe `<img>` with `:load` and `:error` handlers. When there is a URL and the key is already in `loaded-icons`, render the visible `<img>` without any white backing so transparent SVGs continue to match Hyperliquid's dark header treatment.

Update `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs` so it verifies the new contract. The unloaded probe state must still show the monogram and hide the probing image. The loaded state must show the icon without `bg-white`. The missing state must render no `<img>` node and keep the monogram text. Keep the spot `HYPE_spot.svg` URL assertion and the chevron assertion intact.

After the code and tests are in place, run the required repository gates from `/hyperopen`. If all three pass, close `hyperopen-qlw` and update this plan with the completed outcomes.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/active_asset_view.cljs` and restore `loaded-icon?` as a real branch in `asset-icon`.
2. Edit `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs` so it checks probe, loaded, and missing icon states explicitly.
3. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
4. Close the issue:
   - `bd close hyperopen-qlw --reason "Completed" --json`

Expected validation shape:

    npm run check
    ...
    compiled successfully

    npm test
    ...
    0 failures, 0 errors.

    npm run test:websocket
    ...
    0 failures, 0 errors.

## Validation and Acceptance

Acceptance is satisfied when all of the following are true:

1. An active asset whose icon has not loaded yet still shows the monogram circle instead of a visible `<img>`.
2. An active asset whose icon URL fails never shows the browser broken-image placeholder; after the `:error` path, the monogram remains.
3. An active asset whose icon URL loads successfully still renders the icon with transparent SVG parity and without a forced white background.
4. `npm run check`, `npm test`, and `npm run test:websocket` all pass.

## Idempotence and Recovery

This change is safe to repeat because it is a view-layer rendering adjustment plus paired tests. If the revised load gating causes an unexpected visual regression, the safe rollback is to restore the previous `asset-icon` body and the matching tests together so the renderer and assertions stay aligned.

## Artifacts and Notes

- Tracking issue: `hyperopen-qlw`
- Relevant historical commits:
  - `c7bc829c9c329dc580ca86a031379e169c123acb` (`Fix asset icon fallback without hiding available icons`)
  - `a791afc3014e6721fe9deeec3b6e057fee3ca969` (`Fix active asset icon parity`)
  - `d22e112529e348779b4fe909403c187f0c9fcf3c` (`Fix HYPE/USDC active asset icon parity`)

## Interfaces and Dependencies

No new dependencies are required.

The interface contracts that must remain true after the change are:

- `hyperopen.views.asset-icon/market-icon-url` still resolves icon URLs from normalized market metadata.
- `hyperopen.views.active-asset-view/asset-icon` still dispatches `[:actions/mark-loaded-asset-icon market-key]` on `:load` and `[:actions/mark-missing-asset-icon market-key]` on `:error`.
- `:asset-selector :missing-icons` and `:asset-selector :loaded-icons` remain the state authorities for whether the active asset should render its icon or its monogram fallback.

Plan revision note: 2026-03-06 18:49Z - Initial plan authored after confirming the regression source in current code and commit history.
Plan revision note: 2026-03-06 18:55Z - Updated progress and retrospective after restoring the probe-gated icon flow, passing all required validation gates, closing `hyperopen-qlw`, and moving this ExecPlan to `completed`.
