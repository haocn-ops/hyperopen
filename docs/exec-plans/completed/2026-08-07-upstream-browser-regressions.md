# Repair browser regressions introduced by the upstream merge

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This plan follows `docs/PLANS.md` and `.agents/PLANS.md`.

## Purpose / Big Picture

After the upstream merge, a trader must again be able to use account-scoped trade, portfolio, optimizer, vault, wallet, and referral flows at their supported widths without a browser regression. In particular, routing an order through a selected subaccount must retain its `vaultAddress`, wallet enablement and remembered sessions must complete deterministically, and optimizer and vault controls must remain usable rather than clipped, stale, or disconnected from their state.

The result is observable through the committed Playwright suite: the 21 failures recorded below pass, the one recorded flaky test no longer flakes, the smoke suite has no failures, and the release SEO suite remains green. The user subsequently authorized pushing the reviewed branch to the existing GitHub fork and updating the existing Cloudflare Worker. This remains a repair release: it does not authorize data migrations, Mainnet opening, custom-domain changes, or public API changes.

## Context References

Public refs: direct maintainer request, received 2026-08-07, to “fix all browser regressions found after upstream merge.” The request explicitly scopes subaccount `vaultAddress` routing, wallet enablement, Builder Fee review, portfolio address scoping, mobile balances navigation, optimizer controls, volume history, product context, referrals, outcome markets, vault lifecycle, and remembered-session persistence. It also explicitly permits correcting stale test expectations where the product contract changed: the optimizer objective menu now has **five** options because Equal Risk is the fifth.

Repo artifacts: `tmp/playwright/report/interactive/index.html` is the baseline interactive report; `tmp/playwright/test-results/interactive/` contains the retained screenshots, accessibility snapshots, and traces; `tools/playwright/test/**` contains the committed contracts. The baseline release SEO report is `tmp/playwright/report/release/index.html`. Governing browser and UI guidance is `docs/BROWSER_TESTING.md`, `docs/agent-guides/browser-qa.md`, `docs/FRONTEND.md`, `docs/agent-guides/ui-foundations.md`, `docs/agent-guides/trading-ui-policy.md`, and `docs/BROWSER_STORAGE.md`.

Local scratch refs (non-authoritative): the untracked `test-results/` directory was already present when planning began. Do not use it as the only record of a result. No Beads / `bd` record is required or authoritative.

## Scope And Non-Goals

This work repairs only the post-merge browser regressions in the frozen test contract and the single observed flaky asset-selector test. Preserve public APIs, route shapes, tenant behavior, deterministic websocket decisions, and the existing simulated-wallet testing boundary. Keep browser storage reads and writes at infrastructure boundaries; do not add credentials to browser storage.

Do not pull/rebase, alter Cloudflare configuration, make live trading requests, or broaden the ticket into a redesign. Push only the reviewed branch to the configured `fork` remote, and deploy only the existing Worker through the repository-owned release command. Do not change a test merely to hide a regression. The sole pre-approved expectation update is the optimizer menu cardinality and labels required by the shipped Equal Risk objective. Any other suspected stale expectation requires reproduced evidence and a new Decision Log entry that states the old and new user-visible behavior before its test is changed.

This is correctness and interaction repair, not performance work. The “reuse inflight list bootstrap” and “stop retry after navigation” cases prevent duplicate or stale work as an observable lifecycle contract; the plan proposes no algorithmic optimization. If an implementation proposes a cache, throttle, or broader performance optimization, first record the intercepted request-count baseline under the test fixture, the workload (one route transition and one click), and why a narrower lifecycle fix cannot satisfy the existing assertion.

## Progress

