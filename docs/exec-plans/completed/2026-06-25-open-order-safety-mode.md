# Open Order Safety Mode

## Purpose

Give traders explicit control over Hyperliquid's account/vault-level scheduled cancel safety while keeping the current strict default. A trader should be able to choose:

- `strict`: current behavior, arm scheduled cancel roughly one minute out and refresh frequently while Hyperopen is online.
- `extended`: arm scheduled cancel several hours out so browser sleep or short offline windows do not immediately cancel resting scale-order ladders.
- `off`: clear scheduled cancel so GTC orders remain live until filled, manually canceled, or rejected by Hyperliquid.

## Context References

- Direct user request in this Codex thread on 2026-06-25: create and implement an execution plan for the recommended open-order safety setting.
- `src/hyperopen/wallet/agent_safety.cljs` owns the current schedule-cancel watchdog.
- `src/hyperopen/api/trading/agent_actions.cljs` serializes the Hyperliquid `scheduleCancel` action.
- `src/hyperopen/trading_settings.cljs`, `src/hyperopen/header/actions.cljs`, and `src/hyperopen/views/header/vm.cljs` own Trading Settings persistence and projection.
- `test/hyperopen/wallet/agent_safety_test.cljs`, `test/hyperopen/header/actions_test.cljs`, and `test/hyperopen/views/header/vm_test.cljs` cover the nearest behavior.

## Progress

- [x] 2026-06-25 18:05Z Created this ExecPlan after confirming the current code path and the approved product direction.
- [x] 2026-06-25 18:05Z Add RED tests for the safety-mode enum, API clear action, watcher re-arm/clear behavior, and Trading Settings UI/action persistence. `npm test` failed as expected on missing scheduleCancel clear serialization, missing settings/action/UI rows, and missing watcher mode behavior.
- [x] 2026-06-25 18:26Z Implement the normalized setting and persisted action.
- [x] 2026-06-25 18:26Z Implement dynamic watcher policy, including extended/off behavior and no-time scheduleCancel clearing.
- [x] 2026-06-25 18:26Z Update Trading Settings projection and view tests.
- [x] 2026-06-25 18:26Z Run focused tests, required gates, and browser QA; record results below.

## Surprises & Discoveries

- Hyperopen already has a persisted `:trading-settings` state and choice-row UI control, so the setting can fit existing settings plumbing.
- Clearing Hyperliquid's scheduled cancel uses the same `scheduleCancel` action with `time` omitted; the existing wrapper always includes `:time`.

## Decision Log

- Keep `strict` as the default to preserve the current protective behavior for existing users.
- Store this as `:open-order-safety-mode` in `:trading-settings`, because it is a user-facing Trading Settings choice and should restore with other local preferences.
- Use `extended` as a 4-hour scheduled-cancel deadline refreshed every 15 minutes while Hyperopen is online. This is long enough for a few-hours offline window while keeping a bounded dead-man switch.
- Make the setting account/vault-wide in copy and implementation. Hyperliquid's schedule cancel is not per scale order.

## Outcomes & Retrospective

Implemented a persisted Trading Settings choice named "Open order safety" with Strict, 4h, and Off modes. Strict remains the default and uses the existing config-driven 60s ahead / 30s refresh values. Extended uses a 4-hour scheduled-cancel deadline refreshed every 15 minutes while Hyperopen is online. Off sends Hyperliquid `scheduleCancel` without `time`, clearing the account/vault-level dead-man switch and avoiding refresh timers.

The agent safety watcher now includes the normalized safety mode in its fingerprint and recomputes policy from current app state before arming, refreshing, or clearing. This makes mode changes take effect while trading is already enabled. Selected subaccount/vault routing and volume-gate classification remain covered.

Validation results:

