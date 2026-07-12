// Volatility intuition + leverage risk (designer spec 2026-07-12, trimmed per
// the direct user request). The workbench scenes render the REAL rail cards
// and insight strip over explicit result fixtures, so this covers the
// browser-only behavior deterministically without solving an optimization:
// the 365-calendar-day sqrt-time scaling of the displayed values, the
// DOM-radio Target/Current toggle, severity + (-100%)-boundary messaging with
// the monthly value uncapped, the insight strip's very-high gate, the
// leverage-risk card's gross/volatility gates, its modeled dollar rows with
// the vs-current shortfall, and the multiples fallback when account equity is
// unknown.
import { expect, test } from "@playwright/test";

const SCENE_BASE =
  "/ui-workbench.html?id=hyperopen.workbench.scenes.optimize.volatility-intuition-scenes";

function role(name) {
  return `[data-role='${name}']`;
}

async function openScene(page, scene) {
  await page.goto(`${SCENE_BASE}/${scene}`);
  const frame = page.frameLocator("iframe").first();
  await expect(
    frame.locator(role("portfolio-optimizer-volatility-intuition"))
  ).toBeVisible({ timeout: 30_000 });
  return frame;
}

test.describe("volatility intuition (workbench scenes)", () => {
  test("an extreme levered book shows uncapped 365-day horizon scales with the warning stack", async ({
    page
  }) => {
    const frame = await openScene(page, "extreme-levered-book");
    const targetPanel = frame.locator(
      role("portfolio-optimizer-volatility-intuition-panel-target")
    );

    // 411.82% annualized on the optimizer's 365-calendar-day basis.
    await expect(
      targetPanel.locator(
        role("portfolio-optimizer-volatility-intuition-target-annualized")
      )
    ).toHaveText("411.8%");
    await expect(
      targetPanel.locator(
        role("portfolio-optimizer-volatility-intuition-target-daily")
      )
    ).toContainText("±21.56%");
    await expect(
      targetPanel.locator(
        role("portfolio-optimizer-volatility-intuition-target-weekly")
      )
    ).toContainText("±57.03%");
    // Above 100% the monthly value renders uncapped...
    await expect(
      targetPanel.locator(
        role("portfolio-optimizer-volatility-intuition-target-monthly")
      )
    ).toContainText("±118.1%");
    // ...with the extreme callout and the boundary explanation.
    await expect(
      targetPanel.locator(
        role("portfolio-optimizer-volatility-intuition-target-severity")
      )
    ).toContainText("Extreme volatility");
    await expect(
      targetPanel.locator(
        role("portfolio-optimizer-volatility-intuition-target-boundary")
      )
    ).toContainText("dispersion measure");
    // The scaling convention and the 1σ meaning are always-visible copy.
    await expect(
      frame.locator(role("portfolio-optimizer-volatility-intuition-basis"))
    ).toContainText("365 calendar days");
    await expect(
      frame.locator(role("portfolio-optimizer-volatility-intuition"))
    ).toContainText("not a forecast or maximum loss");
    // The under-chart insight strip fires at very-high/extreme σ.
    await expect(
      frame.locator(role("portfolio-optimizer-volatility-insight"))
    ).toContainText("a typical 1σ day is about ±21.56%");
  });

  test("the Target/Current toggle is pure DOM state", async ({ page }) => {
    const frame = await openScene(page, "extreme-levered-book");
    const targetPanel = frame.locator(
      role("portfolio-optimizer-volatility-intuition-panel-target")
    );
    const currentPanel = frame.locator(
      role("portfolio-optimizer-volatility-intuition-panel-current")
    );

    // Target is the recommendation default (no radio checked yet).
    await expect(targetPanel).toBeVisible();
    await expect(currentPanel).toBeHidden();

    await frame
      .locator(role("portfolio-optimizer-volatility-intuition-tab-current"))
      .click();
    await expect(currentPanel).toBeVisible();
    await expect(targetPanel).toBeHidden();
    await expect(
      currentPanel.locator(
        role("portfolio-optimizer-volatility-intuition-current-annualized")
      )
    ).toHaveText("313.9%");
    await expect(
      currentPanel.locator(
        role("portfolio-optimizer-volatility-intuition-current-daily")
      )
    ).toContainText("±16.43%");

    await frame
      .locator(role("portfolio-optimizer-volatility-intuition-tab-target"))
      .click();
    await expect(targetPanel).toBeVisible();
    await expect(currentPanel).toBeHidden();
  });

  test("the leverage-risk card models dollar outcomes honestly for the levered book", async ({
    page
  }) => {
    const frame = await openScene(page, "extreme-levered-book");
    const card = frame.locator(role("portfolio-optimizer-leverage-risk"));

    await expect(card).toBeVisible();
    await expect(card).toContainText("1y · modeled");
    // Median ending equity: the volatility drag makes the target median
    // collapse even though its arithmetic mean is +1866%.
    await expect(
      card.locator(role("portfolio-optimizer-leverage-risk-median-current"))
    ).toContainText("$18,356");
    await expect(
      card.locator(role("portfolio-optimizer-leverage-risk-median-target"))
    ).toContainText("$408");
    await expect(
      card.locator(role("portfolio-optimizer-leverage-risk-median-shortfall"))
    ).toHaveText("Median vs current: −$17,948");
    await expect(card).toContainText("On account equity $100,000");
    await expect(
      card.locator(role("portfolio-optimizer-leverage-risk-terminal"))
    ).toContainText("87.8%");
    // Never a liquidation probability — the drawdown odds are a floor.
    await expect(
      card.locator(role("portfolio-optimizer-leverage-risk-touch"))
    ).toContainText("98.2%");
    await expect(
      card.locator(role("portfolio-optimizer-leverage-risk-touch"))
    ).toContainText("floor on ruin risk");
    await expect(card).toContainText("Modeled, not a guarantee");
  });

  test("a moderate book stays quiet: designer's 40% vector, no warnings, no leverage card", async ({
    page
  }) => {
    const frame = await openScene(page, "moderate-book");
    const targetPanel = frame.locator(
      role("portfolio-optimizer-volatility-intuition-panel-target")
    );

    // 40% annualized / 365 days: the spec's worked example.
    await expect(targetPanel).toContainText("±2.09%");
    await expect(targetPanel).toContainText("±5.54%");
    await expect(targetPanel).toContainText("±11.47%");
    await expect(
      frame.locator(
        role("portfolio-optimizer-volatility-intuition-target-severity")
      )
    ).toHaveCount(0);
    await expect(
      frame.locator(
        role("portfolio-optimizer-volatility-intuition-target-boundary")
      )
    ).toHaveCount(0);
    await expect(
      frame.locator(role("portfolio-optimizer-volatility-insight"))
    ).toHaveCount(0);
    await expect(
      frame.locator(role("portfolio-optimizer-leverage-risk"))
    ).toHaveCount(0);
  });

  test("without a current book or account equity the card degrades honestly", async ({
    page
  }) => {
    const frame = await openScene(page, "very-high-vol-no-current");

    // No current volatility → no toggle, no current panel.
    await expect(
      frame.locator(role("portfolio-optimizer-volatility-intuition-tabs"))
    ).toHaveCount(0);
    await expect(
      frame.locator(
        role("portfolio-optimizer-volatility-intuition-panel-current")
      )
    ).toHaveCount(0);
    // σ ≥ 100% surfaces the leverage card even at 1.2x gross, speaking in
    // multiples of starting equity because no capital is known.
    const card = frame.locator(role("portfolio-optimizer-leverage-risk"));
    await expect(card).toBeVisible();
    await expect(
      card.locator(role("portfolio-optimizer-leverage-risk-median-target"))
    ).toContainText("0.58x start");
    await expect(
      card.locator(role("portfolio-optimizer-leverage-risk-median-shortfall"))
    ).toHaveCount(0);
    await expect(card).toContainText("multiple of starting equity");
  });
});
