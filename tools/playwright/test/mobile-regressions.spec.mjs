import { expect, test } from "@playwright/test";
import {
  dispatch,
  dispatchMany,
  expectOracle,
  mobileViewport,
  oracle,
  sourceRectForLocator,
  visitRoute,
  waitForIdle
} from "../support/hyperopen.mjs";

const spectateRoute =
  "/trade?spectate=0x162cc7c861ebd0c06b3d72319201150482518185";

async function seedMobilePositionRows(page) {
  await page.evaluate(() => {
    const c = globalThis.cljs?.core;
    const store = globalThis.hyperopen?.system?.store;

    if (!c || !store) {
      throw new Error("Hyperopen store or cljs core unavailable");
    }

    const keyword = c.keyword;
    const kwPath = (...segments) =>
      c.PersistentVector.fromArray(segments.map((segment) => keyword(segment)), true);
    const opts = c.PersistentArrayMap.fromArray([keyword("keywordize-keys"), true], true);
    const nextWebdata2 = c.js__GT_clj(
      {
        clearinghouseState: {
          marginSummary: {
            accountValue: "4708974.9",
            totalNtlPos: "6398054.11",
            totalRawUsd: "6392466.23",
            totalMarginUsed: "63387.27"
          },
          crossMarginSummary: {
            accountValue: "4708974.9",
            totalNtlPos: "6398054.11",
            totalRawUsd: "6392466.23",
            totalMarginUsed: "63387.27"
          },
          crossMaintenanceMarginUsed: "63387.27",
          withdrawable: "4234255.18",
          assetPositions: [
            {
              position: {
                coin: "BTC",
                szi: "0.99455",
                positionValue: "67250.5",
                entryPx: "67000",
                markPx: "67251",
                unrealizedPnl: "309",
                returnOnEquity: "0.0046",
                liquidationPx: "4671.46",
                leverage: { value: 0.31 },
                marginUsed: "1000",
                cumFunding: { sinceOpen: "0" }
              }
            }
          ]
        }
      },
      opts
    );

    let nextState = c.deref(store);
    nextState = c.assoc_in(nextState, kwPath("webdata2"), nextWebdata2);
    nextState = c.assoc_in(nextState, kwPath("perp-dex-clearinghouse"), c.PersistentArrayMap.EMPTY);
    nextState = c.assoc_in(nextState, kwPath("account-info", "selected-tab"), keyword("positions"));

    c.reset_BANG_(store, nextState);
  });
}

