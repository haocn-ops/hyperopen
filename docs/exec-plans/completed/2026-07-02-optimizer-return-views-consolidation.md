# Consolidate "Risk-adjusted" + "Use my views" into Maximum Sharpe with honest return-view provenance

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/docs/PLANS.md`. Keep it self-contained and leave at least one unchecked progress item while the plan remains active.

## Purpose / Big Picture

The optimizer setup page offers three top-level presets: Conservative, Risk-adjusted, and Use my views. The last two are the same objective (Maximum Sharpe); they differ only in the return-input source (stabilized historical baseline vs Black-Litterman posterior over the same baseline). Exposing that internal branch as two peer "strategies" is implementation-model leakage, and the "Use my views" editor makes it worse: on Run, `objective-menu-inline-views` silently materializes an absolute view for EVERY universe asset at the implied baseline value with Medium confidence — implied numbers masquerade as user views, every row shows a selected "M", and nothing distinguishes a number the user typed from one the machine seeded.

After this change:

- The preset row offers two cards: **Conservative** (minimum variance) and **Maximum Sharpe** (best risk-adjusted return, uses your saved views where available and implied returns otherwise).
- Only user-authored views enter the solver. Untouched assets contribute the baseline (prior) return exactly as the old Risk-adjusted preset did — `domain/black_litterman.cljs` `posterior-returns` with zero views returns the prior unchanged, and the prior IS the baseline expected-return input, so Maximum Sharpe with no views is numerically the old Risk-adjusted.
- The right rail becomes a **Return views** panel with per-row provenance: a "Your view" / "Implied" source chip, the view's age ("today", "42d ago", stale ≥ 30d), a summary line ("3 your views · 11 implied · 1 stale"), filters, and a confidence control that shows NO selection on implied rows (the current UI falsely renders Medium as selected on every seeded row).
- Editing a row (return text, stepper, or confidence) materializes the view into the draft immediately and upserts it into a per-wallet **view library** (`view-library::<address>` in IndexedDB), so views survive preset switches, draft resets, and reloads. Clearing a row resets it to implied and removes the library entry.
- The scenario contract reports "Returns · N your views · M implied" instead of pretending "Use my views" is a model.

## Context References

Public refs:

- Direct user request on 2026-07-02 relaying designer feedback: consolidate "Use my views" and "Risk-adjusted" into Maximum Sharpe; distinguish user vs implied returns per row; persist user views locally; show view age/staleness; make the L/M/H control legible as confidence. The user delegated final implementation judgment to the repo owner-agent.

Repo artifacts:

- `/hyperopen/docs/exec-plans/active/2026-05-21-optimizer-objective-popover-use-my-views.md` — introduced the inline per-asset views editor this plan reworks.
- `/hyperopen/src/hyperopen/portfolio/optimizer/actions/draft.cljs` — `objective-menu-inline-views` (the bulk materializer this plan removes).
- `/hyperopen/src/hyperopen/portfolio/optimizer/domain/black_litterman.cljs` — `posterior-returns` empty-views ⇒ prior identity that makes the consolidation numerically safe.
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/constraint_profiles.cljs` + `runtime/effect_adapters/portfolio_optimizer.cljs` — the per-wallet IndexedDB remember-pattern the view library mirrors.
- `/hyperopen/docs/BROWSER_STORAGE.md` — browser persistence rules.
- `/hyperopen/AGENTS.md` — gates: `npm run check`, `npm test`, `npm run test:websocket`.

Local scratch refs, non-authoritative:

- None.

## Design

Conceptual model: **views are inputs, not a strategy.** The objective (Maximum Sharpe) consumes return inputs; the return-input policy is "your saved views where available, implied baseline otherwise", shown per asset with provenance.

State/persistence:

- Draft views stay in `[:draft :return-model :views]` (absolute kind, existing spec) — presence of a view = "your view"; absence = implied. No draft spec change.
- New `[:portfolio-optimizer :view-library]` state path mirrors the `view-library::<address>` IndexedDB record: `{instrument-id {:instrument-id ... :return ... :confidence-level ... :updated-at-ms ...}}`. Timestamps are stamped by the effect layer (`*now-ms*`), never by pure actions.
- New effects: `:effects/load-portfolio-optimizer-view-library` (route load, next to constraint profiles) and `:effects/sync-portfolio-optimizer-view-library` (`{:upserts [...] :removes [...]}` from the row-edit actions). Neither action involved is in `effect-order-policy-required-action-ids`, so no policy/Lean change.
- New UI path `[:portfolio-ui :optimizer :return-views-filter]` (`:all | :yours | :implied | :stale`) + one new action `set-portfolio-optimizer-return-views-filter`.

Behavior changes:

- `setup-presets`: `:conservative` unchanged; `:max-sharpe` = max-sharpe objective + `:black-litterman` return model whose views are the current draft's user views hydrated from the library for universe instruments. `:risk-adjusted` / `:use-my-views` remain as aliases of `:max-sharpe` so persisted dispatches don't break.
- Inline row actions (`set/step-portfolio-optimizer-objective-menu-view-return`, `set-...-view-confidence`, `remove-...-view`) materialize/update/remove the draft view immediately and emit the library sync effect. Blank return text removes the view (reset to implied); unparseable non-blank text keeps the existing view while typing. Confidence on an implied row adopts the row as your view at the currently shown return.
- `run-portfolio-optimizer-from-draft` no longer errors on zero views; `setup_readiness` drops the `:missing-black-litterman-views` gate; the setup Run button uses the plain run action for BL too.
- Results-page objective menu drops "Use my views" as an option; Maximum Sharpe carries the views editor; applying a non-BL objective no longer downgrades the return model to historical-mean.
- Center column: the Black-Litterman explainer (legend + preview chart + cards) becomes a collapsed disclosure inside the standard policy pane instead of replacing the pane's tail, so Maximum Sharpe does not swap the page layout.

## Progress

- [x] (2026-07-02) Scouted the full surface: presets (`setup_header`, `draft_options`), inline editor (`scenario_objective_menu`, `draft.cljs`), run/readiness gating (`run.cljs`, `setup_readiness.cljs`), BL math (`domain/black_litterman.cljs` — empty views ⇒ prior), persistence pattern (`constraint_profiles`, `persistence.cljs`, effect adapters), contract surfaces (registration rows, action/effect args, runtime catalog), coupled tests.
- [x] (2026-07-02) Pure layer: `application/view_library.cljs` (record shape, sync application, hydrate-views) and `application/return_views.cljs` (provenance rows, 30d staleness, summary counts, filters) with unit tests.
- [x] (2026-07-02) Actions: presets consolidated (`:max-sharpe` + legacy aliases, hydration from the library on preset/model apply), inline row actions materialize views immediately (+`:effects/sync-portfolio-optimizer-view-library`), zero-view BL runs unblocked (`run.cljs`, `setup_readiness.cljs`), objective menu consolidated (no `:use-my-views` option, no historical-mean downgrade, no bulk materialization), new `set-portfolio-optimizer-return-views-filter` action.
- [x] (2026-07-02) Effects/wiring: `view-library::<address>` persistence, load/sync adapters (timestamps stamped effect-side, read-only accounts update state but skip the IndexedDB write), runtime catalog + registration rows + arg contracts + `view-library-path`/`ui-return-views-filter-path` + defaults.
- [x] (2026-07-02) Views: two-card preset row with live provenance kicker, Return views right-rail panel (source chips + ages, summary line, filters, meter+word confidence with NO fake default on implied rows, per-row reset, conservative/views-off notes), policy pane unified (BL explainer demoted to a collapsed disclosure), scenario contract gained a Returns provenance row, copy sweep (run bar, model picker, why-safe note).
- [x] (2026-07-02) Tests updated/added across the suite: action-layer rewrites (materialization, hydration, zero-view runs), new unit suites (`return_views_test`, `view_library_test`, view-library effect adapters), view-layer rewrites (scenario-detail menu, results rail, setup layout/view/workspace), Playwright reworks (`optimizer-black-litterman-views.spec.mjs`, the two return-views smoke tests). Gates: `npm run check` exit 0, `npm test` 4994 tests / 0 failures, `npm run test:websocket` 546 / 0, both touched Playwright spec files 19/19 at `--workers=1`.
- [x] (2026-07-02) Browser QA (spectated wallet with a 50-asset holdings universe, dev server on :8080): two-card preset row with live provenance kicker; conservative note under the default preset; 50 implied rows with ZERO pre-selected confidence; authoring a row flips its chip to "Your view · today" and live-updates summary/contract/kicker; confidence re-weights; filters; reset-to-implied clears draft + library; Conservative→Maximum Sharpe round-trip restores the view from the library; run reveal verified via the pipeline (solved, `run-state` carried the draft scenario-id).

