import { expect, test } from "@playwright/test";

import { debugCall, dispatch, visitRoute, waitForIdle } from "../support/hyperopen.mjs";

const BUILDER_ADDRESS = "0x36a47878219fb346e031f6cf82cbfc8c77e35932";
const OWNER_ADDRESS = "0x1111111111111111111111111111111111111111";
const AGENT_ADDRESS = "0x9999999999999999999999999999999999999999";
const AGENT_PRIVATE_KEY = `0x${"a".repeat(64)}`;
const DISCLOSURE =
  "DEXHelm charges an additional 0.01% builder fee on eligible fills after you approve it.";

async function injectConfiguredBuilderFee(page) {
  await page.evaluate(({ builderAddress, disclosure }) => {
    const c = globalThis.cljs?.core;
    const store = globalThis.hyperopen?.system?.store;
    const defaultTenant = globalThis.hyperopen?.service?.tenant_config?.default_tenant_raw;
    const renderApp = globalThis.hyperopen?.app?.bootstrap?.render_app_BANG_;
    if (!c || !store || !defaultTenant || typeof renderApp !== "function") {
      throw new Error("builder-fee tenant test seam unavailable");
    }

    const keyword = c.keyword;
    const path = (...segments) =>
      c.PersistentVector.fromArray(segments.map((segment) => keyword(segment)), true);
    const builderFee = c.PersistentArrayMap.fromArray([
      keyword("status"), keyword("configured"),
      keyword("builder-address"), builderAddress,
      keyword("fee-tenths-bp"), 10,
      keyword("disclosure"), disclosure
    ], true);
    const tenant = c.assoc(defaultTenant, keyword("builder-fee"), builderFee);
    const nextState = c.assoc(c.deref(store), keyword("tenant/override"), tenant);
    c.reset_BANG_(store, nextState);
    renderApp(c.deref(store));
  }, { builderAddress: BUILDER_ADDRESS, disclosure: DISCLOSURE });
}

async function openConfiguredBuilderFeeSettings(page) {
  await injectConfiguredBuilderFee(page);
  await waitForIdle(page, { quietMs: 100, timeoutMs: 4_000, pollMs: 50 });
  await page.locator('[data-role="header-settings-button"]').click();
  await expect(page.locator('[data-role="trading-settings-panel"]')).toBeVisible();
}

async function freezeAccountSurfaceSync(page) {
  await page.evaluate((address) => {
    const addressWatcher = globalThis.hyperopen?.wallet?.address_watcher;
    const webdata2 = globalThis.hyperopen?.websocket?.webdata2;
    const userSubscriptions = globalThis.hyperopen?.websocket?.user_runtime?.subscriptions;
    const store = globalThis.hyperopen?.system?.store;
    if (!addressWatcher || !webdata2 || !userSubscriptions || !store) return;
    addressWatcher.stop_watching_BANG_(store);
    addressWatcher.remove_handler_BANG_("webdata2-subscription-handler");
    addressWatcher.remove_handler_BANG_("user-ws-subscription-handler");
    addressWatcher.remove_handler_BANG_("startup-account-bootstrap-handler");
    webdata2.unsubscribe_webdata2_BANG_(address);
    userSubscriptions.unsubscribe_user_BANG_(address);
  }, OWNER_ADDRESS);
}

async function ensureTradingCryptoReady(page) {
  await page.evaluate(async () => {
    const loadTradingCrypto =
      globalThis.hyperopen?.trading_crypto_modules?.load_trading_crypto_module_BANG_;
    if (typeof loadTradingCrypto !== "function") {
      throw new Error("trading crypto module loader unavailable");
    }
    await loadTradingCrypto();
  });
}

