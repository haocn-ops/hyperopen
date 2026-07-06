# Optimizer return views: JSON export/import for desktop AI-agent workflows

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document must be maintained in accordance with `docs/PLANS.md` (and its detailed contract `/.agents/PLANS.md`).

## Purpose / Big Picture

Under the Maximum Sharpe goal the Return views panel is where the user's own expected-return forecasts enter the optimizer. Today those forecasts can only be typed one row at a time, which shuts out the workflow the maintainer described: users who run a proprietary forecasting model (often driven by a desktop AI agent) want to take the current universe out of the app as a file, let their agent fill in per-asset returns and confidences offline, and bring the result back in one step.

After this plan, the Return views panel (whenever views are live, i.e. the Black-Litterman return model) carries an Export and an Import button. Export downloads a versioned JSON file that lists every universe asset with its instrument id, display symbol, current provenance (`user` vs `implied`), the user's return (percent) where one exists, the implied baseline return as read-only context, and the confidence level — plus an embedded `instructions` string so an AI agent handed the bare file knows exactly which fields to fill and how. Import opens a file picker, validates the file, applies each filled entry as an authored user view (synced to the wallet's view library exactly as if typed), leaves untouched/blank entries alone, skips unknown assets, and reports what happened in plain language inside the panel ("Applied 12 views · 3 left implied · 1 unknown asset skipped").

This is observable in the running app (download a file, edit a number, re-import, watch the row flip to "Your view") and in unit tests over the pure export/import model, the new actions, and the panel hiccup.

## Context References

Public refs:

- Direct maintainer request (this session): make it possible for an AI agent to assist with return views — "a JSON or a CSV file could be downloaded that already has all those symbols … as well as what's implied … with the fields that can be changed … then reupload it to the optimizer page to quickly populate all those values." UX left to implementer judgment.

Repo artifacts:

- `src/hyperopen/account/spectate_watchlist_io.cljs` + `src/hyperopen/runtime/effect_adapters/spectate_mode.cljs` — the canonical file import/export pattern (pure envelope builder + merge, Blob/anchor download effect, hidden-input FileReader pick effect, versioned `{type, version, entries}` document).
- `src/hyperopen/views/portfolio/optimize/return_views_panel.cljs`, `src/hyperopen/portfolio/optimizer/application/return_views.cljs` — the provenance-honest Return views panel this plan extends.
- `src/hyperopen/portfolio/optimizer/actions/draft.cljs` — the existing row-edit actions whose authored-view shape and view-library sync the import must reproduce exactly (a view imported from a file is a user view, full stop).
- `docs/exec-plans/completed/2026-07-02-optimizer-return-views-consolidation.md` — established the view library and the "machine-seeded numbers must never pose as user forecasts" honesty rule this plan must respect.

Local scratch refs (non-authoritative): none.

## Design

### File format (export document, version 1)

```json
{
  "type": "hyperopen-optimizer-return-views",
  "version": 1,
  "exported-at": 1783468800000,
  "instructions": "Fill `return-percent` (annual expected return, in percent, e.g. 12.5) for any asset you have a forecast for; optionally set `confidence` to low|medium|high. Leave `return-percent` null to leave that asset on the implied baseline. Do not edit `instrument-id`. Re-import this file in the optimizer's Return views panel.",
  "entries": [
    {"instrument-id": "perp:BTC", "symbol": "BTC", "source": "user",
     "return-percent": 15.0, "implied-return-percent": 11.2, "confidence": "high"},
    {"instrument-id": "perp:ETH", "symbol": "ETH", "source": "implied",
     "return-percent": null, "implied-return-percent": 9.4, "confidence": null}
  ]
}
```

Key honesty decision: implied rows export with `return-percent: null` (the implied value rides in the read-only `implied-return-percent` field). Only non-null `return-percent` entries are applied on import, so re-importing an unedited export is a no-op — implied baselines can never silently become "user" views.

### Import semantics

- Accepts the full envelope or a bare entries array; keys may be strings or keywords; `return-percent` accepts numbers or numeric strings ("12.5", "12.5%").
- Entries resolve by `instrument-id` against the current universe; fallback: case-insensitive unique `symbol` match (so hand-built files work).
- Each resolved entry with a finite `return-percent` upserts an absolute user view (same shape as typing: id/kind/return/confidence-level/weight/variance) and a view-library upsert. Confidence: entry's value if valid, else the existing view's level, else medium.
- Null/blank `return-percent` ⇒ untouched (never a removal — removal stays an explicit in-app act). Unknown assets ⇒ counted and skipped. Unusable file ⇒ error note, state untouched.
- Applying also clears the per-row typing buffers for the applied instruments so stale buffer text cannot mask the imported value.

### Surfaces

- Pure model: new `src/hyperopen/portfolio/optimizer/application/return_views_io.cljs` — `export-payload` (filename, count, document) and `import-plan` (`{:status :ok :views … :upserts … :applied … :unchanged … :unknown …}` or `{:status :error :reason :invalid|:empty}`), plus note-message helpers. No DOM, no clock.
- Actions: new `src/hyperopen/portfolio/optimizer/actions/return_views_io.cljs` (draft.cljs is at its namespace-size cap) — `export-portfolio-optimizer-return-views` (builds rows exactly like the panel: universe + draft views + library + readiness-implied baselines), `import-portfolio-optimizer-return-views` (opens picker), `apply-imported-portfolio-optimizer-return-views` (runs the plan; saves draft views + syncs library + writes the feedback note), `dismiss-portfolio-optimizer-return-views-io-note`.
- Effects: `:effects/download-portfolio-optimizer-return-views-file` and `:effects/pick-portfolio-optimizer-return-views-file` in `runtime/effect_adapters/portfolio_optimizer.cljs`, mirroring the spectate adapters (Blob/anchor download; hidden input + FileReader dispatching the apply action).
- State: new `ui-return-views-io-note-path` (`[:portfolio-ui :optimizer :return-views-io-note]`, `{:kind :success|:error :message …}`) in contracts paths + alias + facade re-export.
- UI: in the panel's live-views body, a compact toolbar row (below the filter chips) with Export/Import buttons in the filter-chip idiom, `data-role` `<container>-export` / `<container>-import`; Export disabled when the universe is empty. The feedback note renders under the toolbar with a dismiss ×. Buttons only exist where views are live (Black-Litterman branch), so they can never export dead controls.
- Registration: binding rows in `schema/runtime_registration/portfolio.cljs`, wiring in `portfolio/optimizer/runtime_catalog.cljs` + `portfolio/optimizer/actions.cljs` facade, arg specs in `schema/contracts/action_args.cljs` / `effect_args.cljs`. None of the new actions joins the effect-order policy (same class as the existing row-edit actions), so no Lean/formal sync is needed.

