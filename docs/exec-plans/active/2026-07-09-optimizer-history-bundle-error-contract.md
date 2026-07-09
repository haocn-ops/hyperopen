# Consume the history-bundle error contract in the optimizer frontend

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. Maintain this document in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The hyperopen data service extended the `optimizer-history-api-v2` bundle contract after the 2026-07-08 degenerate-bundle incident (the client-side complement of that incident is `/docs/exec-plans/active/2026-07-08-optimizer-calendar-poisoning-universe-collapse.md`). The backend now does calendar-poisoning exclusion **server-side** and reports it explicitly, instead of shipping empty shared calendars that the client has to reverse-engineer. This plan teaches the cljs/re-frame frontend (`POST /v1/optimizer/history-bundle` consumer) to consume three additions to the contract (see `API_CONTRACT.md` "Status Values", "Warning Codes", and the aligned-returns section in the `hyperopen_data_service` repo):

1. **`excluded-from-alignment`** (severity `warning`, per-instrument **and** top-level): an instrument left out of `common_calendar`/`return_calendar`/`aligned_returns_by_instrument` because its served window can't meaningfully overlap the majority window. `details.client_instrument_id`, `details.reason` (`empty_series` | `window_disjoint_from_majority` | `insufficient_overlap_with_majority`), `details.window_start_ms`/`window_end_ms`. The excluded instrument is **omitted from `aligned_returns_by_instrument`** but **its per-instrument series is fully served**. The universe panel must surface these as by-exception chips (like the existing SUFFICIENT/STALE badges), **not** treat the instrument as missing.
2. **`common-window-empty`** (severity `error`, top-level) with bundle **`status: "error"`**: no shared return calendar could be computed at all; `details.instrument_ids` + `details.excluded_instrument_ids` name the offenders. Per-instrument series are still served — the UI must **not** treat `status: "error"` as an empty response, and must **not** retry-loop it.
3. Serve-time **`stale-history`** warnings now carry `details.served_end_ms` and `details.serve_age_days`, and **escalate to severity `error` at >= 7 days**. The STALE badge can read `serve_age_days` directly instead of recomputing age; a >= 7-day stale is a pipeline *incident*, not routine staleness, so it must stop being an all-clear/info note.

After this change: an excluded-from-alignment asset paints a by-exception "shared gap" chip (never "missing"), with an info-level readiness note that names it and its reason; a `status: "error"` bundle is stored and surfaced like any partial (series usable, no retry loop) with a top-level caution explaining the empty shared window; and a >= 7-day stale asset shows a visible amber "stale" chip and a readiness caution instead of a silently-folded note, using the backend `serve_age_days`.

## Context References

Public refs:
- Direct maintainer request (2026-07-09): "Handle new history-bundle error contract in UI … the frontend should handle three additions … See API_CONTRACT.md ('Status Values', 'Warning Codes', and the aligned-returns section)."
- Backend contract source: `/projects/hyperopen_data_service` `API_CONTRACT.md` (merged to `main` via `Merge fix/optimizer-history-degenerate-bundle`), sections "POST /v1/optimizer/history-bundle" (aligned-returns paragraph), "Status Values", "Warning Codes", "Cache And Retry".

Repo artifacts:
- `/docs/exec-plans/active/2026-07-08-optimizer-calendar-poisoning-universe-collapse.md` — the CLIENT-side defensive peel that this backend contract now makes authoritative. The client peel stays as a belt-and-suspenders fallback; this plan layers the explicit backend signal on top of it.

## Context and Orientation

Data path (all under `src/hyperopen/portfolio/optimizer/`):

- **Fetch/merge** — `infrastructure/history_api_v2_client.cljs` POSTs the bundle (chunked at 100 instruments) and folds chunk bodies with `merge-history-bodies`/`merged-status`. `request-json!` only rejects on HTTP `!ok`; a `status: "error"` bundle is HTTP 200, so it is **not** rejected and **not** retried. Verified: no history retry-loop exists anywhere.
- **Normalize** — `application/history_loader/api_v2/codec.cljs` (`normalize-warning`, kebab-cases every key incl. nested `details`, keywordizes `:code`) and `.../api_v2/bundle.cljs` (`normalize-history-body`: `:status` via `keyword-like`, `:series-by-instrument`, `:aligned-returns-by-instrument`, `:warnings`; per-series warnings tagged with the local id).
- **Store** — `application/history_workflow.cljs` `success-history-load-state` stamps `:succeeded` for ANY returned bundle regardless of `:status` (so `error` is not a failure); `application/history_cache.cljs` `history-data-worth-persisting?` keys usability off a non-empty `:series-by-instrument` (so `error` is cached as usable, not empty). Both already correct for addition #2.
- **Align** — `.../api_v2/alignment.cljs` `align-api-v2-history-inputs` re-derives alignment: it uses the backend aligned returns only when EVERY candidate has usable aligned returns (`use-aligned?`); otherwise it falls to a client point-level intersection + `peel-poisoning-members`. Because an excluded-from-alignment asset has NO aligned-returns entry, `use-aligned?` is false whenever any asset is excluded, so the client always re-derives in that case. Warnings flow through to the request `:warnings`.
- **Readiness** — `application/setup_readiness.cljs` `history-status-by-instrument` maps warnings → per-asset status via `application/history_warning_policy.cljs` `strongest-warning-status-by-id`; `warning-display-message` renders per-asset copy; `blocking-history-warnings` decides run blocking.
- **Universe chips** — `application/view_model/universe.cljs` `selected-history-status` → `history-chip-display` (by-exception: `#{:sufficient :stale}` render no chip). `:loaded-but-misaligned` readiness-status already maps to the `:shared-gap` amber chip.
- **Readiness panel** — `application/view_model/setup.cljs` `group-readiness-warnings`/`warning-severity` groups warnings by code into blocking/caution/info; `:stale-history` is currently forced to `:info` (folded).

