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

    @Test
    fun readingSessionMergeKeepsNewestCheckpoint() {
        assertTrue(ReadingSessionSyncMergePolicy.shouldApplyRemote(null, null, 100, 10_000))
        assertTrue(ReadingSessionSyncMergePolicy.shouldApplyRemote(100, 8_000, 100, 10_000))
        assertTrue(ReadingSessionSyncMergePolicy.shouldApplyRemote(100, 10_000, 110, 10_000))
        assertFalse(ReadingSessionSyncMergePolicy.shouldApplyRemote(110, 10_000, 100, 12_000))
        assertFalse(ReadingSessionSyncMergePolicy.shouldApplyRemote(100, 10_000, 100, 9_000))
    }

    @Test
    fun reimportedLocalBookWinsOverOlderRemoteDeletion() {
        assertFalse(
            BookSyncConflictPolicy.shouldApplyRemoteDeletion(
                remoteDeletedAt = 100,
                localBookCreatedAt = 120,
                localDeletedAt = null,
            ),
        )
        assertTrue(
            BookSyncConflictPolicy.shouldApplyRemoteDeletion(
                remoteDeletedAt = 130,
                localBookCreatedAt = 120,
                localDeletedAt = null,
            ),
        )
        assertTrue(
            BookSyncConflictPolicy.shouldApplyRemoteDeletion(
                remoteDeletedAt = 120,
                localBookCreatedAt = 120,
                localDeletedAt = null,
            ),
        )
        assertFalse(
            BookSyncConflictPolicy.shouldApplyRemoteDeletion(
                remoteDeletedAt = 100,
                localBookCreatedAt = null,
                localDeletedAt = 110,
            ),
        )
        assertFalse(
            BookSyncConflictPolicy.shouldApplyRemoteDeletion(
                remoteDeletedAt = 0,
                localBookCreatedAt = null,
                localDeletedAt = null,
            ),
        )
    }

    @Test
    fun reimportedRemoteBookClearsOlderLocalDeletion() {
        assertTrue(BookSyncConflictPolicy.shouldRestoreFromRemoteBook(remoteCreatedAt = 120, localDeletedAt = 100))
        assertFalse(BookSyncConflictPolicy.shouldRestoreFromRemoteBook(remoteCreatedAt = 100, localDeletedAt = 100))
        assertFalse(BookSyncConflictPolicy.shouldRestoreFromRemoteBook(remoteCreatedAt = 90, localDeletedAt = 100))
        assertFalse(BookSyncConflictPolicy.shouldRestoreFromRemoteBook(remoteCreatedAt = 120, localDeletedAt = null))
    }
}
