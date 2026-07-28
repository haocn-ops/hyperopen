# Add defense-in-depth XSS, dependency, and Testnet-only release safeguards

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. It follows `docs/PLANS.md` and `.agents/PLANS.md`.

## Purpose / Big Picture

The completed 2026-07-27 security work moved unsupported-passkey agent keys out of browser storage and made the public Worker Testnet-only. This follow-up makes those controls harder to regress. A release build will reject new application-owned HTML or string-to-code sinks, emit and verify a strict script policy, reject package and override drift before security scanning, and pass a Cloudflare preflight that agrees with the intentional `workers_dev=false` and no-Mainnet-binding policy.

After implementation, a maintainer can run the local security and release-readiness commands and see a deterministic pass only when the generated artifact, package lock, dependency overrides, Cloudflare configuration, and Testnet-only host policy agree. This plan deliberately does not publish a Worker, change DNS, open Mainnet, authenticate to Cloudflare, connect a wallet, or submit a trade.

No finite source review, CSP, lock file, audit service, or test suite can mathematically prove the absence of malicious code or unknown zero-day vulnerabilities. The outcome is a narrower, observable attack surface with fail-closed regression checks, not a claim of absolute security.

## Context References

Public refs: direct user request on 2026-07-28 to remediate the remaining security risks after `docs/exec-plans/completed/2026-07-27-residual-security-hardening.md`.

Repo artifacts: `docs/exec-plans/completed/2026-07-27-security-remediation.md`, `docs/exec-plans/completed/2026-07-27-residual-security-hardening.md`, `docs/SECURITY.md`, `docs/BROWSER_STORAGE.md`, `tools/release-assets/security_headers.mjs`, `tools/release-assets/verify_deployment_headers.mjs`, `tools/security/{sbom,clojure_tree,clojure_osv_scan}.mjs`, `.github/workflows/security.yml`, `package.json`, `package-lock.json`, `wrangler.jsonc`, `workers/hyperopen-worker.mjs`, `tools/cloudflare/verify_cloudflare_worker.mjs`, and `.agents/skills/deploy-hyperopen-cloudflare/`.

Local scratch refs (non-authoritative): none.

## Progress

- [x] (2026-07-28T00:00Z) Reviewed the completed residual-hardening record, current CSP/release artifact, dependency-security tooling, Cloudflare preflight, and the Testnet-only Worker configuration.
- [x] (2026-07-28T00:00Z) Reproduced the release-preflight contract mismatch: `node .agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs` reports exactly two policy failures, requiring `workers.dev` and a Mainnet binding that the reviewed Worker intentionally removed.
- [x] (2026-07-28T00:00Z) Validated this active plan with `git diff --check` and `npm run lint:docs`; no whitespace errors were reported and the documentation guard passed.
- [x] (2026-07-28T00:00Z) Added RED fixtures for the release XSS/CSP contract, application-owned sink baseline, and the Testnet-only preflight/artifact contract; the final focused security suite passes 17/17 tests.
- [x] (2026-07-28T00:00Z) Added RED Node fixtures for exact direct dependency pins, package-lock agreement, override resolution drift, and reviewed install-script packages.
- [x] (2026-07-28T00:00Z) Implemented the XSS/CSP guardrail, dependency/override contract, security workflow integration, and Testnet-only preflight/documentation repair.
- [x] (2026-07-28T00:00Z) Completed focused tests, two release builds, artifact preflight, Cloudflare dry-run, Playwright, npm/Maven scans, and `npm run gates`; all required checks passed without deployment.
- [x] (2026-07-28T00:00Z) Completed the final read-only security review, fixed its ClojureScript sink and Worker HSTS findings, reconciled the pre-existing Wrangler 4.114.0 upgrade against the prior completed security plan, and reran all affected checks.

## Surprises & Discoveries

- Observation: the deployment skill's preflight is stale relative to the completed security policy.
  Evidence: `wrangler.jsonc` sets `"workers_dev": false` and supplies only `HYPERUNIT_TESTNET_URL`; the preflight's `checkWranglerContract` still requires `workers_dev === true` and `HYPERUNIT_MAINNET_URL === "https://api.hyperunit.xyz"`. On 2026-07-28 it produced 28 passes, 2 warnings, and 2 failures with those exact failure labels.

