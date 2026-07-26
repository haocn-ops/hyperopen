# Hyperliquid Subaccounts Live Parity and Smoke Health

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Hyperopen now has deterministic local support for Hyperliquid subaccounts, including account selection and `vaultAddress` routing for simulated order-like actions. The remaining work needs either a connected Hyperliquid wallet or separate smoke-suite triage outside the subaccount feature. A future contributor should be able to resume this plan to verify live protocol details, decide whether additional subaccount actions can be safely enabled, and clean up unrelated optimizer smoke failures without reopening the completed implementation plan.

## Context References

- Completed parent plan: `/hyperopen/docs/exec-plans/completed/2026-06-03-hyperliquid-subaccounts.md`.
- Direct maintainer request on 2026-06-03: address uncompleted active-plan items after the first subaccounts implementation commit.
- Official Hyperliquid route needing connected-wallet parity: `https://app.hyperliquid.xyz/subAccounts`.
- Browser routing contract: `/hyperopen/docs/BROWSER_TESTING.md`.

## Progress

- [x] 2026-06-03 - Moved live/testnet subaccount parity and unrelated smoke-health items out of the active implementation plan after deterministic local selector/order-payload coverage passed.
- [x] 2026-06-03 - Ran `npm run test:playwright:smoke`; subaccount route and selector smokes passed, and the suite ended with 35 passed and 3 optimizer-specific failures.
- [ ] Use Browser MCP with a connected or testnet-compatible wallet to inspect Hyperliquid's `/subAccounts` UI, including create, rename, perp transfer, and available spot transfer flows.
- [ ] Capture exact successful and failed exchange response bodies for `createSubAccount`, `subAccountModify`, `subAccountTransfer`, and `subAccountSpotTransfer`.
- [ ] Verify whether current Hyperliquid requires API-wallet approval per subaccount or accepts master-approved API-wallet signing with `vaultAddress` as implemented.
- [ ] Decide, with live evidence, whether spot transfers, staking, vault deposit/withdraw, bridge withdraw, and validator actions should be enabled, blocked, or owner-only while a subaccount is selected.
- [ ] Triage unrelated optimizer smoke failures from the 2026-06-03 full-smoke run and either fix them under an optimizer-specific plan or move them to contributor-visible issue tracking.

## Surprises & Discoveries

- Observation: The restricted unauthenticated Hyperliquid session did not emit useful `subAccounts` network traffic, so connected-wallet parity remains unavailable from the local deterministic browser harness alone.
  Evidence: The completed parent plan records that the live app showed the route shell but not connected-wallet action bodies.

- Observation: The current full smoke-suite failures are optimizer-specific, not subaccount-specific.
  Evidence: `npm run test:playwright:smoke` on 2026-06-03 passed the new selector/order-payload smoke and both desktop and mobile `/subAccounts` route smokes, then failed only these specs: `portfolio optimizer draft allocation row can be excluded and rerun`, `portfolio optimizer draft objective menu captures use my views returns and confidence`, and `portfolio optimizer draft add asset selector stays contained and focused across viewports`.

## Decision Log

- Decision: Keep the deterministic subaccounts implementation completed and do not keep live/testnet parity as an active-plan blocker.
  Rationale: Live parity depends on wallet/testnet access and protocol state that is not available in the local harness. The active implementation work has deterministic unit, integration, websocket, and Playwright proof.
  Date/Author: 2026-06-03 / Codex

- Decision: Treat optimizer smoke failures as unrelated smoke-suite health work, not as subaccount implementation scope.
  Rationale: The failing specs are all under `tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs`; subaccount route and selector tests passed in the same run.
  Date/Author: 2026-06-03 / Codex

## Plan Of Work

### M1 - Connected Hyperliquid Parity

Use Browser MCP only when a connected wallet or testnet-compatible environment is available. Navigate to `https://app.hyperliquid.xyz/subAccounts`, observe the current UI labels and disabled states, and capture network payloads for successful and failed create, rename, perp transfer, and spot transfer attempts. Stop Browser MCP sessions with `npm run browser:cleanup` before concluding the pass.

### M2 - Protocol Response and Action-Scope Decisions

Compare captured live responses against Hyperopen's current simulator fixtures and error handling. Confirm whether the implemented agent-wallet plus `vaultAddress` path is accepted for subaccount trading. For actions that are not ordinary `vaultAddress` order-like actions, decide the safest Hyperopen behavior: enable with explicit field encoding, keep owner-only, or block while a subaccount is selected with a clear message.

### M3 - Optimizer Smoke Health

Run the smallest failing optimizer smoke first, starting with:

    npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep "portfolio optimizer draft allocation row can be excluded and rerun"

Investigate each optimizer failure independently. If the failures are real product regressions, fix them under an optimizer-specific active plan. If they are environment-sensitive or stale assertions, update the optimizer smoke tests with evidence and rerun `npm run test:playwright:smoke`.

## Validation and Acceptance

This deferred plan is complete when:

- Browser MCP parity has captured connected-wallet Hyperliquid subaccount behavior or explicitly records why it remains blocked.
- Hyperopen has source-backed decisions for spot transfer and non-order subaccount-sensitive actions.
- The exact live response bodies needed for simulator fixtures are recorded.
- Optimizer smoke failures are fixed, moved to a dedicated active plan, or tracked in contributor-visible issue/PR notes.
- Any production-code changes made while resuming this deferred plan pass `npm run check`, `npm test`, `npm run test:websocket`, and the smallest relevant Playwright command before broader smoke.

## Outcomes & Retrospective

Deferred as non-active follow-up on 2026-06-03. The parent subaccounts implementation is complete for deterministic local behavior. This plan remains a parking place for work that needs live Hyperliquid access or unrelated optimizer smoke triage.
