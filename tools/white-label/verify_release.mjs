import fs from "node:fs/promises";
import path from "node:path";

import {
  hashContent,
  hashFullContent,
  routePathToOutputHtmlPath,
  TENANT_MANIFEST_FILE_PATH,
  TENANT_NOT_FOUND_FILE_PATH,
} from "../release-assets/generate_release_artifacts.mjs";
import {
  buildSiteMetadata,
  publicPathToRelativePath,
  RELEASE_ROUTE_METADATA_SCRIPT_PATH,
} from "../release-assets/site_metadata.mjs";
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

const MANIFEST_FIELDS = new Set([
  "version",
  "tenant",
  "canonicalOrigin",
  "enabledRoutes",
  "buildId",
  "configDigest",
  "mainBundleDigest",
  "mainScriptHref",
  "artifactDigests",
]);
const INFORMATION_ROUTES = ["/", "/leaderboard", "/vaults", "/staking", "/funding-comparison", "/api"];
const FIXED_FONT_PATHS = new Set([
  "fonts/InterVariable.woff2",
  "fonts/JetBrainsMono-Medium.woff2",
  "fonts/JetBrainsMono-Regular.woff2",
]);
const FIXED_ROOT_PATHS = new Set([
  "site-metadata.json",
  TENANT_MANIFEST_FILE_PATH,
  "DEPLOYMENT.md",
  "_headers",
  "robots.txt",
  "sitemap.xml",
  "sw.js",
  "theme-preload.js",
  TENANT_NOT_FOUND_FILE_PATH,
]);
const WORKER_PATHS = new Set([
  "js/portfolio_worker.js",
  "js/portfolio_optimizer_worker.js",
  "js/vault_detail_worker.js",
]);
const SHADOW_MODULE_PREFIXES = new Set([
  "account_activity",
  "account_funding_history",
  "account_orders",
  "account_positions_outcomes",
  "account_surfaces",
  "api_wallets_route",
  "charts_shared",
  "funding_comparison_route",
  "funding_modal",
  "leaderboard_route",
  "margin_rec",
  "portfolio_route",
  "referrals_route",
  "spectate_mode_modal",
  "staking_route",
  "subaccounts_route",
  "trade_chart",
  "trading_crypto",
  "trading_indicators",
  "vaults_route",
]);
const SECRET_CONTENT_PATTERN = /(?:sk_(?:live|test)_[A-Za-z0-9_-]+|0x[0-9a-f]{32,}|(?:seed|private)[-_ ]?(?:phrase|key)|access[-_ ]?token|api[-_ ]?(?:key|secret)|password(?:\s*[:=])|credential(?:\s*[:=])|raw[-_ ]?signature)/i;

function containsSecretShapedContent(content) {
  const withoutPublicAddresses = content.replace(/\b0x[0-9a-f]{40}\b/gi, "");
  return SECRET_CONTENT_PATTERN.test(withoutPublicAddresses);
}

async function readJson(filePath, label) {
  try {
    return JSON.parse(await fs.readFile(filePath, "utf8"));
  } catch (_error) {
    throw new Error(`Invalid ${label} JSON.`);
  }
}

function assertManifestShape(manifest) {
  if (!manifest || typeof manifest !== "object" || Array.isArray(manifest)) {
    throw new Error("Invalid tenant manifest.");
  }
  for (const key of Object.keys(manifest)) {
    if (!MANIFEST_FIELDS.has(key)) {
      throw new Error("Tenant manifest contains an unknown field.");
    }
  }
  for (const key of MANIFEST_FIELDS) {
    if (!Object.hasOwn(manifest, key)) {
      throw new Error(`Tenant manifest is missing ${key}.`);
    }
  }
  if (manifest.version !== 1 || typeof manifest.buildId !== "string" || !manifest.buildId.trim()) {
    throw new Error("Tenant manifest has an invalid version or build identifier.");
  }
  if (typeof manifest.mainScriptHref !== "string" || !/^\/js\/main\.[A-F0-9]{16,64}\.js$/.test(manifest.mainScriptHref)) {
    throw new Error("Tenant manifest has an invalid main script reference.");
  }
  if (!manifest.artifactDigests || typeof manifest.artifactDigests !== "object" || Array.isArray(manifest.artifactDigests)) {
    throw new Error("Tenant manifest has invalid artifact digests.");
  }
  for (const digest of Object.values(manifest.artifactDigests)) {
    if (typeof digest !== "string" || !/^[A-F0-9]{64}$/.test(digest)) {
      throw new Error("Tenant manifest has invalid artifact digests.");
    }
  }
}

async function listFiles(root, relativePath = "") {
  const currentPath = path.join(root, relativePath);
  const entries = await fs.readdir(currentPath, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const childPath = path.join(relativePath, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await listFiles(root, childPath)));
    } else if (entry.isFile()) {
      files.push(childPath);
    } else {
      throw new Error("Release contains an unsupported artifact type.");
    }
  }
  return files;
}

