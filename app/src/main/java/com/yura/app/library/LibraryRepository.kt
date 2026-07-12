package com.yura.app.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.os.StatFs
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
    private val deletionService = BookDeletionService(context, dao)

    val books: Flow<List<Book>> = dao.books()

    suspend fun importPublication(uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                validateImportSize(uri)
                val importedFile = copyToLibrary(uri)
                val sourceDisplayName = displayName(uri)
                var txtAuthor = ""
                val publicationFile = if (isTxtPublication(uri, importedFile.file, sourceDisplayName)) {
                    val converted = TxtEpubConverter.convert(
                        txtFile = importedFile.file,
                        outputDir = File(context.filesDir, "books"),
                        title = sourceDisplayName?.substringBeforeLast('.') ?: importedFile.file.nameWithoutExtension,
                        identifier = "yura-txt-${importedFile.sha256}",
                    )
                    txtAuthor = converted.author
                    importedFile.file.delete()
                    converted.file
                } else {
                    importedFile.file
                }
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
                    val title = publication.metadata.title ?: sourceDisplayName ?: publicationFile.nameWithoutExtension
                    val author = txtAuthor.ifBlank { publication.metadata.authorName() }
                    val duplicateByIdentifier = dao.bookByIdentifier(duplicateIdentifier)
                    val duplicateByMetadata = if (duplicateByIdentifier == null) {
                        dao.bookByTitleAndAuthor(title, author)
                    } else {
                        null
                    }
                    if (BookImportRules.isDuplicate(
                            hasMatchingIdentifier = duplicateByIdentifier != null,
                            hasMatchingTitleAndAuthor = duplicateByMetadata != null,
                        )
                    ) {
                        publicationFile.delete()
                        error("这本书已经在书架里了")
                    }

                    val now = Clock.System.now().toEpochMilliseconds()
                    val coverFile = storeCover(publication)
                    dao.clearDeletedBook(duplicateIdentifier)
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

    suspend fun removeLocalBook(book: Book) = deletionService.removeLocal(book)

    suspend fun deleteBookEverywhere(book: Book) = deletionService.deleteEverywhere(book)

    suspend fun changeCover(book: Book, uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val coverFile = copyCoverToLibrary(uri)
                dao.updateBookCover(book.id, coverFile.absolutePath)
                runCatching { book.cover.toOwnedLocalFile()?.delete() }
                Unit
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
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "无法读取所选文件。" }
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copiedBytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            copiedBytes += read
                            require(copiedBytes <= MAX_IMPORT_BYTES) { "文件超过 512 MB 导入上限。" }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                }
                ImportedFile(target, digest.digest().toHexString())
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
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

    private fun copyCoverToLibrary(uri: Uri): File {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val extension = displayName(uri)
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it in setOf("jpg", "jpeg", "png", "webp") }
            ?: "jpg"
        val file = File(dir, "${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取所选封面" }
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    private fun validateImportSize(uri: Uri) {
        val declaredSize = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1L }
            ?: -1L
        require(declaredSize <= MAX_IMPORT_BYTES || declaredSize < 0) { "文件超过 512 MB 导入上限。" }
        val availableBytes = StatFs(context.filesDir.path).availableBytes
        val requiredBytes = (declaredSize.takeIf { it > 0 } ?: MIN_FREE_SPACE_BYTES) + MIN_FREE_SPACE_BYTES
        require(availableBytes >= requiredBytes) { "设备可用空间不足，请至少保留 128 MB 后重试。" }
    }
    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?: uri.lastPathSegment

    private fun isTxtPublication(uri: Uri, file: File, displayName: String?): Boolean {
        val extension = (displayName ?: file.name)
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
        val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        return extension == "txt" ||
            mimeType == "text/plain" ||
            mimeType == "application/txt" ||
            mimeType == "text/*"
    }

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

    private companion object {
        const val MAX_IMPORT_BYTES = 512L * 1024 * 1024
        const val MIN_FREE_SPACE_BYTES = 128L * 1024 * 1024
    }

    private data class ImportedFile(
        val file: File,
        val sha256: String,
    )

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }
}
