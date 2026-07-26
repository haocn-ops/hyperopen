const SEED_TAG = "__hyperopenOptimizerSeed";

const defaultSeedIdleOptions = Object.freeze({
  quietMs: 150,
  timeoutMs: 4_000,
  pollMs: 50
});

function tagged(type, fields = {}) {
  return {
    [SEED_TAG]: type,
    ...fields
  };
}

export function keyword(name) {
  return tagged("keyword", { name });
}

export function stateKey(name) {
  return tagged("state-key", { name });
}

export function stringMap(entries) {
  return tagged("string-map", { entries });
}

export function optimizerPath(...segments) {
  return ["portfolio", "optimizer", ...segments];
}

export function optimizerUiPath(...segments) {
  return ["portfolio-ui", "optimizer", ...segments];
}

export function seedPatch(path, value) {
  return { path, value };
}

function normalizePatches(patches) {
  if (!Array.isArray(patches)) {
    throw new TypeError("seedOptimizerState expects an array of seed patches");
  }
  return patches.map((patch) => {
    if (!patch || !Array.isArray(patch.path)) {
      throw new TypeError("Each optimizer seed patch must include a path array");
    }
    return patch;
  });
}

export async function seedOptimizerState(page, patches, options = {}) {
  const normalizedPatches = normalizePatches(patches);
  await page.evaluate(
    ({ patches: serializedPatches, seedTag }) => {
      const c = globalThis.cljs.core;
      const store = globalThis.hyperopen.system.store;
      const kw = (name) => c.keyword(String(name));

      const isTagged = (value, type) =>
        value &&
        typeof value === "object" &&
        !Array.isArray(value) &&
        value[seedTag] === type;

      const toClj = (value) => {
        if (isTagged(value, "keyword")) {
          return kw(value.name);
        }
        if (isTagged(value, "state-key")) {
          return value.name;
        }
        if (isTagged(value, "string-map")) {
          return c.PersistentArrayMap.fromArray(
            value.entries.flatMap(([key, entryValue]) => [String(key), toClj(entryValue)]),
            true
          );
        }
        if (Array.isArray(value)) {
          return c.PersistentVector.fromArray(value.map(toClj), true);
        }
        if (value && typeof value === "object") {
          return c.PersistentArrayMap.fromArray(
            Object.entries(value).flatMap(([key, entryValue]) => [kw(key), toClj(entryValue)]),
            true
          );
        }
        return value;
      };

      const pathSegment = (segment) =>
        isTagged(segment, "state-key") ? segment.name : kw(segment);
      const pathVector = (segments) =>
        c.PersistentVector.fromArray(segments.map(pathSegment), true);

      let nextState = c.deref(store);
      for (const patch of serializedPatches) {
        nextState = c.assoc_in(nextState, pathVector(patch.path), toClj(patch.value));
      }
      c.reset_BANG_(store, nextState);
    },
    { patches: normalizedPatches, seedTag: SEED_TAG }
  );
  const { waitForIdle } = await import("./hyperopen.mjs");
  await waitForIdle(page, options.idleOptions || defaultSeedIdleOptions);
}

export async function seedOptimizerMarkets(page, markets, options = {}) {
  await seedOptimizerState(
    page,
    [
      seedPatch(["asset-selector", "markets"], markets),
      seedPatch(
        ["asset-selector", "market-by-key"],
        stringMap(markets.map((market) => [market.key, market]))
      )
    ],
    options
  );
}

export async function readOptimizerState(page, path) {
  if (!Array.isArray(path)) {
    throw new TypeError("readOptimizerState expects a path array");
  }

  return page.evaluate(
    ({ path: serializedPath, seedTag }) => {
      const c = globalThis.cljs.core;
      const kw = (name) => c.keyword(String(name));
      const isStateKey = (value) =>
        value &&
        typeof value === "object" &&
        !Array.isArray(value) &&
        value[seedTag] === "state-key";
      const pathVector = c.PersistentVector.fromArray(
        serializedPath.map((segment) => (isStateKey(segment) ? segment.name : kw(segment))),
        true
      );
      return c.clj__GT_js(c.get_in(c.deref(globalThis.hyperopen.system.store), pathVector));
    },
    { path, seedTag: SEED_TAG }
  );
}