Key vocabulary: `warning-history-status` outputs feed `history-status-by-instrument` (values: `:missing :insufficient :stale :rejected` today, plus `:aligned`/`:loaded-but-misaligned`); `selected-history-status` maps those to chip statuses (`:loaded-but-misaligned → :shared-gap`). `warning-status-severity` ranks multiple warnings on one asset.

## Milestones

- **M1 — Codec attribution & detail normalization** (`api_v2/codec.cljs`). `normalize-warning` keywordizes `details.reason` and, for `excluded-from-alignment` with a blank top-level `:instrument-id`, lifts `details.client_instrument_id` → `:instrument-id` so per-asset attribution works exactly like every other warning. `serve_age_days`/`served_end_ms`/`instrument_ids`/`excluded_instrument_ids`/`window_*_ms` are already kebab-normalized by `normalize-api-map`; no per-field work needed.
- **M2 — Warning policy** (`history_warning_policy.cljs`). `:excluded-from-alignment` → `:loaded-but-misaligned` (the "loaded but couldn't join the shared calendar" concept, which already paints the `:shared-gap` chip and invites the assumption workflow — never `:missing`). Add `stale-history-incident-min-age-days` (7), `serve-age-days`, and `stale-incident?` (severity `error` OR `serve_age_days >= 7`); stale codes map to `:stale-critical` when an incident, else `:stale`. Extend `warning-status-severity` with `:loaded-but-misaligned` (below `:insufficient`, so a real client `:insufficient`/`:missing` still wins) and `:stale-critical` (above `:stale`).
- **M3 — Universe chips** (`view_model/universe.cljs`). Add `:stale-critical` to `history-status-labels` ("stale") and a `selected-history-status` branch (`:stale-critical` readiness-status → `:stale-critical` chip); `:stale-critical` is NOT all-clear (renders an amber "stale" chip) and `history-adequacy` leaves it `:ok` (data present). `:loaded-but-misaligned → :shared-gap` needs no change. Bump the governed 560-line namespace-size exception with an honest reason.
- **M4 — Readiness copy & panel severity** (`setup_readiness.cljs`, `view_model/setup.cljs`). `warning-display-message` gains `:excluded-from-alignment` (per-reason sentence), `:common-window-empty` (top-level "no shared window") cases, and enriches `:stale-history` with `serve_age_days`. `view_model/setup.cljs`: escalate a stale group to `:caution` when it contains an incident; treat `:excluded-from-alignment` as an info note (the actionable card is the assumption warning); `:common-window-empty` stays a caution.
- **M5 — Client merge** (`history_api_v2_client.cljs`). `merged-status`: `:error` wins over `:partial` across chunks (a single error chunk must not be masked by a partial one). Confirm `history_merge.cljs` delta-merge status handling.
- **M6 — Tests & gates.** Unit tests for each addition (warning policy, codec attribution, universe chips, readiness panel severity, client merge). `npm run setup:worktree` then `npm run gates` PASS; `npm run test:runner:generate` registers any new namespaces.

## Validation and Acceptance

From the repo root: `npm run setup:worktree` then `npm run gates` — every row PASS. Behavior acceptance (verifiable via the new tests):
1. An `excluded-from-alignment` warning (top-level, attributed via `client_instrument_id`) yields per-asset history-status `:loaded-but-misaligned` → chip `:shared-gap`, never `:missing`; each `reason` renders a distinct readiness sentence.
2. A `status: "error"` / `common-window-empty` bundle is stored `:succeeded`, cached as usable (series present), never retried, and surfaces a top-level caution naming the offenders; the run's eligibility (not the error status) decides blocking.
3. A `stale-history` warning with `serve_age_days >= 7` (or `severity: "error"`) yields chip `:stale-critical` (amber "stale") and a readiness `:caution`; a `serve_age_days` of 2 stays chip `:stale` (no chip) and readiness `:info`.

## Progress

