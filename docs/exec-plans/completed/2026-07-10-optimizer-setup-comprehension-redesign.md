# Optimizer Setup: Comprehension Redesign (Quieter By Default)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` are maintained in accordance with `/hyperopen/docs/PLANS.md`.

Durable context: direct user request (product owner, 2026-07-10) — the optimizer
setup page (`/portfolio/optimize`) is "very busy … mentally taxing to look at";
the owner reviewed two design passes (an in-repo critique plus an external
designer's mock and commentary) and asked for the merged first pass to be
planned and implemented. The merged direction, in the reviewers' words: make the
screen "quieter by default, with detail appearing only when the user is acting
on it" — remove duplicated status, collapse configured/inactive surfaces behind
disclosures, and unify the meaning of "ready".

## Purpose / Big Picture

The setup page states the same healthy facts many times at once. With a fully
configured 78-asset universe the user simultaneously sees: a green footer
"READY TO RUN"; a green rail banner "All short-history assets have assumptions.
Ready to run."; a rail note "Proxy assumptions are disclosed in results."; eight
"CONFIGURED" chips in the center proxy-workflow section; eight more in the right
rail plus a "Status: Configured" row inside each rail card (~10 label/value rows
per asset); an always-open proxy section; a titled rail section devoted to a
feature that is *not* in use ("Return views — not used"); and an asset list
where most rows carry a duplicate sublabel (DOT/DOT), an identical PERP badge,
and a third restatement of the included-count. Meanwhile the one thing that IS
wrong (current gross exposure outside the configured policy) appears only as a
small red line inside the exposure panel while the footer shouts green
"Ready to run" — a trust gap.

After this change, with everything healthy the page says "ready" exactly twice
(the footer verdict and the Data health headline), the proxy workflow is one
collapsed row reading "History assumptions — 8 of 8 configured", each rail
assumption is a one-line disclosure that expands to its full detail, the asset
list is mostly single-line rows, and when the current portfolio violates the
exposure policy the footer honestly reads "Ready with 1 warning" with a matching
warning card in the rail that links to the exposure section. Nothing is
deleted — every demoted detail stays one click away, and anything needing
attention forces its surface open.

To see it working: run `npm run dev` (or the Playwright static serve), open
`/portfolio/optimize` with a connected/spectated account, and observe: the
footer verdict; the collapsed "History assumptions" center section with a
trailing "N of N configured" summary that hides when the section is open; the
rail "Run summary" card with Universe / Goal / Return forecast / Risk model /
History data / Status rows plus Exposure policy; one-line expandable rail
assumption rows; a one-line "Return views · Not used by Minimum risk · Switch"
row; and universe rows without duplicate sublabels or uniform PERP badges.

## Context References

Public refs:
- Direct user request (this session, 2026-07-10): "create an execution plan to
  redesign that initial page for easier user comprehension. and then go ahead
  and implement it", following the owner-approved merged review.

Repo artifacts:
- Parent surface plans (completed): `docs/exec-plans/completed/2026-07-06-optimizer-proxy-risk-guardrails.md`
  (guardrails drawer + `optimizer-section-trailing` hide-when-open mechanic),
  `docs/exec-plans/completed/2026-07-04-optimizer-setup-copy-diet.md`,
  `docs/exec-plans/active/2026-07-04-optimizer-right-rail-decision-aid.md`
  (rail order + Data health severity model this plan builds on).

Local scratch refs (non-authoritative):
- None.

## Context and Orientation

The optimizer UI is ClojureScript rendered with Replicant: views are pure
functions from view-model maps to hiccup vectors; user events dispatch action
vectors. "Disclosure" below always means a native `<details>`/`<summary>`
element. The setup route is a 3-column grid (`workspace_view.cljs`): LEFT =
universe list, CENTER = policy pane, RIGHT = context rail.

Files this plan touches (all paths repo-relative):

- `src/hyperopen/views/portfolio/optimize/setup_context.cljs` — right rail:
  `summary-card` (the "Scenario contract"), the Return-views inactive section,
  `history-assumptions-rail-panel`, Data health.
- `src/hyperopen/portfolio/optimizer/application/view_model/setup_history_assumption_rail.cljs`
  — `rail-summary-pairs` (per-asset label/value rows) and
  `history-assumption-rail-model` (`:ready-message`, `:disclosure-note`).
- `src/hyperopen/portfolio/optimizer/application/view_model/setup.cljs` —
  `readiness-panel-model` (Data health status: `:ready`/`:caution`/`:blocked`
  /`:loading`); gains the shared `run-verdict`.
- `src/hyperopen/portfolio/optimizer/application/view_model/exposure.cljs` —
  `exposure-preview` already computes `{:gross-ok? :net-ok? :on-policy?}` from
  the draft constraints and the current portfolio exposure; reused, not changed.
- `src/hyperopen/views/portfolio/optimize/setup_actions.cljs` — footer run bar
  (`run-status`, `setup-bottom-actions`).
- `src/hyperopen/views/portfolio/optimize/setup_sections.cljs` — center pane
  composition; computes the exposure preview and passes the verdict down.
- `src/hyperopen/views/portfolio/optimize/setup_history_assumptions.cljs` — the
  center "Proxy Workflow for Short-History Assets" section.
- `src/hyperopen/views/portfolio/optimize/setup_universe.cljs` — universe rows,
  selected-table header, source line.
- `src/hyperopen/views/portfolio/optimize/setup_objective_controls.cljs` — the
  "active" chip on the selected goal card.
- `src/hyperopen/views/portfolio/optimize/setup_controls.cljs` — disclosure
  helpers (`disclosure-panel*`); gains `:id` on panels so anchors can target
  them.
- `src/styles/surfaces/optimizer/universe.css` — the selected-table grid tracks
  (columns live in static CSS on purpose; see the comment at line ~78).
- Tests: `test/hyperopen/views/portfolio/optimize/{setup_context_test,setup_actions_test,universe_panel_test,setup_layout_test,setup_history_assumptions_test}.cljs`,
  `test/hyperopen/portfolio/optimizer/application/view_model_history_assumption_cards_test.cljs`.
- Playwright (verify, edit only if they fail):
  `tools/playwright/test/optimizer-proxy-loading-ux.spec.mjs`,
  `tools/playwright/test/optimizer-history-assumptions-io.spec.mjs`,
  `tools/playwright/test/optimizer-setup-rail-sticky.spec.mjs`.

Key repo constraints honored throughout: a `<details>` must never compute
`:open` from state that the user's own toggle also controls (it re-asserts
against the user) — force `:open` only from a machine condition like
"needs attention", the same shape the "More goals" drawer uses; conditional
keyed siblings need `:replicant/key`; grid tracks for the universe tables live
in static CSS (Tailwind JIT drops arbitrary `grid-cols-[…]` on watch rebuilds);
uppercase-mono is reserved for tags/values/eyebrows; and warnings the user
cannot act on must fold away while anything actionable stays visible
(honesty policy: demote, never delete).

