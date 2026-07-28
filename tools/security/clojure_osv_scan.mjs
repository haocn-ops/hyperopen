import fs from "node:fs/promises";
import path from "node:path";

import { checkLockfileFreshness } from "./clojure_tree.mjs";

const DEFAULT_ENDPOINT = "https://api.osv.dev/v1/querybatch";
const DEFAULT_BATCH_SIZE = 32;

function validateInventory(lockfile) {
  if (!lockfile || lockfile.schemaVersion !== 1 || !Array.isArray(lockfile.dependencies) || !lockfile.dependencies.length) {
    throw new Error("Clojure dependency lockfile is invalid.");
  }
  const seen = new Set();
  return lockfile.dependencies.map((entry) => {
    if (!entry || typeof entry.name !== "string" || !/^[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+$/.test(entry.name)
      || typeof entry.version !== "string" || !entry.version.trim()) {
      throw new Error("Clojure dependency lockfile contains an invalid coordinate.");
    }
    if (seen.has(entry.name)) throw new Error(`Duplicate locked coordinate ${entry.name}.`);
    seen.add(entry.name);
    return { name: entry.name, version: entry.version };
  });
}

function normalizeAdvisory(advisory) {
  if (!advisory || typeof advisory !== "object") throw new Error("OSV returned an invalid advisory.");
  const id = typeof advisory.id === "string" && advisory.id.trim() ? advisory.id.trim() : null;
  if (!id) throw new Error("OSV returned an advisory without an id.");
  return {
    id,
    summary: typeof advisory.summary === "string" ? advisory.summary : undefined,
    aliases: Array.isArray(advisory.aliases) ? advisory.aliases.filter((alias) => typeof alias === "string") : [],
  };
}

export async function scanInventory(lockfile, { fetchImpl = fetch, endpoint = DEFAULT_ENDPOINT, batchSize = DEFAULT_BATCH_SIZE } = {}) {
  const dependencies = validateInventory(lockfile);
  if (!Number.isInteger(batchSize) || batchSize < 1 || batchSize > 100) throw new Error("OSV batch size is invalid.");
  const results = [];
  for (let offset = 0; offset < dependencies.length; offset += batchSize) {
    const batch = dependencies.slice(offset, offset + batchSize);
    const response = await fetchImpl(endpoint, {
      method: "POST",
      headers: { "content-type": "application/json", accept: "application/json" },
      body: JSON.stringify({ queries: batch.map(({ name, version }) => ({ package: { ecosystem: "Maven", name }, version })) }),
    });
    if (!response || !response.ok) throw new Error(`OSV query failed with status ${response?.status ?? "unknown"}.`);
    let payload;
    try { payload = await response.json(); } catch { throw new Error("OSV returned invalid JSON."); }
    if (!payload || !Array.isArray(payload.results) || payload.results.length !== batch.length) {
      throw new Error("OSV response does not match the query batch.");
    }
    payload.results.forEach((result, index) => {
      if (!result || (result.vulns !== undefined && result.vulns !== null && !Array.isArray(result.vulns))) throw new Error("OSV result is malformed.");
      results.push({ ...batch[index], advisories: (result.vulns ?? []).map(normalizeAdvisory) });
    });
  }
  return { schemaVersion: 1, ecosystem: "Maven", dependencies: results };
}

export function assertNoAdvisories(report) {
  const vulnerable = report.dependencies.filter((entry) => entry.advisories.length);
  if (vulnerable.length) {
    throw new Error(`OSV reported ${vulnerable.length} vulnerable Maven coordinate(s): ${vulnerable.map((entry) => `${entry.name}@${entry.version}`).join(", ")}.`);
  }
}

export async function auditLockfile({ depsPath = "deps.edn", lockfilePath = "tools/security/clojure-dependencies.lock.json", outputPath = "out/security/clojure-osv.json", fetchImpl = fetch, endpoint, batchSize } = {}) {
  await checkLockfileFreshness({ depsPath, lockfilePath });
  const lockfile = JSON.parse(await fs.readFile(lockfilePath, "utf8"));
  const report = await scanInventory(lockfile, { fetchImpl, endpoint, batchSize });
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await fs.writeFile(outputPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  assertNoAdvisories(report);
  return report;
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(new URL(import.meta.url).pathname)) {
  auditLockfile()
    .then((report) => process.stdout.write(`${report.dependencies.length} Maven coordinates scanned\n`))
    .catch((error) => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; });
}
