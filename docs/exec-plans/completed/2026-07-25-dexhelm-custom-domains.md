# DEXHelm Custom Domains and Host-Specific Surfaces

This ExecPlan is a living document. Keep `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` current while the work proceeds.

## Purpose

Publish the existing Hyperopen Cloudflare Worker under the operator-owned `dexhelm.com` zone with four clear public surfaces: `dexhelm.com` for product information, operating documentation, and risk disclosure; `app.dexhelm.com` for the Mainnet terminal; `testnet.dexhelm.com` for the Testnet terminal; and `status.dexhelm.com` for a human-readable service status page. The Mainnet and Testnet hostnames must select their intended Hyperliquid network on every fresh document load without changing ordinary local-development defaults.

## Context References

- Direct user request in the active Codex task on 2026-07-25: bind operator-owned `dexhelm.com` using the recommended apex, Mainnet app, Testnet app, and status hostname structure and complete the Cloudflare deployment.
- Repository deployment baseline: `docs/exec-plans/completed/2026-07-21-cloudflare-worker-deployment.md`.
- Network-selection baseline: `docs/exec-plans/completed/2026-07-22-hyperliquid-testnet-network-default.md`.

## Progress

- [x] (2026-07-25 02:10Z) Confirmed Wrangler authentication, active Worker deployment, and Cloudflare account identity without exposing credentials.
- [x] (2026-07-25 02:16Z) Confirmed through the Cloudflare API that active zone `dexhelm.com` belongs to account `a95e39ff9f1a66e7630e6639a0edb86c`; public DNS delegates to `nina.ns.cloudflare.com` and `yisroel.ns.cloudflare.com`.
- [x] (2026-07-25 02:18Z) Confirmed the four requested hostnames have no existing public A or CNAME records.
- [x] (2026-07-25 02:41Z) Implemented deterministic host routing, operator/status HTML, focused tests, exact Wrangler Custom Domains, and a narrowly validated Worker-first preflight exception.
- [x] (2026-07-25 09:58Z) Ran focused Worker/release tests, release Playwright, Cloudflare artifact checks, dry-run, and the complete governed gate matrix.
- [x] (2026-07-25 10:00Z) Deployed Worker `hyperopen` version `169c1e6f-2ef8-49f9-87b6-82ff04fb0b8c` and bound all four requested Custom Domains.
- [x] (2026-07-25 10:09Z) Verified public DNS, TLS, security headers, Mainnet/Testnet selection, status, assets, APIs, and the retained workers.dev recovery origin.

## Surprises & Discoveries

- Observation: Mainnet and Testnet currently share one Worker and one static application artifact; the browser runtime selects Testnet only from `?hyperliquidNetwork=testnet` (or a pre-load global), while no selector defaults to Mainnet.
  Evidence: `src/hyperopen/config.cljs` resolves the startup selector once, and `wrangler.jsonc` deploys one Worker named `hyperopen`.

- Observation: Workers Static Assets currently invokes the user Worker only for `/api/health` and `/api/hyperunit/*`, so hostname-specific document behavior cannot run on ordinary terminal routes.
  Evidence: `wrangler.jsonc` sets `assets.run_worker_first` to those two patterns.

- Observation: this workspace has no Git commit or tracked baseline.
  Evidence: `git status --short --branch` reports `No commits yet on master` and all repository files as untracked. Release accountability must therefore use the deployed Worker version ID, command record, and this plan rather than a commit SHA.

- Observation: the local command-line resolver retained a pre-deployment negative DNS cache for the three new subdomains after Cloudflare authoritative DNS, 1.1.1.1, 8.8.8.8, and the in-app browser all resolved them correctly.
  Evidence: `dig @nina.ns.cloudflare.com`, `dig @1.1.1.1`, and `dig @8.8.8.8` returned both Cloudflare A records for each hostname; `curl --resolve` verified TLS/SNI and route behavior until the local cache expires.

## Decision Log

- Decision: Keep one Worker and one release artifact for all four hostnames.
  Rationale: the application already centralizes runtime network selection, and a single deployment prevents Mainnet and Testnet code drift. Host routing supplies only the startup selector and informational surfaces.
  Date/Author: 2026-07-25 / Codex.

