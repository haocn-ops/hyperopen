import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const IMMUTABLE_ACTION = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+@[0-9a-f]{40}$/;

function fail(filePath, message) {
  throw new Error(`${filePath}: ${message}`);
}

export function validateWorkflowSecurity({ filePath, source }) {
  if (typeof source !== "string") fail(filePath, "workflow source must be text.");

  const actionPins = [...source.matchAll(/^\s*(?:-\s*)?uses:\s*([^\s#]+)/gm)]
    .map((match) => match[1])
    .filter((reference) => !reference.startsWith("./"));
  for (const reference of actionPins) {
    if (!IMMUTABLE_ACTION.test(reference)) {
      fail(filePath, `third-party action ${reference} must use an immutable 40-character commit.`);
    }
  }

  if (/^\s*[A-Za-z0-9_-]+:\s*write\s*(?:#.*)?$/m.test(source)) {
    fail(filePath, "workflow must not grant a write permission.");
  }
  if (/\bgit\s+push\b/i.test(source)) fail(filePath, "workflow must not run git push.");
  if (/\bgit\s+commit\b/i.test(source)) fail(filePath, "workflow must not create commits.");

  const npmInstallLines = source.split(/\r?\n/).filter((line) => /\bnpm\s+ci\b/.test(line));
  for (const line of npmInstallLines) {
    if (!/--ignore-scripts\b/.test(line)) {
      fail(filePath, "every npm ci command must include --ignore-scripts.");
    }
  }
  if (npmInstallLines.length > 0 && !/\bnpm\s+run\s+security:npm-contract\b/.test(source)) {
    fail(filePath, "workflow must run the npm contract after installation.");
  }

  const archiveDownloadIndex = source.search(/\bcurl\b[^\n]*\.(?:tar\.gz|tgz|zip)\b/);
  if (archiveDownloadIndex !== -1) {
    const checksumIndex = source.search(/\bsha256sum\s+-c\s+-(?:\s|$)/m);
    const extractionIndex = source.search(/\b(?:tar\s+-|unzip\s+)/);
    if (checksumIndex < archiveDownloadIndex
        || (extractionIndex !== -1 && checksumIndex > extractionIndex)) {
      fail(filePath, "downloaded archives require a SHA-256 checksum before extraction.");
    }
  }

  return {
    actionPins: actionPins.sort(),
    npmInstallCount: npmInstallLines.length,
  };
}

export async function checkCiWorkflowContract({ workflowRoot = ".github/workflows" } = {}) {
  const entries = await fs.readdir(workflowRoot, { withFileTypes: true });
  const summaries = [];
  for (const entry of entries.sort((left, right) => left.name.localeCompare(right.name))) {
    if (!entry.isFile() || !/\.ya?ml$/i.test(entry.name)) continue;
    const filePath = path.join(workflowRoot, entry.name);
    summaries.push({
      filePath,
      ...validateWorkflowSecurity({ filePath, source: await fs.readFile(filePath, "utf8") }),
    });
  }
  return summaries;
}

const invokedDirectly = process.argv[1]
  && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedDirectly) {
  checkCiWorkflowContract()
    .then((summaries) => process.stdout.write(
      `${summaries.length} workflows verified with immutable actions and read-only authority\n`,
    ))
    .catch((error) => {
      process.stderr.write(`${error.message}\n`);
      process.exitCode = 1;
    });
}
