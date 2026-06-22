---
owner: trading-ui
status: canonical
last_reviewed: 2026-06-22
review_cycle_days: 90
source_of_truth: true
---

# Frontend Runtime and Interaction Policy

## UI Guidance (UI Tasks Only)
This guidance applies only when tasks touch UI-facing code such as `/hyperopen/src/hyperopen/views/**`, `/hyperopen/src/styles/**`, or interaction flows (selectors/modals/dropdowns/forms/tables).

Use this document first, then apply:
- `/hyperopen/docs/agent-guides/browser-qa.md`
- `/hyperopen/docs/agent-guides/ui-foundations.md`
- `/hyperopen/docs/agent-guides/trading-ui-policy.md`
If guidance conflicts, this document wins for UI runtime behavior and invariant ownership.

## UI QA Contract (MUST)
- MUST use Playwright for committed deterministic browser regression coverage when a stable UI or interaction path should be asserted, repeated, reviewed, or run in CI.
- MUST run the design-system browser QA flow from `/hyperopen/docs/agent-guides/browser-qa.md` for UI-facing changes before signoff.
- MUST keep Browser MCP design-review and exploratory browser QA available; it does not replace committed Playwright regression coverage.
- MUST account for all six browser-QA passes: visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf.
- MUST account for all four design-review widths: `375`, `768`, `1280`, and `1440`.
- MUST report browser-QA results as `PASS`, `FAIL`, or `BLOCKED` with evidence-backed issues and residual blind spots.
- MUST NOT conclude “looks good” or equivalent unless every required pass is explicitly accounted for.

## UI Interaction Runtime Rules (MUST)
- MUST prefer anchored popovers, dropdowns, inline disclosure, or responsive sheets over full-screen modals for page-local, recoverable controls. Use modals only when a workflow requires focus isolation, route-level interruption, destructive confirmation, provider or wallet interaction, or multi-step state that would be risky to leave half-complete.
- MUST apply user-visible UI state transitions first in an action pipeline (example: close dropdown immediately before unsubscribe/subscribe/fetch effects).
- MUST batch related UI state writes caused by one interaction into a single state projection effect when feasible.
- MUST keep effect-order authority centralized in runtime validation contract enforcement:
  `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs` via `/hyperopen/src/hyperopen/runtime/validation.cljs`.
- MUST add an action policy entry in the centralized contract before introducing a new interaction-critical action that emits heavy I/O effects.
- MUST batch logically related writes to the same atom/store into a single `swap!` (or equivalent single transition) when intermediate states are not intentionally observable.
- MUST NOT emit multiple sequential `swap!` calls for one logical UI/domain transition when a single atomic update can represent the same transition.
- If staged intermediate states are intentional, MUST document the reason and add ordering/regression tests that cover the staged behavior.
- MUST avoid duplicate side-effect issuance in one interaction flow (example: only one candle snapshot trigger per asset selection).
- MUST define a single owner per projection path in a flow (`:active-asset`, `:selected-asset`, `:active-market`) and avoid redundant writers unless explicitly documented and tested.
- MUST represent multi-token Replicant `:class` values as collections (for example `["opacity-0" "scale-y-95"]`) and MUST NOT use space-separated class strings in `:class`.
- MUST represent Hiccup `:style` map keys as keywords (including CSS custom properties, for example `:--order-size-slider-progress`) and MUST NOT use string keys.
- MUST keep interactive accessories inside text-input shells borderless and visually integrated (for example TP/SL unit controls inside Gain/Loss rows), and MUST NOT render a second nested bordered control inside the same input shell.
- MUST suppress browser-default blue focus styles for accessory controls and use project-neutral focus classes (`focus:outline-none`, `focus:ring-0`, `focus:ring-offset-0`) unless an accessibility exception is explicitly documented and tested.
- MUST render namespaced instrument identifiers in UI tables as base symbol text plus a prefix/type chip (for example `xyz:NVDA` -> `NVDA` + `xyz` chip), and MUST NOT render the raw concatenated identifier as the primary display label.
- MUST render quantity/size symbol suffixes using the base symbol only (for example `0.500 NVDA`), and MUST NOT include namespace/type prefixes in size strings.

## Canonical UI Rules
- Apply user-visible state transitions before subscription/fetch side effects.
- Runtime validation effect-order contract is the single authority for covered interaction actions.
- Keep one owner per projection path (`:active-asset`, `:selected-asset`, `:active-market`).
- Avoid duplicate side-effect issuance in a single interaction flow.
- Represent multi-token classes as collections in Hiccup attrs.
- Keep Hiccup style keys as keywords, including CSS custom properties.
- Render namespaced instruments as base symbol plus prefix/type chip.
- Render size/quantity suffixes with base symbol only.

## Interaction Regression Scenarios (MUST)
- Asset select emits immediate close/projection update before unsubscribe/subscribe effects.
- Asset select emits no duplicate fetch/subscription effects.
- Active asset bar still renders symbol if `:active-market` is partial.
- Transition from one active asset to another preserves visible symbol and closes selector instantly.
- Funding/order/trade rows display namespaced coins as base symbol + chip, and size fields never include namespace/type prefixes.

## Interaction Assumptions and Defaults
- Assume existing Nexus/Replicant synchronous dispatch model remains unchanged.
- Assume `:active-asset` is canonical identity and `:active-market` is a denormalized projection.
- Default policy style is strict `MUST` / `DO NOT` guidance for interaction flow constraints.

## Companion Guides
- Browser QA contract: `/hyperopen/docs/agent-guides/browser-qa.md`
- UI foundations: `/hyperopen/docs/agent-guides/ui-foundations.md`
- Trading UI policy: `/hyperopen/docs/agent-guides/trading-ui-policy.md`

## Required Interaction Regressions
- Asset select closes selector and updates projection before heavy effects.
- Asset select emits no duplicate fetch/subscription effects.
- Active asset UI renders fallback symbol when projection is partial.
- Transition A -> B preserves visible symbol and instant close behavior.
