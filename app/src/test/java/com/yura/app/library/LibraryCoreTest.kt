package com.yura.app.library

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCoreTest {
    @Test
    fun convertsTxtToReadableEpub() {
        val directory = createTempDirectory("yura-txt-").toFile()
        val source = File(directory, "novel.txt").apply {
            writeText("作者：测试作者\n\n第一章 开始\n正文内容", StandardCharsets.UTF_8)
        }

        val converted = TxtEpubConverter.convert(source, directory, "测试小说", "test-id")

        ZipFile(converted.file).use { zip ->
            assertTrue(zip.getEntry("mimetype") != null)
            assertTrue(zip.getEntry("EPUB/package.opf") != null)
            assertTrue(zip.getEntry("EPUB/text/chapter-1.xhtml") != null)
        }
        assertTrue(converted.author.contains("测试作者"))
    }

    @Test
    fun detectsDuplicateByIdentifierOrMetadata() {
        assertTrue(BookImportRules.isDuplicate(hasMatchingIdentifier = true, hasMatchingTitleAndAuthor = false))
        assertTrue(BookImportRules.isDuplicate(hasMatchingIdentifier = false, hasMatchingTitleAndAuthor = true))
        assertFalse(BookImportRules.isDuplicate(hasMatchingIdentifier = false, hasMatchingTitleAndAuthor = false))
    }

    @Test
    fun deletesLocalFiles() {
        val file = createTempFile("yura-delete-").toFile()
        assertTrue(LocalBookFileCleaner.deleteOwnedFiles(file))
        assertFalse(file.exists())
    }
}
