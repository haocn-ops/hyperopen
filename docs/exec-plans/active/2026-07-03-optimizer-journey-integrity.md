# Optimizer journey integrity: unwedge the draft workspace, reconcile the execution numbers, make seeding reliable, and stop the diagnostics from lying

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document must be maintained in accordance with `docs/PLANS.md` (and its detailed contract `/.agents/PLANS.md`).

## Purpose / Big Picture

A full-journey UX audit of the portfolio optimizer (2026-07-03, live walkthrough in a worktree build against a real 18-position account, every finding then verified in code) found that the optimizer's strongest moments — the fast honest run, refinement, the execution safety rails — are undermined by four defects that break trust exactly where it matters most:

First, the workspace loses the user's work. After saving a scenario, the draft results route (`/portfolio/optimize/draft`) wedges permanently: every later run completes, shows an "Optimization complete — the recommendation is ready" toast, auto-navigates to that route — and renders an eternal "Scenario draft is loading…" shell with all-N/A metrics, a false "read-only" tag, and a Rerun button that silently does nothing. The user's successful result is unreachable.

Second, the commit point — the one screen where real money moves — shows three trade counts that disagree: the verdict says "6 trades", the Buys/Sells dollar KPIs include legs that will never be sent (~$45k inflated in the audited run), and the order ledger shows 19 rows of which 13 say a bare "skipped" with no reason, including five-figure trades.

Third, the promised "holdings load automatically" moment fails silently in three common paths (a pre-connect visit blocks auto-seed forever; a page reload loses the draft under spectate; "Load my holdings" on an account with no open perp positions is a complete no-op with zero feedback).

Fourth, the trust rail doesn't evaluate: the Diversification row is hardcoded to "OK" (it showed "Effective N · 0.5 of 18 · OK" on a portfolio that is effectively half of one position), the readiness panel shows two identically-worded "stale cached history" groups with different counts, and the result-warnings drawer leaks raw engineering codes and data-vendor internals ("CoinGecko Demo provider history window is capped by provider tier").

After this plan, a user can: save a scenario and keep working in the draft workspace without ever hitting a dead page; read one coherent trade count at the commit point with every skipped leg explained; open the optimizer and either see their holdings loaded or be told exactly why not; and trust that a status marked "OK" was actually evaluated. Each of these is observable in the running app and in tests that fail before the change and pass after.

## Context References

Public refs:

- Direct maintainer request (this session): a full-journey design and UX audit of `/portfolio/optimize` "from the way they start using the optimizer all the way through position execution and management", followed by "based on your findings above, create an execution plan". The audit findings and their file:line evidence are embedded in this plan (Surprises & Discoveries) so the plan is self-contained.

Repo artifacts:

- `docs/exec-plans/completed/2026-06-28-optimizer-flow-simplification.md` — the prior *About Face* audit plan. Its Milestone 4 deliberately deferred the "saved-scenario in-place load / isolation re-keying" spike; this plan's Milestone 1 fixes a defect at exactly that seam without doing the full spike. Its Decision Log also chose universe-only auto-preseed, which Milestone 5 here reconciles with the manual button's behavior.
- `docs/agent-guides/trading-ui-policy.md`, `docs/agent-guides/ui-foundations.md`, `docs/PRODUCT_SENSE.md` — the honesty and trading-safety rules this plan enforces (truthful status, no silent no-ops, risk context near submit) and must not weaken (two-step arming, backend-confirmed lifecycle).
- `docs/BROWSER_STORAGE.md` — governs any change to the persisted-draft restore path (Milestone 3).

Local scratch refs (non-authoritative): none.

## Progress