- [x] (2026-07-09) Explored the full data path; confirmed addition #2's runtime (store/cache/no-retry) is already safe, so #2 is surfacing-only.
- [x] (2026-07-09) M1 — codec attribution & detail normalization (`normalize-warning`) + `history_loader_api_v2_error_contract_test`.
- [x] (2026-07-09) M2 — warning policy: `:excluded-from-alignment → :loaded-but-misaligned`, `stale-incident?`/`serve-age-days`/`:stale-critical`, severity map extended; tests in `setup_readiness_warning_policy_test`.
- [x] (2026-07-09) M3 — universe `:stale-critical` chip status + label + branch; namespace-size exception 560 → 568; universe-chip tests in `view_model_error_contract_test`.
- [x] (2026-07-09) M4 — `warning-display-message` (excluded/common-window-empty/stale serve-age) + `warning-code-summary`; panel `warning-severity` escalates stale incidents to caution, excluded stays info; panel tests in `view_model_error_contract_test`.
- [x] (2026-07-09) M5 — client `merged-status` (`:error` wins over `:partial`) + regression test in `history_api_v2_client_test`.
- [x] (2026-07-09) M6 — `npm run gates` 34/34 PASS (6,036 tests, 32,034 assertions; incl. crap/mutation/namespace-sizes/boundaries and all six build compiles).
- [ ] Owner confirmation in a live UI session once a backend `excluded-from-alignment` / `status: error` bundle is served: excluded assets carry the shared-gap chip (not "missing"), a >= 7-day stale asset shows the amber "stale" chip + a readiness caution, and a `status: error` bundle neither empties the panel nor retry-loops.

## Surprises & Discoveries

- The frontend already re-derives alignment client-side (`peel-poisoning-members`, added 2026-07-08), so it was already resilient to the degenerate shape this contract formalizes. The backend `excluded-from-alignment`/`common-window-empty` signals are consumed as an explicit, better-attributed layer ON TOP of that fallback rather than replacing it — lowest risk, and honest even when the client's own peel and the backend disagree on the exact victim.
- `status: "error"` needed no store/cache/fetch changes: `success-history-load-state` ignores `:status`, `history-data-worth-persisting?` keys off series presence, and no retry-loop exists. Addition #2 is surfacing + `merged-status` robustness only.

## Decision Log

- **Map `excluded-from-alignment` → `:loaded-but-misaligned`, severity below `:insufficient`.** It IS "loaded but misaligned", already paints the by-exception `:shared-gap` chip and drives adequacy `:short` (invites a proxy/conservative assumption). Ranking it below the client's own `:insufficient`/`:missing` means it acts as a floor: when the client peel already produced a stronger, more-specific verdict for the same asset, that wins ("labels derive from what alignment DID" — the POL/BTC lesson); when it didn't, the backend signal still keeps the asset off `:missing`.
- **New `:stale-critical` status rather than a display-only flag.** Statuses drive every surface here; a keyword threads cleanly through `warning-history-status` → `history-status-by-instrument` → `selected-history-status`/`history-chip-display` and is unit-testable, at the cost of a tiny namespace-size bump on `universe.cljs`.
- **Do not add `excluded-from-alignment`/`common-window-empty` to `history-blocking-warning-codes`.** The run must be blocked by whether eligible assets remain (the client may peel to a viable subset), not by the presence of these diagnostics. `common-window-empty` reads as a caution; `excluded-from-alignment` reads as an info note (the assumption-required caution is the actionable one).

## Outcomes & Retrospective

Landed 2026-07-09. Seven source files (`api_v2/codec.cljs`, `history_warning_policy.cljs`, `view_model/universe.cljs`, `setup_readiness.cljs`, `view_model/setup.cljs`, `history_api_v2_client.cljs`, plus the `dev/namespace_size_exceptions.edn` bump) and four test surfaces (two extended, two new namespaces). `npm run gates` 34/34 PASS.

- The single biggest realization was that addition #2 (`status: "error"`) needed **no** store/cache/fetch change: `success-history-load-state` never inspects `:status`, `history-data-worth-persisting?` keys off `series-by-instrument` presence, and no history retry-loop exists. That turned a feared "handle the error status everywhere" task into a two-line `merged-status` robustness fix plus surfacing copy. Verifying the runtime before writing code saved a large, risky diff.
- Reusing the existing `:loaded-but-misaligned → :shared-gap` chip path for `excluded-from-alignment` (rather than inventing a new "excluded" chip) meant zero new rendering code and automatic consistency with the client-side common-gap fallback — the backend signal and the client peel converge on the same by-exception chip.
- Only `universe.cljs` was tight against its namespace-size cap (558/560); the `:stale-critical` chip status pushed it to 565, handled by bumping the governed exception to 568 with an honest reason, matching how the repo already treats these exception entries as feature changelogs.
- Left deliberately unchanged: the client-side `peel-poisoning-members` re-derivation. Now that the backend excludes poisoners server-side it is largely redundant, but removing it is a separate, riskier change; it stays as belt-and-suspenders and the new backend signals layer on top as a floor.
