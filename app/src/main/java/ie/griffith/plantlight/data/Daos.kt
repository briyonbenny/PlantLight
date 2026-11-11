package ie.griffith.plantlight.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SpotDao {
    @Query("SELECT * FROM spots ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Spot>>

    @Query("SELECT * FROM spots WHERE id = :id")
    fun observeById(id: Long): Flow<Spot?>

    @Query("SELECT * FROM spots WHERE id = :id")
    suspend fun getById(id: Long): Spot?

    @Insert
    suspend fun insert(spot: Spot): Long

    @Update
    suspend fun update(spot: Spot)

    @Delete
    suspend fun delete(spot: Spot)

    @Query("DELETE FROM spots")
    suspend fun deleteAll()
}

@Dao
interface LightReadingDao {
    @Query("SELECT * FROM light_readings WHERE spotId = :spotId ORDER BY timestamp DESC")
    fun observeForSpot(spotId: Long): Flow<List<LightReading>>

    @Insert
    suspend fun insert(reading: LightReading): Long

    @Query("DELETE FROM light_readings")
    suspend fun deleteAll()
}

@Dao
interface PlantDao {
    @Query("SELECT * FROM plants WHERE lightNeed = :level ORDER BY name")
    fun observeByLightNeed(level: String): Flow<List<Plant>>

    @Query("SELECT * FROM plants WHERE lightNeed = :level ORDER BY name")
    suspend fun getByLightNeed(level: String): List<Plant>

    @Query("SELECT COUNT(*) FROM plants")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plants: List<Plant>)
}
