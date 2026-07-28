# Temporarily close the DEXHelm Mainnet terminal

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. It follows `.agents/PLANS.md`.

## Purpose / Big Picture

The maintainer requested that real-value trading be unavailable while DEXHelm remains in a Testnet-only operating phase. After this change, every request to `https://app.dexhelm.com` receives a clear temporary-closure page with HTTP 503. The apex product page, status page, and Testnet terminal remain available, and neither static assets nor the HyperUnit proxy can be reached through the Mainnet host.

## Context References

Public refs:
- Direct maintainer request on 2026-07-25: "先把正式网入口关闭吧".

Repo artifacts:
- `workers/hyperopen-worker.mjs` owns exact-host delivery policy for the four DEXHelm custom domains.
- `tools/cloudflare/worker.test.mjs` is the deterministic Worker contract suite.
- `.agents/skills/deploy-hyperopen-cloudflare/SKILL.md` defines the release validation and publication process.

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-07-25 00:00+08:00) Identified the existing exact-host policy and confirmed `app.dexhelm.com` currently delegates both documents and assets to the Mainnet release.
- [x] (2026-07-25 18:20+08:00) Added a failing Worker contract proving the Mainnet host returns 503 and bypasses both static assets and proxy fetches; the RED result observed the prior 307 redirect.
- [x] (2026-07-25 18:30+08:00) Implemented the reversible Worker-only host closure and removed public Mainnet links from the apex and status documents.
- [x] (2026-07-25 18:55+08:00) Ran focused and release validation, published Worker version `9aa5c26b-d5e1-40bb-bb11-e8306e7a4af5`, and verified the public custom domains and browser-visible behavior.

## Surprises & Discoveries

- Observation: removing the `app.dexhelm.com` custom domain would unnecessarily force later DNS and certificate reprovisioning.
  Evidence: `wrangler.jsonc` binds all custom domains directly to the same `hyperopen` Worker, while `workers/hyperopen-worker.mjs` already has an exact-host routing layer.

## Decision Log

- Decision: serve an HTTP 503 closure document from the Worker instead of deleting the custom domain or returning an opaque error.
  Rationale: this blocks trading immediately while preserving TLS, DNS, and a reversible recovery path. HTTP 503 correctly signals temporary unavailability to users and intermediaries.
  Date/Author: 2026-07-25 / Codex.

## Outcomes & Retrospective

Mainnet access through `app.dexhelm.com` is closed at the first Worker routing branch. Public document, asset, and proxy probes all receive a no-store HTTP 503 response before any static or upstream call. The apex page now offers only Testnet, and the status page reports Mainnet as `SUSPENDED`. Testnet remains available with live market data. Worker version `9aa5c26b-d5e1-40bb-bb11-e8306e7a4af5` passed the two repository public verifiers, 34/34 repository gates, 6,541 tests, and 35,746 assertions. The implementation adds a small explicit host policy but reduces operational risk because a real-value terminal cannot be reached accidentally while DNS and TLS remain ready for a reviewed reopening.

## Context and Orientation

`workers/hyperopen-worker.mjs` receives every request before the Cloudflare Static Assets binding. `handleRequest` first evaluates hostname-specific product documents, then either delegates to static assets or proxies recognized `/api/hyperunit/**` requests. A new early exact-host branch for `app.dexhelm.com` can therefore prevent every Mainnet document, asset, and proxy request without altering the Testnet hostname or application source.

## Plan of Work

First, add a deterministic test that sends representative document, fingerprinted asset, and proxy paths to `app.dexhelm.com`. It must assert HTTP 503, `no-store`, the closure message, and zero calls to both the static-asset binding and injected upstream fetch. Then add a small static closure document and branch in `handleRequest` before the health and proxy routes. Remove the Mainnet action and status navigation link so the remaining public pages do not direct people to the disabled host. Finally, build and deploy the existing Worker pipeline and use direct public requests to confirm Mainnet is closed while Testnet still returns the terminal.

## Concrete Steps

Run from `/Users/zh/Documents/Hyperopen`:

    npm run setup:worktree
    npm run test:cloudflare-worker
    npm run test:release-assets
    npm run build:cloudflare
    node .agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs --artifact
    npm run cloudflare:check
    npx wrangler whoami
    npm run deploy:cloudflare

The focused Worker suite must initially fail on the new Mainnet-closure assertion, then pass after the host branch is added. Deployment verification will require `app.dexhelm.com` to return 503 and `testnet.dexhelm.com/trade?hyperliquidNetwork=testnet` to return 200.

## Validation and Acceptance

Acceptance requires all of the following observable behaviors:

- A browser navigation to `https://app.dexhelm.com/trade` receives HTTP 503 and a page that says the Mainnet terminal is temporarily unavailable.
- Requests to an asset and a HyperUnit path on `app.dexhelm.com` also receive HTTP 503, with no downstream static or upstream request.
- `https://dexhelm.com/`, `https://status.dexhelm.com/`, and `https://testnet.dexhelm.com/trade?hyperliquidNetwork=testnet` continue to respond successfully.
- The focused Worker suite, release-asset suite, Cloudflare artifact preflight, dry-run, and public verifiers pass.

## Idempotence and Recovery

The code and deployment commands are repeatable. The Worker retains the custom-domain bindings and therefore keeps certificates active. To reopen Mainnet later, remove the early exact-host closure branch, restore intentionally removed Mainnet links, rerun the same validation, and deploy a new version. Do not delete DNS records or the Worker. If the public verification fails after deployment, stop and request explicit rollback authorization for the preceding Worker version.

## Artifacts and Notes

The release will report its new version ID through Wrangler. The final record must include that version and public HTTP results without recording credentials.

The completed public checks returned:

    app.dexhelm.com/trade                                      503 text/html
    testnet.dexhelm.com/trade?hyperliquidNetwork=testnet      200
    dexhelm.com/                                               200 text/html
    status.dexhelm.com/                                        200 text/html

The browser title for Mainnet is `Mainnet unavailable | DEXHelm`; the Testnet browser title continued to show live BTC pricing.

## Interfaces and Dependencies

The only runtime interface changed is `handleRequest(request, env, options)` in `workers/hyperopen-worker.mjs`. It must return a standard Web `Response` with status 503 for the exact `app.dexhelm.com` hostname before calling `env.ASSETS.fetch` or the injected `fetchImpl`. No application source, wallet integration, DNS record, or Cloudflare secret changes are required.

Revision note: created on 2026-07-25 to record the maintainer-authorized reversible closure of the Mainnet entry.

Revision note: completed on 2026-07-25 after Worker version `9aa5c26b-d5e1-40bb-bb11-e8306e7a4af5` passed deterministic, release, public HTTP, and live-browser verification.
