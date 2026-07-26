# Package an Enterprise White-Label Release

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be maintained as work proceeds. It follows `docs/PLANS.md` and `.agents/PLANS.md`.

## Purpose / Big Picture

After this work, an operator can take a checked-in or customer-supplied public tenant JSON file and one canonical HTTPS origin, run one command, and receive an isolated static Hyperopen release carrying that tenant's public name, theme, feature flags, venue label, and affiliate disclosure. The command fails before compilation when the JSON is malformed, has unknown fields, contains secret-shaped data, or violates the public tenant contract. A second command verifies the release manifest, HTML metadata, enabled route metadata, and compiled runtime identity before the directory is deployed.

The observable proof is a sample branded build whose output is separate from `out/release-public`, plus deterministic Node tests and a release Playwright scenario. The default `npm run build` remains the ordinary Hyperopen build and produces unchanged Hyperopen metadata.

## Context References

Durable user context is the direct 2026-07-21 request to continue productizing Hyperopen as a non-custodial trading terminal with professional analytics, enterprise white-label deployment, and official affiliate support. This slice is the enterprise white-label deployment package only.

Repository context includes `docs/exec-plans/completed/2026-07-20-non-custodial-analytics-white-label-affiliate.md`, which established the public tenant boundary, and `docs/exec-plans/active/2026-07-20-portfolio-professional-analytics-states.md`, which completed the preceding professional analytics slice. `src/hyperopen/config.cljs`, `src/hyperopen/service/tenant_config.cljs`, and `src/hyperopen/system.cljs` already parse and activate a Closure define named `hyperopen.config/TENANT_CONFIG_JSON`. `tools/release-assets/generate_release_artifacts.mjs` and `tools/release-assets/site_metadata.mjs` currently package the default Hyperopen release.

No GitHub Issue or Pull Request was supplied. Local workflow artifacts under `tmp/multi-agent/enterprise-white-label-release-packaging/` are non-authoritative.

## Scope

Add a Node-based operator boundary under `tools/white-label/`. It reads UTF-8 JSON, rejects duplicate or unknown schema fields through exact object validation, normalizes the same public values accepted by the ClojureScript tenant service, rejects secret-shaped keys and values recursively, requires HTTPS for every non-empty public URL and the canonical deployment origin, and writes no customer input until validation succeeds. Validation is strict; it must never silently fall back to the default tenant.

Add a build command that stages the public static inputs and all Shadow compiler outputs in a tenant-specific temporary directory, injects the canonical serialized JSON into `hyperopen.config/TENANT_CONFIG_JSON` with `shadow-cljs --config-merge`, runs the existing release artifact generator through an additive tenant-aware interface, and writes the result to an explicitly selected isolated directory. Re-running the same command replaces only that selected white-label staging and output directory. The normal `resources/public/js` and `out/release-public` paths must not be used as white-label compiler or output destinations.

The release contains a versioned `tenant-manifest.json` with only normalized public tenant data, canonical origin, enabled product routes, build identifier, and deterministic config digest. It also contains concise deployment instructions naming the output directory, required static-host behavior, verification command, and the non-custodial/public-config boundaries. It must never contain an environment dump, source config path, local username, raw command line, wallet address, credential, private key, seed phrase, API secret, access token, or raw signature.

Make the release metadata generator accept additive tenant metadata without changing its default behavior. White-label HTML title, description, Open Graph/Twitter metadata, loading shell, sitemap, and route metadata use the tenant brand and canonical origin. `/trade` is excluded from white-label route metadata and generated route HTML when `features.terminal` is false; `/portfolio` is likewise excluded when `features.analytics` is false. Other existing public information routes retain their current availability. Affiliate configuration appears only in the public tenant manifest and the already-owned runtime product context; it does not introduce a redirect, provider call, tracking event, or secret.

