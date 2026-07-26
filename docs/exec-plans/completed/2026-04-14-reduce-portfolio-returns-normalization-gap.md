# Reduce the Portfolio Returns Normalization Gap Outside Lean

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md`. The primary live `bd` issue for this work is `hyperopen-l5m0`, which was created as a follow-up discovered from `hyperopen-uxtv`.

## Purpose / Big Picture

Hyperopen now has a Lean-backed formal surface for the portfolio returns estimator, but that proof starts after the runtime has already parsed, filtered, sorted, deduped, aligned, and anchored raw history rows. The remaining gap is that the user-visible chart still depends on JavaScript parsing and normalization behavior that Lean does not currently model. After this work, a contributor will be able to prove and test the structural normalization path that feeds the estimator: raw account-value and PNL rows with mixed shapes, ordering, duplicates, and accepted numeric encodings will normalize into one deterministic canonical summary before the estimator runs.

The visible outcome is not a new UI. The visible outcome is stronger correctness evidence for the existing chart. A contributor should be able to run a dedicated normalization formal surface, run direct boundary tests for raw history parsing and canonicalization, and then show that normalization-preserving perturbations of the same observed samples do not change the final returns series. This shrinks the proof gap materially without pretending that we can formally model all JavaScript numeric behavior or recover exact economic truth without explicit ledger cash flows.

## Progress

- [x] (2026-04-14 13:15 EDT) Re-read `/hyperopen/AGENTS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/.agents/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md` before drafting the follow-up plan.
- [x] (2026-04-14 13:18 EDT) Audited the normalization boundary in `/hyperopen/src/hyperopen/portfolio/metrics/parsing.cljs`, `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs`, `/hyperopen/src/hyperopen/schema/portfolio_returns_contracts.cljs`, `/hyperopen/test/hyperopen/portfolio/metrics/history_formal_conformance_test.cljs`, and `/hyperopen/test/hyperopen/portfolio/metrics/history_simulator_test.cljs`.
- [x] (2026-04-14 13:21 EDT) Created and claimed `bd` issue `hyperopen-l5m0` for the normalization-gap follow-up and linked it as `discovered-from:hyperopen-uxtv`.
- [x] (2026-04-14 13:28 EDT) Created this active ExecPlan and froze scope to the portfolio returns normalization boundary, a structural Lean surface, raw-input conformance vectors, and normalization-preserving cadence perturbation tests. Full JavaScript floating-point semantics, benchmark parity, and ledger-ground-truth accounting remain out of scope.
- [x] (2026-04-14 14:05 EDT) Implemented Milestone 1 by extracting `/hyperopen/src/hyperopen/portfolio/metrics/normalization.cljs`, routing `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs` through that shared seam, and tightening `/hyperopen/src/hyperopen/portfolio/metrics/parsing.cljs` to accepted finite numeric strings instead of broad `parseFloat` coercion.
- [x] (2026-04-14 14:07 EDT) Implemented Milestone 2 by adding `/hyperopen/test/hyperopen/portfolio/metrics/parsing_test.cljs` and `/hyperopen/test/hyperopen/portfolio/metrics/history_normalization_test.cljs` for strict numeric parsing, mixed row shapes, duplicate timestamps, finite timestamp checks, exact joins, and zero-factor cumulative rows.
- [x] (2026-04-14 14:08 EDT) Implemented Milestone 3 by adding `/hyperopen/spec/lean/Hyperopen/Formal/PortfolioReturnsNormalization.lean`, wiring the `portfolio-returns-normalization` surface through the formal tooling, and syncing `/hyperopen/tools/formal/generated/portfolio-returns-normalization.edn` plus `/hyperopen/test/hyperopen/formal/portfolio_returns_normalization_vectors.cljs`.
- [x] (2026-04-14 14:10 EDT) Implemented Milestone 4 by extending `/hyperopen/test/hyperopen/portfolio/metrics/history_formal_conformance_test.cljs` with normalization-vector conformance coverage, adding `/hyperopen/src/hyperopen/schema/portfolio_returns_normalization_contracts.cljs`, and hardening `/hyperopen/test/hyperopen/portfolio/metrics/history_simulator_test.cljs` with raw-observation perturbation cases.
- [x] (2026-04-14 15:11 EDT) Ran `npm run formal:sync -- --surface portfolio-returns-normalization`, `npm run formal:verify -- --surface portfolio-returns-normalization`, `npm run formal:verify -- --surface portfolio-returns-estimator`, `npm run lint:input-parsing`, `npm test`, `npm run test:websocket`, and `npm run check` successfully, then filed follow-up `bd` issue `hyperopen-ktij` for the remaining summary-parser consistency risk outside this scope.

## Surprises & Discoveries

- Observation: the current formal surface starts after canonicalization, not at the raw API boundary.
  Evidence: `/hyperopen/src/hyperopen/schema/portfolio_returns_contracts.cljs` only validates canonical integer `[time value]` histories for generated vectors, and the conformance tests feed those directly into the runtime without mixed raw row shapes.

- Observation: `optional-number` currently inherits `js/parseFloat` behavior, which is wider and stranger than the formal model.
  Evidence: `/hyperopen/src/hyperopen/portfolio/metrics/parsing.cljs` uses `js/parseFloat` directly, so partial strings such as `"123abc"` and non-finite tokens such as `"Infinity"` are runtime concerns outside Lean.

- Observation: runtime structural normalization is broader than the estimator seam alone.
  Evidence: besides summary normalization in `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs`, the file also normalizes cumulative rows, daily rows, and aligned benchmark rows through `normalize-cumulative-percent-rows`, `normalize-daily-rows`, and `align-daily-returns`.

- Observation: some normalization logic is duplicated instead of shared.
  Evidence: `normalize-cumulative-percent-rows` dedupes same-timestamp cumulative rows, while `cumulative-percent-rows->interval-returns` reparses and sorts independently. That creates a drift risk if one path changes and the other does not.

- Observation: exact timestamp intersection is an intentional policy choice, but the raw-input boundary does not expose it explicitly.
  Evidence: `aligned-account-pnl-points` in `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs` joins histories only when timestamps match exactly. That is safe and deterministic, but the current formal surface only sees the already-joined result.

- Observation: sharing the cumulative-row normalizer with interval-return extraction exposed a real semantic edge at `-100%`.
  Evidence: the first integrated `npm test` run failed in `/hyperopen/test/hyperopen/portfolio/metrics/history_formal_conformance_test.cljs` for `:nonpositive-previous-factor-guards` because the shared normalizer initially filtered zero factors. Keeping zero-factor rows and only dropping negative factors restored the prior estimator invariant and kept the unified seam honest.

- Observation: the normalization-specific proof contracts were large enough to trip the repository namespace-size guard when left inside `/hyperopen/src/hyperopen/schema/portfolio_returns_contracts.cljs`.
  Evidence: the first `npm run check` failed with `[missing-size-exception] src/hyperopen/schema/portfolio_returns_contracts.cljs - namespace has 565 lines`. Splitting the normalization vector contracts into `/hyperopen/src/hyperopen/schema/portfolio_returns_normalization_contracts.cljs` fixed the lint without adding a size exception.

- Observation: there is no concrete repo-local evidence that portfolio history callers require old partial-string coercion today, but adjacent summary view-model helpers still use broader parsing.
  Evidence: a read-only codebase scan found no direct dependence on `"123abc"`-style parsing in the history path, but identified a remaining contract mismatch in adjacent summary helpers under `/hyperopen/src/hyperopen/views/portfolio/vm/**`, which is now tracked in `hyperopen-ktij`.

## Decision Log

- Decision: treat this as a new follow-up issue and active plan instead of silently extending the completed estimator-formalization plan.
  Rationale: the prior work established the estimator kernel and simulator-backed cadence guarantees. This work is about the upstream normalization boundary and deserves its own live issue, progress log, and acceptance criteria.
  Date/Author: 2026-04-14 / Codex

- Decision: reduce the gap by narrowing and centralizing the repository-owned normalization contract rather than trying to formalize all of JavaScript number parsing.
  Rationale: `js/parseFloat` semantics are not the right proof target. A smaller, explicit contract for accepted finite numbers and canonical row normalization is more defensible and easier to test.
  Date/Author: 2026-04-14 / Codex

- Decision: introduce a separate Lean surface for structural normalization, tentatively named `portfolio-returns-normalization`, instead of overloading the existing `portfolio-returns-estimator` surface.
  Rationale: the estimator surface should remain about return math and cadence guarantees. The normalization surface should stay focused on sorting, deduping, joining, anchoring, and canonicalization rules.
  Date/Author: 2026-04-14 / Codex

- Decision: keep string lexing and raw numeric acceptance mostly in ordinary CLJS tests unless the accepted grammar is narrowed enough to model cleanly in Lean.
  Rationale: the biggest value comes from proving structural canonicalization after finite numbers exist. Overextending Lean to match browser numeric corner cases would add complexity without commensurate confidence.
  Date/Author: 2026-04-14 / Codex

- Decision: make normalization-preserving perturbation tests a first-class acceptance target.
  Rationale: the chart should not change just because the same observed samples arrive out of order, with duplicate timestamps, or in mixed map/vector shapes. Those are real runtime hazards that the current theorem surface does not guard.
  Date/Author: 2026-04-14 / Codex

- Decision: preserve zero cumulative factors in the shared cumulative normalizer and reject only negative factors.
  Rationale: the existing returns semantics intentionally retain `-100%` cumulative rows so interval extraction can emit the wipeout interval and then guard later recovery intervals correctly. Filtering zero factors made the shared seam mathematically cleaner but behaviorally wrong for the existing estimator contract.
  Date/Author: 2026-04-14 / Codex

- Decision: split normalization proof contracts into `/hyperopen/src/hyperopen/schema/portfolio_returns_normalization_contracts.cljs` instead of adding a namespace-size exception.
  Rationale: the normalization surface is a separate proof boundary and deserves a separate contract namespace. The split satisfies the lint and keeps the estimator-contract namespace focused on the return-math surfaces.
  Date/Author: 2026-04-14 / Codex

## Outcomes & Retrospective

Completed. The implementation landed a dedicated runtime normalization seam in `/hyperopen/src/hyperopen/portfolio/metrics/normalization.cljs`, a stricter finite-number parser in `/hyperopen/src/hyperopen/portfolio/metrics/parsing.cljs`, a structural Lean surface at `/hyperopen/spec/lean/Hyperopen/Formal/PortfolioReturnsNormalization.lean`, generated normalization vectors, CLJS conformance tests, raw-boundary tests, and simulator perturbation tests that prove representation noise does not alter the estimator output. The estimator surface still verifies, and all required gates passed.

This work reduced overall complexity. `history.cljs` no longer owns two partially overlapping normalization stories, `cumulative-percent-rows->interval-returns` now shares the same canonical cumulative-row path as the rest of the metrics stack, and the normalization proof/data contracts live in their own namespace instead of inflating the estimator-contract surface. The only remaining known gap is broader parser-consistency outside the returns/history path, which is explicitly tracked in `hyperopen-ktij` rather than left implicit.

## Context and Orientation

The portfolio returns estimator lives in `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs`. That namespace currently does two different jobs. First, it normalizes raw rows from the API or from intermediate callers: it parses times and values, filters invalid rows, sorts by time, collapses duplicate timestamps, aligns account-value and PNL histories, trims leading nonpositive account values, and normalizes cumulative and daily return rows. Second, it computes returns from those normalized rows. The Lean module `/hyperopen/spec/lean/Hyperopen/Formal/PortfolioReturnsEstimator.lean` only models the second job plus the simulator-backed observation layer. It assumes canonical `Summary` inputs whose histories are already lists of exact `(Nat × Int)` pairs.

The raw parsing helpers now live in `/hyperopen/src/hyperopen/portfolio/metrics/parsing.cljs`, and the structural normalization seam now lives in `/hyperopen/src/hyperopen/portfolio/metrics/normalization.cljs`. `optional-number` is the important boundary function there. It now accepts native finite numbers and full-string finite decimal or exponent forms. `history-point-value` and `history-point-time-ms` still impose key-precedence rules for maps and accept vector rows. Those are runtime policy choices. They matter because the chart consumes real payloads, not just the canonical vectors generated by Lean.

The proof bridges now live in two places. `/hyperopen/src/hyperopen/schema/portfolio_returns_contracts.cljs` still validates the return-math proof vectors. `/hyperopen/src/hyperopen/schema/portfolio_returns_normalization_contracts.cljs` validates the normalization vectors and runtime structural projections. The current tests under `/hyperopen/test/hyperopen/portfolio/metrics/` now cover both the estimator math and the raw normalization boundary directly.

The normalization gap is therefore concrete:

- raw numeric coercion is outside Lean
- map/vector row shape decoding is outside Lean
- sort and duplicate resolution are outside Lean
- exact timestamp alignment is outside Lean
- positive-account anchoring is outside Lean
- cumulative and daily normalization helpers are outside Lean

This plan narrows that gap by introducing one repository-owned normalization contract, proving the structural part of that contract in Lean, and covering the raw string/numeric boundary with explicit runtime tests instead of leaving it implicit.

## Plan of Work

### Milestone 1: Extract and freeze one canonical normalization seam

Begin by separating normalization from return math so the proof target is obvious in code. The likely shape is a new pure namespace such as `/hyperopen/src/hyperopen/portfolio/metrics/normalization.cljs`, but the exact filename can change if a clearer name emerges during implementation. What matters is that all structural cleanup of raw rows happens in one place and the public functions in `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs` call into it rather than reimplementing variants of the same logic.

The seam should own these responsibilities:

- parsing accepted finite numeric inputs from raw numbers and numeric strings
- decoding supported vector and map row shapes for time and value
- canonicalizing history points into sorted unique `{:time-ms ... :value ...}` rows
- making duplicate resolution explicit and deterministic
- aligning account and PNL histories by exact timestamp intersection
- trimming leading nonpositive account values
- canonicalizing cumulative-percent rows and daily rows so interval and daily calculations share one normalization path

The implementation should avoid preserving accidental `parseFloat` behavior unless the repository truly depends on it. Before changing the parser, audit the portfolio history callers and current tests to determine which numeric forms are actually required. If the real payloads only need finite numbers and full-string decimal forms, replace `js/parseFloat` with a stricter parser and lock that behavior down in tests. If exponent strings are required, support them explicitly. If partial parses such as `"12abc"` are currently relied on anywhere, record that discovery in this plan before deciding whether to preserve or reject them.

At the end of this milestone, the runtime should have exactly one canonical path from raw history rows to normalized rows. `cumulative-percent-rows->interval-returns` must no longer bypass the shared cumulative-row normalizer. Duplicate timestamp handling should be explicit rather than an accidental consequence of sort stability. The public API in `/hyperopen/src/hyperopen/portfolio/metrics.cljs` should remain stable.

### Milestone 2: Add direct runtime boundary tests for the normalization contract

Once the seam exists, add a dedicated test namespace for it. The likely homes are `/hyperopen/test/hyperopen/portfolio/metrics/parsing_test.cljs` and `/hyperopen/test/hyperopen/portfolio/metrics/history_normalization_test.cljs`, though one namespace is acceptable if it stays readable. The goal is to make the boundary policies visible without reading the implementation.

Those tests must cover:

- finite numbers and accepted numeric strings
- rejection of `NaN`, `Infinity`, blank strings, and malformed strings
- vector rows and map rows with the supported time/value keys
- key-precedence conflicts when a row exposes multiple candidate keys
- unsorted histories
- duplicate timestamps with explicit last-write-wins behavior
- exact timestamp joins that intentionally drop unmatched account or PNL rows
- leading zero or negative account values before the anchor point
- non-finite timestamps in cumulative and daily row normalizers
- duplicate cumulative rows and malformed daily rows

Keep a few readable regression tests in `/hyperopen/test/hyperopen/portfolio/metrics/history_test.cljs` for the user-reported rebasing account and other high-signal stories. The new normalization tests should explain the structural contract. The existing estimator tests should explain the business outcome.

### Milestone 3: Add a structural Lean surface for canonicalization

After the runtime seam is explicit, add a new formal surface tentatively named `portfolio-returns-normalization`. Wire it through `/hyperopen/tools/formal/core.clj`, `/hyperopen/spec/lean/Hyperopen/Formal/Common.lean`, `/hyperopen/spec/lean/Hyperopen/Formal.lean`, `/hyperopen/dev/formal_tooling_test.clj`, `/hyperopen/tools/formal/README.md`, and `/hyperopen/docs/tools.md`, following the same repository pattern used by the existing formal surfaces.

The new Lean module should model already-parsed finite rows, not the full raw string parser. Define simple structures for:

- canonical finite history rows
- normalized history points
- normalized summaries
- aligned observed points
- canonical cumulative rows
- canonical daily rows if the daily helpers are brought under the same seam

The theorem-backed properties should be structural and honest:

- normalization outputs are sorted by `time-ms`
- normalization outputs have at most one row per timestamp
- duplicate timestamps resolve deterministically according to the runtime contract
- alignment includes only timestamps present in both histories
- anchoring drops all leading rows until the first strictly positive account value
- re-normalizing already-normalized rows is idempotent
- normalization-preserving permutations of unique-timestamp rows do not change the canonical output

If it proves easier to split daily-row normalization into a second phase, record that decision here and keep the first surface focused on summary histories and cumulative rows. The point is to prove the canonicalization rules that feed the estimator, not to chase every helper in one step.

### Milestone 4: Bridge generated normalization vectors into CLJS conformance tests

Once Lean can emit deterministic normalization vectors, commit them under `/hyperopen/test/hyperopen/formal/portfolio_returns_normalization_vectors.cljs`. Extend `/hyperopen/src/hyperopen/schema/portfolio_returns_contracts.cljs` or add a sibling namespace if that keeps the contracts clearer. The contract layer should validate generated normalization vectors, normalized summary shapes, aligned point shapes, and normalized cumulative or daily rows as needed.

Add a new conformance test namespace, likely `/hyperopen/test/hyperopen/portfolio/metrics/history_normalization_formal_conformance_test.cljs`, that compares the runtime normalization seam against the generated vectors before the estimator runs. The assertions should be direct and structural: exact times, exact chosen values, exact duplicate resolution, exact aligned outputs, exact anchor trimming.

This milestone should also harden the estimator tests by starting some conformance cases from raw mixed-shape summaries rather than only canonical vectors. When the raw input and the canonicalized summary represent the same observed samples, the estimator output should match exactly.

### Milestone 5: Reuse the simulator by perturbing raw representation as well as cadence

The current estimator simulator already varies latent trading steps, cash flows, and observation cadence. Reuse that work instead of building a second simulator. For selected simulator corpora, generate raw observation wrappers that change only representation:

- shuffle account and PNL row order
- duplicate sampled rows at the same timestamp
- mix vector rows and map rows
- vary accepted numeric encodings that normalize to the same number

Feed those raw perturbations through the new normalization seam and then through the existing estimator. The acceptance target is:

- if the underlying observed samples are the same after canonicalization, the estimator output must match the canonical reference exactly
- if the underlying observed samples are cadence-ambiguous rebasing cases, the existing simulator error ceilings and false-wipeout protections must still hold after the raw perturbation layer is added

This milestone makes the cadence story more robust in the only honest way available with current data: it proves that representational noise does not create drift on top of the already-modeled cadence limitations.

### Milestone 6: Validate the full path and keep the claims honest

When implementation is done, rerun both formal surfaces and the repository gates. The normalization surface must verify cleanly, the estimator surface must still verify cleanly, the runtime normalization tests must pass, and the existing returns tests must remain green. If any raw-input behavior has to remain outside Lean, document that precisely here and in the final retrospective. The goal is to narrow the gap, not to pretend it vanished completely.

## Concrete Steps

From `/Users/barry/.codex/worktrees/8d94/hyperopen`, begin by confirming the current estimator-formalization baseline and by auditing the input-parsing lint:

    export PATH="$HOME/.elan/bin:$PATH"
    npm run formal:verify -- --surface portfolio-returns-estimator
    npm run lint:input-parsing

If either command fails before this work begins, stop and repair the baseline before adding a new surface.

Then implement the normalization seam and direct runtime tests. The intended edit set is:

    /hyperopen/src/hyperopen/portfolio/metrics/parsing.cljs
    /hyperopen/src/hyperopen/portfolio/metrics/history.cljs
    /hyperopen/src/hyperopen/portfolio/metrics.cljs
    /hyperopen/src/hyperopen/schema/portfolio_returns_contracts.cljs
    /hyperopen/test/hyperopen/portfolio/metrics/history_test.cljs
    /hyperopen/test/hyperopen/portfolio/metrics/parsing_test.cljs
    /hyperopen/test/hyperopen/portfolio/metrics/history_normalization_test.cljs

If a new pure normalization namespace is added, also edit:

    /hyperopen/src/hyperopen/portfolio/metrics/normalization.cljs

Once the runtime seam exists, wire the new formal surface and generated artifacts:

    /hyperopen/tools/formal/core.clj
    /hyperopen/tools/formal/README.md
    /hyperopen/docs/tools.md
    /hyperopen/dev/formal_tooling_test.clj
    /hyperopen/spec/lean/Hyperopen/Formal/Common.lean
    /hyperopen/spec/lean/Hyperopen/Formal.lean
    /hyperopen/spec/lean/Hyperopen/Formal/PortfolioReturnsNormalization.lean
    /hyperopen/tools/formal/generated/portfolio-returns-normalization.edn
    /hyperopen/test/hyperopen/formal/portfolio_returns_normalization_vectors.cljs
    /hyperopen/test/hyperopen/portfolio/metrics/history_normalization_formal_conformance_test.cljs

If new tests are added, regenerate the runner:

    npm run test:runner:generate

Then sync and verify the new surface:

    export PATH="$HOME/.elan/bin:$PATH"
    npm run formal:sync -- --surface portfolio-returns-normalization
    npm run formal:verify -- --surface portfolio-returns-normalization

Finally, rerun the estimator surface and repository gates:

    export PATH="$HOME/.elan/bin:$PATH"
    npm run formal:verify -- --surface portfolio-returns-estimator
    npm run test:formal-tooling
    npm test
    npm run test:websocket
    npm run check

Record the exact outcomes in `Progress` and `Outcomes & Retrospective`. If parser semantics were intentionally tightened, include at least one short transcript or test name proving the accepted and rejected raw forms.

## Validation and Acceptance

Acceptance is not “a normalization namespace exists.” Acceptance is that Hyperopen gains a stable, explicit, and testable normalization contract upstream of the returns estimator.

The implementation is complete when a contributor can run:

    export PATH="$HOME/.elan/bin:$PATH"
    npm run formal:verify -- --surface portfolio-returns-normalization
    npm run formal:verify -- --surface portfolio-returns-estimator

and see both surfaces verify cleanly without stale-artifact failures.

It is also complete when the ordinary CLJS tests prove these observable behaviors:

Raw history rows that differ only in ordering, duplicate representation, or supported row shape normalize to the same canonical output.

Malformed numeric strings, `NaN`, and `Infinity` are either rejected explicitly or preserved intentionally with documented tests. Silent accidental acceptance is not allowed.

Duplicate timestamps resolve according to one explicit contract that is shared by the estimator path, the cumulative-row path, and the daily-row path.

Exact timestamp joins remain deterministic and visible in direct normalization tests rather than only as an indirect effect of estimator output.

Normalization-preserving perturbations of simulator observations do not change the estimator output. Cadence-ambiguous rebasing cases still satisfy the existing false-wipeout protections and error ceilings after raw perturbations are added.

`npm run lint:input-parsing`, `npm test`, `npm run test:websocket`, and `npm run check` pass, proving the normalization work did not regress the rest of the repository.

## Idempotence and Recovery

All normalization helpers must be pure and rerunnable. `formal:sync` may overwrite `/hyperopen/test/hyperopen/formal/portfolio_returns_normalization_vectors.cljs`, but it must do so deterministically. `formal:verify` must stay read-only apart from transient files under `/hyperopen/target/formal/**`.

If the new parser contract is too strict for an existing caller, do not work around it by scattering fallback coercions through view-model code. Instead, update the normalization seam and its tests deliberately, record the requirement in this plan, and rerun the surface and boundary tests. If the Lean surface cannot express part of the accepted raw-input grammar cleanly, stop the proof at already-parsed finite rows and document that boundary explicitly rather than smuggling parser semantics into theorem claims.

If a new normalization namespace causes churn in unrelated callers, keep the public functions in `/hyperopen/src/hyperopen/portfolio/metrics.cljs` stable and adapt callers incrementally. The goal is to centralize behavior, not to force a broad API migration.

## Artifacts and Notes

The most useful evidence for this work should stay concise in this plan as implementation proceeds. Good artifacts include:

    npm run lint:input-parsing
    ...
    input parsing lint passed after tightening portfolio history normalization.

    npm run formal:verify -- --surface portfolio-returns-normalization
    Verified portfolio-returns-normalization and confirmed the checked-in normalization vectors are current.

    npm test
    ...
    new tests:
      parsing-accepts-strict-decimals-and-rejects-partial-strings-test
      history-normalization-last-write-wins-for-duplicate-times-test
      simulator-raw-perturbations-preserve-canonical-estimator-output-test

When implementation discovers a subtle boundary rule, record one reduced example here in raw form and canonical form. The best candidates are:

- a mixed map/vector summary whose rows are out of order and contain duplicates but normalize into the same aligned points as the canonical proof vector
- a rebasing simulator case whose raw observation rows are shuffled and duplicated but still produce the same final returns series after canonicalization

If the repository turns out to require an accepted numeric form that was not anticipated in this plan, add the smallest concrete example here and update `Decision Log` before expanding the contract.

## Interfaces and Dependencies

The existing estimator formal surface remains `portfolio-returns-estimator`. The new structural surface should be named `portfolio-returns-normalization` in the Babashka wrapper and in the Lean `Surface` enum. The Lean module should be `Hyperopen.Formal.PortfolioReturnsNormalization`. The committed generated bridge should be the namespace `hyperopen.formal.portfolio-returns-normalization-vectors`, written to `/hyperopen/test/hyperopen/formal/portfolio_returns_normalization_vectors.cljs`.

The runtime normalization seam should be pure. It may live in a new namespace such as `/hyperopen/src/hyperopen/portfolio/metrics/normalization.cljs` or stay in `/hyperopen/src/hyperopen/portfolio/metrics/history.cljs` if that keeps the boundaries cleaner, but the end state must expose one internal place where raw rows become canonical rows. Whatever namespace owns that seam should provide stable pure helpers for:

- parsing accepted finite numeric inputs
- canonicalizing raw history rows
- canonicalizing cumulative-percent rows
- canonicalizing daily rows
- aligning account and PNL histories
- anchoring normalized summaries at the first positive account value

`/hyperopen/src/hyperopen/portfolio/metrics/history.cljs` should remain the production authority for the estimator math itself. The normalization surface must feed that authority; it must not become a second estimator implementation.

The existing contract namespace `/hyperopen/src/hyperopen/schema/portfolio_returns_contracts.cljs` may be extended to cover normalized summaries and normalization vectors. If that file becomes hard to read, split a sibling namespace such as `/hyperopen/src/hyperopen/schema/portfolio_returns_normalization_contracts.cljs` and keep the responsibilities explicit. Avoid mixing proof-vector validation with business logic.

Plan revision note (2026-04-14 13:28 EDT): Initial active ExecPlan created after auditing the runtime normalization boundary that remains outside the existing Lean estimator surface. This plan deliberately narrows the proof target to structural canonicalization and explicit parser contracts, while keeping raw browser numeric oddities and ledger-ground-truth accounting outside scope unless future source data justifies a stronger claim.

Plan revision note (2026-04-14 15:11 EDT): Completed implementation. Updated the living sections to record the shipped normalization seam, the zero-factor cumulative-row decision, the contract-namespace split required by namespace-size policy, the full validation transcript, and follow-up `bd` issue `hyperopen-ktij` for parser-consistency work that remains outside the returns/history scope.
