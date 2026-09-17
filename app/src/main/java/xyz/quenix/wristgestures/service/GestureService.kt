package xyz.quenix.wristgestures.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import xyz.quenix.wristgestures.BuildConfig
import xyz.quenix.wristgestures.LiveState
import xyz.quenix.wristgestures.detection.Gesture
import xyz.quenix.wristgestures.detection.GyroGestureSource
import xyz.quenix.wristgestures.settings.GestureAction
import xyz.quenix.wristgestures.settings.GestureSettings

/**
 * The heart of the app: an accessibility service that turns wrist gestures into actions.
 *
 * Lifecycle:
 * 1. The system binds the service once the user enables it (see README) and after every reboot.
 * 2. The gyroscope is listened to **only while the screen is interactive**. In ambient mode or
 *    with the screen off the sensor is released, so the service costs nothing in standby.
 *    This matches Wear OS 2, where wrist gestures also required the screen to be on.
 * 3. A recognized gesture is mapped to a [GestureAction] from settings and executed by
 *    [ActionPerformer].
 *
 * Why an accessibility service? It is the only non-root way for a third-party app to scroll
 * other apps and emulate touches on Wear OS.
 */
class GestureService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var settings: GestureSettings
    private lateinit var performer: ActionPerformer
    private lateinit var gyro: GyroGestureSource
    private lateinit var powerManager: PowerManager
    private var vibrator: Vibrator? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateListening()
    }

    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // --ez pause_sensor true: ignore the real gyroscope while testing from a computer.
            if (intent.hasExtra("pause_sensor")) {
                debugSensorPaused = intent.getBooleanExtra("pause_sensor", false)
                updateListening()
            }
            val name = intent.getStringExtra("gesture")
            Gesture.entries.firstOrNull { it.name == name }?.let(::onGesture)
        }
    }

    private var debugSensorPaused = false

    override fun onServiceConnected() {
        settings = GestureSettings(this)
        performer = ActionPerformer(this)
        gyro = GyroGestureSource(this, ::onGesture)
        powerManager = getSystemService(PowerManager::class.java)
        vibrator = getSystemService(Vibrator::class.java)

        // On Wear OS SCREEN_OFF is also sent when the watch goes to ambient mode.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        settings.prefs.registerOnSharedPreferenceChangeListener(this)

        // Debug builds only: trigger gestures from a computer without moving the wrist:
        // adb shell am broadcast -a xyz.quenix.wristgestures.DEBUG_GESTURE --es gesture FLICK_IN
        if (BuildConfig.DEBUG) {
            val debugFilter = IntentFilter(ACTION_DEBUG_GESTURE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(debugReceiver, debugFilter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(debugReceiver, debugFilter)
            }
        }

        gyro.configure(settings)
        updateListening()
        Log.i(TAG, "Service connected, gyroscope available=${gyro.isAvailable}")
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        gyro.configure(settings)
        updateListening()
    }

    /** Starts or stops the gyroscope depending on the screen state and the master switch. */
    private fun updateListening() {
        val shouldListen = settings.enabled && powerManager.isInteractive && !debugSensorPaused
        if (shouldListen) gyro.start() else gyro.stop()
    }

    private fun onGesture(gesture: Gesture) {
        if (LiveState.calibrationScreenVisible) return

        val action = settings.actionFor(gesture)
        Log.i(TAG, "Gesture $gesture -> $action")
        if (action == GestureAction.NONE) return

        if (settings.haptics) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
        performer.perform(action)
    }

    // The service reacts to the gyroscope, not to UI events.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (::settings.isInitialized) {
            gyro.stop()
            settings.prefs.unregisterOnSharedPreferenceChangeListener(this)
            unregisterReceiver(screenReceiver)
            if (BuildConfig.DEBUG) unregisterReceiver(debugReceiver)
        }
        super.onDestroy()
    }

    private companion object {
        const val TAG = "WristGestures"
        const val ACTION_DEBUG_GESTURE = "xyz.quenix.wristgestures.DEBUG_GESTURE"
    }
}