Add a checked-in example config under `config/white-label/` and deterministic Node tests under `tools/white-label/`. Add one committed Playwright release test proving the sample release loads the tenant name, selected theme, canonical metadata, allowed route metadata, and no disabled route presentation. This is release browser behavior, so governed browser QA is required, but the implementation should not redesign application views.

## Non-Goals

Do not add custody, deposits, withdrawals, balances held by Hyperopen, server-side signing, wallet-secret access, customer administration, billing, entitlements, tenant databases, license enforcement, provider credentials, or a hosted control plane. Do not change wallet connection/signing, order ownership/submission, WebSocket decisions, portfolio analytics calculations, provider schemas, or public ClojureScript APIs. Do not deploy to an external service in this slice. Do not make the generated release accept runtime secrets or arbitrary JavaScript/HTML from tenant JSON.

## Progress

- [x] (2026-07-21) Inspected the existing tenant define, strict public ClojureScript boundary, Shadow release configuration, static release generator, metadata generator, and existing tenant/release tests.
- [x] (2026-07-21) Froze the slice around strict public JSON preflight, isolated compilation/output, branded public metadata, manifest/deployment notes, and verification while preserving the default build.
- [x] (2026-07-21) Reconciled five acceptance scenarios and sixteen adversarial scenarios into a ten-case approved contract covering public validation, zero-side-effect preflight, isolated compilation, atomic recovery, metadata/routes, default compatibility, tamper detection, public-only output, and browser runtime identity.
- [x] (2026-07-21) Materialized the approved RED tests and checked-in `config/white-label/example-enterprise.json`. The narrow Node suite has 13 cases: 11 intentionally fail because `tools/white-label/tenant_config.mjs` and `tools/white-label/build_release.mjs` do not yet exist, while tenant-aware release metadata also lacks `DEPLOYMENT.md` and feature-route filtering.
- [x] (2026-07-21) Implemented strict public JSON validation, isolated staging and atomic publication, release verification, concise CLI scripts, and additive tenant metadata/route filtering without changing the ordinary build command.
- [x] (2026-07-21) Closed browser-discovered white-label runtime gaps: document titles now use the active custom tenant brand while default theme voice remains intact, and the header renders one responsive visible tenant name at mobile and desktop widths.
- [x] (2026-07-21) Applied frozen reviewer remediation without changing runtime or browser coverage: the app alone receives the public Closure define, affiliate event endpoints contribute only an HTTPS origin to CSP, verification pins the main script and closes the artifact inventory, public text artifacts are secret-scanned, and tenant-scoped lock/journal recovery protects publication. The focused Node contract passed 18/18.
- [x] (2026-07-21) Applied the browser-QA tablet header correction: desktop navigation and desktop brand logo now begin at `lg`, preserving the compact menu/logo composition through 768 px; the brand wordmark uses the approved 24 px desktop scale and standard content bottom reserve is `pb-16`. Updated render-class contracts, compiled the app with zero warnings, and ran 5,807 ClojureScript tests with zero failures/errors.
- [x] (2026-07-21) Closed the final release-review findings in tooling only: `tenant-manifest.json` now carries an exact full SHA-256 inventory for every non-manifest artifact, journal recovery is phase-aware, stale lock reclamation checks PID liveness, and staging/publication operations re-resolve owned paths before mutation. The frozen focused Node suite passed 23/23.
- [x] (2026-07-21) Passed the focused white-label/release suite (23/23), real sample validation/build/verification, committed release Playwright coverage (4/4 at 375, 768, 1280, and 1440), reviewer PASS, and governed six-pass browser QA PASS; browser cleanup left no session.
- [x] (2026-07-21) Passed the final `npm run gates` matrix (34/34), including 5,807 ClojureScript tests / 32,322 assertions and 561 WebSocket tests / 3,184 assertions. Move this accepted plan to completed.

## Surprises & Discoveries

