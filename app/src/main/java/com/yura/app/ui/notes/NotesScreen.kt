package com.yura.app.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yura.app.data.ReaderAnnotation
import com.yura.app.notes.BookAnnotationGroup
import com.yura.app.notes.NotesUiState
import com.yura.app.ui.icons.YuraIcons
import java.io.File
import java.text.DateFormat
import java.util.Date

enum class AnnotationFilter(val label: String) {
    All("全部"),
    Notes("笔记"),
    Highlights("高亮"),
}

@Composable
fun NotesScreen(
    state: NotesUiState,
    selectedBookId: Long?,
    onSelectBook: (Long) -> Unit,
    onBackToBooks: () -> Unit,
    onDeleteAnnotation: (ReaderAnnotation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedGroup = state.groups.firstOrNull { it.book.id == selectedBookId }

    LaunchedEffect(state.isLoading, selectedBookId, selectedGroup) {
        if (!state.isLoading && selectedBookId != null && selectedGroup == null) {
            onBackToBooks()
        }
    }

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        selectedGroup != null -> BookAnnotationsDetail(
            group = selectedGroup,
            onDeleteAnnotation = onDeleteAnnotation,
            modifier = modifier,
        )
        else -> NotesOverview(
            groups = state.groups,
            onSelectBook = onSelectBook,
            modifier = modifier,
        )
    }
}

@Composable
private fun NotesOverview(
    groups: List<BookAnnotationGroup>,
    onSelectBook: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(YuraIcons.Note, contentDescription = null, modifier = Modifier.size(30.dp))
                    Text("还没有笔记", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("在阅读时长按文字，即可添加笔记或高亮。")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "共 ${groups.sumOf { it.annotations.size }} 条内容，按图书整理",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(groups, key = { it.book.id }) { group ->
            BookNotesCard(group = group, onClick = { onSelectBook(group.book.id) })
        }
    }
}

@Composable
private fun BookNotesCard(
    group: BookAnnotationGroup,
    onClick: () -> Unit,
) {
    val latest = group.annotations.firstOrNull()
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(width = 58.dp, height = 82.dp),
            ) {
                if (group.book.cover.isNotBlank()) {
                    AsyncImage(
                        model = File(group.book.cover),
                        contentDescription = group.book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = group.book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (group.book.author.isNotBlank()) {
                    Text(
                        text = group.book.author,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountBadge("笔记 ${group.noteCount}", MaterialTheme.colorScheme.tertiaryContainer)
                    CountBadge("高亮 ${group.highlightCount}", MaterialTheme.colorScheme.secondaryContainer)
                }
                latest?.let { annotation ->
                    Text(
                        text = annotationPreview(annotation),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(YuraIcons.ChevronRight, contentDescription = "查看《${group.book.title}》的笔记")
        }
    }
}

@Composable
private fun CountBadge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.72f), shape = RoundedCornerShape(999.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BookAnnotationsDetail(
    group: BookAnnotationGroup,
    onDeleteAnnotation: (ReaderAnnotation) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember(group.book.id) { mutableStateOf(AnnotationFilter.All) }
    var pendingDelete by remember { mutableStateOf<ReaderAnnotation?>(null) }
    val visibleAnnotations = remember(group.annotations, filter) {
        when (filter) {
            AnnotationFilter.All -> group.annotations
            AnnotationFilter.Notes -> group.annotations.filter { it.type == ReaderAnnotation.TYPE_NOTE }
            AnnotationFilter.Highlights -> group.annotations.filter { it.type == ReaderAnnotation.TYPE_HIGHLIGHT }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnnotationFilter.entries.forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text(item.label) },
                    )
                }
            }
        }
        if (visibleAnnotations.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "这个分类下暂无内容",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(visibleAnnotations, key = ReaderAnnotation::id) { annotation ->
                AnnotationCard(annotation = annotation, onDelete = { pendingDelete = annotation })
            }
        }
    }

    pendingDelete?.let { annotation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(if (annotation.type == ReaderAnnotation.TYPE_NOTE) "删除笔记？" else "删除高亮？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteAnnotation(annotation)
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AnnotationCard(
    annotation: ReaderAnnotation,
    onDelete: () -> Unit,
) {
    val isNote = annotation.type == ReaderAnnotation.TYPE_NOTE
    val accent = if (isNote) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
    val container = if (isNote) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.34f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
    }
    val selectedText = annotation.locator?.text?.highlight.orEmpty().trim()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isNote) YuraIcons.Note else YuraIcons.Highlight,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = if (isNote) "笔记" else "高亮",
                        color = accent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = remember(annotation.createdAt) { formatAnnotationTime(annotation.createdAt) },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                        Icon(YuraIcons.Delete, contentDescription = "删除", modifier = Modifier.size(20.dp))
                    }
                }
                if (selectedText.isNotBlank()) {
                    Surface(color = container, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = selectedText,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (isNote && annotation.note.isNotBlank()) {
                    Text(
                        text = annotation.note,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private fun annotationPreview(annotation: ReaderAnnotation): String {
    val selectedText = annotation.locator?.text?.highlight.orEmpty().trim()
    return if (annotation.type == ReaderAnnotation.TYPE_NOTE && annotation.note.isNotBlank()) {
        annotation.note
    } else {
        selectedText.ifBlank { "已保存一条高亮" }
    }
}

private fun formatAnnotationTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
