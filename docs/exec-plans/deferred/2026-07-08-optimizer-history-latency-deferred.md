# Optimizer History Latency: Deferred Workstreams

This ExecPlan is deferred. It records two investigated-but-not-implemented
latency causes from the 2026-07-08 proxy-loading performance traces so a
future activation starts from evidence, not rediscovery.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Companion to `docs/exec-plans/active/2026-07-08-optimizer-history-double-fetch-elimination.md`
(items 1–2 of the maintainer's 2026-07-08 request; this file is items 3–4).
With the double-fetch eliminated, the remaining cold-open timeline for a
~50-asset spectated universe is roughly: ~2s draft restore → ~5.1s bundle
request start → ~10s response + parse → settle. The two remaining costs:

### 3. The load-start gap (~3s: draft visible at ~2s, request at ~5.1s)

The restore funnel appends `[:effects/load-portfolio-optimizer-history]` in
the same batch as the draft write, but the effect is classified in
`src/hyperopen/runtime/effect_order_contract.cljs` `:heavy-effect-ids`, so
startup sequencing defers it behind the deferred-bootstrap window
(`:startup {:deferred-bootstrap-delay-ms 1200 :stream-backfill-delay-ms 450}`
in `src/hyperopen/config.cljs`) plus watcher debounces. Direction: let the
optimizer route opt this effect out of (or shorten) the heavy-effect
deferral — the user is staring at the exact surface the fetch feeds, so
deferring it optimizes for the wrong tab. Validate by tracing that the bundle
request starts within ~500ms of the draft restore without regressing the
PageSpeed budgets that motivated the heavy-effect contract
(`docs/exec-plans/active/2026-06-11-pagespeed-desktop-performance-90.md`).

### 4. The backend bundle response time (~5–7s per ~50-instrument request)

`POST /v1/optimizer/history-bundle` took 4.8s (trace #2, first request) to
6.8s (second request) for ~50 instruments × ~3y daily window. This is
backend-owned latency; the client cannot shrink it, only hide or split it.
Directions, in rough order of value:

- Backend latency budget: agree a target (e.g. p95 < 2s for 50 instruments)
  with the price-history service; measure server-side where the time goes
  (this may be mostly cold-cache assembly).
- Client-side parallel chunking: the client already chunks >100-instrument
  requests; chunking at ~25 and issuing chunks in parallel would roughly
  halve wall-clock IF backend time scales with instrument count — measure
  first, the answer decides whether this is worth the alignment-merge
  complexity (chunked responses have per-chunk calendars; the delta-merge
  rules from the active plan apply).
- Hyperliquid `/info` rate-limit pressure: duplicate parallel POSTs during
  startup (4 identical requests per retry round in trace #1) delay holdings
  arrival on cold opens; a shared in-flight dedup in the info client would
  remove the 429 storms that gate universe seeding in spectate mode.

## Context References

- Direct maintainer request on 2026-07-08 (chat): "write up 3 and 4 as
  deferred for now".
- Performance traces: `Trace-20260708T084245.json` (429 storm, single late
  bundle), `Trace-20260708T100247.json` (double fetch, early first bundle).
- Trace analysis recorded in
  `docs/exec-plans/active/2026-07-08-optimizer-history-double-fetch-elimination.md`
  and
  `docs/exec-plans/completed/2026-07-08-optimizer-proxy-loading-rail-and-normalize-perf.md`.
- Related debt entry: `docs/exec-plans/tech-debt-tracker.md` (optimizer
  history-bundle load path costs).

## Plan of Work

(Deferred — to be detailed at activation.)

1. Measure heavy-effect deferral contribution vs watcher debounce in the
   load-start gap; exempt or fast-path the optimizer-route history load.
2. Instrument backend bundle latency; set a budget; decide parallel chunking
   from the scaling data.
3. Add in-flight dedup for identical `/info` POSTs.

## Progress

- [x] (2026-07-08) Both traces analyzed; mechanisms identified and recorded above.
- [ ] (deferred) Everything else.

## Validation and Acceptance

At activation: a fresh performance trace of the same spectate flow must show
the bundle request starting within ~500ms of draft restore (item 3) and the
end-to-end settle bounded by a single backend fetch within its agreed budget
(item 4), with `npm run gates` green and no PageSpeed budget regression.

## Surprises & Discoveries

(None yet — deferred.)

## Decision Log

- Deferred by maintainer direction on 2026-07-08: items 1–2 (double-fetch
  elimination) deliver most of the user-visible win; these two need
  cross-cutting work (startup contract, backend service) with their own risk.

## Outcomes & Retrospective

(To be filled if activated.)
