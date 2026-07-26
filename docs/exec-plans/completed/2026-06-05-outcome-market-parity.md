# Reach Hyperliquid Outcome Market Parity

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Hyperopen's Outcome selector currently reflects the first May 2026 implementation: it can show simple recurring crypto outcome rows, especially BTC binary markets. Hyperliquid's live app has moved on. On June 5, 2026, Hyperliquid's Outcome tab showed grouped economics markets, sports markets, and a recurring BTC price range question, while Hyperopen's selector model still builds one market row per `outcomeMeta.outcomes` entry and does not model `outcomeMeta.questions` as first-class rows.

After this work, a user opening Hyperopen's asset selector should see the same live active Outcome market universe as `https://app.hyperliquid.xyz`: Mainnet subtabs `All`, `Crypto (1d)`, `Economics`, and `Sports`; one row for grouped questions such as `May CPI year-over-year` and `BTC price range on Jun 6 at 2:00 AM?`; sports rows such as `NBA Finals Game 2`; and no stale or fallback BTC component rows that Hyperliquid does not list. Selecting any displayed row should route to the best tradable side, show grouped side chances, subscribe the relevant side coins, and preserve existing Yes/No order behavior for binary outcome rows.

## Context References

Public refs:

- Direct user request on 2026-06-05: compare Hyperliquid's current Outcome market implementation with Hyperopen's implementation, explain the endpoint differences, and create an execution plan to reach parity.
- Live Hyperliquid app inspection on 2026-06-05: `https://app.hyperliquid.xyz/` loaded frontend bundle `/static/js/main.712d932d.js`.
- Live Hyperliquid API calls on 2026-06-05:
  - `POST https://api.hyperliquid.xyz/info` with `{"type":"outcomeMeta"}` returned 12 `outcomes` and 2 `questions`.
  - `POST https://api-ui.hyperliquid.xyz/info` with `{"type":"outcomeMeta"}` returned the same shape.
  - `POST https://api.hyperliquid.xyz/info` with `{"type":"webData2","user":"0x0000000000000000000000000000000000000000"}` returned 375 `spotAssetCtxs`, including 24 outcome side contexts whose coins begin with `#`.
- Official Hyperliquid docs:
  - `https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/info-endpoint/spot` documents `POST /info` request type `outcomeMeta`.
  - `https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/asset-ids` documents outcome side encoding: `encoding = 10 * outcome + side`, side coins `#<encoding>`, token names `+<encoding>`, and action asset ids `100000000 + encoding`.

Repo artifacts:

- `/hyperopen/docs/exec-plans/completed/2026-05-02-outcome-markets.md` implemented the original recurring BTC outcome flow.
- `/hyperopen/docs/exec-plans/completed/2026-05-03-fix-account-outcome-enrichment.md` and `/hyperopen/docs/exec-plans/completed/2026-05-03-account-outcome-row-data-sources.md` fixed account-row enrichment for simple outcome side balances.
- `/hyperopen/docs/FRONTEND.md` and `/hyperopen/docs/BROWSER_TESTING.md` define UI and browser QA obligations for this parity work.

## Progress

- [x] (2026-06-05 19:08Z) Read the existing Outcome implementation plans and mapped the current source touchpoints.
- [x] (2026-06-05 19:08Z) Queried live Hyperliquid `outcomeMeta` and public `webData2` from both `api.hyperliquid.xyz` and `api-ui.hyperliquid.xyz`.
- [x] (2026-06-05 19:08Z) Inspected the current Hyperliquid frontend bundle enough to infer its Outcome tab list, category filters, recurring question normalization, and fallback-outcome handling.
- [x] (2026-06-05 19:08Z) Created this active ExecPlan with the endpoint differential and implementation milestones.
- [x] (2026-06-06 13:56Z) Implemented the outcome metadata normalization upgrade described in Milestone 1, including grouped question rows, fallback/component suppression, sports/economics classification, and BTC price-bucket option labels.
- [x] (2026-06-06 13:56Z) Implemented selector tabs, grouped rows, route resolution, live subscriptions, cache preservation, and live projection updates described in Milestones 2 and 3.
- [x] (2026-06-06 13:56Z) Implemented grouped outcome account/order-form hardening from Milestone 4, including question option selection and selected-option asset id routing.
- [x] (2026-06-06 13:56Z) Added fixture-backed unit coverage plus Playwright grouped-outcome smoke coverage from Milestone 5.
- [x] (2026-06-06 13:56Z) Ran required validation: `npm run check`, `npm test`, `npm run test:websocket`, focused grouped-outcome Playwright, governed trade-route design review, and browser cleanup.

