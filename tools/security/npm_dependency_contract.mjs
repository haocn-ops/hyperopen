import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const EXACT_SEMVER = /^(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/;
const INTEGRITY = /^sha512-[A-Za-z0-9+/=]+$/;
const APPROVED_INSTALL_SCRIPTS = new Set([
  "esbuild@0.28.1",
  "fsevents@2.3.2",
  "fsevents@2.3.3",
  "workerd@1.20260722.1",
]);

function sortedEntries(value) {
  return Object.entries(value ?? {}).sort(([left], [right]) => left.localeCompare(right));
}

function packageNameFromPath(packagePath, metadata) {
  if (typeof metadata?.name === "string" && metadata.name) return metadata.name;
  const marker = "node_modules/";
  const index = packagePath.lastIndexOf(marker);
  return index < 0 ? null : packagePath.slice(index + marker.length);
}

function assertExactDeclarations(label, declarations) {
  for (const [name, version] of sortedEntries(declarations)) {
    if (typeof version !== "string" || !EXACT_SEMVER.test(version)) {
      throw new Error(`${label} ${name} must use an exact semantic version; found ${JSON.stringify(version)}.`);
    }
  }
}

function assertSameDeclarations(label, declared, locked) {
  const expected = JSON.stringify(Object.fromEntries(sortedEntries(declared)));
  const actual = JSON.stringify(Object.fromEntries(sortedEntries(locked)));
  if (actual !== expected) {
    throw new Error(`${label} root lock declarations do not match package.json.`);
  }
}

function assertSelectedDirectPackages(lockfile, declarations) {
  for (const [name, version] of sortedEntries(declarations)) {
    const selected = lockfile.packages[`node_modules/${name}`];
    if (!selected || typeof selected !== "object") {
      throw new Error(`Direct dependency ${name} has no selected package in package-lock.json.`);
    }
    if (selected.version !== version) {
      throw new Error(`Direct dependency ${name} selected ${selected.version}; expected ${version}.`);
    }
    if (!INTEGRITY.test(selected.integrity ?? "")) {
      throw new Error(`Direct dependency ${name} has no valid SHA-512 integrity value.`);
    }
  }
}

function assertOverrides(packageJson, lockfile) {
  const overrides = sortedEntries(packageJson.overrides);
  const packages = Object.entries(lockfile.packages).filter(([packagePath]) => packagePath);
  for (const [name, version] of overrides) {
    if (typeof version !== "string") {
      throw new Error(`Override ${name} must use the simple package-name-to-version override form.`);
    }
    if (!EXACT_SEMVER.test(version)) {
      throw new Error(`Override ${name} must use an exact semantic version; found ${JSON.stringify(version)}.`);
    }
    const selected = packages.filter(([packagePath, metadata]) => packageNameFromPath(packagePath, metadata) === name);
    if (selected.length === 0) {
      throw new Error(`Override target ${name} is absent from package-lock.json.`);
    }
    for (const [packagePath, metadata] of selected) {
      if (metadata.version !== version) {
        throw new Error(`Override ${name} selected ${metadata.version} at ${packagePath}; expected ${version}.`);
      }
      if (!INTEGRITY.test(metadata.integrity ?? "")) {
        throw new Error(`Override ${name} at ${packagePath} has no valid SHA-512 integrity value.`);
      }
    }
  }
  return overrides.map(([name]) => name);
}

function assertInstallScripts(lockfile) {
  const approved = [];
  for (const [packagePath, metadata] of Object.entries(lockfile.packages)) {
    if (!packagePath || metadata?.hasInstallScript !== true) continue;
    const name = packageNameFromPath(packagePath, metadata);
    const identity = `${name}@${metadata.version}`;
    if (!APPROVED_INSTALL_SCRIPTS.has(identity)) {
      throw new Error(`Unreviewed npm install script package ${identity} at ${packagePath}.`);
    }
    if (!INTEGRITY.test(metadata.integrity ?? "")) {
      throw new Error(`Install script package ${identity} has no valid SHA-512 integrity value.`);
    }
    approved.push(identity);
  }
  return [...new Set(approved)].sort();
}

export function validateNpmDependencyContract(packageJson, lockfile) {
  if (!packageJson || typeof packageJson !== "object") throw new Error("package.json must be an object.");
  if (lockfile?.lockfileVersion !== 3 || typeof lockfile.packages !== "object") {
    throw new Error("npm dependency contract requires package-lock.json lockfileVersion 3.");
  }
  const root = lockfile.packages[""];
  if (!root || typeof root !== "object") throw new Error("package-lock.json has no root package entry.");

  const dependencies = packageJson.dependencies ?? {};
  const devDependencies = packageJson.devDependencies ?? {};
  assertExactDeclarations("Dependency", dependencies);
  assertExactDeclarations("Development dependency", devDependencies);
  assertSameDeclarations("Dependency", dependencies, root.dependencies ?? {});
  assertSameDeclarations("Development dependency", devDependencies, root.devDependencies ?? {});
  assertSelectedDirectPackages(lockfile, { ...dependencies, ...devDependencies });

  return {
    directDependencies: sortedEntries(dependencies).map(([name]) => name),
    directDevDependencies: sortedEntries(devDependencies).map(([name]) => name),
    installScripts: assertInstallScripts(lockfile),
    overrides: assertOverrides(packageJson, lockfile),
  };
}

export async function checkNpmDependencyContract({
  packagePath = "package.json",
  lockfilePath = "package-lock.json",
  outputPath = "out/security/npm-contract.json",
} = {}) {
  const [packageText, lockfileText] = await Promise.all([
    fs.readFile(packagePath, "utf8"),
    fs.readFile(lockfilePath, "utf8"),
  ]);
  const summary = validateNpmDependencyContract(JSON.parse(packageText), JSON.parse(lockfileText));
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await fs.writeFile(outputPath, `${JSON.stringify(summary, null, 2)}\n`, "utf8");
  return summary;
}

const invokedDirectly = process.argv[1]
  && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedDirectly) {
  checkNpmDependencyContract()
    .then((summary) => process.stdout.write(
      `${summary.directDependencies.length} production, ${summary.directDevDependencies.length} development, ${summary.overrides.length} overrides, ${summary.installScripts.length} install-script packages verified\n`,
    ))
    .catch((error) => {
      process.stderr.write(`${error.message}\n`);
      process.exitCode = 1;
    });
}
