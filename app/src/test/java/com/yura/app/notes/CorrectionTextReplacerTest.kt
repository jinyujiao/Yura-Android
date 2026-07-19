package com.yura.app.notes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionTextReplacerTest {
    @Test
    fun usesContextToReplaceOnlyMatchingOccurrence() {
        val html = "<html><body><p>甲看见他门离开。</p><p>乙跟着他门回来。</p></body></html>"
        val correction = correction(
            id = "1",
            before = "乙跟着",
            original = "他门",
            after = "回来",
            replacement = "他们",
        )

        val result = CorrectionTextReplacer.apply(html, listOf(correction))

        assertTrue(result.content.contains("甲看见他门离开"))
        assertTrue(result.content.contains("乙跟着他们回来"))
        assertTrue("1" in result.appliedIds)
    }

    @Test
    fun replacesTextAcrossInlineNodes() {
        val html = "<html><body><p>大家看见他<span>门</span>一起离开。</p></body></html>"
        val correction = correction(
            id = "2",
            before = "大家看见",
            original = "他门",
            after = "一起离开",
            replacement = "他们",
        )

        val result = CorrectionTextReplacer.apply(html, listOf(correction))

        assertTrue(result.content.contains("他们"))
        assertFalse(result.content.contains("他<span>门"))
        assertTrue("2" in result.appliedIds)
    }

    @Test
    fun leavesContentUntouchedWhenOriginalCannotBeLocated() {
        val html = "<html><body><p>这里没有错字。</p></body></html>"
        val correction = correction(
            id = "3",
            before = "这里",
            original = "他门",
            after = "离开",
            replacement = "他们",
        )

        val result = CorrectionTextReplacer.apply(html, listOf(correction))

        assertTrue(result.content.contains("这里没有错字"))
        assertTrue(result.appliedIds.isEmpty())
    }

    @Test
    fun appliesMultipleCorrectionsWithoutShiftingEarlierRanges() {
        val html = "<html><body><p>他门先出发，随后在做决定。</p></body></html>"
        val corrections = listOf(
            correction("4", "", "他门", "先出发", "他们"),
            correction("5", "随后", "在做", "决定", "再做认真"),
        )

        val result = CorrectionTextReplacer.apply(html, corrections)

        assertTrue(result.content.contains("他们先出发，随后再做认真决定"))
        assertTrue(result.appliedIds == setOf("4", "5"))
    }

    private fun correction(
        id: String,
        before: String,
        original: String,
        after: String,
        replacement: String,
    ): TextCorrection = TextCorrection(
        id = id,
        before = before,
        original = original,
        after = after,
        replacement = replacement,
    )
}