test.describe("mobile browser regressions @mobile", () => {
  test.use(mobileViewport);

  test("account surface positions tab stays reachable on mobile @regression", async ({ page }) => {
    await visitRoute(page, spectateRoute);

    await dispatchMany(page, [
      [":actions/select-trade-mobile-surface", ":account"],
      [":actions/select-account-info-tab", ":positions"]
    ]);
    await waitForIdle(page, { quietMs: 200, timeoutMs: 4_000, pollMs: 50 });
    await expectOracle(page, "account-surface", {
      mobileSurface: "account",
      selectedTab: "positions",
      mobileAccountPanelPresent: true
    });
  });

  test("position margin opens as a mobile sheet @regression", async ({ page }) => {
    await visitRoute(page, spectateRoute);

    await dispatchMany(page, [
      [":actions/select-trade-mobile-surface", ":chart"],
      [":actions/select-account-info-tab", ":positions"]
    ]);
    await seedMobilePositionRows(page);
    await waitForIdle(page, { quietMs: 200, timeoutMs: 4_000, pollMs: 50 });
    await expectOracle(
      page,
      "first-position",
      { present: true },
      { timeoutMs: 8_000 }
    );

    const firstPosition = await oracle(page, "first-position");
    const sourceRect = await sourceRectForLocator(
      page,
      page.locator("[data-role^='mobile-position-card-']").first()
    );

    await dispatch(page, [
      ":actions/open-position-margin-modal",
      firstPosition.positionData,
      sourceRect
    ]);
    await waitForIdle(page, { quietMs: 200, timeoutMs: 4_000, pollMs: 50 });
    await expectOracle(
      page,
      "position-overlay",
      {
        open: true,
        presentationMode: "mobile-sheet"
      },
      { args: { surface: "margin" } }
    );
  });

  test("mobile positions list clears the fixed bottom nav @regression", async ({ page }) => {
    await visitRoute(page, spectateRoute);

    await dispatchMany(page, [
      [":actions/select-trade-mobile-surface", ":chart"],
      [":actions/select-account-info-tab", ":positions"]
    ]);
    await seedMobilePositionRows(page);
    await waitForIdle(page, { quietMs: 200, timeoutMs: 8_000, pollMs: 50 });

    const viewport = page.locator("[data-role='positions-mobile-cards-viewport']");
    const lastCard = page.locator(
      "[data-role='positions-mobile-cards-viewport'] [data-role^='mobile-position-card-']"
    ).last();
    const bottomNav = page.locator("[data-role='mobile-bottom-nav']");

    await expect(lastCard).toBeVisible({ timeout: 15_000 });
    const cardCount = await page.evaluate(() =>
      document.querySelectorAll(
        "[data-role='positions-mobile-cards-viewport'] [data-role^='mobile-position-card-']"
      ).length
    );

    expect(cardCount).toBeGreaterThan(0);
    await expect
      .poll(
        () =>
          page.evaluate(() => {
            const viewportNode = document.querySelector(
              "[data-role='positions-mobile-cards-viewport']"
            );
            const cards = Array.from(
              document.querySelectorAll(
                "[data-role='positions-mobile-cards-viewport'] [data-role^='mobile-position-card-']"
              )
            );
            const lastCardNode = cards.at(-1);
            const bottomNavNode = document.querySelector("[data-role='mobile-bottom-nav']");

            if (!viewportNode || !lastCardNode || !bottomNavNode) {
              throw new Error("required mobile positions nodes missing");
            }

            viewportNode.scrollTop = viewportNode.scrollHeight;
            return (
              lastCardNode.getBoundingClientRect().bottom -
              bottomNavNode.getBoundingClientRect().top
            );
          }),
        { timeout: 5_000 }
      )
      .toBeLessThanOrEqual(0);
    await expect(viewport).toBeVisible();
    await expect(bottomNav).toBeVisible();
  });

  test("mobile balances list clears the fixed bottom nav @regression", async ({ page }) => {
    await visitRoute(page, spectateRoute);

    await dispatchMany(page, [
      [":actions/select-trade-mobile-surface", ":chart"],
      [":actions/select-account-info-tab", ":balances"]
    ]);
    await waitForIdle(page, { quietMs: 200, timeoutMs: 8_000, pollMs: 50 });

    const viewport = page.locator("[data-role='balances-mobile-cards-viewport']");
    const lastCard = page.locator(
      "[data-role='balances-mobile-cards-viewport'] [data-role^='mobile-balance-card-']"
    ).last();
    const bottomNav = page.locator("[data-role='mobile-bottom-nav']");

    await expect(lastCard).toBeVisible({ timeout: 15_000 });
    const cardCount = await page.evaluate(() =>
      document.querySelectorAll(
        "[data-role='balances-mobile-cards-viewport'] [data-role^='mobile-balance-card-']"
      ).length
    );

    expect(cardCount).toBeGreaterThan(0);
    await expect
      .poll(
        () =>
          page.evaluate(() => {
            const viewportNode = document.querySelector(
              "[data-role='balances-mobile-cards-viewport']"
            );
            const cards = Array.from(
              document.querySelectorAll(
                "[data-role='balances-mobile-cards-viewport'] [data-role^='mobile-balance-card-']"
              )
            );
            const lastCardNode = cards.at(-1);
            const bottomNavNode = document.querySelector("[data-role='mobile-bottom-nav']");

            if (!viewportNode || !lastCardNode || !bottomNavNode) {
              throw new Error("required mobile balances nodes missing");
            }

            viewportNode.scrollTop = viewportNode.scrollHeight;
            return (
              lastCardNode.getBoundingClientRect().bottom -
              bottomNavNode.getBoundingClientRect().top
            );
          }),
        { timeout: 5_000 }
      )
      .toBeLessThanOrEqual(0);
    await expect(viewport).toBeVisible();
    await expect(bottomNav).toBeVisible();
  });
});
