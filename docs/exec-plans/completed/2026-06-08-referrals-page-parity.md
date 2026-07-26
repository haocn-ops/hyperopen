# Referrals Page Parity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. Keep it self-contained, update it whenever implementation discoveries change the plan, and leave at least one unchecked progress item while the plan remains active.

## Purpose / Big Picture

Hyperopen currently has no first-class referrals page, even though Hyperliquid exposes referrals as a top-level account workflow. After this change, a connected user can open `/referrals`, inspect referral status and rewards, enter another user's referral code, create and share their own code after meeting the volume requirement, and claim referral rewards through Hyperliquid signed L1 exchange actions. A referral join link like `/join/ABC123` should preserve the code and ask for explicit confirmation before signing; it must not auto-submit.

The visible proof is a dedicated referrals route with top-level navigation, KPI cards, referral and legacy-history tables, modals for entering/creating/sharing/claiming, deterministic tests for route/action/API/signing behavior, and browser coverage for the core user flows.

## Context References

Public refs:

- Direct user request on 2026-06-08: implement Hyperliquid referrals feature parity for Hyperopen after creating and reviewing the product spec.

Repo artifacts:

- `/hyperopen/docs/product-specs/referrals-page-parity-prd.md` is the product spec for this work.
- `/hyperopen/docs/product-specs/index.md` links the product spec.
- `/hyperopen/AGENTS.md` requires an ExecPlan for complex features and requires `npm run check`, `npm test`, and `npm run test:websocket` when code changes.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define active ExecPlan requirements.
- `/hyperopen/docs/BROWSER_TESTING.md` governs Playwright and browser QA expectations for UI work.

Local scratch refs, non-authoritative:

- `/Users/barry/.codex/attachments/cebfdcc5-9fd8-440a-8708-84325c34f2e1/pasted-text.txt` contains the external reviewer plan. The durable conclusions from that plan are copied into this ExecPlan: add a first-class route instead of deep-linking, use Hyperliquid `/info` referral data, use signed L1 actions for `setReferrer`, `registerReferrer`, and `claimRewards`, register effects in `app/effects.cljs`, wire API through gateway/default/instance paths, force no `vaultAddress`, block selected-subaccount mutations, and test agent signing on testnet or with a simulator before merging.

## Progress

