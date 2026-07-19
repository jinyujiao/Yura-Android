package com.yura.app.notes

import android.content.Context
import android.net.Uri
import com.yura.app.data.Book
import com.yura.app.data.ReaderAnnotation
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class CorrectionExportSummary(
    val applied: Int,
    val skipped: Int,
)

internal class CorrectedEpubExporter(private val context: Context) {
    suspend fun export(book: Book, corrections: List<ReaderAnnotation>, destination: Uri): CorrectionExportSummary =
        withContext(Dispatchers.IO) {
            require(corrections.isNotEmpty()) { "这本书还没有修订记录" }
            val source = runCatching { File(URI(book.href)) }.getOrElse { error("找不到原始 EPUB 文件") }
            require(source.isFile) { "找不到原始 EPUB 文件" }
            ZipFile(source).use { zip ->
                require(zip.getEntry("mimetype") != null) { "当前文件不是标准 EPUB" }
                require(zip.getEntry("META-INF/license.lcpl") == null && zip.getEntry("META-INF/rights.xml") == null) {
                    "暂不支持导出受 DRM 保护的 EPUB"
                }
                val output = context.contentResolver.openOutputStream(destination, "wt") ?: error("无法创建导出文件")
                output.use { stream ->
                    ZipOutputStream(stream).use { out ->
                        val appliedIds = linkedSetOf<String>()
                        writeMimetype(zip, out)
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (entry.name == "mimetype") continue
                            val bytes = zip.getInputStream(entry).use { it.readBytes() }
                            val entryCorrections = corrections.filter { matchesHref(entry.name, it.chapterHref.ifBlank { it.locator?.href?.toString().orEmpty() }) }
                            val outputBytes = if (entryCorrections.isNotEmpty() && isHtml(entry.name)) {
                                val result = CorrectionTextReplacer.apply(
                                    bytes.toString(StandardCharsets.UTF_8),
                                    entryCorrections.mapNotNull { it.toTextCorrection() },
                                )
                                appliedIds += result.appliedIds
                                result.content.toByteArray(StandardCharsets.UTF_8)
                            } else {
                                bytes
                            }
                            val outputEntry = ZipEntry(entry.name).apply { time = entry.time }
                            out.putNextEntry(outputEntry)
                            out.write(outputBytes)
                            out.closeEntry()
                        }
                        out.finish()
                        CorrectionExportSummary(
                            applied = appliedIds.size,
                            skipped = corrections.count { it.id !in appliedIds },
                        )
                    }
                }
            }
        }

    private fun writeMimetype(zip: ZipFile, out: ZipOutputStream) {
        val bytes = zip.getInputStream(zip.getEntry("mimetype")).use { it.readBytes() }
        val crc = CRC32().apply { update(bytes) }
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        }
        out.putNextEntry(entry)
        out.write(bytes)
        out.closeEntry()
    }

    private fun matchesHref(entryName: String, href: String): Boolean {
        if (href.isBlank()) return false
        val normalizedEntry = normalize(entryName)
        val normalizedHref = normalize(href)
        return normalizedEntry == normalizedHref || normalizedEntry.endsWith("/$normalizedHref") || normalizedHref.endsWith("/$normalizedEntry")
    }

    private fun normalize(value: String): String =
        URLDecoder.decode(value.substringBefore('#').substringBefore('?'), StandardCharsets.UTF_8.name())
            .replace('\\', '/')
            .trimStart('/')

    private fun isHtml(name: String): Boolean =
        name.endsWith(".xhtml", ignoreCase = true) || name.endsWith(".html", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true)

    private fun ReaderAnnotation.toTextCorrection(): TextCorrection? {
        val text = locator?.text ?: return null
        return TextCorrection(
            id = id,
            before = text.before.orEmpty(),
            original = text.highlight.orEmpty(),
            after = text.after.orEmpty(),
            replacement = note,
        )
    }
}
