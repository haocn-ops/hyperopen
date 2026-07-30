# Enforce HTTPS on DEXHelm public hosts

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must remain current until the change is accepted. It follows `/Users/zh/Documents/Hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Cloudflare's report identified that the four DEXHelm public hosts served content directly over HTTP. A first-time visitor could therefore receive a modified trading page before the browser had learned the existing HSTS policy. After this change, HTTP requests to the apex, Testnet, intentionally closed Mainnet, and status hosts will receive a permanent HTTPS redirect while preserving the original path and query string. Existing HTTPS behavior and the normal local development endpoints remain unchanged.

The user-visible proof is an HTTP request such as `curl -I http://testnet.dexhelm.com/trade?coin=HYPE`, which must return a 308 redirect whose `Location` is the same HTTPS URL. The change also retains the existing one-year HSTS response policy. Bot Fight Mode, AI bot blocking, and AI Labyrinth remain out of scope because they are traffic/content policy choices rather than transport-security defects.

## Context References

Public refs:

- Direct user request on 2026-07-30: repair the Cloudflare-reported risks after the read-only assessment.

Repo artifacts:

- `/Users/zh/Documents/Hyperopen/workers/hyperopen-worker.mjs` owns the Worker request boundary and fixed DEXHelm host policy.
- `/Users/zh/Documents/Hyperopen/tools/cloudflare/worker.test.mjs` contains deterministic Worker routing tests.
- `/Users/zh/Documents/Hyperopen/wrangler.jsonc` selects the `hyperopen` Worker and the DEXHelm custom domains.
- `/Users/zh/Documents/Hyperopen/docs/exec-plans/completed/2026-07-28-defense-in-depth-security-and-release-preflight.md` records the existing HSTS hardening and its header contract.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-07-30 08:00 Asia/Shanghai) Confirmed the live HTTP gap: all four public hosts returned content without an HTTPS redirect; HTTPS responses already carried HSTS.
- [x] (2026-07-30 08:05 Asia/Shanghai) Created this active plan and limited scope to transport enforcement; no bot or AI crawler policy changes.
- [x] (2026-07-30 08:12 Asia/Shanghai) Added RED tests for exact-host HTTP redirects, path/query preservation, and unchanged unknown-host behavior; the new redirect test failed before implementation.
- [x] (2026-07-30 08:15 Asia/Shanghai) Implemented the Worker HTTPS redirect before host routing and kept the `workers.dev` hostname unowned.
- [x] (2026-07-30 08:18 Asia/Shanghai) Ran two consecutive `build:cloudflare` releases successfully; both produced digest `995F705A89523B891DF0BA64C80204B44BB97AF6985A0529FA34150C1A07365C`.
- [x] (2026-07-30 08:22 Asia/Shanghai) Passed artifact preflight (33 checks), white-label verification, Worker tests (34/34 with loopback permission), and release asset tests (51/51 with loopback permission).
- [x] (2026-07-30 08:25 Asia/Shanghai) Wrangler dry-run passed; the authenticated account confirmed Worker write permission but only Zone read permission.
- [x] (2026-07-30 08:31 Asia/Shanghai) Deployed the existing `hyperopen` Worker to all four configured custom domains; version `f26e000b-532c-44cd-8de3-cd04aab40fb2` was returned.
- [x] (2026-07-30 08:35 Asia/Shanghai) Verified public HTTP 308 redirects and HTTPS status/header/proxy behavior with curl; repository-wide `npm test` and `npm run test:websocket` passed.
- [x] (2026-07-30 08:40 Asia/Shanghai) Completed this plan; `npm run check` reached release tests but its three loopback checks were blocked by sandbox `listen EPERM`; the same release suite passed with loopback permission.

## Surprises & Discoveries

- Observation: `http://dexhelm.com/` and `http://testnet.dexhelm.com/trade` returned HTTP 200 rather than redirecting.
  Evidence: read-only curl checks on 2026-07-30 returned `code=200` with an empty `redirect_url`.
- Observation: the reported HSTS gap is not a missing application header.
  Evidence: all checked HTTPS document responses included `strict-transport-security: max-age=31536000; includeSubDomains`; the repository's header verifier also passed.
- Observation: the screenshot lists six total findings but only five visible rows.
  Evidence: the left legend totals `4 + 2`, while the cropped Top Insights list shows five rows. The missing row remains outside this plan.
- Observation: the repository's full `npm run check` cannot bind its local header-verifier fixtures in the default sandbox.
  Evidence: the three `verifyDeploymentHeaders` tests fail with `listen EPERM 127.0.0.1`; rerunning `npm run test:release-assets` with loopback permission passes all 51 tests.

## Decision Log

- Decision: enforce HTTPS in the Worker for exactly the four configured DEXHelm hosts, before any hostname-specific route handling.
  Rationale: this closes the observed production gap even when a Cloudflare zone toggle is missing, while preserving the existing 404 behavior for unowned or workers.dev hostnames.
  Date/Author: 2026-07-30 / Codex.
- Decision: use HTTP 308 and preserve path/query data.
  Rationale: 308 is permanent and preserves the request method/body for future API callers; URL construction is limited to HTTPS on an allowlisted host.
  Date/Author: 2026-07-30 / Codex.