- Decision: Canonicalize HTML navigations on `app.dexhelm.com` to `hyperliquidNetwork=mainnet` and on `testnet.dexhelm.com` to `hyperliquidNetwork=testnet`, while preserving other query parameters and leaving APIs and static assets untouched.
  Rationale: configuration is resolved only at page initialization. A redirect is deterministic, visible, shareable, deep-link safe, and avoids mutating generated JavaScript or browser storage.
  Date/Author: 2026-07-25 / Codex.

- Decision: Run the Worker before Static Assets for all custom-domain requests and immediately delegate unowned requests to `env.ASSETS`.
  Rationale: Cloudflare's asset-first routing otherwise bypasses hostname-aware code for every existing HTML file and deep link. The Worker performs only local branching before static delegation; no external subrequest is added to asset delivery.
  Date/Author: 2026-07-25 / Codex.

- Decision: Serve the product/docs/risk and status surfaces as self-contained Worker HTML with no client JavaScript, remote fonts, analytics, cookies, or third-party assets.
  Rationale: these pages remain available even if the trading bundle or an upstream API is degraded, have a narrow CSP, and do not introduce another build or deployment target.
  Date/Author: 2026-07-25 / Codex.

## Scope and Non-Goals

In scope are exact custom domains for `dexhelm.com`, `app.dexhelm.com`, `testnet.dexhelm.com`, and `status.dexhelm.com`; deterministic host behavior in `workers/hyperopen-worker.mjs`; Worker tests; Wrangler configuration; public deployment and verification; an operator page that includes product scope, basic operating documentation, explicit risk disclosure, and AGPL/source access; and a status page that reports only the service surfaces the Worker can truthfully attest to.

Out of scope are changing application code under `src/**`, adding live network switching, persisting a network selection, adding analytics or user tracking, provisioning a separate monitoring vendor, claiming Hyperliquid or HyperUnit uptime as DEXHelm uptime, changing wallet/signing behavior, submitting transactions, or disabling the existing `workers.dev` recovery origin.

## Plan of Work

1. Extend the Worker with pure host and document-navigation helpers. Exact DEXHelm terminal hosts canonicalize the startup network query once and redirect `/` to `/trade`; an already-canonical deep link delegates to Static Assets without another redirect.
2. Add accessible, responsive, self-contained HTML for the apex and status hosts. Apply strict document security headers, honest service wording, links to both terminals, documentation/risk anchors, source, license, health JSON, and recovery/status surfaces.
3. Add exact Custom Domain routes to `wrangler.jsonc` and enable Worker-first asset routing so host policy applies to all documents. Preserve `workers_dev`, fixed upstream variables, Static Assets, and proxy behavior.
4. Add focused deterministic tests for host isolation, redirect status and loop prevention, query preservation, root-to-trade behavior, asset/API pass-through, informational content, security headers, and compatibility for the existing workers.dev host.
5. Run the release sequence from the repository deployment skill, deploy after successful checks, then verify each hostname over public DNS and TLS.

## Validation and Acceptance

1. `https://dexhelm.com/` returns 200 HTML naming DEXHelm and exposing product information, getting-started documentation, risk disclosure, AGPL license, and public source links. `/docs` and `/risk` lead to the corresponding canonical sections.
2. A browser navigation to any supported `app.dexhelm.com` application deep link reaches the same path with exactly one `hyperliquidNetwork=mainnet` selector; other query values survive. The corresponding Testnet behavior uses `testnet`. Assets, API health, and HyperUnit proxy requests are never redirected.
3. `https://status.dexhelm.com/` returns 200 HTML with no-store caching and a link to 200 JSON at `/api/health`. It distinguishes DEXHelm edge/application availability from third-party network and protocol availability.
4. Existing `https://hyperopen.izhenghaocn.workers.dev` behavior remains compatible, including `/trade`, unknown 404s, `/api/health`, release headers, and non-mutating fee probes.
5. Focused Worker and release tests pass, release Playwright passes, the Cloudflare artifact preflight and dry-run pass, and the governed repository gate matrix passes or records only a demonstrated environmental blocker.
6. Cloudflare reports all four Custom Domains attached to Worker `hyperopen`; public DNS resolves through Cloudflare, TLS validates, and direct HTTP responses satisfy the host contracts above.

