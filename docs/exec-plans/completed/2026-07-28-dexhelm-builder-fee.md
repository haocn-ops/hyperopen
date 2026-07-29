# Safely Enable DEXHelm Hyperliquid Builder Fees

This ExecPlan is a living document. Maintain its `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` sections as implementation proceeds. It follows `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

DEXHelm currently sends ordinary Hyperliquid orders and collects no builder fee. After this work, a release that contains a preflighted DEXHelm builder address will let a connected main wallet explicitly review and sign an onchain approval for one disclosed fee. Only after Hyperliquid confirms that approval may eligible orders contain the builder code. A missing, malformed, stale, unavailable, or unapproved configuration must produce the same order action as today, with no additional fee.

The current DEXHelm deployment is testnet. This change configures the supplied Testnet builder only after its activation preflight passes. A future mainnet deployment is a separate release decision; testnet configuration alone cannot create mainnet revenue.

## Context References

Public context:

- Direct maintainer request on 2026-07-28: `开通builder fee吧`, after confirming that the current DEXHelm release receives no fee. No GitHub issue or PR exists yet.
- Hyperliquid Builder codes documentation, retrieved 2026-07-28: `https://hyperliquid.gitbook.io/hyperliquid-docs/trading/builder-codes.md`.
- Hyperliquid exchange endpoint documentation, retrieved 2026-07-28: `https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/exchange-endpoint.md`.
- Hyperliquid account abstraction documentation, retrieved 2026-07-28: `https://hyperliquid.gitbook.io/hyperliquid-docs/trading/account-abstraction-modes.md`.

Repository context:

- `/hyperopen/config/white-label/dexhelm.json`
- `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs`
- `/hyperopen/src/hyperopen/api/trading/user_actions.cljs`
- `/hyperopen/src/hyperopen/utils/hl_signing.cljs`
- `/hyperopen/docs/BROWSER_TESTING.md` and `/hyperopen/docs/FRONTEND.md`

Local scratch references (non-authoritative): none.

## Progress

- [x] (2026-07-28 12:39Z) Read the feature-flow/spec-writer contracts, planning rules, work-tracking rules, DEXHelm config, order construction/submission path, wallet-signing path, and governed UI/browser rules.
- [x] (2026-07-28 12:39Z) Verified the official builder-code requirements and froze the no-silent-charge, explicit-consent design.
- [x] (2026-07-28 12:39Z) Created this active ExecPlan and recorded the missing activation inputs.
- [x] (2026-07-28 13:02Z) Received builder address `0x36a47878219fb346e031f6cf82cbfc8c77e35932`; selected the recommended Testnet-first 1 bp activation and verified Testnet returned raw abstraction `default` with perps account value `976.634613` USDC.
- [x] (2026-07-28 13:08Z) Acceptance and edge-case writers produced schema-valid, non-overlapping proposal artifacts under `tmp/multi-agent/dexhelm-builder-fee/`.
- [x] (2026-07-28 13:12Z) Frozen the merged approved test contract at `tmp/multi-agent/dexhelm-builder-fee/approved-test-contract.json` with seven acceptance cases and six complementary edge cases.
- [x] (2026-07-28 13:48Z) Materialized the approved RED-phase Node, ClojureScript, and Playwright test contracts without changing production files. The focused Node run fails for the intended missing builder-fee config and preflight behavior.
- [x] (2026-07-28 13:48Z) Ran `npm run lint:delimiters`; the new ClojureScript test files pass static delimiter validation.
- [x] (2026-07-28 16:26Z) Closed the static-review implementation findings: runtime builder-fee consumers now use the strict tenant boundary, rejected approval effects leave the review state terminal, and approval request IDs remain unique within one millisecond. `shadow-cljs compile test` and the compiled test runner pass with the configured JDK 21/localStorage harness.
- [x] (2026-07-28 17:58Z) Restored the exact `scheduleCancel` simulator lookup and made the deferred approval-refresh R1/R2 tests direct-link compatible. The rebuilt ClojureScript runner passed 5,858 tests and 32,559 assertions; the websocket runner passed 561 tests and 3,184 assertions; the escalated release-asset suite passed 51 tests.
- [x] (2026-07-28 18:58Z) Completed the configured Testnet activation at 1 bp, normalized checksum-cased wallet identities before approval, passed DEXHelm validation and preflight, passed the 35/35 gate matrix, passed the focused Builder Fee Playwright suite 4/4, and passed all six governed browser-QA checks at 375/768/1280/1440. Cleaned up browser and local-server sessions; no deployment, push, Mainnet switch, or account-mode mutation was performed.
- [x] (2026-07-29 02:00Z) Release review found and fixed a white-label manifest compatibility defect: normalized `builder-fee.max-fee-rate` is now stripped before strict source-config parsing and recomputed for tamper-safe comparison; omitted builder-fee config remains backward-compatible as disabled. Cloudflare artifact build, DEXHelm validation, artifact preflight, Wrangler dry-run, white-label Playwright (375/768/1280/1440), `npm run check`, `npm test`, and `npm run test:websocket` passed. Deployment and GitHub push remain the final external steps.

