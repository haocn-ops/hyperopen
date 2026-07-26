#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

function parseArguments(args) {
  let repoRoot = process.cwd();
  let checkArtifact = false;
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === "--artifact") {
      checkArtifact = true;
    } else if (argument === "--repo") {
      const value = args[index + 1];
      if (!value || value.startsWith("--")) {
        throw new Error("--repo requires a path");
      }
      repoRoot = path.resolve(value);
      index += 1;
    } else if (argument === "--help") {
      console.log("Usage: node preflight.mjs [--repo <path>] [--artifact]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${argument}`);
    }
  }
  return { checkArtifact, repoRoot: path.resolve(repoRoot) };
}

const passes = [];
const warnings = [];
const failures = [];
const pass = (message) => passes.push(message);
const warn = (message) => warnings.push(message);
const fail = (message) => failures.push(message);

function fileExists(filePath) {
  try {
    return fs.statSync(filePath).isFile();
  } catch (_error) {
    return false;
  }
}

function readJson(filePath, label) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch (error) {
    fail(`${label} is not readable JSON-compatible JSONC: ${error.message}`);
    return null;
  }
}

function requireFile(repoRoot, relativePath) {
  if (fileExists(path.join(repoRoot, relativePath))) {
    pass(`${relativePath} exists`);
  } else {
    fail(`${relativePath} is missing`);
  }
}

function commandWorks(command, args, env = process.env) {
  return spawnSync(command, args, {
    env,
    encoding: "utf8",
    stdio: "pipe",
  }).status === 0;
}

function collectJavaScriptFiles(directoryPath) {
  const stat = fs.lstatSync(directoryPath);
  if (stat.isSymbolicLink()) {
    throw new Error(`symbolic link is not allowed in the artifact JavaScript tree: ${directoryPath}`);
  }
  const files = [];
  for (const entry of fs.readdirSync(directoryPath, { withFileTypes: true })) {
    const entryPath = path.join(directoryPath, entry.name);
    if (entry.isSymbolicLink()) {
      throw new Error(`symbolic link is not allowed in the artifact JavaScript tree: ${entryPath}`);
    }
    if (entry.isDirectory()) {
      files.push(...collectJavaScriptFiles(entryPath));
    } else if (entry.isFile() && entry.name.endsWith(".js")) {
      files.push(entryPath);
    }
  }
  return files.sort();
}

function checkPackageContract(repoRoot) {
  const packageJson = readJson(path.join(repoRoot, "package.json"), "package.json");
  if (!packageJson) return;

  const expectedScripts = {
    "build:cloudflare": "node tools/cloudflare/build_dexhelm_release.mjs",
    "cloudflare:check": "npm run build:cloudflare && wrangler deploy --dry-run",
    "cloudflare:dev": "npm run build:cloudflare && wrangler dev --local",
    "deploy:cloudflare": "npm run build:cloudflare && wrangler deploy",
    "test:cloudflare-worker": "node --test tools/cloudflare/*.test.mjs",
    "verify:cloudflare-worker": "node tools/cloudflare/verify_cloudflare_worker.mjs",
  };

  for (const [name, expected] of Object.entries(expectedScripts)) {
    const actual = packageJson.scripts?.[name];
    actual === expected
      ? pass(`package script ${name} matches the release contract`)
      : fail(`package script ${name} must be ${JSON.stringify(expected)}; found ${JSON.stringify(actual)}`);
  }

  const wranglerVersion = packageJson.devDependencies?.wrangler;
  if (typeof wranglerVersion === "string" && /^\d+\.\d+\.\d+$/.test(wranglerVersion)) {
    pass(`wrangler is exactly pinned at ${wranglerVersion}`);
  } else {
    fail(`wrangler must be an exact devDependency version; found ${JSON.stringify(wranglerVersion)}`);
  }

  fileExists(path.join(repoRoot, "node_modules", ".bin", "wrangler"))
    ? pass("local Wrangler binary is installed")
    : fail("local Wrangler binary is missing; run npm run setup:worktree or npm ci");
}

