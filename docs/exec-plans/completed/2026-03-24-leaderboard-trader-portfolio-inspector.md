# Implement Trader Portfolio Inspection From The Leaderboard

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is governed by `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The tracked work item for this plan is `hyperopen-vlle`; the `bd` issue is historical local tracker context for this plan.

## Purpose / Big Picture

After this change, clicking a trader on `/leaderboard` should no longer throw the user out to Hyperliquid’s explorer immediately. Instead, Hyperopen should open a dedicated trader portfolio inspection route that looks and behaves like the existing `/portfolio` page, but the inspected account is the clicked trader’s address rather than the connected wallet or a sticky Spectate Mode session.

The user-visible result should be easy to prove. Open `/leaderboard`, click a trader row, and land on a portfolio-style page for that trader with read-only framing and a separate button that still opens the Hyperliquid explorer address page. Leave that route and the inspected account should stop affecting the rest of the app because no global spectate state was turned on.

## Progress

- [x] (2026-03-24 16:25Z) Created and claimed `bd` issue `hyperopen-vlle` for leaderboard trader portfolio inspection.
- [x] (2026-03-24 16:26Z) Audited the current seams: leaderboard rows currently wrap trader names in direct Hyperliquid explorer links, `/portfolio` is a deferred route module, and portfolio data loading already keys off `account-context/effective-account-address`.
- [x] (2026-03-24 16:39Z) Implemented the portfolio inspection route parsing, route-derived inspected-account context, and portfolio read-only framing without relying on Spectate Mode state.
- [x] (2026-03-24 16:41Z) Rewired leaderboard trader interactions so the main click opens the in-app inspection route and a separate explicit control opens Hyperliquid explorer.
- [x] (2026-03-24 16:50Z) Added focused regression coverage, route smoke coverage, governed browser-QA targets for the new route slice, and completed the required validation commands.

## Surprises & Discoveries

- Observation: the existing portfolio bootstrap path already follows `account-context/effective-account-address` across route loading, startup bootstrap, websocket subscriptions, and account-surface projections.
  Evidence: `/hyperopen/src/hyperopen/startup/runtime.cljs`, `/hyperopen/src/hyperopen/startup/collaborators.cljs`, `/hyperopen/src/hyperopen/account/surface_service.cljs`, and `/hyperopen/src/hyperopen/wallet/address_watcher.cljs` all key account fetch/subscription behavior off `effective-account-address`.

- Observation: using Spectate Mode as the implementation seam would keep too much global state alive outside the intended page.
  Evidence: `/hyperopen/src/hyperopen/account/spectate_mode_actions.cljs` writes modal/search/watchlist state, flips `[:account-context :spectate-mode :active?]`, and persists the mode through route navigation until the user explicitly stops it.

- Observation: the leaderboard already has a dedicated module and deterministic VM, so the trader click behavior can be changed locally in the leaderboard view without reopening the route baseline architecture.
  Evidence: `/hyperopen/src/hyperopen/views/leaderboard_view.cljs`, `/hyperopen/src/hyperopen/views/leaderboard/vm.cljs`, and `/hyperopen/src/hyperopen/leaderboard/actions.cljs`.

- Observation: the governed browser design-review tool did not initially know how to inspect either the new trader portfolio route or the leaderboard route directly.
  Evidence: `/hyperopen/tools/browser-inspection/config/design-review-routing.json` originally shipped with `trade-route`, `portfolio-route`, `vaults-route`, `vault-detail-route`, and `api-wallets-route` only, so UI signoff for this feature needed new permanent targets instead of temporary routing hacks.

## Decision Log

- Decision: add a child portfolio route that encodes the inspected trader address in the path instead of adding more Spectate Mode behavior.
  Rationale: the route path naturally scopes the inspection session to one page and lets the inspected address disappear when the user leaves the route, which directly addresses the sticky-state problem called out in the request.
  Date/Author: 2026-03-24 / Codex

- Decision: drive portfolio inspection through a route-derived account override that participates in existing portfolio bootstrap and websocket refresh flows.
  Rationale: the current account surfaces already fetch and render based on `effective-account-address`. Reusing that read path avoids building a second portfolio data pipeline just for trader inspection.
  Date/Author: 2026-03-24 / Codex

- Decision: make the trader inspection route explicitly read-only in UI framing even though it reuses the portfolio layout.
  Rationale: a portfolio page for another trader should not present cash-movement or trade-mutation affordances as if they were acting on the user’s own wallet. The layout should stay familiar, but the state should be honest.
  Date/Author: 2026-03-24 / Codex

## Outcomes & Retrospective

The implementation delivered the intended “portfolio without sticky spectate mode” behavior. Clicking a trader on `/leaderboard` now navigates to `/portfolio/trader/<address>`, where the app reuses the portfolio surface against a route-scoped inspected address, frames the page as read-only, and keeps the old Hyperliquid explorer destination available as a separate button. Leaving the route clears the inspection context naturally because no Spectate Mode state was activated.

Validation passed on the changed surface and at the repo level. Focused CLJS coverage for routing, account context, startup bootstrap, app-shell framing, leaderboard interactions, and portfolio mode selection all passed. The route smoke Playwright case for the new trader portfolio route passed at desktop and mobile widths. `npm test` passed repo-wide, `npm run test:websocket` passed, the governed browser design-review run passed for both `/leaderboard` and `/portfolio/trader` across `375`, `768`, `1280`, and `1440`, and `npm run check` passed after moving a separate already-finished ExecPlan out of `active/` so the docs lint could succeed again.

The final implementation reduced complexity overall. Instead of teaching Spectate Mode one more sticky workflow, the app now has a pure portfolio-route helper plus a route-scoped account override that plugs into the existing portfolio/account fetch pipeline. The only additive complexity beyond the user-visible feature was permanent browser-QA routing for the new UI surfaces, which is a net positive because future leaderboard and trader-portfolio changes can now use the governed design-review flow directly.

## Context and Orientation

The relevant behavior spans four repository areas.

Routing and deferred page loading live in `/hyperopen/src/hyperopen/router.cljs`, `/hyperopen/src/hyperopen/route_modules.cljs`, and `/hyperopen/src/hyperopen/views/app_view.cljs`. The portfolio page already uses the deferred route-module path, so the new trader inspection route should stay inside that same module instead of creating a second standalone page bundle.

Portfolio rendering lives in `/hyperopen/src/hyperopen/views/portfolio_view.cljs` and `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`. The view already assembles the top action row, metric cards, chart, performance metrics, and account table tabs. The goal is not to invent a new analytics page; it is to reuse this structure while changing which account is being inspected and which actions are safe to show.

Account identity lives in `/hyperopen/src/hyperopen/account/context.cljs`. Today `effective-account-address` means “Spectate Mode address when active, otherwise wallet owner address.” That value already drives account bootstrap, account-history requests, and websocket subscriptions. A route-derived trader inspection address can plug into the same seam if the logic remains deterministic and clearly scoped to the new route shape.

Leaderboard rendering lives in `/hyperopen/src/hyperopen/views/leaderboard_view.cljs` and `/hyperopen/src/hyperopen/views/leaderboard/vm.cljs`. Right now trader names are direct external links to `https://app.hyperliquid.xyz/explorer/address/<address>`. That needs to become an internal navigation target, with a separate explicit external-link control preserving the current explorer behavior.

