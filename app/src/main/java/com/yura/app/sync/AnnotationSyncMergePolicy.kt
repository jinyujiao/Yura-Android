package com.yura.app.sync

object AnnotationSyncMergePolicy {
    fun shouldApplyRemoteAnnotation(
        localUpdatedAt: Long?,
        remoteUpdatedAt: Long,
        deletedAt: Long?,
    ): Boolean =
        remoteUpdatedAt > 0L &&
            (localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt) &&
            (deletedAt == null || deletedAt < remoteUpdatedAt)

    fun shouldApplyRemoteDeletion(
        localDeletedAt: Long?,
        remoteDeletedAt: Long,
    ): Boolean =
        remoteDeletedAt > 0L && (localDeletedAt == null || remoteDeletedAt > localDeletedAt)
}
