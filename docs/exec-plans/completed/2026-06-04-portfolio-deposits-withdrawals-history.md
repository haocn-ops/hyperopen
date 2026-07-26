# Portfolio Deposits and Withdrawals History

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document follows `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The `/portfolio` account table has a `Deposits & Withdrawals` tab, but it currently shows an action card for opening funding modals instead of the historical account ledger that users expect from the Hyperliquid main client. After this change, a user on their own portfolio route can open that tab and see historical non-funding ledger events: deposits, withdrawals, vault movements, transfers, genesis distributions, account value changes, fees when present, and transaction links when a hash is available. This should use Hyperliquid's real ledger API data, not a local placeholder.

Hyperliquid documents the relevant source as `POST https://api.hyperliquid.xyz/info` with body type `userNonFundingLedgerUpdates`, `user`, required `startTime`, and optional `endTime`. The official Python SDK wraps the same request as `Info.user_non_funding_ledger_updates(user, startTime, endTime)`, and the Rust SDK models the result as ledger update variants including `Deposit`, `Withdraw`, `InternalTransfer`, `SubAccountTransfer`, `VaultDeposit`, `VaultWithdraw`, `AccountClassTransfer`, `SpotTransfer`, and `SpotGenesis`. In this repository, `src/hyperopen/api/endpoints/account/portfolio.cljs` already has a tested wrapper for this endpoint, and `src/hyperopen/startup/collaborators.cljs` already stores raw rows under `[:portfolio :ledger-updates]` after loading portfolio summary data.

## Context References

Public refs:

- Direct user request in this Codex session: fix the portfolio page `Deposits and Withdrawals` tab so it shows history like the attached Hyperliquid main-client screenshot.

Repo artifacts:

- `/hyperopen/AGENTS.md` requires an active ExecPlan, multi-agent orchestration with exact agent names, and `npm run check`, `npm test`, and `npm run test:websocket` for code changes.
- `/hyperopen/docs/FRONTEND.md` requires deterministic Playwright coverage where a stable UI path can be asserted and browser QA accounting for visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf passes.
- `/hyperopen/src/hyperopen/views/portfolio/account_tabs.cljs` currently registers `:deposits-withdrawals` as an extra portfolio tab and renders a funding action card.
- `/hyperopen/src/hyperopen/api/endpoints/account/portfolio.cljs` already sends `userNonFundingLedgerUpdates` requests and normalizes wrapped response shapes to a vector.
- `/hyperopen/src/hyperopen/startup/collaborators.cljs` already fetches portfolio ledger rows after `request-portfolio!` and stores them in `[:portfolio :ledger-updates]`.
- `/hyperopen/src/hyperopen/views/account_info_view.cljs` and `/hyperopen/src/hyperopen/views/account_info/tabs/funding_history.cljs` show the existing account table pattern for sort headers, loading state, empty state, pagination, and compact table density.
- `/hyperopen/src/hyperopen/views/vaults/detail/activity/tables.cljs` and `/hyperopen/src/hyperopen/vaults/adapters/webdata.cljs` show an existing but vault-specific normalization and table for deposit/withdraw ledger events.

Local scratch refs (non-authoritative):

- Multi-agent sidecar tasks were started in this session: `explorer` for current code flow, `acceptance_test_writer` for happy-path coverage, and `edge_case_test_writer` for boundary coverage. Their outputs should inform implementation if they return before completion, but this ExecPlan is authoritative for the current rollout.

## Progress

- [x] (2026-06-04 01:55Z) Read repo operating docs, UI/browser QA docs, planning docs, and relevant Superpowers skills.
- [x] (2026-06-04 02:05Z) Researched Hyperliquid `userNonFundingLedgerUpdates` contract and reference SDK behavior.
- [x] (2026-06-04 02:15Z) Traced local root cause: the portfolio tab renders a funding action card even though the app already fetches raw non-funding ledger rows into `[:portfolio :ledger-updates]`.
- [x] (2026-06-04 02:25Z) Created this active ExecPlan before source edits.
- [x] (2026-06-04 02:35Z) Incorporated sidecar `explorer`, `acceptance_test_writer`, and `edge_case_test_writer` findings into the implementation direction.
- [x] (2026-06-04 02:42Z) Wrote failing tests for ledger normalization, portfolio tab rendering, and startup ledger loading/fallback/error behavior.
- [x] (2026-06-04 02:44Z) Verified RED with `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test`; compile failed because `hyperopen.domain.account-ledger` does not exist.
- [x] (2026-06-04 03:10Z) Implemented ledger domain normalization, table rendering, and portfolio tab wiring.
- [x] (2026-06-04 03:15Z) Improved ledger loading/error projection and sparse-summary fallback in startup state.
- [x] (2026-06-04 03:20Z) Ran focused tests with bundled Node: `/Users/barry/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node out/test.js --test=hyperopen.domain.account-ledger-test --test=hyperopen.views.portfolio-view-test --test=hyperopen.startup.collaborators-test`; 22 tests, 217 assertions, 0 failures.
- [x] (2026-06-04) Ran required repo gates: `npm test`, `npm run check`, and `npm run test:websocket` all passed with the bundled Node runtime.
- [x] (2026-06-04) Added and ran Playwright browser coverage for the changed ledger tab, including widths `375`, `768`, `1280`, and `1440`.
- [x] (2026-06-04) Added lifecycle invariant coverage for the new account-derived `:ledger-loading?` state.
- [x] (2026-06-04) Ran portfolio route smoke for desktop and mobile `/portfolio` and `/portfolio/trader/<address>`.
- [x] (2026-06-04) Browser cleanup completed with no remaining browser-inspection sessions.
- [x] (2026-06-04) Move this plan to completed after acceptance passes.

