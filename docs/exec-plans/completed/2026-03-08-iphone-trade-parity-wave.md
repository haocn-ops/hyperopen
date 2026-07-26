# iPhone Trade Parity Wave

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and must be maintained in accordance with that file.

## Purpose / Big Picture

After this wave, the HyperOpen trade page should behave more like Hyperliquid on an iPhone 14 Pro Max sized viewport. A phone user should see a compact top shell with an actionable menu, a condensed market header that hides extra statistics behind a disclosure, a clearer chart-first mobile information hierarchy, and a bottom navigation bar instead of the current utility-heavy footer treatment. The proof will be a fresh before/after browser comparison against Hyperliquid at the iPhone 14 Pro Max viewport, backed by repository-native browser-inspection artifacts and a QA note.

This work also creates a reproducible iPhone 14 Pro Max browser-inspection path so future parity work does not have to hand-roll viewport overrides. The tracked work for this wave is `hyperopen-hwx` with child issues `hyperopen-6sp`, `hyperopen-kj9`, `hyperopen-0rn`, and `hyperopen-87g`.

## Progress

- [x] (2026-03-08 13:37Z) Created epic `hyperopen-hwx` and child issues `hyperopen-6sp`, `hyperopen-kj9`, `hyperopen-0rn`, and `hyperopen-87g`.
- [x] (2026-03-08 13:38Z) Claimed the epic and all child issues.
- [x] (2026-03-08 13:39Z) Re-read the planning and work-tracking rules, inspected the current mobile header, active-asset strip, trade mobile surfaces, footer, app shell, and browser-inspection configuration.
- [x] (2026-03-08 13:41Z) Added the iPhone 14 Pro Max browser-inspection config override and npm scripts, then captured the before-state `/trade` comparison at `/hyperopen/tmp/browser-inspection/compare-2026-03-08T13-41-29-156Z-a0677cdb/`.
- [x] (2026-03-08 13:58Z) Implemented the mobile shell parity changes across the header, active-asset strip, trade layout, footer, and app shell.
- [x] (2026-03-08 14:01Z) Added and updated automated tests covering the mobile menu, mobile asset disclosure, mobile bottom nav, iPhone browser config override, and the account-shortcut visibility change.
- [x] (2026-03-08 14:02Z) Ran `npm run check`, `npm test`, `npm run test:websocket`, and `npm run test:browser-inspection` successfully on the final code.
- [x] (2026-03-08 14:04Z) Captured the final iPhone compare at `/hyperopen/tmp/browser-inspection/compare-2026-03-08T14-03-10-188Z-1e82469d/` and wrote `/hyperopen/docs/qa/iphone-trade-parity-wave-qa-2026-03-08.md`.

## Surprises & Discoveries

- Observation: The current trade mobile shell is still missing the exact structural elements the user called out from the screenshot review: a real hamburger interaction, bottom navigation, and collapsed top statistics.
  Evidence: `/hyperopen/src/hyperopen/views/header_view.cljs` renders a mobile menu icon button with only `{:title "Menu"}` and no click action; `/hyperopen/src/hyperopen/views/footer_view.cljs` still renders diagnostics and footer links in a fixed mobile footer; `/hyperopen/src/hyperopen/views/active_asset_view.cljs` always renders the `Vol`, `Oracle`, `OI`, and `Funding` chips on mobile.

- Observation: The current mobile trade page still exposes too many layers at once even after the earlier parity waves.
  Evidence: `/hyperopen/src/hyperopen/views/trade_view.cljs` renders both the primary mobile surface tabs and a second always-visible row of account shortcuts above the chart/order-book/ticket surfaces.

- Observation: The browser-inspection defaults still only define the older `390x844` mobile preset.
  Evidence: `/hyperopen/tools/browser-inspection/config/defaults.json` contains only `desktop` and `mobile`, and the existing `mobile` viewport is `390x844` with `deviceScaleFactor: 3`.

- Observation: The first pass of the new mobile asset-summary markup and mobile menu markup each missed one closing vector delimiter, and `npm run check` caught both immediately through the hiccup linter.
  Evidence: `npm run check` first failed on `/hyperopen/src/hyperopen/views/active_asset_view.cljs` around rows `1082-1169`, then on `/hyperopen/src/hyperopen/views/header_view.cljs` around rows `474-530`, before both were fixed and the gate passed.

