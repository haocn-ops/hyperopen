# Harden Nightly UI QA Tooling with Preflight, Fallback, and Failure Classification

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, nightly UI QA runs can fail fast with actionable infrastructure diagnostics instead of spending time on repeated opaque failures. The automation can deterministically preflight local socket and target readiness, optionally fall back to a trusted existing Chrome debug endpoint when local bind is blocked, and always emit a machine-readable classification artifact that states whether the failure was an automation gap or a likely product regression. A user can run one command (`npm run qa:nightly-ui`) and receive standardized artifacts, run IDs, and a report skeleton.

## Progress

- [x] (2026-03-09 02:13Z) Audited browser-inspection CLI/service/session-manager/local-app-manager and identified exact integration seams for preflight, fallback attach mode, and EPERM fail-fast messaging.
- [x] (2026-03-09 13:32Z) Implemented shared modules `tools/browser-inspection/src/preflight.mjs` and `tools/browser-inspection/src/failure_classification.mjs`.
- [x] (2026-03-09 13:34Z) Added nightly wrapper `tools/browser-inspection/src/nightly_ui_qa.mjs` with branch safety, route matrix orchestration, report writing, and machine-readable failure classification output.
- [x] (2026-03-09 13:35Z) Wired CLI/service/session/local-app integrations for preflight and actionable EPERM remediation (`cli preflight`, `service.preflight`, startup fail-fast seams).
- [x] (2026-03-09 13:36Z) Added npm scripts `browser:preflight` and `qa:nightly-ui`; updated browser-inspection docs/runbook command surfaces.
- [x] (2026-03-09 13:38Z) Added/updated tests (`failure_classification.test.mjs`, CLI contract preflight test) and validated browser-inspection suite.
- [x] (2026-03-09 13:39Z) Validated wrapper runtime with attach-mode fallback failure path (`npm run qa:nightly-ui -- --attach-port 1`) and confirmed standardized artifact/report output.
- [x] (2026-03-09 13:43Z) Ran required repository validation gates `npm run check`, `npm test`, and `npm run test:websocket` with all tests passing.

## Surprises & Discoveries

- Observation: Existing nightly run artifacts and report generation currently happen via ad-hoc shell execution, not a checked-in wrapper script.
  Evidence: No repository source file matched `nightly-ui-qa` orchestration while artifacts exist under `/hyperopen/tmp/browser-inspection/nightly-ui-qa-*`.

- Observation: EPERM appears in two layers: local app startup (`java.net.SocketException`) and browser-inspection listener startup (`listen EPERM`).
  Evidence: `/hyperopen/tmp/browser-inspection/nightly-ui-qa-2026-03-09T02-03-08Z/dev-startup-2026-03-09.log` and per-attempt `*.log` files.

- Observation: In this environment, local URL probe can return HTTP 404 while loopback bind is healthy; this should not fail required preflight.
  Evidence: `npm run browser:preflight` produced `ok: true` with optional `local-url-reachable` status `404`.

## Decision Log

- Decision: Implement a checked-in nightly wrapper under `tools/browser-inspection/src/nightly_ui_qa.mjs` instead of embedding logic in shell snippets.
  Rationale: A repository-owned script is testable, deterministic, and reusable by automation/manual operators.
  Date/Author: 2026-03-09 / Codex

- Decision: Add a small shared error-classification utility for EPERM and related infrastructure signatures.
  Rationale: Centralizing detection avoids duplicating brittle string checks across local app startup, session startup, and wrapper reporting.
  Date/Author: 2026-03-09 / Codex

- Decision: In attach fallback mode, keep loopback bind probe as informational (non-required) rather than blocking.
  Rationale: Attach mode should remain usable even when local bind is restricted; only attach endpoint reachability is required.
  Date/Author: 2026-03-09 / Codex

## Outcomes & Retrospective

The five hardening goals were implemented in repository-owned tooling:

- Deterministic preflight exists and is callable via `npm run browser:preflight`.
- Nightly wrapper exists at `npm run qa:nightly-ui` with branch safety, standardized artifacts, and dated report generation.
- Attach fallback mode is supported via wrapper/CLI args (`--attach-port`, `--attach-host`, `--target-id`).
- Machine-readable `failure-classification.json` is emitted in nightly run bundles.
- EPERM and related infrastructure failures now produce fast, actionable startup errors.

Validation outcomes:

- `npm run test:browser-inspection` passed.
- `npm run browser:preflight` returned structured JSON output.
- `npm run qa:nightly-ui -- --attach-port 1` produced a classified automation-gap run bundle and report.
- `npm run check`, `npm test`, and `npm run test:websocket` all passed.

Complexity impact: overall complexity is reduced for operators (single standardized command, deterministic diagnostics), while internal tooling complexity increased modestly due new orchestration and classification modules. The increase is contained within `tools/browser-inspection/` and does not touch product runtime behavior.

