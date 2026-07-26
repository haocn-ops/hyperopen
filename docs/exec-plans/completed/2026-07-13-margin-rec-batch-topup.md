# Batch Margin Top-Up for Multiple At-Risk Positions

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan follows `/hyperopen/docs/PLANS.md` and must be maintained in accordance with that file.

Context reference: direct user request (2026-07-13) — "if we have multiple
positions facing liquidation … increase the collateral for those positions …
across all of them at once in one user action", building on the shipped
single-position margin-rec feature
(`docs/exec-plans/completed/2026-07-12-margin-rec-panel-design-fidelity.md`).

## Validation / Acceptance

- `npm run check`, `npm test`, `npm run test:websocket` all pass.
- Unit tests cover: candidate selection (risk level, status, dust threshold,
  per-mode amounts), per-dex pool coverage draining most-severe-first, and the
  apply action emitting the panel-close projection before one
  `:effects/api-submit-position-margin` per fundable position, capped at the
  pool and skipping sub-$1 remainders.
- Browser QA: the batch panel renders correctly (workbench scene), the toolbar
  trigger appears only with ≥1 at-risk candidate and never in read-only mode.

## Purpose / Big Picture

Today each isolated position facing liquidation risk gets an individual margin
recommendation (risk chip → anchored panel → prefilled Adjust Margin modal).
When several positions are at risk at once, the user must repeat that flow per
position. After this change, the Positions tab toolbar shows a "Fix liq. risks
(N)" trigger whenever at least one isolated position has a modeled
high/elevated liquidation risk with an actionable recommended top-up. Clicking
it opens an anchored batch panel listing every at-risk position with its
recommended additional collateral under the shared risk target (Conservative /
Balanced / Capital efficient), the before→after modeled liquidation
probability, and the resulting liquidation price. One click applies the
top-ups to all selected positions in a single action, funding the
highest-risk positions first when available collateral cannot cover the total.

Verify by seeding several at-risk isolated positions (browser-QA seed recipe),
observing the toolbar trigger with the correct count, opening the panel,
switching risk modes (amounts update instantly, no recompute), deselecting a
row, and applying — one `updateIsolatedMargin` request per selected position.

## Progress

- [x] Explored existing margin-rec engine, state loop, intent flow, positions toolbar, and registration surfaces.
- [x] state.cljs: `:batch` bucket, `batch-candidates`, `batch-available-pools`, extended `ui-slice`.
- [x] actions.cljs: toggle/close/keydown/toggle-selection + `margin-rec-batch-plan` + `apply-margin-rec-batch`.
- [x] Registration: binding rows, collaborators, action-args specs, effect-order policy.
- [x] View: `margin-rec-batch-panel` popover + toolbar trigger in `positions-header-actions`.
- [x] Unit tests: candidates selection, pool-capped plan, apply effects shape.
- [x] Gates: `npm run gates` — 34/34 PASS (6336 tests, 34189 assertions).
- [x] Browser QA: workbench scenes (three-at-risk, deselected, insufficient-collateral, still-modeling, trigger) render-verified in the dev-server workbench; coin-column width fixed after visual truncation was spotted.

## Surprises & Discoveries

- The effect-order contract is Lean-verified: adding the batch policy required
  a matching entry in `spec/lean/Hyperopen/Formal/EffectOrderContract.lean`
  plus `bb tools/formal.clj sync --surface effect-order-contract` to regenerate
  the committed vectors — the cljs map alone fails the formal conformance test.
- The workbench renders blank on a bare static serve of `resources/public`;
  the shadow-cljs dev server (`npm run dev`, ui-workbench.html on :8080) is
  needed for scene canvases.

- The existing auto top-up intent flow (`margin-rec-process-intents`) already
  builds programmatic `updateIsolatedMargin` requests via
  `position-margin/from-position-row` + `prepare-submit`, and its effect-order
  policy already allows duplicate `:effects/api-submit-position-margin` in one
  pass — the batch apply reuses that exact pattern.
- Risk-mode switching is a pure view selection over the precomputed
  `:by-risk-mode` table, so the batch panel can offer the mode selector with
  instant amount updates and zero recompute cost.

## Decision Log

