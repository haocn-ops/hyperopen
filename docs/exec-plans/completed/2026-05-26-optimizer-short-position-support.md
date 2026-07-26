# First-Class Optimizer Short Position Support

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan follows `.agents/PLANS.md` from the repository root.

## Purpose / Big Picture

HyperOpen's portfolio optimizer already accepts signed target weights, but its constraint encoder currently treats `:long-only? false` as permission to short every instrument symmetrically up to the same cap used for long exposure. After this change, users can model real long/short portfolios directly: positive weights are long positions, negative weights are short positions, and zero is flat. Gross exposure is the sum of absolute weights, while net exposure is the signed sum of weights. A 130/30 or market-neutral request can produce short weights only for instruments that are explicitly shortable or conservatively known to be perps, while spot, vault, and unknown instruments stay nonnegative by default.

The working behavior is observable by running optimizer tests that encode constraints, adapt split-variable gross exposure problems, solve signed portfolios, normalize drafts, and build rebalance previews for signed deltas.

## Context References

Public refs:
- Direct user request in the Codex thread on 2026-05-26 to implement first-class short-position support in the HyperOpen portfolio optimizer.

Repo artifacts:
- `AGENTS.md` requires an ExecPlan for complex optimizer work and required gates after code changes.
- `.agents/PLANS.md` defines the living ExecPlan format.
- `docs/PLANS.md` and `docs/MULTI_AGENT.md` define planning and workflow expectations.

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-05-26T13:05Z) Read the repo workflow docs, the feature-flow skill, and the optimizer source and test files named in the request.
- [x] (2026-05-26T13:05Z) Identified the existing bug in `src/hyperopen/portfolio/optimizer/domain/constraints.cljs`: signed mode currently sets each lower bound to the negative of the same max weight used for long exposure.
- [x] (2026-05-26T13:06Z) Added focused RED-phase tests for shortability-aware bounds, cap precedence, gross/net feasibility, draft normalization, diagnostics exposure summaries, solver split-variable behavior, defaults, and rebalance signed deltas.
- [x] (2026-05-26T13:07Z) Ran `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`; after installing missing `node_modules`, the test runner produced the expected RED failures in defaults, constraints, and diagnostics.
- [x] (2026-05-26T13:11Z) Implemented shortability-aware bounds, long and short cap resolution, gross/net feasibility presolve checks, signed exposure diagnostics, and the long-only default.
- [x] (2026-05-26T13:24Z) Added final explicit regression coverage for long-only `:max-long-weight` bounds, invalid negative cap violations, and the pure exposure summary helper.
- [x] (2026-05-26T13:24Z) Ran `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`; result was 4072 tests, 22458 assertions, 0 failures, 0 errors.
- [x] (2026-05-26T13:26Z) Ran `npm run check`; exit code 0 after docs, lint, tooling tests, and Shadow CLJS compile targets completed with 0 warnings.
- [x] (2026-05-26T13:27Z) Ran `npm test`; result was 4072 tests, 22458 assertions, 0 failures, 0 errors.
- [x] (2026-05-26T13:27Z) Ran `npm run test:websocket`; result was 527 tests, 3062 assertions, 0 failures, 0 errors.

## Surprises & Discoveries

- Observation: The solver adapter already represents gross exposure as an L1 constraint using positive and negative split variables, and decodes weights as `positive - negative`.
  Evidence: `src/hyperopen/portfolio/optimizer/infrastructure/problem_adapter.cljs` expands gross exposure into split variables and `test/hyperopen/portfolio/optimizer/infrastructure/solver_adapter_test.cljs` already has signed gross exposure tests.
- Observation: Diagnostics currently compute gross and net exposure correctly, but there is no reusable pure exposure summary helper with long and short exposure fields.
  Evidence: `src/hyperopen/portfolio/optimizer/domain/diagnostics.cljs` has a private `gross-exposure` and inlines `:net-exposure`, with no `:long-exposure` or `:short-exposure` result fields.
