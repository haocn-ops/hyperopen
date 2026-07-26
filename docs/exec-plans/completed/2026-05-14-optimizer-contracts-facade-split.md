# Optimizer Contracts Facade Split

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. It is self-contained so an engineer can execute the work without relying on the conversation that produced it.

## Purpose / Big Picture

The portfolio optimizer currently exposes a useful public contract namespace at `src/hyperopen/portfolio/optimizer/contracts.cljs`, but that file also owns several unrelated implementation concerns: app-state paths, ClojureScript specs, version migrations, request signatures, and worker wire codecs. After this refactor, `contracts.cljs` remains the stable facade used by existing callers, while focused implementation namespaces make it clear where a maintainer should add a new path, persisted field, signature input, or worker wire rule.

The change is internal and behavior-preserving. A human can see it working by running the existing optimizer contracts tests, which continue to validate the facade API, then running the required repository gates.

## Context References

Public refs:

- Direct maintainer request in this session: "Split the overloaded optimizer contracts facade."

Repo artifacts:

- Parent context: `docs/exec-plans/completed/2026-05-11-optimizer-contracts-codecs.md`, which introduced the original contracts namespace and its tests.
- Governing docs: `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-14T00:56:46Z) Inspected `src/hyperopen/portfolio/optimizer/contracts.cljs`, current contract tests, the parent contracts ExecPlan, and repo planning rules.
- [x] (2026-05-14T00:56:46Z) Created this active ExecPlan before implementation.
- [x] (2026-05-14T00:59:00Z) Split the contracts internals into focused namespaces while preserving the public facade.
- [x] (2026-05-14T01:01:00Z) Ran `npm run test:runner:generate`; it generated `test/test_runner_generated.cljs` with 639 namespaces.
- [x] (2026-05-14T01:02:00Z) Ran `npx shadow-cljs --force-spawn compile test`; it completed with 1674 files, 1673 compiled, and 0 warnings.
- [x] (2026-05-14T01:03:00Z) Ran `node out/test.js`; it passed with 3893 tests, 21467 assertions, 0 failures, and 0 errors.
- [x] (2026-05-14T01:04:00Z) Ran `npm run test:optimizer-spike`; it passed with 6 tests, 0 failures, and 0 errors after updating the optimizer path guardrail.
- [x] (2026-05-14T01:04:00Z) Ran `npm run check`; it passed, including docs checks, namespace checks, optimizer path checks, style tests, release asset tests, and app/portfolio/worker/test Shadow CLJS compiles.
- [x] (2026-05-14T01:05:00Z) Ran `npm test`; it passed with 3893 tests, 21467 assertions, 0 failures, and 0 errors.
- [x] (2026-05-14T01:05:00Z) Ran `npm run test:websocket`; it passed with 524 tests, 3043 assertions, 0 failures, and 0 errors.
- [x] (2026-05-14T01:05:22Z) Ran `git diff --check`; it reported no whitespace errors.
- [x] (2026-05-14T01:05:22Z) Closed this ExecPlan after required validation passed.

## Surprises & Discoveries

- Observation: Only the optimizer contract tests currently refer directly to `::contracts/*` specs and `contracts/contract-specs`.
  Evidence: `rg "contract-specs|::contracts/(draft|engine-request|request-signature|scenario-record|tracking|result-payload|worker-envelope)" src test -g'*.cljs' -g'*.cljc' -g'*.clj'` returns the facade namespace and `test/hyperopen/portfolio/optimizer/contracts_test.cljs`.

- Observation: The first `node out/test.js` attempt failed before test execution because the worktree did not have installed JavaScript dependencies.
  Evidence: Node reported `Cannot find module 'lucide/dist/esm/icons/external-link.js'`, `npm ls lucide --depth=0` returned `(empty)`, and `require.resolve('lucide/package.json')` failed. Running `npm ci` installed the lockfile dependencies, after which the same `node out/test.js` command passed.

- Observation: The optimizer path guardrail was scoped to the old monolithic facade file.
  Evidence: `npm run check` failed in `tools/optimizer/check-contract-paths.test.mjs` with two violations from `src/hyperopen/portfolio/optimizer/contracts/paths.cljs`, the new canonical path owner.

## Decision Log

- Decision: Keep `hyperopen.portfolio.optimizer.contracts` as the only public namespace used by existing production callers and re-export every existing public var from it.
  Rationale: The maintainer asked for a stable facade. Avoiding caller churn makes this a structural refactor rather than a behavioral migration.
  Date/Author: 2026-05-14 / Codex

- Decision: Put request signature versioning in `contracts.signatures`, persisted record versioning in `contracts.migrations`, result payload status/version specs in `contracts.specs`, and worker wire versioning in `contracts.worker-wire`.
  Rationale: This keeps dependencies acyclic: specs can validate request signatures by calling the signatures namespace, signatures does not need specs, and migrations can be used by specs and the facade without depending on either.
  Date/Author: 2026-05-14 / Codex

- Decision: Update `tools/optimizer/check-contract-paths.mjs` to allow only `src/hyperopen/portfolio/optimizer/contracts/paths.cljs` in addition to the facade.
  Rationale: The guardrail should continue rejecting hardcoded optimizer root paths outside the contract layer, while recognizing that root path ownership moved from the facade body into the dedicated paths namespace.
  Date/Author: 2026-05-14 / Codex

## Outcomes & Retrospective

Complete. The implementation split the original contracts facade into focused implementation namespaces: `contracts.paths` owns optimizer app-state and UI paths, `contracts.migrations` owns persisted schema versions and migration entry points, `contracts.signatures` owns request signature construction, `contracts.specs` owns `clojure.spec.alpha` shapes and lifecycle status sets, and `contracts.worker-wire` owns worker boundary codec aliases. The public `hyperopen.portfolio.optimizer.contracts` namespace remains the stable facade and re-exports the existing public constants and functions. It also defines facade-qualified spec aliases so existing `s/valid? ::contracts/draft` and similar calls continue to work.

This reduced overall complexity by turning one 427-line mixed-responsibility file into a 106-line facade plus smaller implementation namespaces with clear ownership. The only guardrail change was to let the existing optimizer path checker recognize `contracts/paths.cljs` as a canonical contract-layer path owner while continuing to reject hardcoded optimizer root paths elsewhere.

## Context and Orientation

The target bounded context lives under `src/hyperopen/portfolio/optimizer/`. The current facade file is `src/hyperopen/portfolio/optimizer/contracts.cljs`. It defines path constants used throughout optimizer actions, runtime adapters, and view-model code; `clojure.spec.alpha` specs used by `test/hyperopen/portfolio/optimizer/contracts_test.cljs`; migration functions for drafts, saved scenario records, and tracking records; request signature helpers used by `src/hyperopen/portfolio/optimizer/application/run_identity.cljs`; and worker wire aliases used by `src/hyperopen/portfolio/optimizer/infrastructure/wire.cljs`.

The new implementation files should live in `src/hyperopen/portfolio/optimizer/contracts/`. ClojureScript namespace names use hyphens while their file names use underscores, so the worker wire namespace will be `hyperopen.portfolio.optimizer.contracts.worker-wire` in `contracts/worker_wire.cljs`.

## Plan of Work

First, create `contracts/paths.cljs` and move all optimizer app-state and UI path constants into it. The facade will re-export the same names so existing callers using `contracts/draft-path` and related vars do not change.

Second, create `contracts/migrations.cljs` and move draft, scenario record, and tracking record schema version constants plus `migrate-draft`, `migrate-scenario-record`, `migrate-tracking-record`, and `migrate-contract` into it.

Third, create `contracts/signatures.cljs` and move `request-signature-schema-version`, `optimizer-input-keys`, `optimizer-input-signature`, and `build-request-signature` into it.

Fourth, create `contracts/worker_wire.cljs` and move `worker-wire-schema-version` plus the worker wire codec aliases into it.

Fifth, create `contracts/specs.cljs` and move helper predicates, lifecycle status sets, `result-payload-schema-version`, `s/def` declarations, and an internal `contract-specs` map into it. The facade must define facade-qualified spec aliases such as `::contracts/draft` to preserve existing external `s/valid?` calls.

Finally, replace `contracts.cljs` with a facade that requires the focused namespaces, declares facade-qualified spec aliases, exposes `contract-specs` with facade-qualified keywords, and re-exports the public constants and functions already used by production and tests.

## Concrete Steps

Run commands from `/Users/barry/.codex/worktrees/34f1/hyperopen`.

After editing, run:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js

Then run the required gates:

    npm run check
    npm test
    npm run test:websocket

If a broad gate fails outside optimizer contracts, inspect whether this refactor caused the failure before changing unrelated code.

## Validation and Acceptance

Acceptance requires all existing optimizer contract tests to pass with the public facade API unchanged. In particular, `s/valid? ::contracts/draft`, `s/valid? ::contracts/request-signature`, `contracts/contract-specs`, all path constants, migration functions, signature helpers, and worker wire helpers must still be available from `hyperopen.portfolio.optimizer.contracts`.

The required gates are `npm run check`, `npm test`, and `npm run test:websocket`. No browser QA is required because this refactor does not touch UI code or browser interaction behavior.

## Idempotence and Recovery

This refactor is additive and mechanical. It does not migrate persisted data or change runtime side effects. If a compile or test failure appears, compare the failing public var or spec keyword with the original facade API before changing callers. If necessary, the focused namespaces can stay in place while the facade is adjusted to restore compatibility.

## Artifacts and Notes

Important source evidence from the initial inspection:

    src/hyperopen/portfolio/optimizer/contracts.cljs currently requires clojure.spec.alpha, coercion, and instrument-keyed-codec in one namespace.
    The file then defines path constants, specs, migrations, request signatures, and worker wire aliases.

Validation evidence so far:

    npm run test:runner:generate
    Generated test/test_runner_generated.cljs with 639 namespaces.

    npx shadow-cljs --force-spawn compile test
    [:test] Build completed. (1674 files, 1673 compiled, 0 warnings, 24.10s)

    node out/test.js
    Ran 3893 tests containing 21467 assertions.
    0 failures, 0 errors.

Final validation evidence:

    npm run test:optimizer-spike
    tests 6
    pass 6
    fail 0

    npm run check
    [:test] Build completed. (1674 files, 1673 compiled, 0 warnings, 21.45s)

    npm test
    Ran 3893 tests containing 21467 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

    git diff --check
    No output; exit code 0.

## Interfaces and Dependencies

At the end of this work, these implementation namespaces must exist:

- `hyperopen.portfolio.optimizer.contracts.paths`
- `hyperopen.portfolio.optimizer.contracts.specs`
- `hyperopen.portfolio.optimizer.contracts.migrations`
- `hyperopen.portfolio.optimizer.contracts.signatures`
- `hyperopen.portfolio.optimizer.contracts.worker-wire`

The existing public facade namespace `hyperopen.portfolio.optimizer.contracts` must continue to expose the public vars and facade-qualified spec names that existed before this refactor.

## Plan Revision Notes

- 2026-05-14 / Codex: Initial active ExecPlan created after inspecting the current contracts facade, tests, and parent contract-layer plan.
- 2026-05-14 / Codex: Updated progress, discovery notes, and validation artifacts after splitting the contracts namespace and running generated CLJS tests.
- 2026-05-14 / Codex: Recorded optimizer path guardrail discovery and decision after the first `npm run check` attempt.
- 2026-05-14 / Codex: Added final validation evidence and completion retrospective before moving the plan to completed.