## Surprises & Discoveries

- Observation: Live Mainnet `outcomeMeta` now includes grouped `questions`, not only standalone `outcomes`.
  Evidence: On 2026-06-05, the endpoint returned question `19` named `May CPI year-over-year` with `fallbackOutcome 100` and `namedOutcomes [101 102 103]`, plus question `30` named `Recurring` with `description class:priceBucket|underlying:BTC|expiry:20260606-0600|priceThresholds:61044,63535|period:1d`, `fallbackOutcome 160`, and `namedOutcomes [161 162 163]`.

- Observation: Hyperliquid's current Mainnet Outcome subtabs are not the same as Hyperopen's hard-coded subtabs.
  Evidence: The live bundle contains `Mainnet === chain ? ["all","crypto-1d","economics","sports"] : ["all","crypto-15m","crypto-1d","economics","sports"]`. Hyperopen currently renders only `All`, `Crypto (15m)`, and `Crypto (1d)` under Outcome.

- Observation: Hyperliquid classifies economics and sports from outcome metadata rather than from separate REST endpoints.
  Evidence: The bundle parses a `metadata=` suffix from descriptions, accepts `category:economics` and `category:sports`, maps sports subcategories such as `basketball`, and uses a fallback `/\b(?:cpi|fed)\b/i` name match to classify economics. The live sports outcomes `NBA Finals Game 2` and `2026 NBA Finals champion` include `metadata=category:sports|subCategory:basketball`; the CPI and Fed markets do not include metadata but match the name fallback.

- Observation: Hyperliquid no longer treats every `outcomes` entry as a selector row.
  Evidence: The bundle builds `outcomeToSpec`, `questionToSpec`, `outcomeToQuestion`, and `fallbackOutcomes`; skips fallback outcomes as standalone rows; replaces recurring `priceBucket` component outcomes with a single question row; and routes question rows to the named outcome with the highest current Yes mark when a concrete side coin is needed.

- Observation: Hyperopen's current `build-outcome-markets` cannot safely consume the live payload shape.
  Evidence: `/hyperopen/src/hyperopen/asset_selector/markets.cljs` uses `(or (:outcomes outcome-meta) (:questions outcome-meta) [])`, so `questions` are ignored whenever `outcomes` exists. It then calls `outcome-title` for every outcome entry, but `outcome-title` assumes parsed `priceBinary` fields. Live plain outcomes such as `June Fed rate change` and fallback outcomes such as `Fallback` do not have `underlying`, `targetPrice`, or `expiry-ms`.

- Observation: Hyperopen has some stale selected-outcome recovery, but not enough to match Hyperliquid's rotating active universe.
  Evidence: `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs` and `/hyperopen/src/hyperopen/startup/restore.cljs` call `expired-outcome-market?` for active restored outcome markets. That protects the selected market, but it does not prove cached selector rows are replaced, that `outcomeMetaUpdates` are consumed, or that removed/fallback outcomes disappear from the list.

- Observation: Cached and active-market projections needed explicit grouped-field preservation.
  Evidence: RED tests for `markets_cache`, `active_market_cache`, and `market_live_projection` showed grouped fields such as `:question-options`, selected option ids, and grouped side aliases being dropped or not patched by active side ctx updates until those paths were extended.

- Observation: The full `@smoke` Playwright suite currently has optimizer-route failures independent of the outcome selector path.
  Evidence: `npm run test:playwright:smoke` failed in three `portfolio optimizer` tests. Rerunning those three optimizer tests as a focused subset reproduced the same failures. The non-optimizer smoke set passed serially with `--grep-invert "portfolio optimizer" --workers=1`, including the grouped-outcome test and the subaccount order-routing test.

- Observation: Browser MCP tools were not exposed in this session, so browser QA used repo-governed browser-inspection and Playwright instead.
  Evidence: Tool discovery did not expose Browser MCP controls. The governed design review command `npm run qa:design-ui -- --targets trade-route --manage-local-app` completed with `reviewOutcome: PASS`.

