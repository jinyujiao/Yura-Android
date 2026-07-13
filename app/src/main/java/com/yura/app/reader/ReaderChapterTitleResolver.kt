package com.yura.app.reader

import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

internal object ReaderChapterTitleResolver {
    fun resolve(publication: Publication, locator: Locator): String {
        locator.title?.trim()?.takeIf(String::isNotBlank)?.let { return it }
        val currentHref = locator.href.toString().substringBefore('#')
        flatten(publication.tableOfContents)
            .firstOrNull { link -> matches(link.href.toString(), currentHref) }
            ?.title
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
        publication.readingOrder
            .firstOrNull { link -> matches(link.href.toString(), currentHref) }
            ?.title
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
        return ""
    }

    private fun flatten(links: List<Link>): List<Link> = buildList {
        links.forEach { link ->
            add(link)
            addAll(flatten(link.children))
        }
    }

    private fun matches(linkHref: String, currentHref: String): Boolean {
        val normalizedLink = linkHref.substringBefore('#')
        return normalizedLink == currentHref ||
            normalizedLink.startsWith(currentHref) ||
            currentHref.startsWith(normalizedLink)
    }
}
