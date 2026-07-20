package com.yura.app.reader

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal object ReaderTtsParagraphParser {
    private const val READABLE_SELECTOR = "p, h1, h2, h3, h4, h5, h6, li, blockquote"
    private val readableTags = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote")

    fun parse(html: String, baseUri: String = ""): List<String> {
        if (html.isBlank()) return emptyList()
        val document = Jsoup.parse(html, baseUri)
        document.select("script, style, nav[epub|type=toc], nav, aside, audio, video").remove()
        val paragraphs = document.select(READABLE_SELECTOR)
            .map(::readableText)
            .filter { it.length >= 2 }
        if (paragraphs.isNotEmpty()) return paragraphs

        val fallbackText = document.body().text().takeIf(String::isNotBlank)
            ?: html.replace(Regex("<[^>]+>"), " ")
        return fallbackText
            .split(Regex("(?<=[。！？!?])\\s*|\\n+"))
            .map(::cleanForTts)
            .filter { it.length >= 2 }
            .ifEmpty { listOf(cleanForTts(fallbackText)).filter { it.length >= 2 } }
    }

    private fun readableText(element: Element): String {
        val readableDescendants = element.getAllElements()
            .asSequence()
            .drop(1)
            .filter { descendant -> descendant.normalName() in readableTags }
            .toList()
        if (readableDescendants.isEmpty()) return cleanForTts(element.text())

        val clone = element.clone()
        clone.getAllElements()
            .asSequence()
            .drop(1)
            .filter { descendant -> descendant.normalName() in readableTags }
            .toList()
            .asReversed()
            .forEach(Element::remove)
        return cleanForTts(clone.text())
    }

    fun clean(text: String): String = cleanInternal(text, preserveProsody = false)

    fun cleanForTts(text: String): String = cleanInternal(text, preserveProsody = true)

    private fun cleanInternal(text: String, preserveProsody: Boolean): String {
        var cleaned = text
            .replace(QUOTED_ELLIPSIS_REGEX) { match -> if (preserveProsody) match.value else " " }
            .replace(ELLIPSIS_REGEX) { if (preserveProsody) "……" else " " }
            .replace(LONG_DASH_REGEX) { if (preserveProsody) "——" else " " }
            .replace(DECORATIVE_REPEAT_REGEX, " ")
            .replace(REPEATED_TERMINATOR_REGEX) { match ->
                if (preserveProsody) limitRepeatedTerminators(match.value) else match.value.last().toString()
            }
            .replace(REPEATED_SEPARATOR_REGEX) { match -> match.value.last().toString() }
            .replace(ZERO_WIDTH_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
        return cleaned.takeUnless { value -> value.isBlank() || value.none { it.isLetterOrDigit() } }.orEmpty()
    }

    private fun limitRepeatedTerminators(value: String): String = buildString(value.length) {
        var previous = Char.MIN_VALUE
        var repeated = 0
        value.forEach { character ->
            if (character == previous) {
                repeated++
            } else {
                previous = character
                repeated = 1
            }
            if (repeated <= 2) append(character)
        }
    }


        val QUOTED_ELLIPSIS_REGEX = Regex("""[“”\"‘’']\s*(?:[.．｡﹒․·•・…⋯︙︰]\s*){2,}[“”\"‘’']""")
        val ELLIPSIS_REGEX = Regex("""(?:[.．｡﹒․·•・…⋯︙︰]\s*){2,}""")
        val LONG_DASH_REGEX = Regex("""[—–-]{2,}""")
        val DECORATIVE_REPEAT_REGEX = Regex("""[~～_＿=＝*＊#＃]{2,}""")
        val REPEATED_TERMINATOR_REGEX = Regex("""[。！？!?]{2,}""")
        val REPEATED_SEPARATOR_REGEX = Regex("""[，,、；;：:]{2,}""")
        val ZERO_WIDTH_REGEX = Regex("""[\u200B-\u200D\uFEFF]""")
        val WHITESPACE_REGEX = Regex("""\s+""")


}