## Decision Log

- Decision: Treat live `outcomeMeta` as the authoritative Outcome universe and rebuild Hyperopen's selector rows from the normalized `outcomes + questions` graph, not from cached BTC-only assumptions.
  Rationale: Hyperliquid's own app and both public info hosts return grouped question data today. A row-per-outcome model produces false rows, misses grouped markets, and cannot express the CPI or BTC range UI.
  Date/Author: 2026-06-05 / Codex

- Decision: Preserve the existing outcome side encoding and order asset-id math.
  Rationale: The official docs and the May implementation agree on `encoding = 10 * outcome + side`, `#<encoding>`, `+<encoding>`, and `100000000 + encoding`. The parity gap is discovery, grouping, classification, and display, not the exchange action asset id.
  Date/Author: 2026-06-05 / Codex

- Decision: Add a normalized Outcome domain layer inside `hyperopen.asset-selector.markets` before touching views.
  Rationale: View code should consume rows like `:outcome-kind :binary` and `:outcome-kind :question` rather than parsing descriptions or deciding which fallback outcomes to hide. This keeps endpoint drift contained at the data boundary.
  Date/Author: 2026-06-05 / Codex

- Decision: Match Hyperliquid's Mainnet tab behavior: hide `Crypto (15m)` on Mainnet unless live chain/testnet context or future payload evidence says it belongs.
  Rationale: The user specifically compared the Mainnet app screenshot, which shows `All`, `Crypto (1d)`, `Economics`, and `Sports`. Keeping `Crypto (15m)` on Mainnet contributes to the user's "markets listed for bitcoin that they don't list" complaint.
  Date/Author: 2026-06-05 / Codex

- Decision: Keep grouped-question rows as the primary selector identity while routing concrete trades through the selected named option.
  Rationale: Hyperliquid displays one row per question, but exchange actions still require side asset ids for an outcome option. Preserving both the question identity and selected option avoids lossy routing and keeps balances/orders explainable.
  Date/Author: 2026-06-06 / Codex

## Outcomes & Retrospective

Implemented. Hyperopen now derives Outcome rows from the live `outcomes + questions` graph instead of a row-per-outcome BTC-only assumption. Mainnet Outcome subtabs now match Hyperliquid's observed set: `All`, `Crypto (1d)`, `Economics`, and `Sports`. Grouped CPI, BTC price range, and sports markets render as first-class rows; fallback and grouped component outcomes are suppressed as standalone selector rows. Coin aliases, websocket subscriptions, cache/live projection paths, account outcome labels, and the order form now preserve grouped question identity while routing actions to the selected option side asset id.

The main complexity increase is bounded to the Outcome normalization and cache/live preservation paths. That is preferable to pushing description parsing and question/fallback rules into selector views, account projections, and order form components. Browser QA passed for the trade route. The only residual validation gap is the unrelated full-smoke optimizer group, which reproduces as a focused optimizer subset and is documented separately from this feature.

## Context and Orientation

Outcome markets are HIP-4 prediction markets. A binary outcome has an `outcome` id and two sides. Side `0` is usually Yes and side `1` is usually No, though live plain outcomes can use labels such as `Change` and `No Change` or team names. Hyperliquid derives the side coin as `#<10 * outcome + side>`, the user's balance token name as `+<10 * outcome + side>`, and the exchange action asset id as `100000000 + 10 * outcome + side`.

A grouped question is a collection of named outcome ids plus a fallback outcome id. The CPI question uses named outcomes `Below 4.3%`, `Exactly 4.3%`, and `Above 4.3%`, plus fallback outcome `100`. The recurring BTC range question uses named outcomes `161`, `162`, and `163`, whose descriptions are `index:0`, `index:1`, and `index:2`, plus fallback outcome `160`. Hyperliquid renders the grouped question as one selector row with side labels derived from the named outcomes, not as four separate fallback/component rows.

