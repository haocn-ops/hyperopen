# Refine Trading Settings Section Markers And Surface Palette

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md`. The tracked work item for this plan is `hyperopen-548n`; the `bd` issue is historical local tracker context for this plan.

## Purpose / Big Picture

The Trading Settings panel is now structurally cleaner, but two details still dilute brand fit: the section marker dash is too weak to carry hierarchy, and the panel surface color still reads as a safe generic gray instead of an unmistakably Hyperopen trading surface. After this change, the popover should feel more deliberate at a glance: section markers should read as real system cues, and the panel background should sit inside the same dark tinted family as the rest of the product rather than looking like a neutral component-library overlay.

Users should still see the same settings, copy, toggles, and persistence behavior. The visible difference should be a stronger section rhythm and a more native-feeling surface color that better matches the app’s existing dark trading palette.

## Progress

- [x] (2026-03-30 14:21Z) Created and claimed `bd` issue `hyperopen-548n` for the Trading Settings marker-and-surface redesign direction.
- [x] (2026-03-30 14:22Z) Reviewed the current implementation in `/hyperopen/src/hyperopen/views/header/settings.cljs`, the UI policy docs, and the recent Trading Settings refinement work to isolate the remaining design gap.
- [x] (2026-03-30 14:22Z) Authored this active ExecPlan with a concrete recommended redesign direction for the section marker treatment and panel surface palette.
- [x] (2026-03-30 14:27Z) Verified the new active-plan state with `npm run check`.
- [x] (2026-03-30 14:44Z) Captured fresh browser-review evidence on `/trade` through governed browser QA at `375`, `768`, `1280`, and `1440`, confirming the marker and shell changes against the surrounding trading surface.
- [x] (2026-03-30 14:39Z) Implemented the marker redesign in `/hyperopen/src/hyperopen/views/header/settings.cljs`, replacing the hairline dash with a compact baton that carries section hierarchy without adding chrome.
- [x] (2026-03-30 14:39Z) Retinted the panel and sheet shell in `/hyperopen/src/hyperopen/views/header/settings.cljs` from neutral gray to a greener dark surface, with close-button balancing adjusted to match.
- [x] (2026-03-30 14:39Z) Updated deterministic tests in `/hyperopen/test/hyperopen/views/header_view_test.cljs` for the accepted shell tokens.
- [x] (2026-03-30 14:40Z) Reused the existing smallest relevant Playwright Trading Settings seam because browser-visible behavior remained unchanged.
- [x] (2026-03-30 14:44Z) Ran required validation: `npm run check`, `npm test`, `npm run test:websocket`, `npx playwright test tools/playwright/test/trade-regressions.spec.mjs -g "trading settings confirmation toggles respond to visible switch clicks"`, and governed browser QA for `trade-route`.
- [x] (2026-03-30 14:45Z) Updated this plan with final outcomes in preparation for moving it to `/hyperopen/docs/exec-plans/completed/` and closing `hyperopen-548n`.

## Surprises & Discoveries

- Observation: the current section marker already uses the correct accent family, but the treatment is too light to function as hierarchy.
  Evidence: `/hyperopen/src/hyperopen/views/header/settings.cljs` renders the marker as `h-px w-3 bg-[#50d2c1]/85`, which reads more like a hairline garnish than a structural signal in screenshots.

- Observation: the panel surface is internally consistent but still slightly detached from the app’s broader tinted-dark trading surfaces.
  Evidence: the popover shell in `/hyperopen/src/hyperopen/views/header/settings.cljs` uses `bg-[#1c2328]`, while adjacent trading accents and confirmation surfaces already lean into greener blue-charcoal values such as `#182126`, `#123d37`, and `#2d7468`.

- Observation: the repository already exposes a canonical trading-surface base color that is darker and less neutral than the current popover shell.
  Evidence: `/hyperopen/tailwind.config.js` defines `HYPERLIQUID_BG = "#0f1a1f"` and maps it to both `trading-bg` and `trading-surface`.

- Observation: the remaining work is visual tuning, not a component architecture problem.
  Evidence: the current panel already has flattened grouping, stable `data-role` anchors, passing deterministic tests, committed Playwright coverage, and governed browser-QA precedent from the previous Trading Settings pass.

- Observation: the managed browser-review runner passes cleanly once the stale `shadow-cljs` watcher is terminated and the tool is allowed to start its own local app session.
  Evidence: the first QA attempt failed with `shadow-cljs already running in project on http://localhost:9630`, while the final run passed as `design-review-2026-03-30T14-43-46-088Z-a4ecad46` with `reviewOutcome: "PASS"`.

## Decision Log

- Decision: treat this as a focused redesign pass with exactly two visual targets: section markers and panel surface palette.
  Rationale: the current panel already solved its structural and typographic problems well enough; broadening scope again would risk unnecessary churn and dilute the review signal.
  Date/Author: 2026-03-30 / Codex

- Decision: replace the current 1-pixel hairline dash with a compact accent baton rather than removing the marker entirely.
  Rationale: the section labels still benefit from a small left-hand signal, but the signal needs more physical weight. A short, thicker baton preserves the existing rhythm while making the marker feel deliberate and technical.
  Date/Author: 2026-03-30 / Codex

- Decision: move the panel background away from neutral gray toward a slightly tinted green-charcoal surface, while keeping one dominant plane and conservative contrast.
  Rationale: the issue is brand identity, not brightness. A subtle tint shift toward the repo’s canonical trading-surface family anchored by `#0f1a1f` should make the popover feel more native to the trading workspace without introducing a second accent story or reducing text legibility.
  Date/Author: 2026-03-30 / Codex

- Decision: keep the redesign inside the existing component and validation seams.
  Rationale: this is a shell-level art-direction pass. The work should stay mostly within `/hyperopen/src/hyperopen/views/header/settings.cljs` and reuse the existing view test, Playwright toggle seam, and browser QA route.
  Date/Author: 2026-03-30 / Codex

## Outcomes & Retrospective

This redesign landed in `/hyperopen/src/hyperopen/views/header/settings.cljs` and `/hyperopen/test/hyperopen/views/header_view_test.cljs`. The section marker now reads as a compact baton instead of a hairline garnish, and the panel shell now sits in a greener `#132026` family that feels closer to Hyperopen’s trading-surface palette while preserving readable contrast and the existing interaction model.

The result is a noticeably stronger first-glance shell without reopening layout or behavioral scope. The change reduced perceived genericness with very little code churn because it stayed inside the existing render/test seams rather than adding new structure.

Validation results:

- `npm test` passed: 2898 tests, 15548 assertions, 0 failures.
- `npm run test:websocket` passed: 429 tests, 2434 assertions, 0 failures.
- `npx playwright test tools/playwright/test/trade-regressions.spec.mjs -g "trading settings confirmation toggles respond to visible switch clicks"` passed.
- `npm run check` passed.
- `npm run qa:design-ui -- --targets trade-route --manage-local-app` passed with `reviewOutcome: "PASS"` and artifact root `/Users/barry/.codex/worktrees/c285/hyperopen/tmp/browser-inspection/design-review-2026-03-30T14-43-46-088Z-a4ecad46`.

## Context and Orientation

The Trading Settings popover is rendered in `/hyperopen/src/hyperopen/views/header/settings.cljs`. This file owns the panel shell, title row, section labels, row layout, toggle styling, confirmation strip styling, and both the desktop popover and mobile sheet surfaces. It is the primary implementation target for this redesign.

The settings content model lives in `/hyperopen/src/hyperopen/views/header/vm.cljs`. That file decides which settings rows exist, their titles and helper copy, and what actions fire when toggles change. This redesign should not require changes there unless a visual simplification unexpectedly makes a row-level icon or label treatment obsolete.

The deterministic render contract lives in `/hyperopen/test/hyperopen/views/header_view_test.cljs`. That test already verifies the Trading Settings shell and section presence using `data-role` hooks and selected class tokens. If the redesign settles on new accepted shell classes for the marker or surface, the test should be updated to match the new contract rather than left to assert stale visual tokens.

Committed browser behavior coverage already exists in `/hyperopen/tools/playwright/test/trade-regressions.spec.mjs`. That test verifies that the confirmation toggles respond to visible switch clicks and persist state correctly. Since this redesign is visual, the expectation is that this existing browser seam stays sufficient unless a new interaction path is introduced, which should be avoided.

Governed browser QA for this surface runs through `npm run qa:design-ui -- --targets trade-route --manage-local-app`. Because the user’s concern is explicitly visual, that review is the acceptance bar for the final redesign.

## Design Thesis

Visual thesis: turn the section marker from a whisper into a precision instrument cue, and turn the panel shell from neutral gray into a subtle green-charcoal surface that feels cut from the same material as the trading workspace.

Content plan: keep the current title, section labels, helper copy, toggles, and footer note unchanged. The redesign should come entirely from layout weight, marker geometry, and surface tint rather than new copy or new UI elements.

Interaction thesis: preserve the existing motion and toggle behavior. The redesign should improve visual fit while the component is at rest, not add additional movement or ornamental states.

## Plan of Work

Start by capturing a fresh before-state on `/trade` at the required browser-QA widths. The review should compare the Trading Settings shell directly against surrounding trading surfaces, with explicit notes on whether the section marker reads as a real navigational cue and whether the panel background feels neutral compared with nearby surfaces. This before-state is important because the redesign is subtle and needs a direct comparison point.

Then revise the section marker in `/hyperopen/src/hyperopen/views/header/settings.cljs`. The current marker is a 1-pixel by 12-pixel accent line. Replace it with a compact baton treatment that is visually stronger but still restrained. The recommended direction is a short horizontal accent block with more weight than a hairline: roughly 8 to 10 pixels wide, 2 pixels tall, with either square ends or only the slightest rounding. Keep it left of the section label, keep the same accent family, and do not add decorative glow, gradients, or a second color. The label should remain uppercase and technical, but the marker should now feel like an intentional system tick rather than decoration.

After the marker change, retint the panel shell in the same file. The current background `#1c2328` is acceptable but too neutral. Shift the dominant panel surface toward the app’s canonical trading-surface family anchored by `#0f1a1f`, while stopping short of making the popover blend into the background behind it. The recommended direction is a subtle tint shift, not a visible theme change. The panel should remain one calm plane, and any dependent surfaces such as the close button background, divider lines, or footer separator should be rebalanced only as much as needed to preserve contrast and hierarchy.

Do not introduce new card shells, new shadows, or louder accent moments to compensate. The acceptance target is not “more styling.” It is a cleaner relationship between hierarchy and material. If the stronger marker makes any divider feel redundant, reduce the divider emphasis rather than adding more chrome.

Once the new shell direction is stable, update `/hyperopen/test/hyperopen/views/header_view_test.cljs` if the accepted visual shell contract changes. Keep the assertions focused on the shell-level tokens that matter: the section marker treatment and the popover surface tokens. Avoid overfitting the test to every minor class if the design may continue to evolve slightly.

Finally, rerun the existing smallest relevant Playwright seam and the governed browser QA flow on `trade-route`. The browser QA pass should explicitly confirm that the marker now reads clearly at mobile and desktop widths and that the surface tint feels connected to the rest of the app without making the panel muddy or reducing readability.

## Concrete Steps

From `/Users/barry/.codex/worktrees/c285/hyperopen`:

1. Capture a fresh visual baseline:

       npm run qa:design-ui -- --targets trade-route --manage-local-app

   Record the artifact path and note how the current marker and panel background compare with adjacent trading surfaces.

2. Edit the Trading Settings shell:

       /Users/barry/.codex/worktrees/c285/hyperopen/src/hyperopen/views/header/settings.cljs

   Implement the accepted marker baton treatment and the subtler tinted-dark panel surface. Keep behavior, copy, and `data-role` anchors stable unless the plan is updated with a justified exception.

3. Update deterministic shell assertions if needed:

       /Users/barry/.codex/worktrees/c285/hyperopen/test/hyperopen/views/header_view_test.cljs

4. Reuse the existing browser seam:

       npx playwright test tools/playwright/test/trade-regressions.spec.mjs -g "trading settings confirmation toggles respond to visible switch clicks"

5. Run required validation:

       npm run check
       npm test
       npm run test:websocket
       npx playwright test tools/playwright/test/trade-regressions.spec.mjs -g "trading settings confirmation toggles respond to visible switch clicks"
       npm run qa:design-ui -- --targets trade-route --manage-local-app

6. Update this plan with outcomes and move it to `completed/` after acceptance.

## Validation and Acceptance

The redesign is accepted only if all of the following are true.

The section marker is visibly stronger than the current hairline treatment at both mobile and desktop review widths, while still feeling restrained and native to a trading UI.

The panel background is no longer a flat neutral gray and instead sits in the same tinted-dark material family as the rest of the app, without reducing text contrast or muddying the toggle accents.

The component still exposes the same settings, helper copy, toggle behavior, persistence, dismissal behavior, and keyboard/focus affordances as the current implementation.

The deterministic view test passes with any intentionally updated shell assertions, the existing smallest Playwright seam still passes, and governed browser QA on `trade-route` explicitly confirms the final marker and surface treatment at the required viewports.

## Idempotence and Recovery

This redesign should be implemented as safe visual iterations inside the existing shell. If the first marker or surface tint feels louder, more decorative, or less legible than the current version, back out only that styling attempt and keep the previous stable shell intact while trying the next restrained variant.

The browser QA flow is safe to rerun. If the QA command fails because of local app startup state rather than product behavior, record the failure honestly in this plan and in `bd` rather than weakening the visual acceptance bar.

## Artifacts and Notes

Expected artifacts for the eventual implementation:

- governed browser-review output under `/Users/barry/.codex/worktrees/c285/hyperopen/tmp/browser-inspection/**`
- any related multi-agent notes under `/Users/barry/.codex/worktrees/c285/hyperopen/tmp/multi-agent/hyperopen-548n/**`

Recommended exact-agent roster for implementation:

- `ui_designer` to judge the final baton geometry and panel tint restraint before implementation locks.
- `browser_debugger` to capture before/after evidence and run governed browser QA on `/trade`.
- `worker` to implement the shell changes in `/Users/barry/.codex/worktrees/c285/hyperopen/src/hyperopen/views/header/settings.cljs`.
- `reviewer` to verify that the final shell/test contract does not miss regressions or overfit brittle class assertions.
- `ui_visual_validator` for final read-only brand and palette validation.

## Interfaces and Dependencies

No new dependency should be introduced for this redesign. Reuse the existing header settings render path, action vectors, `data-role` anchors, deterministic test harness, committed Playwright seam, and Browser MCP design-review tooling.

The following interfaces must remain stable unless this plan is updated to say otherwise:

- `/Users/barry/.codex/worktrees/c285/hyperopen/src/hyperopen/views/header/settings.cljs` remains the source of truth for Trading Settings shell styling and section-marker rendering.
- `/Users/barry/.codex/worktrees/c285/hyperopen/src/hyperopen/views/header/vm.cljs` remains the source of truth for row content and toggle actions.
- Existing `data-role` anchors remain stable for tests and browser tooling.
- Existing toggle behavior and browser persistence semantics remain unchanged.

Plan revision note: 2026-03-30 14:22Z - Created the initial active ExecPlan after the post-refinement review identified two remaining brand-alignment gaps: the underpowered section marker and the slightly generic panel surface color.
