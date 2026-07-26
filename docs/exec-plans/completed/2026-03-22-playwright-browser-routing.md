# Integrate Playwright Deterministic E2E Alongside Browser MCP

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The tracked work item for this plan is `hyperopen-uk06`; the `bd` issue is historical local tracker context for this plan.

## Purpose / Big Picture

After this change, Hyperopen should have a clear split between two browser-testing jobs instead of using one tool for everything. Browser MCP must stay available for live browser attachment, exploratory debugging, parity capture, design review, and one-off investigation. Playwright must become the repo-owned deterministic browser test framework for committed end-to-end coverage, reusable assertions, and CI execution. A future contributor should be able to open `/hyperopen/AGENTS.md`, choose the right tool immediately, run one exact command for local smoke or CI, and understand which stable Browser MCP flows were promoted into committed Playwright coverage.

## Progress

- [x] (2026-03-22 22:10Z) Audited the current package manager, test stack, CI workflow, frontend entrypoints, and browser-inspection MCP tooling from `/hyperopen/package.json`, `/hyperopen/package-lock.json`, `/hyperopen/.github/workflows/tests.yml`, `/hyperopen/shadow-cljs.edn`, `/hyperopen/resources/public/index.html`, `/hyperopen/.codex/config.toml`, and `/hyperopen/tools/browser-inspection/**`.
- [x] (2026-03-22 22:20Z) Created and claimed `bd` issue `hyperopen-uk06` for the Playwright plus Browser MCP routing work.
- [x] (2026-03-22 22:20Z) Confirmed the worktree was clean before implementation and that Browser MCP is already project-scoped through `/hyperopen/.codex/config.toml`.
- [x] (2026-03-22 22:24Z) Identified the stable Browser MCP scenarios that are suitable for Playwright promotion: route smoke on `/trade`, `/portfolio`, and `/vaults`; asset selection; funding deposit modal; wallet enable-trading; order submit/cancel gating; mobile account-surface selection; and mobile position-margin sheet presentation.
- [x] (2026-03-22 22:39Z) Added Playwright in the repo’s existing Node and `.mjs` tooling style through `/hyperopen/playwright.config.mjs`, `/hyperopen/tools/playwright/**`, `/hyperopen/package.json`, `/hyperopen/.github/workflows/tests.yml`, `/hyperopen/docs/BROWSER_TESTING.md`, updated routing in `/hyperopen/AGENTS.md`, and repo-local skills under `/hyperopen/.agents/skills/playwright-e2e/` and `/hyperopen/.agents/skills/browser-mcp-explore/` without removing the existing Browser MCP workflow.
- [x] (2026-03-22 22:42Z) Ran staged validation in the requested narrow-to-broad order: `npm run lint:docs`, `npx shadow-cljs compile app`, `npm run test:playwright:install`, `npm run test:playwright:smoke`, `npm run test:playwright:ci -- tools/playwright/test/trade-regressions.spec.mjs`, `npm run test:playwright:ci`, `npm run test:browser-inspection`, `npm run test:multi-agent`, `npm test`, `npm run test:websocket`, and `npm run check`.
- [x] (2026-03-22 22:43Z) Updated this plan with final validation evidence and recorded the final completion steps to move it to `/hyperopen/docs/exec-plans/completed/` and close `hyperopen-uk06`.

## Surprises & Discoveries

- Observation: Hyperopen already has a project-scoped MCP registration for the browser-inspection server, so Browser MCP preservation is mostly a guidance and workflow problem, not a tooling-distribution problem.
  Evidence: `/hyperopen/.codex/config.toml` already defines `[mcp_servers.hyperopen-browser]` with `command = "node"` and `args = ["./tools/browser-inspection/src/mcp_server.mjs"]`.

- Observation: The existing browser-inspection subsystem already exposes deterministic hooks that Playwright can reuse instead of adding new test-only application APIs.
  Evidence: `/hyperopen/src/hyperopen/telemetry/console_preload.cljs` installs `globalThis.HYPEROPEN_DEBUG` in debug builds with `dispatch`, `dispatchMany`, `waitForIdle`, named `oracle` helpers, `qaReset`, wallet and exchange simulators, and `qaSnapshot`.

- Observation: The repo’s current stable browser scenarios are stored as checked-in JSON manifests rather than as Playwright tests.
  Evidence: `/hyperopen/tools/browser-inspection/scenarios/*.json` includes route smoke, wallet simulation, funding modal, asset selection, overlay, and mobile account-surface flows that are already treated as repeatable browser checks.

