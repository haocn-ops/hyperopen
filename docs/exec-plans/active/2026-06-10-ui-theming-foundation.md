# UI Theming Foundation

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. Keep it self-contained, update it whenever implementation discoveries change the plan, and leave at least one unchecked progress item while the plan remains active.

## Purpose / Big Picture

Hyperopen currently hardcodes its visual identity (the dark teal "Hyperliquid" look) across three uncoordinated layers: the Tailwind config (`trading-*` hex colors and one daisyui `dark` theme), `src/styles/**` CSS files, and roughly 1,150 arbitrary-value utilities (`bg-[#115046]`, `text-[#f6fefd]`, ...) spread over ~100 view namespaces, plus chart colors embedded in ClojureScript. Two new visual directions exist as mockups (a meme-flavored "HyperDumb" look and a monochrome institutional terminal look), and more are expected. Restyling today means editing every view.

After this plan, Hyperopen has a semantic design-token layer keyed off a single `data-theme` attribute on `<html>`. Adding a new theme means adding one palette entry in `src/styles/themes/palette.js` (colors, chart colors, radii, fonts) plus one ClojureScript catalog row — no view edits. Users switch themes at runtime from the header settings popover, the choice persists in `localStorage`, and the chosen theme is applied pre-paint on reload. The default theme renders pixel-identical to today. The visible proof is the theme picker in Settings switching the whole app (shell, trade surface, order form, tables, daisyui components) between `HyperOpen`, `Institutional`, and `HyperDumb` palettes, and the preference surviving a reload.

Theming scope is visual-token level: colors, typography, radii. Layout, density, copy, and illustration differences shown in mockups are explicitly out of scope for the theme system.

## Context References

Public refs:

- Direct user request on 2026-06-10: make Hyperopen easy to theme so future themes (HyperDumb-style, institutional-style mockups attached) can be introduced; create an execution plan and implement it.

Repo artifacts:

- `/hyperopen/AGENTS.md` requires ExecPlans for multi-file UI work and gates `npm run check`, `npm test`, `npm run test:websocket`.
- `/hyperopen/ARCHITECTURE.md` requires optimizer visual overrides to stay scoped (`src/styles/surfaces/optimizer.css` keeps its own `--o-*` system) and browser persistence to follow `/hyperopen/docs/BROWSER_STORAGE.md` (small synchronous preferences belong in `localStorage`).
- `/hyperopen/docs/THEMING.md` (added by this plan) is the canonical token vocabulary and add-a-theme checklist.
- `tools/styles/main_css_split.test.mjs` asserts the exact `src/styles/main.css` import manifest and must be updated together with new imports.

## Token architecture (the design)

One attribute, three consumers:

1. `data-theme` on `<html>` (already present for daisyui, value `dark`) is the single theme key. Default stays `dark` so existing markup, storage, and daisyui config remain valid.
2. Semantic tokens are CSS custom properties in the `--ho-*` namespace, defined per theme in generated files `src/styles/themes/<id>.css`. Color tokens hold RGB channel triplets (`--ho-bg: 15 26 31`) so Tailwind opacity modifiers keep working (`bg-ho-bg/80`); chart tokens hold complete CSS color strings because ClojureScript chart code passes them to lightweight-charts verbatim; radius and font tokens hold complete CSS values.
3. `src/styles/themes/palette.js` is the single source of truth. Three consumers derive from it:
   - `tools/styles/generate_theme_css.mjs` generates the committed `src/styles/themes/*.css` files (drift-tested by `tools/styles/theme_css_sync.test.mjs`).
   - `tailwind.config.js` derives `ho-*`/`trading-*` color utilities (`rgb(var(--ho-x) / <alpha-value>)`), the themed `borderRadius` scale (`var(--ho-radius-*)` with Tailwind-default fallbacks), and one daisyui theme entry per palette theme, so `base-100/200/300`, `primary`, and component classes follow the active theme.
   - The ClojureScript theme catalog (`hyperopen.ui.theme`) lists ids/labels for the picker (id drift covered by the sync test greping the catalog).

