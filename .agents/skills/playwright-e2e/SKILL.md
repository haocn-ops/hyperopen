---
name: "playwright-e2e"
description: "Use for committed deterministic browser tests, reusable assertions, and CI-safe regression coverage. Do not use for live browser attach, design-review passes, or exploratory-only browser work."
---

# Playwright E2E

Use this skill when the task should end with checked-in browser coverage that can run locally and in CI.

## Read First

- `/hyperopen/AGENTS.md`
- `/hyperopen/docs/BROWSER_TESTING.md`
- `/hyperopen/docs/tools.md`
- `/hyperopen/docs/FRONTEND.md` when UI surfaces change

## Use Playwright When

- the browser flow should be committed, asserted, repeated, reviewed, or run in CI
- the flow is deterministic and can rely on repo-owned selectors or `HYPEROPEN_DEBUG`
- smoke coverage or multi-viewport regression coverage is required

## Do Not Use Playwright When

- the task is exploratory only
- the job requires live attach to an already running browser tab
- the job is Hyperliquid parity compare, governed design review, or selector discovery

## Workflow

- Reuse existing `data-parity-id`, `data-role`, and `HYPEROPEN_DEBUG` seams before adding new hooks.
- Keep helpers thin. Prefer exact assertions and `expect.poll` over timing sleeps or a second scenario framework.
- Port only the stable local portion of a Browser MCP flow. Leave exploratory compare or design-review work on the Browser MCP side.
- Run `npm run test:playwright:smoke` for the quick local suite and `npm run test:playwright:ci` for the full committed suite.
