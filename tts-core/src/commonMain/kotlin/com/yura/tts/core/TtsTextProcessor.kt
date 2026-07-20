package com.yura.tts.core

class TtsTextProcessor {
    fun splitSentences(text: String): List<String> {
        val cleaned = clean(text)
        if (cleaned.isBlank()) return emptyList()

        val sentences = mutableListOf<String>()
        var pairedQuoteDepth = 0
        var straightDoubleQuoteOpen = false
        var start = 0
        var index = 0
        while (index < cleaned.length) {
            val character = cleaned[index]
            when {
                character in OPENING_QUOTES -> pairedQuoteDepth++
                character in CLOSING_QUOTES -> {
                    pairedQuoteDepth = (pairedQuoteDepth - 1).coerceAtLeast(0)
                    if (
                        pairedQuoteDepth == 0 &&
                        !straightDoubleQuoteOpen &&
                        endsWithSentenceTerminator(cleaned, index) &&
                        !continuesAfterClosingQuote(cleaned, index)
                    ) {
                        val nextStart = flushSentence(cleaned, start, index + 1, sentences)
                        start = nextStart
                        index = nextStart
                        continue
                    }
                }
                character == '"' -> {
                    straightDoubleQuoteOpen = !straightDoubleQuoteOpen
                    if (
                        !straightDoubleQuoteOpen &&
                        pairedQuoteDepth == 0 &&
                        endsWithSentenceTerminator(cleaned, index) &&
                        !continuesAfterClosingQuote(cleaned, index)
                    ) {
                        val nextStart = flushSentence(cleaned, start, index + 1, sentences)
                        start = nextStart
                        index = nextStart
                        continue
                    }
                }
                pairedQuoteDepth == 0 &&
                    !straightDoubleQuoteOpen &&
                    isSentenceTerminator(cleaned, index) -> {
                    val nextStart = flushSentence(cleaned, start, index + 1, sentences)
                    start = nextStart
                    index = nextStart
                    continue
                }
            }
            index++
        }

        if (start < cleaned.length) {
            sentences += cleaned.substring(start).trim()
        }
        return sentences.filter(String::isNotBlank).ifEmpty { listOf(cleaned) }.flatMap(::splitLongChunk)
    }