- [x] (2026-07-03) Full-journey audit completed: live walkthrough (entry board → setup → run → results → refinement → execution staged/read-only → save → index → stale/reload flows) in a worktree static build while spectating leaderboard account `0xf5d81a135f756ca16544e53c20fc20643ec3ad53` (18 perp positions), plus code verification of every major finding. Findings embedded below.
- [x] (2026-07-03) Milestone 1 — wedge characterized: save stamps the draft config `:id scn_*, :status :saved` (`scenario_records.cljs` line ~21) and the run request carries `:scenario-id (:id draft*)` (`request_builder.cljs` line ~502), so post-save workspace runs are scenario-bound while `success-commands` revealed the hardcoded `"draft"` alias — a page that only renders *unsaved* runs. The live "Rerun no-op" was the same bug: the rerun ran fine, revealed into the masked page, and only the toast fired.
- [x] (2026-07-03) Milestone 1 — implemented and validated (`npm run gates` 34/34, 5709 tests): `success-commands` now reveals the run's own scenario surface (run-scenario-id, defaulting to "draft" for unsaved runs) from both `/optimize/new` and the `/draft` alias; the `/draft` alias masks to an honest *idle* shell (new `route-loading?`; masked active-scenario `{:status :idle :read-only? false}` for the alias) so the "is loading" copy and the false read-only tag are unreachable for it; Rerun is disabled-with-reason (`:rerun-blocked-reason` computed from the REAL draft's universe, since actions never see the masked state). Tests: 2 new workflow reveal tests, post-save idle-not-loading view test, empty-draft rerun-gate test (all RED before the fix — the idle test reproduced the exact wedge shell).
- [x] (2026-07-03) Milestone 2 — implemented and validated (`npm run gates` 34/34, 5712 tests): `side-totals` excludes `:skipped` as well as `:blocked` rows; every skipped ledger row states its reason in plain language ("skipped · within 3 pp band — no trade needed" — the band travels from `domain/rebalance.cljs` `row-status` through the plan row as `:tolerance`); the verdict trade count is SENDABLE legs only (`recommendation-deltas` counts `:ready-count`); and a new `:at-target?` fact ("Already at target"/"No trades needed" requires ready=0 AND blocked=0) prevents the all-blocked plan from reading as done — it now says "No trades can be sent — N legs are blocked." Tests: skipped-rows-excluded KPI test, skipped-reason ledger test, all-blocked-not-at-target verdict test. Two namespace-size entries adjusted (`rebalance.cljs` new at 520, `scenario_detail_view_test` 996→1020).
- [x] (2026-07-03) Milestone 3 — implemented and validated (`npm run gates` 34/34, 5715 tests). (a) New `install-identity-restore-watcher!` in `infrastructure/draft_autosave.cljs` re-dispatches the restore-or-preseed funnel when the effective account identity resolves or changes while on `/optimize/new` with an untouched draft — the restore effect silently no-ops on a nil address (reload under spectate resolves identity AFTER route load) and the holdings watcher only fires on holdings-arrival transitions, so this was the missing third leg. (b) The universe empty state now distinguishes "snapshot arrived, nothing importable" (`:no-importable-holdings?` on `universe-section-model`, mirroring the seed's perp-always/spot-if-enabled eligibility) and says "No open positions to import for this account" instead of repeating the auto-load promise — making "Load my holdings" never a silent no-op. Scope note: the plan's "persisted-but-untouched empty draft blocks preseed forever" hypothesis was DISPROVEN in code — autosave skips untouched drafts entirely, so such records cannot exist; the real culprit was the identity race (see Decision Log). Tests: identity-arrival restore + gate tests, holdings-empty note render/suppress tests.
- [x] (2026-07-03) Milestone 4 — implemented and validated (`npm run gates` 34/34, 5718 tests). `diversification-status` replaces the hardcoded `:ok` (effective N < 1.5 → "bad", < 25% of universe → "caution", non-finite → "unknown", never falsely green — thresholds recorded in Decision Log); result warnings render one row per CODE with plain-language headline + affected assets, engine messages deduplicated into muted detail lines (25 near-identical rows → a handful), raw namespaced ids stripped via `display-asset-label` (perp:HYPE → HYPE, also fixing the weight-stability subtext leak); `warning-code-summary` gives `:source-fetch-failed` its own sentence so two identically-worded staleness groups can no longer appear side by side. Tests: threshold unit tests, grouped-warnings render test, per-code readiness copy test.
- [ ] Milestone 5 — preset/constraint state honesty implemented and validated.
- [ ] Whole-plan close-out: `npm run gates` PASS and the optimizer Playwright smoke green; plan moved to `completed/`.

## Surprises & Discoveries

Findings established during the audit (each is the evidence base for a milestone; re-verify line numbers before editing — the optimizer moves fast):

- Observation: the draft results route wedges permanently after a save. Repro: run on `/portfolio/optimize/new` → results auto-reveal on `/portfolio/optimize/draft` (works) → "Save scenario" (navigates to `/portfolio/optimize/scn_*`) → edit the draft → any later visit to `/portfolio/optimize/draft` renders the "Scenario draft is loading… Retained data from a previous scenario is hidden" shell with default constraints, a "read-only" tag, and all-N/A KPIs. New successful runs re-navigate to the same masked page.
  Evidence: `route-mismatched?` and `scenario-scoped-state` in `src/hyperopen/portfolio/optimizer/application/view_model/scenario.cljs` (lines ~32–69). After save, `active-scenario-loaded-id` is the saved `scn_*` id, so route-id `"draft"` mismatches; `scenario-scoped-state` then substitutes a default draft, a nil last-run, and `{:status :loading :read-only? true}` — and nothing ever re-keys `loaded-id` when a new draft run starts, so the mask never lifts. The "loading" copy is a lie: nothing is loading and nothing ever will (there is no loader for the id `draft`).

- Observation: Rerun on the masked page silently no-ops. The button is enabled, clicking produces no run, no error, no state change.
  Evidence: the masked scoped state contains the *default* (empty-universe) draft, and run-triggerability requires a non-empty universe (`run-triggerable?` in `application/view_model/workspace.cljs`), so the dispatch refuses downstream while the button stays enabled — a truthful-label violation per `docs/agent-guides/ui-foundations.md`.

- Observation: the execution Buys/Sells KPIs include skipped legs. Live: Buys "+$632,389.82" while only ~$587k was actually staged.
  Evidence: `side-totals` in `src/hyperopen/views/portfolio/optimize/execution_tab.cljs` (lines ~33–46) filters only `(= :blocked (:status %))` rows; its own docstring says the headline should "match the ready-only staged notional and … never [be] inflated by blocked rows", so skipped rows violate the stated intent.

- Observation: 13 of 19 ledger rows rendered a bare "skipped" with an em-dash cost, including a $22.4k ZEC buy and a $21.3k XRP sell, with no reason shown. The reason exists in the data and is benign: the staging pipeline assigns `:status :skipped, :reason :within-tolerance` when a leg's weight delta is inside the rebalance tolerance band (`src/hyperopen/portfolio/optimizer/application/execution.cljs` lines ~80–83; other skip reasons exist too, e.g. `:already-filled`, `:already-resting` around lines ~477–480). The order table simply never renders `:reason` (`src/hyperopen/views/portfolio/optimize/execution_order_table.cljs` lines ~44, ~85).

- Observation: three "how many trades" numbers coexist at the commit point: verdict "6 trades get you there", KPI "9 assets" buys / "9 assets" sells, ledger rows numbered up to #19 (the `#` column is the plan index, meaningless in the notional-sorted display).

- Observation: the holdings auto-seed never fires once any draft has been persisted, because persisted-draft restore beats the preseed and the preseed guard requires an untouched draft. A user who merely *looked* at the optimizer before connecting has an empty persisted draft forever after. Also, a full page reload while spectating loses the draft entirely (universe back to 0 despite the header having shown "Saved 2:56 PM"), and "Load my holdings" on an account with zero open perp positions (audited: an account with $314k perps *collateral* but no positions, plus 19 spot-dust balances) is a silent no-op — the seed action already returns no-op on empty exposures, but no feedback reaches the user.
  Evidence: live repros as described; seed/no-op behavior in `set-portfolio-optimizer-universe-from-current` (`src/hyperopen/portfolio/optimizer/actions/universe.cljs`); auto-preseed hook in `src/hyperopen/portfolio/navigation.cljs` `route-loader-effects` (guarded on `:optimize-new` + untouched draft).

- Observation: the Diversification trust row is hardcoded `:status :ok` regardless of value — live it showed "Effective N · 0.5 of 18" (a one-position portfolio) with a green OK.
  Evidence: `src/hyperopen/views/portfolio/optimize/results_diagnostics_rail.cljs` lines ~277–281: `(trust-row {:label "Diversification" :status :ok …})`.

- Observation: the setup Readiness panel showed two groups both titled "N assets use stale cached history" (15 and 6, overlapping asset lists) because every code in `stale-history-warning-codes` maps to the same sentence in `warning-code-summary` (`src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` lines ~228–242). The result-warnings drawer ("Warnings · 25") renders raw code names ("stale-history", "source-fetch-failed") and vendor internals ("CoinGecko Demo provider history window is capped by provider tier"), and trust-rail copy leaks raw instrument ids ("perp:HYPE is most sensitive"), violating the instrument-identity formatting rule in `docs/FRONTEND.md`.

- Observation: clicking "Load my holdings" replaces the Conservative preset's constraint envelope with a holdings-derived one (live: cap 152%, net floor −1.41×; the Constraints section flips to "Custom") while the preset row still shows "Conservative · ACTIVE". The prior plan's Decision Log chose universe-only seeding for the *automatic* preseed precisely to avoid this surprise, but the manual button intentionally seeds constraints — the two surfaces now tell contradictory stories.

(Add new observations here as implementation proceeds, with short evidence snippets — test output is ideal.)

## Decision Log

- Decision: scope this plan to five milestones — (1) draft workspace lifecycle integrity, (2) execution commit-point reconciliation, (3) seeding/restore reliability and feedback, (4) honest diagnostics and warnings, (5) preset/constraint state honesty — ordered by user-harm severity, and explicitly defer the remaining audit findings (vocabulary unification across surfaces, entry-board rework with timestamps and value proposition, spectate fill-toast aggregation on optimizer routes, signed-range formatting, frontier label collision, limits-hit chip affordance, save-modal polish, duplicate progress surfaces) to follow-up GitHub Issues so this plan stays executable and reviewable.
  Rationale: milestones 1–2 are trust-critical defects (lost work; misleading numbers at the money-moving screen); 3–5 are the audit's highest-value honesty fixes. The deferred items are lower-severity polish or need product decisions (naming, board design) that should not block defect fixes.
  Date/Author: 2026-07-03 / Geronimo (via agent).

- Decision: Milestone 1 fixes the wedge at the state-transition seams (save completion and draft-run start), NOT by re-keying the load-isolation guard itself. The full "saved-scenario in-place load / isolation re-keying" redesign stays deferred as its own spike, exactly as the prior plan decided.
  Rationale: the guard's defensive masking is correct for genuinely-mismatched saved ids; the defect is that the surrounding lifecycle never restores the "retained unsaved run" facts after a save. Fixing the facts is small and reversible; re-keying the guard changes a load-isolation contract and belongs in the spike.
  Date/Author: 2026-07-03 / Geronimo (via agent).

- Decision: Milestone 2 keeps skipped legs visible in the order ledger (with reasons) rather than hiding them.
  Rationale: honest disclosure — the user should see that a leg was considered and why it will not trade ("within tolerance band" is meaningful information about how close they already are); hiding rows would make the plan look smaller than the rebalance delta implies. Only the dollar headline KPIs must stop counting them.
  Date/Author: 2026-07-03 / Geronimo (via agent).

- Decision (Milestone 1): fix the reveal target rather than the draft's saved identity. The alternative — stripping `:id`/`:status` from the draft config when an edit dirties a just-saved scenario — would silently change Save semantics from "update the saved scenario" to "always save-as-new", a product behavior change out of scope here. Revealing the run's own scenario surface preserves save-update semantics and matches the existing "a rerun watched from the results page live-updates in place" behavior.
  Rationale: smallest change that makes every successful run land on a page that can render it; the saved-`{id}` isolation contract is untouched.
  Date/Author: 2026-07-03 / Geronimo (via agent).

- Decision (Milestone 3): do NOT widen `untouched-draft?` for the preseed guard. The autosave watcher already refuses to persist untouched/default drafts, so a "persisted empty untouched draft" cannot exist to poison the guard; a persisted draft with an empty universe is always a deliberate user clear (dirty metadata) that must keep beating the preseed. The audit's observed never-seeds behavior is explained by the identity race the new watcher fixes.
  Rationale: evidence over hypothesis — the flush gate (`(not (untouched-draft? draft))`) in `install-draft-autosave-watcher!` makes the plan's original (a) scenario unreachable.
  Date/Author: 2026-07-03 / Geronimo (via agent).

- Decision (Milestone 3): the "nothing to import" feedback is DERIVED in the view-model (snapshot arrived + no perp exposures + spot off), not recorded by the seed action. Writing a status from the no-op seed would touch the draft (or add a contract path) and would go stale; the derived fact is always current, renders before the user even clicks, and keeps the seed action a pure no-op. Known approximation: an account whose only importable exposures are all dropped for unusable history still falls to the generic copy.
  Rationale: honesty with zero new state surface.
  Date/Author: 2026-07-03 / Geronimo (via agent).

- Decision (Milestone 4): diversification thresholds are effective-N < 1.5 → bad (effectively one position) and effective-N < 25% of universe size → caution; unknown/non-finite renders a muted "unknown", never green. Vendor internals (provider-tier detail) stay available in the demoted per-code detail lines rather than being deleted — the honesty fix is hierarchy (headline = what/which assets, detail = raw engine message), not information loss.
  Rationale: 1.5 cleanly separates "one dominant name" from "a real pair"; 25% flags books like the audited 0.5-of-18 while leaving small deliberate universes (2 of 4) green. Cut-points are view-side and trivially tunable.
  Date/Author: 2026-07-03 / Geronimo (via agent).

(Record further decisions here as they are made, with rationale, date, and author.)

## Outcomes & Retrospective

Not started. To be written per milestone and at completion, including whether the change reduced or increased overall complexity.

## Context and Orientation

The optimizer is a feature of a ClojureScript single-page trading app rendered with Replicant (a data-driven view library; views are pure functions from state to DOM data, and live under `src/hyperopen/views/portfolio/optimize/`). State changes flow through a Nexus-style runtime: views emit `:actions/...` keywords, pure action handlers return new state plus `:effects/...`, and effects run in interpreters. Adding a brand-new action requires lockstep contract entries in `src/hyperopen/schema/runtime_registration/portfolio.cljs`, `src/hyperopen/schema/contracts/action_args.cljs`, and `src/hyperopen/portfolio/optimizer/runtime_catalog.cljs` (checked by exact set-equality in `npm run check`); reusing existing actions needs none of that.

Three routes matter here. `/portfolio/optimize` is an index board of saved scenarios. `/portfolio/optimize/new` is the setup workbench operating on "the draft" — the single live editable configuration at state path `[:portfolio :optimizer :draft]`, autosaved per wallet to browser storage. `/portfolio/optimize/draft` is an alias that renders the retained result of the most recent *unsaved* run (state path `[:portfolio :optimizer :last-successful-run]`); a successful run from the setup page auto-navigates there. Saving promotes the draft to a persistent scenario with an id like `scn_1783105557659` and its own route. "Spectate mode" means viewing a public address read-only without a connected wallet; the optimizer is fully usable in spectate except live-order arming.

The view-model gatekeeper for the results surface is `scenario-detail-model` in `src/hyperopen/portfolio/optimizer/application/view_model/scenario.cljs`. Its helper `retained-unsaved-run?` decides whether an unsaved run is "retained" (three conjuncts: a last-successful-run exists; draft-status is nil or `:draft`; loaded-id is nil or equals the draft id). `route-mismatched?` treats the `/draft` route as legitimate only while that predicate holds; otherwise `scenario-scoped-state` masks the real state with defaults and the view renders a loading shell. The execution surface is a tab of the results page: a staged→armed→running→done/resting/halted machine whose staging pipeline lives in `src/hyperopen/portfolio/optimizer/application/execution.cljs` and whose view is `src/hyperopen/views/portfolio/optimize/execution_tab.cljs` plus `execution_order_table.cljs`.

The trading-safety boundary no milestone may cross: the two-step live-order arming ("Arm execution" then "Confirm & send"), the backend-confirmed order lifecycle, the spectate/read-only submission block, and the stale-plan arming gate all stay exactly as they are. Every change in this plan is pre-trade display, state lifecycle, or configuration honesty.

## Plan of Work

### Milestone 1 — Draft workspace lifecycle integrity

Goal: after saving a scenario, the draft workspace remains fully usable — a later draft edit plus run reveals results on `/portfolio/optimize/draft` exactly as it does before any save; no surface ever claims "loading" when nothing is loading; and Rerun is never an enabled button that does nothing.

Start test-first: extend `test/hyperopen/views/portfolio/optimize/unsaved_draft_route_test.cljs` (and/or the scenario view-model tests) with the failing sequence — simulate run → save (loaded-id becomes a `scn_*` id, draft promoted) → draft edit → new successful draft run → assert `route-mismatched?` is false for `"draft"` and the model renders the new result. This characterizes which conjunct of `retained-unsaved-run?` breaks (the audit's prime suspects: save sets `active-scenario-loaded-id` to the saved id while the draft's id/status no longer satisfy `(or (nil? loaded-id) (= loaded-id draft-id))` / `(contains? #{nil :draft} draft-status)`).

Then fix at the lifecycle seams, in whichever combination the characterization supports: when a run is started *from the draft workspace* (the run action / `run_bridge_workflow.cljs` path that already knows it is the unsaved-draft case), clear `active-scenario-loaded-id` (and restore draft-status to `:draft`) so the completed run is once again a retained unsaved run; and/or when a draft *edit* dirties a just-saved scenario, perform the same restoration. Preserve intact: the saved-`{id}` route's isolation (visiting `/portfolio/optimize/scn_*` for a *different* loaded id must still mask), and save's post-run URL promotion.

Two honesty repairs ride along. First, the masked shell's copy must tell the truth: when the route is `/draft` and there is no retained unsaved run, render the existing idle empty state ("Run or load this scenario…") — never the "is loading" sentence — and drop the spurious "read-only" tag for the draft case. Second, Rerun must be gated the same way Run is: on any surface where the effective universe is empty or the state is masked, Rerun renders disabled with a title explaining why (mirroring the Arm button's disabled-with-reason pattern), instead of an enabled no-op.

