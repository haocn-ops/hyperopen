# Migrate The Lean Formal Workspace Under `spec/lean`

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The primary live `bd` issue for this work is `hyperopen-j3ml`.

## Purpose / Big Picture

Hyperopen already had machine-checked Lean proof surfaces for vault transfer, order requests, trading submit policy, the runtime effect-order contract, and narrow order-form ownership. Before this refactor, those proof models lived under `/hyperopen/tools/formal/lean/**` while the websocket TLA+ model already lived under `/hyperopen/spec/tla/**`. That split made the repository harder to navigate than it needed to be because proof sources were divided between `tools/**` and `spec/**`.

This migration standardizes the repository contract so all checked-in formal model source code now lives under `/hyperopen/spec/**`: TLA+ in `/hyperopen/spec/tla/**` and Lean in `/hyperopen/spec/lean/**`. The wrapper and generated artifacts stay where they were before. Contributors still run the same `npm run formal:verify` and `npm run formal:sync` commands, get the same generated manifests and checked-in vector bridges, and rely on the same repository gates. The gain is organizational clarity, not changed product behavior.

## Progress

- [x] (2026-03-28 20:34 EDT) Created and claimed `hyperopen-j3ml` for the Lean workspace path-standardization refactor.
- [x] (2026-03-28 20:40 EDT) Audited the current path coupling in `/hyperopen/tools/formal/core.clj`, `/hyperopen/tools/formal/lean/**`, `/hyperopen/dev/formal_tooling_test.clj`, `/hyperopen/tools/formal/README.md`, `/hyperopen/docs/tools.md`, and the completed formal ExecPlans.
- [x] (2026-03-28 20:46 EDT) Drafted this active ExecPlan and froze scope to the Lean workspace migration only. Surface ids, command names, generated-vector schema, and proof semantics are explicitly out of scope.
- [x] (2026-03-28 20:57 EDT) Captured the formal baseline from the original `/hyperopen/tools/formal/lean` workspace: `npm run test:formal-tooling` passed and all six `npm run formal:verify -- --surface ...` commands passed.
- [x] (2026-03-28 21:03 EDT) Captured the repository-gate baseline blocker: `npm test`, `npm run test:websocket`, and `npm run check` could not complete because `node_modules` was absent in the local environment.
- [x] (2026-03-28 21:07 EDT) Restored the local Node toolchain with `npm ci`, which installed the missing package tree from the checked-in lockfile.
- [x] (2026-03-28 21:14 EDT) Re-ran the full pre-migration baseline after `npm ci`: `npm test`, `npm run test:websocket`, and `npm run check` all passed from the original Lean root.
- [x] (2026-03-28) Moved the Lean workspace from `/hyperopen/tools/formal/lean/**` to `/hyperopen/spec/lean/**` with history-preserving git renames.
- [x] (2026-03-28) Switched `/hyperopen/tools/formal/core.clj` to launch `lake build` and `lake exe formal` from `/hyperopen/spec/lean`.
- [x] (2026-03-28) Repaired workspace-relative output paths in `/hyperopen/spec/lean/Hyperopen/Formal/Common.lean` so manifests still land under `/hyperopen/tools/formal/generated/**` and transient generated source still lands under `/hyperopen/target/formal/**`.
- [x] (2026-03-28) Extended `/hyperopen/dev/formal_tooling_test.clj` so the fast wrapper tests pin `/hyperopen/spec/lean` as the authoritative Lean root.
- [x] (2026-03-28) Updated `/hyperopen/tools/formal/README.md`, `/hyperopen/docs/tools.md`, and added `/hyperopen/spec/README.md` to document the new formal-model layout.
- [x] (2026-03-28) Added comment-only compatibility stubs under `/hyperopen/tools/formal/lean/**` so historical ExecPlan links still resolve without keeping the old workspace live.
- [x] (2026-03-28) Smoke-tested the moved workspace with `cd /Users/barry/.codex/worktrees/417a/hyperopen/spec/lean && lake build`, then verified all six formal surfaces from the new root.
- [x] (2026-03-28) Ran a controlled `npm run formal:sync -- --surface vault-transfer` plus re-`verify` cycle and confirmed there was no generated-artifact churn beyond the intended migration edits.
- [x] (2026-03-28) Removed the temporary wrapper fallback idea from the final implementation so `/hyperopen/spec/lean` is the only live Lean workspace root.
- [x] (2026-03-28) Completed the required validation suite: `npm run test:formal-tooling`, all six `formal:verify` surfaces, `npm run test:tla-tooling`, `npm run lint:docs`, `npm test`, `npm run test:websocket`, and `npm run check`.

