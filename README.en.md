# Wrist Gestures for Wear OS 3+

[Русская версия](README.md)

[![Latest release](https://img.shields.io/github/v/release/iRapoo/WristGestures)](https://github.com/iRapoo/WristGestures/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/iRapoo/WristGestures/total)](https://github.com/iRapoo/WristGestures/releases)
[![Build](https://github.com/iRapoo/WristGestures/actions/workflows/release.yml/badge.svg)](https://github.com/iRapoo/WristGestures/actions/workflows/release.yml)
![Wear OS 3+](https://img.shields.io/badge/Wear%20OS-3%2B-4285F4)
[![License MIT](https://img.shields.io/github/license/iRapoo/WristGestures)](LICENSE)

Brings back the **wrist gestures** that Wear OS 2 had and that disappeared after the update to
Wear OS 3 (for example on the Mobvoi TicWatch Pro 3). Flick your wrist to open notifications
and quick settings and to scroll lists, without touching the screen.

<p align="center">
  <a href="https://github.com/iRapoo/WristGestures/releases/latest/download/WristGestures.apk">
    <img src="https://img.shields.io/badge/Download-APK-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" height="44">
  </a>
  <br>
  <sub>Latest version · <a href="https://github.com/iRapoo/WristGestures/releases">all releases</a> · <a href="#installation">how to install</a></sub>
</p>

<p align="center">
  <img src="docs/screenshots/main.png" width="230" alt="Main screen: gesture test">
  <img src="docs/screenshots/sensitivity.png" width="230" alt="Sensitivity setting">
  <img src="docs/screenshots/actions.png" width="230" alt="Choosing actions for gestures">
</p>

<sub>Screenshots show the Russian UI; the app is also available in English.</sub>

| Gesture | How to do it | Default action |
|---|---|---|
| **Flick out** | quickly turn the wrist away from you and back | on the watch face: open notifications; in lists: scroll down; in quick settings: close them |
| **Flick in** | quickly turn the wrist towards you and back | on the watch face: open quick settings; in lists: scroll up; at the top of a list: close the screen |
| **Shake** | 2–3 quick turns back and forth, as if shaking off water | back (nothing on the watch face) |

Gestures work across the whole UI: notifications, the app list, settings and regular apps.
Every action can be changed. **No root, no internet permission, no data leaves the watch.**

---

## Contents

- [Tested watches](#tested-watches)
- [How it works in short](#how-it-works-in-short)
- [Installation](#installation)
- [Setup and calibration](#setup-and-calibration)
- [Battery](#battery)
- [Privacy and security](#privacy-and-security)
- [Troubleshooting](#troubleshooting)
- [Reporting a problem](#reporting-a-problem)
- [Uninstalling](#uninstalling)
- [For developers](#for-developers)
- [License](#license)

---

## Tested watches

| Watch | Wear OS version | Result |
|---|---|---|
| Mobvoi TicWatch Pro 3 GPS | 3.5 (RMRB.240228.002) | everything works |

The project is young and has been tested on one model so far. Other Wear OS 3+ watches with a
gyroscope should work too, but system UIs differ between manufacturers. If you tried it on your
watch, [let us know](#reporting-a-problem) how it went, and the model will be added to the table.

## How it works in short

1. While the screen is on, the app reads the **gyroscope** 50 times per second.
2. A flick is a short fast rotation followed by an equally fast rotation back. The recognizer
   looks for exactly this pattern and ignores slow movements such as raising your wrist to look
   at the watch. Details: [docs/ALGORITHM.md](docs/ALGORITHM.md).
3. The action is performed by an **accessibility service**, which is the only way for a regular
   app to scroll screens and emulate touches on Wear OS.
4. When the screen goes off or into ambient mode, the gyroscope is released.

## Installation

You need a watch with **Wear OS 3 or newer** (Android 11+) and a **gyroscope**, and a computer
with **ADB** ([Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools)).
The computer is needed once, to install the app and enable the service.

### 1. Connect to the watch with ADB

On the watch:

1. **Settings → System → About → Versions**, tap **Build number** 7 times to unlock developer options.
2. **Settings → Developer options**: turn on **ADB debugging** and **Debug over Wi-Fi**
   (on some watches it is called **Wireless debugging**).
3. Connect the watch to the **same Wi-Fi network** as the computer and note the IP address
   and port shown under the debugging option.

On the computer:

```bash
# Wear OS 3 on TicWatch and similar: address is shown as IP:5555
adb connect 192.168.1.50:5555

# Newer watches with "Wireless debugging" first need pairing:
#   on the watch: Wireless debugging → Pair new device → note IP:port and the code
adb pair 192.168.1.50:37123
adb connect 192.168.1.50:41234

adb devices   # the watch must be listed as "device"
```

Confirm the debugging prompt on the watch if it appears.

> **Tip:** watches turn Wi-Fi off to save power when they are connected to the phone over
> Bluetooth. If the connection drops, open Wi-Fi settings on the watch and keep the screen on
> while running commands.

### 2. Download the APK

Download [WristGestures.apk](https://github.com/iRapoo/WristGestures/releases/latest/download/WristGestures.apk)
from the latest release. Every release is built by GitHub Actions straight from the tagged
source code; the [build log](https://github.com/iRapoo/WristGestures/actions) is public.

To build it yourself, see [For developers](#for-developers).

### 3. Install

```bash
adb install -r WristGestures.apk
```

A newer version is installed with the same command over the old one. Settings and the enabled
service are kept.

### 4. Enable the accessibility service

The service does all the work and has to be enabled once. It stays enabled after reboots and
app updates.

**Option A — on the watch.** Open **Settings → Accessibility** and turn on **Wrist Gestures**,
or press **Accessibility settings** at the bottom of the app screen.
On many Wear OS builds third-party services are **not shown** there; then use option B.

**Option B — with ADB.** First check whether other accessibility services are already enabled:

```bash
adb shell settings get secure enabled_accessibility_services
```

If the result is `null` or empty:

```bash
adb shell settings put secure enabled_accessibility_services xyz.quenix.wristgestures/.service.GestureService
adb shell settings put secure accessibility_enabled 1
```

If it printed something (for example `com.example/.SomeService`), **keep it** and append ours
after a colon, otherwise the other service will be turned off:

```bash
adb shell settings put secure enabled_accessibility_services com.example/.SomeService:xyz.quenix.wristgestures/.service.GestureService
adb shell settings put secure accessibility_enabled 1
```

Open the app: the top line should say **Service is on**.

## Setup and calibration

Open **Wrist Gestures** on the watch. While this screen is open, gestures are only **shown** in
the box at the top and are not executed, so you can safely experiment.

| Setting | What it does |
|---|---|
| **Gestures** | Master switch. When off, the gyroscope is not used at all. |
| **Sensitivity** | 1 = only strong flicks, 10 = light flicks. The threshold in rad/s is shown above the buttons. If gestures trigger by accident, lower it; if they are hard to trigger, raise it. |
| **Invert direction** | Swaps "flick out" and "flick in". Turn it on if the directions are reversed, e.g. when the watch is on the right wrist. |
| **Vibration** | A short tick when a gesture is recognized. |
| **Actions** | Tap a row to cycle through actions for that gesture: nothing, scroll down / notifications, scroll up / back, notifications, back, watch face. |
| **Gyroscope axis** | The axis around which the wrist rotates. **X** is correct for most watches. The numbers under the test box show the peak rotation speed on each axis over the last second: flick your wrist and pick the axis with the biggest number. |

Recommended routine:

1. Put the watch on and open the app.
2. Do a few flicks out and in. Check that the box shows the correct direction; if it is
   reversed, turn on **Invert direction**.
3. Move your arm normally, raise the wrist, gesticulate. If the box shows false gestures, lower
   **Sensitivity**.
4. Go to the watch face and try it: flick out opens notifications, flick out again scrolls down,
   flick in scrolls up, and at the top notifications close. Flick in on the watch face opens
   quick settings, flick out closes them.

**Gestures work only when the screen is on** (not in ambient mode), the same as on Wear OS 2.
Raise the wrist or tap the screen first.

## Battery

- The gyroscope is used only while the screen is **on** and is released in ambient mode and
  with the screen off. In standby the app does nothing.
- While the screen is on, the app processes 50 samples per second with a few arithmetic
  operations each; it does not keep the CPU awake by itself.
- There are no precise battery measurements yet. If you notice a difference in battery life,
  please report it together with your watch model.

## Privacy and security

An accessibility service is a powerful permission, so here is everything the app does with it:

| Permission / capability | Why |
|---|---|
| Accessibility: *perform gestures* | Emulating a swipe where a list cannot be scrolled directly. |
| Accessibility: *retrieve window content* | Finding the scrollable list on screen to scroll it. The content is read only at the moment of a gesture and is never stored or sent anywhere. |
| `VIBRATE` | Haptic feedback. |
| Gyroscope | Gesture recognition. No permission needed. |

- The app has **no INTERNET permission**, so it physically cannot send anything anywhere. You
  can see it in [AndroidManifest.xml](app/src/main/AndroidManifest.xml).
- No third-party libraries, analytics or ads: only the Android framework.
- The source code is short and fully commented.

### Verifying the APK

All official releases are signed with the same key. SHA-256 fingerprint of the certificate:

```
b5a17b1d96e9e53abe4180d176215002c05cfdcf9c0dedc2ef0a1ab1f19f34bc
```

To check a downloaded file (`apksigner` is part of Android SDK Build Tools):

```bash
apksigner verify --print-certs WristGestures.apk
```

The `Signer #1 certificate SHA-256 digest` line must match the fingerprint above. If it does
not, the file is not an official build.

## Troubleshooting

**"Service is off" although I enabled it.**
**Force-stopping** the app ("Force stop" in settings or `am force-stop`) disables the service:
Android removes it from the enabled list. Enable it again (installation step 4). If the service
still does not start after the ADB commands, reset the list and set it again:

```bash
adb shell settings put secure enabled_accessibility_services null
adb shell settings put secure enabled_accessibility_services xyz.quenix.wristgestures/.service.GestureService
adb shell settings put secure accessibility_enabled 1
```

**`INSTALL_FAILED_UPDATE_INCOMPATIBLE` when installing.**
The watch has a version signed with a different key (for example a self-built one). Uninstall it
(`adb uninstall xyz.quenix.wristgestures`), install again and re-enable the service.

**The test box shows gestures, but nothing happens on screen.**
Check that the service is on (top line of the app) and that the gesture is not set to *nothing*.
While the app screen is open, gestures are intentionally not executed.

**Directions are reversed.** Turn on **Invert direction**.

**Accidental triggers while walking or typing.**
Lower sensitivity, or set the problematic gesture to *nothing*.

**A flick on the watch face does something other than described.**
Your watch's system UI is organized differently from the TicWatch one. Please report it (see
below) so support can be added.

## Reporting a problem

Open an [issue](https://github.com/iRapoo/WristGestures/issues/new/choose) and include:

- the watch model and Wear OS version (**Settings → System → About**);
- the app version;
- what you did, what you expected and what happened.

"Works on my watch" reports are very helpful too.

## Uninstalling

```bash
adb uninstall xyz.quenix.wristgestures
```

or uninstall it from the watch's app list. The accessibility setting is cleaned up
automatically.

## For developers

### Building

```bash
git clone https://github.com/iRapoo/WristGestures.git
cd WristGestures
./gradlew testDebugUnitTest   # recognizer unit tests, no watch needed
./gradlew assembleRelease     # Windows: gradlew.bat assembleRelease
```

Requires JDK 17+ and the Android SDK; the easiest way is to open the project in Android Studio.
The APK will be in `app/build/outputs/apk/release/app-release.apk`. A self-built APK is signed
with your local debug key and cannot update the official release.

### Project structure

```
app/src/main/java/xyz/quenix/wristgestures/
├── detection/
│   ├── FlickRecognizer.kt     # pure-Kotlin gesture recognizer (unit-tested)
│   └── GyroGestureSource.kt   # feeds gyroscope data into the recognizer
├── service/
│   ├── GestureService.kt      # accessibility service: lifecycle, screen on/off, gesture → action
│   └── ActionPerformer.kt     # executes actions: scroll, swipe, back, home
├── settings/
│   └── GestureSettings.kt     # SharedPreferences, Axis and GestureAction enums
├── ui/
│   └── MainActivity.kt        # settings + live calibration screen
└── LiveState.kt               # "calibration screen is open" flag shared with the service
```

The app uses only the Android framework, with no AndroidX or other libraries, to stay small
on watches with 1 GB of RAM.

### Debugging

```bash
./gradlew installDebug        # debug build on the connected watch
adb logcat -s WristGestures   # gestures, actions and screen structure before every action
```

The debug build also accepts gestures from the computer, so actions can be tested without
moving the wrist:

```bash
# Ignore the real gyroscope while testing (send false to resume)
adb shell am broadcast -a xyz.quenix.wristgestures.DEBUG_GESTURE --ez pause_sensor true

# Trigger a gesture: FLICK_OUT, FLICK_IN or SHAKE
adb shell am broadcast -a xyz.quenix.wristgestures.DEBUG_GESTURE --es gesture FLICK_OUT
```

The log shows every movement the recognizer sees, which is the fastest way to tune thresholds
on a new watch:

```
lobe sign=-1 peak=6,6 duration=77ms gap=0ms
lobe sign=1 peak=19,6 duration=97ms gap=38ms
sequence lobes=2 -> FLICK_IN
```

### Documentation

- [docs/ALGORITHM.md](docs/ALGORITHM.md) — how gestures are recognized, with real measurements.
- [docs/SYSTEM_UI.md](docs/SYSTEM_UI.md) — how the TicWatch system UI works and why actions are
  implemented the way they are.
- [docs/RELEASING.md](docs/RELEASING.md) — publishing a release and APK signing.

### Adding a new action

1. Add a constant to `GestureAction` in `settings/GestureSettings.kt`.
2. Add its label to `res/values/strings.xml` and `res/values-ru/strings.xml`.
3. Handle it in `ActionPerformer.perform`.

It automatically appears in the list of actions on the settings screen.

### Adding a new gesture

1. Add a constant to `Gesture` in `detection/FlickRecognizer.kt` and recognize it in
   `finishSequence` (or in a new recognizer).
2. Add a default action in `GestureSettings.defaultAction`, a label in `MainActivity.gestureLabel`
   and a row in `activity_main.xml`.
3. Cover it with a test in `FlickRecognizerTest`.

Pull requests are welcome.

## License

[MIT](LICENSE). Use, modify and share freely.

This project is not affiliated with Mobvoi or Google. TicWatch is a trademark of Mobvoi,
Wear OS is a trademark of Google LLC. The app is provided "as is", without warranty.
