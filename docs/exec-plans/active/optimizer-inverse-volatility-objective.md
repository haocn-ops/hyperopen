# Add a Risk-weighted sizing (inverse-volatility) optimizer objective

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. It must be maintained in accordance with `.agents/PLANS.md` (repository root).

## Purpose / Big Picture

The portfolio optimizer today offers five goals; the two that need no return forecast are Minimum risk and Equal Risk. Both can hand a deliberately side-locked asset a 0% target: Minimum risk excludes assets the covariance dislikes, and Equal Risk floors any asset whose fixed side makes it a hedge to the rest of the book, because a hedge's signed share of portfolio risk can never equal the positive equal target. A user who fixed every side by conviction ("these long, those short") and only wants sizing help currently has no goal that guarantees every selected asset a position.

This change adds a sixth objective, kind `:inverse-volatility`, labeled **"Risk-weighted sizing"** in the UI. It sizes every asset on its user-chosen side proportionally to the inverse of its own volatility (`|w_i| ∝ 1/σ_i`, where `σ_i` is the square root of the asset's variance on the covariance diagonal), scales the result to the gross exposure target, and then projects onto the feasible region (per-asset caps, locks, turnover budget, gross floor/ceiling) by solving one small quadratic program: "find the feasible portfolio closest to the ideal 1/σ sizing." It deliberately ignores correlations — that is the feature that guarantees inclusion — and never reads expected returns.

After this change, a user on `/portfolio/optimize/draft` can open the Optimization goal panel, expand "More goals", pick "Risk-weighted sizing", run, and see every non-excluded asset receive a nonzero target on its chosen side, with a results card proving the sizing property (each asset's `|weight| × σ` is equal unless a cap or turnover limit bit, and bound rows say which). Additionally, when Equal Risk floors an asset at 0%, the floored badge and the Risk Balance panel now point at this objective as the escape hatch, with a one-click switch-and-rerun.

## Context References

Public refs:
- Direct user/maintainer request (2026-07-16, this session): Equal Risk zeroed the only two long positions (HYPE, BTC side-locked long against a short book) on a spectate wallet; user wants a goal that keeps every selected asset with risk-based sizing and no return estimates. No GitHub issue yet; open one if this lands as a PR.

Repo artifacts:
- `docs/exec-plans/completed/` contains prior Equal Risk plans; the Equal Risk engine surface this plan reuses is canonical in `src/hyperopen/portfolio/optimizer/domain/equal_risk.cljs` and companions (see Context and Orientation).

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-07-16 12:25Z) Research: engine dispatch, Equal Risk plan/presolve/seed machinery, payload sections, every objective-keyed UI surface, action wiring chain, test locations mapped.
- [x] (2026-07-16 12:45Z) ExecPlan authored.
- [x] (2026-07-16 12:55Z) Test contract frozen in Validation and Acceptance (10 acceptance items + edge-case list; switch-and-rerun action shape verified against actions/draft.cljs first).
- [x] (2026-07-16 14:55Z) RED phase: failing tests materialized and verified to fail for the intended reason (68 assertion failures, 0 errors, all in the 18 new deftests; no existing test broken — see Artifacts and Notes).
- [x] (2026-07-16 15:40Z) Milestone 1: engine + contracts green — new `domain/inverse_volatility_plan.cljs` (presolve reuse with objective label, zero-vol screening, ideal 1/σ seed, σ-weighted projection QP), `application/engine/inverse_volatility_payload.cljs` sections, dispatch case, covariance-only gates in context/payload, result-section spec. Suite: 15 failures remaining, all in the milestone 2-4 UI/action tests.
- [x] (2026-07-16 17:20Z) Milestone 2: setup UI (goal card, summaries, option maps, objective menu) green — third secondary card under More goals, option-map entries, menu-key branches, display names, return-free set delegating to the contracts covariance-only set.
- [x] (2026-07-16 17:20Z) Milestone 3: results UI (sizing card, KPI strip, panel gating, diagnostics rails) green — new `view_model/inverse_volatility_results.cljs` + `risk_weighted_sizing_card.cljs`, results-panel `inverse-volatility?` gating (no frontier/refinement/equal-risk rails), KPI neutral deltas + Sizing-deviation tile, verdict/assumptions/provenance covariance-only branches.
- [x] (2026-07-16 17:20Z) Milestone 4: floored-state cross-link (badge copy, Risk Balance footer, switch-and-rerun) green — `switch-portfolio-optimizer-objective-and-run` wired through the six-file chain, floored tooltip extension, Risk Balance footer suggestion pinned present/absent, `:inverse-volatility-zero-volatility-asset` control mapping. Suite: 0 failures, 0 errors (5736 tests / 31678 assertions); lint:hiccup, lint:test, lint:namespace-sizes, test:styles all pass.
- [x] (2026-07-16 19:10Z) Milestone 5 validation: `npm run gates` 34/34 PASS (6456 tests / 35064 assertions); optimizer smoke Playwright spec 11 passed + 3 failed with the 3 failures verified PRE-EXISTING via git-stash A/B on the same :8090 static build (objective-menu count/containment + add-asset containment specs — red on main-state code too, not this feature); live browser QA on the :8090 worktree build in spectate mode: goal card selectable under More goals, draft persistence round-trips the kind, 14-asset run holds every asset on its side at equal |w|·σ (8.5% each, 0.0% deviation, "Deterministic · closed form + projection"), Equal Risk on the same book floors BTC (offsets, −0.57 corr) and renders the Risk Balance cross-link, one click switches + reruns onto the sizing card. Screenshots in session scratchpad qa-shots/.
- [ ] Maintainer review of the diff + commit/PR (multi-agent reviewer pass ran 2026-07-16; user sign-off pending).

## Surprises & Discoveries

- Observation: the Equal Risk machinery already contains almost the entire engine for this objective: `seed-weights :inverse-volatility` builds the ideal sizing, `project-book-magnitudes` handles bounded books, `equal-risk-presolve/presolve` screens exactly the feasibility conditions this objective needs (fixed sides, gross sanity, capacity, covariance validity), and `equal-risk-plan/projection-problem` is literally "closest feasible portfolio to a seed" as a QP.
  Evidence: `src/hyperopen/portfolio/optimizer/domain/equal_risk.cljs:159-226`, `domain/equal_risk_presolve.cljs:125-168`, `domain/equal_risk_plan.cljs:60-69`.
- Observation: `solve-one` (`application/engine/solve.cljs:19-22`) routes any problem whose `:kind` is not special-cased to the injected QP adapter, so the new objective needs no solver-dispatch change at all — its planned problem is a plain `:quadratic-program`.
- Observation: the run input signature (`contracts/signatures.cljs:40-46`) already includes `:objective`, so the stale-execution gate covers the new kind with zero extra wiring.
- Observation: dust removal must be disabled for this objective exactly as it is for Equal Risk (`application/engine/payload.cljs:36-50`), or tiny 1/σ positions would be zeroed and break the "keeps every asset" promise.
- Observation: the planned `:seed-weights` must be the UNCLIPPED ideal 1/σ sizing, not `equal-risk/seed-weights` — that helper pre-projects magnitudes into the per-asset bounds, so a capped asset's published weight equals its clipped seed and the `:moved-off-seed?` flag can never fire. The QP owns all feasibility; the ideal seed is the comparison baseline.
  Evidence: `inverse-volatility-binding-cap-pins-and-is-flagged-test` failed at the flag assertion until `ideal-seed` replaced `seed-weights` in `domain/inverse_volatility_plan.cljs`.

## Decision Log

- Decision: reuse `equal-risk-presolve/presolve` verbatim, including its `:equal-risk-*` violation codes, and only generalize the user-facing `:message` strings (the word "Equal Risk" becomes a caller-supplied objective label).
  Rationale: `infeasible_panel.cljs` maps those exact codes to setup controls; new codes would need parallel wiring for identical semantics. Codes are developer-facing; messages are user-facing. One new code is added (zero-volatility asset) because Equal Risk has no such failure.
  Date/Author: 2026-07-16, Claude (session with Barry).
- Decision: the engine strategy is "seed then one projection QP", not a closed-form clip.
  Rationale: caps, locks, gross floor/ceiling, and the turnover L1 budget all live in the QP row encoding already; a single `minimize ||w − seed||²` over that region honors every constraint uniformly and reuses `projection-problem`. When nothing binds, the QP returns the seed itself. The deterministic bisection projection alone cannot honor turnover coupling.
  Date/Author: 2026-07-16, Claude.
- Decision: UI label "Risk-weighted sizing"; placement is a third secondary card under "More goals", not a fourth primary card.
  Rationale: the three primary cards read as a defensive→return-seeking spectrum in a tuned container grid; this objective is a specialist tool discovered chiefly through the floored-state cross-link, which this plan also builds.
  Date/Author: 2026-07-16, Claude (user approved this design in session).
- Decision: introduce `covariance-only-objective-kinds` `#{:equal-risk :inverse-volatility}` in `contracts/constants.cljs` and use it at every site that currently hardcodes `= :equal-risk` for "does not consume returns" semantics (engine context return-model allowance, payload dust special-case, setup summary return-free set, results panel views-editor gate, KPI-strip neutral deltas, assumptions-strip "analytics only").
  Rationale: six scattered hardcodes would otherwise become twelve; the memory of this repo shows objective-keyed `case` misses are its classic regression.
  Date/Author: 2026-07-16, Claude.
- Decision: the projection QP is σ-WEIGHTED — `minimize 0.5·(w−seed)ᵀ·diag(σ/mean σ)·(w−seed)` — not the identity L2 projection the plan first sketched, and the seed rides the planned problem as `:seed-weights` (the RED plan-shape test was amended accordingly; the behavioral acceptance items are unchanged).
  Rationale: with the gross equality active, identity projection redistributes a capped asset's excess as a UNIFORM shift across free assets, which breaks the `|w|·σ` parity that acceptance item 3 demands; the σ-weighted metric's KKT conditions give `|w_i|·σ_i = constant` for every free asset (water-filling), so re-equalization is exact rather than approximate. Gross floor/ceiling rows are also omitted from the QP: presolve already rejects targets outside `[floor, ceiling]` and the signed-gross equality pins realized gross exactly, so the rows would be redundant.
  Date/Author: 2026-07-16, Claude.
- Decision: the floored cross-link gets a new `switch-portfolio-optimizer-objective-and-run` action instead of reusing `apply-portfolio-optimizer-objective-menu-selection-and-run`.
  Rationale: verified 2026-07-16 — the existing action takes no argument and reads the pending selection from `ui-objective-menu-selection-path`; chaining a set-selection dispatch before it is unsafe because the first action's save effects are not projected into the second action's state.
  Date/Author: 2026-07-16, Claude.
- Decision: the results centerpiece for this objective is a new sizing-fidelity card, not the Risk Contribution Balance card. Signed risk contributions may be added later as a diagnostic tab; they are out of scope here.
  Rationale: the objective's promise is `|w|·σ` equality, so that is what the card must prove. Reusing the equal-risk card would drag in target/quality chrome that does not apply.
  Date/Author: 2026-07-16, Claude.

- Decision: the objective-menu row for Risk-weighted sizing renders BY EXCEPTION — only while it is the current or pending objective — instead of always joining the five mainstream options.
  Rationale: two existing pins (`scenario_detail_view_test` option-role equality, `target_sigma_test` five-wrapper count) freeze the always-rendered menu at the five mainstream goals, and this plan's own placement decision already frames the objective as a specialist tool discovered through More goals and the floored cross-link. The conditional row keeps the menu's label and checkmark honest for a selected inverse-volatility scenario (`objective-label` resolves from the full option list either way) without expanding the quick-switch list or bending tests.
  Date/Author: 2026-07-16, Claude (GREEN implementer).
- Decision: `setup-summary/return-free-objective-kinds` DELEGATES as `(conj covariance-only-objective-kinds :minimum-variance)` rather than becoming the bare contracts set, and the results-panel views-editor gate keeps reading this delegated set.
  Rationale: Minimum risk is return-free in the UI sense (Run summary "Not used", views editor suppressed) but deliberately absent from the contracts covariance-only set (it is not engine-exempt from return-model validity); replacing the set outright would have newly activated the results views editor for Minimum risk + Black-Litterman and changed its Run-summary copy. The delegated superset covers `:inverse-volatility` at every existing consumer with zero behavior change for the other kinds.
  Date/Author: 2026-07-16, Claude (GREEN implementer).
- Decision: the `:objective-body` verdict copy lives in the new `view_model/inverse_volatility_results.cljs` (`verdict-body`, mirroring `equal-risk-results/verdict-body`) and is wired at the existing `scenario_detail_view.cljs` or-site, not in `results_summary.cljs`.
  Rationale: the Equal Risk precedent keeps verdict copy in the objective's view-model namespace; results-summary only consumes `:objective-body`.
  Date/Author: 2026-07-16, Claude (GREEN implementer).
- Decision: `:inverse-volatility-zero-volatility-asset` maps to `#{:blocklist}` in `infeasible_panel.cljs` (new "Excluded Assets" control label).
  Rationale: the violation's remedy is excluding the zero-volatility asset (or extending its history), not adjusting any exposure/cap control the existing keys point at.
  Date/Author: 2026-07-16, Claude (GREEN implementer).
- Decision: the sizing card reuses the risk-balance card's CSS grammar (shell, header, row grid, legend, reading note) but its body deliberately does NOT use `.optimizer-risk-balance-panel` — that class is display-gated by the sibling card's tab radios (`:has()` CSS) and the sizing card has no tabs. The "Why this target" context card stays rendered for this objective (engine facts incl. limits hit); only frontier machinery and equal-risk rails are gated off.
  Date/Author: 2026-07-16, Claude (GREEN implementer).
- Decision: review-fix pass applied after the multi-agent reviewer verified findings (see Artifacts and Notes for evidence). (a) Every remaining objective-keyed covariance-only site now derives from `contracts-constants/covariance-only-objective-kinds` instead of `= :equal-risk` / `(or equal-risk? inverse-volatility?)` hardcodes: the return-views panel `return-free?` gate (now `setup-summary/return-free-objective-kinds`) and its note label (per-objective case), the exposure-map view-model net-output gating (with an objective-aware net-output copy so Risk-weighted sizing is never described as "determined by Equal Risk"), the exposure-point action's preserve-net-policy gate, the results-panel `frontier?` gate, and the KPI-strip `covariance-only?` (now read from `[:solver :objective-kind]`). (b) Progress rows are honest for the new kind: solve reads "risk-weighted sizing · projection QP" and the frontier row keys "target selection · selected point" on covariance-only membership — never "frontier sweep · 40 points" for a single deterministic QP. (c) The plan's promised "turnover-limited" tag now exists end-to-end: the payload flags FREE rows more than 1% (relative) off the free-set parity as `:turnover-limited?` (only the turnover L1 budget can displace an unpinned row) and publishes `:ideal-risk-weight` so the view-model stops recomputing it; the sizing card renders the tag beside "capped". (d) Reuse cleanups: `equal-risk-plan/gross-coefficients` is public (two consumers documented), the projection problem carries `:sigmas` so the payload never recomputes them from the covariance diagonal, and the objective menu filters `:specialist? true` options generically instead of hardcoding `:inverse-volatility` twice. `format-multiple`/`format-pct` deliberately stay local in the view-model/card (the namespace-boundary lint forbids non-view imports of `hyperopen.views.*`, and the shared helpers' options-map signature + "N/A" fallback differ from the card's positional-decimals + "—" grammar).
  Date/Author: 2026-07-16, Claude (review-fix worker).

## Outcomes & Retrospective

2026-07-16 (feature complete, pending maintainer sign-off): the objective works end-to-end — engine, setup, results, and the floored-state escape hatch — with the full suite, all 34 gates, styles/hiccup/namespace lints, and live browser QA green. Against the original purpose: the user's exact pain (side-locked longs zeroed by Equal Risk) now has a one-click recovery that was verified live, and the sizing card proves the |w|·σ law rather than asserting it. Complexity: net INCREASE, but bounded and mostly additive — one new objective kind, two new engine namespaces, one new card — while two pieces of shared infrastructure got simpler (the `covariance-only-objective-kinds` set replaced six scattered `= :equal-risk` hardcodes, and presolve grew one label parameter instead of a forked copy). The riskiest design judgment (σ-weighted projection over identity L2) was forced by the acceptance tests themselves, which is the RED-first process working as intended. Remaining: maintainer review, commit, and optionally a committed Playwright spec for the cross-link journey (currently covered by unit tests + a throwaway QA driver).

Notable QA finding, not a defect: on a small book (2 longs vs 4 shorts) Equal Risk legitimately finds the big-long equilibrium (BTC 68.8%, all contributions +16.7%, zero floored assets), so the cross-link correctly does NOT render there; the floored state (and the cross-link) reproduces from ~12 shorts up, matching the theory that hedge-flooring dominates as the short book grows.

## Context and Orientation

The optimizer lives under `src/hyperopen/portfolio/optimizer/` (engine, domain math, contracts, actions) and `src/hyperopen/views/portfolio/optimize/` (Replicant UI). All files are ClojureScript. Terms used below:

- **Objective kind**: the keyword under `[:objective :kind]` on an optimizer run request. The allowed set is `objective-kinds` in `src/hyperopen/portfolio/optimizer/contracts/constants.cljs:17-18`, currently five kinds; specs validate against it (`contracts/specs.cljs:130-137`), and draft persistence checks it (`actions/draft_persistence.cljs:96`).
- **Encoded constraints**: the output of `encode-constraints` in `src/hyperopen/portfolio/optimizer/domain/constraints.cljs` — per-asset `:lower-bounds`/`:upper-bounds` (a side-locked long is `[0, cap]`, a short `[-cap, 0]`, a locked holding `[w, w]`), gross floor/ceiling, turnover, locks, `:exposure-targets`.
- **Book**: the side an asset's encoded bounds pin it to. `book-of-bounds`/`book-split` in `domain/equal_risk.cljs:96-114` classify each index as `:long`, `:short`, or `:two-sided`.
- **Gross target**: the single signed-gross equality Equal Risk sizes into — `sum(long weights) + sum(|short weights|) = G` — derived by `exposure-targets` (`domain/equal_risk.cljs:87-94`).
- **Seed**: `seed-weights` (`domain/equal_risk.cljs:207-226`) builds a signed weight vector for a named initializer kind; the `:inverse-volatility` kind (lines 166-172) sets magnitudes proportional to `1/sqrt(covariance[i][i])`, scaled to the gross target, then projects into per-asset magnitude bounds with `project-book-magnitudes` (a deterministic bisection). It returns nil if any asset variance is not positive.
- **Presolve**: `presolve` in `domain/equal_risk_presolve.cljs:125-168` screens covariance validity, fixed sides (rejects `:two-sided` books and non-shortable shorts), gross-target sanity, and aggregate magnitude capacity, returning `{:status :ok :targets {:gross} :books ...}` or `{:status :infeasible :violations [...]}` with coded, user-facing messages.
- **Projection QP**: `projection-problem` in `domain/equal_risk_plan.cljs:60-69` wraps a template region (`subproblem-template`, lines 40-58: one signed-gross equality, bounds, turnover L1 row) into a QP that minimizes `0.5·||y − seed||²`. Solver plans are built in `build-solver-plan` (`domain/objectives.cljs:474-513`), and `solve-one` (`application/engine/solve.cljs:11-22`) sends any `:quadratic-program` problem to the injected QP adapter.
- **Result payload**: assembled in `application/engine/payload.cljs` (dust threshold at 23-50; equal-risk-only sections merged at 57-62 and 339-346 from `application/engine/equal_risk_payload.cljs`), validated per-point by `application/engine/target_selection.cljs`.
- **Floored badge**: the results Allocation table (`views/portfolio/optimize/target_exposure_table.cljs:308-319`) renders a "floored" chip when a target sits pinned at a zero bound (`binding-kind :floored`, computed in `application/view_model/rebalance.cljs:144-155`), with a tooltip explaining the side setting blocked the direction.

UI surfaces that branch on objective kind (all must gain a branch or be verified unaffected — see Plan of Work): option maps in `actions/draft_options.cljs` (`objective-models` 3-10, `setup-presets` 34-42, `objective-menu-options` 44-54, `numeric-objective-parameter-keys` 75-77); menu-key mapping in `actions/draft.cljs:25-40` and `views/portfolio/optimize/scenario_objective_menu.cljs:16-45`; goal picker `views/portfolio/optimize/setup_objective_controls.cljs` (`goal-summary` 8-17, secondary cards 88-121); display names in `application/view_model/setup_summary.cljs:63-76` and `views/portfolio/optimize/format.cljs:104-111`; results gating in `views/portfolio/optimize/results_panel.cljs:28-30,57,92-143`, `scenario_kpi_strip.cljs:148-186`, `results_summary.cljs:126,283,314-343,374-393`, `results_diagnostics_rail.cljs:379-389,480-522`, `scenario_detail_view.cljs:207-238,342-343`, `setup_context.cljs:222-235,319`; infeasible codes in `views/portfolio/optimize/infeasible_panel.cljs:5-19`; engine return-model allowance in `application/engine/context.cljs:358-371`; progress labels in `application/progress.cljs:42-96`.

Action wiring for any new or reused action follows a six-file chain (impl namespace under `actions/`, re-export in `actions.cljs`, `runtime_catalog.cljs`, `action_adapters.cljs`, `action_args.cljs` schema, `runtime_registration/portfolio.cljs`). The existing action `apply-portfolio-optimizer-objective-menu-selection-and-run` (`actions/draft.cljs:412-430`) already implements "save objective choice, then rerun" and is the intended vehicle for the floored cross-link. A repo gotcha: an undeclared alias in an actions namespace compiles green and fails only at runtime — verify aliases by running the wiring tests.

Validation gates for any code change: `npm run gates` (runs `npm run check`, `npm test`, `npm run test:websocket` with a PASS/FAIL matrix). A fresh worktree must first run `npm run setup:worktree`. Browser verification uses Playwright specs under `tools/playwright/test/` (smoke tag: `npm run test:playwright:smoke`).

## Plan of Work

Milestone 1 — engine and contracts. Add `:inverse-volatility` to `objective-kinds` and add `covariance-only-objective-kinds` `#{:equal-risk :inverse-volatility}` in `contracts/constants.cljs`. Create `src/hyperopen/portfolio/optimizer/domain/inverse_volatility_plan.cljs` with `build-plan`: run `equal-risk-presolve/presolve` (message label generalized — add an optional label argument defaulting to "Equal Risk" so existing callers are unchanged); on `:ok`, build the seed with `equal-risk/seed-weights :inverse-volatility`; if the seed is nil (a non-positive variance), return `{:status :infeasible}` with a new violation code `:inverse-volatility-zero-volatility-asset` listing the offending instrument ids; otherwise emit `{:status :ok :strategy :inverse-volatility :selection-objective {:kind :inverse-volatility} :problems [p]}` where `p` is `projection-problem` of a template built like `subproblem-template` but with `:objective-kind :inverse-volatility`, augmented with the gross-floor inequality and gross-ceiling L1 row exactly as `plan-problem` does (`domain/equal_risk_plan.cljs:111-143`), plus `:seed-weights` carried on the problem for the payload. Add the `:inverse-volatility` case to `build-solver-plan` in `domain/objectives.cljs` (no display frontier: the existing `case` default already yields nil). In `application/engine/context.cljs:361-362` and `application/engine/payload.cljs:36-50`, replace the `= :equal-risk` checks with membership in `covariance-only-objective-kinds`. Add an `inverse-volatility-sections` builder (new namespace `application/engine/inverse_volatility_payload.cljs`) producing an `:inverse-volatility` payload section: per-asset rows of `{:instrument-id :weight :sigma :risk-weight}` where `:risk-weight` is `|w_i|·σ_i`, the seed weights, and the max relative sizing deviation from equality among unbound assets; merge it in `payload.cljs` beside the equal-risk merge. Extend `contracts/specs.cljs` with the optional result section mirroring the equal-risk section pattern (lines 359-373). Optionally add a friendly `default-steps` label branch in `application/progress.cljs` ("risk-weighted sizing / projection").

Milestone 2 — setup UI and option plumbing. Add the kind to the four maps in `actions/draft_options.cljs`; add the menu-key branch in `actions/draft.cljs:25-40` and the card entry plus key mapping in `scenario_objective_menu.cljs:16-45`; add display names in `setup_summary.cljs:63-71` (and the kind to `return-free-objective-kinds` 73-76, now delegating to the contracts set) and `format.cljs:104-111`; add the `goal-summary` line and a third `secondary-goal-card` "Risk-weighted sizing" / "Size by each asset's own volatility · keeps every asset · ignores correlations" dispatching `[:actions/set-portfolio-optimizer-objective-kind :inverse-volatility]` in `setup_objective_controls.cljs` (no parameter block); extend the `return-free-label` case in `setup_context.cljs:233-235`.

Milestone 3 — results UI. New `views/portfolio/optimize/risk_weighted_sizing_card.cljs` rendering the `:inverse-volatility` payload section: a bar per asset of `|w|·σ` against the equal ideal, side glyph, and a "capped" / "turnover-limited" tag on rows where the projection moved the weight off the seed by more than the feasibility tolerance; header status reads "Deterministic · closed form + projection"; a reading-note footer states the objective sizes by standalone volatility and ignores correlations. New view-model namespace `application/view_model/inverse_volatility_results.cljs` shapes the card model from the payload. In `results_panel.cljs`, add an `inverse-volatility?` gate rendering this card in the risk-contributions slot, no frontier chart, no equal-risk rails; the views editor gate (28-30) now keys on the contracts covariance-only set. In `scenario_kpi_strip.cljs`, widen the `equal-risk?` neutral-delta gates (148-186) to covariance-only and add a status tile for this kind (max sizing deviation instead of risk-balance quality). In `results_summary.cljs`, supply the `:objective-body` verdict copy and widen the assumptions-strip "analytics only" branch (387). In `results_diagnostics_rail.cljs`, keep the generic trust rail; the frontier-only confidence rails (480-522) must not render for this kind. Extend `scenario_detail_view.cljs` label/returns-field branches (207-238).

Milestone 4 — floored-state cross-link. In `target_exposure_table.cljs:308-319`, extend the floored tooltip with "To keep every asset with a risk-based size, try Risk-weighted sizing in Optimization goal." In `risk_contributions_card.cljs`, when the balance model reports one or more floored/zero-target side-locked assets, append a footer line to the reading-note with an inline button dispatching a new thin action `switch-portfolio-optimizer-objective-and-run` (takes the objective menu-option key, saves the objective from `draft-options/objective-menu-options`, projects the save effects, and reruns via `run-actions/run-portfolio-optimizer-from-draft`, modeled directly on `apply-portfolio-optimizer-objective-menu-selection-and-run` at `actions/draft.cljs:412-430` — which cannot be reused because it reads the selection from UI state rather than an argument). The action follows the six-file wiring chain. Add `:inverse-volatility-zero-volatility-asset` to `violation-control-keys` in `infeasible_panel.cljs:5-19`.

Milestone 5 — validation. Run `npm run gates`; run the smallest relevant Playwright spec first (the optimizer smoke route spec covering goal cards), broaden after it passes; browser QA on `http://localhost:8081/portfolio/optimize/draft` with the spectate wallet from the user request, verifying the full journey: Equal Risk run showing floored longs → cross-link visible → one click switches and reruns → every asset nonzero on its side → sizing card proves `|w|·σ` equality. Update this plan's living sections; move to `docs/exec-plans/completed/` after acceptance.

## Concrete Steps

All commands run from the repository root (the worktree checkout).

    npm run setup:worktree        # once per fresh worktree
    npm run gates                 # full matrix: check + test + test:websocket
    npm test                      # cljs unit tests only (faster loop)
    npm run test:playwright:smoke # after UI milestones

During RED phase, run `npm test` and confirm the new tests fail with assertion errors that name the missing `:inverse-volatility` behavior (not compile errors from typos). During implementation, re-run per milestone.

## Validation and Acceptance

Test contract FROZEN 2026-07-16 (items 1-10 below plus the edge-case list; item 10 targets the new `switch-portfolio-optimizer-objective-and-run` action per the Decision Log). Acceptance coverage:

1. Plan shape (new `test/hyperopen/portfolio/optimizer/inverse_volatility_plan_test.cljs`): `build-solver-plan` with kind `:inverse-volatility`, three long + two short assets, distinct variances, gross target 1.5 → one `:quadratic-program` problem; its linear term equals the negated seed; seed magnitudes are proportional to `1/σ` within each book and sum to 1.5; every asset's seed magnitude is strictly positive.
2. End-to-end solve (engine test with the real QP adapter): every asset's published target weight is nonzero with the requested sign; `|w_i|·σ_i` equal across unbound assets within tolerance; the gross equality holds; a run with `:dust-usdc` set still publishes every position (dust disabled).
3. Binding caps: cap one asset below its ideal magnitude → it pins at the cap, remaining assets re-equalize `|w|·σ`, and the payload flags that row as moved-off-seed.
4. Zero-volatility asset → `{:status :infeasible}` with `:inverse-volatility-zero-volatility-asset` naming the instrument.
5. Fixed-side screening: a two-sided asset produces the presolve violation whose message names "Risk-weighted sizing", not "Equal Risk".
6. Covariance-only semantics: an `:invalid` return-model result does not block the solve (mirrors the equal-risk allowance test).
7. Signature: changing kind `:equal-risk` → `:inverse-volatility` changes `optimizer-input-signature` (stale gate).
8. Setup view (`setup_view_test.cljs` additions): the new secondary card renders under More goals and dispatches `set-portfolio-optimizer-objective-kind :inverse-volatility`; `goal-summary` renders the new line.
9. Results view: with an `:inverse-volatility` solved payload, the sizing card renders, the frontier chart and equal-risk rails do not, the KPI strip shows neutral deltas and no Exact/Approximate label.
10. Cross-link: an equal-risk balance model containing a floored side-locked asset renders the suggestion button; `switch-portfolio-optimizer-objective-and-run` saves the new objective to the draft path and emits the run-from-draft effects.

Edge-case proposals: turnover budget tight enough to bind (projection deviates from seed; deviations surfaced, run still `:solved`); a locked holding (bounds `[w,w]`) is preserved exactly; long-only mode uses the legacy gross default of 1; sparse-history runtime caps compose with the projection; all 38 assets one-sided short (no long book) still solves.

Acceptance is behavioral: after Milestone 5, on the draft route with the session's spectate wallet, selecting Risk-weighted sizing and running yields a target column with no `-0.0%` side-locked zeros, and the two previously-floored longs (HYPE, BTC) hold nonzero long targets.

## Idempotence and Recovery

All edits are additive (a new kind, new namespaces, new card) plus small widenings of existing gates; re-running tests and gates is always safe. If a milestone must be abandoned mid-way, the new kind is inert as long as it is absent from `objective-kinds` — revert that one line to disable the feature wholesale. No data migrations: drafts persist the objective kind as-is and old drafts never contain the new kind.

## Artifacts and Notes

RED phase (2026-07-16 14:55Z). Test files materialized against EXISTING public
surfaces only (no require of the not-yet-existing plan/payload namespaces):

- `test/hyperopen/portfolio/optimizer/inverse_volatility_plan_test.cljs` (items 1, 4, 5 via `objectives/build-solver-plan`)
- `test/hyperopen/portfolio/optimizer/application/engine_inverse_volatility_test.cljs` (items 2, 3, 6 on the real quadprog adapter)
- `test/hyperopen/portfolio/optimizer/contracts_inverse_volatility_test.cljs` (constants + draft/engine-request specs + item 7 signature)
- `test/hyperopen/portfolio/optimizer/switch_objective_actions_test.cljs` (item 10 action; `exists?`-guarded so it fails as an assertion, not a runtime error)
- `test/hyperopen/views/portfolio/optimize/setup_view_inverse_volatility_test.cljs` (item 8; separate ns because `setup_view_test.cljs` sits at 458/500 lines against the size gate)
- `test/hyperopen/views/portfolio/optimize/results_panel_inverse_volatility_test.cljs` (item 9 + item 10 cross-link button)
- extended: `draft_model_actions_test.cljs` (objective-kind persists), `runtime_catalog_test.cljs` (catalog exposes the switch action)

`npm test` transcript excerpt (suite: 5736 tests / 31646 assertions, 68 failures
0 errors — all 68 in the new deftests; base suite green):

    FAIL in (inverse-volatility-plans-one-projection-qp-with-negated-seed-linear-test)
      :inverse-volatility must plan, not report {:status :infeasible, :reason :unknown-objective}
      expected: (= :ok (:status plan))   actual: (not (= :ok :infeasible))          [18 failures]
    FAIL in (inverse-volatility-zero-volatility-asset-is-a-named-infeasibility-test) [3 failures]
    FAIL in (inverse-volatility-presolve-messages-name-risk-weighted-sizing-test)    [3 failures]
    FAIL in (inverse-volatility-solves-end-to-end-with-sizing-payload-test)
      {:status :infeasible, :reason :unknown-objective, ...}                        [12 failures]
    FAIL in (inverse-volatility-disables-dust-removal-test)                          [5 failures]
    FAIL in (inverse-volatility-binding-cap-pins-and-is-flagged-test)                [6 failures]
    FAIL in (inverse-volatility-proceeds-when-return-model-is-invalid-test)
      {:status :infeasible, :reason :invalid-return-model} (allowance still = :equal-risk) [3 failures]
    FAIL in (inverse-volatility-objective-is-a-canonical-kind-test)                  [1 failure]
    FAIL in (draft-with-inverse-volatility-objective-validates-and-migrates-test)    [1 failure]
    FAIL in (engine-request-with-inverse-volatility-objective-validates-test)        [1 failure]
    FAIL in (set-objective-kind-persists-inverse-volatility-test) (returns [])       [1 failure]
    FAIL in (more-goals-offers-risk-weighted-sizing-secondary-card-test)             [4 failures]
    FAIL in (selected-risk-weighted-sizing-shows-summary-and-pressed-card-test)      [2 failures]
    FAIL in (results-panel-inverse-volatility-shows-sizing-card-and-hides-frontier-test) [2 failures]
    FAIL in (kpi-strip-inverse-volatility-neutral-deltas-and-no-quality-label-test)  [2 failures]
    FAIL in (risk-contributions-card-offers-risk-weighted-sizing-for-floored-assets-test) [2 failures]
    FAIL in (optimizer-catalog-exposes-switch-objective-and-run-test)                [1 failure]
    FAIL in (switch-objective-and-run-saves-objective-and-emits-run-effects-test)    [1 failure]

RED-phase contract clarifications frozen by the tests (for the GREEN implementer):

- Item 3 "payload flags that row as moved-off-seed" is frozen as a row-level
  `:moved-off-seed?` boolean on the `:sizing-rows` entries.
- Item 2/3 "|w|·σ equal within tolerance" is asserted via the rows'
  `:risk-weight` at a 5% relative band — loose enough for the feasible
  projection's uniform-shift redistribution, far tighter than any
  capped/uncapped gap.
- Item 7's signature test passes TODAY by construction (the signature already
  includes `:objective`, per Surprises); it rides as a regression pin, not a
  RED failure.
