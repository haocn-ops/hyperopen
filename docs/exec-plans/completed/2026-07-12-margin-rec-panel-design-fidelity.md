# Margin recommendation panel: designer-spec redesign + probability-vs-collateral chart

This ExecPlan is a living document maintained in accordance with `/.agents/PLANS.md`. It is kept current as work proceeds; Progress, Surprises & Discoveries, and the Decision Log are updated as milestones land.

## Purpose / Big Picture

The isolated-margin recommendation panel shipped on 2026-07-12 (`docs/exec-plans/active/2026-07-12-isolated-margin-collateral-recommendation.md`) as a functional but visually flat stack of tiles plus a breakdown table. The product owner reviewed it against the designer's mockup ("Margin recommendation" card) and asked, by direct request on 2026-07-12, for the displayed panel to match the designer's proposal much more closely — explicitly including the "Modeled probability of liquidation vs. collateral" chart, which makes the *why* of the recommendation legible: the user sees where their current margin sits on the p_liq curve and where the recommended margin moves them.

This plan restyles the panel content to the mockup's structure and ships the curve. The popover mechanics (anchored positioning, focus restore, Escape/close handling, data flow, actions) are kept; only the rendered content and one engine output addition change.

## Context References

- Parent ExecPlan: `docs/exec-plans/active/2026-07-12-isolated-margin-collateral-recommendation.md` (engine math, orchestration, contract surface; its deferred "probability-vs-collateral curve" item is delivered here).
- Direct user request (product owner), 2026-07-12, with the designer mockup image (single tall card: header + coin/leverage row, current-state stat grid, green recommendation block, p_liq-vs-collateral chart with Current/Recommended markers, "How we estimated this" / "Buffers included" two-column, target-risk selector, Apply recommendation / Set custom margin buttons).
- Panel view: `src/hyperopen/views/account_info/margin_recommendation_panel.cljs`; view test: `test/hyperopen/views/account_info/margin_recommendation_panel_test.cljs`.
- Engine finalize: `src/hyperopen/margin_rec/recommend.cljs` (`finalize` receives the full sorted required-equity distribution — the curve is `prob-above` sampled over a collateral grid).
- SVG chart conventions: `src/hyperopen/views/portfolio/optimize/black_litterman_preview_chart.cljs` (viewBox + hiccup SVG, `fontSize` attrs, data-roles, currentColor text).
- Workbench scene precedent: `portfolio/hyperopen/workbench/scenes/account/positions_scenes.cljs`; Playwright-on-workbench precedent: `tools/playwright/test/optimizer-volatility-intuition-workbench.spec.mjs`.

## Progress

- [x] (2026-07-12) Exploration: current panel/tests, engine finalize output, chart + workbench conventions mapped.
- [x] (2026-07-12) Engine: `finalize` gains `:curve` (sampled p_liq(E) points + x-max), `:paths-count`, and `:sigma :annualized` (365-day convention); unit tests.
- [x] (2026-07-12) Panel redesign: header/coin row, current-state stat grid, recommendation highlight block, chart (split into `margin_rec_curve.cljs` for the namespace-size gate), methods/buffers columns, restyled risk selector, Apply recommendation + Set custom margin buttons.
- [x] (2026-07-12) View contract tests updated to the new tree (roles, copy, actions, chart presence/absence).
- [x] (2026-07-12) Workbench scenes (`margin_recommendation_scenes.cljs`, 4 scenes) + Playwright workbench spec pinning the browser-rendered layout.
- [x] (2026-07-12) Gates green (`npm run gates` 34/34) + `margin-rec-panel-workbench.spec.mjs` 4/4 passing against the compiled worktree workbench on :8090; panel screenshots captured and shared with the owner.

## Surprises & Discoveries

- Observation: the engine already computes everything the chart needs — `finalize` holds the full sorted required-equity distribution, so p_liq(E) is a binary search per sample point (`paths/prob-above`); no simulation change is required, only surfacing ~50 sampled points in the result.
- Observation: recommendation results are plain app-db values produced in-page by the lazy `:margin_rec` module (no wire codec), so adding `:curve`/`:paths-count` keys is contract-free; the view must only tolerate cached results that predate the keys (chart section omitted when `:curve` is absent).
- Observation: Tailwind's `content` globs scan only `src/**` and `resources/public/**/*.html` — a class used only in a `portfolio/**` workbench scene (e.g. `min-h-[1100px]`) silently never exists in the built CSS. Scene-only sizing must be inline `:style`.
  Evidence: the canvas iframe collapsed to 190 px until the scene's min-height moved from a class to `:style`.
