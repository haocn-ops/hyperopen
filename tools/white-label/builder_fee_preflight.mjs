import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { parseAndNormalizeTenantConfig } from "./tenant_config.mjs";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");

function infoUrlForNetwork(network) {
  return network === "mainnet"
    ? "https://api.hyperliquid.xyz/info"
    : "https://api.hyperliquid-testnet.xyz/info";
}

function accountValueAtLeast100(response) {
  const value = Number(response?.marginSummary?.accountValue);
  return Number.isFinite(value) && value >= 100;
}

export async function runBuilderFeePreflight(config, { postInfo } = {}) {
  const tenant = typeof config === "string"
    ? parseAndNormalizeTenantConfig(config)
    : config;
  const builderFee = tenant["builder-fee"];
  const builderAddress = builderFee?.["builder-address"];
  if (builderFee?.status !== "configured" ||
      !/^0x[0-9a-f]{40}$/.test(builderAddress || "") ||
      !Number.isInteger(builderFee?.["fee-tenths-bp"])) {
    throw new Error("Builder fee preflight requires configured builder-fee.");
  }
  if (typeof postInfo !== "function") {
    throw new Error("Builder fee preflight requires a read-only info client.");
  }
  const [rawMode, clearinghouseState] = await Promise.all([
    postInfo({ type: "userAbstraction", user: builderAddress }),
    postInfo({ type: "clearinghouseState", user: builderAddress }),
  ]);
  if (rawMode !== "default") {
    throw new Error("Builder fee preflight requires Standard account mode.");
  }
  if (!accountValueAtLeast100(clearinghouseState)) {
    throw new Error("Builder fee preflight requires at least 100 USDC of perps account value.");
  }
  return {
    builderAddress,
    network: tenant["hyperliquid-network"],
    mode: "Standard",
    accountValueAtLeast100: true,
  };
}

async function postInfo(body, network) {
  const response = await fetch(infoUrlForNetwork(network), {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error("Builder fee preflight info request failed.");
  }
  return response.json();
}

async function main() {
  const [flag, configPath] = process.argv.slice(2);
  if (flag !== "--config" || !configPath) {
    throw new Error("Expected --config <path>.");
  }
  const config = await fs.readFile(path.resolve(repositoryRoot, configPath), "utf8");
  const tenant = parseAndNormalizeTenantConfig(config);
  const result = await runBuilderFeePreflight(tenant, {
    postInfo: (body) => postInfo(body, tenant["hyperliquid-network"]),
  });
  process.stdout.write(`${JSON.stringify(result)}\n`);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
