// Equal Risk correlation + per-asset breakdown views (designer specs
// 2026-07-11). The workbench scenes render the REAL risk card + Allocation
// table with a scene-local dispatcher, so this covers the browser-only
// behavior deterministically without solving an optimization in-app:
// DOM-state tab switching, the POSITION P&L / UNDERLYING RETURNS matrix
// toggle (sign flips included), the BREAKDOWN tab's per-asset default with
// its Selected asset / All assets sub-toggle, selection flowing between
// allocation-row clicks and the Change-asset select, the full-width
// correlation heatmap, the 12-asset heatmap cap, and degenerate-variance
// em-dashes.
import { expect, test } from "@playwright/test";

const SCENE_BASE =
  "/ui-workbench.html?id=hyperopen.workbench.scenes.optimize.equal-risk-correlation-scenes";

function role(name) {
  return `[data-role='${name}']`;
}

async function openScene(page, scene) {
  await page.goto(`${SCENE_BASE}/${scene}`);
  const frame = page.frameLocator("iframe").first();
  await expect(
    frame.locator(role("portfolio-optimizer-risk-contributions"))
  ).toBeVisible({ timeout: 30_000 });
  return frame;
}

function corrCell(grid, rowId, colId) {
  return grid.locator(
    `${role("portfolio-optimizer-risk-corr-cell")}[data-row='${rowId}'][data-col='${colId}']`
  );
}

