# Optimizer history assumptions: JSON export/import for desktop AI-agent workflows

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document must be maintained in accordance with `docs/PLANS.md` (and its detailed contract `/.agents/PLANS.md`).

## Purpose / Big Picture

The "Proxy Workflow for Short-History Assets" section asks the user to configure, per thin-history asset, either a proxy basket (which similar assets drive its covariance) or a conservative standalone assumption. Picking good proxies is exactly the judgment an LLM with market knowledge is good at — but today the workflow is chip-by-chip manual, and an agent has no way in.

After this plan, the section carries an Export and an Import button, mirroring the Return views file workflow shipped 2026-07-06. Export downloads a versioned JSON template listing every asset that needs assumptions (plus already-configured ones for context), an authoritative `proxy-candidates` menu (verified long-history assets first, catalog entries marked unverified), the active optimization objective, a `history-policy` block, and an embedded `instructions` array written for an agent with zero codebase context — it explains the covariance mechanics (prior → ridge regression → confidence blend), what `relationship-strength` really controls (the idiosyncratic-risk floor: low 50% / medium 25% / high 15%), when to choose conservative, and the exact fields to fill. Import validates the completed file, authors each filled asset's assumption through the same funnels as hand-editing (draft write + wallet assumption-library sync + reference-instrument reconcile + history prefetch), and reports the outcome in an inline note. The agent's per-asset `rationale` is stored in the entry metadata and rendered on the card, so the user sees *why* the basket was chosen.

Observable in the running app (export with WLFI/PUMP/XPL flagged → hand the file to an agent → import → cards flip to Configured with rationale lines) and in unit tests over the pure IO model, the new actions, and the section hiccup.

## Context References

Public refs:

- Maintainer request (this session): clone the return-views agent IO pattern for the proxy workflow; instructions must be self-sufficient for a context-free agent (merged from a domain expert's prompt + engine mechanics); an explainer should tell users to hand the file to an agent.

Repo artifacts:

- `src/hyperopen/portfolio/optimizer/application/return_views_io.cljs` + `actions/return_views_io.cljs` + the download/pick wrappers in `runtime/effect_adapters/portfolio_optimizer.cljs` — the canonical precedent this plan clones (envelope, honesty invariants, inline note, registration surface).
- `src/hyperopen/portfolio/optimizer/actions/draft.cljs` — the per-asset assumption funnels (`save-assumptions+refs`) whose semantics the bulk import must reproduce (reference-only proxy resolution, prefetch, library sync, collapse-override cleanup). At its 965-line size cap, so nothing is added there.
- `src/hyperopen/portfolio/optimizer/domain/history_assumptions.cljs` + `domain/history_assumption_proxy.cljs` — thresholds (360/30), `relationship-strength → proxy-min-specific-risk-share`, blend math quoted in the instructions.
- `docs/exec-plans/active/2026-07-06-optimizer-return-views-agent-io.md` — decision log this plan inherits (JSON only; import never removes; instructions embedded; inline note not toast).

Local scratch refs (non-authoritative): none.

## Design

### File format (export document, version 1)

```json
{
  "type": "hyperopen-optimizer-history-assumptions",
  "version": 1,
  "exported-at": 1783468800000,
  "instructions": ["# What this file is", "…", "…"],
  "history-policy": {"minimum-history-days": 360, "preferred-history-days": 720, "candle-interval": "1d"},
  "optimization-objective": "minimum-variance",
  "objective-uses-returns": false,
  "assets": [
    {"instrument-id": "perp:WLFI", "symbol": "WLFI", "name": "World Liberty Financial", "kind": "perp",
     "native-days": 12, "status": "needs-assumption",
     "approach": null, "proxies": [], "relationship-strength": null,
     "expected-return-percent": null, "rationale": null}
  ],
  "proxy-candidates": [
    {"instrument-id": "perp:BTC", "symbol": "BTC", "name": "Bitcoin", "kind": "perp",
     "native-days": 1460, "history-status": "verified"},
    {"instrument-id": "perp:AAVE", "symbol": "AAVE", "name": "Aave", "kind": "perp",
     "native-days": null, "history-status": "unverified"}
  ]
}
```

- `instructions` is an ARRAY of markdown strings (readable in a pretty-printed file; the return-views single-string instruction does not scale to this prompt's length).
- Already-configured assets export their current configuration (round-trip fidelity + worked examples for the agent). Unconfigured assets export `approach: null`.
- Verified candidates = universe members + loaded reference instruments with ≥360 native daily observations that do not themselves carry an assumption entry (mirrors the engine's `usable-proxy-id-set` stance: a synthetic row cannot anchor another). Unverified candidates = the live perp/spot catalog (volume-descending), minus universe/reference ids. Vaults are excluded from the export menu (address-labeled, agent-hostile; still pickable in-app).

### Import semantics

- Accepts the full envelope or a bare `assets` array; string or keyword keys; a leading/trailing markdown code fence is stripped before `JSON.parse` (agents fence JSON no matter what the instructions say).
- Target assets resolve by `instrument-id` against the draft universe, falling back to a case-insensitive unique symbol match. Unresolvable → counted `:unknown`, skipped. Any universe asset named in the file may be configured (matches the manual "+ Model an asset with proxies…" entry point), not just flagged ones.
- `approach: null` ⇒ untouched (`:unchanged`). Import NEVER clears an assumption — forgetting stays the card's explicit Clear.
- `approach: "proxy"`: proxy refs resolve by id/unique-symbol against universe + reference instruments + the perp/spot catalog; unresolvable and self-proxy refs are dropped (counted); the basket is deduped and clamped to 4; an empty post-drop basket ⇒ the asset is counted `:invalid` and skipped. Weights are all-or-nothing: every resolved member carries a finite ≥0 weight with positive total ⇒ normalized to sum 1 and stored as `:prior-weights` (first surface to author explicit priors — the draft key was reserved for exactly this); anything else ⇒ nil (equal weight).
- `approach: "conservative"`: seeds `default-assumption`, honoring an optional `expected-return-percent`. Guardrail fields (volatility/cap) stay auto-set app-side.
- Entry building starts from the existing entry when the behavior matches (guardrail edits survive), else from `default-assumption` preserving return/vol like the mode-switch action. Metadata becomes `{:source :agent-import :acknowledged? true (+ :rationale)}` — acknowledged means imported cards collapse to their green Configured summary exactly like a hand Apply.
- Round-trip no-op: the rebuilt entry is compared to the existing one MODULO `:metadata` (plus rationale equality), so re-importing an unedited export applies nothing.
- Apply is one BULK funnel (single `save-many` incl. dirty flag, one assumption-library sync carrying every upsert, one reference-instrument reconcile, one prefetch batch for newly referenced out-of-universe proxies, one collapse-override cleanup) — N sequential per-asset funnels would each read stale state and clobber one another.
- Post-import validation stays honest and asynchronous: prefetched history, readiness, and the engine's proxy validation decide final usability; failures surface as the normal per-card blocking warnings. The success note says validation continues.

### Surfaces

- Pure model: new `src/hyperopen/portfolio/optimizer/application/history_assumptions_io.cljs` — `export-payload`, `import-plan`, `strip-code-fences`, note-message helpers, the instructions text. No DOM, no clock.
- Actions: new `src/hyperopen/portfolio/optimizer/actions/history_assumptions_io.cljs` (draft.cljs is at its size cap) — `export-…` (assembles rows via the same universe/readiness helpers the cards use), `import-…` (opens picker), `apply-imported-…` (bulk funnel), `dismiss-…-io-note`.
- Effects: new ns `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_io.cljs` (the existing adapter facade is at its cap) — `:effects/download-portfolio-optimizer-history-assumptions-file`, `:effects/pick-portfolio-optimizer-history-assumptions-file` (fence-tolerant read, dispatches the apply action).
- State: new `ui-history-assumptions-io-note-path` (`[:portfolio-ui :optimizer :history-assumptions-io-note]`).
- UI: new `src/hyperopen/views/portfolio/optimize/setup_history_assumptions_io.cljs` (toolbar + note, return-views idiom); rendered from the section between header and add-select. Export disabled when no assets are in the workflow. Cards gain a rationale line (`:rationale` from entry metadata via the card view-model).
- Registration: runtime catalog (4 actions, 2 effects via the new adapter ns), binding rows, facade re-exports, arg specs (`action_args.cljs` cap bump), `effect_args.cljs` additions.
- None of the new actions joins the effect-order policy set (same class as the return-views IO actions) ⇒ no Lean/formal sync.

## Progress

- [x] (2026-07-07) Exploration: precedent + assumption workflow + registration surface mapped; caps confirmed (draft.cljs 965, adapter facade 667, action_args 686 — all at cap ⇒ new namespaces); entry spec confirmed to admit `:metadata` extras and id-keyed `:prior-weights`.
- [x] (2026-07-07) ExecPlan authored.
- [x] (2026-07-07) Pure model `history_assumptions_io.cljs` (459 lines incl. the instructions array) + 17 deftests (envelope, tiers, round-trip no-op through real JSON serialization, weight normalization, clamping, fence-stripping, messages).
- [x] (2026-07-07) Actions namespace (bulk import funnel) + registration (catalog, facade, binding rows, arg specs, note path) + 8 action tests.
- [x] (2026-07-07) Effect adapter ns `portfolio_optimizer_io.cljs` (download + fence-tolerant pick).
- [x] (2026-07-07) Section toolbar + note (new `setup_history_assumptions_io.cljs` view ns) + card rationale line + 5 panel tests.
- [x] (2026-07-07) Gates: `npm run gates` 34/34 PASS (5,966 tests / 31,755 assertions, twice — before and after the catalog-cap fix); only `action_args.cljs` needed a size-exception bump (686→691).
- [x] (2026-07-07) Browser QA on the worktree build (:8090 static SPA recipe): policy-pane regression spec PASS; NEW committed spec `optimizer-history-assumptions-io.spec.mjs` (2 tests: full export→agent-edit→fenced-import round trip against the live catalog incl. reference-instrument storage + rationale render; garbage-file rejection) PASS ×3 at `--workers=1`.
- [x] (2026-07-07) Maintainer feedback revision: catalog tier removed from exports (user-selected candidates only); two scoped export buttons ("Export proxy assets" / "Export universe", blocklist-aware); `export-scope` + scoped filenames in the document. Gates re-run 34/34 (5,969 tests / 31,770 assertions; `action_args.cljs` cap 691→694); IO spec + policy-pane PASS against the rebuilt bundle with the live catalog streaming (exported candidates stayed exactly the user's universe).
- [x] (2026-07-07) Maintainer feedback revision 2 ("Export proxy assets still exports the entire universe"): diagnosed — the `assets` target list was correctly scoped (proved: it derives from the same `build-readiness` as the visible cards, now guarded by `export-workflow-assets-match-the-visible-cards-test`); the "entire universe" was the `proxy-candidates` pool, which listed every universe asset. Fix: `proxy-candidates` is now VERIFIED assets only (short-history assets can't anchor a proxy and are dropped), and the universe export omits `proxy-candidates` entirely (its `assets` list already names every asset with a per-asset `history-status`, so the agent proxies from the verified entries). Gates 34/34 (5,970 tests / 31,776 assertions); IO spec PASS ×2-each against the rebuilt bundle (BTC seeded with a year of candles reads verified; WLFI short → not a candidate).
- [x] (2026-07-07) Maintainer feedback revision 3 (live test on a 51-asset universe: the verified menu was still ~48 rows, "not currently in the workflow"): live nREPL probe confirmed those 48 were exactly the universe minus the 3 flagged assets — as-designed, but the design was wrong. Final shape: NO `proxy-candidates` menu in EITHER scope — "Export proxy assets" contains only the workflow assets, "Export universe" only the universe assets; the agent proposes proxies from its own market knowledge, and the instructions lean on the existing import validation (unknown/short-history proxies dropped and reported, basket renormalized, engine re-validates). Gates 34/34 (5,969 tests / 31,776 assertions); IO spec PASS ×2-each.
- [ ] Maintainer review; follow-up candidates: render `rationale` in the collapsed-card summary row; optional export-time candle prefetch to widen the verified tier.

## Surprises & Discoveries

- The assumption entry contract spec (`contracts/specs.cljs`) already tolerates `:metadata` extras and reserves `:prior-weights` as a nilable id-keyed map — import can author explicit priors without a schema change, exactly as the 2026-07-05 comment intended.
- Assumption cards have NO typing buffers to clear (percent inputs render `:input-text` straight from the entry), unlike return views; the only UI residue import must clean is per-card collapse overrides.
- A per-asset import CANNOT reuse `save-assumptions+refs` in a loop: each call derives the whole assumptions map from pre-dispatch state, so later effects would clobber earlier ones. The bulk funnel recomputes once (map, refs, prefetch, sync) — covered by a dedicated action test.
- `filter-and-sort-assets` with a blank query returns the full catalog volume-sorted — the unverified tier costs one call, no new accessor.
- A hand-Apply after import replaces the whole metadata map (`{:source :user :acknowledged? true}`), dropping the agent rationale. Acceptable: re-acknowledging is re-authoring. Noted here rather than changed (the alternative touches at-cap draft.cljs).
- The live catalog streams ~1,550 markets into the static build mid-test — the first Playwright run dumped them all into `proxy-candidates` and made bare-symbol proxy refs ambiguous (BTC resolves uniquely against a 2-market seed, not against perps+spots). Two product consequences, not test hacks: the unverified tier is now explicitly capped at the 300 most liquid markets (stated in the instructions — no silent cap), and the instructions' "copy candidates exactly" rule is what the spec itself now follows (instrument-id refs).
- `clj->js` (used by the Playwright `readOptimizerState` helper) drops keyword namespaces, so the discovery decoration `:optimizer-history/instrument-id "hl:perp:SOL"` shadows `:instrument-id "perp:SOL"` in the JS view of a reference instrument. The draft state was correct all along; the spec asserts on `:coin`. Anyone reading optimizer state through `clj->js` should expect this collision.
- Unloaded history reads `:pending`, which (correctly) never flags a card or an export row — action-test fixtures must enroll via a draft entry, not via bare short-history universes.

## Decision Log

- Decision: all new logic in new namespaces (`application/…-io`, `actions/…-io`, `effect_adapters/portfolio_optimizer_io`, `views/…/setup_history_assumptions_io`). Rationale: draft.cljs, the adapter facade, and action_args.cljs sit exactly at their size caps; only action_args (the unavoidable central contract map) gets a bump.
- Decision: `instructions` is an array of markdown strings. Rationale: ~70 lines of prompt in one JSON string is unreadable in the pretty-printed file the user will actually open; an array renders line-per-element and agents consume either equally well.
- Decision: two-tier `proxy-candidates` with `history-status` verified/unverified instead of dumping the raw catalog or prefetching everything. Rationale: verified-by-construction enforces the expert prompt's mandatory history gate where we can know it; the catalog tier keeps sector reach (SOL/AAVE/PEPE even when not in the universe) with honesty about what is validated only after import. Export-time candle prefetch for the top-N catalog names is a follow-up, not v1 (adds latency + rate-limit exposure to an instant click).
- Decision: vaults excluded from the export candidate menu. Rationale: address-labeled entries are noise to an LLM; in-app picking still offers them.
- Decision (SUPERSEDED same day): the unverified catalog tier ships the 300 most liquid markets. Superseded by maintainer feedback below.
- Decision (maintainer feedback, 2026-07-07): the export carries NO exchange-catalog assets at all — the candidate menu is the user's included universe plus previously picked reference proxies, `history-status` verified (≥360 measured days, not assumption-modeled) or unverified (shorter/still-loading). Rationale: hundreds of unloaded `native-days: null` catalog rows polluted the agent's context with assets the user never considered. Import resolution deliberately stays catalog-tolerant (same reach as the in-app proxy typeahead) so hand-crafted files can still reference out-of-universe proxies.
- Decision (maintainer feedback, 2026-07-07): TWO export scopes as separate buttons — "Export proxy assets" (`:proxy-workflow`, just the flagged/carded assets) and "Export universe" (`:universe`, every included = non-blocklisted universe asset, so the agent can also flag assets IT judges untrustworthy). One action with a scope argument; the document records `export-scope` and the filename carries the scope suffix. Blocklisted assets leave both the universe rows and the candidate menu.
- Decision (maintainer feedback 2, 2026-07-07): the `assets` TARGET list is provably the visible workflow cards (shares `build-readiness` with the render path; guarded by a test), NOT the universe — so "Export proxy assets" over-exporting was the `proxy-candidates` POOL, which listed the whole universe. `proxy-candidates` is now VERIFIED assets only — a short-history asset is the thing that NEEDS a proxy and the engine's `usable-proxy-id-set` forbids a synthetic row anchoring another, so offering short-history assets as candidates was both noisy and wrong. And the universe export drops `proxy-candidates` outright: its `assets` list already enumerates every asset with a per-asset `history-status`, so a separate menu would only duplicate it — the agent proxies from the verified `assets` entries. Every asset row now carries `history-status` (verified/unverified) for this reason.
- Decision (maintainer feedback 3, 2026-07-07, FINAL candidate stance): NO `proxy-candidates` menu in either scope. On a real 51-asset universe the verified menu was still ~48 rows of assets "not in the workflow" — the maintainer's stated contract is that each file contains exactly its scope's assets and nothing else. The agent proposes proxies from its own market knowledge of Hyperliquid-listed assets; safety moves from menu-restriction to the import/engine validation that already existed (unknown symbols and short-history proxies are dropped and counted in the note; the surviving basket renormalizes; readiness re-validates with per-card warnings). Instrument-id references are recommended in the instructions as the unambiguous form. Universe-scope files still let the agent prefer in-universe proxies via each asset's `history-status`.
- Decision: import clamps baskets to 4 members and normalizes weights all-or-nothing. Rationale: matches the embedded instructions exactly (1–4, weights ≥0 summing to 1, omit-all for equal weight); tolerant of agent drift without silently accepting garbage (partial weights → equal-weight, not a guess).
- Decision: imported entries arrive acknowledged with `:source :agent-import` and the rationale in metadata, rendered on the card. Rationale: import is bulk authoring (the return-views stance); the rationale is the trust artifact that lets the user audit the agent's choice at a glance.
- Decision: round-trip equality ignores `:metadata`. Rationale: hand-authored entries carry `:source :user`; without masking, an unedited re-import would rewrite every configured asset just to change provenance — violating the inherited "unedited round-trip is a no-op" invariant.
- Decision: import never removes an assumption (`approach: null` = leave alone). Rationale: inherited from return-views — a file is a bulk fill channel; destructive intent stays an explicit in-app act (Clear).
- Decision: fence-stripping lives in the import path (pure helper, applied in the pick effect). Rationale: agents wrap JSON in ``` fences despite instructions; tolerating it costs three lines and saves the user a baffling "not valid JSON" failure.
- Decision: new actions stay out of the effect-order policy set ⇒ no Lean formal sync. Rationale: same effect classes as the return-views IO actions (save-many + library sync + save), which are not in the set.

## Validation

- Unit: `npm test` — new namespaces `application.history-assumptions-io-test` (pure export/import model), `history-assumptions-io-actions-test` (bulk funnel, notes, picker), `views…setup-history-assumptions-io-panel-test` (toolbar/note/rationale hiccup).
- Gates: `npm run gates` (check, test, test:websocket) — must be all green before landing.
- Browser QA (worktree build): export with flagged assets → inspect file (instructions array, tiers, objective); edit as an agent would (one proxy basket with weights + rationale, one conservative, one by symbol only) → import → cards configure, note reads correctly, library survives reload; import a garbage file and a fence-wrapped file. Smallest relevant Playwright spec for the optimizer setup surface first, then broaden only if needed.

## Outcomes & Retrospective

Implemented 2026-07-07 (this worktree), pending maintainer review; revised twice same day on maintainer feedback — (1) catalog tier removed, candidates are user-selected assets only, two scoped export buttons; (2) candidates narrowed to VERIFIED assets only and dropped entirely from the universe export (redundant with its per-asset `history-status`), after the maintainer reported "Export proxy assets" still carrying the whole universe (which was the candidate pool, not the correctly-scoped target list). Shipped surface: pure IO model with the embedded agent-instructions array (19 deftests incl. a round-trip-through-real-JSON no-op test), 4 actions (export takes a scope argument) + 2 effects through the whole contract surface via new namespaces (only `action_args.cljs` needed a cap bump, 686→694), section toolbar (Export proxy assets / Export universe / Import JSON) + inline note + card rationale line, and a committed Playwright spec covering both scoped downloads, the live fenced-import round trip, and garbage rejection. Validation: `npm run gates` 34/34 (5,969 tests / 31,770 assertions); Playwright policy-pane + both IO tests green at `--workers=1` against the :8090 worktree build with the LIVE market catalog streaming (exported candidates stayed exactly the user's universe). Residual risks: agent-picked unverified candidates validate asynchronously by design (surfaced as the normal per-card blocking warnings — the import note says so); a hand re-Apply replaces agent rationale (documented in Surprises). Deferred: rationale in the collapsed summary row; export-time candle prefetch to widen the verified tier.