## Surprises & Discoveries

- Observation: the endpoint wrapper already exists and is tested, so this is not an API discovery problem.
  Evidence: `src/hyperopen/api/endpoints/account/portfolio.cljs` defines `request-user-non-funding-ledger-updates!`, and `test/hyperopen/api/endpoints/account_accounting_test.cljs` asserts the request body uses `"type" "userNonFundingLedgerUpdates"`.

- Observation: the broken portfolio tab is an extra tab renderer, not a standard account-info tab.
  Evidence: `src/hyperopen/views/portfolio/account_tabs.cljs` maps `:deposits-withdrawals` to `deposits-withdrawals-card`, while trader inspection routes intentionally omit that extra tab.

- Observation: the startup path stores raw ledger rows but has no explicit `:ledger-loading?` flag.
  Evidence: `src/hyperopen/state/app_defaults.cljs` has `:ledger-updates`, `:ledger-error`, and `:ledger-loaded-at-ms`, but not a loading field. `src/hyperopen/startup/collaborators.cljs` writes success/error fields after the request.

- Observation: the sidecar `explorer` confirmed that the smallest integration seam is replacing the portfolio extra-tab body and reading `[:portfolio :ledger-updates]` plus optional `[:orders :ledger]`, not adding a new tab registry entry.
  Evidence: The explorer identified `src/hyperopen/views/portfolio/account_tabs.cljs`, `src/hyperopen/startup/collaborators.cljs`, and `src/hyperopen/websocket/user_runtime/handlers.cljs` as the exact current seams.

- Observation: the acceptance and edge-case sidecars emphasized malformed-row dropping, deduplication between REST and websocket rows, default `Completed` status, hash-backed explorer links, and retaining trader-route hiding.
  Evidence: Their proposal output names focused CLJS tests for normalization, table rendering, loading/error/empty states, and startup ledger error/fallback handling.

- Observation: RED verification failed for the intended first reason.
  Evidence: `npx shadow-cljs --force-spawn compile test` reported `The required namespace "hyperopen.domain.account-ledger" is not available`.

- Observation: The system `node` executable at version `v24.0.2` fails existing async startup tests before ledger assertions, while the Codex-bundled Node `v24.14.0` runs the same namespace correctly.
  Evidence: `node out/test.js --test=hyperopen.startup.collaborators-test` raised `(intermediate value).then is not a function` in an existing async test; `/Users/barry/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node out/test.js --test=hyperopen.startup.collaborators-test` ran 10 tests with 42 assertions and 0 failures.

- Observation: The prior Playwright regression for portfolio funding openers encoded the broken tab behavior by expecting in-tab funding action buttons.
  Evidence: `tools/playwright/test/portfolio-regressions.spec.mjs` expected `portfolio-funding-action-deposit`, `portfolio-funding-action-transfer`, and `portfolio-funding-action-withdraw` after selecting `deposits-withdrawals`; the updated regression now keeps header funding modal coverage and asserts the ledger table instead.

- Observation: Browser test fixtures must seed an effective account before injecting account-derived ledger rows.
  Evidence: The first Playwright attempt failed with the account lifecycle invariant `No effective account must not retain account-derived surfaces` for `[:portfolio :ledger-updates]`; seeding `[:wallet :address]` before ledger rows resolved the invariant.

## Decision Log

