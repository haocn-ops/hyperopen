import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const SOURCE_EXTENSIONS = new Set([".clj", ".cljc", ".cljs", ".js", ".mjs"]);
const AUTHORED_SINKS = [
  ["innerHTML", /(?:\.innerHTML\s*=|\.-innerHTML\b)/],
  ["outerHTML", /(?:\.outerHTML\s*=|\.-outerHTML\b)/],
  ["insertAdjacentHTML", /(?:\binsertAdjacentHTML\s*\(|\(\s*\.insertAdjacentHTML\b)/],
  ["document.write", /(?:\bdocument\s*\.\s*write\s*\(|\(\s*\.write\s+js\/document\b)/],
  ["eval", /(?:\beval\s*\(|\bjs\/eval\b)/],
  ["new Function", /(?:\bnew\s+Function\s*\(|\(\s*js\/Function\.)/],
];
const VENDOR_SINKS = [
  ["innerHTML", /\.innerHTML\s*=/],
  ["outerHTML", /\.outerHTML\s*=/],
  ["insertAdjacentHTML", /\binsertAdjacentHTML\s*\(/],
  ["eval", /\beval\s*\(/],
  ["new Function", /\bnew\s+Function\s*\(/],
];

async function collectFiles(root, predicate) {
  const entries = await fs.readdir(root, { withFileTypes: true });
  const files = [];
  for (const entry of entries.sort((left, right) => left.name.localeCompare(right.name))) {
    const entryPath = path.join(root, entry.name);
    if (entry.isSymbolicLink()) throw new Error(`Security scan does not follow symbolic links: ${entryPath}`);
    if (entry.isDirectory()) files.push(...await collectFiles(entryPath, predicate));
    else if (entry.isFile() && predicate(entryPath)) files.push(entryPath);
  }
  return files;
}

export async function scanAuthoredSources(sourceRoot) {
  const files = await collectFiles(sourceRoot, (filePath) => SOURCE_EXTENSIONS.has(path.extname(filePath)));
  const findings = [];
  for (const filePath of files) {
    const contents = await fs.readFile(filePath, "utf8");
    for (const [sink, pattern] of AUTHORED_SINKS) {
      if (pattern.test(contents)) findings.push(`${path.relative(sourceRoot, filePath)}:${sink}`);
    }
  }
  if (findings.length > 0) {
    throw new Error(`Unsafe authored source sink detected: ${findings.sort().join(", ")}`);
  }
  return findings;
}

function inlineThemeSource(document, themeSource) {
  const scripts = [...document.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script\s*>/gi)];
  for (const match of scripts) {
    const attributes = match[1] ?? "";
    const body = match[2] ?? "";
    const sourceMatch = attributes.match(/\bsrc\s*=\s*(["'])(.*?)\1/i);
    if (sourceMatch) {
      const source = sourceMatch[2].trim();
      if (!source.startsWith("/") || source.startsWith("//")) return { unsafe: `external script ${source}` };
    } else if (!themeSource || body !== themeSource) {
      return { unsafe: "unapproved inline script" };
    }
  }
  return { count: scripts.length };
}

async function scanReleaseHtml(releaseRoot) {
  const htmlFiles = await collectFiles(releaseRoot, (filePath) => path.extname(filePath) === ".html");
  let themeSource = null;
  try {
    themeSource = await fs.readFile(path.join(releaseRoot, "theme-preload.js"), "utf8");
  } catch (_error) {
    // Fixtures and routes without an inline theme script do not need this file.
  }
  let scriptCount = 0;
  for (const filePath of htmlFiles) {
    const contents = await fs.readFile(filePath, "utf8");
    const relativePath = path.relative(releaseRoot, filePath);
    if (/\son[a-z0-9_-]+\s*=/i.test(contents)) {
      throw new Error(`Unsafe release HTML event handler in ${relativePath}.`);
    }
    if (/\b(?:href|src|action)\s*=\s*(?:["']\s*)?javascript:/i.test(contents)) {
      throw new Error(`Unsafe release HTML javascript URL in ${relativePath}.`);
    }
    const scripts = inlineThemeSource(contents, themeSource);
    if (scripts.unsafe) throw new Error(`Unsafe release HTML in ${relativePath}: ${scripts.unsafe}.`);
    scriptCount += scripts.count;
  }
  return scriptCount;
}

async function inventoryVendorSinks(releaseRoot) {
  const javascriptRoot = path.join(releaseRoot, "js");
  try {
    const files = await collectFiles(javascriptRoot, (filePath) => path.extname(filePath) === ".js");
    const inventory = [];
    for (const filePath of files) {
      const contents = await fs.readFile(filePath, "utf8");
      for (const [sink, pattern] of VENDOR_SINKS) {
        if (pattern.test(contents)) inventory.push(`${path.relative(releaseRoot, filePath)}:${sink}`);
      }
    }
    return inventory.sort();
  } catch (error) {
    if (error?.code === "ENOENT") return [];
    throw error;
  }
}

export async function assertReleaseXssContract({ sourceRoot, releaseRoot = null }) {
  await scanAuthoredSources(sourceRoot);
  const releaseScriptCount = releaseRoot ? await scanReleaseHtml(releaseRoot) : 0;
  const vendorSinkInventory = releaseRoot ? await inventoryVendorSinks(releaseRoot) : [];
  return { authoredSinkCount: 0, releaseScriptCount, vendorSinkInventory };
}

function argumentValue(name) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : null;
}

const invokedDirectly = process.argv[1]
  && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedDirectly) {
  const sourceRoot = path.resolve(argumentValue("--source-root") ?? "src/hyperopen");
  const releaseArgument = argumentValue("--release-root");
  assertReleaseXssContract({ sourceRoot, releaseRoot: releaseArgument ? path.resolve(releaseArgument) : null })
    .then((summary) => process.stdout.write(`${JSON.stringify(summary)}\n`))
    .catch((error) => {
      process.stderr.write(`${error.message}\n`);
      process.exitCode = 1;
    });
}
