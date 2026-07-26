# Subaccounts Owner-Mode Identity Scope

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` are maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Sub-account transfers choose between Hyperliquid's legacy subaccount transfer primitive and `sendAsset` based on whether the master account is classic or unified. A prior fix stored the master mode at `[:account-context :subaccounts :owner-mode]`, but stored it as a bare keyword. If the displayed master changes, that bare keyword can outlive its owner and route the next owner's transfer through the wrong primitive. After this change, owner mode is scoped to the displayed master address, so stale mode from another owner is ignored and transfer routing waits for, or preserves, the current owner's actual mode.

## Context References

Public refs:

- Direct user/maintainer request on 2026-06-07: rebase the Claude worktree onto local `main`, review the subaccount implementation, then create and execute a plan for the remaining owner-mode issue.

Repo artifacts:

- `docs/exec-plans/completed/2026-06-07-subaccounts-recovery-and-owner-mode-transfer-routing.md` introduced non-destructive refresh and owner-mode transfer routing.
- `docs/exec-plans/active/2026-06-07-subaccounts-route-refresh-stale-loader.md` records the original stuck-loader regression and refresh behavior.
- `/hyperopen/AGENTS.md` requires an ExecPlan for risky bug/UI work and requires `npm run check`, `npm test`, and `npm run test:websocket` for code changes.

Local scratch refs, non-authoritative:

- The current review found `src/hyperopen/account/context.cljs` trusts any `[:account-context :subaccounts :owner-mode]` keyword, while `src/hyperopen/subaccounts/actions.cljs` changes `:loaded-for-owner` without clearing or keying that owner mode.

## Progress

- [x] (2026-06-08 00:31Z) Rebased `/Users/barry/projects/hyperopen/.claude/worktrees/sweet-neumann-393515` onto local `main` at `a6925b0a` and re-reviewed the merged result.
- [x] (2026-06-08 00:31Z) Identified that `:owner-mode` is not identity-scoped and that `apply-owner-mode!` compares against the wallet owner rather than the displayed master, which also matters for spectate mode.
- [x] (2026-06-08 00:31Z) Wrote this active ExecPlan before implementation.
- [x] (2026-06-08 00:34Z) Added RED unit coverage for route owner changes, load-time owner-mode scoping, stale transfer routing, spectated owner-mode loading, and late owner-mode responses after viewed-owner changes.
- [x] (2026-06-08 00:38Z) Implemented identity-scoped owner-mode records and a shared displayed-master helper for Sub-Accounts routing and transfer decisions.
- [x] (2026-06-08 00:44Z) Ran `npm test`; result: 4280 tests, 23615 assertions, 0 failures, 0 errors.
- [x] (2026-06-08 00:50Z) Ran the first completed-plan validation pass: `npm run check` passed, `npm test` passed with 4280 tests / 23615 assertions, `npm run test:websocket` passed with 534 tests / 3090 assertions, and the targeted Sub-Accounts Playwright regression file passed 5 tests.
- [x] (2026-06-08 01:08Z) Tightened pending/mismatched owner-mode semantics so scoped records never fall back to active account mode, then reran validation: `npm test` passed with 4281 tests / 23618 assertions, `npm run check` passed, `npm run test:websocket` passed with 534 tests / 3090 assertions, and the targeted Sub-Accounts Playwright regression file passed 5 tests.

## Surprises & Discoveries

- Observation: `apply-owner-mode!` guards by `account-context/owner-address`, but the Sub-Accounts page can display a spectated master through `actions/viewed-master-address`.
  Evidence: `src/hyperopen/subaccounts/effects.cljs` calls `load-owner-mode!` with `actions/viewed-master-address @store`, while `apply-owner-mode!` compares the response owner to `account-context/owner-address state`.

- Observation: Adding the owner-mode regression coverage directly to the existing action/effect test namespaces tripped the repo's namespace-size guardrail.
  Evidence: `npm run check` failed with `test/hyperopen/subaccounts/actions_test.cljs` and `test/hyperopen/subaccounts/effects_test.cljs` over their configured limits.

- Observation: The stricter pending-mode rule exposed a Playwright fixture that seeded only `[:account :mode]` for a unified master.
  Evidence: the `unified subaccounts transfer submits sendAsset instead of subAccountTransfer` browser test stopped observing `sendAsset` until `seedSubaccountsState` wrote the new scoped owner-mode record.

## Decision Log

- Decision: Store owner mode as a record containing both the owner address and the mode, for example `{:owner "0x..." :mode :unified}`, rather than as a bare `:unified` or `:classic` keyword.
  Rationale: A keyword has no identity and can leak across wallet, spectate, or viewed-master changes. A scoped record lets readers check whether the mode belongs to the current displayed master before routing money movement.
  Date/Author: 2026-06-08 / Codex

- Decision: Preserve a known mode for the same owner during refresh, but replace mismatched or legacy owner-mode state with a pending record for the current owner.
  Rationale: Preserving the same owner's known mode avoids a UI/routing flicker during refresh. Replacing mismatched state closes the stale-owner race.
  Date/Author: 2026-06-08 / Codex

- Decision: Treat matching pending owner-mode records and mismatched scoped owner-mode records as not unified, instead of falling back to `[:account :mode]`.
  Rationale: Once Sub-Accounts has an owner-mode record, even a pending one, falling back to active account mode can reintroduce identity leakage. The active-mode fallback remains only for legacy/minimal state where owner-mode is truly absent.
  Date/Author: 2026-06-08 / Codex

## Outcomes & Retrospective

Implementation is complete. `subaccounts-owner-unified?` can only return true from owner-mode state when a scoped record belongs to the current displayed master and has `:mode :unified`, so a previous unified owner cannot make the next classic owner submit `sendAsset`. Route loads and direct effect loads now seed a pending scoped record for the displayed master, refresh preserves a same-owner mode, and late owner-mode responses are discarded if the displayed master has changed.

The test layout changed during implementation: owner-mode regression coverage now lives in `test/hyperopen/subaccounts/owner_mode_test.cljs` so the existing action/effect namespaces stay under the repo guardrail without expanding exceptions.

## Context and Orientation

The Sub-Accounts page lists subaccounts owned by a master account. The displayed master is normally the connected wallet, but in spectate mode it is the spectated address. `src/hyperopen/subaccounts/actions.cljs` exposes `viewed-master-address` for that displayed-master identity.

`src/hyperopen/subaccounts/effects.cljs` loads subaccount rows and, in parallel, asks the API for the displayed master's account abstraction mode. Hyperliquid unified accounts require transfers through `sendAsset`; classic masters use the legacy subaccount transfer primitive for perps transfers. The transfer submit path is `transfer-subaccount!` in `src/hyperopen/subaccounts/effects.cljs`.

`src/hyperopen/account/context.cljs` previously had `subaccounts-owner-unified?`, which returned true if `[:account-context :subaccounts :owner-mode]` was `:unified`; otherwise it fell back to `[:account :mode]`. That was the defect: a bare `:owner-mode` said nothing about which master it belonged to.

## Plan of Work

First add RED unit tests. In `test/hyperopen/subaccounts/actions_test.cljs`, update route-load expectations so a cold load for an owner writes a pending scoped owner-mode record, and add a test proving a stale owner-mode record is replaced when the viewed master changes. In `test/hyperopen/subaccounts/effects_test.cljs`, add tests proving `load-subaccounts!` records `{:owner owner-address :mode :unified}` when the owner-mode request resolves, does not stamp the response after the viewed master changes, and routes a classic new owner through the legacy transfer even if stale state says the previous owner was unified. In `test/hyperopen/account/context_test.cljs`, add direct tests for `subaccounts-owner-unified?` so a matching unified record returns true, a mismatched record returns false, and no scoped record falls back to active account mode for legacy/simple state.

Then implement the state invariant. In `src/hyperopen/account/context.cljs`, add a helper for the displayed subaccounts owner and update `subaccounts-owner-unified?` to trust only matching scoped records. In `src/hyperopen/subaccounts/actions.cljs`, make route loads and refreshes write a pending scoped owner-mode record when the current owner differs from the recorded owner, while preserving a known record for the same owner. In `src/hyperopen/subaccounts/effects.cljs`, update `apply-owner-mode!` to write the scoped record and guard against `actions/viewed-master-address`, not just the connected wallet owner. Add a small prepare step in `load-subaccounts!` so direct effect calls also replace stale owner-mode before requests settle.

Finally update tests that intentionally seed `:owner-mode` to use the scoped record shape. Keep compatibility for `nil` owner-mode fallback where tests construct minimal state without a route load.

## Concrete Steps

Work from `/Users/barry/projects/hyperopen/.claude/worktrees/sweet-neumann-393515`.

Write the RED tests first and run:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js

Expected before implementation: the new owner-mode scoping assertions fail because the current code writes or trusts bare owner-mode keywords.

After implementation, run the focused full generated CLJS command again:

    npm test

Expected after implementation: all generated CLJS tests pass.

Run the required repo gates:

    npm run check
    npm test
    npm run test:websocket

Run the targeted browser regression file. If port 8080 is occupied, use the alternate static-server command:

    PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/subaccounts-regressions.spec.mjs

Expected: the subaccounts Playwright regression file passes.

## Validation and Acceptance

Acceptance is that a stale unified owner-mode record from owner A cannot make owner B's classic perps transfer call `sendAsset`. Unit tests must show a matching owner-mode record drives unified routing, a mismatched owner-mode record is ignored, route/effect loads replace stale records with the current owner, and owner-mode responses for old displayed masters are discarded.

Browser acceptance is no regression in the existing Sub-Accounts Playwright coverage: the spectated master loads read-only, refresh/transfer rows remain usable, unified transfers still submit `sendAsset`, and mixed-mode unified-master withdrawals still submit `sendAsset` when the scoped owner record matches.

## Idempotence and Recovery

The implementation is additive and safe to retry. If tests reveal that a pending owner-mode record causes unwanted UI flicker, preserve same-owner records during route refresh and only replace mismatched records. If the abstraction-mode request fails, the page should still load rows, and scoped pending state must not trust a previous owner's mode.

## Artifacts and Notes

Review evidence before implementation:

    src/hyperopen/account/context.cljs:158
      subaccounts-owner-unified? trusts any existing :owner-mode keyword.

    src/hyperopen/subaccounts/actions.cljs:107
      load-route-path-values updates :loaded-for-owner and rows but did not key :owner-mode.

    src/hyperopen/subaccounts/effects.cljs:96
      apply-owner-mode! compares the response owner to wallet owner, not the displayed master.

## Interfaces and Dependencies

The owner-mode state shape after this work is:

    [:account-context :subaccounts :owner-mode] => nil
    [:account-context :subaccounts :owner-mode] => {:owner normalized-address :mode nil}
    [:account-context :subaccounts :owner-mode] => {:owner normalized-address :mode :classic}
    [:account-context :subaccounts :owner-mode] => {:owner normalized-address :mode :unified}

`account-context/subaccounts-owner-unified?` remains the public predicate used by views, management actions, and transfer effects. It must not require callers to know the owner-mode record shape.