Hyperopen's current market hydration starts in `/hyperopen/src/hyperopen/api/market_loader.cljs`, calls `/hyperopen/src/hyperopen/api/endpoints/market.cljs`, and builds normalized markets in `/hyperopen/src/hyperopen/asset_selector/markets.cljs`. Selector filtering lives in `/hyperopen/src/hyperopen/asset_selector/query.cljs`; selector tab controls live in `/hyperopen/src/hyperopen/views/asset_selector/controls.cljs`; selector row rendering lives in `/hyperopen/src/hyperopen/views/asset_selector/rows.cljs`; selected-market subscriptions are coordinated by `/hyperopen/src/hyperopen/runtime/action_adapters/websocket.cljs` and `/hyperopen/src/hyperopen/websocket/subscriptions_runtime.cljs`; order-form outcome side selection lives in `/hyperopen/src/hyperopen/views/trade/order_form_vm.cljs` and order commands are built in `/hyperopen/src/hyperopen/api/gateway/orders/commands.cljs`; account outcome positions are projected in `/hyperopen/src/hyperopen/views/account_info/projections/outcomes.cljs`.

## Differential Findings

Hyperliquid's live data model:

Hyperliquid requests `outcomeMeta`, `webData2`, `spotMeta`, regular market metadata, and live websocket updates. For Outcome selector parity, the relevant stable sources are `outcomeMeta` for names/questions/grouping, `webData2.spotAssetCtxs` for current side marks/volume/open interest, and websocket subscriptions such as `activeSpotAssetCtx`, `spotAssetCtxs`, `outcomeMetaUpdates`, `l2Book`, and `trades` for live updates after selection. On 2026-06-05, public `webData2` exposed active side contexts for `#1000`, `#1001`, `#1010`, `#1011`, through `#1631`, including current marks, 24h notional volume, and circulating supply.

Hyperliquid's display model:

On Mainnet it exposes Outcome subtabs `All`, `Crypto (1d)`, `Economics`, and `Sports`. It keeps `Crypto (15m)` for non-Mainnet/testnet. It parses recurring `priceBinary` descriptions into names such as `BTC above 62290 on Jun 6 at 2:00 AM?`. It parses recurring `priceBucket` question descriptions into names such as `BTC price range on Jun 6 at 2:00 AM?` and side labels `Below 61044`, `61044 to 63535`, and `Above 63535`. It parses `metadata=category:sports|subCategory:basketball` from descriptions and falls back to CPI/Fed name matching for economics. It skips fallback outcomes as standalone selector rows and resolves grouped question rows to a tradable side coin when needed.

Hyperopen's current implementation:

Hyperopen already requests `outcomeMeta` and public `webData2`, already models side asset ids, and already subscribes both side coins for simple binary outcomes. The gap is in normalization and presentation. It builds one row per `outcomes` entry, ignores `questions` whenever `outcomes` exists, assumes the `priceBinary` description shape for all outcomes, hard-codes only `Outcome`, `Crypto (15m)`, and `Crypto (1d)` subtabs, and has tests only for the original BTC recurring fixture. This explains both missing Hyperliquid markets and extra BTC/fallback/component rows.

## Plan of Work

Milestone 1 upgrades pure Outcome metadata normalization. At the end of this milestone, tests can feed the June 5 live `outcomeMeta` fixture plus `webData2.spotAssetCtxs` and get the same logical market rows Hyperliquid lists.

In `/hyperopen/src/hyperopen/asset_selector/markets.cljs`, replace the row-per-outcome builder with a graph normalizer. Keep the existing `outcome-encoding`, `outcome-coin`, and `outcome-asset-id` functions unchanged. Add helpers that build:

- `:outcome-spec-by-id`, keyed by numeric outcome id.
- `:question-spec-by-id`, keyed by numeric question id.
- `:outcome-id->question-id`, for every named and fallback outcome in a question.
- `:fallback-outcome-ids`, containing all `fallbackOutcome` ids.

Extend description parsing so it handles three classes:

- `priceBinary`, with `underlying`, `expiry`, `targetPrice`, and `period`.
- `priceBucket`, with `underlying`, `expiry`, `priceThresholds`, and `period`.
- `plain`, for normal prose outcomes such as `June Fed rate change`, sports outcomes, and fallback outcomes.

Add `parse-outcome-metadata` for prose descriptions with a suffix like `metadata=category:sports|subCategory:basketball`. This parser must split only after the last `metadata=` marker, preserve the display description without the metadata suffix, and return `:outcome-category :sports`, `:outcome-subcategory :basketball`, and a bounded `:extra-metadata` map. If category metadata is absent, classify names containing CPI or Fed as `:economics`. Unknown categories should be preserved as `:uncategorized` and still appear under `All`.