Layered adoption (who reads tokens):

- Tailwind utilities in views: `trading-*` names repointed to tokens (≈580 usages instantly themeable); daisyui utilities already var-driven (≈1,100 usages) follow via per-theme daisyui entries; arbitrary `[#hex]` utilities migrated to semantic `ho-*` utilities by exact-value mapping (pixel-identical under default theme).
- Plain CSS in `src/styles/surfaces/*.css`: hardcoded colors replaced with `rgb(var(--ho-*))` / `rgb(var(--ho-*) / a)`.
- ClojureScript chart code: reads `--ho-chart-*` via `getComputedStyle` with current constants as fallbacks.

Runtime switching follows the existing preference pattern (`data-ui-font`): `localStorage` key `hyperopen-ui-theme`, restore in `startup/init.cljs` critical-UI phase, a pre-paint inline script in `resources/public/index.html` to avoid theme flash, and a standard action/effect pair (`:actions/set-ui-theme` → `:effects/save` + `:effects/local-storage-set` + `:effects/apply-ui-theme`) wired through the registration catalog and arg contracts. The action emits no heavy IO effects, so it stays outside the effect-order/Lean formal surface.

Guardrail: `npm run lint:theme-colors` (babashka, `dev/check_theme_colors.clj`) counts raw color literals per file in views and style surfaces against a committed baseline (`dev/theme_color_baseline.edn`) and fails when any file's count increases. The baseline ratchets down as migration proceeds.

## Progress

- [x] (2026-06-10) Recon: styling layers inventoried — 379 unique arbitrary hex values / 1,154 instances in views, 576 `trading-*` usages, ~1,100 daisyui color-utility usages, chart palettes in `views/trading_chart/utils/*.cljs`, fonts already token-driven via `data-ui-font`.
- [x] (2026-06-10) Wrote this plan and `docs/THEMING.md` (vocabulary + add-a-theme checklist).
- [x] (2026-06-10) Phase 1 — palette source of truth (`src/styles/themes/palette.js`), CSS generator + sync test, generated `themes/{dark,institutional,hyperdumb}.css`, Tailwind/daisyui plumbing, `main.css` manifest + test update. Default theme pixel-identical (token values are exact current hexes).
- [x] (2026-06-10) Phase 2 — `hyperopen.ui.theme` catalog, preference restore (`hyperopen-ui-theme`), startup wiring, pre-paint script (`resources/public/theme-preload.js`), unit tests.
- [x] (2026-06-10) Phase 3 — `:actions/set-ui-theme` + `:effects/apply-ui-theme` contract surface (registration rows, arg contracts, adapters, collaborator deps) + tests.
- [x] (2026-06-10) Phase 4 — Appearance section with theme choice row in header settings popover (`views/header/settings.cljs`, `views/header/vm.cljs`) + vm tests.
- [x] (2026-06-10) Phase 5 — exact-value migration: scripted replacement of 336 mapped `[#hex]` utilities across 56 view namespaces (`tools/styles/migrate_theme_colors.mjs`) + 52 matching test expectations, exact-hex tokenization in style surfaces, chart options/volume/position-marker colors read `--ho-chart-*` through `views/trading_chart/utils/theme_colors.cljs`.
- [x] (2026-06-10) Phase 6 — `lint:theme-colors` ratchet (`dev/check_theme_colors.clj` + `dev/theme_color_baseline.edn`, 1,629 literals across 118 files at baseline), wired into `npm run check`.
- [x] (2026-06-10) Phase 7 — validation gates green (`npm run check` full chain, `npm test` 4,507 tests / 0 failures, `npm run test:websocket` 534 tests / 0 failures) and browser QA on the dev server: default boot pixel-identical (`rgb(15, 26, 31)` body), live switches dark→institutional→hyperdumb via the Appearance picker, hyperdumb survives a full reload through the pre-paint script with `[:ui :theme]` mirrored in state, chart tokens resolve per theme on rebuild, zero console errors, `:actions/set-ui-theme` also verified through the debug dispatch API.
- [ ] Phase 8 (follow-up) — consolidation pass: map the remaining long-tail color literals (near-duplicate grays/teals, `rgba(...)` arbitrary values, inline `:style` colors, modal/staking/referrals/vaults surfaces) onto the token vocabulary with design review via `npm run qa:design-ui`; ratchet baseline toward zero.
- [ ] Phase 9 (follow-up) — live chart re-theme: re-apply chart options on `:effects/apply-ui-theme` so the lightweight-charts instance restyles without an asset/timeframe change; today it re-reads tokens on the next chart rebuild.
- [ ] Phase 10 (follow-up) — theme-aware asset icons/illustrations and per-theme loading shell accents in `resources/public/index.html` if product wants full-bleed branding.