## Plan of Work

Stage A — one global readiness verdict. In `view_model/setup.cljs` add
`run-verdict`: combines the existing `readiness-panel-model` status with an
`:off-policy?` flag into `{:level :ready|:caution|:blocked|:loading, :label,
:warning-count}`; label reads "Ready to run" / "Ready with N warning(s)" /
"Action needed" / "Loading…". In `setup_sections.cljs` compute
`(exposure-vm/exposure-preview {:current-exposure … :constraints (:constraints
draft)})` (the same call the exposure map makes) and pass
`{:verdict … :exposure-preview …}` into `setup-bottom-actions`. In
`setup_actions.cljs` `run-status` accepts `:verdict`; when the run is
triggerable and the verdict level is `:caution` the pill becomes an amber
"Ready with N warning(s)" (tone `:caution`, new amber dot) instead of green
"Ready to run"; blocked reasons keep their specific labels. In
`setup_context.cljs` the rail summary card gains a "Status" row rendering the
same verdict, and, when off-policy, an amber warning card naming which side is
out of range ("Current gross exposure is outside the selected policy.") with a
"Review exposure →" anchor link to `#portfolio-optimizer-constraints-panel`
(the disclosure helpers in `setup_controls.cljs` start emitting `:id` equal to
their data-role so plain fragment anchors work).

