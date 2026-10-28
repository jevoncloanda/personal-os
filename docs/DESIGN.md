---
name: Personal OS Android
description: Sky-blue atmosphere, softly layered panels and clear native financial typography.
colors:
  light-primary: "#2462C3"
  light-on-primary: "#FFFFFF"
  light-primary-container: "#D5E8FF"
  light-on-primary-container: "#103566"
  light-secondary: "#4A6283"
  light-on-secondary: "#FFFFFF"
  light-secondary-container: "#E0EBF7"
  light-on-secondary-container: "#263E59"
  light-surface: "#F6FAFE"
  light-on-surface: "#172533"
  light-background: "#E8F3FC"
  light-on-background: "#172533"
  light-surface-container: "#EDF4FC"
  light-surface-container-low: "#F3F8FE"
  light-surface-container-lowest: "#FFFFFF"
  light-surface-container-high: "#E4EFF9"
  light-surface-container-highest: "#DCE9F7"
  light-surface-bright: "#F6FAFE"
  light-surface-dim: "#D7E5F2"
  light-surface-variant: "#E0EBF5"
  light-on-surface-variant: "#43576A"
  light-outline: "#718599"
  light-outline-variant: "#C2D4E5"
  light-error: "#AE302E"
  light-on-error: "#FFFFFF"
  light-error-container: "#FFDAD5"
  light-on-error-container: "#46120F"
  light-sky: "#B2DDF8"
  light-mist: "#E8F3FC"
  light-ground: "#F2F5FC"
  light-panel: "#F0F7FD"
  light-positive: "#176847"
  light-panel-border: "rgba(255, 255, 255, 0.72)"
  dark-primary: "#A8CDFF"
  dark-on-primary: "#003163"
  dark-primary-container: "#214B7F"
  dark-on-primary-container: "#DAEBFF"
  dark-secondary: "#BCD0EA"
  dark-on-secondary: "#203750"
  dark-secondary-container: "#304861"
  dark-on-secondary-container: "#DDEBFA"
  dark-surface: "#12243A"
  dark-on-surface: "#EAF2FD"
  dark-background: "#0B1829"
  dark-on-background: "#EAF2FD"
  dark-surface-container: "#1C3049"
  dark-surface-container-low: "#162940"
  dark-surface-container-lowest: "#0B1829"
  dark-surface-container-high: "#263C56"
  dark-surface-container-highest: "#304760"
  dark-surface-bright: "#344C66"
  dark-surface-dim: "#0B1829"
  dark-surface-variant: "#30455C"
  dark-on-surface-variant: "#BACCDD"
  dark-outline: "#899FB6"
  dark-outline-variant: "#3A526C"
  dark-error: "#FFB4A9"
  dark-on-error: "#690005"
  dark-error-container: "#73312E"
  dark-on-error-container: "#FFDAD5"
  dark-sky: "#173E66"
  dark-mist: "#11273F"
  dark-ground: "#0B1829"
  dark-panel: "#1C334D"
  dark-positive: "#90DAB7"
  dark-panel-border: "rgba(178, 221, 248, 0.14)"