## Idempotence and Recovery

Worker code and route declarations are deterministic and safe to rebuild. Re-deploying the same configuration updates the same Custom Domains rather than creating additional names. Redirects change only GET/HEAD document URLs and never submit account actions.

If custom-domain provisioning fails before activation, the existing workers.dev deployment remains available. If the new Worker version breaks the existing public origin after deployment, stop further DNS changes, identify the prior version `588a4869-597b-4fd0-b744-42a631b7ec6f`, and request explicit rollback authorization before `wrangler rollback`. Removing or replacing DNS/custom-domain records is a separate destructive operation and is not implied by this plan.

## Artifacts and Notes

Authenticated account: `Izhenghaocn@gmail.com's Account` (`a95e39ff9f1a66e7630e6639a0edb86c`). Zone: `dexhelm.com` (`a199ddb28ae80bcb251f7262a15427a9`). These identifiers are non-secret Cloudflare resource identifiers. Never record OAuth tokens, refresh tokens, wallet addresses, signatures, cookies, or upstream response bodies here.

## Outcomes & Retrospective

### Delivered

- `dexhelm.com` serves a self-contained DEXHelm product page with practical documentation, an explicit risk disclosure, source access, and AGPL-3.0 access.
- `app.dexhelm.com` and `testnet.dexhelm.com` share deployed Worker version `169c1e6f-2ef8-49f9-87b6-82ff04fb0b8c`, but their document requests are canonicalized to `hyperliquidNetwork=mainnet` and `hyperliquidNetwork=testnet` respectively. Root terminal requests go to `/trade`; deep-link query values survive except for the one forced selector.
- `status.dexhelm.com` serves a no-store status page and the shared `GET /api/health` JSON response. It intentionally reports only Cloudflare/Worker reachability and explicitly disclaims independent upstream availability.
- The existing `https://hyperopen.izhenghaocn.workers.dev` origin remains published and passed its route/header and non-mutating mainnet/Testnet HyperUnit proxy verifiers.

### Validation Record

Completed with `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` and explicit Node local-storage files where required:

- `npm run test:cloudflare-worker`: 20 passing tests.
- `npm run test:release-assets`: 48 passing tests.
- `npm run test:playwright:seo`: 6 passing browser tests.
- `node .agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs --artifact`: 33 passes, 0 failures, 2 environment warnings.
- `npm run cloudflare:check`: successful build and Wrangler dry-run.
- `npm run gates`: 34/34 gates passed; 6,541 tests and 35,746 assertions.
- Public verification: `verify:deployment-headers` and `verify:cloudflare-worker` passed at the workers.dev recovery origin; both non-mutating fee probes returned 200 JSON. Cloudflare reported all four Custom Domains on Worker `hyperopen`. Apex `/`, `/docs`, `/risk`, status `/`, status health, and both terminal redirects passed over public Cloudflare TLS.

Browser QA artifacts: `tmp/browser-inspection/dexhelm-domain-2026-07-25/`. Required widths `375`, `768`, `1280`, and `1440` were inspected. Visual and styling-consistency passes are **BLOCKED** only in the formal design-reference sense because the user supplied no visual reference for the newly created operator/status pages; implementation-specific checks passed. Native-control, interaction, layout-regression, and jank/performance passes are **PASS**: zero visible native controls, visible keyboard focus, correct documentation anchor behavior, terminal URL canonicalization, no console errors, no horizontal overflow, stable repeated viewport/scroll observations, and responsive screenshots. Browser and browser-inspection sessions were finalized and cleaned up.

### Residual Risks

- The release bundle carries the pre-existing soft gzip budget warning: `653712` bytes versus `640000` (+13,712). It is not a test failure and this hostname routing change did not add application JavaScript.
- Visual-reference conformance is not independently comparable until the operator supplies a design reference; screenshot and geometry evidence are retained for future comparison.
- DNS recursive caches may briefly retain the prior NXDOMAIN response for the three new subdomains. Authoritative/public resolver, TLS/SNI, and in-app browser checks already passed; no further Cloudflare action is required.
