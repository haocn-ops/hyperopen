# Resolve Browser QA Baseline Failures And Harden The Playwright Debug Bridge

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The tracked `bd` work items are `hyperopen-qkwa`, `hyperopen-cu04`, and `hyperopen-oomd`; `bd` was the local tracker used at the time while this plan records implementation context and evidence.

## Purpose / Big Picture

Full in-browser QA currently reports two desktop `/trade` product-regression scenarios even though both failures reproduce on the baseline parent commit of the open-order overlay refactor. This work should make the browser QA suite accurately distinguish real product regressions from harness false positives, restore the two failing scenario beads to green, and make Playwright tests fail with useful diagnostics when the `HYPEROPEN_DEBUG` bridge is not ready instead of producing ambiguous startup timeouts. A user can see this working by running the targeted Browser Inspection scenarios and the targeted Playwright regression tests and observing stable passes with artifact evidence.

## Progress

- [x] (2026-04-19 18:46Z) Created `hyperopen-oomd` for Playwright Debug Bridge readiness hardening and linked it to the refactor follow-up context.
- [x] (2026-04-19 18:52Z) Dispatched an `explorer` subagent for `hyperopen-qkwa`; it found that the funding tooltip failure is most likely a Browser QA false positive caused by sampling a nested tooltip child with its own `data-role`.
- [x] (2026-04-19 18:55Z) Read the relevant Browser QA scenarios, Playwright helpers, tooltip layout code, wallet simulator code, and prior tooltip-layering ExecPlan.
- [x] (2026-04-19 19:08Z) Claimed `hyperopen-qkwa`, `hyperopen-cu04`, and `hyperopen-oomd`.
- [x] (2026-04-19 19:09Z) Fixed the funding tooltip scenario so it verifies tooltip ownership instead of nearest nested `data-role`; targeted Browser Inspection pass is `tmp/browser-inspection/qkwa-after-fix-2026-04-19T19-09-30-898Z-68044e05`.
- [x] (2026-04-19 19:21Z) Diagnosed and fixed the wallet enable-trading simulator boundary with regression guards; targeted Browser Inspection pass is `tmp/browser-inspection/cu04-after-fix-fresh-2026-04-19T19-21-23-402Z-15936383`.
- [x] (2026-04-19 19:31Z) Hardened Playwright `HYPEROPEN_DEBUG` bridge waits with retry and diagnostic output; the diagnostic timeout guard passes under a support-only Playwright config.
- [x] (2026-04-19 19:36Z) Resolved the `npm run check` namespace-size blocker by extracting debug exchange simulator state/response handling into `hyperopen.api.trading.debug-exchange-simulator` and trimming `console_preload.cljs`; `npm run lint:namespace-sizes`, the focused CLJS simulator tests, `npm run check`, `npm test`, and `npm run test:websocket` pass.
- [x] (2026-04-20 14:02Z) Stabilized the two committed Playwright regressions: the funding fixture now re-applies its live-position seed until it survives quiet-idle, and the wallet regression uses the app-level debug exchange simulator instead of a lower-level route mock.
- [x] (2026-04-20 14:03Z) Targeted Playwright repeat passed 10/10: `funding tooltip transitions from live position to hypothetical estimate` and `wallet connect and enable trading stays deterministic`, repeated five times serially against the local dev app.
- [x] (2026-04-20 14:02Z) Targeted Browser Inspection rerun passed in `tmp/browser-inspection/browser-qa-followup-after-playwright-hardening-2026-04-20T14-02-21-302Z-a4ca5651`.
- [x] (2026-04-20 14:06Z) Broad Browser Inspection follow-up passed 15/15 nightly-tag scenarios in `tmp/browser-inspection/browser-qa-full-followup-2026-04-20T14-05-06-240Z-cfe1163c`.
- [x] (2026-04-20 14:08Z) Final repo gates passed: `npm run check`, `npm test`, `npm run test:websocket`, and `npm run browser:cleanup`.
- [x] (2026-04-20 14:07Z) Closed `hyperopen-qkwa`, `hyperopen-cu04`, and `hyperopen-oomd` with validation notes.
- [x] (2026-04-20 14:18Z) Addressed reviewer findings by reducing the default Playwright bridge wait to fit inside the 45s test timeout with one retry and attaching console/page-error listeners before bootstrap navigation.
- [x] (2026-04-20 14:21Z) Revalidated the debug-bridge support spec and targeted Playwright regressions on an isolated fallback port; the support spec passed and the two targeted regressions passed 6/6.
- [x] (2026-04-20 14:25Z) `npm run qa:pr-ui -- --base-ref HEAD~1 --manage-local-app` passed with PR scenario state `pass`, design review state `PASS`, and overall state `pass`.

