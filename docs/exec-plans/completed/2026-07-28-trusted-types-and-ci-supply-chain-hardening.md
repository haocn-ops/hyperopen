# Enforce Trusted Types and harden the CI supply chain

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current. It follows `docs/PLANS.md` and `.agents/PLANS.md`.

## Purpose / Big Picture

The preceding defense-in-depth work blocks authored HTML sinks, but the generated renderer and chart library still contain two `innerHTML` paths and the release does not enforce Trusted Types. The CI workflows also execute code obtained through floating action tags, grant the main test job repository write access, execute npm lifecycle scripts, and download Babashka without verifying its digest. After this plan, release browsers will reject unapproved HTML and string-to-code assignments at runtime, and CI will run with immutable action revisions, read-only repository authority, verified tool downloads, and npm lifecycle scripts disabled.

The work is observable through release Playwright: the real `/trade` route must load with the enforcement header, the TradingView attribution must remain present, and an attempted arbitrary `innerHTML` assignment must throw. CI contract tests must reject a floating action, write permission, unchecked download, or ordinary `npm ci` command. This plan does not deploy or push.

## Context References

Public refs: direct user request on 2026-07-28 to continue remediating the residual risks recorded after the defense-in-depth security plan.

Repo artifacts: `docs/exec-plans/completed/2026-07-28-defense-in-depth-security-and-release-preflight.md`, `docs/SECURITY.md`, `tools/release-assets/security_headers.mjs`, `resources/public/theme-preload.js`, `.github/workflows/tests.yml`, `.github/workflows/playwright.yml`, and `.github/workflows/security.yml`.

Local scratch refs (non-authoritative): none.

## Progress

- [x] (2026-07-28T00:00Z) Inspected the two generated vendor sinks, current release CSP, signing-key boundary, bundle warning, Browserslist versions, and all GitHub Actions workflow authorities.
- [x] (2026-07-28T00:00Z) Resolved official action v4 tags to immutable commits and obtained the official Babashka 1.12.197 Linux amd64 static archive SHA-256.
- [x] (2026-07-28T01:34Z) Added RED contracts for Trusted Types, release-browser enforcement, and CI supply-chain invariants.
- [x] (2026-07-28T01:52Z) Implemented the narrow Trusted Types policy and exact CSP directives without removing TradingView attribution; browser discovery added a strict fingerprinted Shadow-module URL allowlist.
- [x] (2026-07-28T01:36Z) Pinned CI actions, removed write authority and badge push logic, verified Babashka before extraction, and disabled npm lifecycle scripts.
- [x] (2026-07-28T01:59Z) Refreshed Browserslist metadata without changing direct dependency intent and reviewed the isolated 37-addition/20-deletion lock delta.
- [x] (2026-07-28T02:09Z) Passed focused tests, two deterministic release builds, Playwright 7/7, security scans, the 35/35 gate matrix, and final read-only review.

## Surprises & Discoveries

- Observation: the generated main renderer sink is Replicant compatibility code, not an authored raw-HTML feature.
  Evidence: Replicant 2025.06.21 clears an uninitialized render root with `innerHTML = ""` and contains a generic `innerHTML` attribute branch, while `src/hyperopen/**` contains no `:innerHTML` use. The existing authored-source contract reports zero sinks.

- Observation: the chart sink writes one fixed TradingView attribution SVG required by the package's attribution notice.
  Evidence: Lightweight Charts 5.0.8 assigns its module constant `svg` to the `tv-attr-logo` anchor. Disabling it without adding an equivalent user-visible TradingView link would weaken license compliance.

- Observation: putting signing logic in a Web Worker would not isolate it from arbitrary same-origin code.
  Evidence: hostile same-origin script can send the same worker messages or invoke the application's existing signing commands. Real isolation would require per-operation user verification or an external signer, which changes trading UX and is not an honest implementation detail.

- Observation: CI authority is broader than its test purpose.
  Evidence: `.github/workflows/tests.yml` grants `contents: write` to every dependency and test step; both test workflows use floating `actions/*@v4`, run `npm ci` with lifecycle scripts, and the Babashka download has no checksum. `.github/workflows/playwright.yml` also contains unreachable badge-push code and a stray `fi`.