- Observation: camelCase SVG attributes (`:fontSize`, `:fontWeight`) are dropped in the rendered DOM here — text falls back to 16 px; kebab-case (`:font-size`, `:font-weight`) applies. `hyperopen.views.portfolio.optimize.black-litterman-preview-chart` still uses `:fontSize` and is likely rendering its axis/legend text at the default size.
- Observation: the theme-colors ratchet counts raw color literals per file against a frozen baseline; the chart's strokes/fills had to be expressed as `rgb(var(--ho-*) / a)` tokens (`--ho-warn` is exactly the amber the mockup uses).

## Decision Log

- Decision: keep the anchored-popover shell and swap only the content.
  Rationale: the modal-vs-popover question was settled deliberately on this branch (commit b11043e4, per docs/FRONTEND.md: page-local recoverable controls are anchored popovers); the owner's complaint is visual composition, not anchoring. The mockup's card fits the existing 460–480 px popover width.
  Date/Author: 2026-07-12, feature branch owner request interpreted by implementer.
- Decision: compute the curve in `finalize` (engine) rather than shipping the raw distribution to the view.
  Rationale: the sorted distribution is a Float64Array of up to 4000 entries per position and would bloat app-db and any state snapshot; ~50 {collateral, probability} points render identically. Sampling in `finalize` keeps the engine's "one pass, everything derived from the sorted distribution" property auditable in one place.
  Date/Author: 2026-07-12.
- Decision: curve x-domain = 0 → nice-ceiling(2 × recommended equity, 1-2-5 ladder), matching the mockup's $0–$40 span for an $18.64 recommendation; the y-axis is fixed 0–100%.
  Rationale: 2× the recommendation always shows the flat tail past the recommended marker (the "diminishing returns" story the designer is telling); a 1-2-5 nice ceiling gives round dollar ticks.
  Date/Author: 2026-07-12.
- Decision: "Buffers included" lists the four named buffers (adverse-path, funding, exit/slippage, model uncertainty) with dollar amounts and % of the recommended total, omitting the maintenance-requirement row the old breakdown table showed.
  Rationale: matches the mockup exactly; maintenance is not a buffer (it is the exchange's floor, already expressed by the liquidation price and the chart's left wall). The engine breakdown still contains it and still sums exactly; the panel is a presentation choice.
  Date/Author: 2026-07-12.
- Decision: "Set custom margin" replaces the "Reduce position instead" button; it opens the existing Adjust Margin modal unprefilled. Reduce remains one click away on the position row itself.
  Rationale: mockup fidelity (its two actions are Apply recommendation / Set custom margin); the reduce popover trigger already exists on the row, so no capability is lost.
  Date/Author: 2026-07-12.