## Surprises & Discoveries

- Observation: `trade-funding-tooltip-layering` appears to fail because the scenario expects `document.elementFromPoint(...).closest("[data-role]")` to return `active-asset-funding-tooltip`, but the sampled element can be the tooltip's own position section.
  Evidence: `/hyperopen/src/hyperopen/views/active_asset/funding_tooltip.cljs` renders `data-role="active-asset-funding-position-section"` inside the tooltip, and `/hyperopen/tools/browser-inspection/scenarios/trade-funding-tooltip-layering.json` currently checks the nearest `data-role` at the sampled overlap point. The failing actual value was `active-asset-funding-position-section`, which is an internal tooltip child, not an order book or account panel role.

- Observation: the prior tooltip-layering guard intentionally made the trade shell raise and unclip the market strip while the tooltip is open; those product seams still exist.
  Evidence: `/hyperopen/src/hyperopen/views/trade_view/layout_state.cljs` switches `chart-panel-classes` to `relative z-[160] overflow-visible`, `/hyperopen/src/hyperopen/views/active_asset_view.cljs` adds `z-[160]` to `market-strip`, and `/hyperopen/test/hyperopen/views/trade_view/layout_state_test.cljs` already asserts the open-state shell classes.

- Observation: `wallet-enable-trading-simulated` has a different shape from the tooltip failure. The scenario installs both wallet and exchange simulators, but the app still reaches the Hyperliquid missing-agent-wallet error. That means either the exchange simulator is not being consumed by the actual approval path, is consumed before the manual enable action, or the scenario lacks enough diagnostics to prove which boundary failed.
  Evidence: the nightly artifact `/hyperopen/tmp/browser-inspection/nightly-ui-qa-2026-04-19T18-22-48-719Z-9d29e7f8/summary.json` shows `installExchangeSimulator` returning `approveAgent.responses[0].status = "ok"`, then `agentStatus = "error"` with `Agent wallet not recognized by Hyperliquid. Enable Trading again.`

- Observation: Playwright and Browser Inspection do not currently share debug-bridge readiness behavior. Browser Inspection retries a local bootstrap navigation once if `HYPEROPEN_DEBUG` does not appear; Playwright only polls a boolean expression and times out without recording useful page state.
  Evidence: `/hyperopen/tools/browser-inspection/src/session_manager.mjs` has `debugBridgeRetryCount` and `debugBridgeRetryDelayMs`; `/hyperopen/tools/playwright/support/hyperopen.mjs` has a single `expect.poll` in `waitForDebugBridge`.

- Observation: `wallet-enable-trading-simulated` consumed the configured `approveAgent` response successfully. The post-ready `scheduleCancel` signed action had no simulator response, fell through to real Hyperliquid, and the real missing-agent-wallet response invalidated the local agent.
  Evidence: the new `exchangeSimulatorSnapshot` showed an `approveAgent` call with `responseStatus = "ok"` and no remaining responses before the later failure. After adding a debug-simulator default for `signedActions/scheduleCancel`, `wallet-enable-trading-simulated` passed in `tmp/browser-inspection/cu04-after-fix-fresh-2026-04-19T19-21-23-402Z-15936383`.

