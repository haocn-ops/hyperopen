import fs from "node:fs/promises";
import path from "node:path";
import { execFile } from "node:child_process";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const selectedLine = /^(?:(?<transitive>\s+\.\s+)|)(?<name>[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+)\s+(?<version>\S+)(?:\s+:newer-version)?$/;
const coordinate = /^[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+$/;

function mavenName(name) {
  return name.replace("/", ":");
}

export function parseDependencyTree(treeText) {
  if (typeof treeText !== "string" || !treeText.trim()) {
    throw new Error("Clojure dependency tree is empty.");
  }
  const versions = new Map();
  for (const [index, rawLine] of treeText.split(/\r?\n/).entries()) {
    if (!rawLine.trim()) continue;
    if (/^\s*X\s+/.test(rawLine)) continue;
    const match = rawLine.match(selectedLine);
    if (!match || (match.groups.transitive && !/^\s+\.\s+/.test(rawLine))) {
      throw new Error(`Malformed selected dependency tree line ${index + 1}: ${rawLine}`);
    }
    const name = mavenName(match.groups.name);
    const version = match.groups.version;
    if (!version || version === "X" || !coordinate.test(name)) {
      throw new Error(`Invalid selected dependency at line ${index + 1}.`);
    }
    const previous = versions.get(name);
    if (previous && previous !== version) {
      throw new Error(`Conflicting selected versions for ${name}: ${previous} and ${version}.`);
    }
    versions.set(name, version);
  }
  if (!versions.size) throw new Error("Clojure dependency tree has no selected dependencies.");
  return [...versions.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([name, version]) => ({ name, version }));
}

export function parseDirectDependencies(depsText) {
  if (typeof depsText !== "string") throw new Error("deps.edn content is required.");
  const direct = new Map();
  const pattern = /([A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+)\s+\{\s*:mvn\/version\s+"([^"]+)"/g;
  for (const match of depsText.matchAll(pattern)) {
    const name = mavenName(match[1]);
    const version = match[2];
    const previous = direct.get(name);
    if (previous && previous !== version) {
      throw new Error(`Conflicting direct versions for ${name}: ${previous} and ${version}.`);
    }
    direct.set(name, version);
  }
  if (!direct.size) throw new Error("No Maven dependencies found in deps.edn.");
  return [...direct.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([name, version]) => ({ name, version }));
}

export function validateDirectDependencies(dependencies, directDependencies) {
  const selected = new Map(dependencies.map((entry) => [entry.name, entry.version]));
  for (const direct of directDependencies) {
    const actual = selected.get(direct.name);
    if (!actual) throw new Error(`Direct dependency ${direct.name} is absent from the selected tree.`);
    if (actual !== direct.version) {
      throw new Error(`Direct dependency ${direct.name} is ${direct.version}, selected ${actual}.`);
    }
  }
  return true;
}

export function buildLockfile({ treeText, depsText, clojureCliVersion = "unknown", command = "clojure -A:dev:test -Stree" }) {
  const dependencies = parseDependencyTree(treeText);
  const directDependencies = parseDirectDependencies(depsText);
  validateDirectDependencies(dependencies, directDependencies);
  return {
    schemaVersion: 1,
    clojureCliVersion,
    command,
    directDependencies,
    dependencies,
  };
}

export function validateLockfileFreshness(lockfile, depsText) {
  if (!lockfile || lockfile.schemaVersion !== 1 || !Array.isArray(lockfile.directDependencies)) {
    throw new Error("Clojure dependency lockfile has no direct-dependency inventory; regenerate it.");
  }
  const expected = parseDirectDependencies(depsText);
  const actual = lockfile.directDependencies;
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error("Clojure dependency lockfile is stale for deps.edn; regenerate it.");
  }
  validateDirectDependencies(lockfile.dependencies, expected);
  return true;
}

export async function checkLockfileFreshness({
  depsPath = "deps.edn",
  lockfilePath = "tools/security/clojure-dependencies.lock.json",
} = {}) {
  const [depsText, lockfileText] = await Promise.all([
    fs.readFile(depsPath, "utf8"),
    fs.readFile(lockfilePath, "utf8"),
  ]);
  let lockfile;
  try {
    lockfile = JSON.parse(lockfileText);
  } catch {
    throw new Error("Clojure dependency lockfile is not valid JSON.");
  }
  validateLockfileFreshness(lockfile, depsText);
  return lockfile;
}

async function atomicWriteJson(outputPath, value) {
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  const temporary = `${outputPath}.${process.pid}.${Date.now()}.tmp`;
  try {
    await fs.writeFile(temporary, `${JSON.stringify(value, null, 2)}\n`, "utf8");
    await fs.rename(temporary, outputPath);
  } catch (error) {
    await fs.rm(temporary, { force: true });
    throw error;
  }
}

export async function generateLockfile({
  depsPath = "deps.edn",
  outputPath = "tools/security/clojure-dependencies.lock.json",
  treeText,
  clojureCliVersion,
  execFileImpl = execFileAsync,
  aliases = ["dev", "test"],
} = {}) {
  const depsText = await fs.readFile(depsPath, "utf8");
  const aliasArgument = aliases.length ? `-A:${aliases.join(":")}` : null;
  const treeArguments = [aliasArgument, "-Stree"].filter(Boolean);
  const command = `clojure ${treeArguments.join(" ")}`;
  const tree = treeText ?? (await execFileImpl("clojure", treeArguments, { maxBuffer: 2 * 1024 * 1024 })).stdout;
  const cliVersion = clojureCliVersion ?? (await execFileImpl("clojure", ["--version"], { maxBuffer: 64 * 1024 })).stdout.trim();
  const lockfile = buildLockfile({ treeText: tree, depsText, clojureCliVersion: cliVersion, command });
  await atomicWriteJson(outputPath, lockfile);
  return lockfile;
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(new URL(import.meta.url).pathname)) {
  const checking = process.argv.slice(2).includes("--check");
  (checking ? checkLockfileFreshness() : generateLockfile())
    .then((lockfile) => process.stdout.write(
      `${lockfile.dependencies.length} Maven coordinates ${checking ? "verified" : "generated"}\n`,
    ))
    .catch((error) => {
      process.stderr.write(`${error.message}\n`);
      process.exitCode = 1;
    });
}
