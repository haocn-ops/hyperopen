# Hyperopen Zero-to-Live Runbook

## Contents

- Scope and authority
- Required launch record
- Choose the launch track
- Phase 0: prepare the operator environment
- Phase 1: establish the source baseline
- Phase 2: bootstrap a branded tenant
- Phase 3: prepare Cloudflare and DNS
- Phase 4: validate the release candidate
- Phase 5: deploy without custom domains
- Phase 6: attach and verify custom domains
- Phase 7: complete Testnet trading acceptance
- Phase 8: pass the production-readiness gate
- Phase 9: enable Mainnet separately
- Rollback drill and recovery
- Operations and handoff
- Non-negotiable blockers
- Final acceptance checklist

## Scope and Authority

Use this runbook when taking Hyperopen from a fresh checkout to an operated Cloudflare Worker, adding a new branded tenant, or proving that another operator can reproduce the launch.

Treat these as separate approvals:

1. Editing source and release configuration.
2. Authenticating to Cloudflare.
3. Creating or changing a Worker, custom domain, or DNS record.
4. Publishing a Worker version.
5. Connecting a wallet or requesting a signature.
6. Moving Testnet funds or submitting a Testnet order.
7. Enabling Mainnet delivery.
8. Performing any Mainnet wallet or trading action.

One approval does not imply another. Never automate wallet-extension confirmations, seed phrases, private keys, captchas, or signatures. Pause for the user at every wallet confirmation. Keep Mainnet closed until Phase 8 is complete and the user separately authorizes Phase 9.

This repository is a non-custodial client. The Worker serves assets and proxies fixed HyperUnit routes; it must never receive wallet credentials or sign trading actions.

## Required Launch Record

Create or refresh an ExecPlan before changing a new tenant, Worker policy, or deployment pipeline. Record these non-secret inputs before implementation:

| Input | Required evidence |
|---|---|
| Source | Upstream URL, exact commit, branch or detached state, license, and initial worktree status |
| Launch track | `first-launch` or `existing-worker-update` |
| Cloudflare target | Account identity, zone name, Worker name, and whether `workers.dev` remains enabled |
| Tenant | Tenant id, brand name, theme, logo path, feature flags, venue, and affiliate state |
| Domains | Apex, Mainnet app, Testnet app, and status hostnames |
| Host policy | Expected status and network for every hostname |
| Release target | Tenant config, canonical origin, output directory, and `wrangler.jsonc` asset directory |
| Mainnet state | Closed by default; name the owner who may authorize opening it |
| Test wallet | Intended public address, maximum Testnet amounts, and user confirmation owner |
| Operations | Monitor provider, alert recipient, incident owner, and rollback version |
| Compliance | License/source link, risk disclosure owner, privacy/terms owner, and affiliate disclosure owner |

Do not record API tokens, cookies, private keys, seed phrases, raw signatures, browser storage, or full signed exchange bodies.

## Choose the Launch Track

### First Launch or New Tenant

Use this track when the Cloudflare Worker does not exist, the domain set changes ownership, or the release is for a tenant other than the checked-in DEXHelm tenant.

The current repository is not tenant-neutral at the Cloudflare boundary. `build:cloudflare`, the preflight, Worker host policy, tests, and `wrangler.jsonc` contain DEXHelm-specific contracts. Creating only `config/white-label/<tenant>.json` is insufficient.

Before editing, find every tenant-specific boundary:

```bash
rg -n 'DEXHelm|dexhelm|build_dexhelm|DEXHELM_' package.json wrangler.jsonc workers tools .agents/skills/deploy-hyperopen-cloudflare/scripts
```

Generalize those boundaries or replace them with the approved tenant values. Preserve public APIs unless the user requests otherwise. Add deterministic tests for the new asset directory, canonical origin, logo, custom-domain set, host policy, manifest digest refresh, and public verification.

### Existing Worker Update

Use this track when publishing a new version to an already configured Worker without changing tenant ownership or domains.

Record the current Worker name and recent versions before building:

```bash
npx wrangler whoami
npx wrangler versions list --name <worker-name> --json
```

Do not change `wrangler.jsonc` `name`, `routes`, or `assets.directory` unless that change is explicitly in scope. Capture the prior version id for rollback, prove the new deployment updated the same Worker, and verify the existing host-policy matrix afterward. Skip Phase 5 when the existing Worker and domains are already active; do not detach live routes merely to repeat first-launch staging.

