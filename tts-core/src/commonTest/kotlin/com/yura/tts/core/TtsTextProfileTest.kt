package com.yura.tts.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TtsTextProfileTest {
    private val processor = TtsTextProcessor()
    private val system = SystemTtsTextProfile(processor)
    private val microsoft = MicrosoftTtsTextProfile(processor)
    private val mimo = MimoTtsTextProfile(processor)

    @Test
    fun systemAndMicrosoftKeepValidatedCleaningBehavior() {
        val source = "“咿哈哈哈哈哈哈……”"

        assertEquals("“咿哈哈哈哈哈哈”", system.prepare(source))
        assertEquals("“咿哈哈哈哈哈哈”", microsoft.prepare(source))
    }

    @Test
    fun mimoKeepsProsodyPunctuation() {
        assertEquals("“咿哈哈哈哈哈哈……”", mimo.prepare("“咿哈哈哈哈哈哈……”"))
        assertEquals("“咿哈哈哈哈哈哈！”", mimo.prepare("“咿哈哈哈哈哈哈！”"))
        assertEquals("“咿哈哈哈……”", mimo.prepare("“咿哈哈哈……”"))
        assertEquals("等等？！", mimo.prepare("等等？！"))
        assertEquals("太好了！！", mimo.prepare("太好了！！！！"))
    }

    @Test
    fun mimoNeutralizesControlTagsAndDecorativeSymbols() {
        assertEquals("【系统提示：力量↑10】", mimo.prepare("[系统提示：力量↑10]"))
        assertEquals("〔轻笑〕他说——", mimo.prepare("（轻笑）他说————"))
        assertEquals("〔轻笑〕他说。", mimo.prepare("（ 轻笑 ）他说。"))
        assertEquals(
            "【爆炸的火焰吞没塔吊，砸向追击者之间……】",
            mimo.prepare("【△爆炸的火焰吞没塔吊，砸向追击者之间......】"),
        )
        assertEquals("场景切换", mimo.prepare("◆◆◆ 场景切换 ◆◆◆"))
    }

    @Test
    fun mimoRemovesEmojiAndKeepsReadableText() {
        assertEquals("哈哈哈哈", mimo.prepare("哈哈哈哈😂😂😂"))
        assertEquals("警告前方高能", mimo.prepare("警告⚠️前方高能💥"))
        assertEquals("门铃响了", mimo.prepare("♬♪门铃响了♪♬"))
    }

    @Test
    fun mimoDecodesEntitiesLinksAndPseudoTags() {
        assertEquals("他回来了&她也来了。", mimo.prepare("&nbsp;他回来了&amp;她也来了。"))
        assertEquals("角色设定", mimo.prepare("[角色设定](https://example.com/profile)"))
        assertEquals("【系统提示】获得奖励。", mimo.prepare("<系统提示>获得奖励。"))
    }

    @Test
    fun sourceSplittingPreservesMimoProsodyAndBoundaries() {
        val sentences = processor.splitSourceSentences("他说：“等等……”然后继续。下一句——结束。")

        assertEquals(listOf("他说：“等等……”然后继续。", "下一句——结束。"), sentences)
        assertTrue(sentences.all { it.length <= 160 })
    }

    @Test
    fun legacyProfilesMatchPreviousSentencePipeline() {
        val samples = listOf(
            "他说：“等等？！”然后继续。",
            "第一句。第二句！Third sentence. Visit example.com.",
            "你好…… ……世界！！",
            "会议在2026-07-20,下午10:34开始。",
            buildString { repeat(12) { append("这是用于测试超长朗读文本分块的内容，") } },
        )

        samples.forEach { source ->
            val previous = processor.splitSentences(source)
            val sourceSentences = processor.splitSourceSentences(source)
            assertEquals(previous, sourceSentences.map(system::prepare).filter(String::isNotBlank))
            assertEquals(previous, sourceSentences.map(microsoft::prepare).filter(String::isNotBlank))
            assertEquals(sourceSentences.size, sourceSentences.map(mimo::prepare).size)
        }
    }


    @Test
    fun sourceSplittingKeepsBracketedSystemNoticesTogether() {
        assertEquals(
            listOf("[系统提示！]任务完成。", "下一句。"),
            processor.splitSourceSentences("[系统提示！]任务完成。下一句。"),
        )
    }

}
