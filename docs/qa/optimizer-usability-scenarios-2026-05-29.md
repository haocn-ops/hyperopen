# Optimizer Usability Scenarios - 2026-05-29

## Scope

This note records an exploratory usability pass on `/portfolio/optimize/new` and optimizer result/frontier rendering. The pass used a mix of manually selected assets and read-only existing portfolios to evaluate whether a user can understand setup readiness, rebalance output, efficient-frontier output, and backend-history failures.

The goal was not to change application code. This is a QA finding document for prioritizing frontend and optimizer-history-service improvements.

## Environment

- App route: `http://127.0.0.1:8081/portfolio/optimize/new`
- Dev command: `npm run dev:browser-inspection`
- Local runtime note: the default Java 22 runtime hit a Closure compiler failure while compiling externs, so the dev server was restarted with local Temurin Java 21.
- Browser tools:
  - Codex in-app Browser for exploratory live flows.
  - Headless Playwright for a repeatable "Use my views" search/editor probe.
- Temporary artifacts:
  - `tmp/browser-inspection/optimizer-usability-2026-05-29/scenario-1-after-run.png`
  - `tmp/browser-inspection/optimizer-usability-2026-05-29/scenario-2-after-run.png`
  - `tmp/browser-inspection/optimizer-usability-2026-05-29/scenario-3-second-portfolio-after-run.png`
  - `tmp/browser-inspection/optimizer-usability-2026-05-29/scenario-4-use-my-views-summary.json`
  - `tmp/browser-inspection/optimizer-usability-2026-05-29/scenario-4b-use-my-views-explicit-summary.json`

## Scenario Matrix

| Scenario | Setup | Outcome |
| --- | --- | --- |
| Manual four-asset run | Added BTC, ETH, SOL, and HYPE; selected Maximum Sharpe; ran optimization without connected/spectated current portfolio. | Solver completed and rendered efficient frontier plus standalone asset overlays. Result allocated 50% BTC and 50% HYPE. |
| Existing portfolio 0x162c...8185 | Opened read-only portfolio, then optimizer with `?spectate=0x162cc7c861ebd0c06b3d72319201150482518185`; used From holdings. | 22 instruments added. Setup showed Ready to run even though warnings already indicated no usable common history. Run failed with no eligible history. |
| Existing portfolio 0x7c93...c8fd | Opened optimizer with `?spectate=0x7c930969fcf3e5a5c78bcf2e1cefda3f53e3c8fd`; used From holdings. | 132 instruments added despite the visible `cap: 25 assets`. Run accepted, then failed/timed out waiting for history prefetch to settle. |
| Use my views search/editor | Fresh optimizer, selected Use my views, typed BTC, ETH, HYPE into Manual Add and pressed Enter. | Search added vaults containing the terms instead of the expected perp instruments. Editor rendered view rows for those vaults, but requests produced repeated 429s and missing vault history. |
| Use my views explicit ids | Fresh optimizer, selected Use my views, typed `perp:BTC`, `perp:ETH`, `perp:HYPE`. | Search returned no matching unused instruments. Run stayed disabled because no assets were added. |

## Findings

### P1 - Readiness says runnable when history already proves the run cannot succeed

The 0x162c...8185 From holdings flow showed "Ready to run" and an enabled Run button while the same surface already listed `insufficient-candle-history`, `insufficient-common-history`, and `validation-failed` warnings, including "only 0 usable shared return observations." The run then failed with "No eligible history was available."

The 0x7c93...c8fd flow likewise showed "Ready to run" for 132 assets before failing with "Timed out waiting for optimizer history prefetch to settle" and a long list of `MISSING-CANDLE-HISTORY` entries.

Recommendation: turn known no-eligible-history and no-common-history states into setup-blocking readiness. Either disable Run or offer an explicit "run eligible subset" path that tells the user which assets will be dropped.

### P1 - From holdings bypasses or contradicts the 25-asset universe cap

The large existing portfolio imported 132 assets while the UI continued to display `cap: 25 assets`. This makes the cap look decorative and sends a user into a long, predictably failing run.

Recommendation: enforce the cap in From holdings, or change the cap copy to explain the real limit and provide automatic filtering, such as largest current weights, eligible-history assets only, or user-selected subsets.

### P1 - Live backend/history failures are not actionable enough

The live flows surfaced raw backend and client failure details:

- `stale-history BTC/ETH/SOL/HYPE: Cached history is stale.`
- `hl:spot:HPL#120/USDC#0: backend validation rejected optimizer history`
- `MISSING-CANDLE-HISTORY`
- `MISSING-VAULT-HISTORY`
- repeated 429 resource errors in the browser console during Use my views search/editor probing

These messages tell an engineer something failed, but they do not tell a portfolio user what to do next or whether the recommendation is trustworthy.

Recommendation: have the history service or client adapter return an eligibility summary grouped by user-facing asset label and reason. The frontend should show counts, impact, freshness age, and direct remediation actions such as remove ineligible assets, retry stale data, or reduce universe.

### P1 - Search/Enter selects surprising instruments for common tickers

In a fresh Use my views setup, typing BTC, ETH, and HYPE and pressing Enter added vaults:

- Long HYPE & BTC | Short Garbage
- BTC/ETH CTA | AIM
- Hyperliquidity Provider (HLP)

