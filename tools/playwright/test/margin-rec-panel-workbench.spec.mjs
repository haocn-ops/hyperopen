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
  test("elevated-risk scene renders the wide card end to end", async ({
    page
  }) => {
    const frame = await openScene(page, "elevated-risk-recommendation");
    const panel = frame.locator(role("margin-rec-panel"));

    await expect(panel).toContainText("Margin recommendation");
    await expect(panel.locator(role("margin-rec-coin"))).toHaveText("TSM");
    await expect(panel.locator(role("margin-rec-leverage"))).toHaveText(
      "10x isolated"
    );

    // Recommendation headline + the amount-to-add line (which now carries the
    // current margin instead of a duplicate stat cell).
    await expect(panel.locator(role("margin-rec-recommended"))).toContainText(
      "$18.64 USDC"
    );
    await expect(panel.locator(role("margin-rec-recommended"))).toContainText(
      "+50.1% vs current"
    );
    await expect(panel.locator(role("margin-rec-additional"))).toContainText(
      "Add $6.22 USDC to your current $12.42"
    );

    // One before/after probability line replaces the two separate cells.
    await expect(panel.locator(role("margin-rec-risk-delta"))).toContainText(
      "14.6%"
    );
    await expect(panel.locator(role("margin-rec-risk-delta"))).toContainText(
      "2.1%"
    );
    await expect(panel.locator(role("margin-rec-new-liq"))).toContainText(
      "$403.10"
    );

    // The old duplicated stat cells are gone.
    await expect(
      panel.locator(role("margin-rec-current-stats"))
    ).toHaveCount(0);
    await expect(panel.locator(role("margin-rec-stat-p-now"))).toHaveCount(0);

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

  test("panel is wider than it is tall so it covers the chart, not the whole UI", async ({
    page
  }) => {
    const frame = await openScene(page, "elevated-risk-recommendation");
    const box = await frame.locator(role("margin-rec-panel")).boundingBox();
    expect(box.width).toBeGreaterThan(700);
    // Half-ish the old ~960px tall stack; wider than tall.
    expect(box.height).toBeLessThan(box.width);
    expect(box.height).toBeLessThan(680);
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

  test("curve is stroked with an amber-to-green gradient across the marker band", async ({
    page
  }) => {
    const frame = await openScene(page, "elevated-risk-recommendation");
    const polyline = frame.locator(role("margin-rec-curve") + " polyline");
    await expect(polyline).toHaveAttribute(
      "stroke",
      "url(#margin-rec-curve-gradient)"
    );

    const stops = frame.locator("#margin-rec-curve-gradient stop");
    await expect(stops).toHaveCount(4);

    // Amber holds to the current marker, green from the recommended marker on,
    // so the interior two stops carry the blend and stay ordered left→right.
    const offsets = await stops.evaluateAll((els) =>
      els.map((el) => parseFloat(el.getAttribute("offset")))
    );
    expect(offsets[0]).toBe(0);
    expect(offsets[3]).toBe(100);
    expect(offsets[1]).toBeGreaterThan(offsets[0]);
    expect(offsets[2]).toBeGreaterThan(offsets[1]);
    expect(offsets[3]).toBeGreaterThan(offsets[2]);
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

    await expect(panel.locator(role("margin-rec-summary"))).toBeVisible();
    await expect(panel.locator(role("margin-rec-curve-card"))).toHaveCount(0);
    await expect(panel.locator(role("margin-rec-buffers"))).toBeVisible();
  });
});
