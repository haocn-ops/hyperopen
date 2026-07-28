---
name: deploy-hyperopen-cloudflare
description: Safely bootstrap Hyperopen from a fresh upstream checkout, create a branded tenant, prepare Cloudflare and DNS, validate Testnet trading, assess production readiness, build, dry-run, deploy, verify, operate, and recover its Workers Static Assets release and same-origin HyperUnit proxy. Use for first launches, new white-label tenants, existing Worker updates, workers.dev or custom-domain publication, Mainnet opening gates, wrangler.jsonc or Worker/release changes, Cloudflare build/header/proxy failures, release validation, public verification, rollback, or launch handoff.
---

# Deploy Hyperopen to Cloudflare

Operate the repository-owned Cloudflare pipeline without changing ordinary local-development endpoint behavior. Treat deploy and rollback as explicit external mutations; never infer either from a request to inspect, build, or verify.

## Read First

- `/hyperopen/AGENTS.md`
- `/hyperopen/docs/PLANS.md` for significant implementation or deployment-pipeline changes
- `/hyperopen/docs/BROWSER_TESTING.md` before release Playwright work
- `references/zero-to-live.md` completely for a first launch, new tenant, custom-domain bootstrap, Testnet trading acceptance, Mainnet readiness, or operator handoff
- `references/retrospective.md` when starting from upstream source, diagnosing a failure, or reviewing why the workflow has these gates

Run every command from the Hyperopen repository root.

## Choose the Launch Track

- **First launch or new tenant**: follow `references/zero-to-live.md` from Phase 0. Treat the current DEXHelm build adapter, preflight, Worker copy/host policy, tests, and Wrangler routes as tenant-specific code that must be generalized or replaced through an ExecPlan and deterministic tests. Keep Mainnet closed.
- **Existing Worker update**: record the configured Worker name, current account, routes, asset directory, and prior verified version before building. Preserve them unless the user explicitly scopes a change. Prove the deployment created a new version of that same Worker.
- **Testnet trading acceptance**: also read `/hyperopen/docs/runbooks/hyperliquid-testnet-implementation.md` completely. Obtain separate approval for the wallet address and maximum Testnet amounts. Leave every wallet confirmation, signature, and captcha to the user.
- **Mainnet opening**: require the production-readiness and Mainnet phases in `references/zero-to-live.md`, a new ExecPlan, full gates, and explicit authorization. Deployment or Testnet authorization never implies permission for Mainnet delivery or real-fund actions.

Do not claim a zero-to-live launch is complete at the first successful deploy. Completion requires public host verification, Testnet functional acceptance, monitoring/incident ownership, a rollback version and drill, license/risk/privacy/affiliate review state, and an explicit Mainnet open/closed record.

## Establish the Open-Source Baseline

When the user explicitly authorizes obtaining a new upstream checkout, clone the canonical public repository and pin the approved source commit before editing:

```bash
git clone https://github.com/thegeronimo/hyperopen.git hyperopen
cd hyperopen
git checkout --detach <approved-source-commit>
```

Do not use a moving branch tip as the recorded baseline. Do not fetch, pull, push, or replace the current working tree merely because this skill was invoked.

Before changing a fresh or upstream-derived tree, capture provenance instead of relying on a mutable branch name:

```bash
git remote get-url origin
git branch --show-current
git rev-parse HEAD
git status --short
sed -n '1,40p' LICENSE
```

Record the exact upstream URL, commit, worktree state, and license in the ExecPlan. If `HEAD` is missing or the files are untracked, record that provenance gap and obtain the original archive or commit from the user when exact source attribution matters. Never substitute the current upstream HEAD for an unknown historical baseline.

Preserve `LICENSE` and existing copyright or attribution notices. Record any license-compliance follow-up for a modified public deployment; this workflow does not grant permission to remove notices or relicense the upstream work.

Bootstrap a fresh checkout according to the repository README, then prove the inherited application before adding Cloudflare behavior:

```bash
npm run setup:worktree
```

If the guard cannot reuse an existing dependency tree, install the fresh-checkout prerequisites, rerun the guard, and then run the baseline gates:

```bash
npm ci
clojure -P
npm run setup:worktree
npm run gates
```

If a full baseline gate cannot run, record the environmental blocker and run the smallest available build and release checks. Do not attribute a pre-existing failure to the Cloudflare change.

