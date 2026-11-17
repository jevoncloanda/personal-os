---
name: Obsidian Kinetic
colors:
  surface: '#faf8ff'
  surface-dim: '#d2d9f4'
  surface-bright: '#faf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f3ff'
  surface-container: '#eaedff'
  surface-container-high: '#e2e7ff'
  surface-container-highest: '#dae2fd'
  on-surface: '#131b2e'
  on-surface-variant: '#464554'
  inverse-surface: '#283044'
  inverse-on-surface: '#eef0ff'
  outline: '#767586'
  outline-variant: '#c7c4d7'
  surface-tint: '#494bd6'
  primary: '#4648d4'
  on-primary: '#ffffff'
  primary-container: '#6063ee'
  on-primary-container: '#fffbff'
  inverse-primary: '#c0c1ff'
  secondary: '#006591'
  on-secondary: '#ffffff'
  secondary-container: '#39b8fd'
  on-secondary-container: '#004666'
  tertiary: '#6b38d4'
  on-tertiary: '#ffffff'
  tertiary-container: '#8455ef'
  on-tertiary-container: '#fffbff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e1e0ff'
  primary-fixed-dim: '#c0c1ff'
  on-primary-fixed: '#07006c'
  on-primary-fixed-variant: '#2f2ebe'
  secondary-fixed: '#c9e6ff'
  secondary-fixed-dim: '#89ceff'
  on-secondary-fixed: '#001e2f'
  on-secondary-fixed-variant: '#004c6e'
  tertiary-fixed: '#e9ddff'
  tertiary-fixed-dim: '#d0bcff'
  on-tertiary-fixed: '#23005c'
  on-tertiary-fixed-variant: '#5516be'
  background: '#faf8ff'
  on-background: '#131b2e'
  surface-variant: '#dae2fd'
typography:
  display-lg:
    fontFamily: Sora
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.03em
  display-lg-mobile:
    fontFamily: Sora
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Sora
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Sora
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.015em
  headline-sm:
    fontFamily: Sora
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.01em
  title-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0em
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.005em
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-compact: 0.75rem
  margin: 1.25rem
  margin-expanded: 1.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system expresses a high-efficiency, tactile executive management experience optimized for Android. It fuses modern minimalism with kinetic, high-contrast digital precision. The target audience comprises power users, founders, and knowledge workers who demand density, immediate visual orientation, and tactile fluid feedback. 

The aesthetic is characterized by razor-sharp contrast, structural crispness, and electric luminescence. Interfaces should feel lightweight yet grounded: deep slate text anchors crisp off-white and ice-tinted backdrops, while high-voltage indigo sparks indicate actions, states, and telemetry. Every interaction emphasizes swiftness, clarity, and deliberate visual weight.

## Colors

The core palette centers on electric indigo as the kinetic driver for action, focus, and state progression. Secondary sky cyan and tertiary violet serve contextual data visualizations, progress metrics, and dynamic status tags.

### Surface System
- **Base Canvas (`light`):** `#f8fafc` (Slate 50) delivers a clean, glare-free ground.
- **Surface Level 1:** `#ffffff` (Pure White) creates crisp focal contrast for interactive cards.
- **Surface Level 2 (Tinted Accent):** `#eef2ff` (Indigo 50) demarcates active sections, selection containers, and contextual chips.
- **Borders & Dividers:** Subtle cold-tinted borders at `#e0e7ff` (Indigo 100) or `#cbd5e1` (Slate 300 at 50% opacity).

### Typography & Readability
- **Primary Text:** `#0f172a` (Slate 900) ensures near-maximum optical contrast.
- **Secondary Text:** `#475569` (Slate 600) handles metadata and non-critical details.
- **Tertiary/Muted:** `#94a3b8` (Slate 400) supports placeholder copy and disabled indicators.

### Dark Mode Adaptation
When switching to dark mode, the canvas converts to deep obsidian (`#090d16`), elevated surfaces shift to tinted slate layers (`#111827`, `#1e293b`), borders resolve to `#312e81` (Indigo 900 / 40%), and `#818cf8` replaces primary indigo to preserve WCAG AAA perceptual luminous contrast.

## Typography

Typography pairs the structural, geometric presence of **Sora** for headlines and data anchors with the neutral clarity of **Inter** for running copy, lists, and dense UI controls.

- **Headlines & Metric Data (Sora):** Carries structural precision, open apertures, and distinctive modern geometric forms. Used for dashboard figures, section headings, sheet titles, and hero balance readouts.
- **Body & Functional UI (Inter):** Highly legible at minute sizes across Android displays. Maintains neutral optical rhythm during vertical content scans.
- **Labels & Micro-Tags:** Set with tighter weights (`600`) and slight positive tracking to ensure instant legibility on colored chips and input captions.

## Layout & Spacing

The layout is built on an adaptive fluid grid structured around an 8-point base rhythm, downscaled to 4-point steps for micro-spacing and icon alignments.

