# Restore Master-Account Unstaking With Selected Subaccounts

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. Keep it self-contained, update it whenever implementation discoveries change the plan, and leave at least one unchecked progress item while the plan remains active.

## Purpose / Big Picture

A reviewer reports that unstaking does not work from a master account. The supplied screenshot shows the `/staking` route connected with `Your Stake` of `101`, the `Unstake` popover open, amount `101`, and `Nansen x HypurrCollective` selected. Diagnosis has now demonstrated two separate defects in this flow. First, native staking correctly reads/signs as the owner/master, but the generic trading-account mutation blocker rejects a stale or unavailable raw selected subaccount even when the account selector visibly renders Master. Second, delegation responses already normalize `lockedUntilTimestamp`, but the Unstake action ignores a future lock and can attempt a transaction that should be held locally. After this work, a master account can unstake its valid, unlocked delegation despite stale selected-trading-account state; a future-locked delegation stays local and explains exactly when it unlocks; and form, action, and exchange errors are visible inside the open Unstake popover.

The objective is to correct these demonstrated regressions without relaxing safety checks generally. The solution must preserve the existing rules that a selected owned subaccount is not itself a native staking account, spectate and trader-portfolio inspection routes cannot mutate, an amount cannot exceed that validator's delegated amount, a future lock cannot be submitted, and a request may not be submitted twice while it is in flight.

## Context References

Public refs:

- Direct user/maintainer request on 2026-08-03: the reviewer reports an inability to unstake from a master account after one or more attempted fixes and asks for a plan and implementation.
- Direct user-provided screenshot on 2026-08-03. Durable facts reproduced here: `/staking` displays `Your Stake 101`; the `Unstake` popover contains amount `101` and selected validator `Nansen x HypurrCollective`; no post-click error, wallet prompt, request payload, or response is visible. The screenshot is a visual reference, not proof that the visible button is disabled or that a particular guard fired.

Repo artifacts:

- `54be77fdc1d09f93d82a4219ab6b2a97cc8b1715` (`Harden staking account scoping and portfolio margin order previews`) is the current `HEAD` and the immediately relevant prior change. It introduced master-oriented native staking reads, resource provenance, and the undelegate readiness guard.
- `/hyperopen/docs/exec-plans/completed/2026-08-02-portfolio-margin-scale-staking-parity.md` records the intended behavior of that prior staking work, including master reads/signing, selected-subaccount isolation, and freshness guards.
- `/hyperopen/AGENTS.md` requires this ExecPlan for risky UI bug work and requires `npm run check`, `npm test`, and `npm run test:websocket` after code changes.
- `/hyperopen/docs/BROWSER_TESTING.md`, `/hyperopen/docs/FRONTEND.md`, and `/hyperopen/docs/agent-guides/browser-qa.md` govern deterministic browser tests and the six-pass design review for this interaction flow.

Local scratch refs, non-authoritative:

- The attached image was supplied at `/var/folders/dg/3nkyzrp12fn141vv7f6rc9v40000gn/T/codex-clipboard-802251a0-838e-4dd3-821f-081c168ec7ea.jpg`. Its relevant visible facts are copied above so a future contributor does not depend on that temporary path.

## Scope, Non-Goals, and UI Status

This UI-facing mutation-flow regression is complete. Implementation, static re-review, final repository gates, and the complete governed browser-QA matrix are green. Browser validation used the debug bridge, Browser MCP, controlled promises, and the built-in wallet/exchange simulators; it never broadcast a real `tokenDelegate` transaction or required access to the reporter's wallet.

In scope are the discovered popover event-envelope correction; a staking-specific mutation blocker used by both the action and effect layers; a local selected-validator lock guard; the existing delegation-data readiness/provenance decision; the existing master signer and undelegate wire map; Unstake-popover error placement; deterministic CLJS coverage; committed 101-HYPE master-with-selected-subaccount Playwright regression coverage; review of pending-submit idempotency; and browser QA for `/staking`.

Out of scope are startup-surface fetching, chain/environment selection, a new staking product flow, staking from a selected subaccount, altering Hyperliquid signing/wire contracts, bypassing a confirmed data-staleness guard, changing the one-day lockup/pending-withdrawal protocol, wallet-provider changes, and unrelated trade/portfolio changes included in `54be77fdc`. This is not performance work: no new algorithm, throughput target, baseline, or profiling exercise is justified.

## Progress

