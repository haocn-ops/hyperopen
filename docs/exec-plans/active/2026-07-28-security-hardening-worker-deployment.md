# Publish security hardening and update the existing Hyperopen Worker

This ExecPlan is a living deployment record. It follows `docs/PLANS.md` and the repository-owned `deploy-hyperopen-cloudflare` skill.

## Purpose / Big Picture

Publish the validated security-hardening worktree to the user-owned GitHub fork and deploy the same reviewed source to the existing Cloudflare Worker. Preserve the current DEXHelm custom domains, keep Mainnet closed, capture a rollback version, and prove the public static, header, host, and Testnet proxy contracts after deployment.

## Context References

Repo artifacts:

- `docs/exec-plans/completed/2026-07-28-defense-in-depth-security-and-release-preflight.md`
- `docs/exec-plans/completed/2026-07-28-trusted-types-and-ci-supply-chain-hardening.md`

The direct user request on 2026-07-28 authorizes pushing the current branch to GitHub and deploying the configured Worker; it does not authorize Mainnet opening or live wallet actions.

## Progress

- [x] (2026-07-28T02:12Z) Recorded source and remote baseline: branch `codex/trading-recovery-modal`, HEAD and fork branch `2347c7d6a726a568ec501720afd1944566a44d20`, upstream `thegeronimo/hyperopen`, user fork `haocn-ops/hyperopen`, AGPL-3.0 license preserved.
- [x] (2026-07-28T02:12Z) Recorded deployment baseline: Worker `hyperopen`, account `a95e39ff9f1a66e7630e6639a0edb86c`, prior version `41fd0435-ae88-41a6-8497-ef429e41f035`, `workers_dev=false`, asset root `out/white-label/dexhelm`.
- [x] (2026-07-28T02:58Z) Passed repository preflight (25 checks), artifact preflight (33 checks), Cloudflare Worker tests (32/32), release-asset tests (51/51), SEO Playwright (7/7), DEXHelm validation and verification, and the 375/768/1280/1440 branded Playwright matrix (4/4).
- [x] (2026-07-28T02:58Z) Rebuilt the selected DEXHelm artifact with all four ClojureScript release targets at 0 warnings; rewrote five Mainnet endpoints to the disabled sentinel and one Testnet endpoint to the same-origin proxy; final config digest `713FD5F0B51F2D7EED151F7B1EDF2B5ED8EE70629F1BB7AED7C16DA93A29C4D9`.
- [x] (2026-07-28T02:58Z) Verified local Wrangler with `--local-upstream testnet.dexhelm.com`: 24 `_headers` rules parsed, `/trade` 200, unknown route 404, `/api/health` 200 JSON with `no-store`, Testnet fee probe 200 JSON, and Mainnet/generic proxy probes 404. Cloudflare dry-run passed with 57 assets.
- [x] (2026-07-28T03:02Z) Passed the final 35/35 gate matrix outside the loopback-restricted sandbox: 6,590 tests, 35,839 assertions, all ClojureScript compile targets at 0 warnings.
- [ ] Commit the complete reviewed worktree and push `codex/trading-recovery-modal` to the user fork.
- [ ] Deploy a new version of the same Worker and verify every configured public host plus security/proxy contracts.
- [ ] Record the final commit, Worker version, public evidence, rollback state, and residual risks; archive this plan.

## Surprises & Discoveries

- Wrangler local mode preserves the exact-host Worker policy only when started with `--local-upstream testnet.dexhelm.com`; without that flag, the hardened Worker correctly rejects `localhost` with 404.
- Wrangler rewrites a same-origin CSP source from `https://testnet.dexhelm.com` to the local preview origin in its served `_headers` response. The generated `_headers` file and public tenant manifest both retain the production HTTPS origin, so the exact production CSP remains subject to the post-deploy verifier.
- The first gate pass proved all ClojureScript targets and 6,397 ClojureScript tests but could not bind three Node HTTP fixtures under the sandbox. The failure was `listen EPERM 127.0.0.1`, not an assertion or product-code failure, and requires the same gate command in the approved local environment.

## Decision Log

- Decision: push to the existing `fork` remote rather than upstream `origin`.
  Rationale: the authenticated GitHub identity owns `haocn-ops/hyperopen`; the upstream repository is not the requested publication target.
  Date/Author: 2026-07-28 / Codex.

- Decision: deploy the checked-in DEXHelm configuration without changing routes, DNS, secrets, or Mainnet policy.
  Rationale: the request authorizes an existing Worker update, not a new launch or Mainnet opening.
  Date/Author: 2026-07-28 / Codex.

## Deployment Invariants

The deployment must keep `workers_dev=false`; preserve `dexhelm.com`, `testnet.dexhelm.com`, `app.dexhelm.com`, and `status.dexhelm.com`; serve assets from `out/white-label/dexhelm`; keep Mainnet host intentionally closed; expose no Cloudflare credentials; and leave source development endpoints unchanged. A deploy is successful only when Wrangler reports a new version for Worker `hyperopen` and both repository public verifiers pass against the Testnet origin.

## Validation And Recovery

Run the skill's safe-order commands with JDK 21 and the Node local-storage workaround where required. Before upload, require artifact preflight, white-label validation/verification, release and Cloudflare tests, browser acceptance, Wrangler dry-run, and `npm run gates`. After upload, require the four-host status matrix, logo response, document headers, health endpoint, Testnet fee probe, and closed Mainnet/generic proxy probes. If public verification fails, stop and request explicit rollback authorization to version `41fd0435-ae88-41a6-8497-ef429e41f035`; do not delete the Worker or modify DNS.

## Outcomes & Retrospective

Deployment is in progress. At completion record the Git commit, Worker version ID, exact public URLs, verification results, `_headers` handling, bundle warning, Mainnet state, rollback evidence, and any operational follow-up.

Revision note: created on 2026-07-28 immediately before authorized GitHub and Cloudflare mutations.