async function seedReadyTradingSession(page) {
  await page.evaluate(
    ({ ownerAddress, agentAddress, privateKey }) => {
      const c = globalThis.cljs?.core;
      const store = globalThis.hyperopen?.system?.store;
      const renderApp = globalThis.hyperopen?.app?.bootstrap?.render_app_BANG_;
      const lockbox = globalThis.hyperopen?.wallet?.agent_lockbox;
      if (!c || !store || !lockbox || typeof renderApp !== "function") {
        throw new Error("builder-fee order test seam unavailable");
      }

      const keyword = c.keyword;
      const path = (...segments) =>
        c.PersistentVector.fromArray(segments.map((segment) => keyword(segment)), true);
      const lowerOwner = String(ownerAddress).toLowerCase();
      const approvedAt = 1_700_000_000_000;
      const sessionKey = `hyperopen:agent-session:v1:${lowerOwner}`;

      localStorage.setItem("hyperopen:agent-storage-mode:v1", "session");
      localStorage.setItem("hyperopen:agent-local-protection-mode:v1", "plain");
      localStorage.setItem(
        sessionKey,
        JSON.stringify({
          "agent-address": agentAddress,
          "private-key": privateKey,
          "last-approved-at": approvedAt,
          "nonce-cursor": approvedAt
        })
      );
      lockbox.cache_unlocked_session_BANG_(
        ownerAddress,
        c.js__GT_clj(
          {
            "agent-address": agentAddress,
            "private-key": privateKey,
            "last-approved-at": approvedAt,
            "nonce-cursor": approvedAt
          },
          c.PersistentArrayMap.fromArray([keyword("keywordize-keys"), true], true)
        )
      );

      let nextState = c.deref(store);
      nextState = c.assoc_in(nextState, path("wallet", "connected?"), true);
      nextState = c.assoc_in(nextState, path("wallet", "address"), ownerAddress);
      nextState = c.assoc_in(nextState, path("wallet", "chain-id"), "0xa4b1");
      nextState = c.assoc_in(nextState, path("wallet", "agent", "status"), keyword("ready"));
      nextState = c.assoc_in(nextState, path("wallet", "agent", "storage-mode"), keyword("session"));
      nextState = c.assoc_in(
        nextState,
        path("wallet", "agent", "local-protection-mode"),
        keyword("plain")
      );
      nextState = c.assoc_in(nextState, path("wallet", "agent", "agent-address"), agentAddress);
      nextState = c.assoc_in(nextState, path("wallet", "agent", "last-approved-at"), approvedAt);
      nextState = c.assoc_in(nextState, path("wallet", "agent", "nonce-cursor"), approvedAt);
      nextState = c.assoc_in(nextState, path("wallet", "agent", "error"), null);
      nextState = c.assoc_in(
        nextState,
        path("wallet", "agent", "recovery-modal-open?"),
        false
      );
      c.reset_BANG_(store, nextState);
      renderApp(c.deref(store));
    },
    {
      ownerAddress: OWNER_ADDRESS,
      agentAddress: AGENT_ADDRESS,
      privateKey: AGENT_PRIVATE_KEY
    }
  );
}

async function seedMarket(page, market) {
  await page.evaluate((marketConfig) => {
    const c = globalThis.cljs?.core;
    const store = globalThis.hyperopen?.system?.store;
    const renderApp = globalThis.hyperopen?.app?.bootstrap?.render_app_BANG_;
    if (!c || !store || typeof renderApp !== "function") {
      throw new Error("builder-fee market test seam unavailable");
    }

    const keyword = c.keyword;
    const path = (...segments) =>
      c.PersistentVector.fromArray(segments.map((segment) => keyword(segment)), true);
    const options = c.PersistentArrayMap.fromArray([keyword("keywordize-keys"), true], true);
    const seededMarket = c.js__GT_clj({
      ...marketConfig,
      "market-type": keyword(marketConfig.marketType)
    }, options);

    let nextState = c.deref(store);
    nextState = c.assoc_in(nextState, path("router", "path"), `/trade/${marketConfig.coin}`);
    nextState = c.assoc_in(
      nextState,
      path("asset-selector", "market-by-key"),
      c.PersistentArrayMap.fromArray([marketConfig.key, seededMarket], true)
    );
    c.reset_BANG_(store, nextState);
    renderApp(c.deref(store));
  }, market);

  await dispatch(page, [":actions/select-asset-by-market-key", market.key]);
  await expect.poll(async () => {
    const assetSelector = await debugCall(page, "oracle", "asset-selector", {});
    return assetSelector.activeAsset;
  }).toBe(market.coin);
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });

  await page.evaluate((marketConfig) => {
    const c = globalThis.cljs?.core;
    const store = globalThis.hyperopen?.system?.store;
    const renderApp = globalThis.hyperopen?.app?.bootstrap?.render_app_BANG_;
    const keyword = c.keyword;
    const path = (...segments) =>
      c.PersistentVector.fromArray(segments.map((segment) => keyword(segment)), true);
    const options = c.PersistentArrayMap.fromArray([keyword("keywordize-keys"), true], true);
    const seededMarket = c.js__GT_clj({
      ...marketConfig,
      "market-type": keyword(marketConfig.marketType)
    }, options);
    let nextState = c.deref(store);
    nextState = c.assoc_in(nextState, path("active-asset"), marketConfig.coin);
    nextState = c.assoc_in(nextState, path("selected-asset"), marketConfig.coin);
    nextState = c.assoc_in(nextState, path("active-market"), seededMarket);
    nextState = c.assoc_in(
      nextState,
      path("asset-selector", "market-by-key"),
      c.PersistentArrayMap.fromArray([marketConfig.key, seededMarket], true)
    );
    c.reset_BANG_(store, nextState);
    renderApp(c.deref(store));
  }, market);
}

