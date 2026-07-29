# Recover DEXHelm Builder Fee Approval After Wallet Restore

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current as work proceeds. It follows `docs/PLANS.md` and `.agents/PLANS.md`.

## Purpose / Big Picture

DEXHelm users who have already approved the configured `0.01%` Builder Fee on Hyperliquid Testnet must see that approval again when a remembered wallet is restored after refresh. The application now reads Hyperliquid's authoritative `maxBuilderFee` during the existing account-address bootstrap. A response of `10` for the configured `10` tenths of a basis point renders `Enabled` in Trading Settings and keeps `0.01% additional builder fee active` on eligible order summaries.

The repair fails closed. Missing, malformed, stale, insufficient, or identity-mismatched data cannot activate the fee or add a Builder Fee payload to an order. The user directly requested this repair on 2026-07-29 after an on-chain Testnet authorization was confirmed as `maxBuilderFee = 10`, but the refreshed DEXHelm page still showed `Review and enable` and omitted the active-fee summary.

## Context References

Public refs:

- Direct user request on 2026-07-29 to repair the DEXHelm Builder Fee authorization display and recovery behavior.

Repo artifacts:

- `src/hyperopen/startup/runtime.cljs` owns the account-address bootstrap that runs once for the initial effective account and again only after that address changes.
- `src/hyperopen/startup/collaborators.cljs` injects runtime dependencies, including Builder Fee effects, into that bootstrap.
- `src/hyperopen/builder_fee/effects.cljs` performs the authoritative `maxBuilderFee` read and stores a request-bound approval snapshot.
- `src/hyperopen/builder_fee/policy.cljs` determines when an order may include the Builder Fee payload.
- `src/hyperopen/views/header/vm.cljs` projects the Builder Fee control in Trading Settings.
- `test/hyperopen/startup/builder_fee_recovery_test.cljs`, `test/hyperopen/views/builder_fee_disclosure_acceptance_test.cljs`, and `tools/playwright/test/builder-fee.spec.mjs` are the completed regression surfaces.

Local scratch refs (non-authoritative): none.

## Scope and Non-Goals

The implemented scope is the recovery refresh, one shared authorization predicate, the visible enabled state, and regression coverage. It supports the configured `:mainnet` and `:testnet` network identity values; the reported incident is Testnet.

This change does not alter wallet persistence, provider selection, `eth_accounts` restoration behavior, the normal interactive connected handler, agent-trading enablement, Builder address, fee rate, Hyperliquid signing format, or order eligibility rules. It does not persist approval data and does not submit a real Testnet or Mainnet order.

## Progress

- [x] (2026-07-29 14:00+08:00) Captured the direct user request and reproduced the distinction between on-chain Testnet `maxBuilderFee = 10` and the stale `Review and enable` page state.
- [x] (2026-07-29 14:00+08:00) Traced the missing refresh to the account bootstrap path: Builder Fee refresh existed in `load-user-data!`, but no refresh occurred on restored-address bootstrap.
- [x] (2026-07-29 14:35+08:00) Wrote and ran the RED contract. It reported 5 failures and 0 errors before the production change.
- [x] (2026-07-29 14:35+08:00) Implemented the minimal refresh injection: `startup/runtime.cljs` calls `refresh-builder-fee-approval!` once after initial or changed account-address bootstrap; `startup/collaborators.cljs` supplies the production effect.
- [x] (2026-07-29 14:35+08:00) Reused the shared `policy/approved?` predicate in the approval effect and settings view; ready matching approval now renders a disabled `Enabled` control, while invalid/stale/insufficient approval remains reviewable.
- [x] (2026-07-29 14:35+08:00) Added startup and settings regression coverage and updated Builder Fee Playwright coverage. The focused Playwright scenario passed repeatedly at 375px and 1280px, and the final full Builder Fee file passed all 4 tests.
- [x] (2026-07-29 15:35+08:00) Completed final review and governed QA accounting. All six live visual-review passes are explicitly `BLOCKED` because no inspectable session contained the authorized wallet and the managed local app could not be made reachable; the deterministic Playwright checks remain green.
- [x] (2026-07-29 15:40+08:00) Ran the complete repository gate matrix with local-loopback permission: 35/35 gates passed, including 5861 ClojureScript tests with 32574 assertions and 561 websocket tests with 3184 assertions. Cleaned browser sessions and moved this plan to completed.