- Observation: memory-only storage removes persistence exposure but cannot defend a live document from arbitrary same-origin JavaScript execution.
  Evidence: the in-memory `agent-lockbox` is the live signing boundary documented in `docs/SECURITY.md`; a successfully injected same-origin script can still interact with the same document and its application capabilities until disable, disconnect, or reload clears the cache.

- Observation: the current release policy already forbids arbitrary script sources and inline script execution, but it does not have a deterministic application-sink inventory.
  Evidence: `tools/release-assets/security_headers.mjs` produces `script-src 'self' <theme hash>` with no `unsafe-eval` or `unsafe-inline`; source inspection finds `src/hyperopen/ui/fx.cljs` assigns an empty string to `innerHTML`, while the generated release contains framework and `lightweight-charts` sink internals. A blanket Trusted Types enforcement would therefore require a compatibility migration and must not be claimed as an existing control.

- Observation: npm installation is lockfile-based in CI but the direct dependencies and dev dependencies in `package.json` still use caret ranges, and no repository command proves every declared override resolves to the reviewed version.
  Evidence: `package-lock.json` is lockfile version 3 and `npm ci` is used by the GitHub workflows, but all current direct production dependencies except `snabbdom` use a `^` range. `package.json` overrides `@hono/node-server`, `brace-expansion`, `glob`, and `minimatch`; the installed lock currently resolves them to `2.0.12`, `5.0.8`, `13.0.6`, and `10.2.5` respectively, without a dedicated contract test.

- Observation: network scanners are valuable but are not deterministic local proof.
  Evidence: `npm audit` and `tools/security/clojure_osv_scan.mjs` intentionally fail on registry, DNS, transport, or OSV response failures. Their inability to execute is a non-green security result, not evidence of no vulnerability.

- Observation: four selected npm packages contain lifecycle install scripts and therefore require explicit review even though CI installs with scripts disabled.
  Evidence: the dependency contract verified the exact allowlist `esbuild@0.28.1`, `fsevents@2.3.2`, `fsevents@2.3.3`, and `workerd@1.20260722.1`; any new or changed install-script package now fails closed.

- Observation: the authored-source sink inventory is empty, while two generated vendor/framework sinks remain.
  Evidence: both release builds reported zero authored sinks and inventoried `innerHTML` in the generated main renderer bundle and trading-chart bundle. This is why Trusted Types enforcement remains a separate migration rather than a header-only change.

- Observation: the security patch did not address two non-blocking maintenance warnings.
  Evidence: Playwright reported the release gzip bundle at 655,007 bytes, 15,007 bytes over the 640,000-byte soft budget, and Browserslist data is 13 months old. Neither warning changes the security contract validated here.

- Observation: the first final review found that JavaScript-form sink patterns did not cover equivalent ClojureScript interop forms, and that Worker-generated HTML responses did not inherit the static release HSTS header.
  Evidence: fixtures for `js/eval`, `.write js/document`, `js/Function.`, and `.insertAdjacentHTML` initially passed the authored-source scanner; direct apex, status, and Mainnet-closed Worker responses returned no `Strict-Transport-Security`. The scanner now rejects those ClojureScript forms and the Worker emits `max-age=31536000; includeSubDomains` on all three document paths. Security tests pass 17/17 and Cloudflare tests pass 32/32 after the fixes.

- Observation: the final review initially classified Wrangler 4.114.0 as an upgrade introduced by this milestone, but repository history shows it was an intentional input from the immediately preceding completed security plan.
  Evidence: `docs/exec-plans/completed/2026-07-27-residual-security-hardening.md` records the reviewed 4.114.0 pin and patched transitive overrides. Reverting it to the older HEAD value would discard an already authorized user change, so this milestone preserves and validates 4.114.0 rather than treating it as a new dependency upgrade.

## Decision Log

