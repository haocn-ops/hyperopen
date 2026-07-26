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

async function writeReleaseFixture(files) {
  const releaseDirectory = await fs.mkdtemp(
    path.join(os.tmpdir(), "hyperopen-cloudflare-rewriter-")
  );

  await Promise.all(
    Object.entries(files).map(async ([relativePath, contents]) => {
      const outputPath = path.join(releaseDirectory, relativePath);
      await fs.mkdir(path.dirname(outputPath), { recursive: true });
      await fs.writeFile(outputPath, contents, "utf8");
    })
  );

  return releaseDirectory;
}

test("rewrite_hyperunit_release_endpoints rewrites only the exact mainnet and testnet bases in release JavaScript", async () => {
  const javascript = [
    'const mainnet = "https://api.hyperunit.xyz/v2/estimate-fees";',
    'const testnet = "https://api.hyperunit-testnet.xyz/v2/estimate-fees";',
    'const lookalike = "https://api.hyperunit.xyz.evil/v2/estimate-fees";',
    'const unrelated = "https://example.test/api.hyperunit.xyz";',
  ].join("\n");
  const html = '<script src="https://api.hyperunit.xyz/v2/estimate-fees"></script>';
  const css = 'body { background: url("https://api.hyperunit-testnet.xyz/asset"); }';
  const releaseDirectory = await writeReleaseFixture({
    "js/app.HASH.js": javascript,
    "index.html": html,
    "css/main.HASH.css": css,
  });

  try {
    const result = await runRewriter(releaseDirectory);

    assert.equal(result.exitCode, 0, `${result.stderr}\n${result.stdout}`);
    assert.equal(result.signal, null);
    assert.match(result.stdout, /app\.HASH\.js/);
    assert.match(result.stdout, /mainnet/i);
    assert.match(result.stdout, /testnet/i);

    assert.equal(
      await fs.readFile(path.join(releaseDirectory, "js", "app.HASH.js"), "utf8"),
      [
        'const mainnet = "/api/hyperunit/mainnet/v2/estimate-fees";',
        'const testnet = "/api/hyperunit/testnet/v2/estimate-fees";',
        'const lookalike = "https://api.hyperunit.xyz.evil/v2/estimate-fees";',
        'const unrelated = "https://example.test/api.hyperunit.xyz";',
      ].join("\n")
    );
    assert.equal(await fs.readFile(path.join(releaseDirectory, "index.html"), "utf8"), html);
    assert.equal(
      await fs.readFile(path.join(releaseDirectory, "css", "main.HASH.css"), "utf8"),
      css
    );
  } finally {
    await fs.rm(releaseDirectory, { recursive: true, force: true });
  }
});