### Milestone 2 — Execution commit-point reconciliation

Goal: one coherent set of numbers on the execution tab: dollar KPIs count only legs that will actually be sent, every skipped or blocked row states its reason in plain language, and the verdict's trade count matches the ledger's staged count.

In `src/hyperopen/views/portfolio/optimize/execution_tab.cljs`, change `side-totals` to exclude `:skipped` rows as well as `:blocked` (honoring its own docstring), and give the Buys/Sells KPI subs an "of N legs staged" phrasing so the asset counts refer to sendable legs. In `execution_order_table.cljs`, render the row's `:reason` next to the "skipped" (and "blocked") state — plain-language mapping, e.g. `:within-tolerance` → "within the 3.0 pp band — no trade needed" (interpolating the actual band from the draft constraints), `:already-filled` → "already filled on a previous attempt", `:below-min-notional` → "below the $10 exchange minimum" — as a muted sub-line or title, whichever the dense table tolerates at its existing breakpoints. Verify the verdict trade count in `results_summary.cljs` / the shared `recommendation-deltas` helper counts exactly the ledger's sendable (staged) legs so "6 trades get you there" and "0 / 6" always agree; if it currently counts plan legs, fix the source rather than the copy. Do not renumber or hide rows; the `#` plan-index column may gain a title attribute explaining it is the plan order used by "Resume from #N".