- Observation: the parent worktree cannot currently run the default Playwright config while another worktree owns port 8080.
  Evidence: `npx playwright test tools/playwright/test/debug-bridge-support.spec.mjs --workers=1` exited before running tests with `http://127.0.0.1:8080/ is already used`; `lsof -nP -iTCP:8080 -sTCP:LISTEN` showed a `shadow-cljs watch` process rooted at `/Users/barry/.codex/worktrees/e202/hyperopen`.

- Observation: the first broad `npm run check` attempt failed on namespace-size limits, not on behavior.
  Evidence: the command reached `lint:namespace-sizes` and reported `src/hyperopen/api/trading.cljs - namespace has 775 lines; exception allows at most 710` and `src/hyperopen/telemetry/console_preload.cljs - namespace has 544 lines; exception allows at most 543`.

- Observation: the namespace-size failure was structural, not behavioral.
  Evidence: moving only the debug exchange simulator state, queued response handling, call recording, and simulated fetch response helper into `/hyperopen/src/hyperopen/api/trading/debug_exchange_simulator.cljs` reduced `/hyperopen/src/hyperopen/api/trading.cljs` to 645 lines while preserving the existing `hyperopen.api.trading/set-debug-exchange-simulator!`, `clear-debug-exchange-simulator!`, and `debug-exchange-simulator-snapshot` wrappers. Shortening the debug bridge `registeredActionIds` function reduced `/hyperopen/src/hyperopen/telemetry/console_preload.cljs` to 543 lines. `npm run lint:namespace-sizes` now passes.

- Observation: the committed Playwright funding regression could still lose the seeded live position after entering hypothetical mode.
  Evidence: the failed repeat screenshot showed `data-position-mode="hypothetical"` with no `Use live` button, matching the no-live-position branch in the funding tooltip policy. The test seed was a one-shot direct store write after account sync freeze; a late account refresh could overwrite it. Re-applying the fixture until it survives a quiet-idle window made the repeated test stable.

- Observation: the committed Playwright wallet regression was not using the hardened app-level exchange simulator.
  Evidence: the test previously installed a Playwright route for `https://api.hyperliquid.xyz/exchange`; a failed repeat ended with `agentError="Unable to recover signer."` and the trace did not show a useful simulator-level exchange call ledger. Switching to `HYPEROPEN_DEBUG.installExchangeSimulator` allowed the test to assert both `approveAgent` consumption and the defaulted `signedActions/scheduleCancel` call.

- Observation: a full `npm run test:playwright:ci` attempt was invalidated by local port contention, not by the browser changes under review.
  Evidence: the release Playwright slice passed 6/6, but the interactive slice cascaded into `ERR_CONNECTION_REFUSED` after another worktree started `npm run test:playwright:smoke` from `/Users/barry/.codex/worktrees/5e35/hyperopen` and owned port 8080. The failed run also proved the updated diagnostic path by reporting `document.readyState`, bridge method presence, app-store presence, and startup `pageErrors`. Follow-up isolated fallback-port Playwright validation passed for the changed support spec and targeted regressions, and `qa:pr-ui` passed through the Browser Inspection managed-app fallback flow.

## Decision Log

- Decision: fix `hyperopen-qkwa` in the Browser Inspection scenario first, not by changing product z-index or removing child `data-role` hooks.
  Rationale: the failing value is a tooltip descendant role, and existing Playwright coverage relies on `data-role="active-asset-funding-position-section"`. Product z-index changes would be unjustified unless a direct probe shows the sampled element is outside the tooltip panel.
  Date/Author: 2026-04-19 / Codex

- Decision: treat `hyperopen-cu04` as a diagnosis-first simulator boundary bug until one more instrumented run proves the failing boundary.
  Rationale: the artifact confirms the simulator was installed but does not preserve enough data to show whether the approval path consumed it, skipped it, or made a second request after the queue was empty.
  Date/Author: 2026-04-19 / Codex

- Decision: harden the Playwright bridge helper by borrowing the Browser Inspection retry concept, but keep Playwright-specific implementation inside `/hyperopen/tools/playwright/support/`.
  Rationale: Playwright tests should remain deterministic and self-contained; sharing a browser-inspection CDP helper directly would couple two harnesses with different execution models.
  Date/Author: 2026-04-19 / Codex

