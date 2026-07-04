import { expect, test } from "@playwright/test";
import { visitRoute, waitForIdle } from "../support/hyperopen.mjs";
import {
  keyword,
  optimizerPath,
  readOptimizerState,
  seedOptimizerState,
  seedPatch
} from "../support/optimizer_state.mjs";

const PANEL = "[data-role='portfolio-optimizer-constraints-panel']";

async function expectPortfolioExposureOpen(page) {
  const panel = page.locator(PANEL);
  await expect(panel).toHaveCount(1);
  await expect.poll(async () => panel.evaluate((el) => el.open)).toBe(true);
  await expect(panel.locator("> summary")).toContainText("Portfolio exposure");
  await expect(panel).toContainText(
    "Set how levered and net long/short the target portfolio can be."
  );
}

test.describe("optimizer exposure-map Positioning control", () => {
  test.beforeEach(async ({ page }) => {
    await visitRoute(page, "/portfolio/optimize/new");
    await expectPortfolioExposureOpen(page);
  });

  test("renders the 2D pad, echo, presets, and profile row", async ({ page }) => {
    await expect(page.locator("[data-role='portfolio-optimizer-exposure-map']")).toBeVisible();
    await expect(page.locator("[data-role='portfolio-optimizer-exposure-pad']")).toBeVisible();
    await expect(page.locator("[data-role='portfolio-optimizer-exposure-handle']")).toBeVisible();
    await expect(page.locator("[data-role='portfolio-optimizer-exposure-echo']")).toContainText(
      "Sent to solver"
    );
    await expect(page.locator("[data-role='portfolio-optimizer-exposure-caption']"))
      .toContainText("The dot shows the target gross leverage and net long/short bias.");
    await expect(
      page.locator("[data-role='portfolio-optimizer-exposure-gross-band']")
    ).toBeVisible();
    await expect(
      page.locator("[data-role='portfolio-optimizer-exposure-profile']")
    ).toBeVisible();
    // A fresh draft is the Balanced preset; its echo caps gross at 2x with no floor.
    await expect(page.locator("[data-role='portfolio-optimizer-exposure-echo']")).toContainText(
      "gross ≤ 2.00×"
    );
  });

  test("the pad is bounded and the Run bar stays visible with the panel open", async ({
    page
  }) => {
    // The open Portfolio exposure panel must not consume more than a screen: the pad renders in a
    // bounded column (~21rem) instead of spanning the full center pane.
    const pad = page.locator("[data-role='portfolio-optimizer-exposure-pad']");
    await expect(pad).toBeVisible();
    const box = await pad.boundingBox();
    expect(box.height).toBeLessThanOrEqual(360);
    expect(box.width).toBeLessThanOrEqual(360);
    // The sticky Run bar is pinned inside the viewport even while the tall panel is expanded.
    await expect(
      page.locator("[data-role='portfolio-optimizer-setup-bottom-actions']")
    ).toBeInViewport();
    // The live readout mirrors the Balanced targets in large type next to the pad.
    await expect(
      page.locator("[data-role='portfolio-optimizer-exposure-readout-gross']")
    ).toHaveText("2.00×");
    await expect(
      page.locator("[data-role='portfolio-optimizer-exposure-readout-net']")
    ).toHaveText("+1.00×");
  });

  test("the zoom control steps the fixed axis scale and dragging never grows it", async ({
    page
  }) => {
    const yMax = page.locator("[data-role='portfolio-optimizer-exposure-y-max']");
    const zoomOut = page.locator("[data-role='portfolio-optimizer-exposure-zoom-out']");
    const zoomIn = page.locator("[data-role='portfolio-optimizer-exposure-zoom-in']");
    // The Balanced policy fits the floor level: 0–3x, zoom-in disabled.
    await expect(yMax).toHaveText("3×");
    await expect(zoomIn).toBeDisabled();
    await zoomOut.click();
    await waitForIdle(page);
    await expect(yMax).toHaveText("5×");
    await expect(zoomIn).toBeEnabled();
    // Zooming back in returns to the smallest level that frames the policy.
    await zoomIn.click();
    await waitForIdle(page);
    await expect(yMax).toHaveText("3×");
    await zoomOut.click();
    await waitForIdle(page);
    await expect(yMax).toHaveText("5×");
    // Dragging to the pad's very top edge clamps to the visible scale instead of re-scaling
    // (the old adaptive axis ratcheted upward while the pointer was held at the edge).
    // The bounding box is re-captured before each drag: prior clicks/renders may scroll the
    // page, and a drag against stale viewport coordinates silently misses the pad.
    const pad = page.locator("[data-role='portfolio-optimizer-exposure-pad']");
    await pad.scrollIntoViewIfNeeded();
    const box = await pad.boundingBox();
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width / 2, box.y + 1, { steps: 5 });
    await page.mouse.move(box.x + box.width / 2, box.y + 1, { steps: 3 });
    await page.mouse.up();
    await waitForIdle(page);
    await expect(yMax).toHaveText("5×");
    expect(
      await readOptimizerState(page, optimizerPath("draft", "constraints", "gross-max"))
    ).toBeLessThanOrEqual(5);
    // The dragged policy now needs the 5x level itself, so zooming further in is disabled
    // (zooming in may never clip the band box).
    await expect(zoomIn).toBeDisabled();
    // Dragging back DOWN re-fits the policy smaller, but the scale stays pinned at the level
    // the drag was performed at — the pad never shrinks under the pointer. Zooming back in
    // re-arms instead.
    await pad.scrollIntoViewIfNeeded();
    const box2 = await pad.boundingBox();
    await page.mouse.move(box2.x + box2.width / 2, box2.y + box2.height / 2);
    await page.mouse.down();
    await page.mouse.move(box2.x + box2.width / 2, box2.y + box2.height * 0.6, { steps: 5 });
    await page.mouse.up();
    await waitForIdle(page);
    await expect(yMax).toHaveText("5×");
    await expect(zoomIn).toBeEnabled();
  });

  test("clicking a preset round-trips through the runtime into the draft constraints", async ({
    page
  }) => {
    await expect(
      page.locator("[data-role='portfolio-optimizer-exposure-preset-balanced']")
    ).toHaveAttribute("aria-pressed", "true");

    await page.locator("[data-role='portfolio-optimizer-exposure-preset-conservative']").click();
    await waitForIdle(page);

    expect(await readOptimizerState(page, optimizerPath("draft", "constraints", "gross-max"))).toBe(
      1
    );
    expect(
      await readOptimizerState(page, optimizerPath("draft", "constraints", "max-asset-weight"))
    ).toBe(0.25);
    // No gross floor for a ceiling-only preset.
    expect(
      await readOptimizerState(page, optimizerPath("draft", "constraints", "gross-min"))
    ).toBeNull();

    await expect(
      page.locator("[data-role='portfolio-optimizer-exposure-preset-conservative']")
    ).toHaveAttribute("aria-pressed", "true");
    await expect(page.locator("[data-role='portfolio-optimizer-exposure-echo']")).toContainText(
      "gross ≤ 1.00×"
    );
  });

  test("widening the net band updates the net range sent to the solver", async ({ page }) => {
    const slider = page.locator("[data-role='portfolio-optimizer-exposure-net-band']");
    await slider.evaluate((el) => {
      el.value = "0.25";
      el.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await waitForIdle(page);

    expect(await readOptimizerState(page, optimizerPath("draft", "constraints", "net-min"))).toBe(
      0.75
    );
    expect(await readOptimizerState(page, optimizerPath("draft", "constraints", "net-max"))).toBe(
      1.25
    );
  });

  test("the Advanced drawer exposes the raw gross/net min/max fields", async ({ page }) => {
    const advanced = page.locator("[data-role='portfolio-optimizer-constraints-advanced']");
    await expect(advanced).toHaveCount(1);
    await advanced.evaluate((el) => {
      el.open = true;
    });
    await expect(
      page.locator("[data-role='portfolio-optimizer-constraint-gross-max-input']")
    ).toBeVisible();
    await expect(
      page.locator("[data-role='portfolio-optimizer-constraint-net-min-input']")
    ).toBeVisible();
  });
});

test.describe("optimizer exposure-map drag under Maximum Sharpe", () => {
  test("dragging the pad never recomputes the risk model per pointermove", async ({ page }) => {
    // Regression: under Maximum Sharpe the Return-views rail (and BL preview)
    // read the baseline expected-return inputs on every render, and each read
    // estimated the full covariance matrix from history. A pad drag renders
    // once per pointermove, so the drag recomputed the risk model dozens of
    // times per second and janked. The inputs are memoized on the request
    // sub-values they consume, so a constraints-only change must not call
    // risk/estimate-risk-model at all.
    await visitRoute(page, "/portfolio/optimize/new");
    await seedOptimizerState(page, [
      seedPatch(optimizerPath("draft", "universe"), [
        { "instrument-id": "perp:BTC", "market-type": keyword("perp"), coin: "BTC" },
        { "instrument-id": "perp:ETH", "market-type": keyword("perp"), coin: "ETH" },
        { "instrument-id": "perp:SOL", "market-type": keyword("perp"), coin: "SOL" },
        { "instrument-id": "perp:HYPE", "market-type": keyword("perp"), coin: "HYPE" }
      ]),
      seedPatch(optimizerPath("draft", "objective"), { kind: keyword("max-sharpe") }),
      seedPatch(optimizerPath("draft", "return-model"), {
        kind: keyword("black-litterman"),
        views: []
      })
    ]);
    await waitForIdle(page);
    // The Maximum-Sharpe-only surface that triggered the per-frame recompute.
    await expect(
      page.locator("[data-role='portfolio-optimizer-setup-use-my-views-editor']")
    ).toHaveCount(1);

    const pad = page.locator("[data-role='portfolio-optimizer-exposure-pad']");
    await pad.scrollIntoViewIfNeeded();
    const box = await pad.boundingBox();

    // Count risk-model estimations from here on (dev builds expose namespaces).
    await page.evaluate(() => {
      const risk = globalThis.hyperopen.portfolio.optimizer.domain.risk;
      const original = risk.estimate_risk_model;
      globalThis.__optimizerRiskModelCalls = 0;
      risk.estimate_risk_model = function (...args) {
        globalThis.__optimizerRiskModelCalls += 1;
        return original.apply(this, args);
      };
    });

    const grossBefore = await readOptimizerState(
      page,
      optimizerPath("draft", "constraints", "gross-max")
    );
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width * 0.7, box.y + box.height * 0.3, { steps: 12 });
    await page.mouse.move(box.x + box.width * 0.4, box.y + box.height * 0.55, { steps: 12 });
    await page.mouse.up();
    const riskModelCalls = await page.evaluate(() => globalThis.__optimizerRiskModelCalls);
    await waitForIdle(page);

    // The drag must have actually re-rendered the page per move…
    expect(
      await readOptimizerState(page, optimizerPath("draft", "constraints", "gross-max"))
    ).not.toBe(grossBefore);
    // …without re-estimating the covariance matrix. A small allowance covers an
    // async history-load transition landing mid-drag (a legitimate input
    // change); the unmemoized bug produced one-plus call per pointermove.
    expect(riskModelCalls).toBeLessThanOrEqual(2);
  });
});
