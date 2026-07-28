# Hyperopen Cloudflare Release Retrospective

## Contents

- Case outcome
- Branded custom-domain follow-up
- Source provenance and reconstruction limits
- Open-source starting point
- Source-to-Cloudflare transformation
- Development sequence
- Deployment sequence
- Failures and classifications
- Durable decisions
- Reuse checklist

## Case Outcome

On 2026-07-21, Hyperopen was published through Cloudflare Workers Static Assets with a same-origin HyperUnit proxy.

- Public origin: `https://hyperopen.izhenghaocn.workers.dev`
- Worker version: `9e842a8f-8eb6-412f-a105-c5d15df6da66`
- Static header result: Workers Static Assets parsed 24 generated `_headers` rules; no Worker header fallback was needed.
- Public proxy result: mainnet and testnet non-mutating fee probes returned HTTP 200 JSON.
- Repository result: 34/34 gates, 6,533 tests, and 35,718 assertions passed in the corrected environment.
- Intentional non-goals: no custom domain, DNS route, secrets, Git remote operation, or `/src/**` deployment edit.

The historical URL and version are evidence, not defaults. Always use the values printed by the current deployment.

## Branded Custom-Domain Follow-Up

On 2026-07-25, the existing `hyperopen` Worker was updated with the DEXHelm public tenant rather than creating a second Worker.

- Public Worker origin: `https://hyperopen.izhenghaocn.workers.dev`
- Final Worker version: `906ea1c8-e2cd-42d4-915f-db8d0f8ebe56`
- Authoritative asset directory: `out/white-label/dexhelm`
- Public host policy: apex 200, testnet 200, intentionally closed app/mainnet 503, and status 200
- Public logo: 200 with `image/svg+xml`
- Repository result: 34/34 gates, 6,546 tests, and 35,754 assertions passed.
- White-label browser result: 4/4 at 375, 768, 1280, and 1440 px.
- Final mobile brand geometry: logo right edge 86 px, wordmark left edge 98 px, leaving a 12 px gap.
- Bundle result: 653,917 gzip bytes, 13,917 bytes above the advisory 640,000-byte budget.

The release kept `app.dexhelm.com` unavailable by deliberate Worker host policy. Its 503 was therefore an acceptance result, not an outage. The completed implementation record is `docs/exec-plans/completed/2026-07-25-dexhelm-public-brand-release.md`.

## Source Provenance and Reconstruction Limits

The local repository identifies its origin as `https://github.com/thegeronimo/hyperopen.git`. Its README describes Hyperopen as an open-source Hyperliquid trading client, and `package.json` declares `AGPL-3.0`; the repository includes the GNU Affero General Public License version 3 text.

The exact commit used to create this local source tree cannot be recovered from the available Git metadata. On 2026-07-21, `git rev-parse HEAD` failed because the local `master` branch had no commit, and `git status --short` reported the repository files as untracked. A read-only check showed upstream `HEAD` at `ebc7a233de23779c12d73f35a73704b286fd6c1e` on that date, but that value is only a contemporaneous upstream reference. It is not evidence that the local tree started from that commit.

This is a provenance gap, not a reason to invent a baseline. For future adaptations, record the upstream URL, exact commit, license, and clean-tree status before making changes. If exact historical attribution is required for this case, obtain the original downloaded archive, clone metadata, or source commit from the user.

## Open-Source Starting Point

The Cloudflare work started from an existing application rather than rebuilding Hyperopen. The inherited repository already provided:

- a ClojureScript trading client with Replicant rendering and Nexus action/effect dispatch
- `npm run build`, which compiled the application and generated `out/release-public`
- route-specific release HTML, site metadata, hashed assets, service-worker output, and a generated `_headers` policy
- a deployment-header verifier and release Playwright coverage
- a local Node HyperUnit proxy with established mainnet, testnet, and default-mainnet route meanings
- direct browser defaults for `https://api.hyperunit.xyz` and `https://api.hyperunit-testnet.xyz`
- deterministic repository gates for static checks, application tests, and websocket behavior

Those inherited contracts constrained the solution. The production release needed a same-origin proxy, while the open-source project's ordinary `npm run dev` behavior, source endpoint defaults, route output, security headers, and application architecture needed to remain intact.

## Source-to-Cloudflare Transformation

The adaptation added a deployment layer around the existing release artifact:

- `wrangler.jsonc` configured Workers Static Assets, the `ASSETS` binding, fixed HyperUnit origins, and Worker-first API paths.
- `workers/hyperopen-worker.mjs` added `/api/health`, fixed-origin proxy routing, header filtering, upstream streaming, static delegation, and generic failure responses.
- `tools/cloudflare/rewrite_hyperunit_release_endpoints.mjs` changed only generated release JavaScript from direct HyperUnit origins to same-origin proxy paths.
- deterministic Worker, rewrite, edge-case, and public-verifier tests protected the new boundary.
- package scripts and a pinned Wrangler version made build, local validation, dry-run, deployment, and public verification repeatable.
- README and the ExecPlan documented the opt-in operating path and preserved the existing local workflow.