Add question builders:

- A binary row for standalone non-fallback outcomes not assigned to a question. The row has `:outcome-kind :binary`, one `:outcome-id`, `:outcome-sides`, and a default selected side.
- A question row for `outcomeMeta.questions`. The row has `:outcome-kind :question`, `:question-id`, `:fallback-outcome-id`, `:named-outcome-ids`, and `:question-options`. Each option contains label, outcome id, Yes-side coin, Yes asset id, No-side coin, No asset id, mark, volume, and circulating supply.
- A recurring price bucket question row whose title and option labels are derived from `priceThresholds`, replacing the component outcome names `Recurring Fallback` and `Recurring Named Outcome`.

Filtering rules must skip fallback outcomes as standalone rows. Named outcome component rows should not appear separately when they belong to a question. Keep the raw specs under bounded debug keys so account/open-order projections can still map `+161` and `#1610` back to the question and option label.

Milestone 2 updates selector tabs, filtering, sorting, and cache signatures. At the end of this milestone, the asset selector can show Hyperliquid's Mainnet Outcome subtab set and filter grouped rows correctly.

In `/hyperopen/src/hyperopen/asset_selector/settings.cljs`, add valid tabs `:outcome-economics` and `:outcome-sports`, and add a pure helper that returns the active Outcome tabs for the current chain context. Mainnet should produce `[:outcome :outcome-1d :outcome-economics :outcome-sports]`; testnet should include `:outcome-15m`.

In `/hyperopen/src/hyperopen/asset_selector/query.cljs`, update `tab-match?` so:

- `:outcome` matches every normalized outcome row.
- `:outcome-1d` matches recurring binary rows and recurring question rows with `:period "1d"`.
- `:outcome-15m` matches only when that tab is enabled and period is `15m`.
- `:outcome-economics` matches row category `:economics`.
- `:outcome-sports` matches row category `:sports`.

Question rows should sort by total 24h volume across named options first, then by highest option mark when the sort key is price/chance, then by title. The mobile and desktop headers should label the chance column as `% Chance` for binary rows and keep grouped side summaries visible under the title.

In `/hyperopen/src/hyperopen/views/asset_selector/controls.cljs`, replace the hard-coded Outcome subtab row with data-driven tabs. On Mainnet render exactly `All`, `Crypto (1d)`, `Economics`, and `Sports`. Ensure mobile top-tab active state treats every outcome subtab as active, not only exactly `:outcome`.

In `/hyperopen/src/hyperopen/views/asset_selector/processing.cljs`, include new question/category fields in `processed-assets-market-signature`: `:outcome-kind`, `:question-id`, `:named-outcome-ids`, `:fallback-outcome-id`, `:outcome-category`, `:outcome-subcategory`, and a compact signature of option labels/coins/marks. This prevents the processed-assets cache from keeping old BTC/fallback rows after metadata rotates.

In `/hyperopen/src/hyperopen/asset_selector/markets_cache.cljs` and `/hyperopen/src/hyperopen/asset_selector/active_market_cache.cljs`, persist only the bounded normalized outcome fields needed for first paint. Do not persist raw prose descriptions larger than the existing cache budget unless the description is needed for the details popover. On restore, mark any cached outcome universe incomplete if a subsequent `outcomeMeta` response changes the set of active outcome ids, question ids, or fallback ids.

Milestone 3 updates selection, routing, subscriptions, and selected-market display for grouped questions. At the end of this milestone, clicking a grouped question opens a sensible tradable side while preserving a question-level display.

Extend `coin-aliases`, `market-matches-coin?`, `resolve-market-by-coin`, and related helpers in `/hyperopen/src/hyperopen/asset_selector/markets.cljs` so `#1610`, `#1611`, `#1620`, `#1621`, `#1630`, `#1631`, `outcome:161`, and `question:30` can all resolve to the same BTC range question row while preserving the selected option and side. Do not collapse all questions into the Yes side of the first named outcome without recording the selected option.

