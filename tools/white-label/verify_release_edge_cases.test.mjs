import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const sampleConfigPath = path.join(projectRoot, "config", "white-label", "example-enterprise.json");
const canonicalOrigin = "https://desk.example.com";

async function loadModules() {
  const [tenant, build, verify] = await Promise.all([
    import("./tenant_config.mjs"),
    import("./build_release.mjs"),
    import("./verify_release.mjs"),
  ]);
  return { ...tenant, ...build, ...verify };
}

async function createFixtureRepository() {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "hyperopen-white-label-verify-edge-"));
  const configPath = path.join(root, "config", "white-label", "example-enterprise.json");
  await fs.mkdir(path.dirname(configPath), { recursive: true });
  await fs.copyFile(sampleConfigPath, configPath);
  await fs.cp(path.join(projectRoot, "resources", "public"), path.join(root, "resources", "public"), {
    recursive: true,
  });
  return { root, configPath, outputPath: path.join(root, "out", "white-label", "enterprise-example") };
}

async function buildFixture(modules, fixture) {
  const normalized = modules.parseAndNormalizeTenantConfig(await fs.readFile(fixture.configPath, "utf8"));
  const canonical = modules.canonicalTenantJson(normalized);
  const jsRoot = path.join(fixture.root, "resources", "public", "js");
  const manifestPath = path.join(jsRoot, "manifest.json");
  const manifest = JSON.parse(await fs.readFile(manifestPath, "utf8"));
  const main = manifest.find((entry) => entry["module-id"] === "main");
  assert.ok(main, "fixture must declare a main bundle");
  main["output-name"] = "main.ABCDEF0123456789.js";
  await fs.writeFile(manifestPath, JSON.stringify(manifest));
  await fs.writeFile(
    path.join(jsRoot, main["output-name"]),
    `globalThis.HYPEROPEN_TENANT_CONFIG_JSON=${JSON.stringify(canonical)};\n`
  );
  await modules.buildWhiteLabelRelease(
    {
      repositoryRoot: fixture.root,
      configPath: fixture.configPath,
      canonicalOrigin,
      outputPath: fixture.outputPath,
    },
    { runCommand: async () => ({ code: 0, stderr: "", stdout: "" }) }
  );
}

async function listFiles(root, relativePath = "") {
  const entries = await fs.readdir(path.join(root, relativePath), { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const childPath = path.join(relativePath, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await listFiles(root, childPath)));
    } else if (entry.isFile()) {
      files.push(childPath.split(path.sep).join("/"));
    }
  }
  return files;
}

async function withRelease(mutator, assertion) {
  const modules = await loadModules();
  const fixture = await createFixtureRepository();
  try {
    await buildFixture(modules, fixture);
    const baseline = await modules.verifyWhiteLabelRelease({
      repositoryRoot: fixture.root,
      configPath: fixture.configPath,
      canonicalOrigin,
      outputPath: fixture.outputPath,
    });
    assert.equal(baseline.tenantId, "enterprise-example");
    await mutator(fixture);
    await assertion(modules, fixture);
  } finally {
    await fs.rm(fixture.root, { recursive: true, force: true });
  }
}

test("verification rejects manifest drift and disabled route reintroduction without leaking public-config secrets", async () => {
  const secretSentinel = "RED_VERIFY_SECRET_MUST_NOT_ECHO";
  await withRelease(
    async ({ outputPath }) => {
      const manifestPath = path.join(outputPath, "tenant-manifest.json");
      const manifest = JSON.parse(await fs.readFile(manifestPath, "utf8"));
      manifest.canonicalOrigin = "https://different.example.com";
      manifest["api-secret"] = secretSentinel;
      await fs.writeFile(manifestPath, JSON.stringify(manifest));
      await fs.writeFile(path.join(outputPath, "trade.html"), "disabled route injection");
    },
    async (modules, fixture) => {
      await assert.rejects(
        modules.verifyWhiteLabelRelease({
          repositoryRoot: fixture.root,
          configPath: fixture.configPath,
          canonicalOrigin,
          outputPath: fixture.outputPath,
        }),
        (error) => {
          assert.match(error.message, /manifest|canonical|unknown|trade/i);
          assert.doesNotMatch(error.message, new RegExp(secretSentinel));
          return true;
        }
      );
    }
  );
});

