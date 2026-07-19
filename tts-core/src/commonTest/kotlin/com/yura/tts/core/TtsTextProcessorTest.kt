package com.yura.tts.core

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class TtsTextProcessorTest {
    private val processor = TtsTextProcessor()

    @Test
    fun cleansDecorativeSymbolsAndInvisibleCharacters() {
        val cleaned = processor.clean("  \u200B你好…… ……世界！！  ")

        assertEquals("你好 世界！", cleaned)
    }

    @Test
    fun preservesDialogueQuotesWhileCleaningPunctuationNoise() {
        val cleaned = processor.clean("“你好……”他说：“等等？！ ”")

        assertEquals("“你好”他说：“等等！”", cleaned)
    }

    @Test
    fun removesPunctuationOnlyFragmentsButKeepsSpokenText() {
        val cleaned = processor.clean("。” …… ，！？ “")

        assertEquals("", cleaned)
    }

    @Test
    fun splitsSentencesWithoutCreatingPunctuationOnlyRequests() {
        val sentences = processor.splitSentences("他说：“等等？！”然后继续。")

        assertEquals(listOf("他说：“等等！”然后继续。"), sentences)
        assertTrue(sentences.all { it.any(Char::isLetterOrDigit) })
    }    @Test
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