- [x] (2026-06-08 14:36Z) Updated the product spec to include the external-reviewer gaps: `app/effects.cljs`, API gateway/instance wiring, selected-subaccount mutation guard, and no-`vaultAddress` signed-action tests.
- [x] (2026-06-08 14:36Z) Created this active ExecPlan.
- [x] (2026-06-08 15:12Z) Wrote RED tests for referral route parsing, join-code parsing, code validation, and master-account-only mutation guards.
- [x] (2026-06-08 15:12Z) Wrote RED tests for account API referral request construction and projection stale-address behavior.
- [x] (2026-06-08 15:12Z) Wrote RED tests for signed action helpers that prove `setReferrer`, `registerReferrer`, and `claimRewards` post with no `vaultAddress`.
- [x] (2026-06-08 15:24Z) Implemented the domain, action, API, projection, and signed-action foundation needed to pass the RED tests.
- [x] (2026-06-08 15:41Z) Wired route module, startup route refresh, runtime action adapters, runtime effect adapters, app action registry, app effect registry, and shadow modules.
- [x] (2026-06-08 15:58Z) Built the first dedicated `/referrals` view with header, action panels, KPI cards, tab shell, empty/loading/error states, Hyperliquid table column labels, and claim refresh behavior.
- [x] (2026-06-08 final pass) Added the modal layer for Enter Code, Create Code, Share Code, and Claim Rewards, including explicit `/join/:code` confirmation copy.
- [x] (2026-06-08 final pass) Added interaction tests and deterministic Playwright coverage for `/referrals`, modal validation, tab switching, mobile layout, and `/join/:code` confirmation.
- [x] (2026-06-08 15:59Z) Ran `npm test` after the first implementation pass; result was 4311 tests / 23792 assertions / 0 failures / 0 errors.
- [x] (2026-06-08 16:05Z) Ran `npm run check`; result was clean after updating governed namespace-size exceptions for the touched oversized facades/tests.
- [x] (2026-06-08 16:06Z) Ran `npm run test:websocket`; result was 534 tests / 3090 assertions / 0 failures / 0 errors.
- [x] (2026-06-08 16:06Z) Ran `bb tools/formal.clj verify --surface effect-order-contract` and `git diff --check`; both passed.
- [x] (2026-06-08 final pass) Ran `npx shadow-cljs --force-spawn compile test` after splitting modal rendering; result was 1781 files / 7 compiled / 0 warnings.
- [x] (2026-06-08 final pass) Ran `npx playwright test tools/playwright/test/referrals-regressions.spec.mjs`; result was 4 passed.
- [x] (2026-06-08 final pass) Ran `npx playwright test tools/playwright/test/routes.smoke.spec.mjs --grep "[Rr]eferrals"`; result was 4 passed.
- [x] (2026-06-08 final pass) Ran `npm run qa:design-ui -- --targets referrals-route --manage-local-app`; result was PASS for visual evidence, native controls, styling consistency, interaction, layout regression, and jank/perf across 375, 768, 1280, and 1440 widths.
- [x] (2026-06-08 final pass) Ran `npm run check` after the modal split and governed namespace-size update; result was clean with Shadow app, portfolio, worker, and test builds at 0 warnings.
- [x] (2026-06-08 final pass) Ran final `npm test`; result was 4316 tests / 23815 assertions / 0 failures / 0 errors.
- [x] (2026-06-08 final pass) Ran final `npm run test:websocket`; result was 534 tests / 3090 assertions / 0 failures / 0 errors.

## Surprises & Discoveries

- Observation: The current worktree is already an isolated linked worktree, but it started on a detached HEAD.
  Evidence: `git rev-parse --git-dir` resolved under `/Users/barry/projects/hyperopen/.git/worktrees/...`, `git rev-parse --git-common-dir` resolved to `/Users/barry/projects/hyperopen/.git`, and `git branch --show-current` was empty. The branch `codex/referrals-page-implementation` was created before implementation changes.

- Observation: The external reviewer plan names `app/effects.cljs` and `api/instance.cljs` as wiring points, and both are real in this checkout.
  Evidence: `src/hyperopen/app/effects.cljs` contains `runtime-effect-overrides`; `src/hyperopen/api/instance.cljs` contains `make-instance-account-ops`.

- Observation: Hyperopen's general mutation guard does not block an owned selected subaccount.
  Evidence: `account-context/mutations-blocked-message` blocks spectate mode, trader-portfolio routes, and unavailable selected subaccounts. A valid owned selected subaccount changes the active trading account but does not return a blocked message. Referrals need a feature-specific master-account-only guard.

- Observation: This fresh worktree did not have `node_modules` installed.
  Evidence: the first test runner attempt failed because `shadow-cljs` was unavailable. Running `npm install` restored the local toolchain without changing tracked dependency versions.

- Observation: Effect-order policy has a Lean-backed formal model separate from `src/hyperopen/runtime/effect_order_contract.cljs`.
  Evidence: adding referrals to the runtime policy made the ClojureScript conformance test fail until `spec/lean/Hyperopen/Formal/EffectOrderContract.lean` was updated and `bb tools/formal.clj sync --surface effect-order-contract` regenerated `test/hyperopen/formal/effect_order_contract_vectors.cljs`.

- Observation: The required `npm run check` gate enforces namespace-size exceptions for oversized central facades and shared test suites.
  Evidence: the first `npm run check` attempt failed only on size policy for `src/hyperopen/api/instance.cljs`, `src/hyperopen/schema/contracts/action_args.cljs`, `src/hyperopen/runtime/effect_order_contract.cljs`, `src/hyperopen/runtime/effect_adapters.cljs`, `test/hyperopen/views/app_view_test.cljs`, and `test/hyperopen/views/header_view_test.cljs`. Updating `dev/namespace_size_exceptions.edn` with referral-specific rationale made the rerun pass.

