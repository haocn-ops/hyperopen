const PROXY_PATHS = [
  "/api/hyperunit/mainnet/v2/estimate-fees",
  "/api/hyperunit/testnet/v2/estimate-fees",
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

async function verifyProxyEndpoint(origin, pathname) {
  const endpoint = new URL(pathname, origin);
  const response = await fetch(endpoint, {
    headers: { accept: "application/json" },
    redirect: "manual",
  });
  const contentType = response.headers.get("content-type") ?? "";
  await response.body?.cancel();

  if (response.status >= 500) {
    throw new Error(`${endpoint.pathname} returned ${response.status}.`);
  }
  if (!/^application\/json(?:;|$)/i.test(contentType)) {
    throw new Error(`${endpoint.pathname} returned a non-JSON content type.`);
  }

  console.log(`${endpoint.href}: ${response.status} ${contentType}`);
}

async function main() {
  const origin = verificationOrigin(process.env.HYPEROPEN_VERIFY_ORIGIN);
  console.log(`Verifying Cloudflare Worker at ${origin.origin}`);
  for (const pathname of PROXY_PATHS) {
    await verifyProxyEndpoint(origin, pathname);
  }
}

main().catch((error) => {
  console.error(`Cloudflare Worker verification failed: ${error.message}`);
  process.exitCode = 1;
});
