package ie.griffith.plantlight.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import ie.griffith.plantlight.data.namesFor
import ie.griffith.plantlight.data.SettingsRepository
import ie.griffith.plantlight.data.Spot
import ie.griffith.plantlight.data.TrackingSession
import ie.griffith.plantlight.ui.theme.CardStroke
import ie.griffith.plantlight.ui.theme.Forest
import ie.griffith.plantlight.ui.theme.ForestDark
import ie.griffith.plantlight.ui.theme.Mint
import ie.griffith.plantlight.ui.theme.PlantLightTheme
import ie.griffith.plantlight.ui.theme.Surface
import ie.griffith.plantlight.ui.theme.TextMain
import ie.griffith.plantlight.ui.theme.TextMuted
import ie.griffith.plantlight.ui.theme.levelColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live light tracking for one saved spot.
 *
 * The layout is Jetpack Compose (same structure as class:
 * ComponentActivity + setContent { PlantLightTheme { Screen } }).
 *
 * How the sensor data is used:
 *  1. The light sensor sends lux values to onSensorChanged (we keep the latest one).
 *  2. Every half second a "tick" takes that value. While tracking, it is added
 *     to the session recorder for min / average / max.
 *  3. The 3-second average decides the live light level, which is compared with
 *     what the spot's best-suited plant needs ("Good" / "Too dark" / "Too bright").
 *  4. Stop ends the session on screen without saving. The live graph is a
 *     placeholder. Back while tracking stays on this screen.
 */
class TrackingActivity : ComponentActivity(), SensorEventListener {

    private lateinit var app: PlantLightApp
    private val viewModel: TrackingViewModel by viewModels()
    private val uiState = mutableStateOf(TrackingUi())
    private val snackbarHostState = SnackbarHostState()

    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var spotId: Long = -1L
    private var spot: Spot? = null
    private var targetPlant: Plant? = null
    private var unit: LightUnit = LightUnit.LUX

    private val averager = LuxAverager()
    private var latestLux: Float? = null

    // While tracking, back stays on this screen so the session is not lost.
    private val backWhileTracking = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            // The save or discard dialog is not shown yet.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the toolbar below the status bar (Milestone 1 feedback).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        app = application as PlantLightApp

        // The spot ID comes from Spot Details through an explicit Intent.
        spotId = intent.getLongExtra(PlantLightIntents.EXTRA_SPOT_ID, -1L)
        if (spotId <= 0L) {
            Toast.makeText(this, R.string.error_missing_spot, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, backWhileTracking)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor == null) {
            // No sensor: explain and disable tracking instead of crashing.
            uiState.value = uiState.value.copy(
                noSensor = true,
                startEnabled = false,
                liveLux = "—",
                showLevel = false
            )
        } else {
            uiState.value = uiState.value.copy(
                liveLux = getString(R.string.waiting_for_sensor)
            )
        }

        updateButton()
        updateStats()

        setContent {
            val ui by uiState
            PlantLightTheme {
                TrackingScreen(
                    ui = ui,
                    snackbarHostState = snackbarHostState,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onStartStop = {
                        if (viewModel.isTracking) {
                            // Stop ends the session on screen. It is not saved yet.
                            viewModel.isTracking = false
                            updateButton()
                            updateStats()
                        } else {
                            startTracking()
                        }
                    }
                )
            }
        }

