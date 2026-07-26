import { spawn } from "node:child_process";
import fs from "node:fs/promises";
import path from "node:path";

import {
  generateReleaseArtifacts,
  hashContent,
  hashFullContent,
  TENANT_MANIFEST_FILE_PATH,
} from "../release-assets/generate_release_artifacts.mjs";
import {
  canonicalTenantJson,
  enabledTenantRoutes,
  normalizeWhiteLabelOrigin,
  parseAndNormalizeTenantConfig,
  tenantConfigDigest,
} from "./tenant_config.mjs";
import {
  assertReleasePathContained,
  resolveWhiteLabelPaths,
  resolveWhiteLabelRoots,
} from "./release_paths.mjs";
import { verifyWhiteLabelRelease } from "./verify_release.mjs";

const SHADOW_TARGETS = [
  "app",
  "portfolio-worker",
  "portfolio-optimizer-worker",
  "vault-detail-worker",
];
const PUBLISH_JOURNAL_VERSION = 1;

function escapeEdnString(value) {
  return String(value).replace(/\\/g, "\\\\").replace(/"/g, '\\"');
}

function buildShadowConfigMerge(canonicalConfig, stagingRoot, target) {
  const stagingSourceRoot = path.join(stagingRoot, "public");
  const stagedJsRoot = path.join(stagingSourceRoot, "js");
  const closureConfig = escapeEdnString(canonicalConfig);
  const compilerOptions = target === "app"
    ? ` :compiler-options {:closure-defines {hyperopen.config/TENANT_CONFIG_JSON "${closureConfig}"}}`
    : "";

  // shadow-cljs applies --config-merge directly to the selected build.
  return `{:output-dir "${escapeEdnString(stagedJsRoot)}" :asset-path "/js"${compilerOptions}}`;
}

function defaultRunCommand(executable, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(executable, args, {
      cwd: options.cwd,
      shell: false,
      stdio: options.stdio || "inherit",
    });
    child.once("error", reject);
    child.once("close", (code) => {
      if (code === 0) {
        resolve({ code: 0, stdout: "", stderr: "" });
      } else {
        reject(new Error(`${path.basename(executable)} exited with code ${code}.`));
      }
    });
  });
}

async function runRequiredCommand(runCommand, executable, args, options) {
  const result = await runCommand(executable, args, { ...options, shell: false });
  if (result && typeof result.code === "number" && result.code !== 0) {
    throw new Error(`${path.basename(executable)} exited with code ${result.code}.`);
  }
}

async function readNormalizedTenant(configPath) {
  const rawConfig = await fs.readFile(configPath, "utf8");
  return parseAndNormalizeTenantConfig(rawConfig);
}

function tenantOperationPath(outputRoot, tenantId, suffix) {
  return path.join(outputRoot, `.${tenantId}.${suffix}`);
}

async function pathExists(targetPath) {
  try {
    await fs.access(targetPath);
    return true;
  } catch (_error) {
    return false;
  }
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
    } else {
      throw new Error("Release contains an unsupported artifact type.");
    }
  }
  return files;
}

async function artifactDigestsForRelease(outputPath) {
  const files = (await listFiles(outputPath))
    .filter((filePath) => filePath !== TENANT_MANIFEST_FILE_PATH)
    .sort();
  const digests = {};
  for (const filePath of files) {
    digests[filePath] = hashFullContent(await fs.readFile(path.join(outputPath, filePath)));
  }
  return digests;
}

async function revalidateBuildPaths(paths, tenantId) {
  return resolveWhiteLabelPaths({
    repositoryRoot: paths.root,
    tenantId,
    outputPath: paths.outputPath,
  });
}

async function revalidateStagedOutput(paths, stagedOutputPath) {
  const roots = await resolveWhiteLabelRoots({ repositoryRoot: paths.root });
  return assertReleasePathContained(stagedOutputPath, roots.stagingRoot);
}

async function assertNonSymlink(targetPath) {
  try {
    if ((await fs.lstat(targetPath)).isSymbolicLink()) {
      throw new Error("Unsafe white-label publication path contains a symlink.");
    }
  } catch (error) {
    if (error?.code !== "ENOENT") {
      throw error;
    }
  }
}

async function writePublishJournal(journalPath, journal) {
  const temporaryPath = `${journalPath}.tmp-${process.pid}`;
  await assertNonSymlink(journalPath);
  await fs.writeFile(temporaryPath, `${JSON.stringify(journal)}\n`, { flag: "wx" });
  await fs.rename(temporaryPath, journalPath);
}