## Surprises & Discoveries

- Observation: the highest-risk part of the migration was the Lean common helper, not the wrapper entrypoint.
  Evidence: `/hyperopen/spec/lean/Hyperopen/Formal/Common.lean` writes manifests and transient generated CLJS using workspace-relative paths. Those paths had to change from the old workspace-relative layout to `../../tools/formal/generated/**` and `../../target/formal/**`.

- Observation: the wrapper migration itself was structurally simple once the path-writing helper was repaired.
  Evidence: `/hyperopen/tools/formal/core.clj` now computes `lean-root` as `/hyperopen/spec/lean` and the focused wrapper tests prove both `lake build` and `lake exe formal` run from that directory.

- Observation: the repository already treated proof source, wrapper code, generated manifests, transient output, and checked-in CLJS bridges as distinct concerns before this migration.
  Evidence: manifests already lived under `/hyperopen/tools/formal/generated/**`, transient generated sources already lived under `/hyperopen/target/formal/**`, and checked-in bridges already lived under `/hyperopen/test/hyperopen/formal/**`; only the Lean source tree needed to move.

- Observation: the local environment initially looked like it had repo-gate regressions, but the blocker was only a missing dependency tree.
  Evidence: `npm test`, `npm run test:websocket`, and `npm run check` failed before the migration because `node_modules` was absent. `npm ci` restored the expected baseline from the checked-in lockfile, after which those gates passed both before and after the move.

- Observation: comment-only compatibility stubs were sufficient to preserve historical deep-link utility without weakening the final workspace contract.
  Evidence: completed ExecPlans still deep-link into `/hyperopen/tools/formal/lean/**`, but those paths now resolve to explanatory stub files while all live tooling points only at `/hyperopen/spec/lean/**`.

- Observation: the TLA tooling baseline was verifiable locally, but full TLC execution remained environment-dependent.
  Evidence: `npm run test:tla-tooling` passed, but actual `npm run tla:verify -- --spec ...` runs were not executed because neither `TLA2TOOLS_JAR` nor `/hyperopen/tools/tla/vendor/tla2tools.jar` was available in the local environment.

## Decision Log

- Decision: treat this as a real workspace migration, not a cosmetic alias or documentation-only change.
  Rationale: the stated goal was to standardize all formal models under `/hyperopen/spec/**`, not merely to make the existing location easier to discover.
  Date/Author: 2026-03-28 / Codex

- Decision: keep the repo-local wrapper under `/hyperopen/tools/formal/**` and move only the Lean workspace itself.
  Rationale: the wrapper is operational tooling, not proof source. The repository already separates TLA+ specs in `/hyperopen/spec/tla/**` from the TLA+ wrapper in `/hyperopen/tools/tla.clj`, so the Lean path should follow the same pattern.
  Date/Author: 2026-03-28 / Codex

- Decision: keep manifests under `/hyperopen/tools/formal/generated/**` and checked-in CLJS bridges under `/hyperopen/test/hyperopen/formal/**`.
  Rationale: this refactor is about where proof source lives, not about relocating generated artifacts or widening the runtime/test contract.
  Date/Author: 2026-03-28 / Codex

- Decision: do not rewrite historical completed ExecPlans.
  Rationale: completed plans are historical records. Rewriting old path references would create noise and blur the implementation history. Historical deep-link utility is preserved through compatibility stubs instead.
  Date/Author: 2026-03-28 / Codex

- Decision: use comment-only compatibility stubs under `/hyperopen/tools/formal/lean/**` rather than symlinks or permanent dual-root support.
  Rationale: stubs keep historical links useful while making it impossible for live tooling to silently keep using the old workspace.
  Date/Author: 2026-03-28 / Codex