Typing explicit ids such as `perp:BTC` returned "No matching unused instruments found." A user trying to build a simple BTC/ETH/HYPE view can end up with vault rows and missing-vault-history failures.

Recommendation: rank exact ticker/perp matches ahead of vault names when the query is a simple ticker. If ids are not supported, do not show a dead-end result for `perp:BTC`; consider parsing it or offering the closest asset.

### P2 - Manual no-account results use current-to-target language without a current portfolio

The successful manual run showed KPI labels such as "Volatility - current -> target", "Expected Return - current -> target", and "Sharpe - current -> target", but only target values were present. Allocation rows showed current 0%, target weights, and `$0` delta values because no current portfolio or account value was loaded.

Recommendation: when no current portfolio is available, switch labels to target-only mode and hide rebalance dollar deltas or explicitly state that rebalance dollars require a current/spectated portfolio value.

### P2 - Spot/perp identity is confusing in holdings imports

The 0x162c...8185 import showed rows like `PURR-USDC` and `HYPE-USDC` with Type `spot`, while the remove controls were labelled `Remove perp:PURR` and `Remove perp:HYPE`. The visible row and control identity disagree.

Recommendation: audit the instrument identity mapping between holdings import, selected-universe rows, accessibility labels, constraints, history keys, and backend ids. Use one user-facing identity per row and preserve backend ids only in diagnostics.

### P2 - Failure presentation is too verbose and under-prioritized

Failed runs showed a progress block with every step at 0%, then a long raw error/warning stream. The important summary is buried: no eligible/common history, missing history, validation failures, or prefetch timeout.

Recommendation: lead with the portfolio-level blocker, then group assets under collapsible reason buckets. Keep raw diagnostics available behind a details affordance for support/debugging.

### P2 - Spectate context is fragile across routes

Visiting `/portfolio/trader/:address` without enabling Spectate Mode explicitly warns that leaving the route returns to normal account context. The optimizer requires a `?spectate=` route query to retain read-only current portfolio context. That is easy to miss while exploring an existing portfolio.

Recommendation: provide a clear "Optimize this portfolio" entry point from the trader/portfolio page that opens the optimizer with the correct account context and carries a visible read-only status.

### P2 - Activity/toast noise can distract from optimizer setup

While working in spectated optimizer routes, trade/fill activity messages appeared in the global surface. For optimization setup, these are not directly related to the task and can make it harder to focus on readiness/failure messaging.

Recommendation: consider suppressing or grouping unrelated account activity while the optimizer setup/failure surface is active, especially in read-only Spectate Mode.

## Frontier Observations

The manual four-asset Maximum Sharpe scenario did render the efficient frontier, target marker, and standalone asset overlays. That part of the happy path is discoverable once a run solves.

The existing-portfolio and Use my views live scenarios did not reach a usable frontier because setup/search/history failures blocked or undermined the run. This is the main usability gap: real holdings and common user search paths fail before users can evaluate the frontier.

## Backend-Service Observations

The frontend appears dependent on a live history/freshness service that can return stale cache, no candle history, missing vault history, validation failures, and rate limits. The raw service outcomes are currently shown close to the user-facing workflow.

The service contract would be more useful if it returned a portfolio-level eligibility report:

- eligible assets and observation counts
- ineligible assets grouped by reason
- common-history window and minimum required observations
- freshness age and refresh/retry status
- rate-limit status and retry-after hints
- stable user-facing asset labels alongside backend ids

That would let the UI block impossible runs before the worker phase and present a smaller, actionable readiness panel.

## Browser-QA Accounting

This was exploratory QA, not a code-change signoff.

- Visual pass: FAIL. The successful frontier exists, but failure/readiness states are misleading and too noisy.
- Interaction pass: FAIL. Run is enabled for setups that are already known to be un-runnable; From holdings can exceed the displayed cap.
- Native-control pass: PASS, limited. The inspected setup controls were expected search/input/checkbox/button controls; no unexpected native select/date/file/color controls were observed.
- Layout/styling pass: BLOCKED. I did not complete the full governed 375/768/1280/1440 visual pass because the task was exploratory and the major blockers were functional/readiness issues.
- Jank/performance pass: FAIL, limited. The 132-asset holdings import left many rows loading and made full-page capture unreliable; the live backend also produced repeated 429s in the Use my views probes.

## Commands Run

1. `npm ci`
   - Result: PASS. Installed dependencies. NPM reported existing audit vulnerabilities.
2. `npm run dev:browser-inspection`
   - Result: initial BLOCKED under Java 22 due Closure compiler failure; PASS after restarting with local Temurin Java 21.
3. Browser MCP exploratory runs against `http://127.0.0.1:8081`.
   - Result: completed scenarios 1-3.
4. Headless Playwright one-off probes for Use my views search/editor behavior.
   - Result: completed scenarios 4 and 4b; recorded JSON summaries under `tmp/browser-inspection/optimizer-usability-2026-05-29/`.

## Remaining Risks

- I did not run `npm run check`, `npm test`, or `npm run test:websocket` because no application code changed.
- I did not create deterministic regression tests from these findings.
- Live backend results may vary with rate limits, cache freshness, and available history; the documented failures are still valid UX risks because they were surfaced directly to a user-facing flow.
