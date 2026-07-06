# Proxy baskets: surface the model-adjusted split, never the naive prior

Date: 2026-07-05
Status: in progress
Surface: `domain/history_assumption_proxy.cljs` exposure pipeline +
`/portfolio/optimize/new` history-assumption cards, right rail, and the results
Inputs tab (view-model `view_model/setup.cljs`, `view_model/scenario.cljs`;
views `setup_history_assumptions.cljs`, `inputs_tab.cljs`; wire codec).

## Purpose / Big Picture

External finance-expert review (2026-07-05): when a user selects ETH + BTC as
proxies for a short-history asset, the setup card shows "ETH 50% / BTC 50%"
labeled "Initial qualitative weights (adjusted by model)" — the equal-weight
*prior* presented as if it were the *final* modeled basket. The product rule the
review demands:

> Qualitative prior chooses the proxy set. Quantitative regression adjusts the
> proxy split. Confidence governs how much the regression can move the prior.
> Specific risk and caps keep the optimizer honest.

Audit finding that reframes the work: **the engine already implements the
requested algorithm.** `domain/history_assumption_proxy.cljs` runs ONE
multi-proxy ridge regression toward the prior (never per-proxy R² weights),
computes `q = min(1, n/(n+120)) · min(1, R²/0.5)`, blends
`β_final = q·β_reg + (1−q)·β_prior`, and `β_final` — not the prior — drives the
synthesized covariance row (`Cov(A,X) = Σ_AP·β_final`,
`Var(X) = β_finalᵀΣ_PPβ_final + specific`). So this plan is NOT a modeling
rewrite. The real gaps:

1. **No prior source labeling.** The prior builder returns `:fallback?` only;
   nothing distinguishes user-supplied weights from the equal fallback, and the
   UI copy claims the shown weights are "adjusted by model" when they are not.
