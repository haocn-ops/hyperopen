# Unified vs Standard Account Value Parity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`.

Tracking epic: `hyperopen-fq5`.

## Purpose / Big Picture

After this change, Hyperopen will render account value and balance composition correctly for both account types observed in Hyperliquid and trade.xyz behavior: unified accounts and standard (DEX abstraction) accounts. Today, Hyperopen collapses `dexAbstraction` into unified mode, which causes standard accounts to render unified-style account value semantics. The user-visible fix is simple: when spectating or viewing a standard account, Hyperopen must show standard account value semantics; when viewing a unified account, Hyperopen must keep unified semantics.

A user will verify this by spectating both provided addresses in Ghost Mode on `/trade` and `/portfolio`. The unified address must retain unified-style presentation, and the standard address must switch to standard-style presentation without manual overrides.

## Progress

- [x] (2026-03-04 23:22Z) Re-read planning constraints in `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and repo `AGENTS.md` guardrails.
- [x] (2026-03-04 23:28Z) Captured live trade.xyz Ghost Mode behavior for both addresses using Playwright and request tracing.
- [x] (2026-03-04 23:33Z) Queried Hyperliquid `userAbstraction` for both addresses and confirmed mode payload divergence (`unifiedAccount` vs `dexAbstraction`).
- [x] (2026-03-04 23:35Z) Located Hyperopen root cause in `/hyperopen/src/hyperopen/api/endpoints/account.cljs` where `dexAbstraction` is normalized to `:unified`.
- [x] (2026-03-04 23:38Z) Created epic `hyperopen-fq5` and child tasks `hyperopen-fq5.1` through `hyperopen-fq5.4` in `bd`.
- [x] (2026-03-04 23:45Z) Authored this ExecPlan with file-level implementation and verification scope.
- [x] (2026-03-05 00:05Z) Implemented Milestone 1 by mapping `dexAbstraction` to `:classic` in `/hyperopen/src/hyperopen/api/endpoints/account.cljs` and updating API contract docs.
- [x] (2026-03-05 00:14Z) Implemented Milestone 2 by making account-equity account-value labeling mode-specific (`Account Value` vs `Unified Account Value`) while preserving unified vs classic calculation branches.
- [x] (2026-03-05 00:16Z) Implemented Milestone 3 with regression coverage updates in endpoint/API/websocket parity tests plus account-equity view assertions.
- [x] (2026-03-05 00:27Z) Completed Milestone 4 with a fresh local Ghost Mode parity run on `/trade` and `/portfolio` for both addresses, including screenshots and structured signal capture.

## Surprises & Discoveries

- Observation: trade.xyz Ghost Mode can be driven directly by URL query param `?ghost=<address>`.
  Evidence: visiting `https://app.trade.xyz/?ghost=0x4096d3377ae5ade578daae8188804740c8b1da3e` and `https://app.trade.xyz/?ghost=0x2ba553d9f990a3b66b03b2dc0d030dfc1c061036` immediately switched read context to those addresses.

- Observation: trade.xyz renders materially different account-value labels and composition for these two addresses.
  Evidence: unified address showed `Unified Account Value`; standard address showed `Account Value` with explicit `Spot` and `Perps` equity rows.

- Observation: Hyperliquid API returns different abstraction identifiers for the same two addresses.
  Evidence: `{"type":"userAbstraction","user":"0x4096...da3e"}` returned `"unifiedAccount"`; `{"type":"userAbstraction","user":"0x2ba5...1036"}` returned `"dexAbstraction"`.

- Observation: Hyperopen previously mapped `dexAbstraction` to `:unified`; after this change it maps to `:classic`, which unlocks existing standard-account UI branches.
  Evidence: `/hyperopen/src/hyperopen/api/endpoints/account.cljs` now returns `:classic` for `"dexAbstraction"` in `normalize-user-abstraction-mode`.

- Observation: repeated live probing can trigger Hyperliquid 429 responses on some non-critical portfolio side endpoints.
  Evidence: Playwright API traces for the standard address showed intermittent 429s for delegator/user-fee-related calls while core account payloads still returned.

## Decision Log

