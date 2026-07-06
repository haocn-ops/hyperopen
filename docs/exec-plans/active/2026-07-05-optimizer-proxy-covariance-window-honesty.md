# Proxy assumption cards: report the covariance window, not the calibration overlap

Date: 2026-07-05
Status: in progress
Surface: `/portfolio/optimize/new` history-assumption cards + right rail
(view-model `view_model/setup.cljs`, card view `setup_history_assumptions.cljs`,
request-builder regression coverage)

## Purpose / Big Picture

Owner report (2026-07-05 session): after configuring SOPH with ETH + BTC
proxies, the card's "History window: 248 days of returns" read as "the model
only uses 248 days of history", which defeats the entire point of proxies —
extending a short-history asset's risk links with the proxies' long history so
the shared covariance window is never truncated by the young asset.

The engine already does the right thing. When a proxy assumption is complete,
`request_builder.cljs` excludes the asset from the shared alignment (so its
short calendar cannot shrink anyone's estimation window) and
`domain/history_assumption_proxy.cljs` synthesizes its covariance row from the
proxies' FULL-window covariance (`Cov(X, A) = Σ βⱼ·Cov(Pⱼ, A)`). The asset's own
short overlap is used only to calibrate the basket weights (confidence-shrunk
ridge regression), the residual-variance input to specific risk, and the
confidence tier.

The defect is the diagnostic: the card's "History window" cell (and the right
rail's "History used" line) display `native-history-observations` — the
calibration overlap — labeled as if it were the model's history window. The fix
is to report the real covariance window (the aligned shared window's
`:return-observations`, which the risk model actually estimates over) as the
headline, and demote the overlap to what it is: the calibration sample.

Explicitly out of scope (owner may revisit): a "trust basket as-is" mode that
pins β to the prior weights and skips the regression, and any form of synthetic
series splicing/backfill (rejected: it feeds Ledoit-Wolf fabricated
observations as if real, erases idiosyncratic risk where we know least, and
creates a regime seam at the listing date — the existing synthesis reaches the
same window extension honestly).

## Context References

- Direct maintainer request (2026-07-05 session): "The whole point of using
  these proxies is that we get an extension in the amount of data … and not be
  truncated in our covariance matrix history because of an asset with a short
  window history like SOPH."
- Parent feature: `docs/exec-plans/active/2026-07-05-optimizer-proxy-history-assumptions.md`
  (engine-backed proxy behavior; alignment-exclusion design this plan makes
  visible).
- Data source: the aligned history result already computes the exact quantity
  (`[:history :history-window :return-observations]` on the readiness request,
  built by `application/history_loader/window.cljs` and covered by
  `history_window_test.cljs`).

## Progress

- [x] View-model (`application/view_model/setup.cljs`)
  - `history-assumption-cards` reads the shared covariance window
    (`[:request :history :history-window :return-observations]`) once and
    passes it to every card; cards expose it as `:covariance-observations`.
  - `proxy-diagnostics` third cell becomes "Covariance window": value is the
    shared-window observation count (falling back to the overlap count while
    history is still loading), detail says "Extended via proxies · N-day
    overlap calibrates weights" (or "no native overlap — prior weights only").
  - `rail-summary-pairs`: for proxy cards "History used" reports the covariance
    window ("… · via proxies") and a new "Calibration overlap" pair carries the
    native overlap; conservative cards keep the native count unchanged.
- [x] Regression coverage
  - `request_builder_test.cljs`: strengthen the existing window assertion into
    the equivalence form — the aligned return calendar with a complete proxy
    asset in the universe EQUALS the calendar when that asset is absent
    entirely — and pin `[:history :history-window :return-observations]` (the
    exact field the card displays).
  - `view_model_setup_boundary_test.cljs`: proxy-card test asserts the
    "Covariance window" cell and `:covariance-observations`; rail test asserts
    the "History used … via proxies" + "Calibration overlap" pairs.
- [x] Poisoned-calendar recovery (`history_loader/api_v2/alignment.cljs`) —
  found live by the honest label, same day. The api-v2 fetch covers the FULL
  draft universe (assumption assets + reference proxies included), so the
  BACKEND's `common_calendar`/`return_calendar` are intersected over a superset
  of the alignment universe; adopting them re-introduced exactly the truncation
  the alignment exclusion exists to prevent (owner's live instance: 248
  observations while the members' own series supported 331). Fix: when the
  response covers instruments outside the alignment members AND the members'
  own point-level intersection is longer than the backend return calendar, the
  response calendars are poisoned — recompute both calendars client-side.
  - Regression pins: `history_window_test` (poisoned superset response
    re-intersected to the members' 9-observation window) and
    `request_builder_test` end-to-end (production topology: main alignment
    superset-poisoned recovers 399 observations while the regression
    sub-alignment — no superset — keeps the backend overlap).
- [ ] Deferred (owner call): optional "trust basket as-is" control that pins
  β to the prior weights (engine already supports q = 0 cleanly). Not started;
  revisit only if the owner asks for it after living with honest diagnostics.

## Surprises & Discoveries

- The honest label immediately exposed a REAL engine-window bug on the owner's
  live instance: both cells read 248 because the shared window itself was 248.
  The client excluded the thin asset from alignment rows but adopted the
  backend's response-level calendars, which the backend intersects over every
  instrument in the fetch — the exclusion removed the asset's ROW but kept its
  poisoned CALENDAR. The legacy/fixture path recomputes calendars client-side,
  which is why every existing test showed the full window while production
  truncated.
- Live before/after (owner session, nREPL): covariance window 248 → 331
  observations; the limiting instrument is now honestly attributed to the
  youngest allocatable asset (`perp:VVV`, starts-later) rather than silently
  bound by SOPH. Bonus: the regression sub-alignment also escaped the
  weekday-poisoned backend calendar, so SOPH's calibration overlap rose from
  248 to 373 all-days crypto observations.
- The recovered window (331) is still bounded by weekday-only HIP-3 stock
  proxies and the youngest allocatable asset — inherent to a single shared
  estimation window, now visible via the limiting-instrument attribution.
- The no-truncation property was already asserted in
  `build-engine-request-engine-backs-complete-proxy-assumptions-test`
  (399-observation window kept while the thin asset covers 10 days), but only
  as a magic count — the equivalence form (window with the proxy asset ==
  window without it) is what the owner actually cares about and is now pinned
  directly.
- The aligned history result already carried a purpose-built
  `:history-window` map with `:return-observations` and limiting-instrument
  attribution; no new plumbing was needed to expose the honest number.
- In the dev environment the two cells coincide (373 = 373): the CoinGecko
  demo tier caps the shared window at ~1 year, and SOPH is ~403 days old, so
  its calibration overlap saturates at the full window. Not a sourcing bug —
  the detail line still renders from the window value, and the divergence case
  (399-day window vs 9-day overlap) is pinned by the boundary and
  request-builder tests.

## Decision Log

- Headline number = the shared window's `:return-observations` (covariance
  sample count), not `:return-days` or calendar days: the card's unit is
  "days of returns" and the regression-confidence tier is also
  observation-based, so the two cells stay comparable.
- Keep the diagnostics strip at three cells (replace, don't append): the card
  layout in the screenshot is a 3-across grid; the overlap moves into the
  cell's detail line instead of claiming a fourth cell.
- Fall back to the overlap count (old behavior) when the readiness request has
  no aligned window yet (history still loading) rather than showing "--":
  the cell keeps a stable meaning ("what the model can see right now").
- Rejected splicing/backfill of a synthetic return series for the thin asset
  (owner's literal first framing): statistically dishonest next to the
  existing Stambaugh-style synthesis; documented in Purpose so it is not
  re-proposed later.
- Poisoning detection is STRUCTURAL (response covers instruments outside the
  alignment members) AND-ed with a strict improvement test (members' own
  point-level intersection strictly longer), not a count heuristic alone: in
  the normal no-assumption case the response equals the members, so behavior
  is bit-identical and backend-aligned returns (validated, canonical) keep
  precedence. Rejected the alternative of splitting the fetch into
  alignment-members vs series-only requests: correct in principle but heavier
  (chunk-merge calendar semantics, workflow/classifier plumbing, more
  requests) for the same window.
- The same rule self-selects correctly for the regression sub-alignment
  ({thin asset ∪ proxies} against the same response): the response is a
  superset there too, and the point-level overlap (all-days crypto) is longer
  than the weekday-poisoned backend calendar, so calibration also improves.

## Outcomes & Retrospective

- The proxy card now answers the owner's question directly: SOPH shows the
  real shared covariance window as "Covariance window" with the calibration
  overlap demoted to a detail line, and the right rail mirrors it.
- The honesty pass turned out to be load-bearing: it surfaced that the api-v2
  aligned path really was truncating the shared window to the thin asset's
  life (the exact failure the owner described), invisible until the UI
  reported the true number. Diagnostics honesty found an engine bug the
  green suite could not — the fixture path computed calendars client-side
  and so never exercised the poisoned-adoption branch.
- Live outcome on the owner's instance: covariance window 248 → 331
  observations, SOPH calibration overlap 248 → 373, limiter attribution
  moved to the true binding asset.
- Validation: `npm run gates` (34/34 PASS) after each milestone; live
  before/after verified over the owner's nREPL session.

## Validation

- `npm run check`
- `npm test`
- `npm run test:websocket`
(all three via `npm run gates`)