## Phase 0: Prepare the Operator Environment

Require:

- Git and a clean destination directory.
- Node.js and npm compatible with `package-lock.json`.
- JDK 21, Clojure CLI, and Babashka.
- A Chromium-compatible Playwright browser.
- A Cloudflare account that owns or can manage the intended zone.
- Least-privilege Cloudflare Workers and zone permissions.
- A disposable Testnet wallet on Arbitrum Sepolia with small gas and current Testnet USDC2.
- Human owners for deployment, wallet confirmation, incident response, and production approval.

Check tools without printing credentials:

```bash
git --version
node --version
npm --version
java -version
clojure -Sdescribe
bb --version
npx wrangler --version
```

Authenticate interactively only when the user authorizes Cloudflare access:

```bash
npx wrangler login
npx wrangler whoami
```

For CI, keep the least-privilege token in the CI secret store. Never commit it, echo it, place it in tenant JSON, or write it into an ExecPlan.

Gate: every tool is available, the Cloudflare account and zone are the intended targets, and no secret appears in the repository or recorded command output.

## Phase 1: Establish the Source Baseline

Pin the approved source rather than a moving branch tip:

```bash
git clone https://github.com/thegeronimo/hyperopen.git hyperopen
cd hyperopen
git checkout --detach <approved-source-commit>
git remote get-url origin
git rev-parse HEAD
git status --short
sed -n '1,40p' LICENSE
```

Bootstrap and prove the inherited application before tenant or Cloudflare edits:

```bash
npm ci
clojure -P
npm run setup:worktree
npm run gates
```

Install the Playwright browser if the machine has not run the repository browser suite before:

```bash
npm run test:playwright:install
```

Gate: provenance is recorded and baseline failures are either absent or explicitly classified as inherited/environmental. Do not begin release adaptation on an unexplained failing baseline.

## Phase 2: Bootstrap a Branded Tenant

Use `config/white-label/example-enterprise.json` only as a schema example. Create `config/white-label/<tenant>.json` with:

- a stable letters/numbers/hyphens tenant id;
- the public brand name and same-origin HTTPS logo URL;
- one supported theme: `dark`, `institutional`, or `hyperdegen`;
- explicit terminal, analytics, and affiliate booleans;
- the supported Hyperliquid venue identity;
- an honest affiliate state and disclosure.

If official affiliate service is unavailable, set the feature false, use status `unavailable`, leave provider/id/referral URL empty, and disclose the limitation. Do not invent an affiliate id or tracking endpoint.

Place the logo under `resources/public/brand/` with stable dimensions and accessible use. Do not embed secrets, trackers, remote scripts, or wallet addresses in the asset.

Validate, build, and verify the isolated tenant artifact:

```bash
npm run white-label:validate -- --config config/white-label/<tenant>.json --origin https://<testnet-host>
npm run build:white-label -- --config config/white-label/<tenant>.json --origin https://<testnet-host> --output out/white-label/<tenant>
npm run verify:white-label -- --config config/white-label/<tenant>.json --origin https://<testnet-host> --output out/white-label/<tenant>
```

Adapt the Cloudflare release boundary through an ExecPlan and tests:

- Make `build:cloudflare` build the approved tenant, rewrite only that generated JavaScript, refresh `mainBundleDigest` and `artifactDigests`, and rerun white-label verification.
- Point `wrangler.jsonc` `assets.directory` to the same verified directory.
- Replace or parameterize DEXHelm-specific preflight assertions.
- Implement the exact apex, app, Testnet, and status host policy in the Worker.
- Keep the Mainnet app host closed with 503.
- Update Worker and release tests before changing production behavior.
- Verify every public brand surface: header, logo, banner, chart legend, title, metadata, and manifest.

Gate: two consecutive `build:cloudflare` runs succeed, the selected artifact verifies after rewriting, and tenant-specific search finds no accidental prior-brand public copy.

## Phase 3: Prepare Cloudflare and DNS

Confirm the domain is in the same intended Cloudflare account, the zone is active, and registrar nameserver delegation is complete. Read-only checks may include:

```bash
dig NS <apex-domain>
dig <apex-domain>
dig <testnet-host>
dig <app-host>
dig <status-host>
```