- [x] (2026-08-03 13:30Z) Captured the direct report and screenshot facts as durable plan context.
- [x] (2026-08-03 13:30Z) Inspected `HEAD` `54be77fdc`, the completed prior staking plan, and the current master/subaccount read, action, effect, adapter, view-model, popover, endpoint, unit-test, and Playwright surfaces.
- [x] (2026-08-03 13:30Z) Created this active ExecPlan; no production or test files have been changed by this planning phase.
- [x] (2026-08-03 13:40Z) Froze diagnosis: generic `account-context/mutations-blocked-message` falsely blocks a normal master staking mutation when raw selected-subaccount state is stale/unavailable; it is called by both the undelegate action and submit effect.
- [x] (2026-08-03 13:40Z) Froze diagnosis: normalized `:locked-until-timestamp` is not evaluated by `submit-staking-undelegate`; future locks must be rejected locally while nil, expired, and exact-now timestamps remain allowed.
- [x] (2026-08-03 14:16Z) Captured RED coverage for the master-specific blocker, future/equal/expired/nil lock boundary, exact in-popover error rendering, and 101-HYPE lifecycle: the initial run produced 13 intended failures; a duplicate-follow-up run produced 3 intended failures.
- [x] (2026-08-03 14:16Z) Captured the decisive browser RED: clicking the actual Unstake CTA left app state unchanged and made no request, while direct debug dispatch of the same action worked.
- [x] (2026-08-03 14:16Z) Implemented the nested Replicant event envelope for dynamic CTA and MAX actions, the staking-specific stale-selection blocker, strict future-lock inline guard, and Unstake-popover error placement without changing startup fetches, chain environment, resource provenance, or the native staking wire contract.
- [x] (2026-08-03 14:16Z) Reached initial GREEN evidence: CLJS passed 5,801 tests / 32,056 assertions; focused master Playwright cases passed 3/3; app compilation and websocket validation passed earlier in this implementation pass; the design review reported all six default-route passes.
- [x] (2026-08-03 14:16Z) Static re-review is clean. No correctness, regression, or pending-submit idempotency defect was found in the corrected event/action path.
- [x] (2026-08-03 14:33Z) Added a TDD Escape regression after trusted `Escape` initially failed to close the open popover (RED: 1 intended failure). Attached the shared dialog-focus lifecycle to the popover dialog; focused Escape verification passed 1/1.
- [x] (2026-08-03 14:33Z) Completed final `/staking` browser verification: full staking Playwright passed 6/6; governed browser QA passed visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf at 375, 768, 1280, and 1440; browser cleanup passed with no sessions remaining.
- [x] (2026-08-03 14:33Z) Completed final post-fix `npm run gates`: PASS 34/34 in 1m39s, including 6,522 tests / 35,456 assertions. Static re-review remains clean and every acceptance criterion below is satisfied.
- [x] (2026-08-03 14:33Z) Recorded final evidence, residual live-wallet note, and completion outcome; this plan is ready to move to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The primary user-visible failure was not a master account, validator, or exchange rejection; the real Unstake CTA was a no-op. Dynamic action keywords in the shared CTA and MAX helpers were wrapped only once, producing a bare action vector such as `[:actions/submit-staking-undelegate]`. Replicant requires the click value to be a collection containing that action vector, such as `[[:actions/submit-staking-undelegate]]`.
  Evidence: Browser RED clicked the rendered Unstake CTA and observed no state transition and no request. Direct debug dispatch of the same action worked. The same dynamic-envelope defect existed in `popover-cta-button` and the MAX control in `src/hyperopen/views/staking/popovers.cljs`.

- Observation: The event-envelope correction is intentionally narrow and does not alter action IDs or business semantics. The CTA and MAX helpers now emit nested action vectors so Replicant dispatches the existing dynamic action.
  Evidence: Focused simulator-backed master Playwright coverage passed 3/3 after the correction, including a real click that reaches the existing undelegate path; the earlier real-click RED made no request.

- Observation: Trusted browser `Escape` initially failed despite an action-level key handler because the rendered dialog did not receive keyboard focus. A test that dispatched the action directly would not detect this focus-lifecycle failure.
  Evidence: TDD browser RED had 1 intended failure when pressing real `Escape` against the open Unstake popover. Attaching `hyperopen.views.ui.dialog-focus/dialog-focus-on-render` as the popover dialog's `:replicant/on-render` lifecycle made the focused Escape regression GREEN 1/1.

- Observation: The shared dialog-focus lifecycle resolves keyboard delivery without adding a staking-specific global key listener or changing close action semantics.
  Evidence: `src/hyperopen/views/staking/popovers.cljs` now focuses the existing `role="dialog"` action popover on render; the existing `:actions/handle-staking-action-popover-keydown` Escape path closes it.

- Observation: The prior commit already distinguishes the native staking identity from the active trading identity. In normal connected state, `native-staking-account-address` resolves `owner-address`; in spectate mode and trader-portfolio inspection it uses the effective inspected address.
  Evidence: `src/hyperopen/account/context.cljs` defines `native-staking-account-address`; `test/hyperopen/staking/actions_test.cljs` already asserts that all five addressed staking reads use the owner instead of an owned selected subaccount.

