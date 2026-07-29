# Lazy-load the Funding workflow registry without delaying active-asset predictability

This ExecPlan is a living document. Maintain it under `.agents/PLANS.md` as work proceeds. It freezes the user-approved M2a slice before any production or test edit: move the heavy Funding modal workflow registry out of the release `main` module, while leaving the active-asset funding-predictability path immediately available on `/trade`.

## Purpose / Big Picture

On a cold desktop visit to `/trade`, users who do not open Deposit, Transfer, Send, Repay, or Withdraw should not download and execute the Funding workflow implementation. A user who does open one of those flows must get the same modal and submit behavior after the `funding_modal` module loads. Changing the active asset must still immediately start the Funding predictability request used by the trade funding tooltip; this path cannot wait for a modal module.

The measurable stop condition for this approved slice is a release build whose `main` gzip size is at most 640000 bytes. Once that condition is reached, stop rather than expanding M2a into staking, referrals, subaccounts, or API-wallets. This plan does not claim a new PageSpeed score. The user separately authorized GitHub publication and a Cloudflare deployment after implementation acceptance, so the release evidence is recorded without expanding the M2a code scope.

## Context References

Public refs:

- Direct user request, captured 2026-07-29: implement M2a by lazy-loading Funding first, retaining eager predictability, and stop at release `main` gzip `<= 640000` bytes.
- Direct user request, captured 2026-07-29 after implementation: publish the accepted change to GitHub and deploy the existing DEXHelm Cloudflare Worker.

Repo artifacts:

- `docs/exec-plans/completed/2026-04-20-root-bundle-follow-up-surfaces-and-runtime-aggregators.md` documents the earlier lazy-route registry pattern.
- `tools/release-assets/bundle-budget.json` defines an advisory 640000-byte gzip target. It must not be changed in this slice.
- `docs/BROWSER_TESTING.md` governs the deterministic browser checks described below.

Local scratch references (non-authoritative): none.

## Progress

