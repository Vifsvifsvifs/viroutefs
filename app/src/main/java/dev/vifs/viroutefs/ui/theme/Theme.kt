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

private val AmoledAccentRed = Color(0xFFFF3B30)
private val AmoledAccentRedContainer = Color(0xFF5F0000)

private val AmoledColors = darkColorScheme(
    primary = AmoledAccentRed,
    onPrimary = Color.White,
    primaryContainer = AmoledAccentRedContainer,
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color.Black,
    secondaryContainer = AmoledAccentRedContainer,
    onSecondaryContainer = Color.White,
    tertiary = AmoledAccentRed,
    onTertiary = Color.White,
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
