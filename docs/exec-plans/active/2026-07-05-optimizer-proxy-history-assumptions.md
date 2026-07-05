# Engine-Backed Proxy Behavior For Short/No-History Optimizer Assets

## Purpose

A trader who selects a newly listed asset (for example a HIP-3 perp with ~10 days
of returns, called "TOKENX" throughout) currently gets one of two bad outcomes
from the portfolio optimizer: either the asset is silently included and its
covariance row is estimated from a statistically meaningless handful of
observations (and, worse, the *shared* return window used for every other asset
shrinks to that handful), or the asset is dropped/blocked and the only remedy is
the "conservative assumption" (high volatility, a flat correlation floor against
everything, tiny cap — no diversification structure at all).

After this change the trader can instead say what the asset *behaves like*:
pick proxy assets with real history (BTC, ETH, SOL), state an expected
volatility and a max-weight cap, and the engine synthesizes a defensible
covariance row for the asset from the proxy basket — blended with a regression
on whatever short overlap exists, shrunk by a confidence weight that stays low
for tiny samples, topped up with specific (idiosyncratic) risk, capped, PSD-
repaired, and disclosed in diagnostics and the results inputs audit.

The hard requirement (from the feature owner): proxy assumptions must be
ENGINE-BACKED. A previous `:proxy` mode was removed precisely because it was
collected in the draft but never reached the covariance model. This feature is
only acceptable if the proxy assumptions demonstrably change the covariance
matrix the solver consumes.

Core modeling rule, verbatim from the product brief: "Qualitative prior chooses
the proxy. Quantitative regression adjusts it. Confidence governs shrinkage.
Specific risk and caps keep the optimizer honest." Explicit anti-goals: never
convert separate one-factor R² values into proxy weights; never treat the
basket as a perfect clone (always add specific risk); never let 10 days of data
fully determine exposures; never hide the assumptions from the user.

Durable context: direct maintainer request (2026-07-05 session), expert product
brief embedded in that request. No GitHub issue exists yet; this ExecPlan is the
authoritative artifact.

## Orientation: where everything lives

All paths are repo-relative. The optimizer is a ClojureScript app under
`src/hyperopen/portfolio/optimizer/` with strict layering (see its
`BOUNDARY.md`): `domain/*` is pure math/policy, `application/*` assembles
requests and read-models (no side effects), `infrastructure/*` talks to the
outside, views under `src/hyperopen/views/portfolio/optimize/` render
view-models only. The optimizer solve runs in a web worker: the request crosses
a `clj->js`/`js->clj` boundary and is repaired by
`src/hyperopen/portfolio/optimizer/instrument_keyed_codec.cljs` (keyword values
survive only for keys listed in `enum-value-keys`; maps keyed by instrument-id
strings survive only for keys listed in `instrument-keyed-map-keys`).

Key seams touched by this feature:

- `domain/history_assumptions.cljs` — behavior constants, defaults,
  completeness policy, engine-input extraction. Today supports `:conservative`
  only.
- `domain/history_assumption_proxy.cljs` — NEW pure namespace: prior-weight
  normalization, ridge regression toward the prior, confidence, shrinkage
  blend, specific risk, synthetic covariance row construction.
- `domain/risk.cljs` — existing conservative augmentation
  (`augment-risk-result-with-assumptions`) and `repair-psd`; unchanged except
  reuse.
- `application/request_builder.cljs` — builds the engine request; decides which
  assumptions are engine-backed, re-admits dropped assets, mirrors caps into
  per-asset overrides, normalizes `:history-assumptions` onto the request.
- `application/engine/context.cljs` — folds assumption inputs into the risk
  result and expected returns before solving.
- `application/engine/payload.cljs` — already forwards
  `(:risk-estimation risk-result)` into the solved payload; proxy diagnostics
  ride there.
- `application/setup_readiness.cljs` — run gating and user-facing blocking
  copy.
- `application/view_model/setup.cljs` + `view_model/universe.cljs` +
  `view_model/scenario.cljs` — assumption cards, universe row badges, inputs
  audit rows.
