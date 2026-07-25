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
        assertEquals("系统提示：力量↑10", mimo.prepare("[系统提示：力量↑10]"))
        assertEquals("（轻笑）他说——", mimo.prepare("（轻笑）他说————"))
        assertEquals("（轻笑）他说。", mimo.prepare("（ 轻笑 ）他说。"))
        assertEquals(
            "爆炸的火焰吞没塔吊，砸向追击者之间……",
            mimo.prepare("【△爆炸的火焰吞没塔吊，砸向追击者之间......】"),
        )
        assertEquals("场景切换", mimo.prepare("◆◆◆ 场景切换 ◆◆◆"))
        assertEquals("演员", mimo.prepare("【演员】√"))
        assertEquals("演员，开场。", mimo.prepare("【演员】√，开场。"))
        assertEquals("旁白", mimo.prepare("[旁白]✓"))
        assertEquals("角色台词", mimo.prepare("［角色］【台词】"))
        assertEquals("旁白正文", mimo.prepare("〖旁白〗〚正文〛"))
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
        assertEquals("系统提示获得奖励。", mimo.prepare("<系统提示>获得奖励。"))
    }

    @Test
    fun mimoRemovesHtmlTagsAttributesAndNonSpeechBlocks() {
        assertEquals(
            "正文继续。",
            mimo.prepare("<span class=\"hero\" data-id=\"42\">正文</span><img src=\"cover.jpg\" alt=\"封面\"/>继续。"),
        )
        assertEquals("自定义正文。", mimo.prepare("<custom-widget data-value=\"abc\">自定义正文。</custom-widget>"))
        assertEquals("前后。", mimo.prepare("前<script>window.alert('不要朗读')</script>后。"))
        assertEquals("可见正文。", mimo.prepare("<![CDATA[可见正文。]]>"))
        assertEquals("半截正文", mimo.prepare("半截正文<span class=\"broken\""))
        assertEquals("汉字。", mimo.prepare("<ruby>汉<rt>han</rt><rp>（</rp><rt>hàn</rt><rp>）</rp></ruby>字。"))
    }

    @Test
    fun mimoRemovesUnsafeUnicodeWithoutChangingDialoguePunctuation() {
        val unsafe = "\u0000\u0007\u00AD\u200B\u200E\u202E\u2060\u3164\uFFFC\uFFFD\uE000"

        assertEquals("他说：“好了。”", microsoft.prepare("他${unsafe}说：“好了。”"))
        assertEquals("他说：“好了。”", mimo.prepare("他${unsafe}说：“好了。”"))
        assertEquals("他说：“真的吗？”", mimo.prepare("他说：“真的吗？”"))
        assertEquals("她答：“当然！”", mimo.prepare("她答：“当然！”"))
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
