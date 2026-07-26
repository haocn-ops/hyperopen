owner: platform
status: completed
source_of_truth: true
tracked_issue: hyperopen-flha

# Optimizer Contracts And Codecs ExecPlan

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current while the work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. It is self-contained so an engineer can execute the work without relying on the conversation that produced it.

Tracked issue: `hyperopen-flha` ("Create explicit optimizer contracts and codecs").

## Purpose / Big Picture

The Portfolio Optimizer currently passes plain ClojureScript maps through draft state, request building, worker messages, saved scenario records, tracking records, and result payloads. Those maps work, but their expected keys are scattered across `defaults.cljs`, `request_builder.cljs`, `run_identity.cljs`, `wire.cljs`, and `scenario_records.cljs`. After this change, there will be a named `hyperopen.portfolio.optimizer.contracts` layer that documents and enforces the important shapes in one place. A developer should be able to find optimizer state paths, schema versions, version migrations, request-signature logic, and worker wire codecs without reverse-engineering every consumer.

The change is internal and behavior-preserving. A human can see it working by running the new focused ClojureScript tests that fail before the contracts layer exists and pass after the existing namespaces delegate to it. The full repo gates must also pass before the work is closed.

## Progress

- [x] (2026-05-11T19:19:04Z) Created tracked issue `hyperopen-flha` for this refactor.
- [x] (2026-05-11T19:19:09Z) Claimed `hyperopen-flha`.
- [x] (2026-05-11T19:21:06Z) Inspected the governing ExecPlan docs, optimizer boundary doc, target source files, and existing request/wire/scenario/tracking tests.
- [x] (2026-05-11T19:21:06Z) Created this active ExecPlan before implementation.
- [x] (2026-05-11T19:23:05Z) Wrote RED tests in `test/hyperopen/portfolio/optimizer/contracts_test.cljs`; `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test` failed because `hyperopen.portfolio.optimizer.contracts` does not exist.
- [x] (2026-05-11T19:28:33Z) Implemented `src/hyperopen/portfolio/optimizer/contracts.cljs`, delegated existing request identity and wire APIs to it, stamped saved scenarios and tracking records with contract versions, and updated defaults/request building to use contract constants/migrations.
- [x] (2026-05-11T19:28:33Z) Ran `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`; it passed with 3815 tests, 21027 assertions, 0 failures, 0 errors.
- [x] (2026-05-11T19:28:33Z) Refactored defaults, request identity, wire, scenario records, and tracking to use contracts without changing existing public API names.
- [x] (2026-05-11T19:31:02Z) Ran `npm run check`; it passed, including docs guardrails, namespace checks, tooling tests, app/portfolio/worker compiles, and test compile.
- [x] (2026-05-11T19:31:02Z) Ran `npm test`; it passed with 3815 tests, 21027 assertions, 0 failures, 0 errors.
- [x] (2026-05-11T19:31:02Z) Ran `npm run test:websocket`; it passed with 524 tests, 3043 assertions, 0 failures, 0 errors.
- [x] (2026-05-11T19:31:02Z) Moved this plan to `docs/exec-plans/completed/` after acceptance passed.
- [x] (2026-05-11T19:32:49Z) Corrected completed-plan metadata and ran `npm run lint:docs`; docs check passed.

## Surprises & Discoveries

- Observation: `defaults.cljs` is already the practical draft-state source of truth, but it exposes only default constructors. It does not name paths or schema versions beyond inline `:schema-version 1`.
  Evidence: `src/hyperopen/portfolio/optimizer/defaults.cljs` returns default maps directly, including draft `:schema-version 1`, run-state `:request-signature`, scenario lifecycle states, tracking state, and UI defaults.

- Observation: Request shape conversion is currently local to request building, while request-signature construction is separate and manually repeats the key set that should define optimizer inputs.
  Evidence: `request_builder.cljs` rewrites draft constraint keys like `:gross-max` to engine keys like `:gross-leverage`; `run_identity.cljs` separately defines `optimizer-input-keys` and volatile-field stripping.

