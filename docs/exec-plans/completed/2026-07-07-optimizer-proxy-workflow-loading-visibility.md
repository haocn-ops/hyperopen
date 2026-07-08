# Optimizer Proxy Workflow Loading Visibility

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

When the `/portfolio/optimize` draft page loads, history-assumption (proxy workflow)
cards hydrated from the assumption library render *final-looking* verdicts while
background history fetching is still in flight: the status chip says
"Needs assumptions", the covariance-window diagnostic says "No usable native
returns", the regression panel says "No return overlap with the proxies yet.
Using the prior only.", and the disabled Apply button gives no reason. Seconds
later, when the prefetch queue drains, the same cards silently flip to
Configured/ready. The user has no signal that background work is happening, so
they start re-editing baskets they don't need to touch.

After this change, while proxy history is still loading a card explicitly says
so: the status chip becomes a pulsing "Loading history…" chip, the misleading
regression/covariance verdicts are replaced with loading copy, Apply is held
with an explanation, and the section header carries a pulsing aggregate banner
("Loading proxy history for N assets — cards update automatically."). Once
loading finishes, every surface returns to today's honest final copy — a proxy
that is *still* unusable after a completed load keeps reading as a real problem,
never as "loading" forever.

A developer verifies by loading the optimizer with a hydrated assumption library
and watching the cards show loading state until the bottom-bar "Loading history"
pill clears, and by the new view-model/view unit tests.

## Context References

Public refs:

- Direct maintainer request on 2026-07-07 (chat): the proxy-workflow cards on
  the draft optimize page render as if the assets have no data, then silently
  flip to configured once background loading finishes; surface the in-flight
  work so users don't act on provisional state.

Repo artifacts:

- `src/hyperopen/portfolio/optimizer/application/view_model/setup_history_assumption_cards.cljs` — card projection; `selected-proxy-rows` already computes a per-chip `:loading?` (but purely "not in usable set", so it can also mean "failed").
- `src/hyperopen/views/portfolio/optimize/setup_history_assumptions.cljs` — section + card views (status chip, Apply, collapsed rows).
- `src/hyperopen/views/portfolio/optimize/setup_history_assumption_panels.cljs` — regression estimate + diagnostics strip panels (pure card → hiccup; no logic change needed if the VM swaps the copy).
- State sources: `contracts/history-load-state-path` (`{:status :loading|:succeeded|:failed …}`) and `contracts/history-prefetch-path` (`{:queue [...] :active-instrument-id … :by-instrument-id {id {:status :queued|:loading|…}}}`).
- Existing loading affordances: bottom-bar "Loading history · N assets" pill (`setup_actions.cljs`), muted in-progress universe chip (`view_model/universe.cljs` `history-chip-display`), `animate-pulse` on the run banner.

## Plan of Work

All logic lands in the cards view-model (views stay dispatch-only projections):

1. **In-flight signal (VM).** Derive `load-in-progress?` from
   `history-load-state :status = :loading` OR a non-idle prefetch queue
   (`:active-instrument-id` set or `:queue` non-empty), and a per-instrument
   `in-flight?` from the prefetch `:by-instrument-id` status ∈ `#{:queued :loading}`
   (falling back to the global signal). Sharpen chip `:loading?` to
   `(and in-flight? (not usable))` so "loading" is only claimed while work is
   actually happening; an unusable proxy after a settled load is not "loading".
2. **Card flag.** `:history-loading?` = any selected proxy chip `:loading?`.
   While set: regression skip message becomes "Waiting for proxy history to
   load. The estimate updates automatically."; the covariance-window diagnostic
   cell reads "Loading history…" instead of "No usable native returns".
3. **Views.** Status chip renders a muted, pulsing "Loading history…" variant
   (with `data-loading="true"`) when `:history-loading?` and the card is not
   configured; Apply is additionally disabled while loading with a short
   explanatory line; the section header shows a pulsing aggregate banner with
   the count of loading cards.
4. **Tests.** Extend `view-model-history-assumption-cards-test` (loading vs
   settled semantics of chip/card flags and copy swaps) and
   `setup-history-assumptions-test` (chip variant, Apply gating, section banner).

## Validation and Acceptance

- Required gates: `npm run check`, `npm test`, `npm run test:websocket`
  (run together via `npm run gates`) must all PASS.
- Acceptance: with an in-flight history load (aggregate `:loading` or a
  non-idle prefetch queue) and a hydrated proxy entry, the card's status chip
  reads "Loading history…" (`data-loading="true"`), the regression panel says
  "Waiting for proxy history to load…", the covariance-window cell says
  "Loading history…", Apply is disabled with a waiting note, and the section
  shows the aggregate loading banner. With a settled load, none of that
  loading UI renders and the honest final copy returns — covered by
  `view-model-history-assumption-loading-test` and the two new cases in
  `setup-history-assumptions-test`.

## Progress

- [x] (2026-07-07) Explored card VM, views, prefetch/load-state model, tests.
- [x] (2026-07-07) VM: in-flight-aware chip `:loading?`, card `:history-loading?`, loading copy swaps.
- [x] (2026-07-07) Views: pulsing chip variant, Apply hold + hint, section loading banner.
- [x] (2026-07-07) Unit tests for VM + view.
- [x] (2026-07-07) Gates: `npm run check`, `npm test`, `npm run test:websocket` all PASS (via `npm run gates`).

## Surprises & Discoveries

- The old chip `:loading?` (`usable-ids` present and id not in it) claimed
  "loading" forever for a proxy that failed to load, and claimed nothing while
  `usable-ids` was still nil (i.e. during the exact window the user reported).
  The sharpened semantics fix both directions.
- No existing test asserted the old chip `:loading?` semantics, so the change
  is test-visible only through the new coverage.

## Decision Log

- Loading state is derived, not stored: everything comes from the existing
  `history-load-state` + `history-prefetch` state; no new app-state keys.
- The loading chip overrides EVERY status, including Configured: with
  `usable-proxy-ids` still nil mid-load, `assumption-complete?` skips the
  usable-history check, so a card can read Configured on data it hasn't seen —
  a chip claiming green there would be the same mid-flight falsehood in the
  other direction.
- Apply is held during loading because `complete?` judged against a not-yet
  loaded `usable-proxy-ids` set is exactly the mid-flight falsehood the user
  raced against; the hold carries visible copy so the disabled button reads as
  "wait", not "broken".

## Outcomes & Retrospective

Landed as planned; all three gates green. Follow-up candidate (not done): the
right-rail history-assumptions summary could also count loading cards.
