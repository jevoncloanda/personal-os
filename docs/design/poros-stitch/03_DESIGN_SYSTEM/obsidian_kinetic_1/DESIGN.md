---
name: Obsidian Kinetic
colors:
  surface: '#12131b'
  surface-dim: '#12131b'
  surface-bright: '#383842'
  surface-container-lowest: '#0d0e16'
  surface-container-low: '#1a1b23'
  surface-container: '#1e1f28'
  surface-container-high: '#292932'
  surface-container-highest: '#33343d'
  on-surface: '#e3e1ed'
  on-surface-variant: '#c8c4d7'
  inverse-surface: '#e3e1ed'
  inverse-on-surface: '#2f3039'
  outline: '#928ea0'
  outline-variant: '#474554'
  surface-tint: '#c7bfff'
  primary: '#c7bfff'
  on-primary: '#29009f'
  primary-container: '#8d7fff'
  on-primary-container: '#23008d'
  inverse-primary: '#5945d6'
  secondary: '#c7bfff'
  on-secondary: '#2d2178'
  secondary-container: '#473c92'
  on-secondary-container: '#b8afff'
  tertiary: '#66dbb3'
  on-tertiary: '#003829'
  tertiary-container: '#21a37f'
  on-tertiary-container: '#003123'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e4dfff'
  primary-fixed-dim: '#c7bfff'
  on-primary-fixed: '#170065'
  on-primary-fixed-variant: '#4027be'
  secondary-fixed: '#e4dfff'
  secondary-fixed-dim: '#c7bfff'
  on-secondary-fixed: '#170164'
  on-secondary-fixed-variant: '#443a8f'
  tertiary-fixed: '#84f8ce'
  tertiary-fixed-dim: '#66dbb3'
  on-tertiary-fixed: '#002117'
  on-tertiary-fixed-variant: '#00513d'
  background: '#12131b'
  on-background: '#e3e1ed'
  surface-variant: '#33343d'
typography:
  display-lg:
    fontFamily: Sora
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.02em
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
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Sora
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Sora
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  title-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 22px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
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
    fontSize: 10px
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
  gutter-mobile: 0.75rem
  margin: 1.5rem
  margin-mobile: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

The design system is an unapologetic, deep-space dark mode interface engineered for high-velocity software, technical workflows, and tactile mobile interaction. It targets engineers, power users, and data-dense operators who demand razor-sharp contrast, low eye fatigue under sustained night usage, and responsive visual feedback.

The aesthetic fuses modern technical minimalism with kinetic tactility. Rather than relying on skeuomorphic gradients or pure black OLED cutouts, the interface utilizes carefully calibrated obsidian slate tones layered over a void foundation. Color behaves as energy: electric indigo accents puncture dark surfaces strictly to signify active states, kinetic progress, or pivotal interactions. The ambiance is focused, surgical, and premium.

## Colors

The palette operates on pure dark-mode architecture with zero un-inverted elements or washed-out white containers. The spatial hierarchy is constructed through tonal tiers rather than stark light emission:

- **Canvas Background (`#0D0E16`)**: The bottom-most foundation layer. Absorbs visual noise and preserves contrast.
- **Elevated Surface (`#171925`)**: The middle structural tier used for app bars, bottom sheets, navigation rails, and modal dialogs.
- **Card Surface (`#202234`)**: The interactive component surface. Hosts form controls, distinct grouping panels, and list cards.
- **Primary Indigo (`#7665F5`)**: High-contrast kinetic accent for active selection, focused states, and prominent action buttons.
- **Light Indigo (`#ACA2FF`)**: Luminescent secondary variant for badges, icon tinting, chips, and subtle focus glows.
- **Primary Text (`#F4F3FA`)**: High-legibility crisp text with 96% luminance against the canvas.
- **Secondary Text (`#A5A8BE`)**: De-emphasized, low-strain slate text for metadata, labels, and secondary actions.
- **Divider & Border (`#35374A`)**: Subtle structural separator separating interactive zones without creating harsh gridlines.
- **Error (`#FF7585`)**: Saturated coral red for destructive actions, validation alerts, and system warnings.
- **Success (`#53C9A2`)**: Mint green denoting successful execution, healthy telemetry, and confirmation states.

## Typography

The type scale combines **Sora** for structural display and headline hierarchy with **Inter** for readable body and interface density. Sora injects a futuristic, geometric attitude into major layout landmarks, while Inter ensures distortion-free legibility across high-density data matrices, code previews, and rapid-scan mobile feeds.

