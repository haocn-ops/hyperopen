# Optimizer Proxy Loading: Rail Visibility + Bundle Normalize Performance

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Follow-up to `docs/exec-plans/completed/2026-07-07-optimizer-proxy-workflow-loading-visibility.md`:
the maintainer re-tested and the history-assumption surface still sat on
"Needs input" with no loading indication until ~17s, then silently flipped to
"Configured". A DevTools performance trace
(`Trace-20260708T084245.json`, spectate view of `/portfolio/optimize`) gives
the root cause on both axes:

1. **The visible "Needs input" text is the RIGHT-RAIL row status**
   (`setup_context.cljs` renders `(if configured? "Configured" "Needs input")`),
   which the 2026-07-07 pass never touched — only the cards section learned to
   show loading. The rail also claims "Ready to run" the moment every entry is
   `engine-applied?`, which mid-load (nil `usable-proxy-ids`) is a provisional
   verdict.
2. **The flip lands late because of real, measurable client+network cost**:
   the `POST /v1/optimizer/history-bundle` call runs 10.9s→17.1s (6.2s,
   backend latency; its start is itself delayed by a Hyperliquid `/info` 429
   backoff storm gating holdings→universe seeding), and the main thread then
   burns three long tasks: 1.7s @9.2s + 2.2s @17.1s both ~75% inside
   `history-loader.api-v2.codec/kebab-token` + `normalize-api-map` +
   `key->kebab-keyword` (regex kebab-casing EVERY key of EVERY candle point —
   ~50 assets × ~1100 points × ~6 keys of a tiny fixed vocabulary), and 1.6s
   @19.4s in `request-builder/align-history` (client-side alignment recompute).

After this change: (a) the rail rows read a pulsing "Loading history…" instead
of "Needs input"/"Configured" while a card's proxy history is in flight, and
the "Ready to run" line is suppressed until loading settles; (b) API key
normalization is memoized so the repeated-vocabulary kebab-casing collapses to
cache lookups, cutting the two normalize long-tasks (~4s of main-thread time)
by roughly the 50% that key conversion accounts for; (c) a Playwright case
holds the history-bundle response open and asserts the loading UI (card chip,
section banner, rail row) actually renders during the in-flight window and
settles afterwards — proving the end-to-end signal the maintainer found
missing, on whichever build serves the page.

Out of scope (recorded as debt): the 6.2s backend bundle latency, the `/info`
429 storm delaying load start, and moving `align-history` off the main thread.

## Context References

Public refs:

- Direct maintainer request on 2026-07-08 (chat), with performance trace
  `~/Downloads/Trace-20260708T084245.json`: "get to the root cause, fix the
  UX at least, and take the performance improvement if reasonable."

Repo artifacts:

- Parent ExecPlan: `docs/exec-plans/completed/2026-07-07-optimizer-proxy-workflow-loading-visibility.md`.
- `src/hyperopen/views/portfolio/optimize/setup_context.cljs` — rail row status.
- `src/hyperopen/portfolio/optimizer/application/view_model/setup_history_assumption_rail.cljs` — rail projection.
- `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/codec.cljs` — `kebab-token` hot path.
- `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/bundle.cljs` — per-point `normalize-api-map` calls.
- `tools/playwright/test/optimizer-history-assumptions-io.spec.mjs`, `optimizer-history-api-v2.spec.mjs` — existing route-mocked coverage to extend.

## Plan of Work

1. **Rail loading visibility.** Rail VM rows carry `:history-loading?` (from
   the cards projection) plus loading-aware values for the Status /
   History used / Calibration overlap pairs; `all-configured?` (and the
   "Ready to run" message) require no row loading. Rail view renders a pulsing
   muted "Loading history…" row status with `data-loading="true"`.
2. **Codec memoization.** Cache `key->kebab-keyword` (and the enum-vocabulary
   `keyword-like`) in a `js/Map` keyed by the raw string — the API key/enum
   vocabulary is finite, so the cache is bounded and turns ~300k regex
   pipelines per bundle into lookups.