        lifecycleScope.launch {
            // Load the spot and the plant it is best suited for.
            val loaded = app.database.spotDao().getById(spotId)
            if (loaded == null) {
                finish()
                return@launch
            }
            spot = loaded
            uiState.value = uiState.value.copy(
                title = getString(R.string.track_title, loaded.name)
            )
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
        // Keep the points so the live graph can use them later.
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
        val status = targetPlant?.let { plant ->
            val need = LightLevel.fromStorage(plant.lightNeed)
            when (PlantAdvice.fit(level, need)) {
                PlantAdvice.Fit.GOOD -> getString(R.string.status_good, plant.name)
                PlantAdvice.Fit.TOO_DARK ->
                    getString(R.string.status_too_dark, plant.name, need.label.lowercase())
                PlantAdvice.Fit.TOO_BRIGHT ->
                    getString(R.string.status_too_bright, plant.name, need.label.lowercase())
            }
        } ?: uiState.value.status
        uiState.value = uiState.value.copy(
            liveLux = SettingsRepository.format(averageLux, unit),
            showLevel = true,
            liveLevel = getString(R.string.level_light, level.label),
            level = level,
            status = status,
            livePlants = getString(
                R.string.live_suits,
                level.label.lowercase(),
                PlantCatalog.namesFor(level).take(3).joinToString(", ")
            )
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
        // Not called from the Stop button yet.
        val recorder = viewModel.recorder
        val currentSpot = spot ?: return
        if (recorder.count < MIN_SAMPLES) {
            lifecycleScope.launch {
                snackbarHostState.showSnackbar(getString(R.string.track_too_short))
            }
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
            snackbarHostState.showSnackbar(
                getString(R.string.track_saved, SettingsRepository.format(average, unit), level.label)
            )
            if (finishAfter) finish()
        }
    }

    private fun askSaveOrDiscard() {
        // Not called from the back press yet.
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
        uiState.value = uiState.value.copy(tracking = tracking)
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
        uiState.value = uiState.value.copy(
            minText = getString(R.string.stat_min, SettingsRepository.format(min, unit)),
            avgText = getString(R.string.stat_avg, SettingsRepository.format(avg, unit)),
            maxText = getString(R.string.stat_max, SettingsRepository.format(max, unit)),
            elapsed = if (viewModel.isTracking) {
                val seconds = r.durationMs / 1000
                getString(R.string.elapsed, "%d:%02d".format(seconds / 60, seconds % 60), r.count)
            } else {
                getString(R.string.not_tracking)
            }
        )
    }

    companion object {
        private const val SAMPLE_EVERY_MS = 500L
        /** 10 samples x 0.5 s = at least 5 seconds of data before saving. */
        private const val MIN_SAMPLES = 10
    }
}

/** Values shown on the Track light layout. */
data class TrackingUi(
    val title: String = "Track light",
    val noSensor: Boolean = false,
    val liveLux: String = "",
    val liveLevel: String = "",
    val showLevel: Boolean = false,
    val level: LightLevel? = null,
    val status: String = "",
    val livePlants: String = "",
    val minText: String = "",
    val avgText: String = "",
    val maxText: String = "",
    val elapsed: String = "",
    val tracking: Boolean = false,
    val startEnabled: Boolean = true
)

@Composable
fun TrackingScreen(
    ui: TrackingUi,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartStop: () -> Unit
) {
    PlantLightScaffold(
        title = ui.title,
        onBack = onBack,
        snackbarHostState = snackbarHostState
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            if (ui.noSensor) {
                Text(
                    text = stringResource(R.string.no_sensor_tracking),
                    color = ForestDark,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Mint, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(0.dp),
                border = BorderStroke(1.dp, CardStroke)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.live_now),
                        color = TextMuted,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ui.liveLux,
                            color = TextMain,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (ui.showLevel) {
                            val badgeColor = ui.level?.let { levelColor(it) } ?: Forest
                            Text(
                                text = ui.liveLevel,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(badgeColor)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (ui.status.isNotEmpty()) {
                        Text(
                            text = ui.status,
                            color = ForestDark,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.live_graph_placeholder),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = ui.minText,
                    color = TextMain,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = ui.avgText,
                    color = TextMain,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = ui.maxText,
                    color = TextMain,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = ui.elapsed,
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            if (ui.livePlants.isNotEmpty()) {
                Text(
                    text = ui.livePlants,
                    color = TextMain,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }

            Button(
                onClick = onStartStop,
                enabled = ui.startEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Forest,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = stringResource(
                        if (ui.tracking) R.string.stop_and_save else R.string.start_tracking
                    )
                )
            }

            Text(
                text = stringResource(R.string.track_hint),
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TrackingScreenPreview() {
    PlantLightTheme {
        TrackingScreen(
            ui = TrackingUi(
                title = "Track light: Kitchen",
                liveLux = "640 lux",
                liveLevel = "Medium light",
                showLevel = true,
                level = LightLevel.MEDIUM,
                status = "Good for Spider Plant",
                livePlants = "Right now this spot suits medium plants: Spider Plant, Monstera",
                minText = "Min\n410 lux",
                avgText = "Average\n640 lux",
                maxText = "Max\n900 lux",
                elapsed = "Not tracking yet. The graph shows the live light."
            ),
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onStartStop = {}
        )
    }
}