- Observation: Browser-inspection route smoke scenarios currently mix deterministic local assertions with exploratory or parity-only compare steps against `https://app.hyperliquid.xyz`, so only part of those flows should move into Playwright.
  Evidence: `/hyperopen/tools/browser-inspection/scenarios/trade-route-smoke.json`, `/hyperopen/tools/browser-inspection/scenarios/portfolio-route-smoke.json`, and `/hyperopen/tools/browser-inspection/scenarios/vaults-route-smoke.json` each end with a `compare` step against Hyperliquid after a local parity-element assertion.

- Observation: Playwright will need the debug build, not the release build, because the stable browser flows rely on the debug bridge and simulator APIs.
  Evidence: `/hyperopen/shadow-cljs.edn` preloads `hyperopen.telemetry.console-preload` only in the `:app` devtools configuration, and `/hyperopen/src/hyperopen/telemetry/console_preload.cljs` gates the `HYPEROPEN_DEBUG` global behind `goog.DEBUG`.

## Decision Log

- Decision: keep Browser MCP and Playwright side by side instead of trying to make Playwright replace the browser-inspection stack.
  Rationale: Browser MCP already owns live attach, design-review passes, parity compare, artifact bundles, and exploratory investigation. Playwright should handle committed deterministic assertions and CI. Collapsing both jobs into one layer would either weaken exploratory workflows or overcomplicate the committed suite.
  Date/Author: 2026-03-22 / Codex

- Decision: build Playwright tests on top of the existing `HYPEROPEN_DEBUG` bridge and existing `data-parity-id` or `data-role` anchors instead of adding a second bespoke test harness.
  Rationale: The browser-inspection stack already proved these seams are stable enough for repeatable browser automation. Reusing them keeps the app diff narrow and directly ports repo-owned flows instead of inventing parallel selectors or hidden APIs.
  Date/Author: 2026-03-22 / Codex

- Decision: port only the deterministic parts of stable Browser MCP flows into Playwright and leave parity compare, design review, and live-session debugging on the Browser MCP side.
  Rationale: The user explicitly asked to preserve Browser MCP for exploratory work and to avoid porting exploratory-only flows. Hyperliquid-vs-local compare steps and governed design-review passes are valuable, but they are not the right contract for committed CI tests.
  Date/Author: 2026-03-22 / Codex

- Decision: use Playwright in the existing Node and ESM style of this repository.
  Rationale: `/hyperopen/package.json` already centers Node scripts and `.mjs` tooling, while the rest of the repo’s browser tooling lives under `/hyperopen/tools/**`. Matching that style keeps the new harness legible beside the current browser-inspection code.
  Date/Author: 2026-03-22 / Codex

## Outcomes & Retrospective

This change adds a thin committed Playwright harness beside the existing browser-inspection MCP stack instead of replacing it. The new deterministic surface is centered on `/hyperopen/playwright.config.mjs`, `/hyperopen/tools/playwright/support/hyperopen.mjs`, `/hyperopen/tools/playwright/static_server.mjs`, and three initial test files under `/hyperopen/tools/playwright/test/`. The harness reuses the existing `HYPEROPEN_DEBUG` bridge and serves `/hyperopen/resources/public` with SPA fallback so route-based checks can run deterministically in CI without changing application behavior.

Stable Browser MCP flows promoted into committed Playwright coverage are: route smoke for `/trade`, `/portfolio`, and `/vaults` across desktop and mobile; asset selection for ETH; funding deposit modal selection for USDC; wallet connect plus enable-trading simulation; order submit and cancel gating under the simulator; mobile account-surface selection; and mobile position-margin sheet presentation. The Browser MCP workflows that remain exploratory are the governed design-review passes, live session attach and inspection, local-vs-Hyperliquid parity compare, flaky repro work, and selector or flow discovery before a path is stable enough to promote into Playwright.

Complexity increased only slightly because the new layer stays thin and repo-shaped: one Chromium-only Playwright config, minimal helper wrappers, and small route and regression suites. Contributor clarity improved materially because `/hyperopen/AGENTS.md`, `/hyperopen/docs/BROWSER_TESTING.md`, `/hyperopen/docs/tools.md`, `/hyperopen/docs/FRONTEND.md`, and the repo-local skills now route future work explicitly between committed Playwright coverage and exploratory Browser MCP work.

Validation results:

- `npm run lint:docs` initially failed because `/hyperopen/docs/exec-plans/active/2026-03-22-header-view-ddd-refactor.md` was a stale active plan for a closed `bd` issue. Moving that stale plan to `/hyperopen/docs/exec-plans/completed/2026-03-22-header-view-ddd-refactor.md` made the docs gate honest, and the rerun passed.
- `npx shadow-cljs compile app`, `npm run test:playwright:install`, `npm run test:playwright:smoke`, `npm run test:playwright:ci -- tools/playwright/test/trade-regressions.spec.mjs`, `npm run test:playwright:ci`, `npm run test:browser-inspection`, `npm run test:multi-agent`, `npm test`, `npm run test:websocket`, and `npm run check` all passed after the static Playwright server switched to SPA fallback and the helper started routing through `/index.html` before in-app navigation.
- Final Playwright result: `12 passed`. Final existing test gates remained green, including `npm test` with `2584` tests and `13773` assertions and `npm run test:websocket` with `405` tests and `2308` assertions.

