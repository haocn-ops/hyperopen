import fs from "node:fs/promises";
import path from "node:path";

function isWithin(parent, child) {
  const relative = path.relative(parent, child);
  return relative !== "" && !relative.startsWith("..") && !path.isAbsolute(relative);
}

async function assertNoSymlink(pathToCheck, repositoryRoot) {
  const relative = path.relative(repositoryRoot, pathToCheck);
  const segments = relative ? relative.split(path.sep) : [];
  let current = repositoryRoot;
  for (const segment of segments) {
    current = path.join(current, segment);
    try {
      const stat = await fs.lstat(current);
      if (stat.isSymbolicLink()) {
        throw new Error(`Unsafe white-label path contains a symlink: ${segment}`);
      }
    } catch (error) {
      if (error?.code !== "ENOENT") {
        throw error;
      }
      break;
    }
  }
}

export async function resolveWhiteLabelRoots({ repositoryRoot }) {
  const root = path.resolve(repositoryRoot || process.cwd());
  const outputRoot = path.join(root, "out", "white-label");
  const stagingRoot = path.join(root, "out", "white-label-staging");
  await assertNoSymlink(root, root);
  await assertNoSymlink(outputRoot, root);
  await assertNoSymlink(stagingRoot, root);

  return { root, outputRoot, stagingRoot };
}

export async function resolveWhiteLabelPaths({ repositoryRoot, tenantId, outputPath }) {
  const { root, outputRoot, stagingRoot } = await resolveWhiteLabelRoots({ repositoryRoot });
  const resolvedOutput = path.resolve(root, outputPath || "");
  const expectedOutput = path.join(outputRoot, tenantId);

  if (!isWithin(outputRoot, resolvedOutput) || resolvedOutput !== expectedOutput) {
    throw new Error("Output must be the tenant-specific child of the owned out/white-label root.");
  }
  if (!isWithin(stagingRoot, path.join(stagingRoot, tenantId))) {
    throw new Error("Unsafe white-label staging path.");
  }
  await assertNoSymlink(resolvedOutput, root);

  return { root, outputRoot, stagingRoot, outputPath: resolvedOutput };
}

export async function assertReleasePathContained(releaseRoot, expectedRoot) {
  const resolvedRelease = path.resolve(releaseRoot);
  const resolvedExpected = path.resolve(expectedRoot);
  if (!isWithin(resolvedExpected, resolvedRelease) && resolvedRelease !== resolvedExpected) {
    throw new Error("Unsafe release path outside the owned white-label root.");
  }
  await assertNoSymlink(resolvedRelease, resolvedExpected);
  return resolvedRelease;
}
