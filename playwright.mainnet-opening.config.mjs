import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tools/playwright/test",
  testMatch: /dexhelm-mainnet-opening\.spec\.mjs/,
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  reporter: [["list"], ["html", { open: "never", outputFolder: "tmp/playwright/report/mainnet-opening" }]],
  use: {
    headless: true,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
  },
});