function isKnownShadowModulePath(filePath) {
  const match = /^js\/([a-z0-9_]+)(?:\.[A-F0-9]{16,64})?\.js$/.exec(filePath);
  return Boolean(match && SHADOW_MODULE_PREFIXES.has(match[1]));
}

function isPublicTextArtifact(filePath) {
  return (
    filePath.endsWith(".html") ||
    filePath === "site-metadata.json" ||
    filePath === TENANT_MANIFEST_FILE_PATH ||
    filePath === "DEPLOYMENT.md" ||
    filePath === "robots.txt" ||
    filePath === "sitemap.xml" ||
    filePath === "_headers" ||
    filePath === RELEASE_ROUTE_METADATA_SCRIPT_PATH
  );
}

async function assertAllowedArtifacts(outputPath, files, enabledRoutes, expectedSiteMetadata, mainScriptHref) {
  const allowedRouteFiles = new Set([...INFORMATION_ROUTES, ...enabledRoutes].map(routePathToOutputHtmlPath));
  allowedRouteFiles.add(TENANT_NOT_FOUND_FILE_PATH);
  const allowedRootAssets = new Set(
    expectedSiteMetadata.rootAssetPaths.map(publicPathToRelativePath)
  );
  const allowedJs = new Set([
    mainScriptHref.slice(1),
    "js/module-loader.json",
    RELEASE_ROUTE_METADATA_SCRIPT_PATH,
    ...WORKER_PATHS,
  ]);
  for (const filePath of files) {
    const normalized = filePath.split(path.sep).join("/");
    const allowed =
      allowedRouteFiles.has(normalized) ||
      FIXED_ROOT_PATHS.has(normalized) ||
      allowedRootAssets.has(normalized) ||
      FIXED_FONT_PATHS.has(normalized) ||
      allowedJs.has(normalized) ||
      /^css\/main\.[A-F0-9]{16}\.css$/.test(normalized) ||
      isKnownShadowModulePath(normalized);
    if (!allowed) {
      throw new Error(`Unexpected release artifact: ${normalized}`);
    }
    if (isPublicTextArtifact(normalized)) {
      const content = await fs.readFile(path.join(outputPath, normalized), "utf8");
      if (containsSecretShapedContent(content)) {
        throw new Error(`Release public artifact contains secret-shaped content: ${normalized}`);
      }
    }
  }
}

async function assertArtifactDigests(outputPath, files, artifactDigests) {
  const expectedFiles = files
    .map((filePath) => filePath.split(path.sep).join("/"))
    .filter((filePath) => filePath !== TENANT_MANIFEST_FILE_PATH)
    .sort();
  const manifestFiles = Object.keys(artifactDigests).sort();
  if (JSON.stringify(manifestFiles) !== JSON.stringify(expectedFiles)) {
    throw new Error("Tenant manifest artifact digest inventory does not match the release.");
  }
  for (const filePath of expectedFiles) {
    const digest = hashFullContent(await fs.readFile(path.join(outputPath, filePath)));
    if (artifactDigests[filePath] !== digest) {
      throw new Error(`Release artifact digest does not match: ${filePath}`);
    }
  }
}

function assertEqualJson(expected, actual, label) {
  if (JSON.stringify(expected) !== JSON.stringify(actual)) {
    throw new Error(`${label} does not match the expected public tenant release.`);
  }
}

async function resolveVerificationOutput(options, tenantId) {
  if (options.allowedOutputRoot) {
    return {
      repositoryRoot: path.resolve(options.repositoryRoot),
      outputPath: await assertReleasePathContained(options.outputPath, options.allowedOutputRoot),
    };
  }
  if (!options.allowStagingOutput) {
    const paths = await resolveWhiteLabelPaths({
      repositoryRoot: options.repositoryRoot,
      tenantId,
      outputPath: options.outputPath,
    });
    return { repositoryRoot: paths.root, outputPath: paths.outputPath };
  }
  const paths = await resolveWhiteLabelRoots({ repositoryRoot: options.repositoryRoot });
  const stagedOutput = await assertReleasePathContained(options.outputPath, paths.stagingRoot);
  return { repositoryRoot: paths.root, outputPath: stagedOutput };
}

