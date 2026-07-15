package com.yura.app.ui.settings

import com.yura.app.reader.tts.SimpleTtsController
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MicrosoftVoiceGroupingTest {
    @Test
    fun groupsVoicesByLanguageAndRegion() {
        val groups = groupMicrosoftVoices(
            voices = listOf(
                voice("en-US-GuyNeural", "Guy · en-US", "en-US"),
                voice("zh-CN-YunxiNeural", "云希 · zh-CN", "zh-CN"),
                voice("en-US-JennyNeural", "Jenny · en-US", "en-US"),
                voice("zh-TW-HsiaoChenNeural", "曉臻 · zh-TW", "zh-TW"),
            ),
            displayLocale = Locale.SIMPLIFIED_CHINESE,
        )

        assertEquals(listOf("zh-CN", "zh-TW", "en-US"), groups.map { it.locale })
        assertEquals(2, groups.last().voices.size)
        assertTrue(groups.first().label.contains("中文"))
        assertTrue(groups.last().label.contains("英语"))
    }

    @Test
    fun normalizesUnderscoreLocaleTags() {
        val groups = groupMicrosoftVoices(
            voices = listOf(voice("pt-BR-Test", "Teste", "pt_BR")),
            displayLocale = Locale.SIMPLIFIED_CHINESE,
        )

        assertEquals("pt-BR", groups.single().locale)
        assertTrue(groups.single().label.contains("葡萄牙语"))
    }

    private fun voice(shortName: String, displayName: String, locale: String) =
        SimpleTtsController.MicrosoftVoice(shortName, displayName, locale)
}