import assert from "node:assert/strict";
import test from "node:test";

const workerModuleUrl = new URL("../../workers/hyperopen-worker.mjs", import.meta.url);

async function loadWorker() {
  return import(workerModuleUrl);
}

const env = {
  HYPERUNIT_MAINNET_URL: "https://api.hyperunit.xyz",
  HYPERUNIT_TESTNET_URL: "https://api.hyperunit-testnet.xyz",
};

test("resolveHyperunitTarget rejects unrelated and lookalike path prefixes instead of selecting an upstream", async () => {
  const { resolveHyperunitTarget } = await loadWorker();

  for (const pathname of [
    "/api/hyperunit/mainnetx/v2/estimate-fees",
    "/api/hyperunit/testnetx/v2/estimate-fees",
    "/api/hyperunitx/v2/estimate-fees",
    "/api/hyperunit-mainnet/v2/estimate-fees",
  ]) {
    assert.equal(
      resolveHyperunitTarget(new URL(`https://hyperopen.example${pathname}`), env),
      null,
      pathname
    );
  }
});

test("handleRequest returns a generic JSON 502 without upstream failure details", async () => {
  const { handleRequest } = await loadWorker();
  const upstreamFailure = new Error(
    "fetch https://private-upstream.example failed with cookie=private-session"
  );

  const response = await handleRequest(
    new Request("https://testnet.dexhelm.com/api/hyperunit/testnet/v2/estimate-fees"),
    env,
    {
      fetchImpl: async () => {
        throw upstreamFailure;
      },
    }
  );

  const body = await response.text();
  assert.equal(response.status, 502);
  assert.match(response.headers.get("content-type"), /^application\/json(?:;|$)/i);
  assert.equal(body, '{"error":"HyperUnit proxy request failed."}');
  assert.doesNotMatch(body, /private-upstream|private-session|cookie/i);
});

test("optimizer shell fallback rejects unsafe methods, lookalikes, and unknown routes", async () => {
  const { handleRequest } = await loadWorker();

  for (const request of [
    new Request("https://testnet.dexhelm.com/portfolio/optimizer"),
    new Request("https://testnet.dexhelm.com/portfolio/optimizex"),
    new Request("https://testnet.dexhelm.com/portfolio/optimize/scn_01/unowned"),
    new Request("https://testnet.dexhelm.com/this-route-must-not-exist"),
    new Request("https://testnet.dexhelm.com/portfolio/optimize", { method: "POST" }),
  ]) {
    const receivedUrls = [];
    const response = await handleRequest(request, {
      ...env,
      ASSETS: {
        fetch: async (assetRequest) => {
          receivedUrls.push(assetRequest.url);
          return new Response("not found", { status: 404 });
        },
      },
    });

    assert.equal(response.status, 404, `${request.method} ${request.url}`);
    assert.deepEqual(receivedUrls, [request.url], `${request.method} ${request.url}`);
  }
});

test("DEXHelm informational hosts return deterministic 404s for unknown document routes", async () => {
  const { handleRequest } = await loadWorker();

  for (const url of [
    "https://dexhelm.com/not-a-page",
    "https://status.dexhelm.com/not-a-page",
  ]) {
    const response = await handleRequest(
      new Request(url, { headers: { accept: "text/html" } }),
      env
    );

    assert.equal(response.status, 404, url);
    assert.equal(response.headers.get("cache-control"), "no-store", url);
    assert.match(response.headers.get("content-type"), /^text\/html/i, url);
  }
});

test("DEXHelm docs and risk shortcuts redirect permanently to same-origin sections", async () => {
  const { handleRequest } = await loadWorker();

  for (const [pathname, expectedHash] of [
    ["/docs", "#documentation"],
    ["/risk/", "#risk"],
  ]) {
    const response = await handleRequest(
      new Request(`https://dexhelm.com${pathname}`, { headers: { accept: "text/html" } }),
      env
    );
    const location = new URL(response.headers.get("location"));

    assert.equal(response.status, 308, pathname);
    assert.equal(location.origin, "https://dexhelm.com", pathname);
    assert.equal(location.pathname, "/", pathname);
    assert.equal(location.hash, expectedHash, pathname);
  }
});
