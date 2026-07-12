package com.yura.app.reader.tts

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsTextProcessorTest {
    private val processor = TtsTextProcessor(Locale.US)

    @Test
    fun cleansDecorativeSymbolsAndInvisibleCharacters() {
        val cleaned = processor.clean("  \u200B你好…… ……世界！！  ")

        assertEquals("你好 世界！", cleaned)
    }

    @Test
    fun splitsChineseAndEnglishSentences() {
        val sentences = processor.splitSentences("第一句。第二句！Third sentence. Final question?")

        assertEquals(listOf("第一句。", "第二句！", "Third sentence.", "Final question?"), sentences)
    }

    @Test
    fun splitsLongTextIntoBoundedChunks() {
        val text = buildString {
            repeat(12) { append("这是用于测试超长朗读文本分块的内容，") }
        }

        val chunks = processor.splitSentences(text)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 160 })
        assertEquals(text, chunks.joinToString(separator = ""))
    }
}
