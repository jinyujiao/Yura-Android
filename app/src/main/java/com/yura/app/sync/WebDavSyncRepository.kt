package com.yura.app.sync

import android.content.Context
import com.yura.app.data.Book
import com.yura.app.data.DeletedBook
import com.yura.app.data.DeletedReaderAnnotation
import com.yura.app.data.ReaderAnnotation
import com.yura.app.data.YuraDatabase
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

data class WebDavSyncResult(
    val uploadedBooks: Int,
    val downloadedBooks: Int,
    val mergedProgress: Int,
    val mergedAnnotations: Int,
)

class WebDavSyncRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = YuraDatabase.get(appContext).yuraDao()
    private val client = WebDavClient()

    suspend fun sync(): Result<WebDavSyncResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val settings = WebDavSettingsStore.load(appContext)
                require(settings.enabled) { "请先启用 WebDAV 同步。" }

                client.ensureRemoteDirectory(settings).getOrThrow()
                val remoteText = client.getText(settings, SYNC_FILE).getOrThrow()
                val remote = remoteText.content
                    ?.let { SyncSnapshot.fromJson(JSONObject(it)) }
                    ?: SyncSnapshot(emptyList())

                val mergeResult = mergeRemote(remote)
                val downloadedBooks = downloadMissingBooks(settings, remote)
                val mergedAnnotations = mergeRemoteAnnotations(remote)
                val local = buildSnapshot()
                val uploadedBooks = uploadLocalBooks(settings, local, remote)
                client.putTextIfUnchanged(
                    settings = settings,
                    fileName = SYNC_FILE,
                    content = local.toJson().toString(),
                    expectedETag = remoteText.eTag,
                    remoteExists = remoteText.content != null,
                ).getOrThrow()

                WebDavSyncResult(
                    uploadedBooks = uploadedBooks,
                    downloadedBooks = downloadedBooks,
                    mergedProgress = mergeResult.progressCount,
                    mergedAnnotations = mergedAnnotations,
                )
            }
        }

    private suspend fun downloadMissingBooks(settings: WebDavSettings, snapshot: SyncSnapshot): Int {
        val localIdentifiers = dao.allBooks().map { it.identifier }.toSet()
        val deletedIdentifiers = dao.deletedBooks().map { it.identifier }.toSet() + snapshot.deletedBooks.map { it.identifier }
        var downloaded = 0

        snapshot.books.forEach { remote ->
            if (remote.identifier.isBlank() || remote.identifier in localIdentifiers || remote.identifier in deletedIdentifiers) return@forEach
            val fileName = remote.fileName ?: return@forEach
            val bookFile = File(appContext.filesDir, "books/${uniqueLocalFileName(fileName)}")
            val hasBook = client.getFile(settings, fileName, bookFile).getOrThrow()
            if (!hasBook) return@forEach
            if (remote.fileHash.isNotBlank() && bookFile.sha256() != remote.fileHash) {
                bookFile.delete()
                error("远端图书文件校验失败，请重新同步。")
            }

            val coverPath = remote.coverFileName?.let { coverName ->
                val coverFile = File(appContext.filesDir, "covers/${uniqueLocalFileName(coverName)}")
                if (client.getFile(settings, coverName, coverFile).getOrThrow()) {
                    if (remote.coverHash.isNotBlank() && coverFile.sha256() != remote.coverHash) {
                        coverFile.delete()
                        error("远端封面文件校验失败，请重新同步。")
                    }
                    coverFile.absolutePath
                } else {
                    ""
                }
            }.orEmpty()

            dao.insertBook(
                Book(
                    creationDate = remote.creationDate.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    lastReadDate = remote.lastReadDate,
                    href = bookFile.toURI().toString(),
                    title = remote.title.ifBlank { bookFile.nameWithoutExtension },
                    author = remote.author,
                    identifier = remote.identifier,
                    progression = remote.progression,
                    rawMediaType = remote.mediaType.ifBlank { MediaType.EPUB.toString() },
                    cover = coverPath,
                ),
            )
            downloaded++
        }

        return downloaded
    }

    private suspend fun uploadLocalBooks(
        settings: WebDavSettings,
        local: SyncSnapshot,
        remote: SyncSnapshot,
    ): Int {
        val remoteBooks = remote.books.associateBy { it.identifier }
        var uploaded = 0

        local.books.forEach { book ->
            val source = book.localPath
                ?.let(::File)
                ?.takeIf { it.exists() && it.isFile }
                ?: return@forEach
            val fileName = book.fileName ?: return@forEach
            val remoteBook = remoteBooks[book.identifier]
            if (book.fileHash.isNotBlank() && book.fileHash != remoteBook?.fileHash) {
                client.putFile(settings, fileName, source).getOrThrow()
                uploaded++
            }

            val cover = book.localCoverPath
                ?.let(::File)
                ?.takeIf { it.exists() && it.isFile }
            val coverFileName = book.coverFileName
            if (cover != null && coverFileName != null && book.coverHash.isNotBlank() && book.coverHash != remoteBook?.coverHash) {
                client.putFile(settings, coverFileName, cover).getOrThrow()
            }
        }

        return uploaded
    }

    private suspend fun mergeRemote(snapshot: SyncSnapshot): MergeResult {
        val localDeletedByIdentifier = dao.deletedBooks().associateBy { it.identifier }
        snapshot.deletedBooks.forEach { remoteDeleted ->
            val localDeleted = localDeletedByIdentifier[remoteDeleted.identifier]
            if (localDeleted == null || remoteDeleted.deletedAt > localDeleted.deletedAt) {
                dao.upsertDeletedBook(DeletedBook(remoteDeleted.identifier, remoteDeleted.deletedAt))
                dao.bookByIdentifier(remoteDeleted.identifier)?.let { book ->
                    dao.deleteBookmarksForBook(book.id)
                    dao.deleteAnnotationsForBook(book.id)
                    dao.deleteBook(book.id)
                    runCatching { book.localFile()?.delete() }
                    runCatching { book.cover.takeIf { it.isNotBlank() }?.let(::File)?.delete() }
                }
            }
        }

        val localBooks = dao.allBooks()
        val booksByIdentifier = localBooks.associateBy { it.identifier }
        val deletedIdentifiers = dao.deletedBooks().map { it.identifier }.toSet()
        var progressCount = 0
        snapshot.books.forEach { remote ->
            if (remote.identifier in deletedIdentifiers) return@forEach
            val local = booksByIdentifier[remote.identifier] ?: return@forEach
            if (SyncProgressMergePolicy.shouldApplyRemoteProgress(remote.lastReadDate, remote.progression, local.lastReadDate)) {
                dao.saveProgression(local.id, remote.progression, remote.lastReadDate)
                progressCount++
            }
        }
        return MergeResult(progressCount)
    }

    private suspend fun mergeRemoteAnnotations(snapshot: SyncSnapshot): Int {
        val localDeletedById = dao.deletedReaderAnnotations().associateBy { it.id }
        snapshot.deletedAnnotations.forEach { remoteDeleted ->
            if (remoteDeleted.id.isBlank() || remoteDeleted.bookIdentifier.isBlank() || remoteDeleted.deletedAt <= 0L) {
                return@forEach
            }
            val localDeleted = localDeletedById[remoteDeleted.id]
            if (AnnotationSyncMergePolicy.shouldApplyRemoteDeletion(localDeleted?.deletedAt, remoteDeleted.deletedAt)) {
                dao.upsertDeletedReaderAnnotation(
                    DeletedReaderAnnotation(
                        id = remoteDeleted.id,
                        bookIdentifier = remoteDeleted.bookIdentifier,
                        deletedAt = remoteDeleted.deletedAt,
                    ),
                )
                dao.deleteAnnotation(remoteDeleted.id)
            }
        }

        val deletedById = dao.deletedReaderAnnotations().associateBy { it.id }
        val booksByIdentifier = dao.allBooks().associateBy { it.identifier }
        var merged = 0
        snapshot.annotations.forEach { remote ->
            if (
                remote.id.isBlank() ||
                remote.bookIdentifier.isBlank() ||
                remote.locatorJson.isBlank() ||
                remote.type !in setOf(ReaderAnnotation.TYPE_NOTE, ReaderAnnotation.TYPE_HIGHLIGHT, ReaderAnnotation.TYPE_CORRECTION)
            ) {
                return@forEach
            }
            val deletedAt = deletedById[remote.id]?.deletedAt
            val localAnnotation = dao.annotation(remote.id)
            if (!AnnotationSyncMergePolicy.shouldApplyRemoteAnnotation(localAnnotation?.updatedAt, remote.updatedAt, deletedAt)) {
                return@forEach
            }
            val book = booksByIdentifier[remote.bookIdentifier] ?: return@forEach
            dao.upsertAnnotation(
                ReaderAnnotation(
                    id = remote.id,
                    bookId = book.id,
                    type = remote.type,
                    locatorJson = remote.locatorJson,
                    note = remote.note,
                    createdAt = remote.createdAt,
                    updatedAt = remote.updatedAt,
                    chapterIndex = remote.chapterIndex,
                    chapterTitle = remote.chapterTitle,
                    chapterHref = remote.chapterHref,
                ),
            )
            merged++
        }
        return merged
    }

    private suspend fun buildSnapshot(): SyncSnapshot {
        val tombstoneCutoff = System.currentTimeMillis() - TOMBSTONE_RETENTION_MS
        dao.deleteExpiredTombstones(tombstoneCutoff)
        dao.deleteExpiredReaderAnnotationTombstones(tombstoneCutoff)
        val books = dao.allBooks()
        val booksById = books.associateBy { it.id }
        val progress = books.map { book ->
            val bookFile = book.localFile()
            val coverFile = book.cover
                .takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.exists() && it.isFile && it.length() > 0L }
            SyncBook(
                identifier = book.identifier,
                title = book.title,
                author = book.author,
                creationDate = book.creationDate,
                progression = book.progression,
                lastReadDate = book.lastReadDate,
                mediaType = book.rawMediaType,
                fileName = bookFile?.let { syncFileName(book.identifier, it.extension.ifBlank { "epub" }) },
                coverFileName = coverFile?.let { syncFileName("${book.identifier}-cover", it.extension.ifBlank { "png" }) },
                fileHash = bookFile?.sha256().orEmpty(),
                coverHash = coverFile?.sha256().orEmpty(),
                localPath = bookFile?.absolutePath,
                localCoverPath = coverFile?.absolutePath,
            )
        }
        val annotations = dao.allAnnotations().mapNotNull { annotation ->
            val bookIdentifier = booksById[annotation.bookId]?.identifier ?: return@mapNotNull null
            SyncAnnotation(
                id = annotation.id,
                bookIdentifier = bookIdentifier,
                type = annotation.type,
                locatorJson = annotation.locatorJson,
                note = annotation.note,
                createdAt = annotation.createdAt,
                updatedAt = annotation.updatedAt,
                chapterIndex = annotation.chapterIndex,
                chapterTitle = annotation.chapterTitle,
                chapterHref = annotation.chapterHref,
            )
        }
        return SyncSnapshot(
            books = progress,
            deletedBooks = dao.deletedBooks().map { SyncDeletedBook(it.identifier, it.deletedAt) },
            annotations = annotations,
            deletedAnnotations = dao.deletedReaderAnnotations().map {
                SyncDeletedAnnotation(it.id, it.bookIdentifier, it.deletedAt)
            },
        )
    }

    private data class MergeResult(
        val progressCount: Int,
    )

    private data class SyncSnapshot(
        val books: List<SyncBook>,
        val deletedBooks: List<SyncDeletedBook> = emptyList(),
        val annotations: List<SyncAnnotation> = emptyList(),
        val deletedAnnotations: List<SyncDeletedAnnotation> = emptyList(),
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("version", 5)
                .put("books", JSONArray().also { array -> books.forEach { array.put(it.toJson()) } })
                .put("deletedBooks", JSONArray().also { array -> deletedBooks.forEach { array.put(it.toJson()) } })
                .put("annotations", JSONArray().also { array -> annotations.forEach { array.put(it.toJson()) } })
                .put("deletedAnnotations", JSONArray().also { array -> deletedAnnotations.forEach { array.put(it.toJson()) } })

        companion object {
            fun fromJson(json: JSONObject): SyncSnapshot =
                SyncSnapshot(
                    books = json.optJSONArray("books")?.let { array ->
                        List(array.length()) { SyncBook.fromJson(array.getJSONObject(it)) }
                    }.orEmpty(),
                    deletedBooks = json.optJSONArray("deletedBooks")?.let { array ->
                        List(array.length()) { SyncDeletedBook.fromJson(array.getJSONObject(it)) }
                    }.orEmpty(),
                    annotations = json.optJSONArray("annotations")?.let { array ->
                        List(array.length()) { SyncAnnotation.fromJson(array.getJSONObject(it)) }
                    }.orEmpty(),
                    deletedAnnotations = json.optJSONArray("deletedAnnotations")?.let { array ->
                        List(array.length()) { SyncDeletedAnnotation.fromJson(array.getJSONObject(it)) }
                    }.orEmpty(),
                )
        }
    }

    private data class SyncDeletedBook(
        val identifier: String,
        val deletedAt: Long,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("identifier", identifier)
            .put("deletedAt", deletedAt)

        companion object {
            fun fromJson(json: JSONObject): SyncDeletedBook = SyncDeletedBook(
                identifier = json.optString("identifier"),
                deletedAt = json.optLong("deletedAt"),
            )
        }
    }

    private data class SyncAnnotation(
        val id: String,
        val bookIdentifier: String,
        val type: String,
        val locatorJson: String,
        val note: String,
        val createdAt: Long,
        val updatedAt: Long,
        val chapterIndex: Int,
        val chapterTitle: String,
        val chapterHref: String,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("bookIdentifier", bookIdentifier)
            .put("type", type)
            .put("locator", locatorJson)
            .put("note", note)
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)
            .put("chapterIndex", chapterIndex)
            .put("chapterTitle", chapterTitle)
            .put("chapterHref", chapterHref)

        companion object {
            fun fromJson(json: JSONObject): SyncAnnotation {
                val createdAt = json.optLong("createdAt")
                return SyncAnnotation(
                    id = json.optString("id"),
                    bookIdentifier = json.optString("bookIdentifier"),
                    type = json.optString("type"),
                    locatorJson = json.optString("locator"),
                    note = json.optString("note"),
                    createdAt = createdAt,
                    updatedAt = json.optLong("updatedAt", createdAt),
                    chapterIndex = json.optInt("chapterIndex", -1),
                    chapterTitle = json.optString("chapterTitle"),
                    chapterHref = json.optString("chapterHref"),
                )
            }
        }
    }

    private data class SyncDeletedAnnotation(
        val id: String,
        val bookIdentifier: String,
        val deletedAt: Long,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("bookIdentifier", bookIdentifier)
            .put("deletedAt", deletedAt)

        companion object {
            fun fromJson(json: JSONObject): SyncDeletedAnnotation = SyncDeletedAnnotation(
                id = json.optString("id"),
                bookIdentifier = json.optString("bookIdentifier"),
                deletedAt = json.optLong("deletedAt"),
            )
        }
    }

    private data class SyncBook(
        val identifier: String,
        val title: String,
        val author: String,
        val creationDate: Long,
        val progression: String,
        val lastReadDate: Long,
        val mediaType: String,
        val fileName: String?,
        val coverFileName: String?,
        val fileHash: String = "",
        val coverHash: String = "",
        val localPath: String? = null,
        val localCoverPath: String? = null,
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("identifier", identifier)
                .put("title", title)
                .put("author", author)
                .put("creationDate", creationDate)
                .put("progression", progression)
                .put("lastReadDate", lastReadDate)
                .put("mediaType", mediaType)
                .put("fileName", fileName)
                .put("coverFileName", coverFileName)
                .put("fileHash", fileHash)
                .put("coverHash", coverHash)

        companion object {
            fun fromJson(json: JSONObject): SyncBook =
                SyncBook(
                    identifier = json.optString("identifier"),
                    title = json.optString("title"),
                    author = json.optString("author"),
                    creationDate = json.optLong("creationDate"),
                    progression = json.optString("progression"),
                    lastReadDate = json.optLong("lastReadDate"),
                    mediaType = json.optString("mediaType"),
                    fileName = json.optString("fileName").takeIf { it.isNotBlank() },
                    coverFileName = json.optString("coverFileName").takeIf { it.isNotBlank() },
                    fileHash = json.optString("fileHash"),
                    coverHash = json.optString("coverHash"),
                )
        }
    }

    private fun Book.localFile(): File? =
        runCatching { File(checkNotNull(URI(href).path)) }
            .getOrNull()
            ?.takeIf { it.exists() && it.isFile }

    private fun syncFileName(seed: String, extension: String): String =
        "${seed.sha256().take(24)}.${extension.trimStart('.').ifBlank { "bin" }}"

    private fun uniqueLocalFileName(remoteName: String): String {
        val safeName = remoteName.substringAfterLast('/').substringAfterLast('\\')
        val dot = safeName.lastIndexOf('.')
        val extension = if (dot > 0) {
            safeName.substring(dot + 1)
                .filter(Char::isLetterOrDigit)
                .take(12)
        } else {
            ""
        }
        val suffix = extension.takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()
        return "${remoteName.sha256().take(24)}-${System.currentTimeMillis()}$suffix"
    }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun File.sha256(): String =
        inputStream().buffered().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
        }

    private companion object {
        const val SYNC_FILE = "yura-sync.json"
        const val TOMBSTONE_RETENTION_MS = 90L * 24 * 60 * 60 * 1000
    }
}