- Decision: "How we estimated this" rows surface only quantities the engine actually uses: the 365-day annualized realized vol (sigma-hourly × √8760, the same calendar convention as the optimizer's volatility card), the real simulated path count, and the trade-history-derived horizon with its episode count. No mockup copy is shown for anything not computed.
  Rationale: standing honesty policy (docs/agent-guides/trading-ui-policy.md); the mockup's "10,000 paths" is replaced by the true count.
  Date/Author: 2026-07-12.

## Outcomes & Retrospective

Shipped 2026-07-12 on this branch. The panel now renders the designer's card: title + coin/leverage header, 3+2 current-state stat grid (bordered, divided cells), green recommendation block with the "+X% vs current" chip, the probability-vs-collateral SVG chart with boxed Current/Recommended markers and dashed drop lines, "How we estimated this" / "Buffers included" columns, boxed risk-mode selector with the Settings caption, and full-width Apply recommendation / Set custom margin buttons. Engine `finalize` emits `:curve` (50 points, 1-2-5 nice x-max = 2x the recommendation), `:paths-count`, and 365-day annualized sigma, all derived from the existing sorted distribution in one place.

Validation: `npm run gates` 34/34 PASS (6311 tests / 33977 assertions); new curve invariants in `recommend_test.cljs`; rewritten panel contract test; `margin-rec-panel-workbench.spec.mjs` 4/4 against the compiled workbench (marker geometry, within-target, cached-no-curve). Liquidation prices switched to `shared/format-trade-price` for two-decimal display.

Deliberate departures from the mockup, recorded in the Decision Log: real path count instead of "10,000 paths"; buffers list omits the maintenance row (not a buffer); "Reduce position instead" replaced by the mockup's "Set custom margin" (reduce stays on the row); anchored popover retained instead of a modal.

## Context and Orientation

The panel is opened from the positions-row risk chip (`:actions/toggle-margin-rec-panel`), receives `{:position-key :rec :row-vm :read-only? :risk-mode :anchor}` from `tabs/positions/desktop.cljs`, and renders `rec`'s `[:margin-rec :recs <key>]` entry. `rec-result` is the `finalize` output documented in the parent plan. The apply action opens the existing Adjust Margin modal prefilled (`:actions/open-position-margin-modal` with `:prefill-margin-amount`); this stays byte-identical. Leverage for the header row comes from the row position map (`[:leverage :value]` + margin-mode label, e.g. "10x isolated").

Chart data: for sorted required-equity distribution D, p_liq(E) = `paths/prob-above D E`. `finalize` samples E over 50 evenly spaced points on [0, x-max] and emits `{:x-max <usd> :points [{:e <usd> :p <0..1>} …]}` under `:curve`, plus `:paths-count` (= `(alength sorted-required)`).

## Plan of Work

1. `src/hyperopen/margin_rec/recommend.cljs`: nice-ceiling helper (1-2-5 ladder), `:curve`, `:paths-count`, `:sigma :annualized` in `finalize`. Tests in `test/hyperopen/margin_rec/recommend_test.cljs`: curve monotone non-increasing, endpoints (p(0) = 1 when every path needs equity, tail ≤ p-after), x-max ≥ 2×recommended equity, sample count, paths-count equals requested path count.
2. `src/hyperopen/views/account_info/margin_recommendation_panel.cljs`: rebuild `ready-panel` per the mockup; add the SVG chart (hiccup, viewBox, data-roles per element group, currentColor-tinted marker groups); restyle the risk selector; new action buttons; keep terminal states and shell mechanics. Chart omitted when `:curve` missing.
3. `test/hyperopen/views/account_info/margin_recommendation_panel_test.cljs`: update fixture (add `:curve`/`:paths-count`/annualized sigma), assert the new roles/copy/actions, assert chart absence for cached results without `:curve`, keep desktop-row chip test unchanged.
4. Workbench: `portfolio/hyperopen/workbench/scenes/account/margin_recommendation_scenes.cljs` with ready / within-target / no-curve scenes; `tools/playwright/test/margin-rec-panel-workbench.spec.mjs` pinning visible layout + marker ordering.
5. Parent plan bookkeeping: check off its deferred curve item with a pointer here.
6. Validation: `npm run setup:worktree`, `npm run gates`, targeted Playwright spec, screenshots.

## Validation and Acceptance

- Unit/contract: updated view test passes; new engine curve tests pass; no contract-surface changes required (no new actions/effects).
- Gates: `npm run check`, `npm test`, `npm run test:websocket` green (`npm run gates` matrix all PASS).
- Browser: the workbench Playwright spec renders the real panel and asserts the chart, marker ordering (current marker left of recommended), stat values, buffer percentages, and both action buttons; screenshot review against the mockup.

## Idempotence and Recovery

Pure view + pure engine-output additions; recompute remains signature-gated. Cached results lacking `:curve` degrade gracefully (no chart) and refresh on the next natural recompute.

## Interfaces and Dependencies

Consumes existing: `rec-result` shape from `finalize`, `paths/prob-above`, `:actions/open-position-margin-modal`, `:actions/set-margin-rec-risk-mode`, `:actions/close-margin-rec-panel`, anchored-popover layout helper. Provides: `:curve`/`:paths-count`/`:sigma :annualized` keys on recommendation results; redesigned panel DOM (new `margin-rec-*` roles pinned by tests); workbench scenes + spec.