- Observation: The repo typography test forbids explicit bracketed sub-16px text utilities in view code, even when the rendered intent is modest mobile label sizing.
  Evidence: `npm test` failed `hyperopen.views.typography-scale-test` until `text-[10px]`, `text-[11px]`, and `text-[13px]` were replaced with standard `text-xs` and `text-sm` utilities.

## Decision Log

- Decision: Implement the iPhone 14 Pro Max capture path as a browser-inspection config override plus dedicated npm scripts instead of changing the global default mobile viewport for every run.
  Rationale: The repository already depends on the existing `mobile` preset for earlier parity work. A dedicated override keeps old workflows stable while giving this wave a reproducible iPhone-sized path.
  Date/Author: 2026-03-08 / Codex

- Decision: Keep the trade page chart-first on mobile while making the top shell and secondary surfaces more collapsible.
  Rationale: The user complaint is not that chart-first is wrong; it is that HyperOpen still shows too much at once and lacks the mobile navigation/chrome patterns Hyperliquid uses.
  Date/Author: 2026-03-08 / Codex

- Decision: Replace the small-screen footer treatment with a route-aware bottom navigation bar while preserving diagnostics and utility links on larger breakpoints.
  Rationale: The mobile footer is a large visible contributor to the screenshot gap and does not match Hyperliquid’s bottom navigation model.
  Date/Author: 2026-03-08 / Codex

- Decision: Keep the market-detail disclosure state in `:trade-ui` but implement the hamburger menu with native `details/summary`.
  Rationale: The market disclosure needs explicit app-state control for predictable rendering/tests, while the menu only needs an actionable shell affordance and is simpler as native disclosure markup.
  Date/Author: 2026-03-08 / Codex

## Outcomes & Retrospective

This wave is complete.

The main user-visible outcomes are:

- HyperOpen now has a real iPhone-sized mobile trade shell with a left-side hamburger menu, a compact mobile brand mark, and icon-led controls instead of the old single inert right-side menu button.
- The mobile market strip now starts collapsed and reveals secondary statistics only when the disclosure is opened.
- The trade page now hides more secondary content by default: the primary tabs are `Chart / Order Book / Trade`, and account shortcuts only appear when the account surface is active.
- The old small-screen diagnostics/footer strip is replaced by a `Markets / Trade / Account` bottom nav, while the diagnostics/footer controls remain on larger breakpoints.
- Browser-inspection now has a reproducible iPhone 14 Pro Max override and npm entry points for future parity work.

Measured result:

- Before: `/trade` iPhone diff ratio `0.1139` at `/hyperopen/tmp/browser-inspection/compare-2026-03-08T13-41-29-156Z-a0677cdb/`
- After: `/trade` iPhone diff ratio `0.1091` at `/hyperopen/tmp/browser-inspection/compare-2026-03-08T14-03-10-188Z-1e82469d/`

The raw diff only improved modestly, but the large structural mismatches from the user screenshots are resolved. The remaining gap is mostly chart chrome, content density, and lower-page composition rather than missing mobile shell mechanics.

## Context and Orientation

The trade route is composed from a small set of view files. `/hyperopen/src/hyperopen/views/app_view.cljs` owns the full app shell, including the main content region and the fixed footer. `/hyperopen/src/hyperopen/views/header_view.cljs` renders the global top navigation, wallet controls, and the current non-functional mobile menu button. `/hyperopen/src/hyperopen/views/active_asset_view.cljs` renders the market selector and top-of-chart market statistics for both desktop and mobile. `/hyperopen/src/hyperopen/views/trade_view.cljs` decides which mobile surfaces are visible and how the chart, order book, order ticket, and account tables are arranged. `/hyperopen/src/hyperopen/views/footer_view.cljs` renders the bottom-fixed diagnostics/footer region that currently occupies the same space where Hyperliquid uses `Markets`, `Trade`, and `Account`.