- Decision: do not enable Bot Fight Mode, Block AI bots, or AI Labyrinth in this code change.
  Rationale: those controls can alter legitimate trading/API traffic or public content discovery and require an explicit product decision plus staged observation.
  Date/Author: 2026-07-30 / Codex.

## Outcomes & Retrospective

The Worker now returns 308 for HTTP requests on all four owned hosts. Production evidence shows the four HTTP redirects, HTTPS status matrix `200/200/503/200`, HSTS, health JSON, and proxy boundaries are correct. The change adds a small allowlisted routing guard and deterministic tests, reducing first-visit transport risk without changing normal HTTPS or local development behavior. The Cloudflare dashboard setting remains unsynchronized because the authenticated token has Zone read-only permission; the Worker guard provides the effective protection.

## Context and Orientation

Cloudflare sends requests to `workers/hyperopen-worker.mjs`. The exported `handleRequest` function currently begins with hostname and path routing, so HTTP requests reach the same document or asset handlers as HTTPS requests. The four owned hosts are `dexhelm.com`, `testnet.dexhelm.com`, `app.dexhelm.com`, and `status.dexhelm.com`. The public Worker hostname is intentionally unowned and must remain a 404.

`tools/cloudflare/worker.test.mjs` imports the Worker directly and supplies fake `ASSETS` and `fetchImpl` bindings, making redirect behavior testable without network access. The release pipeline uploads `out/white-label/dexhelm` using the scripts in `package.json`; generated static headers already include HSTS and must not be replaced by a source-level endpoint change.

## Plan of Work

First extend `tools/cloudflare/worker.test.mjs` with tests that send HTTP requests to each owned host and assert status 308, an HTTPS `Location` preserving pathname/search, and no calls to static assets or upstream fetch. Include a test proving an HTTP request to the unowned workers.dev hostname remains 404 rather than being redirected.

Then add an allowlisted host set and a small pure redirect helper in `workers/hyperopen-worker.mjs`. Invoke it immediately after parsing the request URL in `handleRequest`; return the redirect before Mainnet, health, document, asset, or proxy branches. Leave `src/**`, local endpoint defaults, HSTS values, and bot/content policies unchanged.

Run the focused tests first, then rebuild the authoritative DEXHelm artifact and run the release and white-label validators. Run the repository preflight and Cloudflare dry-run. Before publishing, confirm the authenticated account and Worker name, then deploy the same configured Worker only under the user's authorization. Verify both the HTTP redirect matrix and the existing HTTPS header/proxy matrix, and record the returned version ID. The prior rollback version remains in the deployment history; this task did not change or delete it.

## Concrete Steps

From `/Users/zh/Documents/Hyperopen`:

1. Run `npm run setup:worktree`.
2. Run `npm run test:cloudflare-worker` and `npm run test:release-assets`.
3. Run `npm run test:playwright:seo` if the release artifact is regenerated by that suite.
4. Run `env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH npm run build:cloudflare`.
5. Run `node .agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs --artifact` and `npm run cloudflare:check`.
6. Run the governed gates with the documented JDK 21 and Node local-storage correction.
7. After authorized deployment, run the two repository verifiers against `https://testnet.dexhelm.com`, then use curl against HTTP for all four hosts and HTTPS for the required status matrix.

Expected redirect evidence includes `HTTP/1.1 308` and a `Location: https://...` retaining the original path and query. Expected HTTPS evidence remains `/trade` 200, `/api/health` 200 JSON, Mainnet-closed 503, and Mainnet/generic proxy paths 404.

## Validation and Acceptance

The focused Worker test passes 13/13, the full Worker suite passes 34/34, the release asset suite passes 51/51, the white-label validator passes, the artifact preflight has 33 passes and 0 failures, and the Wrangler dry-run completes successfully. `npm test` passes 5,865 tests/32,654 assertions and `npm run test:websocket` passes 561 tests/3,184 assertions. `npm run check` reaches its release suite but cannot bind loopback in the default sandbox; its affected release suite passes unchanged when run with the required loopback permission.

Public acceptance requires every configured HTTP host to redirect to its HTTPS equivalent, including `/trade`, `/api/health`, the closed Mainnet host, and the status host. Every HTTPS host must retain its prior status and security headers. The public Testnet fee probe must remain non-mutating and return the same non-5xx response observed before this change.

## Idempotence and Recovery

The redirect is deterministic and safe to test or deploy repeatedly. Rebuilding regenerates the authoritative artifact before every dry-run/deploy. If deployment causes static routes or headers to fail, stop further changes, retain the exact prior Worker version ID from the plan, and request an explicit rollback using that version; do not delete the Worker.

## Artifacts and Notes

The final plan must record the changed files, exact commands and outcomes, configured account/Worker name, new version ID, prior rollback version, public HTTP redirect matrix, HTTPS status matrix, and any environment warnings. No credentials, cookies, tokens, or response bodies may be recorded.

## Interfaces and Dependencies

`workers/hyperopen-worker.mjs` must expose the existing `handleRequest(request, env, options)` contract unchanged. The new helper may be local and pure; it must use the existing `Response.redirect` Web API and a fixed host allowlist. `tools/cloudflare/worker.test.mjs` remains a Node built-in test suite and must use only deterministic `Request` objects and fake bindings. No new runtime dependency is required.

Plan revision note: created on 2026-07-30 to track the explicit HTTPS enforcement repair requested after the Cloudflare risk assessment; completed after deployment and public verification on the same date.