- Observation: A fresh worktree did not have `node_modules`, so the first RED attempt compiled but could not load `lucide/dist/esm/icons/external-link.js`.
  Evidence: `node out/test.js` failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`; `test -d node_modules` reported `node_modules-missing`. Running `npm install` from the existing lockfile fixed the environment.
- Observation: The RED test run showed the intended behavior gaps.
  Evidence: failures included `default-draft-preserves-v1-model-layer-contract-test`, `encode-shortability-aware-bounds-test`, `encode-short-cap-precedence-and-backwards-compatible-max-weight-test`, `encode-shortability-overrides-metadata-and-locks-current-weight-test`, `encode-gross-and-net-feasibility-violations-test`, and `portfolio-diagnostics-reports-signed-exposure-summary-test`.
- Observation: Existing solver parity tests that used `{:instrument-id "perp:A"}` without `:instrument-type :perp` became non-shortable under the new conservative default.
  Evidence: the parity fixtures initially solved to near-zero weights because the bounds were nonnegative and net target was zero. Adding explicit `:instrument-type :perp` restored the intended shortable-instrument fixture.

## Decision Log

- Decision: Treat the user prompt as the approved implementation spec rather than creating a separate brainstorming document.
  Rationale: The prompt already freezes scope, non-goals, acceptance criteria, and validation commands. Stopping for another design approval would conflict with the direct implementation request.
  Date/Author: 2026-05-26 / Codex.
- Decision: Execute the feature-flow roles locally instead of spawning subagents.
  Rationale: The multi-agent tool discovered in this session permits spawning only when the user explicitly requests delegation. The repo workflow expectations will still be followed by keeping the ExecPlan current, writing RED tests first, implementing only after RED evidence, and performing review/validation locally.
  Date/Author: 2026-05-26 / Codex.

## Outcomes & Retrospective

The implementation now solves signed target weights directly with shortability-aware bounds, separate long and short caps, explicit gross and net exposure feasibility checks, signed exposure diagnostics, and a semantically long-only default draft. The change increases constraint-encoding complexity because it models the product semantics more accurately, but the complexity is localized to pure optimizer domain logic and covered by focused tests. Funding, borrow/carry, liquidation modeling, and execution-order splitting remain out of scope.

## Context and Orientation

The optimizer is organized by responsibility. `src/hyperopen/portfolio/optimizer/domain/constraints.cljs` filters the universe, derives lower and upper target-weight bounds, applies runtime sparse-history caps, and returns encoded constraints before solving. `src/hyperopen/portfolio/optimizer/domain/objectives.cljs` turns encoded constraints into quadratic-program problems, including equality constraints for exact net exposure, inequality constraints for net ranges, and L1 constraints for gross exposure and turnover. `src/hyperopen/portfolio/optimizer/infrastructure/problem_adapter.cljs` adapts those L1 constraints into split variables for solvers that need linear constraints. `src/hyperopen/portfolio/optimizer/application/request_builder.cljs` normalizes draft UI fields such as `:gross-max`, `:net-min`, `:asset-overrides`, and `:perp-leverage` into solver-facing constraint keys. `src/hyperopen/portfolio/optimizer/domain/diagnostics.cljs` computes portfolio diagnostics from solved weights. `src/hyperopen/portfolio/optimizer/domain/rebalance.cljs` builds signed target-minus-current trade previews. `src/hyperopen/portfolio/optimizer/defaults.cljs` defines the default optimizer draft.

In this plan, long exposure means `sum(max(weight, 0))`, short exposure means `sum(max(-weight, 0))`, gross exposure means `sum(abs(weight))`, and net exposure means `sum(weight)`. A shortable instrument is one that may receive a negative target weight. Shortability must be conservative: per-asset `:shortable?` overrides win, then instrument metadata `:shortable?`, then perps may be inferred shortable, while spot, vault, and unknown instruments are not shortable by default.

## Plan of Work

First, add tests near the existing seams. Constraint tests will cover long-only bounds, long/short bounds for shortable perps and non-shortable spot/vault/unknown instruments, per-asset long and short cap precedence, backwards-compatible `:max-weight`, locks, and presolve infeasibility. Request builder tests will prove `:shortable?`, `:max-long-weight`, and `:max-short-weight` survive draft normalization. Diagnostics tests will prove the new exposure summary returns long, short, gross, and net exposure. Solver tests will continue to prove gross exposure uses absolute weights through split variables and add a bounded negative-weight case. Defaults tests will assert that the default draft is long-only. Rebalance tests will assert signed crossing-zero deltas and buy/sell semantics.

Second, implement production changes. `constraints.cljs` will gain cap helpers for finite nonnegative long and short caps, conservative shortability checks, shortability-aware `bounds-for`, and feasibility violations for net range outside lower and upper bound sums, negative gross max, gross max below minimum required net, locked gross exceeding gross max, and locked non-shortable shorts. Runtime sparse caps will keep applying through `:max-weight`, which remains an absolute cap unless more specific long/short caps exist. `diagnostics.cljs` will expose a pure `exposure-summary` helper and use it in `portfolio-diagnostics`. `request_builder.cljs` and `contracts/specs.cljs` will preserve and validate top-level short cap fields consistently with existing constraint fields. `defaults.cljs` will set `:long-only? true`.

Third, run focused tests first, then broad gates. If a test exposes an existing expectation that assumed `:long-only? false` makes all assets shortable, update the test or fixture only when that assumption is now incorrect under the new semantics.

## Concrete Steps

Working directory for all commands is `/Users/barry/.codex/worktrees/0a87/hyperopen`.

Run focused tests after adding RED tests:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js

The new tests should fail before production code changes because signed mode currently assigns negative lower bounds to all assets, lacks separate long/short caps, lacks the new exposure summary fields, and the default draft is still not semantically long-only.

Run the same focused test path after implementation. Because this repository's generated runner currently runs all ClojureScript tests, interpret the focused phase by checking the named optimizer tests in the output or by failure names.

Run required final gates:

    npm run check
    npm test
    npm run test:websocket

All three commands should exit zero before this plan is moved to completed.

## Validation and Acceptance

The change is accepted when tests demonstrate that `:long-only? true` forbids negative target weights except locked positions that current product semantics permit; `:long-only? false` does not automatically make every instrument shortable; shortable instruments can receive negative weights subject to `:max-short-weight`; non-shortable instruments remain nonnegative; gross exposure is the sum of absolute weights; net exposure is the signed sum; gross and net constraints are both enforced; 130/30 and market-neutral constraint sets are feasible when shortable instruments exist; the default draft is semantically long-only; and existing long-only behavior remains compatible.

The required command evidence is `npm run check`, `npm test`, and `npm run test:websocket` passing.

## Idempotence and Recovery

The edits are additive or local replacements in optimizer source and tests. If a test command fails because the generated runner is stale, rerun `npm run test:runner:generate`. If broad gates expose unrelated pre-existing failures, record the failure and the clean working-tree evidence before deciding whether it is in scope. Do not run `git pull --rebase`, `git push`, `git reset --hard`, or destructive checkout commands without explicit user approval.

## Artifacts and Notes

Initial source inspection found this current behavior in `constraints.cljs`:

    (if (:long-only? constraints)
      {:lower 0
       :upper max-weight}
      {:lower (- max-weight)
       :upper max-weight})

That expression is the primary behavior being replaced. No solver rewrite is planned.

## Interfaces and Dependencies

The main public-style helper added by this plan is `hyperopen.portfolio.optimizer.domain.diagnostics/exposure-summary`, called as `(exposure-summary weights)` and returning a map with `:long-exposure`, `:short-exposure`, `:gross-exposure`, and `:net-exposure`.

The constraint model will accept `:max-long-weight`, `:max-short-weight`, and `:shortable?` at least inside `:per-asset-overrides` and `:asset-overrides`, and top-level `:max-long-weight` and `:max-short-weight` when present. Existing `:max-weight` and `:max-asset-weight` remain compatible. Negative or non-finite caps are invalid for solving and should yield explicit constraint violations rather than inverted bounds.

Plan revision note, 2026-05-26: Created the active ExecPlan from the direct implementation request so future contributors can resume from this file alone.

Plan revision note, 2026-05-26: Recorded RED-phase tests and the environment setup discovery that `node_modules` needed to be installed from `package-lock.json` before the ClojureScript test bundle could run.

Plan revision note, 2026-05-26: Recorded the GREEN ClojureScript test run and the fixture metadata decision for conservative shortability.

Plan revision note, 2026-05-26: Recorded required gate results and moved the plan from active to completed after acceptance criteria passed.

Plan revision note, 2026-05-26: Added final explicit coverage for invalid negative caps and direct exposure summary behavior, then reran the focused CLJS bundle and required repo gates.
