package com.yura.app.sync

import android.content.Context
import com.yura.app.data.Book
import com.yura.app.data.DeletedBook
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

            val coverPath = remote.coverFileName?.let { coverName ->
                val coverFile = File(appContext.filesDir, "covers/${uniqueLocalFileName(coverName)}")
                if (client.getFile(settings, coverName, coverFile).getOrThrow()) {
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

    private suspend fun buildSnapshot(): SyncSnapshot {
        dao.deleteExpiredTombstones(System.currentTimeMillis() - TOMBSTONE_RETENTION_MS)
        val books = dao.allBooks()
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
        return SyncSnapshot(
            books = progress,
            deletedBooks = dao.deletedBooks().map { SyncDeletedBook(it.identifier, it.deletedAt) },
        )
    }

    private data class MergeResult(
        val progressCount: Int,
    )

    private data class SyncSnapshot(
        val books: List<SyncBook>,
        val deletedBooks: List<SyncDeletedBook> = emptyList(),
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("version", 4)
                .put("books", JSONArray().also { array -> books.forEach { array.put(it.toJson()) } })
                .put("deletedBooks", JSONArray().also { array -> deletedBooks.forEach { array.put(it.toJson()) } })

        companion object {
            fun fromJson(json: JSONObject): SyncSnapshot =
                SyncSnapshot(
                    books = json.optJSONArray("books")?.let { array ->
                        List(array.length()) { SyncBook.fromJson(array.getJSONObject(it)) }
                    }.orEmpty(),
                    deletedBooks = json.optJSONArray("deletedBooks")?.let { array ->
                        List(array.length()) { SyncDeletedBook.fromJson(array.getJSONObject(it)) }
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
        val dot = remoteName.lastIndexOf('.')
        val base = if (dot > 0) remoteName.substring(0, dot) else remoteName
        val ext = if (dot > 0) remoteName.substring(dot) else ""
        return "${base}-${System.currentTimeMillis()}$ext"
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
