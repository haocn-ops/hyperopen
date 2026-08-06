const PUBLIC_ORIGIN_BY_HOST = Object.freeze({
  apex: "https://dexhelm.com/",
  testnet: "https://testnet.dexhelm.com/",
  mainnet: "https://app.dexhelm.com/",
  status: "https://status.dexhelm.com/",
});

const DEFAULT_EXPECTED_MAINNET_STATUS = 503;
const DEFAULT_TIMEOUT_MS = 10_000;

function expectedMainnetStatus(env = process.env) {
  const value = Number(env.DEXHELM_EXPECT_MAINNET_STATUS || DEFAULT_EXPECTED_MAINNET_STATUS);
  if (!Number.isInteger(value) || value < 100 || value > 599) {
    throw new Error("DEXHELM_EXPECT_MAINNET_STATUS must be an integer HTTP status.");
  }
  return value;
}

function expectedStatusBySurface(mainnetStatus) {
  return { apex: 200, testnet: 200, mainnet: mainnetStatus, status: 200 };
}

async function probe(fetchImpl, url, timeoutMs) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetchImpl(url, {
      method: "GET",
      redirect: "follow",
      signal: controller.signal,
      headers: { accept: "text/html,application/json" },
    });
    return {
      status: response.status,
      contentType: response.headers.get("content-type") || "",
      cacheControl: response.headers.get("cache-control") || "",
    };
  } finally {
    clearTimeout(timer);
  }
}

export async function monitorPublicSurfaces({
  fetchImpl = globalThis.fetch,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  expectedMainnetStatus = DEFAULT_EXPECTED_MAINNET_STATUS,
} = {}) {
  if (typeof fetchImpl !== "function") throw new Error("A fetch implementation is required.");
  const expected = expectedStatusBySurface(expectedMainnetStatus);
  const results = {};
  for (const [surface, url] of Object.entries(PUBLIC_ORIGIN_BY_HOST)) {
    const result = await probe(fetchImpl, url, timeoutMs);
    results[surface] = { url, ...result, expectedStatus: expected[surface] };
  }
  const healthUrl = `${PUBLIC_ORIGIN_BY_HOST.testnet}api/health`;
  const health = await probe(fetchImpl, healthUrl, timeoutMs);
  results.testnetHealth = { url: healthUrl, ...health, expectedStatus: 200 };
  const failures = Object.entries(results)
    .filter(([, result]) => result.status !== result.expectedStatus)
    .map(([surface, result]) => `${surface}: expected ${result.expectedStatus}, got ${result.status}`);
  return { ok: failures.length === 0, failures, results };
}

async function main() {
  const result = await monitorPublicSurfaces({ expectedMainnetStatus: expectedMainnetStatus() });
  for (const [surface, probeResult] of Object.entries(result.results)) {
    console.log(`${surface}: ${probeResult.status} (expected ${probeResult.expectedStatus})`);
  }
  if (!result.ok) throw new Error(`DEXHelm public monitor failed: ${result.failures.join("; ")}`);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}

export { DEFAULT_EXPECTED_MAINNET_STATUS, PUBLIC_ORIGIN_BY_HOST, expectedMainnetStatus };
