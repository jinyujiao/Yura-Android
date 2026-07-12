package com.yura.app.sync

object SyncProgressMergePolicy {
    fun shouldApplyRemoteProgress(remoteLastReadDate: Long, remoteProgression: String, localLastReadDate: Long): Boolean =
        remoteLastReadDate > localLastReadDate && remoteProgression.isNotBlank()
}
