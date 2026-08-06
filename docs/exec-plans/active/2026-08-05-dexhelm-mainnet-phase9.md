# Phase 9: Open DEXHelm Mainnet after explicit authorization

This ExecPlan is a living document and must be maintained according to `/Users/zh/Documents/Hyperopen/.agents/PLANS.md`. It is the release record for the separately authorized Mainnet opening phase. The parent readiness record is [`docs/exec-plans/active/2026-08-05-dexhelm-mainnet-opening.md`](2026-08-05-dexhelm-mainnet-opening.md).

## Purpose / Big Picture

After Testnet fund-flow acceptance and the signed production-readiness gate, this phase publishes the reviewed DEXHelm release that changes only the Mainnet terminal from an intentional HTTP 503 to a Mainnet-forced trading surface. Users should be able to open `https://app.dexhelm.com`, see an explicit Mainnet context, and use the fixed Mainnet same-origin proxy. `https://testnet.dexhelm.com` must continue to force Testnet. This phase proves technical availability only; it does not authorize any wallet connection, signature, deposit, withdrawal, transfer, or order with real funds.

## Context References

Public refs:

- Direct maintainer request in the current session: open formal trading only after governed readiness and explicit authorization.

Repo artifacts:

- Parent readiness plan: `docs/exec-plans/active/2026-08-05-dexhelm-mainnet-opening.md`
- Cloudflare deployment skill: `.agents/skills/deploy-hyperopen-cloudflare/SKILL.md`
- Mainnet runbook: `.agents/skills/deploy-hyperopen-cloudflare/references/zero-to-live.md`
- Worker policy: `workers/hyperopen-worker.mjs`
- Production config (must remain unchanged until this phase is authorized): `wrangler.jsonc`
- Reviewed candidate config: `wrangler.mainnet-opening.jsonc`
- Candidate builder: `tools/cloudflare/build_dexhelm_mainnet_opening_candidate.mjs`
- Dedicated browser contract: `tools/playwright/test/dexhelm-mainnet-opening.spec.mjs`

## Progress