function checkWranglerContract(repoRoot) {
  const config = readJson(path.join(repoRoot, "wrangler.jsonc"), "wrangler.jsonc");
  if (!config) return;

  const expected = [
    [config.name === "hyperopen", "Worker name is hyperopen"],
    [config.main === "./workers/hyperopen-worker.mjs", "Worker main module is repository-owned"],
    [config.workers_dev === true, "workers.dev publishing is enabled"],
    [config.assets?.directory === "./out/white-label/dexhelm", "static asset directory is the verified DEXHelm white-label release"],
    [config.assets?.binding === "ASSETS", "static asset binding is ASSETS"],
    [config.vars?.HYPERUNIT_MAINNET_URL === "https://api.hyperunit.xyz", "mainnet upstream is fixed"],
    [config.vars?.HYPERUNIT_TESTNET_URL === "https://api.hyperunit-testnet.xyz", "testnet upstream is fixed"],
  ];
  for (const [condition, message] of expected) {
    condition ? pass(message) : fail(message);
  }

  const workerFirst = config.assets?.run_worker_first;
  const dexhelmDomains = [
    "dexhelm.com",
    "app.dexhelm.com",
    "testnet.dexhelm.com",
    "status.dexhelm.com",
  ];
  const configuredCustomDomains = Array.isArray(config.routes)
    ? config.routes
        .filter((route) => route?.custom_domain === true && typeof route.pattern === "string")
        .map((route) => route.pattern)
        .sort()
    : [];
  const hasExactDexhelmDomains =
    JSON.stringify(configuredCustomDomains) === JSON.stringify([...dexhelmDomains].sort());

  if (config.routes === undefined) {
    pass("no custom domains are configured");
  } else if (hasExactDexhelmDomains) {
    pass("custom domains are limited to the four DEXHelm public surfaces");
  } else {
    fail("custom-domain routes must be absent or contain only the four exact DEXHelm hostnames");
  }

  if (Array.isArray(workerFirst) && workerFirst.length === 2 && workerFirst.includes("/api/health") && workerFirst.includes("/api/hyperunit/*")) {
    pass("Worker-first routes are limited to health and the HyperUnit proxy");
  } else if (workerFirst === true && hasExactDexhelmDomains) {
    const workerSource = fs.readFileSync(
      path.join(repoRoot, "workers", "hyperopen-worker.mjs"),
      "utf8"
    );
    const hasHostPolicy = dexhelmDomains.every((hostname) => workerSource.includes(hostname));
    hasHostPolicy
      ? pass("Worker-first delivery is enabled for the validated DEXHelm host policy")
      : fail("Worker-first delivery requires an explicit policy for every DEXHelm hostname");
  } else {
    fail("assets.run_worker_first must use the narrow API rules or the validated DEXHelm host policy");
  }

  /^\d{4}-\d{2}-\d{2}$/.test(config.compatibility_date ?? "")
    ? pass(`compatibility_date is set to ${config.compatibility_date}`)
    : fail("wrangler compatibility_date is missing or invalid");
}

function checkRuntime(repoRoot) {
  const configuredJava = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, "bin", "java") : null;
  const homebrewJavaHome = "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home";

  if (configuredJava && fileExists(configuredJava)) {
    pass(`JAVA_HOME points to a Java runtime: ${process.env.JAVA_HOME}`);
  } else if (commandWorks("java", ["-version"])) {
    pass("java is available on PATH");
  } else if (fileExists(path.join(homebrewJavaHome, "bin", "java"))) {
    warn(`JDK 21 is installed but not configured; use JAVA_HOME=${homebrewJavaHome}`);
  } else {
    fail("a usable JDK 21 was not found");
  }

  const nodeMajor = Number.parseInt(process.versions.node.split(".")[0], 10);
  if (nodeMajor >= 25 && !process.env.NODE_OPTIONS?.includes("--localstorage-file=")) {
    warn("Node 25+ may expose incomplete localStorage; run gates with NODE_OPTIONS=--localstorage-file=/tmp/hyperopen-node-gates-localstorage");
  } else {
    pass(`Node ${process.versions.node} options are suitable for the known gate environment`);
  }

  if (fileExists(path.join(repoRoot, ".wrangler", "state", "v3", "cache", "miniflare-CacheObject", "metadata.sqlite"))) {
    pass("local Wrangler state is present but is not a deployment input");
  }
}

