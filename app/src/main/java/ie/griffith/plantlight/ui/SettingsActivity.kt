package ie.griffith.plantlight.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import ie.griffith.plantlight.PlantLightApp
import ie.griffith.plantlight.R
import ie.griffith.plantlight.data.LightUnit
import ie.griffith.plantlight.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Optional settings: display units and a button to clear all saved data.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var app: PlantLightApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = application as PlantLightApp

        binding.toolbar.setNavigationOnClickListener { finish() }

        lifecycleScope.launch {
            when (app.settings.units.first()) {
                LightUnit.LUX -> binding.radioLux.isChecked = true
                LightUnit.FOOT_CANDLE -> binding.radioFc.isChecked = true
            }
        }

        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val unit = if (checkedId == R.id.radio_fc) LightUnit.FOOT_CANDLE else LightUnit.LUX
            lifecycleScope.launch { app.settings.setUnits(unit) }
        }

        binding.buttonClear.setOnClickListener { confirmClear() }
    }
    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_data_title)
            .setMessage(R.string.clear_data_message)
            .setPositiveButton(R.string.clear) { _, _ ->
                lifecycleScope.launch {
                    File(filesDir, "photos").listFiles()?.forEach { it.delete() }
                    app.database.spotDao().deleteAll()
                    app.database.readingDao().deleteAll()
                    Snackbar.make(binding.root, R.string.data_cleared, Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
