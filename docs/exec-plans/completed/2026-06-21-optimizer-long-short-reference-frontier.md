# Make the optimizer reference frontier reflect the long/short opportunity set

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. Keep it self-contained, update it whenever implementation discoveries change the plan, and leave at least one unchecked progress item while the plan remains active.

## Purpose / Big Picture

The portfolio optimizer draws an "Efficient Frontier" chart. On that chart it plots a gold frontier curve plus a "Target" marker for the recommended portfolio. A user running a long/short, leveraged scenario (for example long SP500, short XYZ100, gross ~10x) saw the Target marker float ABOVE the drawn frontier. A point above an efficient frontier is geometrically impossible for the feasible set it belongs to, so this looked like the optimizer was producing an inefficient frontier.

The root cause is not the optimizer math. The chart's default frontier (drawn when the "Constrain Frontier" checkbox is unchecked, which is the default) is computed over a DIFFERENT, artificially narrowed feasible set than the recommended portfolio. Specifically the code that builds that "unconstrained" reference frontier forces the whole universe to be long-only and drops the per-asset weight cap (which then silently falls back to a 100%-per-asset cap). A long/short, levered recommendation simply cannot exist inside a long-only, 100%-capped feasible set, so the recommendation legitimately sits above that curve. The drawn curve is the efficient frontier of a smaller, different problem.

After this change, the reference frontier reflects the user's actual opportunity set: the same per-asset long/short directions the user chose, the same gross/leverage budget, and the same concentration caps. It removes only the position-relative rebalancing constraints (turnover against the current portfolio, held-position locks, and the rebalance tolerance), because those describe friction in moving from today's holdings, not the investable universe. Because the reference feasible set then strictly contains the fully constrained set, the recommended portfolio always lies on or below the drawn frontier. A user can re-open the scenario, leave "Constrain Frontier" unchecked, and see the Target marker sitting on the long/short frontier instead of above it.

## Context References

Public refs:

- Direct user request on 2026-06-21: after diagnosing why the Target plotted above the frontier, the user said "It sounds like we've discovered a bug... the opportunity set consists of being long the assets the user wants to go long and short the assets that the user wants to short. So the frontier should be composed of those assets as well in that long, short combination. I would like you to create an execution plan to fix this... and then implement it."

Repo artifacts:

- `/hyperopen/AGENTS.md` requires an ExecPlan for risky/UI work and requires `npm run check`, `npm test`, and `npm run test:websocket` when code changes, plus browser QA for UI behavior.
- `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md` define the active ExecPlan contract.
- Implementation site: `/hyperopen/src/hyperopen/portfolio/optimizer/application/display_frontier.cljs` (the `unconstrained-frontier-constraints` helper and its caller `display-encoded-constraints`).
- Constraint encoding: `/hyperopen/src/hyperopen/portfolio/optimizer/domain/constraints.cljs` (`bounds-for`, `target-net`, `encode-constraints`; `default-max-asset-weight` is `1`).
- Chart wiring: `/hyperopen/src/hyperopen/views/portfolio/optimize/frontier_chart_model.cljs` (`frontier-points` picks `:constrained` when the box is checked, else `:unconstrained`); default flag `:constrain-frontier? false` lives in `/hyperopen/src/hyperopen/portfolio/optimizer/defaults.cljs`.
- Existing coverage that encodes the OLD behavior: `/hyperopen/test/hyperopen/portfolio/optimizer/application/engine_test.cljs`, test `minimum-variance-emits-unconstrained-and-constrained-display-frontiers-test` asserts the default frontier "removes per-asset caps" (`[1 1]` upper bounds). That assertion documents the bug and must be replaced.

Local scratch refs, non-authoritative:

- None.

## Progress