## Validation

- `npm run check`, `npm test`, `npm run test:websocket` (via `npm run gates`) must pass.
- Unit coverage: preset apply (incl. legacy alias keys), inline edit materialization (author/update/clear/adopt-on-confidence), zero-view run path, provenance summary + staleness math, view-library merge rules, effect adapters (load/sync, read-only gate).
- Browser QA (Playwright smallest relevant + manual spectate pass): default draft shows two cards; editing a row flips its chip to "Your view" and persists across reload; clearing resets to implied; Conservative shows the "views not used" note; solver runs with zero views.

## Surprises & Discoveries

- Observation: The Black-Litterman "market reference" prior in this codebase is NOT reverse-optimized equilibrium returns — `engine/context.cljs` feeds `posterior-returns` the `baseline-expected-return-inputs-by-instrument` (the same stabilized historical/funding estimates `:historical-mean` uses). With zero views the posterior equals that prior exactly. This is what makes the consolidation numerically safe.
  Evidence: `src/hyperopen/portfolio/optimizer/application/engine/context.cljs` (`expected-return-result`, `baseline-expected-return-inputs-by-instrument`), `src/hyperopen/portfolio/optimizer/domain/black_litterman.cljs` (`posterior-returns` empty-views branch).
- Observation: The setup page's Run button for BL dispatches `apply-portfolio-optimizer-objective-menu-selection-and-run`, whose `objective-menu-inline-views` materializes a view for EVERY universe asset from the implied seed text (medium confidence). The structured BL editor views (`black_litterman_views_panel.cljs` and friends) are dead view code — nothing requires them.
  Evidence: `setup_actions.cljs` run-button dispatch; `draft.cljs` `objective-menu-inline-views`; `rg` over requires for the panel namespaces.
- Observation: A prior-equal view is not always a no-op: with OTHER active views present it anchors its asset against covariance spillover. So "set confidence on an implied row" legitimately means "adopt the implied value as an anchored view", which is how the new confidence control behaves.
- Observation: The committed Playwright spec `optimizer-black-litterman-views.spec.mjs` was already broken on main before this work: it expects the run to reveal `/portfolio/optimize/bl-draft-current`, but `success-commands` (since the 2026-07-02 auto-reveal change) always navigates `/optimize/new` runs to the literal `/portfolio/optimize/draft` path. These specs are not part of `npm run check`, so the drift went unnoticed. Fixed to the current contract here.
  Evidence: `git show main:src/.../run_bridge_workflow.cljs` (`:optimize-new` branch hardcodes the "draft" path) vs `git show main:tools/playwright/test/optimizer-black-litterman-views.spec.mjs` line 498.
- Observation: The old smoke test's "implied" seed values (BTC "19.5") were the LAST RUN's posterior expected-returns merged over the baseline — the old editor's `result-return-inputs` beat readiness. Under the provenance model the implied value is the raw baseline the solver actually uses for a viewless asset (the same fixture yields ~1064% annualized from 4 ms-spaced candles), which is exactly the honesty this feature exists for; the test now asserts provenance rather than the posterior-derived number.
- Observation: The objective-menu popover is page-anchored below its trigger, so its footer could already fall below the fold on single-column widths when the trigger sat low; the taller provenance panel tipped the 768px case over. Fixed the growth driver (`.optimizer-objective-menu .optimizer-objective-view-rows` now caps at `min(320px, 34vh)` so a 50-asset universe scrolls inside the list) and the smoke test scrolls the footer into view before asserting reachability.

## Decision Log