- Observation: Modal rendering pushed `src/hyperopen/views/referrals_view.cljs` over the namespace-size limit when kept inline.
  Evidence: the final `npm run check` pass initially failed with `src/hyperopen/views/referrals_view.cljs` at 588 lines and `src/hyperopen/schema/contracts/action_args.cljs` at 582 lines against a 575-line exception. Moving modal rendering into `src/hyperopen/views/referrals/modals.cljs` reduced the route view to 325 lines; the central action-args exception was bumped narrowly to 590 lines with referrals modal rationale.

- Observation: Browser design review routing did not know about the new referrals route.
  Evidence: `npm run qa:design-ui -- --targets referrals-route --manage-local-app` required adding `referrals-route` to `tools/browser-inspection/config/design-review-routing.json`; the final run produced PASS evidence for the governed browser-QA passes across 375, 768, 1280, and 1440 widths.

- Observation: The first Playwright attempt was blocked by a stale dev server from another Hyperopen worktree.
  Evidence: Playwright reported port 8080 in use. Running `npm run dev:kill` stopped stale dev server processes from `/Users/barry/.codex/worktrees/bc1d/hyperopen`, after which the referrals Playwright specs ran against this worktree.

## Decision Log

- Decision: Implement referrals as a first-class Hyperopen account route rather than linking users to Hyperliquid.
  Rationale: The product requirement is feature parity, not a navigation shortcut. Hyperopen already has lazy route modules, account API boundaries, and signed L1 action infrastructure.
  Date/Author: 2026-06-08 / Codex

- Decision: Route referral signed writes through the existing agent L1 signing path first.
  Rationale: The observed Hyperliquid UI signs referral actions through the active agent wallet when trading is ready, and the reference SDKs classify these as L1 actions. The plan still requires testnet or simulator proof before merge because the public exchange docs only explicitly document `claimRewards`.
  Date/Author: 2026-06-08 / Codex

- Decision: Add a referral-specific master-account-only mutation guard.
  Rationale: Hyperliquid referral discounts do not apply to vaults or subaccounts. Hyperopen can safely read referral state for the connected master, but it should not submit sticky account referral mutations while a selected subaccount is the active trading context.
  Date/Author: 2026-06-08 / Codex

- Decision: Keep `/join/:code` explicit-confirmation only.
  Rationale: Referral selection is sticky account state. The route can preserve and prefill a code, but must show the exact normalized code and require an explicit user action before signing.
  Date/Author: 2026-06-08 / Codex

## Outcomes & Retrospective

The referrals parity slice is implemented. `/referrals` is now a lazy route module with nav, route refresh, account referral loading, master-only mutation guards, signed referral actions with nil vault address, referral projection state, KPI cards, referral and legacy-history tabs, explicit modal workflows, `/join/:code` confirmation behavior, deterministic Playwright route/modal coverage, and governed design-review routing. Required repo gates and browser-QA passes were recorded before moving this plan to completed.

## Context and Orientation

Hyperopen is a ClojureScript app using Replicant for views and Nexus-style actions/effects. In this repository, actions are pure functions that receive state and return effect descriptors. Effects live at runtime boundaries and do network calls, storage, wallet signing, clipboard work, and browser history changes. Views should consume normalized view models instead of raw Hyperliquid payloads.

The relevant route system starts in `src/hyperopen/route_modules.cljs`. That file maps route IDs like `:leaderboard` and `:staking` to Shadow CLJS module names and exported route-view symbols. Shadow modules are declared twice in `shadow-cljs.edn`, once under the `:app` build and once under the `:release` build. Header navigation items live in `src/hyperopen/views/header/nav.cljs`. Route-entry refresh effects live in `src/hyperopen/startup/route_refresh.cljs`, and in-browser navigation effects are assembled in `src/hyperopen/runtime/action_adapters/navigation.cljs`.

