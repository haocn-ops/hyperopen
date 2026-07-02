/** @type {import('tailwindcss').Config} */
const palette = require("./src/styles/themes/palette.js");

// Semantic ho-* utilities (bg-ho-surface, text-ho-text-muted, ...) resolve
// through the --ho-* tokens defined per theme in src/styles/themes/*.css.
const hoColors = Object.fromEntries(
  palette.COLOR_TOKEN_KEYS.map((key) => [
    `ho-${key}`,
    `rgb(var(--ho-${key}) / <alpha-value>)`,
  ]),
);

// daisyui theme per palette entry so base-100/200/300, primary, and component
// classes follow data-theme. The default ("dark") keys keep their historical
// values via palette.js.
const daisyThemes = palette.themeOrder.map((id) => ({
  [id]: {
    ...require("daisyui/src/theming/themes")["dark"],
    ...palette.themes[id].daisy,
  },
}));

module.exports = {
  // Optimizer view grids use arbitrary `grid-cols-[…minmax(0,1fr)…]` column
  // tracks. The values carry commas and parentheses, and Tailwind's JIT in
  // `--watch` mode (npm run css:watch) intermittently DROPS them from the
  // regenerated main.css on an incremental rebuild — leaving `display:grid`
  // with no template, which collapses the grid into a single stacked column.
  // A clean `npm run build` regenerates them fine, so it presents as a
  // dev-only "stale build" with no source defect. Safelisting the exact class
  // strings forces them into every build (incremental or clean) and kills the
  // flakiness with no view-file churn or layout-regression risk. (The
  // *universe* panel — the one that actually shipped broken — instead defines
  // its tracks statically in src/styles/surfaces/optimizer/universe.css.)
  // NOTE: extracted verbatim from src/hyperopen/views/portfolio/optimize/**;
  // if a view changes a `grid-cols-[…]` width, update the matching entry here
  // (a stale entry only degrades dev-watch resilience — prod stays correct).
  safelist: [
    "2xl:grid-cols-[500px_minmax(0,1fr)_320px]",
    "grid-cols-[132px_minmax(0,1fr)]",
    "grid-cols-[58px_minmax(0,1fr)_44px]",
    "grid-cols-[8rem_minmax(0,1fr)_4rem]",
    "grid-cols-[92px_minmax(0,1fr)]",
    "grid-cols-[auto_auto_minmax(0,1fr)]",
    "grid-cols-[minmax(0,1fr)_34px_34px_34px_60px_60px]",
    "grid-cols-[minmax(0,1fr)_92px]",
    "grid-cols-[minmax(0,1fr)_auto]",
    "grid-cols-[minmax(0,1fr)_minmax(0,1.28fr)_minmax(0,0.78fr)]",
    "grid-cols-[minmax(8rem,1.1fr)_repeat(4,minmax(5rem,0.8fr))]",
    "grid-cols-[minmax(8rem,1.1fr)_repeat(8,minmax(5rem,0.75fr))]",
    "grid-cols-[minmax(8rem,1.1fr)_repeat(9,minmax(5rem,0.75fr))]",
    "lg:grid-cols-[260px_minmax(0,1fr)]",
    "lg:grid-cols-[minmax(0,1fr)_auto]",
    "xl:grid-cols-[420px_minmax(0,1fr)]",
    "xl:grid-cols-[82px_minmax(0,1fr)]",
    "xl:grid-cols-[minmax(0,1fr)_380px]",
    "xl:grid-cols-[minmax(420px,7fr)_minmax(0,11fr)_minmax(360px,6fr)]",
    "xl:grid-cols-[minmax(300px,5fr)_minmax(0,12fr)_minmax(360px,7fr)]",
  ],
  content: ["./src/**/*.{cljs,clj}", "./resources/public/**/*.html"],
  theme: {
    extend: {
      fontSize: {
        xs: ["12px", { lineHeight: "16px" }],
        sm: ["12px", { lineHeight: "16px" }],
        m: ["13px", { lineHeight: "17px" }],
      },
      colors: {
        ...hoColors,
        // Legacy trading-* names stay valid as aliases of the tokens.
        "trading-bg": "rgb(var(--ho-bg) / <alpha-value>)",
        "trading-surface": "rgb(var(--ho-bg) / <alpha-value>)",
        "trading-border": "rgb(var(--ho-border) / <alpha-value>)",
        "trading-green": "rgb(var(--ho-buy) / <alpha-value>)",
        "trading-red": "rgb(var(--ho-sell) / <alpha-value>)",
        "trading-text": "rgb(var(--ho-text-hi) / <alpha-value>)",
        "trading-text-secondary":
          "rgb(var(--ho-text-secondary) / <alpha-value>)",
      },
      borderRadius: {
        sm: "var(--ho-radius-sm, 0.125rem)",
        DEFAULT: "var(--ho-radius, 0.25rem)",
        md: "var(--ho-radius-md, 0.375rem)",
        lg: "var(--ho-radius-lg, 0.5rem)",
        xl: "var(--ho-radius-xl, 0.75rem)",
        "2xl": "var(--ho-radius-2xl, 1rem)",
        "3xl": "var(--ho-radius-3xl, 1.5rem)",
      },
      fontFamily: {
        sans: [
          "var(--font-ui)",
          "var(--font-ui-system)",
          "system-ui",
          "sans-serif",
        ],
        mono: ["var(--font-mono)", "ui-monospace", "monospace"],
      },
    },
  },
  plugins: [
    require("daisyui"),
    require("@tailwindcss/forms"),
    require("@tailwindcss/typography"),
  ],
  daisyui: {
    themes: daisyThemes,
    darkTheme: "dark",
    base: true,
    styled: true,
    utils: true,
    prefix: "",
    logs: true,
    themeRoot: ":root",
  },
};
