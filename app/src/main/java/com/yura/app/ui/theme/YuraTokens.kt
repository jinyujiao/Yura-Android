package com.yura.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object YuraSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object YuraElevation {
    val flat = 0.dp
    val low = 1.dp
    val medium = 3.dp
    val floating = 8.dp
}

object YuraMotion {
    const val quick = 160
    const val standard = 200
    const val deliberate = 240
}

val YuraBottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

val ColorScheme.yuraSurfaceSubtle: Color
    get() = surfaceContainerLow

val ColorScheme.yuraSurfaceCard: Color
    get() = surfaceContainer

val ColorScheme.yuraSurfaceRaised: Color
    get() = surfaceContainerHigh

val ColorScheme.yuraSelectedSurface: Color
    get() = primaryContainer.copy(alpha = 0.68f)

val ColorScheme.yuraHighlightSurface: Color
    get() = secondaryContainer.copy(alpha = 0.62f)

val ColorScheme.yuraCorrectionSurface: Color
    get() = tertiaryContainer.copy(alpha = 0.52f)
