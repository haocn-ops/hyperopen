# Merge Upstream Main Into an Isolated Hyperopen Branch

This ExecPlan is a living document and must be maintained according to `/Users/zh/Documents/Hyperopen/.agents/PLANS.md` and `/Users/zh/Documents/Hyperopen/docs/PLANS.md`. It records the current-session request to inspect and update from upstream; it is not a deployment authorization.

## Purpose / Big Picture

Hyperopen is currently on local `main` commit `7652651b443d8fa7a9b1d85f49a0f966eb1d3b9b`, while the canonical upstream remote has advanced to `origin/main` commit `d708a545dd45aa9923b0936edb350efca9172e05`. The merge will make an isolated branch that contains both histories so contributors receive upstream staking-account safety, optimizer execution fixes, dead-code cleanup, and QA/test tooling updates without losing the local Cloudflare and DEXHelm release work. Success is visible in the branch ancestry, retained local release files, passing repository gates, and targeted browser/test regressions. No remote push or Cloudflare deployment is part of this plan.

## Context References

Public refs:

- Direct maintainer request in the current session: “看下上游代码，做下更新” (review the upstream code and update it).

Repo artifacts:

- Root operating contract: `AGENTS.md`.
- Planning contract: `docs/PLANS.md` and `.agents/PLANS.md`.
- Multi-agent contract: `docs/MULTI_AGENT.md`.
- Browser command routing: `docs/BROWSER_TESTING.md`.
- Local release/readiness records: `docs/exec-plans/active/2026-08-05-dexhelm-mainnet-opening.md` and `docs/exec-plans/active/2026-08-05-dexhelm-mainnet-phase9.md`.
- Canonical upstream: `https://github.com/thegeronimo/hyperopen.git`, fetched as `origin`.

Local scratch refs (non-authoritative):

- None. The pre-existing untracked `test-results/` directory is user output, not work tracking, and must remain untouched.

## Progress

- [x] (2026-08-06) Confirmed `HEAD=7652651b`, `origin/main=d708a545`, merge base `aa89194b`, local divergence of 17 commits, upstream divergence of 15 commits, and an otherwise clean tracked worktree with only untracked `test-results/`.
- [x] (2026-08-06) Audited the upstream delta: 119 files, 4,266 insertions, and 1,409 deletions, including staking account scope/undelegation safety, optimizer execution stability and pricing, dead-code cleanup, QA tooling/tests, and badge/lockfile churn.
- [x] (2026-08-06) Created `codex/upstream-sync-20260806` from local `main`, merged `origin/main`, and resolved 9 textual conflicts while preserving both parent histories.
- [x] (2026-08-06) Ran delimiter preflight, `npm test`, `npm run check`, `npm run test:websocket`, targeted staking Playwright (6/6), and the full Playwright CI suite (216 passed, 22 failed, 7 flaky, 4 skipped).
- [ ] Verify local Cloudflare/DEXHelm files and `test-results/` are preserved, review the final diff, and hand off the branch and remaining risks without pushing or deploying.

## Surprises & Discoveries

- Observation: 25 paths are modified by both parents, not just the new staking files.
  Evidence: the shared set includes `package.json`, `package-lock.json`, runtime/schema registration, optimizer actions/payloads, app defaults, views, and generated test-runner code.
- Observation: the local parent adds a broad Cloudflare/DEXHelm release and security surface that is absent from the upstream delta.
  Evidence: local-only paths include `workers/hyperopen-worker.mjs`, `wrangler*.jsonc`, `config/white-label/**`, `tools/cloudflare/**`, and `.agents/skills/deploy-hyperopen-cloudflare/**`.
- Observation: the dependency conflict is semantic, not merely textual.
  Evidence: local `package.json` pins dependencies and adds security/Cloudflare scripts and overrides, while upstream only raises `c8` to `^12.0.0`; blindly choosing one side would discard unrelated behavior.
- Observation: the merged upstream maker-fee payload needed a missing local namespace import after automatic merge.
  Evidence: the first post-merge compile warned that `contracts-constants/maker-fee-bps` was undeclared; adding the existing `contracts.constants` require removed the warning and restored all optimizer unit tests.
- Observation: the full Playwright suite is not green in this environment even though the targeted upstream staking suite is.
  Evidence: 216 passed, 22 failed, 7 flaky, and 4 skipped; failures span builder-fee, mobile layout, close-all, referrals, optimizer prefetch, and unrelated trade flows. The design-review runner was also blocked by a stale Shadow/local-app startup state after Playwright.

## Decision Log

- Decision: Perform a no-push, no-deploy merge on `codex/upstream-sync-20260806` using local `main` as the starting point.
  Rationale: the request is a code synchronization review; isolating the result protects the release branch and permits rollback.
  Date/Author: 2026-08-06 / `spec_writer`.
- Decision: Resolve shared source and test conflicts by composing behavior, keeping upstream safety/optimizer fixes and local DEXHelm/runtime contracts where both apply; never use blanket `ours` or `theirs` for the whole merge.
  Rationale: 25 overlapping files cross independent ownership boundaries, so a side-wide choice can silently remove safety checks or release policy.
  Date/Author: 2026-08-06 / `spec_writer`.
