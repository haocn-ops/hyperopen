# Optimizer Equal Risk objective (constrained signed-Euler risk parity)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. Maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Add one new allocation objective to the portfolio optimizer at `/portfolio/optimize/new`: **Equal Risk** (canonical kind `:equal-risk`). After this change a trader who has already decided which assets to hold, which are long, which are short, and what gross leverage and net bias they want, can pick "Equal Risk" and the optimizer sizes those predetermined positions so each position's signed Euler contribution to total portfolio volatility is as equal as possible — while exactly hitting the selected gross and net exposure targets and every hard constraint (per-asset caps, locks, shortability, turnover). The result page shows per-asset risk contributions, the equal target share, the realized error, and an honest `exact` / `approximate` / `not-converged` badge. Expected-return forecasts never influence the weights; the covariance from the selected risk model is the only estimator input.

To see it working: run the dev server, open `/portfolio/optimize/new`, add two or more perps, pick Equal Risk in the goal selector, and Run. The Results tab shows a single selected portfolio (no efficient-frontier sweep), a risk-contribution table where each row shows its share of portfolio volatility versus the `1/n` target, and the realized gross/net matching the Positioning targets.

## Context References

Public refs:
- Direct user/maintainer request (2026-07-10): full specification pasted into the session; also discussed at https://chatgpt.com/share/6a51af6d-91ec-83e8-a36b-11151b8b35d2 (external, non-authoritative; the pasted spec is authoritative and this plan restates everything needed).

Repo artifacts:
- `/hyperopen/src/hyperopen/portfolio/optimizer/BOUNDARY.md` (optimizer ownership map)
- `/hyperopen/ARCHITECTURE.md` (layering rules)

Local scratch refs (non-authoritative): none.

## Progress

- [x] (2026-07-10) Read AGENTS.md, ARCHITECTURE.md, BOUNDARY.md, PLANS.md; mapped every optimizer domain/application/infrastructure/contract/view seam this change touches (details in Context and Orientation).
- [x] (2026-07-10) Wrote this ExecPlan.
- [x] (2026-07-10) Domain: `domain/risk_contributions.cljs` (contribution math, analytic gradient, degeneracy guard, diagnostics from final weights) + tests incl. central-difference gradient checks.
- [x] (2026-07-10) Domain: `domain/equal_risk.cljs` (tolerances, exposure targets, book split, presolve violations with user-facing messages, seeds, bounded-book bisection projection, QP subproblem templates, damped BFGS update, quality classification, plan builder) + tests.
- [x] (2026-07-10) Domain seams: `exposure_policy/engine-constraints->policy`; `constraints.cljs` encodes `:exposure-targets` + `:side-metadata`; `objectives.cljs` plans `:equal-risk` via the new builder.
- [x] (2026-07-10) Application: `engine/equal_risk_solve.cljs` sequential SQP driver (sync+async through injected QP solver), `engine/solve.cljs` dispatch + progress threading, `engine/context.cljs` return-model bypass for `:equal-risk`, `engine/payload.cljs` risk-contribution + solver-metadata payload sections and no-dust cleaning, `engine/target_selection.cljs` metadata passthrough, `application/progress.cljs` step copy.
- [x] (2026-07-10) Contracts: `contracts/constants.cljs` objective enum, `contracts/specs.cljs` optional payload predicates, `instrument_keyed_codec.cljs` wire keys (2 by-instrument maps + 5 enum-valued keys incl. :position-side); contract/codec/migration tests in `contracts_equal_risk_test.cljs`.
- [x] (2026-07-10) UI: Equal Risk goal card (parameterless More-goals entry), objective menu entry with covariance-only description, label maps (`setup_summary`, `format`, `setup_actions`, `scenario_objective_menu`, `actions/draft`), "Return forecast: Not used" treatment broadened to return-free objectives, results panel `risk_contributions_card.cljs` with exact/approximate/not-converged badge, frontier chart + refinement card hidden for `:equal-risk`, infeasible-banner control-key hints for the new violation codes; view tests.
- [x] (2026-07-10) Mathematical test battery (spec tests 1–16) + engine integration tests + worker OSQP test: 5413 tests / 29153 assertions, 0 failures.
- [x] (2026-07-11) Docs: `docs/design-docs/optimizer-equal-risk.md` (+ index entry); formulas as docstrings beside the pure domain code.
- [x] (2026-07-11) Playwright: Equal Risk card flow added to the "portfolio optimizer setup exposes separate model layers" test in `tools/playwright/test/portfolio-regressions.spec.mjs`.
- [x] (2026-07-11) `npm run lint:delimiters -- --changed` passes (required fixing the preflight tool itself — see Surprises).
- [x] (2026-07-11) Focused Playwright passes (18.5s first green, 13.7s on the final tree) after repairing the PRE-EXISTING stale Risk-guards assertions (see Surprises); adjacent frontier-overlays regression also passes.
- [x] (2026-07-11) Namespace-size gate: split `domain/equal_risk.cljs` (654) into core (275) + `equal_risk_presolve.cljs` (205) + `equal_risk_plan.cljs` (197); trimmed `payload.cljs`/`return_views_panel.cljs`/`constraints.cljs` back under their budgets; moved the equal-risk panel tests to `results_panel_equal_risk_test.cljs`.
- [x] (2026-07-11) All gates green on the FINAL tree: `npm run lint:delimiters -- --changed` (44 files), `npm run check` (exit 0), `npm test` (5413 tests / 29153 assertions / 0 failures), `npm run test:websocket` (546 / 3133 / 0), focused Playwright (1 passed).
- [x] (2026-07-11) Live-usage follow-up: non-blocking `:equal-risk-lopsided-books` warning when one book's budget is under 5% of gross while 2+ assets sit on that side (found running the spectated whale book live: holdings-seeded targets G=14.87/N=14.83 left 0.02x for four shorts, run was honest-Approximate but surprising). Presolve emits it, the OK plan carries `:warnings`, solved payloads now merge `(:warnings solver-plan)`; domain + engine tests added.
- [x] (2026-07-11) Results-page redesign (diverging risk-contribution balance chart, objective-specific KPI/verdict/rail, allocation-freedom classification) executed under its own plan: `/hyperopen/docs/exec-plans/active/2026-07-11-optimizer-equal-risk-results-redesign.md`.
- [ ] Post-merge follow-up (not this change, explicitly optional per spec): an exact-ERC fast path — solve ordinary fixed-sign ERC, normalize to the gross target, accept only when net/bounds/locks/turnover and contribution tolerance all hold.