- Observation: the runtime white-label boundary already exists and is intentionally public-only, but invalid build input falls back to Hyperopen at runtime.
  Evidence: `src/hyperopen/config.cljs` defines `TENANT_CONFIG_JSON` and returns `nil` on invalid input, while `src/hyperopen/service/tenant_config.cljs` normalizes missing input to `default-tenant-raw`. Therefore the operator command must reject invalid input before invoking Shadow rather than relying on runtime fallback.
- Observation: the standard release command compiles into `resources/public/js` and packages `out/release-public`.
  Evidence: `shadow-cljs.edn` uses `resources/public/js`, and `tools/release-assets/generate_release_artifacts.mjs` defaults to `resources/public` and `out/release-public`. White-label isolation must override every build output directory and use explicit generator roots.
- Observation: site metadata is currently a static Hyperopen route catalog.
  Evidence: `tools/release-assets/site_metadata.mjs` exports `PUBLIC_ROUTE_METADATA` with Hyperopen titles and both `/trade` and `/portfolio`; tenant-aware metadata must be an additive option whose absence preserves these exact defaults.
- Observation: the broad release-assets command includes existing loopback-server tests that are blocked in this sandbox with `listen EPERM`.
  Evidence: `node --test tools/white-label/*.test.mjs tools/release-assets/*.test.mjs` also failed `verify_deployment_headers.test.mjs` before its assertions. The isolated RED command excludes those unrelated tests and reports only the intended white-label failures.
- Observation: the real white-label build cannot invoke Shadow in this environment because no Java Runtime is available.
  Evidence: the sample `build:white-label` completed the isolated Tailwind stage, then `npx shadow-cljs` reported `Unable to locate a Java Runtime`. The builder removed its temporary staging directory and published no output.
- Observation: tenant branding reached header chrome but not the market document title, and the only exact brand-name node was hidden below the desktop breakpoint.
  Evidence: browser validation found the title retained `HyperOpen` and `page.getByText('Enterprise Desk', {exact: true}).first()` resolved to the hidden desktop `header-brand-name` element at 375 px.
- Observation: a permissive verifier inventory could allow an injected executable or auxiliary text artifact even when the manifest and bundle digest remained valid.
  Evidence: reviewer remediation tests add a non-allowlisted JavaScript payload, retargeted route HTML, a secret-bearing release note, and secret-shaped content in `DEPLOYMENT.md`; all now fail with artifact-oriented diagnostics that do not echo the sentinel.
- Observation: at 768 px, the header's desktop navigation caused a 1,052 px `app-root` scroll width before the route body rendered.
  Evidence: browser QA measured the header/app-root overflow at 768 x 1024. The trade shell already uses `lg` for desktop/mobile composition, so the header now uses the same breakpoint and keeps the existing tablet-hidden spectate trigger unchanged.
- Observation: Node path checks can re-resolve and reject visible symlinks, but Node does not expose an `openat`/`O_NOFOLLOW` directory-handle transaction for this workflow.
  Evidence: staging creation and each journal/rename boundary now repeat `lstat`-based ownership checks. A hostile same-user process able to mutate the repository between those checks remains outside the deployment tool's threat boundary; trusted operators must run builds from a repository that other same-user processes cannot modify.

## Decision Log

- Decision: Build from a file path and explicit canonical origin, not an environment-sized JSON blob.
  Rationale: a file is auditable and avoids shell quoting leaks; the tool can read it without echoing its raw contents. The canonical origin remains an explicit deploy-time input because it is not part of the current tenant runtime schema.
  Date/Author: 2026-07-21 / primary agent.
- Decision: Fail closed before compilation instead of accepting the runtime's default fallback.
  Rationale: silent fallback would let an operator ship a customer release branded Hyperopen after a typo, which is unsafe and commercially misleading.
  Date/Author: 2026-07-21 / primary agent.