Simple UI state in this repository is commonly stored in the application state tree under keys such as `:trade-ui` or `:websocket-ui` and updated through small action functions that return `[:effects/save ...]`. The existing example is `/hyperopen/src/hyperopen/trade/layout_actions.cljs`, which currently normalizes and stores the active mobile trade surface. Any new UI-only toggles for this wave should follow that pattern so the views remain pure functions of state.

Browser parity evidence is produced by the browser-inspection subsystem under `/hyperopen/tools/browser-inspection/`. The CLI reads `/hyperopen/tools/browser-inspection/config/defaults.json`, accepts an override via the `BROWSER_INSPECTION_CONFIG` environment variable, and writes timestamped artifacts to `/hyperopen/tmp/browser-inspection/`. Earlier work discovered that a fresh ephemeral browser profile must first load `/index.html` before deep-link captures on the local app are trustworthy; this warm step must be part of the QA procedure for this wave.

## Plan of Work

First, add an iPhone 14 Pro Max browser-inspection override under `/hyperopen/tools/browser-inspection/config/` and expose it through npm scripts in `/hyperopen/package.json`. The override should keep the same logical viewport name (`mobile`) but change its width and height to the iPhone 14 Pro Max geometry so compare runs can still request `--viewports mobile`. Once that path exists, use it to capture a before-state comparison between Hyperliquid `/trade` and local `/trade`, warming the local session through `/index.html` before the route capture.

Next, update the mobile header in `/hyperopen/src/hyperopen/views/header_view.cljs`. The phone version should move closer to Hyperliquid’s compact icon-led shell: a real hamburger toggle on the left, a smaller mobile brand treatment, and visible compact utility buttons instead of burying everything behind desktop-only classes. The mobile menu must be actionable, not decorative. Keep the desktop header behavior intact.

Then, update `/hyperopen/src/hyperopen/views/active_asset_view.cljs` so the mobile market strip defaults to a compact summary row and hides secondary statistics behind an expand/collapse control. The collapsed state should still show the market identity, price, and change. The expanded state should reveal the same secondary stats the current mobile strip shows now. The disclosure needs to be state-driven and testable.

After that, simplify the small-screen trade route in `/hyperopen/src/hyperopen/views/trade_view.cljs` and `/hyperopen/src/hyperopen/views/footer_view.cljs`. The mobile surface tabs should expose a narrower top-level choice set closer to Hyperliquid’s `Chart`, `Order Book`, and `Trades/Trade` model. The page should stop presenting extra surface rows above the fold unless they are directly relevant to the selected surface. The bottom-fixed footer should become a mobile navigation bar with `Markets`, `Trade`, and `Account` actions on small screens, while the existing diagnostics/footer content stays available on larger breakpoints. Update `/hyperopen/src/hyperopen/views/app_view.cljs` if bottom padding or shell spacing needs to change to accommodate the new mobile nav height cleanly.

Finally, extend the existing view tests in `/hyperopen/test/hyperopen/views/` to cover the new mobile affordances, run the required validation gates, rerun the iPhone compare workflow, and write the QA findings into a note under `/hyperopen/docs/qa/`. If the final screenshots and diff ratios show a meaningful narrowing of the gap and there are no regressions in the required gates, close the four child issues and the parent epic, then move this plan to `/hyperopen/docs/exec-plans/completed/`.

## Concrete Steps

Run all commands from `/hyperopen`.

1. Add the iPhone browser-inspection override and scripts.

   - Edit `/hyperopen/tools/browser-inspection/config/iphone-14-pro-max.json`.
   - Edit `/hyperopen/package.json`.
   - Update any affected browser-inspection tests under `/hyperopen/tools/browser-inspection/test/`.

2. Capture the before-state iPhone comparison.

   - Start the local app with `npm run dev`.
   - Warm the local route by loading `http://localhost:8080/index.html` in a browser-inspection session.
   - Run the iPhone compare path against:
     - `https://app.hyperliquid.xyz/trade`
     - `http://localhost:8080/trade`

3. Implement the UI changes in:

   - `/hyperopen/src/hyperopen/views/header_view.cljs`
   - `/hyperopen/src/hyperopen/views/active_asset_view.cljs`
   - `/hyperopen/src/hyperopen/views/trade_view.cljs`
   - `/hyperopen/src/hyperopen/views/footer_view.cljs`
   - `/hyperopen/src/hyperopen/views/app_view.cljs`
   - any new or updated trade layout action files if state toggles are needed