- [x] (2026-08-07 00:00+08:00) Captured durable user intent, baseline artifacts, scope, non-goals, and the permitted Equal Risk test-contract correction in this active ExecPlan.
- [x] (2026-08-07 00:00+08:00) Extracted the interactive report contract: 223 passed, 21 unexpected failures, one flaky test, and four skipped white-label tests. The separately run smoke suite was 43 passed / 5 failed; release SEO was 7 / 7.
- [x] (2026-08-07 00:00+08:00) Frozen the 21 failing Playwright cases below, grouped by behavior and source test file, so implementation cannot silently narrow the reported scope.
- [x] (2026-08-07 00:00+08:00) Diagnosed the bounded outcome-market, Builder Fee, and WebAuthn fixture failures; retained their user-facing assertions and corrected only deterministic fixture ownership and test-harness environment setup.
- [x] (2026-08-07 00:00+08:00) Focused Playwright fallback against the checked-in static debug bundle passed: market-strip dropdown 1/1, Builder Fee approval 2/2 (375px and 1280px), and market-strip dropdown repeat 3/3 with no retry-only success.
- [x] (2026-08-07 20:00+08:00) Re-ran the corrected ClojureScript and websocket suites with Homebrew OpenJDK 21: 5,937 tests / 33,098 assertions and 562 tests / 3,198 assertions, both with zero failures.
- [x] (2026-08-07 20:00+08:00) Reproduced and repaired the frozen cases, including vault lifecycle, WebAuthn RP validation, optimizer exposure, mobile balances, popover geometry, selected-account trading, Builder Fee, funding comparison, referrals, persisted tracking, history fixtures, and staking synchronization.
- [x] (2026-08-07 20:00+08:00) Passed focused repeats, smoke coverage, release SEO (7/7), and the final full interactive suite (245 passed, 4 configured skips, 0 failed).
- [x] (2026-08-07 20:00+08:00) Ran the 35-gate matrix. The only sandbox failure was three loopback-bind release-asset fixtures; the identical elevated release-assets command passed 52/52, so the corrected aggregate is 35/35.
- [x] (2026-08-07 22:30+08:00) Passed Cloudflare artifact checks and white-label browser coverage, cleaned browser sessions, deployed the existing `hyperopen` Worker as version `ab717702-379e-4a09-abc6-9b065ef34e44`, and passed the public verification matrix. GitHub push follows the completed-record commit.
- [x] (2026-08-08 12:00+08:00) Stabilized the three remaining full-suite fixture races, reran the interactive suite (245 passed, 4 configured skips), release SEO (7/7), Builder Fee repeats, 6,693 ClojureScript/websocket tests with 36,508 assertions, Worker tests (50/50), release-asset tests (52/52), and DEXHelm white-label coverage (4/4).
- [x] (2026-08-08 12:00+08:00) Committed the fixture repairs as `83d0ff6b`, pushed `codex/upstream-sync-20260806` to the user fork, and deployed the existing `hyperopen` Worker as version `0ee0e137-8495-488c-acb3-19aa119b1148` from rollback baseline `ab717702-379e-4a09-abc6-9b065ef34e44`.
- [x] (2026-08-08 12:00+08:00) Reverified public headers, Testnet-only proxy policy, all four custom domains, the DEXHelm logo, and the health endpoint after the follow-up deployment. Mainnet remains closed.

## Surprises & Discoveries

- Observation: the interactive baseline has 21 unexpected outcomes concentrated in shared interaction seams, rather than one isolated route failure.
  Evidence: `tmp/playwright/report/interactive/index.html` records failures in `trade-regressions`, `portfolio-regressions`, optimizer view-model and Black-Litterman suites, Builder Fee, product-context, mobile, and referrals tests.

- Observation: one additional browser contract is flaky rather than consistently failing.
  Evidence: `tools/playwright/test/trade-regressions.spec.mjs:1712`, “asset selector outcome rows use full-width question copy without duplicate chip,” is reported as flaky. It must become deterministic before the full suite is accepted.

- Observation: exactly two Builder Fee failures are the same behavioral test at 375px and 1280px.
  Evidence: the report lists `builder-fee.spec.mjs:273` twice, once under `builder fee review 375px` and once under `builder fee review 1280px`.

- Observation: the optimizer objective menu changed after the test was written.
  Evidence: the direct maintainer request states that Equal Risk is now the fifth menu option. This is a product evolution, not a regression to remove; an assertion that recognizes only four valid objectives is stale.

- Observation: the market-strip test installed grouped outcome state in one page evaluation, then read `question:32` in a second one while the initial asset-selector bootstrap could still replace the fixture. Repeats observed both a BTC reversion and the World Cup key disappearing between helpers.
  Evidence: `tools/playwright/test/trade-regressions.spec.mjs` previously called `seedGroupedOutcomeAssetSelectorState` and then separately read `asset-selector.market-by-key.question:32`; the focused test did not stub or await the initial market-info bootstrap before fixture seeding.

- Observation: Builder Fee tenant injection preceded wallet-session seeding. Startup approval refresh could therefore consume the sole intercepted `maxBuilderFee` response before the reviewed approval flow asserted it.
  Evidence: `tools/playwright/test/builder-fee.spec.mjs` configured the tenant and opened settings before `seedReadyTradingSession`, while the contract expects exactly one read during the explicit confirmation.

- Observation: `passkey-lock-supported?` now rejects literal RP hosts, but the Node test harness supplied no hostname, causing otherwise capable test environments to look unsupported.
  Evidence: `src/hyperopen/platform/webauthn.cljs` checks `globalThis.location.hostname`, whereas `test/hyperopen/platform/webauthn_test.cljs` previously replaced only crypto, WebAuthn, secure-context, and navigator globals.

