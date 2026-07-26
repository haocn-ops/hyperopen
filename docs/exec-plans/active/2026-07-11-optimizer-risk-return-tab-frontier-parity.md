# Bring the Equal Risk RISK / RETURN tab to visual parity with the designer's mock

This ExecPlan is a living document maintained in accordance with `/.agents/PLANS.md`. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

## Purpose / Big Picture

When the portfolio optimizer solves the Equal Risk objective, the results page centers on the "Risk contribution balance" card, whose header carries four DOM-state tabs: RISK CONTRIBUTION, BREAKDOWN, CORRELATION, and RISK / RETURN. Today the RISK / RETURN tab renders a bare positioned-div scatter — tiny anonymous dots floating in an empty bordered box with no axes, no gridlines, no tick labels, and no asset identity. Users read it as broken.

The designer's 2026-07-11 mock shows what the tab should be: a real risk/return chart with a dotted grid, percentage tick labels on both axes, axis titles ("Volatility (Annualized)" / "Expected Return (Annualized)"), each asset plotted as a labeled point, a dashed crosshair through the current portfolio's volatility plus a dashed zero-return line, the current portfolio drawn as a small white dot inside a thin white ring labeled "Current", the recommended portfolio drawn as a small green dot inside a thin green ring labeled "Recommended (Equal Risk)", a thin connector between the two, a legend row beneath the chart, and a three-box context strip above the chart (Current portfolio stats / Recommended stats / a "Context only" disclaimer).

After this change, opening the RISK / RETURN tab on any Equal Risk result shows that chart, built on the SAME chart infrastructure the efficient-frontier objectives already use (grid, axes, nice ticks, per-asset icon markers with hover callouts) — with our asset icons in place of the mock's plain colored dots — while deliberately never drawing a frontier curve: Equal Risk is not selected from a frontier and expected returns did not determine the weights. To see it working: run an Equal Risk optimization at `/portfolio/optimize` (or open the `equal-risk-scenes` workbench scenes) and click the RISK / RETURN tab on the balance card.

## Context References

Public refs:
- Direct user/maintainer request (2026-07-11): "bring [the Equal Risk RISK / RETURN tab] closer to visual parity with what the designer is showing… use our existing efficient frontier [chart] where we just use our icons instead of regular dots… I do like the way they show the crosshairs… and that small white dot and small green dot with a little circle around it rather than the dots that we use currently." Designer mock attached in the request (described exhaustively in Context and Orientation below).

Repo artifacts:
- `docs/exec-plans/completed/2026-07-10-optimizer-setup-comprehension-redesign.md` (precedent for the balance-card design-fidelity work this builds on)
- `src/hyperopen/views/portfolio/optimize/risk_contributions_card.cljs` (the tabbed card that hosts this panel)
- `src/hyperopen/views/portfolio/optimize/frontier_chart_model.cljs`, `frontier_chart_axes.cljs`, `frontier_chart_layers.cljs`, `frontier_overlay_markers.cljs` (the chart infrastructure being reused)

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-07-11) Explore current tab implementation, frontier chart infrastructure, CSS tokens, tests, workbench scenes; write this plan.
- [x] (2026-07-11) Milestone 1: rewrite `risk_return_context.cljs` as an SVG chart on the frontier chart infrastructure (grid, axes, ticks, icon markers, crosshairs, ringed markers, connector, legend, context boxes).
- [x] (2026-07-11) Milestone 2: add `optimizer-risk-return-*` CSS to `src/styles/surfaces/optimizer/results.css`; retire the dead `.optimizer-risk-balance-plot` scatter rule.
- [x] (2026-07-11) Milestone 3: update `results_panel_equal_risk_test.cljs` copy/role assertions; add focused `risk_return_context_test.cljs` (4 deftests); refresh the `designer-parity` workbench scene with mock-parity numbers via a fixture `:overrides` hook.
- [x] (2026-07-11) Milestone 4: `npm run gates` → 34/34 PASS (6160 tests, 32797 assertions; includes lint:docs on this plan, namespace sizes, theme colors, all shadow-cljs builds).
- [x] (2026-07-11) Milestone 5: browser QA in the ui-workbench scenes with screenshot proof — `designer-parity` matches the mock (header + boxes + chart + crosshairs + ringed markers + connector + legend + Sharpe line); `capped-24-asset-universe` shows all 24 markers with the legend capped at 8 + "+ 16 more assets"; `persisted-pre-redesign` renders; zero console errors.
- [ ] Move this plan to `docs/exec-plans/completed/` once accepted by the requester.