## Surprises & Discoveries

- Observation: The trader-facing exposure control is target+band only in the VIEW; the canonical draft keys are `gross-min/gross-max/net-min/net-max`, renamed by the request builder to `gross-floor/gross-leverage/net-exposure`. `exposure-policy/constraints->policy` recovers the exact selected targets (midpoints; `gross-max` alone when the band is zero because `policy->constraints` dissocs `gross-min` at zero band). So targets DO survive into the engine request losslessly and no new request field is needed — only a canonical derivation helper reading the engine-side key names.
  Evidence: `src/hyperopen/portfolio/optimizer/domain/exposure_policy.cljs` `constraints->policy`/`policy->constraints`; `application/request_builder.cljs normalize-constraints`.
- Observation: Draft migration (`contracts/migrations.cljs migrate-universe-instrument`) already forces `:position-side` (`:long` default, `:short` only when shortable) on every universe instrument, and `build-engine-request` migrates the draft first. Therefore every UI-built request has single-signed bounds and the fixed-sides presolve failure is only reachable from hand-built requests — presolve (not setup readiness) is the right home for the check.
- Observation: Default drafts ship `:max-turnover 1.0` active (`defaults.cljs`). A first version that "blocks on turnover" would block every default run; supporting turnover through the existing split-variable QP channel is therefore mandatory, and is why the solver iterates QP subproblems through the injected solver adapter instead of a hand-rolled projected gradient (whose L1-ball projection would not be trivial).
- Observation: `engine/solve.cljs solve-one` is used by BOTH the synchronous `run-optimization` (tests, quadprog) and the promise-based `run-optimization-async` (worker, OSQP). OSQP's `solve` returns a Promise, quadprog returns a plain map. The sequential driver therefore chains through a "thenable-aware" helper: with a synchronous solver the whole solve stays synchronous; with a promise solver it becomes a promise that `solve-plan-async`'s `js/Promise.resolve` absorbs. This preserves both existing engine contracts without duplicating the driver.
- Observation: The OSQP wrapper (`infrastructure/osqp.cljs normalize-solution`) always reports `:status :solved`; infeasibility only surfaces as constraint violations on the returned point. The driver therefore validates every accepted iterate itself (books/bounds/turnover within tolerance) rather than trusting solver status.
- Observation: `weight-cleaning/clean-weights` drops sub-dust weights and (for long-only) rescales. Either action would break the exact book equalities the solver just achieved, so the payload path pins `:dust-threshold 0` for `:equal-risk` and diagnostics are computed from exactly the published weights.
- Observation: The request signature hashes `(:constraints request)` RAW (`contracts/signatures.cljs` has no constraints stripper). Deriving the gross/net targets inside `encode-constraints` (downstream of the request, in the engine) keeps every existing draft's signature byte-identical; writing a derived key into request constraints would have churned signatures and caused false staleness.
- Observation: The result payload spec (`contracts/specs.cljs solved-result-payload?`) is an open predicate (unknown keys pass) and is enforced only in tests/dev preload, and scenario persistence is EDN (`infrastructure/persistence.cljs encode-record`), so the new payload keys need no schema bump and no migration; only the worker-wire codec registries need entries so instrument-keyed maps and keyword enums survive the boundary.
- Risk noted up front (to verify during implementation): QP solvers return points a few 1e-9 OUTSIDE bounds (the diagnostics binding tolerance comment documents ~3e-10 excursions), so iterate feasibility validation must use the documented 1e-6 tolerances, not exact comparisons; and quadprog can report "constraints are inconsistent" on tight-but-feasible subproblems, so a failed subproblem must degrade to "stop this initializer, keep best feasible iterate" rather than failing the run. Outcome: the whole 16-case battery passed with the 1e-6 iterate tolerances on the FIRST run; the only driver fix needed was classifying an all-zero covariance as degenerate when some seeds were merely `:seed-unavailable` (inverse-vol cannot build on zero variance).
  Evidence: first full-suite run after the battery landed: `Ran 5397 tests … 3 failures` — one driver classification fix + two view tests asserting the pre-Equal-Risk menu option count.