Inventory these inherited seams before designing the deployment:

- the ordinary `npm run build` path, the generic `out/release-public` artifact, and any tenant-specific `out/white-label/<tenant>` artifact selected by `wrangler.jsonc`
- route-specific HTML, release metadata, `_headers`, and their verifiers
- the local HyperUnit proxy route meanings
- direct browser mainnet and testnet defaults under `/src/**`
- existing deterministic Node, Playwright, ClojureScript, and websocket gates

Reuse those contracts. Add Cloudflare as an opt-in release adapter around the existing artifact; do not fork the application architecture or silently change local development behavior. See `references/retrospective.md` for the original source-to-deployment transformation map and its provenance limits.

## Select the Operation

- **Inspect or diagnose**: run only read-only checks and the preflight. Explain the cause; do not deploy or edit unless requested.
- **Change the pipeline**: create or refresh an ExecPlan, add deterministic tests, implement the smallest change, and run every relevant gate below.
- **Dry-run a release**: build and run `cloudflare:check`; do not call `wrangler deploy` without `--dry-run`.
- **Deploy**: proceed only when the current user request explicitly authorizes publishing. Capture the exact account, URL, and version ID.
- **Launch or hand off**: complete the zero-to-live launch record, Testnet acceptance, production-readiness decision, monitoring ownership, and rollback drill; do not equate a successful upload with operational readiness.
- **Verify an existing deployment**: require an exact HTTP(S) origin and run the two repository verifiers. Do not redeploy.
- **Rollback**: require explicit rollback authorization and an exact prior version ID. Verify the rolled-back public origin afterward.

## Preserve These Invariants

- Keep normal `npm run dev` source defaults direct. Do not edit `/hyperopen/src/**` merely to activate the production proxy.
- Treat `wrangler.jsonc` `assets.directory` as the authoritative upload root. It may select the generic `out/release-public` artifact or a verified `out/white-label/<tenant>` artifact.
- Rewrite only generated JavaScript inside that authoritative asset directory through `build:cloudflare`; never rewrite `/src/**` or a different tenant's artifact.
- When rewriting a white-label artifact changes bundle bytes, refresh `tenant-manifest.json` `mainBundleDigest` and `artifactDigests`, then rerun white-label verification before upload.
- Keep the current proxy target fixed to the configured HTTPS Testnet origin. Never accept a caller-selected host.
- Reject generic, Mainnet, and lookalike paths such as `mainnetx`. Adding a Mainnet target requires the separately authorized Mainnet-opening plan.
- Forward only the request and response header allowlists already encoded in `workers/hyperopen-worker.mjs`. Never forward cookies, authorization, host, connection, or content-length.
- Preserve upstream response streaming and return a generic JSON 502 without internal error details when the upstream fetch rejects.
- Keep static delivery selective by default. Worker-first paths are `/api/health` and `/api/hyperunit/*` unless an approved exact-host policy must run before document assets; that exception requires preflight validation of the exact custom domains, immediate `ASSETS` delegation for unowned requests, and `_headers` compatibility verification.
- Never store Cloudflare credentials, tokens, cookies, or response bodies in the repository, logs, ExecPlan, or final report.
- Never configure a custom domain, DNS route, secret, or remote Git operation unless the user explicitly adds it to scope.
- Never automate wallet-extension confirmations, signatures, captchas, deposits, withdrawals, transfers, or orders. Treat each live wallet action as separate from deployment authorization and stop on any address, network, amount, destination, or payload mismatch.
- Keep a new Mainnet hostname closed until the production owner separately approves the readiness gate and the Mainnet-opening release.

## Run Preflight

Run the repository-contract and environment check before build or deploy:

```bash
node .agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs
```

After `build:cloudflare`, include the artifact checks:

```bash
node .agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs --artifact
```

Resolve every `FAIL`. Record `WARN` items and apply the printed environment command when relevant. The preflight is read-only and never authenticates or deploys.

## Change the Pipeline

Use test-first contracts for Worker routing, endpoint rewriting, and public verification:

```bash
npm run setup:worktree
npm run test:cloudflare-worker
npm run test:release-assets
```

When changing release generation, prove two consecutive release builds work. Do not mask a loader-parser defect by deleting Shadow caches. The release generator must recognize legitimate already-rewritten loader output and still fail closed on unrelated source.