4. Update automated tests in:

   - `/hyperopen/test/hyperopen/views/header_view_test.cljs`
   - `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs`
   - `/hyperopen/test/hyperopen/views/footer_view_test.cljs`
   - `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`
   - any new trade-view-specific test namespace if the current shared spacing test becomes too indirect

5. Run validation:

   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

6. Run final manual/browser QA and document it:

   - capture final iPhone compare artifacts
   - inspect the local trade page manually at the iPhone 14 Pro Max viewport
   - write the QA note under `/hyperopen/docs/qa/`
   - close `hyperopen-6sp`, `hyperopen-kj9`, `hyperopen-0rn`, `hyperopen-87g`, and `hyperopen-hwx` if complete

## Validation and Acceptance

Acceptance is based on observable mobile behavior and the final browser evidence.

On the local `/trade` route at the iPhone 14 Pro Max viewport, the user should see a compact phone header, an actionable hamburger menu, a collapsed-by-default market stats treatment with a disclosure control, a less cluttered top-of-page layout, and a bottom mobile navigation bar instead of the current diagnostic/footer strip. The page should still render the chart, order book, ticket, and account surfaces correctly, and any new mobile controls should dispatch existing route or surface actions without console errors.

Validation requires:

- `npm run check`
- `npm test`
- `npm run test:websocket`

Browser acceptance requires a fresh compare artifact pair for Hyperliquid `/trade` versus local `/trade` at the iPhone 14 Pro Max viewport, plus a manual review of the final screenshots showing that the major gaps identified from the user screenshots have narrowed.

## Idempotence and Recovery

This wave is limited to UI, view-state actions, tests, docs, and browser-inspection tooling. Re-running the validation commands is safe. Re-running browser-inspection commands only creates new timestamped artifacts under `/hyperopen/tmp/browser-inspection/`.

If the local route does not render in a fresh browser-inspection session, repeat the known-good warm flow by navigating to `/index.html` first, then to `/trade`. If a UI refactor introduces a render-time exception, prefer backing out the specific view change in a focused patch rather than reverting unrelated files, and capture the exception in the `Surprises & Discoveries` section before retrying.

## Artifacts and Notes

Expected outputs from this wave:

- one active ExecPlan at `/hyperopen/docs/exec-plans/active/2026-03-08-iphone-trade-parity-wave.md`
- one final QA note under `/hyperopen/docs/qa/`
- browser-inspection artifact directories under `/hyperopen/tmp/browser-inspection/`
- closed `bd` issues for the epic and child tasks

## Interfaces and Dependencies

If new mobile toggles are needed, add them to `/hyperopen/src/hyperopen/trade/layout_actions.cljs`, register them in `/hyperopen/src/hyperopen/runtime/collaborators.cljs`, and declare them in `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs` plus `/hyperopen/src/hyperopen/schema/contracts.cljs`. Keep these actions UI-only by returning `[:effects/save ...]` or `[:effects/save-many ...]` and avoid introducing side effects for simple disclosure state.

Use the existing route action `[:actions/navigate ...]` for the mobile bottom navigation targets. Use the existing account tab action `[:actions/select-account-info-tab ...]` and trade mobile surface action `[:actions/select-trade-mobile-surface ...]` whenever that behavior already exists instead of inventing alternate routing.

Use the browser-inspection CLI under `/hyperopen/tools/browser-inspection/src/cli.mjs` and the config loader under `/hyperopen/tools/browser-inspection/src/config.mjs`. Keep the iPhone viewport support additive so existing desktop/mobile compare workflows still behave as before.

Plan revision note: 2026-03-08 13:40Z - Created this ExecPlan after the user supplied iPhone screenshots, the epic and child issues were opened and claimed, and the current mobile trade shell/browser-inspection seams were confirmed in code.
Plan revision note: 2026-03-08 14:04Z - Updated after implementation to record the shipped mobile shell changes, the final iPhone compare artifacts, the validation results, and the remaining chart/content differences that still keep the visual diff in the medium band.