## Surprises & Discoveries

- Observation: DEXHelm is explicitly testnet and its existing affiliate configuration is unavailable; no builder identity is present in the repository.
  Evidence: `/hyperopen/config/white-label/dexhelm.json` has `"hyperliquid-network": "testnet"`, `features.affiliate: false`, and no builder-fee field.

- Observation: every ordinary order request currently omits a builder object.
  Evidence: `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs` constructs `{type, orders, grouping}` for `order`, while `twapOrder` is a different action type.

- Observation: the approval cannot use the local API-wallet signer.
  Evidence: the official Builder codes page requires a main-wallet `ApproveBuilderFee` action. Existing `/hyperopen/src/hyperopen/api/trading/user_actions.cljs` signs user actions through the connected provider, while agent order submission uses an encrypted local agent key.

- Observation: `maxBuilderFee` returns a numeric tenths-of-a-basis-point value for an unapproved address.
  Evidence: read-only 2026-07-28 calls to both `/info` endpoints with zero user and builder addresses returned `0`.

- Observation: the supplied builder address passes the documented Testnet operator prerequisites.
  Evidence: read-only Testnet `/info` requests returned `"default"` for `userAbstraction` and `976.634613` for `clearinghouseState.marginSummary.accountValue`.

- Observation: the local Shadow CLJS compiler cannot start in this worktree because no Java Runtime is installed.
  Evidence: `npx shadow-cljs --force-spawn compile test` exited before compilation with `Unable to locate a Java Runtime`; this is an environment blocker, distinct from the intentional Node RED failures.

- Observation: `Date.now` can return the same value for consecutive approval requests, so timestamp-only request IDs cannot safely distinguish an R1/R2 race.
  Evidence: `/hyperopen/src/hyperopen/builder_fee/effects.cljs` now advances beyond the current pending request ID when necessary.

- Observation: direct-linked ClojureScript test builds cannot reliably replace the API facade var used by the approval refresh effect.
  Evidence: the deferred R1/R2 tests raised an arity error after `with-redefs`/`set!`; supplying the request function through the effect's optional test facade preserves the production two-argument API.

- Observation: wallet providers may preserve checksum casing in `:wallet :address`, while the account-context identity used for approval is lowercase.
  Evidence: the final static pass found that comparing the raw review address to the normalized approval identity could reject the same account as changed; header actions and the approval effect now normalize both sides, with a mixed-case regression test.

## Decision Log

- Decision: introduce a dedicated public `builder-fee` tenant configuration object; do not reuse `affiliate` configuration or affiliate-consent state.
  Rationale: a builder fee changes the amount charged on a fill and requires an onchain user approval. The existing affiliate feature is optional redacted attribution and must not become a hidden billing control.
  Date/Author: 2026-07-28 / Codex

- Decision: recommend an initial fee of 1 bp, represented by `fee-tenths-bp: 10` and the derived Hyperliquid approval string `0.01%`; do not put that value or any address into DEXHelm until the operator explicitly supplies them.
  Rationale: `f` is in tenths of a basis point, so `10` is one basis point. It is materially below Hyperliquid's 0.1% perp cap, but an invented recipient or a silent production activation would be unsafe.
  Date/Author: 2026-07-28 / Codex

- Decision: derive `maxFeeRate` exactly from `fee-tenths-bp` rather than accepting a second independently editable fee field.
  Rationale: the signed maximum must equal the configured charge, preventing an approval for a higher amount than this release will send.
  Date/Author: 2026-07-28 / Codex

