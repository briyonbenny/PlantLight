package ie.griffith.plantlight.data

/**
 * Classifies a lux reading into Low / Medium / Bright and averages
 * live sensor samples over a short window so the gauge stays stable.
 *
 * Thresholds are typical indoor values:
 *  - Low:    below 200 lux (a shaded corner or north-facing hall)
 *  - Medium: 200 to 1,000 lux (a few metres from a window)
 *  - Bright: 1,000 lux and above (a windowsill or well-lit desk)
 */
object LightClassifier {
    const val LOW_MAX_LUX = 200f
    const val MEDIUM_MAX_LUX = 1_000f
    const val AVERAGE_WINDOW_MS = 3_000L

    /** Display cap for the gauge; brighter values still classify as Bright. */
    const val GAUGE_MAX_LUX = 2_000f

    fun classify(lux: Float): LightLevel = when {
        lux < LOW_MAX_LUX -> LightLevel.LOW
        lux < MEDIUM_MAX_LUX -> LightLevel.MEDIUM
        else -> LightLevel.BRIGHT
    }
}

/** Sliding-window average of lux samples from the last few seconds. */
class LuxAverager(
    private val windowMs: Long = LightClassifier.AVERAGE_WINDOW_MS
) {
    private val samples = ArrayDeque<Pair<Long, Float>>()

    fun add(lux: Float, now: Long = System.currentTimeMillis()): Float {
        samples.addLast(now to lux)
        while (samples.isNotEmpty() && now - samples.first().first > windowMs) {
            samples.removeFirst()
        }
        return samples.map { it.second }.average().toFloat()
    }

    fun reset() {
        samples.clear()
    }

    val hasSamples: Boolean get() = samples.isNotEmpty()
}