### Milestone 3 — Seeding and restore reliability, with feedback

Goal: entering the optimizer with holdings either shows them loaded or tells the user exactly why not; a page reload never loses a draft the UI claimed was saved; and no primary button is ever a silent no-op.

Three fixes. First, widen the auto-preseed guard in `src/hyperopen/portfolio/navigation.cljs` `route-loader-effects`: treat a *persisted but effectively untouched* draft (empty universe and otherwise equal to the default draft) the same as an absent draft, so a pre-connect visit no longer poisons the preseed forever; keep the existing re-attempt-until-data-arrives behavior and the never-clobber-user-edits guarantee (a non-empty or deliberately-cleared-and-edited universe must never be overwritten — if "cleared by the user" cannot be distinguished from "never seeded", record the distinction with a draft-local touched flag rather than guessing). Second, make the seed's no-op path talk: when `set-portfolio-optimizer-universe-from-current` finds no usable exposures, surface the existing readiness/empty-state machinery with copy like "No open positions to import — search to add assets" (inline near the universe panel, not a toast), so the "Load my holdings" button always visibly responds. Third, fix the reload-restore race under spectate: the per-wallet draft restore must key off the *resolved* effective address (spectated or connected) and re-attempt when identity resolves after route load, following `docs/BROWSER_STORAGE.md`; add a test that boots state where the persisted draft exists for the spectated address but identity resolves after the optimizer route loads, asserting the draft is restored rather than lost.

