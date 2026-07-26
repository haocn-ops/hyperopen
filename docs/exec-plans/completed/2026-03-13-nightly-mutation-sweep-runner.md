# Add Nightly Mutation Sweep Runner

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Linked `bd` issue: `hyperopen-cmc6`.

## Purpose / Big Picture

Hyperopen now has a repo-local mutation tool for one module at a time, but there is no checked-in way to run a repeatable overnight sweep across a known hotspot set and rank where the real testing gaps are. After this change, a contributor or scheduled automation will be able to run one nightly command that rebuilds coverage once, executes mutation testing serially across a curated target list, and writes machine-readable and markdown summaries under `target/mutation/nightly/**`. The visible proof will be a new command that produces a nightly summary naming the weakest modules, survivor counts, uncovered counts, and regressions versus the previous run.

## Progress

- [x] (2026-03-13 18:32Z) Created and claimed `bd` issue `hyperopen-cmc6` for the nightly mutation sweep runner.
- [x] (2026-03-13 18:39Z) Added the nightly target-list config plus nightly runner namespace and entrypoint.
- [x] (2026-03-13 18:44Z) Added deterministic Babashka tests for config loading, aggregation, regression detection, and markdown/json summary output.
- [x] (2026-03-13 18:55Z) Wired package/docs surfaces and ran repository validation gates.
- [x] (2026-03-13 19:09Z) Ran the nightly runner locally in both `--skip-coverage` and default coverage-rebuild modes against the first configured target and moved this plan to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The CRAP tooling already supports JSON output, so nightly target selection can build on machine-readable hotspot reports later without adding another parsing layer now.
  Evidence: `bb tools/crap_report.clj --help` on 2026-03-13 showed `--format <text|json>`.
- Observation: The nightly runner inherited mutation backup recovery automatically and restored a stale backup left by an interrupted earlier mutation session before starting its first real batch run.
  Evidence: `bb tools/mutate_nightly.clj --skip-coverage --limit 1 --format json` printed `Restored stale backup for src/hyperopen/api_wallets/domain/policy.cljs.` on 2026-03-13.
- Observation: Reusing an existing `coverage/lcov.info` via `--skip-coverage` produced a stronger kill rate for the first target than the same target under a fresh `npm run coverage` rebuild.
  Evidence: The `--skip-coverage` run at `target/mutation/nightly/2026-03-13T18-42-18.585349Z/summary.json` reported `24/28` killed (`85.7%`), while the default run at `target/mutation/nightly/2026-03-13T18-56-41.328912Z/summary.json` reported `20/28` killed (`71.4%`) for the same module and target config.

## Decision Log

- Decision: Implement the nightly sweep as a separate runner layered on top of the existing mutation tool instead of extending `bb tools/mutate.clj` into a batch mode.
  Rationale: The single-module mutation command is already stable and directly useful; a separate nightly runner keeps batch concerns like config loading, coverage orchestration, aggregation, and historical comparison out of the hot path.
  Date/Author: 2026-03-13 / Codex
- Decision: Start with a checked-in static target list of four policy-oriented modules instead of dynamic CRAP-driven selection.
  Rationale: A curated static list keeps the first nightly runner deterministic and easy to audit. The CRAP tool’s JSON output is available for a later ranking pass, but it is not required to make the nightly batch useful immediately.
  Date/Author: 2026-03-13 / Codex
- Decision: Write one markdown summary and one JSON summary under a timestamped nightly run directory, and continue past per-target mutation errors instead of aborting the whole batch.
  Rationale: Overnight automation needs stable artifact paths and should still preserve partial results if one module fails unexpectedly. The entrypoint can still exit non-zero when any targets fail, but the summary must survive.
  Date/Author: 2026-03-13 / Codex

## Outcomes & Retrospective

Implemented the nightly mutation batch runner as a layer on top of the repo-local mutation tool. The repository now has a checked-in target list at `/hyperopen/tools/mutate/nightly_targets.edn`, a nightly namespace at `/hyperopen/tools/mutate/nightly.clj`, and an entrypoint at `/hyperopen/tools/mutate_nightly.clj`. The batch runner loads configured targets, optionally rebuilds coverage once, runs mutation testing serially per target, compares results with the previous nightly summary when present, and writes both JSON and markdown summaries under `target/mutation/nightly/<timestamp>/**`.

Validation passed. `npm run check`, `npm test`, and `npm run test:websocket` all passed on 2026-03-13 after the nightly-runner changes. The nightly batch unit coverage is now folded into `npm run test:mutation`, which passed with `10` tests and `59` assertions. A local smoke batch `bb tools/mutate_nightly.clj --skip-coverage --limit 1 --format json` produced a valid summary in `target/mutation/nightly/2026-03-13T18-42-18.585349Z/`, and a real default-path run `bb tools/mutate_nightly.clj --limit 1` rebuilt coverage and produced a second summary in `target/mutation/nightly/2026-03-13T18-56-41.328912Z/`.

This change increased overall code complexity modestly because it adds a second orchestration layer on top of the single-file mutation tool, but the new complexity is well-contained. The batch concerns now live in one dedicated namespace instead of being spread across shell scripts or automation glue, which reduces operational complexity for future nightly scheduling.

## Context and Orientation

The existing mutation tool lives at `/hyperopen/tools/mutate.clj` with helper namespaces under `/hyperopen/tools/mutate/**`. Its structured report object is returned by `tools.mutate.core/execute-command`, and the CLI prints either text or JSON. Each module run already writes a timestamped report under `target/mutation/reports/**`, and manifests live under `target/mutation/manifests/**`.

