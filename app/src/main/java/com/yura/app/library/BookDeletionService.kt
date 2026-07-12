package com.yura.app.library

import android.content.Context
import com.yura.app.data.Book
import com.yura.app.data.DeletedBook
import com.yura.app.data.YuraDao
import java.io.File
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BookDeletionService(
    context: Context,
    private val dao: YuraDao,
) {
    private val appFilesDir = context.applicationContext.filesDir

    suspend fun removeLocal(book: Book) {
        dao.deleteBookmarksForBook(book.id)
        dao.deleteBook(book.id)
        withContext(Dispatchers.IO) {
            runCatching {
                LocalBookFileCleaner.deleteOwnedFiles(
                    book.href.toOwnedLocalFile(),
                    book.cover.toOwnedLocalFile(),
                )
            }
        }
    }

    suspend fun deleteEverywhere(book: Book) {
        dao.upsertDeletedBook(DeletedBook(book.identifier, System.currentTimeMillis()))
        removeLocal(book)
    }

    private fun String.toOwnedLocalFile(): File? {
        if (isBlank()) return null
        val file = runCatching {
            if (startsWith("file:", ignoreCase = true)) File(checkNotNull(URI(this).path)) else File(this)
        }.getOrNull() ?: return null
        val appRoot = appFilesDir.canonicalFile
        val target = file.canonicalFile
        return target.takeIf { it.path.startsWith(appRoot.path) && it.exists() && it.isFile }
    }
}
