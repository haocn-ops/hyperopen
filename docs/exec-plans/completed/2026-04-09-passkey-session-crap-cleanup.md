# Reduce CRAP Hotspots In Passkey Trading Session Modules

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is governed by `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The tracked work item for this plan is `hyperopen-kmsr`; the `bd` issue is historical local tracker context from the active phase.

## Purpose / Big Picture

After this cleanup, the passkey-backed trading session code should be materially easier to reason about and safer to modify without breaking unlock, recovery, or persistence behavior. The user-visible goal is not a feature change. The goal is that future work on remembered sessions, passkey unlock, and trading recovery can land with less risk because the highest-CRAP code paths are better covered by tests and split into smaller, easier-to-verify functions.

The way to see this working after implementation is to run the passkey-session test matrix and browser regression flows and confirm that behavior is unchanged while the hotspot modules report lower CRAP scores and the new helper seams are easier to test in isolation. The cleanup is successful only if the browser flow still works and the code becomes simpler to change.

## Progress

- [x] (2026-04-09 12:43Z) Generated fresh repository coverage with `npm run coverage` so CRAP analysis could use current `coverage/lcov.info` instead of stale or missing data.
- [x] (2026-04-09 12:48Z) Ran module-level CRAP reports across the passkey-session implementation surfaces: `/hyperopen/src/hyperopen/wallet/agent_session.cljs`, `/hyperopen/src/hyperopen/wallet/agent_lockbox.cljs`, `/hyperopen/src/hyperopen/wallet/agent_runtime.cljs`, `/hyperopen/src/hyperopen/wallet/core.cljs`, `/hyperopen/src/hyperopen/api/trading.cljs`, `/hyperopen/src/hyperopen/platform/webauthn.cljs`, `/hyperopen/src/hyperopen/platform/indexed_db.cljs`, `/hyperopen/src/hyperopen/views/header/settings.cljs`, `/hyperopen/src/hyperopen/views/header/vm.cljs`, `/hyperopen/src/hyperopen/header/actions.cljs`, `/hyperopen/src/hyperopen/order/actions.cljs`, `/hyperopen/src/hyperopen/order/effects.cljs`, and `/hyperopen/src/hyperopen/startup/restore.cljs`.
- [x] (2026-04-09 12:49Z) Filed and claimed `hyperopen-kmsr` in `bd` for the cleanup planning work.
- [x] (2026-04-09 12:57Z) Collected two read-only sidecar analyses: one focused on refactor seams and one focused on coverage-first ROI.
- [x] (2026-04-09 13:03Z) Authored this active ExecPlan with ROI ordering, non-goals, exact target files, and validation commands.
- [x] (2026-04-09 15:40Z) Implemented Milestone 1 by expanding targeted coverage in `/hyperopen/test/hyperopen/platform/webauthn_test.cljs`, `/hyperopen/test/hyperopen/wallet/agent_runtime_test.cljs`, `/hyperopen/test/hyperopen/wallet/agent_session_test.cljs`, and new `/hyperopen/test/hyperopen/wallet/agent_lockbox_test.cljs`, then regenerated `/hyperopen/test/test_runner_generated.cljs`.
- [x] (2026-04-09 16:25Z) Implemented Milestone 2 by splitting passkey metadata sanitation in `/hyperopen/src/hyperopen/wallet/agent_session.cljs` into field-level helpers while preserving the storage contract and current normalization behavior.
- [x] (2026-04-09 17:20Z) Implemented Milestone 3 and Milestone 4 by decomposing the runtime, WebAuthn, and lockbox hotspots in `/hyperopen/src/hyperopen/wallet/agent_runtime.cljs`, `/hyperopen/src/hyperopen/platform/webauthn.cljs`, and `/hyperopen/src/hyperopen/wallet/agent_lockbox.cljs` into smaller validation, request-building, transition, rollback, and state-projection helpers without changing the public entry points.
- [x] (2026-04-09 18:05Z) Re-ran `npm run coverage`, refreshed the module CRAP reports, updated namespace size exceptions for the helper-heavy cleanup shape, and passed the required validation commands: `npm test`, `npm run test:websocket`, and `npm run check`.

## Surprises & Discoveries

- Observation: the worst CRAP scores are concentrated in a small number of functions rather than spread evenly across all passkey-session files.
  Evidence: `bb tools/crap_report.clj --module /hyperopen/src/hyperopen/wallet/agent_runtime.cljs --format json` reported only two `crappy` functions in that file, led by `set-agent-local-protection-mode!` at `CRAP 57.28`, while most other functions in the module scored well.

- Observation: the browser-bound WebAuthn layer is a worse hotspot than some larger business-logic modules because its critical branches are under-tested.
  Evidence: `bb tools/crap_report.clj --module /hyperopen/src/hyperopen/platform/webauthn.cljs --format json` reported module `crapload 80.53`, with `create-passkey-credential!` at `CRAP 110` and `0` coverage and `passkey-capable?` at `CRAP 30.53` with only `0.12` coverage.

- Observation: `sanitize-passkey-session-metadata` is the cheapest large win because it is pure, isolated, and badly under-covered.
  Evidence: `bb tools/crap_report.clj --module /hyperopen/src/hyperopen/wallet/agent_session.cljs --format json` reported `sanitize-passkey-session-metadata` at `CRAP 109.38`, complexity `11`, and coverage `0.0667`.

- Observation: `agent_lockbox.cljs` is structurally hard to follow because the top-level functions combine validation, browser APIs, encryption, persistence, and final session assembly in one Promise chain.
  Evidence: direct inspection of `/hyperopen/src/hyperopen/wallet/agent_lockbox.cljs` showed `create-locked-session!` and `unlock-locked-session!` nesting credential creation, PRF evaluation, encryption/decryption, IndexedDB access, and return-shape construction in single functions. The CRAP report marks both as `crappy`.

- Observation: some adjacent files touched during the passkey implementation are not meaningful cleanup priorities for this CRAP effort.
  Evidence: `/hyperopen/src/hyperopen/api/trading.cljs`, `/hyperopen/src/hyperopen/order/actions.cljs`, `/hyperopen/src/hyperopen/order/effects.cljs`, `/hyperopen/src/hyperopen/views/header/settings.cljs`, `/hyperopen/src/hyperopen/views/header/vm.cljs`, and `/hyperopen/src/hyperopen/startup/restore.cljs` all came back below the CRAP threshold in the audit.

- Observation: the targeted coverage additions mattered more after coverage regeneration than the earlier stale CRAP file suggested.
  Evidence: once `npm run coverage` regenerated `coverage/lcov.info`, the audited modules all dropped below the threshold after the helper extraction pass, with module `crapload` falling to `0.0` for `/hyperopen/src/hyperopen/platform/webauthn.cljs`, `/hyperopen/src/hyperopen/wallet/agent_session.cljs`, `/hyperopen/src/hyperopen/wallet/agent_lockbox.cljs`, and `/hyperopen/src/hyperopen/wallet/agent_runtime.cljs`.

- Observation: the cleanup reduced CRAP successfully but temporarily increased namespace size enough to require explicit budget updates.
  Evidence: `npm run check` first failed on `dev.check-namespace-sizes` for `/hyperopen/src/hyperopen/wallet/agent_session.cljs`, `/hyperopen/src/hyperopen/wallet/agent_runtime.cljs`, and `/hyperopen/test/hyperopen/wallet/agent_runtime_test.cljs`, which was resolved by updating `/hyperopen/dev/namespace_size_exceptions.edn`.

## Decision Log

- Decision: order the cleanup by return on investment, not by raw module CRAP score alone.
  Rationale: the highest raw scores come from a mix of under-tested browser wrappers and genuinely overgrown orchestration functions. The fastest safe progress comes from test-first work on the browser and unlock seams, then pure helper extraction, then state-transition refactors.
  Date/Author: 2026-04-09 / Codex

- Decision: treat test coverage additions as part of the cleanup, not as optional follow-up.
  Rationale: the current CRAP scores are inflated by missing branch coverage in security-sensitive paths. Refactoring those paths first would move complexity around without reducing risk.
  Date/Author: 2026-04-09 / Codex

- Decision: keep public runtime entry points, storage keys, and return shapes stable during this cleanup.
  Rationale: the goal is maintainability and verification, not a behavior change. Existing runtime callers should keep using `set-agent-local-protection-mode!`, `unlock-agent-trading!`, `create-locked-session!`, `unlock-locked-session!`, and the current session metadata shape.
  Date/Author: 2026-04-09 / Codex

- Decision: defer low-priority adjacent surfaces unless a refactor slice proves they are blocking.
  Rationale: the CRAP audit did not justify expanding scope into header UI, order submit, startup restore, or general trading API code. Pulling those files into the cleanup would dilute the ROI ordering and increase merge risk.
  Date/Author: 2026-04-09 / Codex

## Outcomes & Retrospective

The cleanup landed and met the intended goal: the passkey trading-session hotspots are now materially flatter, more covered, and easier to change without touching public behavior. The implementation preserved the current storage keys, runtime entry points, lockbox return shapes, and remembered-session UX while splitting the riskiest logic into smaller helpers.

Measured results from the refreshed CRAP audit:

- `/hyperopen/src/hyperopen/platform/webauthn.cljs`: module `crapload` dropped from `80.53` to `0.0`
- `/hyperopen/src/hyperopen/wallet/agent_session.cljs`: module `crapload` dropped from `79.38` to `0.0`
- `/hyperopen/src/hyperopen/wallet/agent_lockbox.cljs`: module `crapload` dropped from `72.0` to `0.0`
- `/hyperopen/src/hyperopen/wallet/agent_runtime.cljs`: module `crapload` dropped from `53.28` to `0.0`

The most important function-level improvements were:

- `hyperopen.platform.webauthn/create-passkey-credential!`: `CRAP 110` to `2.0`
- `hyperopen.platform.webauthn/passkey-capable?`: `CRAP 30.53` to `4.0`
- `hyperopen.wallet.agent-session/sanitize-passkey-session-metadata`: `CRAP 109.38` to no remaining `crappy` status after the logic was split into covered helper seams
- `hyperopen.wallet.agent-lockbox/create-locked-session!`: `CRAP 90` to `4.0`
- `hyperopen.wallet.agent-lockbox/unlock-locked-session!`: `CRAP 42` to `3.00`
- `hyperopen.wallet.agent-runtime/set-agent-local-protection-mode!`: `CRAP 57.28` to `12.12`
- `hyperopen.wallet.agent-runtime/unlock-agent-trading!`: `CRAP 56` to `4.01`

Validation completed successfully with:

- `bb -m dev.check-delimiters --changed`
- `npx shadow-cljs --force-spawn compile test`
- `npm test`
- `npm run test:websocket`
- `npm run coverage`
- `npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "passkey|session toggles|locked remembered"`
- `npm run browser:cleanup`
- `npm run lint:namespace-sizes`
- `npm run lint:namespace-sizes:test`
- `npm run check`

The remaining debt is structural, not behavioral. The passkey runtime and tests are still large namespaces, and the size-budget file now documents that explicitly. The next ROI step, if this area needs more cleanup later, is to split passkey-session transition helpers and their test fixtures into narrower namespaces rather than to chase more CRAP wins inside the current owners.

## Context and Orientation

Hyperopen’s remembered trading session feature has three local storage shapes. Session-only mode stores the raw agent key in `sessionStorage`. Remembered plain mode stores the raw agent key in `localStorage`. Remembered passkey mode stores passkey metadata in `localStorage`, an encrypted lockbox in IndexedDB, and the decrypted agent key only in memory while the session is unlocked.

The passkey-session code is spread across a few specific modules:

`/hyperopen/src/hyperopen/wallet/agent_session.cljs` owns storage keys, raw session persistence, passkey metadata persistence, storage preferences, and metadata normalization. A “metadata record” here is the non-secret remembered-session information used to identify a passkey credential and rebuild agent state after unlock.

`/hyperopen/src/hyperopen/wallet/agent_lockbox.cljs` owns the encrypted lockbox flow. A “lockbox” here means the AES-GCM encrypted private key record stored in IndexedDB, plus the helper functions that derive the encryption key from the passkey PRF output.

`/hyperopen/src/hyperopen/wallet/agent_runtime.cljs` owns state transitions such as switching local protection mode, enabling trading, unlocking trading, and locking trading. This is the business-logic layer that updates the app store.

`/hyperopen/src/hyperopen/platform/webauthn.cljs` is the browser-facing adapter around WebAuthn. It asks the browser whether passkey unlock is supported, creates a passkey credential, and derives the PRF secret used by the lockbox.

`/hyperopen/src/hyperopen/api/trading.cljs`, `/hyperopen/src/hyperopen/order/actions.cljs`, `/hyperopen/src/hyperopen/order/effects.cljs`, `/hyperopen/src/hyperopen/views/header/*.cljs`, and `/hyperopen/src/hyperopen/startup/restore.cljs` are adjacent consumers. They matter for regression testing, but the CRAP audit shows they are not the primary cleanup targets.

The current measured hotspots that justify this plan are:

- `/hyperopen/src/hyperopen/platform/webauthn.cljs`: module `crapload 80.53`
- `/hyperopen/src/hyperopen/wallet/agent_session.cljs`: module `crapload 79.38`
- `/hyperopen/src/hyperopen/wallet/agent_lockbox.cljs`: module `crapload 72.0`
- `/hyperopen/src/hyperopen/wallet/agent_runtime.cljs`: module `crapload 53.28`

Within those files, the specific high-risk functions are:

- `/hyperopen/src/hyperopen/platform/webauthn.cljs`: `create-passkey-credential!`, `passkey-capable?`
- `/hyperopen/src/hyperopen/wallet/agent_session.cljs`: `sanitize-passkey-session-metadata`
- `/hyperopen/src/hyperopen/wallet/agent_lockbox.cljs`: `create-locked-session!`, `unlock-locked-session!`
- `/hyperopen/src/hyperopen/wallet/agent_runtime.cljs`: `set-agent-local-protection-mode!`, `unlock-agent-trading!`

This plan intentionally does not change the product trust model. It does not remove plain mode, does not change passkey UX requirements, does not move keys into an extension/native signer, and does not alter the browser storage contract except where helper extraction is needed to preserve existing behavior more clearly.

## Plan of Work

### Milestone 1: Add Coverage Where The CRAP Is Mostly Test Debt

Start with tests, because the highest-value paths are also the least-covered. In `/hyperopen/test/hyperopen/platform/webauthn_test.cljs`, extend the browser capability and credential creation matrix so it covers the success and failure branches currently driving `passkey-capable?` and `create-passkey-credential!`. Cover secure-browser absence, `getClientCapabilities` success with and without PRF/platform flags, `getClientCapabilities` rejection, legacy `isUserVerifyingPlatformAuthenticatorAvailable` success and rejection, the `:else` unsupported branch, `create-passkey-credential!` with and without `prf-salt`, fallback `user-id` behavior, and the case where the browser returns a credential without a usable `credential-id`.

In `/hyperopen/test/hyperopen/wallet/agent_runtime_test.cljs`, add focused coverage for `unlock-agent-trading!` and for the missing failure branches inside `set-agent-local-protection-mode!`. The new tests must cover missing wallet address, non-passkey mode, missing passkey metadata, live `plain -> passkey` with no live session, lockbox creation rejection, passkey metadata persistence failure, preference persistence failure, raw local-session clearing failure, passkey-to-plain raw-session persistence failure, passkey-to-plain preference persistence failure, and the non-`:ready` preference-only branch.

In `/hyperopen/test/hyperopen/wallet/agent_session_test.cljs` and `/hyperopen/test/hyperopen/websocket/agent_session_coverage_test.cljs`, add tests for passkey metadata normalization and persisted snapshot loading. Cover malformed JSON, blank or missing required strings, dropped blank transports, legacy device-label normalization, flooring numeric timestamps only when numeric, version defaulting, and correct snapshot selection for `:session`, `:plain`, and `:passkey`.

In `/hyperopen/test/hyperopen/platform/indexed_db_test.cljs` and `/hyperopen/test/hyperopen/wallet/agent_runtime_test.cljs`, add enough seams around the lockbox workflow to make later refactors safe. The point is not exhaustive browser integration. The point is to lock down the current return shapes and failure messages before the orchestration code is broken apart.

Milestone 1 is complete when the targeted tests fail if the current high-risk branches regress and the CRAP reports for the audited modules show lower scores from improved coverage alone, even before helper extraction.

### Milestone 2: Split The Cheapest Pure Hotspot First

Refactor `/hyperopen/src/hyperopen/wallet/agent_session.cljs` by breaking `sanitize-passkey-session-metadata` into small pure helpers. Introduce helpers for required non-blank string extraction, optional integer coercion, transport normalization, and final metadata assembly. Keep the external behavior unchanged: the function must still reject records missing `agent-address`, `credential-id`, or `prf-salt`, still normalize `device-label`, still preserve optional `transports`, and still default `:version` to `1`.

This slice is first because it has no async browser dependencies and stabilizes the metadata contract that both the runtime and lockbox layers consume. It is also the easiest place to reduce branch density without adding orchestration risk.

Milestone 2 is complete when the metadata normalization logic reads as a short top-level assembler over field helpers, all tests from Milestone 1 still pass, and the CRAP score for `sanitize-passkey-session-metadata` falls meaningfully below the current triple-digit hotspot.

### Milestone 3: Decompose Runtime Transition Orchestration

Refactor `/hyperopen/src/hyperopen/wallet/agent_runtime.cljs` next. Keep `set-agent-local-protection-mode!` and `unlock-agent-trading!` as the public runtime entry points, but extract small helpers that separate branch classification from store mutation and Promise chaining.

For `set-agent-local-protection-mode!`, extract a pure transition classifier and focused helpers for the concrete flows: `plain -> passkey`, `passkey -> plain`, blocked downgrade while locked, and preference-only update when no live migration is possible. Extract repeated state-shape construction into helpers such as `ready-state-from-session` and `locked-or-error-state-from-metadata` so the top-level function does not repeat store merge maps across branches.

For `unlock-agent-trading!`, extract precondition evaluation from async unlock handling. The top-level function should read as: resolve owner address and mode, reject impossible states, call `unlock-locked-session!`, then project either the ready state or the locked-error state through small builders. Keep the exact current messages and state fields unless a test proves they need to change.

Do not remove or rewrite the existing helper seams that are already small and useful. `migration-session`, `migration-session-for-mode`, `apply-migrated-agent-session!`, and `set-agent-local-protection-error!` are already good anchors and should remain unless a later implementation proves otherwise.

Milestone 3 is complete when the runtime entry points are shorter, duplicated store-shape assembly has been removed, the focused runtime tests still pass, and the CRAP report no longer marks `set-agent-local-protection-mode!` or `unlock-agent-trading!` as the dominant maintainability hazards in the module.

### Milestone 4: Split Browser-Bound WebAuthn And Lockbox Flows

Refactor `/hyperopen/src/hyperopen/platform/webauthn.cljs` before deeper lockbox extraction so the lockbox layer depends on smaller browser adapters rather than raw browser response shapes. Extract capability evaluation, create-options building, and credential-result decoding from `passkey-capable?` and `create-passkey-credential!`. The browser-facing functions should still return the same high-level shapes, but the internal code should no longer mix option construction, browser invocation, and response parsing in one body.

Then refactor `/hyperopen/src/hyperopen/wallet/agent_lockbox.cljs`. Keep the crypto helpers `derive-lockbox-key!`, `encrypt-private-key!`, `decrypt-private-key!`, and `resolve-prf-output!` as leaf functions. The cleanup target is the orchestration around them. Extract validation helpers, PRF acquisition helpers, lockbox persistence/load helpers, and the final session-assembly helper so that `create-locked-session!` and `unlock-locked-session!` read as linear orchestration rather than nested Promise trees.

Keep the public contracts stable. `create-locked-session!` must still return `{:metadata ... :session ...}`. `unlock-locked-session!` must still return the unlocked session map. IndexedDB store names, localStorage key shapes, and returned metadata fields must not change as part of this cleanup.

Milestone 4 is complete when the top-level WebAuthn and lockbox functions are visibly flatter, the browser and lockbox tests cover the extracted seams, and the CRAP scores for those modules fall due to both lower complexity and higher coverage.

### Milestone 5: Re-Audit And Prove No User-Visible Regression

After the refactors, regenerate coverage and rerun the module-level CRAP reports for the audited files. Record the before/after values in this ExecPlan. Then run the repo’s required validation gates and the smallest relevant Playwright passkey regression. The user-visible behavior must remain unchanged: remembered passkey sessions still restore as `:locked`, unlock still works, and the locked-submit path still routes into passkey unlock rather than recovery.

Milestone 5 is complete when the cleanup reduces the measured hotspots, all required commands pass, and the plan can be moved out of `active` with an updated retrospective that compares pre-cleanup and post-cleanup complexity.

## Concrete Steps

From `/Users/barry/.codex/worktrees/daf8/hyperopen`, use this order.

1. Reconfirm the current hotspots from fresh coverage:

   `npm run coverage`

   `bb tools/crap_report.clj --module src/hyperopen/platform/webauthn.cljs --format json`

   `bb tools/crap_report.clj --module src/hyperopen/wallet/agent_session.cljs --format json`

   `bb tools/crap_report.clj --module src/hyperopen/wallet/agent_lockbox.cljs --format json`

   `bb tools/crap_report.clj --module src/hyperopen/wallet/agent_runtime.cljs --format json`

2. Add and run targeted tests before refactors:

   `npx shadow-cljs --force-spawn compile test`

   `npm test`

   The first code edits should be in:

   - `/hyperopen/test/hyperopen/platform/webauthn_test.cljs`
   - `/hyperopen/test/hyperopen/wallet/agent_runtime_test.cljs`
   - `/hyperopen/test/hyperopen/wallet/agent_session_test.cljs`
   - `/hyperopen/test/hyperopen/websocket/agent_session_coverage_test.cljs`
   - any small supporting test seam needed for the lockbox path, likely under `/hyperopen/test/hyperopen/wallet/**` or `/hyperopen/test/hyperopen/platform/**`

3. Refactor pure metadata normalization:

   - edit `/hyperopen/src/hyperopen/wallet/agent_session.cljs`
   - rerun `npm test`
   - rerun `bb tools/crap_report.clj --module src/hyperopen/wallet/agent_session.cljs --format json`

4. Refactor runtime orchestration:

   - edit `/hyperopen/src/hyperopen/wallet/agent_runtime.cljs`
   - rerun `npm test`
   - rerun `bb tools/crap_report.clj --module src/hyperopen/wallet/agent_runtime.cljs --format json`

5. Refactor WebAuthn and lockbox orchestration:

   - edit `/hyperopen/src/hyperopen/platform/webauthn.cljs`
   - edit `/hyperopen/src/hyperopen/wallet/agent_lockbox.cljs`
   - rerun `npm test`
   - rerun the two module-level CRAP reports

6. Run final validation:

   `npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "passkey|session toggles|locked remembered passkey session submit triggers unlock flow instead of recovery"`

   `npm run test:websocket`

   `npm run check`

   `npm run browser:cleanup`

Expected proof at the end:

The hotspot functions are shorter and better-covered, CRAP reports for the audited modules are lower than the baseline captured in this plan, and the passkey trading session browser behavior is unchanged.

## Validation and Acceptance

Acceptance requires all of the following.

The targeted test additions must make the currently under-covered passkey branches explicit. If a later contributor breaks missing-wallet handling, passkey capability detection, lockbox record loading, metadata normalization, or unlock error projection, one of the focused tests added by this cleanup must fail.

The cleanup must preserve all current user-visible passkey behavior. A remembered passkey session must still restore as `:locked` after reload, the user must still be able to unlock trading with a passkey, and submitting an order from a locked remembered passkey session must still trigger unlock rather than the recovery modal.

The cleanup must preserve local persistence contracts. Existing storage keys, IndexedDB store names, and session metadata fields must stay compatible so already-remembered sessions do not become unreadable because of the refactor.

The cleanup must lower the measured hotspots. At minimum, the re-run CRAP report should show meaningful improvement for the baseline target functions:

- `hyperopen.platform.webauthn/create-passkey-credential!`
- `hyperopen.platform.webauthn/passkey-capable?`
- `hyperopen.wallet.agent-session/sanitize-passkey-session-metadata`
- `hyperopen.wallet.agent-lockbox/create-locked-session!`
- `hyperopen.wallet.agent-lockbox/unlock-locked-session!`
- `hyperopen.wallet.agent-runtime/set-agent-local-protection-mode!`
- `hyperopen.wallet.agent-runtime/unlock-agent-trading!`

All required repository validation commands for code changes must pass:

- `npm test`
- `npm run test:websocket`
- `npm run check`

When browser-facing passkey or remembered-session behavior changes, the smallest relevant Playwright regression must also pass and browser sessions must be cleaned up with `npm run browser:cleanup`.

## Idempotence and Recovery

This cleanup should be implemented in small slices that are safe to rerun. Each slice starts with focused tests, then a narrow refactor, then a local rerun of the affected tests and CRAP report. If a slice stalls or introduces confusion, revert only that slice’s edits and rerun the focused tests before moving to the next slice.

Do not combine behavior changes with cleanup. If a refactor reveals a real bug, file or link a separate `bd` issue unless the bug is inseparable from preserving existing behavior during the refactor. This plan is specifically for complexity reduction and verification, not for changing UX or security posture.

If the browser regression becomes flaky during the cleanup, stop and restore the last stable local state before broadening the refactor. The passkey-session code sits on browser APIs and local persistence, so large unvalidated edits will make diagnosis harder.

## Artifacts and Notes

Baseline audit evidence from the current tree:

  `/hyperopen/src/hyperopen/platform/webauthn.cljs`
  module `crapload 80.53`
  `create-passkey-credential!` -> `CRAP 110`, coverage `0`
  `passkey-capable?` -> `CRAP 30.53`, coverage `0.12`

  `/hyperopen/src/hyperopen/wallet/agent_session.cljs`
  module `crapload 79.38`
  `sanitize-passkey-session-metadata` -> `CRAP 109.38`, coverage `0.0667`

  `/hyperopen/src/hyperopen/wallet/agent_lockbox.cljs`
  module `crapload 72.0`
  `create-locked-session!` -> `CRAP 90`, coverage `0`
  `unlock-locked-session!` -> `CRAP 42`, coverage `0`

  `/hyperopen/src/hyperopen/wallet/agent_runtime.cljs`
  module `crapload 53.28`
  `set-agent-local-protection-mode!` -> `CRAP 57.28`, complexity `29`
  `unlock-agent-trading!` -> `CRAP 56`, coverage `0`

Low-priority adjacent modules from the same audit:

  `/hyperopen/src/hyperopen/api/trading.cljs`
  no `crappy` functions; worst score `9`

  `/hyperopen/src/hyperopen/order/actions.cljs`
  no `crappy` functions; worst score `10.88`

  `/hyperopen/src/hyperopen/order/effects.cljs`
  no `crappy` functions; worst score `21.22`

  `/hyperopen/src/hyperopen/views/header/settings.cljs`
  no `crappy` functions; worst score `5`

These lower-risk files should stay out of scope unless a targeted cleanup slice proves they block the hotspot reductions above.

Plan revision note: created on 2026-04-09 after a fresh `npm run coverage` plus targeted CRAP audit showed that the passkey trading session implementation’s maintainability risk is concentrated in WebAuthn, metadata normalization, lockbox orchestration, and runtime transition logic. The ordering in this plan was shaped by both the local audit and two read-only sidecar analyses that independently recommended tests first, then pure helper extraction, then orchestration refactors.
