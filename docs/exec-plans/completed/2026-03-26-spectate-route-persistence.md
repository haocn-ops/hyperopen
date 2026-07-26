# Preserve Spectate Mode Across Internal Route Navigation

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The primary live `bd` issue is `hyperopen-h4qk`.

## Purpose / Big Picture

Spectate Mode is meant to be sticky read-only account context. Once a user starts spectating another address, that mode should persist until the user explicitly stops it. Today, navigating away from a spectated `/trade` route to `/portfolio` can drop Spectate Mode because some internal links still emit plain route `href`s such as `/portfolio` instead of a spectate-aware browser URL. If the browser follows that plain `href`, the app reloads without a `?spectate=` query and startup restores the normal owner account instead of the spectated account. After this fix, internal route links must preserve the active spectate address in the browser URL for normal routes, while still suppressing the query for trader portfolio inspection routes such as `/portfolio/trader/<address>`.

## Progress

- [x] (2026-03-26 13:09Z) Created and claimed `hyperopen-h4qk` for the Spectate Mode route-persistence regression.
- [x] (2026-03-26 13:16Z) Traced the current route flow through `/hyperopen/src/hyperopen/account/spectate_mode_links.cljs`, `/hyperopen/src/hyperopen/runtime/action_adapters/navigation.cljs`, `/hyperopen/src/hyperopen/views/header/vm.cljs`, and `/hyperopen/src/hyperopen/views/header/navigation.cljs`.
- [x] (2026-03-26 13:20Z) Confirmed the action-driven navigation path already preserves `?spectate=` in `/hyperopen/src/hyperopen/runtime/action_adapters/navigation.cljs`, but multiple rendered internal anchors still use plain route `href`s.
- [x] (2026-03-26 13:33Z) Added a shared pure helper in `/hyperopen/src/hyperopen/account/spectate_mode_links.cljs` so both SPA navigation and rendered internal links resolve through the same spectate-aware browser-path rules.
- [x] (2026-03-26 13:37Z) Wired affected internal anchors through the shared helper in `/hyperopen/src/hyperopen/views/header/vm.cljs`, `/hyperopen/src/hyperopen/views/funding_comparison_view.cljs`, and `/hyperopen/src/hyperopen/views/vaults/list_view.cljs`, while keeping trader portfolio route suppression intact.
- [x] (2026-03-26 13:40Z) Added focused regression coverage in `/hyperopen/test/hyperopen/account/spectate_mode_links_test.cljs`, `/hyperopen/test/hyperopen/views/header/vm_test.cljs`, and `/hyperopen/test/hyperopen/views/header_view_test.cljs`.
- [x] (2026-03-26 13:44Z) Added deterministic browser coverage in `/hyperopen/tools/playwright/test/portfolio-regressions.spec.mjs` for the exact user path: spectated trade route -> desktop Portfolio nav -> spectate state preserved on `/portfolio`.
- [x] (2026-03-26 13:45Z) Ran `npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "spectate"` and confirmed the new regression passed on the patched tree.
- [x] (2026-03-26 13:58Z) Ran the required repository gates on the final tree: `npm test`, `npm run test:websocket`, and `npm run check`; all passed.
- [x] (2026-03-26 13:29Z) Moved this ExecPlan from `/hyperopen/docs/exec-plans/active/` to `/hyperopen/docs/exec-plans/completed/` after closing `hyperopen-h4qk` and rerunning the final validation on the completed tree.

## Surprises & Discoveries

- Observation: the `:actions/navigate` path was already correct.
  Evidence: `/hyperopen/src/hyperopen/runtime/action_adapters/navigation.cljs` builds the browser path with `spectate-url-path` and appends the active spectate address for normal routes.

- Observation: the regression seam is broader than one click target.
  Evidence: plain internal anchors currently exist in `/hyperopen/src/hyperopen/views/header/vm.cljs`, `/hyperopen/src/hyperopen/views/funding_comparison_view.cljs`, and `/hyperopen/src/hyperopen/views/vaults/list_view.cljs`, so a header-only fix would leave the same hard-navigation bug available elsewhere.