No `/src/**` deployment edit was required. This was deliberate: the upstream application remained the source of product behavior, while Cloudflare-specific behavior stayed in configuration, Worker code, tooling, tests, and generated release output.

## Development Sequence

This reconstructed sequence combines the completed deployment ExecPlan with the provenance inspection performed for this retrospective. The original implementation recorded the repository mapping and later steps; the source identity and missing-commit limitation were verified afterward and must not be mistaken for contemporaneous baseline capture.

1. Identified the public upstream repository and AGPL-3.0 license, then recorded that the local checkout lacked a recoverable baseline commit.
2. Read the repository entry docs and mapped the inherited ClojureScript architecture, release build, `out/release-public`, route HTML layout, `_headers` generator, local HyperUnit proxy, and direct browser defaults.
3. Chose Workers Static Assets because the app needed both static delivery and a same-origin edge proxy.
4. Froze local development behavior: direct HyperUnit origins stay in ClojureScript source; only generated release JavaScript is rewritten.
5. Wrote RED tests for exact artifact rewriting, proxy routing, boundary rejection, safe headers, request-body forwarding, response streaming, generic failure handling, static delegation, and public verification.
6. Implemented `wrangler.jsonc`, the Worker, release rewriter, verifier, pinned Wrangler dependency, and repository scripts.
7. Proved the release artifact contains relative `/api/hyperunit/*` bases while `/src/**` retains direct development origins.
8. Corrected repeated release builds before allowing dry-run or deployment.

The final proxy is intentionally narrow. It chooses only fixed mainnet or testnet HTTPS origins, uses complete path-segment matching, filters request and response headers, streams successful responses, and returns a generic JSON 502 on a rejected upstream fetch.

## Deployment Sequence

1. Ran deterministic Worker and release-asset tests.
2. Ran release Playwright smoke against the normal release artifact.
3. Rebuilt with `build:cloudflare` because Playwright invokes ordinary `npm run build` and can replace the rewritten artifact.
4. Started local Wrangler and confirmed route title, document security headers, immutable caching, unknown-route 404, health JSON, and both proxy fee routes.
5. Ran `cloudflare:check`, which prepared the real asset upload and exited at `--dry-run`.
6. Ran all repository gates with JDK 21 and the Node localStorage correction.
7. Ran `wrangler whoami` immediately before deployment and checked the authenticated account and Workers write permission.
8. Ran `deploy:cloudflare`, captured the exact public origin and version ID, and made no DNS or custom-domain changes.
9. Ran both repository verifiers against the exact public origin.
10. Recorded outcomes in the ExecPlan and moved it to `completed`.

## Failures and Classifications

### Loopback EPERM

Symptom: Node tests failed with `listen EPERM ... 127.0.0.1`.

Classification: sandbox permission, not a product defect. The failing tests owned short-lived loopback servers. Obtain localhost permission and rerun unchanged; do not weaken the test.

### Java Not Found

Symptom: macOS `java` and `java_home` reported no runtime.

Classification: environment discovery. Homebrew JDK 21 already existed at `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`. Setting `JAVA_HOME` and prepending its `bin` directory allowed Shadow-CLJS builds to pass. Do not use the repository's Linux/x64 Java bootstrap path on macOS.

### Repeated Release Build Failure

Symptom: a second `build:cloudflare` failed with `Expected the main module to contain the shadow-cljs eval-based loader runtime` when Shadow reported zero compiled files.

Classification: code defect in release parsing. Closure emitted a nested prototype alias such as `$APP.d`, while the detector accepted at most one dotted property. The durable fix accepted any number of ordinary dotted identifiers and added a regression test.

Deleting Shadow caches would have hidden the defect and left dry-run followed by deploy unreliable. Consecutive builds are a release invariant.

### Node 25 localStorage Failures

Symptom: `npm test` and `test:websocket` failed broadly with `localStorage.getItem is not a function` or `setItem is not a function`.

Classification: runtime environment. Node 25.6 exposed an incomplete global localStorage object. Running gates with `NODE_OPTIONS=--localstorage-file=/tmp/hyperopen-node-gates-localstorage` restored the expected methods and produced a 34/34 matrix.

### Playwright Artifact Replacement

Risk: `test:playwright:seo` runs ordinary `npm run build`, so a previously rewritten Cloudflare artifact is no longer guaranteed deployable.

Classification: sequencing hazard. Always run `build:cloudflare`, local Wrangler checks, or `cloudflare:check` after release Playwright. The deploy script also rebuilds first.

### Tenant Artifact Selection and Digest Refresh

Symptom: the original deployment workflow assumed `out/release-public`, while the branded Worker uploaded `out/white-label/dexhelm`. Rewriting HyperUnit endpoints changed the main JavaScript after the white-label build had already recorded its digests.

Classification: release-contract drift. `wrangler.jsonc` `assets.directory` is the upload authority. The branded Cloudflare adapter must build that tenant directory, rewrite only its generated endpoints, refresh `mainBundleDigest` and every `artifactDigests` entry, and rerun `verifyWhiteLabelRelease` before Wrangler sees the output.

