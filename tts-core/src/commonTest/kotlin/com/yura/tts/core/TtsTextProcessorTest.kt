package com.yura.tts.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    }

    @Test
    fun splitsChineseAndEnglishSentences() {
        val sentences = processor.splitSentences("第一句。第二句！Third sentence. Final question?")

        assertEquals(listOf("第一句。", "第二句！", "Third sentence.", "Final question?"), sentences)
    }

    @Test
    fun closesDialogueBeforeStartingTheNextSentence() {
        val sentences = processor.splitSentences("他说：“走吧。”我们都点头。")

        assertEquals(listOf("他说：“走吧。”", "我们都点头。"), sentences)
    }

    @Test
    fun handlesStraightQuotesAndEnglishApostrophes() {
        assertEquals(
            listOf("He said\u0022Go.\u0022", "It's fine.", "Don't worry."),
            processor.splitSentences("He said \u0022Go.\u0022 It's fine. Don't worry."),
        )
    }

    @Test
    fun keepsDecimalsDomainsAndAbbreviationsTogether() {
        val sentences = processor.splitSentences("Pi is 3.14. Visit example.com. Mr. Smith arrived. Dr. Lee waved.")

        assertEquals(
            listOf("Pi is 3.14.", "Visit example.com.", "Mr. Smith arrived.", "Dr. Lee waved."),
            sentences,
        )
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

    @Test
    fun doesNotSplitSurrogatePairsAtHardBoundaries() {
        val text = "a".repeat(159) + "🎉" + "b".repeat(80)

        val chunks = processor.splitSentences(text)

        assertEquals(text, chunks.joinToString(separator = ""))
        assertTrue(chunks.any { "🎉" in it })
    }

    @Test
    fun hardSplittingAlwaysMakesProgress() {
        val text = "长".repeat(500)

        val chunks = processor.splitSentences(text)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.isNotEmpty() && it.length <= 160 })
        assertEquals(text, chunks.joinToString(separator = ""))
    }
}
