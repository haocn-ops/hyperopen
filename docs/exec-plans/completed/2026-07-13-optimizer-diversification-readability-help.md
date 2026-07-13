# Improve Equal Risk diversification readability and help

This ExecPlan is a living document maintained under `/hyperopen/docs/PLANS.md`. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must stay current while work proceeds.

## Purpose / Big Picture

The consolidated Equal Risk Diversification matrix is structurally clear, but its typography is still optimized for density and its technical benchmarks require prior portfolio-risk knowledge. This change increases the size and line height of the information-bearing text, shortens the two longest outcome labels, and adds concise accessible explanations without changing risk math, comparison geometry, solver behavior, or result payloads.

Durable context: direct maintainer request in this Codex task on 2026-07-13, accompanied by a screenshot of the consolidated Portfolio Diversification matrix, to implement the previously recommended font-size and explanatory-tooltip improvements.

## Progress

- [x] (2026-07-13) Reviewed the maintainer screenshot, current matrix renderer, responsive CSS, and existing tooltip patterns.
- [x] (2026-07-13) Froze the visual, content, and interaction direction.
- [x] (2026-07-13) Materialized deterministic RED tests for six accessible tooltip pairs, exact copy, focus/blur disclosure, shortened labels, and four-width viewport containment. The CLJS suite failed only the six new assertions (`5,642` tests, `31,196` assertions, `6` failures, `0` errors).
- [x] (2026-07-13) Implemented the typography lift, shorter outcome labels, six accessible help disclosures, caller-namespaced IDs, focus-visible treatment, hover-capable-device gating, and focus-preserving Escape dismissal without changing risk calculations or matrix geometry.
- [x] (2026-07-13) Passed focused CLJS and Playwright, the full `34/34` gate matrix, findings-free static review, visual validation, all six browser-QA passes at `375`, `768`, `1280`, and `1440`, `24/24` tooltip interaction cases, touch tap-away, and browser cleanup.
- [x] (2026-07-13) Moved this plan to `docs/exec-plans/completed/` after signoff.

## Surprises & Discoveries

- The first focused Playwright run found that a nominal `100vw` tooltip cap still exceeded the `375px` document edge because the workbench content itself is inset. The production fix reserves the surrounding shell inset in the width budget; the rerun passed containment for all six panels at all four governed widths.
- The first full gate run reached product checks successfully but `lint:docs` rejected the new active plan because its governance sections and durable direct-request reference were missing. This plan now records all three explicitly.
- Independent static and browser review exposed behavior a screenshot cannot: Escape initially had no dismissal path, touch emulation retained sticky hover after tapping away, the section title lost its intended size in the cascade, and the overview tooltip inherited mono because its font declaration referenced an undefined variable. Each became a focused regression or computed-style probe before correction.
- Escape dismissal required a precise lifecycle: keep keyboard focus stable, suppress both hover and focus disclosure for the rest of that focus session, retain suppression when the pointer leaves, then reset on blur so refocus can reopen the help. The final Playwright and live-browser probes cover that full sequence.
- Adding the new contracts pushed the existing semantics test namespace to `529` lines. Rather than add a size exception, the tooltip contracts moved into a focused `131`-line namespace; the original returned to `441` lines and the namespace-size gate passed.

## Decision Log

- Keep help disclosure outside optimizer application state. Each explanation remains in the DOM, is connected to a real button with `aria-describedby`, and uses CSS for presentation plus a local DOM attribute for focus-preserving Escape dismissal; no atom, reducer, persistence path, or optimizer event is introduced.
- Use one overview explanation and one explanation for each of the five rows. This gives the user help at the point of confusion while keeping the matrix itself scannable.
- Raise information-bearing type only one hierarchy step and shorten the two derived-outcome labels. This improves readability without turning the dense trading matrix into a card stack or altering its shared comparison geometry.
- Budget tooltip width against the inset workbench viewport (`calc(100vw - 96px)`) and cap it at `18rem`, based on the governed `375px` containment failure and rerun evidence.
- Gate hover disclosure behind `(hover: hover)` so touch tap-away cannot retain a sticky hover card. Focus disclosure remains available at every pointer capability.
- Accept a caller-provided help-ID prefix and supply one from the risk card, preserving the existing one-argument renderer while preventing ambiguous `aria-describedby` references when a caller intentionally co-renders result summaries.

## Visual, Content, and Interaction Thesis

The visual thesis is the same calm, dense trading comparison with a more readable type hierarchy: labels and numbers become primary, secondary definitions remain quieter, and small help triggers add no ornamental chrome.