async function recoverInterruptedPublication(paths, tenantId) {
  paths = await revalidateBuildPaths(paths, tenantId);
  const journalPath = tenantOperationPath(paths.outputRoot, tenantId, "publish.json");
  if (!(await pathExists(journalPath))) {
    return;
  }
  await assertNonSymlink(journalPath);
  let journal;
  try {
    journal = JSON.parse(await fs.readFile(journalPath, "utf8"));
  } catch (_error) {
    throw new Error("White-label publish journal is invalid.");
  }
  const expectedBackupName = `.${tenantId}.previous`;
  if (
    journal?.version !== PUBLISH_JOURNAL_VERSION ||
    journal?.backupName !== expectedBackupName ||
    !["prepared", "backup-created", "published"].includes(journal?.phase)
  ) {
    throw new Error("White-label publish journal is invalid.");
  }
  const backupPath = path.join(paths.outputRoot, expectedBackupName);
  const outputExists = await pathExists(paths.outputPath);
  const backupExists = await pathExists(backupPath);
  if (backupExists) {
    await assertNonSymlink(backupPath);
  }
  if (journal.phase === "prepared") {
    if (backupExists) {
      throw new Error("White-label publish journal is invalid.");
    }
  } else if (journal.phase === "backup-created") {
    if (!outputExists && backupExists) {
      paths = await revalidateBuildPaths(paths, tenantId);
      await fs.rename(backupPath, paths.outputPath);
    } else if (outputExists && backupExists) {
      await fs.rm(backupPath, { recursive: true, force: true });
    }
  } else if (!outputExists && backupExists) {
    paths = await revalidateBuildPaths(paths, tenantId);
    await fs.rename(backupPath, paths.outputPath);
  } else if (!outputExists) {
    throw new Error("White-label publish recovery cannot find a complete release.");
  } else if (backupExists) {
    await fs.rm(backupPath, { recursive: true, force: true });
  }
  await fs.rm(journalPath, { force: true });
}

function defaultProcessAlive(pid) {
  if (!Number.isSafeInteger(pid) || pid <= 0) {
    return false;
  }
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    return error?.code !== "ESRCH";
  }
}

async function acquireTenantLock(paths, tenantId, processAlive) {
  const lockPath = tenantOperationPath(paths.outputRoot, tenantId, "build.lock");
  for (let attempt = 0; attempt < 2; attempt += 1) {
    let handle;
    try {
      handle = await fs.open(lockPath, "wx");
    } catch (error) {
      if (error?.code !== "EEXIST") {
        throw error;
      }
      let lockRecord = null;
      try {
        lockRecord = JSON.parse(await fs.readFile(lockPath, "utf8"));
      } catch (_error) {
        // A malformed lock cannot identify a live owner and is stale.
      }
      const validPid = lockRecord?.version === 1 && Number.isSafeInteger(lockRecord?.pid) && lockRecord.pid > 0;
      if (validPid && (await processAlive(lockRecord.pid))) {
        throw new Error("White-label build already in progress for this tenant.");
      }
      if (attempt === 1) {
        throw new Error("White-label build already in progress for this tenant.");
      }
      await assertNonSymlink(lockPath);
      await fs.rm(lockPath, { force: true });
      continue;
    }
    await handle.writeFile(`${JSON.stringify({ version: 1, pid: process.pid })}\n`);
    return async () => {
      await handle.close();
      await fs.rm(lockPath, { force: true });
    };
  }
  throw new Error("White-label build already in progress for this tenant.");
}

async function publishRelease(stagedOutputPath, paths, tenantId) {
  const backupName = `.${tenantId}.previous`;
  let movedPrevious = false;
  try {
    paths = await revalidateBuildPaths(paths, tenantId);
    let backupPath = path.join(paths.outputRoot, backupName);
    let journalPath = tenantOperationPath(paths.outputRoot, tenantId, "publish.json");
    await writePublishJournal(journalPath, { version: PUBLISH_JOURNAL_VERSION, backupName, phase: "prepared" });
    paths = await revalidateBuildPaths(paths, tenantId);
    backupPath = path.join(paths.outputRoot, backupName);
    try {
      await fs.rename(paths.outputPath, backupPath);
      movedPrevious = true;
    } catch (error) {
      if (error?.code !== "ENOENT") {
        throw error;
      }
    }
    paths = await revalidateBuildPaths(paths, tenantId);
    journalPath = tenantOperationPath(paths.outputRoot, tenantId, "publish.json");
    await writePublishJournal(journalPath, { version: PUBLISH_JOURNAL_VERSION, backupName, phase: "backup-created" });
    await revalidateStagedOutput(paths, stagedOutputPath);
    paths = await revalidateBuildPaths(paths, tenantId);
    await fs.rename(stagedOutputPath, paths.outputPath);
    paths = await revalidateBuildPaths(paths, tenantId);
    journalPath = tenantOperationPath(paths.outputRoot, tenantId, "publish.json");
    await writePublishJournal(journalPath, { version: PUBLISH_JOURNAL_VERSION, backupName, phase: "published" });
    if (movedPrevious) {
      backupPath = path.join(paths.outputRoot, backupName);
      await fs.rm(backupPath, { recursive: true, force: true });
    }
    await fs.rm(journalPath, { force: true });
  } catch (error) {
    if (movedPrevious) {
      paths = await revalidateBuildPaths(paths, tenantId);
      const backupPath = path.join(paths.outputRoot, backupName);
      try {
        await fs.access(paths.outputPath);
      } catch (_missingOutput) {
        await fs.rename(backupPath, paths.outputPath).catch(() => {});
      }
    }
    throw error;
  }
}