- `views/portfolio/optimize/setup_history_assumptions.cljs` (card UI),
  `setup_universe.cljs` (row badge), `setup_context.cljs` (right rail),
  `inputs_tab.cljs` (results disclosure).
- Contracts: `contracts/specs.cljs` (draft spec), `contracts/migrations.cljs`
  (legacy proxy entries are currently rewritten to conservative — must learn to
  distinguish the legacy single-proxy shape from the new multi-proxy shape),
  `instrument_keyed_codec.cljs` (wire keys).
- Action surface: `actions/draft.cljs`, `actions.cljs` facade,
  `src/hyperopen/app/actions.cljs`,
  `src/hyperopen/schema/runtime_registration/portfolio.cljs`,
  `src/hyperopen/schema/contracts/action_args.cljs`. New actions emit only
  `:effects/save-many`, so the Lean effect-order surface is NOT touched.

## The modeling design (self-contained)

Draft state shape (persisted, per instrument-id, under the draft's
`:history-assumptions` map — magnitudes are decimals, 5% = 0.05):

    {:behavior :proxy
     :expected-return 0.0        ; required only for return-seeking objectives
     :volatility 0.80            ; required, annualized
     :max-weight 0.05            ; required, mirrored into per-asset overrides
     :proxy {:instrument-ids ["perp:BTC" "perp:ETH" "perp:SOL"]
             :relationship-strength :medium   ; :low | :medium | :high
             :prior-weights nil}  ; nil in V1 => equal weight; shape reserved
     :metadata {:source :user :acknowledged? true}}  ; ack collapses the card only

Conservative entries keep their existing shape and semantics untouched.

Engine-shaped (request) form, produced by the request builder for COMPLETE
proxy entries only:

    {:behavior :proxy
     :expected-return 0.0
     :volatility 0.80
     :max-weight 0.05
     :relationship-strength :medium
     :proxy-instrument-ids [...]
     :proxy-prior-weights {"perp:BTC" 0.3333 ...}   ; normalized prior
     :regression-series {:x-returns [...]           ; TOKENX daily returns
                         :proxy-returns-by-id {"perp:BTC" [...] ...}
                         :observations 10}}         ; overlap sample count

Why the request carries the regression series: the engine runs in a worker and
must be pure over the request. The overlap window of TOKENX with its proxies is
NOT derivable from the engine history because complete proxy assets are
deliberately excluded from the engine-history alignment (next paragraph). The
request builder computes the overlap by running the existing
`history-loader/align-history-inputs` over the sub-universe {TOKENX ∪ proxies}
(the common calendar of that sub-alignment IS the overlap window) and copies
the resulting per-instrument return vectors onto the normalized entry.

Alignment exclusion — the load-bearing decision: the optimizer's history
alignment (`application/history_loader/alignment.cljs`) intersects the return
calendars of every eligible asset. A 10-day asset therefore shrinks EVERY
asset's covariance estimation window to ~10 days, which is precisely the
failure this feature exists to prevent. So: an asset whose proxy assumption is
structurally complete is removed from the alignment input universe (its native
series no longer constrains the shared calendar), then re-admitted into the
engine universe (same mechanism as conservative no-history re-admission), and
its covariance row is synthesized by the proxy augmentation. Assets with
incomplete proxy assumptions stay in alignment, but readiness blocks the run
until they are complete, so they can never poison a solve. The conservative
path is untouched: conservative short-history assets stay aligned exactly as
today (their row is replaced by the floor synthesis afterwards).

Proxy exposure estimation (all in the new pure namespace
`domain/history_assumption_proxy.cljs`):

1. Prior: keep only selected proxy ids present in the base risk result; if
   supplied prior weights are missing/invalid (non-finite, negative, zero
   total) fall back to equal weight; normalize to sum 1.
