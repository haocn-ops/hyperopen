# Optimizer Save Scenario Modal And Route

This ExecPlan is a living document. Keep `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` current while the work proceeds.

## Purpose / Big Picture

Saving a computed optimizer draft should ask the user for a scenario name, persist that name in IndexedDB, and leave the user on the saved scenario route. The current behavior can save under a generated durable id while the browser remains on `/portfolio/optimize/draft`; the detail view then treats the route as mismatched and hides the saved run behind a read-only loading state.

After this change, clicking `Save scenario` opens a small name modal. Confirming `Save as` writes the named scenario through the existing `portfolio-optimizer` IndexedDB persistence layer and replaces the draft route with `/portfolio/optimize/<generated-id>` after persistence succeeds.

## Context Reference

Direct user request on 2026-05-23: implement optimizer scenario saving through IndexedDB, make saved scenarios retrievable from the main optimizer screen, and address the failing gates for that work.

## Progress

- [x] (2026-05-23 12:52Z) Reproduced the root cause from code: save success updates active state to `scn_*`, but the route remains `/portfolio/optimize/draft`, causing `scenario-detail-model` to scope away the saved result as a mismatched loading route.
- [x] (2026-05-23 12:52Z) Confirmed the save flow has no name-entry action or modal state; records currently derive the name from the draft and default to `Untitled Optimization`.
- [ ] Add RED tests for opening the save modal, confirming with a supplied name, persisting the supplied name, and replacing the draft route with the saved route.
- [ ] Implement modal state/actions/view and pass the supplied name into the existing save effect.
- [ ] Navigate to the durable saved scenario route after persistence succeeds.
- [ ] Add/update deterministic Playwright coverage for the modal save path.
- [ ] Run focused tests and required gates.
- [ ] Move this ExecPlan to `docs/exec-plans/completed/` after acceptance passes.

## Surprises & Discoveries

- Observation: `/portfolio/optimize/draft` is parsed as `:optimize-scenario` with scenario id `draft`, so route-follow-up effects can attempt an IndexedDB scenario load for a reserved unsaved id.
  Evidence: `hyperopen.portfolio.routes/parse-portfolio-route` treats any non-`new` suffix under `/portfolio/optimize/` as a scenario id.

- Observation: The stuck screen does not require IndexedDB to be broken.
  Evidence: `apply-scenario-save-success` can set `:active-scenario :loaded-id` to the generated id while the route remains `draft`; `scenario-detail-model` then reports `:loading? true` for the stale route.

## Decision Log

- Decision: Keep scenario IDs generated internally and make the user-supplied value only the scenario display name.
  Rationale: Durable ids are storage keys and route identifiers; names are mutable user-facing metadata.
  Date/Author: 2026-05-23 / Codex

- Decision: Replace the draft route after save rather than pushing a new browser history entry.
  Rationale: The draft route is a transient post-run location; back navigation should not return to the invalid loading state immediately after save.
  Date/Author: 2026-05-23 / Codex

## Validation and Acceptance

- `Save scenario` opens a name modal instead of directly saving.
- The modal has a scenario-name input, cancel action, and `Save as` confirm action.
- Confirm is blocked for a blank name and shows an inline error.
- Confirming with a nonblank name persists that name in the full scenario record, the saved draft config, and the address-scoped scenario index summary.
- After persistence succeeds from `/portfolio/optimize/draft` or `/portfolio/optimize/draft-current`, the app replaces the route with `/portfolio/optimize/<generated-id>`.
- The saved scenario can be opened from the optimizer index without showing the read-only loading state.

Required final commands:

- `npm run check`
- `npm test`
- `npm run test:websocket`

## Outcomes & Retrospective

Pending. Complete this section when the save-scenario modal and route persistence work is accepted and this plan moves to `docs/exec-plans/completed/`.