- Decision: keep `scheduleCancel` defaulting limited to installed debug exchange simulators.
  Rationale: the Browser QA wallet path intentionally avoids real exchange behavior; once a debug simulator is installed, the agent-safety cleanup action should not escape to real Hyperliquid unless the scenario explicitly configures that failure. The non-simulated production path still uses the normal exchange request.
  Date/Author: 2026-04-19 / Codex

- Decision: keep the Playwright funding live-position stabilization in the harness instead of adding a product-only debug bridge method.
  Rationale: the product behavior is correct when the live position genuinely disappears; the failure was a fixture durability problem caused by direct test state seeding and late account refresh. The harness now verifies that the seeded live position survives quiet-idle before proceeding.
  Date/Author: 2026-04-20 / Codex

- Decision: make the Playwright wallet regression use `HYPEROPEN_DEBUG.installExchangeSimulator` and assert the exchange simulator call snapshot.
  Rationale: this aligns Playwright with Browser Inspection and tests the actual debug bridge funnel that was hardened, while preventing real Hyperliquid exchange behavior from leaking into a deterministic wallet enable-trading regression.
  Date/Author: 2026-04-20 / Codex

- Decision: reduce the Playwright debug bridge default timeout from 45s to 15s while retaining one bootstrap retry.
  Rationale: both Playwright configs have a 45s per-test timeout, so the helper's diagnostic failure path must complete inside that budget. Two 15s bridge waits plus a short retry delay leave room for navigation and the custom diagnostic error.
  Date/Author: 2026-04-20 / Codex

- Decision: treat the full Playwright CI attempt as invalid browser evidence and rely on isolated fallback-port Playwright plus Browser Inspection PR/full follow-up validation for this change.
  Rationale: the failure was caused by another worktree owning port 8080 during the interactive suite; Browser Inspection and the local fallback Playwright config both avoid that global port dependency and exercised the affected flows cleanly.
  Date/Author: 2026-04-20 / Codex

## Outcomes & Retrospective

The implementation resolves the Browser Inspection false positive, the wallet exchange simulator boundary, the Playwright debug-bridge readiness diagnostics, the committed Playwright harness flakes, and the source namespace-size blocker that stopped `npm run check`. The namespace refactor kept the public debug exchange simulator API in `hyperopen.api.trading` and moved simulator internals into a focused child namespace. Final validation passed for targeted Browser Inspection, isolated fallback-port Playwright support and regression repeats, the 15-scenario Browser Inspection nightly-tag bundle, `npm run qa:pr-ui -- --base-ref HEAD~1 --manage-local-app`, `npm run check`, `npm test`, `npm run test:websocket`, and `npm run browser:cleanup`.

## Context and Orientation

Browser Inspection scenarios are JSON files under `/hyperopen/tools/browser-inspection/scenarios/`. They run through `/hyperopen/tools/browser-inspection/src/scenario_runner.mjs`, which can call methods on the app's debug bridge, dispatch app actions, evaluate DOM expressions, and capture artifacts. The debug bridge is `globalThis.HYPEROPEN_DEBUG`, installed by `/hyperopen/src/hyperopen/telemetry/console_preload.cljs` during debug builds. The bridge exposes methods such as `qaReset`, `dispatch`, `waitForIdle`, `oracle`, `installWalletSimulator`, `installExchangeSimulator`, and `seedFundingTooltipFixture`.

`hyperopen-qkwa` tracks `trade-funding-tooltip-layering`. This scenario opens `/trade?market=BTC`, seeds active-asset funding data, clicks `[data-role='active-asset-funding-trigger']`, and samples the overlap between `[data-role='active-asset-funding-tooltip']` and neighboring panels. The scenario currently uses `element.closest("[data-role]")` to identify the top element at the sample point. That is too narrow because the tooltip itself contains nested diagnostic roles. The correct product invariant is that the sampled top element belongs to the tooltip panel, not that the nearest nested role equals the panel role.

