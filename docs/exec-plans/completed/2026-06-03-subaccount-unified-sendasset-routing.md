# Subaccount Unified SendAsset Routing Fix

## Purpose

Direct maintainer report on 2026-06-03: Hyperopen subaccount token transfer failed live while the same transfer succeeded in Hyperliquid's UI.

Hyperopen signed:

- `type: "sendAsset"`
- `token: "USDC"`
- `sourceDex: ""`
- `destinationDex: ""`

Hyperliquid's successful request signed:

- `type: "sendAsset"`
- `token: "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"`
- `sourceDex: "spot"`
- `destinationDex: "spot"`

The exchange error, `Unified account only supports sending assets through spot`, matches the DEX-field mismatch.

## Context Reference

- Direct maintainer request in this Codex session on 2026-06-03, including the successful Hyperliquid request body and failed Hyperopen request body.
- Completed parent feature plan: `/hyperopen/docs/exec-plans/completed/2026-06-03-hyperliquid-subaccounts.md`.
- Completed transfer popover plan: `/hyperopen/docs/exec-plans/completed/2026-06-03-subaccount-transfer-popover-polish.md`.

## Execution Plan

- [x] Update unit regressions for unified subaccount deposit and withdrawal to require `sourceDex` and `destinationDex` to be `spot`.
- [x] Update unit regressions to require the full mainnet USDC token id string used by Hyperliquid's UI.
- [x] Update the deterministic Playwright transfer submission regression to assert the same action payload fields.
- [x] Run the RED regression and confirm it fails against the current empty DEX and bare token payload.
- [x] Patch `src/hyperopen/subaccounts/effects.cljs` so unified subaccount transfers build a spot-to-spot `sendAsset` action with the full USDC token id.
- [x] Run focused ClojureScript and Playwright regressions.
- [x] Run required gates: `npm run check`, `npm test`, `npm run test:websocket`, `git diff --check`, and `npm run browser:cleanup`.

## Scope

This plan intentionally does not change the wallet signer, exchange transport URL, `vaultAddress` behavior, or non-unified subaccount transfer paths. The provided live failure and exchange response are explained by the signed action fields.

## Progress

- [x] RED confirmed: `npm test -- --focus hyperopen.subaccounts.effects-test` failed because the expected spot-to-spot full-token action differed from the emitted empty DEX and bare `USDC` payload. The test runner does not honor `--focus`, so the command ran the full suite and also showed unrelated websocket invariant failures on that red run.
- [x] GREEN confirmed: `npm test` passed after patching the unified action builder.
- [x] Focused browser payload regression passed for `unified subaccounts transfer submits`.
- [x] Broader browser transfer slice passed after making the fixture write the exact spot balance path used by unified availability.
- [x] Required repo gates passed.

## Validation / Acceptance

- `npm test` must pass.
- `npx playwright test tools/playwright/test/subaccounts-regressions.spec.mjs --grep "unified subaccounts transfer submits"` must pass.
- `npx playwright test tools/playwright/test/subaccounts-regressions.spec.mjs --grep "subaccounts transfer"` must pass.
- `npm run check` must pass.
- `npm run test:websocket` must pass.
- `git diff --check` must pass.
- `npm run browser:cleanup` must pass.

## Surprises & Discoveries

- The previous regression encoded the live-broken payload shape, so it could pass while Hyperliquid rejected the transfer.
- Hyperopen's signed request already included `signatureChainId`, `hyperliquidChain`, and nonce via the user action signer; the failing behavior was localized to the `sendAsset` action fields.
- The browser fixture needed to write `[:spot :clearinghouse-state :balances]` directly because unified transfer max reads that production projection path.

## Decision Log

- Use `sendAsset` for unified subaccount transfers, not `subAccountTransfer`, because Hyperliquid rejects the L1 transfer path when unified account mode is active.
- Route unified `sendAsset` transfers through `sourceDex: "spot"` and `destinationDex: "spot"`, matching the successful Hyperliquid UI request and the exchange error.
- Use Hyperliquid's full mainnet USDC token id string for default unified USDC transfers: `USDC:0x6d1e7cde53ba9467b783cb7c530ce054`.
- Do not change exchange URL, signer, or `vaultAddress` behavior in this fix.

## Outcomes & Retrospective

- `npm test` passed: 4190 tests, 23249 assertions, 0 failures, 0 errors.
- `npx playwright test tools/playwright/test/subaccounts-regressions.spec.mjs --grep "unified subaccounts transfer submits"` passed: 1 test.
- `npx playwright test tools/playwright/test/subaccounts-regressions.spec.mjs --grep "subaccounts transfer"` passed: 3 tests.
- `npm run check` passed.
- `npm run test:websocket` passed: 531 tests, 3079 assertions, 0 failures, 0 errors.
- `git diff --check` passed.
- `npm run browser:cleanup` passed.
- The unified subaccount transfer UI now signs the same material `sendAsset` fields as the successful Hyperliquid request: `sourceDex: "spot"`, `destinationDex: "spot"`, and `token: "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"`.