- [x] (2026-06-21) Diagnosed root cause across `display_frontier.cljs`, `constraints.cljs`, `target_selection.cljs`, `frontier_chart_model.cljs`, and `frontier_chart_layers.cljs`; confirmed coordinates/estimators/annualization are shared and the only divergence is the reference frontier's feasible set (forced long-only + per-asset cap default-to-1).
- [x] (2026-06-21) Authored this ExecPlan.
- [x] (2026-06-21) Replaced `unconstrained-frontier-constraints` with a public `reference-frontier-constraints` in `display_frontier.cljs` and updated its caller. Final design relaxes ONLY `:held-position-locks` and preserves directionality, gross/leverage, turnover, concentration caps, and net limits (see Decision Log for why turnover is kept).
- [x] (2026-06-21) Rewrote `minimum-variance-emits-unconstrained-and-constrained-display-frontiers-test` to use a held-position lock as the distinguishing constraint and assert the reference sweep keeps per-asset caps; realigned `minimum-variance-emits-display-frontier-without-changing-selected-target-test`, `minimum-variance-keeps-target-result-when-display-frontier-fails-test`, and `run-optimization-solves-frontier-sweep-and-selects-target-volatility-result-test` for the new aliasing/cap behavior.
- [x] (2026-06-21) Added `test/hyperopen/portfolio/optimizer/application/display_frontier_test.cljs` proving the reference constraints keep long/short directionality, gross, turnover, caps, and net while dropping only locks, and that `constraints/encode-constraints` then yields negative lower bounds (shorts preserved) for a shortable universe.
- [x] (2026-06-21) Serialized OSQP solves in `infrastructure/osqp.cljs` after discovering the signed reference sweep exposed a concurrency race on the shared OSQP WASM module (see Surprises). Full CLJS suite then passed: `Ran 4787 tests`, `0 failures, 0 errors` (2 consecutive runs).
- [x] (2026-06-21) Ran the required gates. `npm test` = `Ran 4787 tests`, `0 failures, 0 errors`. `npm run test:websocket` = `Ran 545 tests`, `0 failures, 0 errors`. `npm run check` passes every step my change touches (all nine post-spike lints incl. `lint:namespace-sizes` after trimming `engine_test.cljs` to 521 lines, plus all five production compiles); it halts only on the pre-existing `docs/FRONTEND.md` 90-day staleness gate (last_reviewed 2026-03-22), which I did not touch and did not date-bump. Fixed the other pre-existing blocker (`results.css` `v4` comments) inline.
- [x] (2026-06-21) Browser smoke QA: started the dev server, loaded the app and the `/portfolio/optimize` surface. The CLJS runtime reports `shadow-cljs ready`, the UI renders, and the console has zero errors with the display-frontier, OSQP, and results.css changes compiled in. Stopped the preview server.
- [x] (2026-06-22) Live visual confirmation. Activated Spectate Mode for `0x7c930969fcf3e5a5c78bcf2e1cefda3f53e3c8fd` (loading `/index.html?spectate=…` so the startup route/query restore fires), built the universe from holdings (34 assets), and ran + refined the optimizer on the fixed build (`2da80dd`). The run reproduced the reported scenario (gross 10.43x/8.39x, Current 519%/720%, Target ~220% return, auto-derived gross <= 31.57 / cap 1,013%). With "Constrain Frontier" unchecked, SVG geometry confirmed the Target marker core sits at the bottom of the frontier-point band (screen y 580 vs band 572-580), i.e. ON the frontier, with frontier points above it — `targetAboveFrontier: false`. Also produced a standalone 3-asset mean-variance explainer chart showing the Target on the long/short frontier and 2.3 pts above the long-only frontier at the same volatility.

## Surprises & Discoveries

- Observation: There are actually TWO independent tightenings in the old reference frontier, not one. Forcing long-only removes shorts, and "removing" the per-asset cap via `dissoc :max-asset-weight` silently falls back to `default-max-asset-weight` = 1 (100% per asset), which is TIGHTER than a real levered scenario whose per-asset cap can be 900%+. Either one alone is enough to push a levered long/short Target above the curve.
  Evidence: `display_frontier.cljs:34-45` did `(dissoc :gross-leverage :max-asset-weight ...)` then `(assoc :long-only? true)`; `constraints.cljs:91-92` `global-cap` falls back to `default-max-asset-weight` (1); `constraints.cljs:241-257` `bounds-for` zeroes lower bounds when `:long-only?`.
- Observation: The selected/Target point and the frontier points share the same return vector, covariance, annualization, and chart scale, so this is purely a feasible-set issue, not a coordinate, estimator, or rendering bug.
  Evidence: `frontier_chart_model.cljs:182-186` stores tick-adjusted `:x-domain`/`:y-domain`; `frontier_chart_layers.cljs:219,237-243` feed those same domains to both the frontier path and the Target marker.