## Plan of Work

First, add a small portfolio route parser namespace that can answer three questions deterministically from a path string: whether the path is any portfolio route, whether it is a trader-inspection route, and which normalized address is being inspected. Use that parser from route-module selection and anywhere else that currently checks `str/starts-with? "/portfolio"` so the route behavior stays centralized.

Second, update `/hyperopen/src/hyperopen/account/context.cljs` so account identity can distinguish three states: the wallet owner, a Spectate Mode session, and a route-scoped trader inspection session. `effective-account-address` should prefer the inspected trader address when the current route is the new portfolio-inspection route. Mutation policy must also become route-aware so this page is read-only without pretending Spectate Mode is active globally. Keep owner-address behavior unchanged for the rest of the app.

Third, update `/hyperopen/src/hyperopen/views/portfolio_view.cljs` so the same portfolio layout can render in two modes. The normal `/portfolio` route should keep its existing top action row and deposits/withdrawals tab. The new trader-inspection route should replace that top strip with read-only framing, show the inspected address (and any known leaderboard display name when available), include a Hyperliquid explorer button, and suppress the cash-movement tab and action buttons that would be misleading on a foreign account.

Fourth, update `/hyperopen/src/hyperopen/views/leaderboard_view.cljs` so clicking the trader opens the internal portfolio inspection route. Add a small adjacent explorer button for both desktop rows and mobile cards so the old external destination remains available without being the primary click target.

Fifth, extend focused tests for route parsing, account context, startup/address bootstrap, leaderboard view wiring, and portfolio view mode selection. Then run the smallest relevant Playwright route smoke first, followed by the required repository gates.

## Concrete Steps

Work from `/hyperopen`.

1. Add a new route helper namespace under:

    `/hyperopen/src/hyperopen/portfolio/routes.cljs`

