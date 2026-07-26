# Publish Hyperopen with a Cloudflare Worker and HyperUnit Proxy

Status: completed 2026-07-21 11:19Z.

This ExecPlan is a living document. Maintain its `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` sections in accordance with `/hyperopen/.agents/PLANS.md` while work proceeds.

## Purpose / Big Picture

Publish Hyperopen as a public Cloudflare Worker that serves the existing release artifact through Workers Static Assets and exposes a same-origin `/api/hyperunit/*` proxy. A visitor to the Worker URL can load `/trade` with the existing release metadata, security headers, and hashed assets; a non-mutating request to `/api/hyperunit/mainnet/v2/estimate-fees` reaches the configured HyperUnit mainnet origin without the browser directly calling that origin.

The production release must use the proxy, but ordinary development must not change: `npm run dev` continues to use the current direct HyperUnit defaults, and no visual UI behavior or ClojureScript source under `/hyperopen/src/**` is changed.

## Context References

Public refs:

- Direct user request on 2026-07-21: publish Hyperopen to a Cloudflare Worker with Workers Static Assets and a Worker-side `/api/hyperunit/*` proxy.

Repo artifacts:

- `/hyperopen/package.json` defines the release build and required gates.
- `/hyperopen/tools/hyperunit-proxy/server.mjs` defines the current local proxy path mapping.
- `/hyperopen/tools/release-assets/generate_release_artifacts.mjs` creates `out/release-public`.
- `/hyperopen/tools/release-assets/security_headers.mjs` and `/hyperopen/tools/release-assets/verify_deployment_headers.mjs` define and verify the release header contract.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` govern this living plan.

Local scratch refs (non-authoritative): none.

## Scope and Non-Goals

This work adds Cloudflare deployment configuration, a Workers-runtime JavaScript proxy, release-only endpoint rewriting, deterministic Node tests, deployment commands, and public verification. It may update `package.json`, `package-lock.json`, `README.md`, `tools/**`, `workers/**`, and the active ExecPlan. It must not edit `/hyperopen/src/**`, alter app screens, alter wallet/funding form semantics, add persistence, add custom-domain DNS routes, add Cloudflare secrets, or run git operations.

The first public target is the automatically assigned `https://hyperopen.<account>.workers.dev` URL. A custom domain is a follow-up because its zone and ownership were not supplied. The Worker name `hyperopen` is available and Cloudflare authentication was confirmed before this plan was written.

## Progress

- [x] (2026-07-21 10:16Z) Mapped the release pipeline, current local HyperUnit proxy, direct client defaults, release route layout, and header verifier.
- [x] (2026-07-21 10:16Z) Confirmed Cloudflare authentication is valid, `hyperopen` is available as a Worker name, and Wrangler 4.112.0 can be resolved with `npx`.
- [x] (2026-07-21 10:16Z) Froze scope: production release uses same-origin proxy endpoints; source defaults and local development remain direct upstream.
- [x] (2026-07-21 10:53Z) Added pinned Wrangler 4.112.0, `wrangler.jsonc`, opt-in Cloudflare build/deploy/check/dev/verification scripts, and README operating instructions. `npm ls wrangler --depth=0` reports `wrangler@4.112.0`.
- [x] (2026-07-21 10:53Z) Implemented the release-only HyperUnit endpoint rewriter. Its 4 deterministic fixture tests prove exact JavaScript-only replacement, no partial mutation when an expected origin is absent, repeated-run rejection, and symlink rejection.
- [x] (2026-07-21 10:53Z) Implemented and tested the Worker router and public verifier. `npm run test:cloudflare-worker` passed 11/11 tests, including fixed-origin path mapping, segment boundary rejection, request/response header allowlists, body forwarding, generic 502 behavior, static delegation, and non-mutating verifier probes.
- [x] (2026-07-21 11:19Z) Ran local Wrangler compatibility checks, the release Playwright smoke test, `cloudflare:check`, and the complete repository gate matrix. Workers Static Assets parsed 24 `_headers` rules; `/trade`, immutable assets, control assets, and an unknown-route 404 all matched the release contract. `npm run gates` passed 34/34 gates with 6,533 tests and 35,718 assertions.
- [x] (2026-07-21 11:19Z) Deployed Worker version `9e842a8f-8eb6-412f-a105-c5d15df6da66` to `https://hyperopen.izhenghaocn.workers.dev`. Public security-header/cache verification passed, and both same-origin mainnet/testnet fee probes returned HTTP 200 JSON.

## Surprises & Discoveries

- Observation: the normal browser client defaults directly to `https://api.hyperunit.xyz` or `https://api.hyperunit-testnet.xyz`; the local Node proxy is not used by default.
  Evidence: `/hyperopen/src/hyperopen/funding/effects/common.cljs` and `/hyperopen/src/hyperopen/api/endpoints/funding_hyperunit.cljs` contain those defaults, while `/hyperopen/tools/hyperunit-proxy/server.mjs` is started only by `npm run proxy:dev`.

- Observation: `out/release-public` uses route files such as `trade.html`, `portfolio.html`, and `api.html`, not per-route `index.html` directories.
  Evidence: `find out/release-public -maxdepth 1 -type f` includes `trade.html`, `portfolio.html`, and `api.html`; `/hyperopen/tools/playwright/static_server.mjs` deliberately resolves extensionless paths to those files.

- Observation: the release header policy is currently produced as a Pages-style `_headers` file, so Workers Static Assets compatibility must be demonstrated rather than assumed.
  Evidence: `/hyperopen/README.md` calls the policy a Cloudflare Pages `_headers` policy, and `/hyperopen/tools/release-assets/verify_deployment_headers.mjs` fails closed when document, immutable asset, or control asset headers differ.

- Observation: Java is not registered with macOS `java_home`, but Homebrew JDK 21 is installed and usable when `JAVA_HOME` is explicit.
  Evidence: commands using `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` completed every Shadow build and all 34 repository gates.

- Observation: this sandbox denies localhost binds until an explicit elevated command approval is granted, even though the Cloudflare and release-header test fixtures use only temporary loopback HTTP servers.
  Evidence: the initial runs of `node --test tools/cloudflare/*.test.mjs` and `npm run test:release-assets` reported `listen EPERM: operation not permitted 127.0.0.1`; their elevated reruns passed 11/11 and 46/46 tests respectively.

- Observation: the local macOS host has JDK 21 at `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`, allowing the requested Cloudflare build to complete.
  Evidence: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home npm run build:cloudflare` regenerated `out/release-public`; a subsequent `rg -n 'https://api\\.hyperunit' out/release-public/js --glob '*.js'` produced no matches.

- Observation: the current Closure-minified main module can address the loader prototype through `$APP.d`, while the old detector accepted only a bare identifier or one dotted property.
  Evidence: the generated source contains `$APP.d=vda.prototype;$APP.d.Sj=!1;$APP.d.rk=!1;$APP.d.wk=!1;var jxc=new vda;jxc.rk=!0;`; the former one-property matcher could not recognize it and made a repeated release build fail closed.

- Observation: Node 25.6 exposes an unusable `localStorage` object unless `--localstorage-file` has a valid path, which initially caused the two runtime test gates to fail for environmental reasons.
  Evidence: the initial gate matrix reported `localStorage.getItem is not a function`; rerunning with `NODE_OPTIONS=--localstorage-file=/tmp/hyperopen-node-gates-localstorage` produced a 34/34 PASS matrix.

## Decision Log

- Decision: use a Cloudflare Worker, not Cloudflare Pages.
  Rationale: the required same-origin API proxy needs edge request code; Workers Static Assets serves the existing static artifact without introducing a separate application server.
  Date/Author: 2026-07-21 / platform

- Decision: rewrite only the Cloudflare release artifact's literal HyperUnit bases to `/api/hyperunit/mainnet` and `/api/hyperunit/testnet`; do not edit ClojureScript defaults.
  Rationale: this activates the proxy for the deployed app while preserving the established direct-upstream local development workflow and the no-`src/**` scope. The rewrite is exact-string, JavaScript-only, and fails if either expected upstream literal remains in the final Cloudflare artifact.
  Date/Author: 2026-07-21 / platform

- Decision: copy the local proxy's route meanings, with explicit segment boundaries: `/api/hyperunit/mainnet/*` selects mainnet, `/api/hyperunit/testnet/*` selects testnet, and `/api/hyperunit/*` selects mainnet.
  Rationale: the mapping is already understood by developers and avoids an open proxy because callers cannot choose an arbitrary upstream host.
  Date/Author: 2026-07-21 / platform

- Decision: keep direct static delivery by initially setting Workers Static Assets `run_worker_first` only for `/api/hyperunit/*`.
  Rationale: this is not a latency optimization with a promised speed target. It preserves CDN/static-asset behavior and avoids invoking Worker code for the many hashed modules a normal page load requests. The deterministic local integration check must prove that the generated `_headers` contract still applies. If it does not, the fallback is a Worker-first static response adapter whose tests assert the identical header contract; security correctness takes precedence over minimizing Worker invocations.
  Date/Author: 2026-07-21 / platform

- Decision: forward only safe request headers and never forward application cookies, authorization, host, connection, or content-length headers to HyperUnit.
  Rationale: HyperUnit's documented public routes do not need Hyperopen session credentials. The Worker preserves method, body, query string, `Accept`, `Accept-Language`, `Content-Type`, and conditional-cache headers while preventing cross-origin credential leakage and hop-by-hop header misuse.
  Date/Author: 2026-07-21 / platform

- Decision: expose `GET /api/health` as a Worker-first, non-cached JSON health response and keep every other non-proxy request on the `ASSETS` binding.
  Rationale: the requested Worker configuration explicitly reserves `/api/health`; an exact health route makes that configuration observable without broadening Worker-first static delivery.
  Date/Author: 2026-07-21 / platform

- Decision: reject generic `/api/hyperunit/*` fallback when its first suffix segment starts with `mainnet` or `testnet` but is not an exact environment segment.
  Rationale: otherwise `/api/hyperunit/mainnetx/...` would select the default-mainnet fallback despite violating the segment-bounded route contract. The deterministic edge test now proves this failure mode is not an open proxy route.
  Date/Author: 2026-07-21 / platform

- Decision: allow any number of ordinary dotted identifier properties when detecting the release module-loader prototype, while retaining the existing flag-shape checks.
  Rationale: Closure output may emit `$APP.d` as the prototype alias. This is a grammar correction rather than a cache-clearing workaround; it preserves the generator's fail-closed behavior for unrelated loader forms and makes ordinary repeated release builds recognizable.
  Date/Author: 2026-07-21 / platform

## Context and Orientation

`npm run build` compiles the ClojureScript modules, builds CSS, and writes the publishable artifact to `/hyperopen/out/release-public`. That artifact contains the route-specific HTML files, assets, release metadata, and `_headers`; the existing release Playwright suite proves it through a local static server. It must remain the single input directory for Cloudflare Static Assets.

The deployed Worker has two paths. For `/api/hyperunit/*`, it builds a URL from a fixed mainnet or testnet base, streams the upstream response, and returns a JSON 502 response without internal error details if the upstream fetch rejects. Every other request goes to the `ASSETS` binding, which is Cloudflare's static file fetcher. The configuration must make `/trade` resolve to the generated `trade.html` without changing the browser URL, and must preserve a missing route's 404 behavior.

Because source defaults are intentionally untouched, a new Cloudflare-only post-build step must replace the two exact HyperUnit origin strings only in `out/release-public/js/*.js`. It must not rewrite source, HTML, CSS, `_headers`, the local dev build, or arbitrary URLs. Its result is deployable only after a clean `npm run build:cloudflare`; deployment never operates on an unrewritten `npm run build` artifact.

## Interfaces and Dependencies

Add `/hyperopen/wrangler.jsonc` with the local pinned Wrangler schema, `name` `hyperopen`, `main` `/hyperopen/workers/hyperopen-worker.mjs`, `compatibility_date` `2026-07-21`, `workers_dev` enabled, and `assets.directory` `./out/release-public`. Set public Worker variables `HYPERUNIT_MAINNET_URL=https://api.hyperunit.xyz` and `HYPERUNIT_TESTNET_URL=https://api.hyperunit-testnet.xyz`; neither value is a secret. The assets binding name is `ASSETS`.

Add `/hyperopen/workers/hyperopen-worker.mjs` as a module Worker with these exported, Node-testable helpers:

    resolveHyperunitTarget(requestUrl, env) -> URL | null
    buildHyperunitRequest(request, targetUrl) -> Request
    handleRequest(request, env, { fetchImpl }) -> Promise<Response>

`resolveHyperunitTarget` must remove only the matched prefix, preserve pathname suffix and query string, and choose the longest matching prefix first. Prefixes must match whole path segments: `/api/hyperunit/mainnetx` is not a proxy route. `buildHyperunitRequest` must retain a non-GET/HEAD body exactly once, use a copied and filtered `Headers` object, and use a manual redirect policy. `handleRequest` sends non-proxy requests to `env.ASSETS.fetch(request)` and returns `{ "error": "HyperUnit proxy request failed." }` with `content-type: application/json` and status 502 when `fetchImpl` rejects. It must stream successful upstream response bodies and preserve safe response headers/status.

Add `/hyperopen/tools/cloudflare/rewrite_hyperunit_release_endpoints.mjs`. It must replace exact occurrences of the two upstream base strings in JavaScript files under `out/release-public/js`, report the changed files and count for each replacement, and exit nonzero when either replacement count is zero or either upstream string remains. Add `build:cloudflare` so it runs the normal release build followed by this rewriter. Do not change the existing `build` command.

Pin `wrangler` at `4.112.0` in development dependencies and lock it in `/hyperopen/package-lock.json`. Add `test:cloudflare-worker`, `cloudflare:dev`, `cloudflare:check`, `deploy:cloudflare`, and `verify:cloudflare-worker` scripts. The deploy script must begin with `npm run build:cloudflare`; it must not run automatically from any ordinary build or test command.

## Plan of Work

### Milestone 1: Build a repeatable Cloudflare release input

First run `npm run setup:worktree` to obtain usable dependencies. Add the pinned Wrangler package, configuration, scripts, and a concise README section that distinguishes `npm run dev` from `npm run cloudflare:dev` and states that the Worker deploy is opt-in. Run `npx wrangler whoami` immediately before deployment rather than storing credentials in the repository.

Implement and test the release rewriter before deploying anything. Its unit test uses temporary JavaScript fixtures containing both HyperUnit literals, unrelated URL literals, and non-JavaScript files. It proves the exact strings become their relative mainnet/testnet proxy bases, unrelated content is byte-for-byte unchanged, a missing expected origin fails clearly, and a residual origin fails clearly. After `npm run build:cloudflare`, `rg -n 'https://api\.hyperunit' out/release-public/js --glob '*.js'` must have no output; `rg` against `/hyperopen/src` must still show the direct defaults.

### Milestone 2: Add the Worker and deterministic contracts

Implement the Worker helpers in platform-standard JavaScript with no Node imports or Node compatibility flag. Add `/hyperopen/tools/cloudflare/worker.test.mjs` using the Node test runner and fake `fetchImpl`/`ASSETS` bindings. The deterministic cases must assert mainnet, testnet, default-mainnet, boundary rejection, suffix/query preservation, safe request-header filtering, JSON-body forwarding, upstream status/body/header preservation, generic 502 failure, and non-proxy static delegation.

Start with `assets.run_worker_first` restricted to `/api/hyperunit/*`, then run `npx wrangler dev --local` against an already-built Cloudflare artifact. Prove `GET /trade` returns the route-specific trade title, generated document headers, and the expected fingerprinted stylesheet cache policy; prove its stylesheet is served without entering the proxy. Prove an unknown route has the expected release 404 behavior. This test is the header/route compatibility checkpoint.

If `_headers` is not applied by Workers Static Assets in that checkpoint, change the Worker design before deployment: make the Worker first for static assets and attach the existing document, font, immutable, and control cache policies to asset responses. Keep the policy values in one testable JavaScript contract and add a Node test that compares every required response header to `expectedDocumentHeaders()` from `/hyperopen/tools/release-assets/security_headers.mjs`. Do not deploy until `npm run verify:deployment-headers` succeeds against local `wrangler dev`.

The selective-routing decision has a concrete workload check, not a latency claim: use the local integration test with one route document, one stylesheet, and the release's referenced JavaScript modules, and assert the Worker router is called only for `/api/hyperunit/*` when the asset header path is compatible. Running Worker code for every module is the simpler configuration but is insufficient when selective routing preserves the same observable header contract; if the header fallback forces Worker-first static handling, record the invocation increase and its security rationale in this plan.

### Milestone 3: Validate, deploy, and verify publicly

Run `npm run test:cloudflare-worker`, `npm run test:release-assets`, `npm run test:playwright:seo`, and `npm run cloudflare:check`. The Cloudflare check must run `wrangler deploy --dry-run` only after `npm run build:cloudflare`, so configuration and asset upload preparation are evaluated against the real artifact shape.

Run the required repository gates from the repository root: `npm run gates`. If Java is unavailable on the execution host, run the gate on a host with JDK 21; do not claim a skipped ClojureScript compile as a product failure or as a successful gate.

Before the real deploy, repeat `npx wrangler whoami`; then run `npm run deploy:cloudflare` and capture the `workers.dev` URL that Wrangler prints. Do not configure a custom domain. Run `HYPEROPEN_VERIFY_ORIGIN=<returned-url> npm run verify:deployment-headers`, then `HYPEROPEN_VERIFY_ORIGIN=<returned-url> npm run verify:cloudflare-worker`. The latter must call only non-mutating proxy endpoints (`/api/hyperunit/mainnet/v2/estimate-fees` and the testnet equivalent), require a non-5xx status and JSON content type, and report the URL, status, and validated static paths without printing bodies or credentials.

## Concrete Steps

Run all commands from `/hyperopen`:

    npm run setup:worktree
    npm install --save-dev --save-exact wrangler@4.112.0
    npm run build:cloudflare
    npm run test:cloudflare-worker
    npx wrangler dev --local --port 8787

In a separate terminal after the local Worker reports ready:

    curl --fail --silent --show-error http://127.0.0.1:8787/trade -o /tmp/hyperopen-trade.html
    rg -q '<title>Trade perpetuals on Hyperliquid with an open-source client</title>' /tmp/hyperopen-trade.html
    HYPEROPEN_VERIFY_ORIGIN=http://127.0.0.1:8787 npm run verify:deployment-headers
    curl --fail --silent --show-error http://127.0.0.1:8787/api/hyperunit/mainnet/v2/estimate-fees

Stop the local Worker after the probe. Then run:

    npm run test:release-assets
    npm run test:playwright:seo
    npm run cloudflare:check
    npm run gates
    npx wrangler whoami
    npm run deploy:cloudflare
    HYPEROPEN_VERIFY_ORIGIN=https://hyperopen.<account>.workers.dev npm run verify:deployment-headers
    HYPEROPEN_VERIFY_ORIGIN=https://hyperopen.<account>.workers.dev npm run verify:cloudflare-worker

Expected success evidence includes a successful local `/trade` response with the trade title, no direct HyperUnit URL in release JavaScript, green Node/Playwright/repository gates, Wrangler's deployment URL, verified CSP/anti-framing/cache headers, and non-5xx JSON from both public proxy probes.

## Validation and Acceptance

- `npm run build:cloudflare` exits zero, writes `out/release-public`, and its rewriter reports at least one mainnet and testnet replacement. `rg -n 'https://api\.hyperunit' out/release-public/js --glob '*.js'` prints no matches; the same search in `/hyperopen/src` still finds the original direct development defaults.
- `npm run test:cloudflare-worker` exits zero and deterministically proves the Worker cannot proxy an arbitrary host, keeps the mainnet/testnet route mapping and query string, forwards only approved headers/body, returns upstream content faithfully, returns generic JSON 502 on upstream failure, and delegates non-API paths to static assets.
- Local `wrangler dev` returns HTTP 200 and the existing route-specific `<title>` for `/trade`, preserves every header required by `expectedDocumentHeaders()`, serves the linked fingerprinted CSS with `public, max-age=31556952, immutable`, and returns the release's expected 404 behavior for an unknown path.
- `npm run test:playwright:seo` exits zero before broadening validation; it proves the Cloudflare input artifact still supports release routes, metadata, and cache policies with no UI test changes.
- `npm run cloudflare:check`, `npm run check`, `npm test`, and `npm run test:websocket` all exit zero. `npm run gates` reports a PASS result for each of those required gates.
- `npm run deploy:cloudflare` prints a public `workers.dev` URL. Against that exact URL, `npm run verify:deployment-headers` exits zero and `npm run verify:cloudflare-worker` observes non-5xx JSON responses from both same-origin non-mutating fee endpoints.

## Idempotence and Recovery

`npm run build:cloudflare` may be rerun safely because it regenerates `out/release-public` before applying the deterministic rewrite; it never mutates source files. A failed local Worker may be stopped and restarted after the port is free. A failed dry run or deploy must be fixed locally and rechecked before another deploy attempt.

The upstream URLs are public variables, not secrets. Do not put API tokens, cookies, or account credentials in `wrangler.jsonc`, `.dev.vars`, test fixtures, command output, or this plan. If the deployed Worker regresses after a successful deploy, use `npx wrangler rollback <version-id>` only after obtaining the prior version id from Wrangler and record the rollback outcome here. Do not delete the Worker or its public URL as a recovery action.

## Artifacts and Notes

The final plan update must record the actual Worker URL, deploy version id, exact validation commands, and their pass/fail outcomes. It must also record whether the selective static-asset path accepted `_headers` or required the Worker header fallback, plus the observed Worker invocation behavior for the fixed local page-load workload.

No browser-inspection session is required because no view or interaction changes. The committed Playwright release suite is the deterministic browser proof; if any browser session is created while investigating deployment behavior, stop it with `npm run browser:cleanup` before handoff.

## Outcomes & Retrospective

Hyperopen is publicly deployed at `https://hyperopen.izhenghaocn.workers.dev` as Worker version `9e842a8f-8eb6-412f-a105-c5d15df6da66`. The deployment uses Workers Static Assets for the release artifact and invokes Worker code only for `/api/health` and `/api/hyperunit/*`; no `/hyperopen/src/**` files changed.

Workers Static Assets accepted the generated `_headers` file directly, so no Worker-first static header adapter was required. Local and public `verify:deployment-headers` runs passed for `/trade`, the fingerprinted CSS/main module, release metadata, site metadata, and service worker. The local unknown route remained 404, and the mainnet/testnet same-origin fee probes returned HTTP 200 JSON both locally and publicly.

Final validation was `test:cloudflare-worker` 11/11, `test:release-assets` 47/47, release Playwright status `passed`, `cloudflare:check` successful, and `npm run gates` 34/34 with 6,533 tests and 35,718 assertions. The main bundle remains 13,112 gzip bytes above its soft budget; the existing budget checker reports this without failing the release. Remaining operational risks are HyperUnit upstream availability and the lack of a custom domain, which was intentionally out of scope.

Plan change note (2026-07-21): created from the direct user request and verified repository/deployment discovery. It freezes a release-only same-origin endpoint rewrite to activate the required proxy without modifying `/hyperopen/src/**` or local UI behavior.

Plan change note (2026-07-21 10:53Z): recorded the implemented Worker/deployment artifacts, exact test and build outcomes, localhost test-fixture sandbox requirement, and the intentionally incomplete no-deploy validation boundary.

Plan change note (2026-07-21): recorded the repeated-release loader detector correction and its focused regression result. The current minified `$APP.d` alias is accepted without weakening rejection of non-loader source.

Plan change note (2026-07-21 11:19Z): recorded local Workers Static Assets compatibility, final 34/34 gate evidence, public deployment URL/version, and successful public header and proxy verification.
