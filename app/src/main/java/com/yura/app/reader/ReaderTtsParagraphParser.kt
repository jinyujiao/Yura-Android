package com.yura.app.reader

import org.jsoup.Jsoup

internal object ReaderTtsParagraphParser {
    fun parse(html: String, baseUri: String = ""): List<String> {
        if (html.isBlank()) return emptyList()
        val document = Jsoup.parse(html, baseUri)
        document.select("script, style, nav[epub|type=toc], nav, aside, audio, video").remove()
        val paragraphs = document.select("p, h1, h2, h3, h4, h5, h6, li, blockquote")
            .map { clean(it.text()) }
            .filter { it.length >= 2 }
        if (paragraphs.isNotEmpty()) return paragraphs

        val fallbackText = document.body()?.text()?.takeIf(String::isNotBlank)
            ?: html.replace(Regex("<[^>]+>"), " ")
        return fallbackText
            .split(Regex("(?<=[。！？!?])\\s*|\\n+"))
            .map(::clean)
            .filter { it.length >= 2 }
            .ifEmpty { listOf(clean(fallbackText)).filter { it.length >= 2 } }
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
