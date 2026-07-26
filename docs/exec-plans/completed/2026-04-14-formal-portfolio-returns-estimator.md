# Formally Verify the Portfolio Returns Estimator with Lean and Simulator-Backed Conformance

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md`. The primary live `bd` issue for this work is `hyperopen-uxtv`.

## Purpose / Big Picture

Hyperopen’s portfolio returns chart currently derives cumulative returns from sampled `accountValueHistory` and `pnlHistory` data in `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs`. That estimator is pure and deterministic, but it is also inference-heavy: the underlying API does not give explicit external cash flows, so the code has to infer whether a jump in account value is trading performance or a deposit, withdrawal, or transfer. After this work, a contributor will be able to run a dedicated Lean-backed formal surface for that estimator, regenerate deterministic vector fixtures, and run ordinary repository tests that prove the production code still honors the proven invariants and still behaves safely on simulator-generated rebasing accounts like the one that motivated this investigation.

This is an internal correctness feature, not a UI redesign. The visible outcome is that a contributor can run one formal command for the returns estimator, see deterministic generated vectors for both theorem-backed kernel cases and simulator-backed cadence cases, and then run ordinary ClojureScript tests that prove the production estimator still avoids false wipeouts, still clamps real catastrophic losses correctly, and still stays acceptably close to latent simulator returns only inside the restricted regimes that we can honestly model. That reduces drift risk in a chart that users interpret as portfolio performance even though the upstream data is only partially identified.

## Progress

- [x] (2026-04-14 08:55 EDT) Re-read `/hyperopen/AGENTS.md`, `/hyperopen/docs/MULTI_AGENT.md`, `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md` before drafting this plan.
- [x] (2026-04-14 08:55 EDT) Audited the current returns estimator in `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs`, the direct regression anchors in `/hyperopen/test/hyperopen/portfolio/metrics/history_test.cljs`, the current formal-tooling wrapper under `/hyperopen/tools/formal/core.clj`, and the Lean workspace under `/hyperopen/spec/lean/`.
- [x] (2026-04-14 08:54 EDT) Created and claimed `bd` issue `hyperopen-uxtv` for the Lean-backed returns-estimator verification track.
- [x] (2026-04-14 08:56 EDT) Created this active ExecPlan and froze scope to the portfolio returns estimator, its deterministic helper pipeline, and a simulator-backed latent-versus-observed validation harness. General performance metrics, benchmark curves, and claims about real Hyperliquid economic truth are explicitly out of scope.
- [x] (2026-04-14 09:22 EDT) Added the new formal surface `portfolio-returns-estimator` across `/hyperopen/tools/formal/core.clj`, `/hyperopen/spec/lean/Hyperopen/Formal/Common.lean`, `/hyperopen/spec/lean/Hyperopen/Formal.lean`, `/hyperopen/dev/formal_tooling_test.clj`, `/hyperopen/tools/formal/README.md`, and `/hyperopen/docs/tools.md`, then synced `/hyperopen/tools/formal/generated/portfolio-returns-estimator.edn` and `/hyperopen/test/hyperopen/formal/portfolio_returns_estimator_vectors.cljs`.
- [x] (2026-04-14 09:22 EDT) Implemented `/hyperopen/spec/lean/Hyperopen/Formal/PortfolioReturnsEstimator.lean` with an exact rational model of the current returns kernel, concrete theorem checks for catastrophic loss clamp / positive rebase neutralization / no-flow coarse-sampling exactness, and deterministic corpora for series, interval, daily, and simulator cases.
- [x] (2026-04-14 09:31 EDT) Added the proof-surface contract bridge at `/hyperopen/src/hyperopen/schema/portfolio_returns_contracts.cljs`, committed CLJS conformance tests in `/hyperopen/test/hyperopen/portfolio/metrics/history_formal_conformance_test.cljs` and `/hyperopen/test/hyperopen/portfolio/metrics/history_simulator_test.cljs`, regenerated `/hyperopen/test/test_runner_generated.cljs`, and kept direct readable anchors in `/hyperopen/test/hyperopen/portfolio/metrics/history_test.cljs`.
- [x] (2026-04-14 09:44 EDT) Ran `npm run formal:sync -- --surface portfolio-returns-estimator`, `npm run formal:verify -- --surface portfolio-returns-estimator`, verified the existing formal surfaces (`vault-transfer`, `order-request-standard`, `order-request-advanced`, `effect-order-contract`, `trading-submit-policy`, `order-form-ownership`), then ran `npm run test:formal-tooling`, `npm test`, `npm run test:websocket`, and `npm run check` successfully.

## Surprises & Discoveries

- Observation: the repository already has the right formal-tooling shape for this work.
  Evidence: `/hyperopen/tools/formal/core.clj`, `/hyperopen/spec/lean/Hyperopen/Formal/Common.lean`, `/hyperopen/spec/lean/Hyperopen/Formal.lean`, `/hyperopen/tools/formal/generated/`, and `/hyperopen/test/hyperopen/formal/` already support modeled surfaces such as `effect-order-contract` and `order-form-ownership`, so the returns work should fit that existing pattern instead of inventing a second proof pipeline.

- Observation: the production returns estimator is already a pure seam with narrow dependencies.
  Evidence: `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs` computes aligned points, implied cash flow, Dietz-style period return, fallback return, bounded return, cumulative compounding, and daily regrouping without browser, network, or worker dependencies. That makes it a good formalization target.

- Observation: exact economic truth is not identifiable from `accountValueHistory` and `pnlHistory` alone.
  Evidence: the current implementation infers cash flow from `delta_account - delta_pnl`. The upstream portfolio API does not provide explicit cash-flow events in the same payload, so two different latent histories can produce the same observed sampled series.

- Observation: cadence robustness is not the same thing as correctness, and the plan must separate them.
  Evidence: when the latent process is sampled more coarsely than the underlying trading and cash-flow steps, the estimator can stay safe without being exact. The conversation that motivated this plan exposed the need to prevent false `-100%` wipeouts, but it did not justify a theorem that the estimator is numerically exact under arbitrary sampling schedules.

- Observation: the user-reported rebasing account shape is already a strong regression anchor.
  Evidence: the current direct test suite in `/hyperopen/test/hyperopen/portfolio/metrics/history_test.cljs` now contains `returns-history-rows-neutralizes-large-implied-cash-flow-intervals-test`, which proves the current fix avoids collapsing the series to `-100%` after a huge positive inferred rebase. That is the right seed case for the future formal and simulator corpora.

- Observation: exporting exact rationals as `{ :num, :den }` maps was simpler and safer than emitting pre-rounded decimal strings.
  Evidence: extending `/hyperopen/spec/lean/Hyperopen/Formal/Common.lean` with signed integer rendering let the new Lean surface keep exact arithmetic all the way into `/hyperopen/test/hyperopen/formal/portfolio_returns_estimator_vectors.cljs`, while `/hyperopen/src/hyperopen/schema/portfolio_returns_contracts.cljs` converts those proof values into runtime floats only at the comparison boundary.

- Observation: shifted-window rebasing ended up theorem-grade for the no-flow simulator cases.
  Evidence: the new Lean surface proves that the estimator exactly recovers latent window returns for the `no-flow-every-second-step-exact` and `no-flow-shifted-window-rebased-exact` corpora, even when the observation window starts after the latent process begins and `pnlHistory` is rebased at the first sample.

## Decision Log

- Decision: introduce a new formal surface named `portfolio-returns-estimator` with Lean module `Hyperopen.Formal.PortfolioReturnsEstimator`.
  Rationale: the work is about the specific estimator in `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs`, not all portfolio metrics. The surface name should be explicit enough that future contributors do not confuse it with benchmark or QuantStats parity work.
  Date/Author: 2026-04-14 / Codex

- Decision: prove only the theorem-worthy kernel invariants in Lean and treat numerical reliability under changing cadence as simulator-backed evidence unless a closed-form bound emerges from prototyping.
  Rationale: the current data model does not expose true ledger cash flows, so claims stronger than the kernel invariants would overstate what the formal surface can honestly establish.
  Date/Author: 2026-04-14 / Codex

- Decision: model latent ground truth explicitly instead of trying to “fake” ground truth directly from sampled Hyperliquid-shaped observations.
  Rationale: the correct comparison is between a hidden process we control and the observed `accountValueHistory` and `pnlHistory` we derive from it. This lets the plan distinguish latent truth, sampled observations, and estimator output cleanly.
  Date/Author: 2026-04-14 / Codex

- Decision: keep the production API stable unless conformance work proves that one tiny pure projection helper is necessary.
  Rationale: the public seam already includes `returns-history-rows-from-summary`, `returns-history-rows`, `cumulative-percent-rows->interval-returns`, and `daily-compounded-returns`. The formal work should compare those public functions before considering any API widening.
  Date/Author: 2026-04-14 / Codex

- Decision: encode cadence variation as a first-class acceptance target.
  Rationale: the practical risk here is not only a broken single formula; it is estimator drift or safety failure when the observation schedule is coarser than the latent process. The plan must therefore test sub-sampled and irregularly sampled scenarios deliberately instead of treating them as incidental.
  Date/Author: 2026-04-14 / Codex

## Outcomes & Retrospective

This work is now implemented. Hyperopen has a new Lean-backed formal surface for the portfolio returns estimator, a deterministic simulator-backed validation corpus, and ordinary CLJS tests that prove the production implementation in `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs` still matches the modeled outputs. The resulting surface reduces risk at the exact boundary that motivated the original bug: false catastrophic wipeouts on rebasing accounts are now defended by direct readable tests, formalized concrete theorems, generated conformance vectors, and simulator-backed cadence cases.

The implementation kept the claim boundary honest. Theorem-backed claims now cover concrete invariant corpora such as catastrophic loss clamp behavior, positive-rebase neutralization, and exact no-flow recovery under coarse or shifted-window sampling. Simulator-backed claims now cover cadence-sensitive rebasing scenarios through explicit cases with committed final-error ceilings and safety assertions such as “avoid false wipeout.” The upstream payload still does not identify true ledger cash flows universally, so the surface deliberately stops short of claiming real-world economic truth for arbitrary accounts.

## Context and Orientation

The production estimator lives in `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs`. In that file, `aligned-account-pnl-points` joins `accountValueHistory` and `pnlHistory` by timestamp. `anchored-account-pnl-points` drops any leading rows whose account value is not positive, because the estimator needs a positive starting capital base. `implied-cash-flow` computes `delta_account - delta_pnl`. `modified-dietz-return` and `fallback-period-return` both try to turn one observed interval into a return. `bounded-period-return` chooses among those rules and clamps the result so a single interval can never drop below `-0.999999`. `append-returns-history-row` compounds those interval returns into cumulative percentage rows, which `returns-history-rows-from-summary` and `returns-history-rows` expose publicly. `cumulative-percent-rows->interval-returns` and `daily-compounded-returns` then derive lower-frequency returns from that cumulative series.

The direct regression anchors for the current implementation live in `/hyperopen/test/hyperopen/portfolio/metrics/history_test.cljs`. Those tests already prove important behavior: the estimator uses shared timestamps only, guards invalid Dietz denominators, skips leading nonpositive anchors, returns an empty series when no positive anchor exists, clamps catastrophic losses, neutralizes the newly fixed large positive rebase interval, and builds canonical daily returns from cumulative rows. Those examples explain current intent, but they are not yet a formal proof surface and they do not yet exercise cadence variation systematically.

Hyperopen’s formal toolchain is repo-local. `/hyperopen/tools/formal/core.clj` is the Babashka wrapper invoked by `npm run formal:verify` and `npm run formal:sync`. `/hyperopen/spec/lean/Hyperopen/Formal/Common.lean` owns the `Surface` enum, manifest paths, generated-source paths, and shared rendering helpers. `/hyperopen/spec/lean/Hyperopen/Formal.lean` dispatches each surface to a `verify` and `sync` entrypoint. `/hyperopen/tools/formal/generated/*.edn` stores deterministic generated manifests. `/hyperopen/test/hyperopen/formal/*.cljs` stores the committed generated vector bridges that ordinary CLJS tests consume. Existing surfaces such as `effect-order-contract` provide the house pattern for a new modeled surface.

This plan uses three plain-language terms that must remain distinct. A latent process is the hidden “ground truth” sequence that the simulator controls directly, including starting equity, trading profit and loss for each fine-grained step, external cash flows, and optionally when within the step a cash flow lands. An observation model is the deterministic rule that turns that latent process into sampled `accountValueHistory` and `pnlHistory` arrays that look like the upstream portfolio payload. A cadence is the observation schedule: how often the latent process is sampled, which may be equal to the latent step size, a coarser subsequence of those steps, or an irregular list of observation times.

The formal work must be explicit about what it can and cannot prove. It can prove properties of the estimator algorithm given a modeled input stream. It can compare the estimator against a simulator under assumptions we choose and state. It cannot prove that the estimator matches real external cash flows for every live Hyperliquid account, because the current upstream payload does not contain those cash flows explicitly.

## Plan of Work

### Milestone 1: Register a new formal surface for the estimator and keep the existing workflow intact

Start by adding a new modeled surface named `portfolio-returns-estimator` to the formal wrapper and documentation. The goal of this milestone is not to change production behavior; it is to make the existing formal workflow capable of building, syncing, and verifying a returns-estimator surface the same way it already handles `effect-order-contract` and the other modeled surfaces.

Edit `/hyperopen/tools/formal/core.clj` so `supported-surfaces` includes `portfolio-returns-estimator` with Lean module `Hyperopen.Formal.PortfolioReturnsEstimator`, a manifest at `/hyperopen/tools/formal/generated/portfolio-returns-estimator.edn`, a transient generated source at `/hyperopen/target/formal/portfolio-returns-estimator-vectors.cljs`, and a committed generated bridge at `/hyperopen/test/hyperopen/formal/portfolio_returns_estimator_vectors.cljs`. Update `/hyperopen/spec/lean/Hyperopen/Formal/Common.lean`, `/hyperopen/spec/lean/Hyperopen/Formal.lean`, `/hyperopen/docs/tools.md`, `/hyperopen/tools/formal/README.md`, and `/hyperopen/dev/formal_tooling_test.clj` so the wrapper, usage text, and tooling tests treat the new surface as first-class. By the end of this milestone, `npm run test:formal-tooling` should pass and `npm run formal:verify -- --surface portfolio-returns-estimator` should resolve the surface name even if the proof module still has more work to do.

### Milestone 2: Formalize the theorem-worthy kernel that we can actually defend from the current data model

Create `/hyperopen/spec/lean/Hyperopen/Formal/PortfolioReturnsEstimator.lean` and model the estimator as a pure list transformation from aligned observed points to cumulative percentage rows. Define the minimum state needed for that reasoning directly in Lean: an observed point with `timeMs`, `accountValue`, and `pnlValue`; a bounded period return; a cumulative factor; and a cumulative percentage row. Mirror the production algorithm closely enough that a novice can line up the Lean definitions with `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs`.

The theorem set for this milestone must stay inside claims the current data model can support. The intended theorem corpus is:

- the anchored output preserves timestamp order and never invents new timestamps;
- if the anchored input is non-empty, the first cumulative row is exactly zero percent at the anchor timestamp;
- every period return is greater than or equal to `-0.999999`, so the cumulative factor never becomes negative;
- a sequence of zero period returns leaves cumulative return at zero for every row;
- the no-flow case, where `delta_account = delta_pnl` and previous account value is positive, reduces to the expected simple return and compounds exactly;
- a catastrophic loss interval still clamps to the lower bound rather than producing a factor below zero;
- a large positive inferred flow interval that also raises account value is classified as indeterminate and contributes a neutral period return rather than a false wipeout;
- a sequence of indeterminate positive rebase intervals cannot by itself drive the cumulative series to `-100%`.

These theorems are deliberately structural and conditional. They are about what the estimator guarantees if its premises hold, not about whether the premises are economically true for every real account.

### Milestone 3: Add a latent simulator and an observation model, then discover which cadence claims are theorem-grade and which are only test-grade

This milestone exists because the estimator’s hardest practical risk is cadence mismatch. Add a latent simulator either inside the same Lean module or as a tightly paired Lean helper module. The latent simulator should define a fine-grained process with starting account value, per-step trading `pnlDelta`, per-step external `cashFlow`, and an optional `flowArrivalFraction` that says when in the step the external flow lands. Then define an observation function that emits Hyperliquid-shaped sampled rows by keeping only a strictly increasing subsequence of latent timestamps and aggregating the latent state up to each sampled point.

The first job of this simulator is not to prove everything. Its first job is to tell us what can be claimed honestly. Build a deterministic corpus that covers at least these regimes:

- exact-alignment no-flow trading, where the estimator should recover latent cumulative returns exactly;
- exact-alignment small-flow trading with moderate implied flow ratio, where Dietz-style recovery should stay close to latent return;
- large positive inflow rebases that increase account value, where the estimator should stay safe and neutral rather than wiping out;
- catastrophic trading losses with no external inflow rescue, where the estimator should still clamp hard negative intervals;
- leading zero or negative account value segments, where the estimator should anchor only after the first positive capital base;
- coarse sampling schedules that skip multiple latent steps between observations;
- irregular sampling schedules whose gap widths vary across the same latent trace;
- identical latent processes sampled at two different cadences, where the estimator should not violate its safety properties and where any numerical accuracy comparison must be recorded as a bounded empirical result, not an unconditional theorem, unless a stable closed-form proof emerges.

The plan must treat cadence reliability as a prototyping milestone. If implementation discovers a clear theorem with honest premises, such as exact recovery for zero-flow latent traces under any monotone subsequence sampling, promote that theorem into the Lean proof corpus. If implementation does not find a defensible universal bound for arbitrary flow timing and arbitrary cadence changes, keep the cadence work as generated deterministic simulation vectors plus ordinary tests that enforce regime-specific error ceilings. Do not force a fake theorem just to make the surface feel more formal.

### Milestone 4: Bridge the generated vectors into CLJS conformance and simulator-matrix tests without widening production APIs casually

Once Lean can generate deterministic vectors, add the committed bridge at `/hyperopen/test/hyperopen/formal/portfolio_returns_estimator_vectors.cljs`. If the generated values need runtime contract checks, add a focused proof-surface namespace such as `/hyperopen/src/hyperopen/schema/portfolio_returns_contracts.cljs`. That namespace should validate only the generated proof and simulation projections. It must not become a second home for production business logic.

Then add ordinary CLJS conformance tests. The most natural home is a new file named `/hyperopen/test/hyperopen/portfolio/metrics/history_formal_conformance_test.cljs`. That test should compare the production public seam in `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs` against the generated vectors for:

the cumulative returns history rows, the interval-return extraction, the daily compounded returns, and any explicit classification or projection values that the generated corpus exposes. If the proof surface can be consumed cleanly through the existing public functions, do that. Only add a tiny new public pure helper in production if the absence of one makes the conformance layer brittle or unreadable.

Add a second ordinary test namespace for the simulator matrix if necessary, such as `/hyperopen/test/hyperopen/portfolio/metrics/history_simulator_test.cljs`. That test should consume the generated latent-versus-observed scenarios and enforce the promised regime-specific behavior. For theorem-grade scenarios, the expected result should be exact or epsilon-level equality. For cadence-quality scenarios, the expected result should be an explicit documented ceiling, such as “maximum cumulative error stays below the committed bound for this restricted corpus,” rather than an undefined notion of “fairly reliable.”

Keep the existing direct examples in `/hyperopen/test/hyperopen/portfolio/metrics/history_test.cljs` even after the formal surface lands. Those human-readable anchors explain behavior faster than generated vectors do, especially for the large positive rebase case that triggered this work.

### Milestone 5: Validate the proof pipeline, the simulator-backed harness, and the existing repository gates together

This close-out milestone proves that the new surface is a normal Hyperopen formal surface rather than a one-off Lean spike. Run `formal:sync` and `formal:verify` for `portfolio-returns-estimator`, then rerun the existing formal surfaces so the wrapper changes are not silently breaking unrelated proof work. After that, run the ordinary repository gates.

Do not close `hyperopen-uxtv` until all of the following are true. The Lean workspace builds with the new surface. The committed generated vectors stay deterministic. The theorem-backed corpus proves the kernel invariants listed above. The simulator-backed corpus exercises cadence changes and flow timing changes explicitly. The CLJS conformance tests pass against the production estimator. The existing formal surfaces still verify. `npm test`, `npm run test:websocket`, and `npm run check` are green. If the cadence prototype fails to support a strong theorem, record that outcome here and narrow the final claims honestly instead of leaving the ambiguity implicit.

## Concrete Steps

From `/Users/barry/.codex/worktrees/8d94/hyperopen`, begin by confirming the current formal baseline:

    export PATH="$HOME/.elan/bin:$PATH"
    npm run formal:verify -- --surface vault-transfer
    npm run formal:verify -- --surface order-request-standard
    npm run formal:verify -- --surface order-request-advanced
    npm run formal:verify -- --surface effect-order-contract
    npm run formal:verify -- --surface trading-submit-policy
    npm run formal:verify -- --surface order-form-ownership

Those commands should pass before the new work begins. If one fails, stop and repair the pre-existing formal baseline before adding a new surface.

Then add wrapper support and tests for the new surface:

    npm run test:formal-tooling
    npm run formal:verify -- --surface portfolio-returns-estimator

At first, the verify command may fail because the Lean module and generated bridge do not exist yet. That is acceptable during Milestone 1. By the end of Milestone 1, the command must resolve the surface and fail only for real proof or generated-artifact reasons.

The intended edit set for the full implementation is:

    /hyperopen/tools/formal/core.clj
    /hyperopen/tools/formal/README.md
    /hyperopen/docs/tools.md
    /hyperopen/dev/formal_tooling_test.clj
    /hyperopen/tools/formal/generated/portfolio-returns-estimator.edn
    /hyperopen/spec/lean/Hyperopen/Formal/Common.lean
    /hyperopen/spec/lean/Hyperopen/Formal.lean
    /hyperopen/spec/lean/Hyperopen/Formal/PortfolioReturnsEstimator.lean
    /hyperopen/test/hyperopen/formal/portfolio_returns_estimator_vectors.cljs
    /hyperopen/src/hyperopen/schema/portfolio_returns_contracts.cljs
    /hyperopen/test/hyperopen/portfolio/metrics/history_formal_conformance_test.cljs
    /hyperopen/test/hyperopen/portfolio/metrics/history_simulator_test.cljs
    /hyperopen/test/hyperopen/portfolio/metrics/history_test.cljs

Keep `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs` as the production authority. Only edit that file if implementation discovers that one tiny pure projection helper is necessary for stable conformance or if the work exposes a real bug that must be fixed to make the proven and tested model honest.

If new test namespaces are added, regenerate the runner:

    npm run test:runner:generate

Once the Lean surface emits vectors, refresh and verify the generated bridge:

    npm run formal:sync -- --surface portfolio-returns-estimator
    npm run formal:verify -- --surface portfolio-returns-estimator

Then rerun the existing surfaces and the required repository gates:

    npm run formal:verify -- --surface vault-transfer
    npm run formal:verify -- --surface order-request-standard
    npm run formal:verify -- --surface order-request-advanced
    npm run formal:verify -- --surface effect-order-contract
    npm run formal:verify -- --surface trading-submit-policy
    npm run formal:verify -- --surface order-form-ownership
    npm run test:formal-tooling
    npm test
    npm run test:websocket
    npm run check

Record the exact outcomes in `Progress` and `Outcomes & Retrospective` as the work lands.

## Validation and Acceptance

Acceptance is not “a Lean file exists.” Acceptance is that Hyperopen gains a stable formal surface for the portfolio returns estimator and a deterministic simulator-backed harness that clearly distinguishes proven invariants from empirical regime checks.

The implementation is complete when a contributor can run:

    export PATH="$HOME/.elan/bin:$PATH"
    npm run formal:verify -- --surface portfolio-returns-estimator

and see the new surface verify cleanly without stale-generated-source failures.

It is also complete when ordinary CLJS tests prove these observable behaviors:

The production estimator still emits cumulative rows whose timestamps match the modeled rows and whose first anchored row is zero percent.

The production estimator still clamps catastrophic losses and never produces a period factor below zero.

Large positive inferred rebase intervals that increase account value remain neutral rather than collapsing the cumulative series to `-100%`.

No-flow simulator cases recover latent cumulative returns exactly or at machine-epsilon tolerance.

Cadence-variation simulator cases satisfy the committed regime-specific ceilings or safety properties that the plan records during implementation. If no closed-form theorem exists for a cadence regime, the acceptance text must say exactly what empirical ceiling or qualitative safety property is enforced instead.

The existing formal surfaces still verify, proving the wrapper and shared Lean workspace were not broken by this addition.

## Idempotence and Recovery

All wrapper and proof commands in this plan must be rerunnable. `formal:sync` may overwrite `/hyperopen/test/hyperopen/formal/portfolio_returns_estimator_vectors.cljs`, but it must do so deterministically. `formal:verify` must remain read-only apart from transient files under `/hyperopen/target/formal/**`.

If Lean is missing or misconfigured, the repository must still be able to run `npm test`, `npm run test:websocket`, and `npm run check` against the checked-in generated vectors. Only `formal:sync` and `formal:verify` may require Lean. If the generated bridge looks suspicious, rerun `formal:sync`, inspect the generated namespace only, and confirm the corresponding theorem or simulator corpus change in `/hyperopen/spec/lean/Hyperopen/Formal/PortfolioReturnsEstimator.lean` before accepting it.

If the cadence prototype fails to support a universal reliability bound, do not stall the entire surface. Narrow the theorem corpus to what is provable, keep cadence validation as deterministic generated scenarios with explicit ceilings, and record that narrowing in the `Decision Log` and `Outcomes & Retrospective`.

## Artifacts and Notes

The most important evidence for this work should stay concise inside this plan as implementation proceeds. Capture short command transcripts such as:

    npm run formal:verify -- --surface portfolio-returns-estimator
    Verified portfolio-returns-estimator and confirmed the checked-in artifacts are current.

    npm test
    ...
    Ran 3124 tests containing 16771 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    ...
    Ran 432 tests containing 2479 assertions.
    0 failures, 0 errors.

    npm run check
    ...
    [:test] Build completed. (1209 files, 4 compiled, 0 warnings, 5.87s)

Keep one or two reduced simulator examples in this plan if they clarify the final guarantees. The best candidates are:

one no-flow latent trace whose observed rows are sampled at a coarser cadence but still recover the exact latent return; and one rebasing account trace whose first large positive inflow used to imply a false wipeout and now stays neutral while later negative intervals still move the series honestly.

If implementation exposes a mismatch between production and model, record the smallest failing latent trace and the resulting observed rows directly in this section so the next contributor can reproduce it without reconstructing any outside state.

## Interfaces and Dependencies

The new formal surface must be named `portfolio-returns-estimator` in the Babashka wrapper and in the Lean `Surface` enum. The Lean module should be `Hyperopen.Formal.PortfolioReturnsEstimator`. The committed generated bridge should be the namespace `hyperopen.formal.portfolio-returns-estimator-vectors`, written to `/hyperopen/test/hyperopen/formal/portfolio_returns_estimator_vectors.cljs`.

The production authority remains `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs`. The end state should preserve these stable public functions unless the plan is revised explicitly:

    returns-history-rows-from-summary
    returns-history-rows
    cumulative-percent-rows->interval-returns
    daily-compounded-returns
    strategy-daily-compounded-returns

The new proof-surface contract namespace, if needed, should live under `/hyperopen/src/hyperopen/schema/portfolio_returns_contracts.cljs` and should validate only generated vector and projection shapes. It must not become an alternate implementation of the estimator.

The simulator must model two layers explicitly. The latent layer owns start capital, trading `pnlDelta`, external `cashFlow`, and optional flow timing within each fine-grained step. The observation layer owns monotone sampled timestamps and the derived `accountValueHistory` and `pnlHistory` rows that mimic the upstream portfolio payload. The estimator under test only sees the observation layer. Any accuracy claim stronger than a theorem about that modeled two-layer system is out of scope unless the plan is revised with stronger source data.

Plan revision note (2026-04-14 08:56 EDT): Initial active ExecPlan created after auditing the current portfolio returns estimator, the direct regression anchors in `/hyperopen/test/hyperopen/portfolio/metrics/history_test.cljs`, the current Lean/formal wrapper under `/hyperopen/tools/formal/core.clj`, the Lean workspace under `/hyperopen/spec/lean/`, and the repo’s active-plan and `bd` workflow contracts. The plan deliberately separates theorem-backed estimator invariants from simulator-backed cadence and flow-quality claims so the final verification story stays honest.
