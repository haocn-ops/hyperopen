import assert from "node:assert/strict";
import test from "node:test";

import {
  buildLockfile,
  parseDependencyTree,
  parseDirectDependencies,
  validateLockfileFreshness,
} from "./clojure_tree.mjs";
import { assertNoAdvisories, scanInventory } from "./clojure_osv_scan.mjs";

test("tree parser rejects malformed and conflicting selected lines", () => {
  assert.throws(() => parseDependencyTree("org.clojure/clojure 1.12.0\nnot-a-coordinate"), /Malformed/);
  assert.throws(() => parseDependencyTree("org.clojure/clojure 1.12.0\n  . org.clojure/clojure 1.11.1"), /Conflicting/);
  assert.throws(() => buildLockfile({
    treeText: "org.clojure/clojure 1.12.0",
    depsText: "{:deps {org.yaml/snakeyaml {:mvn/version \"2.0\"}}}",
  }), /absent/);
});

test("direct dependency validation includes Maven coordinates declared by aliases", () => {
  const depsText = `
    {:deps {org.clojure/clojure {:mvn/version "1.12.0"}}
     :aliases {:dev {:extra-deps {thheller/shadow-cljs {:mvn/version "3.4.0"}}}}}`;
  assert.deepEqual(parseDirectDependencies(depsText), [
    { name: "org.clojure:clojure", version: "1.12.0" },
    { name: "thheller:shadow-cljs", version: "3.4.0" },
  ]);
  assert.throws(() => buildLockfile({
    treeText: "org.clojure/clojure 1.12.0",
    depsText,
  }), /thheller:shadow-cljs.*absent/);
});

test("lock freshness rejects added, removed, and version-changed direct dependencies", () => {
  const originalDeps = `{:deps {org.clojure/clojure {:mvn/version "1.12.0"}
                                org.yaml/snakeyaml {:mvn/version "2.0"}}}`;
  const lockfile = buildLockfile({
    treeText: "org.clojure/clojure 1.12.0\norg.yaml/snakeyaml 2.0",
    depsText: originalDeps,
  });
  assert.equal(validateLockfileFreshness(lockfile, originalDeps), true);
  assert.throws(() => validateLockfileFreshness(lockfile,
    `{:deps {org.clojure/clojure {:mvn/version "1.12.0"}
             org.yaml/snakeyaml {:mvn/version "2.0"}
             thheller/shadow-cljs {:mvn/version "3.4.0"}}}`), /stale/);
  assert.throws(() => validateLockfileFreshness(lockfile,
    `{:deps {org.clojure/clojure {:mvn/version "1.12.0"}}}`), /stale/);
  assert.throws(() => validateLockfileFreshness(lockfile,
    `{:deps {org.clojure/clojure {:mvn/version "1.11.1"}
             org.yaml/snakeyaml {:mvn/version "2.0"}}}`), /stale/);
});

test("OSV scanner fails closed on malformed responses and advisories", async () => {
  await assert.rejects(() => scanInventory({ schemaVersion: 1, dependencies: [{ name: "a:b", version: "1" }] }, {
    fetchImpl: async () => ({ ok: true, status: 200, json: async () => ({}) }),
  }), /does not match/);
  const report = { dependencies: [{ name: "a:b", version: "1", advisories: [{ id: "CVE-TEST" }] }] };
  assert.throws(() => assertNoAdvisories(report), /vulnerable/);
});