- Decision: Reuse the existing Hyperliquid REST wrapper and startup portfolio ledger fetch instead of adding a new endpoint or a portfolio-only browser fetch.
  Rationale: The endpoint is already tested, request-policy guarded, and available through the API instance/gateway boundary. Reusing it preserves side effects in infrastructure boundaries and avoids duplicate request orchestration.
  Date/Author: 2026-06-04 / Codex

- Decision: Keep `:deposits-withdrawals` as a portfolio-only extra tab rather than adding it to the base account-info tab registry.
  Rationale: The user asked for the portfolio page, and existing tests intentionally hide this tab on read-only trader inspection routes. Keeping the tab portfolio-only avoids changing the trade account panel and avoids exposing account ledger history in unrelated surfaces.
  Date/Author: 2026-06-04 / Codex

- Decision: Normalize all non-funding ledger rows into a stable view model, not just external bridge deposits and withdrawals.
  Rationale: The attached Hyperliquid screenshot includes deposits, sends, vault deposits, transfers, and genesis distributions under the same tab. Hyperliquid SDK variants also describe multiple balance-moving event types.
  Date/Author: 2026-06-04 / Codex

- Decision: Add a small `:ledger-loading?` state flag in the existing portfolio startup path.
  Rationale: The tab needs to distinguish an in-flight ledger fetch from a genuinely empty ledger. A dedicated flag is clearer and avoids overloading portfolio summary loading.
  Date/Author: 2026-06-04 / Codex

## Outcomes & Retrospective

Completed. The portfolio `Deposits & Withdrawals` tab now renders a history table backed by Hyperliquid non-funding ledger rows instead of the old explanatory funding action card. The table presents the Hyperliquid-main-client-style columns `Time`, `Status`, `Action`, `Source`, `Destination`, `Account Value Change`, and `Fee`, with normalized labels for deposits, withdrawals, vault movements, transfers, sends, and genesis distributions.

Validation completed:

- `PATH=/Users/barry/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:$PATH npm test`: 4145 tests, 22952 assertions, 0 failures.
- `PATH=/Users/barry/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:$PATH npm run check`: passed, including lint/tooling checks and Shadow builds for `app`, `portfolio`, `portfolio-worker`, `portfolio-optimizer-worker`, `vault-detail-worker`, and `test`.
- `PATH=/Users/barry/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:$PATH npm run test:websocket`: 527 tests, 3068 assertions, 0 failures.
- `PATH=/Users/barry/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:$PATH npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "portfolio funding openers launch|portfolio deposits and withdrawals tab renders"`: 2 passed. The ledger table test covers widths `375`, `768`, `1280`, and `1440`.
- `PATH=/Users/barry/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:$PATH npx playwright test tools/playwright/test/routes.smoke.spec.mjs --grep "portfolio.*@smoke"`: 4 passed for desktop and mobile portfolio/trader portfolio route smoke.
- `npm run browser:cleanup`: completed; final run stopped no sessions.

Browser QA accounting:

- Visual pass: PASS. The changed surface renders the expected ledger table and screenshot-aligned columns/actions/amount signs at all required widths through the Playwright regression.
- Native-control pass: PASS. The changed ledger table contains no visible `input`, `select`, `textarea`, or `button` controls in the multi-width Playwright assertion.
- Styling-consistency pass: PASS. The table reuses account-info table/content primitives and the repository style/hiccup checks in `npm run check` passed.
- Interaction pass: PASS. The transaction explorer link is focusable at all required widths, header funding openers still launch and close the funding modal, and route smoke remains green.
- Layout-regression pass: PASS. The multi-width browser assertion verified the table has positive dimensions and no document-level horizontal overflow at `375`, `768`, `1280`, and `1440`.
- Jank/perf pass: PASS. The Playwright test resizes the active ledger tab through all required widths and waits for idle without layout instability or timeout after removing repeated cold loads from the test.

## Context and Orientation

The main portfolio view composes the account table through `src/hyperopen/views/portfolio/account_tabs.cljs`. The tab strip itself comes from `src/hyperopen/views/account_info_view.cljs`, which accepts `extra-tabs` so portfolio-specific tabs can be mixed into the standard account tabs. Standard tabs such as balances, positions, funding history, trade history, and order history are handled by `src/hyperopen/views/account_info/tabs/**`.

The existing `Deposits & Withdrawals` portfolio tab is not a data table. It is an extra tab that renders `deposits-withdrawals-card`, a card with Deposit, Withdraw, and Transfer buttons plus explanatory copy. The user's screenshot shows a ledger table with columns `Time`, `Status`, `Action`, `Source`, `Destination`, `Account Value Change`, and `Fee`; this plan replaces the card content with an account ledger table while leaving the existing top-level portfolio Deposit/Withdraw buttons alone.

