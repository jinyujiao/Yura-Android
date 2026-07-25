package com.yura.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudTtsClientTest {
    @Test
    fun microsoftSsmlUsesVoiceLocaleAndEscapesText() {
        val ssml = createMicrosoftSsml("他说：\"你好\" & <测试>", "en-US-JennyNeural")

        assertTrue(ssml.contains("<speak version=\"1.0\" xml:lang=\"en-US\">"))
        assertTrue(ssml.contains("<voice xml:lang=\"en-US\" name=\"en-US-JennyNeural\">"))
        assertTrue(ssml.contains("他说：&quot;你好&quot; &amp; &lt;测试&gt;"))
    }

    @Test
    fun microsoftVoiceLocaleFallsBackForUnknownVoiceNames() {
        assertEquals("zh-CN", microsoftVoiceLocale("自定义音色"))
        assertEquals("zh-CN", microsoftVoiceLocale("zh-CN-XiaoxiaoNeural"))
        assertEquals("en-US", microsoftVoiceLocale("en-US-JennyNeural"))
    }
}
