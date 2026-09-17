package xyz.quenix.wristgestures.detection

import kotlin.math.abs
import kotlin.math.sign

/** Gestures that [FlickRecognizer] can report. */
enum class Gesture {
    /** Quick turn of the wrist away from you and back (like on Wear OS 2). */
    FLICK_OUT,

    /** Quick turn of the wrist towards you and back. */
    FLICK_IN,

    /** Several fast back-and-forth turns in a row. */
    SHAKE,
}

/**
 * Turns a stream of angular velocity samples around one axis into [Gesture]s.
 *
 * This class has no Android dependencies, so it can be unit-tested on a desktop JVM
 * (see `FlickRecognizerTest`). Feeding it with real sensor data is done by
 * [GyroGestureSource].
 *
 * ## How it works
 *
 * A wrist flick is a short, fast rotation followed by an equally fast rotation back.
 * On the gyroscope it looks like two opposite "lobes" of angular velocity:
 *
 * ```
 *  rad/s
 *    +6 |          ___
 *       |         /   \            <- lobe 2 (return)
 *  +thr |- - - - / - - \ - - - - -
 *     0 |-------/-------\-------/---
 *  -thr |- - \ - - - - - \ - - / - -
 *       |     \___/        \__/     <- lobe 1 (flick), lobe 3 (overshoot)
 *    -6 |
 *       +--------------------------> time
 * ```
 *
 * 1. A **lobe** starts when |ω| rises above `threshold` and ends when |ω| drops below
 *    `threshold * releaseRatio` (hysteresis prevents noise from splitting one lobe in two)
 *    or when the sign flips.
 * 2. A lobe that lasts longer than `maxLobeNanos` is a slow arm movement (for example raising
 *    the wrist to look at the watch), so the whole sequence is discarded.
 * 3. Consecutive lobes with alternating signs and short gaps form a **sequence**.
 * 4. Once no new lobe starts for `settleNanos`, the sequence is classified:
 *    - 1 lobe: ignored (a one-way turn is not a flick);
 *    - 2–3 lobes: a flick; its direction is the sign of the **stronger** of the first two
 *      lobes (the weaker one is the return or a small wind-up before the flick);
 *    - 4+ lobes: a shake.
 * 5. After a gesture the recognizer ignores new lobes for `cooldownNanos`, so the
 *    wrist settling down cannot trigger a second gesture.
 *
 * Because of step 4 a gesture is reported about `settleNanos` after the movement ends;
 * this small delay is what allows telling a flick from the beginning of a shake.
 *
 * Direction convention: for a watch on the **left** wrist the gyroscope X axis points to
 * the crown (towards the hand). Turning the display away from you is a negative rotation
 * around X, so a negative main lobe is [Gesture.FLICK_OUT]. For other ways of wearing the
 * watch use [Config.invert].
 *
 * The class is not thread-safe: call [onSample] and [reset] from one thread.
 */
