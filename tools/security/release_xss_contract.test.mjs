import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { assertReleaseXssContract, scanAuthoredSources } from "./release_xss_contract.mjs";

async function withFixture(callback) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "hyperopen-xss-contract-"));
  const sourceRoot = path.join(root, "src", "hyperopen");
  const releaseRoot = path.join(root, "release");
  await fs.mkdir(sourceRoot, { recursive: true });
  await fs.mkdir(releaseRoot, { recursive: true });
  await fs.writeFile(path.join(sourceRoot, "safe.cljs"), "(set! (.-textContent node) value)\n");
  await fs.writeFile(path.join(releaseRoot, "index.html"), '<!doctype html><script src="/js/main.ABC.js"></script>\n');
  await fs.mkdir(path.join(releaseRoot, "js"));
  await fs.writeFile(path.join(releaseRoot, "js", "main.ABC.js"), "console.log('safe');\n");
  try {
    await callback({ releaseRoot, root, sourceRoot });
  } finally {
    await fs.rm(root, { force: true, recursive: true });
  }
}

test("safe authored source and same-origin release scripts pass", async () => {
  await withFixture(async ({ releaseRoot, sourceRoot }) => {
    assert.deepEqual(await assertReleaseXssContract({ releaseRoot, sourceRoot }), {
      authoredSinkCount: 0,
      releaseScriptCount: 1,
      vendorSinkInventory: [],
    });
  });
});

test("authored source rejects string-to-markup and string-to-code sinks", async () => {
  for (const source of [
    "node.innerHTML = value;",
    "node.outerHTML = value;",
    "node.insertAdjacentHTML('beforeend', value);",
    "document.write(value);",
    "eval(value);",
    "new Function(value);",
    '(js/eval value)',
    '(.write js/document value)',
    '(js/Function. value)',
    '(.insertAdjacentHTML node "beforeend" value)',
  ]) {
    await withFixture(async ({ sourceRoot }) => {
      await fs.writeFile(path.join(sourceRoot, "unsafe.cljs"), source);
      await assert.rejects(() => scanAuthoredSources(sourceRoot), /unsafe authored source sink/i);
    });
  }
});

test("release HTML rejects inline, remote, handler, and javascript execution", async () => {
  const unsafeDocuments = [
    "<script>alert(1)</script>",
    '<script src="https://evil.example/a.js"></script>',
    '<button onclick="alert(1)">go</button>',
    "<button onclick=alert(1)>go</button>",
    '<a href="javascript:alert(1)">go</a>',
    "<a href=javascript:alert(1)>go</a>",
  ];
  for (const document of unsafeDocuments) {
    await withFixture(async ({ releaseRoot, sourceRoot }) => {
      await fs.writeFile(path.join(releaseRoot, "index.html"), document);
      await assert.rejects(() => assertReleaseXssContract({ releaseRoot, sourceRoot }), /unsafe release html/i);
    });
  }
});

test("vendor sink inventory is reported without weakening authored-source checks", async () => {
  await withFixture(async ({ releaseRoot, sourceRoot }) => {
    await fs.writeFile(path.join(releaseRoot, "js", "main.ABC.js"), "node.innerHTML = trustedVendorValue;\n");
    const result = await assertReleaseXssContract({ releaseRoot, sourceRoot });
    assert.deepEqual(result.vendorSinkInventory, ["js/main.ABC.js:innerHTML"]);
  });
});
