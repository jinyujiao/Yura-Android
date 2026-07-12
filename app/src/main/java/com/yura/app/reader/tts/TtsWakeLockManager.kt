package com.yura.app.reader.tts

import android.content.Context
import android.os.PowerManager
import android.util.Log

internal class TtsWakeLockManager(context: Context) {
    private val appContext = context.applicationContext
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire(timeoutMs: Long) {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val lock = wakeLock ?: powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:background-continuation").also {
            it.setReferenceCounted(false)
            wakeLock = it
        }
        runCatching {
            if (lock.isHeld) lock.release()
            lock.acquire(timeoutMs)
            Log.d(TAG, "holdPlaybackWakeLock timeoutMs=$timeoutMs")
        }.onFailure { error ->
            Log.w(TAG, "holdPlaybackWakeLock failed: ${error.message}")
        }
    }

    fun release() {
        val lock = wakeLock ?: return
        runCatching {
            if (lock.isHeld) {
                lock.release()
                Log.d(TAG, "releasePlaybackWakeLock")
            }
        }
    }

    private companion object {
        const val TAG = "YuraTts"
    }
}