- Decision: allow a temporary fallback plan during implementation, but remove it from the final code once validation is green.
  Rationale: a short-lived fallback reduced cutover risk during the move, but permanent dual-root support would weaken the standardization goal and hide regressions. The final committed behavior uses `/hyperopen/spec/lean` only.
  Date/Author: 2026-03-28 / Codex

- Decision: treat `npm ci` as required environment repair before final migration validation, but do not widen the migration scope to package-version changes.
  Rationale: the missing dependency tree was a local toolchain blocker, not part of the Lean workspace refactor. Installing from the checked-in lockfile restored the repo to the state expected by the validation commands without changing the migration contract.
  Date/Author: 2026-03-28 / Codex

## Outcomes & Retrospective

The migration landed successfully. The live Lean workspace now lives under `/hyperopen/spec/lean/**`, while `/hyperopen/tools/formal.clj` and `/hyperopen/tools/formal/core.clj` remain the stable wrapper entrypoints. Generated manifests still land under `/hyperopen/tools/formal/generated/**`, transient generated artifacts still land under `/hyperopen/target/formal/**`, and checked-in CLJS vector bridges still live under `/hyperopen/test/hyperopen/formal/**`.

The most important implementation outcome is that behavior stayed constant while the repository contract became clearer. All six formal surfaces verified from the new workspace root, the controlled `formal:sync` plus re-`verify` cycle showed no unintended generated churn, and the normal repository gates stayed green after the move. The main limitation is that full TLC runs were not part of the final validation because the local environment did not contain a usable `tla2tools.jar`; only the Babashka TLA tooling tests were run.

The compatibility-stub approach also worked as intended. Historical completed plans still have resolvable file targets, but there is no longer any ambiguity about the live proof workspace location because the wrapper, docs, tests, and grep audits all point at `/hyperopen/spec/lean/**`.

## Context and Orientation

A “Lean workspace” in this repository is the self-contained Lean 4 project that `lake` builds. After this migration, that workspace lives under `/hyperopen/spec/lean`. Its root files are `/hyperopen/spec/lean/lakefile.toml`, `/hyperopen/spec/lean/lean-toolchain`, and `/hyperopen/spec/lean/Main.lean`. The proof modules live under `/hyperopen/spec/lean/Hyperopen/Formal/**`.

A “formal wrapper” is the Babashka command surface that contributors run through `npm`. In this repository, that wrapper still lives at `/hyperopen/tools/formal.clj` and `/hyperopen/tools/formal/core.clj`. It parses surface ids such as `vault-transfer` or `trading-submit-policy`, ensures Lean is installed, runs `lake build`, runs `lake exe formal`, and then checks generated artifacts.

A “generated manifest” is the small `.edn` file under `/hyperopen/tools/formal/generated/**` that records which Lean module and status belong to a surface. A “generated bridge” is the checked-in ClojureScript namespace under `/hyperopen/test/hyperopen/formal/**` that contains vectors exported from Lean and consumed by ordinary CLJS tests. A “transient generated source” is the temporary CLJS file written under `/hyperopen/target/formal/**` during `formal:verify` and `formal:sync`.

This migration deliberately did not change any of those contracts. The wrapper in `/hyperopen/tools/formal/core.clj` still exposes the same surface ids, still verifies the same manifests under `/hyperopen/tools/formal/generated/**`, and still compares or copies the same checked-in vector namespaces under `/hyperopen/test/hyperopen/formal/**`.

The live path-sensitive files after the migration are:

- `/hyperopen/tools/formal/core.clj`, which computes `lean-root` as `/hyperopen/spec/lean` and launches `lake`.
- `/hyperopen/spec/lean/lakefile.toml`, which defines the Lean package.
- `/hyperopen/spec/lean/Main.lean`, which is the Lean executable entrypoint.
- `/hyperopen/spec/lean/Hyperopen/Formal.lean`, which routes `verify` and `sync`.
- `/hyperopen/spec/lean/Hyperopen/Formal/Common.lean`, which defines surface metadata and writes manifests and generated sources using new workspace-relative paths.
- `/hyperopen/dev/formal_tooling_test.clj`, which now pins the new workspace root in the fast wrapper regression tests.
- `/hyperopen/tools/formal/README.md`, `/hyperopen/docs/tools.md`, and `/hyperopen/spec/README.md`, which document the new repository contract.