2. **No pre-run visibility of the adjusted split.** The card never shows the
   regression estimate, confidence q, or the final modeled basket — even though
   the readiness request already carries everything needed to compute them
   (`[:request :history-assumptions <id>]` holds the flattened entry with
   `:regression-series` from the request builder's sub-alignment).
3. **Regression floor too permissive.** `min-regression-observations = 4`;
   expert recommends `max(10, k+3)` so a handful of points never counts as
   model evidence (q would stay ≤0.077 anyway, but the skip should be honest).
4. **Diagnostics gaps.** Missing `:prior-source`, `:regression-beta-raw`,
   `:effective-modeled-volatility` (total modeled vol can exceed the user's
   input when systematic + specific floors lift it).
5. **No results disclosure.** The Inputs tab lists the selected proxies but not
   the prior/regression/final story the run actually used.

## Context References

- Direct user request (2026-07-05 session), carrying an external finance-expert
  brief: "Implement model-adjusted proxy basket weights for selected proxy
  assets" — product decisions adopted; implementation shaped to the existing
  engine-backed architecture.
- Parent ExecPlan: `docs/exec-plans/active/2026-07-05-optimizer-proxy-history-assumptions.md`
  (engine-backed proxy behavior; the math this plan makes visible).
- Sibling honesty fix: `docs/exec-plans/active/2026-07-05-optimizer-proxy-covariance-window-honesty.md`
  (same pattern: the engine was right, the display was not).
- Wire-codec contract: result diagnostics cross the worker boundary; new
  instrument-keyed maps and enum keywords must be registered in
  `instrument_keyed_codec.cljs` or they silently corrupt.

## Decision Log

- **Reuse, don't rewrite.** The expert's algorithm (§3–§6 of the prompt) is
  already live in `history_assumption_proxy.cljs`. The work is a source-labeled
  prior seam, a shared exposure-pipeline entry point, thresholds, diagnostics,
  and honest UX. Anti-goals (no R²-as-weights, no per-proxy regressions, no
  clone treatment, no tiny-sample dominance) are already enforced and pinned by
  tests; they gain new pins, not new code.
- **One pipeline, two callers.** New pure `model-exposure` composes
  prior → regression → confidence → blend and returns the full story
  (`:prior-weights :prior-source :regression :confidence-q :final-beta`).
  `synthesize-asset` (engine) and the setup view-model preview both call it, so
  the card preview and the run agree by construction. The preview feeds it the
  SAME inputs the engine will see: the readiness request's flattened entry and
  the selected∩usable proxy set (falls back to the draft entry with no series →
  prior-only preview).
- **`build-proxy-prior` replaces `normalize-prior-weights`.** Output
  `{:weights :source :warnings}` with `:source :user | :equal`. Metadata-scored
  priors are NOT faked (no metadata exists in the app today); the fn is the
  documented extension point where a `:metadata` source slots in between. No
  `target-id` param — self-proxying is rejected upstream
  (`first-missing-proxy-field :self-proxy`).
- **Threshold: `min-regression-observations` becomes `max(10, k+3)`** (fn of
  proxy count, was constant 4). Existing tests unaffected (n=2 still skips;
  the 10-day engine fixture with k=2 still estimates and pins q < 0.08).
- **Diagnostics:** add `:prior-source` (enum), `:regression-beta-raw`
  (instrument-keyed), `:effective-modeled-volatility`
  (`sqrt(systematic + specific)`); rename `:volatility` → `:input-volatility`
  (no external readers of the old key; payload is days old). Codec updated for
  the two new key classes. Invalid user priors emit a
  `:proxy-prior-weights-invalid` warning on top of the equal fallback.
- **No user prior-weight editor in this pass.** The draft field
  (`[:proxy :prior-weights]`) stays reserved; the domain + request path honor
  and source-label it end-to-end, so a future editor is a UI-only change. This
  also means NO new actions/effects — the action/effect contract surface (specs,
  Lean formal sync) is untouched.
- **Card = three stacked mini-panels** (Prior basket + source line; Regression
  estimate with R²/n or the skip reason; Final modeled basket + confidence q,
  visually emphasized) and the summary strip becomes the final basket:
  "Final model: ETH 60% / BTC 40% + specific risk + 5% cap". Confidence tier on
  the card/rail switches from the n-only upper bound to the real preview q
  (thresholds 0.33/0.66 unchanged).
- **Inputs tab discloses what the RUN used**, read from the last successful
  run's `[:risk-estimation :history-assumptions]` payload, guarded per-asset
  (behavior `:proxy`, diag proxy set ⊆ current draft selection) so a post-run
  edit can't show a stale basket as current. Includes the expert's note copy:
  "Proxy weights are regression-adjusted and confidence-shrunk. R² was not used
  as a direct weight."
- **Namespace split instead of size-exception bumps.** The card + rail
  projections had grown `view_model/setup.cljs` to 971 lines against an 875
  exception whose own reason text prescribed this split. They now live in
  `view_model/setup_history_assumption_cards.cljs` (~457) and
  `view_model/setup_history_assumption_rail.cljs` (~73); `setup.cljs` is back
  under the 500 default and both stale exception entries are removed. The
  boundary test split the same way
  (`view_model_history_assumption_cards_test.cljs`). The `view-model` facade
  keeps the public entry points stable, so views and tests of views are
  untouched by the move.

## Progress

- [x] Domain (`domain/history_assumption_proxy.cljs`): `build-proxy-prior`
  (source-labeled, warns on rejected user weights), `min-regression-observations`
  as `max(10, k+3)`, `model-exposure` composition, `synthesize-asset` delegates
  to it and emits the new diagnostics fields.
- [x] Wire codec: `:regression-beta-raw` → instrument-keyed;
  `:prior-source` → enum values; codec test round-trips both.
- [x] Setup view-model: exposure preview from the readiness entry; card gains
  `:prior-source(-label)`, `:regression-estimate`, `:final-basket`, dynamic
  `:final-model-line`; confidence cell uses preview q; rail adds "Final modeled
  basket" and stops reading the confidence cell positionally. (Landed in the
  split namespaces `setup_history_assumption_cards.cljs` / `_rail.cljs`.)
- [x] Card view (`setup_history_assumptions.cljs`): three stacked panels with
  the expert's copy, final basket emphasized, regression-skip message, updated
  "how this works" lines.
- [x] Results disclosure (`view_model/scenario.cljs` + `inputs_tab.cljs`):
  run-diagnostics enrichment + prior/regression/final/q/effective-vol rows +
  note copy, with the stale-guard test.