- Observation: the 655,007-byte gzip main bundle is a performance warning, not a security boundary.
  Evidence: `tools/release-assets/bundle-budget.json` intentionally defines a 640,000-byte soft advisory, and `docs/exec-plans/active/2026-06-11-pagespeed-desktop-performance-90.md` owns evidence-driven bundle reduction. This plan must not increase the budget or claim that hiding the warning fixes security.

- Observation: `require-trusted-types-for 'script'` also covers the Shadow loader's dynamic `HTMLScriptElement.src` assignments.
  Evidence: the first release-browser run failed closed before `trade_chart` loaded. The final policy accepts only `/js/<reviewed-module>.<32 uppercase hex>.js`; absolute URLs, `main`, unknown modules, lowercase hashes, query strings, and traversal-like paths are rejected. Chromium then loaded the chart and TradingView attribution without page errors.

- Observation: the refreshed top-level Browserslist data is current, while Tailwind 3.4.17 embeds an independent stale snapshot and still prints one generic warning.
  Evidence: the resolved `caniuse-lite` 1.0.30001806 data reaches 2026-07-02 and is used by Autoprefixer. Tailwind's bundled `peers/index.js` contains its own old dataset. Suppressing the warning or upgrading to breaking Tailwind 4 would not be an honest security fix, so the message remains a tooling/performance-maintenance concern.

## Decision Log

- Decision: enforce `require-trusted-types-for 'script'` with a default policy that accepts only the empty renderer-clear value and the exact reviewed TradingView SVG.
  Rationale: the default policy is required because the two dependencies assign strings rather than `TrustedHTML`. An exact two-value allowlist preserves compatibility without permitting caller-controlled markup; all other HTML and script strings throw. A wildcard, prefix match, generic sanitizer, or echo policy is forbidden.
  Date/Author: 2026-07-28 / Codex.

- Decision: preserve the TradingView attribution instead of switching off the chart option.
  Rationale: Lightweight Charts requires an attribution notice and TradingView link on a user-visible page. The current fixed logo meets that requirement and can be safely allowlisted exactly.
  Date/Author: 2026-07-28 / Codex.

- Decision: remove badge auto-push behavior from test workflows and make all workflow permissions read-only.
  Rationale: repository write authority must not be available to third-party actions, npm packages, compilers, or tests. Badge files are not worth exposing a write token. A future badge publisher must be a separately scoped job with reviewed inputs and explicit write permission.
  Date/Author: 2026-07-28 / Codex.

- Decision: refresh only transitive Browserslist metadata and review the lock diff.
  Rationale: stale browser targets can produce incorrect compatibility output, but this task must not opportunistically upgrade direct application dependencies. Lifecycle scripts remain disabled.
  Date/Author: 2026-07-28 / Codex.

- Decision: add a narrow `createScriptURL` callback after release-browser discovery, superseding the initial no-callback assumption.
  Rationale: Shadow's dynamic module loader necessarily assigns script URLs under Trusted Types enforcement. An explicit module-name allowlist plus an exact fingerprinted same-origin path grammar preserves lazy loading without admitting caller-chosen URLs or Closure's permissive `goog#html` policy.
  Date/Author: 2026-07-28 / Codex.

## Outcomes & Retrospective

Completed. Release CSP now enforces `require-trusted-types-for 'script'; trusted-types default`. The default policy accepts only empty renderer reset HTML, the exact Lightweight Charts 5.0.8 attribution SVG, and reviewed fingerprinted Shadow module URLs. Playwright passed 7/7 against the built `/trade` route with no page errors, a present `#tv-attr-logo`, rejected arbitrary `innerHTML`, and rejected duplicate/unapproved policies.

