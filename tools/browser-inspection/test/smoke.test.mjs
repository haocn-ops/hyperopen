import { execFile } from "node:child_process";
import fs from "node:fs/promises";
import { promisify } from "node:util";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";

const execFileAsync = promisify(execFile);
const cliPath = path.resolve("tools/browser-inspection/src/cli.mjs");
const enabled = process.env.RUN_BROWSER_INSPECTION_SMOKE === "1";
const spectateFixtureUrl =
  "http://localhost:8080/trade?spectate=0x162cc7c861ebd0c06b3d72319201150482518185";

async function runCli(args) {
  const { stdout } = await execFileAsync(process.execPath, [cliPath, ...args]);
  return JSON.parse(stdout);
}

async function runCliAllowFailure(args) {
  try {
    return await runCli(args);
  } catch (error) {
    if (error?.stdout) {
      return JSON.parse(error.stdout);
    }
    throw error;
  }
}

async function pollUntil(fn, predicate, { timeoutMs = 60000, intervalMs = 1000 } = {}) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const value = await fn();
    if (predicate(value)) {
      return value;
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  throw new Error("Timed out waiting for smoke-test condition");
}

test(
  "browser inspection smoke test",
  {
    skip: !enabled,
    timeout: 180000
  },
  async () => {
    const session = await runCli(["session", "start"]);
    assert.ok(session.id);

    try {
      const inspectResult = await runCli([
        "inspect",
        "--session-id",
        session.id,
        "--url",
        "https://app.hyperliquid.xyz/trade",
        "--target",
        "smoke",
        "--viewports",
        "desktop"
      ]);
      assert.ok(inspectResult.runDir);
      assert.ok(inspectResult.snapshots.length >= 1);

      const compareResult = await runCli([
        "compare",
        "--session-id",
        session.id,
        "--left-url",
        "https://app.hyperliquid.xyz/trade",
        "--right-url",
        "https://app.hyperliquid.xyz/trade",
        "--left-label",
        "left",
        "--right-label",
        "right",
        "--viewports",
        "desktop"
      ]);
      assert.ok(compareResult.runDir);
      assert.ok(compareResult.viewportReports.length >= 1);
    } finally {
      await runCli(["session", "stop", "--session-id", session.id]).catch(() => null);
    }
  }
);

test(
  "browser inspection local smoke resolves visible open-order spot ids",
  {
    skip: !enabled,
    timeout: 240000
  },
  async () => {
    const session = await runCli(["session", "start", "--manage-local-app"]);
    assert.ok(session.id);

    try {
      const navigateResult = await runCli([
        "navigate",
        "--session-id",
        session.id,
        "--url",
        spectateFixtureUrl,
        "--viewport",
        "desktop"
      ]);
      assert.equal(navigateResult.url, spectateFixtureUrl);

      const tabResult = await runCli([
        "eval",
        "--session-id",
        session.id,
        "--allow-unsafe-eval",
        "--expression",
        `(() => {
          const tab = Array.from(document.querySelectorAll("button"))
            .find((el) => (el.textContent || "").trim().startsWith("Open Orders ("));
          if (!tab) {
            return { clicked: false };
          }
          tab.click();
          return { clicked: true, text: tab.textContent.trim() };
        })()`
      ]);
      assert.equal(tabResult.result.clicked, true);

      const probe = await pollUntil(
        async () => {
          const result = await runCli([
            "eval",
            "--session-id",
            session.id,
            "--expression",
            `(() => {
              const rows = Array.from(document.querySelectorAll("button"))
                .filter((el) => (el.textContent || "").trim() === "Cancel")
                .map((button) => button.closest("div.grid"))
                .filter(Boolean);
              const coins = rows
                .map((row) => {
                  const cells = Array.from(row.children);
                  return (cells[2]?.innerText || "").trim();
                })
                .filter(Boolean);
              return {
                visibleCoinCount: coins.length,
                visibleAtCoins: coins.filter((text) => /^@\\d+$/.test(text)),
                sampleCoins: coins.slice(0, 20)
              };
            })()`
          ]);
          return result.result;
        },
        (result) =>
          result.visibleCoinCount > 0 &&
          Array.isArray(result.visibleAtCoins) &&
          result.visibleAtCoins.length === 0,
        { timeoutMs: 90000, intervalMs: 1000 }
      );

      assert.ok(probe.visibleCoinCount > 0);
      assert.deepEqual(probe.visibleAtCoins, []);

      const designReview = await runCliAllowFailure([
        "design-review",
        "--session-id",
        session.id,
        "--targets",
        "trade-route",
        "--viewports",
        "review-375"
      ]);

      assert.ok(designReview.runDir);
      assert.equal(designReview.passes.length, 6);
      await fs.access(path.join(designReview.runDir, "review-spec.json"));
      await fs.access(path.join(designReview.runDir, "summary.json"));
    } finally {
      await runCli(["session", "stop", "--session-id", session.id]).catch(() => null);
    }
  }
);