- [x] (2026-07-29 19:07 CST) Re-scoped this active plan from historical M0-M5 performance work to the approved Funding-only M2a implementation.
- [x] (2026-07-29 19:07 CST) Confirmed the eager dependency path: `src/hyperopen/app/effects.cljs` registers Funding workflow effects through `src/hyperopen/runtime/effect_adapters/funding.cljs`, which statically requires `hyperopen.funding.effects`.
- [x] (2026-07-29 19:07 CST) Confirmed the eager exception: `:effects/sync-active-asset-funding-predictability` is emitted by asset selection and uses `hyperopen.funding.history-cache` plus `hyperopen.funding.predictability`.
- [x] (2026-07-29, TDD RED) Added the approved root-registry, runtime-wiring, and effect-only Funding runtime-export tests. `env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH npx shadow-cljs --force-spawn compile test` compiled cleanly; the generated runner with `NODE_OPTIONS=--localstorage-file=/tmp/hyperopen-funding-m2a-red-localstorage` ran 5,863 tests and failed 20 assertions with 0 errors, led by the seven still-eager Funding workflow adapters and the absent `:funding-modal` lazy registry/export.
- [x] (2026-07-29 20:02 CST) Implemented the Funding-only effect registry split. The seven workflow effects now resolve through the `funding_modal` Shadow module, while `:sync-active-asset-funding-predictability` remains eager. After the fail-closed handler review fix, the final full ClojureScript suite passed: 5,865 tests, 32,654 assertions, 0 failures, 0 errors.
- [x] (2026-07-29 20:02 CST) Added deterministic browser coverage proving cold `/trade` requests no `funding_modal` resource, the first real Funding opener requests it once, and Deposit, Transfer, and Withdraw reuse the loaded module. The focused Playwright scenario passed.
- [x] (2026-07-29 20:02 CST) Met the stop condition. The release main changed from `main.AB7140D03F1D1E66FBF2D3D016FA9FA5.js` at 659,039 gzip bytes to `main.E314C9428CA5C7E17CD7AA48D8A72B4E.js` at 632,116 gzip bytes, a 26,923-byte reduction with 7,884 bytes of target headroom. No other M2a domain was split.
- [x] (2026-07-29 20:02 CST) The cold `/trade` profiler improved from `blockingTimeProxyMs=85`, `maxSingleBlockingTaskMs=118` to `blockingTimeProxyMs=49`, `maxSingleBlockingTaskMs=99`; neither profile requested `funding_modal` at startup.
- [x] (2026-07-29 20:46 CST) Final review hardened cached and post-load handler validation to fail closed on non-functions. The final release is `main.DD630824C70F18A6B50CBF86C08537E8.js` at 632,135 gzip bytes. The two focused Funding Playwright scenarios passed; all six governed browser-QA passes succeeded at 375, 768, 1280, and 1440 widths; and `npm run gates` passed all 35 gates (6,619 tests, 36,050 assertions).
- [x] (2026-07-29 22:12 CST) Fixed a release-blocking responsive regression found during final browser QA: an already-open desktop Funding popover now becomes a full-width, scrollable bottom sheet when the viewport narrows to 640px or less. The 1440-by-900 to 375-by-812 Playwright regression and the three-scenario Funding subset passed; the governed four-width design review passed all six categories with zero issues.
- [x] (2026-07-29 22:12 CST) Re-ran the complete repository and release gates. `npm run gates` passed 35/35 with 6,619 tests and 36,050 assertions; Cloudflare Worker tests passed 32/32, release-asset tests passed 51/51, release SEO passed 7/7, and the DEXHelm white-label suite passed 4/4. Artifact preflight passed 33 checks with no failures, and Wrangler dry-run read 57 assets and completed successfully.
- [x] (2026-07-29 22:12 CST) Recorded the final pre-deploy release: `main.C8233A6435F788320D9625483DD83263.js` is 2,701,468 raw / 632,138 gzip bytes, leaving 7,862 bytes of budget headroom; `funding_modal.2A0AFDD686D2792BCB6C7CD142D9E816.js` is 159,049 raw / 36,428 gzip bytes.
- [x] (2026-07-29 22:23 CST) Published implementation commit `e4255534e3f0894f1599b358ba91cdf33e0fea0a` to `haocn-ops/hyperopen` `main` and deployed the existing Cloudflare Worker `hyperopen`. Wrangler returned version `32112083-f3ac-4077-8138-5170efe4a7ca` for `dexhelm.com`, `app.dexhelm.com`, `testnet.dexhelm.com`, and `status.dexhelm.com`.
- [x] (2026-07-29 22:23 CST) Verified the public release. Deployment headers and immutable assets passed on `https://testnet.dexhelm.com`; the Testnet HyperUnit fee probe returned 200 while Mainnet and generic proxy paths returned 404; the domain matrix was apex 200, Testnet `/trade` 200, intentionally closed Mainnet app 503, and status 200. The public logo returned 200 `image/svg+xml`, `/api/health` returned `{"status":"ok"}`, and `/trade` exposed the DEXHelm route title.

## Surprises & Discoveries

- Observation: the existing `funding_modal` shadow module does not by itself remove Funding workflow code from `main`.
  Evidence: `shadow-cljs.edn` already declares `:funding_modal`, while `src/hyperopen/app/effects.cljs` still directly binds `api-fetch-hyperunit-*` and `api-submit-funding-*` handlers from the eager Funding adapter.

- Observation: a lazy effect wrapper already loads a module and rejects when its handler remains absent.
  Evidence: `src/hyperopen/route_modules.cljs` implements `lazy-route-effect-leaf-deps`; its wrapper calls `load-shadow-module!`, then resolves the handler or throws `route-runtime-handler-error`.

- Observation: the active-asset predictability effect is part of the asset-selection contract, not a modal-only detail.
  Evidence: `test/hyperopen/asset_selector/actions_test.cljs` expects `[:effects/sync-active-asset-funding-predictability <coin>]` after asset selection, and `test/hyperopen/runtime/effect_adapters/funding_test.cljs` verifies its loading, success, and error state transitions.

- Observation: the modal layout breakpoint was evaluated only when Replicant rendered the Funding modal, so resizing an already-open desktop popover to 375px retained its 448px inline width and clipped 93px beyond the viewport.
  Evidence: the final browser QA measured the retained desktop node at `x=20`, `width=448`, and `right=468` in a 375px viewport; the added Playwright test reproduces the same 1440px-to-375px transition.

## Decision Log

- Decision: implement Funding before every other deferred M2a registry domain.
  Rationale: a Funding modal shadow module already exists, the heavy workflow adapters are visibly eager, and the user approved this smallest independently measurable slice.
  Date/Author: 2026-07-29 / spec_writer.

- Decision: retain `:sync-active-asset-funding-predictability` and its history-cache/predictability dependencies in the eager runtime.
  Rationale: asset selection dispatches it synchronously and the trade funding tooltip must keep its current loading, success, and error behavior without waiting for a modal download.
  Date/Author: 2026-07-29 / spec_writer.

