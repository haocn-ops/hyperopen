import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import vm from "node:vm";
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
const themePreloadSource = fs.readFileSync(
  path.join(repoRoot, "resources", "public", "theme-preload.js"),
  "utf8",
);

test("theme preload installs a narrow Trusted Types policy before application code", () => {
  const policies = [];
  vm.runInNewContext(themePreloadSource, {
    trustedTypes: {
      createPolicy(name, rules) {
        policies.push({ name, rules });
      },
    },
    localStorage: { getItem: () => null },
    document: { documentElement: { dataset: {} } },
  });

  assert.equal(policies.length, 1);
  assert.equal(policies[0].name, "default");
  assert.deepEqual(Object.keys(policies[0].rules), ["createHTML", "createScriptURL"]);

  const { createHTML } = policies[0].rules;
  const svgMatch = themePreloadSource.match(/var tradingViewAttributionSvg = '([^']+)'/);
  assert.ok(svgMatch, "reviewed TradingView attribution SVG must remain explicit");
  const chartSource = fs.readFileSync(
    path.join(repoRoot, "node_modules", "lightweight-charts", "dist", "lightweight-charts.production.mjs"),
    "utf8",
  );
  const vendorSvgStart = chartSource.indexOf('<svg xmlns="http://www.w3.org/2000/svg"');
  const vendorSvgEnd = chartSource.indexOf("</svg>", vendorSvgStart);
  assert.notEqual(vendorSvgStart, -1, "Lightweight Charts attribution SVG must remain discoverable");
  assert.notEqual(vendorSvgEnd, -1, "Lightweight Charts attribution SVG must be complete");
  assert.equal(svgMatch[1], chartSource.slice(vendorSvgStart, vendorSvgEnd + 6));
  assert.equal(createHTML(""), "");
  assert.equal(createHTML(svgMatch[1]), svgMatch[1]);
  assert.throws(() => createHTML(`${svgMatch[1]} `), /Unapproved HTML assignment blocked/);
  assert.throws(() => createHTML('<img src=x onerror="alert(1)">'), /Unapproved HTML assignment blocked/);

  const { createScriptURL } = policies[0].rules;
  const fingerprint = "0123456789ABCDEF0123456789ABCDEF";
  assert.equal(
    createScriptURL(`/js/trade_chart.${fingerprint}.js`),
    `/js/trade_chart.${fingerprint}.js`,
  );
  for (const rejected of [
    `/js/main.${fingerprint}.js`,
    `/js/unknown_module.${fingerprint}.js`,
    `/js/trade_chart.${fingerprint.toLowerCase()}.js`,
    `/js/trade_chart.${fingerprint}.js?override=1`,
    `https://example.invalid/js/trade_chart.${fingerprint}.js`,
    `/js/../trade_chart.${fingerprint}.js`,
  ]) {
    assert.throws(() => createScriptURL(rejected), /Unapproved script URL assignment blocked/);
  }

  assert.match(themePreloadSource, /createHTML/);
  assert.match(themePreloadSource, /throw new TypeError/);
  assert.doesNotMatch(themePreloadSource, /createScript\s*:/);
});

function preloadedTheme(storedTheme) {
  const dataset = {};
  vm.runInNewContext(themePreloadSource, {
    document: { documentElement: { dataset } },
    localStorage: { getItem: () => storedTheme },
  });
  return dataset.theme;
}

function preloadedThemeAllowlist() {
  const match = themePreloadSource.match(
    /\bvar\s+allowedThemes\s*=\s*(\[[^;]*\])\s*;/u,
  );
  assert.ok(
    match,
    "theme-preload.js must declare an allowedThemes array",
  );
  return JSON.parse(match[1]);
}

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

test("pre-paint theme restore only applies ids from the theme catalog", () => {
  for (const id of palette.themeOrder) {
    assert.equal(
      preloadedTheme(id),
      id,
      `theme-preload.js must accept catalog theme "${id}"`,
    );
  }

  for (const unknownId of ["not-a-theme", "future-theme"]) {
    assert.equal(
      preloadedTheme(unknownId),
      undefined,
      `theme-preload.js must reject unknown theme "${unknownId}"`,
    );
  }
});

test("pre-paint theme allowlist matches the canonical palette catalog", () => {
  assert.deepEqual(
    preloadedThemeAllowlist(),
    palette.themeOrder,
    "theme-preload.js allowedThemes must match palette themeOrder",
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
