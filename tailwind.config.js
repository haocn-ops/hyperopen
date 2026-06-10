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