- Decision: reuse `route-modules/lazy-route-effect-leaf-deps` for Funding workflow effect keys, extending its runtime-export registry to model the existing `funding_modal` shadow module without treating it as a navigable route.
  Rationale: the helper already provides exactly the needed promise behavior: invoke a loaded handler immediately, otherwise load the owning module, then invoke the resolved handler or surface an error. Duplicating a second lazy-loader would add a competing cache and failure model.
  Date/Author: 2026-07-29 / spec_writer.

- Decision: do not reduce the scope merely by deferring the Funding view.
  Rationale: the view module is already lazy, but the static workflow adapter require keeps the implementation graph in `main`; only moving the registry binding behind the module boundary removes that eager dependency.
  Date/Author: 2026-07-29 / spec_writer.

- Decision: make the retained desktop Funding shell responsive with CSS instead of introducing a global resize listener or new application state.
  Rationale: CSS reacts immediately to viewport changes, can override the stale inline anchor geometry at the existing 640px breakpoint, and avoids adding runtime side effects or another eager dependency to `main`.
  Date/Author: 2026-07-29 / worker.

## Outcomes & Retrospective

Implementation outcome on 2026-07-29: the Funding workflow effect graph moved from release `main` into the existing `funding_modal` module. Final main gzip fell by 26,901 bytes from 659,039 to 632,138, satisfying the 640,000-byte stop condition with 7,862 bytes of headroom without touching the budget or widening into another registry domain. The loaded Funding module is 159,049 raw / 36,428 gzip bytes and is requested only on first use. The final responsive correction also keeps an open Funding panel within the viewport when a desktop window narrows to mobile width.

Complexity assessment: the implementation adds one effect-only module-runtime export and reuses the existing lazy-wrapper abstraction. It reduces startup dependency complexity by removing the static `hyperopen.funding.effects` edge from `main`; the added asynchronous boundary is bounded by the existing Shadow loader, explicit missing-handler failures, and Promise-preserving wrappers. The responsive fix adds scoped CSS hooks only and avoids resize-event state, so it does not add runtime coordination complexity.

Release outcome: Cloudflare version `32112083-f3ac-4077-8138-5170efe4a7ca` updated the same `hyperopen` Worker and preserved the four configured DEXHelm custom domains. Testnet remains enabled, the Mainnet application hostname remains intentionally closed with HTTP 503, and no wallet signature, deposit, withdrawal, transfer, or order was automated during verification.

## Context and Orientation

Hyperopen builds ClojureScript into a release `main` JavaScript module plus shadow modules. A shadow module is a separately downloaded JavaScript file. `shadow-cljs.edn` already defines `:funding_modal`, whose entry namespace is `src/hyperopen/views/funding_modal_module.cljs`. It currently exports only the Funding modal view.

At startup, `src/hyperopen/runtime/wiring.cljs` asks `src/hyperopen/app/effects.cljs` for runtime effect dependencies. The `:api` map in `app/effects.cljs` eagerly references these Funding workflow handlers through the facade in `src/hyperopen/runtime/effect_adapters.cljs`:

- `:api-fetch-hyperunit-fee-estimate`
- `:api-fetch-hyperunit-withdrawal-queue`
- `:api-submit-funding-transfer`
- `:api-submit-funding-send`
- `:api-submit-funding-repay`
- `:api-submit-funding-withdraw`
- `:api-submit-funding-deposit`

Those handler functions are implemented in `src/hyperopen/runtime/effect_adapters/funding.cljs`; its `hyperopen.funding.effects` require is the heavy workflow edge that belongs in the modal module. In contrast, `sync-active-asset-funding-predictability` in the same file reads the current asset and tooltip state, updates loading/error/summary state, and uses the Funding history cache. It must remain an eager binding in `app/effects.cljs`.

`src/hyperopen/route_modules.cljs` owns the existing lazy runtime-dependency pattern. `lazy-route-effect-leaf-deps` returns stable effect handlers for the root registry. When an effect is invoked before its module is loaded, the handler loads the named shadow module, resolves its exported `effect-deps` function, and then invokes the originally requested handler. This behavior is already used by the Portfolio and Vaults modules. Its current runtime-export readiness check requires both `action-deps` and `effect-deps`; Funding needs no lazy actions, so make readiness follow the exports declared for each module and allow `funding_modal` to be effect-only. The Funding implementation must add that equivalent runtime export without adding a URL route or changing public action/effect IDs.

