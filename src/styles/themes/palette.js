/**
 * Single source of truth for Hyperopen themes.
 *
 * Consumers (never hand-edit the derived artifacts):
 * - tools/styles/generate_theme_css.mjs -> src/styles/themes/<id>.css
 * - tailwind.config.js -> ho-* utilities, trading-* aliases, radius scale, daisyui themes
 * - src/hyperopen/ui/theme.cljs mirrors ids/labels (drift-tested by
 *   tools/styles/theme_css_sync.test.mjs)
 *
 * Token formats: `colors` are hex (emitted as RGB channel triplets for
 * Tailwind alpha support), `chart` are complete CSS color strings (read by
 * ClojureScript at runtime), `radius` are CSS lengths, `fonts` are optional
 * CSS font-family overrides. See docs/THEMING.md.
 */

const TAILWIND_DEFAULT_RADIUS = {
  sm: "0.125rem",
  DEFAULT: "0.25rem",
  md: "0.375rem",
  lg: "0.5rem",
  xl: "0.75rem",
  "2xl": "1rem",
  "3xl": "1.5rem",
};

const COLOR_TOKEN_KEYS = [
  "bg",
  "bg-deep",
  "surface",
  "surface-raised",
  "border",
  "text",
  "text-hi",
  "text-secondary",
  "text-muted",
  "text-dim",
  "accent",
  "accent-hi",
  "accent-bright",
  "accent-soft",
  "accent-soft-hi",
  "border-accent",
  "border-accent-muted",
  "buy",
  "sell",
  "sell-hi",
  "sell-tint",
  "sell-soft",
  "sell-soft-deep",
  "border-sell",
  "warn",
  "info",
];

const CHART_TOKEN_KEYS = [
  "bg",
  "text",
  "grid",
  "grid-strong",
  "grid-soft",
  "border-soft",
  "separator",
  "separator-hover",
  "up",
  "down",
  "long",
  "short",
];

