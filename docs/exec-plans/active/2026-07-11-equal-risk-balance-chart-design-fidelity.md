# Equal Risk balance chart: designer-spec fidelity pass

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. Maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The Equal Risk results centerpiece (RISK CONTRIBUTION BALANCE, shipped 2026-07-11) is functionally right but visually far from the designer's specification the user supplied on 2026-07-11. This pass closes the visual gap so the card reads like the spec: a card header with the title, the one-line "Signed Euler contribution to total portfolio volatility" subtitle, and a two-tab switcher (RISK CONTRIBUTION / RISK / RETURN) on the right; a five-cell KPI strip (Equal target · Status · RMS deviation · Max deviation · Negative contributors) with semantic value colors; a framed plot area with full-height vertical gridlines, a continuous dashed purple equal-target line, sign-colored recommended bars with a green recommended dot, gray outlined current circles joined to the bar end by a dashed connector, per-row purple Target and sign-colored Deviation columns, an x-axis titled "Contribution to Total Volatility (%)"; a "READING THIS" footnote; and a "WHY THIS RISK ALLOCATION" section of four icon cards (Book shape / Equal target / Largest risk contributor / Allocation freedom). Purple joins the scoped optimizer palette as the target color (`--optimizer-target`). After this change the card at `/portfolio/optimize/draft` (Recommendation tab, Equal Risk objective) is a close visual match of the spec across the states we actually produce (exact, approximate, hedged books, degraded persisted payloads, capped large universes).

## Context References

Public refs:
- Direct user request (2026-07-11) attaching the designer's mock of the RISK CONTRIBUTION BALANCE card and asking for a near-pixel implementation of it.

Repo artifacts:
- Parent ExecPlan: `/hyperopen/docs/exec-plans/active/2026-07-11-optimizer-equal-risk-results-redesign.md` (built the chart/view-model this pass restyles; its Decision Log is the baseline this plan amends).
- Design doc: `/hyperopen/docs/design-docs/optimizer-equal-risk.md`.
- Scoped visual system: `/hyperopen/src/styles/surfaces/optimizer/base.css` (`--optimizer-*` tokens; ratchet-exempt).

Local scratch refs (non-authoritative): none.

## Progress