Define the host policy before adding routes. The recommended structure is:

| Host | Purpose | Pre-Mainnet result |
|---|---|---|
| `<apex-domain>` | Product, documentation, source/license link, and risk disclosure | 200 |
| `<testnet-host>` | Testnet trading terminal | 200 with forced Testnet network |
| `<app-host>` | Mainnet trading terminal | 503 and no Mainnet proxy/assets |
| `<status-host>` | Edge status and dependency caveat | 200 |

Use exact `custom_domain` routes in `wrangler.jsonc`. Do not use a wildcard that can capture unrelated hosts. Keep `workers_dev: true` for the first verification origin unless the owner explicitly disables it later.

Inventory existing DNS records before deployment. Resolve or explicitly preserve any conflicting record; do not overwrite an unrelated service because a hostname was selected for the launch.

The checked-in status page proves only that the same Worker edge responded. It is not an independent availability monitor. Configure an external monitor in Phase 8.

Gate: the account, zone, four exact hostnames, expected status matrix, and certificate/DNS ownership are documented. No DNS or custom-domain mutation occurs without explicit authorization.

## Phase 4: Validate the Release Candidate

Follow the safe order in `SKILL.md`. At minimum run:

```bash
npm run setup:worktree
npm run test:cloudflare-worker
npm run test:release-assets
npm run test:playwright:seo
npm run build:cloudflare
node .agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs --artifact
npm run cloudflare:check
npm run gates
```

For a branded tenant, run its white-label validator, verifier, and Playwright matrix at 375, 768, 1280, and 1440 px. Prove no horizontal overflow or logo/wordmark collision at 375 px. Derive public CSP image/connect expectations from `tenant-manifest.json`.

Start local Wrangler and run both public-contract verifiers before deployment. Stop the process and clean any Browser MCP sessions afterward.

Gate: deterministic tests, tenant verification, local Worker verification, dry-run, and all repository gates pass. Record advisory bundle-budget overage separately; do not silently change the budget.

## Phase 5: Deploy Without Custom Domains

For a first launch, validate the Worker origin before attaching customer-facing hostnames. Create a reviewed `wrangler.workers-dev.jsonc` beside `wrangler.jsonc` with the same Worker name, entry point, compatibility settings, variables, and authoritative asset directory, but no custom-domain routes. Extend the preflight with an explicit config-path option, add a deterministic check that the critical staging fields match the production config, and run preflight/tests against that staging config. Do not use the default `deploy:cloudflare` script for this staging publish because it reads `wrangler.jsonc`.

Immediately before publishing:

```bash
npx wrangler whoami
npm run build:cloudflare
npx wrangler deploy --config wrangler.workers-dev.jsonc
```

Capture the exact Worker name, `workers.dev` origin, and current version id printed by Wrangler. Verify that exact origin:

```bash
HYPEROPEN_VERIFY_ORIGIN=https://<returned-worker-origin> npm run verify:deployment-headers
HYPEROPEN_VERIFY_ORIGIN=https://<returned-worker-origin> npm run verify:cloudflare-worker
```

Do not infer the origin from the account name and do not continue if headers, assets, health, proxy boundaries, or tenant identity fail.

When this is the Worker's first version, there is no prior version to roll back to. Keep all custom domains unattached. If verification fails, preserve the evidence, fix the release locally, publish a corrected `workers.dev` version, and verify it before proceeding. Never use domain attachment as a first-version test.

Gate: knowing the `workers.dev` hostname is not proof of a correct release; the returned origin must render the intended tenant and pass both repository verifiers. Record this verified version as the first rollback candidate for custom-domain publication.

## Phase 6: Attach and Verify Custom Domains

Restore the reviewed exact custom-domain routes, rerun tests/preflight/dry-run, and obtain separate authorization for the domain mutation and publish. After deployment:

1. Confirm Cloudflare reports the custom domains active and certificates issued.
2. Resolve every hostname with `dig`.
3. Verify the full status matrix with `curl` or the repository verifier.
4. Verify the logo returns 200 with the expected image content type.
5. Open the Testnet trade route and prove the tenant identity and forced Testnet network.
6. Confirm the app/Mainnet host remains 503 for documents, assets, and proxy paths.
7. Record the new version id and prove it belongs to the intended Worker.