test.describe("equal risk correlation view (workbench scenes)", () => {
  test("tabs and the P&L/underlying toggle switch via DOM state, flipping long × short signs", async ({
    page
  }) => {
    const frame = await openScene(page, "correlation-designer-parity");
    const contribution = frame.locator(
      role("portfolio-optimizer-risk-contribution-chart")
    );
    const heatmap = frame.locator(
      role("portfolio-optimizer-risk-correlation-heatmap")
    );
    await expect(contribution).toBeVisible();
    await expect(heatmap).toBeHidden();

    await frame
      .locator(role("portfolio-optimizer-risk-view-tab-correlation"))
      .click();
    await expect(heatmap).toBeVisible();
    await expect(contribution).toBeHidden();

    const positionGrid = frame.locator(
      role("portfolio-optimizer-risk-corr-grid-position")
    );
    const underlyingGrid = frame.locator(
      role("portfolio-optimizer-risk-corr-grid-underlying")
    );
    await expect(positionGrid).toBeVisible();
    await expect(underlyingGrid).toBeHidden();
    // BTC long × MSTR short: the held-position P&L correlation flips the
    // underlying +0.28 negative.
    await expect(corrCell(positionGrid, "perp:BTC", "perp:MSTR")).toHaveText(
      "-0.28"
    );

    await frame
      .locator(role("portfolio-optimizer-risk-corr-mode-underlying"))
      .click();
    await expect(underlyingGrid).toBeVisible();
    await expect(positionGrid).toBeHidden();
    await expect(corrCell(underlyingGrid, "perp:BTC", "perp:MSTR")).toHaveText(
      "0.28"
    );

    await frame
      .locator(role("portfolio-optimizer-risk-corr-mode-position"))
      .click();
    await expect(positionGrid).toBeVisible();
    await expect(underlyingGrid).toBeHidden();
  });

  test("the BREAKDOWN tab defaults to the per-asset panel; row clicks and the Change-asset select share one selection", async ({
    page
  }) => {
    const frame = await openScene(page, "correlation-designer-parity");
    await frame
      .locator(role("portfolio-optimizer-risk-view-tab-breakdown"))
      .click();

    const assetPanel = frame.locator(
      role("portfolio-optimizer-risk-selected-breakdown")
    );
    const selected = frame.locator(
      role("portfolio-optimizer-risk-selected-asset")
    );
    const picker = frame.locator(
      role("portfolio-optimizer-risk-asset-select")
    );
    const mstrRow = frame.locator(
      role("portfolio-optimizer-target-exposure-asset-MSTR")
    );
    const btcRow = frame.locator(
      role("portfolio-optimizer-target-exposure-asset-BTC")
    );

    // The scene store preselects MSTR — the short whose decomposition the
    // designer's spec explains. The per-asset view is the default sub-view,
    // and the select must sync to the selection on first render.
    await expect(assetPanel).toBeVisible();
    await expect(selected).toHaveText("MSTR");
    await expect(picker).toHaveValue("perp:MSTR");
    await expect(mstrRow).toHaveAttribute("data-selected", "true");
    await expect(
      frame.locator(role("portfolio-optimizer-risk-selected-identity"))
    ).toContainText("Net Risk Contribution");
    await expect(
      frame.locator(role("portfolio-optimizer-risk-asset-tile-freedom"))
    ).toContainText("Limited · 2 binding caps");

    // Allocation-row click re-targets the panel AND the select's value.
    await btcRow.click();
    await expect(selected).toHaveText("BTC");
    await expect(btcRow).toHaveAttribute("data-selected", "true");
    await expect(mstrRow).not.toHaveAttribute("data-selected", "true");
    await expect(picker).toHaveValue("perp:BTC");

    // The Change-asset select drives the same app state back the other way.
    await picker.selectOption("perp:ETH");
    await expect(selected).toHaveText("ETH");
    await expect(
      frame.locator(role("portfolio-optimizer-target-exposure-asset-ETH"))
    ).toHaveAttribute("data-selected", "true");
    await expect(btcRow).not.toHaveAttribute("data-selected", "true");

    // The per-row P&L-correlation line rides every held row.
    await expect(
      frame.locator(
        role("portfolio-optimizer-target-exposure-pnl-corr-perp-MSTR")
      )
    ).toContainText("+0.");
  });

  test("the breakdown sub-toggle swaps per-asset and all-assets views without touching app state", async ({
    page
  }) => {
    const frame = await openScene(page, "correlation-designer-parity");
    await frame
      .locator(role("portfolio-optimizer-risk-view-tab-breakdown"))
      .click();

    const assetPanel = frame.locator(
      role("portfolio-optimizer-risk-selected-breakdown")
    );
    const allChart = frame.locator(
      role("portfolio-optimizer-risk-breakdown-chart")
    );
    await expect(assetPanel).toBeVisible();
    await expect(allChart).toBeHidden();

    await frame
      .locator(role("portfolio-optimizer-risk-breakdown-view-all"))
      .click();
    await expect(allChart).toBeVisible();
    await expect(assetPanel).toBeHidden();

    await frame
      .locator(role("portfolio-optimizer-risk-breakdown-view-asset"))
      .click();
    await expect(assetPanel).toBeVisible();
    await expect(allChart).toBeHidden();
  });

  test("the correlation tab is the heatmap alone at full card width", async ({
    page
  }) => {
    const frame = await openScene(page, "correlation-designer-parity");
    await frame
      .locator(role("portfolio-optimizer-risk-view-tab-correlation"))
      .click();

    const heatmap = frame.locator(
      role("portfolio-optimizer-risk-correlation-heatmap")
    );
    await expect(heatmap).toBeVisible();
    // The old in-tab breakdown block now lives on the BREAKDOWN tab, so it
    // must be hidden while the correlation tab is active.
    await expect(
      frame.locator(role("portfolio-optimizer-risk-selected-breakdown"))
    ).toBeHidden();

    const card = frame.locator(
      role("portfolio-optimizer-risk-contributions")
    );
    const cardBox = await card.boundingBox();
    const heatmapBox = await heatmap.boundingBox();
    expect(heatmapBox.width).toBeGreaterThan(cardBox.width * 0.9);
  });

  test("a 28-asset book caps the heatmap at 24 and says how many it dropped", async ({
    page
  }) => {
    const frame = await openScene(page, "correlation-capped-twenty-eight-assets");
    await frame
      .locator(role("portfolio-optimizer-risk-view-tab-correlation"))
      .click();
    const positionGrid = frame.locator(
      role("portfolio-optimizer-risk-corr-grid-position")
    );
    await expect(positionGrid.locator("[data-diagonal='true']")).toHaveCount(24);
    await expect(
      frame.locator(role("portfolio-optimizer-risk-corr-overflow"))
    ).toContainText("+ 4 more held assets not shown");
  });

  test("a zero-variance asset renders em-dash correlations, never numbers", async ({
    page
  }) => {
    const frame = await openScene(page, "correlation-degenerate-column");
    await frame
      .locator(role("portfolio-optimizer-risk-view-tab-correlation"))
      .click();
    const positionGrid = frame.locator(
      role("portfolio-optimizer-risk-corr-grid-position")
    );
    await expect(
      positionGrid.locator("[data-sign='missing']").first()
    ).toHaveText("—");
    // The healthy pair keeps its number.
    await expect(corrCell(positionGrid, "perp:BTC", "perp:ETH")).toHaveText(
      "0.72"
    );
  });
});
