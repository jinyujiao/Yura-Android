package com.yura.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

@Composable
fun TocPane(links: List<Link>, publication: Publication, currentLocator: Locator?, onGo: (Link) -> Unit, modifier: Modifier = Modifier) {
    val entries = remember(links) { flattenToc(links) }
    val activeIndex = remember(entries, currentLocator) { findActiveTocIndex(entries, publication, currentLocator) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = activeIndex.coerceAtLeast(0))
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("目录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            if (entries.isEmpty()) {
                Text("这本书没有提供目录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(entries, key = { _, entry -> "${entry.depth}-${entry.link.href}" }) { index, entry ->
                        TocRow(link = entry.link, depth = entry.depth, selected = index == activeIndex, onGo = onGo)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocSheet(
    links: List<Link>,
    publication: Publication,
    currentLocator: Locator?,
    onDismiss: () -> Unit,
    onGo: (Link) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries = remember(links) { flattenToc(links) }
    val activeIndex = remember(entries, currentLocator) {
        findActiveTocIndex(entries, publication, currentLocator)
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = activeIndex.coerceAtLeast(0),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "\u76ee\u5f55",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            if (entries.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "\u8fd9\u672c\u4e66\u6ca1\u6709\u63d0\u4f9b\u76ee\u5f55\u3002",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(entries, key = { _, entry -> "${entry.depth}-${entry.link.href}" }) { index, entry ->
                        TocRow(
                            link = entry.link,
                            depth = entry.depth,
                            selected = index == activeIndex,
                            onGo = onGo,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TocRow(
    link: Link,
    depth: Int,
    selected: Boolean,
    onGo: (Link) -> Unit,
) {
    TextButton(
        onClick = { onGo(link) },
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else if (depth == 0) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
                },
                shape = RoundedCornerShape(18.dp),
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(
            text = link.title ?: link.href.toString(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 18).dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private data class TocEntry(
    val link: Link,
    val depth: Int,
)

private fun flattenToc(links: List<Link>): List<TocEntry> {
    val result = mutableListOf<TocEntry>()
    fun append(items: List<Link>, depth: Int) {
        items.forEach { link ->
            result += TocEntry(link, depth)
            append(link.children, depth + 1)
        }
    }
    append(links, 0)
    return result
}

private fun findActiveTocIndex(
    entries: List<TocEntry>,
    publication: Publication,
    currentLocator: Locator?,
): Int {
    if (entries.isEmpty() || currentLocator == null) return -1
    val currentHref = currentLocator.href.toString().substringBefore('#')
    val currentProgression = currentLocator.locations.totalProgression ?: -1.0

    val hrefMatch = entries.indexOfLast { entry ->
        val entryHref = entry.link.href.toString().substringBefore('#')
        currentHref == entryHref || currentHref.startsWith(entryHref) || entryHref.startsWith(currentHref)
    }
    if (hrefMatch >= 0) return hrefMatch

    val currentReadingOrderIndex = publication.readingOrder.indexOfFirst { link ->
        val linkHref = link.href.toString().substringBefore('#')
        currentHref == linkHref || currentHref.startsWith(linkHref) || linkHref.startsWith(currentHref)
    }
    if (currentReadingOrderIndex >= 0) {
        var bestIndex = -1
        var bestReadingOrderIndex = -1
        entries.forEachIndexed { index, entry ->
            val entryHref = entry.link.href.toString().substringBefore('#')
            val entryReadingOrderIndex = publication.readingOrder.indexOfFirst { link ->
                val linkHref = link.href.toString().substringBefore('#')
                entryHref == linkHref || entryHref.startsWith(linkHref) || linkHref.startsWith(entryHref)
            }
            if (entryReadingOrderIndex in 0..currentReadingOrderIndex && entryReadingOrderIndex >= bestReadingOrderIndex) {
                bestReadingOrderIndex = entryReadingOrderIndex
                bestIndex = index
            }
        }
        if (bestIndex >= 0) return bestIndex
    }

    if (currentProgression < 0.0) return -1
    var bestIndex = -1
    var bestProgression = -1.0
    entries.forEachIndexed { index, entry ->
        val progression = publication.locatorFromLink(entry.link)?.locations?.totalProgression ?: return@forEachIndexed
        if (progression <= currentProgression && progression >= bestProgression) {
            bestProgression = progression
            bestIndex = index
        }
    }
    return bestIndex
}