Letter spacing is tightened slightly across large headings to avoid loose whitespace on high-contrast backgrounds. Body copy adheres strictly to standard 1.4–1.5x line heights, preventing overlapping scan lines when reading dense content under low ambient light.

## Layout & Spacing

This design system uses an 8pt base grid with a 4pt subgrid for micro-alignment, icons, and inline badges. 

- **Mobile (<600dp)**: 4-column fluid layout with `1rem` (`16px`) outer margins and `0.75rem` (`12px`) gutters. Touch targets follow Jetpack Compose Material 3 ergonomics with a minimum dimension of `48dp x 48dp`.
- **Tablet (600dp–840dp)**: 8-column layout with `1.5rem` (`24px`) margins and `1rem` (`16px`) gutters. Panels transition from modal overlays to split master-detail views.
- **Desktop / Foldable Expanded (>840dp)**: 12-column layout capped at `1280px` max-width, maintaining centered presentation with fluid margins.

All component padding relies strictly on the `space-*` scale. Nested elements inside cards must use `space-md` or `space-lg`, ensuring content breathes naturally against the dark background.

## Elevation & Depth

Visual depth does not rely on traditional diffuse drop shadows, which disappear or look muddy against near-black backdrops. Hierarchy is instead constructed via **Tonal Surface Stacking** complemented by **Thin Luminescent Keylines**:

1. **Level 0 (Base)**: `#0D0E16` (Screen canvas).
2. **Level 1 (Elevated Surfaces)**: `#171925` (Navigation bars, app bars, persistent menus).
3. **Level 2 (Cards & Floating Elements)**: `#202234` (Surface containers, floating cards, dialogs).
4. **Keyline Definition**: Floating surfaces and active cards use a subtle 1px border colored with `#35374A` to delineate bounds crisply against the base canvas.
5. **Kinetic Glow (Active Focus)**: When focused or actively pressed, interactive components emit an ambient indigo glow: `0 0 16px -2px rgba(118, 101, 245, 0.35)`.

## Shapes

The shape system is anchored to a generous **16px / rounded-2xl** standard for primary cards and floating surfaces. This balances modern Android Material 3 geometric curves with technical precision:

- **Cards & Modal Sheets**: `1rem` (`16px`, `rounded-2xl`).
- **Buttons, Text Fields & Chips**: `0.75rem` (`12px`) to `1rem` (`16px`) depending on component height, retaining ergonomic curvature.
- **Badges & Segment Toggles**: `0.5rem` (`8px`) for sharp, compact metadata grouping.
- **Pill Elements**: Reserved exclusively for dynamic notification counters, slider thumbs, and full-width search pills.

## Components

### Buttons
- **Primary Button**: Solid `#7665F5` fill with `#F4F3FA` text, minimum height `48dp`, `16px` corner radius. Active tap state triggers a subtle scale transition (`0.98x`) and shifts tint to `#ACA2FF`.
- **Secondary / Outlined Button**: Transparent or `#171925` background, 1px border of `#35374A`, `#F4F3FA` text. Focus state illuminates the border with `#7665F5`.
- **Destructive Button**: Solid or outlined `#FF7585` container with white text, strictly reserved for irreversible operations.

### Cards & Containers
- Built using `#202234` with an optional 1px border of `#35374A`. No drop shadows; cards differentiate via the contrast between `#202234` and `#0D0E16`.
- Internal padding defaults to `1.25rem` (`20px`).

### Text Input Fields
- Container fill set to `#171925` with a 1px border of `#35374A` and `16px` corner radius.
- Inactive placeholder text colored with `#A5A8BE`.
- Active focus transitions the border to `#7665F5` with a subtle kinetic glow. Error states trigger a `#FF7585` border and inline caption text.

### Selection Controls (Checkboxes & Radios)
- Inactive bounds: 2px border in `#35374A`, transparent interior.
- Selected state: Solid `#7665F5` fill with `#F4F3FA` checkmark or center pip. Touch targets must retain minimum `48dp` bounding boxes.

### Chips & Filter Tags
- Surface fill `#171925`, border `#35374A`, label `#A5A8BE`.
- Selected state: Container shifts to `#202234` with a `#7665F5` border and `#ACA2FF` typography.

### Dividers & Progress Indicators
- Dividers are 1px solid lines using `#35374A`.
- Kinetic linear progress indicators use `#171925` as the track with `#7665F5` as the moving indicator head.