typography:
  display-large:
    fontFamily: "Inter"
    fontSize: "48sp"
    fontWeight: 600
    lineHeight: "56sp"
    letterSpacing: "-1.2sp"
    fontFeature: "tnum"
  display-medium:
    fontFamily: "Inter"
    fontSize: "42sp"
    fontWeight: 600
    lineHeight: "48sp"
    letterSpacing: "-1.1sp"
    fontFeature: "tnum"
  display-small:
    fontFamily: "Inter"
    fontSize: "36sp"
    fontWeight: 600
    lineHeight: "42sp"
    letterSpacing: "-0.9sp"
    fontFeature: "tnum"
  headline-large:
    fontFamily: "Inter"
    fontSize: "28sp"
    fontWeight: 600
    lineHeight: "34sp"
    letterSpacing: "-0.7sp"
    fontFeature: "tnum"
  headline-medium:
    fontFamily: "Inter"
    fontSize: "24sp"
    fontWeight: 600
    lineHeight: "30sp"
    letterSpacing: "-0.5sp"
    fontFeature: "tnum"
  headline-small:
    fontFamily: "Inter"
    fontSize: "22sp"
    fontWeight: 600
    lineHeight: "28sp"
    letterSpacing: "-0.4sp"
    fontFeature: "tnum"
  title-large:
    fontFamily: "Inter"
    fontSize: "20sp"
    fontWeight: 600
    lineHeight: "26sp"
    letterSpacing: "-0.3sp"
    fontFeature: "tnum"
  title-medium:
    fontFamily: "Inter"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "22sp"
    letterSpacing: "-0.2sp"
    fontFeature: "tnum"
  title-small:
    fontFamily: "Inter"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
    letterSpacing: "-0.1sp"
    fontFeature: "tnum"
  body-large:
    fontFamily: "Inter"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
    letterSpacing: "0sp"
  body-medium:
    fontFamily: "Inter"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
    letterSpacing: "0sp"
  body-small:
    fontFamily: "Inter"
    fontSize: "12sp"
    fontWeight: 400
    lineHeight: "17sp"
    letterSpacing: "0.05sp"
  label-large:
    fontFamily: "Inter"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
    letterSpacing: "-0.1sp"
  label-medium:
    fontFamily: "Inter"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
    letterSpacing: "0.1sp"
  label-small:
    fontFamily: "Inter"
    fontSize: "11sp"
    fontWeight: 500
    lineHeight: "16sp"
    letterSpacing: "0.1sp"
rounded:
  extra-small: "8dp"
  small: "12dp"
  medium: "20dp"
  large: "28dp"
  extra-large: "32dp"
spacing:
  detail-gap: "4dp"
  compact-gap: "8dp"
  row-gap: "12dp"
  content-gap: "16dp"
  panel-padding: "20dp"
  overlay-padding: "24dp"
  column-gap: "40dp"
components:
  panel-light:
    backgroundColor: "{colors.light-panel}"
    textColor: "{colors.light-on-surface}"
    rounded: "{rounded.large}"
    padding: "{spacing.panel-padding}"
  panel-dark:
    backgroundColor: "{colors.dark-panel}"
    textColor: "{colors.dark-on-surface}"
    rounded: "{rounded.large}"
    padding: "{spacing.panel-padding}"
  field:
    rounded: "{rounded.small}"
  ledger-row:
    rounded: "{rounded.medium}"
    padding: "14dp"
  icon-badge:
    rounded: "{rounded.small}"
    size: "36dp"
  money-action:
    rounded: "{rounded.medium}"
    typography: "{typography.label-large}"
    padding: "6dp 8dp"
  choice:
    rounded: "{rounded.small}"
    padding: "12dp 16dp"
  editor-save:
    rounded: "{rounded.medium}"
    padding: "8dp 20dp"
  financial-progress:
    height: "7dp"
---

# Design System: Personal OS Android

## Overview

**Creative North Star: "A clear day for your priorities and your money"**

The approved [sky-blue direction and Phase 1.6 refinement](design/phase-1.5-direction.md) retain a sky-to-mist atmosphere, softly layered panels and clear financial figures. The user-selected [finance reference](design/references/finance-ui-direction.png) supplies the material character; native Android components supply navigation and interaction. Phase 1.6 tightens the same world through bundled Inter, one outline icon family and quieter controls.

The implementation uses opaque tonal panels, translucent borders and restrained native elevation to suggest frost without backdrop blur. Today keeps priorities ahead of a compact budget and domain summaries. Money gives currency-specific balances a stronger hierarchy, then quick actions, selected-month summaries and ledger detail. [PRODUCT.md](PRODUCT.md) points to durable product authority; the product name remains provisional.

