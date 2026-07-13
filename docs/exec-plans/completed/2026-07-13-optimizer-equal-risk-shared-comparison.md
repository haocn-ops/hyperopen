# Consolidate Equal Risk current and recommended diversification

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document is maintained in accordance with `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`.

## Purpose / Big Picture

The Equal Risk Diversification tab currently places Current and Recommended in two structurally identical cards. A user must scan left to right, remember one value, find its counterpart, and then infer whether the difference is desirable. After this change, one shared comparison matrix aligns each benchmark row across Current, Recommended, and Change, draws both portfolio markers on the same rail, and ends with a plain-language decision summary. The result should make both the modeled-volatility improvement and any increase in the all-move-together stress benchmark obvious in one scan.

This is a presentation and read-model change. It must not change covariance math, saved result payloads, solver behavior, optimizer weights, signed Euler contributions, or the per-asset attribution below the portfolio comparison.

## Context References

Public context is the direct maintainer request dated 2026-07-13 to implement the approved consolidation recommendation. The predecessor implementation and math are documented in `/hyperopen/docs/exec-plans/completed/2026-07-12-optimizer-equal-risk-diversification-clarity.md` and `/hyperopen/docs/design-docs/optimizer-equal-risk.md`.

The production seams are `/hyperopen/src/hyperopen/portfolio/optimizer/application/view_model/equal_risk_diversification.cljs`, `/hyperopen/src/hyperopen/views/portfolio/optimize/risk_diversification_summary.cljs`, and `/hyperopen/src/styles/surfaces/optimizer/results.css`. The deterministic browser fixture is `/hyperopen/portfolio/hyperopen/workbench/scenes/optimize/equal_risk_correlation_scenes.cljs`, and its committed browser coverage is `/hyperopen/tools/playwright/test/optimizer-risk-correlation-workbench.spec.mjs`.

Local workflow artifacts under `/hyperopen/tmp/multi-agent/equal-risk-shared-comparison/` are non-authoritative.

## Progress

- [x] (2026-07-13) Confirmed the current two-card renderer, common absolute scale, legacy degradation path, target-only path, and existing four-width workbench coverage.
- [x] (2026-07-13) Froze the visual, content, interaction, and semantic direction in this plan.
- [x] (2026-07-13) Produced and approved acceptance plus edge-case test proposals in the local workflow artifact directory.
- [x] (2026-07-13) Materialized RED tests for the shared read model, renderer, semantics, and four-width browser behavior; Shadow compile passed with zero warnings and the test run failed in the expected 63 assertions with zero errors.
- [x] (2026-07-13) Implemented the shared matrix, responsive styling, coherent workbench tradeoff fixture, and canonical design documentation without changing result payload or optimizer math.
- [x] (2026-07-13) Ran focused tests, Playwright, the full gate matrix, findings-first review, all six browser-QA passes at `375`, `768`, `1280`, and `1440`, and browser cleanup.
- [x] (2026-07-13) Moved this plan to `/hyperopen/docs/exec-plans/completed/` after every acceptance criterion passed.

## Surprises & Discoveries

- Observation: the existing read model already places both cards on one absolute scale, so no domain or payload change is required.
  Evidence: `comparison-model` derives one `scale-max` across current and target and stores per-benchmark positions for each card.

- Observation: the current workbench recommendation deliberately has higher all-move-together stress while achieving lower modeled volatility.
  Evidence: this is the tradeoff the new decision summary must preserve; it must not color the recommendation globally as simply good.

- Observation: the approved RED slice failed without compile errors.
  Evidence: 5,636 tests and 30,970 assertions completed with 63 expected failures and zero errors, all localized to the absent shared-row model and matrix renderer.

- Observation: the original workbench current book could not demonstrate the planned decision tradeoff.
  Evidence: its generated current weights made both modeled volatility and all-move-together stress lower than the recommendation. The scene now supplies an explicit concentrated current book; the same production domain math computes Current modeled volatility at 28.65% versus Recommended 23.36%, while Current all-move stress is 31.25% versus Recommended 48.86%.

- Observation: independent review found visual, semantic, and accessibility problems that DOM-count tests alone did not expose.
  Evidence: the corrective passes added collision-aware marker geometry, signed point units derived from displayed endpoints, sign-aware correlation language, target-only rendering coverage, and a valid five-column ARIA table contract. Final static review and visual validation both passed with no findings.