- The item 10 action takes the menu-option key `:inverse-volatility` and the
  floored cross-link test derives "floored" from a zero-bound
  `:binding-constraints` entry plus a zero target weight.

GREEN phase, milestones 2-4 (2026-07-16 17:20Z). `npm test`: 5736 tests /
31678 assertions, 0 failures, 0 errors. `npm run lint:hiccup`, `npm run
lint:test`, `npm run lint:namespace-sizes`, `npm run test:styles`: all pass.
Namespace-size exceptions bumped with reasons for `action_args.cljs` (735),
`actions/draft.cljs` (980), `contracts/specs.cljs` (530), and a NEW entry for
`application/engine/payload.cljs` (525 — milestone 1 growth had left the gate
red with a missing-size-exception). Two pre-existing menu pins
(`scenario_detail_view_test`, `target_sigma_test`) forced the by-exception
menu-row decision recorded in the Decision Log; no test file was modified.

Files changed in GREEN (milestones 2-4): `actions/draft_options.cljs`,
`actions/draft.cljs` (menu-key branch + new action), `actions.cljs`,
`runtime_catalog.cljs`, `runtime/action_adapters.cljs`,
`schema/contracts/action_args.cljs`, `schema/runtime_registration/portfolio.cljs`,
`application/view_model/setup_summary.cljs`,
`application/view_model/equal_risk_results.cljs` (`floored-instrument-ids`),
NEW `application/view_model/inverse_volatility_results.cljs`,
`views/portfolio/optimize/{scenario_objective_menu,setup_objective_controls,
setup_context,format,results_panel,scenario_kpi_strip,results_summary,
scenario_detail_view,risk_contributions_card,target_exposure_table,
infeasible_panel}.cljs`, NEW `views/portfolio/optimize/risk_weighted_sizing_card.cljs`,
`dev/namespace_size_exceptions.edn`.

