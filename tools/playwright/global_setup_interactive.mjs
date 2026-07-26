import vm from "node:vm";

const DEFAULT_BASE_URL = "http://127.0.0.1:8080";
const READY_TIMEOUT_MS = 120_000;
const POLL_MS = 500;
const DEV_ASSET_PATHS = ["/js/manifest.json", "/js/main.js"];

function sleep(ms) {
  return new Promise(resolve => {
    setTimeout(resolve, ms);
  });
}

async function assetResponse(baseUrl, assetPath) {
  try {
    return await fetch(new URL(assetPath, baseUrl), {
      cache: "no-store",
      redirect: "follow"
    });
  } catch (_error) {
    return null;
  }
}

async function manifestReady(baseUrl) {
  const response = await assetResponse(baseUrl, "/js/manifest.json");
  if (!response?.ok) {
    return false;
  }

  try {
    const manifest = await response.json();
    return Array.isArray(manifest) && manifest.length > 0;
  } catch (_error) {
    return false;
  }
}

async function mainBundleReady(baseUrl) {
  const response = await assetResponse(baseUrl, "/js/main.js");
  if (!response?.ok) {
    return false;
  }

  try {
    const source = await response.text();
    if (typeof source !== "string" || source.trim().length === 0) {
      return false;
    }

    new vm.Script(source, { filename: "main.js" });
    return true;
  } catch (_error) {
    return false;
  }
}

async function appBundleReady(baseUrl) {
  const [manifestOk, mainBundleOk] = await Promise.all([
    manifestReady(baseUrl),
    mainBundleReady(baseUrl)
  ]);
  return manifestOk && mainBundleOk;
}

export default async function globalSetup() {
  const baseUrl = process.env.PLAYWRIGHT_BASE_URL || DEFAULT_BASE_URL;
  const deadline = Date.now() + READY_TIMEOUT_MS;

  while (Date.now() < deadline) {
    if (await appBundleReady(baseUrl)) {
      return;
    }
    await sleep(POLL_MS);
  }

  throw new Error(
    `Timed out waiting for the interactive Playwright app bundle at ${baseUrl}. ` +
      `Expected parseable assets for: ${DEV_ASSET_PATHS.join(", ")}`
  );
}
