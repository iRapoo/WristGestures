# Gesture recognition

This document explains how `FlickRecognizer` turns gyroscope data into gestures, and why it is
built this way. The code itself is in
[`app/src/main/java/xyz/quenix/wristgestures/detection/FlickRecognizer.kt`](../app/src/main/java/xyz/quenix/wristgestures/detection/FlickRecognizer.kt).

## Input signal

The gyroscope reports angular velocity ω (rad/s) around three axes of the watch:

```
          12 o'clock (+Y)
               ▲
               │
  9 o'clock ───┼───► 3 o'clock / crown (+X)
               │
            (+Z points out of the display, towards your eyes)
```

For a watch on the **left** wrist the crown points towards the hand, so **X runs along the
forearm**. Turning the wrist is a rotation around X; the other axes barely change. That is why X
is the default axis. Watches with a different sensor orientation can pick another axis on the
settings screen, which shows the peak ω on each axis.

Sampling rate: 50 Hz (every 20 ms). A flick lasts about 100–300 ms, so it spans 5–15 samples.

## What a flick looks like

A flick is a fast rotation and a fast rotation back. Around X it produces two **lobes** of
opposite sign, often with a small overshoot:

```
 ω (rad/s)
   +8 │              ╭──╮
      │             ╱    ╲
 +thr ┼ ─ ─ ─ ─ ─ ─╱─ ─ ─ ╲─ ─ ─ ─ ─ ─ ─ ─
      │           ╱        ╲
    0 ┼──╮       ╱          ╲       ╭────
      │   ╲     ╱            ╲     ╱
 -thr ┼ ─ ─╲─ ─╱─ ─ ─ ─ ─ ─ ─ ╲─ ─╱─ ─ ─ ─
      │     ╲_╱                ╲_╱
   -8 │    lobe 1      lobe 2    lobe 3
      └─────────────────────────────────► t
           flick       return    overshoot
```

Other movements look different:

| Movement | Signature | Result |
|---|---|---|
| Flick | 2–3 short alternating lobes | gesture |
| Shake | 4+ short alternating lobes | gesture |
| Raising the wrist to look at the watch | one long lobe | ignored |
| Turning the wrist and holding it there | one lobe, no return | ignored |
| Slow wrist rotation back and forth | long lobes | ignored |
| Walking, typing | mostly below the threshold | ignored |

## Steps

### 1. Lobes with hysteresis

A lobe **starts** when |ω| ≥ `threshold` and **ends** when |ω| < `threshold × releaseRatio`
(0.5 by default) or when ω changes sign.

Two thresholds (hysteresis) keep sensor noise near the threshold from cutting one lobe into
several short ones.

### 2. Rejecting slow movements

If a lobe lasts longer than `maxLobeNanos` (350 ms), the movement is slow and deliberate, not a
flick. The whole sequence collected so far is thrown away.

Speed alone is not enough: a vigorous arm swing can briefly exceed the threshold, but it takes
much longer than a flick.

### 3. Grouping lobes

A lobe continues the current sequence if its sign is **opposite** to the previous lobe. A lobe
with the **same** sign closes the previous sequence and starts a new one.

### 4. Classification after a pause

When no lobe has started for `settleNanos` (250 ms) since the last one ended, the sequence is
classified:

| Lobes | Gesture |
|---|---|
| 1 | none |
| 2–3 | flick; direction = sign of the **stronger** of the first two lobes |
| 4+ | shake |

Waiting for the pause is what distinguishes a flick (2 lobes) from the beginning of a shake
(which also starts with 2 lobes). The cost is a ~250 ms delay after the movement, which feels
natural because the wrist is still returning.

**Why the stronger lobe and not the first one.** Before a flick people instinctively make a
small wind-up in the opposite direction. The wind-up can be fast enough to cross the threshold,
and then it becomes the first lobe. Real data from a TicWatch Pro 3 (peak ω, rad/s):

| Movement | Lobe 1 | Lobe 2 |
|---|---|---|
| flick out | −19.4, −20.0, −18.0, −14.7 | +14.7, +11.5, +12.7, +7.4 |
| flick in (with wind-up) | −6.6, −6.7, −7.2 | +19.6, +17.9, +18.6 |

With "first lobe wins" every flick in above was reported as a flick out. The main movement is
always clearly stronger than both the wind-up before it and the return after it, so the
stronger of the two lobes gives the right direction in both cases. The overshoot (lobe 3) is
not considered.

### 5. Cooldown

After a gesture, new lobes are ignored for `cooldownNanos` (600 ms). The wrist wobbles a bit as
it settles, and without a cooldown those wobbles could be seen as another gesture.

## Direction

With X along the forearm towards the hand (left wrist), turning the display **away from you**
is a **negative** rotation around X by the right-hand rule. So:

- main (stronger) lobe negative → `FLICK_OUT`
- main (stronger) lobe positive → `FLICK_IN`

On the right wrist, or when the screen is flipped in the system settings, the sign changes;
the **Invert direction** setting multiplies ω by −1 before processing.

## Sensitivity

The settings screen maps sensitivity 1–10 to the threshold:

```
threshold = 8.6 − 0.6 × sensitivity   (rad/s)

sensitivity:  1    2    3    4    5    6    7    8    9    10
threshold:   8.0  7.4  6.8  6.2  5.6  5.0  4.4  3.8  3.2  2.6
```

The default value 6 (5.0 rad/s) is a starting point for a deliberate flick. Everyone moves
differently, so use the live peak values on the settings screen: compare the numbers for your
flicks with the numbers for ordinary arm movements and pick a threshold between them.

## Testing

`FlickRecognizerTest` builds synthetic signals from half-sine lobes at 50 Hz and checks flicks
in both directions, overshoot, inversion, shakes, and the rejection of one-way turns, slow
rotations and weak movements. The recognizer has no Android dependencies, so the tests run on
a regular JVM:

```bash
./gradlew testDebugUnitTest
```

## Ideas for improvement

- Using the accelerometer to reject flicks during vigorous activity (running).
- Automatic calibration: record a few flicks and pick the threshold and axis.
- Separate thresholds for flicks and shakes.
- Recognizing additional gestures, for example rotation around another axis ("tilt") or a
  double flick.
