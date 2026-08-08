# Diagnose and explain the Testnet Bridge2 USDC deposit failure

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This plan follows `docs/PLANS.md` and `.agents/PLANS.md`.

## Purpose / Big Picture

Someone who attempts a Testnet USDC deposit and receives a wallet-provider failure must see a short explanation of what failed and what to do next, rather than an opaque fragment such as `Deposit failed: RPC 0x66eee Custom e...`. The deposit modal must remain open with the full actionable error, and the toast must have a short readable summary plus an untruncated detail when needed.

This plan first establishes the failure category from nested wallet errors. It adds a pre-send USDC2 balance check only if reproducible evidence proves that the requested Bridge2 transfer cannot be funded. The observable result is a deterministic test or local simulator scenario: a wallet rejection, wrong-chain error, token-balance failure, transaction revert, or unknown provider failure produces safe, actionable feedback without performing a real wallet action.

## Context References

Public refs: the direct user report on 2026-08-08, `Deposit failed: RPC 0x66eee Custom e...`, while funding a Testnet deposit. The report is the durable task context; no GitHub Issue or Pull Request was supplied.

Repo artifacts: `docs/exec-plans/active/2026-07-22-worker-browser-qa-remediation.md` records the prior Testnet funding investigation and the current official contract pair. `docs/BROWSER_TESTING.md`, `docs/FRONTEND.md`, and `docs/agent-guides/browser-qa.md` govern any subsequent UI implementation and verification.

Local scratch refs (non-authoritative): none.

## Progress

- [x] (2026-08-08, planning session) Captured the direct user report and constrained this plan to Testnet Bridge2 USDC deposit diagnosis and feedback.
- [x] (2026-08-08, planning session) Traced the Bridge2 submit path, current Testnet chain configuration, toast adapter, and funding-modal feedback projection.
- [x] (2026-08-08, planning session) Identified the missing nested-error decoding, the hidden deposit inline error, and the existing structured-toast capability.
- [x] (2026-08-08) Added deterministic RED/GREEN coverage for nested wallet errors and the evidence-qualified Testnet USDC2 balance preflight.
- [x] (2026-08-08) Implemented scoped funding feedback, Mainnet-safe routing, receipt-revert classification, malformed-balance fallback, duplicate-submit guard, inline visibility, and structured toast rendering.
- [x] (2026-08-08) Completed the corrected 35/35 repository gates with Homebrew JDK 21, including `npm test` (5948 tests, 33148 assertions) and `npm run test:websocket` (562 tests, 3198 assertions).
- [x] (2026-08-08) Source-backed focused Playwright funding regression passed 6/6 at the managed local app. The first governed design-review attempt could not start its managed app; the source fix was also simulated across 375, 768, 1280, and 1440 px with no toast/modal overlap, no footer occlusion, and a visible dismiss focus ring. Native-control, styling-consistency, interaction, layout-regression, and jank/perf evidence were recorded in the browser QA artifacts; dynamic design-review automation remains an environment limitation.

## Surprises & Discoveries

- Observation: `0x66eee` is the configured Arbitrum Sepolia chain identifier, not an explanation of why the transfer failed.
  Evidence: `src/hyperopen/funding/effects/common.cljs` maps `arbitrum-sepolia-chain-id` to `0x66eee`, network label `Arbitrum Sepolia`, USDC2 `0x1baAbB04529D43a73232B713C0FE471f7c7334d5`, and Bridge2 `0x08cfc1B6b2dCF36A1480b99353A354AA8AC56f89`.

- Observation: the direct Bridge2 path checks provider, address, amount, and wallet chain, but sends the ERC-20 transfer immediately after chain confirmation.
  Evidence: `submit-usdc-bridge2-deposit-tx!` in `src/hyperopen/funding/application/deposit_submit.cljs` calls `ensure-wallet-chain!` and then `eth_sendTransaction`; it has no USDC2 `balanceOf` read.

