import { expect, test } from "@playwright/test";
import { debugCall, dispatch, waitForIdle, visitRoute } from "../support/hyperopen.mjs";

const walletAddress = "0x1111111111111111111111111111111111111111";
const privateKey = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

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
  }, walletAddress);
}

async function seedCloseAllAccount(page) {
  await page.evaluate(({ nextWalletAddress, nextPrivateKey }) => {
    const c = globalThis.cljs?.core;
    const store = globalThis.hyperopen?.system?.store;
    if (!c || !store) throw new Error("Hyperopen store or cljs core unavailable");

    const keyword = c.keyword;
    const path = (...segments) =>
      c.PersistentVector.fromArray(segments.map((segment) => keyword(segment)), true);
    const opts = c.PersistentArrayMap.fromArray([keyword("keywordize-keys"), true], true);
    const clj = (value) => c.js__GT_clj(value, opts);
    const positions = [
      { position: { coin: "BTC", szi: "1.25", markPx: "100", entryPx: "99", marginUsed: "10" } },
      { position: { coin: "ETH", szi: "-2", markPx: "50", entryPx: "51", marginUsed: "10" } }
    ];
    let markets = clj({
      "perp:BTC": { key: "perp:BTC", coin: "BTC", dex: null, "market-type": "perp", "asset-id": 1, mark: 100, szDecimals: 2 },
      "perp:ETH": { key: "perp:ETH", coin: "ETH", dex: null, "market-type": "perp", "asset-id": 2, mark: 50, szDecimals: 2 }
    });
    markets = c.assoc_in(markets, path("perp:BTC", "market-type"), keyword("perp"));
    markets = c.assoc_in(markets, path("perp:ETH", "market-type"), keyword("perp"));
    const webdata2 = clj({
      clearinghouseState: {
        marginSummary: { accountValue: "1000", totalNtlPos: "200", totalMarginUsed: "20" },
        crossMarginSummary: { accountValue: "1000", totalNtlPos: "200", totalMarginUsed: "20" },
        withdrawable: "900",
        assetPositions: positions
      }
    });

    let state = c.deref(store);
    state = c.assoc_in(state, path("wallet", "connected?"), true);
    state = c.assoc_in(state, path("wallet", "address"), nextWalletAddress);
    state = c.assoc_in(state, path("wallet", "chain-id"), "0xa4b1");
    state = c.assoc_in(state, path("account-context", "spectate-mode", "active?"), false);
    state = c.assoc_in(state, path("account-context", "spectate-mode", "address"), null);
    state = c.assoc_in(state, path("webdata2"), webdata2);
    state = c.assoc_in(state, path("perp-dex-clearinghouse"), c.PersistentArrayMap.EMPTY);
    state = c.assoc_in(state, path("asset-selector", "phase"), keyword("full"));
    state = c.assoc_in(state, path("asset-selector", "market-by-key"), markets);
    state = c.assoc_in(state, path("account-info", "selected-tab"), keyword("balances"));
    state = c.assoc_in(state, path("account-info", "loading"), false);
    state = c.assoc_in(state, path("account-info", "error"), null);
    state = c.assoc_in(state, path("positions-ui", "close-all-confirmation"), null);
    c.reset_BANG_(store, state);
    globalThis.hyperopen?.app?.bootstrap?.render_app_BANG_?.(c.deref(store));
  }, { nextWalletAddress: walletAddress, nextPrivateKey: privateKey });
  await waitForIdle(page, { quietMs: 250, timeoutMs: 6_000, pollMs: 50 });
}

async function installCloseAllSimulator(page) {
  await debugCall(page, "installExchangeSimulator", {
    approveAgent: { responses: [{ status: "ok" }] },
    signedActions: {
      order: {
        responses: [{
          status: "ok",
          response: { data: { statuses: ["success", "success"] } }
        }]
      }
    },
    info: {
      default: { responses: Array.from({ length: 32 }, () => ({})) }
    }
  });
}

async function installLocalAgentSignerSimulator(page) {
  await page.evaluate(async () => {
    const loader = globalThis.shadow?.loader;
    if (loader && typeof loader.load === "function") {
      await loader.load("trading_crypto");
    }
  });
  await page.waitForFunction(() => Boolean(
    globalThis.goog?.getObjectByName?.("hyperopen.trading_crypto.module")
      || globalThis.hyperopen?.trading_crypto?.module
  ), null, { timeout: 5_000 });
  await page.evaluate(() => {
    const module = globalThis.goog?.getObjectByName?.("hyperopen.trading_crypto.module")
      || globalThis.hyperopen?.trading_crypto?.module;
    if (!module
        || typeof module.signL1ActionWithPrivateKey !== "function"
        || typeof module.signApproveAgentAction !== "function") {
      throw new Error("Trading crypto module unavailable");
    }
    module.signL1ActionWithPrivateKey = () => Promise.resolve({
      connectionId: `0x${"0".repeat(64)}`,
      r: `0x${"1".repeat(64)}`,
      s: `0x${"2".repeat(64)}`,
      v: 27
    });
    module.signApproveAgentAction = () => Promise.resolve({
      r: `0x${"3".repeat(64)}`,
      s: `0x${"4".repeat(64)}`,
      v: 27
    });
    globalThis.hyperopen?.trading_crypto_modules?.reset_trading_crypto_module_state_BANG_?.();
  });
}

