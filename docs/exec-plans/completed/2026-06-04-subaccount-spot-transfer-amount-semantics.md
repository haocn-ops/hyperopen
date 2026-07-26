# Harden Subaccount Spot Transfer Amount Semantics

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`.

## Purpose / Big Picture

Subaccount transfers currently treat trading-account USDC amounts and spot-token amounts very differently. Trading USDC is parsed into raw micro-USDC units before the effect is emitted, but spot transfers only check that the string looks like a positive finite number and then pass the raw text and selected token downstream. After this change, every subaccount transfer amount is normalized by a single pure codec that knows the account kind, token, and token precision before any API effect is emitted.

The user-visible result is that the Send Tokens popover rejects invalid spot precision before signing. For example, a token with 2 decimals rejects `0.001`, a token with 6 decimals accepts `2.5` and emits canonical payload metadata, scientific notation such as `1e6` is rejected, and absurdly large inputs cannot slip through JavaScript number coercion.

## Context References

Public refs:

- Direct user request on 2026-06-04: create an execution plan for the finding "Subaccount spot transfers have weak amount semantics."

Repo artifacts:

- `/hyperopen/AGENTS.md` requires code changes to return changed files, commands, validation results, and risks, and requires `npm run check`, `npm test`, and `npm run test:websocket` when code changes.
- `/hyperopen/src/hyperopen/subaccounts/management.cljs` currently parses trading USDC through `parse-usdc-amount->micros` and validates spot amounts through `valid-spot-amount?`.
- `/hyperopen/src/hyperopen/subaccounts/effects.cljs` submits `:account-kind :spot` through `transfer-sub-account-spot!` and unified-account trading transfers through `submit-send-asset!`.
- `/hyperopen/src/hyperopen/api/trading/agent_actions.cljs` sends spot subaccount transfers as signed `subAccountSpotTransfer` actions with string `:token` and string `:amount`.
- `/hyperopen/src/hyperopen/views/account_info/projections/balances.cljs` already shows the available pattern for deriving spot token decimals from token metadata and market metadata.
- `/hyperopen/test/hyperopen/subaccounts/actions_test.cljs` and `/hyperopen/test/hyperopen/subaccounts/effects_test.cljs` cover the existing transfer action and effect payloads.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-06-04 19:22Z) Reviewed the current action, effect, schema, view-model, and test surfaces for subaccount transfers.
- [x] (2026-06-04 19:22Z) Created this active ExecPlan with the proposed codec, payload, and validation approach.
- [x] (2026-06-04 20:03Z) Added RED tests for transfer amount normalization, spot token precision, invalid notation, huge values, missing non-USDC precision metadata, and normalized effect payload contracts.
- [x] (2026-06-04 20:12Z) Implemented the pure transfer amount codec and token metadata resolver in `hyperopen.subaccounts.transfer-amount`.
- [x] (2026-06-04 20:18Z) Wired the codec into `submit-transfer-subaccount`, tightened transfer effect args, and preserved effect/API compatibility.
- [x] (2026-06-04 20:32Z) Ran focused and full validation: `npm test`, `npm run check`, and `npm run test:websocket` all pass on the final patch.
- [x] (2026-06-04 20:45Z) Addressed reviewer feedback by making transfer effect specs recompute decimal units and reject mismatched or noncanonical normalized payloads.

## Surprises & Discoveries

- Observation: Unified accounts already force the selected transfer account back to `:trading` in `effective-transfer-account`, even if the form field still says `:spot`.
  Evidence: `src/hyperopen/subaccounts/management.cljs` lines 84-92 normalize unified-account transfer account selection.

- Observation: The low-level non-unified spot API still expects the amount as a decimal string, not as atomic units.
  Evidence: `src/hyperopen/api/trading/agent_actions.cljs` lines 316-327 signs `{:type "subAccountSpotTransfer" :amount (str amount)}`.

- Observation: Balance rendering already knows how to derive spot-token decimals from `:weiDecimals`, `:szDecimals`, and market metadata.
  Evidence: `src/hyperopen/views/account_info/projections/balances.cljs` lines 392-418 build token and coin decimal maps.

- Observation: Adding normalized spot metadata to the existing effect test initially pushed `test/hyperopen/subaccounts/effects_test.cljs` over the 500-line namespace-size gate.
  Evidence: `npm run check` failed at `lint:namespace-sizes` with `effects_test.cljs - namespace has 504 lines`; the test hunk was compacted to keep the file at 499 lines without adding an exception.

- Observation: A field-presence-only effect schema still allowed internally inconsistent normalized payloads.
  Evidence: a read-only reviewer reproduced that `:amount "1"` with `:amount-units "999"` passed the transfer spec; regression tests now cover spot unit mismatches, over-precision mismatches, noncanonical spot decimals, trading amount/unit mismatch, and trading display mismatch.

## Decision Log

- Decision: Add a pure namespace `hyperopen.subaccounts.transfer-amount` rather than expanding `management.cljs`.
  Rationale: The existing management namespace owns many unrelated subaccount actions. A focused namespace makes precision parsing testable without wallet, DOM, or API dependencies.
  Date/Author: 2026-06-04 / Codex

- Decision: Spot transfer effect payloads should carry both the API decimal amount string and a normalized atomic-unit string.
  Rationale: Hyperliquid spot transfers submit decimal strings, but the app still needs an exact normalized value to prove precision validation happened without relying on JavaScript floating point.
  Date/Author: 2026-06-04 / Codex

- Decision: USDC can default to 6 decimals, but non-USDC spot tokens must have explicit precision metadata from the selected balance, spot token metadata, or market metadata.
  Rationale: Failing closed for unknown non-USDC precision avoids silently accepting an amount that the provider may round or reject.
  Date/Author: 2026-06-04 / Codex

- Decision: The implementation should preserve existing API wrapper signatures and submit the canonical decimal string to `transfer-sub-account-spot!`.
  Rationale: The bug is in app-side validation and payload semantics. Changing the signed action shape is unnecessary and would increase integration risk.
  Date/Author: 2026-06-04 / Codex

## Outcomes & Retrospective

Implemented. Subaccount transfer amount normalization now runs through a pure codec before any API effect is emitted. Trading USDC still emits a safe-integer `:usd` micro-unit value, and now also carries `:account-kind :trading`, `:token "USDC"`, `:amount-display`, `:amount-units`, and `:amount-decimals 6`. Spot transfers now require token precision metadata, emit canonical decimal `:amount`, display amount, positive integer string `:amount-units`, integer `:amount-decimals`, and `:token-symbol`, and reject over-precision, scientific notation, zero, huge atomic-unit strings, and unknown non-USDC precision before emitting `:effects/api-transfer-subaccount`.

The effect boundary now has separate create, rename, and transfer specs. Transfer specs reject malformed normalized payloads while preserving create/rename compatibility. The low-level spot signing path remains unchanged: `transfer-sub-account-spot!` still receives the selected token and canonical decimal string amount.

Validation evidence:

- `npm test` passes: 4218 tests, 23408 assertions.
- `npm run check` passes, including input parsing, docs, namespace size/boundary gates, release/style tooling, and Shadow compile targets.
- `npm run test:websocket` passes: 531 tests, 3080 assertions.
- `git diff --check` passes.

Browser QA was not required because this change did not touch view code, visible layout, or browser interaction flows. Existing action/effect/schema/unit tests cover the changed semantics.

## Context and Orientation

The subaccount management form stores user input under `[:account-context :subaccounts]`. The relevant fields are `:transfer-amount`, `:transfer-direction`, `:transfer-account`, and `:transfer-token`. `:transfer-direction` is either `:deposit` or `:withdraw`; deposits move funds from the master account to the selected subaccount, and withdrawals move funds from the selected subaccount back to the master account. `:transfer-account` is `:trading` for the perps/trading account or `:spot` for spot-token balances. Unified accounts are special: `effective-transfer-account` treats them as `:trading` so the effect uses the unified `sendAsset` path instead of the older spot subaccount path.

The current trading transfer flow is safer than the current spot flow. In `src/hyperopen/subaccounts/management.cljs`, `parse-usdc-amount->micros` accepts a decimal string with at most 6 fractional digits and converts it to a raw micro-USDC integer. `submit-transfer-subaccount` then emits `[:effects/api-transfer-subaccount {:usd <integer> :amount <original text>}]` for trading transfers. That raw `:usd` value is what `src/hyperopen/subaccounts/effects.cljs` passes to `transfer-sub-account!`.

The current spot transfer flow is weaker. `valid-spot-amount?` checks only that the input matches a decimal regex and becomes a finite positive JavaScript number. This loses exact precision for large values and does not know the selected token's allowed decimals. The action emits raw `:amount`, `:account-kind :spot`, and `:token`; the effect passes those values to `transfer-sub-account-spot!`.

Use the following terms in this plan:

- "Decimal amount string" means the string the API should submit, such as `2.5` or `0.02`.
- "Atomic units" means the exact integer string after scaling by token decimals. For a token with 6 decimals, `2.5` becomes `2500000`. For a token with 2 decimals, `0.02` becomes `2`.
- "Token decimals" means the maximum number of fractional decimal places accepted for that spot token.
- "Codec" means a pure function that validates an amount and returns either a normalized payload map or a specific error reason.

## Plan of Work

First, add focused RED tests before changing production code. Create `test/hyperopen/subaccounts/transfer_amount_test.cljs` for the pure codec. The tests should cover trading USDC behavior that currently exists, spot token precision for at least two non-USDC tokens, bad syntax, huge values, and missing precision metadata. Add action-level assertions in `test/hyperopen/subaccounts/actions_test.cljs` so `submit-transfer-subaccount` emits the new normalized fields. Add effect-level assertions in `test/hyperopen/subaccounts/effects_test.cljs` so the effect still calls `transfer-sub-account-spot!` with the canonical decimal string and still shows the display amount in the toast.

Second, create `src/hyperopen/subaccounts/transfer_amount.cljs`. This namespace must be pure. It should not require runtime effect namespaces, DOM/view namespaces, or API wrappers. It should provide at least these public functions:

    (parse-usdc-amount->micros value)
    (normalize-transfer-amount state opts)

`parse-usdc-amount->micros` should preserve the existing behavior and return an integer micro-USDC value for valid trading amounts. `normalize-transfer-amount` should accept a map containing `:subaccount-address`, `:account-kind`, `:direction`, `:token`, and `:amount-text`, and should return either `{:ok? true :payload {...}}` or `{:ok? false :message "..."}`

For trading transfers, the payload should contain:

    {:account-kind :trading
     :token "USDC"
     :amount <trimmed decimal string>
     :amount-display <trimmed decimal string>
     :amount-decimals 6
     :amount-units <micro-USDC string>
     :usd <micro-USDC number>}

For spot transfers, the payload should contain:

    {:account-kind :spot
     :token <selected token string>
     :token-symbol <display symbol>
     :amount <canonical decimal string>
     :amount-display <canonical decimal string>
     :amount-decimals <token decimals>
     :amount-units <atomic unit string>}

The spot parser must not call `js/Number` for validation. It should validate the string shape, split whole and fractional parts, reject fractional precision greater than `:amount-decimals`, trim leading zeros from the whole part while keeping `0` when needed, right-pad the fractional part for atomic units, reject zero atomic units, and reject an atomic unit string that exceeds an explicit app safety limit. Use a named constant such as `max-spot-atomic-digits` so future provider changes have one place to adjust this. A conservative initial value of 38 digits is acceptable because it avoids JavaScript precision hazards while leaving normal token balances far below the limit.

Third, add token metadata resolution to the same pure namespace. It should resolve decimals in this order:

1. The selected balance row's own `:amount-decimals`, `:decimals`, `:weiDecimals`, or `:szDecimals` fields, including camelCase and string-key variants.
2. Spot token metadata from state, such as `[:spot :meta :tokens]` or `[:webdata2 :spotMeta :tokens]`, matched by token id, token index, name, or coin where available.
3. Spot market metadata where the app already has `:szDecimals` for the token's base coin.
4. The default `6` only for `USDC` or a token whose normalized symbol is `USDC`.

If the selected non-USDC token has no precision metadata, return `{:ok? false :message "Spot asset precision is unavailable. Refresh balances before transferring."}`. Use the existing `invalid-spot-transfer-token-message` for missing token selection and add a new message constant for missing precision.

Fourth, wire the codec into `src/hyperopen/subaccounts/management.cljs`. Replace the spot branch that calls `valid-spot-amount?` with one call to `transfer-amount/normalize-transfer-amount` after owner, row, direction, and effective account kind are known. Keep `effective-transfer-account` semantics unchanged. On success, merge the codec payload into the existing effect request with `:sub-account-user` and `:is-deposit`. On failure, emit the same `[:effects/save-many ...]` error shape used today, clearing `:transferring-address` to `nil`.

Fifth, update `src/hyperopen/subaccounts/actions.cljs` so any public parser var still points to the new namespace. Preserve `parse-usdc-amount->micros` as a public var because tests and external callers already use it.

Sixth, update `src/hyperopen/subaccounts/effects.cljs` only where needed. The effect should keep this compatibility behavior:

- Non-unified spot transfers call `transfer-sub-account-spot!` with `(:token request)` and `(:amount request)`.
- Unified transfers call `submit-send-asset!` with `:amount (:amount request)`.
- Success toasts prefer `(:amount-display request)` and fall back to `(:amount request)`.

Do not submit `:amount-units` to the current Hyperliquid action wrappers unless the API wrapper itself changes in a separate documented plan.

Seventh, tighten effect argument specs in `src/hyperopen/schema/contracts/effect_args.cljs`. The existing `::api-subaccount-management-args` is a broad `(s/tuple map?)` shared by create, rename, and transfer. Split it into create, rename, and transfer specs so `:effects/api-transfer-subaccount` requires the normalized transfer shape. The transfer spec should accept the trading shape with `:usd` and the spot shape with `:account-kind :spot`, `:token`, `:amount`, `:amount-units`, and `:amount-decimals`.

Eighth, update or add tests:

- In `test/hyperopen/subaccounts/transfer_amount_test.cljs`, assert `parse-usdc-amount->micros` still returns `1230000` for `1.23`, `1` for `0.000001`, and `nil` for `1.0000001`, zero, blank, and overflowing micro-USDC values.
- In the same test file, assert a spot token with 6 decimals normalizes `2.5` to `:amount "2.5"` and `:amount-units "2500000"`.
- Assert a spot token with 2 decimals accepts `0.02` and rejects `0.001`.
- Assert `1e6`, `Infinity`, `NaN`, negative values, duplicate decimal points, and blank values fail before an effect payload can be built.
- Assert an atomic unit string longer than `max-spot-atomic-digits` fails.
- Assert an unknown non-USDC token without decimal metadata fails with the missing precision message.
- In `test/hyperopen/subaccounts/actions_test.cljs`, update the valid spot transfer expectation to include `:amount-units`, `:amount-decimals`, `:amount-display`, and `:token-symbol`.
- In `test/hyperopen/subaccounts/actions_test.cljs`, add a rejected non-USDC over-precision case for the selected token.
- In `test/hyperopen/subaccounts/effects_test.cljs`, update the spot transfer success test so the request includes the new normalized fields and the API call still receives the canonical decimal amount.
- Add a small spec assertion if this repo has an existing effect-args spec test surface; otherwise the full `npm test` compile will still exercise the stricter spec registration indirectly.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/e29d/hyperopen`.