`hyperopen-cu04` tracks `wallet-enable-trading-simulated`. The scenario opens `/trade`, installs a wallet simulator with account `0x1111111111111111111111111111111111111111`, suppresses the automatic wallet-connected handler, installs an exchange simulator with an `approveAgent` ok response, dispatches `:actions/connect-wallet`, then dispatches `:actions/enable-agent-trading`. The expected final wallet oracle is `agentStatus = "ready"` and `agentError = nil`, but Browser Inspection observes the Hyperliquid missing-agent-wallet recovery error. The relevant production path starts in `/hyperopen/runtime/action_adapters/wallet.cljs` `enable-agent-trading`, flows through `/hyperopen/wallet/agent_runtime/enable.cljs`, `/hyperopen/wallet/agent_runtime/approval.cljs`, and finally `/hyperopen/api/trading.cljs` `approve-agent!`. The relevant debug simulator functions live in `/hyperopen/src/hyperopen/telemetry/console_preload/simulators.cljs`.

`hyperopen-oomd` tracks Playwright debug-bridge hardening. Playwright tests use `/hyperopen/tools/playwright/support/hyperopen.mjs` helpers. `visitRoute` navigates to `/index.html`, waits for `HYPEROPEN_DEBUG`, calls `qaReset`, optionally dispatches navigation, and then waits for app idle. A targeted repeat run of the funding-tooltip Playwright test intermittently timed out waiting for the debug bridge, especially under parallel workers. Browser Inspection already has a stronger local-bootstrap wait in `/hyperopen/tools/browser-inspection/src/session_manager.mjs`; Playwright needs an equivalent retry and failure message that captures page URL, title, `document.readyState`, app root presence, bridge shape, and recent console/page errors.

## Plan of Work

Milestone 1 proves and fixes the funding-tooltip Browser QA false positive. Run the targeted scenario once before editing to preserve a fresh failing artifact. Change `/hyperopen/tools/browser-inspection/scenarios/trade-funding-tooltip-layering.json` so its eval result reports both the nearest nested role and an owner role. The owner role should be `active-asset-funding-tooltip` when `panel.contains(element)` is true; otherwise it should fall back to the nearest external `data-role`. Keep the existing `insidePanel` expectation and update the role expectation to the owner role. Do not remove `active-asset-funding-position-section` from `/hyperopen/src/hyperopen/views/active_asset/funding_tooltip.cljs`, because Playwright tests use it to assert live versus hypothetical content. After the change, rerun only `trade-funding-tooltip-layering` with `--manage-local-app`, confirm the scenario passes, and record the artifact path in this plan.

Milestone 2 diagnoses and fixes the wallet simulator boundary. First add a narrow diagnostic seam before changing behavior: expose an exchange simulator snapshot or event log through `HYPEROPEN_DEBUG` in `/hyperopen/src/hyperopen/telemetry/console_preload.cljs` and `/hyperopen/src/hyperopen/telemetry/console_preload/simulators.cljs`, backed by existing `/hyperopen/src/hyperopen/api/trading.cljs` simulator state. Cover this seam in `/hyperopen/test/hyperopen/telemetry/console_preload/simulators_test.cljs` or `/hyperopen/test/hyperopen/api/trading/internal_seams_test.cljs`. Then add temporary diagnostic steps to the scenario or run a local one-off eval to determine whether `approveAgent` is consumed. If the simulator is bypassed, fix the simulator installation or lookup path. If it is consumed before the manual enable action, seed enough queued ok responses or prevent the unexpected pre-consumption and cover that with a test. If it is consumed and still returns an error, fix response normalization in `/hyperopen/api/trading.cljs` or `/hyperopen/wallet/agent_runtime/approval.cljs`. The final committed scenario should not depend on real Hyperliquid network behavior.