- [x] (2026-07-11) Audited the shipped card against the mock and mapped every visual delta (header/tabs, KPI strip, gridlines + continuous target line, markers/connectors, Target+Deviation columns replacing Share, reading-this row, icon why-cards, purple target color).
- [x] (2026-07-11) Confirmed constraints: optimizer surface is theme-colors-ratchet-exempt (token additions go in `optimizer/base.css`); `lint:hiccup` (single-class vector entries, keyword style keys); namespace-size caps have headroom (card 252/500, summary 312/500); no Playwright spec references the touched data-roles; lucide ships `list`/`crosshair`/`star`/`lock`/`lock-open`.
- [x] (2026-07-11) Added `--optimizer-target` / `--optimizer-target-border` / `--optimizer-target-soft` to `src/styles/surfaces/optimizer/base.css`.
- [x] (2026-07-11) View-model: per-row `:target-share` (per-instrument with uniform fallback), display ordering (cap by worst |deviation|, display by signed share desc), `deviation-tone` severity classifier, `:largest` computed over ALL rows (pre-cap — fixes the shipped why-card picking the largest only among visible rows), `freedom-card-view` copy.
- [x] (2026-07-11) Rewrote `risk_contributions_card.cljs` to the spec layout (~330 lines: DOM-state radio tabs, five-cell KPI strip, framed plot with backdrop gridlines + continuous dashed target line, sign-colored bars + green recommended dot + gray current circle + dashed connector + per-row target tick, purple Target / signed Deviation columns, axis title, READING THIS row with the hedge sentence folded in); all pre-existing data-roles and test-pinned copy preserved.
- [x] (2026-07-11) `risk_return_context.cljs` → `risk-return-panel` tab body (roles/copy unchanged, markers aligned to the chart language); standalone disclosure removed from `results_panel.cljs`.
- [x] (2026-07-11) `results_summary.cljs/equal-risk-context-card` → four lucide icon cards (list/crosshair/star/lock|lock-open via the shared `setup-controls/lucide-icon`); Limits-hit chips dropped (rail enumerates bindings); "On target" near-zero edge handled.
- [x] (2026-07-11) Scoped CSS in `optimizer/results.css`: tab switcher + panel toggling via `:has()`, hairline KPI grid, static chart-row grid template, backdrop lines, markers, icon-card tones.
- [x] (2026-07-11) Workbench scenes added: designer-parity (mock book, signed per-side targets), hedged-book-uniform-target, exact-two-asset, capped-24-asset-universe, persisted-pre-redesign.
- [x] (2026-07-11) Tests updated/extended: view-model display-order/per-row-target/tone/freedom-card/largest-out-of-cap suites; results-panel suite gains KPI-strip, tab, Target/Deviation-column, reading-row, and icon-card assertions. Full node suite green (5428 tests / 29278 assertions / 0 failures).
- [x] (2026-07-11) Browser QA on the workbench scenes via the worktree static-serve (:8090) recipe: computed-style checks (purple line/columns, amber active-tab underline, sign-colored bars, 10px current ring, one-row KPI strip), zero-app-state tab switching exercised both ways, degradation scene shows no fabricated markers, no console errors; screenshots captured for the user.
- [x] (2026-07-11) `docs/design-docs/optimizer-equal-risk.md` results-page section rewritten for the new card.
- [x] (2026-07-11) Gates green: `npm run gates` Overall PASS (34/34 — check lints + all 6 shadow builds + `npm test` 6134 tests/32623 assertions + `test:websocket`); high-resolution proof screenshots captured (designer-parity and hedged-book scenes) and delivered.
- [ ] User-side visual pass in the live dev session, then land the branch (commit/merge are left to the user's call).

## Surprises & Discoveries

- Observation: the scoped optimizer visual system (`src/styles/surfaces/optimizer/**` and `src/hyperopen/views/portfolio/optimize/**`) is exempt from the theme-colors ratchet, so the spec's purple can be introduced properly as `--optimizer-target` at the token-definition site instead of via a global palette.js token (which would force a value choice on all three app themes for a color only this surface uses).
- Observation: the mock's per-row deviations for the two short positions (+1.8 / +1.0 pts against bars at ≈ −18%) only reconcile if the designer assumed per-side signed targets; our engine targets a uniform signed 1/n for every asset, so hedging rows will honestly show large negative deviations. The visual language carries over unchanged; the numbers follow our math. The payload's per-instrument target section makes the view future-proof: the designer-parity workbench scene feeds signed per-side targets through it and reproduces the mock's numbers exactly.
- Surprise: the KPI strip's responsive wrap was first written as viewport media queries and wrapped 3+2 inside the workbench canvas — the strip's width is set by the CENTER PANEL, not the viewport, so `repeat(auto-fit, minmax(148px, 1fr))` (container-driven) replaced the media queries and keeps all five cells on one row wherever ~760px of card width exists.
- Surprise: a view-model unit test asserting `10.0` deviation points tripped on IEEE 754 (`(-0.4 − −0.5) × 100 = 9.999999999999998`); the fixture now uses dyadic fractions (0.125 steps) so equality assertions stay exact.

## Decision Log

- Decision: Implement the spec's two-tab switcher with two visually-hidden radio inputs whose checked state lives in the DOM, styled/toggled by `:has()` rules in the scoped optimizer CSS — no new runtime action, contract entry, or app state.
  Rationale: the parent plan rejected tabs because they "require new UI state (contract path + action + persistence churn)"; DOM-state radios deliver the designer's explicit tab control under the same zero-app-state constraint the disclosure satisfied, and `:has()` already has precedent in `results.css`. Radio group names are suffixed with the result's `:as-of-ms` so co-rendered workbench scenes don't share a group.
  Date/Author: 2026-07-11 / design-fidelity pass.
- Decision: Purple target tokens are optimizer-scoped (`--optimizer-target`, `--optimizer-target-border`, `--optimizer-target-soft` in `optimizer/base.css`), not palette.js tokens.
  Rationale: the optimizer surface deliberately pins its own near-black palette independent of the active app theme; a global token would demand per-theme purple choices nothing else uses.
  Date/Author: 2026-07-11.
- Decision: The per-row numeric columns become Target (purple) and Deviation (signed, red when above target, green when below) per the mock, replacing the Share column. Share stays recoverable (target + deviation), appears in each row's tooltip, and headlines the Largest-risk-contributor why-card.
  Rationale: matches the spec exactly; no information is lost.
  Date/Author: 2026-07-11.
- Decision: Deviation color follows the mock's sign semantics (above target = red family, below = green family) in the per-row column and the largest-contributor card; the KPI strip's RMS/Max cells instead use a magnitude tone (≤20% of the equal target → green, ≤50% → amber, above → red) so a badly unbalanced book can never show green KPIs.
  Rationale: the mock shows 1.8/3.4 pts green under an Approximate status — small relative to a 20% target; a fixed green would misreport pathological books, and the solver's 1%-of-target exactness tolerance is too strict to reproduce the mock's coloring.
  Date/Author: 2026-07-11.
- Decision: Display order = signed share descending (longs high-to-low, hedges at the bottom) as in the mock; the 16-row cap still selects by worst |deviation| first, and the remainder line is unchanged.
  Rationale: the mock's diverging silhouette (greens descending, reds below) is the design's shape; deviation-first selection keeps the rows that explain an Approximate verdict visible on 88-asset universes.
  Date/Author: 2026-07-11.
- Decision: Keep the honest extras the mock omits — hedge note (folded into the READING THIS row), remainder line, realized-exposure line, and the solver footer ("Converged · N iterations · <seed> start") — as quiet muted rows at the card foot; drop the long quality-note paragraph in favor of a `title` tooltip on the Status KPI cell.
  Rationale: they carry test-pinned, decision-relevant information the design does not contradict; the quality sentence duplicated what Status + deviations already say.
  Date/Author: 2026-07-11.
- Decision: The why-card's Limits-hit chip enumeration is removed; the Allocation-freedom icon card carries the binding count ("Limited · 2 binding caps") and the trust-diagnostics rail remains the enumeration site.
  Rationale: mock parity without information loss — the rail already lists every binding constraint.
  Date/Author: 2026-07-11.
- Decision: Legend renders all four mock items (dashed Target line, gray Current circle, purple per-row Target tick, green Recommended dot); per-row target ticks draw at each row's own target so they coincide with the global line today but stay correct if per-row targets ever diverge.
  Rationale: pixel parity plus forward-compatibility with the per-instrument targets already in the payload.
  Date/Author: 2026-07-11.
- Decision: Chart rows keep the HTML-lane construction (absolutely positioned divs on one shared scale) — no SVG — with the gridline/zero/target backdrop drawn once behind the row stack in a same-grid overlay; the row grid template moves to static CSS in `results.css`.
  Rationale: the parent plan's HTML-lanes ruling stands (1px lines, round markers at any width); static CSS tracks are the established fix for Tailwind JIT intermittently dropping arbitrary `grid-cols-[…]` values in watch mode.
  Date/Author: 2026-07-11.

## Outcomes & Retrospective

- (2026-07-11) The Equal Risk results centerpiece now matches the designer's spec: header with tab switcher (RISK CONTRIBUTION / RISK / RETURN), five-cell semantic KPI strip, framed plot with full-height gridlines and the continuous dashed purple target line, sign-colored bars with recommended dots / current circles / dashed connectors / per-row target ticks, purple Target and signed Deviation columns, axis title, READING THIS footnote, and the four-icon WHY THIS RISK ALLOCATION row. All gates green (34/34, 6134 tests / 32623 assertions), browser QA on five workbench scenes with computed-style verification and zero console errors.
- The designer's tabs landed WITHOUT the app-state cost that had vetoed them in the parent plan: label-wrapped radio inputs + scoped `:has()` CSS keep the checked state in the DOM exactly like the `<details>` idiom they replace. Uncontrolled inputs (never pass `:checked` from render) are what make this Replicant-safe.
- Honesty deltas from the mock, all deliberate: Negative-contributors KPI counts the fixture's real hedges; hedging rows under the engine's uniform target show their true large negative deviations (the mock's ±2-pt shorts only exist under per-side targets, which the payload supports and the parity scene exercises); RMS/Max KPI cells grade by magnitude so a pathological book can never render green; the remainder/exposure/solver footers stay.
- Bonus fix: the why-card's "largest risk contributor" previously scanned only the 16 visible rows — `:largest` now computes pre-cap in the view-model, with a regression test.
- Lesson: inside the workbench canvas iframe, viewport media queries lie about component width — container-driven `repeat(auto-fit, minmax(…))` is the right wrap primitive for strips that live in a resizable panel.

## Context and Orientation

The card lives at `src/hyperopen/views/portfolio/optimize/risk_contributions_card.cljs`, composed by `results_panel.cljs` for `:equal-risk` results together with `risk_return_context.cljs` (vol/return scatter, currently a separate collapsed disclosure) and `results_summary.cljs/equal-risk-context-card` ("Why this risk allocation"). All numbers/labels come from the pure view-model `application/view_model/equal_risk_results.cljs` over the solved payload's `:risk-contributions`, `:current-risk-contributions`, and `:equal-risk-solver` sections. The `.portfolio-optimizer` scope (`src/styles/surfaces/optimizer/base.css`) defines the dense charcoal/amber idiom (`--optimizer-*` vars, utility remaps) the mock is drawn in; component chrome belongs in `src/styles/surfaces/optimizer/results.css`. View tests walk hiccup via `test-support/node-by-role` + `collect-strings` (`test/hyperopen/views/portfolio/optimize/results_panel_equal_risk_test.cljs`). The portfolio.replicant workbench (`portfolio/hyperopen/workbench/scenes/optimize/…`, served at `/ui-workbench.html` by the `:app` dev server or a static server) is the visual-verification harness.

## Plan of Work

Tokens first (purple), then the view-model additions (per-row target share, display ordering, deviation tones) so every visual branch reads one source, then the card rewrite (header+tabs, KPI strip, plot frame with backdrop, rows, axis, reading-this), the scatter's conversion to tab panel, the icon why-cards, and the static CSS. Workbench scenes reproduce the mock's book (3 long / 2 short, mild deviations) plus our honest extreme states; tests update alongside each view change. Validation = full gates plus a browser pass over the scenes compared against the mock.

## Concrete Steps

    npm run setup:worktree
    npm run lint:delimiters -- --changed
    npm run gates
    # browser QA (worktree static-serve on :8090; see memory recipe), then:
    npm run browser:cleanup

## Validation and Acceptance

1. `npm test` passes; updated suites cover: view-model display ordering + per-row targets + tone thresholds; card DOM (tab inputs/labels, five KPI cells with roles, per-row Target/Deviation cells, backdrop target line, current markers + connector only when current data exists, reading-this copy, solver footer); why-card icon cards; graceful degradation on pre-redesign payloads (no current markers, freedom "—").
2. `npm run check` and `npm run test:websocket` pass; `lint:hiccup`, namespace sizes, and the main.css import manifest are untouched or updated consistently.
3. Workbench scenes render a close visual match of the mock (verified in-browser: gridlines, continuous dashed purple target line, bar/marker/connector geometry, KPI colors, tab switching without app state), with screenshots captured for the user.

## Idempotence and Recovery

Pure view/CSS/view-model changes behind existing payload contracts; no migrations, no engine changes, no new runtime actions. Re-running gates is safe; roll back by reverting the branch commits. Persisted pre-redesign results keep rendering (degradation paths retained and test-pinned).
