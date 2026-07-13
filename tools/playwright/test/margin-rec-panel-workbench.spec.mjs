// Margin recommendation panel, designer-spec redesign (2026-07-12). The
// workbench scenes render the REAL anchored popover over explicit result
// fixtures, so this pins the browser-only layout deterministically: the
// current-state stat grid, the green recommendation block, the modeled
// probability-vs-collateral SVG (current marker left of the recommended
// marker, both dots sitting on the curve's x positions), the methods/buffers
// columns, and the Apply recommendation / Set custom margin actions.
import { expect, test } from "@playwright/test";

const SCENE_BASE =
  "/ui-workbench.html?id=hyperopen.workbench.scenes.account.margin-recommendation-scenes";

function role(name) {
  return `[data-role='${name}']`;
}

async function openScene(page, scene) {
  await page.goto(`${SCENE_BASE}/${scene}`);
  const frame = page.frameLocator("iframe").first();
  await expect(frame.locator(role("margin-rec-panel"))).toBeVisible({
    timeout: 30_000
  });
  return frame;
}

test.describe("margin recommendation panel (workbench scenes)", () => {
  test("elevated-risk scene renders the designer card end to end", async ({
    page
  }) => {
    const frame = await openScene(page, "elevated-risk-recommendation");
    const panel = frame.locator(role("margin-rec-panel"));

    await expect(panel).toContainText("Margin recommendation");
    await expect(panel.locator(role("margin-rec-coin"))).toHaveText("TSM");
    await expect(panel.locator(role("margin-rec-leverage"))).toHaveText(
      "10x isolated"
    );

    await expect(panel.locator(role("margin-rec-stat-current"))).toContainText(
      "$12.42 USDC"
    );
    await expect(panel.locator(role("margin-rec-stat-p-now"))).toContainText(
      "14.6%"
    );
    await expect(panel.locator(role("margin-rec-recommended"))).toContainText(
      "$18.64 USDC"
    );
    await expect(panel.locator(role("margin-rec-recommended"))).toContainText(
      "+50.1% vs current"
    );
    await expect(panel.locator(role("margin-rec-additional"))).toContainText(
      "$6.22 USDC"
    );

    await expect(panel.locator(role("margin-rec-buffers"))).toContainText(
      "Adverse-path protection"
    );
    await expect(panel.locator(role("margin-rec-buffers"))).toContainText(
      "$7.87 (42%)"
    );
    await expect(panel.locator(role("margin-rec-methods"))).toContainText(
      "Scenario simulation (4,000 paths)"
    );

    await expect(panel.locator(role("margin-rec-apply"))).toHaveText(
      "Apply recommendation"
    );
    await expect(panel.locator(role("margin-rec-custom"))).toHaveText(
      "Set custom margin"
    );
  });

  test("curve renders with the current marker left of the recommended marker", async ({
    page
  }) => {
    const frame = await openScene(page, "elevated-risk-recommendation");
    const svg = frame.locator(role("margin-rec-curve"));
    await expect(svg).toBeVisible();

    const currentDot = svg.locator(
      `${role("margin-rec-curve-current")} circle`
    );
    const recommendedDot = svg.locator(
      `${role("margin-rec-curve-recommended")} circle`
    );
    await expect(currentDot).toBeVisible();
    await expect(recommendedDot).toBeVisible();

    const currentCx = Number(await currentDot.getAttribute("cx"));
    const recommendedCx = Number(await recommendedDot.getAttribute("cx"));
    expect(currentCx).toBeGreaterThan(0);
    expect(recommendedCx).toBeGreaterThan(currentCx);

    await expect(svg).toContainText("Isolated margin (USDC)");
    await expect(
      svg.locator(role("margin-rec-curve-current"))
    ).toContainText("Current");
    await expect(
      svg.locator(role("margin-rec-curve-recommended"))
    ).toContainText("Recommended");
  });

  test("within-target scene hides apply but keeps custom margin", async ({
    page
  }) => {
    const frame = await openScene(page, "within-target");
    const panel = frame.locator(role("margin-rec-panel"));

    await expect(panel.locator(role("margin-rec-within-target"))).toBeVisible();
    await expect(panel.locator(role("margin-rec-apply"))).toHaveCount(0);
    await expect(panel.locator(role("margin-rec-custom"))).toBeVisible();
  });

  test("cached results without a curve omit the chart card only", async ({
    page
  }) => {
    const frame = await openScene(page, "cached-result-without-curve");
    const panel = frame.locator(role("margin-rec-panel"));

    await expect(panel.locator(role("margin-rec-recommendation"))).toBeVisible();
    await expect(panel.locator(role("margin-rec-curve-card"))).toHaveCount(0);
    await expect(panel.locator(role("margin-rec-buffers"))).toBeVisible();
  });
});
