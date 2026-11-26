package ie.griffith.plantlight.ui.theme

import androidx.compose.ui.graphics.Color
import ie.griffith.plantlight.data.LightLevel

/**
 * Colours copied from res/values/colors.xml so Compose screens
 * and the remaining XML screens look the same.
 */
val Forest = Color(0xFF2D6A4F)
val ForestDark = Color(0xFF1B4332)
val ForestMid = Color(0xFF40916C)
val Mint = Color(0xFFD8F3DC)
val Cream = Color(0xFFF7F4EF)
val Surface = Color(0xFFFFFCF7)
val Terracotta = Color(0xFFC17F59)
val Gold = Color(0xFFC9A227)
val Sage = Color(0xFF6A8F71)
val TextMain = Color(0xFF1F2A24)
val TextMuted = Color(0xFF5C6B63)
val Danger = Color(0xFF9B3A2F)
val LevelLow = Color(0xFF6A8F71)
val LevelMedium = Color(0xFFC9A227)
val LevelBright = Color(0xFFC17F59)
val CardStroke = Color(0xFFE6E0D6)
val GaugeTrack = Color(0xFFE4E0D8)
val SecondaryContainerLight = Color(0xFFF3E0D4)
val OnSecondaryContainerLight = Color(0xFF5A2E16)

fun levelColor(level: LightLevel): Color = when (level) {
    LightLevel.LOW -> LevelLow
    LightLevel.MEDIUM -> LevelMedium
    LightLevel.BRIGHT -> LevelBright
}
