package com.yura.app.reader.tts

import java.text.BreakIterator
import java.util.Locale

internal class TtsTextProcessor(
    private val locale: Locale = Locale.getDefault(),
) {
    fun splitSentences(text: String): List<String> {
        val cleaned = clean(text)
        if (cleaned.isBlank()) return emptyList()

        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(cleaned)

        val sentences = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            cleaned.substring(start, end)
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let(sentences::add)
            start = end
            end = iterator.next()
        }

        val baseSentences = sentences.ifEmpty {
            cleaned.split(Regex("(?<=[。！？!?\\.])\\s+|(?<=[。！？!?])"))
                .map(String::trim)
                .filter(String::isNotBlank)
                .ifEmpty { listOf(cleaned) }
        }
        return baseSentences.flatMap(::splitLongChunk)
    }

    fun clean(text: String): String {
        val cleaned = text
            .replace(Regex("[“”\"‘’']\\s*(?:[.．｡﹒․·•・…⋯︙︰]\\s*){2,}[“”\"‘’']"), " ")
            .replace(Regex("(?:[.．｡﹒․·•・…⋯︙︰]\\s*){2,}"), " ")
            .replace(Regex("[—–-]{2,}"), " ")
            .replace(Regex("[~～_＿=＝*＊#＃]{2,}"), " ")
            .replace(Regex("[“”\"‘’']\\s+[“”\"‘’']"), " ")
            .replace(Regex("([。！？!?，,、；;：:])\\1+"), "$1")
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.takeUnless { value -> value.isBlank() || value.none { it.isLetterOrDigit() } }.orEmpty()
    }

    private fun splitLongChunk(text: String): List<String> {
        val cleaned = clean(text)
        if (cleaned.length <= MAX_CHUNK_CHARS) return listOf(cleaned)

        val chunks = mutableListOf<String>()
        var remaining = cleaned
        while (remaining.length > MAX_CHUNK_CHARS) {
            val splitAt = bestSplitIndex(remaining)
            chunks += remaining.substring(0, splitAt).trim()
            remaining = remaining.substring(splitAt).trimStart()
        }
        if (remaining.isNotBlank()) chunks += remaining.trim()
        return chunks.filter(String::isNotBlank)
    }

    private fun bestSplitIndex(text: String): Int {
        val limit = MAX_CHUNK_CHARS.coerceAtMost(text.length)
        val min = MIN_CHUNK_CHARS.coerceAtMost(limit)
        val preferred = Regex("[，,、；;：:]")
        val whitespace = Regex("\\s+")

        preferred.findAll(text.substring(0, limit))
            .map { it.range.last + 1 }
            .filter { it >= min }
            .lastOrNull()
            ?.let { return it }

        whitespace.findAll(text.substring(0, limit))
            .map { it.range.last + 1 }
            .filter { it >= min }
            .lastOrNull()
            ?.let { return it }

        return limit
    }

    private companion object {
        const val MAX_CHUNK_CHARS = 160
        const val MIN_CHUNK_CHARS = 70
    }
}
