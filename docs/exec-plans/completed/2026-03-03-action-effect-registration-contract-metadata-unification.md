# Action/Effect Registration And Contract Metadata Unification

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Local tracker reference for implementation status was `bd` issue `hyperopen-7wl` (per `/hyperopen/docs/WORK_TRACKING.md`).

## Purpose / Big Picture

Today, adding or changing one runtime action or effect requires synchronized edits across multiple metadata tables:

- `/hyperopen/src/hyperopen/runtime/collaborators.cljs`
- `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`
- `/hyperopen/src/hyperopen/registry/runtime.cljs`
- `/hyperopen/src/hyperopen/schema/contracts.cljs`

The explicit pain note in `/hyperopen/docs/exec-plans/active/2026-02-25-account-info-coin-search-fuzzy-filter.md` confirms this fan-out as agent tax: one action change requires coordinated updates across registration and contract surfaces.

After this refactor, registration and contract metadata for runtime actions/effects will be owned by one canonical catalog module. Runtime registry bindings, contract ID coverage, and registry-composition extraction will derive from that catalog. The only remaining per-action/effect implementation edits outside the catalog should be the actual handler implementation and collaborator function wiring.

A contributor can verify the outcome by adding a temporary action descriptor in one catalog location, wiring one collaborator function, and observing that registration IDs, contract coverage, and composition wiring update automatically without editing three additional metadata lists.

## Progress

- [x] (2026-03-03 19:42Z) Reviewed governance entrypoints and planning rules in `/hyperopen/AGENTS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/.agents/PLANS.md`.
- [x] (2026-03-03 19:44Z) Reviewed work-tracking policy in `/hyperopen/docs/WORK_TRACKING.md`.
- [x] (2026-03-03 19:48Z) Audited current fan-out surfaces in `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`, `/hyperopen/src/hyperopen/registry/runtime.cljs`, `/hyperopen/src/hyperopen/runtime/collaborators.cljs`, and `/hyperopen/src/hyperopen/schema/contracts.cljs`.
- [x] (2026-03-03 19:49Z) Created and claimed initial `bd` tracking task for this refactor.
- [x] (2026-03-03 19:52Z) Authored initial ExecPlan with migration milestones, drift tests, and validation gates.
- [x] (2026-03-03 20:06Z) Recreated tracker after board reset as epic `hyperopen-7wl` with child milestone issues `hyperopen-7wl.1` through `hyperopen-7wl.6`.
- [x] (2026-03-03 20:12Z) Milestone 1: Introduced canonical runtime registration catalog in `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs` and added duplicate-ID drift checks.
- [x] (2026-03-03 20:12Z) Milestone 2: Refactored `/hyperopen/src/hyperopen/registry/runtime.cljs` to derive registration rows and ID sets from catalog.
- [x] (2026-03-03 20:15Z) Milestone 3: Added fail-fast contract drift assertions in `/hyperopen/src/hyperopen/schema/contracts.cljs` and derived contracted ID sets from catalog.
- [x] (2026-03-03 20:16Z) Milestone 4: Replaced manual `/hyperopen/src/hyperopen/runtime/registry_composition.cljs` forwarding lists with catalog-driven runtime handler extraction.
- [x] (2026-03-03 20:17Z) Milestone 5 (test portion): Added catalog-to-runtime-deps coverage tests in `/hyperopen/test/hyperopen/runtime/wiring_test.cljs` and duplicate-key guard test in `/hyperopen/test/hyperopen/runtime/registry_composition_test.cljs`.
- [x] (2026-03-03 20:18Z) Milestone 6 (validation portion): Ran required gates successfully: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-03-03 20:20Z) Milestone 5 (docs/ADR portion): Updated `/hyperopen/docs/RELIABILITY.md` and added ADR `/hyperopen/docs/architecture-decision-records/0023-action-effect-runtime-registration-catalog-authority.md`.
- [x] (2026-03-03 20:21Z) Milestone 6 (closure portion): completed retrospective updates and closed `hyperopen-7wl` epic + children.

## Surprises & Discoveries

- Observation: The current architecture already contains a successful precedent for this style of unification (`/hyperopen/src/hyperopen/schema/order_form_command_catalog.cljs`), where two consumers derive from one catalog instead of owning duplicate tables.
  Evidence: `/hyperopen/docs/architecture-decision-records/0019-command-action-catalog-authority.md`.

