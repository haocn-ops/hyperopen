# Make Equal Risk diversification visible and correctly labeled

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document is maintained in accordance with `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`.

## Purpose / Big Picture

The Equal Risk results card currently proves that signed Euler risk contributions are close to the equal `1/n` target, but its BREAKDOWN tab calls a variance-normalized cross-covariance residual “Diversification Effect.” A trader naturally reads that phrase as “how much risk diversification removed.” That is not what the number means: it compares the modeled book with a zero-correlation book, so every asset can show a red positive residual even when imperfect correlation still provides meaningful diversification versus all held position profit-and-loss streams moving perfectly together.

After this change, the results card separates two questions. RISK BALANCE answers “did Equal Risk distribute the risk budget evenly?” DIVERSIFICATION answers “how much modeled volatility is below the all-position-profit-and-loss-streams-moving-together benchmark, and how did correlation move risk relative to zero correlation?” The per-asset attribution remains available, but it uses the mathematically precise labels “Own-variance term,” “Cross-covariance effect,” and “Net risk contribution,” draws the second term as an anchored movement from the first term to the net result, and explicitly says that it is attribution at final weights rather than a remove-the-asset counterfactual.

The work is visible in the Equal Risk result card on `/portfolio/optimize/draft` and in the existing Equal Risk correlation workbench scenes. It does not change optimizer weights, constraints, solver behavior, execution behavior, or the meaning of signed Euler contributions.

## Context References

Public refs:

- Direct user/maintainer request dated 2026-07-12: create and execute a plan based on the diagnosis that the Equal Risk breakdown math is correct but its “Diversification Effect” label and zero-correlation baseline are misleading.

Repo artifacts:

- `/hyperopen/docs/design-docs/optimizer-equal-risk.md`
- `/hyperopen/docs/exec-plans/active/2026-07-11-optimizer-equal-risk-correlation-view.md`
- `/hyperopen/docs/exec-plans/completed/2026-07-11-optimizer-breakdown-per-asset.md`
- `/hyperopen/src/hyperopen/portfolio/optimizer/domain/risk_contributions.cljs`
- `/hyperopen/src/hyperopen/portfolio/optimizer/domain/risk_structure.cljs`
- `/hyperopen/src/hyperopen/views/portfolio/optimize/risk_contributions_card.cljs`
- `/hyperopen/src/hyperopen/views/portfolio/optimize/risk_asset_breakdown_panel.cljs`
- `/hyperopen/src/hyperopen/views/portfolio/optimize/risk_breakdown_panel.cljs`

Local scratch refs (non-authoritative):

- `/hyperopen/tmp/multi-agent/equal-risk-diversification-clarity/`

## Progress

- [x] (2026-07-12) Traced the screenshot through signed Euler contribution math, the persisted risk-structure payload, the view model, and both breakdown renderers; reproduced TRUMP’s rounded `2.6% + 3.2% = 5.8%` identity from its `+0.36` position-profit-and-loss correlation.
- [x] (2026-07-12) Froze the product distinction between risk-budget balance, portfolio-level diversification, and final-weight cross-covariance attribution.
- [x] (2026-07-12) Produced and schema-validated acceptance plus edge-case proposals and froze the merged 26-case contract under `tmp/multi-agent/equal-risk-diversification-clarity/approved-test-contract.json`.
- [x] (2026-07-12) Materialized the approved domain, payload, contract, read-model, render, and Playwright tests; `node out/test.js` is RED for the intended missing behavior (`5,627` tests, `30,767` assertions, `56` failures, `12` errors).
- [x] (2026-07-12) Implemented the validated portfolio-diversification summary, fail-closed Equal Risk payload alignment, optional current and required target scalar summaries, and backward-compatible finite result contract.
- [x] (2026-07-12) Implemented the renamed tabs, common-scale portfolio comparison, final-weight attribution copy, anchored own-to-cross-to-net bridge, neutral net/KPI semantics, and economic P&L-correlation colors; focused CLJS is GREEN with 5,627 tests and 30,885 assertions.
- [x] (2026-07-12) Resolved static-review boundary findings: non-flat covariance-degenerate books are unavailable, and current diversification is accepted/rendered only alongside a valid target; focused CLJS is GREEN with 5,632 tests and 30,896 assertions.
- [x] (2026-07-12) Updated the canonical Equal Risk design doc and real-domain-math workbench fixture; added eight committed Playwright scenarios including the opened-Diversification state at `375`, `768`, `1280`, and `1440`.
- [x] (2026-07-12) Resolved responsive QA findings in the scene shell, risk-card tabs, Allocation header, and narrow benchmark rows without changing desktop geometry; focused Playwright is `8/8` PASS.
- [x] (2026-07-12) Completed findings-first static review with no unresolved findings, all six governed browser-QA passes at all four widths, session cleanup, and the final `npm run gates` matrix: `34/34` gates, `6,338` tests, and `34,241` assertions PASS.
- [x] (2026-07-12) Moved this plan to `/hyperopen/docs/exec-plans/completed/` after all acceptance criteria passed.