- `npm test`: PASS, 4,816 tests and 26,607 assertions.
- `npm run check`: PASS. Docs lint reported existing stale-doc advisories for `docs/QUALITY_SCORE.md` and `docs/product-specs/leaderboard-page-parity-prd.md`; they are non-blocking and unrelated.
- `npm run test:websocket`: PASS, 545 tests and 3,131 assertions.
- `npm run qa:design-ui -- --targets trade-route --manage-local-app`: PASS. Run ID `design-review-2026-06-25T18-18-42-885Z-e3343409`; run dir `/Users/barry/.codex/worktrees/5303/hyperopen/tmp/browser-inspection/design-review-2026-06-25T18-18-42-885Z-e3343409`. All six passes passed across 375, 768, 1280, and 1440 widths; residual blind spot was state sampling for hover/active/disabled/loading states.
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 PLAYWRIGHT_WEB_PORT=4174 PLAYWRIGHT_WEB_SERVER_COMMAND='node tools/playwright/static_server.mjs' npm run test:playwright:smoke`: 45 passed, 1 failed in the full parallel run. The failed test was `tools/playwright/test/trade-regressions.spec.mjs:3632`, expecting subaccount order/cancel simulator payloads. The exact test passed when rerun isolated with the same alternate-port setup, so this is recorded as a residual parallel-smoke flake rather than a failure tied to this change.
- `npm run browser:cleanup`: PASS, no lingering browser-inspection sessions.

## Context / Orientation

The current watcher starts when the wallet is connected and the trading agent is `:ready`. It sends `scheduleCancel` for the selected exchange vault address when a subaccount/vault is active, then schedules a timer to refresh the deadline. The watch fingerprint does not include Trading Settings today, so settings changes will need to become part of the fingerprint for immediate re-arm/clear behavior.

Trading Settings are restored from `hyperopen:trading-settings:v1` and normalized through `hyperopen.trading-settings/normalize-state`. Header actions persist the whole normalized map with `:effects/save` and `:effects/local-storage-set-json`.

## Plan Of Work

1. Add tests first:
   - `trading_settings` normalization/default tests for `:open-order-safety-mode`.
   - `header/actions` persistence test for `set-open-order-safety-mode`.
   - `header/vm` projection test for a choice row with Strict, 4h, and Off options.
   - `api/trading` test proving nil `cancel-at-ms` emits `{:type "scheduleCancel"}` without `:time`.
   - `wallet/agent_safety` tests proving strict default still arms, extended uses the longer window, and off clears without scheduling refresh.
2. Add the setting contract:
   - Normalize valid modes to `:strict`, `:extended`, or `:off`.
   - Default missing/invalid mode to `:strict`.
   - Persist setting changes through the existing local Trading Settings flow.
3. Add safety policy:
   - Map modes to enabled/disabled policy with `ahead-ms` and `refresh-ms`.
   - Use existing config values for strict by default.
   - Use `14400000` ms ahead and `900000` ms refresh for extended.
4. Update watcher:
   - Include normalized safety mode in the watcher fingerprint.
   - Recompute policy from the current store each arm/refresh.
   - For off mode, clear any scheduled cancel once and do not install a refresh timer.
   - Preserve volume-gate classification and selected-vault routing.
5. Update API serialization:
   - Omit `:time` when `cancel-at-ms` is nil.
6. Update UI:
   - Add an "Open orders" Trading Settings section with a choice row.
   - Use concise copy that names the account/vault-wide offline-cancel behavior.

## Concrete Steps

Run the smallest relevant tests after adding RED tests. After implementation, run focused CLJS tests by namespace if the test runner supports it; otherwise run `npm test`. Finish with `npm run gates` or the required separate gates: `npm run check`, `npm test`, and `npm run test:websocket`.

## Validation / Acceptance

- Default state and invalid persisted state use `:strict`.
- Strict mode remains behaviorally equivalent to today's schedule-cancel loop.
- Extended mode schedules `now + 14,400,000` ms and refreshes every `900,000` ms.
- Off mode sends a scheduleCancel action without `time`, clears any refresh timer, and does not schedule another refresh.
- Switching modes while agent trading is ready immediately re-runs the watcher and either re-arms or clears based on the new mode.
- Trading Settings displays the mode as a choice row and persists user selection.

## Idempotence / Recovery

The watcher clears the prior refresh timer before every re-arm. If an API call fails, it keeps the same failure behavior as the existing watcher and avoids adding a new timer after volume-gate rejection. If localStorage is unavailable or malformed, normalization falls back to strict.

## Artifacts / Notes

- Browser design-review artifacts: `/Users/barry/.codex/worktrees/5303/hyperopen/tmp/browser-inspection/design-review-2026-06-25T18-18-42-885Z-e3343409`.
- Full Playwright smoke flake artifact: `/Users/barry/.codex/worktrees/5303/hyperopen/tmp/playwright/test-results/interactive/trade-regressions-header-a-bf85c-ultAddress-smoke-regression`.

## Interfaces / Dependencies

- Hyperliquid `scheduleCancel` action: `{:type "scheduleCancel" :time <ms>}` arms, and `{:type "scheduleCancel"}` clears.
- Existing `:effects/local-storage-set-json` adapter persists Trading Settings.
- Existing config values remain the strict-mode defaults.
