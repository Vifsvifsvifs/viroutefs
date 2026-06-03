package dev.vifs.viroutefs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.vifs.viroutefs.settings.AppThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF246BFD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001A42),
    secondary = Color(0xFF526070),
    onSecondary = Color.White,
    surface = Color(0xFFFCFDF8),
    onSurface = Color(0xFF1A1C1E),
    background = Color(0xFFFCFDF8),
    onBackground = Color(0xFF1A1C1E),
    surfaceContainerHighest = Color(0xFFECEEF4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFBAC8DB),
    onSecondary = Color(0xFF243140),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    background = Color(0xFF0F1116),
    onBackground = Color(0xFFE2E2E9),
    surfaceContainerHighest = Color(0xFF252832),
)

private val AmoledColors = darkColorScheme(
    primary = Color(0xFF9DBBFF),
    onPrimary = Color(0xFF002D68),
    primaryContainer = Color(0xFF0B3A7A),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFB5C7E4),
    onSecondary = Color(0xFF203044),
    surface = Color.Black,
    onSurface = Color(0xFFE8E8EE),
    background = Color.Black,
    onBackground = Color(0xFFE8E8EE),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = Color(0xFF080808),
    surfaceContainerHigh = Color(0xFF101010),
    surfaceContainerHighest = Color(0xFF171717),
)

@Composable
fun ViRouteFsTheme(
    themeMode: AppThemeMode = AppThemeMode.System,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val colors = when (themeMode) {
        AppThemeMode.System -> if (systemDark) DarkColors else LightColors
        AppThemeMode.Light -> LightColors
        AppThemeMode.Dark -> DarkColors
        AppThemeMode.AmoledBlack -> AmoledColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}