- Decision: Keep the default release generator behavior byte-compatible at its public defaults and add tenant options.
  Rationale: `npm run build` is an established release path. White-label behavior must be opt-in and must not make ordinary releases depend on a tenant file.
  Date/Author: 2026-07-21 / primary agent.
- Decision: Compile all Shadow targets into an isolated staging root.
  Rationale: copying an already compiled default main bundle and rewriting only HTML would not embed the tenant Closure define. Compiling only the main module into the shared tree would risk mixed-customer artifacts.
  Date/Author: 2026-07-21 / primary agent.
- Decision: Treat `terminal` and `analytics` flags as presentation gates for their owned routes only.
  Rationale: those are the route-bearing feature flags in the existing tenant service. Affiliate is disclosure/configuration, not a new route, and unrelated public routes must not be silently removed.
  Date/Author: 2026-07-21 / primary agent.
- Decision: Freeze ten consolidated RED cases rather than materialize all twenty-one proposal cases independently.
  Rationale: several proposals exercised the same invariant at different layers. Consolidation preserves every release and security boundary while giving the worker one deterministic contract per responsibility and keeping the test suite maintainable.
  Date/Author: 2026-07-21 / primary agent.
- Decision: RED tests use the explicit Node public APIs and an injected `runCommand(executable, args, options)` seam for build orchestration.
  Rationale: this makes compiler ordering, `--config-merge` content, output isolation, and atomic recovery deterministic without executing Tailwind or Shadow in unit tests.
  Date/Author: 2026-07-21 / tdd_test_writer.
- Decision: Inject the tenant Closure define into `app` only; worker builds receive only isolated output/cache configuration.
  Rationale: only the app consumes `hyperopen.config/TENANT_CONFIG_JSON`; carrying the JSON through worker compiler commands created avoidable public-config exposure without changing worker runtime behavior.
  Date/Author: 2026-07-21 / reviewer remediation.
- Decision: Verify a closed release inventory and journal publication state.
  Rationale: filename heuristics cannot prevent executable or text artifact injection. A manifest-pinned main script, exact route script references, fixed artifact allowlist, public text scan, tenant lock, and recovery journal make deployment verification fail closed across tampering and interrupted publication.
  Date/Author: 2026-07-21 / reviewer remediation.
- Decision: Pin every generated public artifact with a full SHA-256 map, excluding only the self-referential tenant manifest.
  Rationale: the main-bundle digest alone did not protect lazy Shadow chunks, workers, metadata, headers, or copied static assets. The verifier now first establishes the exact allowlisted inventory, then compares every non-manifest file's 64-character uppercase digest.
  Date/Author: 2026-07-21 / reviewer remediation.

## Outcomes & Retrospective

The implemented tooling replaces manual Shadow define injection and ad hoc directory copying with strict preflight validation, tenant-specific isolated staging, crash-recoverable publication, and read-only artifact verification. It keeps wallet signing, order ownership, provider contracts, and WebSocket decisions unchanged. The sample validator reports `enterprise-example`, `https://desk.example.com`, `/portfolio`, and digest `C33AD81BEF02E8F31676C0F3413C220EBD45883AA4A64A50D548E79E93B0F769` without emitting raw JSON. The focused white-label/release suite passes 23/23, including complete full-SHA-256 verification of every non-manifest output artifact, lazy-chunk tamper rejection, secret-artifact rejection, affiliate endpoint CSP coverage, lock recovery, and journal recovery. The real sample build and `verify:white-label` passed. Committed release Playwright passed 4/4 at 375, 768, 1280, and 1440 after adding direct header/app-root overflow assertions. Governed browser QA passed all six passes at all four widths; it confirmed the 768 px header/app-root width is 768/768, the mobile bottom reserve is 64 px, the desktop brand is 24 px, `/trade` serves the branded 404, and there are no unexpected console/network failures. `npm run browser:cleanup` left no sessions. The final `npm run gates` matrix passed 34/34 (5,807 ClojureScript tests / 32,322 assertions; WebSocket 561 / 3,184); `npm run test:release-assets` passed 46/46 when its loopback tests were run outside the sandbox. Calls without tenant options retain their current metadata/output behavior in compatibility tests. Residual risk: Node lacks an `openat`/`O_NOFOLLOW` directory transaction, so same-user repository mutation between repeated path checks is outside this deployment tool's security boundary.