Review-fix pass (2026-07-16, post-reviewer). Files changed:
`views/portfolio/optimize/{return_views_panel,results_panel,scenario_kpi_strip,
scenario_objective_menu,risk_weighted_sizing_card}.cljs`,
`application/view_model/{exposure,inverse_volatility_results}.cljs`,
`actions/exposure.cljs`, `application/progress.cljs`,
`application/engine/{inverse_volatility_payload,payload}.cljs`,
`domain/{equal_risk_plan,inverse_volatility_plan}.cljs`,
`dev/namespace_size_exceptions.edn` (return_views_panel 505 > 500 after the
honest-gating additions), plus test extensions in
`application/progress_test.cljs` (covariance-only solve/frontier label pins for
both kinds) and `application/engine_inverse_volatility_test.cljs` (new
binding-turnover deftest: at 1.0x gross the fixture book needs 0.4 one-sided
turnover for bare feasibility and ~0.65 to reach the 1/σ seed, so
`:max-turnover 0.5` binds without infeasibility — status `:solved`, a
`:turnover-limited?` row, zero `:moved-off-seed?` rows, positive
`:max-sizing-deviation`; the original 0.3 suggestion is infeasible against the
signed-gross equality at 2.0x gross, where the minimum feasible turnover is
0.9 and the seed itself needs only ~0.9005, leaving no room to displace).
No existing assertion weakened. `npm test`: 5738 tests / 31689 assertions,
0 failures, 0 errors; `lint:hiccup`, `lint:test`, `lint:namespace-sizes`,
`lint:namespace-boundaries`, `test:styles` all pass.