- Observation: the osqp npm package's WASM heap is shared process-wide and the existing serialization queue (`infrastructure/osqp.cljs solve-chain`) already serializes every solve, so the sequential driver's one-at-a-time QP calls need no new coordination. The worker-path test (real OSQP under Node) hits the gross/net targets to 1e-5 and matches the quadprog battery's structure.
- Observation (validation): `npm run lint:delimiters -- --changed` could NEVER pass for `contracts/specs.cljs` — or 63 other files — because the preflight's edamame reader lacked `:auto-resolve`, so any auto-resolved keyword (`::draft`) threw. Verified pre-existing by linting the HEAD copy of specs.cljs (same failure). Fixed the TOOL (`dev/delimiter_preflight.clj` parse-opts now auto-resolve `::k`/`::alias/k` syntactically); the full-repo run went from 64 failures to 1944/1944 passing and the preflight's own 11 tests stay green.
- Observation (validation): the delimiter incident was two-sided — my first spec edit left the new `:risk-contributions` `or` unclosed (balanced overall but the `or` silently swallowed the rebalance-preview validation: tests still passed because valid fixtures pass either way), and my first "fix" over-closed the big `and` (making the spec vacuously check only rebalance-preview — caught by the EXISTING malformed-payload rejection tests flipping to failures). The final shape is verified by both the delimiter gate and the rejection tests. Lesson recorded: for lisp predicate chains, run the REJECTION tests, not just the acceptance tests, after any paren surgery.
- Observation (validation): the focused Playwright test ("portfolio optimizer setup exposes separate model layers") was ALREADY red on a clean checkout — the 2026-07-10 quieter-by-default setup pass moved the per-asset cap input behind the Risk-guards card's Edit disclosure, and the spec still asserted the input visible at rest. Verified by `git stash -u` → same failure → `git stash pop`. Repaired the stale assertions (rest state asserts the read-only "50%" value row + hidden input; interactions open the card first), then added the Equal Risk flow; focused test passes in 18.5s and the adjacent frontier-overlays regression passes too.

## Decision Log

- Decision: Derive the gross/net TARGETS inside `encode-constraints` via a new canonical `exposure-policy/engine-constraints->policy` (delegating to the existing `constraints->policy` midpoint math) and expose them on the encoded map as `:exposure-targets`, instead of adding a new field to the engine request.
  Rationale: The engine constraint keys carry the exact band edges losslessly; one derivation site in the exposure-policy namespace honors the "no duplicated target/band conversion" rule; request signatures for existing drafts stay byte-identical because no request key changes.
  Date/Author: 2026-07-10 / equal-risk implementation session.
- Decision: Long-only requests (`:long-only? true`) use targets G=1, N=1 (single long book summing to exactly 1).
  Rationale: Long-only already forces `net-target 1` in the constraint encoder; a fully invested long book is the only consistent reading, and it matches how every other objective treats long-only.
  Date/Author: 2026-07-10.
- Decision: Book membership is decided by the ENCODED bounds (lower ≥ 0 → long book, upper ≤ 0 → short book, `[0,0]` → long book, straddling zero → presolve `:equal-risk-requires-fixed-sides`); the REQUESTED side is used only to detect the silent short→long bound flip for non-shortable assets (presolve `:equal-risk-short-not-shortable`).
  Rationale: Bounds are the authoritative feasible region (they already fold in locks, shortability, and side pins); using them cannot disagree with what the solver can actually do. The requested-side check exists because `bounds-for` silently long-flips a non-shortable short request, which the spec forbids for this objective.
  Date/Author: 2026-07-10.
- Decision: Solver architecture is sequential quadratic programming (damped BFGS model Hessian, Armijo backtracking, feasible iterates) where every subproblem — including seeding projections — is solved by the EXISTING injected QP solver (quadprog sync / OSQP async) with the existing split-variable L1 channel for gross cap and turnover. Strategy name: `:sequential-equal-risk`.
  Rationale: The default draft has turnover active, so turnover support is mandatory; the QP adapter already encodes it correctly, is deterministic, and keeps the new code free of solver internals. A pure projected-gradient variant would have had to reject turnover.
  Date/Author: 2026-07-10.