- Observation: Runtime ID coverage tests currently compare two independently maintained sets (runtime registry vs contracts), which catches drift but does not eliminate duplicated authoring burden.
  Evidence: `/hyperopen/test/hyperopen/schema/contracts_coverage_test.cljs`.

- Observation: `runtime/registry_composition.cljs` repeats large key lists that mirror collaborator keys and registry bindings, so it is a major duplication amplifier.
  Evidence: `/hyperopen/src/hyperopen/runtime/registry_composition.cljs` has ~645 lines dominated by manual key forwarding maps.

- Observation: The account-info fuzzy-search plan recorded exactly the pain this refactor targets: action additions require synchronized registry/contract edits across multiple files.
  Evidence: `/hyperopen/docs/exec-plans/active/2026-02-25-account-info-coin-search-fuzzy-filter.md` (Surprises & Discoveries section).

## Decision Log

- Decision: Create one canonical catalog for runtime action/effect metadata with explicit descriptor rows (`id`, `handler-key`, collaborator `deps-path`, `args-spec` key).
  Rationale: This keeps metadata explicit and human-readable while removing multi-file synchronization tax.
  Date/Author: 2026-03-03 / Codex

- Decision: Keep collaborator function ownership in `/hyperopen/src/hyperopen/runtime/collaborators.cljs` rather than introducing runtime reflection or dynamic namespace scanning.
  Rationale: Explicit function wiring is easier for agents to trace and preserves deterministic dependency boundaries.
  Date/Author: 2026-03-03 / Codex

- Decision: Migrate additively: introduce catalog and derivations first, then remove duplicated manual maps only after parity tests pass.
  Rationale: Reduces blast radius and keeps rollback simple.
  Date/Author: 2026-03-03 / Codex

- Decision: Preserve public runtime registration and validation APIs (`register-actions!`, `register-effects!`, `registered-action-ids`, `registered-effect-ids`, contract assertion entrypoints).
  Rationale: AGENTS guardrails require stable public API boundaries unless explicitly requested.
  Date/Author: 2026-03-03 / Codex

- Decision: Use descriptor-driven helper functions, not macros or implicit conventions, to derive maps for registry and contract lookups.
  Rationale: Agent comprehension best practices favor explicit, inspectable data over hidden generation magic.
  Date/Author: 2026-03-03 / Codex

## Outcomes & Retrospective

- Registration metadata duplication was removed from two major runtime surfaces (`registry/runtime.cljs` and `runtime/registry_composition.cljs`) and centralized in one catalog namespace.
- Runtime composition now rejects duplicate handler keys across nested dependency domains, converting silent shadowing risk into explicit startup failure.
- Contract drift is now detected eagerly during contracts namespace init via catalog ID parity checks, reducing latent mismatch risk.
- Reliability governance now codifies runtime registration catalog authority, with architectural ownership captured in ADR 0023.

## Context and Orientation

This repository’s runtime registration pipeline has four distinct responsibilities today:

1. Collaborator ownership: `/hyperopen/src/hyperopen/runtime/collaborators.cljs` maps handler keys to concrete functions grouped by domain.
2. Registry composition: `/hyperopen/src/hyperopen/runtime/registry_composition.cljs` manually extracts those keys into flat handler maps.
3. Runtime registry bindings: `/hyperopen/src/hyperopen/registry/runtime.cljs` maps action/effect IDs to handler keys and installs wrapped handlers.
4. Contract metadata: `/hyperopen/src/hyperopen/schema/contracts.cljs` maps action/effect IDs to args specs and exposes contracted ID sets.

In this plan:

- An "action/effect descriptor" means one metadata row that declares runtime id, handler key, collaborator lookup path, and argument contract spec key.
- A "catalog" means the canonical vector of descriptors used by all consumers.
- A "deps path" means a vector path into collaborator maps, such as `[:account-history :set-order-history-page]`.
- "Drift" means any mismatch where one consumer knows about an ID or key that another consumer does not.

The architecture constraints that matter most for this refactor are:

- Preserve deterministic runtime behavior and side-effect boundaries (`/hyperopen/docs/RELIABILITY.md`).
- Keep changes scoped and APIs stable (`/hyperopen/AGENTS.md` hard guardrails).
- Use one source of truth for long-lived metadata to reduce SRP violations and agent tax (the explicit rationale from this task and existing ADR-0019 precedent).

## Plan of Work

### Milestone 1: Add Canonical Catalog Module And Drift Guard Rails

Create a new pure metadata namespace, for example `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`, with two explicit descriptor collections:

- Action descriptors
- Effect descriptors

Each descriptor should include:

- `:id` (for example `:actions/select-asset` or `:effects/save`)
- `:handler-key` (for example `:select-asset`, `:save`)
- `:deps-path` (for example `[:asset-selector :select-asset]`, `[:storage :save]`)
- `:args-spec` (qualified spec keyword expected in contracts namespace)

Add helper accessors in the catalog namespace:

- descriptor queries by kind and id
- `[id handler-key]` runtime-binding rows
- contracted id sets by kind

Add tests that enforce:

- no duplicate IDs within action descriptors
- no duplicate IDs within effect descriptors
- no descriptor missing required keys
- all descriptor `:args-spec` keywords resolve to specs (after contracts load)

### Milestone 2: Derive Runtime Registry Bindings From Catalog

Refactor `/hyperopen/src/hyperopen/registry/runtime.cljs` so `action-bindings` and `effect-bindings` are catalog-derived, not manually curated.

Keep existing runtime behavior unchanged:

- `registered-action-ids`
- `registered-effect-ids`
- `register-actions!`
- `register-effects!`
- validation wrapping and missing-handler errors

This milestone should remove the large static vectors in favor of catalog queries while preserving order where required.

### Milestone 3: Derive Contract Metadata From Catalog

Refactor `/hyperopen/src/hyperopen/schema/contracts.cljs` so contracted ID sets and args-spec lookup are derived from the same catalog descriptors.

Implementation target:

- replace manually maintained `action-args-spec-by-id` and `effect-args-spec-by-id` tables with catalog-derived maps (or equivalent lookup functions)
- keep `assert-action-args!`, `assert-effect-args!`, `contracted-action-ids`, `contracted-effect-ids`, and `action-ids-using-any-args` public semantics stable

Add regression checks ensuring catalog IDs and contract IDs remain identical without relying on parallel hand-edited maps.

### Milestone 4: Replace Manual Registry-Composition Forwarding With Catalog-Driven Extraction

Refactor `/hyperopen/src/hyperopen/runtime/registry_composition.cljs` to use descriptor-driven extraction from collaborator dependency maps instead of manual destructuring lists.

Add one small generic extractor for each kind:

- `build-effect-handlers` from effect descriptors and effect deps map
- `build-action-handlers` from action descriptors and action deps map

Preserve:

- runtime determinism
- existing output map keys (`:save`, `:select-asset`, etc.)
- registration dependency shape returned by `runtime-registration-deps`

### Milestone 5: Add Collaborator Coverage + Documentation Updates

Add drift guard tests that prove every catalog descriptor `:deps-path` resolves to a function in default collaborator deps:

- action side: `runtime-action-deps {}`
- effect side: `runtime-effect-deps {}`

Update docs to encode the new ownership contract:

- `/hyperopen/docs/RELIABILITY.md`: add a rule that runtime action/effect registration and contract metadata derive from the canonical runtime registration catalog.
- Add an ADR if scope materially changes architecture governance (likely ADR 0020).

### Milestone 6: Validation Gates And Issue Closure

Run required gates:

- `npm run check`
- `npm test`
- `npm run test:websocket`

If green, update this plan’s retrospective and close `bd` issue `hyperopen-7wl` with completion reason.

## Concrete Steps

All commands run from `/hyperopen`.

1. Introduce catalog namespace and initial failing drift tests.

   - Create `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`.
   - Add tests (new file expected: `/hyperopen/test/hyperopen/schema/runtime_registration_catalog_test.cljs`).
   - Run:
     - `npm test`

   Expected result before full migration: drift tests expose at least one mismatch until consumers are switched.

