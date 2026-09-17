package xyz.quenix.wristgestures.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import xyz.quenix.wristgestures.BuildConfig
import xyz.quenix.wristgestures.settings.GestureSettings

/**
 * Connects the gyroscope to a [FlickRecognizer].
 *
 * The sensor is only registered between [start] and [stop]. Both the accessibility service
 * and the calibration screen own their own instance, so they can run independently.
 *
 * @param onGesture called on the main thread when a gesture is recognized.
 * @param onSample optional raw data callback (x, y, z in rad/s), used by the calibration screen.
 */
class GyroGestureSource(
    context: Context,
    onGesture: (Gesture) -> Unit,
    private val onSample: ((FloatArray) -> Unit)? = null,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val recognizer = FlickRecognizer(onGesture = onGesture).apply {
        if (BuildConfig.DEBUG) onTrace = { Log.d("WristGestures", it) }
    }

    /** Index into `SensorEvent.values`: 0 = X, 1 = Y, 2 = Z. */
    private var axisIndex = 0

    var isRunning = false
        private set

    /** `false` if the watch has no gyroscope at all. */
    val isAvailable: Boolean get() = gyroscope != null

    /** Applies axis, sensitivity and direction from [settings]. Safe to call while running. */
    fun configure(settings: GestureSettings) {
        axisIndex = settings.axis.index
        recognizer.config = settings.recognizerConfig()
    }

    /** Starts listening. Returns `false` if there is no gyroscope. */
    fun start(): Boolean {
        val sensor = gyroscope ?: return false
        if (!isRunning) {
            // 50 Hz is plenty for a movement that lasts ~100–300 ms and keeps the CPU cost low.
            // maxReportLatencyUs = 0: no batching, gestures must be reported immediately.
            sensorManager.registerListener(this, sensor, SAMPLING_PERIOD_US, 0)
            isRunning = true
        }
        return true
    }

    /** Stops listening and forgets any half-finished movement. */
    fun stop() {
        if (isRunning) {
            sensorManager.unregisterListener(this)
            recognizer.reset()
            isRunning = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        onSample?.invoke(event.values)
        recognizer.onSample(event.timestamp, event.values[axisIndex])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val SAMPLING_PERIOD_US = 20_000
    }
}
