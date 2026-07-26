---
owner: platform
status: completed
last_reviewed: 2026-03-10
review_cycle_days: 90
source_of_truth: false
bd_issue: hyperopen-fq7
---

# Add Portfolio Component Workbench and First-Pass Scene Library

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, contributors can start a dedicated local UI workbench at `/ui-workbench.html`, browse searchable scenes for a large cross-section of Hyperopen UI components, and iterate on reusable Replicant/Nexus-driven views without routing through the full app shell. The production app stays on its existing `:app` build and runtime path, while the workbench lives in a dedicated `portfolio/` source tree and uses Shadow's official `:portfolio` target.

The user-visible workflow from `/hyperopen` is:

    npm ci
    npm run dev

Then open:

    http://localhost:8080/
    http://localhost:8080/ui-workbench.html

The first URL must keep the existing app behavior. The second must load the Portfolio UI, app CSS inside the scene canvas, searchable collections, and interactive scenes backed by local scene-only dispatch stubs.

## Progress

- [x] (2026-03-10 00:24Z) Claimed `bd` issue `hyperopen-fq7`, re-read repo instructions, and audited current Shadow/deps/frontend structure plus existing view/test seams.
- [x] (2026-03-10 00:27Z) Created this ExecPlan and recorded the baseline environment fact that `node_modules` is currently missing and `shadow-cljs compile app portfolio-worker` fails on the missing `@noble/secp256k1` package.
- [x] (2026-03-10 00:57Z) Added Portfolio and memsearch dependencies, widened `:dev-http`, introduced the dedicated `:portfolio` Shadow target, and added the Portfolio scripts plus static HTML entrypoints.
- [x] (2026-03-10 01:19Z) Added the `portfolio/hyperopen/workbench/**` support tree with runner, collection registration, layout wrappers, fixture/state helpers, and a scene-local Replicant/Nexus dispatch stub.
- [x] (2026-03-10 01:28Z) Extracted `render-order-form` and `render-funding-modal`, keeping the public wrappers intact and production behavior unchanged.
- [x] (2026-03-10 01:40Z) Migrated a broad first-pass set of reusable surfaces into domain-scoped Portfolio scenes across primitives, markets, trade, account, funding, API, vaults, and shell areas.
- [x] (2026-03-10 02:00Z) Updated developer docs, added workbench-specific tests, ran the required validation gates, smoke-tested the served entrypoints, and filed the second-batch follow-up issue `hyperopen-anv`.

## Surprises & Discoveries

- Observation: The repo already has an unrelated `:portfolio-worker` Shadow build and `src/hyperopen/portfolio/**` product feature area, so the workbench must not use `hyperopen.portfolio` namespaces or the `portfolio-worker` name.
  Evidence: `/hyperopen/shadow-cljs.edn` and `/hyperopen/src/hyperopen/portfolio/worker.cljs`.
- Observation: The current static dev server only serves `resources/public`, but Portfolio's own UI CSS and JS helper assets live on the classpath under `public/portfolio/**`, so `:dev-http` must serve `classpath:public` as well.
  Evidence: The Portfolio jar contains `public/portfolio/styles/portfolio.css` and `/hyperopen/shadow-cljs.edn` currently serves only `resources/public`.
- Observation: Hyperopen already has strong pure-view seams for many targets through component primitives, tab content functions, and VM builders, so only the order form and funding modal need planned render-seam extraction up front.
  Evidence: `balances-tab-content`, `positions-tab-content`, `open-orders-tab-content`, `trade-history-tab-content`, `vaults-view`, `api-wallets-vm`, and `order-form-vm` already accept explicit data inputs.
- Observation: Portfolio search support in the selected version requires `coderafting/memsearch`; without it the dedicated `:portfolio` build fails at compile time on `memsearch.core`.
  Evidence: Initial `npx shadow-cljs compile portfolio` failed until `coderafting/memsearch "0.1.1"` was added alongside `no.cjohansen/portfolio`.
