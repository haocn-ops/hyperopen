import http from "node:http";
import path from "node:path";
import { Readable } from "node:stream";
import { fileURLToPath } from "node:url";

export const MAX_REQUEST_BODY_BYTES = 1024 * 1024;
export const UPSTREAM_TIMEOUT_MS = 15_000;

const DEFAULTS = Object.freeze({
  port: 8081,
  appOrigin: "http://localhost:8080",
  mainnetBase: "https://api.hyperunit.xyz",
  testnetBase: "https://api.hyperunit-testnet.xyz",
});

const REQUEST_HEADER_ALLOWLIST = new Set(["accept", "content-type"]);
const METHOD_ALLOWLIST = new Set(["GET", "HEAD", "POST", "OPTIONS"]);
const RESPONSE_HEADER_ALLOWLIST = new Set([
  "cache-control",
  "content-encoding",
  "content-language",
  "content-type",
  "etag",
  "last-modified",
]);

function parsePort(value) {
  const port = Number(value ?? DEFAULTS.port);
  if (!Number.isSafeInteger(port) || port < 0 || port > 65535) {
    throw new Error("PORT must be an integer between 0 and 65535.");
  }
  return port;
}

function parseOrigin(value, { label, protocol, loopback = false }) {
  let parsed;
  try {
    parsed = new URL(String(value));
  } catch (_error) {
    throw new Error(`${label} must be an absolute origin.`);
  }
  const loopbackHosts = new Set(["localhost", "127.0.0.1", "[::1]"]);
  if (
    parsed.protocol !== protocol ||
    !parsed.hostname ||
    parsed.username ||
    parsed.password ||
    parsed.pathname !== "/" ||
    parsed.search ||
    parsed.hash ||
    (loopback && !loopbackHosts.has(parsed.hostname))
  ) {
    throw new Error(
      loopback
        ? `${label} must be a loopback ${protocol} origin.`
        : `${label} must be an exact ${protocol} origin.`,
    );
  }
  return parsed.origin;
}

export function normalizeProxyConfig(input = {}) {
  return Object.freeze({
    port: parsePort(input.port),
    appOrigin: parseOrigin(input.appOrigin ?? DEFAULTS.appOrigin, {
      label: "APP_ORIGIN",
      protocol: "http:",
      loopback: true,
    }),
    mainnetBase: parseOrigin(input.mainnetBase ?? DEFAULTS.mainnetBase, {
      label: "HYPERUNIT_MAINNET_URL",
      protocol: "https:",
    }),
    testnetBase: parseOrigin(input.testnetBase ?? DEFAULTS.testnetBase, {
      label: "HYPERUNIT_TESTNET_URL",
      protocol: "https:",
    }),
  });
}

function prefixMatch(pathname, prefix) {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

function stripPrefix(pathname, prefix) {
  const remainder = pathname.slice(prefix.length);
  return remainder || "/";
}

export function routeForRequest(requestUrl, config = normalizeProxyConfig()) {
  const incoming = new URL(requestUrl, "http://localhost");
  const routes = [
    { prefix: "/api/hyperunit/mainnet", base: config.mainnetBase },
    { prefix: "/api/hyperunit/testnet", base: config.testnetBase },
    { prefix: "/api/hyperunit", base: config.mainnetBase },
  ];
  const matched = routes.find(({ prefix }) => prefixMatch(incoming.pathname, prefix));
  if (!matched) {
    return {
      kind: "app",
      targetUrl: new URL(`${incoming.pathname}${incoming.search}`, config.appOrigin),
      upstreamPath: incoming.pathname,
    };
  }
  const upstreamPath = stripPrefix(incoming.pathname, matched.prefix);
  return {
    kind: "hyperunit",
    targetUrl: new URL(`${upstreamPath}${incoming.search}`, matched.base),
    upstreamPath,
  };
}

export function filterRequestHeaders(rawHeaders) {
  const headers = {};
  for (const [key, value] of Object.entries(rawHeaders ?? {})) {
    const lower = key.toLowerCase();
    if (REQUEST_HEADER_ALLOWLIST.has(lower) && value !== undefined) {
      headers[lower] = Array.isArray(value) ? value.join(", ") : String(value);
    }
  }
  return headers;
}

function copyResponseHeaders(upstreamHeaders, response) {
  for (const [key, value] of upstreamHeaders.entries()) {
    if (RESPONSE_HEADER_ALLOWLIST.has(key.toLowerCase())) {
      response.setHeader(key, value);
    }
  }
}

async function readRequestBody(request) {
  const method = (request.method ?? "GET").toUpperCase();
  if (method === "GET" || method === "HEAD") return null;
  const declaredLength = Number(request.headers["content-length"] ?? 0);
  if (Number.isFinite(declaredLength) && declaredLength > MAX_REQUEST_BODY_BYTES) {
    const error = new Error("Request body is too large.");
    error.statusCode = 413;
    throw error;
  }
  const chunks = [];
  let total = 0;
  for await (const chunk of request) {
    total += chunk.length;
    if (total > MAX_REQUEST_BODY_BYTES) {
      const error = new Error("Request body is too large.");
      error.statusCode = 413;
      throw error;
    }
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

export function createProxyServer(options = {}) {
  const config = normalizeProxyConfig(options);
  const fetchFn = options.fetchFn ?? fetch;
  return http.createServer(async (request, response) => {
    try {
      const method = (request.method ?? "GET").toUpperCase();
      if (!METHOD_ALLOWLIST.has(method)) {
        response.statusCode = 405;
        response.setHeader("allow", [...METHOD_ALLOWLIST].join(", "));
        response.end();
        return;
      }
      const route = routeForRequest(request.url ?? "/", config);
      const body = await readRequestBody(request);
      const upstreamResponse = await fetchFn(route.targetUrl, {
        method: request.method,
        headers: filterRequestHeaders(request.headers),
        body,
        redirect: "manual",
        signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
      });
      response.statusCode = upstreamResponse.status;
      copyResponseHeaders(upstreamResponse.headers, response);
      if (!upstreamResponse.body) {
        response.end();
      } else {
        Readable.fromWeb(upstreamResponse.body).pipe(response);
      }
    } catch (error) {
      response.statusCode = error?.statusCode === 413 ? 413 : 502;
      response.setHeader("content-type", "application/json");
      response.end(JSON.stringify({ error: response.statusCode === 413
        ? "Request body is too large."
        : "Proxy request failed." }));
    }
  });
}

export function startProxyServer(input = {}) {
  const config = normalizeProxyConfig(input);
  const server = createProxyServer({ ...input, ...config });
  server.on("error", (error) => {
    console.error("[hyperunit-proxy] server error:", error?.code ?? "UNKNOWN");
  });
  server.listen(config.port, "127.0.0.1", () => {
    console.log(
      `[hyperunit-proxy] listening on http://127.0.0.1:${config.port} (app=${config.appOrigin})`,
    );
  });
  return server;
}

const invokedDirectly =
  process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedDirectly) {
  const server = startProxyServer({
    port: process.env.PORT,
    appOrigin: process.env.APP_ORIGIN,
    mainnetBase: process.env.HYPERUNIT_MAINNET_URL,
    testnetBase: process.env.HYPERUNIT_TESTNET_URL,
  });
  server.once("error", () => {
    process.exitCode = 1;
  });
}