2. Regression (only when the overlap has more than
   `min-regression-observations` = 3 usable rows): multi-proxy ridge regression
   of TOKENX's demeaned returns y on the demeaned proxy return matrix X,
   regularized TOWARD the prior — minimize ||y − Xβ||² + λ||β − β_prior||²,
   i.e. solve (XᵀX + λI)β = Xᵀy + λ·β_prior with the existing
   `domain/linear_solve.cljs` Gauss-Jordan solver. λ = mean-diagonal(XᵀX) ×
   (n0 / n) with n0 = 120: scale-aware, and it dominates the data term exactly
   when the sample is tiny. Never separate per-proxy regressions; never
   R²-as-weights. R² is computed from the final coefficients and clamped to
   [0, 1]; it is a confidence signal only.
3. V1 exposure policy (documented, deterministic): coefficients are clamped
   nonnegative and normalized to sum 1, so β always reads as a "proxy basket".
   A diagnostic flag records when clamping/renormalization changed the raw
   solution.
4. Confidence q = min(1, n/(n + 120)) × min(1, R²/0.5), clamped to [0,1];
   q = 0 when regression was skipped. Ten observations give q ≤ 0.077 even at
   perfect fit.
5. β_final = q·β_regression + (1 − q)·β_prior (then nonneg-renormalized).
   With no usable overlap: β_final = β_prior, q = 0, R² nil.
6. Covariance synthesis against the current (post-conservative-augment) risk
   result Σ: Cov(A, X) = Σ_AP · β_final for every base asset A;
   systematic_var = β_finalᵀ · Σ_PP · β_final;
   specific_var = max(user_vol² − systematic_var,
                      annualized regression residual variance (when available),
                      min_specific_share × user_vol²,
                      0);
   Var(X) = systematic_var + specific_var. min_specific_share maps from
   relationship strength: :low 0.50, :medium 0.25, :high 0.15 (weaker stated
   relationship ⇒ more idiosyncratic risk assumed). If systematic_var exceeds
   user_vol² we do not crash: total variance is lifted through the specific
   floor and a warning (`:proxy-systematic-variance-exceeds-target`) is
   emitted.
7. Row placement: replace the row/column when the asset id is already present
   in the base ids, append otherwise (with the exclusion design, append is the
   normal case; replace keeps the function total). Multiple proxy assets are
   processed in sorted-id order so runs are deterministic; a later asset's
   Σ_AP sees earlier synthesized rows. Symmetry is enforced by construction
   and the whole matrix goes through the existing `risk/repair-psd` once at
   the end.
8. Diagnostics per asset (attached to the risk result under
   `[:risk-estimation :history-assumptions instrument-id]`, which
   `engine/payload.cljs` already forwards to the solved payload):
   behavior, proxy ids, prior weights, regression beta, final beta,
   sample count, r2, confidence-q, systematic/specific/residual variance,
   volatility, max-weight, relationship strength, warnings.

Expected returns: `history-assumptions/augment-expected-returns` (which
overrides the expected return of assumption assets with the user's stated
value) is applied to the merged conservative + proxy engine inputs. Minimum
variance ignores expected returns entirely; return-seeking objectives use the
user's number, never a raw 10-day mean (the asset has no aligned return series
in the engine history, so no historical mean can leak in).

Run gating (setup readiness), new rules in plain language:

- An asset with NO usable history and no assumption blocks the run (existing
  behavior, message updated to mention proxy behavior as an option).
- NEW: an asset whose native history has fewer than
  `assumption-required-max-observations` = 30 daily observations, in a universe
  whose longest native series has at least `short-history-min-observations`
  (360) observations, blocks the run until it carries a complete assumption
  (proxy or conservative). The second clause keeps all-young universes (and the
  existing 2-candle test fixtures) runnable exactly as today: when there is no
  long history anywhere, there is nothing to borrow via proxies and the old
  behavior stands. Assets between 30 and 360 observations remain non-blocking
  "thin history" — the card is offered but optional (matches the approved
  mock, where THIN HISTORY rows coexist with READY TO RUN).