Follow-up work is optional rather than blocking. The next high-value additions would be to promote more stable Browser MCP scenarios into Playwright only after they prove deterministic locally, and to keep Browser MCP focused on the exploratory and artifact-heavy flows that do not belong in CI.

## Context and Orientation

Hyperopen currently uses `npm` as its package manager, confirmed by `/hyperopen/package-lock.json` and `/hyperopen/package.json`. The existing repository test stack is primarily `shadow-cljs` Node test builds plus Babashka utility tests. The main validation gates are `npm run check`, `npm test`, and `npm run test:websocket`, and the only checked-in GitHub Actions workflow is `/hyperopen/.github/workflows/tests.yml`.

The frontend app is a browser-targeted ClojureScript build defined in `/hyperopen/shadow-cljs.edn`. Development serves `resources/public` on port `8080` through Shadow’s `:dev-http`, and `/hyperopen/resources/public/index.html` loads the `main` module bundle. The app has route-oriented modules for `/trade`, `/portfolio`, `/funding-comparison`, `/staking`, `/API`, and `/vaults`, plus a separate workbench build under `/hyperopen/portfolio/**`.

Browser MCP in this repository means the checked-in browser-inspection subsystem under `/hyperopen/tools/browser-inspection/**`. It already exposes a CLI, an MCP server, design-review passes, route scenario manifests, compare tooling, live CDP session attach, and guided QA docs. It is intentionally broader than a normal end-to-end test runner because it supports exploratory debugging and artifact-heavy visual review.

The deterministic test seam is the debug bridge exported from `/hyperopen/src/hyperopen/telemetry/console_preload.cljs`. In debug builds it installs `globalThis.HYPEROPEN_DEBUG`, which provides action dispatch, simulator installation, idle waiting, and named state oracles such as `wallet-status`, `asset-selector`, `funding-modal`, `order-form`, `account-surface`, `first-position`, `position-overlay`, and `effect-order`. Those are the safest anchors for initial Playwright coverage because they are already part of the repo-owned browser-testing contract.

## Plan of Work

First, add Playwright to `/hyperopen/package.json` in the existing Node-based tooling style and commit a root `/hyperopen/playwright.config.mjs` that starts the local dev app, targets Chromium, writes artifacts into the existing `/hyperopen/tmp/` space, and exposes clear local smoke, headed debug, and CI commands. Keep the config small and deterministic: one local app server, Chromium only, failure-focused artifacts, and no new application behavior.

Second, add a thin Playwright support layer under `/hyperopen/tools/playwright/` that wraps the existing debug bridge rather than abstracting the world. The support code should wait for `HYPEROPEN_DEBUG`, call named oracles, dispatch actions, install simulators, and express repeated assertions with Playwright’s own `expect.poll` semantics. Shared helpers are allowed only when they remove obvious duplication across the initial suite.

Third, port the stable local portions of the existing Browser MCP scenarios into a small initial Playwright suite. The suite should cover the main routes and a handful of high-value deterministic trading flows that are already backed by the debug bridge. It should not attempt to reproduce design-review passes, Hyperliquid parity compare, or other exploratory Browser MCP work.

Fourth, wire Playwright into CI and contributor guidance. Update `/hyperopen/AGENTS.md` with explicit tool-routing rules, add repo-local skills under `/hyperopen/.agents/skills/playwright-e2e/` and `/hyperopen/.agents/skills/browser-mcp-explore/`, keep the existing Browser MCP registration in `/hyperopen/.codex/config.toml`, and add or update a short doc that explains which tool to use and the exact commands to run.

Finally, validate in stages. Start with the narrowest checks that prove the new config and docs are coherent, then run a focused Playwright subset, then run the existing broader repository tests that still matter for this infrastructure change.

## Concrete Steps

Work from `/hyperopen`.

1. Install Playwright as a dev dependency in the existing Node toolchain.

2. Add the new Playwright surfaces:

   `/hyperopen/playwright.config.mjs`
   `/hyperopen/tools/playwright/**`

   The config must point at the local dev app and write test artifacts under `/hyperopen/tmp/playwright/**`.

3. Update scripts and CI:

   `/hyperopen/package.json`
   `/hyperopen/package-lock.json`
   `/hyperopen/.github/workflows/tests.yml`

   Add exact local smoke, headed debug, and CI scripts, plus the workflow steps needed to install Chromium and publish artifacts.