Stage B — delete redundant status layers. In the rail model
(`setup_history_assumption_rail.cljs`): drop the `"Status"` pair from
`rail-summary-pairs` (the row's own chip already carries it), and remove
`:ready-message` / `:disclosure-note` / `:any-proxy?` from the model. In
`setup_context.cljs` remove the green all-configured banner and the
disclosure-note paragraph (the "proxy assumptions are disclosed" fact already
surfaces as the folded `:proxy-history-used` info note in Data health, and in
Results). The rail panel header instead shows a compact "N of M configured"
count on the right.

Stage C — rail assumption cards become one-line disclosures. The rail model
rows gain `:summary` (the same one-line summary the center's collapsed card
shows, e.g. "modeled on ETH, ARB, OP · 80% vol · 5% cap"). In
`setup_context.cljs` each rail row renders as a `<details>` (keyed per
instrument): the `<summary>` line = asset label + one-line summary + the
existing status chip (same data-roles, still pulsing "Loading history…" while
fetching); the body = the remaining label/value pairs. Rows that are complete
render closed; a row that needs input renders with `:open` forced so unfinished
work stays visible.

Stage D — the contract becomes a Run summary. Rename the rail card eyebrow
"Scenario contract" → "Run summary" (data-role unchanged). Add a "History data"
row ("70 native · 8 via assumptions", omitted when the workflow is empty)
sourced from the rail model's rows, and the Stage-A "Status" row. Existing
rows (Universe / Goal / Return forecast / Risk model / Exposure policy) stay —
the exposure-policy band numbers are the run's receipt and deliberately remain.

Stage E — universe list diet. In `setup_universe.cljs`: suppress the secondary
label when it case-insensitively equals the primary label; render the TYPE
column (header cell + row cell + its 48px grid track) only when the selected
universe actually mixes market types — a static CSS variant class
(`optimizer-universe-cols-4`) in `universe.css` supplies the 4-track template
so header and rows stay in lockstep; drop the duplicate included-count from the
selected-table sub-header (the panel header keeps the count; the sub-header row
keeps "Clear universe"); shorten the source line to "From holdings · N omitted"
(the included count no longer repeats). In `setup_objective_controls.cljs`
remove the "active" corner chip from the selected goal card — the filled radio
glyph, border, and `aria-pressed` already carry selection.

Stage F — the proxy workflow collapses when nothing needs attention. In
`setup_history_assumptions.cljs` the section becomes a `<details>` panel
(keyed, `data-role` and inner content unchanged) titled "History assumptions"
(the mechanism word "proxy" stays in the body vocabulary — section subtitle,
"Proxy assets" field — per the result-vs-mechanism naming rule). The
`<summary>` header carries a trailing status ("N of N configured" green, or
"N need setup" amber) wrapped in `optimizer-section-trailing` so it hides while
open. `:open` is forced exactly when the section needs attention: any card not
`:configured`, any proxy history still loading, or any card errors; otherwise
the section rests collapsed and the user can open it freely. The agent
import/export toolbar demotes with the body (it is an expert action, not a
primary setup action).

Stage G — inactive Return views become one line. In `setup_context.cljs` the
not-in-use section shrinks to a single flex row: eyebrow "Return views", the
status ("Not used by Minimum risk" / "Not used"), and the existing switch
button, dropping the explainer sentence. Roles
(`portfolio-optimizer-return-views-inactive`, `…-activate`) and dispatches are
unchanged; under Maximum Sharpe the expanded views editor renders exactly as
today (owner decision, untouched).

Deferred (explicitly out of scope for this pass, needing either new actions,
owner sign-off, or heavy spec churn): universe filter tabs
(Included / Needs setup / Omitted — requires a new UI action and the full
action-contract surface incl. Lean sync); collapsing the 2D exposure pad behind
a "Fine-tune exposure" drawer (demotes the signature control; must force-open
when custom-from-holdings or off-policy; largest Playwright churn);
selection-driven master-detail rail; numbered section headers; merging the
"Why this preset is safe" / "What this model assumes" notes.

## Concrete Steps

Work from the worktree root. `npm run setup:worktree` once, then after each
stage `npx shadow-cljs compile test && node out/test.js` is too slow per-edit;
instead rely on `npm test` (the full node test build) at stage boundaries and
`npm run gates` (check + test + test:websocket, single PASS/FAIL matrix) at the
end. When gates pass, run the smallest relevant Playwright specs first:

    npm run test:e2e -- optimizer-proxy-loading-ux
    npm run test:e2e -- optimizer-history-assumptions-io
    npm run test:e2e -- optimizer-setup-rail-sticky

(or the repo's documented Playwright invocation from
`docs/BROWSER_TESTING.md` if it differs), then finish with browser QA on the
built app and stop any browser sessions started.

## Progress

- [x] (2026-07-10) Recon: read all touched views/view-models, located the
  exposure `exposure-preview` compliance fn, the grid-track CSS, and every test
  file pinning affected strings (`Scenario contract` in setup_layout_test:49,
  rail `Status`/ready-message/disclosure-note in
  view_model_history_assumption_cards_test:256-285, run-status labels in
  setup_actions_test).
- [x] (2026-07-10) Stage A: `run-verdict` in view-model.setup (+ facade
  delegate); footer `run-status` takes `:verdict` and renders an amber
  caution pill + dot; rail Status row (`verdict-value`) + exposure-policy
  warning card with `#portfolio-optimizer-constraints-panel` anchor;
  disclosure panels emit `:id` = data-role.
- [x] (2026-07-10) Stage B: ready banner, disclosure note, `:any-proxy?`, and
  the rail "Status" pair removed; rail header shows "N of M configured"
  (loading-gated, `data-loading` while any proxy history fetches).
- [x] (2026-07-10) Stage C: rail rows are keyed one-line `<details>` with
  `:summary` from the rail model; needs-input rows forced `:open`; configured
  chip is a ✓ glyph with `aria-label "Configured"`.
- [x] (2026-07-10) Stage D: "Run summary" rename + "History data" row
  ("N native · M via assumptions").
- [x] (2026-07-10) Stage E: duplicate sublabels suppressed; TYPE column
  by-exception with the `optimizer-universe-cols-4` static-CSS 4-track
  variant; sub-header count removed (Clear-only bar); source line reads
  "From holdings · N omitted"; goal-card "active" chip removed.
- [x] (2026-07-10) Stage F: center section is a keyed `<details>`
  "History assumptions" with hide-when-open trailing status
  (configured/needs-setup/loading/none-needed), forced `:open` exactly on
  attention; count role only exists while the workflow is non-empty.
- [x] (2026-07-10) Stage G: Return-views inactive is a single flex row
  (eyebrow · status · switch button), explainer sentence dropped, roles and
  dispatches unchanged.
- [x] (2026-07-10) Unit tests updated (layout, rail model, loading visibility,
  run-status caution, new `run-verdict` boundary tests); `npm test` 5356
  tests / 28827 assertions, 0 failures.
- [x] (2026-07-10) Namespace-size gate: setup_history_assumptions.cljs hit 543
  lines → editable field controls split to
  `setup_history_assumption_fields.cljs` (315 + ~240), same shape as the
  2026-07-06 panels split.
- [x] (2026-07-10) `npm run gates` full matrix PASS (34/34; 6062 tests /
  32172 assertions) after the docs/size fixes.
- [x] (2026-07-10) Playwright: optimizer-proxy-loading-ux,
  optimizer-history-assumptions-io (2 tests), optimizer-history-cache-swr,
  optimizer-setup-rail-sticky (2 tests) — 6/6 passed against the worktree's
  own compiled build served on :8090 (main checkout held :8080; SPA-fallback
  static-serve recipe). First three specs updated for the ✓ chip / rail count
  / ensure-open helper.
- [x] (2026-07-10) Browser QA on the built app (spectate boot on :8092,
  preview pane): verified the Run summary Status row + policy warning card
  agreeing with the footer pill ("Ready with 2 warnings" → "Ready with 3
  warnings" as data issues changed, byte-identical in both places); universe
  rows with suppressed duplicate sublabels (DOT one-line), no TYPE column, one
  count; the full needs-setup → recommended-setup → configured loop: section
  forced open with the PUMP card, then AUTO-COLLAPSED to "History assumptions
  — 1 of 1 configured" once proxy history settled; rail row collapsed to
  "PUMP · modeled on SOL, UNI - 80% vol - 5% cap · ✓" and expanded on click to
  the full 9 pairs with no Status pair; Return views one-liner; no console
  errors. Scratch static server on :8090 stopped after the run.
- [x] (2026-07-10) Plan lifecycle: moved to `docs/exec-plans/completed/`
  (deferred items recorded in Plan of Work and Outcomes).

## Surprises & Discoveries

- (2026-07-10, recon) The "disclosed in results" rail note is literally
  redundant: readiness already emits `:proxy-history-used` ("Used when direct
  history is limited.") as a folded info note in Data health whenever proxies
  are in play.
- (2026-07-10, recon) No test or spec pins the center section title
  "Proxy Workflow for Short-History Assets" — the rename is contract-free.
- (2026-07-10, recon) The external designer's "number the section headers 1-5"
  suggestion was already deliberated and rejected in this repo: the
  section-heading helper carries a comment ("No leading section number: the
  optimizer setup is a sovereign workbench, not an ordered wizard",
  setup_controls.cljs) — deferring it was the right call twice over.
- (2026-07-10) The universe layout test's `Type` assertion survives the
  by-exception column unchanged, because the SEARCH results header (where
  vaults/spot appear) legitimately keeps its Type column and supplies the
  string.
- (2026-07-10) The loading-visibility unit test pinned the rail "Status" pair
  as the mid-flight honesty carrier; that duty moved to the row chip
  (`:history-loading?`) and the new loading-gated header count, and the test
  now pins the pair's ABSENCE.

## Decision Log

- Decision: compute the unified verdict from `readiness-panel-model` + the
  existing `exposure-preview` compliance flags rather than injecting the policy
  violation into readiness `:warnings`.
  Rationale: raw readiness lists are consumed by history-status/assumption-card
  projections (documented invariant: group/aggregate purely in view-models);
  the policy violation is a constraints fact, not a data-health fact, and
  composing at the verdict level keeps both models pure and reusable.
  Date/Author: 2026-07-10 / Claude
- Decision: footer keeps its specific blocked labels ("Needs assumptions",
  "History incomplete", …) instead of adopting the generic "Action needed".
  Rationale: the specific reason is strictly more useful at the moment the CTA
  is gated; the unified model only changes the READY side (green vs amber).
  Date/Author: 2026-07-10 / Claude
- Decision: rail one-liners + forced-open-on-attention rather than deleting the
  rail assumption panel outright (the external designer's end state).
  Rationale: the master-detail "selected asset" rail needs a selection concept
  that does not exist on this page yet (row click currently toggles inclusion)
  and an answer for the Maximum Sharpe views editor slot; the one-liner list
  delivers most of the density win with zero new state, and upgrades cleanly
  later.
  Date/Author: 2026-07-10 / Claude
- Decision: keep the Exposure policy band rows in the Run summary (the external
  mock replaced them with the current target exposure).
  Rationale: the card is the receipt of what the solver will be sent; the
  target numbers already live in the center readout, and dropping the policy
  bands would remove the only place the user can verify them at a glance.
  Date/Author: 2026-07-10 / Claude
- Decision: suppress the TYPE column only when the selected universe is
  homogeneous, and keep the L/S side toggles (and their direction colors) as
  they are.
  Rationale: by-exception display for a column that is 78 identical badges; but
  side is a real risk-direction input in a trading product — a mostly-short
  book should stay visible at a glance (pushback recorded against the external
  review's "neutral segmented control").
  Date/Author: 2026-07-10 / Claude
- Decision: defer universe filter tabs, the exposure fine-tune drawer, the
  master-detail rail, and numbered section headers (recorded in Plan of Work).
  Rationale: tabs need a new action and the full action/effect contract surface
  (incl. Lean formal:sync); the pad drawer demotes the product's signature
  control days after a designer-driven pass promoted it and carries the largest
  Playwright churn — both need explicit owner sign-off on the specifics, not a
  silent landing inside a cleanup pass.
  Date/Author: 2026-07-10 / Claude

## Outcomes & Retrospective

Landed 2026-07-10. With a fully configured universe the setup page now states
"ready" exactly twice (footer verdict + Data health headline) instead of ~27
times; the proxy workflow rests as one summary line and forces itself open
only when something needs the user; each rail assumption is a one-line
disclosure over its full 9-pair detail; the asset list drops duplicate
sublabels, the homogeneous-universe TYPE column, and two of three count
restatements; an inactive Return views is one line; and — the trust fix — a
current portfolio outside the exposure policy turns the footer pill and the
rail Status row into the same amber "Ready with N warnings" with a warning
card that links to the exposure section. Validation: `npm run gates` 34/34
(6062 tests / 32172 assertions), 6/6 targeted Playwright tests against the
worktree build, and a live browser pass exercising the whole
needs-setup → configure → auto-collapse loop with zero console errors.

Complexity verdict: net REDUCTION. The change deletes two aggregate status
surfaces, one per-asset text pair, an explainer paragraph, and a corner chip,
and reuses existing mechanics (disclosure panels, hide-when-open trailing,
forced-open-on-attention, static-CSS grid variants) everywhere; the only new
abstractions are one pure view-model fn (`run-verdict`) that unifies two
previously independent status vocabularies and a namespace split forced by
the size gate (`setup_history_assumption_fields.cljs`, mechanical move).
Deferred, requiring their own owner sign-off (recorded in Plan of Work):
universe filter tabs (new action + contract surface incl. Lean sync),
exposure fine-tune drawer (demotes the signature pad; heaviest spec churn),
selection-driven master-detail rail, numbered section headers (already
deliberated and rejected once in setup_controls.cljs).

## Validation and Acceptance

Unit: `npm run gates` from the worktree root reports PASS for check, test, and
test:websocket. The updated tests pin: `run-verdict` levels/labels (ready /
caution with count / blocked / loading); `run-status` amber "Ready with 1
warning" when triggerable + off-policy verdict; rail model rows carry
`:summary` and no `"Status"` pair; rail model no longer exposes
`:ready-message` / `:disclosure-note`; the rail panel renders per-row
`<details>` with forced `:open` only for needs-input rows; the summary card
renders "Run summary", a History data row, and a Status row; universe rows omit
duplicate secondary labels and (in a homogeneous universe) the TYPE cell while
the 4-track class is present; the goal card renders no "active" chip; the
center section is a `<details>` with trailing status hidden-when-open and
`:open` forced exactly on attention; the inactive Return-views row keeps both
roles with no explainer paragraph.

Behavior (browser): with a fully configured universe the page shows "ready"
only in the footer + Data health; the proxy section rests collapsed with its
trailing count; expanding a rail assumption row reveals the full pairs; with
the current portfolio outside the gross band the footer reads "Ready with 1
warning" and the rail shows the warning card whose link scrolls to the
exposure section; clearing one assumption forces the proxy section open and
flips its trailing status to "1 needs setup".

## Idempotence and Recovery

All edits are plain view/view-model/CSS/test changes on a feature worktree —
re-running any stage is safe, and `git checkout -- <file>` reverts a stage
cleanly. No data, schema, action-contract, or wire-codec surface is touched
(that constraint is what the deferred items are deferred FOR). If a Playwright
spec fails structurally (section now collapsed), the fix is to open the
disclosure first (`details:not([open]) > summary` click) — never to weaken the
assertion.

## Artifacts and Notes

(Working evidence lands here as stages complete.)

## Interfaces and Dependencies

In `hyperopen.portfolio.optimizer.application.view-model.setup`:

    (defn run-verdict
      "Global run verdict shared by the footer pill and the rail Status row.
       readiness+history-load feed the data side; :off-policy? folds the
       exposure-policy compliance in. Returns {:level :ready|:caution|:blocked
       |:loading :label string :warning-count int}."
      [readiness history-load-state {:keys [off-policy?]}] …)

In `hyperopen.views.portfolio.optimize.setup-actions`, `run-status` gains an
optional `:verdict` key on its input map (absence preserves today's behavior so
existing callers/tests stay valid until updated).

The rail model rows (`history-assumption-rail-model` `:rows`) gain `:summary`
(string or nil); `:ready-message`, `:disclosure-note`, `:any-proxy?` are
removed; everything else is unchanged.
