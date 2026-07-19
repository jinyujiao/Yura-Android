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

    @Test
    fun annotationTombstonePreventsResurrection() {
        assertFalse(
            AnnotationSyncMergePolicy.shouldApplyRemoteAnnotation(
                localUpdatedAt = null,
                remoteUpdatedAt = 100,
                deletedAt = 120,
            ),
        )
        assertTrue(
            AnnotationSyncMergePolicy.shouldApplyRemoteAnnotation(
                localUpdatedAt = null,
                remoteUpdatedAt = 130,
                deletedAt = 120,
            ),
        )
        assertFalse(
            AnnotationSyncMergePolicy.shouldApplyRemoteAnnotation(
                localUpdatedAt = 140,
                remoteUpdatedAt = 130,
                deletedAt = null,
            ),
        )
    }

    @Test
    fun onlyAppliesNewerValidAnnotationDeletion() {
        assertTrue(AnnotationSyncMergePolicy.shouldApplyRemoteDeletion(localDeletedAt = null, remoteDeletedAt = 100))
        assertTrue(AnnotationSyncMergePolicy.shouldApplyRemoteDeletion(localDeletedAt = 90, remoteDeletedAt = 100))
        assertFalse(AnnotationSyncMergePolicy.shouldApplyRemoteDeletion(localDeletedAt = 100, remoteDeletedAt = 100))
        assertFalse(AnnotationSyncMergePolicy.shouldApplyRemoteDeletion(localDeletedAt = null, remoteDeletedAt = 0))
    }
}
