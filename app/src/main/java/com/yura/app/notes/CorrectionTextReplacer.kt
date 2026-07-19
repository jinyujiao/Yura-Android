package com.yura.app.notes

import org.jsoup.Jsoup
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

internal data class CorrectionApplyResult(
    val content: String,
    val appliedIds: Set<String>,
)

internal data class TextCorrection(
    val id: String,
    val before: String,
    val original: String,
    val after: String,
    val replacement: String,
)

internal object CorrectionTextReplacer {
    fun apply(content: String, corrections: List<TextCorrection>): CorrectionApplyResult {
        if (corrections.isEmpty()) return CorrectionApplyResult(content, emptySet())
        val document = Jsoup.parse(content, "", Parser.xmlParser())
        val textNodes = mutableListOf<TextNode>()
        collectTextNodes(document, textNodes)
        if (textNodes.isEmpty()) return CorrectionApplyResult(content, emptySet())

        val fullText = buildString { textNodes.forEach { append(it.wholeText) } }
        val ranges = corrections.mapNotNull { correction ->
            if (correction.original.isBlank() || correction.replacement.isBlank()) return@mapNotNull null
            locate(fullText, correction.original, correction.before, correction.after)
                ?.let { range -> LocatedCorrection(correction.id, range.first, range.last + 1, correction.replacement) }
        }.sortedByDescending(LocatedCorrection::start)

        val applied = linkedSetOf<String>()
        ranges.forEach { correction ->
            if (replaceRange(textNodes, correction.start, correction.endExclusive, correction.replacement)) {
                applied += correction.id
            }
        }
        return CorrectionApplyResult(document.outerHtml(), applied)
    }

    private fun locate(text: String, original: String, before: String, after: String): IntRange? {
        val candidates = buildList {
            var index = text.indexOf(original)
            while (index >= 0) {
                add(index)
                index = text.indexOf(original, index + 1)
            }
        }
        if (candidates.isEmpty()) return null
        val beforeContext = before.takeLast(80)
        val afterContext = after.take(80)
        val best = candidates.maxByOrNull { index ->
            commonSuffixLength(text.substring(0, index), beforeContext) +
                commonPrefixLength(text.substring(index + original.length), afterContext)
        } ?: return null
        return best until (best + original.length)
    }

    private fun replaceRange(nodes: List<TextNode>, start: Int, endExclusive: Int, replacement: String): Boolean {
        var offset = 0
        var startNode: TextNode? = null
        var endNode: TextNode? = null
        var startOffset = 0
        var endOffset = 0
        for (node in nodes) {
            val length = node.wholeText.length
            if (startNode == null && start in offset..(offset + length)) {
                startNode = node
                startOffset = (start - offset).coerceIn(0, length)
            }
            if (endExclusive in offset..(offset + length)) {
                endNode = node
                endOffset = (endExclusive - offset).coerceIn(0, length)
                break
            }
            offset += length
        }
        val first = startNode ?: return false
        val last = endNode ?: return false
        if (first === last) {
            val value = first.wholeText
            first.text(value.substring(0, startOffset) + replacement + value.substring(endOffset))
            return true
        }
        val firstIndex = nodes.indexOf(first)
        val lastIndex = nodes.indexOf(last)
        if (firstIndex < 0 || lastIndex < firstIndex) return false
        first.text(first.wholeText.substring(0, startOffset) + replacement)
        for (index in (firstIndex + 1) until lastIndex) nodes[index].text("")
        last.text(last.wholeText.substring(endOffset))
        return true
    }

    private fun collectTextNodes(node: Node, result: MutableList<TextNode>) {
        node.childNodes().forEach { child ->
            when (child) {
                is TextNode -> result += child
                else -> collectTextNodes(child, result)
            }
        }
    }

    private fun commonSuffixLength(value: String, suffix: String): Int {
        var count = 0
        while (count < value.length && count < suffix.length && value[value.lastIndex - count] == suffix[suffix.lastIndex - count]) count++
        return count
    }

    private fun commonPrefixLength(value: String, prefix: String): Int {
        var count = 0
        while (count < value.length && count < prefix.length && value[count] == prefix[count]) count++
        return count
    }

    private data class LocatedCorrection(
        val id: String,
        val start: Int,
        val endExclusive: Int,
        val replacement: String,
    )
}
