package com.yura.app.ui.notes

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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yura.app.data.Book
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
    Corrections("修订"),
}

@Composable
fun NotesScreen(
    state: NotesUiState,
    selectedBookId: Long?,
    onSelectBook: (Long) -> Unit,
    onBackToBooks: () -> Unit,
    onDeleteAnnotation: (ReaderAnnotation) -> Unit,
    onUpdateCorrection: (ReaderAnnotation, String) -> Unit,
    onOpenAnnotation: (ReaderAnnotation) -> Unit,
    onExportCorrectedEpub: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedGroup = state.groups.firstOrNull { it.book.id == selectedBookId }

    LaunchedEffect(state.isLoading, selectedBookId, selectedGroup) {
        if (!state.isLoading && selectedBookId != null && selectedGroup == null) onBackToBooks()
    }

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        selectedGroup != null -> BookAnnotationsDetail(
            group = selectedGroup,
            onDeleteAnnotation = onDeleteAnnotation,
            onUpdateCorrection = onUpdateCorrection,
            onOpenAnnotation = onOpenAnnotation,
            onExportCorrectedEpub = { onExportCorrectedEpub(selectedGroup.book) },
            modifier = modifier,
        )
        else -> NotesOverview(groups = state.groups, onSelectBook = onSelectBook, modifier = modifier)
    }
}

@Composable
private fun NotesOverview(
    groups: List<BookAnnotationGroup>,
    onSelectBook: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(YuraIcons.Note, contentDescription = null, modifier = Modifier.size(30.dp))
                Text("还没有笔记", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("阅读时长按文字，可以添加笔记、高亮或修订。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(groups, key = { it.book.id }) { group ->
            Surface(
                onClick = { onSelectBook(group.book.id) },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AsyncImage(
                        model = group.book.cover.takeIf(String::isNotBlank)?.let(::File),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(58.dp).height(78.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(group.book.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CountBadge("笔记 ${group.noteCount}")
                            CountBadge("高亮 ${group.highlightCount}")
                            if (group.correctionCount > 0) CountBadge("修订 ${group.correctionCount}")
                        }
                    }
                    Icon(YuraIcons.ChevronRight, contentDescription = "查看笔记")
                }
            }
        }
    }
}

@Composable
private fun BookAnnotationsDetail(
    group: BookAnnotationGroup,
    onDeleteAnnotation: (ReaderAnnotation) -> Unit,
    onUpdateCorrection: (ReaderAnnotation, String) -> Unit,
    onOpenAnnotation: (ReaderAnnotation) -> Unit,
    onExportCorrectedEpub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember(group.book.id) { mutableStateOf(AnnotationFilter.All) }
    var pendingDelete by remember { mutableStateOf<ReaderAnnotation?>(null) }
    var pendingEdit by remember { mutableStateOf<ReaderAnnotation?>(null) }
    var editDraft by remember { mutableStateOf("") }
    val visibleAnnotations = remember(group.annotations, filter) {
        when (filter) {
            AnnotationFilter.All -> group.annotations
            AnnotationFilter.Notes -> group.annotations.filter { it.type == ReaderAnnotation.TYPE_NOTE }
            AnnotationFilter.Highlights -> group.annotations.filter { it.type == ReaderAnnotation.TYPE_HIGHLIGHT }
            AnnotationFilter.Corrections -> group.annotations.filter { it.type == ReaderAnnotation.TYPE_CORRECTION }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnnotationFilter.entries.forEach { item ->
                        FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) })
                    }
                }
                if (group.correctionCount > 0) {
                    Surface(
                        onClick = onExportCorrectedEpub,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(YuraIcons.Export, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("导出修订版 EPUB", fontWeight = FontWeight.Bold)
                                Text(
                                    "应用 ${group.correctionCount} 条修订并生成新副本，不修改原书",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(YuraIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        if (visibleAnnotations.isEmpty()) {
            item {
                Text("当前分类还没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 28.dp))
            }
        } else {
            items(visibleAnnotations, key = ReaderAnnotation::id) { annotation ->
                AnnotationCard(
                    annotation = annotation,
                    onOpen = { onOpenAnnotation(annotation) },
                    onEdit = if (annotation.type == ReaderAnnotation.TYPE_CORRECTION) {
                        {
                            editDraft = annotation.note
                            pendingEdit = annotation
                        }
                    } else null,
                    onDelete = { pendingDelete = annotation },
                )
            }
        }
    }

    pendingDelete?.let { annotation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除${annotationTypeLabel(annotation)}？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDeleteAnnotation(annotation) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    pendingEdit?.let { annotation ->
        val original = annotation.locator?.text?.highlight.orEmpty().trim()
        AlertDialog(
            onDismissRequest = { pendingEdit = null },
            title = { Text("编辑修订") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f), shape = RoundedCornerShape(14.dp)) {
                        Text(original, modifier = Modifier.padding(12.dp))
                    }
                    OutlinedTextField(
                        value = editDraft,
                        onValueChange = { editDraft = it.take(500) },
                        label = { Text("修订为") },
                        minLines = 2,
                        maxLines = 7,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editDraft.isNotBlank() && editDraft.trim() != annotation.note.trim(),
                    onClick = { pendingEdit = null; onUpdateCorrection(annotation, editDraft) },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { pendingEdit = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun AnnotationCard(
    annotation: ReaderAnnotation,
    onOpen: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val isCorrection = annotation.type == ReaderAnnotation.TYPE_CORRECTION
    val original = annotation.locator?.text?.highlight.orEmpty().trim()
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (annotation.type) {
                        ReaderAnnotation.TYPE_NOTE -> YuraIcons.Note
                        ReaderAnnotation.TYPE_CORRECTION -> YuraIcons.Edit
                        else -> YuraIcons.Highlight
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(annotationTypeLabel(annotation), fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(formatAnnotationTime(annotation.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                    Icon(YuraIcons.Delete, contentDescription = "删除", modifier = Modifier.size(20.dp))
                }
            }
            Text(
                chapterLabel(annotation),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            if (original.isNotBlank()) {
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), shape = RoundedCornerShape(14.dp)) {
                    Text(original, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (annotation.type == ReaderAnnotation.TYPE_NOTE && annotation.note.isNotBlank()) {
                Text(annotation.note, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            if (isCorrection) {
                Text("修订为", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(annotation.note, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onOpen, enabled = annotation.locator != null) {
                    Icon(YuraIcons.View, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("查看原文")
                }
                if (onEdit != null) {
                    TextButton(onClick = onEdit) {
                        Icon(YuraIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("编辑")
                    }
                }
            }
        }
    }
}

@Composable
private fun CountBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f), shape = RoundedCornerShape(999.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

private fun annotationTypeLabel(annotation: ReaderAnnotation): String = when (annotation.type) {
    ReaderAnnotation.TYPE_NOTE -> "笔记"
    ReaderAnnotation.TYPE_CORRECTION -> "修订"
    else -> "高亮"
}

private fun chapterLabel(annotation: ReaderAnnotation): String {
    val number = annotation.chapterIndex.takeIf { it > 0 }?.let { "第 ${it} 章" }.orEmpty()
    val title = annotation.chapterTitle.trim()
    return listOf(number, title).filter(String::isNotBlank).joinToString(" · ")
        .ifBlank { annotation.chapterHref.substringAfterLast('/').ifBlank { "章节位置" } }
}

private fun formatAnnotationTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
