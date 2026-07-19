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
    val importCompleted: Int = 0,
    val importTotal: Int = 0,
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
        importPublications(listOf(uri), singleImport = true)
    }

    fun importPublications(uris: List<Uri>) {
        importPublications(uris, singleImport = false)
    }

    private fun importPublications(uris: List<Uri>, singleImport: Boolean) {
        val uniqueUris = uris.distinctBy(Uri::toString)
        if (uniqueUris.isEmpty()) return
        if (transientState.value.isImporting) {
            transientState.value = transientState.value.copy(message = "已有导入任务正在进行")
            return
        }
        viewModelScope.launch {
            transientState.value = transientState.value.copy(
                isImporting = true,
                importCompleted = 0,
                importTotal = uniqueUris.size,
                message = null,
            )
            var succeeded = 0
            var firstError: Throwable? = null
            uniqueUris.forEachIndexed { index, uri ->
                repository.importPublication(uri)
                    .onSuccess { succeeded++ }
                    .onFailure { error -> if (firstError == null) firstError = error }
                transientState.value = transientState.value.copy(importCompleted = index + 1)
            }
            val failed = uniqueUris.size - succeeded
            transientState.value = transientState.value.copy(
                isImporting = false,
                message = importResultMessage(
                    total = uniqueUris.size,
                    succeeded = succeeded,
                    failed = failed,
                    firstError = firstError,
                    singleImport = singleImport,
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

internal fun importResultMessage(
    total: Int,
    succeeded: Int,
    failed: Int,
    firstError: Throwable?,
    singleImport: Boolean,
): String = when {
    singleImport && succeeded == 1 -> "导入完成"
    singleImport -> firstError?.message ?: "导入失败"
    failed == 0 -> "已导入 $succeeded 本图书"
    succeeded == 0 -> "批量导入失败：$total 个文件均未导入"
    else -> "批量导入完成：成功 $succeeded 本，失败 $failed 本"
}
