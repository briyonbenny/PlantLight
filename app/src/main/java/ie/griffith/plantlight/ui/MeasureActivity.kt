package ie.griffith.plantlight.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import ie.griffith.plantlight.PlantLightApp
import ie.griffith.plantlight.PlantLightIntents
import ie.griffith.plantlight.R
import ie.griffith.plantlight.data.LightClassifier
import ie.griffith.plantlight.data.LightLevel
import ie.griffith.plantlight.data.LightReading
import ie.griffith.plantlight.data.LightUnit
import ie.griffith.plantlight.data.LuxAverager
import ie.griffith.plantlight.data.Spot
import ie.griffith.plantlight.databinding.ActivityMeasureBinding
import kotlinx.coroutines.launch

/**
 * First screen: live light measurement (or a manual fallback),
 * a name field and a Save button.
 */
class MeasureActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMeasureBinding
    private lateinit var app: PlantLightApp
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private val averager = LuxAverager()
    private var latestAverage: Float? = null
    private var latestLevel: LightLevel = LightLevel.MEDIUM
    private var unit: LightUnit = LightUnit.LUX
    private var hasSensor: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMeasureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = application as PlantLightApp

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_my_spots -> {
                    startActivity(android.content.Intent(this, MySpotsActivity::class.java))
                    true
                }
                R.id.action_settings -> {
                    startActivity(android.content.Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        binding.buttonMySpots.setOnClickListener {
            startActivity(android.content.Intent(this, MySpotsActivity::class.java))
        }
        binding.buttonSave.setOnClickListener { saveSpot() }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        hasSensor = lightSensor != null

        if (hasSensor) {
            binding.manualGroup.visibility = View.GONE
        } else {
            // Proposal fallback: if the device has no ambient light sensor,
            // the user picks Low, Medium or Bright by hand.
            binding.manualGroup.visibility = View.VISIBLE
            binding.gauge.showUnavailable()
            binding.chipMedium.isChecked = true
            latestLevel = LightLevel.MEDIUM
        }

        lifecycleScope.launch {
            app.settings.units.collect { newUnit ->
                unit = newUnit
                latestAverage?.let { lux ->
                    binding.gauge.setReading(lux, latestLevel, hasSensor, unit)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasSensor) {
            averager.reset()
            latestAverage = null
            sensorManager?.registerListener(
                this,
                lightSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    override fun onPause() {
        super.onPause()
        if (hasSensor) {
            sensorManager?.unregisterListener(this)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_LIGHT) return
        val lux = event.values[0]
        val average = averager.add(lux)
        latestAverage = average
        latestLevel = LightClassifier.classify(average)
        binding.gauge.setReading(average, latestLevel, true, unit)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used; the running average already smooths noisy readings.
    }

    private fun saveSpot() {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            binding.inputNameLayout.error = getString(R.string.error_name_required)
            binding.inputName.requestFocus()
            return
        }
        binding.inputNameLayout.error = null

        val manual = !hasSensor
        val level = if (manual) selectedManualLevel() else latestLevel
        val lux = if (manual) null else latestAverage
        if (!manual && lux == null) {
            Snackbar.make(binding.root, R.string.error_wait_for_reading, Snackbar.LENGTH_SHORT)
                .show()
            return
        }

        hideKeyboard()
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val id = app.database.spotDao().insert(
                Spot(
                    name = name,
                    lightLevel = level.storageKey,
                    averageLux = lux,
                    isManual = manual,
                    createdAt = now
                )
            )
            if (!manual && lux != null) {
                app.database.readingDao().insert(
                    LightReading(spotId = id, lux = lux, timestamp = now)
                )
            }
            binding.inputName.setText("")
            Snackbar.make(binding.root, getString(R.string.spot_saved, name), Snackbar.LENGTH_LONG)
                .setAction(R.string.view) {
                    // Explicit intent: the new spot's ID is passed to Spot Details.
                    startActivity(PlantLightIntents.spotDetails(this@MeasureActivity, id))
                }
                .show()
        }
    }

    private fun selectedManualLevel(): LightLevel {
        val chipId = binding.chipGroup.checkedChipId
        val chip = binding.chipGroup.findViewById<Chip>(chipId)
        return when (chip?.tag) {
            LightLevel.LOW.storageKey -> LightLevel.LOW
            LightLevel.BRIGHT.storageKey -> LightLevel.BRIGHT
            else -> LightLevel.MEDIUM
        }
    }
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.inputName.windowToken, 0)
    }
}