All three workflows have read-only contents authority. Five GitHub actions are fixed to reviewed 40-character commits, npm lifecycle scripts are disabled, the Babashka archive is checked against `1fff1d97fa08b6b43cb9b4f8726a1c72c3115a15611ab1248d3d57c3c70ed908` before extraction, and badge auto-push was removed. Browserslist resolved to 4.28.7, caniuse-lite to 1.0.30001806, and update-browserslist-db to 1.2.3; the isolated lock refresh changed only their expected transitive graph.

`npm run security:check` reported zero npm vulnerabilities and scanned all 62 Maven coordinates through OSV. Release resources passed 51/51, Cloudflare tests 32/32, and the final gate matrix passed 35/35 with 6,590 tests and 35,839 assertions; all ClojureScript targets compiled with zero warnings. Two release builds produced identical SHA-256 values for main JS (`8f1dbd...fea1e`), CSS (`613fe4...17d4`), `_headers` (`d44adc...325d`), and `trade.html` (`6f5b53...64bbf`). Main gzip remains 655,007 bytes against the separate 640,000 soft performance budget.

Irreducible risks remain: already-executing same-origin first-party code can call the application's signing capabilities; browser extensions, CI platform compromise, and unknown zero-days are outside repository enforcement; eliminating the unlocked signing window requires per-operation user verification or an external/hardware signer. No deployment or remote push was performed, so the release controls take effect publicly only after a separately authorized deployment.

## Context and Orientation

`resources/public/theme-preload.js` is the only approved inline release script and executes before the hashed application bundles. Its exact bytes are hashed by `tools/release-assets/security_headers.mjs`, so it is the correct place to create the narrow Trusted Types default policy before Replicant initializes. Trusted Types is a browser mechanism that makes HTML- and script-parsing sinks reject ordinary strings when the CSP contains `require-trusted-types-for 'script'`.

`tools/release-assets/security_headers.mjs` generates the exact release CSP and `_headers`. `tools/playwright/static_server.mjs` applies those headers locally, so `tools/playwright/test/seo.smoke.spec.mjs` can prove the policy works in Chromium against the generated release rather than testing only a string.

The GitHub workflow files under `.github/workflows/` run untrusted dependency code in CI. Pinning an action to a 40-character commit prevents its tag from moving. `npm ci --ignore-scripts` installs the reviewed lock without executing package lifecycle hooks. The local dependency contract already inventories every package declaring an install script, so CI does not need to execute those scripts merely to compile and test this repository.

## Plan of Work

First extend `tools/security/release_xss_contract.test.mjs` and the release-header tests with fixtures that require both Trusted Types directives, require the early default policy, accept only the two exact compatibility values, and reject an echo or wildcard policy. Add a CI workflow contract under `tools/security/` that parses workflow text conservatively and rejects non-SHA action references, write permissions, plain `npm ci`, unchecked Babashka downloads, and push commands.

Then update `resources/public/theme-preload.js` to create the `default` policy when `globalThis.trustedTypes` exists. Its `createHTML` callback returns only `""` or the exact Lightweight Charts 5.0.8 attribution SVG; all other values throw `TypeError`. Browser discovery established that Shadow's loader also requires `createScriptURL`, so that callback accepts only an explicit module-name allowlist under `/js/` with an exact 32-character uppercase release fingerprint and no query, fragment, traversal, or origin. Do not add `createScript`, a sanitizer, a prefix/regular-expression HTML allowlist, or expose the policy object globally. Add `require-trusted-types-for 'script'` and `trusted-types default` to the generated CSP.

Harden all workflows by replacing every `actions/*@v4` reference with the reviewed 40-character SHA plus a readable version comment. Set repository permissions to `contents: read`. Remove unreachable or write-oriented badge commit/push steps. Change npm installs to `npm ci --ignore-scripts` and run the local npm contract immediately afterward. Verify the Babashka archive against `1fff1d97fa08b6b43cb9b4f8726a1c72c3115a15611ab1248d3d57c3c70ed908` before extraction.

Refresh `browserslist`, `caniuse-lite`, and `update-browserslist-db` through an npm lock-only operation with lifecycle scripts disabled. Inspect the exact lock diff and reject any direct dependency or unrelated transitive churn. Run the npm contract and audits afterward.

