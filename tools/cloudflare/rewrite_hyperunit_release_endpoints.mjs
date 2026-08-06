import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const MAINNET_ORIGIN = "https://api.hyperunit.xyz";
const TESTNET_ORIGIN = "https://api.hyperunit-testnet.xyz";
const MAINNET_DISABLED_BASE = "/__hyperopen_disabled__/hyperunit-mainnet";
const TESTNET_DISABLED_BASE = "/__hyperopen_disabled__/hyperunit-testnet";
const MAINNET_PROXY_BASE = "/api/hyperunit/mainnet";
const TESTNET_PROXY_BASE = "/api/hyperunit/testnet";
const URL_BOUNDARY = String.raw`(?=$|[/?#"'\x60\s])`;

function usageError(message) {
  throw new Error(`${message}\nUsage: node tools/cloudflare/rewrite_hyperunit_release_endpoints.mjs [--release-directory <path>] [--network testnet|mainnet]`);
}

function parseArguments(argumentsList) {
  let releaseDirectory = path.resolve("out/release-public");
  let network = "testnet";
  for (let index = 0; index < argumentsList.length; index += 1) {
    const argument = argumentsList[index];
    if (!["--release-directory", "--network"].includes(argument)) {
      usageError(`Unknown argument: ${argument}`);
    }
    const value = argumentsList[index + 1];
    if (!value || value.startsWith("--")) {
      usageError("--release-directory requires a path.");
    }
    if (argument === "--release-directory") {
      releaseDirectory = path.resolve(value);
    } else if (["testnet", "mainnet"].includes(value)) {
      network = value;
    } else {
      usageError("--network must be testnet or mainnet.");
    }
    index += 1;
  }
  return { network, releaseDirectory };
}

function replacementPattern(origin) {
  return new RegExp(`${origin.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}${URL_BOUNDARY}`, "g");
}

function countMatches(content, pattern) {
  return [...content.matchAll(pattern)].length;
}

async function collectJavaScriptFiles(directoryPath) {
  const stat = await fs.lstat(directoryPath);
  if (stat.isSymbolicLink()) {
    throw new Error(`Release JavaScript path contains a symbolic link: ${directoryPath}`);
  }
  if (!stat.isDirectory()) {
    throw new Error(`Expected release JavaScript directory: ${directoryPath}`);
  }

  const files = [];
  const entries = await fs.readdir(directoryPath, { withFileTypes: true });
  for (const entry of entries) {
    const entryPath = path.join(directoryPath, entry.name);
    if (entry.isSymbolicLink()) {
      throw new Error(`Release JavaScript path contains a symbolic link: ${entryPath}`);
    }
    if (entry.isDirectory()) {
      files.push(...(await collectJavaScriptFiles(entryPath)));
    } else if (entry.isFile() && entry.name.endsWith(".js")) {
      files.push(entryPath);
    }
  }
  return files.sort();
}

export async function rewriteReleaseJavaScript(releaseDirectory, { network = "testnet" } = {}) {
  if (!["testnet", "mainnet"].includes(network)) {
    throw new Error("HyperUnit release network must be testnet or mainnet.");
  }
  const releaseRootStat = await fs.lstat(releaseDirectory);
  if (releaseRootStat.isSymbolicLink()) {
    throw new Error(`Release directory contains a symbolic link: ${releaseDirectory}`);
  }
  if (!releaseRootStat.isDirectory()) {
    throw new Error(`Expected release directory: ${releaseDirectory}`);
  }

  const javascriptDirectory = path.join(releaseDirectory, "js");
  const files = await collectJavaScriptFiles(javascriptDirectory);
  const mainnetPattern = replacementPattern(MAINNET_ORIGIN);
  const testnetPattern = replacementPattern(TESTNET_ORIGIN);
  const mainnetReplacement = network === "mainnet" ? MAINNET_PROXY_BASE : MAINNET_DISABLED_BASE;
  const testnetReplacement = network === "testnet" ? TESTNET_PROXY_BASE : TESTNET_DISABLED_BASE;
  const rewrites = [];
  let mainnetCount = 0;
  let testnetCount = 0;

  for (const filePath of files) {
    const contents = await fs.readFile(filePath, "utf8");
    const fileMainnetCount = countMatches(contents, mainnetPattern);
    const fileTestnetCount = countMatches(contents, testnetPattern);
    mainnetCount += fileMainnetCount;
    testnetCount += fileTestnetCount;
    rewrites.push({
      filePath,
      contents: contents
        .replace(mainnetPattern, mainnetReplacement)
        .replace(testnetPattern, testnetReplacement),
      mainnetCount: fileMainnetCount,
      testnetCount: fileTestnetCount,
    });
  }

  if (mainnetCount === 0 || testnetCount === 0) {
    const missing = [
      ...(mainnetCount === 0 ? ["mainnet"] : []),
      ...(testnetCount === 0 ? ["testnet"] : []),
    ];
    throw new Error(`Expected ${missing.join(" and ")} HyperUnit origin replacement(s) in release JavaScript.`);
  }

  for (const rewrite of rewrites) {
    if (countMatches(rewrite.contents, mainnetPattern) || countMatches(rewrite.contents, testnetPattern)) {
      throw new Error(`HyperUnit origin remains after rewrite: ${rewrite.filePath}`);
    }
  }

  // Validate every file before replacing any original, so an invalid release stays untouched.
  const temporaryPaths = [];
  try {
    for (const [index, rewrite] of rewrites.entries()) {
      const temporaryPath = path.join(
        path.dirname(rewrite.filePath),
        `.${path.basename(rewrite.filePath)}.hyperopen-rewrite-${process.pid}-${index}`
      );
      await fs.writeFile(temporaryPath, rewrite.contents, { encoding: "utf8", flag: "wx" });
      temporaryPaths.push({ filePath: rewrite.filePath, temporaryPath });
    }
    for (const { filePath, temporaryPath } of temporaryPaths) {
      await fs.rename(temporaryPath, filePath);
    }
  } finally {
    await Promise.all(
      temporaryPaths.map(({ temporaryPath }) => fs.rm(temporaryPath, { force: true }))
    );
  }

  console.log(
    `Rewrote HyperUnit release endpoints for ${network}: mainnet=${mainnetCount}, testnet=${testnetCount}.`
  );
  for (const rewrite of rewrites) {
    if (rewrite.mainnetCount || rewrite.testnetCount) {
      console.log(
        `${path.relative(releaseDirectory, rewrite.filePath)}: mainnet=${rewrite.mainnetCount}, testnet=${rewrite.testnetCount}`
      );
    }
  }
}

async function main() {
  const { network, releaseDirectory } = parseArguments(process.argv.slice(2));
  await rewriteReleaseJavaScript(releaseDirectory, { network });
}

const invokedDirectly =
  process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedDirectly) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