## Surprises & Discoveries

- Observation: passive provider restoration intentionally does not invoke the ordinary interactive connection callback.
  Evidence: `src/hyperopen/wallet/core.cljs` restores with `set-connected!` without `:notify-connected? true`, and `test/hyperopen/wallet/core_test.cljs` preserves that contract.

- Observation: account-address bootstrap already supplies the exact lifecycle needed for recovery: it runs after address watchers are installed, handles the initial effective address, and is deduplicated by `:bootstrapped-address`.
  Evidence: `src/hyperopen/startup/runtime.cljs` lines 381-416 and 422-466. The implemented refresh is guarded by that same initial-or-changed-address condition.

- Observation: `[:header-ui :builder-fee-review :status]` represents a local review interaction, not durable evidence of exchange authorization.
  Evidence: the prior `builder-fee-section` used `:reviewing` to select `Confirm and enable` and rendered `Review and enable` for every other state, including a valid ready snapshot.

- Observation: a pre-existing policy predicate already encoded the identity and threshold rules but was private and omitted a defensive integer check for the configured fee argument.
  Evidence: `src/hyperopen/builder_fee/policy.cljs` now exports `approved?`, verifies an integer configured threshold, and is shared by order policy, approval confirmation, and the header view.

- Observation: the live market projection could replace a seeded spot market between separate Playwright calls, producing a nondeterministic order submission.
  Evidence: the stable test uses an isolated synthetic `PWSPOT` market and atomically reapplies that market immediately before dispatching the spot order. The focused scenario passed three consecutive runs and the final file passed all 4 tests.

- Observation: adding the startup dependency and view logic directly to their existing namespaces exceeded the governed namespace-size budget.
  Evidence: the UI derivation moved to `src/hyperopen/builder_fee/settings_state.cljs`, the startup regression moved to its own test namespace, and `npm run lint:namespace-sizes` passes.

- Observation: governed live browser review could not observe the authorized connected-wallet state.
  Evidence: the only attachable tab was disconnected and reconnecting, while three managed local-app attempts could not make the inspection target reachable. `tmp/browser-inspection/inspect-2026-07-29T05-58-28-548Z-679b76f2/browser-report.json` records all six required passes as `BLOCKED` and the session cleanup as `PASS`.

## Decision Log

- Decision: trigger the refresh in the existing account-address bootstrap instead of adding a wallet-restored callback or changing the ordinary connected handler.
  Rationale: the bootstrap receives both a restored initial address and later address changes, has an existing once-per-address guard, and keeps passive restoration separate from interactive wallet, attribution, and agent-trading semantics.
  Date/Author: 2026-07-29 / Codex.

- Decision: inject `refresh-builder-fee-approval!` through `startup/collaborators.cljs` and invoke it as an interpreter-owned effect from `bootstrap-account-data!`.
  Rationale: this keeps the startup runtime testable and preserves the repository rule that network I/O lives at infrastructure boundaries rather than in a pure address watcher.
  Date/Author: 2026-07-29 / Codex.

- Decision: promote `builder-fee.policy/approved?` and reuse it in the effect confirmation path and header view.
  Rationale: one definition ensures the visible `Enabled` state cannot disagree with the order policy on owner, Builder recipient, network, maximum fee, or configured threshold.
  Date/Author: 2026-07-29 / Codex.

- Decision: display `Enabled` using the existing Builder Fee button row, disabled with no action.
  Rationale: this is the smallest compatible view-model change. It gives users a clear observed state while preventing another approval action when the current authoritative snapshot already satisfies the configured fee.
  Date/Author: 2026-07-29 / Codex.

- Decision: keep the Playwright spot assertion isolated from provider market data with `PWSPOT`, and force the market plus submission in one browser task.
  Rationale: the assertion concerns Builder Fee eligibility, not production market discovery. Atomic setup prevents live projections from changing the active market between setup and dispatch while preserving the strict no-Builder-payload assertion for spot buys.
  Date/Author: 2026-07-29 / Codex.

