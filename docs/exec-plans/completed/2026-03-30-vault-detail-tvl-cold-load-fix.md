# Fix Vault Detail TVL On Cold Loads

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The live `bd` issue for this work is `hyperopen-6w7x`.

## Purpose / Big Picture

Users can open an individual vault detail page directly, see the vault name and activity load, and still get a false `TVL` of `$0.00`. After this fix, cold visits to `/vaults/:vaultAddress` must load the summary metadata the detail hero depends on so the TVL settles to the real amount instead of zero. The behavior is visible by opening a vault detail route directly and confirming the hero metric shows the correct TVL without requiring a prior visit to `/vaults`.

## Progress

- [x] (2026-03-30 13:22Z) Reproduced the failure chain in code and confirmed the detail hero reads TVL from `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs`.
- [x] (2026-03-30 13:27Z) Confirmed `/hyperopen/src/hyperopen/api/endpoints/vaults.cljs` normalizes list and summary TVL but `vaultDetails` itself does not include a TVL field.
- [x] (2026-03-30 13:30Z) Confirmed `/hyperopen/src/hyperopen/vaults/application/route_loading.cljs` does not guarantee merged summary rows on ordinary detail-route loads, so the VM can legitimately render without a row fallback.
- [x] (2026-03-30 13:36Z) Created and claimed `hyperopen-6w7x` for this bug.
- [x] (2026-03-30 18:54 EDT) Confirmed the landed implementation now bootstraps missing vault index and summary metadata from `/hyperopen/src/hyperopen/vaults/application/route_loading.cljs` before cold detail-route fetches.
- [x] (2026-03-30 18:54 EDT) Confirmed focused regression coverage exists in `/hyperopen/test/hyperopen/vaults/application/route_loading_test.cljs` and the committed Playwright regression in `/hyperopen/tools/playwright/test/trade-regressions.spec.mjs`.
- [x] (2026-03-30 18:54 EDT) Audited the current branch for closeout, moved this plan to `completed/`, and prepared `hyperopen-6w7x` for closure. A local Playwright rerun was attempted during the audit but was blocked because `@playwright/test` is not installed in this worktree.

## Surprises & Discoveries

- Observation: the live `vaultDetails` response does not expose `tvl` for the affected vault.
  Evidence: a direct `POST https://api.hyperliquid.xyz/info` request for `OnlyShorts` on 2026-03-30 returned keys such as `apr`, `followers`, `maxDistributable`, and `portfolio`, but no `tvl`.

- Observation: the canonical live TVL still exists in the summary feed for the same vault.
  Evidence: the live `https://stats-data.hyperliquid.xyz/Mainnet/vaults` entry for `OnlyShorts` includes `"tvl": "719197.99053"`.

- Observation: ordinary detail-route loading no longer fetches list metadata by default.
  Evidence: `/hyperopen/src/hyperopen/vaults/application/route_loading.cljs` only adds list metadata fetches for detail routes when vault benchmark metadata is needed, not for the normal hero-metric path.

## Decision Log

- Decision: fix the data contract at route-loading time instead of trying to synthesize TVL from followers or unrelated detail payload fields.
  Rationale: the live API shows that summary metadata is the authoritative TVL source. Reconstructing TVL from followers would be approximate and brittle.
  Date/Author: 2026-03-30 / Codex

- Decision: fetch summary metadata, not the full vault index, when a cold detail route lacks the selected merged row.
  Rationale: the reported defect is the missing hero metadata path. A summary fetch is the smallest existing request that repopulates `:merged-index-rows` with TVL and APR for the target vault.
  Date/Author: 2026-03-30 / Codex

## Outcomes & Retrospective

The fix is already present on the current branch. Cold `/vaults/:vaultAddress` route loads now prepend the missing vault metadata bootstrap so the detail route fetch path can repopulate `:vaults :merged-index-rows` before the hero reads TVL. That keeps the existing `detail_vm` fallback intact and prevents the false `$0.00` state when `vaultDetails` omits `:tvl`.

The branch also already contains focused regression coverage for the route-loading contract plus a committed Playwright regression named `vault detail hero TVL bootstraps from list metadata when vaultDetails omits tvl @regression`. During the tracker audit on 2026-03-30, a local rerun of that browser test was blocked because this worktree does not have `@playwright/test` installed, but the implementation and committed regression coverage are both present, so `hyperopen-6w7x` is ready to close.

## Context and Orientation

