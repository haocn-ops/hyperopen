# iPhone Trade Parity Wave QA (2026-03-08)

## Scope

Validated the iPhone-focused trade parity wave against Hyperliquid for:

- `/trade`

Viewport used:

- iPhone 14 Pro Max: `430x932`

Local browser target:

- `http://localhost:8080`

Warm-session rule used for local captures:

1. Start a browser-inspection session with the iPhone override config.
2. Load `http://localhost:8080/index.html` first.
3. Then capture `http://localhost:8080/trade`.

This warm step remains necessary for trustworthy local SPA captures in a fresh ephemeral browser profile.

## Validation

Completed on the final code:

- `npm run check`
- `npm test`
- `npm run test:websocket`
- `npm run test:browser-inspection`

## Evidence

- Before compare: `/hyperopen/tmp/browser-inspection/compare-2026-03-08T13-41-29-156Z-a0677cdb/`
- Final compare: `/hyperopen/tmp/browser-inspection/compare-2026-03-08T14-03-10-188Z-1e82469d/`
- Machine-readable summary: `/hyperopen/tmp/browser-inspection/iphone-trade-parity-wave-summary-2026-03-08.json`
- ExecPlan: `/hyperopen/docs/exec-plans/completed/2026-03-08-iphone-trade-parity-wave.md`

## Result

The iPhone trade parity wave succeeded in closing the exact structural gaps highlighted in the screenshot review, with a smaller numerical visual diff and a clearly narrower shell/layout mismatch.

Measured change:

| Route | Viewport | Before | Final | Delta | Verdict |
| --- | --- | --- | --- | --- | --- |
| `/trade` | iPhone 14 Pro Max | `0.1139` (`medium`) | `0.1091` (`medium`) | `-0.0048` | slightly narrower |

## Manual Browser Review

The final local trade screenshot now matches the requested mobile structure much more closely than the baseline:

- a real hamburger menu is present at the top left
- the oversized mobile wordmark is replaced by a compact mobile brand mark
- the top shell now includes icon-led utility controls instead of a single inert menu button on the right
- the market strip starts in a collapsed state, with extra statistics moved behind the disclosure control
- the top surface switcher is reduced to `Chart / Order Book / Trade`
- the account shortcut rail is no longer always visible above the fold; it only appears when the account surface is active
- the fixed footer has been replaced on small screens by a bottom nav with `Markets`, `Trade`, and `Account`

The remaining visual gap is still real, but it is now driven more by chart/content differences than by shell structure:

- Hyperliquid still shows a denser chart tool rail and a different chart chrome stack
- Hyperliquid exposes account tabs directly under the chart instead of using our route-aware bottom-nav/account-surface pattern
- HyperOpen still reads more branded, especially in the top-left identity treatment and the selected bottom-nav pill
- the chart body and lower account region still differ enough that the raw diff ratio remains in the `medium` band

## Functional Review

The screenshot-driven functional complaints are addressed:

- the hamburger is now actionable and exposes route navigation plus Spectate Mode
- the top statistics are no longer always expanded
- the bottom mobile navigation exists and routes/switches surfaces correctly
- the trade page hides more secondary content behind explicit surface changes instead of rendering every control row at once

Interaction note: the browser-inspection tooling is read-only, so the disclosure and menu click paths were verified through the rendered UI tree plus automated view tests rather than live browser clicks.

## Conclusion

This wave narrowed the iPhone trade parity gap in the right places. The raw diff improved only modestly, but the biggest user-visible discrepancies from the supplied screenshots are no longer present. Further improvement would be a separate polish wave focused on chart chrome and the lower trade/account content region rather than on shell/navigation parity.