- Decision: Preserve local exact dependency pins, security overrides, and Cloudflare scripts unless a gate demonstrates a concrete compatibility requirement; make any exception explicit in this plan.
  Rationale: local release work deliberately constrains supply-chain drift, while upstream's `c8` range change is not by itself evidence that local security policy should be weakened.
  Date/Author: 2026-08-06 / `spec_writer`.
- Decision: Do not add, clean, regenerate into, or otherwise mutate `test-results/`.
  Rationale: it is pre-existing untracked user output explicitly outside the synchronization scope.
  Date/Author: 2026-08-06 / `spec_writer`.
- Decision: Retain local exact npm pins and lockfile instead of adopting upstream's `c8` range.
  Rationale: the local security contract rejects dependency ranges and the resulting lockfile passed `npm install --package-lock-only --offline` plus `npm run check`.
  Date/Author: 2026-08-06 / `root`.
- Decision: Add only the missing maker-fee namespace import; do not reintroduce local-parent features that were intentionally removed before this synchronization.
  Rationale: the import is required by the upstream change and was a concrete merge regression, while restoring unrelated inverse-volatility surfaces would exceed the requested update scope.
  Date/Author: 2026-08-06 / `root`.

## Outcomes & Retrospective

The upstream main history is integrated on `codex/upstream-sync-20260806`. Conflicts were limited to badges, dependency manifests, namespace-size exceptions, funding network selection, and app-view imports; local Bridge2/product-context behavior and upstream staking/optimizer changes were composed. The required code gates are green. Targeted staking browser coverage is green, while the broad Playwright suite and design-review launcher remain environment/legacy-flow risks documented above. No Cloudflare or remote mutation was performed.

## Context and Orientation

The repository is a ClojureScript application with JavaScript tooling. `src/hyperopen/staking/**`, `src/hyperopen/api/projections/staking.cljs`, and their tests implement staking account selection, locking, freshness, and undelegation behavior. `src/hyperopen/portfolio/optimizer/**` and `src/hyperopen/views/portfolio/optimize/**` implement optimization planning and execution rendering; upstream changes stabilize execution slots and repricing. Runtime adapters and schema registration under `src/hyperopen/runtime/**` and `src/hyperopen/schema/**` are strict contracts, so related action/effect changes must remain synchronized with tests and `test/test_runner_generated.cljs`.

The local parent additionally owns the DEXHelm white-label and Cloudflare release boundary: `workers/hyperopen-worker.mjs`, `wrangler.jsonc`, `wrangler.mainnet-opening.jsonc`, `config/white-label/**`, `resources/public/brand/**`, `tools/cloudflare/**`, and the active DEXHelm plans. These files must remain available and behaviorally unchanged unless a real merge conflict requires a documented composition. `tools/browser-inspection/**` and `tools/playwright/test/staking-regressions.spec.mjs` are upstream QA surfaces; browser work is deterministic Playwright work, not exploratory Browser MCP work.

## Plan of Work

First snapshot the user-owned untracked output and confirm the tracked worktree has no unrelated edits. Create or switch to `codex/upstream-sync-20260806` from local `main`; do not reset, clean, or checkout away any user files. Merge `origin/main` with a non-fast-forward merge so the two parent commits remain auditable.

During conflict resolution, inspect each conflict with `git diff --cc` and the two parent versions. Keep upstream staking account scoping, undelegation lock/read-only guards, freshness handling, optimizer execution-slot/repricing logic, dead-code removals, and their tests. Preserve the local Cloudflare/DEXHelm worker, tenant, Wrangler, release-tool, security, and active-plan files. For the shared `package.json` and `package-lock.json`, combine all scripts and use a lockfile consistent with the resulting manifest; retain local exact pins and security overrides unless compatibility evidence and a recorded decision justify adopting an upstream range. Regenerate `test/test_runner_generated.cljs` only through the repository generator, never by hand. Do not resolve any conflict by deleting a safety or release path merely to make the merge clean.

After the merge, inspect the staged diff and ancestry, run the focused tests, then run the full gates. If a gate fails, isolate whether the failure is a merge regression or an environment/bootstrap issue, fix only in the allowed implementation branch, and update `Progress`, `Surprises & Discoveries`, and `Decision Log` before retrying. Do not push, deploy, or alter the user's untracked output.

## Concrete Steps

Run these commands from `/Users/zh/Documents/Hyperopen`.

1. Record the pre-merge artifact state without changing it:

        git status --short --untracked-files=all
        find test-results -type f -print0 | sort -z | xargs -0 shasum > /tmp/hyperopen-upstream-sync-test-results.before

   The status must show only the pre-existing `test-results/` entries. If any tracked or unrelated change appears, stop and hand it back for clarification.