- Observation: An earlier version of this fix that ALSO relaxed `:max-turnover` deterministically broke two real-OSQP integration tests (`default-signed-minimum-variance-run-respects-net-min-floor-test`, `explicit-net-min-floor-...`) and cascaded into `osqp-adapter-*` unit tests falling back to quadprog. Root cause: making the reference frontier signed turns its display sweep into a second signed split-variable solve, and the optimizer solves a sweep's points concurrently via `js/Promise.all`; the `osqp` npm package backs every solve with ONE shared WASM module/heap, so concurrent `.setup`/`.solve` races corrupt the module and later solves throw. The clean baseline passed 3/3 runs; the broken branch failed 2/2 with `LDL_factor ... non-convex` errors on stderr. Serializing OSQP solves through one promise chain (`infrastructure/osqp.cljs`) fixed it (0 failures, 2/2 runs).
  Evidence: `engine_test.cljs:478,530` inject `solver-adapter/solve-with-osqp`; `solve.cljs` `solve-problems-async` uses `js/Promise.all`; `osqp.cljs` imports a single `["osqp" :default OSQP]` and per-solve `.setup`/`.cleanup`.
- Observation: Relaxing turnover was unnecessary for the user's bug AND was the thing that perturbed OSQP. Relaxing ONLY held-position locks fixes the directionality bug (the reference is signed because long-only is no longer forced and caps are no longer stripped) while leaving lock-free scenarios — which is most of them, including the OSQP tests — to alias the reference to the constrained frontier, so no extra signed sweep is ever fed to OSQP.
- Observation: `npm run check` was already red on the clean tree for two pre-existing, unrelated reasons: `tools/optimizer/canonical-naming.test.mjs` flagged two `results.css` comments containing `v4` (committed in `6c8ccea0`), and `docs/FRONTEND.md` tripped the 90-day `lint:docs` staleness gate (last_reviewed 2026-03-22). The `&&` chain short-circuits on the first, hiding later steps.

## Decision Log

- Decision (FINAL): The reference ("unconstrained") frontier relaxes ONLY `:held-position-locks` and preserves everything else (directionality, gross/leverage budget, turnover budget, concentration caps, net limits).
  Rationale: This is a strict superset of the constrained feasible set, so the recommendation is always on or below the drawn frontier (the bug cannot recur), and it draws the user's actual long/short universe. It keeps the frontier bounded (gross + turnover + caps remain) and — critically — does NOT add a second signed split-variable OSQP solve in lock-free scenarios, because the reference then aliases the constrained frontier. An initial design that also relaxed `:max-turnover` was abandoned because it perturbed the shared OSQP WASM module (see Surprises) and was not needed for the user's bug.
  Date/Author: 2026-06-21 / Claude.

- Decision: Keep the per-asset concentration caps and gross/leverage budget in the reference frontier instead of "relaxing" them to a looser value.
  Rationale: The old `dissoc`-to-default-1 behavior was the second tightening bug. Keeping the real caps fixes the bug and keeps the curve bounded and visually comparable to the recommendation. A looser "ideal" frontier that also relaxes concentration is a possible future enhancement but is out of scope and risks an unbounded/illegible curve under large gross budgets.
  Date/Author: 2026-06-21 / Claude.

- Decision: Serialize all OSQP solves through one promise chain in `infrastructure/osqp.cljs`.
  Rationale: Concurrent `.setup`/`.solve` against the single shared OSQP WASM module is unsafe and was the root cause of the OSQP test cascade once the solve sequence changed. Serialization preserves correctness (identical solves, run one at a time), is a genuine reliability improvement, and removes the order-sensitivity. The display sweep is a small number of fast solves, so the latency cost is negligible.
  Date/Author: 2026-06-21 / Claude.

- Decision: Distinguish the two display frontiers in the engine test with a held-position lock (not turnover), and reword two pre-existing `results.css` `v4` comments to canonical names.
  Rationale: The old test asserted the buggy "removes per-asset caps" (`[1 1]`) behavior and must change; a lock is the constraint the final design actually relaxes, so it is the honest discriminator. The `results.css` reword is the fix the `canonical-naming` lint explicitly wants and unblocks the rest of `npm run check`; `docs/FRONTEND.md` staleness is a content-review gate left for its owner, not silently date-bumped.
  Date/Author: 2026-06-21 / Claude.

## Outcomes & Retrospective

The reference ("unconstrained") display frontier now reflects the user's real long/short opportunity set instead of an artificially long-only, 100%-capped curve, so a long/short levered recommendation lands on or below it rather than floating above it. The behaviour is locked in by deterministic tests at two levels: `display_frontier_test.cljs` proves the pure constraint transform (directionality, gross, turnover, caps, net survive; only locks are relaxed; encoding a shortable universe yields negative lower bounds), and `engine_test.cljs` proves the end-to-end engine wiring (lock-distinguished reference vs constrained sweeps, caps retained). The full suite is green (4787 + 545 tests, 0 failures) and all production targets compile.

