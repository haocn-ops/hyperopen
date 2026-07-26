# Desktop Trade Short-Height Scroll Recovery

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and must be maintained in accordance with that file.

## Purpose / Big Picture

After this change, a user on `/trade` with a shorter desktop viewport can scroll the desktop trade content downward and reach the right-rail `Unified Account Summary` plus the lower shared account tables (`Balances`, `Positions`, `Order History`) without changing the existing wide-screen composition. The visible proof is that the header remains above the trade content, the footer stays fixed at the bottom, and the trade route itself becomes the scroll owner at `xl` heights where the app shell is intentionally scroll-locked.

This work is tracked by `bd` issue `hyperopen-qda4`.

## Progress

- [x] (2026-03-14 12:33 EDT) Confirmed the current trade shell uses app-root `xl:overflow-y-hidden` while `/trade` itself does not own desktop scrolling, and confirmed the fixed-height `account-info-view` contract remains `h-96`.
- [x] (2026-03-14 12:33 EDT) Created and claimed `bd` bug `hyperopen-qda4` for this regression.
- [x] (2026-03-14 12:35 EDT) Implemented the `/trade` route scroll ownership change in `/hyperopen/src/hyperopen/views/trade_view.cljs` and added `xl` trade-grid minimum height so the desktop account row can move below the fold instead of being compressed inside the viewport.
- [x] (2026-03-14 12:35 EDT) Updated `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs` to prove the new trade-route scroll owner while preserving the app-root trade scroll lock expectation.
- [x] (2026-03-14 12:43 EDT) Verified `/trade` on `1280x720`, `1440x760`, and `1440x900` using browser-inspection and wrote `/hyperopen/docs/qa/desktop-trade-short-height-scroll-qa-2026-03-14.md`.
- [x] (2026-03-14 12:44 EDT) Ran `npm run check`, `npm test`, and `npm run test:websocket` against the final code and docs.
- [x] (2026-03-14 12:44 EDT) Closed `hyperopen-qda4` and moved this ExecPlan to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: the existing test suite already locks in the old shell behavior.
  Evidence: `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs` asserts app-root includes `xl:overflow-y-hidden`, so the fix must add route-level scrolling rather than removing the shell lock.

- Observation: the lower account surface is intentionally fixed-height and should not be resized as part of this change.
  Evidence: `/hyperopen/src/hyperopen/views/account_info_view.cljs` renders the account tables container with `h-96`, and `/hyperopen/test/hyperopen/views/account_info_view_test.cljs` asserts that contract directly.

- Observation: moving scroll ownership to `trade-root` was necessary but not sufficient by itself, because the `xl` grid still compressed the lower account row into the viewport height.
  Evidence: the first browser-inspection pass showed `trade-root` scrolling, but the real `account-tables` surface remained clipped until the grid was given an `xl` minimum height matching its row minimums. The final measurement file `/hyperopen/tmp/browser-inspection/desktop-trade-short-height-scroll-2026-03-14.json` shows the corrected `384px` account-table surface becoming fully visible after scroll.

## Decision Log

- Decision: keep `/hyperopen/src/hyperopen/views/app_view.cljs` unchanged and move desktop scroll ownership into `/hyperopen/src/hyperopen/views/trade_view.cljs`.
  Rationale: the user explicitly wants header and footer behavior preserved, and the existing tests/documentation already treat the trade app shell as scroll-locked at `xl`.
  Date/Author: 2026-03-14 / Codex

- Decision: treat this as a trade-only fix.
  Rationale: `/portfolio` already owns its own route scrolling, and the user-reported cut-off surfaces are trade-shell surfaces.
  Date/Author: 2026-03-14 / Codex

- Decision: add an `xl` grid minimum height in `/hyperopen/src/hyperopen/views/trade_view.cljs` instead of changing the shared `account-info-view` height contract.
  Rationale: the lower account surface must remain `h-96` everywhere it is reused. The safe fix is to let the desktop trade grid honor its own row minimums and scroll under the fold when the shell is shorter than that full composition.
  Date/Author: 2026-03-14 / Codex

## Outcomes & Retrospective

This work completed successfully. `/trade` now keeps the app-shell desktop lock at `xl`, but the route itself owns vertical scrolling and the desktop grid has enough minimum height for the right-rail summary and lower account tables to move below the fold instead of being clipped inside the viewport.

The browser verification showed the intended before/after behavior at all requested desktop heights:

- `1280x720`
- `1440x760`
- `1440x900`

In each case, `trade-root` reported `scrollHeight > clientHeight`, the header remained fixed above the content band, the footer stayed fixed at the bottom, and both the desktop summary rail and the `account-tables` surface became fully visible after scrolling. The QA record is `/hyperopen/docs/qa/desktop-trade-short-height-scroll-qa-2026-03-14.md`, with raw browser-inspection measurements in `/hyperopen/tmp/browser-inspection/desktop-trade-short-height-scroll-2026-03-14.json`.

This reduced overall complexity. The final fix stays local to the trade view and its existing shell test, instead of introducing a new layout mode or changing the shared account-table component contract.

## Context and Orientation