The old `/hyperopen/tools/formal/lean/**` tree is no longer a live workspace. It now contains comment-only redirect stubs so historical completed ExecPlans continue to resolve to useful locations without mutating archival documents.

The main non-goals remained intact throughout the migration. This work did not rename any Lean modules. It did not move generated manifests. It did not move transient outputs. It did not move checked-in vector namespaces. It did not change proof theorems, proof coverage, CLJS conformance logic, or product code under `/hyperopen/src/**`.

## Execution Summary

The work started with a baseline capture from the original `/hyperopen/tools/formal/lean` root. `npm run test:formal-tooling` passed, all six `npm run formal:verify -- --surface ...` commands passed, and then the broader repository gates were repaired and re-run after `npm ci` restored the missing local dependency tree.

The Lean workspace was then moved to `/hyperopen/spec/lean` with history-preserving git renames. The wrapper root in `/hyperopen/tools/formal/core.clj` was updated to point only at the new workspace, and `/hyperopen/spec/lean/Hyperopen/Formal/Common.lean` was repaired so manifests still write to `/hyperopen/tools/formal/generated/**` and transient generated sources still write to `/hyperopen/target/formal/**`.

Once the core path changes were in place, the fast wrapper test suite in `/hyperopen/dev/formal_tooling_test.clj` was extended so it explicitly proves that `lake build` and `lake exe formal` run from `/hyperopen/spec/lean`. The docs were updated to reflect the new contract, `/hyperopen/spec/README.md` was added to make the proof-source split under `/hyperopen/spec/**` explicit, and comment-only stubs were added under `/hyperopen/tools/formal/lean/**` for historical deep links.

Validation then proceeded in layers. The moved workspace passed `lake build` directly, then one focused `formal:verify` surface, then all six current surfaces, then a controlled `formal:sync -- --surface vault-transfer` plus re-`verify` cycle. After that, the temporary fallback idea was removed from the implementation so the new workspace root became authoritative, and the broader repo gates were rerun.

## Concrete Validation Record

The following commands passed during the migration from `/Users/barry/.codex/worktrees/417a/hyperopen` unless otherwise noted:

    npm ci
    npm run test:formal-tooling
    export PATH="$HOME/.elan/bin:$PATH"
    npm run formal:verify -- --surface vault-transfer
    npm run formal:verify -- --surface order-request-standard
    npm run formal:verify -- --surface order-request-advanced
    npm run formal:verify -- --surface effect-order-contract
    npm run formal:verify -- --surface trading-submit-policy
    npm run formal:verify -- --surface order-form-ownership
    cd /Users/barry/.codex/worktrees/417a/hyperopen/spec/lean && lake build
    cd /Users/barry/.codex/worktrees/417a/hyperopen
    npm run formal:sync -- --surface vault-transfer
    npm run formal:verify -- --surface vault-transfer
    npm run test:tla-tooling
    npm run lint:docs
    npm test
    npm run test:websocket
    npm run check

Migration-completeness audits also passed:

    rg -n "tools/formal/lean" docs tools dev package.json .agents AGENTS.md -g '!docs/exec-plans/completed/**'
    rg -n "spec/lean" docs tools dev package.json .agents AGENTS.md
    rg -n "\\.\\./generated|\\.\\./\\.\\./\\.\\./target/formal" spec/lean -S

The first grep returned only intentional historical notes, redirect stubs, and explicit regression tests that model the historical path. The second grep showed the new live convention. The third grep showed no stale old-root relative paths in the moved Lean sources.

Two validation constraints should be recorded explicitly:

- `npm run test:tla-tooling` passed, which verifies the Babashka wrapper behavior for the websocket TLA+ workflow.
- Full TLC runs through `npm run tla:verify -- --spec websocket-runtime` and `npm run tla:verify -- --spec websocket-runtime-liveness` were not executed because neither `TLA2TOOLS_JAR` nor `/hyperopen/tools/tla/vendor/tla2tools.jar` was available in the local environment.

