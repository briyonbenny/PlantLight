package ie.griffith.plantlight.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import ie.griffith.plantlight.PlantLightApp
import ie.griffith.plantlight.PlantLightIntents
import ie.griffith.plantlight.R
import ie.griffith.plantlight.data.LightClassifier
import ie.griffith.plantlight.data.LightLevel
import ie.griffith.plantlight.data.LightReading
import ie.griffith.plantlight.data.LightUnit
import ie.griffith.plantlight.data.LuxAverager
import ie.griffith.plantlight.data.Plant
import ie.griffith.plantlight.data.PlantAdvice
import ie.griffith.plantlight.data.PlantCatalog
import ie.griffith.plantlight.data.SettingsRepository
import ie.griffith.plantlight.data.Spot
import ie.griffith.plantlight.data.TrackingSession
import ie.griffith.plantlight.data.namesFor
import ie.griffith.plantlight.databinding.ActivityTrackingBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Milestone 2 screen: live light tracking for one saved spot.
 *
 * How the sensor data is used:
 *  1. The light sensor sends lux values to onSensorChanged (we keep the latest one).
 *  2. Every half second a "tick" takes that value: it is added to the live graph,
 *     and (while tracking) to the session recorder for min / average / max.
 *  3. The 3-second average decides the live light level, which is compared with
 *     what the spot's best-suited plant needs ("Good" / "Too dark" / "Too bright").
 *  4. When the user stops, the session and its readings are saved in Room and the
 *     spot's light level is updated, so its plant suggestions change automatically.
 */
class TrackingActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityTrackingBinding
    private lateinit var app: PlantLightApp
    private val viewModel: TrackingViewModel by viewModels()

    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var spotId: Long = -1L
    private var spot: Spot? = null
    private var targetPlant: Plant? = null
    private var unit: LightUnit = LightUnit.LUX

    private val averager = LuxAverager()
    private var latestLux: Float? = null

    // While tracking, the back button asks "save or discard?" instead of leaving.
    private val backWhileTracking = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = askSaveOrDiscard()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.setUp(this, binding.root, binding.toolbar)
        app = application as PlantLightApp

        // The spot ID comes from Spot Details through an explicit Intent.
        spotId = intent.getLongExtra(PlantLightIntents.EXTRA_SPOT_ID, -1L)
        if (spotId <= 0L) {
            Toast.makeText(this, R.string.error_missing_spot, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this, backWhileTracking)
        binding.buttonStartStop.setOnClickListener {
            if (viewModel.isTracking) stopAndSave() else startTracking()
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor == null) {
            // No sensor: explain and disable tracking instead of crashing.
            binding.textNoSensor.visibility = View.VISIBLE
            binding.buttonStartStop.isEnabled = false
            binding.textLiveLux.text = "—"
            binding.textLiveLevel.visibility = View.GONE
        } else {
            binding.textLiveLux.setText(R.string.waiting_for_sensor)
        }

        // Redraw the graph after a rotation.
        viewModel.graphPoints.forEach { binding.graph.addPoint(it) }
        updateButton()
        updateStats()

        lifecycleScope.launch {
            // Load the spot and the plant it is best suited for.
            val loaded = app.database.spotDao().getById(spotId)
            if (loaded == null) {
                finish()
                return@launch
            }
            spot = loaded
            binding.toolbar.title = getString(R.string.track_title, loaded.name)
            targetPlant = app.database.plantDao().getByLightNeed(loaded.lightLevel).firstOrNull()
        }
        lifecycleScope.launch {
            app.settings.units.collect { unit = it; updateStats() }
        }

        // Sample the latest reading every 500 ms, but only while the screen is visible.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(SAMPLE_EVERY_MS)
                    tick()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Register the sensor only while the screen is visible (saves battery).
        lightSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_LIGHT) return
        val lux = event.values[0]
        // Just remember the newest value; tick() uses it every 500 ms.
        latestLux = lux
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for the light sensor.
    }

    /**
     * Called every 500 ms. The light sensor only reports when the value changes,
     * so sampling on a timer gives evenly spaced points for the graph and stats.
     */
    private fun tick() {
        val lux = latestLux ?: return
        binding.graph.addPoint(lux)
        viewModel.graphPoints.addLast(lux)
        while (viewModel.graphPoints.size > 120) viewModel.graphPoints.removeFirst()

        if (viewModel.isTracking) {
            viewModel.recorder.add(lux)
            updateStats()
        }
        // 3-second average decides the live level (smooths flicker).
        showLive(averager.add(lux))
    }

    /** Shows the live level and compares it with the target plant's needs. */
    private fun showLive(averageLux: Float) {
        val level = LightClassifier.classify(averageLux)
        binding.textLiveLux.text = SettingsRepository.format(averageLux, unit)
        binding.textLiveLevel.visibility = View.VISIBLE
        binding.textLiveLevel.text = getString(R.string.level_light, level.label)
        binding.textLiveLevel.setBackgroundResource(badgeBackground(level))

        val plant = targetPlant
        if (plant != null) {
            val need = LightLevel.fromStorage(plant.lightNeed)
            binding.textStatus.text = when (PlantAdvice.fit(level, need)) {
                PlantAdvice.Fit.GOOD -> getString(R.string.status_good, plant.name)
                PlantAdvice.Fit.TOO_DARK ->
                    getString(R.string.status_too_dark, plant.name, need.label.lowercase())
                PlantAdvice.Fit.TOO_BRIGHT ->
                    getString(R.string.status_too_bright, plant.name, need.label.lowercase())
            }
        }
        binding.textLivePlants.text = getString(
            R.string.live_suits,
            level.label.lowercase(),
            PlantCatalog.namesFor(level).take(3).joinToString(", ")
        )
    }

    private fun startTracking() {
        viewModel.recorder.start()
        viewModel.isTracking = true
        updateButton()
        updateStats()
    }

    /** Saves the session summary + one reading per second, then updates the spot. */
    private fun stopAndSave(finishAfter: Boolean = false) {
        val recorder = viewModel.recorder
        val currentSpot = spot ?: return
        if (recorder.count < MIN_SAMPLES) {
            Snackbar.make(binding.root, R.string.track_too_short, Snackbar.LENGTH_SHORT).show()
            return
        }
        val average = recorder.averageLux
        val level = LightClassifier.classify(average)
        val session = TrackingSession(
            spotId = currentSpot.id,
            startedAt = recorder.startedAt,
            durationMs = recorder.durationMs,
            minLux = recorder.minLux,
            avgLux = average,
            maxLux = recorder.maxLux,
            sampleCount = recorder.count,
            lightLevel = level.storageKey
        )
        val samples = recorder.savedSamples.toList()

        viewModel.isTracking = false
        recorder.reset()
        updateButton()
        updateStats()

        lifecycleScope.launch {
            val db = app.database
            val sessionId = db.sessionDao().insert(session)
            db.readingDao().insertAll(samples.map { (time, lux) ->
                LightReading(spotId = currentSpot.id, lux = lux, timestamp = time, sessionId = sessionId)
            })
            // The spot now uses the tracked average, so Spot Details shows
            // new plant suggestions automatically (Room Flow).
            val updated = currentSpot.copy(
                averageLux = average,
                lightLevel = level.storageKey,
                isManual = false
            )
            db.spotDao().update(updated)
            spot = updated
            // Reload the target plant so the live Good / Too dark / Too bright
            // status matches the light level that was just saved.
            targetPlant = db.plantDao().getByLightNeed(updated.lightLevel).firstOrNull()
            Snackbar.make(
                binding.root,
                getString(R.string.track_saved, SettingsRepository.format(average, unit), level.label),
                Snackbar.LENGTH_LONG
            ).show()
            if (finishAfter) finish()
        }
    }

    private fun askSaveOrDiscard() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.leave_tracking_title)
            .setMessage(R.string.leave_tracking_message)
            .setPositiveButton(R.string.save) { _, _ ->
                stopAndSave(finishAfter = true)
            }
            .setNegativeButton(R.string.discard) { _, _ ->
                viewModel.isTracking = false
                viewModel.recorder.reset()
                finish()
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun updateButton() {
        val tracking = viewModel.isTracking
        binding.buttonStartStop.setText(if (tracking) R.string.stop_and_save else R.string.start_tracking)
        backWhileTracking.isEnabled = tracking
        // Keep the screen on while tracking so the session is not interrupted.
        if (tracking) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun updateStats() {
        val r = viewModel.recorder
        val min = if (r.hasData) r.minLux else null
        val avg = if (r.hasData) r.averageLux else null
        val max = if (r.hasData) r.maxLux else null
        binding.textMin.text = getString(R.string.stat_min, SettingsRepository.format(min, unit))
        binding.textAvg.text = getString(R.string.stat_avg, SettingsRepository.format(avg, unit))
        binding.textMax.text = getString(R.string.stat_max, SettingsRepository.format(max, unit))
        binding.textElapsed.text = if (viewModel.isTracking) {
            val seconds = r.durationMs / 1000
            getString(R.string.elapsed, "%d:%02d".format(seconds / 60, seconds % 60), r.count)
        } else {
            getString(R.string.not_tracking)
        }
    }

    private fun badgeBackground(level: LightLevel): Int = when (level) {
        LightLevel.LOW -> R.drawable.bg_badge_low
        LightLevel.MEDIUM -> R.drawable.bg_badge_medium
        LightLevel.BRIGHT -> R.drawable.bg_badge_bright
    }

    companion object {
        private const val SAMPLE_EVERY_MS = 500L
        /** 10 samples x 0.5 s = at least 5 seconds of data before saving. */
        private const val MIN_SAMPLES = 10
    }
}
