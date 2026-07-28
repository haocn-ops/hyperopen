import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const REWRITER_PATH = fileURLToPath(
  new URL("./rewrite_hyperunit_release_endpoints.mjs", import.meta.url)
);

function runRewriter(releaseDirectory) {
  return new Promise((resolve, reject) => {
    const child = spawn(
      process.execPath,
      [REWRITER_PATH, "--release-directory", releaseDirectory],
      { stdio: ["ignore", "pipe", "pipe"] }
    );
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

async function writeReleaseFixture(contents) {
  const releaseDirectory = await fs.mkdtemp(
    path.join(os.tmpdir(), "hyperopen-cloudflare-rewriter-edge-")
  );
  const javascriptPath = path.join(releaseDirectory, "js", "app.HASH.js");
  await fs.mkdir(path.dirname(javascriptPath), { recursive: true });
  await fs.writeFile(javascriptPath, contents, "utf8");
  return { releaseDirectory, javascriptPath };
}

test("rewrite_hyperunit_release_endpoints fails atomically when either expected origin is absent", async () => {
  const original = 'const mainnet = "https://api.hyperunit.xyz/v2/estimate-fees";';
  const { releaseDirectory, javascriptPath } = await writeReleaseFixture(original);

  try {
    const result = await runRewriter(releaseDirectory);

    assert.notEqual(result.exitCode, 0);
    assert.match(`${result.stdout}\n${result.stderr}`, /expected.*testnet|testnet.*expected/i);
    assert.equal(await fs.readFile(javascriptPath, "utf8"), original);
  } finally {
    await fs.rm(releaseDirectory, { recursive: true, force: true });
  }
});

test("rewrite_hyperunit_release_endpoints rejects a repeated run without mutating the already rewritten release", async () => {
  const original = [
    'const mainnet = "https://api.hyperunit.xyz/v2/estimate-fees";',
    'const testnet = "https://api.hyperunit-testnet.xyz/v2/estimate-fees";',
  ].join("\n");
  const { releaseDirectory, javascriptPath } = await writeReleaseFixture(original);

  try {
    const firstRun = await runRewriter(releaseDirectory);
    assert.equal(firstRun.exitCode, 0, `${firstRun.stderr}\n${firstRun.stdout}`);
    const rewritten = await fs.readFile(javascriptPath, "utf8");

    const secondRun = await runRewriter(releaseDirectory);
    assert.notEqual(secondRun.exitCode, 0);
    assert.match(`${secondRun.stdout}\n${secondRun.stderr}`, /mainnet|testnet|origin/i);
    assert.equal(await fs.readFile(javascriptPath, "utf8"), rewritten);
  } finally {
    await fs.rm(releaseDirectory, { recursive: true, force: true });
  }
});

test("rewrite_hyperunit_release_endpoints rejects symbolic-link JavaScript entries without touching their targets", async () => {
  const releaseDirectory = await fs.mkdtemp(
    path.join(os.tmpdir(), "hyperopen-cloudflare-rewriter-symlink-")
  );
  const outsideDirectory = await fs.mkdtemp(
    path.join(os.tmpdir(), "hyperopen-cloudflare-rewriter-outside-")
  );
  const outsidePath = path.join(outsideDirectory, "outside.js");
  const outsideContents = [
    'const mainnet = "https://api.hyperunit.xyz/v2/estimate-fees";',
    'const testnet = "https://api.hyperunit-testnet.xyz/v2/estimate-fees";',
  ].join("\n");

  await fs.mkdir(path.join(releaseDirectory, "js"), { recursive: true });
  await fs.writeFile(outsidePath, outsideContents, "utf8");
  await fs.symlink(outsidePath, path.join(releaseDirectory, "js", "linked.js"));

  try {
    const result = await runRewriter(releaseDirectory);

    assert.notEqual(result.exitCode, 0);
    assert.match(`${result.stdout}\n${result.stderr}`, /symbolic link|symlink/i);
    assert.equal(await fs.readFile(outsidePath, "utf8"), outsideContents);
  } finally {
    await Promise.all([
      fs.rm(releaseDirectory, { recursive: true, force: true }),
      fs.rm(outsideDirectory, { recursive: true, force: true }),
    ]);
  }
});
