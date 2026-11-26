# ZERO ACCELERATOR

A lightweight Android **gaming optimizer** built with **Kotlin + native XML Views**.
No Jetpack Compose, no heavy third‑party UI / animation libraries. Target release
APK is kept **under 2 MB** via R8 minification, resource shrinking, single‑locale
resources and lean dependencies (only `appcompat` + `core-ktx` + Shizuku).

---

## Design philosophy — zero idle footprint

The app does **not** run persistent background services or timers while the bound
game is not in the foreground. RAM usage sits near **0 MB** when idle:

* The `AccessibilityService` only reacts to `TYPE_WINDOW_STATE_CHANGED` events.
  It holds no timers and consumes no CPU when the target isn't foreground.
* The privileged **Shizuku UserService** is bound **only** at game launch and
  unbound (`unbindUserService(..., true)`) the moment the game leaves foreground,
  which tears down the shell/root process.
* Overlay windows (banner, edge control, HUD) and wake locks exist **only** while
  the game is running; everything is removed on exit.

---

## How it works

1. **Shizuku gate** (`shizuku/ShizukuGate.kt`)
   * At startup the app checks `Shizuku.pingBinder()` and `Shizuku.checkSelfPermission()`.
   * If Shizuku is missing / not running / not granted, the setup UI is *locked*
     behind a Shizuku prompt dialog (`maybeShowLock`).
   * A single target app (game) is bound via `SharedPreferences`.

2. **Dormant detection** (`GameAccessibilityService.kt`)
   * Listens for `typeWindowStateChanged`. When the bound package becomes the
     foreground window it:
     * shows the floating launch banner,
     * binds the Shizuku UserService and runs the optimization routine,
     * acquires the mode‑specific wake lock,
     * (MONSTER) posts a high‑priority ongoing notification.

3. **Privileged optimization** (`optimizer/OptimizerUserService.kt`)
   Runs as **shell (uid 2000) / root (uid 0)** inside the Shizuku process:
   * force‑stops every third‑party app except the bound game, the whitelist,
     the default keyboard and the default launcher,
   * trims caches (`pm trim-caches`),
   * sets the target's standby bucket to `active`,
   * MONSTER: disables battery saver + applies a `performance` governor.

---

## Pre‑launch banner (`overlay/GameOverlay.kt`)

A floating pill overlay (`TYPE_APPLICATION_OVERLAY`):
* **Line 1** — bold **ZERO ACCELERATOR**
* **Line 2** — `ASSISTED BY <BUILD.MODEL.uppercase()>`
* **Line 3** — live ticker cycling cleanup steps (Purging RAM → …)
* Exits after **1.2 s** with a slide/fade animation, leaving a subtle
  **top‑left edge control** that opens the HUD.

---

## Performance modes & themes

| Mode | Color | Behaviour |
|------|-------|-----------|
| **LOW** | Emerald `#00E676` | Force‑stop heavy apps · keep screen wake lock |
| **BALANCED** | Electric Blue `#2979FF` | Full RAM purge · trim caches · active standby bucket · screen wake lock |
| **MONSTER** | Crimson `#FF1744` | Extensive purge · performance governor · power mode 0 · prioritized notification · wake locks with 10‑min timeout |

All accents (banner, edge control, HUD, buttons) follow the active mode color.

---

## Slide‑down HUD (`overlay/SidebarHud` + `Telemetry`)

Inspired by the iQOO Ultra Game Mode sidebar. Telemetry is loaded **only when the
sidebar is opened** (1 s refresh loop, stopped on close):

* CPU clock, GPU clock, FPS (via `Choreographer`), session duration.
* Quick‑action grid: **RAM CLEAN** (re‑runs the purge) and **DND** toggle.

---

## Main setup activity (`ui/MainActivity.kt`)

Minimal dark theme with:
* Shizuku connection status + lock prompt.
* Permission toggles: Accessibility, Display‑over‑other‑apps, Notifications, DND.
* Single target game picker.
* Performance mode selection.
* Whitelist management (keyboard & launcher always protected).

---

## Permissions & first‑run

1. Install **Shizuku** and start it (ADB or root).
2. Open ZERO ACCELERATOR → grant Shizuku access when prompted.
3. Enable the **ZERO ACCELERATOR** accessibility service.
4. Grant **Display over other apps**.
5. Pick your target game, choose a mode, optionally add whitelist apps.
6. Launch the game — the banner appears, then the edge control / HUD.

---

## Build

Requirements: Android Studio (AGP 8.1.4, Kotlin 1.9.22, Gradle 8.4),
`compileSdk 34`, `minSdk 26`, `targetSdk 34`.

```bash
# From the project root
./gradlew assembleRelease      # needs the Gradle wrapper; generate with `gradle wrapper` if missing
# or just open the folder in Android Studio and Build ▸ Generate Signed Bundle / APK
```

`app/build.gradle` enables `minifyEnabled true` + `shrinkResources true` and
`resourceConfigurations "en"` to keep the release APK small.

> Note: this repository intentionally does **not** ship the Gradle wrapper jar
> (binary). Run `gradle wrapper --gradle-version 8.4` once, or let Android Studio
> generate it, before building.

## Project layout

```
app/src/main/
├── aidl/com/zero/accelerator/IOptimizerService.aidl
├── java/com/zero/accelerator/
│   ├── ZeroApp.kt
│   ├── C.kt
│   ├── GameAccessibilityService.kt
│   ├── model/PerfMode.kt
│   ├── prefs/AppPrefs.kt
│   ├── shizuku/{ShizukuGate,OptimizerUserService}.kt
│   ├── overlay/{GameOverlay,Telemetry}.kt
│   ├── ui/MainActivity.kt
│   └── util/{Perms,PackageUtils,WakeLockManager,SessionNotif,DndUtil,Drawables,Ext}.kt
├── res/{layout,values,values-night,drawable,xml,mipmap-anydpi-v26}
└── AndroidManifest.xml
```
