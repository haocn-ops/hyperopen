# Optimizer execution: use the live native mark as the cost/sizing reference (fix "Spread crossing 0 bp")

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. Maintained per `docs/PLANS.md`.

## Purpose / Big Picture

On the optimizer rebalance/execution page, a trader saw the BABA line item report "Spread crossing 0 bp" while its live order book clearly had a spread, with a "Cost basis · untrusted-snapshot-fill" tag. After this change, a HIP-3 perp whose history is modelled off an equity proxy (for example `xyz:BABA`, whose returns come from the Tiingo Alibaba ADR series) is sized and costed against its **live native perp mark** rather than the proxy's last daily close. The spread-crossing component then reflects the real book, the order quantity matches the intended dollar notional, and the misleading "untrusted snapshot" fallback stops firing for these assets. As a second, defensive change, when the cost model genuinely cannot separate spread from impact (a stale or missing book), the per-row breakdown shows the spread as unknown ("—") with an honest note instead of a deceptive "0 bp".

How to see it working: open the optimizer, run a solve whose result trades a proxy-priced HIP-3 perp (e.g. `xyz:BABA`), open the Execution tab, expand that row. Before this change the breakdown read "Spread crossing 0 bp · Book impact 25 bp" with "Cost basis · untrusted-snapshot-fill". After, it reads a real spread + impact split, and the order quantity reflects the native ~$95 price rather than the ~$112 proxy.

## Context References

Public refs:
- Direct maintainer request (this session): "for the baba line item it shows the cost of crossing the spread as zero, but … there's clearly a spread." Diagnosed live via the shadow-cljs nREPL (port in `.shadow-cljs/nrepl.port`, store atom `hyperopen.system/store`).

Repo artifacts:
- Parent ExecPlan: `docs/exec-plans/active/2026-06-27-execution-cost-transparency.md` (introduced the spread/impact split and the `cost-split`/`side-touch-price` domain helpers; its Decision Log entry "spread/impact are nil … when there is no real book to split" is the behavior the secondary fix refines).
- Cost model: `src/hyperopen/portfolio/optimizer/domain/rebalance.cljs` (`cost-context`, `cost-estimate`, `implausible-favorable-fill?`, `untrusted-fill-cost`, `cost-split`, `rebalance-row`).

Local scratch refs (non-authoritative): none.

## Root Cause (diagnosed, with live evidence)

The per-row execution price (`:prices-by-id` in `application/rebalance_preview.cljs`, merge order `latest-history-prices-by-id` → `current-prices-by-id` → `[:execution-assumptions :prices-by-id]`) defaults to the **last point of the asset's history price series**. For a HIP-3 perp priced off an equity proxy, that series is the proxy equity (`source-id "tiingo:BABA"`), so the reference is the ADR's level (~112), not the native perp mark (~95).

`rebalance-row` (`domain/rebalance.cljs:368`) uses that price for BOTH the order quantity (`quantity = |notional| / price`, line 375) and the cost reference (passed to `cost-estimate`, line 395). `delta-notional-usd = capital × delta-weight` is price-independent, so the SOLVE's dollar targets are unaffected; only quantity and cost are corrupted.

When the reference price diverges from the live book by more than the 50 bp trust band (`max-favorable-fill-deviation` 0.005), a marketable buy filling at the (correct) book looks "implausibly favorable", so `implausible-favorable-fill?` → `untrusted-fill-cost` substitutes a flat 25 bp fallback **with no `:touch-price`**; `cost-split` then returns nil and `cost-estimate` omits `:spread-bps`. The view renders spread = 0 and attributes the whole 25 bp to impact.

Live control set (spectate session, native marks from `https://api.hyperliquid.xyz/info allMids dex=xyz` and the in-app catalog `[:asset-selector :market-by-key]`):

    instrument     optimizer ref px   native mid   divergence   guard?   spread shown
    xyz:AAPL       280.86             280.97       -0.04%        no       0.36 bp (real)
    xyz:SILVER     58.952             58.95        ~0%           no       1.02 bp (real)
    xyz:TSM        441.4              431.6        +2.27%        yes      0 bp
    xyz:BABA       112.55             94.98        +18.5%        yes      0 bp