- Decision: protect against regression of application-owned parser/code sinks and keep the existing strict script CSP rather than claiming that CSP eliminates same-origin compromise.
  Rationale: CSP blocks a large class of injected external and inline script execution, but a compromised first-party asset or a browser flaw can still be same-origin. Removing direct application-owned sinks, forbidding their reintroduction, and testing the generated release/header contract is an implementable defense-in-depth improvement. It does not overstate what the browser can guarantee.
  Date/Author: 2026-07-28 / Codex.

- Decision: do not add `require-trusted-types-for 'script'`, a permissive default Trusted Types policy, or a sanitizer dependency in this milestone.
  Rationale: the current compiled renderer and chart dependency write `innerHTML`; a permissive policy would only make the header look stronger while allowing the dangerous paths. Enforced Trusted Types needs a separately scoped compatibility migration with browser evidence for every release route. This plan removes the one authored sink and blocks new authored sinks while recording the framework/vendor inventory as residual risk.
  Date/Author: 2026-07-28 / Codex.

- Decision: pin direct npm dependency and development dependency versions exactly and validate the package/lock/override contract without downloading or executing package install scripts.
  Rationale: `npm ci --ignore-scripts` is deterministic only after a reviewed lockfile exists. Exact direct declarations make intent visible in review, while a local parser can reject a stale root lock entry, a range, unsupported override syntax, or an override that is not the selected resolved version. This is simpler and more reliable than trusting a successful install alone.
  Date/Author: 2026-07-28 / Codex.

- Decision: make the deployment skill's preflight and instructions follow the reviewed Testnet-only policy, including no `workers.dev` publishing and no Mainnet Worker binding.
  Rationale: a preflight must fail on an unsafe configuration and pass on the approved one. Requiring obsolete Mainnet and workers.dev exposure trains maintainers to weaken the production policy merely to make a check pass. The safe dry run remains permitted; publication remains a separate user authorization.
  Date/Author: 2026-07-28 / Codex.

## Outcomes & Retrospective

The implementation is complete locally. The release XSS contract reports zero authored sinks, 24 release scripts, and two inventoried generated `innerHTML` sinks. The npm contract verifies 8 production dependencies, 16 development dependencies, 4 overrides, and the exact 4-package install-script allowlist. Production and full npm audits both report zero vulnerabilities; the Maven checks verified and scanned 62 coordinates with no advisories. The generated CycloneDX inventory is at `out/security/sbom.cdx.json`.

Two consecutive `npm run build:cloudflare` executions produced the identical build ID `713FD5F0B51F2D7EED151F7B1EDF2B5ED8EE70629F1BB7AED7C16DA93A29C4D9`. The artifact preflight passed 35 checks with zero warnings and failures. `npm run cloudflare:check` read 57 assets and exposed only `ASSETS` and `HYPERUNIT_TESTNET_URL`, then exited at Wrangler's `--dry-run` boundary without publishing. Wrangler could not write its optional user-level log inside the sandbox and emitted `EPERM`, but the dry run itself exited successfully.

Focused validation passed: security 17/17, Cloudflare 32/32, release assets 51/51, and Playwright SEO 6/6. The full gate matrix passed 35/35, comprising 6,586 tests and 35,839 assertions; every ClojureScript target compiled with zero warnings. No Browser MCP session was created, so no browser-inspection cleanup was needed.

The final read-only security review identified two actionable gaps. ClojureScript interop forms are now covered by the authored-sink contract, including `js/eval`, `js/Function.`, `.write js/document`, and `.insertAdjacentHTML`; unquoted inline event handlers and `javascript:` attributes are also rejected. Worker-generated apex, status, and Mainnet-closed documents now carry the same one-year HSTS policy as static release documents. The review's Wrangler concern was resolved against the preceding completed plan, which proves 4.114.0 was an already authorized security upgrade and must be preserved. The final post-fix gate run retained the 35/35, 6,586-test, 35,839-assertion result.

The result replaces implicit release assumptions with fail-closed local contracts. It does not remove the residual risk of a malicious reviewed dependency, compromised CI or first-party artifacts, browser extension abuse, unknown vulnerabilities, or arbitrary same-origin code acting during an unlocked in-memory session. Vendor/framework sinks remain inventoried pending a real Trusted Types migration. The Worker policy changes are local and will not protect the public service until a separately authorized deployment.

