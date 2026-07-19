package com.yura.app.reader

internal object ReaderProgressPersistencePolicy {
    fun shouldPersist(previewMode: Boolean): Boolean = !previewMode
}