- Observation: the current wallet-error formatter reads only top-level `message` and `code`, so nested provider data can reach the user as raw, incomplete text.
  Evidence: `wallet-error-message` in `src/hyperopen/funding/effects/common.cljs` reads `err.message` and `err.code`; its existing tests cover user rejection and a top-level message only.

- Observation: failed deposit state is written correctly but ordinary deposit errors are not rendered inline during amount entry.
  Evidence: `set-funding-submit-error!` in `src/hyperopen/funding/effects.cljs` clears `:submitting?` and stores `[:funding-ui :modal :error]`; `show-status-message?` in `src/hyperopen/funding/application/modal_vm/presentation.cljs` excludes deposit-mode errors, so `src/hyperopen/views/funding_modal.cljs` does not render `funding-status`.

- Observation: the first UI pass exposed a stacking conflict between the fixed generic toast region and an open funding modal. At 375px the toast covered the Deposit button; at 1280px and 1440px it covered the right side of the inline status.
  Evidence: governed QA screenshots under `tmp/browser-inspection/deposit-rpc-error-qa-2026-08-08T06-45-13-631Z/` and `/private/tmp/hyperopen-deposit-error-*.png`.

- Observation: the generic toast dismiss control had no visible keyboard focus indicator because its Tailwind focus reset overrode the surface styling.
  Evidence: QA computed `:focus-visible` outline and box shadow as transparent before the fix.

- Resolution: when the funding modal is open, the notification region is positioned at `top-20`; the dismiss button uses the shared accent token for a visible two-pixel focus outline. This keeps the toast interactive without covering modal controls or status text.

- Observation: the shared toast runtime already accepts a map with `:headline`, `:subline`, and `:detail`; only the first two lines truncate in the generic toast view.
  Evidence: `src/hyperopen/order/feedback_runtime.cljs` preserves mapped toast content, while `src/hyperopen/views/notifications_view.cljs` renders `:detail` with `whitespace-normal` and `break-words`.

## Decision Log

- Decision: treat the screenshot as evidence of an unhelpful presentation failure, not proof that `0x66eee`, the Testnet addresses, or an insufficient USDC2 balance is the root cause.
  Rationale: the chain identifier is valid configuration and a truncated nested wallet error does not identify the revert or provider cause.
  Date/Author: 2026-08-08 / Codex.

- Decision: implement a narrow, pure deposit-wallet-error classifier that accepts nested JavaScript error objects and ClojureScript maps, and returns user-safe feedback rather than exposing raw provider payloads.
  Rationale: error shape handling belongs in the existing pure funding common module. Rendering and scheduling remain at the current application and toast boundaries.
  Date/Author: 2026-08-08 / Codex.

- Decision: add a USDC2 balance preflight only after the diagnostic milestone proves either that the resolved Testnet USDC2 balance is lower than the requested units or that a reproducible nested error explicitly identifies ERC-20 insufficient balance.
  Rationale: local UI state cannot prove a wallet balance, but an extra on-chain read without causal evidence would add a failure branch and latency to every deposit attempt without established value.
  Date/Author: 2026-08-08 / Codex.

- Decision: when enabled, the preflight performs one `eth_call` after chain confirmation and before `eth_sendTransaction`; a failed balance read falls through to the existing send behavior and is never reported as insufficient USDC2.
  Rationale: this is a correctness and safety check, not performance work. The workload is one read only for a user-initiated, locally valid Bridge2 USDC submission; the simpler local-state approach cannot know the connected wallet's current token balance.
  Date/Author: 2026-08-08 / Codex.

- Decision: preserve the existing three-argument `show-toast!` API and pass a structured map as its message only for this funding error path.
  Rationale: `order-feedback-runtime` already supports map messages, avoiding a broad adapter or notification behavior change.
  Date/Author: 2026-08-08 / Codex.