### Memoized Projection Dropped Tenant State

Symptom: some public surfaces retained the upstream brand even though the leaf header view-model understood the DEXHelm tenant.

Classification: state-propagation defect. A memoized parent projection omitted `:tenant/override`, so branding did not reach every consumer. Regression coverage must exist both where the tenant-aware leaf view-model is built and where its parent projection selects inputs. Public verification must cover the header, product-context banner, chart legend, and title.

### Branded CSP Verification

Risk: a locally correct tenant config can still produce public headers that omit the logo or analytics origin.

Classification: verifier-source error. Read public `tenant-manifest.json`, derive the expected image and connect origins, and compare them with the live CSP. Do not hardcode DEXHelm origins or verify only against local configuration.

### Mobile Brand Collision

Symptom: at 375 px, the logo control could flex-shrink and collide with or cross the wordmark.

Classification: responsive layout defect. Prevent the logo control from shrinking, compare computed edges with `logoRight <= wordmarkLeft`, and assert that both document and header widths remain within the viewport. A screenshot alone is insufficient geometry evidence.

### Long-Lived Trading Page Inspection Timeout

Symptom: `browser:inspect` timed out waiting for `Page.loadEventFired` on the live trading route while the application maintained long-lived connections.

Classification: inspection-tool readiness mismatch, not automatically a release defect. Use a DOM snapshot or visible route anchor, or navigate with `waitUntil: "commit"` and then wait for locator readiness. Preserve the failed artifact as evidence and clean up the inspection session.

### Wrangler Sandbox Log Warning

Symptom: Wrangler printed an `EPERM` warning while opening its local log file during a sandboxed dry-run, but prepared the asset upload, completed `--dry-run`, and exited 0.

Classification: non-blocking environment warning. Use exit status and the full dry-run transcript to determine success; record the warning without converting it into a deployment failure.

### Bundle Budget Warning

Symptom: the 2026-07-21 initial release exceeded the soft gzip budget by 13,112 bytes; the 2026-07-25 branded release exceeded it by 13,917 bytes.

Classification: recorded non-blocking risk. The checker deliberately returned success. Do not silently re-ratchet the budget during deployment; report the delta separately.

### Public 502

Potential symptom: a same-origin fee probe returns 5xx.

Classification requires evidence. It may be a Worker mapping or fetch failure, or HyperUnit upstream unavailability. Preserve the generic public response and never log or return private error details.

## Durable Decisions

- Use Worker code for `/api/health` and `/api/hyperunit/*`; keep normal assets direct.
- Treat `_headers` support as something to prove locally and publicly, never something to assume.
- Treat `wrangler.jsonc` `assets.directory` as the authoritative artifact, including tenant-specific white-label output.
- Refresh and verify tenant manifest digests after any post-build endpoint rewrite.
- Test tenant propagation at both view-model and memoized projection boundaries.
- Derive branded CSP expectations from the public tenant manifest.
- Verify custom-domain host policy as an explicit status matrix.
- Keep the deploy command opt-in and rebuild immediately before upload.
- Keep Wrangler pinned so dry-run and deployment use the same CLI behavior.
- Validate path boundaries and fixed upstream construction without network dependencies.
- Run only non-mutating proxy probes after deployment and never print response bodies.
- Capture the account, public origin, and version ID from current Wrangler output.
- Prefer rollback to a known prior version over deleting a faulty Worker.

## Reuse Checklist

- [ ] Upstream URL, exact source commit, worktree state, and license are recorded before edits.
- [ ] The inherited build and smallest relevant baseline gates pass, or pre-existing/environmental failures are recorded.
- [ ] Existing release, proxy, header, and browser contracts are inventoried before choosing the deployment design.
- [ ] User intent distinguishes inspect, dry-run, deploy, verify, and rollback.
- [ ] An active ExecPlan exists when pipeline behavior changes.
- [ ] Preflight passes and environment warnings are resolved.
- [ ] Worker, rewrite, and verifier tests pass without weakened assertions.
- [ ] Release Playwright passes before the final Cloudflare build.
- [ ] Two consecutive release builds are viable.
- [ ] Release JavaScript has no direct HyperUnit origin; source defaults remain.
- [ ] The tested and uploaded directory exactly matches `wrangler.jsonc` `assets.directory`.
- [ ] White-label manifest digests were refreshed after rewriting and tenant verification passes.
- [ ] Header, banner, chart legend, title, logo geometry, and viewport overflow pass branded browser checks when applicable.
- [ ] Local Wrangler proves title, headers, cache, 404, health, and proxy behavior.
- [ ] `cloudflare:check` and repository gates pass.
- [ ] `wrangler whoami` is checked immediately before deploy.
- [ ] Exact public URL and version ID are captured.
- [ ] The configured Worker name and same-Worker update are confirmed.
- [ ] Every expected custom-domain status and public logo content type passes when applicable.
- [ ] Public header and proxy verifiers pass.
- [ ] Local processes are stopped and the ExecPlan is completed.