## Context and Orientation

`src/hyperopen/wallet/agent_lockbox.cljs` keeps the unsupported-passkey agent credential only in process memory. That prevents a later script from reading a raw key out of `localStorage`, `sessionStorage`, or IndexedDB, but no browser storage choice protects a secret while hostile script is already running in the same document. `docs/SECURITY.md` is the canonical place to explain that boundary accurately.

`tools/release-assets/security_headers.mjs` is the single release-header generator. It computes the only allowed inline theme script hash and produces `_headers` for the generated static release. `tools/release-assets/verify_deployment_headers.mjs` reads the public tenant manifest and checks the response header against that same generator. The source `resources/public/index.html` contains development loader scripts, but the release generator rewrites it so the shipped HTML has only the CSP-hashed theme preload plus same-origin fingerprinted scripts. Generated output may contain third-party and renderer internals; it is not a substitute for reviewing authored code.

`package.json` declares npm packages and `package-lock.json` records the exact tree installed by `npm ci`. An override is a deliberate replacement of a transitive package version. The current overrides are simple package-name-to-exact-version mappings, so this plan intentionally rejects more complicated nested, ranged, alias, git, file, URL, and wildcard override forms rather than attempting to guess their security meaning. `tools/security/sbom.mjs` inventories production packages and `tools/security/clojure_tree.mjs` does the equivalent for Maven; neither currently verifies the npm declaration/lock/override relationship.

`wrangler.jsonc` and `workers/hyperopen-worker.mjs` define the publishable Cloudflare boundary. The approved policy is: no Workers development hostname, only the four DEXHelm custom hosts, only `testnet.dexhelm.com` as a terminal/proxy host, and only `HYPERUNIT_TESTNET_URL` as a Worker binding. `.agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs` is a local read-only contract checker, but its old expectations predate that policy. The Cloudflare skill documents safe `build:cloudflare` and `cloudflare:check` commands; `deploy:cloudflare` is an external publication action and is outside this plan.

## Plan of Work

### Milestone 1: Make the release XSS boundary explicit and regression-tested

Start with RED Node tests in `tools/security/release_xss_contract.test.mjs`, focused additions to `tools/release-assets/generate_release_artifacts.test.mjs`, and `tools/release-assets/verify_deployment_headers.test.mjs`. Add a small pure helper under `tools/security/` that reads an authored-source allowlist and a generated release directory. It must reject application-owned `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write`, `eval`, and `new Function` use under `src/hyperopen/**`, while excluding immutable third-party static assets outside the application source tree. It must also reject release HTML that contains an unapproved inline script, an inline event-handler attribute, a non-self external script URL, or a `javascript:` script/navigation value. The fixture tests must include a safe release, one unsafe inline script, each rejected source sink, and a known generated vendor/framework sink that is only reported in the inventory and does not make the authored-source assertion pass by accident.

Replace the empty `innerHTML` assignment in `src/hyperopen/ui/fx.cljs` with the equivalent DOM API (`replaceChildren`) before appending the constructed nodes. Add or extend its ClojureScript test to ensure markup-looking `quip` input is emitted solely through `textContent`, never as an element or event attribute. Do not add an HTML sanitizer, `dangerouslySetInnerHTML` equivalent, or a new generic raw-HTML rendering path.

In `tools/release-assets/security_headers.mjs`, explicitly add `script-src-attr 'none'` while retaining the exact `script-src` policy of `'self'` plus the generated theme hash. The contract must reject `unsafe-inline`, `unsafe-eval`, a wildcard source, a remote script source, or a CSP policy that omits `base-uri 'self'`, `object-src 'none'`, `form-action 'self'`, and `frame-ancestors 'none'`. The header verifier must check the full exact policy on a representative release route and still prove the allowed theme preload and same-origin hashed bundle load. Do not relax `style-src` in this task; the existing inline-style compatibility must be audited separately.

