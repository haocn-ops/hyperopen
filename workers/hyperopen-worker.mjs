const HYPERUNIT_TESTNET_PREFIX = "/api/hyperunit/testnet";
const HYPERUNIT_ROOT_PREFIX = "/api/hyperunit";
const PORTFOLIO_SHELL_PATH = "/portfolio";
const DEXHELM_APEX_HOST = "dexhelm.com";
const DEXHELM_MAINNET_HOST = "app.dexhelm.com";
const DEXHELM_TESTNET_HOST = "testnet.dexhelm.com";
const DEXHELM_STATUS_HOST = "status.dexhelm.com";
const HYPERLIQUID_NETWORK_QUERY_KEY = "hyperliquidNetwork";
const MAX_PROXY_BODY_BYTES = 1024 * 1024;
const PROXY_TIMEOUT_MS = 15_000;
const HYPERUNIT_METHOD_ALLOWLIST = new Set(["GET", "HEAD", "POST"]);

const HYPERUNIT_TESTNET_ROUTE = {
  prefix: HYPERUNIT_TESTNET_PREFIX,
  environmentKey: "HYPERUNIT_TESTNET_URL",
};

const REQUEST_HEADER_ALLOWLIST = new Set([
  "accept",
  "accept-language",
  "content-type",
  "if-match",
  "if-modified-since",
  "if-none-match",
  "if-unmodified-since",
]);

const RESPONSE_HEADER_ALLOWLIST = new Set([
  "cache-control",
  "content-language",
  "content-type",
  "etag",
  "last-modified",
]);

const DOCUMENT_SECURITY_HEADERS = {
  "content-security-policy": [
    "default-src 'none'",
    "base-uri 'none'",
    "form-action 'none'",
    "frame-ancestors 'none'",
    "img-src 'self' data:",
    "style-src 'unsafe-inline'",
    "upgrade-insecure-requests",
  ].join("; "),
  "cross-origin-opener-policy": "same-origin",
  "permissions-policy": "camera=(), geolocation=(), microphone=(), payment=(), usb=()",
  "referrer-policy": "strict-origin-when-cross-origin",
  "strict-transport-security": "max-age=31536000; includeSubDomains",
  "x-content-type-options": "nosniff",
  "x-frame-options": "DENY",
};