- Observation: Generic mutation blocking is semantically wrong for normal native staking when raw selected-subaccount state is stale. The account selector can render Master and native staking can correctly use the owner, while `account-context/mutations-blocked-message` still returns the selected-subaccount-unavailable message from stale trading state.
  Evidence: Diagnosis demonstrated this state combination. Both `submit-staking-undelegate` in `src/hyperopen/staking/actions.cljs` and shared `submit-staking-action!` in `src/hyperopen/staking/effects.cljs` call the generic blocker before their owner/master submit path.

- Observation: The data contract carries the selected validator's lock deadline but the undelegate action never evaluates it. A future deadline can therefore reach the exchange layer.
  Evidence: `src/hyperopen/api/endpoints/account/staking.cljs` normalizes `lockedUntilTimestamp`, `lockedUntil`, or `locked-until-timestamp` into `:locked-until-timestamp`; `delegation-amount-by-validator` in `src/hyperopen/staking/actions.cljs` reads only `:amount`, and `submit-staking-undelegate` has no lock-time condition.

- Observation: Current form, action, and exchange failures are held in `[:staking-ui :form-error]` and flow through the page view-model error surface, but the Unstake content does not receive or render that value inside the open popover.
  Evidence: `src/hyperopen/views/staking/vm.cljs` exports `:error`; `src/hyperopen/views/staking_view.cljs` renders the route-level error; and `unstake-popover-content` in `src/hyperopen/views/staking/popovers.cljs` receives form/submitting/validator values but not an error value.

- Observation: Submission itself still signs with the connected owner and refreshes `load-staking` only after an `{:status "ok"}` response. A rejected exchange response and a runtime exception both clear the submitting flag and surface a message.
  Evidence: `src/hyperopen/staking/effects.cljs` calls `submit-token-delegate!` with `owner-address`, sets the `:undelegate?` submitting flag false in both paths, and dispatches `[:actions/load-staking]` only in the successful branch.

- Observation: Committed browser coverage now exercises the full staking interaction path, including the real DOM event, keyboard close, master/subaccount identity, 101-HYPE submit, future lock, and exchange rejection recovery.
  Evidence: Focused Escape passed 1/1 and the full staking Playwright file passed 6/6. Governed browser QA returned PASS for all six required categories at 375, 768, 1280, and 1440.

- Observation: RED, targeted GREEN, browser QA, and final gates cover distinct layers and collectively provide the closure evidence.
  Evidence: The original RED test pass reported 13 intended failures and a duplicate follow-up reported 3 intended failures; real-click browser RED made no request; trusted Escape RED had 1 intended failure. Generated CLJS was GREEN at 5,801 tests / 32,056 assertions; focused master Playwright was GREEN at 3/3; focused Escape was GREEN 1/1; full staking Playwright was GREEN 6/6; static re-review was clean; final governed QA passed all six categories at all four widths; and final `npm run gates` passed 34/34 in 1m39s with 6,522 tests / 35,456 assertions.

## Decision Log

- Decision: Replace generic mutation blocking only for native staking actions/effects with a staking-specific blocker that preserves spectate and trader-portfolio read-only behavior but ignores `selected-subaccount-unavailable?`.
  Rationale: Native staking is master-scoped in normal connected state, so raw selected-trading-account availability is not authorization evidence for it. Keeping the generic blocker in this path makes the master UI contradict the owner's signing/read identity. The action and effect must use the same staking-specific blocker so a direct effect invocation cannot bypass or reintroduce the mismatch.
  Date/Author: 2026-08-03 / Codex

- Decision: Test normal master state with an owned selected subaccount as the primary regression fixture.
  Rationale: It is the relationship specifically raised in the report and the semantics adopted by `54be77fdc`: staking reads and signatures belong to the owner while the selected subaccount remains the active trading account. A master-only fixture would not prove that the regression is fixed.
  Date/Author: 2026-08-03 / Codex

- Decision: Preserve `account-scope/resource-ready?` unchanged and apply the new blocker independently of it.
  Rationale: Resource provenance prevents an old or another-account delegation response from authorizing a master transaction. The demonstrated generic-blocker defect is unrelated to whether delegations were loaded for the current master.
  Date/Author: 2026-08-03 / Codex

- Decision: Block an undelegate locally only when the matching selected-validator delegation has a finite `:locked-until-timestamp` strictly greater than the evaluation time; nil, an earlier timestamp, and a timestamp equal to the evaluation time are eligible to submit.
  Rationale: A strict future check prevents a known-invalid request without extending the lock beyond its exact unlock instant. The predicate and its clock input must be isolated enough for deterministic tests.
  Date/Author: 2026-08-03 / Codex

- Decision: Render `[:staking-ui :form-error]` inside an open Unstake popover as well as retaining the existing page-level error behavior.
  Rationale: The fields and primary control remain visible while a form/action/exchange error is generated. Showing the same error in that immediate context explains what happened and gives the user a direct recovery path without concealing the route-level status.
  Date/Author: 2026-08-03 / Codex

