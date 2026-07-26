# Formally Verify the Runtime Effect-Order Contract

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md`. The primary live `bd` issue for this work is `hyperopen-4wx0`.

## Purpose / Big Picture

Hyperopen already has centralized runtime enforcement for interaction effect ordering, but that enforcement is still only ordinary ClojureScript plus example-driven tests. Today `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs` is the single authority that decides which actions are covered, which effect identifiers count as projection, persistence, or heavy input/output, and which sequences must be rejected for ordering or duplicate-heavy violations. After this work, a contributor will be able to run a dedicated Lean-backed formal surface for that contract, regenerate deterministic vector fixtures, and prove through ordinary repository tests that the real runtime contract still matches the verified model.

This is an internal correctness feature, not a UI feature. The visible outcome is that a contributor can run one formal command for the effect-order contract, see generated vectors remain deterministic, and then run ordinary ClojureScript tests that prove the production contract still rejects the same illegal effect sequences and still permits the same legal ones. That reduces drift risk in a module that protects responsiveness and duplicate-heavy-effect safety across many action producers.

## Progress

- [x] (2026-03-28 07:34 EDT) Re-read `/hyperopen/AGENTS.md`, `/hyperopen/docs/MULTI_AGENT.md`, `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md` before drafting this plan.
- [x] (2026-03-28 07:34 EDT) Audited `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`, `/hyperopen/src/hyperopen/runtime/validation.cljs`, `/hyperopen/test/hyperopen/runtime/validation_test.cljs`, `/hyperopen/test/hyperopen/vaults/application/transfer_commands_test.cljs`, `/hyperopen/docs/architecture-decision-records/0018-effect-order-authority-contract.md`, and the current formal-tooling surfaces.
- [x] (2026-03-28 07:33 EDT) Created and claimed `bd` issue `hyperopen-4wx0` for the effect-order formalization track.
- [x] (2026-03-28 07:34 EDT) Created this active ExecPlan and froze scope to the centralized runtime effect-order contract and its validation wrapper. Action-specific business semantics, browser flows, and unrelated proof surfaces are explicitly out of scope.
- [x] (2026-03-28 09:44 EDT) Added the new `effect-order-contract` formal surface across `/hyperopen/tools/formal/core.clj`, `/hyperopen/tools/formal/lean/Hyperopen/Formal/Common.lean`, `/hyperopen/tools/formal/lean/Hyperopen/Formal.lean`, `/hyperopen/tools/formal/generated/effect-order-contract.edn`, `/hyperopen/test/hyperopen/formal/effect_order_contract_vectors.cljs`, `/hyperopen/tools/formal/README.md`, `/hyperopen/docs/tools.md`, and `/hyperopen/dev/formal_tooling_test.clj`.
- [x] (2026-03-28 09:44 EDT) Implemented `/hyperopen/tools/formal/lean/Hyperopen/Formal/EffectOrderContract.lean` with deterministic policy, assertion, summary, and wrapper corpora covering policy coverage, tracked phase monotonicity, projection-before-heavy enforcement, duplicate-heavy rejection, uncovered-action pass-through, and representative wrapper enforcement.
- [x] (2026-03-28 09:44 EDT) Added CLJS proof-surface contracts in `/hyperopen/src/hyperopen/schema/effect_order_contracts.cljs` and runtime conformance coverage in `/hyperopen/test/hyperopen/runtime/effect_order_contract_formal_conformance_test.cljs`.
- [x] (2026-03-28 09:44 EDT) Tightened direct regression anchors in `/hyperopen/test/hyperopen/runtime/effect_order_contract_test.cljs` and `/hyperopen/test/hyperopen/runtime/validation_test.cljs`, including phase-order regression after heavy I/O, duplicate-heavy-allowed behavior, and wrapper-level phase-order debug traces.
- [x] (2026-03-28 09:45 EDT) Ran the new surface sync/verify commands, reran the existing formal surfaces, and completed the required repository gates with green results.

## Surprises & Discoveries

- Observation: the production proof target is already a pure list-reasoning seam.
  Evidence: `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs` is pure and deterministic; it classifies effect identifiers, looks up an action policy, and either returns the original effect list or throws a descriptive error. No browser or network interpreter logic is mixed into this file.

- Observation: the repo already has the right tooling shape for this work, but no surface for it yet.
  Evidence: `/hyperopen/tools/formal/core.clj`, `/hyperopen/tools/formal/README.md`, `/hyperopen/tools/formal/lean/Hyperopen/Formal/Common.lean`, and `/hyperopen/dev/formal_tooling_test.clj` already support `vault-transfer`, `order-request-standard`, `order-request-advanced`, and `trading-submit-policy`, but there is no `effect-order-contract` entry in any of those places.

- Observation: current runtime tests cover projection-before-heavy, duplicate-heavy rejection, uncovered-action pass-through, and wrapper enforcement, but they do not yet give a readable anchor for every invariant worth modeling.
  Evidence: `/hyperopen/test/hyperopen/runtime/validation_test.cljs` exercises heavy-before-projection failures and duplicate-heavy failures, while phase-order regression through persistence after heavy input/output and summary-shape behavior are not directly pinned there.

- Observation: action-specific tests already rely on this contract as a centralized authority boundary.
  Evidence: `/hyperopen/test/hyperopen/vaults/application/transfer_commands_test.cljs` calls `effect-order-contract/assert-action-effect-order!` directly to prove the vault-transfer action effects satisfy the runtime contract, which means the formal model can stay centered on the contract module rather than reopening every action producer.

- Observation: `cljs.test/is` is the wrong primitive inside a conformance helper that needs to catch thrown contract errors and project them into a modeled failure shape.
  Evidence: the first draft of `/hyperopen/test/hyperopen/runtime/effect_order_contract_formal_conformance_test.cljs` wrapped `assert-action-effect-order!` inside `is`, which converted expected throws into test errors before the helper could compare them to generated failure vectors. The final harness calls the production function directly and only uses `is` on the normalized projections.

- Observation: wrapper conformance is only complete if the generated wrapper corpus matches the runtime wrapper branches, not just the core assertion branches.
  Evidence: review against `/hyperopen/test/hyperopen/runtime/validation_test.cljs` showed that wrapper-specific coverage needed explicit examples for uncovered pass-through, heavy-before-projection failure, duplicate-heavy rejection, duplicate-heavy-allowed success, and validation-disabled trace behavior. The final Lean wrapper corpus now includes those representative cases.

## Decision Log

- Decision: formalize the existing centralized contract as its own Lean surface instead of treating it as an incidental helper inside runtime validation.
  Rationale: `/hyperopen/docs/architecture-decision-records/0018-effect-order-authority-contract.md` explicitly makes this module the authority for covered interaction actions. The proof surface should therefore map to that authority boundary directly.
  Date/Author: 2026-03-28 / Codex

- Decision: keep the proof scope focused on contract semantics, not on every action producer that happens to use the contract.
  Rationale: the high-value invariant is that the authority module classifies and rejects effect sequences correctly. Re-proving each producer would duplicate business-specific tests and turn a small proof surface into a broad integration campaign.
  Date/Author: 2026-03-28 / Codex

- Decision: use generated vectors plus ordinary CLJS conformance tests, matching the existing repository formal-tool pattern.
  Rationale: Hyperopen already uses this workflow for vault transfer, order requests, and trading submit policy. Reusing the same wrapper and generated-source contract keeps the new surface understandable and operationally consistent.
  Date/Author: 2026-03-28 / Codex

- Decision: keep `effect-phase` private unless conformance work proves a stable public projection is necessary.
  Rationale: `effect-order-summary` already exposes the phase sequence and derived counters in a stable debug-oriented projection, so the CLJS side can compare behavior without widening the production public API unless that becomes necessary.
  Date/Author: 2026-03-28 / Codex

- Decision: make the wrapper conformance test explicitly debug-build aware instead of pretending debug-trace recording is unconditional runtime behavior.
  Rationale: `/hyperopen/src/hyperopen/runtime/validation.cljs` records traces only behind `goog.DEBUG`. The generated wrapper vectors still describe the intended debug-build behavior, but the CLJS conformance test now gates trace expectations on both the modeled flag and `goog.DEBUG` so the test reflects the real runtime boundary honestly.
  Date/Author: 2026-03-28 / Codex

## Outcomes & Retrospective

This work is complete for the effect-order-contract slice. Hyperopen now has a fifth repo-local formal surface at `/hyperopen/tools/formal/lean/Hyperopen/Formal/EffectOrderContract.lean`, deterministic generated artifacts at `/hyperopen/tools/formal/generated/effect-order-contract.edn` and `/hyperopen/test/hyperopen/formal/effect_order_contract_vectors.cljs`, proof-surface schema guards at `/hyperopen/src/hyperopen/schema/effect_order_contracts.cljs`, and runtime conformance coverage at `/hyperopen/test/hyperopen/runtime/effect_order_contract_formal_conformance_test.cljs`.

The implementation stayed within the intended authority boundary. `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs` and `/hyperopen/src/hyperopen/runtime/validation.cljs` were not widened or rewritten. Instead, the work added a formal model and deterministic regression vectors around the existing production contract, then tightened direct human-readable tests where the proof vectors relied on behavior that had been only implicit before.

Validation results on 2026-03-28:

- `export PATH="$HOME/.elan/bin:$PATH" && npm run formal:sync -- --surface effect-order-contract`
- `export PATH="$HOME/.elan/bin:$PATH" && npm run formal:verify -- --surface effect-order-contract`
- `export PATH="$HOME/.elan/bin:$PATH" && npm run formal:verify -- --surface vault-transfer`
- `export PATH="$HOME/.elan/bin:$PATH" && npm run formal:verify -- --surface order-request-standard`
- `export PATH="$HOME/.elan/bin:$PATH" && npm run formal:verify -- --surface order-request-advanced`
- `export PATH="$HOME/.elan/bin:$PATH" && npm run formal:verify -- --surface trading-submit-policy`
- `npm run test:formal-tooling`
- `npm run test:runner:generate`
- `npm test`
- `npm run test:websocket`
- `npm run check`

All of those commands passed. The only recurring log noise during `npm test` was the pre-existing `storage-unavailable` preview-cache test fixture message, and it remained non-failing.

## Context and Orientation

The runtime effect-order contract is Hyperopen’s centralized rule set for “projection first, heavy work later” interaction handling. In this repository, a projection effect is an immediate state update emitted as `:effects/save` or `:effects/save-many`. A persistence effect is a local durability write such as `:effects/local-storage-set`. A heavy input/output effect is a subscription, fetch, reconnect, or API request named explicitly in a per-action policy. Those three categories matter because interactive actions should update visible state before starting slower subscription or network work, and because some actions must never emit the same heavy effect identifier twice.

The authority lives in `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`. That file owns the policy map from `action-id` to required phase order, whether projection must precede heavy effects, whether duplicate heavy effects are allowed, and the set of heavy effect identifiers for that action. It also owns `effect-order-summary`, which turns an action and effect list into a debug projection, and `assert-action-effect-order!`, which either returns the original effect list or throws an error that identifies the violated rule.

The enforcement boundary lives in `/hyperopen/src/hyperopen/runtime/validation.cljs`. `wrap-action-handler` first validates payload shape, then calls the action handler, then validates emitted effect shape, and finally invokes `effect-order-contract/assert-action-effect-order!` before returning the effects. This is the behavior that makes the centralized contract operational during validation-enabled runs.

Current regression anchors live mostly in `/hyperopen/test/hyperopen/runtime/validation_test.cljs`. Those tests prove several key behaviors already: invalid payload arity is rejected, invalid effect shapes are rejected, covered actions allow projection before heavy input/output, covered actions reject heavy effects before projection, covered actions reject duplicate heavy effects when forbidden, and uncovered actions pass through unchanged. A smaller number of action-level tests such as `/hyperopen/test/hyperopen/vaults/application/transfer_commands_test.cljs` call the contract directly to prove a producer satisfies the authority.

The repository’s formal-tool workflow lives under `/hyperopen/tools/formal/**`. The command wrapper in `/hyperopen/tools/formal/core.clj`, the Lean `Surface` enum in `/hyperopen/tools/formal/lean/Hyperopen/Formal/Common.lean`, the entrypoint module in `/hyperopen/tools/formal/lean/Hyperopen/Formal.lean`, the generated manifests under `/hyperopen/tools/formal/generated/`, and the committed CLJS bridges under `/hyperopen/test/hyperopen/formal/**` together define how Hyperopen adds new proof surfaces. Existing surfaces for `vault-transfer`, `order-request-standard`, `order-request-advanced`, and `trading-submit-policy` provide the pattern this work should follow.

This plan intentionally does not try to prove every action in the application. It proves the contract authority itself. That means the model should cover policy definition, phase classification, phase-order validation, duplicate-heavy behavior, summary projections, and validation-wrapper enforcement, but it should not widen into browser, interpreter, or route-loading behavior outside the central runtime contract.

## Plan of Work

### Milestone 1: Register the new formal surface and preserve the existing workflow

Start by adding a new surface named `effect-order-contract` to the repo-local formal wrapper and documentation. The goal of this milestone is not to prove anything yet; it is to make the existing formal workflow capable of building, syncing, and verifying a new modeled surface without disturbing the four surfaces that already exist.

Edit `/hyperopen/tools/formal/core.clj` so `supported-surfaces` includes `effect-order-contract` with the Lean module `Hyperopen.Formal.EffectOrderContract`, a generated manifest path under `/hyperopen/tools/formal/generated/effect-order-contract.edn`, a transient generated source path under `/hyperopen/target/formal/effect-order-contract-vectors.cljs`, and a committed generated bridge path under `/hyperopen/test/hyperopen/formal/effect_order_contract_vectors.cljs`. Update the usage text there so a novice sees the new surface in both the synopsis and examples.

Edit `/hyperopen/tools/formal/lean/Hyperopen/Formal/Common.lean` so the `Surface` enum, `surfaceId`, `surfaceModuleName`, `surfaceStatus`, `surfaceGeneratedSourcePath?`, `parseSurface?`, and `usage` string all recognize `effect-order-contract`. Update `/hyperopen/tools/formal/lean/Hyperopen/Formal.lean` so `runVerify` and `runSync` dispatch to a new `Hyperopen.Formal.EffectOrderContract` module. Update `/hyperopen/dev/formal_tooling_test.clj`, `/hyperopen/tools/formal/README.md`, and `/hyperopen/docs/tools.md` to treat the new surface as a first-class modeled surface. At the end of this milestone, `npm run test:formal-tooling` should pass and `npm run formal:verify -- --surface effect-order-contract` should resolve the new surface name even if the proof module still needs implementation.

### Milestone 2: Model the effect-order contract in Lean as pure policy and list reasoning

This milestone adds the actual proof surface in `/hyperopen/tools/formal/lean/Hyperopen/Formal/EffectOrderContract.lean`. Keep the model focused on pure contract behavior and generated projections, not on every production action body. The Lean side should define plain-language equivalents for tracked phases, action policy, summary projection, and assertion outcome.

The model must cover the contract rules already present in production:

- every covered action has a defined policy with required phase order, duplicate-heavy allowance, and heavy-effect identifiers
- effect identifiers classify deterministically into `projection`, `persistence`, `heavy-io`, or `other`
- heavy effects must appear only after a projection for policies that require that rule
- tracked phases must be nondecreasing across an effect list
- duplicate heavy effect identifiers must fail when the policy forbids them
- uncovered actions must behave as pass-through rather than failing under an implied policy
- summary projections must report effect identifiers, phases, projection count, heavy count, duplicate-heavy identifiers, projection-before-heavy truth value, and whether the sequence passes the assertion

Do not model the full JavaScript error object or any debug-trace ring-buffer behavior from `/hyperopen/src/hyperopen/runtime/validation.cljs`. Those are runtime conveniences, not the authority contract. The useful proof boundary is whether the sequence is accepted or rejected and, for rejected sequences, which rule category explains the rejection. The Lean model can represent that as an enumerated violation reason rather than a stringly typed production error message.

Generated vectors from this module should be rich enough for CLJS conformance without forcing the CLJS side to call private functions. A good shape is a small set of deterministic defs such as policy projections for covered actions, summary vectors for representative effect sequences, and assertion vectors that state whether a given action/effect list should pass or should fail with a specific rule. Keep the generated data focused and finite; this surface is proving rule semantics, not exhaustively enumerating every possible action payload.

### Milestone 3: Bridge the model into CLJS conformance tests and tighten regression anchors

Once Lean can emit deterministic vectors, add the committed generated bridge at `/hyperopen/test/hyperopen/formal/effect_order_contract_vectors.cljs`. If the vector shapes need runtime assertions before tests compare them, add a focused schema namespace such as `/hyperopen/src/hyperopen/schema/effect_order_contracts.cljs`. That namespace should validate proof-surface projections only. Do not overload existing runtime schemas with proof-only responsibilities.

Add focused CLJS conformance coverage. The preferred shape is a new namespace such as `/hyperopen/test/hyperopen/runtime/effect_order_contract_formal_conformance_test.cljs` plus targeted updates to `/hyperopen/test/hyperopen/runtime/validation_test.cljs`. The conformance layer should compare the production contract against the generated vectors in ways that remain stable even if implementation details move around internally. Concretely:

- compare the modeled covered-action policy projection against `covered-action-ids` and `action-policy`
- compare modeled summary vectors against `effect-order-summary`
- compare modeled pass and fail vectors against `assert-action-effect-order!`
- compare wrapper enforcement vectors against `wrap-action-handler` when validation is enabled

The existing runtime tests are already useful human-readable anchors, but they are not complete enough for the full formal story. Add or tighten direct tests for any modeled rule that is currently only implied. The likely missing anchors are tracked phase monotonicity through a persistence-after-heavy regression, summary projection fields, and a clear wrapper-level assertion that a covered action fails for a phase-order regression rather than only for heavy-before-projection or duplicate-heavy.

If a newly added human-readable test duplicates a formal vector exactly, keep the example-based test anyway if it makes the behavior easier to understand. The formal vectors prove contract fidelity; the direct tests explain the intended rule to future maintainers.

### Milestone 4: Validate the full proof pipeline without disturbing existing surfaces

This close-out milestone proves that the new surface behaves like a normal Hyperopen formal surface instead of a one-off experiment. Run `formal:sync` and `formal:verify` for `effect-order-contract`, then rerun the existing formal surfaces to prove the wrapper changes did not break them. After that, run the repository’s required gates for code changes.

Do not close `hyperopen-4wx0` until all of the following are true: the Lean workspace builds with the new surface; stale generated output is detected correctly; the committed CLJS vectors are deterministic; the runtime conformance tests pass; the existing formal surfaces still verify; and the standard repository gates remain green. If implementation discovers a mismatch between the current production contract and the intended invariant, record the smallest counterexample in this plan, then either fix production or narrow the invariant honestly before completion.

## Concrete Steps

From `/Users/barry/.codex/worktrees/0f9e/hyperopen`, begin by confirming the current formal baseline:

    export PATH="$HOME/.elan/bin:$PATH"
    npm run formal:verify -- --surface vault-transfer
    npm run formal:verify -- --surface order-request-standard
    npm run formal:verify -- --surface order-request-advanced
    npm run formal:verify -- --surface trading-submit-policy

Those commands should pass before any new effect-order work begins. If one fails, stop and fix the pre-existing formal regression first instead of layering a new proof surface on top of a broken baseline.

Next, add wrapper support and tests for the new surface:

    npm run test:formal-tooling
    npm run formal:verify -- --surface effect-order-contract

Initially the verify command may fail because the Lean module and generated bridge do not exist yet. That is acceptable during development. By the end of Milestone 1, the command must resolve the surface and fail only for real missing-artifact or proof-work reasons.

Then implement the Lean module, manifests, and generated bridge. The intended edit set is:

    /hyperopen/tools/formal/core.clj
    /hyperopen/tools/formal/README.md
    /hyperopen/docs/tools.md
    /hyperopen/dev/formal_tooling_test.clj
    /hyperopen/tools/formal/generated/effect-order-contract.edn
    /hyperopen/tools/formal/lean/Hyperopen/Formal/Common.lean
    /hyperopen/tools/formal/lean/Hyperopen/Formal.lean
    /hyperopen/tools/formal/lean/Hyperopen/Formal/EffectOrderContract.lean
    /hyperopen/test/hyperopen/formal/effect_order_contract_vectors.cljs
    /hyperopen/src/hyperopen/schema/effect_order_contracts.cljs
    /hyperopen/test/hyperopen/runtime/effect_order_contract_formal_conformance_test.cljs
    /hyperopen/test/hyperopen/runtime/validation_test.cljs

Keep `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs` and `/hyperopen/src/hyperopen/runtime/validation.cljs` as the production authority and enforcement boundaries. Only edit those files if implementation discovers that a tiny pure helper or public projection is necessary to support stable conformance. Do not widen the public runtime API casually.

If a new test namespace is added, regenerate the test runner before final validation:

    npm run test:runner:generate

Once the Lean surface emits vectors, refresh and verify the generated bridge:

    npm run formal:sync -- --surface effect-order-contract
    npm run formal:verify -- --surface effect-order-contract

Finally, rerun the existing formal surfaces and the standard local gates:

    npm run formal:verify -- --surface vault-transfer
    npm run formal:verify -- --surface order-request-standard
    npm run formal:verify -- --surface order-request-advanced
    npm run formal:verify -- --surface trading-submit-policy
    npm run test:formal-tooling
    npm test
    npm run test:websocket
    npm run check
    npm run lint:docs

Record exact outcomes in `Progress` and `Outcomes & Retrospective` as the work lands.

## Validation and Acceptance

Acceptance is not “a Lean file exists.” Acceptance is that Hyperopen gains a stable formal surface for the centralized effect-order authority contract and that ordinary tests prove the production runtime still conforms to the verified model.

The implementation is complete when a contributor can run:

    export PATH="$HOME/.elan/bin:$PATH"
    npm run formal:verify -- --surface effect-order-contract

and see the new surface verify cleanly without stale-generated-source failures.

It is also complete when ordinary CLJS tests prove these observable behaviors:

Covered actions retain deterministic policy metadata that matches the modeled policy projection.

`effect-order-summary` reports the same phase list, projection count, heavy count, duplicate-heavy identifiers, projection-before-heavy flag, and pass/fail status that the model predicts for the generated examples.

`assert-action-effect-order!` accepts legal covered sequences, rejects illegal covered sequences with the expected rule category, and leaves uncovered actions unchanged.

`wrap-action-handler` enforces the centralized contract when validation is enabled, so covered actions fail fast on modeled ordering regressions instead of returning illegal effect sequences.

The existing formal surfaces for vault transfer, order requests, and trading submit policy still verify after the new surface lands, proving that wrapper and tooling changes did not silently break established proof work.

## Idempotence and Recovery

All wrapper and proof commands in this plan must be rerunnable. `formal:sync` is allowed to overwrite `/hyperopen/test/hyperopen/formal/effect_order_contract_vectors.cljs`, but it must do so deterministically. `formal:verify` must remain read-only apart from transient files under `/hyperopen/target/formal/**`.

If Lean is missing or misconfigured, the repository must still be able to run `npm test`, `npm run test:websocket`, and `npm run check` against the checked-in generated vectors. Only `formal:sync` and `formal:verify` may require Lean. If the generated effect-order bridge looks suspicious, rerun `formal:sync`, inspect the generated namespace only, and confirm the corresponding theorem or export change in `/hyperopen/tools/formal/lean/Hyperopen/Formal/EffectOrderContract.lean` before accepting it.

If the conformance layer discovers that the production contract exposes too little stable projection for a clean comparison, prefer adding a tiny pure projection helper in `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs` over making tests depend on private implementation details. If even that would widen the public API too far, fall back to comparing `effect-order-summary` plus assertion pass/fail behavior and record that choice in the `Decision Log`.

## Artifacts and Notes

The most important evidence for this work should stay concise inside this plan as implementation proceeds. Capture short command transcripts such as:

    npm run formal:verify -- --surface effect-order-contract
    ...
    Verified effect-order-contract surface and generated source freshness.

Keep one or two reduced vector examples in the plan if they clarify the contract. Good examples are a covered action that passes because projection precedes heavy input/output and a covered action that fails because a persistence effect appears after heavy input/output or because a heavy effect identifier is duplicated when duplicates are forbidden.

If implementation exposes a mismatch between production and model, record the smallest failing action/effect list directly in this section so the next contributor can reproduce it without reconstructing application state.

## Interfaces and Dependencies

The new formal surface must be named `effect-order-contract` in both the Babashka wrapper and the Lean `Surface` enum. The Lean module should be `Hyperopen.Formal.EffectOrderContract`. The generated bridge should be the namespace `hyperopen.formal.effect-order-contract-vectors`, written to `/hyperopen/test/hyperopen/formal/effect_order_contract_vectors.cljs`.

The production authority remains `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`. The end state should preserve these stable public functions:

    action-policy
    covered-action-ids
    covered-action?
    effect-order-summary
    assert-action-effect-order!

The validation enforcement boundary remains `/hyperopen/src/hyperopen/runtime/validation.cljs`, especially `wrap-action-handler`. The formal work may add a proof-surface-specific contracts namespace under `/hyperopen/src/hyperopen/schema/**` if generated vectors need runtime shape validation, but it must not move ordering authority out of the runtime contract module.

The model must treat the ADR at `/hyperopen/docs/architecture-decision-records/0018-effect-order-authority-contract.md` as the policy boundary: covered interaction actions require explicit policy entries, tracked phases must not regress, heavy input/output must not outrun projection where required, and duplicate heavy effects must fail unless explicitly allowed. Uncovered actions must remain pass-through by design.

Plan revision note (2026-03-28 07:34 EDT): Initial active ExecPlan created after auditing the runtime effect-order authority module, runtime validation wrapper, current runtime tests, the effect-order ADR, and the existing repo-local formal workflow. The plan deliberately scopes the work to the centralized authority contract and wrapper conformance rather than reopening every action producer that relies on the contract.

Plan revision note (2026-03-28 07:40 EDT): Consolidated duplicate sub-agent drafts into one canonical active plan and repointed the plan to the single live `bd` issue `hyperopen-4wx0` so the active-plan and work-tracking contracts stay consistent.
