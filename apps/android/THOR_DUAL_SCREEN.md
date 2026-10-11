# Dual-screen output on Android (AYN Thor)

This branch lets the Android app draw one ancillary window (backglass or score view / DMD)
on a secondary display, such as the AYN Thor bottom screen.

## How it works

- SDL on Android supports one window only, so stock VPX embeds the backglass and DMD in the playfield.
- `SecondScreenPresenter` opens an Android `Presentation` on the secondary display.
- Its `Surface` goes to native code through `VPinballSetExternalSurface` (JNI).
- `VPX::Window` gets an "external surface" mode. It is not an SDL window.
- `RenderDevice::AddWindow` creates a second BGFX swap chain on that surface.

## Setup

Edit `VPinballX.ini` (or set the same values in the app settings).

Backglass on the bottom screen:

```ini
[Backglass]
BackglassOutput = 1
```

DMD (score view) on the bottom screen, when the backglass is not routed:

```ini
[ScoreView]
ScoreViewOutput = 1
```

`1` means "Floating". With neither value set to `1`, the app behaves as stock.

## Limits

- One ancillary window per secondary display.
- The surface is bound at table start. A size change during play is not supported.
- If the bottom surface is destroyed during play, the table stops (no crash).
- If the GPU backend does not support extra swap chains, the bottom screen stays black.

## Continuous playfield (span mode)

The table renders once on a tall virtual canvas: top screen, then the hinge gap, then the bottom screen.
Each screen shows its slice at its own pixel density, so one millimeter of table is one millimeter of glass.
The gap rows are rendered but not shown, so the ball passes "under" the hinge.

```ini
[Player]
SpanPlayfield = 1
SpanGapMM = 10          ; measure: bottom of top active area to top of bottom active area
SpanTopPPI = 367        ; spec-sheet estimate, measure to confirm
SpanBottomPPI = 419.5   ; spec-sheet estimate, measure to confirm
SpanTopOffsetX = 0      ; shift the top column (pixels) to line it up with the bottom
SpanBottomOffsetX = 0
```

Span mode takes the bottom screen, so it wins over `BackglassOutput` / `ScoreViewOutput`.
Keep the backglass and DMD embedded (or disabled) when spanning.

With the Thor spec numbers and a 10 mm gap, the canvas is 1240 x 2479 px. The top screen shows a
1085 px wide centered column; the bottom screen shows the full 1240 px width. The math is in
`src/renderer/SpanLayout.h`.

### Span mode behavior

- The frame rate is capped to the slower screen (60 Hz), so both screens show the same frame.
- The in-game UI draws on the top screen only, at its native size.
- Use a top-down view (Cabinet mode in the point-of-view settings) for the best continuity.
- If the bottom surface is missing, the table renders on the top screen only.
