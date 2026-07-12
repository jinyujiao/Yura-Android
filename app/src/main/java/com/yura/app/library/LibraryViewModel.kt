package com.yura.app.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yura.app.data.Book
import com.yura.app.data.YuraDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val message: String? = null,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LibraryRepository(
        context = application,
        dao = YuraDatabase.get(application).yuraDao(),
        readium = ReadiumServices(application),
    )
    private val transientState = MutableStateFlow(LibraryUiState())

    val uiState: StateFlow<LibraryUiState> =
        combine(repository.books, transientState) { books, transient ->
            transient.copy(books = books, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(),
        )

    fun importPublication(uri: Uri) {
        viewModelScope.launch {
            transientState.value = transientState.value.copy(isImporting = true, message = null)
            val result = repository.importPublication(uri)
            transientState.value = transientState.value.copy(
                isImporting = false,
                message = result.fold(
                    onSuccess = { "导入完成" },
                    onFailure = { it.message ?: "导入失败" },
                ),
            )
        }
    }

    fun removeLocalBook(book: Book) {
        viewModelScope.launch {
            repository.removeLocalBook(book)
            transientState.value = transientState.value.copy(message = "已从本机移除《${book.title}》")
        }
    }

    fun deleteBookEverywhere(book: Book) {
        viewModelScope.launch {
            repository.deleteBookEverywhere(book)
            transientState.value = transientState.value.copy(message = "已标记《${book.title}》为全设备删除")
        }
    }

    fun deleteBook(book: Book) = removeLocalBook(book)

    fun changeCover(book: Book, uri: Uri) {
        viewModelScope.launch {
            val result = repository.changeCover(book, uri)
            transientState.value = transientState.value.copy(
                message = result.fold(
                    onSuccess = { "封面已更新" },
                    onFailure = { it.message ?: "封面更新失败" },
                ),
            )
        }
    }

    fun clearMessage() {
        transientState.value = transientState.value.copy(message = null)
    }
}