- Decision: Fix the Replicant event envelope in the shared dynamic CTA and MAX helpers before judging any staking action guard.
  Rationale: A bare dynamic action vector causes a genuine DOM click to do nothing, so no owner identity, freshness, lock, exchange, or error logic can execute. The nested collection is the minimum contract-correct representation and preserves the existing actions.
  Date/Author: 2026-08-03 14:16Z / Codex

- Decision: Held closure until reviewer assessment of repeated pending CTA interaction.
  Rationale: The button's visual disabled state and existing submitting marker are intended to prevent duplicate submissions, but the corrected real-click envelope changed how that path is exercised. Static re-review and final browser interaction subsequently confirmed the idempotency invariant.
  Date/Author: 2026-08-03 14:16Z / Codex

- Decision: Attach the existing shared dialog-focus lifecycle to the staking action popover instead of adding a route-local Escape listener.
  Rationale: The popover already has `role="dialog"`, focusable tab index, and a keydown action. The missing behavior was trusted keyboard delivery, not a missing close action. Reusing the shared focus-on-render lifecycle restores accessibility-consistent focus ownership with the smallest change.
  Date/Author: 2026-08-03 14:33Z / Codex

## Outcomes & Retrospective

The master-account Unstake regression is fixed and accepted. The decisive primary bug was a malformed Replicant event envelope: the actual CTA click did not dispatch at all, so direct debug dispatch incorrectly made lower layers appear healthy. The implementation now emits nested event vectors from both dynamic CTA and MAX controls, applies the staking-specific stale-selection blocker in action/effect layers, blocks strict-future delegation locks with the exact inline message, renders errors in the open Unstake popover, and focuses the dialog through the shared lifecycle so trusted Escape closes it. The change is complexity-neutral overall: it restores existing event/focus contracts, reuses the owner/master lifecycle and normalized lock data, and adds no parallel state or wire path. Full staking Playwright passed 6/6, governed QA passed all six categories at all four required widths, cleanup left no sessions, static re-review was clean, and final gates passed 34/34 in 1m39s.

## Context and Orientation

Native staking is delegation of HYPE to a validator. A master/owner is the connected wallet address. A selected subaccount is an owned trading account that can be active for trading, but native staking in ordinary connected mode belongs to the owner. An inspected account is a spectate-mode or trader-portfolio address; it is read-only and must not submit a staking mutation.

`src/hyperopen/account/context.cljs` is the source of account identity. `owner-address` normalizes `[:wallet :address]`. `effective-account-address` follows an owned selected subaccount for trading. `native-staking-account-address` chooses the owner in normal connected state, but returns the effective inspected account on the two read-only routes. `account-context/mutations-blocked-message` is broader: it also rejects stale/unavailable selected-subaccount trading state. That generic rule is correct for trading mutations but wrong for normal master-scoped native staking. `src/hyperopen/staking/account_scope.cljs` is the appropriate staking boundary for a `staking-mutations-blocked-message` helper that retains only the spectate/trader read-only reasons. It must not change the shared generic helper. The same namespace already compares the address saved at `[:staking :account-address]`, the current native staking address, and each resource's `[:staking :loaded-for resource]` marker. A marker proves that the in-memory response belongs to the account currently allowed to act.

The page-entry action is `load-staking` in `src/hyperopen/staking/actions.cljs`. It saves the native staking address, clears account-specific data, and requests delegator summary, delegations, rewards, history, and staking-owned spot state for that address. `src/hyperopen/staking/effects.cljs` applies each response only if the address and request generation still match, then records the matching `:loaded-for` marker. This prevents a late response for a different account, or an older response for the same account, from authorizing an action.

The exact reported mutation starts at `submit-staking-undelegate` in `src/hyperopen/staking/actions.cljs`. It builds this existing effect payload after its guards pass:

    [:effects/api-submit-staking-undelegate
     {:kind :undelegate
      :action {:type "tokenDelegate"
               :validator <normalized-validator-address>
               :wei <positive-integer-hype-wei>
               :isUndelegate true}}]

The `:wei` value is HYPE multiplied by `100,000,000`; for the screenshot's integer amount `101`, that is `10,100,000,000`. The action may only use the delegation row whose normalized `:validator` equals the selected validator. `src/hyperopen/api/endpoints/account/staking.cljs` has already normalized each row's possible `lockedUntilTimestamp` into a millisecond `:locked-until-timestamp`. The action must find that same matching row, require an amount no greater than its amount, and reject only a finite deadline strictly after `now-ms`. The exact local error text is:

    This delegation is locked until <M/D/YYYY - HH:MM:SS>.

`<M/D/YYYY - HH:MM:SS>` is the user-local rendering of the normalized timestamp: unpadded numeric month/day, four-digit year, literal ` - `, and zero-padded 24-hour minute/second components. The implementation should make the strict lock predicate and formatter directly testable; the browser can supply the current clock. A valid, current master row with no lock, an expired lock, or a lock exactly equal to the evaluation time must therefore permit the effect; a missing/mismatched snapshot must continue to report a clear loading/retry state rather than submit from uncertain data.

