# Poros — Stitch Final Implementation Handoff

This is a curated **design reference package**, not a runnable Android app. Original Stitch exports are not modified. The original ZIP remains the source archive; this clean package omits six clearly incomplete/superseded exports.

## Contents

- **01_DARK_PRIMARY/**: 13 selected dark-mode screens, in suggested implementation order. This is the primary visual source.
- **02_LIGHT_REFERENCE/**: 9 light-mode screens, including three interaction references; these are secondary to approved dark screens where styles conflict.
- **03_DESIGN_SYSTEM/**: all three raw Stitch `DESIGN.md` files retained for traceability. They are **not identical**; reconcile them into a single light/dark Compose token set, starting from the approved dark screenshots and verified contrast, rather than silently merging all values.
- **04_BRANDING/**: Poros logo concept presentation and a *provisional visual-reference crop*. The crop is not a production-ready Android adaptive/monochrome icon and is not transparent. Do not embed the full brand board into a launcher icon.
- **SCREEN_MANIFEST.csv**: maps original Stitch folder names to the curated structure, also lists exclusions.
- **EXCLUDED_EXPORTS.md**: documents the six excluded designs and reasons.
- **GALLERY.html** and **DARK_OVERVIEW.jpg**: quick visual indexes.

## Important implementation boundaries

1. The real app is already in use. **Do not regenerate the app from Stitch HTML.** Translate style/layout to existing Kotlin + Jetpack Compose/Material 3 components while keeping Room data, migrations, backup/restore, transaction accounting, Schedule recurrence, notifications, and Library data intact.
2. Final user-facing app name is **Poros**; retained internal identifiers/package/application database should not be renamed as part of the visual redesign.
3. Some Stitch designs contain mock data, fake accounts, unsupported options, or copy inconsistent with the existing product. **Existing working app behavior and approved requirements outrank Stitch mock text.** In particular, there is no standalone Tasks domain. Do not claim local notes are encrypted just because a mockup says so; do not add unsupported budget reset day or encryption/vault functionality.
4. Keep light and dark variants using shared Compose screens and theme tokens, not duplicated UI implementations. Missing light-mode-specific mocks should be inferred from these shared design tokens, not populated with invented features.
5. Current design gallery does not cover every state (empty/loading/error, every editor, accessibility). Preserve existing working flows and implement needed states in the shared system.
6. Preserve user data. Do not wipe the app, rebrand Android package ID, or introduce an API. Agent should run focused tests and visual/interaction checks on the Samsung; no broad suite repetition for unchanged logic.
7. The logo is a **concept**. Approve the emblem, then prepare standalone clean icon assets (adaptive foreground, background, monochrome) before shipping branding.
8. Keep unrelated working tree changes and any unfinished notification test harness untouched; do not automatically commit or push.

## Scope and selection

The curated archive contains **22 usable screens**: **13 dark** and **9 light**. **Six** incomplete/superseded screenshots are omitted, not replaced or regenerated. The original full Stitch ZIP remains unchanged outside this package.