## Surprises & Discoveries

- Observation: the "broken" look has a concrete cause — `scatter-scale` in the old panel clamps every point into 0–100% of a 224px-tall unadorned div, so points hug the borders and nothing explains the space.
  Evidence: `risk_return_context.cljs` (pre-change) lines 38–56 clamp `(max 0)`/`(min 100)`; the plot div carried only a border.
- Observation: hover callouts need no per-chart `<style>` block when nested — `.portfolio-frontier-marker:hover .portfolio-frontier-callout { display: inline }` in `frontier.css` handles any marker that nests its callout, which the overlay markers do by default (`render-callout?` defaults truthy). The frontier chart's `<style>` block exists only to hoist callouts into a top layer for z-order; with ≤ a dozen points we accept nested z-order.
  Evidence: `src/styles/surfaces/optimizer/frontier.css` lines 131–142; `frontier_overlay_markers.cljs` `standalone-marker`.
- Observation: in the Claude Code browser pane, pointer clicks on the ui-workbench page did not reach the scene iframe's tab labels at either screenshot-space or viewport-space coordinates (the Portfolio canvas chrome appears to intercept them), even though the geometry computed via `getBoundingClientRect` matched the click point exactly. QA fell back to `label.click()` on the iframe's `contentDocument`, which proved the whole DOM-state chain live (radio `checked` → scoped `:has()` CSS → panel `display: block`). The tab mechanism itself is untouched by this change and was browser-verified when it shipped.
  Evidence: Milestone 5 session — `{"checked":true,"panelDisplay":"block","svg":true,…}` from the live page after `label.click()`.
- Observation: all five designer-parity asset icons resolve, including the equity ones, once the fixture uses dex-namespaced ids (`perp:xyz:SP500`, `perp:xyz:MSTR`) — the icon key parser strips the `perp:` prefix and serves `xyz:SP500.svg` / `xyz:MSTR.svg`.
  Evidence: Milestone 5 screenshots show the S&P and MicroStrategy marks; no icon 404 fallback needed.

## Decision Log

- Decision: reuse the frontier chart's model/axes/marker namespaces directly instead of duplicating chart code or extending `chart-svg`.
  Rationale: `chart-svg` in `frontier_chart_layers.cljs` is frontier-specific (curve path, draggable points that dispatch objective changes — actively wrong for Equal Risk, where a click would silently switch the objective). The reusable parts (geometry constants, `domain`, `point-position`, `axis-ticks`, `tick-label`, standalone icon markers with callouts) are already public functions; composing them in the panel keeps one source of truth for chart chrome without inheriting frontier semantics.
  Date/Author: 2026-07-11 / Claude (with Geronimo).
- Decision: never draw a frontier curve or reuse `portfolio-optimizer-frontier-svg`/`-panel` data-roles; the SVG gets its own `portfolio-optimizer-risk-return-svg` role.
  Rationale: `results_panel_equal_risk_test` asserts no frontier machinery renders for Equal Risk, and the card's docstring promises the tab is "deliberately NOT titled or drawn as a frontier". Parity here means chart QUALITY, not frontier semantics. Inner marker/tick roles inherited from the reused namespaces (`portfolio-optimizer-frontier-overlay-standalone-*`, `-x-tick-*`) are acceptable because the two charts never co-exist in the DOM (Equal Risk replaces the frontier chart).
  Date/Author: 2026-07-11 / Claude.
