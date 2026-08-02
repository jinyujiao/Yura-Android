@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.yura.app.reader

import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread

internal object ReaderWindowPolicy {
    private const val EXPANDED_WIDTH_DP = 600

    fun isExpandedLandscape(windowWidthDp: Int, windowHeightDp: Int): Boolean =
        windowWidthDp >= EXPANDED_WIDTH_DP && windowWidthDp > windowHeightDp

    fun adaptPreferences(
        preferences: EpubPreferences,
        windowWidthDp: Int,
        windowHeightDp: Int,
    ): EpubPreferences {
        if (!isExpandedLandscape(windowWidthDp, windowHeightDp)) return preferences
        if (preferences.scroll == true) return preferences
        if (preferences.columnCount != null && preferences.columnCount != ColumnCount.AUTO) return preferences

        return preferences.copy(
            columnCount = ColumnCount.TWO,
            spread = Spread.ALWAYS,
        )
    }
}
