import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const sampleConfigPath = path.join(projectRoot, "config", "white-label", "example-enterprise.json");

async function loadBuildRelease() {
  return import("./build_release.mjs");
}

async function createFixtureRepository() {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "hyperopen-white-label-build-edge-"));
  await fs.mkdir(path.join(root, "config", "white-label"), { recursive: true });
  await fs.copyFile(sampleConfigPath, path.join(root, "config", "white-label", "example.json"));
  await fs.cp(path.join(projectRoot, "resources", "public"), path.join(root, "resources", "public"), {
    recursive: true,
  });
  return root;
}

async function writeConfig(root, value) {
  const configPath = path.join(root, "config", "white-label", "example.json");
  await fs.writeFile(configPath, typeof value === "string" ? value : JSON.stringify(value));
  return configPath;
}

test("invalid config, origin, and output fail before any staging mutation or compiler command", async () => {
  const { buildWhiteLabelRelease } = await loadBuildRelease();
  const root = await createFixtureRepository();
  const outputPath = path.join(root, "out", "white-label", "enterprise-example");
  const markerPath = path.join(outputPath, "prior-release.txt");
  const commandCalls = [];

  try {
    await fs.mkdir(outputPath, { recursive: true });
    await fs.writeFile(markerPath, "prior-good-release");
    const invalidConfigPath = await writeConfig(root, '{"tenant/id":');

    await assert.rejects(
      buildWhiteLabelRelease(
        { repositoryRoot: root, configPath: invalidConfigPath, canonicalOrigin: "http://desk.example.com", outputPath },
        {
          runCommand: async (...args) => {
            commandCalls.push(args);
            throw new Error("compiler must not run for invalid preflight input");
          },
        }
      ),
      /json|origin|https/i
    );

    assert.deepEqual(commandCalls, []);
    assert.equal(await fs.readFile(markerPath, "utf8"), "prior-good-release");
    await assert.rejects(fs.access(path.join(root, "out", "white-label-staging", "enterprise-example")));
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

test("build output must be a tenant-specific child of the owned white-label output root", async () => {
  const { buildWhiteLabelRelease } = await loadBuildRelease();
  const root = await createFixtureRepository();
  const configPath = path.join(root, "config", "white-label", "example.json");
  const unsafeOutputs = [
    root,
    path.join(root, "out"),
    path.join(root, "out", "white-label"),
    path.join(root, "out", "white-label-escape", "enterprise-example"),
    path.join(root, "out", "release-public"),
    path.join(root, "resources", "public"),
    path.join(root, "out", "white-label", "..", "release-public"),
    path.join(root, "..", "white-label-outside"),
  ];
  const commandCalls = [];

  try {
    for (const outputPath of unsafeOutputs) {
      await assert.rejects(
        buildWhiteLabelRelease(
          { repositoryRoot: root, configPath, canonicalOrigin: "https://desk.example.com", outputPath },
          { runCommand: async (...args) => commandCalls.push(args) }
        ),
        /output|owned|unsafe|white-label/i,
        outputPath
      );
    }
    assert.deepEqual(commandCalls, []);
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

test("a compiler failure leaves a prior verified release untouched and command injection is unavailable", async () => {
  const { buildWhiteLabelRelease } = await loadBuildRelease();
  const root = await createFixtureRepository();
  const configPath = path.join(root, "config", "white-label", "example.json");
  const outputPath = path.join(root, "out", "white-label", "enterprise-example");
  const markerPath = path.join(outputPath, "prior-release.txt");
  const calls = [];

  try {
    await fs.mkdir(outputPath, { recursive: true });
    await fs.writeFile(markerPath, "prior-good-release");

    await assert.rejects(
      buildWhiteLabelRelease(
        { repositoryRoot: root, configPath, canonicalOrigin: "https://desk.example.com", outputPath },
        {
          runCommand: async (executable, args, options = {}) => {
            calls.push({ executable, args, options });
            assert.equal(typeof executable, "string");
            assert.ok(Array.isArray(args));
            assert.notEqual(options.shell, true);
            if (args.includes("portfolio-optimizer-worker")) {
              throw new Error("simulated isolated worker compile failure");
            }
            return { code: 0, stderr: "", stdout: "" };
          },
        }
      ),
      /simulated isolated worker compile failure/
    );

    assert.deepEqual(
      calls.slice(1).map(({ args }) => args.find((argument) => /^(app|portfolio-worker|portfolio-optimizer-worker|vault-detail-worker)$/.test(argument))),
      ["app", "portfolio-worker", "portfolio-optimizer-worker"]
    );
    assert.equal(await fs.readFile(markerPath, "utf8"), "prior-good-release");
    await assert.rejects(fs.access(path.join(outputPath, "tenant-manifest.json")));
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

test("a same-tenant live lock rejects concurrent builds before a compiler command", async () => {
  const { buildWhiteLabelRelease } = await loadBuildRelease();
  const root = await createFixtureRepository();
  const configPath = path.join(root, "config", "white-label", "example.json");
  const outputPath = path.join(root, "out", "white-label", "enterprise-example");
  const lockPath = path.join(root, "out", "white-label", ".enterprise-example.build.lock");
  const commandCalls = [];

  try {
    await fs.mkdir(path.dirname(lockPath), { recursive: true });
    await fs.writeFile(lockPath, JSON.stringify({ version: 1, pid: 4242 }));
    await assert.rejects(
      buildWhiteLabelRelease(
        { repositoryRoot: root, configPath, canonicalOrigin: "https://desk.example.com", outputPath },
        {
          processAlive: (pid) => pid === 4242,
          runCommand: async (...args) => commandCalls.push(args),
        }
      ),
      /already in progress/i
    );
    assert.deepEqual(commandCalls, []);
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

test("a stale same-tenant lock is reclaimed once before the first compiler command", async () => {
  const { buildWhiteLabelRelease } = await loadBuildRelease();
  const root = await createFixtureRepository();
  const configPath = path.join(root, "config", "white-label", "example.json");
  const outputPath = path.join(root, "out", "white-label", "enterprise-example");
  const lockPath = path.join(root, "out", "white-label", ".enterprise-example.build.lock");
  const commandCalls = [];

  try {
    await fs.mkdir(path.dirname(lockPath), { recursive: true });
    await fs.writeFile(lockPath, JSON.stringify({ version: 1, pid: 4242 }));
    await assert.rejects(
      buildWhiteLabelRelease(
        { repositoryRoot: root, configPath, canonicalOrigin: "https://desk.example.com", outputPath },
        {
          processAlive: () => false,
          runCommand: async (...args) => {
            commandCalls.push(args);
            throw new Error("stale lock reclaimed");
          },
        }
      ),
      /stale lock reclaimed/
    );
    assert.equal(commandCalls.length, 1);
    await assert.rejects(fs.access(lockPath));
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

test("a publish journal restores the prior release before a replacement build starts", async () => {
  const { buildWhiteLabelRelease } = await loadBuildRelease();
  const root = await createFixtureRepository();
  const configPath = path.join(root, "config", "white-label", "example.json");
  const outputRoot = path.join(root, "out", "white-label");
  const outputPath = path.join(outputRoot, "enterprise-example");
  const backupPath = path.join(outputRoot, ".enterprise-example.previous");
  const journalPath = path.join(outputRoot, ".enterprise-example.publish.json");

  try {
    await fs.mkdir(backupPath, { recursive: true });
    await fs.writeFile(path.join(backupPath, "prior-release.txt"), "prior-good-release");
    await fs.writeFile(journalPath, JSON.stringify({ version: 1, backupName: ".enterprise-example.previous", phase: "backup-created" }));
    await assert.rejects(
      buildWhiteLabelRelease(
        { repositoryRoot: root, configPath, canonicalOrigin: "https://desk.example.com", outputPath },
        { runCommand: async () => { throw new Error("stop after recovery"); } }
      ),
      /stop after recovery/
    );
    assert.equal(await fs.readFile(path.join(outputPath, "prior-release.txt"), "utf8"), "prior-good-release");
    await assert.rejects(fs.access(journalPath));
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

test("a prepared journal without a prior release is removed before compilation", async () => {
  const { buildWhiteLabelRelease } = await loadBuildRelease();
  const root = await createFixtureRepository();
  const configPath = path.join(root, "config", "white-label", "example.json");
  const outputRoot = path.join(root, "out", "white-label");
  const outputPath = path.join(outputRoot, "enterprise-example");
  const journalPath = path.join(outputRoot, ".enterprise-example.publish.json");

  try {
    await fs.mkdir(outputRoot, { recursive: true });
    await fs.writeFile(journalPath, JSON.stringify({ version: 1, backupName: ".enterprise-example.previous", phase: "prepared" }));
    await assert.rejects(
      buildWhiteLabelRelease(
        { repositoryRoot: root, configPath, canonicalOrigin: "https://desk.example.com", outputPath },
        { runCommand: async () => { throw new Error("prepared journal recovered"); } }
      ),
      /prepared journal recovered/
    );
    await assert.rejects(fs.access(journalPath));
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

test("a prepared journal with a backup is rejected as an impossible publication state", async () => {
  const { buildWhiteLabelRelease } = await loadBuildRelease();
  const root = await createFixtureRepository();
  const configPath = path.join(root, "config", "white-label", "example.json");
  const outputRoot = path.join(root, "out", "white-label");
  const outputPath = path.join(outputRoot, "enterprise-example");
  const backupPath = path.join(outputRoot, ".enterprise-example.previous");
  const journalPath = path.join(outputRoot, ".enterprise-example.publish.json");

  try {
    await fs.mkdir(backupPath, { recursive: true });
    await fs.writeFile(journalPath, JSON.stringify({ version: 1, backupName: ".enterprise-example.previous", phase: "prepared" }));
    await assert.rejects(
      buildWhiteLabelRelease(
        { repositoryRoot: root, configPath, canonicalOrigin: "https://desk.example.com", outputPath },
        { runCommand: async () => { throw new Error("compiler must not run"); } }
      ),
      /journal|invalid|recovery/i
    );
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

test("a published journal retains the published release and removes its prior backup", async () => {
  const { buildWhiteLabelRelease } = await loadBuildRelease();
  const root = await createFixtureRepository();
  const configPath = path.join(root, "config", "white-label", "example.json");
  const outputRoot = path.join(root, "out", "white-label");
  const outputPath = path.join(outputRoot, "enterprise-example");
  const backupPath = path.join(outputRoot, ".enterprise-example.previous");
  const journalPath = path.join(outputRoot, ".enterprise-example.publish.json");

  try {
    await fs.mkdir(outputPath, { recursive: true });
    await fs.mkdir(backupPath, { recursive: true });
    await fs.writeFile(path.join(outputPath, "published-release.txt"), "published-release");
    await fs.writeFile(path.join(backupPath, "prior-release.txt"), "prior-release");
    await fs.writeFile(journalPath, JSON.stringify({ version: 1, backupName: ".enterprise-example.previous", phase: "published" }));
    await assert.rejects(
      buildWhiteLabelRelease(
        { repositoryRoot: root, configPath, canonicalOrigin: "https://desk.example.com", outputPath },
        { runCommand: async () => { throw new Error("published journal recovered"); } }
      ),
      /published journal recovered/
    );
    assert.equal(await fs.readFile(path.join(outputPath, "published-release.txt"), "utf8"), "published-release");
    await assert.rejects(fs.access(backupPath));
    await assert.rejects(fs.access(journalPath));
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});
