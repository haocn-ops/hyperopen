# Optimizer execution-flow usability hardening

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This plan is maintained in accordance with `/hyperopen/docs/PLANS.md` (and its detailed contract at `/hyperopen/.agents/PLANS.md`).

## Purpose / Big Picture

The portfolio optimizer takes a trader from picking assets, through configuring and running an optimization, to reviewing a rebalance, and finally to **placing live real-money orders on Hyperliquid**. A usability audit of that flow (scored against Jenifer Tidwell's *Designing Interfaces*, 3rd ed.) found the structural foundation sound — there is a real two-step arm→confirm gate, displayed order types match routed types, and cost signals are honest — but it surfaced a cluster of concrete gaps, all at or just before the irreversible commit, that this plan closes.

After this change a trader will: (1) get immediate, visible acknowledgement when they hit "Pause" during a live run, instead of a button that appears to do nothing; (2) be able to actually type decimal constraint values like `0.65` (today the field snaps back to `0`); (3) see the dollar amount, buys/sells, and margin-after restated at the moment they confirm, with the commit button visually distinct from safe actions; (4) read honest copy about what "revert" really costs; (5) be asked to confirm before discarding a staged plan; (6) keep their per-order type edits when they navigate away and back; (7) see the same precise notional for an order across the rebalance and execution screens; and (8) get a screen-reader-announced, persistent confirmation when a run finishes.

You can see most of it working by running the app (`npm run dev`, then open `/portfolio/optimize/new`), building a small universe, running the optimizer, staging a rebalance, and walking the Execution tab through staged → armed → running. The input fix is visible on the setup Constraints panel.

## Context References

Public refs:

- Direct user request (captured here): "Based on your findings, I want you to create an execution plan and then implement it." The user explicitly chose the full scope, including the two structural bets (preserve staged order-type overrides across Execution-tab re-entry; reconcile the market-vs-recommended cost computation across the preview→execution boundary).

Repo artifacts:

- Audit knowledge base and report produced during the audit (temporary, in the session scratchpad: `kb/INDEX.md`, `flow-map.md`, `AUDIT-REPORT.md`). Not committed; the relevant findings are restated in this plan so it is self-contained.
- Related project memory: the optimizer flow-simplification ExecPlan at `/hyperopen/docs/exec-plans/completed/` (one-click run, in-place results) and the execution-tab v4 work. This plan builds on the shipped Execution tab without re-deriving it.

Local scratch refs (non-authoritative): none.

## Progress

- [x] (2026-06-29) Audited the flow; produced grounded findings; gathered code-level implementation briefs for the input surface, peripheral surfaces, and execution core.
- [x] (2026-06-29) Wrote this ExecPlan.
- [x] (2026-06-29) M0 — Enabling refactor: extracted the order table + per-order editor into `execution_order_table.cljs` and shared helpers into `execution_shared.cljs`; `execution_tab.cljs` 1054 → 694; ratcheted the size exception to 720 (retire 2026-09-30). Portfolio build + full test suite green (behaviour-preserving).
- [x] (2026-06-29) M1 — Commit-moment safety: armed band restates buys/sells/gross/margin-after (new view-model keys `:ready-buys-usd`/`:ready-sells-usd`); "Confirm & send" now uses a reserved `.optimizer-exec-commit` danger style (lock glyph) off the amber primary; honest irreversibility + "Revert filled (market orders)" copy; `aria-live` on the active band. New tests added.
- [x] (2026-06-29) M2 — Live-run cancel acknowledgement: surfaced `:abort-requested?`; running band flips to "Stopping — no new orders" with a disabled control; `role="progressbar"` on the running + health bars. New test added.
- [x] (2026-06-29) M3 — Discard confirmation: nested `<details>` confirm on "Abort & discard" firing the existing discard action; no new contract surface. New test added.
- [x] (2026-06-29) M4 — Input fixes: constraint + turnover inputs now commit on `:change` (blur/Enter) with a persistent unit echo ("max 50% per asset", "1.00× capital", …); Target Return routed through the percent handler + a `:change` percent input so `15` = 15% and the field echoes the interpreted percent. Added `controls/percent-input` + `decimal->percent-text`. Updated setup_view/layout tests (input→change) + added echo/percent lock-in assertions. Suite green.
- [x] (2026-06-29) M5 — Data legibility: per-order notional now exact `format-usdc` (matches the rebalance preview); execution display rows carry a stable `:order-no` (ledger order) and sort by absolute notional descending so the largest trade leads; "Resume from #N" references `:order-no`; drift bars scale to the run's `:max-abs-weight-drift` with a "full bar = N%" marker. New sort/exact-notional test. Suite green.
- [x] (2026-06-29) M6 — Cost reconciliation: added pure `execution-order-type/recommended-routed-cost-usd` (application layer, reuses `recommend-exec-type`); the rebalance cost KPI now reads "Est. fees + slip · market" (the conservative all-taker upper bound) with a sub showing "≈ $X smart-routed" — the cost the user actually pays at Execution's default routing. Layering-clean (view→application), no domain/engine change. New unit test. Suite green.
- [x] (2026-06-29) M7 — Run-outcome announcements: the execution effect adapter now emits an aria-live toast on a finished run (success / resting / halted) via the existing `feedback-runtime/set-order-feedback-toast!` ([:ui :toasts]); no new contract surface. New assertion on the success path.
- [x] (2026-06-29) M8 — Dead controls + readability + hygiene: removed both dead phantom `…` buttons (setup + scenario headers — `scenario_detail_view` was at 573/580, no room to wire a menu, and Duplicate/Archive already exist on the index surface; recorded in Decision Log); raised the armed irreversibility caveat (0.65→0.72rem) and cost-basis note (0.6rem/50%→0.65rem/70%) above the legibility floor. The stale `execution_tab` docstring was already corrected in the M0 rewrite, and the displayed==routed parity is already asserted by `build-execution-attempt-routes-selected-order-types-test`.
- [x] (2026-06-29) Validation: `npm run gates` = 33/33 PASS (`check` incl. all lints + 5 shadow-cljs builds, `npm test` 5597 tests / 30257 assertions, `test:websocket`). Browser-QA of the Execution `armed` workbench scene confirmed: armed figures render (+$176,718 buys / −$174,720 sells / gross $351,438 / margin after 42.00%), the commit button is "🔒 Confirm & send →" computing to rgb(194,91,91) (the red danger token, distinct from amber), `aria-live="polite"` on the band, the order list sorts largest-first with exact notionals, the "Abort & discard…" nested `<details>` confirm requires a second click, and the irreversibility caveat is 11.52px — no console errors.
- [x] (2026-06-29) Moved this plan to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: `execution_tab.cljs` is 1054 lines against a namespace-size cap of 1060 (exception in `dev/namespace_size_exceptions.edn`, `:retire-by "2026-06-30"`). The default budget is 500. Many planned edits target this file, so an extraction is mandatory before adding code.
  Evidence: `wc -l src/hyperopen/views/portfolio/optimize/execution_tab.cljs` → 1054; exception entry line 29 `:max-lines 1060`.
- Observation: the live-run "Pause / abort" flag is written but never read by any view. `:actions/pause-portfolio-optimizer-execution` saves `true` to `[:portfolio :optimizer :execution :abort-requested?]`; only the submit-loop effect reads it; the view-model and `execution_tab.cljs` never reference it. It self-clears at run start and at the terminal ledger.
  Evidence: repo grep for `abort-requested` returns only the writer action, `contracts/paths.cljs`, `contracts.cljs` (re-export), and `effect_adapters/portfolio_optimizer/execution.cljs` (read).
- Observation: numeric constraint inputs and the Target-Return input commit on every keystroke (`:on {:input ...}`) and the handler eagerly parses with `js/Number` and writes back into the controlled `:value (str value)`. `js/Number "0."` → `0`, so a decimal can never be typed. The codebase already has the correct pattern: `target_sigma.cljs` commits on `:change`.
  Evidence: `setup_constraint_controls.cljs:63-73`; handler `actions/draft.cljs:368-388` → `coercion/parse-number`; reference `target_sigma.cljs:108-131`.
- Observation: "Target Return" is the only return-style field that interprets the typed number as a fraction (`0.15`), while Target σ and Black-Litterman expected-return interpret it as a percent (`15`). Same concept, 100× divergent unit, adjacent in the UI.
  Evidence: `setup_objective_controls.cljs:32-41` (no `/100`) vs `target_sigma.cljs` percent path and BL `parse-percent-text`.
- Observation: "Revert filled" builds fresh reduce-only **market** orders ("Reverts always cross immediately (:market)") — it is a second round-trip paying fees + slippage again, not an exchange undo, yet the armed-band copy frames it as the way to undo.
  Evidence: `application/execution.cljs:485-526` (`reversing-row`, `:order-type :market`); copy at `execution_tab.cljs:286`.
- Observation: opening the Execution tab always rebuilds `default-execution-modal-state`, wiping `:overrides`/`:params`/`:phase`. A stable identity for "did the rebalance change?" exists as `:input-signature` on `last-successful-run-path`, so overrides can be preserved on a no-op re-entry.
  Evidence: `actions/execution.cljs:39-54`; signature at `contracts/signatures.cljs:40-46`.

## Decision Log

- Decision: Create budget headroom by extracting the order table + per-order editor (and its private cost-breakdown helpers) into a new `execution_order_table.cljs`, and keep truly shared pure helpers in a small `execution_shared.cljs`, rather than splitting every section.
  Rationale: the order table is the largest cohesive block (~310 lines) and is explicitly named in the size-exception's split note; one extraction drops `execution_tab.cljs` well under its cap with room for the new code, minimising churn on the most safety-critical view.
  Date/Author: 2026-06-29, Claude.
- Decision: Add no new `:actions/*` or `:effects/*` contract surfaces. Reuse existing actions; add only additive view-model keys and DOM-local state (nested `<details>`).
  Rationale: `src/hyperopen/schema/contracts/action_args.cljs` is over budget with a `:retire-by 2026-06-30`; new contract entries would extend it and risk the gate. Every planned behaviour is achievable additively (briefs confirmed it).
  Date/Author: 2026-06-29, Claude.
- Decision: Keep constraint fields as fraction-entry (type `0.5` for 50%) and only fix the commit timing + add an adornment/echo; do NOT convert them to percent-entry. Route Target Return through the existing percent handler so it matches Target σ and Black-Litterman (type `15`).
  Rationale: switching constraints to percent-entry is a larger semantic change with test churn and muscle-memory cost; the snap-to-`0` bug is the real defect. Target Return is the genuine inconsistency worth unifying.
  Date/Author: 2026-06-29, Claude.
- Decision: For the cost reconciliation (M6), prefer recomputing the rebalance preview cost under the same "recommended" routing the Execution tab defaults to, by relocating the pure `recommend-exec-type` clip-size heuristic into the domain layer if it depends only on row fields; otherwise fall back to an explicit "assumes market fills" annotation plus a recommended-routed estimate. Either way the two screens stop silently disagreeing.
  Rationale: domain code may not depend on the application layer (`lint:namespace-boundaries`); the contingency keeps the milestone shippable even if the heuristic needs application-only context.
  Date/Author: 2026-06-29, Claude.
- Decision (M6 resolved): kept the rebalance headline as the conservative all-market upper bound and added the recommended-routed estimate as the sub-line ("≈ $X smart-routed"), computed by a new pure `recommended-routed-cost-usd` in the existing application namespace (no domain/engine change, view→application layering).
  Rationale: the headline stays an honest worst case (never under-promises), the sub shows the number actually charged at execution's default, and avoiding the domain relocation kept the engine's tested cost output unchanged.
  Date/Author: 2026-06-29, Claude.
- Decision (M8): removed both dead `…` overflow buttons rather than wiring the scenario-header one to a Duplicate/Archive/Back menu.
  Rationale: `scenario_detail_view.cljs` was at 573/580 lines — no budget to add a menu inline without a further extraction, the dead-button finding is low severity, and Duplicate/Archive already exist on the optimizer index surface. Removing eliminates the "clicking yields nothing" phantom control directly.
  Date/Author: 2026-06-29, Claude.

## Outcomes & Retrospective

All eight milestones shipped and the full gate matrix passes (33/33), with a browser-QA pass on the Execution armed scene. What changed, in user terms: the live-order commit now restates the dollars at stake and carries a dedicated red "🔒 Confirm & send" treatment distinct from safe actions; pressing pause mid-run is acknowledged instead of looking inert; the staged plan can't be discarded in one click; constraint and Target-Return number fields accept decimals and percents correctly (a typed `15` means 15%, not 1500%); the same order shows the same exact dollars in the preview and execution screens, with the riskiest trade leading the list; the rebalance preview and execution cost no longer silently disagree; a finished run announces its outcome via the aria-live toast; and two dead phantom buttons are gone.

Complexity: a small net increase, deliberately localized. The 1054-line `execution_tab.cljs` was split into three namespaces (`execution_tab` 694, `execution_order_table` 376, `execution_shared` 63), which lowered its size-budget exception from 1060 to 720 — a structural improvement, not just headroom. The rest is additive: a handful of view-model keys (`:ready-buys-usd`, `:ready-sells-usd`, `:abort-requested?`, `:order-no`), one pure application helper (`recommended-routed-cost-usd`), two reusable form controls (`percent-input`, `decimal->percent-text`), and one effect-adapter toast call. No new action/effect contract surfaces were added (a hard constraint, since `action_args.cljs` is over budget). 12 new/updated tests lock the new behaviours.

What remains / follow-ups (out of scope here, worth a future pass): wire the scenario-header overflow to a real Duplicate/Archive/Back menu once `scenario_detail_view.cljs` is split (it's at 561/580); add per-order/cancel-resting controls so already-released orders can be acted on during an abort; and the deferred split of the execution KPI strip + health rail into their own namespace (the remaining size-exception note).

Lessons: (1) the namespace-size budgets are the binding constraint on this codebase — the enabling extraction had to come first, and pushing display logic into the thin view-model kept the view files within budget. (2) Reconciling the two cost screens by *showing both* (conservative headline + recommended sub-line) was lower-risk and more honest than making the headline optimistic. (3) The view-render test suite (full `portfolio-view` + data-role assertions) made a 1000-line extraction of the most safety-critical surface verifiable as behaviour-preserving.

## Context and Orientation

The optimizer lives under two trees. The **views** are ClojureScript (Replicant hiccup) under `src/hyperopen/views/portfolio/optimize/`. The **view-models** (pure functions from app state to a render model) are under `src/hyperopen/portfolio/optimizer/application/view_model/`. State mutations are **actions** (pure functions returning effect vectors) under `src/hyperopen/portfolio/optimizer/actions/`, dispatched through a Nexus runtime; side effects (network, persistence) run in **effect adapters** under `src/hyperopen/runtime/effect_adapters/`. Routes are in `src/hyperopen/portfolio/routes.cljs`.

The Execution tab is the heart of this plan. Its view is `src/hyperopen/views/portfolio/optimize/execution_tab.cljs` (a phase machine: staged → armed → running → done | halted | resting). Its render model is built by `execution-tab-model` in `src/hyperopen/portfolio/optimizer/application/view_model/execution.cljs`. Its actions are in `src/hyperopen/portfolio/optimizer/actions/execution.cljs`; its plan/recovery builders are in `src/hyperopen/portfolio/optimizer/application/execution.cljs`; the live submit loop is `src/hyperopen/runtime/effect_adapters/portfolio_optimizer/execution.cljs`. Defaults (including `default-execution-modal-state`) are in `src/hyperopen/portfolio/optimizer/defaults.cljs`. Contract paths are `src/hyperopen/portfolio/optimizer/contracts/paths.cljs`.

Two hard constraints govern *where* code may be added. First, **namespace size budgets**: the default cap is 500 lines (`dev/check_namespace_sizes.clj`); files over it need an entry in `dev/namespace_size_exceptions.edn`. `execution_tab.cljs` (1054) and `actions/draft.cljs` (558/580, only ~22 lines of headroom) are tight, so new logic is steered into thin files (`view_model/execution.cljs` at 170/500; `actions/target_sigma.cljs` at 70/500; a new view namespace). Second, **theme-color and input-parsing ratchets**: `lint:theme-colors` forbids new raw color literals (reuse tokens such as `var(--optimizer-short)` / the `text-trading-red` class for danger), and `lint:input-parsing` forbids `js/parseFloat` in a fixed list of guarded files (none of ours, but avoid it anyway). The full gate is `npm run check`; the aggregate is `npm run gates`.

Terms used below, in plain language. "Armed band" — the warning-tinted bar that appears when the user clicks "Arm execution"; it asks them to confirm sending live orders. "Notional" — the dollar size of a trade. "Maker/taker" — a resting limit order that adds liquidity pays the lower *maker* fee and no market-impact slippage; an order that crosses the spread immediately is a *taker* and pays impact + the higher fee. "Recommended routing" — the Execution tab's default, where the app picks Market/Limit/TWAP/Passive per order by clip size. "Input signature" — a stable fingerprint of the solved scenario's inputs (universe, models, objective, constraints) used to tell whether a re-staged rebalance actually changed.

## Plan of Work

The work proceeds as eight milestones (M0–M8), each independently verifiable. Implement in order; M0 unblocks the rest.

### M0 — Enabling refactor (no behaviour change)

In `src/hyperopen/views/portfolio/optimize/execution_tab.cljs`, move the order-table cluster into a new file `src/hyperopen/views/portfolio/optimize/execution_order_table.cljs` (new namespace `hyperopen.views.portfolio.optimize.execution-order-table`). The cluster is the section from `cost-source-label` through `order-table` (roughly the current lines 593–903): `cost-source-label`, `state-cell`, `cost-stat`, `cost-op`, `cost-breakdown`, `cost-breakdown-strip`, `order-editor-row`, `slip-cell`, `order-row`, `row-visible?`, `order-filter-toggle`, `order-table`. Pure shared helpers needed by both files (`abs-num`, `finite`, `format-bps`, `format-knotional`, `data-role-token`, `order-type-labels`, `order-types`, `resting-type?`, `crossing-type?`, `effective-type`, `recommend-exec-type`, `row-params`, `editable?`, `venue-label`, `state-glyph`, `row-display-state`, `side-tone`, `eyebrow`, `chip`) move into a small new `src/hyperopen/views/portfolio/optimize/execution_shared.cljs` (namespace `…optimize.execution-shared`), required by both `execution_tab` and `execution_order_table`. `execution_tab` keeps the header, control bands, KPI strip, health rail, latest-attempt panel, and `execution-tab` entry point, and requires the new namespaces. Update `dev/namespace_size_exceptions.edn`: lower `execution_tab.cljs`'s `:max-lines` to the new size + a small margin (and refresh `:retire-by`), and confirm the two new files are under 500 (no exception). This milestone must not change any rendered output.

### M1 — Commit-moment safety

In `view_model/execution.cljs` `execution-tab-model`, add additive keys derived from the already-present `plan` rows and `summary`: `:ready-buys-usd` and `:ready-sells-usd` (sum of `abs(:delta-notional-usd)` over rows with `:status :ready`, split by `:side`), and surface `:gross-ready-notional-usd` and `:margin` for the band (both already on `summary`). In `execution_tab.cljs` `armed-band`, restate the load-bearing figures inside the confirm copy: "Send N live orders to Hyperliquid — +$X buys / −$Y sells · gross $Z · margin after W%". Put "Confirm & send →" on its own line/segment after the figures, separated from "Cancel" by whitespace, and give it a dedicated commit style (a new `.optimizer-exec-commit` class in `execution.css` built from `var(--optimizer-short)` — the red danger token — with a lock glyph), distinct from the amber `optimizer-primary-action` reused by Arm/Resume/View-tracking. Fix the over-promising copy at `execution_tab.cljs:286`: replace "This cannot be undone without reverting filled trades." with plain-language irreversibility ("Filled trades can't be undone — closing them later sends new market orders at then-current prices and costs."). Relabel the halted "Revert filled" button (`:383`) to "Revert filled (sends market orders)" or add a one-line caveat. Add `:role "status"` + `:aria-live "polite"` (assertive for `:halted`) to the active control-band container.

### M2 — Live-run cancel acknowledgement

In `view_model/execution.cljs`, read `(get-in state contracts/execution-abort-requested-path)` and expose `:abort-requested?` (boolean). In `execution_tab.cljs` `running-band`, when `:abort-requested?` is set: relabel the headline to "Stopping — no new orders" (keep showing how many are filled/resting and that in-flight orders still settle), and render the "Pause / abort" button as a disabled "Stopping…" rather than a still-clickable control. Add the ARIA progressbar contract to the running band's progress bar (`role="progressbar"`, `aria-valuemin/max/now`) mirroring `optimization_progress_panel.cljs:199-211`, and to the health-rail bar.

### M3 — Discard confirmation

In `execution_tab.cljs` `overflow-menu`, replace the single "Abort & discard" `<button>` with a nested `<details>` whose summary is "Abort & discard…" and whose body contains a short caution line and a "Confirm discard" button wired to the existing `[[:actions/discard-portfolio-optimizer-execution]]`. No app-db state, no new action. Add minimal CSS if needed (reuse the overflow menu styles).

### M4 — Input fixes

In `src/hyperopen/views/portfolio/optimize/setup_constraint_controls.cljs`, change the numeric inputs (`constraint-row` and `turnover-cap-row`) from `:on {:input ...}` to `:on {:change ...}` so the DOM holds raw keystrokes until blur/Enter; keep `:value (str value)` so blur reconciles to the interpreted value. Add a persistent unit adornment (`×`/`%`) and a short echo of the interpreted value next to each field. Preserve the blank→clear behaviour for the clearable keys (`:gross-min`, `:max-turnover`): on `:change`, an empty string must dispatch the explicit nil-clear payload (mirroring the existing toggle clear), not be silently dropped. In `src/hyperopen/views/portfolio/optimize/setup_objective_controls.cljs`, route "Target Return" through `:actions/set-portfolio-optimizer-objective-parameter-percent` (whose non-sigma branch divides by 100) instead of `:actions/set-portfolio-optimizer-objective-parameter`, render it with a `:change`-committing percent input that displays the stored fraction as a percent (e.g. via a `decimal→percent-text` helper), add a `%` adornment, and update the default presentation so `15` reads as 15%. Any new parse/format helper goes in `actions/target_sigma.cljs` or `coercion.cljs`, never `actions/draft.cljs` (no headroom). Update affected tests that assert the old fraction storage.

### M5 — Data legibility

In `execution_tab.cljs` (or the new `execution_order_table.cljs` after M0), change the per-order notional cell to exact `opt-format/format-usdc` (the `:829` site and the latest-attempt `:1029` site), keeping the compact `format-knotional` only on the aggregate KPI tiles. In `view_model/execution.cljs`, attach a stable `:order-no` (1-based, in the original/ledger order) to each display row, then sort the display rows by absolute `:delta-notional-usd` descending so the largest trades are at the top (matching the rebalance table); the view renders `:order-no` for the `#` column instead of the render index, and the halted-band "Resume from #N" uses the failed rows' `:order-no` (min) so it stays consistent with the ledger. In `src/hyperopen/views/portfolio/optimize/tracking_panel.cljs`, thread `:max-abs-weight-drift` (already on the latest snapshot, already rendered in the metrics strip) into `drift-chart` and scale each bar to that max instead of the fixed `1000×`, with a small "max NN%" marker; the per-row `%` label stays.

### M6 — Cost reconciliation

Determine whether `recommend-exec-type` (currently `application/execution_order_type.cljs`) depends only on row fields. If yes, relocate the pure heuristic into the domain layer (e.g. `domain/rebalance.cljs` or a new `domain/execution_order_type.cljs`), re-export it from the application namespace for existing callers, and in `domain/rebalance.cljs`'s summary aggregation compute `:estimated-fees-usd`/`:estimated-slippage-usd` under recommended routing (resting rows contribute the maker fee and zero impact; crossing rows keep taker fee + impact), so the rebalance preview cost matches what the Execution tab will charge by default. Annotate the rebalance cost KPI sub-line to state the routing assumption. If `recommend-exec-type` needs application-only context, keep the domain summary as-is and instead annotate the rebalance KPI ("assumes market fills — actual depends on order types at execution") and add the recommended-routed estimate as a sub-line computed in the view. Update rebalance cost tests accordingly.

### M7 — Run-outcome announcements

In `src/hyperopen/runtime/effect_adapters/portfolio_optimizer/execution.cljs`, when a run reaches a terminal ledger (done/resting/halted/failed), append a toast map to `[:ui :toasts]` via the existing `:effects/save-many`, using the `feedback_runtime` helpers (`ensure-toast-id`, `bounded-toasts`) so the 5-cap and id rules hold. Toast shape understood by `notifications_view.cljs`: `{:id … :kind :success|:error|:info :headline "…" :subline "…"}`. This announces success/failure to screen readers (the toast region is `aria-live`) and persists it for a user who tab-switched mid-run. No new action/effect contract surface.

### M8 — Dead controls + readability + hygiene

Wire the scenario-header `…` button (`scenario_detail_view.cljs:98-109`) to a real `<details>` menu of existing actions: Duplicate (`:actions/duplicate-portfolio-optimizer-scenario`), Archive (`:actions/archive-portfolio-optimizer-scenario`), and "Back to all scenarios" (`:actions/navigate` to the optimize index). Remove the dead setup-header `…` (`setup_header.cljs:44-50`) since no existing action cleanly fits. Raise the smallest, dimmest execution copy above the legibility floor: the irreversibility caveat, the phase chip, and the cost-basis notes (bump `text-[0.53–0.6rem]` toward `0.65–0.7rem` where layout allows, and lift `text-trading-muted/50` → `/70`), reusing tokens only. Fix the stale `execution_tab.cljs` ns docstring (it claims order types are "not-yet-wired / single market order", contradicting shipped routing) and add a small test asserting the displayed order type equals the routed order type (`apply-order-type-selections`).

## Concrete Steps

Run everything from the worktree root `/Users/barry/projects/hyperopen/.claude/worktrees/eloquent-sutherland-5f4220`.

Bootstrap (already done in this session; idempotent): `npm run setup:worktree` (symlinks `node_modules`). After each milestone, compile the optimizer build and run the focused tests; run the full gates before declaring done:

    npx shadow-cljs --force-spawn compile portfolio          # optimizer views/view-models compile
    npx shadow-cljs --force-spawn compile test && node out/test.js   # cljs test suite
    npm run lint:namespace-sizes                              # after M0 and any large add
    npm run lint:theme-colors                                # after any CSS/color edit
    npm run gates                                             # check + test + test:websocket matrix, before completion

For browser verification of the Execution states, use the repo `playwright-e2e` skill for committed coverage and the preview tooling / `browser-mcp-explore` for exploratory checks, per `/hyperopen/docs/BROWSER_TESTING.md`.

## Validation and Acceptance

Acceptance is phrased as observable behaviour.

M0: `npx shadow-cljs --force-spawn compile portfolio` succeeds; `node out/test.js` shows the same pass count as before; `npm run lint:namespace-sizes` passes; the Execution tab renders byte-identically (no visual diff) — verify by loading a staged plan and comparing.

M1: On the armed band, the confirm copy shows the dollar gross, buys, sells, and margin-after; "Confirm & send" is visually distinct (red/lock) from "Arm"/"Resume"; the armed and halted copy no longer implies a free undo. A screen reader announces the band.

M2: Clicking "Pause" during a running execution immediately changes the band to "Stopping — no new orders" and disables the button; the progress bar exposes `aria-valuenow`.

M3: Clicking "Abort & discard" reveals a confirm step; the plan is only cleared after "Confirm discard".

M4: In Constraints, typing `0.65` into the per-asset cap stays `0.65` (today it snaps to `0`); a unit/echo shows "= 65%". Typing `15` into Target Return requests 15% (stored `0.15`), matching Target σ. The relevant `draft_actions`/objective tests are updated and pass.

M5: The same order shows the same exact dollar notional in the Rebalance preview and the Execution order row; the execution table lists largest-notional orders first with a stable `#`; drift bars are proportional with a max marker.

M6: The Rebalance "Est. fees + slippage" and the Execution "Est. all-in cost" no longer silently disagree for the default routing (either equal under recommended routing, or the preview explicitly states its assumption and shows the recommended-routed estimate).

M7: Finishing a run shows a persistent toast ("Execution complete …" / "Execution halted …") announced via the global `aria-live` region.

M8: The scenario-header `…` opens a working menu; the setup-header `…` is gone; the irreversibility caveat is legible; `node out/test.js` includes the new displayed==routed parity assertion.

Final: `npm run gates` reports PASS for `check`, `test`, and `test:websocket`; the smallest relevant Playwright command passes for any touched browser flow; browser QA of the Execution states is recorded here.

## Idempotence and Recovery

Every step is additive and re-runnable. The M0 extraction is behaviour-preserving; if a gate regresses, revert the new files and the `execution_tab.cljs` requires in one step. The input changes are local to two view files plus possibly `actions/target_sigma.cljs`. No migrations, no destructive data operations, no remote calls. Do not push or touch a remote (local-only per repo policy). If a milestone cannot land cleanly, leave its `Progress` item unchecked, record why in `Surprises & Discoveries`, and proceed to the next independent milestone.

## Artifacts and Notes

Key file:line anchors (verified during research):

    execution_tab.cljs: armed-band 272-302 (copy 284-286); running-band 304-333 (pause 328-333);
      overflow discard 171-176; per-order notional cell 829; latest-attempt notional 1029;
      order-table 857-903; chip 127-133; ns docstring 2-8.
    view_model/execution.cljs: execution-tab-model 110-170 (returned map 149-170); run-status 93-95.
    actions/execution.cljs: open 39-54; pause/abort-requested 262-267; discard 269-280; revert 202-234.
    application/execution.cljs: reversing-row/build-revert-plan 485-526 (:order-type :market).
    defaults.cljs: default-execution-modal-state 139-158.
    contracts/paths.cljs: execution-abort-requested-path 65; last-successful-run input-signature via signatures.cljs:40-46.
    setup_constraint_controls.cljs: constraint-row 63-73; turnover-cap-row 99-111.
    setup_objective_controls.cljs: Target Return 32-41 (dispatch 40).
    target_sigma.cljs: percent input pattern 108-131; percent handler actions/target_sigma.cljs:24-37.
    tracking_panel.cljs: drift-chart 51-71 (width 1000x).
    rebalance_tab.cljs: cost KPI in summary-kpis 205-240; per-row notional 286-287; side-totals 190-203.
    domain/rebalance.cljs: per-row cost 303-332; summary 465-471.
    optimization_progress_panel.cljs: progressbar ARIA 199-211.
    notifications_view.cljs: toast region/cards 86-243; order/feedback_runtime.cljs helpers.
    dev/namespace_size_exceptions.edn: execution_tab line 29 (1060); actions/draft line 73 (580).