**Key Characteristics:**
- Sky-blue light atmosphere and a deliberate deep-navy night scheme.
- Opaque rounded panels, fine borders and restrained native shadows.
- Bundled Inter across the complete Material role scale, with tabular financial figures.
- One locally bundled Lucide outline family across app surfaces.
- Native actions, adaptive navigation and practical scrolling editors.

## Colors

Frontmatter records static semantic colors from `Theme.kt` and the separate `Atmosphere` palette. These native values remain owned by Compose; components consume roles rather than copying literals.

### Primary

**Action Blue / Night Pale Blue:** primary drives Quick Add, actions, progress and icon accents. Matching on-primary roles keep action content readable. Primary-container roles remain available to native controls; shared panels use the dedicated atmosphere panel fill.

### Secondary

**Slate Blue / Pale Slate Blue:** supporting native selections and tonal actions. On Android 12+, enabled wallpaper personalization replaces secondary and paired roles, plus tertiary/on-tertiary. Brand primary, atmosphere, surfaces and financial semantics remain stable.

**Positive Green:** income text/icons and positive badges, always accompanied by meaning in text or icon direction. Warning colors are defined but have no rendered consumer and establish no warning component.

### Neutral

**Sky, Mist and Ground:** vertical background gradient with normalized stops (0, 0.40, 1). Night retains this structure in deep blue. **Panel:** opaque light or navy fill with a translucent border. On-surface ink and quieter on-surface-variant metadata establish hierarchy. Native overlays consume the themed Material surface hierarchy; errors pair explicit text with error/error-container roles.

**The Stable Sky Rule.** Wallpaper personalization affects supporting Material roles; preserve the brand-blue primary and atmosphere palette.

**The Semantic Pair Rule.** Consume Material and atmosphere roles with matching content colors; static swatches are source evidence rather than screen-level literals.

## Typography

**Display, Body and Label Font:** locally bundled Inter, one unmodified variable face with explicit regular, medium and semibold instances (400/500/600) at optical size (14sp). Every Material type role sets family, size, weight, line height and tracking. System glyph fallback remains available. See [font provenance](design/fonts/README.md) and the [SIL OFL 1.1 notice](design/fonts/Inter-OFL.txt); the full notice also ships in APK raw resources. This selection matches the reference's compact grotesk character without claiming to identify its original font.

### Hierarchy

- **displayLarge / displayMedium / displaySmall:** explicit theme roles; displaySmall serves the active-currency balance. The larger roles are defined in the complete scale but have no current screen consumer.
- **headlineLarge:** greeting, monthly spending and account-creation headline; semibold and tightened tracking.
- **headlineMedium / headlineSmall:** remaining-budget figures, foundation and sheet headings; semibold with tabular-number features.
- **titleLarge / titleMedium / titleSmall:** financial amounts, section headings and ledger titles. All enable tabular numbers; titleSmall uses medium weight.
- **bodyLarge / bodyMedium / bodySmall:** row titles, descriptions and metadata. Empty-state titles use medium weight.
- **labelLarge / labelMedium / labelSmall:** native actions, tabs, time labels and supporting labels; labelSmall completes the explicit scale without a current app-level consumer.

Frontmatter records the complete source scale. Display, headline and title roles enable Inter's tabular figures (`tnum`); body and label roles do not add that feature. All metrics use scalable `sp`. Empty-state titles retain a local medium-weight body override; do not turn that override into a separate type scale.

**The Financial Hierarchy Rule.** Reserve displaySmall for the balance header; retain currency and explicit financial meaning beside supporting figures.

## Layout

Today and Money use horizontal insets (20dp) and lower content padding (28dp), inside Scaffold's navigation/system insets. Panels default to padding (20dp) and child gaps (8dp); compact empty priorities and the grouped monthly summary use (16dp). Money's lazy list uses item gaps (10dp) and native horizontally scrolling underline tabs. The compact balance header has no top padding and a small lower gap (4dp).

