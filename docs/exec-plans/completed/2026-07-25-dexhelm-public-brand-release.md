# Publish the DEXHelm public brand

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. It follows `.agents/PLANS.md`.

## Purpose / Big Picture

The deployed Testnet terminal still displays the upstream `HO / HyperOpen` identity even though the operator domain and informational pages are DEXHelm. After this change, the public Cloudflare release will use one validated DEXHelm tenant configuration for the header logo, wordmark, product-context banner, browser title, route metadata, and canonical Testnet origin. The upstream namespaces, storage keys, open-source license, and local default build remain unchanged.

## Context References

Public refs:
- Direct maintainer request on 2026-07-25 with a screenshot of the current header: "把logo和品牌换掉".

Repo artifacts:
- `src/hyperopen/service/tenant_config.cljs` and `tools/white-label/**` provide the existing compile-time public white-label boundary.
- `tools/release-assets/site_metadata.mjs` applies tenant identity to route metadata.
- `workers/hyperopen-worker.mjs` and `wrangler.jsonc` publish the shared DEXHelm Worker while keeping Mainnet closed.
- `docs/FRONTEND.md`, `docs/agent-guides/browser-qa.md`, and `.agents/skills/deploy-hyperopen-cloudflare/SKILL.md` define UI and release validation.

Local scratch refs (non-authoritative):
- The user-supplied screenshot at `/var/folders/zc/j_bqz4612_z17yhl0swbq8l40000gn/T/TemporaryItems/NSIRD_screencaptureui_RfotLy/Screenshot 2026-07-25 at 18.58.21.png`.

## Progress

- [x] (2026-07-25 19:10+08:00) Confirmed the live header uses the default `HO / HyperOpen` voice because the Cloudflare build does not compile a tenant override.
- [x] (2026-07-25 19:15+08:00) Selected the existing white-label compiler boundary instead of renaming internal Hyperopen APIs and storage keys.
- [x] (2026-07-25 20:40+08:00) Restored `:tenant/override` to the memoized trade-chart projection and added a focused trade-view regression contract; the chart VM now receives the same DEXHelm override it uses for the legend venue.
- [x] (2026-07-25 21:10+08:00) Added the DEXHelm tenant, helm-wheel logo, deterministic build/metadata/browser contracts, and a Cloudflare adapter that preserves verified tenant-manifest digests after endpoint rewriting.
- [x] (2026-07-25 21:30+08:00) Fixed the 375-pixel mobile header so its nonshrinking logo no longer overlaps the wordmark, then added both Hiccup class contracts and a release-Playwright geometry assertion.
- [x] (2026-07-25 21:38+08:00) Passed branded Playwright at 375/768/1280/1440, the governed six-pass QA record, artifact preflight, Wrangler dry-run, and all 34 repository gates.
- [x] (2026-07-25 21:45+08:00) Deployed the same `hyperopen` Worker as version `906ea1c8-e2cd-42d4-915f-db8d0f8ebe56`; verified Mainnet remains 503, Testnet renders DEXHelm, both public Worker verifiers pass, and browser sessions are cleaned up.

## Surprises & Discoveries

- Observation: runtime, document-title, and release metadata branding already derive from a compile-time tenant JSON value.
  Evidence: `hyperopen.config/TENANT_CONFIG_JSON` is injected by `tools/white-label/build_release.mjs`, while `header-vm`, `document-title`, and `site_metadata.mjs` consume the normalized tenant.
- Observation: before this change, the standard Cloudflare command built the unbranded default artifact under `out/release-public`.
  Evidence: the prior `package.json` contract ran the ordinary build plus HyperUnit endpoint rewriting, and the prior `wrangler.jsonc` pointed Static Assets at `out/release-public`; both now select `out/white-label/dexhelm` through the dedicated adapter.
- Observation: the chart VM was tenant-aware, but the memoized trade-view projection removed `:tenant/override` before invoking it.
  Evidence: the first public browser run displayed `BTC · 1D · Hyperopen`; adding `:tenant/override` to `trade-chart-view-base-state-keys` changed the public legend to `BTC · 1D · DEXHelm` and the focused regression receives the alternate tenant.
- Observation: branded deployments need CSP image sources derived from public tenant data rather than a fixed source list.
  Evidence: `verify_deployment_headers.mjs` now reads the public `tenant-manifest.json`; the Worker-origin verifier accepts `https://testnet.dexhelm.com` for the same-origin logo and the release-asset suite passes 50/50.
- Observation: at 375 pixels the mobile brand button could flex-shrink to its eight pixels of horizontal padding while its 24-pixel image overflowed over the wordmark.
  Evidence: the first public measurement was logo right `86` and wordmark left `74`; after `shrink-0` and the narrower xs wordmark slot, the final measurement is logo right `86`, wordmark left `98`, a 12-pixel gap, and document width exactly 375.
