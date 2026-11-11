package ie.griffith.plantlight

import android.content.Context
import android.content.Intent
import android.net.Uri
import ie.griffith.plantlight.data.LightLevel
import ie.griffith.plantlight.data.LightUnit
import ie.griffith.plantlight.data.Plant
import ie.griffith.plantlight.data.SettingsRepository
import ie.griffith.plantlight.data.Spot
import ie.griffith.plantlight.ui.SpotDetailsActivity

/**
 * Central place for the implicit and explicit Intents used by the app.
 * The assignment requires both kinds, and this keeps the calls easy to find.
 */
object PlantLightIntents {
    const val EXTRA_SPOT_ID = "ie.griffith.plantlight.EXTRA_SPOT_ID"

    /** Explicit intent: opens Spot Details and passes the saved spot's ID. */
    fun spotDetails(context: Context, spotId: Long): Intent {
        return Intent(context, SpotDetailsActivity::class.java)
            .putExtra(EXTRA_SPOT_ID, spotId)
    }

    /**
     * Implicit intent: shares a short report of the spot (and its photo
     * when one has been taken) with any app that can handle ACTION_SEND.
     */
    fun shareSpot(
        spot: Spot,
        plants: List<Plant>,
        photoUri: Uri?,
        unit: LightUnit
    ): Intent {
        val level = LightLevel.fromStorage(spot.lightLevel).label
        val reading = if (spot.isManual) {
            "chosen manually"
        } else {
            SettingsRepository.format(spot.averageLux, unit)
        }
        val plantNames = if (plants.isEmpty()) {
            "none listed yet"
        } else {
            plants.joinToString(", ") { it.name }
        }
        val text = buildString {
            appendLine("Plant Light report")
            appendLine("Spot: ${spot.name}")
            appendLine("Light level: $level")
            appendLine("Reading: $reading")
            appendLine("Suggested plants: $plantNames")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Plant Light: ${spot.name}")
            putExtra(Intent.EXTRA_TEXT, text)
            if (photoUri != null) {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, photoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        return Intent.createChooser(intent, "Share ${spot.name}")
    }
}
