package com.yura.app.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.yura.app.data.Book
import com.yura.app.data.YuraDao
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.toUrl

class LibraryRepository(
    private val context: Context,
    private val dao: YuraDao,
    private val readium: ReadiumServices,
) {
    val books: Flow<List<Book>> = dao.books()

    suspend fun importPublication(uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val importedFile = copyToLibrary(uri)
                val publicationFile = importedFile.file
                val asset = readium.assetRetriever.retrieve(publicationFile.toUrl(isDirectory = false))
                    .getOrElse { error("无法识别这个文件：${it.message}") }
                val publication = readium.publicationOpener.open(
                    asset,
                    allowUserInteraction = false,
                ).getOrElse { error("无法打开图书：${it.message}") }

                try {
                    val duplicateIdentifier = publication.metadata.identifier
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: importedFile.sha256
                    val title = publication.metadata.title ?: displayName(uri) ?: publicationFile.nameWithoutExtension
                    val author = publication.metadata.authorName()
                    val duplicate = dao.bookByIdentifier(duplicateIdentifier)
                        ?: dao.bookByTitleAndAuthor(title, author)
                    duplicate?.let {
                        publicationFile.delete()
                        error("这本书已经在书架里了")
                    }

                    val now = Clock.System.now().toEpochMilliseconds()
                    val coverFile = storeCover(publication)
                    dao.insertBook(
                        Book(
                            creationDate = now,
                            lastReadDate = now,
                            href = publicationFile.toUrl(isDirectory = false).toString(),
                            title = title,
                            author = author,
                            identifier = duplicateIdentifier,
                            progression = "{}",
                            rawMediaType = asset.format.mediaType.toString(),
                            cover = coverFile.absolutePath,
                        ),
                    )
                    Unit
                } finally {
                    publication.close()
                    asset.close()
                }
            }
        }

    suspend fun deleteBook(book: Book) {
        dao.deleteBookmarksForBook(book.id)
        dao.deleteBook(book.id)
        withContext(Dispatchers.IO) {
            runCatching { book.href.toOwnedLocalFile()?.delete() }
            runCatching { book.cover.toOwnedLocalFile()?.delete() }
        }
    }

    private suspend fun copyToLibrary(uri: Uri): ImportedFile =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "books").apply { mkdirs() }
            val extension = displayName(uri)
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.takeIf { it.isNotBlank() }
                ?: "epub"
            val target = File(dir, "${UUID.randomUUID()}.$extension")
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取所选文件" }
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
            ImportedFile(target, digest.digest().toHexString())
        }

    private suspend fun storeCover(publication: Publication): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.png")
            val cover = publication.cover()
            if (cover != null) {
                FileOutputStream(file).use { output ->
                    cover.resizeToWidth(600).compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            }
            file
        }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?: uri.lastPathSegment

    private fun Bitmap.resizeToWidth(width: Int): Bitmap {
        if (this.width <= width) return this
        val height = (width.toFloat() / this.width * this.height).toInt()
        return Bitmap.createScaledBitmap(this, width, height, true)
    }

    private fun Metadata.authorName(): String =
        authors.firstOrNull()?.name.orEmpty()

    private fun String.toOwnedLocalFile(): File? {
        if (isBlank()) return null
        val file = runCatching {
            if (startsWith("file:", ignoreCase = true)) {
                File(checkNotNull(URI(this).path))
            } else {
                File(this)
            }
        }.getOrNull() ?: return null
        val appRoot = context.filesDir.canonicalFile
        val target = file.canonicalFile
        return target.takeIf { it.path.startsWith(appRoot.path) && it.exists() && it.isFile }
    }

    private data class ImportedFile(
        val file: File,
        val sha256: String,
    )

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }
}