- Observation: the repository `browser:inspect` command waits for `Page.loadEventFired`, which can time out on this continuously connected public trading page even when the page is interactive.
  Evidence: `inspect-2026-07-25T13-03-11-710Z-4f3954c0` failed on that event; the in-app browser immediately returned a complete DOM snapshot, live market data, responsive screenshots, and zero console errors.

## Decision Log

- Decision: represent DEXHelm as a checked-in white-label tenant and keep the generic local build compatible with upstream Hyperopen.
  Rationale: one normalized public configuration keeps the header, titles, metadata, theme, feature flags, and affiliate disclosure consistent without duplicating user-facing brand constants across application source.
  Date/Author: 2026-07-25 / Codex.
- Decision: use a compact teal helm-wheel mark served from the same DEXHelm origin.
  Rationale: the symbol remains legible at the existing 28-pixel header size, works without an external asset host, and is distinct from the current `HO` fallback.
  Date/Author: 2026-07-25 / Codex.
- Decision: publish Cloudflare Static Assets directly from the verified DEXHelm white-label output directory.
  Rationale: this avoids copying or mutating the ordinary release artifact and preserves the white-label build's atomic publication and manifest verification boundaries.
  Date/Author: 2026-07-25 / Codex.
- Decision: keep internal Hyperopen namespaces, storage identifiers, source attribution, and upstream social links unchanged.
  Rationale: the request concerns operator-facing product identity; renaming internal or licensed upstream identifiers would create migration and attribution risk without improving the public terminal.
  Date/Author: 2026-07-25 / Codex.
- Decision: make non-overlap a release-artifact browser contract, not only a class-level unit contract.
  Rationale: the defect depended on computed flex geometry at 375 pixels, so `logoRight <= wordmarkLeft` is the narrowest deterministic proof of the user-visible behavior.
  Date/Author: 2026-07-25 / Codex.

## Outcomes & Retrospective

DEXHelm is now the public identity on the existing Testnet terminal. The header, logo, product-context banner, browser title, canonical metadata, tenant manifest, and chart legend all derive from the validated DEXHelm tenant. The final Worker version is `906ea1c8-e2cd-42d4-915f-db8d0f8ebe56` at `https://hyperopen.izhenghaocn.workers.dev`; `testnet.dexhelm.com` is 200, `app.dexhelm.com` remains intentionally closed with 503, and the apex and status sites remain 200.

The implementation reduced public-release complexity because one tenant config now owns every operator-facing brand surface and the Cloudflare adapter consumes the verified tenant output directly. It added a small amount of release tooling and validation complexity, but that complexity is bounded by one build adapter, manifest-driven CSP verification, and deterministic tests. The remaining operational risks are upstream Hyperliquid/HyperUnit availability and a soft main-bundle gzip warning of 653,917 bytes versus the 640,000-byte target (+13,917); the budget was not re-ratcheted.

## Context and Orientation

Hyperopen is a ClojureScript browser application. Its public tenant configuration contains a tenant id, brand name, HTTPS logo URL, theme, enabled feature flags, venue identity, and affiliate disclosure. `tools/white-label/build_release.mjs` validates this JSON, compiles the app with the tenant embedded as a Closure define, emits isolated route artifacts, and verifies a digest manifest before publication. The Cloudflare adapter then rewrites only the generated HyperUnit API origins to same-origin Worker proxy paths. Because this rewrite changes the main JavaScript bytes, the DEXHelm Cloudflare adapter must refresh the tenant manifest digests and rerun white-label verification before Wrangler can upload the artifact.

The public header already renders a tenant logo image when `brand/logo-url` is present, and falls back to the tenant's first letter only if that image fails. The application title and product-context banner use the same tenant state. No trading, wallet, signing, WebSocket, or order behavior needs to change.

## Plan of Work

Add `config/white-label/dexhelm.json` with terminal and analytics enabled, affiliate unavailable, the dark theme, canonical Hyperliquid venue data, and a same-origin public logo URL. Add the logo under `resources/public/brand/` as an SVG with stable dimensions and accessible image use through the existing header `alt` contract.

Create a small Cloudflare build adapter that calls the existing white-label build for `https://testnet.dexhelm.com`, rewrites HyperUnit endpoints in that isolated output, refreshes the tenant manifest's main-bundle and full artifact digests, and reruns `verifyWhiteLabelRelease`. Point Wrangler Static Assets to this exact output and update the repository preflight contract accordingly. Keep `npm run build` and its `out/release-public` output unchanged.

Extend deterministic tests so the DEXHelm config validates, the Cloudflare build contract selects the branded artifact, and the white-label Playwright spec can assert DEXHelm's tenant id, logo URL, enabled route, origin, metadata, header text, and title at all four required widths. Run the governed design review against the prepared branded release and account for all six passes.

