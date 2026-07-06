# Optimizer Proxy Workflow: Volatility + Cap Become Auto-Set Risk Guardrails

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` are maintained in accordance with `/hyperopen/docs/PLANS.md`.

Durable context: direct user request (product owner, 2026-07-06) — in the proxy
workflow card the two always-visible inputs "Expected volatility (annualized)"
and "Max weight cap" ask the user to review numbers they have no way to
estimate; at minimum they should be hidden. A mean-variance-optimization expert
review (quoted in the request) confirms the model must keep both fields but
recommends turning them into auto-filled risk guardrails with an Edit
affordance, renaming "Expected volatility" to "Modeled annual volatility"
(the user is not forecasting; the optimizer is using this as its risk input)
and "Max weight cap" to "Max allocation cap". Parent feature ExecPlan:
`/hyperopen/docs/exec-plans/active/2026-07-05-optimizer-proxy-history-assumptions.md`.

## Purpose / Big Picture

The proxy workflow ("Proxy Workflow for Short-History Assets" section on the
optimizer setup route, `/optimize`) lets a user model a thin-history asset as a
basket of assets it behaves like. Today the card asks the user to do too much
at once: pick proxies, pick relationship strength, review an "Expected
volatility (annualized)" input and a "Max weight cap" input, then read three
basket panels and diagnostics. The two percent inputs are seeded automatically
(80% volatility, 5% cap) the moment a mode is chosen, so the user is being
asked to stare at numbers they almost never have grounds to change — the
opposite of the workflow's behavioral question "what does this asset behave
like?".

After this change the proxy card's visible asks are exactly the behavioral
ones (proxy assets + relationship strength, plus expected return when the
objective scores returns). Volatility and cap move into a collapsed
"Risk guardrails" disclosure directly below relationship strength: collapsed,
it reads as one line — "80% vol · 5% max" with an "Auto-set" tag (or "Edited"
once the user changes either) and an Edit affordance; open, it shows the two
renamed inputs plus one sentence each on why the model needs them. Nothing
changes in the model: both fields remain required by readiness, seeded by
`default-assumption`, consumed by the covariance synthesis, and mirrored into
per-asset constraint overrides. The conservative mode keeps its inputs inline
(those numbers ARE the conservative assumption) but adopts the same renamed
labels.

To see it working: run the dev app, open `/optimize`, add assets so the
universe holds at least one, then pick an asset in
"+ Model an asset with proxies…". The card that appears shows proxy search,
relationship strength, and a single collapsed "Risk guardrails" row reading
"80% vol · 5% max · Auto-set"; expanding it reveals "Modeled annual
volatility" and "Max allocation cap" inputs that still commit edits, and
editing either flips the tag to "Edited".

## Orientation: where everything lives

The optimizer UI is ClojureScript rendered with Replicant (hiccup vectors;
views are pure functions of view-model data). The relevant seams:

- `src/hyperopen/views/portfolio/optimize/setup_history_assumptions.cljs` —
  the proxy-workflow section view. `proxy-fields` renders the proxy card body
  (currently: proxy picker, relationship selector, a 2-column grid holding the
  volatility + cap `percent-input`s, optional expected-return input, the three
  basket panels, "How this works", diagnostics). `conservative-fields` renders
  the conservative body (return, volatility, cap inputs inline).
- `src/hyperopen/portfolio/optimizer/application/view_model/setup_history_assumption_cards.cljs`
  — builds the card projections views render. Field models come from
  `percent-field` (stored decimal 0.05 → `:percent-label "5%"`,
  `:input-text "5"`). Blocking warnings arrive per instrument via
  `warnings-by-id` and currently reach the card only as `:errors` messages.
- `src/hyperopen/portfolio/optimizer/application/view_model/setup_history_assumption_rail.cljs`
  — right-rail summary pairs; currently emits the pairs
  `["Expected volatility" …]` and `["Max weight cap" …]`.
- `src/hyperopen/portfolio/optimizer/domain/history_assumptions.cljs` — the one
  source of truth for the numeric presets: `default-conservative-volatility`
  0.8, `default-proxy-max-weight` 0.05, `default-conservative-max-weight`
  0.03, `default-max-weight`, `default-assumption` (seeds every field when a
  mode is chosen). Completeness checks live here too — volatility and cap are
  required (`first-missing-proxy-field` → `:volatility` / `:max-weight` /
  `:max-weight-exceeds-global`).
- `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` — turns
  those missing-field codes into blocking-warning copy (currently "needs an
  expected annual volatility." / "needs a max weight cap." /
  "max weight cap cannot exceed the global max asset weight.").
- `src/styles/surfaces/optimizer/setup.css` — carries the generic rule
  `.portfolio-optimizer details[open] > summary .optimizer-section-trailing
  { display: none }`: anything in a summary tagged `optimizer-section-trailing`
  shows only while the disclosure is collapsed. The guardrails row reuses it.
- Tests:
  `test/hyperopen/views/portfolio/optimize/setup_history_assumptions_test.cljs`
  (hiccup-tree assertions over roles/actions/strings),
  `test/hyperopen/portfolio/optimizer/application/view_model_history_assumption_cards_test.cljs`
  (card + rail projections),
  `test/hyperopen/portfolio/optimizer/application/setup_readiness_test.cljs`
  (warning copy).

Replicant gotchas that constrain the implementation (learned earlier in this
repo): unkeyed conditional siblings reset an open `<details>`, so the new
disclosure carries a `:replicant/key`; and a dynamically computed `:open`
attribute fights the user's own toggle, so the drawer never sets `:open` from
state — it starts collapsed, always.

## The change, precisely

In the cards view-model, every card gains a `:risk-guardrails` submodel built
from the entry and the per-instrument blocking warnings:

    {:auto?        true|false   ;; both values still equal the behavior's seeds
     :source-label "Auto-set" | "Edited"
     :summary      "80% vol · 5% max"   ;; percent-labeled; "--" for a missing side
     :attention?   true|false}  ;; a blocking warning targets vol/cap

`auto?` compares `:volatility` against
`history-assumptions/default-conservative-volatility` and `:max-weight`
against `(history-assumptions/default-max-weight behavior)`. `attention?` is
true when any of that instrument's blocking warnings has `:missing` in
`#{:volatility :max-weight :max-weight-exceeds-global}`.