- [x] (2026-08-05) Mainnet/Testnet candidate implementation exists with explicit network context, fixed upstream mapping, host isolation, and a Mainnet enablement gate.
- [x] (2026-08-05) Dedicated four-viewport Mainnet/Testnet Playwright contract passed 8/8; repository gates passed 35/35; candidate Wrangler dry-run passed without upload.
- [x] (2026-08-05) Re-ran the deterministic Cloudflare Worker and release-asset suites with local-listen permission: Worker `45/45`, release assets `51/51`.
- [x] (2026-08-05) Rebuilt the Mainnet opening candidate with JDK 21; artifact XSS contract reported zero authored sinks, artifact preflight reported `33 passed / 0 failed`, and Wrangler dry-run read `116` assets with `HYPEROPEN_MAINNET_ENABLED="true"`, `HYPERUNIT_MAINNET_URL=https://api.hyperunit.xyz`, and `HYPERUNIT_TESTNET_URL=https://api.hyperunit-testnet.xyz`.
- [x] (2026-08-05) Read-only public verification confirmed apex `200`, Testnet `200`, Mainnet `503`, status `200`; Testnet health returned `{"status":"ok"}` and Mainnet included `cache-control: no-store`. No production mutation occurred.
- [x] (2026-08-05) Audited the public operator page and tenant configs: risk disclosure, source/AGPL links, and affiliate-unavailable disclosure are present; privacy-policy and terms-of-service materials remain unverified and are a Phase 8 blocker.
- [x] (2026-08-05) Confirmed the gap on the live apex page: a read-only scan found `AGPL`, `Risk`, and `Source`, but no `Privacy` or `Terms` link; Mainnet remains Cloudflare-served `503` with `no-store`.
- [x] (2026-08-05) Read-only Wrangler verification reconfirmed account `a95e39ff9f1a66e7630e6639a0edb86c`, Worker `hyperopen`, active version `f26e000b-532c-44cd-8de3-cd04aab40fb2` at 100%, and prior version `32112083-f3ac-4077-8138-5170efe4a7ca` as the immediately preceding deployment candidate for rollback review.
- [x] (2026-08-05) `wrangler versions view 32112083-f3ac-4077-8138-5170efe4a7ca --name hyperopen --json` resolved the rollback candidate as Worker version number `17`; no rollback mutation was performed.
- [x] (2026-08-05) Handoff gate tests passed `3/3`: complete evidence is recorded without sensitive fields, and any missing human gate rejects before probe or deployment calls.
- [x] (2026-08-05) Re-ran the repository gates with JDK 21 and an explicit Node localStorage path: `35/35` passed, `6619` tests, `36051` assertions; `npm test` reported `5865/0` and `npm run test:websocket` reported `561/0`.
- [x] (2026-08-05) Re-ran the dedicated four-viewport Playwright contract: `8/8` passed at `375`, `768`, `1280`, and `1440` px for both hosts, including forced network isolation and no wallet/signature/order requests; browser cleanup completed.
- [x] (2026-08-05) Reviewed the official Hyper Foundation Terms of Service and Privacy Policy as structural references; DEXHelm-specific legal text and links remain required and must receive legal-owner approval.
- [x] (2026-08-05) Received explicit current-session authorization for Phase 8/9 operations and Phase 9 publishing after readiness gates; no wallet confirmation or real-fund action was performed.
- [x] (2026-08-05) User confirmed the already-connected Testnet wallet as the disposable address and approved a total disposable ceiling of `10 USDC` equivalent before any funding or signing flow; no wallet confirmation has yet been automated.
- [x] (2026-08-05) Stopped on the initial wallet-state mismatch: the visible Testnet page showed approximately `977.07 USDC` and one existing position, so no order, close, transfer, withdrawal, or signature was attempted until the user separately expanded the authorization scope.
- [x] (2026-08-05) User expanded authorization to cover the current account's balance and existing position, then explicitly confirmed closing the BTC position. The UI showed a BTC 6x position of approximately `0.0023 BTC` with position value `148.26 USDC`; the `Close` control was exposed, but the agent did not submit an order or trigger a wallet confirmation.
- [x] (2026-08-05) Live Testnet verification now shows `0.0000 BTC`, `No active positions`, `No open orders`, and unrealized PNL `US$0.00`; the position state changed while the user controlled the browser, with no agent-submitted order.
- [x] (2026-08-05) User explicitly signed as the Phase 8 production owner; the signoff remains conditional on completing the listed operations, legal, monitoring, and Testnet-settlement evidence.
- [x] (2026-08-05) Validated `config/white-label/dexhelm-mainnet.json` for `https://app.dexhelm.com`; tenant routes `/trade` and `/portfolio` passed with digest `336D5A871993CE9122249AACDD6D5A2BC6A57114C1940B0689CEAA83E002BF68`.
- [x] (2026-08-05) Read-only Cloudflare identity, current version, rollback candidate, DNS, and pre-Phase-9 public host matrix were recorded in the parent plan.
- [x] (2026-08-06) Re-ran every independent Phase 9 technical check without upload: Worker `45/45`, release assets `51/51`, Mainnet/Testnet Playwright `8/8`, candidate build `106` artifacts with zero authored XSS sinks, preflight `33/0`, and Wrangler dry-run exit `0` with `116` assets and both fixed network bindings. Candidate manifest SHA-256 is `04e2c2597cfeedaace31e4e9b350de1e6e6a5e16f134e6121109bf7a5748d7a0`.
- [x] (2026-08-06) Revalidated the final candidate after the legal-check and cache-policy repairs without upload: `npm test` passed `5888/32843`, `npm run test:websocket` passed `561/3184`, `npm run check` and the aggregate `35/35` gate matrix passed; Worker `49/49`, release assets `52/52`, candidate build `106` artifacts with zero authored XSS sinks, and Wrangler dry-run read `116` assets with fixed Mainnet/Testnet origins. Current candidate manifest SHA-256 is `0d8b9b3e9d8c97658a4bce775d6c5fef39d53a03a8e588f27901d22022e09ca2`.
- [x] (2026-08-06) Re-ran the governed Mainnet-opening Playwright contract on the final candidate: all `8/8` Mainnet/Testnet cases passed at `375/768/1280/1440`, with forced network labels, exact immutable caching for the document-discovered fingerprinted CSS/JavaScript on both hosts, and no wallet, signature, funding, or order action. The candidate manifest remained `0d8b9b3e9d8c97658a4bce775d6c5fef39d53a03a8e588f27901d22022e09ca2` after the run.
- [x] (2026-08-06) Reconfirmed Worker version 18 (`f26e000b-532c-44cd-8de3-cd04aab40fb2`), prior version 17 (`32112083-f3ac-4077-8138-5170efe4a7ca`), public `200/200/503/200` host matrix, Testnet `200` health and fee probe, `404` cross-network/generic proxy boundaries, and SVG logo delivery. No production mutation occurred.
- [x] (2026-08-06) Completed the read-only rollback drill without mutation: the exact prior version 17 resolves with the expected Worker handler, Static Assets, Testnet-only HyperUnit binding, and security-header policy; the rehearsed public verification probe passed against the unchanged current deployment with `200/200/503/200/200`. A real rollback remains separately unauthorized.
- [x] (2026-08-06) Read-only Testnet history confirms the authorized BTC order round trip at `10.95 USDC` open and `10.93 USDC` close, with current `0.0000 BTC`, no active positions, and zero unrealized PNL.
- [x] (2026-08-06) Hyperliquid ledger history independently proves the authorized `1 USDC` Perps -> Spot -> Perps round trip through two opposite `accountClassTransfer` entries; final Spot USDC is `0.0`. These are internal ledger events and correctly have no Arbitrum transaction receipts.
- [ ] (2026-08-06) `BLOCKED`: two separately confirmed minimum `5 USDC` Testnet withdrawal attempts returned `Error withdrawing from bridge`. Neither attempt created a ledger update, Perps debit, payout transaction, or balance change; withdrawable balance remains `977.563573 USDC`. General Hyperliquid Testnet smoke passed, Bridge2 had ample USDC2, and the project's request/signing fields match the current official SDK. Do not retry until the upstream condition changes; Phase 9 still requires one accepted withdrawal plus Arbitrum Sepolia USDC2 delivery evidence.
- [ ] (2026-08-06) High-confidence root-cause lead: Hyperliquid `legalCheck` currently returns `restrictions: "a"` (`BlockActions`) with `acceptedTerms: true` and `userAllowed: true` for both the current account and the zero address. The official frontend maps this source-based restriction to a deposits/withdrawals block. DEXHelm lacked legal-check integration when the failed attempts reached MetaMask; it now fails closed before any signer call when the check is blocked or unavailable. Treat the upstream classification as a compliance-gating blocker until confirmed by Hyperliquid, not as permission to bypass the restriction.
- [x] (2026-08-06) Rechecked Hyperliquid's public `legalCheck` read-only endpoint for the zero address on both Testnet and Mainnet. Both still return `acceptedTerms: true`, `userAllowed: true`, `restrictions: "a"`; no withdrawal retry, signer call, or jurisdiction workaround was made.
- [x] (2026-08-06) Confirmed the local candidate contains the client-side fix for the withdrawal issue: the `legalCheck` `BlockActions` result produces a jurisdiction-blocked message and zero signer calls before `withdraw3`; the remaining inability to settle is upstream Hyperliquid eligibility, not a client-side signature or request-shape defect. This candidate fix is not deployed while Phase 8 remains blocked.
- [x] (2026-08-06) Maintainer confirmed that no legal reviewer, monitor alert recipient, incident owner/channel, independent outage path, or rollback owner is currently available. These remain explicit Phase 8 blockers; no names or approvals were invented.
- [ ] (2026-08-06) Phase 9 remains blocked by external readiness evidence and owner decisions, not the repaired technical candidate: withdrawal delivery is blocked as recorded above; DEXHelm-specific Privacy/Terms approval, a named monitor alert recipient and incident route, and an independent outage-communication path remain absent. Close All Playwright is `4/4`, the final-candidate Mainnet-opening Playwright contract is `8/8` with cache-policy assertions, governed browser QA is PASS, Worker tests are `49/49`, the read-only rollback drill is complete, the current branded candidate is `635,653` gzip bytes against the unchanged `640,000` target, and the repository matrix is `35/35`.
- [ ] Confirm the parent plan's Testnet fund-flow acceptance is complete and independently evidenced.
- [x] (2026-08-05) Obtained the user's Phase 8 production-owner signature. The signature remains conditional and does not convert the missing monitoring, compliance, Testnet settlement, or release-gate evidence into completed items.
- [x] (2026-08-05) Received explicit current-session authorization to perform Phase 8/9 operations and publish Phase 9 once all Phase 8 gates are satisfied; wallet and real-fund actions remain separately unauthorized.
- [ ] Publish the candidate, verify Mainnet availability and Testnet isolation, and record the new version and rollback version.
- [ ] Keep all real-fund Mainnet activity separately unauthorized unless the user provides a distinct action and amount approval.