Extend `docs/SECURITY.md` with a short same-origin/XSS capability statement: passkey encryption and memory-only fallback reduce storage theft, but an active same-origin XSS can act during the unlocked window. Document the behavior that ends the fallback window (reload, disable, disconnect, or account change), the CSP/sink controls, and the fact that Trusted Types is deferred pending a renderer/vendor migration. Update the plan's residual-risk wording if tests reveal an additional authored sink.

### Milestone 2: Fail closed on npm declaration, lock, and override drift

Write RED fixture tests in `tools/security/npm_dependency_contract.test.mjs` before changing package metadata. Add `tools/security/npm_dependency_contract.mjs` with a pure exported validator plus a CLI. It must require a lockfile version 3 map, an exact semantic-version value for every root `dependencies` and `devDependencies` declaration, and an equal root declaration in `package-lock.json`. It must ensure the selected top-level lock entry has that exact version and integrity metadata. It must reject missing, extra, mismatched, range, alias, URL, file, workspace, git, and wildcard direct declarations.

For each declared override, accept only the current simple package-name-to-exact-semantic-version form. Find every selected package entry in the lockfile whose package name equals the override name and require that every such selected entry has the exact overridden version and integrity. Reject a missing override target, a nested override syntax, a range, conflicting resolved override versions, or lockfile entries that prove the override was not applied. Expose a deterministic JSON summary containing counts and package names only; it must never print environment variables, registry tokens, or install output.

Update `package.json` and the root package metadata inside `package-lock.json` so every direct dependency and dev dependency is the current reviewed resolved exact version. Do not upgrade a package merely to satisfy this task. Use a package-lock-only operation with scripts disabled only if it preserves all selected versions; otherwise update the root declarations mechanically and inspect the diff. Keep the existing exact pinned Wrangler and the four reviewed override versions. Add `security:npm-contract` and place it before SBOM/audit operations in `security:check`.

Update `.github/workflows/security.yml` to run `npm ci --ignore-scripts`, then the local npm contract before the network audits. Keep `npm audit` and the Maven OSV scan fail-closed on network/API failure and upload their reports with the contract summary. Keep Dependabot's weekly npm and GitHub Actions monitoring; it proposes changes, while the contract makes an incomplete proposal non-green. Update `docs/SECURITY.md` with the exact dependency-refresh procedure: update direct version and lock together, inspect override target versions, run the local contract and fixture suite, then run the network audits.

### Milestone 3: Repair Testnet-only deployment preflight and release-readiness checks

Add RED contract coverage in `tools/cloudflare/deploy_preflight_contract.test.mjs`. Refactor only enough of `.agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs` to make its checked conditions testable against temporary fixture repositories; preserve its CLI and read-only behavior. A valid fixture must require Worker name `hyperopen`, the repository Worker module, `workers_dev: false`, the current DEXHelm asset directory and custom-domain set, `ASSETS`, the Testnet URL exactly `https://api.hyperunit-testnet.xyz`, and the absence of `HYPERUNIT_MAINNET_URL`. It must reject `workers_dev: true`, a Mainnet binding, a missing/altered Testnet binding, a wildcard/unrecognized host, a generic/Mainnet proxy artifact base, an artifact with a direct HyperUnit origin, and asset output with a mainnet proxy route.

Update the script's artifact check to require only `/api/hyperunit/testnet`, never `/api/hyperunit/mainnet`. Update `tools/cloudflare/verify_cloudflare_worker.mjs` and its tests so public/local verification calls only non-mutating Testnet routes and asserts that Mainnet/generic paths are closed without making an upstream request. Update all relevant deployment-skill prose, especially `SKILL.md` and `references/zero-to-live.md`, so it no longer tells an operator to enable `workers.dev`, preserve a Mainnet Worker upstream, or verify Mainnet fee probes. The runbook must state that a future Mainnet opening needs a separate plan and explicit user authorization.

Keep `wrangler.jsonc` Testnet-only. Run the local preflight before building and with `--artifact` after building. Then run `npm run cloudflare:check` as a dry run only. Do not run `npm run deploy:cloudflare`, `wrangler deploy` without `--dry-run`, `wrangler whoami`, a Cloudflare route/DNS mutation, or a public verification against an inferred/stale URL. If a dry run requires Cloudflare authentication or cannot complete in the environment, record it as a readiness blocker rather than changing the policy or publishing.