### Milestone 4 — Honest diagnostics and readiness warnings

Goal: every status token on the results trust rail reflects an actual evaluation; readiness warning groups are distinguishable; and no user-facing warning leaks engineering codes, vendor tiers, or raw instrument ids.

In `src/hyperopen/views/portfolio/optimize/results_diagnostics_rail.cljs`, replace the hardcoded Diversification `:status :ok` with a threshold function over effective-N relative to universe size (pick defensible cut-points and record them in the Decision Log — e.g. caution below a modest fraction, alert when the portfolio is effectively a single position; the exact numbers are a product decision to confirm with the maintainer, but "0.5 of 18 → OK" must become impossible), with subtext naming the dominant position. In `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` `warning-code-summary`, give each stale-history code its own human sentence (native candle cache vs proxy-provider staleness) so two groups can no longer render identical copy with different counts. For the result-warnings drawer, map warning codes to plain-language labels and merge per-asset duplicates into one row per code with an asset list (reusing the grouped-readiness pattern), render instrument labels through the existing label helpers instead of raw `perp:*` ids (same fix for the weight-stability subtext), and strip vendor-tier internals from user copy (the "provider tier" detail may remain in a collapsed technical sub-line if support needs it, but must not be the headline). Keep every underlying warning available — this milestone changes presentation and evaluation honesty, never suppresses information.

