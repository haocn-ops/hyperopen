# Publish the upstream-synchronized Hyperopen release

This ExecPlan records the requested review, GitHub upload, and existing Worker update after
merging `origin/main` into `codex/upstream-sync-20260806`. It is a deployment record, not
authorization for Mainnet, wallet, DNS, or custom-domain changes.

## Purpose / Big Picture

Publish the reviewed upstream-synchronized code and the verified DEXHelm Testnet artifact to
the existing `hyperopen` Cloudflare Worker. The release must retain the current Worker name,
four configured DEXHelm custom domains, Testnet-only HyperUnit proxy, and `workers_dev=false`.

## Context References

- User request on 2026-08-06: review the upstream update, upload to GitHub, and deploy the Worker.
- Source branch: `codex/upstream-sync-20260806`.
- Merge commit: `ec6f749a`; documentation closure commit: `e9f5505c`.
- Deployment skill: `.agents/skills/deploy-hyperopen-cloudflare/SKILL.md`.
- Worker config: `wrangler.jsonc`; authoritative assets: `out/white-label/dexhelm`.

## Progress

- [x] Static review found no release-blocking correctness, security, or merge-composition issue.
- [x] Worker tests passed 50/50 and release-asset tests passed 52/52.
- [x] DEXHelm white-label validation passed; Playwright passed at 375, 768, 1280, and 1440 px.
- [x] Corrected repository gates passed 35/35 (6,691 tests, 36,502 assertions).
- [x] Final Cloudflare build, artifact preflight (33/33), and Wrangler dry-run passed.
- [x] Pushed `codex/upstream-sync-20260806` to the user fork.
- [ ] Deploy the existing Worker, verify the returned version and public host matrix.
- [ ] Record final evidence and move this plan to `completed/`.

## Decision Log

- Keep the deployment on the existing `hyperopen` Worker and use the latest verified version
  `4bdd9636-2e89-4f4f-8e49-7a4a198959eb` as rollback baseline.
- Push only to `fork` (`https://github.com/haocn-ops/hyperopen.git`), never to canonical `origin`.
- Preserve Testnet-only routing and the intentional `app.dexhelm.com` Mainnet-closed policy.

## Release Evidence Before Deploy

- Cloudflare dry-run read 57 assets from `out/white-label/dexhelm` and exited 0.
- `git push fork codex/upstream-sync-20260806` created the matching GitHub branch.
- Cloudflare account: Izhenghaocn@gmail.com's Account (`a95e39ff9f1a66e7630e6639a0edb86c`).
- Rollback baseline: Worker version `4bdd9636-2e89-4f4f-8e49-7a4a198959eb`.

## Validation and Acceptance

- `node .../preflight.mjs --artifact` passes with only documented environment warnings.
- `npm run cloudflare:check` exits 0 and prepares the configured asset directory.
- `git push fork codex/upstream-sync-20260806` succeeds.
- `npm run deploy:cloudflare` updates Worker `hyperopen` and returns a new version ID.
- Public `testnet.dexhelm.com` and its enabled routes verify successfully; apex/status remain
  available and `app.dexhelm.com` remains intentionally closed.
- Both repository public verifiers pass without printing response bodies.

## Recovery

If public verification fails, stop further mutations and request explicit rollback using the
recorded prior version ID. Do not delete the Worker or alter DNS/custom-domain configuration.

## Revision Note

Created 2026-08-06 after the upstream merge review and before any GitHub or Cloudflare write.