The account read API flows through multiple layers. Low-level request bodies live under `src/hyperopen/api/endpoints/account/**`. The gateway facade in `src/hyperopen/api/gateway/account.cljs` forwards account requests to those endpoints. The default runtime API in `src/hyperopen/api/default.cljs` exposes functions for normal app effects. The injectable API service in `src/hyperopen/api/instance.cljs` also exposes account operations for tests or alternate API-service instances. Projection namespaces under `src/hyperopen/api/projections/**` update app state after requests succeed or fail.

Signed L1 exchange actions flow through `src/hyperopen/api/trading/agent_actions.cljs`. The helper `sign-and-post-agent-action!` signs an action with the active agent wallet and posts to Hyperliquid exchange. Management-style actions use `management-action-options`, which sets `:vault-address nil`. Referral actions must use this nil-vault behavior. `src/hyperopen/api/trading.cljs` re-exports public trading helpers.

The current referral-adjacent implementation is only the fee-schedule display under `src/hyperopen/portfolio/fee_schedule.cljs`, which already reads `activeReferralDiscount` from `userFees`. This plan must not create a parallel fee model.

## Plan of Work

First, create the RED tests for pure referral behavior. Add `test/hyperopen/referrals/actions_test.cljs` to cover `/referrals` route recognition, `/join/:code` parsing, uppercase alphanumeric code normalization, max 20 character validation, `load-referrals-route` effect descriptors, explicit pending-code behavior, and the referral-specific master-account-only mutation guard. Include a state with an owned selected subaccount row and assert that `submit-set-referrer`, `submit-register-referrer`, and `submit-claim-rewards` save a form error instead of emitting API submit effects.

Next, add RED tests for the account read API. Add `test/hyperopen/api/endpoints/account/referrals_test.cljs` to verify `request-referral!` posts exactly `{"type" "referral", "user" address}` and returns nil without a post when address is missing. Add projection tests in `test/hyperopen/api/projections/referrals_test.cljs` for begin/success/error, including loaded-for-address and stale-address protection so a response for one wallet cannot overwrite another wallet's current referral state.

Next, add RED tests for signed referral action helpers. Extend or create `test/hyperopen/api/trading/agent_actions_test.cljs` to install the debug exchange simulator, call `set-referrer!`, `register-referrer!`, and `claim-rewards!`, and assert the signed payload's `action` is exactly the referral action and that the top-level exchange payload has no `vaultAddress`. If existing agent-action tests are hard to reuse because they require a real crypto module, add a small seam or use the existing debug exchange simulator and test the public helpers at the lowest practical boundary.

Then implement the minimal data/action foundation. Create `src/hyperopen/referrals/actions.cljs` with route parsing, code normalization, tab/sort/page defaults, modal form actions, `load-referrals-route`, submit actions, and `referral-mutation-blocked-message`. Create `src/hyperopen/api/endpoints/account/referrals.cljs`, update `src/hyperopen/api/endpoints/account.cljs`, `src/hyperopen/api/gateway/account.cljs`, `src/hyperopen/api/default/account.cljs`, `src/hyperopen/api/default.cljs`, and `src/hyperopen/api/instance.cljs` with `request-referral!`. Create `src/hyperopen/api/projections/referrals.cljs` and export it from `src/hyperopen/api/projections.cljs`.

Then implement signed-action helpers. Add `set-referrer!`, `register-referrer!`, and `claim-rewards!` to `src/hyperopen/api/trading/agent_actions.cljs` using `management-action-options`. Re-export them from `src/hyperopen/api/trading.cljs`. If tests show existing debug seams cannot observe the payload cleanly, introduce the smallest test-only dependency injection seam rather than changing runtime signing semantics.

