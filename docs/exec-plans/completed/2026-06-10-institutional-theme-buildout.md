# Institutional theme buildout (design-faithful trade + portfolio)

## Purpose

The `institutional` theme id has existed in the picker (Settings → Appearance) since the theming
foundation landed, but its palette was a placeholder (GitHub-dark green/red). A design handoff
(`~/Downloads/HyperOpen-institutional.zip`, key artifacts `styles.css`, `Trade.html`,
`Portfolio Analytics.html`, `components/{trade-page,portfolio,chart,trade-chart}.jsx`) defines
the real institutional look: near-black `#07090b` base, hairline `#1a2328` borders, cyan-teal
`#3ddbc4` accent, `#4ade80`/`#f87171` signal pair, JetBrains Mono data type with uppercase
letter-spaced micro-labels, square corners, no shadows. Direct user request on 2026-06-10:
build out the institutional theme so the trade route and portfolio analytics match those designs.
Chat/toast/diagnostics/build-hash material in the zip is explicitly out of scope, as are the
design's layout differences (breadcrumbs, stat-card sparklines) — themes change tokens
(color, type, radius), not layout (docs/THEMING.md).

After this change, selecting "Institutional" restyles the trade route (market strip, chart +
toolbar, orderbook, order ticket, account tables, footer) and portfolio analytics (header, stat
cards, returns chart, performance table) to the design. `dark` and `hyperdegen` render
pixel-identically except where a literal was replaced by the token holding the same value.

## Design

Three layers, in order:

1. **Palette rewrite** — the `institutional` entry in `src/styles/themes/palette.js` (colors,
   chart, daisy, fonts; radius already all-zero). Notable mappings: daisy `base-200` gets the
   design's `--bg-1` `#0b0f12` (table heads/toolbars/footer read `bg-base-200`); `sell-hi`
   collapses onto `sell` `#f87171` (the design has no brightened-sell, which lands token-driven
   ask-side orderbook surfaces exactly on the design red); `--font-mono` points at JetBrains Mono
   (already `@font-face`'d; the default chain is ui-monospace) while `--font-ui` stays
   `var(--font-mono)`. Regenerate with `node tools/styles/generate_theme_css.mjs`.

2. **Token migrations** (all themes benefit; dark pixel-identical or within the Phase-8
   per-channel ≲16 tolerance): nav-active `rgb(151 252 228)` → `accent-bright` (exact); slider
   chrome `rgba(0,212,170,…)`/`rgb(15,26,31)`/`rgb(48 54 61)` → `buy`/`bg`/`border` (exact),
   track fill `rgb(15,51,51)` → `accent-soft` (near), routed through a swappable
   `--slider-accent-rgb` channel var; orderbook ask pair → `sell-hi` utilities (exact), header
   text → `text-trading-text-secondary` (near); performance-metrics label inline hexes →
   `text-ho-text`/`text-ho-text-muted` (near); portfolio header primary `#1f5b55` →
   `accent-soft-hi` (near). Orderbook bid `rgb(31,166,125)` and body `rgb(210,218,215)` have no
   near token: their single-class constants became `ob-bid-px`/`ob-bid-bar`/`ob-body-text`,
   styled with the legacy literals in `src/styles/surfaces/trading-controls.css` (constants must
   stay single-class — Replicant `:class` vectors reject spaced strings).

3. **Institutional chrome layer** — new `src/styles/surfaces/institutional.css` (imported from
   `main.css`; manifest test updated), every rule scoped to `:root[data-theme="institutional"]`,
   colors only as `rgb(var(--ho-*))`: uppercase tracked micro-labels (market strip via the
   `asset-stat-label` hook, orderbook heads/tabs and portfolio chart tabs via existing
   `data-role` attributes, account tables via the `account-table-head` hook, chart toolbar via
   `data-parity-id`), accent slider fill, ob-* re-themes, Inter for structural nav/h1, and
   `--portfolio-strategy-stroke`/`--portfolio-chart-baseline`/`--portfolio-chart-hover-line`
   custom properties read by `portfolio/vm/chart.cljs` and `portfolio/chart_view.cljs` via
   `getComputedStyle` with the legacy literals as DOM-less fallbacks.

## Validation

Gates (all green 2026-06-10): `npm run test:styles`, `npm test` (4579 tests / 25532 assertions,
0 failures after updating orderbook + typography-scale test expectations to the migrated
class/token forms), `npm run css:build`, `npm run lint:theme-colors` (baseline ratcheted DOWN
1489→1425 literals, 110→107 files), `npm run lint:hiccup`, `npm run lint:namespace-sizes`,
`npx shadow-cljs --force-spawn compile app` and `compile portfolio` (0 warnings).

Visual acceptance (static-serve `resources/public`, boot `/index.html`,
`HYPEROPEN_DEBUG.dispatch([":actions/set-ui-theme","institutional"])`): trade route shows square
chrome, uppercase mono kickers/tabs/heads, design orderbook colors, themed candles
(`#4ade80`/`#f87171`); portfolio (spectating
`0x162cc7c861ebd0c06b3d72319201150482518185`) shows Inter h1, mono data, hairline panels, accent
`rgb(61 219 196)` returns line. Dark-theme regression spot-check confirmed the legacy literals
render byte-identical (bid `rgb(31,166,125)`, body `rgb(210,218,215)`, ask `rgb(237,112,136)`,
nav `rgb(151,252,228)`, indicator `rgb(80,210,193)`, system UI font, no uppercase).

## Progress

- [x] (2026-06-10) Recon: design spec extracted; trade/portfolio styling surfaces and theming
      mechanics mapped (4-agent workflow).
- [x] (2026-06-10) Milestone 1 — palette rewrite + regenerate + styles tests.
- [x] (2026-06-10) Milestone 2 — exact/near-exact token migrations + baseline ratchet down +
      test-expectation updates.
- [x] (2026-06-10) Milestone 3 — institutional.css + hooks (orderbook, market strip, toolbar,
      tables, tabs, sliders, portfolio chart, nav/h1 font).
- [x] (2026-06-10) Milestone 4 — gates green + visual acceptance vs design screenshots + dark
      regression check.

## Surprises & Discoveries

- Several "hardcoded" literals were exact token values (`rgb(151 252 228)` = accent-bright,
  `rgb(80,210,193)` = accent, `rgb(237,112,136)` = sell-hi, `rgb(15,26,31)` = bg,
  `rgb(48 54 61)` = border), making much of the migration pixel-free.
- The portfolio d3 chart and vm strategy stroke (`#f5f7f8`) bypass the `--ho-chart-*` system
  entirely; the CSS-variable-with-fallback bridge keeps the vm pure for DOM-less tests while
  letting themes recolor it.
- Account-info tab widths are canvas-measured from label text
  (`hyperopen.ui.fonts/canvas-font` resolves the live `--font-ui`), so the uppercase transform
  is safe only because the institutional UI face is monospace (equal glyph advances); extra
  letter-spacing there would desync the indicator and was deliberately skipped.
- The market-strip cell collision visible at ~1600px is pre-existing (dark shows −17px gaps;
  institutional −21px) — a layout-density issue outside theme scope.
- `npm run lint:docs` (in `check`) was already failing on HEAD for the hyperdegen voice-layer
  plan's headings and a stale PRD; pre-existing, not addressed here.

## Decision Log

- (2026-06-10) Keep `fonts.ui = var(--font-mono)` (mono-first chrome) and point `--font-mono` at
  JetBrains Mono; the design's Inter usage is limited to structural elements (nav/h1), restored
  via scoped rules. Mono-everywhere with Inter exceptions is far less invasive than
  Inter-everywhere with per-label mono hooks.
- (2026-06-10) Dark-pixel-identity is the hard rule for steady states; transient states (hovers,
  e.g. order-form tab hover `#D2DAD7` → `text-ho-text`) may take small drift when a token is
  semantically right.
- (2026-06-10) `sell-hi` = `sell` for institutional rather than adding ask-side hooks — the
  design has one red; "sell hover/bright" simply doesn't brighten under this theme.
- (2026-06-10) Out of scope: chart history-loader animation literals in `surfaces/chart.css`
  (transient FX), montecarlo `--mc-*` and optimizer `--o-*` scoped systems, account-table
  tooltip grays, subaccount banner, benchmark stroke catalog (multi-hue, deliberately unmapped
  like the indicator catalog).

## Outcomes & Retrospective

Shipped in one pass: the institutional picker entry now renders the design handoff's terminal
look across trade + portfolio, with the dark theme regression-checked byte-identical on every
migrated literal and the color-ratchet baseline 64 literals lower than before. The
hook-plus-scoped-CSS pattern (`ob-*`, `asset-stat-label`, `account-table-head`,
`--portfolio-*` bridge vars) is reusable for future themes that need per-theme chrome beyond
tokens. Remaining cosmetic gaps vs the design are recorded in the Decision Log's out-of-scope
list; the largest visible one is the chart history-loader teal and the default-colored
long-tail modals tracked by the Phase 8 consolidation in the ui-theming-foundation plan.
