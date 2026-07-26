import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const pkg = JSON.parse(fs.readFileSync(new URL("../../package.json", import.meta.url), "utf8"));

const automaticDevScripts = [
  "cljs:watch",
  "cljs:watch:portfolio",
  "portfolio:watch",
  "dev:browser-inspection"
];

test("automatic dev startup scripts do not stop existing Shadow servers", () => {
  for (const scriptName of automaticDevScripts) {
    const script = pkg.scripts[scriptName] ?? "";

    assert.doesNotMatch(script, /\bshadow-cljs\s+stop\b/, scriptName);
    assert.doesNotMatch(script, /\bnpm run shadow:stop\b/, scriptName);
  }
});

test("manual dev server cleanup commands remain available", () => {
  assert.equal(pkg.scripts["shadow:stop"], "npx shadow-cljs stop");
  assert.equal(pkg.scripts["dev:kill"], "node tools/dev-server-cleanup/kill_dev_servers.mjs");
});