export async function verifyWhiteLabelRelease(options = {}) {
  const repositoryRoot = path.resolve(options.repositoryRoot || process.cwd());
  const configPath = path.resolve(repositoryRoot, options.configPath || "");
  const normalizedTenant = parseAndNormalizeTenantConfig(await fs.readFile(configPath, "utf8"));
  const canonicalOrigin = normalizeWhiteLabelOrigin(options.canonicalOrigin);
  const expectedRoutes = enabledTenantRoutes(normalizedTenant);
  const configDigest = tenantConfigDigest(normalizedTenant);
  const resolved = await resolveVerificationOutput(
    { ...options, repositoryRoot, outputPath: options.outputPath },
    normalizedTenant["tenant/id"]
  );
  const outputPath = resolved.outputPath;
  const manifest = await readJson(path.join(outputPath, TENANT_MANIFEST_FILE_PATH), "tenant manifest");
  assertManifestShape(manifest);
  // The public manifest stores the normalized tenant, including derived fields
  // such as builder-fee.max-fee-rate. Strip derived values before feeding it
  // through the strict source-config parser; normalization recomputes them and
  // the canonical comparison below rejects any tampering.
  const manifestTenantSource = {
    ...manifest.tenant,
    "builder-fee": manifest.tenant?.["builder-fee"]
      ? Object.fromEntries(
          Object.entries(manifest.tenant["builder-fee"]).filter(([key]) => key !== "max-fee-rate")
        )
      : manifest.tenant?.["builder-fee"],
  };
  const manifestTenant = parseAndNormalizeTenantConfig(JSON.stringify(manifestTenantSource));

  if (manifest.canonicalOrigin !== canonicalOrigin) {
    throw new Error("Tenant manifest canonical origin does not match.");
  }
  if (manifest.configDigest !== configDigest) {
    throw new Error("Tenant manifest config digest does not match.");
  }
  assertEqualJson(canonicalTenantJson(normalizedTenant), canonicalTenantJson(manifestTenant), "Tenant manifest");
  assertEqualJson(expectedRoutes, manifest.enabledRoutes, "Tenant manifest routes");

  const expectedSiteMetadata = buildSiteMetadata({
    canonicalOrigin,
    indexHtml: await fs.readFile(path.join(repositoryRoot, "resources", "public", "index.html"), "utf8"),
    buildId: manifest.buildId,
    buildInfo: null,
    tenant: normalizedTenant,
  });

  const files = await listFiles(outputPath);
  await assertAllowedArtifacts(
    outputPath,
    files,
    expectedRoutes,
    expectedSiteMetadata,
    manifest.mainScriptHref
  );
  await assertArtifactDigests(outputPath, files, manifest.artifactDigests);
  const siteMetadata = await readJson(path.join(outputPath, "site-metadata.json"), "site metadata");
  if (siteMetadata.siteName !== normalizedTenant["brand/name"] || siteMetadata.origin !== canonicalOrigin) {
    throw new Error("Site metadata identity does not match the tenant manifest.");
  }
  assertEqualJson(expectedSiteMetadata.routes, siteMetadata.routes, "Site metadata routes");
  const metadataRoutes = Array.isArray(siteMetadata.routes) ? siteMetadata.routes.map((route) => route.path) : null;
  const expectedMetadataRoutes = [
    "/",
    ...expectedRoutes,
    ...INFORMATION_ROUTES.slice(1),
  ];
  if (!metadataRoutes || metadataRoutes.length !== expectedMetadataRoutes.length) {
    throw new Error("Site metadata route inventory does not match.");
  }
  for (const routePath of expectedMetadataRoutes) {
    if (!metadataRoutes.includes(routePath)) {
      throw new Error("Site metadata route inventory does not match.");
    }
  }
  if (JSON.stringify(siteMetadata).match(/affiliate|referral-url|event-endpoint/i)) {
    throw new Error("Site metadata contains unexpected affiliate data.");
  }

  const routeScript = await fs.readFile(path.join(outputPath, "js", "release-route-metadata.js"), "utf8");
  for (const routePath of expectedMetadataRoutes) {
    const htmlPath = path.join(outputPath, routePathToOutputHtmlPath(routePath));
    const html = await fs.readFile(htmlPath, "utf8");
    if (!html.includes(`href="${canonicalOrigin}${routePath}"`)) {
      throw new Error(`Route HTML canonical metadata does not match: ${routePath}`);
    }
    const scriptSources = [...html.matchAll(/<script\b[^>]*\bsrc="([^"]+)"[^>]*><\/script>/gi)]
      .map((match) => match[1])
      .sort();
    if (JSON.stringify(scriptSources) !== JSON.stringify([manifest.mainScriptHref, `/${RELEASE_ROUTE_METADATA_SCRIPT_PATH}`].sort())) {
      throw new Error(`Route HTML script references do not match the tenant manifest: ${routePath}`);
    }
    if (!routeScript.includes(JSON.stringify(routePath))) {
      throw new Error(`Route metadata script does not match: ${routePath}`);
    }
    const route = siteMetadata.routes.find((candidate) => candidate.path === routePath);
    if (!routeScript.includes(JSON.stringify(route.title)) || !routeScript.includes(JSON.stringify(route.description))) {
      throw new Error(`Route metadata script does not match: ${routePath}`);
    }
  }

  const mainBundle = await fs.readFile(path.join(outputPath, manifest.mainScriptHref.slice(1)));
  if (manifest.mainBundleDigest !== hashContent(mainBundle)) {
    throw new Error("Release main bundle digest does not match.");
  }
  const mainBundleText = mainBundle.toString("utf8");
  const compiledIdentity = [normalizedTenant["tenant/id"], normalizedTenant["brand/name"]];
  if (!compiledIdentity.every((value) => mainBundleText.includes(value))) {
    throw new Error("Release main bundle tenant identity does not match.");
  }

  return {
    tenantId: normalizedTenant["tenant/id"],
    canonicalOrigin,
    enabledRoutes: expectedRoutes,
    configDigest,
    buildId: manifest.buildId,
    outputPath,
  };
}