`src/hyperopen/runtime/effect_adapters/staking.cljs` sends the effect to `api-submit-staking-undelegate!`. The shared submit code in `src/hyperopen/staking/effects.cljs` invokes `hyperopen.api.trading/submit-token-delegate!` with the owner address, not the selected subaccount. Both `submit-staking-undelegate` and shared `submit-staking-action!` now use the staking-specific blocker. On `{:status "ok"}`, it clears the undelegate input/submitting state, shows `Unstake submitted.`, and dispatches `[:actions/load-staking]`; on an exchange rejection or runtime error, it clears submitting state and exposes a human-readable error. `src/hyperopen/views/staking/vm.cljs`, `src/hyperopen/views/staking_view.cljs`, and `src/hyperopen/views/staking/popovers.cljs` render that state. The existing VM error is now threaded to `unstake-popover-content` and rendered under `data-role="staking-unstake-error"` while an Unstake popover remains open.

The decisive UI boundary is shared by the CTA and MAX controls. These helpers accept a dynamic action keyword, such as `:actions/submit-staking-undelegate` or `:actions/set-staking-undelegate-amount-to-max`. Replicant expects the click event value to be a collection of action vectors. Therefore a dynamic helper must render `[[:actions/submit-staking-undelegate]]`, not the once-wrapped bare vector `[:actions/submit-staking-undelegate]`. Before this correction, a direct programmatic dispatch reached the action but a real CTA click did nothing; every lower-layer test could consequently pass while the user-facing button remained inert.

Relevant tests now include `test/hyperopen/staking/unstake_actions_regression_test.cljs`, `test/hyperopen/staking/effects_test.cljs`, `test/hyperopen/staking/effects_freshness_test.cljs`, `test/hyperopen/account/context_test.cljs`, `test/hyperopen/views/staking_view_test.cljs`, and `test/hyperopen/runtime/effect_adapters/staking_test.cljs`. The browser entry point is `tools/playwright/test/staking-regressions.spec.mjs`. The generated CLJS runner runs every discovered namespace, so the practical focused validation is source-targeted test additions followed by the full generated test command.

## Plan of Work

### Milestone 1 — RED evidence, including the actual DOM no-op

Completed. Approved regression coverage seeded a connected master owner, a distinct owned selected subaccount marked stale/unavailable by generic trading state, owner-current delegation provenance, and amount `101`. It also covered future/equal/expired/nil `:locked-until-timestamp` boundaries and error rendering. The initial generated RED run had 13 intended failures; a duplicate follow-up run had 3 intended failures.

The decisive addition was a real browser click. The actual Unstake CTA left state unchanged and made no request, whereas direct debug dispatch of the same action succeeded. This proved that the primary failure occurred before the frozen account/lock guards: dynamic action keywords were rendered as bare event vectors in the shared CTA and MAX helpers. The browser RED is retained as the required proof that the user path, not merely direct dispatch, was broken.

### Milestone 2 — Correct the event envelope, staking guards, and feedback path

Implemented and accepted. The smallest source changes are:

1. In `src/hyperopen/views/staking/popovers.cljs`, change dynamic `popover-cta-button` and MAX `:on :click` values from a bare action vector to a collection containing the action vector. This restores real Replicant click dispatch without changing action IDs, payloads, or control labels.
2. In `src/hyperopen/staking/account_scope.cljs`, add the staking-specific blocker that returns the existing read-only reason for active spectate mode or an active trader-portfolio route and otherwise returns nil, intentionally ignoring raw selected-subaccount availability. In `src/hyperopen/staking/actions.cljs` and `src/hyperopen/staking/effects.cljs`, replace only native-staking generic-blocker calls with that helper. `account-context/mutations-blocked-message` remains untouched for trading/funding flows.
3. In `src/hyperopen/staking/actions.cljs`, expose the matching delegation's amount and `:locked-until-timestamp`, then apply a pure strict-future predicate/formatter before emitting the submit effect. A future lock uses `>` and the exact inline message; nil, expired, and equal locks remain valid. No startup fetch, endpoint normalization, or chain/environment decision changes.
4. In `src/hyperopen/views/staking_view.cljs` and `src/hyperopen/views/staking/popovers.cljs`, thread the existing VM error to the Unstake content and render it under the stable selector while retaining page-level behavior, close/Escape, focus, and in-flight disabled behavior.

The implementation did not modify `src/hyperopen/account/context.cljs`, startup route fetching, API endpoint normalization, signing environment, public action/effect IDs, `:type "tokenDelegate"`, `:isUndelegate true`, locale-aware number parsing, exact delegated-amount cap, freshness request generations, or master-owner signing. Generated CLJS passed 5,801 tests / 32,056 assertions, and all later final validation is recorded below.

### Milestone 3 — Demonstrate the 101-HYPE popover flow and release it safely