- Decision: Keep Hyperopen’s high-level UI branch as a binary distinction (`:unified` vs non-unified) and classify `dexAbstraction` as non-unified for rendering.
  Rationale: The immediate parity requirement is unified vs standard account-value semantics, and existing non-unified rendering code paths already implement the expected standard composition.
  Date/Author: 2026-03-04 / Codex

- Decision: Preserve `:abstraction-raw` as the source payload string and avoid dropping this metadata even when normalizing mode.
  Rationale: Future UI affordances may need to distinguish `default` from `dexAbstraction` without reworking data fetch plumbing.
  Date/Author: 2026-03-04 / Codex

- Decision: Validate parity through both automated tests and manual Ghost Mode checks for the two addresses provided in the epic.
  Rationale: This bug is both data-contract and user-visible UI behavior; tests alone are insufficient without end-to-end rendering verification.
  Date/Author: 2026-03-04 / Codex

## Outcomes & Retrospective

Implemented outcome:

- `dexAbstraction` is now normalized to `:classic`, so standard accounts no longer route through unified account-mode projections.
- Account-equity labeling now explicitly distinguishes account types: classic shows `Account Value`, unified shows `Unified Account Value`.
- Regression coverage was expanded in endpoint/API/websocket and account-equity view tests to lock mode-specific behavior.
- Required validation gates all passed after changes:
  - `npm run check`
  - `npm test` (1870 tests / 9646 assertions)
  - `npm run test:websocket` (295 tests / 1679 assertions)

Residual risk:

- None blocking for this parity scope; local and external parity checks both confirm the unified vs standard split for trade and portfolio summary surfaces.

## Context and Orientation

Define terms as used in this repository:

- Abstraction payload: the string returned by Hyperliquid `userAbstraction` endpoint.
- Normalized account mode: `[:account :mode]` in Hyperopen state, used by view models and projections to branch unified vs non-unified behavior.
- Unified account rendering: paths that collapse USDC presentation and use unified-equity semantics (for example, hiding perps/earn split rows in portfolio summary).
- Standard account rendering: paths that keep perps and spot separated and label account value in the non-unified style.

Relevant files and responsibilities:

- `/hyperopen/src/hyperopen/api/endpoints/account.cljs` normalizes `userAbstraction` payloads.
- `/hyperopen/src/hyperopen/api/fetch_compat.cljs` and `/hyperopen/src/hyperopen/api/projections.cljs` project normalized account mode into app state.
- `/hyperopen/src/hyperopen/views/account_equity_view.cljs` switches unified vs classic account-equity panel.
- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` controls portfolio summary row visibility and `Trading Equity` vs `Spot Account Equity` labeling.
- `/hyperopen/src/hyperopen/views/account_info/projections/balances.cljs` controls unified USDC merge vs separate perps/spot USDC rows.
- `/hyperopen/src/hyperopen/account/context.cljs`, `/hyperopen/src/hyperopen/wallet/address_watcher.cljs`, and `/hyperopen/src/hyperopen/startup/runtime.cljs` ensure Ghost Mode uses effective read identity and fetches fresh account mode per effective address.

Known external behavior baseline (2026-03-04):

- Unified address: `0x4096d3377ae5ade578daae8188804740c8b1da3e`
- Standard address: `0x2ba553d9f990a3b66b03b2dc0d030dfc1c061036`
- trade.xyz Ghost URLs:
  - `https://app.trade.xyz/?ghost=<address>`
  - `https://app.trade.xyz/portfolio?ghost=<address>`

## Plan of Work

### Milestone 1: Correct account-mode normalization contract

Update `/hyperopen/src/hyperopen/api/endpoints/account.cljs` so `normalize-user-abstraction-mode` no longer maps `"dexAbstraction"` to `:unified`. Keep `"unifiedAccount"` and `"portfolioMargin"` as `:unified`; treat `"dexAbstraction"` as non-unified mode (current codebase uses `:classic` for non-unified behavior).

Update inline contract docs in `/hyperopen/src/hyperopen/api/default.cljs` to describe the revised mapping exactly, including what happens for unknown/nil values.

Update endpoint-level tests in `/hyperopen/test/hyperopen/api/endpoints/account_test.cljs` to lock this mapping.

