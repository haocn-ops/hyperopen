import { expect, test } from "@playwright/test";
import { visitRoute, waitForIdle } from "../support/hyperopen.mjs";
import { optimizerPath, readOptimizerState } from "../support/optimizer_state.mjs";

const PANEL = "[data-role='portfolio-optimizer-constraints-panel']";

async function openConstraintsPanel(page) {
  const panel = page.locator(PANEL);
  await expect(panel).toHaveCount(1);
  // The panel is a native <details>; open it directly so the assertions don't depend on a
  // click toggling the right one among several disclosure panels.
  await panel.evaluate((el) => {
    el.open = true;
  });
}

test.describe("optimizer exposure-map Positioning control", () => {
  test.beforeEach(async ({ page }) => {
    await visitRoute(page, "/portfolio/optimize/new");
    await openConstraintsPanel(page);
  });

  test("renders the 2D pad, echo, presets, and profile row", async ({ page }) => {
    await expect(page.locator("[data-role='portfolio-optimizer-exposure-map']")).toBeVisible();
    await expect(page.locator("[data-role='portfolio-optimizer-exposure-pad']")).toBeVisible();
    await expect(page.locator("[data-role='portfolio-optimizer-exposure-handle']")).toBeVisible();
    await expect(page.locator("[data-role='portfolio-optimizer-exposure-echo']")).toContainText(
      "Sent to solver"
    );
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
    // The open Constraints panel must not consume more than a screen: the pad renders in a
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
