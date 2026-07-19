package com.yura.app.reader

import org.readium.r2.shared.publication.Locator

internal object ReaderTtsProgressLocator {
    data class Progressions(
        val resource: Double,
        val publication: Double?,
    )

    fun create(
        baseLocator: Locator,
        nextChapterTotalProgression: Double?,
        paragraphIndex: Int,
        paragraphTotal: Int,
    ): Locator {
        val progressions = calculate(
            chapterStartTotalProgression = baseLocator.locations.totalProgression,
            nextChapterTotalProgression = nextChapterTotalProgression,
            paragraphIndex = paragraphIndex,
            paragraphTotal = paragraphTotal,
        )
        return baseLocator.copy(
            locations = baseLocator.locations.copy(
                progression = progressions.resource,
                totalProgression = progressions.publication,
            ),
        )
    }

    fun calculate(
        chapterStartTotalProgression: Double?,
        nextChapterTotalProgression: Double?,
        paragraphIndex: Int,
        paragraphTotal: Int,
    ): Progressions {
        val safeTotal = paragraphTotal.coerceAtLeast(1)
        val resourceProgression = paragraphIndex
            .coerceIn(0, safeTotal - 1)
            .toDouble() / safeTotal.toDouble()
        val publicationProgression = chapterStartTotalProgression?.let { start ->
            val end = nextChapterTotalProgression
                ?.coerceIn(start, 1.0)
                ?: 1.0
            (start + (end - start) * resourceProgression).coerceIn(0.0, 1.0)
        }
        return Progressions(
            resource = resourceProgression,
            publication = publicationProgression,
        )
    }
}