### Grid & Form Factors
- **Mobile (Compact: < 600dp):** 4-column layout with `1.25rem` (20dp) screen margins and `1rem` (16dp) gutters. Bottom navigation anchor sits flush with safe area inset buffers.
- **Foldables & Tablets (Medium/Expanded: 600dp - 1200dp):** 8 to 12-column layout with `1.5rem` (24dp) margins and a dual-pane master-detail arrangement. Bottom navigation transitions to an ergonomic Material 3 Navigation Rail.
- **Vertical Rhythm:** Stacked cards and list blocks rely on `space-md` (`1rem`) intervals, while sub-groupings maintain compact `space-sm` (`0.5rem`) separations.

## Elevation & Depth

Visual hierarchy does not depend on heavy dropshadows. Instead, this system applies **tonal layer elevation** reinforced by **crisp ambient luminescence** and **hairline indigo borders**.

- **Level 0 (Canvas):** `#f8fafc`. Flat, non-interactive ground.
- **Level 1 (Cards & Modular Tiles):** `#ffffff` layered over canvas, framed by a `1px` stroke of `#e0e7ff`. Shadow: `0px 2px 8px -2px rgba(15, 23, 42, 0.04), 0px 1px 3px -1px rgba(99, 102, 241, 0.06)`.
- **Level 2 (Active/Floating Elements & Drawers):** `#ffffff` with a double shadow structure: `0px 10px 24px -4px rgba(15, 23, 42, 0.08), 0px 4px 12px -2px rgba(99, 102, 241, 0.12)`.
- **Level 3 (Modal Sheets & Snackbars):** `#ffffff` surrounded by an intensified stroke of `#c7d2fe` and a diffuse contact shadow `0px 20px 32px -8px rgba(15, 23, 42, 0.14)`.
- **Interactive Depth (Android Ripple):** Electric indigo fill at 12% opacity (`rgba(99, 102, 241, 0.12)`) expanding from touch origin.

## Shapes

The interface embraces a structured yet soft form language. Primary cards, modular tiles, and modal bottom sheets utilize generous curvature (`rounded-2xl` / 16dp) to create friendly, encapsulated touch zones that conform cleanly to modern Android hardware display curves.

- **Cards, Sheets & Overlays:** Standardized to `1rem` (16dp / `rounded-2xl`).
- **Interactive Controls (Inputs, Action Buttons, Selectors):** Standardized to `0.75rem` (12dp / `rounded-xl`) for tactile finger purchase.
- **Chips, Badges, Navigation Pills:** Fully rounded pill-shapes (`9999px`) for state and category definition.

## Components

### Buttons
- **Primary Kinetic Button:** Deep electric indigo background (`#6366f1`), pure white text, font `label-lg`, height 52dp, rounded-xl (12dp). Pressed state shifts to `#4f46e5` with scale down to 0.98.
- **Tonal / Secondary Button:** Ice-indigo background (`#eef2ff`), indigo text (`#4338ca`), zero border, height 48dp.
- **Ghost / Outlined Button:** Transparent fill, `1.5px` border in `#c7d2fe`, text `#0f172a`.

### Cards & Modules
- Structured container in `#ffffff`, corner radius 16dp, border `1px solid #e0e7ff`. Internal padding set to `1.25rem`. 
- Header section features `headline-sm` in `#0f172a` paired with an optional top-right contextual pill or icon button.

### Material 3 Navigation Bar
- Anchored to bottom edge with native Android navigation inset padding. 
- Background is `#ffffff` with a subtle top border `1px solid #f1f5f9`. 
- Active indicator features an electric indigo pill container (`#eef2ff` or `#6366f1` with high-contrast icon reverse) accompanied by a 12px label in Sora bold.

### Input Fields
- Container height 56dp with background `#f8fafc`, border `1.5px solid #e2e8f0`, corner radius 12dp.
- Focus state activates a distinct `2px` stroke of `#6366f1` alongside an ice-indigo tint fill (`#ffffff`). Label floats cleanly with `label-sm` in `#6366f1`.

### Chips & Interactive Tags
- Height 32dp, shape pill (`9999px`), internal horizontal padding `12px`.
- Inactive: Background `#f1f5f9`, text `#475569`.
- Active / Selected: Background `#eef2ff`, border `1px solid #6366f1`, text `#4338ca`, with a leading checkmark icon.

### Selection Controls (Checkboxes & Radios)
- Checkboxes: 20dp box with 6dp corner radius. Checked state fills with `#6366f1`, displaying a crisp white stroke checkmark.
- Radio Buttons: Concentric circles with active center dot in `#6366f1` and an outer ring in `#c7d2fe`.

### Bottom Action Sheets
- Rounded top corners at 24dp (`rounded-3xl`), drag handle centered at top (32dp wide, 4dp high, `#cbd5e1`). Content aligns to standard margins with primary actions pinned above the thumb zone.