The data source is already available. `src/hyperopen/api/endpoints/account/portfolio.cljs` builds the Hyperliquid info request with `"type" "userNonFundingLedgerUpdates"`, `"user"`, `"startTime"`, and `"endTime"`, and returns a vector from several possible wrapped payload shapes. `src/hyperopen/startup/collaborators.cljs` calls that wrapper after `request-portfolio!`, using the min and max timestamps from portfolio account-value history. The fetched raw rows are currently stored under `[:portfolio :ledger-updates]` but are not rendered on the portfolio page.

The term "non-funding ledger update" means a Hyperliquid account balance event that is not a recurring funding payment. It includes external deposits/withdrawals, internal sends, account-class transfers, vault deposits/withdrawals, vault distributions, spot transfers, genesis distributions, liquidations, rewards claims, and other balance-moving events. Funding payments remain in the existing `Interest` tab, which is backed by `userFunding`.

## Plan of Work

First, add a domain module under `src/hyperopen/domain/account_ledger.cljs` that accepts raw Hyperliquid ledger rows and returns stable rows for display. Each normalized row should include `:id`, `:time-ms`, `:status-label`, `:action-label`, `:source-label`, `:destination-label`, `:asset`, `:amount`, `:signed-amount`, `:amount-text`, `:fee-text`, `:hash`, and `:explorer-url`. The normalizer should be permissive: malformed rows are dropped, unknown ledger types become title-cased action labels, and numeric values can come from strings or numbers. Known mappings should produce screenshot-like labels: `deposit` -> `Deposit` from `Arbitrum` to `Trading Account`, `withdraw` -> `Withdrawal` from `Trading Account` to `Arbitrum`, `vaultDeposit` -> `Vault Deposit`, `vaultWithdraw` -> `Vault Withdrawal`, `spotGenesis` -> `Genesis Distribution`, `internalTransfer` and `spotTransfer` -> `Send`, and `accountClassTransfer` -> `Transfer`.

Second, add tests before implementation. Create `test/hyperopen/domain/account_ledger_test.cljs` to cover the normalizer and sorting. Add a portfolio view test in `test/hyperopen/views/portfolio_view_test.cljs` that selects `:deposits-withdrawals`, provides representative raw ledger rows under `[:portfolio :ledger-updates]`, and expects the table headers and values from the screenshot contract. Update `test/test_runner_generated.cljs` if this repository requires manual test namespace inclusion.

Third, replace the extra tab renderer in `src/hyperopen/views/portfolio/account_tabs.cljs`. Remove the card-specific private button constants only if they are no longer used elsewhere. Add a table renderer that uses the account-info table density and compact tab style. The table should be scrollable inside the existing fixed-height account panel and should not add a second nested card. It should render loading, error, and empty rows. Transaction hashes should be presented as short links when available, with an external-link icon or accessible label. Amount deltas must include explicit `+` or `-` signs and asset suffixes so color is not the only meaning.

Fourth, update startup ledger state. In `src/hyperopen/state/app_defaults.cljs`, add `:ledger-loading? false` to `default-portfolio-state`. In `src/hyperopen/startup/runtime.cljs`, clear `:ledger-loading?` on disconnect. In `src/hyperopen/startup/collaborators.cljs`, set `:ledger-loading? true` just before requesting ledger rows and set it false on success, error, and skipped fetch. If portfolio summary has no usable account-value timestamps, request ledger rows from `0` to `platform/now-ms` so the tab can still show a user's deposit history on sparse accounts.

Fifth, update tests around startup state. Extend `test/hyperopen/startup/collaborators_test.cljs` to assert `:ledger-loading?` ends false after success and to add a no-summary-window fallback test expecting a `0` to current-time ledger request. Update account lifecycle defaults tests if they assert the exact cleared portfolio map.

