# Implement Hyperliquid TWAP order parity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`. The implementation work tracked here is associated with `bd` issue `hyperopen-35i`, which was the local status reference outside this file.

## Purpose / Big Picture

After this change, a Hyperopen user will be able to create, review, submit, monitor, and terminate Hyperliquid TWAP orders with the same core behavior that the official Hyperliquid app exposes today. "TWAP" means a time-weighted average price order: the exchange breaks one large order into many smaller suborders that are sent over time. In this repository, that means the trade ticket must build the right request payload, validation must enforce the same runtime and minimum-size rules as Hyperliquid, and the account area must show active TWAPs, historical TWAPs, and TWAP slice fills instead of the current placeholder tab.

The visible proof is straightforward. In the trade ticket, choosing the `TWAP` order type should show a runtime editor that matches Hyperliquid's interaction model, reject invalid runtimes and too-small suborders, and submit a `twapOrder` request. In account info, the `TWAP` tab should stop saying "coming soon" and instead show live TWAP states, history, and fill history with terminate actions for running TWAPs.

## Progress

- [x] (2026-03-11 19:13Z) Claimed `bd` issue `hyperopen-35i` for TWAP parity work.
- [x] (2026-03-11 19:13Z) Audited the current repository and confirmed that TWAP submit plumbing exists in `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs`, `/hyperopen/src/hyperopen/state/trading.cljs`, and `/hyperopen/src/hyperopen/views/trade/order_form_type_extensions.cljs`, but account-level TWAP rendering is still a placeholder in `/hyperopen/src/hyperopen/views/account_info_view.cljs`.
- [x] (2026-03-11 19:13Z) Researched current Hyperliquid behavior from official documentation and the production frontend bundle, then embedded the results in this plan.
- [x] (2026-03-11 19:27Z) Implemented the first trade-ticket parity slice: TWAP now normalizes legacy drafts into an hours-plus-minutes runtime model, defaults to `0h 30m` with randomization off, enforces the 5-minute to 24-hour runtime range, enforces the per-suborder 10 USDC minimum notional rule, and shows inline TWAP preview details in the trade form.
- [x] (2026-03-11 20:49Z) Implemented account-side TWAP lifecycle surfaces: user websocket subscriptions now cover `twapStates`, `userTwapHistory`, and `userTwapSliceFills`; store projections normalize active TWAPs, TWAP history, and TWAP slice fills; and the account view model exposes those rows only when the TWAP tab is selected.
- [x] (2026-03-11 20:49Z) Replaced the placeholder account TWAP tab with working `active`, `history`, and `fill history` views plus terminate interactions that emit `twapCancel`.
- [x] (2026-03-11 20:49Z) Re-ran the required validation gates successfully on the TWAP account-side slice: `npm run check`, `npm test`, and `npm run test:websocket`.

## Surprises & Discoveries

- Observation: Hyperopen already knows how to submit a TWAP request, but it models TWAP only as a submit-time order type. It does not yet model the separate TWAP lifecycle that Hyperliquid exposes after submission.
  Evidence: `/hyperopen/src/hyperopen/views/account_info_view.cljs` renders `:twap` with `placeholder-tab-content`, while `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs` already emits `{:type "twapOrder" ...}`.

- Observation: Hyperliquid's TWAP rules are more specific than "minutes greater than zero". The official app uses a fixed 30-second suborder frequency, a minimum runtime of 5 minutes, a maximum runtime of 24 hours, and computes the number of suborders as `2 * total_minutes + 1`.
  Evidence: the production app bundle defines the constants `m=5`, `f=1440`, and `g=30`, and calculates `numOrders` from `60 * minutes / 30 + 1`.

- Observation: Hyperliquid also blocks TWAP submission when a single TWAP slice is below the venue minimum order value of 10 quote units, not merely when the total order size is too small.
  Evidence: the production bundle checks `size_per_suborder * reference_price < 10` before submission and shows a TWAP-specific minimum-value error.

- Observation: The official app has a fuller account TWAP surface than a single table. It contains `active`, `history`, and `fill history` views with different columns and status behavior.
  Evidence: the production bundle contains TWAP tab sub-views keyed as active state, `userTwapHistory`, and `userTwapSliceFillsByTime`, plus a live websocket subscription keyed as `twapStates`.

