# Refactor Wallet Agent Runtime Authority Boundaries

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is governed by `docs/PLANS.md` and `.agents/PLANS.md`. The tracked work item for this plan is `hyperopen-g0cv`; that `bd` issue was the local tracker reference while this file was active.

## Purpose / Big Picture

Hyperopen has one public wallet agent runtime namespace, `src/hyperopen/wallet/agent_runtime.cljs`, that currently owns too many jobs at once: exchange error formatting, storage preference switching, passkey protection migration, approve-agent request orchestration, session persistence, enable trading, unlock trading, and lock trading. The file is currently 869 lines, which makes security-sensitive remembered-session behavior harder to change safely.

After this refactor, existing callers still use the same public namespace, `hyperopen.wallet.agent-runtime`, and the same public functions. The observable improvement is that the root runtime file becomes a stable compatibility facade under 500 lines, while focused internal namespaces under `src/hyperopen/wallet/agent_runtime/` own the narrower behavior. A developer can see the change working by running the targeted wallet runtime tests before and after the refactor, confirming the same public API behavior, and then running the required repository gates.

This is an internal runtime refactor, not a UI change. Browser QA is skipped unless implementation touches Playwright-covered passkey live behavior or the committed browser passkey regression suite needs to change.

## Progress

- [x] (2026-04-18 22:18Z) Confirmed `bd show hyperopen-g0cv --json` reports the task as `in_progress` and scoped to refactoring wallet agent runtime authority boundaries.
- [x] (2026-04-18 22:20Z) Read `AGENTS.md`, `docs/PLANS.md`, `.agents/PLANS.md`, `docs/MULTI_AGENT.md`, and `docs/WORK_TRACKING.md` before authoring this active plan.
- [x] (2026-04-18 22:23Z) Measured the current root runtime size with `wc -l src/hyperopen/wallet/agent_runtime.cljs dev/namespace_size_exceptions.edn`; the root runtime is 869 lines and has an active size exception with `:max-lines 900`.
- [x] (2026-04-18 22:25Z) Inspected existing wallet runtime tests and consumers, including `test/hyperopen/wallet/agent_runtime_test.cljs`, `test/hyperopen/api_wallets/effects_test.cljs`, `test/hyperopen/runtime/action_adapters/wallet_test.cljs`, `test/hyperopen/core_bootstrap/agent_trading_lifecycle_test.cljs`, and `test/hyperopen/api/trading/approve_agent_test.cljs`.
- [x] (2026-04-18 22:31Z) Created this active ExecPlan for `hyperopen-g0cv` with tests-first race coverage, facade extraction milestones, namespace-size cleanup, and final validation expectations.
- [x] (2026-04-18 22:45Z) Reviewed the plan against the generated test runner and removed an unsupported filtered `node out/test.js --test=...` command from the plan and spec artifact.
- [x] (2026-04-18 22:55Z) Merged `acceptance_test_writer` and `edge_case_test_writer` proposals into `tmp/multi-agent/hyperopen-g0cv/approved-test-contract.json`.
- [x] (2026-04-18 23:20Z) Materialized the approved RED tests in `test/hyperopen/wallet/agent_runtime_test.cljs`, new `test/hyperopen/wallet/agent_runtime_edge_test.cljs`, and regenerated `test/test_runner_generated.cljs`.
- [x] (2026-04-18 23:23Z) Verified RED with `node out/test.js`: 3279 tests, 17672 assertions, 20 expected failures, 0 errors, all failures in `hyperopen.wallet.agent-runtime-edge-test`.
- [x] (2026-04-18 22:48Z) Extracted the public facade plus focused internal namespaces for errors, state projection, storage mode, protection mode, approval, enable, and unlock/lock. `src/hyperopen/wallet/agent_runtime.cljs` is now 39 lines.
- [x] (2026-04-18 22:48Z) Implemented the approved RED behavior fixes: passkey-only locking, stale enable completion suppression after protection-mode switch, duplicate-enable newer-wins ordering, stale unlock failure suppression, and fail-closed cleanup after plain-session persistence failure.
- [x] (2026-04-18 22:48Z) Removed the stale `src/hyperopen/wallet/agent_runtime.cljs` namespace-size exception after confirming the facade is below 500 lines and all extracted source namespaces are below the default threshold.
- [x] (2026-04-18 22:51Z) Final focused and required validation passed: `npm run test:runner:generate`, `npx shadow-cljs --force-spawn compile test`, `node out/test.js`, `npm run lint:namespace-sizes`, `npm run lint:namespace-sizes:test`, `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-04-18 follow-up) Addressed parent review production gaps by invalidating `:active-enable-token` on storage-mode switches and injecting real enable-flow cleanup callbacks from the wallet action adapter.
- [x] (2026-04-18 follow-up) Validated the follow-up with `npx shadow-cljs --force-spawn compile test`, `node out/test.js`, `npm run check`, `npm test`, and `npm run test:websocket`; all completed successfully after adding the parent closeout progress item required by active-plan docs lint.
- [x] (2026-04-19 00:17Z) Final read-only reviewer identified cross-operation token staleness between unlock and enable plus stale passkey lockbox cleanup after token invalidation.
- [x] (2026-04-19 00:21Z) Added `test/hyperopen/wallet/agent_runtime_concurrency_test.cljs`; verified RED with `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`, which failed with 4 expected assertions in the new concurrency suite.
- [x] (2026-04-19 00:28Z) Fixed the review findings by invalidating competing operation tokens across enable, unlock, storage-mode switch, protection-mode switch, and lock, and by deleting a passkey lockbox when a passkey enable becomes stale after lockbox creation.
- [x] (2026-04-19 00:31Z) Verified GREEN for the review fixes with `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`: 3282 tests, 17683 assertions, 0 failures, 0 errors.
- [x] (2026-04-19 00:45Z) Follow-up reviewer identified reset-time token reuse: `reset-agent-state!` dropped `:runtime-operation-seq`, allowing stale same-type work to become current after a reset and newer operation.
- [x] (2026-04-19 00:49Z) Added `storage-mode-reset-does-not-reuse-enable-token-for-stale-completion-test`; verified RED with `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`: 3283 tests, 17688 assertions, 5 expected failures, 0 errors.
- [x] (2026-04-19 00:51Z) Preserved `:runtime-operation-seq` across `reset-agent-state!`; verified GREEN with `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`: 3283 tests, 17688 assertions, 0 failures, 0 errors.
- [x] (2026-04-19 00:58Z) Fresh required gates passed after the monotonic-token fix: `npm run check`, `npm test` with 3283 tests and 17688 assertions, and `npm run test:websocket` with 438 tests and 2524 assertions.
- [x] (2026-04-19 01:11Z) Final narrow reviewer identified three remaining async side-effect races: stale unlock cache side effects, ungated async protection-mode migrations, and lock not invalidating active enable.
- [x] (2026-04-19 01:16Z) Added RED coverage for those paths in `agent_runtime_concurrency_test.cljs`; the generated test run failed with 10 expected assertions across stale unlock cache, stale protection migration, and lock-vs-enable ordering.
- [x] (2026-04-19 01:23Z) Fixed the final review findings by injecting stale-unlock cache clearing, adding `:active-protection-token` gating to protection migrations, invalidating protection tokens from competing operations, and making lock invalidate enable/unlock/protection tokens.
- [x] (2026-04-19 01:31Z) Fresh required gates passed after the final async side-effect fixes: `npm run check`, `npm test` with 3285 tests and 17699 assertions, and `npm run test:websocket` with 438 tests and 2524 assertions.
- [x] (2026-04-19 01:42Z) Final stale-unlock cache review found that owner-scoped stale cleanup could remove a newer passkey-enable cache; fixed by moving unlock cache commit into the runtime-current branch and making production unlock adapters call lockbox unlock with `:cache-session? false`.
- [x] (2026-04-19 01:48Z) Fresh required gates passed after the cache-boundary fix: `npm run check`, `npm test` with 3285 tests and 17700 assertions, and `npm run test:websocket` with 438 tests and 2524 assertions.
- [x] (2026-04-19 01:50Z) Parent closeout complete: acceptance criteria passed, plan is ready to move from `active` to `completed`, and `bd` can be closed.

## Surprises & Discoveries

- Observation: `src/hyperopen/wallet/agent_runtime.cljs` has no `:require` form today; it centralizes helper functions and all public runtime entry points in one namespace.
  Evidence: `rg -n "^\(ns |^\(defn" src/hyperopen/wallet/agent_runtime.cljs` showed one namespace declaration and all runtime functions, including `exchange-response-error`, `set-agent-storage-mode!`, `set-agent-local-protection-mode!`, `approve-agent-request!`, `enable-agent-trading!`, `unlock-agent-trading!`, and `lock-agent-trading!`.

- Observation: the current namespace-size exception for `src/hyperopen/wallet/agent_runtime.cljs` is stale relative to this ticket's goal.
  Evidence: `dev/namespace_size_exceptions.edn` contains an entry for `src/hyperopen/wallet/agent_runtime.cljs` with `:max-lines 900`, while this plan requires the root facade to drop below the default 500-line threshold and remove the exception unless a later implementation proves it is still needed.

- Observation: existing wallet runtime coverage already protects several happy and failure paths, but it does not explicitly protect the requested async race semantics.
  Evidence: `test/hyperopen/wallet/agent_runtime_test.cljs` covers migration between plain and passkey, unlock guard rails, enable success and failures, non-ok exchange responses, promise rejection, and default chain-id fallback, but no test name currently describes in-flight enable plus mode-switch, duplicate enable approval ordering, stale unlock failure, or fail-closed cleanup after post-approval persistence failure.

- Observation: API wallet approve and remove consumers are already important focused validation surfaces for `approve-agent-request!`.
  Evidence: `test/hyperopen/api_wallets/effects_test.cljs` asserts `:approve-agent-request!` calls for authorize and remove flows and checks `:persist-session? false`, refresh behavior, session clearing for default-row removal, and rejection handling.

- Observation: the generated CLJS test runner does not currently support a committed per-namespace `--test` filter.
  Evidence: `test/test_runner.cljs` calls `generated-runner/run-generated-tests` unconditionally, and `test/test_runner_generated.cljs` builds one generated `run-tests` invocation. The implementation workflow should use `npm test` and `npx shadow-cljs --force-spawn compile test`, not an invented `node out/test.js --test=...` flag.

- Observation: the approved RED suite currently fails only on the unsafe behavior it was meant to expose.
  Evidence: after `npm run test:runner:generate` and `npx shadow-cljs --force-spawn compile test` succeeded, `node out/test.js` ran 3279 tests with 17672 assertions and failed with 20 failures, 0 errors. The failures are in `lock-agent-trading-locks-passkey-ready-only-and-preserves-unlock-metadata-test`, `enable-agent-trading-ignores-stale-completion-after-protection-mode-switch-test`, `duplicate-enable-agent-trading-newer-completion-wins-test`, `stale-unlock-failure-does-not-revert-new-ready-session-test`, and `enable-agent-trading-persistence-failure-cleans-stale-session-and-cache-test`.

- Observation: the facade split removed the stale source namespace-size pressure without adding a new oversized runtime namespace.
  Evidence: `wc -l src/hyperopen/wallet/agent_runtime.cljs src/hyperopen/wallet/agent_runtime/*.cljs` reported 39 lines for the facade and 349 lines or fewer for every extracted runtime namespace.

- Observation: independent enable and unlock tokens were not enough because the operations can race against each other.
  Evidence: the final reviewer showed that a deferred unlock could complete after a newer enable and project the older signer back to `:ready`. The new `unlock-success-is-stale-after-newer-enable-completes-test` failed before the fix by restoring `0xold-agent` over `0xnew-agent`.

- Observation: stale passkey enable work needs cleanup even when token invalidation happens after lockbox creation starts.
  Evidence: the new `stale-passkey-enable-deletes-lockbox-after-token-invalidation-test` failed before the fix because `delete-locked-session!` was not called after a passkey lockbox create resolved stale.

- Observation: invalidated tokens must remain globally monotonic across agent-state resets.
  Evidence: the follow-up reviewer showed that `reset-agent-state!` dropped `:runtime-operation-seq`; the new `storage-mode-reset-does-not-reuse-enable-token-for-stale-completion-test` failed before the fix because stale enable A reused token `1` after a storage reset and newer enable B.

- Observation: stale async work can be dangerous even when store projection is suppressed if the injected function has already performed a side effect.
  Evidence: `agent-lockbox/unlock-locked-session!` caches the unlocked session before resolving. `unlock-success-is-stale-after-newer-enable-completes-test` now models that side effect and failed before the fix because the stale old session remained in the unlocked cache.

- Observation: protection-mode migration is an async credential operation and needs the same stale-completion controls as enable and unlock.
  Evidence: `protection-mode-migration-is-stale-after-storage-mode-switch-test` failed before the fix because an old plain-to-passkey migration finalized `:local/:passkey` ready state and cached credentials after a storage-mode switch reset the agent to `:session`.

- Observation: public lock is also a credential operation boundary.
  Evidence: `lock-agent-trading-invalidates-in-flight-enable-test` failed before the fix because a deferred enable approval completed after lock and projected a new ready signer over the locked remembered passkey session.

- Observation: stale unlock cleanup must not be owner-scoped after a newer passkey enable.
  Evidence: a narrow reviewer showed that clearing by owner after stale unlock would remove a valid newer passkey-enable cache. The runtime now receives uncached unlock results from production adapters and commits the cache only inside the current-token success branch. `unlock-success-is-stale-after-newer-enable-completes-test` asserts the newer passkey cache survives stale unlock completion.

## Decision Log

- Decision: keep `hyperopen.wallet.agent-runtime` as a stable public compatibility facade.
  Rationale: callers already import `hyperopen.wallet.agent-runtime`, and the requested public entry points must keep exporting `exchange-response-error`, `runtime-error-message`, `set-agent-storage-mode!`, `set-agent-local-protection-mode!`, `approve-agent-request!`, `enable-agent-trading!`, `unlock-agent-trading!`, and `lock-agent-trading!`. Moving callers during this refactor would increase blast radius without improving the runtime boundary.
  Date/Author: 2026-04-18 / Codex acting as `spec_writer`

- Decision: extract focused internal namespaces under `src/hyperopen/wallet/agent_runtime/` named `errors.cljs`, `state_projection.cljs`, `storage_mode.cljs`, `protection_mode.cljs`, `approval.cljs`, `enable.cljs`, and `unlock.cljs`.
  Rationale: these names map directly to the current responsibilities inside the root runtime file, which lets the worker move behavior without inventing new product concepts. The root facade can delegate through these modules and preserve public names.
  Date/Author: 2026-04-18 / Codex acting as `spec_writer`

- Decision: preserve the current dependency-injection style across extracted modules.
  Rationale: the existing runtime functions accept concrete storage, lockbox, clock, crypto, and exchange functions from adapters. The extracted runtime modules must continue receiving those functions through opts maps instead of directly requiring browser storage, IndexedDB, WebAuthn, or exchange infrastructure. This keeps side effects at adapter boundaries and keeps runtime tests deterministic.
  Date/Author: 2026-04-18 / Codex acting as `spec_writer`

- Decision: keep adjacent authority boundaries unchanged.
  Rationale: `agent_session.cljs` remains the owner of browser-storage keys, raw session persistence, passkey metadata persistence, storage preferences, and key metadata. `agent_lockbox.cljs` remains the encrypted lockbox and WebAuthn owner. `api/trading.cljs` remains the signing, signer/address reconciliation, and missing-agent invalidation owner. This plan narrows `agent_runtime.cljs`; it does not move lower-level storage or exchange signing authority into runtime helpers.
  Date/Author: 2026-04-18 / Codex acting as `spec_writer`

- Decision: require risk-driven characterization tests before the refactor.
  Rationale: the highest-risk behavior is not the easy happy path. It is async ordering around enable, mode changes, persistence cleanup, and unlock failures. Tests that fail before implementation and pass after the fix will give the worker permission to preserve existing behavior where safe and correct unsafe behavior where tests prove it is unsafe.
  Date/Author: 2026-04-18 / Codex acting as `spec_writer`

- Decision: skip governed browser QA by default and list the Playwright passkey regression as conditional.
  Rationale: the requested work is not UI-facing and should be validated mainly through deterministic CLJS tests and repository gates. If implementation touches Playwright-covered passkey live behavior, updates browser harnesses, or changes visible passkey flow behavior, run `npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "passkey|session toggles|locked remembered passkey session submit triggers unlock flow instead of recovery"` and then `npm run browser:cleanup`.
  Date/Author: 2026-04-18 / Codex acting as `spec_writer`

- Decision: use the supported generated test runner commands for focused validation instead of a fake namespace filter.
  Rationale: the local test runner does not parse `--test`, so listing that command would make the plan non-executable. Workers should name the relevant focused test files in review notes, but use `npm run test:runner:generate`, `npx shadow-cljs --force-spawn compile test`, and `npm test` for committed validation unless a real filtered runner is added in a separate change.
  Date/Author: 2026-04-18 / Codex

- Decision: freeze a compact approved RED test contract instead of materializing every proposal item.
  Rationale: the accepted set covers public facade compatibility, approve-agent persistence and failure edges, local/passkey enable safety, lock guardrails, stale enable completion, duplicate enable ordering, stale unlock failure, and fail-closed persistence cleanup. A separate storage-mode race and duplicate API-wallet acceptance tests are deferred because they exercise the same implementation paths already covered by the approved contract plus existing suites.
  Date/Author: 2026-04-18 / Codex

- Decision: use small internal operation tokens on wallet agent state to gate asynchronous enable and unlock completions.
  Rationale: the RED failures were stale async writes, not algorithmic complexity problems. `:runtime-operation-seq` plus `:active-enable-token` and `:active-unlock-token` let the runtime deterministically ignore older completions; protection-mode switching invalidates active enable work so stale plain completions cannot persist, cache, or project after passkey preference wins.
  Date/Author: 2026-04-18 / Codex acting as `worker`

- Decision: treat enable, unlock, protection-mode switch, storage-mode switch, and passkey lock as competing credential operations for token invalidation.
  Rationale: a newer credential operation should be the only operation allowed to project or persist remembered-session state. Cross-operation invalidation prevents a late unlock from restoring an older signer over a newer enable and prevents mode switches from being undone by stale completions.
  Date/Author: 2026-04-19 / Codex

- Decision: delete a passkey lockbox when passkey enable becomes stale after `create-locked-session!` has produced a lockbox.
  Rationale: `agent-lockbox/create-locked-session!` persists the lockbox before resolving. If a mode switch or newer operation invalidates the token while that promise is pending, leaving the lockbox behind violates fail-closed cleanup. The runtime now calls the injected `delete-locked-session!` and suppresses delete failures for this stale cleanup path.
  Date/Author: 2026-04-19 / Codex

- Decision: preserve `:runtime-operation-seq` across `reset-agent-state!`.
  Rationale: active operation keys can be invalidated during mode resets, but the sequence number is part of stale-completion safety, not display state. Keeping it monotonic prevents a stale same-type async operation from matching a new operation token after reset.
  Date/Author: 2026-04-19 / Codex

- Decision: add `:active-protection-token` for async protection-mode migration.
  Rationale: moving between plain and passkey can create lockboxes, delete lockboxes, persist metadata, cache unlocked sessions, and project ready state after Promise boundaries. Treating it as a first-class credential operation prevents stale migration completion from undoing newer storage/protection decisions.
  Date/Author: 2026-04-19 / Codex

- Decision: clear unlocked cache on stale unlock success.
  Rationale: this approach was superseded by moving cache commit out of the concrete unlock path for runtime callers. Owner-scoped stale cleanup could remove a newer passkey-enable cache.
  Date/Author: 2026-04-19 / Codex

- Decision: make runtime unlock callers use no-cache lockbox unlock and cache only after token validation.
  Rationale: stale unlock completion must not overwrite or delete a newer valid passkey cache. `agent-lockbox/unlock-locked-session!` now supports `:cache-session? false`; action and effect adapters use that mode and inject `cache-unlocked-session!` so `unlock.cljs` commits the cache only when `:active-unlock-token` is still current.
  Date/Author: 2026-04-19 / Codex

- Decision: make `lock-agent-trading!` a no-op unless the current wallet owns a ready local/passkey session.
  Rationale: locking should clear only the in-memory unlocked cache for remembered passkey sessions. Plain sessions and missing-wallet state do not have a passkey unlock path, so clearing their cache or projecting `:locked` was unsafe.
  Date/Author: 2026-04-18 / Codex acting as `worker`

## Outcomes & Retrospective

Initial planning is complete. This plan freezes scope for `hyperopen-g0cv`: split the overgrown wallet agent runtime into narrower runtime modules while preserving the public facade and current adapter-injected side-effect boundaries. No production code has been changed by the `spec_writer` phase.

The implementation is expected to reduce structural complexity by moving unrelated authority into named internal namespaces. If the worker discovers that a current behavior is unsafe, especially around async races or stale persistence cleanup, the behavior change must be pinned by a failing characterization test first and recorded in this section with the exact test name and observed before/after behavior.

Worker implementation is complete. The public `hyperopen.wallet.agent-runtime` namespace is now a 39-line facade delegating to focused internal runtime namespaces. The approved RED failures are fixed without weakening tests: lock now only affects ready local/passkey sessions, stale enable and unlock completions are ignored through deterministic operation tokens, and plain-session persistence failure clears stale persisted/cache state when cleanup functions are injected. Browser QA remains skipped because this was not UI-facing and did not change Playwright harnesses or visible passkey interaction flows.

Follow-up review gaps are closed in production code: storage-mode switching now invalidates the in-flight enable token just like protection-mode switching, and the real action adapter now supplies `agent-session/clear-agent-session-by-mode!` plus `agent-lockbox/clear-unlocked-session!` to `enable-agent-trading!` so post-approval persistence failure cleanup is available outside tests.

Final review found additional stale-operation races across operation types, reset boundaries, and injected side effects, which are now covered by `agent_runtime_concurrency_test.cljs`. Enable, unlock, storage-mode switch, protection-mode switch, and passkey lock now invalidate competing operation tokens. `reset-agent-state!` preserves the monotonic operation sequence, so tokens are not reused after resets. Stale passkey enable completion after lockbox creation also performs injected lockbox cleanup before suppressing the stale completion. Runtime unlock now uses no-cache lockbox unlock and commits the unlocked cache only after token validation, preserving newer passkey-enable cache state when older unlock work resolves late. Async protection-mode migrations now have their own operation token and cannot finalize after a newer storage/protection decision wins.

Follow-up validation passed with the requested CLJS compile and generated runner plus the required repository gates. This plan reduced overall complexity by replacing the large authority-heavy root runtime namespace with a small public facade and focused internal modules, while also making async credential side effects more explicit and token-gated. Browser QA remains skipped because no UI flow, browser harness, or visible interaction surface changed.

## Context and Orientation

The repository root for this work is `/Users/barry/.codex/worktrees/4401/hyperopen`. All paths in this plan are repository-relative unless stated otherwise.

The public runtime namespace is `src/hyperopen/wallet/agent_runtime.cljs`, which compiles to the ClojureScript namespace `hyperopen.wallet.agent-runtime`. A facade is a public module that keeps the stable API while delegating implementation to narrower internal modules. In this plan, the facade is the existing `agent_runtime.cljs` file.

The current public facade must still export these functions after the refactor:

- `exchange-response-error`
- `runtime-error-message`
- `set-agent-storage-mode!`
- `set-agent-local-protection-mode!`
- `approve-agent-request!`
- `enable-agent-trading!`
- `unlock-agent-trading!`
- `lock-agent-trading!`

The current runtime is dependency-injected. Dependency injection here means callers pass functions such as `persist-agent-session-by-mode!`, `clear-agent-session-by-mode!`, `create-locked-session!`, `unlock-locked-session!`, `approve-agent!`, `now-ms-fn`, and `default-signature-chain-id-for-environment` into runtime opts maps. The runtime code should call those injected functions, not directly import browser storage, IndexedDB, WebAuthn, crypto, signing, or exchange transport modules.

The adjacent owners are:

`src/hyperopen/wallet/agent_session.cljs` owns browser storage keys, raw agent session persistence, passkey metadata persistence, storage mode preferences, local protection mode preferences, device-label normalization, and metadata loading. Runtime modules may receive functions from this namespace through adapters, but should not take over its storage key or metadata authority.

`src/hyperopen/wallet/agent_lockbox.cljs` owns passkey lock support, credential creation, encrypted private-key lockbox persistence, and unlock through WebAuthn. Runtime modules may receive `create-locked-session!`, `unlock-locked-session!`, and `delete-locked-session!` functions, but must not directly own WebAuthn or encryption details.

`src/hyperopen/api/trading.cljs` owns exchange signing and trading API submission. It also owns signer/address reconciliation and missing-agent invalidation for trading submissions. This refactor must not move those concerns into wallet runtime modules.

Existing test surfaces that matter:

- `test/hyperopen/wallet/agent_runtime_test.cljs` is the main behavior suite for the runtime public facade.
- `test/hyperopen/wallet/agent_lockbox_test.cljs` and `test/hyperopen/wallet/agent_session_test.cljs` protect adjacent lockbox and metadata contracts.
- `test/hyperopen/api_wallets/effects_test.cljs` protects API wallet approve/remove consumers of `approve-agent-request!`.
- `test/hyperopen/runtime/action_adapters/wallet_test.cljs` protects wallet action adapter delegation into `agent-runtime`.
- `test/hyperopen/core_bootstrap/agent_trading_lifecycle_test.cljs` protects action/effect projections for enable and storage-mode behavior.
- `test/hyperopen/core_bootstrap/order_entry_actions_test.cljs` protects locked-agent submit behavior that dispatches unlock.
- `test/hyperopen/api/trading/approve_agent_test.cljs` and `test/hyperopen/api/trading/session_invalidation_test.cljs` protect adjacent API trading authority and must remain green.

## Non-Goals

This plan does not change the public wallet runtime API, storage key names, passkey metadata shape, lockbox encryption format, WebAuthn behavior, exchange signing behavior, UI copy, or trading settings UI.

This plan does not move `agent_session.cljs`, `agent_lockbox.cljs`, or `api/trading.cljs` responsibilities into the runtime split. It also does not add direct browser, storage, lockbox, crypto, or exchange requires to the extracted runtime modules except for pure/runtime helper modules in the `hyperopen.wallet.agent-runtime` internal package.

This plan is not performance-motivated. No optimization is proposed, and no baseline performance measurement is required. The change is justified by authority boundaries, testability, and namespace-size guardrails.

## Plan of Work

### Milestone 1: Characterization Tests And Race Gates

Start by adding focused tests before production refactoring. The test writer should extend `test/hyperopen/wallet/agent_runtime_test.cljs` unless a fixture split is needed to keep that file manageable. If a new helper test namespace is introduced, it must be registered by running `npm run test:runner:generate` and committed through the generated runner output. These tests must call the public facade functions first so they protect compatibility before internals move.

Add a race test for in-flight enable plus protection or storage mode switching. The test should use deferred Promises for `approve-agent!`, `create-locked-session!`, or persistence hooks so the test can start `enable-agent-trading!`, switch storage or local protection mode before the enable Promise resolves, then resolve the older enable. Acceptance is observable when the final store state reflects the latest requested storage and protection preferences, and no raw key is persisted when the latest local protection preference is `:passkey`. The assertions must inspect the store atom and the captured calls to `persist-agent-session-by-mode!`, `persist-passkey-session-metadata!`, `clear-agent-session-by-mode!`, and `cache-unlocked-session!`.

Add a duplicate enable approval ordering test. The test should start two enable operations for the same owner with different generated agent addresses and controllable approval completion order. Acceptance is observable when an older completion cannot overwrite the newer approved signer in `[:wallet :agent :agent-address]`, `:last-approved-at`, `:nonce-cursor`, or captured persisted session data.

Add a stale unlock failure test. The test should start an unlock that later rejects, advance the store to a newer ready session before the rejection arrives, and then reject the older unlock. Acceptance is observable when the stale failure does not revert the newer ready session to `:locked` and does not overwrite the newer session's signer or error state.

Add a post-approval persistence failure cleanup test. The test should approve successfully, force the post-approval persistence step to fail, and verify fail-closed cleanup semantics. Acceptance is observable when stale persisted sessions and stale unlocked cache entries for that owner are cleared or not created, the store ends in `:error`, and no raw private key remains in local/session persistence when the operation reports failure. If current behavior leaves stale data behind, the test should first document the unsafe behavior and then the worker must fix it.

Extend `approve-agent-request!` coverage for `:persist-session? true`, chain-id precedence, non-ok exchange response, and promise rejection. Acceptance is observable when `approve-agent-request!` persists exactly one session only after an `"ok"` exchange response, resolves signature chain id in this order: explicit `:signature-chain-id`, wallet `[:wallet :chain-id]`, then `default-signature-chain-id-for-environment`, rejects non-ok responses with `exchange-response-error`, and wraps promise rejections through `runtime-error-message` unless the error is already a known runtime error.

Protect passkey-local enable behavior. Acceptance is observable when a successful passkey-local enable checks `passkey-lock-supported?`, creates a lockbox through the injected `create-locked-session!`, persists passkey metadata through the injected `persist-passkey-session-metadata!`, caches only the unlocked in-memory session, and does not persist a raw key with `persist-agent-session-by-mode!`. Add companion coverage for unsupported passkey lock and passkey metadata persist cleanup, where unsupported lock rejects fail closed and metadata-persist failure deletes the lockbox through `delete-locked-session!`.

Add `lock-agent-trading!` coverage. Acceptance is observable when a ready remembered passkey session clears the unlocked in-memory session for the owner and projects `[:wallet :agent :status]` to `:locked` without clearing metadata fields needed for unlock. Also assert that non-passkey or missing-wallet cases do not lock unrelated state.

Keep existing API wallet approve/remove consumer tests in focused validation. Acceptance is observable when `test/hyperopen/api_wallets/effects_test.cljs` still passes, including authorize, remove-default-wallet, remove-named-wallet, `:persist-session? false`, refresh, and rejection scenarios.

Milestone 1 is complete when the new focused tests fail for any unsafe current behavior they expose or pass for behavior already safe, and the unchanged existing consumer tests still pass through the focused command described below.

### Milestone 2: Pure Helper And Facade Extraction

Create internal runtime namespaces under `src/hyperopen/wallet/agent_runtime/` without changing public callers. The expected files are:

- `src/hyperopen/wallet/agent_runtime/errors.cljs`
- `src/hyperopen/wallet/agent_runtime/state_projection.cljs`
- `src/hyperopen/wallet/agent_runtime/storage_mode.cljs`
- `src/hyperopen/wallet/agent_runtime/protection_mode.cljs`
- `src/hyperopen/wallet/agent_runtime/approval.cljs`
- `src/hyperopen/wallet/agent_runtime/enable.cljs`
- `src/hyperopen/wallet/agent_runtime/unlock.cljs`

Move `exchange-response-error`, `runtime-error-message`, known-error helpers, and generic runtime error wrapping into `errors.cljs`. Public `agent_runtime.cljs` must re-export `exchange-response-error` and `runtime-error-message` as functions with the same names and return behavior. Internal modules should use the `errors` namespace for shared known-error handling instead of duplicating message parsing.

Move store projection helpers into `state_projection.cljs`. This namespace should own small pure or store-mutating helpers such as agent error projection, default-state reset projection, ready-session projection, locked-session projection, and metadata-to-ready-session projection. It must not call browser storage, WebAuthn, exchange, or crypto functions.

Move `set-agent-storage-mode!` implementation into `storage_mode.cljs` and leave the public function in `agent_runtime.cljs` delegating to it. Acceptance is observable when existing storage-mode tests still call `agent-runtime/set-agent-storage-mode!` and pass unchanged.

Milestone 2 is complete when `agent_runtime.cljs` delegates through the new internal namespaces for errors, state projection, and storage mode, all public calls still compile, and the focused wallet runtime tests still pass.

### Milestone 3: Approval And Enable Extraction

Move `approve-agent-request!` implementation into `approval.cljs`. This module should own request nonce creation, agent name formatting call-through, signature chain-id resolution, exchange approval invocation, optional persistence after successful approval, and conversion of exchange or runtime failures into rejected Promises. It should continue receiving `build-approve-agent-action`, `format-agent-name-with-valid-until`, `approve-agent!`, `persist-agent-session-by-mode!`, `now-ms-fn`, `runtime-error-message`, and `exchange-response-error` through the opts map or delegated defaults.

Move `enable-agent-trading!` implementation and enable-specific persistence orchestration into `enable.cljs`. This module should own generated credential approval, post-approval persistence into plain or passkey-local mode, and ready/error store projection. It must call the extracted approval module rather than duplicating approval logic. It must keep lockbox and storage behavior injected: `passkey-lock-supported?`, `create-locked-session!`, `cache-unlocked-session!`, `persist-passkey-session-metadata!`, `delete-locked-session!`, and `persist-agent-session-by-mode!` remain opts.

While implementing the race gates from Milestone 1, introduce the smallest operation-generation guard needed to prevent stale async completions from overwriting newer state. The plan does not prescribe the exact implementation, but the guard must be deterministic and stored where the runtime already writes agent state. For example, the worker may assign an operation token in the store before each enable or unlock and check it before applying async success or failure projections. The chosen approach must be recorded in the Decision Log.

Milestone 3 is complete when public `agent-runtime/approve-agent-request!` and `agent-runtime/enable-agent-trading!` delegate to internal modules, the new approval and enable race tests pass, API wallet approve/remove consumer tests remain green, and public action adapter tests still see the same function names.

### Milestone 4: Storage, Protection, Unlock, And Lock Extraction

Move `set-agent-local-protection-mode!` implementation into `protection_mode.cljs`. This module should own only runtime migration orchestration between `:plain` and `:passkey` local protection modes, including live-session lookup through injected loaders, passkey lockbox creation/deletion through injected functions, metadata persistence through injected functions, raw-session clearing through injected functions, and store projection through `state_projection.cljs`.

Move unlock and lock behavior into `unlock.cljs`. This module should own `unlock-agent-trading!`, unlock precondition classification, stale completion protection, unlock success projection, unlock failure projection, and `lock-agent-trading!`. It should receive `load-passkey-session-metadata`, `unlock-locked-session!`, `clear-unlocked-session!`, normalization functions, and error formatting functions through opts. It should not require `agent_lockbox.cljs` directly.

Keep the root facade in `agent_runtime.cljs` as the only public compatibility namespace. It should require the focused internal modules and delegate public functions to them. It should not grow new business logic while extraction proceeds.

Milestone 4 is complete when public protection, unlock, and lock tests still call `hyperopen.wallet.agent-runtime` and pass, stale unlock failure tests pass, and the root facade is clearly smaller than before.

### Milestone 5: Namespace-Size And Documentation Cleanup

Run `wc -l src/hyperopen/wallet/agent_runtime.cljs` and confirm the root facade is below 500 lines. Then update `dev/namespace_size_exceptions.edn`: remove the `src/hyperopen/wallet/agent_runtime.cljs` exception if the file is below the default 500-line threshold. If the file remains above 500 for a justified reason, update the exception with the new smaller `:max-lines`, a fresh reason tied to this ticket, and a concrete retire-by date. The expected outcome is removal, not renewal.

Check whether new internal namespaces or expanded tests exceed size thresholds by running `npm run lint:namespace-sizes` and `npm run lint:namespace-sizes:test`. Acceptance is observable when both commands pass without stale or missing size exception errors. If `test/hyperopen/wallet/agent_runtime_test.cljs` grows past its current budget, prefer splitting focused helper tests over increasing the exception.

Update this ExecPlan living sections with final file sizes, decisions, and any unsafe behavior fixed by tests. Do not create a second markdown requirements file. If the manager path needs machine-readable data, keep it under `tmp/multi-agent/hyperopen-g0cv/`.

Milestone 5 is complete when namespace-size guardrails pass and this plan accurately reflects the final implementation shape.

### Milestone 6: Final Validation

Run focused validation first, then the required repository gates. The mandatory gates after code changes are `npm run check`, `npm test`, and `npm run test:websocket`.

Because this is not UI-facing, Browser MCP and governed browser QA are skipped by default. If implementation changes Playwright-covered passkey live behavior, modifies `tools/playwright/**`, or changes visible trading settings or passkey interaction behavior, also run `npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "passkey|session toggles|locked remembered passkey session submit triggers unlock flow instead of recovery"` and then `npm run browser:cleanup`.

Milestone 6 is complete when all required commands pass, conditional browser accounting is explicitly marked as skipped or passed, and `bd` was the local tracker used at the time for any remaining follow-up.

## Concrete Steps

From `/Users/barry/.codex/worktrees/4401/hyperopen`, use this order.

1. Reconfirm the baseline before editing production code:

       bd show hyperopen-g0cv --json
       wc -l src/hyperopen/wallet/agent_runtime.cljs dev/namespace_size_exceptions.edn
       rg -n "src/hyperopen/wallet/agent_runtime.cljs" dev/namespace_size_exceptions.edn

   Expected baseline: `src/hyperopen/wallet/agent_runtime.cljs` is about 869 lines and has a namespace-size exception with `:max-lines 900`.

2. Add characterization tests first. Expected touched test areas are:

       test/hyperopen/wallet/agent_runtime_test.cljs
       test/hyperopen/api_wallets/effects_test.cljs
       test/hyperopen/runtime/action_adapters/wallet_test.cljs
       test/hyperopen/core_bootstrap/agent_trading_lifecycle_test.cljs
       test/hyperopen/core_bootstrap/order_entry_actions_test.cljs
       test/hyperopen/api/trading/approve_agent_test.cljs
       test/hyperopen/api/trading/session_invalidation_test.cljs

   If a new wallet runtime helper test namespace is added, run:

       npm run test:runner:generate

3. Run the supported test commands after characterization changes and after each extraction slice:

       npm test

   When diagnosing compile failures separately from assertion failures, compile the generated test build directly:

       npm run test:runner:generate
       npx shadow-cljs --force-spawn compile test

   Success is observable when the generated test build compiles and `npm test` reports passing tests with no unexpected async timeout.

4. Extract helpers and facade delegation in small slices:

       src/hyperopen/wallet/agent_runtime/errors.cljs
       src/hyperopen/wallet/agent_runtime/state_projection.cljs
       src/hyperopen/wallet/agent_runtime/storage_mode.cljs
       src/hyperopen/wallet/agent_runtime/protection_mode.cljs
       src/hyperopen/wallet/agent_runtime/approval.cljs
       src/hyperopen/wallet/agent_runtime/enable.cljs
       src/hyperopen/wallet/agent_runtime/unlock.cljs
       src/hyperopen/wallet/agent_runtime.cljs

   After each slice, rerun the focused test command or `npm test`.

5. Clean up size exceptions:

       wc -l src/hyperopen/wallet/agent_runtime.cljs
       npm run lint:namespace-sizes
       npm run lint:namespace-sizes:test

   Success is observable when `src/hyperopen/wallet/agent_runtime.cljs` is below 500 lines and `dev/namespace_size_exceptions.edn` no longer contains a stale exception for that file.

6. Run final required validation:

       npm run check
       npm test
       npm run test:websocket

7. Conditional browser validation only if implementation touches Playwright-covered live passkey behavior:

       npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "passkey|session toggles|locked remembered passkey session submit triggers unlock flow instead of recovery"
       npm run browser:cleanup

   If no UI, Playwright, or visible passkey flow behavior changed, record browser QA as skipped in this plan and final handoff.

## Validation And Acceptance

Acceptance criterion 1: public API compatibility is preserved. Observable proof is that `test/hyperopen/runtime/action_adapters/wallet_test.cljs` still passes, `test/hyperopen/core_bootstrap/agent_trading_lifecycle_test.cljs` still passes, and public callers can still resolve `hyperopen.wallet.agent-runtime/exchange-response-error`, `runtime-error-message`, `set-agent-storage-mode!`, `set-agent-local-protection-mode!`, `approve-agent-request!`, `enable-agent-trading!`, `unlock-agent-trading!`, and `lock-agent-trading!` after `npm test`.

Acceptance criterion 2: the root facade no longer owns all runtime responsibilities. Observable proof is that the files `src/hyperopen/wallet/agent_runtime/errors.cljs`, `state_projection.cljs`, `storage_mode.cljs`, `protection_mode.cljs`, `approval.cljs`, `enable.cljs`, and `unlock.cljs` exist; `src/hyperopen/wallet/agent_runtime.cljs` delegates public functions to them; and `wc -l src/hyperopen/wallet/agent_runtime.cljs` reports fewer than 500 lines.

Acceptance criterion 3: dependency-injection boundaries are preserved. Observable proof is code inspection plus `rg` checks showing the new internal runtime modules do not directly require `hyperopen.wallet.agent-session`, `hyperopen.wallet.agent-lockbox`, `hyperopen.api.trading`, browser platform modules, WebAuthn modules, IndexedDB modules, crypto modules, or signing modules. They may require other pure/runtime helper modules under `hyperopen.wallet.agent-runtime`.

Acceptance criterion 4: adjacent authority owners remain stable. Observable proof is that `test/hyperopen/wallet/agent_session_test.cljs`, `test/hyperopen/wallet/agent_lockbox_test.cljs`, `test/hyperopen/api/trading/approve_agent_test.cljs`, and `test/hyperopen/api/trading/session_invalidation_test.cljs` pass without moving storage key ownership, lockbox ownership, signing ownership, signer/address reconciliation, or missing-agent invalidation into runtime modules.

Acceptance criterion 5: in-flight enable plus mode-switch race is safe. Observable proof is a test in `test/hyperopen/wallet/agent_runtime_test.cljs` that starts enable, switches storage or protection mode while the enable Promise is unresolved, resolves the old enable, and observes that final store state reflects the latest mode preference and no raw key persistence remains under passkey preference.

Acceptance criterion 6: duplicate enable approvals cannot stale-overwrite a newer signer. Observable proof is a deterministic test that completes an older enable after a newer enable and observes the newer `:agent-address`, `:last-approved-at`, `:nonce-cursor`, and persisted session still win.

Acceptance criterion 7: stale unlock failure cannot revert a newer ready session to locked. Observable proof is a deterministic test that rejects an older unlock after the store has advanced to a new ready session and observes `[:wallet :agent :status]` remains `:ready` with the newer signer fields intact.

Acceptance criterion 8: post-approval persistence failure fails closed. Observable proof is a deterministic test where approval succeeds and persistence fails; the store ends in `:error`, stale persisted sessions and unlocked cache entries are cleared or not created, and captured persistence calls prove no raw key remains when the operation reports failure.

Acceptance criterion 9: `approve-agent-request!` edge behavior is protected. Observable proof is passing tests for `:persist-session? true`, chain-id precedence, non-ok exchange response, and promise rejection wrapping in `test/hyperopen/wallet/agent_runtime_test.cljs`, plus unchanged API wallet authorize/remove tests in `test/hyperopen/api_wallets/effects_test.cljs`.

Acceptance criterion 10: passkey-local enable and lock behavior are protected. Observable proof is passing tests for passkey-local enable success, unsupported passkey lock, passkey metadata persist cleanup, and `lock-agent-trading!` in `test/hyperopen/wallet/agent_runtime_test.cljs`.

Acceptance criterion 11: namespace-size cleanup is complete. Observable proof is `npm run lint:namespace-sizes` and `npm run lint:namespace-sizes:test` passing, with the stale source exception for `src/hyperopen/wallet/agent_runtime.cljs` removed from `dev/namespace_size_exceptions.edn` unless a documented final line count above 500 proves an updated exception is still necessary.

Acceptance criterion 12: required repo gates pass. Observable proof is successful completion of `npm run check`, `npm test`, and `npm run test:websocket` from `/Users/barry/.codex/worktrees/4401/hyperopen`. Conditional Playwright proof is required only if implementation touches Playwright-covered passkey live behavior.

## Idempotence And Recovery

The refactor should be done in small, reversible slices. Tests should be added before moving implementation. If a slice fails, keep the failing test and revert or adjust only the most recent implementation slice, not unrelated user changes.

The new internal namespaces are additive at first. The root facade should delegate one responsibility at a time. This makes it safe to rerun `npm test`, `npm run check`, and namespace-size checks repeatedly.

If async race fixes require adding operation tokens or sequence fields to agent state, keep them internal and deterministic. Do not expose them through public APIs unless a consumer already observes the exact field. If a temporary field is added only to prevent stale async writes, document it in the Decision Log and ensure tests assert behavior rather than the private token value.

Do not run `git pull --rebase`, `git push`, or destructive git commands for this plan unless the user explicitly requests remote sync in the current session.

## Interfaces And Dependencies

At the end of the refactor, `src/hyperopen/wallet/agent_runtime.cljs` should look like a compatibility facade. It should require internal modules and define the public functions by delegating to them. The public function arities should remain compatible with current callers.

`src/hyperopen/wallet/agent_runtime/errors.cljs` should provide the internal implementation for `exchange-response-error`, `runtime-error-message`, known runtime errors, and runtime error wrapping.

`src/hyperopen/wallet/agent_runtime/state_projection.cljs` should provide store projection helpers for agent error, default reset, ready session, locked session, and metadata-derived session state. It can mutate the passed store atom, but it must not perform browser or network side effects.

`src/hyperopen/wallet/agent_runtime/storage_mode.cljs` should provide the implementation behind `set-agent-storage-mode!`.

`src/hyperopen/wallet/agent_runtime/protection_mode.cljs` should provide the implementation behind `set-agent-local-protection-mode!` and migration helpers for plain and passkey local protection.

`src/hyperopen/wallet/agent_runtime/approval.cljs` should provide the implementation behind `approve-agent-request!`.

`src/hyperopen/wallet/agent_runtime/enable.cljs` should provide the implementation behind `enable-agent-trading!` and post-approval persistence orchestration.

`src/hyperopen/wallet/agent_runtime/unlock.cljs` should provide the implementations behind `unlock-agent-trading!` and `lock-agent-trading!`.

No extracted runtime namespace should directly require `hyperopen.wallet.agent-session`, `hyperopen.wallet.agent-lockbox`, `hyperopen.api.trading`, `hyperopen.platform.webauthn`, `hyperopen.platform.indexed-db`, `hyperopen.trading-crypto-modules`, or signing utilities. Those functions must remain injected through adapter opts.

## Artifacts And Notes

Baseline observations from the `spec_writer` phase:

       bd show hyperopen-g0cv --json
       # status: in_progress
       # title: Refactor wallet agent runtime authority boundaries

       wc -l src/hyperopen/wallet/agent_runtime.cljs dev/namespace_size_exceptions.edn
       # 869 src/hyperopen/wallet/agent_runtime.cljs
       #  73 dev/namespace_size_exceptions.edn

       rg -n "src/hyperopen/wallet/agent_runtime.cljs" dev/namespace_size_exceptions.edn
       # entry exists with :max-lines 900 and retire-by 2026-06-30

The relevant existing public function definitions in `src/hyperopen/wallet/agent_runtime.cljs` are currently at:

- `exchange-response-error`
- `runtime-error-message`
- `set-agent-storage-mode!`
- `set-agent-local-protection-mode!`
- `approve-agent-request!`
- `enable-agent-trading!`
- `unlock-agent-trading!`
- `lock-agent-trading!`

Plan revision note: created on 2026-04-18 22:31Z for `hyperopen-g0cv` after reading the required planning and multi-agent contracts, confirming the active `bd` issue, measuring the 869-line root runtime namespace, and inspecting the existing wallet runtime, API wallet consumer, runtime adapter, core bootstrap, and API trading test surfaces. This plan records the requested stable facade, internal namespace split, dependency-injection boundary preservation, race-driven test coverage, namespace-size acceptance, browser-QA skip semantics, and final validation gates.

Plan revision note: updated on 2026-04-18 22:45Z after parent review found that the generated CLJS test runner does not support the previously listed `node out/test.js --test=...` filter. The plan now uses only supported compile and `npm test` commands for committed validation.

Plan revision note: updated on 2026-04-18 22:55Z after merging acceptance and edge-case test proposals into `tmp/multi-agent/hyperopen-g0cv/approved-test-contract.json`. The approved contract intentionally selects a bounded RED scope and records deferred proposal items so later workers do not mistake them for untracked backlog.

Plan revision note: updated on 2026-04-18 23:23Z after RED materialization and local verification. The plan now records the exact failing command and failure set that the implementation must turn green without weakening the approved tests.

Plan revision note: updated on 2026-04-18 follow-up after parent review found two production wiring gaps not fully covered by the approved RED tests. The plan now records the storage-mode enable-token invalidation, real action-adapter cleanup callback injection, and remaining parent closeout item required while the `bd` issue stays active.