- Decision: "At-risk" = rec status `:ok`, `:risk-level` ∈ `#{:high :elevated}`,
  and recommended `:additional` ≥ $0.01 under the active risk mode. Rationale:
  matches the row risk chip ("Liq. risk high/elevated") the user already sees;
  `:normal` positions with small suggested top-ups are not "facing liquidation".
- Decision: the trigger shows for N ≥ 1 (not only ≥ 2). One mental model — the
  toolbar button is "all liquidation risks in one place" — beats a button that
  appears and disappears based on count.
- Decision: available collateral is tracked as one pool per dex (named-dex
  clearinghouses are separate pools; the default/unified pool is shared).
  Candidates drain their dex pool most-severe-first (highest `p-now`), so if
  funds run short the worst risks get funded first and the rest are skipped
  (min top-up $1, same threshold as auto top-up intents). In unified account
  mode multiple dex pools can alias the same spot USDC balance; the per-dex
  model can then over-allocate across dexes — the exchange rejects the excess,
  same failure mode as the existing single-position modal.
- Decision: apply caps rather than blocks on insufficient balance; the footer
  states how much of the total is covered before the user commits.
- Decision: desktop toolbar only (the mobile positions view keeps the per-row
  flow); the panel is an anchored popover per docs/FRONTEND.md (page-local,
  recoverable control), like the single-position recommendation panel.
- Decision: risk-mode changes from the batch panel dispatch the existing
  `:actions/set-margin-rec-risk-mode` (shared persisted setting), so the
  single-position panel, row hints, and batch panel always agree.

## Outcomes & Retrospective

- Shipped as planned: toolbar trigger + anchored batch panel + one-action
  apply, reusing the intent-flow request machinery end-to-end.
- All gates pass (`npm run gates` 34/34; 6336 tests, 34189 assertions).
- Follow-up candidates: mobile entry point; a post-apply toast summarizing
  submitted/skipped positions (today each submit surfaces through the existing
  order-response path).

### Locked-agent unlock-and-replay (2026-07-13 follow-up)

- Gap: `apply-margin-rec-batch` originally fanned out N
  `:effects/api-submit-position-margin` effects unconditionally. When agent
  trading is locked, each effect independently hits the error branch in
  `order/effects.cljs api-submit-position-margin` → N "Unlock trading…" error
  toasts and no unlock prompt. This is the anti-pattern the codebase forbids
  (the single-position margin modal has the same latent gap — it errors rather
  than prompting; the batch layer now guards ahead of it).
- Fix: `apply-margin-rec-batch` now branches on `[:wallet :agent :status]`,
  mirroring `order/actions.cljs submit-order` and
  `confirm-portfolio-optimizer-execution`:
  - `:ready` → close panel + N submits (unchanged);
  - `:locked` → flip status to `:unlocking`, clear the agent error, emit
    `:effects/unlock-agent-trading` with `:after-success-actions
    [[:actions/apply-margin-rec-batch]]` — the passkey prompt shows and the
    batch replays itself on success. The panel is left OPEN on the locked pass
    so the replay re-plans from intact selections; it closes only on the
    `:ready` pass that submits;
  - `:unlocking` → `[]` (a prompt is already in flight; don't double-prompt);
  - not enabled (`nil`/`:approving`/`:error`) → open the enable-trading
    recovery modal, same as manual order entry.
- No new action/registration/contract/Lean changes were required:
  `:effects/unlock-agent-trading` classifies as `:other` in the effect-order
  validator (not in any policy's `:heavy-effect-ids`), so it is ignored by the
  phase-order/duplicate checks — exactly why `submit-order`'s locked branch
  passes with an unchanged policy. Replaying the action ITSELF (not a new
  `submit-unlocked-*` action) sidesteps the deselection-reset problem because
  the panel is not closed on the locked pass.
- Tests: `apply-batch-locked-prompts-unlock-and-replays`,
  `apply-batch-unlocking-holds-without-submitting`,
  `apply-batch-not-enabled-opens-recovery` in
  `test/hyperopen/margin_rec/actions_test.cljs`; existing ready-path tests now
  set `[:wallet :agent :status] :ready`. All gates re-run 34/34.