1. Add the RED codec tests.

    Edit `test/hyperopen/subaccounts/transfer_amount_test.cljs`. If the file does not exist, create it. Require `cljs.test` and `hyperopen.subaccounts.transfer-amount`. Include fixture states with:

    - a master spot balance for `USDH:0xabc` with 6 decimals in metadata;
    - a subaccount spot balance for `MEOW:0xdef` with 2 decimals in metadata;
    - an unknown non-USDC token with no metadata.

2. Run the test suite and confirm the new namespace or functions are missing.

    Command:

        npm test

    Expected before implementation:

        The new transfer amount tests fail because `hyperopen.subaccounts.transfer-amount` or `normalize-transfer-amount` does not exist.

3. Implement `src/hyperopen/subaccounts/transfer_amount.cljs`.

    Keep the namespace pure. Do not import views, runtime effects, or API wrappers. Use string operations for spot decimal parsing. Reuse `clojure.string` and `hyperopen.account.context` only if address normalization is needed for selecting the active subaccount row.

4. Wire `src/hyperopen/subaccounts/management.cljs`.

    Add a require alias such as `[hyperopen.subaccounts.transfer-amount :as transfer-amount]`. Replace the local spot validation helper with the codec. Keep the old public behavior for trading USDC by routing through the codec and preserving the `:usd` field.