The nightly runner should not scrape terminal text. It should call the mutation core directly, aggregate the returned maps, and produce its own nightly summary artifacts. The nightly flow also needs one coverage build at the beginning because `bb tools/mutate.clj` depends on `coverage/lcov.info` for covered-versus-uncovered routing.

This repository already has a pattern for checked-in tool entrypoints such as `/hyperopen/tools/crap_report.clj` and `/hyperopen/tools/mutate.clj`. It also already writes markdown summaries for longer-running tooling under `target/**` in the browser-inspection toolchain. The nightly mutation runner should follow the same spirit: checked-in entrypoint, deterministic file layout, and summaries that can be read by humans or post-processed by automation.

## Plan of Work

First, add the checked-in nightly target list. Create an EDN file under `/hyperopen/tools/mutate/` that lists the initial nightly hotspot modules and their suite modes. Keep the format simple: one vector of maps, one module per map, with keys for `:module`, `:suite`, and whether to force a full-file run. The initial target list should focus on pure or policy-oriented modules that already work well with the repo-local mutation tool and can finish overnight without needing ad hoc line filters.

Second, add a nightly runner namespace and entrypoint. The entrypoint should live at `/hyperopen/tools/mutate_nightly.clj`, and the implementation namespace should live under `/hyperopen/tools/mutate/nightly.clj`. The runner must load the target list, optionally rebuild coverage once, execute each target serially by calling `tools.mutate.core/execute-command`, and write aggregate outputs under `target/mutation/nightly/<date-or-timestamp>/**`. At minimum, it should emit one EDN or JSON summary and one markdown summary. The markdown summary should rank modules by kill rate, then by survivor count, and call out uncovered mutation sites separately.

Third, add historical comparison. The nightly runner should load the most recent previous nightly summary, compare per-module executed count, kill count, survivor count, uncovered count, and kill percentage, and annotate regressions and improvements in the new summary. “Regression” here means weaker mutation results than the previous night for the same module, not merely different totals because of a changed target list. If a module is new or missing from the prior run, the summary should say so instead of fabricating a delta.

Fourth, add deterministic tests and wire the tool into package/docs surfaces. The tests should stub out shell execution and mutation-core execution so the nightly runner can be validated without re-running full mutation passes in `npm run check`. Package scripts and docs should expose the new nightly entrypoint and explain that it is the intended command for overnight mutation sweeps or local automation.

## Concrete Steps

From the repository root:

1. Create `/hyperopen/tools/mutate/nightly_targets.edn`.
2. Create `/hyperopen/tools/mutate/nightly.clj` and `/hyperopen/tools/mutate_nightly.clj`.
3. Add or extend Babashka tests so the nightly runner is covered in local validation.
4. Update `/hyperopen/package.json`, `/hyperopen/docs/tools.md`, and `/hyperopen/docs/references/toolchain.md`.
5. Run:
   - `npm run test:mutation`
   - `bb tools/mutate_nightly.clj --skip-coverage --limit 1`
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
6. Run the real nightly command once:
   - `bb tools/mutate_nightly.clj --limit 1`

## Validation and Acceptance

Acceptance requires a working nightly command that can be rerun safely and produces observable artifacts. A dry-ish local run with `--skip-coverage` must still load the target config, execute the requested subset, and write a markdown plus JSON summary under `target/mutation/nightly/**`. A real run must rebuild coverage once, then execute the configured modules serially without leaving mutated source behind. The summary must rank modules by weakness, include the prior-run comparison when a previous nightly summary exists, and call out modules with survivors or uncovered mutation sites.

## Idempotence and Recovery

The nightly runner must be safe to rerun. Each module execution already restores stale backups and recompiles original outputs through the mutation core; the nightly wrapper must preserve that behavior by calling the core rather than reimplementing mutation execution. Coverage rebuilding should be a fresh one-shot step at the start of a real run, and summary output paths should be timestamped so reruns do not clobber prior nightly evidence.

## Artifacts and Notes

Implementation artifacts are the nightly target-list config, the nightly runner namespace and entrypoint, updated mutation tests, package/docs changes, and aggregate nightly summaries under `target/mutation/nightly/**`.

Observed command summaries:

- `npm run test:mutation` passed on 2026-03-13 with `10` tests, `59` assertions, and no failures.
- `bb tools/mutate_nightly.clj --skip-coverage --limit 1 --format json` wrote `summary.json` and `summary.md` under `target/mutation/nightly/2026-03-13T18-42-18.585349Z/`.
- `bb tools/mutate_nightly.clj --limit 1` rebuilt coverage, wrote `summary.json` and `summary.md` under `target/mutation/nightly/2026-03-13T18-56-41.328912Z/`, and reported `20/28` killed (`71.4%`) for the first configured target.
- `npm run check` passed on 2026-03-13.
- `npm test` passed on 2026-03-13 with `2363` tests and `12406` assertions.
- `npm run test:websocket` passed on 2026-03-13 with `385` tests and `2187` assertions.

## Interfaces and Dependencies

The final implementation must expose:

- `bb tools/mutate_nightly.clj` as the nightly batch entrypoint.
- A checked-in target list under `/hyperopen/tools/mutate/nightly_targets.edn`.
- A helper API in `/hyperopen/tools/mutate/nightly.clj` that loads targets, runs coverage on demand, aggregates module reports, compares to the previous run, and writes markdown/json summaries.
- Package/docs references that make the nightly command discoverable for local automation and future CI scheduling.

Revision note (2026-03-13): Updated the plan with the implemented nightly runner, validation evidence, the coverage-rebuild versus skip-coverage discrepancy, and final artifact paths before moving the plan to completed.
