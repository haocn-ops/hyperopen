# Detect proxy-superset calendar poisoning by timestamp coverage

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. Maintain this document in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

An optimizer run with a complete proxy assumption for SOPH used only 262 shared returns even though the assets that actually participate in raw-history alignment support 350. SOPH is intentionally excluded from that alignment because its covariance row is synthesized later from its selected factors, but the history API response still contains SOPH and its response-level calendar begins near SOPH's later listing date. The client has a guard for this exact superset-calendar problem, but it compares observation counts. That comparison fails when the correct member-only calendar is weekday-heavy while the poisoned response calendar is crypto-daily: 350 valid member returns can cover a longer period than 386 response returns.

After this change, the optimizer decides whether a response calendar covers the alignment members by checking timestamps rather than asking which calendar has more observations. The same live scenario now uses 350 shared returns from 2025-01-21 through 2026-06-17 instead of 262 from 2025-05-28 through 2026-06-17. SOPH remains in the final risk model through its factor-based covariance synthesis; this change only prevents its excluded raw-history calendar from shortening the covariance sample.

## Context References

Public refs:

- Direct user/maintainer request on 2026-07-11: investigate why the run reports 262 returns despite TRUMP having more than 500 days, explain whether SOPH should contribute through its factor blend, then create an execution plan and implement the fix.
- Live nREPL evidence from the maintainer's running optimizer session, first on port 49257 and then on port 49447, is embedded below so this plan does not depend on session-local notes.

Repo artifacts:

- `/hyperopen/docs/exec-plans/active/2026-07-05-optimizer-proxy-history-assumptions.md` records the intentional model: a complete proxy-assumption asset is excluded from raw shared-calendar alignment and re-admitted to the final covariance matrix through factor covariance synthesis plus specific variance.
- `/hyperopen/docs/exec-plans/active/2026-07-05-optimizer-proxy-covariance-window-honesty.md` introduced the existing response-superset poisoning guard. Its regression covers only a poisoned response with fewer observations than the member-only calendar; this plan closes the mixed-cadence hole in that guard.

Local scratch refs (non-authoritative):

- None. The required live evidence is recorded in `Context and Orientation`.

## Progress

- [x] (2026-07-12 00:48Z) Confirmed the failure mode from the live session and created this active ExecPlan.
- [x] (2026-07-12 00:52Z) Recorded the frozen mixed-cadence regression in RED: expected five member price timestamps and four member return timestamps, but the retained response calendar produced six later price timestamps and only two returns.
- [x] (2026-07-12 00:56Z) Replaced the observation-count heuristic with timestamp containment under the existing response-superset guard; precomputed member calendars are reused by the point-level fallback when eligibility is unchanged.
- [x] (2026-07-12 00:59Z) Verified the history-window and request-builder namespaces in the generated test runner; the post-change repository test run passed 5,455 tests / 29,458 assertions with 0 failures. `npm run lint:delimiters -- --changed` and `npm run lint:namespace-sizes` also passed.
- [x] (2026-07-12 01:09Z) Froze the reviewer-found common-boundary regression in RED and added the positive containment regression. Current production passed the fully covering superset case but failed four assertions when the response covered every member return timestamp while omitting the price predecessor needed for the first return interval.
- [x] (2026-07-12 01:18Z) Extended superset validation to require independent common- and return-calendar containment, while preserving backend authority for exact membership and fully covered supersets. Candidate intersections are delayed so an exact-membership usable aligned response does not traverse point history.
- [x] (2026-07-12 01:19Z) Generated suite GREEN: 5,457 tests / 29,467 assertions / 0 failures, including history-window and request-builder. Delimiter preflight, namespace size (alignment remains 580 lines), and `git diff --check` passed.
- [x] (2026-07-12 01:20Z) Ran the final `npm run gates`: all 34 checks passed; the generated application runner reported 6,163 tests and 32,812 assertions. Final reviewer verdict was PASS with no findings.
- [x] (2026-07-12 01:20Z) Hot-loaded the implementation into nREPL 49447 and rebuilt draft `scn_1783390202425`: the ready request used 350 returns from 2025-01-21 through 2026-06-17, retained SOPH in the 18-asset engine universe through its proxy assumption, and confirmed TRUMP as the honest January-boundary limiter rather than the false May-cutoff explanation.

## Surprises & Discoveries

- Observation: observation count is not a proxy for temporal coverage when calendars have different cadence. The correct alignment-member calendar had 350 mostly weekday observations beginning 2025-01-21; the response-level calendar had 386 daily observations but began around SOPH's late-May 2025 history. Because `350 > 386` is false, the guard retained the later boundary and the eventual member intersection fell to 262.
  Evidence: live recomputation from the running app produced a stored optimizer window of 262 returns from 2025-05-28 through 2026-06-17 and a member-only window of 350 returns from 2025-01-21 through 2026-06-17.