Add a pure resolver that chooses a default route coin for a question row. Match Hyperliquid's behavior: select the named outcome whose Yes side has the highest current mark, then side `0`, unless the user clicked a specific option. Use this only as a default route and subscription target; do not lose the question row identity.

Update `/hyperopen/src/hyperopen/runtime/action_adapters/websocket.cljs` and `/hyperopen/src/hyperopen/websocket/subscriptions_runtime.cljs` so binary rows subscribe both side coins, while question rows subscribe the visible named option side coins needed for the order form and row summaries. At minimum, subscribe Yes and No for the selected named outcome. If row summaries depend on all named outcomes staying live, subscribe all named option Yes sides with explicit owner keys and clean them up deterministically on asset switch.

Update active header and details rendering so:

- Binary rows keep the current `Countdown`, `% Chance`, `Price (Yes)`, `24h Change`, `24h Volume`, and `Open Interest` presentation.
- Question rows show the question title, selected/default option, and compact side distribution. The details copy should use the question description plus option settlement text when available.
- Plain sports/economics rows use their plain name and prose details, not the BTC `above target` template.

Milestone 4 updates account, open-order, and order form semantics for grouped outcomes. At the end of this milestone, existing user balances or orders for `+101`, `+161`, or similar tokens render as the grouped market and option label instead of raw side identifiers.

In `/hyperopen/src/hyperopen/views/account_info/projections/outcomes.cljs`, keep filtering zero-size rows, but enrich rows from the new normalized maps. A held `+101` balance should render under `May CPI year-over-year`, with option `Below 4.3%` and side `Yes`. A held `+161` balance should render under the BTC range question, with option `Below 61044` and side `Yes`. Fallback balances should remain visible with an explicit fallback label but should not count as primary market choices in the selector.

In open-order and fill projections, map outcome side coins to question title plus option/side labels. Preserve raw coin and asset id in debug attributes or tooltips for support, but do not make `#1610` or `+1610` the primary label.

In the order form view model and command builder, keep binary Yes/No behavior unchanged. For question rows, add a scoped option selector before the Yes/No side selector if the selected market has multiple named outcomes. The exchange action must still use the selected option's side asset id, not the question id. Generic spot markets remain read-only unless a separate spot-trading plan changes that policy.

Milestone 5 adds live update handling, deterministic tests, Browser MCP parity, and Playwright regression coverage. At the end of this milestone, the feature has evidence that it matches Hyperliquid and remains stable under future metadata rotation.

Add fixtures based on the June 5 live payload:

- `outcomeMeta` with outcomes `100`, `101`, `102`, `103`, `104`, `141`, `142`, `159`, `160`, `161`, `162`, and `163`, plus questions `19` and `30`.
- `webData2.spotAssetCtxs` contexts for `#1590`, `#1591`, `#1610`, `#1620`, `#1630`, `#1040`, `#1410`, and `#1420` with representative marks and volumes.

Add or update tests:

- `/hyperopen/test/hyperopen/asset_selector/markets_test.cljs`: live grouped fixture normalization, fallback skipping, question option labels, sports/economics classification, price bucket title/details, and coin resolution for named option side coins.
- `/hyperopen/test/hyperopen/views/asset_selector/processing_test.cljs`: Mainnet/testnet tab set, `Economics` and `Sports` filters, stale signature invalidation, grouped row sorting, and absence of fallback/component rows.
- `/hyperopen/test/hyperopen/views/asset_selector/controls_test.cljs` and `/hyperopen/test/hyperopen/views/asset_selector/rows_test.cljs`: desktop/mobile subtab labels and grouped row summaries.
- `/hyperopen/test/hyperopen/websocket/subscriptions_runtime_test.cljs` and `/hyperopen/test/hyperopen/runtime/action_adapters/websocket_test.cljs`: selected question subscription owner behavior and cleanup.
- Account/open-order/order-form tests covering `+101`, `+104`, `+141`, and `+161`.

Use Browser MCP for exploratory Hyperliquid-vs-local parity compare when implementation begins. Capture artifacts under `/hyperopen/tmp/browser-inspection/**`, including the live Hyperliquid Outcome tab screenshot, local Outcome tab screenshot, and a short network evidence note for `outcomeMeta`, `webData2`, and `outcomeMetaUpdates`. Convert stable local assertions into Playwright coverage under `/hyperopen/tools/playwright/test/trade-regressions.spec.mjs` or a focused outcome spec. Before concluding browser work, run `npm run browser:cleanup`.