In the view, `proxy-fields` drops the always-visible vol/cap grid and renders,
directly after the relationship selector, a `[:details]` drawer
(`data-role "portfolio-optimizer-history-assumption-guardrails-<id>"`,
`:replicant/key`, collapsed by default, never force-opened). Its `[:summary]`
row: eyebrow "Risk guardrails", then — wrapped in
`optimizer-section-trailing` so it hides while open — the summary value, the
Auto-set/Edited tag, and an "Edit" hint. The body: one short line explaining
volatility (sets the asset's total modeled risk; the basket only sets
co-movement) and the cap (limits allocation because the estimate rests on
proxy assumptions), then the two `percent-input`s with their existing
`data-role`s and actions, relabeled "Modeled annual volatility" and
"Max allocation cap". When `attention?` is true the drawer border/summary text
pick up the warning tint so the card-level error has a visible anchor.
`conservative-fields` only renames its labels to match. The rail pairs rename
to "Modeled volatility" / "Max allocation cap". Readiness warning copy renames
to "needs a modeled annual volatility." / "needs a max allocation cap." /
"max allocation cap cannot exceed the global max asset weight.".

Out of scope, recorded for the future: deriving the volatility default from
native realized vol / proxy-basket vol / peer floors (the expert's auto-default
formula) and confidence-tiered default caps. Both require the seed values to be
recomputed when history arrives (the entry is seeded at mode-set time, usually
before history/readiness settle), which is a behavior change to the draft
lifecycle, not presentation. The static, disclosed 80%/5% seeds plus the
specific-risk floor already keep the optimizer honest; the engine also
discloses `:effective-modeled-volatility` when the basket implies more risk
than stated.

## Progress

- [x] (2026-07-06) View-model: `:risk-guardrails` submodel (auto?/source-label/summary/attention?) on history-assumption cards; raw per-instrument warnings threaded into the card builder alongside the existing message-only `:errors`.
- [x] (2026-07-06) View: proxy card renders the collapsed Risk guardrails drawer after relationship strength (same vol/cap input roles + actions inside, labels renamed); conservative card labels renamed in place.
- [x] (2026-07-06) Rail + readiness copy renamed ("Modeled volatility", "Max allocation cap", "needs a modeled annual volatility.", "needs a max allocation cap.", "max allocation cap cannot exceed the global max asset weight.").
- [x] (2026-07-06) Namespace-size gate: split the read-only exposure-story panels (prior/regression/final/how-this-works/diagnostics) into `setup_history_assumption_panels.cljs` instead of adding a size exception (view was 512 lines vs the 500 cap; now 390 + 131).
- [x] (2026-07-06) Unit tests updated/extended: view test asserts the drawer is a collapsed `:details` with auto-set summary + renamed labels + intact input dispatches; view-model tests cover auto?/Edited/attention? and renamed rail pairs; readiness copy assertions renamed.
- [x] (2026-07-06) Gates: `npm run gates` 34/34 PASS (5867 tests, 31431 assertions) in this worktree.
- [x] (2026-07-06) Browser QA on the dev app (`/portfolio/optimize/new`, BTC carded via the workflow picker, ETH added as proxy): collapsed row reads "RISK GUARDRAILS · 80% vol · 5% max · AUTO-SET · EDIT"; open shows the why-copy and the renamed inputs; editing volatility to 60 flips the tag to EDITED and the collapsed summary to "60% vol · 5% max"; the drawer stays open across re-renders; no console errors from the drawer.

## Surprises & Discoveries

- (2026-07-06) Both fields are already auto-filled: `default-assumption` seeds
  volatility 0.8 and cap 0.05/0.03 the moment a behavior is chosen, and the
  field setters cannot even run before an entry exists. The "auto-set with
  Edit" product design therefore needs no lifecycle change — only the
  presentation moves.
