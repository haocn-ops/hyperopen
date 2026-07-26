import { expect, test } from "@playwright/test";
import { dispatch, visitRoute, waitForIdle } from "../support/hyperopen.mjs";

const ROUTES = ["/trade", "/portfolio"];
const TENANT_LOGO_URL = "https://cdn.example.test/desk-alpha.svg";
const TENANT_LOGO_SVG = `
  <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32">
    <rect width="32" height="32" fill="#0f766e" />
    <path d="M8 8h8c5 0 8 3 8 8s-3 8-8 8H8V8Zm6 5v6h2c2 0 3-1 3-3s-1-3-3-3h-2Z" fill="#fff" />
  </svg>`;
const VIEWPORTS = [
  { width: 375, height: 812 },
  { width: 768, height: 900 },
  { width: 1280, height: 900 },
  { width: 1440, height: 900 }
];

const EXPECTED = Object.freeze({
  brand: process.env.HYPEROPEN_EXPECT_BRAND || "HyperOpen",
  venue: process.env.HYPEROPEN_EXPECT_VENUE || "Hyperliquid",
  affiliateStatus:
    process.env.HYPEROPEN_EXPECT_AFFILIATE_STATUS || "unavailable",
  theme: process.env.HYPEROPEN_EXPECT_THEME || "dark",
  hasLogo: /^(1|true|yes)$/i.test(process.env.HYPEROPEN_EXPECT_HAS_LOGO || "false")
});

function textPattern(value) {
  const escaped = String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(escaped, "i");
}

test.beforeEach(async ({ page }) => {
  if (EXPECTED.hasLogo) {
    await page.route(TENANT_LOGO_URL, (route) => route.fulfill({
      status: 200,
      contentType: "image/svg+xml",
      body: TENANT_LOGO_SVG
    }));
  }
});

async function assertBrandLogoAnchors(page, mobile) {
  for (const prefix of ["mobile-brand", "header-brand"]) {
    const shell = page.locator(`[data-role='${prefix}-logo-shell']`);
    const image = page.locator(`[data-role='${prefix}-logo']`);
    const fallback = page.locator(`[data-role='${prefix}-fallback']`);

    await expect(fallback).toHaveCount(1);
    if (EXPECTED.hasLogo) {
      await expect(shell).toHaveCount(1);
      await expect(image).toHaveCount(1);
    } else {
      await expect(shell).toHaveCount(0);
      await expect(image).toHaveCount(0);
    }
  }

  const activePrefix = mobile ? "mobile-brand" : "header-brand";
  if (EXPECTED.hasLogo) {
    const activeImage = page.locator(`[data-role='${activePrefix}-logo']`);
    await expect(activeImage).toBeVisible();
    await expect.poll(() => activeImage.evaluate((node) => node.naturalWidth))
      .toBeGreaterThan(0);
  } else {
    await expect(page.locator(`[data-role='${activePrefix}-fallback']`)).toBeVisible();
  }
}

async function assertProductContext(page, route) {
  const desktopBrand = page.locator("[data-role='header-brand-name']");
  const mobileBrand = page.locator("[data-role='mobile-brand']");
  const mobile = (page.viewportSize()?.width || 0) < 768;
  if (mobile) {
    await expect(mobileBrand).toBeVisible();
  } else {
    await expect(desktopBrand).toBeVisible();
    await expect(desktopBrand).toContainText(textPattern(EXPECTED.brand));
  }
  await assertBrandLogoAnchors(page, mobile);
  await expect(page.locator("html")).toHaveAttribute("data-theme", EXPECTED.theme);

  const bannerRole = route === "/trade"
    ? "trade-product-context-banner"
    : "portfolio-product-context-banner";
  const banner = page.locator(`[data-role='${bannerRole}']`);
  await expect(banner).toBeVisible();
  await expect(
    banner.locator(`[data-role='${bannerRole}-brand']`)
  ).toContainText(textPattern(EXPECTED.brand));
  await expect(
    banner.locator(`[data-role='${bannerRole}-venue']`)
  ).toContainText(textPattern(EXPECTED.venue));
  await expect(
    banner.locator(`[data-role='${bannerRole}-affiliate-status']`)
  ).toContainText(textPattern(EXPECTED.affiliateStatus));
  await expect(
    banner.locator(`[data-role='${bannerRole}-affiliate-disclosure']`)
  ).toContainText(/affiliate|官方/i);
}

async function seedAnalyticsDisabledTenant(page) {
  await page.evaluate(() => {
    const c = globalThis.cljs?.core;
    const store = globalThis.hyperopen?.system?.store;
    const defaultTenant = globalThis.hyperopen?.service?.tenant_config?.default_tenant_raw;
    const renderApp = globalThis.hyperopen?.app?.bootstrap?.render_app_BANG_;
    if (!c || !store || !defaultTenant || typeof renderApp !== "function") {
      throw new Error("tenant state injection seam unavailable");
    }

    const keyword = c.keyword;
    const path = (...segments) =>
      c.PersistentVector.fromArray(segments.map((segment) => keyword(segment)), true);
    const tenant = c.assoc_in(defaultTenant, path("features", "analytics"), false);
    const nextState = c.assoc(c.deref(store), keyword("tenant/override"), tenant);
    c.reset_BANG_(store, nextState);
    renderApp(c.deref(store));
  });
}