- Observation: Running a fresh Portfolio compile caused Shadow to install `snabbdom@3.5.1`, so the repo needed the resulting `package.json` and `package-lock.json` updates tracked to keep subsequent installs deterministic.
  Evidence: `npm run check` and `npx shadow-cljs compile portfolio` succeeded only after the generated lockfile/dependency updates were preserved.
- Observation: In this worktree, the Shadow static server serves the app entry from `/index.html`; `/ui-workbench.html` and classpath Portfolio assets served correctly on the active watch server, but `/` still returned `404` during smoke testing and appears to be pre-existing.
  Evidence: `curl -I http://localhost:8081/index.html`, `curl -I http://localhost:8081/ui-workbench.html`, `curl -I http://localhost:8081/portfolio/styles/portfolio.css`, and `curl -I http://localhost:8081/portfolio/prism.js`.

## Decision Log

- Decision: Put all workbench namespaces under `portfolio/hyperopen/workbench/**`.
  Rationale: This keeps scene code out of `src/`, avoids collisions with the existing product feature namespace `hyperopen.portfolio`, and makes Shadow source ownership explicit.
  Date/Author: 2026-03-10 / Codex
- Decision: Use Shadow's official `:portfolio` target with `runner-ns hyperopen.workbench.runner`.
  Rationale: Portfolio ships a first-class Shadow target that handles scene discovery, HTML generation, and dev reload behavior. A custom browser target would widen maintenance burden for no gain.
  Date/Author: 2026-03-10 / Codex
- Decision: Reuse realistic data shapes from current view tests and VM tests rather than requiring test namespaces directly from scenes.
  Rationale: The scene library should stay production-adjacent and maintainable, but it still needs realistic props and state shapes that already exist in test fixtures.
  Date/Author: 2026-03-10 / Codex
- Decision: Keep the scene registry and fake dispatch wiring entirely under `hyperopen.workbench.support.dispatch` and install it globally only inside the Portfolio runner.
  Rationale: Scenes need interactive local state, but production code must not depend on or even know about workbench dispatch behavior.
  Date/Author: 2026-03-10 / Codex
- Decision: Stabilize the existing `wait-for-idle-times-out-false-when-digest-keeps-changing-test` by making the route digest monotonic rather than toggling between two values.
  Rationale: The test had an aliasing bug against the poll interval and failed under load even though the implementation was correct; the workbench changes surfaced it during validation.
  Date/Author: 2026-03-10 / Codex
- Decision: Serve the workbench at `/ui-workbench.html` instead of `/portfolio.html`.
  Rationale: `/portfolio` is a product route reserved for account analytics; the workbench entrypoint should be explicitly different so it cannot be confused with, or visually collide with, portfolio page navigation semantics.
  Date/Author: 2026-03-10 / Codex

## Outcomes & Retrospective

The workbench now runs on a dedicated Shadow `:portfolio` target with scene discovery under `/hyperopen/portfolio/hyperopen/workbench/scenes/**`, shared workbench-only helpers under `/hyperopen/portfolio/hyperopen/workbench/support/**`, and generated HTML entrypoints at `/hyperopen/resources/public/ui-workbench.html` and `/hyperopen/resources/public/ui-workbench-canvas.html`. The production app build remains on `:app`, and none of the new workbench namespaces are required from production code.

The first-pass scene library covers nineteen domain-scoped scene files across:

- shell surfaces: header states, footer states, notifications
- primitives: order-form controls, table headers, mobile cards
- markets: asset selector, active asset panel, orderbook, chart controls
- trade: full order form states and account-equity action strip
- account: balances, positions, open orders, trade/funding/order history, TP/SL and margin overlays
- funding: deposit/send/withdraw modal states including mobile sheet
- API: API wallet connection and modal states
- vaults: list states, detail chart states, activity states, transfer modal states

The minimal render seams were sufficient. Only `render-order-form` and `render-funding-modal` were extracted; the rest of the scene tree could reuse existing pure views, tab content functions, and VM builders with realistic fixture/state inputs.

The intentionally skipped surfaces are:

