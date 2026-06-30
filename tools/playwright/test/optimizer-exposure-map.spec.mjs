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