### Milestone 2: Ensure rendering branches follow corrected mode end-to-end

Audit and adjust mode-sensitive view-model/projection branches so they consume corrected mode without latent assumptions. Primary files:

- `/hyperopen/src/hyperopen/views/account_equity_view.cljs`
- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`
- `/hyperopen/src/hyperopen/views/account_info/projections/balances.cljs`

Most likely expected code outcome is minimal logic change because these modules already treat only `:unified` specially. The key is to verify that corrected normalization flows through Ghost Mode address switches and owner-mode switches without stale mode retention.

### Milestone 3: Add regression coverage for abstraction-to-rendering parity

Add/extend tests so the regression cannot return:

- Endpoint normalization tests in `/hyperopen/test/hyperopen/api/endpoints/account_test.cljs`.
- API projection tests in `/hyperopen/test/hyperopen/api_test.cljs` and/or `/hyperopen/test/hyperopen/api/compat_test.cljs` confirming `"dexAbstraction"` persists as non-unified mode snapshot.
- VM/view tests in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` and `/hyperopen/test/hyperopen/views/account_equity_view_test.cljs` that prove non-unified rows/labels appear when mode is non-unified.

Add a fixture-style assertion note (or dedicated test data helper) referencing the two parity addresses for manual QA traceability, even if tests remain unit-level and do not call live endpoints.

### Milestone 4: Validate parity and document evidence

Run required gates:

- `npm run check`
- `npm test`
- `npm run test:websocket`

Then perform manual Ghost Mode verification on local Hyperopen for both addresses and record observed UI labels/composition in this plan’s `Artifacts and Notes` section. If behavior diverges from trade.xyz baseline, file follow-up `bd` tasks linked as discovered-from `hyperopen-fq5`.

## Concrete Steps

Work from repository root:

    cd /Users/barry/.codex/worktrees/8166/hyperopen

1. Implement normalization change and tests.

    rg -n "normalize-user-abstraction-mode|dexAbstraction" src/hyperopen/api/endpoints/account.cljs test/hyperopen/api/endpoints/account_test.cljs

    # edit files

    npm test -- test/hyperopen/api/endpoints/account_test.cljs

Expected: normalization test reports `dexAbstraction` mapped to non-unified mode and test passes.

2. Validate projection and mode-sensitive view-model behavior.

    npm test -- test/hyperopen/api_test.cljs test/hyperopen/views/portfolio/vm_test.cljs test/hyperopen/views/account_equity_view_test.cljs

Expected: portfolio/account-equity expectations still pass for unified mode and pass for non-unified mode with perps/spot split semantics.

3. Run full gates.

    npm run check
    npm test
    npm run test:websocket

Expected: all gates pass with zero failures.

4. Manual parity spot-check (after starting local app runtime as normally documented in this repo).

    # spectate unified
    # spectate standard
    # inspect /trade and /portfolio for account-equity/summary differences

Expected:
- Unified address retains unified-only labels/rows.
- Standard address shows non-unified account value semantics (spot/perps split).

5. Update living sections and issue status.

    bd update hyperopen-fq5.1 --status closed --json
    bd update hyperopen-fq5.2 --status closed --json
    bd update hyperopen-fq5.3 --status closed --json
    bd update hyperopen-fq5.4 --status closed --json
    bd close hyperopen-fq5 --reason "Completed" --json

If work is incomplete, leave remaining items open and record exact blockers in this plan and in the corresponding `bd` task.

## Validation and Acceptance

Acceptance is satisfied when all conditions below are true:

- `normalize-user-abstraction-mode` classifies `"dexAbstraction"` as non-unified mode.
- For unified mode, portfolio summary hides perps/earn split rows and uses unified-style equity labeling exactly as current unified behavior intends.
- For non-unified mode, portfolio summary and account-equity panel show spot/perps split semantics.
- Balance row projection no longer follows unified USDC merge behavior for `dexAbstraction` accounts.
- Ghost Mode switching between the two provided addresses updates account mode-driven rendering deterministically.
- Required validation gates pass (`npm run check`, `npm test`, `npm run test:websocket`).

## Idempotence and Recovery

All edits in this plan are source-controlled and idempotent. Re-running the steps should produce the same result.

