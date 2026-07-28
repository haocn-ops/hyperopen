import { pathToFileURL } from "node:url";

export const TESTNET_ENDPOINTS = {
  infoUrl: "https://api.hyperliquid-testnet.xyz/info",
  exchangeUrl: "https://api.hyperliquid-testnet.xyz/exchange",
  wsUrl: "wss://api.hyperliquid-testnet.xyz/ws",
};

const DEFAULT_TIMEOUT_MS = 15_000;

function finiteTimeout(timeoutMs) {
  const value = Number(timeoutMs);

  if (!Number.isFinite(value) || value <= 0) {
    throw new Error("Testnet smoke timeout must be a positive finite number.");
  }

  return Math.floor(value);
}

function withTimeout(label, timeoutMs, setTimeoutFn, clearTimeoutFn, work) {
  const timeout = finiteTimeout(timeoutMs);

  return new Promise((resolve, reject) => {
    let settled = false;
    let timeoutId;
    const finish = (settle, value) => {
      if (settled) {
        return;
      }
      settled = true;
      clearTimeoutFn(timeoutId);
      settle(value);
    };

    timeoutId = setTimeoutFn(
      () => finish(reject, new Error(`${label} timed out after ${timeout}ms`)),
      timeout
    );

    let workPromise;
    try {
      workPromise = work();
    } catch (error) {
      finish(reject, error);
      return;
    }

    Promise.resolve(workPromise)
      .then(
        (value) => finish(resolve, value),
        (error) => finish(reject, error)
      );
  });
}

async function parseJsonResponse(label, response) {
  try {
    return await response.json();
  } catch {
    throw new Error(`${label} returned non-JSON data`);
  }
}

function responseStatus(response) {
  return Number(response?.status);
}

function responseRejectionText(payload, rawText = "") {
  return [payload?.status, payload?.error, payload?.response, payload?.message, rawText]
    .filter((value) => value !== undefined && value !== null)
    .join(" ")
    .toLowerCase();
}

function rejectedExchangeResponse(response, payload, rawText) {
  const status = responseStatus(response);
  const rejectionText = responseRejectionText(payload, rawText);

  return Number.isFinite(status)
    && status < 500
    && !/\b(ok|success|accepted|executed|submitted)\b/i.test(rejectionText)
    && /\b(err|error|reject|rejection|invalid|missing|deserializ\w*)\b/i.test(rejectionText);
}

async function parseExchangeRejection(response) {
  if (typeof response?.text === "function") {
    const rawText = await response.text();

    try {
      return { payload: JSON.parse(rawText), rawText: "" };
    } catch {
      return { payload: {}, rawText };
    }
  }

  return { payload: await parseJsonResponse("exchange", response), rawText: "" };
}

function marketCountFromMessage(event) {
  try {
    const payload = JSON.parse(String(event?.data ?? ""));
    const mids = payload?.channel === "allMids" ? payload?.data?.mids : null;

    return mids && typeof mids === "object" ? Object.keys(mids).length : 0;
  } catch {
    return 0;
  }
}

function addSocketListener(socket, type, listener) {
  if (typeof socket.addEventListener === "function") {
    socket.addEventListener(type, listener);
  } else {
    socket[`on${type}`] = listener;
  }
}

async function receiveAllMids({ WebSocketCtor, timeoutMs, setTimeoutFn, clearTimeoutFn }) {
  let socket;

  try {
    socket = new WebSocketCtor(TESTNET_ENDPOINTS.wsUrl);

    return await withTimeout(
      "websocket allMids",
      timeoutMs,
      setTimeoutFn,
      clearTimeoutFn,
      () =>
        new Promise((resolve, reject) => {
          addSocketListener(socket, "open", () => {
            try {
              socket.send(JSON.stringify({
                method: "subscribe",
                subscription: { type: "allMids" },
              }));
            } catch {
              reject(new Error("websocket allMids subscription failed"));
            }
          });
          addSocketListener(socket, "message", (event) => {
            const marketCount = marketCountFromMessage(event);

            if (marketCount > 0) {
              resolve(marketCount);
            }
          });
          addSocketListener(socket, "error", () => {
            reject(new Error("websocket allMids connection failed"));
          });
        })
    );
  } finally {
    if (socket) {
      socket.close();
    }
  }
}

export function buildInertExchangeProbe(probe = {}) {
  if (!probe || typeof probe !== "object" || Object.keys(probe).length > 0) {
    throw new Error("Exchange smoke probe must remain an empty inert object.");
  }

  return {};
}

export async function runTestnetSmoke({
  fetchFn = fetch,
  WebSocketCtor = globalThis.WebSocket,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  setTimeoutFn = setTimeout,
  clearTimeoutFn = clearTimeout,
  log = console.log,
} = {}) {
  if (typeof fetchFn !== "function") {
    throw new Error("Testnet smoke requires a fetch function.");
  }
  if (typeof WebSocketCtor !== "function") {
    throw new Error("Testnet smoke requires Node's WebSocket constructor.");
  }

  const requestOptions = (body) => ({
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  log("Hyperliquid network: testnet");

  const infoResponse = await withTimeout(
    "info request",
    timeoutMs,
    setTimeoutFn,
    clearTimeoutFn,
    () => fetchFn(TESTNET_ENDPOINTS.infoUrl, requestOptions({ type: "meta" }))
  );
  const infoPayload = await withTimeout(
    "info JSON",
    timeoutMs,
    setTimeoutFn,
    clearTimeoutFn,
    () => parseJsonResponse("info", infoResponse)
  );
  const universeCount = Array.isArray(infoPayload?.universe) ? infoPayload.universe.length : 0;

  if (responseStatus(infoResponse) !== 200 || universeCount === 0) {
    throw new Error("info meta response did not include a nonempty universe");
  }
  log(`PASS info ${TESTNET_ENDPOINTS.infoUrl} type=meta universe-count=${universeCount}`);

  const exchangeResponse = await withTimeout(
    "exchange request",
    timeoutMs,
    setTimeoutFn,
    clearTimeoutFn,
    () => fetchFn(TESTNET_ENDPOINTS.exchangeUrl, requestOptions(buildInertExchangeProbe()))
  );
  const { payload: exchangePayload, rawText: exchangeRawText } = await withTimeout(
    "exchange rejection",
    timeoutMs,
    setTimeoutFn,
    clearTimeoutFn,
    () => parseExchangeRejection(exchangeResponse)
  );

  if (!rejectedExchangeResponse(exchangeResponse, exchangePayload, exchangeRawText)) {
    throw new Error("exchange probe must be rejected with a non-5xx response");
  }
  log(`PASS exchange ${TESTNET_ENDPOINTS.exchangeUrl} rejected-as-expected status=${responseStatus(exchangeResponse)}`);

  const marketCount = await receiveAllMids({
    WebSocketCtor,
    timeoutMs,
    setTimeoutFn,
    clearTimeoutFn,
  });
  log(`PASS websocket ${TESTNET_ENDPOINTS.wsUrl} channel=allMids market-count=${marketCount}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  runTestnetSmoke().catch((error) => {
    console.error(`FAIL testnet smoke: ${error.message}`);
    process.exitCode = 1;
  });
}
