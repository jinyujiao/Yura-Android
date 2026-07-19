package com.yura.app.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderProgressPersistencePolicyTest {
    @Test
    fun normalReaderPersistsProgress() {
        assertTrue(ReaderProgressPersistencePolicy.shouldPersist(previewMode = false))
    }

    @Test
    fun annotationPreviewNeverPersistsProgress() {
        assertFalse(ReaderProgressPersistencePolicy.shouldPersist(previewMode = true))
    }
}