### Milestone 5 — Preset and constraint state honesty

Goal: the preset row never claims a preset is ACTIVE while the draft's constraints no longer match it.

Derive the preset row's active state in `src/hyperopen/views/portfolio/optimize/setup_header.cljs` from actual draft-vs-preset equality (objective and constraint envelope): when "Load my holdings" has seeded holdings-derived constraints, the Conservative card shows a truthful modified state (e.g. kicker "modified — constraints from holdings") instead of "ACTIVE", and re-selecting the preset restores its defaults (the existing preset-apply action already does this). Additionally, make the constraint seeding visible at the moment it happens: the Constraints section's "Custom" tag is necessary but not sufficient — add a short inline note in the constraints summary ("seeded from your current book — gross floor 1.76×, cap 152%") the first time the seed runs. Whether the manual button should keep seeding constraints at all is a product question; this milestone makes the current behavior honest, and if the maintainer prefers aligning the button with the auto-preseed's universe-only decision, record that in the Decision Log and reduce scope accordingly.

## Concrete Steps

All commands run from the worktree root.

Bootstrap a fresh worktree once before any gate (a fresh worktree has no `node_modules` and `shadow-cljs` is local-only, so gates otherwise fail with an opaque environmental error):

    npm run setup:worktree

Full required gate matrix (single PASS/FAIL matrix, does not short-circuit):

    npm run gates

