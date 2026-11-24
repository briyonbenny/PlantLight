package ie.griffith.plantlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Milestone 2: unit tests for the sensor logic (run on the computer, no phone needed). */
class LightLogicTest {

    @Test
    fun classify_usesLowMediumBrightThresholds() {
        assertEquals(LightLevel.LOW, LightClassifier.classify(150f))
        assertEquals(LightLevel.MEDIUM, LightClassifier.classify(200f))
        assertEquals(LightLevel.MEDIUM, LightClassifier.classify(999f))
        assertEquals(LightLevel.BRIGHT, LightClassifier.classify(1_000f))
    }

    @Test
    fun recorder_tracksMinAverageMax() {
        val recorder = LightSessionRecorder()
        recorder.start(now = 0L)
        recorder.add(100f, now = 0L)
        recorder.add(300f, now = 500L)
        recorder.add(200f, now = 1_000L)
        assertEquals(100f, recorder.minLux, 0.01f)
        assertEquals(300f, recorder.maxLux, 0.01f)
        assertEquals(200f, recorder.averageLux, 0.01f)
        assertEquals(3, recorder.count)
        assertEquals(1_000L, recorder.durationMs)
    }

    @Test
    fun recorder_savesOneSamplePerSecond() {
        val recorder = LightSessionRecorder()
        recorder.start(now = 0L)
        for (i in 0 until 10) recorder.add(500f, now = i * 500L) // 0 .. 4.5 seconds
        assertEquals(5, recorder.savedSamples.size)
    }

    @Test
    fun averager_isStableOnlyWhenReadingsAreClose() {
        val steady = LuxAverager()
        steady.add(500f, 0L); steady.add(510f, 100L); steady.add(495f, 200L)
        assertTrue(steady.isStable())

        val moving = LuxAverager()
        moving.add(100f, 0L); moving.add(600f, 100L); moving.add(1_200f, 200L)
        assertFalse(moving.isStable())
    }

    @Test
    fun plantAdvice_comparesLiveLevelWithPlantNeed() {
        assertEquals(PlantAdvice.Fit.TOO_DARK, PlantAdvice.fit(LightLevel.LOW, LightLevel.MEDIUM))
        assertEquals(PlantAdvice.Fit.GOOD, PlantAdvice.fit(LightLevel.MEDIUM, LightLevel.MEDIUM))
        assertEquals(PlantAdvice.Fit.TOO_BRIGHT, PlantAdvice.fit(LightLevel.BRIGHT, LightLevel.LOW))
    }
}
