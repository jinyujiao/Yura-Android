package com.yura.app.notes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.yura.app.data.Book
import com.yura.app.data.DeletedReaderAnnotation
import com.yura.app.data.ReaderAnnotation
import com.yura.app.data.YuraDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookAnnotationGroup(
    val book: Book,
    val annotations: List<ReaderAnnotation>,
) {
    val noteCount: Int = annotations.count { it.type == ReaderAnnotation.TYPE_NOTE }
    val highlightCount: Int = annotations.count { it.type == ReaderAnnotation.TYPE_HIGHLIGHT }
    val correctionCount: Int = annotations.count { it.type == ReaderAnnotation.TYPE_CORRECTION }
}

data class NotesUiState(
    val groups: List<BookAnnotationGroup> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
)

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val database = YuraDatabase.get(application)
    private val dao = database.yuraDao()
    private val exporter = CorrectedEpubExporter(application)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<NotesUiState> =
        combine(dao.books(), dao.annotations(), message) { books, annotations, currentMessage ->
            val booksById = books.associateBy(Book::id)
            val groups = annotations
                .groupBy(ReaderAnnotation::bookId)
                .mapNotNull { (bookId, bookAnnotations) ->
                    val book = booksById[bookId] ?: return@mapNotNull null
                    BookAnnotationGroup(
                        book = book,
                        annotations = bookAnnotations.sortedByDescending(ReaderAnnotation::updatedAt),
                    )
                }
                .sortedByDescending { group -> group.annotations.firstOrNull()?.updatedAt ?: 0L }
            NotesUiState(groups = groups, isLoading = false, message = currentMessage)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotesUiState(),
        )

    fun updateCorrection(annotation: ReaderAnnotation, replacement: String) {
        val value = replacement.trim()
        if (annotation.type != ReaderAnnotation.TYPE_CORRECTION || value.isBlank()) return
        viewModelScope.launch {
            dao.upsertAnnotation(annotation.copy(note = value, updatedAt = System.currentTimeMillis()))
            message.value = "修订已更新"
        }
    }

    fun exportCorrectedEpub(bookId: Long, destination: Uri) {
        viewModelScope.launch {
            val book = dao.book(bookId)
            val corrections = dao.annotationsForBook(bookId)
                .filter { it.type == ReaderAnnotation.TYPE_CORRECTION }
            if (book == null) {
                message.value = "导出失败：图书不存在"
                return@launch
            }
            runCatching { exporter.export(book, corrections, destination) }
                .onSuccess { summary ->
                    message.value = if (summary.skipped == 0) {
                        "修订版 EPUB 已导出，共应用 ${summary.applied} 条修订"
                    } else {
                        "修订版已导出：成功 ${summary.applied} 条，无法定位 ${summary.skipped} 条"
                    }
                }
                .onFailure { error -> message.value = "导出失败：${error.message ?: "未知错误"}" }
        }
    }

    fun deleteAnnotation(annotation: ReaderAnnotation) {
        viewModelScope.launch {
            val book = dao.book(annotation.bookId)
            if (book == null) {
                message.value = "无法删除：图书不存在"
                return@launch
            }
            database.withTransaction {
                dao.upsertDeletedReaderAnnotation(
                    DeletedReaderAnnotation(
                        id = annotation.id,
                        bookIdentifier = book.identifier,
                        deletedAt = System.currentTimeMillis(),
                    ),
                )
                dao.deleteAnnotation(annotation.id)
            }
            message.value = when (annotation.type) {
                ReaderAnnotation.TYPE_NOTE -> "笔记已删除"
                ReaderAnnotation.TYPE_CORRECTION -> "修订已删除"
                else -> "高亮已删除"
            }
        }
    }

    fun clearMessage() {
        message.value = null
    }
}
