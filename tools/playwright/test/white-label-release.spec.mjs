import { expect, test } from "@playwright/test";

const expected = Object.freeze({
  brand: process.env.HYPEROPEN_EXPECT_BRAND || "Enterprise Desk",
  tenantId: process.env.HYPEROPEN_EXPECT_TENANT_ID || "enterprise-example",
  theme: process.env.HYPEROPEN_EXPECT_THEME || "institutional",
  origin: process.env.HYPEROPEN_EXPECT_ORIGIN || "https://desk.example.com",
  enabledRoute: process.env.HYPEROPEN_EXPECT_ENABLED_ROUTE || "/portfolio",
  disabledRoute: Object.hasOwn(process.env, "HYPEROPEN_EXPECT_DISABLED_ROUTE")
    ? process.env.HYPEROPEN_EXPECT_DISABLED_ROUTE
    : "/trade",
  logoUrl: process.env.HYPEROPEN_EXPECT_LOGO_URL || "",
});
const viewports = [
  { width: 375, height: 812 },
  { width: 768, height: 900 },
  { width: 1280, height: 900 },
  { width: 1440, height: 900 },
];

function installFailureCapture(page) {
  const failures = [];
  page.on("console", (message) => {
    if (message.type() === "error") {
      failures.push(`console: ${message.text()}`);
    }
  });
  page.on("requestfailed", (request) => failures.push(`request: ${request.url()}`));
  return failures;
}

for (const viewport of viewports) {
  test.describe(`white-label sample release at ${viewport.width}px`, () => {
    test.use({ viewport });
    test.skip(
      !process.env.PLAYWRIGHT_WHITE_LABEL_ROOT,
      "requires an isolated sample release served through PLAYWRIGHT_WHITE_LABEL_ROOT"
    );

    test("renders compiled tenant identity, public metadata, and enabled routes only", async ({ page, request }) => {
      const failures = installFailureCapture(page);
      const metadataResponse = await request.get("/site-metadata.json");
      const metadata = await metadataResponse.json();
      const disabledArtifact = expected.disabledRoute
        ? await request.get(`${expected.disabledRoute}.html`)
        : null;

      if (expected.logoUrl) {
        const logoPath = new URL(expected.logoUrl).pathname;
        const localLogoResponse = await request.get(logoPath);
        expect(localLogoResponse.ok()).toBe(true);
        const localLogoBody = await localLogoResponse.body();
        await page.route(expected.logoUrl, (route) =>
          route.fulfill({
            status: 200,
            contentType: localLogoResponse.headers()["content-type"] || "image/svg+xml",
            body: localLogoBody,
          })
        );
      }

      expect(metadataResponse.ok()).toBe(true);
      expect(metadata.siteName).toBe(expected.brand);
      expect(metadata.origin).toBe(expected.origin);
      expect(metadata.routes.map((route) => route.path)).toContain(expected.enabledRoute);
      if (expected.disabledRoute) {
        expect(metadata.routes.map((route) => route.path)).not.toContain(expected.disabledRoute);
        expect(disabledArtifact.status()).toBe(404);
      }

      await page.goto("/", { waitUntil: "networkidle" });
      await expect(page.locator("html")).toHaveAttribute("data-theme", expected.theme);
      await expect(page.getByText(expected.brand, { exact: true }).first()).toBeVisible();
      await expect(page).toHaveTitle(new RegExp(expected.brand));
      await expect(page.locator("link[rel='canonical']")).toHaveAttribute("href", `${expected.origin}/`);
      await expect(page.locator("meta[property='og:url']")).toHaveAttribute("content", `${expected.origin}/`);

      const mainScriptHref = await page.locator("script[src*='main.'][src$='.js']").getAttribute("src");
      expect(mainScriptHref).toBeTruthy();
      const mainBundle = await (await request.get(mainScriptHref)).text();
      expect(mainBundle).toContain("tenant/id");
      expect(mainBundle).toContain(expected.tenantId);
      expect(mainBundle).toContain(expected.brand);

      await page.goto(expected.enabledRoute, { waitUntil: "networkidle" });
      await expect(page.locator("link[rel='canonical']")).toHaveAttribute(
        "href",
        `${expected.origin}${expected.enabledRoute}`
      );
      await expect(page.locator("[data-role='header-brand-name']")).toHaveText(expected.brand);
      if (expected.logoUrl) {
        const logoRole = viewport.width >= 1024 ? "header-brand-logo" : "mobile-brand-logo";
        await expect(page.locator(`[data-role='${logoRole}']`)).toHaveAttribute("src", expected.logoUrl);
        await expect(page.locator(`[data-role='${logoRole}']`)).toBeVisible();
      }
      if (viewport.width === 375) {
        const mobileBrandGeometry = await page.evaluate(() => {
          const logo = document.querySelector("[data-role='mobile-brand-logo']");
          const wordmark = document.querySelector("[data-role='header-brand-name']");
          if (!logo || !wordmark) return null;
          return {
            logoRight: logo.getBoundingClientRect().right,
            wordmarkLeft: wordmark.getBoundingClientRect().left,
          };
        });
        expect(mobileBrandGeometry).not.toBeNull();
        expect(mobileBrandGeometry.logoRight).toBeLessThanOrEqual(mobileBrandGeometry.wordmarkLeft);
      }
      const dimensions = await page.evaluate(() => ({
        documentWidth: document.documentElement.scrollWidth,
        viewportWidth: document.documentElement.clientWidth,
        appRootWidth: document.querySelector("[data-parity-id='app-root']")?.scrollWidth ?? 0,
        appRootClientWidth: document.querySelector("[data-parity-id='app-root']")?.clientWidth ?? 0,
        headerWidth: document.querySelector("[data-parity-id='header']")?.scrollWidth ?? 0,
        headerClientWidth: document.querySelector("[data-parity-id='header']")?.clientWidth ?? 0,
      }));
      expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewportWidth + 1);
      expect(dimensions.appRootWidth).toBeLessThanOrEqual(dimensions.appRootClientWidth + 1);
      expect(dimensions.headerWidth).toBeLessThanOrEqual(dimensions.headerClientWidth + 1);
      expect(failures).toEqual([]);
    });
  });
}