- Observation: TRUMP did not cause the May 2025 start. Its individual series contained 529 returns, beginning around 2025-01-18 and ending 2026-07-01. It was named because `window.cljs` can only choose a limiter from the actual alignment members while it was handed a calendar whose later start came from excluded SOPH.
  Evidence: recomputing the calendar from the actual members recovered 88 returns and moved the oldest shared timestamp back by more than four months without adding TRUMP data.
- Observation: the June 2026 end remains independently limited by stale member data. Recovering the start boundary should not be described as refreshing SAND, ZEN, BABA, or any other stale series.
  Evidence: both the stored and recomputed live windows ended 2026-06-17.
- Observation: the mixed-cadence fixture proved the defect at both calendar layers. The old rule retained response price timestamps `[day 4..9]` instead of member timestamps `[day 0, 1, 2, 7, 8]`, then returned only `[day 7, 8]` instead of the four valid member returns `[day 1, 2, 7, 8]`.
  Evidence: the pre-change `npm test` run reported exactly two failures in `align-api-v2-mixed-cadence-poisoned-response-calendar-recomputed-test`; the same 5,455-test run passed after the production change.
- Observation: return-calendar containment is necessary but not sufficient because every modeled return also needs its preceding price timestamp to compute the correct interval duration. A superset response with common calendar `[day 1, 3]` and return calendar `[day 1, 3]` covered the member return calendar but omitted member price boundary `day 0`, leaving the first interval's `:start-ms` and `:dt-days` nil and retaining API aligned returns.
  Evidence: the generated 5,457-test run failed four assertions only in `align-api-v2-superset-missing-common-boundary-recomputed-test`; the adjacent fully covering superset regression passed and preserved `:api-v2-aligned-returns`.
- Observation: calendar derivation cannot be unconditional merely to support poisoning detection. Exact-membership responses are authoritative and may already carry complete validated aligned returns, so walking every member's point history on that hot path is unnecessary work.
  Evidence: the candidate common/return pair now lives behind one delay. `calendar-poisoned?` forces it only after `response-superset?` succeeds; point-level fallback forces the same cached pair only when the eligible ids still equal the candidate ids.
- Observation: after hot-load and request rebuild, TRUMP remains the limiting instrument, but the attribution is now honest. It limits the recovered January boundary; it no longer appears to explain the excluded SOPH-derived May cutoff.
  Evidence: draft `scn_1783390202425` rebuilt to a ready request with 350 returns from 2025-01-21 through 2026-06-17 and `:history-window :return-observations` equal to 350.
- Observation: the corrected raw alignment and downstream proxy synthesis preserve the intended modeling boundary. SOPH is absent from the 20 raw series used for alignment but present in the final 18-asset engine universe.
  Evidence: the live engine request carries SOPH's complete proxy assumption over IMX, SAND, ETH, and BTC with confidence `:medium`, maximum weight `0.05`, and 385 regression observations.

## Decision Log

- Decision: define response-calendar coverage by timestamp containment, not observation-count ordering. A response return calendar covers a member-derived return calendar only when every member-valid return timestamp is present in the response calendar.
  Rationale: this directly tests the property the alignment needs and works for daily, weekday, holiday-gapped, or otherwise mixed calendars. A larger response count cannot compensate for omitted earlier member-valid timestamps.
  Date/Author: 2026-07-12 / spec_writer, based on maintainer-requested live diagnosis.
- Decision: retain the existing structural `response-superset?` guard.
  Rationale: when the API response covers exactly the alignment universe, its validated aligned returns remain canonical. Client-side recomputation is appropriate only when excluded or reference-only instruments are present and the response calendar omits timestamps valid for every actual alignment member.
  Date/Author: 2026-07-12 / spec_writer.
- Decision: make no change to proxy-assumption covariance synthesis and do not materialize a fabricated SOPH daily return series.
  Rationale: `domain/history_assumption_proxy.cljs` already uses the selected factor covariance to synthesize SOPH's covariance row and adds explicit specific variance. The defect is upstream calendar selection, not SOPH's presence in the final risk model.
  Date/Author: 2026-07-12 / spec_writer.
- Decision: do not independently change `history_loader/window.cljs` unless focused tests show attribution remains false after the calendar is corrected.
  Rationale: the live TRUMP attribution is downstream evidence of the poisoned start. The smallest root-cause fix is to supply `history-window` the member-derived calendar; after that, TRUMP may honestly remain the latest-starting actual member even though it no longer explains a May 2025 cutoff.
  Date/Author: 2026-07-12 / spec_writer.