- Observation: Java is installed but not discoverable by the default macOS launcher, and Node 25 needs an explicit local-storage mode for gates.
  Evidence: the corrected environment uses `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`, prepends the matching `bin`, uses `NODE_OPTIONS=--no-experimental-webstorage` for Playwright, and uses `NODE_OPTIONS=--localstorage-file=/tmp/hyperopen-node-gates-localstorage` for gates.

- Observation: `qa:pr-ui` starts a managed local app for its scenario bundle and then attempts a second managed app for design review before the first process group has released ports 8080/9630.
  Evidence: two wrapper runs failed with `shadow-cljs already running`; the separated critical scenario run passed loopback preflight but ended `automation-gap` after its managed app was not reachable. Final deterministic Playwright coverage still passed at 375, 768, 1280, and 1440 where those surfaces are asserted.

- Observation: the documented DEXHelm white-label command inherits the sample tenant's default dark-theme and disabled-route expectations unless both overrides are supplied.
  Evidence: the first follow-up invocation simultaneously treated `/trade` as enabled and disabled; the corrected invocation with `HYPEROPEN_EXPECT_THEME=dark` and an empty `HYPEROPEN_EXPECT_DISABLED_ROUTE` passed 4/4 at 375, 768, 1280, and 1440 px without code changes.

## Decision Log

- Decision: treat all 21 unexpected cases as the initial acceptance contract, not as suggestions for selective remediation.
  Rationale: the user asked to fix all browser regressions after the merge, and the retained report gives an exact reproducible starting point.
  Date/Author: 2026-08-07 / Codex.

- Decision: allow only the Equal Risk fifth-option expectation to change before diagnosis.
  Rationale: the user supplied the current intended behavior. Other test changes could mask a real upstream regression and must be justified with evidence in this plan.
  Date/Author: 2026-08-07 / Codex.

- Decision: retain the asset-selector outcome-row flake as a required stability criterion even though it is not one of the 21 unexpected outcomes.
  Rationale: “all browser regressions” includes an observed nondeterministic failure in a related outcome-market interaction. Passing only on retry would leave a broken CI contract.
  Date/Author: 2026-08-07 / Codex.

- Decision: sequence repair by shared state boundaries first, then optimizer/portfolio interaction, then responsive and vault lifecycle surfaces.
  Rationale: account address, wallet session, and route-lifecycle correctness feed multiple user-facing failures. This ordering minimizes duplicated diagnosis while the frozen test list keeps every surface accountable.
  Date/Author: 2026-08-07 / Codex.

- Decision: keep the outcome-dropdown and Builder Fee product assertions unchanged. Stabilize only their test lifecycle: settle stubbed bootstrap before atomically installing the selected grouped market, and settle the seeded wallet before the Builder Fee tenant becomes configured.
  Rationale: the observed failures were fixture ordering races; adding retries, extending timeouts, or weakening the single-read/single-approval checks would hide the regression contract.
  Date/Author: 2026-08-07 / Codex.

- Decision: model a DNS hostname by default in the WebAuthn test harness and assert IPv4, IPv6, and DNS-host outcomes directly.
  Rationale: the production guard is based on the browser's implicit relying-party hostname, so the harness must supply that input rather than leaving capability tests dependent on the Node global environment.
  Date/Author: 2026-08-07 / Codex.

- Decision: use the checked-in static debug bundle only as a focused fallback for the two Playwright fixture changes while Shadow compilation is unavailable.
  Rationale: the changes under test are JavaScript fixture ordering and test state assembly, and the fallback preserves the normal browser interaction assertions. It does not replace the blocked ClojureScript suite or full browser validation.
  Date/Author: 2026-08-07 / Codex.

- Decision: use zero-based Hyperliquid perp asset identifiers in the close-all simulator fixture (`BTC=0`, `ETH=1`).
  Rationale: the current market catalog and signed action contract are zero-based; the old one-based fixture asserted a provider contract that no longer exists.
  Date/Author: 2026-08-07 / Codex.

- Decision: freeze startup account watchers only inside deterministic browser fixtures that directly inject wallet/account state.
  Rationale: startup refreshes legitimately own production state, but allowing them to overwrite a fixture after seeding made Builder Fee, referrals, staking, and close-all assertions depend on response timing.
  Date/Author: 2026-08-07 / Codex.

- Decision: return deterministic funding rows from the predicted-fundings stub and observe optimizer history only after the explicit asset-add window.
  Rationale: empty or startup requests could land after fixture seeding and erase or contaminate the state being asserted.
  Date/Author: 2026-08-07 / Codex.

- Decision: share the optimizer risk estimate across UI helpers and solver context with a bounded eight-entry memo.
  Rationale: constraints and wall-clock freshness do not change covariance inputs; repeated Ledoit-Wolf estimation caused deterministic jank and the bucket-roll regression.
  Date/Author: 2026-08-07 / Codex.

