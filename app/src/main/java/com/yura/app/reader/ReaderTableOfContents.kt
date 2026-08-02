package com.yura.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yura.app.ui.icons.YuraIcons
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

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
    val allEntries = remember(links) { flattenToc(links) }
    val activeEntry = remember(allEntries, currentLocator) {
        findActiveTocEntry(allEntries, publication, currentLocator)
    }
    var expandedPaths by remember(links) {
        mutableStateOf(activeEntry?.ancestors.orEmpty().toSet())
    }
    val entries = remember(allEntries, expandedPaths) {
        visibleTocEntries(allEntries, expandedPaths)
    }
    val activeIndex = remember(entries, activeEntry) {
        entries.indexOfFirst { it.path == activeEntry?.path }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(activeEntry?.path) {
        expandedPaths = expandedPaths + activeEntry?.ancestors.orEmpty()
    }

    LaunchedEffect(activeEntry?.path) {
        if (activeIndex >= 0) listState.scrollToItem(activeIndex)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = com.yura.app.ui.theme.YuraBottomSheetShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            com.yura.app.ui.components.YuraBottomSheetTitle(
                title = "\u76ee\u5f55",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            if (entries.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.large,
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
                    itemsIndexed(entries, key = { _, entry -> entry.path }) { index, entry ->
                        TocRow(
                            link = entry.link,
                            depth = entry.depth,
                            selected = index == activeIndex,
                            hasChildren = entry.hasChildren,
                            expanded = entry.path in expandedPaths,
                            onToggleExpanded = {
                                expandedPaths = if (entry.path in expandedPaths) {
                                    expandedPaths - entry.path
                                } else {
                                    expandedPaths + entry.path
                                }
                            },
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
    hasChildren: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onGo: (Link) -> Unit,
) {
    TextButton(
        onClick = { onGo(link) },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                } else if (depth == 0) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                shape = MaterialTheme.shapes.medium,
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = link.title ?: link.href.toString(),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = (depth * 18).dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (hasChildren) {
                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.padding(end = 2.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) YuraIcons.ExpandLess else YuraIcons.ExpandMore,
                        contentDescription = if (expanded) "收起子目录" else "展开子目录",
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class TocEntry(
    val link: Link,
    val depth: Int,
    val path: String,
    val parentPath: String?,
    val hasChildren: Boolean,
) {
    val ancestors: List<String>
        get() {
            val parts = path.split('/')
            return parts.dropLast(1).indices.map { index -> parts.take(index + 1).joinToString("/") }
        }
}

private fun flattenToc(links: List<Link>): List<TocEntry> {
    val result = mutableListOf<TocEntry>()
    fun append(items: List<Link>, depth: Int, parentPath: String?) {
        items.forEachIndexed { index, link ->
            val path = if (parentPath == null) index.toString() else "$parentPath/$index"
            result += TocEntry(
                link = link,
                depth = depth,
                path = path,
                parentPath = parentPath,
                hasChildren = link.children.isNotEmpty(),
            )
            append(link.children, depth + 1, path)
        }
    }
    append(links, 0, null)
    return result
}

private fun visibleTocEntries(entries: List<TocEntry>, expandedPaths: Set<String>): List<TocEntry> =
    entries.filter { entry ->
        entry.parentPath == null || entry.ancestors.all { it in expandedPaths }
    }

private fun findActiveTocEntry(
    entries: List<TocEntry>,
    publication: Publication,
    currentLocator: Locator?,
): TocEntry? = entries.getOrNull(findActiveTocIndex(entries, publication, currentLocator))

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
