package ie.griffith.plantlight.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ie.griffith.plantlight.PlantLightApp
import ie.griffith.plantlight.PlantLightIntents
import ie.griffith.plantlight.R
import ie.griffith.plantlight.data.LightLevel
import ie.griffith.plantlight.data.LightUnit
import ie.griffith.plantlight.data.Plant
import ie.griffith.plantlight.data.SettingsRepository
import ie.griffith.plantlight.data.Spot
import ie.griffith.plantlight.databinding.ActivitySpotDetailsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Third screen: one saved spot, its suggested plants, a photo taken
 * with an implicit camera intent, and a share button (implicit ACTION_SEND).
 */
class SpotDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpotDetailsBinding
    private lateinit var app: PlantLightApp
    private var spotId: Long = -1L
    private var currentSpot: Spot? = null
    private var currentPlants: List<Plant> = emptyList()
    private var unit: LightUnit = LightUnit.LUX
    private var pendingPhoto: File? = null

    // Activity Result wrapper around the implicit ACTION_IMAGE_CAPTURE intent.
    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingPhoto
        pendingPhoto = null
        val spot = currentSpot
        if (!success || file == null || spot == null) {
            file?.delete()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            app.photos.delete(spot.photoPath)
            app.database.spotDao().update(spot.copy(photoPath = file.absolutePath))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpotDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = application as PlantLightApp

        // The spot to show is supplied by the explicit Intent from Measure or My Spots.
        spotId = intent.getLongExtra(PlantLightIntents.EXTRA_SPOT_ID, -1L)
        if (spotId <= 0L) {
            Toast.makeText(this, R.string.error_missing_spot, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.buttonPhoto.setOnClickListener { launchCamera() }
        binding.buttonShare.setOnClickListener { shareSpot() }
        binding.buttonDelete.setOnClickListener { confirmDelete() }

        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        lifecycleScope.launch {
            combine(
                app.database.spotDao().observeById(spotId),
                app.settings.units
            ) { spot, newUnit ->
                spot to newUnit
            }.collectLatest { (spot, newUnit) ->
                if (spot == null) {
                    finish()
                    return@collectLatest
                }
                currentSpot = spot
                unit = newUnit
                bindSpot(spot, dateFormat)
                val plants = app.database.plantDao().getByLightNeed(spot.lightLevel)
                currentPlants = plants
                bindPlants(plants)
            }
        }
    }

    private fun bindSpot(spot: Spot, dateFormat: DateFormat) {
        val level = LightLevel.fromStorage(spot.lightLevel)
        binding.toolbar.title = spot.name
        binding.textLevel.text = getString(R.string.level_light, level.label)
        binding.textLevel.setBackgroundResource(badgeBackground(level))
        binding.textReading.text = if (spot.isManual) {
            getString(R.string.reading_manual)
        } else {
            getString(R.string.reading_value, SettingsRepository.format(spot.averageLux, unit))
        }
        binding.textDate.text = getString(
            R.string.saved_on,
            dateFormat.format(Date(spot.createdAt))
        )

        if (!spot.photoPath.isNullOrBlank() && File(spot.photoPath).exists()) {
            binding.imagePhoto.setImageURI(Uri.fromFile(File(spot.photoPath)))
            binding.imagePhoto.visibility = View.VISIBLE
            binding.photoPlaceholder.visibility = View.GONE
            binding.buttonPhoto.setText(R.string.replace_photo)
        } else {
            binding.imagePhoto.setImageDrawable(null)
            binding.imagePhoto.visibility = View.GONE
            binding.photoPlaceholder.visibility = View.VISIBLE
            binding.buttonPhoto.setText(R.string.take_photo)
        }
    }

    private fun bindPlants(plants: List<Plant>) {
        val text = if (plants.isEmpty()) {
            getString(R.string.no_plants)
        } else {
            plants.joinToString("\n\n") { plant ->
                getString(R.string.plant_line, plant.name, plant.notes)
            }
        }
        binding.textPlants.text = text
    }

    private fun launchCamera() {
        // Implicit intent: the system camera app captures the photo into
        // a file in this app's private storage, via FileProvider.
        val file = app.photos.newPhotoFile()
        pendingPhoto = file
        val uri = app.photos.uriFor(file)
        takePicture.launch(uri)
    }

    private fun shareSpot() {
        val spot = currentSpot ?: return
        val photoUri = spot.photoPath
            ?.takeIf { File(it).exists() }
            ?.let { app.photos.uriForPath(it) }
        startActivity(PlantLightIntents.shareSpot(spot, currentPlants, photoUri, unit))
    }

    private fun confirmDelete() {
        val spot = currentSpot ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_spot_title)
            .setMessage(getString(R.string.delete_spot_message, spot.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    app.photos.delete(spot.photoPath)
                    app.database.spotDao().delete(spot)
                    finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun badgeBackground(level: LightLevel): Int = when (level) {
        LightLevel.LOW -> R.drawable.bg_badge_low
        LightLevel.MEDIUM -> R.drawable.bg_badge_medium
        LightLevel.BRIGHT -> R.drawable.bg_badge_bright
    }
}
