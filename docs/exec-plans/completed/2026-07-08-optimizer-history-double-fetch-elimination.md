# Optimizer History Double-Fetch Elimination

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Second follow-up to the proxy-loading investigation (parent:
`docs/exec-plans/completed/2026-07-08-optimizer-proxy-loading-rail-and-normalize-perf.md`).
A fresh performance trace (`Trace-20260708T100247.json`, spectate view,
~50-asset universe) shows the history-bundle request fires promptly at ~5.1s
and completes at ~9.9s — but the assumption cards do not settle until ~19s
because a SECOND ~6.8s bundle request fires the instant the first one's parse
ends. Two distinct client behaviors cause redundant full-universe fetches:

1. **The current-portfolio duplicate fetch (this trace's actual second
   request).** After the selected-universe bundle resolves, the effect adapter
   (`effect_adapters/portfolio_optimizer/history.cljs`
   `current-history-needed?`) fetches the ENTIRE current-portfolio (holdings)
   universe as a separate bundle whenever the holdings id-set differs at all
   from the selected id-set. But the draft universe is seeded FROM holdings
   plus reference proxies, so holdings ⊂ selected in the default flow — and
   the consumer (`request_builder.cljs` `history-data-for-current-portfolio`,
   `set/subset?` branch) then reads the MAIN bundle and never touches the
   separate one. ~7.8s (6.8s network + 1.0s parse) fetched and discarded.
2. **The full-universe refetch on any queued delta.** Once api-v2 history
   exists, `history-workflow/begin-selection-prefetch` refetches the whole
   draft universe + reference proxies for ANY queued instrument (a late
   reference-proxy admission, a single universe add), because the backend's
   aligned-returns are only consistent within one response. The client
   already has point-level re-alignment (built for the superset-calendar
   poisoning), so a delta fetch + safe merge covers the same need at delta
   cost.

After this change, the default spectate/holdings-seeded flow issues ONE
bundle request (cards settle right after its parse — ~11s in this trace
instead of ~19s), and later universe/reference additions fetch only the new
instruments. Two remaining latency causes (the ~3s load-start gap and the
~5-7s backend bundle response) are explicitly deferred to
`docs/exec-plans/deferred/2026-07-08-optimizer-history-latency-deferred.md`.

## Context References

Public refs:

- Direct maintainer request on 2026-07-08 (chat): implement items 1 and 2 of
  the double-fetch analysis; write items 3 (load-start gap) and 4 (backend
  latency) up as deferred.
- Performance trace `~/Downloads/Trace-20260708T100247.json`: bundle #1
  5.1→9.9s, parse ends 10.683s, bundle #2 fires 10.683→17.45s, alignment
  1.46s, settle ~19s.

Repo artifacts:

- `src/hyperopen/runtime/effect_adapters/portfolio_optimizer/history.cljs` — `current-history-needed?` (fetcher side).
- `src/hyperopen/portfolio/optimizer/application/request_builder.cljs` `history-data-for-current-portfolio` — consumer side already implements subset semantics; the fetcher must mirror it.
- `src/hyperopen/portfolio/optimizer/application/history_workflow.cljs` — `begin-selection-prefetch` full-universe refetch; `merge-history-bundle`.
- `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/alignment.cljs` — the all-or-nothing `use-aligned?` mode and the point-level fallback the delta merge relies on.
- Tests pinning current behavior: `test/hyperopen/portfolio/optimizer/application/history_workflow_test.cljs` (lines 346, 403, 454), `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_history_test.cljs` (line 107).

## Plan of Work

1. **Subset-gate the current-portfolio fetch.** `current-history-needed?`
   fetches only when holdings contain instruments OUTSIDE the selected fetch
   (`(not (set/subset? current-ids selected-ids))`), mirroring the consumer
   exactly. Holdings ⊆ draft-universe (the default flow) → no second fetch.
2. **Delta-fetch the selection prefetch.** `begin-selection-prefetch`
   requests only the queued instruments — EXCEPT when some current
   universe/reference member is "aligned-only" (non-empty backend aligned
   returns but no usable point-level returns): merging a delta forces the
   all-or-nothing alignment into point-level mode, which would drop such a
   member, so their presence keeps today's full-universe refetch.