- Decision: close the release with the governed `qa:pr-ui` wrapper recorded as an automation gap, not an application failure.
  Rationale: the wrapper attempts to start a second managed app before the first releases ports 8080/9630. The final deterministic Playwright suite passed 245 cases with no failures or retry-only outcomes, white-label coverage passed all four governed widths, and `npm run browser:cleanup` confirmed no residual sessions.
  Date/Author: 2026-08-07 / Codex.

- Decision: update the existing `hyperopen` Worker and preserve all four configured custom-domain routes and the Testnet-only policy.
  Rationale: the user authorized deployment of the existing Worker, not a new Worker, DNS changes, or Mainnet opening. Wrangler confirmed the same Worker name and replaced prior version `1e10deb3-eae7-4943-aeeb-951e87b760a9` with `ab717702-379e-4a09-abc6-9b065ef34e44`.
  Date/Author: 2026-08-07 / Codex.

- Decision: publish the 2026-08-08 fixture-stability follow-up to the same fork branch and existing Worker without changing routes, DNS, secrets, or Mainnet policy.
  Rationale: the user explicitly authorized the GitHub push and Worker update after the full regression rerun. Wrangler confirmed `ab717702-379e-4a09-abc6-9b065ef34e44` as the immediate rollback baseline and created `0ee0e137-8495-488c-acb3-19aa119b1148` on the same `hyperopen` Worker.
  Date/Author: 2026-08-08 / Codex.

## Outcomes & Retrospective

The regression repair is implemented and the final self-managed Playwright run is green: 245 passed and four configured white-label skips. The release SEO suite is 7/7 and the DEXHelm white-label suite is 4/4 at 375, 768, 1280, and 1440 px. ClojureScript and websocket suites pass with zero failures, and the corrected 35-gate matrix is green after rerunning loopback fixtures outside the sandbox. Production changes remain bounded to vault loading, WebAuthn RP safety, optimizer exposure/risk calculation, and the affected responsive/popover styles; test changes isolate startup ownership races without weakening user-facing behavior. The findings-first static review found no remaining correctness, security, race, or public-contract issue.

Cloudflare preflight passed 25 checks with two documented environment warnings; artifact preflight passed 33 checks with the same warnings. `cloudflare:check` read 57 assets, prepared a 31.16 KiB upload (8.31 KiB gzip), and exited successfully; Wrangler's inability to write its sandboxed local debug log was non-blocking. The deployment updated the existing `hyperopen` Worker from version `1e10deb3-eae7-4943-aeeb-951e87b760a9` to `ab717702-379e-4a09-abc6-9b065ef34e44`. Workers Static Assets accepted the existing `_headers` policy; no fallback Worker logic was required.

Public verification passed: `https://dexhelm.com` returned 200, `https://testnet.dexhelm.com` and `/trade` returned 200, `https://app.dexhelm.com` returned the intentional 503, `https://status.dexhelm.com` returned 200, and the public logo returned 200 `image/svg+xml`. The deployment-header verifier passed for the document, fingerprinted CSS/JavaScript, route metadata, site metadata, and service worker. The Worker verifier returned 200 JSON for the non-mutating Testnet fee probe and 404 for both Mainnet and generic proxy paths. Mainnet remains closed. Rollback remains available to prior version `1e10deb3-eae7-4943-aeeb-951e87b760a9` but was not exercised because all public checks passed.

Governed `qa:pr-ui` remains a tooling automation gap because its wrapper double-starts the managed app; it produced no application assertion failure. Deterministic browser coverage and the public release checks account for the changed surfaces, and browser cleanup reported no active sessions. Upstream service availability and the uncorrected wrapper lifecycle are the remaining operational risks.

The 2026-08-08 follow-up release committed three deterministic Playwright fixture repairs as `83d0ff6b` and pushed them to `haocn-ops/hyperopen:codex/upstream-sync-20260806`. The final interactive suite passed 245 tests with four configured skips, SEO passed 7/7, DEXHelm white-label coverage passed 4/4, Worker tests passed 50/50, release-asset tests passed 52/52, and the governed ClojureScript/websocket suites passed 6,693 tests with 36,508 assertions. Artifact preflight passed 33 checks; Wrangler dry-run read 57 assets and exited successfully.

Wrangler updated the same `hyperopen` Worker from `ab717702-379e-4a09-abc6-9b065ef34e44` to `0ee0e137-8495-488c-acb3-19aa119b1148`. Post-deploy verification returned 200 for `dexhelm.com`, `testnet.dexhelm.com`, `/trade`, `status.dexhelm.com`, the SVG logo, the health endpoint, and the non-mutating Testnet fee probe. `app.dexhelm.com` remained intentionally closed with 503, while Mainnet and generic HyperUnit proxy probes returned 404. Static headers and fingerprinted asset caching verified successfully.