async function seedSpotMarket(page) {
  const market = {
    key: "spot:PWSPOT",
    coin: "PWSPOT",
    symbol: "PWSPOT/USDC",
    base: "PWSPOT",
    marketType: "spot",
    "asset-id": 10000,
    szDecimals: 4,
    mark: 0.06
  };
  await seedMarket(page, market);
  return market;
}

async function seedPerpMarket(page) {
  await seedMarket(page, {
    key: "perp:BTC",
    coin: "BTC",
    symbol: "BTC",
    marketType: "perp",
    "asset-id": 0,
    szDecimals: 4,
    mark: 100
  });
}

async function prepareLimitOrder(page, side, inputMode = ":base") {
  const price = inputMode === ":quote" ? "0.06" : "100";
  await dispatch(page, [":actions/select-order-entry-mode", ":limit"]);
  await dispatch(page, [":actions/set-order-size-input-mode", inputMode]);
  await dispatch(page, [":actions/update-order-form", [":side"], side]);
  await dispatch(page, [":actions/update-order-form", [":price"], price]);
  await dispatch(page, [":actions/set-order-size-display", "1"]);
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
}

async function submitOrderInMarket(page, market) {
  return page.evaluate((marketConfig) => {
    const c = globalThis.cljs?.core;
    const store = globalThis.hyperopen?.system?.store;
    const debug = globalThis.HYPEROPEN_DEBUG;
    const keyword = c.keyword;
    const path = (...segments) =>
      c.PersistentVector.fromArray(segments.map((segment) => keyword(segment)), true);
    const options = c.PersistentArrayMap.fromArray([keyword("keywordize-keys"), true], true);
    const seededMarket = c.js__GT_clj({
      ...marketConfig,
      "market-type": keyword(marketConfig.marketType)
    }, options);
    let nextState = c.deref(store);
    nextState = c.assoc_in(nextState, path("active-asset"), marketConfig.coin);
    nextState = c.assoc_in(nextState, path("selected-asset"), marketConfig.coin);
    nextState = c.assoc_in(nextState, path("active-market"), seededMarket);
    nextState = c.assoc_in(
      nextState,
      path("asset-selector", "market-by-key"),
      c.PersistentArrayMap.fromArray([marketConfig.key, seededMarket], true)
    );
    c.reset_BANG_(store, nextState);
    const orderForm = debug.oracle("order-form", {});
    const dispatchResult = debug.dispatch([":actions/submit-order"]);
    return { orderForm, dispatchResult };
  }, market);
}

async function signedOrderActions(page) {
  const snapshot = await debugCall(page, "exchangeSimulatorSnapshot");
  return (snapshot?.calls ?? [])
    .map((call) => call?.request)
    .filter((request) => request?.action?.type === "order")
    .map((request) => request.action);
}

