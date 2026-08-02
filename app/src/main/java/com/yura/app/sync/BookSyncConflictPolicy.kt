package com.yura.app.sync

internal object BookSyncConflictPolicy {
    fun shouldApplyRemoteDeletion(
        remoteDeletedAt: Long,
        localBookCreatedAt: Long?,
        localDeletedAt: Long?,
    ): Boolean =
        remoteDeletedAt > 0L &&
            (localBookCreatedAt == null || remoteDeletedAt >= localBookCreatedAt) &&
            (localDeletedAt == null || remoteDeletedAt > localDeletedAt)

    fun shouldRestoreFromRemoteBook(remoteCreatedAt: Long, localDeletedAt: Long?): Boolean =
        localDeletedAt != null && remoteCreatedAt > localDeletedAt
}
