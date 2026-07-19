package com.yura.app.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTtsProgressLocatorTest {
    @Test
    fun mapsParagraphToResourceAndPublicationProgression() {
        val result = ReaderTtsProgressLocator.calculate(
            chapterStartTotalProgression = 0.2,
            nextChapterTotalProgression = 0.4,
            paragraphIndex = 5,
            paragraphTotal = 10,
        )

        assertEquals(0.5, result.resource, 0.0001)
        assertEquals(0.3, result.publication!!, 0.0001)
    }

    @Test
    fun clampsInvalidParagraphAndUsesBookEndForLastChapter() {
        val result = ReaderTtsProgressLocator.calculate(
            chapterStartTotalProgression = 0.8,
            nextChapterTotalProgression = null,
            paragraphIndex = 99,
            paragraphTotal = 4,
        )

        assertEquals(0.75, result.resource, 0.0001)
        assertEquals(0.95, result.publication!!, 0.0001)
    }
}
