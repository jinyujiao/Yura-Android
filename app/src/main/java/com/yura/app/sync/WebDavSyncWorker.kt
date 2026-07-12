package com.yura.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class WebDavSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        WebDavSyncRepository(applicationContext).sync().fold(
            onSuccess = {
                WebDavSettingsStore.saveLastSyncAt(applicationContext, System.currentTimeMillis())
                Result.success()
            },
            onFailure = { error ->
                if (runAttemptCount >= MAX_ATTEMPTS || error.message?.contains("同步冲突") == true) {
                    Result.failure(workDataOf(KEY_ERROR to (error.message ?: "同步失败")))
                } else {
                    Result.retry()
                }
            },
        )

    companion object {
        const val WORK_NAME = "webdav-sync"
        const val KEY_ERROR = "sync_error"
        private const val MAX_ATTEMPTS = 3

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WebDavSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
