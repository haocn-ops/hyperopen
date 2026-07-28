import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { promisify } from "node:util";
import test from "node:test";
import { fileURLToPath } from "node:url";

const execFileAsync = promisify(execFile);
const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const sampleRelativePath = path.join("config", "white-label", "example-enterprise.json");
const sampleConfigPath = path.join(projectRoot, sampleRelativePath);
const canonicalOrigin = "https://desk.example.com";

async function loadTenantConfig() {
  return import("./tenant_config.mjs");
}

async function loadBuildRelease() {
  return import("./build_release.mjs");
}

async function loadVerifyRelease() {
  return import("./verify_release.mjs");
}

async function createFixtureRepository() {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "hyperopen-white-label-acceptance-"));
  await fs.mkdir(path.join(root, "config", "white-label"), { recursive: true });
  await fs.copyFile(sampleConfigPath, path.join(root, sampleRelativePath));
  await fs.cp(path.join(projectRoot, "resources", "public"), path.join(root, "resources", "public"), {
    recursive: true,
  });
  return root;
}

async function seedCompiledTenantIdentity(root, canonicalTenantJson) {
  const jsRoot = path.join(root, "resources", "public", "js");
  const manifestPath = path.join(jsRoot, "manifest.json");
  const manifest = JSON.parse(await fs.readFile(manifestPath, "utf8"));
  const main = manifest.find((entry) => entry["module-id"] === "main");
  assert.ok(main, "fixture must declare a main bundle");
  main["output-name"] = "main.ABCDEF0123456789.js";
  await fs.writeFile(manifestPath, JSON.stringify(manifest));

  const fixtureBundle = `globalThis.HYPEROPEN_TENANT_CONFIG_JSON=${JSON.stringify(canonicalTenantJson)};\n`;
  await fs.writeFile(path.join(jsRoot, main["output-name"]), fixtureBundle);
}

function requiredRouteHtmlPaths(routes) {
  return ["index.html", ...routes.filter((route) => route !== "/").map((route) => `${route.slice(1)}.html`)];
}

test("validate CLI accepts the checked-in public example and emits only concise operator-safe identity", async () => {
  const { canonicalTenantJson, parseAndNormalizeTenantConfig, tenantConfigDigest } =
    await loadTenantConfig();
  const raw = await fs.readFile(sampleConfigPath, "utf8");
  const normalized = parseAndNormalizeTenantConfig(raw);
  const expectedDigest = tenantConfigDigest(normalized);

  const { stdout, stderr } = await execFileAsync(
    process.execPath,
    ["tools/white-label/cli.mjs", "validate", "--config", sampleRelativePath, "--origin", canonicalOrigin],
    { cwd: projectRoot }
  );
  const output = `${stdout}\n${stderr}`;

  assert.match(output, /enterprise-example/);
  assert.match(output, new RegExp(canonicalOrigin.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  assert.match(output, new RegExp(expectedDigest));
  assert.match(output, /\/portfolio/);
  assert.doesNotMatch(output, /"brand\/name"/);
  assert.doesNotMatch(output, new RegExp(sampleRelativePath.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  assert.doesNotMatch(output, /HYPEROPEN_|PATH=|HOME=/);
  assert.equal(canonicalTenantJson(normalized).includes("private"), false);
});

test("the sample builds and verifies one isolated public release with matching identity", async () => {
  const { canonicalTenantJson, enabledTenantRoutes, parseAndNormalizeTenantConfig, tenantConfigDigest } =
    await loadTenantConfig();
  const { buildWhiteLabelRelease } = await loadBuildRelease();
  const { verifyWhiteLabelRelease } = await loadVerifyRelease();
  const root = await createFixtureRepository();
  const configPath = path.join(root, sampleRelativePath);
  const outputPath = path.join(root, "out", "white-label", "enterprise-example");
  const normalized = parseAndNormalizeTenantConfig(await fs.readFile(configPath, "utf8"));
  const canonical = canonicalTenantJson(normalized);
  const expectedRoutes = enabledTenantRoutes(normalized);
  const commandCalls = [];

  try {
    await seedCompiledTenantIdentity(root, canonical);
    const result = await buildWhiteLabelRelease(
      { repositoryRoot: root, configPath, canonicalOrigin, outputPath },
      {
        runCommand: async (executable, args, options = {}) => {
          commandCalls.push({ executable, args, options });
          assert.equal(typeof executable, "string");
          assert.ok(Array.isArray(args));
          assert.notEqual(options.shell, true);
          return { code: 0, stderr: "", stdout: "" };
        },
      }
    );
    const verification = await verifyWhiteLabelRelease({
      repositoryRoot: root,
      configPath,
      canonicalOrigin,
      outputPath,
    });

    assert.equal(result.tenantId, "enterprise-example");
    assert.equal(result.canonicalOrigin, canonicalOrigin);
    assert.equal(result.configDigest, tenantConfigDigest(normalized));
    assert.deepEqual(result.enabledRoutes, expectedRoutes);
    assert.equal(verification.tenantId, result.tenantId);
    assert.equal(verification.configDigest, result.configDigest);
    assert.deepEqual(verification.enabledRoutes, expectedRoutes);

    const flattenedCalls = commandCalls.map(({ executable, args }) => [executable, ...args].join(" "));
    assert.equal(commandCalls.length, 5, "Tailwind plus four isolated Shadow targets");
    assert.match(flattenedCalls[0], /tailwind/i);
    assert.deepEqual(
      flattenedCalls.slice(1).map((call) => call.match(/(?:release|compile)\s+([^\s]+)/)?.[1]),
      ["app", "portfolio-worker", "portfolio-optimizer-worker", "vault-detail-worker"]
    );
    const appCall = flattenedCalls[1];
    assert.match(appCall, /:output-dir/);
    assert.doesNotMatch(appCall, /:builds/);
    assert.match(appCall, /TENANT_CONFIG_JSON/);
    assert.match(appCall, /closure-defines/);
    for (const call of flattenedCalls.slice(2)) {
      assert.doesNotMatch(call, /TENANT_CONFIG_JSON/);
      assert.doesNotMatch(call, /closure-defines/);
      assert.doesNotMatch(call, /resources\/public\/js|out\/release-public/);
    }

    for (const relativePath of [
      "site-metadata.json",
      "tenant-manifest.json",
      "DEPLOYMENT.md",
      "_headers",
      "robots.txt",
      "sitemap.xml",
      ...requiredRouteHtmlPaths(["/", "/leaderboard", "/vaults", "/staking", "/funding-comparison", "/api", ...expectedRoutes]),
    ]) {
      await fs.access(path.join(outputPath, relativePath));
    }
    await assert.rejects(fs.access(path.join(outputPath, "trade.html")));
    assert.match(await fs.readFile(path.join(outputPath, "tenant-manifest.json"), "utf8"), /enterprise-example/);
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});
