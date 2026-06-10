# HyperDegen voice layer (theme-keyed UI copy)

## Why

The `hyperdegen` theme (tokens: colors/type/radius, see `docs/THEMING.md`) matches the HyperDegen reference prototype's palette, but most of the prototype's identity is its parody copy — "TRADE (GAMBLE)", "Buy / Moon", "Positions (hope and dreams)". Tokens deliberately cannot carry copy. Direct user request on 2026-06-10: deliver the prototype experience behind the existing theme toggle (no fork, no separate build).

## Outcome

A pure copy catalog, `hyperopen.ui.voice`, keyed off the active theme: `hyperdegen` → the `:degen` voice, every other theme → `:default` (canonical strings, pixel-identical behavior). Switching themes in Settings → Appearance relabels the header nav (desktop/mobile/more), the account-info tabs (trade + portfolio), and the order-form buy/sell/submit buttons in the same gesture that recolors them. No new actions, effects, or persistence — the voice rides `[:ui :theme]`.

## Design

- `voice/labels`: `{label-key {:default s :degen s}}`. `:default` is the canon and is **sync-tested** against the surfaces that own those strings (`views.header.nav` items, `views.account-info.tab-registry/tab-labels`), so catalog and surface cannot drift.
- `voice/active-voice state` → voice keyword via a `theme->voice` map (extensible; a future explicit voice preference only changes this fn).
- Lookups: `(voice/label state k)`, `(voice/label-for voice k)` (missing voice falls back to `:default`; unknown key → nil), `(voice/nav-label state id fallback)`, `(voice/account-tab-overrides state)` (nil under the default voice).
- Wiring shapes:
  - Header: `views.header.vm` relabels nav items by `:id` before action decoration; views untouched.
  - Account tabs: `account-info-panel` merges voice overrides **under** caller `tab-label-overrides`, so context renames (portfolio's `:funding-history` → "Interest") win over voice.
  - Order form: `order-form-view` passes voice strings through the existing `side-row` label options and new `submit-row` `:submit-label`/`:submitting-label` keys (hardcoded strings remain as fallbacks for label-less callers).
- Memoization correctness: `trade_view` memoizes panel renders on selected state subsets; `ui-voice-state` (`{:ui {:theme …}}`) is merged into `account-info-view-state` and `order-form-view-state` so a theme switch busts those memos. Any future voiced surface rendered through a selected-state memo must include this slice.
- Counted tabs (`:balances :positions :outcomes :open-orders :twap`) use paren-free degen nouns ("Hope & Dreams") so registry count suffixes ("… (3)") compose.

## Status

- [x] (2026-06-10) `hyperopen.ui.voice` catalog + lookups; unit tests incl. nav/tab-registry sync tests and catalog hygiene.
- [x] (2026-06-10) Header nav wiring (desktop/mobile/more) + vm tests for both voices.
- [x] (2026-06-10) Account-info tab override merge (trade + portfolio call sites) + memo slice.
- [x] (2026-06-10) Order form buy/sell/submit wiring + memo slice.
- [x] (2026-06-10) Orderbook panel tabs voiced — `orderbook-tabs-row` grew a labels arity, `l2-orderbook-view` passes voice labels, `orderbook-view-state` carries the `{:ui {:theme …}}` slice.
- [x] (2026-06-10) Phase C decor — `hyperopen.views.degen.widgets` (gated on `voice/degen?`, tokens only, desktop lg+): trade-route stats strip (Total Value/Unrealized P&L from `account-equity-metrics`, liq-risk tier from margin ratio, market vibes from active-asset 24h%, NFA + CONGRATS gold dashed cards) and bottom widget row (Degen Tip day-rotated, Whale Watch, Daily Motivation, Feeling Gauge from real unrealized PNL); brand wordmark/mark voiced (`HyperDegen`/`HD`) through the header vm; emoji on buy/sell/submit labels. Gotcha: new Tailwind classes in new namespaces need `npm run css:build` (JIT) — a stale CSS bundle silently drops them.
- [x] (2026-06-10) Phase D decor — chart doodle overlay (`degen-widgets/chart-doodles`: pointer-events-none SVG annotations in `--font-marker` handwriting, threaded through trade-view `renderers` into the chart wrapper; `--font-marker` cursive stack in base.css), Shill of the Day card (real top 24h gainer via `top-gainer`, click dispatches the existing `:actions/select-asset-by-market-key`, explicit not-financial-advice copy, no buy CTA), tiered `leverage-warning-banner` under the order-form leverage row (20x/50x/100x, real ui-leverage).
- [ ] Follow-up — RESET LIFE interaction (needs a new action; avoid dead controls); mobile decor (everything is lg+ only); shell.cljs mobile surface tabs ("Order Book"/"Trades") still unvoiced (no state at that call site).

## Decisions

- Voice derives from theme rather than a separate preference: one toggle, one persistence path; revisit only if product wants voice and palette decoupled.
- The most specific override wins: caller `tab-label-overrides` > voice > registry default.
- Existing tests assert default-voice strings and stay untouched; degen assertions are additive.
