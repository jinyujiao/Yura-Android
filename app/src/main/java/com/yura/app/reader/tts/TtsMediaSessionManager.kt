package com.yura.app.reader.tts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.media3.common.Player
import com.yura.app.reader.MediaService

internal class TtsMediaSessionManager(
    private val context: Context,
    private val player: Player,
    private val sessionId: String,
    private val onPrevious: () -> Unit,
    private val onNext: () -> Unit,
    private val onStop: () -> Unit,
) {
    private var binder: MediaService.Binder? = null
    private var bound = false
    private var connecting = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service as? MediaService.Binder
            bound = binder != null
            connecting = false
            openSession()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            bound = false
            connecting = false
        }
    }

    fun ensure() {
        if (bound) {
            openSession()
            return
        }
        if (connecting) return
        connecting = true
        val intent = Intent(MediaService.SERVICE_INTERFACE).setClass(context, MediaService::class.java)
        runCatching { context.startService(intent) }
        runCatching {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) connecting = false
        }.onFailure { connecting = false }
    }

    fun release() {
        binder?.closeTtsSession()
        if (bound) runCatching { context.unbindService(connection) }
        binder = null
        bound = false
        connecting = false
    }

    private fun openSession() {
        binder?.openTtsSession(
            player = player,
            sessionId = sessionId,
            onPrevious = onPrevious,
            onNext = onNext,
            onStop = onStop,
        )
    }
}