### Milestone 4: Validate the complete hardening path

Bootstrap from `/Users/zh/Documents/Hyperopen` with `npm run setup:worktree`. First run the focused Node suites for security contracts, release artifacts, Cloudflare Worker/preflight, and header verification. Because release header behavior changes, run the smallest relevant deterministic Playwright release command from the existing `playwright-e2e` contract before broader gates. Let it exit cleanly; no Browser MCP session is required for this static/release work.

Use the known environment corrections only when necessary: JDK 21 at `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`, and Node 25's local storage workaround. Run the release build twice to preserve the existing repeatability invariant, then the artifact preflight and `cloudflare:check`. Run `npm run security:npm-contract`, `npm run security:sbom`, `npm run security:audit`, `npm run security:clojure-tree:check`, and `npm run security:clojure-audit`. A registry or OSV failure remains a failed/blocked security result; retry once only after approved network access.

Finally run `npm run gates`, which covers `npm run check`, `npm test`, and `npm run test:websocket`. Preserve unrelated worktree changes. Update `Progress`, `Surprises & Discoveries`, `Outcomes & Retrospective`, and this revision note with pass/fail evidence. Move this plan to `completed` only after every acceptance criterion passes; otherwise leave its precise failing condition as an unchecked progress item.

## Non-Goals

This plan does not deploy a Worker, publish a version, change Cloudflare account/DNS/custom-domain state, authenticate to Cloudflare, reopen Mainnet, alter signing payload serialization, introduce an HTML sanitizer, add a permissive Trusted Types policy, promise isolation from a hostile browser extension, or claim to detect all malicious code or zero-days. It does not broadly upgrade npm/Maven dependencies; any version upgrade must be separately justified by a reviewed vulnerability finding. It does not remove the browser's normal style compatibility or re-architect the renderer.

## Concrete Steps