- Decision: Four deterministic initializers, run in fixed order with early exit once a run classifies `:exact`: equal-notional per book, inverse-volatility per book, inverse-variance per book, current-weights; each raw seed is first shaped by a deterministic bounded-book bisection projection (books + boxes) and then projected onto the FULL feasible set (including turnover and locks) by a `minimize ||y − seed||²` QP.
  Rationale: The spec's list (equal notional, inverse vol, current, one more justified initializer). Inverse-variance is the justified fourth: it brackets inverse-vol for high-dispersion universes, costing one more deterministic start. The projection QP guarantees feasible starts under every supported constraint, which plain bisection cannot (turnover).
  Date/Author: 2026-07-10.
- Decision: `:quality` classification: `:exact` requires max-abs RRC error ≤ tolerance AND rms ≤ tolerance AND `converged?`; a converged solve above tolerance is `:approximate`; a non-converged solve is always `:not-converged` (plus a visible warning), even if its realized error happens to be small.
  Rationale: The spec both says ":exact when the realized error is below the documented exactness tolerance" and "mark iteration-limited results :not-converged". The strictest consistent reading gates `:exact` on convergence; it can never over-claim.
  Date/Author: 2026-07-10.
- Decision: Exactness tolerance is per-target-share: max |RRC_i − 1/n| ≤ 0.01 × (1/n) and rms ≤ 0.01 × (1/n).
  Rationale: An absolute tolerance mislabels large-n portfolios (where 1/n itself is small) — 1% of each asset's own target share is scale-honest and documented in one place.
  Date/Author: 2026-07-10.
- Decision: The published equal-risk weights skip dust cleaning (`:dust-threshold 0`) and are exactly the validated solver weights; all diagnostics (including `:risk-contributions`) are computed from the published vector.
  Rationale: Every position in the fixed universe is intentional; dropping dust would break the exact book equalities the solver honored, violating the final-weight feasibility requirement. Re-projection after cleaning would be extra machinery for zero benefit here.
  Date/Author: 2026-07-10.
- Decision: The plan-level problem map (`:kind :sequential-equal-risk`) carries the FULL constraint encoding (book equalities, net equality, net-band and gross-floor inequalities, gross/turnover L1, bounds, locks) so `target-selection`'s existing solver-result validation re-checks the final point; the per-iteration QP subproblems carry only the MINIMAL independent rows (two book equalities + bounds + L1 channels).
  Rationale: quadprog's Goldfarb–Idnani method can fail on linearly dependent equality rows (net = long − short is implied by the books); validation-side redundancy is harmless and keeps the honesty check strong.
  Date/Author: 2026-07-10.
- Decision: No efficient-frontier sweep and no fabricated frontier: `build-display-frontier-plan` already returns nil for unknown kinds (add an explicit test), the payload's existing target-solve fallback carries the single selected point, and the RESULTS PANEL hides the frontier chart section for `:equal-risk` (replacing it with the risk-contribution section).
  Rationale: A one-point frontier chart renders a lone draggable dot whose click handler would silently switch the objective to Target Return (`frontier_chart_model.cljs objective-target` defaults to `:target-return`) — hiding the section is the honest "no frontier" treatment the spec allows, without touching the chart component.
  Date/Author: 2026-07-10.
- Decision: For `:equal-risk` the engine proceeds when the return model is `:invalid` (risk model readiness still required); portfolio-level expected-return/Sharpe display metrics are nil'd and a non-blocking `:return-model-unavailable-for-display` warning is attached. Other objectives keep the existing invalid-return-model infeasibility. `:equal-risk` stays OUT of `history_assumptions/objectives-needing-expected-return`, so assumption readiness never demands expected returns for it.
  Rationale: Equal Risk is covariance-only per spec; return-model failure must not block it, but fabricating return metrics from a zero vector would be dishonest.
  Date/Author: 2026-07-10.
- Decision: New payload maps keyed by instrument id are named `:relative-contributions-by-instrument` and `:target-relative-contributions-by-instrument` (registered in `instrument_keyed_codec.cljs instrument-keyed-map-keys`); keyword-valued fields `:method`, `:quality`, `:termination-reason`, `:selected-initialization` are registered in `enum-value-keys`. The parent keys `:risk-contributions` / `:equal-risk-solver` need no registration (plain wrapper maps).
  Rationale: Matches the existing `-by-instrument` codec convention; without registration, worker-boundary decoding would keywordize instrument-id map keys (the ":perp:BTC" phantom-row bug class) and leave enum strings as strings.
  Date/Author: 2026-07-10.
- Decision: Solver metadata excludes wall-clock timing (`:elapsed-ms` etc.); `:selected-initialization` records the initializer NAME (e.g. `:inverse-volatility`); `:initialization-count` records how many initializers actually ran (early exit can make it < 4).
  Rationale: The determinism test compares termination metadata for equality; timing is the one nondeterministic field. Names are stable under early exit, indexes are not.
  Date/Author: 2026-07-10.
