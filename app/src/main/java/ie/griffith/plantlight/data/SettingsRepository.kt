package ie.griffith.plantlight.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "plant_light_settings")

enum class LightUnit(val storageKey: String, val suffix: String) {
    LUX("lux", "lux"),
    FOOT_CANDLE("fc", "fc");

    companion object {
        fun fromStorage(key: String): LightUnit =
            entries.firstOrNull { it.storageKey == key } ?: LUX
    }
}

/**
 * Settings stored in Jetpack DataStore. Milestone 1 keeps this simple:
 * the display unit (lux or foot-candles). Calibration is planned later.
 */
class SettingsRepository(private val context: Context) {

    val units: Flow<LightUnit> = context.dataStore.data.map { prefs ->
        LightUnit.fromStorage(prefs[KEY_UNITS] ?: LightUnit.LUX.storageKey)
    }

    suspend fun setUnits(unit: LightUnit) {
        context.dataStore.edit { it[KEY_UNITS] = unit.storageKey }
    }

    companion object {
        private val KEY_UNITS = stringPreferencesKey("units")

        /** 1 foot-candle is approximately 10.764 lux. */
        private const val LUX_PER_FOOT_CANDLE = 10.764f

        fun format(lux: Float?, unit: LightUnit): String {
            if (lux == null) return "—"
            val value = if (unit == LightUnit.FOOT_CANDLE) lux / LUX_PER_FOOT_CANDLE else lux
            val formatted = if (value >= 100f) {
                value.toInt().toString()
            } else {
                String.format("%.1f", value)
            }
            return "$formatted ${unit.suffix}"
        }
    }
}