## Context and Orientation

Browser inspection tooling is under `/hyperopen/tools/browser-inspection/`.

- `/hyperopen/tools/browser-inspection/src/cli.mjs` exposes user commands (`inspect`, `compare`, `session` operations).
- `/hyperopen/tools/browser-inspection/src/service.mjs` coordinates run creation and capture/compare lifecycles.
- `/hyperopen/tools/browser-inspection/src/session_manager.mjs` launches or attaches Chrome, manages tool sessions, and optionally starts local app.
- `/hyperopen/tools/browser-inspection/src/local_app_manager.mjs` starts/polls/stops the local app.
- `/hyperopen/tools/browser-inspection/src/artifact_store.mjs` writes manifests and run metadata.
- `/hyperopen/tools/browser-inspection/config/defaults.json` contains defaults (artifact root, local app command/url, timeouts).
- `/hyperopen/package.json` defines npm wrappers.

The nightly matrix requirement for this automation is `/trade` and `/portfolio` against three spectate addresses plus `/vaults`, with branch safety gating on `main`.

## Plan of Work

First, add two new shared modules: one for deterministic preflight checks (socket bind probe, URL probe, attach endpoint probe), and one for error/failure classification (`automation-gap` vs `product-regression`). Then, update browser-inspection startup paths to apply health-check logic and produce clearer fail-fast remediation for EPERM conditions.

Next, add a first-class nightly wrapper script that enforces branch safety, executes required route/address attempts, captures stdout/stderr and run IDs, classifies failures, writes `failure-classification.json`, and writes a standardized markdown report to `/hyperopen/docs/qa/nightly-ui-report-YYYY-MM-DD.md`.

Then, extend CLI/package entry points so operators can run preflight directly and run the entire nightly flow via `npm run qa:nightly-ui`.

Finally, add targeted tests for the new pure utilities and CLI contract updates, then run tooling test gates.

## Concrete Steps

Run from `/Users/barry/projects/hyperopen`.

1. Implement modules and integrations.
2. Add npm scripts.
3. Add tests.
4. Run:
   - `npm run test:browser-inspection`
   - `node tools/browser-inspection/src/cli.mjs preflight`
   - `npm run qa:nightly-ui` (expected to classify infra blockers in this environment)

Expected observable outcomes:

- `cli preflight` prints JSON with deterministic pass/fail checks.
- `qa:nightly-ui` creates a new `tmp/browser-inspection/nightly-ui-qa-*` directory.
- That directory contains `attempt-summary.tsv`, `run-meta.json`, `failure-classification.json`, and per-attempt logs/json files.
- A report is written to `docs/qa/nightly-ui-report-YYYY-MM-DD.md`.

## Validation and Acceptance

Acceptance criteria:

- One-command wrapper exists (`npm run qa:nightly-ui`) and enforces `main` branch safety.
- Preflight fails quickly with actionable EPERM remediation when local bind is blocked.
- Fallback attach mode can be requested via explicit attach args and is used when preflight local bind check fails.
- Every nightly run writes machine-readable failure classification with evidence paths.
- Existing browser-inspection tests remain green and new tests cover the added utility behavior.

## Idempotence and Recovery

The wrapper only writes additive artifacts under `tmp/browser-inspection/nightly-ui-qa-*` and one dated report file under `docs/qa/`. Re-running the command creates a new timestamped run directory and overwrites the same date report file intentionally. If a run fails midway, re-run `npm run qa:nightly-ui`; the previous run directory remains as evidence.

## Artifacts and Notes

Primary expected artifacts per run:

- `tmp/browser-inspection/nightly-ui-qa-<timestamp>/attempt-summary.tsv`
- `tmp/browser-inspection/nightly-ui-qa-<timestamp>/run-meta.json`
- `tmp/browser-inspection/nightly-ui-qa-<timestamp>/failure-classification.json`
- `tmp/browser-inspection/nightly-ui-qa-<timestamp>/*.log`
- `docs/qa/nightly-ui-report-<YYYY-MM-DD>.md`

## Interfaces and Dependencies

New interfaces (planned):

- `runPreflightChecks(config, options)` in `tools/browser-inspection/src/preflight.mjs` returning structured check results and overall status.
- `classifyFailure(input)` in `tools/browser-inspection/src/failure_classification.mjs` returning `{classification, reason, summary, evidence}`.
- Nightly wrapper CLI options in `tools/browser-inspection/src/nightly_ui_qa.mjs`:
  - `--attach-port`
  - `--attach-host`
  - `--target-id`
  - `--allow-non-main` (for local debug only; default false)

Revision note: 2026-03-09 initial plan created to implement all five nightly QA hardening improvements as repository-owned tooling changes.
Revision note: 2026-03-09 progress/outcomes updated after implementation, tests, and wrapper validation.
