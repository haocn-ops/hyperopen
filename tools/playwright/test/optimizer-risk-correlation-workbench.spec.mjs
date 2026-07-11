// Equal Risk correlation view (designer spec 2026-07-11). The workbench
// scenes render the REAL risk card + Allocation table with a scene-local
// dispatcher, so this covers the browser-only behavior deterministically
// without solving an optimization in-app: DOM-state tab switching, the
// POSITION P&L / UNDERLYING RETURNS matrix toggle (sign flips included),
// click-to-select from allocation rows re-targeting the contribution
// breakdown, the 12-asset heatmap cap, and degenerate-variance em-dashes.
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

  test("clicking an allocation row re-targets the contribution breakdown and the highlight", async ({
    page
  }) => {
    const frame = await openScene(page, "correlation-designer-parity");
    await frame
      .locator(role("portfolio-optimizer-risk-view-tab-correlation"))
      .click();

    const selected = frame.locator(
      role("portfolio-optimizer-risk-selected-asset")
    );
    const mstrRow = frame.locator(
      role("portfolio-optimizer-target-exposure-asset-MSTR")
    );
    const btcRow = frame.locator(
      role("portfolio-optimizer-target-exposure-asset-BTC")
    );

    // The scene store preselects MSTR — the short whose decomposition the
    // designer's spec explains.
    await expect(selected).toHaveText("MSTR Short");
    await expect(mstrRow).toHaveAttribute("data-selected", "true");
    await expect(
      frame.locator(role("portfolio-optimizer-risk-selected-identity"))
    ).toContainText("Net contribution = standalone risk + diversification effect");

    await btcRow.click();
    await expect(selected).toHaveText("BTC Long");
    await expect(btcRow).toHaveAttribute("data-selected", "true");
    await expect(mstrRow).not.toHaveAttribute("data-selected", "true");

    // The per-row P&L-correlation line rides every held row.
    await expect(
      frame.locator(
        role("portfolio-optimizer-target-exposure-pnl-corr-perp-MSTR")
      )
    ).toContainText("+0.");
  });

  test("a 16-asset book caps the heatmap at 12 and says how many it dropped", async ({
    page
  }) => {
    const frame = await openScene(page, "correlation-capped-sixteen-assets");
    await frame
      .locator(role("portfolio-optimizer-risk-view-tab-correlation"))
      .click();
    const positionGrid = frame.locator(
      role("portfolio-optimizer-risk-corr-grid-position")
    );
    await expect(positionGrid.locator("[data-diagonal='true']")).toHaveCount(12);
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