3. **Unit tests.** Codec: cached conversion correctness (camelCase/snake_case,
   repeat keys, non-string keys). Rail VM: loading row semantics + ready-line
   suppression mirroring the cards tests.
4. **Playwright.** A route-mocked case that delays the history-bundle response
   and asserts, mid-flight: card status chip `data-loading`, section loading
   banner, rail row "Loading history…"; then releases the response and asserts
   the flip to Configured + ready line.
5. **Verification.** `npm run gates`; smallest relevant Playwright spec first;
   micro-benchmark of the memoized codec vs the regex pipeline to size the win.

## Validation and Acceptance

- Required gates: `npm run check`, `npm test`, `npm run test:websocket` (via
  `npm run gates`) must all PASS.
- Browser: the extended Playwright spec passes with the delayed-response
  loading assertions, run via the repo Playwright config.
- Acceptance: while the bundle request is in flight, the rail row for a
  hydrated proxy asset reads "Loading history…" (pulsing, `data-loading`)
  instead of "Needs input", no "Ready to run" line shows, and the cards
  section shows the banner/chips from the parent plan; after the response
  lands, the honest final labels return. Codec normalization produces
  byte-identical output with the cache in place (unit-tested) and no longer
  pays per-occurrence regex cost.

## Progress

- [x] (2026-07-08) Trace analysis: network timeline, long-task attribution via CPU profile samples.
- [x] (2026-07-08) Rail VM + view loading visibility (`:history-loading?` rows, loading-aware Status/History-used/Calibration pairs, ready-line suppression, pulsing rail status with `data-loading`).
- [x] (2026-07-08) Codec key/enum memoization (`js/Map` caches for `key->kebab-keyword` and `keyword-like`) + unit tests.
- [x] (2026-07-08) Playwright `optimizer-proxy-loading-ux.spec.mjs`: held bundle response → chip/banner/rail/apply-hold assertions → release → Configured settle. PASS.
- [x] (2026-07-08) `npm run gates` 34/34 PASS (5,986 tests / 31,858 assertions); io + api-v2 + loading-ux specs 5/5 PASS; micro-benchmark: 336k key conversions 57ms→4ms (14x) on the mechanical op, plus avoided per-key `keyword()` construction/hashing seen in the profile.

## Surprises & Discoveries

- The trace shows TWO full normalize passes (1.7s @9.2s before any visible
  bundle network, 2.2s @17.1s on response) — the early one is presumably a
  cached/first-phase bundle; both burn in the same codec path, so one fix pays
  twice.
- The `/info` 429 backoff storm (4 duplicate parallel POSTs per round,
  6.4s→14.9s) is what delays holdings→universe seeding and thus the bundle
  request start to 10.9s.

## Decision Log

- Fix the rail rather than only relying on the cards banner: the maintainer's
  wording ("needs input … flips to configured") matches the rail copy, and the
  rail is the always-visible summary on wide viewports.
- Memoize instead of restructuring `normalize-point` field reads: bounded
  vocabulary makes a `js/Map` cache equivalent in outcome and far lower risk
  than hand-rolling per-field extraction.
- Backend bundle latency, 429 dedup/backoff, and alignment-off-main-thread are
  recorded in the tech-debt tracker, not attempted here.

## Outcomes & Retrospective

Landed as planned. The end-to-end Playwright case proved the whole loading
story (cards chip + banner from the 2026-07-07 pass, rail row + ready-line
suppression from this pass) renders during a held bundle request and settles
to Configured afterwards — the 2026-07-07 card work was correct but invisible
to the maintainer because the rail is what they watch, and the branch was
never in their running build. The stale `optimizer-history-api-v2.spec.mjs`
expectation ("approved proxy history" → "approved substitute history",
copy changed in c8a801c5) was repaired in passing. Remaining latency causes
(backend bundle time, `/info` 429 storm, main-thread alignment) are recorded
in the tech-debt tracker.
