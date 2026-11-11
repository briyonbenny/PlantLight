package ie.griffith.plantlight.data

/**
 * The three light classes used throughout the app, matching the proposal:
 * a spot is Low, Medium or Bright.
 */
enum class LightLevel(val storageKey: String, val label: String) {
    LOW("LOW", "Low"),
    MEDIUM("MEDIUM", "Medium"),
    BRIGHT("BRIGHT", "Bright");

    companion object {
        fun fromStorage(key: String): LightLevel =
            entries.firstOrNull { it.storageKey == key } ?: MEDIUM
    }
}
