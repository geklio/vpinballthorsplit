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

## Limits (version 1)

- One ancillary window per secondary display.
- The surface is bound at table start. A size change during play is not supported.
- If the bottom surface is destroyed during play, the table stops (no crash).
- If the GPU backend does not support extra swap chains, the bottom screen stays black.

## Not yet done

- Continuous playfield across both screens (one tall render, sliced, with a gap and per-screen scale).
  This version builds the second-display path that the continuous mode needs.
