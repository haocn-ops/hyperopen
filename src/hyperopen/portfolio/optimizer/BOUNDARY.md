---
owner: portfolio
status: canonical
last_reviewed: 2026-05-13
review_cycle_days: 90
source_of_truth: true
---

# Portfolio Optimizer Boundary

## Owns

- The `/portfolio/optimize` route family: `/portfolio/optimize`,
  `/portfolio/optimize/new`, and `/portfolio/optimize/:scenario-id`.
- Optimizer draft state, scenario lifecycle, local scenario persistence,
  route-query tab state, and current-vs-target result identity.
- Optimizer map contracts, schema versions, request signatures, state path
  constants, and worker wire normalization.
- Current-portfolio snapshots, arbitrary universe selection, vault/perp/spot
  instrument identity, candle/funding/vault history assembly, setup readiness,
  and optimizer run request construction.
- Pure optimization policy: constraints, objectives, return inputs, risk model
  shaping, Black-Litterman views, efficient frontier data, diagnostics,
  rebalance preview shaping, and weight cleanup.
- Optimizer run orchestration, progress state, worker request dispatch, solver
  result payload shaping, tracking snapshots, and execution-preview workflow.
- Optimizer-specific UI view models and canonical route surfaces. Views may render
  optimizer state, but they must not become the source of optimizer math,
  readiness, history alignment, or persistence semantics.

## Stable Public Seams

- `hyperopen.portfolio.routes`
  Canonical route parsing and path construction for `/portfolio/optimize`.
  `/portfolio/optimizer` is not the route spelling.
- `hyperopen.portfolio.optimizer.contracts`
  Canonical optimizer state paths, schema versions, specs, request signatures,
  and wire codecs. Do not re-declare optimizer map shapes in callers.
- `hyperopen.portfolio.optimizer.defaults`
  Canonical default draft, run-state, progress, scenario, history-load, and
  tracking shapes.
- `hyperopen.portfolio.optimizer.actions`
  Public optimizer action normalization facade. Keep action names stable unless
  a migration is explicit and tested.
- `hyperopen.portfolio.optimizer.query-state`
  Optimizer scenario tab query parsing and serialization for `otab`.
- `hyperopen.portfolio.optimizer.application.view-model`
  Route-facing read model for setup and scenario-detail views.
- `hyperopen.portfolio.optimizer.application.request-builder`
  Draft/current-portfolio/history to engine-request assembly.
- `hyperopen.portfolio.optimizer.application.setup-readiness`
  Blocking readiness reasons and user-facing run gating copy.
- `hyperopen.portfolio.optimizer.application.history-loader`
  Application facade for history request planning, normalization, alignment,
  and warnings.
- `hyperopen.portfolio.optimizer.application.pipeline-workflow`
  Pure run pipeline state machine. Runtime adapters interpret its commands.
- `hyperopen.portfolio.optimizer.application.engine`
  Worker-safe optimizer engine facade for risk/return context, solve plans,
  solver results, and result payloads.
- `hyperopen.portfolio.optimizer.infrastructure.*`
  Optimizer-owned API clients, persistence, prior data, solver adapter,
  worker-client, and worker wire normalization.
- `hyperopen.runtime.effect-adapters.portfolio-optimizer*`
  Browser/runtime side-effect interpreters for history loading, scenario
  persistence, tracking, execution, and worker requests.
- `hyperopen.portfolio.optimizer.worker`
  Dedicated worker entrypoint. It may depend on worker-safe optimizer
  domain/application code and worker-safe infrastructure such as the solver
  adapter.

## Dependency Rules

- Allowed:
  `hyperopen.portfolio.optimizer.domain.*` stays pure and may depend only on
  generic utilities, optimizer contracts/coercion, and other pure optimizer
  helpers.
- Allowed:
  `hyperopen.portfolio.optimizer.application.*` may depend on optimizer domain
  policy, optimizer contracts/defaults, portfolio/account read models, injected
  collaborators, and effect data contracts. Application namespaces may emit
  effect descriptions, but they must not perform browser, network, websocket,
  IndexedDB, or exchange-submit side effects directly.
- Allowed:
  `hyperopen.portfolio.optimizer.infrastructure.*` may depend on browser-safe or
  worker-safe integration APIs appropriate to that namespace. External protocol
  payloads must be normalized before they become optimizer application data.
- Allowed:
  Runtime effect adapters may mutate the app store, call browser APIs, talk to
  workers, submit orders, fetch history, and interact with websocket/orderbook
  seams because they are the side-effect boundary.
- Allowed:
  Views under `hyperopen.views.portfolio.optimize.*` may consume optimizer view
  models, formatters, and action maps. They may own route composition, layout,
  data roles, and visual states.
- Forbidden:
  Do not import `hyperopen.views.*` from optimizer domain, application,
  infrastructure, contracts, worker, or runtime workflow namespaces.
- Forbidden:
  Do not duplicate optimizer state paths, request signatures, or result payload
  shapes outside `contracts.cljs`.
- Forbidden:
  Do not branch UI code on raw exchange/API payload shape; normalize through
  optimizer history clients, wire adapters, or application helpers first.
- Forbidden:
  Do not mutate websocket runtime state from optimizer application code.
  Optimizer code may plan orderbook needs; runtime effect adapters own the
  websocket side effects.
- Forbidden:
  Do not add browser persistence outside optimizer infrastructure or shared
  platform storage seams. Follow `/hyperopen/docs/BROWSER_STORAGE.md` for
  storage choice and lifecycle.

## Key Tests

- Core contracts and defaults:
  `hyperopen.portfolio.optimizer.contracts-test`,
  `hyperopen.portfolio.optimizer.defaults-test`,
  `hyperopen.portfolio.optimizer.actions-test`,
  `hyperopen.portfolio.optimizer.query-state-test`
