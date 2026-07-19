package com.yura.app.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryImportResultMessageTest {
    @Test
    fun reportsSuccessfulBatchImport() {
        assertEquals(
            "已导入 4 本图书",
            importResultMessage(4, 4, 0, null, singleImport = false),
        )
    }

    @Test
    fun reportsPartialBatchImport() {
        assertEquals(
            "批量导入完成：成功 3 本，失败 2 本",
            importResultMessage(5, 3, 2, IllegalArgumentException("损坏"), singleImport = false),
        )
    }

    @Test
    fun preservesSingleImportError() {
        assertEquals(
            "文件格式不受支持",
            importResultMessage(1, 0, 1, IllegalArgumentException("文件格式不受支持"), singleImport = true),
        )
    }

    @Test
    fun reportsCompletelyFailedBatchImport() {
        assertEquals(
            "批量导入失败：3 个文件均未导入",
            importResultMessage(3, 0, 3, IllegalArgumentException("损坏"), singleImport = false),
        )
    }
}
