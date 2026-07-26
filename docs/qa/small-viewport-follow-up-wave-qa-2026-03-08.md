# Small-Viewport Follow-Up Wave QA (2026-03-08)

## Scope

Validated the follow-up implementation for:

- `/trade`
- `/vaults`

Viewports used:

- Phone: `390x844`
- Tablet: `1024x1366`

Local browser target:

- `http://localhost:8081`

Warm-session rule used for local captures:

1. Load `http://localhost:8081/index.html` in a fresh browser profile.
2. Let the service worker take control.
3. Then capture `/trade` and `/vaults`.

This warm step was required because direct SPA-route loads against the local dev server returned invalid blank captures in a fresh ephemeral browser profile.

## Validation

Completed after the final header hotfix:

- `npm run check`
- `npm test`
- `npm run test:websocket`

## Evidence

- Fresh local trade sanity inspect: `/hyperopen/tmp/browser-inspection/inspect-2026-03-08T02-08-25-290Z-f35ffac1/`
- Final trade compare: `/hyperopen/tmp/browser-inspection/compare-2026-03-08T02-08-51-180Z-cd70b603/`
- Final vaults compare: `/hyperopen/tmp/browser-inspection/compare-2026-03-08T02-09-35-467Z-06504c52/`
- Follow-up machine-readable summary: `/hyperopen/tmp/browser-inspection/small-viewport-follow-up-wave-summary-2026-03-08.json`
- Prior post-implementation QA baseline: `/hyperopen/docs/qa/small-viewport-hyperliquid-parity-implementation-qa-2026-03-08.md`

## Result

This follow-up wave succeeded as a polish-and-stability pass, not as another large structural shift.

The measurable outcomes versus the March 8 post-implementation baseline were:

| Route | Viewport | Prior | Final | Delta | Verdict |
| --- | --- | --- | --- | --- | --- |
| `/trade` | phone | `0.1283` (`high`) | `0.1228` (`high`) | `-0.0055` | slightly narrower |
| `/trade` | tablet | `0.0743` (`medium`) | `0.0823` (`medium`) | `+0.0080` | effectively flat; rerun noise outweighed tiny polish changes |
| `/vaults` | phone | `0.1166` (`medium`) | `0.1154` (`medium`) | `-0.0012` | effectively flat but visually cleaner |
| `/vaults` | tablet | `0.0430` (`medium`) | `0.0325` (`low`) | `-0.0105` | narrower |

## Browser QA Notes

### `/trade`

- The visible header bug is gone. The final local screenshots no longer show raw `:data-parity-id` text in the header.
- Phone still reads more branded and shell-heavy than Hyperliquid, but the trade route is cleaner: tighter header spacing, tighter market strip, and the earlier account/history shortcut rail is now visible beneath the main `Chart / Order Book / Trade / Account` surface switcher.
- Tablet preserved the intended right-rail ticket composition from the main March 8 wave. The rerun ratio is slightly worse than the earlier artifact, but the layout did not regress in the browser; the remaining gap is mostly chrome, branding, and the extra account-equity/account-tabs band that Hyperliquid does not emphasize the same way.

### `/vaults`

- Phone is functionally and visually closer to Hyperliquid now. HyperOpen now exposes the same kind of route-level `Connect` CTA when disconnected, and the search/filter block plus TVL panel are closer to Hyperliquid’s shape and spacing.
- Tablet is the clearest numerical improvement from this follow-up. The page remains more branded than Hyperliquid because of the logo/header treatment, but the content region below the hero is now very close in composition.
- The vault phone ratio moved only slightly, but manual comparison shows a meaningful qualitative cleanup: flatter mobile rows, no disabled CTA, and more direct parity with Hyperliquid’s disconnected-state treatment.

## Stability Check

The initial browser QA pass for this wave exposed a live render bug introduced by the responsive disconnected wallet button markup. The broken capture is preserved at:

- `/hyperopen/tmp/browser-inspection/inspect-2026-03-08T02-03-22-339Z-4e0770c0/`

That run showed blank local trade rendering plus console warnings pointing at the fragment-based wallet button children. After replacing that structure, the fresh inspect run at `/hyperopen/tmp/browser-inspection/inspect-2026-03-08T02-08-25-290Z-f35ffac1/` showed:

- no browser exceptions
- no fragment-warning matches
- `1098` semantic nodes on local `/trade`

That inspect run is the proof that the final compare artifacts are based on a healthy local render.

## Conclusion

The follow-up wave is complete. It removed the visible header regression on `/trade`, improved earlier access to trade account/history surfaces on phone, aligned `/vaults` disconnected-state behavior more closely with Hyperliquid, and verified the final state with fresh warmed-session browser artifacts. The remaining differences are now mostly brand/shell choices rather than missed responsive mechanics.