- Decision: approval state is onchain and fetched from `maxBuilderFee`; it is never persisted as a local consent flag. A failed or unknown read fails closed.
  Rationale: wallet changes, network changes, external revocation, and tenant rebuilds must not leave an old browser preference able to charge a fee.
  Date/Author: 2026-07-28 / Codex

- Decision: v1 applies only to main-account `order` actions for perps and spot sells. It excludes spot buys, outcome markets, `twapOrder`, subaccounts, vaults, and any action whose target cannot be proved to be the connected owner.
  Rationale: official documentation says fees apply to both perp sides and only the quote/collateral side of spot; it documents the optional builder object on `order`, not `twapOrder`. Main-wallet approval semantics for delegated targets require separate protocol verification before they can be charged.
  Date/Author: 2026-07-28 / Codex

- Decision: do not change account-abstraction mode in product code. The operator builder account, not the end user, must be Standard; the release preflight rejects `unifiedAccount`, `portfolioMargin`, `dexAbstraction`, and unknown responses.
  Rationale: an account-mode change has collateral and operational consequences. It is not a prerequisite the application may silently mutate.
  Date/Author: 2026-07-28 / Codex

- Decision: configure DEXHelm Testnet for builder address `0x36a47878219fb346e031f6cf82cbfc8c77e35932` at 1 bp after the feature and preflight gates pass; do not switch the Mainnet hostname or release network in this change.
  Rationale: the maintainer supplied the recipient after the 1 bp/Testnet-first recommendation, and the address passed the current Testnet protocol prerequisites. Mainnet revenue still requires a separate explicit deployment decision and repeated Mainnet preflight.
  Date/Author: 2026-07-28 / Codex

- Decision: all runtime builder-fee entry points must consume `active-builder-fee-config` rather than raw tenant state.
  Rationale: strict validation must apply before the review control is rendered, a nonce is allocated, or wallet signing begins.
  Date/Author: 2026-07-28 / Codex

- Decision: preserve the `scheduleCancel` debug simulator path as `[[:signedActions "scheduleCancel"] [:signedActions :default]]`; use keyword-first lookup with a legacy string fallback only for ordinary action types.
  Rationale: the schedule-cancel seam is an exact established contract, while normal simulator configuration must support both parsed JavaScript keyword maps and existing Clojure test maps.
  Date/Author: 2026-07-28 / Codex

- Decision: inject `request-max-builder-fee!` through an optional third effect argument in deferred tests instead of replacing the direct-linked API var.
  Rationale: it confines test substitution to the infrastructure boundary and keeps runtime callers and the public two-argument effect interface unchanged.
  Date/Author: 2026-07-28 / Codex

- Decision: ratchet only the exact namespace-size ceilings reached by this feature, with dated retirement reasons; do not raise the global 500-line threshold.
  Rationale: the Builder Fee wiring adds small boundary adapters to existing oversized owners, while splitting unrelated signing, trading, and optimizer domains would materially expand this activation task.
  Date/Author: 2026-07-28 / Codex

## Outcomes & Retrospective

DEXHelm now has a strict public Builder Fee configuration for `0x36a47878219fb346e031f6cf82cbfc8c77e35932` on Testnet at 1 bp (`f=10`). Users must explicitly review and sign `approveBuilderFee`; the application then refreshes Hyperliquid's authoritative `maxBuilderFee` value before eligible main-account orders can carry the builder object. Missing, malformed, stale, insufficient, or failed approval state remains fee-free.

The implementation covers ordinary perp orders on both sides and spot sells across the trade ticket, position reduction, position TP/SL, and optimizer execution. Spot buys, outcomes, TWAP, subaccounts, and vaults remain excluded. Runtime config validation, signing, approval refresh, order field ordering, simulator compatibility, stale R1/R2 responses, checksum-cased wallet identities, disclosure UI, and release artifact secret checks all have regression coverage.

