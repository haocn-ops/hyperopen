# Optimizer Equal Risk results redesign (risk-contribution balance chart)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. Maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The Equal Risk objective shipped with a plain table as its results centerpiece. This pass makes the results page present the quantity the objective actually optimized: each position's SIGNED share of portfolio volatility versus the equal `1/n` target. After this change, an Equal Risk run at `/portfolio/optimize/draft` (Recommendation tab) shows a horizontal diverging risk-contribution chart (signed axis through zero, recommended bar per asset, muted current marker, dashed equal-target line, per-row deviation in points), an objective-specific KPI tile (Risk balance: current → recommended max deviation), objective-specific recommendation copy (including the "constraint-determined" case where gross/net targets pin every weight), a "Why this risk allocation" card (book shape, equal target, largest risk contributor, allocation freedom), and an Equal-Risk-specific confidence rail (fit, allocation freedom, solution stability, real solver stop reasons) — replacing the frontier-speak (frontier quality, draft point budget) that currently leaks onto Equal Risk results. A collapsed "Risk / Return Context" disclosure provides the vol/return scatter (current, recommended, per-asset standalone points) with an explicit note that expected returns did not affect the weights. No efficient frontier is computed or implied.

## Context References

Public refs:
- Direct user request (2026-07-11) with a design mockup and detailed implementer commentary; the user delegated final design authority to this plan.

Repo artifacts:
- Parent ExecPlan: `/hyperopen/docs/exec-plans/active/2026-07-10-optimizer-equal-risk-objective.md` (the objective itself; shipped).
- `/hyperopen/src/hyperopen/portfolio/optimizer/BOUNDARY.md`.

Local scratch refs (non-authoritative): none.

## Progress

- [x] (2026-07-11) Read the mockup + commentary; audited every component in scope: `results_panel.cljs`, `risk_contributions_card.cljs`, `results_summary.cljs` (verdict, target-context, assumptions strip), `results_diagnostics_rail.cljs` (confidence + trust rails), `scenario_detail_view.cljs` (KPI strip, provenance strip; 580-line exception cap, 23 lines headroom), `target_exposure_table.cljs` (498/500 lines), `frontier_chart_model.cljs` (SVG color conventions).
- [x] (2026-07-11) Wrote this ExecPlan with the design rulings below.
- [x] (2026-07-11) Payload: `:current-risk-contributions` (nil-safe summary from CURRENT weights) and `:equal-risk-solver :allocation-freedom` (fully-determined / limited / open + binding count + per-book members + free degrees), extracted to `application/engine/equal_risk_payload.cljs` (payload.cljs was at its size cap); optional spec predicates; engine tests assert both plus :fully-determined on the one-long/one-short book.
- [x] (2026-07-11) New pure view-model `application/view_model/equal_risk_results.cljs` (balance model with 16-row cap + remainder, KPI values, verdict copy incl. constraint-determined, freedom labels, stability from initialization objectives, stop-reason labels) + unit suite.
- [x] (2026-07-11) Chart rewritten: HTML lanes (not stretched SVG — 1px lines and round markers at any width) on one shared 5%-rounded scale with zero line, dashed target line, sign-colored bars, muted current markers, tick lane, legend, summary strip, remainder line, hedge note, realized-exposure line, solver footer.
- [x] (2026-07-11) `risk_return_context.cljs` scatter disclosure (current/recommended/standalone dots, Sharpe footnote, returns-context-only note; never titled or drawn as a frontier).
- [x] (2026-07-11) `results_summary.cljs` equal-risk why-card + "· analytics only" return-model fact; `equal_risk_confidence_rail.cljs` rail (From here / Equal-Risk Fit / Allocation Freedom / Solution Stability / Stop Reason); trust rail swaps Diversification for Negative Contributors on equal-risk.
- [x] (2026-07-11) KPI strip extracted wholesale to `scenario_kpi_strip.cljs` (with `recommendation-deltas`), gaining the Risk Balance tile + neutral vol/return deltas for equal-risk; `scenario_detail_view.cljs` dropped to 424 lines — its size-exception entry became STALE and was removed (the gate enforces removal); verdict `:objective-body` override + provenance "· analytics only" wired.
- [x] (2026-07-11) `results_panel.cljs` wiring with plain conditional siblings (never list-as-one-child — Replicant stringifies those) and a keyed center container.
- [x] (2026-07-11) Graceful degradation verified by test: pre-redesign payloads render with honest placeholders (no current markers, "Not recorded on this result", "No initialization record").
- [x] (2026-07-11) Tests green: 5425 tests / 29247 assertions / 0 failures; `lint:delimiters --changed` (17 files), namespace sizes (+ stale-exception removal), `test:websocket` (546/3133/0).
- [x] (2026-07-11) Design doc updated with the results-page section.
- [x] (2026-07-11) `npm run check` exit 0; focused Playwright regression pair 2/2 passed; parent ExecPlan cross-referenced; Outcomes & Retrospective written; committed.
- [ ] Live verification in the user's dev session: the nREPL on port 57170 had closed by validation time, so the visual pass is pending the next dev-server start (the worktree watch rebuild picks these commits up automatically; the payload additions appear on the next Run because the optimizer worker is recreated per run).