test("verification detects compiled identity tampering and unexpected secret-dump artifacts", async () => {
  const secretSentinel = "RED_BUNDLE_SECRET_MUST_NOT_ECHO";
  await withRelease(
    async ({ outputPath }) => {
      const jsRoot = path.join(outputPath, "js");
      const mainFile = (await fs.readdir(jsRoot)).find((entry) => /^main\.[A-F0-9]+\.js$/.test(entry));
      assert.ok(mainFile, "release fixture must contain one main bundle");
      await fs.appendFile(path.join(jsRoot, mainFile), "\n/* tampered */\n");
      await fs.writeFile(path.join(outputPath, "operator-config-dump.txt"), secretSentinel);
    },
    async (modules, fixture) => {
      await assert.rejects(
        modules.verifyWhiteLabelRelease({
          repositoryRoot: fixture.root,
          configPath: fixture.configPath,
          canonicalOrigin,
          outputPath: fixture.outputPath,
        }),
        (error) => {
          assert.match(error.message, /bundle|artifact|main|unexpected|digest/i);
          assert.doesNotMatch(error.message, new RegExp(secretSentinel));
          return true;
        }
      );
    }
  );
});

test("verification rejects injected JavaScript, retargeted route HTML, and secret-shaped public artifacts", async () => {
  const secretSentinel = "PRIVATE_KEY=DO_NOT_ECHO";
  await withRelease(
    async ({ outputPath }) => {
      const indexPath = path.join(outputPath, "index.html");
      const indexHtml = await fs.readFile(indexPath, "utf8");
      await fs.writeFile(path.join(outputPath, "js", "payload.js"), "window.payload = true;");
      await fs.writeFile(
        indexPath,
        indexHtml.replace(/src="\/js\/main\.[A-F0-9]+\.js"/, 'src="/js/payload.js"')
      );
      await fs.writeFile(path.join(outputPath, "release-notes.txt"), secretSentinel);
    },
    async (modules, fixture) => {
      await assert.rejects(
        modules.verifyWhiteLabelRelease({
          repositoryRoot: fixture.root,
          configPath: fixture.configPath,
          canonicalOrigin,
          outputPath: fixture.outputPath,
        }),
        (error) => {
          assert.match(error.message, /artifact|script|release/i);
          assert.doesNotMatch(error.message, new RegExp(secretSentinel));
          return true;
        }
      );
    }
  );
});

test("verification scans allowed public text artifacts for secret-shaped content without echoing it", async () => {
  const secretSentinel = "PRIVATE_KEY=DO_NOT_ECHO";
  await withRelease(
    async ({ outputPath }) => {
      await fs.appendFile(path.join(outputPath, "DEPLOYMENT.md"), `\n${secretSentinel}\n`);
    },
    async (modules, fixture) => {
      await assert.rejects(
        modules.verifyWhiteLabelRelease({
          repositoryRoot: fixture.root,
          configPath: fixture.configPath,
          canonicalOrigin,
          outputPath: fixture.outputPath,
        }),
        (error) => {
          assert.match(error.message, /public artifact|secret/i);
          assert.doesNotMatch(error.message, new RegExp(secretSentinel));
          return true;
        }
      );
    }
  );
});

test("verification requires full digests for every public artifact and rejects an allowed lazy chunk change", async () => {
  await withRelease(
    async ({ outputPath }) => {
      const manifest = JSON.parse(await fs.readFile(path.join(outputPath, "tenant-manifest.json"), "utf8"));
      const releaseFiles = (await listFiles(outputPath))
        .filter((filePath) => filePath !== "tenant-manifest.json")
        .sort();
      const lazyChunk = releaseFiles.find((filePath) => /^js\/portfolio_route(?:\.[A-F0-9]+)?\.js$/.test(filePath));

      assert.deepEqual(Object.keys(manifest.artifactDigests).sort(), releaseFiles);
      assert.ok(Object.values(manifest.artifactDigests).every((digest) => /^[A-F0-9]{64}$/.test(digest)));
      assert.ok(lazyChunk, "fixture must contain an allowed lazy chunk");
      await fs.appendFile(path.join(outputPath, lazyChunk), "\n/* lazy chunk tampered */\n");
    },
    async (modules, fixture) => {
      await assert.rejects(
        modules.verifyWhiteLabelRelease({
          repositoryRoot: fixture.root,
          configPath: fixture.configPath,
          canonicalOrigin,
          outputPath: fixture.outputPath,
        }),
        /artifact.*digest|digest.*artifact/i
      );
    }
  );
});
