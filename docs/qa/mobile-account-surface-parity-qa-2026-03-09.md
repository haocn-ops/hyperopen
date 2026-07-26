# Mobile account surface parity QA (2026-03-09)

## Scope

Manual browser QA for the mobile `/trade` footer-surface ownership change that moves account summary metrics and funding actions out of the small-screen `Trade` surface and into the small-screen `Account` surface.

The QA pass verified three things:

1. `Markets` no longer shows the account panel underneath the chart on mobile.
2. `Trade` no longer shows the account summary or the `Deposit` / `Perps <-> Spot` / `Withdraw` buttons on mobile.
3. `Account` now owns the summary plus funding actions plus account tabs, and it renders the correct summary variant for both unified and classic account modes.

## Environment

- Workspace: `/hyperopen`
- Local app: `http://localhost:8080/trade`
- Browser session: browser-inspection session `sess-1773021630328-78d66c`
- Viewport: mobile `390x844`

## Tooling note

The standard browser-inspection `inspect` command always re-navigates before capturing, which reset `/trade` back to the default chart surface. For the final screenshots in this QA pass, live surface selection was done first through `HYPEROPEN_DEBUG.dispatch(...)`, then screenshots were captured directly from the active Chrome DevTools target over CDP so the already-selected mobile surface could be preserved.

## Artifact directory

`/hyperopen/tmp/browser-inspection/manual-mobile-account-surface-parity-2026-03-09T02-04-37Z/`

Key files:

- `markets-surface.png`
- `trade-surface.png`
- `account-surface.png`
- `spectate-unified-account.png`
- `spectate-classic-account.png`
- `summary.json`
- `spectate-summary.json`

## Steps and observations

### 1. Non-spectate mobile footer surfaces

Selected `Markets`, `Trade`, and `Account` through `HYPEROPEN_DEBUG.dispatch([":actions/select-trade-mobile-surface", ...])`, then captured each live surface.

Observed:

- `Markets` screenshot shows the chart surface only. `summary.json` records `accountPanelDisplay: "none"` and no account-summary or balance-tab text.
- `Trade` screenshot shows the order ticket only. `summary.json` records `orderEntryDisplay: "flex"`, `accountPanelDisplay: "none"`, and no `Deposit` / `Withdraw` / account-summary text.
- `Account` screenshot shows the account summary at the top, the funding action row below it, and the balances tab rail underneath. `summary.json` records `accountPanelDisplay: "flex"`, `hasClassicAccountEquity: true`, `hasDeposit: true`, `hasWithdraw: true`, `hasTransferLabel: true`, `hasBalances: true`, `hasOpenOrders: true`, and `hasTradeHistory: true`.

### 2. Unified account summary variant

Started spectate mode for unified address `0x4096d3377ae5ade578daae8188804740c8b1da3e`, kept the mobile `Account` surface selected, waited for the account data to settle, then captured the live screenshot.

Observed:

- `spectate-summary.json` records `accountMode: "unified"` and `hasUnifiedAccountSummary: true`.
- `spectate-unified-account.png` shows `Unified Account Summary` with the expected unified labels and the balances tabs directly below it.

### 3. Classic account summary variant

Started spectate mode for classic address `0x2ba553d9f990a3b66b03b2dc0d030dfc1c061036`, kept the mobile `Account` surface selected, waited for the account data to settle, then captured the live screenshot.

Observed:

- `spectate-summary.json` records `accountMode: "classic"` and `hasClassicAccountEquity: true`.
- `spectate-classic-account.png` shows `Account Equity` / `Account Value` with classic `Spot` and `Perps` rows and the balances tabs directly below.

## Regression assessment

No regression was observed in the mobile footer navigation or trade/account surface ownership during this QA pass.

- `Markets` remained chart-first.
- `Trade` gained vertical room by dropping the account summary/action block.
- `Account` became the single mobile location for account summary plus funding actions plus account tabs.
- Unified and classic account modes both rendered the expected summary variant on the mobile `Account` surface.

## Validation gates

The code changes associated with this QA note also passed:

- `npm test`
- `npm run check`
- `npm run test:websocket`