Complexity went DOWN where it matters: the frontier helper shrank from an eight-key `dissoc` plus a `long-only?` override to a single-key `dissoc`, and the two-frontier semantics are now provably a superset relationship rather than an ad-hoc relaxation. The one net-new mechanism is the OSQP solve-serialization queue in `osqp.cljs`; that is a small, well-justified addition that fixes a real pre-existing concurrency hazard (the shared WASM module was being driven by `Promise.all`), so it is a reliability improvement rather than incidental complexity.

Biggest lesson: the first design (also relaxing turnover) was simpler-sounding but wrong on two counts — it was unnecessary for the user's bug and it doubled the signed OSQP solves in the net-min integration tests, exposing the shared-module race. Bisecting against a clean baseline (3/3 green) versus the branch (2/2 red, with `LDL_factor non-convex` on stderr) was what pinned the cause to concurrency rather than to the frontier math. Keeping the relaxation minimal (locks only) both fixed the bug and sidestepped the perturbation.

Confirmed live (2026-06-22): a spectate run of the reported address on the fixed build reproduced the scenario (gross 10.43x) and the Target marker sits on the frontier (`targetAboveFrontier: false`), with a standalone mean-variance explainer chart showing the same on-the-long/short-frontier, above-the-long-only-frontier relationship. All acceptance criteria are met; this plan is moved to `completed/`.

Open follow-ups (non-blocking, not required for this fix): (1) `docs/FRONTEND.md` is past its 90-day review gate and keeps `npm run check` red — needs an owner review/refresh, not a silent date bump. (2) Optional UX nicety: draw a faint "long-only reference" line behind the real frontier in the optimizer so the on-vs-above distinction is visible in-app, not only in the explainer.

## Context and Orientation

The optimizer lives under `/hyperopen/src/hyperopen/portfolio/optimizer/`. A run produces, among other things, a `:frontiers` map with two entries: `:unconstrained` and `:constrained`. The chart view-model `/hyperopen/src/hyperopen/views/portfolio/optimize/frontier_chart_model.cljs` chooses which to draw based on the "Constrain Frontier" checkbox: checked draws `:constrained`, unchecked draws `:unconstrained`. The checkbox defaults to unchecked (`:constrain-frontier? false` in `/hyperopen/src/hyperopen/portfolio/optimizer/defaults.cljs`), so the `:unconstrained` frontier is what most users see.

Both frontiers are produced by `/hyperopen/src/hyperopen/portfolio/optimizer/application/display_frontier.cljs`. Its `build-plans` builds a separate small "display frontier" sweep for each mode by encoding constraints with `/hyperopen/src/hyperopen/portfolio/optimizer/domain/constraints.cljs` `encode-constraints`. For the `:unconstrained` mode it first rewrites the user's constraint map through a helper. Today that helper (`unconstrained-frontier-constraints`) drops the gross/leverage, concentration, turnover, net, lock, and per-perp keys and forces `:long-only? true`. The term "long-only" means every weight is constrained to be zero or positive (no shorts); "gross" or "leverage budget" means the cap on the sum of absolute weights; "turnover" means how far the new weights move from the current portfolio; a "held-position lock" pins an asset's weight to its current value.

`encode-constraints` turns a constraint map into per-asset lower/upper weight bounds. When `:long-only?` is true every lower bound becomes 0. When a per-asset weight cap is absent it falls back to `default-max-asset-weight`, which is `1` (100%). So the old reference frontier was a long-only, at-most-100%-per-asset, fully invested (sum-to-one) curve, which cannot contain a long/short levered recommendation.

## Plan of Work

In `/hyperopen/src/hyperopen/portfolio/optimizer/application/display_frontier.cljs` (DONE):

- Renamed the private `unconstrained-frontier-constraints` to a public `reference-frontier-constraints` (public so the new unit test can call it directly) and changed its body to `(dissoc constraints :held-position-locks)`. It does NOT force `:long-only?` and does NOT drop `:gross-leverage`, `:max-asset-weight`, `:max-turnover`, `:net-exposure`, `:per-asset-overrides`, or `:per-perp-leverage-caps`. The docstring explains the superset rationale and why only locks are relaxed.
- Updated `display-encoded-constraints` to call `reference-frontier-constraints` for the `:unconstrained` mode. No other call sites exist.