## Decision Log

- Decision: render one cardless comparison matrix instead of two portfolio cards.
  Rationale: aligned rows reduce eye travel and make each current-to-recommended change directly comparable.
  Date/Author: 2026-07-13 / Codex and maintainer.

- Decision: keep three absolute-volatility benchmark rows—All move together, Zero correlation, and Modeled—with one common rail per row.
  Rationale: a gray outlined Current marker, purple filled Recommended marker, and neutral connector show direction and magnitude without conflating portfolio identity with good/bad judgment.
  Date/Author: 2026-07-13 / Codex.

- Decision: put Diversification versus all-move-together and Correlation effect versus zero in aligned outcome rows without absolute-volatility rails.
  Rationale: those values are ratios/effects with different baselines and would be misleading on the absolute-volatility scale.
  Date/Author: 2026-07-13 / Codex.

- Decision: reserve green/red for the semantic direction of each Change cell.
  Rationale: lower volatility/stress is favorable, a larger diversification benefit is favorable, and a more-negative correlation effect is more offsetting. Current stays gray and Recommended stays purple.
  Date/Author: 2026-07-13 / Codex.

- Decision: preserve the existing comparison-model `:cards` data while adding shared-row fields.
  Rationale: this preserves the established read-model surface while allowing the renderer and new tests to consume the clearer matrix representation.
  Date/Author: 2026-07-13 / Codex.

## Outcomes & Retrospective

The two repeated comparison cards are now one matrix with three paired benchmark rails, two aligned outcome rows, and one tradeoff sentence. The shared layout lowers recall burden while retaining the distinct Current and Recommended identities, including when markers are close. Change values now use percentage points, correlation wording reflects whether the effect is amplifying or offsetting, and the matrix exposes five-column assistive semantics.

Final validation is green. The focused CLJS suite ran 5,641 tests and 31,186 assertions with zero failures or errors; focused Playwright passed 8/8, including all governed widths. The final repository gate matrix passed 34/34 with 6,347 tests and 34,531 assertions. Static review and visual validation passed with no findings. Browser QA passed visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf at `375`, `768`, `1280`, and `1440`; cleanup left zero browser-inspection sessions.

Completion confidence is 98.5%: testing 40/40, review 30/30, and logical inspection 28.5/30. The remaining blind spot is minor browser/screen-reader announcement variance because the deterministic contract validates ARIA structure but does not snapshot every assistive-technology accessibility tree. Optimizer math, payloads, solver behavior, and removal-impact analysis remain explicit non-goals.

## Visual, Content, and Interaction Thesis

The visual thesis is a calm, dense trading comparison: one bordered matrix, restrained dividers, one purple identity accent, and no card mosaic. The component should be readable by scanning only row labels and the Current, Recommended, and Change columns.

The content plan has four jobs in order. First, orient the user with “Portfolio diversification.” Second, compare the three absolute-volatility benchmarks. Third, compare the two diversification outcomes. Fourth, state the decision implication in one concise summary before the existing final-weight attribution details.

The interaction thesis is stability. This matrix introduces no app state, animation framework, or new control. Existing tab, selected/all-assets, asset selection, and correlation-mode interactions remain unchanged. Hover or focus emphasis may clarify a row or connector, but it must be subtle and respect reduced motion.

## Context and Orientation

The result payload stores `:target-diversification` and optionally `:current-diversification` inside `:risk-structure`. Each summary supplies modeled volatility, all-move-together volatility, zero-correlation volatility, diversification benefit relative to all-move-together, and the signed modeled-minus-zero-correlation effect. The target summary is required whenever the new fields are present; a current summary can be absent for flat, degenerate, or unavailable current books.

The view model formats no Hiccup. It should expose three benchmark rows with raw current/target values, their shared-scale positions, signed changes, and semantic tones. It should also expose two outcome rows and a decision-summary model. The renderer formats the final percentages and creates stable `data-role` hooks. Views must not recompute covariance or optimization math.

## Plan of Work