const OPERATOR_PAGE_HTML = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="DEXHelm is an independent, non-custodial Hyperliquid trading terminal currently available on Testnet.">
  <title>DEXHelm | Hyperliquid trading terminal</title>
  <style>
    :root { color-scheme: dark; --bg: #0b0d10; --panel: #12161b; --line: #29313a; --text: #f2f5f7; --muted: #a9b3bd; --green: #49d69d; --amber: #f4c15d; --blue: #72a9ff; }
    * { box-sizing: border-box; }
    html { scroll-behavior: smooth; }
    body { margin: 0; background: var(--bg); color: var(--text); font: 16px/1.6 Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    a { color: inherit; }
    .wrap { width: min(1120px, calc(100% - 40px)); margin: 0 auto; }
    header { position: sticky; top: 0; z-index: 2; border-bottom: 1px solid var(--line); background: rgba(11, 13, 16, .94); backdrop-filter: blur(12px); }
    nav { min-height: 64px; display: flex; align-items: center; gap: 24px; }
    .brand { margin-right: auto; font-size: 18px; font-weight: 760; text-decoration: none; }
    nav a:not(.brand) { color: var(--muted); font-size: 14px; text-decoration: none; }
    nav a:hover { color: var(--text); }
    .hero { min-height: 72vh; padding: clamp(72px, 11vw, 132px) 0 80px; border-bottom: 1px solid var(--line); background-image: url('/apple-touch-icon.png'); background-repeat: no-repeat; background-position: right 8% center; background-size: min(42vw, 420px); }
    .eyebrow { color: var(--green); font: 700 12px/1.3 ui-monospace, SFMono-Regular, Menlo, monospace; text-transform: uppercase; }
    h1 { max-width: 760px; margin: 18px 0; font-size: clamp(52px, 8vw, 92px); line-height: .98; letter-spacing: 0; }
    .lede { max-width: 660px; color: var(--muted); font-size: 20px; }
    .actions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 34px; }
    .button { min-height: 44px; padding: 10px 16px; border: 1px solid var(--line); border-radius: 6px; background: var(--panel); text-decoration: none; font-weight: 700; }
    .button.primary { border-color: var(--green); background: var(--green); color: #06130e; }
    main section { padding: 72px 0; border-bottom: 1px solid var(--line); }
    h2 { margin: 0 0 18px; font-size: 34px; letter-spacing: 0; }
    h3 { margin: 0 0 8px; font-size: 17px; }
    p { max-width: 760px; }
    .muted { color: var(--muted); }
    .grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-top: 32px; }
    .item { min-height: 170px; padding: 22px; border: 1px solid var(--line); border-radius: 6px; background: var(--panel); }
    .item p { margin-bottom: 0; color: var(--muted); }
    .steps { counter-reset: step; display: grid; gap: 0; margin-top: 30px; border-top: 1px solid var(--line); }
    .step { display: grid; grid-template-columns: 54px 1fr; padding: 22px 0; border-bottom: 1px solid var(--line); }
    .step:before { counter-increment: step; content: "0" counter(step); color: var(--blue); font: 700 13px/1.6 ui-monospace, SFMono-Regular, Menlo, monospace; }
    .risk { border-left: 3px solid var(--amber); padding-left: 22px; }
    footer { padding: 36px 0 56px; color: var(--muted); font-size: 14px; }
    footer .wrap { display: flex; flex-wrap: wrap; gap: 18px; }
    @media (max-width: 760px) { nav a:not(.brand):not(.status-link) { display: none; } .hero { min-height: 640px; background-position: center bottom 36px; background-size: auto, 210px; } .grid { grid-template-columns: 1fr; } h1 { font-size: 54px; } }
  </style>
</head>
<body>
  <header><nav class="wrap" aria-label="Primary"><a class="brand" href="/">DEXHelm</a><a href="#documentation">Docs</a><a href="#risk">Risk</a><a class="status-link" href="https://status.dexhelm.com/">Status</a></nav></header>
  <div class="hero">
    <div class="wrap">
      <div class="eyebrow">Independent open-source interface</div>
      <h1>DEXHelm</h1>
      <p class="lede">A non-custodial Hyperliquid terminal for trading, portfolio analysis, vault research, and account operations. The public terminal is currently limited to Testnet.</p>
      <div class="actions"><a class="button primary" href="https://testnet.dexhelm.com/trade">Open Testnet terminal</a></div>
    </div>
  </div>
  <main>
    <section id="product"><div class="wrap"><div class="eyebrow">Product</div><h2>Testnet-first trading terminal</h2><p class="muted">The public terminal is currently locked to Hyperliquid Testnet so users can evaluate the interface without Mainnet trading through DEXHelm.</p><div class="grid"><article class="item"><h3>Trade and manage</h3><p>Inspect markets, sign test orders, review positions, and manage supported account actions directly from your wallet.</p></article><article class="item"><h3>Analyze</h3><p>Explore portfolio performance, funding, vaults, leaderboards, staking, and optimization tools in one interface.</p></article><article class="item"><h3>Verify</h3><p>The client is source-available under AGPL-3.0. DEXHelm does not custody wallet keys or hold customer funds.</p></article></div></div></section>
    <section id="documentation"><div class="wrap"><div class="eyebrow">Documentation</div><h2>Getting started</h2><div class="steps"><div class="step"><div><h3>Use Testnet</h3><p class="muted">Testnet assets have no monetary value and may be reset by the network. The DEXHelm Mainnet terminal is temporarily unavailable.</p></div></div><div class="step"><div><h3>Verify the hostname</h3><p class="muted">The Testnet terminal is <strong>testnet.dexhelm.com</strong>. Check the complete HTTPS origin before connecting or signing.</p></div></div><div class="step"><div><h3>Connect and inspect every request</h3><p class="muted">Your wallet remains the signing authority. Read the network, action, amount, destination, and fees shown by the wallet before approval.</p></div></div><div class="step"><div><h3>Use service diagnostics</h3><p class="muted">Check <a href="https://status.dexhelm.com/">service status</a> when the terminal is unavailable. Protocol, wallet, RPC, and market-data incidents may remain external to DEXHelm.</p></div></div></div><p><a href="https://github.com/thegeronimo/hyperopen#readme">Open source documentation</a> · <a href="https://github.com/thegeronimo/hyperopen">Source repository</a></p></div></section>
    <section id="risk"><div class="wrap"><div class="eyebrow">Risk disclosure</div><div class="risk"><h2>Trading can result in rapid and total loss</h2><p>Leveraged perpetuals involve liquidation, volatility, liquidity, oracle, smart-contract, bridge, protocol, wallet, network, and interface risks. Transactions and signatures may be irreversible. DEXHelm is an independent interface, is not Hyperliquid, does not provide investment advice, and does not guarantee execution, availability, pricing, or recovery of assets.</p><p class="muted">Confirm the environment and all wallet prompts yourself. Begin with Testnet, use small amounts, and never share seed phrases or private keys. You remain responsible for legal, tax, and regulatory obligations in your jurisdiction.</p></div></div></section>
  </main>
  <footer><div class="wrap"><span>DEXHelm</span><a href="https://status.dexhelm.com/">Status</a><a href="https://github.com/thegeronimo/hyperopen">Source</a><a href="https://github.com/thegeronimo/hyperopen/blob/master/LICENSE">GNU AGPL v3</a><span>Independent from Hyperliquid.</span></div></footer>
</body>
</html>`;

const MAINNET_CLOSED_PAGE_HTML = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="noindex, nofollow">
  <title>Mainnet unavailable | DEXHelm</title>
  <style>
    :root { color-scheme: dark; --bg: #0b0d10; --line: #29313a; --text: #f2f5f7; --muted: #a9b3bd; --amber: #f4c15d; }
    * { box-sizing: border-box; }
    body { min-height: 100vh; margin: 0; display: grid; place-items: center; background: var(--bg); color: var(--text); font: 16px/1.6 Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    main { width: min(680px, calc(100% - 40px)); padding: 64px 0; }
    .eyebrow { color: var(--amber); font: 700 12px/1.3 ui-monospace, SFMono-Regular, Menlo, monospace; text-transform: uppercase; }
    h1 { margin: 16px 0; font-size: clamp(38px, 7vw, 64px); line-height: 1.05; letter-spacing: 0; }
    p { max-width: 620px; color: var(--muted); }
    .actions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 30px; padding-top: 24px; border-top: 1px solid var(--line); }
    a { min-height: 44px; padding: 9px 15px; border: 1px solid var(--line); border-radius: 6px; color: var(--text); text-decoration: none; font-weight: 700; }
  </style>
</head>
<body>
  <main>
    <div class="eyebrow">Service suspended</div>
    <h1>Mainnet terminal is temporarily unavailable</h1>
    <p>DEXHelm is currently operating on Testnet only. Mainnet trading, assets, and API proxy access are disabled on this hostname.</p>
    <div class="actions"><a href="https://testnet.dexhelm.com/trade">Open Testnet terminal</a><a href="https://status.dexhelm.com/">View service status</a></div>
  </main>
</body>
</html>`;

const STATUS_PAGE_HTML = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="Current DEXHelm edge and application status.">
  <title>DEXHelm service status</title>
  <style>
    :root { color-scheme: dark; --bg: #0b0d10; --panel: #12161b; --line: #29313a; --text: #f2f5f7; --muted: #a9b3bd; --green: #49d69d; --amber: #f4c15d; }
    * { box-sizing: border-box; }
    body { margin: 0; background: var(--bg); color: var(--text); font: 16px/1.55 Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    a { color: inherit; }
    .wrap { width: min(760px, calc(100% - 36px)); margin: 0 auto; }
    header { border-bottom: 1px solid var(--line); }
    nav { min-height: 64px; display: flex; align-items: center; justify-content: space-between; }
    .brand { font-weight: 760; text-decoration: none; }
    main { padding: 72px 0 96px; }
    .eyebrow { color: var(--green); font: 700 12px/1.3 ui-monospace, SFMono-Regular, Menlo, monospace; text-transform: uppercase; }
    h1 { margin: 14px 0 10px; font-size: 42px; letter-spacing: 0; }
    .muted { color: var(--muted); }
    .summary { margin: 36px 0; padding: 20px; border: 1px solid var(--green); border-radius: 6px; background: var(--panel); }
    .summary strong { display: block; color: var(--green); font-size: 18px; }
    .services { border-top: 1px solid var(--line); }
    .service { display: grid; grid-template-columns: 1fr auto; gap: 16px; padding: 20px 0; border-bottom: 1px solid var(--line); }
    .service span:last-child { color: var(--green); font: 700 13px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace; }
    .notice { margin-top: 36px; padding-left: 18px; border-left: 3px solid var(--amber); color: var(--muted); }
    @media (max-width: 520px) { .service { grid-template-columns: 1fr; gap: 4px; } h1 { font-size: 34px; } }
  </style>
</head>
<body>
  <header><nav class="wrap"><a class="brand" href="https://dexhelm.com/">DEXHelm</a><a href="https://testnet.dexhelm.com/trade">Testnet terminal</a></nav></header>
  <main class="wrap">
    <div class="eyebrow">Service status</div><h1>DEXHelm is reachable</h1><p class="muted">This page is served independently from the trading application bundle.</p>
    <div class="summary"><strong>Operational</strong><span>Cloudflare edge and DEXHelm Worker responded to this request.</span></div>
    <div class="services"><div class="service"><span>Mainnet terminal delivery</span><span>SUSPENDED</span></div><div class="service"><span>Testnet terminal delivery</span><span>AVAILABLE</span></div><div class="service"><span>DEXHelm health endpoint</span><span>AVAILABLE</span></div></div>
    <p><a href="/api/health">View machine-readable health JSON</a></p>
    <div class="notice">Hyperliquid, HyperUnit, wallet providers, RPC services, and market-data sources are independent dependencies. Their availability is not asserted by this page.</div>
  </main>
</body>
</html>`;

function htmlResponse(html, { cacheControl = "public, max-age=300", status = 200 } = {}) {
  return new Response(html, {
    status,
    headers: {
      ...DOCUMENT_SECURITY_HEADERS,
      "cache-control": cacheControl,
      "content-type": "text/html; charset=utf-8",
    },
  });
}

function mainnetClosedResponse() {
  return htmlResponse(MAINNET_CLOSED_PAGE_HTML, {
    cacheControl: "no-store",
    status: 503,
  });
}

function isDocumentNavigation(request) {
  const method = request.method.toUpperCase();
  return (
    ["GET", "HEAD"].includes(method) &&
    request.headers.get("accept")?.toLowerCase().includes("text/html") === true
  );
}

function isSafeDocumentMethod(request) {
  return ["GET", "HEAD"].includes(request.method.toUpperCase());
}

function canonicalTerminalRedirect(requestUrl, expectedNetwork) {
  const target = new URL(requestUrl);
  const isRoot = target.pathname === "/";
  const hasExpectedNetwork =
    target.searchParams.get(HYPERLIQUID_NETWORK_QUERY_KEY) === expectedNetwork &&
    target.searchParams.getAll(HYPERLIQUID_NETWORK_QUERY_KEY).length === 1;

  if (!isRoot && hasExpectedNetwork) {
    return null;
  }

  if (isRoot) {
    target.pathname = "/trade";
  }
  target.searchParams.set(HYPERLIQUID_NETWORK_QUERY_KEY, expectedNetwork);
  return Response.redirect(target, 307);
}

function apexDocumentResponse(requestUrl) {
  if (requestUrl.pathname === "/") {
    return htmlResponse(OPERATOR_PAGE_HTML);
  }
  if (requestUrl.pathname === "/docs" || requestUrl.pathname === "/docs/") {
    return Response.redirect(new URL("/#documentation", requestUrl), 308);
  }
  if (requestUrl.pathname === "/risk" || requestUrl.pathname === "/risk/") {
    return Response.redirect(new URL("/#risk", requestUrl), 308);
  }
  return htmlResponse(
    OPERATOR_PAGE_HTML.replace("<title>DEXHelm |", "<title>Not found |"),
    { cacheControl: "no-store", status: 404 }
  );
}

function statusDocumentResponse(requestUrl) {
  return htmlResponse(STATUS_PAGE_HTML, {
    cacheControl: "no-store",
    status: requestUrl.pathname === "/" ? 200 : 404,
  });
}

function matchesPathPrefix(pathname, prefix) {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

function isPortfolioOptimizerRoute(pathname) {
  const segments = pathname.split("/").filter(Boolean);
  if (segments[0] !== "portfolio" || segments[1] !== "optimize") {
    return false;
  }

  return (
    segments.length === 2 ||
    (segments.length === 3 && segments[2].length > 0) ||
    (segments.length === 4 && segments[2].length > 0 && segments[3] === "details")
  );
}

async function fetchStaticAsset(request, env) {
  const response = await env.ASSETS.fetch(request);
  const method = request.method.toUpperCase();
  const pathname = new URL(request.url).pathname;

  if (
    response.status !== 404 ||
    !["GET", "HEAD"].includes(method) ||
    !isPortfolioOptimizerRoute(pathname)
  ) {
    return response;
  }

  const shellUrl = new URL(request.url);
  shellUrl.pathname = PORTFOLIO_SHELL_PATH;
  shellUrl.search = "";
  shellUrl.hash = "";
  return env.ASSETS.fetch(new Request(shellUrl, request));
}

function matchingHyperunitRoute(pathname) {
  return matchesPathPrefix(pathname, HYPERUNIT_TESTNET_PREFIX)
    ? HYPERUNIT_TESTNET_ROUTE
    : null;
}

function validatedUpstreamOrigin(value) {
  try {
    const upstream = new URL(value);
    if (
      upstream.protocol !== "https:" ||
      upstream.username ||
      upstream.password ||
      upstream.pathname !== "/" ||
      upstream.search ||
      upstream.hash
    ) {
      return null;
    }
    return upstream;
  } catch (_error) {
    return null;
  }
}

function filteredRequestHeaders(headers) {
  const safeHeaders = new Headers();
  for (const [name, value] of headers) {
    if (REQUEST_HEADER_ALLOWLIST.has(name.toLowerCase())) {
      safeHeaders.set(name, value);
    }
  }
  return safeHeaders;
}

function filteredResponseHeaders(headers) {
  const safeHeaders = new Headers();
  for (const [name, value] of headers) {
    if (RESPONSE_HEADER_ALLOWLIST.has(name.toLowerCase())) {
      safeHeaders.set(name, value);
    }
  }
  return safeHeaders;
}

function genericProxyFailureResponse() {
  return new Response(JSON.stringify({ error: "HyperUnit proxy request failed." }), {
    status: 502,
    headers: { "content-type": "application/json" },
  });
}

function proxyMethodNotAllowedResponse() {
  return new Response(JSON.stringify({ error: "HyperUnit proxy method not allowed." }), {
    status: 405,
    headers: {
      allow: "GET, HEAD, POST",
      "content-type": "application/json",
    },
  });
}

function proxyBodyTooLargeResponse() {
  return new Response(JSON.stringify({ error: "HyperUnit proxy request body is too large." }), {
    status: 413,
    headers: { "content-type": "application/json" },
  });
}

function hostMayProxyHyperunit(hostname) {
  return hostname === DEXHELM_TESTNET_HOST;
}

function hostMayServeAssets(hostname) {
  return hostname === DEXHELM_TESTNET_HOST;
}

function notFoundResponse() {
  return new Response("Not found", {
    status: 404,
    headers: { "cache-control": "no-store", "content-type": "text/plain; charset=utf-8" },
  });
}

function proxyAbortError() {
  const error = new Error("proxy request deadline exceeded");
  error.name = "AbortError";
  error.code = "PROXY_ABORTED";
  return error;
}

async function readBoundedRequestBody(request, signal) {
  const declaredLength = request.headers.get("content-length");
  if (declaredLength !== null) {
    const parsedLength = Number(declaredLength);
    if (!Number.isSafeInteger(parsedLength) || parsedLength < 0) {
      throw new Error("invalid request content length");
    }
    if (parsedLength > MAX_PROXY_BODY_BYTES) {
      const error = new Error("request body is too large");
      error.code = "BODY_TOO_LARGE";
      throw error;
    }
  }

  if (!request.body) {
    return null;
  }

  const reader = request.body.getReader();
  const chunks = [];
  let total = 0;
  let abortListener;
  const aborted = new Promise((_resolve, reject) => {
    abortListener = () => {
      reject(proxyAbortError());
      void reader.cancel(proxyAbortError()).catch(() => {});
    };
    signal?.addEventListener("abort", abortListener, { once: true });
  });
  try {
    if (signal?.aborted) {
      abortListener();
    }
    while (true) {
      const read = reader.read();
      const { done, value } = signal ? await Promise.race([read, aborted]) : await read;
      if (signal?.aborted) throw proxyAbortError();
      if (done) break;
      total += value.byteLength;
      if (total > MAX_PROXY_BODY_BYTES) {
        const error = new Error("request body is too large");
        error.code = "BODY_TOO_LARGE";
        await reader.cancel();
        throw error;
      }
      chunks.push(value);
    }
  } finally {
    if (abortListener) {
      signal?.removeEventListener("abort", abortListener);
    }
    reader.releaseLock();
  }

  const body = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return body;
}

function responseBodyWithDeadline(body, signal, finish) {
  const reader = body.getReader();
  let ended = false;
  let controller;

  const finishOnce = () => {
    if (ended) return false;
    ended = true;
    signal.removeEventListener("abort", abortListener);
    finish();
    return true;
  };
  const abortListener = () => {
    if (!finishOnce()) return;
    void reader.cancel(proxyAbortError()).catch(() => {});
    controller?.error(proxyAbortError());
  };
  signal.addEventListener("abort", abortListener, { once: true });

  return new ReadableStream({
    start(streamController) {
      controller = streamController;
      if (signal.aborted) abortListener();
    },
    async pull(streamController) {
      if (ended) return;
      try {
        const { done, value } = await reader.read();
        if (ended) return;
        if (done) {
          finishOnce();
          streamController.close();
          return;
        }
        streamController.enqueue(value);
      } catch (error) {
        if (finishOnce()) streamController.error(error);
      }
    },
    async cancel(reason) {
      if (!finishOnce()) return;
      await reader.cancel(reason);
    },
  });
}

export function resolveHyperunitTarget(requestUrl, env) {
  const incoming = requestUrl instanceof URL ? requestUrl : new URL(requestUrl);
  if (incoming.hostname !== DEXHELM_TESTNET_HOST) {
    return null;
  }
  const route = matchingHyperunitRoute(incoming.pathname);
  if (!route) {
    return null;
  }

  const upstream = validatedUpstreamOrigin(env?.[route.environmentKey]);
  if (!upstream) {
    return null;
  }

  const suffix = incoming.pathname.slice(route.prefix.length) || "/";
  return new URL(`${suffix}${incoming.search}`, upstream);
}

export function buildHyperunitRequest(request, targetUrl, body = undefined, signal = undefined) {
  const method = request.method.toUpperCase();
  const init = {
    method,
    headers: filteredRequestHeaders(request.headers),
    redirect: "manual",
  };

  if (signal) {
    init.signal = signal;
  }

  if (method !== "GET" && method !== "HEAD") {
    init.body = body === undefined ? request.body : body;
    init.duplex = "half";
  }

  return new Request(targetUrl, init);
}

export async function handleRequest(request, env, {
  fetchImpl = fetch,
  setTimeoutImpl = setTimeout,
  clearTimeoutImpl = clearTimeout,
  AbortControllerImpl = AbortController,
  timeoutMs = PROXY_TIMEOUT_MS,
} = {}) {
  const requestUrl = new URL(request.url);

  if (requestUrl.hostname === DEXHELM_MAINNET_HOST) {
    return mainnetClosedResponse();
  }

  if (requestUrl.pathname === "/api/health") {
    if (![DEXHELM_STATUS_HOST, DEXHELM_TESTNET_HOST].includes(requestUrl.hostname)) {
      return notFoundResponse();
    }
    return new Response(JSON.stringify({ status: "ok" }), {
      headers: {
        "cache-control": "no-store",
        "content-type": "application/json",
      },
    });
  }

  if (isSafeDocumentMethod(request) && requestUrl.hostname === DEXHELM_APEX_HOST) {
    if (["/", "/docs", "/docs/", "/risk", "/risk/"].includes(requestUrl.pathname)) {
      return apexDocumentResponse(requestUrl);
    }
  }

  if (
    isSafeDocumentMethod(request) &&
    requestUrl.hostname === DEXHELM_STATUS_HOST &&
    requestUrl.pathname === "/"
  ) {
    return statusDocumentResponse(requestUrl);
  }

  if (isDocumentNavigation(request)) {
    if (requestUrl.hostname === DEXHELM_APEX_HOST) {
      return apexDocumentResponse(requestUrl);
    }
    if (requestUrl.hostname === DEXHELM_STATUS_HOST) {
      return statusDocumentResponse(requestUrl);
    }
    if (requestUrl.hostname === DEXHELM_MAINNET_HOST) {
      const redirect = canonicalTerminalRedirect(requestUrl, "mainnet");
      if (redirect) {
        return redirect;
      }
    }
    if (requestUrl.hostname === DEXHELM_TESTNET_HOST) {
      const redirect = canonicalTerminalRedirect(requestUrl, "testnet");
      if (redirect) {
        return redirect;
      }
    }
  }

  const route = matchingHyperunitRoute(requestUrl.pathname);
  if (!route) {
    if (matchesPathPrefix(requestUrl.pathname, HYPERUNIT_ROOT_PREFIX)) {
      return notFoundResponse();
    }
    if (!hostMayServeAssets(requestUrl.hostname)) {
      return notFoundResponse();
    }
    return fetchStaticAsset(request, env);
  }

  if (
    !hostMayProxyHyperunit(requestUrl.hostname) ||
    route.prefix !== HYPERUNIT_TESTNET_PREFIX
  ) {
    return notFoundResponse();
  }

  const method = request.method.toUpperCase();
  if (!HYPERUNIT_METHOD_ALLOWLIST.has(method)) {
    return proxyMethodNotAllowedResponse();
  }

  const targetUrl = resolveHyperunitTarget(requestUrl, env);
  if (!targetUrl) {
    return genericProxyFailureResponse();
  }

  const controller = new AbortControllerImpl();
  const timeout = setTimeoutImpl(() => controller.abort(), timeoutMs);
  let deadlineFinished = false;
  const finishDeadline = () => {
    if (deadlineFinished) return;
    deadlineFinished = true;
    clearTimeoutImpl(timeout);
  };

  let body;
  try {
    body = method === "POST" ? await readBoundedRequestBody(request, controller.signal) : null;
  } catch (error) {
    finishDeadline();
    if (error?.code === "BODY_TOO_LARGE") {
      return proxyBodyTooLargeResponse();
    }
    return genericProxyFailureResponse();
  }

  try {
    const upstreamResponse = await fetchImpl(
      buildHyperunitRequest(request, targetUrl, body, controller.signal)
    );
    const responseBody = method === "HEAD" || !upstreamResponse.body
      ? null
      : responseBodyWithDeadline(upstreamResponse.body, controller.signal, finishDeadline);
    if (!responseBody) finishDeadline();
    return new Response(responseBody, {
      status: upstreamResponse.status,
      headers: filteredResponseHeaders(upstreamResponse.headers),
    });
  } catch (_error) {
    finishDeadline();
    return genericProxyFailureResponse();
  }
}

export default {
  fetch(request, env) {
    return handleRequest(request, env);
  },
};
