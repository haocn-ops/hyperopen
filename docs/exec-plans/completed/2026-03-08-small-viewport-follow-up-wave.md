# Small-Viewport Parity Follow-Up Wave

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and must be maintained in accordance with that file.

## Purpose / Big Picture

After this follow-up wave, HyperOpen should close the two residual small-viewport gaps left by the March 8 parity pass. On `/trade` phone, the page should feel less header-heavy and expose account/history access sooner without falling back to the old long stack. On `/vaults`, phone and tablet should read closer to Hyperliquid’s flatter responsive-table presentation, and the next QA rerun should use geometry-compatible compare artifacts so the delta is trustworthy.

The visible proof will be a fresh browser QA note that shows a smaller trade-phone gap than the March 8 run and improved vault screenshots with a clearer parity story.

## Progress

- [x] (2026-03-08 01:45 EST) Claimed follow-up tasks `hyperopen-wrr.6` and `hyperopen-wrr.7`.
- [x] (2026-03-08 01:47 EST) Re-read the March 8 QA note and confirmed the next implementation order should be `/trade` phone polish first, then `/vaults` small-view polish.
- [x] (2026-03-08 01:50 EST) Inspected the current `header_view`, `trade_view`, `active_asset_view`, and `vaults/list_view` implementations plus the latest compare screenshots.
- [x] (2026-03-08 01:56 EST) Fixed the header parity-id rendering bug, tightened the disconnected wallet button/header spacing, and added view-test coverage so raw metadata no longer renders as visible text.
- [x] (2026-03-08 01:58 EST) Improved `/trade` phone parity by tightening the market strip, renaming the ticket surface to `Trade`, and surfacing direct account/history shortcut chips below the primary phone surface tabs.
- [x] (2026-03-08 01:59 EST) Improved `/vaults` phone/tablet parity by flattening the small-view card layout, reducing hero weight, tightening controls, and adding a route-level `Connect` CTA when disconnected.
- [x] (2026-03-08 02:07 EST) Discovered and fixed a live browser regression in the responsive wallet button markup after browser QA exposed blank local trade renders in a warmed session.
- [x] (2026-03-08 02:10 EST) Re-ran `npm run check`, `npm test`, and `npm run test:websocket`, then completed fresh warmed-session browser QA for `/trade` and `/vaults` on phone and tablet.

## Surprises & Discoveries

- Observation: The current mobile screenshots still show `{:data-parity-id "header-wallet-control"}` as visible text.
  Evidence: The March 8 trade and vault screenshots under `/hyperopen/tmp/browser-inspection/compare-2026-03-08T01-16-28-846Z-5258dda8/` and `/hyperopen/tmp/browser-inspection/compare-2026-03-08T01-18-13-653Z-00def0fd/` display the raw map string in the header.

- Observation: The visible header text leak comes from malformed hiccup in `/hyperopen/src/hyperopen/views/header_view.cljs`, where `:data-parity-id` maps were inserted as child nodes instead of merged into the element attrs.
  Evidence: `header-view` currently uses forms like `[:div {:class ...} {:data-parity-id ...} ...]`, which renders the second map as content.

- Observation: The remaining `/trade` phone gap is not the old layout failure anymore; it is mostly chrome density and earlier access to account/history affordances.
  Evidence: The March 8 QA note in `/hyperopen/docs/qa/small-viewport-hyperliquid-parity-implementation-qa-2026-03-08.md` records trade phone improving from `0.2388` to `0.1283` while still staying `high`.

- Observation: The March 7 and March 8 vault compare runs used incompatible screenshot geometries, which makes vault delta numbers unreliable.
  Evidence: The March 8 QA note records March 7 vault phone at `3072x4098` vs March 8 phone at `1170x2532`, and March 7 vault tablet at `780x1688` vs March 8 tablet at `2048x2732`.

- Observation: Fresh ephemeral browser profiles do not get valid local route captures by navigating straight to `/trade` or `/vaults`; the session must first load `/index.html` so the service worker can take control of SPA routes.
  Evidence: The invalid compare runs under `/hyperopen/tmp/browser-inspection/compare-2026-03-08T01-58-03-683Z-ab5a2d90/` and `/hyperopen/tmp/browser-inspection/compare-2026-03-08T01-58-43-935Z-9a17f659/` produced essentially blank local captures, while the warmed-session reruns under `/hyperopen/tmp/browser-inspection/compare-2026-03-08T02-08-51-180Z-cd70b603/` and `/hyperopen/tmp/browser-inspection/compare-2026-03-08T02-09-35-467Z-06504c52/` produced full local UI snapshots.