5. Wire `src/hyperopen/subaccounts/actions.cljs`.

    Update the public `parse-usdc-amount->micros` var to point at the new namespace if it currently points at `management`.

6. Update `src/hyperopen/subaccounts/effects.cljs`.

    Prefer `(:amount-display request)` in success toasts, but keep `(:amount request)` as a fallback. Keep `transfer-sub-account-spot!` and `submit-send-asset!` API calls using the canonical decimal `:amount`.

7. Tighten `src/hyperopen/schema/contracts/effect_args.cljs`.

    Replace broad shared transfer specs with precise create, rename, and transfer specs. Make sure `:effects/api-create-subaccount` and `:effects/api-rename-subaccount` still accept their existing payload maps.

8. Run focused and full validation.

    During implementation, run:

        npm test

    Before completion, run:

        npm run check
        npm test
        npm run test:websocket

    If implementation touches `src/hyperopen/views/**`, account for browser QA explicitly. If the view changes are metadata-only and deterministic render tests cover the surface, record the browser QA skip with the reason. If any visible copy, layout, or interaction changes, run the smallest relevant Playwright or Browser MCP check according to `/hyperopen/docs/BROWSER_TESTING.md`.

## Validation and Acceptance

Acceptance requires all of the following:

- A trading USDC transfer still emits `:usd` as a safe integer micro-USDC value and rejects more than 6 fractional digits.
- A non-unified spot transfer emits `:account-kind :spot`, selected `:token`, canonical decimal `:amount`, `:amount-display`, integer-string `:amount-units`, and `:amount-decimals`.
- A token with 2 decimals rejects an input with 3 fractional digits before any `:effects/api-transfer-subaccount` effect is emitted.
- Scientific notation, non-finite strings, negative values, blank strings, zero, and atomic-unit strings above the configured digit limit are rejected.
- A non-USDC token without precision metadata is rejected with a clear refresh/precision error.
- `transfer-subaccount!` still calls `transfer-sub-account-spot!` with the canonical decimal string for non-unified spot transfers.
- Unified account transfers still use `submit-send-asset!` and are not accidentally routed into the spot subaccount API.
- `npm run check`, `npm test`, and `npm run test:websocket` pass.