The simulator path is stable. `tools/playwright/test/staking-regressions.spec.mjs` now has focused master cases, all passing 3/3. They exercise the real `/staking` Unstake popover with a master owner, stale selected subaccount, matching unlocked 101-HYPE delegation, and owner provenance. A real click enters `Submitting...`, the captured request contains `isUndelegate: true` and `wei: 10100000000`, and the signer is the owner, never the selected subaccount. The lock branch retains the open popover, shows the exact local message, and makes no simulator request. The regression must continue to use test-local bridge/simulator state only and must never call a live wallet.

The final browser pass is complete. Trusted Escape RED exposed a dialog-focus gap; after attaching the shared dialog-focus lifecycle, focused Escape passed 1/1. Full staking Playwright then passed 6/6. Browser MCP governed QA for `staking-route` passed visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf at 375, 768, 1280, and 1440. The review included the real CTA/MAX event envelope, pending/repeated-click behavior, amount/validator input, Escape close, master stale-selection submission, future-lock error, exchange-error placement, and overlay/clipping behavior. `npm run browser:cleanup` passed with no sessions remaining.

Final post-fix repository gates also passed. The recorded outcome is `npm run gates` PASS 34/34 in 1m39s with 6,522 tests / 35,456 assertions. No acceptance criterion remains blocked or failed, so this plan moves to `docs/exec-plans/completed/`.

## Concrete Steps

Run commands from `/Users/barry/.codex/worktrees/036e/hyperopen`.

1. The RED phase is complete. From `/Users/barry/.codex/worktrees/036e/hyperopen`, its full generated-suite commands and evidence were:

       npm run test:runner:generate
       npx shadow-cljs --force-spawn compile test
       node out/test.js

   Recorded RED: the initial run had 13 intended failures; the duplicate follow-up had 3 intended failures. The real browser click RED showed no state change and no request even though direct debug dispatch worked. These failures identified the malformed CTA/MAX Replicant event envelope in addition to the stale-selection, lock, and error-placement contracts.

2. The initial GREEN CLJS result is recorded from the same command sequence:

       npm run test:runner:generate
       npx shadow-cljs --force-spawn compile test
       node out/test.js

   Recorded GREEN: 5,801 tests / 32,056 assertions, with no failures or errors. Earlier in this implementation pass, `npx shadow-cljs --force-spawn compile app` and `npm run test:websocket` also passed. Final gates below supersede this initial evidence.

3. The focused browser regression was green:

       npx playwright test tools/playwright/test/staking-regressions.spec.mjs --grep "master.*101.*unstake"

   Recorded GREEN: focused master cases passed 3/3. This is the proof that a real click now dispatches, rather than only the debug-dispatch path working.

4. Static re-review and the final browser interaction pass are complete. The browser interaction pass exercised a second CTA click while the first simulated submission was unresolved and confirmed that the existing `:undelegate?` guard/disabled control prevents a second request.

5. The full staking Playwright file, the governed four-width review, and cleanup passed:

       npx playwright test tools/playwright/test/staking-regressions.spec.mjs
       npm run qa:design-ui -- --targets staking-route --manage-local-app
       npm run browser:cleanup

   Recorded result: full staking Playwright GREEN 6/6. Governed QA returned PASS for visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf at 375, 768, 1280, and 1440. It included nested event, pending/idempotency, MAX, lock, inline error, and Escape interactions. Cleanup PASS: no browser-inspection sessions remained.

6. The mandated quality gates have passed after code changes:

       npm run gates

   Recorded final result: PASS 34/34 in 1m39s, covering 6,522 tests / 35,456 assertions and including `npm run check`, `npm test`, and `npm run test:websocket`.

## Validation and Acceptance

Acceptance is behaviorally complete only when all of the following are observable.

- A real DOM click on the dynamic Unstake CTA emits a nested Replicant event collection, not a bare action vector. It changes state and reaches the existing undelegate action; a dynamic MAX click likewise reaches its existing amount action. The original browser RED demonstrated that the bare-vector representation left state unchanged and made no request. Focused master Playwright passed 3/3 and full staking Playwright passed 6/6.

- A deterministic ClojureScript fixture has a connected master owner, a distinct owned selected subaccount whose raw trading state is stale/unavailable, owner-current `:delegations` provenance, and a matching unlocked delegation of at least `101` HYPE. Calling `submit-staking-undelegate` for `101` emits exactly one `:effects/api-submit-staking-undelegate` request with normalized validator, `:type "tokenDelegate"`, `:isUndelegate true`, and `:wei 10100000000`; it does not return the generic selected-subaccount-unavailable error, a stale-data/loading error, or a selected-subaccount signer. `test/hyperopen/staking/unstake_actions_regression_test.cljs` is the primary proof surface.