## Surprises & Discoveries

- Observation: daisyui utilities are a bigger surface than `trading-*` (`base-300` 494×, `base-200` 229×, `base-100` 226×, `primary` 185×). Defining per-theme daisyui entries themes ~1,100 usages with zero view edits.
  Evidence: `grep -rho "base-100\|base-200\|base-300\|\bprimary\b" src/hyperopen/views | sort | uniq -c`.
- Observation: the settings actions (`:actions/set-fill-alerts-enabled` etc.) are not in the effect-order policy map, confirming preference actions with projection+persistence effects need no Lean sync.
  Evidence: `src/hyperopen/runtime/effect_order_contract.cljs` covers only heavy-IO actions.
- Observation: top arbitrary hex values are de-facto tokens already (`#f6fefd` 80×, `#8a96a6` 25×, `#1b2429` 23×), so an exact-value mapping migrates ~29% of instances (336 of 1,154) with zero visual change; the long tail (~350 unique values used 1–4×) is a consolidation problem, not a plumbing problem.
  Evidence: `grep -rho '\[#[0-9a-fA-F]*\]' src/hyperopen/views | sort | uniq -c | sort -rn`; `node tools/styles/migrate_theme_colors.mjs --dry-run`.
- Observation: the release pipeline forbids inline `<script>` tags in generated route HTML (CSP `script-src 'self'`), so the pre-paint theme restore must ship as a tracked root asset (`/theme-preload.js`, following the `/sw.js` precedent in `REQUIRED_ROOT_PUBLIC_PATHS`), not as an inline script.
  Evidence: `rewriteAppIndexHtml works against the real tracked app entry` failed on the inline variant; `tools/release-assets/security_headers.mjs` line 78.
- Observation: chart volume colors flowed through three call paths (`data_processing`, `chart_interop` series creation, `transforms` tail updates); converting the shared constants to theme-reading functions required updating every call site, caught by `series_test` equality assertions on the raw function object.
  Evidence: 5 series_test failures showing `#object[...hyperliquid_volume_up_color]` in place of the color string.

## Decision Log

- Decision: key everything off the existing `data-theme` attribute with default id `dark`, rather than introducing a parallel attribute or renaming.
  Rationale: daisyui already switches on `data-theme` with `themeRoot: ":root"`; keeping `dark` means index.html, stored prefs, and daisyui config stay backward compatible.
  Date/Author: 2026-06-10 / Claude
- Decision: tokens are RGB channel triplets for Tailwind colors, full color strings for chart tokens.
  Rationale: channel triplets are required for `<alpha-value>` opacity modifiers; chart code passes colors to lightweight-charts as strings and needs alpha baked in.
  Date/Author: 2026-06-10 / Claude
- Decision: `palette.js` + generated committed CSS + drift test, instead of hand-maintained parallel hex values in Tailwind config and CSS.
  Rationale: mirrors the repo's generated-artifact-with-sync-test idiom (Lean effect-order vectors); prevents the palette drifting across consumers.
  Date/Author: 2026-06-10 / Claude