- Decision: The setup goal selector gains an "Equal Risk" card that dispatches `set-portfolio-optimizer-objective-kind :equal-risk` (leaving the return model untouched), and the results-route objective menu gains `{:key :equal-risk :title "Equal Risk" :description …}` with matching entries in `draft_options.cljs objective-models` / `objective-menu-options` and the two objective-kind→menu-key conds.
  Rationale: Equal Risk has no parameters and must not clobber the user's return-model/views (which stay relevant for display and for switching back); the parameterless secondary-card dispatch pattern already exists.
  Date/Author: 2026-07-10.
- Decision: Description copy (menu + card subtitle): "Balances each selected position's contribution to portfolio volatility as closely as possible while preserving the selected long/short sides, gross leverage, net bias, and position limits." with the supporting note "Uses the selected risk model's covariance only — return forecasts never affect the weights. Exact equality may not be achievable under your exposure targets; results are labeled exact or approximate."
  Rationale: Spec-recommended wording plus the three required disclosures (returns unused, risk model decides, exactness not guaranteed).
  Date/Author: 2026-07-10.
- Decision: Keep every NEW namespace under the 500-LOC cap by splitting the domain policy — `domain/equal_risk.cljs` (tolerances, targets, books, seeds, BFGS; 275), `domain/equal_risk_presolve.cljs` (feasibility violations + presolve; 205), `domain/equal_risk_plan.cljs` (QP shapes, iterate validation, build-plan; 197) — with acyclic requires core ← presolve ← plan, alongside `domain/risk_contributions.cljs` and the application driver. No size-exception registry entries for new files; the three EXISTING files my edits pushed over budget (engine payload, return-views panel, constraints) were trimmed back under their budgets instead, and the appended results-panel tests moved to their own `results_panel_equal_risk_test.cljs`.
  Rationale: ARCHITECTURE.md caps new namespaces at 500 LOC and the exception registry is for pre-existing debt, not new code; the split follows the natural seams (policy core vs feasibility screening vs plan construction).
  Date/Author: 2026-07-11 (revised: the single domain namespace had reached 654 lines once presolve messages landed).

## Outcomes & Retrospective

- (2026-07-11, completion) Shipped end to end and verified against the original purpose: a trader can select Equal Risk in the setup goal cards or the results objective menu, run, and read signed per-asset risk contributions with a truthful exact/approximate/not-converged badge; the published weights hit the selected gross/net targets to ≤1e-6 in every test; sides, caps, locks, shortability, and turnover are honored exactly; no frontier sweep runs; the return model cannot block the solve. Minimum Variance and Maximum Sharpe behavior is untouched (their tests pass unchanged, and the planner branch is additive). All required gates pass on the final tree: lint:delimiters --changed, check, test (5413/29153/0), test:websocket (546/3133/0), focused Playwright plus the adjacent frontier regression.
- Gaps deliberately left: no exact-ERC fast path (optional per spec, tracked as the remaining unchecked item); turnover-vs-target conflicts surface at solve time as `:equal-risk-no-feasible-start` with a turnover-naming message rather than in presolve (minimum-turnover feasibility is itself an optimization); the objective is nonconvex so global optimality is not claimed — four deterministic starts and truthful labeling stand in.
- Complexity: net increase, but contained behind existing seams — five new namespaces (three domain, one application driver, one view card), zero public API shape changes (one enum value, optional payload keys), no schema bumps, no migrations. The riskiest piece (one driver serving the sync engine and the promise-based worker) is neutralized by the thenable-aware chaining helper and pinned by an explicit sync-vs-async equivalence test.
- Lessons: (1) the fixture deep-merge kept `:long-only? true` under my overrides and produced inverted test outcomes — state every load-bearing constraint explicitly in fixture overrides; (2) after paren surgery on predicate chains, run the REJECTION tests — acceptance tests passed through two differently-wrong nestings; (3) two gates were broken before this feature touched them (the delimiter tool on `::keywords`, the Playwright setup spec after the 2026-07-10 redesign) — verifying failures against a clean stash before debugging saved hours.

## Context and Orientation

The optimizer lives under `/hyperopen/src/hyperopen/portfolio/optimizer/`. Pure math/policy is in `domain/`; deterministic orchestration in `application/`; browser/worker/solver integrations in `infrastructure/`; map shapes, enums, schema versions and worker wire codecs in `contracts*`; the web-worker entry is `worker.cljs`. Views render under `/hyperopen/src/hyperopen/views/portfolio/optimize/`.