## Surprises & Discoveries

- Observation: the current Equal Risk results page leaks frontier-speak — the refinement-based confidence rail renders "Frontier quality: Low" and "Stop reason: Draft budget reached" for Equal Risk runs (visible in the user's screenshots), because `result-confidence-rail` keys off the refinement assessment regardless of objective. This pass replaces it for equal-risk rather than patching its copy.
- Observation: `scenario_detail_view.cljs` sits 23 lines under its 580-line exception cap and `target_exposure_table.cljs` sits 2 lines under the 500 default — both constrain how the KPI/table changes may be made (view-model extraction; no new table column).
- Observation: existing SVG charts color via `currentColor` + Tailwind text classes plus three shared hex constants in `frontier_chart_model` — the theme-colors ratchet means the new chart must reuse those, never introduce new hex literals.

## Decision Log

- Decision: The diverging signed risk-contribution chart is the primary (and only default) visualization for Equal Risk results; no pie/donut/stacked/absolute forms; no efficient frontier by default anywhere on the page. The optional mean-variance comparison overlay from the commentary is DEFERRED (recorded below), not built.
  Rationale: matches the commentary's core (correct) argument — the chart displays the optimized quantity and is the only form that handles negative contributions and >100% positive contributions.
  Date/Author: 2026-07-11 / design pass.
- Decision: No center-panel tabs. The chart is primary; "Risk / Return Context" is a collapsed `<details>` disclosure directly beneath it.
  Rationale: tabs require new UI state (contract path + action + persistence churn) for a secondary view; the repo's established idiom for secondary content is a disclosure whose open state lives in the DOM. Same information hierarchy as the commentary's tabs, less machinery.
  Date/Author: 2026-07-11.
- Decision: Rows are drawn per-asset as HTML grid rows each containing a fixed-height SVG lane sharing one scale, capped at 16 rows sorted by |deviation from target| descending, with an honest remainder line ("+ M more within ±X pts of target"). The full per-asset table remains available in the chart rows themselves (label, weight, share, deviation).
  Rationale: real universes here reach 88+ assets; an uncapped bar chart is unreadable and the commentary doesn't address scale. Sorting by deviation surfaces exactly the assets that explain an Approximate verdict. Per-row SVG lanes keep label/number alignment in HTML (no SVG text layout) while the shared scale keeps bars comparable.
  Date/Author: 2026-07-11.
- Decision: NO Risk Share column in the allocation table in this pass, despite the mockup showing one.
  Rationale: the table sits 2 lines under its namespace-size cap, is memoized and scales to 100+ rows with group/leg expansion, and the identical per-asset numbers (share + deviation + target) are the star of the chart 40px to its right. The commentary itself allows "at minimum the expanded row"; the column is deferred as an isolated follow-up rather than half-done here.
  Date/Author: 2026-07-11.
- Decision: Current-portfolio risk contributions are computed engine-side into the payload (`:current-risk-contributions`, same summary shape minus quality; nil when the current book is empty/degenerate on the selected universe) rather than recomputed in views.
  Rationale: views must not own optimizer math (BOUNDARY.md); the chart's muted "current" markers and the KPI tile's "current → recommended" both need it; payload assembly already holds the covariance and aligned current weights.
  Date/Author: 2026-07-11.
- Decision: Allocation freedom is computed engine-side into `:equal-risk-solver :allocation-freedom`: free degrees = Σ over books of max(0, unlocked-members − 1); `:fully-determined` at 0 degrees (each book's weights pinned by its sum equality), else `:limited` when binding bound constraints exist, else `:open`. Includes `:binding-count` (distinct instruments at bounds) and per-book member counts.
  Rationale: the commentary's "constraint-determined" state is a property of problem geometry the engine knows exactly; the two-position case (one long, one short) classifies as fully-determined and drives the honest "gross and net determine both weights" copy.
  Date/Author: 2026-07-11.
- Decision: `:quality` (exact/approximate/not-converged) is unchanged; "constraint-determined" is presented as ALLOCATION FREEDOM plus copy overlays ("Allocation fully determined by constraints" as the stop-reason line, freedom card value "None — gross and net determine the weights"), never as a replacement for the measured contribution quality.
  Rationale: the commentary agrees the result "may also be Exact or Approximate"; freedom explains WHY, quality states WHAT. Keeping them orthogonal avoids a fourth quality enum leaking through contracts/codecs.
  Date/Author: 2026-07-11.
- Decision: Solution stability classifies from the per-initialization objectives already in `:equal-risk-solver :initializations`: ≥2 completed starts whose objectives agree within max(1e-10 absolute, 5% relative of best) → High; exactly 1 completed start → note "single feasible start" (status caution-free but labeled); otherwise Caution ("initializations reached materially different local solutions").
  Rationale: honest and computable from data the solver already records deterministically; weights per init are not persisted and objective agreement is the meaningful proxy for "same local solution".
  Date/Author: 2026-07-11.
- Decision: KPI strip for equal-risk: Sharpe tile → "Risk balance · current → target" (max deviation pts, RMS in the delta line, green when deviation falls); volatility and expected-return tiles keep their values but use NEUTRAL delta coloring (direction is not success for this objective). Sharpe remains available inside the Risk/Return context disclosure.
  Rationale: per commentary; success for Equal Risk is balance under constraints, not vol/return direction.
  Date/Author: 2026-07-11.
- Decision: Verdict copy branches: exact → "Balances N selected positions toward X% of portfolio volatility each while preserving G× gross and N× net exposure. Max contribution deviation Y pts."; approximate → same plus the deviation and (when present) binding-cap count; fully-determined → "The selected G× gross and N× net exposures require these exact position sizes. With ≤1 free position per book, the weights are fully determined by the exposure constraints; Equal Risk evaluates the resulting balance but cannot improve it."
  Rationale: commentary's recommended language, compressed to one verdict sentence + one qualifier line to fit the existing verdict bar.
  Date/Author: 2026-07-11.
- Decision: New/changed views must render old persisted equal-risk results (which lack the two new payload fields) without errors: current markers and the KPI current side degrade to "—", allocation freedom degrades to absent-card/"—".
  Rationale: results live in IndexedDB scenarios; migrations don't rewrite saved runs.
  Date/Author: 2026-07-11.
- Decision: Browser validation = re-run the two existing focused Playwright specs as regression guards only; no new Playwright for the redesigned results panel in this pass. DOM coverage lives in unit view tests.
  Rationale: scripting a full in-browser equal-risk solve needs the worker-result harness from the BL spec generalized to this objective — meaningful new tooling; the unit tests assert the same DOM, and the user's live worktree dev server verifies visually. Recorded as an explicit gap.
  Date/Author: 2026-07-11.
- Deferred (explicitly out of scope, from the commentary): on-demand mean-variance frontier comparison overlay; "gross risk concentration / effective contributors" metric; Risk Share column (or expanded-row details) in the allocation table; optimization-status footer bar with solve time (timing is deliberately excluded from solver metadata for determinism).

## Outcomes & Retrospective

- (2026-07-11, completion) The Equal Risk results page now presents the optimized quantity itself: the diverging signed-contribution chart (with current markers, target line, per-row deviations, deviation-sorted 16-row cap), the Risk balance KPI tile, objective-specific verdict copy including the constraint-determined case, the "Why this risk allocation" card, and an honest confidence rail (fit / allocation freedom / solution stability / real stop reasons) — no frontier language remains anywhere on an Equal Risk result. All gates green: 5425 tests / 29247 assertions / 0 failures, check exit 0, websocket 546/0, delimiters, namespace sizes, Playwright regression pair 2/2.
- Complexity: roughly NET-NEUTRAL despite the feature surface — six new focused namespaces, but the KPI-strip extraction dropped `scenario_detail_view.cljs` from 557 to 424 lines and RETIRED its size-exception registry entry (the stale-exception gate enforced the removal), and every equal-risk branch reads one view-model so the page cannot self-contradict.
- Gaps (deferred by design decision, recorded in the Decision Log): allocation-table Risk Share column, on-demand mean-variance comparison overlay, gross-risk-concentration metric, and a scripted in-browser equal-risk solve for Playwright. Live visual verification pends the user's next dev-server session (nREPL was closed at validation time); the degradation tests guarantee their existing persisted result renders correctly meanwhile.
- Lessons: (1) HTML lanes beat SVG for diverging bar charts here — `preserveAspectRatio "none"` distorts circles and strokes, while absolutely-positioned divs give 1px lines and round markers at any width with less code; (2) extracting a component to satisfy a size cap can pay double — the KPI strip move both made room for the feature and deleted a debt-registry entry; (3) the Replicant list-as-one-child trap (from memory) was avoided at wiring time rather than debugged live.

## Context and Orientation

The Equal Risk results page is composed by `src/hyperopen/views/portfolio/optimize/results_panel.cljs`: left = allocation table (`target_exposure_table.cljs`), center = `risk_contributions_card.cljs` (currently a plain table; this pass rewrites it) + `results_summary.cljs/target-context-card` ("Why this target"), right = `results_diagnostics_rail.cljs` (`result-confidence-rail` driven by frontier-refinement assessment — wrong for equal-risk — plus `trust-diagnostics-rail`). The KPI strip, verdict bar, and provenance strip live in `scenario_detail_view.cljs` (calls `results_summary/verdict-headline` with `recommendation-deltas`). The solved payload (from `application/engine/payload.cljs`) already carries `:risk-contributions` (signed shares, targets, rms/max errors, negative count, quality) and `:equal-risk-solver` (termination, initializations with per-start objectives, exactness tolerance); this pass adds `:current-risk-contributions` and `:allocation-freedom`. "Signed Euler contribution" and all solver math are documented in `docs/design-docs/optimizer-equal-risk.md` and `domain/risk_contributions.cljs`.

## Plan of Work

Engine: in `payload.cljs equal-risk-sections`, additionally compute `contribution-summary` over `:current-weights` (nil-safe: degenerate or all-zero current book → omit) and an allocation-freedom map from the plan problem's `:books`/`:locked-weights` plus the run diagnostics' binding constraints; specs gain optional predicates. New view-model `application/view_model/equal_risk_results.cljs` holds every label/copy/classification decision (balance rows + display cap, KPI values, verdict copy, freedom label, stability, stop reasons) so the four view files change surgically. Views: rewrite the chart card; add the risk/return scatter disclosure; add the equal-risk why-card and rail; branch the KPI strip/verdict/provenance in `scenario_detail_view.cljs`. Chart colors reuse `frontier_chart_model` constants + `currentColor`; no new hex literals.

## Concrete Steps

    npm run lint:delimiters -- --changed
    npm run check
    npm test
    npm run test:websocket
    npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs \
      --grep "portfolio optimizer setup exposes separate model layers|recommendation chart shows minimum variance" --workers=1

## Validation and Acceptance

1. `npm test` passes; new suites cover: balance-model row ordering/cap/remainder, freedom classification (fully-determined for the 1-long/1-short book, limited with binding caps, open otherwise), stability classification, stop-reason labels, verdict copy variants, chart DOM (signed axis with zero + target line, negative bar for a hedge, current markers, deviation text), rail rows, KPI tile swap and neutral deltas, old-payload graceful degradation.
2. An equal-risk engine run's payload carries `:current-risk-contributions` and `:equal-risk-solver :allocation-freedom` and still passes canonical result specs; min-variance/max-sharpe payloads are unchanged.
3. All repo gates pass; the two focused Playwright specs still pass.

## Idempotence and Recovery

Additive view/payload changes guarded by tests; re-running gates is safe; no migrations (new payload keys optional; old persisted results render degraded-but-correct). Roll back by reverting the branch commits.