- Decision: this plan authorizes no real wallet confirmation, Testnet/Mainnet funding, deployment, remote push, or source/test implementation in the planning session.
  Rationale: the task is to create a scoped active ExecPlan. All later diagnostic and implementation work must use deterministic fakes until separately authorized.
  Date/Author: 2026-08-08 / Codex.

## Outcomes & Retrospective

Implementation is complete for the scoped funding path. Nested wallet errors now become bounded, actionable feedback; Testnet USDC2 balance evidence prevents a known underfunded send while malformed reads fall back to the existing transaction path; Mainnet keeps its legacy path. The modal stays open and recoverable, and the notification layer avoids the modal while exposing keyboard focus.

Validation completed: `npm run setup:worktree`, the corrected 35/35 gates, `node --check` for the focused Playwright spec, `git diff --check`, `npm run build:cloudflare`, artifact preflight (33 passed, 2 environment warnings), and `npm run cloudflare:check` dry-run. The source-backed focused funding Playwright suite passed 6/6; browser sessions and local dev processes were cleaned up. The release still carries the existing soft bundle-budget warning and the governed design-review runner could not manage its app in this environment, so the four-size evidence is recorded as simulated/source-backed QA rather than a fresh automated design-review report.

## Context and Orientation

The relevant transfer is an ERC-20 token transfer: the wallet submits a transaction calling the USDC2 token contract's `transfer` method with the Bridge2 contract as recipient. A wallet provider is the browser extension interface that implements JSON-RPC methods such as `eth_call` (a read-only contract query) and `eth_sendTransaction` (a wallet-confirmed transaction request). A nested wallet error is an error whose meaningful code or message is stored below fields such as `error`, `data`, `originalError`, or `cause` rather than at the top level.

`src/hyperopen/funding/application/deposit_submit.cljs` owns asynchronous deposit submission. Its `submit-usdc-bridge2-deposit-tx!` function is injected with browser-facing dependencies and returns a promise resolving to `{:status "ok" ...}` or `{:status "err" ...}`. `src/hyperopen/funding/effects/transport_runtime.cljs` supplies those dependencies, including the existing `read-erc20-balance-units!` wrapper around the `balanceOf` RPC implementation in `src/hyperopen/funding/infrastructure/erc20_rpc.cljs`.

`src/hyperopen/funding/effects/common.cljs` owns pure funding configuration and message helpers. `src/hyperopen/funding/application/submit_effects.cljs` turns a failed submit response into funding-modal state through `src/hyperopen/funding/effects.cljs`. `src/hyperopen/runtime/effect_adapters/funding_workflow.cljs` and `src/hyperopen/views/funding_modal_module.cljs` pass the existing toast callback into that path. `src/hyperopen/order/feedback_runtime.cljs` stores either a legacy string or a structured toast map, and `src/hyperopen/views/notifications_view.cljs` renders it. Finally, `src/hyperopen/funding/application/modal_vm/presentation.cljs` decides whether the stored modal error is visible, and `src/hyperopen/views/funding_modal.cljs` renders the inline `funding-status` region.

The implementation must stay on the resolved Testnet entry when the action uses `:chainId "0x66eee"`: Arbitrum Sepolia, USDC2 `0x1baAbB04529D43a73232B713C0FE471f7c7334d5`, and Bridge2 `0x08cfc1B6b2dCF36A1480b99353A354AA8AC56f89`. It must not alter the Mainnet configuration or public APIs outside this feedback path.

## Plan of Work

First add deterministic tests that reproduce the reported class of nested provider error with no live wallet. In `test/hyperopen/funding/effects/common_test.cljs`, create representative nested JavaScript-object and map shapes for user rejection, wrong-chain or switch failure, explicit ERC-20 insufficient balance, transaction revert, and unknown RPC failure. The tests must assert the exact category and user-facing strings, and assert that raw fields such as `RPC`, `0x66eee`, unbounded provider messages, request data, and nested JSON do not become visible text. Keep only a short bounded diagnostic reason when it is safe and useful.