4. Update durable guidance:

   `/hyperopen/AGENTS.md`
   `/hyperopen/docs/tools.md`
   `/hyperopen/docs/FRONTEND.md` only if the browser-test routing needs a UI-policy reference
   `/hyperopen/docs/runbooks/browser-live-inspection.md` only if command cross-links need clarification
   a short new browser-testing doc under `/hyperopen/docs/**`
   `/hyperopen/.agents/skills/playwright-e2e/SKILL.md`
   `/hyperopen/.agents/skills/browser-mcp-explore/SKILL.md`

5. Run staged validation. The exact commands will be recorded here as they execute, but the expected order is:

   cd /hyperopen
   npm run lint:docs
   npx shadow-cljs compile app
   npm run test:playwright:smoke
   npm run test:playwright:ci -- --grep "<focused flow>" if a targeted subset is needed first
   npm run test:browser-inspection
   npm run test:multi-agent
   npm test
   npm run test:websocket

   If broader gates stay green after the focused Playwright checks, run `npm run check` as the final lint and compile gate before closing the work.

## Validation and Acceptance

Acceptance means all of the following are true:

1. Hyperopen still exposes Browser MCP through `/hyperopen/.codex/config.toml` and the browser-inspection tooling remains documented for exploratory, live-session, compare, and design-review work.
2. A contributor can run one exact local smoke command, one exact headed-debug command, and one exact CI command for Playwright from `/hyperopen/package.json`.
3. The initial Playwright suite covers the main route smoke flows plus a small set of stable deterministic user workflows that were previously only expressed as Browser MCP scenarios.
4. Browser MCP-only flows are still documented as exploratory or artifact-driven rather than incorrectly duplicated into CI.
5. `/hyperopen/AGENTS.md` and the new repo-local skills tell future Codex runs to use Playwright for committed and CI-grade browser tests and Browser MCP for exploratory investigation.
6. The staged validation commands complete with explicit pass or fail evidence recorded in this plan.

## Idempotence and Recovery

Adding Playwright is safe to stage incrementally as long as the Browser MCP workflow is left intact during the migration. The main risk is accidental overlap or guidance drift: a partial change could make Playwright exist without clear routing, or make Browser MCP seem deprecated even though the repo still depends on it for exploratory QA. If a validation step fails mid-way, keep the docs honest about the current split, rerun the focused Playwright subset first, and only broaden the gate after the targeted failure is understood.

The dev app server used by Playwright is safe to restart. Playwright browser installs are also repeatable. The recovery rule is to avoid introducing app-level test hooks beyond the existing debug bridge unless a specific deterministic gap appears and cannot be solved with the current oracles or selectors.

## Artifacts and Notes

Audit evidence captured before implementation:

- `/hyperopen/package.json` confirms the existing script surface and shows there is no Playwright script or dependency yet.
- `/hyperopen/.github/workflows/tests.yml` shows the current CI workflow has Node, Java, Babashka, coverage, and the existing test gates but no browser-test runner.
- `/hyperopen/shadow-cljs.edn` confirms the app runs on Shadow’s dev server at port `8080` and that debug-only preloads power the current browser-testing bridge.
- `/hyperopen/resources/public/index.html` confirms the main browser entrypoint loads the app bundle from `/js/manifest.json` or `/js/main.js`.
- `/hyperopen/tools/browser-inspection/scenarios/*.json` captures the stable flows available for Playwright promotion and the exploratory compare steps that must remain on the Browser MCP side.

## Interfaces and Dependencies

This work is expected to add one new Node dev dependency: `@playwright/test`. It should not add a second browser automation stack beyond Playwright plus the existing browser-inspection subsystem.

The core files expected to change are:

- `/hyperopen/package.json`
- `/hyperopen/package-lock.json`
- `/hyperopen/.github/workflows/tests.yml`
- `/hyperopen/playwright.config.mjs`
- `/hyperopen/tools/playwright/**`
- `/hyperopen/AGENTS.md`
- `/hyperopen/docs/tools.md`
- one short new doc under `/hyperopen/docs/**`
- `/hyperopen/.agents/skills/playwright-e2e/SKILL.md`
- `/hyperopen/.agents/skills/browser-mcp-explore/SKILL.md`

The app-facing testing interface should remain the existing debug bridge and stable selector contract from:

- `/hyperopen/src/hyperopen/telemetry/console_preload.cljs`
- `/hyperopen/src/hyperopen/views/**`
- `/hyperopen/tools/browser-inspection/scenarios/*.json`

Revision note: created this ExecPlan on 2026-03-22 after auditing the current browser-inspection MCP stack, CI workflow, frontend entrypoints, and stable deterministic browser seams before beginning the Playwright integration.