- Observation: the first red-state browser regression failed exactly where the code audit predicted.
  Evidence: the implementation sidecar reported `href="/portfolio"` on the desktop Portfolio link before the patch, and the final Playwright regression now asserts the corrected `href="/portfolio?spectate=<address>"`.

## Decision Log

- Decision: treat spectate-aware browser `href` generation as the canonical fix, not a click-handler-only workaround.
  Rationale: preserving the correct `href` protects normal clicks, open-in-new-tab, middle-click, and any browser-driven navigation path that bypasses the SPA action handler.
  Date/Author: 2026-03-26 / Codex

- Decision: keep trader portfolio routes (`/portfolio/trader/<address>`) exempt from the spectate query.
  Rationale: trader portfolio inspection is its own explicit route-owned read-only mode, and the existing navigation behavior already suppresses `?spectate=` there.
  Date/Author: 2026-03-26 / Codex

- Decision: fix all currently known internal hard-navigation anchors in the same pass instead of only the reported Portfolio header link.
  Rationale: the user requirement is that Spectate Mode stays active until explicitly disabled. Leaving trade-market or vault-detail anchors on plain `href`s would preserve the anti-pattern in adjacent flows.
  Date/Author: 2026-03-26 / Codex

## Outcomes & Retrospective

The patch achieved the intended user-visible outcome: Spectate Mode now persists when moving from a spectated trade route into `/portfolio` through the desktop header navigation, and the browser URL keeps the `spectate` query instead of dropping back to the owner-account route. The same helper now also protects the known internal trade-market and vault-detail anchors from the same hard-navigation seam.

Overall complexity went down. Before the change, SPA navigation and browser-native navigation used different route rules, which meant the reducer path was correct but the rendered `href` path was wrong. After the change, both flows share `/hyperopen/src/hyperopen/account/spectate_mode_links.cljs` as the route-authority seam for spectate-aware internal browser paths.

## Context and Orientation

`/hyperopen/src/hyperopen/account/spectate_mode_links.cljs` currently owns low-level helpers for parsing and writing the `spectate` query parameter. It knows how to turn a path such as `/portfolio` plus an address into `/portfolio?spectate=<address>`.

`/hyperopen/src/hyperopen/runtime/action_adapters/navigation.cljs` already uses those helpers correctly. When `:actions/navigate` receives a target path, it writes `[:router :path]`, then pushes or replaces a browser URL that preserves the active spectate address unless the destination is a trader portfolio route.

The gap is in rendered links. `/hyperopen/src/hyperopen/views/header/vm.cljs` currently assigns plain `:href` values from each nav item’s `:route`. `/hyperopen/src/hyperopen/views/funding_comparison_view.cljs` renders direct `/trade/<coin>` anchors. `/hyperopen/src/hyperopen/views/vaults/list_view.cljs` renders direct vault detail anchors. Those `href`s do not currently include the active spectate query, so if the browser follows them directly the app boots into the owner account context.

The route exception is `/hyperopen/src/hyperopen/portfolio/routes.cljs`. `trader-portfolio-route?` returns true for `/portfolio/trader/<address>`, and those routes must continue to suppress the spectate query because they are already explicit read-only inspection routes.

## Plan of Work

Add one pure helper that takes application state plus a target path and returns the correct browser path for internal navigation. Put it near the existing spectate query helpers in `/hyperopen/src/hyperopen/account/spectate_mode_links.cljs` so both runtime navigation and rendered links can share the same decision logic. The helper must normalize the path, preserve `?spectate=<address>` when Spectate Mode is active, and suppress the query for trader portfolio routes. Completed as `internal-route-href` plus the lower-level `spectate-navigation-path`.

Update `/hyperopen/src/hyperopen/runtime/action_adapters/navigation.cljs` to call the shared helper instead of keeping its own local copy of that logic. Completed.

