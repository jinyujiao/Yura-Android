package com.yura.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2A6658),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3EBDD),
    onPrimaryContainer = Color(0xFF0E211C),
    secondary = Color(0xFF806323),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4DFA9),
    onSecondaryContainer = Color(0xFF241A05),
    tertiary = Color(0xFF925649),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9D1),
    onTertiaryContainer = Color(0xFF3A0A03),
    background = Color(0xFFFAFAF6),
    onBackground = Color(0xFF1B1D19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1D19),
    surfaceVariant = Color(0xFFE4E8DF),
    onSurfaceVariant = Color(0xFF42483F),
    outline = Color(0xFF73796F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7D9C3),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF1D5145),
    onPrimaryContainer = Color(0xFFD3EBDD),
    secondary = Color(0xFFE4C878),
    onSecondary = Color(0xFF412D00),
    secondaryContainer = Color(0xFF5C4205),
    onSecondaryContainer = Color(0xFFF4DFA9),
    tertiary = Color(0xFFFFB4A5),
    onTertiary = Color(0xFF561E13),
    tertiaryContainer = Color(0xFF733326),
    onTertiaryContainer = Color(0xFFFFDAD2),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE7E6DF),
    surface = Color(0xFF191C18),
    onSurface = Color(0xFFE7E6DF),
    surfaceVariant = Color(0xFF3F473F),
    onSurfaceVariant = Color(0xFFC4CBC1),
    outline = Color(0xFF8E958C),
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
