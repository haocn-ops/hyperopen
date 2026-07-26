# ADR 0023: Action/Effect Runtime Registration Catalog Authority

- Status: Accepted
- Date: 2026-03-03

## Context

Runtime action/effect metadata was split across multiple modules:

- `/hyperopen/src/hyperopen/registry/runtime.cljs` owned action/effect ID -> handler-key registration rows
- `/hyperopen/src/hyperopen/runtime/registry_composition.cljs` owned manual action/effect handler forwarding key lists
- `/hyperopen/src/hyperopen/schema/contracts.cljs` owned contracted action/effect ID sets and args-spec lookup maps

That structure created repeated synchronization work and drift risk:

- adding/changing one action or effect required coordinated edits across multiple files
- registry and contract coverage checks detected mismatches late but did not remove duplicate authoring burden
- runtime composition could silently diverge from registry binding intent due to independent key lists

## Decision

1. Runtime action/effect registration authority is centralized in:
   `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`.
2. The catalog owns canonical action/effect binding rows (`[id handler-key]`) and ID/key helper queries.
3. `/hyperopen/src/hyperopen/registry/runtime.cljs` derives registration rows and registered ID sets from that catalog.
4. `/hyperopen/src/hyperopen/runtime/registry_composition.cljs` derives runtime handler key selection from that catalog rather than hand-maintained forwarding tables.
5. `/hyperopen/src/hyperopen/schema/contracts.cljs` derives contracted action/effect ID sets from that catalog and enforces fail-fast drift checks.
6. Runtime composition rejects duplicate handler keys across nested dependency maps to prevent shadowing ambiguity.

## Consequences

- Action/effect registration edits now have one metadata authority rather than three duplicated lists.
- Registry, composition, and contract surfaces cannot drift on ID coverage without immediate failure.
- Runtime wiring remains explicit and deterministic while reducing agent and maintainer edit fan-out.
- Public runtime registry and contract assertion entrypoints remain stable.

## Invariant Ownership

- Canonical runtime registration catalog:
  `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`
- Runtime registry binding installation:
  `/hyperopen/src/hyperopen/registry/runtime.cljs`
- Runtime registration composition:
  `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`
- Contract drift enforcement and payload validation entrypoints:
  `/hyperopen/src/hyperopen/schema/contracts.cljs`
- Coverage and wiring drift tests:
  `/hyperopen/test/hyperopen/schema/contracts_coverage_test.cljs`
  `/hyperopen/test/hyperopen/runtime/wiring_test.cljs`
  `/hyperopen/test/hyperopen/runtime/registry_composition_test.cljs`