## Surprises & Discoveries

- Observation: the screenshot is internally exact rather than a sign-rendering failure.
  Evidence: for TRUMP, `sqrt(0.026) * 0.36 = 0.05805`; `0.05805 - 0.026 = 0.03205`, matching the displayed `5.8%` net contribution and `+3.2%` residual after rounding.

- Observation: the current “Standalone Risk” number is not isolated volatility.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/domain/risk_structure.cljs` computes `w_i^2 Sigma_ii / q`, a diagonal variance term normalized by portfolio variance. Held literally alone, an asset’s relative Euler contribution would be 100%; its volatility relative to the modeled portfolio is the square root of this displayed term.

- Observation: the existing all-assets renderer makes addition harder to see than necessary.
  Evidence: `/hyperopen/src/hyperopen/views/portfolio/optimize/risk_breakdown_panel.cljs` starts both component bars at zero and places only the net dot at their sum, even though the active correlation-view plan described an additive stacked segment.

- Observation: current and target weights plus the solved covariance already exist together inside the Equal Risk payload builder.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/application/engine/equal_risk_payload.cljs` receives `:covariance`, `:target-weights`, and `:current-weights`, so the portfolio-level baselines can be computed once at the domain boundary without persisting the covariance or doing optimization math in a view.

- Observation: the frozen malformed-alignment tests exposed that `equal-risk-sections` currently lets vector-index exceptions escape on hand-built misaligned inputs.
  Evidence: the RED run throws `No item ... in vector` before a partial summary can be returned. The implementation must fail closed with a warning or omit the section atomically; valid engine-aligned runs must remain unchanged.

- Observation: existing namespace-size caps required the new comparison and contract logic to remain delegated rather than expanding already-large facades.
  Evidence: focused diversification read-model, view, and contract namespaces keep the existing structure facade, result specs, and target-exposure table within their checked caps; `bb -m dev.check-namespace-sizes` passes.

- Observation: zero cross-covariance has meaningful coincident bridge endpoints but should not render a fabricated visible segment.
  Evidence: the bridge model retains exact start/end data while the renderer omits segments whose economic effect is within `1e-12` of zero.

- Observation: mathematically exact zero volatility from a non-flat singular hedge is not a stable display benchmark for this result payload.
  Evidence: the domain now applies the same covariance-scaled variance-degeneracy tolerance used by signed Euler contribution math, so `[0.5, -0.5]` under `[[1, 1], [1, 1]]` returns `:degenerate-variance` and current comparison is omitted.

- Observation: responsive validation had to exercise the opened Diversification state rather than only the default Risk Balance state.
  Evidence: the first targeted run found a fixed two-column scene shell, then the stateful run exposed fixed benchmark columns and an unwrapped scale note. The committed Playwright path now opens Diversification at every governed width; final document client/scroll pairs are `345/345`, `378/378`, `890/890`, and `1050/1050`.

## Decision Log

- Decision: preserve the Equal Risk solver and signed Euler contribution formulas unchanged.
  Rationale: the diagnosed defect is semantic presentation and a missing benchmark, not optimizer math.
  Date/Author: 2026-07-12 / Codex and user.

- Decision: define the portfolio-level perfect-comovement benchmark as `sum_i abs(w_i) * sigma_i`, the zero-correlation benchmark as `sqrt(sum_i w_i^2 * Sigma_ii)`, and modeled volatility as `sqrt(w' Sigma w)`.
  Rationale: these three values distinguish conventional diversification from the existing cross-covariance residual. They remain valid for signed long/short books because they operate on the held position profit-and-loss streams.
  Date/Author: 2026-07-12 / Codex.

- Decision: compute and persist scalar current/target diversification summaries in the existing `:risk-structure` Equal Risk result section.
  Rationale: the engine owns covariance-dependent math; views should consume a stable persisted result and degrade honestly for older results.
  Date/Author: 2026-07-12 / Codex.

