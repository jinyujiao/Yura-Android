package com.yura.tts.core

class TtsTextProcessor {
    fun splitSentences(text: String): List<String> {
        val cleaned = clean(text)
        if (cleaned.isBlank()) return emptyList()
        val sentences = mutableListOf<String>()
        val openingQuotes = setOf('“', '‘', '「', '『', '【', '《', '（')
        val closingQuotes = setOf('”', '’', '」', '』', '】', '》', '）')
        var quoteDepth = 0
        var start = 0
        var index = 0
        while (index < cleaned.length) {
            val character = cleaned[index]
            when {
                character in openingQuotes -> quoteDepth++
                character in closingQuotes -> quoteDepth = (quoteDepth - 1).coerceAtLeast(0)
                character == '"' || character == '\'' -> quoteDepth = if (quoteDepth == 0) 1 else 0
                quoteDepth == 0 && character in setOf('。', '！', '？', '!', '?', '.') -> {
                    var end = index + 1
                    while (end < cleaned.length && cleaned[end].isWhitespace()) end++
                    sentences += cleaned.substring(start, end).trim()
                    start = end
                    index = end
                }
            }
            index++
        }
        if (start < cleaned.length) sentences += cleaned.substring(start).trim()
        return sentences.filter(String::isNotBlank).ifEmpty { listOf(cleaned) }.flatMap(::splitLongChunk)
    }
    fun clean(text: String): String {
        val cleaned = text
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("[`*_#~]+"), " ")
            .replace(Regex("[“”\\\"‘’']\\s*(?:[.．｡﹒․·•・…⋯︙︰]\\s*){2,}[“”\\\"‘’']"), " ")
            .replace(Regex("(?:[.．｡﹒․·•・…⋯︙︰]\\s*){2,}"), " ")
            .replace(Regex("[—–-]{2,}"), " ")
            .replace(Regex("[~～_＿=＝*＊#＃]{2,}"), " ")
                        .replace(Regex("[。！？!?]{2,}")) { match -> match.value.last().toString() }
            .replace(Regex("[，,、；;：:]{2,}")) { match -> match.value.last().toString() }
            .replace(Regex("\\s+([”\"’'」』】》）])"), "$1")
            .replace(Regex("([“\"‘'「『【《（])\\s+"), "$1")
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.takeUnless { value -> value.isBlank() || value.none { it.isLetterOrDigit() } }.orEmpty()
    }

    private fun splitLongChunk(text: String): List<String> {
        val cleaned = clean(text)
        if (cleaned.length <= 160) return listOf(cleaned)
        val chunks = mutableListOf<String>()
        var remaining = cleaned
        while (remaining.length > 160) {
            val limit = 160.coerceAtMost(remaining.length)
            val min = 70.coerceAtMost(limit)
            val sample = remaining.substring(0, limit)
            val splitAt = Regex("[，,、；;：:]|\\s+").findAll(sample)
                .map { it.range.last + 1 }.filter { it >= min }.lastOrNull() ?: limit
            chunks += remaining.substring(0, splitAt).trim()
            remaining = remaining.substring(splitAt).trimStart()
        }
        if (remaining.isNotBlank()) chunks += remaining
        return chunks.filter(String::isNotBlank)
    }
}
