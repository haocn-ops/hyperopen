import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { generatedFiles } from "./generate_theme_css.mjs";

const require = createRequire(import.meta.url);
const repoRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "..",
  "..",
);
const palette = require(
  path.join(repoRoot, "src", "styles", "themes", "palette.js"),
);

test("committed theme css matches palette.js (run tools/styles/generate_theme_css.mjs)", () => {
  for (const { id, file, css } of generatedFiles()) {
    assert.ok(fs.existsSync(file), `missing generated css for theme "${id}"`);
    assert.equal(
      fs.readFileSync(file, "utf8"),
      css,
      `src/styles/themes/${id}.css is stale for theme "${id}"`,
    );
  }
});

test("main.css imports every generated theme css", () => {
  const mainCss = fs.readFileSync(
    path.join(repoRoot, "src", "styles", "main.css"),
    "utf8",
  );
  for (const id of palette.themeOrder) {
    assert.ok(
      mainCss.includes(`@import "./themes/${id}.css";`),
      `main.css missing @import for themes/${id}.css`,
    );
  }
});

test("cljs theme catalog mirrors palette ids and labels", () => {
  const catalog = fs.readFileSync(
    path.join(repoRoot, "src", "hyperopen", "ui", "theme.cljs"),
    "utf8",
  );
  for (const id of palette.themeOrder) {
    assert.ok(
      catalog.includes(`:id "${id}"`),
      `hyperopen.ui.theme missing theme id "${id}"`,
    );
    assert.ok(
      catalog.includes(`:label "${palette.themes[id].label}"`),
      `hyperopen.ui.theme missing label "${palette.themes[id].label}" for "${id}"`,
    );
  }
  const catalogIds = [...catalog.matchAll(/:id "([^"]+)"/g)].map(
    (match) => match[1],
  );
  assert.deepEqual(
    catalogIds,
    palette.themeOrder,
    "hyperopen.ui.theme order/ids must match palette themeOrder",
  );
});

test("default theme keeps the legacy trading palette pixel-identical", () => {
  const dark = palette.themes[palette.defaultTheme];
  assert.equal(dark.colors.bg, "#0f1a1f");
  assert.equal(dark.colors.border, "#30363d");
  assert.equal(dark.colors.buy, "#00d4aa");
  assert.equal(dark.colors.sell, "#ff6b6b");
  assert.equal(dark.colors["text-hi"], "#ffffff");
  assert.equal(dark.colors["text-secondary"], "#8b949e");
  assert.equal(dark.radius.DEFAULT, "0.25rem");
});
