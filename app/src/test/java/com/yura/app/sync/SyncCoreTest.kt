package com.yura.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCoreTest {
    @Test
    fun normalizesWebDavUrlsAndRequiresHttps() {
        assertEquals("https://example.com/dav/Yura", WebDavUrlResolver.remoteDirectoryUrl("https://example.com/dav/", "Yura"))
        assertEquals("https://example.com/dav/Yura/books.json", WebDavUrlResolver.fileUrl("https://example.com/dav", "/Yura", "books.json"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHttpWebDavUrls() {
        WebDavUrlResolver.remoteDirectoryUrl("http://example.com", "/Yura")
    }

    @Test
    fun onlyAppliesNewerNonBlankProgress() {
        assertTrue(SyncProgressMergePolicy.shouldApplyRemoteProgress(20, "{\"locations\":{}}", 10))
        assertFalse(SyncProgressMergePolicy.shouldApplyRemoteProgress(10, "{\"locations\":{}}", 10))
        assertFalse(SyncProgressMergePolicy.shouldApplyRemoteProgress(20, "", 10))
    }
}