Then add a pure helper in `src/hyperopen/funding/effects/common.cljs`, named `deposit-wallet-error-feedback`, with this contract:

    (deposit-wallet-error-feedback err)
    ;; => {:kind :wallet-rejected | :wrong-network | :insufficient-usdc2 |
    ;;            :transaction-reverted | :wallet-rpc
    ;;     :message <full inline message>
    ;;     :toast {:headline <short title>
    ;;             :subline <short next action>
    ;;             :detail <optional wrapped explanation>
    ;;             :auto-timeout? false}}

The helper must walk only a bounded, cycle-safe set of common nested fields (`message`, `code`, `error`, `data`, `originalError`, and `cause`) and normalize whitespace. It must recognize wallet rejection before generic RPC handling. It must recognize insufficient USDC2 only from deterministic balance evidence passed by the submit path or an explicit ERC-20 insufficient-balance/revert reason, never from a chain identifier alone. For an unknown provider failure, use a generic recovery such as checking Arbitrum Sepolia, enough USDC2, and test ETH for gas before retrying; do not render the provider's raw payload. Keep `wallet-error-message` compatible for the USDT and USDH deposit paths unless those paths are deliberately migrated with their own acceptance coverage.

In `test/hyperopen/funding/application/deposit_submit_test.cljs`, establish the preflight evidence gate with injected dependencies. If the diagnostic tests show that insufficient USDC2 is the failure category, extend `submit-usdc-bridge2-deposit-tx!` in `src/hyperopen/funding/application/deposit_submit.cljs` to accept `:read-erc20-balance-units!`. After `ensure-wallet-chain!` resolves, query the resolved `:usdc-address` for the normalized sender and compare bigint units to `amount-units`. When the balance is lower, resolve a structured `:status "err"` response without calling `eth_sendTransaction`. When it is equal or higher, send exactly the current `{ :from, :to, :data }` transfer payload and retain receipt waiting and success response behavior. When the read rejects or returns unusable data, continue to the existing send path; it must not produce an insufficient-balance label solely because the read failed.

If the diagnostic milestone cannot meet either proof condition, do not add the balance dependency or read. Record the rejected hypothesis in `Surprises & Discoveries` and implement only nested-error classification, structured feedback, and inline visibility. This branch is complete when the unknown and reverted errors become actionable without changing the send sequence.

For either branch, change the Bridge2 catch path to return the classifier's full inline message and structured toast feedback without leaking raw RPC content. Thread `:read-erc20-balance-units!` through `src/hyperopen/funding/effects/transport_runtime.cljs` only in the evidence-qualified branch, using its existing wrapper. Add an assertion in `test/hyperopen/funding/effects/transport_runtime_test.cljs` that the Bridge2 dependency map exposes that existing function, while the existing route dependencies remain unchanged.

At the submit-effect boundary, make `set-funding-submit-error!` in `src/hyperopen/funding/effects.cljs` accept both its existing string input and a funding error map with `:message` and `:toast`. It must make one state transition that sets `[:funding-ui :modal :submitting?]` to false and stores the complete inline message, then invoke the unchanged `show-toast!` callback with the structured toast map. In `src/hyperopen/funding/application/submit_effects.cljs`, consume Bridge2 structured error feedback while preserving the current `Deposit failed:` prefix for the modal message and existing string behavior for all other deposit submitters. Extend `test/hyperopen/funding/application/deposit_submit_test.cljs` to verify the modal remains open, the complete error is stored, and the captured toast is structured.

Restore error visibility in `src/hyperopen/funding/application/modal_vm/presentation.cljs` for a failed deposit that remains on amount entry. Do not surface unrelated preview validation while the user is still choosing an asset. Extend `test/hyperopen/views/funding_modal_test.cljs` and `test/hyperopen/views/funding_modal_accessibility_test.cljs` to prove `funding-status` appears for the failed deposit, contains the full message, leaves the amount field and submit control reachable, and keeps existing dialog labels and focus behavior. Extend `test/hyperopen/views/notifications_view_trade_confirmation_test.cljs` with a funding error map and verify that the action title and next step use the short lines while the explanatory detail is present, wrapped, and not truncated.

