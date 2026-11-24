package ie.griffith.plantlight.data

/**
 * Milestone 2: collects the light samples of one tracking session.
 *
 * It keeps a running minimum, maximum and average (so the screen can show
 * them live) and stores one sample per second, which is what gets saved
 * to the database when the session ends.
 *
 * This is plain Kotlin with no Android code, so it is easy to unit test.
 */
class LightSessionRecorder(
    private val saveEveryMs: Long = 1_000L
) {
    var startedAt: Long = 0L
        private set
    var lastSampleAt: Long = 0L
        private set
    var count: Int = 0
        private set
    var minLux: Float = Float.MAX_VALUE
        private set
    var maxLux: Float = 0f
        private set
    private var sumLux: Double = 0.0

    /** One saved sample per second: (timestamp, lux). */
    val savedSamples = mutableListOf<Pair<Long, Float>>()

    val averageLux: Float
        get() = if (count == 0) 0f else (sumLux / count).toFloat()

    val durationMs: Long
        get() = if (count == 0) 0L else lastSampleAt - startedAt

    val hasData: Boolean get() = count > 0

    fun start(now: Long = System.currentTimeMillis()) {
        reset()
        startedAt = now
    }

    fun add(lux: Float, now: Long = System.currentTimeMillis()) {
        if (count == 0 && startedAt == 0L) startedAt = now
        count++
        sumLux += lux
        if (lux < minLux) minLux = lux
        if (lux > maxLux) maxLux = lux
        lastSampleAt = now
        val lastSaved = savedSamples.lastOrNull()?.first
        if (lastSaved == null || now - lastSaved >= saveEveryMs) {
            savedSamples.add(now to lux)
        }
    }

    fun reset() {
        startedAt = 0L
        lastSampleAt = 0L
        count = 0
        minLux = Float.MAX_VALUE
        maxLux = 0f
        sumLux = 0.0
        savedSamples.clear()
    }
}

/** Milestone 2: compares the live light with what a plant needs. */
object PlantAdvice {
    enum class Fit { TOO_DARK, GOOD, TOO_BRIGHT }

    fun fit(current: LightLevel, plantNeed: LightLevel): Fit = when {
        current.ordinal < plantNeed.ordinal -> Fit.TOO_DARK
        current.ordinal > plantNeed.ordinal -> Fit.TOO_BRIGHT
        else -> Fit.GOOD
    }
}