- Pure domain and engine behavior:
  `hyperopen.portfolio.optimizer.domain.*-test`,
  `hyperopen.portfolio.optimizer.application.engine-test`,
  `hyperopen.portfolio.optimizer.worker-test`
- Request, readiness, history, and pipeline behavior:
  `hyperopen.portfolio.optimizer.application.request-builder-test`,
  `hyperopen.portfolio.optimizer.application.setup-readiness-test`,
  `hyperopen.portfolio.optimizer.application.history-loader-test`,
  `hyperopen.portfolio.optimizer.application.history-loader-vaults-test`,
  `hyperopen.portfolio.optimizer.application.pipeline-workflow-test`,
  `hyperopen.runtime.effect-adapters.portfolio-optimizer-pipeline-test`
- Persistence, worker, tracking, and execution integration:
  `hyperopen.portfolio.optimizer.infrastructure.persistence-test`,
  `hyperopen.portfolio.optimizer.infrastructure.solver-adapter-test`,
  `hyperopen.portfolio.optimizer.infrastructure.solver-adapter-parity-test`,
  `hyperopen.runtime.effect-adapters.portfolio-optimizer-test`,
  `hyperopen.runtime.effect-adapters.portfolio-optimizer-history-test`,
  `hyperopen.runtime.effect-adapters.portfolio-optimizer-scenarios-test`,
  `hyperopen.runtime.effect-adapters.portfolio-optimizer-tracking-test`,
  `hyperopen.runtime.effect-adapters.portfolio-optimizer-execution-test`
- Route and UI behavior:
  `hyperopen.views.portfolio.optimize.view-test`,
  `hyperopen.views.portfolio.optimize.index-view-test`,
  `hyperopen.views.portfolio.optimize.setup-view-test`,
  `hyperopen.views.portfolio.optimize.setup-layout-test`,
  `hyperopen.views.portfolio.optimize.scenario-detail-view-test`,
  `hyperopen.views.portfolio.optimize.results-panel-test`,
  `hyperopen.views.portfolio.optimize.frontier-chart-contract-test`,
  `hyperopen.views.portfolio.optimize.execution-tab-test`,
  `hyperopen.views.portfolio.optimize.tracking-panel-test`
- Browser validation for route or interaction work:
  follow `/hyperopen/docs/BROWSER_TESTING.md` and run the smallest relevant
  Playwright optimizer command before broader browser validation.
- Final repo gates when code changes:
  `npm run check`, `npm test`, `npm run test:websocket`

## Where This Change Goes

- New optimizer route shape or path helper:
  `hyperopen.portfolio.routes`, then `hyperopen.views.portfolio.optimize.view`
  for dispatch.
- New optimizer tab query value or tab alias:
  `hyperopen.portfolio.optimizer.query-state`, associated state defaults, and
  scenario-detail view tests.
- New persisted field, schema version, state path, wire key, or request
  signature input:
  `hyperopen.portfolio.optimizer.contracts`, `defaults`, migration/persistence
  helpers, and contract tests.
- New draft action, preset, control, or state transition:
  `hyperopen.portfolio.optimizer.actions.*`, then setup view-model/UI tests.
- New universe source, instrument type, or label behavior:
  `application.universe-candidates`, `application.history-loader.instruments`,
  `application.instrument-labels`, and optimizer fixture tests.
- New candle, funding, or vault history behavior:
  `application.history-loader.request-plan`, `normalization`, `alignment`,
  `infrastructure.history-client`, and history-loader tests.
- New setup run gate, readiness warning, or run-disabled copy:
  `application.setup-readiness`, then setup readiness panel or universe row
  tests if the user-visible status changes.
- New request payload field or engine input semantics:
  `application.request-builder`, `contracts`, and request-builder tests before
  touching the engine.
- New return, risk, objective, constraint, Black-Litterman, frontier, diagnostic,
  or rebalance math:
  the focused namespace under `domain/`, then `application.engine.*` or
  `application.engine.payload` only if payload assembly must change.
- New solver integration or OSQP behavior:
  `application.engine.solve`, `infrastructure.solver-adapter`, and worker tests.
- New run pipeline, progress step, history-prefetch wait, or worker dispatch:
  `application.pipeline-workflow` first, then
  `runtime.effect-adapters.portfolio-optimizer-pipeline`.
- New scenario save/load/archive/duplicate behavior:
  `application.scenario-records`, `application.scenario-workflow`,
  `infrastructure.persistence`, and
  `runtime.effect-adapters.portfolio-optimizer-scenarios`.
- New execution preview, rebalance submit, orderbook depth, or tracking behavior:
  `domain.rebalance`, `application.execution*`, `application.orderbook-loader`,
  `application.tracking`, and the matching runtime effect adapter.
- New setup route UI under `/portfolio/optimize/new`:
  `views.portfolio.optimize.setup-view`, `workspace-view`, `setup-*`, and
  `src/styles/surfaces/optimizer.css`.
- New scenario detail UI under `/portfolio/optimize/:scenario-id`:
  `views.portfolio.optimize.scenario-detail-view` plus the tab file
  (`results-panel`, `rebalance-tab`, `tracking-panel`, or `inputs-tab`).
- New chart, marker, callout, or frontier display behavior:
  `application.display-frontier` or `domain.frontier*` for data rules, then
  `views.portfolio.optimize.frontier-*` for rendering.
- New test fixture for future optimizer work:
  `test/hyperopen/portfolio/optimizer/fixtures.cljs`. Keep fixture helpers out
  of production namespaces unless runtime code actually needs them.