Shell width (600dp) activates a rail. Today independently changes to equal columns at available content width (720dp), with a gap (40dp); the rail reduces that width. Font scale above (1.3) moves Quick Add above navigation and ledger amounts beneath titles. Money actions share a weighted row at width at least (300dp) and font scale at most (1.15), otherwise wrapping. These are source-defined native rules, not browser breakpoints.

Heading/action pairs wrap. Agenda times reserve at least (58dp). Full-screen editors scroll with padding (20dp), gaps (12dp), IME and Scaffold insets. Sheets scroll; choices use bounded native dropdowns.

Monthly spending and income share a wrapping FlowRow below their period selector, with horizontal/vertical gaps (24dp/12dp) and minimum column widths (140dp/110dp). These are native layout constraints, not a promise that every localized amount fits one line.

## Elevation & Depth

The static Compose gradient supports `FrostedPanel`: opaque fill, fine border (1dp), quiet native shadow elevation (0.5dp). Opaque fills avoid the translucent-shadow artifact observed on Samsung. Translucency remains on surfaces without explicit shadows: ledger fill uses panel alpha (0.60), badge fills use primary alpha (0.09) or positive alpha (0.10). There is no runtime backdrop blur or shipping raster texture.

The compact central Quick Add specifies native FAB elevation at rest/pressed (2dp/4dp). Rail FABs, sheets, dialogs and menus retain Material depth/state behavior. No app-specific motion scale exists; validation uses native scroll animation. Native elevation is not a CSS shadow.

**The Tonal Frost Rule.** Keep elevated panels opaque; create frost through tone, border and restrained native elevation.

## Shapes

The explicit Material shape scale appears in frontmatter. Panels use large corners, ledger rows and Money actions medium corners, fields and badges small corners. Navigation has large top corners; the compact central Quick Add is circular. Save uses medium corners; other native buttons retain component-specific Material shapes. Money sections use underlines rather than outlined pills.

App icons are a [23-icon Lucide outline subset](design/icon-provenance.md): native Compose vectors on a 24-unit grid with rounded caps/joins and a uniform stroke (1.75 units). Back/book vectors auto-mirror; RTL remains unverified. Semantic roles supply tint. Full ISC/Feather notices ship in APK raw resources; no runtime icon library, icon font or extended Material icon dependency is used. Native checkbox, radio and switch glyphs remain Material.

## Components

### Buttons

Filled buttons carry creation/save actions. Compact tonal Money actions carry expense/income/transfer, with minimum height (48dp), medium corners, horizontal/vertical padding (8dp/6dp), icons (18dp) and icon/text gap (4dp). Their opaque panel fill pairs with primary text. Text buttons provide section links; outlined buttons provide choices/routes. Save uses medium corners and horizontal/vertical padding (20dp/8dp). Material supplies focus, pressed and disabled behavior. Busy editors disable controls and show saving text; destructive actions confirm explicitly.

### Chips

Only developer preview choices use FilterChip; their styling is native Material and is not Money section navigation. Debug chips and disclosures are development utilities.

### Cards / Containers

`FrostedPanel` groups priorities, budgets and summaries. `IconBadge` contains a vector (20dp) within a tonal tile (36dp); this supporting tile does not define a smaller interactive target. `LedgerRow` combines badge, title, metadata and currency-aware amount; larger text stacks amounts beneath titles. Empty priorities use one compact row with badge, heading/status and Schedule action. Other empty messages remain concise body copy.

### Inputs / Fields

Full-width labeled outlined fields use small corners and panel fills. Choice controls use start-aligned weighted text, a trailing disclosure vector (18dp), small corners and a minimum height (48dp); dropdowns have bounded height (360dp). Failed Save reveals field errors/supporting help and a visible error-container message when needed. Decimal keyboards retain centralized precision validation. Opening balance includes an explicit negative checkbox. Choice menus, archive switches and discard confirmation remain native. ISO dates and 24-hour time have explicit help. Dirty drafts request discard confirmation; source uses `rememberSaveable`.