- Decision: retain the existing DOM-state tab identities and selectors while changing their visible labels to RISK BALANCE, DIVERSIFICATION, CORRELATION DRIVERS, and RISK / RETURN.
  Rationale: preserving `contribution`, `breakdown`, `correlation`, and `risk-return` internal identities avoids unnecessary interaction-state and test churn while making the user-facing information architecture truthful.
  Date/Author: 2026-07-12 / Codex.

- Decision: reserve green/red for economic offset/amplification, render net risk shares in neutral target purple, and make the negative-contributor count visually neutral.
  Rationale: the current screens reverse positive/negative color meaning. Risk parity fit is already expressed by deviation magnitude; contribution sign alone should not be called good or bad.
  Date/Author: 2026-07-12 / Codex.

- Decision: keep “re-optimize without this asset at the same gross target” outside this ticket.
  Rationale: it is a causal optimization counterfactual requiring additional solver runs, feasibility/error semantics, latency policy, and persisted comparison data. The present ticket must clearly state that final-weight attribution does not answer that causal question rather than shipping an unsafe approximation.
  Date/Author: 2026-07-12 / Codex.

- Decision: delegate portfolio-comparison and bridge geometry to a focused Equal Risk diversification read-model namespace, while retaining compatibility wrappers in the existing structure facade.
  Rationale: this keeps views free of covariance math, preserves the tested public call surface, and satisfies namespace-size governance without weakening the frozen behavior.
  Date/Author: 2026-07-12 / Codex.

- Decision: make the workbench and production card responsive through wrapping and contained scrolling, not clipping or shorter semantic labels.
  Rationale: the four product questions and native asset selector remain reachable at narrow widths, while desktop keeps the dense comparison layout and existing DOM identities.
  Date/Author: 2026-07-12 / Codex.

## Outcomes & Retrospective

Complete. The UI now separates risk-budget ownership from total modeled volatility, compares current and recommended diversification on one absolute scale, and labels per-asset algebra as final-weight attribution rather than a causal removal claim. The implementation adds focused pure domain, contract, read-model, and rendering namespaces, which increases file count but reduces complexity inside the existing oversized facades and removes the prior semantic ambiguity. It preserves solver weights, signed Euler math, existing internal tab ids, and legacy results without the new summary.

Review found and resolved two boundary defects before closure: a singular non-flat hedge could have been displayed as literal zero modeled volatility, and a current-only partial summary could have passed the contract. Stateful browser QA then exposed responsive issues that initial-state automation missed; those are fixed and covered by committed four-width Playwright assertions. Final evidence is `5,632` CLJS tests / `30,896` assertions, focused Playwright `8/8`, static review PASS, governed and targeted browser QA PASS with session cleanup, and `npm run gates` PASS across `34/34` gates (`6,338` tests / `34,241` assertions). The only intentional non-goal remains the causal “re-optimize without this asset” counterfactual.

## Context and Orientation

Equal Risk sizes already-selected positions so that each position’s signed Euler contribution to total portfolio volatility is close to `1/n`. With covariance matrix `Sigma`, signed weight vector `w`, portfolio variance `q = w' Sigma w`, and `m = Sigma w`, the relative contribution is `w_i * m_i / q`. Contributions sum to one and may be negative. The objective in `/hyperopen/src/hyperopen/portfolio/optimizer/domain/risk_contributions.cljs` minimizes their dispersion around the positive equal target; it does not minimize total portfolio volatility.

`/hyperopen/src/hyperopen/portfolio/optimizer/domain/risk_structure.cljs` derives correlations and the current per-asset decomposition. The diagonal term is `w_i^2 * Sigma_ii / q`. The residual is `net_i - diagonal_i`, equal to the weighted cross-covariance terms for asset `i`, normalized by `q`. This ticket keeps that algebra but gives it precise copy and a visually additive bridge.

`/hyperopen/src/hyperopen/portfolio/optimizer/application/engine/equal_risk_payload.cljs` builds `:risk-contributions`, `:current-risk-contributions`, `:equal-risk-solver`, and `:risk-structure` from final published weights. It is the correct place to calculate target and current portfolio-level summaries because covariance is intentionally not persisted.

`/hyperopen/src/hyperopen/portfolio/optimizer/application/view_model/equal_risk_structure.cljs` joins persisted structure values to instrument identity and prepares pure display models. The new current/target summary model and shared-scale percentages belong here, not in Hiccup render code.