## Context and Orientation

Hyperopen’s committed interactive browser tests run against the local debug build defined by `playwright.config.mjs`. They use `tools/playwright/support/hyperopen.mjs` to navigate, seed deterministic state, call the debug bridge, and inspect simulators. The release SEO tests are separate because they inspect generated static release output rather than the debug bridge. A `data-role` attribute is a stable test anchor rendered by the application; retain existing anchors where possible instead of adding browser-only behavior.

The relevant source areas are organized by responsibility. `src/hyperopen/header/actions.cljs`, `src/hyperopen/api/trading/agent_actions.cljs`, and order/effect adapters carry selected-account data into signed trade requests. `src/hyperopen/wallet/agent_runtime/{enable,storage_mode,state_projection}.cljs`, `src/hyperopen/wallet/agent_session.cljs`, and `src/hyperopen/runtime/effect_adapters/wallet.cljs` own simulated wallet enablement and remembered-session lifecycle. Builder Fee state is in `src/hyperopen/builder_fee/settings_state.cljs` and its visible settings controls are in `src/hyperopen/views/header/settings.cljs`.

Portfolio address selection and analytics presentation flow through `src/hyperopen/account/context.cljs`, `src/hyperopen/portfolio/actions.cljs`, `src/hyperopen/views/portfolio/vm.cljs`, and `src/hyperopen/views/portfolio_view.cljs`. Its volume-history overlay is `src/hyperopen/views/portfolio/volume_history_popover.cljs`. Optimizer setup and result menus use `src/hyperopen/portfolio/optimizer/actions/{draft,draft_options,return_views_io,universe}.cljs`, the optimizer view-model namespaces under `src/hyperopen/portfolio/optimizer/application/view_model/`, and `src/hyperopen/views/portfolio/optimize/{scenario_objective_menu,return_views_panel,setup_universe,scenario_detail_view}.cljs`. The Equal Risk objective remains a valid objective defined by the current optimizer contract; do not remove it to satisfy an old assertion.

Responsive trade/account surfaces are rendered by `src/hyperopen/views/account_info/tabs/balances.cljs`, `src/hyperopen/views/footer/mobile_nav.cljs`, `src/hyperopen/views/active_asset_view.cljs`, and `src/hyperopen/views/active_asset/row.cljs`; shared layout tokens live in `src/styles/main.css`. Product context is rendered by `src/hyperopen/views/trade_view.cljs` and `src/hyperopen/views/portfolio_view.cljs`; referrals by `src/hyperopen/views/referrals_view.cljs`. Vault route state, retry policy, list bootstrap, and market jumps are split between `src/hyperopen/vaults/{actions,effects}.cljs`, `src/hyperopen/vaults/application/{detail_commands,list_commands,route_loading}.cljs`, `src/hyperopen/vaults/infrastructure/{routes,list_cache,preview_cache}.cljs`, and the vault view-model/view namespaces under `src/hyperopen/views/vaults/**`.

“Spectate mode” means a read-only view of another wallet selected through a route query parameter. “Vault address” (`vaultAddress`) is the account identifier required by the exchange when a selected subaccount, rather than the connected master account, submits an order. “Remembered session” means the explicit user-selected mode for retaining the local API-wallet session; it is not permission to store new secret material outside the existing lockbox policy.

## Frozen Browser Contract

The following 21 unexpected cases are the baseline contract. Keep their test identities and behavioral coverage. A worker may split or improve tests only if the equivalent behavior remains asserted deterministically.

