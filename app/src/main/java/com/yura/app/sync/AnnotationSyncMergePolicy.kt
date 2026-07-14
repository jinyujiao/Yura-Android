package com.yura.app.sync

object AnnotationSyncMergePolicy {
    fun shouldApplyRemoteAnnotation(
        localExists: Boolean,
        remoteCreatedAt: Long,
        deletedAt: Long?,
    ): Boolean =
        !localExists && remoteCreatedAt > 0L && (deletedAt == null || deletedAt < remoteCreatedAt)

    fun shouldApplyRemoteDeletion(
        localDeletedAt: Long?,
        remoteDeletedAt: Long,
    ): Boolean =
        remoteDeletedAt > 0L && (localDeletedAt == null || remoteDeletedAt > localDeletedAt)
}
