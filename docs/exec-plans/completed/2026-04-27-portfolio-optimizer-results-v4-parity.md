---
owner: frontend
status: completed
created: 2026-04-27
completed: 2026-05-13
source_of_truth: false
local_scratch_refs:
  - bd: hyperopen-4pr0
    authoritative: false
---

# Portfolio Optimizer Results V4 Parity

This completed ExecPlan records historical implementation context for the Results V4 parity pass. New work should use a new active ExecPlan or a public issue instead of reopening this file as an implementation backlog.

## Context References

Public refs:
- Direct maintainer request captured in this ExecPlan. No GitHub issue or PR is linked in this checkout.

Repo artifacts:
- Parent implementation context: `/hyperopen/docs/exec-plans/completed/2026-04-26-portfolio-optimizer-v4-alignment.md`.

Local scratch refs (non-authoritative):
- Beads / `bd`: `hyperopen-4pr0` ("Align optimizer results page to v4 parity"), authoritative: false.

## Objective
Bring `/portfolio/optimize/:scenario-id?otab=recommendation` materially closer to the v4 Results artboard while preserving the existing optimizer run result, diagnostics, frontier, signed exposure, funding decomposition, and scenario persistence behavior.

This is a layout and visual-system alignment pass. It does not change solver math, worker orchestration, scenario persistence, or rebalance execution semantics.

## Evidence
- Designer source: `/Users/barry/Downloads/hyperopen portfolio optimizer/v4.jsx`, `ResultsV4`.
- Designer visual spec: `/var/folders/dg/3nkyzrp12fn141vv7f6rc9v40000gn/T/TemporaryItems/NSIRD_screencaptureui_86HYE5/Screenshot 2026-04-27 at 8.34.48 PM.png`.
- Current source: `src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs`, `results_panel.cljs`, `target_exposure_table.cljs`, `frontier_chart.cljs`, and `src/styles/main.css`.

## Mismatch Inventory
- Scenario detail order is wrong: current order is header, KPI cards, stale banner, provenance card, pill tabs, body. V4 order is header, provenance strip, flat tabs, KPI strip, body.
- Provenance is too card-like: current implementation uses a rounded panel titled "Provenance"; V4 uses a quiet horizontal audit strip with small fields and right-aligned data-as-of/link affordance.
- Tabs are too heavy: current implementation uses pill buttons inside a rounded container; V4 uses a flat 32px tab bar with a gold underline.
- KPIs are too card-like: current implementation uses five rounded cards; V4 uses one continuous strip with vertical dividers and compact numeric hierarchy.
- Results body breakpoint is wrong: current recommendation body waits until `2xl` for three columns; V4 is a 1440px desktop three-rail layout.
- Recommendation body duplicates top metadata: current body renders assumptions and performance cards already represented by provenance and KPI strips.
- Allocation table is too card-like and too vertically expanded: V4 uses a dense table rail with by-asset/by-leg controls and compact numeric cells.
- Frontier is not enough of a center-stage hero: V4 gives the efficient frontier the center column with larger chart framing, legend overlay, and reading guidance.
- Diagnostics are split across multiple cards: V4 uses one continuous right trust rail with conditioning, diversification, and weight-stability rows plus collapsed secondary diagnostics.

## Implementation Plan
1. Reorder scenario detail composition so provenance and tabs precede the KPI strip, matching the v4 Results hierarchy.
2. Restyle provenance, tabs, and KPI strip through component markup plus targeted `.portfolio-optimizer-v4` CSS instead of changing global app chrome.
3. Convert recommendation body to a three-rail results grid at desktop widths: allocation left, frontier center, trust diagnostics right.
4. Remove duplicated assumptions/performance cards from the recommendation body while preserving data roles that tests rely on where practical.
5. Retheme the target exposure table into a dense allocation rail with a compact header and by-asset/by-leg toggles.
6. Promote the frontier chart into the center hero and add a current-position marker plus v4-style overlay legend.
7. Merge trust/caution, warnings, and summary diagnostics into one v4-style diagnostics rail while preserving underlying diagnostics content.
8. Update focused unit tests where they asserted old card copy rather than product behavior.
9. Run focused optimizer view tests first, then required repo gates.

