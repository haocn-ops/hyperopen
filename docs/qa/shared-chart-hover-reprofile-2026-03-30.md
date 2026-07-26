# Shared Chart Hover Reprofile 2026-03-30

## Scope

Follow-up reprofiling for `hyperopen-atw0` after the shared chart hover jank reduction landed on 2026-03-16.

## Environment

- Worktree: `/Users/barry/.codex/worktrees/6263/hyperopen`
- Local app origin used for the final reprofiling run: `http://localhost:8081`
- Browser driver: Playwright Chromium headless
- Local `/portfolio` note: the disconnected default `/portfolio` route in this session did not expose a hoverable chart state, so the populated Portfolio repro used spectate mode:
  - `/portfolio?spectate=0x162cc7c861ebd0c06b3d72319201150482518185`
- Vault detail route exercised during the run:
  - `/vaults/0xdfc24b077bc1425ad1dea75bcb6f8158e10df303`

## Method

1. Warm the SPA through `/index.html`.
2. Navigate with the debug bridge instead of direct deep-link loads.
3. Select the returns chart on Portfolio and Vault detail.
4. Drive repeated left-to-right and right-to-left pointer sweeps across the shared D3 host with Playwright mouse input.
5. Collect during-hover metrics in-page:
   - `PerformanceObserver` layout-shift totals
   - `PerformanceObserver` long-task totals
   - hover settle samples derived from pointermove timing to hover-line / tooltip transform mutations

## Results

### Portfolio

- Route: `http://localhost:8081/portfolio?spectate=0x162cc7c861ebd0c06b3d72319201150482518185`
- Hover settle:
  - sample count: `225`
  - p95: `7.4ms`
  - max: `33ms`
- Long tasks during the sampled hover window:
  - count: `3`
  - max: `312ms`
- Layout shifts during the sampled hover window:
  - entries: `51`
  - total value: `0.2050`

Attribution sample from a tighter follow-up trace:

- sampled `LayoutShift` sources pointed at route-shell containers, the account table shell, and small metric-value spans
- the shared tooltip root did not appear as a sampled layout-shift source

### Vault Detail

- Route: `http://localhost:8081/vaults/0xdfc24b077bc1425ad1dea75bcb6f8158e10df303`
- Hover settle:
  - sample count: `225`
  - p95: `0.5ms`
  - max: `52.4ms`
- Long tasks during the sampled hover window:
  - count: `7`
  - max: `439ms`
- Layout shifts during the sampled hover window:
  - entries: `29`
  - total value: `0.1091`

## Interpretation

The reprofiling does not support the original pre-fix theory that the shared tooltip shell is still the primary cause of hover jank.

What looks fixed:

- the tooltip updates through transform-only positioning
- hover-line and tooltip settle timings are low in the steady-state path
- the shared tooltip no longer showed up as the obvious layout-shift source in the populated Portfolio attribution sample

What remains open:

- live-route long tasks still occur during the hover window
- non-tooltip layout shifts still occur during the hover window
- the broader acceptance target of no hover-window blocking work above `50ms` is still not met

## Outcome

- `hyperopen-atw0`: satisfied as a reprofiling follow-up
- Remaining perf debt: tracked separately in `hyperopen-mceo`
