# ZERO ACCELERATOR v2

Lightweight Android game booster: **boot splash**, **left-edge slide-in panel**, and an
optional **Shizuku** cleanup. Native Views only (UI is built in code), no Compose, no
third-party UI libraries. Dependencies: `appcompat`, `core-ktx`, Shizuku.

## What you get when the selected game opens

1. **Boot splash**: full-screen, non-touchable branded screen (spinner, device name, game, mode,
   live cleanup steps). Auto-dismisses once the cleanup finishes (1.6 to 4.5 s).
2. **Left-edge handle**: slim amber pill. Tap it or drag it to open the panel.
3. **Slide-in panel** (iQOO Monster Mode style):
   * CPU GHz and GPU MHz ring gauges, game duration, **real game FPS**, battery
   * Mode switch: Battery Saver / Balanced / MONSTER (applies live)
   * Refresh rate: Smart / 60 / 90 / 120 Hz (only rates your display supports)
   * Network: Wi-Fi / Mobile data
   * Tools: Block notifications (DND), Lock brightness, Keep screen on, Screenshot,
     RAM clean, Fixed performance, Mute, Settings

Everything the panel changes is restored when the game leaves the foreground.

## Why v2 works when v1 did not

| v1 problem | v2 fix |
|---|---|
| Overlays used `TYPE_APPLICATION_OVERLAY`. Without the overlay permission `addView` threw inside the accessibility service, so no splash and no panel. | Uses `TYPE_ACCESSIBILITY_OVERLAY`: **no extra permission**, and every window call is guarded. |
| Any other window (keyboard, notification shade, popup) counted as "game exited" and tore the overlay down. | Transient windows are ignored, exit is debounced 600 ms. |
| Non-cancelable Shizuku dialog locked the whole UI when Shizuku was off. | Shizuku is optional. The UI never blocks; missing items are tappable rows. |
| `Shizuku.requestPermission()` and friends could throw when the binder was dead. | Every Shizuku call is exception-safe. |
| CPU/GPU read from sysfs in the app process (blocked by SELinux, always "N/A"). FPS was just the display refresh rate. | Read in the Shizuku shell process. FPS comes from SurfaceFlinger frame timestamps. |
| Force-stop ran one process per app, sequentially (slow). | Parallel batches. |

## Setup on the phone

1. Install the APK. Open the app.
2. **Accessibility service**: tap ENABLE. If the toggle is greyed out (Android 13+ sideloaded apps):
   App info, three dots, **Allow restricted settings**, then enable it again.
3. Pick your game. Choose a mode.
4. Optional but recommended: install and start **Shizuku**, then tap ALLOW.
5. Tap **PREVIEW** to see the splash and panel without opening the game.
6. On vivo / iQOO / Xiaomi / OPPO: set the app to **Unrestricted** battery and allow autostart,
   otherwise the OS may kill the accessibility service.

## Notes and limits

* Refresh-rate, brightness lock, network and fixed-performance use `settings` / `svc` / `cmd`
  through Shizuku. Some OEM ROMs use different keys, in which case a toggle can have no effect.
* GPU/CPU/FPS sources differ per SoC. A value that cannot be read shows `--`, never a fake number.
* Cleanup force-stops all third-party apps except: the game, whitelist, keyboard, launcher,
  Shizuku, and apps hosting an enabled accessibility service.

## Build

AGP 8.1.4, Kotlin 1.9.22, Gradle 8.4, `compileSdk 34`, `minSdk 26`. JDK 17.

```bash
gradle wrapper --gradle-version 8.4   # once
./gradlew assembleRelease
```

Or run the **Build ZERO ACCELERATOR APK** workflow in GitHub Actions (outputs debug + signed release).

## Layout

```
app/src/main/
  aidl/.../IOptimizerService.aidl
  java/com/zero/accelerator/
    GameAccessibilityService.kt     detection + session
    overlay/  OverlayController, SplashView, HandleView, PanelView, RingGauge, IconView, TelemetrySampler
    shizuku/  ShizukuGate           safe wrapper
    optimizer/OptimizerUserService  runs as shell/root
    ui/       MainActivity, Ui
    util/     Perms, PackageUtils, DndUtil, WakeLockManager, SessionNotif, Sysfs, Pal, Ext
    model/PerfMode, prefs/AppPrefs
```

## Upgrading an existing checkout

Delete `app/src` first, then copy this tree over. v1 layouts and `GameOverlay.kt` /
`Telemetry.kt` must not remain.