If a live trading page times out waiting for `Page.loadEventFired`, use `waitUntil: "commit"` plus a route-specific locator or inspect a DOM snapshot. Do not treat the readiness mismatch alone as a release failure. Preserve the artifact and clean up the inspection session.

Gate: DNS, TLS, all host statuses, logo content type, brand surfaces, security headers, health, and non-mutating proxy probes pass publicly.

## Phase 7: Complete Testnet Trading Acceptance

Read `/hyperopen/docs/runbooks/hyperliquid-testnet-implementation.md` completely and use its current contract addresses and verification commands. Do not duplicate remembered token or bridge addresses from an older release.

Obtain Sepolia gas and Testnet USDC2 only through currently documented official Hyperliquid, Arbitrum, or token-issuer channels. Reconfirm the full HTTPS hostname and current contract in official documentation before connecting a wallet. Treat third-party faucets as untrusted, and leave every captcha and wallet request to the user. Do not encode a faucet URL in this skill because availability and token contracts change.

Obtain explicit authorization for the wallet address and maximum Testnet amounts. Then complete, with manual wallet confirmation:

1. Open the explicit Testnet URL and connect the intended Arbitrum Sepolia wallet.
2. Match the page address to the wallet and verify the exact current USDC2 contract.
3. Deposit the minimum approved amount and verify `clearinghouseState`, not only the chain receipt.
4. Enable a fresh API-wallet trading session and verify it through `extraAgents`.
5. Place a minimal order, close or reduce it completely, and verify no position or open order remains.
6. Transfer a small amount Perps -> Spot -> Perps and verify both ledgers.
7. Withdraw the minimum approved amount and verify both the Perps debit and Arbitrum Sepolia ERC-20 delivery.
8. Record the requested amount, delivered amount, transaction hashes, and any fee discrepancy without recording signatures or secrets.

Stop rather than retry when the connected address changes, the page network is not explicit, a signature payload is unexpected, a withdrawal remains pending, or the amount/destination differs. A submitted message is not settlement evidence.

If the current Testnet token or gas source is unavailable, record this phase as `BLOCKED` with the exact missing prerequisite. Do not bypass the gate with an older same-symbol token, fabricated evidence, or Mainnet funds.

Gate: the complete Testnet flow passes, the account is flattened, open orders are empty, and external delivery is independently verified. A deployment can remain Testnet-only when this gate fails; it cannot progress to Mainnet readiness.

## Phase 8: Pass the Production-Readiness Gate

Keep Mainnet closed until every item has an owner and evidence:

- All release, Worker, browser, and Testnet trading acceptance gates pass on the current version.
- The Mainnet host policy and network selection have deterministic tests that prevent Testnet/Mainnet fallback mistakes.
- Risk disclosure covers leverage, liquidation, protocol, bridge, wallet, oracle, availability, execution, and irreversible-signature risks.
- Privacy and terms owners have reviewed analytics, logs, event endpoints, cookies, and jurisdictional obligations.
- AGPL license and source links remain public; counsel or the project owner has reviewed distribution/network-use obligations.
- Affiliate status and disclosure match an actually approved service. Unavailable service remains disabled.
- An external monitor checks the Worker health endpoint, apex, Testnet route, status route, and expected Mainnet 503 from outside Cloudflare.
- Alerts reach a named operator; the incident record distinguishes Worker, Hyperliquid, HyperUnit, wallet/RPC, DNS/TLS, and browser defects.
- Logs and analytics exclude wallet secrets, signatures, authorization headers, cookies, and sensitive browser storage.
- A known prior Worker version is recorded and a rollback drill has been completed without touching wallets or funds.
- Support and incident-response owners know how to publish an outage notice independent of the failed Worker.
- Bundle-budget overage and known product defects have explicit accept/reject owners.

Gate: the production owner signs off the checklist. Deployment completion, Testnet success, or a working domain does not implicitly satisfy this gate.

## Phase 9: Enable Mainnet Separately

Opening Mainnet is a product and risk-policy change, not a DNS toggle. Require a new ExecPlan, deterministic host/network tests, the full repository gates, a new release dry-run, and explicit current-session authorization.

Change the app/Mainnet host from closed 503 only after Phase 8 approval. Verify that Testnet remains forced to Testnet and app remains forced to Mainnet; never use an absent or malformed query parameter as the only safety boundary.

