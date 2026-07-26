# Eliminate the portfolio slash redirect

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This plan follows `.agents/PLANS.md` and tracks `bd` issue `hyperopen-g66z`.

## Purpose / Big Picture

The production portfolio URL `/portfolio?spectate=0x162cc7c861ebd0c06b3d72319201150482518185&range=1y&scope=all&chart=returns&bench=BTC&tab=performance-metrics` currently pays an extra HTTP redirect before the app shell can load. In the captured Chrome trace, that redirect accounts for roughly one throttled round trip before the final document starts loading. After this change, Cloudflare Pages should serve `/portfolio?...` directly as a `200` HTML response, while the app keeps the same canonical metadata and client-side portfolio behavior.

The visible proof is that the generated release artifact contains `portfolio.html` as the canonical static page for `/portfolio`, the local Pages-style smoke test fetches `/portfolio?...` without following a redirect, and the production deployment no longer returns `308 Location: /portfolio/?...` for the slashless portfolio URL.

## Progress

- [x] (2026-04-20 15:45Z) Created and claimed `bd` issue `hyperopen-g66z` for the redirect fix.
- [x] (2026-04-20 15:46Z) Created branch `codex/eliminate-portfolio-slash-redirect`.
- [x] (2026-04-20 15:50Z) Confirmed the redirect is caused by generated release file shape, not by the client router.
- [x] (2026-04-20 15:50Z) Recorded Cloudflare Pages behavior: `route.html` serves `/route`, while `route/index.html` redirects to `/route/`.
- [x] (2026-04-20 15:53Z) Added RED release-asset coverage for file-style route HTML and unsafe route path rejection; `node --test tools/release-assets/generate_release_artifacts.test.mjs` failed as expected.
- [x] (2026-04-20 15:56Z) Changed the release generator to emit file-style route HTML such as `portfolio.html` and reject traversal-like route paths.
- [x] (2026-04-20 15:56Z) Updated the local Pages-style static server to prefer `route.html` for slashless route requests before directory indexes.
- [x] (2026-04-20 15:58Z) Finished Pages-style smoke coverage for the production-shaped portfolio query URL with redirects disabled.
- [x] (2026-04-20 16:00Z) Passed `npm run test:release-assets` and `npm run test:playwright:seo`.
- [x] (2026-04-20 16:02Z) Passed `npm run check`, `npm test`, and `npm run test:websocket`; reviewer reported no blocking issues, and `hyperopen-g66z` was closed as completed.

## Surprises & Discoveries

- Observation: The existing Playwright static server masks the production redirect.
  Evidence: `tools/playwright/static_server.mjs` strips trailing slashes and serves `portfolio/index.html` for `/portfolio` with status `200`, while production Cloudflare Pages returned `308` from `/portfolio?...` to `/portfolio/?...`.

- Observation: The release-assets unit test encodes the directory-index convention through `routePathToOutputHtmlPath`.
  Evidence: `tools/release-assets/generate_release_artifacts.test.mjs` derives `STATIC_ROUTE_HTML_FILES` by calling the helper under test, and reads portfolio HTML from `portfolio/index.html`.

- Observation: Cloudflare Pages documents the serving rule needed for this fix.
  Evidence: Cloudflare Pages "Serving Pages" says matching HTML files are served for the requested route, and that `/contact.html` redirects to `/contact` while `/about/index.html` redirects to `/about/`.

- Observation: The RED release-assets tests fail on the intended behavior.
  Evidence: `node --test tools/release-assets/generate_release_artifacts.test.mjs` failed because `/portfolio` still produced `portfolio/index.html`, traversal-like paths did not throw, and `trade.html` was not generated.

- Observation: The generated artifact now has root-level route HTML files.
  Evidence: after `npm run test:playwright:seo`, `out/release-public` contained `portfolio.html`, `trade.html`, `api.html`, `leaderboard.html`, `vaults.html`, `staking.html`, and `funding-comparison.html`.

- Observation: The repository gates passed after the artifact-shape change.
  Evidence: `npm run check` completed all configured lint, docs, release-assets, and Shadow CLJS compile steps; `npm test` ran 3321 tests with 18134 assertions; `npm run test:websocket` ran 449 tests with 2701 assertions.

## Decision Log

- Decision: Fix all generated static public routes, not only `/portfolio`.
  Rationale: The generator applies the same directory-index rule to `/trade`, `/portfolio`, `/leaderboard`, `/vaults`, `/staking`, `/funding-comparison`, and `/api`. Changing only one route would leave the same performance and canonicalization bug elsewhere and would make the generator harder to reason about.
  Date/Author: 2026-04-20 / Codex

- Decision: Generate `route.html` for non-root routes instead of extensionless files.
  Rationale: Cloudflare Pages has documented clean-HTML handling for `.html` files. A no-extension file could create content-type ambiguity because Cloudflare also sends `X-Content-Type-Options: nosniff`; `route.html` keeps HTML content type obvious while still serving at `/route`.
  Date/Author: 2026-04-20 / Codex

