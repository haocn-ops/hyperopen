# Formally Verify Narrow Order-Form Ownership Invariants

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md`. The primary live `bd` issue for this work is `hyperopen-rj0r`.

## Purpose / Big Picture

Hyperopen already has formal surfaces for vault transfer, order requests, trading submit policy, the websocket runtime kernel, and the runtime effect-order contract. The next unfinished item in the proof order is the narrow order-form ownership slice: the rules that decide which fields may live in persisted `:order-form`, which fields belong only in `:order-form-ui`, which write paths must be rejected, and which UI combinations are impossible once the normalized form is known.

After this work, a contributor will be able to run a dedicated Lean-backed surface for order-form ownership, regenerate deterministic vector fixtures, and prove through ordinary repository tests that persisted order-form state stays clean, blocked canonical writes fail closed, and the effective UI projection still collapses impossible combinations such as scale plus TP/SL or cross margin on isolated-only markets. This is internal correctness work, not a UI redesign. The visible result is a trustworthy repo-local formal workflow and stronger regression protection around a boundary that currently relies on ordinary unit tests plus one generative model test.

## Progress

- [x] (2026-03-28 09:02 EDT) Created and claimed `bd` issue `hyperopen-rj0r` for the next unfinished formalization slice.
- [x] (2026-03-28 09:05 EDT) Re-read `/hyperopen/AGENTS.md`, `/hyperopen/docs/MULTI_AGENT.md`, `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/docs/WORK_TRACKING.md`, and the repo-local `spec-writer` skill before drafting.
- [x] (2026-03-28 09:05 EDT) Audited the current ownership seams in `/hyperopen/src/hyperopen/state/trading/order_form_key_policy.cljs`, `/hyperopen/src/hyperopen/state/trading.cljs`, `/hyperopen/src/hyperopen/trading/order_form_state.cljs`, `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`, and `/hyperopen/src/hyperopen/trading/BOUNDARY.md`.
- [x] (2026-03-28 09:05 EDT) Audited the current regression anchors in `/hyperopen/test/hyperopen/state/trading_test.cljs`, `/hyperopen/test/hyperopen/state/trading/order_form_key_policy_test.cljs`, `/hyperopen/test/hyperopen/state/trading/order_form_state_test.cljs`, `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs`, plus the existing formal-tool workflow under `/hyperopen/tools/formal/**`.
- [x] (2026-03-28 09:05 EDT) Reviewed `/hyperopen/docs/exec-plans/completed/2026-02-25-legacy-order-form-key-policy-support-deprecation-boundary.md` and the recent completed formal ExecPlans so this plan starts from the real boundary work already in the repository.
- [x] (2026-03-28 09:05 EDT) Created this active ExecPlan and froze scope to narrow order-form ownership invariants only. Submit-policy, request construction, locale parsing, and portfolio quality gates are explicitly out of scope.
- [x] (2026-03-28 10:01 EDT) Added the new `order-form-ownership` formal surface to `/hyperopen/tools/formal/**`, `/hyperopen/docs/tools.md`, and `/hyperopen/tools/formal/README.md` without disturbing the existing surfaces.
- [x] (2026-03-28 10:01 EDT) Implemented `/hyperopen/tools/formal/lean/Hyperopen/Formal/OrderFormOwnership.lean`, synced `/hyperopen/tools/formal/generated/order-form-ownership.edn`, and generated `/hyperopen/test/hyperopen/formal/order_form_ownership_vectors.cljs`.
- [x] (2026-03-28 10:09 EDT) Updated `/hyperopen/dev/formal_tooling_test.clj` so the wrapper test suite explicitly tracks the new surface and its generated manifest/bridge paths.
- [x] (2026-03-28 10:05 EDT) Verified `order-form-ownership` plus the five existing formal surfaces, and confirmed the docs and delimiter preflights still pass.
- [x] (2026-03-28 17:18 EDT) Extracted the private ownership sanitizer into `/hyperopen/src/hyperopen/trading/order_form_ownership.cljs` and rewired `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs` to use the new pure seam without changing the transition entrypoints.
- [x] (2026-03-28 17:20 EDT) Added `/hyperopen/src/hyperopen/schema/order_form_ownership_contracts.cljs`, added `/hyperopen/test/hyperopen/state/trading/order_form_ownership_formal_conformance_test.cljs`, regenerated `/hyperopen/test/test_runner_generated.cljs`, and added readable anchors for non-limit-like UI closure, projection-driven cross-margin collapse, leverage ownership, and ownership sanitizer behavior.
- [x] (2026-03-28 17:33 EDT) Re-synced and re-verified the new surface after fixing the leverage ownership vector mismatch, reran every formal surface, and passed `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-03-28 17:34 EDT) Closed `hyperopen-rj0r` with reason `Implemented order-form-ownership formal surface and passed validation`.

## Surprises & Discoveries

- Observation: the repository already has an explicit key-policy table, so the missing work is not policy discovery but proof-surface extraction and conformance.
  Evidence: `/hyperopen/src/hyperopen/state/trading/order_form_key_policy.cljs` already defines UI-owned keys, legacy compatibility keys, deprecated canonical keys, and blocked canonical write paths.

- Observation: the strongest current regression anchor is already a property-based transition model, which means the formal plan must complement rather than replace that coverage.
  Evidence: `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs` includes `transition-state-machine-generative-model-invariants-test`, which already checks persisted-form purity, UI/form coherence, and scale TP/SL closure over generated intent sequences.

- Observation: the most important production seam for this work is currently private.
  Evidence: the helper `enforce-field-ownership` in `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs` rewrites transition results so canonical `:order-form` state is persisted cleanly and UI/runtime data is projected into the correct maps, but it is not available as a stable public seam for conformance work.

- Observation: readable margin-mode coverage is currently transition-driven, not projection-driven.
  Evidence: `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs` proves `set-order-margin-mode` collapses cross to isolated when a market forbids cross, but there is no equally direct test for `order-form-ui-state` forcing `:margin-mode :isolated` and closing the dropdown when the persisted form still says cross.

- Observation: the public formal-tool registry is coherent, but one documentation subsection is already stale.
  Evidence: `/hyperopen/tools/formal/core.clj` and `/hyperopen/tools/formal/README.md` list `effect-order-contract`, while the `Supported surfaces` subsection in `/hyperopen/docs/tools.md` still omits it. This plan should leave the docs fully synchronized when adding one more surface instead of preserving that drift.

- Observation: the first cut of the leverage conformance corpus modeled UI-owned leverage fields as if they persisted in `:order-form`.
  Evidence: the interrupted `hyperopen.state.trading.order-form-ownership-formal-conformance-test` run failed because the production transition correctly stripped `:ui-leverage` and `:leverage-draft` from persisted `:order-form`, while the generated leverage vectors still expected those keys. The fix was to narrow the leverage vectors to the persisted-ownership property and assert the cleaned persisted-form projection directly.

## Decision Log

- Decision: name the new formal surface `order-form-ownership`.
  Rationale: the scope is not the entire order-form domain. It is specifically the ownership and consistency rules that govern persisted canonical state, effective UI projection, and the transition post-processing boundary.
  Date/Author: 2026-03-28 / Codex

- Decision: use Lean plus generated vectors, not TLA+.
  Rationale: this slice is pure data normalization and finite transition-result reasoning. The work is closer to the existing Lean surfaces than to the websocket temporal model.
  Date/Author: 2026-03-28 / Codex

- Decision: extract the private `enforce-field-ownership` helper into a new pure production seam, tentatively `/hyperopen/src/hyperopen/trading/order_form_ownership.cljs`, and keep the existing public transition entrypoints as facades over that seam.
  Rationale: the helper is the real ownership kernel today. Making it stable and explicit reduces hidden complexity and gives the conformance harness a clean public boundary without proving the whole transition namespace.
  Date/Author: 2026-03-28 / Codex

- Decision: keep the proof scope narrow to ownership invariants and UI-consistency rules only.
  Rationale: locale parsing, price fallback, submit gating, quote/base size conversions, and the full transition state machine already have different owners and different test/value profiles. Reopening them here would turn a bounded proof slice into a broad trading refactor.
  Date/Author: 2026-03-28 / Codex

- Decision: add proof-surface-specific schema and projection helpers instead of overloading existing VM-facing order-form schemas.
  Rationale: `/hyperopen/src/hyperopen/schema/order_form_contracts.cljs` validates view-model and transition shapes, not formal vector corpora. The formal surface needs reduced projections for persistence, effective UI, and transition ownership outcomes.
  Date/Author: 2026-03-28 / Codex

## Outcomes & Retrospective

This plan is implemented, and the linked `bd` task `hyperopen-rj0r` is closed.

The repository now has a sixth Lean-backed surface, `order-form-ownership`, wired through the formal CLI, manifest generation, committed vector bridge, and formal-tooling tests. The production ownership seam is now explicit in `/hyperopen/src/hyperopen/trading/order_form_ownership.cljs`, the transition namespace delegates to it, and the new proof-surface contract namespace plus CLJS conformance suite keep the Lean vectors tied to the real CLJS behavior.

The highest-value production outcomes are:

- persisted `:order-form` ownership is now exercised both through the extracted sanitizer and the committed formal vectors,
- blocked canonical writes remain fail-closed,
- `effective-order-form-ui` and `order-form-ui-state` now have direct readable tests for non-limit-like closure and cross-margin collapse,
- leverage normalization is checked both through readable tests and formal conformance, including the fact that leverage stays UI-owned after persistence.

The only implementation surprise that required a code change after the first test pass was the leverage vector mismatch. That was the correct failure mode: the formal surface initially over-modeled persisted leverage state, the conformance test caught it, and the final synced vectors now encode the actual ownership invariant instead of a cleaner-but-wrong model.

## Context and Orientation

Order-form state in Hyperopen is intentionally split across three maps.

` :order-form ` is the canonical persisted draft. It should contain only domain-owned fields such as order type, side, price, size, TP/SL trigger data, TWAP inputs, and scale inputs.

` :order-form-ui ` is the UI-owned state. It holds dropdown open flags, price-focus state, leverage draft state, entry-mode, margin-mode presentation state, and the display-only size string.

` :order-form-runtime ` is the runtime workflow state. It holds submit/error workflow flags such as `:submitting?` and `:error`.

An “ownership invariant” in this plan means a rule that keeps those three maps honest. Examples: persisted canonical state must not retain UI-owned keys, blocked canonical write paths must not mutate `:order-form`, and the effective UI projection must collapse impossible combinations like “scale order with TP/SL panel open”.

The current code already has a strong foundation for this work.

`/hyperopen/src/hyperopen/state/trading/order_form_key_policy.cljs` is the canonical key-policy table. It defines UI-owned keys, legacy compatibility keys, deprecated canonical keys, and blocked write paths.

`/hyperopen/src/hyperopen/state/trading.cljs` owns the public read and projection seams that matter here. `persist-order-form` strips deprecated canonical keys. `effective-order-form-ui` derives UI state from the normalized form and closes impossible UI combinations. `order-form-draft` composes persisted form plus UI overlays into a normalized working form. `order-form-ui-state` applies the final UI projection and additionally collapses cross margin to isolated when the market does not allow cross.

`/hyperopen/src/hyperopen/trading/order_form_state.cljs` owns default order-form and UI state plus normalization of UI-owned fields like margin mode, leverage, size-input mode, and TP/SL units.

`/hyperopen/src/hyperopen/trading/order_form_transitions.cljs` owns deterministic transition entrypoints such as `set-order-ui-leverage`, `confirm-order-ui-leverage`, `set-order-margin-mode`, `toggle-order-tpsl-panel`, and `update-order-form`. The key hidden seam is the private helper `enforce-field-ownership`, which rewrites transition outputs so the persisted canonical form is stripped clean and the UI/runtime parts are projected into the right maps.

The highest-value invariants already visible in production are:

- `PersistedFormPurity`: `persist-order-form` and transition post-processing must strip deprecated canonical keys.
- `CanonicalWriteBlocked`: `update-order-form` must reject writes to blocked canonical paths and only clear runtime error state.
- `EffectiveUiConsistency`: the effective UI projection must force `entry-mode = entry-mode-for-type(type)`, close price focus and TIF dropdown on non-limit-like types, close TP/SL affordances for scale orders, and keep the TP/SL unit dropdown closed whenever the TP/SL panel is closed.
- `CrossMarginCollapse`: `order-form-ui-state` must force `:margin-mode :isolated` and close the margin dropdown when the market forbids cross.
- `ReduceOnlyExcludesTPSL`: enabling reduce-only must disable TP and SL and close the TP/SL panel.
- `LeverageUiOwnership`: leverage changes must normalize to positive integers within market rules and remain UI-owned after persistence.
- `OffsetCacheInvalidation`: direct TP/SL trigger edits clear the corresponding offset cache, and TP/SL unit changes clear both caches.

The current regression anchors are already meaningful. `/hyperopen/test/hyperopen/state/trading/order_form_key_policy_test.cljs` proves the key table itself is unique and coherent. `/hyperopen/test/hyperopen/state/trading_test.cljs` proves public persistence stripping. `/hyperopen/test/hyperopen/state/trading/order_form_state_test.cljs` proves several normalized UI rules. `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs` proves blocked writes, reduce-only behavior, offset-cache invalidation, margin-mode collapse, leverage confirmation behavior, and a property-based model of transition invariants. The formal surface should reuse these anchors and close the remaining readable gaps instead of replacing them wholesale.

## Plan of Work

### Milestone 1: Register the new formal surface and keep the tooling contract coherent

Start by teaching the repo-local formal wrapper about a sixth surface named `order-form-ownership`. Edit `/hyperopen/tools/formal/core.clj`, `/hyperopen/tools/formal/lean/Hyperopen/Formal/Common.lean`, and `/hyperopen/tools/formal/lean/Hyperopen/Formal.lean` so `formal:verify` and `formal:sync` understand the new surface id, manifest path, generated-source path, and Lean module name `Hyperopen.Formal.OrderFormOwnership`.

The generated manifest should live at `/hyperopen/tools/formal/generated/order-form-ownership.edn`. The transient generated source should live at `/hyperopen/target/formal/order-form-ownership-vectors.cljs`. The checked-in bridge should live at `/hyperopen/test/hyperopen/formal/order_form_ownership_vectors.cljs`.

Update `/hyperopen/dev/formal_tooling_test.clj`, `/hyperopen/tools/formal/README.md`, and `/hyperopen/docs/tools.md` so the wrapper, the README, and the public docs all agree on the full supported-surface list. While touching `/hyperopen/docs/tools.md`, fix the existing omission of `effect-order-contract` in the `Supported surfaces` subsection so the document ends in a fully synchronized state.

Milestone 1 is complete when `npm run test:formal-tooling` passes, `npm run formal:verify -- --surface order-form-ownership` resolves the new surface name, and the existing five surfaces still behave exactly as before.

### Milestone 2: Extract the pure ownership sanitizer without widening the public API

The goal of this milestone is to make the actual ownership kernel explicit. Move the private `enforce-field-ownership` helper out of `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs` into a new pure namespace, tentatively `/hyperopen/src/hyperopen/trading/order_form_ownership.cljs`.

That new namespace should own only the ownership post-processing logic. In plain language, it should accept the current state plus a proposed transition result and return the sanitized result where:

- persisted `:order-form` is passed through `persist-order-form`
- effective `:order-form-ui` is recomputed from the working form plus any UI overrides
- runtime state is preserved only when explicitly present in the transition
- no deprecated canonical keys leak back into stored `:order-form`

Keep the existing public transition entrypoints in `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`. They should call the extracted seam and keep their current names and behavior. Do not widen the proof target into localized numeric parsing, price fallback capture, or size recalculation logic. The extraction should make the ownership boundary explicit, not rewrite the rest of the transition system.

If implementation proves that one helper is not enough for stable conformance, add a second pure projection helper in the same namespace rather than making the conformance tests depend on private map shapes inside the transition namespace.

Milestone 2 is complete when the ownership sanitizer is a stable pure seam, the transition entrypoints still pass their existing tests, and the repository does not gain any new public UI-facing API surface beyond the new pure ownership namespace.

### Milestone 3: Model the ownership kernel in Lean and emit deterministic vectors

Add `/hyperopen/tools/formal/lean/Hyperopen/Formal/OrderFormOwnership.lean` as the executable reference model for this slice. The model should stay finite and narrow. It does not need to model every order-form field or every transition branch. It needs only the fields and operations that affect ownership and the impossible-UI combinations listed above.

The Lean model must cover at least these invariants:

- `PersistedFormPurity`: persisted canonical form strips every key listed in `deprecated-canonical-order-form-keys`.
- `CanonicalWriteBlocked`: blocked canonical write paths produce no `:order-form` or `:order-form-ui` mutation and only clear runtime error state.
- `EffectiveUiConsistency`: `entry-mode` follows normalized type, non-limit-like types close price focus and TIF dropdown, scale closes TP/SL affordances, and a closed TP/SL panel closes the TP/SL unit dropdown.
- `CrossMarginCollapse`: when cross margin is disallowed, `order-form-ui-state` forces isolated mode and closes the margin-mode dropdown.
- `ReduceOnlyExcludesTPSL`: enabling reduce-only disables TP and SL and closes the TP/SL panel.
- `LeverageUiOwnership`: leverage normalization returns positive integers within market constraints and the persisted canonical form does not retain UI-only leverage fields after ownership sanitization.
- `OffsetCacheInvalidation`: direct trigger edits clear the matching offset cache and TP/SL unit changes clear both caches.

The generated bridge should export small deterministic vector corpora instead of one monolithic state dump. A good split is:

- persistence vectors for `persist-order-form`
- effective UI vectors for `effective-order-form-ui`
- UI-state vectors for `order-form-ui-state`
- ownership-transition vectors for representative transition outcomes after the sanitizer runs

Keep JavaScript locale parsing and floating-point display behavior out of the Lean model. Those already have ordinary tests and do not belong to this ownership slice.

Milestone 3 is complete when `formal:sync` deterministically regenerates `/hyperopen/test/hyperopen/formal/order_form_ownership_vectors.cljs`, `formal:verify` fails on stale generated output, and the Lean module contains representative theorems for the invariants above.

### Milestone 4: Add proof-surface contracts, CLJS conformance, and missing readable anchors

Once Lean emits vectors, add a proof-surface contract namespace at `/hyperopen/src/hyperopen/schema/order_form_ownership_contracts.cljs`. That namespace should validate the reduced vector shapes and expose small projection helpers for comparing production outputs against the generated corpus. Keep it proof-surface-specific. Do not overload `/hyperopen/src/hyperopen/schema/order_form_contracts.cljs`, which is for view-model and transition contracts.

Add focused CLJS conformance coverage, preferably in a new namespace such as `/hyperopen/test/hyperopen/trading/order_form_ownership_formal_conformance_test.cljs`. That harness should compare:

- production `persist-order-form` against the persistence vectors
- production `effective-order-form-ui` against the effective-UI vectors
- production `order-form-ui-state` against the UI-state vectors
- the extracted ownership sanitizer and representative public transitions against the ownership-transition vectors

Keep the ordinary human-readable tests and tighten the gaps discovered during planning. At minimum, add direct readable anchors for:

- `effective-order-form-ui` closing `:price-input-focused?` and `:tif-dropdown-open?` on every non-limit-like type
- `order-form-ui-state` forcing `:margin-mode :isolated` and `:margin-mode-dropdown-open? false` when cross is forbidden
- leverage normalization against market max bounds if the ownership surface claims that invariant

Do not remove the existing property-based transition model in `/hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs`. It should remain as a broader stochastic regression anchor around the narrower formalized kernel.

Milestone 4 is complete when the generated vectors are consumed by ordinary CLJS tests, the missing readable anchors have been added, and the formal surface is no longer isolated from the normal test suite.

### Milestone 5: Validate the full proof pipeline and keep the active plan honest

This is the close-out milestone. Run the new `order-form-ownership` surface through `formal:sync` and `formal:verify`, rerun the existing formal surfaces so wrapper changes cannot silently break them, regenerate the test runner if a new namespace was added, and run the repository gates required by this repo for code changes.

Do not close `hyperopen-rj0r` until all of the following are true:

- the Lean workspace builds with the new surface
- stale generated output is detected correctly
- the committed CLJS vectors are deterministic
- the new conformance tests pass
- the existing formal surfaces still verify
- `npm run check`, `npm test`, and `npm run test:websocket` all pass

Current evidence from this session:

- `npm run formal:sync -- --surface order-form-ownership` succeeded and wrote both the manifest and the committed bridge.
- `npm run formal:verify -- --surface order-form-ownership` succeeded.
- `npm run formal:verify` also succeeded for `vault-transfer`, `order-request-standard`, `order-request-advanced`, `effect-order-contract`, and `trading-submit-policy`.
- `npx shadow-cljs compile test && node out/test.js --test=hyperopen.state.trading-test,hyperopen.state.trading.order-form-key-policy-test,hyperopen.state.trading.order-form-state-test,hyperopen.trading.order-form-transitions-test,hyperopen.state.trading.order-form-ownership-formal-conformance-test` passed.
- `npm run test:formal-tooling` passed.
- `npm run test:runner:generate` passed.
- `npm test` passed.
- `npm run test:websocket` passed.
- `npm run check` passed.
- `npm run lint:docs` passed.

If implementation discovers a mismatch between the production ownership behavior and the intended invariant, record the smallest counterexample directly in this plan, then either fix production or narrow the invariant honestly before completion.

## Concrete Steps

From `/Users/barry/.codex/worktrees/0bbd/hyperopen`, begin by confirming the current formal baseline:

    export PATH="$HOME/.elan/bin:$PATH"
    npm run formal:verify -- --surface vault-transfer
    npm run formal:verify -- --surface order-request-standard
    npm run formal:verify -- --surface order-request-advanced
    npm run formal:verify -- --surface trading-submit-policy
    npm run formal:verify -- --surface effect-order-contract

Those commands should pass before any new ownership work begins. If one fails, fix the pre-existing formal regression first instead of layering a sixth surface on top of a broken baseline.

Next, add wrapper support for the new surface:

    npm run test:formal-tooling
    npm run formal:verify -- --surface order-form-ownership

At first, the verify command may fail because the Lean module and generated bridge do not exist yet. That is acceptable during implementation. By the end of Milestone 1, it must resolve the new surface name and fail only for real missing-artifact or proof-work reasons.

Then extract the pure ownership seam and implement the Lean model. The intended edit set is:

    /hyperopen/tools/formal/core.clj
    /hyperopen/tools/formal/README.md
    /hyperopen/docs/tools.md
    /hyperopen/dev/formal_tooling_test.clj
    /hyperopen/tools/formal/generated/order-form-ownership.edn
    /hyperopen/tools/formal/lean/Hyperopen/Formal/Common.lean
    /hyperopen/tools/formal/lean/Hyperopen/Formal.lean
    /hyperopen/tools/formal/lean/Hyperopen/Formal/OrderFormOwnership.lean
    /hyperopen/src/hyperopen/trading/order_form_ownership.cljs
    /hyperopen/src/hyperopen/trading/order_form_transitions.cljs
    /hyperopen/src/hyperopen/schema/order_form_ownership_contracts.cljs
    /hyperopen/test/hyperopen/formal/order_form_ownership_vectors.cljs
    /hyperopen/test/hyperopen/trading/order_form_ownership_formal_conformance_test.cljs
    /hyperopen/test/hyperopen/state/trading_test.cljs
    /hyperopen/test/hyperopen/state/trading/order_form_state_test.cljs
    /hyperopen/test/hyperopen/trading/order_form_transitions_test.cljs

If a new test namespace is added, regenerate the test runner before validation:

    npm run test:runner:generate

For focused iteration, compile the test target and run only the ownership-related suites:

    npx shadow-cljs compile test
    node out/test.js --test=hyperopen.state.trading-test,hyperopen.state.trading.order-form-key-policy-test,hyperopen.state.trading.order-form-state-test,hyperopen.trading.order-form-transitions-test,hyperopen.trading.order-form-ownership-formal-conformance-test

Once the Lean surface emits vectors, refresh and verify the generated bridge:

    export PATH="$HOME/.elan/bin:$PATH"
    npm run formal:sync -- --surface order-form-ownership
    npm run formal:verify -- --surface order-form-ownership

Finally, rerun the existing formal surfaces and the required repository gates:

    export PATH="$HOME/.elan/bin:$PATH"
    npm run formal:verify -- --surface vault-transfer
    npm run formal:verify -- --surface order-request-standard
    npm run formal:verify -- --surface order-request-advanced
    npm run formal:verify -- --surface trading-submit-policy
    npm run formal:verify -- --surface effect-order-contract
    npm run test:formal-tooling
    npm run test:runner:generate
    npm test
    npm run test:websocket
    npm run check
    npm run lint:docs

Record the exact outcomes in `Progress` and `Outcomes & Retrospective` as the work lands.

## Validation and Acceptance

Acceptance is not “a Lean file exists.” Acceptance is that Hyperopen gains a stable formal surface for order-form ownership and ordinary tests prove the production code still conforms to that verified model.

The implementation is complete when a contributor can run:

    export PATH="$HOME/.elan/bin:$PATH"
    npm run formal:verify -- --surface order-form-ownership

and see the new surface verify cleanly without stale-generated-source failures.

It is also complete when ordinary CLJS tests prove these observable behaviors:

- `persist-order-form` always strips every deprecated canonical key, including UI-owned and legacy runtime keys.
- blocked canonical write paths fail closed and do not mutate persisted `:order-form` or `:order-form-ui`.
- `effective-order-form-ui` closes impossible UI combinations for non-limit-like and scale order types.
- `order-form-ui-state` forces isolated margin mode and closes the margin dropdown when the market forbids cross.
- enabling reduce-only disables TP/SL and closes the TP/SL panel.
- TP/SL trigger edits and TP/SL unit changes clear offset caches exactly as modeled.
- leverage updates stay normalized and UI-owned after transition post-processing.
- the existing five formal surfaces still verify after the new surface lands.

## Idempotence and Recovery

All wrapper and proof commands in this plan must be rerunnable. `formal:sync` is allowed to overwrite `/hyperopen/test/hyperopen/formal/order_form_ownership_vectors.cljs`, but it must do so deterministically. `formal:verify` must remain read-only apart from transient files under `/hyperopen/target/formal/**`.

Implement the extraction in additive slices. First register the new surface. Then extract the ownership seam with the old transition behavior preserved. Then add the Lean model and generated vectors. Then add conformance tests. This order keeps failures local and easier to diagnose.

If the seam extraction introduces regressions, keep the new namespace and route the old transition namespace back through a thin wrapper rather than reverting the plan entirely. If the generated vectors look suspicious, rerun `formal:sync`, inspect only the checked-in bridge under `/hyperopen/test/hyperopen/formal/order_form_ownership_vectors.cljs`, and compare it against the corresponding theorem/export changes in `/hyperopen/tools/formal/lean/Hyperopen/Formal/OrderFormOwnership.lean`.

If Lean is missing or misconfigured, the repository must still be able to run `npm test`, `npm run test:websocket`, and `npm run check` against the checked-in generated vectors. Only `formal:sync` and `formal:verify` may require Lean.

## Artifacts and Notes

The most important evidence for this work should stay concise inside this plan as implementation proceeds. Capture short command transcripts such as:

    npm run formal:verify -- --surface order-form-ownership
    ...
    Verified order-form-ownership surface and generated source freshness.

If implementation exposes a mismatch between production and model, record the smallest failing case directly here. Good examples would be:

- a blocked write path that still leaks a UI-owned key into persisted `:order-form`
- an isolated-only market where `order-form-ui-state` still reports `:margin-mode :cross`
- a scale-order UI projection that still allows the TP/SL panel to remain open

Keep those examples reduced. A future contributor should be able to reproduce them from the plan alone without reconstructing full app state from unrelated fixtures.

## Interfaces and Dependencies

The new formal surface must be named `order-form-ownership` in both the Babashka wrapper and the Lean `Surface` enum. The Lean module should be `Hyperopen.Formal.OrderFormOwnership`. The generated bridge should be the namespace `hyperopen.formal.order-form-ownership-vectors`, written to `/hyperopen/test/hyperopen/formal/order_form_ownership_vectors.cljs`.

The extracted pure ownership seam should live at `/hyperopen/src/hyperopen/trading/order_form_ownership.cljs`. The preferred public entrypoint is:

    enforce-field-ownership

If implementation needs a second helper for stable conformance, add it there instead of widening the public view-model APIs.

The existing public seams that must remain stable are:

- `/hyperopen/src/hyperopen/state/trading.cljs`
  `persist-order-form`
  `effective-order-form-ui`
  `order-form-draft`
  `order-form-ui-state`
  `normalize-order-form`
- `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`
  `update-order-form`
  `set-order-ui-leverage`
  `set-order-ui-leverage-draft`
  `confirm-order-ui-leverage`
  `set-order-margin-mode`
  `toggle-order-tpsl-panel`

The proof surface depends on the existing order-form key-policy table in `/hyperopen/src/hyperopen/state/trading/order_form_key_policy.cljs` and the existing transition and state-normalization behavior. It does not depend on the submit-policy formal surface conceptually and must not widen into `submit-policy`, `build-order-request`, websocket runtime, or portfolio metric gating.

Plan revision note (2026-03-28 09:05 EDT): Initial active ExecPlan created after claiming `hyperopen-rj0r`, auditing the current order-form ownership seams and tests, and selecting the narrow ownership kernel as the next highest-priority unfinished formalization slice.
