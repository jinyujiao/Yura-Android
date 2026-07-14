package com.yura.app.notes

import android.app.Application
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
}

data class NotesUiState(
    val groups: List<BookAnnotationGroup> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
)

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val database = YuraDatabase.get(application)
    private val dao = database.yuraDao()
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
                        annotations = bookAnnotations.sortedByDescending(ReaderAnnotation::createdAt),
                    )
                }
                .sortedByDescending { group -> group.annotations.firstOrNull()?.createdAt ?: 0L }
            NotesUiState(groups = groups, isLoading = false, message = currentMessage)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotesUiState(),
        )

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
            message.value = if (annotation.type == ReaderAnnotation.TYPE_NOTE) "笔记已删除" else "高亮已删除"
        }
    }

    fun clearMessage() {
        message.value = null
    }
}