Keep tests deterministic with fake `fetchImpl` and `ASSETS` bindings. Cover exact upstream mapping, query preservation, method/body forwarding, header filtering, response streaming, generic 502 behavior, static delegation, prefix-boundary rejection, atomic rewriting, repeated-run rejection, and symlink rejection.

For a tenant-branded release, preserve the full state-propagation boundary. A tenant-aware leaf view-model is not sufficient when a memoized parent projection can omit `:tenant/override`. Add regression coverage at both the leaf view-model and parent projection boundaries. Public browser assertions must prove the same tenant brand reaches the header logo and wordmark, product-context banner, chart legend, and document title.

Derive branded CSP expectations from the public `tenant-manifest.json`. The manifest's logo and event endpoints determine the expected image and connect origins; do not hardcode tenant domains in the public header verifier or trust only the local tenant config.

Brand controls must not flex-shrink into one another. At 375 px, assert computed geometry satisfies `logoRight <= wordmarkLeft`, and assert the document and header scroll widths do not exceed their client widths. Retain the governed 375, 768, 1280, and 1440 px browser matrix.

## Validate in the Safe Order

The order matters because Playwright commands may invoke a release build and replace or invalidate a previously rewritten artifact.

1. Run deterministic Node and release-asset tests.
2. Run the relevant generic or white-label Playwright suite against its normal generated artifact.
3. Rebuild the exact asset directory selected by `wrangler.jsonc` after Playwright.
4. Validate the artifact and local Wrangler behavior.
5. Run Wrangler dry-run and repository gates.

Commands:

```bash
npm run test:cloudflare-worker
npm run test:release-assets
npm run test:playwright:seo
npm run build:cloudflare
node .agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs --artifact
npm run cloudflare:check
```

For a checked-in white-label tenant, also run its validator and verifier and the tenant-aware Playwright suite. For the current DEXHelm target:

```bash
npm run white-label:validate -- --config config/white-label/dexhelm.json --origin https://testnet.dexhelm.com
npm run verify:white-label -- --config config/white-label/dexhelm.json --origin https://testnet.dexhelm.com --output out/white-label/dexhelm
PLAYWRIGHT_WHITE_LABEL_ROOT=out/white-label/dexhelm HYPEROPEN_EXPECT_BRAND=DEXHelm HYPEROPEN_EXPECT_TENANT_ID=dexhelm HYPEROPEN_EXPECT_ORIGIN=https://testnet.dexhelm.com HYPEROPEN_EXPECT_ENABLED_ROUTE=/trade HYPEROPEN_EXPECT_LOGO_URL=https://testnet.dexhelm.com/brand/dexhelm-mark.svg npm run test:playwright:white-label
```

`build:cloudflare` must report at least one Mainnet-to-disabled-sentinel rewrite and one Testnet-to-proxy rewrite and, for white-label output, a successful post-rewrite manifest verification. Confirm the selected release JavaScript has no direct HyperUnit origin or Mainnet proxy base while source retains its development defaults. Use the actual `assets.directory`; these examples show the current DEXHelm target:

```bash
rg -n 'https://api\.hyperunit' out/white-label/dexhelm/js --glob '*.js'
rg -n 'https://api\.hyperunit' src
```

The first command must have no matches. The second must retain the intentional source defaults.

### Environment Corrections

Use a real JDK 21. On the known macOS workstation, Homebrew provides it even when `java_home` does not:

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH npm run build:cloudflare
```

Node 25 may expose `localStorage` without `getItem` or `setItem`. If tests report that exact condition, rerun gates with an explicit temporary storage path:

```bash
env NODE_OPTIONS=--localstorage-file=/tmp/hyperopen-node-gates-localstorage npm run gates
```

Treat localhost `listen EPERM` as sandbox policy only after the stack points to a loopback test fixture. Request the required localhost permission and rerun the same command; do not weaken the test.

Wrangler can emit an `EPERM` warning while trying to create its local log file in a sandbox, yet still complete `--dry-run` and exit 0. Judge the dry-run by its exit code and deployment transcript. Record the warning, but do not classify it as a release failure when asset preparation and dry-run completion succeeded.

## Validate Local Wrangler

Start a freshly rebuilt Worker on a free port:

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH npm run cloudflare:dev -- --port 8790
```

From another shell, verify the static and proxy contracts:

```bash
HYPEROPEN_VERIFY_ORIGIN=http://127.0.0.1:8790 npm run verify:deployment-headers
HYPEROPEN_VERIFY_ORIGIN=http://127.0.0.1:8790 npm run verify:cloudflare-worker
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8790/trade
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8790/this-route-must-not-exist
curl -sS http://127.0.0.1:8790/api/health
```

Require `/trade` 200, the unknown route 404, health 200 JSON with `no-store`, document security headers, immutable caching on fingerprinted assets, non-5xx JSON from the Testnet fee probe, and 404 from the Mainnet and generic proxy probes. Confirm Wrangler logs that `_headers` rules were parsed. If `_headers` is not applied, stop: add and test a Worker-first static header adapter before deployment.

Stop the local Wrangler process cleanly before continuing. Let Playwright exit on its own and run `npm run browser:cleanup` only if a Browser MCP or browser-inspection session was created.

## Run Repository Gates

Run the governed matrix, applying the JDK and Node corrections only when the environment requires them:

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH NODE_OPTIONS=--localstorage-file=/tmp/hyperopen-node-gates-localstorage npm run gates
```

Require 34/34 gates. A soft bundle-budget warning is not a test failure, but record its byte delta. Never label environmental Java, sandbox bind, or Node localStorage failures as code defects without reproducing them in the corrected environment.

## Deploy

Immediately before the external mutation, confirm the account and Workers write permission:

```bash
npx wrangler whoami
```

Then run the repository deploy command, which rebuilds the Cloudflare artifact before uploading:

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH npm run deploy:cloudflare
```

Capture the exact configured custom-domain targets and `Current Version ID` from Wrangler. `workers_dev=false` must remain in effect; do not infer or reuse an old `workers.dev` URL from documentation.

## Verify Publicly

Use the exact returned origin:

```bash
HYPEROPEN_VERIFY_ORIGIN=https://<verified-testnet-origin> npm run verify:deployment-headers
HYPEROPEN_VERIFY_ORIGIN=https://<verified-testnet-origin> npm run verify:cloudflare-worker
```

The proxy verifier intentionally calls the non-mutating Testnet fee endpoint, proves the Mainnet and generic paths are closed, and never prints response bodies. Also confirm `/trade` contains its route-specific title and `/api/health` returns 200 JSON.

For custom domains, derive the expected host policy from the Worker and verify the entire matrix, not only the trading origin. The current DEXHelm policy requires:

- `https://dexhelm.com` returns 200.
- `https://testnet.dexhelm.com` and its enabled trade route return 200.
- `https://app.dexhelm.com` returns the intentional mainnet-closed 503.
- `https://status.dexhelm.com` returns 200.
- The public logo returns 200 with `Content-Type: image/svg+xml`.

Confirm Wrangler deployed the same configured Worker name, then capture the newly returned version ID. A successful custom-domain response without matching Worker identity and version evidence is incomplete deployment evidence.

Live trading pages keep long-lived connections. `browser:inspect` may time out waiting for `Page.loadEventFired` even when the page is usable; do not classify that timeout alone as a release failure. Fall back to a DOM snapshot, a route-specific visible anchor, or navigation with `waitUntil: "commit"` followed by locator readiness. Record the failed inspection artifact or session, then stop it with the specific session command or `npm run browser:cleanup`.

If a public fee probe returns 5xx, distinguish Worker failure from HyperUnit upstream unavailability. Do not expose upstream error bodies. If static headers or routes fail after deployment, stop further changes, identify the prior version, and request rollback authorization rather than deleting the Worker.

## Complete the Record

Update the active ExecPlan throughout implementation. After every acceptance criterion passes, record the final URL, version ID, commands, gate matrix, `_headers` compatibility result, operational owners, rollback version, Mainnet state, and residual risks, then move the plan from `active` to `completed`.

Return:

- public Worker URL and version ID
- configured Worker name and whether the new version updated that same Worker
- changed files
- exact build, test, dry-run, deploy, and verification commands
- validation outcomes and any environment corrections
- whether Workers Static Assets accepted `_headers` or required fallback logic
- custom-domain status matrix and branded surface/browser results when applicable
- Testnet functional-acceptance result and evidence boundaries when launch scope includes trading
- monitoring, incident, rollback, license/risk/privacy/affiliate, and Mainnet readiness state for zero-to-live work
- remaining risks, including upstream availability, custom-domain scope, and bundle-budget warnings