The release artifact measurement is deterministic for one local build: `npm run build` writes `resources/public/js/manifest.json`, whose entry with `"module-id": "main"` names the fingerprinted main file. `node tools/release-assets/check_bundle_budget.mjs` gzips that file at level 9 and reports its byte count. The 640000-byte target is advisory in tooling, but it is the explicit scope-completion criterion for this plan.

## Performance Baseline and Workload

The bottleneck is an eager static import, not an algorithmic hot path. The current cold `/trade` workload is: clear or new browser storage, desktop viewport, visit `/trade`, allow the initial UI to settle, and do not open a Funding modal. Under that workload the Funding workflow is unused, yet it is reachable from the runtime registry and therefore contributes to `main` parsing and evaluation.

Before changing code, run from the repository root:

    npm run setup:worktree
    npm run build
    node tools/release-assets/check_bundle_budget.mjs
    npm run browser:profile:trade-startup:cached

Record the `main` gzip line, the manifest-selected main output name, `blockingTimeProxyMs`, and the profiler module timeline in this plan's Progress section. The profile is evidence about startup work; it is not a release-quality PageSpeed result. The simpler alternative, leaving the registry eager while only lazy-loading the view, is insufficient because the static `funding-workflow-effects` require remains in `main`, as confirmed above.

## Plan of Work

First, establish the module boundary. Update `src/hyperopen/route_modules.cljs` so its lazy runtime-export machinery recognizes an internal module id for the existing `funding_modal` shadow module and can resolve an exported `effect-deps` function after loading it. Change the runtime-export readiness check to require only the export types declared for the module: Portfolio and Vaults still require both action and effect exports, while Funding requires only its effect export. Keep `route-module-id` unchanged: Funding modal loading is a surface concern, not a URL route. Extend `src/hyperopen/views/funding_modal_module.cljs`, or a Funding runtime namespace required exclusively by it, to export a function that builds the seven workflow `:api` handlers for a supplied runtime. The exported function must be globally resolvable using the same advanced-compilation-safe export pattern as Portfolio and Vault runtime modules.

Second, split the eager Funding adapter at the dependency boundary. In `src/hyperopen/runtime/effect_adapters/funding.cljs` and `src/hyperopen/runtime/effect_adapters.cljs`, retain only the eager predictability and other non-modal Funding behavior that is still needed at startup. Move the seven workflow adapter functions and their `hyperopen.funding.effects` dependency into the Funding modal runtime export. In `src/hyperopen/app/effects.cljs`, define the approved workflow key list once, obtain wrappers with `route-modules/lazy-route-effect-leaf-deps runtime :funding-modal :api <keys>`, and merge those wrappers into the existing `:api` effect map. Leave `:sync-active-asset-funding-predictability` as a direct eager function value with the same public effect id, parameters, and state behavior.

The wrapper must preserve the original effect call arguments and return its promise. A dispatch that happens before the module resolves must either invoke the intended handler after loading or reject with a visible module/handler error. It must never silently return success, discard the effect, reorder the action's already-emitted effects, or substitute an alternate Funding operation. Preserve the existing module cache so concurrent first-use effects share the same module load.

Third, add only the approved regression coverage. Update `test/hyperopen/app/effects_test.cljs` and `test/hyperopen/runtime/wiring_test.cljs` to prove the root registry exposes a lazy function for every listed workflow key and the eager predictability binding remains the direct adapter function. Update `test/hyperopen/route_modules_test.cljs` to prove an unloaded Funding wrapper loads `funding_modal`, invokes the resolved handler with unchanged arguments, and rejects when loading or export resolution fails. Update `test/hyperopen/runtime/effect_adapters/funding_test.cljs` only as necessary to retain the predictability loading/success/error assertions while moving workflow-wrapper assertions to their new owner. Keep the existing Funding application tests under `test/hyperopen/funding/**` as the behavioral contract for transfer, send, repay, withdraw, deposit, and HyperUnit flows; add narrow cases only if the module boundary exposes a currently untested interface.

Finally, update the existing Funding subset in `tools/playwright/test/trade-regressions.spec.mjs`. On a cold `/trade`, collect requested JavaScript URLs after initial route settling and prove no `funding_modal` artifact is requested before a Funding opener is used. Trigger Deposit, Transfer, and Withdraw through their real controls; each must load the module once and expose the existing modal title/state. Retain the existing Deposit asset-selection and tooltip scenarios to prove opening the module did not regress user-visible behavior. Browser tests must use the established fixture and cleanup path from `docs/BROWSER_TESTING.md`.