- Observation: The first implementation of the responsive disconnected wallet button introduced a live render regression that the test suite did not catch.
  Evidence: `/hyperopen/tmp/browser-inspection/inspect-2026-03-08T02-03-22-339Z-4e0770c0/` captured a blank local trade render with browser exceptions and console warnings pointing at `[:<> [:span {:class ["sm:hidden"]} "Connect"] ...]`. After replacing that fragment-based child structure, `/hyperopen/tmp/browser-inspection/inspect-2026-03-08T02-08-25-290Z-f35ffac1/` showed `1098` semantic nodes and no exceptions.

## Decision Log

- Decision: Treat the header parity-id text leak as part of this wave instead of deferring it as unrelated cleanup.
  Rationale: It is visible in the exact mobile screenshots that define the residual parity gap and directly inflates the remaining diff.
  Date/Author: 2026-03-08 / Codex

- Decision: Improve `/trade` phone parity by adding earlier account/history shortcuts rather than making the account surface the default mobile view.
  Rationale: The March 8 wave already established chart-first phone layout as the correct direction. The remaining problem is reachability, not default surface choice.
  Date/Author: 2026-03-08 / Codex

- Decision: Improve `/vaults` parity by making the route feel more like a dense responsive list and by adding a real connect CTA when disconnected.
  Rationale: Hyperliquid’s vault route visibly exposes an inline connect button and flatter label/value rows; matching that is safer and more honest than reintroducing the old disabled CTA.
  Date/Author: 2026-03-08 / Codex

- Decision: Treat warmed-session browser QA as the canonical local QA path for this wave.
  Rationale: Direct SPA-route navigation in a fresh profile is not reliable against the dev server here, so QA evidence must explicitly use the warmed `/index.html` step before route captures.
  Date/Author: 2026-03-08 / Codex

## Outcomes & Retrospective

This wave completed the two residual follow-up tasks from epic `hyperopen-wrr`.

The main user-visible fixes are:

- `/trade` phone no longer leaks `:data-parity-id` metadata in the header, reaches account/history affordances earlier, and keeps a tighter market-summary band.
- `/vaults` now shows a real route-level `Connect` CTA when disconnected, uses flatter phone cards, and reads closer to Hyperliquid on tablet.
- The live browser render regression introduced during the header refactor was caught and fixed before closeout.

Fresh final browser QA is recorded in `/hyperopen/docs/qa/small-viewport-follow-up-wave-qa-2026-03-08.md`.

Final compare summary versus the March 8 post-implementation QA baseline:

- `/trade` phone improved from `0.1283` to `0.1228`.
- `/trade` tablet moved from `0.0743` to `0.0823`; visually the intended right-rail composition remained intact, but the raw diff ratio was slightly noisier on the rerun.
- `/vaults` phone moved from `0.1166` to `0.1154`.
- `/vaults` tablet improved from `0.0430` to `0.0325`.

Net: the follow-up wave delivered the intended polish and corrected the visible header bug plus the browser-render regression. The strongest measurable win from the follow-up was `/vaults` tablet; the clearest qualitative wins were removing the header text leak on `/trade` and aligning `/vaults` phone with Hyperliquid’s route-level connect treatment.

## Context and Orientation

This follow-up wave starts after the completed parity implementation in `/hyperopen/docs/exec-plans/completed/2026-03-07-small-viewport-hyperliquid-parity-implementation.md` and the QA note in `/hyperopen/docs/qa/small-viewport-hyperliquid-parity-implementation-qa-2026-03-08.md`.

`/trade` phone composition is defined in `/hyperopen/src/hyperopen/views/trade_view.cljs`. The compact asset strip above the chart is in `/hyperopen/src/hyperopen/views/active_asset_view.cljs`. The global page header that still contributes too much mobile chrome is in `/hyperopen/src/hyperopen/views/header_view.cljs`. The account/history table surface reused on trade and portfolio is in `/hyperopen/src/hyperopen/views/account_info_view.cljs`.

`/vaults` list-route composition is in `/hyperopen/src/hyperopen/views/vaults/list_view.cljs`. This file defines the hero, search/filter controls, responsive desktop table, and mobile cards. The route can already dispatch `[:actions/connect-wallet]` through the existing wallet runtime.

The browser QA artifacts from the previous wave are under `/hyperopen/tmp/browser-inspection/compare-2026-03-08T01-16-28-846Z-5258dda8/` for trade phone and `/hyperopen/tmp/browser-inspection/compare-2026-03-08T01-18-13-653Z-00def0fd/` for vaults phone. The next rerun must keep viewport geometry consistent with the current run so comparisons are meaningful.

## Plan of Work