1. `tools/playwright/test/trade-regressions.spec.mjs:1788` must let a multi-option outcome market open its searchable market-strip dropdown and select the intended option.
2. `tools/playwright/test/trade-regressions.spec.mjs:2664` must stop a vault-detail 429 retry after the user returns to Trade; no stale retry may mutate the abandoned view.
3. `tools/playwright/test/trade-regressions.spec.mjs:2865` must reuse the in-flight vault-list bootstrap when the user clicks a startup preview row, avoiding a second competing bootstrap.
4. `tools/playwright/test/trade-regressions.spec.mjs:3031` must route a vault position’s coin to the matching Trade market.
5. `tools/playwright/test/trade-regressions.spec.mjs:3579` must take the simulator from wallet connect through Enable Trading to a deterministic ready state.
6. `tools/playwright/test/trade-regressions.spec.mjs:3766` must include the selected subaccount’s `vaultAddress` in the submitted order payload, not substitute the master address.
7. `tools/playwright/test/trade-regressions.spec.mjs:4097` must make remembered-session toggles visibly gate passkey-lock controls and retain the expected enabled/disabled states.
8. `tools/playwright/test/portfolio-regressions.spec.mjs:1487` must request and display analytics using the connected address on `/portfolio` and the observed address on `/portfolio/trader/<address>` without cross-address fixture leakage.
9. `tools/playwright/test/portfolio-regressions.spec.mjs:1698` must make From holdings seed the current exposure constraints from the visible holdings data.
10. `tools/playwright/test/portfolio-regressions.spec.mjs:2179` must add and remove vault rows through the manual optimizer-universe builder.
11. `tools/playwright/test/portfolio-regressions.spec.mjs:3006` must open volume history next to its metric-card trigger and keep it visible/interactive.
12. `tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs:1167` must change Maximum Sharpe return views immediately through the objective menu. Its option assertion must recognize Minimum Variance, Maximum Sharpe, Target Return, Target Risk, and Equal Risk when those are the rendered product choices.
13. `tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs:1530` must keep the optimizer objective menu inside the viewport at its tested review widths, with an operable selected option.
14. `tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs:1581` must keep the add-asset selector inside the viewport, focus its search field, and update the draft/recompute state after a valid selection.
15. `tools/playwright/test/builder-fee.spec.mjs:273` at 375px must expose Review and enable, then Confirm and enable, issue exactly one `maxBuilderFee` read and approval, and attach Builder Fee only to eligible orders.
16. `tools/playwright/test/builder-fee.spec.mjs:273` at 1280px must provide the identical Builder Fee behavior without a desktop-only regression.
17. `tools/playwright/test/product-context-ui.spec.mjs:142` at 768px on `/trade` must render the shared product-context banner and have `scrollWidth <= clientWidth + 1` for both document and body.
18. `tools/playwright/test/product-context-ui.spec.mjs:142` at 768px on `/portfolio` must render the corresponding shared product-context banner with the same no-horizontal-overflow condition.
19. `tools/playwright/test/mobile-regressions.spec.mjs:190` must permit the last mobile balance card to scroll to or above the fixed bottom-navigation top edge, leaving the bottom nav visible rather than covering data.
20. `tools/playwright/test/optimizer-black-litterman-views.spec.mjs:381` must retain compact, reachable Black-Litterman return-view row controls in the optimizer setup editor.
21. `tools/playwright/test/referrals-regressions.spec.mjs:213` must render the ready referral statistics and rows, then open usable Share Referral Code and Claim Rewards modal flows.

The non-frozen stability case is `tools/playwright/test/trade-regressions.spec.mjs:1712`: outcome selector rows must consistently show the full question copy exactly once and no duplicate chip. Run it repeatedly or with CI retries until it has no retry-only success.

## Plan of Work

First, reproduce the frozen contract in focused groups before changing implementation. Use Playwright’s retained trace and screenshot only to identify the first broken observable transition. For each failure, record in `Surprises & Discoveries` the source function or rendering boundary that owns the broken transition, the fixture state, and whether the test assertion remains current. Do not run live APIs or use a real wallet. The `tdd_test_writer` may amend only approved test surfaces; the `worker` is the only role that may alter `src/**`.

Repair selected-account propagation as one coherent boundary. In the header selection actions and signed-order assembly, retain the selected account’s `vaultAddress` all the way to the exchange simulator action. In portfolio account context and analytics request construction, derive request identity from the active connected or spectated route address, never from stale master-account state. Keep this state derivation deterministic and side-effect-free; effects only perform the already-decided request. The focused tests in contract entries 6 and 8 demonstrate the boundary end to end.

Repair wallet and Builder Fee lifecycle separately from account identity. Follow the existing wallet runtime state machine through provider connection, trading-enable approval, storage-mode selection, and visible settings projection. Do not add direct `localStorage` calls to reducers or views. If remembered-session persistence is adjusted, use the existing lockbox/storage helpers and preserve their explicit session-only vs remembered semantics. Make the Builder Fee review control reachable at both tested widths, then retain its one-read/one-approval and eligible-order payload rules rather than weakening the simulator assertions. Entries 5, 7, 15, and 16 prove the outcome.

Repair optimizer and portfolio interactions at their existing view-model/action seam. Make the view-model expose all current objective choices, including Equal Risk, and change the stale option-count/label expectation only to assert the actual five-option product contract. Preserve immediate return-view dispatch for Maximum Sharpe, viewport-bounded popovers/menus, focused add-asset search, compact Black-Litterman rows, current-holdings constraint seeding, vault-row add/remove, and anchored volume history. Do not invent a second state model in views; actions update the draft and view-models render it. Entries 9 through 14 and 20 provide targeted proof.