During final release QA, add a scoped responsive fallback in `src/hyperopen/views/funding_modal.cljs` and `src/styles/surfaces/app-shell.css`. The desktop shell keeps its anchored geometry above 640px, but at or below the breakpoint CSS overrides stale inline anchor coordinates and presents the retained node as a full-width bottom sheet. Extend `tools/playwright/test/trade-regressions.spec.mjs` to open Deposit at 1440px, resize the same page to 375px, and prove the dialog stays inside the viewport without horizontal document overflow.

## Approved Test Surfaces

The implementation may modify only the tests required by this contract:

- `test/hyperopen/app/effects_test.cljs` for root effect-map ownership and eager predictability.
- `test/hyperopen/route_modules_test.cljs` for effect-only Funding runtime-export readiness, first-load, resolved-handler, and failure propagation of the Funding lazy effect wrapper, while retaining the existing action-plus-effect requirement for Portfolio and Vaults.
- `test/hyperopen/runtime/wiring_test.cljs` for composition through `runtime/wiring`.
- `test/hyperopen/runtime/effect_adapters/funding_test.cljs` for the retained eager predictability facade and any relocated adapter contract.
- Existing relevant tests under `test/hyperopen/funding/**`, especially the effect/query/submit tests that correspond to the seven listed keys.
- `test/hyperopen/asset_selector/actions_test.cljs` only if an assertion needs to make the eager predictability ordering explicit.
- `tools/playwright/test/trade-regressions.spec.mjs` for the cold-load request assertion and existing Funding modal flows.

Do not modify `tools/release-assets/check_bundle_budget.mjs` or its tests unless the implementation unexpectedly proves the measurement cannot identify the release main artifact; that tooling is not part of this M2a change.

## Non-Goals

- Do not lazy-load Funding history cache or predictability code, including `:sync-active-asset-funding-predictability`.
- Do not split staking, referrals, subaccounts, API-wallets, Funding comparison, or any non-Funding domain.
- Do not change Funding APIs, wallet signing, persistence, business rules, public action/effect identifiers, or the ordering of effects emitted by actions.
- Do not change the bundle-budget policy, re-ratchet `bundle-budget.json`, or claim a PageSpeed score. GitHub publication and Cloudflare deployment remain separately authorized release operations after acceptance.
- Do not make unrelated UI, CSS, data-plane, or browser-storage changes; the responsive Funding shell correction is limited to the release-blocking resize regression.

## Concrete Steps

Work from `/Users/zh/Documents/Hyperopen`.

1. Bootstrap the worktree before any code gate. Run `npm run setup:worktree`. If it reports no reusable dependency directory, run `npm ci` only after obtaining the required dependency-install permission, then rerun setup.

2. Capture the pre-change release and startup evidence with the four commands in `Performance Baseline and Workload`. The bundle command must identify one hashed `main.<hash>.js` and print its gzip byte count. Preserve the exact values in Progress rather than replacing the 640000-byte target.

3. Implement the module export, adapter split, and root-registry wrapper changes in the named source files. Run the focused ClojureScript test route supported by the repository, or run `npm test` when no stable test-name filter is available. A focused failure must name the added test and demonstrate the pre-change eager/failure behavior; do not leave an intentionally failing test committed.

4. Run the focused deterministic browser coverage:

       npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "funding modal deposit flow selects USDC|trade funding openers launch the funding modal|open desktop funding modal adapts when viewport narrows"

   Extend this selector only after the cold-load network assertion is added. Expect the selected Funding scenarios to pass, with one `funding_modal` request only after the first opener.

5. Run the complete implementation validation sequence:

       npm run browser:profile:trade-startup
       node tools/release-assets/check_bundle_budget.mjs
       npm run check
       npm test
       npm run test:websocket
       npm run gates

   The profile command creates a fresh release build. Record its JSON artifact path, the module timeline, and `blockingTimeProxyMs`. `npm run gates` must report PASS for `check`, `test`, and `test:websocket`. If an unrelated pre-existing failure prevents a clean gate, record the exact command, failing file, and evidence that the Funding tests passed separately; do not waive a failure without evidence.

6. Confirm the stop condition using the post-change `check_bundle_budget` output. When `main` gzip is `<= 640000`, mark this plan complete and do not begin another M2a domain. When it is above 640000, document the measurement and stop for a new scope decision; do not silently broaden the change.

