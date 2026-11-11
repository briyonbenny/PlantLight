package ie.griffith.plantlight.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Spot::class, LightReading::class, Plant::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spotDao(): SpotDao
    abstract fun readingDao(): LightReadingDao
    abstract fun plantDao(): PlantDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "plant_light.db"
                ).build().also { db ->
                    instance = db
                    seedPlantsIfNeeded(db)
                }
            }
        }

        private fun seedPlantsIfNeeded(db: AppDatabase) {
            CoroutineScope(Dispatchers.IO).launch {
                if (db.plantDao().count() == 0) {
                    db.plantDao().insertAll(PlantCatalog.ALL)
                }
            }
        }
    }
}