Use the existing `HYPEROPEN_DEBUG` bridge and wallet simulator for a Playwright test only if it can inject a nested provider rejection without a real wallet. In that case, extend the focused funding portion of `tools/playwright/test/trade-regressions.spec.mjs` so it opens Deposit USDC, enters a valid amount, drives the simulated failure, observes the open modal, full inline status, and structured toast, and asserts that no transaction is approved or sent. If the simulator cannot express this state without adding a browser-only application API, retain the deterministic ClojureScript coverage and record this limitation as a browser-test gap rather than using a live wallet.

## Concrete Steps

Run commands from `/Users/zh/Documents/Hyperopen`. These commands use only local test doubles and must not connect or submit through a real wallet:

    npm run setup:worktree

Expected result: the command links usable dependencies or explains that `npm ci` is needed. Do not interpret a missing local `shadow-cljs` binary before this step as a code failure.

During the RED and focused-GREEN phases, start the ClojureScript test REPL and run the named test namespaces:

    npx shadow-cljs cljs-repl test

    (cljs.test/run-tests
      'hyperopen.funding.effects.common-test
      'hyperopen.funding.application.deposit-submit-test
      'hyperopen.funding.effects.transport-runtime-test
      'hyperopen.funding.infrastructure.erc20-rpc-test
      'hyperopen.views.funding-modal-test
      'hyperopen.views.funding-modal-accessibility-test
      'hyperopen.views.notifications-view-trade-confirmation-test)

Expected result after implementation: zero failures and zero errors in every listed namespace. Before the implementation, the new nested-error, visibility, and conditional preflight tests must fail for the intended missing behavior rather than because the test harness cannot start.

When a deterministic browser injection exists, run the smallest relevant browser regression before broader browser work:

    npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "funding modal"

Expected result: the selected funding tests pass, the page has no uncaught errors, and the simulator records no signed or sent wallet transaction for the insufficient-balance scenario.

After source or test changes, run the required repository gates:

    npm run lint:docs
    npm run check
    npm test
    npm run test:websocket

`npm run gates` may replace the final three commands when its complete PASS/FAIL matrix is retained in the task record. A documentation-only plan update is validated with `npm run lint:docs`; later code changes require all listed code gates.

For a UI implementation, run governed design review on the local Trade route:

    npm run qa:design-ui -- --targets trade-route --manage-local-app

If Browser MCP or browser-inspection is used, explicitly clean its sessions before handoff:

    npm run browser:cleanup

## Validation and Acceptance

The work is accepted only when all applicable statements below are observable through the named tests or governed browser evidence:

1. A synthetic nested wallet error resembling the report is classified into a readable category and remediation. Neither the inline text nor any toast line includes the opaque `RPC 0x66eee Custom e...` fragment, raw nested payload, transaction data, or an unbounded provider message.

2. A user rejection remains distinguishable as a cancelled wallet confirmation, a known wrong-network or switch failure tells the user to use Arbitrum Sepolia, an explicit insufficient-token failure tells the user to obtain or select enough USDC2, and a revert or unknown provider error has a safe retry-oriented explanation. The classifier tests demonstrate each result.

3. Only when the evidence gate is satisfied, a deterministic USDC2 balance below the requested units returns an error without one call to `eth_sendTransaction`; the modal remains open, `:submitting?` is false, the full inline error is visible, and the toast has a readable title, next action, and wrapped detail. The test asserts the exact resolved Testnet USDC2 address and normalized owner passed to the balance reader.

4. Only when the evidence gate is satisfied, a balance equal to or greater than the requested units sends the exact pre-existing ERC-20 transfer payload after chain confirmation and still returns the existing success shape after receipt confirmation. A rejected balance read does not label the user insufficient and does not prevent this existing send attempt.

5. Any failed Bridge2 response preserves the inline `Deposit failed:` message, stores it at `[:funding-ui :modal :error]`, clears `[:funding-ui :modal :submitting?]`, keeps the modal open, and passes a structured toast map through the existing three-argument callback. Existing string-only toast callers and USDT/USDH deposit error tests remain green.

6. At 375, 768, 1280, and 1440 pixel viewport widths, browser QA explicitly marks visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf passes as `PASS`, `FAIL`, or `BLOCKED`. The inline error and toast must not overlap controls, clip the full actionable text, create horizontal overflow, or prevent dismissal, focus, close, retry, and resize behavior. A missing deterministic browser injection is a documented `BLOCKED` browser-test gap, not permission to use a real wallet.

7. `npm run check`, `npm test`, and `npm run test:websocket` pass after code changes. No Mainnet configuration, deployment artifact, remote branch, wallet balance, transaction, browser persistence schema, or public API outside the narrowly compatible toast message shape changes.

## Idempotence and Recovery

All tests and local browser checks are repeatable because they use injected providers and simulated data. The optional balance query is read-only and is issued at most once per locally valid Bridge2 submission; it creates no wallet prompt or persistent state. If its test double fails, retry the test after confirming that the fallback path still sends no more than the original single transfer request.

Do not retry the reported failure by confirming a live wallet transaction. Do not open Mainnet, deploy, edit production/test configuration, push, or alter browser storage for this plan. If later live diagnosis becomes necessary, stop and request explicit authorization, then capture only the minimum redacted evidence required to update this plan. A failed implementation is recovered by reverting only the narrow unaccepted source/test changes and retaining this plan's evidence and decision log; no funds or external resources need rollback.

## Artifacts and Notes

The durable failure report is:

    Deposit failed: RPC 0x66eee Custom e...

The relevant current Testnet configuration is:

    chain: Arbitrum Sepolia (`0x66eee`)
    USDC2: 0x1baAbB04529D43a73232B713C0FE471f7c7334d5
    Bridge2: 0x08cfc1B6b2dCF36A1480b99353A354AA8AC56f89

The prior plan records historical evidence that a connected Testnet account had zero USDC2 after legacy-token transfers. That is useful context, but it is not sufficient by itself to add a new preflight for every user; the diagnostic evidence gate in this plan remains mandatory.

## Interfaces and Dependencies

Use the existing `hyperopen.funding.infrastructure.erc20-rpc/read-erc20-balance-units!` implementation through the existing `hyperopen.funding.effects.transport-runtime/read-erc20-balance-units!` wrapper. Do not introduce another RPC client, token ABI encoder, persistence key, or external dependency.

The final Bridge2 submission dependency map has this additional entry only if the evidence gate is satisfied:

    :read-erc20-balance-units!
    ;; (fn [provider token-address owner-address] -> Promise<bigint>)

The classifier introduced for this plan has the stable local contract described in Plan of Work:

    hyperopen.funding.effects.common/deposit-wallet-error-feedback
    ;; error object or map -> {:kind keyword :message string :toast map}

`hyperopen.funding.effects/set-funding-submit-error!` must remain compatible with existing callers that pass a string. Its additional accepted value is:

    {:message "Deposit failed: <safe full explanation>"
     :toast {:headline "Deposit could not be submitted"
             :subline "<short next action>"
             :detail "<safe wrapped explanation>"
             :auto-timeout? false}}

`show-toast!` remains `(fn [store kind message])`. The existing `hyperopen.order.feedback-runtime/show-order-feedback-toast!` map handling stores the structured fields, and `hyperopen.views.notifications-view/notifications-view` renders `:detail` as wrapped text. No caller outside the funding path needs a signature change.

Revision note: created on 2026-08-08 from the direct Testnet deposit failure report. It deliberately keeps insufficient-USDC2 preflight conditional until deterministic diagnosis proves it is the causal failure class.