Repair responsive composition without replacing shared layout. Give mobile balances enough scroll clearance for the fixed navigation at 375px, and correct the 768px product-context overflow at the owning wrapper, child min-width, or style token instead of globally hiding overflow. Keep the market strip’s multi-option selection searchable, its full outcome label unique, and product/referral controls keyboard-accessible. Verify the share and claim dialogs have operable visible controls and do not overflow their review viewport. Entries 1, 17 through 19, and 21 provide deterministic proof; the asset-selector stability case prevents retry-dependent success.

Repair vault lifecycle behavior at the command/effect/route boundary. Cancellation or route-exit must invalidate a scheduled detail retry before it can issue another request or update state. A startup preview-row click must subscribe to/reuse the already-running list load rather than starting another load. The selected vault position must turn its normalized coin and market identity into the correct Trade route. Keep request ownership and cancellation decisions deterministic, with side effects confined to vault effect adapters. Entries 2 through 4 prove this sequence.

After focused tests pass, run governed browser QA for changed interaction surfaces at 375, 768, 1280, and 1440 widths. Record each required pass (visual, native-control, styling-consistency, interaction, layout-regression, and jank/performance) as PASS, FAIL, or BLOCKED in a QA artifact or this plan. Browser MCP is for reproducing uncertain behavior and inspecting layout; convert stable paths back into Playwright assertions. Explicitly run `npm run browser:cleanup` after any Browser MCP/browser-inspection session. Then run smoke, full interactive, release SEO, and repository gates in that order.

## Concrete Steps

Run all commands from `/Users/zh/Documents/Hyperopen`. Start by making the dependency guard explicit:

    npm run setup:worktree

Run focused groups with a single worker and CI retry behavior so a retry-only pass is visible. Begin with the exact case under investigation, then widen to its source file:

    CI=1 npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "multi-option outcome markets|429 retries stop|inflight list bootstrap|position coin jumps|wallet connect and enable|vaultAddress|session toggles|full-width question" --workers=1
    CI=1 npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "address-scoped|From holdings|adds and removes vaults|volume history" --workers=1
    CI=1 npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep "return views immediately|objective menu stays contained|add asset selector stays contained" --workers=1
    CI=1 npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs --grep "compact row controls" --workers=1
    CI=1 npx playwright test tools/playwright/test/builder-fee.spec.mjs --grep "confirmed approval refreshes" --workers=1
    CI=1 npx playwright test tools/playwright/test/mobile-regressions.spec.mjs --grep "balances list clears" --workers=1
    CI=1 npx playwright test tools/playwright/test/product-context-ui.spec.mjs --grep "shared product context without horizontal overflow" --workers=1
    CI=1 npx playwright test tools/playwright/test/referrals-regressions.spec.mjs --grep "share and claim modal flows" --workers=1

When the focused groups are green, use the broad contract command before full-suite work:

    CI=1 npx playwright test \
      tools/playwright/test/trade-regressions.spec.mjs \
      tools/playwright/test/portfolio-regressions.spec.mjs \
      tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs \
      tools/playwright/test/optimizer-black-litterman-views.spec.mjs \
      tools/playwright/test/builder-fee.spec.mjs \
      tools/playwright/test/mobile-regressions.spec.mjs \
      tools/playwright/test/product-context-ui.spec.mjs \
      tools/playwright/test/referrals-regressions.spec.mjs \
      --workers=1

Expected focused and broad-contract result: every named test passes with no retry-only success. If a result is flaky, preserve its trace, reproduce it through Browser MCP if needed, and repair the nondeterministic lifecycle rather than adding arbitrary waits.

Run browser QA only after a stable focused path exists. Use the repo’s required review widths and then clean up tool-created sessions:

    npm run qa:pr-ui
    npm run browser:cleanup

Finally run the escalation ladder and record the actual pass/fail counts in this plan:

    npm run test:playwright:smoke
    CI=1 npx playwright test --workers=1
    npm run test:playwright:seo
    npm run gates

`npm run gates` is required because code changes require `npm run check`, `npm test`, and `npm run test:websocket`; it reports their combined PASS/FAIL matrix without short-circuiting. Do not deploy or push after the gates. If a command fails because `node_modules` is absent, rerun `npm run setup:worktree`; if a browser process remains after an interrupted inspection, run `npm run browser:cleanup` before retrying.

## Validation and Acceptance

Acceptance is behavioral and all conditions below are required.

