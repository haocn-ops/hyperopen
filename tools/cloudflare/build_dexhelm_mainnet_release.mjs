import path from "node:path";
import { fileURLToPath } from "node:url";

import { buildWhiteLabelRelease as defaultBuildWhiteLabelRelease } from "../white-label/build_release.mjs";
import { verifyWhiteLabelRelease as defaultVerifyWhiteLabelRelease } from "../white-label/verify_release.mjs";
import { refreshTenantManifestDigests as defaultRefreshTenantManifestDigests } from "./build_dexhelm_release.mjs";
import { rewriteReleaseJavaScript as defaultRewriteReleaseJavaScript } from "./rewrite_hyperunit_release_endpoints.mjs";

export const DEXHELM_MAINNET_CONFIG_PATH = "config/white-label/dexhelm-mainnet.json";
export const DEXHELM_MAINNET_CANONICAL_ORIGIN = "https://app.dexhelm.com";
export const DEXHELM_MAINNET_OUTPUT_PATH = "out/white-label/dexhelm-mainnet";

export async function buildDexhelmMainnetRelease(options = {}, dependencies = {}) {
  const repositoryRoot = path.resolve(
    options.repositoryRoot || path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "..")
  );
  const outputPath = path.join(repositoryRoot, DEXHELM_MAINNET_OUTPUT_PATH);
  const buildWhiteLabelRelease = dependencies.buildWhiteLabelRelease || defaultBuildWhiteLabelRelease;
  const rewriteReleaseJavaScript = dependencies.rewriteReleaseJavaScript || defaultRewriteReleaseJavaScript;
  const refreshTenantManifestDigests = dependencies.refreshTenantManifestDigests || defaultRefreshTenantManifestDigests;
  const verifyWhiteLabelRelease = dependencies.verifyWhiteLabelRelease || defaultVerifyWhiteLabelRelease;

  const result = await buildWhiteLabelRelease({
    repositoryRoot,
    configPath: DEXHELM_MAINNET_CONFIG_PATH,
    canonicalOrigin: DEXHELM_MAINNET_CANONICAL_ORIGIN,
    outputPath: DEXHELM_MAINNET_OUTPUT_PATH,
  });

  await rewriteReleaseJavaScript(outputPath, { network: "mainnet" });
  await refreshTenantManifestDigests(outputPath);
  await verifyWhiteLabelRelease({
    repositoryRoot,
    configPath: DEXHELM_MAINNET_CONFIG_PATH,
    canonicalOrigin: DEXHELM_MAINNET_CANONICAL_ORIGIN,
    outputPath,
  });

  return result;
}

const invokedDirectly =
  process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedDirectly) {
  buildDexhelmMainnetRelease()
    .then((result) => {
      process.stdout.write(
        `Built DEXHelm Mainnet release ${result.configDigest} at ${result.outputPath}\n`
      );
    })
    .catch((error) => {
      process.stderr.write(`${error.message}\n`);
      process.exitCode = 1;
    });
}
