# Split Core Bootstrap Tests into Domain-Focused Namespaces

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

`/hyperopen/test/hyperopen/core_bootstrap_test.cljs` currently mixes many bounded contexts in one 2558-line namespace. This increases maintenance cost for humans and coding agents because unrelated test setup and behaviors must be loaded together to make small changes. After this change, the same assertions will be preserved in smaller domain-focused files under `/hyperopen/test/hyperopen/core_bootstrap/`, with shared test helpers in dedicated support namespaces.

A contributor should be able to open one small file for one concern (for example wallet copy feedback or order-entry transitions), add a test, and run the same test gates with behavior parity.

## Progress

- [x] (2026-02-20 13:40Z) Re-read `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and active ExecPlan examples to align format and lifecycle requirements.
- [x] (2026-02-20 13:40Z) Audited `/hyperopen/test/hyperopen/core_bootstrap_test.cljs` structure, helper functions, and all `deftest` boundaries.
- [x] (2026-02-20 13:40Z) Confirmed `/hyperopen/test/test_runner.cljs` uses explicit namespace wiring, so split work must update both `:require` and `run-tests` lists.
- [x] (2026-02-20 13:40Z) Authored this active ExecPlan before implementation.
- [x] (2026-02-20 13:42Z) Extracted shared test support helpers into `/hyperopen/test/hyperopen/core_bootstrap/test_support/` (`fixtures.cljs`, `browser_mocks.cljs`, and `effect_extractors.cljs`).
- [x] (2026-02-20 13:43Z) Split the monolith into 10 domain-focused namespaces under `/hyperopen/test/hyperopen/core_bootstrap/` and removed `/hyperopen/test/hyperopen/core_bootstrap_test.cljs`.
- [x] (2026-02-20 13:43Z) Updated `/hyperopen/test/test_runner.cljs` to replace `hyperopen.core-bootstrap-test` with the new split namespace list in both `:require` and `run-tests`.
- [x] (2026-02-20 13:45Z) Resolved extraction-related compile regressions (`Unexpected EOF`) by restoring truncated test bodies in `asset_cache_persistence_test.cljs` and `chart_menu_and_storage_test.cljs`.
- [x] (2026-02-20 13:47Z) Added isolation fixture in `/hyperopen/test/hyperopen/wallet/agent_session_test.cljs` to reset by-mode agent-session wrappers before/after each test; this prevents leaked async stubs from unrelated suites.
- [x] (2026-02-20 13:48Z) Ran required gates successfully on final state: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-02-20 13:48Z) Updated this ExecPlan with final outcomes, discoveries, and decisions.

## Surprises & Discoveries

- Observation: the monolith contains at least ten distinct concerns (startup/bootstrap, account history controls, websocket diagnostics, wallet effects, chart controls, order entry, agent trading, and cache projections) rather than one cohesive module.
  Evidence: `rg -n "^\(deftest" /hyperopen/test/hyperopen/core_bootstrap_test.cljs` shows broad prefix families (`select-account-info`, `websocket-diagnostics`, `select-order-entry`, `enable-agent-trading`, and others) clustered in one namespace.

- Observation: many tests in the monolith duplicate domain ownership that already exists in source paths (`startup`, `wallet`, `websocket`, `asset_selector`), indicating test topology drift rather than source-model drift.
  Evidence: production and test trees already contain focused directories such as `/hyperopen/src/hyperopen/startup/`, `/hyperopen/src/hyperopen/wallet/`, and `/hyperopen/test/hyperopen/websocket/`.

- Observation: line-range extraction from the monolith introduced silent truncation for trailing tests when range end was set at the `deftest` declaration line rather than its closing form.
  Evidence: `npm run check` failed with `Unexpected EOF` in `/hyperopen/test/hyperopen/core_bootstrap/asset_cache_persistence_test.cljs:170` and `/hyperopen/test/hyperopen/core_bootstrap/chart_menu_and_storage_test.cljs:116`.

- Observation: `wallet/agent_session_test.cljs` can inherit leaked by-mode stubs from earlier async suites unless wrappers are force-reset per test.
  Evidence: first `npm test` run after split produced 3 deterministic failures in `by-mode-storage-wrappers-target-local-and-session-storage-test`; adding a per-test fixture restoring `load/persist/clear-agent-session-by-mode` eliminated failures.

## Decision Log

- Decision: perform this as a behavior-preserving refactor, moving tests and helpers without intentionally changing assertions or production behavior.
  Rationale: this keeps risk bounded and makes any failures attributable to wiring/migration, not feature changes.
  Date/Author: 2026-02-20 / Codex

- Decision: split by bounded context under a dedicated `/hyperopen/test/hyperopen/core_bootstrap/` folder instead of scattering into many existing domain folders in one pass.
  Rationale: this preserves migration traceability from the monolith while still reducing context size and making future gradual redistribution easy.
  Date/Author: 2026-02-20 / Codex

- Decision: extract shared helpers (local storage mock, navigator mock, effect extraction, runtime bootstrap fixtures, timeout cleanup) into test-support namespaces.
  Rationale: this avoids helper drift and gives coding agents a single place to understand shared test scaffolding.
  Date/Author: 2026-02-20 / Codex

- Decision: keep split namespaces under `/hyperopen/test/hyperopen/core_bootstrap/` with explicit domain names rather than redistributing into many existing folders in the same change.
  Rationale: this preserves one-step traceability from the deleted monolith while still reducing context size and making future migration waves incremental and low risk.
  Date/Author: 2026-02-20 / Codex

- Decision: add a defensive fixture to `/hyperopen/test/hyperopen/wallet/agent_session_test.cljs` to restore by-mode wrappers each test.
  Rationale: this ensures deterministic isolation when async tests in other namespaces temporarily `set!` the same vars; it fixed observed post-split leakage without changing production code.
  Date/Author: 2026-02-20 / Codex

## Outcomes & Retrospective

Implementation completed as a behavior-preserving test-topology refactor.

- Replaced `/hyperopen/test/hyperopen/core_bootstrap_test.cljs` (2558 lines) with 10 domain-focused files plus 3 support files under `/hyperopen/test/hyperopen/core_bootstrap/`.
- Preserved all monolith test names while relocating by bounded context, keeping failure/search ergonomics stable.
- Updated explicit test-runner wiring in `/hyperopen/test/test_runner.cljs` to include all new namespaces.
- Added test-isolation hardening to `/hyperopen/test/hyperopen/wallet/agent_session_test.cljs` for by-mode agent-session wrapper resets.

Validation evidence:

- `npm run check`: pass.
- `npm test`: pass (`Ran 1168 tests containing 5445 assertions. 0 failures, 0 errors.`).
- `npm run test:websocket`: pass (`Ran 135 tests containing 587 assertions. 0 failures, 0 errors.`).

Residual risk is low. The main migration risk was async stub leakage across namespaces; the added wallet fixture addresses that explicitly.

## Context and Orientation

The monolithic file `/hyperopen/test/hyperopen/core_bootstrap_test.cljs` included global fixtures (`use-fixtures :once` and `:each`), private helper utilities, and 115 tests spanning unrelated domains. Because it was a single namespace, adding or updating a single behavior required loading large unrelated context.

The test runner `/hyperopen/test/test_runner.cljs` does not auto-discover tests. It manually requires each namespace and calls `run-tests` with explicit symbols. Any namespace additions/removals must be reflected there or tests are silently skipped.

This plan introduces a focused topology:

- `/hyperopen/test/hyperopen/core_bootstrap/runtime_startup_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap/asset_selector_actions_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap/account_history_actions_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap/asset_cache_persistence_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap/websocket_diagnostics_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap/wallet_actions_effects_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap/order_effects_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap/chart_menu_and_storage_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap/order_entry_actions_test.cljs`
- `/hyperopen/test/hyperopen/core_bootstrap/agent_trading_lifecycle_test.cljs`
- support helpers under `/hyperopen/test/hyperopen/core_bootstrap/test_support/`

For this plan, “domain-focused” means one test namespace covers one primary responsibility family, so contributors can locate relevant tests with minimal unrelated setup.

## Plan of Work

Milestone 1 creates support scaffolding. Extract shared helper functions from the monolith into support namespaces under `/hyperopen/test/hyperopen/core_bootstrap/test_support/`, preserving existing helper semantics.

Milestone 2 creates new domain-focused files and moves tests with minimal text changes. Keep test names stable to preserve searchable history and diagnostics continuity.

Milestone 3 removes or retires the old monolith namespace once all tests have a new home, ensuring no duplicate test execution.

Milestone 4 updates `/hyperopen/test/test_runner.cljs` requires and `run-tests` entries to reference the new split namespaces and remove the old monolith reference.

Milestone 5 runs required repository gates and records outcomes in this plan.

## Concrete Steps

1. Create support namespaces under `/hyperopen/test/hyperopen/core_bootstrap/test_support/`.
2. Create each domain-focused namespace file listed above and move matching `deftest` forms from `/hyperopen/test/hyperopen/core_bootstrap_test.cljs`.
3. Keep each file’s `:require` set minimal and explicit.
4. Remove `/hyperopen/test/hyperopen/core_bootstrap_test.cljs` after confirming all tests were migrated.
5. Update `/hyperopen/test/test_runner.cljs` with new namespaces in both `:require` and `run-tests`.
6. Run from `/hyperopen`:

   npm run check
   npm test
   npm run test:websocket

7. Update this ExecPlan with final progress entries, discoveries, decision log additions, and retrospective evidence.

## Validation and Acceptance

This split is accepted when:

1. The monolith file is replaced by smaller domain-focused files under `/hyperopen/test/hyperopen/core_bootstrap/`.
2. Shared helper logic lives in dedicated support namespaces rather than being redefined across files.
3. `/hyperopen/test/test_runner.cljs` includes every new namespace and no stale `hyperopen.core-bootstrap-test` references remain.
4. `npm run check`, `npm test`, and `npm run test:websocket` pass with zero failures and zero errors.
5. No intentional production behavior changes were introduced.

## Idempotence and Recovery

The migration is source-only and can be repeated safely. If a partial split fails, re-add the affected namespace to `/hyperopen/test/test_runner.cljs` and continue moving tests incrementally. Recovery is done through normal file edits and rerunning required gates; no destructive git operations are needed.

## Artifacts and Notes

Initial scoping evidence:

- `/hyperopen/test/hyperopen/core_bootstrap_test.cljs`: 2558 lines.
- Shared local helper defs in monolith: 7 private functions.
- Approximate test families identified: startup/runtime, asset selector icon/render/selection, account history controls, cache persistence, websocket diagnostics/health, wallet effects, order effects, chart controls, order-entry actions, agent trading lifecycle.

Implementation evidence:

- New split namespaces:
  - `/hyperopen/test/hyperopen/core_bootstrap/runtime_startup_test.cljs` (8 tests)
  - `/hyperopen/test/hyperopen/core_bootstrap/asset_selector_actions_test.cljs` (14 tests)
  - `/hyperopen/test/hyperopen/core_bootstrap/account_history_actions_test.cljs` (24 tests)
  - `/hyperopen/test/hyperopen/core_bootstrap/asset_cache_persistence_test.cljs` (7 tests)
  - `/hyperopen/test/hyperopen/core_bootstrap/websocket_diagnostics_test.cljs` (10 tests)
  - `/hyperopen/test/hyperopen/core_bootstrap/wallet_actions_effects_test.cljs` (7 tests)
  - `/hyperopen/test/hyperopen/core_bootstrap/order_effects_test.cljs` (3 tests)
  - `/hyperopen/test/hyperopen/core_bootstrap/chart_menu_and_storage_test.cljs` (11 tests)
  - `/hyperopen/test/hyperopen/core_bootstrap/order_entry_actions_test.cljs` (22 tests)
  - `/hyperopen/test/hyperopen/core_bootstrap/agent_trading_lifecycle_test.cljs` (9 tests)
- New support namespaces:
  - `/hyperopen/test/hyperopen/core_bootstrap/test_support/fixtures.cljs`
  - `/hyperopen/test/hyperopen/core_bootstrap/test_support/browser_mocks.cljs`
  - `/hyperopen/test/hyperopen/core_bootstrap/test_support/effect_extractors.cljs`
- Runner update:
  - `/hyperopen/test/test_runner.cljs` now references the split namespaces and no longer references `hyperopen.core-bootstrap-test`.
- Additional isolation hardening:
  - `/hyperopen/test/hyperopen/wallet/agent_session_test.cljs` now uses a per-test fixture to restore by-mode wrapper vars.

## Interfaces and Dependencies

No production interface is intentionally changed. This plan changes test namespace topology and test runner wiring only.

Expected new namespace symbols:

- `hyperopen.core-bootstrap.runtime-startup-test`
- `hyperopen.core-bootstrap.asset-selector-actions-test`
- `hyperopen.core-bootstrap.account-history-actions-test`
- `hyperopen.core-bootstrap.asset-cache-persistence-test`
- `hyperopen.core-bootstrap.websocket-diagnostics-test`
- `hyperopen.core-bootstrap.wallet-actions-effects-test`
- `hyperopen.core-bootstrap.order-effects-test`
- `hyperopen.core-bootstrap.chart-menu-and-storage-test`
- `hyperopen.core-bootstrap.order-entry-actions-test`
- `hyperopen.core-bootstrap.agent-trading-lifecycle-test`

Support namespaces:

- `hyperopen.core-bootstrap.test-support.fixtures`
- `hyperopen.core-bootstrap.test-support.browser-mocks`
- `hyperopen.core-bootstrap.test-support.effect-extractors`

Dependencies remain `cljs.test`, `hyperopen.core.compat`, and existing runtime/domain modules already used by the monolith.

Revision Note (2026-02-20 13:48Z): Updated this ExecPlan from draft state to completed implementation state, including migration evidence, validation outcomes, and a discovered cross-namespace async stub-isolation fix.
