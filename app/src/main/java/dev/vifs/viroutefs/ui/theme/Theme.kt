package dev.vifs.viroutefs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.vifs.viroutefs.settings.AppThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF155EEF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4ECFF),
    onPrimaryContainer = Color(0xFF102A56),
    secondary = Color(0xFF4B5F7C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E9F4),
    onSecondaryContainer = Color(0xFF24344B),
    tertiary = Color(0xFF197647),
    onTertiary = Color.White,
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF181B20),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF181B20),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F2F7),
    surfaceContainerHigh = Color(0xFFE9ECF2),
    surfaceContainerHighest = Color(0xFFE2E6EE),
    outlineVariant = Color(0xFFD7DCE5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF98B9FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF183C75),
    onPrimaryContainer = Color(0xFFDDE7FF),
    secondary = Color(0xFFBCC8DC),
    onSecondary = Color(0xFF263245),
    secondaryContainer = Color(0xFF303C50),
    onSecondaryContainer = Color(0xFFDCE5F7),
    tertiary = Color(0xFF72D69E),
    onTertiary = Color(0xFF00391F),
    surface = Color(0xFF101318),
    onSurface = Color(0xFFE4E7ED),
    background = Color(0xFF0C0F14),
    onBackground = Color(0xFFE4E7ED),
    surfaceContainerLow = Color(0xFF15181E),
    surfaceContainer = Color(0xFF1A1E25),
    surfaceContainerHigh = Color(0xFF20252D),
    surfaceContainerHighest = Color(0xFF282E38),
    outlineVariant = Color(0xFF343B46),
)

private val AmoledAccent = Color(0xFF79A7FF)
private val AmoledAccentContainer = Color(0xFF0D2B5C)

private val AmoledColors = darkColorScheme(
    primary = AmoledAccent,
    onPrimary = Color(0xFF001A42),
    primaryContainer = AmoledAccentContainer,
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1A1F29),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF62D98F),
    onTertiary = Color(0xFF00391F),
    surface = Color.Black,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFFC7C7C7),
    background = Color.Black,
    onBackground = Color(0xFFF2F2F2),
    outline = Color(0xFF777777),
    outlineVariant = Color(0xFF2A2A2A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color(0xFF050505),
    surfaceContainerHigh = Color(0xFF0D0D0D),
    surfaceContainerHighest = Color(0xFF151515),
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
