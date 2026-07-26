import assert from "node:assert/strict";
import test from "node:test";

import {
  keyword,
  optimizerPath,
  optimizerUiPath,
  seedPatch,
  stateKey,
  stringMap
} from "./optimizer_state.mjs";

test("optimizerPath and optimizerUiPath centralize browser seed roots", () => {
  assert.deepEqual(optimizerPath("draft", "return-model"), [
    "portfolio",
    "optimizer",
    "draft",
    "return-model"
  ]);
  assert.deepEqual(optimizerUiPath("black-litterman-editor"), [
    "portfolio-ui",
    "optimizer",
    "black-litterman-editor"
  ]);
});

test("seed descriptors preserve keywords and string-keyed optimizer maps", () => {
  assert.deepEqual(keyword("black-litterman"), {
    __hyperopenOptimizerSeed: "keyword",
    name: "black-litterman"
  });
  assert.deepEqual(stateKey("BTC"), {
    __hyperopenOptimizerSeed: "state-key",
    name: "BTC"
  });
  assert.deepEqual(stringMap([["perp:BTC", 1]]), {
    __hyperopenOptimizerSeed: "string-map",
    entries: [["perp:BTC", 1]]
  });
});

test("seedPatch returns serializable path and value descriptors", () => {
  assert.deepEqual(
    seedPatch(optimizerPath("history-data", "candle-history-by-coin", stateKey("BTC")), [
      { time: 1000, close: "100" }
    ]),
    {
      path: [
        "portfolio",
        "optimizer",
        "history-data",
        "candle-history-by-coin",
        { __hyperopenOptimizerSeed: "state-key", name: "BTC" }
      ],
      value: [{ time: 1000, close: "100" }]
    }
  );
});