Update rendered internal links to use the shared helper. At minimum, wire `/hyperopen/src/hyperopen/views/header/vm.cljs` so desktop and More-menu anchors expose spectate-aware `:href`s, `/hyperopen/src/hyperopen/views/funding_comparison_view.cljs` so trade-market anchors preserve spectating, and `/hyperopen/src/hyperopen/views/vaults/list_view.cljs` so vault detail links preserve spectating. Completed.

Add unit coverage in `/hyperopen/test/hyperopen/account/spectate_mode_links_test.cljs` for the shared helper, including normal routes, nested trade routes, and trader portfolio suppression. Add a view-model or rendered-view regression in `/hyperopen/test/hyperopen/views/header/vm_test.cljs` or `/hyperopen/test/hyperopen/views/header_view_test.cljs` that proves the portfolio desktop nav link exposes `/portfolio?spectate=<address>` while Spectate Mode is active. Completed in both header VM and rendered header tests.

Add a Playwright regression in `/hyperopen/tools/playwright/test/portfolio-regressions.spec.mjs` that starts on `/trade?spectate=<address>`, clicks the desktop Portfolio nav link, and verifies that the app lands on `/portfolio`, still shows the Spectate Mode banner, and keeps the `spectate` query in the browser URL. Completed.

## Concrete Steps

From `/Users/barry/projects/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/account/spectate_mode_links.cljs` to add a shared spectate-aware internal-route helper, and update `/hyperopen/src/hyperopen/runtime/action_adapters/navigation.cljs` to consume it.
2. Edit `/hyperopen/src/hyperopen/views/header/vm.cljs`, `/hyperopen/src/hyperopen/views/funding_comparison_view.cljs`, and `/hyperopen/src/hyperopen/views/vaults/list_view.cljs` so internal anchors use the helper-generated `href`.
3. Update `/hyperopen/test/hyperopen/account/spectate_mode_links_test.cljs` and `/hyperopen/test/hyperopen/views/header/vm_test.cljs` with focused regression coverage.
4. Update `/hyperopen/tools/playwright/test/portfolio-regressions.spec.mjs` with a stable route-navigation regression.
5. Run the smallest relevant browser command first, then the required gates:

       npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "spectate"
       npm run check
       npm test
       npm run test:websocket

## Validation and Acceptance

Acceptance is behavioral:

- Starting from a spectated trade route, clicking the desktop Portfolio nav must land on `/portfolio` without disabling Spectate Mode.
- The resulting browser URL must still contain `?spectate=<address>`.
- The Spectate Mode banner in `/hyperopen/src/hyperopen/views/app_view.cljs` must remain visible after the navigation.
- Internal link `href`s for other normal routes such as trade-market and vault-detail links must also preserve the active spectate query.
- Trader portfolio routes such as `/portfolio/trader/<address>` must continue to suppress the spectate query.

Executed validation commands for this task:

    npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "spectate"
    npm test
    npm run test:websocket
    npm run check

## Idempotence and Recovery

These edits are safe to re-run because they only change pure browser-path generation and the `href`s that consume it. If a patch accidentally adds `?spectate=` to trader portfolio routes, revert only the helper callsites or suppression branch and rerun the unit and Playwright coverage. Do not clear unrelated user state or broaden the spectate query to explicit trader-inspection routes.

## Artifacts and Notes

Key implemented seams:

    /hyperopen/src/hyperopen/runtime/action_adapters/navigation.cljs
    /hyperopen/src/hyperopen/account/spectate_mode_links.cljs
    /hyperopen/src/hyperopen/views/header/vm.cljs
    /hyperopen/src/hyperopen/views/funding_comparison_view.cljs
    /hyperopen/src/hyperopen/views/vaults/list_view.cljs

The critical failing behavior was not that state reducers called `stop-spectate-mode`; it was that plain internal `href`s could bypass the reducer path entirely and reload the app without a spectate query.

Plan update note: completed the plan after implementing the shared-helper approach, broadening the fix from the reported Portfolio header link to the other known internal route anchors that shared the same hard-navigation seam.
