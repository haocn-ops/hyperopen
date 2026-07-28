import assert from "node:assert/strict";
import test from "node:test";

import {
  TESTNET_ENDPOINTS,
  buildInertExchangeProbe,
  runTestnetSmoke,
} from "./testnet_live_smoke.mjs";

const expectedEndpoints = {
  infoUrl: "https://api.hyperliquid-testnet.xyz/info",
  exchangeUrl: "https://api.hyperliquid-testnet.xyz/exchange",
  wsUrl: "wss://api.hyperliquid-testnet.xyz/ws",
};

const jsonResponse = (status, body) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => body,
});

const textResponse = (status, body) => ({
  ok: status >= 200 && status < 300,
  status,
  text: async () => body,
});

class FakeWebSocket {
  static instances = [];

  constructor(url) {
    this.url = url;
    this.listeners = new Map();
    this.sent = [];
    this.closeCalls = 0;
    FakeWebSocket.instances.push(this);

    queueMicrotask(() => this.emit("open"));
  }

  addEventListener(type, listener) {
    const listeners = this.listeners.get(type) || [];
    listeners.push(listener);
    this.listeners.set(type, listeners);
  }

  send(payload) {
    this.sent.push(payload);
    const message = JSON.parse(payload);

    if (message.method === "subscribe" && message.subscription?.type === "allMids") {
      queueMicrotask(() =>
        this.emit("message", {
          data: JSON.stringify({
            channel: "allMids",
            data: { mids: { ETH: "3000.00" } },
          }),
        })
      );
    }
  }

  close() {
    this.closeCalls += 1;
    this.emit("close");
  }

  emit(type, event = {}) {
    for (const listener of this.listeners.get(type) || []) {
      listener(event);
    }

    const propertyListener = this[`on${type}`];
    if (typeof propertyListener === "function") {
      propertyListener(event);
    }
  }
}

test("runTestnetSmoke posts only inert data, accepts exchange rejection, and closes allMids socket", async () => {
  FakeWebSocket.instances = [];
  const fetchCalls = [];
  const logs = [];
  const fetchFn = async (url, init) => {
    fetchCalls.push({ url, init });

    if (url === expectedEndpoints.infoUrl) {
      return jsonResponse(200, { universe: [{ name: "ETH" }] });
    }

    if (url === expectedEndpoints.exchangeUrl) {
      return jsonResponse(400, { status: "err", error: "missing action" });
    }

    throw new Error(`Unexpected URL: ${url}`);
  };

  assert.deepEqual(TESTNET_ENDPOINTS, expectedEndpoints);
  assert.deepEqual(buildInertExchangeProbe(), {});

  await runTestnetSmoke({
    fetchFn,
    WebSocketCtor: FakeWebSocket,
    timeoutMs: 25,
    log: (...parts) => logs.push(parts.join(" ")),
  });

  assert.deepEqual(
    fetchCalls.map(({ url, init }) => ({
      url,
      method: init.method,
      body: JSON.parse(init.body),
    })),
    [
      { url: expectedEndpoints.infoUrl, method: "POST", body: { type: "meta" } },
      { url: expectedEndpoints.exchangeUrl, method: "POST", body: {} },
    ]
  );
  assert.doesNotMatch(
    JSON.stringify(fetchCalls[1]),
    /signature|private|agent|address|vault|nonce|order|cancel|transfer/i
  );
  assert.equal(FakeWebSocket.instances.length, 1);
  assert.equal(FakeWebSocket.instances[0].url, expectedEndpoints.wsUrl);
  assert.deepEqual(FakeWebSocket.instances[0].sent, [
    JSON.stringify({ method: "subscribe", subscription: { type: "allMids" } }),
  ]);
  assert.equal(FakeWebSocket.instances[0].closeCalls, 1);
  assert.ok(logs.some((line) => line.includes(`PASS info ${expectedEndpoints.infoUrl}`)));
  assert.ok(
    logs.some((line) => line.includes(`PASS exchange ${expectedEndpoints.exchangeUrl} rejected-as-expected`))
  );
  assert.ok(
    logs.some((line) => line.includes(`PASS websocket ${expectedEndpoints.wsUrl} channel=allMids`))
  );
});

test("runTestnetSmoke rejects an accepted exchange response before opening a websocket", async () => {
  FakeWebSocket.instances = [];
  let fetchCount = 0;

  await assert.rejects(
    runTestnetSmoke({
      fetchFn: async () => {
        fetchCount += 1;
        return fetchCount === 1
          ? jsonResponse(200, { universe: [{ name: "ETH" }] })
          : jsonResponse(200, { status: "ok" });
      },
      WebSocketCtor: FakeWebSocket,
      timeoutMs: 25,
      log: () => {},
    }),
    /exchange.*reject/i
  );

  assert.equal(fetchCount, 2);
  assert.equal(FakeWebSocket.instances.length, 0);
});

test("runTestnetSmoke accepts a bounded plain-text exchange deserialization rejection", async () => {
  FakeWebSocket.instances = [];

  await runTestnetSmoke({
    fetchFn: async (url) =>
      url === expectedEndpoints.infoUrl
        ? jsonResponse(200, { universe: [{ name: "ETH" }] })
        : textResponse(422, "Failed to deserialize the JSON body into the target type"),
    WebSocketCtor: FakeWebSocket,
    timeoutMs: 25,
    log: () => {},
  });

  assert.equal(FakeWebSocket.instances.length, 1);
  assert.equal(FakeWebSocket.instances[0].closeCalls, 1);
});

test("runTestnetSmoke reports a bounded info timeout without opening a websocket", async () => {
  const scheduled = [];
  FakeWebSocket.instances = [];

  const result = runTestnetSmoke({
    fetchFn: () => new Promise(() => {}),
    WebSocketCtor: FakeWebSocket,
    timeoutMs: 17,
    setTimeoutFn: (callback, delay) => {
      scheduled.push({ callback, delay });
      return scheduled.length;
    },
    clearTimeoutFn: () => {},
    log: () => {},
  });

  assert.deepEqual(scheduled.map(({ delay }) => delay), [17]);
  scheduled[0].callback();

  await assert.rejects(result, /info.*timed out/i);
  assert.equal(FakeWebSocket.instances.length, 0);
});