## Concrete Steps

Run from `/Users/zh/Documents/Hyperopen`:

    npm run setup:worktree
    npm run white-label:validate -- --config config/white-label/dexhelm.json --origin https://testnet.dexhelm.com
    node --test tools/cloudflare/*.test.mjs tools/white-label/*.test.mjs tools/release-assets/*.test.mjs
    npm run build:cloudflare
    npm run verify:white-label -- --config config/white-label/dexhelm.json --origin https://testnet.dexhelm.com --output out/white-label/dexhelm
    PLAYWRIGHT_WHITE_LABEL_ROOT=out/white-label/dexhelm HYPEROPEN_EXPECT_BRAND=DEXHelm HYPEROPEN_EXPECT_TENANT_ID=dexhelm HYPEROPEN_EXPECT_ORIGIN=https://testnet.dexhelm.com HYPEROPEN_EXPECT_ENABLED_ROUTE=/trade HYPEROPEN_EXPECT_LOGO_URL=https://testnet.dexhelm.com/brand/dexhelm-mark.svg npm run test:playwright:white-label
    node .agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs --artifact
    npm run cloudflare:check
    env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH NODE_OPTIONS=--localstorage-file=/tmp/hyperopen-node-gates-localstorage npm run gates
    npx wrangler whoami
    env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH npm run deploy:cloudflare

## Validation and Acceptance

At 375, 768, 1280, and 1440 pixels, the first viewport must show the DEXHelm wordmark and the new logo without clipping, horizontal overflow, overlap, or layout shifts. The browser title and `site-metadata.json` must contain DEXHelm; route canonical URLs must use `https://testnet.dexhelm.com`; and the main bundle and tenant manifest must identify tenant `dexhelm`. The `HO`, `HyperOpen`, and default Hyperopen product label must not remain in public header or title surfaces.

The public deployment is accepted only when `app.dexhelm.com` remains closed with HTTP 503, `testnet.dexhelm.com/trade?hyperliquidNetwork=testnet` returns HTTP 200 and renders the DEXHelm identity, the apex and status documents remain available, both Worker verifiers pass, all six governed QA passes are accounted for, and all 34 repository gates pass.

## Idempotence and Recovery

The white-label build uses tenant-scoped locks, staging, verification, and atomic publication and can be repeated. Endpoint rewriting is allowed exactly once per fresh build and fails closed on a repeated run. If deployment verification fails, stop and request explicit rollback authorization for the previous Worker version. Reverting the Wrangler asset directory and `build:cloudflare` contract to the generic release restores the prior publication model but should be done only through the same tests and deployment gates.

## Artifacts and Notes

The governed design review is `tmp/browser-inspection/design-review-2026-07-25T11-32-16-078Z-611e461f` and records PASS for visual evidence, native controls, styling consistency, interaction, layout regression, and jank/perf at 375, 768, 1280, and 1440 pixels. Final branded Playwright passed 4/4 and includes the 375-pixel logo/wordmark geometry assertion. The final in-app browser measurement recorded a 12-pixel mobile logo-to-wordmark gap, exact viewport/document widths of 375, a DEXHelm chart legend, and no console errors; desktop recorded a visible logo, exact DEXHelm wordmark, DEXHelm chart legend, no legacy header brand, and no console errors.

The DEXHelm config digest is `CFAA6AB3AF1530A34D5D16DAD1BA0E186F20048B50E09A76DEF230FD7DF25D94`. Artifact preflight passed 39 checks with the expected JDK and Node-localStorage environment warnings. The corrected full gate run passed 34/34 with 6,546 tests and 35,754 assertions. Public response-header verification passed against `/trade` and the final fingerprinted CSS/JavaScript assets; both HyperUnit fee probes returned 200 JSON. The main bundle `main.5F26A4D56428FBB66BB8BD1E9C09E88C.js` is 653,917 gzip bytes, 13,917 over the advisory 640,000-byte target.

## Interfaces and Dependencies

The tenant file uses the existing strict public schema in `tools/white-label/tenant_config.mjs`. The Cloudflare adapter uses only Node standard-library file APIs plus exported repository helpers from `tools/white-label/build_release.mjs`, `tools/white-label/verify_release.mjs`, `tools/release-assets/generate_release_artifacts.mjs`, and `tools/cloudflare/rewrite_hyperunit_release_endpoints.mjs`. No new package, backend, wallet permission, private key, API credential, or external logo host is permitted.

Revision note: created on 2026-07-25 after discovery confirmed the existing tenant compiler is the narrowest coherent way to replace the deployed logo and public brand. Completed on 2026-07-25 after the final Worker deployment, public verifiers, responsive browser measurements, and cleanup passed.
