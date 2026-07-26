# HyperDegen voice layer (theme-keyed UI copy)

## Purpose

The `hyperdegen` theme (tokens: colors/type/radius, see `docs/THEMING.md`) matches the HyperDegen reference prototype's palette, but most of the prototype's identity is its parody copy — "TRADE (GAMBLE)", "Buy / Moon", "Positions (hope and dreams)". Tokens deliberately cannot carry copy. Direct user request on 2026-06-10: deliver the prototype experience behind the existing theme toggle (no fork, no separate build).

## Outcomes & Retrospective

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

## Progress

- [x] (2026-06-10) `hyperopen.ui.voice` catalog + lookups; unit tests incl. nav/tab-registry sync tests and catalog hygiene.
- [x] (2026-06-10) Header nav wiring (desktop/mobile/more) + vm tests for both voices.
- [x] (2026-06-10) Account-info tab override merge (trade + portfolio call sites) + memo slice.
- [x] (2026-06-10) Order form buy/sell/submit wiring + memo slice.
- [x] (2026-06-10) Orderbook panel tabs voiced — `orderbook-tabs-row` grew a labels arity, `l2-orderbook-view` passes voice labels, `orderbook-view-state` carries the `{:ui {:theme …}}` slice.
- [x] (2026-06-10) Phase C decor — `hyperopen.views.degen.widgets` (gated on `voice/degen?`, tokens only, desktop lg+): trade-route stats strip (Total Value/Unrealized P&L from `account-equity-metrics`, liq-risk tier from margin ratio, market vibes from active-asset 24h%, NFA + CONGRATS gold dashed cards) and bottom widget row (Degen Tip day-rotated, Whale Watch, Daily Motivation, Feeling Gauge from real unrealized PNL); brand wordmark/mark voiced (`HyperDegen`/`HD`) through the header vm; emoji on buy/sell/submit labels. Gotcha: new Tailwind classes in new namespaces need `npm run css:build` (JIT) — a stale CSS bundle silently drops them.
- [x] (2026-06-10) Phase D decor — chart doodle overlay (`degen-widgets/chart-doodles`: pointer-events-none SVG annotations in `--font-marker` handwriting, threaded through trade-view `renderers` into the chart wrapper; `--font-marker` cursive stack in base.css), Shill of the Day card (real top 24h gainer via `top-gainer`, click dispatches the existing `:actions/select-asset-by-market-key`, explicit not-financial-advice copy, no buy CTA), tiered `leverage-warning-banner` under the order-form leverage row (20x/50x/100x, real ui-leverage).
- [x] (2026-06-10) RESET LIFE — `:actions/reset-degen-life` through the full contract surface (fn in `header.actions`, collaborator dep, registration row in `runtime-registration.wallet`, `::common/no-args` contract; save-only so outside the effect-order/Lean surface). Bumps `[:degen :life-resets]`; the Feeling Gauge shows "Lives used: N" and the Degen Tip re-rolls by the counter.
- [x] (2026-06-10) Mobile — stats strip now renders on all sizes (horizontal scroll); mobile surface tabs voiced via short variants ("Chart (hopium)" / "Book (lol)" / "Trades (pain)") threaded from trade-view (shell stays voice-agnostic with a labels arity). Doodles + widget row stay lg+ deliberately (small charts and vertical space).
- [x] (2026-06-10) Mascots — `hyperopen.views.degen.illustrations`: original full-color flat SVG cast (frog, shiba, shiba-with-shades, whale) drawn in code, plus a PNL-driven gauge dial. Illustration hexes are exempt-by-design via a `dev/theme_color_baseline.edn` entry; the dial is UI and stays token-colored (test-enforced).
- [x] (2026-06-10) Phase E — prototype parity: `hyperopen.ui.sfx` (WebAudio synth, no assets) finally implements the dormant Sound-on-fill setting (soft chime default, prototype cha-ching under degen; played in `show-user-fill-toast!` behind the existing settings gate); live leverage taunts (`degen.order-form/leverage-tier`, 6 tiers from "Sensible. Are you lost?" to "MAXIMUM DEGEN", rendered live in the popover off the slider draft + committed-value banner ≥20x); massive stacked BUY MOON / SELL PANIC side selectors (`massive-side-row`; compact row everywhere else); desktop Account Equity panel hidden under degen so the widget row renders unclipped (`:hide-account-equity?` through panel-context); widgets row carries `relative z-10 bg-ho-bg`; "formerly responsible" brand tagline; quote-of-the-day in the motivation card. CLJS gotcha: destructuring a `:when` option key shadows the `when` macro — the option is named `:delay`.
- [x] (2026-06-10) Celebration FX — `hyperopen.ui.fx` (DOM-side, render-tree-free, self-cleaning, no-op without a DOM): emoji-only confetti burst on degen fills (keyframes in `surfaces/utilities.css`; emoji pieces keep the color ratchet clean) and the full-screen REKT overlay (💀 LIQUIDATED + marker-font quip + COPE & CONTINUE, click/8s dismiss). `fills/liquidation-fill-row?` detects HL liquidation fills (`liquidation` payload or "Liquidated …" dir); `celebrate-fills!` routes: liquidation → overlay + `sfx/rekt!` (rumble + descending saw + sad trombone), ordinary fill → confetti + cha-ching, other themes → plain chime. Slider tier-crossing ticks via `sfx/leverage-tick-on-change!` called from the popover message render (idempotent per tier level — the data-style `:on` handlers can't be wrapped, so the render side owns the prototype's lastThresh ref).
- [x] (2026-06-10) Size-slider risk feedback — the order-size percent slider doubles as the prototype's leverage slider via imputed account leverage (`degen.order-form/effective-leverage` = size% × margin leverage): live tier taunt line under the slider ("≈12.0x account leverage. Getting spicy. 🌶️"; "ALL IN." flavor at 100%), slider fill tinted by tier through the existing `--order-size-slider-active` var with token forms (`rgb(var(--ho-warn) / 0.55)` — the ratchet only counts hex literals), and tier-crossing ticks via the now-keyed `sfx/leverage-tick-on-change!` (`:leverage` vs `:order-size` atoms so the two sliders don't swallow each other's crossings).
- [ ] Follow-up — book-depth ratio bar under the order book (needs panel snapshot plumbing); the prototype's purely-fake controls (YOLO toggles, weapons, DEGEN LVL, IQ chip, roulette, meme feed, coin modal, nav joke toasts) stay unimplemented by design in a real-money product.

