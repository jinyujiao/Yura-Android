package com.yura.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5D50),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBCE5D2),
    onPrimaryContainer = Color(0xFF0B2119),
    secondary = Color(0xFF775A1B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDEA0),
    onSecondaryContainer = Color(0xFF291800),
    tertiary = Color(0xFF904B3C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD2),
    onTertiaryContainer = Color(0xFF3A0A03),
    background = Color(0xFFFBF9F3),
    onBackground = Color(0xFF1C1C18),
    surface = Color(0xFFFBF9F3),
    onSurface = Color(0xFF1C1C18),
    surfaceVariant = Color(0xFFE0E4D8),
    onSurfaceVariant = Color(0xFF43483F),
    outline = Color(0xFF73786D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA0D0BA),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF164D40),
    onPrimaryContainer = Color(0xFFBCE5D2),
    secondary = Color(0xFFE8C37B),
    onSecondary = Color(0xFF412D00),
    secondaryContainer = Color(0xFF5C4205),
    onSecondaryContainer = Color(0xFFFFDEA0),
    tertiary = Color(0xFFFFB4A5),
    onTertiary = Color(0xFF561E13),
    tertiaryContainer = Color(0xFF733326),
    onTertiaryContainer = Color(0xFFFFDAD2),
    background = Color(0xFF141512),
    onBackground = Color(0xFFE5E3DC),
    surface = Color(0xFF141512),
    onSurface = Color(0xFFE5E3DC),
    surfaceVariant = Color(0xFF43483F),
    onSurfaceVariant = Color(0xFFC4C8BC),
    outline = Color(0xFF8D9287),
)

@Composable
fun YuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors: ColorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