- Decision: Do not also generate `route/index.html` in the first implementation.
  Rationale: Keeping both file and directory forms could preserve duplicate slash URLs but leaves file-versus-directory precedence ambiguous and can keep duplicate HTML reachable. The canonical site metadata already uses slashless routes, so the release artifact should make slashless URLs the only route-specific static HTML shape. The SPA fallback can still render unusual trailing-slash navigations, but the release-specific HTML target is slashless.
  Date/Author: 2026-04-20 / Codex

## Outcomes & Retrospective

The release artifact now emits file-style HTML entries for all static public non-root routes: `trade.html`, `portfolio.html`, `leaderboard.html`, `vaults.html`, `staking.html`, `funding-comparison.html`, and `api.html`. That aligns the generated output with Cloudflare Pages' documented extensionless HTML behavior, so `/portfolio?...` can be served from `portfolio.html` without the directory-index redirect to `/portfolio/?...`.

The local Pages-style static server now mirrors the relevant Cloudflare behavior: it tries `route.html` before directory indexes and redirects slashless requests to a trailing slash only when the artifact is still directory-index shaped. This makes the SEO smoke test capable of catching a regression back to `portfolio/index.html`.

Overall complexity increased slightly in test tooling because the static server now models clean-HTML lookup and directory-index redirects more accurately. Release artifact complexity decreased because each public route has one canonical generated HTML file instead of a directory-index shape that conflicts with slashless canonical URLs. The remaining verification is deployment-only: after this branch is deployed to Cloudflare Pages, production should be checked with the `curl` command in this plan to confirm that no dashboard-level Redirect Rule or Pages Function still forces `/portfolio` to `/portfolio/`.

## Context and Orientation

The app builds a production static artifact in `out/release-public`. The command `npm run build` refreshes build metadata, builds CSS, runs Shadow CLJS release builds, and then runs `node tools/release-assets/generate_release_artifacts.mjs`.

`tools/release-assets/site_metadata.mjs` is the source of truth for static public routes. Its public route list includes `/`, `/trade`, `/portfolio`, `/leaderboard`, `/vaults`, `/staking`, `/funding-comparison`, and `/api`, and its route canonical URLs are slashless except for `/`.

`tools/release-assets/generate_release_artifacts.mjs` rewrites `resources/public/index.html` into route-specific HTML pages with route-specific title, description, canonical URL, Open Graph metadata, Twitter metadata, and a route loading shell. The function `routePathToOutputHtmlPath(routePath)` currently maps `/portfolio` to `portfolio/index.html`. Cloudflare Pages treats that as a directory-index page and redirects slashless `/portfolio` to `/portfolio/`. The fix is to map `/portfolio` to `portfolio.html`, which Cloudflare Pages serves at `/portfolio`.

`tools/release-assets/generate_release_artifacts.test.mjs` owns deterministic unit coverage for release artifact shape. `tools/playwright/static_server.mjs` is the local Pages-style server used by `npm run test:playwright:seo`. `tools/playwright/test/seo.smoke.spec.mjs` verifies the generated release artifact with Playwright's HTTP client.

The phrase "slashless URL" means a route path such as `/portfolio` with no trailing `/`. The phrase "directory index" means a file named `index.html` inside a route directory, such as `portfolio/index.html`.

## Plan of Work

First, update tests before production code. In `tools/release-assets/generate_release_artifacts.test.mjs`, add direct helper assertions that `/portfolio` and `/portfolio/` both map to `portfolio.html`, while `/` still maps to `index.html`. Change the release artifact expectation so non-root static routes are `trade.html`, `portfolio.html`, `leaderboard.html`, `vaults.html`, `staking.html`, `funding-comparison.html`, and `api.html`. Read generated portfolio and API HTML from those files, and assert the old `portfolio/index.html` file is absent.

Second, update `tools/playwright/static_server.mjs` to emulate Cloudflare's clean HTML lookup. For an extensionless request such as `/portfolio`, the server should try an exact file, then `portfolio.html`, then a directory index if appropriate, then the existing 404 or SPA fallback behavior. This keeps local smoke tests meaningful after route HTML files move from directories to `.html` files.

Third, update `tools/playwright/test/seo.smoke.spec.mjs` so the portfolio smoke test requests the actual production-shaped query URL with redirects disabled and asserts `200`, no `Location` header, HTML content type, and the portfolio title. This test is local, so it cannot prove production edge behavior by itself, but it prevents the repository from regressing back to a directory-only artifact.

Fourth, implement the generator change in `tools/release-assets/generate_release_artifacts.mjs`. Change `routePathToOutputHtmlPath(routePath)` so `/` still returns `index.html` and each non-root normalized route returns `<route>.html`. Keep route normalization through `normalizePublicPath` and `publicPathToRelativePath` so inputs with a trailing slash still produce the canonical file path.