class FlickRecognizer(
    config: Config = Config(),
    private val onGesture: (Gesture) -> Unit,
) {

    /** Tuning parameters. Times are in nanoseconds, same as `SensorEvent.timestamp`. */
    data class Config(
        /** Angular velocity (rad/s) a movement must exceed to count. Lower = more sensitive. */
        val threshold: Float = 5f,
        /** A lobe ends when |ω| falls below `threshold * releaseRatio`. */
        val releaseRatio: Float = 0.5f,
        /** Lobes longer than this are slow movements, not flicks. */
        val maxLobeNanos: Long = 350 * MS,
        /** Max pause between two lobes of the same gesture; also the settle time. */
        val settleNanos: Long = 250 * MS,
        /** Dead time after a recognized gesture. */
        val cooldownNanos: Long = 600 * MS,
        /** Swap [Gesture.FLICK_OUT] and [Gesture.FLICK_IN] (right wrist, flipped screen). */
        val invert: Boolean = false,
    )

    /** Current configuration. Can be changed at any time; applies from the next sample. */
    var config: Config = config

    // Current lobe.
    private var inLobe = false
    private var lobeSign = 0f
    private var lobeStart = 0L
    private var lobeTooLong = false

    // Current sequence of alternating lobes.
    private var lobeCount = 0
    private var firstSign = 0f
    private var firstPeak = 0f
    private var secondPeak = 0f
    private var lastSign = 0f
    private var lastLobeEnd = 0L

    private var cooldownUntil = Long.MIN_VALUE

    /**
     * Optional trace of every lobe and sequence, for tuning on a real watch.
     * Debug builds route it to logcat (`adb logcat -s WristGestures`).
     */
    var onTrace: ((String) -> Unit)? = null

    private var lobePeak = 0f

    /**
     * Feeds one sample.
     *
     * @param timestampNanos monotonic sample time, e.g. `SensorEvent.timestamp`.
     * @param angularVelocity rotation speed around the chosen axis, rad/s.
     */
    fun onSample(timestampNanos: Long, angularVelocity: Float) {
        val c = config
        val w = if (c.invert) -angularVelocity else angularVelocity

        if (inLobe) {
            val released = abs(w) < c.threshold * c.releaseRatio || sign(w) != lobeSign
            if (released) {
                endLobe(timestampNanos)
            } else {
                lobePeak = maxOf(lobePeak, abs(w))
                if (timestampNanos - lobeStart > c.maxLobeNanos) lobeTooLong = true
            }
        }

        // Not `else`: the same sample may end one lobe and start the opposite one.
        if (!inLobe) {
            if (lobeCount > 0 && timestampNanos - lastLobeEnd > c.settleNanos) {
                finishSequence()
            }
            if (abs(w) >= c.threshold && timestampNanos >= cooldownUntil) {
                inLobe = true
                lobeSign = sign(w)
                lobeStart = timestampNanos
                lobeTooLong = false
                lobePeak = abs(w)
            }
        }
    }

    /** Forgets any movement in progress, e.g. when the sensor is stopped. */
    fun reset() {
        inLobe = false
        lobeCount = 0
        cooldownUntil = Long.MIN_VALUE
    }

    private fun endLobe(timestampNanos: Long) {
        inLobe = false
        onTrace?.invoke(
            "lobe sign=${lobeSign.toInt()} peak=%.1f duration=%dms gap=%dms%s".format(
                lobePeak,
                (timestampNanos - lobeStart) / MS,
                if (lobeCount == 0) 0 else (lobeStart - lastLobeEnd) / MS,
                if (lobeTooLong) " TOO LONG" else "",
            ),
        )

        if (lobeTooLong) {
            // Slow movement: whatever was collected so far is not a gesture.
            lobeCount = 0
            return
        }

        when {
            lobeCount == 0 -> startSequence()
            lobeSign != lastSign -> {
                lobeCount++
                if (lobeCount == 2) secondPeak = lobePeak
            }
            else -> {
                // Same direction twice in a row: the previous sequence is over.
                finishSequence()
                startSequence()
            }
        }
        lastSign = lobeSign
        lastLobeEnd = timestampNanos
    }

    private fun startSequence() {
        firstSign = lobeSign
        firstPeak = lobePeak
        lobeCount = 1
    }

    private fun finishSequence() {
        val count = lobeCount
        lobeCount = 0

        val gesture = when {
            count >= SHAKE_MIN_LOBES -> Gesture.SHAKE
            count >= 2 -> {
                // The main movement is the stronger of the first two lobes. The other one is
                // either the return (after it) or a small wind-up in the opposite direction
                // (before it), which people do instinctively before a flick.
                val mainSign = if (secondPeak > firstPeak) -firstSign else firstSign
                if (mainSign < 0) Gesture.FLICK_OUT else Gesture.FLICK_IN
            }
            else -> null
        }
        onTrace?.invoke("sequence lobes=$count -> ${gesture ?: "ignored"}")
        if (gesture == null) return

        cooldownUntil = lastLobeEnd + config.cooldownNanos
        onGesture(gesture)
    }

    companion object {
        /** One millisecond in nanoseconds. */
        const val MS = 1_000_000L

        /** Minimal number of alternating lobes that counts as a shake. */
        const val SHAKE_MIN_LOBES = 4
    }
}
