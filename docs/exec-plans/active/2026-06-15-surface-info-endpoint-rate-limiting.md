# Surface Info-Endpoint Rate Limiting In Network Diagnostics

This ExecPlan is a living document. Keep `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` current while the work proceeds.

## Purpose / Big Picture

When the Hyperliquid HTTP `/info` endpoint rate-limits us (HTTP 429), the app already
detects it and backs off (`hyperopen.api.info-client.flow` → `track-rate-limit!` /
`mark-rate-limit-cooldown!`), but that state lives only in the info-client's private
request-runtime atom and is never surfaced to the user. The "network diagnostics" drawer
(`hyperopen.views.footer.diagnostics-drawer`) is sourced exclusively from websocket
health, so a user who opens it while being throttled sees nothing about it.

After this change, an info-endpoint rate-limit is visible in the diagnostics drawer the
user clicks into: a prominent "Rate limited" notice while a cooldown is active, a
cumulative "Rate-limited (info)" counter in Developer details, and a `:info-rate-limit`
section in the redacted **Copy diagnostics** payload for support.

The info-client stays 100% store-agnostic. It exposes a settable `on-rate-limit` callback;
app startup installs a store-aware listener that folds events into
`[:websocket-ui :info-rate-limit]`, mirroring the existing direct-`swap!` pattern used for
reconnect/reset/auto-recover counters (no new action/effect contract).

## Context Reference

Direct user request on 2026-06-15: after confirming the diagnostics drawer does not surface
info-endpoint rate-limiting, create an execution plan to surface that the user is being rate
limited and implement it in that component.

## Progress

- [x] (2026-06-15) Mapped the drawer/view-model/payload data sources and confirmed they are websocket-only.
- [x] (2026-06-15) Scoped the decoupled wiring seam, gate requirements, namespace-size budgets, and test surface.
- [x] (2026-06-15) Add a settable `on-rate-limit` callback to the info-client (`make-info-client`) and expose `set-on-rate-limit!` through the api service/default facade.
- [x] (2026-06-15) Fold rate-limit events into `[:websocket-ui :info-rate-limit]` via a pure reducer + a startup-installed listener.
- [x] (2026-06-15) Read the state in the diagnostics view-model (`:rate-limit` model + Developer-details counter row) and render the notice in the drawer.
- [x] (2026-06-15) Add the rate-limit section to the Copy diagnostics payload.
- [x] (2026-06-15) Add focused tests (info-client callback, policy model, view-model, payload, runtime reducer).
- [ ] Verify the indicator against a live 429 on testnet (cannot be exercised from unit tests; requires a real rate-limit window).

## Surprises & Discoveries

- Observation: the info-client is constructed at namespace load (`hyperopen.api.default.state/api-facade-state` defonce) with no reference to the store, so a store-bound callback cannot be supplied at construction.
  Evidence: `api/default/state.cljs` builds the default service eagerly; `hyperopen.system/store` is an independent defonce.
  Resolution: expose a `set-on-rate-limit!` setter on the live default client and install the store-aware listener during `app/startup.cljs init!`.

- Observation: writing the indicator state needs no contract surface.
  Evidence: `reconnect-count`, `reset-counts`, and `auto-recover-count` are all written by plain `swap!` into `[:websocket-ui ...]`; only user-button intents are contract-registered.

## Decision Log

- Decision: Fire the callback from the info-client's `mark-rate-limit-cooldown` wrapper (the single chokepoint both 429 sites already hit) rather than threading it through `flow.cljs`.
  Rationale: that wrapper has `now-ms-fn`, the post-swap `cooldown-until-ms`, and the running rate-limited count in scope; it fires exactly once per 429 with no `flow.cljs` change.
  Date/Author: 2026-06-15 / Claude

- Decision: Install the listener via a `set-on-rate-limit!` setter at startup, not by rebuilding the service via `configure-api-service!`.
  Rationale: avoids discarding the load-time client and its warm cache; the callback survives `reset-request-runtime!` (atoms reset in place) and is only lost on a full `reset-api-service!` (test-only).
  Date/Author: 2026-06-15 / Claude

- Decision: Show the prominent notice only while a cooldown is active; keep the cumulative counter in Developer details.
  Rationale: "surface that they ARE being rate limited (when it's the case)" maps to the active-throttle window; the counter remains useful for support after the window closes.
  Date/Author: 2026-06-15 / Claude

## Validation and Acceptance

- A 429 on the `/info` endpoint invokes the info-client's `on-rate-limit` callback once with `{:at-ms :delay-ms :cooldown-until-ms :count}`.
- The callback folds into `[:websocket-ui :info-rate-limit]` as `{:count :until-ms :last-at-ms}`.
- `policy/info-rate-limit-model` returns `nil` when never rate-limited, `:active? true` with a `:retry-in-seconds` while the cooldown is in the future, and `:active? false` once it expires (counter still present).
- The drawer renders the "Rate limited" notice while active and a "Rate-limited (info)" counter row in Developer details when the count is positive.
- The Copy diagnostics payload includes an `:info-rate-limit` section.
- Info-client remains store-agnostic (only a function is injected).

Required final commands:

- `npm run check`
- `npm test`
- `npm run test:websocket`

## Outcomes & Retrospective

Pending. Complete this section when the rate-limit indicator is accepted and this plan moves
to `docs/exec-plans/completed/`. Remaining open item: live testnet verification against a
real 429 window.