- The shared effect guard accepts the same normal master/stale-selected-subaccount fixture and calls `submit-token-delegate!` with the owner. Spectate mode and trader-portfolio inspection still return their existing read-only errors at both the action and effect entry points and never call the submit function. `test/hyperopen/staking/actions_test.cljs` and `test/hyperopen/staking/effects_test.cljs` are the proof surfaces.

- A matching selected-validator delegation with a finite lock later than the controlled `now-ms` emits no submit effect and displays exactly `This delegation is locked until <M/D/YYYY - HH:MM:SS>.`. The string contains no additional punctuation, timezone, locale word, or generic error prefix. Nil, expired, and equal-to-now locks emit the standard valid undelegate request. `test/hyperopen/staking/actions_test.cljs` proves all four boundaries and the formatter output.

- A controlled successful submit visibly enters the existing pending state, then clears `:undelegate?`, clears the amount input, shows `Unstake submitted.`, and dispatches `[:actions/load-staking]`. A controlled exchange rejection or runtime error clears pending and presents the exact actionable error inside the still-open Unstake popover as `data-role="staking-unstake-error"`, while retaining the existing page-level error surface. `test/hyperopen/staking/effects_test.cljs` and `test/hyperopen/views/staking_view_test.cljs` are the proof surfaces.

- An account switch, missing delegation response, mismatched `:loaded-for :delegations`, invalid validator, amount above that validator's delegation, and a future lock remain unable to submit. The behavior must be an explicit local error/retry path, not a transaction with uncertain account data. `test/hyperopen/staking/actions_test.cljs` and `test/hyperopen/staking/effects_freshness_test.cljs` are the proof surfaces.

- The committed focused master Playwright cases passed 3/3, and the full staking file passed 6/6. They open the real `/staking` Unstake popover against simulator-backed master/stale-selected-subaccount state, enter `101`, click the actual CTA, observe the pending and terminal UI, and inspect the captured request for the master signer and undelegate payload. The lock branch observes the exact in-popover message and zero request. `npx playwright test tools/playwright/test/staking-regressions.spec.mjs --grep "master.*101.*unstake"` and `npx playwright test tools/playwright/test/staking-regressions.spec.mjs` are the recorded proofs.

- Static re-review found no pending-submit idempotency defect, and the final browser interaction pass confirmed that a second real CTA click while the first simulated submission is pending cannot create a second request.

- Browser QA for `/staking` passed all six required categories—visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf—at 375, 768, 1280, and 1440. Browser cleanup passed with no sessions remaining. Residual note: the connected popover at 1440 was manually inspected for layout/interaction; request signing remained simulator-backed by design, so no live wallet transaction was sent.

