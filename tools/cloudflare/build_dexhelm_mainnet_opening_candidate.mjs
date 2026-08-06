import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  hashFullContent,
  immutableAssetPathsForReleaseFiles,
} from "../release-assets/generate_release_artifacts.mjs";
import {
  SECURITY_HEADERS_FILE_PATH,
  buildImmutableAssetHeaderBlocks,
} from "../release-assets/security_headers.mjs";
import { verifyWhiteLabelRelease as defaultVerifyWhiteLabelRelease } from "../white-label/verify_release.mjs";
import {
  buildDexhelmCloudflareRelease,
  DEXHELM_CANONICAL_ORIGIN,
  DEXHELM_CONFIG_PATH,
  DEXHELM_OUTPUT_PATH,
  refreshTenantManifestDigests,
} from "./build_dexhelm_release.mjs";
import {
  buildDexhelmMainnetRelease,
  DEXHELM_MAINNET_CANONICAL_ORIGIN,
  DEXHELM_MAINNET_CONFIG_PATH,
  DEXHELM_MAINNET_OUTPUT_PATH,
} from "./build_dexhelm_mainnet_release.mjs";

export const MAINNET_CANDIDATE_PREFIX = "__hyperopen_mainnet";
export const MAINNET_CANDIDATE_MANIFEST = "mainnet-opening-manifest.json";
export const DEXHELM_MAINNET_CANDIDATE_OUTPUT_PATH = "out/cloudflare/dexhelm-mainnet-opening";

async function pathExists(targetPath) {
  try {
    await fs.access(targetPath);
    return true;
  } catch (_error) {
    return false;
  }
}

async function collectFiles(root, relativePath = "") {
  const currentPath = path.join(root, relativePath);
  const stat = await fs.lstat(currentPath);
  if (stat.isSymbolicLink() || !stat.isDirectory()) {
    throw new Error(`Unsafe release directory: ${currentPath}`);
  }
  const files = [];
  for (const entry of await fs.readdir(currentPath, { withFileTypes: true })) {
    const childRelativePath = path.join(relativePath, entry.name);
    const childPath = path.join(root, childRelativePath);
    if (entry.isSymbolicLink()) {
      throw new Error(`Release contains a symbolic link: ${childPath}`);
    }
    if (entry.isDirectory()) {
      files.push(...(await collectFiles(root, childRelativePath)));
    } else if (entry.isFile()) {
      files.push(childRelativePath.split(path.sep).join("/"));
    } else {
      throw new Error(`Release contains an unsupported artifact: ${childPath}`);
    }
  }
  return files.sort();
}

async function readJson(filePath, label) {
  try {
    return JSON.parse(await fs.readFile(filePath, "utf8"));
  } catch (_error) {
    throw new Error(`Invalid ${label} JSON.`);
  }
}

async function artifactDigests(root, files) {
  const digests = {};
  for (const relativePath of files) {
    digests[relativePath] = hashFullContent(await fs.readFile(path.join(root, relativePath)));
  }
  return digests;
}

async function addMainnetCacheRules(testnetRoot, mainnetRoot) {
  const mainnetFiles = await collectFiles(mainnetRoot);
  const immutableAssetPaths = immutableAssetPathsForReleaseFiles(mainnetFiles);
  const prefixedHeaderBlocks = buildImmutableAssetHeaderBlocks({
    immutableAssetPaths,
    prefix: `/${MAINNET_CANDIDATE_PREFIX}`,
  });
  if (!prefixedHeaderBlocks) {
    throw new Error("Mainnet release does not declare any immutable assets.");
  }

  const headersPath = path.join(testnetRoot, SECURITY_HEADERS_FILE_PATH);
  const existingHeaders = await fs.readFile(headersPath, "utf8");
  await fs.writeFile(
    headersPath,
    `${existingHeaders.trimEnd()}\n\n${prefixedHeaderBlocks}\n`
  );
}

function assertSourceOutputSeparation(sourceRoots, outputRoot) {
  const resolvedOutput = path.resolve(outputRoot);
  for (const sourceRoot of sourceRoots.map((root) => path.resolve(root))) {
    const outputFromSource = path.relative(sourceRoot, resolvedOutput);
    const sourceFromOutput = path.relative(resolvedOutput, sourceRoot);
    if (
      outputFromSource === "" ||
      sourceFromOutput === "" ||
      (!outputFromSource.startsWith("..") && !path.isAbsolute(outputFromSource)) ||
      (!sourceFromOutput.startsWith("..") && !path.isAbsolute(sourceFromOutput))
    ) {
      throw new Error("Candidate output must be separate from both release sources.");
    }
  }
}

function assertReleaseIdentity(manifest, { network, origin, label }) {
  if (
    manifest?.tenant?.["hyperliquid-network"] !== network ||
    manifest?.canonicalOrigin !== origin ||
    typeof manifest?.configDigest !== "string" ||
    !manifest.configDigest ||
    typeof manifest?.mainScriptHref !== "string"
  ) {
    throw new Error(`${label} release identity does not match the candidate contract.`);
  }
}