### Navigation

Four native labeled destinations remain: Today, Money, Schedule and Library. Compact navigation has content height (72dp) plus system gesture insets, destination vectors (22dp) and a central circular Quick Add target (48dp) with vector (24dp). Active destinations use primary content and a tonal primary indicator (0.10 alpha). Above font scale (1.3), Quick Add becomes a separate labeled action above a taller navigation row (80dp). The existing rail places Quick Add at its top.

Money sections use `PrimaryScrollableTabRow`: Overview, History, Accounts, Budgets and Categories, with native underline indication, no divider, transparent container, no edge padding and minimum tab width (64dp). Selected text uses primary; other text uses on-surface-variant. The scrolling Quick Add sheet uses leading badges, trailing chevrons and native rows at least (56dp) high, opening working Money actions and honestly deferred other domains.

Transparent app bars reveal the atmosphere. Foundation screens, appearance and editors share the theme. `SystemBarAppearance` applies bar contrast only to the focused Activity/Dialog window; theme and focus changes reapply after native focus dispatch through a posted update, so an inactive window cannot overwrite the active window's bars.

### Financial Summaries and Visualization

Balance states currency, active-account count and all-recorded-dates scope. The monthly panel groups period selection with actual spending and income, using bodySmall labels, headlineLarge spending and titleLarge positive income. Budgets retain their separate scope. Category spending uses zero-origin progress bars (5dp), explicit labels/amounts, and disappears without slices. Shared financial progress (7dp) has spent/limit text; overspending adds explicit words and error color. Different currencies stay separate; transfers/forecasts are excluded from spending. No invented trends or AI statistics are components.

## Do's and Don'ts

### Do:

- **Do** preserve stable blue branding and semantic Material/atmosphere roles.
- **Do** use opaque bordered panels over the shared gradient with native elevation.
- **Do** use the bundled Inter Material role scale, tabular financial figures and currency labels.
- **Do** use the shared Lucide outline vectors and retain native Material control state glyphs.
- **Do** preserve native insets, wrapping, scrollable forms and explicit status text.

### Don't:

- **Don't** replace native navigation, Back or vector icons with copied iOS chrome or glyphs.
- **Don't** apply runtime backdrop blur or translucent fills to shared elevated panels.
- **Don't** invent trends, combine currencies or present forecasts as recorded spending.
- **Don't** imply deferred non-Money workflows save records.

Not canonized: the pre-existing `Foundation screen` eyebrow is a craft-floor defect, not a reusable label style; it is outside this documentation-only edit. Debug/sample disclosures remain local status. No hard offset shadow or glyph-icon convention is established. The context loader's legacy PRODUCT.md schema drift remains outside the requested design-record refresh; `init` owns that separate repair.

Evidence: current Theme, AppIcons, VisualSystem, PersonalOsApp, TodayScreen, MoneyOverview, MoneyScreen and MoneyEditors Kotlin sources; approved reference, direction and user brief. Directly inspected final physical Samsung SM-S942B captures: [Money light](screenshots/phase-1.6/money-overview-light.png), [Today dark](screenshots/phase-1.6/today-personal-dark.png) and [Money at app-local 1.5x text scale](screenshots/phase-1.6/large-text-money.png). These confirm compact controls, grouped monthly comparison, quiet panels, outline navigation, readable dark bars and the large-text wrapping/labeled Quick Add path. Phone settings were not changed. The [Phase 1.6 handover](phase-1.6-handover.md) records the full physical capture set, build/lint gates, JVM/device results and measured cold startup; [independent review](design/phase-1.6-review.md) records the bounded rendered inspection. Captures include retained disposable regression records, not product seed content. No 2x text, tablet/foldable, arbitrary wallpaper contrast, TalkBack, RTL or general performance certification is made. The app bundles the licensed font and native vector paths; reference and captures remain documentation evidence, not shipping raster assets. Browser CSS detection and HTML/CSS component replicas are inapplicable to this native system.