## Validation and Acceptance

- A release build followed by `node tools/release-assets/check_bundle_budget.mjs` reports the manifest-selected `main` artifact at `<= 640000` gzip bytes. This is the required stop condition for this plan.
- The cold `/trade` Playwright scenario observes no `funding_modal` JavaScript request before a Deposit, Transfer, or Withdraw opener, then observes the module load and the expected modal title after the opener. The checked surface is `tools/playwright/test/trade-regressions.spec.mjs`.
- The focused `route_modules` tests prove an effect-only `funding_modal` runtime export is ready, while the existing action-plus-effect modules retain their requirements; every approved workflow key waits for `funding_modal` to load, receives the original context/store/arguments, and rejects on a module-load or missing-effect-export failure. The observable result is a passing `npm test` suite containing those assertions.
- The Funding modal behaviors remain usable: the focused Playwright command opens Deposit, Transfer, and Withdraw through real controls, and existing Funding application tests continue to pass for their submit/query paths.
- A Funding modal opened at 1440-by-900 and retained while resizing to 375-by-812 becomes a full-width bottom sheet whose left, right, and bottom edges stay within the viewport and whose document does not gain horizontal overflow.
- Asset selection still synchronously emits `:effects/sync-active-asset-funding-predictability`, and the adapter tests preserve its loading, successful summary, and error state transitions. The checked surfaces are `test/hyperopen/asset_selector/actions_test.cljs` and `test/hyperopen/runtime/effect_adapters/funding_test.cljs`.
- `npm run check`, `npm test`, `npm run test:websocket`, and `npm run gates` pass after implementation. The browser profiler records a post-change cold-desktop `/trade` timeline without an eager Funding modal request; it may not regress `blockingTimeProxyMs` relative to the recorded pre-change baseline.

## Idempotence and Recovery

All measurement and test commands are read-only with respect to tracked source except generated build artifacts. Re-running `npm run build` replaces generated release output and is the correct recovery after a partial build. If the module does not export its handler map after loading, restore the direct eager binding only in a local diagnostic change, capture the error, and return to the existing lazy-wrapper contract; do not add a fallback that drops an operation. If the gzip result exceeds 640000 after the complete Funding-only change, retain the working implementation and request a new scope decision rather than changing another registry domain or changing the budget.

## Artifacts and Notes

Before implementation, add a compact evidence entry under Progress in this form:

    pre-change build: main.<hash>.js gzip=<bytes>
    pre-change profile: tmp/browser-inspection/<run>/profile.json; blockingTimeProxyMs=<value>

After implementation, add the corresponding post-change values and the command results. Do not copy browser artifacts into the repository unless a separate request requires durable QA evidence.

## Interfaces and Dependencies

The root runtime contract remains a nested map returned by `hyperopen.app.effects/runtime-effect-deps`. Its `:api` keys are registered under public `:effects/<key>` identifiers; those names and argument shapes must not change. The Funding lazy wrapper must conform to the existing `route-modules/lazy-route-effect-leaf-deps` interface:

    (lazy-route-effect-leaf-deps runtime module-id group-key handler-keys)

For this work the values are `runtime`, `:funding-modal`, `:api`, and the seven approved workflow keys. `route_modules` must accept a module whose declared runtime interface contains only `effect-deps`; it must continue to require both exports for modules that declare both. The loaded `funding_modal` module must export an `effect-deps` function that accepts `runtime` and returns a map shaped as:

    {:api
     {:api-fetch-hyperunit-fee-estimate <effect-function>
      :api-fetch-hyperunit-withdrawal-queue <effect-function>
      :api-submit-funding-transfer <effect-function>
      :api-submit-funding-send <effect-function>
      :api-submit-funding-repay <effect-function>
      :api-submit-funding-withdraw <effect-function>
      :api-submit-funding-deposit <effect-function>}}

Each `<effect-function>` accepts the normal registry invocation `(ctx store & args)` and returns its original value or promise. In particular, asynchronous Funding submissions must return their promise so failures continue through the registry rather than disappearing.

Plan refresh note (2026-07-29): replaced the broad historical M0-M5 performance record with the user-approved Funding-only M2a execution contract. This avoids treating unapproved registry domains or PageSpeed follow-up as active work. Later refreshes record the release-blocking responsive fix and the separately authorized GitHub/Cloudflare deployment, including public verification, without broadening the implementation domain.