Validation completed with DEXHelm config validation, a live read-only Testnet preflight reporting Standard mode and account value at least 100 USDC, the 35-gate matrix (6,613 tests and 35,957 assertions; the release-asset gate was rerun with local-listener permission after the sandbox-only failure), the focused Builder Fee Playwright suite at 4/4, the white-label release suite at 4/4 across 375, 768, 1280, and 1440, `npm run check`, `npm test`, and `npm run test:websocket`. The Cloudflare artifact preflight passed 33 checks with only JDK/localStorage environment warnings, and Wrangler dry-run exited 0 while reading 57 assets. Deployment and remote Git publication are authorized next; Mainnet remains closed.

## Context and Orientation

Hyperliquid builder codes are optional objects on an order action: `{"b": builder-address, "f": fee-in-tenths-of-a-basis-point}`. Hyperliquid requires the customer to sign `approveBuilderFee` with their main wallet first. The signed action includes `hyperliquidChain`, `maxFeeRate`, `builder`, and `nonce`; its EIP-712 primary type is `HyperliquidTransaction:ApproveBuilderFee`. The outer exchange nonce and action nonce must match. Later order actions are signed by the customer's API/agent wallet, not by the main wallet.

The builder itself must hold at least 100 USDC of perps account value and use Standard account mode to accrue fees. Each customer can have at most ten active builder approvals. The configured 1 bp charge is eligible on both perp sides and spot sells; spot buys do not pay it. Perp builder fees may not exceed 0.1%, so this plan restricts all configured fees to `1..100` tenths of a basis point. This conservative common ceiling also remains valid for spot, whose documented cap is 1%.

`/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs` builds the signed wire actions. Main ticket requests flow through `/hyperopen/src/hyperopen/state/trading.cljs`; position reductions use `/hyperopen/src/hyperopen/account/history/position_reduce.cljs`; position TP/SL uses `/hyperopen/src/hyperopen/account/history/position_tpsl_application.cljs`; and optimizer execution uses `/hyperopen/src/hyperopen/portfolio/optimizer/application/execution.cljs`. These are the complete current direct callers of `build-order-request` or `build-tpsl-orders` and must all use the same policy before v1 claims main-account coverage.

## Frozen Scope

The implementation adds a public, strict configuration object with these two valid shapes:

    "builder-fee": {
      "status": "disabled",
      "builder-address": null,
      "fee-tenths-bp": null,
      "disclosure": "No DEXHelm builder fee is active in this release."
    }

    "builder-fee": {
      "status": "configured",
      "builder-address": "0x<40 lowercase hexadecimal characters>",
      "fee-tenths-bp": 10,
      "disclosure": "DEXHelm charges an additional 0.01% builder fee on eligible fills after you approve it."
    }

The parser accepts no unknown fields. A configured address must be normalized lowercase and exactly 42 characters. A disabled object must contain no latent recipient or fee. The value is public configuration, never a secret; existing secret scanning remains in force. `maxFeeRate` is rendered deterministically from `f` as `f * 0.001%`, so `10` renders as `0.01%` without a floating-point string.

The application will expose a Builder fee section only for a configured tenant. It explains the recipient, the exact additional percentage, market eligibility, and that the customer must sign an onchain permission. `Review and enable` opens a confirmation surface before the wallet prompt. Success means the exchange responded successfully and a refreshed `maxBuilderFee` value is at least `f`; cancellation, rejection, timeout, error, stale owner, missing config, or an unavailable query leaves the fee inactive. The visible order ticket and every other in-scope submit review state show the same fee note when active. There is no localStorage permission flag and no automatic wallet prompt.

The pure policy receives public config, connected owner, current submission target, approval snapshot, market type, side, and action. It returns either the original request byte-for-byte or an `array-map` order action with the root `:builder` appended after `:grouping`. It never changes an individual order struct, `:cloid`, pre-actions, cancellations, or `twapOrder`. The builder object is `{ :b lowercase-address, :f integer }`. Signing tests must pin the action's resulting connection-id so accidental field-order changes cannot invalidate it.

## Explicit Non-Goals

This work does not invent a builder address, switch DEXHelm to mainnet, make an account-mode change, collect referral rewards, download builder-fill CSVs, create affiliate analytics, change exchange fees, or implement a generalized billing system. It does not charge subaccount/vault, outcome, spot-buy, or TWAP orders. It also does not claim an in-product revocation mechanism without an official, tested exchange action; the surface must state that onchain approval is externally revocable and link to the official Builder codes documentation until that separate workflow is specified and tested.

## Plan of Work