async function seedReadyTradingSession(page) {
  await page.evaluate(() => {
    const c = globalThis.cljs?.core;
    const store = globalThis.hyperopen?.system?.store;
    const lockbox = globalThis.hyperopen?.wallet?.agent_lockbox;
    if (!c || !store || !lockbox) {
      throw new Error("Hyperopen store, cljs core, or agent lockbox unavailable");
    }
    const keyword = c.keyword;
    const path = (...segments) =>
      c.PersistentVector.fromArray(segments.map((segment) => keyword(segment)), true);
    const opts = c.PersistentArrayMap.fromArray([keyword("keywordize-keys"), true], true);
    const walletAddress = "0x1111111111111111111111111111111111111111";
    const agentAddress = "0x9999999999999999999999999999999999999999";
    const privateKey = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    const lastApprovedAt = 1700000000000;
    const session = {
      "agent-address": agentAddress,
      "private-key": privateKey,
      "last-approved-at": lastApprovedAt,
      "nonce-cursor": lastApprovedAt
    };
    localStorage.setItem("hyperopen:agent-storage-mode:v1", "session");
    localStorage.setItem("hyperopen:agent-local-protection-mode:v1", "plain");
    lockbox.cache_unlocked_session_BANG_(walletAddress, c.js__GT_clj(session, opts));
    let state = c.deref(store);
    state = c.assoc_in(state, path("wallet", "connected?"), true);
    state = c.assoc_in(state, path("wallet", "address"), walletAddress);
    state = c.assoc_in(state, path("wallet", "chain-id"), "0xa4b1");
    state = c.assoc_in(state, path("wallet", "agent", "status"), keyword("ready"));
    state = c.assoc_in(state, path("wallet", "agent", "storage-mode"), keyword("session"));
    state = c.assoc_in(state, path("wallet", "agent", "local-protection-mode"), keyword("plain"));
    state = c.assoc_in(state, path("wallet", "agent", "agent-address"), agentAddress);
    state = c.assoc_in(state, path("wallet", "agent", "last-approved-at"), lastApprovedAt);
    state = c.assoc_in(state, path("wallet", "agent", "nonce-cursor"), lastApprovedAt);
    state = c.assoc_in(state, path("wallet", "agent", "error"), null);
    state = c.assoc_in(state, path("wallet", "agent", "recovery-modal-open?"), false);
    c.reset_BANG_(store, state);
  });
}

async function signedOrderCalls(page) {
  const snapshot = await debugCall(page, "exchangeSimulatorSnapshot");
  return (snapshot?.calls ?? []).filter((call) =>
    (call?.paths ?? []).some((path) => Array.isArray(path) && path[0] === "signedActions" && path[1] === "order")
  );
}

test.describe("Positions Close All", () => {
  for (const viewport of [
    { width: 375, height: 812 },
    { width: 768, height: 900 },
    { width: 1280, height: 900 },
    { width: 1440, height: 900 }
  ]) {
    test(`is bounded and deterministic at ${viewport.width}px`, async ({ page }) => {
      await page.setViewportSize(viewport);
      await visitRoute(page, "/trade");
      await freezeAccountSurfaceSync(page);
      await seedReadyTradingSession(page);
      await installLocalAgentSignerSimulator(page);
      await installCloseAllSimulator(page);
      await freezeAccountSurfaceSync(page);
      await seedCloseAllAccount(page);
      await dispatch(page, [":actions/select-account-info-tab", ":positions"]);
      await waitForIdle(page, { quietMs: 500, timeoutMs: 8_000, pollMs: 50 });
      await page.evaluate(() => {
        const c = globalThis.cljs?.core;
        const store = globalThis.hyperopen?.system?.store;
        globalThis.hyperopen?.app?.bootstrap?.render_app_BANG_?.(c.deref(store));
      });

      const trigger = page.locator("[data-role='positions-close-all-trigger']");
      if (viewport.width < 1024) {
        await expect(trigger).toHaveCount(0);
        await expect(page.locator("[data-role='positions-close-all-confirmation']")).toHaveCount(0);
        return;
      }

      if (await trigger.count() === 0) {
        await dispatch(page, [":actions/trigger-close-all-positions"]);
        await page.evaluate(() => {
          const c = globalThis.cljs?.core;
          const store = globalThis.hyperopen?.system?.store;
          globalThis.hyperopen?.app?.bootstrap?.render_app_BANG_?.(c.deref(store));
        });
      }
      if (await trigger.count() > 0) {
        await expect(trigger).toBeVisible();
        await trigger.click();
      }
      const confirmation = page.locator("[data-role='positions-close-all-confirmation']");
      await expect(confirmation).toBeVisible();
      await expect(confirmation.locator("[data-role='positions-close-all-confirmation-count']"))
        .toHaveText("2 positions");
      expect(await signedOrderCalls(page)).toHaveLength(0);

      await confirmation.getByRole("button", { name: "Close all positions" }).click();
      await expect(confirmation).toContainText("Close requests submitted for 2 positions");
      await expect.poll(() => signedOrderCalls(page), { timeout: 10_000 }).toHaveLength(1);
      const calls = await signedOrderCalls(page);
      expect(calls).toHaveLength(1);
      const orders = calls[0].request.action.orders;
      expect(orders).toHaveLength(2);
      expect(orders.map((order) => ({ a: order.a, b: order.b, s: order.s, r: order.r, tif: order.t?.limit?.tif })))
        .toEqual([
          { a: 1, b: false, s: "1.25", r: true, tif: "Ioc" },
          { a: 2, b: true, s: "2", r: true, tif: "Ioc" }
        ]);
      const geometry = await confirmation.evaluate((node) => {
        const rect = node.getBoundingClientRect();
        return { left: rect.left, right: rect.right, width: rect.width };
      });
      expect(geometry.left).toBeGreaterThanOrEqual(0);
      expect(geometry.right).toBeLessThanOrEqual(viewport.width + 1);
    });
  }
});