- (2026-07-06) The cap is not literally redundant with the advanced overrides:
  the assumption's `:max-weight` is the source that `mirror-assumption-caps`
  (request builder) merges into `:per-asset-overrides`, taking the tighter of
  any existing override. Removing the field from the model would remove the
  guardrail itself, so it is hidden-not-removed, exactly as the expert review
  argued.
- (2026-07-06) Adding the ~50-line drawer pushed the view namespace over the
  500-line size gate (`npm run lint:namespace-sizes`). Rather than register an
  exception, the read-only exposure-story panels moved to a new
  `setup-history-assumption-panels` namespace — the same split shape the cards
  view-model itself used on 2026-07-05, and the panels are pure card → hiccup
  projections with no dispatches, so the seam is natural.

## Decision Log

- Decision: hide vol/cap behind a collapsed disclosure instead of deleting the
  inputs or moving them to the global advanced-overrides surface.
  Rationale: the model requires both per-assumption (readiness blocks without
  them); the per-asset override table only receives the mirrored cap and knows
  nothing of volatility, so "move it to advanced overrides" cannot host the
  pair. A per-card drawer keeps required-by-model / auto-provided /
  editable / disclosed all true.
  Date/Author: 2026-07-06 / Claude
- Decision: rename to "Modeled annual volatility" and "Max allocation cap"
  everywhere the pair surfaces (card inputs, rail, readiness warnings).
  Rationale: expert review — "expected volatility" reads as a forecast the
  user must produce; "modeled" states what the optimizer does with it.
  Date/Author: 2026-07-06 / Claude
- Decision: the drawer never sets `:open` from state (not even when a
  blocking warning targets a guardrail field); attention is carried by tint +
  the existing card-level error line.
  Rationale: a computed `:open` re-asserts itself against the user's own
  toggle (the value flips exactly while the user is interacting); and the
  missing-field state is nearly unreachable since defaults are always seeded —
  it requires deliberately blanking a field, which happens with the drawer
  already open.
  Date/Author: 2026-07-06 / Claude
- Decision: conservative mode keeps its three inputs inline, renamed.
  Rationale: the conservative assumption IS those numbers ("a high volatility
  with no diversification credit, pre-filled and editable"); collapsing them
  would leave a mode with no visible content.
  Date/Author: 2026-07-06 / Claude
- Decision: keep the static 80%/5% seeds; defer history-derived volatility
  defaults and confidence-tiered caps (recorded in scope note above).
  Rationale: presentation change vs. draft-lifecycle change; the current seeds
  are conservative, disclosed, and editable, and the engine already lifts and
  discloses effective modeled volatility when the basket implies more.
  Date/Author: 2026-07-06 / Claude
- Decision: expected return (shown only for return-seeking objectives) stays a
  visible primary input, outside the guardrails drawer.
  Rationale: it is a return view, not a risk guardrail; under return-seeking
  objectives it is a genuine user decision with no defensible auto value, and
  under the default minimum-variance objective it never renders anyway.
  Date/Author: 2026-07-06 / Claude

## Validation

Required gates (run from the worktree root after `npm run setup:worktree`):

    npm run gates        # check + test + test:websocket, single PASS/FAIL matrix

Unit-level acceptance, all in the existing three test namespaces: the proxy
card exposes `data-role portfolio-optimizer-history-assumption-guardrails-…`
as a `:details` element whose inputs still dispatch
`set-…-expected-volatility` / `set-…-max-weight-cap`; the card model carries
`:risk-guardrails` with `:auto?` true for a freshly-seeded entry, false once a
value departs the seed, `:attention?` true when a blocking warning names a
guardrail field; the rail emits `["Modeled volatility" "80%"]` /
`["Max allocation cap" "5%"]`; readiness messages carry the renamed copy.

Browser QA (exploratory, dev app): on `/optimize`, card a thin-history asset
via the workflow picker; observe the collapsed guardrails row with the
Auto-set summary, expand, edit volatility, observe the Edited tag and the
collapsed summary reflecting the new number. Confirm no console errors.

## Outcomes & Retrospective

Landed 2026-07-06. The proxy card's visible asks are now exactly the behavioral
ones — proxy assets and relationship strength (plus expected return under
return-seeking objectives) — while volatility and cap live in one collapsed
"Risk guardrails" row that reads its values, its Auto-set/Edited provenance,
and an Edit affordance. No model change: the fields stay required, seeded,
engine-consumed, and cap-mirrored; readiness still blocks when either goes
missing, with renamed copy. All 34 gates pass (5867 tests / 31431 assertions);
browser QA on the live dev app confirmed the collapse/expand/edit loop and
that the drawer survives re-renders (keyed `<details>`, no state-driven
`:open`).

What went as planned: the change stayed purely presentational because the
defaults were already seeded at mode-set time; input roles and actions were
preserved so no dispatch contract moved. What was discovered along the way:
the view namespace crossed the 500-line size gate and was split
(`setup_history_assumption_panels.cljs`) rather than excepted; and the rail
"Expected volatility"/"Max weight cap" labels plus three readiness messages
were the only other user-facing surfaces of the old vocabulary. Deferred
follow-up (recorded in scope note): history-derived volatility seeds and
confidence-tiered default caps, which require re-seeding draft entries when
history arrives.