- Observation: Worker wire normalization already behaves like a codec but is not named as one and owns path constants locally.
  Evidence: `wire.cljs` defines `enum-value-keys`, `instrument-keyed-map-paths`, `normalize-worker-boundary`, and Black-Litterman view weight normalization.

- Observation: Saved scenario and tracking records need versioned migration entry points even if the current version remains `1`.
  Evidence: `scenario_records.cljs` stamps saved scenarios with `:schema-version 1`, while `tracking.cljs` builds records without an explicit schema version.

- Observation: The RED test is wired into the generated CLJS runner before production code exists.
  Evidence: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test` generated 616 namespaces and failed with `The required namespace "hyperopen.portfolio.optimizer.contracts" is not available`.

- Observation: Hydrating older persisted scenario and tracking payloads should not stamp schema versions into runtime state as a side effect.
  Evidence: The first green run broke existing exact hydration assertions in `portfolio_optimizer_test.cljs` and `portfolio_optimizer_tracking_test.cljs` when load-state helpers migrated records before storing them. The fix keeps migrations explicit at the contract/scenario-record mutation boundary while preserving loaded payload state.

## Decision Log

- Decision: Implement a single additive `hyperopen.portfolio.optimizer.contracts` namespace first, then turn existing namespaces into callers of that contract layer instead of changing their public function names.
  Rationale: The optimizer has many existing tests and UI/runtime callers. Keeping `defaults/default-draft`, `request-builder/build-engine-request`, `run-identity/build-request-signature`, `wire/normalize-worker-boundary`, and `scenario-records/*` public APIs stable reduces blast radius while centralizing the implicit shapes.
  Date/Author: 2026-05-11 / Codex

- Decision: Use `clojure.spec.alpha` for lightweight named specs and keep runtime behavior permissive.
  Rationale: Existing optimizer data is broad and evolving. Specs should document and test the required keys for the contract boundaries without rejecting unrelated extension keys that existing views and adapters may carry.
  Date/Author: 2026-05-11 / Codex

- Decision: Add current-version migrations that normalize missing versions to version `1` rather than attempting a data rewrite.
  Rationale: There is no known older persisted optimizer format to transform. The contract should still expose a versioned migration seam so future saved scenarios, tracking records, and signatures have one canonical upgrade path.
  Date/Author: 2026-05-11 / Codex

- Decision: Do not migrate loaded legacy scenario/tracking records inside scenario hydration helpers.
  Rationale: Loading should preserve the persisted payload shape for runtime compatibility. New saves and tracking appends write explicit versions, while `contracts/migrate-*` remains available for callers that need an upgraded record.
  Date/Author: 2026-05-11 / Codex

## Outcomes & Retrospective

Complete. The implementation adds `src/hyperopen/portfolio/optimizer/contracts.cljs` as the named optimizer contract layer and moves the canonical request-signature and wire-codec behavior there. Existing public namespaces now delegate instead of owning parallel key lists: `run_identity.cljs` delegates signatures, `wire.cljs` delegates codec behavior, `defaults.cljs` uses schema-version constants, `request_builder.cljs` migrates incoming drafts before request building, `scenario_records.cljs` stamps/migrates saved scenario records, and `tracking.cljs` writes versioned tracking records.

The change reduces overall complexity by replacing duplicated hidden key lists with named contracts and constants while preserving public API names. The only deliberate compatibility choice is that scenario load hydration preserves legacy loaded payloads instead of stamping versions into runtime state; new saves/appends and explicit migration calls produce versioned records.

Validation passed on 2026-05-11: `npm run check`; `npm test` with 3815 tests and 21027 assertions; `npm run test:websocket` with 524 tests and 3043 assertions; and a final `npm run lint:docs` after moving the ExecPlan to completed. No browser QA was required because no UI code or browser interaction flow changed.

## Context And Orientation

The relevant bounded context lives under `src/hyperopen/portfolio/optimizer/`. The optimizer boundary document at `src/hyperopen/portfolio/optimizer/BOUNDARY.md` says this context owns draft state, scenario lifecycle, current portfolio snapshots, optimization request/result contracts, diagnostics, rebalance preview shaping, execution orchestration, local scenario persistence, worker communication, and route query state.

The target files are:

- `src/hyperopen/portfolio/optimizer/defaults.cljs`, which creates default draft, run, history, scenario, execution, tracking, optimizer, and optimizer UI states.
- `src/hyperopen/portfolio/optimizer/application/request_builder.cljs`, which converts a draft plus current portfolio and history data into the engine request map consumed by the optimizer engine and worker.
- `src/hyperopen/portfolio/optimizer/application/run_identity.cljs`, which decides whether a solved run still matches the current optimizer inputs.
- `src/hyperopen/portfolio/optimizer/infrastructure/wire.cljs`, which normalizes messages crossing the browser worker boundary by keywordizing enums and stringifying instrument-id keyed maps.
- `src/hyperopen/portfolio/optimizer/application/scenario_records.cljs`, which builds and mutates saved scenario records.
- `src/hyperopen/portfolio/optimizer/application/tracking.cljs`, which builds tracking snapshots and tracking records.

The new contract file will live at `src/hyperopen/portfolio/optimizer/contracts.cljs`. "Contract" means a small, named description of a data boundary: schema version constants, `clojure.spec.alpha` specs for important map shapes, state path constants, migration functions, canonical request-signature helpers, and wire codec helpers.

## Plan Of Work

First, add focused tests in `test/hyperopen/portfolio/optimizer/contracts_test.cljs`. The tests should require `hyperopen.portfolio.optimizer.contracts` and prove these behaviors: the draft defaults and saved records carry named schema versions; state path constants resolve the expected paths; `build-request-signature` includes a stable canonical optimizer input that ignores live orderbook cost contexts and history freshness; version migration functions stamp missing draft, scenario, and tracking versions; and the wire codec normalizes enum string values and instrument-keyed maps. Run the focused contract test target and observe it fail because the namespace does not exist yet.

Second, create `src/hyperopen/portfolio/optimizer/contracts.cljs`. Define version constants such as `draft-schema-version`, `scenario-record-schema-version`, `tracking-record-schema-version`, `request-signature-schema-version`, `result-payload-schema-version`, and `worker-wire-schema-version`. Define state path constants such as `optimizer-path`, `draft-path`, `active-scenario-path`, `run-state-path`, `history-load-state-path`, `scenario-index-path`, `tracking-path`, and UI paths under `[:portfolio-ui :optimizer]`. Define named specs for draft, engine request, request signature, scenario record, tracking snapshot, tracking record, worker envelope, and result payload. Keep specs permissive but require the keys that make each map identifiable.

Third, move canonical request-signature construction into contracts. The canonical input should select only optimizer inputs that affect the solve, using the current key set from `run_identity.cljs`: `:requested-universe`, `:universe`, `:current-portfolio`, `:return-model`, `:risk-model`, `:objective`, `:constraints`, `:execution-assumptions`, `:history`, and `:black-litterman-prior`. It must remove volatile fields `[:execution-assumptions :cost-contexts-by-id]` and `[:history :freshness]`. `run_identity.cljs` should delegate `build-request-signature` and `optimizer-input-signature` to contracts.

Fourth, move worker wire codec definitions into contracts. The current behavior must be preserved: enum string values under keys like `:kind`, `:status`, `:source`, and `:funding-source` become keywords; instrument-keyed maps at known paths become string-keyed maps; and Black-Litterman view `:weights` maps use string instrument ids. Keep `wire.cljs` as the public infrastructure namespace and delegate to `contracts/normalize-worker-boundary`.

Fifth, migrate defaults and records to use contract constants and migration seams. `defaults.cljs` should stamp `:schema-version` values from contracts and use contract helpers where appropriate. `scenario_records.cljs` should build records with `contracts/scenario-record-schema-version`, migrate incoming records before summary/archive/duplicate/tracking status updates, and migrate draft configs before saving. `tracking.cljs` should build tracking records with `contracts/tracking-record-schema-version`.

Sixth, run focused and broad validation. Start with the smallest relevant command:

    cd /Users/barry/projects/hyperopen
    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --include hyperopen.portfolio.optimizer.contracts-test

If the generated runner does not support namespace filtering, run `node out/test.js` after compiling. Then run the required repo gates:

    cd /Users/barry/projects/hyperopen
    npm run check
    npm test
    npm run test:websocket

No browser QA is required because this refactor does not touch UI code or interaction flows.

## Validation And Acceptance

Acceptance requires all of the following:

1. `test/hyperopen/portfolio/optimizer/contracts_test.cljs` exists and fails before the production contracts namespace exists.
2. `src/hyperopen/portfolio/optimizer/contracts.cljs` exists and exposes named schema versions, path constants, specs, migration functions, canonical request signature helpers, and wire codec helpers.
3. Existing public APIs remain available: `defaults/default-draft`, `defaults/default-optimizer-state`, `request-builder/build-engine-request`, `run-identity/build-request-signature`, `run-identity/optimizer-input-signature`, `wire/normalize-worker-boundary`, and `scenario-records/build-saved-scenario-record`.
4. Request matching still ignores live orderbook cost contexts and history freshness, but rejects material model input changes.
5. Worker boundary normalization still keywordizes known enum values and stringifies instrument-id keyed maps.
6. Saved scenario records and tracking records carry explicit schema versions and can be passed through current-version migrations idempotently.
7. `npm run check`, `npm test`, and `npm run test:websocket` pass.

## Idempotence And Recovery

The refactor is additive and safe to rerun. The new contract namespace should not perform side effects. Migration functions for current version `1` must be idempotent: running them repeatedly produces the same map after the first normalization. If a focused test fails, inspect whether the failure is a behavior change or a test expectation error before broadening the validation run. If a broad gate fails outside optimizer contracts, do not revert unrelated user changes; capture the failure and decide whether it is caused by this refactor.

## Artifacts And Notes

Focused tests to run after writing RED tests:

    cd /Users/barry/projects/hyperopen
    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js

Expected RED failure before implementation: compiler error or test failure indicating that `hyperopen.portfolio.optimizer.contracts` cannot be found or the contract functions are undefined.

## Interfaces And Dependencies

In `src/hyperopen/portfolio/optimizer/contracts.cljs`, define these public functions and values:

- `draft-schema-version`, `scenario-record-schema-version`, `tracking-record-schema-version`, `request-signature-schema-version`, `result-payload-schema-version`, and `worker-wire-schema-version`.
- `optimizer-path`, `draft-path`, `active-scenario-path`, `run-state-path`, `history-load-state-path`, `history-prefetch-path`, `optimization-progress-path`, `scenario-index-path`, `scenario-save-state-path`, `scenario-load-state-path`, `scenario-index-load-state-path`, `scenario-archive-state-path`, `scenario-duplicate-state-path`, `execution-modal-path`, `execution-path`, `tracking-path`, and `optimizer-ui-path`.
- `contract-specs`, a map from contract ids such as `:optimizer/draft`, `:optimizer/engine-request`, `:optimizer/request-signature`, `:optimizer/scenario-record`, `:optimizer/tracking-record`, `:optimizer/tracking-snapshot`, `:optimizer/result-payload`, and `:optimizer/worker-envelope` to spec keywords.
- `migrate-draft`, `migrate-scenario-record`, `migrate-tracking-record`, and `migrate-contract`.
- `optimizer-input-signature` and `build-request-signature`.
- `normalize-worker-boundary`, `normalize-wire-values`, `normalize-instrument-keyed-maps`, `instrument-id-key`, `stringify-instrument-keyed-map`, `enum-value-keys`, and `instrument-keyed-map-paths`.

Use `clojure.spec.alpha` only for lightweight spec definitions. Do not introduce a new schema library.

## Plan Revision Notes

- 2026-05-11 / Codex: Initial active ExecPlan created after inspecting current optimizer contract, request, wire, scenario, and tracking surfaces. The plan chooses a behavior-preserving additive contract layer before migrating callers.
- 2026-05-11 / Codex: Added RED test evidence to Progress and Surprises before implementing the contract namespace.
- 2026-05-11 / Codex: Added implementation and first green CLJS suite evidence, plus the hydration compatibility decision discovered during the first green run.
- 2026-05-11 / Codex: Added final validation evidence and completion retrospective before moving the plan to completed.
- 2026-05-11 / Codex: Corrected completed-plan metadata and recorded the final docs guardrail run.