## Surprises & Discoveries

- Observation: the checked-in production Worker is intentionally Testnet-only while the Mainnet candidate uses a separate Wrangler configuration.
  Evidence: `wrangler.jsonc` binds only the Testnet upstream; `wrangler.mainnet-opening.jsonc` binds both fixed upstreams and sets the explicit Mainnet enablement flag.
- Observation: the current public Mainnet host is expected to return `503` with `no-store` before this phase.
  Evidence: the parent plan's public matrix records apex `200`, Testnet `200`, Mainnet `503`, and status `200`.
- Observation: the prior release measured 653,917 gzip bytes against a 640,000-byte advisory target, but the current candidate is below the unchanged target.
  Evidence: a fresh same-workload rebuild on 2026-08-06 measured the DEXHelm branded and Mainnet candidate modules at 635,653 gzip bytes, leaving 4,347 bytes of headroom; no budget ratchet was made.
- Observation: Testnet fund-flow evidence now proves the internal Perps/Spot round trip, while external settlement remains incomplete because Bridge2 rejected both authorized minimum withdrawals before any ledger debit.
  Evidence: the parent plan records the two opposite `accountClassTransfer` updates, both identical upstream withdrawal errors, unchanged `977.563573 USDC` withdrawable balance, and the absence of a payout transaction. Mainnet must remain closed.