## Validation and Acceptance

Acceptance for this refactor was that the formal workflow continued to behave identically while the proof-source contract became uniform across formal backends. That acceptance bar is now met.

The final state is:

- `/hyperopen/spec/lean/**` is the only live Lean workspace used by the wrapper.
- `/hyperopen/tools/formal/core.clj` launches `lake build` and `lake exe formal` from `/hyperopen/spec/lean`.
- `/hyperopen/spec/lean/Hyperopen/Formal/Common.lean` writes manifests to `/hyperopen/tools/formal/generated/**` and transient generated sources to `/hyperopen/target/formal/**`.
- `npm run test:formal-tooling` passes with explicit coverage for the new workspace root.
- All six current `formal:verify` surfaces pass after the move.
- A controlled `formal:sync` plus re-`formal:verify` cycle passed with no unintended generated churn.
- `npm run test:tla-tooling`, `npm run lint:docs`, `npm test`, `npm run test:websocket`, and `npm run check` all passed.
- Live documentation points to `/hyperopen/spec/lean`, not `/hyperopen/tools/formal/lean`.
- Historical completed-plan references remain useful through comment-only compatibility stubs instead of archival rewrites.

## Idempotence and Recovery

This migration remains safe to retry because it changed repository layout and path references, not product semantics. The validation commands above can be rerun as long as the worktree remains coherent.

The key recovery rule after landing is simpler than during implementation because there is no committed dual-root fallback anymore. If the formal wrapper regresses, recovery means restoring the moved workspace, wrapper root, and Lean-side relative path changes together in one revert, not reviving permanent support for both roots.

If future work touches `/hyperopen/spec/lean/Hyperopen/Formal/Common.lean`, re-run the direct `lake build`, at least one focused `formal:verify`, and then the broader formal validation set before running `formal:sync`. That file remains the most path-sensitive part of the workflow because it writes manifests and transient generated sources using workspace-relative paths.

## Artifacts and Notes

The key live implementation paths for this migration are:

    /hyperopen/tools/formal/core.clj
      - computes `lean-root` as `/hyperopen/spec/lean`
      - runs `lake build`
      - runs `lake exe formal`

    /hyperopen/spec/lean/Hyperopen/Formal/Common.lean
      - writes manifests to `../../tools/formal/generated/...`
      - writes transient CLJS to `../../target/formal/...`

    /hyperopen/tools/formal/README.md
      - documents `spec/lean/` as the live Lean workspace

    /hyperopen/docs/tools.md
      - documents the same live workspace root for current users

    /hyperopen/spec/README.md
      - documents `/hyperopen/spec/**` as the repository home for checked-in formal model sources

The key historical-compatibility paths are:

    /hyperopen/tools/formal/lean/README.md
    /hyperopen/tools/formal/lean/Main.lean
    /hyperopen/tools/formal/lean/Hyperopen/Formal.lean
    /hyperopen/tools/formal/lean/Hyperopen/Formal/Common.lean
    /hyperopen/tools/formal/lean/Hyperopen/Formal/VaultTransfer.lean
    /hyperopen/tools/formal/lean/Hyperopen/Formal/TradingSubmitPolicy.lean
    /hyperopen/tools/formal/lean/Hyperopen/Formal/EffectOrderContract.lean
    /hyperopen/tools/formal/lean/Hyperopen/Formal/OrderFormOwnership.lean
    /hyperopen/tools/formal/lean/Hyperopen/Formal/OrderRequest/Common.lean
    /hyperopen/tools/formal/lean/Hyperopen/Formal/OrderRequest/Standard.lean
    /hyperopen/tools/formal/lean/Hyperopen/Formal/OrderRequest/Advanced.lean

Those files are comment-only redirect stubs. They are present only so historical completed ExecPlans continue to open something useful after the workspace move.

Plan update note (2026-03-28): The initial pre-migration draft was replaced with an implementation record once the workspace move, path repair, docs updates, compatibility stubs, and validation suite were complete.
