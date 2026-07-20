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
            .map(::clean)
            .filter { it.length >= 2 }
            .ifEmpty { listOf(clean(fallbackText)).filter { it.length >= 2 } }
    }

    private fun readableText(element: Element): String {
        val readableDescendants = element.getAllElements()
            .asSequence()
            .drop(1)
            .filter { descendant -> descendant.normalName() in readableTags }
            .toList()
        if (readableDescendants.isEmpty()) return clean(element.text())

        val clone = element.clone()
        clone.getAllElements()
            .asSequence()
            .drop(1)
            .filter { descendant -> descendant.normalName() in readableTags }
            .toList()
            .asReversed()
            .forEach(Element::remove)
        return clean(clone.text())
    }

    fun clean(text: String): String {
        val cleaned = text
            .replace(Regex("[“”\"‘’']\\s*(?:[.．｡﹒․·•・…⋯︙︰]\\s*){2,}[“”\"‘’']"), " ")
            .replace(Regex("(?:[.．｡﹒․·•・…⋯︙︰]\\s*){2,}"), " ")
            .replace(Regex("[—–-]{2,}"), " ")
            .replace(Regex("[~～_＿=＝*＊#＃]{2,}"), " ")
            .replace(Regex("[“”\"‘’']\\s+[“”\"‘’']"), " ")
            .replace(Regex("([。！？!?，,、；;：:])\\1+"), "$1")
            .replace(Regex("[\u200B-\u200D\uFEFF]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.takeUnless { value -> value.isBlank() || value.none { it.isLetterOrDigit() } }.orEmpty()
    }
}
