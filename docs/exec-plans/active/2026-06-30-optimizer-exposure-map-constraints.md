# Optimizer Constraints: 2D Exposure Map, Profiles, and Live Preview

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`,
`Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.
It must be maintained in accordance with `/hyperopen/docs/PLANS.md` and its detailed
contract `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Today the optimizer's `Constraints` panel exposes the solver's *implementation model*: a
stack of eight-plus numeric fields — `Gross exposure min`, `Gross exposure max`,
`Net exposure min`, `Net exposure max`, `Per-asset cap`, `Dust threshold`, `Turnover cap`,
`Rebalance tolerance` — that a trader must re-edit on every visit. A trader does not think
in lower/upper bounds. They think: "keep me around this leverage and this directional bias,
don't overtrade, don't concentrate, and remember what I set last time."

After this change a trader configures positioning by dragging a single point on a small 2D
map: the vertical axis is **gross leverage** (how much total exposure) and the horizontal
axis is **net bias** (how long or short). A shaded box around the point shows the exact
allowed range (the min/max band) that is sent to the solver, so nothing is hidden. Two small
sliders set how tight each band is, named presets (Conservative, Balanced, High Gross, Long
Bias) seed sensible starting points, the optimizer remembers the last-used policy per wallet
and per universe so the trader stops re-entering values, and a compact preview shows current
vs. target exposure and the estimated number of trades before running. The raw min/max
fields remain available in an `Advanced solver constraints` drawer for experts. The exact
numbers reaching the solver are always shown.

You can see it working by opening the optimizer setup (Portfolio → Optimize → New), expanding
`Constraints`, dragging the exposure point, and watching the `Gross: a–b× · Net: c–d×` echo
update; by clicking a preset; by pressing `Save as default`, reloading, and seeing the policy
restored; and by running the validation gates and Playwright spec described under
`Validation and Acceptance`.

## Context References

Public refs:
- Direct user request (maintainer, 2026-06-30): redesign the optimizer constraints UX so the
  many numeric fields collapse into one or two intuitive controls, remember sensible defaults,
  and specifically build the suggested 2D exposure control. The maintainer chose "Everything
  now" (2D map + advanced drawer + remember-last-used + named presets + live preview).

Repo artifacts:
- Builds on the optimizer setup workbench and constraint pipeline. Canonical model docs:
  `/hyperopen/ARCHITECTURE.md`, `/hyperopen/docs/FRONTEND.md`,
  `/hyperopen/docs/BROWSER_STORAGE.md`, and the agent guide
  `/hyperopen/docs/agent-guides/trading-ui-policy.md` (execution-critical behavior must not be
  hidden — hence "simple on top, exact underneath").
- Related completed plan for tone and structure:
  `/hyperopen/docs/exec-plans/completed/2026-06-28-optimizer-flow-simplification.md`.

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-06-30) Mapped every subsystem the redesign touches (UI, actions, view-model,
  solver wiring, persistence, CSS, tests, exec-plan format) and confirmed the core feasibility
  claim: target/band is purely a view representation; no solver or spec change is required.
- [x] (2026-06-30) Authored this ExecPlan.
- [x] (2026-06-30) M1 — Pure `exposure-policy` domain namespace + unit tests. Added
  `src/hyperopen/portfolio/optimizer/domain/exposure_policy.cljs` and its test ns; `npm test`
  green (4917 tests / 27006 assertions, 0 failures). Surprise: the system-default constraints
  (gross 2.0, net 1.0/1.0, cap 0.5) ARE exactly the Balanced preset, so `active-preset` returns
  `:balanced` for a fresh draft — corrected the test to assert that.
- [x] (2026-06-30) M2 — Exposure actions (`actions/exposure.cljs`), `:event/clientY` +
  `:event/pointer-buttons` placeholders, and all contract surfaces (facade, runtime catalog,
  registration rows, action_args specs + ceiling bump). `npm test` green; drift gate green.
  Note: handlers write the WHOLE constraints map (so a zero gross band can DISSOC `:gross-min`).
- [x] (2026-06-30) M3 — 2D exposure-map view (`setup_exposure_map.cljs`), exposure view-model
  (`view_model/exposure.cljs`), panel restructured into Positioning / Risk guards / Rebalance /
  Advanced groups (`setup_constraint_controls.cljs`), current-exposure threaded through
  `control-rail`, CSS block + range reset. `npm test`, `lint:hiccup`, `lint:theme-colors`,
  `lint:input-parsing`, `lint:namespace-boundaries`, `test:styles` pass; `portfolio` build
  compiles clean. Surprise: existing view tests + Playwright key on the original constraint
  `data-role`s, so each canonical control appears exactly once (no duplication), with the raw
  gross/net min/max in the Advanced drawer.
- [ ] M4 — Named presets and live pre-run preview.
- [ ] M5 — Profile memory persistence (remember last-used per wallet + universe).
- [ ] M6 — Workbench scene, Playwright spec, and full validation gates.

## Surprises & Discoveries

- Observation: The drag interaction is already supported by existing dispatch placeholders.
  Evidence: `src/hyperopen/registry/runtime.cljs` registers `:event/clientX` (L80) and
  `:event.currentTarget/bounds` (L84) — the latter returns the pad's bounding rect. Only
  `:event/clientY` and a pointer-`buttons` placeholder are missing. The pad's coordinate math
  can therefore live in a pure action (it receives `client-x`, `client-y`, the `bounds` map,
  and `buttons` as plain values), with no DOM access in the handler.
- Observation: Constraints are two-layer; the solver never sees `gross-min`/`net-min` names.
  Evidence: `application/request_builder.cljs` `normalize-constraints` (L48-84) renames
  `gross-max→:gross-leverage`, `gross-min→:gross-floor`, and collapses `net-min`/`net-max`
  into `:net-exposure {:min :max}`. A UI representation that round-trips to the draft keys is
  invisible to the solver.
- Observation: `gross-min` is nilable and absent by default (no floor); `net-min`/`net-max`
  default to `1.0`/`1.0`. Evidence: `defaults.cljs` `:constraints` (L15-28). The policy math
  must treat a zero gross band as "ceiling only, no floor" (clear `gross-min`) to preserve the
  default semantics.
- Observation: Two registry namespaces are at their size ceiling and retire today.
  Evidence: `dev/namespace_size_exceptions.edn` — `action_args.cljs` 642 (file ~633),
  `draft.cljs` 580 (file ~558). New actions must go in a *new* actions namespace, and the
  `action_args.cljs` ceiling must be bumped (it is an append-only registry expected to grow).

## Decision Log

- Decision: Keep `[:draft :constraints]` as the canonical flat min/max model; introduce
  target/band only as a view + action-time representation.
  Rationale: The solver, specs, and persistence already consume min/max. A pure-UI
  representation that converts back to min/max before `normalize-constraints` needs zero solver
  or spec change and keeps `Advanced` raw editing trivially correct.
  Date/Author: 2026-06-30 / Claude (maintainer-directed).

- Decision: The 2D pad sets *targets*; bands are separate; a zero gross band clears the gross
  floor.
  Rationale: This is the honest mapping given `gross-min` is nil-by-default. Gross band 0 =
  "cap leverage at the target, no floor" (matches the system default). Gross band > 0 =
  "keep leverage within ± of target" (the seed-from-current floor behavior). Net always writes
  both `net-min`/`net-max` symmetric about the target.
  Date/Author: 2026-06-30 / Claude.

- Decision: New actions live in a new `actions/exposure.cljs` namespace, not in `draft.cljs`.
  Rationale: `draft.cljs` is near its line ceiling and the exposure/profile logic is a cohesive
  unit worth isolating. `action_args.cljs`'s ceiling is bumped (append-only registry).
  Date/Author: 2026-06-30 / Claude.

- Decision: Profiles persist as one IndexedDB record per wallet — value is a map of
  `universe-hash → profile`. Loaded once on optimizer route load into
  `[:portfolio :optimizer :constraint-profiles]`; the view-model selects the entry matching the
  current universe.
  Rationale: One read / one write per wallet, no `get-all` scan, mirrors the existing
  scenario-persistence chain exactly. Universe-hash is a stable hash of the *normalized
  universe instrument-id set only* (not the full input signature) so constraint edits do not
  relabel the profile.
  Date/Author: 2026-06-30 / Claude.

- Decision: Live preview is honest, not a hidden solve. It shows current-vs-target exposure
  always, and estimated-trades / binding-constraints only when a `last-successful-run` for the
  current draft exists; otherwise it says "Run to preview estimated trades."
  Rationale: Running a real solve on every constraint tweak is expensive and out of scope; a
  fabricated estimate would violate the trading-UI honesty policy.
  Date/Author: 2026-06-30 / Claude.

## Outcomes & Retrospective

To be completed at milestone boundaries. Track whether the change reduced or increased overall
complexity. Expectation: net UI complexity for the common path drops sharply (one drag vs.
eight fields); internal complexity rises modestly (one pure namespace, one new actions
namespace, one persistence triple), all additive and behind the existing constraint model.

## Context and Orientation

The optimizer lives under `src/hyperopen/portfolio/optimizer/` (pure domain, application,
actions, contracts, infrastructure) and `src/hyperopen/views/portfolio/optimize/` (Replicant
hiccup views). The app is ClojureScript with a data-oriented state atom, pure action handlers
that return effect descriptors, and dedicated boundary namespaces for DOM/storage. "Replicant"
is the hiccup renderer; "nexus" is the action/effect dispatch runtime. "Hiccup" is Clojure data
describing HTML, e.g. `[:div {:class ["x"]} "hi"]`. A "placeholder" is a keyword like
`[:event.target/value]` inside a dispatched action vector that the runtime resolves from the DOM
event at click/drag time.

Key existing files (full paths from repo root):

- `src/hyperopen/portfolio/optimizer/defaults.cljs` — `default-draft` and the canonical
  `:constraints` defaults.
- `src/hyperopen/portfolio/optimizer/actions/draft.cljs` — `set-portfolio-optimizer-constraint`
  (L368): the existing single-key constraint write. Model new handlers on it. Reads of state
  are allowed (handlers take `[_state & args]`).
- `src/hyperopen/portfolio/optimizer/actions/draft_options.cljs` — `numeric-constraint-keys`,
  `clearable-numeric-constraint-keys`, `boolean-constraint-keys` whitelists (L44-60).
- `src/hyperopen/portfolio/optimizer/actions/common.cljs` — `save-draft-path-values` (L12)
  emits one `[:effects/save-many ...]` and always appends `[draft-dirty-path true]`;
  `parse-number-value`, `parse-boolean-value`, `normalize-keyword-like`, `constraint-list`.
- `src/hyperopen/portfolio/optimizer/actions.cljs` — flat facade re-exporting per-namespace
  handlers (constraint at L52). New handlers need a `def` here.
- `src/hyperopen/portfolio/optimizer/runtime_catalog.cljs` — `action-deps` (L7, dep-key →
  handler fn) and `effect-deps` (L184, dep-key → effect adapter fn).
- `src/hyperopen/schema/runtime_registration/portfolio.cljs` — `action-binding-rows` and
  `effect-binding-rows` (public `:actions/…`/`:effects/…` keyword → dep-key).
- `src/hyperopen/schema/contracts/action_args.cljs` — per-action arg specs and the
  registration map (constraint at L94/L275). A drift test enforces set-equality between
  registered and contracted action ids; `::any-args` fallback is forbidden.
- `src/hyperopen/registry/runtime.cljs` — `register-placeholders!` (L50).
- `src/hyperopen/portfolio/optimizer/contracts/paths.cljs` (+ re-export
  `…/optimizer/contracts.cljs`) — the ONLY place `[:portfolio :optimizer …]` literals may live.
- `src/hyperopen/portfolio/optimizer/contracts/specs.cljs` — `constraints?` (L187) open-map
  validator; add new numeric keys to its finite whitelist (none needed: we reuse existing keys).
- `src/hyperopen/portfolio/optimizer/application/request_builder.cljs` — `normalize-constraints`
  (L48); the single draft→solver translation seam. Unchanged by this work.
- `src/hyperopen/views/portfolio/optimize/setup_constraint_controls.cljs` — the panel to
  restructure (`constraints-section` L176).
- `src/hyperopen/views/portfolio/optimize/setup_controls.cljs` — `disclosure-panel`,
  `disclosure-heading`, `eyebrow-class`, `input-class`, `section-heading`.
- `src/hyperopen/views/portfolio/optimize/setup_sections.cljs` — `control-rail` (L14) renders
  `constraints-section` at L27; `Advanced Overrides` drawer (L28-33) is the template for a new
  collapsible drawer.
- `src/hyperopen/views/portfolio/optimize/target_sigma.cljs` — `sigma-slider` (L96): the only
  existing `type=range` control; mirror it for band sliders.
- `src/hyperopen/views/portfolio/optimize/infeasible_panel.cljs` — `highlighted-control-keys`
  (L96) returns the set of infeasible constraint keys (`:gross-max`, `:net-min`, …); the new
  control reuses this for warning highlights.
- `src/hyperopen/portfolio/optimizer/infrastructure/persistence.cljs` — wallet-scoped IndexedDB
  boundary; `draft-key`/`save-draft!` triple pattern to mirror for profiles.
- `src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` and
  `…/portfolio_optimizer_scenarios.cljs` — effect adapter layer; `effective-account-address`
  (account/context.cljs L259) yields the wallet+subaccount identity as one normalized address.
- `src/hyperopen/portfolio/optimizer/contracts/signatures.cljs` — `optimizer-input-keys` (L5)
  includes `:universe`; basis for a universe-only hash helper.
- CSS: `src/styles/surfaces/optimizer/setup.css`, `…/base.css` (tokens + range reset L159-170),
  `src/styles/surfaces/optimizer.css` (aggregator). Rules are `@layer components { .portfolio-optimizer … }`,
  tokens only (no raw colors; `lint:theme-colors` ratchet), static grid tracks (no arbitrary
  Tailwind `grid-cols-[…]`).
- Tests: `test/hyperopen/portfolio/optimizer/draft_actions_test.cljs`
  (`set-draft-constraint-normalizes-supported-values-test` L311) is the model for action tests;
  `test/hyperopen/schema/contracts_coverage_test.cljs` is the drift gate. Run `npm test`
  (shadow-cljs node test), `npm run check`, `npm run test:websocket`, or `npm run gates`.
  Playwright: `tools/playwright/test/*.spec.mjs`, run `npx playwright test <file>`.

## Plan of Work

The work is sequenced into six milestones, each independently verifiable. Land and commit each
before starting the next; if work pauses, completed milestones leave a strictly better,
green product.

### The constraint model (shared by all milestones)

Canonical state (unchanged): `[:portfolio :optimizer :draft :constraints]` holds
`{:long-only? :include-spot? :gross-min :gross-max :net-min :net-max :max-asset-weight
:dust-usdc :max-turnover :rebalance-tolerance …}`. `gross-min` is nilable (no floor when nil).

Target/band view representation (new, pure):

    net-target  = (net-min + net-max) / 2        (both present by default)
    net-band    = (net-max - net-min) / 2
    gross-target = gross-min present ? (gross-min + gross-max)/2 : gross-max
    gross-band   = gross-min present ? (gross-max - gross-min)/2 : 0

Reverse (policy → canonical writes), preserving nil-floor semantics:

    net-min = net-target - net-band ;  net-max = net-target + net-band   (always both)
    gross-max = gross-target + gross-band
    gross-min = gross-band > eps ? max(0, gross-target - gross-band) : NIL  (clear floor)

Axis scales for the pad (display domain, defined once in the pure namespace; advanced fields
may exceed them):

    gross Y axis: 0 .. 3.0×           (top = higher gross)
    net   X axis: -2.0× .. +2.0×      (right = more long)
    target snap step: 0.05×

Pad fraction math (pure; the action receives plain numbers, no DOM):

    fx = clamp((client-x - left) / width, 0, 1)
    fy = clamp((client-y - top)  / height, 0, 1)
    net-target   = snap(-2.0 + fx*4.0)
    gross-target = snap(3.0 * (1 - fy))            ; invert Y so up = higher gross
    gross-target = max(gross-target, abs(net-target))   ; gross >= |net| (physical)

### Milestone 1 — Pure exposure-policy domain core

Goal: a single pure namespace owning every number, so the view, the action, and tests share one
source of truth. Nothing user-visible yet; proven by unit tests that fail before and pass after.

Create `src/hyperopen/portfolio/optimizer/domain/exposure_policy.cljs` with:

- Constants: `gross-axis-max` (3.0), `net-axis-extent` (2.0), `target-snap` (0.05),
  `band-eps` (1e-6), `max-band` (0.5).
- `constraints->policy [constraints] → {:gross-target :gross-band :net-target :net-band}`
  implementing the forward map above (nil-safe; gross-min nil ⇒ band 0, target = gross-max;
  net side nils handled).
- `policy->constraint-writes [policy] → [[k v] …]` returning the canonical path-key/value pairs
  for `:gross-max :gross-min :net-min :net-max` honoring the nil-floor reverse map; values
  rounded to 4 decimals; `gross-min` value is `nil` when band ≤ eps.
- `point->targets [{:keys [client-x client-y bounds buttons]}] → {:gross-target :net-target} | nil`
  returns `nil` when `buttons` is 0/blank (a hover, not a drag) or bounds are degenerate;
  otherwise the snapped, clamped targets per the fraction math.
- `policy-marker [policy] → {:x-fraction :y-fraction}` and
  `exposure->fraction`/`current-exposure-marker [{:keys [gross net]}]` for plotting the current
  portfolio dot.
- `apply-point [constraints targets] → constraints'` and
  `apply-band [constraints axis value] → constraints'` (axis ∈ `#{:gross :net}`): pure
  transforms that recompute min/max from the new target/band while preserving the other axis.
- `presets` map: `:conservative :balanced :high-gross :long-bias` → a partial constraints map.
  Suggested values (record exact chosen values in the Decision Log if adjusted):
  conservative gross≤1.0 net 0±0 cap 0.25; balanced gross≤2.0 net 1.0±0 cap 0.5; high-gross
  gross≤3.0 net 1.0±0 cap 0.5; long-bias gross≤2.0 net 1.5±0.25 cap 0.5.
- `apply-preset [constraints preset-key] → constraints'` merging the preset partial.
- `active-preset [constraints] → preset-key | :custom` for chip highlighting.
- `system-default-constraints [] → constraints` (delegates to `defaults/default-draft`
  `:constraints`) for the `Reset to system` action.

Tests `test/hyperopen/portfolio/optimizer/domain/exposure_policy_test.cljs`: forward map for
the default constraints (gross target 2.0 band 0; net target 1.0 band 0); a seeded floor case
(gross 1.91..1.92 → target 1.915 band 0.005); reverse round-trip; nil-floor preservation
(gross band 0 ⇒ gross-min nil write); net symmetric writes; `point->targets` for corner/centre
fractions and the hover (buttons 0 ⇒ nil) and gross ≥ |net| clamp cases; each preset; and
`active-preset`.

Acceptance: `npm test` passes with the new tests; they fail if the namespace is absent.

### Milestone 2 — Actions, placeholders, and contract surfaces

Goal: the pad's "brain" — atomic actions wired through every contract surface, drift gate green.

1. Placeholders (`src/hyperopen/registry/runtime.cljs`, in `register-placeholders!`): add
   `:event/clientY` (`(some-> dom-event .-clientY)`) and `:event/pointer-buttons`
   (`(some-> dom-event .-buttons)`). (`:event/clientX` and `:event.currentTarget/bounds` exist.)

2. New actions namespace `src/hyperopen/portfolio/optimizer/actions/exposure.cljs`. Handlers are
   pure `[state & args] → effect-vector`; read current constraints via
   `(get-in state contracts/draft-constraints-path)`; emit via
   `common/save-draft-path-values`:
   - `set-portfolio-optimizer-exposure-point [state client-x client-y bounds buttons]` —
     `exposure-policy/point->targets`; if nil return `[]`; else `apply-point` and write the
     changed `:gross-*`/`:net-*` pairs.
   - `set-portfolio-optimizer-exposure-band [state axis value]` — parse number, `apply-band`,
     write the changed pairs for that axis.
   - `apply-portfolio-optimizer-exposure-preset [state preset]` — `apply-preset`, write the full
     constraints diff.
   - `reset-portfolio-optimizer-constraints-to-system [state]` — write
     `system-default-constraints` over the draft constraints.

3. Facade `actions.cljs`: `def` each new handler.

4. `runtime_catalog.cljs` `action-deps`: add four dep-key → handler entries.

5. `runtime_registration/portfolio.cljs` `action-binding-rows`: add four
   `[:actions/… :…]` rows. (These are pure draft writes — do NOT add to
   `effect-order-policy-required-action-ids`.)

6. `action_args.cljs`: add specs + registration entries. Reuse where possible —
   `exposure-band` → `::portfolio-optimizer-key-value-args`; `exposure-preset` →
   `::portfolio-optimizer-model-kind-args`; `reset…` → `::common/no-args`. Add one new spec
   `::portfolio-optimizer-exposure-point-args` `(s/tuple any? any? any? any?)` for the
   point setter (four positional args: x, y, bounds-map, buttons). Bump the
   `action_args.cljs` ceiling in `dev/namespace_size_exceptions.edn` (append-only registry) and
   extend its `:retire-by`.

7. `draft_options.cljs`: no new keys required (we write existing `:gross-*`/`:net-*`). Confirm
   `:net-min`/`:net-max` need not be clearable — the band model always writes both. `:gross-min`
   is already clearable, which is what the reverse-map nil write needs.

Tests `test/hyperopen/portfolio/optimizer/exposure_actions_test.cljs`: each handler's exact
effect vector (model on `draft_actions_test.cljs` L311) — including a drag inside known bounds
producing specific gross/net writes, a hover (buttons 0) producing `[]`, a band change, a
preset, and reset. `contracts_coverage_test.cljs` must stay green (run it explicitly).

Acceptance: `npm test` green; dispatching `:actions/set-portfolio-optimizer-exposure-point`
from a REPL/test with a bounds map writes the expected constraints.

### Milestone 3 — The 2D exposure-map view, panel restructure, view-model, CSS

Goal: the visible centerpiece. The `Constraints` panel becomes four labelled groups —
Positioning (the exposure map), Risk guards, Rebalance behavior, and an Advanced drawer.

1. View-model: add `exposure-map-model [draft current-exposure highlighted-controls]` to
   `application/view_model/setup.cljs` (or a new `view_model/exposure.cljs` if `setup.cljs`
   nears its ceiling) returning a pure display map: policy, pad marker fractions, current-exposure
   marker, band-slider values, the generated-constraints echo strings
   (`gross a–b× · net c–d×`), preset chips with `active?`, and per-control `highlighted?` flags.
   `current-exposure` (current gross/net) comes from `application/current_portfolio.cljs`.

2. View `src/hyperopen/views/portfolio/optimize/setup_exposure_map.cljs`:
   - An inline SVG pad (`viewBox` 0..100 square): background grid, zero-net vertical guide,
     axis tick labels (gross 0/1/2/3×, net −2/0/+2×), the shaded band rectangle, the target
     point handle, and a faint current-portfolio dot. The SVG root has
     `:on {:pointerdown … :pointermove …}` each dispatching
     `[[:actions/set-portfolio-optimizer-exposure-point [:event/clientX] [:event/clientY]
       [:event.currentTarget/bounds] [:event/pointer-buttons]]]`. `data-role`
     `portfolio-optimizer-exposure-pad`; handle/markers carry their own data-roles.
   - Two `type=range` band sliders (mirror `sigma-slider`) dispatching
     `[[:actions/set-portfolio-optimizer-exposure-band :gross [:event.target/value]]]` (and
     `:net`) on `:input`; numeric echo of each band.
   - Preset chips (buttons) dispatching `:actions/apply-portfolio-optimizer-exposure-preset`;
     `aria-pressed` on the active one.
   - The read-only `Generated solver constraints` echo line.
   - Keyboard a11y: the pad handle is focusable and arrow keys nudge the target via the band/point
     actions (use the existing value placeholder path; arrow handling may dispatch
     preset/point with stepped values). At minimum the sliders and chips are fully keyboard
     operable; document any pad-keyboard limitation in `Surprises & Discoveries`.

3. Restructure `setup_constraint_controls.cljs`:
   - `Positioning` group → `setup-exposure-map/exposure-map` (replaces the four gross/net
     min/max `constraint-row`s).
   - `Risk guards` group → per-asset cap, long-only, include-spot (existing rows/toggles).
   - `Rebalance behavior` group → rebalance tolerance, turnover cap, dust (existing rows),
     framed as "fewer trades ↔ tighter tracking".
   - `Advanced solver constraints` drawer (a nested `disclosure-panel`) → the raw gross-min,
     gross-max, net-min, net-max, per-asset cap, dust, turnover, rebalance-tolerance
     `constraint-row`s, so experts keep exact control. Editing a raw field flips the preset to
     `Custom` automatically (it already writes canonical keys; `active-preset` derives `Custom`).
   - Thread `current-exposure` into `constraints-section` from `control-rail`
     (`setup_sections.cljs`) — it already has `state`; derive current exposure there.

4. CSS: add a `.optimizer-exposure-map` block to `src/styles/surfaces/optimizer/setup.css`
   (inside `@layer components`), static grid tracks, tokens only
   (`--optimizer-accent`, `--optimizer-border`, `--optimizer-long`, `--optimizer-short`,
   `--optimizer-text-2`); add `.optimizer-exposure-band` to the range-reset selector list in
   `base.css` L159-170. SVG strokes/fills reference the tokens via CSS classes (no inline raw
   colors).

Acceptance: in the workbench scene (M6) and the running app, dragging moves the point and box
and updates the echo; band sliders widen/narrow the box; presets jump the point; the Advanced
drawer still edits raw fields and flips the preset to Custom. `npm run lint:hiccup`,
`lint:theme-colors`, `lint:input-parsing`, and `test:styles` pass.

### Milestone 4 — Named presets and live pre-run preview

Goal: faster starting points and an honest preview. Presets are already wired in M2/M3; this
milestone finalizes their values against real solver behavior and adds the preview block.

- Validate/adjust preset values by running each through the solver in a scenario and confirming
  feasibility on a representative universe; record final values in the Decision Log.
- Add `exposure-preview-model [draft current-exposure last-successful-run] →
  {:current {:gross :net} :target {:gross-range :net-range} :estimated-trades :binding [...]
  :runnable? :has-estimate?}` to the view-model. `estimated-trades` and `binding` are derived
  from `last-successful-run` rebalance preview / diagnostics for the current draft only
  (`application/rebalance_preview.cljs`, `domain/diagnostics.cljs`); when no matching run exists,
  `:has-estimate? false` and the view shows "Run to preview estimated trades."
- Render the preview block in `setup_exposure_map.cljs` (or a sibling) below the echo.

Acceptance: with a solved run loaded, the preview shows current vs. target gross/net, an
estimated trade count, and binding constraints; with no run, it shows the honest hint. No solve
is triggered by editing constraints.

### Milestone 5 — Profile memory persistence (remember last-used)

Goal: stop re-entering values. The optimizer remembers the policy per wallet + universe and
applies it; `Save as default` / `Reset` are explicit; auto-apply happens when a universe with a
saved default is loaded.

1. Paths: add `constraint-profiles-path` (`(conj optimizer-path :constraint-profiles)`) to
   `contracts/paths.cljs` + the catalog; re-export via `contracts.cljs`. Seed
   `:constraint-profiles {}` in `defaults/default-optimizer-state`.
2. Universe hash: add `universe-hash [draft] → string` (stable hash of the sorted, normalized
   universe instrument-id set only) near `contracts/signatures.cljs`.
3. Persistence boundary `infrastructure/persistence.cljs`: add `constraint-profiles-key`
   (`"constraint-profiles::<addr>"`), `save-constraint-profiles!`, `load-constraint-profiles!`
   using the existing `encode-record`/`decode-record` envelope; records include `:saved-at-ms`
   and `:version` per `BROWSER_STORAGE.md`.
4. Effect adapters `effect_adapters/portfolio_optimizer.cljs` (+ `…_scenarios.cljs`): dynamic-var
   seams `*save-constraint-profiles!*` / `*load-constraint-profiles!*`, and effect fns
   `save-portfolio-optimizer-constraint-profiles-effect` /
   `load-portfolio-optimizer-constraint-profiles-effect` resolving the address via
   `effective-account-address` (no-op on nil/spectate; gate writes on `mutations-allowed?`).
5. Dep-keys + descriptor binding rows in `runtime_catalog.cljs` `effect-deps` and
   `runtime_registration/portfolio.cljs` `effect-binding-rows`.
6. Actions (in `actions/exposure.cljs`):
   - `save-portfolio-optimizer-constraint-default [state]` — write current constraints under the
     current universe-hash into `constraint-profiles` state and emit the persist effect.
   - `receive-portfolio-optimizer-constraint-profiles [state profiles]` — store the loaded map
     into state; if a default exists for the current universe and the draft is pristine
     (`:metadata :dirty?` false), apply it to the draft.
   - `reset-portfolio-optimizer-constraints-to-system` (from M2) doubles as the `Reset` button.
   - Register these (catalog/registration/action_args). The load is dispatched from the existing
     `load-portfolio-optimizer-route` action (append the load effect) and after a successful run
     to remember last-used; the receive action is dispatched by the load effect adapter.
7. View: a `Profile` row at the top of the panel — current source label
   ("Using: Last used · this wallet + universe" / "System defaults"), `Save as default`, and
   `Reset to system` buttons.

Tests: `exposure_actions_test` extends to cover save/receive/apply with the dynamic-var
persistence seams stubbed; nil-address is a no-op; pristine-vs-dirty auto-apply. Coverage gate
stays green.

Acceptance: set a policy, `Save as default`, reload the route (or re-enter the universe) — the
saved policy is restored; switching to a universe without a saved default shows system defaults;
spectate/no-wallet does not persist.

### Milestone 6 — Workbench scene, Playwright, and gates

- Add `portfolio/hyperopen/workbench/scenes/optimize/exposure_scenes.cljs` (model on
  `execution_scenes.cljs`) seeding a draft with constraints + a current-exposure marker, so the
  exposure map renders in the Storybook-style workbench at `ui-workbench.html:8080`
  (`?id=…/exposure-scenes/…`). Wrap in `.portfolio-optimizer`.
- Add `tools/playwright/test/optimizer-exposure-map.spec.mjs`: load the optimizer setup, expand
  Constraints, assert the pad and echo render, drag the pad (pointer down+move) and assert the
  echo updates, move a band slider and assert the range widens, click a preset and assert the
  echo, open the Advanced drawer and assert raw fields, and `Save as default` then reload to
  assert persistence. Use `data-role` selectors.
- Run, in order: `npm run setup:worktree`, `npm run gates` (single PASS/FAIL matrix for
  `check` + `test` + `test:websocket`), then `npx playwright test
  tools/playwright/test/optimizer-exposure-map.spec.mjs` (broaden to the optimizer smoke set
  after it passes). Browser-verify the live app in the preview per repo routing.

## Concrete Steps

All commands run from the worktree root
`/Users/barry/projects/hyperopen/.claude/worktrees/fervent-ishizaka-4fbf7a`.

    npm run setup:worktree          # link node_modules (idempotent)
    npm test                        # shadow-cljs compile test && node out/test.js
    npm run test:websocket          # ws runtime determinism
    npm run gates                   # full PASS/FAIL matrix (check + test + websocket)
    npx playwright test tools/playwright/test/optimizer-exposure-map.spec.mjs

Per-milestone, prefer the fastest relevant command first (e.g. `npm test` for M1/M2 logic;
`npm run lint:hiccup && npm run test:styles` for M3 view/CSS) and broaden to `npm run gates`
before marking a milestone complete.

## Validation and Acceptance

Behavioral acceptance (a human can verify): open Portfolio → Optimize → New, expand
`Constraints`. Observe a 2D pad with a draggable point and a shaded band box; drag it and watch
`Generated solver constraints: gross a–b× · net c–d×` change live; widen a band slider and watch
the box grow; click `Balanced` and watch the point jump; open `Advanced solver constraints` and
confirm the raw min/max fields reflect the same numbers and that editing one flips the chip to
`Custom`; press `Save as default`, reload, and confirm the policy returns; run an optimization and
confirm the recommendation respects the gross/net ranges shown. Test acceptance: the new unit
tests fail before their implementation and pass after; `npm run gates` reports PASS for `check`,
`test`, and `test:websocket`; the Playwright spec passes.

## Idempotence and Recovery

Every step is additive and re-runnable. `npm run setup:worktree`, `npm test`, and the gates are
idempotent. New namespaces and the new actions namespace are independent; if a milestone is
reverted, earlier milestones remain green because each writes only canonical constraint keys the
solver already understands. Persistence is wallet-scoped and nil-safe (no write without a valid
address), so a missing/spectate wallet degrades to in-memory-only with no corruption.

## Artifacts and Notes

Mapping evidence and exact line anchors are captured under `Context and Orientation` and
`Surprises & Discoveries`. Keep short transcripts of the failing-then-passing unit tests and the
Playwright run here as milestones complete.

## Interfaces and Dependencies

New pure namespace `hyperopen.portfolio.optimizer.domain.exposure-policy` must expose at least:

    (constraints->policy [constraints]) ; → {:gross-target :gross-band :net-target :net-band}
    (policy->constraint-writes [policy]) ; → [[:gross-max v] [:gross-min v|nil] [:net-min v] [:net-max v]]
    (point->targets [{:keys [client-x client-y bounds buttons]}]) ; → {:gross-target :net-target} | nil
    (apply-point [constraints targets])  ; → constraints'
    (apply-band [constraints axis value]) ; axis ∈ #{:gross :net} → constraints'
    (apply-preset [constraints preset-key]) ; → constraints'
    (active-preset [constraints]) ; → preset-key | :custom
    (system-default-constraints []) ; → constraints

New actions namespace `hyperopen.portfolio.optimizer.actions.exposure` must expose pure handlers
`set-portfolio-optimizer-exposure-point`, `set-portfolio-optimizer-exposure-band`,
`apply-portfolio-optimizer-exposure-preset`, `reset-portfolio-optimizer-constraints-to-system`,
and (M5) `save-portfolio-optimizer-constraint-default`,
`receive-portfolio-optimizer-constraint-profiles`, each `[state & args] → effect-vector` using
`common/save-draft-path-values` (or a persist effect for the profile save).

Persistence boundary additions in
`hyperopen.portfolio.optimizer.infrastructure.persistence`: `constraint-profiles-key`,
`save-constraint-profiles!`, `load-constraint-profiles!` (IndexedDB, edn-v1 envelope,
`:saved-at-ms` + `:version`).

Dependencies: nexus dispatch runtime, Replicant, the existing optimizer constraint pipeline, and
`platform/indexed_db.cljs`. No new third-party libraries.

## Note on revisions

2026-06-30 (initial): Authored from a full subsystem mapping. Records the canonical-vs-view
constraint model, the honest nil-floor gross-band decision, the placeholder-based drag seam, the
wallet+universe profile persistence design, and the six-milestone sequence. Reason: maintainer
asked for a plan-then-implement of the 2D constraints redesign ("Everything now").
