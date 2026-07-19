@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.yura.app.reader

import org.readium.r2.navigator.preferences.FontFamily

internal object ReaderFonts {
    val LXGW_WEN_KAI = FontFamily("LXGW WenKai Screen")
    const val LXGW_ASSET_PATH = "fonts/LXGWWenKaiScreen.ttf"
    const val SERVED_ASSETS_PATTERN = "fonts/.*"
}