Milestone 3 hardens the Playwright debug bridge. Refactor `/hyperopen/tools/playwright/support/hyperopen.mjs` so `waitForDebugBridge` uses a small explicit polling loop rather than bare `expect.poll`. On timeout, it should throw an error that includes diagnostic page state: current URL, title, `document.readyState`, whether `globalThis.HYPEROPEN_DEBUG` exists, which required bridge methods are present, whether the app store is present, and any console or page errors captured by the helper. Add an optional retry path for `visitRoute`: if the bridge wait times out immediately after navigation to `/index.html`, reload or re-navigate once after a short delay, then retry the bridge wait. Keep the default timeout budget compatible with existing tests.

Milestone 4 adds regression coverage for the hardening. Prefer a small Node-level test if the helper can be split into pure functions without distorting the Playwright support code. Otherwise use targeted Playwright repeat runs as the regression guard. The minimum acceptance command is the funding-tooltip Playwright test repeated serially and, if the local machine can support it, a short parallel repeat that previously timed out waiting for the bridge. The intended failure mode after hardening is either a pass or a diagnostic error that names a real missing bridge/app field, not a generic `expect.poll` timeout.

Milestone 5 validates the whole browser surface. Run the targeted Browser Inspection scenarios together:

    node tools/browser-inspection/src/cli.mjs scenario run --ids trade-funding-tooltip-layering,wallet-enable-trading-simulated --manage-local-app --run-kind browser-qa-followup

Then run the relevant Playwright regression tests:

    npx playwright test tools/playwright/test/trade-regressions.spec.mjs -g "funding tooltip transitions from live position to hypothetical estimate|wallet connect and enable trading stays deterministic" --workers=1

If both targeted commands pass, run the full browser gates:

    npm run test:playwright:ci
    npm run qa:pr-ui -- --base-ref HEAD~1 --manage-local-app
    npm run qa:nightly-ui -- --allow-non-main --manage-local-app
    npm run browser:cleanup

Because code changes will touch app and tooling surfaces, also run the required repo gates:

    npm run check
    npm test
    npm run test:websocket

## Concrete Steps

Start in `/Users/barry/.codex/worktrees/9a13/hyperopen`. Confirm the worktree is clean with `git status --short`.

Claim the three work items:

    bd update hyperopen-qkwa hyperopen-cu04 hyperopen-oomd --claim --json

Run and save the current failure for `trade-funding-tooltip-layering`:

    node tools/browser-inspection/src/cli.mjs scenario run --ids trade-funding-tooltip-layering --manage-local-app --run-kind qkwa-before-fix

Edit `/hyperopen/tools/browser-inspection/scenarios/trade-funding-tooltip-layering.json`. In the final eval expression, keep the existing `insidePanel` field and add a field such as `topOwnerRole` that returns `active-asset-funding-tooltip` when the sampled element is contained by the tooltip panel. Update the expectation from `rightSample.topDataRole = active-asset-funding-tooltip` to `rightSample.topOwnerRole = active-asset-funding-tooltip`. Keep the raw nearest role in the returned result as diagnostic data.

Rerun:

    node tools/browser-inspection/src/cli.mjs scenario run --ids trade-funding-tooltip-layering --manage-local-app --run-kind qkwa-after-fix

For `hyperopen-cu04`, add diagnostics first. Add an exchange-simulator debug snapshot method and tests for it, then run:

    node tools/browser-inspection/src/cli.mjs scenario run --ids wallet-enable-trading-simulated --manage-local-app --run-kind cu04-diagnostic

Use the diagnostic output to decide the smallest behavior fix. After the fix, rerun:

    node tools/browser-inspection/src/cli.mjs scenario run --ids wallet-enable-trading-simulated --manage-local-app --run-kind cu04-after-fix

For `hyperopen-oomd`, update `/hyperopen/tools/playwright/support/hyperopen.mjs` and run:

    npx playwright test tools/playwright/test/trade-regressions.spec.mjs -g "funding tooltip transitions from live position to hypothetical estimate|wallet connect and enable trading stays deterministic" --repeat-each=3 --workers=1

At each milestone, update this plan's `Progress`, `Surprises & Discoveries`, and `Decision Log` before moving to the next milestone.

## Validation and Acceptance

