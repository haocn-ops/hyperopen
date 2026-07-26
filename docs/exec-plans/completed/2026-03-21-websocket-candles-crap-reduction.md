# Reduce CRAP In Websocket Candle State And Normalization

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is governed by `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The tracked work item for this plan is `hyperopen-oy4j`, and that `bd` issue was the local tracker reference while this plan recorded the implementation story.

## Purpose / Big Picture

`/hyperopen/src/hyperopen/websocket/candles.cljs` currently mixes owner-subscription state transitions, websocket subscribe and unsubscribe scheduling, payload field fallback logic, candle row normalization, and store-entry writes in a few branch-heavy functions. The goal of this change is to keep the public websocket candle behavior exactly the same while splitting those responsibilities into smaller pure seams with direct regression coverage. After the refactor, contributors should be able to adjust candle subscription ownership or payload normalization rules without reopening one monolithic function, and they should be able to prove the behavior with focused websocket candle tests plus the normal repository gates.

## Progress

- [x] (2026-03-21 23:49Z) Audited `/hyperopen/AGENTS.md`, `/hyperopen/docs/MULTI_AGENT.md`, `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md`.
- [x] (2026-03-21 23:49Z) Created and claimed `hyperopen-oy4j` for this deCRAP refactor.
- [x] (2026-03-21 23:49Z) Audited `/hyperopen/src/hyperopen/websocket/candles.cljs`, `/hyperopen/test/hyperopen/websocket/candles_test.cljs`, and gathered sidecar-agent findings on hotspot seams and missing regression coverage.
- [x] (2026-03-21 23:49Z) Created this active ExecPlan.
- [x] (2026-03-21 23:55Z) Captured a fresh baseline for the touched module. The manual coverage recovery path produced `coverage/lcov.info`, and `bb tools/crap_report.clj --module src/hyperopen/websocket/candles.cljs --format json --top-functions 100` confirmed the starting hotspots: `normalize-candle-entry` at `CRAP 557.69`, `sync-candle-subscription!` at `48.03`, and `write-candle-entry` at `32.09`, with module summary `crapload=547.81`, `max_crap=557.69`, `crappy_functions=3`, and `avg_coverage=0.49`.
- [x] (2026-03-22 00:00Z) Refactored the candle subscription, payload normalization, and entry-write hotspots into smaller helpers without changing public APIs or state shape.
- [x] (2026-03-22 00:00Z) Added focused websocket candle regressions for no-op resubscribe behavior, shared-owner transition behavior, payload-level fallback fields, single-map payloads, alternate entry shapes, and error-clearing on successful writes.
- [x] (2026-03-22 00:03Z) Ran `npm run check`, `npm test`, `npm run test:websocket`, `npm run coverage`, `npx c8 report --temp-directory .coverage`, and the focused CRAP report. `npm run check` and `npm run test:websocket` passed. `npm test` and `npm run coverage` still failed for the pre-existing unrelated `gap-x-3` versus `gap-x-4` assertions in `/hyperopen/test/hyperopen/views/account_info/table_contract_test.cljs`, but the raw V8 coverage data was still usable and the final focused CRAP report confirmed the module now has `crappy_functions=0` and `crapload=0.0`.
- [x] (2026-03-22 00:03Z) Prepared this ExecPlan to move from `active/` to `completed/` and close `hyperopen-oy4j`.

## Surprises & Discoveries

- Observation: this worktree did not have a current `coverage/lcov.info`, so the first focused CRAP run could not start.
  Evidence: `bb tools/crap_report.clj --module src/hyperopen/websocket/candles.cljs --format json --top-functions 20` exited with `Missing coverage/lcov.info. Run npm run coverage first.`

- Observation: the first `npm run coverage` attempt failed before recompilation because the cleanup step could not fully remove the existing `coverage/` tree.
  Evidence: the command failed with `rm: coverage/lcov-report/hyperopen/views: Directory not empty` and `rm: coverage: Directory not empty`.

- Observation: the module’s likely CRAP drivers are function-level hotspots rather than a namespace-boundary design failure.
  Evidence: the audited file concentrates complexity in `sync-candle-subscription!`, `normalize-candle-entry`, and the handler/update path around `create-candles-handler`, while the public API surface is only `sync-candle-subscription!`, `clear-owner-subscription!`, `get-subscriptions`, `create-candles-handler`, and `init!`.

- Observation: the repository-wide `npm test` and `npm run coverage` failures are unrelated to this websocket change and match the same account-info table contract assertions seen before the refactor.
  Evidence: both commands fail only in `/hyperopen/test/hyperopen/views/account_info/table_contract_test.cljs` lines `90` and `103`, where the tests still expect `gap-x-3` while the rendered class set contains `gap-x-4`.

- Observation: a second helper split was needed after the first post-change CRAP run, because the extracted `required-candle-row` helper still landed above threshold at `CRAP 40.768`.
  Evidence: the first post-refactor module report showed `required-candle-row` at `complexity 8`, `coverage 0.2`, and `crap 40.768`; splitting timestamp parsing from OHLC parsing eliminated the last above-threshold hotspot.

## Decision Log

- Decision: keep the public websocket candle API, store shape, and wire-message payloads unchanged.
  Rationale: this task is maintainability remediation, not a protocol or consumer migration, and the existing tests and downstream code already rely on the current public seams.
  Date/Author: 2026-03-21 / Codex

- Decision: prefer local helper extraction inside `/hyperopen/src/hyperopen/websocket/candles.cljs` unless the refactor reveals a genuine reusable policy boundary.
  Rationale: the initial audit points to a function-level CRAP problem in one cohesive runtime namespace. Splitting into a sibling namespace should happen only if the pure logic boundary becomes obvious and materially lowers risk.
  Date/Author: 2026-03-21 / Codex

- Decision: treat success as zero remaining CRAP hotspots above threshold in the touched module, not merely a lower module summary.
  Rationale: recent deCRAP plans in this repository close only when the hotspot functions are retired or driven below threshold with direct coverage, which prevents simply moving the complexity around.
  Date/Author: 2026-03-21 / Codex

- Decision: when the repository-wide coverage command fails after generating raw V8 coverage because of an unrelated test, manually finish the reporting step with `npx c8 report --temp-directory .coverage` instead of altering unrelated test files.
  Rationale: this preserves the normal coverage source for the CRAP tool while keeping the scope limited to the websocket candle refactor.
  Date/Author: 2026-03-21 / Codex

## Outcomes & Retrospective

This refactor achieved the intended deCRAP result in `/hyperopen/src/hyperopen/websocket/candles.cljs` without changing the public websocket candle surface. The original hotspot trio was:

- `normalize-candle-entry` at `CRAP 557.69`, `complexity 26`, `coverage 0.0769`
- `sync-candle-subscription!` at `CRAP 48.03`, `complexity 13`, `coverage 0.4082`
- `write-candle-entry` at `CRAP 32.09`, `complexity 7`, `coverage 0.2`

The final focused report shows the touched module at:

- `crappy_functions = 0`
- `crapload = 0.0`
- `max_crap = 30.0`
- `avg_coverage = 0.5193`

Post-change hotspot results for the original functions are:

- `normalize-candle-entry` -> `CRAP 11.53`, `complexity 4`, `coverage 0.2222`
- `sync-candle-subscription!` -> `CRAP 4.00`, `complexity 4`, `coverage 0.9474`
- `write-candle-entry` -> `CRAP 2.256`, `complexity 2`, `coverage 0.6`

This reduced overall complexity. The final structure separates owner-subscription state transitions, row metadata resolution, required OHLC parsing, optional candle-field enrichment, and entry-shape writes into smaller pure helpers, while `sync-candle-subscription!` and `create-candles-handler` remain thin runtime facades. The only residual validation gap is unrelated to this task: the repo-wide main test suite still has the pre-existing account-info table contract failure on `gap-x-3` versus `gap-x-4`.

## Context and Orientation

`/hyperopen/src/hyperopen/websocket/candles.cljs` owns two related responsibilities. First, it tracks which local owner currently wants which candle wire subscription by storing three maps inside `candle-state`: `:subscriptions` is the set of active `[coin interval]` wire subscriptions, `:owners-by-sub` maps each wire subscription to the owners using it, and `:sub-by-owner` maps each owner to its currently selected wire subscription. Second, it normalizes incoming candle websocket payloads and merges them into `[:candles coin interval]` entries in the application store.

The current file is only 293 lines, but two functions are doing too much work. `sync-candle-subscription!` normalizes inputs, removes the owner from a previous subscription, adds the owner to a next subscription, decides whether websocket messages should be emitted, and mutates `candle-state`. `normalize-candle-entry` searches multiple alias fields on both the row and the payload, parses all numeric values, validates required fields, and assembles the final normalized row. The handler path then merges normalized rows into existing vector-shaped or map-shaped store entries.

The current direct test surface is `/hyperopen/test/hyperopen/websocket/candles_test.cljs`. It already proves the high-level subscription switch behavior and vector-plus-`:candles` entry updates, but it does not directly cover the no-op resubscribe branch, the “one owner moves while another still holds the old wire subscription” branch, payload-level `:coin` and `:interval` fallbacks, single-map `:data` payloads, `:rows` or `:data` entry shapes, or clearing `:error` and `:error-category` after a successful merge.

## Plan of Work

First, capture a clean baseline by regenerating coverage and a focused CRAP report for `/hyperopen/src/hyperopen/websocket/candles.cljs`. If the stale `coverage/` directory remains a blocker, remove it explicitly before rerunning the normal coverage command so the final measurements come from fresh artifacts.

Next, refactor the owner-subscription path. Keep `sync-candle-subscription!` as the public entrypoint, but move the branching state transition into small private helpers. The target shape is one helper to build the normalized next subscription, one helper to remove an owner from its previous subscription, one helper to add an owner to the next subscription, and one pure orchestration helper that returns both the next state and the optional subscribe or unsubscribe side effects to emit after the swap.

Then, refactor the incoming candle normalization path. Keep the final normalized row contract the same, but split `normalize-candle-entry` into small helpers for resolving coin and interval metadata, extracting timestamps, parsing the required OHLC numeric fields, and attaching optional fields such as volume, close time, and trade count. Keep invalid rows filtered out exactly as they are today.

Finally, thin the store update path by extracting helpers that update one `[coin interval]` candle entry and that apply the normalized rows map to the full store. Expand `/hyperopen/test/hyperopen/websocket/candles_test.cljs` to pin the missing behaviors named above, then rerun the repository gates, refresh coverage, and verify the module with a focused CRAP report. This work is complete: the measured post-change report shows the touched module has no above-threshold hotspots.

## Concrete Steps

1. Regenerate baseline evidence from `/hyperopen`:

       rm -rf .coverage coverage
       npm run coverage
       bb tools/crap_report.clj --module src/hyperopen/websocket/candles.cljs --format json --top-functions 100

2. Edit `/hyperopen/src/hyperopen/websocket/candles.cljs` to extract pure helpers for subscription transition, normalized payload metadata, required OHLC parsing, optional row enrichment, and per-entry store updates while preserving the current public API surface.

3. Edit `/hyperopen/test/hyperopen/websocket/candles_test.cljs` to add direct regressions for the missing branches and entry shapes:

       - re-subscribing an owner to the same `[coin interval]` emits no duplicate wire traffic
       - moving one owner off a shared subscription does not unsubscribe until the last owner leaves
       - payload-level `:coin` and `:interval` fields fill in missing row-level metadata
       - a single-map `:data` payload is accepted
       - existing `:rows` and `:data` entry shapes are preserved on write
       - successful writes clear `:error` and `:error-category`

4. Run validation from `/hyperopen`:

       npm run check
       npm test
       npm run test:websocket
       npm run coverage
       npx c8 report --temp-directory .coverage
       bb tools/crap_report.clj --module src/hyperopen/websocket/candles.cljs --format json --top-functions 100

5. Update this ExecPlan with the final measured results and close `hyperopen-oy4j` when the module outcome is proven.

## Validation and Acceptance

Acceptance is behavior-based:

- candle subscription ownership still shares one wire subscription across multiple owners
- moving one owner away from a shared subscription unsubscribes only when the old subscription loses its last owner
- re-subscribing an owner to the same normalized `[coin interval]` is a no-op on both state and websocket traffic
- incoming candle payloads still normalize the same required row fields and accept the same supported alias shapes
- merging normalized rows still deduplicates by candle time, sorts ascending by `:t`, bounds the history length, and preserves map-shaped entry containers
- successful writes still clear any pre-existing candle entry error fields
- `npm run check` and `npm run test:websocket` pass
- `npm test` is unchanged except for the existing unrelated failures in `/hyperopen/test/hyperopen/views/account_info/table_contract_test.cljs`
- `npm run coverage` still stops on the same unrelated account-info table-contract failures, but the raw V8 coverage data remains usable and `npx c8 report --temp-directory .coverage` succeeds
- the focused CRAP report for `/hyperopen/src/hyperopen/websocket/candles.cljs` shows no remaining above-threshold hotspots in the touched module

## Idempotence and Recovery

This refactor is source-local and safe to rerun. Recreating coverage artifacts is also safe because the commands only rebuild generated test and coverage output. If a helper extraction changes behavior unexpectedly, restore the last touched helper boundary inside `/hyperopen/src/hyperopen/websocket/candles.cljs`, rerun `/hyperopen/test/hyperopen/websocket/candles_test.cljs` through the normal project test harness, and only then rerun the full repository gates. Do not widen the public websocket API just to simplify the refactor.

## Artifacts and Notes

Current baseline evidence captured before implementation:

- User-reported module summary for `/hyperopen/src/hyperopen/websocket/candles.cljs`: `crapload=547.81`, `max_crap=557.69`, `crappy_functions=3`, `avg_coverage=0.49`
- Baseline focused CRAP report:
  `normalize-candle-entry` -> `CRAP 557.69`
  `sync-candle-subscription!` -> `CRAP 48.03`
  `write-candle-entry` -> `CRAP 32.09`
- Final focused CRAP report:
  module summary -> `crappy_functions=0`, `crapload=0.0`, `max_crap=30.0`
  `normalize-candle-entry` -> `CRAP 11.53`
  `sync-candle-subscription!` -> `CRAP 4.00`
  `write-candle-entry` -> `CRAP 2.256`
- Final validation results:
  `npm run check` -> PASS
  `npm run test:websocket` -> PASS (`405` tests, `2308` assertions, `0` failures, `0` errors)
  `npm test` -> FAIL only on the pre-existing unrelated account-info table contract assertions in `/hyperopen/test/hyperopen/views/account_info/table_contract_test.cljs:90` and `/hyperopen/test/hyperopen/views/account_info/table_contract_test.cljs:103`
  `npm run coverage` -> FAIL for the same unrelated account-info table contract assertions after raw coverage generation
  `npx c8 report --temp-directory .coverage` -> PASS

Relevant files:

- `/hyperopen/src/hyperopen/websocket/candles.cljs`
- `/hyperopen/test/hyperopen/websocket/candles_test.cljs`
- `/hyperopen/test/hyperopen/websocket/trades_test.cljs`
- `/hyperopen/test/hyperopen/runtime/effect_adapters/websocket_test.cljs`

Plan revision note (2026-03-21 23:49Z): initial ExecPlan created after auditing the websocket candles module, the direct test surface, repository planning rules, the user-provided CRAP summary, and the first coverage-regeneration blocker.
Plan revision note (2026-03-22 00:03Z): updated after implementation with the extracted helper structure, expanded websocket candle regressions, final validation results, and the before-versus-after CRAP measurements for the touched module.

## Interfaces and Dependencies

No public API changes are planned. The file must still export the same callable entrypoints: `sync-candle-subscription!`, `clear-owner-subscription!`, `get-subscriptions`, `create-candles-handler`, and `init!`. The internal `candle-state` shape must remain compatible with existing runtime callers and tests. Websocket side effects must continue to flow through `hyperopen.websocket.client/send-message!` and `hyperopen.websocket.client/register-handler!`, and telemetry logging must stay in `init!`.
