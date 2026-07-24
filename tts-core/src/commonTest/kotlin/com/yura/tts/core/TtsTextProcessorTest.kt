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

        assertEquals(listOf("第一句。", "第二句！", "Third sentence.", "Final question？"), sentences)
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

    @Test
    fun normalizesDatesAndTimesForSpeech() {
        assertEquals(
            "会议在二零二六年七月二十日，下午十点三十四分开始。",
            processor.clean("会议在2026-07-20,下午10:34开始。"),
        )
        assertEquals("凌晨零点整开始。", processor.clean("凌晨00:00开始。"))
        assertEquals("倒计时十二点三十分四十五秒。", processor.clean("倒计时12:30:45。"))
    }

    @Test
    fun normalizesPercentagesCurrenciesAndThousands() {
        assertEquals(
            "价格是一千二百三十四点五六美元，增长百分之五十点五。",
            processor.clean("价格是\u00241,234.56,增长50.5%。"),
        )
        assertEquals("余额一万零一元。", processor.clean("余额￥10,001。"))
        assertEquals("人数 1234567 人。", processor.clean("人数1,234,567人。"))
    }

    @Test
    fun leavesInvalidDatesAndTimesUnlocalized() {
        assertEquals(
            "时间 99：99，日期 2026-02-30。",
            processor.clean("时间99:99,日期2026-02-30。"),
        )
    }

    @Test
    fun insertsSpacesBetweenCjkAndLatinOrNumbers() {
        assertEquals("使用 Kotlin 写代码 50 次。", processor.clean("使用Kotlin写代码50次。"))
    }

    @Test
    fun convertsHalfWidthPunctuationGlobally() {
        assertEquals(
            "Hello， world！ （test）： ok？",
            processor.clean("Hello, world! (test): ok?"),
        )
    }

    @Test
    fun normalizationIsIdempotent() {
        val once = processor.clean("会议在2026-07-20,价格\u00241,234.56,增长50%。")

        assertEquals(once, processor.clean(once))
    }

    @Test
    fun keepsInitialismsNumberedAbbreviationsAndTechnicalVersionsTogether() {
        assertEquals(
            listOf("The U.S.A. team uses API v1.2.3.", "No. 5 passed.", "A Ph.D. student replied."),
            processor.splitSentences("The U.S.A. team uses API v1.2.3. No. 5 passed. A Ph.D. student replied."),
        )
        assertEquals(
            listOf("He moved to the U.S.A.", "Next sentence."),
            processor.splitSentences("He moved to the U.S.A. Next sentence."),
        )
    }

    @Test
    fun handlesChineseEnglishMixedTextWithDomainsAndDialoguePunctuation() {
        assertEquals(
            listOf(
                "版本 v1.2.3 已发布，API 地址是 api.example.com/v2。",
                "He said\"更新完成！\"然后继续。",
                "成功率百分之九十九点九。",
            ),
            processor.splitSentences(
                "版本v1.2.3已发布,API地址是api.example.com/v2。He said \"更新完成!\"然后继续。成功率99.9%。",
            ),
        )
    }

    @Test
    fun keepsEmailIpFileNamesAndDecimalMeasurementsInsideSentences() {
        assertEquals(
            listOf(
                "联系 test.user@example.com。",
                "服务器 192.168.1.10 正常。",
                "打开 report.final.txt，误差 0.05mm。",
            ),
            processor.splitSentences(
                "联系test.user@example.com。服务器192.168.1.10正常。打开report.final.txt,误差0.05mm。",
            ),
        )
    }
}