- A proxy assumption blocks while: no proxies selected; the asset is its own
  proxy; any selected proxy lacks usable optimizer history (not in the aligned
  eligible set, or itself carries a history assumption); volatility missing;
  cap missing/nonpositive or above the global max-asset-weight; expected
  return missing when the objective is return-seeking. Each condition has
  dedicated user-facing copy (see message list in the Progress section's
  readiness item).
- A complete proxy assumption unblocks the run; conservative behavior
  continues to unblock exactly as today.

## Decision Log

- 2026-07-05 Alignment exclusion for complete proxy assets (described above).
  The brief says "their synthetic proxy row should replace the native
  short-history row"; replacement alone would leave the 10-day intersection
  poisoning every other asset's covariance, so exclusion + re-admission +
  append implements the brief's intent (the augment function still supports
  replace for the already-present case). Recorded as the design's central
  mechanism.
- 2026-07-05 "Apply assumptions" is an acknowledgment, not a staging commit.
  Assumption fields already save live (existing conservative card behavior;
  no staged-draft machinery exists for assumptions). The Apply button sets
  `:metadata {:acknowledged? true :source :user}` which collapses the card to
  its summary; Reset re-seeds the mode defaults; Clear removes the entry.
  Run-gating NEVER depends on acknowledgment (the brief's completeness rules
  do not include it).
- 2026-07-05 Prior basket is equal-weight in V1 (brief explicitly allows
  this); the mock's 20/30/50 basket is a future explicit-prior editor, the
  state shape already reserves `:prior-weights`.
- 2026-07-05 Relationship strength maps to the specific-risk floor share only
  (:low 0.50 / :medium 0.25 / :high 0.15). It does not change the regression
  λ, and it does not retroactively rewrite the user's cap (the brief suggests
  a tighter default cap for :low; since strength is edited after the entry is
  seeded, silently rewriting an already-visible cap field would be worse UX
  than an honest floor mapping).
- 2026-07-05 Universe rows paint a badge ONLY for assumption-workflow states
  (Needs proxy / Needs assumption / Configured / Proxy behavior /
  Conservative). Plain READY / THIN HISTORY remain data-role-only, respecting
  the earlier owner decision that generic history noise made the list read
  like an error log; the mock's READY/THIN chips are intentionally not
  painted.