function checkSourceDefaults(repoRoot) {
  const sourcePaths = [
    "src/hyperopen/funding/effects/common.cljs",
    "src/hyperopen/api/endpoints/funding_hyperunit.cljs",
  ];
  const contents = sourcePaths
    .filter((relativePath) => fileExists(path.join(repoRoot, relativePath)))
    .map((relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), "utf8"))
    .join("\n");

  if (contents.includes("https://api.hyperunit.xyz") && contents.includes("https://api.hyperunit-testnet.xyz")) {
    pass("source retains direct HyperUnit development defaults");
  } else {
    warn("direct HyperUnit development defaults were not found in their current source files; inspect whether the source contract intentionally changed");
  }
}

function checkArtifact(repoRoot) {
  const releaseRoot = path.join(repoRoot, "out", "white-label", "dexhelm");
  const javascriptRoot = path.join(releaseRoot, "js");
  for (const relativePath of ["_headers", "trade.html", "site-metadata.json", "sw.js", "tenant-manifest.json", "brand/dexhelm-mark.svg"]) {
    fileExists(path.join(releaseRoot, relativePath))
      ? pass(`release artifact contains ${relativePath}`)
      : fail(`release artifact is missing ${relativePath}`);
  }
  if (!fs.existsSync(javascriptRoot)) {
    fail("release artifact JavaScript directory is missing; run npm run build:cloudflare");
    return;
  }

  try {
    const files = collectJavaScriptFiles(javascriptRoot);
    const contents = files.map((filePath) => fs.readFileSync(filePath, "utf8")).join("\n");
    /https:\/\/api\.hyperunit(?:-testnet)?\.xyz/.test(contents)
      ? fail("release JavaScript still contains a direct HyperUnit origin")
      : pass("release JavaScript contains no direct HyperUnit origin");
    contents.includes("/api/hyperunit/mainnet") && contents.includes("/api/hyperunit/testnet")
      ? pass("release JavaScript contains both same-origin proxy bases")
      : fail("release JavaScript is missing one or both same-origin HyperUnit proxy bases");

    const tenantManifest = readJson(path.join(releaseRoot, "tenant-manifest.json"), "tenant-manifest.json");
    if (tenantManifest) {
      tenantManifest.tenant?.["tenant/id"] === "dexhelm" &&
      tenantManifest.tenant?.["brand/name"] === "DEXHelm" &&
      tenantManifest.canonicalOrigin === "https://testnet.dexhelm.com"
        ? pass("release tenant manifest identifies DEXHelm on the Testnet origin")
        : fail("release tenant manifest must identify DEXHelm on the Testnet origin");
    }
  } catch (error) {
    fail(`release artifact JavaScript validation failed: ${error.message}`);
  }
}

function printResults() {
  passes.forEach((message) => console.log(`PASS ${message}`));
  warnings.forEach((message) => console.log(`WARN ${message}`));
  failures.forEach((message) => console.error(`FAIL ${message}`));
  console.log(`Preflight summary: ${passes.length} passed, ${warnings.length} warnings, ${failures.length} failures`);
}

try {
  const { checkArtifact: shouldCheckArtifact, repoRoot } = parseArguments(process.argv.slice(2));
  for (const relativePath of [
    "package.json",
    "package-lock.json",
    "wrangler.jsonc",
    "workers/hyperopen-worker.mjs",
    "config/white-label/dexhelm.json",
    "resources/public/brand/dexhelm-mark.svg",
    "tools/cloudflare/build_dexhelm_release.mjs",
    "tools/cloudflare/rewrite_hyperunit_release_endpoints.mjs",
    "tools/cloudflare/verify_cloudflare_worker.mjs",
    "tools/release-assets/verify_deployment_headers.mjs",
  ]) {
    requireFile(repoRoot, relativePath);
  }
  checkPackageContract(repoRoot);
  checkWranglerContract(repoRoot);
  checkRuntime(repoRoot);
  checkSourceDefaults(repoRoot);
  if (shouldCheckArtifact) checkArtifact(repoRoot);
  printResults();
  if (failures.length > 0) process.exitCode = 1;
} catch (error) {
  console.error(`FAIL ${error.message}`);
  process.exitCode = 1;
}