- Observation: Hyperliquid's public legal-check state explains why the official frontend would block this withdrawal before signing.
  Evidence: current Testnet and Mainnet `legalCheck` responses both contain `restrictions: "a"`; the official Testnet JavaScript defines `"a"` as `BlockActions` and renders deposits/withdrawals unavailable for that state. No jurisdiction workaround was attempted. DEXHelm now performs the same fail-closed check before deposit and withdrawal signer paths, with deterministic coverage proving blocked or unavailable results produce zero signer calls.

## Decision Log

- Decision: do not change `wrangler.jsonc`, attach routes, or publish while this plan is being prepared.
  Rationale: a Phase 9 plan and a current-session publish authorization are separate gates from implementation and Testnet approval.
  Date/Author: 2026-08-05 / Codex.
- Decision: technical Mainnet availability does not include real-fund testing.
  Rationale: deployment authorization must never be inferred as authorization for wallet actions, signatures, transfers, deposits, withdrawals, or orders.
  Date/Author: 2026-08-05 / Codex.

## Outcomes & Retrospective

At creation, this plan records a reviewed local candidate but no production mutation. Mainnet remains closed. Completion requires the pending human approvals, a successful publish of the reviewed candidate, and public evidence that Mainnet is open while Testnet remains forced to Testnet. The plan should be moved to `docs/exec-plans/completed/` only after those acceptance conditions are met.

## Context and Orientation

The Worker serves the DEXHelm static artifact and proxies fixed HyperUnit routes. `app.dexhelm.com` is the Mainnet terminal host, `testnet.dexhelm.com` is the Testnet terminal host, `dexhelm.com` is the product host, and `status.dexhelm.com` is the status host. The candidate release has separate Mainnet and Testnet upstream bindings and rejects generic or cross-network proxy paths. The source development defaults are not production authority; the selected tenant artifact and Worker host policy are.

## Plan of Work

First, freeze the candidate and confirm that the parent plan contains redacted Testnet API and chain evidence, the production owner checklist, and a verified rollback target. Resolve any discrepancy before requesting publish authorization.

Next, immediately before the external mutation, re-run the candidate build, artifact preflight, Wrangler dry-run, dedicated Mainnet opening Playwright contract, and full repository gates. Confirm the exact Cloudflare account and Worker identity with Wrangler. Record the authorization text and timestamp in this plan without recording credentials or wallet secrets.

