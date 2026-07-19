package com.yura.app.reader

import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

internal data class ReaderChapterInfo(
    val index: Int,
    val title: String,
    val href: String,
)

internal object ReaderChapterTitleResolver {
    fun resolve(publication: Publication, locator: Locator): String =
        resolveInfo(publication, locator).title

    fun resolveInfo(publication: Publication, locator: Locator): ReaderChapterInfo {
        val currentHref = locator.href.toString().substringBefore('#')
        val tocEntries = flatten(publication.tableOfContents)
        val tocIndex = tocEntries.indexOfFirst { link -> matches(link.href.toString(), currentHref) }
        val readingOrderIndex = publication.readingOrder.indexOfFirst { link -> matches(link.href.toString(), currentHref) }
        val title = locator.title?.trim()?.takeIf(String::isNotBlank)
            ?: tocEntries.getOrNull(tocIndex)?.title?.trim()?.takeIf(String::isNotBlank)
            ?: publication.readingOrder.getOrNull(readingOrderIndex)?.title?.trim()?.takeIf(String::isNotBlank)
            ?: ""
        return ReaderChapterInfo(
            index = when {
                tocIndex >= 0 -> tocIndex + 1
                readingOrderIndex >= 0 -> readingOrderIndex + 1
                else -> -1
            },
            title = title,
            href = currentHref,
        )
    }

    private fun flatten(links: List<Link>): List<Link> = buildList {
        links.forEach { link ->
            add(link)
            addAll(flatten(link.children))
        }
    }

    private fun matches(linkHref: String, currentHref: String): Boolean {
        val normalizedLink = normalize(linkHref)
        val normalizedCurrent = normalize(currentHref)
        return normalizedLink == normalizedCurrent ||
            normalizedLink.endsWith("/$normalizedCurrent") ||
            normalizedCurrent.endsWith("/$normalizedLink")
    }

    private fun normalize(href: String): String =
        href.substringBefore('#').substringBefore('?').replace('\\', '/').trimStart('/')
}
