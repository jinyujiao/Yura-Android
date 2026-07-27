package com.yura.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yura.app.reader.ReaderPreferencesStore
import org.readium.r2.navigator.preferences.Theme

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
    surfaceDim = Color(0xFFDADDD6),
    surfaceBright = Color(0xFFFAFAF6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F6F1),
    surfaceContainer = Color(0xFFEEF1EB),
    surfaceContainerHigh = Color(0xFFE8ECE5),
    surfaceContainerHighest = Color(0xFFE1E6DE),
    surfaceVariant = Color(0xFFE4E8DF),
    onSurfaceVariant = Color(0xFF42483F),
    outline = Color(0xFF73796F),
    outlineVariant = Color(0xFFC8CEC5),
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
    surfaceDim = Color(0xFF111411),
    surfaceBright = Color(0xFF373A35),
    surfaceContainerLowest = Color(0xFF0C0F0C),
    surfaceContainerLow = Color(0xFF171A16),
    surfaceContainer = Color(0xFF1D211C),
    surfaceContainerHigh = Color(0xFF272C26),
    surfaceContainerHighest = Color(0xFF323831),
    surfaceVariant = Color(0xFF3F473F),
    onSurfaceVariant = Color(0xFFC4CBC1),
    outline = Color(0xFF8E958C),
    outlineVariant = Color(0xFF414941),
)

private val SepiaColors = lightColorScheme(
    primary = Color(0xFF765A28),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3DFB3),
    onPrimaryContainer = Color(0xFF281A03),
    secondary = Color(0xFF6C5B3B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEDDBB),
    onSecondaryContainer = Color(0xFF241A09),
    background = Color(0xFFF4ECD8),
    onBackground = Color(0xFF282117),
    surface = Color(0xFFFBF3DF),
    onSurface = Color(0xFF282117),
    surfaceDim = Color(0xFFDDD2BB),
    surfaceBright = Color(0xFFFFF8E6),
    surfaceContainerLowest = Color(0xFFFFFAEC),
    surfaceContainerLow = Color(0xFFF8F0DC),
    surfaceContainer = Color(0xFFF1E8D3),
    surfaceContainerHigh = Color(0xFFEAE0C9),
    surfaceContainerHighest = Color(0xFFE2D6BD),
    surfaceVariant = Color(0xFFE9DEC7),
    onSurfaceVariant = Color(0xFF50483A),
    outline = Color(0xFF7D7465),
    outlineVariant = Color(0xFFCFC3AB),
)

private val YuraTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val YuraShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun YuraTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val selection by remember(context) {
        ReaderPreferencesStore.themeSelection(context)
    }.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val resolvedTheme = if (selection.autoTheme) {
        if (systemDark) Theme.DARK else Theme.LIGHT
    } else {
        selection.theme
    }
    val colors: ColorScheme = when (resolvedTheme) {
        Theme.DARK -> DarkColors
        Theme.SEPIA -> SepiaColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = YuraTypography,
        shapes = YuraShapes,
        content = content,
    )
}
