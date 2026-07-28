---
owner: security
status: canonical
last_reviewed: 2026-07-27
review_cycle_days: 90
source_of_truth: true
---

# Security and Signing Safety

## Crypto Signing and Exchange Application Programming Interface Rules (MUST)
- MUST treat signing payload serialization as consensus-critical behavior; any wire-format change requires explicit parity tests against known vectors.
- MUST preserve integer fidelity for signing-critical fields (`oid`, nonces, asset indexes, sizes where applicable) and MUST NOT route them through lossy float encoding.
- MUST keep signing identity deterministic: the signing private key and persisted `agent-address` must be reconciled before signing/submit.
- MUST fail fast or reconcile when persisted session identity drifts, and MUST NOT silently continue with mismatched key/address pairs.
- MUST verify missing-wallet/missing-agent exchange errors before clearing local agent credentials (for example via `userRole` or equivalent info lookup).
- MUST avoid destructive key invalidation on ambiguous errors; only clear local credentials when invalidity is confirmed.
- MUST keep signing diagnostics non-sensitive: log hashes, action types, and derived signer metadata only; MUST NOT log raw private keys or raw secret material.
- MUST keep protocol-shape translation and exchange error normalization centralized in Anti-Corruption Layer and Application Programming Interface boundaries (for example `/hyperopen/src/hyperopen/api/trading.cljs` and signing utilities), not UI callbacks.
- MUST cross-check signing and exchange action behavior against reference software development kits whenever signing or Application Programming Interface code is changed.
- MUST document any intentional divergence from reference SDK behavior in PR notes and include compensating regression coverage.

## Signing Rules
- Treat payload serialization as consensus-critical.
- Preserve integer fidelity for signing-critical fields.
- Reconcile signing key identity and persisted `agent-address` before submit.
- Fail fast or reconcile on signer/session drift; do not silently continue.

## Credential Invalidation Rules
- Validate missing-wallet/missing-agent conditions before clearing local credentials.
- Do not destructively invalidate keys on ambiguous errors.
- Clear local credentials only when invalidity is confirmed.
- New raw API-wallet private keys must never be persisted to `localStorage`.
- Durable agent credentials require the passkey lockbox; browsers without passkey support keep the raw agent key only in the current process memory and require Enable Trading again after reload. `sessionStorage` is never a credential store.
- Legacy plaintext local credentials are quarantine-only input and must never become ready-to-sign state automatically.

## Browser Script Boundary

- Passkey encryption and memory-only fallback prevent later storage theft, but they do not make an unlocked credential safe from arbitrary JavaScript already executing in the same origin.
- Reload, explicit Disable Trading, wallet disconnect, and account change end the memory-only credential window. Browsers without passkey support must enable trading again after reload.
- Release documents allow scripts only from the same origin plus the exact hashed theme preload, and forbid inline event-handler attributes through `script-src-attr 'none'`.
- `npm run security:xss-contract` rejects application-owned HTML parsing and string-to-code sinks. Generated renderer and chart internals remain inventoried vendor code rather than an allowed authored sink.
- Release documents enforce `require-trusted-types-for 'script'` and permit only the `default` policy. Its HTML callback accepts exactly the renderer's empty-root reset and the reviewed Lightweight Charts attribution SVG; its script-URL callback accepts only fingerprinted, same-origin Shadow modules from the explicit module allowlist. All other HTML, script, policy-name, URL, query-string, and path-traversal inputs fail closed.
- `npm run test:playwright:seo` proves the release policy in Chromium against `/trade`: the chart and TradingView attribution load, arbitrary `innerHTML` fails, and duplicate or unapproved policy creation fails. Browsers without Trusted Types support retain the release CSP and authored-sink contract but cannot provide the browser-level Trusted Types enforcement.

## Deployment Network Authority

- The Hyperliquid network is a compile-time release declaration, not a query-string or browser-global choice.
- Missing, blank, or unsupported declarations disable REST, WebSocket, approval, funding, order, and signing I/O.
- Mainnet is enabled only by an exact `mainnet` declaration; DEXHelm declares `testnet`.

## Diagnostics and Logging
- Log only non-sensitive signing diagnostics (hashes, action types, derived signer metadata).
- Never log raw private keys or secret material.

## Protocol and SDK Parity
When signing or exchange behavior changes, verify against at least two reference SDK and document intentional divergence with regression coverage:
- [nktkas/hyperliquid](https://github.com/nktkas/hyperliquid)
- [nomeida/hyperliquid](https://github.com/nomeida/hyperliquid)
- [hyperliquid-dex/hyperliquid-python-sdk](https://github.com/hyperliquid-dex/hyperliquid-python-sdk)

## Dependency Governance

- Every direct npm dependency and development dependency uses an exact version. `npm run security:npm-contract` verifies manifest/lock agreement, SHA-512 integrity, the selected version of every override target, and the exact allowlist of packages with install scripts.
- `npm run security:sbom` writes the deterministic production CycloneDX inventory to `out/security/sbom.cdx.json`.
- `npm run security:audit` fails on high or critical production findings. Registry/DNS failure is not a clean result and must remain a failed gate.
- `npm run security:clojure-tree` refreshes the committed selected Maven inventory after an intentional `deps.edn` change; it does not resolve dependencies during CI.
- `npm run security:clojure-audit` queries every locked Maven coordinate through OSV and fails on transport errors, malformed responses, or reported advisories.
- Pull requests and the weekly security workflow run both checks and retain their outputs as CI artifacts.
- Dependabot checks npm and GitHub Actions weekly. The committed Clojure/Maven inventory covers the resolved transitive graph; refresh it deliberately when `deps.edn` changes and review the resulting version diff.
- GitHub Actions workflows use read-only repository permissions, immutable 40-character action commits, `npm ci --ignore-scripts`, and the local dependency/CI contracts. Downloaded archives must be checksum-verified before extraction; test workflows do not commit or push generated artifacts.
- Adding a runtime dependency requires an owner, exact lockfile resolution, license review, install-script review, and a documented reason an existing dependency or platform API is insufficient.
- To refresh npm dependencies, update the exact direct version and lock together with lifecycle scripts disabled, inspect every selected-version and integrity diff, update the install-script allowlist only after reviewing that package's published lifecycle code, then run `npm run security:npm-contract`, `npm run test:security`, and the network audits. Do not convert an exact declaration back to a range.

## Attribution Privacy Boundary

- `wallet/address-hash` is a pseudonymous, linkable identifier. It is not anonymous, encrypted, or irreversible for an observer who can test candidate wallet addresses.
- Attribution delivery requires the enabled affiliate feature, a valid canonical HTTPS endpoint, and explicit consent. Raw wallet addresses, private keys, seed phrases, and signatures remain prohibited in events.