- Decision: crosshair = dashed vertical line at the CURRENT portfolio's volatility spanning the plot height, plus a dashed horizontal line at 0% expected return spanning the plot width; both in `--optimizer-text-3` with `4 4` dashes.
  Rationale: that is exactly what the mock draws (they cross at current-vol × 0%); the vertical line anchors "where my book sits today" against every other point, and the zero line separates positive from negative expected return. Neither is derived state — both render only from values already on the result.
  Date/Author: 2026-07-11 / Claude.
- Decision: current marker = thin ring (r 8) + small core dot (r 2.6) in `--optimizer-text` (near-white) with plain "Current" text below; recommended marker = same geometry in `--optimizer-long` (green) with "Recommended (Equal Risk)" text on a short leader, painted last; thin connector line between the two.
  Rationale: the user explicitly preferred the mock's ring-and-dot markers over our frontier markers (blue halo "Current" pill, purple gradient "Target" orb). Green-for-recommended and outlined-for-current also matches the marker language of the balance chart on the first tab (`.optimizer-risk-balance-dot` green / `.optimizer-risk-balance-current` outlined), so the card stays self-consistent.
  Date/Author: 2026-07-11 / Claude.
- Decision: adopt the mock's note copy — "Expected returns are shown for context and did not determine the Equal Risk allocation." — replacing "…did not affect…", and add the mock's three context boxes above the chart (Current portfolio / Recommended (Equal Risk) / Context only), with the third box copy kept return-model-agnostic ("Expected returns come from the return model (analytics only). Not used to size positions.") rather than the mock's "historical mean" wording.
  Rationale: "determine" is the stronger, more accurate honesty claim; the mock's "historical mean" would be false whenever the return model is Black-Litterman or views-adjusted, and the Run Assumptions strip already names the model per-run.
  Date/Author: 2026-07-11 / Claude.
- Decision: label every asset point with small muted text beside its icon, and add a legend row capped at 8 asset entries ("+N more assets" overflow) plus Current Portfolio and Recommended (Equal Risk) swatches.
  Rationale: the mock labels its points and carries a full legend; icons alone leave less-recognizable assets (equity indexes, HIP-3 dex assets) anonymous. The cap keeps a 24-asset universe from wrapping the legend into a wall; the honest overflow text follows the balance chart's remainder-line precedent. Hover callouts remain for exact numbers.
  Date/Author: 2026-07-11 / Claude.
