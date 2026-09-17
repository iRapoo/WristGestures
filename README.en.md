# Wrist Gestures for Wear OS 3+

[Русская версия](README.md)

Brings back the **wrist gestures** that Wear OS 2 had and that disappeared after the update to
Wear OS 3 (for example on the Mobvoi TicWatch Pro 3): flick your wrist to open notifications
and scroll through them, without touching the screen.

| Gesture | What it looks like | Default action |
|---|---|---|
| **Flick out** | quickly turn the wrist away from you and back | scroll down; on the watch face — open notifications |
| **Flick in** | quickly turn the wrist towards you and back | scroll up; at the top of a list — go back (closes notifications); on the watch face — open quick settings |
| **Shake** | several fast turns back and forth | back |

Every action can be changed in the app. No root, no internet permission, no data collection.

> **Status:** early version. Developed and tested on a TicWatch Pro 3 GPS
> (Wear OS 3.5, build RMRB.240228.002). Other watches should work if they have a gyroscope,
> but the notification swipe may need tuning — reports are welcome.

---

## Contents

- [How it works in short](#how-it-works-in-short)
- [Requirements](#requirements)
- [Installation](#installation)
  - [1. Connect to the watch with ADB](#1-connect-to-the-watch-with-adb)
  - [2. Get the APK](#2-get-the-apk)
  - [3. Install](#3-install)
  - [4. Enable the accessibility service](#4-enable-the-accessibility-service)
- [Setup and calibration](#setup-and-calibration)
- [Battery](#battery)
- [Privacy and permissions](#privacy-and-permissions)
- [Troubleshooting](#troubleshooting)
- [Uninstalling](#uninstalling)
- [For developers](#for-developers)
- [License](#license)

---

## How it works in short

1. While the screen is on, the app reads the **gyroscope** 50 times per second.
2. A flick is a short fast rotation followed by a rotation back. The recognizer looks for this
   pattern and ignores slow movements such as raising your wrist to look at the watch.
   Details: [docs/ALGORITHM.md](docs/ALGORITHM.md).
3. The recognized gesture is turned into an action through an **accessibility service**, which
   is the only way for a regular app to scroll other apps and emulate touches on Wear OS.
   Wear OS 3 has no command to open the notification shade, so the app performs the same
   swipe up that your finger would.
4. When the screen goes off or into ambient mode, the gyroscope is released.

## Requirements

- A watch with **Wear OS 3 or newer** (Android 11 / API 30+) and a **gyroscope**.
- A computer with **ADB** ([Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools)).
  It is needed once, to install the app and enable the service.

## Installation

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

### 2. Get the APK

**Download** `WristGestures-x.y.z.apk` from the
[latest release](https://github.com/iRapoo/WristGestures/releases/latest).
Every release is built from the tagged source code by GitHub Actions.

Or **build it yourself**:

```bash
git clone https://github.com/iRapoo/WristGestures.git
cd WristGestures
./gradlew assembleRelease        # Windows: gradlew.bat assembleRelease
```

The APK will be in `app/build/outputs/apk/release/app-release.apk`.
Building requires JDK 17+ and the Android SDK (the easiest way is to open the project in
Android Studio, which installs both). A self-built APK is signed with your local debug key, so
it cannot update the official release: uninstall one before installing the other.

### 3. Install

```bash
adb install -r WristGestures-1.0.1.apk
```

Updating to a newer release works the same way; settings and the enabled service are kept.

### 4. Enable the accessibility service

The service does all the work and has to be enabled once. It stays enabled after reboots
and app updates.

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

Open **Wrist Gestures** on the watch. While this screen is open, gestures are only
**shown** in the box at the top and are not executed, so you can safely experiment.

| Setting | What it does |
|---|---|
| **Gestures** | Master switch. When off, the gyroscope is not used at all. |
| **Sensitivity** | 1 = only strong flicks, 10 = light flicks. The number below is the threshold in rad/s. If gestures trigger by accident, lower it; if they are hard to trigger, raise it. |
| **Invert direction** | Swaps "flick out" and "flick in". Turn it on if the directions are reversed, e.g. when the watch is on the right wrist. |
| **Vibration** | A short tick when a gesture is recognized. |
| **Actions** | Tap a row to cycle through actions for that gesture: nothing, scroll down / notifications, scroll up / back, notifications, back, watch face. |
| **Gyroscope axis** | The axis around which the wrist rotates. **X** is correct for most watches. The numbers under the test box show the peak rotation speed on each axis over the last second: flick your wrist and pick the axis with the biggest number. |

Recommended routine:

1. Put the watch on the wrist, open the app.
2. Do a few flicks out and in. Check that the box shows the correct direction; if it is
   reversed, turn on **Invert direction**.
3. Move your arm normally, raise the wrist, gesticulate. If the box shows false gestures, reduce
   **Sensitivity**.
4. Leave the app and try it on the watch face: flick out opens notifications, flick out again
   scrolls, flick in scrolls back, and at the top it closes notifications. Flick in on the watch
   face opens quick settings, flick out closes them.

**Gestures work only when the screen is on** (not in ambient mode), the same as on Wear OS 2.
Raise the wrist or tap the screen first.

## Battery

- The gyroscope is used only while the screen is **on** and is released in ambient mode and
  with the screen off. In standby the app does nothing.
- While the screen is on, the app processes 50 samples per second with a few arithmetic
  operations each; it does not keep the CPU awake by itself.
- If you notice a difference in battery life, please report it together with your watch model.

## Privacy and permissions

| Permission / capability | Why |
|---|---|
| Accessibility service: *perform gestures* | Emulating a swipe to open notifications. |
| Accessibility service: *retrieve window content* | Finding the scrollable list on screen to scroll it. The service subscribes only to window changes and does not read or store anything in the background. |
| `VIBRATE` | Haptic feedback. |
| Gyroscope | Gesture recognition. No permission is needed. |

The app has **no INTERNET permission**, so it physically cannot send anything anywhere.
The source code is short and fully commented; you are encouraged to read it.

## Troubleshooting

**"Service is off" although I enabled it.**
Check with `adb shell settings get secure enabled_accessibility_services` that our component is
in the list and `adb shell settings get secure accessibility_enabled` returns `1`. After
reinstalling the app from a different signature, enable it again.

**Nothing happens on the watch face, but the test box shows gestures.**
The service may have been stopped by the system. Toggle it off and on (or run the ADB commands
again). Also make sure the flick action is not set to *nothing*.

**Flick on the watch face switches tiles instead of opening notifications.**
Your watch's system UI exposes the watch face as a scrollable container. Please open an issue
with the output of `adb logcat -s WristGestures` from a **debug** build (it prints the screen
structure before every action).

**Accidental triggers while walking or typing.**
Lower sensitivity, or set the problematic gesture to *nothing*.

**The watch lags after installing.**
The service has no UI and does very little work, so this is unlikely. Turn the service off to
check, and report the watch model if it helps.

## Uninstalling

```bash
adb uninstall xyz.quenix.wristgestures
```

or uninstall it from the watch's app list. The accessibility setting is cleaned up
automatically.

## For developers

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
app/src/test/.../FlickRecognizerTest.kt   # synthetic-signal tests of the recognizer
docs/ALGORITHM.md                          # detailed description of gesture recognition
```

The app uses only the Android framework, with no AndroidX or other libraries, to stay small
on watches with 1 GB of RAM.

### Build and test

```bash
./gradlew testDebugUnitTest   # recognizer unit tests, no device needed
./gradlew installDebug        # build and install the debug build on a connected watch
adb logcat -s WristGestures   # debug build logs gestures, actions and screen structure
```

The debug build also accepts gestures from the computer, so actions can be tested without
moving the wrist:

```bash
# Ignore the real gyroscope while testing (sent again with false to resume)
adb shell am broadcast -a xyz.quenix.wristgestures.DEBUG_GESTURE --ez pause_sensor true

# Trigger a gesture: FLICK_OUT, FLICK_IN or SHAKE
adb shell am broadcast -a xyz.quenix.wristgestures.DEBUG_GESTURE --es gesture FLICK_OUT
```

The debug log prints every movement the recognizer sees, which is the fastest way to tune
thresholds on a new watch:

```
lobe sign=-1 peak=6,6 duration=77ms gap=0ms
lobe sign=1 peak=19,6 duration=97ms gap=38ms
sequence lobes=2 -> FLICK_IN
```

### How the system UI is handled

Findings on the Mobvoi TicWatch Pro 3 system UI (`com.mobvoi.wear.refsysui`), which the
action logic in `ActionPerformer` is built around:

- The **watch face** is a full-screen vertical pager. Scrolling it forward opens notifications,
  scrolling it back opens quick settings. That is why flick out on the watch face opens
  notifications and flick in opens quick settings.
- **Quick settings** is a container that can only scroll forward, and scrolling it forward
  closes the shade. Flick out in quick settings returns to the watch face.
- The **notification panel** is a full-screen container with a list inside. While the list can
  scroll up, it gets the "scroll back" action; at the top only the container offers it, and
  scrolling the container back closes the panel.
- The **Home** key opens the app list, not the watch face, and **Back on the watch face** also
  toggles the app list. So "back" is skipped on the watch face. The watch face is recognized by
  having no text and nothing clickable on screen (its layout only has a content description
  such as "Watch face 13:07").

Other watches may differ. The debug log shows the structure of each screen before an action.

### Publishing a release

Releases are built by [.github/workflows/release.yml](.github/workflows/release.yml) when a tag
`v*` is pushed. The workflow runs the unit tests, builds the release APK signed with the release
key, and attaches `WristGestures-<version>.apk` to a new GitHub release. The version comes from
the tag: `v1.2.3` → versionName `1.2.3`, versionCode `10203`.

```bash
git tag v1.0.1
git push origin v1.0.1
```

**One-time setup.** The signing key is not stored in the repository. Add it under
**Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | the keystore file encoded with base64 (`base64 -w0 release.jks`) |
| `SIGNING_KEYSTORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key password |

For local release builds put the same data into `keystore.properties` in the project root
(git-ignored):

```properties
storeFile=keystore/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

> **Keep a backup of the keystore and passwords.** If the key is lost, new versions can no
> longer be installed as updates: every user would have to uninstall the app first.

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

## License

[MIT](LICENSE). Use, modify and share freely.
