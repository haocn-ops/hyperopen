import assert from "node:assert/strict";
import fs from "node:fs/promises";
import test from "node:test";

import { buildCycloneDxSbom } from "./sbom.mjs";

test("production SBOM is deterministic and excludes development-only dependencies", async () => {
  const lockfile = JSON.parse(await fs.readFile(new URL("../../package-lock.json", import.meta.url)));
  const first = buildCycloneDxSbom(lockfile);
  const second = buildCycloneDxSbom(lockfile);

  assert.deepEqual(first, second);
  assert.equal(first.bomFormat, "CycloneDX");
  assert.equal(first.specVersion, "1.5");
  assert.equal(first.components.some((component) => component.name === "osqp"), false);
  assert.ok(first.components.every((component) => component.version && component.purl));
});

test("SBOM generation fails closed when a production package has no integrity", () => {
  assert.throws(
    () => buildCycloneDxSbom({
      name: "fixture",
      version: "1.0.0",
      lockfileVersion: 3,
      packages: {
        "": { name: "fixture", version: "1.0.0", dependencies: { unsafe: "1.0.0" } },
        "node_modules/unsafe": { version: "1.0.0" },
      },
    }),
    /integrity/i,
  );
});