- Decision: derive the candidate common and return calendars once behind a delay, and reuse each in fallback only when the post-validation eligible-id set still equals the candidate-id set.
  Rationale: lazy reuse avoids point-history intersections on exact-membership usable-aligned paths, avoids repeating both intersections for a superset or fallback, and preserves existing behavior when unusable or insufficient members are excluded during preparation.
  Date/Author: 2026-07-12 / worker.
- Decision: a structural superset is trusted only when its common calendar contains every member common timestamp and its return calendar independently contains every member return timestamp.
  Rationale: return coverage alone can omit the price predecessor required to describe the first return interval; common coverage alone cannot prove aligned-return availability. Exact-membership responses remain authoritative because the containment audit is deliberately gated by `response-superset?`.
  Date/Author: 2026-07-12 / worker, after reviewer RED.

## Outcomes & Retrospective

Completed. A structural superset must now cover both member common and return calendars; exact membership and fully covered supersets retain backend aligned-return authority. Candidate intersections are cached lazily, so the exact-membership usable-aligned path avoids point-history work. The alignment namespace remains at its existing 580-line allowance.

The deterministic regression first reproduced the mixed-cadence failure, including the reviewer-discovered missing price-predecessor boundary, then passed with independent common- and return-calendar containment. The final reviewer verdict was PASS with no findings. The final `npm run gates` matrix passed all 34 checks; the application runner reported 6,163 tests and 32,812 assertions.

Live proof on nREPL 49447 completed the original purpose. Rebuilding draft `scn_1783390202425` produced a ready request with 350 returns from 2025-01-21 through 2026-06-17 and `:history-window :return-observations` equal to 350. The request held 20 raw series; SOPH was correctly absent from raw alignment yet present in the 18-asset engine universe through its IMX/SAND/ETH/BTC proxy assumption, with medium confidence, maximum weight 0.05, and 385 regression observations. TRUMP remains the honest limiter for the January boundary, not a false explanation for a May cutoff.

Overall complexity decreased at the decision seam: timestamp containment directly states the coverage invariant and removes the cadence-sensitive count heuristic. Lazy reuse adds a small implementation detail but prevents repeated intersections and keeps the authoritative exact-membership path cheap. No backend, persisted-state, worker-message, proxy-synthesis, or UI contract changed.

## Context and Orientation

The optimizer asks the history API for one bundle covering the draft universe and any reference factors. The normalized bundle includes per-instrument point series in `:series-by-instrument`, a response-level `:common-calendar`, a response-level `:return-calendar`, and optionally pre-aligned return vectors. A calendar is a sorted vector of UTC millisecond timestamps. A return calendar contains the timestamps at which every participating series has a finite return.

`/hyperopen/src/hyperopen/portfolio/optimizer/application/request_builder.cljs` excludes an asset with a complete proxy assumption from raw-history alignment. This is deliberate: allowing a young asset such as SOPH to join the raw intersection would shorten every established asset's covariance sample. Later, `/hyperopen/src/hyperopen/portfolio/optimizer/domain/history_assumption_proxy.cljs` re-admits SOPH to the final covariance matrix. Its cross-covariance with another asset is the weighted sum of the selected factors' covariances with that asset, and its own variance includes both factor-driven variance and calibrated specific variance. Therefore “excluded from alignment” does not mean “excluded from the optimizer.”

The defect was in `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/alignment.cljs`, inside `align-api-v2-history-inputs`. The fetch response can contain SOPH even when the `universe` passed to this particular alignment excludes it. `response-superset?` detects that structural mismatch. `candidate-series-by-id` contains only actual alignment members, and the calendar helpers derive their honest shared price and return timestamps from their own points. Before this change, `calendar-poisoned?` compared the count of that member-derived return calendar with the count of the API response's `:return-calendar`. If the member count was not larger, the code could retain the response `:common-calendar`; a later point-level intersection over the members could not recover timestamps already cut off by that retained boundary.

That old rule worked for the original regression in `/hyperopen/test/hyperopen/portfolio/optimizer/application/history_window_test.cljs`, where the excluded thin asset collapsed the backend calendar to one observation while the remaining members supported nine. It failed for the live stock/crypto mix: 350 weekday-heavy member observations span farther back than 386 crypto-daily response observations. The live result became 262 after the later response boundary was intersected with weekday series. The replacement rule audits timestamp containment independently for the member price calendar and member return calendar whenever the response is a structural superset.