Then wire runtime actions and effects. Create `src/hyperopen/referrals/effects.cljs` for loading referral state and submitting referral actions, including success refetches of referral data, `userFees`, and spot balances after `claimRewards`. Create `src/hyperopen/runtime/effect_adapters/referrals.cljs` and `src/hyperopen/runtime/action_adapters/referrals.cljs`. Update `src/hyperopen/runtime/effect_adapters.cljs`, `src/hyperopen/runtime/action_adapters.cljs`, `src/hyperopen/app/actions.cljs`, and `src/hyperopen/app/effects.cljs` so the effect IDs and action IDs are registered. Update `src/hyperopen/startup/route_refresh.cljs` and `src/hyperopen/runtime/action_adapters/navigation.cljs` so entering `/referrals` or `/join/:code` loads the route module and route data.

Then wire the route module. Update `shadow-cljs.edn` in both app and release builds with `:referrals_route`. Update `src/hyperopen/route_modules.cljs` to import `hyperopen.referrals.actions`, map `:referrals` to `"referrals_route"`, resolve `hyperopen.views.referrals_view.route_view`, and recognize `/referrals` and `/join/:code`. Update `src/hyperopen/views/header/nav.cljs` so desktop nav includes `Referrals` near `Staking` and `Leaderboard`, and mobile nav includes it in the secondary page list.

Then build the view. Create `src/hyperopen/referrals/vm.cljs` to derive a route view model from normalized referral state, wallet state, and UI state. Create `src/hyperopen/views/referrals_view.cljs`, `src/hyperopen/views/referrals/shared.cljs`, `src/hyperopen/views/referrals/tables.cljs`, and `src/hyperopen/views/referrals/modals.cljs`. The first complete view must render title, learn-more link, Enter Code, Create/Share Code, Claim Rewards, three KPI cards, `Referrals` and `Legacy Reward History` tabs, empty/loading/error states, and modal validation. Keep the UI compact and use existing Hyperopen page shell conventions.

Finally, add deterministic browser coverage and run gates. Add or extend Playwright coverage under `tools/playwright/test/` for the `/referrals` render, modal validation, tab switching, mobile layout, and `/join/:code` pending-code confirmation. Run the smallest relevant Playwright test first, then `npm run check`, `npm test`, and `npm run test:websocket`.

## Concrete Steps

From `/Users/barry/.codex/worktrees/fc88/hyperopen`, begin with current status:

    git status --short --branch

Expected: branch `codex/referrals-page-implementation` and only intentional edits from this plan.

Write the RED tests for `hyperopen.referrals.actions-test`, API endpoint/projection tests, and signed action payload tests. Then run:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js

Expected before implementation: the new tests fail because the referral namespaces and helpers do not exist or because the expected effects/actions are not emitted. The generated runner may ignore namespace arguments, so use the full runner if targeted invocation is unavailable.

Implement the foundation namespaces and rerun:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js

Expected after the foundation: the new action/API/projection/signing tests pass, while view/browser tests may still be pending if not yet written.

After view implementation, run the smallest relevant Playwright command. If a new referrals spec file is created at `tools/playwright/test/referrals-regressions.spec.mjs`, run:

    npx playwright test tools/playwright/test/referrals-regressions.spec.mjs

Expected: the referrals route, modal validation, tabs, mobile layout, and join-code confirmation scenarios pass.

For final repo validation after code changes, run:

    npm run check
    npm test
    npm run test:websocket

Expected: all required gates pass. If an unrelated active-plan or stale-doc guardrail fails, record the exact failure here and in the final response rather than hiding it with unrelated metadata edits.

## Validation and Acceptance

Route acceptance: navigating to `/referrals` loads the referrals route module, marks the nav item active, and renders a dedicated referrals page. Navigating to `/join/ABC123` renders the same page, preserves `ABC123` as the pending code, and requires explicit user confirmation before signing.

Data acceptance: the route requests Hyperliquid referral info with `{"type" "referral", "user" master-address}` for the connected master account, normalizes token reward state without losing raw payload fields, protects against stale address responses, and refetches `userFees` after a successful `setReferrer`.

