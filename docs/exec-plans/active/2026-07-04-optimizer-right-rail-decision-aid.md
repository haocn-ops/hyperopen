# Optimizer setup right rail: from status panel to decision aid

Date: 2026-07-04
Status: in progress
Surface: `/portfolio/optimize/new` right rail (`setup_context.cljs`, `setup_readiness_panel.cljs`, view-model `setup.cljs`, `setup_readiness.cljs`)

## Purpose / Big Picture

Expert review of the setup right rail: it reads like a debug/status panel, not a
decision aid. The rail should answer, in order: what exactly will run, is it safe
enough to run, and what should be fixed before running. Concretely:

1. The inactive Return Views section gets nearly the same visual weight as live
   readiness warnings.
2. "Returns: Historical mean" under a Minimum Variance goal implies expected
   returns drive the optimization when they do not.
3. "Trust & Freshness" is internal product language; "Data health" is the user's
   question.
4. Warning copy is telemetry-grade ("proxy history used", raw
   `INSUFFICIENT-COMMON-HISTORY` code labels leading the card).
5. All warnings look identical (amber); no blocking / caution / info ranking and
   no overall "Ready with cautions" verdict.
6. The exposure line ("Gross 2.12×–2.68× · Net …") compresses four numbers into
   one wrapping line; stacked rows scan better.
7. Two generic status lines ("Current portfolio snapshot is available." +
   "Optimizer history is loaded…") should combine into one.

## Context References

- Direct maintainer request: 2026-07-04 owner question + expert UX response
  on the `/optimize/new` right rail (session transcript; full response quoted in
  the task brief).
- Prior related work: `docs/exec-plans/active/2026-07-03-optimizer-journey-integrity.md`
  (Milestone 4, honest diagnostics) and the 2026-07-02 setup-readability passes.

## Progress

- [x] View-model (`view_model/setup.cljs`)
  - `setup-summary-card-model`: added `:return-forecast-label` ("Not used" when
    the goal is minimum variance, else the live source line), `:exposure-rows`
    (stacked label/value pairs for Gross / Net / Max asset / Rebalance),
    `:views-active?`, `:min-variance?`.
  - `readiness-panel-model`: title is now "Data health"; added `:status`
    `{:level :ready|:caution|:blocked|:loading :label …}`; grouped warnings gain
    `:severity` (`:blocking` when built from blocking-warnings, `:info` for
    by-design notes like proxy-history-used, else `:caution`) and an optional
    human `:detail` line. Provider-limit groups lead with a human headline; the
    raw provider message moves to the detail line and the code behind Details.
- [x] Domain copy (`setup_readiness.cljs`): `warning-code-summary` says
  "substitute history" for proxy-history-used and "stale history" (not "stale
  cached history"); per-asset proxy message says "approved substitute history".
- [x] Rail view (`setup_context.cljs`): contract rows Universe / Goal /
  Return forecast / Risk model / Exposure policy (stacked); Return views full
  editor only when the views-aware model is active, otherwise a one-line
  demoted section ranked last with a Switch-goal / Use-my-views action;
  "Trust & Freshness" → "Data health" with a right-aligned overall status;
  healthy snapshot + history lines combine into one sentence.
- [x] Readiness panel view (`setup_readiness_panel.cljs`): severity-tinted
  cards (error / warning / muted info), detail line under the headline, raw
  code labels demoted behind a "Details" disclosure.
- [x] Tests updated: `view_model_setup_boundary_test`, `setup_readiness_test`,
  `setup_layout_test`.
Round 2 (2026-07-04, expert follow-up review — "calming pass", P0→P2):

- [x] P0 Vocabulary unified to **"Minimum risk"** everywhere user-facing:
  scenario-contract Goal row (`objective-display-names` in the setup
  view-model), `format.cljs` display-labels, run-bar action label
  (`setup_actions.cljs`), results objective menu (was "Minimum volatility"),
  return-views notes, why-safe note (keeps "minimum-variance objective" as the
  technical term in secondary copy), rail inactive note.
- [x] P0 "Start with" inside Portfolio exposure renamed to "Exposure presets".
- [x] P0 Exposure pad legend ("● Target — drag to move" / "◌ Current") under
  the pad; the current-portfolio marker already existed but was unlabeled.
- [x] P0 Drag affordance: outer grab ring around the target handle
  (`__handle-ring`) + `cursor: grab` on handle and ring.
- [x] P1 "More goals" (Target volatility / Target return) collapsed behind a
  `<details>` drawer, forced open while an advanced objective is selected.
- [x] P1 Holdings-seeded bands note converted from an amber sentence to a chip
  ("Custom from holdings") + muted "Review before running" hint.
- [x] P1 Data health verdict promoted to the section headline (colored,
  0.8125rem semibold) instead of a small right-aligned chip; warnings now sort
  severity → actionability (fixable-in-app cautions before the provider limit).
- [x] P1 Max Sharpe rail: Return views editor renders `:collapsible? true` —
  the resting state is a one-line summary ("Used by Maximum Sharpe · N your
  views · M implied"), so a 13-asset editor can't push Data health/run context
  below the fold.
- [x] P2 Implied-row confidence label "Adopt at" → "Save as my view" (tooltips
  and aria-labels updated to "save as your view · <level> confidence").
- [x] P2 Scenario-contract row labels de-teletyped: plain sentence-case muted
  text; mono/uppercase reserved for values and section eyebrows.
- [ ] Follow-up: consider a compact collapsed-by-default contract once the
  expanded card proves to be scanned rather than read; revisit whether the
  provider-limit warning should carry an explicit "configure data source" path;
  audit selected-state vs keyboard-focus outline colors (gold selection vs blue
  focus) across the goal cards in a dedicated theming pass.

## Surprises & Discoveries

- The "CoinGecko Demo provider history window is capped by provider tier."
  string is not in this repo — it arrives as a backend warning `:message` on
  `:insufficient-common-history`. The humanized headline therefore lives in the
  grouped projection, with the raw provider message demoted to the detail line.
- `readiness-panel` had exactly one caller, so its signature could keep the
  (readiness, history-load-state) shape while gaining an options map.

## Decision Log

- Minimum variance ⇒ "Return forecast: Not used" regardless of the configured
  estimator: the estimator still feeds displayed metrics, but it does not drive
  the recommendation, and the contract describes what drives the run.
- The full Return views editor renders only under the views-aware
  (Black-Litterman) model; inactive states get a one-line note ranked last with
  the one-click activation (apply-setup-preset :max-sharpe under min-variance,
  set-return-model :black-litterman otherwise) — matching the goal-consolidation
  action semantics.
- Severity is projected in the view-model (pure), not the view: blocking when a
  group was built from `:blocking-warnings`, info for by-design disclosure codes
  (`proxy-history-used`, `vault-derived-history-used`, `funding-history-missing`,
  `manual-capital-base`), caution otherwise; cautions sort before infos.
- Top-level rail sections carry `:replicant/key` so conditional reordering does
  not reset open `<details>` state (known Replicant gotcha).

## Outcomes & Retrospective

Landed 2026-07-04 pending final gate run. The rail now ranks: scenario contract →
data health (with an overall verdict) → actionable warnings → demoted inactive
return-views note. Raw codes and vendor internals are available by exception
only. Remaining risk: the "Ready with cautions" verdict derives from grouped
warning severity, so a future warning code that is neither blocking nor listed
as info defaults to caution — safe (over-warns) but worth a glance when adding
codes.

## Validation

- `npm run gates` (check, test, websocket) — required; browser QA via workbench
  scene or dev server if visual verification is requested.