Acceptance for `hyperopen-qkwa` is that the targeted Browser Inspection scenario passes while still proving that the sampled overlap point belongs to the tooltip panel. A passing run must still report `rightOverlap = true` and `rightSample.insidePanel = true`; it must not simply remove the overlap assertion.

Acceptance for `hyperopen-cu04` is that `wallet-enable-trading-simulated` passes without relying on a real Hyperliquid exchange response. The final artifact must show the wallet connected to `0x1111111111111111111111111111111111111111`, then `agentStatus = "ready"`, `agentError = nil`, and the effect-order oracle passing for `:actions/enable-agent-trading`.

Acceptance for `hyperopen-oomd` is that a debug-bridge startup failure, if it happens, produces a diagnostic error that identifies which page or bridge field was missing. The targeted Playwright repeat should pass on a healthy local app. If the local app is genuinely broken, the failure should include URL, title, ready state, bridge method availability, and app store presence.

Overall acceptance is:

    npm run test:playwright:ci
    npm run qa:pr-ui -- --base-ref HEAD~1 --manage-local-app
    npm run qa:nightly-ui -- --allow-non-main --manage-local-app
    npm run browser:cleanup
    npm run check
    npm test
    npm run test:websocket

The final `npm run browser:cleanup` must return `ok: true`, and `git status --short` must show only intentional tracked changes before commit.

## Idempotence and Recovery

All scenario runs are safe to repeat; they write artifacts under `/hyperopen/tmp/browser-inspection/**`. If a managed browser session remains after a failed command, run `npm run browser:cleanup` before retrying. If the wallet diagnostic exposes sensitive raw request data, redact it before writing any durable QA note; artifact paths may remain under `tmp/`.

The funding tooltip fix should be limited to the scenario assertion unless a fresh probe shows `insidePanel = false` or the top sampled element is outside the tooltip. If that happens, stop and update this plan because the task becomes a real product layout regression rather than a harness false positive.

The wallet fix should be one boundary fix at a time. Do not combine simulator diagnostics, response normalization, and scenario queue changes in a single untested patch. If three hypotheses fail, pause and record the architecture question in the `Decision Log` before making another attempt.

## Artifacts and Notes

Baseline artifacts from the full QA run are:

    /Users/barry/.codex/worktrees/9a13/hyperopen/tmp/browser-inspection/scenario-rerun-2026-04-19T18-18-25-852Z-4b0a4538
    /Users/barry/projects/hyperopen/tmp/browser-inspection/baseline-scenario-rerun-2026-04-19T18-19-24-565Z-c0164604
    /Users/barry/.codex/worktrees/9a13/hyperopen/tmp/browser-inspection/nightly-ui-qa-2026-04-19T18-22-48-719Z-9d29e7f8

The `explorer` subagent for `hyperopen-qkwa` reported no file edits and concluded that the current evidence points to a Browser QA false positive rather than a product z-index regression. It recommended changing the scenario hit-test to preserve raw nearest-role diagnostics while asserting tooltip ownership through `panel.contains(element)`.

## Interfaces and Dependencies

Do not introduce new browser harnesses. Use the existing Browser Inspection scenario runner, `HYPEROPEN_DEBUG` bridge, and Playwright helpers.

The scenario file `/hyperopen/tools/browser-inspection/scenarios/trade-funding-tooltip-layering.json` must continue to click the real `[data-role='active-asset-funding-trigger']` and sample the real overlap geometry.

The wallet simulator interface remains debug-only through `HYPEROPEN_DEBUG.installWalletSimulator` and `HYPEROPEN_DEBUG.installExchangeSimulator`. Any new diagnostic method must be exposed only on the debug bridge and covered by ClojureScript tests.

The Playwright interface remains:

    waitForDebugBridge(page, timeoutMs)
    debugCall(page, method, ...args)
    visitRoute(page, route, options)

Call sites should not need broad rewrites. If new options are added, they should be optional and default to current behavior plus better retry and diagnostics.

Plan update note (2026-04-19): created this ExecPlan before implementing the requested follow-ups. The plan incorporates the initial QA artifacts and the read-only funding-tooltip subagent finding.