Publish and publicly verify the new version and host matrix. Do not connect a wallet, sign, transfer, deposit, withdraw, or place a Mainnet order unless the user separately authorizes that exact action and amount. A successful Mainnet page launch is sufficient technical acceptance; real-fund testing is not implied.

## Rollback Drill and Recovery

List recent versions and identify the last verified version:

```bash
npx wrangler versions list --name <worker-name> --json
```

Before a release, rehearse the read-only identification and verification steps. Do not perform the mutation during a drill.

For a real failed release, stop further changes, obtain explicit rollback authorization for the exact version, and run:

```bash
npx wrangler rollback <prior-version-id> --name <worker-name> --message "Rollback failed release <failed-version-id>"
```

Do not pass `--yes`; keep Wrangler's confirmation visible. After rollback, capture the resulting deployment evidence and rerun the Worker origin and entire custom-domain matrix. Rollback does not reverse external wallet signatures, orders, transfers, DNS changes outside the Worker deployment, or third-party incidents.

If rollback cannot restore service, keep Mainnet closed, publish an incident notice through an independent channel, preserve logs without secrets, and classify the failing layer before another deployment.

## Operations and Handoff

For every release, hand off:

- source commit and worktree state;
- tenant config digest and asset directory;
- Cloudflare account identity, zone, Worker name, returned origin, and version id;
- prior rollback version;
- exact commands and gate results;
- public host/status/TLS/header/logo matrix;
- Testnet transaction and API evidence with secrets removed;
- bundle-budget result and accepted known issues;
- external monitor URLs, last probe time, alert owner, and incident channel;
- Mainnet open/closed state and approving owner;
- compliance, affiliate, and source/license review state.

Schedule recurring read-only public checks and dependency review. Do not claim the in-Worker status page independently monitors the Worker. Re-run Testnet trading acceptance after signing, funding, network-selection, wallet-session, or withdrawal lifecycle changes.

## Non-Negotiable Blockers

Stop the launch and report `BLOCKED` when any of these remains unresolved:

- The source commit, license, target Cloudflare account, zone, Worker, or domain ownership is unknown.
- A baseline, preflight, artifact, browser, local Worker, dry-run, or governed gate failure is unexplained.
- A new tenant has changed only tenant JSON while DEXHelm-specific build, preflight, Worker, tests, routes, or verifier contracts remain.
- DNS conflicts, certificate issuance, security headers, fixed proxy boundaries, or the expected host matrix cannot be verified.
- The intended wallet address, selected network, signature payload, amount, destination, or user authorization is ambiguous.
- Current official Testnet gas or USDC2 prerequisites are unavailable.
- Testnet settlement is incomplete, positions or orders remain open, or a withdrawal is pending or discrepant without owner acceptance.
- Monitoring, incident response, independent outage communication, compliance review, production ownership, or a verified rollback candidate is missing.
- Mainnet opening lacks a separate approved ExecPlan and explicit current-session authorization.

Do not replace missing evidence with screenshots alone, stale documentation, older same-symbol tokens, fabricated results, a Mainnet transaction, or an unrelated Worker/domain response.

## Final Acceptance Checklist

- [ ] The launch record contains every required non-secret input and owner.
- [ ] The exact upstream commit and license are recorded.
- [ ] The correct launch track was used.
- [ ] A new tenant updated every hardcoded Cloudflare boundary, not only tenant JSON.
- [ ] Tenant validation, build, post-rewrite digest refresh, and verification pass.
- [ ] Cloudflare account, zone, Worker, DNS, and certificate targets are confirmed.
- [ ] Local verification, dry-run, and all repository gates pass.
- [ ] The returned Worker origin and version id are recorded and verified.
- [ ] Apex, Testnet, Mainnet app, status, logo, health, and proxy results match policy.
- [ ] Testnet connect, deposit, enable, order/close, transfer, and withdrawal pass with API/chain evidence.
- [ ] Wallet confirmations and captchas were handled only by the user.
- [ ] External monitoring, alerting, independent incident communication, and rollback ownership exist.
- [ ] Risk, privacy/terms, AGPL/source, affiliate, and known-issue reviews are recorded.
- [ ] A rollback version is known and the read-only drill is complete.
- [ ] Mainnet remains closed unless Phase 8 and Phase 9 were separately approved.
- [ ] Browser and local Worker sessions are stopped.
