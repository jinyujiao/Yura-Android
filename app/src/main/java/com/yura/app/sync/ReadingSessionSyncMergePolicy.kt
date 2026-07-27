package com.yura.app.sync

internal object ReadingSessionSyncMergePolicy {
    fun shouldApplyRemote(
        localUpdatedAt: Long?,
        localDurationMs: Long?,
        remoteUpdatedAt: Long,
        remoteDurationMs: Long,
    ): Boolean =
        localUpdatedAt == null ||
            remoteUpdatedAt > localUpdatedAt ||
            (remoteUpdatedAt == localUpdatedAt && remoteDurationMs > (localDurationMs ?: 0L))
}
