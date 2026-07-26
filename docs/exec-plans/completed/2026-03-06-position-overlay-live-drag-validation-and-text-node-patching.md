# Position Overlay Live Drag Validation and Text-Node Patching

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`.

## Purpose / Big Picture

The previous position-overlay repaint refactor removed the expensive root-level teardown path, but two follow-up gaps remain. First, live browser QA still has not proven the full liquidation drag interaction end to end on a real chart surface. Second, the overlay still updates visible text through element `textContent`, which can replace child text nodes even though the overlay row elements themselves are retained.

After this plan is implemented, contributors will be able to verify live drag behavior in a visible browser session using a documented, repeatable recipe, and the overlay text path will update persistent text nodes in place instead of replacing subtree text children. The result should be stronger browser-level confidence in the drag workflow and a narrower repaint footprint than the current post-refactor baseline.

## Progress

- [x] (2026-03-06 14:00Z) Re-read `/hyperopen/.agents/PLANS.md` and confirmed this follow-up requires a dedicated ExecPlan.
- [x] (2026-03-06 14:02Z) Audited current overlay patching code in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs`, fake DOM support in `/hyperopen/test/hyperopen/views/trading_chart/test_support/fake_dom.cljs`, and browser inspection constraints in `/hyperopen/tools/browser-inspection/src/session_manager.mjs`.
- [x] (2026-03-06 14:04Z) Reproduced the current browser QA state on local `/trade` using spectate address `0x162cc7c861ebd0c06b3d72319201150482518185` and confirmed that asset `MON` renders both overlay rows while live market repaints keep root children stable.
- [x] (2026-03-06 14:06Z) Authored this active ExecPlan.
- [ ] Create and claim a `bd` follow-up issue for this work (blocked at planning time: local Dolt server on `127.0.0.1:13881` was not accepting connections for `bd` commands).
- [x] (2026-03-06 14:09Z) Replace element-level `textContent` patching with persistent text-node patching in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs`.
- [x] (2026-03-06 14:10Z) Extend fake DOM support and regression tests so they can assert text-node identity and no subtree child-list churn for text-only overlay updates.
- [x] (2026-03-06 14:17Z) Run a visible-browser live drag QA pass on local `/trade` with the documented spectate address and asset recipe, then publish the artifact and findings under `/hyperopen/docs/qa/`.
- [x] (2026-03-06 14:24Z) Run required validation gates and record acceptance outcomes: `npm run check`, `npm test`, `npm run test:websocket`.

## Surprises & Discoveries

- Observation: The browser inspection subsystem is intentionally read-only and blocks mutating eval patterns such as `click(`, `dispatchEvent(`, `.value =`, and storage writes unless the operator explicitly opts out.
  Evidence: `/hyperopen/tools/browser-inspection/src/session_manager.mjs` `validateEvalForReadOnly` rejects configured mutation patterns before eval execution.

- Observation: The current overlay refactor already eliminated root-level child churn during live market-driven repaints.
  Evidence: A browser `MutationObserver` on `.chart-position-overlays` reported `0` added and `0` removed root children over a 12 second live repaint window while overlay text changed.

- Observation: A broader subtree `MutationObserver` still reports added and removed nodes during text updates even when the retained PNL/liquidation element nodes remain identical.
  Evidence: In the same browser QA session, `samePnlChip`, `sameLiqChip`, and `sameDragHit` were all `true`, but subtree child-list counts increased while visible PNL text changed.

- Observation: The current fake DOM supports `createElement` but not `createTextNode`, so tests cannot currently model or assert persistent text-node identity.
  Evidence: `/hyperopen/test/hyperopen/views/trading_chart/test_support/fake_dom.cljs` exposes `createElement` and `createElementNS` only and stores text as element `textContent`.

- Observation: The spectate address `0x162cc7c861ebd0c06b3d72319201150482518185` is a useful browser QA fixture because it has live positions, and asset `MON` renders both the PNL row and liquidation row in the visible chart viewport.
  Evidence: Local browser inspection on `http://localhost:8080/trade?spectate=0x162cc7c861ebd0c06b3d72319201150482518185` showed only the PNL row for `BTC`, but after setting active asset to `MON`, the overlay rendered both row nodes with visible PNL and liquidation chips.

- Observation: During the live drag workflow, the margin modal opens on the first preview move and remains open after release.
  Evidence: Headed browser QA on 2026-03-06 produced `/hyperopen/tmp/browser-inspection/manual-position-overlay-drag-2026-03-06T14-16-41-953Z/summary.json`, and `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs` already expects the preview callback to dispatch `:chart-liquidation-drag-margin-preview`.

- Observation: The text-node micro-optimization is observable in a real browser, not only in fake DOM tests.
  Evidence: The same QA run recorded `sameTextNodes: true`, `sameChipTextNode: true`, and `0` added / `0` removed child nodes on the liquidation badge subtree while the visible price and margin text changed.

- Observation: `bd` tracking remains unavailable after a retry because Dolt still does not accept connections on `127.0.0.1:13881`.
  Evidence: `bd ready --json` failed on 2026-03-06 after auto-start reported `server started (PID 24273) but not accepting connections on port 13881: timeout after 10s`.

## Decision Log

- Decision: Close the live drag validation gap with a visible-browser QA workflow and artifact, not by widening the browser inspection subsystem with general mutating input commands.
  Rationale: The missing piece is end-to-end validation, not product behavior. A documented visible-browser recipe preserves the read-only design of `/hyperopen/tools/browser-inspection/` while still producing reproducible evidence.
  Date/Author: 2026-03-06 / Codex

- Decision: Use the existing spectate address `0x162cc7c861ebd0c06b3d72319201150482518185` and active asset `MON` as the canonical manual QA target for this plan.
  Rationale: This combination already proved that both overlay rows render in the local chart viewport, avoiding a brittle search for a new account/asset pair.
  Date/Author: 2026-03-06 / Codex

- Decision: Replace `set-text-content!`-style element patching with persistent text-node references updated through `nodeValue` or `data`.
  Rationale: This removes the remaining subtree child-list churn without reopening the broader overlay row caching design.
  Date/Author: 2026-03-06 / Codex

- Decision: Extend the fake DOM test seam rather than relying only on browser observation for text-node stability assertions.
  Rationale: The micro-optimization is an internal DOM ownership change and should be locked by deterministic tests in addition to manual/browser QA.
  Date/Author: 2026-03-06 / Codex

## Outcomes & Retrospective

Implementation is complete:

- Overlay badge/chip/drag-note text now updates through persistent text-node references.
- Fake DOM coverage now models text nodes and regression tests assert retained text-node identity.
- Headed browser QA produced a documented artifact under `/hyperopen/docs/qa/position-overlay-live-drag-and-text-node-validation-2026-03-06.md` with live drag screenshots and DOM-stability evidence.
- Required validation gates passed: `npm run check`, `npm test`, and `npm run test:websocket`.

The only remaining non-code blocker is `bd` infrastructure health. No further implementation work is pending for this plan.

## Context and Orientation

The current overlay implementation lives in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs`. The previous refactor retained the two top-level overlay row groups:

- a PNL row for the position PNL label and entry-price chip;
- a liquidation row for the liquidation label, drag note, drag hit area, and liquidation-price chip.

That work fixed the original root-level repaint problem by removing normal-path `clear-children!` from `render-overlays!`. However, text is still patched through `set-text-content!`, which assigns to element `textContent`. In browser DOM, that can replace child text nodes even when the parent element stays the same.

The live drag workflow itself was implemented earlier in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` and wired through `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` into the existing margin-modal flow. Unit tests already cover callback emission and modal prefill behavior, but there is not yet a published browser-level QA artifact proving the same interaction on a real rendered chart.

Browser inspection tooling lives under `/hyperopen/tools/browser-inspection/`. It supports visible Chrome sessions, navigation, read-only eval, snapshot capture, and attach mode, but it is intentionally guarded against generic mutating eval. The intended way to validate live drag in this plan is to use a visible browser tab for the actual pointer interaction and use browser inspection to observe and capture evidence before and after the drag.

The current fake DOM used by chart interop tests lives in `/hyperopen/test/hyperopen/views/trading_chart/test_support/fake_dom.cljs`. It models elements and listeners but does not currently model text nodes as first-class DOM nodes.

## Plan of Work

### Milestone 1: Convert overlay text patching to persistent text nodes

Refactor `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` so overlay text-bearing spans and chips allocate text nodes once during row creation and update those text nodes in place. This milestone should remove the need to set `textContent` on container elements during normal overlay patching.

The change should cover all current overlay text surfaces:

- the PNL badge text;
- the PNL price-chip text;
- the liquidation label text if it remains split from price text;
- the liquidation price text;
- the liquidation drag-note text;
- the liquidation price-chip text.

If a helper is introduced, it should be scoped to persistent text nodes only, not a generic DOM abstraction layer. At the end of this milestone, subtree child-list churn from text-only updates should be eliminated for the retained overlay subtree.

### Milestone 2: Extend fake DOM and regression coverage for text-node identity

Extend `/hyperopen/test/hyperopen/views/trading_chart/test_support/fake_dom.cljs` to model text nodes and allow tests to inspect their identity and value. Then update `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs` with focused assertions that:

- the same text nodes are reused across overlay updates;
- overlay text content still changes correctly when PNL or liquidation values change;
- the existing retained row/chip identity assertions still pass.

If needed, add a lightweight mutation-counter seam in fake DOM so tests can prove that text-only updates do not produce synthetic child removal/addition at the overlay subtree level.

At the end of this milestone, the micro-optimization is regression-protected without depending only on browser inspection.

### Milestone 3: Publish a visible-browser live drag QA artifact

Run a real browser QA pass on local `/trade` using the existing browser inspection tooling in attached or managed visible-browser mode. The QA recipe should be documented so another contributor can repeat it without guessing:

- use `http://localhost:8080/trade?spectate=0x162cc7c861ebd0c06b3d72319201150482518185`;
- ensure the active asset is `MON`;
- confirm both overlay rows are visible before interaction;
- perform a real pointer drag on the `Liq. Price` handle in the visible browser;
- observe that preview text changes during drag and releasing the drag opens the existing `Edit Margin` modal with chart-derived prefill.

Publish the evidence in a new `/hyperopen/docs/qa/` artifact that records:

- the exact route, address, and active asset used;
- the observed before/after overlay state;
- whether the modal opened and showed the expected drag-derived context;
- any screenshot or browser-inspection artifact paths.

At the end of this milestone, the residual browser-validation gap is closed with a repeatable, documented artifact.

### Milestone 4: Run full validation and update artifacts

After the code and QA documentation changes are in place, run the required repository validation gates:

- `npm run check`
- `npm test`
- `npm run test:websocket`

If browser inspection tooling code changes are introduced during implementation, also run `npm run test:browser-inspection`.

Update this ExecPlan with results, then create or close the corresponding `bd` issue once the local tracker infrastructure is available.

## Concrete Steps

From repository root `/hyperopen`:

1. Refactor overlay text ownership in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs`.
   Replace element-level text assignment helpers with persistent text-node helpers and store those node references in the cached row-node maps.

2. Extend fake DOM support in `/hyperopen/test/hyperopen/views/trading_chart/test_support/fake_dom.cljs`.
   Add `createTextNode` semantics, parent ownership, and any minimal helpers needed to inspect text-node identity in tests.

3. Add regression tests in `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs`.
   Keep the existing retained-row assertions and add new identity assertions for text nodes under repeated overlay updates.

4. Run the live browser QA workflow on local `/trade`.
   Use visible Chrome via `/hyperopen/tools/browser-inspection/`, spectate address `0x162cc7c861ebd0c06b3d72319201150482518185`, and asset `MON`. Perform a real drag on `Liq. Price` and capture evidence under `/hyperopen/docs/qa/`.

5. Run validation commands:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
   - optionally `npm run test:browser-inspection` if browser tooling changes

Expected evidence:

- text-node identity stays stable across overlay updates;
- visible-browser drag opens the expected margin modal flow;
- required validation gates pass.

## Validation and Acceptance

Acceptance is satisfied when all conditions below are true:

1. `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` no longer uses element `textContent` assignment as the steady-state patch mechanism for retained overlay text surfaces.
2. Focused tests prove persistent text-node identity across overlay updates and preserve existing retained row/chip identity expectations.
3. A visible-browser QA run on local `/trade` demonstrates a real `Liq. Price` drag interaction end to end, including preview-state changes and margin modal open on release.
4. The browser QA evidence is published under `/hyperopen/docs/qa/` with enough detail for another contributor to reproduce it.
5. `npm run check`, `npm test`, and `npm run test:websocket` pass.
6. If browser inspection code changes were required, `npm run test:browser-inspection` also passes.

## Idempotence and Recovery

This work is source-controlled and safe to repeat. Re-running the refactor should leave the same overlay ownership model and the same QA artifact structure.

If the text-node refactor introduces a rendering regression, recovery is to revert only the text-node ownership changes while leaving the retained row-element architecture in place. If the manual browser QA recipe proves flaky for the chosen address or asset, recovery is to keep the code refactor and update the QA artifact with a better proven address/asset pair rather than broadening scope into a new browser automation subsystem.

No destructive data migration is involved.

## Artifacts and Notes

Primary implementation files expected to change:

- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs`
- `/hyperopen/test/hyperopen/views/trading_chart/test_support/fake_dom.cljs`
- `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs`

Expected QA artifact area:

- `/hyperopen/docs/qa/`
- `/hyperopen/tmp/browser-inspection/`

Relevant prior plan:

- `/hyperopen/docs/exec-plans/completed/2026-03-05-position-overlay-incremental-dom-patching.md`

Operational blocker at planning time:

- `bd` commands were unavailable because the local Dolt server on `127.0.0.1:13881` was not accepting connections. This should be retried when tracker infrastructure is healthy.

## Interfaces and Dependencies

This plan should preserve the public overlay entry points:

- `hyperopen.views.trading-chart.utils.chart-interop.position-overlays/sync-position-overlays!`
- `hyperopen.views.trading-chart.utils.chart-interop.position-overlays/clear-position-overlays!`

The browser inspection subsystem should remain read-only by default. This plan does not require adding generic mutating browser commands. The QA workflow should instead rely on:

- existing visible-browser session support from `/hyperopen/tools/browser-inspection/src/cli.mjs`;
- read-only observation and snapshot capture from `/hyperopen/tools/browser-inspection/src/service.mjs`;
- a human or agent-controlled visible pointer drag in the browser window.

At the DOM layer, the final overlay row caches should still own:

- the retained row element;
- child element references for line, badge, chip, handle, and hit area as applicable;
- persistent text-node references for each text-bearing surface.

Revision note: 2026-03-06. Created this follow-up ExecPlan after completing live browser QA on the retained-row refactor and identifying the two remaining gaps: no published end-to-end drag artifact and residual subtree text-node churn from element `textContent` patching.
