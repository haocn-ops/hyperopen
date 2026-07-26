import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import http from "node:http";
import test from "node:test";
import { fileURLToPath } from "node:url";

const VERIFIER_PATH = fileURLToPath(
  new URL("./verify_cloudflare_worker.mjs", import.meta.url)
);

function runVerifier(origin) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [VERIFIER_PATH], {
      env: { ...process.env, HYPEROPEN_VERIFY_ORIGIN: origin },
      stdio: ["ignore", "pipe", "pipe"],
    });
    let stdout = "";
    let stderr = "";

    child.stdout.on("data", (chunk) => {
      stdout += chunk;
    });
    child.stderr.on("data", (chunk) => {
      stderr += chunk;
    });
    child.on("error", reject);
    child.on("close", (exitCode, signal) => {
      resolve({ exitCode, signal, stdout, stderr });
    });
  });
}

function closeServer(server) {
  return new Promise((resolve, reject) => {
    server.close((error) => (error ? reject(error) : resolve()));
  });
}

test("verify_cloudflare_worker probes only non-mutating fee endpoints, accepts non-5xx JSON, and never prints response bodies", async () => {
  const observedRequests = [];
  const mainnetPath = "/api/hyperunit/mainnet/v2/estimate-fees";
  const testnetPath = "/api/hyperunit/testnet/v2/estimate-fees";
  const server = http.createServer((request, response) => {
    observedRequests.push({ method: request.method, url: request.url });

    if (request.url === mainnetPath) {
      response.writeHead(429, { "content-type": "application/json; charset=utf-8" });
      response.end('{"privateBody":"mainnet-fee-body-must-not-be-printed"}');
      return;
    }

    if (request.url === testnetPath) {
      response.writeHead(404, { "content-type": "application/json; charset=utf-8" });
      response.end('{"privateBody":"testnet-fee-body-must-not-be-printed"}');
      return;
    }

    response.writeHead(500, { "content-type": "application/json; charset=utf-8" });
    response.end('{"privateBody":"unexpected-path"}');
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  const origin = `http://127.0.0.1:${address.port}`;

  try {
    const result = await runVerifier(origin);

    assert.equal(result.exitCode, 0, `${result.stderr}\n${result.stdout}`);
    assert.equal(result.signal, null);
    assert.deepEqual(observedRequests, [
      { method: "GET", url: mainnetPath },
      { method: "GET", url: testnetPath },
    ]);
    assert.match(result.stdout, new RegExp(origin.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
    assert.match(result.stdout, /429/);
    assert.match(result.stdout, /404/);
    assert.doesNotMatch(
      `${result.stdout}\n${result.stderr}`,
      /mainnet-fee-body-must-not-be-printed|testnet-fee-body-must-not-be-printed|unexpected-path/
    );
  } finally {
    await closeServer(server);
  }
});
