---
name: Poros Android
description: Native offline personal organization in Obsidian & Electric Indigo.
colors:
  dark-primary: "#C7BFFF"
  dark-primary-container: "#8D7FFF"
  dark-on-primary: "#29009F"
  dark-canvas: "#0D0E16"
  dark-surface: "#12131B"
  dark-panel: "#1E1F28"
  dark-container-low: "#1A1B23"
  dark-container-high: "#292932"
  dark-container-highest: "#33343D"
  dark-text: "#E3E1ED"
  dark-muted: "#C8C4D7"
  dark-border: "#353443"
  dark-secondary: "#B8AFFF"
  dark-secondary-container: "#473C92"
  dark-positive: "#66DBB3"
  dark-warning: "#F0CC84"
  dark-error: "#FFB4AB"
  light-primary: "#4648D4"
  light-primary-container: "#E4DFFF"
  light-on-primary: "#FFFFFF"
  light-canvas: "#FAF8FF"
  light-panel: "#FFFFFF"
  light-container: "#F2F0FA"
  light-container-low: "#F6F3FF"
  light-container-high: "#EBE8F5"
  light-container-highest: "#E4E0EF"
  light-text: "#131B2E"
  light-muted: "#585466"
  light-border: "#E3DFEE"
  light-secondary: "#62549B"
  light-secondary-container: "#EEE9FF"
  light-positive: "#006C50"
  light-warning: "#805400"
  light-error: "#BA1A1A"
typography:
  display-small: { fontFamily: Inter, fontSize: 36sp, fontWeight: 600, lineHeight: 42sp, letterSpacing: -0.9sp, fontFeature: tnum }
  headline-large: { fontFamily: Inter, fontSize: 28sp, fontWeight: 600, lineHeight: 34sp, letterSpacing: -0.7sp, fontFeature: tnum }
  headline-medium: { fontFamily: Inter, fontSize: 24sp, fontWeight: 600, lineHeight: 30sp, letterSpacing: -0.5sp, fontFeature: tnum }
  headline-small: { fontFamily: Inter, fontSize: 22sp, fontWeight: 600, lineHeight: 28sp, letterSpacing: -0.4sp, fontFeature: tnum }
  title-large: { fontFamily: Inter, fontSize: 20sp, fontWeight: 600, lineHeight: 26sp, letterSpacing: -0.3sp, fontFeature: tnum }
  title-medium: { fontFamily: Inter, fontSize: 16sp, fontWeight: 600, lineHeight: 22sp, letterSpacing: -0.2sp, fontFeature: tnum }
  title-small: { fontFamily: Inter, fontSize: 14sp, fontWeight: 500, lineHeight: 20sp, letterSpacing: -0.1sp, fontFeature: tnum }
  body-large: { fontFamily: Inter, fontSize: 16sp, fontWeight: 400, lineHeight: 24sp, letterSpacing: 0sp }
  body-medium: { fontFamily: Inter, fontSize: 14sp, fontWeight: 400, lineHeight: 20sp, letterSpacing: 0sp }
  body-small: { fontFamily: Inter, fontSize: 12sp, fontWeight: 400, lineHeight: 17sp, letterSpacing: 0.05sp }
  label-large: { fontFamily: Inter, fontSize: 14sp, fontWeight: 500, lineHeight: 20sp, letterSpacing: -0.1sp }
  label-medium: { fontFamily: Inter, fontSize: 12sp, fontWeight: 500, lineHeight: 16sp, letterSpacing: 0.1sp }
  label-small: { fontFamily: Inter, fontSize: 11sp, fontWeight: 500, lineHeight: 16sp, letterSpacing: 0.1sp }
rounded:
  extra-small: 8dp
  small: 12dp
  medium: 16dp
  large: 16dp
  extra-large: 24dp
spacing:
  detail: 4dp
  compact: 8dp
  row: 12dp
  section: 16dp
  content: 20dp
  wide: 24dp
