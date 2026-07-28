const PROXY_CHECKS = [
  { pathname: "/api/hyperunit/testnet/v2/estimate-fees", expectedStatus: null },
  { pathname: "/api/hyperunit/mainnet/v2/estimate-fees", expectedStatus: 404 },
  { pathname: "/api/hyperunit/v2/estimate-fees", expectedStatus: 404 },
];

function verificationOrigin(value) {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error("HYPEROPEN_VERIFY_ORIGIN must be an HTTP(S) origin.");
  }

  const origin = new URL(value.trim());
  if (
    !["http:", "https:"].includes(origin.protocol) ||
    origin.username ||
    origin.password ||
    origin.pathname !== "/" ||
    origin.search ||
    origin.hash
  ) {
    throw new Error("HYPEROPEN_VERIFY_ORIGIN must be an HTTP(S) origin without credentials or a path.");
  }
  return origin;
}

async function verifyProxyEndpoint(origin, { pathname, expectedStatus }) {
  const endpoint = new URL(pathname, origin);
  const response = await fetch(endpoint, {
    headers: { accept: "application/json" },
    redirect: "manual",
  });
  const contentType = response.headers.get("content-type") ?? "";
  await response.body?.cancel();

  if (expectedStatus !== null && response.status !== expectedStatus) {
    throw new Error(`${endpoint.pathname} returned ${response.status}; expected ${expectedStatus}.`);
  }
  if (expectedStatus === null && response.status >= 500) {
    throw new Error(`${endpoint.pathname} returned ${response.status}.`);
  }
  if (expectedStatus === null && !/^application\/json(?:;|$)/i.test(contentType)) {
    throw new Error(`${endpoint.pathname} returned a non-JSON content type.`);
  }

  console.log(`${endpoint.href}: ${response.status} ${contentType}`);
}

async function main() {
  const origin = verificationOrigin(process.env.HYPEROPEN_VERIFY_ORIGIN);
  console.log(`Verifying Cloudflare Worker at ${origin.origin}`);
  for (const check of PROXY_CHECKS) {
    await verifyProxyEndpoint(origin, check);
  }
}

main().catch((error) => {
  console.error(`Cloudflare Worker verification failed: ${error.message}`);
  process.exitCode = 1;
});