async function assertNoHorizontalOverflow(page) {
  const dimensions = await page.evaluate(() => ({
    documentScrollWidth: document.documentElement.scrollWidth,
    documentClientWidth: document.documentElement.clientWidth,
    bodyScrollWidth: document.body?.scrollWidth || 0,
    bodyClientWidth: document.body?.clientWidth || 0
  }));

  expect(dimensions.documentScrollWidth).toBeLessThanOrEqual(
    dimensions.documentClientWidth + 1
  );
  expect(dimensions.bodyScrollWidth).toBeLessThanOrEqual(
    dimensions.bodyClientWidth + 1
  );
}

for (const viewport of VIEWPORTS) {
  test.describe(`product context ${viewport.width}px`, () => {
    test.use({ viewport });

    for (const route of ROUTES) {
      test(`${route} exposes shared product context without horizontal overflow`, async ({ page }) => {
        await visitRoute(page, route);
        await assertProductContext(page, route);
        await assertNoHorizontalOverflow(page);
      });
    }
  });
}

for (const width of [1280, 1440]) {
  test.describe(`trade desktop parity ${width}px`, () => {
    test.use({ viewport: { width, height: 900 } });

    test("chart, orderbook, and account table panels have stable flush geometry", async ({ page }) => {
      await visitRoute(page, "/trade");
      await waitForIdle(page, { quietMs: 200, timeoutMs: 6_000, pollMs: 50 });

      const geometry = await page.evaluate(() => {
        const rectFor = (parityId) => {
          const node = document.querySelector(`[data-parity-id='${parityId}']`);
          if (!node) return null;
          const rect = node.getBoundingClientRect();
          return {
            top: rect.top,
            bottom: rect.bottom,
            width: rect.width,
            height: rect.height
          };
        };

        return {
          chart: rectFor("trade-chart-panel"),
          orderbook: rectFor("trade-orderbook-panel"),
          accountPanel: rectFor("trade-account-tables-panel"),
          accountTables: rectFor("account-tables")
        };
      });

      expect(geometry.chart).not.toBeNull();
      expect(geometry.orderbook).not.toBeNull();
      expect(geometry.accountPanel).not.toBeNull();
      expect(geometry.accountTables).not.toBeNull();
      expect(geometry.chart.width).toBeGreaterThan(0);
      expect(geometry.orderbook.width).toBeGreaterThan(0);
      expect(geometry.accountTables.width).toBeGreaterThan(0);
      expect(Math.abs(geometry.accountPanel.top - geometry.chart.bottom)).toBeLessThanOrEqual(2);
      expect(Math.abs(geometry.accountPanel.top - geometry.orderbook.bottom)).toBeLessThanOrEqual(2);
    });
  });
}

test.describe("tenant feature gating", () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test("analytics-disabled tenant hides portfolio navigation and redirects direct access", async ({ page }) => {
    await visitRoute(page, "/trade");
    await seedAnalyticsDisabledTenant(page);

    const headerNav = page.locator("[data-parity-id='header-nav']");
    await expect(headerNav.getByRole("link", { name: "Portfolio", exact: true })).toHaveCount(0);
    await expect(headerNav.getByRole("link", { name: "Optimize", exact: true })).toHaveCount(0);

    await dispatch(page, [":actions/navigate", "/portfolio"]);
    await waitForIdle(page, { quietMs: 200, timeoutMs: 6_000, pollMs: 50 });
    await expect(page).toHaveURL(/\/trade(?:[?#].*)?$/);
    await expect(page.locator("[data-parity-id='trade-root']")).toBeVisible();
  });
});

for (const width of [375, 1280]) {
  test.describe(`wallet provider feedback ${width}px`, () => {
    test.use({ viewport: { width, height: 900 } });

    test("connect reports when no browser wallet provider is available @smoke", async ({ page }) => {
      await page.goto("/trade", { waitUntil: "domcontentloaded" });
      await expect(page.locator("[data-role='wallet-connect-button']")).toBeVisible();
      await page.locator("[data-role='wallet-connect-button']").click();

      const error = page.locator("[data-role='wallet-connect-error']");
      await expect(error).toBeVisible();
      await expect(error).toHaveAttribute("role", "alert");
      await expect(error).toContainText(/No browser wallet detected/i);
      await expect(page.locator("[data-role='wallet-connect-button']"))
        .toHaveAttribute("aria-describedby", "wallet-connect-error");
      await assertNoHorizontalOverflow(page);
    });
  });
}

if (EXPECTED.hasLogo) {
  test.describe("tenant logo failure", () => {
    test.use({ viewport: { width: 1280, height: 900 } });

    test("broken logo reports zero natural width and reveals the fallback", async ({ page }) => {
      await visitRoute(page, "/trade");
      const image = page.locator("[data-role='header-brand-logo']");
      await expect(image).toBeVisible();
      await expect(page.locator("[data-role='header-brand-logo-shell']")).toBeVisible();
      await expect(page.locator("[data-role='header-brand-fallback']")).toHaveCount(1);
      await expect.poll(() => image.evaluate((node) => node.naturalWidth))
        .toBeGreaterThan(0);

      const naturalWidth = await image.evaluate((node) => {
        Object.defineProperty(node, "naturalWidth", {
          configurable: true,
          value: 0
        });
        return node.naturalWidth;
      });
      expect(naturalWidth).toBe(0);
      await image.dispatchEvent("error");
      await waitForIdle(page, { quietMs: 200, timeoutMs: 6_000, pollMs: 50 });

      await expect(page.locator("[data-role='header-brand-logo']")).toHaveCount(0);
      await expect(page.locator("[data-role='mobile-brand-logo']")).toHaveCount(0);
      await expect(page.locator("[data-role='header-brand-fallback']")).toBeVisible();
      await expect(page.locator("[data-role='mobile-brand-fallback']")).toHaveCount(1);
    });
  });
}