The trade page shell is composed in `/hyperopen/src/hyperopen/views/trade_view.cljs`. Its top-level node is `trade-root`, which currently fills available space inside the app shell but does not own vertical scrolling on desktop. The global app shell is composed in `/hyperopen/src/hyperopen/views/app_view.cljs`; on `/trade` it intentionally adds `xl:overflow-y-hidden` so the whole page does not scroll once the layout reaches the desktop `xl` breakpoint. That app-shell lock is deliberate because the header and fixed footer should remain stable.

The desktop trade layout has three major vertical regions that matter here: the chart and orderbook grid, the right-rail order-entry column with the desktop `account-equity-view`, and the lower account-table surface rendered by `/hyperopen/src/hyperopen/views/account_info_view.cljs`. The account-table surface is a shared component and intentionally uses `h-96`, meaning a fixed 24rem height. This work must not change that contract.

The relevant regression tests live in `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`. That file already proves the app-root trade scroll lock and is the right place to add assertions for the new `trade-root` scroll ownership. The shared account-table fixed-height contract is separately tested in `/hyperopen/test/hyperopen/views/account_info_view_test.cljs` and should remain untouched.

## Plan of Work

Change the top-level `trade-root` node in `/hyperopen/src/hyperopen/views/trade_view.cljs` so it keeps its current flex/min-height structure but adds desktop-only vertical scrolling classes. The intended class shape is additive: preserve existing `flex-1`, `flex`, `flex-col`, and `min-h-0`, then add `xl:overflow-y-auto` plus the existing hidden-scrollbar treatment so the scroll behavior only activates on desktop where the app shell is locked.

Do not modify `/hyperopen/src/hyperopen/views/app_view.cljs`. The header/footer positioning depends on the current `xl:overflow-y-hidden` rule at the app-root level, and the fix should work by making the trade route content scroll inside that shell.

Update `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs` in the existing trade layout coverage. The tests must continue to assert that app-root contains `xl:overflow-y-hidden`, and they must newly assert that `trade-root` contains `xl:overflow-y-auto` and `scrollbar-hide`. Do not weaken the current app-shell assertions.

After the code and test changes are in place, run the required repository gates from `/hyperopen`: `npm run check`, `npm test`, and `npm run test:websocket`. Then verify the behavior manually in the browser on `/trade` at short desktop sizes, specifically `1280x720` and `1440x760`, and confirm that `1440x900` still looks unchanged. Record the manual results in a short QA note under `/hyperopen/docs/qa/`.

## Concrete Steps

Run all commands from `/hyperopen`.

1. Edit `/hyperopen/src/hyperopen/views/trade_view.cljs` so `trade-root` owns vertical scrolling at `xl`.
2. Edit `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs` to assert the new `trade-root` classes and the unchanged app-root lock.
3. Run:

   npm run check
   npm test
   npm run test:websocket

4. Run the local app and manually verify `/trade` at `1280x720`, `1440x760`, and `1440x900`.
5. Write the QA note in `/hyperopen/docs/qa/`.
6. Close `hyperopen-qda4` and move this plan to `/hyperopen/docs/exec-plans/completed/`.

## Validation and Acceptance

Acceptance is behavior-based.

On `/trade` at `1280x720` and `1440x760`, wheel or trackpad scrolling inside the trade route must move the content so the right-rail `Unified Account Summary` and the lower account tables become reachable. The header must remain above the content, and the footer must remain fixed at the bottom. At `1440x900`, the page should preserve the same visual composition as before, with no new structural reflow.

Automated validation requires:

- `npm run check`
- `npm test`
- `npm run test:websocket`

Test acceptance requires a trade-shell view test that proves `trade-root` owns desktop scroll while `app-root` still owns the trade `xl` scroll lock. Final validation passed, and the browser QA note is `/hyperopen/docs/qa/desktop-trade-short-height-scroll-qa-2026-03-14.md`.

## Idempotence and Recovery

The code change is additive and local to the trade shell. Re-running the tests or browser checks is safe. If the route-level scroll change causes an unexpected desktop regression, the lowest-risk recovery is to revert only the `trade-root` class change and its companion test assertions while leaving the app-root shell lock untouched.

## Artifacts and Notes

The key proof artifacts for this change are:

- updated trade-shell and app-shell view tests
- passing outputs from `npm run check`, `npm test`, and `npm run test:websocket`
- `/hyperopen/docs/qa/desktop-trade-short-height-scroll-qa-2026-03-14.md`
- `/hyperopen/tmp/browser-inspection/desktop-trade-short-height-scroll-2026-03-14.json`

## Interfaces and Dependencies

No public API, action, schema, or type changes are needed. This work stays inside the existing view layer and test layer:

- `/hyperopen/src/hyperopen/views/trade_view.cljs`
- `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs`
- `/hyperopen/docs/qa/` for the manual QA note

Plan revision note: 2026-03-14 12:33 EDT - Created the active ExecPlan after claiming `hyperopen-qda4`, documenting the route-level scroll strategy and the requirement to preserve the existing app-shell lock and shared account-table height contract.
Plan revision note: 2026-03-14 12:44 EDT - Completed the implementation, validation, browser QA, and closeout; recorded the additional `xl` grid minimum-height discovery and moved the plan to `completed`.
