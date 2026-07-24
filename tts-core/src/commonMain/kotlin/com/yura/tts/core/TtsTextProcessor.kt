package com.yura.tts.core

class TtsTextProcessor {
    fun splitSentences(text: String): List<String> =
        splitPreparedSentences(clean(text), ::splitLongChunk)

    fun splitSourceSentences(text: String): List<String> =
        splitPreparedSentences(prepareSource(text), ::splitLongSourceChunk)

    private fun splitPreparedSentences(
        cleaned: String,
        longChunkSplitter: (String) -> List<String>,
    ): List<String> {
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
        return sentences.filter(String::isNotBlank).ifEmpty { listOf(cleaned) }.flatMap(longChunkSplitter)
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

    fun cleanForMimo(text: String): String {
        var cleaned = decodeHtmlEntities(text)
        cleaned = MARKDOWN_LINK_REGEX.replace(cleaned, "$1")
        cleaned = normalizeAngleTagsForMimo(cleaned)
            .replace(MARKDOWN_DECORATION_REGEX, " ")
            .replace(BARE_ELLIPSIS_REGEX, "……")
            .replace(LONG_DASH_REGEX, "——")
            .replace(DECORATIVE_REPEAT_REGEX, " ")
            .replace(MIMO_DECORATIVE_REPEAT_REGEX, " ")
            .replace(MIMO_SEPARATOR_REPEAT_REGEX, " ")
            .replace(REPLACEMENT_CHARACTER_REGEX, "")
            .replace(PRIVATE_USE_REGEX, "")

        cleaned = removeMimoSquareBrackets(cleaned)
            .replace(MIMO_CHECKMARK_REGEX, "")
        cleaned = stripEmoji(cleaned)
            .replace(MIMO_DECORATIVE_EDGE_REGEX, "")
            .replace(MIMO_DECORATIVE_AFTER_OPENER_REGEX, "$1")
            .replace(MIMO_DECORATIVE_BEFORE_CLOSER_REGEX, "$1")

        cleaned = TextNormalizer.normalize(cleaned)
        cleaned = normalizeHalfWidthPunctuation(cleaned)
        cleaned = normalizeMimoTerminators(cleaned)
            .replace(REPEATED_SEPARATOR_REGEX) { match -> match.value.last().toString() }
            .replace(SPACE_BEFORE_CLOSER_REGEX, "$1")
            .replace(SPACE_AFTER_OPENER_REGEX, "$1")
            .replace(MIMO_SPACE_BEFORE_CLOSER_REGEX, "$1")
            .replace(MIMO_SPACE_AFTER_OPENER_REGEX, "$1")
            .replace(CJK_BEFORE_LATIN_NUMBER_REGEX, "$1 $2")
            .replace(LATIN_NUMBER_BEFORE_CJK_REGEX, "$1 $2")
            .replace(ZERO_WIDTH_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
        return cleaned.takeUnless { value -> value.isBlank() || value.none { it.isLetterOrDigit() } }.orEmpty()
    }

    private fun prepareSource(text: String): String = text
        .replace(BARE_ELLIPSIS_REGEX, "……")
        .replace(LONG_DASH_REGEX, "——")
        .replace(ZERO_WIDTH_REGEX, "")
        .replace(WHITESPACE_REGEX, " ")
        .trim()

    private fun decodeHtmlEntities(text: String): String {
        if ('&' !in text) return text
        return HTML_ENTITY_REGEX.replace(text) { match ->
            val body = match.groupValues[1]
            when {
                body.startsWith("#x", ignoreCase = true) -> body.substring(2).toIntOrNull(16)?.let(::codePointToString)
                body.startsWith("#") -> body.substring(1).toIntOrNull()?.let(::codePointToString)
                else -> HTML_ENTITIES[body.lowercase()]
            } ?: match.value
        }
    }

    private fun codePointToString(codePoint: Int): String? = when (codePoint) {
        in 0..0xFFFF -> codePoint.toChar().toString()
        in 0x10000..0x10FFFF -> {
            val value = codePoint - 0x10000
            charArrayOf(
                ((value shr 10) + 0xD800).toChar(),
                ((value and 0x3FF) + 0xDC00).toChar(),
            ).concatToString()
        }
        else -> null
    }

    private fun normalizeAngleTagsForMimo(text: String): String = HTML_TAG_REGEX.replace(text) { match ->
        val raw = match.value
        if (raw.startsWith("<!--")) return@replace " "
        val content = raw.substring(1, raw.length - 1).trim()
        val tagName = content
            .removePrefix("/")
            .substringBefore(' ')
            .substringBefore('/')
            .lowercase()
        if (tagName in HTML_TAG_NAMES) " " else "【$content】"
    }

    private fun removeMimoSquareBrackets(text: String): String = buildString(text.length) {
        text.forEach { character ->
            if (character !in MIMO_SQUARE_BRACKETS) append(character)
        }
    }

    private fun stripEmoji(text: String): String = buildString(text.length) {
        var index = 0
        while (index < text.length) {
            val first = text[index]
            val codePoint: Int
            val width: Int
            if (first.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
                codePoint = 0x10000 + ((first.code - 0xD800) shl 10) + (text[index + 1].code - 0xDC00)
                width = 2
            } else {
                codePoint = first.code
                width = 1
            }
            if (!isEmojiCodePoint(codePoint)) {
                append(text, index, index + width)
            }
            index += width
        }
    }

    private fun isEmojiCodePoint(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF ||
            codePoint in 0x2600..0x27BF ||
            codePoint in 0x1F1E6..0x1F1FF ||
            codePoint in 0x1F3FB..0x1F3FF ||
            codePoint == 0x200D ||
            codePoint == 0x20E3 ||
            codePoint == 0xFE0E ||
            codePoint == 0xFE0F ||
            codePoint == 0x00A9 ||
            codePoint == 0x00AE ||
            codePoint == 0x2122

    private fun normalizeMimoTerminators(text: String): String = MIMO_REPEATED_TERMINATOR_REGEX.replace(text) { match ->
        limitRepeatedCharacters(match.value, maximumRunLength = 2)
    }

    private fun limitRepeatedCharacters(value: String, maximumRunLength: Int): String = buildString(value.length) {
        var previous = Char.MIN_VALUE
        var repeated = 0
        value.forEach { character ->
            if (character == previous) {
                repeated++
            } else {
                previous = character
                repeated = 1
            }
            if (repeated <= maximumRunLength) append(character)
        }
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
        val rawTail = text.substring(closingQuoteIndex + 1)
        val tail = rawTail.trimStart()
        if (text[closingQuoteIndex] in INLINE_LABEL_CLOSERS && tail.isNotEmpty()) return true
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
        val prefix = text.substring(0, index + 1)
        if (ABBREVIATION_SUFFIX_REGEX.containsMatchIn(prefix)) return false
        if (INITIALISM_SUFFIX_REGEX.containsMatchIn(prefix) && hasFollowingSpeechText(text, index)) return false
        return true
    }

    private fun hasFollowingSpeechText(text: String, periodIndex: Int): Boolean {
        for (index in periodIndex + 1 until text.length) {
            val character = text[index]
            if (character.isWhitespace()) continue
            return character.isLowerCase() ||
                character.isDigit() ||
                character.isCjk() ||
                character in setOf(',', '，', ';', '；', ':', '：')
        }
        return false
    }

    private fun Char.isCjk(): Boolean =
        this in '\u3400'..'\u9FFF' || this in '\uF900'..'\uFAFF'
    private fun splitLongChunk(text: String): List<String> = splitLongPrepared(clean(text))

    private fun splitLongSourceChunk(text: String): List<String> = splitLongPrepared(prepareSource(text))

    private fun splitLongPrepared(cleaned: String): List<String> {
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

        val OPENING_QUOTES = setOf('“', '‘', '「', '『', '【', '〖', '〔', '《', '（', '[', '［')
        val CLOSING_QUOTES = setOf('”', '’', '」', '』', '】', '〗', '〕', '》', '）', ']', '］')
        val TRAILING_CLOSERS = CLOSING_QUOTES + setOf('"', 39.toChar())
        val INLINE_LABEL_CLOSERS = setOf(']', '］', '】', '〗', '〕')
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
            """(?i)(?:\b(?:mr|mrs|ms|dr|st|vs|etc|prof|sr|jr|no|fig|vol|dept|inc|ltd|co)\.|\b(?:e\.g|i\.e|ph\.d)\.)$"""
        )
        val INITIALISM_SUFFIX_REGEX = Regex("""(?i)(?:\b[A-Z]\.){2,}$""")

        val MARKDOWN_LINK_REGEX = Regex("""\[([^\]]+)]\((?:https?://|www\.)[^)]+\)""", RegexOption.IGNORE_CASE)
        val HTML_ENTITY_REGEX = Regex("""&([a-zA-Z]+|#\d+|#x[0-9a-fA-F]+);""")
        val MIMO_SQUARE_BRACKETS = setOf('[', ']', '［', '］', '【', '】', '〖', '〗', '〚', '〛')
        val MIMO_CHECKMARK_REGEX = Regex("[√✓✔☑]+")
        val MIMO_DECORATIVE_REPEAT_REGEX = Regex("""[△▲▼▽◆◇★☆●○◎■□※♬♪▶▷◀◁▌▍▎▏〓㊣]{2,}""")
        val MIMO_SEPARATOR_REPEAT_REGEX = Regex("""[/\\|]{3,}""")
        val MIMO_DECORATIVE_EDGE_REGEX = Regex("""^[\s△▲▼▽◆◇★☆●○◎■□※♬♪▶▷◀◁▌▍▎▏〓㊣]+|[\s△▲▼▽◆◇★☆●○◎■□※♬♪▶▷◀◁▌▍▎▏〓㊣]+$""")
        val MIMO_DECORATIVE_AFTER_OPENER_REGEX = Regex("""([【〖〔「『《“‘])\s*[△▲▼▽◆◇★☆●○◎■□※♬♪▶▷◀◁▌▍▎▏〓㊣]+""")
        val MIMO_DECORATIVE_BEFORE_CLOSER_REGEX = Regex("""[△▲▼▽◆◇★☆●○◎■□※♬♪▶▷◀◁▌▍▎▏〓㊣]+\s*([】〗〕」』》”’])""")
        val MIMO_REPEATED_TERMINATOR_REGEX = Regex("""[。！？]{2,}""")
        val MIMO_SPACE_BEFORE_CLOSER_REGEX = Regex("""\s+([〗〕］])""")
        val MIMO_SPACE_AFTER_OPENER_REGEX = Regex("""([〖〔［])\s+""")
        val REPLACEMENT_CHARACTER_REGEX = Regex("""�""")
        val PRIVATE_USE_REGEX = Regex("""[\uE000-\uF8FF]""")
        val HTML_TAG_NAMES = setOf(
            "html", "head", "body", "title", "meta", "link", "style", "script", "p", "br", "div", "span",
            "section", "article", "header", "footer", "main", "nav", "aside", "h1", "h2", "h3", "h4", "h5", "h6",
            "ul", "ol", "li", "blockquote", "pre", "code", "em", "strong", "b", "i", "u", "s", "a", "img", "ruby",
            "rt", "rp", "table", "thead", "tbody", "tfoot", "tr", "td", "th", "hr", "audio", "video", "source",
        )
        val HTML_ENTITIES = mapOf(
            "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
            "ndash" to "–", "mdash" to "—", "hellip" to "……", "ldquo" to "“", "rdquo" to "”",
            "lsquo" to "‘", "rsquo" to "’", "middot" to "·", "copy" to "©", "reg" to "®", "trade" to "™",
        )
    }
}