Finally generate the release twice, run the release Playwright suite, and prove `/trade` loads without page errors, retains `#tv-attr-logo`, advertises the exact Trusted Types CSP, and rejects arbitrary `innerHTML`. Measure the bundle without changing its budget. Run security checks, Cloudflare/release tests, `npm run gates`, documentation lint, multi-agent tests, and `git diff --check`. Obtain a read-only final security review.

## Concrete Steps

Run from `/Users/zh/Documents/Hyperopen`:

    node --test tools/security/*.test.mjs
    npm run test:release-assets
    npm run build
    npm run test:playwright:seo
    npm run lint:bundle-budget
    npm run security:npm-contract
    npm run security:audit
    npm run security:clojure-tree:check
    npm run security:clojure-audit
    npm run gates
    npm run lint:docs
    npm run test:multi-agent
    git diff --check

Use JDK 21 and the Node 25 local-storage option documented by the preceding plan when required. Browser tests need approved local-loopback access. No deploy, push, or public probe is authorized.

## Validation and Acceptance

The browser boundary passes only when the generated CSP includes exact `require-trusted-types-for 'script'` and `trusted-types default` directives, the release route loads with no policy violation, TradingView attribution remains visible in the DOM, duplicate `default` and unapproved policy creation throw, and assigning attacker-controlled HTML to `document.body.innerHTML` throws. The policy source must have no generic pass-through.

The CI boundary passes only when every third-party action uses a reviewed 40-character commit, every job has read-only contents authority, Babashka is checked before extraction, npm installs ignore lifecycle scripts and run the local dependency contract, and no workflow contains `git push` or badge auto-commit behavior.

Browserslist refresh passes only if its staleness warning disappears, the npm dependency contract remains green, audits report no findings, and the lock diff contains only the intended transitive metadata changes. The bundle measurement is reported honestly; its separate active performance plan remains authoritative if the warning persists.

The full acceptance condition is two deterministic release builds, release Playwright green, focused security and release tests green, all network audits green, and `npm run gates` green. No warning is reported as a security success unless the corresponding boundary is actually enforced.

## Idempotence and Recovery

All validators are read-only. Repeating release generation and tests is safe. If the Trusted Types policy breaks a dependency path, identify the exact constant input and either replace the sink with DOM APIs or reject the milestone; do not widen the policy to caller-controlled markup. If the Browserslist lock refresh changes unrelated packages, restore only that lock operation's changes while preserving the user's existing dirty-worktree edits.

## Artifacts and Notes

Reviewed immutable action revisions:

    actions/checkout       11d5960a326750d5838078e36cf38b85af677262
    actions/setup-node     49933ea5288caeca8642d1e84afbd3f7d6820020
    actions/setup-java     c1e323688fd81a25caa38c78aa6df2d33d3e20d9
    actions/cache          0057852bfaa89a56745cba8c7296529d2fc39830
    actions/upload-artifact ea165f8d65b6e75b540449e92b4886f43607fa02

Official Babashka archive SHA-256:

    1fff1d97fa08b6b43cb9b4f8726a1c72c3115a15611ab1248d3d57c3c70ed908

## Interfaces and Dependencies

The inline policy must call `globalThis.trustedTypes.createPolicy("default", {createHTML, createScriptURL})` without exporting the result. `createHTML(value)` has exactly two accepted string values and throws otherwise. `createScriptURL(value)` accepts only reviewed fingerprinted Shadow module paths and throws otherwise.

Add a pure CI validator callable from Node tests with a stable shape equivalent to:

    validateWorkflowSecurity({filePath, source}) -> {actionPins, installs, permissions}

It must throw non-secret errors on a floating action, write permission, unverified download, lifecycle-enabled npm install, or push command. It must require no new npm dependency.

Revision note: created on 2026-07-28 after source, generated-artifact, CI, dependency, and browser-boundary discovery. Completed and archived on 2026-07-28 after release-browser discovery required a narrow fingerprinted module-URL callback. It deliberately separates enforceable security controls from the pre-existing performance budget and from product-level per-signature user verification.