`/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/window.cljs` summarizes the effective calendars and chooses a limiting member from `source-series-by-instrument`. Because SOPH had already been excluded from that source map, the poisoned May start was attributed to TRUMP, the latest-starting remaining member, even though TRUMP's own series begins in January. Correcting the effective calendar at the alignment seam fixed both the count and the misleading cutoff explanation; TRUMP remains the honest limiter for the recovered January boundary.

## Milestones

Milestone 1 established RED at the actual decision seam. `/hyperopen/test/hyperopen/portfolio/optimizer/application/history_window_test.cljs` now includes a focused api-v2 fixture whose alignment universe contains an all-days crypto series and a weekday-gapped equity series while the response also contains an excluded SOPH-like series. The response begins later but has more return observations because it includes weekend timestamps. Before the production edit, the fixture retained six later price timestamps and only two returns instead of the five member price timestamps and four member returns.

Milestone 2 delivered the smallest production correction. `align-api-v2-history-inputs` in `/hyperopen/src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/alignment.cljs` now derives the member-only common and return calendars once behind a delay. Under the existing `response-superset?` guard, `calendar-poisoned?` requires the response common calendar to contain every member common timestamp and the response return calendar to contain every member return timestamp. Any missing timestamp selects the existing client point-level recomputation path. The alignment namespace remains exactly at its existing 580-line allowance.

Milestone 3 verified the result. The focused history-window and request-builder regressions passed, including the reviewer-added missing-price-predecessor case and a fully covered superset control that preserves API-aligned returns. The final repository gate matrix passed 34/34. Hot-loading the app attached to nREPL 49447 and rebuilding draft `scn_1783390202425` recovered 350 returns from 2025-01-21 through 2026-06-17. Browser QA was not required because no view, style, selector, or interaction code changed; deterministic application tests and live optimizer state provided the behavioral proof.

## Plan of Work

First, the test writer changed only `history_window_test.cljs` and proved the pre-change implementation failed for the mixed-cadence shape while preserving the original poisoned-superset regression.

Second, the worker updated the `calendar-poisoned?` inputs and predicate in `api_v2/alignment.cljs` without a public API change. The response common and return calendars are converted to membership sets and each must contain its corresponding member-derived timestamps. Empty member-derived calendars remain the responsibility of existing insufficient-history and peel behavior. Failed coverage under `response-superset?` sets `use-aligned?` false and recomputes both effective calendars and per-member returns from point data.

Third, the team ran focused and full validation and reran the live scenario. The corrected member calendar retained TRUMP as an honest January limiter, so no separate `window.cljs` change was needed.

## Concrete Steps

Work from `/hyperopen` (in this checkout, `/Users/barry/.codex/worktrees/592c/hyperopen`). Bootstrap dependencies before any gate:

    npm run setup:worktree

Materialize the regression, regenerate and compile the test runner, then run the two focused namespaces:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.application.history-window-test --test=hyperopen.portfolio.optimizer.application.request-builder-test

Before implementation, the new mixed-cadence assertion failed with the effective window clipped to the response's later start while the existing request-builder proxy regression passed. After implementation and the reviewer follow-up, both namespaces passed.

The required repository matrix then passed:

    npm run gates

Every row—`npm run check`, `npm test`, and `npm run test:websocket`—reported PASS: 34/34 gates, 6,163 application tests, and 32,812 assertions.

For live verification, the implementation was hot-loaded into the running app through nREPL 49447 and memoized alignment/request values were rebuilt for draft `scn_1783390202425`. The observed evidence was:

    return observations: 350 (previously 262)
    oldest shared return: 2025-01-21 (previously 2025-05-28)
    newest shared return: 2026-06-17 (unchanged until stale member data refreshes)

Future live counts may drift as the backend refreshes. The durable acceptance rule is equality with a fresh client-side intersection over the actual alignment members, not a permanently hard-coded count of 350.

## Validation and Acceptance

Status: accepted. All of the following behaviors were demonstrated:

- A response that covers excluded instruments and omits at least one return timestamp valid for every actual alignment member is rejected as poisoned even when the response has more total observations than the member-only calendar.
- The mixed daily/weekday regression fails before the production change and passes after it, with the effective oldest timestamp and return vector equal to the client-side member intersection.
- Existing proxy-assumption request-builder tests still prove that SOPH-like assets remain excluded from raw alignment and are available for downstream covariance synthesis.
- A response that is not a superset, or a superset response that contains all member-derived common and return timestamps, retains the existing validated aligned-return path.
- The live optimizer request no longer uses the SOPH-late-start 262-return window. It uses 350 returns from 2025-01-21 through 2026-06-17; TRUMP is the honest limiter for that January boundary rather than an explanation for the former May cutoff.
- SOPH remains absent from the 20 raw alignment series but present in the 18-asset engine universe through the intended IMX/SAND/ETH/BTC proxy assumption, with 385 regression observations.
- Final reviewer verdict is PASS with no findings, and `npm run gates` reports 34/34 PASS with 6,163 application tests and 32,812 assertions.

