package ie.griffith.plantlight

import android.app.Application
import ie.griffith.plantlight.data.AppDatabase
import ie.griffith.plantlight.data.PhotoStore
import ie.griffith.plantlight.data.SettingsRepository

/**
 * Application entry point. Holds the Room database and DataStore settings
 * so every Activity can reach the same local data.
 */
class PlantLightApp : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var photos: PhotoStore
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        settings = SettingsRepository(this)
        photos = PhotoStore(this)
    }
}