Individually: `npm run check`, `npm test`, `npm run test:websocket`.

For UI-affecting milestones (all five), run the smallest relevant Playwright target first, then broaden:

    npm run test:playwright:smoke -- --workers=1

(`--workers=1` avoids the known cold-compile flakiness of the watch server under multiple workers.)

For live verification without a wallet, spectate an account with open positions (the audit used `/portfolio/trader/0xf5d81a135f756ca16544e53c20fc20643ec3ad53`, found via the leaderboard; any account with several open perp positions works — verify with the Hyperliquid `clearinghouseState` info call that `assetPositions` is non-empty). The Milestone 1 wedge repro, pre-fix: run → Save scenario → edit the draft → visit `/portfolio/optimize/draft` → observe the stuck loading shell; run again → observe the success toast pointing at the still-stuck page.

Per milestone, the loop is: write the named failing test (RED), implement, confirm green, update this plan's Progress and living sections.

## Validation and Acceptance

Acceptance is phrased as behavior a human can verify in the running app, plus tests that fail before and pass after.

Milestone 1: perform run → save → edit draft → run again; the result renders on `/portfolio/optimize/draft` with correct KPIs and the draft's actual constraints; the "Scenario draft is loading…" sentence is impossible to reach when nothing is loading (idle empty state renders instead, without a "read-only" tag for the draft); Rerun is disabled-with-reason wherever it cannot run and never silently no-ops; visiting a saved `scn_*` route for a non-loaded scenario still shows the isolation placeholder. Tests: the new lifecycle sequence test in `unsaved_draft_route_test.cljs` fails before and passes after; existing saved-route isolation tests unchanged and green.

Milestone 2: stage an execution with tolerance-band skips; the Buys/Sells KPI dollars equal the sum of staged legs only; every skipped/blocked row shows a plain-language reason; the verdict's trade count equals the ledger's staged count and the "0 / N" fill counter's N. Tests: a `side-totals` unit test with mixed staged/skipped/blocked rows fails before and passes after; an order-table test asserts the reason text renders for `:within-tolerance`.

Milestone 3: with a persisted empty-untouched draft and holdings present, entering `/portfolio/optimize/new` seeds the universe; "Load my holdings" on a positionless account renders the explanatory empty-state copy (never a silent nothing); reload while spectating restores the draft. Tests: preseed-guard test for the persisted-empty case; seed no-op feedback test; identity-arrival restore test.

Milestone 4: a result with effective-N far below universe size shows a caution/alert Diversification row, never OK; two different stale-history codes render distinguishable sentences; the warnings drawer shows grouped plain-language rows with instrument labels (no `perp:*`, no vendor-tier headline). Tests: diagnostics-rail threshold tests; `warning-code-summary` per-code copy test; a warnings-drawer rendering test.

Milestone 5: after "Load my holdings" seeds constraints, the Conservative preset card visibly reads as modified rather than ACTIVE, and re-selecting it restores preset defaults. Test: setup-header state derivation test with a holdings-modified draft.

Whole-plan gate: `npm run gates` PASS and the optimizer Playwright smoke green before this plan moves to `completed/`.

## Idempotence and Recovery

Each milestone is independently shippable and independently revertible; land them one at a time with `npm run gates` green between. Milestone 1 changes state-lifecycle writes at save/run seams and is reverted by restoring the current writes; it must be implemented so repeated saves, repeated runs, and route revisits are stable (no flag that can be consumed once and lost — derive from state facts wherever possible). Milestone 3 touches the persisted-draft restore path: any change to persistence keys or shapes follows `docs/BROWSER_STORAGE.md` (additive, migration-guarded, re-runnable); the widened preseed guard must remain a pure predicate so re-entry loops cannot oscillate between seeding and clearing. Milestones 2, 4, and 5 are pure view/view-model presentation changes with no persistence risk. No milestone adds a new action/effect contract surface unless a genuinely new user gesture is introduced; prefer reusing existing actions to avoid the three-file lockstep churn.

