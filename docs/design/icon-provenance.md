# Outline icon subset

Phase 1.6 uses 23 locally bundled Lucide icons, converted to Compose `ImageVector` paths in `AppIcons.kt`. No Lucide runtime or icon-font dependency is installed. The unused Material extended icon dependency was removed.

Source: https://github.com/lucide-icons/lucide/tree/main/icons, retrieved 25 October 2026. Individual sources use `https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/NAME.svg`.

Names: house, wallet, calendar-days, book-open, plus, arrow-left, code, settings-2, refresh-cw, arrow-up-right, arrow-down-left, arrow-left-right, chevron-left, chevron-right, chevron-down, tag, banknote, landmark, trending-up, circle-check, circle, gamepad-2, clapperboard.

Copyright and ISC license, including inherited Feather notices, are bundled in `app/src/main/res/raw/lucide_license.txt`. That full notice accompanies distributions. The only visual adaptation is a uniform 1.75-unit rounded stroke on Lucide's 24-unit grid. SVG rect/circle/line primitives were converted to equivalent paths, with the book's packed SVG arc flags expanded into explicit separators for Compose's parser. Native vector tint uses semantic theme roles. Back/book vectors retain auto-mirroring; this is not a claim of RTL verification.

Use one outlined family across navigation, accounts, categories, transactions, Today, foundation screens and editor actions. Selection is shown with a tonal native navigation indicator and color, rather than mixing an unrelated filled icon family. Native checkbox, switch and radio controls retain Material's state glyphs.