- Decision: complete the plan with governed live browser QA marked `BLOCKED` rather than retrying an unavailable inspection environment indefinitely.
  Rationale: the artifact identifies the missing authorized CDP session and records all six passes individually. Deterministic CLJS and two-viewport Playwright coverage validates the changed behavior without claiming a live-wallet visual result.
  Date/Author: 2026-07-29 / Codex.

## Outcomes & Retrospective

The implemented solution is narrower than the original callback-based proposal. It adds one injected startup dependency and one guarded call, while avoiding wallet callback registration, browser-storage changes, and any normal-connect behavioral change. Shared authorization logic removes duplicated threshold checks between the effect and display paths, reducing the risk that the UI claims approval while order policy rejects it.

The final implementation restores the authoritative approval once per initial or changed account address, projects `Enabled` from the same fail-closed predicate used by order policy, and leaves wallet restoration, storage, signing, and order eligibility contracts unchanged. The focused Builder Fee Playwright scenario passed repeatedly at both committed viewports, the final Builder Fee file passed all 4 tests, and the repository gate matrix passed 35/35.

The remaining limitation is environmental rather than an open implementation task: live governed visual QA could not reach an authorized connected-wallet browser session. Its six passes are explicitly recorded as `BLOCKED` with artifacts and a deterministic post-fix reproduction recipe. No deployment or real order was performed.

## Context and Orientation

Hyperliquid's `maxBuilderFee` is an integer in tenths of a basis point. The DEXHelm configuration uses `10`, representing `0.01%`. Approval is current only if the stored snapshot has `:status :ready`, matches the current wallet owner, configured Builder address, and selected Hyperliquid network, and its integer `:max-builder-fee` is at least the configured integer threshold. Approval snapshots are transient at `[:builder-fee :approval]`; they are never restored from browser storage.

`startup/runtime/bootstrap-account-data!` receives an effective account address from the address watcher. It skips a nil address and does not repeat work for `:bootstrapped-address`; a newly restored address or a different wallet address enters the body. The implementation invokes `refresh-builder-fee-approval!` immediately after resetting account-surface state and before account-history and surface fetches. `startup/collaborators/startup-base-deps` supplies the concrete effect from `builder-fee.effects`, so the startup runtime remains dependency-injected in tests.

`builder-fee.effects/refresh-builder-fee-approval!` begins a request-bound loading snapshot, calls the Hyperliquid `maxBuilderFee` endpoint, and applies only a current identity and request ID. Errors and invalid responses become `:unapproved`. `builder-fee.policy/approved?` is the shared pure check. `policy-decision` still additionally verifies configured Builder Fee validity, target-owner equality, order shape, and market eligibility before appending `{:builder {:b <address> :f <tenths-bp>}}`.

## Implemented Work

In `src/hyperopen/startup/runtime.cljs`, `bootstrap-account-data!` accepts the injected `refresh-builder-fee-approval!` dependency and calls it with `nil` context and the runtime store exactly when it begins the bootstrap for an address not equal to the recorded `:bootstrapped-address`. Thus a restored address refreshes authorization, repeated bootstrap for the same address does not duplicate it, a nil address performs no refresh, and a changed address initiates a new refresh.

In `src/hyperopen/startup/collaborators.cljs`, `startup-base-deps` injects `builder-fee-effects/refresh-builder-fee-approval!`. No wallet module, connected handler, action adapter, storage API, or new callback was changed.

In `src/hyperopen/builder_fee/policy.cljs`, `approved?` is public and requires an integer configured threshold. `src/hyperopen/builder_fee/effects.cljs` calls it when deciding whether the signed approval was confirmed. `src/hyperopen/views/header/vm.cljs` calls the same predicate with the configured builder fee, current owner, and selected Hyperliquid network. It renders `Enabled`, disables the row, and removes its action only for a valid current approval; all mismatches, insufficient values, and stale loading states continue to render `Review and enable`.

Regression coverage was updated as follows:

- `test/hyperopen/startup/builder_fee_recovery_test.cljs` proves no refresh for nil, one refresh on first address bootstrap, no duplicate for a repeated address, and a second refresh after an address change. Its rejecting refresh stub also proves startup consumes the already-recorded failure instead of producing an unhandled rejection.
- `test/hyperopen/views/builder_fee_disclosure_acceptance_test.cljs` proves `Enabled` only for matching current owner, Builder, Testnet, and maximum fee. It proves different owner, Builder, network, insufficient maximum fee, and loading state remain reviewable while preserving the fee disclosure and active-summary contract.
- `tools/playwright/test/builder-fee.spec.mjs` asserts the successful approval flow exposes a disabled `Enabled` control at both committed viewport widths before the eligible order assertion runs.

## Concrete Steps

All commands run from `/Users/zh/Documents/Hyperopen`.

Completed evidence:

    npm test
    # 5861 tests, 32574 assertions, 0 failures, 0 errors

    npx playwright test tools/playwright/test/builder-fee.spec.mjs
    # PASS: 4 tests, covering Builder Fee scenarios at 375px and 1280px

    npm run gates
    # PASS: 35/35 gates; 6615 tests; 35970 assertions

    npm run browser:cleanup
    # PASS: no managed browser sessions remained

Governed live browser QA was attempted against the existing browser session and three times with managed local-app startup. It is `BLOCKED`, not failed: no inspectable browser contained the authorized wallet and the managed inspection app did not become reachable. The report accounts for visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf individually.

## Validation and Acceptance

1. When the account-address bootstrap first sees a restored configured DEXHelm Testnet wallet, it invokes the injected Builder Fee refresh exactly once. It invokes it again only when the effective account changes, never for nil or the same address.

2. When the authoritative response for the current owner, configured Builder, and Testnet is integer `10`, Trading Settings visibly shows a disabled `Enabled` control instead of `Review and enable`. The displayed `0.01%` disclosure remains separate from exchange maker and taker fees.

3. An eligible perp order after valid approval visibly includes `0.01% additional builder fee active` and its signed action contains the configured Builder payload `{b: builder-address, f: 10}`. Existing spot-buy behavior remains without the Builder payload.

4. Request failure, malformed or stale response, insufficient fee, owner mismatch, Builder mismatch, or network mismatch never expose `Enabled`, never show an active Builder Fee summary, and never authorize the Builder payload.

5. The full ClojureScript suite remains green at 5861 tests and 32574 assertions, the repeated focused Builder Fee Playwright suite passes at 375px and 1280px, review finds no blocking regression, governed browser QA records its explicit environment blocker, and `npm run gates` passes 35/35.

## Idempotence and Recovery

The new refresh is idempotent for a stable address because the existing account bootstrap guard prevents repeat calls, and the Builder Fee effect deduplicates a same-identity loading or ready request. On endpoint error or invalid data, the effect replaces the approval snapshot with `:unapproved`; the user may use the existing review flow to retry. A changed account receives a fresh snapshot request, and an old response cannot overwrite the new identity because the existing request-id and identity guards reject it. No real orders or signatures are needed for validation.

## Artifacts and Notes

The committed browser test remains `tools/playwright/test/builder-fee.spec.mjs`. Governed browser-QA evidence is in `tmp/browser-inspection/inspect-2026-07-29T05-58-28-548Z-679b76f2/browser-report.json`, with its disconnected-state screenshot and snapshot in the same run directory. The report contains no wallet secrets or signatures. The green deterministic evidence is local implementation evidence, not a claim of a live Testnet trade.

## Interfaces and Dependencies

No third-party dependency was added. The only startup contract addition is optional dependency key `:refresh-builder-fee-approval!` supplied by `startup/collaborators` and consumed by `startup/runtime/bootstrap-account-data!` with `(nil store)`. The wallet callback signatures, browser storage schema, public actions, and order action shape are unchanged. Existing `nexus.registry` dispatch and Hyperliquid request boundaries remain the owners of side effects.

Revision note: created on 2026-07-29 to capture the direct user request and the diagnosed gap between authoritative Hyperliquid Builder Fee approval, passive wallet restoration, and the Trading Settings display.

Revision note: updated on 2026-07-29 after implementation to replace the proposed wallet-callback design with the completed address-bootstrap refresh, record the completed source and test changes, and preserve final review, QA, and gate work as active.

Revision note: completed on 2026-07-29 after repeated two-viewport Playwright validation, explicit six-pass browser-QA blocker accounting, a clean browser-session cleanup, final diff review, and a 35/35 repository gate result.