1. The 21 frozen tests in `Frozen Browser Contract` pass under `CI=1` and one worker, including both 375px and 1280px Builder Fee cases. The report contains zero unexpected outcomes for those test IDs.
2. The asset selector’s full-width outcome question test passes without retry. Its row exposes one full question label and no duplicate chip, so its result is never classified as flaky.
3. In the simulated selected-subaccount order flow, the captured action’s `vaultAddress` equals the selected subaccount’s address; portfolio analytics requests/visible fixtures use the connected or spectated address appropriate to their route.
4. The simulator wallet connect/Enable Trading flow reaches ready deterministically. Remembered-session controls visibly gate passkey lock as specified, and Builder Fee Review then Confirm sends exactly one approval and one `maxBuilderFee` lookup while only eligible order actions carry Builder Fee data.
5. At 375px, the final mobile balance card can be scrolled clear of the fixed bottom navigation. At 768px, both Trade and Portfolio product-context documents and bodies satisfy `scrollWidth <= clientWidth + 1`. At each required review width, changed menus, cards, popovers, dialogs, and mobile navigation remain within the viewport without clipping, cover-up, or unintended horizontal scrolling.
6. The optimizer exposes all five current objectives, including Equal Risk, while Maximum Sharpe return views still update immediately. Objective and asset selectors remain focused and selectable; Black-Litterman rows, From holdings constraints, vault rows, and volume history complete the asserted interactions.
7. Multi-option market selection works through searchable dropdown results; vault retry cancels on navigation away; an in-flight list bootstrap is reused; and a vault position jump reaches the expected Trade market. Referrals ready state opens working share and claim dialogs.
8. Browser QA explicitly reports all six required passes for every changed user-facing surface at 375, 768, 1280, and 1440. Any Browser MCP/browser-inspection sessions created during QA are stopped with `npm run browser:cleanup`.
9. `npm run test:playwright:smoke` has zero failures, the full interactive command has zero unexpected and zero flaky results (the four white-label skips remain only if their existing configuration still legitimately skips them), `npm run test:playwright:seo` reports 7 passing tests, and `npm run gates` reports PASS for check, ClojureScript tests, and websocket tests.

## Idempotence and Recovery

The test, setup, QA, and cleanup commands are repeatable. Preserve generated failure artifacts until the corresponding case has a green rerun; new Playwright artifacts stay under `tmp/playwright/**`, and Browser MCP artifacts stay under `tmp/browser-inspection/**`. A focused failure should be retried only after correcting the responsible code or test contract, not with a larger timeout or suppression. If an intended product contract changes after this plan is created, update `Decision Log`, the frozen contract, and acceptance criteria together before changing a test.

External state changed only through the explicitly authorized Git commits and updates of the existing Cloudflare Worker. For the 2026-08-08 follow-up, request explicit rollback authorization and deploy immediate prior version `ab717702-379e-4a09-abc6-9b065ef34e44`; do not delete the Worker. Historical version `1e10deb3-eae7-4943-aeeb-951e87b760a9` remains an earlier release baseline. To abandon local implementation, use normal version-control review/revert procedures for the specific changed files; do not run destructive repository commands. Do not move or delete baseline reports merely to obtain a clean browser-report directory.

## Artifacts and Notes

Baseline interactive result: 223 passed, 21 failed, 1 flaky, 4 skipped. Baseline smoke result: 43 passed, 5 failed. Baseline release SEO result: 7 passed. The retained interactive HTML report is self-contained and embeds the detailed Playwright report data; failure evidence remains in `tmp/playwright/test-results/interactive/**`.

The original repair release was committed locally as `8a19cb87` before its final record update. The 2026-08-08 fixture-stability follow-up was committed as `83d0ff6b`; this updated completed record is the final evidence intended for the authorized `fork` push. The untracked `test-results/` directory remains outside version control. The local review report is `tmp/multi-agent/upstream-browser-regressions/review-report.json`; it records a pass with no findings and the same QA-wrapper residual risk.

## Interfaces and Dependencies

Use existing dependencies only: Playwright and the repository’s `HYPEROPEN_DEBUG` bridge/simulators for deterministic browser tests; Browser MCP/browser-inspection only for exploratory or governed QA; the existing wallet lockbox/storage helpers for session state; and existing vault command/effect adapters for retry/list lifecycles. Do not introduce a browser-only API, a new persistence backend, a real wallet dependency, or a new library to repair these regressions.

The stable interfaces to preserve are the current route/query shapes, the signed exchange action payload shape (including `vaultAddress` for selected subaccounts), established `data-role`/`data-parity-id` test anchors, optimizer objective values including `:equal-risk`, and the existing tenant/product-context configuration. All side effects remain in the existing effect/interpreter boundaries; action and view-model decisions remain pure and deterministic.

Revision note: created on 2026-08-07 from the direct upstream-merge regression request and the recorded 21-failure interactive Playwright baseline. It freezes the exact failing test contract, records the one asset-selector flake as a stability requirement, and permits only the current five-option Equal Risk objective expectation to replace a stale assertion without further product clarification. Updated on 2026-08-08 with the fixture-stability follow-up commit, Worker deployment, rollback baseline, and public verification evidence.