## Idempotence and Recovery

All planned code changes are additive or local replacements. The pure codec can be tested repeatedly without external services. If schema tightening breaks unrelated create or rename effects, restore their broad map acceptance through separate create and rename specs while keeping transfer strict. If token metadata resolution proves incomplete for live data, fail closed for non-USDC tokens and record the missing raw shape in `Surprises & Discoveries` before adding another metadata source.

If browser QA is required because view code changes, stop any browser inspection sessions with `npm run browser:cleanup` before final handoff.

## Artifacts and Notes

The key current behavior to replace is:

    src/hyperopen/subaccounts/management.cljs
      parse-usdc-amount->micros: converts trading USDC to integer micros
      valid-spot-amount?: only checks positive finite JavaScript Number
      submit-transfer-subaccount: emits raw spot :amount and :token

The key API compatibility constraint is:

    src/hyperopen/api/trading/agent_actions.cljs
      transfer-sub-account-spot!: signs :amount as a string

The key metadata precedent is:

    src/hyperopen/views/account_info/projections/balances.cljs
      token-decimals: uses :weiDecimals, :szDecimals, and market metadata

## Interfaces and Dependencies

At the end of the implementation, `src/hyperopen/subaccounts/transfer_amount.cljs` should expose:

    (def invalid-transfer-amount-message "Enter a positive USDC amount with at most 6 decimal places.")
    (def invalid-spot-transfer-amount-message "Enter a positive spot amount that matches the selected asset precision.")
    (def missing-spot-transfer-precision-message "Spot asset precision is unavailable. Refresh balances before transferring.")
    (def max-spot-atomic-digits 38)
    (defn parse-usdc-amount->micros [value] ...)
    (defn normalize-transfer-amount [state opts] ...)

`normalize-transfer-amount` should accept:

    {:subaccount-address <normalized address>
     :account-kind :trading or :spot
     :direction :deposit or :withdraw
     :token <selected token string>
     :amount-text <user input string>}

and return either:

    {:ok? true
     :payload <normalized effect payload fragment>}

or:

    {:ok? false
     :message <user-facing error message>}

The implementation must not add a new external dependency.

## Change Notes

- 2026-06-04: Initial ExecPlan created from the maintainer request and current source review. The plan is intentionally scoped to validation, payload normalization, schema tightening, and tests; it does not change the signed Hyperliquid action API shape.