## Progress
- [x] (2026-04-28 00:55Z) Created tracked issue `hyperopen-4pr0` for this focused results parity pass.
- [x] (2026-04-28 00:55Z) Audited the current recommendation surface against `ResultsV4` in the designer JSX and captured mismatches in this plan.
- [x] (2026-04-28 01:05Z) Implemented the first results parity slice: provenance/tabs/KPI order, three-rail recommendation body, allocation table restyle, frontier hero treatment, and compact diagnostics rail.
- [x] (2026-05-13 13:04Z) Closed this plan as completed historical implementation context. Any future design-review findings should be tracked in a new issue, PR review, or new active ExecPlan if they require implementation.

## Surprises & Discoveries
- The largest remaining results mismatch was composition: the old surface duplicated assumptions/performance inside the body instead of letting provenance and KPI strips own those facts.
- The existing result payload lacked current-portfolio return/volatility metrics, so the engine now emits `:current-expected-return`, `:current-volatility`, and `:current-performance` alongside target metrics for results-page comparison UI.
- The remaining unchecked item was design-review status rather than implementation work, so keeping the plan under `active/` made the active implementation set misleading.

## Decision Log
- Decision: Preserve existing optimizer result contracts and add current metrics without changing solver selection.
  Rationale: The v4 results page needs current-vs-target comparison, but solver behavior should remain outside this visual alignment pass.
  Date/Author: 2026-04-28 / Codex
- Decision: Keep funding decomposition visible below allocation rather than remove it entirely.
  Rationale: V1 product requirements make funding auditable, while the v4 artboard prioritizes allocation/frontier/trust above the fold.
  Date/Author: 2026-04-28 / Codex
- Decision: Move this ExecPlan from `active/` to `completed/`.
  Rationale: Hyperopen planning rules define `active` as work being executed now. The implementation recorded here was already complete; future design-review feedback should start from a new durable work item instead of keeping historical implementation context active indefinitely.
  Date/Author: 2026-05-13 / Codex

## Acceptance Criteria
- Scenario detail renders `header -> provenance strip -> tabs -> KPI strip -> body` for recommendation scenarios.
- Provenance, tabs, and KPIs are flat strips, not rounded cards.
- At the designer desktop width, the recommendation tab uses three columns without requiring `2xl`.
- Recommendation body begins with allocation, frontier, and trust rail, not assumptions/performance cards.
- Allocation remains signed-exposure aware and preserves existing `data-role` hooks for rows/groups.
- Frontier remains interactive and preserves click/drag target actions.
- Right rail communicates conditioning, diversification, and weight stability as first-class trust signals.
- Existing solver/run/persistence/rebalance contracts remain unchanged.

## Validation
- Focused CLJS optimizer tests covering scenario detail and results panel.
- Existing Playwright portfolio optimizer regression for recommendation output.
- Required gates after code changes: `npm run check`, `npm test`, `npm run test:websocket`.
- Browser cleanup after any browser-driven validation.

## Outcomes & Retrospective
The Results V4 parity implementation is closed as historical context. It delivered the first results-route parity slice: the recommendation surface moved toward the v4 hierarchy with provenance/tabs/KPI order, a three-rail body, allocation table restyling, a frontier hero treatment, and a compact diagnostics rail while preserving solver, persistence, and rebalance semantics.

Any future product or design review can reference this file for implementation history, but follow-up work should be captured in a new active ExecPlan or public work item. Complexity increased modestly in the UI layer because the route gained more explicit v4 composition, but future complexity is reduced by not leaving stale implementation plans active.

Revision note (2026-05-13 / Codex): Moved from `active/` to `completed/` during optimizer source-of-truth documentation cleanup. The move corrects plan lifecycle state only; it does not change optimizer runtime behavior.
