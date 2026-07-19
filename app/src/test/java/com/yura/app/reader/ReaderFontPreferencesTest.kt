@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.yura.app.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.navigator.preferences.FontFamily

class ReaderFontPreferencesTest {
    private val serializer = EpubPreferencesSerializer()

    @Test
    fun serializesSystemSerifFont() {
        val restored = serializer.deserialize(serializer.serialize(EpubPreferences(fontFamily = FontFamily.SERIF)))

        assertEquals(FontFamily.SERIF, restored.fontFamily)
    }

    @Test
    fun serializesBundledWenKaiFont() {
        val restored = serializer.deserialize(serializer.serialize(EpubPreferences(fontFamily = ReaderFonts.LXGW_WEN_KAI)))

        assertEquals(ReaderFonts.LXGW_WEN_KAI, restored.fontFamily)
    }
}