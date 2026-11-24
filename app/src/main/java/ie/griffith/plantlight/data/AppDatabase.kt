package ie.griffith.plantlight.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Spot::class, LightReading::class, Plant::class, TrackingSession::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spotDao(): SpotDao
    abstract fun readingDao(): LightReadingDao
    abstract fun plantDao(): PlantDao
    abstract fun sessionDao(): TrackingSessionDao

    companion object {
        /**
         * Milestone 2 migration (version 1 -> 2). Existing spots are kept:
         *  - light_readings gets a nullable sessionId column
         *  - a new tracking_sessions table stores each session's min/avg/max
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE light_readings ADD COLUMN sessionId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS tracking_sessions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "spotId INTEGER NOT NULL, " +
                        "startedAt INTEGER NOT NULL, " +
                        "durationMs INTEGER NOT NULL, " +
                        "minLux REAL NOT NULL, " +
                        "avgLux REAL NOT NULL, " +
                        "maxLux REAL NOT NULL, " +
                        "sampleCount INTEGER NOT NULL, " +
                        "lightLevel TEXT NOT NULL, " +
                        "FOREIGN KEY(spotId) REFERENCES spots(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_tracking_sessions_spotId ON tracking_sessions (spotId)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "plant_light.db"
                ).addMigrations(MIGRATION_1_2).build().also { db ->
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