Finally, run focused validation, then the required gates. If validation passes, update this ExecPlan with outcomes, move it to `docs/exec-plans/completed/`, and close `hyperopen-g66z`.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/aba7/hyperopen`.

1. Add tests first and run the focused release-assets test:

    node --test tools/release-assets/generate_release_artifacts.test.mjs

   Before implementation, the new helper assertions should fail because `/portfolio` still maps to `portfolio/index.html`.

2. Patch `tools/release-assets/generate_release_artifacts.mjs`, `tools/playwright/static_server.mjs`, and `tools/playwright/test/seo.smoke.spec.mjs`.

3. Rerun focused tests:

    npm run test:release-assets
    npm run test:playwright:seo

   The release-assets suite should show all tests passing. The Playwright SEO suite should build `out/release-public`, serve it locally, and pass the portfolio direct-load smoke without a redirect.

4. Run required repository gates:

    npm run check
    npm test
    npm run test:websocket

   All three commands must pass before this work is complete. If one fails for an unrelated pre-existing reason, record the exact command, failure, and why it is unrelated in this plan and in the final report.

5. Close the issue after passing validation:

    bd close hyperopen-g66z --reason "Completed" --json

## Validation and Acceptance

Acceptance for the source change is that `routePathToOutputHtmlPath("/")` returns `index.html`, `routePathToOutputHtmlPath("/portfolio")` returns `portfolio.html`, and `routePathToOutputHtmlPath("/portfolio/")` also returns `portfolio.html`. A generated release artifact must contain `out/release-public/portfolio.html` and must not contain `out/release-public/portfolio/index.html`.

Acceptance for local browser-facing behavior is that `npm run test:playwright:seo` fetches `/portfolio?spectate=0x162cc7c861ebd0c06b3d72319201150482518185&range=1y&scope=all&chart=returns&bench=BTC&tab=performance-metrics` with redirects disabled and receives a `200` HTML response with no `Location` header and the title `Portfolio analytics and tearsheets`.

Acceptance for deployment behavior is that, after this branch is deployed to Cloudflare Pages, this command returns `HTTP/2 200` for the first response rather than `HTTP/2 308`:

    curl -sS -D - -o /dev/null 'https://hyperopen.xyz/portfolio?spectate=0x162cc7c861ebd0c06b3d72319201150482518185&range=1y&scope=all&chart=returns&bench=BTC&tab=performance-metrics'

Deployment verification is outside the local worktree, so local completion records the expected deployment check but does not claim the production URL has changed until the branch is deployed.

## Idempotence and Recovery

The release generator removes and recreates `out/release-public`, so rerunning `npm run build` or `npm run test:playwright:seo` is safe. Generated `out/` artifacts are not the source of truth. If a release-assets assertion fails, fix the generator or test expectation and rerun `npm run test:release-assets`. If Playwright finds the root HTML instead of portfolio HTML, check `tools/playwright/static_server.mjs` route resolution order before changing application code.

No destructive git operations are required. Do not run `git pull --rebase` or `git push` unless the user explicitly requests remote synchronization.

## Artifacts and Notes

Current production evidence before this fix:

    GET /portfolio?... -> HTTP/2 308
    Location: /portfolio/?...

Current generator behavior before this fix:

    routePathToOutputHtmlPath("/portfolio") -> "portfolio/index.html"

Expected generator behavior after this fix:

    routePathToOutputHtmlPath("/portfolio") -> "portfolio.html"
    routePathToOutputHtmlPath("/portfolio/") -> "portfolio.html"

Focused and required validation after this fix:

    node --test tools/release-assets/generate_release_artifacts.test.mjs
    # 22 tests passed

    npm run test:release-assets
    # 24 tests passed

    npm run test:playwright:seo
    # 6 tests passed

    npm run check
    # passed

    npm test
    # 3321 tests, 18134 assertions, 0 failures, 0 errors

    npm run test:websocket
    # 449 tests, 2701 assertions, 0 failures, 0 errors

## Interfaces and Dependencies

`tools/release-assets/generate_release_artifacts.mjs` must continue exporting:

    export function routePathToOutputHtmlPath(routePath)

The function must accept a route path string, normalize it with `normalizePublicPath`, return `index.html` for `/`, and return `${publicPathToRelativePath(normalizedPath)}.html` for non-root routes.

`generateReleaseArtifacts()` must continue returning `generatedRouteHtmlFiles` as a sorted array of every generated route HTML relative path. After this change, that array should contain `index.html` plus one `.html` file per non-root static route.

`tools/playwright/static_server.mjs` must remain a standalone Node HTTP server with no new npm dependencies. Its route resolution must stay rooted under `PLAYWRIGHT_STATIC_ROOT` and must continue rejecting paths that escape the root directory.

Plan revision note, 2026-04-20: Initial plan created after local trace analysis, Cloudflare Pages documentation review, and read-only implementation-surface exploration. The chosen direction is file-style route HTML because it addresses the documented Pages redirect rule directly and avoids no-extension content-type ambiguity.

Plan revision note, 2026-04-20: Added the RED test result to Progress and Surprises & Discoveries before changing implementation code.

Plan revision note, 2026-04-20: Updated progress after implementing the generator/static-server changes and passing focused release and Playwright SEO validation.

Plan revision note, 2026-04-20: Marked the plan complete after required gates passed, read-only review found no blocking issues, and `hyperopen-g66z` was closed.