3. **Make `merge-history-bundle` delta-safe.** Incoming api-v2 history whose
   series cover all existing keys replaces wholesale (a full refresh, incl.
   the guard path). Otherwise (a delta): merge `series-by-instrument`, keep
   the EXISTING calendars/status/metadata, and merge
   `aligned-returns-by-instrument` only when both return- and common-calendars
   are identical — mismatched-calendar aligned rows must never mix, and the
   point-level alignment recomputes from the merged series (the same fallback
   the calendar-poisoning path already exercises in production).
4. **Tests.** Adapter: holdings ⊆ selected → one request, no
   `:current-portfolio-history-data`; disjoint case unchanged. Workflow:
   delta request when members have usable points; full-universe when an
   aligned-only member exists (the existing 454 fixture IS that case); merge
   wholesale / same-calendar / mismatched-calendar semantics; reference-proxy
   test updated to delta expectations.

## Validation and Acceptance

- Required gates: `npm run check`, `npm test`, `npm run test:websocket` (via
  `npm run gates`) all PASS.
- Playwright: `optimizer-proxy-loading-ux.spec.mjs`,
  `optimizer-history-api-v2.spec.mjs`,
  `optimizer-history-assumptions-io.spec.mjs` pass (the api-v2 spec's
  route-mocked flows exercise the prefetch + merge paths end to end).
- Acceptance: with a draft universe seeded from holdings (holdings ⊆
  selected), the load issues exactly one bundle request; adding one
  instrument to an api-v2-cached universe issues a request whose `:universe`
  is only the queued instruments (unless an aligned-only member exists);
  merged delta bundles never mix aligned-returns across different calendars.

## Progress

- [x] (2026-07-08) Trace #2 analysis: two sequential bundles, `.then`-chain timing pins bundle #2 to the current-portfolio fetch; consumer-side subset check found in request_builder.
- [x] (2026-07-08) Subset-gate `current-history-needed?` (`clojure.set/subset?`) + adapter test asserting one request and no `:current-portfolio-history-data` when holdings ⊆ selected.
- [x] (2026-07-08) Delta prefetch + `aligned-only-member?` guard in `begin-selection-prefetch`; workflow tests updated (reference proxy fetches as `["perp:SOL"]` delta; the old full-refetch test re-justified as the aligned-only guard case; new delta test).
- [x] (2026-07-08) Delta-safe merge extracted to new ns `application.history-merge` (workflow was over the size gate): wholesale on series-superset refreshes, delta keeps cached calendars, aligned rows merge only on identical calendars; `history-merge-test` covers all three.
- [x] (2026-07-08) `npm run gates` 34/34 PASS (5,990 tests / 31,870 assertions); 6/6 history-related Playwright specs PASS — notably the api-v2 sparse-aligned spec exercises the guard (its BTC fixture is aligned-only, so the full refetch correctly survives there).

## Surprises & Discoveries

- The consumer (`request_builder.cljs`) has had subset semantics all along —
  the wasted second fetch was produced by the fetcher and then provably never
  read in the subset case.
- The existing workflow test fixture at line 454 (BTC with empty points but
  non-empty aligned returns) is exactly the aligned-only guard case, so it
  keeps pinning the full-universe behavior under its new justification.

## Decision Log

- The aligned-only guard errs conservative: a member whose returns exist only
  in backend aligned form keeps the full refetch, because the delta merge
  forces point-level alignment (all-or-nothing `use-aligned?`) which would
  silently exclude that member from runs — honesty over speed.
- Delta merges keep the EXISTING calendars rather than adopting the delta's:
  the delta's calendars describe only the delta instruments' intersection and
  would poison the shared window for everyone else; the alignment layer
  recomputes effective calendars client-side regardless.
- Wholesale-replace on series-superset responses (rather than plumbing a
  "full refresh" flag through the command) keeps the merge decision local and
  pure.

## Outcomes & Retrospective

Landed as planned. The default holdings-seeded spectate flow now issues one
bundle request instead of two sequential full-universe requests (in the
2026-07-08 trace that removes the 10.7→17.5s second fetch plus its 1.0s
parse — settle moves from ~19s to right after the first response, ~11s), and
any later universe/reference addition fetches only the queued instruments
unless an aligned-only member pins the backend alignment. The
`optimizer-history-api-v2.spec.mjs` sparse-aligned browser spec doubles as
the guard's end-to-end regression test. Deferred items (load-start gap,
backend bundle latency, `/info` dedup) live in
`docs/exec-plans/deferred/2026-07-08-optimizer-history-latency-deferred.md`.