Spread collapses to 0 exactly when |ref − native| > 50 bp. AAPL/SILVER price off proxies that happen to equal their native marks, so they decompose normally — proving the pipeline is correct and only the reference price is wrong.

## Scope

- `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` — add `native-marks-by-id` (resolve a coin→native-mark map from `[:asset-selector :market-by-key]`, key the result by the universe instrument-id) and `with-native-marks` (merge those into `[:execution-assumptions :prices-by-id]`, existing explicit prices win). Compose it at the `build-request` call site (line 473) alongside `with-cost-contexts`.
- `src/hyperopen/views/portfolio/optimize/execution_tab.cljs` — `cost-breakdown` emits `:splittable?` and returns nil (not 0) spread/impact when un-splittable; `cost-breakdown-strip` renders a third branch for an un-splittable crossing row: a "Spread + impact … not separable" note (reusing the existing `.optimizer-exec-cost-rests` markup) instead of a "0 bp" spread stat. `cost-stat` already renders "—" for non-finite values.
- Tests: `test/.../application/setup_readiness_test.cljs` (native-mark injection) and `test/.../views/portfolio/optimize/execution_tab_test.cljs` (un-splittable row renders the note, not "Spread crossing").

Out of scope (recorded for follow-up): the doubled dex prefix on some instrument-ids (`perp:xyz:xyz:SILVER` vs `perp:xyz:BABA`) — handled here by keying native marks via `:coin`, but the id inconsistency itself is a separate defect; whether the SOLVE should also value held proxy-priced positions at native marks (the solve does not consume `:prices-by-id` today).

## Why this is safe (solve is untouched)

The only consumers of `:prices-by-id` are `rebalance/build-rebalance-preview` at `application/rebalance_preview.cljs:168` (client) and `application/engine/payload.cljs:237` (worker-side preview). The OSQP/closed-form solver never reads it (verified by grep across `portfolio/optimizer/**`). So injecting native marks changes the rebalance preview (quantity + cost) only, not the optimization weights. The staged plan inherits it: `application/rebalance_snapshot.cljs` `last-run-with-snapshot-contexts` reuses the stored request (with the injected `:prices-by-id`) and only overwrites `:cost-contexts-by-id`.

## Progress

- [x] (2026-06-27) Diagnosis + live verification (control set above); native-mark source identified at `[:asset-selector :market-by-key <id> :markRaw]`, coin-keyed resolution proven for all plan perps.
- [x] (2026-06-27) RED: `setup_readiness_test` asserts `[:request :execution-assumptions :prices-by-id "perp:BTC"]` = native mark (95) over proxy history close (112); `execution_tab_test` asserts an un-splittable crossing row renders the "not separable" note and not "Spread crossing". Both failed as expected (3 assertion failures) before the fix.
- [x] (2026-06-27) GREEN: implemented `native-mark-price`/`native-marks-by-id`/`with-native-marks` in `setup_readiness.cljs`, composed at the `build-request` call site; `cost-breakdown` now emits `:splittable?` with nil spread/impact when un-splittable and `cost-breakdown-strip` renders the honest note branch.
- [x] (2026-06-27) `npm run gates` 33/33 PASS (4858 tests / 26798 assertions / 0 failures, + 546 websocket tests). Bumped three namespace-size exceptions (setup_readiness.cljs 519→560, execution_tab.cljs 975→990, setup_readiness_test.cljs 577→602).
- [x] (2026-06-27) Live re-verification (nREPL against the running dev server): `build-readiness` injects native execution prices (BABA 95, TSM 432.87, SILVER 58.952 via the doubled-prefix `perp:xyz:xyz:SILVER` → coin `xyz:SILVER`); end-to-end with the live BABA book, native ref (95) → `:source :snapshot, :spread-bps 4.21` (real spread), proxy ref (112.55) → `:source :untrusted-snapshot-fill` (the old bug).
- [x] (2026-06-27) Adversarial review found two real issues, both fixed and re-gated 33/33 (4859 tests): (P0) `:prices-by-id` was NOT stripped from the input-signature, so live marks would churn it → false staleness + re-solve storm — fixed in `contracts/signatures.cljs` (`stable-execution-assumptions` now also dissocs `:prices-by-id`) with a regression test; (P1) coin-keyed resolution could collide a spot and perp sharing a base token — fixed by resolving each instrument via its own market-type-qualified catalog key (instrument-id, then `<market-type>:<coin>`), with a same-coin decoy test.
- [ ] User review / land via finish-feature-branch.

