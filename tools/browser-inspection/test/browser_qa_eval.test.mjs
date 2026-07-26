import test from "node:test";
import assert from "node:assert/strict";
import { runBrowserQaEval } from "../src/browser_qa_eval.mjs";

test("browser QA eval corpus stays green and seeded", () => {
  const result = runBrowserQaEval();
  assert.ok(result.corpusSize >= 30);
  assert.equal(result.failing, 0);
});