First, extend the matching public config validators in `/hyperopen/tools/white-label/tenant_config.mjs`, `/hyperopen/src/hyperopen/service/tenant_config.cljs`, and `/hyperopen/src/hyperopen/config.cljs`. Add matching fixtures and tests, then add the disabled object to `/hyperopen/config/white-label/dexhelm.json`. Do not change it to `configured` in this step. Add a read-only `builder-fee:preflight` command that reads the configured builder's `userAbstraction` and `clearinghouseState` from the selected network, rejects non-Standard raw modes and account values below 100, and reports only address, network, mode, and threshold result. It must never print secrets or sign anything.

Next, add `/hyperopen/src/hyperopen/builder_fee/policy.cljs` as the one pure eligibility and request-decoration owner, with unit tests in `/hyperopen/test/hyperopen/builder_fee/policy_test.cljs`. Thread its explicit decision result into each of the four order-construction callers named above. This keeps every no-fee path unchanged and makes optimizer/position flows intentionally covered instead of accidental. It must reject a non-owner target before attaching any builder data.

Add a small account endpoint path for `{"type":"maxBuilderFee","user":"0x...","builder":"0x..."}`, plus a stale-owner-safe projection and effect that refreshes on owner/config change and after approval. It must normalize only a finite nonnegative integer. The UI reads its state but the pure order policy treats loading, error, and stale snapshots as unapproved. Use existing account endpoint, gateway, API facade, effect-adapter, and projection patterns rather than making a direct view fetch.

Add `approveBuilderFee` support beside existing user-signed actions. Update `/hyperopen/src/hyperopen/utils/hl_signing.cljs` with the exact four EIP-712 fields and primary type, export it through `/hyperopen/src/hyperopen/trading_crypto/module.cljs` and `/hyperopen/src/hyperopen/trading_crypto_modules.cljs`, then call it from `/hyperopen/src/hyperopen/api/trading/user_actions.cljs` and the public trading facade. The action builder must use the connected owner address, selected release chain fields, a fresh user-action nonce, the derived exact rate, and the configured builder address. It must not call `sign-l1-action-with-private-key!`, target a subaccount, or execute when trading/network preconditions fail.

Add UI state and effects using the existing header-settings and order-confirmation patterns. `/hyperopen/src/hyperopen/views/header/vm.cljs`, `/hyperopen/src/hyperopen/header/actions.cljs`, and their action/effect registration own the review, in-flight, success, rejection, and retry states. `/hyperopen/src/hyperopen/views/trade/order_form_footer.cljs` and the associated summary presenter display the extra active builder fee separately from exchange maker/taker fees; do not fold it into a protocol fee rate. The in-scope position and optimizer confirmation surfaces receive the same concise fee notice. These are UI-facing interaction changes, so add stable `data-role` anchors, use a single projection before heavy I/O, prevent duplicate submissions, and keep wallet-provider UI as the only permitted manual exception.

Before changing DEXHelm to `configured`, the operator runs the preflight on the intended testnet or mainnet config and records a passing result. The operator then supplies the actual address, confirms the exact 1 bp rate or explicitly selects another allowed rate, approves the disclosure wording, and decides whether a separately reviewed mainnet release is authorized. A failed preflight, absent address, or missing approval leaves the release disabled; it does not block generic implementation or test fixtures.

## Concrete Steps

From `/hyperopen`, first run:

    npm run setup:worktree

The test writers propose contracts only. After the parent freezes them, `tdd_test_writer` materializes RED tests. Run the narrow tests that own the changed behavior, including the config Node tests, builder-fee policy tests, order-command tests, user-action/typed-data tests, endpoint/projection/effect tests, and header/order-summary view tests. The intended RED failure is a missing builder-fee policy/config/signer path, not an unrelated compile error.

For activation, after the operator has supplied the non-secret public address:

    npm run white-label:validate -- --config config/white-label/dexhelm.json --origin https://testnet.dexhelm.com
    npm run builder-fee:preflight -- --config config/white-label/dexhelm.json

The validator must report a normalized DEXHelm config. The preflight must report the selected network, Standard mode, and perps account value at least 100; any other output is a release blocker. A mainnet activation repeats these commands against the mainnet DEXHelm config and separately follows the governed deployment workflow.