Mutation acceptance: `setReferrer`, `registerReferrer`, and `claimRewards` sign L1 actions through the agent-wallet path with no `vaultAddress`; all three are blocked in spectate, trader-portfolio, unavailable-subaccount, selected-owned-subaccount, disconnected, and locked-agent states.

View acceptance: the page shows title, helper link, action buttons, KPI cards, two tabs, referral rows, legacy rows, empty states, loading states, error retry, and accessible modals. Claiming rewards refreshes referral data and spot balances. Existing portfolio fee-schedule referral discount behavior continues to use `userFees`.

Browser acceptance: deterministic Playwright coverage exercises the route, modals, tabs, mobile layout, and join-code confirmation flow without relying on real wallet signatures.

## Idempotence and Recovery

Most changes are additive. If route-module wiring breaks startup, revert the route-module entry and Shadow module entry together, leaving pure API/action tests in place. If agent-wallet signing tests expose that the action cannot use the API-wallet path, do not ship a guessed fallback; update this plan and route that specific action through a verified user-wallet signing path. If browser tests are flaky, replace timing waits with controlled request fixtures and record the fixture behavior here.

The active plan must stay active until implementation and validation are complete. If work is paused before completion, leave at least one unchecked Progress item and record the exact stopping point in `Outcomes & Retrospective`.

## Artifacts and Notes

Current route-module baseline:

    src/hyperopen/route_modules.cljs maps :portfolio, :leaderboard, :funding-comparison, :staking, :api-wallets, :subaccounts, and :vaults. There is no :referrals entry yet.

Current header baseline:

    src/hyperopen/views/header/nav.cljs lists Trade, Portfolio, Funding, Vaults, Staking, Leaderboard, API, and Sub-Accounts. There is no Referrals item yet.

Current Shadow module baseline:

    shadow-cljs.edn declares route modules for portfolio, leaderboard, funding comparison, staking, API wallets, subaccounts, and vaults in app and release builds. There is no referrals_route yet.

Current signed-action baseline:

    src/hyperopen/api/trading/agent_actions.cljs has management-action-options, which associates :vault-address nil, and management helpers for createSubAccount, subAccountModify, subAccountTransfer, subAccountSpotTransfer, and scheduleCancel.

## Interfaces and Dependencies

New route/action namespace:

    hyperopen.referrals.actions

Required public functions:

    canonical-route
    normalize-referral-code
    valid-referral-code?
    referrals-route?
    join-code-from-path
    load-referrals-route
    set-referrals-active-tab
    set-referrals-form-field
    open-referrals-modal
    close-referrals-modal
    submit-set-referrer
    submit-register-referrer
    submit-claim-rewards
    referral-mutation-blocked-message

New account API request:

    hyperopen.api.endpoints.account.referrals/request-referral!

It takes `post-info!`, `address`, and `opts`. With an address it posts `{"type" "referral", "user" address}` through the request policy. Without an address it returns a resolved promise with nil.

New projection namespace:

    hyperopen.api.projections.referrals

Required public functions:

    begin-load
    apply-success
    apply-error

New signed action helpers:

    hyperopen.api.trading.agent-actions/set-referrer!
    hyperopen.api.trading.agent-actions/register-referrer!
    hyperopen.api.trading.agent-actions/claim-rewards!

They must call `sign-and-post-agent-action!` with these action maps and `management-action-options`:

    {:type "setReferrer" :code code}
    {:type "registerReferrer" :code code}
    {:type "claimRewards"}

New route module:

    :referrals -> "referrals_route" -> hyperopen.views.referrals_view.route_view

New core effect IDs:

    :effects/api-fetch-referral
    :effects/api-set-referrer
    :effects/api-register-referrer
    :effects/api-claim-referral-rewards

New route/action IDs:

    :actions/load-referrals-route
    :actions/set-referrals-active-tab
    :actions/set-referrals-form-field
    :actions/open-referrals-modal
    :actions/close-referrals-modal
    :actions/submit-set-referrer
    :actions/submit-register-referrer
    :actions/submit-claim-rewards
