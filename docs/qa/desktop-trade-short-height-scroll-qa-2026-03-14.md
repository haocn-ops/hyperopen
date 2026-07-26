# Desktop Trade Short-Height Scroll QA (2026-03-14)

## Scope

Validated `/trade` desktop short-height scrolling after moving `xl` scroll ownership into the trade route while keeping the app-shell trade lock in place.

Viewports checked:

- `1280x720`
- `1440x760`
- `1440x900`

Local browser target:

- `http://localhost:8081/trade`

Warm-session rule used for local captures:

1. Open `http://localhost:8081/index.html`.
2. Let the local app load.
3. Capture and inspect `http://localhost:8081/trade`.

## Validation

Validation gates completed during this change:

- `npm run check`
- `npm test`
- `npm run test:websocket`

## Evidence

- Browser-inspection metric summary: `/hyperopen/tmp/browser-inspection/desktop-trade-short-height-scroll-2026-03-14.json`
- `1280x720` capture: `/hyperopen/tmp/browser-inspection/inspect-2026-03-14T16-42-46-082Z-b4f91ffc/`
- `1440x760` capture: `/hyperopen/tmp/browser-inspection/inspect-2026-03-14T16-42-50-463Z-8a1f8809/`
- `1440x900` capture: `/hyperopen/tmp/browser-inspection/inspect-2026-03-14T16-42-54-048Z-eea31f42/`

## Result

The trade route now owns vertical scrolling at `xl`, and the desktop trade grid now reserves enough minimum height for the lower account row to move below the fold instead of being compressed inside the viewport.

At all three checked desktop heights:

- `trade-root` reported `overflowY: "auto"` and `scrollHeight > clientHeight`
- the header stayed fixed above the content band
- the footer stayed fixed at the bottom of the viewport
- the right-rail desktop account summary started partially clipped and became fully visible after scrolling to the bottom
- the desktop `account-tables` surface started partially clipped and became fully visible after scrolling to the bottom

## Measured Behavior

### `1280x720`

- Before scroll: `trade-root` `clientHeight=607`, `scrollHeight=964`, `maxScrollTop=357`
- Before scroll: summary `top=636`, `bottom=1019`, not fully within the visible band
- Before scroll: account tables `top=646`, `bottom=1030`, not fully within the visible band
- After scroll: summary `top=279`, `bottom=662`, fully within the visible band
- After scroll: account tables `top=289`, `bottom=673`, fully within the visible band

### `1440x760`

- Before scroll: `trade-root` `clientHeight=647`, `scrollHeight=964`, `maxScrollTop=317`
- Before scroll: summary `top=636`, `bottom=1019`, not fully within the visible band
- Before scroll: account tables `top=646`, `bottom=1030`, not fully within the visible band
- After scroll: summary `top=319`, `bottom=702`, fully within the visible band
- After scroll: account tables `top=329`, `bottom=713`, fully within the visible band

### `1440x900`

- Before scroll: `trade-root` `clientHeight=787`, `scrollHeight=964`, `maxScrollTop=177`
- Before scroll: summary `top=636`, `bottom=1019`, not fully within the visible band
- Before scroll: account tables `top=646`, `bottom=1030`, not fully within the visible band
- After scroll: summary `top=459`, `bottom=842`, fully within the visible band
- After scroll: account tables `top=469`, `bottom=853`, fully within the visible band

## Conclusion

This change fixes the short-height desktop regression on `/trade`. The desktop composition stays intact, but the summary rail and lower account tables now fall under the fold and are reachable by scrolling inside the trade route, with the global header and fixed footer unchanged.
