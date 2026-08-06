import { expect, test } from "@playwright/test";
import { spawn } from "node:child_process";
import fs from "node:fs/promises";
import https from "node:https";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..", "..");
const wrangler = path.join(repositoryRoot, "node_modules", ".bin", "wrangler");
const appPort = Number(process.env.PLAYWRIGHT_MAINNET_OPENING_APP_PORT || 8795);
const testnetPort = Number(process.env.PLAYWRIGHT_MAINNET_OPENING_TESTNET_PORT || 8796);
const candidateRoot = path.resolve(
  repositoryRoot,
  process.env.PLAYWRIGHT_MAINNET_OPENING_ROOT || "out/cloudflare/dexhelm-mainnet-opening"
);

function startWorker(port, upstream) {
  return spawn(wrangler, [
    "dev", "--local", "--local-protocol", "https", "--local-upstream", upstream,
    "--config", "wrangler.mainnet-opening.jsonc", "--port", String(port),
  ], { cwd: repositoryRoot, stdio: ["ignore", "pipe", "pipe"] });
}

function waitForWorker(port) {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + 30_000;
    const probe = () => {
      const request = https.get({ hostname: "127.0.0.1", port, path: "/api/health", rejectUnauthorized: false }, (response) => {
        response.resume();
        if (response.statusCode) return resolve();
        retry();
      });
      request.on("error", retry);
      function retry() {
        if (Date.now() >= deadline) return reject(new Error("Worker did not start on " + port + "."));
        setTimeout(probe, 100);
      }
    };
    probe();
  });
}

let appWorker;
let testnetWorker;

test.beforeAll(async () => {
  const manifest = JSON.parse(await fs.readFile(path.join(candidateRoot, "tenant-manifest.json"), "utf8"));
  expect(manifest.tenant["hyperliquid-network"]).toBe("testnet");
  appWorker = startWorker(appPort, "app.dexhelm.com");
  await waitForWorker(appPort);
  testnetWorker = startWorker(testnetPort, "testnet.dexhelm.com");
  await waitForWorker(testnetPort);
});

test.afterAll(() => {
  appWorker?.kill("SIGTERM");
  testnetWorker?.kill("SIGTERM");
});

test.use({
  ignoreHTTPSErrors: true,
  launchOptions: {
    args: [
      "--host-resolver-rules=MAP app.dexhelm.com 127.0.0.1,MAP testnet.dexhelm.com 127.0.0.1",
    ],
  },
});

for (const viewport of [
  { width: 375, height: 812 },
  { width: 768, height: 900 },
  { width: 1280, height: 900 },
  { width: 1440, height: 900 },
]) {
  test.describe("Mainnet opening host matrix at " + viewport.width + "px", () => {
    test.use({ viewport });

    for (const surface of [
      { host: "app.dexhelm.com", port: appPort, network: "mainnet", opposite: "testnet" },
      { host: "testnet.dexhelm.com", port: testnetPort, network: "testnet", opposite: "mainnet" },
    ]) {
      test(surface.host + " forces " + surface.network + " without wallet actions", async ({ page }) => {
        const localFailures = [];
        page.on("console", (message) => {
          const text = message.text();
          // Wrangler local rewrites the canonical logo source to the local
          // port in its CSP; production uses the portless canonical origin.
          const expectedLocalLogoCspWarning =
            text.includes("Content Security Policy") &&
            text.includes("brand/dexhelm-mark.svg");
          if (message.type() === "error" &&
              !text.includes("goog#html") &&
              !expectedLocalLogoCspWarning) {
            localFailures.push(text);
          }
        });
        const requests = [];
        page.on("request", (request) => requests.push(request.url()));
        await page.goto(
          "https://" + surface.host + ":" + surface.port + "/trade?coin=BTC&hyperliquidNetwork=" + surface.opposite,
          { waitUntil: "domcontentloaded" }
        );
        await expect(page).toHaveTitle(/DEXHelm/);
        await expect(page.locator("[data-role='header-brand-name']")).toHaveText("DEXHelm");
        await expect(page.locator("[data-role='trade-product-context-banner-network']"))
          .toHaveText(`Network: ${surface.network === "mainnet" ? "Mainnet" : "Testnet"}`);
        await expect.poll(() => page.locator("[data-role='header-brand-name']")
          // Chromium exposes an authored zero letter-spacing as `normal` in CSSOM.
          .evaluate((node) => {
            const value = getComputedStyle(node).letterSpacing;
            return value === "normal" ? "0px" : value;
          })).toBe("0px");
        await expect(page.locator("link[rel='canonical']")).toHaveAttribute(
          "href", "https://" + surface.host + "/trade"
        );
        const assetCachePolicies = await page.evaluate(async () => {
          const stylesheetHref = document.querySelector("link[rel='stylesheet']")?.getAttribute("href");
          const mainScriptHref = document.querySelector("script[src*='/js/main.']")?.getAttribute("src");
          return Object.fromEntries(await Promise.all([
            ["stylesheet", stylesheetHref],
            ["mainScript", mainScriptHref],
          ].map(async ([label, href]) => {
            if (!href) return [label, null];
            const response = await fetch(href, { method: "HEAD" });
            return [label, {
              href,
              status: response.status,
              cacheControl: response.headers.get("cache-control"),
            }];
          })));
        });
        for (const asset of Object.values(assetCachePolicies)) {
          expect(asset?.href).toMatch(/^\/(?:css|js)\/.+\.[A-F0-9]{16,32}\.(?:css|js)$/);
          expect(asset?.status).toBe(200);
          expect(asset?.cacheControl).toBe("public, max-age=31556952, immutable");
        }
        const manifest = await page.evaluate(async () => fetch("/tenant-manifest.json").then((response) => response.json()));
        expect(manifest.tenant["hyperliquid-network"]).toBe(surface.network);
        const dimensions = await page.evaluate(() => ({
          documentWidth: document.documentElement.scrollWidth,
          viewportWidth: document.documentElement.clientWidth,
        }));
        expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewportWidth + 1);
        expect(requests.some((url) => /wallet|signature|funding|order/i.test(url))).toBe(false);
        expect(localFailures).toEqual([]);
      });
    }
  });
}
