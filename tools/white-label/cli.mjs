import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { buildWhiteLabelRelease } from "./build_release.mjs";
import {
  enabledTenantRoutes,
  normalizeWhiteLabelOrigin,
  parseAndNormalizeTenantConfig,
  tenantConfigDigest,
} from "./tenant_config.mjs";
import { verifyWhiteLabelRelease } from "./verify_release.mjs";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");

function parseArguments(argv) {
  const [command, ...remaining] = argv;
  const options = {};
  for (let index = 0; index < remaining.length; index += 2) {
    const flag = remaining[index];
    const value = remaining[index + 1];
    if (!flag?.startsWith("--") || value === undefined) {
      throw new Error("Expected named white-label options.");
    }
    options[flag.slice(2)] = value;
  }
  return { command, options };
}

function requireOption(options, name) {
  if (typeof options[name] !== "string" || !options[name].trim()) {
    throw new Error(`Missing required --${name} option.`);
  }
  return options[name];
}

function publicResultLine(action, result) {
  const routes = result.enabledRoutes.length ? result.enabledRoutes.join(", ") : "none";
  return `${action} tenant ${result.tenantId} for ${result.canonicalOrigin}; routes: ${routes}; digest: ${result.configDigest}\n`;
}

async function main() {
  const { command, options } = parseArguments(process.argv.slice(2));
  const configPath = requireOption(options, "config");
  const canonicalOrigin = requireOption(options, "origin");

  if (command === "validate") {
    const tenant = parseAndNormalizeTenantConfig(
      await fs.readFile(path.resolve(repositoryRoot, configPath), "utf8")
    );
    process.stdout.write(
      publicResultLine("Validated", {
        tenantId: tenant["tenant/id"],
        canonicalOrigin: normalizeWhiteLabelOrigin(canonicalOrigin),
        enabledRoutes: enabledTenantRoutes(tenant),
        configDigest: tenantConfigDigest(tenant),
      })
    );
    return;
  }

  const outputPath = requireOption(options, "output");
  const operation = command === "build" ? buildWhiteLabelRelease : command === "verify" ? verifyWhiteLabelRelease : null;
  if (!operation) {
    throw new Error("Expected white-label command: validate, build, or verify.");
  }
  const result = await operation({ repositoryRoot, configPath, canonicalOrigin, outputPath });
  process.stdout.write(publicResultLine(command === "build" ? "Built" : "Verified", result));
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
});