- [x] Tests: domain (prior sources, threshold floor, synthetic 70/30 recovery
  at n=248, tiny-sample prior dominance at n=10, β_final ≠ β_prior ⇒ covariance
  row moves, effective-modeled-volatility, raw-beta diagnostics), boundary
  view-model (skipped + estimated preview paths), card view, codec, inputs tab.
- [x] Gates: `npm run gates` 34/34 PASS (check + test + websocket) in this
  worktree, including the namespace-size and docs lints.
- [x] Live browser QA: throwaway Playwright spec (deleted after the pass)
  seeded BTC/ETH/SOL, walked the real actions, and asserted all three panels on
  the live DOM — prior 50/50 "Equal-weight fallback", regression "R² 0.72 ·
  519 observations", final BTC 52% / ETH 48% with "Confidence q 81%", rail
  "Final modeled basket" pair, dynamic final-model strip. Screenshot captured;
  no Replicant rendering bugs.
- [ ] Follow-up (out of scope here): metadata-informed prior scoring via
  `build-proxy-prior`, a user prior-weight editor over the reserved draft
  field, and committed Playwright coverage of the proxy-card flow if it
  becomes a stable regression surface.

## Validation

Acceptance expectations (from the expert brief, adapted):

- ETH/BTC-style selected proxies no longer render a final 50/50 unless the
  model actually produces 50/50; the card separates prior basket, regression
  estimate, and final modeled basket, with the final basket emphasized and the
  summary strip naming its weights.
- The final modeled basket drives covariance synthesis (pinned:
  `covariance-row-follows-final-beta-not-prior-test`); R² is never converted
  into weights (pinned: `estimate-regression-uses-joint-fit-not-r2-weights-test`).
- Tiny samples stay prior-dominant (`model-exposure-stays-prior-dominant-on-tiny-samples-test`,
  q < 0.08 at n = 10); n = 248 with strong fit moves the split materially
  (`model-exposure-recovers-the-true-split-with-enough-overlap-test`).
- Results disclose prior + source, regression estimate, confidence q, final
  basket, and effective modeled volatility; a post-run proxy edit hides the
  stale run basket (`inputs-tab-omits-proxy-model-when-run-diagnostics-mismatch-test`).
- Existing conservative-mode and engine-backed proxy behavior unchanged
  (existing suites must stay green).

Gates: `npm run gates` (check + test + websocket) must pass in this worktree.

## Surprises & Discoveries

- The expert's "current UX shows equal weights as final" complaint understated
  the codebase: the final basket was already computed and already driving
  covariance — it was simply never shown anywhere pre-run, and the prior panel's
  caption ("adjusted by model") actively misattributed it.
- The readiness request already ships the regression series to the main thread
  (`normalize-history-assumptions` attaches `:regression-series` for
  engine-backed entries), so the pre-run preview needs zero new plumbing — just
  a shared domain entry point.
- Live-QA plumbing: under the preview harness the app boots (debug bridge up,
  dispatches accepted, router updates the URL) but never replaces the
  `HYPEROPEN_RELEASE_APP_LOADING_SHELL` with a first render. The same dev
  server renders fine under Playwright (`visitRoute` → `/trade` → in-app
  navigate). For this repo, browser QA should go straight to the Playwright
  support helpers (`visitRoute`/`dispatch`/`seedOptimizerState`) rather than
  raw browser eval.
- In dev, seeded fixture candles do NOT isolate the run: adding real assets to
  the universe triggers the api-v2 fetch and the aligned history reflects the
  real market series (the QA overlap came out 519 days, not the seeded 200).
  Real SOL loads ~52/48 on BTC/ETH — the live pass asserted structure, not the
  synthetic tilt.

## Outcomes & Retrospective

- Landed 2026-07-05, gates 34/34. The user-visible change: the proxy card now
  tells the full story (source-labeled prior → regression estimate with R²/n →
  emphasized final modeled basket with q), the summary strip and right rail
  name the final split, and the results Inputs tab discloses what the run used.
  The modeling change is deliberately small: a stricter regression floor
  (max(10, k+3)), source labeling, and richer diagnostics — the β_final
  pipeline itself was already correct and already drove covariance.
- Retro: the expert brief assumed the model needed building; the audit showed
  it needed *showing*. Reading the engine first turned a modeling task into an
  honesty/disclosure task and kept the diff mostly in view-models, views, and
  tests.