2. Isolate and merge the histories:

        git switch main
        git switch -c codex/upstream-sync-20260806
        git merge --no-ff origin/main -m "Merge origin/main into codex/upstream-sync-20260806"

   If the branch already exists, verify it points to the intended local starting commit before continuing; never force-reset it. For conflicts, use `git status`, `git diff --cc`, and file-scoped edits, then `git add` only resolved files and finish the merge with `git commit`.

3. Bootstrap and validate the resulting worktree:

        npm run setup:worktree
        npm run test:runner:generate
        npm test
        npm run test:websocket
        npm run check
        npm run gates

   `npm run gates` should report PASS for `check`, `test`, and `test:websocket`; the component commands provide actionable logs if the summary fails. The generator must leave no unexpected generated-test diff after it is run.

4. Exercise the changed deterministic browser contract. After installing browsers if needed, run the smallest relevant suite first and clean browser-inspection sessions afterward:

        npm run test:playwright:install
        npx playwright test tools/playwright/test/staking-regressions.spec.mjs
        npm run test:playwright:ci
        npm run browser:cleanup

   A local dev server may be started by the Playwright configuration; Playwright must exit cleanly. Do not create a Browser MCP session for this synchronization-only validation.

5. Prove ancestry, local release retention, and artifact preservation:

        git merge-base --is-ancestor 7652651b443d8fa7a9b1d85f49a0f966eb1d3b9b HEAD
        git merge-base --is-ancestor d708a545dd45aa9923b0936edb350efca9172e05 HEAD
        git diff --exit-code 7652651b443d8fa7a9b1d85f49a0f966eb1d3b9b HEAD -- workers config/white-label resources/public/brand tools/cloudflare .agents/skills/deploy-hyperopen-cloudflare wrangler.jsonc wrangler.mainnet-opening.jsonc
        find test-results -type f -print0 | sort -z | xargs -0 shasum > /tmp/hyperopen-upstream-sync-test-results.after
        cmp /tmp/hyperopen-upstream-sync-test-results.before /tmp/hyperopen-upstream-sync-test-results.after
        git status --short --untracked-files=all

   Both ancestry checks and `cmp` must exit 0. The local release diff must be empty for paths untouched by upstream, and `test-results/` must remain the same untracked user output. Review `git diff HEAD^1 HEAD` and `git log --graph --oneline --decorate -20` before handoff.

## Validation and Acceptance

The synchronization is accepted only when all of the following are observable on `codex/upstream-sync-20260806`:

- `git merge-base --is-ancestor` succeeds for both `7652651b` and `d708a545`, and the graph shows a merge commit with both parents.
- `npm run gates` passes its `check`, `test`, and `test:websocket` rows; the direct commands report successful completion with no compile, contract, or generated-runner failure.
- The targeted staking Playwright suite and the full committed Playwright suite pass, with Playwright exiting cleanly and `npm run browser:cleanup` leaving no tracked browser sessions.
- Upstream staking tests cover account scoping, lock/read-only undelegation, freshness, and master-account regression behavior, while optimizer execution tests cover stable slots/repricing; these behaviors are included through the merged upstream source and test files and remain green in the gates.
- `workers/hyperopen-worker.mjs`, Wrangler configs, white-label DEXHelm configs/assets, `tools/cloudflare/**`, and the local deployment skill/plans remain present and retain their local behavior; no Cloudflare command is run.
- The before/after `test-results/` hash manifests compare equal, and the final status shows those files still untracked rather than staged or deleted.
- No `git push`, `git pull --rebase`, deploy, wallet, signing, or real-fund action occurs.

## Idempotence and Recovery

All inspection and test commands are repeatable. If the merge conflicts, leave the branch in its conflicted state while investigating; use `git merge --abort` only to return to the newly created branch before retrying with a corrected resolution. Do not use `git reset --hard`, `git clean`, or broad checkout commands. If a generated file or lockfile changes solely because a command was run, inspect it and either commit the deterministic generated result or restore only that command-owned change with an explicit, file-scoped operation. The original local `main` commit remains a rollback point, and no remote state changes are authorized.

## Artifacts and Notes

The final handoff must include the active plan path, merge commit hash, conflict list and decisions, the `npm run gates` PASS/FAIL matrix, targeted/full Playwright result, the two artifact-manifest comparison result, and any unresolved risk. Keep command transcripts concise and update this section as evidence becomes available.

## Interfaces and Dependencies

No new public API is designed by this synchronization. Existing action/effect/schema contracts remain authoritative across `src/hyperopen/runtime/**`, `src/hyperopen/schema/**`, and generated test registration. The resulting JavaScript dependency graph must be represented by the merged `package.json` and `package-lock.json`; use the repository's npm version and `npm run setup:worktree` bootstrap path. Upstream ClojureScript modules and tests must compile under the existing `shadow-cljs` targets invoked by `npm run check`, `npm test`, and `npm run test:websocket`.

Revision note (2026-08-06): created this active plan after inspecting the two parent histories and repository planning/testing contracts; the scope explicitly protects local DEXHelm/Cloudflare work and the pre-existing `test-results/` artifact.