A run flows: draft → `application/request_builder.cljs build-engine-request` (migrates the draft, renames constraint keys: `:gross-max`→`:gross-leverage`, `:gross-min`→`:gross-floor`, `:net-min/:net-max`→`:net-exposure {:min :max}`) → worker → `application/engine.cljs run-optimization-async` → `engine/context.cljs optimization-context` (risk covariance, expected returns, `domain/constraints.cljs encode-constraints`, `domain/objectives.cljs build-solver-plan`) → `engine/solve.cljs` maps the plan's `:problems` through the injected QP solver (`infrastructure/solver_adapter.cljs`: quadprog sync, OSQP async-promise) → `engine/target_selection.cljs` validates each solver result against the problem's constraint encoding and picks the target → `engine/payload.cljs` cleans weights, computes diagnostics, and assembles the result payload that crosses the worker wire (`infrastructure/wire.cljs` + `instrument_keyed_codec.cljs`).

Terms used below:
- "Books": the long book (assets whose encoded bounds keep the weight ≥ 0) and the short book (bounds ≤ 0). Book gross targets follow from the user's gross target G and net target N: G_long = (G+N)/2, G_short = (G−N)/2. Feasibility requires G > 0 and |N| ≤ G (both book targets nonnegative).
- "Signed Euler contribution": with covariance Σ, weights w, m = Σw, q = wᵀΣw, σ = √q, asset i contributes RC_i = w_i·m_i/σ to σ (they sum to σ). The relative contribution RRC_i = w_i·m_i/q sums to 1. Contributions may be NEGATIVE (a hedge) and are never absolute-valued.
- "Objective": F(w) = 0.5 · mean_i((RRC_i − 1/n)²), minimized subject to exact book equalities and all hard constraints. Analytic gradient (u_i = w_i·m_i, e_i = RRC_i − 1/n, Σ symmetric):

      ∂RRC_i/∂w_j = (δ_ij·m_i + w_i·Σ_ij)/q − 2·u_i·m_j/q²
      ∇F_j = (1/(n·q)) · ( e_j·m_j + (Σ·(e⊙w))_j − (2·(e·u)/q)·m_j )

- "SQP": at iterate w_k solve the convex QP `min 0.5·yᵀB_k·y + (g_k − B_k·w_k)ᵀy` over the feasible set (equivalent to minimizing `0.5·(y−w_k)ᵀB_k(y−w_k) + g_kᵀ(y−w_k)`), take d = y − w_k, Armijo-backtrack along w_k + αd (feasible because the set is convex), update B by damped BFGS (Powell damping; skip update on invalid curvature; rescale B once after the first accepted step by `(y·y)/(y·s)`).

UI seams enumerated from the current checkout (all under `src/hyperopen/` unless noted): `views/portfolio/optimize/setup_objective_controls.cljs` (`goal-summary` case + goal cards), `views/portfolio/optimize/scenario_objective_menu.cljs` (`objective-menu-options` vector, `current-objective-menu-key`, `objective-label`), `portfolio/optimizer/actions/draft_options.cljs` (`objective-models`, `objective-menu-options`), `portfolio/optimizer/actions/draft.cljs` (`current-objective-menu-option`), `views/portfolio/optimize/setup_actions.cljs` (`action-objective-label`), `portfolio/optimizer/application/view_model/setup_summary.cljs` (`objective-display-names`, `min-variance?`/return-forecast label), `views/portfolio/optimize/setup_context.cljs` (right-rail return-views note), `views/portfolio/optimize/return_views_panel.cljs` (return-free note), `views/portfolio/optimize/format.cljs` (`display-labels`), `views/portfolio/optimize/results_panel.cljs` (frontier section gating + new risk-contribution section), `views/portfolio/optimize/frontier_chart*.cljs` (untouched; section hidden for `:equal-risk`).

Contract seams: `contracts/constants.cljs objective-kinds` (THE gate — draft spec, persistence guard, engine-request spec all read it), `contracts/specs.cljs` (open payload predicate; optional hardening), `instrument_keyed_codec.cljs` (`instrument-keyed-map-keys`, `enum-value-keys`), signatures/migrations/persistence need NO change (see Surprises).

## Plan of Work