## Artifacts and Notes

Audit evidence retained for reference (2026-07-03, spectating `0xf5d81a…ad53`, 18 positions, ~$2.09M equity):

    Wedged draft route (post-save):  "Scenario draft / scenario id draft · read-only [LOADING]"
    KPIs: N/A · N/A · N/A · N/A   constraints shown: "gross ≤ 2 · cap 50.00%" (defaults, not the draft's)
    while a toast reads: "Optimization complete — The recommendation is ready on the opti…"

    Execution KPIs vs ledger:  Buys "+$632,389.82 · 9 assets"  Sells "−$598,461.25 · 9 assets"
    Orders filled "0 / 6" · staged legs sum ≈ $587k · ledger: 19 rows, 13 × "skipped" (no reason shown),
    largest skipped legs: ZEC +$22,374.97, XRP −$21,305.64, AAVE +$16,767.93

    Trust rail: "Diversification — Effective N · 0.5 of 18 — ● OK"  (status hardcoded)
    Readiness: "15 assets use stale cached history" AND "6 assets use stale cached history" (same copy, overlapping lists)
    Warnings drawer: "insufficient-common-history XRP: CoinGecko Demo provider history window is capped by provider tier."

Keep short RED/GREEN test transcripts, the chosen effective-N thresholds, the final skip-reason copy strings, and any namespace-size-exception bumps here as work proceeds (several files in scope — `scenario_detail_view`, `execution_tab`, their tests — sit near their `dev/namespace_size_exceptions.edn` ceilings).

## Interfaces and Dependencies

Milestone 1: `src/hyperopen/portfolio/optimizer/application/view_model/scenario.cljs` (`retained-unsaved-run?`, `route-mismatched?`, `scenario-scoped-state` — behavior preserved, inputs fixed upstream; masked-shell copy/read-only fix), the draft-run action path and `src/hyperopen/portfolio/optimizer/application/run_bridge_workflow.cljs` (restore retained-unsaved facts on draft run start), the save action in `src/hyperopen/portfolio/optimizer/actions/` (post-save draft-status/loaded-id writes), `src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs` (idle-vs-loading shell selection, Rerun disabled-with-reason). Tests: `test/hyperopen/views/portfolio/optimize/unsaved_draft_route_test.cljs`, `test/hyperopen/portfolio/optimizer/application/run_bridge_workflow_test.cljs`, scenario view-model tests.

Milestone 2: `src/hyperopen/views/portfolio/optimize/execution_tab.cljs` (`side-totals`, KPI subs), `src/hyperopen/views/portfolio/optimize/execution_order_table.cljs` (reason rendering; a pure `skip-reason-label` helper taking the reason keyword and the draft's tolerance band), `src/hyperopen/views/portfolio/optimize/results_summary.cljs` / `recommendation-deltas` (trade-count source). Tests: execution-tab and order-table view tests, `scenario_detail_view_test.cljs`.

Milestone 3: `src/hyperopen/portfolio/navigation.cljs` (`route-loader-effects` preseed guard), `src/hyperopen/portfolio/optimizer/actions/universe.cljs` (no-op feedback), the optimizer draft persistence/restore module (per `docs/BROWSER_STORAGE.md`; restore keyed on resolved effective address with re-attempt). Tests: `universe_from_holdings_actions_test.cljs`, draft persistence tests.

Milestone 4: `src/hyperopen/views/portfolio/optimize/results_diagnostics_rail.cljs` (pure `diversification-status` fn: effective-n + universe-size → `:ok|:caution|:alert`; warning grouping + label rendering via the existing instrument-label helpers), `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` (`warning-code-summary` per-code copy). Tests: diagnostics-rail tests, `setup_readiness_test.cljs`.

Milestone 5: `src/hyperopen/views/portfolio/optimize/setup_header.cljs` (preset-active derivation from draft equality; a pure `preset-state` helper), `src/hyperopen/views/portfolio/optimize/setup_constraint_controls.cljs` (seeded-from-holdings note). Tests: setup-header/layout tests.

No new libraries. No new `:actions/*` or `:effects/*` planned; if one becomes necessary, follow the three-file contract lockstep named in Context and Orientation.