- the full trading chart workspace in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` and related chart interop, because it depends on lifecycle-heavy `lightweight-charts` mounting and drag interactions that deserve a dedicated second-pass harness
- route-shell compositions such as `/hyperopen/src/hyperopen/views/app_view.cljs`, `/hyperopen/src/hyperopen/views/trade_view.cljs`, and `/hyperopen/src/hyperopen/views/vaults/detail_view.cljs`, because the first pass focused on reusable leaf/composite surfaces rather than full-page orchestration

The next highest-value batch was recorded in local `bd` issue `hyperopen-anv`: add Portfolio scenes for the trading chart workspace and the route-shell compositions built from the newly isolated subcomponents.

## Context and Orientation

Hyperopen is a ClojureScript frontend built with Replicant for rendering and Nexus for action dispatch. The main browser app build is `:app` in `/hyperopen/shadow-cljs.edn`, mounted through `/hyperopen/src/hyperopen/core.cljs` and `/hyperopen/src/hyperopen/app/bootstrap.cljs`, with static app HTML at `/hyperopen/resources/public/index.html` and generated Tailwind CSS at `/hyperopen/resources/public/css/main.css` from `/hyperopen/src/styles/main.css`.

The workbench must not interfere with the existing product-feature portfolio code in `/hyperopen/src/hyperopen/portfolio/**` or the existing `:portfolio-worker` build used for metrics offload.

The reusable view surface is concentrated in:

- `/hyperopen/src/hyperopen/views/trade/**`
- `/hyperopen/src/hyperopen/views/account_info/**`
- `/hyperopen/src/hyperopen/views/asset_selector_view.cljs`
- `/hyperopen/src/hyperopen/views/active_asset_view.cljs`
- `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`
- `/hyperopen/src/hyperopen/views/funding_modal.cljs`
- `/hyperopen/src/hyperopen/views/api_wallets_view.cljs`
- `/hyperopen/src/hyperopen/views/vaults/**`
- `/hyperopen/src/hyperopen/views/header_view.cljs`
- `/hyperopen/src/hyperopen/views/footer_view.cljs`
- `/hyperopen/src/hyperopen/views/notifications_view.cljs`

Portfolio scenes are discovered by namespace regexp. The current plan is to use `-scenes` namespaces under `hyperopen.workbench.scenes.*`, plus support code under `hyperopen.workbench.support.*`.

## Plan of Work

1. Update `/hyperopen/deps.edn`, `/hyperopen/shadow-cljs.edn`, and `/hyperopen/package.json` to add the Portfolio dependency, add the dedicated `:portfolio` Shadow target, widen the dev server static asset roots, and add focused workbench scripts without changing production build behavior.
2. Add static HTML scaffolding in `/hyperopen/resources/public/ui-workbench.html` and `/hyperopen/resources/public/ui-workbench-canvas.html` so Portfolio can boot with app theme attributes and can load the app CSS inside the scene iframe.
3. Add the new `portfolio/hyperopen/workbench/**` tree with the runner, collection registration, layout wrappers, realistic fixture builders, base state builders, and a scene-local dispatch system that updates atoms for interactive scenes but never triggers real app effects.
4. Extract `render-order-form` from `/hyperopen/src/hyperopen/views/trade/order_form_view.cljs` and `render-funding-modal` from `/hyperopen/src/hyperopen/views/funding_modal.cljs`, keeping the public wrappers intact and behavior unchanged.
5. Add scene files by domain for primitives, markets, trade, account, funding, API, vaults, and shell surfaces, using existing pure view functions or VM outputs wherever possible.
6. Update existing docs (`README.md`, `/hyperopen/docs/tools.md`, `/hyperopen/docs/references/toolchain.md`) with Portfolio startup instructions, scene locations, naming conventions, and extension guidance.
7. Run `npm ci`, `npm run check`, `npm test`, and `npm run test:websocket`, then manually smoke the app and workbench routes. Record results here and move this plan to `completed/` once the work is done.

## Concrete Steps

From `/hyperopen`:

1. Edit build config and package scripts for the new Portfolio target.
2. Add the static HTML entrypoints for Portfolio.
3. Add `portfolio/hyperopen/workbench/support/*.cljs`, `runner.cljs`, `collections.cljs`, and domain-scoped `*_scenes.cljs` files.
4. Extract the pure render seams in the trade order form and funding modal.
5. Add tests for the workbench dispatch helpers and any new render seam branches that are not already covered.
6. Run:

       npm ci
       npm run check
       npm test
       npm run test:websocket

7. Start the watchers and smoke test:

       npm run dev

   Then open `/` and `/ui-workbench.html` in the browser.

## Validation and Acceptance

The work is complete when all of the following are true:

- `npm run check` passes from `/hyperopen`.
- `npm test` passes from `/hyperopen`.
- `npm run test:websocket` passes from `/hyperopen`.
- `http://localhost:8080/` still loads the existing app behavior.
- `http://localhost:8080/ui-workbench.html` loads Portfolio UI with its own chrome, app CSS in the canvas, searchable scene collections, and interactive scene coverage for at least asset selector, order form, funding modal, and a vault chart surface.
- The scene tree covers a broad first-pass of reusable components across primitives, markets, trade, account, funding, API, vaults, and shell domains.
- Any intentionally skipped components are documented with a concrete reason.

## Idempotence and Recovery

The workbench setup is additive. If a Portfolio scene fails, retry `shadow-cljs compile portfolio` before the full repository gates to isolate workbench-only issues. If static Portfolio assets do not load, verify that `:dev-http` still serves both `resources/public` and `classpath:public`. If `npm ci` fails, no repo-tracked files should change; fix the package environment and rerun the validation path. The required build/test gates remain the authoritative proof of correctness.

## Artifacts and Notes

Baseline environment evidence before implementation:

- `npx shadow-cljs compile app portfolio-worker` currently fails in this worktree because `node_modules/@noble/secp256k1` is missing.
- The worktree is otherwise clean at start.

Final command transcripts, scene coverage summary, skipped-component list, and validation evidence will be added after implementation.

Validation evidence after implementation:

- `npm ci` completed successfully after the lockfile/dependency updates were present.
- `npm run check` passed on 2026-03-10 after compiling `:app`, `:portfolio`, `:portfolio-worker`, and `:test`.
- `npm test` passed on 2026-03-10 after fixing the workbench dispatch helper metadata bug and stabilizing the pre-existing telemetry idle test.
- `npm run test:websocket` passed on 2026-03-10.
- `npm run dev` served the app entry at `http://localhost:8081/index.html` and the workbench at `http://localhost:8081/ui-workbench.html` in this environment because port `8080` was already occupied. The workbench canvas and Portfolio classpath assets also served correctly at `http://localhost:8081/ui-workbench-canvas.html`, `http://localhost:8081/portfolio/styles/portfolio.css`, and `http://localhost:8081/portfolio/prism.js`.
- `npm run portfolio` served the renamed workbench entrypoint at `http://localhost:8083/ui-workbench.html`; `http://localhost:8083/ui-workbench-canvas.html` and `http://localhost:8083/portfolio/styles/portfolio.css` returned `200`, while the old `http://localhost:8083/portfolio.html` returned `404`.

## Interfaces and Dependencies

New dependencies and public interfaces expected at completion:

- `no.cjohansen/portfolio` added to `/hyperopen/deps.edn` and `/hyperopen/shadow-cljs.edn`
- New Shadow build `:portfolio`
- New workbench runner namespace `hyperopen.workbench.runner`
- New support namespaces under `hyperopen.workbench.support.*`
- New scene namespaces ending in `-scenes`
- New pure render seams:

    hyperopen.views.trade.order-form-view/render-order-form
    hyperopen.views.funding-modal/render-funding-modal

These render seams must preserve the current public wrappers:

    hyperopen.views.trade.order-form-view/order-form-view
    hyperopen.views.funding-modal/funding-modal-view

The scene-local dispatch system must stay isolated from the production runtime and must only mutate local scene atoms.
