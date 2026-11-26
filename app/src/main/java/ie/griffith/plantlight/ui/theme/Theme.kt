package ie.griffith.plantlight.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The app theme, the same idea as AppTheme in Tutorial Six
 * (or FirstTheme / TutorialTheme from the Empty Activity template).
 *
 * The default palette matches the XML colours so Measure and Spot Details
 * still look the same while other screens move over to Compose.
 * Dynamic colour on Android 12+ is available but off by default, so the
 * plant-green branding stays consistent during the conversion.
 */
private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = ForestDark,
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    background = Cream,
    onBackground = TextMain,
    surface = Surface,
    onSurface = TextMain,
    onSurfaceVariant = TextMuted,
    error = Danger,
    onError = Color.White,
    outline = CardStroke
)

private val DarkColors = darkColorScheme(
    primary = ForestMid,
    onPrimary = Color.White,
    primaryContainer = ForestDark,
    onPrimaryContainer = Mint,
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = OnSecondaryContainerLight,
    onSecondaryContainer = SecondaryContainerLight,
    background = Color(0xFF0E1712),
    onBackground = Cream,
    surface = Color(0xFF16211B),
    onSurface = Cream,
    onSurfaceVariant = Color(0xFFA8B5AD),
    error = Color(0xFFD06457),
    onError = Color.White,
    outline = Color(0xFF2A3A32)
)

@Composable
fun PlantLightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