- Final post-fix `npm run gates` passed: 34/34 in 1m39s, with 6,522 tests / 35,456 assertions, including `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

The deterministic fixtures and simulators are local and repeatable. The completed tests control the lock evaluation time and deferred submit settlement rather than sleeping or relying on a future wall-clock date. They never submit through a real wallet. Re-running `load-staking` for the same owner may refresh data but may not permit an old or another-account response to authorize a new unstake; the completed repeated-click browser pass confirms the in-flight guard prevents a second request.

If a future lock-format test varies by the runner's timezone, keep the formatter's local-date semantics explicit and assert local components rather than changing the user-facing format to a locale-dependent string. The simulator supports deterministic submit capture for the focused flow. The residual live-wallet boundary is intentional: connected-popover manual inspection at 1440 verified layout/interaction, while request signing stayed simulator-backed. Future recovery work should use file-scoped patches and never reset or discard unrelated changes.

## Artifacts and Notes

Implemented source and test surfaces:

    src/hyperopen/account/context.cljs
      native-staking-account-address -> owner-address in normal connected state.
      native-staking-account-address -> effective-account-address only for spectate/trader inspection.

    src/hyperopen/staking/actions.cljs
      load-staking saves [:staking :account-address] before issuing five owner-addressed reads.
      submit-staking-undelegate uses the staking-specific blocker, validates the matching delegation and strict-future lock, then requires :delegations provenance.

    src/hyperopen/staking/effects.cljs
      api-submit-staking-undelegate! delegates to shared submit-staking-action!.
      submit-staking-action! uses the staking-specific blocker before submit-token-delegate! with owner-address and reloads staking after status "ok".

    src/hyperopen/api/endpoints/account/staking.cljs
      normalize-delegation-row preserves a parsed :locked-until-timestamp from all supported wire aliases.

    src/hyperopen/views/staking/popovers.cljs
      dynamic CTA and MAX controls emit a collection containing their dynamic action vector, as required by Replicant.
      unstake-popover-content renders the existing form/action/exchange error under staking-unstake-error.

    tools/playwright/test/staking-regressions.spec.mjs
      contains trusted Escape plus master 101-HYPE Unstake simulator cases; focused Escape passed 1/1, focused master passed 3/3, and the full staking file passed 6/6.

    test/hyperopen/staking/unstake_actions_regression_test.cljs
      covers master stale-selection safety and future/equal/expired/nil lock boundaries.

    test/hyperopen/staking/effects_test.cljs and test/hyperopen/views/staking_view_test.cljs
      cover effect-layer blocker/error lifecycle and the in-popover error surface.

Recorded validation evidence:

    RED initial generated CLJS run: 13 intended failures.
    RED duplicate follow-up: 3 intended failures.
    Browser RED: real CTA click left state unchanged and made no request; direct debug dispatch worked.
    GREEN generated CLJS run: 5,801 tests / 32,056 assertions, no failures or errors.
    GREEN focused master Playwright: 3/3 passed.
    Browser Escape RED: 1 intended failure for trusted Escape on the unfocused open dialog.
    Browser Escape GREEN: 1/1 after shared dialog-focus lifecycle attachment.
    Earlier checks: app compilation and websocket validation passed.
    Static re-review: clean.
    Final npm run gates: PASS 34/34 in 1m39s, 6,522 tests / 35,456 assertions.
    Full staking Playwright: PASS 6/6.
    Governed QA: PASS all six categories at 375, 768, 1280, and 1440.
    Browser cleanup: PASS, no sessions.

Residual note: the connected popover at 1440 was manually inspected for layout and interaction; its request signing remained simulator-backed by design, so no live wallet transaction was used. This is a deliberate safety boundary, not an acceptance blocker.

## Interfaces and Dependencies

The existing public runtime interfaces are preserved:

- `:actions/submit-staking-undelegate` accepts no external arguments. It reads user-entered `[:staking-ui :undelegate-amount]`, selected validator, and current state.
- `:effects/api-submit-staking-undelegate` receives the existing map shape shown in Context and Orientation.
- `hyperopen.staking.effects/api-submit-staking-undelegate!` calls the existing `hyperopen.api.trading/submit-token-delegate!` dependency as `(submit-token-delegate! store owner-address action)`.
- `hyperopen.account.context/native-staking-account-address` remains the single semantic identity helper for native staking reads; `owner-address` remains the signer in normal connected state.
- `hyperopen.staking.account-scope/staking-mutations-blocked-message` is the internal app-state helper used by the action and shared effect guard. It preserves only spectate/trader read-only reasons and is not a new public runtime action or API.
- The internal strict-future lock predicate and `M/D/YYYY - HH:MM:SS` formatter in the staking action path accept normalized millisecond timestamps and an explicit evaluation time for deterministic tests.
- Dynamic `popover-cta-button` and MAX event attributes must contain a collection of action vectors. When passed `:actions/submit-staking-undelegate`, their click value is `[[:actions/submit-staking-undelegate]]`; bare `[:actions/submit-staking-undelegate]` is not a valid dynamic Replicant event envelope.
- The existing `hyperopen.views.ui.dialog-focus/dialog-focus-on-render` lifecycle is attached to the action-popover `role="dialog"`. It gives the existing Escape keydown action trusted keyboard delivery without a separate global listener.

No new library, endpoint, startup fetch, chain/environment selector, browser-only production API, public action name, wire schema, or performance optimization was added.

Plan revision note, 2026-08-03 13:30Z / Codex: Initial active ExecPlan created from the direct master-account unstake report and screenshot. It records `54be77fdc`'s existing account-scoping behavior, explicitly avoids guessing a root cause, and requires a controlled master-with-selected-subaccount reproduction before any corrective source edit.
Plan revision note, 2026-08-03 13:40Z / Codex: Replaced the diagnosis-pending contract with two demonstrated defects: generic selected-subaccount blocking wrongly applies to normal master staking in both action/effect layers, and normalized delegation locks are ignored. Added exact local lock behavior, in-popover error rendering, stable 101-HYPE regression requirements, and explicit non-goals for startup surface fetching and chain environment.
Plan revision note, 2026-08-03 14:16Z / Codex: Recorded the decisive browser discovery that the shared dynamic CTA and MAX helpers emitted bare action vectors, making real clicks no-ops while direct dispatch worked. Recorded the nested-envelope correction, stale-selection blocker, strict lock/error work, RED 13 then 3 failures, initial GREEN CLJS 5,801/32,056, focused Playwright 3/3, and the then-outstanding idempotency, QA, and gate work.
Plan revision note, 2026-08-03 14:16Z / Codex: Recorded clean static re-review and the then-current gate evidence; the final post-fix 1m39 gate result supersedes it.
Plan revision note, 2026-08-03 14:33Z / Codex: Recorded trusted-Escape RED 1, shared dialog-focus lifecycle remediation, focused Escape GREEN 1/1, full staking Playwright GREEN 6/6, governed QA PASS for all six categories at 375/768/1280/1440, connected-popover 1440 residual manual note, and cleanup PASS/no sessions. Superseded final gate evidence with the post-fix `npm run gates` PASS 34/34 in 1m39s (6,522 tests / 35,456 assertions), then completed the plan.