## Interfaces and Dependencies

In `src/hyperopen/portfolio/optimizer/domain/inverse_volatility_plan.cljs`, define:

    (defn build-plan
      "Solver plan for :inverse-volatility: presolve fixed-side/gross/covariance
      feasibility, build the 1/σ seed, and plan ONE projection QP (closest
      feasible portfolio to the seed). Expected returns are never consumed."
      [{:keys [instrument-ids covariance encoded-constraints]}] ...)
      ;; returns {:status :ok :strategy :inverse-volatility
      ;;          :selection-objective {:kind :inverse-volatility}
      ;;          :problems [qp]}  or  {:status :infeasible :reason ... :details ...}

In `src/hyperopen/portfolio/optimizer/application/engine/inverse_volatility_payload.cljs`, define `inverse-volatility-sections` returning `{:inverse-volatility {:sizing-rows [...] :seed-weights [...] :max-sizing-deviation ...}}` from the solver result plus covariance. In `contracts/constants.cljs`, define `covariance-only-objective-kinds`. `equal-risk-presolve/presolve` gains an optional trailing options map `{:objective-label "..."}` defaulting to current behavior. No public API changes elsewhere; every other edit is a new branch in an existing `case`/`cond` or a new UI namespace.

---

Revision note (2026-07-16, initial): plan authored from live-session research; design decisions (secondary card placement, projection-QP strategy, covariance-only set, presolve reuse) captured in the Decision Log with the user's in-session approval of the UX direction.