export async function buildWhiteLabelRelease(options = {}, dependencies = {}) {
  const repositoryRoot = path.resolve(options.repositoryRoot || process.cwd());
  const configPath = path.resolve(repositoryRoot, options.configPath || "");
  const normalizedTenant = await readNormalizedTenant(configPath);
  const canonicalOrigin = normalizeWhiteLabelOrigin(options.canonicalOrigin);
  const canonicalConfig = canonicalTenantJson(normalizedTenant);
  const configDigest = tenantConfigDigest(normalizedTenant);
  const enabledRoutes = enabledTenantRoutes(normalizedTenant);
  let paths = await resolveWhiteLabelPaths({
    repositoryRoot,
    tenantId: normalizedTenant["tenant/id"],
    outputPath: options.outputPath,
  });
  const runCommand = dependencies.runCommand || defaultRunCommand;
  const processAlive = dependencies.processAlive || defaultProcessAlive;
  let stagingPath = null;
  let releaseLock = null;

  try {
    paths = await revalidateBuildPaths(paths, normalizedTenant["tenant/id"]);
    await fs.mkdir(paths.outputRoot, { recursive: true });
    paths = await revalidateBuildPaths(paths, normalizedTenant["tenant/id"]);
    releaseLock = await acquireTenantLock(paths, normalizedTenant["tenant/id"], processAlive);
    paths = await revalidateBuildPaths(paths, normalizedTenant["tenant/id"]);
    await recoverInterruptedPublication(paths, normalizedTenant["tenant/id"]);
    paths = await revalidateBuildPaths(paths, normalizedTenant["tenant/id"]);
    await fs.mkdir(paths.stagingRoot, { recursive: true });
    paths = await revalidateBuildPaths(paths, normalizedTenant["tenant/id"]);
    stagingPath = await fs.mkdtemp(path.join(paths.stagingRoot, `${normalizedTenant["tenant/id"]}-`));
    await revalidateStagedOutput(paths, stagingPath);
    const stagingSourceRoot = path.join(stagingPath, "public");
    const stagedOutputPath = path.join(stagingPath, "release");
    await fs.cp(path.join(repositoryRoot, "resources", "public"), stagingSourceRoot, {
      recursive: true,
    });

    await runRequiredCommand(
      runCommand,
      "npx",
      [
        "--no-install",
        "tailwindcss",
        "-i",
        path.join(repositoryRoot, "src", "styles", "main.css"),
        "-o",
        path.join(stagingSourceRoot, "css", "main.css"),
        "--minify",
      ],
      { cwd: repositoryRoot }
    );

    for (const target of SHADOW_TARGETS) {
      const configMerge = buildShadowConfigMerge(canonicalConfig, stagingPath, target);
      await runRequiredCommand(
        runCommand,
        "npx",
        [
          "--no-install",
          "shadow-cljs",
          "--force-spawn",
          "--config-merge",
          configMerge,
          "release",
          target,
        ],
        { cwd: repositoryRoot }
      );
    }

    const buildId = `white-label-${configDigest.slice(0, 16)}`;
    const tenantManifest = {
      version: 1,
      tenant: normalizedTenant,
      canonicalOrigin,
      enabledRoutes,
      buildId,
      configDigest,
    };
    const generated = await generateReleaseArtifacts({
      sourceRoot: stagingSourceRoot,
      outputRoot: stagedOutputPath,
      canonicalOrigin,
      tenant: normalizedTenant,
      tenantManifest,
      // Injected runners are a deterministic compilation seam and provide the fixture bundle unchanged.
      rewriteMainModule: runCommand === defaultRunCommand,
    });
    const mainBundlePath = path.join(stagedOutputPath, generated.mainScriptHref.replace(/^\//, ""));
    const mainBundleDigest = hashContent(await fs.readFile(mainBundlePath));
    const artifactDigests = await artifactDigestsForRelease(stagedOutputPath);
    await fs.writeFile(
      path.join(stagedOutputPath, TENANT_MANIFEST_FILE_PATH),
      `${JSON.stringify({ ...tenantManifest, mainScriptHref: generated.mainScriptHref, mainBundleDigest, artifactDigests }, null, 2)}\n`
    );

    await verifyWhiteLabelRelease({
      repositoryRoot,
      configPath,
      canonicalOrigin,
      outputPath: stagedOutputPath,
      allowStagingOutput: true,
    });
    await revalidateStagedOutput(paths, stagedOutputPath);
    paths = await revalidateBuildPaths(paths, normalizedTenant["tenant/id"]);
    await fs.mkdir(paths.outputRoot, { recursive: true });
    paths = await revalidateBuildPaths(paths, normalizedTenant["tenant/id"]);
    await publishRelease(stagedOutputPath, paths, normalizedTenant["tenant/id"]);

    return {
      tenantId: normalizedTenant["tenant/id"],
      canonicalOrigin,
      enabledRoutes,
      configDigest,
      buildId,
      outputPath: paths.outputPath,
    };
  } finally {
    if (stagingPath) {
      await fs.rm(stagingPath, { recursive: true, force: true });
    }
    if (releaseLock) {
      await releaseLock();
    }
  }
}