2. Update the route/account-context seams in:

    `/hyperopen/src/hyperopen/account/context.cljs`
    `/hyperopen/src/hyperopen/route_modules.cljs`
    `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`
    `/hyperopen/src/hyperopen/startup/runtime.cljs`

3. Update the portfolio and leaderboard views in:

    `/hyperopen/src/hyperopen/views/portfolio_view.cljs`
    `/hyperopen/src/hyperopen/views/leaderboard_view.cljs`
    `/hyperopen/src/hyperopen/views/leaderboard/vm.cljs` if display-name or button-visibility derivations need a view-model seam

4. Add or update focused tests in:

    `/hyperopen/test/hyperopen/account/context_test.cljs`
    `/hyperopen/test/hyperopen/route_modules_test.cljs`
    `/hyperopen/test/hyperopen/runtime/action_adapters_test.cljs`
    `/hyperopen/test/hyperopen/startup/runtime_test.cljs`
    `/hyperopen/test/hyperopen/views/leaderboard_view_test.cljs`
    `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`
    `/hyperopen/test/hyperopen/router_test.cljs` if route normalization helpers need new assertions

5. Run validation commands:

    cd /hyperopen
    npm test -- --test=hyperopen.account.context-test,hyperopen.route-modules-test,hyperopen.runtime.action-adapters-test,hyperopen.startup.runtime-test,hyperopen.views.leaderboard-view-test,hyperopen.views.portfolio-view-test
    npm run test:playwright:smoke
    npm run test:websocket
    npm run check

If repo-wide `npm test` is still required after focused checks, run it and record any unrelated standing failures honestly in this document.

## Validation and Acceptance

Acceptance is behavior, not just code edits.

On a running local app, visiting `/leaderboard` and clicking a trader row must navigate to a portfolio-style page for that trader instead of immediately opening Hyperliquid explorer. That page must visibly identify that it is inspecting another trader, keep the familiar portfolio layout, and expose a separate button that opens the Hyperliquid explorer address page in a new tab. Navigating away from that route must stop inspecting the trader because no sticky Spectate Mode session was created.

The trader inspection route must bootstrap portfolio/account data for the address encoded in the path, even when no wallet is connected, because it is a read-only public-inspection surface. Mutation affordances that would mislead the user on a foreign account must either be hidden on that route or blocked by the route-aware mutation policy with clear read-only semantics.

The minimum automated acceptance is:

    cd /hyperopen
    npm test -- --test=hyperopen.account.context-test,hyperopen.route-modules-test,hyperopen.runtime.action-adapters-test,hyperopen.startup.runtime-test,hyperopen.views.leaderboard-view-test,hyperopen.views.portfolio-view-test
    npm run test:websocket
    npm run check

The minimum browser acceptance is:

- a deterministic browser smoke that still proves the route loads cleanly
- explicit reporting of whether governed browser QA was run, blocked, or deferred for this route slice

## Idempotence and Recovery

All work is additive tracked-file editing and is safe to repeat. If the inspected-address route causes unexpected account-surface churn, the safest rollback is to remove the route-derived account override while leaving the pure route parser and leaderboard navigation tests in place so the intended behavior remains documented. If UI read-only framing lands before all affordance cleanup does, keep the route functional and record the residual affordance debt explicitly rather than reverting the core navigation behavior.

## Artifacts and Notes

Key local reference files for this work:

- `/hyperopen/src/hyperopen/views/leaderboard_view.cljs`
- `/hyperopen/src/hyperopen/views/portfolio_view.cljs`
- `/hyperopen/src/hyperopen/account/context.cljs`
- `/hyperopen/src/hyperopen/startup/runtime.cljs`
- `/hyperopen/src/hyperopen/wallet/address_watcher.cljs`

The current external explorer destination pattern that must remain available as a secondary action is:

    https://app.hyperliquid.xyz/explorer/address/<address>

## Interfaces and Dependencies

No new third-party libraries are needed.

The implementation should end with a stable pure route helper interface in `/hyperopen/src/hyperopen/portfolio/routes.cljs` that exposes path parsing and path construction for trader inspection routes.

`/hyperopen/src/hyperopen/account/context.cljs` should still own the public account-identity helpers used across the app, but those helpers must become aware of route-scoped trader inspection without breaking Spectate Mode.

`/hyperopen/src/hyperopen/views/portfolio_view.cljs` should keep exporting `route_view` for the existing portfolio deferred module. The trader inspection page should reuse that same route export and module id rather than creating a new bundle.

Revision note: created on 2026-03-24 to implement `hyperopen-vlle` as an active, self-contained plan for leaderboard trader portfolio inspection without sticky Spectate Mode state.