If a change causes mode regressions:

- Revert only the impacted file-level edits with a follow-up commit rather than destructive workspace resets.
- Re-run the focused test commands in `Concrete Steps` before re-running full gates.
- If live endpoint behavior changes (for example Hyperliquid introduces new abstraction strings), add a defensive normalization branch plus tests and record the new payload in `Surprises & Discoveries`.

## Artifacts and Notes

Evidence captured during plan authoring (2026-03-04):

- trade.xyz Ghost Mode snapshots:
  - `/tmp/trade_unified-trade.png`
  - `/tmp/trade_unified-portfolio.png`
  - `/tmp/trade_standard-trade.png`
  - `/tmp/trade_standard-portfolio.png`

- Fresh local Hyperopen Ghost Mode parity run (2026-03-05):
  - Report JSON: `/tmp/hyperopen-ghost-parity-20260305-002649/parity-report.json`
  - Unified trade: `/tmp/hyperopen-ghost-parity-20260305-002649/unified-trade.png`
  - Unified portfolio: `/tmp/hyperopen-ghost-parity-20260305-002649/unified-portfolio.png`
  - Standard trade: `/tmp/hyperopen-ghost-parity-20260305-002649/standard-trade.png`
  - Standard portfolio: `/tmp/hyperopen-ghost-parity-20260305-002649/standard-portfolio.png`

- Local parity observations from the 2026-03-05 run:
  - Unified trade panel lines include `Unified Account Summary` and `Unified Account Value`; no classic `Account Equity`/`Account Value` lines.
  - Standard trade panel lines include `Account Equity` and `Account Value`; no unified account lines.
  - Unified portfolio summary includes `Trading Equity` and omits `Perps Account Equity` / `Spot Account Equity`.
  - Standard portfolio summary includes `Perps Account Equity` + `Spot Account Equity` and omits `Trading Equity`.

- Key observed lines from trade.xyz:
  - Unified trade view: `Unified Account Value`.
  - Standard trade view: `Account Value` plus `Spot` and `Perps` rows.

- Hyperliquid API payload evidence:

    curl -sS https://api.hyperliquid.xyz/info -H 'content-type: application/json' \
      --data '{"type":"userAbstraction","user":"0x4096d3377ae5ade578daae8188804740c8b1da3e"}'
    # => "unifiedAccount"

    curl -sS https://api.hyperliquid.xyz/info -H 'content-type: application/json' \
      --data '{"type":"userAbstraction","user":"0x2ba553d9f990a3b66b03b2dc0d030dfc1c061036"}'
    # => "dexAbstraction"

- Issue tracking artifacts:
  - Epic: `hyperopen-fq5`
  - Tasks: `hyperopen-fq5.1`, `hyperopen-fq5.2`, `hyperopen-fq5.3`, `hyperopen-fq5.4`

## Interfaces and Dependencies

Interfaces that must remain stable:

- `request-user-abstraction!` endpoint call shape in `/hyperopen/src/hyperopen/api/endpoints/account.cljs` must remain:

    {"type" "userAbstraction", "user" <address>}

- Account snapshot projected into state must retain shape:

    {:mode <keyword>
     :abstraction-raw <string-or-nil>}

Dependencies and contracts touched by this plan:

- Normalization source: `/hyperopen/src/hyperopen/api/endpoints/account.cljs`.
- Projection path: `/hyperopen/src/hyperopen/api/fetch_compat.cljs` and `/hyperopen/src/hyperopen/api/projections.cljs`.
- Mode-sensitive view logic: `/hyperopen/src/hyperopen/views/account_equity_view.cljs`, `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`, `/hyperopen/src/hyperopen/views/account_info/projections/balances.cljs`.
- Effective-address orchestration for Ghost Mode account switching: `/hyperopen/src/hyperopen/account/context.cljs`, `/hyperopen/src/hyperopen/wallet/address_watcher.cljs`, `/hyperopen/src/hyperopen/startup/runtime.cljs`.

Plan revision note: 2026-03-04 23:45Z - Initial plan authored after live parity investigation and epic/task creation; added concrete external evidence, root-cause mapping, and implementation milestones for unified vs standard account-value parity.