## Surprises & Discoveries

- Observation: the cleanest native-mark source is `[:asset-selector :market-by-key]`, keyed by optimizer instrument-id with a `:coin` field and a precise `:markRaw` string (`:mark` is display-rounded). Resolving by `:coin` (not instrument-id) is required because some HIP-3 ids are doubled (`perp:xyz:xyz:SILVER` while the catalog key is `perp:xyz:SILVER`); the `:coin` (`xyz:SILVER`) matches cleanly.
  Evidence: live `[:asset-selector :market-by-key]` → BABA `{:markRaw "95.0" :coin "xyz:BABA"}`; coin-map resolved all plan perps (BABA 95.0, TSM 432.57, AAPL 280.86, SILVER 58.952, ZETA/EIGEN/SOPH/BTC), only spot @-tokens missed (they carry no cost).
- Observation: the investigation sub-agent claimed `:prices-by-id` "affects the solve output"; grep proved it is consumed only by `build-rebalance-preview`. Verifying avoided an unnecessary input-signature change.
- Observation (adversarial review): although the solver ignores `:prices-by-id`, the field WAS still part of the `optimizer-input-signature` (only `:cost-contexts-by-id` was stripped). Seeding live, websocket-ticking marks into it would have churned the signature every tick → false staleness via `run-identity` + a re-solve storm via `auto-recompute-stale-portfolio-optimizer-scenario` (the same failure class `actions_test`'s "requested at most once" guards). Fixed by stripping `:prices-by-id` from the signature too.
  Evidence: `contracts/signatures.cljs` `stable-execution-assumptions`; new `execution-prices-by-id-excluded-from-input-signature-test`.
- Observation (adversarial review): the global market catalog keys spot and perp of the same base token with the same `:coin` (e.g. spot + perp HYPE), so a bare-coin→price map collapses them and mis-assigns one's price to the other. Fixed by resolving each instrument via its own market-type-qualified catalog key instead of a shared coin map. Live probe confirmed the per-instrument lookup (direct id, with `<market-type>:<coin>` fallback) resolves every plan instrument including the doubled-prefix `perp:xyz:xyz:SILVER`.

## Decision Log

- Decision: source native marks from `[:asset-selector :market-by-key]` and resolve by `:coin`, injecting into `[:execution-assumptions :prices-by-id]` at request build (mirroring `with-cost-contexts`).
  Rationale: it is the global market catalog already loaded for the app, keyed compatibly, and `[:execution-assumptions :prices-by-id]` is the highest-precedence price slot that already round-trips through the codec/signature and into both the client and worker preview, and survives the snapshot refresh. Existing explicit prices keep priority (`merge marks existing`).
  Date/Author: 2026-06-27 / Geronimo (via agent).
- Decision: keep the change preview-only; do not alter the solver or strip `:prices-by-id` from the input-signature.
  Rationale: the solver does not consume `:prices-by-id`; busting the preview cache on price change is desirable, and the dollar targets (`capital × weight`) are price-independent so weights are unaffected.
  Date/Author: 2026-06-27 / Geronimo (via agent).
- Decision (secondary): when a crossing row cannot be split, render the spread/impact terms as a single honest "not separable" note rather than "Spread crossing 0 bp / Book impact = full".
  Rationale: refines the parent plan's "show the total as a flat estimate" decision — a literal "0 bp" reads as "no spread cost", which is exactly what confused the maintainer. The cost-basis source line still names the reason (e.g. untrusted-snapshot-fill).
  Date/Author: 2026-06-27 / Geronimo (via agent).
- Decision (review fix P0): strip `:prices-by-id` from `optimizer-input-signature` alongside `:cost-contexts-by-id`.
  Rationale: it is preview-only (solver never reads it); leaving live marks in the signature churns it on every websocket tick and triggers false staleness / re-solve storms.
  Date/Author: 2026-06-27 / Geronimo (via agent).
- Decision (review fix P1): resolve native marks per instrument by its own market-type-qualified catalog key (instrument-id, then `<market-type>:<coin>`), not via a shared coin→price map.
  Rationale: `:coin` is not unique across market types (a spot and perp can share a base token); a coin map would collide and mis-price one as the other. Per-key resolution also handles doubled-prefix HIP-3 ids via the `<market-type>:<coin>` fallback.
  Date/Author: 2026-06-27 / Geronimo (via agent).

## Outcomes & Retrospective

Implemented on branch `feature/sweet-newton-a2029f` (worktree), gates 33/33, live-verified; uncommitted pending user review. Files:
- `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` — `native-mark-price`, `native-marks-by-id` (coin-keyed catalog resolution), `with-native-marks` (seeds `[:execution-assumptions :prices-by-id]`), composed at the `build-request` call site.
- `src/hyperopen/views/portfolio/optimize/execution_tab.cljs` — `cost-breakdown` emits `:splittable?` and nil (not 0) spread/impact when un-splittable; `cost-breakdown-strip` renders a "Spread + impact · Not separable — flat estimate" note for un-splittable crossing rows instead of "Spread crossing 0 bp".
- `src/hyperopen/portfolio/optimizer/contracts/signatures.cljs` — strip `:prices-by-id` from the input-signature (review fix P0).
- Tests: `setup_readiness_test` (native-mark injection over proxy close + same-coin spot/perp decoy), `execution_tab_test` (un-splittable row note), `contracts_test` (`:prices-by-id` excluded from input-signature). `dev/namespace_size_exceptions.edn` budgets bumped.

Behavior: a proxy-priced HIP-3 perp (e.g. `xyz:BABA`) is now sized and costed against its live native mark (~95) rather than the equity-proxy daily close (~112). The favorable-fill guard stops misfiring for these assets, so the row shows a real spread crossing (~4 bp for BABA) and the executed quantity matches the intended dollar notional. When a book genuinely can't be split, the breakdown says so honestly rather than printing a deceptive 0 bp.

Complexity: small net increase (one focused resolver + one render branch). No new namespace, no boundary violation (reads the `[:asset-selector :market-by-key]` state path, no view import), solver untouched.

Follow-ups (out of scope, recorded): the doubled dex prefix on some instrument-ids (`perp:xyz:xyz:SILVER`) is a separate id-construction defect (worked around here by resolving via `:coin`); whether the SOLVE should value held proxy-priced positions at native marks (it does not consume `:prices-by-id` today).

## Validation & Acceptance

Required gates (per `AGENTS.md`): `npm run gates` (runs `npm run check`, `npm test`, `npm run test:websocket`).

Acceptance:
- [ ] `build-readiness` injects native marks into `[:request :execution-assumptions :prices-by-id]` keyed by instrument-id; a proxy-priced asset's execution price equals the native mark, not the history close.
- [ ] An expanded crossing row whose cost has no spread split renders a "not separable" note (no "Spread crossing" stat, no "0 bp" spread); a normal crossing row still shows the real spread + impact.
- [ ] `npm run gates` PASS; live re-verification shows BABA reference ≈ 95 with a real spread and no `untrusted-snapshot-fill` tag.