for (const viewport of [
  { width: 375, height: 812 },
  { width: 1280, height: 900 }
]) {
  test.describe(`builder fee review ${viewport.width}px`, () => {
    test.use({ viewport });

    test("configured tenant exposes a disclosed review control before any wallet request", async ({ page }) => {
      await visitRoute(page, "/trade");
      await openConfiguredBuilderFeeSettings(page);

      const section = page.locator("[data-role='builder-fee-section']");
      await expect(section).toBeVisible();
      await expect(section).toContainText(BUILDER_ADDRESS);
      await expect(section).toContainText("0.01%");
      await expect(section).toContainText(/perp|spot sell/i);
      await expect(section.getByRole("button", { name: "Review and enable" })).toBeVisible();
    });

    test("confirmed approval refreshes maxBuilderFee and only eligible orders carry the builder payload", async ({
      page
    }) => {
      await visitRoute(page, "/trade");
      await freezeAccountSurfaceSync(page);
      const maxBuilderFeeRequests = [];
      await page.route("**/info", async (route) => {
        const request = route.request();
        const body = request.postDataJSON?.();
        if (body?.type === "maxBuilderFee") {
          maxBuilderFeeRequests.push(body);
          await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: "10"
          });
          return;
        }
        await route.continue();
      });
      await debugCall(page, "installWalletSimulator", {
        accounts: [OWNER_ADDRESS],
        requestAccounts: [OWNER_ADDRESS],
        chainId: "0xa4b1",
        typedDataSignature: `0x${"1".repeat(64)}${"2".repeat(64)}1b`
      });
      await debugCall(page, "installExchangeSimulator", {
        signedActions: {
          approveBuilderFee: { responses: [{ status: "ok" }] },
          updateLeverage: { responses: [{ status: "ok" }] },
          order: { responses: [{ status: "ok" }, { status: "ok" }] }
        }
      });
      await seedReadyTradingSession(page);
      await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
      await ensureTradingCryptoReady(page);
      await openConfiguredBuilderFeeSettings(page);
      await dispatch(page, [":actions/set-confirm-open-orders-enabled", false]);

      const section = page.locator("[data-role='builder-fee-section']");
      const reviewButton = section.getByRole("button", { name: "Review and enable" });
      await reviewButton.click({ trial: true });
      await reviewButton.click();
      await expect(section.getByRole("button", { name: "Confirm and enable" })).toBeVisible();
      await section.getByRole("button", { name: "Confirm and enable" }).click();

      await expect
        .poll(async () => {
          const snapshot = await debugCall(page, "exchangeSimulatorSnapshot");
          const calls = snapshot?.calls ?? [];
          return {
            approvals: calls.filter(
              (call) => call?.matchedPath?.join(":") === "signedActions:approveBuilderFee"
            ).length,
            maxBuilderFeeReads: maxBuilderFeeRequests.length
          };
        })
        .toEqual({ approvals: 1, maxBuilderFeeReads: 1 });

      const approvalSnapshot = await debugCall(page, "exchangeSimulatorSnapshot");
      const approvalCall = approvalSnapshot.calls.find(
        (call) => call?.matchedPath?.join(":") === "signedActions:approveBuilderFee"
      );
      expect(approvalCall?.request?.action).toMatchObject({
        type: "approveBuilderFee",
        builder: BUILDER_ADDRESS,
        maxFeeRate: "0.01%"
      });
      expect(maxBuilderFeeRequests).toEqual([{
        type: "maxBuilderFee",
        user: OWNER_ADDRESS,
        builder: BUILDER_ADDRESS
      }]);
      await expect(section.getByRole("button", { name: "Enabled" })).toBeDisabled();
      await page.locator('[data-role="trading-settings-close"]').click();
      await expect(page.locator('[data-role="trading-settings-panel"]')).toBeHidden();
      await seedPerpMarket(page);
      await prepareLimitOrder(page, ":buy");
      expect(await debugCall(page, "oracle", "order-form", {})).toMatchObject({
        submitDisabled: false,
        submitReason: null
      });
      await dispatch(page, [":actions/submit-order"]);
      await waitForIdle(page, { quietMs: 250, timeoutMs: 4_000, pollMs: 50 });
      expect(await debugCall(page, "oracle", "wallet-status", {})).toMatchObject({
        agentStatus: "ready",
        agentError: null
      });
      expect(await debugCall(page, "oracle", "order-form", {})).toMatchObject({
        runtimeError: null
      });
      await expect.poll(() => signedOrderActions(page)).toHaveLength(1);
      expect((await signedOrderActions(page))[0].builder).toEqual({
        b: BUILDER_ADDRESS,
        f: 10
      });

      const spotMarket = await seedSpotMarket(page);
      await prepareLimitOrder(page, ":buy", ":quote");
      const spotSubmission = await submitOrderInMarket(page, spotMarket);
      expect(spotSubmission.orderForm).toMatchObject({
        submitDisabled: false,
        submitReason: null
      });
      await waitForIdle(page, { quietMs: 250, timeoutMs: 4_000, pollMs: 50 });
      expect(await debugCall(page, "oracle", "wallet-status", {})).toMatchObject({
        agentStatus: "ready",
        agentError: null
      });
      expect(await debugCall(page, "oracle", "order-form", {})).toMatchObject({
        runtimeError: null
      });
      await expect.poll(() => signedOrderActions(page)).toHaveLength(2);
      expect((await signedOrderActions(page))[1].builder).toBeUndefined();
    });
  });
}
