package xyz.quenix.wristgestures.settings

import android.content.Context
import android.content.SharedPreferences
import xyz.quenix.wristgestures.R
import xyz.quenix.wristgestures.detection.FlickRecognizer
import xyz.quenix.wristgestures.detection.Gesture

/** Gyroscope axis the recognizer listens to. */
enum class Axis(val index: Int) { X(0), Y(1), Z(2) }

/**
 * What to do when a gesture is recognized. See `ActionPerformer` for the exact behavior.
 *
 * To add a new action: add a constant here, a label in `strings.xml`
 * and a branch in `ActionPerformer.perform`.
 */
enum class GestureAction(/** String resource with the label. */ val label: Int) {
    NONE(R.string.action_none),

    /** Scroll the list down; on the watch face open notifications. */
    NEXT(R.string.action_next),

    /** Scroll the list up; at the top of the list (or when nothing scrolls) go back. */
    PREVIOUS(R.string.action_previous),

    OPEN_NOTIFICATIONS(R.string.action_open_notifications),
    BACK(R.string.action_back),
    HOME(R.string.action_home),
}

/**
 * All user settings, stored in [SharedPreferences].
 *
 * The service subscribes to [prefs] changes, so settings changed on the calibration screen
 * apply immediately without restarting anything.
 */
class GestureSettings(context: Context) {

    val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Master switch. The accessibility service stays enabled but ignores the gyroscope. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** 1 (needs a strong flick) .. 10 (reacts to a light flick). */
    var sensitivity: Int
        get() = prefs.getInt(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)
        set(value) = prefs.edit()
            .putInt(KEY_SENSITIVITY, value.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)).apply()

    var axis: Axis
        get() = enumOrDefault(prefs.getString(KEY_AXIS, null), Axis.X)
        set(value) = prefs.edit().putString(KEY_AXIS, value.name).apply()

    /** Swaps flick out / flick in. Needed for the right wrist or a flipped screen. */
    var invert: Boolean
        get() = prefs.getBoolean(KEY_INVERT, false)
        set(value) = prefs.edit().putBoolean(KEY_INVERT, value).apply()

    /** Short vibration when a gesture is recognized. */
    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    fun actionFor(gesture: Gesture): GestureAction =
        enumOrDefault(prefs.getString(actionKey(gesture), null), defaultAction(gesture))

    fun setAction(gesture: Gesture, action: GestureAction) =
        prefs.edit().putString(actionKey(gesture), action.name).apply()

    /** Builds the recognizer configuration from the current settings. */
    fun recognizerConfig() = FlickRecognizer.Config(
        threshold = thresholdFor(sensitivity),
        invert = invert,
    )

    companion object {
        const val MIN_SENSITIVITY = 1
        const val MAX_SENSITIVITY = 10
        const val DEFAULT_SENSITIVITY = 6

        private const val KEY_ENABLED = "enabled"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_AXIS = "axis"
        private const val KEY_INVERT = "invert"
        private const val KEY_HAPTICS = "haptics"

        /**
         * Maps sensitivity to the angular velocity threshold, rad/s:
         * 1 → 8.0, 6 → 5.0 (default), 10 → 2.6.
         * See docs/ALGORITHM.md for how to choose a value.
         */
        fun thresholdFor(sensitivity: Int): Float = 8.6f - 0.6f * sensitivity

        /** Defaults that mirror the Wear OS 2 wrist gestures. */
        fun defaultAction(gesture: Gesture) = when (gesture) {
            Gesture.FLICK_OUT -> GestureAction.NEXT
            Gesture.FLICK_IN -> GestureAction.PREVIOUS
            Gesture.SHAKE -> GestureAction.BACK
        }

        private fun actionKey(gesture: Gesture) = "action_${gesture.name.lowercase()}"

        private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