2. Migrate runtime registry bindings to catalog.

   - Edit `/hyperopen/src/hyperopen/registry/runtime.cljs`.
   - Run:
     - `npm test`

   Expected result: `registered-action-ids` and `registered-effect-ids` still match prior behavior.

3. Migrate contracts to catalog-derived metadata.

   - Edit `/hyperopen/src/hyperopen/schema/contracts.cljs`.
   - Update `contracts_coverage` tests as needed.
   - Run:
     - `npm test`

   Expected result: contracts coverage remains green with no manual ID table drift.

4. Migrate registry composition to catalog-driven extraction and remove manual key-forwarding maps.

   - Edit `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`.
   - Run:
     - `npm test`
     - `npm run test:websocket`

   Expected result: runtime wiring tests and websocket suite remain green.

5. Add collaborator resolution drift guards and finalize docs.

   - Edit `/hyperopen/src/hyperopen/runtime/collaborators.cljs` tests if needed.
   - Update `/hyperopen/docs/RELIABILITY.md`.
   - Add ADR file if final scope warrants.

6. Run required gates and close tracker.

   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
   - `bd close hyperopen-7wl --reason "Completed" --json`

## Validation and Acceptance

This work is accepted when all conditions are true:

1. Runtime action/effect metadata has one canonical descriptor authority.
2. `/registry/runtime`, `/schema/contracts`, and `/runtime/registry_composition` derive their metadata from that authority rather than independent manual tables.
3. Adding a new action/effect registration entry no longer requires direct edits in all four prior metadata files.
4. Drift guard tests fail if any descriptor is missing in registry bindings, contracts, or collaborator deps.
5. Existing public runtime registration/validation APIs remain stable.
6. Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

Migration is designed to be additive and recoverable:

- Add catalog + tests first.
- Switch one consumer at a time (registry, then contracts, then composition).
- Remove duplicated manual tables only after parity tests pass.

If a milestone fails:

- restore the previous consumer implementation for that one file,
- keep catalog and drift tests,
- split descriptors or migration patches into smaller commits,
- re-run tests before proceeding.

No destructive data migration is involved.

## Artifacts and Notes

Primary source files expected to change:

- `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs` (new)
- `/hyperopen/src/hyperopen/registry/runtime.cljs`
- `/hyperopen/src/hyperopen/schema/contracts.cljs`
- `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`
- `/hyperopen/src/hyperopen/runtime/collaborators.cljs` (minimal, mostly for coverage alignment)
- `/hyperopen/docs/RELIABILITY.md`
- `/hyperopen/docs/architecture-decision-records/00xx-*.md` (if ADR added)

Primary tests expected to change:

- `/hyperopen/test/hyperopen/schema/contracts_coverage_test.cljs`
- `/hyperopen/test/hyperopen/runtime/registry_composition_test.cljs`
- `/hyperopen/test/hyperopen/runtime/collaborators_test.cljs`
- `/hyperopen/test/hyperopen/schema/runtime_registration_catalog_test.cljs` (new)

Command snippets useful during implementation:

    rg -n "action-bindings|effect-bindings|action-args-spec-by-id|effect-args-spec-by-id" src/hyperopen
    rg -n "Action contract drift detected|Effect contract drift detected" test/hyperopen/schema

## Interfaces and Dependencies

Public interfaces that must remain stable:

- `/hyperopen/src/hyperopen/registry/runtime.cljs`
  - `register-actions!`
  - `register-effects!`
  - `registered-action-ids`
  - `registered-effect-ids`
- `/hyperopen/src/hyperopen/schema/contracts.cljs`
  - `assert-action-args!`
  - `assert-effect-args!`
  - `contracted-action-ids`
  - `contracted-effect-ids`

New internal interface to add:

- Catalog query API in `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs` for descriptor rows and derived binding/ID views.

Dependencies:

- No new external libraries.
- Keep descriptor catalog pure and deterministic.
- Keep side effects at runtime interpreters/infrastructure boundaries only.

Plan revision note: 2026-03-03 19:52Z - Initial ExecPlan created to unify runtime action/effect registration metadata and contract metadata under a single catalog authority, with `bd` tracking id `hyperopen-ef2`.
Plan revision note: 2026-03-03 20:06Z - Updated work-tracking references to epic `hyperopen-7wl` and added milestone child issue IDs after Beads board reset/reinit.