`/hyperopen/src/hyperopen/views/portfolio/optimize/risk_contributions_card.cljs` owns the card shell and tabs. `/hyperopen/src/hyperopen/views/portfolio/optimize/risk_asset_breakdown_panel.cljs` owns the default selected-asset sub-view. `/hyperopen/src/hyperopen/views/portfolio/optimize/risk_breakdown_panel.cljs` owns the all-assets attribution plot. A small delegated view namespace may own the portfolio-level diversification comparison if that keeps the existing panels within namespace-size policy.

Persisted Equal Risk results created before this ticket lack the new current/target scalar summary. They must retain the Risk Balance, per-asset attribution, Correlation Drivers, and Risk / Return views; the portfolio-level summary must quietly omit itself or display an explicit re-run note, never fabricate values.

## Plan of Work

Milestone 1 freezes the behavior with tests. Add pure domain tests proving that two equal long positions with correlation `0.5` have modeled volatility below perfect comovement while both per-asset cross-covariance terms are positive. Add a hedged long/short case, zero-correlation equality, flat-current degradation, and target/current payload coverage. Add view-model and render assertions for the three benchmark labels, truthful tab/copy changes, the anchored bridge start/end values, neutral net encoding, and removal of “Risk if held in isolation” and broad “Diversification Effect” language. Extend the existing Equal Risk correlation workbench Playwright spec to exercise the renamed tabs, portfolio comparison, and additive bridge.

Milestone 2 adds pure portfolio-level math in `domain/risk_structure.cljs`. Define a public summary function over covariance and weights that returns modeled volatility, perfect-comovement volatility, zero-correlation volatility, the signed absolute-volatility reduction, the reduction ratio relative to perfect comovement, and the signed modeled-minus-zero-correlation effect. It returns an explicit error or nil-safe unavailable result for non-finite shapes, degenerate all-zero books, or unstable denominators. Extend `structure-summary` or the Equal Risk payload assembler so target is required and current is optional. Update result contracts and worker round-trip tests without persisting covariance.

Milestone 3 adds the read model and UI. The view model builds current/target cards on one absolute annualized-volatility scale. The DIVERSIFICATION tab leads with a portfolio comparison explaining that Equal Risk balances risk shares and does not minimize total volatility. Each card shows “All move together,” “Zero correlation,” and “Modeled,” plus the percentage lower than perfect comovement and the signed correlation effect versus zero correlation. The selected-asset and all-assets sections use “Own-variance term,” “Cross-covariance effect,” and “Net risk contribution.” The all-assets lane draws own variance from zero, then the cross-covariance segment from the own endpoint to net. The selected asset states whether the held position profit-and-loss stream amplifies or offsets risk and says the view is final-weight attribution, not removal impact.

Milestone 4 aligns semantics and validates. Net contribution bars/markers become target purple; negative-contributor count is neutral; position-profit-and-loss correlation uses green for negative/offsetting and red for positive/amplifying, with text/title support so color is not the only signal. Update the canonical Equal Risk design doc, workbench scene, CLJS tests, and focused Playwright test. Run the focused test slice, repository gates, findings-first review, governed browser QA at all four widths and all six passes, and browser cleanup. Resolve findings through RED/GREEN before closing this plan.

## Concrete Steps

All commands run from `/Users/barry/.codex/worktrees/bde6/hyperopen`.

Bootstrap the worktree before any gate:

    npm run setup:worktree

Materialize and verify the focused RED tests, then re-run them after implementation:

    npx shadow-cljs --force-spawn compile test
    node out/test.js

Run the smallest committed browser test first:

    npx playwright test tools/playwright/test/optimizer-risk-correlation-workbench.spec.mjs --workers=1

Run the required repository matrix:

    npm run gates

Run governed UI QA for the changed optimizer files. Use the exact changed-file list produced by `git diff --name-only`:

    npm run qa:design-ui -- --changed-files <comma-separated-changed-files> --manage-local-app
    npm run browser:cleanup

Move this plan to completed only after every acceptance criterion passes:

    git mv docs/exec-plans/active/2026-07-12-optimizer-equal-risk-diversification-clarity.md docs/exec-plans/completed/2026-07-12-optimizer-equal-risk-diversification-clarity.md

## Validation and Acceptance

For a two-asset equal-long book with unit volatilities and `0.5` correlation, the domain summary reports perfect-comovement volatility `1.0`, zero-correlation volatility approximately `0.7071`, modeled volatility approximately `0.8660`, and diversification benefit approximately `13.4%`, while both existing cross-covariance residuals remain positive. This proves the UI can explain both truths at once.

