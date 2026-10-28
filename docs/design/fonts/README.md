# Bundled typography

Inter is the Phase 1.6 interface family. Compared with Plus Jakarta Sans's more geometric construction and Manrope's rounder character, Inter's compact grotesk forms, open counters and distinct financial numerals are closest to the approved finance reference. This is a visual selection, not a claim to identify the reference's original font.

- Official upstream: https://github.com/rsms/inter
- Distributed source: https://github.com/google/fonts/tree/main/ofl/inter
- Download: https://raw.githubusercontent.com/google/fonts/main/ofl/inter/Inter%5Bopsz%2Cwght%5D.ttf
- Retrieved: 25 October 2026.
- Unmodified variable TTF: `app/src/main/res/font/inter_variable.ttf` (876,576 bytes).
- SHA-256: `29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031`.
- License: SIL Open Font License 1.1, Copyright 2020 The Inter Project Authors. Full notice: [Inter-OFL.txt](Inter-OFL.txt), also packaged in the APK as `res/raw/inter_ofl.txt`.

The license allows embedding and redistribution with this notice. The font is not modified, renamed or sold separately. No SF Pro files are used. Compose loads the local resource without network access and explicitly selects weight instances 400, 500 and 600 at optical size 14sp. All Material roles use scalable sp units; financial display/title roles enable `tnum`. System fallback remains available for glyphs outside the bundled face.

The complete role scale is recorded in [DESIGN.md](../../DESIGN.md). Typography is a coordinated change to family, size, weight, tracking and line height; do not introduce isolated per-screen sizes.