components:
  card-dark: { backgroundColor: "{colors.dark-panel}", textColor: "{colors.dark-text}", rounded: "{rounded.large}", padding: "{spacing.content}" }
  card-light: { backgroundColor: "{colors.light-panel}", textColor: "{colors.light-text}", rounded: "{rounded.large}", padding: "{spacing.content}" }
  editor-save: { rounded: "{rounded.medium}", height: 52dp, typography: "{typography.label-large}" }
  field: { rounded: "{rounded.small}" }
  ledger-row: { rounded: "{rounded.medium}", padding: 14dp }
  icon-badge: { rounded: "{rounded.small}", size: 36dp }
---

# Poros Android Design System

## Overview

**Creative North Star — Obsidian & Electric Indigo**

Poros uses charcoal tonal layers, restrained indigo actions, compact information groups and clear financial figures. Light mode carries the same shared native Compose components onto a pale canvas with white panels.

**Key Characteristics**

- Signature dark appearance with paired light semantic roles.
- Flat rounded panels and bundled Inter/Lucide assets.
- Native Material 3 navigation, controls, sheets and editors.

Approved dark screens lead visual decisions, followed by light screens and consolidated reference prose. The extracted tokens above describe the current implementation. [Reference package](design/poros-stitch/README.md)

## Colors

### Primary

Electric Indigo identifies actions, selection and progress. Dark primary is pale indigo for legibility, with stronger violet in its container role. Light primary supplies the deeper action color.

### Secondary

Violet supporting accents group secondary actions. Positive green identifies income and favorable financial states. Warning and error roles accompany explicit status text.

### Neutral

Obsidian canvas and charcoal containers establish dark hierarchy. Light mode uses a pale canvas, white panels and cool muted text. Fields use shared panel and border roles.

**The Stable Indigo Rule.** Optional Android wallpaper colors customize supporting secondary and tertiary roles while preserving Poros primary, surfaces and financial positive color.

**The Semantic Pair Rule.** Use Material color/content pairs and shared panel roles across both themes.

## Typography

Bundled variable Inter supplies explicit regular, medium and semibold instances with optical size (14sp) and system glyph fallback. The frontmatter records reused screen roles. Theme.kt additionally defines displayLarge (48/56sp) and displayMedium (42/48sp), both semibold with tabular figures.

Display-small serves balances and amount entry. Headlines introduce screens and financial summaries. Titles organize cards and rows. Body text carries descriptions and metadata. Labels serve actions and navigation. Display, headline and title styles enable tabular figures.

**The Financial Meaning Rule.** Retain currency, period and recorded-versus-upcoming meaning beside financial amounts.

## Layout

Screens commonly use horizontal content padding (20dp), section gaps (16dp), row gaps (12dp) and compact gaps (8dp). PorosCard defaults to content padding (20dp) with internal gaps (8dp). Compact card variants use (16dp).

Five destinations remain visible in bottom navigation. The shell switches to a rail at (600dp). Today uses two columns at (720dp). Money actions use a weighted row at widths of at least (300dp) with font scale at most (1.15), then wrap. Above font scale (1.3), ledger amounts stack and Quick Add tiles become one column. Native insets, scrolling and IME padding protect editor content and actions.

At font scale at most (1.15), Library groups Media, Status and Sort into a horizontally scrollable compact toolbar, and Settings groups System, Light and Dark into native segmented buttons. Larger text uses stacked full-width Library choices and Settings radio rows.

## Elevation & Depth

PorosCard has zero native shadow elevation. Tonal contrast separates panels from the solid canvas. Bottom navigation has zero tonal elevation. Floating actions, sheets, dialogs and control feedback retain native Material behavior.

**The Tonal Layer Rule.** Establish hierarchy through shared canvas and container roles with flat resting cards.

## Shapes

