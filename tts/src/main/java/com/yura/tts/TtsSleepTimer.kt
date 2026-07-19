package com.yura.tts

import android.os.Handler
import android.os.Looper

internal class TtsSleepTimer(
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val onTimerFinished: () -> Unit,
) {
    private var timerRunnable: Runnable? = null

    fun schedule(minutes: Int): Int {
        cancel()
        val safeMinutes = minutes.coerceAtLeast(0)
        if (safeMinutes == 0) return 0

        timerRunnable = Runnable {
            timerRunnable = null
            onTimerFinished()
        }.also { timer ->
            handler.postDelayed(timer, safeMinutes * 60_000L)
        }
        return safeMinutes
    }

    fun cancel() {
        timerRunnable?.let(handler::removeCallbacks)
        timerRunnable = null
    }
}
