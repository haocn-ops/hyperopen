import { defineConfig } from "@playwright/test";

const ci = process.env.CI === "1" || process.env.CI === "true";
const releaseRoot = process.env.PLAYWRIGHT_WHITE_LABEL_ROOT;

if (!releaseRoot) {
  throw new Error("PLAYWRIGHT_WHITE_LABEL_ROOT must name a prepared white-label release directory.");
}

const port = Number(process.env.PLAYWRIGHT_WEB_PORT || 4175);
const baseURL = process.env.PLAYWRIGHT_BASE_URL || `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: "./tools/playwright/test",
  testMatch: /white-label-release\.spec\.mjs/,
  timeout: 45_000,
  fullyParallel: false,
  forbidOnly: ci,
  retries: ci ? 1 : 0,
  workers: 1,
  outputDir: "tmp/playwright/test-results/white-label",
  reporter: [
    ["list"],
    ["html", { open: "never", outputFolder: "tmp/playwright/report/white-label" }],
  ],
  use: {
    baseURL,
    headless: true,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
  },
  webServer: {
    command: "node tools/playwright/static_server.mjs",
    url: `${baseURL}/`,
    env: {
      ...process.env,
      PLAYWRIGHT_STATIC_ROOT: releaseRoot,
      PLAYWRIGHT_WEB_PORT: String(port),
    },
    reuseExistingServer: false,
    timeout: 120_000,
  },
});