The shared shape scale is defined in the frontmatter. Cards and primary editor actions use medium/large corners. Fields and icon badges use small corners. Material supplies pill silhouettes for relevant controls and overlay geometry.

The Poros mark uses a central core and segmented orbit. Standalone native vectors supply adaptive and monochrome launcher assets.

## Components

- **Cards and rows** use PorosCard and LedgerRow. Ledger rows merge semantics, carry a leading badge, and pair title/amount with muted detail.
- **Buttons** use Material filled, tonal, outlined and text variants. Editor Save is full-width with minimum height (52dp). Money actions retain minimum height (48dp), with Expense using primary fill and its paired content color.
- **Fields** use rounded Material controls with semantic focus/error states. Reminder and event titles use prominent transparent fields. Compact date/time trigger rows open native pickers. Amount input uses display-small and a decimal keyboard. Technical date input is available inside the picker when needed.
- **Navigation** uses five labeled destinations, outline icons (22dp), primary selected content and a primary indicator at (12% alpha). A separate floating Quick Add opens the native bottom sheet. Subscriptions remain reachable through Money and related dashboard links.
- **Quick Add** emphasizes Expense, Reminder and Event tiles, followed by Income, Transfer, Subscription and Library routes. Tiles wrap with larger text. The scrolling sheet uses surface-container-low.
- **Editors** use full-screen native dialogs with themed app bars, scrolling fields, IME handling and a bottom Save action. More options summarizes active optional values and preserves collapsed draft state. Validation errors reveal its fields. Saved-record notification timing is available there after draft changes are saved. Read-only transaction detail exposes a separate edit action.
- **Progress** uses primary for budget consumption and error for overspending, with explicit amounts/status. Shared financial tracks are (7dp), category tracks (5dp).
- **Summary groups** keep Today's financial heading, budget summary and chooser in one focal card with padding (16dp) and internal gaps (4dp). Its header pairs This month with an ExpandMore IconButton labeled “Choose Today budget” and a Money action. Priorities, upcoming payments and Library use open section layouts with compact empty states. Money previews one overall budget and one category limit, with the complete list available through Manage.
- **Choices and filters** use native menus and tonal selection rows. Compact Choice controls retain minimum height (48dp) with horizontal padding (12dp). Money section tabs wrap to keep every label visible. Schedule groups previous/next date controls and Today in one row, with horizontally scrolling domain and completed-state chips below. Event rows pair the calendar icon directly with the title.
- **System bars** resolve the nearest dialog-window ancestor, falling back to the Activity window. Contrast applies through the owning window decor, with posted reapplication after composition and focus dispatch.
- **Icons** use locally bundled Lucide vectors on a 24-unit grid with rounded (1.75-unit) strokes. Material retains control-state glyphs.

## Do's and Don'ts

### Do

- **Do** consume shared theme roles and Poros primitives for both appearances.
- **Do** use bundled Inter, outline vectors, native focus feedback and accessible labels.
- **Do** retain wrapping, touch targets, system insets and scrollable editors.
- **Do** preserve currency-specific totals and explicit financial and schedule status.

### Don't

- **Don't** introduce repeated glowing borders or excessive decorative effects.
- **Don't** substitute icon-font glyphs for the shared vector family.
- **Don't** turn fictional reference content into product capabilities.

Source evidence is Theme.kt, VisualSystem.kt, PersonalOsApp.kt, QuickAdd.kt, SettingsScreen.kt, MoneyEditors.kt, MoneyOverview.kt, MoneyScreen.kt, TodayScreen.kt, LibraryScreen.kt, ScheduleScreen.kt and AppIcons.kt under `app/src/main/java/com/jevon/personalos/ui/`, plus launcher resources. Native implementation and verification are recorded separately. [Implementation handover](poros-redesign-handover.md)

Not canonized or repaired in this documentation pass — decorative Quick Add subtitle copy remains local to that sheet. Device/reference parity and accessibility verification belong to the implementation review.