## Concrete Steps

Run commands from `/Users/barry/.codex/worktrees/bc1d/hyperopen`.

1. Refresh live evidence before coding, because Outcome markets rotate:

       curl -sS https://api.hyperliquid.xyz/info -H 'content-type: application/json' --data '{"type":"outcomeMeta"}' | jq '{outcomes_count:(.outcomes|length), questions_count:(.questions|length), outcomes:.outcomes, questions:.questions}'
       curl -sS https://api.hyperliquid.xyz/info -H 'content-type: application/json' --data '{"type":"webData2","user":"0x0000000000000000000000000000000000000000"}' | jq '[.spotAssetCtxs[]? | select(.coin|startswith("#"))]'

   Expected current shape as of 2026-06-05: 12 outcomes, 2 questions, and 24 outcome side contexts.

2. Add RED tests for Milestone 1. Before implementation, expect failures showing that `questions` are ignored, fallback outcomes appear as standalone rows, and sports/economics/plain outcomes receive bad BTC-style titles.

3. Implement Milestone 1 and run:

       npm test -- --focus hyperopen.asset-selector.markets-test

   The runner may ignore `--focus` in this repo; if it does, record that and use the full `npm test` result as evidence.

4. Implement Milestone 2 and run:

       npm test -- --focus hyperopen.views.asset-selector.processing-test
       npm test -- --focus hyperopen.views.asset-selector.controls-test
       npm test -- --focus hyperopen.views.asset-selector.rows-test

5. Implement Milestone 3 and run websocket/action adapter tests:

       npm test -- --focus hyperopen.websocket.subscriptions-runtime-test
       npm test -- --focus hyperopen.runtime.action-adapters.websocket-test
       npm run test:websocket

6. Implement Milestone 4 and run account/order tests selected from the changed namespaces.

7. Add Playwright regression coverage for the stable local flow:

       npm run test:playwright:headed -- --grep "outcome"

   The test should seed grouped outcome metadata through existing debug helpers, open the selector, verify tabs and rows, select the BTC range question, switch named option when supported, and assert no fallback/component row is primary.

8. Run required gates:

       npm run check
       npm test
       npm run test:websocket

9. Run governed browser QA for UI-facing changes:

       npm run qa:design-ui -- --targets trade-route --manage-local-app
       npm run browser:cleanup

   Record PASS, FAIL, or BLOCKED for visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf passes at widths 375, 768, 1280, and 1440.

## Validation and Acceptance

Acceptance requires a deterministic fixture based on the June 5 live Hyperliquid payload to normalize into these user-visible rows:

- `BTC above 62290 on Jun 6 at 2:00 AM?` under `Crypto (1d)`.
- `BTC price range on Jun 6 at 2:00 AM?` under `Crypto (1d)`, with options `Below 61044`, `61044 to 63535`, and `Above 63535`.
- `May CPI year-over-year` under `Economics`, with options `Below 4.3%`, `Exactly 4.3%`, and `Above 4.3%`.
- `June Fed rate change` under `Economics`, with sides `Change` and `No Change`.
- `NBA Finals Game 2` and `2026 NBA Finals champion` under `Sports`, with basketball metadata retained.

The selector must not show fallback outcome `100`, fallback outcome `160`, or component outcomes `161`, `162`, and `163` as standalone primary market rows when their question rows are present. A search for `BTC` should show the current binary BTC and BTC range question, not expired BTC rows from a prior day. A search for `NBA` should show sports rows. A search for `CPI` or `Fed` should show economics rows.

Selecting a binary row must preserve current Yes/No order form behavior and correct outcome asset ids. Selecting a grouped question row must choose a default named outcome side based on current marks, expose an option selector, and submit using the selected option side asset id. Account outcome balances and open orders must display question/option/side labels instead of raw `#` or `+` identifiers.

Required gates before completion are `npm run check`, `npm test`, `npm run test:websocket`, focused Playwright outcome coverage, governed browser QA accounting, and `npm run browser:cleanup`.

## Idempotence and Recovery

The implementation should be additive until final cleanup. If live `outcomeMeta` becomes unavailable, retain existing perps/spot hydration and return an empty normalized outcome universe with a logged recoverable warning. Do not block the entire asset selector because outcome metadata failed.