Milestone 1 freezes the shared comparison behavior. Add tests proving row order, common-scale marker positions, signed deltas, semantic tone, target-only degradation, and the decision summary for both the workbench tradeoff and an all-improving case. Render tests must prove there is one matrix, not Current and Recommended cards, and that the legend and all five rows are present. Extend the committed Playwright path to verify aligned values, marker identities, summary copy, and no overflow after opening Diversification at all four governed widths.

Milestone 2 extends `equal_risk_diversification.cljs` without changing inputs. Preserve `:cards`, then add shared benchmark rows, outcome rows, marker/connector geometry, change metadata, and a nil-safe summary. Percentage changes must fail closed when a current denominator is absent or zero. Target-only data shows em dashes for Current and Change, one Recommended marker, and an honest current-unavailable summary.

Milestone 3 replaces the two-card Hiccup in `risk_diversification_summary.cljs` with one semantic comparison section. Each absolute benchmark row contains one rail, optional Current marker, Recommended marker, and optional connector. Numeric columns show Current, Recommended, and Change. Outcome rows use the same aligned numeric columns without pretending they share the absolute rail. The final sentence must state the modeled-volatility direction and the all-move-together stress direction; it must never hide a worsened stress benchmark behind a global success color.

Milestone 4 updates responsive CSS, the workbench assertions, and the canonical design doc. Desktop keeps the dense aligned matrix. Narrow layouts keep each benchmark together as a paired row with the rail and values beneath its label; they never recreate separate Current and Recommended cards. Finish with focused CLJS, Playwright, repository gates, review, governed browser QA, and cleanup.

## Concrete Steps

Run all commands from `/Users/barry/.codex/worktrees/bde6/hyperopen`.

    npm run setup:worktree
    npx shadow-cljs --force-spawn compile test
    node out/test.js
    npx playwright test tools/playwright/test/optimizer-risk-correlation-workbench.spec.mjs --workers=1
    npm run gates
    npm run qa:design-ui -- --changed-files <comma-separated-changed-files> --manage-local-app
    npm run browser:cleanup

After all acceptance criteria pass, move this file to `/hyperopen/docs/exec-plans/completed/2026-07-13-optimizer-equal-risk-shared-comparison.md`.

## Validation and Acceptance

The Diversification tab contains one comparison matrix and no separate Current or Recommended cards. Its visible columns are Benchmark, Current, Recommended, and Change, with an unlabeled shared visual rail between Benchmark and the numeric values where space permits.

All move together, Zero correlation, and Modeled each use one absolute-volatility rail. Current is an outlined neutral marker, Recommended is a filled purple marker, and the connector joins their exact positions. Their values and signed changes are aligned in columns.

Diversification versus all-move-together and Correlation effect versus zero appear as aligned outcome rows. A larger diversification benefit is favorable; a more-negative correlation effect is more offsetting. Copy supplements color.

For the workbench fixture, the decision summary says modeled volatility falls while all-move-together stress rises, and includes the current and recommended values. For other direction combinations, wording changes truthfully. Target-only results show Recommended, em dashes for unavailable Current/Change, and no fabricated zero marker or percentage change.

The legend reads Current and Recommended rather than “Shared absolute volatility scale.” Existing per-asset attribution, tabs, internal radio ids, and asset selection continue to work.

At `375`, `768`, `1280`, and `1440`, opening Diversification causes no document overflow, clipped values, overlapping labels, or inaccessible markers. The focused Playwright spec passes, all six governed browser-QA passes are PASS, browser cleanup succeeds, static review has no unresolved findings, and `npm run gates` passes.

## Idempotence and Recovery

The work changes only local read-model, view, CSS, test, workbench, plan, and documentation files. No network write, migration, wallet action, or stored-result rewrite is involved. Re-running compiles, tests, browser checks, and cleanup is safe. If a managed browser server remains, run `npm run browser:cleanup` and `npm run dev:kill`, then retry.

## Interfaces and Dependencies

No dependency is added. Preserve the public Equal Risk result payload and solver. Preserve `diversification-comparison-model` and its existing `:scale-max` and `:cards` fields while adding shared comparison fields. Keep all geometry and semantic direction pure and deterministic in the view model. Hiccup consumes the model and CSS owns layout only.

Revision note (2026-07-13): initial plan created from the maintainer-approved consolidation recommendation; it freezes one shared matrix, explicit tradeoff copy, target-only degradation, and four-width stateful browser coverage.
