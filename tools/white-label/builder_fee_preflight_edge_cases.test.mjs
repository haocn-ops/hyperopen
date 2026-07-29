import assert from "node:assert/strict";
import test from "node:test";

const builderAddress = "0x36a47878219fb346e031f6cf82cbfc8c77e35932";
const configured = Object.freeze({
  "hyperliquid-network": "testnet",
  "builder-fee": {
    status: "configured",
    "builder-address": builderAddress,
    "fee-tenths-bp": 10,
    disclosure:
      "DEXHelm charges an additional 0.01% builder fee on eligible fills after you approve it.",
  },
});

async function loadPreflight() {
  try {
    return await import("./builder_fee_preflight.mjs");
  } catch (_error) {
    return {};
  }
}

test("builder-fee preflight makes exactly the two read-only readiness calls and accepts Standard with 100 USDC", async () => {
  const { runBuilderFeePreflight } = await loadPreflight();
  assert.equal(typeof runBuilderFeePreflight, "function", "missing builder-fee preflight entry point");

  const calls = [];
  const result = await runBuilderFeePreflight(configured, {
    postInfo: async (body) => {
      calls.push(body);
      if (body.type === "userAbstraction") return "default";
      if (body.type === "clearinghouseState") {
        return { marginSummary: { accountValue: "100" } };
      }
      throw new Error("unexpected preflight request");
    },
  });

  assert.deepEqual(calls, [
    { type: "userAbstraction", user: builderAddress },
    { type: "clearinghouseState", user: builderAddress },
  ]);
  assert.deepEqual(result, {
    builderAddress,
    network: "testnet",
    mode: "Standard",
    accountValueAtLeast100: true,
  });
});

test("builder-fee preflight fails closed for disabled config, non-Standard mode, and ambiguous account value", async () => {
  const { runBuilderFeePreflight } = await loadPreflight();
  assert.equal(typeof runBuilderFeePreflight, "function", "missing builder-fee preflight entry point");

  for (const scenario of [
    { config: { ...configured, "builder-fee": { ...configured["builder-fee"], status: "disabled", "builder-address": null, "fee-tenths-bp": null } }, responses: [] },
    { config: configured, responses: ["unifiedAccount", { marginSummary: { accountValue: "1000" } }] },
    { config: configured, responses: ["default", { marginSummary: { accountValue: "99.999" } }] },
    { config: configured, responses: ["default", { marginSummary: { accountValue: "NaN" } }] },
  ]) {
    const calls = [];
    await assert.rejects(
      () => runBuilderFeePreflight(scenario.config, {
        postInfo: async (body) => {
          calls.push(body);
          return scenario.responses.shift();
        },
      }),
      /builder|Standard|100|configured/i,
    );
    assert.ok(calls.length <= 2, "preflight must remain bounded to two info reads");
  }
});