Pure domain (new): `domain/risk_contributions.cljs` holds the contribution math, degeneracy guard (q must be finite and exceed `1e-12 · mean-diag(Σ) · ‖w‖²`; nonpositive/nonfinite/near-zero q → explicit `{:status :error :reason :degenerate-variance}`), the analytic gradient above, covariance validation (finite, square, aligned; symmetrize only when max asymmetry ≤ 1e-8 relative, else reject), and `contribution-summary` (the `:risk-contributions` payload section computed from final weights). `domain/equal_risk.cljs` holds the centralized `tolerances` map (exposure feasibility 1e-6, bound feasibility 1e-6, degeneracy factor 1e-12, Armijo c1 1e-4 with α ≥ 1e-6, step tolerance 1e-8·max(1,G), objective-improvement 1e-12, exactness ratio 0.01 of 1/n, caps: 80 iterations/init, 4 inits, 25 line-search halvings), target derivation from `:exposure-targets`/long-only, book split from encoded bounds + side metadata, the presolve checks (codes `:equal-risk-invalid-exposure-targets`, `:equal-risk-gross-target-not-positive`, `:equal-risk-net-exceeds-gross`, `:equal-risk-gross-target-above-max`, `:equal-risk-gross-target-below-floor`, `:equal-risk-net-target-outside-band`, `:equal-risk-requires-fixed-sides`, `:equal-risk-short-not-shortable`, `:equal-risk-long-book-empty`, `:equal-risk-short-book-empty`, `:equal-risk-long-book-minimum-above-target`, `:equal-risk-long-book-capacity-below-target`, `:equal-risk-short-book-minimum-above-target`, `:equal-risk-short-book-capacity-below-target`, `:equal-risk-covariance-shape`, `:equal-risk-covariance-asymmetric`, `:equal-risk-unsupported-constraint`), seed construction, the bounded-book bisection projection (x_i = clip(v_i − λ, lower_i, upper_i) with deterministic bisection on λ per book), QP subproblem templates (projection: quadratic = I, linear = −seed; iteration: quadratic = B_k, linear = g_k − B_k·w_k; both with the two book equalities + bounds + the L1 gross/turnover channels), damped-BFGS update, iterate feasibility validation, and quality classification. `build-plan` returns `{:status :ok :strategy :sequential-equal-risk :problems [one problem]}` or `{:status :infeasible :reason :equal-risk-presolve :details {:violations […]}}`.

Seams changed: `domain/exposure_policy.cljs` gains `engine-constraints->policy`; `domain/constraints.cljs encoded-result` gains `:exposure-targets` and `:side-metadata`; `domain/objectives.cljs build-solver-plan` gains the `:equal-risk` branch (closed-form already rejects unknown kinds); `domain/history_assumptions.cljs` docstring for the return-required set names `:equal-risk` as return-free.

Application: new `application/engine/equal_risk_solve.cljs` — the sequential driver. It receives (problem, solve-problem, on-progress), builds seeds, projects each to feasibility (projection QP, validated), runs the SQP loop per initializer with progress callbacks (`{:step :solve :percent … :detail "init i/k · iter j · rms x%"}`), collects per-init outcomes, picks the best feasible F (ties: earliest initializer), classifies quality, and returns a solver-result map `{:status :solved :solver :sequential-equal-risk :weights … :objective-value … :iterations … :equal-risk {…metadata…}}` (or `{:status :infeasible :reason :equal-risk-no-feasible-start :violations …}` when no seed projects feasibly, e.g. turnover conflicts). It chains through a thenable-aware helper so quadprog stays synchronous and OSQP becomes a promise. `engine/solve.cljs solve-one` dispatches `:kind :sequential-equal-risk` to it and now threads `on-progress`. `engine/context.cljs` proceeds past an `:invalid` return model when the objective is `:equal-risk`. `engine/target_selection.cljs portfolio-point` carries the `:equal-risk` metadata key. `engine/payload.cljs` pins dust-threshold 0 for equal-risk, attaches `:risk-contributions` (computed from published weights via `risk-contributions/contribution-summary`, quality folded from solver metadata) and `:equal-risk-solver`, nils return-display metrics when the return model was invalid, and adds the `:equal-risk-not-converged` warning when applicable. `application/progress.cljs default-steps` names the solve step "sequential equal-risk" and the frontier step "selected point" for this objective.

The `:risk-contributions` payload section:

    {:method :signed-euler-volatility
     :instrument-ids […]
     :variance-contributions […]          ; w_i·m_i
     :volatility-contributions […]        ; w_i·m_i/σ
     :relative-contributions […]          ; w_i·m_i/q
     :target-relative-contributions […]   ; 1/n each
     :relative-contributions-by-instrument {…}
     :target-relative-contributions-by-instrument {…}
     :sum-relative-contributions x
     :rms-error x
     :max-absolute-error x
     :negative-contribution-count k
     :quality :exact | :approximate | :not-converged}

The `:equal-risk-solver` payload section:

    {:strategy :sequential-equal-risk
     :converged? bool
     :termination-reason :step-tolerance | :objective-improvement | :max-iterations | :line-search-exhausted | :subproblem-failed
     :iterations n-selected-init
     :total-iterations n-all-inits
     :initialization-count k
     :selected-initialization :equal-notional | :inverse-volatility | :inverse-variance | :current-weights
     :objective-value F
     :step-residual ‖d‖∞
     :exactness-tolerance t}

Contracts: `contracts/constants.cljs objective-kinds` + `:equal-risk`; `contracts/specs.cljs` optionally validates the two new payload keys as OPTIONAL map shapes (kept optional so existing fixtures stand); `instrument_keyed_codec.cljs` registers the two new `-by-instrument` maps and the four new enum-valued keys. No schema version bumps; no migrations; persistence untouched (EDN).

UI: as enumerated in Context and Orientation — goal card + summaries + label maps + menu entry + return-forecast-not-used treatment (driven by `(not (history-assumptions/return-required-for-objective? kind))` where a boolean exists, with kind-specific copy where the string names "Minimum risk") + results panel risk-contribution section (per-asset signed share vs 1/n, rms/max error, negative-contribution note, quality badge, realized gross/net/long/short from `:diagnostics`) + frontier section hidden for `:equal-risk`.

