package com.yura.app.ui.shelf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yura.app.data.Book
import com.yura.app.library.LibraryUiState
import java.io.File
import org.json.JSONObject

private enum class ShelfDeleteAction {
    RemoveFromDevice,
    DeleteEverywhere,
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    query: String,
    sort: ShelfSort,
    onOpenReader: (Book) -> Unit,
    onRemoveFromDevice: (List<Book>) -> Unit,
    onDeleteEverywhere: (List<Book>) -> Unit,
    onChangeCover: (Book) -> Unit,
) {
    var selectedBookIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var deleteAction by remember { mutableStateOf<ShelfDeleteAction?>(null) }
    val selectedBooks = remember(state.books, selectedBookIds) { state.books.filter { it.id in selectedBookIds } }
    val sortedBooks = remember(state.books, query, sort) {
        ShelfBookFilter.filterAndSort(state.books, query, sort)
    }
    val gridState = rememberLazyGridState()
    var previousSort by remember { mutableStateOf(sort) }

    LaunchedEffect(sort) {
        if (sort != previousSort) {
            previousSort = sort
            gridState.scrollToItem(0)
        }
    }

    BackHandler(enabled = selectedBookIds.isNotEmpty()) { selectedBookIds = emptySet() }

    deleteAction?.let { action ->
        val removeFromDevice = action == ShelfDeleteAction.RemoveFromDevice
        AlertDialog(
            onDismissRequest = { deleteAction = null },
            title = { Text(if (removeFromDevice) "移除本机书籍" else "删除所有设备上的书") },
            text = {
                Text(if (removeFromDevice) "确定从本机移除 ${selectedBooks.size} 本书吗？远端和其他设备上的副本会保留。" else "确定从所有设备删除 ${selectedBooks.size} 本书吗？下次同步会删除远端副本。")
            },
            confirmButton = {
                TextButton(onClick = {
                    if (removeFromDevice) onRemoveFromDevice(selectedBooks) else onDeleteEverywhere(selectedBooks)
                    selectedBookIds = emptySet()
                    deleteAction = null
                }) { Text(if (removeFromDevice) "移除本机" else "删除") }
            },
            dismissButton = { TextButton(onClick = { deleteAction = null }) { Text("取消") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedBooks.isNotEmpty()) {
            ShelfSelectionBar(
                selectedCount = selectedBooks.size,
                canChangeCover = selectedBooks.size == 1,
                onChangeCover = {
                    selectedBooks.singleOrNull()?.let(onChangeCover)
                    selectedBookIds = emptySet()
                },
                onRemoveFromDevice = { deleteAction = ShelfDeleteAction.RemoveFromDevice },
                onDeleteEverywhere = { deleteAction = ShelfDeleteAction.DeleteEverywhere },
                onCancel = { selectedBookIds = emptySet() },
                modifier = Modifier.padding(start = 26.dp, top = 10.dp, end = 26.dp),
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val shelfColumns = if (maxWidth >= 840.dp) 4 else if (maxWidth >= 600.dp) 3 else 2
            LazyVerticalGrid(
                columns = GridCells.Fixed(shelfColumns),
                state = gridState,
            contentPadding = PaddingValues(start = 26.dp, top = 18.dp, end = 26.dp, bottom = 116.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            if (state.isImporting) item(span = { GridItemSpan(maxLineSpan) }) { ImportStatusBanner() }
            if (state.isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            } else if (state.books.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyLibraryCard() }
            } else if (sortedBooks.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.height(150.dp)) {
                        Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("没有符合条件的书籍", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            items(sortedBooks, key = { it.id }) { book ->
                ShelfBookCard(
                    book = book,
                    selected = book.id in selectedBookIds,
                    onClick = {
                        if (selectedBookIds.isEmpty()) onOpenReader(book) else selectedBookIds = selectedBookIds.toggle(book.id)
                    },
                    onLongClick = { selectedBookIds = selectedBookIds.toggle(book.id) },
                )
            }
            }
        }
    }
}

@Composable
private fun ShelfSelectionBar(
    selectedCount: Int,
    canChangeCover: Boolean,
    onChangeCover: () -> Unit,
    onRemoveFromDevice: () -> Unit,
    onDeleteEverywhere: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("已选择 $selectedCount 本书", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Surface(
                onClick = onCancel,
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.76f),
            ) {
                Text(
                    "取消选择",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShelfActionButton(text = "移除本机", onClick = onRemoveFromDevice)
            ShelfActionButton(text = "全设备删除", onClick = onDeleteEverywhere)
            ShelfActionButton(text = "更换封面", onClick = onChangeCover, enabled = canChangeCover)
        }
    }
}

@Composable
private fun ShelfActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.76f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyLibraryCard() {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.height(180.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("书架还是空的", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("点击右上角 + 导入本地 EPUB。", color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

private fun Set<Long>.toggle(bookId: Long): Set<Long> = if (bookId in this) this - bookId else this + bookId

@Composable
private fun ImportStatusBanner() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "\u6b63\u5728\u5bfc\u5165 EPUB...",
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfBookCard(
    book: Book,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val progress = remember(book.progression) { bookProgressLabel(book.progression) }
    val progressFraction = remember(book.progression) { bookProgressFraction(book.progression) }

    Column(
        modifier = Modifier.fillMaxWidth().graphicsLayer { clip = false; shadowElevation = 0f }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
                border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.68f),
            ) {
                AsyncImage(
                    model = File(book.cover),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                )
            }
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp),
                ) { Box(contentAlignment = Alignment.Center) { Text("✓", fontWeight = FontWeight.Black) } }
            }
        }
        Spacer(Modifier.height(11.dp))
        Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(book.author.ifBlank { "未知作者" }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(progress, maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))) {
            Box(modifier = Modifier.fillMaxWidth(progressFraction).height(3.dp).clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)))
        }
    }
}

private fun bookProgressLabel(progression: String): String {
    val percent = bookProgressFraction(progression).toDouble()
    return "${(percent * 100).toInt()}%"
}

private fun bookProgressFraction(progression: String): Float =
    runCatching {
        JSONObject(progression)
            .optJSONObject("locations")
            ?.optDouble("totalProgression", 0.0)
            ?: 0.0
    }.getOrDefault(0.0).coerceIn(0.0, 1.0).toFloat()