The vault detail hero is rendered from `/hyperopen/src/hyperopen/views/vaults/detail/hero.cljs`, but the values it displays come from `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs`. That view model reads `:tvl` from either the normalized detail payload or the selected vault row in `:vaults :merged-index-rows`.

The detail payload is normalized in `/hyperopen/src/hyperopen/api/endpoints/vaults.cljs`. That namespace normalizes TVL for vault index and vault summary rows, but `normalize-vault-details` does not produce a `:tvl` key because the upstream `vaultDetails` payload does not provide one.

The route-level loading contract lives in `/hyperopen/src/hyperopen/vaults/application/route_loading.cljs`. Normal detail-route loads currently fetch `vaultDetails`, `webData2`, and optional activity or benchmark support data. They do not guarantee summary metadata for the selected vault unless the user is already in a benchmark flow that requests list metadata.

The merged summary row state is assembled in `/hyperopen/src/hyperopen/api/projections.cljs`. Fetching vault summaries updates `:vaults :recent-summaries` and merges those rows into `:vaults :merged-index-rows`, which is the same structure `detail_vm` already knows how to read.

## Plan of Work

First, update `/hyperopen/src/hyperopen/vaults/application/route_loading.cljs` so a detail route with no merged row for the selected vault schedules `[:effects/api-fetch-vault-summaries]` as support metadata. Keep the change narrow: do not widen this path to the full vault index unless another existing feature path already requires it, and do not duplicate the summaries effect when benchmark-related support effects already requested it.

Second, update the route-loading and action tests that encode detail-route effect ordering. The expected effect list for a cold detail load should now include the summary fetch before the detail API calls, while cases that already have a merged row should stay unchanged. The existing benchmark-metadata case must continue to avoid duplicate summary fetch effects.

Third, add a deterministic Playwright regression that opens a vault detail route with stubbed `vaultDetails`, `webData2`, and `vaultSummaries` responses. The test should prove the page can show the correct TVL from summary metadata even when the detail payload itself omits TVL.

## Concrete Steps

From `/Users/barry/.codex/worktrees/d6f9/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/vaults/application/route_loading.cljs` to add the missing detail-route summary metadata effect.
2. Update `/hyperopen/test/hyperopen/vaults/application/route_loading_test.cljs` and `/hyperopen/test/hyperopen/vaults/actions_test.cljs` to lock the new effect ordering and non-duplication behavior.
3. Update a committed Playwright suite under `/hyperopen/tools/playwright/test/**` with a cold vault-detail TVL regression.
4. Run the smallest relevant Playwright command for the new browser test before broader validation.
5. Run `npm run check`, `npm test`, and `npm run test:websocket`.

## Validation and Acceptance

Acceptance is satisfied when all of the following are true:

1. A cold `/vaults/:vaultAddress` route schedules a summary metadata fetch when the selected merged row is absent.
2. The route-loading and action tests prove that existing warm-row and benchmark-metadata paths still behave as intended.
3. A deterministic Playwright test stubs a `vaultDetails` response without `tvl`, stubs a `vaultSummaries` response with TVL, opens the vault detail route, and observes the correct TVL in the rendered hero.
4. The required repository gates pass:

   npx playwright test <targeted test>
   npm run check
   npm test
   npm run test:websocket

## Idempotence and Recovery

The patch is additive and safe to re-run. If the new summary-support effect causes duplicate fetches in a benchmark flow, restore the route-loading helper and re-run the focused route-loading tests before touching broader behavior. Do not synthesize TVL from follower rows as a fallback because that would replace a deterministic upstream source with derived data.

## Artifacts and Notes

Live evidence collected during diagnosis:

   Python request to https://stats-data.hyperliquid.xyz/Mainnet/vaults found:
     name: "OnlyShorts"
     vaultAddress: "0x61b1cf5c2d7c4bf6d5db14f36651b2242e7cba0a"
     tvl: "719197.99053"

   Python request to POST https://api.hyperliquid.xyz/info with:
     {"type":"vaultDetails","vaultAddress":"0x61b1cf5c2d7c4bf6d5db14f36651b2242e7cba0a"}

   returned detail keys without `tvl`, confirming the hero cannot rely on `vaultDetails` alone.

Plan revision note (2026-03-30): Created during active implementation after repository audit and live API verification established that cold vault-detail loads lack the summary metadata required for TVL.
Plan revision note: 2026-03-30 18:54 EDT - Audit closeout confirmed the cold-load TVL fix and committed regressions are already present on the current branch, moved this plan to `/completed/`, and prepared `hyperopen-6w7x` for closure. Local Playwright rerun remained blocked because `@playwright/test` is not installed in this worktree.