Run all commands from `/Users/zh/Documents/Hyperopen`:

    npm run setup:worktree
    node --test tools/security/*.test.mjs
    node --test tools/release-assets/generate_release_artifacts.test.mjs tools/release-assets/verify_deployment_headers.test.mjs
    npm run test:cloudflare-worker
    npm run test:release-assets
    npm run test:playwright:seo
    npm run build:cloudflare
    npm run build:cloudflare
    node .agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs --artifact
    npm run cloudflare:check
    npm run security:npm-contract
    npm run security:sbom
    npm run security:audit
    npm run security:clojure-tree:check
    npm run security:clojure-audit
    npm run gates

When the local shell lacks a configured JDK or uses Node 25 web storage, use:

    env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH NODE_OPTIONS=--localstorage-file=/tmp/hyperopen-node-gates-localstorage npm run gates

Expected final evidence: the preflight has zero failures while still reporting `workers_dev=false` and no Mainnet binding; both consecutive release builds pass; the dry-run exits zero without publishing a version; the XSS/source contract reports no unreviewed authored sinks; the npm contract reports exact direct declarations and applied overrides; network audits report no findings; and `npm run gates` prints a complete PASS matrix. Do not treat a warning, network outage, skipped command, or an inferred public URL as a successful publication check.

## Validation and Acceptance

The XSS/CSP acceptance condition is observable when the new fixture suite rejects every listed authored source sink and unsafe release HTML, `src/hyperopen/ui/fx.cljs` no longer contains `innerHTML`, the generated release has only the single hash-approved inline theme script plus same-origin script URLs, and the exact document CSP contains `script-src 'self' <theme hash>` and `script-src-attr 'none'` but no `unsafe-inline`, `unsafe-eval`, wildcard, or remote script source. The release Playwright check must show that the normal trading route still loads. Documentation must accurately state that this reduces XSS exposure but does not make a live in-memory key safe from arbitrary same-origin code.

The dependency acceptance condition is observable when fixture tests fail for a caret/range, lock root mismatch, missing integrity, extra/missing direct package, unapplied override, nested/ranged override, and inconsistent resolved override, while the checked-in package/lock pair passes with a stable package-only summary. The security workflow must run this contract after `npm ci --ignore-scripts` and before SBOM, npm audit, and Maven OSV audit. No production or development direct dependency remains range-declared.

The release-preflight acceptance condition is observable when the current repository preflight succeeds with `workers_dev=false`, no `HYPERUNIT_MAINNET_URL`, and the exact Testnet URL; deterministic fixtures fail for each unsafe inversion; artifact validation rejects a direct origin or Mainnet proxy base; and the Worker verifier never sends a Mainnet request. `npm run cloudflare:check` must finish as a dry run only. No deployment command may appear in the implementation transcript.

The complete acceptance condition is that all focused checks and `npm run gates` pass. Report changed files, focused command results, dry-run result, and residual risks. Unknown zero-days, compromised reviewed dependencies, same-origin script compromise during an unlocked memory-only session, browser extensions, and external audit-service coverage remain explicitly documented residual risks.

## Idempotence and Recovery

The source/lock/header/preflight validators are read-only or write only regenerated local release and audit artifacts. Repeating them must not mutate Cloudflare. `npm ci --ignore-scripts` removes an inconsistent installed tree and recreates it strictly from the reviewed lock without package lifecycle scripts. A failed release build may be rerun after its local cause is corrected; do not delete caches merely to conceal a repeated-build failure. The two-build test detects such an error.

If package metadata pinning produces an unexpected resolved-version change, stop and restore the intended selected version through the manifest/lock review rather than accepting an opportunistic update. If strict sink scanning finds a legitimate new sink, do not add a broad exception: document the data provenance, use a safe DOM API where possible, add an input-specific test, and obtain a new security decision. If the Cloudflare dry run is blocked by credentials or environment, stop after local checks and leave publication unattempted.

## Artifacts and Notes

Keep generated SBOM and audit reports under `out/security/`; do not commit external responses unless an existing repository rule requires a deterministic fixture. Commit the source/lock/override validator, source/release sink validator, focused deterministic tests, skill preflight repair, package metadata, workflow, and canonical documentation. No private key, passkey output, raw browser storage, Cloudflare token, cookie, authorization header, signed payload, or full upstream response belongs in the plan, fixtures, test logs, or final report.

Initial preflight evidence to preserve in the implementation notes:

    FAIL workers.dev publishing is enabled
    FAIL mainnet upstream is fixed
    Preflight summary: 28 passed, 2 warnings, 2 failures

Those failures are expected before Milestone 3 because the preflight is stale, not because the approved Worker policy should be weakened.

## Interfaces and Dependencies

Add a Node security-contract module with a stable exported shape equivalent to:

    validateNpmDependencyContract(packageJson, lockfile) -> { directDependencies, directDevDependencies, overrides }
    assertReleaseXssContract({ sourceRoot, releaseRoot }) -> { authoredSinkCount, releaseScriptCount, vendorSinkInventory }

Both functions must throw descriptive non-secret errors on invalid input and be callable from deterministic `node --test` fixtures. Their CLIs must exit nonzero on a contract violation. Do not add an npm runtime dependency; Node's built-in `fs`, `path`, `crypto`, and URL APIs are sufficient.

Keep `buildContentSecurityPolicy`, `expectedDocumentHeaders`, `verifyDeploymentHeaders`, the Worker public exports, and the deployment skill preflight CLI compatible with current callers. The preflight may export internal validation helpers for tests, but `node .agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs [--repo <path>] [--artifact]` must remain the operator command. Its valid policy has exactly the Testnet Worker binding and no Mainnet/Workers-development exposure.

Revision note: created on 2026-07-28 for user-requested defense-in-depth remediation after the completed 2026-07-27 residual hardening plan. It intentionally authorizes local build, dry-run, and publication-readiness checks only; a Cloudflare publication requires a separate explicit user request. Updated after implementation to record focused tests, deterministic builds, artifact preflight, dry-run, dependency scans, browser verification, the complete gate matrix, and residual risks.