- 2026-07-05 The new blocking threshold is absolute-with-a-guard (30 native
  observations, active only when the universe's longest series ≥ 360) rather
  than purely relative, so test fixtures and all-young universes keep today's
  behavior.
- 2026-07-05 Results disclosure V1 = the scenario detail "Inputs" tab's
  existing "History Assumptions Used" card extended with proxy fields
  (proxies, relationship, confidence/sample count when a solved payload's
  risk-estimation diagnostics are available via the draft-independent audit
  row builder staying draft-based; run-derived numbers surface on the setup
  card's diagnostics strip and in the payload for future results-rail work).
- 2026-07-05 Complete CONSERVATIVE assumptions are now also excluded from the
  alignment universe (they are re-admitted exactly as before). Their
  synthesized row is identical either way — volatility + correlation floor
  fully determine it — but keeping a thin native series in the intersection
  silently shrank every other asset's estimation window, which would have made
  the new "configure an assumption" gate a false promise whenever the user
  picked conservative. No user-visible flow changes; all existing conservative
  tests pass unchanged.
- 2026-07-05 The proxy workflow section moved from the left universe rail to
  the center policy pane (below Portfolio exposure), matching the approved
  mock; the slot wrapper always renders so the keyed `<details>` siblings
  below it never shift position (Replicant reset gotcha).
- 2026-07-05 An asset that itself carries a history assumption can never serve
  as a proxy (a synthetic row must not anchor another synthetic row); this
  falls out of the "usable proxy" definition (aligned realized history only)
  rather than a special case.
- 2026-07-05 (post-landing, from owner feedback on the live branch) The
  workflow gained a USER-INITIATED entry point: the section now renders
  whenever the universe is non-empty and its header carries a "Model an asset
  with proxies…" dropdown listing every selected asset not already in the
  workflow; choosing one seeds proxy mode and its card appears. Rationale: the
  thresholds only auto-flag egregious cases (no history / <30 rows), but the
  user may judge an asset statistically unsound on their own (the live
  example: SOPH, ~403 rows against 750-1080 for the rest of the universe) —
  the engine has always backed proxy assumptions by COMPLETENESS, not
  thinness, so only the entry point was missing. The right-rail summary still
  keys off cards-exist. Thin-history rows also paint a muted THIN HISTORY chip
  now (mock-consistent; the earlier no-paint deviation is narrowed to Ready
  only).

## Surprises & Discoveries

- Production sessions load optimizer history through the api-v2 backend: the
  state-side `:candle-history-by-coin` map is EMPTY in real use, so the
  state-only native-observation count (which both the pre-existing thin-card
  detection and this feature's first cut read) returned nil for EVERY asset —
  no thin cards, no badges, no proxy section, ever, outside fixture-shaped dev
  seeds. Found by inspecting the owner's live session over the shadow-cljs
  nREPL (`hyperopen.system/store` + `cljs-eval :app`). Fix: observation counts
  now come from the aligned history's `raw-price-series-by-instrument` on the
  readiness request (falling back to the configured proxy's regression-series
  overlap, then to state candles for fixtures). A boundary regression test
  pins the api-v2 shape (empty state candles + populated aligned raw series).

- History alignment intersects EVERY eligible asset's return calendar, so a
  10-day asset silently shrank the whole universe's covariance estimation
  window to ~10 days even before this feature — the blocking rule plus
  alignment exclusion fixes a live modeling defect, not just a UX gap.
- The worker wire codec keywordizes only allowlisted value keys and
  re-stringifies only allowlisted instrument-keyed maps; the new
  `:relationship-strength` enum and the prior/beta/return-series maps all
  needed codec entries or they would silently corrupt across the worker
  boundary.
- `run_gates_summary.mjs` does not short-circuit: the first full-gates run
  surfaced a stale namespace-size ledger and the missing ExecPlan sections
  (this lint enforces "Decision Log" / "Surprises & Discoveries" /
  "Outcomes & Retrospective" headings verbatim) while everything else passed.
- The ridge-toward-prior regression deliberately keeps prior weight on
  weak-signal proxies (a near-zero-variance proxy cannot resist the λ·prior
  pull), so tests must assert direction and bounds, not exact least-squares
  coefficients.
- A real bug survived every automated gate: `proxy-add-select` in
  `setup_history_assumptions.cljs` built the `<option>` list via
  `(into [[:option ...]] (map f) available)` and handed that whole vector to
  `[:select ...]` as ONE nested child, instead of using `into` on the
  `[:select ...]` vector itself so each `[:option ...]` lands as a direct
  sibling. Every hiccup-tree test still passed (`find-by-data-role` walks
  arbitrarily nested structures, so it found the `:select` node regardless of
  shape), but live in a real Replicant-rendered DOM the nested vector rendered
  as a single stringified text node — the dropdown showed literal
  `[[:option ...] ...]` text with zero real `<option>` elements, so a user
  could never actually pick a proxy. Caught only by loading the real app in a
  browser and clicking through the flow (prompted by the user asking how to
  use it), not by the full green test suite. Fixed, and a new test
  (`history-assumptions-section-proxy-add-select-renders-real-option-siblings-test`)
  asserts the `<select>`'s direct children are individual `[:option ...]`
  nodes — verified to fail against the reintroduced bug and pass against the
  fix. Lesson: the existing hiccup-list helpers in this file (`card-errors`,
  `diagnostics-strip`, `mode-tabs`, etc.) are all safe because they use
  `(into [:tag {...}] (map f) coll)` with the TAG itself as the target; the one
  place that instead built a standalone list and passed it as a child value is
  the one that broke.

## Milestones

Milestone 1 — pure domain policy + math. `domain/history_assumptions.cljs`
gains the `:proxy` behavior (constants, defaults, completeness with a
first-missing-field classifier, merged engine-input extraction, generalized
expected-return augmentation). New `domain/history_assumption_proxy.cljs`
implements prior normalization, ridge regression, confidence, blending,
specific risk, and `augment-risk-result-with-proxy-assumptions`. Proof:
`npx shadow-cljs compile test && node out/test.js` (or the targeted runner)
passes new domain tests covering the 18 domain cases in the brief (prior
fallback, q low at n=10, β_final = prior at q=0, PSD/symmetry, replace vs
append, conservative unchanged, R²-not-weights, specific variance positive).

Milestone 2 — request builder + engine + readiness. Complete proxy assumptions
become engine-backed: excluded from alignment, re-admitted, caps mirrored,
normalized entries (with regression series) on the request; engine context
folds the proxy augmentation and merged expected-return overrides; readiness
implements the new blocking rules and copy; contracts (spec, migrations, wire
codec, signatures ride along automatically) accept the new shape. Proof:
request-builder tests (engine-backing, re-admission, cap mirroring tighter-
wins, normalized entry with series), readiness tests (each blocking condition
and the unblock), engine test proving the solved covariance row for TOKENX is
proxy-derived (nonzero off-diagonals ≠ zero-correlation) and the asset is not
dropped.

Milestone 3 — draft actions + view-model + views + disclosure. New actions
(toggle proxy asset, set relationship strength, apply/acknowledge, reset) with
full registration surface; assumption cards grow the mode tabs, proxy
multi-select chips, relationship selector, prior basket preview, diagnostics
strip, Apply/Reset/Clear; universe rows badge NEEDS PROXY → PROXY BEHAVIOR;
right rail gains the "History assumptions" summary panel with the green
all-configured line; inputs tab discloses proxy details. Proof: view tests
(render + dispatch assertions via data-roles), view-model tests (cards,
badges, rail model), actions tests.

Milestone 4 — gates. `npm run setup:worktree` once, then `npm run gates`
(runs `npm run check`, `npm test`, `npm run test:websocket` without
short-circuiting) all PASS; namespace-size exceptions bumped where legitimate.

## Progress

- [x] Explore all seams; write this plan.
- [x] M1 domain: behaviors/defaults/completeness/engine-inputs in
      `history_assumptions.cljs` (+ constants: 30-obs blocking threshold).
- [x] M1 domain: `history_assumption_proxy.cljs` (prior, ridge, q, blend,
      specific risk, augment + diagnostics + PSD).
- [x] M1 tests: `domain/history_assumptions_test.cljs` extended +
      new `domain/history_assumption_proxy_test.cljs`.
- [x] M2 contracts: specs accept new proxy shape; migrations distinguish
      legacy single-proxy (`:proxy-instrument-id` key ⇒ conservative rewrite)
      from new shape (passes through); codec: `:relationship-strength` enum,
      `:proxy-prior-weights`/`:prior-weights`/`:regression-beta`/`:final-beta`
      /`:proxy-returns-by-id` instrument-keyed.
- [x] M2 request builder: classification, alignment exclusion, sub-alignment
      regression series, re-admission, cap mirroring, normalized entries;
      align-history memo capacity raised (4 → 16) for sub-alignments.
- [x] M2 engine context: merged inputs, proxy augment fold, expected-return
      augmentation over merged inputs.
- [x] M2 readiness: 30-obs rule (guarded), proxy incompleteness warnings with
      the product copy: "<label> needs a history assumption. Choose proxy
      behavior or a conservative assumption." / "…is set to proxy behavior but
      no proxy assets are selected." / "…cannot use itself as a proxy." /
      "…proxy asset <proxy> has no usable optimizer history." / "…needs an
      expected annual volatility." / "…needs a max weight cap." / "…needs an
      expected annual return for this objective." / cap-above-global variant.
- [x] M2 tests: request-builder, readiness, engine integration (TOKENX
      min-variance run includes asset, capped, proxy-derived row, diagnostics
      present, conservative regression suite still green).
- [x] M3 actions: toggle proxy asset / relationship strength / apply / reset +
      facade + app wiring + registration rows + arg contracts (+ tests).
- [x] M3 view-model: proxy cards (options exclude self + assumption-carrying
      assets, chips, prior basket, diagnostics strip, status labels), universe
      badge states, right-rail model, inputs audit rows.
- [x] M3 views: card UI (mode tabs, chips, relationship, inputs, basket,
      diagnostics, actions), universe row badge, right-rail panel, inputs tab
      disclosure; styles if needed.
- [x] M3 tests: setup_history_assumptions_test, setup_universe/view tests,
      inputs_tab_test, view-model tests.
- [x] M4: `npm run gates` PASS; namespace-size exceptions updated; plan moved
      to completed/ if the owner agrees the follow-ups below are separate.
- [x] Post-landing owner feedback: manual "Model an asset with proxies…"
      entry point + api-v2 observation sourcing + muted THIN HISTORY chip;
      verified against the owner's live session over nREPL; gates 34/34.
- [ ] Follow-up (not this pass): explicit prior-weight editor; results-rail
      run-derived confidence panel; negative/leveraged proxy exposures;
      "Excluded - needs assumption" card status label is wrong for
      thin-but-runnable assets (they are included, just thin) — reword.

## Validation

From the repo root (worktree): `npm run setup:worktree` once (symlinks
node_modules; a fresh worktree otherwise fails every gate with environmental
errors). Targeted loop while developing:
`npx shadow-cljs compile test && node out/test.js` filtered per the repo's
test runner conventions (or `npm test` for the full suite). Final:
`npm run gates` → expect a PASS row for check, test, and test:websocket.
Manual proof (optional, browser): `npm run dev`, open `/portfolio/optimize/new`
on :8080, add a short-history asset plus BTC/ETH/SOL, watch the NEEDS PROXY
badge, configure proxy behavior, watch Ready-to-run flip green, run, and read
the covariance-backed diagnostics on the card and the Inputs tab disclosure.

## Outcomes & Retrospective

- Landed 2026-07-05: proxy behavior is engine-backed end to end. A complete
  proxy assumption changes the covariance matrix the solver consumes (engine
  integration test captures the solver problem and asserts the synthesized
  TOKENX row, the 5% upper bound, symmetric off-diagonals from the basket, and
  the low confidence-q at 10 observations), the asset is never silently
  dropped, expected returns come from the assumption (never a raw short mean),
  diagnostics ride the solved payload under
  `[:risk-estimation :history-assumptions]`, and the Inputs tab disclosure
  covers proxy details with the covariance-synthesis note.
- Validation: full cljs suite 5138 tests / 27949 assertions green;
  `npm run gates` matrix green after bumping the namespace-size ledger and
  adding the required ExecPlan sections (`npm run check`, `npm test`,
  `npm run test:websocket`).
- The alignment-window poisoning by short-history assets was the decisive
  discovery: replacement-only synthesis (the brief's literal wording) would
  not have fixed the model for the OTHER assets in the universe.
- Browser QA: `npx playwright test optimizer-view-model-routes.smoke.spec.mjs
  --workers=1` → 12/14 pass. The 2 failures are a PRE-EXISTING stale assertion
  (the spec still expects the objective trigger to read "Minimum volatility";
  commit 410a49d8 renamed it to "Minimum risk" on 2026-07-04, after the spec's
  last update on 2026-07-03) — unrelated to this feature, flagged as its own
  follow-up task. Everything else in those two tests passed, including a live
  in-browser optimizer re-run through the modified readiness/request-builder
  path (progress succeeded, frontier produced).
- Not done in this pass (deliberate): explicit prior-weight editor (state
  shape reserved), run-derived confidence refresh on the setup card after a
  solve, negative/leveraged proxy exposures, and a results-rail panel for the
  regression diagnostics. A dedicated Playwright spec for the proxy workflow
  card should accompany the first UI-visual polish follow-up.