The content plan is: retain the one-sentence orientation; add a “How to read this” explanation at the section title; explain each of the three volatility benchmarks and two derived outcomes; shorten the visible outcome labels to “Diversification benefit” and “Correlation effect”; and keep the exact baselines on their secondary lines.

The interaction thesis is application-stateless disclosure. Tooltip content is present in the DOM and linked with `aria-describedby`; CSS reveals it on hover-capable devices and focus-within everywhere, so mouse, keyboard, and tap-focus users receive the same text. A local dismissed attribute handles Escape while preserving focus and resets on blur. No new optimizer event, atom, reducer, animation framework, or persistence path is introduced.

## Decisions

- Raise section title to about `12px`, row labels and numeric values to `12px`, explanatory/decision text to `11.5–12px`, secondary labels to `10px`, and headers/direction copy to `10px`.
- Increase row padding modestly so the larger type does not feel crowded.
- Use a minimum `24px` tooltip trigger target with a restrained `?` glyph.
- Add six tooltips: How to read this, All move together, Zero correlation, Modeled, Diversification benefit, and Correlation effect.
- Keep tooltip panels left-anchored to their label group and bounded to the mobile viewport. They must not affect row geometry while closed.
- Keep green/red confined to the Change column and retain all current marker, point-unit, target-only, and five-column accessibility semantics.

## Tooltip Copy Contract

- How to read this: Current and Recommended share one annualized-volatility scale; Change is Recommended minus Current in percentage points.
- All move together: hypothetical volatility if all held position P&L streams moved together; a stress benchmark, not a forecast.
- Zero correlation: hypothetical volatility if held position P&L streams moved independently.
- Modeled: estimated portfolio volatility using the modeled relationships between positions.
- Diversification benefit: how far modeled volatility is below the all-move-together benchmark; a larger benefit does not necessarily mean lower absolute risk.
- Correlation effect: modeled volatility minus zero-correlation volatility; negative offsets risk and positive amplifies it.

## Acceptance

The matrix remains one comparison component. The larger text is legible without clipping or horizontal overflow at all four governed widths. Each help trigger is a real button with an accessible name and `aria-describedby` pointing to one `role="tooltip"` node. Hover and keyboard focus reveal the correct tooltip; Escape dismisses it without moving focus, pointer movement does not reopen it during that focus session, and blur/refocus resets it. The tooltip stays inside the viewport and does not obscure the trigger. The shortened outcome labels retain their baseline meaning on the secondary line. Existing risk values, marker positions, decision summary, semantic tones, target-only behavior, and attribution interactions remain unchanged.

## Validation

Run from `/Users/barry/.codex/worktrees/bde6/hyperopen`:

    npx shadow-cljs --force-spawn compile test && node out/test.js
    npx playwright test tools/playwright/test/optimizer-risk-correlation-workbench.spec.mjs --workers=1
    npm run gates
    npm run browser:cleanup

## Outcomes & Retrospective

The matrix now renders a `12px` section title, `12px` row labels and values, `11.5px` explanatory and decision copy, `10px` secondary/header/direction copy, and `10.5px` legend copy. Six `24px` help buttons explain the overview, three benchmarks, and two derived outcomes. “Diversification benefit” and “Correlation effect” replace the two long primary labels while their baselines remain visible beneath them.

The final disclosure is responsive and accessible across the governed surface: exact copy and ARIA relationships pass, all 24 four-width tooltip cases remain in bounds, touch tap-away closes every tooltip, Escape preserves focus and dismissal through pointer movement, blur/refocus reopens it, and matrix dimensions do not change. The static reviewer and UI visual validator returned PASS with no actionable findings. Browser evidence lives at `tmp/multi-agent/equal-risk-diversification-readability-help/browser-report.json` and `tmp/browser-inspection/equal-risk-readability-tooltips-final-reset-2026-07-13/evidence-summary.json`.

Validation completed with focused Playwright `9/9`, CLJS `5,643` tests / `31,199` assertions, websocket `546` tests / `3,133` assertions, and the final `npm run gates` matrix `34/34` (`6,349` tests / `34,544` assertions overall). `git diff --check` and browser cleanup passed.

Completion confidence: `99%` (`40/40` testing, `30/30` code review, `29/30` logical inspection), above the required `84.7%` threshold. Residual blind spots are physical iOS/Android focus heuristics, screen-reader/browser announcement combinations, and localization/font scaling beyond the governed viewport matrix.

Revision note (2026-07-13): created from the maintainer-approved readability and tooltip recommendation; completed after governed implementation, review, and browser QA.