For deterministic browser coverage, run the smallest new Playwright spec first, then governed UI QA and the required repository gates:

    npx playwright test tools/playwright/test/builder-fee.spec.mjs
    npm run qa:design-ui -- --targets trade-route --manage-local-app
    npm run browser:cleanup
    npm run gates

## Validation and Acceptance

1. A disabled DEXHelm config validates, renders no builder-fee consent control, and produces byte-for-byte identical order actions and L1 connection IDs to the current formal vectors.

2. A synthetic configured config accepts only a lowercase 42-character address and an integer `f` from 1 through 100. Unknown config fields, an uppercase/non-address recipient, zero/decimal/out-of-cap fee, mismatched disabled fields, and secret-shaped input cause validation to fail before a build or wallet prompt.

3. Pressing `Review and enable` with a connected main wallet shows the recipient, exact 1 bp rate, eligible-market wording, and an explicit confirm command. Confirming emits exactly one `approveBuilderFee` exchange request whose action and outer nonce match, has `hyperliquidChain` and `signatureChainId` from the selected release, and uses EIP-712 primary type `HyperliquidTransaction:ApproveBuilderFee`. A signer or network error leaves no active fee state.

4. An info request for a connected owner and configured builder sends exactly `maxBuilderFee`. No builder object is attached while this request is loading, fails, belongs to a different owner, or returns less than `f`. After it returns `>= f`, a main-account perp or spot-sell `order` action has exactly `:builder {:b <lowercase-address> :f 10}` at the root. Spot buys, outcomes, TWAP, subaccount/vault targets, and unapproved orders omit it.

5. The builder object survives the real agent signing/post path and the debug exchange simulator captures it; the approval itself is captured as a user-signed action. No private key, signature, or credential appears in tenant JSON, logs, browser storage, release artifacts, or test output.

6. Deterministic Playwright proves the configured review flow, declined/error states, successful simulated approval, active fee note, and an eligible versus ineligible order payload at stable selectors. Browser QA records PASS, FAIL, or BLOCKED for visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf at 375, 768, 1280, and 1440. Clean up Browser MCP sessions with `npm run browser:cleanup`.

7. `npm run check`, `npm test`, and `npm run test:websocket` pass, preferably through `npm run gates`. Any environment-only missing-Java or missing-node-modules failure is recorded distinctly from a feature failure.

This is not performance-motivated work. No performance optimization or more complex algorithm is proposed; the new read occurs at bounded identity/config transitions and post-approval, never in the websocket decision path or render loop.

## Idempotence and Recovery

All parsers and request decorators are pure and repeatable. Reopening the review before the prior request completes must not issue a second approval. A rejected wallet request, exchange rejection, stale response, or reload returns to the inactive state and leaves future orders unchanged. Configuration validation and preflight are read-only. To stop collection after deployment, publish a validated disabled configuration; this changes future order decoration only and cannot revoke an approval already recorded onchain. Do not claim that deployment rollback revokes an existing customer authorization.

## Interfaces and Dependencies

The stable pure interface should be equivalent to:

    builder-fee/policy-decision
      config approval owner-address target-address market action side
      => {:active? boolean :reason keyword :action order-action}

`approval` is the latest numeric `maxBuilderFee` response for the same lowercase owner, network, and configured builder. `target-address` must equal the connected owner for v1. The only active result appends a root builder map to an already valid `order` action. Every other result returns the original action unchanged.

The user-action interface should be equivalent to:

    trading/approve-builder-fee! store owner-address
      {:type "approveBuilderFee" :builder address :maxFeeRate "0.01%"}

It adds chain fields and nonce internally, signs through the connected wallet provider, and posts to the currently selected Hyperliquid exchange endpoint. It must preserve the existing public APIs and agent-order signing behavior.

## Artifacts and Notes

The official Python SDK example passes `builder={"b": address, "f": 1}` to an order after `approve_builder_fee(builder, "0.001%")`. This plan uses the same units but recommends `f = 10` for a 1 bp DEXHelm rate. The Python SDK lowercases the builder address before building the action; Hyperopen's public config and pure policy must do the same before signing.

Plan revision note (2026-07-28): created from the direct DEXHelm builder-fee request. It freezes the implementation and consent boundary while recording the missing real builder address as an activation prerequisite rather than inventing billing data.