- Decision: keep the existing 5-cell KPI strip OFF this tab (the mock shows none), keep the Sharpe footnote, and leave the why-card tiles below the card untouched.
  Rationale: the mock's bottom tile row (BOOK SHAPE / EQUAL TARGET / CURRENT VS RECOMMENDED / ALLOCATION FREEDOM) is the already-shipped `equal-risk-context-card`; its third tile differing (Current vs recommended instead of Correlation view) is a separate design conversation — out of scope here and recorded as a non-goal.
  Date/Author: 2026-07-11 / Claude (with Geronimo's request scoped to the tab chart).

## Outcomes & Retrospective

Implemented and QA'd 2026-07-11; awaiting requester acceptance (the one open Progress item). Against the purpose: the tab now reads as a real chart — side-by-side with the mock, the header/note line, the three context boxes (purple vol / green return values, green-framed recommended box), grid + percent ticks + axis titles, five labeled icon points, the dashed crosshair pair meeting at (current vol, 0%), the white and green ring-and-dot markers with connector and labels, the legend, and the why-tiles below all correspond; the deliberate departures (our asset icons instead of anonymous colored dots, model-agnostic Context-only copy, no frontier curve ever) are in the Decision Log. Gates: 34/34 PASS. Coverage: this panel went from 3 incidental assertions to a dedicated 4-deftest namespace plus 8 new assertions in the results-panel test. Complexity: concept count fell — the panel's private percent-clamping scale is gone and it now shares the frontier chart's geometry/axes/markers — at the cost of view-code volume (121 → 445 lines, under the 500-line gate). Lesson: the original div-scatter was cheap to write but read as a defect precisely because it lacked chart chrome; when a "context" chart shares an axis language with a sibling chart, building on the sibling's infrastructure from the start is the cheaper path overall.

## Context and Orientation

Hyperopen is a ClojureScript trading UI (Replicant hiccup views, no React). The optimizer lives under `src/hyperopen/views/portfolio/optimize/` (views) and `src/hyperopen/portfolio/optimizer/` (domain/application). Styles are plain CSS files under `src/styles/surfaces/optimizer/` scoped under `.portfolio-optimizer`, with design tokens as CSS custom properties in `src/styles/surfaces/optimizer/base.css` (`--optimizer-bg` #0a0b0d, `--optimizer-text` #e6e7ea near-white, `--optimizer-text-2` gray, `--optimizer-text-3` dim gray, `--optimizer-long` #4ea674 green, `--optimizer-target` #a78bfa purple, `--optimizer-border`/`-strong`).

"Hiccup" means views are plain Clojure vectors like `[:div {:class [...]} child]`; tests walk these vectors directly without a DOM. "Data-role" means the `:data-role` attribute used as the stable hook for tests and browser QA.

The Equal Risk results card is `risk-contributions-card` in `src/hyperopen/views/portfolio/optimize/risk_contributions_card.cljs`. Its tabs are visually-hidden radio inputs toggled by scoped `:has()` CSS in `src/styles/surfaces/optimizer/results.css` (search `optimizer-risk-balance-panel--risk-return`) — tab state lives in the DOM, not app state. The RISK / RETURN tab body is `risk-return-panel` in `src/hyperopen/views/portfolio/optimize/risk_return_context.cljs`, called with the solved result map; it returns nil when neither portfolio point exists, and the card then omits the tab.

The result map fields this panel consumes (all already computed by the engine for every objective):

    :volatility, :expected-return                 — the recommended portfolio point
    :current-volatility, :current-expected-return — the current book's point (may be absent)
    :frontier-overlays {:standalone [...]}        — per-asset standalone points, each
        {:instrument-id "perp:BTC" :label "BTC" :volatility 0.4 :expected-return 0.12 ...}
    :performance / :current-performance {:in-sample-sharpe n} — for the Sharpe footnote

The efficient-frontier objectives already render a full SVG chart from these namespaces (all under `src/hyperopen/views/portfolio/optimize/`):

- `frontier_chart_model.cljs` — public geometry constants (`chart-width` 680, `chart-height` 380, plot paddings, `plot-geometry`, `chart-bounds`, `chart-grid-stroke`, `chart-axis-stroke`), `domain` (data → padded axis domain, with `:floor-zero?` for x and `:include-zero?` for y), and `point-position` (maps a `{:volatility :expected-return}` point into SVG coordinates).
- `frontier_chart_axes.cljs` — `axis-ticks` (nice tick values), `tick-domain` (snap domain to ticks), `x-tick-position`/`y-tick-position`, `tick-label` (renders a percent tick `<text>`).
- `frontier_overlay_markers.cljs` — `marker` with `:overlay-mode :standalone` renders one asset as an icon-in-circle SVG marker (asset icon URL via `hyperopen.views.asset-icon/market-icon-url`, text-symbol fallback when no icon resolves) and nests a hover callout by default. Hover visibility comes from `.portfolio-frontier-marker:hover .portfolio-frontier-callout` rules in `src/styles/surfaces/optimizer/frontier.css` — no per-chart style block needed when callouts stay nested.
- `frontier_callout.cljs` — `point-rows` (Expected Return / Volatility / Sharpe rows) and `callout` (the hover tooltip group), `hitbox` (transparent hover circle).
- `hyperopen.portfolio.optimizer.application.view-model.frontier` — `overlay-label` and `point-market` (icon resolution input).

The designer mock, described exactly (values are the mock's fake data): a header line "RISK / RETURN CONTEXT ⓘ" with a right-aligned note "Expected returns are shown for context and did not determine the Equal Risk allocation."; below it three boxes — CURRENT PORTFOLIO (VOLATILITY (ANN.) 298.88% in purple, EXPECTED RETURN (ANN.) 853.23% in green), RECOMMENDED (EQUAL RISK) (green title and border; 301.47% / 841.91%), CONTEXT ONLY ("Expected return informed by historical mean (analytics only). Not used for optimization."); then the chart — y axis −1,200%…1,200% ticks every 400%, x axis 0%…500% ticks every 50%, dotted grid, five labeled asset dots (SOL, BTC, ETH, SP500, MSTR), a dashed horizontal line at 0% return, a dashed vertical line at the current volatility (~300%), the Current marker (white ring + white core dot, "Current" label) at (298.88%, 853.23%), the Recommended marker (green ring + green core dot, green "Recommended (Equal Risk)" label) at (301.47%, 841.91%), a thin line connecting the two; a legend row "● SOL ● BTC ● ETH ● SP500 ● MSTR ○ Current Portfolio ◉ Recommended (Equal Risk)".

Guardrails that shaped the work: namespaces over 500 lines fail `dev/check_namespace_sizes.clj`; theme colors in CSS are ratcheted by `dev/check_theme_colors.clj` (use the existing `--optimizer-*` tokens; the optimizer surface is exempt from some app-wide ratchets but do not add new raw hex values to CSS); hiccup attribute lints run under `npm run check`. Existing tests that pin this area: `test/hyperopen/views/portfolio/optimize/results_panel_equal_risk_test.cljs` asserts the tab exists (`portfolio-optimizer-risk-view-tab-risk-return`), the panel role (`portfolio-optimizer-risk-return-context`), the recommended marker role (`portfolio-optimizer-risk-return-target`), the honesty copy, and that NO frontier panel role renders.

## Plan of Work

All paths are repository-relative; the working directory is the repository root.

Milestone 1 — the panel. Rewrite `src/hyperopen/views/portfolio/optimize/risk_return_context.cljs` (keeping the public `risk-return-panel` entry point and its nil-when-no-points contract). Build the scatter model from the result (standalone points + optional current + recommended target). Compute `x-domain` via `model/domain` of all volatilities with `{:floor-zero? true}` and `y-domain` of all expected returns with `{:include-zero? true}`, then snap both through `chart-axes/axis-ticks` (6) and `chart-axes/tick-domain` — identical to the frontier chart, so axes read the same across objectives. Render, in paint order inside one `<svg viewBox="0 0 680 380" data-role="portfolio-optimizer-risk-return-svg">`: grid lines at each tick; the two axis lines; tick labels via `chart-axes/tick-label`; the two axis titles; the dashed zero-return line (`data-role portfolio-optimizer-risk-return-zero-line`, stroke `var(--optimizer-text-3)`, `stroke-dasharray "4 4"`, opacity 0.65, drawn when 0 lies inside the y-domain); the dashed current-volatility crosshair (`data-role portfolio-optimizer-risk-return-crosshair`, same style, drawn only when the current point exists); the thin current→recommended connector (`data-role portfolio-optimizer-risk-return-connector`, stroke `var(--optimizer-text-3)`, opacity 0.6); each standalone asset via `frontier-overlays/marker {:overlay-mode :standalone ...}` (nested hover callouts) plus a small muted `<text>` label beside each icon; the current marker (`data-role portfolio-optimizer-risk-return-current`: ring r 8 stroke `var(--optimizer-text)` fill `var(--optimizer-bg)` at 0.85 opacity, core r 2.6, "Current" text below, nested `frontier-callout/callout` + hitbox so hover shows exact numbers); and last the recommended marker (`data-role portfolio-optimizer-risk-return-target`: same geometry in `var(--optimizer-long)`, leader line to a green "Recommended (Equal Risk)" label that flips to the left when the point sits in the right 40% of the plot, nested callout + hitbox). Above the SVG render the header row (title "Risk / return context", an ⓘ glyph carrying the full explanation in `:title`, right-aligned designer note with `data-role portfolio-optimizer-risk-return-note`) and the three context boxes (`-current-box`, `-recommended-box`, `-context-box`; vol values in `--optimizer-target` purple, return values in `--optimizer-long` green; boxes render only when their point exists). Below the SVG render the legend (`data-role portfolio-optimizer-risk-return-legend`; asset entries as 12px rounded icon images falling back to a neutral disc, capped at 8 with "+N more assets"), and keep the existing Sharpe footnote.

Milestone 2 — the styles. In `src/styles/surfaces/optimizer/results.css`, next to the existing risk-balance rules: add `optimizer-risk-return-header/-title/-info/-note`, `optimizer-risk-return-boxes` (3-up auto-fit grid with the 1px-gap hairline trick used by `optimizer-risk-balance-kpis`), `optimizer-risk-return-box`, `--recommended` variant (green title + `color-mix` border tint), `-box-label/-box-value/--vol/--ret`, `optimizer-risk-return-legend/-legend-item/-legend-icon/-legend-dot/-legend-current/-legend-recommended` (the last two draw the ring-plus-core-dot swatch with a border plus a radial-gradient center). Delete the now-unused `.optimizer-risk-balance-plot` rule. Use only existing `--optimizer-*` tokens.

Milestone 3 — tests and scenes. Update `test/hyperopen/views/portfolio/optimize/results_panel_equal_risk_test.cljs`: the honesty string becomes "did not determine the Equal Risk allocation"; add assertions that the SVG, crosshair, zero line, legend, and context boxes render. Create `test/hyperopen/views/portfolio/optimize/risk_return_context_test.cljs` rendering `risk-return-panel` directly: full result → svg + both markers + connector + crosshair + asset markers + labels + boxes; no current point → no crosshair/connector/current box, panel still renders; neither point → nil; non-finite overlay points are dropped. In `portfolio/hyperopen/workbench/scenes/optimize/equal_risk_scenes.cljs`, extend the `equal-risk-result` fixture builder with an `:overrides` deep-merge and give `designer-parity` the mock's numbers (current 298.88%/853.23%, recommended 301.47%/841.91%, five standalone points at the mock's coordinates with real dex-namespaced instrument ids so icons resolve).

Milestone 4 — gates. `npm run gates` (runs `npm run check`, `npm test`, `npm run test:websocket` with a PASS/FAIL matrix). No Playwright spec changes: no committed browser flow changes (the tab, radios, and card structure are untouched; only the panel body changed), and `spec/` has no assertions on this panel — verified by grep.

Milestone 5 — browser QA. Serve the workbench (`npm run dev`, open `http://localhost:8080/ui-workbench.html`), open the Equal Risk balance scenes, click RISK / RETURN on `designer-parity`, and compare against the mock: grid + ticks + titles, labeled icon points, both dashed lines crossing at (current vol, 0%), ringed white/green markers with connector and labels, legend, boxes. Hover an asset icon and the current/recommended markers for callouts. Check `persisted-pre-redesign` (no current book) still renders without crosshair/current box, and `capped-24-asset-universe` for legend overflow. Screenshot proof for the requester.

## Concrete Steps

From the repository root:

    npm run setup:worktree        # once per fresh worktree (symlinks node_modules)
    npm run gates                 # check + unit tests + websocket suite, PASS/FAIL matrix

For a single fast test cycle while editing:

    npx shadow-cljs compile test && TZ=UTC node out/test.js   # or: npm test

Browser QA:

    npm run dev                   # shadow-cljs watch + dev server on :8080
    # open http://localhost:8080/ui-workbench.html → Optimize → "Equal Risk balance"
    # → designer-parity → click the RISK / RETURN tab

Expected: `npm run gates` prints a matrix with every gate PASS; the new test namespace reports 0 failures; the workbench tab shows the chart described above.

## Validation and Acceptance

Acceptance is behavioral. (1) With an Equal Risk solved result that has current and recommended points plus standalone overlays, the RISK / RETURN tab shows: a chart with visible gridlines and percent tick labels on both axes and the two axis titles; every universe asset as an icon marker with a text label; a dashed vertical line at exactly the current portfolio's volatility and a dashed horizontal line at 0% return; the current portfolio as a white-ringed dot labeled "Current"; the recommended portfolio as a green-ringed dot labeled "Recommended (Equal Risk)" painted above everything; a thin line connecting the two; a legend naming assets plus both portfolio markers; three context boxes above the chart whose numbers equal the result's volatility/return fields as formatted percentages; and no frontier curve anywhere. (2) Hovering any asset icon or either portfolio marker shows the standard callout with Expected Return / Volatility / Sharpe. (3) With no current point, the crosshair, connector, current box, and Current legend entry are absent and everything else still renders. (4) With neither portfolio point, the panel returns nil and the card hides the tab (unchanged contract). (5) `npm run gates` passes; `results_panel_equal_risk_test` still proves no `portfolio-optimizer-frontier-panel` renders for Equal Risk.

## Idempotence and Recovery

Every step is a plain file edit plus re-runnable commands; `npm run gates` and the workbench are safe to run repeatedly. If a milestone goes wrong, `git checkout -- <file>` restores any of the five touched files independently; the panel is pure (result map in, hiccup out), so no state or migration concerns exist. The workbench dev server is stopped with Ctrl-C (or `npm run browser:cleanup` if browser sessions were left behind).

## Artifacts and Notes

Touched files (final):

    src/hyperopen/views/portfolio/optimize/risk_return_context.cljs      (rewrite, 121 → 445 lines)
    src/styles/surfaces/optimizer/results.css                            (+167/−6: optimizer-risk-return-*, dead .optimizer-risk-balance-plot removed)
    test/hyperopen/views/portfolio/optimize/risk_return_context_test.cljs (new, 114 lines, 4 deftests)
    test/hyperopen/views/portfolio/optimize/results_panel_equal_risk_test.cljs (copy fix + 8 chart-role assertions)
    portfolio/hyperopen/workbench/scenes/optimize/equal_risk_scenes.cljs  (fixture :overrides hook + mock-parity designer scene)
    test/test_runner_generated.cljs                                       (auto-registered new test ns)

Gate transcript (2026-07-11):

    npm run gates → 34/34 PASS · 6160 tests · 32797 assertions · 2m13s
    (includes lint:docs on this plan, lint:namespace-sizes, lint:theme-colors,
     all shadow-cljs builds, npm test, test:websocket)

## Interfaces and Dependencies

The public contract is unchanged: `hyperopen.views.portfolio.optimize.risk-return-context/risk-return-panel` takes the solved result map and returns hiccup or nil; `risk-contributions-card` keeps deciding tab presence from that nil. The panel newly depends on (all existing, no new libraries): `hyperopen.views.portfolio.optimize.frontier-chart-model` (`chart-width`, `chart-height`, `plot-geometry`, `chart-bounds`, `chart-grid-stroke`, `chart-axis-stroke`, `domain`, `point-position`, plot edge constants), `…frontier-chart-axes` (`axis-ticks`, `tick-domain`, `x-tick-position`, `y-tick-position`, `tick-label`), `…frontier-overlay-markers` (`marker`), `…frontier-callout` (`point-rows`, `callout`, `hitbox`, `aria-label`), `hyperopen.portfolio.optimizer.application.view-model.frontier` (`overlay-label`, `point-market`), and `hyperopen.views.asset-icon` (`market-icon-url`, legend icons). Data-roles added: `portfolio-optimizer-risk-return-svg`, `-note`, `-zero-line`, `-crosshair`, `-connector`, `-legend`, `-current-box`, `-recommended-box`, `-context-box`; retained: `-context`, `-current`, `-target`, `-sharpe`; retired: the old div-scatter `-asset` role (replaced by the standalone overlay marker roles).

Revision note (2026-07-11, Claude): initial plan authored after the exploration pass, before implementation. Reason: capture intent, decisions, and acceptance before edits begin.

Revision note (2026-07-11, Claude, later same day): Progress checked through Milestone 5 with real evidence; Surprises gained the browser-pane pointer-interception finding and the dex-namespaced icon confirmation; Outcomes & Retrospective written; Artifacts updated with final line counts and the 34/34 gate transcript. The plan stays in `active/` with one open item — moving to `completed/` awaits the requester's acceptance of the shipped tab. Reason: living-document upkeep at the implementation stopping point.
