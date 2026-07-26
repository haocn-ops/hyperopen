# Pages-Style Static Fallback And SEO Smoke Checks

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md`. The tracked `bd` issue for this work is `hyperopen-85a4`; the `bd` issue is historical local tracker context from the active phase.

## Purpose / Big Picture

Local Playwright validation currently hides release regressions because `/hyperopen/tools/playwright/static_server.mjs` falls straight back to root `index.html` whenever a path is missing. That means requests like `/robots.txt` can return HTML and route directories such as `/trade` never exercise the generated `/trade/index.html` file. After this change, the local static server will resolve requests in a Cloudflare Pages-like order, and the committed Playwright smoke suite will assert basic SEO/static-surface expectations against the generated release artifact.

## Progress

- [x] (2026-03-31 18:46Z) Created and claimed `bd` issue `hyperopen-85a4` for Pages-style static fallback and SEO smoke checks.
- [x] (2026-03-31 18:47Z) Audited the current local static server, Playwright smoke suite, and Playwright web-server config to confirm the existing fallback behavior and test surface.
- [x] (2026-03-31 18:51Z) Updated the static server to resolve exact files, directory index files, and 404/SPA fallback in Pages-style order.
- [x] (2026-03-31 19:01Z) Added committed SEO smoke assertions for `robots.txt`, `sitemap.xml`, route-specific titles, and lowercase `/api`, documented the local smoke workflow, and ran validation.

## Surprises & Discoveries

- Observation: the committed Playwright config already uses the repo-local static server as its `webServer`, so changing that server automatically improves all local smoke coverage.
  Evidence: `/hyperopen/playwright.config.mjs`.

- Observation: the existing smoke suite already covers route entry points such as `/trade`, `/portfolio`, and `/vaults`, so the SEO checks can live alongside those route smokes instead of requiring a new runner.
  Evidence: `/hyperopen/tools/playwright/test/routes.smoke.spec.mjs`.

- Observation: the Playwright route helpers intentionally start from `/index.html` and navigate in-app through `HYPEROPEN_DEBUG`, so direct SEO/static-surface checks must use raw HTTP requests instead of those helpers.
  Evidence: `visitRoute()` in `/hyperopen/tools/playwright/support/hyperopen.mjs`.

## Decision Log

- Decision: keep the new SEO checks in the committed Playwright smoke suite rather than creating a separate ad hoc script.
  Rationale: the same local static server and route-entry behavior need to be validated in CI-safe browser coverage, and the existing smoke suite already owns the fast route-level checks.
  Date/Author: 2026-03-31 / Codex

## Outcomes & Retrospective

`/hyperopen/tools/playwright/static_server.mjs` now serves files in Pages-style order: exact file, directory `index.html`, SPA fallback only when root `404.html` is absent, and nearest `404.html` otherwise. The server also accepts `PLAYWRIGHT_STATIC_ROOT`, so local validation can point directly at `/hyperopen/out/release-public`.

The committed Playwright smoke suite now includes `/hyperopen/tools/playwright/test/seo.smoke.spec.mjs`, which asserts that `/robots.txt` is plain text without HTML, `/sitemap.xml` is XML, `/trade` and `/portfolio` return route-specific titles from the server response, and `/api` keeps lowercase canonical metadata. `/hyperopen/playwright.config.mjs` now builds the compiled app/workers, generates `out/release-public`, and serves that release artifact for Playwright runs. `/hyperopen/README.md` now documents the Pages-style local smoke flow instead of recommending `serve -s`.

Validation passed for `npm run test:release-assets`, `npm run build`, manual startup of `PLAYWRIGHT_STATIC_ROOT=out/release-public node tools/playwright/static_server.mjs`, the dedicated SEO smoke spec, `npm test`, and `npm run test:websocket`. `npm run check` initially failed only because completed ExecPlans were still left in `docs/exec-plans/active/`; that governance issue is being corrected by moving the completed plans out of `active`.

## Context and Orientation

`/hyperopen/tools/playwright/static_server.mjs` currently resolves only an exact file and otherwise serves `/hyperopen/resources/public/index.html`. `/hyperopen/playwright.config.mjs` starts that server after `npm run css:build` and `npx shadow-cljs compile app`. The quick committed Playwright smoke suite lives in `/hyperopen/tools/playwright/test/routes.smoke.spec.mjs`.

The release artifact generator now emits route-specific HTML pages plus `robots.txt` and `sitemap.xml`, but the local Playwright server still masks missing-file regressions because it never tries `directory/index.html` and it serves the SPA shell for missing files like `/robots.txt`. The static server and smoke checks need to validate the actual release artifact behavior more faithfully.

## Plan Of Work

Refactor the static server so path resolution is explicit and deterministic: check the exact file target first, then check the corresponding directory `index.html`, then use root `index.html` only when no top-level `404.html` exists, otherwise serve the nearest matching `404.html` or root `404.html` with a 404 status. Keep the server rooted in a configurable static root so the smoke suite can target `out/release-public` directly when needed.

Extend the committed Playwright route smoke spec with lightweight SEO/static-surface checks that request `/robots.txt`, `/sitemap.xml`, `/trade`, `/portfolio`, and `/api`. The checks should verify text/XML content types, absence of HTML in `robots.txt`, route-specific document titles, and lowercase `/api` canonical metadata. Add one short note to the repo docs describing how to smoke-test the release artifact locally with the updated static server.

## Concrete Steps

Run from `/Users/barry/.codex/worktrees/dfa4/hyperopen`:

1. `npm run test:release-assets`
2. Start the local static server
3. Execute the new smoke tests
4. `npm run build` if the environment remains healthy
5. `npm run check`
6. `npm test`
7. `npm run test:websocket`

Expected observable outcomes:

- `/robots.txt` is served as plain text instead of HTML
- `/sitemap.xml` is served as XML
- `/trade` resolves to `/trade/index.html` before any SPA fallback and exposes the trade-specific title
- `/portfolio` resolves to `/portfolio/index.html` before any SPA fallback and exposes the portfolio-specific title
- `/api` HTML exposes lowercase canonical metadata

## Validation And Acceptance

This work is complete when the local static server resolves release output like Cloudflare Pages for the covered cases, the committed smoke suite fails if `robots.txt` or `sitemap.xml` regress to HTML, and the route-specific title/canonical checks fail if the release artifact stops serving the per-route HTML entries.

## Idempotence And Recovery

The static server changes are local and deterministic. The smoke tests should be rerunnable against the same build output without cleanup. If a smoke check fails because the server is still serving `resources/public`, rerun against `out/release-public` after regenerating the release artifact with `node tools/release-assets/generate_release_artifacts.mjs`.