## Context and Orientation

Hyperopen is a ClojureScript browser application compiled by Shadow CLJS. A Closure define is a compile-time constant; here `hyperopen.config/TENANT_CONFIG_JSON` embeds the public tenant JSON into the optimized main bundle. `shadow-cljs --config-merge '<EDN>' release ...` can override build output directories and Closure defines without changing `shadow-cljs.edn`.

`resources/public` contains static source assets. The normal build compiles JavaScript there, then `tools/release-assets/generate_release_artifacts.mjs` fingerprints assets, writes route HTML, SEO metadata, security headers, sitemap, and robots data into `out/release-public`. The new white-label command must create a staging root containing copied safe static assets, freshly generated CSS, and freshly compiled JavaScript, then call the generator with explicit `sourceRoot`, `outputRoot`, and tenant metadata.

The public tenant JSON uses exact keys such as `tenant/id`, `brand/name`, `brand/logo-url`, `theme/id`, `features`, `venue`, and `affiliate`. It supports the current themes `dark`, `institutional`, and `hyperdegen`; the venue id is `hyperliquid`; and at least one of terminal or analytics must be enabled. The implementation must derive its accepted values from a small Node schema whose fixtures are cross-checked against the existing ClojureScript tenant acceptance fixtures. It must not loosen `src/hyperopen/service/tenant_config.cljs`.

## Plan of Work

First add `tools/white-label/tenant_config.mjs` with pure parse, exact-schema validation, normalization, canonical JSON serialization, public-route selection, secret detection, and digest functions. Add a CLI entry that supports `validate`, `build`, and `verify` subcommands with explicit `--config`, `--origin`, and `--output` arguments. Errors name the invalid field without printing secret-shaped values or the entire config.

Next add orchestration in `tools/white-label/build_release.mjs`. It creates a tenant-id-scoped staging directory under `out/white-label-staging`, copies only required public inputs, invokes Tailwind with an explicit staging output, constructs EDN config merge data without shell evaluation, invokes Shadow through `execFile` or `spawn` argument arrays for `app`, `portfolio-worker`, `portfolio-optimizer-worker`, and `vault-detail-worker`, and passes explicit roots and tenant metadata to the release generator. Any failure removes incomplete staging and leaves an existing final release untouched until an atomic replacement step.

Then extend `tools/release-assets/site_metadata.mjs` and `tools/release-assets/generate_release_artifacts.mjs` with optional metadata and route-catalog arguments. With no arguments, existing constants, titles, descriptions, paths, and output roots remain unchanged. With tenant input, derived metadata escapes tenant text, filters only feature-owned routes, and writes the tenant manifest/deployment notes after the release content has passed structural verification.

Add `config/white-label/example-enterprise.json`, package scripts `white-label:validate`, `build:white-label`, and `verify:white-label`, and tests. The RED phase owns tests before implementation. Tests use temporary directories, fake compiler runners where appropriate, and a smallest real sample build for final verification. Playwright serves the isolated output rather than `resources/public` and asserts tenant behavior from the produced files and browser runtime.

Finally run a read-only security/correctness review and governed browser QA. Correct all findings without weakening the approved tests, rerun the smallest failed check, then run the full repository gate matrix once focused validation is green.

## Touched Areas