async function assertNetworkJavaScript(root, { network }) {
  const jsRoot = path.join(root, "js");
  const javascriptFiles = (await collectFiles(jsRoot)).filter((file) => file.endsWith(".js"));
  let contents = "";
  for (const relativePath of javascriptFiles) {
    contents += await fs.readFile(path.join(jsRoot, relativePath), "utf8");
  }
  if (/https:\/\/api\.hyperunit(?:-testnet)?\.xyz/.test(contents)) {
    throw new Error(`${network} candidate contains a direct HyperUnit origin.`);
  }
  const expectedProxy = `/api/hyperunit/${network}`;
  const unexpectedProxy = `/api/hyperunit/${network === "mainnet" ? "testnet" : "mainnet"}`;
  if (!contents.includes(expectedProxy) || contents.includes(unexpectedProxy)) {
    throw new Error(`${network} candidate proxy authority does not match its network.`);
  }
}

async function verifyNestedWhiteLabelReleases(
  outputRoot,
  { repositoryRoot, verifyWhiteLabelRelease }
) {
  const resolvedOutput = path.resolve(outputRoot);
  const verificationParent = await fs.mkdtemp(
    path.join(path.dirname(resolvedOutput), ".mainnet-opening-verify-")
  );
  const isolatedTestnetRoot = path.join(verificationParent, "testnet");
  try {
    await fs.mkdir(isolatedTestnetRoot, { recursive: true });
    for (const entry of await fs.readdir(resolvedOutput, { withFileTypes: true })) {
      if ([MAINNET_CANDIDATE_PREFIX, MAINNET_CANDIDATE_MANIFEST].includes(entry.name)) {
        continue;
      }
      await fs.cp(
        path.join(resolvedOutput, entry.name),
        path.join(isolatedTestnetRoot, entry.name),
        { recursive: true }
      );
    }
    await verifyWhiteLabelRelease({
      repositoryRoot,
      configPath: DEXHELM_CONFIG_PATH,
      canonicalOrigin: DEXHELM_CANONICAL_ORIGIN,
      outputPath: isolatedTestnetRoot,
      allowedOutputRoot: verificationParent,
    });
    await verifyWhiteLabelRelease({
      repositoryRoot,
      configPath: DEXHELM_MAINNET_CONFIG_PATH,
      canonicalOrigin: DEXHELM_MAINNET_CANONICAL_ORIGIN,
      outputPath: path.join(resolvedOutput, MAINNET_CANDIDATE_PREFIX),
      allowedOutputRoot: resolvedOutput,
    });
  } finally {
    await fs.rm(verificationParent, { recursive: true, force: true });
  }
}

export async function verifyDexhelmMainnetOpeningCandidate(outputRoot, options = {}, dependencies = {}) {
  const resolvedOutput = path.resolve(outputRoot);
  const repositoryRoot = path.resolve(options.repositoryRoot || process.cwd());
  const verifyWhiteLabelRelease = dependencies.verifyWhiteLabelRelease || defaultVerifyWhiteLabelRelease;
  const manifest = await readJson(
    path.join(resolvedOutput, MAINNET_CANDIDATE_MANIFEST),
    "Mainnet opening manifest"
  );
  const files = (await collectFiles(resolvedOutput))
    .filter((file) => file !== MAINNET_CANDIDATE_MANIFEST);
  const expectedFiles = Object.keys(manifest?.artifactDigests || {}).sort();
  if (JSON.stringify(files) !== JSON.stringify(expectedFiles)) {
    throw new Error("Candidate artifact digest inventory does not match the release.");
  }
  for (const relativePath of files) {
    const digest = hashFullContent(await fs.readFile(path.join(resolvedOutput, relativePath)));
    if (manifest.artifactDigests[relativePath] !== digest) {
      throw new Error(`Candidate artifact digest does not match: ${relativePath}`);
    }
  }

  const testnetManifest = await readJson(
    path.join(resolvedOutput, "tenant-manifest.json"),
    "Testnet tenant manifest"
  );
  const mainnetRoot = path.join(resolvedOutput, MAINNET_CANDIDATE_PREFIX);
  const mainnetManifest = await readJson(
    path.join(mainnetRoot, "tenant-manifest.json"),
    "Mainnet tenant manifest"
  );
  assertReleaseIdentity(testnetManifest, {
    network: "testnet",
    origin: "https://testnet.dexhelm.com",
    label: "Testnet",
  });
  assertReleaseIdentity(mainnetManifest, {
    network: "mainnet",
    origin: "https://app.dexhelm.com",
    label: "Mainnet",
  });
  await assertNetworkJavaScript(resolvedOutput, { network: "testnet" });
  await assertNetworkJavaScript(mainnetRoot, { network: "mainnet" });
  await verifyNestedWhiteLabelReleases(resolvedOutput, {
    repositoryRoot,
    verifyWhiteLabelRelease,
  });

  return {
    outputRoot: resolvedOutput,
    testnetOrigin: testnetManifest.canonicalOrigin,
    mainnetOrigin: mainnetManifest.canonicalOrigin,
    artifactCount: files.length,
  };
}

