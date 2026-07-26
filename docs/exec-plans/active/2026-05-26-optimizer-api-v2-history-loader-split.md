# Optimizer API v2 History Loader Split

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan follows `/hyperopen/.agents/PLANS.md`. It exists because of a direct user request on 2026-05-26 to implement the highest-priority optimizer refactoring opportunity identified in a prior codebase review: split the API v2 history loader access-control layer into focused namespaces while preserving the existing public facade.

## Purpose / Big Picture

The optimizer history API v2 loader currently concentrates several responsibilities in `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs`: response key normalization, discovery metadata normalization, history bundle normalization, return alignment, warning assembly, funding summaries, and calendar freshness helpers. This makes the code harder to navigate and makes future expansion risky because unrelated changes require editing the same large namespace.

After this change, a maintainer can navigate API v2 loader work by responsibility. The public namespace `hyperopen.portfolio.optimizer.application.history-loader.api-v2` remains available for existing callers, but its implementation delegates to focused namespaces under `history_loader/api_v2/` plus a shared `history_loader/calendar.cljs`. The observable behavior is unchanged: existing optimizer API v2 tests still pass, and new tests prove the extracted namespaces produce the same facade-level values.

## Context References

Public refs:

- Direct user request in this Codex session on 2026-05-26: "Create an execution plan for the number one item on that list and then go ahead and implement it."

Repo artifacts:

- `src/hyperopen/portfolio/optimizer/BOUNDARY.md` defines optimizer layering and side-effect boundaries.
- `docs/exec-plans/active/2026-05-14-optimizer-history-api-v2-integration.md` records the original API v2 integration context.
- `docs/exec-plans/active/2026-05-15-optimizer-api-v2-return-first-readiness.md` records later API v2 return-alignment behavior.
- `dev/namespace_size_exceptions.edn` lists `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs` as optimizer refactoring debt.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-26T16:25:40Z) Confirmed the workspace is a clean linked worktree and created branch `codex/optimizer-api-v2-history-split`.
- [x] (2026-05-26T16:25:40Z) Read the current API v2 loader, legacy history alignment, existing API v2 tests, and ExecPlan rules.
- [x] (2026-05-26T16:25:40Z) Add RED tests that require the new focused namespaces while confirming the existing API v2 facade remains usable.
- [x] (2026-05-26T16:36:30Z) Extract shared calendar helpers into `src/hyperopen/portfolio/optimizer/application/history_loader/calendar.cljs` and use them from both API v2 and legacy alignment paths.
- [x] (2026-05-26T16:36:30Z) Extract API v2 codec, discovery, bundle, and alignment responsibilities into focused namespaces under `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/`.
- [x] (2026-05-26T16:36:30Z) Replace `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs` with a compatibility facade that re-exports or delegates to the focused namespaces.
- [x] (2026-05-26T16:36:30Z) Remove the stale namespace-size exception for the now-small API v2 facade and split the new contract test into `history_loader_api_v2_split_test.cljs`.
- [x] (2026-05-26T16:38:39Z) Run RED verification, generated ClojureScript tests, focused namespace and optimizer contract lints, `npm test`, and `npm run test:websocket`.
- [ ] `npm run check` is blocked by unrelated `docs/exec-plans/active/2026-05-24-rebalance-slippage-snapshot-estimates.md` having no unchecked progress items; rerun after that stale active ExecPlan is moved or refreshed by its owner.
- [ ] Move this plan to `docs/exec-plans/completed/` after `npm run check` is no longer blocked.

## Surprises & Discoveries

- Observation: The existing API v2 tests already exercise behavior through the public facade, but they do not fail when the implementation remains monolithic.
  Evidence: `test/hyperopen/portfolio/optimizer/application/history_loader_api_v2_test.cljs` imports only `hyperopen.portfolio.optimizer.application.history-loader.api-v2` before this plan.

- Observation: Calendar and freshness helpers are duplicated between `history_loader/api_v2.cljs` and `history_loader/alignment.cljs`.
  Evidence: both namespaces define local `common-calendar`, return interval, and freshness functions around the same `metrics-history/day-ms` conversion.