Expected production tooling is `tools/white-label/tenant_config.mjs`, `tools/white-label/build_release.mjs`, `tools/white-label/verify_release.mjs`, optional small helpers in that directory, `tools/release-assets/site_metadata.mjs`, `tools/release-assets/generate_release_artifacts.mjs`, `package.json`, and `config/white-label/example-enterprise.json`. Expected tests are `tools/white-label/*.test.mjs`, additive release-asset tests, and `tools/playwright/test/white-label-release.spec.mjs`. The active ExecPlan and `tmp/multi-agent/enterprise-white-label-release-packaging/` hold workflow state. No `src/hyperopen/**` edit is expected.

## Concrete Steps

Run all commands from `/Users/zh/Documents/Hyperopen`.

1. Prepare dependencies and materialize the approved RED tests.

       npm run setup:worktree
       node --test tools/white-label/*.test.mjs

   Before implementation, expect a focused failure identifying the missing validator/build module or missing behavior. Record that exact failure in this plan.

2. Run focused tooling tests while implementing.

       node --test tools/white-label/*.test.mjs tools/release-assets/*.test.mjs

   Expect zero failures after implementation. Tests must cover success, malformed JSON, unknown keys, secret-shaped keys and values, HTTP URLs, unsafe output paths, command failure cleanup, default metadata compatibility, feature-filtered routes, and manifest verification.

3. Exercise the checked-in example through the full operator path.

       npm run white-label:validate -- --config config/white-label/example-enterprise.json --origin https://desk.example.com
       npm run build:white-label -- --config config/white-label/example-enterprise.json --origin https://desk.example.com --output out/white-label/example-enterprise
       npm run verify:white-label -- --config config/white-label/example-enterprise.json --origin https://desk.example.com --output out/white-label/example-enterprise

   Expect each command to identify the tenant id and normalized config digest without echoing raw JSON. The output must contain `index.html`, fingerprinted CSS/JavaScript, `site-metadata.json`, `tenant-manifest.json`, deployment instructions, security headers, sitemap, and only enabled feature-owned route HTML.

4. Run the focused release browser regression and cleanup.

       npx playwright test tools/playwright/test/white-label-release.spec.mjs
       npm run qa:design-ui -- --targets white-label-release --manage-local-app
       npm run browser:cleanup

   Expect the browser to display the sample brand and selected theme, use the supplied canonical origin, omit disabled feature route presentation, report no console/network failures, and complete all governed visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf passes at 375, 768, 1280, and 1440 widths.

5. Prove the ordinary release remains valid and run all gates.

       npm run test:release-assets
       npm run gates

   Expect the release-assets suite and the complete PASS/FAIL matrix to pass. Record exact counts and any advisory in this plan.

## Validation and Acceptance

Acceptance requires a valid sample config to produce an isolated branded release and `verify:white-label` to exit zero only when the public tenant manifest, digest, canonical origin, HTML metadata, route metadata, compiled runtime brand, and feature-owned routes agree. Editing the manifest, swapping a compiled main file, changing canonical HTML, or adding a disabled `/trade` or `/portfolio` artifact must make verification fail with a field-specific message.

Malformed JSON, duplicate or unknown fields, missing required fields, unsupported theme/venue/status, all primary features disabled, secret-shaped keys or values, non-HTTPS URLs, credentials embedded in URLs, fragments where disallowed, and unsafe output paths must fail before Tailwind or Shadow is invoked. Tests must prove the compiler runner was not called. Error output must not contain the rejected value when it could be sensitive.

The example release must render the configured tenant name in the first viewport, apply the configured theme, retain non-custodial wallet/order semantics, expose affiliate data only as public disclosure/configuration, and omit presentation of feature-owned disabled routes. No test may assert that a local fixture is a live affiliate conversion or provider response.

Calling `npm run build` without white-label arguments must retain the current Hyperopen origin, route catalog, titles, and artifact locations. Existing release-asset tests must pass unchanged or receive only additive assertions; tests must not rewrite snapshots to accept accidental rebranding.

The final required evidence is zero focused Node failures, one passing real sample build and verification, passing committed Playwright coverage, an explicit six-pass browser QA report at all four widths, `npm run browser:cleanup`, reviewer PASS, and `npm run gates` PASS.

## Idempotence and Recovery

Validation is read-only. A build may delete and recreate only its resolved staging directory and explicit output directory after proving both are beneath repository-owned `out/white-label-staging` or `out/white-label`. It must reject the repository root, `resources/public`, `out/release-public`, parent traversal, symlink escapes, and paths outside the repository. Build subprocesses receive arguments as arrays, not shell strings.

The final output should be assembled in a sibling temporary directory and renamed into place only after verification, so a failed compile or verification leaves the prior good release intact. Re-running the same command with identical normalized input produces the same tenant config digest, although fingerprinted build identifiers may differ when normal release build metadata differs. Temporary directories are cleaned on success and handled failure.

## Artifacts and Notes

Pre-implementation evidence:

    $ rg -n 'TENANT_CONFIG_JSON|configured-tenant-override' src/hyperopen
    src/hyperopen/config.cljs:6:(goog-define TENANT_CONFIG_JSON "")
    src/hyperopen/config.cljs:40:(defn configured-tenant-override ...)

    $ rg -n 'DEFAULT_OUTPUT_ROOT|PUBLIC_ROUTE_METADATA' tools/release-assets
    tools/release-assets/generate_release_artifacts.mjs:DEFAULT_OUTPUT_ROOT = path.resolve("out/release-public")
    tools/release-assets/site_metadata.mjs:export const PUBLIC_ROUTE_METADATA = [...]

Completion evidence must include the exact validator output shape, example manifest digest, output file inventory, focused Node and Playwright results, browser QA artifact, cleanup confirmation, reviewer result, and gate matrix. It must not include raw customer config or secrets.

RED evidence (2026-07-21):

    $ node --test tools/white-label/*.test.mjs tools/release-assets/white_label_*.test.mjs
    tests 13; pass 2; fail 11

The intended first-layer failures are `ERR_MODULE_NOT_FOUND` for `tools/white-label/tenant_config.mjs` in `strict public tenant parsing normalizes the checked-in example into stable canonical data` and `tools/white-label/build_release.mjs` in `invalid config, origin, and output fail before any staging mutation or compiler command`. The additive metadata cases also fail as intended: `tenant-aware metadata brands public entries and filters only feature-owned routes` cannot read missing `DEPLOYMENT.md`, and `each disabled owned route is absent everywhere while unrelated public information routes remain` finds `/trade` in the generated sitemap. The Playwright contract was added but not run before the required release exists.

## Interfaces and Dependencies

The Node tooling must export pure functions suitable for tests:

    parseAndNormalizeTenantConfig(rawText) -> normalizedPublicTenant
    canonicalTenantJson(normalizedPublicTenant) -> string
    tenantConfigDigest(normalizedPublicTenant) -> uppercase SHA-256 prefix or full digest
    enabledTenantRoutes(normalizedPublicTenant) -> string[]
    buildWhiteLabelRelease(options, dependencies?) -> Promise<buildResult>
    verifyWhiteLabelRelease(options) -> Promise<verificationResult>

`buildWhiteLabelRelease` accepts absolute or repository-resolved config/output paths, canonical HTTPS origin, and injected subprocess/file-system dependencies for deterministic tests. The CLI maps `validate`, `build`, and `verify` onto these functions and exits non-zero on any contract failure.

The existing release interfaces remain compatible. `buildSiteMetadata` and `generateReleaseArtifacts` may accept optional `tenant` or derived route metadata, but calls with current arguments must return current Hyperopen content. Use only Node standard libraries and already installed dependencies. Do not add an npm package, external API, network fetch, server component, or runtime secret.

Plan revision note (2026-07-21): created after white-label discovery to turn the existing runtime tenant define into a strict, isolated, operator-verifiable enterprise release workflow without changing trading or custody behavior.