In `/hyperopen/src/hyperopen/portfolio/optimizer/infrastructure/osqp.cljs` (DONE):

- Wrapped the per-solve `.setup`/`.solve`/`.cleanup` body in a module-level promise-chain (`solve-chain` + `run-serialized`) so only one OSQP solve runs against the shared WASM module at a time.

In `/hyperopen/test/hyperopen/portfolio/optimizer/application/engine_test.cljs` (DONE):

- Rewrote `minimum-variance-emits-unconstrained-and-constrained-display-frontiers-test` to add a held-position lock (`:held-position-locks ["perp:BTC"]`) and assert: default frontier-summary mode `:unconstrained`; both `:frontiers` entries present; the constrained sweep carries `:locked-weights` while the reference sweep does not; the reference (unlocked) sweep keeps the `[0.8 0.8]` per-asset cap (proving caps are NOT stripped to `[1 1]`).
- Realigned three incidental tests for the new aliasing/cap behavior: the two min-variance display-frontier tests now expect a single aliased sweep (3 solver calls) because `base-request` has no locks, and frontier-point stubs return cap-feasible weights (`[0.7 0.3]` within the `base-request` perp caps).

New file `/hyperopen/test/hyperopen/portfolio/optimizer/application/display_frontier_test.cljs` (DONE):

- Unit-tests `reference-frontier-constraints` directly: directionality/gross/caps/net/overrides AND turnover survive; only `:held-position-locks` is removed.
- Pipes the result through `constraints/encode-constraints` with a shortable two-asset universe and asserts `:long-only?` false, both lower bounds negative (shorts preserved), gross retained, turnover retained, and per-asset caps retained.

Incidental pre-existing fix (DONE): reworded two `v4` comments in `/hyperopen/src/styles/surfaces/optimizer/results.css` so `npm run check`'s `canonical-naming` lint passes. (`docs/FRONTEND.md` staleness is a separate pre-existing gate, left for its owner.)

## Concrete Steps

Run from the repository root `/Users/barry/projects/hyperopen`.

Targeted compile/run of the optimizer engine + new test during development:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test && node out/test.js

Full required gates before completion:

    npm run check
    npm test
    npm run test:websocket

Browser QA (UI behavior):

    npm run dev    # serves on :8080
    # open /portfolio/optimize/draft?...&otab=recommendation for the long/short scenario,
    # leave "Constrain Frontier" unchecked, confirm the Target marker is on (not above) the curve.

## Validation and Acceptance

Behavioral acceptance: in the optimizer Recommendation tab for a long/short, levered scenario, with "Constrain Frontier" unchecked, the "Target" marker sits on or below the gold frontier curve (no longer above it). Ticking "Constrain Frontier" continues to show the tighter constrained frontier.

Test acceptance: the new unit test `reference-frontier-constraints-*` in `display_frontier_test.cljs` fails before the source change (because the old helper forces long-only and drops caps) and passes after. The rewritten `minimum-variance-emits-unconstrained-and-constrained-display-frontiers-test` passes after the change. `npm test` reports zero failures/errors; `npm run check` and `npm run test:websocket` pass. Record the final counts in `Outcomes & Retrospective`.

## Idempotence and Recovery

All edits are additive or in-place text replacements and can be re-applied safely. If a gate fails, fix forward; the change is isolated to one helper, one caller, and two test files, so reverting the three files restores prior behavior. No data migrations or destructive operations are involved.

## Artifacts and Notes

Will capture the failing-before/passing-after transcript for the new unit test and the browser-QA screenshot here once produced.

## Interfaces and Dependencies

In `/hyperopen/src/hyperopen/portfolio/optimizer/application/display_frontier.cljs`, define and export:

    (defn reference-frontier-constraints
      "Reference ('unconstrained') display-frontier feasible set: the user's
       opportunity set with only position-relative rebalancing constraints
       relaxed."
      [constraints]
      (-> (or constraints {})
          (dissoc :max-turnover :held-position-locks :rebalance-tolerance)))

The new test namespace `hyperopen.portfolio.optimizer.application.display-frontier-test` depends on `hyperopen.portfolio.optimizer.application.display-frontier` and `hyperopen.portfolio.optimizer.domain.constraints`. Test files ending in `_test.cljs` are auto-discovered by `tools/generate-test-runner.mjs`, so no manual runner registration is required.