const themes = {
  // Default HyperOpen look. Values are the exact hexes the app used before
  // tokenization; changing one changes the default theme's rendering.
  dark: {
    label: "HyperOpen",
    colors: {
      bg: "#0f1a1f",
      "bg-deep": "#06131a",
      surface: "#1b2429",
      "surface-raised": "#273035",
      border: "#30363d",
      text: "#f6fefd",
      "text-hi": "#ffffff",
      "text-secondary": "#8b949e",
      "text-muted": "#8a96a6",
      "text-dim": "#6f7a88",
      accent: "#50d2c1",
      "accent-hi": "#66e3c5",
      "accent-bright": "#97fce4",
      "accent-soft": "#0d3a35",
      "accent-soft-hi": "#115046",
      "border-accent": "#1f3b3c",
      "border-accent-muted": "#17313d",
      buy: "#00d4aa",
      sell: "#ff6b6b",
      "sell-hi": "#ed7088",
      "sell-tint": "#f2b8c5",
      "sell-soft": "#3a1b22",
      "sell-soft-deep": "#2b1118",
      "border-sell": "#7b3340",
      warn: "#fbbd23",
      info: "#3abff8",
    },
    chart: {
      bg: "rgb(15, 26, 31)",
      text: "#e5e7eb",
      grid: "#374151",
      "grid-strong": "#4b5563",
      "grid-soft": "rgba(139, 148, 158, 0.16)",
      "border-soft": "rgba(139, 148, 158, 0.24)",
      separator: "rgba(139, 148, 158, 0.22)",
      "separator-hover": "rgba(139, 148, 158, 0.30)",
      up: "rgba(34, 171, 148, 0.5)",
      down: "rgba(247, 82, 95, 0.5)",
      long: "#26a69a",
      short: "#ef5350",
    },
    radius: TAILWIND_DEFAULT_RADIUS,
    fonts: {},
    daisy: {
      primary: "#00d4aa",
      secondary: "#8b949e",
      accent: "#ff6b6b",
      neutral: "#0f1a1f",
      "base-100": "#0f1a1f",
      "base-200": "#0f1a1f",
      "base-300": "#30363d",
      info: "#3abff8",
      success: "#00d4aa",
      warning: "#fbbd23",
      error: "#ff6b6b",
    },
  },

  // Monochrome terminal look: near-black, green/red signals, square corners,
  // mono UI type.
  institutional: {
    label: "Institutional",
    colors: {
      bg: "#0b0e11",
      "bg-deep": "#07090c",
      surface: "#11151a",
      "surface-raised": "#181e24",
      border: "#262d35",
      text: "#e6edf3",
      "text-hi": "#ffffff",
      "text-secondary": "#8b98a5",
      "text-muted": "#768390",
      "text-dim": "#545d68",
      accent: "#3fb950",
      "accent-hi": "#56d364",
      "accent-bright": "#aff5b4",
      "accent-soft": "#0f2418",
      "accent-soft-hi": "#143020",
      "border-accent": "#1f3328",
      "border-accent-muted": "#182a20",
      buy: "#3fb950",
      sell: "#f85149",
      "sell-hi": "#ff7b72",
      "sell-tint": "#ffc1c8",
      "sell-soft": "#2d1418",
      "sell-soft-deep": "#1f0c10",
      "border-sell": "#5c2429",
      warn: "#d29922",
      info: "#58a6ff",
    },
    chart: {
      bg: "rgb(11, 14, 17)",
      text: "#c9d1d9",
      grid: "#21262d",
      "grid-strong": "#30363d",
      "grid-soft": "rgba(139, 148, 158, 0.12)",
      "border-soft": "rgba(139, 148, 158, 0.20)",
      separator: "rgba(139, 148, 158, 0.18)",
      "separator-hover": "rgba(139, 148, 158, 0.28)",
      up: "rgba(63, 185, 80, 0.5)",
      down: "rgba(248, 81, 73, 0.5)",
      long: "#3fb950",
      short: "#f85149",
    },
    radius: {
      sm: "0px",
      DEFAULT: "0px",
      md: "0px",
      lg: "0px",
      xl: "0px",
      "2xl": "0px",
      "3xl": "0px",
    },
    fonts: {
      ui: "var(--font-mono)",
    },
    daisy: {
      primary: "#3fb950",
      secondary: "#8b98a5",
      accent: "#f85149",
      neutral: "#0b0e11",
      "base-100": "#0b0e11",
      "base-200": "#0b0e11",
      "base-300": "#262d35",
      info: "#58a6ff",
      success: "#3fb950",
      warning: "#d29922",
      error: "#f85149",
      "--rounded-box": "0",
      "--rounded-btn": "0",
      "--rounded-badge": "0",
      "--tab-radius": "0",
    },
  },

  // Degen neon look: black-green base, vivid green/red signals, chunky
  // rounded chrome.
  hyperdumb: {
    label: "HyperDumb",
    colors: {
      bg: "#0a0d0a",
      "bg-deep": "#050705",
      surface: "#131a13",
      "surface-raised": "#1c261c",
      border: "#2a3a2a",
      text: "#f2fff2",
      "text-hi": "#ffffff",
      "text-secondary": "#9cb39c",
      "text-muted": "#7e957e",
      "text-dim": "#5c705c",
      accent: "#00ff88",
      "accent-hi": "#4dffaa",
      "accent-bright": "#b3ffd9",
      "accent-soft": "#003b22",
      "accent-soft-hi": "#00552f",
      "border-accent": "#14532d",
      "border-accent-muted": "#0e3d22",
      buy: "#00ff88",
      sell: "#ff5252",
      "sell-hi": "#ff7a7a",
      "sell-tint": "#ffc4c4",
      "sell-soft": "#3d1414",
      "sell-soft-deep": "#2a0d0d",
      "border-sell": "#8a3333",
      warn: "#ffd60a",
      info: "#38bdf8",
    },
    chart: {
      bg: "rgb(10, 13, 10)",
      text: "#e8ffe8",
      grid: "#1e2a1e",
      "grid-strong": "#2a3a2a",
      "grid-soft": "rgba(154, 176, 154, 0.16)",
      "border-soft": "rgba(154, 176, 154, 0.24)",
      separator: "rgba(154, 176, 154, 0.22)",
      "separator-hover": "rgba(154, 176, 154, 0.30)",
      up: "rgba(0, 255, 136, 0.5)",
      down: "rgba(255, 82, 82, 0.5)",
      long: "#00d474",
      short: "#ff5252",
    },
    radius: {
      sm: "0.25rem",
      DEFAULT: "0.5rem",
      md: "0.625rem",
      lg: "0.875rem",
      xl: "1.125rem",
      "2xl": "1.5rem",
      "3xl": "2rem",
    },
    fonts: {},
    daisy: {
      primary: "#00ff88",
      secondary: "#9cb39c",
      accent: "#ff5252",
      neutral: "#0a0d0a",
      "base-100": "#0a0d0a",
      "base-200": "#0a0d0a",
      "base-300": "#2a3a2a",
      info: "#38bdf8",
      success: "#00ff88",
      warning: "#ffd60a",
      error: "#ff5252",
      "--rounded-box": "1rem",
      "--rounded-btn": "1rem",
      "--rounded-badge": "1.9rem",
      "--tab-radius": "0.75rem",
    },
  },
};

const themeOrder = ["dark", "institutional", "hyperdumb"];
const defaultTheme = "dark";

function hexToChannels(hex) {
  const normalized = hex.replace("#", "");
  const expanded =
    normalized.length === 3
      ? normalized
          .split("")
          .map((c) => c + c)
          .join("")
      : normalized;
  if (!/^[0-9a-fA-F]{6}$/.test(expanded)) {
    throw new Error(`palette: expected 3/6-digit hex color, got "${hex}"`);
  }
  const r = parseInt(expanded.slice(0, 2), 16);
  const g = parseInt(expanded.slice(2, 4), 16);
  const b = parseInt(expanded.slice(4, 6), 16);
  return `${r} ${g} ${b}`;
}

function assertCompleteTheme(id, theme) {
  for (const key of COLOR_TOKEN_KEYS) {
    if (!theme.colors[key]) {
      throw new Error(`palette: theme "${id}" missing color token "${key}"`);
    }
  }
  for (const key of CHART_TOKEN_KEYS) {
    if (!theme.chart[key]) {
      throw new Error(`palette: theme "${id}" missing chart token "${key}"`);
    }
  }
  for (const key of Object.keys(TAILWIND_DEFAULT_RADIUS)) {
    if (!theme.radius[key]) {
      throw new Error(`palette: theme "${id}" missing radius token "${key}"`);
    }
  }
}

for (const id of themeOrder) {
  if (!themes[id]) {
    throw new Error(`palette: themeOrder lists unknown theme "${id}"`);
  }
  assertCompleteTheme(id, themes[id]);
}

module.exports = {
  themes,
  themeOrder,
  defaultTheme,
  COLOR_TOKEN_KEYS,
  CHART_TOKEN_KEYS,
  hexToChannels,
};
