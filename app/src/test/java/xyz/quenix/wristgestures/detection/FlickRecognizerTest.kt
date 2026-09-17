package xyz.quenix.wristgestures.detection

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Tests the recognizer with synthetic gyroscope signals sampled at 50 Hz.
 *
 * Each movement is described as a list of half-sine "lobes" (amplitude in rad/s and duration
 * in ms), which is close to what a real wrist flick looks like on the gyroscope.
 */
class FlickRecognizerTest {

    private data class Lobe(val amplitude: Float, val durationMs: Long)

    private fun recognize(vararg lobes: Lobe, config: FlickRecognizer.Config = FlickRecognizer.Config()): List<Gesture> {
        val result = mutableListOf<Gesture>()
        val recognizer = FlickRecognizer(config) { result += it }
        var t = 0L

        fun sample(value: Float) {
            recognizer.onSample(t * FlickRecognizer.MS, value)
            t += SAMPLE_MS
        }

        repeat(10) { sample(0f) }
        for (lobe in lobes) {
            val samples = (lobe.durationMs / SAMPLE_MS).toInt()
            for (i in 0 until samples) {
                sample(lobe.amplitude * sin(PI * (i + 0.5) / samples).toFloat())
            }
        }
        // One second of stillness lets the recognizer settle and report.
        repeat(50) { sample(0f) }
        return result
    }

    @Test
    fun `negative then positive lobe is a flick out`() {
        assertEquals(listOf(Gesture.FLICK_OUT), recognize(Lobe(-8f, 160), Lobe(7f, 160)))
    }

    @Test
    fun `positive then negative lobe is a flick in`() {
        assertEquals(listOf(Gesture.FLICK_IN), recognize(Lobe(8f, 160), Lobe(-7f, 160)))
    }

    @Test
    fun `small overshoot after the return still counts as one flick`() {
        assertEquals(listOf(Gesture.FLICK_OUT), recognize(Lobe(-8f, 160), Lobe(8f, 160), Lobe(-6f, 100)))
    }

    // Real data from a TicWatch Pro 3: a small wind-up in the opposite direction before the flick.
    @Test
    fun `wind-up before a flick in does not flip the direction`() {
        assertEquals(listOf(Gesture.FLICK_IN), recognize(Lobe(-6.6f, 80), Lobe(19.6f, 100)))
    }

    @Test
    fun `strong flick out with a weaker return stays a flick out`() {
        assertEquals(listOf(Gesture.FLICK_OUT), recognize(Lobe(-19.4f, 140), Lobe(14.7f, 160)))
    }

    @Test
    fun `invert swaps directions`() {
        val config = FlickRecognizer.Config(invert = true)
        assertEquals(listOf(Gesture.FLICK_IN), recognize(Lobe(-8f, 160), Lobe(7f, 160), config = config))
    }

    @Test
    fun `four alternating lobes are a shake`() {
        assertEquals(
            listOf(Gesture.SHAKE),
            recognize(Lobe(8f, 120), Lobe(-8f, 120), Lobe(8f, 120), Lobe(-8f, 120), Lobe(8f, 120)),
        )
    }

    @Test
    fun `one-way turn is ignored`() {
        assertEquals(emptyList<Gesture>(), recognize(Lobe(-8f, 200)))
    }

    @Test
    fun `slow rotation is ignored even if fast enough at its peak`() {
        assertEquals(emptyList<Gesture>(), recognize(Lobe(-8f, 900), Lobe(8f, 900)))
    }

    @Test
    fun `weak movement below threshold is ignored`() {
        assertEquals(emptyList<Gesture>(), recognize(Lobe(-3f, 160), Lobe(3f, 160)))
    }

    @Test
    fun `lower threshold makes weak flicks count`() {
        val config = FlickRecognizer.Config(threshold = 2.5f)
        assertEquals(listOf(Gesture.FLICK_OUT), recognize(Lobe(-4f, 160), Lobe(4f, 160), config = config))
    }

    private companion object {
        const val SAMPLE_MS = 20L
    }
}