export async function assembleDexhelmMainnetOpeningCandidate({
  testnetRoot,
  mainnetRoot,
  outputRoot,
}, options = {}, dependencies = {}) {
  const resolvedTestnet = path.resolve(testnetRoot);
  const resolvedMainnet = path.resolve(mainnetRoot);
  const resolvedOutput = path.resolve(outputRoot);
  assertSourceOutputSeparation([resolvedTestnet, resolvedMainnet], resolvedOutput);
  await collectFiles(resolvedTestnet);
  await collectFiles(resolvedMainnet);

  const outputParent = path.dirname(resolvedOutput);
  await fs.mkdir(outputParent, { recursive: true });
  const stagingParent = await fs.mkdtemp(path.join(outputParent, ".mainnet-opening-staging-"));
  const stagedRelease = path.join(stagingParent, "release");
  let backupPath = null;
  try {
    await fs.cp(resolvedTestnet, stagedRelease, { recursive: true });
    await addMainnetCacheRules(stagedRelease, resolvedMainnet);
    await refreshTenantManifestDigests(stagedRelease);
    await fs.cp(resolvedMainnet, path.join(stagedRelease, MAINNET_CANDIDATE_PREFIX), {
      recursive: true,
    });
    const testnetManifest = await readJson(
      path.join(stagedRelease, "tenant-manifest.json"),
      "Testnet tenant manifest"
    );
    const mainnetManifest = await readJson(
      path.join(stagedRelease, MAINNET_CANDIDATE_PREFIX, "tenant-manifest.json"),
      "Mainnet tenant manifest"
    );
    const files = await collectFiles(stagedRelease);
    const manifest = {
      version: 1,
      testnet: {
        root: "/",
        canonicalOrigin: testnetManifest.canonicalOrigin,
        configDigest: testnetManifest.configDigest,
      },
      mainnet: {
        root: `/${MAINNET_CANDIDATE_PREFIX}`,
        canonicalOrigin: mainnetManifest.canonicalOrigin,
        configDigest: mainnetManifest.configDigest,
      },
      artifactDigests: await artifactDigests(stagedRelease, files),
    };
    await fs.writeFile(
      path.join(stagedRelease, MAINNET_CANDIDATE_MANIFEST),
      `${JSON.stringify(manifest, null, 2)}\n`
    );
    await verifyDexhelmMainnetOpeningCandidate(stagedRelease, options, dependencies);

    if (await pathExists(resolvedOutput)) {
      backupPath = `${resolvedOutput}.previous-${process.pid}`;
      await fs.rename(resolvedOutput, backupPath);
    }
    await fs.rename(stagedRelease, resolvedOutput);
    if (backupPath) {
      await fs.rm(backupPath, { recursive: true, force: true });
    }
    return verifyDexhelmMainnetOpeningCandidate(resolvedOutput, options, dependencies);
  } catch (error) {
    if (backupPath && !(await pathExists(resolvedOutput)) && await pathExists(backupPath)) {
      await fs.rename(backupPath, resolvedOutput).catch(() => {});
    }
    throw error;
  } finally {
    await fs.rm(stagingParent, { recursive: true, force: true });
  }
}

export async function buildDexhelmMainnetOpeningCandidate(options = {}, dependencies = {}) {
  const repositoryRoot = path.resolve(options.repositoryRoot || process.cwd());
  const buildTestnet = dependencies.buildTestnet || buildDexhelmCloudflareRelease;
  const buildMainnet = dependencies.buildMainnet || buildDexhelmMainnetRelease;
  await buildTestnet({ repositoryRoot });
  await buildMainnet({ repositoryRoot });
  return assembleDexhelmMainnetOpeningCandidate({
    testnetRoot: path.join(repositoryRoot, DEXHELM_OUTPUT_PATH),
    mainnetRoot: path.join(repositoryRoot, DEXHELM_MAINNET_OUTPUT_PATH),
    outputRoot: path.join(repositoryRoot, DEXHELM_MAINNET_CANDIDATE_OUTPUT_PATH),
  }, { repositoryRoot });
}

const invokedDirectly =
  process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedDirectly) {
  buildDexhelmMainnetOpeningCandidate()
    .then((result) => {
      process.stdout.write(
        `Built DEXHelm Mainnet opening candidate with ${result.artifactCount} artifacts at ${result.outputRoot}\n`
      );
    })
    .catch((error) => {
      process.stderr.write(`${error.message}\n`);
      process.exitCode = 1;
    });
}