For a signed hedged pair, the summary uses absolute position-volatility magnitudes for perfect comovement, reports the modeled book below the zero-correlation marker when correlations offset held profit-and-loss streams, and retains signed Euler contributions exactly.

The visible tabs read RISK BALANCE, DIVERSIFICATION, CORRELATION DRIVERS, and RISK / RETURN while their existing DOM radio identities remain stable. RISK BALANCE explicitly says it balances risk ownership and does not measure total risk reduction.

DIVERSIFICATION shows current and recommended books on a common annualized-volatility scale when both are available. Every card labels all-move-together, zero-correlation, and modeled values and states the benefit versus perfect comovement. A target with higher absolute modeled volatility than current remains visibly higher even if its diversification percentage is better.

No visible Equal Risk breakdown calls the diagonal component “Risk if held in isolation” or calls the cross-covariance component a broad “Diversification Effect.” The selected-asset view calls the result final-weight attribution and does not imply a remove-the-asset counterfactual.

The all-assets plot visually encodes `net = own variance + cross covariance`: the cross-covariance segment starts at the own-variance endpoint and ends at the net marker for positive, negative, and zero residuals. The labels, titles, and tests expose both endpoints so this is deterministic rather than screenshot-only.

Green/red communicates offsets/amplifies risk, supplemented by words or direction. Net contribution uses neutral target purple, and the negative-contributor KPI is not intrinsically red or green.

Persisted results without the new summary render without errors and keep the previously available tabs. Empty or degenerate current books omit the current comparison without inventing zero risk.

The focused Playwright spec passes, `npm run gates` passes, static review has no unresolved findings, and each of visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf is PASS at `375`, `768`, `1280`, and `1440`. Browser cleanup succeeds.

## Idempotence and Recovery

All changes are local source, test, workbench, and documentation edits. No external API, database, wallet, or migration is involved. Re-running compiles, tests, Playwright, design review, and cleanup is safe. If a browser run leaves a managed server or session behind, run `npm run browser:cleanup` and `npm run dev:kill`, then retry. If persisted pre-change fixtures fail contracts, preserve their degradation behavior rather than rewriting saved user data.

## Artifacts and Notes

Test proposals, the approved contract, and review artifacts live under `/hyperopen/tmp/multi-agent/equal-risk-diversification-clarity/` and are non-authoritative. Browser artifacts live under `/hyperopen/tmp/browser-inspection/`. The durable behavior, decisions, and validation evidence belong in this plan and `/hyperopen/docs/design-docs/optimizer-equal-risk.md`.

## Interfaces and Dependencies

No new dependency is required. `domain/risk_structure.cljs` must expose a pure portfolio-diversification summary derived only from a covariance matrix and aligned weight vector. `application/engine/equal_risk_payload.cljs` must persist target and optional current scalar summaries inside `:risk-structure`. `application/view_model/equal_risk_structure.cljs` must expose a nil-safe current/target comparison model with common-scale positions. Views may format and render this model but must not recompute covariance math.

Preserve public objective kind `:equal-risk`, result compatibility, existing internal tab ids, selected-risk-instrument action/state, allocation row selectors, correlation matrices, and signed Euler contribution payload fields. Do not add solver runs, network requests, or app state for the new portfolio comparison.

Revision note (2026-07-12): initial plan created from the user-approved diagnosis and freezes a presentation-only Equal Risk clarification with a new pure portfolio-level diversification benchmark; asset-removal counterfactuals remain a separate feature.

Revision note (2026-07-12): acceptance and adversarial proposals were merged into one approved test contract covering the pure benchmark math, payload compatibility, common-scale view model, additive bridge, semantic copy/colors, legacy degradation, and the existing workbench flow.

Revision note (2026-07-12): the approved contract was materialized and verified RED. Failures are confined to the intended new domain/payload/read-model/presentation behaviors plus the newly specified fail-closed alignment path.

Revision note (2026-07-12): recorded completion and GREEN evidence for the worker-owned production slice, the namespace delegation decision, zero-segment rendering behavior, and the remaining browser/documentation validation work.

Revision note (2026-07-12): resolved the two static-review boundary findings by aligning modeled-variance degeneracy with signed Euler tolerance and enforcing target-required/current-optional semantics in both contract and read model.

Revision note (2026-07-12): closed after responsive stateful RED/GREEN coverage, `8/8` focused Playwright, review PASS, all six browser-QA passes at four widths, cleanup, and a final `34/34` repository gate matrix.