- Observation: The RED test failed before implementation for the intended missing namespace.
  Evidence: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test` exited 1 with `The required namespace "hyperopen.portfolio.optimizer.application.history-loader.calendar" is not available`.

- Observation: Adding the split contract test to the existing API v2 test namespace exceeded the project namespace-size limit, so the test needed its own focused namespace.
  Evidence: `npm run lint:namespace-sizes` reported `test/hyperopen/portfolio/optimizer/application/history_loader_api_v2_test.cljs - namespace has 578 lines`; after moving the test to `history_loader_api_v2_split_test.cljs`, the namespace size check passed.

- Observation: The public API v2 facade is now small enough that its old namespace-size exception is stale.
  Evidence: `npm run lint:namespace-sizes` reported `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs - namespace is now 37 lines; remove the stale exception entry`, and the exception was removed from `dev/namespace_size_exceptions.edn`.

- Observation: The full repository `npm run check` gate is blocked outside this refactor's files.
  Evidence: `npm run check` exited 1 at `lint:docs` with `[active-exec-plan-no-unchecked-progress] docs/exec-plans/active/2026-05-24-rebalance-slippage-snapshot-estimates.md - active ExecPlan has no remaining unchecked progress items; move it out of active`.

## Decision Log

- Decision: Preserve the public `api-v2` namespace as a facade rather than updating every caller to the extracted namespaces.
  Rationale: Existing runtime, infrastructure, application, and test callers already depend on the facade. Keeping it preserves the current public API while improving internal navigation.
  Date/Author: 2026-05-26 / Codex

- Decision: Introduce `history_loader/calendar.cljs` as shared application code and route both legacy history alignment and API v2 alignment through it.
  Rationale: This removes duplicated date/freshness math without changing optimizer domain behavior or introducing a new abstraction outside the history loader boundary.
  Date/Author: 2026-05-26 / Codex

- Decision: Add narrow tests for the extracted namespace contracts instead of changing broad optimizer behavior tests.
  Rationale: The refactor should not change behavior. Existing API v2 tests protect facade behavior, while the new tests make the intended split observable and prevent the monolithic namespace from silently returning.
  Date/Author: 2026-05-26 / Codex

- Decision: Keep the split-contract test in a new test namespace rather than extending `history_loader_api_v2_test.cljs`.
  Rationale: The existing behavior suite is already near the size threshold; a focused split test keeps the new architecture check discoverable without creating a new namespace-size exception.
  Date/Author: 2026-05-26 / Codex

## Outcomes & Retrospective

Implementation is complete pending final required gates. The API v2 facade is now a small delegating namespace, the implementation lives in focused codec/discovery/bundle/alignment namespaces, and shared calendar helpers are used by both legacy and API v2 alignment paths.

Validation evidence so far: the RED compile failed before implementation for the missing namespace; after implementation, `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js` passed with 4,084 tests and 22,541 assertions; `npm run lint:namespace-sizes`, `npm run lint:namespace-boundaries`, and `npm run lint:optimizer-contract-paths` passed; `npm test` passed with 4,084 tests and 22,541 assertions; `npm run test:websocket` passed with 527 tests and 3,067 assertions. `npm run check` is not green because of the unrelated stale active ExecPlan documented above.

Revision note 2026-05-26T16:25:40Z: Added RED test progress and failure evidence after confirming the new namespace contract fails before implementation.

Revision note 2026-05-26T16:36:30Z: Recorded implementation progress, namespace-size cleanup, and the decision to keep the new split contract test in its own namespace.

Revision note 2026-05-26T16:38:39Z: Added validation evidence and recorded the unrelated `npm run check` blocker so the plan remains active and self-contained.

## Context and Orientation

The optimizer history loader turns market history into the aligned price and return data that optimizer requests consume. In this repository, a "facade" namespace is a stable entry point that other namespaces import; it may delegate to internal namespaces but keeps existing callers from needing churn. A "codec" here means pure conversion between API-shaped maps and ClojureScript maps with kebab-case keywords and normalized scalar values.

The current public facade is `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs`, namespace `hyperopen.portfolio.optimizer.application.history-loader.api-v2`. Current callers include `src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs`, `src/hyperopen/portfolio/optimizer/infrastructure/history_client.cljs`, `src/hyperopen/portfolio/optimizer/infrastructure/history_api_v2_client.cljs`, `src/hyperopen/portfolio/optimizer/application/universe_candidates.cljs`, `src/hyperopen/portfolio/optimizer/actions/universe.cljs`, `src/hyperopen/portfolio/optimizer/application/history_workflow.cljs`, and `src/hyperopen/portfolio/optimizer/application/history_loader.cljs`.

The extracted implementation namespaces will be:

- `src/hyperopen/portfolio/optimizer/application/history_loader/calendar.cljs`: common calendar intersection, return interval, return interval for a supplied return calendar, and freshness helpers.
- `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/codec.cljs`: `contract-version`, API map normalization, warning normalization, proxy normalization, and scalar coercion helpers used by the API v2 loader.
- `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/discovery.cljs`: `normalize-discovery` and `with-discovery-metadata`.
- `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/bundle.cljs`: `normalize-history-body` and `normalize-history-bundle`.
- `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/alignment.cljs`: `default-min-observations` and `align-api-v2-history-inputs`.
- `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs`: compatibility facade that exposes the same public vars as before.

## Plan of Work

First add RED coverage in `test/hyperopen/portfolio/optimizer/application/history_loader_api_v2_test.cljs`. The test should require the focused namespaces and assert that `codec/normalize-api-map`, `discovery/normalize-discovery`, `bundle/normalize-history-bundle`, and `alignment/align-api-v2-history-inputs` match the facade's public results for a small API v2 payload. Before extraction, this test should fail during compile because those namespaces do not exist.

Then create `history_loader/calendar.cljs` and move the shared pure calendar helpers there. Update `history_loader/alignment.cljs` to require that namespace and replace local calls to `common-calendar`, `return-intervals`, and `freshness` with `calendar/common-calendar`, `calendar/return-intervals`, and `calendar/freshness`.

Next extract the top portion of `api_v2.cljs` into `api_v2/codec.cljs`, including API key normalization, `normalize-api-map`, warning normalization, proxy normalization, and numeric coercion helpers that are needed by the other extracted namespaces. Keep implementation details private where only one namespace uses them, but expose helpers that multiple API v2 namespaces need.

Then extract discovery normalization into `api_v2/discovery.cljs`. It should require `api_v2/codec.cljs` and `history_loader/instruments.cljs` only if needed; it should expose only `normalize-discovery` and `with-discovery-metadata`.

Then extract bundle normalization into `api_v2/bundle.cljs`. It should require `api_v2/codec.cljs` and `history_loader/instruments.cljs`. It should expose only `normalize-history-body` and `normalize-history-bundle`.

Then extract alignment into `api_v2/alignment.cljs`. It should require `api_v2/codec.cljs`, `api_v2/bundle.cljs` only if needed, `history_loader/calendar.cljs`, `history_loader/instruments.cljs`, and `history-series`. It should expose `default-min-observations` and `align-api-v2-history-inputs`.

Finally replace `api_v2.cljs` with a small facade that requires the four API v2 implementation namespaces and exposes `contract-version`, `default-min-observations`, `normalize-api-map`, `normalize-discovery`, `with-discovery-metadata`, `normalize-history-body`, `normalize-history-bundle`, and `align-api-v2-history-inputs`. No runtime caller should need to change.

## Concrete Steps

Run all commands from `/Users/barry/.codex/worktrees/3565/hyperopen`.

1. Write the focused namespace contract test in `test/hyperopen/portfolio/optimizer/application/history_loader_api_v2_test.cljs`.

2. Verify RED:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test

Expected before extraction: compile failure because `hyperopen.portfolio.optimizer.application.history-loader.api-v2.codec` and sibling namespaces cannot be found.

3. Add the new source namespaces and update the facade and legacy alignment.

4. Verify GREEN with the same compile command, then run the generated tests:

    node out/test.js

Expected after extraction: the generated ClojureScript test suite reports zero failures and zero errors.

5. Run focused guardrails:

    npm run lint:namespace-sizes
    npm run lint:namespace-boundaries
    npm run lint:optimizer-contract-paths

Expected: all commands exit 0.

6. Run required repository gates because source code changed:

    npm run check
    npm test
    npm run test:websocket

Expected: all commands exit 0.

## Validation and Acceptance

Acceptance requires all existing API v2 history loader behavior to remain available through `hyperopen.portfolio.optimizer.application.history-loader.api-v2`, while new focused namespaces are directly importable in tests. Specifically, `test/hyperopen/portfolio/optimizer/application/history_loader_api_v2_test.cljs` must demonstrate that focused namespace calls and facade calls produce the same normalized discovery, normalized history bundle, and aligned history results for a small API v2 payload.

The final validation commands are `npm run check`, `npm test`, and `npm run test:websocket`. Because this is not UI-facing and does not change browser flows, Browser MCP and Playwright QA are not required.

## Idempotence and Recovery

The refactor is additive first: create new namespaces, route code through them, then shrink the facade. If a compile or test failure appears, inspect the namespace reported by Shadow CLJS and restore the facade to delegate to the last passing extracted function. No data migrations or destructive commands are involved. If the work must be paused, leave this plan in `docs/exec-plans/active/` with the next unchecked progress item describing what remains.

## Artifacts and Notes

Initial source-size evidence from the prior review identified `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2.cljs` as 774 lines and listed it in `dev/namespace_size_exceptions.edn`. The planned result should leave the facade small enough to remove or reduce that exception in a future cleanup, but this plan does not require changing namespace-size policy.

## Interfaces and Dependencies

At the end of the refactor, these public functions and values must exist:

- `hyperopen.portfolio.optimizer.application.history-loader.calendar/common-calendar`
- `hyperopen.portfolio.optimizer.application.history-loader.calendar/return-intervals`
- `hyperopen.portfolio.optimizer.application.history-loader.calendar/return-intervals-for-calendar`
- `hyperopen.portfolio.optimizer.application.history-loader.calendar/freshness`
- `hyperopen.portfolio.optimizer.application.history-loader.api-v2.codec/contract-version`
- `hyperopen.portfolio.optimizer.application.history-loader.api-v2.codec/normalize-api-map`
- `hyperopen.portfolio.optimizer.application.history-loader.api-v2.discovery/normalize-discovery`
- `hyperopen.portfolio.optimizer.application.history-loader.api-v2.discovery/with-discovery-metadata`
- `hyperopen.portfolio.optimizer.application.history-loader.api-v2.bundle/normalize-history-body`
- `hyperopen.portfolio.optimizer.application.history-loader.api-v2.bundle/normalize-history-bundle`
- `hyperopen.portfolio.optimizer.application.history-loader.api-v2.alignment/default-min-observations`
- `hyperopen.portfolio.optimizer.application.history-loader.api-v2.alignment/align-api-v2-history-inputs`

The facade `hyperopen.portfolio.optimizer.application.history-loader.api-v2` must continue exposing the same public functions and values that existing callers use today.