After authorization, publish only the reviewed candidate. Capture Wrangler's exact Worker name and new version ID. Verify the returned deployment origin first, then verify the four custom domains, status and security headers, health endpoints, logo content type, canonical network parameters, same-network proxy behavior, and cross-network 404 boundaries.

If any public check fails, stop further changes, keep evidence, and request explicit rollback authorization for the exact prior version. A rollback restores the Worker release only; it cannot reverse wallet signatures, orders, transfers, or third-party incidents.

## Concrete Steps

Run from `/Users/zh/Documents/Hyperopen`.

Before authorization, run the safe validation order:

    npm run setup:worktree
    npm run test:cloudflare-worker
    npm run test:release-assets
    npm run test:playwright:mainnet-opening
    npm run build:cloudflare:mainnet-candidate
    node .agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs --artifact
    npm run cloudflare:check:mainnet-candidate
    env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH NODE_OPTIONS=--localstorage-file=/tmp/hyperopen-node-gates-localstorage npm run gates

Do not run a publish command until the Phase 8 signature and current-session Phase 9 authorization are both recorded. The authorized publish command is:

    npm run build:cloudflare:mainnet-candidate
    npx wrangler deploy --config wrangler.mainnet-opening.jsonc

Then run both repository verifiers against the returned deployment origin and the public four-host matrix. Expected post-release behavior is: apex `200`, Testnet terminal `200` with Testnet forced, Mainnet terminal `200` with Mainnet forced, status `200`, health `200` JSON with `no-store`, same-network proxy non-5xx JSON, and cross-network proxy `404`.

## Validation and Acceptance

This phase is accepted only when the Phase 8 checklist is signed, the current-session authorization is explicit, the publish updates the intended Worker, and the public matrix proves the expected statuses, headers, network labels, URL canonicalization, proxy isolation, and logo delivery at all governed browser widths (`375`, `768`, `1280`, and `1440`). The browser contract must report 8/8 or better with no horizontal overflow and no wallet/signature/order request during smoke verification.

Acceptance does not include a Mainnet wallet connection or a real-fund order. Such actions require a new, separately scoped approval.

## Required Human Handoff Inputs

Before the publish command can be authorized, the production owner must provide a secret-free readiness record containing the following fields. Boolean fields are evidence-backed approvals, not declarations inferred from tests: `testnetSettlement`, `externalMonitor`, `incidentOwner`, `rollbackDrill`, and `phase8Signoff` must all be true. The current-session maintainer authorization is a separate `phase9Authorization` field and must be recorded with its timestamp and scope.

The record must also name the external monitor URL, incident channel, compliance state, affiliate state, and the decision to accept or remediate the known bundle-budget advisory overage. It must include the candidate, Testnet, and Mainnet artifact digests, the current Worker version, and the verified prior rollback version. It must not contain tokens, cookies, authorization headers, wallet addresses, signatures, or raw exchange response bodies.

The minimum user-provided Testnet input is a disposable public wallet address and a maximum disposable amount. The user must manually approve every wallet signature and captcha. Those inputs authorize only the Testnet acceptance flow and never imply Mainnet deployment or real-fund trading.

## Idempotence and Recovery

All pre-authorization checks are repeatable and must leave `app.dexhelm.com` closed. Do not retry an ambiguous external request. For a failed authorized release, identify the prior verified Worker version, obtain explicit rollback authorization, run Wrangler rollback without bypassing its confirmation, and rerun the full public matrix. Preserve the failed version ID and evidence for incident review.

## Artifacts and Notes

Record only secret-free evidence: source commit, candidate manifest digest, test and gate summaries, Cloudflare account/Worker identity, authorization timestamp, new version ID, rollback version ID, public status matrix, and browser QA artifact paths. Redact wallet addresses, cookies, signatures, authorization headers, and raw exchange response bodies.

## Interfaces and Dependencies

The phase depends on `workers/hyperopen-worker.mjs`, `wrangler.mainnet-opening.jsonc`, the candidate build adapter, the fixed Mainnet/Testnet HyperUnit origins, the dedicated Playwright contract, Wrangler, Cloudflare custom domains, and the parent plan's Testnet and production-readiness evidence. No new wallet automation, secret store, DNS mutation, or direct browser-to-HyperUnit origin is permitted in this phase.