Docs: extend the optimizer objectives documentation (docs/design-docs or product-specs — locate the existing objectives doc; if none, add a short `docs/design-docs/optimizer-equal-risk.md` linked from the design-docs index) covering: what Equal Risk optimizes, why exact parity can conflict with gross/net targets, signed contributions (a short position may contribute positively or negatively; hedges preserve negative contributions), how exact/approximate/not-converged is decided, and that expected returns never affect weights.

## Concrete Steps

All commands run from the repository root (this worktree). All of these were run on the final tree and pass:

    npm run setup:worktree        # link node_modules before any gate
    npm run lint:delimiters -- --changed
    npm run check
    npm test
    npm run test:websocket
    npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs \
      --grep "portfolio optimizer setup exposes separate model layers" --workers=1

Final changed/added files. New: domain `risk_contributions.cljs`, `equal_risk.cljs`, `equal_risk_presolve.cljs`, `equal_risk_plan.cljs`; application `engine/equal_risk_solve.cljs`; view `risk_contributions_card.cljs`; doc `docs/design-docs/optimizer-equal-risk.md`; tests `domain/risk_contributions_test.cljs`, `domain/equal_risk_test.cljs`, `application/engine/equal_risk_solve_test.cljs`, `application/engine_equal_risk_test.cljs`, `contracts_equal_risk_test.cljs`, `views/.../results_panel_equal_risk_test.cljs`. Modified: `dev/delimiter_preflight.clj` (tool fix), `docs/design-docs/index.md`, optimizer `actions/{draft,draft_options}.cljs`, `application/engine/{context,payload,solve,target_selection}.cljs`, `application/progress.cljs`, `application/view_model/setup_summary.cljs`, `contracts/{constants,specs}.cljs`, `domain/{constraints,exposure_policy,history_assumptions,objectives}.cljs`, `instrument_keyed_codec.cljs`, views `{format,infeasible_panel,results_panel,return_views_panel,scenario_objective_menu,setup_actions,setup_context,setup_objective_controls}.cljs`, tests `domain/{exposure_policy,objectives}_test.cljs`, `worker_test.cljs`, views `{format,results_panel,scenario_detail_view,setup_view,target_sigma}_test.cljs`, and `tools/playwright/test/portfolio-regressions.spec.mjs`.

## Validation and Acceptance

Acceptance is behavioral. After the change:

1. `npm test` passes, including the new suites. Before the change, an `:equal-risk` request is rejected by the planner as `:unknown-objective` (assert in a test that this is no longer the case).
2. The mathematical battery asserts, among others: diagonal Σ = diag(0.01, 0.04) with G=1, N=1 solves to weights ≈ [2/3, 1/3] with RRCs ≈ [0.5, 0.5] (tolerance 1e-3); the mirrored all-short case (N=−1) gives the negated weights and identical RRCs; a symmetric long/short pair at G=2, N=0 gives w ≈ [1, −1] with equal RRCs; an asymmetric constrained case reports nonzero error and `:approximate`; a hedged construction preserves a negative contribution and RRCs still sum to ≈ 1; analytic gradient matches central differences on several deterministic PSD matrices; a binding cap keeps all hard constraints exact and reports the error; a locked weight is unchanged; each infeasible-exposure construction fails presolve with its specific code; degenerate covariance errors explicitly; permutation / covariance-scale / global sign-flip invariances hold within tolerance; repeated runs return identical weights, diagnostics, and termination metadata; published weights satisfy books/bounds/locks/turnover within 1e-6; an active turnover constraint is honored (asserted via a binding-turnover case).
3. `engine/run-optimization` (sync, quadprog) and `engine/run-optimization-async` (async) return equal weights/diagnostics for the same request.
4. An equal-risk result payload contains `:risk-contributions` (with `:quality`) and `:equal-risk-solver`, satisfies the canonical result specs, and no display-frontier sweep runs (asserted by counting injected-solver invocations and checking `:frontier-summary` source is `:target-solve`).
5. The engine solves `:equal-risk` when the return model is invalid, while `:max-sharpe` remains blocked (existing behavior).
6. Existing minimum-variance/max-sharpe engine, planner, view, and worker tests pass unchanged.
7. `npm run check`, `npm run test:websocket`, `npm run lint:delimiters -- --changed` pass. The focused Playwright flow passes: the goal selector lists "Equal Risk", selecting it updates the goal summary and run-summary "Goal" row.

## Idempotence and Recovery

All steps are additive file edits guarded by tests; re-running any gate is safe. If a gate fails mid-way, fix and re-run the single failing command (`npm run gates` gives the full PASS/FAIL matrix without short-circuiting). No migrations, no persisted-data rewrites: old drafts/scenarios load unchanged because the only contract additions are a new enum value and optional payload keys. To roll back, revert the branch; no external state is touched.