## Progress

- [x] (2026-07-06) Exploration: panel/model/actions/effects/registration surfaces mapped; spectate watchlist IO confirmed as the file-IO pattern; draft.cljs size cap (965) confirmed ⇒ new action namespace.
- [x] (2026-07-06) Pure model `return_views_io.cljs` + unit tests (export payload incl. null-for-implied; import plan: envelope/bare-array, symbol fallback, percent strings, unknown/blank counting, confidence default/preserve, invalid/empty statuses).
- [x] (2026-07-06) Actions namespace + registration (catalog, facade, binding rows, arg specs) + `ui-return-views-io-note-path` + action tests.
- [x] (2026-07-06) Effect adapters (download + pick) in `portfolio_optimizer.cljs`.
- [x] (2026-07-06) Panel toolbar + feedback note + view tests.
- [x] (2026-07-06) Gates: `npm run gates` 34/34 PASS (5913 tests, 31583 assertions); namespace-size exceptions bumped for `action_args.cljs` (680→685) and `effect_adapters/portfolio_optimizer.cljs` (610→655).
- [x] (2026-07-06) Browser QA: smallest relevant Playwright spec `optimizer-black-litterman-views.spec.mjs` 5/5 at `--workers=1` (the spec that exercises the Return views editor surface this plan extends).
- [ ] Maintainer review; optional follow-up: CSV variant if agent workflows demand it.

## Surprises & Discoveries

- `parse-percent-text` (coercion) already accepts numbers, "12.5" and "12.5%" strings — import needs no bespoke number parsing.
- The existing row actions keep a per-row typing buffer (`ui-objective-menu-view-drafts-path`) that would visually mask imported values; the apply action must clear buffers for applied instruments (caught in design, verified by a test).
- `npm run lint:namespace-sizes` flagged only the two central contract-surface files (`schema/contracts/action_args.cljs`, `effect_adapters/portfolio_optimizer.cljs`); the panel, registration rows, and all new namespaces stayed under their caps.
- The panel test support (`optimizer-hiccup-*` helpers) lives in `test/hyperopen/views/portfolio/optimize/test_support.cljs` — searching hiccup by `data-role` is the established idiom, no DOM needed.

## Decision Log

- Decision: JSON only, no CSV (for now). Rationale: one canonical, versioned, lossless format; JSON is the native format for AI-agent workflows (the stated use case), carries the confidence enum and the embedded instructions cleanly, and halves the validation/test surface. CSV can be added later as a pure-parser extension if spreadsheet users ask.
- Decision: implied rows export `return-percent: null` with the implied value in a separate read-only field. Rationale: import applies only non-null returns, so a round-trip of an unedited file is a no-op — preserving the panel's core honesty rule that machine-seeded numbers never pose as user forecasts.
- Decision: import never removes views (null on a user row = leave alone). Rationale: a file is a bulk *fill* channel; destructive intent should stay an explicit in-app act (the per-row reset ×), and this makes partial files safe by construction.
- Decision: embed an `instructions` string in the export document. Rationale: the file is designed to be handed to an AI agent with no other context; self-description costs bytes and saves a support round-trip.
- Decision: feedback via an inline panel note (dismissable, overwritten by the next export/import) rather than the wallet toast. Rationale: the outcome ("Applied 12 · 1 unknown skipped") is panel-local information the user acts on right there; the global toast channel is for account-level feedback.
- Decision: new actions stay out of the effect-order policy set. Rationale: they are the same class as the existing row-edit actions (save-many + view-library sync), which are not in the set; adding them would drag in the Lean formal gate for no ordering benefit.

## Validation

- Unit: `npm test` — new namespaces `return-views-io-test` (pure), `actions.return-views-io-test` (actions incl. buffer-clearing and non-BL guard), panel toolbar/note render tests.
- Gates: `npm run gates` (check, test, test:websocket) — all green before landing (34/34, 5913 tests).
- Browser QA (manual, worktree build): export with a mixed universe → inspect file; edit two returns (one by symbol only) → import → rows flip to "Your view", note reads correctly; import a garbage file → error note, state untouched. Deterministic Playwright coverage deferred unless this surface regresses (the flow is FileReader/download-dialog heavy, which the repo's smoke suite avoids).

## Outcomes & Retrospective

Shipped 2026-07-06 (this worktree). Full surface: pure IO model (11 test groups over export/import), 4 actions + 2 effects registered through the whole contract surface, panel toolbar + inline note (8 action tests, 5 panel render tests). `npm run gates` 34/34 (5913 tests); Playwright BL-views spec 5/5. Residual risks: none known; the import path is pure up to the two save effects, and the library sync self-gates in spectate mode. Deferred: CSV variant; Playwright coverage of the picker/download flow.
