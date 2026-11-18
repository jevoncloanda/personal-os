# Approved Poros logo assets

The five supplied PNGs are preserved verbatim here. Each is 1254 × 1254 pixels.
The color, black and white transparent files have alpha channels containing detached
low-alpha noise. The obsidian and light presentations are fully opaque and have
different geometry and spacing.

## Authoritative artwork

`supplied-color-transparent.png` is the master. The prepared Android mark retains
its five largest connected nonzero-alpha components, removing 3714 detached noise
components. Original RGB values and alpha within those five components are preserved.
The surviving bounds are (249, 209)–(1012, 1047), using exclusive right/bottom edges.
A centered 842 × 842 square crop includes two pixels of vertical outer padding.

## Android resources

- `drawable-nodpi/ic_poros_mark.png` is the cropped full-color master for shared
  in-app branding in both themes. Compose Image preserves the original gradients.
- `drawable-xxxhdpi/ic_poros_foreground.png` places a Lanczos-resized 256 × 256
  master at (88, 88) on a transparent 432 × 432 canvas, corresponding to 108dp.
  Every nontransparent pixel lies within radius 130.23px, inside the adaptive
  icon safe-circle radius of 132px (33dp).
- `drawable-xxxhdpi/ic_poros_monochrome.png` uses white RGB with the exact same
  alpha bytes as the adaptive foreground. Negative spaces are transparent.
- API 26+ adaptive resources use the existing obsidian background (#0D0E16).
  API 33+ adds the matching themed mask. Android 12+ uses the padded transparent
  foreground on the existing obsidian splash background.

Circular, squircle and rounded-square mask previews and 24/32/48px dark/light
previews were inspected locally. Physical launcher and splash rendering require
an authorized connected Android device.