    fun clean(text: String): String {
        var cleaned = text
            .replace(HTML_TAG_REGEX, " ")
            .replace(MARKDOWN_DECORATION_REGEX, " ")
            .replace(QUOTED_ELLIPSIS_REGEX, " ")
            .replace(BARE_ELLIPSIS_REGEX, " ")
            .replace(LONG_DASH_REGEX, " ")
            .replace(DECORATIVE_REPEAT_REGEX, " ")
            .replace(REPEATED_TERMINATOR_REGEX) { match -> match.value.last().toString() }
            .replace(REPEATED_SEPARATOR_REGEX) { match -> match.value.last().toString() }

        cleaned = TextNormalizer.normalize(cleaned)
        cleaned = normalizeHalfWidthPunctuation(cleaned)
            .replace(SPACE_BEFORE_CLOSER_REGEX, "$1")
            .replace(SPACE_AFTER_OPENER_REGEX, "$1")
            .replace(CJK_BEFORE_LATIN_NUMBER_REGEX, "$1 $2")
            .replace(LATIN_NUMBER_BEFORE_CJK_REGEX, "$1 $2")
            .replace(ZERO_WIDTH_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
        return cleaned.takeUnless { value -> value.isBlank() || value.none { it.isLetterOrDigit() } }.orEmpty()
    }

    private fun normalizeHalfWidthPunctuation(text: String): String = buildString(text.length) {
        text.forEach { character -> append(HALF_WIDTH_PUNCTUATION[character] ?: character) }
    }

    private fun flushSentence(
        text: String,
        start: Int,
        initialEnd: Int,
        sentences: MutableList<String>,
    ): Int {
        var end = initialEnd
        while (end < text.length && text[end] in TRAILING_CLOSERS) end++
        while (end < text.length && text[end].isWhitespace()) end++
        text.substring(start, end).trim().takeIf(String::isNotBlank)?.let(sentences::add)
        return end
    }

    private fun endsWithSentenceTerminator(text: String, closingQuoteIndex: Int): Boolean {
        val terminatorIndex = closingQuoteIndex - 1
        return terminatorIndex >= 0 && isSentenceTerminator(text, terminatorIndex)
    }

    private fun continuesAfterClosingQuote(text: String, closingQuoteIndex: Int): Boolean {
        val tail = text.substring(closingQuoteIndex + 1).trimStart()
        return QUOTE_CONTINUATION_PREFIXES.any(tail::startsWith) ||
            QUOTE_ATTRIBUTION_REGEX.containsMatchIn(tail)
    }

    private fun isSentenceTerminator(text: String, index: Int): Boolean {
        val character = text[index]
        if (character in HARD_TERMINATORS) return true
        if (character != '.') return false

        val previous = text.getOrNull(index - 1)
        val next = text.getOrNull(index + 1)
        if (previous?.isDigit() == true && next?.isDigit() == true) return false
        if (previous?.isLetter() == true && next?.isLetter() == true) return false
        if (ABBREVIATION_SUFFIX_REGEX.containsMatchIn(text.substring(0, index + 1))) return false
        return true
    }

    private fun splitLongChunk(text: String): List<String> {
        val cleaned = clean(text)
        if (cleaned.length <= MAX_CHUNK_LENGTH) return listOf(cleaned)

        val chunks = mutableListOf<String>()
        var remaining = cleaned
        while (remaining.length > MAX_CHUNK_LENGTH) {
            val limit = MAX_CHUNK_LENGTH.coerceAtMost(remaining.length)
            val minimum = MIN_CHUNK_LENGTH.coerceAtMost(limit)
            val sample = remaining.substring(0, limit)
            val preferredSplit = SOFT_BREAK_REGEX.findAll(sample)
                .map { match -> match.range.last + 1 }
                .filter { split -> split >= minimum }
                .lastOrNull()
                ?: limit
            val safeSplit = avoidSurrogateBoundary(remaining, preferredSplit).coerceAtLeast(1)
            remaining.substring(0, safeSplit).trim().takeIf(String::isNotBlank)?.let(chunks::add)
            val nextRemaining = remaining.substring(safeSplit).trimStart()
            if (nextRemaining.length >= remaining.length) {
                val forcedSplit = avoidSurrogateBoundary(remaining, minimum.coerceAtLeast(1)).coerceAtLeast(1)
                remaining.substring(0, forcedSplit).trim().takeIf(String::isNotBlank)?.let(chunks::add)
                remaining = remaining.substring(forcedSplit).trimStart()
            } else {
                remaining = nextRemaining
            }
        }
        remaining.takeIf(String::isNotBlank)?.let(chunks::add)
        return chunks
    }

    private fun avoidSurrogateBoundary(text: String, split: Int): Int {
        if (split <= 0 || split >= text.length) return split
        return if (text[split - 1].isHighSurrogate() && text[split].isLowSurrogate()) split - 1 else split
    }

    private companion object {
        const val MAX_CHUNK_LENGTH = 160
        const val MIN_CHUNK_LENGTH = 70

        val OPENING_QUOTES = setOf('“', '‘', '「', '『', '【', '《', '（')
        val CLOSING_QUOTES = setOf('”', '’', '」', '』', '】', '》', '）')
        val TRAILING_CLOSERS = CLOSING_QUOTES + setOf('"', 39.toChar())
        val HARD_TERMINATORS = setOf('。', '！', '？', '!', '?')
        val HALF_WIDTH_PUNCTUATION = mapOf(
            ',' to '，',
            ';' to '；',
            ':' to '：',
            '?' to '？',
            '!' to '！',
            '(' to '（',
            ')' to '）',
        )
        val QUOTE_CONTINUATION_PREFIXES = setOf("然后", "接着", "随后", "并", "却", "又", "才", "说道", "说", "问道", "问", "答道", "答", "喊道", "喊", "叫道", "叫", "道")

        val HTML_TAG_REGEX = Regex("""<[^>]+>""")
        val MARKDOWN_DECORATION_REGEX = Regex("""[\u0060*_#~]+""")
        val QUOTED_ELLIPSIS_REGEX = Regex("""[“”"‘’']\s*(?:[.．｡﹒․·•・…⋯︙︰]\s*){2,}[“”"‘’']""")
        val BARE_ELLIPSIS_REGEX = Regex("""(?:[.．｡﹒․·•・…⋯︙︰]\s*){2,}""")
        val LONG_DASH_REGEX = Regex("""[—–-]{2,}""")
        val DECORATIVE_REPEAT_REGEX = Regex("""[~～_＿=＝*＊#＃]{2,}""")
        val REPEATED_TERMINATOR_REGEX = Regex("""[。！？!?]{2,}""")
        val REPEATED_SEPARATOR_REGEX = Regex("""[，,、；;：:]{2,}""")
        val SPACE_BEFORE_CLOSER_REGEX = Regex("""\s+([”"’'」』】》）])""")
        val SPACE_AFTER_OPENER_REGEX = Regex("""([“"‘'「『【《（])\s+""")
        val CJK_BEFORE_LATIN_NUMBER_REGEX = Regex("""([\u3400-\u9FFF\uF900-\uFAFF])([A-Za-z0-9])""")
        val LATIN_NUMBER_BEFORE_CJK_REGEX = Regex("""([A-Za-z0-9])([\u3400-\u9FFF\uF900-\uFAFF])""")
        val ZERO_WIDTH_REGEX = Regex("""[\u200B-\u200D\uFEFF]""")
        val WHITESPACE_REGEX = Regex("""\s+""")
        val SOFT_BREAK_REGEX = Regex("""[，,、；;：:]|\s+""")
        val QUOTE_ATTRIBUTION_REGEX = Regex("""^(?:他|她|他们|她们)(?:说|问|答|喊|叫|道)""")
        val ABBREVIATION_SUFFIX_REGEX = Regex(
            """(?i)(?:\b(?:mr|mrs|ms|dr|st|vs|etc|prof|sr|jr)\.|\b(?:e\.g|i\.e)\.)$"""
        )
    }
}