- Decision: Maximum Sharpe always uses the `:black-litterman` return model (views policy); zero views is valid and equals the old Risk-adjusted result. Historical/EW estimators remain selectable in the Return/Risk panel for power users, and the views panel states honestly that views are off in those modes.
  Rationale: one mental model ("baseline + your tilts"), no behavior cliff, keeps the engine's expressiveness reachable.
  Date/Author: 2026-07-02 / Claude.
- Decision: Only user-authored views materialize; row edits commit immediately to the draft (and library) instead of staging in UI-only drafts until Run/Apply.
  Rationale: removes the implied-as-view lie at the solver boundary; makes autosave and provenance timestamps truthful; the results-page auto-recompute already tolerates immediate input-signature changes (same pattern as constraint edits).
  Date/Author: 2026-07-02 / Claude.
- Decision: Deviate from the designer's "confidence-only edits keep the return implied" sub-state. In this engine confidence exists only on views, so the confidence control on an implied row adopts the row as your view at the shown value (copy says so). A dual return-source/confidence-source provenance would require a new view shape through the request contract for a state the solver cannot otherwise express.
  Rationale: one consistent rule (touch a row → it becomes your view; clear it → implied) with no dead controls and no contract ripple.
  Date/Author: 2026-07-02 / Claude.
- Decision: Persist the view library as its own per-wallet IndexedDB record (`view-library::<address>`) written explicitly by the edit actions' sync effect — not by a store watcher diffing draft views.
  Rationale: bulk draft replacements (scenario load, preset switch, restore) must never delete or stomp library entries; explicit per-gesture upserts/removes make deletion intent unambiguous. Mirrors the constraint-profiles seam. Library persistence is skipped (in-memory only) when mutations are not allowed (spectate), matching `save-portfolio-optimizer-constraint-default-effect`.
  Date/Author: 2026-07-02 / Claude.
- Decision: Single stale tier at ≥ 30 days (constant in `application/return_views.cljs`), relative age labels always shown for user views.
  Rationale: thresholds are product-tunable; one honest tier beats four speculative ones. The designer's fresh/aging/very-stale ladder can be layered on the same constants later.
  Date/Author: 2026-07-02 / Claude.

## Outcomes & Retrospective

- Shipped in one pass: two-preset row (Conservative / Maximum Sharpe with legacy keys aliased), user-authored-only view materialization (the implied-as-view bulk materializer is gone), the provenance Return views panel (source chips, ages, 30d staleness, summary counts, filters, meter+word confidence with no fake default), the per-wallet `view-library::<address>` IndexedDB store with hydration on preset/model application, zero-view Black-Litterman runs, and the honest scenario-contract Returns row.
- Numerically the consolidation was free: the BL prior IS the baseline expected-return input and `posterior-returns` with no views returns it unchanged, so Maximum Sharpe with zero views equals the old Risk-adjusted. Untouched assets no longer submit prior-equal medium-confidence views, which if anything reduces solver noise.
- Deviations from the designer's spec, by domain judgment: no dual return/confidence provenance (confidence exists only on views in this engine — a confidence click on an implied row adopts it as a view, and the copy says so); one stale tier (≥30d) instead of four; one "Implied" reason (there is only one implied source — the baseline) explained at panel level rather than per-row fake variety.
- Validation: `npm run check` exit 0; `npm test` 4994/0; `npm run test:websocket` 546/0; the two touched Playwright specs 19/19; live browser QA on a 50-asset spectated universe covered authoring/adopt/reset/filters/round-trip.
- Residual risks: view ages are only as good as the local library (views restored from a scenario on a fresh device show no age — by design, no fabricated timestamps); library persistence is intentionally skipped in spectate/read-only sessions (in-memory only, matching constraint defaults); instruments added to the universe AFTER a preset application do not auto-hydrate remembered views until the next preset/model application (noted follow-up).
- Retrospective: the load-bearing discovery was reading `engine/context.cljs` early — everything else (preset collapse, zero-view runs, honest implied values) followed from "prior = baseline". The costliest surprise was inherited, not introduced: a committed Playwright spec that could not pass on main, which surfaced only because this work finally re-ran it.