No browser screenshot is required because the change does not alter presentation or interaction code. A refreshed UI may be used for maintainer confirmation, but deterministic test output plus live request state are the acceptance authority.

## Idempotence and Recovery

The fixture and predicate edits are deterministic and safe to rerun. Test generation rewrites the generated runner from discovered test namespaces and is already part of normal repository validation. If the nREPL session has stale memoized results, refresh the optimizer page or restart the dev app; do not mutate saved portfolio data merely to force recomputation. If the live backend has refreshed and the historical 350 count has changed, compare the app result to a fresh member-only client intersection and record both rather than weakening the regression.

If timestamp containment unexpectedly marks a normal response as poisoned, stop and inspect whether the response is a true superset and which member-valid timestamps are absent. Do not restore the count heuristic. Either the omitted dates prove the response is unsuitable for this alignment subset, or the point-level member calendar is malformed and should be corrected at its source.

## Artifacts and Notes

The essential before-state transcript is:

    backend response return observations: 386 (daily-heavy, later start)
    actual alignment-member returns:       350 (weekday-heavy, earlier start)
    optimizer returns after retained clip:  262
    stored optimizer range:                 2025-05-28 -> 2026-06-17
    recomputed member-only range:           2025-01-21 -> 2026-06-17
    TRUMP individual returns:               529, through 2026-07-01

The accepted after-state transcript is:

    draft id:                               scn_1783390202425
    request state:                          ready
    optimizer return observations:          350
    optimizer range:                        2025-01-21 -> 2026-06-17
    raw series count:                       20
    engine universe count:                  18
    SOPH raw series:                        absent
    SOPH engine membership:                 present via proxy assumption
    SOPH factors:                           IMX, SAND, ETH, BTC
    SOPH confidence / max weight:           medium / 0.05
    SOPH regression observations:           385
    limiter:                                TRUMP, honest January boundary
    final gates:                            34/34 PASS
    final application suite:                6,163 tests / 32,812 assertions
    final review:                           PASS, no findings

The key invariant for the replacement predicate is:

    response-covers-common?  = every member-derived common timestamp is present in response common-calendar
    response-covers-returns? = every member-derived return timestamp is present in response return-calendar
    calendar-poisoned?       = response-superset? and not (response-covers-common? and response-covers-returns?)

Counts remain diagnostics, not the decision rule.

## Interfaces and Dependencies

No public API, persisted state, worker message, or backend contract changes. The implementation stays inside the existing ClojureScript history-loader pipeline and uses core timestamp/set membership operations. The relevant internal values remain `candidate-series-by-id`, `response-superset?`, `calendar-poisoned?`, `use-aligned?`, `effective-calendar`, and `effective-return-calendar` in `align-api-v2-history-inputs`.

The final behavior must preserve the existing downstream contract returned by alignment: `:calendar`, `:return-calendar`, `:return-series-by-instrument`, `:history-window`, `:alignment-source`, and warnings retain their current shapes. SOPH's downstream covariance synthesis in `domain/history_assumption_proxy.cljs` remains unchanged.

Plan update note: 2026-07-12 00:48Z — Created this follow-up plan after live evidence showed the existing proxy-superset guard is cadence-sensitive. Scope is intentionally limited to one mixed-cadence regression, the timestamp-coverage predicate at the alignment seam, focused tests, full gates, and live verification.

Plan update note: 2026-07-12 01:00Z — Worker implementation complete. RED showed the old later response boundary and two retained returns; GREEN recovers the member calendars. The 580-line alignment allowance is unchanged, delimiters and namespace size pass, and the full generated test runner is green. Full gate matrix and live refresh remain open.

Plan update note: 2026-07-12 01:19Z — Reviewer follow-up complete. Superset validation now covers price boundaries and returns independently, fully covered supersets still use API-aligned returns, exact membership bypasses point-history intersections through lazy candidate calendars, and the 5,457-test generated suite is green.

Plan update note: 2026-07-12 01:20Z — Completion evidence recorded. Final review passed with no findings; `npm run gates` passed 34/34 with 6,163 tests and 32,812 assertions; live draft `scn_1783390202425` rebuilt ready with the recovered 350-return January-to-June window and SOPH correctly synthesized into the final engine universe. All acceptance criteria are complete, so the plan moved from `active` to `completed`.
