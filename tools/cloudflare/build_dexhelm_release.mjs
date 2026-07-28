import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  hashContent,
  hashFullContent,
  TENANT_MANIFEST_FILE_PATH,
} from "../release-assets/generate_release_artifacts.mjs";
import { buildWhiteLabelRelease } from "../white-label/build_release.mjs";
import { verifyWhiteLabelRelease } from "../white-label/verify_release.mjs";
import { rewriteReleaseJavaScript } from "./rewrite_hyperunit_release_endpoints.mjs";

export const DEXHELM_CONFIG_PATH = "config/white-label/dexhelm.json";
export const DEXHELM_CANONICAL_ORIGIN = "https://testnet.dexhelm.com";
export const DEXHELM_OUTPUT_PATH = "out/white-label/dexhelm";

async function collectReleaseFiles(root, relativePath = "") {
  const directoryPath = path.join(root, relativePath);
  const directoryStat = await fs.lstat(directoryPath);
  if (directoryStat.isSymbolicLink() || !directoryStat.isDirectory()) {
    throw new Error(`Unsafe DEXHelm release directory: ${directoryPath}`);
  }

  const files = [];
  for (const entry of await fs.readdir(directoryPath, { withFileTypes: true })) {
    const childRelativePath = path.join(relativePath, entry.name);
    const childPath = path.join(root, childRelativePath);
    if (entry.isSymbolicLink()) {
      throw new Error(`DEXHelm release contains a symbolic link: ${childPath}`);
    }
    if (entry.isDirectory()) {
      files.push(...(await collectReleaseFiles(root, childRelativePath)));
    } else if (entry.isFile()) {
      files.push(childRelativePath.split(path.sep).join("/"));
    } else {
      throw new Error(`DEXHelm release contains an unsupported entry: ${childPath}`);
    }
  }
  return files.sort();
}

export async function refreshTenantManifestDigests(releaseDirectory) {
  const releaseRoot = path.resolve(releaseDirectory);
  const manifestPath = path.join(releaseRoot, TENANT_MANIFEST_FILE_PATH);
  const manifest = JSON.parse(await fs.readFile(manifestPath, "utf8"));
  if (typeof manifest.mainScriptHref !== "string" || !manifest.mainScriptHref.startsWith("/js/")) {
    throw new Error("DEXHelm tenant manifest is missing its main script path.");
  }

  const files = (await collectReleaseFiles(releaseRoot)).filter(
    (filePath) => filePath !== TENANT_MANIFEST_FILE_PATH
  );
  const artifactDigests = {};
  for (const filePath of files) {
    artifactDigests[filePath] = hashFullContent(await fs.readFile(path.join(releaseRoot, filePath)));
  }

  const mainBundlePath = path.resolve(releaseRoot, manifest.mainScriptHref.replace(/^\//, ""));
  if (!mainBundlePath.startsWith(`${releaseRoot}${path.sep}`)) {
    throw new Error("DEXHelm tenant manifest main script escapes the release directory.");
  }
  const refreshed = {
    ...manifest,
    mainBundleDigest: hashContent(await fs.readFile(mainBundlePath)),
    artifactDigests,
  };
  const temporaryPath = `${manifestPath}.tmp-${process.pid}`;
  await fs.writeFile(temporaryPath, `${JSON.stringify(refreshed, null, 2)}\n`, { flag: "wx" });
  await fs.rename(temporaryPath, manifestPath);
  return refreshed;
}

export async function buildDexhelmCloudflareRelease(options = {}) {
  const repositoryRoot = path.resolve(
    options.repositoryRoot || path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "..")
  );
  const outputPath = path.join(repositoryRoot, DEXHELM_OUTPUT_PATH);
  const result = await buildWhiteLabelRelease({
    repositoryRoot,
    configPath: DEXHELM_CONFIG_PATH,
    canonicalOrigin: DEXHELM_CANONICAL_ORIGIN,
    outputPath,
  });

  await rewriteReleaseJavaScript(outputPath);
  await refreshTenantManifestDigests(outputPath);
  await verifyWhiteLabelRelease({
    repositoryRoot,
    configPath: DEXHELM_CONFIG_PATH,
    canonicalOrigin: DEXHELM_CANONICAL_ORIGIN,
    outputPath,
  });

  return result;
}

const invokedDirectly =
  process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedDirectly) {
  buildDexhelmCloudflareRelease()
    .then((result) => {
      process.stdout.write(
        `Built DEXHelm Cloudflare release ${result.configDigest} at ${result.outputPath}\n`
      );
    })
    .catch((error) => {
      process.stderr.write(`${error.message}\n`);
      process.exitCode = 1;
    });
}