## Surprises & Discoveries

- Memoization is the sharp edge: voiced surfaces render through selected-state memos (`trade_view`), so a theme switch only relabels if the `{:ui {:theme …}}` slice is merged into that surface's selected state — any future voiced surface behind a memo must include it or it silently won't re-render.
- New Tailwind classes in new namespaces need `npm run css:build` (JIT); a stale CSS bundle silently drops them.
- Destructuring a `:when` option key shadows the `when` macro, so the decor option is named `:delay`.
- Data-style `:on` handlers can't be wrapped, so the render side owns the prototype's `lastThresh` ref for leverage tick crossings (`sfx/leverage-tick-on-change!`, keyed per slider so the two sliders don't swallow each other's crossings).
- Illustration hexes are exempt-by-design via a `dev/theme_color_baseline.edn` entry; the PNL gauge dial is UI and stays token-colored (test-enforced) so the color ratchet stays clean.

## Decision Log

- Voice derives from theme rather than a separate preference: one toggle, one persistence path; revisit only if product wants voice and palette decoupled.
- The most specific override wins: caller `tab-label-overrides` > voice > registry default.
- Existing tests assert default-voice strings and stay untouched; degen assertions are additive.

## Validation

- The `:default` voice catalog is sync-tested against the surfaces that own those strings (`views.header.nav` items, `views.account-info.tab-registry/tab-labels`), so catalog and surface cannot drift; catalog hygiene unit tests cover lookups and fallbacks.
- vm tests assert both voices for the header nav (desktop/mobile/more), account-info tabs (trade + portfolio), and order-form buy/sell/submit labels.
- Acceptance: switching themes in Settings → Appearance relabels nav, account-info tabs, and order-form buttons in the same gesture that recolors them; under the degen voice, fills trigger sfx/confetti and liquidation fills trigger the REKT overlay, with no behavior change under any other theme.