If a cached Outcome universe conflicts with fresh `outcomeMeta`, fresh metadata wins. Cached active outcome selections whose outcome id or question id is absent from fresh metadata should fall back to the default active asset, matching the existing selected-market expiry protection. If the user has a balance or open order for a no-longer-listed fallback/component outcome, keep it visible in account/order tables with a fallback label, but do not list it as a selector market.

If grouped question subscriptions cause duplicate websocket traffic, prefer a narrower selected-option subscription first and compute non-selected option summaries from `webData2` until `outcomeMetaUpdates` or `spotAssetCtxs` provides a cleaner live row update path. Record that decision in this plan before completion.

## Artifacts and Notes

Live `outcomeMeta` snapshot summary from 2026-06-05:

    outcomes_count: 12
    questions_count: 2
    standalone/plain outcomes: 104 June Fed rate change, 141 NBA Finals Game 2, 142 2026 NBA Finals champion
    recurring binary outcome: 159 class:priceBinary|underlying:BTC|expiry:20260606-0600|targetPrice:62290|period:1d
    CPI question: question 19, fallbackOutcome 100, namedOutcomes [101 102 103]
    BTC range question: question 30, fallbackOutcome 160, namedOutcomes [161 162 163], description class:priceBucket|underlying:BTC|expiry:20260606-0600|priceThresholds:61044,63535|period:1d

Representative public outcome side contexts from 2026-06-05:

    #1590 markPx 0.03522, dayNtlVlm 628568.0347699994, circulatingSupply 231239.0
    #1591 markPx 0.96478, dayNtlVlm 1181700.9652299997, circulatingSupply 231239.0
    #1040 markPx 0.012535, dayNtlVlm 1025.91579, circulatingSupply 227942.0
    #1410 markPx 0.67279, dayNtlVlm 29092.49023, circulatingSupply 50529.0
    #1420 markPx 0.47708, dayNtlVlm 94713.3387, circulatingSupply 241849.0
    #1610 markPx 0.7176, dayNtlVlm 13944.46462, circulatingSupply 6183.0
    #1620 markPx 0.22, dayNtlVlm 15061.54122, circulatingSupply 30561.0
    #1630 markPx 0.008055, dayNtlVlm 5387.85978, circulatingSupply 22214.0

Frontend bundle inference from `/static/js/main.712d932d.js`:

    Mainnet tabs: ["all","crypto-1d","economics","sports"]
    Non-Mainnet tabs: ["all","crypto-15m","crypto-1d","economics","sports"]
    Period tab map: {"crypto-15m":"15m","crypto-1d":"1d"}
    Category parser: uses metadata category economics/sports, else CPI/Fed name fallback for economics.
    Sports subcategory map observed: basketball -> basketball_outcome, football -> football_outcome.
    Question normalization: creates outcomeToSpec, questionToSpec, outcomeToQuestion, and fallbackOutcomes; recurring priceBucket questions replace component outcomes with bucket labels.

## Interfaces and Dependencies

No new third-party dependency is required. Add pure interfaces in `/hyperopen/src/hyperopen/asset_selector/markets.cljs` or a small adjacent namespace if the file needs to stay readable:

    normalize-outcome-meta
    [outcome-meta spot-asset-ctxs opts] -> {:markets [...]
                                            :outcome-spec-by-id {...}
                                            :question-spec-by-id {...}
                                            :outcome-id->question-id {...}
                                            :fallback-outcome-ids #{...}}

    build-outcome-markets
    [outcome-meta spot-asset-ctxs] -> vector of normalized selector rows

    resolve-outcome-route-side
    [market clicked-option-or-side] -> {:coin string
                                        :asset-id integer
                                        :outcome-id integer
                                        :side-index integer}

    outcome-category
    [normalized-outcome-or-question] -> :crypto, :economics, :sports, or :uncategorized

Keep existing public helpers `outcome-encoding`, `outcome-coin`, and `outcome-asset-id` stable because order command tests and account projections already rely on them.

Revision note: 2026-06-05 19:08Z - Created from the user's parity request, live Hyperliquid endpoint evidence, live frontend bundle inspection, and current Hyperopen source review.