- Observation: The local environment did not have repository dependencies installed at the start of implementation, so the first `npm test` attempt failed before the test runner even loaded application code.
  Evidence: `npm test` initially failed with `sh: shadow-cljs: command not found`; after `npm ci`, the named test gates ran successfully.

- Observation: Hyperliquid's TWAP history and slice-fill websocket subscriptions are sufficient to populate the account tab without introducing a second REST hydration path in this slice.
  Evidence: the production bundle subscribes to `userTwapHistory` and `userTwapSliceFills`, and the local implementation passed the full test suite using websocket-backed snapshots for both surfaces.

## Decision Log

- Decision: Start implementation with trade-ticket parity before account-tab rendering.
  Rationale: The trade ticket already contains partial TWAP code, so bringing defaults, validation, and request construction in line with Hyperliquid is the lowest-risk slice and gives us immediately testable behavior while the larger account-surface work is still pending.
  Date/Author: 2026-03-11 / Codex

- Decision: Treat TWAP as a parallel account lifecycle instead of forcing it into the existing open-order and order-history tables.
  Rationale: Hyperliquid exposes TWAP through separate exchange actions (`twapOrder`, `twapCancel`), a separate websocket feed (`twapStates`), and separate info queries (`twapHistory`, `userTwapSliceFills`). Reusing the standard order lifecycle would create hidden mismatches around identifiers, statuses, cancel semantics, and fill history.
  Date/Author: 2026-03-11 / Codex

- Decision: Preserve backward compatibility for persisted drafts by normalizing legacy TWAP minute values into the new runtime representation instead of hard-breaking stored order-form state.
  Rationale: Existing local drafts and tests still use a single `:twap {:minutes ...}` shape. A normalization layer lets us introduce an hours-plus-minutes UI without breaking old data or requiring a migration step.
  Date/Author: 2026-03-11 / Codex

## Outcomes & Retrospective

The planned TWAP parity work is now implemented in two cohesive slices. The trade ticket has one shared interpretation of TWAP runtime, slice count, and minimum slice notional across form defaults, UI preview, validation, and request building. The account area now models TWAP as its own lifecycle instead of pretending it is a standard open order: websocket subscriptions hydrate active TWAPs, TWAP history, and TWAP slice fills, the TWAP tab renders the official three-subtab structure, and terminate actions send `twapCancel` using the resolved asset index and `twapId`.

The most useful simplification was keeping TWAP lifecycle separate from ordinary order history while still reusing stable table primitives and fill-history rendering where that behavior truly matched. That preserved clarity around identifiers and statuses and avoided hidden coupling with standard order cancel logic. The end state passed `npm run check`, `npm test`, and `npm run test:websocket`, which gives us good confidence that the TWAP path is now covered end to end.

## Context and Orientation

The current trade ticket is built from a normalized order form in `/hyperopen/src/hyperopen/state/trading.cljs`, with default form state in `/hyperopen/src/hyperopen/trading/order_form_state.cljs`, UI section rendering in `/hyperopen/src/hyperopen/views/trade/order_form_type_extensions.cljs`, and submit validation in `/hyperopen/src/hyperopen/domain/trading/validation.cljs`. Request payloads are built in `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs`. Tests covering these paths already exist in `/hyperopen/test/hyperopen/state/trading/validation_and_scale_test.cljs`, `/hyperopen/test/hyperopen/api/gateway/orders/commands_test.cljs`, and `/hyperopen/test/hyperopen/views/trade/order_form_component_sections_test.cljs`.

The account info area is separate. `/hyperopen/src/hyperopen/views/account_info_view.cljs` owns the tab switcher and renderer. `/hyperopen/src/hyperopen/views/account-info/vm.cljs` builds the view model for tables such as open orders and trade history. TWAP is currently not connected there. The websocket and data-fetch boundaries that will need extension live under `/hyperopen/src/hyperopen/websocket/**`, `/hyperopen/src/hyperopen/account/**`, and `/hyperopen/src/hyperopen/api/**`.

Research captured here replaces the need to re-open external sources during implementation. Hyperliquid currently exposes TWAP through these behaviors:

TWAP submission behavior:

1. The exchange endpoint supports `twapOrder` and `twapCancel`.
2. A successful submit response includes a running TWAP identifier in `response.data.status.running.twapId`.
3. A TWAP uses fixed 30-second suborders.
4. Valid runtime is from 5 minutes through 1440 minutes inclusive.
5. The official app defaults to 30 minutes total runtime and `randomize = false`.
6. The official app edits runtime as separate hours and minutes inputs, then converts that to total minutes for the wire payload.
7. The number of suborders is `2 * total_minutes + 1`.
8. The size per suborder is `total_size / (2 * total_minutes + 1)`.
9. Submission is blocked when one suborder's notional value is below 10 quote units, using the current reference price.

TWAP account behavior:

1. The websocket API exposes a `twapStates` subscription for running TWAPs.
2. The info API exposes TWAP history and TWAP slice fill history.
3. The official app shows a TWAP tab with three sub-views: active TWAPs, TWAP history, and TWAP fill history.
4. Active TWAP rows show coin, size, executed size, average price, elapsed runtime versus total runtime, reduce-only, creation time, and a terminate control.
5. History rows show time, coin, total size, executed size, average price, total runtime, reduce-only, randomize, and status, with special handling for error descriptions.

## Plan of Work

The first milestone updates the trade-ticket path. In `/hyperopen/src/hyperopen/trading/order_form_state.cljs`, introduce TWAP runtime normalization that supports a new hours-plus-minutes editing model while still accepting legacy drafts that only store total minutes. Define shared TWAP defaults there so the form starts at 30 minutes with randomization off. In `/hyperopen/src/hyperopen/domain/trading/core.cljs`, add TWAP helper functions for total runtime minutes, fixed suborder frequency, number of suborders, per-suborder size, runtime validity, and the minimum suborder notional rule. Expose those helpers through `/hyperopen/src/hyperopen/domain/trading.cljs` and `/hyperopen/src/hyperopen/state/trading.cljs` so both the UI and the validators can use the same logic.

Still in the first milestone, update `/hyperopen/src/hyperopen/domain/trading/validation.cljs` to replace the current "minutes greater than zero" check with two TWAP validations: runtime must be between 5 and 1440 minutes inclusive, and each TWAP suborder must be worth at least 10 quote units at the current reference price. Keep the validation messages deterministic and wire them into `submit-required-fields` so the existing submit-guidance UI can still explain what is missing or invalid.

Then update the TWAP section renderer in `/hyperopen/src/hyperopen/views/trade/order_form_type_extensions.cljs`, plus its handlers and commands in `/hyperopen/src/hyperopen/views/trade/order_form_handlers.cljs` and `/hyperopen/src/hyperopen/views/trade/order_form_commands.cljs`. The section should present separate hours and minutes inputs and the randomize toggle. If there is already a review or summary area in the trade ticket, extend it to show runtime, frequency, number of orders, and size per suborder using the shared helpers.

The second milestone extends data acquisition. Add TWAP-specific request and projection support for live TWAP states, TWAP history, and TWAP slice fills. The work will touch `/hyperopen/src/hyperopen/account/surface_service.cljs`, `/hyperopen/src/hyperopen/account/surface_policy.cljs`, `/hyperopen/src/hyperopen/websocket/user_runtime/subscriptions.cljs`, `/hyperopen/src/hyperopen/websocket/user_runtime/handlers.cljs`, and the relevant API endpoint modules. This milestone must also introduce terminate support through `twapCancel` and store the returned TWAP identifiers and statuses in a shape the account view model can consume.

The third milestone replaces the placeholder account TWAP tab. Add TWAP-specific projections in `/hyperopen/src/hyperopen/views/account-info/projections.cljs` and `/hyperopen/src/hyperopen/views/account-info/vm.cljs`, then render the three TWAP sub-tabs and their tables in `/hyperopen/src/hyperopen/views/account_info_view.cljs` or new tab modules under `/hyperopen/src/hyperopen/views/account-info/tabs/`. Reuse existing account-table patterns where possible, but do not collapse TWAP history into ordinary order history because the fields and statuses differ.

## Concrete Steps

All commands are run from `/Users/barry/.codex/worktrees/e0ee/hyperopen`.

The work begins with targeted TWAP tests while the first milestone is under active development:

    npm test -- --runInBand test/hyperopen/state/trading/validation_and_scale_test.cljs
    npm test -- --runInBand test/hyperopen/api/gateway/orders/commands_test.cljs
    npm test -- --runInBand test/hyperopen/views/trade/order_form_component_sections_test.cljs

As milestones complete, run the repository gates:

    npm run check
    npm test
    npm run test:websocket

Expected signals during milestone one:

1. The TWAP form shows separate `Hours` and `Minutes` inputs instead of a single minutes input.
2. A TWAP with runtime below 5 minutes or above 24 hours fails validation.
3. A TWAP whose per-suborder value is below 10 fails validation even when total order size is positive.
4. A valid TWAP still builds a `twapOrder` request with total minutes in `:m`.

## Validation and Acceptance

Milestone one is accepted when the trade ticket exposes Hyperliquid-style TWAP runtime editing and the automated tests prove the new rules. Specifically, run the three TWAP-related test files listed above and observe that new tests covering runtime normalization, the 5-minute minimum, the 24-hour maximum, and the suborder-notional minimum pass. Also verify that existing scale and non-TWAP order tests continue to pass.

The full plan is accepted when all required repository gates pass and a manual product check shows the following behavior:

1. Selecting `TWAP` in the trade ticket displays the new runtime inputs and a randomize toggle with defaults of `0` hours, `30` minutes, and randomize disabled.
2. Submitting a valid TWAP sends a `twapOrder` request.
3. The account `TWAP` tab shows active TWAPs, historical TWAPs, and TWAP fill history instead of placeholder content.
4. A running TWAP can be terminated from the account view, which sends `twapCancel` for the correct `twapId`.

## Idempotence and Recovery

The planned edits are additive and safe to re-run. Order-form normalization should continue to accept both legacy and new TWAP draft shapes so repeated local testing does not corrupt drafts. If the account-surface work stalls midway, keep the placeholder replacement behind deterministic view-model checks so partially loaded data does not break the rest of account info. If a websocket or info surface is added incorrectly, recovery means removing the new surface registration and restoring the prior tests before continuing.

## Artifacts and Notes

Key implementation facts captured from research:

- Hyperliquid TWAP submit uses fixed 30-second frequency.
- Runtime bounds are 5 to 1440 minutes inclusive.
- Default runtime in the official app is 30 minutes.
- Default randomize flag in the official app is false.
- Active TWAP state comes from a `twapStates` websocket subscription.
- TWAP history and fill history come from separate info surfaces, not from ordinary order history.

Key local gaps captured during audit and resolved during implementation:

- `/hyperopen/src/hyperopen/views/account_info_view.cljs` had rendered the TWAP tab as placeholder content.
- `/hyperopen/src/hyperopen/views/account-info/vm.cljs` did not derive TWAP rows or counts.
- `/hyperopen/src/hyperopen/domain/trading/validation.cljs` only checked that TWAP minutes were greater than zero.
- `/hyperopen/src/hyperopen/trading/order_form_state.cljs` defaulted TWAP to 5 minutes and randomize true.

## Interfaces and Dependencies

The first milestone should leave these stable helper interfaces available for reuse across UI, validation, and request construction:

In `/hyperopen/src/hyperopen/domain/trading/core.cljs`, define shared TWAP helpers with behavior equivalent to:

    (def twap-min-runtime-minutes 5)
    (def twap-max-runtime-minutes 1440)
    (def twap-frequency-seconds 30)
    (def twap-min-suborder-notional 10)
    (defn twap-total-minutes [twap-form] ...)
    (defn valid-twap-runtime? [minutes] ...)
    (defn twap-suborder-count [minutes] ...)
    (defn twap-suborder-size [total-size minutes] ...)
    (defn twap-suborder-notional [total-size minutes reference-price] ...)

In `/hyperopen/src/hyperopen/domain/trading/validation.cljs`, TWAP validation must emit deterministic codes for invalid runtime and too-small suborder notional. The messages should explain the real rule in plain language. The validator must continue returning a vector of error maps so existing submit-policy code remains unchanged.

In `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs`, `build-twap-action` must continue emitting the same wire payload shape, but the total minutes value must come from the shared TWAP runtime helper rather than assuming one raw `:minutes` field.

Revision note: Created this plan after auditing the repository, claiming `hyperopen-35i`, and researching Hyperliquid's current TWAP behavior so the implementation can proceed from a self-contained specification.

Revision note: Updated the plan after completing the first trade-ticket TWAP parity slice and running `npm run check`, `npm test`, and `npm run test:websocket` successfully.

Revision note: Updated the plan after wiring TWAP websocket lifecycle surfaces, the account TWAP tab, and `twapCancel`, then re-running `npm run check`, `npm test`, and `npm run test:websocket` successfully.
