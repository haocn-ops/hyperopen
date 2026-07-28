import assert from "node:assert/strict";
import test from "node:test";

import { buildLockfile, parseDependencyTree, validateLockfileFreshness } from "./clojure_tree.mjs";
import { scanInventory } from "./clojure_osv_scan.mjs";

const tree = `org.clojure/clojure 1.12.0
  . org.clojure/spec.alpha 0.5.238 :newer-version
org.yaml/snakeyaml 2.0
  X org.yaml/snakeyaml 1.33 :use-top
`;

test("selected Clojure tree becomes sorted Maven inventory", () => {
  const lockfile = buildLockfile({
    treeText: tree,
    depsText: `{:deps {org.clojure/clojure {:mvn/version "1.12.0"}
                       org.yaml/snakeyaml {:mvn/version "2.0"}}}`,
    clojureCliVersion: "Clojure CLI version fixture",
  });
  assert.deepEqual(lockfile.dependencies, [
    { name: "org.clojure:clojure", version: "1.12.0" },
    { name: "org.clojure:spec.alpha", version: "0.5.238" },
    { name: "org.yaml:snakeyaml", version: "2.0" },
  ]);
  assert.deepEqual(lockfile.directDependencies, [
    { name: "org.clojure:clojure", version: "1.12.0" },
    { name: "org.yaml:snakeyaml", version: "2.0" },
  ]);
  assert.equal(validateLockfileFreshness(lockfile, `{:deps {org.clojure/clojure {:mvn/version "1.12.0"}
                       org.yaml/snakeyaml {:mvn/version "2.0"}}}`), true);
  assert.equal(JSON.stringify(lockfile), JSON.stringify(buildLockfile({
    treeText: tree,
    depsText: `{:deps {org.clojure/clojure {:mvn/version "1.12.0"}
                       org.yaml/snakeyaml {:mvn/version "2.0"}}}`,
    clojureCliVersion: "Clojure CLI version fixture",
  })));
});

test("OSV Maven scan batches exact pinned coordinates and normalizes no findings", async () => {
  const requests = [];
  const report = await scanInventory({
    schemaVersion: 1,
    dependencies: [
      { name: "org.clojure:clojure", version: "1.12.0" },
      { name: "org.yaml:snakeyaml", version: "2.0" },
      { name: "com.fasterxml.jackson.core:jackson-core", version: "2.18.8" },
    ],
  }, {
    batchSize: 2,
    fetchImpl: async (_url, options) => {
      requests.push(JSON.parse(options.body));
      const queryCount = JSON.parse(options.body).queries.length;
      return { ok: true, status: 200, json: async () => ({ results: Array.from({ length: queryCount }, () => ({ vulns: [] })) }) };
    },
  });
  assert.equal(requests.length, 2);
  assert.equal(requests[0].queries[0].package.ecosystem, "Maven");
  assert.equal(report.dependencies.every(({ advisories }) => advisories.length === 0), true);
});