- Decision: Phase 5 migrates only exact-value matches (token default == replaced hex); near-duplicates stay hardcoded until the Phase 8 consolidation pass.
  Rationale: keeps the default theme pixel-identical so this change is safe to land without full design QA; consolidation changes rendered colors and needs design review.
  Date/Author: 2026-06-10 / Claude
- Decision: optimizer `--o-*` variables stay a scoped sub-system and are not migrated.
  Rationale: ARCHITECTURE.md mandates optimizer visual overrides stay scoped to the optimizer surface.
  Date/Author: 2026-06-10 / Claude
- Decision: theme fonts may override the `data-ui-font` user preference (theme CSS loads later in the cascade).
  Rationale: a theme owns its typography (institutional is mono-first, hyperdumb is display-first); the font preference remains meaningful for the default theme.
  Date/Author: 2026-06-10 / Claude

## Validation

Required gates when code changes (AGENTS.md): `npm run check`, `npm test`, `npm run test:websocket`.

Acceptance expectations for this plan:

- `npm test` (full ClojureScript suite) and `npm run test:websocket` pass with the migrated class expectations.
- `npm run test:styles` proves generated theme CSS, the `main.css` manifest, and the ClojureScript catalog are in sync with `palette.js`.
- `npm run test:release-assets` proves `/theme-preload.js` ships as a declared root asset and route HTML stays free of inline scripts.
- `npm run lint:theme-colors` passes and fails closed when any file gains a raw color literal (self-tested by injecting one).
- `app` and `portfolio` release-config compiles succeed with zero warnings.
- Browser QA on the dev server: Settings → Appearance switches all three themes live (shell, trade surface, order form, tables, daisyui components), the choice persists across reload via `localStorage` + pre-paint script, and the console stays free of new errors.

## Outcomes & Retrospective

Implemented and verified through Phase 7 on 2026-06-10. The default theme renders from tokens with identical values, `Institutional` and `HyperDumb` prove the system end to end (live switch, persistence, pre-paint restore, daisyui + chart coverage), and 1,629 remaining raw color literals across 118 files are enumerated in the ratchet baseline for the Phase 8 consolidation pass. Retrospective: the exact-value-only rule kept the change safe to land without design review, and the registration/contract drift gates made the new action/effect wiring mechanical rather than risky; the release pipeline's inline-script ban was the only genuine surprise. Known limits for follow-ups: a mounted chart restyles on its next rebuild rather than instantly (Phase 9), candlestick body colors are lightweight-charts defaults under every theme (fold into Phase 9), and the settings popover chrome (`ts-*`/`--o-*`) plus long-tail modal/staking/referrals/vaults surfaces keep default-theme colors until Phase 8.

## Context and Orientation

Styling layers and owners after this plan:

- `src/styles/themes/palette.js` — single source of truth for every theme (colors, chart colors, radii, fonts, daisyui mapping). Edit this to change or add a theme.
- `tools/styles/generate_theme_css.mjs` — regenerates `src/styles/themes/*.css`; `tools/styles/theme_css_sync.test.mjs` fails `npm run test:styles` when generated files or the ClojureScript catalog drift.
- `src/styles/themes/*.css` — committed generated token definitions per theme.
- `tailwind.config.js` — derives `ho-*` utilities, `trading-*` aliases, radius scale, daisyui themes from the palette.
- `src/hyperopen/ui/theme.cljs` — theme catalog (ids, labels, normalize) used by preferences, actions, and the picker.
- `src/hyperopen/ui/preferences.cljs` — restore-on-startup for font and theme.
- `src/hyperopen/header/actions.cljs` + `src/hyperopen/runtime/effect_adapters.cljs` — runtime switching (`:actions/set-ui-theme`, `:effects/apply-ui-theme`).
- `src/hyperopen/views/header/{settings,vm}.cljs` — the Appearance picker UI.
- `dev/check_theme_colors.clj` + `dev/theme_color_baseline.edn` — hardcoded-color ratchet (`npm run lint:theme-colors`).
- `docs/THEMING.md` — token vocabulary, naming rules, add-a-theme checklist.
