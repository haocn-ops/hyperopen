import assert from "node:assert/strict";
import test from "node:test";

import { validateNpmDependencyContract } from "./npm_dependency_contract.mjs";

function fixture({ dependency = "1.2.3", locked = "1.2.3", override = "4.5.6", overridden = "4.5.6" } = {}) {
  return {
    packageJson: {
      name: "fixture",
      version: "1.0.0",
      dependencies: { direct: dependency },
      devDependencies: { devonly: "2.0.0" },
      overrides: { transitive: override },
    },
    lockfile: {
      name: "fixture",
      version: "1.0.0",
      lockfileVersion: 3,
      packages: {
        "": {
          name: "fixture",
          version: "1.0.0",
          dependencies: { direct: dependency },
          devDependencies: { devonly: "2.0.0" },
        },
        "node_modules/direct": { name: "direct", version: locked, integrity: "sha512-ZmFrZQ==" },
        "node_modules/devonly": { name: "devonly", version: "2.0.0", integrity: "sha512-ZmFrZQ==", dev: true },
        "node_modules/transitive": { name: "transitive", version: overridden, integrity: "sha512-ZmFrZQ==" },
      },
    },
  };
}

test("exact direct declarations and applied overrides pass with a stable summary", () => {
  const { packageJson, lockfile } = fixture();
  assert.deepEqual(validateNpmDependencyContract(packageJson, lockfile), {
    directDependencies: ["direct"],
    directDevDependencies: ["devonly"],
    installScripts: [],
    overrides: ["transitive"],
  });
});

test("unreviewed install scripts fail closed", () => {
  const { packageJson, lockfile } = fixture();
  lockfile.packages["node_modules/unsafe-installer"] = {
    name: "unsafe-installer",
    version: "1.0.0",
    integrity: "sha512-ZmFrZQ==",
    hasInstallScript: true,
  };
  assert.throws(() => validateNpmDependencyContract(packageJson, lockfile), /unreviewed npm install script/i);
});

test("direct dependency declarations reject ranges and lock drift", () => {
  for (const invalid of ["^1.2.3", "~1.2.3", "*", "latest", "file:../direct", "git+https://example.test/repo.git", "npm:other@1.2.3"]) {
    const { packageJson, lockfile } = fixture({ dependency: invalid });
    assert.throws(() => validateNpmDependencyContract(packageJson, lockfile), /exact semantic version/i);
  }

  const mismatch = fixture({ locked: "1.2.4" });
  assert.throws(() => validateNpmDependencyContract(mismatch.packageJson, mismatch.lockfile), /selected.*1\.2\.4/i);
});

test("direct dependency contract rejects root and integrity drift", () => {
  const rootMismatch = fixture();
  rootMismatch.lockfile.packages[""].dependencies.direct = "1.2.4";
  assert.throws(() => validateNpmDependencyContract(rootMismatch.packageJson, rootMismatch.lockfile), /root lock/i);

  const missingDirect = fixture();
  delete missingDirect.lockfile.packages["node_modules/direct"];
  assert.throws(() => validateNpmDependencyContract(missingDirect.packageJson, missingDirect.lockfile), /selected package/i);

  const missingIntegrity = fixture();
  delete missingIntegrity.lockfile.packages["node_modules/direct"].integrity;
  assert.throws(() => validateNpmDependencyContract(missingIntegrity.packageJson, missingIntegrity.lockfile), /integrity/i);
});

test("overrides reject non-exact syntax, missing targets, and unapplied versions", () => {
  const ranged = fixture({ override: "^4.5.6" });
  assert.throws(() => validateNpmDependencyContract(ranged.packageJson, ranged.lockfile), /override.*exact semantic version/i);

  const nested = fixture();
  nested.packageJson.overrides = { parent: { transitive: "4.5.6" } };
  assert.throws(() => validateNpmDependencyContract(nested.packageJson, nested.lockfile), /simple.*override/i);

  const missing = fixture();
  delete missing.lockfile.packages["node_modules/transitive"];
  assert.throws(() => validateNpmDependencyContract(missing.packageJson, missing.lockfile), /override target/i);

  const unapplied = fixture({ overridden: "4.5.5" });
  assert.throws(() => validateNpmDependencyContract(unapplied.packageJson, unapplied.lockfile), /override.*4\.5\.5/i);
});