Sixth, run validation. Run focused tests first, then `npm test`, `npm run check`, and `npm run test:websocket`. Because this touches UI, run the smallest relevant Playwright route smoke for `/portfolio` if available, then account for the six browser QA passes and the four widths required by `/hyperopen/docs/agent-guides/browser-qa.md`. If browser tooling cannot run in the current environment, record the exact blocker in this plan and final response.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/1787/hyperopen`.

1. Write the failing domain test:

   Add `test/hyperopen/domain/account_ledger_test.cljs` with assertions that mixed raw rows normalize to descending time order and produce labels like `Deposit`, `Vault Deposit`, `Genesis Distribution`, `Send`, and signed amount text like `+100 USDC` and `-1 HYPE`.

2. Run the domain test and expect failure because `hyperopen.domain.account-ledger` does not exist:

   `npx shadow-cljs compile test`

   The expected RED signal is a missing namespace or unresolved var for `hyperopen.domain.account-ledger`.

3. Add the domain module at `src/hyperopen/domain/account_ledger.cljs` and rerun the focused test until it passes.

4. Write the failing portfolio view test:

   Modify `test/hyperopen/views/portfolio_view_test.cljs` to select `:deposits-withdrawals` on `sample-state` with raw ledger rows and assert visible table headers and rows. The expected RED signal before UI implementation is that the old action-card copy appears and the ledger table data does not.

5. Replace `deposits-withdrawals-card` in `src/hyperopen/views/portfolio/account_tabs.cljs` with a ledger table renderer.

6. Add or update startup tests in `test/hyperopen/startup/collaborators_test.cljs` and default/clear-state tests as needed, then update startup state handling.

7. Run focused tests:

   `npx shadow-cljs compile test`

   If the generated test runner is stale, update `test/test_runner_generated.cljs` consistently with existing namespace ordering and rerun.

8. Run required gates:

   `npm run check`
   `npm test`
   `npm run test:websocket`

9. Run browser verification:

   Prefer the smallest committed Playwright command that can exercise `/portfolio`, such as `npm run test:playwright:smoke` if it includes portfolio smoke coverage. If a narrower portfolio test command exists after inspection, run that first. If Browser MCP or browser-inspection sessions are created, finish with `npm run browser:cleanup`.

## Validation and Acceptance

The change is accepted when all of these are true:

- The portfolio `Deposits & Withdrawals` tab renders a history table instead of the old explanatory action card on the user's own `/portfolio` route.
- The table includes `Time`, `Status`, `Action`, `Source`, `Destination`, `Account Value Change`, and `Fee` columns.
- Representative Hyperliquid rows normalize into screenshot-like rows: deposits show positive USDC, withdrawals/sends show negative amounts, vault and genesis rows have human-readable action labels, and missing fees render as `--`.
- Loading, error, and empty states are user-visible and do not change the outer account panel height.
- The read-only `/portfolio/trader/<address>` route still hides the `Deposits & Withdrawals` tab.
- Tests for normalization, portfolio rendering, and startup ledger loading pass.
- Required gates pass: `npm run check`, `npm test`, and `npm run test:websocket`.
- Browser QA is explicitly accounted for across visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf passes at widths `375`, `768`, `1280`, and `1440`, or any blocker is reported with evidence.

## Idempotence and Recovery

All source changes are additive or local replacements. Re-running the ledger normalizer is pure and has no side effects. The startup fetch remains guarded by the effective account address check, so stale portfolio or ledger responses must not overwrite a newer wallet address. If the ledger fetch fails, portfolio summary data should remain visible and the ledger tab should show the ledger error without breaking the rest of the page.

If tests fail because the generated test runner does not include the new namespace, update `test/test_runner_generated.cljs` using the existing namespace list pattern. If browser QA starts a Browser MCP or browser-inspection session and then fails, run `npm run browser:cleanup` before stopping.

## Artifacts and Notes

Key endpoint request shape embedded for implementation:

    {"type" "userNonFundingLedgerUpdates"
     "user" "0x..."
     "startTime" 0
     "endTime" 1780000000000}

Representative raw row shape:

    {:time 1770000000000
     :hash "0xabc..."
     :delta {:type "deposit"
             :usdc "100.0"}}

Expected display row shape:

    {:time-ms 1770000000000
     :status-label "Completed"
     :action-label "Deposit"
     :source-label "Arbitrum"
     :destination-label "Trading Account"
     :amount-text "+100 USDC"
     :fee-text "--"}

## Interfaces and Dependencies

Create `hyperopen.domain.account-ledger` with these public functions:

    (normalize-ledger-row row) => map or nil
    (normalize-ledger-rows rows) => vector sorted newest first
    (merge-ledger-rows primary secondary) => vector deduped by id/hash/time/type/amount

Add `hyperopen.views.portfolio.account-tabs/deposits-withdrawals-table` as a private renderer that accepts the full app state and reads:

    [:portfolio :ledger-updates]
    [:orders :ledger]
    [:portfolio :ledger-loading?]
    [:portfolio :ledger-error]

`merge-ledger-rows` should let startup REST rows and websocket rows coexist without duplicate display. The startup REST rows remain the historical source of truth; websocket rows can add recent live updates when present.

Revision note 2026-06-04 / Codex: Initial plan created from user request, Hyperliquid API documentation, reference SDK behavior, and local source tracing. The plan scopes implementation to portfolio-only rendering plus a small ledger loading-state fix because the endpoint wrapper already exists.
