package com.yura.tts

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log

internal class TtsWakeLockManager(context: Context) {
    private val appContext = context.applicationContext
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun acquire(timeoutMs: Long) {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val lock = wakeLock ?: powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:background-continuation").also {
            it.setReferenceCounted(false)
            wakeLock = it
        }
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val nextWifiLock = wifiLock ?: wifiManager?.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "$TAG:background-network",
        )?.also {
            it.setReferenceCounted(false)
            wifiLock = it
        }
        runCatching {
            if (lock.isHeld) lock.release()
            lock.acquire()
            if (nextWifiLock != null && !nextWifiLock.isHeld) nextWifiLock.acquire()
            Log.d(TAG, "holdPlaybackWakeLock acquired cpu=${lock.isHeld} wifi=${nextWifiLock?.isHeld == true}")
        }.onFailure { error ->
            Log.w(TAG, "holdPlaybackWakeLock failed: ${error.message}")
        }
    }

    fun release() {
        val lock = wakeLock ?: return
        runCatching {
            if (lock.isHeld) lock.release()
            wifiLock?.let { if (it.isHeld) it.release() }
            Log.d(TAG, "releasePlaybackWakeLock")
        }
    }

    private companion object {
        const val TAG = "YuraTts"
    }
}
