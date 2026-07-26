# Optimizer Portfolio Exposure: Simplified Default View (Fine-Tune Drawer)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` are maintained in accordance with `/hyperopen/docs/PLANS.md`.

Durable context: direct user request (product owner, 2026-07-10) — the owner
supplied a designer mock of a simplified Portfolio exposure section ("very
busy, and it's distracting"), asked for it to be matched ("the way everything
else is in there looks nice"), and added one deletion beyond the mock: remove
the four exposure-preset buttons entirely ("I'm not even sure how that
functionality would work"). Fine details, theme fit, and how existing
functionality maps into the new layout were delegated ("I'll leave it up to
your best judgment"). This executes the "exposure fine-tune drawer" item
deferred by `docs/exec-plans/completed/2026-07-10-optimizer-setup-comprehension-redesign.md`,
which recorded it as needing exactly this owner sign-off.

## Purpose / Big Picture

The open "Portfolio exposure" panel currently shows three control paradigms at
once: preset chips, a 2D drag pad wrapped in captions/legend/hints
("POSITIONING", "Drag the dot to set target exposure.", "● Target — drag to
move ◌ Current", "gross leverage + net bias", "fewer trades ↔ tighter
tracking"), two band sliders, a CURRENT line with verdict, a MEMORY row, and
two groups of always-editable inputs. After this change the resting view is
the designer's: the pad (kept, compact) with a large stacked readout beside it
("19.5× gross" / "+10.5× net long", net tinted by direction), two quiet
read-only cards — "Risk guards" and "Rebalancing" — each with an Edit
disclosure, and one "Fine-tune exposure" disclosure that holds the band
sliders, the current-portfolio line, and the save-as-default memory row.
Presets are gone. Every existing control keeps its data-role and dispatch;
nothing is deleted from the model — the envelope actions, the advanced
min/max drawer, and the solver echo are unchanged.

To see it working: `npm run dev` (or the static-serve recipe), open
`/portfolio/optimize` with assets in the universe. The exposure section shows
pad + readout + the two cards; clicking "Edit" on Risk guards reveals the
per-asset cap input and the long-only / include-spot toggles in place;
"Fine-tune exposure" reveals the band sliders and memory controls; dragging
the pad still moves the target and the readout live; when the current book
violates the policy an amber warning line renders in the default view (the
rail's "Review warning →" anchor still lands on this panel).

## Context References

Public refs:
- Direct user request with designer mock (this session, 2026-07-10).

Repo artifacts:
- Parent: `docs/exec-plans/completed/2026-07-10-optimizer-setup-comprehension-redesign.md`
  (deferred-item record + the run-verdict/warning-card contract this must keep
  honest); `docs/exec-plans/completed/2026-06-30-optimizer-exposure-map-constraints.md`
  (the pad's origin).

Local scratch refs (non-authoritative):
- None.

## Context and Orientation

Views are ClojureScript hiccup (Replicant); "disclosure" means a native
`<details>`. Files touched:

- `src/hyperopen/views/portfolio/optimize/setup_constraint_controls.cljs` —
  owns the Portfolio exposure panel composition: header, holdings-seeded note,
  the "Positioning" group (pad), the Risk guards / Rebalance behavior groups,
  and the nested "Advanced solver limits" drawer. All editable rows
  (`constraint-row`, `long-only-row`, `include-spot-row`, `turnover-cap-row`)
  live here with pinned data-roles.
- `src/hyperopen/views/portfolio/optimize/setup_exposure_map.cljs` — the pad
  (SVG), axis frame + zoom, caption, legend, readout, preset chips, band
  sliders, current-portfolio preview line, memory/profile row, solver echo.
- `src/hyperopen/portfolio/optimizer/application/view_model/exposure.cljs` —
  `exposure-map-model` (loses only its `:presets` chip vector; `:active-preset`
  stays — the panel header label uses it).
- `src/hyperopen/views/portfolio/optimize/setup_controls.cljs` /
  `setup_context.cljs` — the lucide icon helper moves from setup-context into
  setup-controls so the new cards can share it.
- `src/styles/surfaces/optimizer/setup.css` — gains a `details[open] >
  summary .optimizer-section-open-only` counterpart to the existing
  hide-when-open rule (for "Edit" ↔ "Done" flips), keeps the existing pad CSS.
- Tests/specs: `tools/playwright/test/optimizer-exposure-map.spec.mjs`
  (caption/band/profile visibility, two preset tests, band-slider and stripes
  interactions), `tools/playwright/test/portfolio-regressions.spec.mjs`
  (turnover-cap toggle test must open the Rebalancing card first),
  `test/hyperopen/portfolio/optimizer/application/view_model/exposure_test.cljs`
  (`:presets` assertions). Hiccup-level unit tests see through closed
  `<details>`, so control-level assertions (long-only toggle etc.) survive
  unchanged.

Constraints honored: `<details>` never computes `:open` from user-toggled
state; every canonical control keeps its single data-role and dispatch; the
off-policy warning may not hide behind a closed drawer (run-verdict honesty);
removing the preset CHIPS does not remove the preset ACTION or domain
(`apply-portfolio-optimizer-exposure-preset` stays registered — deleting an
action is contract-surface churn with no user benefit).

## Plan of Work

In `setup_exposure_map.cljs`: delete the caption, legend, preset chip block,
and the old horizontal readout + two-column composition. Keep the SVG pad and
axis frame; the axis header becomes "Gross leverage (×)" left and "Net bias
(×)" + the −/+ zoom buttons right (the "view 0–N×" text goes; the y-tick and
zoom data-roles stay). The bottom axis drops the "◄ Short / Long ►" words for
colored end ticks (−N in error tint, 0×, +N in success tint). Export public
pieces for the panel to compose: `pad-frame` (wraps the frame in the
`portfolio-optimizer-exposure-map` role the specs assert), `readout` (stacked:
large gross value over its label, then the net value tinted by direction —
same `…-exposure-readout(-gross/-net)` roles), `bands-block` (both sliders,
unchanged roles), `preview-block` and `profile-row` (now public, unchanged),
and `policy-warning` (the existing off-policy sentence, rendered only when
violated; the quiet "Inside policy" state stays on the CURRENT line inside
Fine-tune).

In `setup_constraint_controls.cljs`: the panel body becomes — subtitle;
holdings-seeded chip (unchanged role, only when seeded); a full-width
"Fine-tune exposure" `<details>` whose summary renders as a right-aligned
bordered control with a chevron, body = bands-block + preview-block +
profile-row; a two-column grid with `pad-frame` + `readout` on the left and
the two cards on the right; the `policy-warning` line under the grid; the
unchanged Advanced solver limits drawer last. Each card is a `<details>`:
summary = icon (lucide shield / refresh-cw) + title ("Risk guards" /
"Rebalancing") + an "Edit" trailing that swaps to "Done" while open (new
`optimizer-section-open-only` CSS class), plus the read-only value rows
wrapped in `optimizer-section-trailing` so they hide while editing; body = the
existing editable rows exactly as they are today (per-asset cap, long-only,
include-spot / rebalance tolerance, turnover cap, dust threshold). Read-only
values format via the existing conventions: "15%", "On/Off", "No turnover cap"
or "1.00×", "3.0 pp", "$0". The "Positioning" / "Risk guards" / "Rebalance
behavior" group eyebrows and both group hints are deleted along with
`group-block` and `presets-block`.

In the view-model: drop `:presets` from `exposure-map-model` and update its
unit test. Move `lucide-node->hiccup`/`lucide-icon` into `setup_controls.cljs`
and repoint setup-context.

Playwright: in the exposure spec, drop the caption assertion, delete the two
preset tests, add an `openFineTune` helper before band/profile interactions,
and keep pad/zoom/drag/advanced tests as-is; in portfolio-regressions, open
the Rebalancing card before clicking the turnover toggle.

## Progress

- [x] (2026-07-10) Recon: full read of both view namespaces, the layout CSS,
  and every pinned selector in the exposure spec, regressions turnover test,
  layout test (long-only toggle assertions are hiccup-level and survive), and
  the exposure vm test.
- [x] (2026-07-10) Exposure-map ns: caption/legend/presets/old composition
  deleted; public pad-frame / stacked readout / bands-block / preview-block /
  profile-row / policy-warning; axis header carries both titles + zoom;
  colored −/+ end ticks.
- [x] (2026-07-10) Constraint-controls ns: fine-tune drawer, the two Edit
  cards (Risk guards / Rebalancing) with read-only value rows hidden while
  editing and Edit↔Done trailing, new pad+readout / cards grid, default-view
  policy-warning line, group-block/presets code removed.
- [x] (2026-07-10) Shared lucide helper in setup-controls (setup-context
  repointed); `optimizer-section-open-only` + `optimizer-plain-summary`
  (marker suppression) CSS.
- [x] (2026-07-10) View-model `:presets` removal + exposure vm test update.
- [x] (2026-07-10) Playwright: exposure spec rewritten (caption-gone +
  chips-gone assertions, openFineTune helper, preset round-trip replaced by a
  cards-expose-controls test — one wrong first draft asserted the CURRENT
  line in a walletless run and was corrected); regressions turnover test
  opens the Rebalancing card first.
- [x] (2026-07-10) `npm run gates` 34/34 PASS (6062 tests / 32174 assertions).
- [x] (2026-07-10) Playwright against the worktree build (:8090 static
  serve): all 9 exposure-map tests + the 2 affected regressions tests green.
  Browser QA on :8092: resting view is pad + stacked readout + two cards +
  Fine-tune button; the off-policy sentence shows by default; Fine-tune opens
  to bands/CURRENT/MEMORY; card markers suppressed; no console errors.
- [x] (2026-07-10) Plan moved to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- (2026-07-10, recon) The long-only/turnover unit assertions in
  setup_layout_test are hiccup-tree lookups, so moving those controls inside a
  closed `<details>` costs zero unit-test churn — only the two Playwright
  click-paths need an open step.

## Decision Log

- Decision: remove the preset CHIPS and the model's `:presets` vector but keep
  the `apply-portfolio-optimizer-exposure-preset` action and domain presets.
  Rationale: owner asked for the UI to go; deleting the action means
  action-contract + Lean surface churn for no user-visible gain, and
  `:active-preset` still labels the panel header ("Conservative" / "Custom ·
  from holdings").
  Date/Author: 2026-07-10 / Claude
- Decision: the pad keeps its zoom control (moved beside a new "Net bias (×)"
  label in the axis header) instead of moving into Fine-tune.
  Rationale: zoom is a view control of the pad itself; separating them makes
  the pad's scale surprising, and it keeps the zoom/drag Playwright tests
  intact.
  Date/Author: 2026-07-10 / Claude
- Decision: the off-policy warning sentence renders in the DEFAULT view (under
  the grid) while the quiet CURRENT/"Inside policy" line lives in Fine-tune.
  Rationale: the rail warning card and footer verdict link here; an actionable
  violation must not hide behind a closed drawer (honesty policy), but the
  healthy-state line is exactly the kind of reassurance the comprehension pass
  demotes.
  Date/Author: 2026-07-10 / Claude
- Decision: Edit cards flip their trailing to "Done" while open via a new
  `optimizer-section-open-only` CSS rule (counterpart of
  `optimizer-section-trailing`), and their read-only rows hide while editing.
  Rationale: zero new state, native `<details>` semantics, and the same
  mechanic the section headers already use — no controlled-open fighting the
  user's toggle.
  Date/Author: 2026-07-10 / Claude
- Decision: cards render all their rows read-only (including include-spot and
  dust threshold, which the mock omits), and "Rebalance behavior" renames to
  the mock's "Rebalancing"; tolerance keeps its honest "pp" unit rather than
  the mock's "bp".
  Rationale: hiding a live constraint's value entirely would trade noise for
  dishonesty; pp is the actual unit the domain stores and every other surface
  states.
  Date/Author: 2026-07-10 / Claude

## Outcomes & Retrospective

Landed 2026-07-10. The resting Portfolio exposure section is the designer's
quiet view: pad + large stacked readout, two read-only cards whose Edit
discloses the untouched canonical controls, one Fine-tune drawer for bands /
current-line / memory, and no presets, captions, legends, or group eyebrows.
Honesty held: the off-policy sentence stays in the default view and the rail's
Review-warning anchor still lands here. Validation: gates 34/34, 9/9 exposure
Playwright tests + 2/2 affected regressions tests against the worktree build,
live browser QA of the drag/Edit/Fine-tune loops.

Complexity verdict: net REDUCTION — the change deletes the preset chip system
from the UI and model, two captions, a legend, three group eyebrows and two
hints, and replaces a bespoke two-column composition with two reusable
mechanics (hide-when-open rows, show-when-open Done) on native <details>; the
additions are two small formatting helpers and one CSS rule pair. The preset
ACTION and domain presets remain (programmatic surface, header labeling), so
no contract surface moved.

## Validation and Acceptance

`npm run gates` reports 34/34 PASS. Playwright (worktree static-serve on
:8090): the updated `optimizer-exposure-map.spec.mjs` and the regressions
turnover test pass. Browser acceptance: resting section shows pad + stacked
readout + two read-only cards + Fine-tune control and nothing else besides
the subtitle/holdings chip; Edit on a card reveals the existing inputs in
place and "Done" collapses it; Fine-tune reveals band sliders whose drag still
updates the pad stripes and echo; pad drag still updates the readout; with an
off-policy book the amber sentence shows in the resting view and the rail's
"Review warning →" lands on the panel.

## Idempotence and Recovery

Pure view/CSS/spec edits on a feature branch; any stage reverts with
`git checkout -- <file>`. No action, effect, wire, or storage surface moves.
