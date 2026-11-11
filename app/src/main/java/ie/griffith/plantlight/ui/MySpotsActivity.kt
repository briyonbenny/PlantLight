package ie.griffith.plantlight.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import ie.griffith.plantlight.PlantLightApp
import ie.griffith.plantlight.PlantLightIntents
import ie.griffith.plantlight.R
import ie.griffith.plantlight.data.LightUnit
import ie.griffith.plantlight.data.Spot
import ie.griffith.plantlight.databinding.ActivityMySpotsBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Second screen: a list of saved spots. Tapping a row opens Spot Details
 * with an explicit Intent that carries the spot ID.
 */
class MySpotsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMySpotsBinding
    private lateinit var app: PlantLightApp
    private lateinit var adapter: SpotAdapter
    private var unit: LightUnit = LightUnit.LUX

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMySpotsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = application as PlantLightApp

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = SpotAdapter(
            unitProvider = { unit },
            onOpen = { spot ->
                startActivity(PlantLightIntents.spotDetails(this, spot.id))
            },
            onDelete = { spot -> confirmDelete(spot) }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        lifecycleScope.launch {
            combine(app.database.spotDao().observeAll(), app.settings.units) { spots, newUnit ->
                unit = newUnit
                spots
            }.collect { spots ->
                adapter.submitList(spots)
                val empty = spots.isEmpty()
                binding.empty.visibility = if (empty) View.VISIBLE else View.GONE
                binding.list.visibility = if (empty) View.GONE else View.VISIBLE
            }
        }
    }

    private fun confirmDelete(spot: Spot) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_spot_title)
            .setMessage(getString(R.string.delete_spot_message, spot.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    app.photos.delete(spot.photoPath)
                    app.database.spotDao().delete(spot)
                    Snackbar.make(binding.root, R.string.spot_deleted, Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
