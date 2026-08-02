@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.yura.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread

class ReaderWindowPolicyTest {
    @Test
    fun detectsExpandedLandscapeFromCurrentWindowBounds() {
        assertTrue(ReaderWindowPolicy.isExpandedLandscape(windowWidthDp = 1280, windowHeightDp = 800))
        assertFalse(ReaderWindowPolicy.isExpandedLandscape(windowWidthDp = 800, windowHeightDp = 1280))
        assertFalse(ReaderWindowPolicy.isExpandedLandscape(windowWidthDp = 599, windowHeightDp = 400))
    }

    @Test
    fun usesTwoColumnsForAutomaticPagedLayoutOnExpandedLandscape() {
        val adapted = ReaderWindowPolicy.adaptPreferences(
            preferences = EpubPreferences(
                columnCount = ColumnCount.AUTO,
                spread = Spread.NEVER,
                scroll = false,
            ),
            windowWidthDp = 1280,
            windowHeightDp = 800,
        )

        assertEquals(ColumnCount.TWO, adapted.columnCount)
        assertEquals(Spread.ALWAYS, adapted.spread)
    }

    @Test
    fun keepsPortraitAndExplicitUserLayoutsUnchanged() {
        val portrait = EpubPreferences(columnCount = ColumnCount.AUTO, spread = Spread.NEVER, scroll = false)
        assertSame(
            portrait,
            ReaderWindowPolicy.adaptPreferences(portrait, windowWidthDp = 800, windowHeightDp = 1280),
        )

        val singleColumn = EpubPreferences(columnCount = ColumnCount.ONE, spread = Spread.NEVER, scroll = false)
        assertSame(
            singleColumn,
            ReaderWindowPolicy.adaptPreferences(singleColumn, windowWidthDp = 1280, windowHeightDp = 800),
        )

        val scrolling = EpubPreferences(columnCount = ColumnCount.AUTO, spread = Spread.NEVER, scroll = true)
        assertSame(
            scrolling,
            ReaderWindowPolicy.adaptPreferences(scrolling, windowWidthDp = 1280, windowHeightDp = 800),
        )
    }
}
