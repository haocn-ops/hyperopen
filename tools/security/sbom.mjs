import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

function packageNameFromPath(packagePath, metadata) {
  if (metadata.name) return metadata.name;
  const marker = "node_modules/";
  const index = packagePath.lastIndexOf(marker);
  return index < 0 ? null : packagePath.slice(index + marker.length);
}

function purl(name, version) {
  const encodedName = name.split("/").map(encodeURIComponent).join("/");
  return `pkg:npm/${encodedName}@${encodeURIComponent(version)}`;
}

function integrityHash(integrity, name) {
  const match = /^sha512-([A-Za-z0-9+/=]+)$/.exec(integrity ?? "");
  if (!match) {
    throw new Error(`Production package ${name} has an invalid integrity value.`);
  }
  return { alg: "SHA-512", content: match[1] };
}

export function buildCycloneDxSbom(lockfile) {
  if (lockfile?.lockfileVersion !== 3 || typeof lockfile?.packages !== "object") {
    throw new Error("SBOM generation requires an npm lockfileVersion 3 packages map.");
  }
  const root = lockfile.packages[""];
  if (!root?.name || !root?.version) {
    throw new Error("SBOM generation requires root package name and version.");
  }
  const entries = Object.entries(lockfile.packages)
    .filter(([packagePath, metadata]) => packagePath && metadata?.dev !== true)
    .map(([packagePath, metadata]) => {
      const name = packageNameFromPath(packagePath, metadata);
      if (!name || !metadata.version) {
        throw new Error(`Production package ${packagePath} has no name or version.`);
      }
      if (!metadata.integrity) {
        throw new Error(`Production package ${name} has no integrity value.`);
      }
      return { packagePath, name, metadata, ref: purl(name, metadata.version) };
    })
    .sort((a, b) => a.ref.localeCompare(b.ref));
  const refByName = new Map(entries.map((entry) => [entry.name, entry.ref]));
  const rootRef = purl(root.name, root.version);
  const components = entries.map(({ name, metadata, ref }) => ({
    type: "library",
    "bom-ref": ref,
    name,
    version: metadata.version,
    purl: ref,
    hashes: [integrityHash(metadata.integrity, name)],
    ...(metadata.license ? { licenses: [{ license: { id: metadata.license } }] } : {}),
  }));
  const dependencies = [
    {
      ref: rootRef,
      dependsOn: Object.keys(root.dependencies ?? {})
        .map((name) => refByName.get(name))
        .filter(Boolean)
        .sort(),
    },
    ...entries.map(({ metadata, ref }) => ({
      ref,
      dependsOn: Object.keys(metadata.dependencies ?? {})
        .map((name) => refByName.get(name))
        .filter(Boolean)
        .sort(),
    })),
  ].sort((a, b) => a.ref.localeCompare(b.ref));
  return {
    bomFormat: "CycloneDX",
    specVersion: "1.5",
    version: 1,
    metadata: {
      component: {
        type: "application",
        "bom-ref": rootRef,
        name: root.name,
        version: root.version,
        purl: rootRef,
      },
    },
    components,
    dependencies,
  };
}

export async function writeSbom({ lockfilePath = "package-lock.json", outputPath = "out/security/sbom.cdx.json" } = {}) {
  const lockfile = JSON.parse(await fs.readFile(lockfilePath, "utf8"));
  const sbom = buildCycloneDxSbom(lockfile);
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await fs.writeFile(outputPath, `${JSON.stringify(sbom, null, 2)}\n`);
  return outputPath;
}

const invokedDirectly = process.argv[1]
  && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedDirectly) {
  writeSbom()
    .then((outputPath) => process.stdout.write(`${outputPath}\n`))
    .catch((error) => {
      process.stderr.write(`${error.message}\n`);
      process.exitCode = 1;
    });
}
