# Release-Public SEO Basics

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md`. The tracked `bd` issue for this work is `hyperopen-i4do`; the `bd` issue is historical local tracker context from the active phase.

## Purpose / Big Picture

Hyperopen's production release artifact currently omits crawl/index basics even though the app entry references root assets and the deployment needs a stable canonical origin. After this change, `out/release-public/` will contain the release HTML, the referenced root SEO assets, a plain-text `robots.txt`, and a `sitemap.xml` that enumerates the public routes `/`, `/trade`, `/portfolio`, `/leaderboard`, `/vaults`, `/staking`, `/funding-comparison`, and `/api`. The generated metadata will treat `/api` as the canonical API-wallet route while still accepting legacy uppercase requests in the client route parser.

## Progress

- [x] (2026-03-31 17:29Z) Created and claimed `bd` issue `hyperopen-i4do` for the release-public SEO artifact fix.
- [x] (2026-03-31 17:29Z) Audited the current release generator, tracked app entry, root public assets, and API-wallet route parsing to confirm the missing surfaces.
- [x] (2026-03-31 17:34Z) Added `tools/release-assets/site_metadata.mjs`, injected release-only canonical/site metadata into the generated HTML, emitted `robots.txt` and `sitemap.xml`, copied declared root assets fail-closed, and normalized the API-wallet canonical route to `/api`.
- [x] (2026-03-31 17:39Z) Extended the node tests and updated the affected ClojureScript view/API-wallet tests for the lowercase canonical route.
- [x] (2026-03-31 17:41Z) Installed lockfile dependencies with `npm ci` after the required repo gate failed for environment reasons (`zod` and `smol-toml` were missing from the local workspace).
- [x] (2026-03-31 17:45Z) Ran `npm run check`, `npm test`, `npm run test:websocket`, `npm run css:build`, `npx shadow-cljs release app portfolio-worker vault-detail-worker`, and `node tools/release-assets/generate_release_artifacts.mjs` successfully.

## Surprises & Discoveries

- Observation: the tracked app entry already references `favicon.svg`, `favicon-16x16.png`, `favicon-32x32.png`, `apple-touch-icon.png`, and `favicon.ico`, but the release generator only copies `sw.js` and `favicon.ico`.
  Evidence: `resources/public/index.html` vs. `tools/release-assets/generate_release_artifacts.mjs`.

- Observation: there is no current canonical-link, robots, sitemap, or route-metadata generation in the release pipeline.
  Evidence: repository search on 2026-03-31 found no existing `rel="canonical"`, `robots.txt`, `sitemap.xml`, or site-metadata generator under `resources/public`, `src`, or `tools/release-assets`.

- Observation: injecting a new inline SEO `<script>` into the head made the old manifest-bootstrap regex over-match and strip part of the document until the rewrite logic was narrowed to the specific bootstrap script body.
  Evidence: the first `npm run test:release-assets` run failed because the generated fixture HTML lost the favicon links and stylesheet after the SEO injection.

- Observation: the standalone generator is intentionally not self-bootstrapping; it needs `resources/public/css/main.css` plus release JS outputs to exist first.
  Evidence: the first direct `node tools/release-assets/generate_release_artifacts.mjs` attempt failed with `ENOENT` on `resources/public/js/manifest.json`, and the command passed immediately after `npm run css:build` plus `npx shadow-cljs release app portfolio-worker vault-detail-worker`.

## Decision Log

- Decision: keep the SEO source of truth in the release-assets toolchain instead of inventing a second runtime-only metadata registry.
  Rationale: this task is about the release artifact, and the generator is the single place that already rewrites production HTML and decides which public files ship in `out/release-public`.
  Date/Author: 2026-03-31 / Codex

- Decision: preserve backwards-compatible route matching for API-wallet paths while changing the canonical route constant and generated SEO metadata to lowercase `/api`.
  Rationale: user navigation or old links should not break, but search/canonical surfaces must converge on one lowercase route.
  Date/Author: 2026-03-31 / Codex

- Decision: keep future favicon/social-asset copying driven by head references plus one explicit required root asset (`/sw.js`) instead of hard-coding a longer static copy list in the generator.
  Rationale: this keeps the release artifact aligned with `resources/public/index.html` automatically when future OG/social images or root icons are added, while still failing closed if a declared file is missing.
  Date/Author: 2026-03-31 / Codex

## Outcomes & Retrospective

The release artifact now has an explicit site metadata source of truth in `/hyperopen/tools/release-assets/site_metadata.mjs`. That module defines the public route list, canonical-origin normalization, and root-asset discovery contract. `generate_release_artifacts.mjs` now emits `site-metadata.json`, `robots.txt`, and `sitemap.xml`, rewrites the release HTML with a canonical-link plus route-aware metadata bootstrap, and copies every declared head/root asset plus `sw.js` into `/hyperopen/out/release-public` with fail-closed checks.

The API-wallet canonical route is now lowercase `/api`, and the release metadata plus generated sitemap both use that canonical form while the route parser still accepts uppercase legacy input. The node release-assets tests now cover robots generation, sitemap generation, root-asset copying, preview-origin fallback, missing SEO assets, and lowercase `/api`; the affected ClojureScript tests for header/API navigation were updated to the same canonical route.

This change reduced overall complexity. The release artifact contract is now centralized instead of being split between a narrow JS/CSS copier and ad hoc manual assumptions about root assets or canonical URLs. The only extra moving part is a small metadata module, and it replaces repeated implicit knowledge with explicit generated outputs.

## Context and Orientation

`tools/release-assets/generate_release_artifacts.mjs` builds the shipping `out/release-public/` directory after the ClojureScript release. It fingerprints CSS, rewrites `resources/public/index.html` to point at the hashed CSS and main JS bundle, and copies a narrow subset of public assets. `resources/public/index.html` is the static HTML shell that declares favicon, apple-touch-icon, service-worker, and future SEO head assets. `src/hyperopen/api_wallets/actions.cljs` defines the canonical API-wallet route used by navigation and route matching. The route parser currently accepts `/api` case-insensitively, but the exported canonical route constant is `/API`, which is the wrong canonical output for search metadata and navigation links.

The release fix needs one small metadata source of truth that names the public indexable routes, the stable canonical origin, and the root SEO assets. The generator must use that source to emit `robots.txt`, `sitemap.xml`, and any generated route metadata/config, and it must fail closed if a declared SEO asset is missing from `resources/public`. The canonical origin must come from `HYPEROPEN_SITE_ORIGIN` when set, and otherwise fall back to a stable production-safe default rather than a preview URL or a hash-path URL.

## Plan of Work

Add a release-assets metadata module under `tools/release-assets/` that exports the canonical public routes, helper functions for canonical-origin normalization, and the declared root SEO assets. Use that module from `generate_release_artifacts.mjs` to rewrite the release HTML with canonical/site metadata, generate `robots.txt` and `sitemap.xml`, and copy every declared root SEO asset into `out/release-public/`.

Update `resources/public/index.html` only where the release HTML needs new head placeholders or metadata hooks. Keep preview noindex behavior untouched by avoiding any attempt to remove platform-level preview controls. Update `src/hyperopen/api_wallets/actions.cljs` so `canonical-route` is `/api` while `parse-api-wallet-route` still recognizes uppercase input as the same page.

Extend `tools/release-assets/generate_release_artifacts.test.mjs` so the node tests assert that `robots.txt` is emitted as plain text, `sitemap.xml` contains the canonical origin and every public route, declared root assets are copied, missing SEO assets fail the build, and the generated metadata/config exposes lowercase `/api`.

## Concrete Steps

Run from `/Users/barry/.codex/worktrees/dfa4/hyperopen`:

1. `npm ci`
2. `npm run test:release-assets`
3. `npm run check`
4. `npm test`
5. `npm run test:websocket`
6. `npm run css:build`
7. `npx shadow-cljs release app portfolio-worker vault-detail-worker`
8. `node tools/release-assets/generate_release_artifacts.mjs`

Expected observable outcomes:

- the release-assets test suite passes and includes assertions for sitemap, robots, root-asset copying, and lowercase `/api`
- the generator prints that it created `out/release-public`
- `out/release-public/robots.txt` exists and is plain text
- `out/release-public/sitemap.xml` exists and lists the canonical origin with the public routes

## Validation and Acceptance

This work is complete when `out/release-public/` contains `index.html`, the fingerprinted CSS, the release JS files, the required root SEO assets, `robots.txt`, and `sitemap.xml`; when generated site metadata/config uses lowercase `/api`; and when the release-assets tests plus the required repository gates pass. Manual inspection of `out/release-public/index.html`, `out/release-public/robots.txt`, and `out/release-public/sitemap.xml` must show a stable canonical origin rather than a preview/hash URL.

## Idempotence and Recovery

The generator already removes and recreates `out/release-public/`, so rerunning it is safe. The new fail-closed checks should stop the build before producing a silently incomplete artifact if an SEO asset declaration drifts from `resources/public`. If a validation command fails, fix the underlying code or test expectation and rerun the same command; no destructive recovery steps are required.

## Artifacts and Notes

Initial issue creation:

    bd create "Make release-public SEO artifact complete for crawl/index basics" --description="Patch tools/release-assets/generate_release_artifacts.mjs so release-public includes robots.txt, sitemap.xml, canonical-origin-backed site metadata, and every declared root SEO asset. Normalize the API canonical route to /api while preserving backwards-compatible route matching. Add/extend node tests for sitemap, robots, root asset copying, and lowercase /api metadata." -t bug -p 1 --json

Claim:

    bd update hyperopen-i4do --claim --json

Validation and release prep:

    npm ci
    npm run test:release-assets
    npm run check
    npm test
    npm run test:websocket
    npm run css:build
    npx shadow-cljs release app portfolio-worker vault-detail-worker
    node tools/release-assets/generate_release_artifacts.mjs

## Interfaces and Dependencies

The release-assets metadata module must export stable functions and constants that `tools/release-assets/generate_release_artifacts.mjs` and its tests can import directly. At minimum, it must define the canonical origin helper, the public route metadata list, and the required root SEO assets list. `src/hyperopen/api_wallets/actions.cljs` must continue exporting `canonical-route`, `api-wallet-route?`, and `parse-api-wallet-route` with compatible behavior for existing callers.

Plan revision note: 2026-03-31 17:29Z - Created the active ExecPlan after auditing the release generator, public HTML shell, and API-wallet route handling for `hyperopen-i4do`.
Plan revision note: 2026-03-31 17:45Z - Updated the plan with the implemented metadata/generator changes, the validation results, the environment dependency install, and the final release-build preparation commands before moving the document to `completed`.