First, repair the malformed header hiccup in `/hyperopen/src/hyperopen/views/header_view.cljs` so `:data-parity-id` metadata is attached correctly and no longer renders as literal text. While in that file, tighten the phone header further by shortening the disconnected wallet button label on the smallest screens and compressing the logo treatment so trade and vaults start closer to content.

Next, improve `/trade` phone. In `/hyperopen/src/hyperopen/views/trade_view.cljs`, keep the chart-first mobile surface model but change the surface copy and add a second compact row of account/history shortcuts that immediately jump to the relevant account tab while switching the active mobile surface to `:account`. In `/hyperopen/src/hyperopen/views/active_asset_view.cljs`, tighten the phone market strip so the top line and stat chips consume less height and feel closer to Hyperliquid’s compact market summary.

Then, improve `/vaults`. In `/hyperopen/src/hyperopen/views/vaults/list_view.cljs`, replace the remaining small-view card presentation with flatter label/value rows, add a route-level connect button that appears only when the wallet is disconnected, reduce hero height again on phone, and keep the route visually denser than the current implementation.

Finally, run the required repository gates, rerun browser QA for `/trade` and `/vaults` on phone and tablet, update the QA note or add a follow-up note, then close `hyperopen-wrr.6` and `hyperopen-wrr.7` if the results are acceptable.

## Concrete Steps

Run all commands from `/hyperopen`.

1. Edit:

   - `/hyperopen/src/hyperopen/views/header_view.cljs`
   - `/hyperopen/src/hyperopen/views/trade_view.cljs`
   - `/hyperopen/src/hyperopen/views/active_asset_view.cljs`
   - `/hyperopen/src/hyperopen/views/vaults/list_view.cljs`
   - any directly affected tests under `/hyperopen/test/hyperopen/views/`

2. Run validation:

   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

3. Start the local app:

   - `npm run dev`

   If `8080` is busy, note the alternate port and use that exact local URL in QA.

4. Rerun browser compare captures for:

   - local `/trade`
   - local `/vaults`
   - Hyperliquid `/trade`
   - Hyperliquid `/vaults`

   on phone and tablet, using compatible viewport config for both new runs.

5. Update the QA note and close:

   - `hyperopen-wrr.6`
   - `hyperopen-wrr.7`

## Validation and Acceptance

Acceptance is based on both behavior and browser evidence.

On `/trade` phone, the header should no longer leak raw hiccup metadata as visible text, the market strip should be shorter, and mobile users should be able to jump directly to balances, positions, orders, or trade history without scrolling through the chart and ticket stack. On `/vaults`, the page should expose a route-level connect CTA when disconnected, the small-view list should read as flatter label/value rows with snapshot data, and the hero should be less visually dominant.

Validation requires:

- `npm run check`
- `npm test`
- `npm run test:websocket`

Browser QA must produce fresh compare artifacts for `/trade` and `/vaults` and show the header parity-id leak gone from screenshots.

## Idempotence and Recovery

These edits are view-only and additive. Re-running the tests or browser compare commands is safe and will only create new timestamped artifacts under `/hyperopen/tmp/browser-inspection/`.

If the local app binds to a different port than `8080`, rerun the compare commands against the actual reported port instead of trying to force the older URL. If browser geometry does not match the current baseline, discard that run and rerun with the correct viewport configuration before recording conclusions.

## Artifacts and Notes

Expected artifacts from this wave:

- updated mobile header, trade, and vaults view code
- updated view tests
- fresh browser compare artifacts for `/trade` and `/vaults`
- updated QA documentation under `/hyperopen/docs/qa/`
- closed follow-up issues `hyperopen-wrr.6` and `hyperopen-wrr.7`

## Interfaces and Dependencies

Use the existing wallet action `[:actions/connect-wallet]` for any route-level vault connect CTA. Do not create a vault-specific wallet effect.

Use the existing trade mobile surface action `[:actions/select-trade-mobile-surface ...]` together with the existing account tab selection action `[:actions/select-account-info-tab ...]` when adding earlier trade account/history shortcuts.

Keep browser QA on the existing browser-inspection CLI under `/hyperopen/tools/browser-inspection/src/cli.mjs`.

Plan revision note: 2026-03-08 01:51 EST - Created this follow-up ExecPlan after claiming `hyperopen-wrr.6` and `hyperopen-wrr.7`, identifying the header parity-id text leak, and defining the residual trade/vault parity work from the March 8 QA artifacts.
Plan revision note: 2026-03-08 02:10 EST - Completed the wave after landing the trade/vault polish, fixing the live wallet-button render regression discovered during browser QA, rerunning all required gates, and writing the fresh warmed-session QA evidence.
