package com.yura.app.library

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TxtEpubConverter {
    private const val MAX_CHAPTER_CHARS = 90_000
    private const val MIN_CHAPTERS_FOR_TOC = 2

    private val chapterPatterns = listOf(
        Regex("^\\s*(\\u7b2c[\\u96f6\\u3007\\u4e00\\u4e8c\\u4e24\\u4e09\\u56db\\u4e94\\u516d\\u4e03\\u516b\\u4e5d\\u5341\\u767e\\u5343\\u4e07\\d]+[\\u7ae0\\u8282\\u56de\\u5377\\u90e8\\u96c6\\u7bc7](?:\\s*[:\\u003a\\u3001.\\-\\u2014]?\\s*.{0,48})?)\\s*$"),
        Regex("^\\s*((?:\\u6b63\\u6587\\s*)?\\u7b2c[\\u96f6\\u3007\\u4e00\\u4e8c\\u4e24\\u4e09\\u56db\\u4e94\\u516d\\u4e03\\u516b\\u4e5d\\u5341\\u767e\\u5343\\u4e07\\d]+[\\u7ae0\\u56de](?:\\s+.{1,48})?)\\s*$"),
        Regex("^\\s*((?:Chapter|CHAPTER)\\s+[0-9IVXLCDM]+(?:\\s*[:.\\-\\u2014]?\\s*.{0,48})?)\\s*$"),
        Regex("^\\s*([0-9]{1,4}\\s*[\\u3001.\\uff0e]\\s*.{1,48})\\s*$"),
        Regex("^\\s*((?:\\u5e8f\\u7ae0|\\u6954\\u5b50|\\u5f15\\u5b50|\\u524d\\u8a00|\\u6b63\\u6587|\\u5c3e\\u58f0|\\u540e\\u8bb0|\\u756a\\u5916)(?:\\s*[:\\u003a\\u3001.\\-\\u2014]?\\s*.{0,48})?)\\s*$"),
    )

    private val authorPatterns = listOf(
        Regex("^\\s*(?:\\u4f5c\\s*\\u8005|\\u8457\\s*\\u8005|\\u4f5c\\s*\\u8005\\s*\\u540d|Author|author)\\s*[:\\uff1a\\u3001\\-]?\\s*(.{1,64})\\s*$"),
        Regex("^.{0,96}(?:\\u4f5c\\s*\\u8005|\\u8457\\s*\\u8005|\\u4f5c\\s*\\u8005\\s*\\u540d)\\s*[:\\uff1a]\\s*(.{1,64})\\s*$"),
        Regex("^.{0,96}(?:Author|author)\\s*[:\\uff1a]\\s*(.{1,64})\\s*$"),
        Regex("^\\s*(?:By|by|BY)\\s+(.{1,48})\\s*$"),
    )

    fun convert(
        txtFile: File,
        outputDir: File,
        title: String,
        identifier: String,
    ): ConvertedTxtPublication {
        val text = normalizeText(decodeTxt(txtFile.readBytes()))
        val metadata = extractMetadata(text)
        val bookTitle = title.ifBlank { txtFile.nameWithoutExtension }
        val chapters = detectChapters(text).ifEmpty {
            listOf(TxtChapter(title = "\u6b63\u6587", body = text))
        }.flatMap { splitLargeChapter(it) }

        outputDir.mkdirs()
        val epubFile = File(outputDir, "${UUID.randomUUID()}.epub")
        writeEpub(
            epubFile = epubFile,
            title = bookTitle,
            author = metadata.author,
            identifier = identifier,
            chapters = chapters,
        )
        return ConvertedTxtPublication(file = epubFile, author = metadata.author)
    }

    private fun decodeTxt(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes.copyOfRange(3, bytes.size), StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes.copyOfRange(2, bytes.size), StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes.copyOfRange(2, bytes.size), StandardCharsets.UTF_16BE)
        }

        val candidates = listOf(
            StandardCharsets.UTF_8,
            Charset.forName("GB18030"),
            Charset.forName("GBK"),
            Charset.forName("Big5"),
        )
        return candidates
            .mapNotNull { charset -> decodeCandidate(bytes, charset) }
            .minByOrNull { decoded ->
                decoded.count { it == '\uFFFD' } * 10_000 + decoded.count { it.code in 0x0000..0x0008 }
            }
            ?: String(bytes, StandardCharsets.UTF_8)
    }

    private fun decodeCandidate(bytes: ByteArray, charset: Charset): String? =
        try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

    private fun normalizeText(text: String): String =
        text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
            .replace(Regex("[ \\t\\u3000]+\\n"), "\n")
            .trim()

    private fun extractMetadata(text: String): TxtMetadata {
        val headerLines = text.lineSequence()
            .take(80)
            .filter { it.length < 180 }
            .toList()
        val author = headerLines.firstNotNullOfOrNull { line ->
            val raw = extractAuthorFromLine(line) ?: authorPatterns.firstNotNullOfOrNull { pattern ->
                pattern.matchEntire(line)?.groupValues?.getOrNull(1)
            }
            raw?.cleanMetadataValue()
        }
        return TxtMetadata(author = author.orEmpty())
    }

    private fun extractAuthorFromLine(line: String): String? {
        val compact = line.trim()
        if (compact.isBlank() || compact.length > 180) return null
        val authorKeyword = Regex("(?:\\u4f5c\\s*\\u8005|\\u8457\\s*\\u8005|\\u4f5c\\s*\\u8005\\s*\\u540d)")
        val keyword = authorKeyword.find(compact) ?: return null
        val afterKeyword = compact.substring(keyword.range.last + 1)
        val separator = afterKeyword.indexOfFirst { it == ':' || it == '\uff1a' }
        if (separator < 0) return null
        return afterKeyword.substring(separator + 1).cleanMetadataValue().takeIf { it.isNotBlank() }
    }

    private fun String.cleanMetadataValue(): String =
        trim()
            .substringBefore("\u4e66\u540d")
            .substringBefore("\u7c7b\u578b")
            .substringBefore("\u72b6\u6001")
            .substringBefore("\u66f4\u65b0")
            .substringBefore("\u7b80\u4ecb")
            .substringBefore("http")
            .trim(' ', '\t', ':', '\uff1a', '\u3001', '-', '\u2014', '\u300b', '\u300d')
            .replace(Regex("[\\u3000\\t ]+"), " ")
            .take(64)

    private fun detectChapters(text: String): List<TxtChapter> {
        val lines = text.lines()
        val headings = mutableListOf<Heading>()
        var offset = 0
        lines.forEach { line ->
            val title = matchChapterTitle(line)
            if (title != null) {
                headings += Heading(title = title, start = offset, end = offset + line.length)
            }
            offset += line.length + 1
        }
        if (headings.size < MIN_CHAPTERS_FOR_TOC) return emptyList()

        val chapters = mutableListOf<TxtChapter>()
        val preface = text.substring(0, headings.first().start).trim()
        if (preface.isNotBlank()) {
            chapters += TxtChapter("\u5f00\u7bc7", preface)
        }
        headings.forEachIndexed { index, heading ->
            val bodyStart = heading.end
            val bodyEnd = headings.getOrNull(index + 1)?.start ?: text.length
            val body = text.substring(bodyStart, bodyEnd).trim()
            chapters += TxtChapter(heading.title, body.ifBlank { heading.title })
        }
        return chapters
    }

    private fun matchChapterTitle(line: String): String? {
        val compact = line.trim()
        if (compact.length !in 2..64) return null
        if (compact.count { it.isLetterOrDigit() || it in CHAPTER_KEY_CHARS } < 2) return null
        return chapterPatterns.firstNotNullOfOrNull { pattern ->
            pattern.matchEntire(compact)?.groupValues?.getOrNull(1)?.trim()
        }
    }

    private fun splitLargeChapter(chapter: TxtChapter): List<TxtChapter> {
        if (chapter.body.length <= MAX_CHAPTER_CHARS) return listOf(chapter)
        val parts = mutableListOf<TxtChapter>()
        var start = 0
        var index = 1
        while (start < chapter.body.length) {
            val preferredEnd = (start + MAX_CHAPTER_CHARS).coerceAtMost(chapter.body.length)
            val nextBreak = chapter.body.lastIndexOf('\n', preferredEnd).takeIf { it > start + MAX_CHAPTER_CHARS / 2 }
            val end = nextBreak ?: preferredEnd
            parts += TxtChapter("${chapter.title} $index", chapter.body.substring(start, end).trim())
            start = end
            index += 1
        }
        return parts
    }

    private fun writeEpub(
        epubFile: File,
        title: String,
        author: String,
        identifier: String,
        chapters: List<TxtChapter>,
    ) {
        ZipOutputStream(epubFile.outputStream()).use { zip ->
            zip.putStoredEntry("mimetype", "application/epub+zip".toByteArray(StandardCharsets.UTF_8))
            zip.putDeflatedEntry("META-INF/container.xml", containerXml().toByteArray(StandardCharsets.UTF_8))
            zip.putDeflatedEntry("EPUB/styles/yura-txt.css", css().toByteArray(StandardCharsets.UTF_8))
            zip.putDeflatedEntry("EPUB/package.opf", packageOpf(title, author, identifier, chapters).toByteArray(StandardCharsets.UTF_8))
            zip.putDeflatedEntry("EPUB/nav.xhtml", navXhtml(title, chapters).toByteArray(StandardCharsets.UTF_8))
            chapters.forEachIndexed { index, chapter ->
                zip.putDeflatedEntry(
                    "EPUB/text/chapter-${index + 1}.xhtml",
                    chapterXhtml(chapter).toByteArray(StandardCharsets.UTF_8),
                )
            }
        }
    }

    private fun ZipOutputStream.putStoredEntry(name: String, bytes: ByteArray) {
        val crc = CRC32().apply { update(bytes) }
        putNextEntry(
            ZipEntry(name).apply {
                method = ZipEntry.STORED
                size = bytes.size.toLong()
                compressedSize = bytes.size.toLong()
                this.crc = crc.value
            },
        )
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.putDeflatedEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun containerXml(): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

    private fun packageOpf(title: String, author: String, identifier: String, chapters: List<TxtChapter>): String {
        val creator = author.takeIf { it.isNotBlank() }
            ?.let { "    <dc:creator>${it.xmlEscape()}</dc:creator>\n" }
            .orEmpty()
        val manifestItems = chapters.indices.joinToString("\n") { index ->
            """    <item id="chapter-${index + 1}" href="text/chapter-${index + 1}.xhtml" media-type="application/xhtml+xml"/>"""
        }
        val spineItems = chapters.indices.joinToString("\n") { index ->
            """    <itemref idref="chapter-${index + 1}"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="book-id" version="3.0" xml:lang="zh-CN">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="book-id">${identifier.xmlEscape()}</dc:identifier>
    <dc:title>${title.xmlEscape()}</dc:title>
$creator    <dc:language>zh-CN</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="style" href="styles/yura-txt.css" media-type="text/css"/>
$manifestItems
  </manifest>
  <spine>
$spineItems
  </spine>
</package>"""
    }

    private fun navXhtml(title: String, chapters: List<TxtChapter>): String {
        val items = chapters.mapIndexed { index, chapter ->
            """      <li><a href="text/chapter-${index + 1}.xhtml">${chapter.title.xmlEscape()}</a></li>"""
        }.joinToString("\n")
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="zh-CN" lang="zh-CN">
<head>
  <title>${title.xmlEscape()}</title>
  <link rel="stylesheet" type="text/css" href="styles/yura-txt.css"/>
</head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>${title.xmlEscape()}</h1>
    <ol>
$items
    </ol>
  </nav>
</body>
</html>"""
    }

    private fun chapterXhtml(chapter: TxtChapter): String {
        val paragraphs = chapter.body
            .split(Regex("\n{2,}|\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n") { paragraph -> "    <p>${paragraph.xmlEscape()}</p>" }
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="zh-CN" lang="zh-CN">
<head>
  <title>${chapter.title.xmlEscape()}</title>
  <link rel="stylesheet" type="text/css" href="../styles/yura-txt.css"/>
</head>
<body>
  <section>
    <h1>${chapter.title.xmlEscape()}</h1>
$paragraphs
  </section>
</body>
</html>"""
    }

    private fun css(): String =
        """body {
  line-height: 1.78;
  word-break: break-word;
}
h1 {
  font-size: 1.35em;
  line-height: 1.45;
  margin: 0 0 1.25em;
  text-align: center;
}
p {
  margin: 0 0 0.9em;
  text-indent: 2em;
}"""

    private fun String.xmlEscape(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private data class Heading(
        val title: String,
        val start: Int,
        val end: Int,
    )

    private data class TxtChapter(
        val title: String,
        val body: String,
    )

    private data class TxtMetadata(
        val author: String,
    )

    data class ConvertedTxtPublication(
        val file: File,
        val author: String,
    )

    private val CHAPTER_KEY_CHARS = setOf(
        '\u7b2c',
        '\u7ae0',
        '\u8282',
        '\u56de',
        '\u5377',
        '\u90e8',
        '\u96c6',
        '\u7bc7',
        '\u5e8f',
        '\u6954',
        '\u5b50',
        '\u5f15',
        '\u524d',
        '\u8a00',
        '\u6b63',
        '\u6587',
        '\u5c3e',
        '\u58f0',
        '\u540e',
        '\u8bb0',
        '\u756a',
        '\u5916',
    )
}
