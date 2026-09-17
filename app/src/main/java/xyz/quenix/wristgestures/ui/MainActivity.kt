package xyz.quenix.wristgestures.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Button
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import xyz.quenix.wristgestures.LiveState
import xyz.quenix.wristgestures.R
import xyz.quenix.wristgestures.detection.Gesture
import xyz.quenix.wristgestures.detection.GyroGestureSource
import xyz.quenix.wristgestures.service.GestureService
import xyz.quenix.wristgestures.settings.Axis
import xyz.quenix.wristgestures.settings.GestureAction
import xyz.quenix.wristgestures.settings.GestureSettings
import kotlin.math.abs
import kotlin.math.max

/**
 * Settings and calibration screen.
 *
 * While it is open, the screen runs its own gyroscope listener and shows:
 * - the last recognized gesture (so you can tune sensitivity and direction);
 * - peak angular velocity per axis over the last second (so you can see which axis your
 *   flick actually rotates around and how strong it is).
 *
 * The service does not perform actions while this screen is visible (see [LiveState]).
 */
class MainActivity : Activity() {

    private lateinit var settings: GestureSettings
    private lateinit var gyro: GyroGestureSource
    private var vibrator: Vibrator? = null

    private lateinit var scroll: ScrollView
    private lateinit var serviceStatus: TextView
    private lateinit var lastGesture: TextView
    private lateinit var liveValues: TextView
    private lateinit var enabledSwitch: Switch
    private lateinit var sensitivityCaption: TextView
    private lateinit var sensitivityValue: TextView
    private lateinit var axisButton: Button
    private lateinit var invertSwitch: Switch
    private lateinit var hapticsSwitch: Switch
    private val actionButtons = mutableMapOf<Gesture, Button>()

    // Peak |ω| per axis, decaying so the numbers show roughly the last second.
    private val peaks = FloatArray(3)
    private var lastLiveUpdate = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = GestureSettings(this)
        gyro = GyroGestureSource(this, ::showGesture, ::showSample)
        vibrator = getSystemService(Vibrator::class.java)

        scroll = findViewById(R.id.scroll)
        serviceStatus = findViewById(R.id.service_status)
        lastGesture = findViewById(R.id.last_gesture)
        liveValues = findViewById(R.id.live_values)
        enabledSwitch = findViewById(R.id.enabled_switch)
        sensitivityCaption = findViewById(R.id.sensitivity_caption)
        sensitivityValue = findViewById(R.id.sensitivity_value)
        axisButton = findViewById(R.id.axis_button)
        invertSwitch = findViewById(R.id.invert_switch)
        hapticsSwitch = findViewById(R.id.haptics_switch)
        actionButtons[Gesture.FLICK_OUT] = findViewById(R.id.action_flick_out)
        actionButtons[Gesture.FLICK_IN] = findViewById(R.id.action_flick_in)
        actionButtons[Gesture.SHAKE] = findViewById(R.id.action_shake)

        enabledSwitch.setOnCheckedChangeListener { _, checked -> settings.enabled = checked }
        invertSwitch.setOnCheckedChangeListener { _, checked -> settings.invert = checked; applySettings() }
        hapticsSwitch.setOnCheckedChangeListener { _, checked -> settings.haptics = checked }

        findViewById<Button>(R.id.sensitivity_minus).setOnClickListener { changeSensitivity(-1) }
        findViewById<Button>(R.id.sensitivity_plus).setOnClickListener { changeSensitivity(+1) }

        axisButton.setOnClickListener {
            settings.axis = Axis.entries[(settings.axis.ordinal + 1) % Axis.entries.size]
            applySettings()
        }

        actionButtons.forEach { (gesture, button) ->
            button.setOnClickListener {
                val actions = GestureAction.entries
                val next = actions[(settings.actionFor(gesture).ordinal + 1) % actions.size]
                settings.setAction(gesture, next)
                refresh()
            }
        }

        findViewById<Button>(R.id.open_accessibility).setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
    }

    override fun onResume() {
        super.onResume()
        LiveState.calibrationScreenVisible = true
        refresh()
        applySettings()
        if (!gyro.start()) lastGesture.setText(R.string.no_gyroscope)
        // Lets the rotating crown / bezel scroll the screen.
        scroll.requestFocus()
    }

    override fun onPause() {
        LiveState.calibrationScreenVisible = false
        gyro.stop()
        super.onPause()
    }

    private fun changeSensitivity(delta: Int) {
        settings.sensitivity += delta
        applySettings()
    }

    private fun applySettings() {
        gyro.configure(settings)
        refresh()
    }

    /** Re-reads all settings into the views. */
    private fun refresh() {
        serviceStatus.setText(if (isServiceEnabled()) R.string.service_on else R.string.service_off)
        enabledSwitch.isChecked = settings.enabled
        sensitivityCaption.text = getString(
            R.string.sensitivity_caption, GestureSettings.thresholdFor(settings.sensitivity),
        )
        sensitivityValue.text = settings.sensitivity.toString()
        axisButton.text = getString(R.string.axis, settings.axis.name)
        invertSwitch.isChecked = settings.invert
        hapticsSwitch.isChecked = settings.haptics
        actionButtons.forEach { (gesture, button) ->
            button.text = getString(
                R.string.action_row, getString(gestureLabel(gesture)), getString(settings.actionFor(gesture).label),
            )
        }
    }

    private fun showGesture(gesture: Gesture) {
        lastGesture.setText(gestureLabel(gesture))
        if (settings.haptics) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    private fun showSample(values: FloatArray) {
        for (i in 0..2) peaks[i] = max(peaks[i] * PEAK_DECAY, abs(values[i]))

        val now = SystemClock.elapsedRealtime()
        if (now - lastLiveUpdate < LIVE_UPDATE_MS) return
        lastLiveUpdate = now
        liveValues.text = getString(R.string.live_values, peaks[0], peaks[1], peaks[2])
    }

    /** Checks the secure setting that lists enabled accessibility services. */
    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, GestureService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private companion object {
        const val LIVE_UPDATE_MS = 100L

        /** At 50 Hz, 0.96 per sample halves the peak in ~0.35 s. */
        const val PEAK_DECAY = 0.96f

        fun gestureLabel(gesture: Gesture) = when (gesture) {
            Gesture.FLICK_OUT -> R.string.gesture_flick_out
            Gesture.FLICK_IN -> R.string.gesture_flick_in
            Gesture.SHAKE -> R.string.gesture_shake
        }
    }
}
