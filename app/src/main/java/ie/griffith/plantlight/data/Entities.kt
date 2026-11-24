package ie.griffith.plantlight.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "spots")
data class Spot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lightLevel: String,
    val averageLux: Float?,
    val isManual: Boolean,
    val createdAt: Long,
    val photoPath: String? = null
)

@Entity(
    tableName = "light_readings",
    foreignKeys = [
        ForeignKey(
            entity = Spot::class,
            parentColumns = ["id"],
            childColumns = ["spotId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("spotId")]
)
data class LightReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spotId: Long,
    val lux: Float,
    val timestamp: Long,
    // Milestone 2: which tracking session this sample belongs to (null for a quick Measure save).
    val sessionId: Long? = null
)

@Entity(tableName = "plants")
data class Plant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lightNeed: String,
    val notes: String
)

/**
 * Milestone 2: summary of one live tracking session for a spot.
 * The individual samples are stored in light_readings with the same sessionId.
 */
@Entity(
    tableName = "tracking_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Spot::class,
            parentColumns = ["id"],
            childColumns = ["spotId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("spotId")]
)
data class TrackingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spotId: Long,
    val startedAt: Long,
    val durationMs: Long,
    val minLux: Float,
    val avgLux: Float,
    val maxLux: Float,
    val sampleCount: Int,
    val lightLevel: String
)
